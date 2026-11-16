package me.jhot.swiftcurrent.agenda

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

data class AgendaDay(val date: LocalDate, val label: String?, val events: List<AgendaEvent>)

/** Every word the formatter produces; the UI builds it from string resources, JVM tests pass a fake. */
interface AgendaStrings {
    val today: String
    val tomorrow: String
    val noTitle: String
    val now: String
    fun inMinutes(n: Long): String
    fun inHours(n: Long): String
    fun inDays(n: Long): String
    fun nextLine(title: String, whenText: String): String
    fun nextLineMore(title: String, whenText: String, more: Int): String
    fun detail(timeRange: String, relative: String): String
}

/** The Home line, split where Home may cut it: [text] is the whole line, [tail] what follows the title. */
data class AgendaNextLine(val text: String, val tail: String)

class AgendaFormatter(private val strings: AgendaStrings, private val locale: Locale = Locale.getDefault()) {
    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)

    /** Today (even when empty) and every later day with events, grouped by local date; a multi-day event is listed on every day it covers. */
    fun days(events: List<AgendaEvent>, now: Instant, zone: ZoneId): List<AgendaDay> {
        val today = now.atZone(zone).toLocalDate()
        val byDate = sortedMapOf<LocalDate, MutableList<AgendaEvent>>()
        // Today is always offered, so an event can be added when nothing is coming up.
        byDate[today] = mutableListOf()
        for (e in upcoming(events, now)) {
            val span = span(e, today, zone)
            var d = span.start
            while (d <= span.endInclusive) {
                byDate.getOrPut(d) { mutableListOf() } += e
                d = d.plusDays(1)
            }
        }
        return byDate.map { (date, list) ->
            val label = when (date) {
                today -> strings.today
                today.plusDays(1) -> strings.tomorrow
                else -> null
            }
            AgendaDay(date, label, list)
        }
    }

    /** From the INSTANT difference, so a DST change between now and [start] counts its real hours. */
    fun relative(start: Instant, now: Instant): String {
        val d = Duration.between(now, start)
        return when {
            d.isNegative || d.isZero -> strings.now
            d.toMinutes() < 1 -> strings.inMinutes(1)
            d.toHours() < 1 -> strings.inMinutes(d.toMinutes())
            d.toHours() < HOURS_PER_DAY -> strings.inHours(d.toHours())
            else -> strings.inDays(d.toDays())
        }
    }

    /** The Home line, or null when nothing is left to show (which hides the line). */
    fun nextLine(events: List<AgendaEvent>, now: Instant, zone: ZoneId): String? =
        nextLineParts(events, now, zone)?.text

    /**
     * The Home line and the part after its title, from one pass. Home keeps [AgendaNextLine.tail]
     * readable and gives way only the title. An empty tail means the locale puts the title
     * somewhere other than the start, so nothing can be cut.
     */
    fun nextLineParts(events: List<AgendaEvent>, now: Instant, zone: ZoneId): AgendaNextLine? {
        val upcoming = upcoming(events, now)
        val next = upcoming.firstOrNull() ?: return null
        val today = now.atZone(zone).toLocalDate()
        val date = maxOf(next.start.atZone(zone).toLocalDate(), today)
        // Counted over the same pass, rather than building every day just to read one.
        val more = upcoming.count { on(date, it, today, zone) } - 1
        val whenText = if (next.allDay) {
            when (date) {
                today -> strings.today
                today.plusDays(1) -> strings.tomorrow
                else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            }
        } else {
            time(next.start, zone)
        }
        val title = title(next)
        val text = if (more > 0) strings.nextLineMore(title, whenText, more) else strings.nextLine(title, whenText)
        return AgendaNextLine(text, if (text.startsWith(title)) text.removePrefix(title) else "")
    }

    /** "11:30 AM\u201312:00 PM" in the locale's short time format, or null for an all-day event. */
    fun timeRange(event: AgendaEvent, zone: ZoneId): String? =
        if (event.allDay) null else "${time(event.start, zone)}\u2013${time(event.end, zone)}"

    /**
     * When an event starts, for a search result: "Today", "Tomorrow" or "Fri, Nov 13" (with the year when it is not
     * this one), then " \u00b7 " and the start time; an all-day event has the date alone.
     */
    fun eventWhen(event: AgendaEvent, now: Instant, zone: ZoneId): String {
        val today = now.atZone(zone).toLocalDate()
        val date = event.start.atZone(zone).toLocalDate()
        val day = when (date) {
            today -> strings.today.replaceFirstChar { it.titlecase(locale) }
            today.plusDays(1) -> strings.tomorrow.replaceFirstChar { it.titlecase(locale) }
            else -> DateTimeFormatter.ofPattern(if (date.year == today.year) "EEE, MMM d" else "EEE, MMM d, yyyy", locale).format(date)
        }
        return if (event.allDay) day else "$day \u00b7 ${time(event.start, zone)}"
    }

    fun title(event: AgendaEvent): String = event.title?.takeIf { it.isNotBlank() } ?: strings.noTitle

    fun detail(event: AgendaEvent, now: Instant, zone: ZoneId): String? =
        timeRange(event, zone)?.let { strings.detail(it, relative(event.start, now)) }

    // Events that have not ended, soonest first; all-day before timed on the same start.
    private fun upcoming(events: List<AgendaEvent>, now: Instant) =
        events.filter { it.end.isAfter(now) }.sortedWith(compareBy<AgendaEvent> { it.start }.thenBy { !it.allDay }.thenBy { it.title })

    /** The dates [e] is listed on: from its first day (not before [today]) for as many days as it covers, at most [MAX_SPAN_DAYS] more. */
    private fun span(e: AgendaEvent, today: LocalDate, zone: ZoneId): ClosedRange<LocalDate> {
        val first = maxOf(e.start.atZone(zone).toLocalDate(), today)
        return first..minOf(lastDate(e, zone), first.plusDays(MAX_SPAN_DAYS))
    }

    /** Whether [e] is listed on [date]; the one rule [days] and the Home line share. */
    private fun on(date: LocalDate, e: AgendaEvent, today: LocalDate, zone: ZoneId): Boolean = date in span(e, today, zone)

    // END is exclusive: an event ending at local midnight does not touch the next date.
    private fun lastDate(e: AgendaEvent, zone: ZoneId): LocalDate {
        val end = e.end.atZone(zone)
        return if (end.toLocalTime() == LocalTime.MIDNIGHT && e.end.isAfter(e.start)) end.toLocalDate().minusDays(1) else end.toLocalDate()
    }

    // Newer ICU puts a narrow no-break space before AM/PM; a plain space reads the same and compares.
    private fun time(at: Instant, zone: ZoneId) =
        timeFormat.format(at.atZone(zone)).replace('\u202f', ' ').replace('\u00a0', ' ')

    private companion object {
        const val HOURS_PER_DAY = 24
        const val MAX_SPAN_DAYS = 31L
    }
}
