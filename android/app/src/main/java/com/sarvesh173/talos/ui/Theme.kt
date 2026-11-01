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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
// APPLE iOS HUMAN INTERFACE GUIDELINES (HIG) MONOCHROMATIC PALETTE
// Pure OLED black canvas (#000000), Apple secondary card surface (#1C1C1E),
// Apple tertiary elevated chrome (#2C2C2E), and Apple Action Blue (#0071E3 / #2997FF).
// -----------------------------------------------------------------------------
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF2997FF),            // Apple Action Blue (Luminous on Dark)
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF0040DD),   // Deep Apple Blue container
    onPrimaryContainer = Color(0xFFD0E2FF),
    secondary = Color(0xFF8E8E93),          // Apple System Gray (Clean Muted)
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF2C2C2E), // Apple Tertiary System Background
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF30D158),           // Apple System Green (Live Node status)
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF0D3E1A),
    onTertiaryContainer = Color(0xFFA3F3B8),
    error = Color(0xFFFF453A),              // Apple System Red
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF491210),
    onErrorContainer = Color(0xFFFFB4AB),
    background = Color(0xFF000000),         // Pure Apple OLED Black Canvas
    onBackground = Color(0xFFFFFFFF),       // Pure White Text
    surface = Color(0xFF000000),            // True OLED Surface
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF8E8E93),   // Apple Secondary Text Gray
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF141416),
    surfaceContainer = Color(0xFF1C1C1E),   // Apple Secondary System Background (Cards)
    surfaceContainerHigh = Color(0xFF2C2C2E), // Apple Tertiary System Background (Floating headers)
    surfaceContainerHighest = Color(0xFF3A3A3C), // Apple Elevated controls / Separators
    surfaceDim = Color(0xFF000000),
    surfaceBright = Color(0xFF2C2C2E),
    outline = Color(0xFF38383A),            // Apple Hairline Border
    outlineVariant = Color(0xFF2C2C2E),
    inverseSurface = Color(0xFFF2F2F7),
    inverseOnSurface = Color(0xFF1C1C1E),
    inversePrimary = Color(0xFF0071E3)
)

// -----------------------------------------------------------------------------
// LIGHT APPLE CUPERTINO PALETTE
// -----------------------------------------------------------------------------
val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0071E3),            // Apple Action Blue (Signature Light)
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5F1FF),
    onPrimaryContainer = Color(0xFF001F52),
    secondary = Color(0xFF8E8E93),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF2F2F7),
    onSecondaryContainer = Color(0xFF1C1C1E),
    tertiary = Color(0xFF34C759),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD6F5DE),
    onTertiaryContainer = Color(0xFF053913),
    error = Color(0xFFFF3B30),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF2F2F7),         // Pale Apple Gray (#F2F2F7)
    onBackground = Color(0xFF1D1D1F),       // Apple Near-Black Ink (#1D1D1F)
    surface = Color(0xFFFFFFFF),            // Pure White Card Canvas
    onSurface = Color(0xFF1D1D1F),
    onSurfaceVariant = Color(0xFF6E6E73),   // Apple Secondary Text
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFBFD),
    surfaceContainer = Color(0xFFF5F5F7),   // Pale Apple Gray Card
    surfaceContainerHigh = Color(0xFFE5E5EA),
    surfaceContainerHighest = Color(0xFFD1D1D6),
    surfaceDim = Color(0xFFE5E5EA),
    surfaceBright = Color(0xFFFFFFFF),
    outline = Color(0xFFC7C7CC),
    outlineVariant = Color(0xFFE5E5EA),
    inverseSurface = Color(0xFF1C1C1E),
    inverseOnSurface = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFF2997FF)
)

// -----------------------------------------------------------------------------
// APPLE SYSTEM ACCENTS & VIBRANCY TINTS
// -----------------------------------------------------------------------------
enum class AppleAccent(val displayName: String, val lightColor: Color, val darkColor: Color) {
    BLUE("Cupertino Blue", Color(0xFF0071E3), Color(0xFF2997FF)),
    EMERALD("Cyber Emerald", Color(0xFF047857), Color(0xFF30D158)),
    INDIGO("Electric Indigo", Color(0xFF4338CA), Color(0xFF5E5CE6)),
    TEAL("Skyline Teal", Color(0xFF0284C7), Color(0xFF64D2FF)),
    AMBER("Solar Amber", Color(0xFFD97706), Color(0xFFFFD60A)),
    CORAL("Sunset Coral", Color(0xFFEA580C), Color(0xFFFF9F0A)),
    CRIMSON("System Red", Color(0xFFDC2626), Color(0xFFFF453A)),
    VIOLET("Orchid Violet", Color(0xFF7C3AED), Color(0xFFBF5AF2))
}

