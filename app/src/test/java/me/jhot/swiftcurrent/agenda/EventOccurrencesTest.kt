package me.jhot.swiftcurrent.agenda

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class EventOccurrencesTest {
    private val now = Instant.parse("2026-11-12T12:00:00Z")

    private fun h(hours: Long) = now.plusSeconds(hours * 3600)

    private fun occ(
        eventId: Long,
        startH: Long,
        endH: Long = startH + 1,
        series: Long = eventId,
        declined: Boolean = false,
        cancelled: Boolean = false,
        title: String = "Event $eventId",
    ) = EventOccurrence(
        event = AgendaEvent(title, h(startH), h(endH), false, null, 0, 1, eventId),
        calendarName = "Personal",
        seriesId = series,
        recurring = series != eventId,
        declined = declined,
        cancelled = cancelled,
    )

    private fun ids(rows: List<EventOccurrence>) = EventOccurrences.select(rows, now).map { it.event.eventId }

    @Test
    fun drops_declined_and_cancelled() {
        assertEquals(listOf(3L), ids(listOf(occ(1, 2, declined = true), occ(2, 3, cancelled = true), occ(3, 4))))
    }

    @Test
    fun drops_events_that_already_ended() {
        assertEquals(listOf(2L), ids(listOf(occ(1, -3, endH = 0), occ(2, 1))))
    }

    @Test
    fun keeps_an_ongoing_event() {
        assertEquals(listOf(1L), ids(listOf(occ(1, -1, endH = 1))))
    }

    @Test
    fun a_daily_series_collapses_to_its_ongoing_occurrence() {
        val days = (0L..5L).map { occ(eventId = 10, startH = it * 24 - 1, endH = it * 24 + 1, series = 10) }
        val chosen = EventOccurrences.select(days, now)
        assertEquals(1, chosen.size)
        assertEquals(h(-1), chosen.single().event.start)
    }

    @Test
    fun a_daily_series_collapses_to_the_next_occurrence_when_none_is_ongoing() {
        val days = (1L..5L).map { occ(eventId = 10, startH = it * 24, series = 10) }
        assertEquals(listOf(h(24)), EventOccurrences.select(days, now).map { it.event.start })
    }

    @Test
    fun a_declined_instance_is_skipped_before_collapsing() {
        val rows = listOf(occ(10, 24, series = 10, declined = true), occ(10, 48, series = 10))
        assertEquals(listOf(h(48)), EventOccurrences.select(rows, now).map { it.event.start })
    }

    @Test
    fun a_moved_instance_and_its_series_collapse_to_one_row() {
        // Editing one occurrence gives it its own event id, with the series as its original.
        val rows = listOf(occ(eventId = 10, startH = 48, series = 10), occ(eventId = 11, startH = 30, series = 10))
        assertEquals(listOf(11L), ids(rows))
    }

    @Test
    fun result_is_sorted_soonest_first() {
        assertEquals(listOf(2L, 3L, 1L), ids(listOf(occ(1, 30), occ(2, -1, endH = 2), occ(3, 5))))
    }
}
