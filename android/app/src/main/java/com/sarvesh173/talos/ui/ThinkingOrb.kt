package com.sarvesh173.talos.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * Apple Intelligence & Material 3 Expressive Luminous AI Orb.
 * Renders iridescent multi-layer chromatic fluid energy waves,
 * soft radial bloom, and organic particle photons on Jetpack Compose Canvas.
 */
@Composable
fun ThinkingOrb(
    state: OrbState = OrbState.THINKING,
    size: Dp = 64.dp,
    dotColor: Color = Color.White,
    secondaryColor: Color = Color(0xFF38BDF8),
    speedMultiplier: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LuminousOrbEngine")

    // Continuous rotational and wave phase clocks
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (3200 / speedMultiplier).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OrbWavePhase"
    )

    // Breathing pulse for radial chromatic aura
    val breath by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "OrbBreath"
    )

    // Chromatic palette inspired by Apple Intelligence & M3 Expressive
    val chromaticCyan = Color(0xFF38BDF8)
    val chromaticViolet = Color(0xFF818CF8)
    val chromaticRose = Color(0xFFF43F5E)
    val chromaticEmerald = Color(0xFF10B981)

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = this.size.minDimension / 2f * 0.82f

        // 1. AMBIENT SOFT CHROMATIC BLOOM (LAYER 0)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    secondaryColor.copy(alpha = 0.35f * breath),
                    chromaticViolet.copy(alpha = 0.20f * breath),
                    Color.Transparent
                ),
                center = center,
                radius = radius * 1.5f * breath
            ),
            radius = radius * 1.5f * breath,
            center = center
        )

        when (state) {
            OrbState.IDLE -> {
                // Calm glowing sphere with gentle shimmering outer rim
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.12f),
                    radius = radius * 0.75f * breath,
                    center = center
                )
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(chromaticCyan, chromaticViolet, chromaticCyan),
                        center = center
                    ),
                    radius = radius * 0.75f * breath,
                    center = center,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
                drawCircle(
                    color = dotColor.copy(alpha = 0.90f),
                    radius = radius * 0.25f,
                    center = center
                )
            }

            OrbState.THINKING, OrbState.WEAVING -> {
                // Dual undulating sinusoidal energy ribbons (Apple Intelligence Siri Wave)
                val points = 60
                val path1 = Path()
                val path2 = Path()

                for (i in 0..points) {
                    val angle = (i.toFloat() / points) * 2f * PI.toFloat()
                    val wave1 = sin(angle * 3f + phase) * (radius * 0.16f)
                    val r1 = (radius * 0.72f * breath) + wave1
                    val x1 = center.x + r1 * cos(angle)
                    val y1 = center.y + r1 * sin(angle)
                    if (i == 0) path1.moveTo(x1, y1) else path1.lineTo(x1, y1)

                    val wave2 = cos(angle * 3f - phase * 1.2f) * (radius * 0.14f)
                    val r2 = (radius * 0.60f * breath) + wave2
                    val x2 = center.x + r2 * cos(angle)
                    val y2 = center.y + r2 * sin(angle)
                    if (i == 0) path2.moveTo(x2, y2) else path2.lineTo(x2, y2)
                }
                path1.close()
                path2.close()

                // Primary chromatic fluid sweep ribbon
                drawPath(
                    path = path1,
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            chromaticCyan,
                            chromaticViolet,
                            chromaticRose,
                            chromaticEmerald,
                            chromaticCyan
                        ),
                        center = center
                    ),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // Secondary inner energy ribbon
                drawPath(
                    path = path2,
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            chromaticRose.copy(alpha = 0.8f),
                            chromaticCyan.copy(alpha = 0.8f),
                            chromaticViolet.copy(alpha = 0.8f),
                            chromaticRose.copy(alpha = 0.8f)
                        ),
                        center = center
                    ),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                // Luminous central nucleus core
                drawCircle(
                    color = dotColor.copy(alpha = 0.95f),
                    radius = radius * 0.22f,
                    center = center
                )
            }

            OrbState.SEARCHING, OrbState.CONNECTING -> {
                // Expanding orbital radar rings with rotating satellite nodes
                val sweepAngle = Math.toDegrees(phase.toDouble()).toFloat()

                // Rotating sweep beam
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            secondaryColor.copy(alpha = 0.75f),
                            Color.Transparent
                        ),
                        center = center
                    ),
                    startAngle = sweepAngle - 60f,
                    sweepAngle = 120f,
                    useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                )

                // Outer guide ring
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.35f),
                    radius = radius * 0.85f,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Orbiting satellite photon nodes
                val satCount = 4
                for (s in 0 until satCount) {
                    val satAngle = phase + (s * PI.toFloat() / 2f)
                    val sx = center.x + (radius * 0.85f) * cos(satAngle)
                    val sy = center.y + (radius * 0.85f) * sin(satAngle)
                    drawCircle(
                        color = Color.White,
                        radius = 3.5.dp.toPx(),
                        center = Offset(sx, sy)
                    )
                }

                // Center glowing beacon
                drawCircle(
                    color = Color.White,
                    radius = radius * 0.28f,
                    center = center
                )
            }

            OrbState.SOLVING, OrbState.COMPOSING -> {
                // Multi-harmonic crystalline alignment
                val facets = 8
                val path = Path()
                for (i in 0..facets) {
                    val angle = (i.toFloat() / facets) * 2f * PI.toFloat() + phase * 0.5f
                    val pulse = 1f + 0.15f * sin(angle * 2f + phase * 2f)
                    val r = radius * 0.75f * pulse
                    val x = center.x + r * cos(angle)
                    val y = center.y + r * sin(angle)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()

                drawPath(
                    path = path,
                    brush = Brush.sweepGradient(
                        colors = listOf(chromaticViolet, chromaticCyan, chromaticRose, chromaticViolet),
                        center = center
                    ),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                drawCircle(
                    color = Color.White,
                    radius = radius * 0.30f * breath,
                    center = center
                )
            }

            OrbState.LISTENING -> {
                // Multi-ripple soundwave pulse
                for (rIdx in 1..3) {
                    val rProgress = ((phase / (2f * PI.toFloat()) + (rIdx * 0.33f)) % 1f)
                    val r = radius * (0.3f + 0.7f * rProgress)
                    val alpha = (1f - rProgress).coerceIn(0f, 0.85f)
                    drawCircle(
                        color = secondaryColor.copy(alpha = alpha),
                        radius = r,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
                drawCircle(
                    color = Color.White,
                    radius = radius * 0.25f,
                    center = center
                )
            }
        }
    }
}
