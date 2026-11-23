package me.jhot.swiftcurrent.ui.design

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A down arrow resting on a line: "this app is not on the phone yet, tap to download it". */
fun DrawScope.download(color: Color, width: Float) {
    val x = size.width * 0.5f
    drawLine(color, Offset(x, size.height * 0.1f), Offset(x, size.height * 0.68f), width, StrokeCap.Round)
    drawLine(color, Offset(size.width * 0.22f, size.height * 0.46f), Offset(x, size.height * 0.7f), width, StrokeCap.Round)
    drawLine(color, Offset(size.width * 0.78f, size.height * 0.46f), Offset(x, size.height * 0.7f), width, StrokeCap.Round)
    drawLine(color, Offset(size.width * 0.18f, size.height * 0.92f), Offset(size.width * 0.82f, size.height * 0.92f), width, StrokeCap.Round)
}

/** A pencil: a slanted shaft with a short tip line, drawn like the other line icons. */
fun DrawScope.pencil(color: Color, width: Float) {
    drawLine(color, Offset(size.width * 0.22f, size.height * 0.78f), Offset(size.width * 0.78f, size.height * 0.22f), width * 2.2f, StrokeCap.Round)
    drawLine(color, Offset(size.width * 0.12f, size.height * 0.9f), Offset(size.width * 0.5f, size.height * 0.9f), width, StrokeCap.Round)
}

// The line glyphs below are modifiers rather than DrawScope functions: each builds its Path in
// drawWithCache, once per size, instead of allocating one on every draw. [width] is the stroke.

private fun Modifier.strokedPath(
    color: Color,
    width: Dp,
    cap: StrokeCap,
    join: StrokeJoin,
    build: Path.(Size, Float) -> Unit,
): Modifier = drawWithCache {
    val stroke = width.toPx()
    val path = Path().apply { build(size, stroke) }
    val style = Stroke(stroke, cap = cap, join = join)
    onDrawBehind { drawPath(path, color, style = style) }
}

/** Points right when closed, down when open. */
fun Modifier.chevronGlyph(color: Color, width: Dp, open: Boolean): Modifier = strokedPath(color, width, StrokeCap.Round, StrokeJoin.Round) { s, _ ->
    if (open) {
        moveTo(s.width * 0.15f, s.height * 0.35f); lineTo(s.width * 0.5f, s.height * 0.7f); lineTo(s.width * 0.85f, s.height * 0.35f)
    } else {
        moveTo(s.width * 0.35f, s.height * 0.15f); lineTo(s.width * 0.7f, s.height * 0.5f); lineTo(s.width * 0.35f, s.height * 0.85f)
    }
}

fun Modifier.checkGlyph(color: Color, width: Dp): Modifier = strokedPath(color, width, StrokeCap.Round, StrokeJoin.Round) { s, _ ->
    moveTo(s.width * 0.12f, s.height * 0.55f)
    lineTo(s.width * 0.4f, s.height * 0.82f)
    lineTo(s.width * 0.88f, s.height * 0.2f)
}

/** Four points: eight vertices alternating tip and notch. */
fun Modifier.sparkleGlyph(color: Color, width: Dp): Modifier = strokedPath(color, width, StrokeCap.Butt, StrokeJoin.Round) { s, stroke ->
    polygon(s, outer = s.minDimension / 2f - stroke, innerRatio = 0.3f, points = 4)
}

/** Five points, outlined. */
fun Modifier.starGlyph(color: Color, width: Dp): Modifier = strokedPath(color, width, StrokeCap.Butt, StrokeJoin.Miter) { s, stroke ->
    polygon(s, outer = s.minDimension / 2f - stroke, innerRatio = 0.45f, points = 5)
}

fun Modifier.diamondGlyph(color: Color, width: Dp): Modifier = strokedPath(color, width, StrokeCap.Butt, StrokeJoin.Round) { s, stroke ->
    moveTo(s.center.x, stroke); lineTo(s.width - stroke, s.center.y); lineTo(s.center.x, s.height - stroke); lineTo(stroke, s.center.y); close()
}

/** Two linked pills. */
fun Modifier.linkGlyph(color: Color, width: Dp): Modifier = drawBehind {
    val stroke = width.toPx()
    val h = size.height * 0.5f
    val w = size.width * 0.62f
    val corner = CornerRadius(h / 2f)
    drawRoundRect(color, Offset(stroke, (size.height - h) / 2f), Size(w, h), corner, Stroke(stroke))
    drawRoundRect(color, Offset(size.width - w - stroke, (size.height - h) / 2f), Size(w, h), corner, Stroke(stroke))
}

fun Modifier.plusGlyph(color: Color, width: Dp): Modifier = drawBehind {
    val stroke = width.toPx()
    drawLine(color, Offset(stroke, center.y), Offset(size.width - stroke, center.y), stroke, StrokeCap.Round)
    drawLine(color, Offset(center.x, stroke), Offset(center.x, size.height - stroke), stroke, StrokeCap.Round)
}

/** A small ring: the scrubber's settings mark. */
fun Modifier.cogGlyph(color: Color, width: Dp): Modifier = drawBehind {
    val stroke = width.toPx()
    drawCircle(color, radius = size.minDimension / 2f - stroke / 2f, style = Stroke(stroke))
}

/** A gear: a toothed outline around a hub, so it reads as settings where [cogGlyph]'s bare ring would not. */
fun Modifier.gearGlyph(color: Color, width: Dp, teeth: Int = 8): Modifier = drawWithCache {
    val stroke = width.toPx()
    val c = size.center
    val outer = size.minDimension / 2f - stroke
    val inner = outer * 0.76f
    // Each tooth is a flat-topped trapezoid: [half] radians wide at the root, narrower at the tip.
    val half = (PI / teeth * 0.5).toFloat()
    val path = Path().apply {
        for (i in 0 until teeth) {
            val a = (2 * PI * i / teeth).toFloat()
            val pts = listOf(a - half to inner, a - half * 0.6f to outer, a + half * 0.6f to outer, a + half to inner)
            pts.forEachIndexed { k, (ang, r) ->
                val x = c.x + r * cos(ang)
                val y = c.y + r * sin(ang)
                if (i == 0 && k == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        close()
    }
    onDrawBehind {
        drawPath(path, color, style = Stroke(stroke, join = StrokeJoin.Round))
        drawCircle(color, radius = inner * 0.42f, center = c, style = Stroke(stroke))
    }
}

/** A magnifier: a ring with a handle to the bottom right. */
fun Modifier.searchGlyph(color: Color, width: Dp): Modifier = drawBehind {
    val stroke = width.toPx()
    val r = size.minDimension * 0.34f
    val c = Offset(r + stroke, r + stroke)
    drawCircle(color, radius = r, center = c, style = Stroke(stroke))
    val d = r * 0.7071f
    drawLine(color, c + Offset(d, d), Offset(size.width - stroke, size.height - stroke), stroke, StrokeCap.Round)
}

// [points] tips alternating with notches at [innerRatio] of the tip radius, the first tip straight up.
private fun Path.polygon(size: Size, outer: Float, innerRatio: Float, points: Int) {
    val center = size.center
    for (k in 0 until points * 2) {
        val radius = if (k % 2 == 0) outer else outer * innerRatio
        val a = -PI / 2 + k * PI / points
        val x = center.x + (radius * cos(a)).toFloat()
        val y = center.y + (radius * sin(a)).toFloat()
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
