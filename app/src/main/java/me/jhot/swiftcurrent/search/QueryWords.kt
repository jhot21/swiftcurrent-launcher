package me.jhot.swiftcurrent.search

/**
 * The words of a typed query, folded once. A source keeps the folded text of what it searches (folding it
 * per keystroke was the cost), so [matches] takes a haystack that is already folded by [Ranking.normalize].
 */
class QueryWords private constructor(private val words: List<String>) {
    val isEmpty: Boolean get() = words.isEmpty()

    /** True when every word is somewhere in [folded], in any order. */
    fun matches(folded: String): Boolean = words.all { it in folded }

    companion object {
        fun of(query: String): QueryWords =
            QueryWords(Ranking.normalize(query).split(' ').filter { it.isNotEmpty() })
    }
}
