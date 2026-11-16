package me.jhot.swiftcurrent.ui.applist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrubberMathTest {
    private val letters = listOf(ScrubberDefaults.STAR, '#') + ('A'..'E') + ScrubberDefaults.COG
    private val top = 100f
    private val bottom = 100f + 20f * letters.size // 20px per slot
    private val slot = (bottom - top) / letters.size

    private fun centerOf(i: Int) = top + slot * (i + 0.5f)

    // The draw path uses these two directly; the tests go through them too, so the two helpers the
    // app used to expose for tests only are gone.
    private fun letterAt(y: Float, trackTop: Float, trackBottom: Float, xs: List<Char>) =
        xs[ScrubberMath.indexAt(y, trackTop, trackBottom, xs.size)]

    private fun fisheye(xs: List<Char>, y: Float, trackTop: Float, trackBottom: Float, radius: Float) =
        List(xs.size) { ScrubberMath.offsetAt(it, xs.size, y, trackTop, trackBottom, radius) }

    @Test
    fun letterAt_maps_top_and_bottom_to_first_and_last_letter() {
        assertEquals(letters.first(), letterAt(top, top, bottom, letters))
        assertEquals(letters.last(), letterAt(bottom, top, bottom, letters))
        // Every slot centre maps to its own letter, so the spread is even.
        letters.indices.forEach { i ->
            assertEquals("slot $i", letters[i], letterAt(centerOf(i), top, bottom, letters))
        }
        // Slot edges belong to the slot below the line.
        assertEquals('#', letterAt(top + slot, top, bottom, letters))
        assertEquals(ScrubberDefaults.STAR, letterAt(top + slot - 0.01f, top, bottom, letters))
    }

    @Test
    fun letterAt_clamps_outside_the_track() {
        assertEquals(letters.first(), letterAt(top - 500f, top, bottom, letters))
        assertEquals(letters.first(), letterAt(Float.NEGATIVE_INFINITY, top, bottom, letters))
        assertEquals(letters.last(), letterAt(bottom + 500f, top, bottom, letters))
        assertEquals(letters.last(), letterAt(Float.POSITIVE_INFINITY, top, bottom, letters))
        // A collapsed track (zero height) still answers instead of dividing by zero.
        assertEquals(letters.first(), letterAt(top, top, top, letters))
    }

    @Test
    fun fisheye_peak_is_at_the_touch_and_decays_to_zero() {
        val radius = slot * 3f
        val touched = 4 // 'C'
        val offsets = fisheye(letters, centerOf(touched), top, bottom, radius)
        assertEquals(letters.size, offsets.size)
        assertEquals(1f, offsets[touched], 1e-6f)
        assertEquals(touched, offsets.indices.maxBy { offsets[it] })
        // Strictly falling away from the touch until the radius, then flat zero.
        for (i in touched + 1 until letters.size) {
            val d = (i - touched) * slot
            if (d >= radius) assertEquals("letter $i", 0f, offsets[i], 0f)
            else assertTrue("letter $i: ${offsets.toList()}", offsets[i] < offsets[i - 1] && offsets[i] > 0f)
        }
        for (i in touched - 1 downTo 0) {
            val d = (touched - i) * slot
            if (d >= radius) assertEquals("letter $i", 0f, offsets[i], 0f)
            else assertTrue("letter $i: $offsets", offsets[i] < offsets[i + 1] && offsets[i] > 0f)
        }
        // The radius really cuts off on both sides of this fixture.
        assertEquals(0f, offsets[0], 0f)
        assertEquals(0f, offsets.last(), 0f)
        // Smooth at the edge: just inside the radius the bend is nearly gone, not a step.
        val nearEdge = fisheye(letters, centerOf(0) + radius * 0.98f, top, bottom, radius)
        assertTrue("edge value ${nearEdge[0]}", nearEdge[0] in 0f..0.01f)
    }

    @Test
    fun fisheye_is_symmetric_around_the_touch() {
        val radius = slot * 4f
        val mid = 4 // three letters each side
        val offsets = fisheye(letters, centerOf(mid), top, bottom, radius)
        for (k in 1..3) assertEquals("k=$k", offsets[mid - k], offsets[mid + k], 1e-5f)
    }

    @Test
    fun fisheye_with_one_letter_does_not_divide_by_zero() {
        val one = listOf('A')
        val offsets = fisheye(one, 110f, 100f, 120f, 50f)
        assertEquals(listOf(1f), offsets)
        val collapsed = fisheye(one, 100f, 100f, 100f, 50f)
        assertTrue(collapsed.all { it.isFinite() })
        assertTrue(fisheye(letters, 150f, top, bottom, 0f).all { it == 0f || it == 1f })
        assertEquals(emptyList<Float>(), fisheye(emptyList(), 150f, top, bottom, 50f))
    }

    @Test
    fun fisheye_never_exceeds_the_peak() {
        val radius = slot * 5f
        var y = top - 200f
        while (y <= bottom + 200f) {
            val offsets = fisheye(letters, y, top, bottom, radius)
            assertTrue("y=$y $offsets", offsets.all { it in 0f..1f })
            // Wherever the finger is, the letter under it bends the most.
            val under = letters.indexOf(letterAt(y, top, bottom, letters))
            assertEquals("y=$y $offsets", offsets.max(), offsets[under], 1e-6f)
            y += 3.7f
        }
    }

    @Test
    fun onFirstSlot_is_the_star_slot_and_everything_above_it() {
        val n = letters.size
        assertTrue(ScrubberMath.onFirstSlot(centerOf(0), top, bottom, n))
        assertTrue(ScrubberMath.onFirstSlot(top, top, bottom, n))
        assertTrue(ScrubberMath.onFirstSlot(top + slot - 0.01f, top, bottom, n))
        // Reaching up past the star is the star, however far: this is how a swipe up to it really ends.
        assertTrue(ScrubberMath.onFirstSlot(top - 6f, top, bottom, n))
        assertTrue(ScrubberMath.onFirstSlot(top - 40f, top, bottom, n))
        assertTrue(ScrubberMath.onFirstSlot(top - 500f, top, bottom, n))
        // The line between slots belongs to the slot below it, exactly as letterAt maps it.
        assertTrue(!ScrubberMath.onFirstSlot(top + slot, top, bottom, n))
        assertTrue(!ScrubberMath.onFirstSlot(centerOf(1), top, bottom, n))
        assertTrue(!ScrubberMath.onFirstSlot(Float.NaN, top, bottom, n))
        assertTrue(!ScrubberMath.onFirstSlot(top, top, bottom, 0))
    }

    @Test
    fun a_lift_is_on_the_star_exactly_when_the_bubble_would_show_it() {
        val n = letters.size
        var y = top - 300f
        while (y < bottom + 50f) {
            val bubbleShowsStar = letters[ScrubberMath.indexAt(y, top, bottom, n)] == '\u2606'
            assertEquals("y=$y", bubbleShowsStar, ScrubberMath.onFirstSlot(y, top, bottom, n))
            y += 2.3f
        }
    }

    @Test
    fun onLastSlot_is_bounded_by_the_slot_and_tolerance() {
        val n = letters.size
        val tol = 6f
        assertTrue(ScrubberMath.onLastSlot(centerOf(n - 1), top, bottom, n, tol))
        assertTrue(ScrubberMath.onLastSlot(bottom - slot, top, bottom, n, tol))
        assertTrue(ScrubberMath.onLastSlot(bottom + tol, top, bottom, n, tol))
        // Unlike letterAt, no clamping: past the tolerance or in the slot above is a miss.
        assertTrue(!ScrubberMath.onLastSlot(bottom + tol + 0.5f, top, bottom, n, tol))
        assertTrue(!ScrubberMath.onLastSlot(bottom + 500f, top, bottom, n, tol))
        assertTrue(!ScrubberMath.onLastSlot(centerOf(n - 2), top, bottom, n, tol))
        assertTrue(!ScrubberMath.onLastSlot(Float.NaN, top, bottom, n, tol))
        assertTrue(!ScrubberMath.onLastSlot(bottom, top, bottom, 0, tol))
    }
}
