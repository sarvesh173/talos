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
import kotlin.math.cos
import kotlin.math.sin

/**
 * Advanced Multi-Agent Cybernetic Synapse Constellation (High-Density Material 3)
 *
 * Visual & Engineering Highlights:
 * 1. Intricate Neural Mesh: Cross-agent constellation lattice connecting all nodes
 *    into an organic geometric diamond web with faint harmonic starfield particles.
 * 2. Multi-Strand Curvilinear S-Curves: Fluid multi-conductor fiber-optic ribbons with
 *    parallel secondary harmonics and animated data packet comets anchored via onGloballyPositioned.
 * 3. 100% Authentic Vendor Icons: Dashboard Icons MCP full-color OpenClaw, Codex cyber iris,
 *    Nous Hermes winged monogram, and Claude Code terracotta spark.
 * 4. High-Density Telemetry Core: Concentric rotating compass rings, real-time peer stats,
 *    and animated audio spectrum equalizer capsule.
 */
data class AgentNode(
    val key: String,
    val name: String,
    val role: String,
    val specTag: String,
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
            AgentNode("openclaw", "OpenClaw", "Tools & Automation", "Tools · Auto", emblemByKey("openclaw"), Color(0xFFEA580C)),
            AgentNode("claude", "Claude Code", "Anthropic · Architecture", "Arch · Plan", emblemByKey("claude"), Color(0xFFD97757)),
            AgentNode("hermes", "Hermes Agent", "Nous · Autonomy", "Nous · Exec", emblemByKey("hermes"), Color(0xFF16A34A)),
            AgentNode("codex", "Codex", "OpenAI · Synthesis", "Code · Synth", emblemByKey("codex"), Color(0xFF0D9488))
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

            delay(1900)
            isReceiverSpeaking = true
            receiverReply = scenario.replyText

            delay(2800)
            currentScenarioIndex = (currentScenarioIndex + 1) % scenarios.size
        }
    }

    // Continuous Animation Drivers
    val infiniteTransition = rememberInfiniteTransition(label = "mesh_pulse")

    // Traveling comet packet progress (0 to 1)
    val packetProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "packet_pulse"
    )

    // Rotating celestial compass angle
    val compassRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "compass_rotation"
    )

    // Breathing core aura
    val auraPulse by infiniteTransition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_pulse"
    )

    // 5-bar Equalizer animation
    val eq1 by infiniteTransition.animateFloat(
        initialValue = 4f, targetValue = 18f,
        animationSpec = infiniteRepeatable(tween(360, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq1"
    )
    val eq2 by infiniteTransition.animateFloat(
        initialValue = 16f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq2"
    )
    val eq3 by infiniteTransition.animateFloat(
        initialValue = 6f, targetValue = 22f,
        animationSpec = infiniteRepeatable(tween(300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq3"
    )
    val eq4 by infiniteTransition.animateFloat(
        initialValue = 20f, targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(460, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq4"
    )
    val eq5 by infiniteTransition.animateFloat(
        initialValue = 8f, targetValue = 17f,
        animationSpec = infiniteRepeatable(tween(390, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq5"
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
                            text = "Mesh Online · WireGuard",
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

                Spacer(modifier = Modifier.height(3.dp))

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
                    border = BorderStroke(1.dp, activeColor.copy(alpha = 0.50f)),
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
            // Background Canvas: Neural Mesh Grid, Orbital Compass, and Multi-Strand Synapses
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (centerAnchor == Offset.Zero) return@Canvas

                // 1. Clean Atmosphere (No Neon Bloom or Slop Grids)

                // 3. Rotating Celestial Compass Rings
                val radCompass = Math.toRadians(compassRotation.toDouble())
                val orbitRadius = 78.dp.toPx()

                // Dashed Inner Ring
                drawCircle(
                    color = Color(0xFF6366F1).copy(alpha = 0.25f),
                    radius = orbitRadius,
                    center = centerAnchor,
                    style = Stroke(
                        width = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 14f), compassRotation * 1.5f)
                    )
                )

                // Orbiting satellite pulse node
                val satX = centerAnchor.x + (orbitRadius * cos(radCompass)).toFloat()
                val satY = centerAnchor.y + (orbitRadius * sin(radCompass)).toFloat()
                drawCircle(
                    color = Color(0xFF94A3B8),
                    radius = 3.dp.toPx(),
                    center = Offset(satX, satY)
                )

                // 4. Secondary Cross-Mesh Peer Links (Diamond Constellation Lattice)
                if (tlAnchor != Offset.Zero && trAnchor != Offset.Zero && blAnchor != Offset.Zero && brAnchor != Offset.Zero) {
                    val peerLatticeColor = Color(0xFF334155).copy(alpha = 0.20f)
                    val latticeStroke = Stroke(width = 0.9.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f))
                    // TL -> TR
                    drawLine(peerLatticeColor, tlAnchor, trAnchor, strokeWidth = 0.9.dp.toPx(), pathEffect = latticeStroke.pathEffect)
                    // TR -> BR
                    drawLine(peerLatticeColor, trAnchor, brAnchor, strokeWidth = 0.9.dp.toPx(), pathEffect = latticeStroke.pathEffect)
                    // BR -> BL
                    drawLine(peerLatticeColor, brAnchor, blAnchor, strokeWidth = 0.9.dp.toPx(), pathEffect = latticeStroke.pathEffect)
                    // BL -> TL
                    drawLine(peerLatticeColor, blAnchor, tlAnchor, strokeWidth = 0.9.dp.toPx(), pathEffect = latticeStroke.pathEffect)
                }

                // 5. Multi-Strand Curvilinear S-Curve Conduits
                for ((key, cardPos) in anchorMap) {
                    if (cardPos == Offset.Zero) continue

                    val isBeamActive = (key == activeSender || key == activeReceiver)
                    val nodeAccent = nodes.firstOrNull { it.key == key }?.accent ?: Color(0xFF6366F1)

                    val dx = centerAnchor.x - cardPos.x
                    val dy = centerAnchor.y - cardPos.y

                    // Sweeping Curvilinear S-Curve geometry
                    val ctrl1 = Offset(cardPos.x + dx * 0.72f, cardPos.y + dy * 0.08f)
                    val ctrl2 = Offset(cardPos.x + dx * 0.28f, cardPos.y + dy * 0.92f)

                    val pathPrimary = Path().apply {
                        moveTo(cardPos.x, cardPos.y)
                        cubicTo(ctrl1.x, ctrl1.y, ctrl2.x, ctrl2.y, centerAnchor.x, centerAnchor.y)
                    }

                    // Parallel secondary harmonic data trace (at +3.5dp offset)
                    val pathHarmonic = Path().apply {
                        val offX = if (dx > 0) 3.5.dp.toPx() else -3.5.dp.toPx()
                        val offY = if (dy > 0) -3.5.dp.toPx() else 3.5.dp.toPx()
                        moveTo(cardPos.x + offX, cardPos.y + offY)
                        cubicTo(ctrl1.x + offX, ctrl1.y + offY, ctrl2.x + offX, ctrl2.y + offY, centerAnchor.x + offX, centerAnchor.y + offY)
                    }



                    // Main crisp fiber-optic stream line
                    drawPath(
                        path = pathPrimary,
                        brush = Brush.linearGradient(
                            colors = if (isBeamActive) {
                                listOf(nodeAccent.copy(alpha = 0.90f), Color(0xFF818CF8))
                            } else {
                                listOf(Color(0xFF334155).copy(alpha = 0.40f), Color(0xFF1E293B).copy(alpha = 0.25f))
                            },
                            start = cardPos,
                            end = centerAnchor
                        ),
                        style = Stroke(width = if (isBeamActive) 2.2.dp.toPx() else 1.2.dp.toPx())
                    )

                    // Secondary harmonic wire
                    drawPath(
                        path = pathHarmonic,
                        color = if (isBeamActive) nodeAccent.copy(alpha = 0.35f) else Color(0xFF334155).copy(alpha = 0.15f),
                        style = Stroke(
                            width = 0.8.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), packetProgress * 20f)
                        )
                    )

                    // Luminous Data Packet Pulse
                    if (key == activeSender) {
                        val t = packetProgress
                        val u = 1.0f - t

                        val px = u * u * u * cardPos.x + 3 * u * u * t * ctrl1.x + 3 * u * t * t * ctrl2.x + t * t * t * centerAnchor.x
                        val py = u * u * u * cardPos.y + 3 * u * u * t * ctrl1.y + 3 * u * t * t * ctrl2.y + t * t * t * centerAnchor.y

                        // Clean luminous pearl
                        drawCircle(
                            color = nodeAccent.copy(alpha = 0.50f),
                            radius = 5.dp.toPx(),
                            center = Offset(px, py),
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.8.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                }
            }

            // ==============================================================
            // 1. CENTER TALOS HIGH-DENSITY CORE ENGINE
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
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier
                            .size(82.dp)
                            .shadow(6.dp, RoundedCornerShape(20.dp), spotColor = Color.Black)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "TALOS",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp
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
                                        text = "MESH CORE",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    )
                                }
                                Text(
                                    text = "4 PEERS · 0.8ms",
                                    color = talosTextMuted,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Medium
                                )
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
                            horizontalArrangement = Arrangement.spacedBy(3.5.dp)
                        ) {
                            Box(modifier = Modifier.width(3.dp).height(eq1.dp).clip(CircleShape).background(Color(0xFF818CF8)))
                            Box(modifier = Modifier.width(3.dp).height(eq2.dp).clip(CircleShape).background(Color(0xFF34D399)))
                            Box(modifier = Modifier.width(3.dp).height(eq3.dp).clip(CircleShape).background(Color(0xFF38BDF8)))
                            Box(modifier = Modifier.width(3.dp).height(eq4.dp).clip(CircleShape).background(Color(0xFFF472B6)))
                            Box(modifier = Modifier.width(3.dp).height(eq5.dp).clip(CircleShape).background(Color(0xFFFBBF24)))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Mesh Bus",
                                color = talosTextMuted,
                                fontSize = 9.5.sp,
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
 * Clean Material 3 Agent Card with 30dp Official Vector Drawable and Status Tag
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
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, node.accent.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .widthIn(max = 160.dp)
                        .shadow(8.dp, RoundedCornerShape(12.dp), )
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
                .size(width = 116.dp, height = 104.dp)
                .scale(cardScale)
                .clip(RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { onTap() }
                .shadow(
                    elevation = if (isActive) 4.dp else 0.dp,
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Prominent 44dp icon well with 30dp vector drawable
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(node.accent.copy(alpha = 0.15f))
                        .border(1.dp, node.accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AgentEmblemIcon(emblem = node.emblem, size = 30.dp, tint = null)
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
                        text = if (isActive) "Active" else node.specTag,
                        color = if (isActive) node.accent else talosTextMuted,
                        fontSize = 9.5.sp,
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
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, node.accent.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .widthIn(max = 160.dp)
                        .shadow(8.dp, RoundedCornerShape(12.dp), )
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
