package me.jhot.swiftcurrent.ui.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SwipeThreshold = 48.dp

/**
 * Calls [onSwipe] once, while the finger is still moving, when a drag to the right gets clearly past
 * touch slop. Only a rightward drag is claimed (and consumed, so the row's tap does not also fire on
 * release): a drag that turns out mostly vertical belongs to the list and one to the left is no one's,
 * and both are left alone. The rows sit inside the page gutter, clear of the system back gesture.
 */
fun Modifier.swipeRight(onSwipe: () -> Unit): Modifier = composed {
    // The gesture loop starts once, so it reads the callback through this, not the one it was started with.
    val current by rememberUpdatedState(onSwipe)
    pointerInput(Unit) {
        val threshold = SwipeThreshold.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val slop = viewConfiguration.touchSlop
            var dx = 0f
            var dy = 0f
            var fired = false
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val moved = change.positionChange()
                dx += moved.x
                dy += moved.y
                when {
                    // Clearly rightward: ours from here on.
                    dx > slop && dx > kotlin.math.abs(dy) -> {
                        change.consume()
                        if (!fired && dx >= threshold) { fired = true; current() }
                    }
                    // Mostly vertical, or to the left: not ours.
                    kotlin.math.abs(dy) > slop && kotlin.math.abs(dy) > kotlin.math.abs(dx) -> break
                    dx < -slop -> break
                }
            }
        }
    }
}

/**
 * A bottom sheet that follows the finger. [offset] is how far down it has been dragged (never up);
 * on release a flick down, or a drag past [DISMISS_FRACTION] of its [height], slides it away and calls
 * [onDismiss], and anything less springs it back to rest, as the Material guidelines describe.
 */
@Stable
class SheetDrag(private val scope: CoroutineScope, private val flickPxPerSecond: Float, private val onDismiss: () -> Unit) {
    var offset by mutableFloatStateOf(0f)
        private set

    /** The sheet's own height in pixels, reported by its layout; the distance that clears the screen. */
    var height by mutableFloatStateOf(0f)

    /** 1 at rest, fading to 0 as the sheet leaves, so the scrim lifts with it. */
    val scrimAlpha: Float get() = if (height <= 0f) 1f else (1f - offset / height).coerceIn(0f, 1f)

    val state = DraggableState { delta -> offset = (offset + delta).coerceAtLeast(0f) }

    fun release(velocity: Float) {
        scope.launch {
            val away = velocity > flickPxPerSecond || offset > height * DISMISS_FRACTION
            if (away) {
                animate(offset, maxOf(height, offset), animationSpec = tween(180)) { v, _ -> offset = v }
                onDismiss()
            } else {
                animate(offset, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> offset = v }
            }
        }
    }

    companion object {
        const val DISMISS_FRACTION = 0.4f

        /** A downward flick faster than this closes the sheet however short the drag. */
        val FLICK = 700.dp
    }
}

@Composable
fun rememberSheetDrag(onDismiss: () -> Unit): SheetDrag {
    val scope = rememberCoroutineScope()
    val flick = with(LocalDensity.current) { SheetDrag.FLICK.toPx() }
    val latest by rememberUpdatedState(onDismiss)
    return remember(scope, flick) { SheetDrag(scope, flick) { latest() } }
}

/** Dragging this area (a sheet's handle and title) moves the whole sheet with the finger. */
fun Modifier.dragSheet(drag: SheetDrag): Modifier =
    draggable(drag.state, Orientation.Vertical, onDragStopped = { velocity -> drag.release(velocity) })

/** Shifts the sheet down by how far it has been dragged. */
fun Modifier.sheetOffset(drag: SheetDrag): Modifier = offset { IntOffset(0, drag.offset.roundToInt()) }
