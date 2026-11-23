package me.jhot.swiftcurrent.shortcuts

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CreatorDiscoveryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pm = context.packageManager

    private fun install(pkg: String, appLabel: String, vararg creators: Pair<String, String>) {
        val app = ApplicationInfo().apply {
            packageName = pkg
            nonLocalizedLabel = appLabel
            enabled = true
        }
        shadowOf(pm).installPackage(PackageInfo().apply { packageName = pkg; applicationInfo = app })
        creators.forEach { (cls, label) ->
            val component = ComponentName(pkg, cls)
            shadowOf(pm).addOrUpdateActivity(
                ActivityInfo().apply {
                    packageName = pkg
                    name = cls
                    nonLocalizedLabel = label
                    applicationInfo = app
                    exported = true
                    enabled = true
                },
            )
            shadowOf(pm).addIntentFilterForActivity(
                component,
                IntentFilter(Intent.ACTION_CREATE_SHORTCUT).apply { addCategory(Intent.CATEGORY_DEFAULT) },
            )
        }
    }

    @Test
    fun creators_listed_per_app_with_labels() {
        install("pkg.bravo.mail", "Bravo Mail", "pkg.bravo.mail.Label" to "Label")
        install("pkg.alpha.maps", "Alpha Maps", "pkg.alpha.maps.Directions" to "Directions", "pkg.alpha.maps.Friend" to "Friend's location")
        val found = PackageManagerCreatorDiscovery(pm, ownPackage = context.packageName).creators()
        // Grouped by app (alphabetical), then by the creator's own label.
        assertEquals(
            listOf(
                Creator("pkg.alpha.maps/pkg.alpha.maps.Directions", "Directions", "Alpha Maps"),
                Creator("pkg.alpha.maps/pkg.alpha.maps.Friend", "Friend's location", "Alpha Maps"),
                Creator("pkg.bravo.mail/pkg.bravo.mail.Label", "Label", "Bravo Mail"),
            ),
            found,
        )
    }

    @Test
    fun our_own_package_is_not_offered_as_a_creator() {
        install(context.packageName, "Swiftcurrent", "x.Own" to "Own")
        assertEquals(emptyList<Creator>(), PackageManagerCreatorDiscovery(pm, context.packageName).creators())
    }

    @Test
    fun a_creator_resolves_to_an_icon_and_an_unknown_one_to_none() {
        install("pkg.alpha.maps", "Alpha Maps", "pkg.alpha.maps.Directions" to "Directions")
        val discovery = PackageManagerCreatorDiscovery(pm, context.packageName)
        val known = discovery.creators().single()
        assertNotNull(discovery.icon(known))
        assertNull(discovery.icon(Creator("pkg.gone/pkg.gone.Nope", "Nope", "Gone")))
    }
}
