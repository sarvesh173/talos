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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
 * TALOS WWDC25 True Liquid Glass Architecture
 * 
 * Authentic Apple Developer WWDC25 Liquid Glass Specifications:
 * 1. Molded Translucent Glass Pods & Convex Refractive Lenses.
 * 2. Prismatic Chromatic Sheen Borders (Cyan -> Azure -> Violet -> Coral).
 * 3. Ethereal Ceramic Glass Canvas with Ambient Prismatic Caustics.
 * 4. Embossed Frosted Liquid Glass [+] Action Lens.
 * 5. Single-Agent Direct Drill-Down & Emil Kowalski Damped Kinematics.
 */

data class AgentSession(
    val id: String,
    val title: String,
    val timestamp: String,
    val status: String = "Active",
    val turnsCount: Int = 1
)

data class CliToolAgent(
    val id: String,
    val name: String,
    val description: String,
    val iconRes: Int,
    val brandColor: Color,
    val coreGradients: List<Color>,
    val sessions: List<AgentSession>
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
    onOpenAgentChat: ((String, String, String, Int, Color) -> Unit)? = null
) {
    val context = LocalContext.current
    val currentAccentName by client.accentColor.collectAsState()
    val effectiveAccent = resolveAccentColor(currentAccentName, isDark)

    var selectedToolId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val dynamicSessions = remember {
        mutableStateMapOf<String, MutableList<AgentSession>>()
    }

    // Single-agent CLI tools with WWDC25 chromatic cores
    val cliTools = remember {
        listOf(
            CliToolAgent(
                id = "opencode",
                name = "OpenCode",
                description = "Pulse engine with max reasoning & stealth models",
                iconRes = R.drawable.ic_opencode,
                brandColor = Color(0xFF2563EB),
                coreGradients = listOf(Color(0xFF007AFF), Color(0xFF5856D6)),
                sessions = listOf(
                    AgentSession("ses_e5dcc117fffePxuWJE6NrZxZhE", "LandingScreen 3-tier navigation design", "Just now", "Active", 20),
                    AgentSession("ses_e5e5c8d1fffe6DqRGlZ47M0eTV", "Top 3 longest Kotlin files line counts", "3h ago", "Completed", 11),
                    AgentSession("ses_e5e861b56ffeUVpjWqUAcuVCFL", "TALOS Android: Liquid Glass, Compose performance", "3h ago", "Completed", 22),
                    AgentSession("ses_e5e69286affe4FWfpjXuY7eMoz", "Liquid Glass HIG audit", "3h ago", "Completed", 62),
                    AgentSession("ses_e5e83ee12ffeofZa3GLd50S5b4", "Touch target audit", "3h ago", "Completed", 182),
                    AgentSession("ses_e5e8131deffend7h5wuRHaMEgq", "Liquid Glass, 48dp targets, state hoisting", "4h ago", "Completed", 23)
                )
            ),
            CliToolAgent(
                id = "hermes",
                name = "Hermes Agent",
                description = "Nous Research autonomous multimodal gateway",
                iconRes = R.drawable.ic_hermes,
                brandColor = Color(0xFF7C4DFF),
                coreGradients = listOf(Color(0xFF7C4DFF), Color(0xFFAF52DE)),
                sessions = listOf(
                    AgentSession("h_sess_1", "Kotlin Compose hierarchy cleanup", "12m ago", "Active", 9),
                    AgentSession("h_sess_2", "Secret scope & memory quarantine", "1h ago", "Completed", 5),
                    AgentSession("h_sess_3", "Screenshot coordinate comparison", "3h ago", "Completed", 14),
                    AgentSession("h_sess_4", "Multi-unit workstream pipeline", "6h ago", "Completed", 22)
                )
            ),
            CliToolAgent(
                id = "claudecode",
                name = "Claude Code",
                description = "Anthropic architecture & synthesis suite",
                iconRes = R.drawable.ic_claude,
                brandColor = Color(0xFFD97757),
                coreGradients = listOf(Color(0xFFFF9500), Color(0xFFFF2D55)),
                sessions = listOf(
                    AgentSession("c_sess_1", "Apple design spec decomposition", "30m ago", "Active", 4),
                    AgentSession("c_sess_2", "UI touch target red-green matrix", "2h ago", "Completed", 7),
                    AgentSession("c_sess_3", "State hoisting refactor pass", "4h ago", "Completed", 3)
                )
            ),
            CliToolAgent(
                id = "codex",
                name = "Codex CLI",
                description = "OpenAI high-throughput code synthesis",
                iconRes = R.drawable.ic_codex,
                brandColor = Color(0xFF10A37F),
                coreGradients = listOf(Color(0xFF34C759), Color(0xFF00C7BE)),
                sessions = listOf(
                    AgentSession("cx_sess_1", "Compose vector drawable exporter", "1h ago", "Active", 2),
                    AgentSession("cx_sess_2", "Inference latency profiling", "5h ago", "Completed", 11)
                )
            ),
            CliToolAgent(
                id = "omnirush",
                name = "OmniRush",
                description = "Sandboxed autonomous agent orchestrator",
                iconRes = R.drawable.ic_omnirush,
                brandColor = Color(0xFFA855F7),
                coreGradients = listOf(Color(0xFFAF52DE), Color(0xFFFF2D55)),
                sessions = listOf(
                    AgentSession("om_sess_1", "Rootfs sandbox isolation pass", "40m ago", "Active", 5),
                    AgentSession("om_sess_2", "Parallel fan-out dispatch", "3h ago", "Completed", 16)
                )
            )
        )
    }

    LaunchedEffect(cliTools) {
        cliTools.forEach { tool ->
            if (!dynamicSessions.containsKey(tool.id)) {
                dynamicSessions[tool.id] = tool.sessions.toMutableList()
            }
        }
    }

    val currentTool = remember(selectedToolId, cliTools) {
        cliTools.firstOrNull { it.id == selectedToolId }
    }

    BackHandler(enabled = selectedToolId != null) {
        TalosHaptics.sendHaptic(context)
        selectedToolId = null
    }

    val filteredTools = remember(searchQuery, cliTools) {
        if (searchQuery.isBlank()) cliTools
        else cliTools.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    // WWDC25 Ethereal Ceramic Canvas with Prismatic Caustics
    val canvasBg = if (isDark) Color(0xFF0B0C10) else Color(0xFFF6F8FA)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(canvasBg)
    ) {
        // ==============================================================
        // WWDC25 MOLDED GLASS GEOMETRY & OPTICAL REFRACTION CONTOURS
        // ==============================================================
        Canvas(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val w = size.width
            val h = size.height
            val glassOutline = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
            val glassRim = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)

            // Upper molded optical lens pod contour
            drawRoundRect(
                color = glassOutline,
                topLeft = Offset(w * 0.08f, h * 0.16f),
                size = androidx.compose.ui.geometry.Size(120.dp.toPx(), 44.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx()),
                style = Stroke(width = 1.2.dp.toPx())
            )

            // Prismatic Chromatic refraction line (WWDC25 spectrum track)
            drawLine(
                brush = Brush.linearGradient(
                    listOf(
                        Color(0xFF34C759).copy(alpha = if (isDark) 0.35f else 0.50f),
                        Color(0xFF007AFF).copy(alpha = if (isDark) 0.40f else 0.55f),
                        Color(0xFFAF52DE).copy(alpha = if (isDark) 0.38f else 0.50f),
                        Color(0xFFFF2D55).copy(alpha = if (isDark) 0.35f else 0.45f)
                    ),
                    start = Offset(w * 0.35f, h * 0.18f),
                    end = Offset(w * 0.92f, h * 0.18f)
                ),
                start = Offset(w * 0.35f, h * 0.18f),
                end = Offset(w * 0.92f, h * 0.18f),
                strokeWidth = 3.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            // Molded Glass Circular Lens Orb
            drawCircle(
                color = glassRim,
                radius = 48.dp.toPx(),
                center = Offset(w * 0.5f, h * 0.74f),
                style = Stroke(width = 1.2.dp.toPx())
            )
            drawCircle(
                color = glassOutline,
                radius = 20.dp.toPx(),
                center = Offset(w * 0.5f, h * 0.74f),
                style = Stroke(width = 1.2.dp.toPx())
            )
        }

        // ==============================================================
        // MAIN SURFACE WITH FLUID ANIMATED KINEMATICS
        // ==============================================================
        AnimatedContent(
            targetState = Pair(selectedToolId, currentTool),
            transitionSpec = {
                val isAdvancing = targetState.first != null
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
        ) { (toolId, tool) ->
            if (toolId == null || tool == null) {
                // ======================================================
                // ROOT HUB: WWDC25 MOLDED LIQUID GLASS PODS
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

                            // Molded Glass Capsule Badge
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isDark) Color(0xFF1C1D24).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.90f),
                                border = BorderStroke(
                                    1.dp,
                                    Brush.verticalGradient(
                                        listOf(
                                            if (isDark) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.90f),
                                            if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)
                                        )
                                    )
                                ),
                                shadowElevation = 1.dp,
                                modifier = Modifier.height(24.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 12.dp),
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
                        val sessionsCount = dynamicSessions[cliTool.id]?.size ?: cliTool.sessions.size

                        // WWDC25 Liquid Glass Card with Prismatic Specular Rim
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isDark) Color(0xFF141620).copy(alpha = 0.82f) else Color.White.copy(alpha = 0.90f),
                            border = BorderStroke(
                                1.2.dp,
                                Brush.linearGradient(
                                    listOf(
                                        Color.White.copy(alpha = if (isDark) 0.40f else 0.95f),
                                        cliTool.brandColor.copy(alpha = 0.30f),
                                        Color(0xFFAF52DE).copy(alpha = 0.20f),
                                        Color.White.copy(alpha = if (isDark) 0.08f else 0.30f)
                                    )
                                )
                            ),
                            shadowElevation = 3.dp,
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
                                // Molded Convex Glass Lens containing SVG Logo
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    cliTool.brandColor.copy(alpha = if (isDark) 0.25f else 0.18f),
                                                    cliTool.brandColor.copy(alpha = if (isDark) 0.10f else 0.08f)
                                                )
                                            )
                                        )
                                        .border(
                                            BorderStroke(
                                                1.dp,
                                                Brush.verticalGradient(
                                                    listOf(
                                                        Color.White.copy(alpha = if (isDark) 0.45f else 0.90f),
                                                        cliTool.brandColor.copy(alpha = 0.35f)
                                                    )
                                                )
                                            ),
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

                                        // Liquid Core Pill with Glowing Green Dot
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = cliTool.brandColor.copy(alpha = if (isDark) 0.18f else 0.10f),
                                            border = BorderStroke(0.8.dp, cliTool.brandColor.copy(alpha = 0.25f)),
                                            modifier = Modifier.height(20.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp),
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
                                                    text = "$sessionsCount sessions",
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
            } else {
                // ======================================================
                // DIRECT SESSIONS & WWDC25 LIQUID GLASS CREATE ACTION
                // ======================================================
                val sessions = dynamicSessions[tool.id] ?: tool.sessions

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

                        // WWDC25 Molded Liquid Glass "Create session" Pod
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = tool.brandColor,
                            border = BorderStroke(
                                1.2.dp,
                                Brush.linearGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.60f),
                                        Color(0xFF34C759).copy(alpha = 0.30f),
                                        Color(0xFFAF52DE).copy(alpha = 0.25f),
                                        Color.White.copy(alpha = 0.15f)
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
                                    val newTitle = "New ${tool.name} session"
                                    val newSess = AgentSession(
                                        id = newId,
                                        title = newTitle,
                                        timestamp = "$now (Just created)",
                                        status = "Active",
                                        turnsCount = 1
                                    )
                                    dynamicSessions.getOrPut(tool.id) { mutableListOf() }.add(0, newSess)
                                    onOpenAgentChat?.invoke(newId, newTitle, tool.name, tool.iconRes, tool.brandColor)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp, horizontal = 20.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Embossed Glass Lens Icon Container
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.25f))
                                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.50f)), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Create session",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

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
                                color = if (isDark) Color(0xFF141620).copy(alpha = 0.50f) else Color.White.copy(alpha = 0.70f),
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
                                color = if (isDark) Color(0xFF141620).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.92f),
                                border = BorderStroke(
                                    1.dp,
                                    Brush.verticalGradient(
                                        listOf(
                                            if (isDark) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.85f),
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
                                        onOpenAgentChat?.invoke(session.id, session.title, tool.name, tool.iconRes, tool.brandColor)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 56.dp)
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // WWDC25 Molded Jelly Status Indicator Dot
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (session.status == "Active") StatusGreen else Color(0xFF8E8E93)
                                            )
                                            .border(
                                                BorderStroke(1.dp, Color.White.copy(alpha = 0.40f)),
                                                CircleShape
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
        // TOP FLOATING WWDC25 LIQUID GLASS CAPSULE BAR
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
                color = if (isDark) Color(0xFF161822).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.94f),
                border = BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            if (isDark) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.95f),
                            if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                        )
                    )
                ),
                shadowElevation = 4.dp
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
                                .background(if (isDark) Color(0xFF252733) else Color(0xFFE5E5EA))
                                .applePressable(0.94f) {
                                    TalosHaptics.sendHaptic(context)
                                    selectedToolId = null
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
                        // Chat Button: Opens AppleAgentChatScreen
                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = 34.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .applePressable(0.94f) {
                                    TalosHaptics.sendHaptic(context)
                                    val defaultTool = cliTools.first()
                                    onOpenAgentChat?.invoke("sess_active", "Active conversation", defaultTool.name, defaultTool.iconRes, defaultTool.brandColor)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = TalosIcons.TalosChat,
                                contentDescription = "Chat",
                                tint = if (isDark) Color.White.copy(alpha = 0.60f) else Color(0xFF1D1D1F).copy(alpha = 0.50f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Agents Button: Stays on Hub
                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = 34.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isDark) Color(0xFF252733) else Color(0xFF1D1D1F))
                                .applePressable(0.94f) {
                                    TalosHaptics.sendHaptic(context)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = TalosIcons.TalosHub,
                                contentDescription = "Agents",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isDark) Color(0xFF161822).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.94f),
                border = BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            if (isDark) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.95f),
                            if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                        )
                    )
                ),
                shadowElevation = 4.dp
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
        // FLOATING BOTTOM WWDC25 LIQUID GLASS CONTROLS
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
                color = if (isDark) Color(0xFF141620).copy(alpha = 0.88f) else Color.White.copy(alpha = 0.94f),
                border = BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            if (isDark) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.95f),
                            if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                        )
                    )
                ),
                shadowElevation = 5.dp,
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
                                    text = if (selectedToolId != null) "Search active sessions" else "Search cli tools and engines",
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

            // WWDC25 Embossed Molded Glass Circular Action Button
            Surface(
                shape = CircleShape,
                color = if (isDark) Color(0xFF222533).copy(alpha = 0.92f) else Color(0xFFE2E4EC),
                border = BorderStroke(
                    1.2.dp,
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = if (isDark) 0.50f else 0.95f),
                            Color(0xFF007AFF).copy(alpha = 0.30f),
                            Color(0xFFAF52DE).copy(alpha = 0.25f),
                            Color.White.copy(alpha = if (isDark) 0.10f else 0.40f)
                        )
                    )
                ),
                shadowElevation = 5.dp,
                modifier = Modifier
                    .size(54.dp)
                    .applePressable(0.92f) {
                        TalosHaptics.sendHaptic(context)
                        val tool = currentTool ?: cliTools.first()
                        val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                        val newId = "sess_${System.currentTimeMillis()}"
                        val newTitle = "New ${tool.name} session"
                        val newSess = AgentSession(
                            id = newId,
                            title = newTitle,
                            timestamp = "$now (Just created)",
                            status = "Active",
                            turnsCount = 1
                        )
                        dynamicSessions.getOrPut(tool.id) { mutableListOf() }.add(0, newSess)
                        onOpenAgentChat?.invoke(newId, newTitle, tool.name, tool.iconRes, tool.brandColor)
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
