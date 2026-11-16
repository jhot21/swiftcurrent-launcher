package me.jhot.swiftcurrent.ui.applist

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import me.jhot.swiftcurrent.catalog.Section
import me.jhot.swiftcurrent.ui.design.AppText
import me.jhot.swiftcurrent.ui.design.AppType
import me.jhot.swiftcurrent.ui.design.Tokens
import me.jhot.swiftcurrent.ui.design.cogGlyph
import me.jhot.swiftcurrent.ui.design.starGlyph
import me.jhot.swiftcurrent.ui.edge.EDGE_FRACTION
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt

/** Every number the app list and its scrubber use, so the feel is tuned in one place. */
object ScrubberDefaults {
    /** Favorites jump; drawn as an outlined star. */
    const val STAR = '☆'

    /** Settings, after Z; drawn as a small ring like the reference. */
    const val COG = '⚙'

    /** The reserved right-hand strip; shared with the home edge swipe so both agree. */
    const val STRIP_FRACTION = EDGE_FRACTION

    /** How far the touched letter bends left, as a multiple of the strip width (the reference bends past the strip). */
    const val PEAK_FRACTION = 1.0f

    /** Short enough to read as a response, not an animation; letters never wait for it. */
    const val BEND_IN_MS = 90

    /** The search button fades out under a scrub, as in the reference, and back at rest. */
    const val SEARCH_FADE_MS = 90

    /** Distance along the track at which the bend has fully decayed. */
    val bendRadius: Dp = 150.dp

    /** Pitch of one scrubber entry; matches AppType.label line height. */
    val slotHeight: Dp = 20.dp

    val bubbleSize: Dp = 52.dp

    /** Space between the bubble and the bent letter. */
    val bubbleGap: Dp = 8.dp

    val cogRing: Dp = 7.dp
    val starSize: Dp = 13.dp

    /** A lift this far past the track's end still counts as on the end slot; further out is a stray touch. */
    val liftTolerance: Dp = 6.dp
}

/** Star, #, the letters that have apps, then the cog. # stays even when empty so the strip keeps its shape. */
fun scrubberLetters(sections: List<Section>): List<Char> =
    buildList {
        add(ScrubberDefaults.STAR)
        add('#')
        sections.forEach { if (it.key != '#') add(it.key) }
        add(ScrubberDefaults.COG)
    }

fun scrubTag(c: Char): String = when (c) {
    ScrubberDefaults.STAR -> "scrub-star"
    ScrubberDefaults.COG -> "scrub-cog"
    else -> "scrub-$c"
}

/**
 * The scrubber's geometry, kept free of Compose so it can be tested exhaustively. Letters sit in
 * equal slots between top and bottom; a letter's position is its slot centre.
 */
object ScrubberMath {
    fun indexAt(y: Float, top: Float, bottom: Float, count: Int): Int {
        require(count > 0) { "no letters" }
        val height = bottom - top
        if (height <= 0f || y.isNaN()) return 0
        val i = floor((y - top) / height * count)
        return i.coerceIn(0f, (count - 1).toFloat()).toInt()
    }

    /**
     * Bend of letter [index] for a finger at [touchY], from 1 (under the finger) to 0 at [radiusPx]
     * and beyond. A raised cosine, so the curve has no corner at the peak or at the edge. The
     * finger is clamped to the first and last centres so the bend follows the selected letter
     * when the finger runs past the track. Allocation free: the draw path calls it per letter.
     */
    fun offsetAt(index: Int, count: Int, touchY: Float, top: Float, bottom: Float, radiusPx: Float): Float {
        if (count <= 0 || radiusPx <= 0f) return 0f
        val slot = (bottom - top) / count
        val first = top + slot / 2f
        val last = bottom - slot / 2f
        val y = if (touchY.isNaN()) first else touchY.coerceIn(minOf(first, last), maxOf(first, last))
        val d = abs(top + slot * (index + 0.5f) - y)
        if (d >= radiusPx) return 0f
        return (0.5 * (1.0 + cos(PI * d / radiusPx))).toFloat()
    }

