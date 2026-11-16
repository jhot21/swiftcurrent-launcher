package me.jhot.swiftcurrent.agenda

import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The calendar app's own screens for adding and opening events; the launcher never edits events itself. */
object AgendaIntents {
    private val NINE_AM = LocalTime.of(9, 0)

    /** A new event on [date], proposed for 9:00 local so the owner only has to name it. */
    fun newEvent(date: LocalDate, zone: ZoneId): Intent =
        Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, date.atTime(NINE_AM).atZone(zone).toInstant().toEpochMilli())

    fun view(event: AgendaEvent, zone: ZoneId): Intent {
        // The calendar app expects an all-day event's times as UTC midnights, the provider's own form.
        val utc = ZoneId.of("UTC")
        fun ms(at: java.time.Instant) =
            if (event.allDay) at.atZone(zone).toLocalDate().atStartOfDay(utc).toInstant().toEpochMilli() else at.toEpochMilli()
        return Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, ms(event.start))
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, ms(event.end))
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, event.allDay)
    }
}
