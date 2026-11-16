package me.jhot.swiftcurrent.search

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeSource(
    override val name: String,
    private val delayMs: Long = 0,
    private val labels: List<String> = emptyList(),
    private val fail: Boolean = false,
    private val onCall: () -> Unit = {},
) : SearchSource {
    override suspend fun search(query: String): List<SearchResult> {
        onCall()
        delay(delayMs)
        if (fail) error("boom")
        return labels.map { result(it, source = name) }
    }
}

/** Resolves the ids it [owns], in its own order (not the order asked), the way a real source answers. */
private class Resolver(
    override val name: String,
    private val owns: List<String>,
    private val delayMs: Long = 0,
    private val fail: Boolean = false,
    private val hang: Boolean = false,
) : SearchSource {
    var resolves = 0
    var searches = 0
    override suspend fun search(query: String): List<SearchResult> {
        searches++
        return emptyList()
    }

    override suspend fun resolve(ids: Set<String>): List<SearchResult> {
        resolves++
        if (hang) awaitCancellation()
        delay(delayMs)
        if (fail) error("boom")
        return owns.filter { it in ids }.map { recent(it, name) }
    }
}

private fun recent(id: String, source: String = "test") =
    SearchResult(id, "Label $id", source, Kind.App, null, Action(id, null) { LaunchOutcome.Launched })

/** A contact with one own action and a shortcut named after it, plus a recents row for that action. */
private class ContactWithShortcut : SearchSource {
    override val name = "Contacts"
    private val act = Action("Messages", null) { LaunchOutcome.Launched }
    override suspend fun search(query: String) = listOf(
        SearchResult(
            "contact:1", "Alex Example", name, Kind.Contact, null, Action("Alex Example", null) { LaunchOutcome.Launched },
            secondary = listOf(Action("Call", null, recentId = "contact:1|call|+15550100") { LaunchOutcome.Launched }),
        ),
        SearchResult(
            "shortcut:k", "Messages: Alex Example", "Shortcuts", Kind.Shortcut, null, act,
            appLabel = "Messages", entryLabel = "Alex Example",
        ),
    )

    override suspend fun resolve(ids: Set<String>) = ids.filter { it == "contact:1|call|+15550100" }.map {
        SearchResult(it, "Call: Alex Example", name, Kind.Contact, null, Action("Call", null, recentId = it) { LaunchOutcome.Launched }, ownerId = "contact:1")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SearchEngineTest {
    @Test
    fun a_hidden_folded_shortcut_leaves_the_contact_and_does_not_come_back_as_its_own_row() = runTest {
        val shown = SearchEngine(listOf(ContactWithShortcut())).results("alex").toList().last()
        assertEquals(listOf("Call", "Messages"), shown.single().secondary.map { it.label })
        val hidden = setOf(ContactHiding.key("contact:1", "shortcut:k"))
        val out = SearchEngine(listOf(ContactWithShortcut()), hidden = { hidden }).results("alex").toList().last()
        assertEquals(listOf("Call"), out.single().secondary.map { it.label })
    }

    @Test
    fun a_hidden_contact_method_is_left_out_of_recents() = runTest {
        val id = "contact:1|call|+15550100"
        val shown = SearchEngine(listOf(ContactWithShortcut()), recents = { listOf(id) }).results("").toList().last()
        assertEquals(listOf(id), shown.map { it.id })
        val hidden = setOf(ContactHiding.key("contact:1", id))
        val out = SearchEngine(listOf(ContactWithShortcut()), recents = { listOf(id) }, hidden = { hidden }).results("").toList().last()
        assertEquals(emptyList<String>(), out.map { it.id })
    }

    @Test
    fun sources_run_on_the_compute_dispatcher_when_given_one() = runBlocking {
        val caller = Thread.currentThread()
        var ran: Thread? = null
        val source = object : SearchSource {
            override val name = "t"
            override suspend fun search(query: String): List<SearchResult> {
                ran = Thread.currentThread()
                return listOf(result("Maps", source = name))
            }
        }
        val pool = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "compute-test") }
        try {
            SearchEngine(listOf(source), compute = pool.asCoroutineDispatcher()).results("map").toList()
            assertEquals("compute-test", ran?.name)
            assertNotEquals(caller, ran)
        } finally {
            pool.shutdown()
        }
    }

