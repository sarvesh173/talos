package com.sarvesh173.talos.ui.features

import com.sarvesh173.talos.ui.TalosIcons
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient
import com.sarvesh173.talos.ui.AppleToggleSwitch
import com.sarvesh173.talos.ui.LocalIsDark
import com.sarvesh173.talos.ui.applePressable
import com.sarvesh173.talos.ui.talosTextMuted

/**
 * Dedicated Apple HIG Security Screen.
 * Provides biometric lock controls, device authentication fallback,
 * and encrypted credential coverage matching Nous Mobile specifications.
 */
@Composable
fun SecurityScreen(
    client: TalosClient,
    onBack: () -> Unit
) {
    val isDark = LocalIsDark.current
    val scrollState = rememberScrollState()
    val requireBiometrics by client.requireBiometrics.collectAsState()

    val appleGreen = Color(0xFF34C759)
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
                            imageVector = TalosIcons.TalosBackArrow,
                            contentDescription = "Back",
                            tint = if (isDark) Color.White else Color(0xFF1D1D1F),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Security",
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
            // 1. BIOMETRICS & APP LOCK (Apple Inset Grouped Card)
            // -----------------------------------------------------------------
            Text(
                text = "BIOMETRICS & APP LOCK",
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
                    // Row 1: Require Biometrics Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF1D1D1F),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Require Biometrics",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.2).sp,
                                color = if (isDark) Color.White else Color(0xFF1D1D1F)
                            )
                        }
                        AppleToggleSwitch(
                            checked = requireBiometrics,
                            onCheckedChange = { client.setRequireBiometrics(it) }
                        )
                    }

                    HorizontalDivider(
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 54.dp)
                    )

                    // Explanatory text inside the Inset Grouped card
                    Text(
                        text = "Asked for when the app opens. Conversations, files and instance credentials stay covered until the device owner is authenticated. The device screen lock always works as a fallback, so a failed scan cannot lock you out.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        letterSpacing = (-0.1).sp,
                        color = talosTextMuted,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // -----------------------------------------------------------------
            // 2. DEVICE CREDENTIAL COVERAGE
            // -----------------------------------------------------------------
            Text(
                text = "CREDENTIAL COVERAGE",
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
                    // Row 1: Instance Tokens & Keys
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF1D1D1F),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Instance Tokens & Keys",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.2).sp,
                            color = if (isDark) Color.White else Color(0xFF1D1D1F),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (requireBiometrics) "Covered" else "Protected",
                            fontSize = 13.sp,
                            color = talosTextMuted,
                            letterSpacing = (-0.15).sp
                        )
                    }

                    HorizontalDivider(
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 54.dp)
                    )

                    // Row 2: Device Screen Lock Fallback
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF1D1D1F),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Screen Lock Fallback",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.2).sp,
                            color = if (isDark) Color.White else Color(0xFF1D1D1F),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Enabled",
                            fontSize = 13.sp,
                            color = Color(0xFF34C759),
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.15).sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
