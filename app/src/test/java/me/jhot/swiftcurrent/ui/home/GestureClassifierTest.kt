package me.jhot.swiftcurrent.ui.home

import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.Popup
import me.jhot.swiftcurrent.core.PopupRow
import me.jhot.swiftcurrent.core.Target
import org.junit.Assert.assertEquals
import org.junit.Test

class GestureClassifierTest {
    private val w = 1000f
    private val h = 2000f
    private val inset = 60f
    private val back = 60f

    private fun classify(startX: Float, startY: Float, dx: Float, dy: Float) =
        GestureClassifier.classify(startX, startY, dx, dy, w, h, inset, back)

    @Test
    fun swipe_up_in_the_middle_opens_search() {
        assertEquals(HomeGesture.OpenSearch, classify(400f, 1200f, 10f, -400f))
    }

    @Test
    fun swipe_up_starting_in_the_right_15_percent_opens_app_list() {
        assertEquals(HomeGesture.OpenAppListScrub, classify(900f, 1200f, 0f, -400f))
        // Just left of the strip is still search.
        assertEquals(HomeGesture.OpenSearch, classify(849f, 1200f, 0f, -400f))
    }

    @Test
    fun swipe_up_starting_in_the_bottom_inset_is_none() {
        assertEquals(HomeGesture.None, classify(400f, h - 10f, 0f, -400f))
    }

    @Test
    fun swipe_right_starting_near_the_left_edge_is_none() {
        assertEquals(HomeGesture.None, classify(10f, 800f, 400f, 0f))
        assertEquals(HomeGesture.OpenPopup, classify(100f, 800f, 400f, 0f))
    }

    @Test
    fun a_clear_downward_drag_opens_the_shade() {
        assertEquals(HomeGesture.OpenShade, classify(400f, 800f, 10f, 400f))
        assertEquals(HomeGesture.OpenShade, classify(900f, 800f, 0f, 400f))
        assertEquals(HomeGesture.None, classify(400f, 800f, 300f, 300f))
    }

    @Test
    fun short_or_diagonal_moves_are_none() {
        assertEquals(HomeGesture.None, classify(400f, 800f, 5f, -10f))
        assertEquals(HomeGesture.None, classify(400f, 800f, 300f, -300f))
        assertEquals(HomeGesture.None, classify(400f, 800f, -400f, 0f))
    }

    private fun edge(x: Float, y: Float) = GestureClassifier.isEdgeStart(x, y, w, h, inset)

    private fun armed(startX: Float, startY: Float, dx: Float, dy: Float, threshold: Float = 48f) =
        GestureClassifier.armed(startX, startY, dx, dy, w, h, inset, back, threshold)

    @Test
    fun a_touch_exactly_at_85_percent_of_the_width_is_in_the_strip() {
        assertEquals(true, edge(850f, 1000f))
        assertEquals(false, edge(849.9f, 1000f))
        assertEquals(true, edge(999f, 1000f))
        assertEquals(false, edge(0f, 1000f))
    }

    @Test
    fun the_strip_is_the_same_one_the_classifier_sends_to_the_app_list() {
        for (x in listOf(849.9f, 850f, 900f)) {
            assertEquals(edge(x, 1200f), classify(x, 1200f, 0f, -400f) == HomeGesture.OpenAppListScrub)
        }
    }

    @Test
    fun a_touch_in_the_bottom_inset_is_never_an_edge_start() {
        assertEquals(false, edge(950f, h - inset))
        assertEquals(false, edge(950f, h - 1f))
        assertEquals(true, edge(950f, h - inset - 0.1f))
        assertEquals(true, edge(950f, 0f))
    }

    @Test
    fun search_arms_exactly_at_the_threshold_and_not_before() {
        assertEquals(false, armed(400f, 1200f, 0f, -47.9f))
        assertEquals(true, armed(400f, 1200f, 0f, -48f))
        assertEquals(true, armed(400f, 1200f, 0f, -48f, threshold = 48f))
        assertEquals(false, armed(400f, 1200f, 0f, -100f, threshold = 120f))
    }

    @Test
    fun search_needs_a_mostly_vertical_upward_move() {
        // An exact 45 degree diagonal is not "mostly up".
        assertEquals(false, armed(400f, 1200f, 100f, -100f))
        assertEquals(false, armed(400f, 1200f, -100f, -100f))
        assertEquals(true, armed(400f, 1200f, 99f, -100f))
        assertEquals(true, armed(400f, 1200f, -99f, -100f))
        // Pulling down is the shade (armed too, tested below); a swipe right is neither.
        assertEquals(false, armed(400f, 1200f, 400f, 0f))
    }

