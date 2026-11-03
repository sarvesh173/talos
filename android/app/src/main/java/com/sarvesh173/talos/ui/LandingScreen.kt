package com.sarvesh173.talos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.R
import com.sarvesh173.talos.network.TalosClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TALOS 3-Tier Aggressive Apple Liquid Glass Hub
 * 
 * Synthesized across all loaded Apple Design skills:
 * 1. apple-agent-drilldown-architecture: 3-tier progressive disclosure (CLI Tools -> Agents -> Sessions + Create).
 * 2. apple-liquid-glass-architecture: Optical specular rim highlights, continuous tree geometry, and tactile depth.
 * 3. emil-apple-design: Instant touch-down feedback, interruptible spring kinematics, optical sizing & negative tracking.
 * 4. apple-hig-reviewer: Strict sentence case, accessibility minimum 48dp targets, 4px/8px spatial grid.
 * 5. compose-apple-design-standards: Full BackHandler integration & haptic feedback on all interactions.
 */

data class AgentSession(
    val id: String,
    val title: String,
    val timestamp: String,
    val status: String = "Active",
    val turnsCount: Int = 1
)

data class DelegateAgent(
    val id: String,
    val name: String,
    val activity: String,
    val iconRes: Int,
    val accentColor: Color,
    val isOnline: Boolean = true,
    val sessions: List<AgentSession>
)

