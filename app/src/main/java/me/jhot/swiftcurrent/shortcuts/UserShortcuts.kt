package me.jhot.swiftcurrent.shortcuts

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.core.LauncherUpdate
import me.jhot.swiftcurrent.core.UserShortcut
import me.jhot.swiftcurrent.search.LaunchOutcome
import java.io.ByteArrayOutputStream
import java.net.URISyntaxException
import java.util.UUID

sealed interface AddOutcome {
    data class Added(val id: String) : AddOutcome
    data class Rejected(val reason: RejectReason) : AddOutcome

    /** The same app, intent and name is already stored. */
    data object Duplicate : AddOutcome
}

enum class RejectReason { MissingIntent, MissingName, WrongTarget }

/** Starts an activity from outside an Activity; may throw what `startActivity` throws. */
fun interface IntentStarter {
    fun start(intent: Intent)
}

/** Keeps a content URI readable after a reboot; may throw when the creator did not really grant it. */
fun interface UriPermissionTaker {
    fun takePersistable(uri: Uri, modeFlags: Int)
}

/** Turns a create-shortcut result into small PNG bytes, or null when it carries no usable icon. */
fun interface ShortcutIcons {
    fun encode(result: Intent): ByteArray?
}

/** Turns a drawable (an app's icon) into small PNG bytes, or null when it cannot be encoded. */
fun interface DrawableIcons {
    fun encode(drawable: Drawable): ByteArray?
}

/** User-readable launch failures, passed in so the logic stays free of resources. */
class LaunchMessages(val notFound: String, val notAllowed: String, val unusable: String)

internal const val ICON_MAX_PX = 96

fun userShortcutKey(id: String) = "user/$id"

/** The inverse of [userShortcutKey]; null for any other key. */
fun userShortcutId(key: String): String? = key.removePrefix("user/").takeIf { key.startsWith("user/") }

private const val GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION

/**
 * User-made shortcuts: the intents other apps' create-shortcut activities return.
 *
 * SECURITY: a stored intent is data that came from another app (and can come back from a restored
 * backup), so it is never trusted. [launch] always strips URI grant flags and any selector, so a
 * shortcut cannot hand our permissions on, and refuses an intent whose explicit component or
 * package is not the app that created it ([UserShortcut.appPackage]). Implicit intents (a `geo:`
 * or `https:` link) are allowed: that is the common shape of a shortcut. The same target check
 * runs when a result is added, so a creator cannot store a shortcut aimed at a third app.
 */
