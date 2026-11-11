package com.sarvesh173.talos.ui.agent

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.ui.appleLiquidGlassOptics
import com.sarvesh173.talos.ui.applePressable
import com.sarvesh173.talos.ui.avatar.AgentAvatar
import com.sarvesh173.talos.ui.avatar.AgentAvatarEngine
import com.sarvesh173.talos.ui.avatar.AvatarIdentity
import com.sarvesh173.talos.ui.avatar.AvatarState

enum class AgentRolePreset(
    val title: String,
    val description: String,
    val color: Color,
    val defaultPrompt: String
) {
    GENERAL(
        title = "Generalist",
        description = "Execute multi-step tasks, file operations, and orchestration",
        color = Color(0xFF007AFF),
        defaultPrompt = "You are a senior generalist agent. Analyze the goal, break it into thin verifiable slices, and execute each step cleanly."
    ),
    ARCHITECT(
        title = "Code architect",
        description = "Deep code architecture, system design, refactoring, and review",
        color = Color(0xFF5856D6),
        defaultPrompt = "You are a principal software architect. Audit design patterns, maintain modular boundaries, and enforce clean architecture."
    ),
    SCOUT(
        title = "Research scout",
        description = "Web reconnaissance, document analysis, and finding skills",
        color = Color(0xFF34C759),
        defaultPrompt = "You are a research scout agent. Discover tools, scrape relevant documentation, verify findings, and present cited evidence."
    ),
    AUDITOR(
        title = "Security auditor",
        description = "Vulnerability review, test suite verification, and contract safety",
        color = Color(0xFFFF9500),
        defaultPrompt = "You are a security auditor. Hunt down regressions, inspect permissions, review diffs, and ensure zero credentials leak."
    ),
    DESIGNER(
        title = "UI designer",
        description = "Apple Liquid Glass polish, micro-animations, and visual craft",
        color = Color(0xFFAF52DE),
        defaultPrompt = "You are a design engineer following Apple HIG and WWDC25 Liquid Glass standards. Ensure 48dp minimum targets and specular rims."
    )
}

val DELEGATE_MODELS = listOf(
    "stealth/crouton-alpha" to "Crouton Alpha · 1M Ctx",
    "deepseek/deepseek-v4.1-flash" to "DeepSeek v4.1 · 1M Ctx",
    "nvidia/nemotron-3.5-super-vl-free" to "Nemotron 3.5 · Vision",
    "minimax/minimax-m3.1-flash" to "MiniMax M3.1 · Fast"
)

val REASONING_LEVELS = listOf("minimal", "low", "medium", "high", "max")

