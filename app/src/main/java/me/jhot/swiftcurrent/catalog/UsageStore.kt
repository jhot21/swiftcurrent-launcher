package me.jhot.swiftcurrent.catalog

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.jhot.swiftcurrent.core.LegacyCounts
import me.jhot.swiftcurrent.core.db.PickSource
import me.jhot.swiftcurrent.core.db.SelectionRow
import me.jhot.swiftcurrent.core.db.UsageDao
import me.jhot.swiftcurrent.search.Kind
import me.jhot.swiftcurrent.search.LearningWeights
import me.jhot.swiftcurrent.search.Pick
import me.jhot.swiftcurrent.search.PickIndex
import me.jhot.swiftcurrent.search.Ranking

/** Launch counts and search picks kept by the launcher itself, so no usage-access permission is needed. */
interface UsageStore {
    /** A launch outside search. [learn] also keeps it as a pick with no query. */
    fun record(component: String, learn: Boolean)

    /**
     * A result picked in search, with the query as typed; only called while learning is on.
     * [actionId] names the specific action picked inside the result, if any.
     */
    fun recordPick(resultId: String, kind: Kind, query: String, actionId: String? = null)

    fun counts(): Map<String, Int>

    fun picks(): PickIndex

    /** Up to [RoomUsageStore.RECENTS] ids picked in search (the action's id when one was picked), newest first, each once. */
    fun recents(): List<String>

    /**
     * For a [recents] id picked as an action inside another result (an app shortcut folded onto a contact),
     * the id of that result: what ranking should credit when the entry is tapped again. Ids with no
     * separate owner are absent.
     */
    fun recentOwners(): Map<String, String> = emptyMap()
}

/**
 * Serves counts and picks from memory, so no read ever waits on the database. Each record updates
 * memory at once and writes on [scope], one writer at a time. [hydrate] loads what is stored; a
 * failure there or in a write goes to [onError] and leaves memory as it was, so a broken database
 * costs the learned order and nothing else.
 */
