package me.jhot.swiftcurrent.shortcuts

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.net.Uri

enum class CreatorKind {
    /** An activity another app declares for `android.intent.action.CREATE_SHORTCUT`. */
    External,

    /** Ours: a web link made in an in-app form, bound to one browser. Never launched as an activity. */
    WebLink,

    /** Ours: a Google Chat conversation link, bound to the Chat-capable app that opens it. Never launched as an activity. */
    ChatLink,
}

/**
 * Something the Add shortcut sheet offers. For an [CreatorKind.External] creator, [component] is the
 * flattened component name of the activity, [label] what it calls itself, [appLabel] the owning app.
 * A [CreatorKind.WebLink] creator carries the browser in a synthetic [component] and is built with [webLink].
 */
data class Creator(
    val component: String,
    val label: String,
    val appLabel: String,
    val kind: CreatorKind = CreatorKind.External,
) {
    val isWebLink: Boolean get() = kind == CreatorKind.WebLink
    val isChatLink: Boolean get() = kind == CreatorKind.ChatLink

    /** Made in one of our own forms rather than by another app's activity. */
    val isLink: Boolean get() = kind != CreatorKind.External

    val packageName: String
        get() = when (kind) {
            CreatorKind.WebLink -> component.removePrefix(WEB_LINK_PREFIX)
            CreatorKind.ChatLink -> component.removePrefix(CHAT_LINK_PREFIX)
            CreatorKind.External -> component.substringBefore('/')
        }

    companion object {
        private const val WEB_LINK_PREFIX = "weblink:"
        private const val CHAT_LINK_PREFIX = "chatlink:"

        fun webLink(browserPackage: String, browserLabel: String, label: String) =
            Creator("$WEB_LINK_PREFIX$browserPackage", label, browserLabel, CreatorKind.WebLink)

        fun chatLink(appPackage: String, appLabel: String, label: String) =
            Creator("$CHAT_LINK_PREFIX$appPackage", label, appLabel, CreatorKind.ChatLink)

        /** The apps that answer a `chat.google.com` conversation link: Gmail, and the standalone Chat app. */
        val CHAT_PACKAGES = listOf("com.google.android.gm", "com.google.android.apps.dynamite")
    }
}

interface CreatorDiscovery {
    /** Blocking package-manager query: call off the main thread. */
    fun creators(): List<Creator>

    /**
     * The creator's own icon, which the platform falls back to its app's icon for (a web link uses its
     * browser's); null when the component or browser is gone. Blocking: call off the main thread.
     */
    fun icon(creator: Creator): Drawable?
}

class PackageManagerCreatorDiscovery(
    private val packageManager: PackageManager,
    private val ownPackage: String,
    private val webLinkLabel: String,
    private val chatLinkLabel: String = "Google Chat link",
    // A seam so a test can see the flags a query was made with; the platform's answer cannot be faked by flags.
    private val queryActivities: (Intent, Int) -> List<ResolveInfo> = { intent, flags -> packageManager.queryIntentActivities(intent, flags) },
) : CreatorDiscovery {
    override fun creators(): List<Creator> =
        (external() + webLinks() + chatLinks())
            .distinct()
            .sortedWith(
                compareBy<Creator, String>(String.CASE_INSENSITIVE_ORDER) { it.appLabel }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label },
            )

    private fun external(): List<Creator> =
        queryActivities(Intent(Intent.ACTION_CREATE_SHORTCUT), 0)
            .mapNotNull { r ->
                val a = r.activityInfo ?: return@mapNotNull null
                if (a.packageName == ownPackage) return@mapNotNull null
                val appLabel = a.applicationInfo?.let { packageManager.getApplicationLabel(it).toString() } ?: a.packageName
                val label = r.loadLabel(packageManager).toString().ifBlank { appLabel }
                Creator("${a.packageName}/${a.name}", label, appLabel)
            }

    // A browser answers VIEW for any web address. A URI with no host only matches filters that name no
    // host, which leaves out apps that open one site's links. MATCH_ALL because a plain web query is
    // narrowed to the default browser when one is set, which would hide every other browser.
    private fun webLinks(): List<Creator> =
        queryActivities(
            Intent(Intent.ACTION_VIEW, Uri.parse("http://")).addCategory(Intent.CATEGORY_BROWSABLE),
            PackageManager.MATCH_ALL,
        )
            .mapNotNull { r ->
                val a = r.activityInfo ?: return@mapNotNull null
                if (a.packageName == ownPackage) return@mapNotNull null
                val appLabel = a.applicationInfo?.let { packageManager.getApplicationLabel(it).toString() } ?: a.packageName
                Creator.webLink(a.packageName, appLabel, webLinkLabel)
            }

    // Only an installed app that really answers a Chat link is offered, so the row never leads to a dead end.
    private fun chatLinks(): List<Creator> = Creator.CHAT_PACKAGES.mapNotNull { pkg ->
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://chat.google.com/room/AAAAAAAAAAA"))
            .addCategory(Intent.CATEGORY_BROWSABLE).setPackage(pkg)
        if (queryActivities(probe, 0).isEmpty()) return@mapNotNull null
        val appLabel = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            return@mapNotNull null
        }
        Creator.chatLink(pkg, appLabel, chatLinkLabel)
    }

    override fun icon(creator: Creator): Drawable? = try {
        if (creator.isLink) {
            packageManager.getApplicationInfo(creator.packageName, 0).loadIcon(packageManager)
        } else {
            val component = ComponentName.unflattenFromString(creator.component)
            component?.let { packageManager.getActivityInfo(it, 0).loadIcon(packageManager) }
        }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}
