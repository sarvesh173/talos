package com.sarvesh173.talos.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient

/**
 * Enterprise Multi-Agent Mesh Landing Screen.
 * Rebuilt using pure Material Design 3 (M3) architecture.
 * Features official vendor brand marks (Nous Research Hermes, Anthropic Claude,
 * OpenAI Codex, OpenClaw, OmniRush) in structured M3 elevated cards.
 */
@Composable
fun LandingScreen(
    client: TalosClient,
    isDark: Boolean = true,
    onToggleTheme: () -> Unit = {},
    onGetStarted: () -> Unit,
    onConnect: ((String, String) -> Unit)? = null,
    onOpenWizard: (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val beaconPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "beacon_pulse"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 800f),
        label = "btn_press_spring"
    )

    Scaffold(
        containerColor = talosBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // M3 Mini Brand Capsule
                Surface(
                    color = talosSurface,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, talosBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .scale(beaconPulse)
                                .clip(CircleShape)
                                .background(StatusGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "TALOS MESH",
                            color = talosTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Theme Mode Switcher
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(talosSurface)
                        .border(1.dp, talosBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle Theme",
                        tint = talosTextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(talosBg)
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // M3 Live Status Pill
                Surface(
                    color = talosSurface,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, talosBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .scale(beaconPulse)
                                .clip(CircleShape)
                                .background(StatusGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "WireGuard Mesh · Direct Peer-to-Peer",
                            color = PrimaryAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // M3 Primary Action Button
                Button(
                    onClick = onGetStarted,
                    interactionSource = interactionSource,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryAccent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .scale(buttonScale)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Get Started",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Includes Tailscale setup guide & one-tap device token generation",
                    color = talosTextMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // M3 Category Label
            Surface(
                color = PrimaryAccent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PrimaryAccent.copy(alpha = 0.35f))
            ) {
                Text(
                    text = "AUTONOMOUS MULTI-AGENT MESH",
                    color = PrimaryAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Display Title (Clean M3, NO spinning rainbow orb)
            Text(
                text = "TALOS",
                color = talosTextPrimary,
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle Description (Crystal clear, zero overlap)
            Text(
                text = "Your private mesh of always-on CLI agents\nthat execute, reason, and build together",
                color = talosTextMuted,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Fleet Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AGENT FLEET",
                    color = talosTextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "5 Connected",
                    color = StatusGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // M3 Horizontal Agent Fleet Cards (Hermes, Claude, Codex, OpenClaw, OmniRush)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(AgentEmblems) { emblem ->
                    M3AgentCard(emblem = emblem)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Fleet Capabilities Specs in M3 Surface Card
            Surface(
                color = talosSurface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, talosBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Unified Agent Bus Architecture",
                        color = talosTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Hermes, Claude Code, OpenAI Codex, OpenClaw, and OmniRush collaborate seamlessly over private WireGuard tunnels with peer-to-peer event routing, live streaming, and zero public exposure.",
                        color = talosTextMuted,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// =====================================================================
// MATERIAL 3 AGENT CARD (STRUCTURED, MULTI-COLOR OFFICIAL ARTWORK)
// =====================================================================

@Composable
private fun M3AgentCard(
    emblem: AgentEmblem,
    modifier: Modifier = Modifier
) {
    Surface(
        color = talosSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, talosBorder),
        modifier = modifier
            .width(170.dp)
            .height(160.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Official Multi-Color Vector Emblem
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(emblem.accent.copy(alpha = 0.14f))
                        .border(1.dp, emblem.accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AgentEmblemIcon(emblem = emblem, size = 28.dp, tint = null)
                }

                // Active Status Indicator Dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(StatusGreen)
                )
            }

            Column {
                Text(
                    text = emblem.label,
                    color = talosTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = emblem.role,
                    color = talosTextMuted,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 2
                )
            }
        }
    }
}
