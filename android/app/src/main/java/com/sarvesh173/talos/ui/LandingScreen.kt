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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Speed
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
 * Full Material Design 3 (M3 Expressive) Constellation Dashboard
 *
 * Google M3 Standards:
 * 1. M3 TopAppBar & Badges: CenterAligned / Elevated Top Bar with dynamic live peer badge.
 * 2. M3 AssistChips: Live telemetry row (WireGuard, 0.8ms Ping, 60% Quorum).
 * 3. M3 Segmented Button: Perspective switcher ([ ☩ Mesh Constellation | 📊 Telemetry Council ]).
 * 4. M3 ElevatedCards: Real elevated card containers with tonal elevation and metallic borders.
 * 5. Machined Dark-Well Sockets: Every agent emblem sits in a precision obsidian socket (#0F172A)
 *    to eliminate contrast washed-out issues across both dark and light modes.
 * 6. M3 Springs & Motion: 800f/0.75f tactile press scale and 400f/0.75f spatial morphs.
 * 7. Authentic MCP Vectors: Nous Research Hermes, Codex >_ prompt, Claude terracotta spark,
 *    OpenClaw lobster, and OpenCode 3D monogram.
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
    val actionType: DelegationAction = DelegationAction.NORMAL
)

enum class DelegationAction {
    NORMAL,
    SUMMON_OPENCODE,
    TERMINATE_OPENCODE
}

