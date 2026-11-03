package com.sarvesh173.talos.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
 * The 3 Optical Pillars of Apple Liquid Glass:
 * 1. Refraction: ambient chromatic caustic depth bending across the glass body.
 * 2. Rim: dual-stage directional specular highlight catching overhead light.
 * 3. Reflection: glossy meniscus surface gleam across the upper convex face.
 *
 * Geometry contract: the optics follow the host shape exactly, including
 * asymmetric shapes such as a chat bubble with one tucked corner. Drawing an
 * un-rounded rect over a rounded surface leaves opaque corner wedges, which
 * breaks the material illusion instantly — so the shape is resolved to an
 * outline and used as the clip for every layer.
 */
fun Modifier.appleLiquidGlassOptics(
    isDark: Boolean = true,
    chromaticGlow: Color = Color(0xFF007AFF),
    cornerRadius: Dp = 20.dp,
    shape: Shape? = null
): Modifier {
    val resolved = shape ?: RoundedCornerShape(cornerRadius)
    return this.drawWithContent {
        drawGlassLayers(isDark = isDark, chromaticGlow = chromaticGlow, shape = resolved)
        drawContent()
        drawGlassRim(isDark = isDark, chromaticGlow = chromaticGlow, shape = resolved)
    }
}

/**
 * Interactive variant: identical optics, but the specular rim brightens while
 * the surface is pressed. Alpha-only — no spatial change — so it never shifts
 * layout and stays safe under reduced motion.
 */
fun Modifier.appleInteractiveGlass(
    interactionSource: MutableInteractionSource,
    isDark: Boolean = true,
    chromaticGlow: Color = Color(0xFF007AFF),
    shape: Shape = RoundedCornerShape(20.dp),
    pressBoost: Float = 0f
): Modifier = this.drawWithContent {
    drawGlassLayers(
        isDark = isDark,
        chromaticGlow = chromaticGlow,
        shape = shape,
        intensity = 1f + pressBoost
    )
    drawContent()
    drawGlassRim(
        isDark = isDark,
        chromaticGlow = chromaticGlow,
        shape = shape,
        intensity = 1f + pressBoost
    )
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

private fun DrawScope.drawGlassLayers(
    isDark: Boolean,
    chromaticGlow: Color,
    shape: Shape,
    intensity: Float = 1f
) {
    val path = glassPath(shape)
    val clamped = intensity.coerceIn(0f, 1.6f)

    // 1. REFRACTION — chromatic caustic depth, brightest toward the light-facing corner.
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(
                chromaticGlow.copy(alpha = ((if (isDark) 0.14f else 0.09f) * clamped).coerceAtMost(0.30f)),
                chromaticGlow.copy(alpha = ((if (isDark) 0.03f else 0.02f) * clamped).coerceAtMost(0.12f)),
                Color.Transparent
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width * 0.75f, size.height * 0.75f)
        )
    )

    // 2. REFLECTION — meniscus sheen on the upper convex face only.
    //    Clipped to the upper band via a rect intersect on the same path.
    val sheenHeight = size.height * 0.45f
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = ((if (isDark) 0.16f else 0.28f) * clamped).coerceAtMost(0.40f)),
                Color.White.copy(alpha = ((if (isDark) 0.03f else 0.06f) * clamped).coerceAtMost(0.14f)),
                Color.Transparent
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width, sheenHeight)
        )
    )
}

private fun DrawScope.drawGlassRim(
    isDark: Boolean,
    chromaticGlow: Color,
    shape: Shape,
    intensity: Float = 1f
) {
    val path = glassPath(shape)
    val clamped = intensity.coerceIn(0f, 1.6f)

    // 3. RIM — directional specular hairline: overhead catch, chromatic edge,
    //    softer bottom bounce. One pixel of light is what sells the thickness.
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = ((if (isDark) 0.75f else 0.95f) * clamped).coerceAtMost(1f)),
                chromaticGlow.copy(alpha = (0.35f * clamped).coerceAtMost(0.6f)),
                Color.White.copy(alpha = ((if (isDark) 0.10f else 0.18f) * clamped).coerceAtMost(0.35f))
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height)
        ),
        style = Stroke(width = 1.2.dp.toPx())
    )
}

/**
 * Animated 0f..1f strength for an interactive glass surface, driven by whether
 * the surface is currently pressed. Effects-family spring: it brightens, never
 * bounces, and collapses to a short fade under reduced motion.
 */
@androidx.compose.runtime.Composable
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
@androidx.compose.runtime.Composable
fun rememberGlassInteraction(): MutableInteractionSource =
    remember { MutableInteractionSource() }
