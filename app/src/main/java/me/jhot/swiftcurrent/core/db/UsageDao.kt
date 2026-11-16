package me.jhot.swiftcurrent.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class UsageDao {
    /** One transaction, so a launch never leaves its row and its count disagreeing. */
    @Transaction
    open suspend fun recordLaunch(selection: SelectionRow?, component: String?) {
        if (selection != null) insert(selection)
        if (component != null) bump(component)
    }

    @Query("SELECT * FROM selection WHERE at >= :since")
    abstract suspend fun selectionsSince(since: Long): List<SelectionRow>

    @Query("SELECT * FROM selection")
    abstract suspend fun allSelections(): List<SelectionRow>

    @Query("SELECT * FROM launch_count")
    abstract suspend fun counts(): List<LaunchCountRow>

    @Query("SELECT COUNT(*) FROM launch_count")
    abstract suspend fun countRows(): Int

    @Query("DELETE FROM selection WHERE at < :before")
    abstract suspend fun pruneBefore(before: Long): Int

    @Query("DELETE FROM selection")
    abstract suspend fun clearSelections()

    @Transaction
    open suspend fun replaceCounts(counts: Map<String, Int>) {
        clearCounts()
        insertCounts(counts.map { LaunchCountRow(it.key, it.value) })
    }

    @Insert
    protected abstract suspend fun insert(row: SelectionRow)

    @Insert
    protected abstract suspend fun insertCounts(rows: List<LaunchCountRow>)

    @Query("DELETE FROM launch_count")
    protected abstract suspend fun clearCounts()

    @Query("INSERT INTO launch_count (component, count) VALUES (:component, 1) ON CONFLICT(component) DO UPDATE SET count = count + 1")
    protected abstract suspend fun bump(component: String)
}
