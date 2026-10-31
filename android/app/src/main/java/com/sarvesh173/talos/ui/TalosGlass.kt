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
 * TALOS Liquid Glass & One UI 8.5 Reachability Design System
 * 
 * Synthesizes:
 * 1. Apple Liquid Glass: Directional top-edge light catching highlight (1.dp specular bevel)
 * 2. Windows 11 Fluent Acrylic: 5-layer occlusion depth over opaque backdrop
 * 3. Material 3 Expressive: Tonal tints (0.dp elevation) and tactile spring motion
 * 4. Samsung One UI 8.5: Bottom-weighted interaction zones on tall screens
 */

/**
 * Creates a directional specular light-catching border brush.
 * Simulates real optical glass where ambient light from above catches the top rim.
 */
@Composable
fun talosSpecularGlassBorder(
    isDark: Boolean,
    strokeWidth: Dp = 1.2.dp,
    highlightAlpha: Float = if (isDark) 0.35f else 0.60f,
    shadowAlpha: Float = if (isDark) 0.05f else 0.15f
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
 * Applies true hardware backdrop blur via Haze with a clipped glass geometry
 * and a directional specular rim.
 */
fun Modifier.talosGlassSurface(
    hazeState: HazeState,
    shape: Shape = RoundedCornerShape(22.dp),
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
