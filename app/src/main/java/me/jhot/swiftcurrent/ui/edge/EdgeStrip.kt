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
 * be excluded on a tall screen. We spend all of it on the middle of the letter track (the part a
 * thumb scrubs) and leave the rest to the system gesture. Android offers no way for an app to
 * raise the limit, so a track taller than the cap always has its far ends uncovered.
 */
object ScrubberExclusion {
    const val CAP_DP = 200

    /** Window centre as a fraction down the strip, used when the track's place is not known. */
    private const val CENTER_FRACTION = 0.6f

    /**
     * Where the letter track lies, top to bottom in px, mirroring the scrubber's own layout: it is pinned
     * [bottomPx] above the bottom and each of [count] letters takes [slotPx], shrunk if the screen is short.
     * Null with no letters.
     */
    fun trackSpan(heightPx: Int, topPx: Int, bottomPx: Int, slotPx: Float, count: Int): IntRange? {
        if (count <= 0) return null
        val bottom = (heightPx - bottomPx).toFloat()
        val slot = minOf(slotPx, maxOf(0f, bottom - topPx) / count)
        return (bottom - slot * count).toInt()..bottom.toInt()
    }

    /** The window is centred on [track] when given, else [CENTER_FRACTION] down the strip. */
    fun rects(strip: IntRect, capDp: Int, density: Float, track: IntRange? = null): List<IntRect> {
        if (strip.width <= 0 || strip.height <= 0) return emptyList()
        val cap = (capDp * density).toInt()
        if (cap <= 0) return emptyList()
        if (strip.height <= cap) return listOf(strip)
        val centre = if (track != null) (track.first + track.last) / 2f else strip.top + strip.height * CENTER_FRACTION
        val top = (centre - cap / 2f).toInt()
            .coerceIn(strip.top, strip.bottom - cap)
        return listOf(IntRect(strip.left, top, strip.right, top + cap))
    }

    /**
     * The same window for Home's right strip, in Home's own coordinates. Home spans the screen
     * like the list does, so the window sits in the same place on both and does not move when
     * the list opens under the finger.
     */
    fun homeRects(width: Int, height: Int, capDp: Int, density: Float, track: IntRange? = null): List<IntRect> {
        if (width <= 0 || height <= 0) return emptyList()
        val strip = IntRect(width - (width * EDGE_FRACTION).roundToInt(), 0, width, height)
        return rects(strip, capDp, density, track)
    }
}