val LocalAppleAccent = staticCompositionLocalOf { AppleAccent.BLUE }

// -----------------------------------------------------------------------------
// DYNAMIC THEME PRESETS (APPLE CUPERTINO & MONOCHROMATIC FINISHES)
// Pure Hermes & Apple HIG palette: Pure AMOLED Black, Space Gray, Paper White, Pure White.
// -----------------------------------------------------------------------------
enum class MaterialThemePreset(val displayName: String, val seedColor: Color) {
    APPLE_AMOLED_BLACK("AMOLED True Black", Color(0xFF000000)),
    APPLE_DARK_SLATE("Dark Space Gray", Color(0xFF1C1C1E)),
    APPLE_PAPER_WHITE("Paper White", Color(0xFFF5F5F7)),
    APPLE_PURE_WHITE("Pure White", Color(0xFFFFFFFF)),

    // Aliases to avoid any Deserialization crash if stored in SharedPreferences / config
    APPLE_CUPERTINO("AMOLED True Black", Color(0xFF000000)),
    APPLE_MIDNIGHT("Dark Space Gray", Color(0xFF1C1C1E)),
    APPLE_GRAPHITE("Dark Space Gray", Color(0xFF1C1C1E)),
    PURPLE_IRIS("AMOLED True Black", Color(0xFF000000)),
    COBALT_OCEAN("AMOLED True Black", Color(0xFF000000)),
    EMERALD_MINT("AMOLED True Black", Color(0xFF000000)),
    CRIMSON_EMBER("AMOLED True Black", Color(0xFF000000)),
    MOLTEN_AMBER("AMOLED True Black", Color(0xFF000000))
}

