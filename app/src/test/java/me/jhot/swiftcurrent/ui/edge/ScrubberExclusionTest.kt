package me.jhot.swiftcurrent.ui.edge

import androidx.compose.ui.unit.IntRect
import me.jhot.swiftcurrent.ui.applist.ScrubberDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrubberExclusionTest {
    private val strip = IntRect(900, 100, 1080, 2300)

    @Test
    fun exclusion_never_exceeds_the_cap() {
        val rects = ScrubberExclusion.rects(strip, capDp = 200, density = 2.75f)
        assertEquals(550, rects.sumOf { it.height })
        val small = ScrubberExclusion.rects(IntRect(0, 0, 100, 300), capDp = 200, density = 2f)
        assertTrue(small.sumOf { it.height } <= 400)
    }

    @Test
    fun exclusion_stays_inside_the_strip() {
        for (s in listOf(strip, IntRect(900, 100, 1080, 400), IntRect(10, 20, 30, 40))) {
            ScrubberExclusion.rects(s, 200, 3f).forEach {
                assertTrue(it.left >= s.left && it.right <= s.right && it.top >= s.top && it.bottom <= s.bottom)
            }
        }
    }

    @Test
    fun a_strip_shorter_than_the_cap_is_excluded_whole() {
        val s = IntRect(0, 0, 50, 300)
        assertEquals(listOf(s), ScrubberExclusion.rects(s, 200, 2f))
    }

    @Test
    fun degenerate_strips_give_nothing() {
        assertTrue(ScrubberExclusion.rects(IntRect(0, 0, 0, 500), 200, 2f).isEmpty())
    }

    @Test
    fun home_excludes_the_same_lower_middle_window_of_its_right_strip_as_the_list() {
        // 1080 x 2400 at density 2.75: the strip is the right 15%, the window is capped at 200dp.
        val rects = ScrubberExclusion.homeRects(width = 1080, height = 2400, capDp = 200, density = 2.75f)
        val strip = IntRect(1080 - 162, 0, 1080, 2400)
        assertEquals(ScrubberExclusion.rects(strip, 200, 2.75f), rects)
        val r = rects.single()
        assertEquals(918, r.left)
        assertEquals(1080, r.right)
        assertEquals(550, r.height)
        // Centred 60% down the screen, where a thumb on the right edge rests.
        assertEquals(1440, (r.top + r.bottom) / 2)
    }

    @Test
    fun the_home_strip_matches_the_list_scrubber_width() {
        // The list's scrubber is width * STRIP_FRACTION wide at the right edge; Home must agree.
        for (w in listOf(1080, 1079, 720, 1440)) {
            val r = ScrubberExclusion.homeRects(w, 2400, 200, 3f).single()
            assertEquals(w, r.right)
            assertEquals(Math.round(w * ScrubberDefaults.STRIP_FRACTION), r.width)
        }
    }

    @Test
    fun a_short_home_is_excluded_whole_and_an_empty_one_not_at_all() {
        assertEquals(listOf(IntRect(85, 0, 100, 300)), ScrubberExclusion.homeRects(100, 300, 200, 2f))
        assertTrue(ScrubberExclusion.homeRects(0, 2400, 200, 3f).isEmpty())
        assertTrue(ScrubberExclusion.homeRects(1080, 0, 200, 3f).isEmpty())
    }

    @Test
    fun the_window_is_centred_on_the_letter_track_and_as_tall_as_allowed() {
        // 2400px screen at 2.625: a 17-slot track of 20dp ends 42dp + nav inset above the bottom.
        val d = 2.625f
        val span = ScrubberExclusion.trackSpan(2400, topPx = 80, bottomPx = (66 * d).toInt(), slotPx = 20 * d, count = 17)!!
        val r = ScrubberExclusion.homeRects(1080, 2400, 200, d, span).single()
        assertEquals((200 * d).toInt(), r.height)
        assertTrue(Math.abs((span.first + span.last) / 2 - (r.top + r.bottom) / 2) <= 1)
    }

    @Test
    fun a_track_shorter_than_the_cap_is_covered_whole_and_the_window_stays_on_screen() {
        val d = 2f
        val span = ScrubberExclusion.trackSpan(2000, 0, 100, 20 * d, count = 5)!!
        val r = ScrubberExclusion.homeRects(1000, 2000, 200, d, span).single()
        assertTrue(r.top <= span.first && r.bottom >= span.last)
        // Near the bottom edge the window is pushed up rather than off screen.
        val low = ScrubberExclusion.homeRects(1000, 2000, 200, d, 1990..2000).single()
        assertEquals(2000, low.bottom)
        assertEquals(400, low.height)
    }

    @Test
    fun the_track_span_agrees_with_the_scrubbers_own_geometry() {
        // The window must sit where the letters are, so drift between the two formulas would show here.
        for ((h, count) in listOf(2400 to 17, 2400 to 3, 800 to 40, 1500 to 0)) {
            val slot = 52f
            val g = me.jhot.swiftcurrent.ui.applist.Geometry(
                h.toFloat(), 100f, count, slot, 60f, 180f, 1f, 1f, 1f, 1f, 1f,
            )
            val span = ScrubberExclusion.trackSpan(h, 60, 180, slot, count)
            if (count == 0) assertEquals(null, span) else {
                assertEquals(g.trackTop.toInt(), span!!.first)
                assertEquals(g.trackBottom.toInt(), span.last)
            }
        }
    }
}
