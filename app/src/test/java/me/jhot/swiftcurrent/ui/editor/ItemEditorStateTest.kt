package me.jhot.swiftcurrent.ui.editor

import me.jhot.swiftcurrent.catalog.AppInfo
import me.jhot.swiftcurrent.catalog.Section
import me.jhot.swiftcurrent.core.Condition
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.Popup
import me.jhot.swiftcurrent.core.PopupRow
import me.jhot.swiftcurrent.core.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun app(word: String) = AppInfo("pkg.${word.lowercase()}/.Main", word, "pkg.${word.lowercase()}")
private fun entryOf(word: String, id: String = "fav-${word.lowercase()}") =
    Entry(id, Target.App("pkg.${word.lowercase()}/.Main"))

private val WORDS = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel", "India", "Juliet")

private fun candidates(shortcuts: List<ShortcutCandidate> = emptyList(), folders: List<Entry> = emptyList()) =
    Candidates(
        suggestions = listOf(app("Alpha")),
        apps = WORDS.groupBy { it.first() }.toSortedMap().map { (k, v) -> Section(k, v.map(::app)) },
        shortcuts = shortcuts,
        folders = folders,
    )

private fun state(
    selectedWords: List<String>,
    capacity: Int?,
    ownApp: String? = null,
    ownIndex: Int? = null,
    missing: (Entry) -> Boolean = { false },
    inactive: (Entry) -> Boolean = { false },
    onChange: (List<PopupRow>) -> Unit = {},
) = Ids().let { ids ->
    ItemEditorState(
        selected = selectedWords.map { entryOf(it) },
        candidates = candidates(),
        capacity = capacity,
        strings = EnglishEditorStrings,
        ownShortcutsApp = ownApp,
        ownShortcutsIndex = ownIndex,
        isMissing = missing,
        isInactive = inactive,
        newId = ids::next,
        onChange = onChange,
    )
}

private class Ids {
    private var n = 0
    fun next() = "folder-${++n}"
}

private fun ids(s: ItemEditorState) = s.rows.map {
    when (it) {
        is PopupRow.Item -> it.entry.id
        is PopupRow.OwnShortcuts -> "own"
    }
}

class ItemEditorStateTest {
    @Test
    fun counter_reads_7_of_8_style() {
        val s = state(WORDS.take(7), capacity = 8)
        assertEquals("7 of 8", s.counter)
        s.toggle(entryOf("Hotel", id = "pkg.hotel/.Main"))
        assertEquals("8 of 8", s.counter)
        // Removing works for an entry whose id differs from the candidate's.
        s.toggle(entryOf("Alpha", id = "pkg.alpha/.Main"))
        assertEquals("7 of 8", s.counter)
        assertFalse(s.isSelected(entryOf("Alpha")))
    }

    @Test
    fun adding_past_capacity_is_allowed() {
        val s = state(WORDS.take(8), capacity = 8)
        s.toggle(entryOf("India", id = "pkg.india/.Main"))
        assertEquals(9, s.entries.size)
        assertEquals("9 of 8", s.counter)
        assertTrue(s.overLimit)
        assertTrue(s.isSelected(entryOf("India")))
    }

    @Test
    fun inactive_entries_do_not_count_toward_the_limit() {
        val s = state(WORDS.take(9), capacity = 8, inactive = { it.id == "fav-bravo" })
        assertEquals("8 of 8", s.counter)
        assertFalse(s.overLimit)
        assertNull(s.firstHiddenIndex)
    }

    @Test
    fun counter_counts_visible_entries_only() {
        val s = state(WORDS.take(6), capacity = 8, missing = { it.id == "fav-alpha" }, inactive = { it.id == "fav-bravo" })
        assertEquals("4 of 8", s.counter)
    }

    @Test
    fun first_hidden_index_skips_inactive_and_missing_entries() {
        val s = state(
            listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo"), capacity = 2,
            missing = { it.id == "fav-alpha" }, inactive = { it.id == "fav-bravo" },
        )
        // Only Charlie and Delta are drawn; Echo is the first one not shown.
        assertEquals(4, s.firstHiddenIndex)
    }

