package me.jhot.swiftcurrent.ui.settings

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.jhot.swiftcurrent.catalog.RoomUsageStore
import me.jhot.swiftcurrent.core.Backup
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.LauncherData
import me.jhot.swiftcurrent.core.LegacyCounts
import me.jhot.swiftcurrent.core.Target
import me.jhot.swiftcurrent.core.db.LaunchCountRow
import me.jhot.swiftcurrent.core.db.SelectionRow
import me.jhot.swiftcurrent.core.db.SwiftcurrentDatabase
import me.jhot.swiftcurrent.core.db.UsageDao
import me.jhot.swiftcurrent.search.Kind
import me.jhot.swiftcurrent.shortcuts.MemoryLauncherStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupControllerTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), SwiftcurrentDatabase::class.java).build()

    @After
    fun close() = db.close()

    private val noLegacy = object : LegacyCounts {
        override suspend fun read(): Map<String, Int>? = null
        override suspend fun drop() {}
    }

    private fun usage() = RoomUsageStore(db.usage(), noLegacy, { 1_000L }, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private fun awaitStored(expected: Map<String, Int>) = runBlocking {
        withTimeout(5_000) { while (db.usage().counts().associate { it.component to it.count } != expected) delay(10) }
    }
    private val mine = LauncherData(favorites = listOf(Entry("a", Target.App("pkg.a/.Main"))))

    @Test
    fun backing_up_writes_the_stored_data_as_a_backup() = runBlocking {
        val store = MemoryLauncherStore(mine)
        var written = ""
        assertEquals(BackupOutcome.BackedUp, BackupController(store, usage(), Dispatchers.Unconfined).export { written = it })
        assertEquals(mine, (Backup.import(written) as me.jhot.swiftcurrent.core.ImportResult.Ok).data)
    }

    @Test
    fun restoring_a_good_backup_replaces_the_data() = runBlocking {
        val store = MemoryLauncherStore(LauncherData())
        val out = BackupController(store, usage(), Dispatchers.Unconfined).restore { Backup.export(mine) }
        assertEquals(BackupOutcome.Restored, out)
        assertEquals(mine, store.load())
    }

    @Test
    fun restoring_a_rejected_file_keeps_the_data() = runBlocking {
        val store = MemoryLauncherStore(mine)
        val c = BackupController(store, usage(), Dispatchers.Unconfined)
        assertEquals(BackupOutcome.Rejected, c.restore { "not json at all" })
        assertEquals(BackupOutcome.Rejected, c.restore { """{"version":99}""" })
        assertEquals(mine, store.load())
    }

    @Test
    fun a_file_that_cannot_be_read_or_written_is_a_failure_not_a_crash() = runBlocking {
        val store = MemoryLauncherStore(mine)
        val c = BackupController(store, usage(), Dispatchers.Unconfined)
        assertEquals(BackupOutcome.Failed, c.restore { throw java.io.IOException("gone") })
        assertEquals(BackupOutcome.Failed, c.export { throw java.io.IOException("full") })
        assertEquals(mine, store.load())
    }

    @Test
    fun export_includes_counts_from_the_database() = runBlocking {
        val store = MemoryLauncherStore(mine)
        val usage = usage()
        usage.record("pkg.a/.Main", learn = false)
        usage.record("pkg.a/.Main", learn = false)
        var written = ""
        BackupController(store, usage, Dispatchers.Unconfined).export { written = it }
        assertEquals(mine.copy(launchCounts = mapOf("pkg.a/.Main" to 2)), (Backup.import(written) as me.jhot.swiftcurrent.core.ImportResult.Ok).data)
    }

    @Test
    fun export_has_no_selection_history() = runBlocking {
        val usage = usage()
        usage.recordPick("contact:7", Kind.Contact, "Zebulon")
        usage.record("pkg.a/.Main", learn = true)
        var written = ""
        BackupController(MemoryLauncherStore(mine), usage, Dispatchers.Unconfined).export { written = it }
        // The learn-from-picks switch is a setting, not history, so its name is taken out before looking.
        val text = written.replace("\"learnFromPicks\"", "").lowercase()
        assertTrue(written, written.contains("\"learnFromPicks\""))
        for (word in listOf("selection", "picks", "zebulon", "contact:7", "app:pkg.a")) assertFalse(word, text.contains(word))
    }

    /** Works except that it cannot replace counts, as a full or damaged database would. */
    private class CountsFailDao(private val inner: UsageDao) : UsageDao() {
        override suspend fun recordLaunch(selection: SelectionRow?, component: String?) = inner.recordLaunch(selection, component)
        override suspend fun selectionsSince(since: Long) = inner.selectionsSince(since)
        override suspend fun allSelections() = inner.allSelections()
        override suspend fun counts() = inner.counts()
        override suspend fun countRows() = inner.countRows()
        override suspend fun pruneBefore(before: Long) = inner.pruneBefore(before)
        override suspend fun clearSelections() = inner.clearSelections()
        override suspend fun replaceCounts(counts: Map<String, Int>) = throw android.database.sqlite.SQLiteFullException("full")
        override suspend fun insert(row: SelectionRow) = error("not delegated")
        override suspend fun insertCounts(rows: List<LaunchCountRow>) = error("not delegated")
        override suspend fun clearCounts() = error("not delegated")
        override suspend fun bump(component: String) = error("not delegated")
    }

    @Test
    fun restore_is_restored_even_when_the_counts_cannot_be_replaced() = runBlocking {
        // The settings are already on disk by then, so a Failed answer would leave the old setup on screen.
        val store = MemoryLauncherStore(LauncherData())
        val usage = RoomUsageStore(CountsFailDao(db.usage()), noLegacy, { 1_000L }, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        val backup = mine.copy(launchCounts = mapOf("pkg.a/.Main" to 9))
        val out = BackupController(store, usage, Dispatchers.Unconfined).restore { Backup.export(backup) }
        assertEquals(BackupOutcome.Restored, out)
        assertEquals(mine, store.load().copy(launchCounts = emptyMap()))
    }

    @Test
    fun restore_replaces_database_counts() = runBlocking {
        val usage = usage()
        usage.record("pkg.old/.Main", learn = false)
        awaitStored(mapOf("pkg.old/.Main" to 1))
        val backup = mine.copy(launchCounts = mapOf("pkg.a/.Main" to 9))
        assertEquals(BackupOutcome.Restored, BackupController(MemoryLauncherStore(LauncherData()), usage, Dispatchers.Unconfined).restore { Backup.export(backup) })
        assertEquals(mapOf("pkg.a/.Main" to 9), usage.counts())
        awaitStored(mapOf("pkg.a/.Main" to 9))
    }
}
