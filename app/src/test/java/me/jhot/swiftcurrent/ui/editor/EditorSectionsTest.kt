package me.jhot.swiftcurrent.ui.editor

import me.jhot.swiftcurrent.catalog.AppInfo
import me.jhot.swiftcurrent.catalog.Section
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun app(word: String) = AppInfo("pkg.${word.lowercase()}/.Main", word, "pkg.${word.lowercase()}")

private fun fixture(
    letters: List<Char> = listOf('#', 'A', 'B'),
    selected: Int = 2,
    shortcuts: Int = 1,
    folders: Int = 1,
    suggestions: Int = 1,
): ItemEditorState {
    val sections = letters.map { c -> Section(c, listOf(app(if (c == '#') "2048" else "${c}pp"))) }
    return ItemEditorState(
        selected = (1..selected).map { Entry("s$it", Target.App("pkg.s$it/.Main")) },
        candidates = Candidates(
            suggestions = (1..suggestions).map { app("Sug$it") },
            apps = sections,
            shortcuts = (1..shortcuts).map { ShortcutCandidate("net.x/$it", "Short $it", "X") },
            folders = (1..folders).map { Entry("f$it", Target.Folder) },
        ),
        capacity = 8,
        strings = EnglishEditorStrings,
    )
}

private fun withShortcuts(vararg s: ShortcutCandidate, selectedKeys: List<String> = emptyList()) = ItemEditorState(
    selected = selectedKeys.map { Entry(it, Target.Shortcut(it)) },
    candidates = Candidates(shortcuts = s.toList()),
    capacity = 8,
    strings = EnglishEditorStrings,
)

private fun sc(app: String, n: Int) = ShortcutCandidate("$app/$n", "$app $n", app)

class EditorSectionsTest {
    @Test
    fun shortcuts_group_by_app_sorted_by_app_label_keeping_the_system_order_inside() {
        val groups = EditorSections.groupShortcuts(listOf(sc("tasker", 2), sc("Maps", 1), sc("tasker", 1), sc("Maps", 2), sc("Alpha", 1)))
        assertEquals(listOf("Alpha", "Maps", "tasker"), groups.map { it.app })
        assertEquals(listOf("tasker/2", "tasker/1"), groups.last().shortcuts.map { it.key })
    }

    @Test
    fun an_app_with_several_shortcuts_is_a_collapsed_header_and_one_with_a_single_shortcut_stays_a_plain_row() {
        val rows = EditorSections.rows(withShortcuts(sc("Maps", 1), sc("Maps", 2), sc("Solo", 1)))
        val shortcutRows = rows.dropWhile { it != EditorRow.Heading(EditorSection.Shortcuts) }.drop(1)
            .takeWhile { it != EditorRow.Heading(EditorSection.Folders) }
        assertEquals(
            listOf(
                EditorRow.AppGroup("Maps", count = 2, selected = 0, open = false),
                EditorRow.Pick(EditorSection.Shortcuts, EditorSections.shortcutEntry(sc("Solo", 1)), "Solo 1", "Solo"),
            ),
            shortcutRows,
        )
    }

    @Test
    fun opening_a_group_lists_its_shortcuts_under_the_header_and_closing_hides_them_again() {
        val state = withShortcuts(sc("Maps", 1), sc("Maps", 2))
        state.toggleGroup("Maps")
        val open = EditorSections.rows(state).filterIsInstance<EditorRow.Pick>().filter { it.section == EditorSection.Shortcuts }
        assertEquals(listOf("Maps 1", "Maps 2"), open.map { it.label })
        assertTrue(open.all { it.grouped && it.subtitle == null })
        state.toggleGroup("Maps")
        assertTrue(EditorSections.rows(state).none { it is EditorRow.Pick && it.section == EditorSection.Shortcuts })
    }

    @Test
    fun a_group_with_a_selected_shortcut_starts_open_and_reports_how_many_are_selected() {
        val state = withShortcuts(sc("Maps", 1), sc("Maps", 2), sc("Tasker", 1), sc("Tasker", 2), selectedKeys = listOf("Maps/2"))
        val rows = EditorSections.rows(state)
        assertTrue(EditorRow.AppGroup("Maps", count = 2, selected = 1, open = true) in rows)
        assertTrue(EditorRow.AppGroup("Tasker", count = 2, selected = 0, open = false) in rows)
        // The owner can still close it: a selection above the list is not lost by hiding its row.
        state.toggleGroup("Maps")
        assertTrue(EditorRow.AppGroup("Maps", count = 2, selected = 1, open = false) in EditorSections.rows(state))
    }

    private fun heading(rows: List<EditorRow>, i: Int) = rows[i]