class RoomUsageStore(
    private val dao: UsageDao,
    private val legacy: LegacyCounts,
    private val now: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val onError: (Throwable) -> Unit = { Log.w("RoomUsageStore", "usage write failed", it) },
) : UsageStore {
    /** One record made in memory whose write has not reached the database yet. Compared by identity. */
    private class Unwritten(val component: String?, val pick: Pick?, val recent: String? = null, val owner: String? = null)

    private val lock = Any()

    @Volatile private var countsNow: Map<String, Int> = emptyMap()

    @Volatile private var picksNow: PickIndex = PickIndex.EMPTY

    @Volatile private var recentsNow: List<String> = emptyList()

    @Volatile private var ownersNow: Map<String, String> = emptyMap()

    // Guarded by [lock]. A loaded state lacks exactly these, because every write and every load
    // holds [writer]; adding them back is how a launch made while loading is kept, and kept once.
    private val unwritten = ArrayList<Unwritten>()

    private val writer = Mutex()

    override fun record(component: String, learn: Boolean) {
        val pick = if (learn) Pick("app:$component", null, now()) else null
        write(Unwritten(component, pick), pick?.let { row(it, Kind.App, PickSource.LAUNCHER) })
    }

    override fun recordPick(resultId: String, kind: Kind, query: String, actionId: String?) {
        // An empty stored query would count as related to every query, so it is kept as "no query" instead.
        val pick = Pick(resultId, Ranking.normalize(query).trim().ifEmpty { null }, now())
        val component = if (kind == Kind.App) resultId.removePrefix("app:") else null
        val owner = resultId.takeIf { actionId != null && actionId != resultId }
        write(Unwritten(component, pick, actionId ?: resultId, owner), row(pick, kind, PickSource.SEARCH, actionId))
    }

    override fun counts(): Map<String, Int> = countsNow

    override fun picks(): PickIndex = picksNow

    override fun recents(): List<String> = recentsNow

    override fun recentOwners(): Map<String, String> = ownersNow

    /**
     * Moves counts left in DataStore into the database, prunes old picks and loads both into
     * memory. Runs on the caller's dispatcher. Records made meanwhile are kept.
     */
    suspend fun hydrate() = guarded {
        writer.withLock {
            val old = legacy.read()
            if (old != null) {
                // Copied only into an empty table: a copy that landed before the process died is not added twice.
                if (old.isNotEmpty() && dao.countRows() == 0) dao.replaceCounts(old)
                legacy.drop()
            }
            val since = now() - LearningWeights.KEEP_MS
            dao.pruneBefore(since)
            val counts = dao.counts().associate { it.component to it.count }
            val rows = dao.selectionsSince(since)
            val picks = PickIndex.of(rows.map { Pick(it.resultId, it.query, it.at) })
            val (recents, owners) = recentsOf(rows)
            synchronized(lock) { publish(counts, picks, recents, owners, unwritten) }
        }
    }

    /** Forgets every pick; launch counts stay, since the app list orders by them. */
    suspend fun clearPicks() = writer.withLock {
        dao.clearSelections()
        synchronized(lock) {
            publish(countsNow, PickIndex.EMPTY, emptyList(), emptyMap(), unwritten.filter { it.pick != null }.map { Unwritten(null, it.pick, it.recent, it.owner) })
        }
    }

    /** Puts [counts] in place of the stored ones, as a restore does. */
    suspend fun replaceCounts(counts: Map<String, Int>) = writer.withLock {
        dao.replaceCounts(counts)
        synchronized(lock) {
            publish(counts, picksNow, recentsNow, ownersNow, unwritten.filter { it.component != null }.map { Unwritten(it.component, null) })
        }
    }

    suspend fun exportPicks(): List<SelectionRow> = dao.allSelections()

    // Called under [lock]: [pending] land after this state, so they are applied on top of it.
    private fun publish(counts: Map<String, Int>, picks: PickIndex, recents: List<String>, owners: Map<String, String>, pending: List<Unwritten>) {
        var c = counts
        var p = picks
        var r = recents
        var o = owners
        for (u in pending) {
            if (u.component != null) c = c + (u.component to (c[u.component] ?: 0) + 1)
            if (u.pick != null) p = p + u.pick
            // Dropping the old entry first is what keeps a pick made while loading in the list once.
            if (u.recent != null) {
                r = (listOf(u.recent) + r.filter { it != u.recent }).take(RECENTS)
                // The newest pick says who owns the entry, so a later plain pick of it clears the owner.
                o = if (u.owner != null) o + (u.recent to u.owner) else o - u.recent
            }
        }
        o = o.filterKeys { it in r }
        countsNow = c
        picksNow = p
        recentsNow = r
        ownersNow = o
    }

    private fun write(u: Unwritten, row: SelectionRow?) {
        synchronized(lock) {
            unwritten += u
            publish(countsNow, picksNow, recentsNow, ownersNow, listOf(u))
        }
        scope.launch {
            guarded {
                writer.withLock {
                    // Whether the write lands or fails, the database now says what it will say on the next load.
                    try {
                        dao.recordLaunch(row, u.component)
                    } finally {
                        synchronized(lock) { unwritten.remove(u) }
                    }
                }
            }
        }
    }

    private fun row(p: Pick, kind: Kind, source: String, actionId: String? = null) =
        SelectionRow(resultId = p.resultId, kind = kind.name, query = p.query, source = source, at = p.at, actionId = actionId)

    // Keeps the newest row per id before sorting, so only distinct ids are sorted, not the whole history.
    // Rows stored in the same millisecond are ordered by row id, which is insertion order.
    private fun recentsOf(rows: List<SelectionRow>): Pair<List<String>, Map<String, String>> {
        val newest = HashMap<String, SelectionRow>()
        for (row in rows) {
            if (row.source != PickSource.SEARCH) continue
            val id = row.actionId ?: row.resultId
            val seen = newest[id]
            if (seen == null || row.at > seen.at || (row.at == seen.at && row.id > seen.id)) newest[id] = row
        }
        val top = newest.entries
            .sortedWith(compareByDescending<Map.Entry<String, SelectionRow>> { it.value.at }.thenByDescending { it.value.id })
            .take(RECENTS)
        val owners = top.filter { it.value.actionId != null && it.value.actionId != it.value.resultId }
            .associate { it.key to it.value.resultId }
        return top.map { it.key } to owners
    }

    companion object {
        const val RECENTS = 40
    }

    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(e)
        }
    }
}
