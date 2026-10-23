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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient
import kotlinx.coroutines.delay

/**
 * Benchmark Material 3 Multi-Agent Mesh Screen (Curvilinear Synapse Architecture)
 *
 * Engineering:
 * 1. Curvilinear S-Curves: Sweeping horizontal-to-vertical organic bezier spline
 *    conduits connecting agent cards directly to TALOS core with exact pixel anchors.
 * 2. Complete Context & Dialogue Restored: Hero banner, mesh mission statement,
 *    and non-clipping in-quadrant conversational task dialogue bubbles.
 * 3. 100% Authentic Vector Marks from Dashboard Icons MCP: OpenClaw, Claude Code,
 *    Hermes Agent, Codex, Gemini, Mistral, Meta, DeepSeek, Qwen.
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

    // 4 Corner CLI Agents with authentic vector emblems
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

            delay(1800)
            isReceiverSpeaking = true
            receiverReply = scenario.replyText

            delay(2800)
            currentScenarioIndex = (currentScenarioIndex + 1) % scenarios.size
        }
    }

    // Traveling packet animation (0 to 1)
    val infiniteTransition = rememberInfiniteTransition(label = "mesh_pulse")
    val packetProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "packet_pulse"
    )

    // Breathing core aura
    val auraPulse by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_pulse"
    )

    // EQ waveform bars
    val eq1 by infiniteTransition.animateFloat(
        initialValue = 4f, targetValue = 16f,
        animationSpec = infiniteRepeatable(tween(360, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq1"
    )
    val eq2 by infiniteTransition.animateFloat(
        initialValue = 14f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq2"
    )
    val eq3 by infiniteTransition.animateFloat(
        initialValue = 6f, targetValue = 18f,
        animationSpec = infiniteRepeatable(tween(300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq3"
    )
    val eq4 by infiniteTransition.animateFloat(
        initialValue = 18f, targetValue = 7f,
        animationSpec = infiniteRepeatable(tween(460, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq4"
    )

    // Live exact coordinate anchors measured from layout
    var centerAnchor by remember { mutableStateOf(Offset.Zero) }
    var tlAnchor by remember { mutableStateOf(Offset.Zero) }
    var trAnchor by remember { mutableStateOf(Offset.Zero) }
    var blAnchor by remember { mutableStateOf(Offset.Zero) }
    var brAnchor by remember { mutableStateOf(Offset.Zero) }

    val anchorMap = mapOf(
        "openclaw" to tlAnchor,
        "claude" to trAnchor,
        "hermes" to blAnchor,
        "codex" to brAnchor
    )

    Scaffold(
        containerColor = talosBg,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "TALOS",
                            color = talosTextPrimary,
                            fontSize = 20.sp,
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

                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier
                            .size(38.dp)
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

                Spacer(modifier = Modifier.height(4.dp))

                // Hero Mission Description (Restored Context)
                Text(
                    text = "Your private mesh of always-on CLI agents that execute, reason, and build together.",
                    color = talosTextMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2
                )
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
                // Docked Live Activity Pill
                val activeNode = nodes.firstOrNull { it.key == activeSender }
                val targetNode = nodes.firstOrNull { it.key == activeReceiver }
                val activeColor = activeNode?.accent ?: Color(0xFF6366F1)

                Surface(
                    color = talosSurface,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, activeColor.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(activeColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${activeNode?.name ?: "Agent"} → ${targetNode?.name ?: "Agent"}:",
                            color = activeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = senderTask,
                            color = talosTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

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

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Tap any agent to trigger instant task delegation",
                    color = talosTextMuted,
                    fontSize = 10.5.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Background Canvas: Curvilinear S-Curve Conduits Layered Behind Cards
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (centerAnchor == Offset.Zero) return@Canvas

                // Ambient Radial Glow from Center Core
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF6366F1).copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = centerAnchor,
                        radius = size.width * 0.6f
                    )
                )

                // Draw True Curvilinear S-Curves Connecting Card Centers to TALOS Core
                for ((key, cardPos) in anchorMap) {
                    if (cardPos == Offset.Zero) continue

                    val isBeamActive = (key == activeSender || key == activeReceiver)
                    val nodeAccent = nodes.firstOrNull { it.key == key }?.accent ?: Color(0xFF6366F1)

                    val dx = centerAnchor.x - cardPos.x
                    val dy = centerAnchor.y - cardPos.y

                    // True Curvilinear S-Curve: sweeps horizontally outward, inflects in middle canvas, sweeps vertically into Talos
                    val ctrl1 = Offset(cardPos.x + dx * 0.75f, cardPos.y + dy * 0.10f)
                    val ctrl2 = Offset(cardPos.x + dx * 0.25f, cardPos.y + dy * 0.90f)

                    val path = Path().apply {
                        moveTo(cardPos.x, cardPos.y)
                        cubicTo(ctrl1.x, ctrl1.y, ctrl2.x, ctrl2.y, centerAnchor.x, centerAnchor.y)
                    }

                    // Soft ambient halo
                    if (isBeamActive) {
                        drawPath(
                            path = path,
                            color = nodeAccent.copy(alpha = 0.22f),
                            style = Stroke(width = 5.dp.toPx())
                        )
                    }

                    // Crisp fiber-optic stream line
                    drawPath(
                        path = path,
                        brush = Brush.linearGradient(
                            colors = if (isBeamActive) {
                                listOf(nodeAccent.copy(alpha = 0.90f), Color(0xFF818CF8))
                            } else {
                                listOf(Color(0xFF334155).copy(alpha = 0.35f), Color(0xFF1E293B).copy(alpha = 0.25f))
                            },
                            start = cardPos,
                            end = centerAnchor
                        ),
                        style = Stroke(width = if (isBeamActive) 2.2.dp.toPx() else 1.2.dp.toPx())
                    )

                    // Luminous Data Packet Pulse
                    if (key == activeSender) {
                        val t = packetProgress
                        val u = 1.0f - t

                        val px = u * u * u * cardPos.x + 3 * u * u * t * ctrl1.x + 3 * u * t * t * ctrl2.x + t * t * t * centerAnchor.x
                        val py = u * u * u * cardPos.y + 3 * u * u * t * ctrl1.y + 3 * u * t * t * ctrl2.y + t * t * t * centerAnchor.y

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(nodeAccent.copy(alpha = 0.95f), nodeAccent.copy(alpha = 0.25f), Color.Transparent),
                                center = Offset(px, py),
                                radius = 13.dp.toPx()
                            ),
                            radius = 13.dp.toPx(),
                            center = Offset(px, py)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 3.5.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                }
            }

            // ==============================================================
            // 1. CENTER TALOS CORE ENGINE
            // ==============================================================
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-15).dp)
                    .scale(auraPulse)
                    .onGloballyPositioned { coords ->
                        val p = coords.positionInParent()
                        val s = coords.size
                        centerAnchor = Offset(p.x + s.width / 2f, p.y + s.height / 2f)
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        color = Color(0xFF131826),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.5.dp, Color(0xFF6366F1).copy(alpha = 0.6f)),
                        modifier = Modifier
                            .size(84.dp)
                            .shadow(16.dp, RoundedCornerShape(22.dp), spotColor = Color(0xFF6366F1))
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
                                Spacer(modifier = Modifier.height(3.dp))
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

                    Spacer(modifier = Modifier.height(8.dp))

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
            // 2. CORNER AGENT CARDS WITH IN-QUADRANT DIALOGUE BUBBLES
            // ==============================================================

            // TOP-LEFT: OpenClaw
            AgentNodeWithBubble(
                node = nodes[0],
                isActive = activeSender == "openclaw" || activeReceiver == "openclaw",
                bubbleText = if (activeSender == "openclaw") senderTask else (if (activeReceiver == "openclaw" && isReceiverSpeaking) receiverReply else null),
                bubbleBelow = true,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 18.dp, top = 16.dp)
                    .onGloballyPositioned { coords ->
                        val p = coords.positionInParent()
                        val s = coords.size
                        tlAnchor = Offset(p.x + s.width / 2f, p.y + s.height / 2f)
                    },
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
                isActive = activeSender == "claude" || activeReceiver == "claude",
                bubbleText = if (activeSender == "claude") senderTask else (if (activeReceiver == "claude" && isReceiverSpeaking) receiverReply else null),
                bubbleBelow = true,
                alignRight = true,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 18.dp, top = 16.dp)
                    .onGloballyPositioned { coords ->
                        val p = coords.positionInParent()
                        val s = coords.size
                        trAnchor = Offset(p.x + s.width / 2f, p.y + s.height / 2f)
                    },
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
                isActive = activeSender == "hermes" || activeReceiver == "hermes",
                bubbleText = if (activeSender == "hermes") senderTask else (if (activeReceiver == "hermes" && isReceiverSpeaking) receiverReply else null),
                bubbleBelow = false,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 18.dp, bottom = 22.dp)
                    .onGloballyPositioned { coords ->
                        val p = coords.positionInParent()
                        val s = coords.size
                        blAnchor = Offset(p.x + s.width / 2f, p.y + s.height / 2f)
                    },
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
                isActive = activeSender == "codex" || activeReceiver == "codex",
                bubbleText = if (activeSender == "codex") senderTask else (if (activeReceiver == "codex" && isReceiverSpeaking) receiverReply else null),
                bubbleBelow = false,
                alignRight = true,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 22.dp)
                    .onGloballyPositioned { coords ->
                        val p = coords.positionInParent()
                        val s = coords.size
                        brAnchor = Offset(p.x + s.width / 2f, p.y + s.height / 2f)
                    },
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
 * Clean Material 3 Agent Card with In-Quadrant Dialogue Bubble
 */
