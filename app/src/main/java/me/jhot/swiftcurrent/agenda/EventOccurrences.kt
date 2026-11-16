package me.jhot.swiftcurrent.agenda

import java.time.Instant

/**
 * One occurrence of an event as search sees it. [seriesId] is the id a repeating series answers to: an occurrence
 * that was edited on its own has its own event id, but its original's id is the series.
 */
data class EventOccurrence(
    val event: AgendaEvent,
    val calendarName: String,
    val seriesId: Long,
    val recurring: Boolean,
    val declined: Boolean,
    val cancelled: Boolean,
)

object EventOccurrences {
    /**
     * What search offers: occurrences that are not declined, cancelled or over, one per series (its ongoing
     * occurrence, else its next), soonest first. Declined ones go before the choice, so a declined instance
     * lets the next accepted one stand for its series.
     */
    fun select(rows: List<EventOccurrence>, now: Instant): List<EventOccurrence> =
        rows.asSequence()
            .filter { !it.declined && !it.cancelled && it.event.end.isAfter(now) }
            .groupBy { it.seriesId }
            .values
            .map { series -> series.minBy { it.event.start } }
            .sortedBy { it.event.start }
}