    @Test
    fun inactive_entry_is_never_flagged_over_the_limit() {
        val s = state(listOf("Alpha", "Bravo", "Charlie"), capacity = 2, inactive = { it.id == "fav-charlie" })
        assertFalse(s.overLimit)
        assertNull(s.firstHiddenIndex)
    }

    @Test
    fun is_dimmed_follows_isInactive() {
        val s = state(listOf("Alpha", "Bravo"), capacity = 8, inactive = { it.id == "fav-bravo" })
        assertTrue(s.isDimmed(entryOf("Bravo")))
        assertFalse(s.isDimmed(entryOf("Alpha")))
    }

    @Test
    fun missing_conditional_entry_is_not_counted_but_still_flagged_missing() {
        val s = state(listOf("Alpha", "Bravo"), capacity = 8, missing = { it.id == "fav-bravo" }, inactive = { it.id == "fav-bravo" })
        assertTrue(s.missing(entryOf("Bravo")))
        assertEquals("1 of 8", s.counter)
    }

    @Test
    fun saved_favorites_over_the_current_capacity_show_the_over_limit_state() {
        val s = state(WORDS.take(9), capacity = 8)
        assertEquals("9 of 8", s.counter)
        assertTrue(s.overLimit)
        // Home draws the first 8; the ninth is listed after the boundary.
        assertEquals(8, s.firstHiddenIndex)
        s.toggle(entryOf("Juliet", id = "pkg.juliet/.Main"))
        assertEquals(10, s.entries.size)
        s.toggle(entryOf("Alpha"))
        s.toggle(entryOf("Juliet"))
        assertEquals("8 of 8", s.counter)
        assertFalse(s.overLimit)
        assertNull(s.firstHiddenIndex)
    }

    @Test
    fun missing_favorites_are_kept_in_the_list_but_not_counted_or_drawn() {
        val s = state(listOf("Alpha", "Bravo", "Charlie", "Delta"), capacity = 2, missing = { it.id == "fav-bravo" })
        assertTrue(s.missing(entryOf("Bravo")))
        assertEquals("3 of 2", s.counter)
        // Home skips Bravo: it draws Alpha and Charlie, so Delta is the first one not shown.
        assertEquals(3, s.firstHiddenIndex)
        assertEquals(4, s.entries.size)
    }

    @Test
    fun popup_mode_has_no_capacity() {
        val s = state(WORDS.take(3), capacity = null)
        assertNull(s.capacity)
        assertEquals("3 selected", s.counter)
        WORDS.drop(3).forEach { s.toggle(entryOf(it, id = "pkg.${it.lowercase()}/.Main")) }
        assertEquals("10 selected", s.counter)
        assertFalse(s.overLimit)
        assertNull(s.firstHiddenIndex)
    }

    @Test
    fun selected_rows_reorder() {
        val s = state(listOf("Alpha", "Bravo", "Charlie", "Delta"), capacity = 8)
        s.move(0, 2)
        assertEquals(listOf("fav-bravo", "fav-charlie", "fav-alpha", "fav-delta"), ids(s))
        s.move(3, 0)
        assertEquals(listOf("fav-delta", "fav-bravo", "fav-charlie", "fav-alpha"), ids(s))
        // Out of range and no-op moves change nothing.
        s.move(1, 9)
        s.move(-1, 0)
        s.move(2, 2)
        assertEquals(listOf("fav-delta", "fav-bravo", "fav-charlie", "fav-alpha"), ids(s))
    }

    @Test
    fun own_shortcuts_row_can_move_and_be_hidden() {
        val s = state(listOf("Alpha", "Bravo"), capacity = null, ownApp = "pkg.host", ownIndex = 0)
        assertEquals(listOf("own", "fav-alpha", "fav-bravo"), ids(s))
        assertEquals("3 selected", s.counter)
        s.move(0, 2)
        assertEquals(listOf("fav-alpha", "fav-bravo", "own"), ids(s))
        s.toggleOwnShortcuts()
        assertTrue(s.ownShortcutsHidden)
        assertEquals(listOf("fav-alpha", "fav-bravo"), ids(s))
        assertEquals("2 selected", s.counter)
        // Restored where it was, not at the top.
        s.toggleOwnShortcuts()
        assertFalse(s.ownShortcutsHidden)
        assertEquals(listOf("fav-alpha", "fav-bravo", "own"), ids(s))
        // Only the item rows are the favorites list.
        assertEquals(listOf("fav-alpha", "fav-bravo"), s.entries.map { it.id })
    }

