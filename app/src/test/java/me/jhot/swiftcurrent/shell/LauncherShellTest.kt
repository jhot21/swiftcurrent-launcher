package me.jhot.swiftcurrent.shell

import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.WatchdogTimeout
import me.jhot.swiftcurrent.agenda.AgendaEvent
import me.jhot.swiftcurrent.catalog.AppInfo
import me.jhot.swiftcurrent.core.Condition
import me.jhot.swiftcurrent.core.ContextState
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.LauncherData
import me.jhot.swiftcurrent.core.Popup
import me.jhot.swiftcurrent.core.PopupRow
import me.jhot.swiftcurrent.core.Target
import me.jhot.swiftcurrent.core.UserShortcut
import me.jhot.swiftcurrent.lock.LockResult
import me.jhot.swiftcurrent.lock.Screen
import me.jhot.swiftcurrent.search.LaunchOutcome
import me.jhot.swiftcurrent.search.SearchSource
import me.jhot.swiftcurrent.search.sources.AppSource
import me.jhot.swiftcurrent.search.sources.ContactSource
import me.jhot.swiftcurrent.search.sources.ShortcutSource
import me.jhot.swiftcurrent.search.sources.UserShortcutSource
import me.jhot.swiftcurrent.shortcuts.Creator
import me.jhot.swiftcurrent.ui.applist.ScrubberDefaults
import me.jhot.swiftcurrent.ui.applist.scrubTag
import me.jhot.swiftcurrent.ui.design.Tokens
import me.jhot.swiftcurrent.ui.editor.EditorSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CopyOnWriteArrayList
import me.jhot.swiftcurrent.core.Settings as AppSettings

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class LauncherShellTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    // A hang (an animation that never settles) fails the test instead of stalling the gate.
    @get:Rule
    val timeout = WatchdogTimeout(seconds = 120)

    private fun app(word: String) = AppInfo("pkg.${word.lowercase()}/.Main", word, "pkg.${word.lowercase()}")

    // Enough filler under each letter that the list really scrolls, so a jump to M moves it away from A.
    private val apps = listOf("Alpha", "Bravo", "Charlie", "Mike", "Sierra", "Zulu").map(::app) +
        listOf("Alpha", "Charlie", "Mike", "Sierra").flatMap { w -> (1..12).map { app("$w $it") } }

    private val folderMember = Entry("fav-bravo", Target.App("pkg.bravo/.Main"))
    private val favorites = listOf(
        Entry("fav-alpha", Target.App(ALPHA)),
        Entry("fav-gone", Target.App("pkg.gone/.Main"), customLabel = "Gone"),
        Entry("fav-bed", Target.Shortcut("$TASKER/bedtime"), customLabel = "Bedtime"),
        Entry("fav-folder", Target.Folder, customLabel = "Folder", popup = Popup(listOf(PopupRow.Item(folderMember)))),
    )

    private fun start(data: LauncherData = LauncherData(favorites = favorites)): FakeDependencies {
        val deps = FakeDependencies(data, apps)
        compose.setContent { LauncherShell(deps) }
        // The first frame is black until usage is hydrated and the store loaded.
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("clock").fetchSemanticsNodes().isNotEmpty() }
        return deps
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitForTag(tag: String) = compose.waitUntil(5_000) { exists(tag) }

    private fun waitForNoTag(tag: String) = compose.waitUntil(5_000) { !exists(tag) }

    // A sheet reaches the bottom edge and can cover the scrim's centre, so tap its top-left corner, which is always bare scrim.
    private fun tapScrim() = compose.onNodeWithTag("popup-scrim").performTouchInput { click(Offset(10f, 10f)) }

    private fun back() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    private fun swipeUpAt(xFraction: Float, yFraction: Float = 0.5f, distance: Float = 600f) {
        compose.onRoot().performTouchInput {
            swipe(Offset(width * xFraction, height * yFraction), Offset(width * xFraction, height * yFraction - distance))
        }
    }

    private fun openList(deps: FakeDependencies) {
        compose.runOnUiThread { deps.session.navigate(Screen.AppList) }
        waitForTag("applist")
    }

    // A letter's centre in the scrubber strip's own coordinates, where touch input is injected.
    private fun touchPointOf(c: Char): Offset {
        val strip = compose.onNodeWithTag("scrubber").getBoundsInRoot()
        val letter = compose.onNodeWithTag(scrubTag(c), useUnmergedTree = true).getBoundsInRoot()
        val density = compose.activity.resources.displayMetrics.density
        return Offset(
            ((strip.right - strip.left).value / 2f) * density,
            ((letter.top + letter.bottom).value / 2f - strip.top.value) * density,
        )
    }

    private fun listTop() = compose.onNodeWithTag("applist", useUnmergedTree = true).getBoundsInRoot()

    // The block (header plus rows) sits in the middle of the list's visible area, within one row.
    private fun blockIsCentred(key: Char, rows: Int): Boolean {
        if (!exists("header-$key")) return false
        val row = Tokens.rowHeight.value
        val list = listTop()
        val visible = list.bottom.value - list.top.value - (Tokens.searchButtonBottom + Tokens.searchButtonSize).value
        val above = compose.onNodeWithTag("header-$key", useUnmergedTree = true).getBoundsInRoot().top.value - list.top.value
        val below = visible - above - (rows + 1) * row
        return above > 0f && Math.abs(above - below) <= row
    }

    @Test
    fun scrubbing_to_a_short_letter_centres_its_block_and_lifting_keeps_it_there() {
        val deps = start()
        openList(deps)
        tapLetter('B')
        compose.waitUntil(5_000) { blockIsCentred('B', rows = 1) }
        compose.waitForIdle()
        assertTrue(blockIsCentred('B', rows = 1))
    }

    private fun tapLetter(c: Char) {
        val at = touchPointOf(c)
        compose.onNodeWithTag("scrubber").performTouchInput { down(at); up() }
    }

    @Test
    fun home_shows_favorites_from_the_store_and_hides_uninstalled_ones() {
        start()
        compose.onNodeWithTag("clock").assertIsDisplayed()
        compose.onNodeWithText("8:40").assertIsDisplayed()
        compose.onNodeWithText("Sun, Nov 8").assertIsDisplayed()
        // The app favorite is labelled from the catalog, the others by their saved label.
        compose.onNodeWithTag("favorite-fav-alpha").assertIsDisplayed()
        compose.onNodeWithText("Alpha").assertIsDisplayed()
        compose.onNodeWithTag("favorite-fav-bed").assertIsDisplayed()
        compose.onNodeWithTag("favorite-fav-folder").assertIsDisplayed()
        compose.onAllNodesWithTag("favorite-fav-gone").assertCountEquals(0)
    }

    // 10:23 UTC on Sunday 8 Nov 2026; the fake zone is UTC so times read the same everywhere.
    private val agendaNow = java.time.Instant.parse("2026-11-08T10:23:00Z")
    private fun agendaEvent(title: String, at: String, minutes: Long = 30, calendar: Long = 1, id: Long = 1) =
        java.time.Instant.parse(at).let { AgendaEvent(title, it, it.plusSeconds(minutes * 60), false, null, 0x2E7D32, calendar, id) }

    private fun startWithAgenda(
        events: List<AgendaEvent>,
        data: LauncherData = LauncherData(favorites = favorites),
        calendarGranted: Boolean = true,
        loadDelayMs: Long = 0,
    ): FakeDependencies {
        val deps = FakeDependencies(data, apps)
        deps.fakeCalendar.loadDelayMs = loadDelayMs
        deps.nowMillis = agendaNow.toEpochMilli()
        deps.calendarGranted = calendarGranted
        deps.fakeCalendar.events = events
        compose.setContent { LauncherShell(deps) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("clock").fetchSemanticsNodes().isNotEmpty() }
        return deps
    }

    private val teamSync get() = agendaEvent("Team sync", "2026-11-08T11:30:00Z", id = 21)
    private val standup get() = agendaEvent("Standup", "2026-11-08T15:00:00Z", id = 22)

    @Test
    fun home_shows_the_next_event_with_how_many_more_follow_that_day() {
        startWithAgenda(listOf(standup, teamSync))
        waitForTag("event")
        compose.onNodeWithText("Team sync \u00b7 11:30 AM +1").assertIsDisplayed()
    }

    @Test
    fun no_events_hides_the_event_line_and_the_sheet_says_nothing_is_coming_up() {
        // The slow read pins the race: the date is only tappable-as-agenda once the first refresh has landed.
        val deps = startWithAgenda(emptyList(), loadDelayMs = 300)
        compose.waitUntil(5_000) { deps.fakeCalendar.loads.get() > 0 }
        compose.waitForIdle()
        assertTrue(!exists("event"))
        compose.onNodeWithTag("date").performClick()
        waitForTag("agenda-sheet")
        compose.onNodeWithText("Nothing coming up").assertIsDisplayed()
        // Today still has its New event row, so an event can be added when nothing is coming up.
        compose.onNodeWithText("today").assertIsDisplayed()
        compose.onAllNodesWithTag("agenda-new-event").assertCountEquals(1)
        compose.onNodeWithTag("agenda-new-event").performClick()
        compose.waitUntil(5_000) { deps.started.isNotEmpty() }
        val insert = deps.started.single()
        assertEquals(Intent.ACTION_INSERT, insert.action)
        // 9:00 on the sheet's day in the zone the shell was given (pinned in the fake), never the machine's.
        assertEquals(
            java.time.LocalDate.of(2026, 11, 8).atTime(9, 0).atZone(deps.zone()).toInstant().toEpochMilli(),
            insert.getLongExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1),
        )
    }

    @Test
    fun revoked_permission_hides_agenda_block() {
        val deps = startWithAgenda(listOf(teamSync), calendarGranted = false)
        // The permission check is what hides the line, so wait for it, and prove the agenda was never read.
        compose.waitUntil(5_000) { deps.calendarChecks.get() > 0 }
        compose.waitForIdle()
        assertEquals("a revoked permission is never read through", 0, deps.fakeCalendar.loads.get())
        assertTrue(!exists("event"))
        // The date still answers a tap: it offers to grant access instead of opening an empty agenda.
        compose.onNodeWithTag("date").performClick()
        waitForTag("shell-message")
        compose.onNodeWithText("Allow calendar access to see your agenda").assertIsDisplayed()
        assertTrue(!exists("agenda-sheet"))
        compose.onNodeWithTag("shell-message-action").performClick()
        compose.waitUntil(5_000) { deps.started.isNotEmpty() }
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, deps.started.single().action)
        assertEquals("package:${compose.activity.packageName}", deps.started.single().dataString)
    }

    @Test
    fun tapping_the_event_line_opens_the_agenda_sheet_and_back_and_screen_off_close_it() {
        val deps = startWithAgenda(listOf(teamSync, standup))
        waitForTag("event")
        compose.onNodeWithTag("event").performClick()
        waitForTag("agenda-sheet")
        compose.onNodeWithText("Your agenda").assertIsDisplayed()
        compose.onNodeWithText("today").assertIsDisplayed()
        // Gestures are off under the sheet, like any overlay.
        swipeUpAt(0.4f)
        compose.waitForIdle()
        assertEquals(Screen.Home, deps.session.screen.value)
        back()
        waitForNoTag("agenda-sheet")
        assertEquals(Screen.Home, deps.session.screen.value)

        compose.onNodeWithTag("date").performClick()
        waitForTag("agenda-sheet")
        compose.runOnUiThread { deps.session.onScreenOff() }
        waitForNoTag("agenda-sheet")
    }

    @Test
    fun new_event_and_event_taps_open_the_calendar_app() {
        val deps = startWithAgenda(listOf(teamSync))
        waitForTag("event")
        compose.onNodeWithTag("date").performClick()
        waitForTag("agenda-sheet")
        compose.onNodeWithText("Team sync").performClick()
        compose.waitUntil(5_000) { deps.started.isNotEmpty() }
        val view = deps.started.single()
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals("${android.provider.CalendarContract.Events.CONTENT_URI}/21", view.dataString)
        waitForNoTag("agenda-sheet")

        compose.onNodeWithTag("date").performClick()
        waitForTag("agenda-sheet")
        deps.startOutcome = LaunchOutcome.Failed("No calendar app")
        compose.onNodeWithTag("agenda-new-event").performClick()
        compose.waitUntil(5_000) { deps.started.size == 2 }
        val insert = deps.started.last()
        assertEquals(Intent.ACTION_INSERT, insert.action)
        // 9:00 on the sheet's day in the zone the shell was given (pinned in the fake), never the machine's.
        assertEquals(
            java.time.LocalDate.of(2026, 11, 8).atTime(9, 0).atZone(deps.zone()).toInstant().toEpochMilli(),
            insert.getLongExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1),
        )
        // A calendar app that cannot start shows a message instead of failing silently.
        waitForTag("shell-message")
        compose.onNodeWithText("No calendar app").assertIsDisplayed()
    }

    @Test
    fun disabled_calendars_are_filtered_out_of_the_home_line() {
        val data = LauncherData(favorites = favorites, settings = AppSettings(enabledCalendars = setOf(2L)))
        startWithAgenda(
            listOf(teamSync.copy(title = "Hidden"), standup.copy(calendarId = 2)),
            data,
        )
        waitForTag("event")
        compose.onNodeWithText("Standup \u00b7 3:00 PM").assertIsDisplayed()
        compose.onAllNodesWithText("Hidden", substring = true).assertCountEquals(0)
    }

    @Test
    fun permissions_are_read_again_when_the_launcher_is_shown() {
        val deps = startWithAgenda(listOf(teamSync), calendarGranted = false)
        compose.waitUntil(5_000) { deps.calendarChecks.get() > 0 }
        compose.waitForIdle()
        assertTrue(!exists("event"))
        // Granted calendar, revoked contacts in system settings, then back to the launcher.
        deps.calendarGranted = true
        deps.contactsGranted = false
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        waitForTag("event")
        compose.runOnUiThread { deps.session.navigate(Screen.Search) }
        waitForTag("contacts-prompt")

        // And a revoke takes the agenda block away again.
        compose.runOnUiThread { deps.session.navigate(Screen.Home) }
        deps.calendarGranted = false
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        waitForNoTag("event")
    }

    @Test
    fun a_launch_adds_once_to_the_stored_count() {
        // Stored count 5. Startup does not wait for it, so the launch may come first; either way it adds exactly one.
        val deps = start(LauncherData(favorites = favorites, launchCounts = mapOf(ALPHA to 5)))
        compose.onNodeWithTag("favorite-fav-alpha").performClick()
        compose.waitUntil(5_000) { deps.launches.isNotEmpty() }
        compose.waitUntil(5_000) { kotlinx.coroutines.runBlocking { deps.usageDao.counts() }.any { it.component == ALPHA && it.count == 6 } }
        assertEquals(6, deps.usage.counts()[ALPHA])
    }

    @Test
    fun search_pick_is_recorded_once() {
        val deps = start()
        searchFor(deps, "alpha")
        waitForTag("result-app:$ALPHA")
        compose.onNodeWithTag("result-app:$ALPHA").performClick()
        compose.waitUntil(5_000) { deps.launches.isNotEmpty() }
        waitForNoTag("search-field")
        compose.waitUntil(5_000) { kotlinx.coroutines.runBlocking { deps.usageDao.allSelections() }.isNotEmpty() }
        compose.waitForIdle()
        // One pick, kept with its query; a second record (the source's own) would have counted 2.
        assertEquals(1, deps.usage.counts()[ALPHA])
        val rows = kotlinx.coroutines.runBlocking { deps.usageDao.allSelections() }
        assertEquals(listOf(Triple("app:$ALPHA", "alpha", me.jhot.swiftcurrent.core.db.PickSource.SEARCH)), rows.map { Triple(it.resultId, it.query, it.source) })
        assertEquals(listOf(1), kotlinx.coroutines.runBlocking { deps.usageDao.counts() }.filter { it.component == ALPHA }.map { it.count })
    }

    private val learningOff = LauncherData(favorites = favorites, settings = me.jhot.swiftcurrent.core.Settings(learnFromPicks = false))

    private fun storedCount(deps: FakeDependencies, component: String) =
        kotlinx.coroutines.runBlocking { deps.usageDao.counts() }.firstOrNull { it.component == component }?.count

    private fun storedSelections(deps: FakeDependencies) = kotlinx.coroutines.runBlocking { deps.usageDao.allSelections() }

    @Test
    fun with_learning_off_a_search_launch_counts_once_and_keeps_no_pick() {
        val deps = start(learningOff)
        val picksBefore = deps.usage.picks()
        searchFor(deps, "alpha")
        waitForTag("result-app:$ALPHA")
        compose.onNodeWithTag("result-app:$ALPHA").performClick()
        compose.waitUntil(5_000) { deps.launches.isNotEmpty() }
        // The count and any selection row are written in one transaction, so once the count is stored a row would be too.
        compose.waitUntil(5_000) { storedCount(deps, ALPHA) != null }
        compose.waitForIdle()
        assertEquals(1, deps.usage.counts()[ALPHA])
        assertEquals(1, storedCount(deps, ALPHA))
        assertTrue(storedSelections(deps).isEmpty())
        org.junit.Assert.assertSame(picksBefore, deps.usage.picks())
    }

    @Test
    fun with_learning_off_picking_a_shortcut_in_search_writes_nothing() {
        val deps = start(learningOff)
        val picksBefore = deps.usage.picks()
        searchFor(deps, "bedtime")
        waitForTag("result-shortcut:$TASKER/bedtime")
        compose.onNodeWithTag("result-shortcut:$TASKER/bedtime").performClick()
        compose.waitUntil(5_000) { deps.shortcuts.launched.isNotEmpty() }
        compose.waitForIdle()
        // Memory changes as soon as anything is recorded, so an empty memory means nothing was recorded.
        assertTrue(deps.usage.counts().isEmpty())
        org.junit.Assert.assertSame(picksBefore, deps.usage.picks())
        assertTrue(kotlinx.coroutines.runBlocking { deps.usageDao.counts() }.isEmpty())
        assertTrue(storedSelections(deps).isEmpty())
    }

    private val alexPhone = me.jhot.swiftcurrent.search.sources.ContactRow(5, "Alex Example", "vnd.android.cursor.item/phone_v2", "+15550100")

    @Test
    fun picking_call_on_a_contact_records_the_contact_with_the_action() {
        val deps = FakeDependencies(LauncherData(favorites = favorites), apps).apply { contactRows = listOf(alexPhone) }
        compose.setContent { LauncherShell(deps) }
        waitForTag("clock")
        searchFor(deps, "alex")
        waitForTag("action-contact:5-Call")
        compose.onNodeWithTag("action-contact:5-Call", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { deps.started.isNotEmpty() }
        compose.waitUntil(5_000) { storedSelections(deps).isNotEmpty() }
        // Ranking learns the contact; the call is kept beside it for recents.
        assertEquals(
            listOf("contact:5" to "contact:5|call|+15550100"),
            storedSelections(deps).map { it.resultId to it.actionId },
        )
    }

    @Test
    fun picking_a_folded_shortcut_records_the_contact_with_the_shortcut_id() {
        val deps = FakeDependencies(LauncherData(favorites = favorites), apps).apply { contactRows = listOf(alexPhone) }
        deps.shortcuts.extraEntries = listOf(
            me.jhot.swiftcurrent.shortcuts.ShortcutEntry("com.example.chat/alex", "Chat", "com.example.chat", "alex", "Alex Example"),
        )
        compose.setContent { LauncherShell(deps) }
        waitForTag("clock")
        searchFor(deps, "alex")
        waitForTag("action-contact:5-Chat")
        compose.onNodeWithTag("action-contact:5-Chat", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { deps.shortcuts.launched.isNotEmpty() }
        compose.waitUntil(5_000) { storedSelections(deps).isNotEmpty() }
        assertEquals(
            listOf("contact:5" to "shortcut:com.example.chat/alex"),
            storedSelections(deps).map { it.resultId to it.actionId },
        )
    }

    private val mattPhone = me.jhot.swiftcurrent.search.sources.ContactRow(7, "Matt Lastname", "vnd.android.cursor.item/phone_v2", "+15550177")
    private val mattCall = "contact:7|call|+15550177"
    private val mattChat = "shortcut:com.example.chat/matt"

    private fun startWithMatt(data: LauncherData = LauncherData(favorites = favorites)): FakeDependencies {
        val deps = FakeDependencies(data, apps).apply { contactRows = listOf(mattPhone) }
        deps.shortcuts.extraEntries = listOf(
            me.jhot.swiftcurrent.shortcuts.ShortcutEntry("com.example.chat/matt", "Chat", "com.example.chat", "matt", "Matt Lastname"),
        )
        compose.setContent { LauncherShell(deps) }
        waitForTag("clock")
        return deps
    }

    /** Of [ids], the ones shown as result rows, top first. */
    private fun rowsShown(vararg ids: String) =
        ids.filter { exists("result-$it") }.sortedBy { compose.onNodeWithTag("result-$it").getBoundsInRoot().top.value }

    private fun pickThenClose(deps: FakeDependencies, picks: Int, tap: () -> Unit) {
        tap()
        compose.waitUntil(5_000) { storedSelections(deps).size == picks }
        waitForNoTag("search-field")
    }

    @Test
    fun a_picked_result_shows_in_recents_next_time() {
        val deps = startWithMatt()
        searchFor(deps, "alpha")
        waitForTag("result-app:$ALPHA")
        pickThenClose(deps, 1) { compose.onNodeWithTag("result-app:$ALPHA").performClick() }
        searchFor(deps, "matt")
        waitForTag("action-contact:7-Call")
        pickThenClose(deps, 2) { compose.onNodeWithTag("action-contact:7-Call", useUnmergedTree = true).performClick() }
        searchFor(deps, "matt")
        waitForTag("action-contact:7-Chat")
        pickThenClose(deps, 3) { compose.onNodeWithTag("action-contact:7-Chat", useUnmergedTree = true).performClick() }

        compose.runOnUiThread { deps.session.navigate(Screen.Search) }
        waitForTag("search-field")
        waitForTag("result-app:$ALPHA")
        // Newest first; the folded shortcut shows as itself and the call as "Call: <name>", not as the contact.
        assertEquals(
            listOf(mattChat, mattCall, "app:$ALPHA"),
            rowsShown(mattChat, mattCall, "app:$ALPHA", "contact:7"),
        )
        compose.onNodeWithText("Call: Matt Lastname").assertIsDisplayed()
        compose.onNodeWithText("Chat: Matt Lastname").assertIsDisplayed()
    }

    @Test
    fun tapping_a_recents_row_records_a_normal_pick() {
        val deps = startWithMatt()
        searchFor(deps, "matt")
        waitForTag("action-contact:7-Call")
        pickThenClose(deps, 1) { compose.onNodeWithTag("action-contact:7-Call", useUnmergedTree = true).performClick() }
        searchFor(deps, "alpha")
        waitForTag("result-app:$ALPHA")
        pickThenClose(deps, 2) { compose.onNodeWithTag("result-app:$ALPHA").performClick() }

        compose.runOnUiThread { deps.session.navigate(Screen.Search) }
        waitForTag("result-$mattCall")
        pickThenClose(deps, 3) { compose.onNodeWithTag("result-$mattCall").performClick() }
        compose.runOnUiThread { deps.session.navigate(Screen.Search) }
        waitForTag("result-app:$ALPHA")
        pickThenClose(deps, 4) { compose.onNodeWithTag("result-app:$ALPHA").performClick() }

        // Ranking still learns the contact for the call; nothing was typed, so no query is kept.
        val fromRecents = storedSelections(deps).sortedBy { it.id }.drop(2).map { Triple(it.resultId, it.actionId, it.query) }
        assertEquals(listOf(Triple("contact:7", mattCall, null), Triple<String, String?, String?>("app:$ALPHA", null, null)), fromRecents)
    }

    @Test
    fun tapping_a_recents_folded_shortcut_row_credits_the_contact_and_keeps_the_shortcut_entry() {
        val deps = startWithMatt()
        searchFor(deps, "matt")
        waitForTag("action-contact:7-Chat")
        pickThenClose(deps, 1) { compose.onNodeWithTag("action-contact:7-Chat", useUnmergedTree = true).performClick() }
        compose.runOnUiThread { deps.session.navigate(Screen.Search) }
        waitForTag("result-$mattChat")
        pickThenClose(deps, 2) { compose.onNodeWithTag("result-$mattChat").performClick() }
        // The recents entry remembers whose action it was, so this credits Matt (as the first pick did) and keeps
        // the shortcut as the entry, with no query.
        val again = storedSelections(deps).maxBy { it.id }
        assertEquals(Triple<String, String?, String?>("contact:7", mattChat, null), Triple(again.resultId, again.actionId, again.query))
        assertEquals(listOf(mattChat), deps.usage.recents().filter { it == mattChat })
    }

    /** Waits a bounded time for something that must not happen, so an absence is not asserted before it could show. */
    private fun allowTimeFor(condition: () -> Boolean) {
        runCatching { compose.waitUntil(2_000, condition) }
        compose.waitForIdle()
    }

    @Test
    fun recents_are_hidden_when_learning_is_off() {
        val deps = startWithMatt()
        deps.usage.recordPick("contact:7", me.jhot.swiftcurrent.search.Kind.Contact, "matt")
        // With learning on, the same history shows, and resolving it reads the contacts provider once.
        compose.runOnUiThread { deps.session.navigate(Screen.Search) }
        waitForTag("result-contact:7")
        assertEquals(1, deps.contactResolves.get())
        compose.runOnUiThread { deps.session.navigate(Screen.Settings) }
        waitForTag("settings-root")
        compose.onNodeWithText(compose.activity.getString(me.jhot.swiftcurrent.R.string.settings_search)).performClick()
        waitForTag("toggle-learn-from-picks")
        compose.onNodeWithTag("toggle-learn-from-picks").performClick()
        compose.waitUntil(5_000) { !deps.fakeStore.data.settings.learnFromPicks }
        compose.waitForIdle()
        // The history is kept, but the next open neither resolves it nor shows it.
        compose.runOnUiThread { deps.session.navigate(Screen.Search) }
        waitForTag("search-field")
        allowTimeFor { deps.contactResolves.get() > 1 || exists("result-contact:7") }
        assertEquals(1, deps.contactResolves.get())
        assertFalse(exists("result-contact:7"))
        assertEquals(listOf("contact:7"), deps.usage.recents())
    }

    @Test
    fun with_learning_off_a_home_launch_counts_without_a_launcher_pick() {
        val deps = start(learningOff)
        compose.onNodeWithTag("favorite-fav-alpha").performClick()
        compose.waitUntil(5_000) { deps.launches.isNotEmpty() }
        compose.waitUntil(5_000) { storedCount(deps, ALPHA) != null }
        assertEquals(1, storedCount(deps, ALPHA))
        assertTrue(storedSelections(deps).isEmpty())
    }

    @Test
    fun with_learning_on_a_home_launch_keeps_one_launcher_pick() {
        val deps = start()
        compose.onNodeWithTag("favorite-fav-alpha").performClick()
        compose.waitUntil(5_000) { deps.launches.isNotEmpty() }
        compose.waitUntil(5_000) { storedCount(deps, ALPHA) != null }
        assertEquals(1, storedCount(deps, ALPHA))
        assertEquals(
            listOf(Triple<String, String?, String>("app:$ALPHA", null, me.jhot.swiftcurrent.core.db.PickSource.LAUNCHER)),
            storedSelections(deps).map { Triple(it.resultId, it.query, it.source) },
        )
    }

    @Test
    fun swipe_up_on_home_opens_search_and_back_returns_home() {
        val deps = start()
        swipeUpAt(0.4f)
        waitForTag("search-field")
        assertEquals(Screen.Search, deps.session.screen.value)
        back()
        waitForNoTag("search-field")
        assertEquals(Screen.Home, deps.session.screen.value)
        compose.onNodeWithTag("clock").assertIsDisplayed()
    }

    @Test
    fun edge_swipe_up_opens_the_app_list_and_back_closes_it_and_removes_it() {
        val deps = start()
        // Ends on a letter low on the strip; ending at the star or above it would go back to favorites.
        swipeUpAt(0.95f, yFraction = 0.95f, distance = 150f)
        waitForTag("applist")
        assertEquals(Screen.AppList, deps.session.screen.value)
        back()
        // The close slides off, then the screen leaves composition so nothing is left over Home.
        waitForNoTag("applist")
        assertEquals(Screen.Home, deps.session.screen.value)
        // Home is usable again: a swipe up still reaches the gesture host.
        swipeUpAt(0.4f)
        waitForTag("search-field")
    }

    @Test
    fun gestures_are_disabled_while_an_overlay_is_open() {
        val deps = start()
        compose.onNodeWithTag("favorite-fav-alpha").performTouchInput { longClick() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("App info").fetchSemanticsNodes().isNotEmpty() }
        // The sheet opened while the finger was down; its release must not tap the row underneath.
        compose.waitForIdle()
        assertTrue(deps.launches.isEmpty())
        compose.onNodeWithText("App info").assertIsDisplayed()
        // Started on the sheet itself: a swipe that starts on the scrim closes the sheet, like a tap on it.
        swipeUpAt(0.4f, 0.9f)
        compose.waitForIdle()
        assertEquals(Screen.Home, deps.session.screen.value)
        assertTrue(!exists("search-field"))
        swipeUpAt(0.95f, 0.9f)
        compose.waitForIdle()
        assertEquals(Screen.Home, deps.session.screen.value)
        // Back closes the sheet, and gestures work again.
        back()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("App info").fetchSemanticsNodes().isEmpty() }
        swipeUpAt(0.4f)
        waitForTag("search-field")
    }

    @Test
    fun tapping_a_folder_opens_its_popup_and_back_dismisses_it() {
        val deps = start()
        compose.onNodeWithTag("favorite-fav-folder").performClick()
        waitForTag("popup-scrim")
        compose.onNodeWithText("Bravo").assertIsDisplayed()
        back()
        waitForNoTag("popup-scrim")
        // Back only closed the pop-up: the launcher is still on Home and nothing launched.
        assertEquals(Screen.Home, deps.session.screen.value)
        assertTrue(deps.launches.isEmpty())
        compose.onNodeWithTag("clock").assertIsDisplayed()
    }

    @Test
    fun tapping_an_app_records_usage_then_launches_it() {
        val deps = start()
        compose.onNodeWithTag("favorite-fav-alpha").performClick()
        compose.waitUntil(5_000) { deps.launches.isNotEmpty() }
        assertEquals(listOf(ALPHA), deps.launches.toList())
        // Recorded before the launch call, not after.
        assertEquals(listOf(1), deps.countsAtLaunch.toList())
        compose.onNodeWithTag("favorite-fav-bed").performClick()
        compose.waitUntil(5_000) { deps.shortcuts.launched.isNotEmpty() }
        assertEquals(listOf("$TASKER/bedtime"), deps.shortcuts.launched.toList())
    }

    @Test
    fun failed_launch_shows_a_message() {
        val deps = start()
        deps.launchOutcome = LaunchOutcome.Failed("That app is gone")
        compose.onNodeWithTag("favorite-fav-alpha").performClick()
        waitForTag("shell-message")
        compose.onNodeWithText("That app is gone").assertIsDisplayed()
        // Tapping the bar dismisses it.
        compose.onNodeWithTag("shell-message").performClick()
        waitForNoTag("shell-message")
    }

    @Test
    fun clock_tap_starts_the_default_clock_app() {
        val deps = start()
        compose.onNodeWithTag("clock").performClick()
        compose.waitUntil(5_000) { deps.started.isNotEmpty() }
        assertEquals(AlarmClock.ACTION_SHOW_ALARMS, deps.started.single().action)
    }

    @Test
    fun a_clock_app_that_cannot_start_shows_a_message() {
        val deps = start()
        deps.startOutcome = LaunchOutcome.Failed("No clock app")
        compose.onNodeWithTag("clock").performClick()
        waitForTag("shell-message")
        compose.onNodeWithText("No clock app").assertIsDisplayed()
    }

    @Test
    fun double_tap_on_the_alphabet_locks_and_returns_to_home() {
        val deps = start()
        openList(deps)
        val tokenBefore = deps.session.resetToken.value
        tapLetter('B')
        // The first tap only jumps; B (a short block) is now centred, away from the top.
        compose.waitUntil(5_000) { blockIsCentred('B', rows = 1) }
        assertEquals(0, deps.fakeLock.calls)
        tapLetter('C')
        compose.waitUntil(5_000) { deps.fakeLock.calls == 1 }
        waitForNoTag("applist")
        assertEquals(Screen.Home, deps.session.screen.value)
        assertNotEquals(tokenBefore, deps.session.resetToken.value)
        // Opening the list again starts at the top: the scroll was discarded.
        openList(deps)
        compose.onNodeWithTag("header-A", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun a_lock_the_connected_service_refuses_shows_a_failure_not_the_enable_prompt() {
        val deps = start()
        deps.fakeLock.result = LockResult.Failed
        openList(deps)
        tapLetter('B')
        tapLetter('C')
        waitForTag("shell-message")
        compose.onNodeWithText(res(R.string.lock_failed)).assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText(res(R.string.lock_service_disabled)).fetchSemanticsNodes().isEmpty())
        assertTrue(!exists("shell-message-action"))
    }

    @Test
    fun lock_with_service_disabled_shows_a_message_with_a_link() {
        val deps = start()
        deps.fakeLock.result = LockResult.ServiceDisabled
        openList(deps)
        tapLetter('B')
        tapLetter('C')
        waitForTag("shell-message")
        compose.onNodeWithText("Turn on the Swiftcurrent accessibility service to lock the screen.").assertIsDisplayed()
        // Nothing was locked, so the list stays.
        assertTrue(exists("applist"))
        compose.onNodeWithTag("shell-message-action").performClick()
        compose.waitUntil(5_000) { deps.started.isNotEmpty() }
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, deps.started.single().action)
    }

    @Test
    fun screen_off_resets_to_home_and_clears_open_overlays() {
        val deps = start()
        compose.onNodeWithTag("favorite-fav-folder").performClick()
        waitForTag("popup-scrim")
        compose.runOnUiThread { deps.session.onScreenOff() }
        waitForNoTag("popup-scrim")
        compose.onNodeWithTag("clock").assertIsDisplayed()

        compose.onNodeWithTag("favorite-fav-alpha").performTouchInput { longClick() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("App info").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnUiThread { deps.session.onScreenOff() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("App info").fetchSemanticsNodes().isEmpty() }

        openList(deps)
        compose.runOnUiThread { deps.session.onScreenOff() }
        waitForNoTag("applist")
        assertEquals(Screen.Home, deps.session.screen.value)
    }

    @Test
    fun launching_from_the_list_goes_home_without_sliding() {
        val deps = start()
        openList(deps)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("row-pkg.alpha 1/.Main", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(Screen.Home, deps.session.screen.value)
        assertFalse(exists("applist"))
    }

    @Test
    fun home_press_with_the_list_open_goes_home_without_sliding() {
        val deps = start()
        openList(deps)
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { deps.session.onHomePressed() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(Screen.Home, deps.session.screen.value)
        assertFalse(exists("applist"))
    }

    @Test
    fun tapping_a_favorite_mid_slide_does_not_launch_it() {
        val deps = start()
        val density = compose.activity.resources.displayMetrics.density
        val b = compose.onNodeWithTag("favorite-fav-alpha").getBoundsInRoot()
        // The left gutter of the favorite, on Home's side of the sliding list: only a leak through the layer could answer a tap here.
        val at = Offset(40f * density, (b.top + b.bottom).value / 2f * density)
        openList(deps)
        compose.mainClock.autoAdvance = false
        back()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(60)
        // Really mid-slide, and the tap lands on the part of Home the list has already left.
        val listLeft = compose.onNodeWithTag("applist", useUnmergedTree = true).getBoundsInRoot().left.value * density
        assertTrue("list left $listLeft should be right of the tap ${at.x}", listLeft > at.x)
        // Home's rows are hidden from accessibility under the list, so tap by position, on the favorite's spot.
        compose.onRoot().performTouchInput { click(at) }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        // Launches run on a real IO thread, so give a leaked one time to land before asserting there is none.
        Thread.sleep(300)
        assertTrue("a tap during the slide launched ${deps.launches.toList()}", deps.launches.isEmpty())
        // Positive control: the same tap launches the favorite once the list is gone, so the position was a real hit.
        compose.mainClock.autoAdvance = true
        waitForNoTag("applist")
        compose.onRoot().performTouchInput { click(at) }
        compose.waitUntil(5_000) { deps.launches.isNotEmpty() }
    }

    @Test
    fun an_overlay_never_survives_a_screen_change() {
        val deps = start()
        compose.onNodeWithTag("favorite-fav-alpha").performTouchInput { longClick() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("App info").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnUiThread { deps.session.navigate(Screen.AppList) }
        waitForTag("applist")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("App info").fetchSemanticsNodes().isEmpty() }
        compose.runOnUiThread { deps.session.navigate(Screen.Home) }
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText("App info").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun a_null_editor_target_navigates_home() {
        val deps = start()
        // The editor screen with nothing to edit (state lost): never an empty layer that eats every touch.
        compose.runOnUiThread { deps.session.navigate(Screen.Editor) }
        compose.waitUntil(5_000) { deps.session.screen.value == Screen.Home }
        compose.onNodeWithTag("favorite-fav-alpha").performClick()
        compose.waitUntil(5_000) { deps.launches.isNotEmpty() }
    }

    @Test
    fun editing_survives_a_configuration_change_without_losing_earlier_ticks() {
        // Without the saved Bedtime favorite, so all three Tasker shortcuts can be ticked.
        val start = favorites.filter { it.id != "fav-bed" }
        val deps = FakeDependencies(LauncherData(favorites = start), apps)
        var config by androidx.compose.runtime.mutableStateOf<android.content.res.Configuration?>(null)
        compose.setContent {
            val base = androidx.compose.ui.platform.LocalConfiguration.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalConfiguration provides (config ?: base),
            ) { LauncherShell(deps) }
        }
        compose.waitUntil(5_000) { exists("clock") }
        openFavoritesEditor()
        fun tick(key: String) {
            val at = touchPointOf(EditorSections.SHORTCUTS)
            compose.onNodeWithTag("scrubber").performTouchInput { down(at); up() }
            // Tasker's three shortcuts are one collapsible group; the first tick opens it, and it stays open after.
            waitForTag("group-Tasker")
            if (!exists("pick-Shortcuts-$key")) compose.onNodeWithTag("group-Tasker").performClick()
            waitForTag("pick-Shortcuts-$key")
            compose.onNodeWithTag("pick-Shortcuts-$key").performClick()
            compose.waitUntil(5_000) { deps.fakeStore.data.favorites.any { it.target == Target.Shortcut(key) } }
        }
        tick("$TASKER/work")
        tick("$TASKER/home")
        // A handled configuration change (dark mode, font size, language) recomposes the editor.
        compose.runOnUiThread {
            config = android.content.res.Configuration(compose.activity.resources.configuration).apply {
                uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or android.content.res.Configuration.UI_MODE_NIGHT_YES
                fontScale = 1.3f
            }
        }
        compose.waitForIdle()
        tick("$TASKER/bedtime")
        val saved = deps.fakeStore.data.favorites.map { it.target }
        assertTrue(saved.toString(), Target.Shortcut("$TASKER/work") in saved)
        assertTrue(saved.toString(), Target.Shortcut("$TASKER/home") in saved)
        assertTrue(saved.toString(), Target.Shortcut("$TASKER/bedtime") in saved)
        assertEquals(start.size + 3, deps.fakeStore.data.favorites.size)
    }

    private fun openSheetOf(tag: String) {
        compose.onNodeWithTag(tag).performTouchInput { longClick() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(res(R.string.action_sheet_edit_favorites)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun action_sheet_app_info_opens_the_details_screen_of_that_apps_package() {
        val deps = start()
        openSheetOf("favorite-fav-alpha")
        compose.onNodeWithText(res(R.string.action_sheet_app_info)).performClick()
        compose.waitUntil(5_000) { deps.started.isNotEmpty() }
        val intent = deps.started.single()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:pkg.alpha", intent.dataString)
        compose.waitUntil(5_000) { compose.onAllNodesWithText(res(R.string.action_sheet_app_info)).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun action_sheet_hide_hides_the_app_through_the_store_and_closes_the_sheet() {
        val deps = start()
        openSheetOf("favorite-fav-alpha")
        compose.onNodeWithText(res(R.string.action_sheet_hide)).performClick()
        compose.waitUntil(5_000) { ALPHA in deps.fakeStore.data.hiddenApps }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(res(R.string.action_sheet_hide)).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun a_shortcut_favorites_sheet_has_no_app_rows() {
        start()
        openSheetOf("favorite-fav-bed")
        assertTrue(compose.onAllNodesWithText(res(R.string.action_sheet_app_info)).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText(res(R.string.action_sheet_hide)).fetchSemanticsNodes().isEmpty())
    }

    private fun res(id: Int, vararg args: Any): String = compose.activity.getString(id, *args)

    private fun startWithPinnedBedtime(outcome: LaunchOutcome = LaunchOutcome.Launched): FakeDependencies {
        val deps = FakeDependencies(
            LauncherData(
                favorites = favorites + Entry("fav-pop", Target.Folder, popup = Popup(listOf(PopupRow.Item(Entry("in-pop", Target.Shortcut("$TASKER/bedtime")))))),
                settings = AppSettings(disabledShortcuts = setOf("$TASKER/bedtime")),
            ),
            apps,
        )
        deps.shortcuts.pinnedKeys = setOf("$TASKER/bedtime")
        deps.shortcuts.unpinOutcome = outcome
        compose.setContent { LauncherShell(deps) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("clock").fetchSemanticsNodes().isNotEmpty() }
        return deps
    }

    @Test
    fun deleting_a_pinned_shortcut_unpins_it_and_clears_favorites_popups_and_the_disabled_key() {
        val deps = startWithPinnedBedtime()
        openSheetOf("favorite-fav-bed")
        compose.waitUntil(5_000) { exists("sheet-delete") }
        compose.onNodeWithTag("sheet-delete").performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.favorites.none { it.id == "fav-bed" } }
        assertEquals(listOf("$TASKER/bedtime"), deps.shortcuts.unpinned.toList())
        val popup = deps.fakeStore.data.favorites.single { it.id == "fav-pop" }.popup!!
        assertTrue(popup.rows.isEmpty())
        assertTrue(deps.fakeStore.data.settings.disabledShortcuts.isEmpty())
        waitForNoTag("sheet-delete")
    }

    @Test
    fun a_shortcut_that_is_not_pinned_has_no_delete_row() {
        start()
        openSheetOf("favorite-fav-bed")
        assertFalse(exists("sheet-delete"))
    }

    @Test
    fun an_app_has_no_delete_row() {
        start()
        openSheetOf("favorite-fav-alpha")
        assertFalse(exists("sheet-delete"))
    }

    @Test
    fun deleting_a_user_made_shortcut_removes_it_and_its_favorite() {
        val bob = UserShortcut("u1", "net.example.maps", "Bob", "intent:#Intent;end")
        val deps = start(LauncherData(favorites = listOf(Entry("fav-bob", Target.Shortcut("user/u1"))), userShortcuts = listOf(bob)))
        openSheetOf("favorite-fav-bob")
        compose.waitUntil(5_000) { exists("sheet-delete") }
        compose.onNodeWithTag("sheet-delete").performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.userShortcuts.isEmpty() }
        assertTrue(deps.fakeStore.data.favorites.isEmpty())
        assertTrue(deps.shortcuts.unpinned.isEmpty())
    }

    @Test
    fun a_failed_unpin_keeps_the_shortcut_and_shows_a_notice() {
        val deps = startWithPinnedBedtime(LaunchOutcome.Failed("cannot unpin"))
        openSheetOf("favorite-fav-bed")
        compose.waitUntil(5_000) { exists("sheet-delete") }
        compose.onNodeWithTag("sheet-delete").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("cannot unpin").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(deps.fakeStore.data.favorites.any { it.id == "fav-bed" })
        assertEquals(listOf("$TASKER/bedtime"), deps.shortcuts.unpinned.toList())
    }

    private fun openFavoritesEditor() {
        compose.onNodeWithTag("favorite-fav-alpha").performTouchInput { longClick() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Edit favorites").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Edit favorites").performClick()
        waitForTag("editor-header-Selected")
    }

    @Test
    fun action_sheet_edit_favorites_opens_the_editor_with_the_home_capacity() {
        val deps = start()
        openFavoritesEditor()
        assertEquals(Screen.Editor, deps.session.screen.value)
        compose.onNodeWithText(res(R.string.editor_title_favorites)).assertIsDisplayed()
        // Four saved favorites, one gone and so not counted, against the most rows home can draw (12 with no insets at 891dp).
        compose.onNodeWithText(res(R.string.editor_counter_of, 3, 12)).assertIsDisplayed()
        // Gone is flagged; gestures on Home are not reachable from here.
        compose.onNodeWithText(res(R.string.editor_not_installed)).assertIsDisplayed()
        back()
        waitForNoTag("editor-header-Selected")
        assertEquals(Screen.Home, deps.session.screen.value)
    }

    @Test
    fun editing_writes_live_and_closing_the_editor_reloads_home() {
        val deps = start()
        openFavoritesEditor()
        compose.onNodeWithTag("toggle-sel-fav-alpha").performClick()
        // Written before Done, through the store.
        compose.waitUntil(5_000) { deps.fakeStore.data.favorites.none { it.id == "fav-alpha" } }
        assertEquals(listOf("fav-gone", "fav-bed", "fav-folder"), deps.fakeStore.data.favorites.map { it.id })
        compose.onNodeWithText(res(R.string.editor_done)).performClick()
        waitForNoTag("editor-header-Selected")
        assertEquals(Screen.Home, deps.session.screen.value)
        // Home shows the edit without a restart.
        compose.waitUntil(5_000) { !exists("favorite-fav-alpha") }
        compose.onNodeWithTag("favorite-fav-bed").assertIsDisplayed()
    }

    @Test
    fun a_long_press_on_a_favorite_gives_one_gesture_tick_at_the_chosen_strength() {
        val deps = start(LauncherData(favorites = favorites, settings = me.jhot.swiftcurrent.core.Settings(setupSeen = true, gestureHaptic = 100)))
        compose.onNodeWithTag("favorite-fav-alpha").performTouchInput { longClick() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Edit favorites").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf("gesture:100"), deps.recordedHaptics.ticks.toList())
    }

    @Test
    fun a_swipe_right_on_a_favorite_gives_one_gesture_tick_and_opens_its_sheet() {
        val deps = start()
        compose.onNodeWithTag("favorite-fav-alpha").performTouchInput { swipe(Offset(100f, centerY), Offset(700f, centerY)) }
        waitForTag("popup-edit")
        assertEquals(listOf("gesture:20"), deps.recordedHaptics.ticks.toList())
    }

    @Test
    fun no_tick_when_haptics_are_off() {
        val deps = start(LauncherData(favorites = favorites, settings = me.jhot.swiftcurrent.core.Settings(setupSeen = true, haptics = false)))
        compose.onNodeWithTag("favorite-fav-alpha").performTouchInput { longClick() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Edit favorites").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(emptyList<String>(), deps.recordedHaptics.ticks.toList())
    }

    @Test
    fun pulling_the_notification_panel_ticks_once_when_armed_whether_or_not_the_system_opens_it() {
        val deps = start()
        compose.onRoot().performTouchInput { swipe(Offset(width * 0.5f, height * 0.3f), Offset(width * 0.5f, height * 0.3f + 500f)) }
        compose.waitUntil(5_000) { deps.shadePulls.get() == 1 }
        assertEquals(listOf("gesture:20"), deps.recordedHaptics.ticks.toList())
        // The tick now marks the drag becoming a pull, so a pull the system ignored (the hidden call failed) ticks too.
        deps.shadeWorks = false
        compose.onRoot().performTouchInput { swipe(Offset(width * 0.5f, height * 0.3f), Offset(width * 0.5f, height * 0.3f + 500f)) }
        compose.waitUntil(5_000) { deps.shadePulls.get() == 2 }
        assertEquals(listOf("gesture:20", "gesture:20"), deps.recordedHaptics.ticks.toList())
    }

    @Test
    fun the_search_swipe_ticks_when_armed_and_opens_search_only_on_the_lift() {
        val deps = start()
        val x = 0.4f
        compose.onRoot().performTouchInput { down(Offset(width * x, height * 0.6f)) }
        compose.onRoot().performTouchInput { moveTo(Offset(width * x, height * 0.6f - 400f)) }
        compose.waitForIdle()
        // Armed: the tick has played, and nothing has opened with the finger still down.
        assertEquals(listOf("gesture:20"), deps.recordedHaptics.ticks.toList())
        assertEquals(Screen.Home, deps.session.screen.value)
        compose.onRoot().performTouchInput { up() }
        waitForTag("search-field")
        assertEquals(Screen.Search, deps.session.screen.value)
        assertEquals(listOf("gesture:20"), deps.recordedHaptics.ticks.toList())
    }

    @Test
    fun swiping_right_on_an_app_in_search_shows_its_menu_over_the_search_and_launching_ends_the_search() {
        val deps = start()
        compose.runOnUiThread { deps.session.navigate(Screen.Search) }
        waitForTag("search-field")
        compose.onNodeWithTag("search-field").performTextInput("alpha")
        waitForTag("result-app:$ALPHA")
        compose.onNodeWithTag("result-app:$ALPHA").performTouchInput { swipe(Offset(100f, centerY), Offset(600f, centerY)) }
        waitForTag("popup-scrim")
        assertEquals(listOf("gesture:20"), deps.recordedHaptics.ticks.toList())
        // The search is still there under the menu; closing the menu returns to it.
        assertEquals(Screen.Search, deps.session.screen.value)
        tapScrim()
        waitForNoTag("popup-scrim")
        assertEquals(Screen.Search, deps.session.screen.value)
        assertTrue(deps.launches.isEmpty())
    }

    @Test
    fun swiping_right_on_an_app_in_the_list_shows_its_menu_and_closing_it_keeps_the_scroll_position() {
        val deps = start()
        openList(deps)
        tapLetter('M')
        compose.waitForIdle()
        val tag = "row-pkg.mike 1/.Main"
        waitForTag(tag)
        val before = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot().top
        compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput { swipe(Offset(100f, centerY), Offset(600f, centerY)) }
        waitForTag("popup-scrim")
        assertEquals("gesture:20", deps.recordedHaptics.ticks.last())
        // The menu opens over the list; nothing launched and the list is still the screen underneath.
        assertEquals(Screen.AppList, deps.session.screen.value)
        assertTrue(deps.launches.isEmpty())
        tapScrim()
        waitForNoTag("popup-scrim")
        assertEquals(Screen.AppList, deps.session.screen.value)
        assertEquals(before, compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot().top)
    }

    @Test
    fun swiping_an_app_inside_a_menu_opens_its_own_menu_over_it_and_back_closes_one_at_a_time() {
        val deps = start(alphaWithPopup)
        searchMenuWithOneItem(deps)
        assertEquals(1, compose.onAllNodesWithTag("popup-sheet", useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.onNodeWithTag("popup-line-0", useUnmergedTree = true).performTouchInput { swipe(Offset(100f, centerY), Offset(600f, centerY)) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("popup-sheet", useUnmergedTree = true).fetchSemanticsNodes().size == 2 }
        // Nothing launched by the swipe, and Search is still underneath both sheets.
        assertTrue(deps.launches.isEmpty())
        assertEquals(Screen.Search, deps.session.screen.value)
        back()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("popup-sheet", useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText("Bravo").assertIsDisplayed()
        back()
        waitForNoTag("popup-scrim")
        assertEquals(Screen.Search, deps.session.screen.value)
    }

    @Test
    fun the_space_below_favorites_is_adjusted_from_settings_with_the_real_home_as_the_preview() {
        val deps = start()
        compose.runOnUiThread { deps.session.navigate(Screen.Settings) }
        waitForTag("settings-root")
        compose.onNodeWithText("Home").performClick()
        waitForTag("row-spacing")
        compose.onNodeWithTag("row-spacing").performClick()
        waitForTag("settings-spacing")
        // The preview is Home itself: the clock and the owner's favorites are there under the controls.
        compose.onNodeWithTag("clock").assertIsDisplayed()
        compose.onNodeWithTag("favorite-fav-alpha").assertIsDisplayed()
        compose.onNodeWithTag("spacing-value").assertTextEquals("32 dp")
        compose.onNodeWithTag("spacing-more").performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.settings.bottomGapDp == 36 }
        // The page redraws from the reloaded store a moment after the write, so wait for it, not for the write.
        compose.waitUntil(5_000) {
            compose.onNodeWithTag("spacing-value").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text]
                .joinToString("") { it.text } == "36 dp"
        }
        compose.onNodeWithTag("spacing-reset").performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.settings.bottomGapDp == 32 }
        // Back goes to the Home settings page, not out of Settings.
        back()
        waitForTag("settings-home")
    }

    private fun searchMenuWithOneItem(deps: FakeDependencies) {
        compose.runOnUiThread { deps.session.navigate(Screen.Search) }
        waitForTag("search-field")
        compose.onNodeWithTag("search-field").performTextInput("alpha")
        waitForTag("result-app:$ALPHA")
        compose.onNodeWithTag("result-app:$ALPHA").performTouchInput { swipe(Offset(100f, centerY), Offset(600f, centerY)) }
        waitForTag("popup-scrim")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Bravo").fetchSemanticsNodes().isNotEmpty() }
    }

    private val alphaWithPopup = LauncherData(
        favorites = listOf(Entry("fav-alpha", Target.App(ALPHA), popup = Popup(listOf(PopupRow.Item(Entry("pkg.bravo/.Main", Target.App("pkg.bravo/.Main"))))))),
    )

    @Test
    fun launching_from_a_menu_over_search_ends_the_search() {
        val deps = start(alphaWithPopup)
        searchMenuWithOneItem(deps)
        compose.onNodeWithText("Bravo").performClick()
        compose.waitUntil(5_000) { deps.launches.isNotEmpty() }
        compose.waitUntil(5_000) { deps.session.screen.value == Screen.Home }
        assertEquals(listOf("pkg.bravo/.Main"), deps.launches.toList())
    }

    @Test
    fun a_launch_that_fails_from_a_menu_over_search_keeps_the_search_open() {
        val deps = start(alphaWithPopup)
        deps.launchOutcome = me.jhot.swiftcurrent.search.LaunchOutcome.Failed("could not start it")
        searchMenuWithOneItem(deps)
        compose.onNodeWithText("Bravo").performClick()
        waitForTag("shell-message")
        compose.onNodeWithText("could not start it").assertIsDisplayed()
        // Still in Search, with the query intact: a failed launch should not throw the search away.
        assertEquals(Screen.Search, deps.session.screen.value)
        compose.onNodeWithTag("search-field").assertTextEquals("alpha")
    }

    private fun longPressRow(component: String) {
        compose.onNodeWithTag("row-$component").performTouchInput { longClick() }
    }

    @Test
    fun long_pressing_an_app_in_the_list_opens_its_actions_with_a_tick() {
        val deps = start()
        openList(deps)
        longPressRow("pkg.alpha 1/.Main")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Add to favorites").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Hide from the app list").assertIsDisplayed()
        compose.onNodeWithText("App info").assertIsDisplayed()
        // Editing the whole list is not the point from here; any app can be renamed, favorite or not.
        assertFalse(exists("sheet-edit-favorites"))
        compose.onAllNodesWithText("Edit favorites").assertCountEquals(0)
        compose.onNodeWithText("Rename").assertIsDisplayed()
        assertEquals(listOf("gesture:20"), deps.recordedHaptics.ticks.toList())
        // A plain tap on a row still launches it; nothing opened by accident.
    }

    @Test
    fun adding_an_app_from_the_list_makes_it_a_favorite() {
        val deps = start()
        openList(deps)
        longPressRow("pkg.alpha 1/.Main")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Add to favorites").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add to favorites").performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.favorites.any { (it.target as? Target.App)?.component == "pkg.alpha 1/.Main" } }
        assertEquals(favorites + Entry("pkg.alpha 1/.Main", Target.App("pkg.alpha 1/.Main")), deps.fakeStore.data.favorites)
        // The sheet closed and the list is still open.
        waitForNoTag("sheet-favorite")
        assertEquals(Screen.AppList, deps.session.screen.value)
    }

    @Test
    fun an_app_that_is_a_favorite_offers_remove_and_rename_in_the_list() {
        val deps = start()
        openList(deps)
        longPressRow(ALPHA)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Remove from favorites").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Rename").assertIsDisplayed()
        compose.onNodeWithText("Remove from favorites").performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.favorites.none { it.id == "fav-alpha" } }
        assertEquals(favorites.drop(1), deps.fakeStore.data.favorites)
    }

    @Test
    fun starring_an_app_from_the_list_is_never_refused_for_capacity() {
        // More favorites than Home can draw: conditions can make the visible set smaller, so the list does not gate adding.
        val many = apps.filter { it.label.startsWith("Mike ") || it.label.startsWith("Sierra ") }.take(24)
            .map { Entry(it.component, Target.App(it.component)) }
        val deps = start(LauncherData(favorites = many))
        openList(deps)
        longPressRow("pkg.alpha 1/.Main")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Add to favorites").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add to favorites").performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.favorites.size == many.size + 1 }
        assertEquals("pkg.alpha 1/.Main", deps.fakeStore.data.favorites.last().id)
    }

    @Test
    fun hiding_an_app_from_the_list_removes_its_row() {
        val deps = start()
        openList(deps)
        longPressRow("pkg.alpha 1/.Main")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Hide from the app list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Hide from the app list").performClick()
        compose.waitUntil(5_000) { "pkg.alpha 1/.Main" in deps.fakeStore.data.hiddenApps }
        waitForNoTag("row-pkg.alpha 1/.Main")
    }

    @Test
    fun the_editor_does_not_open_on_stale_data_when_the_store_cannot_be_read() {
        val deps = start()
        // Home loaded fine; the disk then fails just as the editor opens. Building the editor from the
        // data Home already had, then writing, would put the older list back over newer edits.
        deps.fakeStore.loadFails = true
        compose.onNodeWithTag("favorite-fav-alpha").performTouchInput { longClick() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Edit favorites").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Edit favorites").performClick()
        waitForTag("shell-message")
        // No editor, no edit window: back on Home with a notice, and nothing was written.
        assertFalse(exists("editor-header-Selected"))
        assertEquals(Screen.Home, deps.session.screen.value)
        assertEquals(favorites, deps.fakeStore.data.favorites)
    }

    @Test
    fun edit_popup_creates_the_popup_and_saves_rows_back_into_the_favorite() {
        val deps = start()
        compose.onNodeWithTag("favorite-fav-alpha").performTouchInput { swipe(Offset(100f, centerY), Offset(700f, centerY)) }
        waitForTag("popup-edit")
        compose.onNodeWithTag("popup-edit").performClick()
        waitForTag("editor-header-Selected")
        compose.onNodeWithText(res(R.string.editor_title_popup_for, "Alpha")).assertIsDisplayed()
        // A default pop-up holds the app's own shortcuts as its one row.
        compose.onNodeWithTag("sel-own").assertIsDisplayed()
        compose.onNodeWithTag("toggle-sel-own").performClick()
        compose.waitUntil(5_000) {
            deps.fakeStore.data.favorites.first { it.id == "fav-alpha" }.popup == Popup(emptyList())
        }
        back()
        waitForNoTag("editor-header-Selected")
        assertEquals(Screen.Home, deps.session.screen.value)
        // Other favorites are untouched.
        assertEquals(favorites.drop(1), deps.fakeStore.data.favorites.drop(1))
    }

    @Test
    fun a_sheet_for_an_app_from_the_list_has_an_edit_button_that_saves_the_pop_up_for_that_app() {
        val deps = start()
        openList(deps)
        tapLetter('M')
        val component = "pkg.mike 1/.Main"
        waitForTag("row-$component")
        compose.onNodeWithTag("row-$component", useUnmergedTree = true).performTouchInput { swipe(Offset(100f, centerY), Offset(600f, centerY)) }
        waitForTag("popup-edit")
        compose.onNodeWithTag("popup-edit").performClick()
        waitForTag("editor-header-Selected")
        compose.onNodeWithText(res(R.string.editor_title_popup_for, "Mike 1")).assertIsDisplayed()
        compose.onNodeWithTag("toggle-sel-own").performClick()
        // Not a favorite, so the pop-up is kept for the app, and Home's favorites are untouched.
        compose.waitUntil(5_000) { deps.fakeStore.data.appPopups[component] == Popup(emptyList()) }
        assertEquals(favorites, deps.fakeStore.data.favorites)
        back()
        waitForNoTag("editor-header-Selected")
        // The stored pop-up is what the app's sheet shows next time.
        compose.runOnUiThread { deps.session.navigate(Screen.AppList) }
        waitForTag("applist")
        waitForTag("scrubber")
        compose.waitForIdle()
        // A second tap on the strip soon after the first counts as a double tap (by the fake clock), so let the window pass.
        deps.nowMillis += 5_000
        tapLetter('M')
        compose.waitForIdle()
        waitForTag("row-$component")
        compose.onNodeWithTag("row-$component", useUnmergedTree = true).performTouchInput { swipe(Offset(100f, centerY), Offset(600f, centerY)) }
        waitForTag("popup-empty")
    }

    @Test
    fun a_sheet_opened_over_another_sheet_has_its_own_edit_button() {
        val deps = start(alphaWithPopup)
        searchMenuWithOneItem(deps)
        compose.onNodeWithTag("popup-line-0", useUnmergedTree = true).performTouchInput { swipe(Offset(100f, centerY), Offset(600f, centerY)) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("popup-sheet", useUnmergedTree = true).fetchSemanticsNodes().size == 2 }
        assertEquals(2, compose.onAllNodesWithTag("popup-edit", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    private fun swipePopupOf(tag: String) {
        compose.onNodeWithTag(tag).performTouchInput { swipe(Offset(100f, centerY), Offset(700f, centerY)) }
        waitForTag("popup-edit")
        compose.onNodeWithTag("popup-edit").performClick()
        waitForTag("editor-header-Selected")
    }

    @Test
    fun deselecting_a_folder_keeps_it_in_the_library_with_its_contents() {
        val deps = start()
        openFavoritesEditor()
        compose.onNodeWithTag("toggle-sel-fav-folder").performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.favorites.none { it.id == "fav-folder" } }
        val saved = deps.fakeStore.data.folders.single { it.id == "fav-folder" }
        assertEquals(Popup(listOf(PopupRow.Item(folderMember))), saved.popup)
        // And it can be ticked again with its pop-up intact.
        tapLetter(EditorSections.FOLDERS)
        compose.onNodeWithTag("pick-Folders-fav-folder").performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.favorites.any { it.id == "fav-folder" } }
        assertEquals(Popup(listOf(PopupRow.Item(folderMember))), deps.fakeStore.data.favorites.single { it.id == "fav-folder" }.popup)
    }

    @Test
    fun created_folder_persists_even_when_it_is_unticked() {
        val deps = start()
        openFavoritesEditor()
        tapLetter(EditorSections.ADVANCED)
        compose.onNodeWithTag("action-CreateFolder").performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.folders.any { it.target == Target.Folder && it.id != "fav-folder" } }
        val created = deps.fakeStore.data.folders.first { it.id != "fav-folder" }
        assertEquals(Popup(emptyList()), created.popup)
        // A new folder asks for its name first; keeping the default is what this test is about.
        waitForTag("rename-field")
        back()
        waitForNoTag("rename-field")
        tapLetter(EditorSections.SELECTED)
        compose.onNodeWithTag("toggle-sel-${created.id}").performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.favorites.none { it.id == created.id } }
        assertTrue(deps.fakeStore.data.folders.any { it.id == created.id })
    }

    private val work = Condition.MeldMode("work")

    @Test
    fun favorite_with_unmet_condition_is_not_on_home_and_appears_when_it_holds() {
        val data = LauncherData(favorites = listOf(Entry("fav-alpha", Target.App(ALPHA), conditions = listOf(work))))
        val deps = FakeDependencies(data, apps)
        deps.context.value = ContextState(activeModes = emptySet(), knownModes = setOf("work"))
        compose.setContent { LauncherShell(deps) }
        compose.waitUntil(5_000) { exists("clock") }
        assertFalse(exists("favorite-fav-alpha"))
        // Stored favorites exist, so the hint must not take their place.
        assertFalse(exists("empty-favorites-hint"))
        deps.context.value = ContextState(activeModes = setOf("work"), knownModes = setOf("work"))
        waitForTag("favorite-fav-alpha")
    }

    @Test
    fun editor_dims_a_favorite_whose_condition_is_unmet_and_undims_when_it_holds() {
        val data = LauncherData(
            favorites = listOf(
                Entry("fav-alpha", Target.App(ALPHA)),
                Entry("fav-charlie", Target.App("pkg.charlie/.Main"), conditions = listOf(work)),
            ),
        )
        val deps = FakeDependencies(data, apps)
        deps.context.value = ContextState(activeModes = emptySet(), knownModes = setOf("work"))
        compose.setContent { LauncherShell(deps) }
        compose.waitUntil(5_000) { exists("clock") }
        openFavoritesEditor()
        val hidden = res(R.string.editor_hidden_by_conditions)
        compose.onNodeWithTag("sel-fav-charlie").assert(
            androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, hidden),
        )
        compose.onNodeWithTag("sel-fav-alpha").assert(
            androidx.compose.ui.test.SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsProperties.StateDescription),
        )
        deps.context.value = ContextState(activeModes = setOf("work"), knownModes = setOf("work"))
        compose.waitUntil(5_000) {
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription !in
                compose.onNodeWithTag("sel-fav-charlie").fetchSemanticsNode().config
        }
    }

    @Test
    fun folder_and_shortcut_favorites_honour_conditions() {
        val on = listOf(work)
        val data = LauncherData(
            favorites = favorites.map { if (it.id == "fav-bed" || it.id == "fav-folder") it.copy(conditions = on) else it },
        )
        val deps = FakeDependencies(data, apps)
        deps.context.value = ContextState(activeModes = emptySet(), knownModes = setOf("work"))
        compose.setContent { LauncherShell(deps) }
        compose.waitUntil(5_000) { exists("clock") }
        assertTrue(exists("favorite-fav-alpha"))
        assertFalse(exists("favorite-fav-bed"))
        assertFalse(exists("favorite-fav-folder"))
        deps.context.value = ContextState(activeModes = setOf("work"), knownModes = setOf("work"))
        waitForTag("favorite-fav-bed")
        waitForTag("favorite-fav-folder")
    }

    @Test
    fun settings_spacing_preview_does_not_show_the_hint_when_all_favorites_are_hidden() {
        val data = LauncherData(favorites = listOf(Entry("fav-alpha", Target.App(ALPHA), conditions = listOf(work))))
        val deps = FakeDependencies(data, apps)
        deps.context.value = ContextState(activeModes = emptySet(), knownModes = setOf("work"))
        compose.setContent { LauncherShell(deps) }
        compose.waitUntil(5_000) { exists("clock") }
        compose.runOnUiThread { deps.session.navigate(Screen.Settings) }
        waitForTag("settings-root")
        compose.onNodeWithText("Home").performClick()
        waitForTag("row-spacing")
        compose.onNodeWithTag("row-spacing").performClick()
        waitForTag("settings-spacing")
        assertFalse(exists("empty-favorites-hint"))
    }

    @Test
    fun a_folder_favorite_keeps_its_conditions_across_an_editor_save_and_a_popup_edit() {
        val conditional = favorites.map { if (it.id == "fav-folder") it.copy(conditions = listOf(work)) else it }
        val deps = FakeDependencies(LauncherData(favorites = conditional), apps)
        deps.context.value = ContextState(activeModes = setOf("work"), knownModes = setOf("work"))
        compose.setContent { LauncherShell(deps) }
        compose.waitUntil(5_000) { exists("clock") }
        // A popup edit writes the folder back to both the library and the favorite.
        swipePopupOf("favorite-fav-folder")
        compose.onNodeWithTag("toggle-sel-fav-bravo").performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.folders.any { it.id == "fav-folder" } }
        assertEquals(listOf(work), deps.fakeStore.data.favorites.single { it.id == "fav-folder" }.conditions)
        assertEquals(emptyList<Condition>(), deps.fakeStore.data.folders.single { it.id == "fav-folder" }.conditions)
        back()
        waitForNoTag("editor-header-Selected")
        // And the favorites editor, saving the whole list, keeps them too.
        openFavoritesEditor()
        compose.onNodeWithTag("toggle-sel-fav-alpha").performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.favorites.none { it.id == "fav-alpha" } }
        assertEquals(listOf(work), deps.fakeStore.data.favorites.single { it.id == "fav-folder" }.conditions)
        assertEquals(emptyList<Condition>(), deps.fakeStore.data.folders.single { it.id == "fav-folder" }.conditions)
    }

    @Test
    fun editing_a_folder_popup_updates_the_library_entry() {
        val deps = start()
        swipePopupOf("favorite-fav-folder")
        compose.onNodeWithTag("toggle-sel-fav-bravo").performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.folders.any { it.id == "fav-folder" } }
        assertEquals(Popup(emptyList()), deps.fakeStore.data.folders.single { it.id == "fav-folder" }.popup)
        // The favorite's mirror copy changes with it, so Home's pop-up shows the same rows.
        assertEquals(Popup(emptyList()), deps.fakeStore.data.favorites.single { it.id == "fav-folder" }.popup)
    }

    @Test
    fun search_uses_the_five_real_source_types() {
        val deps = FakeDependencies(LauncherData(), apps)
        val sources: List<SearchSource> = searchSources(deps, { LauncherData() }, { apps }, me.jhot.swiftcurrent.search.sources.EnglishSearchStrings, Dispatchers.IO, me.jhot.swiftcurrent.agenda.AgendaFormatter(me.jhot.swiftcurrent.agenda.EnglishAgendaStrings))
        assertEquals(
            listOf(AppSource::class, ShortcutSource::class, UserShortcutSource::class, ContactSource::class, me.jhot.swiftcurrent.search.sources.EventSource::class),
            sources.map { it::class },
        )

        compose.setContent { LauncherShell(deps) }
        compose.waitUntil(5_000) { exists("clock") }
        compose.runOnUiThread { deps.session.navigate(Screen.Search) }
        waitForTag("search-field")
        compose.onNodeWithTag("search-field").performTextInput("bed")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Tasker: Bedtime").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun the_contacts_prompt_opens_this_apps_details_screen() {
        val deps = FakeDependencies(LauncherData(), apps)
        deps.contactsGranted = false
        compose.setContent { LauncherShell(deps) }
        compose.waitUntil(5_000) { exists("clock") }
        compose.runOnUiThread { deps.session.navigate(Screen.Search) }
        waitForTag("contacts-prompt")
        compose.onNodeWithText("Grant").performClick()
        compose.waitUntil(5_000) { deps.started.isNotEmpty() }
        val intent = deps.started.single()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${compose.activity.packageName}", intent.dataString)
    }

    // Decision: the cog is only reachable from the list, so Back from Settings returns to the list
    // (with its scroll kept), and Back from the list returns to Home.
    @Test
    fun settings_opens_from_the_cog_and_back_returns_to_the_list_or_home() {
        val deps = start()
        openList(deps)
        tapLetter('M')
        compose.waitUntil(5_000) { !exists("header-A") }
        val at = touchPointOf(ScrubberDefaults.COG)
        compose.onNodeWithTag("scrubber").performTouchInput { down(at); up() }
        waitForTag("settings-root")
        compose.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty().let { assertTrue(it) }
        assertEquals(Screen.Settings, deps.session.screen.value)
        assertTrue(!exists("applist"))

        back()
        waitForTag("applist")
        assertEquals(Screen.AppList, deps.session.screen.value)
        // Same visit to the list: the scroll position survived the trip to Settings.
        assertTrue(!exists("header-A"))

        back()
        waitForNoTag("applist")
        assertEquals(Screen.Home, deps.session.screen.value)
    }

    @Test
    fun a_throwing_store_load_still_reaches_ready_with_empty_favorites() {
        val deps = FakeDependencies(LauncherData(favorites = favorites), apps)
        deps.fakeStore.loadFails = true
        compose.setContent { LauncherShell(deps) }
        compose.waitUntil(5_000) { exists("clock") }
        compose.onAllNodesWithTag("favorite-fav-alpha").assertCountEquals(0)
    }

    @Test
    fun a_throwing_catalog_still_reaches_ready() {
        val deps = FakeDependencies(LauncherData(favorites = favorites), apps)
        deps.catalog.appsFails = true
        compose.setContent { LauncherShell(deps) }
        compose.waitUntil(5_000) { exists("clock") }
        // No catalog means every app favorite counts as uninstalled, but the screen is up.
        compose.onNodeWithTag("clock").assertIsDisplayed()
    }

    @Test
    fun a_throwing_catalog_change_flow_does_not_crash_the_shell() {
        val deps = FakeDependencies(LauncherData(favorites = favorites), apps)
        deps.catalog.changesFlow = kotlinx.coroutines.flow.flow { throw IllegalStateException("callback died") }
        compose.setContent { LauncherShell(deps) }
        compose.waitUntil(5_000) { exists("clock") }
        compose.onNodeWithTag("favorite-fav-alpha").assertIsDisplayed()
    }

    // ---- user-made shortcuts (Task 14) ----

    private val bob = UserShortcut(
        "u1", "pkg.alpha", "Friend Bob",
        Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:1,2")).setPackage("pkg.alpha").toUri(Intent.URI_INTENT_SCHEME),
    )

    // Its app is not installed, so only the package manager can name it.
    private val gone = UserShortcut("u-gone", "net.example.gone", "Gone", "intent:#Intent;end")

    private fun searchFor(deps: FakeDependencies, text: String) {
        compose.runOnUiThread { deps.session.navigate(Screen.Search) }
        waitForTag("search-field")
        compose.onNodeWithTag("search-field").performTextInput(text)
    }

    @Test
    fun typing_does_not_call_the_catalog_again() {
        val deps = start()
        compose.waitForIdle()
        val before = deps.catalog.appsCalls.get()
        searchFor(deps, "alp")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Alpha").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("search-field").performTextInput("h")
        compose.waitForIdle()
        compose.onNodeWithTag("search-field").performTextInput("a")
        compose.waitForIdle()
        assertEquals("search reads the loaded list, not the catalog", before, deps.catalog.appsCalls.get())
    }

    // Home reads the shortcut labels itself every time it is shown, so the search tests count from that read.
    private fun awaitShortcutReads(deps: FakeDependencies, n: Int) {
        compose.waitUntil(5_000) { deps.shortcuts.entriesCalls.get() >= n }
        compose.waitForIdle()
    }

    @Test
    fun typing_does_not_re_read_shortcuts_after_the_first_load() {
        val deps = start()
        awaitShortcutReads(deps, 1)
        compose.runOnUiThread { deps.session.navigate(Screen.Search) }
        waitForTag("search-field")
        // The open-time refresh is the second read; the first keystroke must wait for it, not read again.
        awaitShortcutReads(deps, 2)
        compose.onNodeWithTag("search-field").performTextInput("b")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Tasker: Bedtime").fetchSemanticsNodes().isNotEmpty() }
        val afterFirst = deps.shortcuts.entriesCalls.get()
        assertEquals("home's label read plus the open-time refresh, nothing for the keystroke", 2, afterFirst)
        compose.onNodeWithTag("search-field").performTextInput("e")
        compose.waitForIdle()
        compose.onNodeWithTag("search-field").performTextInput("d")
        compose.waitForIdle()
        assertEquals(afterFirst, deps.shortcuts.entriesCalls.get())
    }

    @Test
    fun opening_search_refreshes_the_cache() {
        val deps = start()
        awaitShortcutReads(deps, 1)
        val home = deps.shortcuts.entriesCalls.get()
        // No keystroke at all: only the open-time refresh can have read the provider.
        compose.runOnUiThread { deps.session.navigate(Screen.Search) }
        waitForTag("search-field")
        awaitShortcutReads(deps, home + 1)
        assertEquals(home + 1, deps.shortcuts.entriesCalls.get())
        compose.runOnUiThread { deps.session.navigate(Screen.Home) }
        waitForNoTag("search-field")
        awaitShortcutReads(deps, home + 2)
        compose.runOnUiThread { deps.session.navigate(Screen.Search) }
        waitForTag("search-field")
        awaitShortcutReads(deps, home + 3)
    }

    @Test
    fun typing_a_user_shortcuts_name_finds_it_and_a_tap_runs_it() {
        val deps = start(LauncherData(userShortcuts = listOf(bob)))
        searchFor(deps, "bob")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Alpha: Friend Bob").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Alpha: Friend Bob").performClick()
        compose.waitUntil(5_000) { deps.userStarts.isNotEmpty() }
        assertEquals("geo:1,2", deps.userStarts.single().dataString)
    }

    private fun dentist(calendar: Long = 1) = me.jhot.swiftcurrent.agenda.EventOccurrence(
        AgendaEvent("Bed dentist", java.time.Instant.ofEpochMilli(deps0Now + 86_400_000), java.time.Instant.ofEpochMilli(deps0Now + 90_000_000), false, "Main St", 0, calendar, 7),
        "Personal", 7, false, declined = false, cancelled = false,
    )

    private val deps0Now = 1_700_000_000_000L

    @Test
    fun typing_an_events_title_shows_it_and_a_tap_opens_it_in_the_calendar() {
        val deps = start()
        deps.fakeCalendar.occurrences = listOf(dentist())
        searchFor(deps, "bed dent")
        waitForTag("result-event:7")
        compose.onNodeWithTag("result-event:7").performClick()
        compose.waitUntil(5_000) { deps.started.isNotEmpty() }
        val opened = deps.started.single()
        assertEquals(android.content.Intent.ACTION_VIEW, opened.action)
        assertEquals("content://com.android.calendar/events/7", opened.dataString)
    }

    @Test
    fun an_events_location_opens_a_maps_query() {
        val deps = start()
        deps.fakeCalendar.occurrences = listOf(dentist())
        searchFor(deps, "bed dent")
        waitForTag("action-event:7-Main St")
        compose.onNodeWithTag("action-event:7-Main St", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { deps.started.isNotEmpty() }
        assertEquals("geo:0,0?q=Main%20St", deps.started.single().dataString)
    }

    @Test
    fun no_events_are_searched_without_calendar_permission() {
        val deps = start().also { it.calendarGranted = false }
        deps.fakeCalendar.occurrences = listOf(dentist())
        searchFor(deps, "bed")
        // The shortcut that matches too proves the answer arrived.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Tasker: Bedtime").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        assertEquals(false, exists("result-event:7"))
    }

    @Test
    fun a_calendar_left_out_of_search_is_not_searched() {
        val deps = start(LauncherData(settings = AppSettings(searchCalendars = setOf(2L))))
        deps.fakeCalendar.occurrences = listOf(dentist(calendar = 1))
        searchFor(deps, "bed")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Tasker: Bedtime").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        assertEquals(false, exists("result-event:7"))
    }

    @Test
    fun a_switched_off_user_shortcut_stays_out_of_the_results_for_its_own_name() {
        val off = LauncherData(userShortcuts = listOf(bob), settings = me.jhot.swiftcurrent.core.Settings(disabledShortcuts = setOf("user/u1")))
        val deps = start(off)
        searchFor(deps, "alpha")
        // The app itself is found; its switched-off shortcut is not.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Alpha").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onAllNodesWithText("Alpha: Friend Bob").assertCountEquals(0)
    }

    @Test
    fun a_user_shortcut_favorite_shows_its_name_and_launches_through_the_user_shortcuts() {
        val deps = start(
            LauncherData(favorites = listOf(Entry("fav-bob", Target.Shortcut("user/u1"))), userShortcuts = listOf(bob)),
        )
        compose.onNodeWithText("Friend Bob").assertIsDisplayed()
        compose.onNodeWithTag("favorite-fav-bob").performClick()
        compose.waitUntil(5_000) { deps.userStarts.isNotEmpty() }
        assertTrue(deps.shortcuts.launched.isEmpty())
    }

    @Test
    fun a_user_shortcut_whose_app_cannot_open_it_shows_a_message() {
        val deps = start(
            LauncherData(favorites = listOf(Entry("fav-bob", Target.Shortcut("user/u1"))), userShortcuts = listOf(bob)),
        )
        deps.userStartFails = android.content.ActivityNotFoundException("gone")
        compose.onNodeWithTag("favorite-fav-bob").performClick()
        waitForTag("shell-message")
        compose.onNodeWithText("shortcut gone").assertIsDisplayed()
    }

    // The creator activity cannot run under Robolectric, so a fake registry answers the launch with a result.
    private class FakeRegistryOwner(val answer: (Intent) -> Intent?, val context: () -> android.content.Context) :
        androidx.activity.result.ActivityResultRegistryOwner {
        val launched = CopyOnWriteArrayList<Intent>()
        override val activityResultRegistry = object : androidx.activity.result.ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: androidx.activity.result.contract.ActivityResultContract<I, O>,
                input: I,
                options: androidx.core.app.ActivityOptionsCompat?,
            ) {
                val intent = contract.createIntent(context(), input)
                launched += intent
                dispatchResult(requestCode, android.app.Activity.RESULT_OK, answer(intent))
            }
        }
    }

    private val mapsCreator = Creator("pkg.alpha/pkg.alpha.Friend", "Friend's location", "Alpha")

    private fun startWithCreator(
        answer: (Intent) -> Intent?,
        data: LauncherData = LauncherData(favorites = favorites),
        creators: List<Creator> = listOf(mapsCreator),
    ): Pair<FakeDependencies, FakeRegistryOwner> {
        val deps = FakeDependencies(data, apps)
        deps.creators.list = creators
        val owner = FakeRegistryOwner(answer) { compose.activity }
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.activity.compose.LocalActivityResultRegistryOwner provides owner,
            ) { LauncherShell(deps) }
        }
        compose.waitUntil(5_000) { exists("clock") }
        return deps to owner
    }

    private fun openAddShortcutPicker(creator: Creator = mapsCreator) {
        openFavoritesEditor()
        val at = touchPointOf(EditorSections.ADVANCED)
        compose.onNodeWithTag("scrubber").performTouchInput { down(at); up() }
        waitForTag("action-AddShortcut")
        compose.onNodeWithTag("action-AddShortcut").performClick()
        waitForTag("creator-${creator.component}")
    }

    private val browserCreator = Creator.webLink("org.example.browser", "Example Browser", "Web link")

    private fun openWebLinkForm(): Pair<FakeDependencies, FakeRegistryOwner> {
        val started = startWithCreator({ null }, creators = listOf(browserCreator))
        openAddShortcutPicker(browserCreator)
        compose.onNodeWithTag("creator-${browserCreator.component}").performClick()
        waitForTag("web-link-url")
        return started
    }

    private fun typeWebLink(url: String, name: String = "") {
        compose.onNodeWithTag("web-link-url", useUnmergedTree = true).performTextInput(url)
        if (name.isNotEmpty()) compose.onNodeWithTag("web-link-name", useUnmergedTree = true).performTextInput(name)
        compose.onNodeWithTag("web-link-save", useUnmergedTree = true).performClick()
    }

    @Test
    fun a_web_link_is_found_in_search_by_its_address() {
        val link = me.jhot.swiftcurrent.shortcuts.WebLinks.parse("https://github.com/jhot21")!!
        val shortcut = UserShortcut(
            "w1", "pkg.alpha", "Code", me.jhot.swiftcurrent.shortcuts.WebLinks.intentUri(link, "pkg.alpha"),
        )
        val deps = start(LauncherData(favorites = favorites, userShortcuts = listOf(shortcut)))
        searchFor(deps, "github")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Alpha: Code").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun a_browser_gets_a_web_link_row_that_opens_the_form_and_launches_no_activity() {
        val (_, owner) = openWebLinkForm()
        assertTrue(owner.launched.isEmpty())
    }

    @Test
    fun a_saved_web_link_is_stored_for_its_browser_with_its_icon_and_offered_by_the_editor() {
        val (deps, _) = openWebLinkForm()
        typeWebLink("example.com", "Example")
        compose.waitUntil(5_000) { deps.fakeStore.data.userShortcuts.isNotEmpty() }
        val made = deps.fakeStore.data.userShortcuts.single()
        assertEquals("org.example.browser", made.appPackage)
        assertEquals("Example", made.label)
        assertTrue(byteArrayOf(7).contentEquals(made.iconBytes))
        waitForNoTag("web-link-url")

        val pick = "pick-Shortcuts-user/${made.id}"
        val at = touchPointOf(EditorSections.SHORTCUTS)
        compose.onNodeWithTag("scrubber").performTouchInput { down(at); up() }
        waitForTag(pick)
    }

    @Test
    fun the_same_web_link_twice_is_refused_with_the_duplicate_message() {
        val (deps, _) = openWebLinkForm()
        typeWebLink("example.com", "Example")
        compose.waitUntil(5_000) { deps.fakeStore.data.userShortcuts.size == 1 }
        waitForNoTag("web-link-url")
        compose.onNodeWithTag("action-AddShortcut").performClick()
        waitForTag("creator-${browserCreator.component}")
        compose.onNodeWithTag("creator-${browserCreator.component}").performClick()
        waitForTag("web-link-url")
        typeWebLink("example.com", "Example")
        waitForTag("shell-message")
        compose.onNodeWithText(res(R.string.shortcut_rejected_duplicate)).assertIsDisplayed()
        assertEquals(1, deps.fakeStore.data.userShortcuts.size)
    }

    @Test
    fun dismissing_the_web_link_form_stores_nothing() {
        val (deps, _) = openWebLinkForm()
        back()
        waitForNoTag("web-link-url")
        assertTrue(deps.fakeStore.data.userShortcuts.isEmpty())
    }

    private fun madeResult(name: String? = "Friend Dave") = Intent().apply {
        putExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:3,4")).setPackage("pkg.alpha"))
        if (name != null) putExtra(Intent.EXTRA_SHORTCUT_NAME, name)
    }

    @Test
    fun the_picker_makes_a_shortcut_that_the_editor_offers_and_search_finds() {
        val (deps, owner) = startWithCreator({ madeResult() })
        openAddShortcutPicker()
        // Grouped by app, with the creator's own label.
        compose.onNodeWithText("Friend's location").assertIsDisplayed()
        compose.onNodeWithTag("creator-${mapsCreator.component}").performClick()

        compose.waitUntil(5_000) { deps.fakeStore.data.userShortcuts.isNotEmpty() }
        val made = deps.fakeStore.data.userShortcuts.single()
        assertEquals("Friend Dave", made.label)
        assertEquals("pkg.alpha", made.appPackage)
        assertEquals(Intent.ACTION_CREATE_SHORTCUT, owner.launched.single().action)
        assertEquals(mapsCreator.component, owner.launched.single().component?.flattenToString())

        // It appears among the editor's shortcuts and can be picked as a favorite.
        val pick = "pick-Shortcuts-user/${made.id}"
        val at = touchPointOf(EditorSections.SHORTCUTS)
        compose.onNodeWithTag("scrubber").performTouchInput { down(at); up() }
        waitForTag(pick)
        compose.onNodeWithTag(pick).performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.favorites.any { it.target == Target.Shortcut("user/${made.id}") } }

        back()
        waitForNoTag("editor-header-Selected")
        searchFor(deps, "dave")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Alpha: Friend Dave").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun a_duplicate_result_is_refused_with_a_message_and_stored_once() {
        val (deps, _) = startWithCreator({ madeResult() })
        openAddShortcutPicker()
        compose.onNodeWithTag("creator-${mapsCreator.component}").performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.userShortcuts.size == 1 }
        compose.onNodeWithTag("action-AddShortcut").performClick()
        waitForTag("creator-${mapsCreator.component}")
        compose.onNodeWithTag("creator-${mapsCreator.component}").performClick()
        waitForTag("shell-message")
        compose.onNodeWithText(res(R.string.shortcut_rejected_duplicate)).assertIsDisplayed()
        assertEquals(1, deps.fakeStore.data.userShortcuts.size)
    }

    @Test
    fun a_result_without_a_name_is_refused_with_a_message_and_nothing_is_stored() {
        val (deps, _) = startWithCreator({ madeResult(name = null) })
        openAddShortcutPicker()
        compose.onNodeWithTag("creator-${mapsCreator.component}").performClick()
        waitForTag("shell-message")
        compose.onNodeWithText(res(R.string.shortcut_rejected_no_name)).assertIsDisplayed()
        assertTrue(deps.fakeStore.data.userShortcuts.isEmpty())
    }

    @Test
    fun the_per_app_shortcuts_page_renders_and_persists_a_toggle() {
        val deps = FakeDependencies(LauncherData(userShortcuts = listOf(bob)), apps)
        deps.creators.list = listOf(mapsCreator)
        val model = ShellData(deps, Dispatchers.IO)
        compose.setContent {
            LaunchedEffect(model) { model.run() }
            val ready by model.ready.collectAsState()
            if (ready) AppShortcutsHost(deps, model, Dispatchers.IO, onBack = {}, onMessage = {})
        }
        // Tasker's three system shortcuts and the made one: the list counts both, plus "make your own".
        waitForTag("app-shortcuts-list")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Tasker").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Tasker").performClick()
        compose.onNodeWithTag("shortcut-toggle-$TASKER/bedtime").assertIsOn().performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.settings.disabledShortcuts == setOf("$TASKER/bedtime") }
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("shortcut-toggle-$TASKER/bedtime").assertIsOff() }.isSuccess }
    }

    @Test
    fun the_per_app_shortcuts_page_makes_a_web_link_for_a_browser_too() {
        val deps = FakeDependencies(LauncherData(), apps)
        deps.creators.list = listOf(browserCreator)
        val model = ShellData(deps, Dispatchers.IO)
        compose.setContent {
            LaunchedEffect(model) { model.run() }
            val ready by model.ready.collectAsState()
            if (ready) AppShortcutsHost(deps, model, Dispatchers.IO, onBack = {}, onMessage = {})
        }
        waitForTag("app-shortcuts-list")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Example Browser").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Example Browser").performClick()
        compose.onNodeWithText(res(R.string.shortcut_web_link)).performClick()
        waitForTag("web-link-url")
        typeWebLink("example.com", "Example")
        compose.waitUntil(5_000) { deps.fakeStore.data.userShortcuts.isNotEmpty() }
        assertEquals("org.example.browser", deps.fakeStore.data.userShortcuts.single().appPackage)
        waitForNoTag("web-link-url")
    }

    @Test
    fun the_per_app_shortcuts_page_reads_a_gone_apps_name_off_the_main_thread() {
        val deps = FakeDependencies(LauncherData(userShortcuts = listOf(gone)), apps)
        val model = ShellData(deps, Dispatchers.IO)
        compose.setContent {
            LaunchedEffect(model) { model.run() }
            val ready by model.ready.collectAsState()
            if (ready) AppShortcutsHost(deps, model, Dispatchers.IO, onBack = {}, onMessage = {})
        }
        waitForTag("app-shortcuts-list")
        // With no label from the app list, its shortcuts or a creator, the package manager names it.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("net.example.gone").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("package label read during composition", 0, deps.mainThreadLabels)
    }

    @Test
    fun the_editor_reads_a_gone_shortcuts_app_name_off_the_main_thread() {
        val deps = start(LauncherData(favorites = favorites, userShortcuts = listOf(gone)))
        openFavoritesEditor()
        compose.waitForIdle()
        assertEquals("package label read during composition", 0, deps.mainThreadLabels)
    }

    // The Settings page swallows a failed access read; this page crashed the composition on one.
    @Test
    fun the_per_app_shortcuts_page_survives_a_failed_access_read() {
        val deps = FakeDependencies(LauncherData(userShortcuts = listOf(bob)), apps)
        (deps.shortcuts as FakeShortcuts).accessFails = IllegalStateException("launcher service gone")
        val model = ShellData(deps, Dispatchers.IO)
        compose.setContent {
            LaunchedEffect(model) { model.run() }
            val ready by model.ready.collectAsState()
            if (ready) AppShortcutsHost(deps, model, Dispatchers.IO, onBack = {}, onMessage = {})
        }
        waitForTag("app-shortcuts-list")
        assertTrue("access was read", (deps.shortcuts as FakeShortcuts).accessCalls.get() > 0)
    }

    @Test
    fun the_per_app_shortcuts_page_deletes_a_made_shortcut() {
        val deps = FakeDependencies(LauncherData(userShortcuts = listOf(bob)), apps)
        val model = ShellData(deps, Dispatchers.IO)
        compose.setContent {
            LaunchedEffect(model) { model.run() }
            val ready by model.ready.collectAsState()
            if (ready) AppShortcutsHost(deps, model, Dispatchers.IO, onBack = {}, onMessage = {})
        }
        waitForTag("app-shortcuts-list")
        compose.onNodeWithText("Alpha").performClick()
        compose.onNodeWithTag("shortcut-delete-u1").performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.userShortcuts.isEmpty() }
    }

    @Test
    fun the_settings_page_opens_the_shortcuts_page_and_back_returns() {
        val deps = start()
        compose.runOnUiThread { deps.session.navigate(Screen.Settings) }
        waitForTag("settings-root")
        compose.onNodeWithText(res(R.string.settings_search)).performClick()
        waitForTag("settings-search")
        compose.onNodeWithText(res(R.string.settings_search_shortcuts)).performClick()
        waitForTag("app-shortcuts-list")
        back()
        waitForTag("settings-search")
        back()
        waitForTag("settings-root")
    }

    // ---- shortcut favorites and their labels (Task 14 fix round 1) ----

    @Test
    fun a_system_shortcut_favorite_shows_its_label_on_home() {
        start(LauncherData(favorites = listOf(Entry("fav-work", Target.Shortcut("$TASKER/work")))))
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Work").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("$TASKER/work").assertCountEquals(0)
    }

    @Test
    fun a_user_shortcut_favorite_shows_its_label_on_home() {
        start(LauncherData(favorites = listOf(Entry("fav-bob", Target.Shortcut("user/u1"))), userShortcuts = listOf(bob)))
        compose.onNodeWithText("Friend Bob").assertIsDisplayed()
    }

    @Test
    fun a_shortcut_favorite_with_no_known_label_shows_a_neutral_label_not_the_raw_id() {
        start(LauncherData(favorites = listOf(Entry("fav-x", Target.Shortcut("pkg.none/zzz")))))
        compose.onNodeWithText(res(R.string.label_unknown_shortcut)).assertIsDisplayed()
        compose.onAllNodesWithText("pkg.none/zzz").assertCountEquals(0)
    }

    @Test
    fun the_editor_persists_the_candidate_label_as_customLabel_and_home_agrees() {
        val deps = start(LauncherData(favorites = favorites))
        openFavoritesEditor()
        val at = touchPointOf(EditorSections.SHORTCUTS)
        compose.onNodeWithTag("scrubber").performTouchInput { down(at); up() }
        val pick = "pick-Shortcuts-$TASKER/work"
        waitForTag(pick)
        compose.onNodeWithTag(pick).performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.favorites.any { it.target == Target.Shortcut("$TASKER/work") } }
        assertEquals("Work", deps.fakeStore.data.favorites.single { it.target == Target.Shortcut("$TASKER/work") }.customLabel)
        back()
        waitForNoTag("editor-header-Selected")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Work").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun deleting_a_user_shortcut_removes_its_favorite_from_home() {
        val deps = start(LauncherData(favorites = listOf(Entry("fav-bob", Target.Shortcut("user/u1"))), userShortcuts = listOf(bob)))
        compose.onNodeWithTag("favorite-fav-bob").assertIsDisplayed()
        compose.runOnUiThread { deps.session.navigate(Screen.Settings) }
        waitForTag("settings-root")
        compose.onNodeWithText(res(R.string.settings_search)).performClick()
        waitForTag("settings-search")
        compose.onNodeWithText(res(R.string.settings_search_shortcuts)).performClick()
        waitForTag("app-shortcuts-list")
        compose.onNodeWithText("Alpha").performClick()
        compose.onNodeWithTag("shortcut-delete-u1").performClick()
        compose.waitUntil(5_000) { deps.fakeStore.data.userShortcuts.isEmpty() }
        assertTrue(deps.fakeStore.data.favorites.isEmpty())
        compose.runOnUiThread { deps.session.navigate(Screen.Home) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("favorite-fav-bob").fetchSemanticsNodes().isEmpty() }
    }
}
