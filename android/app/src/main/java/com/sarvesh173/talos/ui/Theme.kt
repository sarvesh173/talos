package com.sarvesh173.talos.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark & OLED Palette
val BgDark = Color(0xFF090D15)
val SurfaceDark = Color(0xFF131926)
val SurfaceBorderDark = Color(0xFF222B3D)
val PrimaryAccent = Color(0xFF6366F1)
val StatusGreen = Color(0xFF10B981)
val TextPrimaryDark = Color(0xFFF1F5F9)
val TextMutedDark = Color(0xFF94A3B8)

// Default / Legacy Aliases for existing components
val SurfaceBorder = SurfaceBorderDark
val TextPrimary = TextPrimaryDark
val TextMuted = TextMutedDark

// Light Palette
val BgLight = Color(0xFFF8FAFC)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceBorderLight = Color(0xFFE2E8F0)
val TextPrimaryLight = Color(0xFF0F172A)
val TextMutedLight = Color(0xFF64748B)

val DarkColorScheme = darkColorScheme(
    primary = PrimaryAccent,
    background = BgDark,
    surface = SurfaceDark,
    onPrimary = Color.White,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark
)

val LightColorScheme = lightColorScheme(
    primary = PrimaryAccent,
    background = BgLight,
    surface = SurfaceLight,
    onPrimary = Color.White,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight
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

    MaterialTheme(
        colorScheme = if (isDark) DarkColorScheme else LightColorScheme,
        content = content
    )
}