    @Test
    fun blank_or_whitespace_query_returns_nothing() = runTest {
        var calls = 0
        val engine = SearchEngine(listOf(FakeSource("a", labels = listOf("Maps"), onCall = { calls++ })))
        assertEquals(listOf(emptyList<SearchResult>()), engine.results("  ").toList())
        assertEquals(0, calls)
    }

    @Test
    fun slow_source_times_out_and_others_still_arrive() = runTest {
        val engine = SearchEngine(
            listOf(FakeSource("slow", delayMs = 10_000, labels = listOf("Maps slow")), FakeSource("fast", labels = listOf("Maps"))),
            timeoutMs = 1000,
        )
        val emissions = engine.results("map").toList()
        assertEquals(listOf("Maps"), emissions.last().map { it.label })
        assertEquals(1000L, testScheduler.currentTime)
    }

    @Test
    fun sources_that_answer_within_the_settle_time_produce_one_list() = runTest {
        val engine = SearchEngine(
            listOf(FakeSource("a", delayMs = 10, labels = listOf("Maps")), FakeSource("b", delayMs = 40, labels = listOf("Maps two"))),
            settleMs = 100,
        )
        val emissions = engine.results("map").toList()
        assertEquals(listOf(listOf("Maps", "Maps two")), emissions.map { l -> l.map { it.label } })
    }

    @Test
    fun a_query_nothing_matches_still_clears_the_list() = runTest {
        val engine = SearchEngine(listOf(FakeSource("a", labels = emptyList()), FakeSource("b", delayMs = 30, labels = emptyList())), settleMs = 100)
        assertEquals(listOf(emptyList<SearchResult>()), engine.results("zzz").toList())
    }

    @Test
    fun throwing_source_is_dropped() = runTest {
        val engine = SearchEngine(listOf(FakeSource("bad", fail = true), FakeSource("ok", labels = listOf("Maps"))))
        assertEquals(listOf("Maps"), engine.results("map").toList().last().map { it.label })
    }

    @Test
    fun partial_results_emit_before_slow_source_finishes() = runTest {
        val engine = SearchEngine(
            listOf(FakeSource("slow", delayMs = 800, labels = listOf("Maps slow")), FakeSource("fast", labels = listOf("Maps"))),
            timeoutMs = 1000,
            settleMs = 100,
        )
        val seen = mutableListOf<List<String>>()
        val job = launch { engine.results("map").collect { seen += it.map { r -> r.label } } }
        runCurrent()
        // Held for the settle time, so a slow source does not make the list change shape a moment after it shows.
        assertEquals(emptyList<List<String>>(), seen)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(listOf(listOf("Maps")), seen)
        advanceTimeBy(900)
        runCurrent()
        assertEquals(listOf("Maps", "Maps slow"), seen.last())
        job.join()
    }

    @Test
    fun options_are_read_once_per_query() = runTest {
        var reads = 0
        val engine = SearchEngine(
            listOf(
                FakeSource("fast", labels = listOf("Maps")),
                FakeSource("later", delayMs = 50, labels = listOf("Mail")),
                FakeSource("slow", delayMs = 400, labels = listOf("Mark")),
            ),
            settleMs = 100,
            options = { reads++; RankingOptions() },
        )
        val emissions = engine.results("ma").toList()
        // Two lists, both ranked with the one value: the first row never moves because a setting was re-read.
        assertEquals(2, emissions.size)
        assertEquals(1, reads)
    }

