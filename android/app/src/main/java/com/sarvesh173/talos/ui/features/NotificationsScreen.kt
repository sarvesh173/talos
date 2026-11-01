package com.sarvesh173.talos.ui.features

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient
import com.sarvesh173.talos.ui.AppleToggleSwitch
import com.sarvesh173.talos.ui.LocalIsDark
import com.sarvesh173.talos.ui.applePressable
import com.sarvesh173.talos.ui.talosTextMuted

/**
 * Dedicated Apple HIG Notifications Screen.
 * Provides system notification linkages, category filtering switches,
 * and ongoing working agent monitor controls matching Nous Mobile specifications.
 */
@Composable
fun NotificationsScreen(
    client: TalosClient,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isDark = LocalIsDark.current
    val scrollState = rememberScrollState()

    val notificationsAllowed by client.notificationsAllowed.collectAsState()
    val catAgentReplies by client.categoryAgentReplies.collectAsState()
    val catApprovals by client.categoryApprovals.collectAsState()
    val catAutomations by client.categoryAutomations.collectAsState()
    val catGatewayStatus by client.categoryGatewayStatus.collectAsState()
    val showWorkingAgents by client.showWorkingAgents.collectAsState()

    val appleRed = Color(0xFFFF3B30)
    val appleGreen = Color(0xFF34C759)
    val appleOrange = Color(0xFFFF9500)
    val applePurple = Color(0xFFAF52DE)
    val appleBlue = Color(0xFF007AFF)
    val appleIndigo = Color(0xFF5856D6)
    val appleGray = Color(0xFF8E8E93)

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = if (isDark) Color(0xFF000000).copy(alpha = 0.85f) else Color(0xFFFFFFFF).copy(alpha = 0.85f),
                border = BorderStroke(0.6.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                            .border(BorderStroke(0.8.dp, if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)), RoundedCornerShape(10.dp))
                            .applePressable(0.94f) { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Settings",
                            tint = if (isDark) Color.White else Color(0xFF1D1D1F),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Notifications",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
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
            // 1. ON THIS DEVICE
            // -----------------------------------------------------------------
            Text(
                text = "ON THIS DEVICE",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
                border = BorderStroke(0.6.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Notifications Allowed Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF1D1D1F),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Notifications allowed",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = (-0.2).sp,
                                    color = if (isDark) Color.White else Color(0xFF1D1D1F)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Allow Hermes Agent to show messages and requests from paired instances.",
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = talosTextMuted
                                )
                            }
                        }
                        AppleToggleSwitch(
                            checked = notificationsAllowed,
                            onCheckedChange = { client.setNotificationsAllowed(it) }
                        )
                    }

                    HorizontalDivider(
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 58.dp)
                    )

                    // Link to Android App Notification Settings Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .applePressable(0.97f) {
                                val intent = Intent().apply {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    } else {
                                        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                        data = android.net.Uri.fromParts("package", context.packageName, null)
                                    }
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val fallback = Intent(Settings.ACTION_SETTINGS)
                                    context.startActivity(fallback)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF1D1D1F),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Open Android notification settings",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = (-0.15).sp,
                                color = Color(0xFF0071E3)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = talosTextMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // -----------------------------------------------------------------
            // 2. CATEGORIES
            // -----------------------------------------------------------------
            Text(
                text = "CATEGORIES",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
            )

            data class NotificationCategoryItem(
                val title: String,
                val icon: ImageVector,
                val iconBg: Color,
                val checked: Boolean,
                val onToggle: (Boolean) -> Unit
            )

            val categories = listOf(
                NotificationCategoryItem(
                    title = "Agent Replies",
                    icon = Icons.Default.QuestionAnswer,
                    iconBg = appleGreen,
                    checked = catAgentReplies,
                    onToggle = { client.setCategoryAgentReplies(it) }
                ),
                NotificationCategoryItem(
                    title = "Approvals & Questions",
                    icon = Icons.Default.CheckCircle,
                    iconBg = appleOrange,
                    checked = catApprovals,
                    onToggle = { client.setCategoryApprovals(it) }
                ),
                NotificationCategoryItem(
                    title = "Automations & Deliveries",
                    icon = Icons.Default.Bolt,
                    iconBg = applePurple,
                    checked = catAutomations,
                    onToggle = { client.setCategoryAutomations(it) }
                ),
                NotificationCategoryItem(
                    title = "Gateway Status",
                    icon = Icons.Default.Dns,
                    iconBg = appleBlue,
                    checked = catGatewayStatus,
                    onToggle = { client.setCategoryGatewayStatus(it) }
                )
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
                border = BorderStroke(0.6.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    categories.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF1D1D1F),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = item.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = (-0.15).sp,
                                    color = if (isDark) Color.White else Color(0xFF1D1D1F)
                                )
                            }
                            AppleToggleSwitch(
                                checked = item.checked,
                                onCheckedChange = item.onToggle
                            )
                        }

                        if (index < categories.size - 1) {
                            HorizontalDivider(
                                color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 58.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // -----------------------------------------------------------------
            // 3. WORKING AGENTS
            // -----------------------------------------------------------------
            Text(
                text = "WORKING AGENTS",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
                border = BorderStroke(0.6.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF1D1D1F),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Working Agents",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.2).sp,
                                color = if (isDark) Color.White else Color(0xFF1D1D1F)
                            )
                        }
                        AppleToggleSwitch(
                            checked = showWorkingAgents,
                            onCheckedChange = { client.setShowWorkingAgents(it) }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Show which agents are working, for how long, what they are doing and any question waiting for you, in an ongoing notification.",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = talosTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
