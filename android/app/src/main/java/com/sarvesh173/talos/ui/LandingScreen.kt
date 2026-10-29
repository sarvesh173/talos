package com.sarvesh173.talos.ui

import androidx.compose.ui.zIndex

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
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalView
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
 * Material Design 3 Expressive (M3) Constellation Dashboard
 *
 * Strict Anti-Slop & Craft Standards:
 * 1. Zero Unicode Emojis / Glyphs: Replaced entirely with official Material Icons.
 * 2. M3 Expressive Motion Curves: Emphasized Decelerate (CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f))
 *    for fluid packet flight and natural spring damping.
 * 3. Fluid Optical Conduits: Soft layered Bezier streams with luminous core paths and ambient diffusion halos.
 * 4. Canonical M3 Controls: CenterAlignedTopAppBar, AssistChips, SingleChoiceSegmentedButtonRow,
 *    and ElevatedCards with tonal elevation hierarchy.
 * 5. Machined Obsidian Sockets (#0F172A): Multi-color emblems sit in dark contrast wells to preserve
 *    crisp vector geometry in both light and dark themes.
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

// Material 3 Expressive Easing Curve
private val M3EmphasizedEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
private val M3EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

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
    val scheme = MaterialTheme.colorScheme
    val dissolvePaletteLocal = dissolvePalette()
    val isCurrentDark = LocalIsDark.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 800f),
        label = "ButtonScale"
    )

    var selectedViewMode by remember { mutableStateOf(0) } // 0 = Constellation, 1 = Council Telemetry

    // ==== FULL-SCREEN ADAPTIVE MICRO-PARTICLE DISSOLVE (Material 3 Motion) ====
    val rootView = LocalView.current
    var isDissolving by remember { mutableStateOf(false) }
    var particleField by remember { mutableStateOf<DotField?>(null) }
    val dissolveProgress = remember { mutableFloatStateOf(0f) }
    val scaffoldAlpha = remember { mutableFloatStateOf(1f) }

    // Ambient idle micro-motion: keeps the hero alive while the user rests on the page.
    val idleBreath by rememberInfiniteTransition(label = "idle_micro_motion").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_breath_phase"
    )

    LaunchedEffect(isDissolving) {
        if (!isDissolving) return@LaunchedEffect

        val frozenFrame = captureRenderedFrame(rootView)
        val palette = dissolvePaletteLocal
        particleField = buildDotField(frozenFrame, palette)

        val startNanos = withFrameNanos { it }
        var navigated = false
        while (true) {
            val nowNanos = withFrameNanos { it }
            val elapsedMs = (nowNanos - startNanos) / 1_000_000f
            val normalized = (elapsedMs / DISSOLVE_TOTAL_MS).coerceIn(0f, 1f)
            dissolveProgress.floatValue = normalized
            scaffoldAlpha.floatValue = (1f - (elapsedMs / 950f)).coerceIn(0f, 1f)

            if (!navigated && elapsedMs >= DISSOLVE_NAVIGATE_MS) {
                navigated = true
                onGetStarted()
            }
            if (elapsedMs >= DISSOLVE_TOTAL_MS) {
                break
            }
        }
    }

    // Breaking particle dot animation: strictly ONE-TIME ONLY.
    // Once launched, the state is persisted and the animation never re-arms or replays.
    val handlePrimaryAction = {
        if (!client.hasLaunched && !isDissolving) {
            client.markLaunched()
            isDissolving = true
        } else {
            client.markLaunched()
            onGetStarted()
        }
    }

    val baseNodes = remember {
        listOf(
            AgentNode("openclaw", "OpenClaw", "Tools & Automation", "Tools · Auto", emblemByKey("openclaw"), emblemByKey("openclaw").accent),
            AgentNode("claude", "Claude Code", "Anthropic · Architecture", "Arch · Plan", emblemByKey("claude"), emblemByKey("claude").accent),
            AgentNode("hermes", "Hermes Agent", "Nous · Autonomy", "Nous · Exec", emblemByKey("hermes"), emblemByKey("hermes").accent),
            AgentNode("codex", "Codex", "OpenAI · Synthesis", "Code · Synth", emblemByKey("codex"), emblemByKey("codex").accent),
            AgentNode("opencode", "OpenCode", "Open-Source Terminal", "CLI · Exec", emblemByKey("opencode"), emblemByKey("opencode").accent)
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

    // Material 3 Continuous Expressive Animation Drivers
    val infiniteTransition = rememberInfiniteTransition(label = "mesh_pulse")

    val rawProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "packet_pulse"
    )

    // Fluid eased motion packet progress
    val packetProgress = remember(rawProgress) {
        M3EmphasizedEasing.transform(rawProgress)
    }

    val compassRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(40000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "compass_rotation"
    )

    val ambientGlowPulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_glow"
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Smooth initial cross-dissolve to particles
                alpha = scaffoldAlpha.floatValue
            },
        containerColor = scheme.background,
                topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = MdSpacing.sm, vertical = 6.dp)
            ) {
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
                            color = scheme.onSurface,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = ShapeFull,
                            border = BorderStroke(1.dp, scheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(StatusGreen)
                                )
                                Text(
                                    text = if (isOpenCodeSpawned) "5 AGENTS ACTIVE" else "4 AGENTS ACTIVE",
                                    color = scheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    val themeTransition = updateTransition(targetState = isCurrentDark, label = "landing_theme_transition")
                    val themeRot by themeTransition.animateFloat(
                        transitionSpec = { spring(dampingRatio = 0.55f, stiffness = 500f) },
                        label = "landing_theme_rot"
                    ) { dark -> if (dark) 0f else 180f }
                    val themeScale by themeTransition.animateFloat(
                        transitionSpec = { spring(dampingRatio = 0.55f, stiffness = 500f) },
                        label = "landing_theme_scale"
                    ) { dark -> if (dark) 1.0f else 1.15f }
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .border(1.dp, metallicBorderBrush(), CircleShape)
                            .semantics {
                                contentDescription = if (isCurrentDark) "Switch to Light Mode" else "Switch to Dark Mode"
                            }
                    ) {
                        Icon(
                            imageVector = if (isCurrentDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = null,
                            tint = PrimaryAccent,
                            modifier = Modifier
                                .size(19.dp)
                                .graphicsLayer {
                                    rotationZ = themeRot
                                    scaleX = themeScale
                                    scaleY = themeScale
                                }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
        },

        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(scheme.background)
                    .padding(horizontal = MdSpacing.sm, vertical = MdSpacing.xxs),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Docked Live Activity Pill
                val activeNode = baseNodes.firstOrNull { it.key == activeSender }
                val targetNode = baseNodes.firstOrNull { it.key == activeReceiver }
                val activeColor = activeNode?.accent ?: PrimaryAccent

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = ShapeFull,
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = MdSpacing.xxs)
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
                            color = scheme.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Primary M3 Action Button
                Button(
                    onClick = handlePrimaryAction,
                    interactionSource = interactionSource,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.surfaceContainerHighest,
                        contentColor = scheme.onSurface
                    ),
                    shape = ShapeFull,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .scale(buttonScale)
                        .graphicsLayer {
                            // Idle micro-motion: never a dead frame while the page rests.
                            val breath = idleBreath
                            val breathScale = 1f + 0.010f * breath
                            scaleX = breathScale
                            scaleY = breathScale
                            translationY = -1.6f * breath
                        }
                        .semantics {
                            contentDescription = "Enter Chamber and begin multi-agent session"
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enter Chamber",
                        style = MaterialTheme.typography.labelLarge,
                        letterSpacing = 0.4.sp
                    )
                }

                // Frictionless Onboarding & Setup Actions (M3 Outlined Secondary)
                if (onOpenWizard != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = onOpenWizard,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SettingsEthernet,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Setup WireGuard Mesh & Token",
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
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
            // The dissolve overlay now lives at the window root (see ParticleDissolveOverlay)
            // so the micro-particles cover the entire application, edge to edge.
            // Fluid Material 3 Expressive Canvas
            // (scheme colors are captured BEFORE the draw scope - DrawScope is not composable)
            val canvasPrimary = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (centerAnchor == Offset.Zero) return@Canvas

                // 1. Ambient Breathing Halo behind Central Hub
                val glowRadius = 88.dp.toPx() * ambientGlowPulse
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            scheme.onSurfaceVariant.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = centerAnchor,
                        radius = glowRadius
                    ),
                    radius = glowRadius,
                    center = centerAnchor
                )

                // 2. Subtle Orbital Compass Track
                val radCompass = Math.toRadians(compassRotation.toDouble())
                val orbitRadius = 68.dp.toPx()

                drawCircle(
                    color = scheme.onSurfaceVariant.copy(alpha = 0.20f),
                    radius = orbitRadius,
                    center = centerAnchor,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f), compassRotation * 0.8f)
                    )
                )

                val satX = centerAnchor.x + (orbitRadius * cos(radCompass)).toFloat()
                val satY = centerAnchor.y + (orbitRadius * sin(radCompass)).toFloat()
                drawCircle(
                    color = scheme.onSurfaceVariant,
                    radius = 2.dp.toPx(),
                    center = Offset(satX, satY)
                )

                // 3. Direct Peer Conduit: Claude Code -> OpenCode (When OpenCode is Summoned)
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

                    val claudeBeamColor = emblemByKey("claude").accent
                    val opencodeBeamColor = emblemByKey("opencode").accent

                    // Soft ambient diffusion glow
                    drawPath(
                        path = directClaudePath,
                        brush = Brush.linearGradient(
                            colors = if (isDirectBeamActive) {
                                listOf(claudeBeamColor.copy(alpha = 0.25f), opencodeBeamColor.copy(alpha = 0.25f))
                            } else {
                                listOf(claudeBeamColor.copy(alpha = 0.10f), opencodeBeamColor.copy(alpha = 0.10f))
                            },
                            start = trAnchor,
                            end = opencodeAnchor
                        ),
                        style = Stroke(width = 5.dp.toPx())
                    )

                    // Crisp core beam
                    drawPath(
                        path = directClaudePath,
                        brush = Brush.linearGradient(
                            colors = if (isDirectBeamActive) {
                                listOf(claudeBeamColor.copy(alpha = 0.90f), opencodeBeamColor.copy(alpha = 0.90f))
                            } else {
                                listOf(claudeBeamColor.copy(alpha = 0.35f), opencodeBeamColor.copy(alpha = 0.35f))
                            },
                            start = trAnchor,
                            end = opencodeAnchor
                        ),
                        style = Stroke(
                            width = if (isDirectBeamActive) 1.8.dp.toPx() else 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), packetProgress * 24f)
                        )
                    )

                    // Eased traveling photon packet
                    if (isDirectBeamActive) {
                        val t = packetProgress
                        val u = 1.0f - t
                        val px = u * u * u * trAnchor.x + 3 * u * u * t * ctrlClaude1.x + 3 * u * t * t * ctrlClaude2.x + t * t * t * opencodeAnchor.x
                        val py = u * u * u * trAnchor.y + 3 * u * u * t * ctrlClaude1.y + 3 * u * t * t * ctrlClaude2.y + t * t * t * opencodeAnchor.y

                        // Outer luminous halo
                        drawCircle(
                            color = opencodeBeamColor.copy(alpha = 0.35f),
                            radius = 7.dp.toPx(),
                            center = Offset(px, py)
                        )
                        // Specular core
                        drawCircle(
                            color = scheme.onSurface,
                            radius = 2.5.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                }

                // 4. Primary Curvilinear Conduits to Central TALOS Core
                for ((key, cardPos) in anchorMap) {
                    if (cardPos == Offset.Zero) continue

                    val isBeamActive = (key == activeSender || key == activeReceiver)
                    val nodeAccent = baseNodes.firstOrNull { it.key == key }?.accent ?: canvasPrimary

                    val dx = centerAnchor.x - cardPos.x
                    val dy = centerAnchor.y - cardPos.y

                    val ctrl1 = Offset(cardPos.x + dx * 0.70f, cardPos.y + dy * 0.10f)
                    val ctrl2 = Offset(cardPos.x + dx * 0.30f, cardPos.y + dy * 0.90f)

                    val pathPrimary = Path().apply {
                        moveTo(cardPos.x, cardPos.y)
                        cubicTo(ctrl1.x, ctrl1.y, ctrl2.x, ctrl2.y, centerAnchor.x, centerAnchor.y)
                    }

                    // Ambient glow stroke
                    if (isBeamActive) {
                        drawPath(
                            path = pathPrimary,
                            brush = Brush.linearGradient(
                                colors = listOf(nodeAccent.copy(alpha = 0.20f), Color.Transparent),
                                start = cardPos,
                                end = centerAnchor
                            ),
                            style = Stroke(width = 6.dp.toPx())
                        )
                    }

                    // Main crisp conduit line
                    drawPath(
                        path = pathPrimary,
                        brush = Brush.linearGradient(
                            colors = if (isBeamActive) {
                                listOf(nodeAccent.copy(alpha = 0.90f), scheme.onSurfaceVariant.copy(alpha = 0.70f))
                            } else {
                                listOf(scheme.onSurfaceVariant.copy(alpha = 0.30f), scheme.outline.copy(alpha = 0.15f))
                            },
                            start = cardPos,
                            end = centerAnchor
                        ),
                        style = Stroke(width = if (isBeamActive) 1.8.dp.toPx() else 1.dp.toPx())
                    )

                    // Luminous Eased Data Packet
                    if (key == activeSender) {
                        val t = packetProgress
                        val u = 1.0f - t

                        val px = u * u * u * cardPos.x + 3 * u * u * t * ctrl1.x + 3 * u * t * t * ctrl2.x + t * t * t * centerAnchor.x
                        val py = u * u * u * cardPos.y + 3 * u * u * t * ctrl1.y + 3 * u * t * t * ctrl2.y + t * t * t * centerAnchor.y

                        drawCircle(
                            color = nodeAccent.copy(alpha = 0.35f),
                            radius = 7.dp.toPx(),
                            center = Offset(px, py)
                        )
                        drawCircle(
                            color = scheme.onSurface,
                            radius = 2.6.dp.toPx(),
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
                        .border(1.2.dp, metallicBorderBrush(), ShapeLargeIncreased)
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
                            color = scheme.onSurface,
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
                                color = scheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.6.dp, scheme.outlineVariant),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = if (isOpenCodeSpawned) "5 PEERS · 0.8ms" else "4 PEERS · 0.8ms",
                                color = scheme.onSurfaceVariant,
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

            // TOP-LEFT: OpenClaw
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

            // DYNAMIC 5TH AGENT: OpenCode
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
                    bubbleBelow = true,
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

    // Full-screen adaptive micro-particle dissolve overlay: covers the complete window,
    // top bar and bottom gesture area included, on top of every other surface.
    ParticleDissolveOverlay(
        field = particleField,
        progress = { dissolveProgress.floatValue },
        modifier = Modifier.fillMaxSize()
    )
    }
}

// ============================================================================
//  Adaptive Micro-Particle Dissolve Engine
// ============================================================================

/** Total dissolve length: exactly 4.8 seconds for sustained visual satisfaction. */
private const val DISSOLVE_TOTAL_MS = 4800f

/** Seamless handover: next screen materializes under the floating starry embers at 3.2s. */
private const val DISSOLVE_NAVIGATE_MS = 2900f

/** Coarse-to-fine density of the micro-particle lattice. */
private const val DISSOLVE_COLS = 96
private const val MAX_DISSOLVE_DOTS = 26000

/** Material 3 emphasised motion ramp for the whole dissolve. */
private val DISSOLVE_RAMP_EASING = CubicBezierEasing(0.20f, 0.66f, 0.14f, 1.0f)

/**
 * Dissolve gradient palette - derived from the active scheme so the same
 * gradient reads correctly in light and dark (one code path, no per-mode
 * duplicate palette to maintain).
 */
@Composable
private fun dissolvePalette(): IntArray {
    val s = MaterialTheme.colorScheme
    return intArrayOf(
        s.surfaceContainerLowest.toArgb(), s.surfaceContainerLowest.toArgb(),
        s.surfaceContainerLow.toArgb(), s.surfaceContainer.toArgb(),
        s.surfaceContainerHigh.toArgb(), s.onSurface.toArgb(),
        s.onSurfaceVariant.toArgb(),
        s.primary.toArgb(), s.tertiary.toArgb(), s.secondary.toArgb(),
        s.primaryContainer.toArgb(), s.tertiaryContainer.toArgb()
    )
}

/**
 * Immutable micro-particle lattice. Every dot carries its own sampled colour, its own
 * entropy delay, life span, radial drift and tangential swirl, so no two taps, and no
 * two dots, ever resolve into the same trajectory.
 *
 * Dots are pre-grouped into colour buckets and drawn with batched native point runs,
 * which keeps tens of thousands of particles inside the frame budget.
 */
private class DotField(
    val count: Int,
    val cols: Int,
    val rows: Int,
    val xs: FloatArray,
    val ys: FloatArray,
    val delay: FloatArray,
    val life: FloatArray,
    val dirX: FloatArray,
    val dirY: FloatArray,
    val swirl: FloatArray,
    val order: IntArray,
    val bucketColor: IntArray,
    val bucketStart: IntArray,
    val bucketEnd: IntArray
)

private fun hash01(x: Float): Float {
    val s = sin(x) * 43758.5453f
    return s - kotlin.math.floor(s)
}

/** Lifts captured pixels slightly so dark particles stay readable against dark surfaces. */
private fun liftLuma(argb: Int): Int {
    val a = (argb ushr 24) and 0xFF
    if (a < 24) return 0xFF131C2B.toInt()
    val r = ((argb ushr 16) and 0xFF) + 30
    val g = ((argb ushr 8) and 0xFF) + 30
    val b = (argb and 0xFF) + 30
    return (0xFF shl 24) or
            (r.coerceAtMost(255) shl 16) or
            (g.coerceAtMost(255) shl 8) or
            b.coerceAtMost(255)
}

/** Synchronous freeze-frame of what is on screen right now, in device pixels. */
private fun captureRenderedFrame(view: android.view.View): android.graphics.Bitmap? {
    return try {
        val w = view.width
        val h = view.height
        if (w <= 0 || h <= 0) {
            null
        } else {
            val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            view.draw(canvas)
            bitmap
        }
    } catch (t: Throwable) {
        null
    }
}

private fun buildDotField(
    shot: android.graphics.Bitmap?,
    fallbackPalette: IntArray
): DotField {
    val bmpW = shot?.width ?: 1080
    val bmpH = shot?.height ?: 2340
    val cols = DISSOLVE_COLS
    val rows = ((cols.toFloat() * bmpH.toFloat()) / bmpW.toFloat()).toInt().coerceIn(60, 240)
    val count = cols * rows

    val xs = FloatArray(count)
    val ys = FloatArray(count)
    val delay = FloatArray(count)
    val life = FloatArray(count)
    val dirX = FloatArray(count)
    val dirY = FloatArray(count)
    val swirl = FloatArray(count)
    val colors = IntArray(count)

    val rowBuf = IntArray(bmpW)
    val histogram = HashMap<Int, Int>()
    val captured = shot != null

    for (r in 0 until rows) {
        if (captured) {
            val srcRow = (((r + 0.5f) * bmpH.toFloat()) / rows.toFloat()).toInt().coerceIn(0, bmpH - 1)
            shot!!.getPixels(rowBuf, 0, bmpW, 0, srcRow, bmpW, 1)
        }
        for (c in 0 until cols) {
            val i = r * cols + c
            val u = (c + 0.5f) / cols.toFloat()
            val v = (r + 0.5f) / rows.toFloat()
            xs[i] = u
            ys[i] = v

            // Distance from button blast epicenter (bottom center: 0.5, 0.88)
            val dx = u - 0.5f
            val dy = v - 0.88f
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)

            val e1 = hash01(c * 17.123f + r * 89.431f)
            val e2 = hash01(c * 43.192f + r * 23.774f)
            val e3 = hash01(c * 7.891f + r * 53.219f)

            // Radial shockwave cascade: dots near button detonate first, spreading upward
            delay[i] = (dist * 0.35f + e1 * 0.10f).coerceIn(0f, 0.42f)
            // Sustained life: dots stay visible and float gracefully across the full 4.8s
            life[i] = 0.52f + e2 * 0.16f
            swirl[i] = (e3 - 0.5f) * 2f

            val nx = if (dist > 0.001f) dx / dist else (e1 - 0.5f) * 2f

            // Upward Ascension (↑): particles ascend vertically with subtle organic flutter (no wide sideways drift)
            dirX[i] = (nx * 0.18f) + (e3 - 0.5f) * 0.22f
            dirY[i] = -0.92f - (e2 * 0.48f)

            if (captured) {
                val px = ((c + 0.5f) * bmpW.toFloat() / cols.toFloat()).toInt().coerceIn(0, bmpW - 1)
                val sampled = liftLuma(rowBuf[px])
                colors[i] = sampled
                val key = sampled and 0xFFE0E0E0.toInt()
                histogram[key] = (histogram[key] ?: 0) + 1
            } else {
                colors[i] = fallbackPalette[(e1 * fallbackPalette.size).toInt().coerceIn(0, fallbackPalette.size - 1)]
            }
        }
    }

    if (captured && histogram.size < 6) {
        for (i in 0 until count) {
            val e = hash01(xs[i] * 917.13f + ys[i] * 41.77f)
            colors[i] = fallbackPalette[(e * fallbackPalette.size).toInt().coerceIn(0, fallbackPalette.size - 1)]
        }
    }

    val bucketIndex = IntArray(count)
    val bucketOrderColors = ArrayList<Int>()
    val lookup = HashMap<Int, Int>()
    for (i in 0 until count) {
        val key = colors[i] and 0xFFE0E0E0.toInt()
        val existing = lookup[key]
        if (existing != null) {
            bucketIndex[i] = existing
        } else {
            val id = bucketOrderColors.size
            bucketOrderColors.add(colors[i])
            lookup[key] = id
            bucketIndex[i] = id
        }
    }

    val nb = bucketOrderColors.size
    val bucketStart = IntArray(nb)
    val bucketEnd = IntArray(nb)
    val counts = IntArray(nb)
    for (i in 0 until count) counts[bucketIndex[i]]++
    var acc = 0
    for (b in 0 until nb) {
        bucketStart[b] = acc
        acc += counts[b]
        bucketEnd[b] = acc
    }
    val cursor = bucketStart.copyOf()
    val order = IntArray(count)
    for (i in 0 until count) {
        val b = bucketIndex[i]
        order[cursor[b]] = i
        cursor[b]++
    }

    return DotField(
        count = count,
        cols = cols,
        rows = rows,
        xs = xs,
        ys = ys,
        delay = delay,
        life = life,
        dirX = dirX,
        dirY = dirY,
        swirl = swirl,
        order = order,
        bucketColor = bucketColorsOf(bucketOrderColors),
        bucketStart = bucketStart,
        bucketEnd = bucketEnd
    )
}

