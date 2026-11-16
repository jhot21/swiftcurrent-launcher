package me.jhot.swiftcurrent.ui.home

import android.content.Context
import android.view.ViewConfiguration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.jhot.swiftcurrent.WatchdogTimeout
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class HomeGesturesTest {
    @get:Rule
    val compose = createComposeRule()

    // A hang (an animation that never settles) fails the test instead of stalling the gate.
    @get:Rule
    val timeout = WatchdogTimeout(seconds = 60)

    private val rows = listOf("one", "two").map { HomeRow(Entry(it, Target.App("pkg.$it/.Main")), it, false) }
    private val popups = mutableListOf<String>()
    private val longs = mutableListOf<String>()
    private var search = 0
    private var appList = 0
    private var shade = 0
    private var tapped = mutableListOf<String>()

    private val consumedMoves = mutableListOf<Boolean>()

    /** Records the edge finger's life as "down y", "move y moved", "up y moved", "cancel"; y in whole pixels. */
    private class EdgeRecorder : EdgeTouch {
        val calls = mutableListOf<String>()
        override fun down(y: Float) { calls += "down ${y.toInt()}" }
        override fun move(y: Float, moved: Boolean) { calls += "move ${y.toInt()} $moved" }
        override fun up(y: Float, moved: Boolean) { calls += "up ${y.toInt()} $moved" }
        override fun cancel() { calls += "cancel" }
    }

    private val edge = EdgeRecorder()
    private val drag = HomeDrag()
    private var armedCount = 0

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val density = context.resources.displayMetrics.density
    private val travelPx get() = RUBBER_BAND_TRAVEL_ROWS * HomeLayoutDefaults.ROW_HEIGHT_DP * density

    private fun show(enabled: Boolean = true) = compose.setContent {
        HomeGestureHost(
            enabled = enabled,
            onSearch = { search++ }, onAppList = { appList++ },
            onItemPopup = { popups += it.id }, onItemLongPress = { longs += it.id },
            edge = edge, onShade = { shade++ },
            drag = drag, onArmed = { armedCount++ },
        ) {
            HomeScreen(
                HomeUiState("7:20", "Sun", null, rows, anyStored = rows.isNotEmpty()),
                onClock = {}, onAgenda = {}, onItem = { tapped += it.id },
            )
            // Sees events after the detector (Initial pass runs first) and records if they were taken.
            Box(
                Modifier.testTag("probe").size(100.dp).pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            if (e.type == PointerEventType.Move) consumedMoves += e.changes.any { it.isConsumed }
                        }
                    }
                },
            )
        }
    }

    private fun assertNothingElse(popup: List<String> = emptyList(), long: List<String> = emptyList(), tap: List<String> = emptyList(), search: Int = 0, shade: Int = 0) {
        assertEquals(popup, popups); assertEquals(long, longs); assertEquals(tap, tapped)
        assertEquals(search, this.search); assertEquals(0, appList); assertEquals(shade, this.shade)
    }

    @Test
    fun a_real_tap_reaches_only_the_row_tap_handler() {
        show()
        compose.onNodeWithTag("favorite-one").performTouchInput { click() }
        assertNothingElse(tap = listOf("one"))
    }

    @Test
    fun a_long_press_fires_only_the_long_press_callback() {
        show()
        compose.onNodeWithTag("favorite-one").performTouchInput { longClick() }
        assertNothingElse(long = listOf("one"))
    }

    @Test
    fun swipe_right_on_an_item_opens_its_popup() {
        show()
        compose.onNodeWithTag("favorite-two").performTouchInput {
            swipeRight(startX = 150f, endX = 700f)
        }
        assertNothingElse(popup = listOf("two"))
    }

    @Test
    fun a_tap_on_an_empty_area_does_nothing() {
        show()
        compose.onNodeWithTag("clock").performTouchInput { click(Offset(width - 5f, 2f)) }
        compose.onNodeWithTag("probe").performTouchInput { click() }
        assertNothingElse()
    }

    @Test
    fun swipe_right_off_any_item_does_nothing_and_swipe_up_opens_search() {
        show()
        compose.onNodeWithTag("clock").performTouchInput { swipe(Offset(150f, centerY), Offset(700f, centerY)) }
        assertEquals(emptyList<String>(), popups)
        compose.onNodeWithTag("favorite-one").performTouchInput { swipe(Offset(300f, centerY), Offset(300f, centerY - 600f)) }
        assertEquals(1, search)
        compose.onNodeWithTag("favorite-one").performTouchInput { swipe(Offset(width - 20f, centerY), Offset(width - 20f, centerY - 600f)) }
        assertEquals(1, appList)
    }

    @Test
    fun disabled_detector_consumes_nothing_and_fires_nothing() {
        show(enabled = false)
        compose.onNodeWithTag("favorite-one").performTouchInput { swipe(Offset(300f, centerY), Offset(300f, centerY - 600f)) }
        compose.onNodeWithTag("favorite-two").performTouchInput { swipeRight(startX = 150f, endX = 700f) }
        compose.onNodeWithTag("favorite-one").performTouchInput { longClick() }
        // Rows still see their own touches (their click ignores slop), but no gesture callback fires.
        assertEquals(emptyList<String>(), popups)
        assertEquals(emptyList<String>(), longs)
        assertEquals(0, search); assertEquals(0, appList)
        compose.onNodeWithTag("probe").performTouchInput { swipe(Offset(centerX, centerY), Offset(centerX, centerY - 80f)) }
        assertTrue(consumedMoves.isNotEmpty())
        assertFalse(consumedMoves.any { it })
    }

    @Test
    fun swipe_left_is_not_consumed_and_a_child_clickable_still_gets_its_tap() {
        show()
        compose.onNodeWithTag("probe").performTouchInput { swipeLeft(startX = centerX + 40f, endX = centerX - 40f) }
        assertTrue(consumedMoves.isNotEmpty())
        assertFalse(consumedMoves.any { it })
        compose.onNodeWithTag("favorite-one").performTouchInput { click() }
        assertNothingElse(tap = listOf("one"))
    }

    @Test
    fun swipe_down_pulls_the_shade_once() {
        show()
        compose.onNodeWithTag("probe").performTouchInput { swipeDown(startY = centerY - 40f, endY = centerY + 400f) }
        assertNothingElse(shade = 1)
    }

    @Test
    fun a_short_downward_move_pulls_nothing() {
        show()
        compose.onNodeWithTag("probe").performTouchInput { swipeDown(startY = centerY - 40f, endY = centerY + 40f) }
        assertNothingElse()
    }

    private fun assertRowNotTapped() {
        assertNothingElse()
    }

    @Test
    fun swipe_left_starting_on_a_favorite_does_not_tap_it_and_fires_nothing() {
        show()
        compose.onNodeWithTag("favorite-one").performTouchInput { swipeLeft(startX = 600f, endX = 200f) }
        assertRowNotTapped()
    }

    @Test
    fun swipe_down_starting_on_a_favorite_pulls_the_shade_and_does_not_tap_it() {
        show()
        compose.onNodeWithTag("favorite-one").performTouchInput { swipeDown(startY = centerY - 40f, endY = centerY + 400f) }
        assertNothingElse(shade = 1)
    }

    @Test
    fun diagonal_drag_starting_on_a_favorite_does_not_tap_it_and_fires_nothing() {
        show()
        compose.onNodeWithTag("favorite-one").performTouchInput { swipe(Offset(400f, centerY + 10f), Offset(270f, centerY - 10f)) }
        assertRowNotTapped()
    }

    @Test
    fun enabled_flipping_to_false_mid_gesture_still_consumes_the_release() {
        // What the shell does: the long press opens an overlay, which turns the detector off while the finger is down.
        val enabled = mutableStateOf(true)
        compose.setContent {
            HomeGestureHost(
                enabled = enabled.value,
                onSearch = { search++ }, onAppList = { appList++ },
                onItemPopup = { popups += it.id }, onItemLongPress = { longs += it.id },
            ) {
                HomeScreen(HomeUiState("7:20", "Sun", null, rows, anyStored = rows.isNotEmpty()), onClock = {}, onAgenda = {}, onItem = { tapped += it.id })
            }
        }
        compose.onNodeWithTag("favorite-one").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_500)
        assertEquals(listOf("one"), longs)
        compose.runOnUiThread { enabled.value = false }
        compose.waitForIdle()
        compose.onNodeWithTag("favorite-one").performTouchInput { up() }
        compose.waitForIdle()
        // The release was consumed by the gesture that began while enabled: the row under the finger was not tapped.
        assertEquals(emptyList<String>(), tapped)
        assertEquals(listOf("one"), longs)
    }

    @Test
    fun vertical_scroll_in_a_child_works_when_detector_is_disabled() {
        val scroll = androidx.compose.foundation.ScrollState(0)
        compose.setContent {
            HomeGestureHost({ search++ }, { appList++ }, { popups += it.id }, { longs += it.id }, enabled = false) {
                Column(Modifier.fillMaxSize().testTag("list").verticalScroll(scroll)) {
                    repeat(60) { BasicText("row $it", Modifier.height(48.dp)) }
                }
            }
        }
        compose.onNodeWithTag("list").performTouchInput { swipe(Offset(centerX, centerY + 200f), Offset(centerX, centerY - 200f)) }
        assertTrue("child should scroll", scroll.value > 0)
        assertEquals(0, search)
    }

    @Test
    fun an_edge_touch_down_opens_the_list_and_hands_over_the_finger_before_any_movement() {
        show()
        val root = compose.onRoot()
        root.performTouchInput { down(Offset(width * 0.95f, 1200f)) }
        compose.waitForIdle()
        assertEquals(1, appList)
        assertEquals(listOf("down 1200"), edge.calls)
        root.performTouchInput { moveTo(Offset(width * 0.95f, 1190f)) }
        root.performTouchInput { moveTo(Offset(width * 0.95f, 900f)) }
        root.performTouchInput { moveTo(Offset(width * 0.95f, 600f)) }
        root.performTouchInput { up() }
        compose.waitForIdle()
        // Within slop the move is still forwarded (the letter follows) but does not count as moved.
        assertEquals(listOf("down 1200", "move 1190 false", "move 900 true", "move 600 true", "up 600 true"), edge.calls)
        assertEquals(1, appList)
        assertEquals(0, search)
    }

    @Test
    fun a_stationary_tap_on_the_strip_reports_an_unmoved_lift() {
        show()
        compose.onRoot().performTouchInput { down(Offset(width * 0.9f, 1200f)); up() }
        compose.waitForIdle()
        assertEquals(listOf("down 1200", "up 1200 false"), edge.calls)
    }

    @Test
    fun a_cancelled_edge_finger_is_reported_as_cancelled() {
        show()
        compose.onRoot().performTouchInput { down(Offset(width * 0.95f, 1200f)) }
        compose.onRoot().performTouchInput { moveTo(Offset(width * 0.95f, 900f)) }
        compose.onRoot().performTouchInput { cancel() }
        compose.waitForIdle()
        assertEquals(listOf("down 1200", "move 900 true", "cancel"), edge.calls)
    }

    @Test
    fun the_strip_is_reserved_so_a_tap_on_a_favorite_there_does_not_launch_it() {
        show()
        compose.onNodeWithTag("favorite-one").performTouchInput { click(Offset(width - 10f, centerY)) }
        compose.onNodeWithTag("favorite-one").performTouchInput { swipe(Offset(width - 10f, centerY), Offset(width - 10f, centerY - 600f)) }
        compose.onNodeWithTag("favorite-one").performTouchInput { longClick(Offset(width - 10f, centerY)) }
        compose.waitForIdle()
        assertEquals(emptyList<String>(), tapped)
        assertEquals(emptyList<String>(), longs)
        assertEquals(3, appList)
        // Just left of the strip the row is an ordinary row again.
        compose.onNodeWithTag("favorite-one").performTouchInput { click(Offset(width * 0.8f, centerY)) }
        assertEquals(listOf("one"), tapped)
    }

    @Test
    fun a_disabled_host_never_starts_an_edge_scrub() {
        show(enabled = false)
        compose.onRoot().performTouchInput { down(Offset(width * 0.95f, 1200f)); moveTo(Offset(width * 0.95f, 600f)); up() }
        compose.waitForIdle()
        assertEquals(0, appList)
        assertTrue(edge.calls.isEmpty())
    }

    @Test
    fun swipe_up_opens_search_on_the_lift_not_at_the_threshold() {
        show()
        val root = compose.onRoot()
        val x = 400f
        root.performTouchInput { down(Offset(x, 1500f)) }
        // Past touch slop but short of the 48dp threshold (144px here): nothing.
        root.performTouchInput { moveTo(Offset(x, 1500f - 100f)) }
        compose.waitForIdle()
        assertEquals(0, search)
        // Past the threshold with the finger still down: still nothing, the lift decides.
        root.performTouchInput { moveTo(Offset(x, 1500f - 200f)) }
        compose.waitForIdle()
        assertEquals(0, search)
        root.performTouchInput { moveTo(Offset(x, 1500f - 600f)) }
        compose.waitForIdle()
        assertEquals(0, search)
        root.performTouchInput { up() }
        compose.waitForIdle()
        assertNothingElse(search = 1)
    }

    @Test
    fun swipe_down_waits_for_the_lift_to_pull_the_shade() {
        show()
        val root = compose.onRoot()
        root.performTouchInput { down(Offset(400f, 600f)) }
        root.performTouchInput { moveTo(Offset(400f, 600f + 400f)) }
        compose.waitForIdle()
        assertEquals(0, shade)
        root.performTouchInput { up() }
        compose.waitForIdle()
        assertNothingElse(shade = 1)
    }

    @Test
    fun lifting_after_dropping_back_under_the_threshold_does_nothing() {
        show()
        val root = compose.onRoot()
        root.performTouchInput { down(Offset(400f, 1500f)) }
        root.performTouchInput { moveTo(Offset(400f, 1500f - 400f)) }
        root.performTouchInput { moveTo(Offset(400f, 1500f - 60f)) }
        root.performTouchInput { up() }
        compose.waitForIdle()
        assertNothingElse()
    }

    @Test
    fun a_cancelled_swipe_fires_nothing() {
        show()
        val root = compose.onRoot()
        root.performTouchInput { down(Offset(400f, 1500f)) }
        root.performTouchInput { moveTo(Offset(400f, 1500f - 400f)) }
        root.performTouchInput { cancel() }
        compose.waitForIdle()
        assertNothingElse()
        root.performTouchInput { down(Offset(400f, 600f)) }
        root.performTouchInput { moveTo(Offset(400f, 600f + 400f)) }
        root.performTouchInput { cancel() }
        compose.waitForIdle()
        assertNothingElse()
    }

    @Test
    fun a_swipe_starting_on_a_favorite_still_never_taps_it() {
        show()
        val row = compose.onNodeWithTag("favorite-one")
        row.performTouchInput { down(Offset(300f, centerY)) }
        row.performTouchInput { moveTo(Offset(300f, centerY - 300f)) }
        compose.waitForIdle()
        assertEquals(0, search)
        // The finger comes back onto the row before lifting: still no tap, and nothing opens.
        row.performTouchInput { moveTo(Offset(300f, centerY)) }
        row.performTouchInput { up() }
        compose.waitForIdle()
        assertNothingElse()
    }

    @Test
    fun search_fires_once_on_the_lift_after_a_wobble() {
        show()
        val root = compose.onRoot()
        root.performTouchInput { down(Offset(400f, 1500f)) }
        root.performTouchInput { moveTo(Offset(400f, 1200f)) }
        root.performTouchInput { moveTo(Offset(400f, 1490f)) }
        root.performTouchInput { moveTo(Offset(400f, 900f)) }
        assertEquals(0, search)
        root.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(1, search)
    }

    private fun held(fromY: Float, dy: Float, x: Float = 400f) {
        val root = compose.onRoot()
        root.performTouchInput { down(Offset(x, fromY)) }
        root.performTouchInput { moveTo(Offset(x, fromY + dy)) }
        compose.waitForIdle()
    }

    @Test
    fun the_block_follows_the_curve_up() {
        show()
        held(1500f, -60f)
        assertEquals(-rubberBand(60f - slop, travelPx), drag.offsetPx, 1f)
        compose.onRoot().performTouchInput { moveTo(Offset(400f, 1500f - 300f)) }
        compose.waitForIdle()
        assertEquals(-rubberBand(300f - slop, travelPx), drag.offsetPx, 1f)
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
    }

    @Test
    fun the_block_follows_the_curve_down() {
        show()
        held(600f, 60f)
        assertEquals(rubberBand(60f - slop, travelPx), drag.offsetPx, 1f)
        compose.onRoot().performTouchInput { moveTo(Offset(400f, 600f + 300f)) }
        compose.waitForIdle()
        assertEquals(rubberBand(300f - slop, travelPx), drag.offsetPx, 1f)
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
    }

    @Test
    fun the_block_resists_a_long_pull() {
        show()
        held(1500f, -300f)
        // 300 px of finger, far less of block.
        assertTrue("moved ${drag.offsetPx}", drag.offsetPx > -travelPx && drag.offsetPx < -60f)
    }

    @Test
    fun the_block_never_exceeds_its_travel() {
        show()
        held(2300f, -2000f)
        assertTrue("offset ${drag.offsetPx} travel $travelPx", abs(drag.offsetPx) <= travelPx + 0.01f)
        assertTrue(abs(drag.offsetPx) > travelPx - 1f)
    }

    @Test
    fun arming_ticks_once_per_crossing() {
        show()
        val root = compose.onRoot()
        root.performTouchInput { down(Offset(400f, 1500f)) }
        root.performTouchInput { moveTo(Offset(400f, 1500f - 100f)) }
        assertEquals(0, armedCount)
        root.performTouchInput { moveTo(Offset(400f, 1500f - 200f)) }
        assertEquals(1, armedCount)
        root.performTouchInput { moveTo(Offset(400f, 1500f - 400f)) }
        assertEquals(1, armedCount)
        root.performTouchInput { moveTo(Offset(400f, 1500f - 60f)) }
        assertEquals(1, armedCount)
        root.performTouchInput { moveTo(Offset(400f, 1500f - 300f)) }
        assertEquals(2, armedCount)
        root.performTouchInput { up() }
        compose.waitForIdle()
    }

    @Test
    fun the_block_returns_to_rest_after_the_lift() {
        show()
        held(1500f, -300f)
        assertTrue(drag.offsetPx < -1f)
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(0f, drag.offsetPx, 0f)
    }

    @Test
    fun a_cancelled_swipe_returns_the_block_to_rest() {
        show()
        held(1500f, -300f)
        compose.onRoot().performTouchInput { cancel() }
        compose.waitForIdle()
        assertEquals(0f, drag.offsetPx, 0f)
    }

    @Test
    fun a_horizontal_turn_returns_the_block_to_rest() {
        show()
        held(1500f, -300f)
        assertTrue(drag.offsetPx < -1f)
        compose.onRoot().performTouchInput { moveTo(Offset(400f + 900f, 1500f - 300f)) }
        compose.waitForIdle()
        assertEquals(0f, drag.offsetPx, 0f)
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
    }

    @Test
    fun swipe_right_never_moves_the_block() {
        show()
        held(1500f, 0f)
        compose.onRoot().performTouchInput { moveTo(Offset(400f + 400f, 1500f)) }
        compose.waitForIdle()
        assertEquals(0f, drag.offsetPx, 0f)
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
    }

    @Test
    fun the_edge_strip_never_moves_the_block() {
        show()
        held(1500f, -300f, x = 1233f * 0.95f)
        assertEquals(0f, drag.offsetPx, 0f)
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(1, appList)
    }

    @Test
    fun a_vertical_drag_that_becomes_a_right_swipe_still_opens_the_right_rows_popup() {
        show()
        val row = compose.onNodeWithTag("favorite-one")
        row.performTouchInput { down(center) }
        row.performTouchInput { moveTo(Offset(center.x, center.y - 150f)) }
        compose.waitForIdle()
        assertTrue(drag.offsetPx < -1f)
        row.performTouchInput { moveTo(Offset(center.x + 500f, center.y - 150f)) }
        row.performTouchInput { up() }
        compose.waitForIdle()
        // The block went back to rest before the lookup, so the row's recorded bounds were still right.
        assertEquals(listOf("one"), popups)
    }

    @Test
    fun a_wobble_near_the_diagonal_neither_snaps_the_block_nor_ticks_again() {
        show()
        val root = compose.onRoot()
        root.performTouchInput { down(Offset(400f, 1500f)) }
        root.performTouchInput { moveTo(Offset(400f, 1500f - 300f)) }
        assertEquals(1, armedCount)
        val pulled = drag.offsetPx
        assertTrue(pulled < -1f)
        // Sideways draws level and just past vertical: the block stays put and nothing ticks.
        for (dx in listOf(310f, 290f, 315f, 280f)) {
            root.performTouchInput { moveTo(Offset(400f + dx, 1500f - 300f)) }
            assertEquals("dx $dx", pulled, drag.offsetPx, 0.01f)
            assertEquals("dx $dx", 1, armedCount)
        }
        root.performTouchInput { up() }
        compose.waitForIdle()
    }

    @Test
    fun jitter_around_the_threshold_does_not_tick_again_but_a_real_drop_does() {
        show()
        val root = compose.onRoot()
        root.performTouchInput { down(Offset(400f, 1500f)) }
        root.performTouchInput { moveTo(Offset(400f, 1500f - 150f)) }
        assertEquals(1, armedCount)
        // The 48dp threshold is 144 px here: 140 is a hair under it, 150 a hair over; neither is a new crossing.
        for (dy in listOf(140f, 150f, 141f, 152f)) {
            root.performTouchInput { moveTo(Offset(400f, 1500f - dy)) }
            assertEquals("dy $dy", 1, armedCount)
        }
        // A real drop well under the threshold re-arms.
        root.performTouchInput { moveTo(Offset(400f, 1500f - 60f)) }
        root.performTouchInput { moveTo(Offset(400f, 1500f - 200f)) }
        assertEquals(2, armedCount)
        root.performTouchInput { up() }
        compose.waitForIdle()
    }

    @Test
    fun a_relayout_during_a_drag_does_not_leave_the_rows_registered_where_the_block_was_pulled_to() {
        val clock = mutableStateOf("7:20")
        compose.setContent {
            HomeGestureHost(
                onSearch = { search++ }, onAppList = { appList++ },
                onItemPopup = { popups += it.id }, onItemLongPress = { longs += it.id },
                drag = drag,
            ) {
                HomeScreen(HomeUiState(clock.value, "Sun", null, rows, anyStored = rows.isNotEmpty()), onClock = {}, onAgenda = {}, onItem = { tapped += it.id })
            }
        }
        val rowBounds = compose.onNodeWithTag("favorite-one").getBoundsInRoot()
        // Just above the row's bottom edge, where the row below would answer if the registry were off by the pull.
        val y = rowBounds.bottom.value * density - 15f
        held(1500f, -300f)
        assertTrue(drag.offsetPx < -1f)
        // A relayout of the whole block while it is pulled up (the clock text changes width).
        compose.runOnUiThread { clock.value = "10:00:00 PM" }
        compose.waitForIdle()
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(0f, drag.offsetPx, 0f)
        compose.onRoot().performTouchInput { down(Offset(300f, y)) }
        compose.onRoot().performTouchInput { moveTo(Offset(900f, y)) }
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(listOf("one"), popups)
    }

    @Test
    fun a_drag_recognised_during_the_return_snaps_the_block_and_follows() {
        show()
        compose.mainClock.autoAdvance = false
        val root = compose.onRoot()
        root.performTouchInput { down(Offset(400f, 1500f)) }
        root.performTouchInput { moveTo(Offset(400f, 1500f - 300f)) }
        root.performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(DRAG_RELEASE_MS / 2L)
        val returning = drag.offsetPx
        assertTrue("mid-return, got $returning", returning < -1f && returning > -rubberBand(300f - slop, travelPx))
        // The press alone leaves the block returning; the first recognised move takes over from rest.
        root.performTouchInput { down(Offset(400f, 1500f)) }
        assertTrue(drag.offsetPx < -1f)
        root.performTouchInput { moveTo(Offset(400f, 1500f - 100f)) }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(-rubberBand(100f - slop, travelPx), drag.offsetPx, 1f)
        root.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    @Test
    fun enabled_false_mid_swipe_still_lets_the_lift_act_and_returns_the_block_to_rest() {
        val enabled = mutableStateOf(true)
        compose.setContent {
            HomeGestureHost(
                enabled = enabled.value,
                onSearch = { search++ }, onAppList = { appList++ },
                onItemPopup = { popups += it.id }, onItemLongPress = { longs += it.id },
                drag = drag,
            ) {
                HomeScreen(HomeUiState("7:20", "Sun", null, rows, anyStored = rows.isNotEmpty()), onClock = {}, onAgenda = {}, onItem = { tapped += it.id })
            }
        }
        held(1500f, -300f)
        assertTrue(drag.offsetPx < -1f)
        compose.runOnUiThread { enabled.value = false }
        compose.waitForIdle()
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(1, search)
        assertEquals(0f, drag.offsetPx, 0f)
    }

    @Test
    fun the_block_moves_the_content_on_screen() {
        show()
        val before = compose.onNodeWithTag("clock").getBoundsInRoot().top.value
        held(1500f, -300f)
        val after = compose.onNodeWithTag("clock").getBoundsInRoot().top.value
        assertEquals(drag.offsetPx / density, after - before, 1f)
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
    }
}
