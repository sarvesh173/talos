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

/**
 * High-Craft Multi-Agent Synapse Constellation Screen (Emil Kowalski / GSAP Quality Standards)
 *
 * Visual Architecture:
 * - Center: High-dimensional TALOS core with concentric orbital resonance rings and live audio equalizer capsule.
 * - 4 Corner Nodes: OpenClaw, Claude Code, Hermes Agent, Codex in zero-gravity float.
 * - Fiber-Optic Synapse Streams: Multi-layer luminous bezier ribbons with gradient comet-tail pulses.
 * - Conversational Banter: Floating glass speech bubbles anchored in the interior quadrant canvas (zero screen clipping).
 * - Tactile feedback: 0.97f press springs, custom easings, and authentic vector emblems from Dashboard Icons.
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
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 800f),
        label = "ButtonScale"
    )

    val handlePrimaryAction = {
        onOpenWizard?.invoke() ?: onGetStarted()
    }

    // 4 Corner CLI Agents
    val nodes = remember {
        listOf(
            AgentNode("openclaw", "OpenClaw", "Tools & Automation", emblemByKey("openclaw"), Color(0xFFF97316)),
            AgentNode("claude", "Claude Code", "Anthropic · Arch", emblemByKey("claude"), Color(0xFFD97757)),
            AgentNode("hermes", "Hermes Agent", "Nous · Autonomy", emblemByKey("hermes"), Color(0xFF22C55E)),
            AgentNode("codex", "Codex", "OpenAI · Code", emblemByKey("codex"), Color(0xFF10A37F))
        )
    }

    // Dynamic Multi-Agent Banter Scenarios
    val scenarios = remember {
        listOf(
            DelegationEvent("hermes", "codex", "Hi @codex, synthesize unit test suite", "Tests generated: 60/60 passing"),
            DelegationEvent("codex", "claude", "Hi @claude, review AST parser refactor", "System design verified: clean"),
            DelegationEvent("claude", "openclaw", "Hi @openclaw, verify browser endpoints", "Endpoints audited: 100% green"),
            DelegationEvent("openclaw", "hermes", "Hi @hermes, consensus quorum proposal", "Quorum approved (4/4 threshold)"),
            DelegationEvent("hermes", "claude", "Hi! Syncing WireGuard peer mesh", "Mesh peer handshaked: 0.8ms"),
            DelegationEvent("codex", "hermes", "Hi @hermes, delegate code review", "Review complete: 0 warnings")
        )
    }

    // Live Scenario State
    var currentScenarioIndex by remember { mutableStateOf(0) }
    var activeSender by remember { mutableStateOf("hermes") }
    var activeReceiver by remember { mutableStateOf("codex") }
    var senderTask by remember { mutableStateOf("Hi @codex, synthesize test suite") }
    var receiverReply by remember { mutableStateOf<String?>(null) }
    var isReceiverSpeaking by remember { mutableStateOf(false) }

    // Banter simulation loop
    LaunchedEffect(Unit) {
        while (true) {
            val scenario = scenarios[currentScenarioIndex]
            activeSender = scenario.fromKey
            activeReceiver = scenario.toKey
            senderTask = scenario.taskText
            receiverReply = null
            isReceiverSpeaking = false

            // Packet travels from sender to center (1.8s)
            delay(1800)

            // Receiver responds with reply
            isReceiverSpeaking = true
            receiverReply = scenario.replyText

            delay(2800)
            currentScenarioIndex = (currentScenarioIndex + 1) % scenarios.size
        }
    }

    // Continuous Animation Drivers
    val infiniteTransition = rememberInfiniteTransition(label = "synapse_engine")

    // Traveling comet packet progress (0 to 1)
    val packetProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "packet_pulse"
    )

    // Orbital ring slow rotation
    val orbitalRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(32000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbital_spin"
    )

    // Core breathing glow aura
    val auraPulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_aura_pulse"
    )

    // Independent Zero-Gravity Floating Oscillators for 4 Corner Cards
    val floatTL by infiniteTransition.animateFloat(
        initialValue = -5f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(3100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "float_tl"
    )
    val floatTR by infiniteTransition.animateFloat(
        initialValue = 4f, targetValue = -4f,
        animationSpec = infiniteRepeatable(tween(2700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "float_tr"
    )
    val floatBL by infiniteTransition.animateFloat(
        initialValue = -4f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "float_bl"
    )
    val floatBR by infiniteTransition.animateFloat(
        initialValue = 5f, targetValue = -5f,
        animationSpec = infiniteRepeatable(tween(3300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "float_br"
    )

    // EQ waveform animated bars
    val eq1 by infiniteTransition.animateFloat(
        initialValue = 5f, targetValue = 18f,
        animationSpec = infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq1"
    )
    val eq2 by infiniteTransition.animateFloat(
        initialValue = 16f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(440, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq2"
    )
    val eq3 by infiniteTransition.animateFloat(
        initialValue = 8f, targetValue = 22f,
        animationSpec = infiniteRepeatable(tween(320, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq3"
    )
    val eq4 by infiniteTransition.animateFloat(
        initialValue = 20f, targetValue = 9f,
        animationSpec = infiniteRepeatable(tween(480, easing = FastOutSlowInEasing), RepeatMode.Reverse),
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
                // Wordmark + Live Beacon
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
                        .size(40.dp)
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
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Solid, high-contrast action CTA
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
                        .height(50.dp)
                        .scale(buttonScale)
                        .semantics {
                            contentDescription = "Enter Chamber and begin multi-agent session"
                        }
                ) {
                    Text(
                        text = "Enter Chamber",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        letterSpacing = 0.3.sp
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

            // Synchronized Core Center and 4 Quadrant Anchors
            val centerPos = Offset(widthPx * 0.50f, heightPx * 0.44f)
            val tlPos = Offset(widthPx * 0.22f, heightPx * 0.16f)
            val trPos = Offset(widthPx * 0.78f, heightPx * 0.16f)
            val blPos = Offset(widthPx * 0.22f, heightPx * 0.72f)
            val brPos = Offset(widthPx * 0.78f, heightPx * 0.72f)

            val anchorMap = mapOf(
                "openclaw" to tlPos,
                "claude" to trPos,
                "hermes" to blPos,
                "codex" to brPos
            )

            // Canvas Layer: Ambient Glow, Orbital Rings, Fiber-Optic Beams, and Travelling Packets
            Canvas(modifier = Modifier.fillMaxSize()) {
                // 1. Deep Galactic Ambient Radial Glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF6366F1).copy(alpha = 0.16f),
                            Color(0xFF4F46E5).copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = centerPos,
                        radius = size.width * 0.65f
                    )
                )

                // 2. Concentric Orbital Resonance Rings
                // Inner dashed orbital ring
                drawCircle(
                    color = Color(0xFF6366F1).copy(alpha = 0.22f),
                    radius = 82.dp.toPx(),
                    center = centerPos,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 14f), orbitalRotation * 2f)
                    )
                )
                // Outer subtle orbital ring
                drawCircle(
                    color = Color(0xFF334155).copy(alpha = 0.25f),
                    radius = 120.dp.toPx(),
                    center = centerPos,
                    style = Stroke(width = 0.8.dp.toPx())
                )

                // 3. Smooth Fiber-Optic Bezier Synapse Beams
                for ((key, nodePos) in anchorMap) {
                    val isBeamActive = (key == activeSender || key == activeReceiver)
                    val nodeAccent = nodes.firstOrNull { it.key == key }?.accent ?: Color(0xFF6366F1)

                    // Calculate graceful inward control points per quadrant
                    val isTop = nodePos.y < centerPos.y
                    val isLeft = nodePos.x < centerPos.x

                    // Control point 1 sweeps horizontally from card
                    val ctrl1 = Offset(
                        nodePos.x + (if (isLeft) 65.dp.toPx() else -65.dp.toPx()),
                        nodePos.y + (if (isTop) 30.dp.toPx() else -30.dp.toPx())
                    )
                    // Control point 2 enters smoothly toward center quadrant
                    val ctrl2 = Offset(
                        centerPos.x + (if (isLeft) -45.dp.toPx() else 45.dp.toPx()),
                        centerPos.y + (if (isTop) -55.dp.toPx() else 55.dp.toPx())
                    )

                    val beamPath = Path().apply {
                        moveTo(nodePos.x, nodePos.y)
                        cubicTo(ctrl1.x, ctrl1.y, ctrl2.x, ctrl2.y, centerPos.x, centerPos.y)
                    }

                    // Ambient soft beam glow
                    if (isBeamActive) {
                        drawPath(
                            path = beamPath,
                            color = nodeAccent.copy(alpha = 0.18f),
                            style = Stroke(width = 5.dp.toPx())
                        )
                    }

                    // Core crisp stream line
                    drawPath(
                        path = beamPath,
                        brush = Brush.linearGradient(
                            colors = if (isBeamActive) {
                                listOf(nodeAccent.copy(alpha = 0.75f), Color(0xFF818CF8).copy(alpha = 0.85f))
                            } else {
                                listOf(Color(0xFF334155).copy(alpha = 0.28f), Color(0xFF1E293B).copy(alpha = 0.20f))
                            },
                            start = nodePos,
                            end = centerPos
                        ),
                        style = Stroke(width = if (isBeamActive) 1.8.dp.toPx() else 1.0.dp.toPx())
                    )

                    // 4. Luminous Traveling Comet-Tail Packet
                    if (key == activeSender) {
                        val t = packetProgress
                        val u = 1.0f - t

                        // Position on cubic bezier
                        val px = u * u * u * nodePos.x + 3 * u * u * t * ctrl1.x + 3 * u * t * t * ctrl2.x + t * t * t * centerPos.x
                        val py = u * u * u * nodePos.y + 3 * u * u * t * ctrl1.y + 3 * u * t * t * ctrl2.y + t * t * t * centerPos.y

                        // Soft comet glow halo
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(nodeAccent.copy(alpha = 0.8f), nodeAccent.copy(alpha = 0.2f), Color.Transparent),
                                center = Offset(px, py),
                                radius = 14.dp.toPx()
                            ),
                            radius = 14.dp.toPx(),
                            center = Offset(px, py)
                        )
                        // Brilliant white energy nucleus
                        drawCircle(
                            color = Color.White,
                            radius = 3.5.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                }
            }

            // ==============================================================
            // 1. CENTER TALOS CORE ENGINE & AUDIO EQUALIZER CAPSULE
            // ==============================================================
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-30).dp)
                    .scale(auraPulse),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Central Frosted Core Hub
                    Surface(
                        color = Color(0xFF131826),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.5.dp, Color(0xFF6366F1).copy(alpha = 0.55f)),
                        modifier = Modifier
                            .size(80.dp)
                            .shadow(18.dp, RoundedCornerShape(22.dp), spotColor = Color(0xFF6366F1))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "TALOS",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.8.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(StatusGreen)
                                    )
                                    Text(
                                        text = "CORE",
                                        color = Color(0xFF818CF8),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Audio Waveform Equalizer Capsule
                    Surface(
                        color = talosSurface,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, talosBorder),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(modifier = Modifier.width(3.dp).height(eq1.dp).clip(CircleShape).background(Color(0xFF818CF8)))
                            Box(modifier = Modifier.width(3.dp).height(eq2.dp).clip(CircleShape).background(Color(0xFF34D399)))
                            Box(modifier = Modifier.width(3.dp).height(eq3.dp).clip(CircleShape).background(Color(0xFF38BDF8)))
                            Box(modifier = Modifier.width(3.dp).height(eq4.dp).clip(CircleShape).background(Color(0xFFF472B6)))
                            Spacer(modifier = Modifier.width(4.dp))
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
            // 2. CORNER AGENT NODES (ZERO-GRAVITY OSCILLATION & NON-CLIPPING BUBBLES)
            // ==============================================================

            // TOP-LEFT: OpenClaw
            AgentNodeWithBubble(
                node = nodes[0],
                floatOffset = floatTL,
                isActiveSender = activeSender == "openclaw",
                isActiveReceiver = activeReceiver == "openclaw" && isReceiverSpeaking,
                bubbleText = if (activeSender == "openclaw") senderTask else (if (activeReceiver == "openclaw" && isReceiverSpeaking) receiverReply else null),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 18.dp, top = 22.dp),
                bubblePlacement = BubblePlacement.BELOW,
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
                floatOffset = floatTR,
                isActiveSender = activeSender == "claude",
                isActiveReceiver = activeReceiver == "claude" && isReceiverSpeaking,
                bubbleText = if (activeSender == "claude") senderTask else (if (activeReceiver == "claude" && isReceiverSpeaking) receiverReply else null),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 18.dp, top = 22.dp),
                bubblePlacement = BubblePlacement.BELOW,
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
                floatOffset = floatBL,
                isActiveSender = activeSender == "hermes",
                isActiveReceiver = activeReceiver == "hermes" && isReceiverSpeaking,
                bubbleText = if (activeSender == "hermes") senderTask else (if (activeReceiver == "hermes" && isReceiverSpeaking) receiverReply else null),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 18.dp, bottom = 28.dp),
                bubblePlacement = BubblePlacement.ABOVE,
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
                floatOffset = floatBR,
                isActiveSender = activeSender == "codex",
                isActiveReceiver = activeReceiver == "codex" && isReceiverSpeaking,
                bubbleText = if (activeSender == "codex") senderTask else (if (activeReceiver == "codex" && isReceiverSpeaking) receiverReply else null),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 28.dp),
                bubblePlacement = BubblePlacement.ABOVE,
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

enum class BubblePlacement {
    ABOVE,
    BELOW
}

/**
 * Tactile Corner Agent Squircle with In-Quadrant Conversational Speech Bubble.
 */
