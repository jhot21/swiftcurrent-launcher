package me.jhot.swiftcurrent.lock

import org.junit.Assert.assertEquals
import org.junit.Test

class DoubleTapTest {
    private val timeout = 300L

    @Test
    fun first_tap_jumps_immediately_with_no_delay() {
        val d = DoubleTapDetector(timeout)
        assertEquals(TapOutcome.Jump('M'), d.onTap('M', 1_000))
    }

    @Test
    fun second_tap_within_timeout_locks() {
        val d = DoubleTapDetector(timeout)
        d.onTap('M', 1_000)
        assertEquals(TapOutcome.Lock, d.onTap('M', 1_000 + timeout))
    }

    @Test
    fun second_tap_up_to_three_letters_away_within_timeout_also_locks() {
        val d = DoubleTapDetector(timeout)
        d.onTap('M', 1_000)
        assertEquals(TapOutcome.Lock, d.onTap('P', 1_100))
        d.onTap('M', 2_000)
        assertEquals(TapOutcome.Lock, d.onTap('J', 2_100))
    }

    @Test
    fun second_tap_four_or_more_letters_away_is_a_new_jump_that_can_start_a_double_tap() {
        val d = DoubleTapDetector(timeout)
        d.onTap('M', 1_000)
        assertEquals(TapOutcome.Jump('Q'), d.onTap('Q', 1_100))
        assertEquals(TapOutcome.Lock, d.onTap('R', 1_200))
    }

    @Test
    fun the_hash_slot_counts_as_sitting_just_before_A() {
        val d = DoubleTapDetector(timeout)
        d.onTap('#', 1_000)
        assertEquals(TapOutcome.Lock, d.onTap('C', 1_100))
        d.onTap('#', 2_000)
        assertEquals(TapOutcome.Jump('D'), d.onTap('D', 2_100))
    }

    @Test
    fun second_tap_after_timeout_is_a_new_jump() {
        val d = DoubleTapDetector(timeout)
        d.onTap('M', 1_000)
        assertEquals(TapOutcome.Jump('M'), d.onTap('M', 1_000 + timeout + 1))
    }

    @Test
    fun after_a_lock_the_next_tap_is_a_fresh_jump() {
        val d = DoubleTapDetector(timeout)
        d.onTap('M', 1_000)
        d.onTap('M', 1_100)
        assertEquals(TapOutcome.Jump('M'), d.onTap('M', 1_150))
    }
}
