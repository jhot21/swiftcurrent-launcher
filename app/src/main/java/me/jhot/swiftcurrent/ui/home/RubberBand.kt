package me.jhot.swiftcurrent.ui.home

import kotlin.math.exp

/** How far the Home block can be pulled, as a fraction of one favorite row's height. */
const val RUBBER_BAND_TRAVEL_ROWS = 0.6f

/**
 * Resistance for a drag of [distance] against a total travel of [max]: it starts at the finger's own pace (slope 1)
 * and flattens toward [max], so the further the finger goes the less the block follows. Reaches [max] only in
 * floating point, for a huge distance; never negative.
 */
fun rubberBand(distance: Float, max: Float): Float {
    if (distance <= 0f || max <= 0f) return 0f
    return max * (1f - exp(-distance / max))
}
