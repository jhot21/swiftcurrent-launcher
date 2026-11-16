package me.jhot.swiftcurrent.search.sources

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import me.jhot.swiftcurrent.agenda.AgendaEvent
import me.jhot.swiftcurrent.agenda.AgendaFormatter
import me.jhot.swiftcurrent.agenda.CalendarInfo
import me.jhot.swiftcurrent.agenda.CalendarRepository
import me.jhot.swiftcurrent.agenda.EnglishAgendaStrings
import me.jhot.swiftcurrent.agenda.EventOccurrence
import me.jhot.swiftcurrent.agenda.LocationIntentSpec
import me.jhot.swiftcurrent.search.Kind
import me.jhot.swiftcurrent.search.LaunchOutcome
import me.jhot.swiftcurrent.search.Ranking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class EventSourceTest {
    private val denver = ZoneId.of("America/Denver")
    private val now = Instant.parse("2026-11-08T12:00:00Z")

    private fun h(hours: Long) = now.plusSeconds(hours * 3600)

    private class Repo(var rows: List<EventOccurrence> = emptyList()) : CalendarRepository {
        var reads = 0
        var gate: CompletableDeferred<Unit>? = null
        var failNext = false
        var readMs = 0L
        override var changes: kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.emptyFlow()
        override fun load(now: Instant, days: Int, enabled: Set<Long>?) = throw UnsupportedOperationException()
        override fun calendars(): List<CalendarInfo> = emptyList()
        override fun occurrences(now: Instant, days: Int, enabled: Set<Long>?): List<EventOccurrence> {
            reads++
            if (readMs > 0) Thread.sleep(readMs)
            gate?.let { runBlocking { it.await() } }
            if (failNext) {
                failNext = false
                error("provider down")
            }
            return rows.filter { enabled == null || it.event.calendarId in enabled }
        }
    }

    private class Actions : EventActions {
        val opened = mutableListOf<Long>()
        val launched = mutableListOf<LocationIntentSpec>()
        override fun open(event: AgendaEvent): LaunchOutcome {
            opened += event.eventId
            return LaunchOutcome.Launched
        }
        override fun launch(spec: LocationIntentSpec): LaunchOutcome {
            launched += spec
            return LaunchOutcome.Launched
        }
    }

    private fun occ(
        id: Long,
        title: String?,
        startH: Long = 48,
        calendar: Long = 1,
        calendarName: String = "Personal",
        location: String? = null,
        recurring: Boolean = false,
        allDay: Boolean = false,
    ) = EventOccurrence(
        AgendaEvent(title, h(startH), h(startH + 1), allDay, location, 0, calendar, id),
        calendarName, id, recurring, declined = false, cancelled = false,
    )

    private val repo = Repo()
    private val actions = Actions()
    private var granted = true
    private var enabled: Set<Long>? = null

    private fun source() = EventSource(
        repo, { granted }, { enabled }, { now }, { denver }, AgendaFormatter(EnglishAgendaStrings, Locale.US), actions, Dispatchers.Unconfined,
    )

    private fun find(query: String, s: EventSource = source()) =
        Ranking.order(query, runBlocking { s.search(query) })

    @Test
    fun finds_an_event_by_title_and_opens_it_in_the_calendar() {
        repo.rows = listOf(occ(7, "Dentist", startH = 24))
        val r = find("dentist").single()
        assertEquals("Dentist", r.label)
        assertEquals(Kind.Event, r.kind)
        assertEquals("event:7", r.id)
        assertEquals("Personal", r.prefix)
        r.primary.perform()
        assertEquals(listOf(7L), actions.opened)
    }

    @Test
    fun detail_is_the_date_and_time_apart_from_the_calendar_and_marks_recurrence() {
        repo.rows = listOf(occ(1, "Standup", startH = 24, recurring = true), occ(2, "Trip", startH = 72, allDay = true))
        val s = source()
        val standup = find("standup", s).single()
        // 12:00 UTC is 05:00 in Denver, so 24h later is tomorrow at the same hour.
        assertEquals("Personal", standup.prefix)
        assertEquals("Tomorrow · 5:00 AM", standup.detail)
        assertTrue(standup.recurring)
        val trip = find("trip", s).single()
        assertEquals("Wed, Nov 11", trip.detail)
        assertFalse(trip.recurring)
    }

    @Test
    fun does_not_match_on_the_calendar_name() {
        repo.rows = listOf(occ(1, "Standup", calendarName = "Dentist office"))
        assertTrue(find("dentist").isEmpty())
    }

    @Test
    fun blank_title_events_are_not_searchable() {
        repo.rows = listOf(occ(1, null), occ(2, "  "), occ(3, "Lunch"))
        assertEquals(listOf("Lunch"), find("l").map { it.label })
        assertTrue(find(" ").isEmpty())
    }

    @Test
    fun returns_at_most_five_soonest_first() {
        repo.rows = (1L..8L).map { occ(it, "Dentist", startH = 100 - it) }
        val ids = find("dentist").map { it.id }
        assertEquals(listOf("event:8", "event:7", "event:6", "event:5", "event:4"), ids)
    }

    @Test
    fun same_name_events_are_soonest_first() {
        repo.rows = listOf(occ(1, "Dinner", startH = 90), occ(2, "Dinner", startH = 30), occ(3, "Dinner", startH = 60))
        assertEquals(listOf("event:2", "event:3", "event:1"), find("dinner").map { it.id })
    }

    @Test
    fun results_carry_their_start_so_ranking_can_order_by_date() {
        repo.rows = listOf(occ(1, "Dinner", startH = 30))
        assertEquals(h(30).toEpochMilli(), find("dinner").single().startMillis)
    }

    @Test
    fun the_five_shown_are_the_soonest_word_start_matches_whatever_the_word_order() {
        // Title-first and name-first wordings are equals, so five late "Dentist ..." events must not push out sooner "... Dentist" ones.
        repo.rows = (1L..5L).map { occ(it, "Dentist Alex", startH = 200 + it) } +
            (6L..8L).map { occ(it, "Jordan Dentist", startH = it) }
        assertEquals(
            listOf("event:6", "event:7", "event:8", "event:1", "event:2"),
            find("dentist").map { it.id },
        )
    }

    @Test
    fun location_becomes_a_single_line_secondary_action_that_performs_the_location_specs() {
        repo.rows = listOf(occ(1, "Dinner", location = "Osteria Rosa\n120 Main St"))
        val r = find("dinner").single()
        val action = r.secondary.single()
        assertEquals("Osteria Rosa 120 Main St", action.label)
        assertEquals(LaunchOutcome.Launched, action.perform())
        assertEquals("geo:0,0?q=Osteria%20Rosa%20120%20Main%20St", actions.launched.single().uri)
    }

    @Test
    fun no_location_means_no_secondary_action() {
        repo.rows = listOf(occ(1, "Dentist"), occ(2, "Lunch", location = "  "))
        assertTrue(find("dentist").single().secondary.isEmpty())
        assertTrue(find("lunch").single().secondary.isEmpty())
    }

    @Test
    fun a_calendar_left_out_of_the_selection_is_not_searched() {
        repo.rows = listOf(occ(1, "Dentist", calendar = 1), occ(2, "Dentist visit", calendar = 2))
        enabled = setOf(2L)
        assertEquals(listOf("Dentist visit"), find("dentist").map { it.label })
        enabled = emptySet()
        assertTrue(find("dentist").isEmpty())
    }

    @Test
    fun a_selection_change_applies_to_an_already_loaded_cache() {
        repo.rows = listOf(occ(1, "Dentist", calendar = 1))
        val s = source()
        assertEquals(1, find("dentist", s).size)
        enabled = setOf(2L)
        assertTrue(find("dentist", s).isEmpty())
    }

    @Test
    fun nothing_without_permission() {
        repo.rows = listOf(occ(1, "Dentist"))
        granted = false
        assertTrue(find("dentist").isEmpty())
        assertEquals(0, repo.reads)
    }

    @Test
    fun events_that_have_ended_since_the_load_drop_out() {
        repo.rows = listOf(occ(1, "Dentist", startH = 1))
        var clock = now
        val s = EventSource(repo, { true }, { null }, { clock }, { denver }, AgendaFormatter(EnglishAgendaStrings, Locale.US), actions, Dispatchers.Unconfined)
        assertEquals(1, find("dentist", s).size)
        clock = h(3)
        assertTrue(find("dentist", s).isEmpty())
    }

    @Test
    fun resolve_waits_for_the_first_load() = runBlocking {
        repo.rows = listOf(occ(7, "Dentist"))
        repo.gate = CompletableDeferred()
        val s = source()
        val refresh = async(Dispatchers.Default) { s.refresh() }
        while (repo.reads == 0) kotlinx.coroutines.delay(5)
        val resolved = async(Dispatchers.Default) { s.resolve(setOf("event:7", "event:99")) }
        kotlinx.coroutines.delay(50)
        assertFalse(resolved.isCompleted)
        repo.gate!!.complete(Unit)
        refresh.await()
        assertEquals(listOf("event:7"), resolved.await().map { it.id })
    }

    @Test
    fun a_refresh_right_after_a_cold_load_does_not_read_again() = runBlocking {
        repo.rows = listOf(occ(7, "Dentist"))
        val s = source()
        // Recents open first, so the cold read happens before the open's refresh arrives.
        assertEquals(1, s.resolve(setOf("event:7")).size)
        s.refresh()
        assertEquals(1, repo.reads)
        // Later refreshes (the next open, a change) do read.
        s.refresh()
        assertEquals(2, repo.reads)
    }

    @Test
    fun a_burst_of_calendar_changes_is_not_one_read_each() = runBlocking {
        repo.rows = listOf(occ(7, "Dentist"))
        repo.readMs = 30
        repo.changes = kotlinx.coroutines.flow.flow { repeat(10) { emit(Unit) } }
        val s = EventSource(repo, { true }, { null }, { now }, { denver }, AgendaFormatter(EnglishAgendaStrings, Locale.US), actions, Dispatchers.Default)
        s.followChanges()
        assertTrue("read ${repo.reads} times for 10 changes", repo.reads in 1..4)
    }

    @Test
    fun a_failed_refresh_keeps_the_previous_events() = runBlocking {
        repo.rows = listOf(occ(1, "Dentist"))
        val s = source()
        s.refresh()
        repo.failNext = true
        val failure = runCatching { s.refresh() }.exceptionOrNull()
        assertEquals("provider down", failure?.message)
        assertEquals(1, runBlocking { s.search("dentist") }.size)
    }
}