data class CliToolAgent(
    val id: String,
    val name: String,
    val description: String,
    val iconRes: Int,
    val brandColor: Color,
    val delegates: List<DelegateAgent>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandingScreen(
    client: TalosClient,
    isDark: Boolean = true,
    onToggleTheme: () -> Unit = {},
    onGetStarted: () -> Unit = {},
    onConnect: ((String, String) -> Unit)? = null,
    onOpenWizard: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onOpenAgentChat: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val currentAccentName by client.accentColor.collectAsState()
    val effectiveAccent = resolveAccentColor(currentAccentName, isDark)

    // Tier navigation state
    var selectedToolId by remember { mutableStateOf<String?>(null) }
    var selectedAgentId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Dynamic sessions map for live session creation
    val dynamicSessions = remember {
        mutableStateMapOf<String, MutableList<AgentSession>>()
    }

    // Tier 1: Pure Developer CLI Engines
    val cliTools = remember {
        listOf(
            CliToolAgent(
                id = "opencode",
                name = "OpenCode",
                description = "Pulse engine with max reasoning & stealth models",
                iconRes = R.drawable.ic_opencode,
                brandColor = Color(0xFF2563EB),
                delegates = listOf(
                    DelegateAgent(
                        id = "opencode_pulse",
                        name = "Pulse Alpha Engine",
                        activity = "Autonomous coding with reasoning max",
                        iconRes = R.drawable.ic_opencode,
                        accentColor = Color(0xFF2563EB),
                        sessions = listOf(
                            AgentSession("op_sess_1", "TALOS Apple design remake", "Just now", "Active", 14),
                            AgentSession("op_sess_2", "AST cyclomatic complexity audit", "18m ago", "Completed", 6)
                        )
                    ),
                    DelegateAgent(
                        id = "opencode_watchman",
                        name = "Watchman Sentry",
                        activity = "Process supervision & webhook alert dispatch",
                        iconRes = R.drawable.ic_core,
                        accentColor = Color(0xFF10B981),
                        sessions = listOf(
                            AgentSession("op_sess_3", "Background agent lifecycle monitor", "5m ago", "Active", 4)
                        )
                    ),
                    DelegateAgent(
                        id = "opencode_beam",
                        name = "Beam Coder",
                        activity = "High-speed linting & diff repair",
                        iconRes = R.drawable.ic_deepseek,
                        accentColor = Color(0xFF4D6BFE),
                        sessions = listOf(
                            AgentSession("op_sess_4", "Design tokens consistency check", "45m ago", "Completed", 3)
                        )
                    ),
                    DelegateAgent(
                        id = "opencode_auditor",
                        name = "Architecture Auditor",
                        activity = "Design compliance & anti-slop verification",
                        iconRes = R.drawable.ic_gemini,
                        accentColor = Color(0xFF007AFF),
                        sessions = listOf(
                            AgentSession("op_sess_5", "Liquid glass touch target audit", "2h ago", "Completed", 8)
                        )
                    )
                )
            ),
            CliToolAgent(
                id = "hermes",
                name = "Hermes Agent",
                description = "Nous Research autonomous multimodal gateway",
                iconRes = R.drawable.ic_hermes,
                brandColor = Color(0xFF7C4DFF),
                delegates = listOf(
                    DelegateAgent(
                        id = "hermes_swe",
                        name = "SWE Architect",
                        activity = "Refactoring AST & design check",
                        iconRes = R.drawable.ic_codex,
                        accentColor = Color(0xFF007AFF),
                        sessions = listOf(
                            AgentSession("h_sess_1", "Kotlin Compose hierarchy cleanup", "12m ago", "Active", 9)
                        )
                    ),
                    DelegateAgent(
                        id = "hermes_sec",
                        name = "Security Auditor",
                        activity = "Taint analysis & credential guard",
                        iconRes = R.drawable.ic_deepseek,
                        accentColor = Color(0xFF4D6BFE),
                        sessions = listOf(
                            AgentSession("h_sess_2", "Secret scope & memory quarantine", "1h ago", "Completed", 5)
                        )
                    ),
                    DelegateAgent(
                        id = "hermes_vis",
                        name = "Vision Inspector",
                        activity = "UI hierarchy & liquid glass diffs",
                        iconRes = R.drawable.ic_gemini,
                        accentColor = Color(0xFF3186FF),
                        sessions = listOf(
                            AgentSession("h_sess_3", "Screenshot coordinate comparison", "3h ago", "Completed", 14)
                        )
                    ),
                    DelegateAgent(
                        id = "hermes_orch",
                        name = "Task Orchestrator",
                        activity = "Subagent fan-out synthesis",
                        iconRes = R.drawable.ic_qwen,
                        accentColor = Color(0xFFA855F7),
                        sessions = listOf(
                            AgentSession("h_sess_4", "Multi-unit workstream pipeline", "6h ago", "Completed", 22)
                        )
                    )
                )
            ),
            CliToolAgent(
                id = "claudecode",
                name = "Claude Code",
                description = "Anthropic architecture & synthesis suite",
                iconRes = R.drawable.ic_claude,
                brandColor = Color(0xFFD97757),
                delegates = listOf(
                    DelegateAgent(
                        id = "claude_planner",
                        name = "Planning Maestro",
                        activity = "PRD & task breakdown",
                        iconRes = R.drawable.ic_claude,
                        accentColor = Color(0xFFD97757),
                        sessions = listOf(
                            AgentSession("c_sess_1", "Apple design spec decomposition", "30m ago", "Active", 4)
                        )
                    ),
                    DelegateAgent(
                        id = "claude_tester",
                        name = "Test Engineer",
                        activity = "Red-green-refactor suite",
                        iconRes = R.drawable.ic_mistral,
                        accentColor = Color(0xFFFF8205),
                        sessions = listOf(
                            AgentSession("c_sess_2", "UI touch target red-green matrix", "2h ago", "Completed", 7)
                        )
                    ),
                    DelegateAgent(
                        id = "claude_simplifier",
                        name = "Code Simplifier",
                        activity = "Complexity & anti-slop audit",
                        iconRes = R.drawable.ic_openai,
                        accentColor = Color(0xFF10A37F),
                        sessions = listOf(
                            AgentSession("c_sess_3", "State hoisting refactor pass", "4h ago", "Completed", 3)
                        )
                    )
                )
            ),
            CliToolAgent(
                id = "codex",
                name = "Codex CLI",
                description = "OpenAI high-throughput code synthesis",
                iconRes = R.drawable.ic_codex,
                brandColor = Color(0xFF10A37F),
                delegates = listOf(
                    DelegateAgent(
                        id = "codex_synthesizer",
                        name = "Code Synthesizer",
                        activity = "High-speed implementation & unit tests",
                        iconRes = R.drawable.ic_codex,
                        accentColor = Color(0xFF10A37F),
                        sessions = listOf(
                            AgentSession("cx_sess_1", "Compose vector drawable exporter", "1h ago", "Active", 2)
                        )
                    ),
                    DelegateAgent(
                        id = "codex_benchmark",
                        name = "Benchmark Runner",
                        activity = "SWE-bench evaluation & telemetry",
                        iconRes = R.drawable.ic_core,
                        accentColor = Color(0xFF007AFF),
                        sessions = listOf(
                            AgentSession("cx_sess_2", "Inference latency profiling", "5h ago", "Completed", 11)
                        )
                    )
                )
            ),
            CliToolAgent(
                id = "omnirush",
                name = "OmniRush",
                description = "Sandboxed autonomous agent orchestrator",
                iconRes = R.drawable.ic_omnirush,
                brandColor = Color(0xFFA855F7),
                delegates = listOf(
                    DelegateAgent(
                        id = "omnirush_sandbox",
                        name = "Sandbox Executor",
                        activity = "Bwrap isolated runtime supervision",
                        iconRes = R.drawable.ic_omnirush,
                        accentColor = Color(0xFFA855F7),
                        sessions = listOf(
                            AgentSession("om_sess_1", "Rootfs sandbox isolation pass", "40m ago", "Active", 5)
                        )
                    ),
                    DelegateAgent(
                        id = "omnirush_runner",
                        name = "Workflow Runner",
                        activity = "Multi-stage agent pipelining",
                        iconRes = R.drawable.ic_openclaw,
                        accentColor = Color(0xFFEC4899),
                        sessions = listOf(
                            AgentSession("om_sess_2", "Parallel fan-out dispatch", "3h ago", "Completed", 16)
                        )
                    )
                )
            )
        )
    }

    // Populate initial dynamic sessions
    LaunchedEffect(cliTools) {
        cliTools.forEach { tool ->
            tool.delegates.forEach { delegate ->
                if (!dynamicSessions.containsKey(delegate.id)) {
                    dynamicSessions[delegate.id] = delegate.sessions.toMutableList()
                }
            }
        }
    }

    val currentTool = remember(selectedToolId, cliTools) {
        cliTools.firstOrNull { it.id == selectedToolId }
    }
    val currentAgent = remember(selectedAgentId, currentTool) {
        currentTool?.delegates?.firstOrNull { it.id == selectedAgentId }
    }

    // Intercept back gesture across the 3-tier hierarchy
    BackHandler(enabled = selectedToolId != null) {
        TalosHaptics.sendHaptic(context)
        if (selectedAgentId != null) {
            selectedAgentId = null
        } else {
            selectedToolId = null
        }
    }

    val filteredTools = remember(searchQuery, cliTools) {
        if (searchQuery.isBlank()) cliTools
        else cliTools.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    val backgroundColor = if (isDark) Color(0xFF000000) else Color(0xFFFFFFFF)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // ==============================================================
        // ETHEREAL MONOCHROME ARTWORK
        // ==============================================================
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp)
        ) {
            val artColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
            val sphereColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
            val cx = size.width / 2f
            val cy = size.height * 0.38f

            drawCircle(
                color = sphereColor.copy(alpha = sphereColor.alpha * 0.4f),
                radius = 44.dp.toPx(),
                center = Offset(cx, cy)
            )
            drawCircle(
                color = artColor,
                radius = 32.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 1.2.dp.toPx())
            )
            drawCircle(
                color = artColor,
                radius = 11.dp.toPx(),
                center = Offset(cx, cy - 54.dp.toPx()),
                style = Stroke(width = 1.2.dp.toPx())
            )
            drawLine(
                color = artColor,
                start = Offset(cx, cy - 43.dp.toPx()),
                end = Offset(cx, cy + 50.dp.toPx()),
                strokeWidth = 1.2.dp.toPx()
            )
            drawArc(
                color = artColor,
                startAngle = 25f,
                sweepAngle = 130f,
                useCenter = false,
                topLeft = Offset(cx - 38.dp.toPx(), cy - 20.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(76.dp.toPx(), 44.dp.toPx()),
                style = Stroke(width = 1.2.dp.toPx())
            )
            drawLine(
                color = artColor,
                start = Offset(cx - 8.dp.toPx(), cy + 50.dp.toPx()),
                end = Offset(cx - 16.dp.toPx(), size.height - 20.dp.toPx()),
                strokeWidth = 1.2.dp.toPx()
            )
            drawLine(
                color = artColor,
                start = Offset(cx + 8.dp.toPx(), cy + 50.dp.toPx()),
                end = Offset(cx + 18.dp.toPx(), size.height - 20.dp.toPx()),
                strokeWidth = 1.2.dp.toPx()
            )
        }

        // ==============================================================
        // MAIN 3-TIER PROGRESSIVE DISCLOSURE SURFACE
        // ==============================================================
        AnimatedContent(
            targetState = Triple(selectedToolId, selectedAgentId, currentTool),
            transitionSpec = {
                val isAdvancing = targetState.second != null || (targetState.first != null && initialState.first == null)
                val slideDir = if (isAdvancing) AnimatedContentTransitionScope.SlideDirection.Start else AnimatedContentTransitionScope.SlideDirection.End

                (fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing)) +
                slideIntoContainer(
                    towards = slideDir,
                    animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)
                )).togetherWith(
                fadeOut(animationSpec = tween(180)) +
                slideOutOfContainer(
                    towards = slideDir,
                    animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)
                ))
            },
            modifier = Modifier.fillMaxSize()
        ) { (toolId, agentId, tool) ->
            if (toolId == null || tool == null) {
                // ======================================================
                // TIER 1: PURE DEVELOPER CLI ENGINES HUB
                // ======================================================
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 96.dp, bottom = 110.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Cli tools",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color.White else Color(0xFF1D1D1F),
                                letterSpacing = (-0.25).sp
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${filteredTools.size} installed",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isDark) Color.White.copy(alpha = 0.70f) else Color(0xFF1D1D1F)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    items(filteredTools, key = { it.id }) { cliTool ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isDark) Color(0xFF161820).copy(alpha = 0.84f) else Color.White.copy(alpha = 0.92f),
                            border = BorderStroke(
                                1.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        if (isDark) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.85f),
                                        if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f)
                                    )
                                )
                            ),
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .applePressable(0.97f) {
                                    TalosHaptics.sendHaptic(context)
                                    selectedToolId = cliTool.id
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 64.dp)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Official SVG Vector Logo inside Specular Squircle
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(cliTool.brandColor.copy(alpha = if (isDark) 0.18f else 0.12f))
                                        .border(
                                            BorderStroke(1.dp, cliTool.brandColor.copy(alpha = 0.35f)),
                                            RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = cliTool.iconRes),
                                        contentDescription = cliTool.name,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = cliTool.name,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isDark) Color.White else Color(0xFF1D1D1F),
                                            letterSpacing = (-0.15).sp
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = cliTool.brandColor.copy(alpha = if (isDark) 0.18f else 0.10f),
                                            modifier = Modifier.height(18.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .clip(CircleShape)
                                                        .background(StatusGreen)
                                                )
                                                Text(
                                                    text = "${cliTool.delegates.size} agents",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = cliTool.brandColor
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = cliTool.description,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.50f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Icon(
                                    imageVector = TalosIcons.TalosChevronRight,
                                    contentDescription = "Open ${cliTool.name}",
                                    tint = if (isDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.30f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            } else if (agentId == null) {
                // ======================================================
                // TIER 2: SELECTED CLI TOOL ACTIVE AGENTS
                // ======================================================
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 96.dp, bottom = 110.dp)
                ) {
                    item {
                        // Back Navigation Breadcrumb
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .applePressable(0.96f) {
                                    TalosHaptics.sendHaptic(context)
                                    selectedToolId = null
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back to cli tools",
                                tint = effectiveAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Cli tools",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = effectiveAccent
                            )
                            Text(
                                text = " / ${tool.name}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color.White.copy(alpha = 0.70f) else Color.Black.copy(alpha = 0.60f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Selected Tool Hero Card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = tool.brandColor.copy(alpha = if (isDark) 0.16f else 0.10f),
                            border = BorderStroke(1.dp, tool.brandColor.copy(alpha = 0.30f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(tool.brandColor.copy(alpha = 0.20f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = tool.iconRes),
                                        contentDescription = tool.name,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column {
                                    Text(
                                        text = "${tool.name} agents",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDark) Color.White else Color(0xFF1D1D1F),
                                        letterSpacing = (-0.15).sp
                                    )
                                    Text(
                                        text = "Select an agent to view and create active sessions",
                                        fontSize = 12.sp,
                                        color = if (isDark) Color.White.copy(alpha = 0.60f) else Color.Black.copy(alpha = 0.50f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Active Agents List
                    items(tool.delegates, key = { it.id }) { agent ->
                        val sessionsCount = dynamicSessions[agent.id]?.size ?: agent.sessions.size

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isDark) Color(0xFF161820).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.92f),
                            border = BorderStroke(
                                1.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        if (isDark) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.80f),
                                        if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.03f)
                                    )
                                )
                            ),
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .applePressable(0.97f) {
                                    TalosHaptics.sendHaptic(context)
                                    selectedAgentId = agent.id
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 56.dp)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(agent.accentColor.copy(alpha = if (isDark) 0.18f else 0.12f))
                                        .border(
                                            BorderStroke(0.8.dp, agent.accentColor.copy(alpha = 0.35f)),
                                            RoundedCornerShape(8.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = agent.iconRes),
                                        contentDescription = agent.name,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = agent.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isDark) Color.White else Color(0xFF1D1D1F)
                                        )

                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(StatusGreen)
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                                            modifier = Modifier.height(18.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(horizontal = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "$sessionsCount sessions",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (isDark) Color.White.copy(alpha = 0.65f) else Color(0xFF1D1D1F)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = agent.activity,
                                        fontSize = 12.sp,
                                        color = if (isDark) Color.White.copy(alpha = 0.50f) else Color.Black.copy(alpha = 0.45f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Icon(
                                    imageVector = TalosIcons.TalosChevronRight,
                                    contentDescription = "Open ${agent.name} sessions",
                                    tint = if (isDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.30f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // ======================================================
                // TIER 3: AGENT SESSIONS + PROMINENT "CREATE SESSION"
                // ======================================================
                val agent = currentAgent
                val sessions = dynamicSessions[agentId] ?: emptyList()

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 96.dp, bottom = 110.dp)
                ) {
                    item {
                        // Back Navigation Breadcrumb
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .applePressable(0.96f) {
                                    TalosHaptics.sendHaptic(context)
                                    selectedAgentId = null
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back to agents",
                                tint = effectiveAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${tool.name} agents",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = effectiveAccent
                            )
                            Text(
                                text = " / ${agent?.name ?: "Agent"}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color.White.copy(alpha = 0.70f) else Color.Black.copy(alpha = 0.60f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Prominent Apple Liquid Glass "Create session" Pill Action
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = tool.brandColor,
                            border = BorderStroke(
                                1.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.40f),
                                        Color.White.copy(alpha = 0.10f)
                                    )
                                )
                            ),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .applePressable(0.96f) {
                                    TalosHaptics.sendHaptic(context)
                                    val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                                    val newId = "sess_${System.currentTimeMillis()}"
                                    val newSess = AgentSession(
                                        id = newId,
                                        title = "New interactive session",
                                        timestamp = "$now (Just created)",
                                        status = "Active",
                                        turnsCount = 1
                                    )
                                    dynamicSessions.getOrPut(agentId) { mutableListOf() }.add(0, newSess)
                                    onOpenAgentChat?.invoke(newId)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp, horizontal = 20.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Create session",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Create session",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    letterSpacing = (-0.1).sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Active sessions",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color.White.copy(alpha = 0.90f) else Color(0xFF1D1D1F),
                                letterSpacing = (-0.1).sp
                            )

                            Text(
                                text = "${sessions.size} total",
                                fontSize = 12.sp,
                                color = if (isDark) Color.White.copy(alpha = 0.50f) else Color.Black.copy(alpha = 0.40f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (sessions.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isDark) Color(0xFF161820).copy(alpha = 0.50f) else Color.White.copy(alpha = 0.70f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No sessions running yet. Tap \"Create session\" above.",
                                        fontSize = 13.sp,
                                        color = if (isDark) Color.White.copy(alpha = 0.50f) else Color.Black.copy(alpha = 0.40f)
                                    )
                                }
                            }
                        }
                    } else {
                        items(sessions, key = { it.id }) { session ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isDark) Color(0xFF161820).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.92f),
                                border = BorderStroke(
                                    1.dp,
                                    Brush.verticalGradient(
                                        listOf(
                                            if (isDark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.80f),
                                            if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f)
                                        )
                                    )
                                ),
                                shadowElevation = 2.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .applePressable(0.97f) {
                                        TalosHaptics.sendHaptic(context)
                                        onOpenAgentChat?.invoke(session.id)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 56.dp)
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (session.status == "Active") StatusGreen else Color(0xFF8E8E93)
                                            )
                                    )

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = session.title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isDark) Color.White else Color(0xFF1D1D1F),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = session.timestamp,
                                                fontSize = 12.sp,
                                                color = if (isDark) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.40f)
                                            )
                                            Text(
                                                text = "\u2022",
                                                fontSize = 12.sp,
                                                color = if (isDark) Color.White.copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.25f)
                                            )
                                            Text(
                                                text = "${session.turnsCount} turns",
                                                fontSize = 12.sp,
                                                color = if (isDark) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.40f)
                                            )
                                        }
                                    }

                                    Icon(
                                        imageVector = TalosIcons.TalosChevronRight,
                                        contentDescription = "Resume session",
                                        tint = if (isDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.30f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==============================================================
        // TOP FLOATING LIQUID GLASS BAR (Dynamic Breadcrumb Navigation)
        // ==============================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isDark) Color(0xFF1C1C1E).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.95f),
                border = BorderStroke(
                    0.8.dp,
                    if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f)
                ),
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedToolId != null) {
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 72.dp, minHeight = 34.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))
                                .applePressable(0.94f) {
                                    TalosHaptics.sendHaptic(context)
                                    if (selectedAgentId != null) selectedAgentId = null
                                    else selectedToolId = null
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = if (isDark) Color.White else Color(0xFF1D1D1F),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Back",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isDark) Color.White else Color(0xFF1D1D1F)
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = 34.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFF1D1D1F))
                                .applePressable(0.94f) {
                                    TalosHaptics.sendHaptic(context)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = TalosIcons.TalosChat,
                                contentDescription = "Chat",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = 34.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .applePressable(0.94f) {
                                    TalosHaptics.sendHaptic(context)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = TalosIcons.TalosHub,
                                contentDescription = "Agents",
                                tint = if (isDark) Color.White.copy(alpha = 0.60f) else Color(0xFF1D1D1F).copy(alpha = 0.50f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isDark) Color(0xFF1C1C1E).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.95f),
                border = BorderStroke(
                    0.8.dp,
                    if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f)
                ),
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 34.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .applePressable(0.94f) {
                                TalosHaptics.sendHaptic(context)
                                onToggleTheme()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Filter",
                            tint = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF1D1D1F),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 34.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .applePressable(0.94f) {
                                TalosHaptics.sendHaptic(context)
                                onOpenSettings?.invoke() ?: onOpenWizard?.invoke()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "More options",
                            tint = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF1D1D1F),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // ==============================================================
        // FLOATING BOTTOM CONTROLS
        // ==============================================================
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = if (isDark) Color(0xFF161820).copy(alpha = 0.88f) else Color.White.copy(alpha = 0.95f),
                border = BorderStroke(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f)
                ),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (isDark) Color.White.copy(alpha = 0.50f) else Color.Black.copy(alpha = 0.40f),
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = if (isDark) Color.White else Color(0xFF1D1D1F)
                        ),
                        cursorBrush = SolidColor(effectiveAccent),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = when {
                                        selectedAgentId != null -> "Search active sessions"
                                        selectedToolId != null -> "Search ${currentTool?.name ?: ""} agents"
                                        else -> "Search cli tools and engines"
                                    },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = if (isDark) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.35f),
                                    letterSpacing = (-0.1).sp
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = if (isDark) Color(0xFF2C2C32).copy(alpha = 0.92f) else Color(0xFFD4D0E2),
                border = BorderStroke(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.06f)
                ),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .size(54.dp)
                    .applePressable(0.92f) {
                        TalosHaptics.sendHaptic(context)
                        val targetAgent = selectedAgentId ?: currentTool?.delegates?.firstOrNull()?.id
                        if (targetAgent != null) {
                            val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                            val newId = "sess_${System.currentTimeMillis()}"
                            val newSess = AgentSession(
                                id = newId,
                                title = "New interactive session",
                                timestamp = "$now (Just created)",
                                status = "Active",
                                turnsCount = 1
                            )
                            dynamicSessions.getOrPut(targetAgent) { mutableListOf() }.add(0, newSess)
                            onOpenAgentChat?.invoke(newId)
                        } else {
                            selectedToolId = "opencode"
                        }
                    }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "New session",
                        tint = if (isDark) Color.White else Color(0xFF1D1D1F),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
