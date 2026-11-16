package me.jhot.swiftcurrent.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import me.jhot.swiftcurrent.core.LegacyCounts
import me.jhot.swiftcurrent.core.db.LaunchCountRow
import me.jhot.swiftcurrent.core.db.PickSource
import me.jhot.swiftcurrent.core.db.SelectionRow
import me.jhot.swiftcurrent.core.db.SwiftcurrentDatabase
import me.jhot.swiftcurrent.core.db.UsageDao
import me.jhot.swiftcurrent.search.Kind
import me.jhot.swiftcurrent.search.LearningWeights
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val NOW = 1_000_000_000_000L

private class FakeLegacy(@Volatile var counts: Map<String, Int>?) : LegacyCounts {
    var drops = 0
    override suspend fun read() = counts
    override suspend fun drop() {
        drops++
        counts = null
    }
}

/** Passes every call through, so a test can hold one read or make all of them fail. */
private open class WrappedDao(private val inner: UsageDao) : UsageDao() {
    override suspend fun recordLaunch(selection: SelectionRow?, component: String?) = inner.recordLaunch(selection, component)
    override suspend fun selectionsSince(since: Long) = inner.selectionsSince(since)
    override suspend fun allSelections() = inner.allSelections()
    override suspend fun counts() = inner.counts()
    override suspend fun countRows() = inner.countRows()
    override suspend fun pruneBefore(before: Long) = inner.pruneBefore(before)
    override suspend fun clearSelections() = inner.clearSelections()
    override suspend fun replaceCounts(counts: Map<String, Int>) = inner.replaceCounts(counts)
    override suspend fun insert(row: SelectionRow) = error("not delegated")
    override suspend fun insertCounts(rows: List<LaunchCountRow>) = error("not delegated")
    override suspend fun clearCounts() = error("not delegated")
    override suspend fun bump(component: String) = error("not delegated")
}

private class BrokenDao(inner: UsageDao) : WrappedDao(inner) {
    private fun broken(): Nothing = throw android.database.sqlite.SQLiteException("database disk image is malformed")
    override suspend fun recordLaunch(selection: SelectionRow?, component: String?) = broken()
    override suspend fun selectionsSince(since: Long) = broken()
    override suspend fun counts() = broken()
    override suspend fun countRows() = broken()
    override suspend fun pruneBefore(before: Long) = broken()
}

