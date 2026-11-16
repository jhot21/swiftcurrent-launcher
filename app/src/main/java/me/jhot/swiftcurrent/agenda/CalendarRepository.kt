package me.jhot.swiftcurrent.agenda

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.provider.CalendarContract
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * One occurrence of a calendar event. For an all-day event [start] and [end] are LOCAL midnights
 * ([end] exclusive), already converted from the provider's UTC midnights by [AgendaMapper].
 */
data class AgendaEvent(
    val title: String?,
    val start: Instant,
    val end: Instant,
    val allDay: Boolean,
    val location: String?,
    val calendarColor: Int,
    val calendarId: Long,
    val eventId: Long = 0,
)

/** A calendar the owner can switch on or off in settings. */
data class CalendarInfo(val id: Long, val name: String, val color: Int, val accountName: String)

sealed interface CalendarAccess {
    data class Granted(val events: List<AgendaEvent>) : CalendarAccess

    /** Calendar permission is missing or was revoked; the agenda block is hidden. */
    data object Denied : CalendarAccess
}

interface CalendarRepository {
    /** Blocking provider query: callers run it off the main thread. [enabled] null means every calendar. */
    fun load(now: Instant, days: Int, enabled: Set<Long>?): CalendarAccess

    fun calendars(): List<CalendarInfo>

    /**
     * Every occurrence from [now] for [days] with what search needs to choose among them. Blocking, like [load];
     * empty when the permission is missing.
     */
    fun occurrences(now: Instant, days: Int, enabled: Set<Long>?): List<EventOccurrence> = emptyList()

    /** Emits whenever the calendar provider says something changed. */
    val changes: Flow<Unit> get() = emptyFlow()
}

/** Pure row handling, kept apart from the cursor so the all-day quirk is testable. */
object AgendaMapper {
    private val utc: ZoneId = ZoneId.of("UTC")

    /**
     * Timed rows keep their instants. An all-day row's BEGIN and END are midnights in UTC whatever the
     * device zone, so they are read as dates and re-anchored to local midnights (END is exclusive);
     * taking them as instants would put the event on the previous evening west of Greenwich.
     */
    @Suppress("LongParameterList")
    fun fromRow(
        eventId: Long,
        title: String?,
        beginMs: Long,
        endMs: Long,
        allDay: Boolean,
        location: String?,
        color: Int,
        calendarId: Long,
        zone: ZoneId,
    ): AgendaEvent {
        var start = Instant.ofEpochMilli(beginMs)
        var end = Instant.ofEpochMilli(endMs)
        if (allDay) {
            val first = start.atZone(utc).toLocalDate()
            val after = end.atZone(utc).toLocalDate().let { if (it.isAfter(first)) it else first.plusDays(1) }
            start = first.atStartOfDay(zone).toInstant()
            end = after.atStartOfDay(zone).toInstant()
        }
        return AgendaEvent(
            title = title?.takeIf { it.isNotBlank() },
            start = start, end = end, allDay = allDay,
            location = location?.takeIf { it.isNotBlank() },
            calendarColor = color, calendarId = calendarId, eventId = eventId,
        )
    }

    /** The search view of a row: the event plus the flags [EventOccurrences.select] chooses by. */
    @Suppress("LongParameterList")
    fun occurrence(
        event: AgendaEvent,
        calendarName: String?,
        originalId: Long?,
        rrule: String?,
        rdate: String?,
        status: Int,
        selfStatus: Int,
    ) = EventOccurrence(
        event = event,
        calendarName = calendarName.orEmpty(),
        seriesId = originalId ?: event.eventId,
        recurring = !rrule.isNullOrBlank() || !rdate.isNullOrBlank(),
        declined = selfStatus == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED,
        cancelled = status == CalendarContract.Events.STATUS_CANCELED,
    )

    /** Null [enabled] keeps everything, an empty set keeps nothing. */
    fun filter(events: List<AgendaEvent>, enabled: Set<Long>?): List<AgendaEvent> =
        if (enabled == null) events else events.filter { it.calendarId in enabled }

    /** The SQL selection for the enabled calendars, or null for no restriction. */
    fun selection(enabled: Set<Long>?): String? = when {
        enabled == null -> null
        // `IN ()` is invalid SQL, and an empty set must show nothing.
        enabled.isEmpty() -> "0"
        else -> "${CalendarContract.Instances.CALENDAR_ID} IN (${enabled.sorted().joinToString(",")})"
    }
}

