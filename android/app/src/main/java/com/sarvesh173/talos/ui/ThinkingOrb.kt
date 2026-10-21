package com.sarvesh173.talos.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*

enum class OrbState {
    IDLE,
    THINKING,
    SEARCHING,
    SOLVING,
    LISTENING,
    CONNECTING,
    WEAVING,
    COMPOSING
}

/**
 * Monochrome Thinking Orb indicator inspired by Jakub Antalik & Yogesh Raut.
 * Renders state-driven procedural dotted particles on Jetpack Compose Canvas.
 */
@Composable
fun ThinkingOrb(
    state: OrbState = OrbState.THINKING,
    size: Dp = 64.dp,
    dotColor: Color = Color.White,
    secondaryColor: Color = Color(0xFF64748B),
    speedMultiplier: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ThinkingOrbLoop")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (2800 / speedMultiplier).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OrbPhase"
    )

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = this.size.minDimension / 2f * 0.85f

        when (state) {
            OrbState.IDLE -> {
                // Calm breathing ring of 18 dots
                val dotCount = 18
                val breath = 1f + 0.08f * sin(time)
                for (i in 0 until dotCount) {
                    val angle = (i.toFloat() / dotCount) * 2f * PI.toFloat()
                    val r = radius * 0.7f * breath
                    val x = center.x + r * cos(angle)
                    val y = center.y + r * sin(angle)
                    val alpha = 0.4f + 0.35f * ((sin(angle + time) + 1f) / 2f)
                    drawCircle(
                        color = dotColor.copy(alpha = alpha),
                        radius = 2.2f,
                        center = Offset(x, y)
                    )
                }
                // Center calm core
                drawCircle(
                    color = dotColor.copy(alpha = 0.85f),
                    radius = 3.5f,
                    center = center
                )
            }

            OrbState.THINKING -> {
                // Multi-ring harmonic particle orbits with phase shifts
                val rings = 3
                val dotsPerRing = 12
                for (ring in 1..rings) {
                    val r = radius * (ring / 3f)
                    val dir = if (ring % 2 == 0) 1f else -1f
                    for (i in 0 until dotsPerRing) {
                        val baseAngle = (i.toFloat() / dotsPerRing) * 2f * PI.toFloat()
                        val angle = baseAngle + (time * dir * (0.8f + ring * 0.2f))
                        val pulse = 1f + 0.12f * sin(angle * 3f + time * 2f)
                        val x = center.x + (r * pulse) * cos(angle)
                        val y = center.y + (r * pulse) * sin(angle)
                        val alpha = if (ring == rings) 0.9f else 0.5f + (ring * 0.15f)
                        drawCircle(
                            color = if (ring % 2 == 0) dotColor.copy(alpha = alpha) else secondaryColor.copy(alpha = alpha),
                            radius = if (ring == 3) 2.5f else 1.8f,
                            center = Offset(x, y)
                        )
                    }
                }
            }

            OrbState.SEARCHING -> {
                // Sweeping radar scan wave with trailing decay
                val scanAngle = time
                val rings = 4
                for (ring in 1..rings) {
                    val r = radius * (ring / 4f)
                    val count = ring * 8
                    for (i in 0 until count) {
                        val angle = (i.toFloat() / count) * 2f * PI.toFloat()
                        val diff = (angle - scanAngle).let { d ->
                            val norm = (d % (2f * PI.toFloat()) + 2f * PI.toFloat()) % (2f * PI.toFloat())
                            norm
                        }
                        // Bright if near sweep line, fading behind
                        val intensity = (1f - (diff / (2f * PI.toFloat()))).coerceIn(0.15f, 1f)
                        val x = center.x + r * cos(angle)
                        val y = center.y + r * sin(angle)
                        drawCircle(
                            color = dotColor.copy(alpha = intensity.pow(2f)),
                            radius = 1.6f + intensity * 1.5f,
                            center = Offset(x, y)
                        )
                    }
                }
            }

            OrbState.SOLVING -> {
                // Crystalline geometric matrix alignment
                val count = 24
                for (i in 0 until count) {
                    val baseAngle = (i.toFloat() / count) * 2f * PI.toFloat()
                    val morph = sin(time * 2f + baseAngle * 4f)
                    val r = radius * (0.4f + 0.45f * abs(morph))
                    val x = center.x + r * cos(baseAngle + (time * 0.3f))
                    val y = center.y + r * sin(baseAngle + (time * 0.3f))
                    drawCircle(
                        color = if (i % 2 == 0) dotColor else secondaryColor,
                        radius = 2.4f,
                        center = Offset(x, y)
                    )
                }
            }

            OrbState.LISTENING -> {
                // Concentric soundwave ripples
                val rippleCount = 4
                for (i in 0 until rippleCount) {
                    val phase = (time + i * (PI.toFloat() / 2f)) % (2f * PI.toFloat())
                    val progress = phase / (2f * PI.toFloat())
                    val r = radius * progress
                    val alpha = (1f - progress).coerceIn(0f, 0.9f)
                    val dotCount = 16
                    for (j in 0 until dotCount) {
                        val angle = (j.toFloat() / dotCount) * 2f * PI.toFloat()
                        val x = center.x + r * cos(angle)
                        val y = center.y + r * sin(angle)
                        drawCircle(
                            color = dotColor.copy(alpha = alpha),
                            radius = 2f,
                            center = Offset(x, y)
                        )
                    }
                }
            }

            OrbState.CONNECTING -> {
                // Dual intertwined helix pattern
                val dots = 20
                for (i in 0 until dots) {
                    val t = (i.toFloat() / dots) * 2f * PI.toFloat()
                    val r1 = radius * 0.75f * sin(t + time)
                    val r2 = radius * 0.75f * cos(t + time)
                    val x1 = center.x + r1 * cos(t)
                    val y1 = center.y + r1 * sin(t)
                    val x2 = center.x + r2 * cos(t + PI.toFloat() / 2f)
                    val y2 = center.y + r2 * sin(t + PI.toFloat() / 2f)

                    drawCircle(color = dotColor.copy(alpha = 0.85f), radius = 2.2f, center = Offset(x1, y1))
                    drawCircle(color = secondaryColor.copy(alpha = 0.7f), radius = 2f, center = Offset(x2, y2))
                }
            }

            OrbState.WEAVING -> {
                // Sinusoidal wave rings that weave in and out
                val dots = 32
                for (i in 0 until dots) {
                    val angle = (i.toFloat() / dots) * 2f * PI.toFloat()
                    val wave = sin(angle * 5f + time * 2f) * (radius * 0.25f)
                    val r = (radius * 0.65f) + wave
                    val x = center.x + r * cos(angle)
                    val y = center.y + r * sin(angle)
                    drawCircle(
                        color = dotColor.copy(alpha = 0.5f + 0.45f * abs(wave / (radius * 0.25f))),
                        radius = 2.2f,
                        center = Offset(x, y)
                    )
                }
            }

            OrbState.COMPOSING -> {
                // Inward-flowing particle matrix
                val streams = 8
                val dotsPerStream = 4
                for (s in 0 until streams) {
                    val angle = (s.toFloat() / streams) * 2f * PI.toFloat()
                    for (d in 0 until dotsPerStream) {
                        val progress = ((time / (2f * PI.toFloat()) + d.toFloat() / dotsPerStream) % 1f)
                        val r = radius * (1f - progress)
                        val x = center.x + r * cos(angle + progress * 0.5f)
                        val y = center.y + r * sin(angle + progress * 0.5f)
                        val alpha = progress.coerceIn(0.2f, 1f)
                        drawCircle(
                            color = dotColor.copy(alpha = alpha),
                            radius = 1.8f + (1f - progress) * 1.5f,
                            center = Offset(x, y)
                        )
                    }
                }
            }
        }
    }
}
