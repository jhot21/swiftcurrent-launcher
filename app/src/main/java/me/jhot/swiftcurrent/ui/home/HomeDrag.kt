package me.jhot.swiftcurrent.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** How long the Home block takes to settle back to rest once the finger lifts. */
internal const val DRAG_RELEASE_MS = 200

/**
 * How far the Home block is pulled, in px: read inside a graphics layer, so a drag never recomposes. Both calls
 * run on the [CoroutineScope] they are given, undispatched, so [follow] has taken effect when it returns; an
 * Animatable lets a later call cancel an earlier one, so a drag that starts during a return simply takes over.
 */
@Stable
class HomeDrag {
    private val offset = Animatable(0f)

    val offsetPx: Float get() = offset.value

    /** Puts the block at [px] now, cancelling any return in progress. */
    fun follow(scope: CoroutineScope, px: Float) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { offset.snapTo(px) }
    }

    /** Eases the block back to rest; a later [follow] cancels it. */
    fun release(scope: CoroutineScope) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            offset.animateTo(0f, tween(DRAG_RELEASE_MS, easing = FastOutSlowInEasing))
        }
    }
}
