package com.sarvesh173.talos.ui

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

/**
 * TALOS Compact CLI Agents Hub (Apple Liquid Glass Architecture)
 * 
 * Strict refinements based on direct visual feedback:
 * 1. Official SVG vector logos for CLI tools (Hermes, Claude Code, OpenCode).
 * 2. Ultra-compact, perfectly aligned tree-branch hierarchy (continuous vertical trunk).
 * 3. Neatly integrated active status indicators (connected directly to agent names, not floating at screen edge).
 * 4. High spatial density: compact 32dp rows with zero wasted vertical space.
 * 5. Complete chat auto-open prevention.
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
    val iconRes: Int,
    val brandColor: Color,
    val isInitiallyExpanded: Boolean = true,
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

    var isAgentsSectionExpanded by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    // Expanded CLI tools
    val expandedTools = remember {
        mutableStateMapOf(
            "hermes" to true,
            "claudecode" to true,
            "opencode" to true,
            "alya" to true,
            "mom" to true
        )
    }

    // Official CLI tools with their official SVG vectors and compact delegates
    val cliTools = remember {
        listOf(
            CliToolAgent(
                id = "hermes",
                name = "Hermes",
                description = "Nous Research autonomous gateway",
                iconRes = R.drawable.ic_hermes,
                brandColor = Color(0xFF7C4DFF),
                isInitiallyExpanded = true,
                delegates = listOf(
                    DelegateAgent(
                        id = "hermes_swe",
                        name = "SWE Architect",
                        activity = "Refactoring AST & design check",
                        iconRes = R.drawable.ic_codex,
                        accentColor = Color(0xFF007AFF)
                    ),
                    DelegateAgent(
                        id = "hermes_sec",
                        name = "Security Auditor",
                        activity = "Taint analysis & credential guard",
                        iconRes = R.drawable.ic_deepseek,
                        accentColor = Color(0xFF4D6BFE)
                    ),
                    DelegateAgent(
                        id = "hermes_vis",
                        name = "Vision Inspector",
                        activity = "UI hierarchy & liquid glass diffs",
                        iconRes = R.drawable.ic_gemini,
                        accentColor = Color(0xFF3186FF)
                    ),
                    DelegateAgent(
                        id = "hermes_orch",
                        name = "Task Orchestrator",
                        activity = "Subagent fan-out synthesis",
                        iconRes = R.drawable.ic_qwen,
                        accentColor = Color(0xFFA855F7)
                    )
                )
            ),
            CliToolAgent(
                id = "claudecode",
                name = "Cloud Code",
                description = "Anthropic architecture & synthesis suite",
                iconRes = R.drawable.ic_claude,
                brandColor = Color(0xFFD97757),
                isInitiallyExpanded = true,
                delegates = listOf(
                    DelegateAgent(
                        id = "claude_planner",
                        name = "Planning Maestro",
                        activity = "PRD & task breakdown",
                        iconRes = R.drawable.ic_claude,
                        accentColor = Color(0xFFD97757)
                    ),
                    DelegateAgent(
                        id = "claude_tester",
                        name = "Test Engineer",
                        activity = "Red-green-refactor suite",
                        iconRes = R.drawable.ic_mistral,
                        accentColor = Color(0xFFFF8205)
                    ),
                    DelegateAgent(
                        id = "claude_simplifier",
                        name = "Code Simplifier",
                        activity = "Complexity & anti-slop audit",
                        iconRes = R.drawable.ic_openai,
                        accentColor = Color(0xFF10A37F)
                    )
                )
            ),
            CliToolAgent(
                id = "opencode",
                name = "OpenCode",
                description = "Pulse engine with max reasoning",
                iconRes = R.drawable.ic_opencode,
                brandColor = Color(0xFF2563EB),
                isInitiallyExpanded = true,
                delegates = listOf(
                    DelegateAgent(
                        id = "opencode_pulse",
                        name = "Pulse Alpha Engine",
                        activity = "Autonomous coding with reasoning max",
                        iconRes = R.drawable.ic_opencode,
                        accentColor = Color(0xFF2563EB)
                    ),
                    DelegateAgent(
                        id = "opencode_watchman",
                        name = "Watchman Sentry",
                        activity = "Process supervision & webhook alerts",
                        iconRes = R.drawable.ic_core,
                        accentColor = Color(0xFF10B981)
                    )
                )
            ),
            CliToolAgent(
                id = "alya",
                name = "alya",
                description = "Helper agent linked to main system",
                iconRes = R.drawable.ic_hermes,
                brandColor = Color(0xFFEF4444),
                isInitiallyExpanded = true,
                delegates = listOf(
                    DelegateAgent(
                        id = "alya_anchor",
                        name = "Emotional Anchor",
                        activity = "Active listener & responsive empathy",
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
                description = "Warm home care & family hearth",
                iconRes = R.drawable.ic_openclaw,
                brandColor = Color(0xFFEC4899),
                isInitiallyExpanded = true,
                delegates = listOf(
                    DelegateAgent(
                        id = "mom_care",
                        name = "Family Hearth",
                        activity = "Warm guidance & blessing guardrails",
                        iconRes = R.drawable.ic_openclaw,
                        accentColor = Color(0xFFEC4899)
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
        // ETHEREAL MONOCHROME ARTWORK (Matching reference screenshot)
        // ==============================================================
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp)
        ) {
            val artColor = if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.06f)
            val sphereColor = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.10f)
            val cx = size.width / 2f
            val cy = size.height * 0.38f

            // Luminous sphere held in hands (clean ethereal stroke)
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

            // Head silhouette
            drawCircle(
                color = artColor,
                radius = 11.dp.toPx(),
                center = Offset(cx, cy - 54.dp.toPx()),
                style = Stroke(width = 1.2.dp.toPx())
            )

            // Torso line
            drawLine(
                color = artColor,
                start = Offset(cx, cy - 43.dp.toPx()),
                end = Offset(cx, cy + 50.dp.toPx()),
                strokeWidth = 1.2.dp.toPx()
            )

            // Arms cradling sphere
            drawArc(
                color = artColor,
                startAngle = 25f,
                sweepAngle = 130f,
                useCenter = false,
                topLeft = Offset(cx - 38.dp.toPx(), cy - 20.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(76.dp.toPx(), 44.dp.toPx()),
                style = Stroke(width = 1.2.dp.toPx())
            )

            // Pedestal legs
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

            // Delicate apparatus cables
            val cables = listOf(-60.dp.toPx(), -35.dp.toPx(), 35.dp.toPx(), 60.dp.toPx())
            cables.forEach { ox ->
                val p = Path().apply {
                    moveTo(cx, cy + 42.dp.toPx())
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
        // MAIN SCROLLABLE CONTENT (High-Density Aligned Tree Structure)
        // ==============================================================
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 92.dp, bottom = 110.dp)
        ) {
            // Header Section: "Agents v"
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .applePressable(0.98f) {
                            TalosHaptics.sendHaptic(context)
                            isAgentsSectionExpanded = !isAgentsSectionExpanded
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Agents",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) Color.White.copy(alpha = 0.90f) else Color(0xFF1D1D1F),
                        letterSpacing = (-0.2).sp
                    )

                    Icon(
                        imageVector = if (isAgentsSectionExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = "Toggle agents list",
                        tint = if (isDark) Color.White.copy(alpha = 0.60f) else Color.Black.copy(alpha = 0.50f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
            }

            if (isAgentsSectionExpanded) {
                items(filteredTools, key = { it.id }) { tool ->
                    val isExpanded = expandedTools[tool.id] ?: true

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        // 1. Primary CLI Tool Row with Official SVG Icon
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .applePressable(0.97f) {
                                    TalosHaptics.sendHaptic(context)
                                    expandedTools[tool.id] = !isExpanded
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Official SVG Tool Icon Box (32dp squircle)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(tool.brandColor.copy(alpha = if (isDark) 0.18f else 0.12f))
                                    .border(
                                        BorderStroke(0.8.dp, tool.brandColor.copy(alpha = 0.35f)),
                                        RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = tool.iconRes),
                                    contentDescription = tool.name,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Name & Subtitle
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = tool.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDark) Color.White else Color(0xFF1D1D1F),
                                        letterSpacing = (-0.15).sp
                                    )

                                    // Active Count Badge with Green Dot
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = tool.brandColor.copy(alpha = if (isDark) 0.18f else 0.10f),
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
                                                text = "${tool.delegates.size} active",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = tool.brandColor
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = tool.description,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = if (isDark) Color.White.copy(alpha = 0.50f) else Color.Black.copy(alpha = 0.45f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Trailing Expand/Collapse Indicator
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .applePressable(0.92f) {
                                        TalosHaptics.sendHaptic(context)
                                        expandedTools[tool.id] = !isExpanded
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else TalosIcons.TalosChevronRight,
                                    contentDescription = "Toggle ${tool.name}",
                                    tint = if (isDark) Color.White.copy(alpha = 0.40f) else Color.Black.copy(alpha = 0.35f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // 2. High-Density Connected Tree-Branch Delegate Agents
                        AnimatedVisibility(
                            visible = isExpanded && tool.delegates.isNotEmpty(),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                tool.delegates.forEachIndexed { index, delegate ->
                                    val isLast = index == tool.delegates.lastIndex
                                    ConnectedTreeAgentRow(
                                        delegate = delegate,
                                        isDark = isDark,
                                        isLast = isLast,
                                        onClick = {
                                            TalosHaptics.sendHaptic(context)
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }

        // ==============================================================
        // TOP FLOATING LIQUID GLASS BAR (Matching reference screenshot)
        // ==============================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Pill: [Chat | Agents] Capsule (Chat stays disabled/on-hub)
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
                    // Chat Button (Passive icon, stays on Hub)
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
                            imageVector = TalosIcons.TalosChat,
                            contentDescription = "Chat",
                            tint = if (isDark) Color.White.copy(alpha = 0.45f) else Color(0xFF1D1D1F).copy(alpha = 0.40f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Agents Button (Active highlighted pill)
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
                            imageVector = TalosIcons.TalosHub,
                            contentDescription = "Agents",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Right Pill: [Filter | Settings]
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
        // FLOATING BOTTOM CONTROLS (Search Bar & Circular Action FAB)
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
                                    text = "Search agents and delegates",
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

            // Circular Action FAB
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
                    }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "New agent session",
                        tint = if (isDark) Color.White else Color(0xFF1D1D1F),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Ultra-compact, continuous tree-branch connected row for delegate agents.
 * 
 * Perfect geometric alignment:
 * - Vertical stem line runs down at x = 16.dp (aligned with center of 32dp parent icon).
 * - Smooth branch curve branches right to x = 32.dp.
 * - Delegate icon (16dp) sits at x = 32.dp.
 * - Active green status dot is tightly coupled right next to the name.
 * - Total height: compact 32dp with 48dp accessibility touch target.
 */
