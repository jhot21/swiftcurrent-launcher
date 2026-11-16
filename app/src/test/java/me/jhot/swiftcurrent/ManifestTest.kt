package me.jhot.swiftcurrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Reads the source manifest: package visibility is a manifest property no Robolectric call resolves faithfully. */
class ManifestTest {
    private val manifest = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml")).documentElement

    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun queryIntents(): List<Pair<Set<String>, Set<String>>> {
        val intents = manifest.getElementsByTagName("queries").item(0).let { (it as Element).getElementsByTagName("intent") }
        return (0 until intents.length).map { i ->
            val intent = intents.item(i) as Element
            fun names(tag: String) = (0 until intent.getElementsByTagName(tag).length).map {
                (intent.getElementsByTagName(tag).item(it) as Element).getAttributeNS(androidNs, "name")
            }.toSet()
            names("action") to names("category")
        }
    }

    @Test
    fun the_manifest_queries_launchable_apps() {
        assertTrue(
            queryIntents().any { (actions, categories) ->
                "android.intent.action.MAIN" in actions && "android.intent.category.LAUNCHER" in categories
            },
        )
    }

    @Test
    fun the_manifest_handles_confirm_pin_shortcut_in_an_exported_activity() {
        val activities = manifest.getElementsByTagName("activity")
        val handlers = (0 until activities.length).map { activities.item(it) as Element }.filter { a ->
            val actions = a.getElementsByTagName("action")
            (0 until actions.length).any { (actions.item(it) as Element).getAttributeNS(androidNs, "name") == "android.content.pm.action.CONFIRM_PIN_SHORTCUT" }
        }
        assertEquals(1, handlers.size)
        assertEquals("true", handlers.single().getAttributeNS(androidNs, "exported"))
    }

    @Test
    fun the_manifest_still_queries_create_shortcut_activities() {
        assertTrue(queryIntents().any { (actions, _) -> "android.intent.action.CREATE_SHORTCUT" in actions })
    }

    @Test
    fun the_manifest_asks_for_blanket_package_visibility_so_archived_apps_stay_listed() {
        val perms = manifest.getElementsByTagName("uses-permission")
        val names = (0 until perms.length).map { (perms.item(it) as Element).getAttributeNS(androidNs, "name") }
        assertTrue("android.permission.QUERY_ALL_PACKAGES" in names)
    }

    private val home: Element
        get() {
            val activities = manifest.getElementsByTagName("activity")
            return (0 until activities.length).map { activities.item(it) as Element }
                .single { it.getAttributeNS(androidNs, "name") == ".HomeActivity" }
        }

    @Test
    fun home_handles_every_configuration_change_itself_so_shell_state_is_never_rebuilt() {
        val handled = home.getAttributeNS(androidNs, "configChanges").split("|").toSet()
        val required = setOf(
            "orientation", "screenSize", "smallestScreenSize", "screenLayout", "keyboardHidden", "uiMode",
            "fontScale", "density", "locale", "layoutDirection", "fontWeightAdjustment", "colorMode",
            "navigation", "touchscreen", "mcc", "mnc",
        )
        assertEquals(emptySet<String>(), required - handled)
    }

    @Test
    fun home_is_locked_to_portrait_because_the_capacity_math_is_portrait_only() {
        assertEquals("portrait", home.getAttributeNS(androidNs, "screenOrientation"))
    }
}
