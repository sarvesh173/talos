package com.sarvesh173.talos.ui.features

import com.sarvesh173.talos.ui.TalosIcons
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import com.sarvesh173.talos.network.TalosClient
import com.sarvesh173.talos.ui.*
import com.sarvesh173.talos.ui.settings.AudioFormat
import com.sarvesh173.talos.ui.settings.VoiceNotePreferences

/**
 * Dedicated Apple HIG "Chat Preferences" Screen matching official Nous Mobile specifications.
 * Provides granular user controls for Hermes reasoning transparency, device action cards,
 * rich web link previews, in-app browser routing, and post-turn skill/memory activity notifications.
 */
@Composable
fun ChatPreferencesScreen(
    client: TalosClient,
    onBack: () -> Unit
) {
    val isDark = LocalIsDark.current
    val scrollState = rememberScrollState()

    val isThinking by client.isThinkingEnabled.collectAsState()
    val isDeviceActions by client.isDeviceActionsEnabled.collectAsState()
    val isLinkPreviews by client.isLinkPreviewsEnabled.collectAsState()
    val isOpenLinksInApp by client.isOpenLinksInApp.collectAsState()
    val isMemoryUpdates by client.isMemoryUpdatesEnabled.collectAsState()

    val context = LocalContext.current
    var currentFormat by remember { mutableStateOf(VoiceNotePreferences.getFormat(context)) }
    var currentLimit by remember { mutableStateOf(VoiceNotePreferences.getLimitSeconds(context)) }

    // Apple Vibrant Palette
    val applePurple = Color(0xFF5856D6)
    val appleGreen = Color(0xFF34C759)
    val appleBlue = Color(0xFF007AFF)
    val appleOrange = Color(0xFFFF9500)
    val applePink = Color(0xFFAF52DE)

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                border = BorderStroke(
                    0.6.dp,
                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .applePressable(0.94f) { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = TalosIcons.TalosBackArrow,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Chat preferences",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.25).sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // -----------------------------------------------------------------
            // 1. INTELLIGENCE & ACTIONS (Thinking, Device actions, Links)
            // -----------------------------------------------------------------
            Text(
                text = "Intelligence & actions",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.15).sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    0.6.dp,
                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .appleLiquidGlassOptics(
                        isDark = isDark,
                        chromaticGlow = MaterialTheme.colorScheme.primary,
                        cornerRadius = 20.dp
                    )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Thinking
                    ChatPreferenceToggleRow(
                        title = "Thinking",
                        description = "Show what Hermes reasons through before it answers, when the model shares it.",
                        icon = Icons.Default.Psychology,
                        iconTint = applePurple,
                        checked = isThinking,
                        onCheckedChange = { client.setThinkingEnabled(it) },
                        isDark = isDark
                    )

                    HorizontalDivider(
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 56.dp)
                    )

                    // Device actions
                    ChatPreferenceToggleRow(
                        title = "Device actions",
                        description = "Show calendar, health, location and other device requests between messages.",
                        icon = Icons.Default.Devices,
                        iconTint = appleGreen,
                        checked = isDeviceActions,
                        onCheckedChange = { client.setDeviceActionsEnabled(it) },
                        isDark = isDark
                    )

                    HorizontalDivider(
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 56.dp)
                    )

                    // Link previews
                    ChatPreferenceToggleRow(
                        title = "Link previews",
                        description = "Show a card under a reply for each web page it links to.",
                        icon = Icons.Default.Link,
                        iconTint = appleBlue,
                        checked = isLinkPreviews,
                        onCheckedChange = { client.setLinkPreviewsEnabled(it) },
                        isDark = isDark
                    )

                    HorizontalDivider(
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 56.dp)
                    )

                    // Open links in app
                    ChatPreferenceToggleRow(
                        title = "Open links in app",
                        description = "Off opens web pages in your default browser.",
                        icon = Icons.Default.OpenInBrowser,
                        iconTint = appleOrange,
                        checked = isOpenLinksInApp,
                        onCheckedChange = { client.setOpenLinksInApp(it) },
                        isDark = isDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // -----------------------------------------------------------------
            // 2. ACTIVITY (Memory updates)
            // -----------------------------------------------------------------
            Text(
                text = "Activity",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.15).sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    0.6.dp,
                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .appleLiquidGlassOptics(
                        isDark = isDark,
                        chromaticGlow = MaterialTheme.colorScheme.primary,
                        cornerRadius = 20.dp
                    )
            ) {
                ChatPreferenceToggleRow(
                    title = "Memory updates",
                    description = "Show a note when Hermes updates its memory or skills after a turn.",
                    icon = Icons.Default.Memory,
                    iconTint = applePink,
                    checked = isMemoryUpdates,
                    onCheckedChange = { client.setMemoryUpdatesEnabled(it) },
                    isDark = isDark
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // -----------------------------------------------------------------
            // 3. VOICE NOTES (Format, Max length)
            // -----------------------------------------------------------------
            Text(
                text = "Voice notes",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.15).sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    0.6.dp,
                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .appleLiquidGlassOptics(
                        isDark = isDark,
                        chromaticGlow = MaterialTheme.colorScheme.primary,
                        cornerRadius = 20.dp
                    )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    VoiceNoteFormatPickerRow(
                        currentFormat = currentFormat,
                        onFormatChange = { format ->
                            currentFormat = format
                            VoiceNotePreferences.setFormat(context, format)
                        },
                        isDark = isDark
                    )

                    VoiceNoteFormatBlurbRow(format = currentFormat, isDark = isDark)

                    if (currentFormat == AudioFormat.MP3) {
                        VoiceNoteMp3HonestyRow(isDark = isDark)
                    }

                    VoiceNoteDurationRow(
                        currentLimit = currentLimit,
                        onLimitChange = { seconds ->
                            currentLimit = seconds
                            VoiceNotePreferences.setLimitSeconds(context, seconds)
                        },
                        isDark = isDark
                    )

                    if (Build.VERSION.SDK_INT < 29) {
                        VoiceNoteCapabilityRow(isDark = isDark)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Hermes adapts its responses and active skills based on context. Changes apply immediately to active and future chambers.",
                fontSize = 12.sp,
                color = talosTextMuted,
                lineHeight = 16.sp,
                letterSpacing = (-0.1).sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ChatPreferenceToggleRow(
    title: String,
    description: String,
    icon: ImageVector,
    iconTint: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isDark: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                TalosHaptics.sendHaptic(context)
                onCheckedChange(!checked)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF1D1D1F),
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                color = if (isDark) Color.White else Color(0xFF1D1D1F)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = (-0.1).sp,
                color = talosTextMuted
            )
        }

        AppleToggleSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun VoiceNoteFormatPickerRow(
    currentFormat: AudioFormat,
    onFormatChange: (AudioFormat) -> Unit,
    isDark: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val formats = listOf(AudioFormat.M4A, AudioFormat.WEBM, AudioFormat.OGG, AudioFormat.MP3)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            formats.forEach { format ->
                val selected = currentFormat == format
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            else
                                Color.Transparent
                        )
                        .border(
                            if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            else BorderStroke(0.dp, Color.Transparent)
                        )
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            TalosHaptics.sendHaptic(context)
                            onFormatChange(format)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = format.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = (-0.1).sp,
                        color = if (selected) MaterialTheme.colorScheme.primary else talosTextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceNoteFormatBlurbRow(format: AudioFormat, isDark: Boolean) {
    Text(
        text = format.blurb,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = (-0.1).sp,
        color = talosTextMuted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun VoiceNoteMp3HonestyRow(isDark: Boolean) {
    Text(
        text = "Not real MP3 — records Ogg/Opus with an .mp3 name. Some players will refuse it.",
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = (-0.1).sp,
        color = Color(0xFFFF9500),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun VoiceNoteDurationRow(
    currentLimit: Int,
    onLimitChange: (Int) -> Unit,
    isDark: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val minLimit = VoiceNotePreferences.MIN_LIMIT_SECONDS
    val maxLimit = VoiceNotePreferences.MAX_LIMIT_SECONDS
    val step = 15

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Max length",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
            color = if (isDark) Color.White else Color(0xFF1D1D1F)
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = formatDuration(currentLimit),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.2).sp,
            color = if (isDark) Color.White else Color(0xFF1D1D1F)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    TalosHaptics.sendHaptic(context)
                    onLimitChange((currentLimit - step).coerceIn(minLimit, maxLimit))
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Decrease",
                tint = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF1D1D1F),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    TalosHaptics.sendHaptic(context)
                    onLimitChange((currentLimit + step).coerceIn(minLimit, maxLimit))
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Increase",
                tint = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF1D1D1F),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun VoiceNoteCapabilityRow(isDark: Boolean) {
    Text(
        text = "Ogg and MP3 fall back to M4A on this device — Android 10+ needed.",
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = (-0.1).sp,
        color = talosTextMuted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
    )
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return when {
        minutes > 0 && remainingSeconds > 0 -> "$minutes min ${remainingSeconds}s"
        minutes > 0 -> "$minutes min"
        else -> "${remainingSeconds}s"
    }
}
