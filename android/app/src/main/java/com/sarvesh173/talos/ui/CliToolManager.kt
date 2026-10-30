package com.sarvesh173.talos.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient

data class SubagentChild(
    val id: String,
    val name: String,
    val role: String,
    val status: String,
    val turnsUsed: Int
)

data class DelegateAgent(
    val id: String,
    val name: String,
    val model: String,
    var reasoningEffort: String,
    var commandLimit: Int,
    var turnsSpent: Int,
    val children: MutableList<SubagentChild>
)

data class CliEngineEntry(
    val id: String,
    val name: String,
    val command: String,
    val isOnline: Boolean,
    val delegates: List<DelegateAgent>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CliToolManagerScreen(
    client: TalosClient,
    onBack: () -> Unit
) {
    val engines = remember {
        mutableStateListOf(
            CliEngineEntry(
                id = "omnirush",
                name = "OmniRush Engine",
                command = "omnirush",
                isOnline = true,
                delegates = listOf(
                    DelegateAgent(
                        id = "del_1",
                        name = "UI Refactoring Lead",
                        model = "agy/gemini-3.8-flash-tiered",
                        reasoningEffort = "Medium",
                        commandLimit = 50,
                        turnsSpent = 24,
                        children = mutableStateListOf(
                            SubagentChild("c_1", "Motion Token Validator", "Physics & Easing", "RUNNING", 14),
                            SubagentChild("c_2", "Color Contrast Auditor", "WCAG 2.2 Gate", "IDLE", 10)
                        )
                    ),
                    DelegateAgent(
                        id = "del_2",
                        name = "Hardware Bridge Agent",
                        model = "agy/gemini-3.8-flash-tiered",
                        reasoningEffort = "Medium",
                        commandLimit = 40,
                        turnsSpent = 16,
                        children = mutableStateListOf(
                            SubagentChild("c_3", "Camera & Mic Harness", "Sensor Introspection", "IDLE", 8)
                        )
                    )
                )
            ),
            CliEngineEntry(
                id = "opencode",
                name = "OpenCode Zen Bridge",
                command = "opencode serve",
                isOnline = true,
                delegates = listOf(
                    DelegateAgent(
                        id = "del_3",
                        name = "Rust Core Maintainer",
                        model = "space-bunny-free",
                        reasoningEffort = "High",
                        commandLimit = 60,
                        turnsSpent = 32,
                        children = mutableStateListOf(
                            SubagentChild("c_4", "Tokio Runtime Watcher", "Async Threading", "RUNNING", 20),
                            SubagentChild("c_5", "Memory Leak Sanitizer", "Valgrind & ASAN", "IDLE", 12)
                        )
                    )
                )
            ),
            CliEngineEntry(
                id = "claude_code",
                name = "Claude Code CLI",
                command = "claude",
                isOnline = false,
                delegates = listOf(
                    DelegateAgent(
                        id = "del_4",
                        name = "Architecture Reviewer",
                        model = "claude-opus-5.5",
                        reasoningEffort = "High",
                        commandLimit = 30,
                        turnsSpent = 5,
                        children = mutableStateListOf()
                    )
                )
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "CLI & Agent Governance",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Manage multi-CLI delegates & command quotas",
                            style = MaterialTheme.typography.bodySmall,
                            color = PrimaryAccent
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            items(engines, key = { it.id }) { engine ->
                CliEngineCard(engine = engine)
            }
        }
    }
}

@Composable
private fun CliEngineCard(engine: CliEngineEntry) {
    var isExpanded by remember { mutableStateOf(true) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (engine.isOnline) Color(0xFF10B981) else Color(0xFF64748B))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = engine.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "command: ${engine.command}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    engine.delegates.forEach { delegate ->
                        DelegateAgentRow(delegate = delegate)
                    }
                }
            }
        }
    }
}

@Composable
private fun DelegateAgentRow(delegate: DelegateAgent) {
    var commandLimit by remember { mutableFloatStateOf(delegate.commandLimit.toFloat()) }
    var reasoning by remember { mutableStateOf(delegate.reasoningEffort) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = delegate.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${delegate.model} • Reasoning: $reasoning",
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimaryAccent
                    )
                }

                // Reasoning Effort Toggle Pill
                FilterChip(
                    selected = reasoning == "High",
                    onClick = {
                        reasoning = if (reasoning == "High") "Medium" else "High"
                        delegate.reasoningEffort = reasoning
                    },
                    label = { Text(reasoning, fontSize = 10.sp) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Command limit progress and control
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Quota: ${delegate.turnsSpent} / ${commandLimit.toInt()} turns",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            if (commandLimit > 10f) {
                                commandLimit -= 5f
                                delegate.commandLimit = commandLimit.toInt()
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(
                        onClick = {
                            if (commandLimit < 100f) {
                                commandLimit += 5f
                                delegate.commandLimit = commandLimit.toInt()
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase", tint = PrimaryAccent)
                    }
                }
            }

            // Subagent Children list
            if (delegate.children.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "DELEGATED SUBAGENTS (${delegate.children.size})",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline
                )

                delegate.children.forEach { child ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SubdirectoryArrowRight,
                                contentDescription = null,
                                tint = PrimaryAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = child.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = child.status,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (child.status == "RUNNING") Color(0xFF10B981) else Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }
    }
}
