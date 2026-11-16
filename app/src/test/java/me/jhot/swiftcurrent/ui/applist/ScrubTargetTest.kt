package me.jhot.swiftcurrent.ui.applist

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The offset is how far the letter's header sits below the top of the visible area: 0 puts it at the top,
 * a negative value scrolls the list back so the block lands lower. At the very start or end of the list the
 * scroll limit clamps it, which is why A with a few apps and the last letters cannot centre. No padding is
 * added at the end: a blank gap after the last app would look like a bug, and the cog row already follows Z.
 */
class ScrubTargetTest {
    private val viewport = 1000

    @Test
    fun a_block_that_fits_is_centred_with_equal_space_above_and_below() {
        val block = ScrubTarget.blockHeight(headerPx = 100, rowPx = 100, rows = 3) // 400
        assertEquals(400, block)
        val offset = ScrubTarget.offset(block, viewport)
        assertEquals(-300, offset)
        assertEquals(1000 - 400 - (-offset), -offset) // space above equals space below
    }

    @Test
    fun a_block_taller_than_the_viewport_puts_its_header_at_the_top() {
        assertEquals(0, ScrubTarget.offset(1200, viewport))
    }

    @Test
    fun a_block_exactly_as_tall_as_the_viewport_puts_its_header_at_the_top() {
        assertEquals(0, ScrubTarget.offset(viewport, viewport))
    }

    @Test
    fun an_odd_leftover_is_split_within_one_pixel() {
        val offset = ScrubTarget.offset(401, viewport) // 599 left over
        val above = -offset
        val below = viewport - 401 - above
        assertEquals(299, above)
        assertEquals(300, below)
    }

    @Test
    fun a_header_alone_still_centres() {
        assertEquals(-450, ScrubTarget.offset(ScrubTarget.blockHeight(100, 100, 0), viewport))
    }

    @Test
    fun rows_of_different_heights_all_count_toward_the_block() {
        assertEquals(100 + 3 * 40, ScrubTarget.blockHeight(100, 40, 3))
        assertEquals(-(1000 - 340) / 2, ScrubTarget.offset(340, viewport))
    }

    @Test
    fun an_empty_viewport_does_not_move_the_header() {
        assertEquals(0, ScrubTarget.offset(100, 0))
    }
}
