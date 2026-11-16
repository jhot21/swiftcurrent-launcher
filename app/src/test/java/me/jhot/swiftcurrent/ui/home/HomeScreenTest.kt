package me.jhot.swiftcurrent.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.Target
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
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val gap = HomeLayoutDefaults.BOTTOM_GAP_DP
    private val topInset = HomeLayoutDefaults.TYPICAL_TOP_INSET_DP
    private val bottomInset = HomeLayoutDefaults.TYPICAL_BOTTOM_INSET_DP

    private fun entry(id: String) = Entry(id = id, target = Target.App("pkg.$id/.Main"))

    private fun row(id: String, label: String, missing: Boolean = false) = HomeRow(entry(id), label, missing)

    private fun state(favorites: List<HomeRow>, event: String? = "Team meeting · today", anyStored: Boolean = favorites.isNotEmpty()) = HomeUiState(
        clock = "7:20", date = "Sun, Nov 8", nextEvent = event, favorites = favorites, anyStored = anyStored,
    )

    private val names = listOf("Media", "Maps", "Messages", "Browser", "Home Assistant", "Memos", "Camera")
    private val longEvent = "An extremely long calendar event title " + "that keeps going ".repeat(12)

    private fun drawn(total: Int) =
        (1..total).filter { compose.onAllNodesWithTag("favorite-r$it").fetchSemanticsNodes().isNotEmpty() }

    private fun rootHeight() = compose.onRoot().getBoundsInRoot().height.value

    private fun metrics(fs: Float, top: Float, bottom: Float) = HomeMetrics(
        rootHeight(), top, bottom, fs, HomeLayoutDefaults.ROW_HEIGHT_DP,
        HomeLayoutDefaults.HEADER_BLOCK_HEIGHT_DP, gap,
    )

    private var live by mutableStateOf(state(emptyList()))

    // Paused before the content shows, so the first frame is really the first frame.
    private fun showLive(initial: HomeUiState, onItem: (Entry) -> Unit = {}, bottomGapDp: Float = gap, bottom: Float = bottomInset) {
        live = initial
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box {
                HomeScreen(live, {}, {}, onItem, insets = WindowInsets(top = topInset.dp, bottom = bottom.dp), bottomGapDp = bottomGapDp)
                // The same rows animated on their own, invisible and never short of room: what each row's height should be.
                Column(Modifier.alpha(0f)) {
                    AnimatedRows(live.favorites, key = { it.entry.id }) { r, _ ->
                        Box(Modifier.width(10.dp).height(HomeLayoutDefaults.ROW_HEIGHT_DP.dp).testTag("ref-${r.entry.id}"))
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    // A write made under a paused clock reaches the recomposer only once the snapshot is applied.
    private fun updateLive(next: HomeUiState) {
        compose.runOnUiThread {
            live = next
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun clockTop() = compose.onNodeWithTag("clock").getBoundsInRoot().top.value

    @Test
    fun home_rows_are_full_height_on_the_first_frame() {
        showLive(state((1..3).map { row("r$it", "App $it") }))
        for (i in 1..3) {
            val b = compose.onNodeWithTag("favorite-r$i").getBoundsInRoot()
            assertEquals(HomeLayoutDefaults.ROW_HEIGHT_DP, (b.bottom - b.top).value, 0.5f)
        }
    }

    @Test
    fun a_new_favorite_makes_the_clock_glide_up_over_the_animation() {
        showLive(state((1..2).map { row("r$it", "App $it") }))
        val before = clockTop()
        updateLive(state((1..3).map { row("r$it", "App $it") }))
        compose.mainClock.advanceTimeBy(HOME_ROW_MS / 2L)
        val mid = clockTop()
        assertTrue("half way, clock top $mid between $before and ${before - HomeLayoutDefaults.ROW_HEIGHT_DP}", mid < before - 0.5f && mid > before - HomeLayoutDefaults.ROW_HEIGHT_DP + 0.5f)
        compose.mainClock.advanceTimeBy(HOME_ROW_MS + 64L)
        assertEquals(before - HomeLayoutDefaults.ROW_HEIGHT_DP, clockTop(), 0.5f)
    }

    // The tightest fit: the bottom inset takes up exactly the room left over at capacity (891 dp window, 172 dp
    // header, 56 dp rows: 47 dp), so with a larger stored gap the gap is 0 at capacity and a whole row more one row below.
    private val tightBottom = bottomInset + 47f
    private val tightGap = 60f

    private fun rowsOf(n: Int) = (1..n).map { row("r$it", "App $it") }

    private fun heightOf(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot().let { (it.bottom - it.top).value }

    // A frame that had to squash a row to fit shows as a row shorter than the same row animating on its own; the gap
    // (measured last) is what should give way. The header must stay fully on screen and Home must not scroll.
    private fun assertNothingSquashedEveryFrame(frames: Int, label: String, ids: List<String>) {
        repeat(frames) { frame ->
            compose.mainClock.advanceTimeByFrame()
            for (id in ids) {
                val refs = compose.onAllNodesWithTag("ref-$id").fetchSemanticsNodes()
                val homes = compose.onAllNodesWithTag("favorite-$id").fetchSemanticsNodes()
                if (refs.isEmpty() || homes.isEmpty()) continue
                assertEquals("$label frame $frame row $id", heightOf("ref-$id"), heightOf("favorite-$id"), 0.5f)
            }
            assertTrue("$label frame $frame clock top ${clockTop()}", clockTop() >= topInset - 0.5f)
            val config = compose.onNodeWithTag("favorites").fetchSemanticsNode().config
            assertFalse(config.contains(SemanticsProperties.VerticalScrollAxisRange))
        }
    }

    private fun capacity(): Int {
        val m = metrics(1f, topInset, tightBottom)
        val c = FavoritesCapacity.maxRows(m)
        assertTrue("needs room for the case, capacity $c", c >= 3)
        assertEquals("the case needs no room left over at capacity", 0f, FavoritesCapacity.maxGapDp(m, c), 0.5f)
        return c
    }

    @Test
    fun adding_the_last_slot_never_overflows_at_any_frame() {
        showLive(state(listOf(row("r1", "App 1"))), bottomGapDp = tightGap, bottom = tightBottom)
        val capacity = capacity()
        updateLive(state(rowsOf(capacity - 1)))
        compose.mainClock.advanceTimeBy(HOME_ROW_MS + 64L)
        updateLive(state(rowsOf(capacity)))
        assertNothingSquashedEveryFrame(HOME_ROW_MS / 16 + 6, "add", rowsOf(capacity).map { it.entry.id })
        val m = metrics(1f, topInset, tightBottom).copy(bottomGapDp = tightGap)
        val last = compose.onNodeWithTag("favorite-r$capacity").getBoundsInRoot()
        val limit = m.heightDp - tightBottom - FavoritesCapacity.fit(m, capacity).gapDp
        assertTrue("last bottom ${last.bottom} limit $limit", last.bottom.value <= limit + 0.5f)
    }

    @Test
    fun removing_the_last_slot_never_overflows_at_any_frame() {
        showLive(state(listOf(row("r1", "App 1"))), bottomGapDp = tightGap, bottom = tightBottom)
        val capacity = capacity()
        updateLive(state(rowsOf(capacity)))
        compose.mainClock.advanceTimeBy(HOME_ROW_MS + 64L)
        updateLive(state(rowsOf(capacity - 1)))
        assertNothingSquashedEveryFrame(HOME_ROW_MS / 16 + 6, "remove", rowsOf(capacity).map { it.entry.id })
    }

    @Test
    fun a_row_that_leaves_and_returns_never_overflows_at_any_frame() {
        showLive(state(listOf(row("r1", "App 1"))), bottomGapDp = tightGap, bottom = tightBottom)
        val capacity = capacity()
        updateLive(state(rowsOf(capacity)))
        compose.mainClock.advanceTimeBy(HOME_ROW_MS + 64L)
        updateLive(state(rowsOf(capacity - 1)))
        assertNothingSquashedEveryFrame(6, "leaving", rowsOf(capacity).map { it.entry.id })
        // Turning round mid-animation: the row regrows while the gap shrinks again.
        updateLive(state(rowsOf(capacity)))
        assertNothingSquashedEveryFrame(HOME_ROW_MS / 16 * 2 + 6, "returning", rowsOf(capacity).map { it.entry.id })
    }

    @Test
    fun a_leaving_favorite_cannot_be_tapped() {
        val tapped = mutableListOf<String>()
        showLive(state(rowsOf(2)), onItem = { tapped += it.id })
        updateLive(state(listOf(row("r1", "App 1"))))
        // Right away the leaving row is still full height, so the click lands on it and only the guard can stop it.
        compose.onNodeWithTag("favorite-r2").assertHasNoClickAction().performClick()
        assertEquals(emptyList<String>(), tapped)
        // A row that is staying is tappable in the very same state.
        compose.onNodeWithTag("favorite-r1").performClick()
        assertEquals(listOf("r1"), tapped)
    }

    @Test
    fun the_hint_fades_in_as_the_last_favorite_leaves() {
        showLive(state(listOf(row("r1", "App 1")), anyStored = true))
        updateLive(state(emptyList(), anyStored = false))
        compose.mainClock.advanceTimeBy(HOME_ROW_MS / 2L)
        compose.onNodeWithTag("favorite-r1").assertIsDisplayed()
        compose.onNodeWithTag("empty-favorites-hint").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(HOME_ROW_MS + 64L)
        compose.onAllNodesWithTag("favorite-r1").assertCountEquals(0)
        compose.onNodeWithTag("empty-favorites-hint").assertIsDisplayed()
    }

    @Test
    fun home_has_no_scroll_semantics_and_last_row_clears_bottom_gap() {
        val rows = (1..40).map { row("r$it", "App $it") }
        compose.setContent { HomeScreen(state(rows), {}, {}, {}) }
        val config = compose.onNodeWithTag("favorites").fetchSemanticsNode().config
        assertFalse(config.contains(SemanticsProperties.VerticalScrollAxisRange))
        assertFalse(config.contains(SemanticsActions.ScrollBy))
        val last = compose.onNodeWithTag("favorite-r${drawn(40).last()}").getBoundsInRoot()
        assertTrue(last.bottom.value <= rootHeight() - gap + 0.5f)
    }

    @Test
    fun real_layout_never_overflows_and_matches_capacity() {
        val rows = (1..60).map { row("r$it", "App $it") }
        var fs by mutableStateOf(1f)
        var event by mutableStateOf<String?>(null)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fs)) {
                HomeScreen(
                    state(rows, event), {}, {}, {},
                    insets = WindowInsets(top = topInset.dp, bottom = bottomInset.dp),
                )
            }
        }
        for (scale in listOf(0.85f, 1f, 1.3f, 2f)) for (ev in listOf("Team meeting · today", null, longEvent)) {
            fs = scale
            event = ev
            compose.waitForIdle()
            val case = "fs=$scale event=${ev?.take(8)}"
            val m = metrics(scale, topInset, bottomInset)
            val ids = drawn(60)
            assertEquals(case, FavoritesCapacity.maxRows(m), ids.size)
            val last = compose.onNodeWithTag("favorite-r${ids.last()}").getBoundsInRoot()
            val limit = m.heightDp - bottomInset - FavoritesCapacity.fit(m, 60).gapDp
            assertTrue("$case last bottom ${last.bottom} limit $limit", last.bottom.value <= limit + 0.5f)
            val clock = compose.onNodeWithTag("clock").getBoundsInRoot()
            assertTrue("$case clock top ${clock.top} inset $topInset", clock.top.value >= topInset - 0.5f)
            val header = compose.onNodeWithTag("favorite-r${ids.first()}").getBoundsInRoot().top.value - clock.top.value
            assertTrue(
                "$case header $header > estimate",
                header <= HomeLayoutDefaults.HEADER_BLOCK_HEIGHT_DP * scale + 0.5f,
            )
        }
    }

    @Test
    fun a_long_event_title_truncates_but_keeps_its_time_and_count() {
        compose.setContent {
            HomeScreen(
                HomeUiState("7:20", "Sun, Nov 8", longEvent + " \u00b7 today +1", emptyList(), anyStored = false, nextEventTail = " \u00b7 today +1"),
                {}, {}, {}, insets = WindowInsets(top = topInset.dp, bottom = bottomInset.dp),
            )
        }
        compose.onNodeWithTag("event-tail", useUnmergedTree = true).assertExists().assertIsDisplayed()
        val root = compose.onRoot().getBoundsInRoot().right.value
        val tail = compose.onNodeWithTag("event-tail", useUnmergedTree = true).getBoundsInRoot()
        assertTrue("tail ${tail.right} must end inside the right gutter", tail.right.value <= root - Tokens.homeGutter.value + 0.5f)
    }

    @Test
    fun hint_is_not_shown_when_favorites_exist_but_all_are_hidden() {
        compose.setContent { HomeScreen(state(emptyList(), anyStored = true), {}, {}, {}) }
        compose.onAllNodesWithTag("empty-favorites-hint").assertCountEquals(0)
    }

    @Test
    fun hint_is_shown_when_nothing_is_stored() {
        compose.setContent { HomeScreen(state(emptyList(), anyStored = false), {}, {}, {}) }
        compose.onNodeWithTag("empty-favorites-hint").assertIsDisplayed()
    }

    @Test
    fun home_text_starts_at_the_wider_gutter() {
        compose.setContent {
            HomeScreen(state(listOf(row("r1", "Media"))), {}, {}, {}, insets = WindowInsets(top = topInset.dp, bottom = bottomInset.dp))
        }
        assertEquals(Tokens.homeGutter.value, compose.onNodeWithTag("clock").getBoundsInRoot().left.value, 0.5f)
    }

    @Test
    fun an_archived_favorite_wears_the_download_mark() {
        compose.setContent {
            HomeScreen(
                state(listOf(HomeRow(entry("r1"), "Gone", missing = false, archived = true), row("r2", "Here"))), {}, {}, {},
                insets = WindowInsets(top = topInset.dp, bottom = bottomInset.dp),
            )
        }
        compose.onNodeWithTag("archived-r1", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithTag("archived-r2", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun a_long_label_stays_on_one_line_in_its_row_at_double_font_scale() {
        val label = "An extremely long favorite name that would wrap onto several lines " + "and keep going ".repeat(6)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                HomeScreen(
                    state(listOf(row("r1", label), row("r2", "Short"))), {}, {}, {},
                    insets = WindowInsets(top = topInset.dp, bottom = bottomInset.dp),
                )
            }
        }
        compose.waitForIdle()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText(label).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        assertEquals("one line, ellipsized rather than wrapped and clipped", 1, layouts.single().lineCount)
        assertTrue("the end of the label is cut with an ellipsis", layouts.single().isLineEllipsized(0))
    }

    @Test
    fun injected_insets_are_counted_once() {
        val rows = (1..60).map { row("r$it", "App $it") }
        compose.setContent {
            HomeScreen(state(rows), {}, {}, {}, insets = WindowInsets(top = 60.dp, bottom = 80.dp))
        }
        val m = metrics(1f, 60f, 80f)
        val ids = drawn(60)
        assertEquals(FavoritesCapacity.maxRows(m), ids.size)
        val last = compose.onNodeWithTag("favorite-r${ids.last()}").getBoundsInRoot()
        // Within one row of the limit means the insets were not subtracted a second time.
        val limit = m.heightDp - 80f - FavoritesCapacity.fit(m, 60).gapDp
        assertTrue("bottom ${last.bottom} limit $limit", last.bottom.value <= limit + 0.5f)
        assertTrue("bottom ${last.bottom} limit $limit", last.bottom.value > limit - HomeLayoutDefaults.ROW_HEIGHT_DP)
        assertTrue(compose.onNodeWithTag("clock").getBoundsInRoot().top.value >= 60f - 0.5f)
    }

    @Test
    fun favorites_beyond_capacity_are_not_shown_but_stay_saved() {
        val saved = (1..40).map { row("r$it", "App $it") }
        val before = saved.toList()
        compose.setContent { HomeScreen(state(saved), {}, {}, {}) }
        val cap = FavoritesCapacity.max(metrics(1f, 0f, 0f))
        assertTrue(cap in 7 until 40)
        assertEquals((1..cap).toList(), drawn(40))
        assertEquals(before, saved)
    }

    @Test
    fun rows_that_fit_only_after_the_gap_shrinks_are_all_shown() {
        val rows = (1..40).map { row("r$it", "App $it") }
        compose.setContent { HomeScreen(state(rows), {}, {}, {}, insets = WindowInsets(top = 0.dp, bottom = 20.dp)) }
        val m = metrics(1f, 0f, 20f)
        val atStoredGap = FavoritesCapacity.max(m)
        val most = FavoritesCapacity.maxRows(m)
        // Only meaningful if the stored gap really costs a row.
        assertTrue(most > atStoredGap)
        assertEquals((1..most).toList(), drawn(40))
        val last = compose.onNodeWithTag("favorite-r$most").getBoundsInRoot()
        assertTrue("bottom ${last.bottom} of ${m.heightDp}", last.bottom.value <= m.heightDp - 20f + 0.5f)
    }

    @Test
    fun uninstalled_favorite_is_not_shown_and_is_flagged_missing() {
        val saved = listOf(entry("a"), entry("b"), entry("c"))
        val rows = HomeRows.build(saved, installed = { it != "pkg.b/.Main" }, labelFor = { "App ${it.id}" })
        assertEquals(listOf(false, true, false), rows.map { it.missing })
        compose.setContent { HomeScreen(state(rows), {}, {}, {}) }
        compose.onNodeWithTag("favorite-a").assertExists()
        compose.onAllNodesWithTag("favorite-b").assertCountEquals(0)
        compose.onNodeWithTag("favorite-c").assertExists()
        assertEquals(3, saved.size)
    }

    @Test
    fun clock_tap_invokes_onClock() {
        var clock = 0
        var agenda = 0
        compose.setContent { HomeScreen(state(emptyList()), { clock++ }, { agenda++ }, {}) }
        compose.onNodeWithTag("clock").performClick()
        assertEquals(1, clock)
        compose.onNodeWithTag("date").performClick()
        compose.onNodeWithTag("event").performClick()
        assertEquals(2, agenda)
        assertEquals(1, clock)
    }

    @Test
    fun row_tap_invokes_onItem() {
        var tapped: Entry? = null
        val rows = listOf(row("a", "Media"))
        compose.setContent { HomeScreen(state(rows), {}, {}, { tapped = it }) }
        compose.onNodeWithTag("favorite-a").performClick()
        assertEquals(rows[0].entry, tapped)
    }

    @Test
    fun home_pixel9a() {
        val rows = names.mapIndexed { i, n -> row("s$i", n) }
        compose.setContent { HomeScreen(state(rows), {}, {}, {}) }
        compose.onRoot().captureRoboImage("src/test/snapshots/home_pixel9a.png")
    }

    @Test
    fun the_owners_bottom_gap_moves_the_last_favorite_by_exactly_that_much() {
        var gapDp by mutableStateOf(gap)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2.625f, 1f)) {
                HomeScreen(
                    state(listOf(row("r1", "Maps"), row("r2", "Media"))),
                    onClock = {}, onAgenda = {}, onItem = {},
                    insets = WindowInsets(0, 0, 0, 0), bottomGapDp = gapDp,
                )
            }
        }
        fun lastBottom() = compose.onNodeWithTag("favorite-r2").getBoundsInRoot().bottom.value
        val atDefault = lastBottom()
        gapDp = gap + 40f
        compose.waitForIdle()
        assertEquals(atDefault - 40f, lastBottom(), 0.5f)
        gapDp = 0f
        compose.waitForIdle()
        assertEquals(atDefault + gap, lastBottom(), 0.5f)
    }
}
