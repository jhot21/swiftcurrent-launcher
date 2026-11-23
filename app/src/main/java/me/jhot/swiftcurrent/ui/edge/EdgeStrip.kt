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
 * The whole right strip is kept from the system back gesture, so a thumb can start a scrub anywhere on
 * it. An ordinary app gets only about 200dp of excluded height per edge, but the platform lifts that
 * cap for the home activity, which is what the default launcher runs as. When the app is not the
 * default home the system clips this back to its own limit.
 */
object ScrubberExclusion {
    fun rects(strip: IntRect): List<IntRect> =
        if (strip.width <= 0 || strip.height <= 0) emptyList() else listOf(strip)

    /** Home's right strip in Home's own coordinates; it spans the screen like the list does, so the two agree. */
    fun homeRects(width: Int, height: Int): List<IntRect> {
        if (width <= 0 || height <= 0) return emptyList()
        return rects(IntRect(width - (width * EDGE_FRACTION).roundToInt(), 0, width, height))
    }
}
