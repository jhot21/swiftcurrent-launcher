package me.jhot.swiftcurrent.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.HomeSpacing
import me.jhot.swiftcurrent.core.Target
import me.jhot.swiftcurrent.ui.home.HomeRow
import me.jhot.swiftcurrent.ui.home.HomeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class HomeSpacingPageTest {
    @get:Rule
    val compose = createComposeRule()

    private fun rows(n: Int) = (1..n).map {
        HomeRow(Entry("fav$it", Target.App("pkg.fav$it/.Main")), "Favorite $it", missing = false)
    }

    private fun preview(n: Int) = HomeUiState(clock = "7:20", date = "Sun, Nov 8", nextEvent = null, favorites = rows(n), anyStored = n > 0)

    private fun show(gap: Int = HomeSpacing.DEFAULT, favorites: Int = 3, onGap: (Int) -> Unit = {}) = compose.setContent {
        HomeSpacingPage(preview(favorites), gap, onGap)
    }

    @Test
    fun the_page_draws_the_real_home_under_the_controls_and_shows_the_value() {
        show(gap = 48)
        compose.onNodeWithTag("clock").assertIsDisplayed()
        compose.onNodeWithTag("favorite-fav1").assertIsDisplayed()
        compose.onNodeWithTag("spacing-value").assertTextEquals("48 dp")
    }

    @Test
    fun the_buttons_step_by_the_setting_step_and_the_reset_goes_back_to_the_default() {
        val picked = mutableListOf<Int>()
        show(gap = 48, onGap = { picked += it })
        compose.onNodeWithTag("spacing-more").performClick()
        compose.onNodeWithTag("spacing-less").performClick()
        compose.onNodeWithTag("spacing-reset").performClick()
        assertEquals(listOf(48 + HomeSpacing.STEP, 48 - HomeSpacing.STEP, HomeSpacing.DEFAULT), picked)
    }

    @Test
    fun the_ends_of_the_range_are_not_clickable_and_reset_is_inert_at_the_default() {
        var gap by mutableIntStateOf(HomeSpacing.MIN)
        compose.setContent { HomeSpacingPage(preview(3), gap) { gap = it } }
        compose.onNodeWithTag("spacing-less").assertIsNotEnabled()
        compose.onNodeWithTag("spacing-more").assertIsEnabled()
        gap = HomeSpacing.LIMIT
        compose.waitForIdle()
        compose.onNodeWithTag("spacing-more").assertIsNotEnabled()
        gap = HomeSpacing.DEFAULT
        compose.waitForIdle()
        compose.onNodeWithTag("spacing-reset").assertIsNotEnabled()
    }

    @Test
    fun the_note_says_when_every_favorite_fits() {
        show(gap = HomeSpacing.DEFAULT, favorites = 3)
        compose.onNodeWithTag("spacing-fit").assertTextEquals("All 3 favorites fit")
    }

    @Test
    fun the_note_names_how_many_fit_when_the_chosen_space_pushes_favorites_off_the_screen() {
        // Far more than any gap leaves room for; how many fit is what the preview itself measured.
        show(gap = HomeSpacing.DEFAULT, favorites = 40)
        compose.onNodeWithTag("spacing-fit").assertTextContains(" of 40 favorites fit; the rest are hidden", substring = true)
    }

    @Test
    fun the_note_does_not_claim_hidden_rows_when_they_fit_once_the_gap_shrinks() {
        // 12 favorites fit with a smaller gap but not at the stored 96 dp; Home shrinks the gap, so nothing is hidden.
        show(gap = 96, favorites = 12)
        compose.onNodeWithTag("spacing-fit").assertTextEquals("All 12 favorites fit")
    }

    @Test
    fun even_a_big_gap_leaves_room_for_some_favorites() {
        show(gap = 96, favorites = 40)
        val tall = noteCount()
        assertTrue("the preview should fit some favorites at 96 dp", tall > 0)
    }

    private fun noteCount(): Int {
        val text = compose.onNodeWithTag("spacing-fit").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text }
        return Regex("^(\\d+) of").find(text)?.groupValues?.get(1)?.toInt() ?: -1
    }

    private fun maxShown(): Int =
        Regex("(\\d+) dp").find(
            compose.onNodeWithTag("spacing-max").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text },
        )!!.groupValues[1].toInt()

    @Test
    fun the_page_allows_far_more_than_96_dp_when_there_are_few_favorites() {
        show(gap = 96, favorites = 3)
        assertTrue("max was ${maxShown()}", maxShown() > 300)
        // 96 is no longer the end: more is enabled and steps past it.
        compose.onNodeWithTag("spacing-more").assertIsEnabled()
    }

    @Test
    fun more_favorites_leave_a_smaller_maximum() {
        var n by mutableIntStateOf(2)
        compose.setContent { HomeSpacingPage(preview(n), 0) {} }
        val few = maxShown()
        n = 8
        compose.waitForIdle()
        val more = maxShown()
        assertTrue("few=$few more=$more", few > more)
    }

    @Test
    fun the_slider_sets_the_gap_snapped_to_the_step_and_never_past_the_maximum() {
        val picked = mutableListOf<Int>()
        show(gap = 32, favorites = 3, onGap = { picked += it })
        val max = maxShown()
        compose.onNodeWithTag("spacing-slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(150f) }
        compose.onNodeWithTag("spacing-slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(151.9f) }
        compose.onNodeWithTag("spacing-slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(10_000f) }
        assertEquals(listOf(152, 152, max), picked)
        assertTrue(picked.all { it % HomeSpacing.STEP == 0 })
    }

    @Test
    fun a_stored_gap_past_the_maximum_still_shows_and_can_only_go_down() {
        // The favorites grew since it was chosen: the page shows what is stored and allows less, not more.
        show(gap = HomeSpacing.LIMIT, favorites = 3)
        compose.onNodeWithTag("spacing-value").assertTextEquals("${HomeSpacing.LIMIT} dp")
        compose.onNodeWithTag("spacing-more").assertIsNotEnabled()
        compose.onNodeWithTag("spacing-less").assertIsEnabled()
    }

    @Test
    fun the_controls_sit_above_the_clock_normally_and_move_below_the_favorites_once_the_gap_is_bigger() {
        var gap by mutableIntStateOf(HomeSpacing.DEFAULT)
        compose.setContent { HomeSpacingPage(preview(3), gap) { gap = it } }
        compose.onNodeWithTag("spacing-controls-top").assertIsDisplayed()
        gap = 480
        compose.waitForIdle()
        // With the favorites pushed up the clock would sit under a card at the top; the card goes to the free space below.
        compose.onNodeWithTag("spacing-controls-bottom").assertIsDisplayed()
        compose.onNodeWithTag("clock").assertIsDisplayed()
    }

    @Test
    fun with_no_favorites_there_is_no_fit_note() {
        show(favorites = 0)
        compose.onAllNodesWithTag("spacing-fit").assertCountEquals(0)
    }
}