@Composable
private fun AgentNodeWithBubble(
    node: AgentNode,
    floatOffset: Float,
    isActiveSender: Boolean,
    isActiveReceiver: Boolean,
    bubbleText: String?,
    modifier: Modifier = Modifier,
    bubblePlacement: BubblePlacement = BubblePlacement.BELOW,
    alignRight: Boolean = false,
    onTap: () -> Unit
) {
    val isEngaged = isActiveSender || isActiveReceiver

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cardScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.95f
            isEngaged -> 1.05f
            else -> 1.0f
        },
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 600f),
        label = "node_card_scale"
    )

    Column(
        modifier = modifier
            .offset(y = floatOffset.dp)
            .scale(cardScale),
        horizontalAlignment = if (alignRight) Alignment.End else Alignment.Start
    ) {
        // Speech Bubble Placed ABOVE the card (for bottom agents)
        if (bubblePlacement == BubblePlacement.ABOVE) {
            ConversationBubble(
                bubbleText = bubbleText,
                node = node,
                alignRight = alignRight
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Tactile Agent Squircle Card
        Surface(
            color = talosSurface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = if (isEngaged) 1.8.dp else 1.dp,
                color = if (isEngaged) node.accent else talosBorder
            ),
            modifier = Modifier
                .size(width = 112.dp, height = 96.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { onTap() }
                .shadow(
                    elevation = if (isEngaged) 16.dp else 0.dp,
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

        // Speech Bubble Placed BELOW the card (for top agents - zero screen edge clipping)
        if (bubblePlacement == BubblePlacement.BELOW) {
            Spacer(modifier = Modifier.height(6.dp))
            ConversationBubble(
                bubbleText = bubbleText,
                node = node,
                alignRight = alignRight
            )
        }
    }
}

@Composable
private fun ConversationBubble(
    bubbleText: String?,
    node: AgentNode,
    alignRight: Boolean
) {
    AnimatedVisibility(
        visible = !bubbleText.isNullOrBlank(),
        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { 15 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { -10 }
    ) {
        Surface(
            color = Color(0xFF161E2E),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, node.accent.copy(alpha = 0.55f)),
            modifier = Modifier
                .widthIn(max = 165.dp)
                .shadow(10.dp, RoundedCornerShape(12.dp), spotColor = node.accent)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
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
}
