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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =============================================================================
// TALOS "MOLTEN FORGE" M3 EXPRESSIVE THEME.
//
// Why copper, not violet (antislop R-01/R-31, written reason): the M3 baseline
// violet (#6750A4/#D0BCFF) is the generic AI-default palette - indistinguishable
// from every other generated app. TALOS is a machined instrument (forged metal,
// brushed specular borders, obsidian emblem sockets), so the palette is drawn
// from the material itself: molten copper (primary), steel (secondary),
// verdigris patina (tertiary = live mesh), on warm graphite surfaces.
//
// Motion: MotionScheme.expressive() (spatial 0.6/800, 0.8/380, 0.8/200 -
// effects critically damped 1.0/3800, 1.0/1600, 1.0/800).
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

// -----------------------------------------------------------------------------
// DARK MOLTEN FORGE palette (contrast-verified: onSurface 14.7:1, onPrimary 5.9:1,
// onSurfaceVariant 8.8:1, primary on surfaceContainer 7.4:1)
// -----------------------------------------------------------------------------
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE0A261),            // Molten Copper
    onPrimary = Color(0xFF4A2A08),
    primaryContainer = Color(0xFF6B4116),   // Bronze ingot
    onPrimaryContainer = Color(0xFFFFDCC0),
    secondary = Color(0xFFB9C0C8),          // Machined Steel
    onSecondary = Color(0xFF242A31),
    secondaryContainer = Color(0xFF3A4149),
    onSecondaryContainer = Color(0xFFDCE2EA),
    tertiary = Color(0xFF7FC8BB),           // Verdigris Patina (live mesh)
    onTertiary = Color(0xFF00382F),
    tertiaryContainer = Color(0xFF1E4E45),
    onTertiaryContainer = Color(0xFF9CE8D9),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121316),         // Graphite casing
    onBackground = Color(0xFFE8E4DE),
    surface = Color(0xFF121316),
    onSurface = Color(0xFFE8E4DE),
    onSurfaceVariant = Color(0xFFB8B2AA),
    surfaceContainerLowest = Color(0xFF0B0C0E),
    surfaceContainerLow = Color(0xFF191A1E),
    surfaceContainer = Color(0xFF1E2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353B),
    surfaceDim = Color(0xFF121316),
    surfaceBright = Color(0xFF3A3D42),
    outline = Color(0xFF8E8A84),
    outlineVariant = Color(0xFF70747A),
    inverseSurface = Color(0xFFE8E4DE),
    inverseOnSurface = Color(0xFF2F3134),
    inversePrimary = Color(0xFF6B4116)
)

// -----------------------------------------------------------------------------
// LIGHT MOLTEN FORGE palette (warm paper + bronze; onSurface 16.4:1,
// onSurfaceVariant 8.6:1, primary on surfaceContainer 5.2:1)
// -----------------------------------------------------------------------------
val LightColorScheme = lightColorScheme(
    primary = Color(0xFF8A5524),            // Deep Bronze
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCC0),
    onPrimaryContainer = Color(0xFF2E1804),
    secondary = Color(0xFF4E5A66),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE2EA),
    onSecondaryContainer = Color(0xFF222930),
    tertiary = Color(0xFF2F6A60),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF9CE8D9),
    onTertiaryContainer = Color(0xFF002019),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBF8F3),         // Warm bone paper
    onBackground = Color(0xFF1B1A17),
    surface = Color(0xFFFBF8F3),
    onSurface = Color(0xFF1B1A17),
    onSurfaceVariant = Color(0xFF4C4842),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2EB),
    surfaceContainer = Color(0xFFF1EBE3),
    surfaceContainerHigh = Color(0xFFEBE5DC),
    surfaceContainerHighest = Color(0xFFE5DFD6),
    surfaceDim = Color(0xFFDED8CE),
    surfaceBright = Color(0xFFFBF8F3),
    outline = Color(0xFF7E7970),
    outlineVariant = Color(0xFF8C8377),
    inverseSurface = Color(0xFF303235),
    inverseOnSurface = Color(0xFFF4EFE8),
    inversePrimary = Color(0xFFE0A261)
)

// -----------------------------------------------------------------------------
// Semantic status palette - mode-aware so text/icons clear 4.5:1 in BOTH modes.
// Verified: dark #34D399 = 8.49:1 on surfaceContainer, light #047857 = 4.63:1.
// Components read ONE code path (LocalTalosStatus) - no per-component isDark.
// -----------------------------------------------------------------------------
data class TalosStatusColors(
    val success: Color,
    val successContainer: Color,
    val onSuccess: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarning: Color,
    val danger: Color
)

private val DarkStatusColors = TalosStatusColors(
    success = Color(0xFF34D399),
    successContainer = Color(0xFF0E3B2A),
    onSuccess = Color(0xFFA7F3D0),
    warning = Color(0xFFFBBF24),
    warningContainer = Color(0xFF4A3208),
    onWarning = Color(0xFFFEF3C7),
    danger = Color(0xFFFF8A80)
)