    @Test
    fun options_reach_ranking() = runTest {
        val shortcut = result("Mail: inbox", Kind.Shortcut)
        val contact = result("Mary", Kind.Contact)
        val source = object : SearchSource {
            override val name = "fake"
            override suspend fun search(query: String) = listOf(shortcut, contact)
        }
        val byDefault = SearchEngine(listOf(source), options = { RankingOptions(contactsFirst = false) })
        assertEquals(listOf("Mail: inbox", "Mary"), byDefault.results("ma").toList().last().map { it.label })
        val contactsFirst = SearchEngine(listOf(source), options = { RankingOptions(contactsFirst = true) })
        assertEquals(listOf("Mary", "Mail: inbox"), contactsFirst.results("ma").toList().last().map { it.label })
    }

    private fun selfCancelling(name: String, thrower: suspend () -> Nothing) = object : SearchSource {
        override val name = name
        override suspend fun search(query: String): List<SearchResult> = thrower()
    }

    private suspend fun collectWithin(engine: SearchEngine) =
        withTimeout(5_000) { engine.results("map").toList() }

    @Test
    fun source_that_throws_CancellationException_is_dropped_and_flow_completes() = runTest {
        val engine = SearchEngine(
            listOf(selfCancelling("bad") { throw CancellationException("self") }, FakeSource("ok", labels = listOf("Maps"))),
        )
        assertEquals(listOf("Maps"), collectWithin(engine).last().map { it.label })
    }

    @Test
    fun source_that_throws_TimeoutCancellationException_is_dropped() = runTest {
        val engine = SearchEngine(
            listOf(
                selfCancelling("bad") { withTimeout(1) { awaitCancellation() } },
                FakeSource("ok", labels = listOf("Maps")),
            ),
        )
        assertEquals(listOf("Maps"), collectWithin(engine).last().map { it.label })
    }

