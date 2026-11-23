package me.jhot.swiftcurrent.shell

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntRect
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.jhot.swiftcurrent.WatchdogTimeout
import me.jhot.swiftcurrent.catalog.AppInfo
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.LauncherData
import me.jhot.swiftcurrent.core.Popup
import me.jhot.swiftcurrent.core.PopupRow
import me.jhot.swiftcurrent.core.Target
import me.jhot.swiftcurrent.lock.Screen
import me.jhot.swiftcurrent.ui.applist.ScrubberDefaults
import me.jhot.swiftcurrent.ui.applist.scrubTag
import me.jhot.swiftcurrent.ui.edge.ScrubberExclusion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The edge swipe from Home: the list opens on touch-down and the same finger scrubs it. The
 * finger is injected on the root and never lifts between steps, so these drive the real path
 * (Home's detector forwarding to the list's scrubber), not the list's own pointer input.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class EdgeScrubShellTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val timeout = WatchdogTimeout(seconds = 120)

    private fun app(word: String) = AppInfo("pkg.${word.lowercase()}/.Main", word, "pkg.${word.lowercase()}")

    // Filler under each letter so the list really scrolls: a jump to M moves A's header off screen.
    private val apps = listOf("Alpha", "Bravo", "Charlie", "Mike", "Sierra", "Zulu").map(::app) +
        listOf("Alpha", "Charlie", "Mike", "Sierra").flatMap { w -> (1..12).map { app("$w $it") } }

    private val favorites = listOf(
        Entry("fav-alpha", Target.App("pkg.alpha/.Main")),
        Entry("fav-folder", Target.Folder, customLabel = "Folder", popup = Popup(listOf(PopupRow.Item(Entry("fav-bravo", Target.App("pkg.bravo/.Main")))))),
    )

    private fun start(): FakeDependencies {
        val deps = FakeDependencies(LauncherData(favorites = favorites), apps)
        compose.setContent { LauncherShell(deps) }
        compose.waitUntil(5_000) { exists("clock") }
        return deps
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitForTag(tag: String) = compose.waitUntil(5_000) { exists(tag) }
    private fun waitForNoTag(tag: String) = compose.waitUntil(5_000) { !exists(tag) }

    private val density get() = compose.activity.resources.displayMetrics.density

    // Root pixels; the edge finger is injected on the root, so these are its coordinates too.
    private fun edgeX(): Float = compose.onRoot().getBoundsInRoot().right.value * density * 0.95f

    private fun midY(): Float = compose.onRoot().getBoundsInRoot().bottom.value * density * 0.5f

    // Four fifths down is on the alphabet: the track is bottom-pinned and the star owns only its
    // top slot, which starts two thirds down the screen.
    private fun alphabetY(): Float = compose.onRoot().getBoundsInRoot().bottom.value * density * 0.8f

    private fun letterY(c: Char): Float {
        val b = compose.onNodeWithTag(scrubTag(c), useUnmergedTree = true).getBoundsInRoot()
        return (b.top + b.bottom).value / 2f * density
    }

    private fun finger(block: androidx.compose.ui.test.TouchInjectionScope.() -> Unit) {
        compose.onRoot().performTouchInput(block)
        compose.waitForIdle()
    }

    private fun bubbleShowsStar() {
        compose.onNodeWithTag("scrub-bubble", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(exists("scrub-bubble-glyph-star"))
    }

    private fun bubbleShows(c: Char) =
        compose.onNodeWithTag("scrub-bubble-letter", useUnmergedTree = true).assertTextEquals(c.toString())

    /** The list (not just its letters) is showing: its rows are in the semantics tree. */
    private fun listShown() = exists("applist")

    private fun downOnEdgeAt(y: Float) {
        finger { down(Offset(edgeX(), y)) }
        waitForTag("scrubber")
    }

    private fun downOnEdge() = downOnEdgeAt(midY())

    @Test
    fun edge_touch_down_opens_the_list_at_the_letter_before_any_movement() {
        val deps = start()
        finger { down(Offset(edgeX(), alphabetY())) }
        assertEquals(Screen.AppList, deps.session.screen.value)
        compose.onNodeWithTag("scrub-bubble", useUnmergedTree = true).assertIsDisplayed()
        // The list shows from the touch-down, not after the finger has moved.
        waitForTag("applist")
        finger { up() }
    }

    // The geometry the other tests lean on: 0.8 of the screen must be a letter of the real strip, not an action slot.
    @Test
    fun the_alphabet_height_the_tests_use_lands_on_a_letter_of_the_strip() {
        start()
        val y = alphabetY()
        downOnEdgeAt(y)
        val strip = compose.onNodeWithTag("scrubber").getBoundsInRoot()
        assertTrue(y / density in strip.top.value..strip.bottom.value)
        val shown = compose.onNodeWithTag("scrub-bubble-letter", useUnmergedTree = true).fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("")
        assertTrue("bubble shows '$shown'", shown.length == 1 && (shown[0].isLetter() || shown[0] == '#'))
        finger { up() }
    }

    @Test
    fun edge_swipe_up_opens_the_list_and_scrubs_in_one_gesture() {
        val deps = start()
        downOnEdge()
        finger { moveTo(Offset(edgeX(), letterY('M'))) }
        // Still down: the list is revealed, scrolled to M, and the bubble says M.
        assertTrue(listShown())
        compose.onNodeWithTag("header-M", useUnmergedTree = true).assertIsDisplayed()
        assertFalse(exists("header-A"))
        bubbleShows('M')
        finger { moveTo(Offset(edgeX(), letterY('S'))) }
        compose.onNodeWithTag("header-S", useUnmergedTree = true).assertIsDisplayed()
        bubbleShows('S')
        assertEquals(Screen.AppList, deps.session.screen.value)
        finger { up() }
    }

    @Test
    fun release_after_scrubbing_keeps_the_list_open_at_that_letter() {
        val deps = start()
        downOnEdge()
        finger { moveTo(Offset(edgeX(), letterY('M'))) }
        finger { up() }
        assertEquals(Screen.AppList, deps.session.screen.value)
        assertTrue(listShown())
        compose.onNodeWithTag("header-M", useUnmergedTree = true).assertIsDisplayed()
        // The press ended: no bubble, and the list is an ordinary open list (a row tap launches).
        assertFalse(exists("scrub-bubble"))
        compose.onNodeWithTag("row-pkg.mike/.Main").performClick()
        compose.waitUntil(5_000) { deps.launches.isNotEmpty() }
        assertEquals(listOf("pkg.mike/.Main"), deps.launches.toList())
    }

    @Test
    fun edge_swipe_up_to_the_star_and_lift_returns_home() {
        val deps = start()
        downOnEdge()
        finger { moveTo(Offset(edgeX(), letterY(ScrubberDefaults.STAR))) }
        // The list is revealed under the finger and the bubble says star: it is an action slot.
        assertTrue(listShown())
        compose.onNodeWithTag("scrub-bubble-glyph-star", useUnmergedTree = true).assertIsDisplayed()
        finger { up() }
        waitForNoTag("scrubber")
        assertEquals(Screen.Home, deps.session.screen.value)
        compose.onNodeWithTag("favorite-fav-alpha").assertIsDisplayed()
    }

    @Test
    fun edge_swipe_up_a_little_past_the_star_and_lift_returns_home_too() {
        val deps = start()
        downOnEdge()
        // A little above the star, as a swipe up to it really ends; the bubble shows the star there.
        finger { moveTo(Offset(edgeX(), letterY(ScrubberDefaults.STAR) - 60f)) }
        compose.onNodeWithTag("scrub-bubble-glyph-star", useUnmergedTree = true).assertIsDisplayed()
        finger { up() }
        waitForNoTag("scrubber")
        assertEquals(Screen.Home, deps.session.screen.value)
        compose.onNodeWithTag("favorite-fav-alpha").assertIsDisplayed()
    }

    @Test
    fun a_touch_above_the_strip_shows_the_star_and_lifting_there_goes_back_to_favorites() {
        val deps = start()
        // Above the strip the list opens with the star selected; lifting without sliding to a letter is the star.
        downOnEdgeAt(midY())
        bubbleShowsStar()
        finger { up() }
        waitForNoTag("scrubber")
        assertEquals(Screen.Home, deps.session.screen.value)
    }

    @Test
    fun sliding_up_to_the_star_after_resting_on_a_letter_and_lifting_goes_back_to_favorites() {
        val deps = start()
        downOnEdgeAt(alphabetY())
        finger { moveTo(Offset(edgeX(), letterY(ScrubberDefaults.STAR) - 600f)) }
        finger { up() }
        waitForNoTag("scrubber")
        assertEquals(Screen.Home, deps.session.screen.value)
    }

    @Test
    fun a_tap_on_the_strip_without_moving_keeps_the_list_open_at_that_letter() {
        val deps = start()
        downOnEdgeAt(alphabetY())
        val shown = compose.onNodeWithTag("scrub-bubble-letter", useUnmergedTree = true).fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("")
        finger { up() }
        assertEquals(Screen.AppList, deps.session.screen.value)
        assertTrue(listShown())
        assertFalse(exists("scrub-bubble"))
        // The list stays where the finger put it.
        compose.onNodeWithTag("header-${shown[0]}", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun two_quick_taps_on_the_strip_from_home_lock() {
        val deps = start()
        // From favorites, with no list open: the first tap opens the list and counts, so a
        // second tap on its strip within the double-tap timeout locks.
        downOnEdgeAt(alphabetY())
        finger { up() }
        assertEquals(0, deps.fakeLock.calls)
        // The lock resets to Home at once, so the list may never show for this touch.
        finger { down(Offset(edgeX(), alphabetY())) }
        compose.waitUntil(5_000) { deps.fakeLock.calls == 1 }
        finger { up() }
        assertEquals(Screen.Home, deps.session.screen.value)
    }

    @Test
    fun a_single_tap_on_the_strip_does_not_lock() {
        val deps = start()
        downOnEdgeAt(alphabetY())
        finger { up() }
        assertEquals(0, deps.fakeLock.calls)
        assertEquals(Screen.AppList, deps.session.screen.value)
    }

    @Test
    fun the_opening_touch_counts_as_the_first_tap_of_a_double_tap_lock() {
        val deps = start()
        // On the alphabet: the star is an action slot now, so its press is not a tap.
        downOnEdgeAt(alphabetY())
        finger { moveTo(Offset(edgeX(), letterY('M'))) }
        finger { up() }
        assertEquals(0, deps.fakeLock.calls)
        // A tap near the opening touch's letter, within the double-tap timeout, locks.
        val strip = compose.onNodeWithTag("scrubber").getBoundsInRoot()
        val at = Offset((strip.right - strip.left).value / 2f * density, alphabetY() - strip.top.value * density)
        compose.onNodeWithTag("scrubber").performTouchInput { down(at); up() }
        compose.waitUntil(5_000) { deps.fakeLock.calls == 1 }
    }

    @Test
    fun cancel_during_scrub_does_not_leave_a_stuck_scrub() {
        val deps = start()
        downOnEdge()
        finger { moveTo(Offset(edgeX(), letterY('M'))) }
        finger { cancel() }
        // The list had been revealed, so it stays open at M, with the press ended.
        assertEquals(Screen.AppList, deps.session.screen.value)
        assertFalse(exists("scrub-bubble"))
        compose.onNodeWithTag("header-M", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("row-pkg.mike/.Main").performClick()
        compose.waitUntil(5_000) { deps.launches.isNotEmpty() }
    }

    @Test
    fun cancel_before_the_finger_moved_closes_the_list_again() {
        val deps = start()
        downOnEdge()
        finger { cancel() }
        waitForNoTag("scrubber")
        assertEquals(Screen.Home, deps.session.screen.value)
    }

    @Test
    fun a_stray_edge_touch_closes_the_list_at_once_without_sliding() {
        val deps = start()
        downOnEdge()
        compose.mainClock.autoAdvance = false
        finger { cancel() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        // Two frames, far inside the slide: a close that slid would still have the list on screen.
        assertFalse(exists("applist"))
        assertEquals(Screen.Home, deps.session.screen.value)
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun a_cancel_before_moving_keeps_the_opening_tap_for_the_lock() {
        val deps = start()
        // On the alphabet: the star is an action slot now, so its press is not a tap.
        downOnEdgeAt(alphabetY())
        finger { cancel() }
        waitForNoTag("scrubber")
        assertEquals(Screen.Home, deps.session.screen.value)
        // Not forgotten: the cancelled touch still counts, so the next touch on the alphabet
        // within the timeout is the second tap and locks.
        finger { down(Offset(edgeX(), alphabetY())) }
        compose.waitUntil(5_000) { deps.fakeLock.calls == 1 }
        finger { up() }
    }

    @Test
    fun gestures_do_nothing_while_an_overlay_is_open() {
        val deps = start()
        compose.onNodeWithTag("favorite-fav-folder").performClick()
        waitForTag("popup-scrim")
        finger { down(Offset(edgeX(), midY())) }
        assertEquals(Screen.Home, deps.session.screen.value)
        assertFalse(exists("scrubber"))
        finger { moveTo(Offset(edgeX(), midY() - 600f)) }
        finger { up() }
        assertEquals(Screen.Home, deps.session.screen.value)
        assertFalse(exists("scrubber"))
    }

    @Test
    fun a_screen_lock_reset_cancels_a_scrub_in_progress() {
        val deps = start()
        downOnEdge()
        finger { moveTo(Offset(edgeX(), letterY('M'))) }
        compose.runOnUiThread { deps.session.onScreenOff() }
        waitForNoTag("scrubber")
        assertEquals(Screen.Home, deps.session.screen.value)
        // The rest of that finger's life reaches nothing.
        finger { moveTo(Offset(edgeX(), midY() - 300f)) }
        finger { up() }
        assertEquals(Screen.Home, deps.session.screen.value)
        assertFalse(exists("scrubber"))
        // A new edge swipe works, from the top of a fresh list.
        downOnEdge()
        finger { moveTo(Offset(edgeX(), letterY('M'))) }
        finger { up() }
        assertEquals(Screen.AppList, deps.session.screen.value)
        compose.onNodeWithTag("header-M", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun composeView(): View {
        var v: View = compose.activity.findViewById<ViewGroup>(android.R.id.content)
        while (v is ViewGroup && v.childCount > 0 && v.javaClass.simpleName != "AndroidComposeView") v = v.getChildAt(0)
        return v
    }

    @Test
    fun home_declares_the_edge_exclusion_and_drops_it_when_covered() {
        val deps = start()
        val view = composeView()
        compose.waitForIdle()
        fun rectsNow() = view.systemGestureExclusionRects.map { IntRect(it.left, it.top, it.right, it.bottom) }
        val home = rectsNow().single()
        assertEquals("the whole strip is excluded, top to bottom", view.height, home.height)
        assertEquals("the strip hugs the right edge", view.width, home.right)
        // The list's own scrubber must declare the same strip, so the letters stay covered as the list opens under the finger.
        compose.runOnUiThread { deps.session.navigate(Screen.AppList) }
        waitForTag("scrubber")
        compose.waitForIdle()
        val inList = rectsNow()
        assertTrue("home window $home differs from the list's $inList", home in inList)
        compose.runOnUiThread { deps.session.navigate(Screen.Home) }
        waitForNoTag("scrubber")
        compose.waitForIdle()
        val expected = home
        compose.runOnUiThread { deps.session.navigate(Screen.Search) }
        waitForTag("search-field")
        compose.waitForIdle()
        val covered = view.systemGestureExclusionRects.map { IntRect(it.left, it.top, it.right, it.bottom) }
        assertFalse("home rect still excluded under search: $covered", expected in covered)
    }
}
