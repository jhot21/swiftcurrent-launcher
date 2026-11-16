package me.jhot.swiftcurrent.ui.agenda

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.jhot.swiftcurrent.agenda.AgendaDay
import me.jhot.swiftcurrent.agenda.AgendaEvent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class AgendaSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.of("America/Denver")
    private fun at(d: Int, h: Int, m: Int = 0) = ZonedDateTime.of(2026, 11, d, h, m, 0, 0, zone).toInstant()
    private val now = at(8, 22, 23)

    private val standup = AgendaEvent("Standup", at(9, 11, 30), at(9, 12), false, null, 0x2E7D32, 1, eventId = 11)
    private val sync = AgendaEvent("Team sync", at(9, 18), at(9, 19), false, "Room 4", 0xD50000, 1, eventId = 12)
    private val dentist = AgendaEvent("Dentist", at(8, 0), at(9, 0), true, null, 0x9C27B0, 2, eventId = 13)

    private val days = listOf(
        AgendaDay(LocalDate.of(2026, 11, 8), "today", listOf(dentist)),
        AgendaDay(LocalDate.of(2026, 11, 9), "tomorrow", listOf(standup, sync)),
        AgendaDay(LocalDate.of(2026, 11, 11), null, listOf(sync.copy(title = "Dentist", location = null, eventId = 14))),
    )

    private fun show(
        days: List<AgendaDay> = this.days,
        onNewEvent: (LocalDate) -> Unit = {},
        onEvent: (AgendaEvent) -> Unit = {},
        onDismiss: () -> Unit = {},
    ) = compose.setContent { AgendaSheet(days, onNewEvent, onDismiss, now, zone, onEvent) }

    @Test
    fun the_sheet_shows_days_events_ranges_and_how_soon_they_start() {
        show()
        compose.onNodeWithText("Your agenda").assertIsDisplayed()
        compose.onNodeWithText("today").assertIsDisplayed()
        compose.onNodeWithText("tomorrow").assertIsDisplayed()
        compose.onNodeWithText("Team sync").assertIsDisplayed()
        compose.onNodeWithText("11:30 AM–12:00 PM · in 13h").assertIsDisplayed()
        compose.onAllNodesWithText("6:00 PM\u20137:00 PM \u00b7 in 19h").assertCountEquals(2)
        // The location shows under the time.
        compose.onNodeWithText("Room 4").assertIsDisplayed()
    }

    @Test
    fun an_all_day_event_has_a_title_and_no_time_line() {
        show(listOf(days[0]))
        compose.onNodeWithText("Dentist").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("in ", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun each_day_has_a_new_event_row_that_reports_its_date() {
        val picked = mutableListOf<LocalDate>()
        show(onNewEvent = { picked += it })
        assertEquals(3, compose.onAllNodesWithTag("agenda-new-event").fetchSemanticsNodes().size)
        compose.onAllNodesWithTag("agenda-new-event")[1].performClick()
        assertEquals(listOf(LocalDate.of(2026, 11, 9)), picked)
    }

    @Test
    fun tapping_an_event_reports_it() {
        val opened = mutableListOf<AgendaEvent>()
        show(onEvent = { opened += it })
        compose.onNodeWithText("Team sync").performClick()
        assertEquals(listOf(sync), opened)
    }

    @Test
    fun an_empty_agenda_says_so() {
        show(emptyList())
        compose.onNodeWithText("Nothing coming up").assertIsDisplayed()
    }

    @Test
    fun back_dismisses() {
        var dismissed = 0
        show(onDismiss = { dismissed++ })
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(1, dismissed)
    }
}
