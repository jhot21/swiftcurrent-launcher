package me.jhot.swiftcurrent.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.catalog.AppInfo
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.HiddenContactAction
import me.jhot.swiftcurrent.core.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class SettingsPagesTest {
    @get:Rule
    val compose = createComposeRule()

    private fun res(id: Int, vararg a: Any) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *a)

    @Test
    fun home_page_has_calendar_filter_and_favorites_entry() {
        val calls = mutableListOf<String>()
        compose.setContent {
            HomeSettings(calendarGranted = true, onFavorites = { calls += "fav" }, onCalendars = { calls += "cal" }, onGrantCalendar = { calls += "grant" })
        }
        compose.onNodeWithText(res(R.string.settings_home_favorites)).performClick()
        compose.onNodeWithText(res(R.string.settings_home_calendars)).performClick()
        compose.onAllNodesWithText(res(R.string.settings_home_calendar_access)).assertCountEquals(0)
        assertEquals(listOf("fav", "cal"), calls)
    }

    @Test
    fun home_page_offers_the_calendar_permission_when_access_is_missing() {
        val calls = mutableListOf<String>()
        compose.setContent {
            HomeSettings(calendarGranted = false, onFavorites = { calls += "fav" }, onCalendars = { calls += "cal" }, onGrantCalendar = { calls += "grant" })
        }
        compose.onAllNodesWithText(res(R.string.settings_home_calendars)).assertCountEquals(0)
        compose.onNodeWithText(res(R.string.settings_home_calendar_access)).performClick()
        compose.onNodeWithText(res(R.string.settings_home_favorites)).performClick()
        assertEquals(listOf("grant", "fav"), calls)
    }

    @Test
    fun advanced_has_backup_and_restore() {
        val calls = mutableListOf<String>()
        compose.setContent { AdvancedSettings(hasUnreadable = false, onBackUp = { calls += "backup" }, onRestore = { calls += "restore" }, onRecover = { calls += "recover" }, onSetup = {}, onExportSearchHistory = {}) }
        compose.onNodeWithText(res(R.string.settings_advanced_backup)).performClick()
        compose.onNodeWithText(res(R.string.settings_advanced_restore)).performClick()
        compose.onAllNodesWithText(res(R.string.settings_advanced_recover)).assertCountEquals(0)
        assertEquals(listOf("backup", "restore"), calls)
    }

    @Test
    fun recover_row_shows_only_when_unreadable_data_exists() {
        val calls = mutableListOf<String>()
        compose.setContent { AdvancedSettings(hasUnreadable = true, onBackUp = {}, onRestore = {}, onRecover = { calls += "recover" }, onSetup = {}, onExportSearchHistory = {}) }
        compose.onNodeWithText(res(R.string.settings_advanced_recover)).performClick()
        assertEquals(listOf("recover"), calls)
    }

    @Test
    fun advanced_exports_search_history() {
        val calls = mutableListOf<String>()
        compose.setContent { AdvancedSettings(hasUnreadable = false, onBackUp = {}, onRestore = {}, onRecover = {}, onSetup = {}, onExportSearchHistory = { calls += "export" }) }
        compose.onNodeWithText(res(R.string.settings_advanced_export_history)).performClick()
        assertEquals(listOf("export"), calls)
    }

    @Test
    fun apps_page_toggle_reports_the_change() {
        var sort = true
        var changes = 0
        compose.setContent {
            var on by remember { mutableStateOf(true) }
            AppsSettings(on, 0, 0, { on = it; sort = it; changes++ }, {}, {})
        }
        compose.onNodeWithTag("toggle-sort-by-usage").assertIsOn()
        compose.onNodeWithTag("toggle-sort-by-usage").performClick()
        compose.onNodeWithTag("toggle-sort-by-usage").assertIsOff()
        assertEquals(false, sort)
        assertEquals(1, changes)
    }

    @Test
    fun the_hidden_apps_copy_says_hidden_apps_stay_searchable() {
        val text = res(R.string.settings_hidden_section)
        // Search still finds a hidden app (with a Hidden badge), so the copy must not promise otherwise.
        assertTrue(text, text.contains("searchable"))
        assertFalse(text, text.contains("and search"))
    }

    @Test
    fun hidden_apps_page_lists_every_app_and_reports_ticks() {
        val apps = listOf(AppInfo("pkg.a/.M", "Alpha", "pkg.a"), AppInfo("pkg.b/.M", "Bravo", "pkg.b"))
        val calls = mutableListOf<Pair<String, Boolean>>()
        compose.setContent { HiddenAppsPage(apps, setOf("pkg.b/.M")) { c, h -> calls += c to h } }
        compose.onNodeWithTag("hide-pkg.a/.M").assertIsOff()
        compose.onNodeWithTag("hide-pkg.b/.M").assertIsOn()
        compose.onNodeWithTag("hide-pkg.a/.M").performClick()
        compose.onNodeWithTag("hide-pkg.b/.M").performClick()
        assertEquals(listOf("pkg.a/.M" to true, "pkg.b/.M" to false), calls)
    }

    @Test
    fun deleting_a_folder_asks_first_and_keep_changes_nothing() {
        val f = Entry("f1", Target.Folder, customLabel = "Games")
        val deleted = mutableListOf<String>()
        compose.setContent { PopupFoldersPage(listOf(f), { it.customLabel ?: "" }) { deleted += it.id } }
        compose.onNodeWithTag("folder-delete-f1").performClick()
        compose.onNodeWithText(res(R.string.settings_folders_confirm_title, "Games")).assertExists()
        assertEquals(emptyList<String>(), deleted)
        compose.onNodeWithText(res(R.string.settings_folders_confirm_no)).performClick()
        compose.onAllNodesWithText(res(R.string.settings_folders_confirm_title, "Games")).assertCountEquals(0)
        compose.onNodeWithTag("folder-delete-f1").performClick()
        compose.onNodeWithText(res(R.string.settings_folders_confirm_yes)).performClick()
        assertEquals(listOf("f1"), deleted)
    }

    @Test
    fun search_page_explains_when_shortcuts_need_the_default_home_app() {
        val calls = mutableListOf<String>()
        compose.setContent { searchSettings(shortcutsWork = false, onShortcuts = { calls += "shortcuts" }, onOpenHomeSettings = { calls += "home" }) }
        compose.onNodeWithText(res(R.string.settings_search_status_needs_home)).performClick()
        compose.onNodeWithText(res(R.string.settings_search_shortcuts)).performClick()
        assertEquals(listOf("home", "shortcuts"), calls)
    }

    @Test
    fun search_page_puts_the_default_home_prompt_above_the_shortcut_switches() {
        compose.setContent { searchSettings(shortcutsWork = false) }
        val prompt = compose.onNodeWithText(res(R.string.settings_search_status_needs_home)).fetchSemanticsNode().boundsInRoot.top
        val shortcuts = compose.onNodeWithText(res(R.string.settings_search_shortcuts)).fetchSemanticsNode().boundsInRoot.top
        assertTrue("prompt at $prompt should sit above the shortcuts row at $shortcuts", prompt < shortcuts)
    }

    @Test
    fun search_page_shows_no_shortcut_status_once_setup_is_done_or_unknown() {
        var work by mutableStateOf<Boolean?>(true)
        compose.setContent { searchSettings(shortcutsWork = work) }
        compose.onAllNodesWithText(res(R.string.settings_search_status_needs_home)).assertCountEquals(0)
        work = null
        compose.waitForIdle()
        compose.onAllNodesWithText(res(R.string.settings_search_status_needs_home)).assertCountEquals(0)
        work = false
        compose.waitForIdle()
        compose.onAllNodesWithText(res(R.string.settings_search_status_needs_home)).assertCountEquals(1)
    }

    @Composable
    private fun searchSettings(
        shortcutsWork: Boolean? = true,
        onShortcuts: () -> Unit = {},
        onOpenHomeSettings: () -> Unit = {},
        appsFirst: Boolean = true,
        contactsFirst: Boolean = true,
        learnFromPicks: Boolean = true,
        onAppsFirst: (Boolean) -> Unit = {},
        onContactsFirst: (Boolean) -> Unit = {},
        onLearnFromPicks: (Boolean) -> Unit = {},
        onClearHistory: () -> Unit = {},
        hiddenCount: Int = 0,
        onHiddenMethods: () -> Unit = {},
        calendarGranted: Boolean = true,
        onCalendars: () -> Unit = {},
        onGrantCalendar: () -> Unit = {},
    ) = SearchSettings(
        shortcutsWork, onShortcuts, onOpenHomeSettings,
        appsFirst, contactsFirst, learnFromPicks,
        onAppsFirst, onContactsFirst, onLearnFromPicks, onClearHistory,
        hiddenCount, onHiddenMethods,
        calendarGranted, onCalendars, onGrantCalendar,
    )

    @Test
    fun search_page_opens_the_calendar_choice_when_calendar_access_is_granted() {
        var opened = 0
        compose.setContent { searchSettings(onCalendars = { opened++ }) }
        compose.onAllNodesWithText(res(R.string.settings_search_calendar_access)).assertCountEquals(0)
        compose.onNodeWithText(res(R.string.settings_search_calendars)).performClick()
        assertEquals(1, opened)
    }

    @Test
    fun search_page_asks_for_calendar_access_instead_when_it_is_missing() {
        var asked = 0
        compose.setContent { searchSettings(calendarGranted = false, onGrantCalendar = { asked++ }) }
        compose.onAllNodesWithText(res(R.string.settings_search_calendars)).assertCountEquals(0)
        compose.onNodeWithText(res(R.string.settings_search_calendar_access)).performClick()
        assertEquals(1, asked)
    }

    private val meetVideo = HiddenContactAction("contact:1", "contact:1|app|x.meet|a@example.com", "Alex Example", "Video call on Meet a@example.com")
    private val messages = HiddenContactAction("contact:2", "shortcut:k", "Bonne Hotmann", "Messages")

    @Test
    fun search_page_opens_the_hidden_contact_methods_page_and_says_how_many_are_hidden() {
        var opened = 0
        compose.setContent { searchSettings(hiddenCount = 2, onHiddenMethods = { opened++ }) }
        compose.onNodeWithText(res(R.string.settings_search_hidden_methods)).performClick()
        assertEquals(1, opened)
        compose.onNodeWithText(res(R.string.settings_search_hidden_methods_count, 2)).assertExists()
    }

    @Test
    fun hidden_contact_methods_page_lists_each_with_its_contact_and_full_label_and_shows_one_again_on_tap() {
        val shown = mutableListOf<String>()
        compose.setContent { HiddenContactMethodsPage(listOf(meetVideo, messages), onShow = { shown += it.action }) }
        compose.onNodeWithText("Alex Example: Video call on Meet a@example.com").assertExists()
        compose.onNodeWithText("Bonne Hotmann: Messages").performClick()
        assertEquals(listOf("shortcut:k"), shown)
    }

    @Test
    fun hidden_contact_methods_page_says_so_when_nothing_is_hidden() {
        compose.setContent { HiddenContactMethodsPage(emptyList(), onShow = {}) }
        compose.onNodeWithText(res(R.string.settings_hidden_methods_none)).assertExists()
    }

    @Test
    fun each_search_ranking_toggle_reports_its_flipped_value() {
        val calls = mutableListOf<String>()
        compose.setContent {
            searchSettings(
                appsFirst = true, contactsFirst = false, learnFromPicks = true,
                onAppsFirst = { calls += "apps=$it" }, onContactsFirst = { calls += "contacts=$it" }, onLearnFromPicks = { calls += "learn=$it" },
            )
        }
        compose.onNodeWithTag("toggle-apps-first").assertIsOn()
        compose.onNodeWithTag("toggle-contacts-first").assertIsOff()
        compose.onNodeWithTag("toggle-learn-from-picks").assertIsOn()
        compose.onNodeWithTag("toggle-apps-first").performClick()
        compose.onNodeWithTag("toggle-contacts-first").performClick()
        compose.onNodeWithTag("toggle-learn-from-picks").performClick()
        assertEquals(listOf("apps=false", "contacts=true", "learn=false"), calls)
    }

    @Test
    fun clearing_search_history_asks_first_and_keep_changes_nothing() {
        var cleared = 0
        compose.setContent { searchSettings(onClearHistory = { cleared++ }) }
        compose.onNodeWithText(res(R.string.settings_search_clear)).performClick()
        compose.onNodeWithText(res(R.string.settings_search_clear_confirm_title)).assertExists()
        assertEquals(0, cleared)
        compose.onNodeWithText(res(R.string.settings_search_clear_confirm_no)).performClick()
        compose.onAllNodesWithText(res(R.string.settings_search_clear_confirm_title)).assertCountEquals(0)
        assertEquals(0, cleared)
        compose.onNodeWithText(res(R.string.settings_search_clear)).performClick()
        compose.onNodeWithText(res(R.string.settings_search_clear_confirm_yes)).performClick()
        compose.onAllNodesWithText(res(R.string.settings_search_clear_confirm_title)).assertCountEquals(0)
        assertEquals(1, cleared)
    }

    @Test
    fun the_haptic_sliders_set_a_strength_and_are_absent_when_haptics_are_off() {
        val picked = mutableListOf<String>()
        var on by androidx.compose.runtime.mutableStateOf(true)
        compose.setContent {
            GesturesSettings(
                lockServiceOn = true, haptics = on, onHaptics = {}, onOpenAccessibility = {},
                scrubStrength = 55, onScrubStrength = { picked += "scrub=$it" },
                gestureStrength = 100, onGestureStrength = { picked += "gesture=$it" },
            )
        }
        val setProgress = androidx.compose.ui.semantics.SemanticsActions.SetProgress
        compose.onNodeWithTag("haptic-scrub").performSemanticsAction(setProgress) { it(0f) }
        compose.onNodeWithTag("haptic-gesture").performSemanticsAction(setProgress) { it(37f) }
        // Snaps to the step, and past the end sits at the end.
        compose.onNodeWithTag("haptic-scrub").performSemanticsAction(setProgress) { it(1000f) }
        assertEquals(listOf("scrub=0", "gesture=35", "scrub=100"), picked)
        on = false
        compose.waitForIdle()
        compose.onAllNodesWithTag("haptic-scrub").assertCountEquals(0)
    }

    // Tall, so the whole page is on screen: a lazy list only holds the rows that fit, and the strength rows push the guide down.
    @Config(qualifiers = "w411dp-h1800dp-xxhdpi", sdk = [34])
    @Test
    fun gestures_page_shows_lock_status_haptics_and_the_gesture_list() {
        val calls = mutableListOf<String>()
        compose.setContent { GesturesSettings(lockServiceOn = false, haptics = true, onHaptics = { calls += "haptics=$it" }, onOpenAccessibility = { calls += "a11y" }) }
        compose.onNodeWithText(res(R.string.settings_gestures_lock_off)).performClick()
        compose.onNodeWithTag("toggle-haptics").performClick()
        listOf(R.string.settings_gesture_search, R.string.settings_gesture_list, R.string.settings_gesture_popup, R.string.settings_gesture_actions, R.string.settings_gesture_lock)
            .forEach { compose.onNodeWithText(res(it)).assertExists() }
        assertEquals(listOf("a11y", "haptics=false"), calls)
    }

    @Config(qualifiers = "w411dp-h1800dp-xxhdpi", sdk = [34])
    @Test
    fun gestures_page_puts_the_quick_lock_prompt_first_and_drops_it_once_the_service_is_on() {
        var on by mutableStateOf(false)
        compose.setContent { GesturesSettings(lockServiceOn = on, haptics = true, onHaptics = {}, onOpenAccessibility = {}) }
        val prompt = compose.onNodeWithText(res(R.string.settings_gestures_lock_off)).fetchSemanticsNode().boundsInRoot.top
        val haptics = compose.onNodeWithText(res(R.string.settings_gestures_haptics)).fetchSemanticsNode().boundsInRoot.top
        assertTrue("prompt at $prompt should sit above haptics at $haptics", prompt < haptics)
        on = true
        compose.waitForIdle()
        compose.onAllNodesWithText(res(R.string.settings_gestures_lock_off)).assertCountEquals(0)
        compose.onAllNodesWithText(res(R.string.settings_gestures_lock)).assertCountEquals(0)
    }
}
