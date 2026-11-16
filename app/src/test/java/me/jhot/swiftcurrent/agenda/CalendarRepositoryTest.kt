package me.jhot.swiftcurrent.agenda

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CalendarRepositoryTest {
    private val utc = ZoneId.of("UTC")
    private val honolulu = ZoneId.of("Pacific/Honolulu")
    private val denver = ZoneId.of("America/Denver")

    private fun utcMidnight(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(utc).toInstant().toEpochMilli()

    @Test
    fun all_day_rows_come_back_as_local_midnights_not_utc_midnights() {
        // The provider stores an all-day Monday 9 Nov as 9 Nov 00:00 UTC to 10 Nov 00:00 UTC.
        val e = AgendaMapper.fromRow(7, "Dentist", utcMidnight(2026, 11, 9), utcMidnight(2026, 11, 10), true, null, 5, 2, denver)
        assertEquals(LocalDate.of(2026, 11, 9).atStartOfDay(denver).toInstant(), e.start)
        assertEquals(LocalDate.of(2026, 11, 10).atStartOfDay(denver).toInstant(), e.end)
        // Read as an instant in a negative-offset zone the UTC value would be the previous evening.
        assertEquals(LocalDate.of(2026, 11, 9), e.start.atZone(denver).toLocalDate())
        val hawaii = AgendaMapper.fromRow(7, "Dentist", utcMidnight(2026, 11, 9), utcMidnight(2026, 11, 10), true, null, 5, 2, honolulu)
        assertEquals(LocalDate.of(2026, 11, 9), hawaii.start.atZone(honolulu).toLocalDate())
        assertTrue(e.allDay)
        assertEquals(7L, e.eventId)
        assertEquals(2L, e.calendarId)
    }

    @Test
    fun a_degenerate_all_day_row_still_covers_a_day() {
        val e = AgendaMapper.fromRow(1, "Dentist", utcMidnight(2026, 11, 9), utcMidnight(2026, 11, 9), true, null, 0, 1, denver)
        assertEquals(LocalDate.of(2026, 11, 10).atStartOfDay(denver).toInstant(), e.end)
    }

    @Test
    fun timed_rows_keep_their_instants_and_blank_text_becomes_null() {
        val e = AgendaMapper.fromRow(1, "", 1_000, 2_000, false, " ", 9, 3, denver)
        assertEquals(Instant.ofEpochMilli(1_000), e.start)
        assertEquals(Instant.ofEpochMilli(2_000), e.end)
        assertNull(e.title)
        assertNull(e.location)
    }

    private fun mapped(
        rrule: String? = null,
        rdate: String? = null,
        status: Int = 0,
        self: Int = 0,
        original: Long? = null,
        name: String? = "Personal",
        id: Long = 5,
    ) = AgendaMapper.occurrence(AgendaEvent("Dentist", Instant.EPOCH, Instant.EPOCH, false, null, 0, 1, id), name, original, rrule, rdate, status, self)

    @Test
    fun an_occurrence_knows_whether_it_repeats_is_declined_or_cancelled() {
        assertEquals(false, mapped().recurring)
        assertEquals(true, mapped(rrule = "FREQ=DAILY").recurring)
        assertEquals(true, mapped(rdate = "20261113T090000Z").recurring)
        assertEquals(false, mapped(rrule = " ").recurring)
        assertEquals(true, mapped(self = CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED).declined)
        assertEquals(false, mapped(self = CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED).declined)
        assertEquals(true, mapped(status = CalendarContract.Events.STATUS_CANCELED).cancelled)
    }

    @Test
    fun an_occurrence_edited_on_its_own_belongs_to_its_originals_series() {
        assertEquals(5L, mapped().seriesId)
        assertEquals(9L, mapped(original = 9).seriesId)
        // A blank calendar name is empty, never null.
        assertEquals("", mapped(name = null).calendarName)
    }

    private fun ev(calendar: Long) = AgendaEvent("Standup", Instant.EPOCH, Instant.EPOCH.plusSeconds(60), false, null, 0, calendar)

    @Test
    fun disabled_calendars_are_filtered_out() {
        val all = listOf(ev(1), ev(2), ev(3))
        assertEquals(listOf(1L, 3L), AgendaMapper.filter(all, setOf(1L, 3L)).map { it.calendarId })
        // Null means every calendar; an empty set means none.
        assertEquals(3, AgendaMapper.filter(all, null).size)
        assertEquals(0, AgendaMapper.filter(all, emptySet()).size)
        assertNull(AgendaMapper.selection(null))
        assertEquals("${CalendarContract.Instances.CALENDAR_ID} IN (1,3)", AgendaMapper.selection(setOf(1L, 3L)))
        // An empty set must select nothing, not everything (an empty IN () list is invalid SQL).
        assertEquals("0", AgendaMapper.selection(emptySet()))
    }
}

/** A calendar provider that answers Instances queries with fixed rows, or refuses like a revoked permission. */
class FakeCalendarProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, order: String?): Cursor {
        if (revoked) throw SecurityException("READ_CALENDAR denied")
        val cols = projection ?: emptyArray()
        val rows = if (uri.path.orEmpty().contains("instances")) instanceRows else calendarRows
        return MatrixCursor(cols).also { c -> rows.forEach { row -> c.addRow(cols.map { row[it] }) } }
    }

    override fun getType(uri: Uri) = null
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, args: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?) = 0

    companion object {
        @Volatile var revoked = false

        @Volatile var instanceRows: List<Map<String, Any?>> = emptyList()

        @Volatile var calendarRows: List<Map<String, Any?>> = emptyList()
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CalendarRepositoryProviderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun row(id: Long, title: String, begin: Long, calendar: Long, allDay: Int = 0, end: Long = begin + 1_800_000) = mapOf(
        CalendarContract.Instances.EVENT_ID to id, CalendarContract.Instances.TITLE to title,
        CalendarContract.Instances.BEGIN to begin, CalendarContract.Instances.END to end,
        CalendarContract.Instances.ALL_DAY to allDay, CalendarContract.Instances.EVENT_LOCATION to "Room 1",
        CalendarContract.Instances.CALENDAR_COLOR to 0xFF0000, CalendarContract.Instances.CALENDAR_ID to calendar,
    )

    private fun searchRow(
        id: Long,
        title: String,
        begin: Long,
        calendar: Long,
        extra: Map<String, Any?> = emptyMap(),
    ) = row(id, title, begin, calendar) + mapOf(CalendarContract.Instances.CALENDAR_DISPLAY_NAME to "Personal") + extra

    private fun repo(): ContentResolverCalendarRepository {
        Robolectric.setupContentProvider(FakeCalendarProvider::class.java, CalendarContract.AUTHORITY)
        return ContentResolverCalendarRepository(context)
    }

    private val now = Instant.parse("2026-11-08T12:00:00Z")

    @Test
    fun events_come_back_sorted_by_start_and_filtered_by_calendar() {
        FakeCalendarProvider.revoked = false
        val t = now.toEpochMilli()
        FakeCalendarProvider.instanceRows = listOf(
            row(1, "Team sync", t + 7_200_000, 1), row(2, "Standup", t + 3_600_000, 2), row(3, "Dentist", t + 600_000, 1),
        )
        val repo = repo()
        val all = (repo.load(now, 14, null) as CalendarAccess.Granted).events
        assertEquals(listOf("Dentist", "Standup", "Team sync"), all.map { it.title })
        val only = (repo.load(now, 14, setOf(1L)) as CalendarAccess.Granted).events
        assertEquals(listOf("Dentist", "Team sync"), only.map { it.title })
        assertEquals(emptyList<AgendaEvent>(), (repo.load(now, 14, emptySet()) as CalendarAccess.Granted).events)
    }

    @Test
    fun revoked_permission_gives_denied_not_a_crash() {
        FakeCalendarProvider.revoked = true
        val repo = repo()
        assertEquals(CalendarAccess.Denied, repo.load(now, 14, null))
        // Denied means no events at all, from either read path.
        assertEquals(
            emptyList<AgendaEvent>(),
            (repo.load(now, 14, setOf(1L)) as? CalendarAccess.Granted)?.events.orEmpty(),
        )
        assertEquals(emptyList<CalendarInfo>(), repo.calendars())
    }

    @Test
    fun calendars_list_their_name_color_and_account() {
        FakeCalendarProvider.revoked = false
        FakeCalendarProvider.calendarRows = listOf(
            mapOf(
                CalendarContract.Calendars._ID to 4L, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME to "Personal",
                CalendarContract.Calendars.CALENDAR_COLOR to 0x00FF00, CalendarContract.Calendars.ACCOUNT_NAME to "me@example.com",
            ),
        )
        assertEquals(listOf(CalendarInfo(4, "Personal", 0x00FF00, "me@example.com")), repo().calendars())
    }

    @Test
    fun occurrences_carry_calendar_name_repeat_declined_cancelled_and_series() {
        FakeCalendarProvider.revoked = false
        val t = now.toEpochMilli()
        FakeCalendarProvider.instanceRows = listOf(
            searchRow(1, "Standup", t + 3_600_000, 1, mapOf(CalendarContract.Instances.RRULE to "FREQ=DAILY")),
            searchRow(2, "Lunch", t + 7_200_000, 1, mapOf(CalendarContract.Instances.SELF_ATTENDEE_STATUS to CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED)),
            searchRow(3, "Trip", t + 9_000_000, 1, mapOf(CalendarContract.Instances.STATUS to CalendarContract.Events.STATUS_CANCELED)),
            searchRow(4, "Moved", t + 10_000_000, 1, mapOf(CalendarContract.Instances.ORIGINAL_ID to 1L)),
        )
        val byTitle = repo().occurrences(now, 365, null).associateBy { it.event.title }
        assertEquals("Personal", byTitle.getValue("Standup").calendarName)
        assertEquals(true, byTitle.getValue("Standup").recurring)
        assertEquals(true, byTitle.getValue("Lunch").declined)
        assertEquals(true, byTitle.getValue("Trip").cancelled)
        assertEquals(1L, byTitle.getValue("Moved").seriesId)
        assertEquals(4L, byTitle.getValue("Moved").event.eventId)
    }

    @Test
    fun occurrences_honour_the_enabled_calendars_including_none() {
        FakeCalendarProvider.revoked = false
        val t = now.toEpochMilli()
        FakeCalendarProvider.instanceRows = listOf(searchRow(1, "A", t + 600_000, 1), searchRow(2, "B", t + 700_000, 2))
        val repo = repo()
        assertEquals(listOf("A", "B"), repo.occurrences(now, 365, null).map { it.event.title })
        assertEquals(listOf("B"), repo.occurrences(now, 365, setOf(2L)).map { it.event.title })
        assertEquals(emptyList<EventOccurrence>(), repo.occurrences(now, 365, emptySet()))
    }

    @Test
    fun occurrences_are_empty_when_permission_is_denied() {
        FakeCalendarProvider.revoked = true
        assertEquals(emptyList<EventOccurrence>(), repo().occurrences(now, 365, null))
    }

    @Test
    fun changes_emit_when_the_calendar_provider_notifies() = runBlocking {
        FakeCalendarProvider.revoked = false
        val repo = repo()
        withTimeout(5_000) {
            val poke = launch(Dispatchers.Default) {
                while (true) {
                    context.contentResolver.notifyChange(CalendarContract.CONTENT_URI, null)
                    delay(20)
                }
            }
            repo.changes.first()
            poke.cancel()
        }
    }

    @Test
    fun changes_end_quietly_when_the_calendar_permission_is_missing() = runBlocking {
        // A real resolver refuses to register an observer on a provider the app may not read; Robolectric's does not.
        val refusing = ContentResolverCalendarRepository(context, registerObserver = { _, _ -> throw SecurityException("READ_CALENDAR denied") })
        assertEquals(emptyList<Unit>(), withTimeout(5_000) { refusing.changes.toList() })
    }
}
