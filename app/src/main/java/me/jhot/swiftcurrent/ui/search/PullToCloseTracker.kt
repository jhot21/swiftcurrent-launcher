package me.jhot.swiftcurrent.ui.search

/**
 * Decides when a downward drag on the results is a request to close. Only the part of the drag the
 * list could not scroll itself counts, so a list that is mid-scroll never closes, and one gesture
 * closes at most once (the same drag keeps reporting leftover after the close).
 */
class PullToCloseTracker(private val thresholdPx: Float) {
    private var pulled = 0f
    private var fired = false

    /** [dy] is the drag since the last event (positive is down); [listConsumedY] how much of it the list scrolled. Returns true to close. */
    fun onDrag(dy: Float, listConsumedY: Float): Boolean {
        if (dy <= 0f) {
            pulled = 0f
            return false
        }
        pulled += (dy - listConsumedY).coerceAtLeast(0f)
        if (pulled >= thresholdPx && !fired) {
            fired = true
            return true
        }
        return false
    }

    fun onFlingEnd() {
        pulled = 0f
        fired = false
    }
}
