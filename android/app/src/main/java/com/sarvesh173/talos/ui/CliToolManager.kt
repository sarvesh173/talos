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
import com.sarvesh173.talos.network.CliEngineDto
import com.sarvesh173.talos.network.DelegateLeadDto
import com.sarvesh173.talos.network.SubagentChildDto
import com.sarvesh173.talos.network.TalosClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CliToolManagerScreen(
    client: TalosClient,
    onBack: () -> Unit
) {
    val hierarchyDto by client.delegationHierarchy.collectAsState()
    var spawningDelegateId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        client.fetchDelegationHierarchy()
    }

    // Default fallback engines if offline / connecting
    val fallbackEngines = remember {
        listOf(
            CliEngineDto(
                id = "omnirush",
                name = "OmniRush Engine",
                command_binary = "omnirush",
                is_available = true,
                delegates = listOf(
                    DelegateLeadDto(
                        id = "del_omni_ui",
                        name = "UI Refactoring Lead",
                        engine_id = "omnirush",
                        assigned_model = "agy/gemini-3.8-flash-tiered",
                        reasoning_effort = "Medium",
                        command_turn_limit = 50,
                        turns_consumed = 24,
                        is_paused = false,
                        children = listOf(
                            SubagentChildDto("c_1", "Motion Token Validator", "del_omni_ui", "Physics & Easing", "running", 14),
                            SubagentChildDto("c_2", "Color Contrast Auditor", "del_omni_ui", "WCAG 2.2 Gate", "completed", 10)
                        )
                    )
                )
            ),
            CliEngineDto(
                id = "opencode",
                name = "OpenCode Zen Bridge",
                command_binary = "opencode serve",
                is_available = true,
                delegates = listOf(
                    DelegateLeadDto(
                        id = "del_open_rust",
                        name = "Rust Core Maintainer",
                        engine_id = "opencode",
                        assigned_model = "space-bunny-free",
                        reasoning_effort = "High",
                        command_turn_limit = 60,
                        turns_consumed = 32,
                        is_paused = false,
                        children = listOf(
                            SubagentChildDto("c_3", "Tokio Runtime Watcher", "del_open_rust", "Async Threading", "running", 20)
                        )
                    )
                )
            )
        )
    }

    val displayEngines = hierarchyDto?.engines ?: fallbackEngines
    val totalActiveChildren = hierarchyDto?.total_active_children ?: displayEngines.sumOf { eng ->
        eng.delegates.sumOf { del -> del.children.count { it.state.equals("running", ignoreCase = true) } }
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
                            text = "$totalActiveChildren subagents active across ${displayEngines.size} CLI engines",
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
                actions = {
                    IconButton(onClick = { client.fetchDelegationHierarchy() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = PrimaryAccent
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
            items(displayEngines, key = { it.id }) { engine ->
                CliEngineCard(
                    engine = engine,
                    onUpdateQuota = { delegateId, limit, effort ->
                        client.updateDelegateQuota(delegateId, limit, effort)
                    },
                    onTogglePause = { delegateId, paused ->
                        client.toggleDelegatePause(delegateId, paused)
                    },
                    onStartSpawnChild = { delegateId ->
                        spawningDelegateId = delegateId
                    }
                )
            }
        }
    }

    // Spawn Subagent Dialog
    spawningDelegateId?.let { delegateId ->
        var subagentName by remember { mutableStateOf("") }
        var subagentGoal by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { spawningDelegateId = null },
            icon = {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = PrimaryAccent)
            },
            title = {
                Text("Spawn Child Subagent", style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = subagentName,
                        onValueChange = { subagentName = it },
                        placeholder = { Text("Subagent name (e.g. AST Linter)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = subagentGoal,
                        onValueChange = { subagentGoal = it },
                        placeholder = { Text("Task goal / responsibility...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (subagentName.isNotBlank() && subagentGoal.isNotBlank()) {
                            client.spawnChildSubagent(delegateId, subagentName, subagentGoal)
                            spawningDelegateId = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                ) {
                    Text("Spawn", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { spawningDelegateId = null }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}

@Composable
private fun CliEngineCard(
    engine: CliEngineDto,
    onUpdateQuota: (String, Int, String) -> Unit,
    onTogglePause: (String, Boolean) -> Unit,
    onStartSpawnChild: (String) -> Unit
) {
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
                            .background(if (engine.is_available) Color(0xFF10B981) else Color(0xFF64748B))
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
                            text = "binary: ${engine.command_binary}",
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
                        DelegateLeadCard(
                            delegate = delegate,
                            onUpdateQuota = { limit, effort ->
                                onUpdateQuota(delegate.id, limit, effort)
                            },
                            onTogglePause = { paused ->
                                onTogglePause(delegate.id, paused)
                            },
                            onSpawnChild = {
                                onStartSpawnChild(delegate.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DelegateLeadCard(
    delegate: DelegateLeadDto,
    onUpdateQuota: (Int, String) -> Unit,
    onTogglePause: (Boolean) -> Unit,
    onSpawnChild: () -> Unit
) {
    var commandLimit by remember(delegate.command_turn_limit) { mutableFloatStateOf(delegate.command_turn_limit.toFloat()) }
    var reasoning by remember(delegate.reasoning_effort) { mutableStateOf(delegate.reasoning_effort) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                        text = "${delegate.assigned_model} • Reasoning: $reasoning",
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimaryAccent
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Pause / Resume Toggle
                    IconButton(
                        onClick = { onTogglePause(!delegate.is_paused) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (delegate.is_paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (delegate.is_paused) "Resume" else "Pause",
                            tint = if (delegate.is_paused) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Reasoning Toggle Chip
                    FilterChip(
                        selected = reasoning.equals("High", ignoreCase = true),
                        onClick = {
                            val newEffort = if (reasoning.equals("High", ignoreCase = true)) "Medium" else "High"
                            reasoning = newEffort
                            onUpdateQuota(commandLimit.toInt(), newEffort)
                        },
                        label = { Text(reasoning, fontSize = 10.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Command limit progress and control
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Turns: ${delegate.turns_consumed} / ${commandLimit.toInt()} limit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (commandLimit > 10f) {
                                commandLimit -= 5f
                                onUpdateQuota(commandLimit.toInt(), reasoning)
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(
                        onClick = {
                            if (commandLimit < 100f) {
                                commandLimit += 5f
                                onUpdateQuota(commandLimit.toInt(), reasoning)
                            }
                        },
                        modifier = Modifier.size(36.dp)
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "DELEGATED SUBAGENTS (${delegate.children.size})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )

                    TextButton(
                        onClick = onSpawnChild,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = PrimaryAccent, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Spawn", fontSize = 10.sp, color = PrimaryAccent)
                    }
                }

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
                            text = child.state.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (child.state.equals("running", ignoreCase = true)) Color(0xFF10B981) else Color(0xFF94A3B8)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = onSpawnChild,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = PrimaryAccent, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Spawn Subagent", fontSize = 11.sp, color = PrimaryAccent)
                }
            }
        }
    }
}
