package com.sarvesh173.talos.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgDark = Color(0xFF080C14)
val SurfaceDark = Color(0xFF0F172A)
val SurfaceBorder = Color(0xFF1E293B)
val PrimaryAccent = Color(0xFF6366F1)
val StatusGreen = Color(0xFF10B981)
val TextPrimary = Color(0xFFF1F5F9)
val TextMuted = Color(0xFF94A3B8)
val AlyaPink = Color(0xFFEC4899)
val OmniBlue = Color(0xFF3B82F6)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryAccent,
    background = BgDark,
    surface = SurfaceDark,
    onPrimary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun TalosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
