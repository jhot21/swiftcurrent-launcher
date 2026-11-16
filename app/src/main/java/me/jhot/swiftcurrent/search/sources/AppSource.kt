package me.jhot.swiftcurrent.search.sources

import me.jhot.swiftcurrent.catalog.AppInfo
import me.jhot.swiftcurrent.search.Action
import me.jhot.swiftcurrent.search.IconRef
import me.jhot.swiftcurrent.search.Kind
import me.jhot.swiftcurrent.search.LaunchOutcome
import me.jhot.swiftcurrent.search.QueryWords
import me.jhot.swiftcurrent.search.Ranking
import me.jhot.swiftcurrent.search.SearchResult
import me.jhot.swiftcurrent.search.SearchSource

fun interface AppLauncher {
    fun launch(component: String): LaunchOutcome
}

/**
 * Hidden apps leave the app list but stay findable here, marked with a "Hidden" badge. */
class AppSource(
    private val apps: () -> List<AppInfo>,
    private val hidden: () -> Set<String>,
    private val launcher: AppLauncher,
    private val strings: SearchStrings,
) : SearchSource {
    override val name = "Apps"

    override suspend fun search(query: String): List<SearchResult> {
        val words = QueryWords.of(query)
        if (words.isEmpty) return emptyList()
        // The list the shell already loaded (and refreshes on package changes): no binder call per keystroke.
        val hiddenNow = hidden()
        return folded().mapNotNull { (app, haystack) ->
            if (!words.matches(haystack)) return@mapNotNull null
            row(app, hiddenNow)
        }
    }

    private class Folded(val source: List<AppInfo>, val rows: List<Pair<AppInfo, String>>)

    @Volatile private var memo: Folded? = null

    /**
     * Each app with its folded names, found by the owner's name and by the system's so renaming never costs
     * the old way to find it. Folding every label per keystroke was the cost, so it is redone only when the
     * shell publishes a new list (a different object), not per query.
     */
    private fun folded(): List<Pair<AppInfo, String>> {
        val now = apps()
        memo?.takeIf { it.source === now }?.let { return it.rows }
        val rows = now.map { it to Ranking.normalize(it.label) + " " + Ranking.normalize(it.systemLabel) }
        memo = Folded(now, rows)
        return rows
    }

    override suspend fun resolve(ids: Set<String>): List<SearchResult> {
        val byId = apps().associateBy { idOf(it) }
        val hiddenNow = hidden()
        return ids.mapNotNull { id -> byId[id]?.let { row(it, hiddenNow) } }
    }

    private fun idOf(app: AppInfo) = "app:${app.component}"

    private fun row(app: AppInfo, hiddenNow: Set<String>): SearchResult {
        val icon = IconRef.Package(app.packageName)
        return SearchResult(
            id = idOf(app),
            label = app.label,
            sourceName = name,
            kind = Kind.App,
            icon = icon,
            // Not recorded here: search reports the pick once it launched, with the query.
            primary = Action(app.label, icon) { launcher.launch(app.component) },
            badge = when {
                app.component in hiddenNow -> strings.hiddenBadge
                app.archived -> strings.archivedBadge
                else -> null
            },
            alias = app.systemLabel.takeIf { it != app.label },
        )
    }
}
