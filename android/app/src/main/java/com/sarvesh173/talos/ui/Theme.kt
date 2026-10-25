package com.sarvesh173.talos.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.animation.animateColorAsState
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
// TALOS MATERIAL 3 EXPRESSIVE THEME - full M3E system, authored ground-up.
// Motion: MotionScheme.expressive() (verified spring physics: spatial 0.6/800,
// 0.8/380, 0.8/200 - effects critically damped 1.0/3800, 1.0/1600, 1.0/800).
// =============================================================================

/** 8dp spatial system tokens */
object MdSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 16.dp
    val md = 24.dp
    val lg = 32.dp
    val xl = 48.dp
}

// Brand accent anchors
val PrimaryAccent = Color(0xFFD0BCFF)       // M3 Expressive Luminous Lavender
val PrimaryIris = Color(0xFF6750A4)         // Google M3 Baseline Iris
val StatusGreen = Color(0xFF10B981)
val StatusAmber = Color(0xFFF59E0B)
val StatusRed = Color(0xFFEF4444)
val CoralPink = Color(0xFFEC4899)
val OmniBlue = Color(0xFF38BDF8)

// -----------------------------------------------------------------------------
// Dark M3 Expressive Purple palette (HCT-derived, baseline seed #6750A4)
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
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    surfaceDim = Color(0xFF141218),
    surfaceBright = Color(0xFF3B383E),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    inverseSurface = Color(0xFFE6E0E9),
    inverseOnSurface = Color(0xFF322F35),
    inversePrimary = Color(0xFF6750A4)
)

// -----------------------------------------------------------------------------
// Light palette (M3 Purple Lilac)
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
    error = Color(0xFFB3261E),
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
// M3 Shape Scale (10-level expressive scale)
// -----------------------------------------------------------------------------
val TalosShapes: Shapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
)

val ShapeLargeIncreased = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
val ShapeExtraLargeIncreased = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
val ShapeFull = androidx.compose.foundation.shape.CircleShape

// -----------------------------------------------------------------------------
// Typography scale
// -----------------------------------------------------------------------------
val TalosTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 52.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 9.sp, lineHeight = 12.sp, letterSpacing = 0.5.sp)
)

// -----------------------------------------------------------------------------
// Dynamic Theme CompositionLocal & Accessors
// -----------------------------------------------------------------------------
val LocalIsDark = compositionLocalOf { true }

val talosBg: Color @Composable get() = MaterialTheme.colorScheme.background
val talosSurface: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer
val talosBorder: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
val talosTextPrimary: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val talosTextMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

val SurfaceBorder: Color @Composable get() = talosBorder
val TextPrimary: Color @Composable get() = talosTextPrimary
val TextMuted: Color @Composable get() = talosTextMuted

// -----------------------------------------------------------------------------
// M3 Metallic specular brush tokens
// -----------------------------------------------------------------------------
@Composable
fun metallicBorderBrush(isDark: Boolean = LocalIsDark.current): Brush {
    return Brush.linearGradient(
        colors = if (isDark) {
            listOf(
                Color(0xFFD0BCFF).copy(alpha = 0.40f),
                Color(0xFF8B5CF6).copy(alpha = 0.22f),
                Color(0xFF4F378B).copy(alpha = 0.12f)
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

enum class AppThemeMode { DARK, LIGHT, SYSTEM }

/**
 * Smoothly interpolates the color scheme across theme switches.
 * Uses M3E default effects spring (critically damped 1.0/1600) - color never overshoots.
 */
@Composable
private fun animatedColorSchemeOf(target: ColorScheme): ColorScheme {
    val spec = M3EMotion.defaultEffects<Color>()
    return target.copy(
        primary = animateColorAsState(target.primary, spec, label = "th_primary").value,
        onPrimary = animateColorAsState(target.onPrimary, spec, label = "th_onPrimary").value,
        primaryContainer = animateColorAsState(target.primaryContainer, spec, label = "th_pc").value,
        onPrimaryContainer = animateColorAsState(target.onPrimaryContainer, spec, label = "th_opc").value,
        background = animateColorAsState(target.background, spec, label = "th_bg").value,
        surface = animateColorAsState(target.surface, spec, label = "th_surface").value,
        surfaceContainerLowest = animateColorAsState(target.surfaceContainerLowest, spec, label = "th_scl").value,
        surfaceContainerLow = animateColorAsState(target.surfaceContainerLow, spec, label = "th_sclow").value,
        surfaceContainer = animateColorAsState(target.surfaceContainer, spec, label = "th_sc").value,
        surfaceContainerHigh = animateColorAsState(target.surfaceContainerHigh, spec, label = "th_sch").value,
        surfaceContainerHighest = animateColorAsState(target.surfaceContainerHighest, spec, label = "th_schh").value,
        onSurface = animateColorAsState(target.onSurface, spec, label = "th_onSurface").value,
        onSurfaceVariant = animateColorAsState(target.onSurfaceVariant, spec, label = "th_osv").value,
        outline = animateColorAsState(target.outline, spec, label = "th_outline").value,
        outlineVariant = animateColorAsState(target.outlineVariant, spec, label = "th_ov").value,
        secondaryContainer = animateColorAsState(target.secondaryContainer, spec, label = "th_sc2").value,
        onSecondaryContainer = animateColorAsState(target.onSecondaryContainer, spec, label = "th_osc2").value,
        tertiary = animateColorAsState(target.tertiary, spec, label = "th_ter").value,
        tertiaryContainer = animateColorAsState(target.tertiaryContainer, spec, label = "th_tc").value
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
        MaterialExpressiveTheme(
            colorScheme = animatedScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = TalosShapes,
            typography = TalosTypography,
            content = content
        )
    }
}
