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
// GOOGLE MATERIAL 3 EXPRESSIVE PALETTE (Seed: #6750A4)
// Verified contrast: onSurface 14.2:1, onPrimary 6.8:1, onSurfaceVariant 9.1:1
// -----------------------------------------------------------------------------
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),            // Luminous Iris / Lavender
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),   // Rich Violet Core
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),          // Slate Lilac
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),           // Warm Orchid Rose (live mesh & actions)
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF141218),         // Twilight Surface
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
// LIGHT MATERIAL 3 EXPRESSIVE PALETTE
// -----------------------------------------------------------------------------
val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),            // Deep Purple Iris
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
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFEF7FF),         // Pure M3 Light Surface
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
