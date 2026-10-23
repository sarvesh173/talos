package com.sarvesh173.talos.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * TALOS Interactive Multi-Agent Synapse Landing Screen.
 *
 * Implements Sarvesh's orbital multi-agent architecture:
 * - Center: TALOS core hub with breathing resonance aura & dynamic audio equalizer capsule.
 * - 4 Corner Nodes: OpenClaw, Claude Code, Hermes Agent, Codex.
 * - Organic curved cubic bezier synapse beams with streaming energy pulses.
 * - Live agent-to-agent task delegation simulation: agents dynamically emit messages ("Hi",
 *   "Refactoring AST...", "Audit PR #28", "Running tests...") which travel through Talos
 *   to other agents as luminous data packets.
 * - Interactive: Tapping any agent initiates an instant task delegation burst.
 */
data class AgentNode(
    val key: String,
    val name: String,
    val role: String,
    val emblem: AgentEmblem,
    val accent: Color
)

data class DelegationEvent(
    val fromKey: String,
    val toKey: String,
    val taskText: String,
    val replyText: String
)

@Composable
fun LandingScreen(
    client: TalosClient,
    isDark: Boolean = true,
    onToggleTheme: () -> Unit = {},
    onGetStarted: () -> Unit,
    onConnect: ((String, String) -> Unit)? = null,
    onOpenWizard: (() -> Unit)? = null
) {
    val isCurrentDark = LocalIsDark.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
        label = "ButtonScale"
    )

    val handlePrimaryAction = {
        onOpenWizard?.invoke() ?: onGetStarted()
    }

    // Agent Nodes Setup (4 corner agents from Sarvesh's design)
    val nodes = remember {
        listOf(
            AgentNode("openclaw", "OpenClaw", "Tools & Automation", emblemByKey("openclaw"), Color(0xFFF97316)),
            AgentNode("claude", "Claude Code", "Anthropic · Architecture", emblemByKey("claude"), Color(0xFFD97757)),
            AgentNode("hermes", "Hermes Agent", "Nous Research · Autonomy", emblemByKey("hermes"), Color(0xFF22C55E)),
            AgentNode("codex", "Codex", "OpenAI · Synthesis", emblemByKey("codex"), Color(0xFF10A37F))
        )
    }

    // Dynamic Delegation Scenarios pool
    val scenarios = remember {
        listOf(
            DelegationEvent("hermes", "codex", "Hi @codex, synthesize unit test suite", "Tests generated: 60/60 passing"),
            DelegationEvent("codex", "claude", "Hi @claude, review AST parser refactor", "System design verified: clean"),
            DelegationEvent("claude", "openclaw", "Hi @openclaw, verify browser endpoints", "Endpoints audited: 100% green"),
            DelegationEvent("openclaw", "hermes", "Hi @hermes, consensus quorum proposal", "Quorum approved (4/4 threshold)"),
            DelegationEvent("hermes", "claude", "Hi! Syncing WireGuard peer mesh", "Mesh peer handshaked"),
            DelegationEvent("codex", "hermes", "Hi @hermes, delegate code review", "Review complete: 0 warnings")
        )
    }

    // Active delegation animation state
    var currentScenarioIndex by remember { mutableStateOf(0) }
    var activeSender by remember { mutableStateOf("hermes") }
    var activeReceiver by remember { mutableStateOf("codex") }
    var senderTask by remember { mutableStateOf("Hi @codex, synthesize test suite") }
    var receiverReply by remember { mutableStateOf<String?>(null) }
    var isReceiverSpeaking by remember { mutableStateOf(false) }

    // Periodic simulation loop
    LaunchedEffect(Unit) {
        while (true) {
            val scenario = scenarios[currentScenarioIndex]
            activeSender = scenario.fromKey
            activeReceiver = scenario.toKey
            senderTask = scenario.taskText
            receiverReply = null
            isReceiverSpeaking = false

            // Wait for packet to travel from sender -> center -> receiver
            delay(2200)

            // Receiver responds
            isReceiverSpeaking = true
            receiverReply = scenario.replyText

            delay(2800)

            currentScenarioIndex = (currentScenarioIndex + 1) % scenarios.size
        }
    }

    // Beam packet animation
    val infiniteTransition = rememberInfiniteTransition(label = "synapse_engine")
    val packetProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "packet_pulse"
    )

    // Center Core Aura breathing pulse
    val auraPulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_pulse"
    )

    // EQ bars oscillation
    val eq1 by infiniteTransition.animateFloat(
        initialValue = 6f, targetValue = 20f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq1"
    )
    val eq2 by infiniteTransition.animateFloat(
        initialValue = 18f, targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq2"
    )
    val eq3 by infiniteTransition.animateFloat(
        initialValue = 10f, targetValue = 24f,
        animationSpec = infiniteRepeatable(tween(510, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq3"
    )
    val eq4 by infiniteTransition.animateFloat(
        initialValue = 22f, targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq4"
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
                // Brand wordmark + dynamic live badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "TALOS",
                        color = talosTextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(StatusGreen)
                    )
                    Text(
                        text = "Mesh Online",
                        color = talosTextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Theme Mode Switcher
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(talosSurface)
                        .border(1.dp, talosBorder, CircleShape)
                        .semantics {
                            contentDescription = if (isCurrentDark) "Switch to Light Mode" else "Switch to Dark Mode"
                        }
                ) {
                    Icon(
                        imageVector = if (isCurrentDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = null,
                        tint = talosTextMuted,
                        modifier = Modifier.size(18.dp)
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
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Tactile, premium action button
                Button(
                    onClick = handlePrimaryAction,
                    interactionSource = interactionSource,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCurrentDark) Color(0xFFF8FAFC) else Color(0xFF0F172A),
                        contentColor = if (isCurrentDark) Color(0xFF090D14) else Color(0xFFF8FAFC)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .scale(buttonScale)
                        .semantics {
                            contentDescription = "Enter Chamber and begin multi-agent session"
                        }
                ) {
                    Text(
                        text = "Enter Chamber",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        letterSpacing = 0.4.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Tap any agent to trigger instant task delegation",
                    color = talosTextMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()

            // Calculate anchor positions for Center and 4 Corner Nodes
            val centerOffset = Offset(widthPx * 0.50f, heightPx * 0.40f)
            val tlOffset = Offset(widthPx * 0.22f, heightPx * 0.16f)
            val trOffset = Offset(widthPx * 0.78f, heightPx * 0.16f)
            val blOffset = Offset(widthPx * 0.22f, heightPx * 0.64f)
            val brOffset = Offset(widthPx * 0.78f, heightPx * 0.64f)

            val nodeMap = mapOf(
                "openclaw" to tlOffset,
                "claude" to trOffset,
                "hermes" to blOffset,
                "codex" to brOffset
            )

            // Dynamic Synapse Canvas: Draws organic curved streams & flowing energy particles
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Radial center background glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF6366F1).copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        center = centerOffset,
                        radius = size.width * 0.55f
                    )
                )

                // Draw 4 organic curved bezier beams
                for ((key, pos) in nodeMap) {
                    val isBeamActive = (key == activeSender || key == activeReceiver)
                    val beamColor = if (isBeamActive) {
                        nodes.firstOrNull { it.key == key }?.accent?.copy(alpha = 0.55f) ?: Color(0xFF6366F1)
                    } else {
                        Color(0xFF334155).copy(alpha = 0.25f)
                    }

                    val path = Path().apply {
                        moveTo(pos.x, pos.y)
                        // Smooth cubic bezier with organic sway
                        val ctrl1 = Offset(
                            pos.x + (centerOffset.x - pos.x) * 0.5f,
                            pos.y
                        )
                        val ctrl2 = Offset(
                            centerOffset.x,
                            pos.y + (centerOffset.y - pos.y) * 0.5f
                        )
                        cubicTo(ctrl1.x, ctrl1.y, ctrl2.x, ctrl2.y, centerOffset.x, centerOffset.y)
                    }

                    // Base synapse beam
                    drawPath(
                        path = path,
                        color = beamColor,
                        style = Stroke(
                            width = if (isBeamActive) 2.4.dp.toPx() else 1.2.dp.toPx(),
                            pathEffect = if (isBeamActive) PathEffect.dashPathEffect(floatArrayOf(16f, 10f), packetProgress * 26f) else null
                        )
                    )

                    // Active luminous traveling data packet
                    if (key == activeSender) {
                        val t = packetProgress
                        val u = 1.0f - t
                        val ctrl1 = Offset(pos.x + (centerOffset.x - pos.x) * 0.5f, pos.y)
                        val ctrl2 = Offset(centerOffset.x, pos.y + (centerOffset.y - pos.y) * 0.5f)

                        // Cubic Bezier interpolation: B(t) = (1-t)^3 P0 + 3(1-t)^2 t P1 + 3(1-t) t^2 P2 + t^3 P3
                        val packetX = u * u * u * pos.x + 3 * u * u * t * ctrl1.x + 3 * u * t * t * ctrl2.x + t * t * t * centerOffset.x
                        val packetY = u * u * u * pos.y + 3 * u * u * t * ctrl1.y + 3 * u * t * t * ctrl2.y + t * t * t * centerOffset.y

                        // Outer halo
                        drawCircle(
                            color = beamColor.copy(alpha = 0.35f),
                            radius = 9.dp.toPx(),
                            center = Offset(packetX, packetY)
                        )
                        // Inner bright core
                        drawCircle(
                            color = Color.White,
                            radius = 3.5.dp.toPx(),
                            center = Offset(packetX, packetY)
                        )
                    }
                }
            }

            // ==============================================================
            // 1. CENTER HUB: TALOS Core Engine & Waveform Capsule
            // ==============================================================
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (heightPx * 0.40f).dp / 2.75f)
                    .scale(auraPulse),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Pulsing Core Glyph Box
                    Surface(
                        color = talosSurface,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.5.dp, Color(0xFF6366F1).copy(alpha = 0.5f)),
                        modifier = Modifier
                            .size(76.dp)
                            .shadow(16.dp, RoundedCornerShape(20.dp), spotColor = Color(0xFF6366F1))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "TALOS",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Audio Waveform Equalizer Capsule (From Sarvesh's sketch)
                    Surface(
                        color = talosSurface,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, talosBorder),
                        modifier = Modifier
                            .height(26.dp)
                            .padding(horizontal = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(modifier = Modifier.width(3.dp).height(eq1.dp).clip(CircleShape).background(Color(0xFF818CF8)))
                            Box(modifier = Modifier.width(3.dp).height(eq2.dp).clip(CircleShape).background(Color(0xFF818CF8)))
                            Box(modifier = Modifier.width(3.dp).height(eq3.dp).clip(CircleShape).background(Color(0xFF34D399)))
                            Box(modifier = Modifier.width(3.dp).height(eq4.dp).clip(CircleShape).background(Color(0xFF38BDF8)))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Mesh Bus",
                                color = talosTextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // ==============================================================
            // 2. CORNER AGENT NODES + LIVE FLOATING TASK SPEECH CHIPS
            // ==============================================================

            // TOP-LEFT: OpenClaw
            AgentNodeWithBubble(
                node = nodes[0],
                isActiveSender = activeSender == "openclaw",
                isActiveReceiver = activeReceiver == "openclaw" && isReceiverSpeaking,
                bubbleText = if (activeSender == "openclaw") senderTask else receiverReply,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 20.dp),
                onTap = {
                    activeSender = "openclaw"
                    activeReceiver = "hermes"
                    senderTask = "Hi @hermes, delegate automated task!"
                    receiverReply = null
                    isReceiverSpeaking = false
                }
            )

            // TOP-RIGHT: Claude Code
            AgentNodeWithBubble(
                node = nodes[1],
                isActiveSender = activeSender == "claude",
                isActiveReceiver = activeReceiver == "claude" && isReceiverSpeaking,
                bubbleText = if (activeSender == "claude") senderTask else receiverReply,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp, top = 20.dp),
                alignRight = true,
                onTap = {
                    activeSender = "claude"
                    activeReceiver = "codex"
                    senderTask = "Hi @codex, synthesize code module!"
                    receiverReply = null
                    isReceiverSpeaking = false
                }
            )

            // BOTTOM-LEFT: Hermes Agent
            AgentNodeWithBubble(
                node = nodes[2],
                isActiveSender = activeSender == "hermes",
                isActiveReceiver = activeReceiver == "hermes" && isReceiverSpeaking,
                bubbleText = if (activeSender == "hermes") senderTask else receiverReply,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 24.dp),
                onTap = {
                    activeSender = "hermes"
                    activeReceiver = "claude"
                    senderTask = "Hi @claude, start architecture pass!"
                    receiverReply = null
                    isReceiverSpeaking = false
                }
            )

            // BOTTOM-RIGHT: Codex
            AgentNodeWithBubble(
                node = nodes[3],
                isActiveSender = activeSender == "codex",
                isActiveReceiver = activeReceiver == "codex" && isReceiverSpeaking,
                bubbleText = if (activeSender == "codex") senderTask else receiverReply,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 24.dp),
                alignRight = true,
                onTap = {
                    activeSender = "codex"
                    activeReceiver = "openclaw"
                    senderTask = "Hi @openclaw, verify automation tests!"
                    receiverReply = null
                    isReceiverSpeaking = false
                }
            )
        }
    }
}

