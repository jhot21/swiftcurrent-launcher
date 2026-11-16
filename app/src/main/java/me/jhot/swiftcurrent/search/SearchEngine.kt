package me.jhot.swiftcurrent.search

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

open class SearchEngine(
    private val sources: List<SearchSource>,
    private val timeoutMs: Long = 1500,
    private val settleMs: Long = 100,
    private val options: () -> RankingOptions = { RankingOptions() },
    private val recents: () -> List<String> = { emptyList() },
    // Recents ids picked as an action inside another result, to that result: tapping one credits the owner.
    private val recentOwners: () -> Map<String, String> = { emptyMap() },
    // Contacts took about 200 ms to resolve on a warm emulator and about 400 ms in a slow session, so a tighter
    // budget dropped them. Every source answering ends the wait early, so this only costs time when one is stuck.
    private val recentsSettleMs: Long = 750,
    private val defaultApps: () -> DefaultApps = { DefaultApps() },
    /** [ContactHiding] keys of the contact methods the owner hid; read on each list, so a hide shows at once. */
    private val hidden: () -> Set<String> = { emptySet() },
    /**
     * Where matching and ranking run, so a keystroke never does that work on the main thread the list is drawn
     * on. Null keeps the caller's dispatcher, which is what the virtual-time tests rely on.
     */
    private val compute: CoroutineDispatcher? = null,
) {
    // The recents answer for this engine's life (one search open), so clearing the field again asks no source.
    // Collections overlap (a keystroke cancels one while the next starts), so the answer is read and kept under a
    // lock; a resolve cut short by a keystroke keeps nothing, and the next empty query asks again.
    private val recentsLock = Mutex()
    private var resolvedRecents: List<SearchResult>? = null

    /**
     * Emits a growing merged, ranked list as each source answers. A source that times out
     * or throws is dropped; it never holds up the others.
     *
     * The first list waits until every source has answered or [settleMs] has passed, whichever is
     * first. Sources answer at different speeds, and showing each answer the moment it arrives made the
     * top row change shape (a contact gaining its app shortcuts) a moment after it appeared. Anything
     * slower than [settleMs] still streams in afterward, as before.
     *
     * [options] is read once per query and used for every list it emits, so a learned score that
     * changes while the owner types never reorders a list already shown.
     *
     * Nothing typed gives the owner's recent picks instead (see [recentRows]), as one list.
     */
    open fun results(query: String): Flow<List<SearchResult>> = channelFlow {
        if (query.isEmpty()) {
            val ids = recents()
            val rows = if (ids.isEmpty()) emptyList() else recentsLock.withLock { resolvedRecents ?: recentRows(ids).also { resolvedRecents = it } }
            // The rows are kept for this engine's life, so a way hidden meanwhile is dropped when they are sent.
            val hiding = hidden()
            // A recents row for one way to reach a contact is known by the contact it belongs to and its own id.
            send(rows.filterNot { r -> r.ownerId != null && ContactHiding.key(r.ownerId, r.id) in hiding })
            return@channelFlow
        }
        if (query.isBlank()) {
            send(emptyList())
            return@channelFlow
        }
        val ranking = options()
        val defaults = defaultApps()
        // Children only send their answer here; the list is accumulated in this body alone,
        // so no state is shared between coroutines whatever dispatcher the caller collects on.
        val answers = Channel<List<SearchResult>?>(Channel.UNLIMITED)
        for (source in sources) {
            launch {
                answers.send(withTimeoutOrNull(timeoutMs) { answerOrNull { source.search(query) } })
            }
        }
        val merged = mutableListOf<SearchResult>()
        var received = 0
        fun absorb(found: List<SearchResult>?) {
            received++
            if (found != null) merged += found
        }
        fun ranked() = ContactHiding.apply(
            ContactFolding.foldIntoTop(Ranking.order(query, ContactFolding.mark(merged.toList()), ranking), defaults),
            hidden(),
        )

        // Hold the first paint, unless the settle time is zero.
        if (settleMs > 0) {
            withTimeoutOrNull(settleMs) { while (received < sources.size) absorb(answers.receive()) }
            // Nothing yet means nothing to show, so the previous list stays rather than flashing empty.
            if (merged.isNotEmpty() || received == sources.size) send(ranked())
        }
        while (received < sources.size) {
            absorb(answers.receive())
            if (merged.isNotEmpty() || received == sources.size) send(ranked())
        }
    }.let { flow -> if (compute == null) flow else flow.flowOn(compute) }

    /**
     * The rows for [ids], newest first: every source asked at once, and whatever has answered after
     * [recentsSettleMs] is the list. A later answer is dropped rather than added, so the list never grows or
     * reorders once shown, and ids that resolved nowhere let older ones fill the [RECENTS_SHOWN] rows. Not ranked
     * and not folded: the order is the order they were picked in, and every row is a plain tap target.
     *
     * The sources run as children of [this] so that one stuck in an uninterruptible call delays only the end of
     * the flow, not the list.
     */
    private suspend fun CoroutineScope.recentRows(ids: List<String>): List<SearchResult> {
        val wanted = ids.toCollection(LinkedHashSet())
        val answers = Channel<Pair<Int, List<SearchResult>?>>(Channel.UNLIMITED)
        val asks = sources.mapIndexed { index, source -> launch { answers.send(index to answerOrNull { source.resolve(wanted) }) } }
        val bySource = arrayOfNulls<List<SearchResult>>(sources.size)
        var received = 0
        withTimeoutOrNull(recentsSettleMs) {
            while (received < sources.size) {
                val (index, found) = answers.receive()
                bySource[index] = found
                received++
            }
        }
        asks.forEach { it.cancel() }
        // Source order, not answer order, settles an id two sources both claim, so the same open shows the same row.
        val byId = HashMap<String, SearchResult>()
        bySource.forEach { found -> found?.forEach { byId.putIfAbsent(it.id, it) } }
        val owners = recentOwners()
        return wanted.asSequence().mapNotNull { byId[it] }.take(RECENTS_SHOWN)
            .map { it.copy(secondary = emptyList(), ownerId = it.ownerId ?: owners[it.id]) }.toList()
    }

    private suspend fun answerOrNull(ask: suspend () -> List<SearchResult>): List<SearchResult>? =
        try {
            ask()
        } catch (e: CancellationException) {
            // Only real cancellation of this coroutine propagates. A source that
            // cancelled itself (e.g. an inner withTimeout) is just dropped, or the
            // body would wait forever for its answer.
            if (!currentCoroutineContext().isActive) throw e
            null
        } catch (e: Exception) {
            null
        }

    private companion object {
        const val RECENTS_SHOWN = 8
    }
}
