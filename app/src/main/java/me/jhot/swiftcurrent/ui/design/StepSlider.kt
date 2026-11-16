package me.jhot.swiftcurrent.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A slider from nothing to [max], snapping to [step]. A [value] past [max] sits at the end. Exposed to
 * accessibility as a range with a set-progress action. [onValue] hears only a change, so a drag that
 * stays on one step does not repeat the same write (or the same buzz).
 */
@Composable
fun StepSlider(value: Int, max: Int, step: Int, label: String, tag: String, onValue: (Int) -> Unit) {
    val maxNow by rememberUpdatedState(max)
    val valueNow by rememberUpdatedState(value)
    val onNow by rememberUpdatedState(onValue)
    fun snap(raw: Float): Int = ((raw / step).roundToInt() * step).coerceIn(0, maxNow)
    fun move(raw: Float) { val v = snap(raw); if (v != valueNow) onNow(v) }
    val thumb = 24.dp
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(40.dp).testTag(tag)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(0, max).toFloat(), 0f..max.toFloat().coerceAtLeast(1f))
                contentDescription = label
                setProgress { target -> move(target); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val span = (size.width - thumb.toPx()).coerceAtLeast(1f)
                    fun at(x: Float) = move(((x - thumb.toPx() / 2f) / span).coerceIn(0f, 1f) * maxNow)
                    at(down.position.x)
                    down.consume()
                    drag(down.id) { change -> at(change.position.x); change.consume() }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val span = maxWidth - thumb
        val fraction = if (max <= 0) 0f else value.coerceIn(0, max).toFloat() / max
        Box(Modifier.fillMaxWidth().height(4.dp).background(Tokens.card, CircleShape))
        Box(Modifier.width(thumb / 2 + span * fraction).height(4.dp).background(Tokens.dimText, CircleShape))
        Box(Modifier.offset(x = span * fraction).size(thumb).background(Tokens.text, CircleShape))
    }
}