    @Test
    fun own_shortcuts_row_starts_hidden_when_the_stored_popup_left_it_out_and_stays_inert_without_an_app() {
        val hidden = state(listOf("Alpha"), capacity = null, ownApp = "pkg.host", ownIndex = null)
        assertTrue(hidden.ownShortcutsHidden)
        assertEquals(listOf("fav-alpha"), ids(hidden))
        hidden.toggleOwnShortcuts()
        // No stored position: shown first, the default of a fresh pop-up.
        assertEquals(listOf("own", "fav-alpha"), ids(hidden))

        val none = state(listOf("Alpha"), capacity = null)
        none.toggleOwnShortcuts()
        assertEquals(listOf("fav-alpha"), ids(none))
    }

    @Test
    fun a_shortcut_made_while_the_editor_is_open_is_offered_once_and_selectable() {
        val seen = mutableListOf<List<PopupRow>>()
        val s = state(listOf("Alpha"), capacity = 3, onChange = { seen += it })
        val made = ShortcutCandidate("user/u1", "Friend Bob", "Alpha Maps")
        s.addShortcutCandidate(made)
        s.addShortcutCandidate(made)
        assertEquals(listOf(made), s.shortcutCandidates)
        // Offering it selects nothing and saves nothing.
        assertEquals(0, seen.size)
        val entry = Entry("user/u1", Target.Shortcut("user/u1"))
        assertEquals("Friend Bob", s.labelOf(entry))
        s.toggle(entry)
        assertEquals(1, seen.size)
    }

    @Test
    fun a_shortcut_made_while_the_editor_is_open_opens_its_apps_group_so_it_can_be_seen() {
        val s = ItemEditorState(
            selected = emptyList(),
            candidates = candidates(listOf(ShortcutCandidate("a/1", "One", "Gmail"), ShortcutCandidate("a/2", "Two", "Gmail"))),
            capacity = null,
            strings = EnglishEditorStrings,
            onChange = {},
        )
        assertFalse(s.isGroupOpen("Gmail"))
        s.addShortcutCandidate(ShortcutCandidate("user/u1", "Chat", "Gmail"))
        assertTrue(s.isGroupOpen("Gmail"))
        // Adding the same one again changes nothing, so it does not reopen a group the owner closed.
        s.toggleGroup("Gmail")
        s.addShortcutCandidate(ShortcutCandidate("user/u1", "Chat", "Gmail"))
        assertFalse(s.isGroupOpen("Gmail"))
    }

    @Test
    fun every_change_is_reported_live() {
        val seen = mutableListOf<List<PopupRow>>()
        val s = state(listOf("Alpha", "Bravo"), capacity = 3, ownApp = null, onChange = { seen += it })
        s.toggle(entryOf("Charlie", id = "pkg.charlie/.Main"))
        assertEquals(1, seen.size)
        s.move(0, 1)
        assertEquals(2, seen.size)
        // A move that does nothing is not a change.
        s.move(1, 1)
        assertEquals(2, seen.size)
        s.toggle(entryOf("Alpha"))
        assertEquals(3, seen.size)
        assertEquals(s.rows, seen.last())
    }

    @Test
    fun create_folder_adds_an_empty_folder_candidate_and_selects_it() {
        val seen = mutableListOf<List<PopupRow>>()
        val s = state(listOf("Alpha"), capacity = 2, onChange = { seen += it })
        val folder = s.createFolder()
        assertEquals(Target.Folder, folder.target)
        assertEquals(Popup(emptyList()), folder.popup)
        assertEquals(listOf(folder), s.folders)
        assertTrue(s.isSelected(folder))
        assertEquals(2, s.entries.size)
        assertEquals(1, seen.size)
        // Past capacity a folder is still selected: adding is not gated.
        val second = s.createFolder()
        assertEquals(2, s.folders.size)
        assertTrue(s.isSelected(second))
        assertEquals("folder-1", folder.id)
    }

