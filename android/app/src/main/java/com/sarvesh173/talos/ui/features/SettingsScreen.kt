package com.sarvesh173.talos.ui.features

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient
import com.sarvesh173.talos.ui.*
import kotlinx.coroutines.launch

private data class SettingsFleetAgent(
    val name: String,
    val role: String,
    val icon: ImageVector
)

/**
 * Dedicated Apple HIG Hierarchical Settings Screen.
 * Overhauled to authentic iOS Inset Grouped list specifications:
 * - 16dp horizontal margin, 12dp continuous squircle grouped cards.
 * - 48dp standard row height with vertical centering.
 * - 30x30dp squircle leading icon badges with Apple vibrant background tints:
 *   (Blue for Look & Feel, Green for Network, Purple for Fleet, Gray for About).
 * - Clean 54dp inset dividers between rows.
 * - Trailing chevron indicators (14dp).
 * - Apple spring press animations on all touchable rows.
 */
@Composable
fun SettingsScreen(
    client: TalosClient,
    onBack: () -> Unit,
    onNavigateToLookAndFeel: () -> Unit,
    onNavigateToChatPreferences: () -> Unit = {},
    onNavigateToSecurity: () -> Unit = {},
    onNavigateToCaching: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {}
) {
    val context = LocalContext.current
    val isDark = LocalIsDark.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var urlText by remember { mutableStateOf(client.baseUrl) }
    var tokenText by remember { mutableStateOf(client.apiToken) }
    var isVerifying by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    val currentAccent = LocalAppleAccent.current
    val accentTint = MaterialTheme.colorScheme.primary

    // iOS System Vibrant Palette
    val appleBlue = Color(0xFF007AFF)
    val appleGreen = Color(0xFF34C759)
    val applePurple = Color(0xFFAF52DE)
    val appleGray = Color(0xFF8E8E93)
    val appleRed = Color(0xFFFF3B30)
    val appleTeal = Color(0xFF30B0C7)

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
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                            .border(BorderStroke(0.6.dp, if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)), RoundedCornerShape(10.dp))
                            .applePressable(0.94f) { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isDark) Color.White else Color(0xFF1D1D1F),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Settings",
                        fontSize = 18.sp,
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
            // 1. APPEARANCE & LOOK AND FEEL CATEGORY
            // -----------------------------------------------------------------
            Text(
                text = "APPEARANCE & PERSONALITY",
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .applePressable(0.97f) { onNavigateToLookAndFeel() }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 30x30dp squircle leading icon badge (Blue for Look & Feel)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(appleBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = TalosSparkle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Look & Feel",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp,
                            color = if (isDark) Color.White else Color(0xFF1D1D1F),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Customize",
                            fontSize = 13.sp,
                            color = talosTextMuted,
                            letterSpacing = (-0.15).sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Open Look & Feel",
                            tint = talosTextMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    HorizontalDivider(
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 54.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .applePressable(0.97f) { onNavigateToChatPreferences() }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 30x30dp squircle leading icon badge (Purple for Chat Preferences)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(applePurple),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Chat Preferences",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp,
                            color = if (isDark) Color.White else Color(0xFF1D1D1F),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Configure",
                            fontSize = 13.sp,
                            color = talosTextMuted,
                            letterSpacing = (-0.15).sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Open Chat Preferences",
                            tint = talosTextMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // -----------------------------------------------------------------
            // PREFERENCES & PRIVACY (Notifications, Security, Caching)
            // -----------------------------------------------------------------
            Text(
                text = "PREFERENCES & PRIVACY",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
            )

            val notifAllowedState by client.notificationsAllowed.collectAsState()
            val biometricsState by client.requireBiometrics.collectAsState()

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
                border = BorderStroke(0.6.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Row 1: Notifications
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .applePressable(0.97f) { onNavigateToNotifications() }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(appleRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Notifications",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp,
                            color = if (isDark) Color.White else Color(0xFF1D1D1F),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (notifAllowedState) "On" else "Off",
                            fontSize = 13.sp,
                            color = talosTextMuted,
                            letterSpacing = (-0.15).sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Open Notifications",
                            tint = talosTextMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    HorizontalDivider(
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 54.dp)
                    )

                    // Row 2: Security
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .applePressable(0.97f) { onNavigateToSecurity() }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(appleGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Security",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp,
                            color = if (isDark) Color.White else Color(0xFF1D1D1F),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (biometricsState) "Biometrics" else "Standard",
                            fontSize = 13.sp,
                            color = talosTextMuted,
                            letterSpacing = (-0.15).sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Open Security",
                            tint = talosTextMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    HorizontalDivider(
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 54.dp)
                    )

                    // Row 3: Caching
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .applePressable(0.97f) { onNavigateToCaching() }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(appleTeal),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Caching",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp,
                            color = if (isDark) Color.White else Color(0xFF1D1D1F),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Storage",
                            fontSize = 13.sp,
                            color = talosTextMuted,
                            letterSpacing = (-0.15).sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Open Caching",
                            tint = talosTextMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // -----------------------------------------------------------------
            // 2. NETWORK & TAILSCALE CORE TRANSPORT
            // -----------------------------------------------------------------
            Text(
                text = "NETWORK & CORE TRANSPORT",
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
                    // Row 1: 48dp standard row height with vertical centering & 30x30dp Green badge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 30x30dp squircle leading icon badge (Green for Network)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(appleGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = TalosHub,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tailscale Node",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp,
                            color = if (isDark) Color.White else Color(0xFF1D1D1F),
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = CircleShape,
                            color = (if (isSuccess || client.baseUrl.isNotBlank()) Color(0xFF30D158) else Color(0xFFFF9F0A)).copy(alpha = 0.12f),
                            border = BorderStroke(0.6.dp, (if (isSuccess || client.baseUrl.isNotBlank()) Color(0xFF30D158) else Color(0xFFFF9F0A)).copy(alpha = 0.30f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(if (isSuccess || client.baseUrl.isNotBlank()) Color(0xFF30D158) else Color(0xFFFF9F0A))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isSuccess || client.baseUrl.isNotBlank()) "LINKED" else "PENDING",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = if (isSuccess || client.baseUrl.isNotBlank()) (if (isDark) Color(0xFF30D158) else Color(0xFF248A3D)) else (if (isDark) Color(0xFFFF9F0A) else Color(0xFFC97800))
                                )
                            }
                        }
                    }

                    // Clean 54dp inset divider
                    HorizontalDivider(
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 54.dp)
                    )

                    // Row 2: Host URL (48dp standard row height)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Host",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.2).sp,
                            color = if (isDark) Color.White else Color(0xFF1D1D1F),
                            modifier = Modifier.width(60.dp)
                        )
                        TextField(
                            value = urlText,
                            onValueChange = {
                                urlText = it
                                statusMessage = null
                                isSuccess = false
                            },
                            placeholder = { Text("http://100.x.y.z:8010", fontSize = 14.sp, color = talosTextMuted.copy(alpha = 0.5f), letterSpacing = (-0.15).sp) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, letterSpacing = (-0.15).sp, color = if (isDark) Color.White else Color(0xFF1D1D1F)),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Clean 54dp inset divider
                    HorizontalDivider(
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 54.dp)
                    )

                    // Row 3: Host API Token (48dp standard row height)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Token",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.2).sp,
                            color = if (isDark) Color.White else Color(0xFF1D1D1F),
                            modifier = Modifier.width(60.dp)
                        )
                        TextField(
                            value = tokenText,
                            onValueChange = {
                                tokenText = it
                                statusMessage = null
                                isSuccess = false
                            },
                            placeholder = { Text("talos_sk_...", fontSize = 14.sp, color = talosTextMuted.copy(alpha = 0.5f), letterSpacing = (-0.15).sp) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, letterSpacing = (-0.15).sp, color = if (isDark) Color.White else Color(0xFF1D1D1F)),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Clean 54dp inset divider
                    HorizontalDivider(
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 54.dp)
                    )

                    // Row 4: Verify Touch Action (48dp standard row height, Apple spring press)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .applePressable(0.97f) {
                                if (!isVerifying) {
                                    scope.launch {
                                        isVerifying = true
                                        statusMessage = null
                                        val cleanUrl = urlText.trim().trimEnd('/')
                                        val cleanToken = tokenText.trim()
                                        val (ok, msg) = client.verifyAndConnect(cleanUrl, cleanToken)
                                        isVerifying = false
                                        if (ok) {
                                            isSuccess = true
                                            statusMessage = "Verified & Connected (200 OK)"
                                            client.updateConfig(cleanUrl, cleanToken)
                                        } else {
                                            isSuccess = false
                                            statusMessage = msg
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = appleBlue
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Verifying Handshake...",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = appleBlue
                            )
                        } else {
                            Text(
                                text = "Verify & Update Handshake",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = appleBlue
                            )
                        }
                    }

                    if (statusMessage != null) {
                        HorizontalDivider(
                            color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 54.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isSuccess) Color(0xFF30D158) else Color(0xFFFF453A))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = statusMessage ?: "",
                                color = if (isSuccess) (if (isDark) Color(0xFF30D158) else Color(0xFF248A3D)) else (if (isDark) Color(0xFFFF453A) else Color(0xFFD70015)),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = (-0.15).sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // -----------------------------------------------------------------
            // 3. MULTI-AGENT COUNCIL FLEET
            // -----------------------------------------------------------------
            Text(
                text = "COUNCIL AGENT FLEET",
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
                    val fleetAgents = listOf(
                        SettingsFleetAgent("Hermes Core", "Nous Research Agent Engine", TalosTerminal),
                        SettingsFleetAgent("OmniRush Council", "Autonomous Background Execution", TalosHub),
                        SettingsFleetAgent("Alya", "Companion & Emotional Anchor", TalosSparkle)
                    )

                    fleetAgents.forEachIndexed { index, agent ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .applePressable(0.97f) { /* Apple tactile tap */ }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 30x30dp squircle leading icon badge (Purple for Fleet)
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(applePurple),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = agent.icon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = agent.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = (-0.2).sp,
                                    color = if (isDark) Color.White else Color(0xFF1D1D1F),
                                    maxLines = 1
                                )
                                Text(
                                    text = agent.role,
                                    fontSize = 11.sp,
                                    letterSpacing = (-0.15).sp,
                                    color = talosTextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF30D158).copy(alpha = 0.12f),
                                border = BorderStroke(0.6.dp, Color(0xFF30D158).copy(alpha = 0.30f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF30D158))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "LIVE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                        color = if (isDark) Color(0xFF30D158) else Color(0xFF248A3D)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = talosTextMuted.copy(alpha = 0.5f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        if (index < fleetAgents.size - 1) {
                            HorizontalDivider(
                                color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 54.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // -----------------------------------------------------------------
            // 4. ABOUT TALOS
            // -----------------------------------------------------------------
            Text(
                text = "ABOUT TALOS",
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
                    // Row 1: Release Version (48dp height, 30x30dp Gray badge, 14dp chevron)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .applePressable(0.97f) { /* Tactile tap */ }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 30x30dp squircle leading icon badge (Gray for About)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(appleGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Release Version",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.2).sp,
                            color = if (isDark) Color.White else Color(0xFF1D1D1F)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "v2.10.72 (Apple HIG)",
                            fontSize = 14.sp,
                            color = talosTextMuted,
                            letterSpacing = (-0.15).sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = talosTextMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Clean 54dp inset divider
                    HorizontalDivider(
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 54.dp)
                    )

                    // Row 2: Design System (48dp height, 30x30dp Gray badge, 14dp chevron)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .applePressable(0.97f) { /* Tactile tap */ }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 30x30dp squircle leading icon badge (Gray for About)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(appleGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Design System",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.2).sp,
                            color = if (isDark) Color.White else Color(0xFF1D1D1F)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Apple Cupertino",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accentTint,
                            letterSpacing = (-0.15).sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = talosTextMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Clean 54dp inset divider
                    HorizontalDivider(
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 54.dp)
                    )

                    // Row 3: Engine Architecture (48dp height, 30x30dp Gray badge, 14dp chevron)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .applePressable(0.97f) { /* Tactile tap */ }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 30x30dp squircle leading icon badge (Gray for About)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(appleGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Engine Architecture",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.2).sp,
                            color = if (isDark) Color.White else Color(0xFF1D1D1F)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Hermes Agent + Compose",
                            fontSize = 14.sp,
                            color = talosTextMuted,
                            letterSpacing = (-0.15).sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = talosTextMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
