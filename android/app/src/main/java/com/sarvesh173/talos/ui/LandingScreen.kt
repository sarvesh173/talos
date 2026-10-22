package com.sarvesh173.talos.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient

/**
 * Autonomous Landing Screen for TALOS.
 * Features the 5 CLI agents (Hermes, Claude, OpenClaw, OmniRush, Core)
 * orbiting in zero-gravity with independent M3 sinusoidal oscillators,
 * authentic vector emblems, and tactile action halo.
 */
@Composable
fun LandingScreen(
    client: TalosClient,
    onGetStarted: () -> Unit,
    onConnect: ((String, String) -> Unit)? = null,
    onOpenWizard: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "agent_orbital_drift")

    // Independent M3 sinusoidal oscillators for each CLI agent (non-harmonic periods)
    val floatHermes by infiniteTransition.animateFloat(
        initialValue = -12f, targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hermes_float"
    )
    val floatClaude by infiniteTransition.animateFloat(
        initialValue = 10f, targetValue = -10f,
        animationSpec = infiniteRepeatable(tween(2900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "claude_float"
    )
    val floatOpenClaw by infiniteTransition.animateFloat(
        initialValue = -11f, targetValue = 11f,
        animationSpec = infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "openclaw_float"
    )
    val floatOmniRush by infiniteTransition.animateFloat(
        initialValue = 9f, targetValue = -9f,
        animationSpec = infiniteRepeatable(tween(3100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "omnirush_float"
    )
    val floatCore by infiniteTransition.animateFloat(
        initialValue = -8f, targetValue = 8f,
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
        initialValue = 0.82f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "beacon_pulse"
    )

    // Scale on press for tactile feedback (0.96f scale)
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
            .background(Color(0xFF090D14))
    ) {
        val screenW = maxWidth
        val screenH = maxHeight

        // Ambient radial background glow behind center hero
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1E293B).copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(size.width / 2, size.height * 0.42f),
                    radius = size.width * 0.7f
                )
            )
        }

        // ==============================================================
        // 1. TOP-LEFT: HERMES AGENT (Emerald Green Crest + Winged Badge)
        // ==============================================================
        Box(
            modifier = Modifier
                .offset(x = screenW * 0.08f, y = screenH * 0.07f + floatHermes.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    HermesCreature(
                        modifier = Modifier.size(width = 86.dp, height = 74.dp),
                        color = Color(0xFF22C55E)
                    )
                    AgentEmblemBadge(
                        emblem = emblemByKey("hermes"),
                        modifier = Modifier.align(Alignment.BottomEnd),
                        size = 20.dp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                AgentPillTag(label = "Hermes", accent = Color(0xFF22C55E))
            }
        }

        // ==============================================================
        // 2. TOP-RIGHT: CLAUDE CODE (Terracotta Coral Sphere + Spark)
        // ==============================================================
        Box(
            modifier = Modifier
                .offset(x = screenW * 0.65f, y = screenH * 0.08f + floatClaude.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    ClaudeCreature(
                        modifier = Modifier.size(74.dp),
                        color = Color(0xFFD97757)
                    )
                    AgentEmblemBadge(
                        emblem = emblemByKey("claude"),
                        modifier = Modifier.align(Alignment.BottomStart),
                        size = 20.dp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                AgentPillTag(label = "Claude Code", accent = Color(0xFFD97757))
            }
        }

        // ==============================================================
        // 3. MID-LEFT: OPENCLAW (Electric Orange Sweeping Arc + Claw)
        // ==============================================================
        Box(
            modifier = Modifier
                .offset(x = (-16).dp, y = screenH * 0.33f + floatOpenClaw.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    OpenClawCreature(
                        modifier = Modifier.size(width = 88.dp, height = 94.dp),
                        color = Color(0xFFF97316)
                    )
                    AgentEmblemBadge(
                        emblem = emblemByKey("openclaw"),
                        modifier = Modifier.align(Alignment.TopEnd),
                        size = 20.dp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                AgentPillTag(label = "OpenClaw", accent = Color(0xFFF97316))
            }
        }

        // ==============================================================
        // 4. MID-RIGHT: OMNIRUSH (Speed Polygon + Bolt Badge)
        // ==============================================================
        Box(
            modifier = Modifier
                .offset(x = screenW - 74.dp, y = screenH * 0.31f + floatOmniRush.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    OmniRushCreature(
                        modifier = Modifier.size(76.dp),
                        color = Color(0xFFA855F7)
                    )
                    AgentEmblemBadge(
                        emblem = emblemByKey("omnirush"),
                        modifier = Modifier.align(Alignment.BottomStart),
                        size = 20.dp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                AgentPillTag(label = "OmniRush", accent = Color(0xFFA855F7))
            }
        }

        // ==============================================================
        // 5. BOTTOM-LEFT: TALOS CORE (Cyber Cyan Nucleus Squircle)
        // ==============================================================
        Box(
            modifier = Modifier
                .offset(x = screenW * 0.10f, y = screenH * 0.60f + floatCore.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    CoreSquircleCreature(
                        modifier = Modifier.size(68.dp),
                        color = Color(0xFF38BDF8)
                    )
                    AgentEmblemBadge(
                        emblem = emblemByKey("core"),
                        modifier = Modifier.align(Alignment.BottomEnd),
                        size = 20.dp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                AgentPillTag(label = "Core Engine", accent = Color(0xFF38BDF8))
            }
        }

        // ==============================================================
        // 6. BOTTOM-RIGHT: THE COUNCIL (Indigo Consensus Hub)
        // ==============================================================
        Box(
            modifier = Modifier
                .offset(x = screenW * 0.68f, y = screenH * 0.58f + floatCouncil.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CouncilHubCreature(
                    modifier = Modifier.size(width = 82.dp, height = 76.dp),
                    color = Color(0xFF6366F1)
                )
                Spacer(modifier = Modifier.height(4.dp))
                AgentPillTag(label = "The Council", accent = Color(0xFF818CF8))
            }
        }

        // ==============================================================
        // CENTER HERO SECTION (BALANCED OPTICAL HIERARCHY)
        // ==============================================================
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-24).dp)
                .padding(horizontal = 28.dp)
        ) {
            Text(
                text = "TALOS",
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Architectural Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF38BDF8))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Autonomous Multi-Agent Mesh",
                    color = Color(0xFF38BDF8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Your private mesh of always-on CLI agents\nthat execute, reason, and build together",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp
            )
        }

        // ==============================================================
        // BOTTOM ACTION AREA (ANIMATED BEACON + TACTILE GET STARTED)
        // ==============================================================
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            // Action Vital Sign Halo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF131A26))
                    .border(1.dp, Color(0xFF232E42), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                ThinkingOrb(
                    state = OrbState.WEAVING,
                    size = 28.dp,
                    dotColor = Color.White,
                    secondaryColor = Color(0xFF38BDF8),
                    speedMultiplier = 1.3f
                )
                Spacer(modifier = Modifier.width(10.dp))
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
                    color = Color(0xFFA7F3D0),
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
// HELPER PILL FOR AGENT LABELS (CONCENTRIC RADIUS, PURE VECTOR)
// =====================================================================

@Composable
private fun AgentPillTag(label: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.85f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// =====================================================================
// AGENT-SPECIFIC CANVAS CREATURES (INDIVIDUAL IDENTITIES & FACES)
// =====================================================================

/** Hermes: Emerald Green winged crest body with observant vertical pill eyes. */
@Composable
fun HermesCreature(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Cloud-crest geometry
        drawCircle(color, radius = w * 0.32f, center = Offset(w * 0.30f, h * 0.50f))
        drawCircle(color, radius = w * 0.36f, center = Offset(w * 0.52f, h * 0.38f))
        drawCircle(color, radius = w * 0.30f, center = Offset(w * 0.72f, h * 0.52f))
        drawCircle(color, radius = w * 0.28f, center = Offset(w * 0.44f, h * 0.64f))

        // Vertical observant pill eyes in dark obsidian
        val eyeW = w * 0.08f
        val eyeH = h * 0.26f
        val eyeY = h * 0.38f
        drawRoundRect(
            color = Color(0xFF090D14),
            topLeft = Offset(w * 0.38f, eyeY),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2, eyeW / 2)
        )
        drawRoundRect(
            color = Color(0xFF090D14),
            topLeft = Offset(w * 0.58f, eyeY),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2, eyeW / 2)
        )
    }
}

