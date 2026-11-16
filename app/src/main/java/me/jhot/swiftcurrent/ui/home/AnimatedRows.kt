package me.jhot.swiftcurrent.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment

/** One row of an animated list: still wanted, or [leaving] while its exit plays. */
internal data class Slot<T>(val key: Any, val item: T, val leaving: Boolean)

/**
 * The rows to draw after [items] changed: every item in order, plus each [previous] slot that is no longer wanted,
 * kept as leaving right after the nearest earlier slot that is still there (first if none), so a row fades out
 * where it was instead of jumping to an end.
 */
internal fun <T> mergeSlots(previous: List<Slot<T>>, items: List<T>, key: (T) -> Any): List<Slot<T>> {
    val result = items.map { Slot(key(it), it, leaving = false) }.toMutableList()
    val wanted = result.map { it.key }.toSet()
    var anchor: Any? = null
    for (slot in previous) {
        if (slot.key in wanted) {
            anchor = slot.key
            continue
        }
        val at = if (anchor == null) 0 else result.indexOfFirst { it.key == anchor } + 1
        result.add(at, slot.copy(leaving = true))
        anchor = slot.key
    }
    return result
}

/** How long a row takes to grow in or shrink out, and the gap under the rows to follow; a starting value, judged on a device. */
internal const val HOME_ROW_MS = 200

/** What [AnimatedRows] remembers between compositions: the slots, and each row's own transition. */
private class RowsHolder<T> {
    var started = false
    var slots: List<Slot<T>> = emptyList()
    val states = mutableMapOf<Any, MutableTransitionState<Boolean>>()
}

/**
 * [items] as rows that grow and fade in when they arrive and shrink and fade out when they go, so a bottom-anchored
 * column glides everything above them with it. Rows present the first time are shown at once. A row that has left
 * stays composed, told `leaving = true`, until its exit has finished.
 */
@Composable
internal fun <T> ColumnScope.AnimatedRows(
    items: List<T>,
    key: (T) -> Any,
    content: @Composable (item: T, leaving: Boolean) -> Unit,
) {
    val holder = remember { RowsHolder<T>() }
    // Read so that dropping a finished row recomposes the list.
    var version by remember { mutableIntStateOf(0) }
    version.let { }
    val first = !holder.started
    holder.started = true
    holder.slots = mergeSlots(holder.slots, items, key)
    holder.slots.forEach { slot -> holder.states.getOrPut(slot.key) { MutableTransitionState(first) } }
    // The target is set after composition, so the transition never changes under the composition that reads it.
    SideEffect { holder.slots.forEach { holder.states[it.key]?.targetState = !it.leaving } }
    holder.slots.forEach { slot ->
        key(slot.key) {
            val state = holder.states.getValue(slot.key)
            AnimatedVisibility(
                visibleState = state,
                enter = expandVertically(tween(HOME_ROW_MS), expandFrom = Alignment.Top) + fadeIn(tween(HOME_ROW_MS)),
                exit = shrinkVertically(tween(HOME_ROW_MS), shrinkTowards = Alignment.Top) + fadeOut(tween(HOME_ROW_MS)),
            ) { content(slot.item, slot.leaving) }
            if (slot.leaving && state.isIdle && !state.targetState) {
                SideEffect {
                    holder.slots = holder.slots.filter { it.key != slot.key }
                    holder.states.remove(slot.key)
                    version++
                }
            }
        }
    }
}
