package me.jhot.swiftcurrent.ui.applist

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import me.jhot.swiftcurrent.WatchdogTimeout
import me.jhot.swiftcurrent.catalog.AppInfo
import me.jhot.swiftcurrent.catalog.Section
import me.jhot.swiftcurrent.ui.design.Tokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class AppListScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    // An animated scroll that never settles would hang the whole gate; this makes it fail instead.
    @get:Rule
    val timeout = WatchdogTimeout(seconds = 60)

    private val words = mapOf(
        'A' to "Alpha", 'B' to "Bravo", 'C' to "Charlie", 'D' to "Delta", 'E' to "Echo",
        'G' to "Golf", 'M' to "Mike", 'S' to "Sierra", 'Z' to "Zulu",
    )

    private fun section(key: Char, word: String, n: Int) = Section(
        key, (1..n).map { AppInfo("pkg.$word$it/.Main", "$word $it", "pkg.$word$it") },
    )

    // No F, H..L, N..R, T..Y: those letters must be left out of the scrubber.
    private val sections = listOf(section('#', "123", 2)) + words.map { (k, w) -> section(k, w, 6) }

    // Index of each section's sticky header in the list: one header plus its apps per section.
    private fun headerIndex(key: Char): Int =
        sections.takeWhile { it.key != key }.sumOf { 1 + it.apps.size }

    private fun tagOf(c: Char) = scrubTag(c)

    private fun show(
        state: LazyListState = LazyListState(),
        onLaunch: (AppInfo) -> Unit = {},
        onSearch: () -> Unit = {},
        onSettings: () -> Unit = {},
        onScrubTap: (Char) -> Unit = {},
        onClose: () -> Unit = {},
        onSwipeRight: (AppInfo, androidx.compose.ui.geometry.Rect) -> Unit = { _, _ -> },
        scrub: ScrubState = ScrubState(),
        edgeScrub: EdgeScrub? = null,
    ) = compose.setContent {
        AppListScreen(sections, onLaunch, onSearch, onSettings, onScrubTap, onClose, state, edgeScrub = edgeScrub, onSwipeRight = onSwipeRight, scrub = scrub)
    }

    private fun top(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot().top.value
    private fun bottom(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot().bottom.value

    // A letter's centre in the scrubber strip's own coordinates, where touch input is injected.
    private fun touchPointOf(c: Char): Offset {
        val strip = compose.onNodeWithTag("scrubber").getBoundsInRoot()
        val letter = compose.onNodeWithTag(tagOf(c), useUnmergedTree = true).getBoundsInRoot()
        val density = compose.activity.resources.displayMetrics.density
        return Offset(
            ((strip.right - strip.left).value / 2f) * density,
            ((letter.top + letter.bottom).value / 2f - strip.top.value) * density,
        )
    }

    private fun scrubWith(haptics: Boolean): RecordingHaptics {
        val fake = RecordingHaptics()
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalHapticFeedback provides fake) {
                AppListScreen(sections, {}, {}, {}, {}, haptics = haptics)
            }
        }
        val at = touchPointOf('M')
        compose.onNodeWithTag("scrubber").performTouchInput { down(at); up() }
        compose.waitForIdle()
        return fake
    }

    @Test
    fun the_haptics_flag_reaches_the_scrubber() {
        val on = scrubWith(true)
        assertTrue(on.performed.isNotEmpty())
        assertEquals(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove, on.performed.first())
    }

    @Test
    fun no_haptics_when_the_setting_is_off() {
        assertTrue(scrubWith(false).performed.isEmpty())
    }

    @Test
    fun letters_without_apps_are_not_in_the_scrubber() {
        val expected = listOf(ScrubberDefaults.STAR, '#') + words.keys.sorted() + ScrubberDefaults.COG
        assertEquals(expected, scrubberLetters(sections))
        show()
        expected.forEach { compose.onNodeWithTag(tagOf(it), useUnmergedTree = true).assertIsDisplayed() }
        listOf('F', 'H', 'K', 'N', 'R', 'Y').forEach {
            compose.onAllNodesWithTag(tagOf(it), useUnmergedTree = true).assertCountEquals(0)
        }
        // Even with no digit apps the # jump stays, so the strip keeps its shape.
        assertEquals('#', scrubberLetters(sections.drop(1))[1])
    }

    @Test
    fun sections_show_bold_headers_and_rows_in_order() {
        show()
        compose.onNodeWithTag("header-#", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("123 1").assertIsDisplayed()
        assertTrue(top("header-A") < top("row-pkg.Alpha1/.Main"))
        assertTrue(top("row-pkg.Alpha1/.Main") < top("row-pkg.Alpha2/.Main"))
    }

    @Test
    fun swiping_a_row_to_the_right_asks_for_its_menu_with_the_row_bounds_and_does_not_launch_it() {
        val swiped = mutableListOf<Pair<String, androidx.compose.ui.geometry.Rect>>()
        val launched = mutableListOf<String>()
        val app = sections.first().apps.first()
        show(onLaunch = { launched += it.component }, onSwipeRight = { a, r -> swiped += a.component to r })
        val row = compose.onNodeWithTag("row-${app.component}", useUnmergedTree = true)
        val before = row.getBoundsInRoot()
        row.performTouchInput { swipe(Offset(100f, centerY), Offset(500f, centerY)) }
        compose.waitForIdle()
        assertEquals(listOf(app.component), swiped.map { it.first })
        val density = compose.activity.resources.displayMetrics.density
        assertEquals(before.top.value * density, swiped.single().second.top, 2f)
        assertTrue("a swipe is not a tap", launched.isEmpty())
    }

    @Test
    fun an_archived_app_wears_a_download_mark_and_the_others_do_not() {
        val apps = listOf(
            AppInfo("pkg.gone/.Main", "Gone", "pkg.gone", archived = true),
            AppInfo("pkg.here/.Main", "Here", "pkg.here"),
        )
        compose.setContent { AppListScreen(listOf(Section('G', apps)), {}, {}, {}, {}) }
        compose.onNodeWithTag("archived-pkg.gone/.Main", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithTag("archived-pkg.here/.Main", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun touching_the_scrubber_shows_letters_and_bubble_immediately() {
        val taps = mutableListOf<Char>()
        show(onScrubTap = { taps += it })
        compose.onAllNodesWithTag("scrub-bubble", useUnmergedTree = true).assertCountEquals(0)
        val at = touchPointOf('M')
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("scrubber").performTouchInput { down(at) }
        // One frame only: anything that waits for slop, a delay or an entry animation fails here.
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("scrub-bubble", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("scrub-bubble-letter", useUnmergedTree = true).assertTextEquals("M")
        scrubberLetters(sections).forEach { compose.onNodeWithTag(tagOf(it), useUnmergedTree = true).assertIsDisplayed() }
        assertEquals(listOf('M'), taps)

        compose.onNodeWithTag("scrubber").performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
        compose.onAllNodesWithTag("scrub-bubble", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun dragging_across_letters_jumps_to_the_letter_under_the_finger() {
        val state = LazyListState()
        show(state = state)
        val from = touchPointOf('A')
        val to = touchPointOf('C')
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("scrubber").performTouchInput { down(from); moveTo(to) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        // Still pressed: a drag jump is as immediate as a press jump.
        compose.onNodeWithTag("header-C", useUnmergedTree = true).assertIsDisplayed()
        assertTrue("C is centred, not at the top", top("header-C") > top("applist"))
        compose.onNodeWithTag("scrub-bubble-letter", useUnmergedTree = true).assertTextEquals("C")
        compose.onNodeWithTag("scrubber").performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
    }

    // Task 11 locks on a double tap: drag jumps a few ms apart must never look like taps.
    @Test
    fun a_drag_across_letters_calls_onScrubTap_exactly_once() {
        val taps = mutableListOf<Char>()
        show(onScrubTap = { taps += it })
        val from = touchPointOf('A')
        val b = touchPointOf('B')
        val to = touchPointOf('C')
        compose.onNodeWithTag("scrubber").performTouchInput {
            down(from)
            moveTo(from + Offset(0f, 1f))
            moveTo(b)
            moveTo(to)
            moveTo(from)
            moveTo(to)
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf('A'), taps)
    }

    @Test
    fun two_separate_presses_call_onScrubTap_twice() {
        val taps = mutableListOf<Char>()
        show(onScrubTap = { taps += it })
        val a = touchPointOf('A')
        val c = touchPointOf('C')
        compose.onNodeWithTag("scrubber").performTouchInput { down(a); up() }
        compose.onNodeWithTag("scrubber").performTouchInput { down(c); up() }
        compose.onNodeWithTag("scrubber").performTouchInput { down(c); up() }
        compose.waitForIdle()
        assertEquals(listOf('A', 'C', 'C'), taps)
    }

    @Test
    fun lifting_on_the_cog_opens_settings() {
        var settings = 0
        show(onSettings = { settings++ })
        val cog = touchPointOf(ScrubberDefaults.COG)
        val z = touchPointOf('Z')
        // Pressing elsewhere and sliding onto the cog counts too: only the lift point matters.
        compose.onNodeWithTag("scrubber").performTouchInput { down(z); moveTo(cog + Offset(0f, 3f)); up() }
        compose.waitForIdle()
        assertEquals(1, settings)
    }

    @Test
    fun lifting_below_the_cog_slot_does_not_open_settings() {
        var settings = 0
        show(onSettings = { settings++ })
        // A stray tap in the strip's bottom corner, under the cog.
        compose.onNodeWithTag("scrubber").performTouchInput { down(Offset(centerX, height - 2f)); up() }
        compose.waitForIdle()
        assertEquals(0, settings)
    }

    @Test
    fun overshooting_Z_downward_highlights_the_cog_but_lift_far_below_does_nothing() {
        var settings = 0
        show(onSettings = { settings++ })
        val z = touchPointOf('Z')
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("scrubber").performTouchInput { down(z); moveTo(Offset(centerX, height - 2f)) }
        compose.mainClock.advanceTimeByFrame()
        // The highlight still clamps to the last entry, so the bubble shows the cog.
        compose.onNodeWithTag("scrub-bubble-glyph-cog", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("scrubber").performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals(0, settings)
    }

    @Test
    fun search_button_is_hidden_while_scrubbing_and_back_at_rest() {
        var searched = 0
        show(onSearch = { searched++ })
        compose.onNodeWithTag("applist-search").assertIsDisplayed()
        val z = touchPointOf('Z')
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("scrubber").performTouchInput { down(z) }
        compose.mainClock.advanceTimeBy(ScrubberDefaults.SEARCH_FADE_MS + 64L)
        compose.onAllNodesWithTag("applist-search").assertCountEquals(0)
        compose.onNodeWithTag("scrubber").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(ScrubberDefaults.SEARCH_FADE_MS + 64L)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("applist-search").assertIsDisplayed().performClick()
        assertEquals(1, searched)
    }

    @Test
    fun other_rows_fade_in_when_the_finger_lifts() {
        val scrub = ScrubState()
        show(scrub = scrub)
        val z = touchPointOf('Z')
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("scrubber").performTouchInput { down(z) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(0f, scrub.othersAlpha.value, 0f)
        compose.onNodeWithTag("scrubber").performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(REVEAL_MS / 2L)
        assertTrue("half way, got ${scrub.othersAlpha.value}", scrub.othersAlpha.value > 0f && scrub.othersAlpha.value < 1f)
        compose.mainClock.advanceTimeBy(REVEAL_MS + 64L)
        assertEquals(1f, scrub.othersAlpha.value, 0f)
    }

    @Test
    fun a_new_press_during_the_reveal_hides_the_others_again() {
        val scrub = ScrubState()
        show(scrub = scrub)
        val z = touchPointOf('Z')
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("scrubber").performTouchInput { down(z) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("scrubber").performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(REVEAL_MS / 2L)
        // Really mid-reveal, so the press below has an animation to cancel.
        assertTrue("mid-reveal, got ${scrub.othersAlpha.value}", scrub.othersAlpha.value > 0f && scrub.othersAlpha.value < 1f)
        compose.onNodeWithTag("scrubber").performTouchInput { down(z) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(0f, scrub.othersAlpha.value, 0f)
        compose.mainClock.advanceTimeBy(REVEAL_MS + 64L)
        assertEquals(0f, scrub.othersAlpha.value, 0f)
    }

    @Test
    fun a_press_shorter_than_a_frame_still_fades_the_others_in() {
        val scrub = ScrubState()
        show(scrub = scrub)
        val z = touchPointOf('Z')
        compose.mainClock.autoAdvance = false
        // A finger from Home can land and lift before the list has composed once, so no frame sees it pressed.
        compose.onNodeWithTag("scrubber").performTouchInput { down(z); up() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(REVEAL_MS / 2L)
        assertTrue("mid-reveal, got ${scrub.othersAlpha.value}", scrub.othersAlpha.value > 0f && scrub.othersAlpha.value < 1f)
        compose.mainClock.advanceTimeBy(REVEAL_MS + 64L)
        assertEquals(1f, scrub.othersAlpha.value, 0f)
    }

    @Test
    fun a_second_back_during_the_slide_is_swallowed() {
        var closed = 0
        show(onClose = { closed++ })
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(LIST_SLIDE_MS / 2L)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertFalse("a second Back must not fall through to the activity", compose.activity.isFinishing)
        compose.mainClock.advanceTimeBy(LIST_SLIDE_MS + 64L)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(1, closed)
    }

    @Test
    fun closing_before_the_first_jump_lands_does_not_slide_an_invisible_list() {
        var closed = 0
        // A finger from Home is down but no letter was sent yet (a quick tap on the star): the list is still held unseen.
        // Paused before the list shows, or the hold's own 150ms timeout runs out while it composes.
        compose.mainClock.autoAdvance = false
        show(onClose = { closed++ }, edgeScrub = EdgeScrub().apply { press(10f) })
        compose.mainClock.advanceTimeByFrame()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        // Nothing is on screen to slide, so it closes at once rather than after LIST_SLIDE_MS.
        assertEquals(1, closed)
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun rows_of_other_letters_follow_the_reveal_and_the_touched_one_never_hides() {
        // While pressed the others are hidden whatever the reveal says; after the lift they take its value.
        assertEquals(0f, othersAlphaFor(scrubbed = 'M', section = 'A', pressed = true, reveal = 1f), 0f)
        assertEquals(0.4f, othersAlphaFor(scrubbed = 'M', section = 'A', pressed = false, reveal = 0.4f), 0f)
        assertEquals(1f, othersAlphaFor(scrubbed = 'M', section = 'M', pressed = true, reveal = 0f), 0f)
        // No letter touched (the star, or '#' without apps): nothing is hidden.
        assertEquals(1f, othersAlphaFor(scrubbed = null, section = 'A', pressed = true, reveal = 0f), 0f)
    }

    // The list's visible area: below its top, above the bottom padding that clears the search button.
    private fun visibleBottom() = bottom("applist") - (Tokens.searchButtonBottom + Tokens.searchButtonSize).value

    private fun lastRowOf(key: Char) = "row-" + sections.first { it.key == key }.apps.last().component

    @Test
    fun tap_on_a_letter_centres_its_block_at_once() {
        val taps = mutableListOf<Char>()
        val state = LazyListState()
        show(state = state, onScrubTap = { taps += it })
        val at = touchPointOf('M')
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("scrubber").performTouchInput { down(at); up() }
        // Two frames: an animated scroll would still be in flight.
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        val above = top("header-M") - top("applist")
        val below = visibleBottom() - bottom(lastRowOf('M'))
        assertTrue("above $above below $below", above > 0f && Math.abs(above - below) <= Tokens.rowHeight.value)
        assertEquals(listOf('M'), taps)
    }

    @Test
    fun a_block_taller_than_the_visible_area_keeps_its_header_at_the_top() {
        val tall = listOf(section('A', "Alpha", 3), section('B', "Bravo", 40), section('C', "Charlie", 3))
        val state = LazyListState()
        compose.setContent { AppListScreen(tall, {}, {}, {}, {}, {}, state) }
        val at = touchPointOf('B')
        compose.onNodeWithTag("scrubber").performTouchInput { down(at); up() }
        compose.waitForIdle()
        assertEquals(top("applist"), top("header-B"), 0.5f)
    }

    @Test
    fun the_first_letter_cannot_centre_and_stops_at_the_top_of_the_list() {
        val state = LazyListState(firstVisibleItemIndex = headerIndex('M'))
        show(state = state)
        val at = touchPointOf('A')
        compose.onNodeWithTag("scrubber").performTouchInput { down(at); up() }
        compose.waitForIdle()
        assertEquals(0, state.firstVisibleItemIndex)
        assertEquals(0, state.firstVisibleItemScrollOffset)
    }

    @Test
    fun the_last_letter_stops_at_the_end_of_the_list_without_a_gap() {
        val state = LazyListState()
        show(state = state)
        val at = touchPointOf('Z')
        compose.onNodeWithTag("scrubber").performTouchInput { down(at); up() }
        compose.waitForIdle()
        assertTrue("list end is in view", !state.canScrollForward)
        compose.onNodeWithTag("list-cog", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun hash_jumps_to_the_top() {
        val state = LazyListState(firstVisibleItemIndex = headerIndex('M'))
        show(state = state)
        val at = touchPointOf('#')
        compose.onNodeWithTag("scrubber").performTouchInput { down(at); up() }
        compose.waitForIdle()
        assertEquals(0, state.firstVisibleItemIndex)
    }

    @Test
    fun the_star_is_an_action_slot_and_does_not_jump() {
        val state = LazyListState(firstVisibleItemIndex = headerIndex('M'))
        var closed = 0
        show(state = state, onClose = { closed++ })
        val at = touchPointOf(ScrubberDefaults.STAR)
        compose.onNodeWithTag("scrubber").performTouchInput { down(at); up() }
        compose.waitForIdle()
        // Neither scrolled nor left open: the list stayed put and the lift closed it.
        assertEquals(headerIndex('M'), state.firstVisibleItemIndex)
        assertEquals(1, closed)
    }

    @Test
    fun the_star_never_feeds_the_double_tap_lock() {
        val taps = mutableListOf<Char>()
        show(onScrubTap = { taps += it })
        val at = touchPointOf(ScrubberDefaults.STAR)
        compose.onNodeWithTag("scrubber").performTouchInput { down(at); up() }
        compose.waitForIdle()
        // Like the cog: an action slot is not a letter, so it never arms a lock.
        assertEquals(emptyList<Char>(), taps)
    }

    @Test
    fun lifting_on_the_star_closes_the_list_after_its_slide() {
        val state = LazyListState(firstVisibleItemIndex = headerIndex('M'))
        var closed = 0
        show(state = state, onClose = { closed++ })
        val a = touchPointOf('A')
        val star = touchPointOf(ScrubberDefaults.STAR)
        compose.mainClock.autoAdvance = false
        // Pressing elsewhere and sliding onto the star counts too: only the lift point matters.
        compose.onNodeWithTag("scrubber").performTouchInput { down(a); moveTo(star + Offset(0f, 3f)); up() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        // Lets the paused looper start the slide's effect without moving the clock.
        compose.waitForIdle()
        // A slide, not a cut, exactly as Back closes the list.
        assertEquals(0, closed)
        compose.mainClock.advanceTimeBy(LIST_SLIDE_MS + 64L)
        compose.waitForIdle()
        assertEquals(1, closed)
        // A cannot centre (the '#' block is all there is above it), so it stops at the start of the list.
        assertEquals(0, state.firstVisibleItemIndex)
    }

    @Test
    fun swiping_up_past_the_star_and_lifting_there_still_closes_the_list() {
        val state = LazyListState(firstVisibleItemIndex = headerIndex('M'))
        var closed = 0
        show(state = state, onClose = { closed++ })
        val a = touchPointOf('A')
        val star = touchPointOf(ScrubberDefaults.STAR)
        compose.mainClock.autoAdvance = false
        // The bubble shows the star for a finger above the strip, so a lift up there is the star too:
        // this is how a swipe up to the star really ends, well past it, not on its 13 dp.
        compose.onNodeWithTag("scrubber").performTouchInput { down(a); moveTo(star + Offset(0f, -120f)); up() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(LIST_SLIDE_MS + 64L)
        compose.waitForIdle()
        assertEquals(1, closed)
    }

    @Test
    fun dragging_onto_the_star_and_back_does_not_close_the_list() {
        var closed = 0
        show(onClose = { closed++ })
        val a = touchPointOf('A')
        val star = touchPointOf(ScrubberDefaults.STAR)
        compose.onNodeWithTag("scrubber").performTouchInput { down(a); moveTo(star); moveTo(a); up() }
        compose.waitForIdle()
        assertEquals(0, closed)
    }

    @Test
    fun a_cancelled_scrub_on_the_star_does_not_close_the_list() {
        var closed = 0
        show(onClose = { closed++ })
        val star = touchPointOf(ScrubberDefaults.STAR)
        compose.onNodeWithTag("scrubber").performTouchInput { down(star); cancel() }
        compose.waitForIdle()
        assertEquals(0, closed)
    }

    @Test
    fun sticky_header_stays_while_its_section_scrolls() {
        val state = LazyListState()
        show(state = state)
        compose.runOnIdle { state.requestScrollToItem(headerIndex('B') + 3, 20) }
        compose.waitForIdle()
        compose.onNodeWithTag("header-B", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(top("applist"), top("header-B"), 0.5f)
        compose.onAllNodesWithTag("header-A", useUnmergedTree = true).assertCountEquals(0)
        // Rows of the section slide under the pinned header.
        assertTrue(top("row-pkg.Bravo4/.Main") < bottom("header-B"))
    }

    @Test
    fun cog_is_last_and_calls_onSettings() {
        var settings = 0
        val taps = mutableListOf<Char>()
        show(onSettings = { settings++ }, onScrubTap = { taps += it })
        assertEquals(ScrubberDefaults.COG, scrubberLetters(sections).last())
        assertTrue(top(tagOf(ScrubberDefaults.COG)) > top(tagOf('Z')))
        val at = touchPointOf(ScrubberDefaults.COG)
        compose.onNodeWithTag("scrubber").performTouchInput { down(at); up() }
        compose.waitForIdle()
        assertEquals(1, settings)
        // The cog is not a letter, so it never feeds the double-tap lock.
        assertEquals(emptyList<Char>(), taps)
    }

    @Test
    fun dragging_past_the_cog_and_back_does_not_open_settings() {
        var settings = 0
        show(onSettings = { settings++ })
        val cog = touchPointOf(ScrubberDefaults.COG)
        val z = touchPointOf('Z')
        compose.onNodeWithTag("scrubber").performTouchInput { down(z); moveTo(cog); moveTo(z); up() }
        compose.waitForIdle()
        assertEquals(0, settings)
    }

    @Test
    fun rows_launch_and_search_button_searches() {
        val launched = mutableListOf<AppInfo>()
        var searched = 0
        show(onLaunch = { launched += it }, onSearch = { searched++ })
        compose.onNodeWithText("Alpha 2").performClick()
        assertEquals("pkg.Alpha2/.Main", launched.single().component)
        compose.onNodeWithTag("applist-search").performClick()
        assertEquals(1, searched)
        // The search button sits left of the reserved strip, never on it.
        val strip = compose.onNodeWithTag("scrubber").getBoundsInRoot()
        val search = compose.onNodeWithTag("applist-search").getBoundsInRoot()
        assertTrue(search.right <= strip.left)
    }

    @Test
    fun back_closes_the_list() {
        var closed = 0
        show(onClose = { closed++ })
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeByFrame()
        // Lets the paused looper start the slide's effect without moving the clock.
        compose.waitForIdle()
        // A slide, not a cut: still on screen for a frame, off to the right half way, gone right after.
        assertEquals(0, closed)
        compose.mainClock.advanceTimeBy(LIST_SLIDE_MS / 2L)
        val rootWidth = compose.onRoot().getBoundsInRoot().let { (it.right - it.left).value }
        val halfLeft = compose.onNodeWithTag("applist", useUnmergedTree = true).getBoundsInRoot().left.value
        assertTrue("half way, left was $halfLeft of $rootWidth", halfLeft > 1f && halfLeft < rootWidth)
        assertEquals(0, closed)
        compose.mainClock.advanceTimeBy(LIST_SLIDE_MS + 64L)
        compose.waitForIdle()
        assertEquals(1, closed)
    }

    @Test
    fun appList_scrubbing() {
        show()
        val at = touchPointOf('G')
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("scrubber").performTouchInput { down(at) }
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("src/test/snapshots/appList_scrubbing.png")
        compose.onNodeWithTag("scrubber").performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
    }
}

private class RecordingHaptics : androidx.compose.ui.hapticfeedback.HapticFeedback {
    val performed = mutableListOf<androidx.compose.ui.hapticfeedback.HapticFeedbackType>()
    override fun performHapticFeedback(hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType) { performed += hapticFeedbackType }
}