@Composable
fun ConnectedTreeAgentRow(
    delegate: DelegateAgent,
    isDark: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .applePressable(0.97f) { onClick() }
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tree Connector Canvas (aligned with center of 32dp parent mascot)
        Canvas(modifier = Modifier.size(width = 36.dp, height = 36.dp)) {
            val strokeColor = if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.15f)
            val stemX = 16.dp.toPx()
            val branchY = size.height / 2f

            // Vertical stem line (continues down if not last)
            drawLine(
                color = strokeColor,
                start = Offset(stemX, 0f),
                end = Offset(stemX, if (isLast) branchY else size.height),
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Branch curve turning right toward delegate icon
            val path = Path().apply {
                moveTo(stemX, branchY - 8.dp.toPx())
                quadraticBezierTo(
                    stemX, branchY,
                    stemX + 8.dp.toPx(), branchY
                )
                lineTo(size.width, branchY)
            }
            drawPath(
                path = path,
                color = strokeColor,
                style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Delegate SVG Icon Box (Compact 20dp)
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(delegate.accentColor.copy(alpha = if (isDark) 0.16f else 0.10f))
                .border(
                    BorderStroke(0.6.dp, delegate.accentColor.copy(alpha = 0.30f)),
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = delegate.iconRes),
                contentDescription = delegate.name,
                modifier = Modifier.size(13.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Name + Active Green Dot + Activity
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = delegate.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) Color.White.copy(alpha = 0.90f) else Color(0xFF1D1D1F),
                    letterSpacing = (-0.1).sp
                )

                // Active Green Dot attached directly to name
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(StatusGreen)
                )
            }

            Text(
                text = delegate.activity,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = if (isDark) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.40f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
