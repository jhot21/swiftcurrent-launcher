package me.jhot.swiftcurrent.core.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UsageDaoTest {
    private lateinit var db: SwiftcurrentDatabase
    private val dao get() = db.usage()

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SwiftcurrentDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun close() = db.close()

    private fun row(at: Long, id: String = "a/.A") =
        SelectionRow(resultId = id, kind = "app", query = "a", source = PickSource.SEARCH, at = at)

    @Test
    fun recordLaunch_inserts_row_and_bumps_count() = runTest {
        dao.recordLaunch(row(1), "a/.A")
        dao.recordLaunch(row(2), "a/.A")
        assertEquals(listOf(LaunchCountRow("a/.A", 2)), dao.counts())
        assertEquals(2, dao.allSelections().size)
    }

    @Test
    fun recordLaunch_without_row_only_counts() = runTest {
        dao.recordLaunch(null, "a/.A")
        assertEquals(listOf(LaunchCountRow("a/.A", 1)), dao.counts())
        assertEquals(0, dao.allSelections().size)
    }

    @Test
    fun recordLaunch_without_component_only_inserts() = runTest {
        dao.recordLaunch(row(1, "contact:1"), null)
        assertEquals(emptyList<LaunchCountRow>(), dao.counts())
        assertEquals(1, dao.allSelections().size)
    }

    @Test
    fun pruneBefore_drops_only_older_rows() = runTest {
        dao.recordLaunch(row(100), null)
        dao.recordLaunch(row(200), null)
        assertEquals(1, dao.pruneBefore(150))
        assertEquals(listOf(200L), dao.allSelections().map { it.at })
        assertEquals(listOf(200L), dao.selectionsSince(150).map { it.at })
    }

    @Test
    fun replaceCounts_replaces_all() = runTest {
        dao.replaceCounts(mapOf("a" to 5))
        dao.replaceCounts(mapOf("b" to 2))
        assertEquals(listOf(LaunchCountRow("b", 2)), dao.counts())
        assertEquals(1, dao.countRows())
    }

    @Test
    fun clearSelections_leaves_counts() = runTest {
        dao.recordLaunch(row(1), "a/.A")
        dao.clearSelections()
        assertEquals(0, dao.allSelections().size)
        assertEquals(listOf(LaunchCountRow("a/.A", 1)), dao.counts())
    }
}
