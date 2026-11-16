package me.jhot.swiftcurrent.search

/**
 * The one extension point for search. A new integration is a new implementation of this
 * interface plus a settings page; the search screen does not change.
 */
interface SearchSource {
    val name: String

    /** May return loose matches; [Ranking] drops results whose label does not match. */
    suspend fun search(query: String): List<SearchResult>

    /**
     * The rows for stored ids (the recents list), as [search] would build them. Ids this source does not
     * own, or whose item is gone, are left out.
     */
    suspend fun resolve(ids: Set<String>): List<SearchResult> = emptyList()
}

data class SearchResult(
    val id: String,
    val label: String,
    val sourceName: String,
    val kind: Kind,
    val icon: IconRef?,
    val primary: Action,
    val secondary: List<Action> = emptyList(),
    /** Short marker the screen shows beside the label (for example "Hidden"); kept out of the label so ranking is unaffected. */
    val badge: String? = null,
    /** For a shortcut result: the app's name and the shortcut's own label, which is how it is matched to a contact. */
    val appLabel: String? = null,
    val entryLabel: String? = null,
    /** For a shortcut result: the package that owns it, so a contact's own buttons can tell which shortcuts they replace. */
    val packageName: String? = null,
    /** Another name the result can be found by (an app's system name once the owner has renamed it). */
    val alias: String? = null,
    /** How many shortcuts are named after this contact; such a contact ranks above plain shortcuts. */
    val folded: Int = 0,
    /** The id a pick of this row counts toward in ranking, when that is not [id]. */
    val ownerId: String? = null,
    /** For an event: its calendar's name, shown before the label ("Work: Standup") on every row but the top. */
    val prefix: String? = null,
    /** For an event: when it starts, shown on the second line of the top result after its calendar's name ([prefix]). */
    val detail: String? = null,
    /** For an event: it belongs to a repeating series, which the top result marks. */
    val recurring: Boolean = false,
    /** For an event: its start in epoch milliseconds, so events that tie on everything else go soonest first. */
    val startMillis: Long? = null,
) {
    // Folded on first use and kept with the row: a row a source keeps between keystrokes is then folded
    // once, not by every match, ranking pass and contact fold. Not part of equality or copy().
    val foldedLabel: String by lazy(LazyThreadSafetyMode.PUBLICATION) { Ranking.normalize(label) }
    val foldedAlias: String? by lazy(LazyThreadSafetyMode.PUBLICATION) { alias?.let(Ranking::normalize) }
    val foldedEntryLabel: String? by lazy(LazyThreadSafetyMode.PUBLICATION) { entryLabel?.let(Ranking::normalize) }
}

enum class Kind { App, Shortcut, Contact, Event, Other }

data class Action(
    val label: String,
    val icon: IconRef?,
    /**
     * The recents identity of an action that is not its row's primary one; the primary is known by the row's id.
     * Placed before [perform] so `Action(label, icon) { ... }` keeps its trailing lambda.
     */
    val recentId: String? = null,
    val perform: () -> LaunchOutcome,
)

sealed interface LaunchOutcome {
    data object Launched : LaunchOutcome
    data class Failed(val message: String) : LaunchOutcome
}

/** Describes where an icon comes from; how it is loaded and drawn is decided by the UI. */
sealed interface IconRef {
    data class Package(val packageName: String) : IconRef
    data class ShortcutId(val packageName: String, val id: String) : IconRef

    class Bytes(val bytes: ByteArray) : IconRef {
        override fun equals(other: Any?) = other is Bytes && bytes.contentEquals(other.bytes)
        override fun hashCode() = bytes.contentHashCode()
    }
}
