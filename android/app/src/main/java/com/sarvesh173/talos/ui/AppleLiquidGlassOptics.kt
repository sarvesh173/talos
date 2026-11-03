package com.sarvesh173.talos.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * The 3 Optical Pillars of Apple Liquid Glass:
 * 1. Refraction: Ambient chromatic caustic depth bending across the glass body.
 * 2. Rim: Dual-stage directional specular highlight catching overhead light.
 * 3. Reflection: Glossy meniscus surface gleam across the upper convex face.
 */
fun Modifier.appleLiquidGlassOptics(
    isDark: Boolean = true,
    chromaticGlow: Color = Color(0xFF007AFF)
): Modifier = this.drawWithContent {
    // 1. REFRACTION: Optical light bending & ambient chromatic depth
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                chromaticGlow.copy(alpha = if (isDark) 0.14f else 0.09f),
                chromaticGlow.copy(alpha = if (isDark) 0.03f else 0.02f),
                Color.Transparent
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width * 0.75f, size.height * 0.75f)
        ),
        size = size
    )

    // Render underlying card content cleanly
    drawContent()

    // 2. REFLECTION: Diagonal gloss meniscus sheen across top 45% of the glass pod
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = if (isDark) 0.16f else 0.28f),
                Color.White.copy(alpha = if (isDark) 0.03f else 0.06f),
                Color.Transparent
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height * 0.45f)
        ),
        size = Size(size.width, size.height * 0.45f)
    )

    // 3. RIM: Directional Specular Hairline (Overhead light catch -> subtle bottom bounce)
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = if (isDark) 0.75f else 0.95f),
                chromaticGlow.copy(alpha = 0.35f),
                Color.White.copy(alpha = if (isDark) 0.10f else 0.18f)
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height)
        ),
        size = size,
        style = Stroke(width = 1.2.dp.toPx())
    )
}
