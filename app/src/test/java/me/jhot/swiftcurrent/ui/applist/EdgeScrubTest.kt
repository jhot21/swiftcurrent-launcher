package me.jhot.swiftcurrent.ui.applist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeScrubTest {
    private class Sink : ScrubSink {
        val calls = mutableListOf<String>()
        override fun down(y: Float) { calls += "down $y" }
        override fun move(y: Float) { calls += "move $y" }
        override fun up(y: Float) { calls += "up $y" }
        override fun cancel() { calls += "cancel" }
    }

    private val scrub = EdgeScrub()
    private val sink = Sink()

    @Test
    fun the_finger_landing_before_the_list_exists_is_replayed_as_a_fresh_press() {
        scrub.press(100f)
        assertTrue(scrub.active)
        assertFalse(scrub.moved)
        scrub.attach(sink)
        assertEquals(listOf("down 100.0"), sink.calls)
    }

    @Test
    fun moves_before_the_list_exists_replay_only_the_latest_position() {
        scrub.press(100f)
        scrub.move(150f, moved = false)
        scrub.move(300f, moved = true)
        scrub.attach(sink)
        assertEquals(listOf("down 100.0", "move 300.0"), sink.calls)
    }

    @Test
    fun once_attached_every_move_is_forwarded_live() {
        scrub.attach(sink)
        scrub.press(100f)
        scrub.move(120f, moved = false)
        scrub.move(400f, moved = true)
        assertEquals(listOf("down 100.0", "move 120.0", "move 400.0"), sink.calls)
    }

    @Test
    fun the_finger_counts_as_moved_only_once_it_passes_touch_slop() {
        scrub.press(100f)
        scrub.move(110f, moved = false)
        assertFalse(scrub.moved)
        scrub.move(300f, moved = true)
        assertTrue(scrub.moved)
        // A new press starts unmoved again.
        scrub.release(300f)
        scrub.press(50f)
        assertFalse(scrub.moved)
    }

    @Test
    fun a_release_before_the_list_exists_still_lands_the_jump_and_the_lift() {
        scrub.press(100f)
        scrub.move(300f, moved = true)
        scrub.release(320f)
        assertFalse(scrub.active)
        assertTrue(scrub.moved)
        scrub.attach(sink)
        assertEquals(listOf("down 100.0", "move 320.0", "up 320.0"), sink.calls)
        // Replayed once only.
        scrub.detach(sink)
        scrub.attach(sink)
        assertEquals(3, sink.calls.size)
    }

    @Test
    fun a_release_lifts_the_attached_scrubber() {
        scrub.attach(sink)
        scrub.press(100f)
        scrub.release(100f)
        assertEquals(listOf("down 100.0", "up 100.0"), sink.calls)
        assertFalse(scrub.active)
    }

    @Test
    fun cancel_releases_the_scrubber_without_a_lift() {
        scrub.attach(sink)
        scrub.press(100f)
        scrub.cancel()
        assertEquals(listOf("down 100.0", "cancel"), sink.calls)
        assertFalse(scrub.active)
        // Nothing stays pending for a later attach.
        val late = Sink()
        scrub.attach(late)
        assertTrue(late.calls.isEmpty())
    }

    @Test
    fun cancel_before_the_list_exists_replays_nothing() {
        scrub.press(100f)
        scrub.cancel()
        scrub.attach(sink)
        assertTrue(sink.calls.isEmpty())
    }

    @Test
    fun events_after_detach_go_nowhere_and_a_new_scrubber_picks_up_the_live_finger() {
        scrub.attach(sink)
        scrub.press(100f)
        scrub.detach(sink)
        scrub.move(200f, moved = true)
        assertEquals(listOf("down 100.0"), sink.calls)
        val next = Sink()
        scrub.attach(next)
        assertEquals(listOf("down 100.0", "move 200.0"), next.calls)
    }

    @Test
    fun a_scrubber_that_attaches_with_no_finger_down_gets_nothing() {
        scrub.attach(sink)
        assertTrue(sink.calls.isEmpty())
        assertFalse(scrub.active)
        assertFalse(scrub.moved)
    }
}
