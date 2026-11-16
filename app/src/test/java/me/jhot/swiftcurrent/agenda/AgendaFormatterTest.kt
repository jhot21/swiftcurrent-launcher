package me.jhot.swiftcurrent.agenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class AgendaFormatterTest {
    private val denver = ZoneId.of("America/Denver")
    private val f = AgendaFormatter(EnglishAgendaStrings, Locale.US)

    private fun at(zone: ZoneId, y: Int, m: Int, d: Int, h: Int, min: Int = 0): Instant =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant()

    private fun mt(d: Int, h: Int, min: Int = 0, month: Int = 11) = at(denver, 2026, month, d, h, min)

    private fun timed(title: String?, start: Instant, end: Instant, calendar: Long = 1, location: String? = null) =
        AgendaEvent(title, start, end, false, location, 0xFF3366, calendar)

    /** An all-day event the way the provider stores it (UTC midnights), mapped to local time. */
    private fun allDay(title: String?, first: LocalDate, lastInclusive: LocalDate, zone: ZoneId = denver) =
        AgendaMapper.fromRow(
            0, title,
            first.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
            lastInclusive.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
            true, null, 0xFF3366, 1, zone,
        )

    // Sunday 8 Nov 2026, 22:23 local (MST).
    private val now = mt(8, 22, 23)

    @Test
    fun today_and_tomorrow_are_labeled() {
        val days = f.days(
            listOf(
                timed("Team sync", mt(10, 9), mt(10, 10)),
                timed("Standup", mt(9, 11, 30), mt(9, 12)),
                timed("Dentist", mt(8, 23), mt(8, 23, 45)),
            ),
            now, denver,
        )
        assertEquals(listOf(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10)), days.map { it.date })
        assertEquals(listOf("today", "tomorrow", null), days.map { it.label })
        assertEquals(listOf("Dentist"), days[0].events.map { it.title })
    }

    @Test
    fun events_group_by_local_date_not_utc_date() {
        // 23:30 local on the 8th is already the 9th in UTC.
        val days = f.days(listOf(timed("Dentist", mt(8, 23, 30), mt(9, 0, 30))), now, denver)
        assertEquals(
            listOf(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 9)),
            days.map { it.date },
        )
    }

    @Test
    fun relative_reads_in_13h_and_in_19h() {
        assertEquals("in 13h", f.relative(mt(9, 11, 30), now))
        assertEquals("in 19h", f.relative(mt(9, 18), now))
        assertEquals("in 45m", f.relative(now.plusSeconds(45 * 60 + 20), now))
        assertEquals("now", f.relative(now, now))
        assertEquals("now", f.relative(now.minusSeconds(600), now))
        assertEquals("in 2d", f.relative(now.plusSeconds(2 * 86_400L + 3 * 3_600L), now))
    }

    @Test
    fun dst_change_does_not_break_relative_hours() {
        // DST ends 1 Nov 2026 at 02:00 local: 00:00 to 09:00 that morning is TEN hours, not nine.
        val midnight = mt(1, 0)
        val nine = mt(1, 9)
        assertEquals("in 10h", f.relative(nine, midnight))
        // Spring forward (8 Mar 2026): 00:00 to 09:00 is EIGHT hours.
        assertEquals("in 8h", f.relative(mt(8, 9, month = 3), mt(8, 0, month = 3)))
    }

    @Test
    fun dst_day_all_day_event_stays_on_one_date() {
        val days = f.days(listOf(allDay("Dentist", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1))), mt(1, 6), denver)
        assertEquals(listOf(LocalDate.of(2026, 11, 1)), days.map { it.date })
    }

    @Test
    fun all_day_event_has_no_time_range() {
        val day = LocalDate.of(2026, 11, 9)
        val event = allDay("Dentist", day, day)
        assertNull(f.timeRange(event, denver))
        val timed = timed("Standup", mt(9, 11, 30), mt(9, 12))
        assertEquals("11:30 AM–12:00 PM", f.timeRange(timed, denver))
    }

    @Test
    fun multi_day_event_appears_on_each_day() {
        // Fri 6 to Sun 8 Nov, all day.
        val trip = allDay("Team sync", LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 8))
        val days = f.days(listOf(trip), mt(6, 8), denver)
        assertEquals(
            listOf(LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 8)),
            days.map { it.date },
        )
        assertEquals(listOf(1, 1, 1), days.map { it.events.size })
        // A timed event over midnight also shows on both dates.
        val overnight = f.days(listOf(timed("Standup", mt(9, 22), mt(10, 2))), mt(9, 8), denver)
        assertEquals(2, overnight.size)
    }

    @Test
    fun all_day_events_sort_before_timed_ones_and_ended_events_are_dropped() {
        val tomorrow = LocalDate.of(2026, 11, 9)
        val days = f.days(
            listOf(
                timed("Standup", mt(9, 9), mt(9, 10)),
                allDay("Dentist", tomorrow, tomorrow),
                timed("Team sync", mt(8, 8), mt(8, 9)),
            ),
            now, denver,
        )
        assertEquals(listOf("Dentist", "Standup"), days.last().events.map { it.title })
    }

    @Test
    fun untitled_event_gets_a_placeholder() {
        val e = timed(null, mt(8, 23), mt(8, 23, 30))
        assertEquals("(No title)", f.title(e))
        assertEquals("(No title)", f.title(e.copy(title = "  ")))
        assertEquals("(No title) · 11:00 PM", f.nextLine(listOf(e), now, denver))
    }

    @Test
    fun no_events_gives_null_next_line_and_hides_the_block() {
        assertNull(f.nextLine(emptyList(), now, denver))
        // Everything already ended: nothing to show either.
        assertNull(f.nextLine(listOf(timed("Standup", mt(8, 9), mt(8, 10))), now, denver))
    }

    @Test
    fun next_line_names_the_event_the_time_and_how_many_more_that_day() {
        val events = listOf(
            timed("Standup", mt(9, 11, 30), mt(9, 12)),
            timed("Team sync", mt(9, 18), mt(9, 19)),
            timed("Dentist", mt(9, 20), mt(9, 21)),
            timed("Standup", mt(10, 9), mt(10, 10)),
        )
        assertEquals("Standup · 11:30 AM +2", f.nextLine(events, now, denver))
        // The tail is the part Home keeps readable while the title gives way.
        assertEquals(" \u00b7 11:30 AM +2", f.nextLineParts(events, now, denver)?.tail)
        assertEquals("Standup · 11:30 AM", f.nextLine(events.take(1), now, denver))

        // A locale that puts the title last has nothing to cut, so the tail is empty.
        val titleLast = AgendaFormatter(
            object : AgendaStrings by EnglishAgendaStrings {
                override fun nextLine(title: String, whenText: String) = "$whenText · $title"
                override fun nextLineMore(title: String, whenText: String, more: Int) = "$whenText · $title +$more"
            },
            Locale.US,
        )
        assertEquals("11:30 AM · Standup", titleLast.nextLine(events.take(1), now, denver))
        assertEquals("", titleLast.nextLineParts(events.take(1), now, denver)?.tail)
    }

    @Test
    fun next_line_for_all_day_events_says_today_tomorrow_or_the_weekday() {
        val sunday = LocalDate.of(2026, 11, 8)
        assertEquals("Dentist · today", f.nextLine(listOf(allDay("Dentist", sunday, sunday)), now, denver))
        val monday = sunday.plusDays(1)
        assertEquals("Dentist · tomorrow", f.nextLine(listOf(allDay("Dentist", monday, monday)), now, denver))
        val wednesday = sunday.plusDays(3)
        assertEquals("Dentist · Wednesday", f.nextLine(listOf(allDay("Dentist", wednesday, wednesday)), now, denver))
    }

    @Test
    fun an_event_in_progress_is_the_next_event() {
        val events = listOf(
            timed("Team sync", mt(8, 22), mt(8, 23)),
            timed("Standup", mt(9, 8), mt(9, 9)),
        )
        assertEquals("Team sync · 10:00 PM +0".removeSuffix(" +0"), f.nextLine(events, now, denver))
    }

    @Test
    fun an_empty_today_still_has_a_day_entry_and_a_new_event_row() {
        val today = LocalDate.of(2026, 11, 8)
        val none = f.days(emptyList(), now, denver)
        assertEquals(listOf(today), none.map { it.date })
        assertEquals("today", none.single().label)
        assertEquals(emptyList<AgendaEvent>(), none.single().events)
        // Today's events all ended: it is still offered.
        val ended = f.days(listOf(timed("Standup", mt(8, 9), mt(8, 10))), now, denver)
        assertEquals(listOf(today), ended.map { it.date })
        assertNull(f.nextLine(emptyList(), now, denver))
    }

    @Test
    fun days_with_events_after_an_empty_today_keep_their_order() {
        val days = f.days(
            listOf(timed("Dentist", mt(11, 9), mt(11, 10)), timed("Standup", mt(9, 9), mt(9, 10))),
            now, denver,
        )
        assertEquals(
            listOf(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 11)),
            days.map { it.date },
        )
        assertEquals(listOf("today", "tomorrow", null), days.map { it.label })
        assertEquals(listOf(0, 1, 1), days.map { it.events.size })
    }

    @Test
    fun relative_boundaries() {
        assertEquals("in 59m", f.relative(now.plusSeconds(59 * 60 + 59), now))
        assertEquals("in 1h", f.relative(now.plusSeconds(3600), now))
        assertEquals("in 23h", f.relative(now.plusSeconds(23 * 3600 + 59 * 60), now))
        assertEquals("in 1d", f.relative(now.plusSeconds(24 * 3600), now))
        // Rule: anything in the future under a minute reads "in 1m"; "now" is only for started events.
        assertEquals("in 1m", f.relative(now.plusSeconds(20), now))
        assertEquals("now", f.relative(now, now))
        assertEquals("now", f.relative(now.minusSeconds(1800), now))
    }

    @Test
    fun an_event_ending_exactly_now_is_dropped_and_one_in_progress_is_kept() {
        val endsNow = timed("Standup", now.minusSeconds(1800), now)
        assertNull(f.nextLine(listOf(endsNow), now, denver))
        assertEquals(emptyList<AgendaEvent>(), f.days(listOf(endsNow), now, denver).single().events)
        val running = timed("Team sync", now.minusSeconds(60), now.plusSeconds(60))
        assertEquals(listOf("Team sync"), f.days(listOf(running), now, denver).single().events.map { it.title })
        assertEquals("now", f.relative(running.start, now))
    }

    @Test
    fun plus_n_counts_an_all_day_and_timed_mix_on_the_same_date() {
        val sunday = LocalDate.of(2026, 11, 8)
        val events = listOf(
            timed("Standup", mt(8, 23, 30), mt(8, 23, 45)),
            allDay("Dentist", sunday, sunday),
            timed("Team sync", mt(8, 23), mt(8, 23, 20)),
        )
        assertEquals("Dentist \u00b7 today +2", f.nextLine(events, now, denver))
    }

    @Test
    fun event_when_names_today_and_tomorrow_then_a_weekday_date() {
        assertEquals("Today \u00b7 11:00 PM", f.eventWhen(timed("A", mt(8, 23), mt(8, 23, 30)), now, denver))
        assertEquals("Tomorrow \u00b7 9:00 AM", f.eventWhen(timed("A", mt(9, 9), mt(9, 10)), now, denver))
        assertEquals("Fri, Nov 13 \u00b7 9:00 AM", f.eventWhen(timed("A", mt(13, 9), mt(13, 10)), now, denver))
    }

    @Test
    fun event_when_adds_the_year_only_when_it_is_not_this_one() {
        assertEquals("Wed, Jan 13, 2027 \u00b7 9:30 AM", f.eventWhen(timed("A", at(denver, 2027, 1, 13, 9, 30), at(denver, 2027, 1, 13, 10)), now, denver))
    }

    @Test
    fun event_when_has_no_time_for_an_all_day_event() {
        val e = allDay("Trip", LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 13))
        assertEquals("Fri, Nov 13", f.eventWhen(e, now, denver))
        assertEquals("Today", f.eventWhen(allDay("Trip", LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 8)), now, denver))
    }
}
