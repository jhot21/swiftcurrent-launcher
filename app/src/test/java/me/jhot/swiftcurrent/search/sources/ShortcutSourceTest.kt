package me.jhot.swiftcurrent.search.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.jhot.swiftcurrent.search.Kind
import me.jhot.swiftcurrent.search.LaunchOutcome
import me.jhot.swiftcurrent.search.Ranking
import me.jhot.swiftcurrent.shortcuts.ShortcutAccess
import me.jhot.swiftcurrent.shortcuts.ShortcutEntry
import me.jhot.swiftcurrent.shortcuts.ShortcutProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

private const val TASKER = "net.dinglisch.android.taskerm"

private fun tasker(id: String, label: String) =
    ShortcutEntry(key = "$TASKER/$id", appLabel = "Tasker", packageName = TASKER, id = id, label = label)

private class FakeProvider(
    var access: ShortcutAccess = ShortcutAccess.Granted,
    var entries: List<ShortcutEntry> = listOf(
        tasker("Tasker0", "ExitNightMode"),
        tasker("Tasker1", "CreateAlarm"),
        tasker("Tasker2", "Bedtime"),
    ),
    var outcome: LaunchOutcome = LaunchOutcome.Launched,
) : ShortcutProvider {
    val launched = mutableListOf<String>()
    var entryReads = 0
    val threads = mutableListOf<Thread>()
    override fun access(): ShortcutAccess {
        threads += Thread.currentThread()
        return access
    }
    override fun entries(): List<ShortcutEntry> {
        entryReads++
        threads += Thread.currentThread()
        return entries
    }
    override fun launch(key: String): LaunchOutcome {
        launched += key
        return outcome
    }
    val unpinned = mutableListOf<String>()
    override fun unpin(key: String): LaunchOutcome { unpinned += key; return outcome }
    override val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}

class ShortcutSourceTest {
    private fun ranked(provider: ShortcutProvider, query: String) =
        Ranking.order(query, runBlocking { ShortcutSource(provider).search(query) })

    @Test
    fun bed_finds_Tasker_Bedtime_labeled_App_colon_name() {
        val results = ranked(FakeProvider(), "bed")
        assertEquals(listOf("Tasker: Bedtime"), results.map { it.label })
        assertEquals(Kind.Shortcut, results.single().kind)
        assertEquals("Shortcuts", results.single().sourceName)
        // The app name alone also reaches the shortcuts, including with the task name after it.
        assertEquals(3, ranked(FakeProvider(), "tasker").size)
        assertEquals(listOf("Tasker: Bedtime"), ranked(FakeProvider(), "tasker bed").map { it.label })
    }

    @Test
    fun a_row_is_kept_between_keystrokes_with_its_folded_label() {
        val source = ShortcutSource(FakeProvider())
        val first = runBlocking { source.search("bed") }.single()
        val second = runBlocking { source.search("bedt") }.single()
        assertTrue("same row, folded once", first === second)
    }

    @Test
    fun selecting_it_calls_launch_with_its_key() {
        val provider = FakeProvider()
        val result = ranked(provider, "bed").single()
        assertEquals(LaunchOutcome.Launched, result.primary.perform())
        assertEquals(listOf("$TASKER/Tasker2"), provider.launched)
    }

    @Test
    fun launch_failure_surfaces_Failed_message() {
        val provider = FakeProvider(outcome = LaunchOutcome.Failed("Shortcut is gone"))
        assertEquals(LaunchOutcome.Failed("Shortcut is gone"), ranked(provider, "bed").single().primary.perform())
    }

    @Test
    fun blank_labels_never_become_results() {
        val provider = FakeProvider(entries = listOf(tasker("a", "  "), tasker("b", ""), tasker("Tasker2", "Bedtime")))
        assertEquals(listOf("Tasker: Bedtime"), runBlocking { ShortcutSource(provider).search("t") }.map { it.label })
    }

    @Test
    fun not_default_home_returns_no_shortcuts() {
        val provider = FakeProvider(access = ShortcutAccess.NotDefaultHome)
        assertTrue(runBlocking { ShortcutSource(provider).search("bed") }.isEmpty())
    }

    @Test
    fun the_index_is_read_once_and_searching_reads_the_cache() {
        val provider = FakeProvider()
        val source = ShortcutSource(provider)
        runBlocking { source.search("b"); source.search("be"); source.search("bed") }
        assertEquals(1, provider.entryReads)
    }

    @Test
    fun a_shortcut_change_refreshes_the_cache() = runBlocking {
        val provider = FakeProvider(entries = emptyList())
        val source = ShortcutSource(provider)
        assertTrue(source.search("bed").isEmpty())
        val following = launch(Dispatchers.Unconfined) { source.followChanges() }
        provider.entries = listOf(tasker("Tasker2", "Bedtime"))
        // Until the provider says something changed, the cache is what search sees.
        assertTrue(source.search("bed").isEmpty())
        provider.changes.emit(Unit)
        withTimeout(5_000) { while (source.search("bed").isEmpty()) delay(10) }
        following.cancel()
    }

    @Test
    fun an_explicit_refresh_re_reads_the_provider() {
        val provider = FakeProvider(entries = emptyList())
        val source = ShortcutSource(provider)
        assertTrue(runBlocking { source.search("bed") }.isEmpty())
        provider.entries = listOf(tasker("Tasker2", "Bedtime"))
        runBlocking { source.refresh() }
        assertEquals(1, runBlocking { source.search("bed") }.size)
        provider.entries = emptyList()
        runBlocking { source.refresh() }
        assertTrue(runBlocking { source.search("bed") }.isEmpty())
    }

    @Test
    fun search_reads_the_provider_off_the_calling_thread() {
        val executor = Executors.newSingleThreadExecutor { Thread(it, "provider-thread") }
        try {
            val provider = FakeProvider()
            val results = runBlocking { ShortcutSource(provider, executor.asCoroutineDispatcher()).search("bed") }
            assertEquals(1, results.size)
            assertTrue(provider.threads.isNotEmpty())
            assertTrue(provider.threads.all { it.name == "provider-thread" && it !== Thread.currentThread() })
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun resolve_returns_only_known_ids() {
        val provider = FakeProvider()
        val source = ShortcutSource(provider, filter = me.jhot.swiftcurrent.shortcuts.ShortcutFilter { it != "$TASKER/Tasker1" })
        val rows = runBlocking {
            source.refresh()
            source.resolve(setOf("shortcut:$TASKER/Tasker2", "shortcut:$TASKER/Removed", "shortcut:$TASKER/Tasker1", "app:$TASKER/.Main"))
        }
        // A removed shortcut, one switched off and another source's id are absent.
        assertEquals(listOf("shortcut:$TASKER/Tasker2"), rows.map { it.id })
        assertEquals("Tasker: Bedtime", rows.single().label)
        assertEquals(Kind.Shortcut, rows.single().kind)
        assertEquals(LaunchOutcome.Launched, rows.single().primary.perform())
        assertEquals(listOf("$TASKER/Tasker2"), provider.launched)
    }

    @Test
    fun resolve_reads_a_cold_index_instead_of_returning_nothing() {
        // The first open after a process start resolves recents before any refresh finished.
        val provider = FakeProvider()
        val source = ShortcutSource(provider)
        assertEquals(listOf("Tasker: Bedtime"), runBlocking { source.resolve(setOf("shortcut:$TASKER/Tasker2")) }.map { it.label })
        runBlocking { source.resolve(setOf("shortcut:$TASKER/Tasker0")); source.search("bed") }
        assertEquals(1, provider.entryReads)
    }
}
