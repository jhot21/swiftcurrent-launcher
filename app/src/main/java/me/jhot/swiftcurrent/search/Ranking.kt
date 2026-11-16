package me.jhot.swiftcurrent.search

import java.text.Normalizer

data class RankingOptions(
    val appsFirst: Boolean = true,
    val contactsFirst: Boolean = false,
    val learned: LearnedScore? = null,
)

object Ranking {
    // Nonspacing marks only (accents): spacing marks such as Devanagari vowel signs are letters, not accents.
    private val combiningMarks = Regex("\\p{Mn}+")

    /** Lowercases and strips accents so "cafe" finds "Café". */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(combiningMarks, "").lowercase()

    fun order(
        query: String,
        results: List<SearchResult>,
        options: RankingOptions = RankingOptions(),
    ): List<SearchResult> {
        val q = normalize(query).trim()
        if (q.isEmpty()) return emptyList()
        return results.mapNotNull { r ->
            val label = r.foldedLabel
            // The best of the name shown and any other name it can be found by.
            val byLabel = quality(q, label)
            val byAlias = r.foldedAlias?.let { quality(q, it) }
            val best = listOfNotNull(byLabel, byAlias).minOrNull() ?: return@mapNotNull null
            // Every sort key is computed here, once, so the comparator only compares fields.
            Ranked(
                result = r,
                group = if (options.appsFirst && r.kind != Kind.App) 1 else 0,
                learned = options.learned?.score(r.id, q) ?: 0.0,
                kind = if (options.contactsFirst) contactsFirstRank(r) else rank(r),
                quality = if (r.kind == Kind.Event) eventTier(best) else best,
                normalized = label,
                startMillis = r.startMillis,
            )
        }.sortedWith(byRank).map { it.result }
    }

    private data class Ranked(
        val result: SearchResult,
        val group: Int,
        val learned: Double,
        val kind: Int,
        val quality: Int,
        val normalized: String,
        val startMillis: Long?,
    )

    // Hand-written so no key is boxed per comparison, which compareBy/thenBy would do.
    private val byRank = Comparator<Ranked> { a, b ->
        if (a.group != b.group) return@Comparator a.group.compareTo(b.group)
        // Higher score first; compared directly rather than negated to avoid -0.0.
        if (a.learned != b.learned) return@Comparator if (a.learned > b.learned) -1 else 1
        if (a.kind != b.kind) return@Comparator a.kind.compareTo(b.kind)
        if (a.quality != b.quality) return@Comparator a.quality.compareTo(b.quality)
        // Rows with a start come first, soonest first. Total on its own, so mixing them with rows that have none stays one order.
        val sa = a.startMillis
        val sb = b.startMillis
        if (sa != null && sb != null) {
            if (sa != sb) return@Comparator sa.compareTo(sb)
        } else if (sa != null) {
            return@Comparator -1
        } else if (sb != null) {
            return@Comparator 1
        }
        val n = a.normalized.compareTo(b.normalized)
        if (n != 0) n else a.result.label.compareTo(b.result.label)
    }

    private fun contactsFirstRank(r: SearchResult): Int = when (r.kind) {
        Kind.App -> 0
        Kind.Contact -> 1
        Kind.Shortcut, Kind.Event -> 2
        else -> 3
    }

    /**
     * Apps, then a contact that shortcuts are named after (a person the owner reaches through apps),
     * then the remaining shortcuts, contacts and others.
     */
    private fun rank(r: SearchResult): Int = when {
        r.kind == Kind.App -> 0
        r.kind == Kind.Contact && r.folded > 0 -> 1
        r.kind == Kind.Shortcut || r.kind == Kind.Event -> 2
        r.kind == Kind.Contact -> 3
        else -> 4
    }

    /** How well [label] matches [query]: 0 prefix, 1 word prefix, 2 substring, null none. Both are normalized here. */
    fun matchQuality(query: String, label: String): Int? = quality(normalize(query).trim(), normalize(label))

    /** As [matchQuality] for text already normalized and trimmed, such as a [SearchResult.foldedLabel]. */
    fun matchQualityFolded(foldedQuery: String, foldedLabel: String): Int? = quality(foldedQuery, foldedLabel)

    /**
     * An event's match quality for ordering. Calendar titles put a name before or after what the entry is
     * ("Jordan Dentist", "Dentist Jordan"), so a word start counts as a title start; a match inside a word stays lower.
     */
    fun eventTier(quality: Int): Int = if (quality == 1) 0 else quality

    /** 0 = prefix (or exact), 1 = word prefix, 2 = substring, null = no match. */
    private fun quality(q: String, label: String): Int? = when {
        label.startsWith(q) -> 0
        label.split(' ', '-', '_', ':', '.').any { it.isNotEmpty() && it.startsWith(q) } -> 1
        label.contains(q) -> 2
        else -> multiWord(q, label)
    }

    /** "tasker bed" finds "Tasker: Bedtime": each word of the query starts a word of the label. */
    private fun multiWord(q: String, label: String): Int? {
        val queryWords = q.split(' ').filter { it.isNotEmpty() }
        if (queryWords.size < 2) return null
        val labelWords = label.split(' ', '-', '_', ':', '.').filter { it.isNotEmpty() }
        return if (queryWords.all { w -> labelWords.any { it.startsWith(w) } }) 1 else null
    }
}
