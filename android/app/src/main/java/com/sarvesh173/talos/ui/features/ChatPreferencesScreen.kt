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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient
import com.sarvesh173.talos.ui.*

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
                color = if (isDark) Color(0xFF000000).copy(alpha = 0.85f) else Color(0xFFFFFFFF).copy(alpha = 0.85f),
                border = BorderStroke(
                    0.6.dp,
                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                            .border(
                                BorderStroke(
                                    0.6.dp,
                                    if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
                                ),
                                RoundedCornerShape(12.dp)
                            )
                            .applePressable(0.94f) { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = TalosIcons.TalosBackArrow,
                            contentDescription = "Back",
                            tint = if (isDark) Color.White else Color(0xFF1D1D1F),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Chat preferences",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.25).sp,
                        color = if (isDark) Color.White else Color(0xFF1D1D1F)
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
                color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
                border = BorderStroke(
                    0.6.dp,
                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                ),
                modifier = Modifier.fillMaxWidth()
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
                color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
                border = BorderStroke(
                    0.6.dp,
                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                ),
                modifier = Modifier.fillMaxWidth()
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
