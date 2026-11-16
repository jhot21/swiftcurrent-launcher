package me.jhot.swiftcurrent.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticTuningTest {
    private val ticks = listOf(HapticTuning::scrub, HapticTuning::gesture)

    @Test
    fun zero_is_no_tick_at_all() {
        for (tuning in ticks) assertNull(tuning(0))
    }

    @Test
    fun the_top_of_the_slider_is_the_old_strong_value() {
        for (tuning in ticks) assertEquals(255, tuning(100)!!.amplitude)
    }

    @Test
    fun each_step_is_firmer_than_the_one_before_for_both_kinds_of_tick() {
        for (tuning in ticks) {
            val a = (5..100 step 5).map { tuning(it)!!.amplitude }
            assertEquals(a.sorted(), a)
            assertEquals(a.size, a.toSet().size)
        }
    }

    @Test
    fun every_amplitude_is_one_the_vibrator_accepts_even_for_stored_values_out_of_range() {
        for (strength in listOf(-10, 1, 20, 100, 500)) for (tuning in ticks) {
            val spec = tuning(strength) ?: continue
            assertTrue(spec.amplitude in 1..255)
            assertTrue(spec.millis > 0)
        }
        assertNotNull(HapticTuning.scrub(1))
    }

    @Test
    fun a_scrub_tick_is_shorter_than_the_search_buzz() {
        assertTrue(HapticTuning.scrub(50)!!.millis < HapticTuning.gesture(50)!!.millis)
    }
}
