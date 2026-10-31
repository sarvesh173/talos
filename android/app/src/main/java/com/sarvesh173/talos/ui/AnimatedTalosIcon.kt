package com.sarvesh173.talos.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * AnimatedTalosIcon
 *
 * Animated vector icon system built on the M3 Expressive motion physics model:
 *  - Spatial motion (scale/rotation) uses spring specs WITH overshoot.
 *  - Effect motion (opacity/alpha) uses effect specs with NO overshoot.
 *
 * Modes:
 *  - IDLE      : static vector, optional gentle ambient breath
 *  - RECORDING : mic-style pulse — spatial ring emission + subtle scale breath
 *  - ACTIVATE  : camera-style iris blip — one-shot spring pop with scale overshoot
 */
enum class TalosIconMode { IDLE, RECORDING, ACTIVATE }

@Composable
fun AnimatedTalosIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    mode: TalosIconMode = TalosIconMode.IDLE,
    emitters: Boolean = mode == TalosIconMode.RECORDING,
    pulseColor: Color = tint
) {
    // --- Spatial spring for the icon body (overshoot allowed) ---
    val targetScale = when (mode) {
        TalosIconMode.ACTIVATE -> 1.18f
        TalosIconMode.RECORDING -> 1.06f
        TalosIconMode.IDLE -> 1f
    }
    val bodyScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "talos_icon_body_scale"
    )

    // One-shot activation pop (camera iris blip): springs back to rest.
    var activationPop by remember { mutableStateOf(false) }
    LaunchedEffect(mode) {
        if (mode == TalosIconMode.ACTIVATE) {
            activationPop = true
            kotlinx.coroutines.delay(180)
            activationPop = false
        }
    }
    val popScale by animateFloatAsState(
        targetValue = if (activationPop) 1.22f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "talos_icon_pop"
    )

    // --- Effect motion: expanding emission rings while recording ---
    val infinite = rememberInfiniteTransition(label = "talos_icon_emit")
    val ringProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "talos_icon_ring"
    )

    // Ambient breath for idle state — smooth sinusoidal breathing
    val idleBreath by infinite.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "talos_icon_breath"
    )

    val painter = rememberVectorPainter(imageVector)

    androidx.compose.foundation.layout.Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Delicate emission rings during active recording
        if (emitters) {
            Canvas(modifier = Modifier.size(size * 1.5f)) {
                val maxR = this.size.minDimension / 2f
                repeat(2) { i ->
                    val p = (ringProgress + i * 0.5f) % 1f
                    val alpha = (1f - p) * 0.28f
                    drawCircle(
                        color = pulseColor.copy(alpha = alpha),
                        radius = maxR * (0.45f + 0.55f * p),
                        style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }

        val effectiveScale = when (mode) {
            TalosIconMode.ACTIVATE -> popScale
            TalosIconMode.RECORDING -> bodyScale
            TalosIconMode.IDLE -> bodyScale * idleBreath
        }

        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = effectiveScale
                    scaleY = effectiveScale
                }
        )
    }
}

/**
 * Convenience: mic icon that emits recording pulses.
 */
@Composable
fun TalosRecordingMic(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    isRecording: Boolean = false
) {
    AnimatedTalosIcon(
        imageVector = TalosIcons.TalosMic,
        contentDescription = if (isRecording) "Recording" else "Microphone",
        modifier = modifier,
        size = size,
        tint = tint,
        mode = if (isRecording) TalosIconMode.RECORDING else TalosIconMode.IDLE
    )
}

/**
 * Convenience: camera icon with a one-shot iris blip on activation.
 */
@Composable
fun TalosActivatingCamera(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    activated: Boolean = false
) {
    AnimatedTalosIcon(
        imageVector = TalosIcons.TalosCamera,
        contentDescription = "Camera",
        modifier = modifier,
        size = size,
        tint = tint,
        mode = if (activated) TalosIconMode.ACTIVATE else TalosIconMode.IDLE
    )
}
