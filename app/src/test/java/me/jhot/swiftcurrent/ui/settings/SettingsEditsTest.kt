package me.jhot.swiftcurrent.ui.settings

import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.HiddenContactAction
import me.jhot.swiftcurrent.core.LauncherData
import me.jhot.swiftcurrent.core.Popup
import me.jhot.swiftcurrent.core.PopupRow
import me.jhot.swiftcurrent.core.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsEditsTest {
    private val app = Entry("a", Target.App("pkg.a/.Main"))
    private val inner = Entry("f-inner", Target.Folder, customLabel = "Inner", popup = Popup(listOf(PopupRow.Item(app))))
    private val doomed = Entry("f-doomed", Target.Folder, customLabel = "Doomed", popup = Popup(listOf(PopupRow.Item(app))))

    @Test
    fun hiding_and_unhiding_an_app_changes_only_the_hidden_set() {
        val d = LauncherData(hiddenApps = setOf("x"))
        val hidden = SettingsEdits.setHidden(d, "pkg.a/.Main", true)
        assertEquals(setOf("x", "pkg.a/.Main"), hidden.hiddenApps)
        assertEquals(setOf("x"), SettingsEdits.setHidden(hidden, "pkg.a/.Main", false).hiddenApps)
        assertEquals(d.copy(hiddenApps = hidden.hiddenApps), hidden)
    }

    @Test
    fun hiding_a_contact_method_twice_stores_it_once_and_showing_it_removes_it() {
        val h = HiddenContactAction("contact:1", "contact:1|app|x.meet|a@example.com", "Alex Example", "Video call on Meet")
        val once = SettingsEdits.hideContactAction(SettingsEdits.hideContactAction(LauncherData(), h), h.copy(label = "again"))
        assertEquals(listOf(h), once.settings.hiddenContactActions)
        assertEquals(emptyList<HiddenContactAction>(), SettingsEdits.showContactAction(once, h.contact, h.action).settings.hiddenContactActions)
    }

    @Test
    fun the_switches_write_their_own_setting_only() {
        val d = LauncherData()
        assertFalse(SettingsEdits.setSortByUsage(d, false).settings.sortByUsage)
        assertFalse(SettingsEdits.setHaptics(d, false).settings.haptics)
        assertTrue(SettingsEdits.setHaptics(d, false).settings.sortByUsage)
    }

    @Test
    fun the_search_switches_write_their_own_setting_only() {
        val d = LauncherData()
        assertEquals(d.settings.copy(appsFirst = false), SettingsEdits.setAppsFirst(d, false).settings)
        assertEquals(d.settings.copy(contactsFirst = false), SettingsEdits.setContactsFirst(d, false).settings)
        assertEquals(d.settings.copy(learnFromPicks = false), SettingsEdits.setLearnFromPicks(d, false).settings)
        val off = d.copy(settings = d.settings.copy(appsFirst = false, contactsFirst = false, learnFromPicks = false))
        assertTrue(SettingsEdits.setAppsFirst(off, true).settings.appsFirst)
        assertTrue(SettingsEdits.setContactsFirst(off, true).settings.contactsFirst)
        assertTrue(SettingsEdits.setLearnFromPicks(off, true).settings.learnFromPicks)
    }

    @Test
    fun deleting_a_folder_removes_it_from_the_library_favorites_and_every_nested_popup() {
        val holder = Entry("holder", Target.App("pkg.h/.Main"), popup = Popup(listOf(PopupRow.OwnShortcuts("pkg.h"), PopupRow.Item(doomed), PopupRow.Item(app))))
        val outer = Entry("f-outer", Target.Folder, customLabel = "Outer", popup = Popup(listOf(PopupRow.Item(inner.copy(popup = Popup(listOf(PopupRow.Item(doomed))))))))
        val d = LauncherData(favorites = listOf(app, doomed, holder, outer), folders = listOf(doomed, outer, inner))

        val out = SettingsEdits.deleteFolder(d, "f-doomed")

        assertEquals(listOf("f-outer", "f-inner"), out.folders.map { it.id })
        assertEquals(listOf("a", "holder", "f-outer"), out.favorites.map { it.id })
        // The holder keeps its own-shortcuts row and the app; only the folder row is gone.
        assertEquals(listOf(PopupRow.OwnShortcuts("pkg.h"), PopupRow.Item(app)), out.favorites[1].popup!!.rows)
        // Two levels down inside another folder's pop-up.
        val nested = (out.favorites[2].popup!!.rows.single() as PopupRow.Item).entry
        assertEquals(emptyList<PopupRow>(), nested.popup!!.rows)
    }

    @Test
    fun deleting_an_app_with_the_same_id_as_a_folder_does_nothing() {
        val d = LauncherData(favorites = listOf(app))
        assertEquals(d, SettingsEdits.deleteFolder(d, "a"))
    }

    @Test
    fun the_two_haptic_strengths_are_set_independently() {
        val d = me.jhot.swiftcurrent.core.LauncherData()
        val a = SettingsEdits.setScrubHaptic(d, 100)
        assertEquals(100, a.settings.scrubHaptic)
        assertEquals(me.jhot.swiftcurrent.core.HapticStrength.DEFAULT, a.settings.gestureHaptic)
        val b = SettingsEdits.setGestureHaptic(a, 0)
        assertEquals(100, b.settings.scrubHaptic)
        assertEquals(0, b.settings.gestureHaptic)
    }

    @Test
    fun a_strength_out_of_range_is_brought_back_into_it() {
        val d = me.jhot.swiftcurrent.core.LauncherData()
        assertEquals(0, SettingsEdits.setScrubHaptic(d, -5).settings.scrubHaptic)
        assertEquals(100, SettingsEdits.setGestureHaptic(d, 400).settings.gestureHaptic)
    }
}
