package me.jhot.swiftcurrent.ui.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.WatchdogTimeout
import me.jhot.swiftcurrent.catalog.AppInfo
import me.jhot.swiftcurrent.catalog.Section
import me.jhot.swiftcurrent.core.Condition
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.PopupRow
import me.jhot.swiftcurrent.core.Target
import me.jhot.swiftcurrent.ui.applist.scrubTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val WORDS = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel", "India", "Juliet")

private fun comp(word: String) = "pkg.${word.lowercase()}/.Main"

private fun appInfo(word: String) = AppInfo(comp(word), word, "pkg.${word.lowercase()}")

private fun fav(word: String) = Entry("fav-${word.lowercase()}", Target.App(comp(word)))

/** Enough apps under each letter that the list really scrolls. */
internal fun editorCandidates(
    shortcuts: List<ShortcutCandidate> = listOf(ShortcutCandidate("net.x/bedtime", "Bedtime", "Tasker")),
    folders: List<Entry> = emptyList(),
): Candidates {
    val sections = ('A'..'H').map { c ->
        val named = WORDS.filter { it.first() == c }
        val filler = (1..6).map { AppInfo("pkg.$c$it/.Main", "$c filler $it", "pkg.$c$it") }
        Section(c, named.map(::appInfo) + filler)
    }
    return Candidates(
        suggestions = listOf(appInfo("Bravo"), appInfo("Echo")),
        apps = listOf(Section('#', listOf(AppInfo("pkg.n2048/.Main", "2048", "pkg.n2048")))) + sections,
        shortcuts = shortcuts,
        folders = folders,
    )
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class ItemEditorScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    // A hang (an animation that never settles) fails the test instead of stalling the gate.
    @get:Rule
    val timeout = WatchdogTimeout(seconds = 60)

    // The real resources, so a renamed string breaks the test and the words cannot drift from the screen.
    private val resources get() = ApplicationProvider.getApplicationContext<Context>().resources
    private val strings: EditorStrings get() = ResourceEditorStrings(resources)
    private fun str(id: Int, vararg args: Any) = resources.getString(id, *args)

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun favorites(
        selected: List<String> = listOf("Alpha", "Bravo", "Charlie"),
        capacity: Int? = 8,
        onChange: (List<PopupRow>) -> Unit = {},
        missing: (Entry) -> Boolean = { false },
        candidates: Candidates = editorCandidates(),
    ) = ItemEditorState(selected.map(::fav), candidates, capacity, strings, isMissing = missing, newId = { "folder-1" }, onChange = onChange)

    private fun show(
        state: ItemEditorState,
        mode: Mode = Mode.Favorites,
        onDone: () -> Unit = {},
        onAddShortcut: () -> Unit = {},
        onCreateFolder: (Entry) -> Unit = {},
        onRename: (Entry) -> Unit = {},
        onSelectHiddenApps: () -> Unit = {},
        subtitle: (String) -> String? = { null },
    ) = compose.setContent {
        ItemEditor(
            state, mode, onDone,
            onAddShortcut = onAddShortcut, onCreateFolder = onCreateFolder, onRename = onRename,
            onSelectHiddenApps = onSelectHiddenApps, ownShortcutsSubtitle = subtitle,
        )
    }

    private fun tap(c: Char) {
        val strip = compose.onNodeWithTag("scrubber").getBoundsInRoot()
        val letter = compose.onNodeWithTag(scrubTag(c), useUnmergedTree = true).getBoundsInRoot()
        val density = compose.activity.resources.displayMetrics.density
        val at = Offset(
            ((strip.right - strip.left).value / 2f) * density,
            ((letter.top + letter.bottom).value / 2f - strip.top.value) * density,
        )
        compose.onNodeWithTag("scrubber").performTouchInput { down(at); up() }
        compose.waitForIdle()
    }

    private fun customAction(tag: String, label: String) {
        val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
        val action = node.config[SemanticsActions.CustomActions].first { it.label == label }
        compose.runOnUiThread { action.action() }
        compose.waitForIdle()
    }

    private fun top(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot().top.value

    @Test
    fun editor_shows_the_title_counter_selected_rows_and_every_section() {
        show(favorites())
        compose.onNodeWithText(strings.titleFavorites).assertIsDisplayed()
        compose.onNodeWithText(strings.counterOf(3, 8)).assertIsDisplayed()
        compose.onNodeWithTag("editor-header-Selected").assertIsDisplayed()
        listOf("Alpha", "Bravo", "Charlie").forEach { compose.onNodeWithTag("sel-fav-${it.lowercase()}").assertIsDisplayed() }
        compose.onNodeWithTag("editor-header-Suggestions").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.editor_done)).assertIsDisplayed()
    }

    @Test
    fun editor_applies_changes_live_and_done_only_closes() {
        val changes = mutableListOf<List<PopupRow>>()
        var done = 0
        val state = favorites(onChange = { changes += it })
        show(state, onDone = { done++ })

        // Suggestions list Echo unselected; checking it commits at once, before Done.
        compose.onNodeWithTag("pick-Suggestions-${comp("Echo")}").performClick()
        compose.waitForIdle()
        assertEquals(1, changes.size)
        assertEquals(listOf("fav-alpha", "fav-bravo", "fav-charlie", comp("Echo")), state.entries.map { it.id })
        compose.onNodeWithText(strings.counterOf(4, 8)).assertIsDisplayed()

        // Unchecking a selected row commits too.
        compose.onNodeWithTag("toggle-sel-fav-alpha").performClick()
        compose.waitForIdle()
        assertEquals(2, changes.size)
        assertEquals(3, state.entries.size)

        compose.onNodeWithText(str(R.string.editor_done)).performClick()
        compose.waitForIdle()
        assertEquals(1, done)
        // Done only closes: it wrote nothing more and changed nothing.
        assertEquals(2, changes.size)
        assertEquals(3, state.entries.size)
    }

    @Test
    fun back_closes_like_done() {
        var done = 0
        show(favorites(), onDone = { done++ })
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(1, done)
    }

    @Test
    fun an_apps_shortcuts_sit_in_a_group_that_opens_and_closes_and_announces_which() {
        val many = (1..3).map { ShortcutCandidate("net.t/$it", "Task $it", "Tasker") }
        show(favorites(candidates = editorCandidates(shortcuts = many)))
        tap(EditorSections.SHORTCUTS)
        compose.onNodeWithTag("group-Tasker").assertIsDisplayed()
        compose.onNodeWithText(strings.groupSummary(3, 0)).assertIsDisplayed()
        compose.onAllNodesWithTag("pick-Shortcuts-net.t/1").assertCountEquals(0)
        compose.onNodeWithTag("group-Tasker").assert(
            androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, strings.groupCollapsed),
        )

        compose.onNodeWithTag("group-Tasker").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("pick-Shortcuts-net.t/1").assertIsDisplayed()
        compose.onNodeWithTag("group-Tasker").assert(
            androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, strings.groupExpanded),
        )

        compose.onNodeWithTag("pick-Shortcuts-net.t/2").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(strings.groupSummary(3, 1)).assertIsDisplayed()

        compose.onNodeWithTag("group-Tasker").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithTag("pick-Shortcuts-net.t/1").assertCountEquals(0)
    }

    @Test
    fun scrubber_icons_jump_to_selected_suggestions_hash_shortcuts_folders_advanced() {
        show(favorites())
        // The A-Z list is long, so each jump really moves the list.
        tap(EditorSections.ADVANCED)
        compose.onNodeWithTag("editor-header-Advanced").assertIsDisplayed()
        compose.onAllNodesWithTag("editor-header-Selected").assertCountEquals(0)
        tap(EditorSections.FOLDERS)
        compose.onNodeWithTag("editor-header-Folders").assertIsDisplayed()
        tap(EditorSections.SHORTCUTS)
        compose.onNodeWithTag("editor-header-Shortcuts").assertIsDisplayed()
        tap('#')
        compose.onNodeWithTag("editor-header-#").assertIsDisplayed()
        compose.onAllNodesWithTag("editor-header-Shortcuts").assertCountEquals(0)
        tap('D')
        compose.onNodeWithTag("editor-header-D").assertIsDisplayed()
        tap(EditorSections.SUGGESTIONS)
        compose.onNodeWithTag("editor-header-Suggestions").assertIsDisplayed()
        tap(EditorSections.SELECTED)
        compose.onNodeWithTag("editor-header-Selected").assertIsDisplayed()
        // Selected sits at the top of the list, with its first row under it.
        compose.onNodeWithTag("sel-fav-alpha").assertIsDisplayed()
        compose.onAllNodesWithTag("editor-header-D").assertCountEquals(0)
    }

    @Test
    fun move_actions_reorder_selected_rows_without_dragging() {
        val state = favorites()
        show(state)
        customAction("sel-fav-alpha", strings.moveDown)
        assertEquals(listOf("fav-bravo", "fav-alpha", "fav-charlie"), state.entries.map { it.id })
        assertTrue(top("sel-fav-bravo") < top("sel-fav-alpha"))
        assertTrue(top("sel-fav-alpha") < top("sel-fav-charlie"))
        customAction("sel-fav-charlie", strings.moveUp)
        assertEquals(listOf("fav-bravo", "fav-charlie", "fav-alpha"), state.entries.map { it.id })
        assertTrue(top("sel-fav-charlie") < top("sel-fav-alpha"))
        // At the ends the action does nothing.
        customAction("sel-fav-bravo", strings.moveUp)
        assertEquals(listOf("fav-bravo", "fav-charlie", "fav-alpha"), state.entries.map { it.id })
    }

    @Test
    fun a_full_list_still_accepts_another_and_shows_the_over_limit_label() {
        val changes = mutableListOf<List<PopupRow>>()
        val state = favorites(
            selected = listOf("Alpha", "Charlie", "Delta", "Foxtrot", "Golf", "Hotel", "India", "Juliet"),
            onChange = { changes += it },
        )
        show(state)
        compose.onNodeWithText(strings.counterOf(8, 8)).assertIsDisplayed()
        compose.onNodeWithTag("pick-Suggestions-${comp("Bravo")}").performClick()
        compose.waitForIdle()
        assertEquals(9, state.entries.size)
        assertEquals(1, changes.size)
        compose.onNodeWithText(strings.overLimit(strings.counterOf(9, 8))).assertIsDisplayed()
        // Removing one brings it back within the limit.
        compose.onNodeWithTag("toggle-sel-fav-alpha").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(strings.counterOf(8, 8)).assertIsDisplayed()
    }

    @Test
    fun over_limit_favorites_show_the_marker_and_the_boundary() {
        val state = favorites(selected = WORDS.take(9), capacity = 8)
        show(state)
        compose.onNodeWithText(strings.overLimit(strings.counterOf(9, 8))).assertIsDisplayed()
        compose.onNodeWithTag("editor-boundary").assertIsDisplayed()
        assertTrue(top("sel-fav-hotel") < top("editor-boundary"))
        assertTrue(top("editor-boundary") < top("sel-fav-india"))
    }

    @Test
    fun uninstalled_favorites_are_flagged_in_the_selected_list() {
        show(favorites(missing = { it.id == "fav-bravo" }))
        compose.onNodeWithText(strings.notInstalled).assertIsDisplayed()
        compose.onNodeWithText(strings.counterOf(2, 8)).assertIsDisplayed()
    }

    @Test
    fun advanced_rows_call_back_and_create_folder_makes_and_selects_a_folder() {
        var shortcuts = 0
        var hidden = 0
        var folders = 0
        val state = favorites()
        show(state, onAddShortcut = { shortcuts++ }, onCreateFolder = { folders++ }, onSelectHiddenApps = { hidden++ })
        tap(EditorSections.ADVANCED)
        compose.onNodeWithTag("action-AddShortcut").performClick()
        compose.onNodeWithTag("action-SelectHiddenApps").performClick()
        compose.onNodeWithTag("action-CreateFolder").performClick()
        compose.waitForIdle()
        assertEquals(listOf(1, 1, 1), listOf(shortcuts, hidden, folders))
        assertEquals(4, state.entries.size)
        assertEquals(Target.Folder, state.entries.last().target)
        assertEquals(1, state.folders.size)
    }

    @Test
    fun creating_a_folder_reports_the_new_folder_so_it_can_be_named() {
        val made = mutableListOf<Entry>()
        show(favorites(), onCreateFolder = { made += it })
        tap(EditorSections.ADVANCED)
        compose.onNodeWithTag("action-CreateFolder").performClick()
        compose.waitForIdle()
        assertEquals(listOf("folder-1"), made.map { it.id })
        assertEquals(Target.Folder, made.single().target)
    }

    @Test
    fun a_selected_row_has_a_rename_button_that_reports_the_entry() {
        val renamed = mutableListOf<Entry>()
        show(favorites(), onRename = { renamed += it })
        compose.onNodeWithTag("rename-fav-bravo").assertIsDisplayed().performClick()
        assertEquals(listOf("fav-bravo"), renamed.map { it.id })
    }

    @Test
    fun the_rename_button_is_at_least_48dp_and_its_description_names_the_row() {
        show(favorites())
        listOf("Alpha" to "alpha", "Bravo" to "bravo").forEach { (label, id) ->
            val node = compose.onNodeWithTag("rename-fav-$id")
            val b = node.getBoundsInRoot()
            assertTrue("${b.width} x ${b.height}", b.width >= 48.dp && b.height >= 48.dp)
            node.assertContentDescriptionEquals(str(R.string.editor_rename_item, label))
        }
    }

    @Test
    fun rename_is_also_an_accessibility_action_of_the_row() {
        val renamed = mutableListOf<Entry>()
        show(favorites(), onRename = { renamed += it })
        customAction("sel-fav-charlie", str(R.string.editor_rename_item, "Charlie"))
        assertEquals(listOf("fav-charlie"), renamed.map { it.id })
    }

    @Test
    fun a_renamed_row_shows_its_custom_label_at_once() {
        val state = favorites()
        show(state)
        state.applyRename("fav-bravo", "Browser")
        compose.waitForIdle()
        compose.onNodeWithTag("sel-fav-bravo").assert(
            androidx.compose.ui.test.hasAnyDescendant(androidx.compose.ui.test.hasText("Browser")),
        )
    }

    @Test
    fun popup_mode_has_a_plain_count_and_a_movable_own_shortcuts_row() {
        val changes = mutableListOf<List<PopupRow>>()
        val state = ItemEditorState(
            selected = listOf(fav("Alpha")),
            candidates = editorCandidates(),
            capacity = null,
            strings = strings,
            ownShortcutsApp = "pkg.bravo",
            ownShortcutsIndex = 0,
            onChange = { changes += it },
        )
        show(state, mode = Mode.Popup, subtitle = { "New memo, Search" })
        compose.onNodeWithText(strings.titlePopup).assertIsDisplayed()
        compose.onNodeWithText(strings.selectedCount(2)).assertIsDisplayed()
        compose.onNodeWithText(strings.ownShortcuts("Bravo")).assertIsDisplayed()
        compose.onNodeWithText("New memo, Search").assertIsDisplayed()
        assertTrue(top("sel-own") < top("sel-fav-alpha"))

        customAction("sel-own", strings.moveDown)
        assertTrue(top("sel-fav-alpha") < top("sel-own"))

        // Unchecking hides the row; it is offered again under Suggestions.
        compose.onNodeWithTag("toggle-sel-own").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithTag("sel-own").assertCountEquals(0)
        compose.onNodeWithText(strings.selectedCount(1)).assertIsDisplayed()
        compose.onNodeWithTag("pick-own").assertIsDisplayed()
        compose.onNodeWithTag("pick-own").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("sel-own").assertIsDisplayed()
        assertEquals(3, changes.size)
    }

    @Test
    fun row_with_conditions_shows_summary_and_is_dimmed_only_while_inactive() {
        val work = Condition.MeldMode("work")
        var inactive by androidx.compose.runtime.mutableStateOf(true)
        val conditional = Entry("fav-alpha", Target.App(comp("Alpha")), conditions = listOf(work))
        val state = ItemEditorState(
            listOf(conditional, fav("Bravo")), editorCandidates(), 8, strings, isInactive = { it.conditions.isNotEmpty() && inactive },
        )
        show(state)
        val summary = conditionSummary(listOf(work), strings)!!
        compose.onNodeWithTag("sel-fav-alpha").assert(androidx.compose.ui.test.hasAnyDescendant(androidx.compose.ui.test.hasText(summary)))
        compose.onNodeWithTag("sel-fav-alpha").assert(
            androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, strings.hiddenByConditions),
        )
        // A row without conditions shows neither.
        compose.onNodeWithTag("sel-fav-bravo").assert(
            androidx.compose.ui.test.SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsProperties.StateDescription),
        )
        compose.runOnIdle { inactive = false }
        compose.waitForIdle()
        compose.onNodeWithTag("sel-fav-alpha").assert(
            androidx.compose.ui.test.SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsProperties.StateDescription),
        )
        compose.onNodeWithTag("sel-fav-alpha").assert(androidx.compose.ui.test.hasAnyDescendant(androidx.compose.ui.test.hasText(summary)))
    }

    @Test
    fun popup_mode_never_shows_the_condition_summary() {
        val work = Condition.MeldMode("work")
        val state = ItemEditorState(
            listOf(Entry("fav-alpha", Target.App(comp("Alpha")), conditions = listOf(work))), editorCandidates(), null, strings,
        )
        show(state, mode = Mode.Popup)
        compose.onAllNodesWithText(conditionSummary(listOf(work), strings)!!).assertCountEquals(0)
    }

    @Test
    fun the_editor_has_no_widget_row() {
        show(favorites())
        compose.onAllNodesWithText("Add widget").assertCountEquals(0)
        compose.onAllNodesWithText("Widgets").assertCountEquals(0)
    }

    @Test
    fun an_editor_opened_on_a_list_with_duplicate_ids_does_not_crash() {
        val state = ItemEditorState(listOf(fav("Alpha"), fav("Bravo"), fav("Alpha")), editorCandidates(), capacity = 8, strings = strings)
        show(state)
        compose.onAllNodesWithTag("sel-fav-alpha").assertCountEquals(2)
        compose.onNodeWithTag("sel-fav-bravo").assertIsDisplayed()
    }

    @Test
    fun the_plain_english_fake_matches_the_resources() {
        val real = strings
        val fake = EnglishEditorStrings
        assertEquals(fake.titleFavorites, real.titleFavorites)
        assertEquals(fake.titlePopup, real.titlePopup)
        assertEquals(fake.titlePopupFor("X"), real.titlePopupFor("X"))
        EditorSection.entries.filter { it != EditorSection.Letters }.forEach { assertEquals(fake.heading(it), real.heading(it)) }
        listOf(EditorSection.Selected, EditorSection.Suggestions, EditorSection.Shortcuts, EditorSection.Folders)
            .forEach { assertEquals(fake.hint(it), real.hint(it)) }
        ActionKind.entries.forEach { assertEquals(fake.action(it), real.action(it)) }
        assertEquals(fake.notInstalled, real.notInstalled)
        assertEquals(fake.notShownOnHome, real.notShownOnHome)
        assertEquals(fake.moveUp, real.moveUp)
        assertEquals(fake.moveDown, real.moveDown)
        assertEquals(fake.reorder, real.reorder)
        assertEquals(fake.newFolder, real.newFolder)
        assertEquals(fake.unknownApp, real.unknownApp)
        assertEquals(fake.unknownShortcut, real.unknownShortcut)
        assertEquals(fake.ownShortcuts("A"), real.ownShortcuts("A"))
        assertEquals(fake.counterOf(7, 8), real.counterOf(7, 8))
        assertEquals(fake.selectedCount(3), real.selectedCount(3))
        assertEquals(fake.selectedCount(1), real.selectedCount(1))
        assertEquals(fake.groupSummary(3, 0), real.groupSummary(3, 0))
        assertEquals(fake.groupSummary(3, 2), real.groupSummary(3, 2))
        assertEquals(fake.groupExpanded, real.groupExpanded)
        assertEquals(fake.groupCollapsed, real.groupCollapsed)
        assertEquals(fake.overLimit("9 of 8"), real.overLimit("9 of 8"))
    }
}