    /**
     * Whether a lift at [y] is on the first entry (the star): its own slot or anywhere above it. The star
     * is the top end of the strip and the way back to favorites, and the bubble shows the star for a
     * finger above the strip (see [indexAt]), so a lift up there must do what the bubble says; a tiny
     * band left most real swipes up to the star as misses.
     */
    fun onFirstSlot(y: Float, top: Float, bottom: Float, count: Int): Boolean {
        if (count <= 0 || y.isNaN()) return false
        val slot = (bottom - top) / count
        // Exclusive at the slot's lower edge: as in [indexAt], the line between slots belongs to the one below.
        return y < top + slot
    }

    /**
     * Whether a lift at [y] is on the last entry's own slot (the cog), allowing [tolerancePx]
     * below it. Unlike [indexAt] it does not clamp, so lifting far under the track is not a hit.
     */
    fun onLastSlot(y: Float, top: Float, bottom: Float, count: Int, tolerancePx: Float): Boolean {
        if (count <= 0 || y.isNaN()) return false
        val slot = (bottom - top) / count
        return y >= bottom - slot && y <= bottom + tolerancePx
    }
}

/** Finger state read only in layout and draw, so moving the finger never recomposes. */
@Stable
internal class ScrubState {
    val y = mutableFloatStateOf(0f)
    var pressed by mutableStateOf(false)
    var index by mutableIntStateOf(-1)

    /** Counts lifts, so a press that ended before the list composed once still starts the reveal; a level alone would miss it. */
    var releases by mutableIntStateOf(0)

    /** The other letters' rows while one letter is held: 0 while pressed, fading to 1 after the lift. */
    val othersAlpha = Animatable(1f)
}

/**
 * The right-hand strip. One pointerInput handles down, move and up in a single pass: the first
 * down selects and jumps at once, with no slop or delay.
 *
 * - [onJump] fires on the press and on every letter change while dragging (the letters, plus the
 *   star when no [onStar] is supplied; never the cog), so the list follows the finger.
 * - [onPress] fires only for the touch-down itself, once per press, on a letter (or the star when
 *   it has no [onStar]). A press on the cog, or on the star when it is an action slot, never
 *   fires it. Drag jumps never fire it: they can come milliseconds apart and must not read as the
 *   double tap that locks the phone.
 * - [onCog] fires on release only when the lift point is inside the cog's own slot (plus
 *   [ScrubberDefaults.liftTolerance] below it). Clamping still highlights the cog when the
 *   finger runs past the track, but lifting far below it does nothing.
 * - [onStar] is the same for the first entry when a screen supplies one: the star stops jumping
 *   and fires on release when the lift point is its own slot or anywhere above it (the bubble shows
 *   the star there too), the same for a finger that opened the list from Home. Without it the star is
 *   an ordinary jump target, as the editor needs.
 *
 * [state] is hoisted so the screen can hide the search button while a scrub is pressed.
 * [glyph] draws every entry when a screen has its own icons (the editor); by default letters, the
 * star and the cog are drawn here. Entries that are not letters or `#` use it in the bubble too.
 * [external] lets a finger that landed on Home drive the same rules, as if it had landed here.
 */