    @Test
    fun scrubber_icons_jump_to_selected_suggestions_hash_shortcuts_folders_advanced() {
        val rows = EditorSections.rows(fixture())
        assertEquals(EditorRow.Heading(EditorSection.Selected), heading(rows, EditorSections.indexFor(EditorIcon.Selected, rows)))
        assertEquals(EditorRow.Heading(EditorSection.Suggestions), heading(rows, EditorSections.indexFor(EditorIcon.Suggestions, rows)))
        assertEquals(EditorRow.Letter('#'), heading(rows, EditorSections.indexFor(EditorIcon.Hash, rows)))
        assertEquals(EditorRow.Heading(EditorSection.Shortcuts), heading(rows, EditorSections.indexFor(EditorIcon.Shortcuts, rows)))
        assertEquals(EditorRow.Heading(EditorSection.Folders), heading(rows, EditorSections.indexFor(EditorIcon.Folders, rows)))
        assertEquals(EditorRow.Heading(EditorSection.Advanced), heading(rows, EditorSections.indexFor(EditorIcon.Advanced, rows)))
        // They run top to bottom in the order the scrubber lists them.
        val order = EditorIcon.entries.map { EditorSections.indexFor(it, rows) }
        assertEquals(order.sorted(), order)
        assertEquals(order.toSet().size, order.size)
    }

    @Test
    fun letters_jump_to_their_header_and_missing_letters_land_on_the_next_one() {
        val rows = EditorSections.rows(fixture(letters = listOf('#', 'A', 'C')))
        assertEquals(EditorRow.Letter('A'), rows[EditorSections.indexForChar('A', rows)])
        assertEquals(EditorRow.Letter('C'), rows[EditorSections.indexForChar('C', rows)])
        // No B apps: the nearest following letter is the useful place to land.
        assertEquals(EditorRow.Letter('C'), rows[EditorSections.indexForChar('B', rows)])
        // Past the last letter: the last one.
        assertEquals(EditorRow.Letter('C'), rows[EditorSections.indexForChar('Y', rows)])
    }

    @Test
    fun hash_falls_back_to_the_first_letter_when_no_app_starts_with_a_digit() {
        val rows = EditorSections.rows(fixture(letters = listOf('A', 'B')))
        assertEquals(EditorRow.Letter('A'), rows[EditorSections.indexFor(EditorIcon.Hash, rows)])
        val none = EditorSections.rows(fixture(letters = emptyList()))
        assertEquals(EditorRow.Heading(EditorSection.Shortcuts), none[EditorSections.indexFor(EditorIcon.Hash, none)])
    }

    @Test
    fun empty_sections_keep_their_header_with_a_hint_so_every_icon_has_a_target() {
        val rows = EditorSections.rows(fixture(selected = 0, shortcuts = 0, folders = 0, suggestions = 0))
        EditorIcon.entries.forEach { assertTrue(EditorSections.indexFor(it, rows) >= 0) }
        assertTrue(rows.any { it is EditorRow.Hint && it.section == EditorSection.Selected })
        assertTrue(rows.any { it is EditorRow.Hint && it.section == EditorSection.Shortcuts })
        assertTrue(rows.any { it is EditorRow.Hint && it.section == EditorSection.Folders })
    }

    @Test
    fun advanced_lists_the_three_actions_and_selected_rows_carry_the_over_limit_boundary() {
        val rows = EditorSections.rows(fixture())
        assertEquals(
            listOf(ActionKind.AddShortcut, ActionKind.CreateFolder, ActionKind.SelectHiddenApps),
            rows.filterIsInstance<EditorRow.Action>().map { it.kind },
        )
        assertTrue(rows.none { it is EditorRow.Boundary })

        val over = ItemEditorState(
            (1..3).map { Entry("s$it", Target.App("pkg.s$it/.Main")) },
            Candidates(), capacity = 2, strings = EnglishEditorStrings,
        )
        val overRows = EditorSections.rows(over)
        val boundary = overRows.indexOfFirst { it is EditorRow.Boundary }
        // Two drawn rows, then the boundary, then the one that is not shown.
        assertEquals(listOf(0, 1), overRows.take(boundary).filterIsInstance<EditorRow.Selected>().map { it.index })
        assertEquals(listOf(2), overRows.drop(boundary).filterIsInstance<EditorRow.Selected>().map { it.index })
    }

    @Test
    fun a_hidden_own_shortcuts_row_is_offered_under_suggestions_and_row_keys_are_unique() {
        val s = ItemEditorState(
            listOf(Entry("a", Target.App("pkg.a/.Main"))),
            Candidates(suggestions = listOf(app("Sug"))), capacity = null, strings = EnglishEditorStrings,
            ownShortcutsApp = "pkg.host", ownShortcutsIndex = null,
        )
        val rows = EditorSections.rows(s)
        assertTrue(rows.any { it is EditorRow.OwnPick })
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
        s.toggleOwnShortcuts()
        assertTrue(EditorSections.rows(s).none { it is EditorRow.OwnPick })
    }

    @Test
    fun scrubber_letters_are_the_three_leading_icons_the_letters_then_the_three_trailing_icons() {
        val letters = EditorSections.letters(listOf(Section('#', emptyList()), Section('A', emptyList()), Section('B', emptyList())))
        assertEquals(
            listOf(
                EditorSections.SELECTED, EditorSections.SUGGESTIONS, '#', 'A', 'B',
                EditorSections.SHORTCUTS, EditorSections.FOLDERS, EditorSections.ADVANCED,
            ),
            letters,
        )
        // '#' stays even with no digit apps so the strip keeps its shape.
        assertEquals('#', EditorSections.letters(emptyList())[2])
    }
}