@OptIn(ExperimentalMaterial3Api::class)
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
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 800f),
        label = "ButtonScale"
    )

    var selectedViewMode by remember { mutableStateOf(0) } // 0 = Constellation, 1 = Council Telemetry

    val handlePrimaryAction = {
        onGetStarted()
    }

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

    // Dynamic Multi-Agent Banter & Summon/Dismiss Lifecycle
    val scenarios = remember {
        listOf(
            DelegationEvent("hermes", "codex", "Hi @codex, synthesize unit test suite", "Tests generated: 63/63 passing"),
            DelegationEvent("codex", "claude", "Hi @claude, review AST parser refactor", "System design verified: clean"),
            DelegationEvent(
                fromKey = "claude",
                toKey = "opencode",
                taskText = "Deep terminal AST analysis required. Summoning @opencode CLI agent...",
                replyText = "OpenCode online! AST & terminal tools ready.",
                actionType = DelegationAction.SUMMON_OPENCODE
            ),
            DelegationEvent(
                fromKey = "opencode",
                toKey = "claude",
                taskText = "Executing deep AST pass: 0 syntax defects.",
                replyText = "AST verified clean. Terminating @opencode session.",
                actionType = DelegationAction.TERMINATE_OPENCODE
            ),
            DelegationEvent("openclaw", "hermes", "Consensus proposal across mesh cluster.", "Quorum verified (4/4 approved)")
        )
    }

    var currentScenarioIndex by remember { mutableStateOf(0) }
    var activeSender by remember { mutableStateOf("hermes") }
    var activeReceiver by remember { mutableStateOf("codex") }
    var senderTask by remember { mutableStateOf("Hi @codex, synthesize test suite") }
    var receiverReply by remember { mutableStateOf<String?>(null) }
    var isReceiverSpeaking by remember { mutableStateOf(false) }

    // Banter simulation loop with dynamic summon and termination
    LaunchedEffect(Unit) {
        while (true) {
            val scenario = scenarios[currentScenarioIndex]
            activeSender = scenario.fromKey
            activeReceiver = scenario.toKey
            senderTask = scenario.taskText
            receiverReply = null
            isReceiverSpeaking = false

            if (scenario.actionType == DelegationAction.SUMMON_OPENCODE) {
                delay(800)
                isOpenCodeSpawned = true
            }

            delay(2100)
            isReceiverSpeaking = true
            receiverReply = scenario.replyText

            if (scenario.actionType == DelegationAction.TERMINATE_OPENCODE) {
                delay(2000)
                isOpenCodeSpawned = false
            }

            delay(2600)
            currentScenarioIndex = (currentScenarioIndex + 1) % scenarios.size
        }
    }

    // Continuous Animation Drivers
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

    val compassRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "compass_rotation"
    )

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
    var opencodeAnchor by remember { mutableStateOf(Offset.Zero) }

    val anchorMap = mutableMapOf(
        "openclaw" to tlAnchor,
        "claude" to trAnchor,
        "hermes" to blAnchor,
        "codex" to brAnchor
    )
    if (isOpenCodeSpawned && opencodeAnchor != Offset.Zero) {
        anchorMap["opencode"] = opencodeAnchor
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = MdSpacing.sm, vertical = MdSpacing.xs)
            ) {
                // M3 Top Bar Row with Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MdSpacing.xs)
                    ) {
                        Text(
                            text = "TALOS",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )

                        // M3 Live Peer Badge
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = ShapeFull,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(StatusGreen)
                                )
                                Text(
                                    text = if (isOpenCodeSpawned) "5 AGENTS ACTIVE" else "4 AGENTS ACTIVE",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // M3 Icon Button for Theme Toggle
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .semantics {
                                contentDescription = if (isCurrentDark) "Switch to Light Mode" else "Switch to Dark Mode"
                            }
                    ) {
                        Icon(
                            imageVector = if (isCurrentDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(MdSpacing.xs))

                // M3 AssistChip Telemetry Row (Genuine Material 3 Identity)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = {},
                        label = { Text("WireGuard Mesh", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(12.dp))
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text("0.8ms Ping", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(12.dp))
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text("Quorum 60%", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(12.dp))
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // M3 Segmented Perspective Switcher
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (selectedViewMode == 0) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                                .clickable { selectedViewMode = 0 },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "☩ Mesh Constellation",
                                color = if (selectedViewMode == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selectedViewMode == 0) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (selectedViewMode == 1) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                                .clickable { selectedViewMode = 1 },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "📊 Council Telemetry",
                                color = if (selectedViewMode == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selectedViewMode == 1) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = MdSpacing.sm, vertical = MdSpacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Docked Live Activity Pill
                val activeNode = baseNodes.firstOrNull { it.key == activeSender }
                val targetNode = baseNodes.firstOrNull { it.key == activeReceiver }
                val activeColor = activeNode?.accent ?: PrimaryAccent

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = ShapeFull,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = MdSpacing.xs)
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
                        Spacer(modifier = Modifier.width(MdSpacing.xs))
                        Text(
                            text = "${activeNode?.name ?: "Agent"} → ${targetNode?.name ?: "Agent"}:",
                            color = activeColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = senderTask,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Primary M3 Enter Chamber Action - FilledButton with ShapeFull and spring physics
                Button(
                    onClick = handlePrimaryAction,
                    interactionSource = interactionSource,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCurrentDark) Color(0xFFF8FAFC) else Color(0xFF0F172A),
                        contentColor = if (isCurrentDark) Color(0xFF090D14) else Color(0xFFF8FAFC)
                    ),
                    shape = ShapeFull,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .scale(buttonScale)
                        .semantics {
                            contentDescription = "Enter Chamber and begin multi-agent session"
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enter Chamber",
                        style = MaterialTheme.typography.labelLarge,
                        letterSpacing = 0.4.sp
                    )
                }

                // M3 OutlinedButton for Setup Mesh & Token
                if (onOpenWizard != null) {
                    Spacer(modifier = Modifier.height(MdSpacing.sm))
                    OutlinedButton(
                        onClick = onOpenWizard,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = ShapeFull,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SettingsEthernet,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(MdSpacing.xs))
                        Text(
                            text = "Setup Mesh & Token",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Precision Engineering Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (centerAnchor == Offset.Zero) return@Canvas

                // 1. Subtle Celestial Compass Ring
                val radCompass = Math.toRadians(compassRotation.toDouble())
                val orbitRadius = 66.dp.toPx()

                drawCircle(
                    color = Color(0xFF64748B).copy(alpha = if (isCurrentDark) 0.25f else 0.18f),
                    radius = orbitRadius,
                    center = centerAnchor,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f), compassRotation * 1.2f)
                    )
                )

                // Orbiting satellite pulse node
                val satX = centerAnchor.x + (orbitRadius * cos(radCompass)).toFloat()
                val satY = centerAnchor.y + (orbitRadius * sin(radCompass)).toFloat()
                drawCircle(
                    color = Color(0xFF94A3B8),
                    radius = 2.5.dp.toPx(),
                    center = Offset(satX, satY)
                )

                // 2. Secondary Cross-Mesh Peer Lattice
                if (tlAnchor != Offset.Zero && trAnchor != Offset.Zero && blAnchor != Offset.Zero && brAnchor != Offset.Zero) {
                    val peerLatticeColor = Color(0xFF64748B).copy(alpha = if (isCurrentDark) 0.18f else 0.14f)
                    val latticeStroke = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f))
                    drawLine(peerLatticeColor, tlAnchor, trAnchor, strokeWidth = 1.dp.toPx(), pathEffect = latticeStroke.pathEffect)
                    drawLine(peerLatticeColor, trAnchor, brAnchor, strokeWidth = 1.dp.toPx(), pathEffect = latticeStroke.pathEffect)
                    drawLine(peerLatticeColor, brAnchor, blAnchor, strokeWidth = 1.dp.toPx(), pathEffect = latticeStroke.pathEffect)
                    drawLine(peerLatticeColor, blAnchor, tlAnchor, strokeWidth = 1.dp.toPx(), pathEffect = latticeStroke.pathEffect)
                }

                // 3. Direct Peer-to-Peer Conduit: Claude Code -> OpenCode (When OpenCode is Summoned!)
                if (isOpenCodeSpawned && trAnchor != Offset.Zero && opencodeAnchor != Offset.Zero) {
                    val isDirectBeamActive = (activeSender == "claude" && activeReceiver == "opencode") ||
                            (activeSender == "opencode" && activeReceiver == "claude")

                    val dxClaude = opencodeAnchor.x - trAnchor.x
                    val dyClaude = opencodeAnchor.y - trAnchor.y

                    val ctrlClaude1 = Offset(trAnchor.x + dxClaude * 0.20f, trAnchor.y + dyClaude * 0.80f)
                    val ctrlClaude2 = Offset(trAnchor.x + dxClaude * 0.80f, trAnchor.y + dyClaude * 0.20f)

                    val directClaudePath = Path().apply {
                        moveTo(trAnchor.x, trAnchor.y)
                        cubicTo(ctrlClaude1.x, ctrlClaude1.y, ctrlClaude2.x, ctrlClaude2.y, opencodeAnchor.x, opencodeAnchor.y)
                    }

                    // Direct summon beam
                    drawPath(
                        path = directClaudePath,
                        brush = Brush.linearGradient(
                            colors = if (isDirectBeamActive) {
                                listOf(Color(0xFFD97757).copy(alpha = 0.85f), Color(0xFF3B82F6).copy(alpha = 0.85f))
                            } else {
                                listOf(Color(0xFFD97757).copy(alpha = 0.35f), Color(0xFF3B82F6).copy(alpha = 0.35f))
                            },
                            start = trAnchor,
                            end = opencodeAnchor
                        ),
                        style = Stroke(
                            width = if (isDirectBeamActive) 2.2.dp.toPx() else 1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), packetProgress * 20f)
                        )
                    )

                    // Direct packet traveling between Claude and OpenCode
                    if (isDirectBeamActive) {
                        val t = packetProgress
                        val u = 1.0f - t
                        val px = u * u * u * trAnchor.x + 3 * u * u * t * ctrlClaude1.x + 3 * u * t * t * ctrlClaude2.x + t * t * t * opencodeAnchor.x
                        val py = u * u * u * trAnchor.y + 3 * u * u * t * ctrlClaude1.y + 3 * u * t * t * ctrlClaude2.y + t * t * t * opencodeAnchor.y

                        drawCircle(
                            color = Color(0xFF3B82F6).copy(alpha = 0.50f),
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

                // 4. Primary Curvilinear S-Curve Conduits to Central TALOS Core
                for ((key, cardPos) in anchorMap) {
                    if (cardPos == Offset.Zero) continue

                    val isBeamActive = (key == activeSender || key == activeReceiver)
                    val nodeAccent = baseNodes.firstOrNull { it.key == key }?.accent ?: PrimaryAccent

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

                    // Luminous Data Packet to Center Hub
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
            // 1. CENTER TALOS HARDWARE INSTRUMENT (M3 ElevatedCard)
            // ==============================================================
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-36).dp)
                    .scale(auraPulse)
                    .onGloballyPositioned { coords ->
                        val p = coords.positionInParent()
                        val s = coords.size
                        centerAnchor = Offset(p.x + s.width / 2f, p.y + s.height / 2f)
                    },
                contentAlignment = Alignment.Center
            ) {
                ElevatedCard(
                    shape = ShapeLargeIncreased,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                    modifier = Modifier
                        .size(width = 114.dp, height = 98.dp)
                        .border(1.2.dp, metallicBorderBrush(isCurrentDark), ShapeLargeIncreased)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = MdSpacing.xs, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "TALOS",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MdSpacing.xxs)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(StatusGreen)
                            )
                            Text(
                                text = "MESH CORE",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = if (isOpenCodeSpawned) "5 PEERS · 0.8ms" else "4 PEERS · 0.8ms",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // ==============================================================
            // 2. CORNER & DYNAMIC AGENT CARDS (M3 ElevatedCards)
            // ==============================================================

            // TOP-LEFT: OpenClaw (With dark metallic squircle well for 100% lobster contrast!)
            AgentNodeWithBubble(
                node = baseNodes[0],
                isActive = activeSender == "openclaw" || activeReceiver == "openclaw",
                bubbleText = if (activeSender == "openclaw") senderTask else (if (activeReceiver == "openclaw" && isReceiverSpeaking) receiverReply else null),
                bubbleBelow = true,
                isDark = isCurrentDark,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = MdSpacing.sm, top = MdSpacing.xs)
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
                    .padding(end = MdSpacing.sm, top = MdSpacing.xs)
                    .onGloballyPositioned { coords ->
                        val p = coords.positionInParent()
                        val s = coords.size
                        trAnchor = Offset(p.x + s.width / 2f, p.y + s.height / 2f)
                    },
                onTap = {
                    activeSender = "claude"
                    activeReceiver = "opencode"
                    senderTask = "Deep terminal AST analysis required. Summoning @opencode CLI agent..."
                    receiverReply = null
                    isReceiverSpeaking = false
                    isOpenCodeSpawned = true
                }
            )

            // DYNAMIC 5TH AGENT: OpenCode (Recruited by Claude Code, positioned cleanly below center with zero overlap!)
            AnimatedVisibility(
                visible = isOpenCodeSpawned,
                enter = fadeIn(spring(dampingRatio = 0.75f, stiffness = 400f)) + scaleIn(spring(dampingRatio = 0.75f, stiffness = 400f)),
                exit = fadeOut(tween(200)) + scaleOut(tween(200)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 96.dp)
            ) {
                AgentNodeWithBubble(
                    node = baseNodes[4],
                    isActive = activeSender == "opencode" || activeReceiver == "opencode",
                    bubbleText = if (activeSender == "opencode") senderTask else (if (activeReceiver == "opencode" && isReceiverSpeaking) receiverReply else null),
                    bubbleBelow = true, // Bubble placed BELOW so it never overlaps TALOS Core!
                    isDark = isCurrentDark,
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            val p = coords.positionInParent()
                            val s = coords.size
                            opencodeAnchor = Offset(p.x + s.width / 2f, p.y + s.height / 2f)
                        },
                    onTap = {
                        activeSender = "opencode"
                        activeReceiver = "claude"
                        senderTask = "Executing deep AST pass: 0 syntax defects."
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
                    .padding(start = MdSpacing.sm, bottom = MdSpacing.sm)
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
                    .padding(end = MdSpacing.sm, bottom = MdSpacing.sm)
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
 * Material 3 ElevatedCard with Machined Dark Obsidian Sockets
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
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, node.accent.copy(alpha = if (isDark) 0.60f else 0.85f)),
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
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Tactile Material 3 ElevatedCard
        ElevatedCard(
            shape = ShapeLargeIncreased,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.elevatedCardElevation(
                defaultElevation = if (isActive) 6.dp else 2.dp,
                pressedElevation = 0.dp
            ),
            modifier = Modifier
                .size(width = 114.dp, height = 100.dp)
                .scale(cardScale)
                .border(
                    width = if (isActive) 1.5.dp else 1.dp,
                    brush = if (isActive) {
                        Brush.linearGradient(
                            colors = listOf(
                                node.accent.copy(alpha = 0.95f),
                                node.accent.copy(alpha = 0.40f)
                            )
                        )
                    } else {
                        metallicBorderBrush(isDark)
                    },
                    shape = ShapeLargeIncreased
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { onTap() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(7.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Machined Dark Obsidian Socket (ensures 100% emblem contrast in all themes!)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, node.accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AgentEmblemIcon(emblem = node.emblem, size = 28.dp, tint = null)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = node.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isActive) "Active" else node.specTag,
                        color = if (isActive) node.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Speech Bubble Placed Below Card (for top agents & OpenCode)
        if (bubbleBelow) {
            AnimatedVisibility(
                visible = !bubbleText.isNullOrBlank() && isActive,
                enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -15 },
                exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { 10 }
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, node.accent.copy(alpha = if (isDark) 0.60f else 0.85f)),
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
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
