package me.jhot.swiftcurrent.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import me.jhot.swiftcurrent.core.Entry
import me.jhot.swiftcurrent.core.Target
import me.jhot.swiftcurrent.ui.edge.EDGE_FRACTION
import me.jhot.swiftcurrent.ui.edge.ScrubberExclusion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

enum class HomeGesture { OpenSearch, OpenAppListScrub, OpenPopup, OpenShade, None }

/** Pure so the rules can be tested without a device; the pointer code only measures and calls it. */
object GestureClassifier {
    /** Fallback for [thresholdPx]; the modifier passes the real touch slop instead. */
    const val DEFAULT_THRESHOLD_PX = 48f

    fun classify(
        startX: Float, startY: Float, dx: Float, dy: Float,
        widthPx: Float, heightPx: Float, bottomInsetPx: Float, leftBackMarginPx: Float,
        thresholdPx: Float = DEFAULT_THRESHOLD_PX,
    ): HomeGesture = when {
        dy <= -thresholdPx && abs(dy) > abs(dx) -> when {
            // Under the system gesture strip a swipe up is the home gesture, not ours.
            startY >= heightPx - bottomInsetPx -> HomeGesture.None
            startX >= widthPx * (1f - EDGE_FRACTION) -> HomeGesture.OpenAppListScrub
            else -> HomeGesture.OpenSearch
        }
        // Closer to the left edge than the margin is the system back gesture.
        dx >= thresholdPx && abs(dx) > abs(dy) && startX >= leftBackMarginPx -> HomeGesture.OpenPopup
        // A launcher owns the pull-down: the system only opens the shade from the status bar.
        dy >= thresholdPx && abs(dy) > abs(dx) -> HomeGesture.OpenShade
        else -> HomeGesture.None
    }

    /**
     * Whether a touch landing at this point belongs to the edge scrubber. Decided on touch-down,
     * before any movement, so the letters can appear the moment the finger lands. The same strip
     * and bottom-inset rule as [classify] uses for [HomeGesture.OpenAppListScrub].
     */
    fun isEdgeStart(startX: Float, startY: Float, widthPx: Float, heightPx: Float, bottomInsetPx: Float): Boolean =
        startY < heightPx - bottomInsetPx && startX >= widthPx * (1f - EDGE_FRACTION)

    /**
     * The vertical distance (signed, in px) the Home block may follow: the drag's `dy` while it is vertical, otherwise
     * 0, and 0 for a swipe up that starts under the system gesture strip (that one is the system's). Vertical means
     * vertical leads sideways; once a drag [wasVertical] it stays so until sideways leads by a clear margin
     * ([STAY_VERTICAL_RATIO]), so a wobbling finger near the diagonal does not snap the block back and forth.
     * The block is at rest whenever the drag is not vertical, so the row bounds recorded for the pop-up swipe stay valid.
     */
    fun bandDistance(startY: Float, dx: Float, dy: Float, heightPx: Float, bottomInsetPx: Float, wasVertical: Boolean = false): Float {
        val vertical = abs(dy) > abs(dx) || (wasVertical && abs(dx) <= STAY_VERTICAL_RATIO * abs(dy))
        return when {
            !vertical -> 0f
            dy < 0f && startY >= heightPx - bottomInsetPx -> 0f
            else -> dy
        }
    }

    /** How far sideways may lead a vertical drag before it stops counting as vertical (see [bandDistance]). */
    const val STAY_VERTICAL_RATIO = 1.25f

    /** A tick re-arms only after the drag has dropped under this share of the threshold, so jitter at the line is quiet. */
    const val REARM_FRACTION = 0.8f

    /**
     * Whether the drag so far would open search (up) or pull the shade (down) if the finger lifted now. It is
     * checked on every move to tick the moment the drag becomes one of them; nothing opens until the lift.
     */
    fun armed(
        startX: Float, startY: Float, dx: Float, dy: Float,
        widthPx: Float, heightPx: Float, bottomInsetPx: Float, leftBackMarginPx: Float,
        thresholdPx: Float = DEFAULT_THRESHOLD_PX,
    ): Boolean = when (classify(startX, startY, dx, dy, widthPx, heightPx, bottomInsetPx, leftBackMarginPx, thresholdPx)) {
        HomeGesture.OpenSearch, HomeGesture.OpenShade -> true
        else -> false
    }
}

/**
 * Receives a finger that landed on Home's right strip, for its whole life, in host (root)
 * coordinates. [HomeGestureHost] calls its `onAppList` first, then [down]; the list opens on top
 * but this finger stays with Home's detector, which forwards it here.
 */
