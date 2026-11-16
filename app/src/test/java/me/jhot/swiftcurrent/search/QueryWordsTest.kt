package me.jhot.swiftcurrent.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryWordsTest {
    @Test
    fun blank_query_has_no_words() {
        assertTrue(QueryWords.of("   ").isEmpty)
        assertTrue(QueryWords.of("").isEmpty)
    }

    @Test
    fun every_word_must_be_in_the_haystack_in_any_order() {
        val words = QueryWords.of("bed tasker")
        assertTrue(words.matches(Ranking.normalize("Tasker: Bedtime")))
        assertFalse(words.matches(Ranking.normalize("Tasker: Wake")))
    }

    @Test
    fun accents_and_case_are_folded_on_the_query_side() {
        assertTrue(QueryWords.of("CAFE").matches(Ranking.normalize("Café")))
        assertTrue(QueryWords.of("café").matches(Ranking.normalize("Cafe")))
    }

    @Test
    fun the_haystack_is_taken_as_already_folded() {
        // Folding the haystack is the caller's job, so it can be done once instead of per keystroke.
        assertFalse(QueryWords.of("cafe").matches("Café"))
    }

    @Test
    fun spacing_marks_are_not_accents() {
        // Devanagari vowel signs are spacing marks (Mc): stripping them would turn "कि" into "क".
        assertEquals("कि", Ranking.normalize("कि"))
    }
}