/**
 * Corner Agent Node Squircle with Live Floating Speech Bubble.
 */
@Composable
private fun AgentNodeWithBubble(
    node: AgentNode,
    isActiveSender: Boolean,
    isActiveReceiver: Boolean,
    bubbleText: String?,
    modifier: Modifier = Modifier,
    alignRight: Boolean = false,
    onTap: () -> Unit
) {
    val isEngaged = isActiveSender || isActiveReceiver

    val glowScale by animateFloatAsState(
        targetValue = if (isEngaged) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "node_glow_scale"
    )

    Column(
        modifier = modifier.scale(glowScale),
        horizontalAlignment = if (alignRight) Alignment.End else Alignment.Start
    ) {
        // Floating animated speech bubble / task capsule
        AnimatedVisibility(
            visible = !bubbleText.isNullOrBlank() && isEngaged,
            enter = fadeIn(tween(180)) + slideInVertically(tween(240)) { -20 },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(200)) { -15 }
        ) {
            Surface(
                color = talosSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, node.accent.copy(alpha = 0.6f)),
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .widthIn(max = 160.dp)
                    .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = node.accent)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(node.accent)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = bubbleText ?: "",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Agent Squircle Card (Clickable to trigger manual delegation)
        Surface(
            color = talosSurface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = if (isEngaged) 1.8.dp else 1.dp,
                color = if (isEngaged) node.accent else talosBorder
            ),
            modifier = Modifier
                .size(width = 110.dp, height = 94.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { onTap() }
                .shadow(
                    elevation = if (isEngaged) 14.dp else 0.dp,
                    shape = RoundedCornerShape(16.dp),
                    spotColor = node.accent
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Official Emblem Icon inside soft brand-tinted well
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(node.accent.copy(alpha = 0.14f))
                        .border(1.dp, node.accent.copy(alpha = 0.40f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AgentEmblemIcon(emblem = node.emblem, size = 22.dp, tint = null)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = node.name,
                        color = talosTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isEngaged) "Active" else node.key.uppercase(),
                        color = if (isEngaged) node.accent else talosTextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