interface EdgeTouch {
    fun down(y: Float)

    /** [moved]: the finger has gone past touch slop at some point in this gesture (sticky). */
    fun move(y: Float, moved: Boolean)
    fun up(y: Float, moved: Boolean)

    /** The system took the pointer, or the detector went away (a reset) before the lift. */
    fun cancel()
}

sealed interface TapAction {
    data class Launch(val component: String) : TapAction
    data class LaunchShortcut(val key: String) : TapAction
    data object OpenPopup : TapAction
}

/** An app with a custom pop-up still launches on tap; only a swipe opens its pop-up. */
fun tapAction(entry: Entry): TapAction = when (val t = entry.target) {
    is Target.App -> TapAction.Launch(t.component)
    is Target.Shortcut -> TapAction.LaunchShortcut(t.key)
    Target.Folder -> TapAction.OpenPopup
}

/**
 * Where each visible row is, so the single root gesture detector can tell which item a swipe
 * started on. Bounds are in root coordinates, which equal the detector's local ones as long
 * as it sits at the root, as [HomeGestureHost] does.
 */
class HomeGestureTargets {
    private val bounds = LinkedHashMap<String, Pair<Rect, Entry>>()

    fun update(entry: Entry, rect: Rect) { bounds[entry.id] = rect to entry }

    fun remove(entry: Entry) { bounds.remove(entry.id) }

    fun hit(at: Offset): Entry? = bounds.values.firstOrNull { it.first.contains(at) }?.second

    /** Where the row for [entry] is on screen right now, or null when it is not a visible Home row. */
    fun boundsOf(entry: Entry): Rect? = bounds[entry.id]?.first
}

val LocalHomeGestureTargets = staticCompositionLocalOf<HomeGestureTargets?> { null }

/** Marks a row as an item the gestures can target. No-op outside a [HomeGestureHost]. */
@Composable
fun Modifier.homeGestureTarget(entry: Entry): Modifier {
    val targets = LocalHomeGestureTargets.current ?: return this
    DisposableEffect(entry.id) { onDispose { targets.remove(entry) } }
    return onGloballyPositioned { targets.update(entry, it.boundsInRoot()) }
}

/** Provides the registry to the rows and puts the one gesture detector at the root. */
@Composable
fun HomeGestureHost(
    onSearch: () -> Unit,
    onAppList: () -> Unit,
    onItemPopup: (Entry) -> Unit,
    onItemLongPress: (Entry) -> Unit,
    enabled: Boolean = true,
    edge: EdgeTouch? = null,
    onShade: () -> Unit = {},
    // Where the Home block is pulled to; the host owns it unless a test or the shell wants to read it.
    drag: HomeDrag = remember { HomeDrag() },
    // Called each time a drag becomes one that would open search or pull the shade on lift.
    onArmed: () -> Unit = {},
    // The shell passes its own so it can ask where a row is (to anchor a pop-up next to it).
    targets: HomeGestureTargets = remember { HomeGestureTargets() },
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current.density
    val fontScale = LocalDensity.current.fontScale
    // One row's height in px at this font scale, as HomeScreen sizes its rows; the block can be pulled a fraction of it.
    val travelPx = with(LocalDensity.current) { (HomeLayoutDefaults.ROW_HEIGHT_DP * fontScale).dp.toPx() } * RUBBER_BAND_TRAVEL_ROWS
    CompositionLocalProvider(LocalHomeGestureTargets provides targets) {
        Box(
            Modifier.fillMaxSize()
                .homeGestures(onSearch, onAppList, onItemPopup, onItemLongPress, targets, enabled, edge, onShade, drag, travelPx, onArmed)
                // Last in the chain so toggling it never rebuilds the detector mid-gesture. Off while
                // something covers Home, so the system back gesture works there.
                .then(if (enabled) Modifier.systemGestureExclusion { homeExclusion(it, density) } else Modifier),
        ) {
            // An inner box, so the detector's own box (and its hit-testing) stays where it is.
            Box(Modifier.fillMaxSize().graphicsLayer { translationY = drag.offsetPx }) { content() }
        }
    }
}

// Without it, gesture navigation takes a swipe on the right edge as back before Home sees it.
private fun homeExclusion(coordinates: LayoutCoordinates, density: Float): Rect {
    val size = coordinates.size
    val r = ScrubberExclusion.homeRects(size.width, size.height, ScrubberExclusion.CAP_DP, density)
        .firstOrNull() ?: return Rect.Zero
    return Rect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())
}

