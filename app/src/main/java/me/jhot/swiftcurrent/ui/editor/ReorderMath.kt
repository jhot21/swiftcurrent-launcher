package me.jhot.swiftcurrent.ui.editor

import kotlin.math.abs

/** The drag handle's arithmetic, free of Compose so it can be tested exhaustively. */
object ReorderMath {
    /** Distance between two neighbouring rows: the row plus the gap the list puts between rows. */
    fun pitch(rowHeightPx: Float, gapPx: Float): Float = rowHeightPx + gapPx

    /**
     * Where a row dragged by [dragOffsetPx] lands: one step per row pitch (see [pitch]; passing the bare height overshoots), the step taken once
     * the drag passes half a row. Clamped to the list; bad input leaves the row where it is.
     */
    fun targetIndex(from: Int, dragOffsetPx: Float, rowPitchPx: Float, count: Int): Int {
        if (count <= 0) return 0
        val start = from.coerceIn(0, count - 1)
        if (dragOffsetPx.isNaN() || rowPitchPx.isNaN() || rowPitchPx <= 0f) return start
        // Capped before the Int conversion so an absurd drag cannot overflow.
        val steps = minOf(abs(dragOffsetPx) / rowPitchPx + 0.5f, count.toFloat()).toInt()
        val delta = if (dragOffsetPx < 0f) -steps else steps
        return (start + delta).coerceIn(0, count - 1)
    }

    /**
     * How far the row at [index] steps aside to make room while row [from] is dragged to [to]:
     * rows the dragged one passes move one [rowPitchPx] the other way; the dragged row itself and
     * every row outside its path stay put (the dragged row follows the finger instead).
     */
    fun shiftFor(index: Int, from: Int, to: Int, rowPitchPx: Float): Float = when {
        index == from -> 0f
        to > from && index in (from + 1)..to -> -rowPitchPx
        to < from && index in to until from -> rowPitchPx
        else -> 0f
    }
}
