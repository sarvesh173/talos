package com.sarvesh173.talos.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// Dark & OLED Palette
val BgDark = Color(0xFF090D14)
val SurfaceDark = Color(0xFF131926)
val SurfaceBorderDark = Color(0xFF222B3D)
val PrimaryAccent = Color(0xFF38BDF8)
val StatusGreen = Color(0xFF10B981)
val TextPrimaryDark = Color(0xFFF1F5F9)
val TextMutedDark = Color(0xFF94A3B8)

// Light Palette
val BgLight = Color(0xFFF8FAFC)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceBorderLight = Color(0xFFE2E8F0)
val TextPrimaryLight = Color(0xFF0F172A)
val TextMutedLight = Color(0xFF64748B)

val CoralPink = Color(0xFFEC4899)
val OmniBlue = Color(0xFF3B82F6)

val LocalIsDark = compositionLocalOf { true }

// Dynamic theme accessors that adapt immediately to dark/light toggle
val talosBg: Color
    @Composable get() = if (LocalIsDark.current) BgDark else BgLight

val talosSurface: Color
    @Composable get() = if (LocalIsDark.current) SurfaceDark else SurfaceLight

val talosBorder: Color
    @Composable get() = if (LocalIsDark.current) SurfaceBorderDark else SurfaceBorderLight

val talosTextPrimary: Color
    @Composable get() = if (LocalIsDark.current) TextPrimaryDark else TextPrimaryLight

val talosTextMuted: Color
    @Composable get() = if (LocalIsDark.current) TextMutedDark else TextMutedLight

// Dynamic aliases for existing components
val SurfaceBorder: Color
    @Composable get() = talosBorder

val TextPrimary: Color
    @Composable get() = talosTextPrimary

val TextMuted: Color
    @Composable get() = talosTextMuted

val DarkColorScheme = darkColorScheme(
    primary = PrimaryAccent,
    background = BgDark,
    surface = SurfaceDark,
    outline = SurfaceBorderDark,
    onPrimary = Color.White,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextMutedDark
)

val LightColorScheme = lightColorScheme(
    primary = PrimaryAccent,
    background = BgLight,
    surface = SurfaceLight,
    outline = SurfaceBorderLight,
    onPrimary = Color.White,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    onSurfaceVariant = TextMutedLight
)

enum class AppThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

@Composable
fun TalosTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    CompositionLocalProvider(LocalIsDark provides isDark) {
        MaterialTheme(
            colorScheme = if (isDark) DarkColorScheme else LightColorScheme,
            content = content
        )
    }
}
