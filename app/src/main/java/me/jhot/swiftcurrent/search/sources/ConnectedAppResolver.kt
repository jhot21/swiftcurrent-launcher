package me.jhot.swiftcurrent.search.sources

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri

/** The app that owns a connected-app row: its name for the button, its package so the launch goes to it alone. */
data class ConnectedApp(val name: String, val packageName: String)

/** Who can open a connected app's data row, or null when no installed, enabled app declares it. */
fun interface ConnectedAppResolver {
    fun appFor(mimetype: String, rowId: Long): ConnectedApp?
}

/**
 * The app that owns a row is the one whose activity declares that exact mimetype for opening it. Catch-all viewers
 * (a file manager's "Save as", an "open with" tool) accept any type and can be listed before the real owner, so a
 * wildcard match does not count. Default-only, as startActivity requires.
 */
class AndroidConnectedAppResolver(private val context: Context) : ConnectedAppResolver {
    override fun appFor(mimetype: String, rowId: Long): ConnectedApp? = try {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://com.android.contacts/data/$rowId"), mimetype)
        val pm = context.packageManager
        val flags = PackageManager.MATCH_DEFAULT_ONLY or PackageManager.GET_RESOLVED_FILTER
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
            .firstOrNull { it.filter?.declares(mimetype) == true }
            ?.let { found ->
                val appInfo = found.activityInfo.applicationInfo
                val name = appInfo.loadLabel(pm).toString().takeIf { it.isNotBlank() }
                name?.let { ConnectedApp(it, found.activityInfo.packageName) }
            }
    } catch (e: RuntimeException) {
        null
    }
}

/** True when the filter lists [mimetype] itself, not a wildcard that merely matches it. */
private fun IntentFilter.declares(mimetype: String) = (0 until countDataTypes()).any { getDataType(it) == mimetype }
