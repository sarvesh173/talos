package com.sarvesh173.talos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle

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
    // A white-to-white gradient rim is a hairline, not glass: it reads as a
    // plastic edge and carries no information. Callers keep the name.
    return BorderStroke(
        width = strokeWidth,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/**
 * Creates Apple's signature iridescent chromatic glass rim border,
 * directly matching the WWDC25 Liquid Glass hero visual from Apple Developer.
 * Features a delicate caustic spectrum (White -> Rose Pink -> Violet -> Azure -> Green)
 * across the top-left specular bevel.
 */
@Composable
fun appleLiquidGlassIridescentBorder(
    isDark: Boolean,
    strokeWidth: Dp = 1.2.dp
): BorderStroke {
    val colors = if (isDark) {
        listOf(
            Color.White.copy(alpha = 0.48f),
            Color(0xFFFF2D55).copy(alpha = 0.35f), // Apple Rose Pink
            Color(0xFFAF52DE).copy(alpha = 0.40f), // Apple Violet
            Color(0xFF5AC8D8).copy(alpha = 0.38f),
            Color(0xFF34C759).copy(alpha = 0.28f), // Apple Green
            Color.White.copy(alpha = 0.12f),
            Color.White.copy(alpha = 0.04f)
        )
    } else {
        listOf(
            Color.White.copy(alpha = 0.85f),
            Color(0xFFFF2D55).copy(alpha = 0.45f),
            Color(0xFFAF52DE).copy(alpha = 0.50f),
            Color(0xFF5AC8D8).copy(alpha = 0.45f),
            Color(0xFF34C759).copy(alpha = 0.35f),
            Color.White.copy(alpha = 0.35f),
            Color.Black.copy(alpha = 0.06f)
        )
    }
    return BorderStroke(
        width = strokeWidth,
        brush = Brush.linearGradient(
            colors = colors,
            start = androidx.compose.ui.geometry.Offset(0f, 0f),
            end = androidx.compose.ui.geometry.Offset(1000f, 1000f)
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
    blurRadius: Dp = 24.dp,
    isDark: Boolean = true
): Modifier = this
    .clip(shape)
    .background(backgroundColor)
    // Below API 31 Haze draws no blur — only a flat scrim. In light theme that
    // scrim is White over a White page (contrast 1.000:1), so without a rim the
    // bars are invisible. A hairline gives the surface an edge on every API.
    .border(
        width = 0.8.dp,
        color = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.07f),
        shape = shape
    )

/**
 * Floating Circular Liquid Glass Action Button,
 * directly matching the centered (+) glass lens from Apple Developer Design.
 */
@Composable
fun AppleLiquidGlassFloatingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    val isDark = LocalIsDark.current
    Surface(
        shape = CircleShape,
        color = if (isDark) Color(0xFF242630).copy(alpha = 0.75f) else Color(0xFFFFFFFF).copy(alpha = 0.82f),
        border = BorderStroke(
            1.2.dp,
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (isDark) 0.60f else 0.90f),
                    Color.White.copy(alpha = if (isDark) 0.12f else 0.25f)
                )
            )
        ),
        shadowElevation = 6.dp,
        modifier = modifier
            .size(48.dp)
            .applePressable(0.92f) { onClick() }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
    }
}
