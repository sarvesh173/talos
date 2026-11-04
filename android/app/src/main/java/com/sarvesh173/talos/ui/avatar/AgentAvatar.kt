package com.sarvesh173.talos.ui.avatar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sarvesh173.talos.ui.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Renders an agent's avatar.
 *
 * The whole face is drawn from primitives — one rounded head path, two eyes,
 * two brows, and an orbiting dot field that grows as the agent works. Nothing
 * is an image asset, which means the same code produces a crisp mark at 16dp
 * in a chat row and at 96dp on a profile, and the hue can be changed per agent
 * without re-exporting anything.
 *
 * Timing is driven by [withFrameMillis] rather than a fixed ticker: the engine
 * is spring-based, so it needs real deltas to look right, and a fixed 16ms tick
 * would run slow on a 120Hz panel and fast on a laggy frame.
 */
@Composable
fun AgentAvatar(
    engine: AgentAvatarEngine,
    size: Dp = 40.dp,
    isDark: Boolean = false,
    modifier: Modifier = Modifier,
    announce: Boolean = true
) {
    val reducedMotion = rememberReducedMotion()
    var frame by remember { mutableStateOf(0L) }

    LaunchedEffect(engine, reducedMotion) {
        if (reducedMotion) {
            // One frame, then stop. A reduced-motion user gets the resting
            // face; the state is still communicated by the colour and by the
            // text next to it, so nothing is lost.
            frame = 1L
            return@LaunchedEffect
        }
        var last = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            val dt = ((now - last) / 1000f).coerceIn(0f, 0.05f)
            last = now
            engine.update(dt)
            frame = now
        }
    }

    val ink = avatarInk(engine.identity.hue, engine.identity.saturation, isDark)
    val accent = avatarAccent(engine.identity.hue, engine.identity.saturation, isDark)

    // Reading `frame` is what re-triggers the draw each tick.
    @Suppress("UNUSED_EXPRESSION")
    frame

    Box(
        modifier = modifier
            .size(size)
            .semantics {
                if (announce) {
                    contentDescription = "${engine.identity.name}, ${engine.state.name.lowercase()}"
                }
            }
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f

            val (sx, sy) = squashToScale(engine.squash.x)
            val bob = engine.bobY.x * h
            val tiltRad = engine.tilt.x

            // Head: a superellipse, not a circle. A true circle reads as a
            // generic dot; the slightly squared shoulder is what makes it read
            // as a character.
            val headW = w * 0.62f * sx
            val headH = h * 0.60f * sy

            val headPath = Path().apply {
                val rx = headW / 2f
                val ry = headH / 2f
                val n = 2.6f // superellipse exponent; 2 would be an ellipse
                val steps = 48
                for (i in 0..steps) {
                    val a = (i.toFloat() / steps) * Math.PI.toFloat() * 2f
                    val ca = cos(a)
                    val sa = sin(a)
                    val px = rx * signPow(ca, 2f / n)
                    val py = ry * signPow(sa, 2f / n)
                    val rotX = px * cos(tiltRad) - py * sin(tiltRad)
                    val rotY = px * sin(tiltRad) + py * cos(tiltRad)
                    if (i == 0) moveTo(cx + rotX, cy + bob + rotY)
                    else lineTo(cx + rotX, cy + bob + rotY)
                }
                close()
            }

            drawPath(headPath, color = ink.copy(alpha = 0.20f))
            drawPath(headPath, color = ink.copy(alpha = 0.55f), style = Stroke(width = w * 0.022f))

            // Orbit dots: the agent's "activity field". Hidden at rest so an
            // idle agent is calm, visible as soon as it works.
            if (engine.morph.x > 0.02f) {
                val count = (6 + (engine.morph.x * 10).toInt()).coerceAtMost(16)
                val radius = w * 0.44f
                for (i in 0 until count) {
                    val (ox, oy, angle) = engine.orbitDot(i, count, radius)
                    // Far dots on the back of the orbit are smaller and dimmer,
                    // which is the whole depth cue.
                    val depth = (sin(angle) + 1f) / 2f
                    val dotR = w * (0.012f + 0.010f * depth) * (0.7f + 0.5f * engine.morph.x)
                    drawCircle(
                        color = accent.copy(alpha = (0.25f + 0.55f * depth) * engine.morph.x),
                        radius = dotR,
                        center = Offset(cx + ox, cy + bob + oy)
                    )
                }
            }

            // Eyes.
            val eyeSep = headW * 0.26f
            val eyeW = headW * 0.20f * engine.eyeScaleX.x
            val eyeH = headH * 0.30f * engine.eyeOpen.x.coerceIn(0.02f, 1.15f)
            val lid = lidCoverage(engine.eyeOpen.x)
            val (pupX, pupY) = pupilOffset(engine.gazeX.x, engine.gazeY.x, headW * 0.045f)

            for (side in listOf(-1f, 1f)) {
                val ex = cx + side * eyeSep * cos(tiltRad) - pupY * sin(tiltRad)
                val ey = cy + bob + side * eyeSep * sin(tiltRad) + pupY * cos(tiltRad)

                if (eyeH > 0.6f) {
                    drawOval(
                        color = accent.copy(alpha = 0.92f),
                        topLeft = Offset(ex - eyeW / 2f + pupX, ey - eyeH / 2f),
                        size = Size(eyeW, eyeH)
                    )
                }
                // A closed eye is a line, not a squeezed oval: past a point the
                // oval turns into a dot and the face looks surprised instead of
                // asleep.
                if (lid > 0.72f) {
                    drawLine(
                        color = ink.copy(alpha = 0.75f),
                        start = Offset(ex - eyeW * 0.60f, ey),
                        end = Offset(ex + eyeW * 0.60f, ey),
                        strokeWidth = w * 0.016f
                    )
                }
            }

            // Brows. Only drawn when they carry a mood; two extra strokes on a
            // neutral face just look like noise at small sizes.
            val brow = engine.browAngle.x
            if (abs(brow) > 0.03f && size > 24.dp) {
                val browY = cy + bob - headH * 0.30f
                for (side in listOf(-1f, 1f)) {
                    val bx = cx + side * eyeSep
                    val inner = brow * side * headW * 0.10f
                    drawLine(
                        color = ink.copy(alpha = 0.55f),
                        start = Offset(bx - side * eyeW * 0.6f, browY + inner),
                        end = Offset(bx + side * eyeW * 0.6f, browY - inner),
                        strokeWidth = w * 0.018f
                    )
                }
            }
        }
    }
}

/** The head and stroke colour for a hue, tuned per theme. */
private fun avatarInk(hue: Float, saturation: Float, isDark: Boolean): Color {
    val light = hsvToColor(hue, saturation * 0.35f, if (isDark) 0.94f else 0.28f)
    return light
}

/** The eye/accent colour: the agent's actual identity colour. */
private fun avatarAccent(hue: Float, saturation: Float, isDark: Boolean): Color {
    return hsvToColor(hue, saturation, if (isDark) 0.92f else 0.72f)
}

/** HSV to Color without allocating a Bitmap or pulling in awt. */
private fun hsvToColor(h: Float, s: Float, v: Float): Color {
    val hh = ((h % 360f) + 360f) % 360f / 60f
    val c = v * s
    val x = c * (1f - abs(hh % 2f - 1f))
    val m = v - c
    val (r, g, b) = when (hh.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}

/** x^n that keeps the sign, for the superellipse. */
private fun signPow(x: Float, n: Float): Float =
    if (x < 0f) -pow(-x, n) else pow(x, n)
