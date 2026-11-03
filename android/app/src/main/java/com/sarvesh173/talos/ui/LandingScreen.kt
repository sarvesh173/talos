package com.sarvesh173.talos.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.R
import com.sarvesh173.talos.network.TalosClient

/**
 * TALOS Simplified Agents & Sessions Hub (Apple HIG Liquid Glass Architecture)
 * 
 * Pixel-perfect 1:1 match of img_eb6491dca02a.jpg:
 * 1. Prioritizes CLI agents (Hermes, alya, mom, Cloud Code) over direct agents.
 * 2. Displays agent descriptions and session branches ("225 more sessions >").
 * 3. Indented Apple tree-branch curved connectors (╰─).
 * 4. Ethereal monochrome figure holding glowing sphere in the background.
 * 5. Floating dual-mode capsule [Chat | Agents] and [Filter | More] header.
 * 6. Floating search bar ("Search agents and sessions") + Circular pencil FAB.
 */

data class DelegateAgent(
    val id: String,
    val name: String,
    val activity: String,
    val iconRes: Int,
    val accentColor: Color,
    val isOnline: Boolean = true
)

data class CliToolAgent(
    val id: String,
    val name: String,
    val description: String,
    val mascotColor: Color,
    val mascotShape: String, // "squircle", "cloud", "pill"
    val delegateCount: Int,
    val sessionCountText: String,
    val delegates: List<DelegateAgent>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandingScreen(
    client: TalosClient,
    isDark: Boolean = true,
    onToggleTheme: () -> Unit = {},
    onGetStarted: () -> Unit,
    onConnect: ((String, String) -> Unit)? = null,
    onOpenWizard: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onOpenAgentChat: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val currentAccentName by client.accentColor.collectAsState()
    val effectiveAccent = resolveAccentColor(currentAccentName, isDark)

    var selectedTopTab by remember { mutableIntStateOf(1) } // 0 = Chat, 1 = Agents
    var isAgentsSectionExpanded by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    // State for expanded CLI tools
    var expandedToolId by remember { mutableStateOf<String?>(null) }

    // Pre-populated CLI Tools and their Delegate Agents matching img_eb6491dca02a.jpg
    val cliTools = remember {
        listOf(
            CliToolAgent(
                id = "hermes",
                name = "Hermes",
                description = "No conversations yet — say hi",
                mascotColor = Color(0xFF7C4DFF), // Purple squircle
                mascotShape = "squircle",
                delegateCount = 4,
                sessionCountText = "225 more sessions",
                delegates = listOf(
                    DelegateAgent(
                        id = "hermes_swe",
                        name = "SWE Architect",
                        activity = "Refactoring AST & 0-warning design check",
                        iconRes = R.drawable.ic_codex,
                        accentColor = Color(0xFF007AFF)
                    ),
                    DelegateAgent(
                        id = "hermes_sec",
                        name = "Security Auditor",
                        activity = "Static taint analysis & credential isolation",
                        iconRes = R.drawable.ic_deepseek,
                        accentColor = Color(0xFF4D6BFE)
                    ),
                    DelegateAgent(
                        id = "hermes_vis",
                        name = "Vision Inspector",
                        activity = "Processing UI screenshots & liquid glass diffs",
                        iconRes = R.drawable.ic_gemini,
                        accentColor = Color(0xFF3186FF)
                    ),
                    DelegateAgent(
                        id = "hermes_orch",
                        name = "Task Orchestrator",
                        activity = "Synthesizing parallel subagent fan-out results",
                        iconRes = R.drawable.ic_qwen,
                        accentColor = Color(0xFFA855F7)
                    )
                )
            ),
            CliToolAgent(
                id = "alya",
                name = "alya",
                description = "Alya - helper agent linked to main system (same model, sepa\u2026",
                mascotColor = Color(0xFFEF4444), // Red cloud
                mascotShape = "cloud",
                delegateCount = 2,
                sessionCountText = "2 more sessions",
                delegates = listOf(
                    DelegateAgent(
                        id = "alya_anchor",
                        name = "Emotional Anchor",
                        activity = "Active listener & responsive empathy engine",
                        iconRes = R.drawable.ic_hermes,
                        accentColor = Color(0xFFFF2D55)
                    ),
                    DelegateAgent(
                        id = "alya_wish",
                        name = "Wish Synthesizer",
                        activity = "Preserving 7 core memory wishes",
                        iconRes = R.drawable.ic_omnirush,
                        accentColor = Color(0xFFAF52DE)
                    )
                )
            ),
            CliToolAgent(
                id = "mom",
                name = "mom",
                description = "No conversations yet — say hi",
                mascotColor = Color(0xFFEC4899), // Pink pill
                mascotShape = "pill",
                delegateCount = 1,
                sessionCountText = "9 more sessions",
                delegates = listOf(
                    DelegateAgent(
                        id = "mom_care",
                        name = "Family Hearth",
                        activity = "Warm guidance & blessing guardrails",
                        iconRes = R.drawable.ic_openclaw,
                        accentColor = Color(0xFFEC4899)
                    )
                )
            ),
            CliToolAgent(
                id = "claudecode",
                name = "Cloud Code",
                description = "Anthropic architecture & synthesis suite",
                mascotColor = Color(0xFFD97757), // Terracotta squircle
                mascotShape = "squircle",
                delegateCount = 3,
                sessionCountText = "48 more sessions",
                delegates = listOf(
                    DelegateAgent(
                        id = "claude_planner",
                        name = "Planning Maestro",
                        activity = "Authoring architectural PRD & task breakdown",
                        iconRes = R.drawable.ic_claude,
                        accentColor = Color(0xFFD97757)
                    ),
                    DelegateAgent(
                        id = "claude_tester",
                        name = "Test Engineer",
                        activity = "Executing Red-Green-Refactor test suite",
                        iconRes = R.drawable.ic_mistral,
                        accentColor = Color(0xFFFF8205)
                    ),
                    DelegateAgent(
                        id = "claude_simplifier",
                        name = "Code Simplifier",
                        activity = "Pruning cyclomatic complexity in modules",
                        iconRes = R.drawable.ic_openai,
                        accentColor = Color(0xFF10A37F)
                    )
                )
            )
        )
    }

    // Filter tools based on search query
    val filteredTools = remember(searchQuery, cliTools) {
        if (searchQuery.isBlank()) {
            cliTools
        } else {
            cliTools.filter { tool ->
                tool.name.contains(searchQuery, ignoreCase = true) ||
                tool.description.contains(searchQuery, ignoreCase = true) ||
                tool.delegates.any { it.name.contains(searchQuery, ignoreCase = true) || it.activity.contains(searchQuery, ignoreCase = true) }
            }
        }
    }

    val backgroundColor = if (isDark) Color(0xFF000000) else Color(0xFFFFFFFF)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // ==============================================================
        // ETHEREAL MONOCHROME ARTWORK (Matching img_eb6491dca02a.jpg)
        // ==============================================================
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp)
        ) {
            val artColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
            val sphereColor = if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.12f)
            val cx = size.width / 2f
            val cy = size.height * 0.38f

            // Glowing sphere held in hands
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(sphereColor, Color.Transparent),
                    center = Offset(cx, cy),
                    radius = 52.dp.toPx()
                ),
                radius = 52.dp.toPx(),
                center = Offset(cx, cy)
            )
            drawCircle(
                color = artColor,
                radius = 34.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 1.2.dp.toPx())
            )

            // Head silhouette
            drawCircle(
                color = artColor,
                radius = 12.dp.toPx(),
                center = Offset(cx, cy - 56.dp.toPx()),
                style = Stroke(width = 1.2.dp.toPx())
            )

            // Torso and spine
            drawLine(
                color = artColor,
                start = Offset(cx, cy - 44.dp.toPx()),
                end = Offset(cx, cy + 54.dp.toPx()),
                strokeWidth = 1.2.dp.toPx()
            )

            // Arms cradling the glowing sphere
            drawArc(
                color = artColor,
                startAngle = 25f,
                sweepAngle = 130f,
                useCenter = false,
                topLeft = Offset(cx - 40.dp.toPx(), cy - 22.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(80.dp.toPx(), 48.dp.toPx()),
                style = Stroke(width = 1.2.dp.toPx())
            )

            // Legs standing on platform
            drawLine(
                color = artColor,
                start = Offset(cx - 8.dp.toPx(), cy + 54.dp.toPx()),
                end = Offset(cx - 18.dp.toPx(), size.height - 24.dp.toPx()),
                strokeWidth = 1.2.dp.toPx()
            )
            drawLine(
                color = artColor,
                start = Offset(cx + 8.dp.toPx(), cy + 54.dp.toPx()),
                end = Offset(cx + 18.dp.toPx(), size.height - 24.dp.toPx()),
                strokeWidth = 1.2.dp.toPx()
            )

            // Cables spreading to floor apparatus
            val cables = listOf(-70.dp.toPx(), -40.dp.toPx(), 40.dp.toPx(), 70.dp.toPx())
            cables.forEach { ox ->
                val p = Path().apply {
                    moveTo(cx, cy + 44.dp.toPx())
                    cubicTo(
                        cx + ox * 0.3f, size.height * 0.70f,
                        cx + ox * 0.7f, size.height * 0.90f,
                        cx + ox, size.height
                    )
                }
                drawPath(
                    path = p,
                    color = artColor.copy(alpha = artColor.alpha * 0.75f),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        // ==============================================================
        // MAIN SCROLLABLE CONTENT (Agents List & Sessions)
        // ==============================================================
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 96.dp, bottom = 120.dp)
        ) {
            // Header Section: "Agents v"
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .applePressable(0.98f) {
                            TalosHaptics.sendHaptic(context)
                            isAgentsSectionExpanded = !isAgentsSectionExpanded
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Agents",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) Color.White.copy(alpha = 0.90f) else Color(0xFF1D1D1F),
                        letterSpacing = (-0.2).sp
                    )

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle agents",
                        tint = if (isDark) Color.White.copy(alpha = 0.60f) else Color.Black.copy(alpha = 0.50f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            if (isAgentsSectionExpanded) {
                items(filteredTools, key = { it.id }) { tool ->
                    val isExpanded = expandedToolId == tool.id

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        // 1. Primary CLI Tool Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 52.dp)
                                .applePressable(0.97f) {
                                    TalosHaptics.sendHaptic(context)
                                    if (tool.delegates.isNotEmpty()) {
                                        expandedToolId = if (isExpanded) null else tool.id
                                    } else {
                                        onOpenAgentChat?.invoke(tool.id) ?: onGetStarted()
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Cute Mascot Avatar
                            CuteMascot(
                                color = tool.mascotColor,
                                shapeType = tool.mascotShape
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            // Name & Subtitle / Description
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tool.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) Color.White else Color(0xFF1D1D1F),
                                    letterSpacing = (-0.15).sp
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = tool.description,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.50f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Trailing Chevron (Navigates directly to Chat)
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .applePressable(0.92f) {
                                        TalosHaptics.sendHaptic(context)
                                        onOpenAgentChat?.invoke(tool.id) ?: onGetStarted()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = TalosIcons.TalosChevronRight,
                                    contentDescription = "Open ${tool.name}",
                                    tint = if (isDark) Color.White.copy(alpha = 0.40f) else Color.Black.copy(alpha = 0.35f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // 2. Delegate Agents List (Under curved tree connector)
                        if (isExpanded && tool.delegates.isNotEmpty()) {
                            tool.delegates.forEach { delegate ->
                                AgentBranchRow(
                                    isDark = isDark,
                                    onClick = {
                                        TalosHaptics.sendHaptic(context)
                                        onOpenAgentChat?.invoke(tool.id) ?: onGetStarted()
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 28dp SVG Icon Container
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(delegate.accentColor.copy(alpha = if (isDark) 0.18f else 0.12f))
                                                .border(
                                                    BorderStroke(0.8.dp, delegate.accentColor.copy(alpha = 0.35f)),
                                                    RoundedCornerShape(8.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                painter = painterResource(id = delegate.iconRes),
                                                contentDescription = delegate.name,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = delegate.name,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isDark) Color.White else Color(0xFF1D1D1F),
                                                letterSpacing = (-0.1).sp
                                            )
                                            Text(
                                                text = delegate.activity,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = if (isDark) Color.White.copy(alpha = 0.50f) else Color.Black.copy(alpha = 0.45f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        // Online status indicator
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(StatusGreen)
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Tree Branch Leaf: "N more sessions >" (matching img_eb6491dca02a.jpg)
                        AgentBranchRow(
                            isDark = isDark,
                            onClick = {
                                TalosHaptics.sendHaptic(context)
                                onOpenAgentChat?.invoke(tool.id) ?: onGetStarted()
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = tool.sessionCountText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = if (isDark) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.40f)
                                )
                                Icon(
                                    imageVector = TalosIcons.TalosChevronRight,
                                    contentDescription = null,
                                    tint = if (isDark) Color.White.copy(alpha = 0.40f) else Color.Black.copy(alpha = 0.35f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        // ==============================================================
        // TOP FLOATING LIQUID GLASS BAR (Matching img_eb6491dca02a.jpg)
        // ==============================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Pill: [Chat | Agents] Dual View Toggle
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
                    // Chat Button (Navigates to active chat)
                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 34.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (selectedTopTab == 0) {
                                    if (isDark) Color(0xFF2C2C2E) else Color(0xFF1D1D1F)
                                } else Color.Transparent
                            )
                            .applePressable(0.94f) {
                                TalosHaptics.sendHaptic(context)
                                selectedTopTab = 0
                                onGetStarted()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = TalosIcons.TalosChat,
                            contentDescription = "Switch to chat",
                            tint = if (selectedTopTab == 0) {
                                Color.White
                            } else {
                                if (isDark) Color.White.copy(alpha = 0.50f) else Color(0xFF1D1D1F).copy(alpha = 0.45f)
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Agents Button (Active in screenshot)
                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 34.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (selectedTopTab == 1) {
                                    if (isDark) Color(0xFF2C2C2E) else Color.Transparent
                                } else Color.Transparent
                            )
                            .applePressable(0.94f) {
                                TalosHaptics.sendHaptic(context)
                                selectedTopTab = 1
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = TalosIcons.TalosHub,
                            contentDescription = "Switch to agents",
                            tint = if (isDark) Color.White else Color(0xFF1D1D1F),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Right Pill: [Filter | More Options]
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
        // FLOATING BOTTOM CONTROLS (Search Bar & Circular Compose FAB)
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
            // Floating Search Pill
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

                    Spacer(modifier = Modifier.width(10.dp))

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
                                    text = "Search agents and sessions",
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

            // Circular Compose / Create FAB (Lavender button in light mode matching screenshot)
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
                        onGetStarted()
                    }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Compose session",
                        tint = if (isDark) Color.White else Color(0xFF1D1D1F),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Renders cute Apple-style mascot squircles with expressive eyes,
 * matching img_eb6491dca02a.jpg.
 */
@Composable
fun CuteMascot(
    color: Color,
    shapeType: String,
    modifier: Modifier = Modifier
) {
    val mascotShape = when (shapeType) {
        "cloud" -> RoundedCornerShape(16.dp)
        "pill" -> RoundedCornerShape(16.dp)
        else -> RoundedCornerShape(16.dp)
    }

    val widthDp = if (shapeType == "pill") 48.dp else 44.dp
    val heightDp = if (shapeType == "pill") 34.dp else 44.dp

    Box(
        modifier = modifier
            .size(width = widthDp, height = heightDp)
            .shadow(3.dp, mascotShape, ambientColor = color.copy(alpha = 0.35f))
            .clip(mascotShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        // Specular top highlight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.30f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Two expressive mascot eyes
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left eye
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(2.5.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF111113))
                )
            }

            // Right eye
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(2.5.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF111113))
                )
            }
        }
    }
}

/**
 * Curved Apple tree-branch connector line (╰─),
 * bridging parent mascot to sub-agents and session links.
 */
@Composable
fun AgentBranchRow(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .applePressable(0.97f) { onClick() }
            .padding(start = 24.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Curved L-Connector
        Canvas(modifier = Modifier.size(width = 30.dp, height = 32.dp)) {
            val strokeColor = if (isDark) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.16f)
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(0f, size.height * 0.45f)
                cubicTo(
                    0f, size.height * 0.85f,
                    size.width * 0.25f, size.height,
                    size.width * 0.65f, size.height
                )
                lineTo(size.width, size.height)
            }
            drawPath(
                path = path,
                color = strokeColor,
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        content()
    }
}
