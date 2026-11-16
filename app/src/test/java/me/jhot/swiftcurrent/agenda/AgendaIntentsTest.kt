package me.jhot.swiftcurrent.agenda

import android.content.Intent
import android.provider.CalendarContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AgendaIntentsTest {
    private val denver = ZoneId.of("America/Denver")

    @Test
    fun a_new_event_proposes_nine_in_the_morning_local_time() {
        val i = AgendaIntents.newEvent(LocalDate.of(2026, 11, 9), denver)
        assertEquals(Intent.ACTION_INSERT, i.action)
        assertEquals(CalendarContract.Events.CONTENT_URI, i.data)
        val nine = ZonedDateTime.of(2026, 11, 9, 9, 0, 0, 0, denver).toInstant().toEpochMilli()
        assertEquals(nine, i.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1))
    }

    @Test
    fun opening_an_event_views_it_by_id_with_its_times() {
        val start = ZonedDateTime.of(2026, 11, 9, 11, 30, 0, 0, denver).toInstant()
        val e = AgendaEvent("Standup", start, start.plusSeconds(1800), false, null, 0, 1, eventId = 42)
        val i = AgendaIntents.view(e, denver)
        assertEquals(Intent.ACTION_VIEW, i.action)
        assertEquals("${CalendarContract.Events.CONTENT_URI}/42", i.dataString)
        assertEquals(start.toEpochMilli(), i.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1))
        assertEquals(start.plusSeconds(1800).toEpochMilli(), i.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1))
    }

    @Test
    fun an_all_day_event_is_passed_as_utc_midnights_the_way_the_provider_stores_it() {
        val e = AgendaMapper.fromRow(
            9, "Dentist",
            LocalDate.of(2026, 11, 9).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
            LocalDate.of(2026, 11, 10).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
            true, null, 0, 1, denver,
        )
        val i = AgendaIntents.view(e, denver)
        assertEquals(Instant.parse("2026-11-09T00:00:00Z").toEpochMilli(), i.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1))
        assertTrue(i.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false))
    }
}