/** Claude Code: Coral sphere with inquisitive slanted eyes looking toward center. */
@Composable
fun ClaudeCreature(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawCircle(color, radius = w * 0.48f, center = Offset(w * 0.5f, h * 0.5f))

        // Inquisitive slanted eyes
        val eyeW = w * 0.11f
        val eyeH = h * 0.28f
        drawRoundRect(
            color = Color(0xFF090D14),
            topLeft = Offset(w * 0.46f, h * 0.34f),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2, eyeW / 2)
        )
        drawRoundRect(
            color = Color(0xFF090D14),
            topLeft = Offset(w * 0.68f, h * 0.40f),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2, eyeW / 2)
        )
    }
}

/** OpenClaw: Dynamic sweeping arc with sharp curved eyes. */
@Composable
fun OpenClawCreature(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawOval(color, topLeft = Offset(-w * 0.35f, 0f), size = Size(w * 1.35f, h))

        // Dynamic arched eyes peeking inward
        val eyeR = w * 0.12f
        drawCircle(Color(0xFF090D14), radius = eyeR, center = Offset(w * 0.48f, h * 0.42f))
        drawCircle(Color(0xFF090D14), radius = eyeR, center = Offset(w * 0.72f, h * 0.48f))
    }
}

/** OmniRush: Kinetic speed-runner polygon with energetic pill eyes. */
@Composable
fun OmniRushCreature(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h / 2
        val r = w * 0.48f

        val path = Path().apply {
            for (i in 0 until 6) {
                val angle = Math.toRadians((60.0 * i - 30.0)).toFloat()
                val x = cx + r * kotlin.math.cos(angle)
                val y = cy + r * kotlin.math.sin(angle)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(path, color)

        // Wide energetic pill eyes
        val eyeW = w * 0.11f
        val eyeH = h * 0.32f
        drawRoundRect(
            color = Color(0xFF090D14),
            topLeft = Offset(cx - eyeW * 1.5f, cy - eyeH / 2),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2, eyeW / 2)
        )
        drawRoundRect(
            color = Color(0xFF090D14),
            topLeft = Offset(cx + eyeW * 0.5f, cy - eyeH / 2),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2, eyeW / 2)
        )
    }
}