@RunWith(RobolectricTestRunner::class)
class RoomUsageStoreTest {
    private lateinit var db: SwiftcurrentDatabase
    private val dao get() = db.usage()

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), SwiftcurrentDatabase::class.java)
            .build()
    }

    @After
    fun close() = db.close()

    private fun TestScope.store(
        legacy: LegacyCounts = FakeLegacy(null),
        dao: UsageDao = this@RoomUsageStoreTest.dao,
        onError: (Throwable) -> Unit = { throw AssertionError("unexpected", it) },
    ) = RoomUsageStore(dao, legacy, { NOW }, this, onError)

    // Writes hop to Room's threads, so wait on the jobs themselves rather than the test scheduler.
    private suspend fun TestScope.settle() {
        while (true) {
            val running = coroutineContext[Job]!!.children.toList()
            if (running.isEmpty()) return
            running.forEach { it.join() }
        }
    }

    private suspend fun stored() = dao.counts().associate { it.component to it.count }

    @Test
    fun record_counts_in_memory_at_once_and_persists() = runTest {
        val usage = store()
        repeat(15) { usage.record("c/A", learn = true) }
        usage.record("c/B", learn = true)
        assertEquals(mapOf("c/A" to 15, "c/B" to 1), usage.counts())
        // Fifteen launches outside search add up to the threshold at the "any query" weight.
        assertEquals(15 * LearningWeights.ANY, usage.picks().at(NOW).score("app:c/A", "x"), 1e-9)

        settle()
        assertEquals(mapOf("c/A" to 15, "c/B" to 1), stored())
        val rows = dao.allSelections()
        assertEquals(16, rows.size)
        val row = rows.first { it.resultId == "app:c/B" }
        assertEquals(listOf(Kind.App.name, null, PickSource.LAUNCHER, NOW), listOf(row.kind, row.query, row.source, row.at))
    }

    @Test
    fun record_without_learning_writes_no_selection() = runTest {
        val usage = store()
        repeat(15) { usage.record("c/A", learn = false) }
        settle()
        assertEquals(mapOf("c/A" to 15), usage.counts())
        assertEquals(mapOf("c/A" to 15), stored())
        assertEquals(emptyList<SelectionRow>(), dao.allSelections())
        assertEquals(0.0, usage.picks().at(NOW).score("app:c/A", "x"), 0.0)
    }

    @Test
    fun recordPick_of_app_bumps_count_and_index() = runTest {
        val usage = store()
        usage.recordPick("app:c/A", Kind.App, "Máps ")
        usage.recordPick("app:c/A", Kind.App, "maps")
        assertEquals(mapOf("c/A" to 2), usage.counts())
        assertEquals(2 * LearningWeights.EXACT, usage.picks().at(NOW).score("app:c/A", "maps"), 1e-9)

        settle()
        assertEquals(mapOf("c/A" to 2), stored())
        val row = dao.allSelections().first()
        assertEquals(listOf("app:c/A", Kind.App.name, "maps", PickSource.SEARCH), listOf(row.resultId, row.kind, row.query, row.source))
    }

    @Test
    fun recordPick_of_contact_bumps_no_count() = runTest {
        val usage = store()
        usage.recordPick("contact:1", Kind.Contact, "al")
        usage.recordPick("contact:1", Kind.Contact, "al")
        assertEquals(emptyMap<String, Int>(), usage.counts())
        assertEquals(2.0, usage.picks().at(NOW).score("contact:1", "al"), 1e-9)

        settle()
        assertEquals(emptyMap<String, Int>(), stored())
        assertEquals(listOf("contact:1", "contact:1"), dao.allSelections().map { it.resultId })
    }

    @Test
    fun hydrate_migrates_legacy_once_and_drops_it() = runTest {
        val legacy = FakeLegacy(mapOf("c/A" to 4, "c/B" to 1))
        val usage = store(legacy)
        usage.hydrate()
        assertEquals(mapOf("c/A" to 4, "c/B" to 1), usage.counts())
        assertEquals(mapOf("c/A" to 4, "c/B" to 1), stored())
        assertNull(legacy.counts)
        assertEquals(1, legacy.drops)

        usage.hydrate()
        assertEquals(mapOf("c/A" to 4, "c/B" to 1), usage.counts())
        assertEquals(1, legacy.drops)
    }

    @Test
    fun hydrate_after_interrupted_migration_does_not_double() = runTest {
        // The copy landed and then the process died before the drop: legacy still holds the same counts.
        dao.replaceCounts(mapOf("c/A" to 3))
        val legacy = FakeLegacy(mapOf("c/A" to 3))
        val usage = store(legacy)
        usage.hydrate()
        assertEquals(mapOf("c/A" to 3), usage.counts())
        assertEquals(mapOf("c/A" to 3), stored())
        assertNull(legacy.counts)
    }

    @Test
    fun record_during_hydrate_is_kept() = runTest {
        dao.replaceCounts(mapOf("c/A" to 3))
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val held = object : WrappedDao(dao) {
            override suspend fun counts(): List<LaunchCountRow> {
                entered.complete(Unit)
                gate.await()
                return super.counts()
            }
        }
        val usage = store(dao = held)
        val hydrating = launch { usage.hydrate() }
        entered.await()
        usage.record("c/A", learn = true)
        gate.complete(Unit)
        hydrating.join()
        assertEquals(mapOf("c/A" to 4), usage.counts())

        settle()
        assertEquals(mapOf("c/A" to 4), usage.counts())
        assertEquals(mapOf("c/A" to 4), stored())
        assertEquals(1, dao.allSelections().size)
    }

    @Test
    fun hydrate_prunes_old_selections() = runTest {
        fun row(at: Long) = SelectionRow(resultId = "contact:1", kind = Kind.Contact.name, query = "al", source = PickSource.SEARCH, at = at)
        dao.recordLaunch(row(NOW - LearningWeights.KEEP_MS - 1), null)
        dao.recordLaunch(row(NOW - 1_000), null)
        dao.recordLaunch(row(NOW - 2_000), null)
        val usage = store()
        usage.hydrate()
        assertEquals(listOf(NOW - 1_000, NOW - 2_000), dao.allSelections().map { it.at }.sortedDescending())
        // Two recent exact picks reach the threshold; the pruned one is not in memory either.
        val score = usage.picks().at(NOW).score("contact:1", "al")
        assertTrue(score in 1.9..2.0)
    }

    @Test
    fun failing_database_leaves_empty_state_and_does_not_throw() = runTest {
        val errors = mutableListOf<Throwable>()
        val usage = store(FakeLegacy(mapOf("c/A" to 2)), BrokenDao(dao)) { errors += it }
        usage.hydrate()
        assertEquals(emptyMap<String, Int>(), usage.counts())
        assertEquals(0.0, usage.picks().at(NOW).score("app:c/A", "a"), 0.0)
        assertEquals(1, errors.size)

        usage.record("c/A", learn = true)
        settle()
        assertEquals(mapOf("c/A" to 1), usage.counts())
        assertEquals(2, errors.size)
    }

    @Test
    fun clearPicks_empties_index_and_table_but_keeps_counts() = runTest {
        val usage = store()
        usage.recordPick("app:c/A", Kind.App, "maps")
        usage.recordPick("app:c/A", Kind.App, "maps")
        usage.record("c/B", learn = true)
        settle()
        usage.clearPicks()
        assertEquals(0.0, usage.picks().at(NOW).score("app:c/A", "maps"), 0.0)
        assertEquals(emptyList<SelectionRow>(), dao.allSelections())
        assertEquals(mapOf("c/A" to 2, "c/B" to 1), usage.counts())
        assertEquals(mapOf("c/A" to 2, "c/B" to 1), stored())
    }

    @Test
    fun replaceCounts_replaces_memory_and_table() = runTest {
        val usage = store()
        usage.record("c/A", learn = false)
        settle()
        usage.replaceCounts(mapOf("c/B" to 7))
        assertEquals(mapOf("c/B" to 7), usage.counts())
        assertEquals(mapOf("c/B" to 7), stored())
    }

    @Test
    fun exportPicks_returns_every_stored_row() = runTest {
        val usage = store()
        usage.recordPick("contact:1", Kind.Contact, "al")
        usage.record("c/A", learn = true)
        settle()
        assertEquals(setOf("contact:1", "app:c/A"), usage.exportPicks().map { it.resultId }.toSet())
    }

    @Test
    fun recordPick_with_action_stores_it_and_recents_use_it() = runTest {
        val usage = store()
        usage.recordPick("app:c/A", Kind.App, "maps", actionId = "shortcut:c/A/nav")
        usage.recordPick("contact:1", Kind.Contact, "al")
        assertEquals(listOf("contact:1", "shortcut:c/A/nav"), usage.recents())

        settle()
        val row = dao.allSelections().first { it.resultId == "app:c/A" }
        assertEquals("shortcut:c/A/nav", row.actionId)
        assertNull(dao.allSelections().first { it.resultId == "contact:1" }.actionId)
    }

    // A shortcut folded onto a contact is kept as the entry, but ranking is credited to the contact.
    @Test
    fun an_action_picked_inside_another_result_remembers_its_owner() = runTest {
        val usage = store()
        usage.recordPick("contact:7", Kind.Contact, "matt", actionId = "shortcut:chat/matt")
        usage.recordPick("app:c/A", Kind.App, "a")
        assertEquals(mapOf("shortcut:chat/matt" to "contact:7"), usage.recentOwners())

        // The same entry picked on its own is no longer anyone's action.
        usage.recordPick("shortcut:chat/matt", Kind.Shortcut, "")
        assertEquals(emptyMap<String, String>(), usage.recentOwners())
    }

    @Test
    fun hydrate_restores_the_owner_of_a_recents_action() = runTest {
        dao.recordLaunch(
            SelectionRow(
                resultId = "contact:7", kind = Kind.Contact.name, query = "matt", source = PickSource.SEARCH,
                at = NOW - 1_000, actionId = "shortcut:chat/matt",
            ),
            null,
        )
        // A pick with the same id as its action (a contact's own method) has no separate owner.
        dao.recordLaunch(
            SelectionRow(
                resultId = "app:c/A", kind = Kind.App.name, query = null, source = PickSource.SEARCH,
                at = NOW - 500, actionId = "app:c/A",
            ),
            null,
        )
        val usage = store()
        usage.hydrate()
        assertEquals(mapOf("shortcut:chat/matt" to "contact:7"), usage.recentOwners())
    }

    @Test
    fun clearing_picks_clears_the_owners() = runTest {
        val usage = store()
        usage.recordPick("contact:7", Kind.Contact, "matt", actionId = "shortcut:chat/matt")
        settle()
        usage.clearPicks()
        assertEquals(emptyMap<String, String>(), usage.recentOwners())
    }

    @Test
    fun recents_are_newest_first_and_each_once() = runTest {
        val usage = store()
        usage.recordPick("app:c/A", Kind.App, "a")
        usage.recordPick("app:c/B", Kind.App, "b")
        usage.recordPick("app:c/A", Kind.App, "a")
        assertEquals(listOf("app:c/A", "app:c/B"), usage.recents())
    }

    @Test
    fun launcher_launches_are_not_recents() = runTest {
        val usage = store()
        usage.recordPick("contact:1", Kind.Contact, "al")
        usage.record("c/A", learn = true)
        usage.record("c/B", learn = false)
        assertEquals(listOf("contact:1"), usage.recents())
    }

    @Test
    fun recents_are_capped_at_40() = runTest {
        val usage = store()
        repeat(45) { usage.recordPick("contact:$it", Kind.Contact, "x") }
        assertEquals((44 downTo 5).map { "contact:$it" }, usage.recents())
    }

    @Test
    fun hydrate_rebuilds_recents_from_stored_search_rows() = runTest {
        fun row(id: String, source: String, at: Long, actionId: String? = null) =
            SelectionRow(resultId = id, kind = Kind.App.name, query = null, source = source, at = at, actionId = actionId)
        dao.recordLaunch(row("app:c/A", PickSource.SEARCH, NOW - 5_000), null)
        dao.recordLaunch(row("app:c/B", PickSource.SEARCH, NOW - 4_000, actionId = "shortcut:c/B/x"), null)
        dao.recordLaunch(row("app:c/C", PickSource.LAUNCHER, NOW - 3_000), null)
        dao.recordLaunch(row("app:c/A", PickSource.SEARCH, NOW - 2_000), null)
        dao.recordLaunch(row("app:c/D", PickSource.SEARCH, NOW - LearningWeights.KEEP_MS - 1), null)
        // Same instant as A's newer row but stored later, so it counts as newer.
        dao.recordLaunch(row("app:c/E", PickSource.SEARCH, NOW - 2_000), null)
        val usage = store()
        usage.hydrate()
        assertEquals(listOf("app:c/E", "app:c/A", "shortcut:c/B/x"), usage.recents())
    }

    @Test
    fun hydrate_keeps_40_distinct_recents_when_old_ones_repeat() = runTest {
        fun row(id: String, at: Long) = SelectionRow(resultId = id, kind = Kind.Contact.name, query = "x", source = PickSource.SEARCH, at = at)
        repeat(45) { dao.recordLaunch(row("contact:$it", NOW - 100_000 + it), null) }
        repeat(50) { dao.recordLaunch(row("contact:44", NOW - 1_000 + it), null) }
        val usage = store()
        usage.hydrate()
        assertEquals((44 downTo 5).map { "contact:$it" }, usage.recents())
    }

    @Test
    fun hydrate_reads_selections_once() = runTest {
        dao.recordLaunch(SelectionRow(resultId = "contact:1", kind = Kind.Contact.name, query = "al", source = PickSource.SEARCH, at = NOW - 1), null)
        var reads = 0
        val counting = object : WrappedDao(dao) {
            override suspend fun selectionsSince(since: Long): List<SelectionRow> {
                reads++
                return super.selectionsSince(since)
            }
        }
        val usage = store(dao = counting)
        usage.hydrate()
        assertEquals(1, reads)
        assertEquals(listOf("contact:1"), usage.recents())
    }

    @Test
    fun clearPicks_empties_recents() = runTest {
        val usage = store()
        usage.recordPick("contact:1", Kind.Contact, "al")
        settle()
        usage.clearPicks()
        assertEquals(emptyList<String>(), usage.recents())
        usage.hydrate()
        assertEquals(emptyList<String>(), usage.recents())
    }

    @Test
    fun pick_during_hydrate_is_in_recents_once() = runTest {
        fun row(id: String, at: Long) = SelectionRow(resultId = id, kind = Kind.Contact.name, query = "x", source = PickSource.SEARCH, at = at)
        dao.recordLaunch(row("contact:1", NOW - 2_000), null)
        dao.recordLaunch(row("contact:2", NOW - 1_000), null)
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val held = object : WrappedDao(dao) {
            override suspend fun selectionsSince(since: Long): List<SelectionRow> {
                entered.complete(Unit)
                gate.await()
                return super.selectionsSince(since)
            }
        }
        val usage = store(dao = held)
        val hydrating = launch { usage.hydrate() }
        entered.await()
        usage.recordPick("contact:1", Kind.Contact, "x")
        gate.complete(Unit)
        hydrating.join()
        assertEquals(listOf("contact:1", "contact:2"), usage.recents())

        settle()
        assertEquals(listOf("contact:1", "contact:2"), usage.recents())
        usage.hydrate()
        assertEquals(listOf("contact:1", "contact:2"), usage.recents())
    }
}
