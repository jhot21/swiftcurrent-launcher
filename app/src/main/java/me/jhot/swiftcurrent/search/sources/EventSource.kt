package me.jhot.swiftcurrent.search.sources

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.jhot.swiftcurrent.agenda.AgendaEvent
import me.jhot.swiftcurrent.agenda.AgendaFormatter
import me.jhot.swiftcurrent.agenda.CalendarRepository
import me.jhot.swiftcurrent.agenda.EventOccurrence
import me.jhot.swiftcurrent.agenda.EventOccurrences
import me.jhot.swiftcurrent.agenda.LocationIntentSpec
import me.jhot.swiftcurrent.agenda.LocationIntents
import me.jhot.swiftcurrent.search.Action
import me.jhot.swiftcurrent.search.Kind
import me.jhot.swiftcurrent.search.LaunchOutcome
import me.jhot.swiftcurrent.search.Ranking
import me.jhot.swiftcurrent.search.SearchResult
import me.jhot.swiftcurrent.search.SearchSource
import java.time.Instant
import java.time.ZoneId

/** The two things an event result can start; the shell turns them into intents, so this source stays plain JVM. */
interface EventActions {
    /** Opens [event] (the shown occurrence) in the calendar app. */
    fun open(event: AgendaEvent): LaunchOutcome

    /** Starts one way of reaching an event's location. */
    fun launch(spec: LocationIntentSpec): LaunchOutcome
}

private const val WINDOW_DAYS = 365
private const val MAX_EVENTS = 5

/**
 * Searches the ongoing and upcoming events of the calendars search covers. They are read for a year ahead when
 * search opens ([refresh]) and when the provider reports a change ([followChanges]), never per keystroke, since
 * expanding repeating events is the slow part. Matching is on the title only.
 */
class EventSource(
    private val repo: CalendarRepository,
    private val granted: () -> Boolean,
    /** The calendars to search; null means every calendar. Read per query, so a switch applies at once. */
    private val enabled: () -> Set<Long>?,
    private val now: () -> Instant,
    private val zone: () -> ZoneId,
    private val formatter: AgendaFormatter,
    private val actions: EventActions,
    private val io: CoroutineDispatcher,
) : SearchSource {
    override val name = "Events"

    private class Cached(val row: SearchResult, val calendarId: Long, val start: Instant, val end: Instant)

    @Volatile
    private var cache: List<Cached>? = null
    private val firstLoad = Mutex()

    // True from a read a query forced until the next refresh, which that read already answers. Under [firstLoad].
    private var coldLoaded = false

    private suspend fun read(): List<Cached> = withContext(io) {
        if (!granted()) return@withContext emptyList()
        val at = now()
        EventOccurrences.select(repo.occurrences(at, WINDOW_DAYS, enabled()), at).mapNotNull { row(it, at) }
    }

    /**
     * Search opening reads the events again. A failure leaves the previous events in place and is thrown to the
     * caller. When recents already forced a cold read for this open, that read is the one: it is not repeated.
     */
    suspend fun refresh() {
        // Under the first-load lock so a query that arrives mid-refresh waits for it instead of reading twice.
        firstLoad.withLock {
            if (coldLoaded) coldLoaded = false else cache = read()
        }
    }

    /** A read for a calendar change, which no earlier read answers. */
    private suspend fun reread() {
        firstLoad.withLock {
            coldLoaded = false
            cache = read()
        }
    }

    /** Keeps the events current for as long as the caller stays in it; a failed refresh keeps the old ones. */
    suspend fun followChanges() {
        // Conflated: a sync that notifies in a burst costs the reads it takes to catch up, not one per notification.
        repo.changes.conflate().collect {
            try {
                reread()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The old events stay; the next change or the next open reads them again.
            }
        }
    }

    override suspend fun search(query: String): List<SearchResult> {
        val q = Ranking.normalize(query).trim()
        if (q.isEmpty()) return emptyList()
        return current()
            .mapNotNull { c -> Ranking.matchQualityFolded(q, c.row.foldedLabel)?.let { c to Ranking.eventTier(it) } }
            // Soonest first within a tier, the same order Ranking gives, so the five kept are the five it would put first.
            .sortedWith(compareBy({ it.second }, { it.first.start }))
            .take(MAX_EVENTS)
            .map { it.first.row }
    }

    // Awaits a cold cache like search does: refresh() runs on every open, and recents must not miss it.
    override suspend fun resolve(ids: Set<String>): List<SearchResult> =
        current().filter { it.row.id in ids }.map { it.row }

    private suspend fun current(): List<Cached> {
        val all = cache ?: firstLoad.withLock { cache ?: read().also { cache = it; coldLoaded = true } }
        val calendars = enabled()
        val at = now()
        return all.filter { (calendars == null || it.calendarId in calendars) && it.end.isAfter(at) }
    }

    private fun row(o: EventOccurrence, at: Instant): Cached? {
        val title = o.event.title?.trim().orEmpty()
        if (title.isEmpty()) return null
        val z = zone()
        val whenText = formatter.eventWhen(o.event, at, z)
        val calendar = o.calendarName.takeIf { it.isNotBlank() }
        val location = o.event.location?.trim()?.replace(WHITESPACE, " ")?.takeIf { it.isNotEmpty() }
        val result = SearchResult(
            id = "event:${o.seriesId}",
            label = title,
            sourceName = name,
            kind = Kind.Event,
            icon = null,
            primary = Action(title, null) { actions.open(o.event) },
            secondary = if (location == null) emptyList() else listOf(Action(location, null) { LocationIntents.perform(LocationIntents.specs(location), actions::launch) }),
            prefix = calendar,
            detail = whenText,
            recurring = o.recurring,
            startMillis = o.event.start.toEpochMilli(),
        )
        return Cached(result, o.event.calendarId, o.event.start, o.event.end)
    }

    private companion object {
        val WHITESPACE = Regex("""\s+""")
    }
}
