package me.jhot.swiftcurrent.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderMathTest {
    private val h = 100f

    @Test
    fun no_drag_or_a_small_drag_stays_put() {
        assertEquals(2, ReorderMath.targetIndex(2, 0f, h, 5))
        assertEquals(2, ReorderMath.targetIndex(2, 49f, h, 5))
        assertEquals(2, ReorderMath.targetIndex(2, -49f, h, 5))
    }

    @Test
    fun crossing_half_a_row_moves_one_step_in_either_direction() {
        assertEquals(3, ReorderMath.targetIndex(2, 50f, h, 5))
        assertEquals(1, ReorderMath.targetIndex(2, -50f, h, 5))
        assertEquals(3, ReorderMath.targetIndex(2, 149f, h, 5))
        assertEquals(4, ReorderMath.targetIndex(2, 150f, h, 5))
        assertEquals(0, ReorderMath.targetIndex(2, -150f, h, 5))
    }

    @Test
    fun the_target_is_clamped_to_the_list() {
        assertEquals(4, ReorderMath.targetIndex(2, 10_000f, h, 5))
        assertEquals(0, ReorderMath.targetIndex(2, -10_000f, h, 5))
        assertEquals(0, ReorderMath.targetIndex(0, -300f, h, 5))
        assertEquals(4, ReorderMath.targetIndex(4, 300f, h, 5))
    }

    @Test
    fun degenerate_input_is_a_no_op() {
        assertEquals(2, ReorderMath.targetIndex(2, 300f, 0f, 5))
        assertEquals(2, ReorderMath.targetIndex(2, 300f, -10f, 5))
        assertEquals(2, ReorderMath.targetIndex(2, Float.NaN, h, 5))
        assertEquals(2, ReorderMath.targetIndex(2, 300f, Float.NaN, 5))
        assertEquals(0, ReorderMath.targetIndex(0, 300f, h, 1))
        assertEquals(0, ReorderMath.targetIndex(0, 300f, h, 0))
    }

    @Test
    fun a_from_index_outside_the_list_is_clamped_in() {
        assertEquals(4, ReorderMath.targetIndex(9, 0f, h, 5))
        assertEquals(0, ReorderMath.targetIndex(-3, 0f, h, 5))
    }
}

class ReorderPitchTest {
    // 56dp rows with the 16dp gap EditorPage puts between items.
    private val pitch = ReorderMath.pitch(56f, 16f)

    @Test
    fun targetIndex_uses_the_row_pitch_including_the_gap() {
        assertEquals(72f, pitch, 0f)
        // 2.9 pitches is past 2.5, so three rows; dividing by the bare height (3.7 rows) would give four.
        assertEquals(3, ReorderMath.targetIndex(0, 2.9f * pitch, pitch, 10))
        // The old divisor: the same physical drag overshoots to four rows.
        assertEquals(4, ReorderMath.targetIndex(0, 2.9f * pitch, 56f, 10))
        // Boundaries: just under 2.5 pitches stays at two rows, exactly 2.5 takes the third.
        assertEquals(2, ReorderMath.targetIndex(0, 2.49f * pitch, pitch, 10))
        assertEquals(3, ReorderMath.targetIndex(0, 2.5f * pitch, pitch, 10))
        assertEquals(1, ReorderMath.targetIndex(0, 0.5f * pitch, pitch, 10))
        assertEquals(0, ReorderMath.targetIndex(0, 0.49f * pitch, pitch, 10))
        assertEquals(5, ReorderMath.targetIndex(8, -3f * pitch, pitch, 10))
    }

    @Test
    fun rows_the_dragged_one_passes_step_aside_the_other_way() {
        // Dragging row 1 down to 3: rows 2 and 3 move up one pitch.
        assertEquals(0f, ReorderMath.shiftFor(0, 1, 3, 100f), 0f)
        assertEquals(0f, ReorderMath.shiftFor(1, 1, 3, 100f), 0f)
        assertEquals(-100f, ReorderMath.shiftFor(2, 1, 3, 100f), 0f)
        assertEquals(-100f, ReorderMath.shiftFor(3, 1, 3, 100f), 0f)
        assertEquals(0f, ReorderMath.shiftFor(4, 1, 3, 100f), 0f)
        // Dragging row 3 up to 1: rows 1 and 2 move down one pitch.
        assertEquals(0f, ReorderMath.shiftFor(0, 3, 1, 100f), 0f)
        assertEquals(100f, ReorderMath.shiftFor(1, 3, 1, 100f), 0f)
        assertEquals(100f, ReorderMath.shiftFor(2, 3, 1, 100f), 0f)
        assertEquals(0f, ReorderMath.shiftFor(3, 3, 1, 100f), 0f)
    }

    @Test
    fun nothing_steps_aside_when_the_drag_has_not_left_its_row() {
        for (i in 0..4) assertEquals(0f, ReorderMath.shiftFor(i, 2, 2, 100f), 0f)
    }
}
