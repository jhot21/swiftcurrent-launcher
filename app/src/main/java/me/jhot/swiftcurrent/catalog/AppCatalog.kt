package me.jhot.swiftcurrent.catalog

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * An installed app. [label] is what the owner sees (their own name for it, when they gave one);
 * [systemLabel] is what the system calls it, kept so search can still find it by that and a rename
 * can be reset to it.
 */
data class AppInfo(
    val component: String,
    val label: String,
    val packageName: String,
    val systemLabel: String = label,
    /** Archived by the system (Android 15): the app is gone but its entry stays, and a tap downloads it again. */
    val archived: Boolean = false,
)

/** An archived app keeps its launcher entry but is no longer installed, so the system clears FLAG_INSTALLED. */
internal fun isArchived(applicationFlags: Int): Boolean = applicationFlags and ApplicationInfo.FLAG_INSTALLED == 0

interface AppCatalog {
    fun apps(): List<AppInfo>
    val changes: Flow<Unit>
}

class LauncherAppsCatalog(context: Context) : AppCatalog {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    override fun apps(): List<AppInfo> =
        launcherApps.getActivityList(null, Process.myUserHandle()).map {
            AppInfo(
                it.componentName.flattenToString(), it.label.toString(), it.applicationInfo.packageName,
                archived = isArchived(it.applicationInfo.flags),
            )
        }

    override val changes: Flow<Unit> = callbackFlow {
        val cb = object : LauncherApps.Callback() {
            private fun ping() { trySend(Unit) }
            override fun onPackageAdded(packageName: String, user: UserHandle) = ping()
            override fun onPackageChanged(packageName: String, user: UserHandle) = ping()
            override fun onPackageRemoved(packageName: String, user: UserHandle) = ping()
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = ping()
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = ping()
        }
        launcherApps.registerCallback(cb)
        awaitClose { launcherApps.unregisterCallback(cb) }
    }
}
