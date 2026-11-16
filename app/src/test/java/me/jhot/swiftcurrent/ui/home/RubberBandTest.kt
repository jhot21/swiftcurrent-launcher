package me.jhot.swiftcurrent.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RubberBandTest {
    @Test
    fun no_distance_gives_no_movement() {
        assertEquals(0f, rubberBand(0f, 34f), 0f)
    }

    @Test
    fun small_distances_follow_the_finger() {
        assertEquals(1f, rubberBand(1f, 100f), 0.01f)
    }

    @Test
    fun it_is_strictly_increasing_over_the_useful_range() {
        val max = 100.8f
        var previous = rubberBand(0f, max)
        for (d in 10..300 step 10) {
            val now = rubberBand(d.toFloat(), max)
            assertTrue("at $d: $now should exceed $previous", now > previous)
            previous = now
        }
    }

    @Test
    fun it_never_exceeds_the_maximum_and_approaches_it() {
        val v = rubberBand(100_000f, 34f)
        assertTrue("got $v", v <= 34f)
        assertEquals(34f, v, 0.01f)
    }

    @Test
    fun at_48_of_distance_it_is_about_three_quarters_of_a_34_travel() {
        assertEquals(25.7f, rubberBand(48f, 34f), 0.1f)
    }

    @Test
    fun a_negative_distance_or_no_travel_gives_nothing() {
        assertEquals(0f, rubberBand(-5f, 34f), 0f)
        assertEquals(0f, rubberBand(50f, 0f), 0f)
        assertEquals(0f, rubberBand(50f, -3f), 0f)
    }
}
