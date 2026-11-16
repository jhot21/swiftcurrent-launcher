package me.jhot.swiftcurrent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RenamesTest {
    private val app = Entry("a", Target.App("pkg.a/.Main"))
    private val other = Entry("b", Target.App("pkg.b/.Main"))
    private fun folder(id: String, label: String? = null, vararg inside: Entry) =
        Entry(id, Target.Folder, label, Popup(inside.map { PopupRow.Item(it) }))

    private fun label(d: LauncherData, id: String) = d.favorites.first { it.id == id }.customLabel

    @Test
    fun renaming_an_app_gives_the_app_one_name_and_leaves_other_apps_alone() {
        val d = LauncherData(favorites = listOf(app, other))
        val out = Renames.apply(d, "a", "Browser")
        assertEquals(mapOf("pkg.a/.Main" to "Browser"), out.appNames)
        // The favorites themselves carry no copy of it.
        assertEquals(d.favorites, out.favorites)
        assertEquals("Browser", Renames.appNameOf(out, "pkg.a/.Main"))
        assertNull(Renames.appNameOf(out, "pkg.b/.Main"))
    }

    @Test
    fun the_label_is_trimmed() {
        assertEquals("Browser", Renames.appNameOf(Renames.apply(LauncherData(favorites = listOf(app)), "a", "  Browser \t"), "pkg.a/.Main"))
    }

    @Test
    fun a_blank_or_null_label_resets_to_the_system_name() {
        val d = LauncherData(favorites = listOf(app), appNames = mapOf("pkg.a/.Main" to "Browser"))
        for (reset in listOf("", "   \n ", null)) {
            val out = Renames.apply(d, "a", reset)
            assertNull(Renames.appNameOf(out, "pkg.a/.Main"))
            assertEquals(emptyMap<String, String>(), out.appNames)
        }
    }

    @Test
    fun an_over_long_label_is_cut_to_the_maximum() {
        val out = Renames.apply(LauncherData(favorites = listOf(app)), "a", "x".repeat(Renames.MAX_LENGTH + 25))
        assertEquals("x".repeat(Renames.MAX_LENGTH), Renames.appNameOf(out, "pkg.a/.Main"))
    }

    @Test
    fun cutting_never_splits_a_surrogate_pair() {
        val emoji = "😀"
        val s = Renames.sanitize("x".repeat(Renames.MAX_LENGTH - 1) + emoji)!!
        assertEquals("x".repeat(Renames.MAX_LENGTH - 1), s)
    }

    @Test
    fun control_characters_and_newlines_are_stripped_and_everything_else_is_kept() {
        assertEquals("Two Lines", Renames.sanitize("Two\n Lines\u0000"))
        assertEquals("Café ☃ 日本", Renames.sanitize("Café ☃ 日本"))
    }

    @Test
    fun a_rename_to_the_same_value_changes_nothing() {
        val d = LauncherData(favorites = listOf(app), appNames = mapOf("pkg.a/.Main" to "Browser"))
        assertSame(d, Renames.apply(d, "a", " Browser "))
    }

    @Test
    fun two_apps_may_share_a_name() {
        val d = Renames.apply(Renames.apply(LauncherData(favorites = listOf(app, other)), "a", "Same"), "b", "Same")
        assertEquals(mapOf("pkg.a/.Main" to "Same", "pkg.b/.Main" to "Same"), d.appNames)
    }

    @Test
    fun an_unknown_id_changes_nothing() {
        val d = LauncherData(favorites = listOf(app))
        assertSame(d, Renames.apply(d, "zzz", "X"))
    }

    @Test
    fun renaming_a_folder_updates_the_library_the_favorite_and_never_its_pop_up() {
        val f = folder("f", null, app)
        val d = LauncherData(favorites = listOf(f, other), folders = listOf(f))
        val out = Renames.apply(d, "f", "Work")
        assertEquals("Work", out.favorites[0].customLabel)
        assertEquals("Work", out.folders[0].customLabel)
        assertEquals(f.popup, out.favorites[0].popup)
        assertEquals(f.popup, out.folders[0].popup)
        assertNull(out.favorites[1].customLabel)
    }

    @Test
    fun a_folder_copy_nested_in_another_pop_up_reads_the_same() {
        val inner = folder("f")
        val outer = folder("g", null, inner)
        val d = LauncherData(favorites = listOf(inner, outer), folders = listOf(inner, outer))
        val out = Renames.apply(d, "f", "Work")
        val nestedInFavorite = (out.favorites[1].popup!!.rows[0] as PopupRow.Item).entry
        val nestedInLibrary = (out.folders[1].popup!!.rows[0] as PopupRow.Item).entry
        assertEquals("Work", nestedInFavorite.customLabel)
        assertEquals("Work", nestedInLibrary.customLabel)
        assertNull(out.favorites[1].customLabel)
    }

    @Test
    fun a_folder_only_in_the_library_can_be_renamed() {
        val f = folder("f")
        val out = Renames.apply(LauncherData(folders = listOf(f)), "f", "Spare")
        assertEquals("Spare", out.folders[0].customLabel)
    }

    @Test
    fun an_app_nested_only_in_a_pop_up_is_renamed_by_name_not_by_entry() {
        val host = folder("f", null, app)
        val d = LauncherData(favorites = listOf(host), folders = listOf(host))
        val out = Renames.apply(d, "a", "Browser")
        assertEquals("Browser", Renames.appNameOf(out, "pkg.a/.Main"))
        assertNull((out.favorites[0].popup!!.rows[0] as PopupRow.Item).entry.customLabel)
    }

    @Test
    fun an_app_that_is_a_favorite_and_also_in_a_pop_up_has_one_name_in_both_places() {
        val host = folder("f", null, app)
        val d = LauncherData(favorites = listOf(app, host), folders = listOf(host))
        val top = Renames.apply(d, "a", "Top")
        assertEquals("Top", Renames.appNameOf(top, "pkg.a/.Main"))
        // Renaming it from inside the pop-up is the same rename: the app is named once.
        val nested = Renames.apply(d, "a", "Nested", within = "f")
        assertEquals("Nested", Renames.appNameOf(nested, "pkg.a/.Main"))
        assertEquals(mapOf("pkg.a/.Main" to "Nested"), nested.appNames)
    }

    @Test
    fun a_missing_or_hidden_app_can_still_be_renamed() {
        val d = LauncherData(favorites = listOf(app), hiddenApps = setOf("pkg.a/.Main"))
        assertEquals("Gone", Renames.appNameOf(Renames.apply(d, "a", "Gone"), "pkg.a/.Main"))
    }

    @Test
    fun custom_label_of_reads_the_stored_label_in_the_same_scope() {
        val host = folder("f", "Host", app.copy(customLabel = "Inner"))
        val d = LauncherData(favorites = listOf(host), folders = listOf(host))
        assertEquals("Host", Renames.customLabelOf(d, "f"))
        // An app's name is the app's, wherever it is read from: a name an older version kept on the entry still counts.
        assertEquals("Inner", Renames.customLabelOf(d, "a", within = "f"))
        assertEquals("Inner", Renames.customLabelOf(d, "a"))
        assertNull(Renames.customLabelOf(d, "nope"))
    }

    // An app that is no favorite keeps its pop-up in appPopups, with no favorite entry to hold it.
    private val shortcut = Entry("s1", Target.Shortcut("pkg.a/k"))
    private fun popupOf(vararg rows: Entry) = Popup(rows.map { PopupRow.Item(it) })
    private fun storedRows(d: LauncherData, component: String) =
        d.appPopups.getValue(component).rows.map { (it as PopupRow.Item).entry }

    @Test
    fun a_shortcut_renamed_in_a_non_favorites_pop_up_is_stored_there() {
        val d = LauncherData(appPopups = mapOf("pkg.a/.Main" to popupOf(shortcut)))
        val out = Renames.apply(d, "s1", "Mine", within = "pkg.a/.Main")
        assertEquals("Mine", storedRows(out, "pkg.a/.Main").single().customLabel)
        assertEquals("Mine", Renames.customLabelOf(out, "s1", within = "pkg.a/.Main"))
    }

    @Test
    fun an_app_renamed_in_a_non_favorites_pop_up_is_named_once() {
        val d = LauncherData(appPopups = mapOf("pkg.a/.Main" to popupOf(other)))
        val out = Renames.apply(d, "b", "Other", within = "pkg.a/.Main")
        assertEquals(mapOf("pkg.b/.Main" to "Other"), out.appNames)
    }

    @Test
    fun naming_an_app_drops_an_older_copy_of_its_name_kept_in_a_non_favorites_pop_up() {
        val d = LauncherData(appPopups = mapOf("pkg.a/.Main" to popupOf(other.copy(customLabel = "Old"))))
        val out = Renames.setAppName(d, "pkg.b/.Main", "New")
        assertNull(storedRows(out, "pkg.a/.Main").single().customLabel)
        assertEquals("New", Renames.appNameOf(out, "pkg.b/.Main"))
    }

    @Test
    fun a_folder_rename_reaches_its_copy_in_a_non_favorites_pop_up() {
        val inner = folder("f", null)
        val d = LauncherData(folders = listOf(inner), appPopups = mapOf("pkg.a/.Main" to popupOf(inner)))
        val out = Renames.apply(d, "f", "Work")
        assertEquals("Work", out.folders.single().customLabel)
        assertEquals("Work", storedRows(out, "pkg.a/.Main").single().customLabel)
    }

    @Test
    fun renaming_to_the_stored_value_in_a_non_favorites_pop_up_changes_nothing() {
        val d = LauncherData(appPopups = mapOf("pkg.a/.Main" to popupOf(shortcut.copy(customLabel = "Mine"))))
        assertSame(d, Renames.apply(d, "s1", "Mine", within = "pkg.a/.Main"))
    }

    private fun rowsOf(e: Entry) = e.popup!!.rows.map { (it as PopupRow.Item).entry }

    @Test
    fun a_folder_nested_two_levels_deep_is_renamed_everywhere() {
        val deep = folder("f")
        val mid = folder("m", null, deep)
        val top = folder("t", null, mid)
        val d = LauncherData(favorites = listOf(top, deep), folders = listOf(top, mid, deep))
        val out = Renames.apply(d, "f", "Work")
        val inFavorite = rowsOf(rowsOf(out.favorites[0]).single()).single()
        val inLibrary = rowsOf(rowsOf(out.folders[0]).single()).single()
        val inMid = rowsOf(out.folders[1]).single()
        assertEquals(
            listOf("Work", "Work", "Work", "Work", "Work"),
            listOf(
                inFavorite.customLabel, inLibrary.customLabel, inMid.customLabel, out.favorites[1].customLabel, out.folders[2].customLabel,
            ),
        )
        assertNull(out.favorites[0].customLabel)
    }

    @Test
    fun a_folder_and_an_app_sharing_an_id_are_renamed_separately() {
        val sameId = Entry("x", Target.App("pkg.x/.Main"))
        val f = folder("x")
        val d = LauncherData(favorites = listOf(sameId, f), folders = listOf(f))
        val folderOnly = Renames.apply(d, "x", "Work", isFolder = true)
        assertEquals(emptyMap<String, String>(), folderOnly.appNames)
        assertEquals("Work", folderOnly.favorites[1].customLabel)
        assertEquals("Work", folderOnly.folders[0].customLabel)
        val appOnly = Renames.apply(d, "x", "Browser", isFolder = false)
        assertEquals(mapOf("pkg.x/.Main" to "Browser"), appOnly.appNames)
        assertNull(appOnly.favorites[1].customLabel)
        assertNull(appOnly.folders[0].customLabel)
    }

    @Test
    fun a_stored_empty_label_is_treated_like_no_label() {
        val d = LauncherData(favorites = listOf(app.copy(customLabel = "")))
        assertNull(Renames.customLabelOf(d, "a"))
        assertNull(Renames.appNameOf(d, "pkg.a/.Main"))
        assertEquals("Browser", Renames.appNameOf(Renames.apply(d, "a", "Browser"), "pkg.a/.Main"))
    }

    @Test
    fun a_favorite_folder_missing_from_the_library_still_renames() {
        val f = folder("f", null, app)
        val d = LauncherData(favorites = listOf(f))
        val out = Renames.apply(d, "f", "Legacy")
        assertEquals("Legacy", out.favorites[0].customLabel)
        assertEquals(emptyList<Entry>(), out.folders)
    }

    @Test
    fun sanitize_is_idempotent() {
        listOf("  a\nb ", "x".repeat(60), "x".repeat(39) + "\uD83D\uDE00y", "   ", "\u0000", " Caf\u00E9 ").forEach {
            assertEquals(it, Renames.sanitize(it), Renames.sanitize(Renames.sanitize(it)))
        }
    }

    @Test
    fun an_emoji_at_the_cut_after_ascii_is_dropped_whole() {
        val out = Renames.sanitize("a".repeat(Renames.MAX_LENGTH - 1) + "\uD83D\uDE00" + "tail")!!
        assertEquals("a".repeat(Renames.MAX_LENGTH - 1), out)
        assertEquals(false, out.any { Character.isSurrogate(it) })
        val fits = Renames.sanitize("a".repeat(Renames.MAX_LENGTH - 2) + "\uD83D\uDE00")!!
        assertEquals(Renames.MAX_LENGTH, fits.length)
    }

    // --- one name per app ---

    @Test
    fun a_name_an_older_version_kept_on_a_favorite_is_the_apps_name_until_it_is_renamed() {
        val d = LauncherData(favorites = listOf(app.copy(customLabel = "Old")))
        assertEquals("Old", Renames.appNameOf(d, "pkg.a/.Main"))
        // Renaming writes the one name and drops the copy on the entry, so they can never disagree.
        val out = Renames.setAppName(d, "pkg.a/.Main", "New")
        assertEquals("New", Renames.appNameOf(out, "pkg.a/.Main"))
        assertNull(out.favorites[0].customLabel)
    }

    @Test
    fun the_stored_name_wins_over_an_old_entry_label() {
        val d = LauncherData(favorites = listOf(app.copy(customLabel = "Old")), appNames = mapOf("pkg.a/.Main" to "Stored"))
        assertEquals("Stored", Renames.appNameOf(d, "pkg.a/.Main"))
    }

    @Test
    fun an_app_not_in_the_favorites_can_be_named_and_unnamed() {
        val d = LauncherData()
        val named = Renames.setAppName(d, "pkg.z/.Main", "Zed")
        assertEquals("Zed", Renames.appNameOf(named, "pkg.z/.Main"))
        assertEquals(emptyMap<String, String>(), Renames.setAppName(named, "pkg.z/.Main", null).appNames)
        // Nothing to undo changes nothing.
        assertSame(d, Renames.setAppName(d, "pkg.z/.Main", ""))
    }

    @Test
    fun renaming_an_app_clears_every_copy_of_an_old_label_wherever_the_app_appears() {
        val inner = app.copy(customLabel = "Inner")
        val host = folder("f", null, inner)
        val d = LauncherData(favorites = listOf(app.copy(customLabel = "Top"), host), folders = listOf(host))
        val out = Renames.setAppName(d, "pkg.a/.Main", "One")
        assertNull(out.favorites[0].customLabel)
        assertNull((out.favorites[1].popup!!.rows[0] as PopupRow.Item).entry.customLabel)
        assertNull((out.folders[0].popup!!.rows[0] as PopupRow.Item).entry.customLabel)
        assertEquals("One", Renames.appNameOf(out, "pkg.a/.Main"))
    }

    @Test
    fun shortcuts_and_folders_keep_their_own_names() {
        val sc = Entry("s", Target.Shortcut("k"))
        val out = Renames.apply(LauncherData(favorites = listOf(sc, app)), "s", "Mine")
        assertEquals("Mine", out.favorites[0].customLabel)
        assertEquals(emptyMap<String, String>(), out.appNames)
    }
}