private enum class Outcome { Tap, Swipe, Ignored }

private const val LEFT_BACK_MARGIN_DP = 24

// Well past touch slop, so a sloppy tap or a drag while scrolling past never opens anything.
private const val SWIPE_THRESHOLD_DP = 48

/**
 * The only place home gestures are detected. It watches on the Initial pass and leaves a plain
 * tap alone so it reaches the rows' own click handlers. Once a press becomes a swipe or a long
 * press, its remaining events are consumed so the row does not also count it as a tap. Search and
 * the shade open when the finger lifts past the threshold, not while it is still moving, and a cancelled
 * gesture opens nothing. Other
 * drags (left, diagonal) are consumed too when they start on a row, for the same reason;
 * started anywhere else they are left for children (a scrolling list). [enabled] = false turns
 * it fully off for the next gesture, and then a swipe on a row may tap it, which is fine because
 * overlays cover the rows. A gesture already under way when it flips to false still ends consumed.
 *
 * A touch that lands on the right strip ([GestureClassifier.isEdgeStart]) is the edge scrubber's
 * from touch-down: [onAppList] fires at once, every event of that finger is consumed (the strip is
 * reserved, so a row under it is never tapped or long-pressed) and the finger goes to [edge].
 */
fun Modifier.homeGestures(
    onSearch: () -> Unit,
    onAppList: () -> Unit,
    onItemPopup: (Entry) -> Unit,
    onItemLongPress: (Entry) -> Unit,
    targets: HomeGestureTargets? = null,
    enabled: Boolean = true,
    edge: EdgeTouch? = null,
    onShade: () -> Unit = {},
    drag: HomeDrag? = null,
    travelPx: Float = 0f,
    onArmed: () -> Unit = {},
): Modifier = composed {
    val registry = targets ?: LocalHomeGestureTargets.current
    val density = LocalDensity.current
    val config = LocalViewConfiguration.current
    val gestureInsets = WindowInsets.systemGestures
    val search by rememberUpdatedState(onSearch)
    val appList by rememberUpdatedState(onAppList)
    val popup by rememberUpdatedState(onItemPopup)
    val longPress by rememberUpdatedState(onItemLongPress)
    val edgeNow by rememberUpdatedState(edge)
    val shade by rememberUpdatedState(onShade)
    // Read through rememberUpdatedState like the callbacks above: the pointerInput block below captures once, and
    // onArmed closes over the owner's haptic settings.
    val dragNow by rememberUpdatedState(drag)
    val travelNow by rememberUpdatedState(travelPx)
    val armedNow by rememberUpdatedState(onArmed)
    val scope = rememberCoroutineScope()
    // Read per gesture, not a pointerInput key: a long press opens an overlay that turns this off
    // while the finger is still down, and restarting here would stop consuming that gesture, so
    // the row under the finger would also receive the release as a tap.
    val enabledNow by rememberUpdatedState(enabled)

    pointerInput(registry) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // Off while an overlay (app list, search, pop-up) is up: nothing is classified or consumed.
            if (!enabledNow) return@awaitEachGesture
            val start = down.position
            val slop = config.touchSlop
            val size: IntSize = this.size
            val bottomInset = gestureInsets.getBottom(density).toFloat()
            val backMargin = maxOf(
                density.run { LEFT_BACK_MARGIN_DP.dp.toPx() },
                gestureInsets.getLeft(density, LayoutDirection.Ltr).toFloat(),
            )
            val threshold = with(density) { SWIPE_THRESHOLD_DP.dp.toPx() }
            var last = start

            // The Home block follows a vertical drag with resistance, and each time the drag becomes one that would open
            // search or the shade on lift, it ticks. The block is 0 whenever the drag is not vertical.
            var wasVertical = false
            // Ticked since the drag last dropped well under the threshold: one tick per real crossing, not per jitter.
            var ticked = false
            var lastShown = 0f
            fun track(d: Offset) {
                val band = GestureClassifier.bandDistance(start.y, d.x, d.y, size.height.toFloat(), bottomInset, wasVertical)
                wasVertical = band != 0f
                val shown = if (band == 0f) 0f else sign(band) * rubberBand(max(0f, abs(band) - slop), travelNow)
                if (shown != lastShown) {
                    lastShown = shown
                    dragNow?.follow(scope, shown)
                }
                val armed = GestureClassifier.armed(
                    startX = start.x, startY = start.y, dx = d.x, dy = d.y,
                    widthPx = size.width.toFloat(), heightPx = size.height.toFloat(),
                    bottomInsetPx = bottomInset, leftBackMarginPx = backMargin, thresholdPx = threshold,
                )
                if (armed && !ticked) {
                    ticked = true
                    armedNow()
                } else if (ticked && (!wasVertical || abs(d.y) < GestureClassifier.REARM_FRACTION * threshold)) {
                    ticked = false
                }
            }

            if (GestureClassifier.isEdgeStart(start.x, start.y, size.width.toFloat(), size.height.toFloat(), bottomInset)) {
                down.consume()
                // Captured now: the list that opens turns this host off, and the finger must still reach the same receiver.
                val receiver = edgeNow
                appList()
                receiver?.down(start.y)
                var moved = false
                var lifted = false
                // finally: a system cancel or a reset (this detector torn down) must not leave the scrub pressed.
                try {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes
                            .firstOrNull { it.id == down.id } ?: break
                        // A system cancel arrives as a synthetic release that is already consumed;
                        // a real lift reaches this root untouched. Treat it as a cancel, not a lift.
                        if (change.isConsumed) break
                        change.consume()
                        if (change.changedToUpIgnoreConsumed()) {
                            lifted = true
                            receiver?.up(change.position.y, moved)
                            break
                        }
                        if (!moved && (change.position - start).getDistance() > slop) moved = true
                        receiver?.move(change.position.y, moved)
                    }
                } finally {
                    if (!lifted) receiver?.cancel()
                }
                return@awaitEachGesture
            }

            // Null means the finger stayed down and still past the timeout: a long press.
            // Only a drag that is mostly up or mostly right can open something (the only two gestures
            // the classifier accepts). Any other drag is swallowed when it starts on a row and left
            // for children everywhere else.
            val ended = withTimeoutOrNull(config.longPressTimeoutMillis) {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes
                        .firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull Outcome.Ignored
                    last = change.position
                    if (change.changedToUpIgnoreConsumed()) return@withTimeoutOrNull Outcome.Tap
                    val d = last - start
                    if (d.getDistance() > slop) {
                        val up = d.y < 0f && abs(d.y) > abs(d.x)
                        val right = d.x > 0f && abs(d.x) > abs(d.y)
                        val down = d.y > 0f && abs(d.y) > abs(d.x)
                        if (!up && !right && !down) {
                            // On a row, swallow it too: the row's click ignores slop and would
                            // launch the app on release. Off any row it stays free for children.
                            if (registry?.hit(start) == null) return@withTimeoutOrNull Outcome.Ignored
                            change.consume()
                            return@withTimeoutOrNull Outcome.Swipe
                        }
                        // Row click handlers ignore touch slop, so without this a swipe that ends
                        // on the same row would also count as a tap on it.
                        change.consume()
                        track(d)
                        return@withTimeoutOrNull Outcome.Swipe
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                Outcome.Ignored
            }

            if (ended == null) {
                registry?.hit(start)?.let { longPress(it) }
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes
                        .firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (change.changedToUpIgnoreConsumed()) break
                }
                return@awaitEachGesture
            }
            if (ended != Outcome.Swipe) return@awaitEachGesture

            // The block always settles back, however the gesture ends (a lift, a cancel, this detector torn down).
            try {
                // Nothing opens until the finger lifts. A system cancel arrives as a synthetic release that is already
                // consumed (the edge path above relies on the same), and a pointer that vanishes ends the loop with no
                // lift at all; neither may act on the last position.
                var lifted = false
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes
                        .firstOrNull { it.id == down.id } ?: break
                    last = change.position
                    val consumedBefore = change.isConsumed
                    change.consume()
                    track(last - start)
                    if (change.changedToUpIgnoreConsumed()) {
                        lifted = !consumedBefore
                        break
                    }
                }
                if (!lifted) return@awaitEachGesture

                val gesture = GestureClassifier.classify(
                    startX = start.x, startY = start.y, dx = last.x - start.x, dy = last.y - start.y,
                    widthPx = size.width.toFloat(), heightPx = size.height.toFloat(),
                    bottomInsetPx = bottomInset, leftBackMarginPx = backMargin, thresholdPx = threshold,
                )
                when (gesture) {
                    HomeGesture.OpenSearch -> search()
                    // A strip touch never gets here: it was handed to the edge scrubber on touch-down.
                    HomeGesture.OpenAppListScrub -> Unit
                    HomeGesture.OpenPopup -> registry?.hit(start)?.let { popup(it) }
                    HomeGesture.OpenShade -> shade()
                    HomeGesture.None -> Unit
                }
            } finally {
                dragNow?.release(scope)
            }
        }
    }
}
