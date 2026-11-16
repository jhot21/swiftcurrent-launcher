package me.jhot.swiftcurrent.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningTest {
    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000
    private val id = "app:x"

    private fun picks(n: Int, query: String?, at: Long = now) = List(n) { Pick(id, query, at) }

    @Test
    fun no_picks_scores_zero() {
        assertEquals(0.0, PickIndex.EMPTY.at(now).score(id, "m"), 0.0)
    }

    @Test
    fun one_exact_pick_is_below_threshold() {
        val s = PickIndex.of(picks(1, "matt")).at(now)
        assertEquals(0.0, s.score(id, "matt"), 0.0)
    }

    @Test
    fun two_exact_picks_pass_threshold() {
        val s = PickIndex.of(picks(2, "matt")).at(now)
        assertEquals(2.0, s.score(id, "matt"), 1e-9)
    }

    @Test
    fun related_query_counts_half() {
        val s = PickIndex.of(picks(4, "matt")).at(now)
        assertEquals(2.0, s.score(id, "mat"), 1e-9)
        assertEquals(2.0, s.score(id, "matt n"), 1e-9)
    }

    @Test
    fun unrelated_and_queryless_picks_count_a_tenth() {
        val s = PickIndex.of(picks(20, null)).at(now)
        assertEquals(2.0, s.score(id, "zz"), 1e-9)
    }

    @Test
    fun half_life_halves_weight() {
        val s = PickIndex.of(picks(4, "matt", now - 30 * day)).at(now)
        assertEquals(2.0, s.score(id, "matt"), 1e-9)
    }

    @Test
    fun plus_matches_of() {
        val list = picks(3, "matt", now - 3 * day) + picks(5, null, now - 40 * day) +
            picks(2, "mx", now - day) + Pick("app:y", "matt", now)
        val folded = list.fold(PickIndex.EMPTY) { acc, p -> acc + p }.at(now)
        val built = PickIndex.of(list).at(now)
        for (q in listOf("matt", "mat", "mx", "zz", "")) {
            for (rid in listOf(id, "app:y", "app:none")) {
                assertEquals("$rid/$q", built.score(rid, q), folded.score(rid, q), 1e-9)
            }
        }
    }

    @Test
    fun ref_far_from_now_stays_finite() {
        val s = PickIndex.of(picks(3, "matt", now - 364 * day)).at(now)
        val score = s.score(id, "matt")
        assertTrue(score.isFinite())
        assertTrue(score >= 0.0)
    }
}