val TTL_PRESETS = listOf(
    15 to "15m",
    30 to "30m",
    60 to "1h",
    240 to "4h",
    1440 to "24h"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentSummonSheet(
    isDark: Boolean,
    onDismiss: () -> Unit,
    onSummon: (name: String, role: String, model: String, reasoning: String, ttlMinutes: Int, prompt: String, color: Color) -> Unit
) {
    var selectedRole by remember { mutableStateOf(AgentRolePreset.GENERAL) }
    var agentName by remember { mutableStateOf("Crouton Agent") }
    var selectedModel by remember { mutableStateOf(DELEGATE_MODELS[0].first) }
    var selectedReasoning by remember { mutableStateOf("high") }
    var selectedTtl by remember { mutableStateOf(60) }
    var taskPrompt by remember { mutableStateOf(AgentRolePreset.GENERAL.defaultPrompt) }

    val avatarEngine = remember(agentName, selectedRole) {
        AgentAvatarEngine(
            AvatarIdentity(
                agentId = "summon_preview",
                name = agentName.ifBlank { "Agent" },
                hue = when (selectedRole) {
                    AgentRolePreset.GENERAL -> 210f
                    AgentRolePreset.ARCHITECT -> 250f
                    AgentRolePreset.SCOUT -> 140f
                    AgentRolePreset.AUDITOR -> 35f
                    AgentRolePreset.DESIGNER -> 280f
                }
            )
        ).apply {
            setState(AvatarState.Thinking)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xFF14151C) else Color(0xFFF7F8FC),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color.White.copy(0.24f) else Color.Black.copy(0.20f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Summon Agent",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF1D1D1F),
                        letterSpacing = (-0.3).sp
                    )
                    Text(
                        text = "Spawn an autonomous delegate with watchdog loop",
                        fontSize = 12.sp,
                        color = if (isDark) Color.White.copy(0.60f) else Color.Black.copy(0.55f)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = if (isDark) Color.White.copy(0.70f) else Color.Black.copy(0.60f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Avatar Live Preview Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isDark) Color(0xFF1C1E2A) else Color.White)
                    .border(
                        BorderStroke(
                            1.dp,
                            if (isDark) Color.White.copy(0.10f) else Color.Black.copy(0.06f)
                        ),
                        RoundedCornerShape(20.dp)
                    )
                    .appleLiquidGlassOptics(
                        isDark = isDark,
                        chromaticGlow = selectedRole.color,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AgentAvatar(
                        engine = avatarEngine,
                        size = 56.dp,
                        isDark = isDark
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = agentName.ifBlank { "Autonomous Delegate" },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color.White else Color(0xFF1D1D1F)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${selectedRole.title} · ${selectedModel.substringAfterLast('/')}",
                            fontSize = 12.sp,
                            color = selectedRole.color,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Role Presets
            Text(
                text = "ROLE & SPECIALIZATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White.copy(0.50f) else Color.Black.copy(0.45f),
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AgentRolePreset.values()) { role ->
                    val isSelected = selectedRole == role
                    Box(
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) role.color.copy(alpha = if (isDark) 0.24f else 0.16f)
                                else if (isDark) Color.White.copy(0.05f) else Color.Black.copy(0.04f)
                            )
                            .border(
                                BorderStroke(
                                    if (isSelected) 1.2.dp else 0.8.dp,
                                    if (isSelected) role.color else if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f)
                                ),
                                RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                selectedRole = role
                                agentName = "${role.title} Agent"
                                taskPrompt = role.defaultPrompt
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = role.title,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) role.color else if (isDark) Color.White.copy(0.70f) else Color(0xFF2C2C2E)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Model Selection
            Text(
                text = "MODEL ASSIGNMENT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White.copy(0.50f) else Color.Black.copy(0.45f),
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DELEGATE_MODELS.forEach { (modelId, modelLabel) ->
                    val isSelected = selectedModel == modelId
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) selectedRole.color.copy(alpha = if (isDark) 0.18f else 0.12f)
                                else if (isDark) Color.White.copy(0.04f) else Color.Black.copy(0.03f)
                            )
                            .border(
                                BorderStroke(
                                    if (isSelected) 1.2.dp else 0.8.dp,
                                    if (isSelected) selectedRole.color else if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f)
                                ),
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { selectedModel = modelId }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = modelLabel,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) selectedRole.color else if (isDark) Color.White else Color(0xFF1D1D1F)
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(selectedRole.color)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Reasoning Effort & TTL Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Reasoning
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "REASONING EFFORT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White.copy(0.50f) else Color.Black.copy(0.45f),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.05f))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        REASONING_LEVELS.forEach { level ->
                            val isSel = selectedReasoning == level
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSel) selectedRole.color else Color.Transparent)
                                    .clickable { selectedReasoning = level },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = level.replaceFirstChar { it.uppercase() }.take(3),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) Color.White else if (isDark) Color.White.copy(0.60f) else Color.Black.copy(0.55f)
                                )
                            }
                        }
                    }
                }

                // Lifespan / TTL
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "WATCHDOG TTL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White.copy(0.50f) else Color.Black.copy(0.45f),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.05f))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TTL_PRESETS.forEach { (mins, label) ->
                            val isSel = selectedTtl == mins
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSel) selectedRole.color else Color.Transparent)
                                    .clickable { selectedTtl = mins },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) Color.White else if (isDark) Color.White.copy(0.60f) else Color.Black.copy(0.55f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Task / Directive
            Text(
                text = "TASK DIRECTIVE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White.copy(0.50f) else Color.Black.copy(0.45f),
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isDark) Color(0xFF1B1D28) else Color.White)
                    .border(
                        BorderStroke(0.8.dp, if (isDark) Color.White.copy(0.12f) else Color.Black.copy(0.08f)),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(14.dp)
            ) {
                BasicTextField(
                    value = taskPrompt,
                    onValueChange = { taskPrompt = it },
                    textStyle = TextStyle(
                        fontSize = 13.sp,
                        color = if (isDark) Color.White else Color(0xFF1D1D1F),
                        lineHeight = 18.sp
                    ),
                    cursorBrush = SolidColor(selectedRole.color),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Summon Action Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(selectedRole.color)
                    .applePressable(0.97f, pressHaptic = true) {
                        onSummon(
                            agentName.ifBlank { "${selectedRole.title} Agent" },
                            selectedRole.name.lowercase(),
                            selectedModel,
                            selectedReasoning,
                            selectedTtl,
                            taskPrompt,
                            selectedRole.color
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Summon Delegate",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.2).sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
