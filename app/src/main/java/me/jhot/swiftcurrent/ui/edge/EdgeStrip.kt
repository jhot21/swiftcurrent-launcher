package me.jhot.swiftcurrent.ui.edge

import androidx.compose.ui.unit.IntRect
import kotlin.math.roundToInt

/**
 * How much of the screen's right edge belongs to the app-list strip. Home uses it to decide that a
 * swipe up from there opens the list instead of search, and the list's scrubber is this wide, so
 * the two never disagree about where the strip is.
 */
const val EDGE_FRACTION = 0.15f

/**
 * The platform keeps only about [CAP_DP] of excluded height per edge, so the full strip cannot
 * be excluded on a tall screen. We spend the budget on the lower-middle of the strip, where a
 * thumb resting on the right edge scrubs, and leave the far top and bottom to the system gesture.
 */
object ScrubberExclusion {
    const val CAP_DP = 200

    /** Window centre as a fraction down the strip. */
    private const val CENTER_FRACTION = 0.6f

    fun rects(strip: IntRect, capDp: Int, density: Float): List<IntRect> {
        if (strip.width <= 0 || strip.height <= 0) return emptyList()
        val cap = (capDp * density).toInt()
        if (cap <= 0) return emptyList()
        if (strip.height <= cap) return listOf(strip)
        val top = (strip.top + strip.height * CENTER_FRACTION - cap / 2f).toInt()
            .coerceIn(strip.top, strip.bottom - cap)
        return listOf(IntRect(strip.left, top, strip.right, top + cap))
    }

    /**
     * The same window for Home's right strip, in Home's own coordinates. Home spans the screen
     * like the list does, so the window sits in the same place on both and does not move when
     * the list opens under the finger.
     */
    fun homeRects(width: Int, height: Int, capDp: Int, density: Float): List<IntRect> {
        if (width <= 0 || height <= 0) return emptyList()
        val strip = IntRect(width - (width * EDGE_FRACTION).roundToInt(), 0, width, height)
        return rects(strip, capDp, density)
    }
}