class UserShortcuts(
    private val starter: IntentStarter,
    private val messages: LaunchMessages,
    private val permissions: UriPermissionTaker = UriPermissionTaker { _, _ -> },
    private val icons: ShortcutIcons = ShortcutIcons { null },
    private val drawableIcons: DrawableIcons = DrawableIcons { null },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /**
     * [update] is the owner's writer (the shell's), not a store: whoever holds the live data makes
     * the change and publishes it, so nothing has to read the store back to see the new shortcut.
     */
    suspend fun addFromResult(creator: Creator, result: Intent, update: LauncherUpdate): AddOutcome {
        @Suppress("DEPRECATION")
        val target = result.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)
            ?: return AddOutcome.Rejected(RejectReason.MissingIntent)
        val name = result.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return AddOutcome.Rejected(RejectReason.MissingName)
        val pkg = creator.packageName
        if (!targetsOnly(target, pkg)) return AddOutcome.Rejected(RejectReason.WrongTarget)

        takePersistablePermissions(result, target)
        val clean = Intent(target).apply {
            setFlags(flags and GRANT_FLAGS.inv())
            selector = null
            clipData = null
        }
        val shortcut = UserShortcut(newId(), pkg, name, clean.toUri(Intent.URI_INTENT_SCHEME), icons.encode(result))
        return store(shortcut, update)
    }

    /**
     * Stores a web link as a shortcut of [browserPackage]. The intent is built here, from a validated
     * [link], and passes the same [targetsOnly] check as any other shortcut. A blank [name] becomes the host.
     */
    suspend fun addWebLink(browserPackage: String, link: WebLink, name: String, icon: Drawable?, update: LauncherUpdate): AddOutcome {
        val label = name.trim().ifEmpty { WebLinks.defaultName(link) }
        val shortcut = UserShortcut(newId(), browserPackage, label, WebLinks.intentUri(link, browserPackage), icon?.let(drawableIcons::encode))
        return store(shortcut, update)
    }

    private suspend fun store(shortcut: UserShortcut, update: LauncherUpdate): AddOutcome {
        // Decided inside the update so two results arriving together cannot both pass the check.
        var duplicate = false
        val written = update { d ->
            if (d.userShortcuts.any { it.appPackage == shortcut.appPackage && it.intentUri == shortcut.intentUri && it.label == shortcut.label }) {
                duplicate = true
                d
            } else {
                d.copy(userShortcuts = d.userShortcuts + shortcut)
            }
        }
        if (duplicate) return AddOutcome.Duplicate
        // The shell's writer logs a failed write and returns the data it already had, so look for the shortcut
        // rather than assume it was saved; the caller turns this into the same message as any other failure.
        if (written.userShortcuts.none { it.id == shortcut.id }) throw java.io.IOException("the shortcut was not saved")
        return AddOutcome.Added(shortcut.id)
    }

    /** Failures are logged and ignored: a shortcut whose link may lapse is still better than none. */
    private fun takePersistablePermissions(result: Intent, target: Intent) {
        for (source in listOf(result, target)) {
            val flags = source.flags
            if (flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION == 0) continue
            val mode = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            if (mode == 0) continue
            val uris = buildList {
                source.data?.let { add(it) }
                source.clipData?.let { clip -> for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { add(it) } }
            }
            for (uri in uris.distinct().filter { it.scheme == "content" }) {
                try {
                    permissions.takePersistable(uri, mode)
                } catch (e: SecurityException) {
                    Log.w("UserShortcuts", "no persistable permission for $uri", e)
                } catch (e: IllegalArgumentException) {
                    Log.w("UserShortcuts", "unsupported uri $uri", e)
                }
            }
        }
    }

    /** Blocking (starts an activity): call off the main thread, like every other launch action. */
    fun launch(s: UserShortcut): LaunchOutcome {
        val intent = try {
            Intent.parseUri(s.intentUri, Intent.URI_INTENT_SCHEME)
        } catch (e: URISyntaxException) {
            return LaunchOutcome.Failed(messages.unusable)
        }
        if (!targetsOnly(intent, s.appPackage)) return LaunchOutcome.Failed(messages.notAllowed)
        intent.setFlags((intent.flags and GRANT_FLAGS.inv()) or Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.selector = null
        return try {
            starter.start(intent)
            LaunchOutcome.Launched
        } catch (e: ActivityNotFoundException) {
            LaunchOutcome.Failed(messages.notFound)
        } catch (e: SecurityException) {
            LaunchOutcome.Failed(messages.notAllowed)
        }
    }

    /** Removes the shortcut and everything that points at it ([withoutShortcut]), in one write. */
    suspend fun delete(id: String, update: LauncherUpdate) {
        update { d -> d.withoutShortcut(userShortcutKey(id)).copy(userShortcuts = d.userShortcuts.filter { it.id != id }) }
    }

    companion object {
        /** True when [intent] names no component or package other than [appPackage]. */
        internal fun targetsOnly(intent: Intent, appPackage: String): Boolean {
            val component: ComponentName? = intent.component
            if (component != null && component.packageName != appPackage) return false
            val pkg = intent.`package`
            return pkg == null || pkg == appPackage
        }

        fun create(context: Context): UserShortcuts {
            val app = context.applicationContext
            return UserShortcuts(
                starter = { app.startActivity(it) },
                messages = LaunchMessages(
                    notFound = app.getString(R.string.user_shortcut_not_found),
                    notAllowed = app.getString(R.string.launch_not_allowed),
                    unusable = app.getString(R.string.user_shortcut_unusable),
                ),
                permissions = { uri, mode -> app.contentResolver.takePersistableUriPermission(uri, mode) },
                icons = BitmapShortcutIcons(app),
                drawableIcons = BitmapDrawableIcons(),
            )
        }
    }
}

/** Scales [bitmap] down to [ICON_MAX_PX] (never up), keeping its proportions, and encodes it as PNG; null on failure. */
internal fun encodePng(bitmap: Bitmap): ByteArray? = try {
    val scale = minOf(1f, ICON_MAX_PX.toFloat() / maxOf(bitmap.width, bitmap.height))
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true,
        )
    } else bitmap
    ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
} catch (e: Exception) {
    Log.w("UserShortcuts", "could not encode the icon", e)
    null
}

/** Draws an app's icon at [ICON_MAX_PX] and stores it as PNG, for a shortcut we make ourselves. */
class BitmapDrawableIcons : DrawableIcons {
    override fun encode(drawable: Drawable): ByteArray? = try {
        encodePng(drawable.toBitmap(ICON_MAX_PX, ICON_MAX_PX))
    } catch (e: Exception) {
        Log.w("UserShortcuts", "could not draw the icon", e)
        null
    }
}

/** Reads `EXTRA_SHORTCUT_ICON` (a bitmap) or `EXTRA_SHORTCUT_ICON_RESOURCE` and stores a small PNG. */
class BitmapShortcutIcons(private val context: Context) : ShortcutIcons {
    override fun encode(result: Intent): ByteArray? {
        val bitmap = try {
            @Suppress("DEPRECATION")
            result.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON, Bitmap::class.java) ?: fromResource(result)
        } catch (e: Exception) {
            Log.w("UserShortcuts", "could not read the icon", e)
            null
        } ?: return null
        return encodePng(bitmap)
    }

    private fun fromResource(result: Intent): Bitmap? {
        @Suppress("DEPRECATION")
        val res = result.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource::class.java)
            ?: return null
        val resources = context.packageManager.getResourcesForApplication(res.packageName)
        val id = resources.getIdentifier(res.resourceName, null, null)
        if (id == 0) return null
        val drawable = resources.getDrawable(id, null) ?: return null
        if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: ICON_MAX_PX
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: ICON_MAX_PX
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            val canvas = android.graphics.Canvas(it)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
        }
    }
}
