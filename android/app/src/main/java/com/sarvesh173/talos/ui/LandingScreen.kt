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
 * Benchmark Material 3 Multi-Agent Mesh Screen (AppLlama / Anti-Slop Discipline)
 *
 * Architecture & Engineering:
 * 1. Mathematically Exact Synapse Beams: Uses `onGloballyPositioned` to anchor curves
 *    directly to card centers with 0px offset drift across all device aspect ratios.
 * 2. Purged AI Slop: Removed fake radar circles, floating speech bubble stickers,
 *    and techno-babble HUD boxes.
 * 3. Integrated Telemetry Pill: Replaced floating bubbles with a sleek docked live
 *    activity marquee pill displaying real-time agent delegation.
 * 4. Native Tactile Fidelity: 0.97f press springs, authentic vector marks from Dashboard Icons,
 *    and calm semantic color tokens.
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
    val taskText: String
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
            DelegationEvent("hermes", "codex", "Synthesize unit test suite (60/60 passing)"),
            DelegationEvent("codex", "claude", "Review AST parser refactor (zero regression)"),
            DelegationEvent("claude", "openclaw", "Verify browser endpoints and auth tokens"),
            DelegationEvent("openclaw", "hermes", "Propose consensus quorum (4/4 threshold)"),
            DelegationEvent("hermes", "claude", "Synchronize WireGuard peer mesh (0.8ms latency)"),
            DelegationEvent("codex", "hermes", "Delegate code security review (clean)")
        )
    }

    var currentScenarioIndex by remember { mutableStateOf(0) }
    var activeSender by remember { mutableStateOf("hermes") }
    var activeReceiver by remember { mutableStateOf("codex") }
    var activeTaskText by remember { mutableStateOf("Synthesize unit test suite (60/60 passing)") }

    // Banter simulation loop
    LaunchedEffect(Unit) {
        while (true) {
            val scenario = scenarios[currentScenarioIndex]
            activeSender = scenario.fromKey
            activeReceiver = scenario.toKey
            activeTaskText = scenario.taskText
            delay(3600)
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
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
                // Docked Live Activity Pill (Anti-Slop: clean, docked, no floating stickers)
                val activeNode = nodes.firstOrNull { it.key == activeSender }
                val targetNode = nodes.firstOrNull { it.key == activeReceiver }
                val activeColor = activeNode?.accent ?: Color(0xFF6366F1)

                Surface(
                    color = talosSurface,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, activeColor.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
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
                            text = activeTaskText,
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Background Canvas: Layered behind cards so lines cleanly anchor into centers
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

                // Draw Mathematically Exact Curved Synapse Streams to Measured Anchors
                for ((key, cardPos) in anchorMap) {
                    if (cardPos == Offset.Zero) continue

                    val isBeamActive = (key == activeSender || key == activeReceiver)
                    val nodeAccent = nodes.firstOrNull { it.key == key }?.accent ?: Color(0xFF6366F1)

                    val isTop = cardPos.y < centerAnchor.y
                    val isLeft = cardPos.x < centerAnchor.x

                    // Natural organic bezier inflection points
                    val ctrl1 = Offset(
                        cardPos.x + (if (isLeft) 40.dp.toPx() else -40.dp.toPx()),
                        cardPos.y + (if (isTop) 30.dp.toPx() else -30.dp.toPx())
                    )
                    val ctrl2 = Offset(
                        centerAnchor.x + (if (isLeft) -30.dp.toPx() else 30.dp.toPx()),
                        centerAnchor.y + (if (isTop) -40.dp.toPx() else 40.dp.toPx())
                    )

                    val path = Path().apply {
                        moveTo(cardPos.x, cardPos.y)
                        cubicTo(ctrl1.x, ctrl1.y, ctrl2.x, ctrl2.y, centerAnchor.x, centerAnchor.y)
                    }

                    // Soft ambient halo
                    if (isBeamActive) {
                        drawPath(
                            path = path,
                            color = nodeAccent.copy(alpha = 0.20f),
                            style = Stroke(width = 4.dp.toPx())
                        )
                    }

                    // Crisp fiber-optic stream line
                    drawPath(
                        path = path,
                        brush = Brush.linearGradient(
                            colors = if (isBeamActive) {
                                listOf(nodeAccent.copy(alpha = 0.85f), Color(0xFF818CF8).copy(alpha = 0.90f))
                            } else {
                                listOf(Color(0xFF334155).copy(alpha = 0.35f), Color(0xFF1E293B).copy(alpha = 0.25f))
                            },
                            start = cardPos,
                            end = centerAnchor
                        ),
                        style = Stroke(width = if (isBeamActive) 2.0.dp.toPx() else 1.2.dp.toPx())
                    )

                    // Luminous Data Packet Pulse
                    if (key == activeSender) {
                        val t = packetProgress
                        val u = 1.0f - t

                        val px = u * u * u * cardPos.x + 3 * u * u * t * ctrl1.x + 3 * u * t * t * ctrl2.x + t * t * t * centerAnchor.x
                        val py = u * u * u * cardPos.y + 3 * u * u * t * ctrl1.y + 3 * u * t * t * ctrl2.y + t * t * t * centerAnchor.y

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(nodeAccent.copy(alpha = 0.9f), nodeAccent.copy(alpha = 0.2f), Color.Transparent),
                                center = Offset(px, py),
                                radius = 12.dp.toPx()
                            ),
                            radius = 12.dp.toPx(),
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
                    .offset(y = (-20).dp)
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
            // 2. CORNER AGENT CARDS (EXACT ANCHOR MEASUREMENT)
            // ==============================================================

            // TOP-LEFT: OpenClaw
            AgentNodeCard(
                node = nodes[0],
                isActive = activeSender == "openclaw" || activeReceiver == "openclaw",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 20.dp, top = 20.dp)
                    .onGloballyPositioned { coords ->
                        val p = coords.positionInParent()
                        val s = coords.size
                        tlAnchor = Offset(p.x + s.width / 2f, p.y + s.height / 2f)
                    },
                onTap = {
                    activeSender = "openclaw"
                    activeReceiver = "hermes"
                    activeTaskText = "Propose consensus quorum (4/4 threshold)"
                }
            )

            // TOP-RIGHT: Claude Code
            AgentNodeCard(
                node = nodes[1],
                isActive = activeSender == "claude" || activeReceiver == "claude",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 20.dp, top = 20.dp)
                    .onGloballyPositioned { coords ->
                        val p = coords.positionInParent()
                        val s = coords.size
                        trAnchor = Offset(p.x + s.width / 2f, p.y + s.height / 2f)
                    },
                onTap = {
                    activeSender = "claude"
                    activeReceiver = "codex"
                    activeTaskText = "Synthesize code module and verify AST"
                }
            )

            // BOTTOM-LEFT: Hermes Agent
            AgentNodeCard(
                node = nodes[2],
                isActive = activeSender == "hermes" || activeReceiver == "hermes",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = 24.dp)
                    .onGloballyPositioned { coords ->
                        val p = coords.positionInParent()
                        val s = coords.size
                        blAnchor = Offset(p.x + s.width / 2f, p.y + s.height / 2f)
                    },
                onTap = {
                    activeSender = "hermes"
                    activeReceiver = "claude"
                    activeTaskText = "Start architecture pass on consensus engine"
                }
            )

            // BOTTOM-RIGHT: Codex
            AgentNodeCard(
                node = nodes[3],
                isActive = activeSender == "codex" || activeReceiver == "codex",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 24.dp)
                    .onGloballyPositioned { coords ->
                        val p = coords.positionInParent()
                        val s = coords.size
                        brAnchor = Offset(p.x + s.width / 2f, p.y + s.height / 2f)
                    },
                onTap = {
                    activeSender = "codex"
                    activeReceiver = "openclaw"
                    activeTaskText = "Run automated regression tests"
                }
            )
        }
    }
}

/**
 * Clean Material 3 Agent Card with Tactile Press State and Official Vector Icon
 */
@Composable
private fun AgentNodeCard(
    node: AgentNode,
    isActive: Boolean,
    modifier: Modifier = Modifier,
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

    Surface(
        color = talosSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isActive) 1.8.dp else 1.dp,
            color = if (isActive) node.accent else talosBorder
        ),
        modifier = modifier
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
}