private fun bucketColorsOf(list: ArrayList<Int>): IntArray {
    val arr = IntArray(list.size)
    for (i in list.indices) arr[i] = list[i]
    return arr
}

/**
 * Draws the dissolving window: tens of thousands of microscopic glowing quantum dots,
 * with outer colored halos and glowing specular white cores, sweeping upward and floating.
 */
@Composable
private fun ParticleDissolveOverlay(
    field: DotField?,
    progress: () -> Float,
    modifier: Modifier = Modifier
) {
    val scratchHalo = remember { FloatArray(MAX_DISSOLVE_DOTS * 2) }
    val scratchCore = remember { FloatArray(MAX_DISSOLVE_DOTS * 2) }
    val haloPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
        }
    }
    val corePaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            color = android.graphics.Color.WHITE
        }
    }

    Canvas(modifier = modifier) {
        val f = field ?: return@Canvas
        val p = progress()
        if (p <= 0.001f || p >= 1.0f) return@Canvas

        val w = size.width
        val h = size.height
        val baseDot = (w / f.cols.toFloat()) * 0.76f

        // Smooth quadratic evaporation envelope: particles softly fade to absolute zero
        val globalFade = if (p < 0.58f) 1.0f else ((1.0f - p) / 0.42f).coerceIn(0f, 1f)
        val fadeAlpha = globalFade * globalFade

        var b = 0
        while (b < f.bucketColor.size) {
            val start = f.bucketStart[b]
            val end = f.bucketEnd[b]
            if (start < end) {
                var haloCount = 0
                var coreCount = 0
                var k = start
                while (k < end) {
                    val i = f.order[k]
                    val d = f.delay[i]
                    val l = f.life[i]
                    if (p > d && p < d + l) {
                        val localP = (p - d) / l
                        val alpha = kotlin.math.sin(localP * 3.14159f)
                        val drag = (1f - kotlin.math.exp(-2.6f * localP))
                        val spread = (0.04f + 0.09f * kotlin.math.abs(f.swirl[i])) * drag

                        val px = f.xs[i] * w + f.dirX[i] * spread * w
                        // Continuous upward lift (↑): particles ascend steadily towards the top of the screen
                        val py = f.ys[i] * h - (localP * 0.18f * h) + f.dirY[i] * spread * h

                        scratchHalo[haloCount] = px
                        scratchHalo[haloCount + 1] = py
                        haloCount += 2

                        if (alpha > 0.40f) {
                            scratchCore[coreCount] = px
                            scratchCore[coreCount + 1] = py
                            coreCount += 2
                        }
                    }
                    k++
                }

                if (haloCount > 0) {
                    haloPaint.color = f.bucketColor[b]
                    haloPaint.alpha = (255f * fadeAlpha).toInt().coerceIn(0, 255)
                    haloPaint.strokeWidth = baseDot * 1.35f
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawPoints(scratchHalo, 0, haloCount, haloPaint)
                    }
                }
                if (coreCount > 0) {
                    corePaint.alpha = (220f * fadeAlpha).toInt().coerceIn(0, 255)
                    corePaint.strokeWidth = baseDot * 0.60f
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawPoints(scratchCore, 0, coreCount, corePaint)
                    }
                }
            }
            b++
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
        // Speech Bubble Placed Above Card
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
                        metallicBorderBrush()
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
                // Machined Dark Obsidian Socket (ensures 100% emblem contrast in all themes)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(EmblemSocket)
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

        // Speech Bubble Placed Below Card
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
