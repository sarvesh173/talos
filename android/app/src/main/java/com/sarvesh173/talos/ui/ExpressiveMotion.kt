package com.sarvesh173.talos.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale

/**
 * TALOS M3 Expressive Motion Tokens.
 *
 * Values verified against AndroidX Compose Material3 ExpressiveMotionTokens /
 * StandardMotionTokens and the M3 motion physics spec (m3.material.io, as of 2026-10).
 *
 * Two families, never mixed:
 *  - SPATIAL: position, size, scale, shape morphs. Expressive scheme overshoots (bounces).
 *  - EFFECTS: color, opacity, state layers. Critically damped (zeta = 1.0) - never overshoots.
 */
object M3EMotion {
    // ---- Expressive spatial springs (overshoot, lively) ----
    const val FAST_SPATIAL_DAMPING = 0.6f
    const val FAST_SPATIAL_STIFFNESS = 800f
    const val DEFAULT_SPATIAL_DAMPING = 0.8f
    const val DEFAULT_SPATIAL_STIFFNESS = 380f
    const val SLOW_SPATIAL_DAMPING = 0.8f
    const val SLOW_SPATIAL_STIFFNESS = 200f

    // ---- Effects springs (critically damped, no bounce) ----
    const val FAST_EFFECTS_STIFFNESS = 3800f
    const val DEFAULT_EFFECTS_STIFFNESS = 1600f
    const val SLOW_EFFECTS_STIFFNESS = 800f

    /** Fast spatial: small controls - buttons, chips, switches, send FAB. */
    fun <T> fastSpatial() = spring<T>(
        dampingRatio = FAST_SPATIAL_DAMPING,
        stiffness = FAST_SPATIAL_STIFFNESS
    )

    /** Default spatial: partial-screen surfaces - sheets, cards, drawers. */
    fun <T> defaultSpatial() = spring<T>(
        dampingRatio = DEFAULT_SPATIAL_DAMPING,
        stiffness = DEFAULT_SPATIAL_STIFFNESS
    )

    /** Slow spatial: full-screen transitions, hero reveals. */
    fun <T> slowSpatial() = spring<T>(
        dampingRatio = SLOW_SPATIAL_DAMPING,
        stiffness = SLOW_SPATIAL_STIFFNESS
    )

    /** Fast effects: icon tints, selection highlights, state-layer fade. */
    fun <T> fastEffects() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = FAST_EFFECTS_STIFFNESS
    )

    /** Default effects: container color transitions. */
    fun <T> defaultEffects() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = DEFAULT_EFFECTS_STIFFNESS
    )

    /** Slow effects: atmospheric fades, backdrop changes. */
    fun <T> slowEffects() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = SLOW_EFFECTS_STIFFNESS
    )

    // ---- M3 emphasized easing curves (legacy-curve fallback for non-interruptible paths) ----
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.70f, 0.10f, 1.00f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.30f, 0.00f, 0.80f, 0.15f)
    val StandardEasing: Easing = CubicBezierEasing(0.20f, 0.00f, 0.00f, 1.00f)
    val LinearOut: Easing = LinearEasing
}

/**
 * M3E tactile press physics: spring-driven scale(0.96f) with fast spatial spring.
 * Standard tactile touch target per M3 expressive guidance.
 */
fun Modifier.m3ePressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = M3EMotion.fastSpatial(),
        label = "m3e_press_scale"
    )
    scale(scale)
}

/** Convenience: remember an interaction source bound to press-scale. */
@androidx.compose.runtime.Composable
fun rememberM3ePressInteraction(): MutableInteractionSource = remember { MutableInteractionSource() }

/**
 * Apple iOS 17/18 spring-based tactile press physics.
 * Scales down to pressedScale (default 0.97f) on press with dampingRatio = 0.85f, stiffness = 400f.
 */
fun Modifier.applePressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = 400f
        ),
        label = "apple_press_scale"
    )
    scale(scale)
}

fun Modifier.applePressable(
    pressedScale: Float = 0.97f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = 400f
        ),
        label = "apple_pressable"
    )
    this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}
