package me.jhot.swiftcurrent.ui.edge

import androidx.compose.ui.unit.IntRect
import me.jhot.swiftcurrent.ui.applist.ScrubberDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrubberExclusionTest {
    @Test
    fun the_whole_strip_is_excluded_however_tall_it_is() {
        // Far past the 200dp an ordinary app is allowed: a launcher's home activity is exempt from that cap.
        val strip = IntRect(900, 100, 1080, 2300)
        assertEquals(listOf(strip), ScrubberExclusion.rects(strip))
    }

    @Test
    fun degenerate_strips_give_nothing() {
        assertTrue(ScrubberExclusion.rects(IntRect(0, 0, 0, 500)).isEmpty())
        assertTrue(ScrubberExclusion.rects(IntRect(0, 0, 50, 0)).isEmpty())
        assertTrue(ScrubberExclusion.homeRects(0, 2400).isEmpty())
        assertTrue(ScrubberExclusion.homeRects(1080, 0).isEmpty())
    }

    @Test
    fun home_excludes_its_whole_right_strip_top_to_bottom() {
        // 1080 x 2400: the strip is the right 15%.
        assertEquals(listOf(IntRect(1080 - 162, 0, 1080, 2400)), ScrubberExclusion.homeRects(1080, 2400))
    }

    @Test
    fun the_home_strip_matches_the_list_scrubber_width() {
        // The list's scrubber is width * STRIP_FRACTION wide at the right edge; Home must agree.
        for (w in listOf(1080, 1079, 720, 1440)) {
            val r = ScrubberExclusion.homeRects(w, 2400).single()
            assertEquals(w, r.right)
            assertEquals(Math.round(w * ScrubberDefaults.STRIP_FRACTION), r.width)
            assertEquals(0, r.top)
            assertEquals(2400, r.bottom)
        }
    }
}
