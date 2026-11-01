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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient
import com.sarvesh173.talos.ui.*
import kotlinx.coroutines.launch

private data class SettingsFleetAgent(
    val name: String,
    val role: String,
    val tintColor: Color,
    val icon: ImageVector
)

/**
 * Dedicated Apple HIG Hierarchical Settings Screen.
 * Categorizes settings into distinct sections: Appearance (Look & Feel),
 * Tailscale Connection & Host Node, Multi-Agent Fleet, and About TALOS.
 */
@Composable
fun SettingsScreen(
    client: TalosClient,
    onBack: () -> Unit,
    onNavigateToLookAndFeel: () -> Unit
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
    val accentTint = if (isDark) currentAccent.darkColor else currentAccent.lightColor

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
                    val backInteraction = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = onBack,
                        interactionSource = backInteraction,
                        modifier = Modifier
                            .size(38.dp)
                            .applePressScale(backInteraction, 0.97f)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isDark) Color.White else Color(0xFF1D1D1F),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Settings",
                        fontSize = 20.sp,
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
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // -----------------------------------------------------------------
            // 1. APPEARANCE & LOOK AND FEEL CATEGORY
            // -----------------------------------------------------------------
            Text(
                text = "APPEARANCE & PERSONALITY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            val lookAndFeelInteraction = remember { MutableInteractionSource() }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
                border = BorderStroke(0.8.dp, if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.06f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .applePressScale(lookAndFeelInteraction, 0.97f)
                    .clickable(
                        interactionSource = lookAndFeelInteraction,
                        indication = null
                    ) { onNavigateToLookAndFeel() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            accentTint.copy(alpha = 0.28f),
                                            accentTint.copy(alpha = 0.12f)
                                        )
                                    )
                                )
                                .border(
                                    0.8.dp,
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = if (isDark) 0.35f else 0.45f),
                                            accentTint.copy(alpha = 0.30f)
                                        )
                                    ),
                                    RoundedCornerShape(9.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(1.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                accentTint.copy(alpha = 0.35f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                            Icon(
                                imageVector = TalosSparkle,
                                contentDescription = null,
                                tint = accentTint,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Look & Feel",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.2).sp,
                                color = if (isDark) Color.White else Color(0xFF1D1D1F)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Theme mode, accent tints, canvas depth & typography",
                                fontSize = 11.5.sp,
                                letterSpacing = (-0.15).sp,
                                color = talosTextMuted
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open Look & Feel",
                        tint = talosTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // -----------------------------------------------------------------
            // 2. NETWORK & TAILSCALE CORE TRANSPORT
            // -----------------------------------------------------------------
            Text(
                text = "NETWORK & CORE TRANSPORT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
                border = BorderStroke(0.8.dp, if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.06f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Core Tailscale Address:",
                        color = talosTextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.15).sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = {
                            urlText = it
                            statusMessage = null
                            isSuccess = false
                        },
                        placeholder = { Text("http://100.x.y.z:8010", fontSize = 12.sp, letterSpacing = (-0.15).sp) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, letterSpacing = (-0.15).sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = if (isDark) Color(0xFF141416) else Color(0xFFF7F7F9),
                            unfocusedContainerColor = if (isDark) Color(0xFF141416) else Color(0xFFF7F7F9),
                            focusedBorderColor = accentTint,
                            unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f),
                            focusedTextColor = if (isDark) Color.White else Color(0xFF1D1D1F),
                            unfocusedTextColor = if (isDark) Color.White else Color(0xFF1D1D1F)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Host API Token (talos_sk_...):",
                        color = talosTextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.15).sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = tokenText,
                        onValueChange = {
                            tokenText = it
                            statusMessage = null
                            isSuccess = false
                        },
                        placeholder = { Text("talos_sk_...", fontSize = 12.sp, letterSpacing = (-0.15).sp) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, letterSpacing = (-0.15).sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = if (isDark) Color(0xFF141416) else Color(0xFFF7F7F9),
                            unfocusedContainerColor = if (isDark) Color(0xFF141416) else Color(0xFFF7F7F9),
                            focusedBorderColor = accentTint,
                            unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f),
                            focusedTextColor = if (isDark) Color.White else Color(0xFF1D1D1F),
                            unfocusedTextColor = if (isDark) Color.White else Color(0xFF1D1D1F)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (statusMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSuccess) Color(0xFF30D158).copy(alpha = 0.12f) else Color(0xFFFF453A).copy(alpha = 0.12f),
                            border = BorderStroke(0.8.dp, if (isSuccess) Color(0xFF30D158).copy(alpha = 0.35f) else Color(0xFFFF453A).copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
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
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = (-0.15).sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val verifyInteraction = remember { MutableInteractionSource() }
                    Button(
                        onClick = {
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
                        },
                        enabled = !isVerifying,
                        interactionSource = verifyInteraction,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentTint,
                            disabledContainerColor = accentTint.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .applePressScale(verifyInteraction, 0.97f)
                    ) {
                        Text(
                            text = if (isVerifying) "Verifying Handshake..." else "Verify & Update Handshake",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // -----------------------------------------------------------------
            // 3. MULTI-AGENT COUNCIL FLEET
            // -----------------------------------------------------------------
            Text(
                text = "COUNCIL AGENT FLEET",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
                border = BorderStroke(0.8.dp, if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.06f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val agents = listOf(
                        SettingsFleetAgent("Hermes Core", "Nous Research Agent Engine (Tools & Code)", Color(0xFF2997FF), TalosTerminal),
                        SettingsFleetAgent("OmniRush Council", "Autonomous Background Execution & Model Dispatch", Color(0xFF5E5CE6), TalosHub),
                        SettingsFleetAgent("Alya", "Emotional Anchor & Companion System", Color(0xFFFF9F0A), TalosSparkle)
                    )

                    agents.forEachIndexed { index, agent ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                agent.tintColor.copy(alpha = 0.22f),
                                                agent.tintColor.copy(alpha = 0.10f)
                                            )
                                        )
                                    )
                                    .border(0.8.dp, agent.tintColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = agent.icon,
                                    contentDescription = null,
                                    tint = agent.tintColor,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = agent.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = (-0.2).sp,
                                    color = if (isDark) Color.White else Color(0xFF1D1D1F)
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = agent.role,
                                    fontSize = 11.5.sp,
                                    letterSpacing = (-0.15).sp,
                                    color = talosTextMuted
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
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
                        }
                        if (index < agents.size - 1) {
                            HorizontalDivider(
                                color = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f),
                                modifier = Modifier.padding(start = 46.dp, top = 10.dp, bottom = 10.dp)
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
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
                border = BorderStroke(0.8.dp, if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.06f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Release Version",
                            fontSize = 13.sp,
                            letterSpacing = (-0.15).sp,
                            color = talosTextMuted
                        )
                        Text(
                            text = "v2.10.72 (Apple HIG)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp,
                            color = if (isDark) Color.White else Color(0xFF1D1D1F)
                        )
                    }

                    HorizontalDivider(
                        color = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f),
                        modifier = Modifier.padding(vertical = 10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Design System",
                            fontSize = 13.sp,
                            letterSpacing = (-0.15).sp,
                            color = talosTextMuted
                        )
                        Text(
                            text = "Apple Cupertino & Monochromatic",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp,
                            color = accentTint
                        )
                    }

                    HorizontalDivider(
                        color = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f),
                        modifier = Modifier.padding(vertical = 10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Engine Architecture",
                            fontSize = 13.sp,
                            letterSpacing = (-0.15).sp,
                            color = talosTextMuted
                        )
                        Text(
                            text = "Hermes Agent + Compose M3E",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp,
                            color = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF1D1D1F)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
