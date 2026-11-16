package me.jhot.swiftcurrent.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.WatchdogTimeout
import me.jhot.swiftcurrent.agenda.CalendarInfo
import me.jhot.swiftcurrent.catalog.AppInfo
import me.jhot.swiftcurrent.core.Backup
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.LauncherData
import me.jhot.swiftcurrent.core.Popup
import me.jhot.swiftcurrent.core.PopupRow
import me.jhot.swiftcurrent.core.Target
import me.jhot.swiftcurrent.lock.Screen
import me.jhot.swiftcurrent.shortcuts.ShortcutAccess
import me.jhot.swiftcurrent.ui.applist.scrubTag
import me.jhot.swiftcurrent.ui.editor.EditorSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import me.jhot.swiftcurrent.core.Settings as AppSettings

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class SettingsShellTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    // A hang (an animation that never settles) fails the test instead of stalling the gate.
    @get:Rule
    val timeout = WatchdogTimeout(seconds = 120)

    private val apps = listOf("Alpha", "Bravo", "Charlie").map { AppInfo("pkg.${it.lowercase()}/.Main", it, "pkg.${it.lowercase()}") }

    private fun res(id: Int, vararg a: Any) = compose.activity.getString(id, *a)
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitForTag(tag: String) = compose.waitUntil(5_000) { exists(tag) }
    private fun back() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    private fun start(data: LauncherData = LauncherData(), setup: (FakeDependencies) -> Unit = {}): FakeDependencies {
        val deps = FakeDependencies(data, apps)
        setup(deps)
        compose.setContent { LauncherShell(deps) }
        compose.waitUntil(5_000) { exists("clock") }
        compose.runOnUiThread { deps.session.navigate(Screen.Settings) }
        waitForTag("settings-root")
        return deps
    }

    private fun open(topic: Int, tag: String) {
        compose.onNodeWithText(res(topic)).performClick()
        waitForTag(tag)
    }

    @Test
    fun back_goes_to_the_parent_page_then_to_the_app_list() {
        val deps = start()
        open(R.string.settings_apps, "settings-apps")
        open(R.string.settings_apps_hidden, "settings-hidden")
        back(); waitForTag("settings-apps")
        back(); waitForTag("settings-root")
        back(); waitForTag("applist")
        assertEquals(Screen.AppList, deps.session.screen.value)
    }

    @Test
    fun the_sort_toggle_persists_and_the_app_list_re_sorts() {
        // Charlie has the most launches, so with usage sorting it leads its own letter group... all in different
        // letters, so use two apps under one letter.
        val twin = listOf(AppInfo("pkg.a1/.M", "Aaa", "pkg.a1"), AppInfo("pkg.a2/.M", "Abb", "pkg.a2"))
        val deps = FakeDependencies(LauncherData(launchCounts = mapOf("pkg.a2/.M" to 5)), twin)
        compose.setContent { LauncherShell(deps) }
        compose.waitUntil(5_000) { exists("clock") }
        // Counts load from the database after Home shows; the list orders by them once they are in.
        compose.waitUntil(5_000) { deps.usage.counts().isNotEmpty() }
        compose.runOnUiThread { deps.session.navigate(Screen.AppList) }
        waitForTag("applist")
        fun order() = listOf("row-pkg.a1/.M", "row-pkg.a2/.M").sortedBy {
            compose.onNodeWithTag(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        }
        assertEquals(listOf("row-pkg.a2/.M", "row-pkg.a1/.M"), order())
        compose.runOnUiThread { deps.session.navigate(Screen.Settings) }
        waitForTag("settings-root")
        open(R.string.settings_apps, "settings-apps")
        compose.onNodeWithTag("toggle-sort-by-usage").assertIsOn()
        compose.onNodeWithTag("toggle-sort-by-usage").performClick()
        compose.waitUntil(5_000) { !deps.fakeStore.data.settings.sortByUsage }
        back(); back()
        waitForTag("applist")
        assertEquals(Screen.AppList, deps.session.screen.value)
        assertEquals(listOf("row-pkg.a1/.M", "row-pkg.a2/.M"), order())
    }

    @Test
    fun the_hidden_apps_checklist_writes_hiddenApps() {
        val deps = start()
        open(R.string.settings_apps, "settings-apps")
        open(R.string.settings_apps_hidden, "settings-hidden")
        compose.onNodeWithTag("hide-pkg.bravo/.Main").performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.hiddenApps == setOf("pkg.bravo/.Main") }
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("hide-pkg.bravo/.Main").assertIsOn() }.isSuccess }
        compose.onNodeWithTag("hide-pkg.bravo/.Main").performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.hiddenApps.isEmpty() }
    }

    @Test
    fun the_home_page_favorites_row_opens_the_editor_even_with_no_favorites() {
        val deps = start()
        open(R.string.settings_home, "settings-home")
        compose.onNodeWithText(res(R.string.settings_home_favorites)).performClick()
        waitForTag("editor-header-Selected")
        assertEquals(Screen.Editor, deps.session.screen.value)
    }

    @Test
    fun the_editor_select_hidden_apps_action_opens_the_checklist() {
        val deps = start()
        open(R.string.settings_home, "settings-home")
        compose.onNodeWithText(res(R.string.settings_home_favorites)).performClick()
        waitForTag("editor-header-Selected")
        val strip = compose.onNodeWithTag("scrubber").getBoundsInRoot()
        val letter = compose.onNodeWithTag(scrubTag(EditorSections.ADVANCED), useUnmergedTree = true).getBoundsInRoot()
        val d = compose.activity.resources.displayMetrics.density
        val at = Offset(((strip.right - strip.left).value / 2f) * d, ((letter.top + letter.bottom).value / 2f - strip.top.value) * d)
        compose.onNodeWithTag("scrubber").performTouchInput { down(at); up() }
        waitForTag("action-SelectHiddenApps")
        compose.onNodeWithTag("action-SelectHiddenApps").performClick()
        waitForTag("settings-hidden")
        assertEquals(Screen.Settings, deps.session.screen.value)
        back(); waitForTag("settings-apps")
    }

    @Test
    fun deleting_a_folder_removes_it_from_library_favorites_and_popups() {
        val doomed = Entry("f1", Target.Folder, customLabel = "Games", popup = Popup(emptyList()))
        val holder = Entry("h", Target.App("pkg.alpha/.Main"), popup = Popup(listOf(PopupRow.Item(doomed))))
        val deps = start(LauncherData(favorites = listOf(doomed, holder), folders = listOf(doomed)))
        open(R.string.settings_apps, "settings-apps")
        open(R.string.settings_apps_folders, "settings-folders")
        compose.onNodeWithTag("folder-delete-f1").performClick()
        compose.onNodeWithText(res(R.string.settings_folders_confirm_yes)).performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.folders.isEmpty() }
        assertEquals(listOf("h"), deps.fakeStore.data.favorites.map { it.id })
        assertEquals(emptyList<PopupRow>(), deps.fakeStore.data.favorites.single().popup!!.rows)
    }

    @Test
    fun the_calendar_filter_lists_real_calendars_and_persists_a_toggle() {
        val deps = start(LauncherData()) { it.fakeCalendar.infos = listOf(CalendarInfo(1, "Personal", 0x2E7D32, "me"), CalendarInfo(2, "Work", 0xD50000, "me")) }
        open(R.string.settings_home, "settings-home")
        open(R.string.settings_home_calendars, "settings-calendars")
        // The page opens with an empty list and fills it from a read off the main thread; the toggle is not there until that lands.
        waitForTag("calendar-toggle-2")
        compose.onNodeWithTag("calendar-toggle-2").performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.settings.enabledCalendars == setOf(1L) }
    }

    @Test
    fun search_has_its_own_calendar_selection_apart_from_the_agendas() {
        val deps = start(LauncherData()) { it.fakeCalendar.infos = listOf(CalendarInfo(1, "Personal", 0x2E7D32, "me"), CalendarInfo(2, "Work", 0xD50000, "me")) }
        open(R.string.settings_search, "settings-search")
        open(R.string.settings_search_calendars, "settings-search-calendars")
        waitForTag("calendar-toggle-2")
        compose.onNodeWithTag("calendar-toggle-2").performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.settings.searchCalendars == setOf(1L) }
        assertNull(deps.fakeStore.data.settings.enabledCalendars)
    }

    @Test
    fun search_page_prompts_for_calendar_access_when_missing() {
        val deps = start { it.calendarGranted = false }
        open(R.string.settings_search, "settings-search")
        compose.onAllNodesWithText(res(R.string.settings_search_calendars)).assertCountEquals(0)
        compose.onNodeWithText(res(R.string.settings_search_calendar_access)).performClick()
        compose.waitUntil(5_000) { deps.started.isNotEmpty() }
        assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, deps.started.single().action)
    }

    @Test
    fun home_page_prompts_for_calendar_access_when_missing() {
        val deps = start { it.calendarGranted = false }
        open(R.string.settings_home, "settings-home")
        compose.onAllNodesWithText(res(R.string.settings_home_calendars)).assertCountEquals(0)
        compose.onNodeWithText(res(R.string.settings_home_calendar_access)).performClick()
        compose.waitUntil(5_000) { deps.started.isNotEmpty() }
        assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, deps.started.single().action)
    }

    // Settings used to drop a refused launch silently while Setup showed it.
    @Test
    fun a_refused_settings_launch_is_shown_not_dropped() {
        val deps = start {
            it.calendarGranted = false
            it.startOutcome = me.jhot.swiftcurrent.search.LaunchOutcome.Failed("no settings app")
        }
        open(R.string.settings_home, "settings-home")
        compose.onNodeWithText(res(R.string.settings_home_calendar_access)).performClick()
        compose.waitUntil(5_000) { exists("shell-message") }
        compose.onNodeWithText("no settings app").assertExists()
        assertEquals(1, deps.started.size)
    }

    @Test
    fun the_search_page_links_to_default_home_settings_when_shortcuts_cannot_work() {
        val deps = start { it.shortcuts.shortcutAccess = ShortcutAccess.NotDefaultHome }
        open(R.string.settings_search, "settings-search")
        compose.waitUntil(5_000) { compose.onAllNodesWithText(res(R.string.settings_search_status_needs_home)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(res(R.string.settings_search_status_needs_home)).performClick()
        compose.waitUntil(5_000) { deps.started.isNotEmpty() }
        assertEquals(android.provider.Settings.ACTION_HOME_SETTINGS, deps.started.single().action)
    }

    @Test
    fun the_search_page_has_no_default_home_prompt_once_shortcuts_work() {
        val deps = start()
        open(R.string.settings_search, "settings-search")
        // The prompt is hidden until the access check has answered, so wait for the check itself.
        compose.waitUntil(5_000) { deps.shortcuts.accessCalls.get() > 0 }
        compose.waitForIdle()
        compose.onAllNodesWithText(res(R.string.settings_search_status_needs_home)).assertCountEquals(0)
        assertTrue(deps.started.isEmpty())
    }

    @Test
    fun clearing_search_history_empties_recents_at_once() {
        val matt = me.jhot.swiftcurrent.search.sources.ContactRow(7, "Matt Lastname", "vnd.android.cursor.item/phone_v2", "+15550177")
        val deps = start { it.contactRows = listOf(matt) }
        deps.usage.recordPick("contact:7", me.jhot.swiftcurrent.search.Kind.Contact, "matt")
        compose.runOnUiThread { deps.session.navigate(Screen.Search) }
        waitForTag("result-contact:7")
        assertEquals(1, deps.contactResolves.get())
        compose.runOnUiThread { deps.session.navigate(Screen.Settings) }
        waitForTag("settings-root")
        open(R.string.settings_search, "settings-search")
        compose.onNodeWithText(res(R.string.settings_search_clear)).performClick()
        compose.onNodeWithText(res(R.string.settings_search_clear_confirm_yes)).performClick()
        compose.waitUntil(5_000) { deps.usage.recents().isEmpty() && kotlinx.coroutines.runBlocking { deps.usageDao.allSelections() }.isEmpty() }
        compose.waitForIdle()
        // No restart: the very next open shows nothing. A leftover id would be resolved again and shown,
        // so give that a bounded chance to happen before asserting it did not.
        compose.runOnUiThread { deps.session.navigate(Screen.Search) }
        waitForTag("search-field")
        runCatching { compose.waitUntil(2_000) { deps.contactResolves.get() > 1 || exists("result-contact:7") } }
        compose.waitForIdle()
        assertEquals(1, deps.contactResolves.get())
        compose.onAllNodesWithTag("result-contact:7", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun the_haptics_toggle_persists() {
        val deps = start()
        open(R.string.settings_gestures, "settings-gestures")
        compose.onNodeWithTag("toggle-haptics").assertIsOn()
        compose.onNodeWithTag("toggle-haptics").performClick()
        compose.waitUntil(5_000) { !deps.fakeStore.data.settings.haptics }
    }

    @Test
    fun the_gestures_page_links_to_accessibility_settings_while_the_service_is_off() {
        val deps = start()
        open(R.string.settings_gestures, "settings-gestures")
        compose.onNodeWithText(res(R.string.settings_gestures_lock_off)).performClick()
        compose.waitUntil(5_000) { deps.started.isNotEmpty() }
        assertEquals(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS, deps.started.single().action)
    }

    @Test
    fun restore_of_rejected_file_shows_error_and_keeps_data() {
        val mine = LauncherData(hiddenApps = setOf("keep"))
        val deps = start(mine) { it.unreadable = "this is not a backup" }
        open(R.string.settings_advanced, "settings-advanced")
        compose.waitUntil(5_000) { compose.onAllNodesWithText(res(R.string.settings_advanced_recover)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(res(R.string.settings_advanced_recover)).performClick()
        compose.waitUntil(5_000) { exists("shell-message") }
        compose.onNodeWithText(res(R.string.settings_restore_rejected)).assertExists()
        // The fake marks setup seen; nothing else may differ.
        assertEquals(mine.copy(settings = mine.settings.copy(setupSeen = true)), deps.fakeStore.data)
        // Still offered: nothing was recovered.
        compose.onNodeWithText(res(R.string.settings_advanced_recover)).assertExists()
    }

    @Test
    fun recovering_readable_text_restores_reloads_and_hides_the_offer() {
        val saved = LauncherData(hiddenApps = setOf("pkg.bravo/.Main"), settings = AppSettings(sortByUsage = false))
        val deps = start(LauncherData()) { it.unreadable = Backup.export(saved) }
        open(R.string.settings_advanced, "settings-advanced")
        compose.waitUntil(5_000) { compose.onAllNodesWithText(res(R.string.settings_advanced_recover)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(res(R.string.settings_advanced_recover)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(res(R.string.settings_restore_done)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(saved, deps.fakeStore.data)
        compose.waitUntil(5_000) { compose.onAllNodesWithText(res(R.string.settings_advanced_recover)).fetchSemanticsNodes().isEmpty() }
        // The shell's own data was reloaded: the Apps page shows the restored hidden count.
        back(); waitForTag("settings-root")
        open(R.string.settings_apps, "settings-apps")
        compose.onNodeWithText("1").assertExists()
    }

    // ---- the file-based restore path: the same runRestore the OpenDocument callback calls ----

    private fun restoreDocument(deps: FakeDependencies, uri: String, store: me.jhot.swiftcurrent.core.LauncherStore = deps.store): Pair<me.jhot.swiftcurrent.ui.settings.BackupOutcome, Int> {
        val model = ShellData(deps, kotlinx.coroutines.Dispatchers.IO)
        val controller = me.jhot.swiftcurrent.ui.settings.BackupController(store, deps.usage, kotlinx.coroutines.Dispatchers.IO)
        val outcome = kotlinx.coroutines.runBlocking {
            runRestore(deps, model, kotlinx.coroutines.Dispatchers.IO, controller, { deps.readDocument(android.net.Uri.parse(uri)) })
        }
        return outcome to noticeRes(outcome, R.string.settings_restore_failed)
    }

    @Test
    fun a_garbage_document_is_rejected_with_the_exact_message_and_nothing_changes() {
        val mine = LauncherData(hiddenApps = setOf("keep"))
        val deps = FakeDependencies(mine, apps, setupSeen = false)
        deps.unreadable = "old unreadable text"
        deps.documents["content://x/bad"] = "this is not a backup"
        val (outcome, msg) = restoreDocument(deps, "content://x/bad")
        assertEquals(me.jhot.swiftcurrent.ui.settings.BackupOutcome.Rejected, outcome)
        assertEquals("That file is not a backup this version can read. Nothing was changed.", res(msg))
        assertEquals(mine, deps.fakeStore.data)
        assertEquals("old unreadable text", deps.unreadable)
    }

    @Test
    fun a_newer_version_document_is_rejected_and_nothing_changes() {
        val mine = LauncherData(hiddenApps = setOf("keep"))
        val deps = FakeDependencies(mine, apps, setupSeen = false)
        deps.unreadable = "old unreadable text"
        deps.documents["content://x/new"] = """{"version":99}"""
        val (outcome, msg) = restoreDocument(deps, "content://x/new")
        assertEquals(me.jhot.swiftcurrent.ui.settings.BackupOutcome.Rejected, outcome)
        assertEquals(res(R.string.settings_restore_rejected), res(msg))
        assertEquals(mine, deps.fakeStore.data)
        assertEquals("old unreadable text", deps.unreadable)
    }

    @Test
    fun a_missing_document_is_a_read_failure_and_changes_nothing() {
        val mine = LauncherData(hiddenApps = setOf("keep"))
        val deps = FakeDependencies(mine, apps, setupSeen = false)
        val (outcome, msg) = restoreDocument(deps, "content://x/none")
        assertEquals(me.jhot.swiftcurrent.ui.settings.BackupOutcome.Failed, outcome)
        assertEquals(res(R.string.settings_restore_failed), res(msg))
        assertEquals(mine, deps.fakeStore.data)
    }

    @Test
    fun a_good_document_replaces_the_favorites_and_confirms() {
        val deps = FakeDependencies(LauncherData(), apps)
        val saved = LauncherData(favorites = listOf(Entry("fav-a", Target.App("pkg.alpha/.Main"))))
        deps.documents["content://x/good"] = Backup.export(saved)
        val (outcome, msg) = restoreDocument(deps, "content://x/good")
        assertEquals(me.jhot.swiftcurrent.ui.settings.BackupOutcome.Restored, outcome)
        assertEquals("Backup restored", res(msg))
        assertEquals(saved, deps.fakeStore.data)
    }

    @Test
    fun after_a_restore_the_app_list_order_follows_the_restored_launch_counts() {
        val twin = listOf(AppInfo("pkg.a1/.M", "Aaa", "pkg.a1"), AppInfo("pkg.a2/.M", "Abb", "pkg.a2"))
        val deps = FakeDependencies(LauncherData(launchCounts = mapOf("pkg.a2/.M" to 5)), twin)
        compose.setContent { LauncherShell(deps) }
        compose.waitUntil(5_000) { exists("clock") }
        // Counts load from the database after Home shows; the list orders by them once they are in.
        compose.waitUntil(5_000) { deps.usage.counts().isNotEmpty() }
        fun order() = listOf("row-pkg.a1/.M", "row-pkg.a2/.M").sortedBy {
            compose.onNodeWithTag(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        }
        compose.runOnUiThread { deps.session.navigate(Screen.AppList) }
        waitForTag("applist")
        assertEquals(listOf("row-pkg.a2/.M", "row-pkg.a1/.M"), order())
        // A backup that reverses the counts, restored through the real restore path.
        deps.documents["content://x/b"] = Backup.export(LauncherData(launchCounts = mapOf("pkg.a1/.M" to 9)))
        val model = ShellData(deps, kotlinx.coroutines.Dispatchers.IO)
        val controller = me.jhot.swiftcurrent.ui.settings.BackupController(deps.store, deps.usage, kotlinx.coroutines.Dispatchers.IO)
        kotlinx.coroutines.runBlocking {
            runRestore(deps, model, kotlinx.coroutines.Dispatchers.IO, controller, { deps.readDocument(android.net.Uri.parse("content://x/b")) })
        }
        assertEquals(mapOf("pkg.a1/.M" to 9), deps.usage.counts())
        // The shell reloads its data when the list is shown again; the order must now follow the counts.
        compose.runOnUiThread { deps.session.navigate(Screen.Home) }
        compose.waitUntil(5_000) { !exists("applist") }
        compose.runOnUiThread { deps.session.navigate(Screen.AppList) }
        waitForTag("applist")
        compose.waitUntil(5_000) { order() == listOf("row-pkg.a1/.M", "row-pkg.a2/.M") }
    }
}
