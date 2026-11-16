package me.jhot.swiftcurrent.search.sources

import me.jhot.swiftcurrent.core.UserShortcut
import me.jhot.swiftcurrent.search.Action
import me.jhot.swiftcurrent.search.IconRef
import me.jhot.swiftcurrent.search.Kind
import me.jhot.swiftcurrent.search.LaunchOutcome
import me.jhot.swiftcurrent.search.QueryWords
import me.jhot.swiftcurrent.search.SearchResult
import me.jhot.swiftcurrent.search.SearchSource
import me.jhot.swiftcurrent.shortcuts.ShortcutFilter
import me.jhot.swiftcurrent.shortcuts.userShortcutKey

/**
 * The shortcuts the owner made, as "App: name" results beside the system's. [shortcuts] is read on
 * every query so a new or deleted one shows up at once. [appLabel] returns null for a package that
 * is gone, which hides its shortcuts rather than showing a raw package name; it may be a binder call,
 * so it is asked once per [shortcuts] list and [labelsVersion], not per keystroke. [labelsVersion]
 * answers a different object whenever the names [appLabel] reads may have changed.
 */
class UserShortcutSource(
    private val shortcuts: () -> List<UserShortcut>,
    private val appLabel: (String) -> String?,
    private val launch: (UserShortcut) -> LaunchOutcome,
    private val filter: ShortcutFilter = ShortcutFilter.All,
    private val labelsVersion: () -> Any = {},
) : SearchSource {
    override val name = "Shortcuts"

    private class Built(val source: List<UserShortcut>, val version: Any, val rows: List<Pair<UserShortcut, SearchResult>>)

    @Volatile private var built: Built? = null

    // Rows with their labels resolved and folded, rebuilt only when the owner's list or the app names change.
    private fun rows(): List<Pair<UserShortcut, SearchResult>> {
        val list = shortcuts()
        val version = labelsVersion()
        built?.takeIf { it.source === list && it.version === version }?.let { return it.rows }
        val rows = list.mapNotNull { s -> row(s)?.let { s to it } }
        built = Built(list, version, rows)
        return rows
    }

    override suspend fun search(query: String): List<SearchResult> {
        val words = QueryWords.of(query)
        if (words.isEmpty) return emptyList()
        return rows().mapNotNull { (s, row) ->
            row.takeIf { filter.enabled(userShortcutKey(s.id)) && words.matches(it.foldedLabel) }
        }
    }

    override suspend fun resolve(ids: Set<String>): List<SearchResult> {
        val byId = rows().associate { (s, row) -> row.id to (s to row) }
        return ids.mapNotNull { id -> byId[id]?.takeIf { (s, _) -> filter.enabled(userShortcutKey(s.id)) }?.second }
    }

    private fun idOf(s: UserShortcut) = "shortcut:${userShortcutKey(s.id)}"

    private fun row(s: UserShortcut): SearchResult? {
        if (s.label.isBlank()) return null
        val app = appLabel(s.appPackage)?.takeIf { it.isNotBlank() } ?: return null
        val label = "$app: ${s.label}"
        val icon = s.iconBytes?.let { IconRef.Bytes(it) } ?: IconRef.Package(s.appPackage)
        return SearchResult(
            id = idOf(s),
            label = label,
            sourceName = name,
            kind = Kind.Shortcut,
            icon = icon,
            primary = Action(label, icon) { launch(s) },
        )
    }
}
