package me.jhot.swiftcurrent.shortcuts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import me.jhot.swiftcurrent.search.LaunchOutcome

/** [key] is "package/id", the handle [ShortcutProvider.launch] takes. */
data class ShortcutEntry(
    val key: String,
    val appLabel: String,
    val packageName: String,
    val id: String,
    val label: String,
    /** Pinned for this launcher, by the owner or by a pin request: the ones it may delete. */
    val pinned: Boolean = false,
)

sealed interface ShortcutAccess {
    data object Granted : ShortcutAccess

    /** Android only shows launcher shortcuts to the default home app. */
    data object NotDefaultHome : ShortcutAccess
}

/**
 * [access], [entries] and [launch] are blocking binder IPC (and [entries] a package lookup per app).
 * Call them off the main thread.
 */
interface ShortcutProvider {
    fun access(): ShortcutAccess
    fun entries(): List<ShortcutEntry>
    fun launch(key: String): LaunchOutcome

    /** Removes this launcher's pin on [key]. Blocking IPC, like [launch]. */
    fun unpin(key: String): LaunchOutcome
    val changes: Flow<Unit>
}

/** Null when the shortcut cannot be shown: disabled, or no usable label. */
fun shortcutEntryOrNull(
    appLabel: String,
    packageName: String,
    id: String,
    shortLabel: CharSequence?,
    longLabel: CharSequence?,
    enabled: Boolean,
    pinned: Boolean = false,
): ShortcutEntry? {
    if (!enabled) return null
    val label = listOf(shortLabel, longLabel).firstNotNullOfOrNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
        ?: return null
    return ShortcutEntry("$packageName/$id", appLabel, packageName, id, label, pinned)
}

/** The pin set to hand `LauncherApps.pinShortcuts` once [removeId] is unpinned. */
fun pinsAfterRemoving(pinnedIds: Collection<String>, removeId: String): List<String> =
    pinnedIds.filter { it != removeId }

/** User-readable launch failures, passed in so the provider stays free of resources. */
class ShortcutMessages(val gone: String, val unavailable: String, val needsDefaultHome: String)

class LauncherAppsShortcutProvider(context: Context, private val messages: ShortcutMessages) : ShortcutProvider {
    private val packageManager = context.packageManager
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val user = Process.myUserHandle()

    override fun access() =
        if (launcherApps.hasShortcutHostPermission()) ShortcutAccess.Granted else ShortcutAccess.NotDefaultHome

    override fun entries(): List<ShortcutEntry> {
        if (access() != ShortcutAccess.Granted) return emptyList()
        val query = LauncherApps.ShortcutQuery().setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                // Gmail publishes each Google Chat conversation as a long-lived shortcut that Android
                // only caches (never dynamic or pinned), so without this flag they are invisible.
                LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED,
        )
        val shortcuts: List<ShortcutInfo> = try {
            launcherApps.getShortcuts(query, user)
        } catch (_: SecurityException) {
            null // permission was lost between the check and the call
        } ?: return emptyList()
        val appLabels = HashMap<String, String>()
        return shortcuts.mapNotNull { s ->
            val appLabel = appLabels.getOrPut(s.`package`) {
                runCatching {
                    packageManager.getApplicationLabel(packageManager.getApplicationInfo(s.`package`, 0)).toString()
                }.getOrDefault(s.`package`)
            }
            shortcutEntryOrNull(appLabel, s.`package`, s.id, s.shortLabel, s.longLabel, s.isEnabled, s.isPinned)
        }
    }

    /**
     * Blocking IPC. [me.jhot.swiftcurrent.search.Action.perform] is not suspending, so the search
     * screen must invoke it off the main thread.
     */
    override fun launch(key: String): LaunchOutcome {
        val pkg = key.substringBefore('/')
        val id = key.substringAfter('/')
        return try {
            launcherApps.startShortcut(pkg, id, null, null, user)
            LaunchOutcome.Launched
        } catch (e: ActivityNotFoundException) {
            LaunchOutcome.Failed(messages.gone)
        } catch (e: IllegalStateException) {
            LaunchOutcome.Failed(messages.unavailable)
        } catch (e: SecurityException) {
            LaunchOutcome.Failed(messages.needsDefaultHome)
        }
    }

    override fun unpin(key: String): LaunchOutcome {
        val pkg = key.substringBefore('/')
        val id = key.substringAfter('/')
        return try {
            val query = LauncherApps.ShortcutQuery().setPackage(pkg).setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            val pinned = launcherApps.getShortcuts(query, user).orEmpty().map { it.id }
            launcherApps.pinShortcuts(pkg, pinsAfterRemoving(pinned, id), user)
            LaunchOutcome.Launched
        } catch (e: IllegalStateException) {
            LaunchOutcome.Failed(messages.unavailable)
        } catch (e: SecurityException) {
            LaunchOutcome.Failed(messages.needsDefaultHome)
        }
    }

    override val changes: Flow<Unit> = callbackFlow {
        val cb = object : LauncherApps.Callback() {
            private fun ping() { trySend(Unit) }
            override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<ShortcutInfo>, user: android.os.UserHandle) = ping()
            override fun onPackageAdded(packageName: String, user: android.os.UserHandle) = ping()
            override fun onPackageChanged(packageName: String, user: android.os.UserHandle) = ping()
            override fun onPackageRemoved(packageName: String, user: android.os.UserHandle) = ping()
            override fun onPackagesAvailable(packageNames: Array<out String>, user: android.os.UserHandle, replacing: Boolean) = ping()
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: android.os.UserHandle, replacing: Boolean) = ping()
        }
        launcherApps.registerCallback(cb)
        awaitClose { launcherApps.unregisterCallback(cb) }
    }
}
