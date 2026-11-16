package me.jhot.swiftcurrent.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNavTest {
    @Test
    fun back_goes_to_the_parent_page_then_leaves_settings() {
        val nav = SettingsNav()
        nav.open(SettingsRoute.Search)
        nav.open(SettingsRoute.Shortcuts)
        assertTrue(nav.back())
        assertEquals(SettingsRoute.Search, nav.current.value)
        assertTrue(nav.back())
        assertEquals(SettingsRoute.Root, nav.current.value)
        // At the root there is no page to go back to: the caller leaves Settings.
        assertFalse(nav.back())
        assertEquals(SettingsRoute.Root, nav.current.value)
    }

    @Test
    fun a_page_opened_directly_still_has_its_parents_behind_it() {
        val nav = SettingsNav(SettingsRoute.HiddenApps)
        assertTrue(nav.back())
        assertEquals(SettingsRoute.Apps, nav.current.value)
        assertTrue(nav.back())
        assertEquals(SettingsRoute.Root, nav.current.value)
    }

    @Test
    fun the_topics_are_home_apps_search_gestures_advanced_in_order() {
        assertEquals(
            listOf(SettingsRoute.Home, SettingsRoute.Apps, SettingsRoute.Search, SettingsRoute.Gestures, SettingsRoute.Advanced),
            SETTINGS_TOPICS,
        )
        SETTINGS_TOPICS.forEach { assertEquals(SettingsRoute.Root, it.parent) }
    }

    @Test
    fun every_route_reaches_the_root_without_a_cycle() {
        SettingsRoute.entries.forEach { r ->
            var at: SettingsRoute? = r
            var steps = 0
            while (at != SettingsRoute.Root) { at = at!!.parent; assertTrue("no root above $r", at != null && ++steps < 10) }
        }
    }
}
