package com.sarvesh173.talos.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient

/**
 * Enterprise Multi-Agent Mesh Landing Screen.
 * Built with Apple HIG Liquid Glass and Material 3 Expressive motion physics.
 * Features official CLI agent brand marks in zero-gravity orbital suspension.
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
    val infiniteTransition = rememberInfiniteTransition(label = "mesh_orbital_drift")

    // Independent non-harmonic M3 sinusoidal oscillators
    val floatHermes by infiniteTransition.animateFloat(
        initialValue = -10f, targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hermes_float"
    )
    val floatClaude by infiniteTransition.animateFloat(
        initialValue = 9f, targetValue = -9f,
        animationSpec = infiniteRepeatable(tween(2900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "claude_float"
    )
    val floatOpenClaw by infiniteTransition.animateFloat(
        initialValue = -11f, targetValue = 11f,
        animationSpec = infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "openclaw_float"
    )
    val floatOmniRush by infiniteTransition.animateFloat(
        initialValue = 8f, targetValue = -8f,
        animationSpec = infiniteRepeatable(tween(3100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "omnirush_float"
    )
    val floatCore by infiniteTransition.animateFloat(
        initialValue = -9f, targetValue = 9f,
        animationSpec = infiniteRepeatable(tween(3500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "core_float"
    )
    val floatCouncil by infiniteTransition.animateFloat(
        initialValue = 7f, targetValue = -7f,
        animationSpec = infiniteRepeatable(tween(3800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "council_float"
    )

    // Breathing pulse for live mesh status badge
    val beaconPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "beacon_pulse"
    )

    // Tactile Apple scale on press (0.96f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 800f),
        label = "btn_press_spring"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(talosBg)
    ) {
        val screenW = maxWidth
        val screenH = maxHeight
        val glowBorder = talosBorder

        // Ambient radial background glow behind center hero
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowBorder.copy(alpha = 0.40f), Color.Transparent),
                    center = Offset(size.width / 2, size.height * 0.42f),
                    radius = size.width * 0.75f
                )
            )
        }

        // Top Right Theme Mode Toggle Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onToggleTheme) {
                Icon(
                    imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Toggle Dark/Light Mode",
                    tint = talosTextMuted
                )
            }
        }

        // ==============================================================
        // 1. TOP-LEFT: HERMES AGENT (Nous Research / Winged Mark)
        // ==============================================================
        Box(
            modifier = Modifier
                .offset(x = screenW * 0.08f, y = screenH * 0.07f + floatHermes.dp)
        ) {
            AgentCardNode(
                emblemKey = "hermes",
                name = "Hermes Agent",
                accentColor = Color(0xFF22C55E)
            )
        }

        // ==============================================================
        // 2. TOP-RIGHT: CLAUDE CODE (Official Anthropic Spark)
        // ==============================================================
        Box(
            modifier = Modifier
                .offset(x = screenW * 0.62f, y = screenH * 0.08f + floatClaude.dp)
        ) {
            AgentCardNode(
                emblemKey = "claude",
                name = "Claude Code",
                accentColor = Color(0xFFD97757)
            )
        }

        // ==============================================================
        // 3. MID-LEFT: OPENCLAW (Official Claw Mark)
        // ==============================================================
        Box(
            modifier = Modifier
                .offset(x = (-6).dp, y = screenH * 0.33f + floatOpenClaw.dp)
        ) {
            AgentCardNode(
                emblemKey = "openclaw",
                name = "OpenClaw",
                accentColor = Color(0xFFF97316)
            )
        }

        // ==============================================================
        // 4. MID-RIGHT: OMNIRUSH (Speed Bolt Mark)
        // ==============================================================
        Box(
            modifier = Modifier
                .offset(x = screenW - 88.dp, y = screenH * 0.31f + floatOmniRush.dp)
        ) {
            AgentCardNode(
                emblemKey = "omnirush",
                name = "OmniRush",
                accentColor = Color(0xFFA855F7)
            )
        }

        // ==============================================================
        // 5. BOTTOM-LEFT: TALOS CORE (Cyber Nucleus Ring Mark)
        // ==============================================================
        Box(
            modifier = Modifier
                .offset(x = screenW * 0.08f, y = screenH * 0.58f + floatCore.dp)
        ) {
            AgentCardNode(
                emblemKey = "core",
                name = "Core Engine",
                accentColor = Color(0xFF38BDF8)
            )
        }

        // ==============================================================
        // 6. BOTTOM-RIGHT: THE COUNCIL (Consensus Chamber Hub)
        // ==============================================================
        Box(
            modifier = Modifier
                .offset(x = screenW * 0.64f, y = screenH * 0.56f + floatCouncil.dp)
        ) {
            AgentCardNode(
                emblemKey = "core",
                name = "The Council",
                accentColor = Color(0xFF818CF8)
            )
        }

        // ==============================================================
        // CENTER HERO SECTION (APPLE INTELLIGENCE LUMINOUS ORB + TITLE)
        // ==============================================================
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-20).dp)
                .padding(horizontal = 24.dp)
        ) {
            // Luminous Chromatic Orb at Center Hero
            ThinkingOrb(
                state = OrbState.WEAVING,
                size = 56.dp,
                dotColor = Color.White,
                secondaryColor = Color(0xFF38BDF8),
                speedMultiplier = 1.1f
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "TALOS",
                color = talosTextPrimary,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Glass Status Capsule
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(talosSurface)
                    .border(1.dp, PrimaryAccent.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(PrimaryAccent)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Autonomous Multi-Agent Mesh",
                    color = PrimaryAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Your private mesh of always-on CLI agents\nthat execute, reason, and build together",
                color = talosTextMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp
            )
        }

        // ==============================================================
        // BOTTOM ACTION AREA (VITAL SIGN HALO + TACTILE GET STARTED)
        // ==============================================================
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            // Live Mesh Telemetry Capsule
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(talosSurface)
                    .border(1.dp, talosBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .scale(beaconPulse)
                        .clip(CircleShape)
                        .background(Color(0xFF22C55E))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "WireGuard Mesh · Direct Peer-to-Peer",
                    color = PrimaryAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Full-Width White Pill Button with Spring Scale Feedback
            Button(
                onClick = onGetStarted,
                interactionSource = interactionSource,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF090D14)
                ),
                shape = RoundedCornerShape(32.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .scale(buttonScale)
            ) {
                Text(
                    text = "Get Started",
                    color = Color(0xFF090D14),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Includes Tailscale setup guide & one-tap device token generation",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// =====================================================================
// APPLE LIQUID GLASS AGENT CARD NODE (ZERO CARTOONS, PURE ENTERPRISE)
// =====================================================================

@Composable
private fun AgentCardNode(
    emblemKey: String,
    name: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val emblem = emblemByKey(emblemKey)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(talosSurface.copy(alpha = 0.90f))
                .border(
                    BorderStroke(
                        1.2.dp,
                        Brush.verticalGradient(
                            listOf(
                                accentColor.copy(alpha = 0.80f),
                                talosBorder.copy(alpha = 0.35f)
                            )
                        )
                    ),
                    RoundedCornerShape(22.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // Ambient inner radial glow
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accentColor.copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(size.width / 2, size.height / 2),
                        radius = size.width * 0.65f
                    )
                )
            }
            // Official Brand Vector Emblem (Full Multi-Color Official Artwork)
            AgentEmblemIcon(emblem = emblem, size = 32.dp, tint = null)
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Glass Name Tag
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(talosSurface.copy(alpha = 0.92f))
                .border(1.dp, accentColor.copy(alpha = 0.40f), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = name,
                color = talosTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp
            )
        }
    }
}
