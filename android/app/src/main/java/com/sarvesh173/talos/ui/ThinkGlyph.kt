package com.sarvesh173.talos.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The thinking glyph: two crossed orbits with a centre point.
 *
 * Ported from dsh-Go's `think_glyph.dart`, which takes it from DeepSeek's own
 * web UI. It reads as "thinking" in a way a generic spinner does not, and it is
 * the mark people already associate with a reasoning model.
 *
 * The orbits spin slowly while the model is still thinking — slow enough not to
 * compete with the label, just enough to say "alive". When the turn settles the
 * rotation stops rather than fading, so a finished thought looks settled.
 *
 * @param active spin while true.
 */
@Composable
fun ThinkGlyph(
    modifier: Modifier = Modifier,
    size: Dp = 15.dp,
    color: Color,
    active: Boolean = false,
    strokeWidth: Float = 1.25f
) {
    // 7.2s per revolution: the DeepSeek timing. Faster reads as a loading
    // spinner, which is exactly what this is not.
    val transition = rememberInfiniteTransition(label = "think-glyph")
    val turns by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (active) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(7200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "think-turns"
    )

    Canvas(modifier = modifier.size(size)) {
        rotate(turns * 360f) {
            drawThinkGlyph(color, strokeWidth)
        }
    }
}

private val orbitA = "M10.2854 5.71481C12.9673 8.39663 14.1182 11.5938 12.8562 12.8559C11.5942 14.1179 8.39706 12.9669 5.71518 10.2851C3.03333 7.60323 1.88236 4.40608 3.14441 3.14403C4.40644 1.882 7.6036 3.03297 10.2854 5.71481Z"
private val orbitB = "M10.2854 10.2851C7.6036 12.9669 4.40644 14.1179 3.14441 12.8559C1.88236 11.5938 3.03333 8.39663 5.71518 5.71481C8.39706 3.03297 11.5942 1.882 12.8562 3.14403C14.1182 4.40608 12.9673 7.60323 10.2854 10.2851Z"

/**
 * Both orbits in one path, then a centre dot.
 *
 * The paths are parsed once per draw rather than cached: the glyph is 15dp and
 * there is one per visible turn, so the parse is cheaper than the bookkeeping
 * to avoid it.
 */
private fun DrawScope.drawThinkGlyph(color: Color, strokeWidth: Float) {
    // The paths are authored on a 16x16 grid. Parse them scaled rather than
    // transforming the canvas: DrawScope has no scale(), and building the path
    // at final size keeps the stroke width in real pixels.
    val k = size.minDimension / 16f
    val path = Path().apply {
        addPath(parseOrbit(orbitA, k), Offset.Zero)
        addPath(parseOrbit(orbitB, k), Offset.Zero)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth * k)
    )
    // Centre point.
    drawCircle(
        color = color,
        radius = 1.15f * k,
        center = Offset(8f * k, 8f * k)
    )
}

/** Minimal M/C/Z parser — these two paths use nothing else. */
private fun parseOrbit(d: String, k: Float = 1f): Path {
    val path = Path()
    val tokens = Regex("[MCZ]|-?\\d*\\.?\\d+").findAll(d).map { it.value }.iterator()
    fun next(): Float {
        tokens.next()
        return tokens.next().toFloat() * k
    }
    while (tokens.hasNext()) {
        when (val cmd = tokens.next()) {
            "M" -> path.moveTo(next(), next())
            "C" -> path.cubicTo(next(), next(), next(), next(), next(), next())
            "Z" -> path.close()
            else -> Unit
        }
    }
    return path
}