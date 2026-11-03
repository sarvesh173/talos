package com.sarvesh173.talos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild

/**
 * TALOS Aggressive Apple Liquid Glass Design System (iOS 26+ Specification)
 * Synthesizes:
 * 1. Apple Liquid Glass: Directional top-edge light catching highlight (1.dp specular bevel)
 * 2. Optical Caustic Refraction: Dynamic ambient backlight reflecting accent colors
 * 3. Windows 11 Fluent Acrylic & M3 Expressive: Multi-layer depth and tactile spring motion
 * 4. Samsung One UI 8.5: Bottom-weighted interaction zones on tall screens
 */

/**
 * Creates a directional specular light-catching border brush.
 * Simulates real optical glass where ambient light from above catches the top rim.
 */
@Composable
fun talosSpecularGlassBorder(
    isDark: Boolean,
    strokeWidth: Dp = 1.dp,
    highlightAlpha: Float = if (isDark) 0.38f else 0.70f,
    shadowAlpha: Float = if (isDark) 0.05f else 0.10f
): BorderStroke {
    val highlightColor = Color.White.copy(alpha = highlightAlpha)
    val shadowColor = Color.White.copy(alpha = shadowAlpha)
    return BorderStroke(
        width = strokeWidth,
        brush = Brush.verticalGradient(
            colors = listOf(highlightColor, shadowColor)
        )
    )
}

/**
 * Creates an aggressive Apple Liquid Glass card border with specular top-edge refraction.
 */
@Composable
fun appleLiquidGlassCardBorder(
    isDark: Boolean,
    strokeWidth: Dp = 1.dp
): BorderStroke {
    val topHighlight = if (isDark) Color.White.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.75f)
    val bottomRim = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.08f)
    return BorderStroke(
        width = strokeWidth,
        brush = Brush.verticalGradient(
            colors = listOf(topHighlight, bottomRim)
        )
    )
}

/**
 * Creates a dynamic Liquid Glass background brush with optical caustic glow.
 */
@Composable
fun appleLiquidGlassBackground(
    isDark: Boolean,
    accentColor: Color
): Brush {
    val baseColor = if (isDark) Color(0xFF161820).copy(alpha = 0.82f) else Color(0xFFFFFFFF).copy(alpha = 0.88f)
    val causticTint = accentColor.copy(alpha = if (isDark) 0.14f else 0.08f)
    return Brush.radialGradient(
        colors = listOf(causticTint, baseColor),
        radius = 800f
    )
}

/**
 * Applies true hardware backdrop blur via Haze with a clipped glass geometry
 * and a directional specular rim.
 */
fun Modifier.talosGlassSurface(
    hazeState: HazeState,
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color,
    blurRadius: Dp = 24.dp
): Modifier = this
    .clip(shape)
    .hazeChild(
        state = hazeState,
        shape = shape,
        style = HazeDefaults.style(
            backgroundColor = backgroundColor,
            blurRadius = blurRadius
        )
    )