@Composable
private fun AgentNodeWithBubble(
    node: AgentNode,
    isActive: Boolean,
    bubbleText: String?,
    bubbleBelow: Boolean,
    modifier: Modifier = Modifier,
    alignRight: Boolean = false,
    onTap: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cardScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.96f
            isActive -> 1.04f
            else -> 1.0f
        },
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 600f),
        label = "card_scale"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = if (alignRight) Alignment.End else Alignment.Start
    ) {
        // Speech Bubble Placed Above Card (for bottom agents)
        if (!bubbleBelow) {
            AnimatedVisibility(
                visible = !bubbleText.isNullOrBlank() && isActive,
                enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { 15 },
                exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { -10 }
            ) {
                Surface(
                    color = Color(0xFF131826),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, node.accent.copy(alpha = 0.55f)),
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
        }

        // Tactile Agent Card
        Surface(
            color = talosSurface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = if (isActive) 1.8.dp else 1.dp,
                color = if (isActive) node.accent else talosBorder
            ),
            modifier = Modifier
                .size(width = 112.dp, height = 98.dp)
                .scale(cardScale)
                .clip(RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { onTap() }
                .shadow(
                    elevation = if (isActive) 14.dp else 0.dp,
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
                        text = if (isActive) "Active" else node.key.uppercase(),
                        color = if (isActive) node.accent else talosTextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Speech Bubble Placed Below Card (for top agents)
        if (bubbleBelow) {
            AnimatedVisibility(
                visible = !bubbleText.isNullOrBlank() && isActive,
                enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -15 },
                exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { 10 }
            ) {
                Surface(
                    color = Color(0xFF131826),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, node.accent.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .padding(top = 6.dp)
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
        }
    }
}
