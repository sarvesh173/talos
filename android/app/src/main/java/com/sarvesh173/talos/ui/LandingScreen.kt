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
 * High-Craft Multi-Agent Constellation Canvas (Apple & Material 3 Expressive)
 *
 * Core Capabilities:
 * 1. 100% Light Mode & Dark Mode Text Legibility: Universal dynamic theme accessors
 *    (talosTextPrimary, talosTextMuted, talosSurface, talosBorder) prevent white-on-white text clipping.
 * 2. Purged Mesh Bus Gimmick: Equalizer and fake bus bars completely removed.
 * 3. Dynamic 5th Agent Spawning: OpenClaw and Claude dynamically recruit OpenCode into the mesh
 *    with smooth spring pop-in, auto-wired S-curve conduits, and live delegation banter.
 * 4. Authentic Vendor Vectors: Verified SVGs from Dashboard Icons MCP for Hermes Agent (#FFBF00 gold),
 *    Codex (>_ prompt #7A9DFF), Claude (#D97757 terracotta), OpenClaw (#EA580C), and OpenCode (#3B82F6).
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
    val replyText: String,
    val triggersSpawn: Boolean = false
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

    // Agent fleet with authentic Dashboard Icons MCP vectors
    val baseNodes = remember {
        listOf(
            AgentNode("openclaw", "OpenClaw", "Tools & Automation", "Tools · Auto", emblemByKey("openclaw"), Color(0xFFEA580C)),
            AgentNode("claude", "Claude Code", "Anthropic · Architecture", "Arch · Plan", emblemByKey("claude"), Color(0xFFD97757)),
            AgentNode("hermes", "Hermes Agent", "Nous · Autonomy", "Nous · Exec", emblemByKey("hermes"), Color(0xFFFFBF00)),
            AgentNode("codex", "Codex", "OpenAI · Synthesis", "Code · Synth", emblemByKey("codex"), Color(0xFF7A9DFF)),
            AgentNode("opencode", "OpenCode", "Open-Source Terminal", "CLI · Exec", emblemByKey("opencode"), Color(0xFF3B82F6))
        )
    }

    var isOpenCodeSpawned by remember { mutableStateOf(false) }

    // Dynamic Multi-Agent Banter Scenarios (Includes dynamic OpenCode recruitment!)
    val scenarios = remember {
        listOf(
            DelegationEvent("hermes", "codex", "Hi @codex, synthesize unit test suite", "Tests generated: 60/60 passing"),
            DelegationEvent("codex", "claude", "Hi @claude, review AST parser refactor", "System design verified: clean"),
            DelegationEvent("claude", "openclaw", "Need another CLI agent to analyze terminal AST.", "Spawning @opencode CLI agent to mesh...", triggersSpawn = true),
            DelegationEvent("opencode", "openclaw", "OpenCode online! AST & terminal tools ready.", "Terminal tools mapped into cluster."),
            DelegationEvent("openclaw", "hermes", "Consensus proposal across 5 active CLI agents.", "Quorum verified (5/5 approved)")
        )
    }

    var currentScenarioIndex by remember { mutableStateOf(0) }
    var activeSender by remember { mutableStateOf("hermes") }
    var activeReceiver by remember { mutableStateOf("codex") }
    var senderTask by remember { mutableStateOf("Hi @codex, synthesize test suite") }
    var receiverReply by remember { mutableStateOf<String?>(null) }
    var isReceiverSpeaking by remember { mutableStateOf(false) }

    // Banter simulation loop with dynamic agent spawning
    LaunchedEffect(Unit) {
        while (true) {
            val scenario = scenarios[currentScenarioIndex]
            activeSender = scenario.fromKey
            activeReceiver = scenario.toKey
            senderTask = scenario.taskText
            receiverReply = null
            isReceiverSpeaking = false

            if (scenario.triggersSpawn) {
                delay(1200)
                isOpenCodeSpawned = true
            }

            delay(1900)
            isReceiverSpeaking = true
            receiverReply = scenario.replyText

            delay(2800)
            currentScenarioIndex = (currentScenarioIndex + 1) % scenarios.size
            if (currentScenarioIndex == 0) {
                // Reset spawn periodically so the user can watch the spawn animation replay!
                isOpenCodeSpawned = false
            }
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

    // Live exact coordinate anchors measured from layout
    var centerAnchor by remember { mutableStateOf(Offset.Zero) }
    var tlAnchor by remember { mutableStateOf(Offset.Zero) }
    var trAnchor by remember { mutableStateOf(Offset.Zero) }
    var blAnchor by remember { mutableStateOf(Offset.Zero) }
    var brAnchor by remember { mutableStateOf(Offset.Zero) }
    var bcAnchor by remember { mutableStateOf(Offset.Zero) }

    val anchorMap = mutableMapOf(
        "openclaw" to tlAnchor,
        "claude" to trAnchor,
        "hermes" to blAnchor,
        "codex" to brAnchor
    )
    if (isOpenCodeSpawned && bcAnchor != Offset.Zero) {
        anchorMap["opencode"] = bcAnchor
    }

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
                                .size(6.dp)
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
                val activeNode = baseNodes.firstOrNull { it.key == activeSender }
                val targetNode = baseNodes.firstOrNull { it.key == activeReceiver }
                val activeColor = activeNode?.accent ?: Color(0xFF6366F1)

                Surface(
                    color = talosSurface,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, talosBorder),
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
                                .size(6.dp)
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
            // Precision Engineering Canvas: Minimal Compass, Diamond Lattice, and S-Curves
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (centerAnchor == Offset.Zero) return@Canvas

                // 1. Subtle Celestial Compass Ring
                val radCompass = Math.toRadians(compassRotation.toDouble())
                val orbitRadius = 70.dp.toPx()

                drawCircle(
                    color = Color(0xFF64748B).copy(alpha = if (isCurrentDark) 0.30f else 0.20f),
                    radius = orbitRadius,
                    center = centerAnchor,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f), compassRotation * 1.2f)
                    )
                )

                // Satellite node
                val satX = centerAnchor.x + (orbitRadius * cos(radCompass)).toFloat()
                val satY = centerAnchor.y + (orbitRadius * sin(radCompass)).toFloat()
                drawCircle(
                    color = Color(0xFF94A3B8),
                    radius = 2.5.dp.toPx(),
                    center = Offset(satX, satY)
                )

                // 2. Secondary Cross-Mesh Peer Lattice
                if (tlAnchor != Offset.Zero && trAnchor != Offset.Zero && blAnchor != Offset.Zero && brAnchor != Offset.Zero) {
                    val peerLatticeColor = Color(0xFF64748B).copy(alpha = if (isCurrentDark) 0.20f else 0.15f)
                    val latticeStroke = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f))
                    drawLine(peerLatticeColor, tlAnchor, trAnchor, strokeWidth = 1.dp.toPx(), pathEffect = latticeStroke.pathEffect)
                    drawLine(peerLatticeColor, trAnchor, brAnchor, strokeWidth = 1.dp.toPx(), pathEffect = latticeStroke.pathEffect)
                    drawLine(peerLatticeColor, brAnchor, blAnchor, strokeWidth = 1.dp.toPx(), pathEffect = latticeStroke.pathEffect)
                    drawLine(peerLatticeColor, blAnchor, tlAnchor, strokeWidth = 1.dp.toPx(), pathEffect = latticeStroke.pathEffect)
                }

                // 3. Crisp Curvilinear S-Curve Conduits
                for ((key, cardPos) in anchorMap) {
                    if (cardPos == Offset.Zero) continue

                    val isBeamActive = (key == activeSender || key == activeReceiver)
                    val nodeAccent = baseNodes.firstOrNull { it.key == key }?.accent ?: Color(0xFF6366F1)

                    val dx = centerAnchor.x - cardPos.x
                    val dy = centerAnchor.y - cardPos.y

                    val ctrl1 = Offset(cardPos.x + dx * 0.72f, cardPos.y + dy * 0.08f)
                    val ctrl2 = Offset(cardPos.x + dx * 0.28f, cardPos.y + dy * 0.92f)

                    val pathPrimary = Path().apply {
                        moveTo(cardPos.x, cardPos.y)
                        cubicTo(ctrl1.x, ctrl1.y, ctrl2.x, ctrl2.y, centerAnchor.x, centerAnchor.y)
                    }

                    // Main conduit line
                    drawPath(
                        path = pathPrimary,
                        brush = Brush.linearGradient(
                            colors = if (isBeamActive) {
                                listOf(nodeAccent.copy(alpha = 0.85f), Color(0xFF64748B))
                            } else {
                                listOf(Color(0xFF64748B).copy(alpha = 0.35f), Color(0xFF475569).copy(alpha = 0.20f))
                            },
                            start = cardPos,
                            end = centerAnchor
                        ),
                        style = Stroke(width = if (isBeamActive) 1.8.dp.toPx() else 1.dp.toPx())
                    )

                    // Luminous Data Packet
                    if (key == activeSender) {
                        val t = packetProgress
                        val u = 1.0f - t

                        val px = u * u * u * cardPos.x + 3 * u * u * t * ctrl1.x + 3 * u * t * t * ctrl2.x + t * t * t * centerAnchor.x
                        val py = u * u * u * cardPos.y + 3 * u * u * t * ctrl1.y + 3 * u * t * t * ctrl2.y + t * t * t * centerAnchor.y

                        drawCircle(
                            color = nodeAccent.copy(alpha = 0.50f),
                            radius = 5.dp.toPx(),
                            center = Offset(px, py),
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                        drawCircle(
                            color = if (isCurrentDark) Color.White else Color(0xFF0F172A),
                            radius = 2.8.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                }
            }

            // ==============================================================
            // 1. CENTER TALOS HARDWARE INSTRUMENT (Unibody & Clean)
            // ==============================================================
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-20).dp)
                    .scale(auraPulse)
                    .onGloballyPositioned { coords ->
                        val p = coords.positionInParent()
                        val s = coords.size
                        centerAnchor = Offset(p.x + s.width / 2f, p.y + s.height / 2f)
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = talosSurface,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.2.dp, talosBorder),
                    modifier = Modifier
                        .size(width = 112.dp, height = 98.dp)
                        .shadow(8.dp, RoundedCornerShape(22.dp), spotColor = Color.Black.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "TALOS",
                            color = talosTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )

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
                                color = talosTextMuted,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }

                        Surface(
                            color = if (isCurrentDark) Color(0xFF000000).copy(alpha = 0.35f) else Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.6.dp, talosBorder),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = if (isOpenCodeSpawned) "5 PEERS · 0.8ms" else "4 PEERS · 0.8ms",
                                color = talosTextMuted,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // ==============================================================
            // 2. CORNER & DYNAMIC AGENT CARDS
            // ==============================================================

            // TOP-LEFT: OpenClaw
            AgentNodeWithBubble(
                node = baseNodes[0],
                isActive = activeSender == "openclaw" || activeReceiver == "openclaw",
                bubbleText = if (activeSender == "openclaw") senderTask else (if (activeReceiver == "openclaw" && isReceiverSpeaking) receiverReply else null),
                bubbleBelow = true,
                isDark = isCurrentDark,
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
                node = baseNodes[1],
                isActive = activeSender == "claude" || activeReceiver == "claude",
                bubbleText = if (activeSender == "claude") senderTask else (if (activeReceiver == "claude" && isReceiverSpeaking) receiverReply else null),
                bubbleBelow = true,
                alignRight = true,
                isDark = isCurrentDark,
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

            // DYNAMIC 5TH AGENT: OpenCode (Recruited by Claude & OpenClaw!)
            AnimatedVisibility(
                visible = isOpenCodeSpawned,
                enter = fadeIn(spring(dampingRatio = 0.75f, stiffness = 400f)) + scaleIn(spring(dampingRatio = 0.75f, stiffness = 400f)),
                exit = fadeOut(tween(200)) + scaleOut(tween(200)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 86.dp)
            ) {
                AgentNodeWithBubble(
                    node = baseNodes[4],
                    isActive = activeSender == "opencode" || activeReceiver == "opencode",
                    bubbleText = if (activeSender == "opencode") senderTask else (if (activeReceiver == "opencode" && isReceiverSpeaking) receiverReply else null),
                    bubbleBelow = false,
                    isDark = isCurrentDark,
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            val p = coords.positionInParent()
                            val s = coords.size
                            bcAnchor = Offset(p.x + s.width / 2f, p.y + s.height / 2f)
                        },
                    onTap = {
                        activeSender = "opencode"
                        activeReceiver = "claude"
                        senderTask = "OpenCode CLI executing deep terminal pass!"
                        receiverReply = null
                        isReceiverSpeaking = false
                    }
                )
            }

            // BOTTOM-LEFT: Hermes Agent
            AgentNodeWithBubble(
                node = baseNodes[2],
                isActive = activeSender == "hermes" || activeReceiver == "hermes",
                bubbleText = if (activeSender == "hermes") senderTask else (if (activeReceiver == "hermes" && isReceiverSpeaking) receiverReply else null),
                bubbleBelow = false,
                isDark = isCurrentDark,
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
                node = baseNodes[3],
                isActive = activeSender == "codex" || activeReceiver == "codex",
                bubbleText = if (activeSender == "codex") senderTask else (if (activeReceiver == "codex" && isReceiverSpeaking) receiverReply else null),
                bubbleBelow = false,
                alignRight = true,
                isDark = isCurrentDark,
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
 * Tactile Material 3 Agent Card with High-Contrast Text for Both Light and Dark Modes
 */
@Composable
private fun AgentNodeWithBubble(
    node: AgentNode,
    isActive: Boolean,
    bubbleText: String?,
    bubbleBelow: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    alignRight: Boolean = false,
    onTap: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cardScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.96f
            isActive -> 1.03f
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
                    color = if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, node.accent.copy(alpha = if (isDark) 0.55f else 0.85f)),
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .widthIn(max = 168.dp)
                        .shadow(4.dp, RoundedCornerShape(12.dp), spotColor = Color.Black.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
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
                            color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
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
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(
                width = if (isActive) 1.5.dp else 1.dp,
                color = if (isActive) node.accent.copy(alpha = 0.85f) else talosBorder
            ),
            modifier = Modifier
                .size(width = 114.dp, height = 100.dp)
                .scale(cardScale)
                .clip(RoundedCornerShape(18.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { onTap() }
                .shadow(
                    elevation = if (isActive) 6.dp else 1.dp,
                    shape = RoundedCornerShape(18.dp),
                    spotColor = Color.Black.copy(alpha = 0.20f)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(7.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 44dp icon well with authentic vector drawable
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(node.accent.copy(alpha = if (isDark) 0.12f else 0.10f))
                        .border(1.dp, node.accent.copy(alpha = if (isDark) 0.30f else 0.40f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AgentEmblemIcon(emblem = node.emblem, size = 28.dp, tint = null)
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
                    color = if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, node.accent.copy(alpha = if (isDark) 0.55f else 0.85f)),
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .widthIn(max = 168.dp)
                        .shadow(4.dp, RoundedCornerShape(12.dp), spotColor = Color.Black.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
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
                            color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
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
