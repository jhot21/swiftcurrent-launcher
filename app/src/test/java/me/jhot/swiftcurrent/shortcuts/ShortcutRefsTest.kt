package me.jhot.swiftcurrent.shortcuts

import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.LauncherData
import me.jhot.swiftcurrent.core.Popup
import me.jhot.swiftcurrent.core.PopupRow
import me.jhot.swiftcurrent.core.Settings
import me.jhot.swiftcurrent.core.Target
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutRefsTest {
    private val gone = Target.Shortcut("pkg.a/pin1")
    private val keep = Target.Shortcut("pkg.a/other")

    private fun item(id: String, t: Target) = PopupRow.Item(Entry(id, t))

    private fun data(): LauncherData {
        val nested = Entry("nested", Target.Folder, popup = Popup(listOf(item("n-gone", gone), item("n-keep", keep))))
        val favoriteWithPopup = Entry(
            "app", Target.App("p/.M"),
            popup = Popup(listOf(PopupRow.OwnShortcuts("p"), item("a-gone", gone), item("a-keep", keep), PopupRow.Item(nested))),
        )
        return LauncherData(
            favorites = listOf(Entry("fav-gone", gone), Entry("fav-keep", keep), favoriteWithPopup),
            folders = listOf(Entry("lib", Target.Folder, popup = Popup(listOf(item("l-gone", gone), item("l-keep", keep))))),
            appPopups = mapOf("q/.M" to Popup(listOf(item("p-gone", gone), item("p-keep", keep)))),
            settings = Settings(disabledShortcuts = setOf(gone.key, keep.key)),
        )
    }

    @Test
    fun a_shortcut_is_removed_from_favorites_folders_nested_popups_app_popups_and_the_disabled_set() {
        val d = data().withoutShortcut(gone.key)
        assertEquals(listOf("fav-keep", "app"), d.favorites.map { it.id })
        val rows = d.favorites.single { it.id == "app" }.popup!!.rows
        assertEquals(listOf("OwnShortcuts", "a-keep", "nested"), rows.map { if (it is PopupRow.Item) it.entry.id else "OwnShortcuts" })
        val nested = (rows.last() as PopupRow.Item).entry.popup!!.rows
        assertEquals(listOf("n-keep"), nested.map { (it as PopupRow.Item).entry.id })
        assertEquals(listOf("l-keep"), d.folders.single().popup!!.rows.map { (it as PopupRow.Item).entry.id })
        assertEquals(listOf("p-keep"), d.appPopups.getValue("q/.M").rows.map { (it as PopupRow.Item).entry.id })
        assertEquals(setOf(keep.key), d.settings.disabledShortcuts)
    }

    @Test
    fun a_key_that_is_nowhere_leaves_the_data_unchanged() {
        assertEquals(data(), data().withoutShortcut("pkg.a/never"))
    }

    @Test
    fun own_shortcut_rows_are_kept() {
        val d = data().withoutShortcut(gone.key)
        assertEquals(PopupRow.OwnShortcuts("p"), d.favorites.single { it.id == "app" }.popup!!.rows.first())
    }
}
