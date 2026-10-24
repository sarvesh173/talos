package com.sarvesh173.talos.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =============================================================================
// MATERIAL DESIGN 3: FULL SYSTEM TOKENS & METALLIC ACCENTS
// =============================================================================

/**
 * 8dp Spatial System Tokens (Google I/O 2026 M3 Guidance)
 */
object MdSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 16.dp
    val md = 24.dp
    val lg = 32.dp
    val xl = 48.dp
}

// Brand Accent Anchors
val PrimaryAccent = Color(0xFF6366F1)       // Indigo primary
val StatusGreen = Color(0xFF10B981)         // Status Online beacon
val StatusAmber = Color(0xFFF59E0B)         // Warning
val StatusRed = Color(0xFFEF4444)           // Error / Danger
val CoralPink = Color(0xFFEC4899)
val OmniBlue = Color(0xFF3B82F6)

// -----------------------------------------------------------------------------
// Dark & True OLED Black Palette (0% Light Waste, Infinite Contrast)
// -----------------------------------------------------------------------------
val DarkColorScheme = darkColorScheme(
    primary = PrimaryAccent,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF0C4A6E),
    secondaryContainer = Color(0xFF0369A1),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = Color(0xFFFFBF00),
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFFEF3C7),
    error = StatusRed,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    background = Color(0xFF000000),             // True Pure OLED Black
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF000000),                // Base surface
    onSurface = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF94A3B8),       // Muted typography
    surfaceContainerLowest = Color(0xFF000000), // Lowest plane
    surfaceContainerLow = Color(0xFF080B10),    // Recessed background
    surfaceContainer = Color(0xFF0C1017),       // Elevated card plane
    surfaceContainerHigh = Color(0xFF131923),   // Modal/Pop-in plane
    surfaceContainerHighest = Color(0xFF1A2230),// Highlighted card plane
    surfaceDim = Color(0xFF05070B),
    surfaceBright = Color(0xFF1F2937),
    outline = Color(0xFF334155),                // Control boundary (3:1 contrast)
    outlineVariant = Color(0xFF1E2430),         // Decorative hairlines
    inverseSurface = Color(0xFFF1F5F9),
    inverseOnSurface = Color(0xFF0F172A),
    inversePrimary = Color(0xFF4338CA)
)

// -----------------------------------------------------------------------------
// Light Palette (Calm Editorial Slate)
// -----------------------------------------------------------------------------
val LightColorScheme = lightColorScheme(
    primary = PrimaryAccent,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF0284C7),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = Color(0xFFD97706),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    error = StatusRed,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF64748B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F5F9),
    surfaceContainer = Color(0xFFE2E8F0),
    surfaceContainerHigh = Color(0xFFCBD5E1),
    surfaceContainerHighest = Color(0xFF94A3B8),
    surfaceDim = Color(0xFFE2E8F0),
    surfaceBright = Color(0xFFFFFFFF),
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFFE2E8F0),
    inverseSurface = Color(0xFF0F172A),
    inverseOnSurface = Color(0xFFF8FAFC),
    inversePrimary = Color(0xFF818CF8)
)

// -----------------------------------------------------------------------------
// Material Design 3 Shape Scale
// -----------------------------------------------------------------------------
val TalosShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// Expressive Shape Extensions
val ShapeLargeIncreased = RoundedCornerShape(20.dp)
val ShapeExtraLargeIncreased = RoundedCornerShape(32.dp)
val ShapeFull = CircleShape

// -----------------------------------------------------------------------------
// Material Design 3 Typography Scale
// -----------------------------------------------------------------------------
val TalosTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.5.sp
    )
)

// -----------------------------------------------------------------------------
// Dynamic Theme CompositionLocal & Accessors
// -----------------------------------------------------------------------------
val LocalIsDark = compositionLocalOf { true }

val talosBg: Color
    @Composable get() = MaterialTheme.colorScheme.background

val talosSurface: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainer

val talosBorder: Color
    @Composable get() = MaterialTheme.colorScheme.outlineVariant

val talosTextPrimary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface

val talosTextMuted: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

// Backward-compatible aliases
val SurfaceBorder: Color @Composable get() = talosBorder
val TextPrimary: Color @Composable get() = talosTextPrimary
val TextMuted: Color @Composable get() = talosTextMuted

// -----------------------------------------------------------------------------
// M3 Metallic Specular Brush Tokens
// -----------------------------------------------------------------------------
@Composable
fun metallicBorderBrush(isDark: Boolean = LocalIsDark.current): Brush {
    return Brush.linearGradient(
        colors = if (isDark) {
            listOf(
                Color(0xFF94A3B8).copy(alpha = 0.45f), // Specular highlight
                Color(0xFF475569).copy(alpha = 0.25f), // Midtone
                Color(0xFF1E2430).copy(alpha = 0.60f)  // Core shadow
            )
        } else {
            listOf(
                Color(0xFFCBD5E1).copy(alpha = 0.80f),
                Color(0xFF94A3B8).copy(alpha = 0.40f),
                Color(0xFFE2E8F0).copy(alpha = 0.90f)
            )
        }
    )
}

enum class AppThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

/**
 * Smoothly interpolates the color scheme across theme switches for fluid M3 transition satisfaction.
 */
@Composable
private fun animatedColorSchemeOf(target: ColorScheme): ColorScheme {
    val animSpec = tween<Color>(360, easing = CubicBezierEasing(0.05f, 0.70f, 0.10f, 1.00f))
    return target.copy(
        primary = animateColorAsState(target.primary, animSpec, label = "th_primary").value,
        background = animateColorAsState(target.background, animSpec, label = "th_bg").value,
        surface = animateColorAsState(target.surface, animSpec, label = "th_surface").value,
        surfaceContainer = animateColorAsState(target.surfaceContainer, animSpec, label = "th_sc").value,
        surfaceContainerHigh = animateColorAsState(target.surfaceContainerHigh, animSpec, label = "th_sch").value,
        surfaceContainerHighest = animateColorAsState(target.surfaceContainerHighest, animSpec, label = "th_schh").value,
        onSurface = animateColorAsState(target.onSurface, animSpec, label = "th_onSurface").value,
        onSurfaceVariant = animateColorAsState(target.onSurfaceVariant, animSpec, label = "th_onSurfaceVar").value,
        outline = animateColorAsState(target.outline, animSpec, label = "th_outline").value,
        outlineVariant = animateColorAsState(target.outlineVariant, animSpec, label = "th_outlineVar").value
    )
}

@Composable
fun TalosTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val targetScheme = if (isDark) DarkColorScheme else LightColorScheme
    val animatedScheme = animatedColorSchemeOf(targetScheme)

    CompositionLocalProvider(LocalIsDark provides isDark) {
        MaterialTheme(
            colorScheme = animatedScheme,
            shapes = TalosShapes,
            typography = TalosTypography,
            content = content
        )
    }
}
