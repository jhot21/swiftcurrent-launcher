package me.jhot.swiftcurrent.ui.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PullToCloseTrackerTest {
    private val threshold = 100f

    @Test
    fun a_drag_the_list_scrolls_never_closes() {
        val t = PullToCloseTracker(threshold)
        // The list consumed the whole drag each time: it was mid-scroll, not at the top.
        repeat(50) { assertFalse(t.onDrag(dy = 40f, listConsumedY = 40f)) }
    }

    @Test
    fun a_pull_at_the_top_past_the_threshold_closes_once_per_gesture() {
        val t = PullToCloseTracker(threshold)
        assertFalse(t.onDrag(60f, 0f))
        assertTrue(t.onDrag(60f, 0f))
        // The same drag keeps going: no second close.
        assertFalse(t.onDrag(60f, 0f))
        assertFalse(t.onDrag(60f, 0f))
    }

    @Test
    fun only_the_part_the_list_could_not_scroll_counts() {
        val t = PullToCloseTracker(threshold)
        // Scrolls back to the top during the drag, then pulls the rest.
        assertFalse(t.onDrag(90f, 90f))
        assertFalse(t.onDrag(90f, 60f)) // 30 leftover
        assertFalse(t.onDrag(60f, 0f)) // 90 total
        assertTrue(t.onDrag(20f, 0f)) // 110
    }

    @Test
    fun reversing_the_drag_resets_the_pull() {
        val t = PullToCloseTracker(threshold)
        assertFalse(t.onDrag(80f, 0f))
        assertFalse(t.onDrag(-10f, 0f))
        assertFalse(t.onDrag(80f, 0f)) // 80 again, not 160
        assertTrue(t.onDrag(30f, 0f))
    }

    @Test
    fun a_second_gesture_after_a_fling_can_close_again() {
        val t = PullToCloseTracker(threshold)
        assertTrue(t.onDrag(150f, 0f))
        t.onFlingEnd()
        assertFalse(t.onDrag(50f, 0f))
        assertTrue(t.onDrag(60f, 0f))
    }
}
