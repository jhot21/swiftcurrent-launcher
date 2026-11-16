package me.jhot.swiftcurrent.ui.home

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HomeDragTest {
    @get:Rule
    val compose = createComposeRule()

    private val drag = HomeDrag()
    private lateinit var scope: CoroutineScope

    // An Animatable needs the composition's frame clock; paused so each frame is stepped by hand.
    @Before
    fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent { scope = rememberCoroutineScope() }
        compose.mainClock.advanceTimeByFrame()
    }

    // Left paused, the clock would leak into whatever test runs next in the same JVM.
    @After
    fun resumeClock() {
        compose.mainClock.autoAdvance = true
    }

    private fun follow(px: Float) = compose.runOnUiThread { drag.follow(scope, px) }

    private fun release() {
        compose.runOnUiThread { drag.release(scope) }
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun follow_sets_the_offset_at_once() {
        follow(40f)
        assertEquals(40f, drag.offsetPx, 0f)
    }

    @Test
    fun release_returns_to_rest_over_DRAG_RELEASE_MS() {
        follow(40f)
        release()
        compose.mainClock.advanceTimeBy(DRAG_RELEASE_MS / 2L)
        assertTrue("half way, got ${drag.offsetPx}", drag.offsetPx > 0f && drag.offsetPx < 40f)
        compose.mainClock.advanceTimeBy(DRAG_RELEASE_MS + 64L)
        assertEquals(0f, drag.offsetPx, 0f)
    }

    @Test
    fun a_follow_during_a_release_cancels_it_and_wins() {
        follow(40f)
        release()
        compose.mainClock.advanceTimeBy(DRAG_RELEASE_MS / 2L)
        follow(40f)
        compose.mainClock.advanceTimeBy(DRAG_RELEASE_MS + 64L)
        assertEquals(40f, drag.offsetPx, 0f)
    }

    @Test
    fun release_from_rest_is_a_no_op() {
        release()
        compose.mainClock.advanceTimeBy(DRAG_RELEASE_MS + 64L)
        assertEquals(0f, drag.offsetPx, 0f)
    }
}