    @Test
    fun search_never_arms_from_the_strip_or_the_bottom_inset() {
        assertEquals(false, armed(850f, 1200f, 0f, -400f))
        assertEquals(true, armed(849.9f, 1200f, 0f, -400f))
        assertEquals(false, armed(400f, h - inset, 0f, -400f))
        assertEquals(true, armed(400f, h - inset - 0.1f, 0f, -400f))
    }

    @Test
    fun search_from_near_the_left_back_margin_still_armed() {
        // The back margin only guards swipes right; a swipe up there is still search.
        assertEquals(true, armed(10f, 1200f, 0f, -400f))
        assertEquals(true, armed(back, 1200f, 0f, -400f))
    }

    @Test
    fun the_shade_arms_exactly_at_the_threshold_and_needs_a_mostly_vertical_pull() {
        assertEquals(false, armed(400f, 800f, 0f, 47.9f))
        assertEquals(true, armed(400f, 800f, 0f, 48f))
        // An exact 45 degree diagonal is not "mostly down".
        assertEquals(false, armed(400f, 800f, 48f, 48f))
        assertEquals(true, armed(400f, 800f, 47f, 48f))
    }

    @Test
    fun the_shade_does_not_care_about_the_bottom_inset_or_the_strip() {
        assertEquals(true, armed(400f, h - 10f, 0f, 400f))
        assertEquals(true, armed(900f, 800f, 0f, 400f))
    }

    @Test
    fun a_swipe_right_or_a_short_drag_is_not_armed() {
        assertEquals(false, armed(400f, 800f, 400f, 0f))
        assertEquals(false, armed(400f, 800f, 0f, 0f))
    }

    private fun band(startY: Float, dx: Float, dy: Float, wasVertical: Boolean = false) =
        GestureClassifier.bandDistance(startY, dx, dy, h, inset, wasVertical)

    @Test
    fun the_block_follows_only_a_mostly_vertical_drag() {
        assertEquals(-200f, band(1200f, 10f, -200f), 0f)
        assertEquals(200f, band(800f, -10f, 200f), 0f)
        // An exact diagonal and a horizontal drag leave the block at rest.
        assertEquals(0f, band(800f, 200f, 200f), 0f)
        assertEquals(0f, band(800f, 300f, 20f), 0f)
    }

    @Test
    fun a_vertical_drag_survives_a_little_sideways_drift_but_not_a_clear_lead() {
        // Entering needs vertical to lead; staying only needs sideways not to lead by a clear margin.
        assertEquals(0f, band(1200f, 110f, -100f, wasVertical = false), 0f)
        assertEquals(-100f, band(1200f, 110f, -100f, wasVertical = true), 0f)
        assertEquals(-100f, band(1200f, 124f, -100f, wasVertical = true), 0f)
        assertEquals(0f, band(1200f, 126f, -100f, wasVertical = true), 0f)
    }

    @Test
    fun a_swipe_up_from_under_the_system_gesture_strip_does_not_move_the_block() {
        assertEquals(0f, band(h - inset, 0f, -200f), 0f)
        assertEquals(-200f, band(h - inset - 0.1f, 0f, -200f), 0f)
        // Pulling down from there is still the shade.
        assertEquals(200f, band(h - inset, 0f, 200f), 0f)
    }

    private val app = Entry("a", Target.App("pkg.a/.Main"))

    @Test
    fun folder_tap_opens_popup_and_app_tap_launches() {
        assertEquals(TapAction.OpenPopup, tapAction(Entry("f", Target.Folder, popup = Popup(emptyList()))))
        assertEquals(TapAction.Launch("pkg.a/.Main"), tapAction(app))
        assertEquals(TapAction.LaunchShortcut("k"), tapAction(Entry("s", Target.Shortcut("k"))))
    }

    @Test
    fun app_with_custom_popup_tap_launches_swipe_opens_popup() {
        val custom = app.copy(popup = Popup(listOf(PopupRow.OwnShortcuts("pkg.a"))))
        assertEquals(TapAction.Launch("pkg.a/.Main"), tapAction(custom))
        // The swipe always opens the popup; the gesture layer does not look at the target.
        assertEquals(HomeGesture.OpenPopup, classify(100f, 800f, 400f, 0f))
    }
}