    @Test
    fun labels_come_from_the_catalog_then_the_custom_label_and_missing_ones_fall_back_to_a_neutral_word_never_the_raw_id() {
        val shortcut = Entry("sc", Target.Shortcut("net.x/bed"))
        val s = ItemEditorState(
            listOf(entryOf("Bravo"), shortcut, Entry("gone", Target.App("pkg.gone/.Main"), customLabel = "Browser")),
            candidates().copy(shortcuts = listOf(ShortcutCandidate("net.x/bed", "Bedtime", "Tasker"))),
            capacity = 8, strings = EnglishEditorStrings,
        )
        assertEquals("Bravo", s.labelOf(entryOf("Bravo")))
        assertEquals("Bedtime", s.labelOf(shortcut))
        assertEquals("Browser", s.labelOf(Entry("gone", Target.App("pkg.gone/.Main"), customLabel = "Browser")))
        assertEquals("App", s.labelOf(Entry("zzz", Target.App("pkg.zzz/.Main"))))
        assertEquals("Shortcut", s.labelOf(Entry("yyy", Target.Shortcut("net.x/none"))))
    }

    @Test
    fun an_editor_opened_on_a_list_with_duplicate_ids_does_not_crash() {
        val s = ItemEditorState(
            listOf(entryOf("Alpha"), entryOf("Bravo"), entryOf("Alpha")),
            candidates(), capacity = 8, strings = EnglishEditorStrings,
        )
        // Nothing is dropped, and every row still gets its own lazy key.
        assertEquals(3, s.entries.size)
        val keys = EditorSections.uniqueKeys(EditorSections.rows(s))
        assertEquals(keys.size, keys.toSet().size)
    }

    private val library = Entry(
        "folder-a", Target.Folder, customLabel = "Work",
        popup = Popup(listOf(PopupRow.Item(entryOf("Bravo")))),
    )

    @Test
    fun deselecting_a_folder_keeps_it_in_the_library_with_its_contents() {
        // Saved data from before the library existed: the folder is only a favorite.
        val s = ItemEditorState(listOf(entryOf("Alpha"), library), candidates(), capacity = 8, strings = EnglishEditorStrings)
        assertEquals(listOf(library), s.folders)
        s.toggle(library)
        assertFalse(s.isSelected(library))
        assertEquals(listOf(library), s.folders)
    }

    @Test
    fun reselecting_a_folder_restores_its_popup() {
        val s = ItemEditorState(listOf(entryOf("Alpha")), candidates(folders = listOf(library)), capacity = 8, strings = EnglishEditorStrings)
        s.toggle(library)
        assertEquals(library.popup, s.entries.last().popup)
        s.toggle(library)
        s.toggle(s.folders.single())
        assertEquals(library.popup, s.entries.last().popup)
    }

    @Test
    fun created_folder_is_selected_and_saved_even_at_capacity() {
        val seen = mutableListOf<List<PopupRow>>()
        val s = ItemEditorState(listOf(entryOf("Alpha")), candidates(), capacity = 1, strings = EnglishEditorStrings, onChange = { seen += it })
        val folder = s.createFolder()
        assertTrue(s.isSelected(folder))
        assertEquals(listOf(folder), s.folders)
        assertEquals(1, seen.size)
    }

    @Test
    fun a_rename_changes_the_label_in_the_list_without_reporting_a_change() {
        var changes = 0
        val s = state(listOf("Alpha", "Bravo"), capacity = 8, onChange = { changes++ })
        s.applyRename("fav-bravo", "Browser")
        assertEquals("Browser", s.labelOf(s.entries[1]))
        assertEquals("Alpha", s.labelOf(s.entries[0]))
        assertEquals("Bravo", s.defaultLabelOf(s.entries[1]))
        // The host persists a rename itself; the state only follows it so a later move cannot undo it.
        assertEquals(0, changes)
        s.applyRename("fav-bravo", "")
        assertEquals("Bravo", s.labelOf(s.entries[1]))
    }

