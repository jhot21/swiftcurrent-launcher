package me.jhot.swiftcurrent.search

import kotlin.math.pow

/** One result the owner opened, with the (normalized) query typed at the time, if any. */
data class Pick(val resultId: String, val query: String?, val at: Long)

fun interface LearnedScore {
    /** [query] is already normalized. Returns 0.0 until the evidence passes [LearningWeights.THRESHOLD]. */
    fun score(resultId: String, query: String): Double
}

object LearningWeights {
    const val HALF_LIFE_MS = 30L * 24 * 60 * 60 * 1000
    const val EXACT = 1.0
    const val RELATED = 0.5
    const val ANY = 0.1
    const val THRESHOLD = 1.5
    const val KEEP_MS = 365L * 24 * 60 * 60 * 1000
}

/**
 * Picks summed per result and stored query, each weighted 2^((at - ref) / half-life) against a
 * fixed [ref]. Adding a pick is then one add, and decaying to any later time is one factor applied
 * at the end, so ranking does no per-pick work.
 */
class PickIndex private constructor(
    private val ref: Long,
    private val byResult: Map<String, Map<String?, Double>>,
) {
    operator fun plus(pick: Pick): PickIndex {
        // An empty index has no reference yet; the first pick sets it so exponents stay small.
        val base = if (byResult.isEmpty()) pick.at else ref
        val inner = byResult[pick.resultId].orEmpty()
        val updated = inner + (pick.query to (inner[pick.query] ?: 0.0) + weight(pick.at, base))
        return PickIndex(base, byResult + (pick.resultId to updated))
    }

    fun at(now: Long): LearnedScore {
        val decay = 2.0.pow(-(now - ref).toDouble() / LearningWeights.HALF_LIFE_MS)
        return LearnedScore { resultId, query ->
            val queries = byResult[resultId]
            if (queries == null) {
                0.0
            } else {
                var sum = 0.0
                for ((stored, w) in queries) {
                    val factor = when {
                        stored == null -> LearningWeights.ANY
                        stored == query -> LearningWeights.EXACT
                        stored.startsWith(query) || query.startsWith(stored) -> LearningWeights.RELATED
                        else -> LearningWeights.ANY
                    }
                    sum += w * factor
                }
                val score = sum * decay
                if (score < LearningWeights.THRESHOLD) 0.0 else score
            }
        }
    }

    companion object {
        val EMPTY = PickIndex(0L, emptyMap())

        fun of(picks: List<Pick>): PickIndex {
            if (picks.isEmpty()) return EMPTY
            val ref = picks.maxOf { it.at }
            val sums = HashMap<String, HashMap<String?, Double>>()
            for (p in picks) {
                val inner = sums.getOrPut(p.resultId) { HashMap() }
                inner[p.query] = (inner[p.query] ?: 0.0) + weight(p.at, ref)
            }
            return PickIndex(ref, sums)
        }

        private fun weight(at: Long, ref: Long) =
            2.0.pow((at - ref).toDouble() / LearningWeights.HALF_LIFE_MS)
    }
}
