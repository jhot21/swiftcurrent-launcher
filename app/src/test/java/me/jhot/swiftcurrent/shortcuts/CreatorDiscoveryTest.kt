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
        installOnce(pkg, app)
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

    // Installing a package again resets what was registered for it, so a second helper call must not.
    private fun installOnce(pkg: String, app: ApplicationInfo) {
        val installed = try {
            pm.getPackageInfo(pkg, 0)
            true
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            false
        }
        if (!installed) shadowOf(pm).installPackage(PackageInfo().apply { packageName = pkg; applicationInfo = app })
    }

    private fun discovery() = PackageManagerCreatorDiscovery(pm, ownPackage = context.packageName, webLinkLabel = "Web link")

    /** An app that answers VIEW for any http(s) address; [onlyHost] makes it answer one site only, as a site's own app does. */
    private fun installBrowser(pkg: String, appLabel: String, onlyHost: String? = null) {
        val app = ApplicationInfo().apply {
            packageName = pkg
            nonLocalizedLabel = appLabel
            enabled = true
        }
        installOnce(pkg, app)
        shadowOf(pm).addOrUpdateActivity(
            ActivityInfo().apply {
                packageName = pkg
                name = "$pkg.Main"
                nonLocalizedLabel = appLabel
                applicationInfo = app
                exported = true
                enabled = true
            },
        )
        shadowOf(pm).addIntentFilterForActivity(
            ComponentName(pkg, "$pkg.Main"),
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme("http")
                addDataScheme("https")
                if (onlyHost != null) addDataAuthority(onlyHost, null)
            },
        )
    }

    @Test
    fun creators_listed_per_app_with_labels() {
        install("pkg.bravo.mail", "Bravo Mail", "pkg.bravo.mail.Label" to "Label")
        install("pkg.alpha.maps", "Alpha Maps", "pkg.alpha.maps.Directions" to "Directions", "pkg.alpha.maps.Friend" to "Friend's location")
        val found = discovery().creators()
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
        assertEquals(emptyList<Creator>(), discovery().creators())
    }

    @Test
    fun a_creator_resolves_to_an_icon_and_an_unknown_one_to_none() {
        install("pkg.alpha.maps", "Alpha Maps", "pkg.alpha.maps.Directions" to "Directions")
        val discovery = discovery()
        val known = discovery.creators().single()
        assertNotNull(discovery.icon(known))
        assertNull(discovery.icon(Creator("pkg.gone/pkg.gone.Nope", "Nope", "Gone")))
    }

    @Test
    fun a_browser_is_offered_a_web_link_creator_sorted_with_the_rest() {
        installBrowser("org.example.browser", "Zed Browser")
        install("pkg.alpha.maps", "Alpha Maps", "pkg.alpha.maps.Directions" to "Directions")
        assertEquals(
            listOf(
                Creator("pkg.alpha.maps/pkg.alpha.maps.Directions", "Directions", "Alpha Maps"),
                Creator.webLink("org.example.browser", "Zed Browser", "Web link"),
            ),
            discovery().creators(),
        )
    }

    @Test
    fun an_app_that_only_opens_one_site_is_not_a_browser() {
        installBrowser("com.example.news", "News", onlyHost = "example.com")
        assertEquals(emptyList<Creator>(), discovery().creators())
    }

    @Test
    fun our_own_package_is_not_offered_a_web_link() {
        installBrowser(context.packageName, "Swiftcurrent")
        assertEquals(emptyList<Creator>(), discovery().creators())
    }

    @Test
    fun each_browser_gets_its_own_row_grouped_with_its_other_creators() {
        installBrowser("org.one.browser", "One")
        installBrowser("org.two.browser", "Two")
        install("org.two.browser", "Two", "org.two.browser.Label" to "Label")
        val found = discovery().creators()
        assertEquals(
            listOf(
                Creator("org.two.browser/org.two.browser.Label", "Label", "Two"),
                Creator.webLink("org.two.browser", "Two", "Web link"),
            ),
            found.filter { it.appLabel == "Two" },
        )
        assertEquals(listOf(Creator.webLink("org.one.browser", "One", "Web link")), found.filter { it.appLabel == "One" })
    }

    @Test
    fun a_web_link_creator_resolves_to_its_browsers_icon_until_it_is_uninstalled() {
        installBrowser("org.example.browser", "Example")
        val creator = discovery().creators().single()
        assertNotNull(discovery().icon(creator))
        shadowOf(pm).removePackage("org.example.browser")
        assertNull(discovery().icon(creator))
    }

    @Test
    fun only_a_web_link_creator_is_a_web_link() {
        assertEquals(true, Creator.webLink("p", "P", "Web link").isWebLink)
        assertEquals(false, Creator("p/p.A", "A", "P").isWebLink)
    }
}
