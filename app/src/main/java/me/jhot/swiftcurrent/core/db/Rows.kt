package me.jhot.swiftcurrent.core.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One pick, kept so search can learn which result the user chooses for a query. */
@Entity(tableName = "selection", indices = [Index("resultId"), Index("query")])
data class SelectionRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val resultId: String,
    val kind: String,
    val query: String?,
    val source: String,
    val at: Long,
    /** The specific action picked inside the result (a shortcut, say); null for a plain launch and for rows from before this column existed. */
    val actionId: String? = null,
)

@Entity(tableName = "launch_count")
data class LaunchCountRow(
    @PrimaryKey val component: String,
    val count: Int,
)

object PickSource {
    const val SEARCH = "search"

    /**
     * Every launch through the shell's `launchComponent` (Home, app list, popups, gestures). That
     * path has one entry point, so it cannot tell those surfaces apart.
     */
    const val LAUNCHER = "launcher"
}
