package me.jhot.swiftcurrent.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.WatchdogTimeout
import me.jhot.swiftcurrent.catalog.AppInfo
import me.jhot.swiftcurrent.context.DefaultMeldModes
import me.jhot.swiftcurrent.core.Condition
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.LauncherData
import me.jhot.swiftcurrent.core.Popup
import me.jhot.swiftcurrent.core.PopupRow
import me.jhot.swiftcurrent.core.Target
import me.jhot.swiftcurrent.lock.Screen
import me.jhot.swiftcurrent.ui.applist.scrubTag
import me.jhot.swiftcurrent.ui.editor.EditorSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renaming through the real shell: the long-press sheet, the editor, Home, the pop-up and search. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class RenameShellTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val timeout = WatchdogTimeout(seconds = 120)

    private fun app(word: String) = AppInfo("pkg.${word.lowercase()}/.Main", word, "pkg.${word.lowercase()}")
    private val apps = listOf("Alpha", "Bravo", "Mike").map(::app)

    private val inner = Entry("fav-bravo", Target.App("pkg.bravo/.Main"))
    private val folder = Entry("fav-folder", Target.Folder, popup = Popup(listOf(PopupRow.Item(inner))))
    private val favorites = listOf(Entry("fav-alpha", Target.App(ALPHA)), folder)

    private val shellState = ShellState()
    private val perms = FakePermissions()

    private fun start(data: LauncherData = LauncherData(favorites = favorites, folders = listOf(folder))): FakeDependencies {
        val deps = FakeDependencies(data, apps)
        compose.setContent { LauncherShell(deps, state = shellState, permissions = { onResult -> perms.also { it.onResult = onResult } }) }
        compose.waitUntil(5_000) { exists("clock") }
        return deps
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitForTag(tag: String) = compose.waitUntil(5_000) { exists(tag) }
    private fun waitForNoTag(tag: String) = compose.waitUntil(5_000) { !exists(tag) }
    private fun res(id: Int, vararg args: Any): String = compose.activity.getString(id, *args)
    private fun back() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    private fun renameFromSheet(tag: String, name: String) {
        compose.onNodeWithTag(tag).performTouchInput { longClick() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(res(R.string.action_sheet_rename)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(res(R.string.action_sheet_rename)).performClick()
        waitForTag("rename-field")
        compose.onNodeWithTag("rename-field").performTextReplacement(name)
        compose.onNodeWithTag("rename-save").performClick()
        waitForNoTag("rename-field")
    }

    private fun openEditor() {
        compose.onNodeWithTag("favorite-fav-alpha").performTouchInput { longClick() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(res(R.string.action_sheet_edit_favorites)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(res(R.string.action_sheet_edit_favorites)).performClick()
        waitForTag("editor-header-Selected")
    }

    private fun scrubTo(c: Char) {
        val strip = compose.onNodeWithTag("scrubber").getBoundsInRoot()
        val letter = compose.onNodeWithTag(scrubTag(c), useUnmergedTree = true).getBoundsInRoot()
        val density = compose.activity.resources.displayMetrics.density
        val at = Offset(
            ((strip.right - strip.left).value / 2f) * density,
            ((letter.top + letter.bottom).value / 2f - strip.top.value) * density,
        )
        compose.onNodeWithTag("scrubber").performTouchInput { down(at); up() }
    }

    private fun createFolder() {
        openEditor()
        scrubTo(EditorSections.ADVANCED)
        waitForTag("action-CreateFolder")
        compose.onNodeWithTag("action-CreateFolder").performClick()
    }

    private fun storedFolders(deps: FakeDependencies) = deps.fakeStore.data.folders

    @Test
    fun renaming_an_app_from_the_action_sheet_changes_its_label_on_home() {
        val deps = start()
        renameFromSheet("favorite-fav-alpha", "Browser")
        compose.waitUntil(5_000) { deps.fakeStore.data.appNames[ALPHA] == "Browser" }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Browser").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Alpha").assertCountEquals(0)
        // Nothing else about the favorite moved, and the name is the app's, not a copy on the entry.
        assertEquals(null, deps.fakeStore.data.favorites.first().customLabel)
        assertEquals(Target.App(ALPHA), deps.fakeStore.data.favorites.first().target)
    }

    @Test
    fun the_rename_sheet_starts_on_the_current_label_and_reset_goes_back_to_the_app_name() {
        val deps = start(LauncherData(favorites = listOf(Entry("fav-alpha", Target.App(ALPHA))), appNames = mapOf(ALPHA to "Browser")))
        compose.onNodeWithTag("favorite-fav-alpha").performTouchInput { longClick() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(res(R.string.action_sheet_rename)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(res(R.string.action_sheet_rename)).performClick()
        waitForTag("rename-field")
        compose.onNodeWithTag("rename-field").assert(hasText("Browser"))
        compose.onNodeWithText(res(R.string.rename_reset, "Alpha")).performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.appNames.isEmpty() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Alpha").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun saving_the_app_s_own_name_stores_no_custom_label() {
        val deps = start()
        renameFromSheet("favorite-fav-alpha", "Browser")
        compose.waitUntil(5_000) { deps.fakeStore.data.appNames[ALPHA] == "Browser" }
        renameFromSheet("favorite-fav-alpha", "Alpha")
        compose.waitUntil(5_000) { deps.fakeStore.data.appNames.isEmpty() }
    }

    @Test
    fun renaming_a_folder_updates_home_the_editor_and_the_popup() {
        val deps = start()
        renameFromSheet("favorite-fav-folder", "Work")
        compose.waitUntil(5_000) { deps.fakeStore.data.favorites[1].customLabel == "Work" }
        // The library is the source of truth for a folder, so it holds the name too.
        assertEquals("Work", storedFolders(deps).single().customLabel)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Work").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("favorite-fav-folder").assert(hasText("Work"))

        compose.onNodeWithTag("favorite-fav-folder").performClick()
        waitForTag("popup-scrim")
        compose.onNodeWithTag("popup-header", useUnmergedTree = true).assert(hasAnyDescendant(hasText("Work")))
        compose.onNodeWithText("Bravo").assertIsDisplayed()
        back()
        waitForNoTag("popup-scrim")

        openEditor()
        compose.onNodeWithTag("sel-fav-folder").assert(hasAnyDescendant(hasText("Work")))
    }

    @Test
    fun a_row_can_be_renamed_in_the_editor_and_the_list_updates_at_once() {
        val deps = start()
        openEditor()
        compose.onNodeWithTag("rename-fav-alpha").performClick()
        waitForTag("rename-field")
        compose.onNodeWithTag("rename-field").performTextReplacement("Browser")
        compose.onNodeWithTag("rename-save").performClick()
        waitForNoTag("rename-field")
        compose.onNodeWithTag("sel-fav-alpha").assert(hasAnyDescendant(hasText("Browser")))
        compose.waitUntil(5_000) { deps.fakeStore.data.appNames[ALPHA] == "Browser" }
        // A later edit in the same editor must not put the old label back, and the name stays the app's.
        compose.onNodeWithTag("toggle-sel-fav-folder").performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.favorites.size == 1 }
        assertEquals("Browser", deps.fakeStore.data.appNames[ALPHA])
        assertEquals(null, deps.fakeStore.data.favorites.single().customLabel)
        back()
        waitForNoTag("editor-header-Selected")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Browser").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun creating_a_folder_opens_the_rename_sheet() {
        val deps = start()
        createFolder()
        waitForTag("rename-field")
        compose.onNodeWithTag("rename-field").assert(hasText(res(R.string.editor_new_folder)))
        compose.onNodeWithTag("rename-field").performTextReplacement("Work")
        compose.onNodeWithTag("rename-save").performClick()
        waitForNoTag("rename-field")
        compose.waitUntil(5_000) { storedFolders(deps).any { it.customLabel == "Work" } }
        val made = storedFolders(deps).first { it.customLabel == "Work" }
        assertEquals("Work", deps.fakeStore.data.favorites.first { it.id == made.id }.customLabel)
        scrubTo(EditorSections.SELECTED)
        waitForTag("sel-${made.id}")
        compose.onNodeWithTag("sel-${made.id}").assert(hasAnyDescendant(hasText("Work")))
    }

    @Test
    fun dismissing_the_new_folder_rename_keeps_the_default_name() {
        val deps = start()
        createFolder()
        waitForTag("rename-field")
        back()
        waitForNoTag("rename-field")
        compose.waitUntil(5_000) { storedFolders(deps).size == 2 }
        val made = storedFolders(deps).first { it.id != "fav-folder" }
        assertNull(made.customLabel)
        scrubTo(EditorSections.SELECTED)
        waitForTag("sel-${made.id}")
        compose.onNodeWithTag("sel-${made.id}").assert(hasAnyDescendant(hasText(res(R.string.editor_new_folder))))
        // The editor is still open under the dismissed sheet.
        assertEquals(Screen.Editor, deps.session.screen.value)
    }

    @Test
    fun a_renamed_app_is_found_by_its_new_name_and_by_its_original_name_in_search() {
        val deps = start()
        renameFromSheet("favorite-fav-alpha", "Browser")
        compose.waitUntil(5_000) { deps.fakeStore.data.appNames[ALPHA] == "Browser" }
        compose.runOnUiThread { deps.session.navigate(Screen.Search) }
        waitForTag("search-field")
        // By the original name: the result is shown under the name the owner gave.
        compose.onNodeWithTag("search-field").performTextInput("alph")
        waitForTag("result-app:$ALPHA")
        compose.onNodeWithTag("result-app:$ALPHA").assert(hasText("Browser"))
        // By the new name too.
        compose.onNodeWithTag("search-field").performTextReplacement("brow")
        waitForTag("result-app:$ALPHA")
        compose.onNodeWithTag("result-app:$ALPHA").assert(hasText("Browser"))
    }

    @Test
    fun a_favorites_rename_shows_in_the_app_list_and_the_list_files_it_under_the_new_name() {
        val deps = start()
        renameFromSheet("favorite-fav-alpha", "Zebra mail")
        compose.waitUntil(5_000) { deps.fakeStore.data.appNames[ALPHA] == "Zebra mail" }
        compose.runOnUiThread { deps.session.navigate(Screen.AppList) }
        waitForTag("applist")
        // The list rebuilds from the store after it opens, so wait for the new name before checking where it is filed.
        waitForTag("header-Z")
        // The row carries the new name and is no longer under A with the other A apps.
        compose.onNodeWithTag("row-$ALPHA").assert(hasText("Zebra mail"))
        compose.onAllNodesWithText("Alpha").assertCountEquals(0)
        compose.onNodeWithTag("header-Z").assertIsDisplayed()
    }

    @Test
    fun an_app_that_is_not_a_favorite_can_be_renamed_from_the_app_list() {
        val deps = start()
        compose.runOnUiThread { deps.session.navigate(Screen.AppList) }
        waitForTag("applist")
        compose.onNodeWithTag("row-pkg.bravo/.Main").performTouchInput { longClick() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(res(R.string.action_sheet_rename)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(res(R.string.action_sheet_rename)).performClick()
        waitForTag("rename-field")
        compose.onNodeWithTag("rename-field").assert(hasText("Bravo"))
        compose.onNodeWithTag("rename-field").performTextReplacement("Yankee")
        compose.onNodeWithTag("rename-save").performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.appNames["pkg.bravo/.Main"] == "Yankee" }
        // Not a favorite, and still not one.
        assertEquals(favorites.size, deps.fakeStore.data.favorites.size)
        waitForNoTag("rename-field")
        compose.onNodeWithTag("row-pkg.bravo/.Main").assert(hasText("Yankee"))
    }

    @Test
    fun a_screen_lock_closes_the_rename_sheet() {
        val deps = start()
        compose.onNodeWithTag("favorite-fav-alpha").performTouchInput { longClick() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(res(R.string.action_sheet_rename)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(res(R.string.action_sheet_rename)).performClick()
        waitForTag("rename-field")
        compose.runOnUiThread { deps.session.onScreenOff() }
        waitForNoTag("rename-field")
        compose.onNodeWithTag("clock").assertIsDisplayed()
    }

    @Test
    fun a_screen_lock_closes_the_editor_rename_sheet() {
        val deps = start()
        openEditor()
        compose.onNodeWithTag("rename-fav-alpha").performClick()
        waitForTag("rename-field")
        compose.runOnUiThread { deps.session.onScreenOff() }
        waitForNoTag("rename-field")
        waitForNoTag("editor-header-Selected")
    }

    @Test
    fun an_uninstalled_favorite_can_still_be_renamed_in_the_editor() {
        val gone = Entry("fav-gone", Target.App("pkg.gone/.Main"))
        val deps = start(LauncherData(favorites = listOf(Entry("fav-alpha", Target.App(ALPHA)), gone)))
        openEditor()
        compose.onNodeWithTag("rename-fav-gone").performClick()
        waitForTag("rename-field")
        compose.onNodeWithTag("rename-field").performTextReplacement("Old app")
        compose.onNodeWithTag("rename-save").performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.appNames["pkg.gone/.Main"] == "Old app" }
        // The app is gone, but the row still shows the name the owner gave it.
        compose.onNodeWithTag("sel-fav-gone").assert(hasAnyDescendant(hasText("Old app")))
    }

    private fun openFavoriteSheet(tag: String) {
        compose.onNodeWithTag(tag).performTouchInput { longClick() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(res(R.string.action_sheet_rename)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(res(R.string.action_sheet_rename)).performClick()
        waitForTag("rename-field")
    }

    private fun clickTag(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()

    @Test
    fun pop_up_entry_sheet_has_no_show_when_section() {
        start()
        // An entry inside a folder's pop-up is no top-level favorite, so it cannot carry conditions.
        compose.runOnUiThread { shellState.openRename(inner) }
        waitForTag("rename-field")
        // The sheet swallows taps with a clickable, which merges a plain tagged node into it.
        compose.onAllNodesWithTag("conditions-section", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun a_favorite_s_sheet_saves_its_conditions_to_the_store() {
        val deps = start()
        deps.grantedPermissions += DefaultMeldModes.PERMISSION
        deps.meldModeNames = listOf("church")
        openFavoriteSheet("favorite-fav-folder")
        waitForTag("conditions-section")
        clickTag("condition-add")
        clickTag("condition-add-meld")
        // Already granted: no dialog.
        assertEquals(emptyList<String>(), perms.requests.toList())
        waitForTag("meld-mode-church")
        clickTag("meld-mode-church")
        compose.waitUntil(5_000) { !exists("meld-mode-church") }
        clickTag("rename-save")
        waitForNoTag("rename-field")
        compose.waitUntil(5_000) { deps.fakeStore.data.favorites[1].conditions.isNotEmpty() }
        assertEquals(listOf<Condition>(Condition.MeldMode("church")), deps.fakeStore.data.favorites[1].conditions)
        // The folder's library entry never carries conditions, and the name did not change.
        assertEquals(emptyList<Condition>(), storedFolders(deps).single().conditions)
        assertNull(deps.fakeStore.data.favorites[1].customLabel)
    }

    @Test
    fun a_denied_permission_shows_a_notice_and_adds_nothing() {
        val deps = start()
        openFavoriteSheet("favorite-fav-alpha")
        clickTag("condition-add")
        clickTag("condition-add-meld")
        compose.waitUntil(5_000) { perms.requests.isNotEmpty() }
        assertEquals(listOf(DefaultMeldModes.PERMISSION), perms.requests.toList())
        compose.runOnUiThread { perms.deliver(DefaultMeldModes.PERMISSION, false) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(res(R.string.conditions_meld_denied)).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithTag("condition-0", useUnmergedTree = true).assertCountEquals(0)
        assertEquals(false, exists("meld-mode-church"))
        assertEquals(0, deps.contextRefreshes.get())
    }

    @Test
    fun a_granted_permission_refreshes_the_context_and_opens_the_picker() {
        val deps = start()
        deps.meldModeNames = listOf("church")
        openFavoriteSheet("favorite-fav-alpha")
        clickTag("condition-add")
        clickTag("condition-add-meld")
        compose.waitUntil(5_000) { perms.requests.isNotEmpty() }
        compose.runOnUiThread { perms.deliver(DefaultMeldModes.PERMISSION, true) }
        waitForTag("meld-mode-church")
        assertEquals(1, deps.contextRefreshes.get())
    }

    @Test
    fun an_already_granted_permission_still_refreshes_the_context() {
        val deps = start()
        deps.grantedPermissions += DefaultMeldModes.PERMISSION
        deps.meldModeNames = listOf("church")
        openFavoriteSheet("favorite-fav-alpha")
        clickTag("condition-add")
        clickTag("condition-add-meld")
        waitForTag("meld-mode-church")
        // Meld announces no new or renamed inactive mode, so the known set is read again before judging it.
        assertEquals(1, deps.contextRefreshes.get())
    }

    @Test
    fun a_folder_favorite_picked_into_a_pop_up_leaves_its_conditions_behind() {
        val shown = Entry("fav-folder", Target.Folder, popup = Popup(emptyList()), conditions = listOf(Condition.MeldMode("church")))
        // Not in the library yet: the favorite itself is offered as the folder candidate.
        val deps = start(LauncherData(favorites = listOf(Entry("fav-alpha", Target.App(ALPHA)), shown)))
        compose.runOnUiThread {
            shellState.openEditor(EditorTarget.Popup("fav-alpha"))
            deps.session.navigate(Screen.Editor)
        }
        waitForTag("editor-header-Selected")
        scrubTo(EditorSections.FOLDERS)
        waitForTag("pick-Folders-fav-folder")
        compose.onNodeWithTag("pick-Folders-fav-folder").performClick()
        // The app's own shortcuts row is there by default; the folder is the one item.
        compose.waitUntil(5_000) { deps.fakeStore.data.favorites.first().popup?.rows.orEmpty().any { it is PopupRow.Item } }
        val row = deps.fakeStore.data.favorites.first().popup!!.rows.filterIsInstance<PopupRow.Item>().single()
        assertEquals("fav-folder", row.entry.id)
        assertEquals(emptyList<Condition>(), row.entry.conditions)
    }

    @Test
    fun conditions_edited_from_the_editor_pencil_survive_a_later_editor_change() {
        val deps = start()
        deps.grantedPermissions += DefaultMeldModes.PERMISSION
        deps.meldModeNames = listOf("church")
        openEditor()
        compose.onNodeWithTag("rename-fav-folder").performClick()
        waitForTag("conditions-section")
        clickTag("condition-add")
        clickTag("condition-add-meld")
        waitForTag("meld-mode-church")
        clickTag("meld-mode-church")
        compose.waitUntil(5_000) { !exists("meld-mode-church") }
        clickTag("rename-save")
        waitForNoTag("rename-field")
        compose.waitUntil(5_000) { deps.fakeStore.data.favorites.any { it.conditions.isNotEmpty() } }
        // A later edit in the same editor writes the whole list; the conditions must ride along.
        compose.onNodeWithTag("toggle-sel-fav-alpha").performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.favorites.size == 1 }
        val folderFav = deps.fakeStore.data.favorites.single()
        assertEquals("fav-folder", folderFav.id)
        assertEquals(listOf<Condition>(Condition.MeldMode("church")), folderFav.conditions)
        // The library entry never carries them.
        assertEquals(emptyList<Condition>(), storedFolders(deps).single().conditions)
    }

    @Test
    fun a_pop_up_row_in_the_editor_has_no_show_when_section() {
        val deps = start()
        compose.runOnUiThread {
            shellState.openEditor(EditorTarget.Popup("fav-folder"))
            deps.session.navigate(Screen.Editor)
        }
        waitForTag("rename-fav-bravo")
        compose.onNodeWithTag("rename-fav-bravo").performClick()
        waitForTag("rename-field")
        compose.onAllNodesWithTag("conditions-section", useUnmergedTree = true).assertCountEquals(0)
    }
}
