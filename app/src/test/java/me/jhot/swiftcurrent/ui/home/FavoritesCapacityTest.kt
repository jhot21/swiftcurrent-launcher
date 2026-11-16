package me.jhot.swiftcurrent.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesCapacityTest {
    private fun metrics(
        height: Float = 2424f / (420f / 160f),
        top: Float = HomeLayoutDefaults.TYPICAL_TOP_INSET_DP,
        bottom: Float = HomeLayoutDefaults.TYPICAL_BOTTOM_INSET_DP,
        fontScale: Float = 1f,
    ) = HomeMetrics(
        heightDp = height,
        topInsetDp = top,
        bottomInsetDp = bottom,
        fontScale = fontScale,
        rowHeightDp = HomeLayoutDefaults.ROW_HEIGHT_DP,
        headerBlockHeightDp = HomeLayoutDefaults.HEADER_BLOCK_HEIGHT_DP,
        bottomGapDp = HomeLayoutDefaults.BOTTOM_GAP_DP,
    )

    @Test
    fun capacity_on_pixel_9a_fits_at_least_seven() {
        assertTrue(FavoritesCapacity.max(metrics()) >= 7)
    }

    @Test
    fun capacity_never_negative_at_font_scale_2() {
        assertEquals(0, FavoritesCapacity.max(metrics(height = 200f, fontScale = 2f)))
        assertTrue(FavoritesCapacity.max(metrics(fontScale = 2f)) >= 0)
    }

    @Test
    fun capacity_shrinks_as_font_scale_grows() {
        val small = FavoritesCapacity.max(metrics(fontScale = 0.85f))
        val normal = FavoritesCapacity.max(metrics(fontScale = 1f))
        val big = FavoritesCapacity.max(metrics(fontScale = 2f))
        assertTrue("$small $normal $big", small >= normal && normal > big)
    }

    @Test
    fun n_rows_never_exceed_available_height_minus_gap() {
        for (h in 400..1200 step 7) for (top in listOf(0f, 24f, 48f)) for (bottom in listOf(0f, 24f, 48f)) {
            for (fs in listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 2f)) {
                val m = metrics(height = h.toFloat(), top = top, bottom = bottom, fontScale = fs)
                val n = FavoritesCapacity.max(m)
                assertTrue(n >= 0)
                val used = n * m.rowHeightDp * fs + m.headerBlockHeightDp * fs
                val limit = h - top - bottom - m.bottomGapDp
                assertTrue("h=$h fs=$fs n=$n used=$used limit=$limit", n == 0 || used <= limit + 1e-3f)
            }
        }
    }

    @Test
    fun a_larger_bottom_gap_leaves_room_for_fewer_favorites_and_a_smaller_one_for_more() {
        fun cap(gap: Float) = FavoritesCapacity.max(homeMetrics(891f, 32f, 24f, 1f, bottomGapDp = gap))
        assertTrue(cap(96f) < cap(32f))
        assertTrue(cap(0f) >= cap(32f))
        // The default is the old fixed gap, so the capacity is unchanged for anyone who never touches the setting.
        assertEquals(FavoritesCapacity.max(homeMetrics(891f, 32f, 24f, 1f)), cap(HomeLayoutDefaults.BOTTOM_GAP_DP))
    }

    @Test
    fun the_largest_gap_is_exactly_the_one_at_which_every_favorite_still_fits() {
        for (n in listOf(0, 1, 3, 7)) for (fs in listOf(0.85f, 1f, 1.3f)) {
            val m = metrics(fontScale = fs)
            val max = FavoritesCapacity.maxGapDp(m, n)
            val rows = maxOf(n, 1)
            // At the largest gap all of them fit; one dp more and the last one no longer does.
            assertTrue("n=$n fs=$fs", FavoritesCapacity.max(m.copy(bottomGapDp = max - 0.01f)) >= rows)
            assertTrue("n=$n fs=$fs", FavoritesCapacity.max(m.copy(bottomGapDp = max + 1f)) < rows)
        }
    }

    @Test
    fun more_favorites_leave_less_room_for_the_gap_and_a_larger_font_leaves_less_too() {
        val m = metrics()
        assertTrue(FavoritesCapacity.maxGapDp(m, 1) > FavoritesCapacity.maxGapDp(m, 5))
        assertTrue(FavoritesCapacity.maxGapDp(m, 3) > FavoritesCapacity.maxGapDp(metrics(fontScale = 1.5f), 3))
    }

    @Test
    fun the_largest_gap_is_never_negative_even_when_nothing_fits() {
        assertEquals(0f, FavoritesCapacity.maxGapDp(metrics(height = 200f, fontScale = 2f), 5), 0f)
    }

    @Test
    fun with_a_few_favorites_there_is_far_more_than_the_old_96_dp_of_room() {
        assertTrue(FavoritesCapacity.maxGapDp(metrics(), 3) > 300f)
    }

    @Test
    fun a_stored_gap_is_cut_to_what_keeps_the_header_on_screen() {
        val tall = metrics(height = 800f)
        // Fine where it was chosen.
        assertEquals(300f, FavoritesCapacity.clampGapDp(tall.copy(bottomGapDp = 300f)), 0f)
        // The same value on a short screen (rotated, or a bigger font) would push the clock off the top.
        val short = metrics(height = 360f)
        val room = short.heightDp - short.topInsetDp - short.bottomInsetDp - short.headerBlockHeightDp * short.fontScale
        assertEquals(room, FavoritesCapacity.clampGapDp(short.copy(bottomGapDp = 600f)), 0.001f)
        // Never negative, even when the header alone does not fit.
        assertEquals(0f, FavoritesCapacity.clampGapDp(metrics(height = 150f).copy(bottomGapDp = 100f)), 0f)
    }

    @Test
    fun fit_keeps_the_stored_gap_when_everything_fits() {
        val m = metrics()
        val fit = FavoritesCapacity.fit(m, 3)
        assertEquals(3, fit.rows)
        assertEquals(m.bottomGapDp, fit.gapDp, 0f)
    }

    @Test
    fun fit_shrinks_the_gap_before_dropping_rows() {
        val m = metrics()
        val atStoredGap = FavoritesCapacity.max(m)
        val most = FavoritesCapacity.maxRows(m)
        // The test only means something if the stored gap really costs a row.
        assertTrue(most > atStoredGap)
        val fit = FavoritesCapacity.fit(m, atStoredGap + 1)
        assertEquals(atStoredGap + 1, fit.rows)
        assertTrue("gap ${fit.gapDp}", fit.gapDp < m.bottomGapDp)
        assertEquals(FavoritesCapacity.maxGapDp(m, fit.rows), fit.gapDp, 0.001f)
    }

    @Test
    fun fit_drops_rows_once_the_gap_has_shrunk_to_the_leftover() {
        val m = metrics()
        val most = FavoritesCapacity.maxRows(m)
        val fit = FavoritesCapacity.fit(m, most + 5)
        assertEquals(most, fit.rows)
        assertTrue("gap ${fit.gapDp}", fit.gapDp >= 0f && fit.gapDp < m.rowHeightDp * m.fontScale)

        // Room that divides into whole rows leaves nothing over.
        val exact = metrics(height = 32f + 24f + HomeLayoutDefaults.HEADER_BLOCK_HEIGHT_DP + 10 * HomeLayoutDefaults.ROW_HEIGHT_DP)
        val full = FavoritesCapacity.fit(exact, 20)
        assertEquals(10, full.rows)
        assertEquals(0f, full.gapDp, 0f)
    }

    @Test
    fun fit_never_grows_the_gap_past_the_stored_setting() {
        for (stored in listOf(0f, 8f, 32f, 96f)) for (n in listOf(0, 1, 5, 40)) {
            val m = metrics().copy(bottomGapDp = stored)
            assertTrue("stored=$stored n=$n", FavoritesCapacity.fit(m, n).gapDp <= stored)
        }
    }

    @Test
    fun fit_with_larger_font_scale_still_leaves_the_header_on_screen() {
        for (fs in listOf(1f, 1.3f, 2f)) for (n in listOf(0, 3, 40)) {
            val m = metrics(fontScale = fs, height = 500f).copy(bottomGapDp = 200f)
            assertTrue("fs=$fs n=$n", FavoritesCapacity.fit(m, n).gapDp <= FavoritesCapacity.clampGapDp(m) + 0.001f)
        }
    }
}
