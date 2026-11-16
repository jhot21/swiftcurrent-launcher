package me.jhot.swiftcurrent.catalog

import me.jhot.swiftcurrent.search.Ranking

data class Section(val key: Char, val apps: List<AppInfo>)

object AppSections {
    private class Keyed(val app: AppInfo, val folded: String) {
        /** The section letter, from the folded label already in hand (folding never changes whitespace). */
        val letter: Char get() = letterOfFolded(folded)
    }

    fun build(
        apps: List<AppInfo>,
        hidden: Set<String>,
        counts: Map<String, Int>,
        sortByUsage: Boolean,
    ): List<Section> {
        // Each label is folded once up front; the comparators below run O(n log n) times.
        val alpha = compareBy<Keyed> { it.folded }.thenBy { it.app.label }
        val order = if (sortByUsage) compareByDescending<Keyed> { counts[it.app.component] ?: 0 }.then(alpha) else alpha
        return apps.filter { it.component !in hidden }
            .map { Keyed(it, fold(it.label)) }
            .groupBy { it.letter }
            .toSortedMap(compareBy { if (it == '#') '\u0000' else it })
            .map { (k, v) -> Section(k, v.sortedWith(order).map { it.app }) }
    }

    // Marks are stripped so "Émail" sorts and files with "E". The same folding search uses, so the two never disagree.
    private fun fold(s: String) = Ranking.normalize(s)

    private fun letterOfFolded(folded: String): Char {
        val c = folded.trim().firstOrNull()?.uppercaseChar() ?: return '#'
        return if (c in 'A'..'Z') c else '#'
    }
}
