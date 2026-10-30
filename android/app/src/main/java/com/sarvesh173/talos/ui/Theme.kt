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
// GOOGLE MATERIAL 3 SIGNATURE PURPLE / IRIS PALETTE (Seed: #6750A4)
// Verified contrast: onSurface 14.8:1, onPrimary 7.2:1, onSurfaceVariant 9.2:1
// Pure Google Material Design 3 Signature Theme.
// -----------------------------------------------------------------------------
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),            // Luminous Iris / Lavender
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),   // Deep Royal Violet
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
// LIGHT MATERIAL 3 SIGNATURE PURPLE PALETTE
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
// DYNAMIC MATERIAL UI 3 THEME PRESETS
// -----------------------------------------------------------------------------
enum class MaterialThemePreset(val displayName: String, val seedColor: Color) {
    PURPLE_IRIS("Signature Purple", Color(0xFF6750A4)),
    COBALT_OCEAN("Cobalt Ocean", Color(0xFF1E40AF)),
    EMERALD_MINT("Cyber Emerald", Color(0xFF047857)),
    CRIMSON_EMBER("Coral Rose", Color(0xFFB91C1C)),
    MOLTEN_AMBER("Titanium Amber", Color(0xFFB45309))
}

fun getPresetColorScheme(preset: MaterialThemePreset, isDark: Boolean): ColorScheme {
    return when (preset) {
        MaterialThemePreset.PURPLE_IRIS -> if (isDark) DarkColorScheme else LightColorScheme
        MaterialThemePreset.COBALT_OCEAN -> if (isDark) {
            DarkColorScheme.copy(
                primary = Color(0xFF93C5FD),
                primaryContainer = Color(0xFF1E3A8A),
                onPrimaryContainer = Color(0xFFDBEAFE),
                tertiary = Color(0xFF38BDF8),
                background = Color(0xFF0B1120),
                surface = Color(0xFF0B1120),
                surfaceContainer = Color(0xFF131D31),
                surfaceContainerHigh = Color(0xFF1E293B),
                surfaceContainerHighest = Color(0xFF334155)
            )
        } else {
            LightColorScheme.copy(
                primary = Color(0xFF1E40AF),
                primaryContainer = Color(0xFFDBEAFE),
                tertiary = Color(0xFF0284C7)
            )
        }
        MaterialThemePreset.EMERALD_MINT -> if (isDark) {
            DarkColorScheme.copy(
                primary = Color(0xFF6EE7B7),
                primaryContainer = Color(0xFF064E3B),
                onPrimaryContainer = Color(0xFFD1FAE5),
                tertiary = Color(0xFF2DD4BF),
                background = Color(0xFF09120F),
                surface = Color(0xFF09120F),
                surfaceContainer = Color(0xFF10211B),
                surfaceContainerHigh = Color(0xFF183028),
                surfaceContainerHighest = Color(0xFF244439)
            )
        } else {
            LightColorScheme.copy(
                primary = Color(0xFF047857),
                primaryContainer = Color(0xFFD1FAE5),
                tertiary = Color(0xFF0D9488)
            )
        }
        MaterialThemePreset.CRIMSON_EMBER -> if (isDark) {
            DarkColorScheme.copy(
                primary = Color(0xFFFCA5A5),
                primaryContainer = Color(0xFF7F1D1D),
                onPrimaryContainer = Color(0xFFFFE4E6),
                tertiary = Color(0xFFFB7185),
                background = Color(0xFF140A0D),
                surface = Color(0xFF140A0D),
                surfaceContainer = Color(0xFF241217),
                surfaceContainerHigh = Color(0xFF331920),
                surfaceContainerHighest = Color(0xFF44222B)
            )
        } else {
            LightColorScheme.copy(
                primary = Color(0xFFB91C1C),
                primaryContainer = Color(0xFFFFE4E6),
                tertiary = Color(0xFFE11D48)
            )
        }
        MaterialThemePreset.MOLTEN_AMBER -> if (isDark) {
            DarkColorScheme.copy(
                primary = Color(0xFFFCD34D),
                primaryContainer = Color(0xFF451A03),
                onPrimaryContainer = Color(0xFFFEF3C7),
                tertiary = Color(0xFFF59E0B),
                background = Color(0xFF0E0C09),
                surface = Color(0xFF0E0C09),
                surfaceContainer = Color(0xFF1A1610),
                surfaceContainerHigh = Color(0xFF262118),
                surfaceContainerHighest = Color(0xFF383124)
            )
        } else {
            LightColorScheme.copy(
                primary = Color(0xFFB45309),
                primaryContainer = Color(0xFFFEF3C7),
                tertiary = Color(0xFFD97706)
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
val LocalThemePreset = staticCompositionLocalOf { MaterialThemePreset.PURPLE_IRIS }

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
    preset: MaterialThemePreset = MaterialThemePreset.PURPLE_IRIS,
    remoteConfig: TalosThemeConfig? = null,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    // User's chosen preset is the single source of truth.
    // Built-in presets (Signature Purple default, Cobalt, Emerald, etc.)
    // will never be hijacked by stale legacy cache.
    val targetScheme = getPresetColorScheme(preset, isDark)
    val animatedScheme = animatedColorSchemeOf(targetScheme)
    val statusColors = if (isDark) DarkStatusColors else LightStatusColors

    CompositionLocalProvider(
        LocalIsDark provides isDark,
        LocalTalosStatus provides statusColors,
        LocalThemePreset provides preset
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
 * Material UI 3 Interactive Theme Preset Selector Dialog.
 */
@Composable
fun ThemeSelectionDialog(
    currentPreset: MaterialThemePreset,
    onSelectPreset: (MaterialThemePreset) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        title = {
            Text("Material UI 3 Themes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Personalize your Material You system palette. Updates all surface containers, buttons, and specular highlights live.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                MaterialThemePreset.values().forEach { preset ->
                    val isSelected = preset == currentPreset
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPreset(preset) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(preset.seedColor)
                                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = preset.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Done", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    )
}
