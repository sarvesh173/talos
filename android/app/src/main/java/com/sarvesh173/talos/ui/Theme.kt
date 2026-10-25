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
val PrimaryAccent = Color(0xFFD0BCFF)       // M3 Expressive Luminous Lavender
val PrimaryIris = Color(0xFF6750A4)         // Google M3 Baseline Iris
val StatusGreen = Color(0xFF10B981)         // Status Online beacon
val StatusAmber = Color(0xFFF59E0B)         // Warning
val StatusRed = Color(0xFFEF4444)           // Error / Danger
val CoralPink = Color(0xFFEC4899)
val OmniBlue = Color(0xFF38BDF8)

// -----------------------------------------------------------------------------
// Dark M3 Expressive Purple Gradient Palette (Google Material 3 Baseline)
// -----------------------------------------------------------------------------
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    error = StatusRed,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF141218),             // Deep Twilight Purple Surface
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),                // Base surface
    onSurface = Color(0xFFE6E0E9),
    onSurfaceVariant = Color(0xFFCAC4D0),       // Muted typography
    surfaceContainerLowest = Color(0xFF0F0D13), // Lowest plane
    surfaceContainerLow = Color(0xFF1D1B20),    // Recessed background
    surfaceContainer = Color(0xFF211F26),       // Elevated card plane
    surfaceContainerHigh = Color(0xFF2B2930),   // Modal/Pop-in plane
    surfaceContainerHighest = Color(0xFF36343B),// Highlighted card plane
    surfaceDim = Color(0xFF141218),
    surfaceBright = Color(0xFF3B383E),
    outline = Color(0xFF938F99),                // Control boundary (3:1 contrast)
    outlineVariant = Color(0xFF49454F),         // Decorative hairlines
    inverseSurface = Color(0xFFE6E0E9),
    inverseOnSurface = Color(0xFF322F35),
    inversePrimary = Color(0xFF6750A4)
)

// -----------------------------------------------------------------------------
// Light Palette (Google Material 3 Purple Lilac)
// -----------------------------------------------------------------------------
val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    error = StatusRed,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFEF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFEF7FF),
    onSurface = Color(0xFF1D1B20),
    onSurfaceVariant = Color(0xFF49454F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    surfaceDim = Color(0xFFDED8E1),
    surfaceBright = Color(0xFFFEF7FF),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    inverseSurface = Color(0xFF322F35),
    inverseOnSurface = Color(0xFFF5EFF7),
    inversePrimary = Color(0xFFD0BCFF)
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
                Color(0xFFD0BCFF).copy(alpha = 0.40f), // Luminous lavender specular highlight
                Color(0xFF8B5CF6).copy(alpha = 0.22f), // Radiant violet midtone
                Color(0xFF4F378B).copy(alpha = 0.12f)  // Deep royal violet shadow
            )
        } else {
            listOf(
                Color(0xFF6750A4).copy(alpha = 0.35f),
                Color(0xFF9A82DB).copy(alpha = 0.20f),
                Color(0xFFEADDFF).copy(alpha = 0.50f)
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