fun getPresetColorScheme(preset: MaterialThemePreset, isDark: Boolean): ColorScheme {
    return when (preset) {
        MaterialThemePreset.APPLE_AMOLED_BLACK,
        MaterialThemePreset.APPLE_CUPERTINO,
        MaterialThemePreset.PURPLE_IRIS,
        MaterialThemePreset.COBALT_OCEAN,
        MaterialThemePreset.EMERALD_MINT,
        MaterialThemePreset.CRIMSON_EMBER,
        MaterialThemePreset.MOLTEN_AMBER -> {
            if (isDark) {
                // Pitch AMOLED Black (#000000)
                DarkColorScheme.copy(
                    background = Color(0xFF000000),
                    surface = Color(0xFF000000),
                    surfaceContainerLowest = Color(0xFF000000),
                    surfaceContainerLow = Color(0xFF101012),
                    surfaceContainer = Color(0xFF1C1C1E),
                    surfaceContainerHigh = Color(0xFF2C2C2E),
                    surfaceContainerHighest = Color(0xFF3A3A3C)
                )
            } else {
                LightColorScheme.copy(
                    background = Color(0xFFFFFFFF),
                    surface = Color(0xFFFFFFFF),
                    surfaceContainer = Color(0xFFF5F5F7)
                )
            }
        }
        MaterialThemePreset.APPLE_DARK_SLATE,
        MaterialThemePreset.APPLE_MIDNIGHT,
        MaterialThemePreset.APPLE_GRAPHITE -> {
            if (isDark) {
                // Space Gray (#121214)
                DarkColorScheme.copy(
                    background = Color(0xFF121214),
                    surface = Color(0xFF121214),
                    surfaceContainerLowest = Color(0xFF0C0C0E),
                    surfaceContainerLow = Color(0xFF18181A),
                    surfaceContainer = Color(0xFF222226),
                    surfaceContainerHigh = Color(0xFF2C2C30),
                    surfaceContainerHighest = Color(0xFF38383E)
                )
            } else {
                LightColorScheme.copy(
                    background = Color(0xFFF2F2F7),
                    surface = Color(0xFFFFFFFF),
                    surfaceContainer = Color(0xFFE5E5EA)
                )
            }
        }
        MaterialThemePreset.APPLE_PAPER_WHITE -> {
            // Paper White (#F7F7F9 / warm editorial clean)
            LightColorScheme.copy(
                background = Color(0xFFF7F7F9),
                surface = Color(0xFFFFFFFF),
                surfaceContainer = Color(0xFFEBEBF0),
                surfaceContainerHigh = Color(0xFFDFDFE6),
                onBackground = Color(0xFF1D1D1F)
            )
        }
        MaterialThemePreset.APPLE_PURE_WHITE -> {
            // Pure White (#FFFFFF / clean stark light)
            LightColorScheme.copy(
                background = Color(0xFFFFFFFF),
                surface = Color(0xFFFFFFFF),
                surfaceContainer = Color(0xFFF2F2F7),
                surfaceContainerHigh = Color(0xFFE5E5EA),
                onBackground = Color(0xFF000000)
            )
        }
    }
}

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
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
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
val LocalThemePreset = staticCompositionLocalOf { MaterialThemePreset.APPLE_CUPERTINO }

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
    val isDark = LocalIsDark.current
    val highlight = if (isDark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.60f)
    val shadow = if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.08f)
    return Brush.verticalGradient(
        colors = listOf(highlight, shadow)
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
    preset: MaterialThemePreset = MaterialThemePreset.APPLE_AMOLED_BLACK,
    accentName: String = "BLUE",
    remoteConfig: TalosThemeConfig? = null,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val activeAccent = AppleAccent.values().find {
        it.name.equals(accentName, ignoreCase = true)
    } ?: AppleAccent.BLUE

    val baseScheme = getPresetColorScheme(preset, isDark)
    val accentColor = if (isDark) activeAccent.darkColor else activeAccent.lightColor
    val targetScheme = baseScheme.copy(
        primary = accentColor,
        onPrimary = if (isDark) Color.Black else Color.White
    )
    val animatedScheme = animatedColorSchemeOf(targetScheme)
    val statusColors = if (isDark) DarkStatusColors else LightStatusColors

    CompositionLocalProvider(
        LocalIsDark provides isDark,
        LocalTalosStatus provides statusColors,
        LocalThemePreset provides preset,
        LocalAppleAccent provides activeAccent,
        LocalTalosMotion provides (remoteConfig?.motion ?: MotionConfig())
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

/**
 * Apple HIG & Hermes Monochromatic Appearance Selector Dialog.
 */
@Composable
fun ThemeSelectionDialog(
    currentPreset: MaterialThemePreset,
    onSelectPreset: (MaterialThemePreset) -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = LocalIsDark.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
        title = {
            Text(
                text = "App Finish & Appearance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.15).sp,
                color = if (isDark) Color.White else Color(0xFF1D1D1F)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Clean monochromatic tones: AMOLED true black, dark space gray, and paper white.",
                    style = MaterialTheme.typography.bodySmall,
                    color = talosTextMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                val visiblePresets = listOf(
                    MaterialThemePreset.APPLE_AMOLED_BLACK,
                    MaterialThemePreset.APPLE_DARK_SLATE,
                    MaterialThemePreset.APPLE_PAPER_WHITE,
                    MaterialThemePreset.APPLE_PURE_WHITE
                )
                visiblePresets.forEach { preset ->
                    val isSelected = when (currentPreset) {
                        MaterialThemePreset.APPLE_AMOLED_BLACK,
                        MaterialThemePreset.APPLE_CUPERTINO,
                        MaterialThemePreset.PURPLE_IRIS,
                        MaterialThemePreset.COBALT_OCEAN,
                        MaterialThemePreset.EMERALD_MINT,
                        MaterialThemePreset.CRIMSON_EMBER,
                        MaterialThemePreset.MOLTEN_AMBER -> preset == MaterialThemePreset.APPLE_AMOLED_BLACK
                        MaterialThemePreset.APPLE_DARK_SLATE,
                        MaterialThemePreset.APPLE_MIDNIGHT,
                        MaterialThemePreset.APPLE_GRAPHITE -> preset == MaterialThemePreset.APPLE_DARK_SLATE
                        MaterialThemePreset.APPLE_PAPER_WHITE -> preset == MaterialThemePreset.APPLE_PAPER_WHITE
                        MaterialThemePreset.APPLE_PURE_WHITE -> preset == MaterialThemePreset.APPLE_PURE_WHITE
                    }
                    PressableSurface(
                        onClick = { onSelectPreset(preset) },
                        shape = RoundedCornerShape(16.dp),
                        pressedShape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF0071E3).copy(alpha = 0.16f) else (if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
                        border = BorderStroke(
                            if (isSelected) 1.2.dp else 0.8.dp,
                            if (isSelected) Color(0xFF2997FF).copy(alpha = 0.6f) else (if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f))
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(preset.seedColor)
                                        .border(1.dp, if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.15f), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = preset.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    letterSpacing = (-0.1).sp,
                                    color = if (isSelected) Color(0xFF2997FF) else (if (isDark) Color.White else Color(0xFF1D1D1F))
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2997FF), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0071E3))
            ) {
                Text("Done", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