class ContentResolverCalendarRepository(
    context: Context,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    /** How the observer is attached; a test stands in for a resolver that refuses it. */
    private val registerObserver: (ContentResolver, ContentObserver) -> Unit = { r, o ->
        r.registerContentObserver(CalendarContract.CONTENT_URI, true, o)
    },
) : CalendarRepository {
    private val resolver = context.applicationContext.contentResolver

    override fun load(now: Instant, days: Int, enabled: Set<Long>?): CalendarAccess = try {
        // The selection already restricts the rows; filtering again is a cheap guard so a provider that
        // ignores it cannot leak a calendar the owner switched off. It also keeps the provider test honest,
        // since its fake does not apply SQL.
        CalendarAccess.Granted(AgendaMapper.filter(read(now, days, enabled, INSTANCE_COLUMNS, ::event), enabled).sortedBy { it.start })
    } catch (e: SecurityException) {
        CalendarAccess.Denied
    }

    override fun occurrences(now: Instant, days: Int, enabled: Set<Long>?): List<EventOccurrence> = try {
        read(now, days, enabled, SEARCH_COLUMNS, ::occurrence).filter { enabled == null || it.event.calendarId in enabled }
    } catch (e: SecurityException) {
        emptyList()
    }

    override val changes: Flow<Unit> = callbackFlow {
        // No handler: the callback runs on the notifying thread, which is all trySend needs.
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        try {
            registerObserver(resolver, observer)
        } catch (e: SecurityException) {
            // Without READ_CALENDAR the provider refuses even an observer. There is nothing to watch, and a flow
            // that threw here would take down whoever collects it (search, on every open).
            close()
            return@callbackFlow
        }
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    /** The one Instances read, so the agenda and search cannot drift apart. Throws [SecurityException] when access is missing. */
    private fun <T> read(now: Instant, days: Int, enabled: Set<Long>?, columns: Array<String>, map: (Cursor, ZoneId) -> T): List<T> {
        if (enabled != null && enabled.isEmpty()) return emptyList()
        // A day of slack before now: an all-day event's UTC midnights can fall outside a tight window in
        // negative-offset zones, and in-progress events must come back.
        val from = now.minus(1, ChronoUnit.DAYS).toEpochMilli()
        val to = now.plus(days.toLong(), ChronoUnit.DAYS).toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(from.toString()).appendPath(to.toString()).build()
        val z = zone()
        val rows = mutableListOf<T>()
        resolver.query(uri, columns, AgendaMapper.selection(enabled), null, CalendarContract.Instances.BEGIN)
            ?.use { c -> while (c.moveToNext()) rows += map(c, z) }
        return rows
    }

    private fun event(c: Cursor, zone: ZoneId) = AgendaMapper.fromRow(
        eventId = c.getLong(0), title = c.getString(1), beginMs = c.getLong(2), endMs = c.getLong(3),
        allDay = c.getInt(4) != 0, location = c.getString(5), color = c.getInt(6), calendarId = c.getLong(7),
        zone = zone,
    )

    private fun occurrence(c: Cursor, zone: ZoneId) = AgendaMapper.occurrence(
        event = event(c, zone), calendarName = c.getString(8), originalId = if (c.isNull(9)) null else c.getLong(9),
        rrule = c.getString(10), rdate = c.getString(11), status = c.getInt(12), selfStatus = c.getInt(13),
    )

    override fun calendars(): List<CalendarInfo> = try {
        val out = mutableListOf<CalendarInfo>()
        resolver.query(CalendarContract.Calendars.CONTENT_URI, CALENDAR_COLUMNS, null, null, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            ?.use { c ->
                while (c.moveToNext()) out += CalendarInfo(c.getLong(0), c.getString(1).orEmpty(), c.getInt(2), c.getString(3).orEmpty())
            }
        out
    } catch (e: SecurityException) {
        emptyList()
    }

    private companion object {
        val INSTANCE_COLUMNS = arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_COLOR, CalendarContract.Instances.CALENDAR_ID,
        )

        // Indices 8 and up are read by occurrence() only.
        val SEARCH_COLUMNS = INSTANCE_COLUMNS + arrayOf(
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME, CalendarContract.Instances.ORIGINAL_ID, CalendarContract.Instances.RRULE,
            CalendarContract.Instances.RDATE, CalendarContract.Instances.STATUS, CalendarContract.Instances.SELF_ATTENDEE_STATUS,
        )
        val CALENDAR_COLUMNS = arrayOf(
            CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR, CalendarContract.Calendars.ACCOUNT_NAME,
        )
    }
}
