package com.sarvesh173.talos.ui.orb

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sarvesh173.talos.ui.rememberReducedMotion

/**
 * Thinking Orb — native Compose renderer for the ported geometry engine.
 *
 * Upstream is React + canvas. This draws the same finalised dot list straight
 * into a Compose `Canvas`, so there is no bitmap, no extra dependency, and no
 * Skia picture round-trip: one Canvas draw call per dot, already z-sorted.
 *
 * Accessibility contract (same as upstream):
 *  - the orb is indeterminate feedback, never a progress value;
 *  - pass a task-specific `label`, or set `announce = false` when an adjacent
 *    live status line already says the same thing;
 *  - under reduced motion the animation freezes on a representative frame
 *    instead of disappearing — the state is still visible, just static.
 */
@Composable
fun ThinkingOrb(
    state: OrbState,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    ink: Color? = null,
    label: String? = null,
    announce: Boolean = true,
    paused: Boolean = false,
    speed: Float = 1f
) {
    val reducedMotion = rememberReducedMotion()
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val tint = ink ?: if (darkTheme) Color.White else Color(0xFF1D1D1F)

    // One shared clock. Upstream uses real elapsed seconds; a linear infinite
    // transition is the Compose-native equivalent and keeps the phase smooth
    // across recompositions and configuration changes.
    val clock = rememberInfiniteTransition(label = "thinking_orb")
    val t by clock.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 360_000, easing = LinearEasing)
        ),
        label = "thinking_orb_phase"
    )

    val timeSeconds = if (reducedMotion || paused) REDUCED_MOTION_T else (t / 100f) * speed

    val dots = remember(state, timeSeconds, size) {
        OrbPresets.frame(state, size.value, timeSeconds).dots
    }

    val semanticsModifier = if (announce) {
        Modifier.semantics { contentDescription = label ?: defaultLabel(state) }
    } else {
        Modifier
    }

    Canvas(modifier = modifier.size(size).then(semanticsModifier)) {
        drawOrbDots(dots, tint)
    }
}

/** The static frame reduced-motion users see — a representative instant. */
private const val REDUCED_MOTION_T = 0.6f

private fun defaultLabel(state: OrbState): String = when (state) {
    OrbState.WORKING -> "Working"
    OrbState.SEARCHING -> "Searching"
    OrbState.SOLVING -> "Solving"
    OrbState.LISTENING -> "Listening"
}

/**
 * Matte grayscale dots, far→near, exactly as upstream paints them: the ink
 * value is mapped into the theme tint so the orb belongs to its surface
 * instead of being hardcoded gray.
 */
private fun DrawScope.drawOrbDots(dots: List<OrbDot>, tint: Color) {
    for (dot in dots) {
        // `white` 0 = darkest ink, 1 = lightest. Blend toward the tint so a
        // near dot reads bright on dark surfaces and dark on light ones.
        val w = dot.white.coerceIn(0f, 1f)
        val alpha = dot.alpha.coerceIn(0f, 1f)
        val shade = if (isDarkSurface(tint)) w else 1f - w
        drawCircle(
            color = tint.copy(alpha = alpha * (0.25f + 0.75f * shade)),
            radius = dot.r.coerceAtLeast(0.3f),
            center = Offset(dot.x, dot.y)
        )
    }
}

/**
 * A light tint means we're drawing on a dark surface, where deep-depth dots
 * must brighten. Derived from luminance so a custom tint still behaves.
 */
private fun isDarkSurface(tint: Color): Boolean {
    val l = 0.2126f * tint.red + 0.7152f * tint.green + 0.0722f * tint.blue
    return l > 0.6f
}