@Composable
internal fun Scrubber(
    letters: List<Char>,
    bottomPadding: Dp,
    topPadding: Dp,
    onJump: (Char) -> Unit,
    onPress: (Char) -> Unit,
    onCog: () -> Unit,
    modifier: Modifier = Modifier,
    state: ScrubState = remember { ScrubState() },
    glyph: (@Composable (Char, Color, Modifier) -> Unit)? = null,
    external: EdgeScrub? = null,
    onStar: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val bend = animateFloatAsState(
        if (state.pressed) 1f else 0f, tween(ScrubberDefaults.BEND_IN_MS), label = "bend",
    )
    val jump = rememberUpdatedState(onJump)
    val press = rememberUpdatedState(onPress)
    val cog = rememberUpdatedState(onCog)
    val star = rememberUpdatedState(onStar)
    val count = letters.size

    BoxWithConstraints(modifier.fillMaxHeight()) {
        val px = with(density) {
            Geometry(
                height = constraints.maxHeight.toFloat(),
                width = constraints.maxWidth.toFloat(),
                count = count,
                slot = ScrubberDefaults.slotHeight.toPx(),
                top = topPadding.toPx(),
                bottom = bottomPadding.toPx(),
                peak = constraints.maxWidth * ScrubberDefaults.PEAK_FRACTION,
                radius = ScrubberDefaults.bendRadius.toPx(),
                bubble = ScrubberDefaults.bubbleSize.toPx(),
                gap = ScrubberDefaults.bubbleGap.toPx(),
                liftTolerance = ScrubberDefaults.liftTolerance.toPx(),
            )
        }
        val geoState = rememberUpdatedState(px)
        val geo by geoState
        val slotDp = with(density) { px.slot.toDp() }

        val driver = remember(letters, state) { ScrubDriver(letters, state, geoState, jump, press, cog, star) }
        if (external != null) {
            // Root to strip coordinates for a finger that landed on Home; set before the first attach.
            val fromRoot = remember(driver) { RootScrubSink(driver) }
            DisposableEffect(external, fromRoot) { onDispose { external.detach(fromRoot) } }
            Box(
                Modifier.matchParentSize().onGloballyPositioned {
                    fromRoot.origin = it.positionInRoot().y
                    external.attach(fromRoot)
                },
            )
        }

        Box(
            Modifier.matchParentSize().testTag("scrubber").pointerInput(driver) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var lifted = false
                    // finally: if the gesture is cancelled (sections changed mid-scrub), the bubble must not stick.
                    try {
                        driver.down(down.position.y)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            // A system cancel arrives as a synthetic release that is already consumed;
                            // a real lift reaches here untouched. Treat it as a cancel, not a lift.
                            if (change.isConsumed) break
                            change.consume()
                            if (change.changedToUpIgnoreConsumed()) {
                                lifted = true
                                driver.up(change.position.y)
                                break
                            }
                            driver.move(change.position.y)
                        }
                    } finally {
                        if (!lifted) driver.cancel()
                    }
                }
            },
        )

        letters.forEachIndexed { i, c ->
            Box(
                Modifier.align(Alignment.TopCenter).size(width = maxWidth, height = slotDp)
                    .offset {
                        val g = geo
                        val shift = g.peak * bend.value *
                            ScrubberMath.offsetAt(i, g.count, state.y.floatValue, g.trackTop, g.trackBottom, g.radius)
                        IntOffset(-shift.roundToInt(), (g.trackTop + g.slot * i).roundToInt())
                    }
                    .testTag(scrubTag(c)),
                contentAlignment = Alignment.Center,
            ) { if (glyph != null) glyph(c, Tokens.text, Modifier) else ScrubberGlyph(c) }
        }

        ScrubBubble(state, letters, bend, geoState, glyph)
    }
}

/** Its own scope, so a letter change recomposes only the bubble. */
@Composable
private fun BoxScope.ScrubBubble(
    state: ScrubState,
    letters: List<Char>,
    bend: State<Float>,
    geo: State<Geometry>,
    glyph: (@Composable (Char, Color, Modifier) -> Unit)?,
) {
    if (!state.pressed || state.index !in letters.indices) return
    val c = letters[state.index]
    Box(
        Modifier.align(Alignment.TopStart).size(ScrubberDefaults.bubbleSize)
            .offset {
                val g = geo.value
                val first = g.trackTop + g.slot / 2f
                val last = maxOf(first, g.trackBottom - g.slot / 2f)
                val y = state.y.floatValue.coerceIn(first, last)
                // Left of the bent letter, so the finger never covers it.
                val x = g.width / 2f - g.peak * bend.value - g.slot / 2f - g.gap - g.bubble
                IntOffset(x.roundToInt(), (y - g.bubble / 2f).roundToInt())
            }
            .background(Tokens.dot, CircleShape).testTag("scrub-bubble"),
        contentAlignment = Alignment.Center,
    ) {
        when {
            glyph != null && !(c.isLetter() || c == '#') ->
                glyph(c, Tokens.card, Modifier.testTag("scrub-bubble-glyph-" + scrubTag(c).removePrefix("scrub-")))
            c == ScrubberDefaults.STAR || c == ScrubberDefaults.COG ->
                ScrubberGlyph(c, Tokens.card, Modifier.testTag("scrub-bubble-glyph-" + scrubTag(c).removePrefix("scrub-")))
            else -> AppText(c.toString(), AppType.body, Modifier.testTag("scrub-bubble-letter"), color = Tokens.card)
        }
    }
}

