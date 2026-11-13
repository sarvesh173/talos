package com.sarvesh173.talos.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Real glass. The translucency comes from the fill colour's alpha at the call
 * site — this modifier only adds the one thing a fill cannot: a hairline that
 * catches the light along the top edge.
 *
 * What it deliberately does NOT do, because that is what made the old version
 * look like dirty plastic:
 *  - no chromatic colour wash painted over the content (up to 0.30 alpha),
 *  - no white meniscus sheen across the upper half (up to 0.40 alpha),
 *  - no 0.75-alpha white rim stroke running around the whole shape.
 *
 * Overdraw stays at one stroke per surface, which matters on this device.
 */
fun Modifier.appleLiquidGlassOptics(
    isDark: Boolean = true,
    chromaticGlow: Color = Color.Unspecified,
    cornerRadius: Dp = 20.dp,
    shape: Shape? = null
): Modifier {
    val resolved = shape ?: RoundedCornerShape(cornerRadius)
    return this.drawWithContent {
        drawContent()
        drawGlassRim(isDark = isDark, shape = resolved)
    }
}

/**
 * Interactive variant: the same hairline, brightened while pressed. Alpha-only,
 * so it never shifts layout and stays safe under reduced motion.
 */
fun Modifier.appleInteractiveGlass(
    interactionSource: MutableInteractionSource,
    isDark: Boolean = true,
    chromaticGlow: Color = Color.Unspecified,
    shape: Shape = RoundedCornerShape(20.dp),
    pressBoost: Float = 0f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val boost by animateFloatAsState(
        targetValue = if (pressed) pressBoost else 0f,
        label = "glass_press_boost"
    )
    return this.drawWithContent {
        drawContent()
        drawGlassRim(isDark = isDark, shape = shape, intensity = 1f + boost)
    }
}

// ---------------------------------------------------------------------------
// Internals
// ---------------------------------------------------------------------------

private fun DrawScope.glassPath(
    shape: Shape,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr
): Path {
    val outline = shape.createOutline(size, layoutDirection, this)
    return when (outline) {
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
        is Outline.Generic -> outline.path
    }
}

/** One hairline: light lands on top, fades to nothing at the bottom. */
private fun DrawScope.drawGlassRim(
    isDark: Boolean,
    shape: Shape,
    intensity: Float = 1f
) {
    val c = intensity.coerceIn(0f, 1.6f)
    val top = if (isDark) Color.White.copy(alpha = (0.12f * c).coerceAtMost(0.18f))
    else Color.Black.copy(alpha = (0.08f * c).coerceAtMost(0.13f))
    val bottom = if (isDark) Color.White.copy(alpha = (0.03f * c).coerceAtMost(0.05f))
    else Color.Black.copy(alpha = (0.02f * c).coerceAtMost(0.04f))
    drawPath(
        path = glassPath(shape),
        brush = Brush.verticalGradient(
            colors = listOf(top, bottom),
            start = Offset(0f, 0f),
            end = Offset(0f, size.height)
        ),
        style = Stroke(width = 0.8.dp.toPx())
    )
}

/**
 * Animated 0f..1f strength for an interactive glass surface, driven by whether
 * the surface is currently pressed. Effects-family spring: it brightens, never
 * bounces, and collapses to a short fade under reduced motion.
 */
@Composable
fun rememberGlassPressAlpha(
    interactionSource: MutableInteractionSource,
    reducedMotion: Boolean
): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = effectsSpring(reducedMotion),
        label = "glass_press_alpha"
    )
    return alpha
}

/** Convenience: an interaction source for glass surfaces that also click. */
@Composable
fun rememberGlassInteraction(): MutableInteractionSource =
    remember { MutableInteractionSource() }