package me.jhot.swiftcurrent.shortcuts

import android.content.Intent
import android.content.pm.PackageManager

/**
 * An activity an app declares for `android.intent.action.CREATE_SHORTCUT`. [component] is the
 * flattened component name, [label] what the activity calls itself, [appLabel] the owning app.
 */
data class Creator(val component: String, val label: String, val appLabel: String) {
    val packageName: String get() = component.substringBefore('/')
}

interface CreatorDiscovery {
    /** Blocking package-manager query: call off the main thread. */
    fun creators(): List<Creator>
}

class PackageManagerCreatorDiscovery(
    private val packageManager: PackageManager,
    private val ownPackage: String,
) : CreatorDiscovery {
    override fun creators(): List<Creator> =
        packageManager.queryIntentActivities(Intent(Intent.ACTION_CREATE_SHORTCUT), 0)
            .mapNotNull { r ->
                val a = r.activityInfo ?: return@mapNotNull null
                if (a.packageName == ownPackage) return@mapNotNull null
                val appLabel = a.applicationInfo?.let { packageManager.getApplicationLabel(it).toString() } ?: a.packageName
                val label = r.loadLabel(packageManager).toString().ifBlank { appLabel }
                Creator("${a.packageName}/${a.name}", label, appLabel)
            }
            .distinct()
            .sortedWith(
                compareBy<Creator, String>(String.CASE_INSENSITIVE_ORDER) { it.appLabel }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label },
            )
}