    @Test
    fun applied_conditions_show_on_the_row_without_reporting_a_change_and_a_later_change_keeps_them() {
        var changes = 0
        var last: List<PopupRow> = emptyList()
        val s = state(listOf("Alpha", "Bravo"), capacity = 8, onChange = { changes++; last = it })
        val church = listOf<Condition>(Condition.MeldMode("church"))
        s.applyConditions("fav-bravo", church)
        assertEquals(church, s.entries[1].conditions)
        assertEquals(emptyList<Condition>(), s.entries[0].conditions)
        // The host saves the conditions itself; the state only follows so its next write does not drop them.
        assertEquals(0, changes)
        s.move(0, 1)
        assertEquals(church, (last[0] as PopupRow.Item).entry.conditions)
    }

    @Test
    fun conditions_on_a_folder_stay_on_the_row_and_never_reach_the_library() {
        val s = state(listOf("Alpha"), capacity = 8)
        val folder = s.createFolder()
        s.applyConditions(folder.id, listOf(Condition.MeldMode("church")))
        assertEquals(listOf<Condition>(Condition.MeldMode("church")), s.entries.last().conditions)
        assertEquals(emptyList<Condition>(), s.folders.single().conditions)
    }

    @Test
    fun renaming_a_folder_renames_the_selected_row_and_the_library_entry() {
        val s = state(listOf("Alpha"), capacity = 8)
        val folder = s.createFolder()
        s.applyRename(folder.id, "Work")
        assertEquals("Work", s.labelOf(s.entries.last()))
        assertEquals("Work", s.folders.single().customLabel)
        assertEquals("New folder", s.defaultLabelOf(s.entries.last()))
    }

    @Test
    fun a_later_move_still_reports_the_renamed_label() {
        var last: List<PopupRow> = emptyList()
        val s = state(listOf("Alpha", "Bravo"), capacity = 8, onChange = { last = it })
        s.applyRename("fav-alpha", "First")
        s.move(0, 1)
        // The name is the app's, not a copy on the row: it follows the app wherever the row moves.
        assertEquals("First", s.labelOf((last[1] as PopupRow.Item).entry))
    }

    @Test
    fun an_app_renamed_in_the_editor_shows_the_new_name_on_every_row_for_that_app_and_reset_restores_the_app_name() {
        val s = state(listOf("Alpha", "Bravo"), capacity = 8)
        val alpha = s.entries.first { it.id == "fav-alpha" }
        s.applyRename("fav-alpha", "First")
        assertEquals("First", s.labelOf(alpha))
        s.applyRename("fav-alpha", "")
        assertEquals("Alpha", s.labelOf(alpha))
    }

    @Test
    fun a_rename_keeps_the_own_shortcuts_row_in_place() {
        val s = state(listOf("Alpha", "Bravo"), capacity = null, ownApp = "pkg.host", ownIndex = 1)
        s.applyRename("fav-bravo", "B")
        assertEquals(listOf("fav-alpha", "own", "fav-bravo"), ids(s))
    }

    @Test
    fun the_drag_placement_stays_equal_while_the_target_row_does_not_change() {
        val s = state(listOf("Alpha", "Bravo", "Charlie"), capacity = null)
        s.drag = ItemEditorState.Drag(0, 0f, 100f, 3)
        val start = s.dragPlacement
        // A finger moving within the same slot must not look like a new placement, or every row recomposes per frame.
        s.drag = ItemEditorState.Drag(0, 20f, 100f, 3)
        assertEquals(start, s.dragPlacement)
        s.drag = ItemEditorState.Drag(0, 120f, 100f, 3)
        assertEquals(1, s.dragPlacement?.target)
        s.drag = null
        assertNull(s.dragPlacement)
    }

    @Test
    fun the_row_count_is_the_number_of_selected_rows() {
        assertEquals(3, state(listOf("Alpha", "Bravo"), capacity = null, ownApp = "pkg.host", ownIndex = 1).rowCount)
    }
}
