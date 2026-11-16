package me.jhot.swiftcurrent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesTest {
    private val a = Entry("pkg.a/.Main", Target.App("pkg.a/.Main"), customLabel = "Alpha")
    private val folder = Entry("fold", Target.Folder, popup = Popup(listOf(PopupRow.Item(Entry("pkg.b/.Main", Target.App("pkg.b/.Main"))))))
    private val data = LauncherData(favorites = listOf(a, folder))

    @Test
    fun an_app_is_added_at_the_end_the_way_the_editor_adds_it() {
        val out = Favorites.add(data, "pkg.c/.Main")
        assertEquals(listOf("pkg.a/.Main", "fold", "pkg.c/.Main"), out.favorites.map { it.id })
        assertEquals(Target.App("pkg.c/.Main"), out.favorites.last().target)
        assertTrue(Favorites.isFavorite(out, "pkg.c/.Main"))
    }

    private val custom = Popup(listOf(PopupRow.Item(Entry("s1", Target.Shortcut("pkg.c/k")))))

    @Test
    fun starring_an_app_carries_its_custom_pop_up_onto_the_favorite() {
        val withPopup = data.copy(appPopups = mapOf("pkg.c/.Main" to custom))
        val out = Favorites.add(withPopup, "pkg.c/.Main")
        assertEquals(custom, out.favorites.last().popup)
        // One place holds it, so the two cannot drift.
        assertTrue(out.appPopups.isEmpty())
    }

    @Test
    fun unstarring_an_app_keeps_its_pop_up_for_its_sheet() {
        val starred = data.copy(favorites = data.favorites + Entry("pkg.c/.Main", Target.App("pkg.c/.Main"), popup = custom))
        val out = Favorites.remove(starred, "pkg.c/.Main")
        assertEquals(custom, out.appPopups["pkg.c/.Main"])
        assertEquals(custom, Favorites.menuEntry(out, "pkg.c/.Main").popup)
    }

    @Test
    fun unstarring_an_app_with_no_pop_up_stores_none() {
        assertTrue(Favorites.remove(data, "pkg.a/.Main").appPopups.isEmpty())
    }

    @Test
    fun adding_a_favorite_that_is_already_one_changes_nothing() {
        assertSame(data, Favorites.add(data, "pkg.a/.Main"))
    }

    @Test
    fun removing_takes_the_app_out_and_leaves_the_rest_in_order() {
        val out = Favorites.remove(data, "pkg.a/.Main")
        assertEquals(listOf("fold"), out.favorites.map { it.id })
        assertFalse(Favorites.isFavorite(out, "pkg.a/.Main"))
    }

    @Test
    fun removing_something_that_is_not_a_favorite_changes_nothing() {
        assertSame(data, Favorites.remove(data, "pkg.zzz/.Main"))
    }

    @Test
    fun an_app_only_inside_a_folder_is_not_a_top_level_favorite() {
        assertFalse(Favorites.isFavorite(data, "pkg.b/.Main"))
        assertSame(data, Favorites.remove(data, "pkg.b/.Main"))
    }

    @Test
    fun the_menu_entry_of_a_favorite_is_the_favorite_and_of_any_other_app_a_stand_in_with_its_stored_popup() {
        val fav = Entry("fav-a", Target.App("pkg.a/.Main"), popup = Popup(emptyList()))
        val stored = Popup(listOf(PopupRow.OwnShortcuts("pkg.b")))
        val data = LauncherData(favorites = listOf(fav), appPopups = mapOf("pkg.b/.Main" to stored, "pkg.a/.Main" to Popup(listOf(PopupRow.OwnShortcuts("x")))))
        assertEquals(fav, Favorites.menuEntry(data, "pkg.a/.Main"))
        assertEquals(Entry("pkg.b/.Main", Target.App("pkg.b/.Main"), popup = stored), Favorites.menuEntry(data, "pkg.b/.Main"))
        assertEquals(Entry("pkg.c/.Main", Target.App("pkg.c/.Main")), Favorites.menuEntry(data, "pkg.c/.Main"))
    }
}