/**
 * The scrubber's press, drag and lift rules in one place, shared by its own pointer input and a
 * finger handed over from Home ([EdgeScrub]). Positions are in the strip's coordinates.
 */
private class ScrubDriver(
    private val letters: List<Char>,
    private val state: ScrubState,
    private val geo: State<Geometry>,
    private val jump: State<(Char) -> Unit>,
    private val press: State<(Char) -> Unit>,
    private val cog: State<() -> Unit>,
    private val star: State<(() -> Unit)?>,
) : ScrubSink {
    private fun select(y: Float, fresh: Boolean) {
        state.y.floatValue = y
        val g = geo.value
        val i = ScrubberMath.indexAt(y, g.trackTop, g.trackBottom, letters.size)
        if (!fresh && i == state.index) return
        state.index = i
        val c = letters[i]
        if (c == ScrubberDefaults.COG) return
        // The star is an action on lift when the screen supplies one: like the cog, it neither
        // jumps nor feeds the double-tap lock.
        if (c == ScrubberDefaults.STAR && star.value != null) return
        jump.value(c)
        if (fresh) press.value(c)
    }

    override fun down(y: Float) {
        state.pressed = true
        select(y, fresh = true)
    }

    override fun move(y: Float) {
        if (state.pressed) select(y, fresh = false)
    }

    override fun up(y: Float) = end(y)

    override fun cancel() = end(Float.NaN)

    private fun end(liftY: Float) {
        if (!state.pressed) return
        val g = geo.value
        val starAction = star.value
        val onStarNow = starAction != null && letters.firstOrNull() == ScrubberDefaults.STAR &&
            ScrubberMath.onFirstSlot(liftY, g.trackTop, g.trackBottom, letters.size)
        val onCogNow = letters.lastOrNull() == ScrubberDefaults.COG &&
            ScrubberMath.onLastSlot(liftY, g.trackTop, g.trackBottom, letters.size, g.liftTolerance)
        state.pressed = false
        state.releases++
        state.index = -1
        when {
            onStarNow -> starAction?.invoke()
            onCogNow -> cog.value()
        }
    }
}

/** Takes root coordinates (Home's finger) and hands strip coordinates to [driver]. */
private class RootScrubSink(private val driver: ScrubDriver) : ScrubSink {
    var origin = 0f
    override fun down(y: Float) = driver.down(y - origin)
    override fun move(y: Float) = driver.move(y - origin)
    override fun up(y: Float) = driver.up(y - origin)
    override fun cancel() = driver.cancel()
}

/** Pixel geometry for one layout; the track is pinned to the bottom and shrinks its slots if the screen is short. */
internal class Geometry(
    val height: Float,
    val width: Float,
    val count: Int,
    slot: Float,
    top: Float,
    bottom: Float,
    val peak: Float,
    val radius: Float,
    val bubble: Float,
    val gap: Float,
    val liftTolerance: Float,
) {
    val trackBottom = height - bottom
    val slot = if (count == 0) slot else minOf(slot, maxOf(0f, trackBottom - top) / count)
    val trackTop = trackBottom - this.slot * count
}

@Composable
private fun ScrubberGlyph(c: Char, color: Color = Tokens.text, modifier: Modifier = Modifier) {
    when (c) {
        ScrubberDefaults.STAR -> Spacer(modifier.size(ScrubberDefaults.starSize).starGlyph(color, 1.2.dp))
        ScrubberDefaults.COG -> Spacer(modifier.size(ScrubberDefaults.cogRing).cogGlyph(color, 1.5.dp))
        else -> AppText(c.toString(), AppType.label, modifier, color = color)
    }
}