/** Core: Cyber Sky Squircle with concentric orbital eyes. */
@Composable
fun CoreSquircleCreature(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(w * 0.34f, h * 0.34f)
        )
        // Concentric dot eyes
        val eyeR = w * 0.08f
        drawCircle(Color(0xFF090D14), radius = eyeR, center = Offset(w * 0.38f, h * 0.48f))
        drawCircle(Color(0xFF090D14), radius = eyeR, center = Offset(w * 0.64f, h * 0.48f))
        drawCircle(Color.White, radius = eyeR * 0.4f, center = Offset(w * 0.38f, h * 0.48f))
        drawCircle(Color.White, radius = eyeR * 0.4f, center = Offset(w * 0.64f, h * 0.48f))
    }
}

/** The Council: Indigo Consensus Hub with multi-agent dot cluster. */
@Composable
fun CouncilHubCreature(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawCircle(color, radius = w * 0.46f, center = Offset(w * 0.5f, h * 0.5f))

        // Multi-agent consensus dot eyes cluster
        drawCircle(Color(0xFF090D14), radius = w * 0.08f, center = Offset(w * 0.36f, h * 0.46f))
        drawCircle(Color(0xFF090D14), radius = w * 0.08f, center = Offset(w * 0.64f, h * 0.46f))
        drawCircle(Color(0xFF090D14), radius = w * 0.06f, center = Offset(w * 0.50f, h * 0.62f))
    }
}
