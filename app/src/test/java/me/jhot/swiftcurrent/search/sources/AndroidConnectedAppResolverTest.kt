package me.jhot.swiftcurrent.search.sources

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

private const val SIGNAL_VOICE = "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.call"

@RunWith(RobolectricTestRunner::class)
class AndroidConnectedAppResolverTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val resolver = AndroidConnectedAppResolver(context)

    /** An app with one activity that opens [mimetypes] (a wildcard type is a catch-all viewer such as a file manager's "Save as"). */
    private fun app(pkg: String, label: String, vararg mimetypes: String) {
        val pm = shadowOf(context.packageManager)
        val appInfo = ApplicationInfo().apply { packageName = pkg; enabled = true; nonLocalizedLabel = label }
        pm.installPackage(PackageInfo().apply { packageName = pkg; applicationInfo = appInfo })
        val info = ActivityInfo().apply {
            packageName = pkg; name = "$pkg.Open"; nonLocalizedLabel = label; exported = true; enabled = true; applicationInfo = appInfo
        }
        pm.addOrUpdateActivity(info)
        pm.addIntentFilterForActivity(
            ComponentName(pkg, info.name),
            IntentFilter(Intent.ACTION_VIEW).apply { addCategory(Intent.CATEGORY_DEFAULT); mimetypes.forEach { addDataType(it) } },
        )
    }

    @Test
    fun a_catch_all_viewer_listed_first_does_not_become_the_app() {
        app("me.example.files", "Save as", "*/*")
        app("org.example.signal", "Signal", SIGNAL_VOICE)
        assertEquals(ConnectedApp("Signal", "org.example.signal"), resolver.appFor(SIGNAL_VOICE, 1))
    }

    @Test
    fun a_row_only_a_catch_all_viewer_opens_has_no_app() {
        app("me.example.files", "Save as", "*/*")
        assertNull(resolver.appFor(SIGNAL_VOICE, 1))
    }

    @Test
    fun a_row_nothing_opens_has_no_app() {
        assertNull(resolver.appFor(SIGNAL_VOICE, 1))
    }
}