    @Test
    fun cancelling_collection_cancels_sources() = runTest {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val hanging = object : SearchSource {
            override val name = "hang"
            override suspend fun search(query: String): List<SearchResult> {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
        }
        val job = launch { SearchEngine(listOf(hanging), timeoutMs = Long.MAX_VALUE / 2).results("x").toList() }
        started.await()
        job.cancel()
        runCurrent()
        // The timeout is effectively infinite, so only propagated cancellation can end the source.
        assertTrue(cancelled.isCompleted)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun concurrent_sources_on_a_multithreaded_dispatcher_lose_nothing() = runBlocking {
        repeat(50) {
            val sources = (1..12).map { i -> FakeSource("s$i", delayMs = 1, labels = listOf("Map $i")) }
            val last = withContext(Dispatchers.Default) {
                SearchEngine(sources).results("map").toList().last()
            }
            assertEquals(12, last.size)
        }
    }

    private fun ids(list: List<SearchResult>) = list.map { it.id }

    @Test
    fun empty_query_resolves_recents_in_order_up_to_eight() = runTest {
        val recents = (1..10).map { "r$it" }
        // Each source answers in its own order; the list follows the recents order regardless.
        val odd = Resolver("odd", recents.filterIndexed { i, _ -> i % 2 == 0 }.reversed())
        val even = Resolver("even", recents.filterIndexed { i, _ -> i % 2 == 1 }, delayMs = 30)
        val engine = SearchEngine(listOf(odd, even), recents = { recents })
        val emissions = engine.results("").toList()
        assertEquals(listOf((1..8).map { "r$it" }), emissions.map(::ids))
    }

    @Test
    fun unresolved_ids_are_skipped_and_older_ones_fill_the_list() = runTest {
        val recents = (1..12).map { "r$it" }
        // An uninstalled app, a deleted contact and a removed shortcut: nobody resolves them.
        val gone = setOf("r2", "r5", "r7")
        val engine = SearchEngine(listOf(Resolver("all", recents - gone)), recents = { recents })
        assertEquals(listOf("r1", "r3", "r4", "r6", "r8", "r9", "r10", "r11"), ids(engine.results("").toList().single()))
    }

    @Test
    fun a_slow_source_costs_only_its_own_entries() = runTest {
        val engine = SearchEngine(
            listOf(
                Resolver("slow", listOf("r1"), delayMs = 1000),
                Resolver("bad", listOf("r2"), fail = true),
                Resolver("fast", listOf("r3", "r4")),
            ),
            timeoutMs = 1500,
            recents = { listOf("r1", "r2", "r3", "r4") },
        )
        // One list, never grown by the late answer.
        assertEquals(listOf(listOf("r3", "r4")), engine.results("").toList().map(::ids))
    }

    @Test
    fun a_hung_source_costs_only_the_recents_budget() = runTest {
        val engine = SearchEngine(
            listOf(Resolver("hung", listOf("r1"), hang = true), Resolver("ok", listOf("r2"))),
            timeoutMs = Long.MAX_VALUE / 2,
            recents = { listOf("r1", "r2") },
            recentsSettleMs = 300,
        )
        var shownAt = -1L
        val first = engine.results("").first().also { shownAt = testScheduler.currentTime }
        assertEquals(listOf("r2"), ids(first))
        assertEquals(300L, shownAt)
    }

    @Test
    fun a_source_answering_inside_the_budget_keeps_its_entries() = runTest {
        val engine = SearchEngine(
            listOf(Resolver("contacts", listOf("r1"), delayMs = 200), Resolver("apps", listOf("r2"))),
            recents = { listOf("r1", "r2") },
            recentsSettleMs = 300,
        )
        val emissions = engine.results("").toList()
        assertEquals(listOf(listOf("r1", "r2")), emissions.map(::ids))
        // Everyone answered, so the list did not wait out the budget.
        assertEquals(200L, testScheduler.currentTime)
    }

    @Test
    fun a_source_answering_at_four_hundred_ms_keeps_its_entries() = runTest {
        // A slow contacts provider took about 400 ms on the emulator; the default budget must still wait for it.
        val engine = SearchEngine(
            listOf(Resolver("contacts", listOf("r1"), delayMs = 400), Resolver("apps", listOf("r2"))),
            recents = { listOf("r1", "r2") },
        )
        assertEquals(listOf(listOf("r1", "r2")), engine.results("").toList().map(::ids))
        assertEquals(400L, testScheduler.currentTime)
    }

    @Test
    fun recents_resolve_once_per_open_not_per_empty_query() = runTest {
        val sources = listOf(Resolver("a", listOf("r1")), Resolver("b", listOf("r2"), delayMs = 20))
        val engine = SearchEngine(sources, recents = { listOf("r1", "r2") })
        val first = engine.results("").toList()
        engine.results("a").toList()
        val again = engine.results("").toList()
        assertEquals(sources.size, sources.sumOf { it.resolves })
        assertEquals(first, again)
        assertEquals(listOf(listOf("r1", "r2")), again.map(::ids))
    }

    @Test
    fun no_recents_asks_no_source() = runTest {
        val source = Resolver("a", listOf("r1"))
        val engine = SearchEngine(listOf(source), recents = { emptyList() })
        assertEquals(listOf(emptyList<SearchResult>()), engine.results("").toList())
        assertEquals(0, source.resolves)
        assertEquals(0, source.searches)
    }

    @Test
    fun a_typed_query_never_resolves_recents() = runTest {
        var reads = 0
        val source = Resolver("a", listOf("r1"))
        val engine = SearchEngine(listOf(source), recents = { reads++; listOf("r1") })
        engine.results("r").toList()
        assertEquals(0, source.resolves)
        assertEquals(0, reads)
    }

    @Test
    fun whitespace_only_query_emits_nothing() = runTest {
        val source = Resolver("a", listOf("r1"))
        val engine = SearchEngine(listOf(source), recents = { listOf("r1") })
        assertEquals(listOf(emptyList<SearchResult>()), engine.results("  ").toList())
        assertEquals(0, source.resolves)
    }
}