private val LightStatusColors = TalosStatusColors(
    success = Color(0xFF047857),
    successContainer = Color(0xFFD1FAE5),
    onSuccess = Color(0xFF064E3B),
    warning = Color(0xFF92400E),
    warningContainer = Color(0xFFFEF3C7),
    onWarning = Color(0xFF78350F),
    danger = Color(0xFFB3261E)
)

val LocalTalosStatus = staticCompositionLocalOf { DarkStatusColors }

/** One fixed obsidian substrate for brand emblems in BOTH themes (M3E emblem-tile rule). */
val EmblemSocket = Color(0xFF101114)

// Legacy accent names now resolve as SCHEME ROLES - one code path, no hardcoded fills.
val PrimaryAccent: Color @Composable get() = MaterialTheme.colorScheme.primary

// Semantic status accessors (keep the historical call-site names).
val StatusGreen: Color @Composable get() = LocalTalosStatus.current.success
val StatusAmber: Color @Composable get() = LocalTalosStatus.current.warning
val StatusRed: Color @Composable get() = LocalTalosStatus.current.danger

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
// Typography scale (Conforming to Google MD3 Typescale from hamen/material-3-skill)
// -----------------------------------------------------------------------------
val TalosTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
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
/**
 * One brush for both modes. The scheme already carries light + dark role
 * values, so there is no second code path and no per-mode asset to maintain.
 */
@Composable
fun metallicBorderBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    return Brush.linearGradient(
        colors = listOf(
            scheme.primary.copy(alpha = 0.40f),
            scheme.primary.copy(alpha = 0.22f),
            scheme.primaryContainer.copy(alpha = 0.16f)
        )
    )
}

enum class AppThemeMode { DARK, LIGHT, SYSTEM }

/**
 * Smoothly interpolates the ENTIRE scheme across theme switches.
 * M3E default effects spring (critically damped 1.0/1600) - color never overshoots.
 */
@Composable
private fun animatedColorSchemeOf(target: ColorScheme): ColorScheme {
    val spec = M3EMotion.defaultEffects<Color>()
    @Composable
    fun anim(label: String, value: Color) =
        animateColorAsState(value, spec, label = label).value
    return target.copy(
        primary = anim("th_primary", target.primary),
        onPrimary = anim("th_onPrimary", target.onPrimary),
        primaryContainer = anim("th_pc", target.primaryContainer),
        onPrimaryContainer = anim("th_opc", target.onPrimaryContainer),
        secondary = anim("th_sec", target.secondary),
        onSecondary = anim("th_onSec", target.onSecondary),
        secondaryContainer = anim("th_sc2", target.secondaryContainer),
        onSecondaryContainer = anim("th_osc2", target.onSecondaryContainer),
        tertiary = anim("th_ter", target.tertiary),
        onTertiary = anim("th_onTer", target.onTertiary),
        tertiaryContainer = anim("th_tc", target.tertiaryContainer),
        onTertiaryContainer = anim("th_otc", target.onTertiaryContainer),
        error = anim("th_err", target.error),
        onError = anim("th_onErr", target.onError),
        errorContainer = anim("th_ec", target.errorContainer),
        onErrorContainer = anim("th_oec", target.onErrorContainer),
        background = anim("th_bg", target.background),
        onBackground = anim("th_onBg", target.onBackground),
        surface = anim("th_surface", target.surface),
        onSurface = anim("th_onSurface", target.onSurface),
        onSurfaceVariant = anim("th_osv", target.onSurfaceVariant),
        surfaceContainerLowest = anim("th_scl", target.surfaceContainerLowest),
        surfaceContainerLow = anim("th_sclow", target.surfaceContainerLow),
        surfaceContainer = anim("th_sc", target.surfaceContainer),
        surfaceContainerHigh = anim("th_sch", target.surfaceContainerHigh),
        surfaceContainerHighest = anim("th_schh", target.surfaceContainerHighest),
        surfaceDim = anim("th_sdim", target.surfaceDim),
        surfaceBright = anim("th_sbright", target.surfaceBright),
        outline = anim("th_outline", target.outline),
        outlineVariant = anim("th_ov", target.outlineVariant),
        inverseSurface = anim("th_is", target.inverseSurface),
        inverseOnSurface = anim("th_ios", target.inverseOnSurface),
        inversePrimary = anim("th_ip", target.inversePrimary)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TalosTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    remoteConfig: TalosThemeConfig? = null,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    // Remote tokens (talos-core /api/theme) override the built-in scheme -
    // same runtime-swap pattern ReadYou uses for its tonal palettes.
    val targetScheme = when {
        remoteConfig != null && isDark -> remoteConfig.darkScheme()
        remoteConfig != null -> remoteConfig.lightScheme()
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }
    val animatedScheme = animatedColorSchemeOf(targetScheme)
    val statusColors = if (isDark) DarkStatusColors else LightStatusColors

    CompositionLocalProvider(
        LocalIsDark provides isDark,
        LocalTalosStatus provides statusColors
    ) {
        MaterialExpressiveTheme(
            colorScheme = animatedScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = TalosShapes,
            typography = TalosTypography,
            content = content
        )
    }
}
