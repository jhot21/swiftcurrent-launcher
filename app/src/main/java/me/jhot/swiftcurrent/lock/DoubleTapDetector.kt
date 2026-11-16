package me.jhot.swiftcurrent.lock

import kotlin.math.abs

sealed interface TapOutcome {
    data class Jump(val letter: Char) : TapOutcome
    data object Lock : TapOutcome
}

/**
 * Rule: a tap within [timeoutMs] of the previous tap and within [MAX_LETTER_DISTANCE] letters of it
 * locks, because the owner double-taps around one spot on the alphabet rather than on one exact
 * letter, while two stray touches far apart on the strip are not a double tap. A tap that is too
 * late or too far away is a fresh jump and starts a new double tap. The first tap always jumps at
 * once (never delayed to wait for a second one), and a lock resets the detector so the next tap
 * is a fresh jump. Time comes only from the caller, so tests need no clock.
 */
class DoubleTapDetector(private val timeoutMs: Long) {
    private var lastTapMs: Long? = null
    private var lastLetter: Char = ' '

    fun onTap(letter: Char, nowMs: Long): TapOutcome {
        val last = lastTapMs
        if (last != null && nowMs - last in 0..timeoutMs && abs(slot(letter) - slot(lastLetter)) <= MAX_LETTER_DISTANCE) {
            lastTapMs = null
            return TapOutcome.Lock
        }
        lastTapMs = nowMs
        lastLetter = letter
        return TapOutcome.Jump(letter)
    }

    // The '#' slot sits just before A on the strip.
    private fun slot(c: Char) = if (c == '#') '@'.code else c.code

    companion object {
        const val MAX_LETTER_DISTANCE = 3
    }
}
