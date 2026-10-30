package com.sarvesh173.talos.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient

data class CanvasAgentNode(
    val id: String,
    val name: String,
    val role: String,
    val tier: String,
    val cliEngine: String,
    val model: String,
    val reasoningEffort: String,
    val status: AgentStatus,
    val turnsUsed: Int,
    var commandLimit: Int,
    val isPaused: Boolean = false,
    val parentId: String? = null
)

enum class AgentStatus {
    RUNNING, IDLE, THINKING, VERIFYING, PAUSED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentCanvasScreen(
    client: TalosClient,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hierarchy by client.delegationHierarchy.collectAsState()
    var selectedNode by remember { mutableStateOf<CanvasAgentNode?>(null) }
    var selectedFilter by remember { mutableStateOf("All") }
    var selectedCli by remember { mutableStateOf("All CLI") }
    var showSpawnDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        client.fetchDelegationHierarchy()
    }

    // Build flattened active canvas nodes from live Rust delegation hierarchy
    val activeNodes = remember(hierarchy) {
        val list = mutableListOf<CanvasAgentNode>()

        hierarchy?.engines?.forEach { engine ->
            list.add(
                CanvasAgentNode(
                    id = engine.id,
                    name = engine.name,
                    role = "CLI Execution Engine",
                    tier = "Tier 1: Engine",
                    cliEngine = engine.command_binary,
                    model = "Host Environment",
                    reasoningEffort = "Native",
                    status = if (engine.is_available) AgentStatus.RUNNING else AgentStatus.IDLE,
                    turnsUsed = engine.delegates.sumOf { it.turns_consumed },
                    commandLimit = engine.delegates.sumOf { it.command_turn_limit },
                    parentId = null
                )
            )

            engine.delegates.forEach { delegate ->
                list.add(
                    CanvasAgentNode(
                        id = delegate.id,
                        name = delegate.name,
                        role = "Delegate Lead Orchestrator",
                        tier = "Tier 2: Delegate Lead",
                        cliEngine = engine.name,
                        model = delegate.assigned_model,
                        reasoningEffort = delegate.reasoning_effort,
                        status = if (delegate.is_paused) AgentStatus.PAUSED else AgentStatus.RUNNING,
                        turnsUsed = delegate.turns_consumed,
                        commandLimit = delegate.command_turn_limit,
                        isPaused = delegate.is_paused,
                        parentId = engine.id
                    )
                )

                delegate.children.forEach { child ->
                    val isChildPaused = child.state.equals("paused", ignoreCase = true)
                    list.add(
                        CanvasAgentNode(
                            id = child.id,
                            name = child.name,
                            role = child.task_goal,
                            tier = "Tier 3: Child Worker",
                            cliEngine = engine.name,
                            model = delegate.assigned_model,
                            reasoningEffort = delegate.reasoning_effort,
                            status = if (isChildPaused) AgentStatus.PAUSED else AgentStatus.RUNNING,
                            turnsUsed = child.turns_used,
                            commandLimit = delegate.command_turn_limit,
                            isPaused = isChildPaused,
                            parentId = delegate.id
                        )
                    )
                }
            }
        }

        // Fallback default node if hierarchy is initializing
        if (list.isEmpty()) {
            list.add(
                CanvasAgentNode(
                    id = "root",
                    name = "TALOS Orchestrator",
                    role = "Core Daemon Lead",
                    tier = "Tier 1: Engine",
                    cliEngine = "talos-core",
                    model = "agy/gemini-3.8-flash-tiered",
                    reasoningEffort = "Medium",
                    status = AgentStatus.RUNNING,
                    turnsUsed = 12,
                    commandLimit = 50,
                    parentId = null
                )
            )
        }
        list
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Multi-Agent Canvas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${activeNodes.size} agents in live parallel mesh",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showSpawnDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.AddCircleOutline,
                            contentDescription = "Spawn Subagent",
                            tint = MaterialTheme.colorScheme.primary
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // CLI Category Filters Row
            val cliCategories = listOf("All CLI", "hermes", "opencode", "omnirush", "codex", "cline", "talos-core")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cliCategories) { cli ->
                    val isSelected = selectedCli == cli
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCli = cli },
                        label = { Text(cli.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // Tier & State Filter Pills Row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(listOf("All", "Tier 1: Engine", "Tier 2: Delegate Lead", "Tier 3: Child Worker", "Paused")) { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                }
            }

            val filteredNodes = activeNodes.filter { node ->
                val matchesTier = when (selectedFilter) {
                    "Tier 1: Engine" -> node.tier.startsWith("Tier 1")
                    "Tier 2: Delegate Lead" -> node.tier.startsWith("Tier 2")
                    "Tier 3: Child Worker" -> node.tier.startsWith("Tier 3")
                    "Paused" -> node.isPaused
                    else -> true
                }
                val matchesCli = if (selectedCli == "All CLI") true else {
                    node.cliEngine.contains(selectedCli, ignoreCase = true) || node.id.contains(selectedCli, ignoreCase = true)
                }
                matchesTier && matchesCli
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredNodes, key = { it.id }) { agent ->
                    AgentNodeCard(
                        agent = agent,
                        isSelected = selectedNode?.id == agent.id,
                        onClick = { selectedNode = agent }
                    )
                }
            }
        }
    }

    // Node Inspector & Governance Bottom Sheet
    selectedNode?.let { node ->
        ModalBottomSheet(
            onDismissRequest = { selectedNode = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = node.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${node.tier} • ${node.cliEngine}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    StatusPill(status = node.status)
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Custom Command Turn Quota Slider
                var commandLimit by remember(node.id) { mutableFloatStateOf(node.commandLimit.toFloat()) }
                Text(
                    text = "Custom Command Limit: ${commandLimit.toInt()} Turns",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Bounds execution loop before automatic consensus check or pause.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = commandLimit,
                    onValueChange = {
                        commandLimit = it
                        node.commandLimit = it.toInt()
                    },
                    valueRange = 10f..100f,
                    steps = 17,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                val ok = client.setAgentCommandLimit(node.id, commandLimit.toInt())
                                if (ok) {
                                    Toast.makeText(context, "Command limit saved (${commandLimit.toInt()} turns)!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Limit updated locally (${commandLimit.toInt()} turns)", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply Command Limit", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Metadata Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Assigned Model", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(node.model, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Reasoning Effort", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(node.reasoningEffort, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Turns Used / Cap", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${node.turnsUsed} / ${commandLimit.toInt()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (node.tier.startsWith("Tier 3")) {
                        // Pause / Resume Toggle for Child Subagents
                        Button(
                            onClick = {
                                client.toggleChildPause(node.id, node.isPaused)
                                selectedNode = null
                                Toast.makeText(context, if (node.isPaused) "Child Resumed" else "Child Paused", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (node.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (node.isPaused) "Resume" else "Pause", color = MaterialTheme.colorScheme.onSurface)
                        }

                        // Terminate Action
                        OutlinedButton(
                            onClick = {
                                client.terminateChildSubagent(node.id)
                                selectedNode = null
                                Toast.makeText(context, "Child Terminated", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Terminate", color = MaterialTheme.colorScheme.error)
                        }
                    } else if (node.tier.startsWith("Tier 2")) {
                        // Apply Quota for Delegate Leads
                        Button(
                            onClick = {
                                client.updateDelegateQuota(node.id, commandLimit.toInt(), node.reasoningEffort)
                                selectedNode = null
                                Toast.makeText(context, "Quota updated to ${commandLimit.toInt()} turns!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Apply Quota Changes", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    } else {
                        Button(
                            onClick = { selectedNode = null },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Close Inspector", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Spawn Subagent Dialog
    if (showSpawnDialog) {
        var subagentName by remember { mutableStateOf("") }
        var taskGoal by remember { mutableStateOf("") }
        var selectedEngineId by remember { mutableStateOf("omnirush") }

        AlertDialog(
            onDismissRequest = { showSpawnDialog = false },
            icon = {
                Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            title = {
                Text("Spawn Delegated Subagent", style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = subagentName,
                        onValueChange = { subagentName = it },
                        label = { Text("Subagent Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = taskGoal,
                        onValueChange = { taskGoal = it },
                        label = { Text("Task Goal") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                    Text("Host CLI Engine", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("omnirush", "opencode", "codex").forEach { engineId ->
                            FilterChip(
                                selected = selectedEngineId == engineId,
                                onClick = { selectedEngineId = engineId },
                                label = { Text(engineId.uppercase(), fontSize = 10.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (subagentName.isNotBlank() && taskGoal.isNotBlank()) {
                            val delegateId = hierarchy?.engines?.find { it.id == selectedEngineId }?.delegates?.firstOrNull()?.id
                                ?: "lead-$selectedEngineId-main"
                            client.spawnChildSubagent(delegateId, subagentName, taskGoal)
                            showSpawnDialog = false
                            Toast.makeText(context, "Subagent spawned under $selectedEngineId!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = subagentName.isNotBlank() && taskGoal.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Spawn", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSpawnDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AgentNodeCard(
    agent: CanvasAgentNode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        tonalElevation = if (isSelected) 4.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when (agent.status) {
                                    AgentStatus.RUNNING -> Color(0xFF10B981).copy(alpha = pulseGlow)
                                    AgentStatus.THINKING -> MaterialTheme.colorScheme.primary.copy(alpha = pulseGlow)
                                    AgentStatus.VERIFYING -> Color(0xFF2DD4BF).copy(alpha = pulseGlow)
                                    AgentStatus.IDLE -> Color(0xFF64748B)
                                    AgentStatus.PAUSED -> Color(0xFFF59E0B)
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = agent.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                StatusPill(status = agent.status)
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${agent.tier} • ${agent.cliEngine}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (agent.role.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = agent.role,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Metallic Progress Indicator for Command Limit Quota
            val progress = (agent.turnsUsed.toFloat() / agent.commandLimit.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                MetallicLoadingBar(
                    progress = progress,
                    height = 6.dp,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "${agent.turnsUsed}/${agent.commandLimit} turns",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusPill(status: AgentStatus) {
    val (label, bg, fg) = when (status) {
        AgentStatus.RUNNING -> Triple("RUNNING", Color(0xFF10B981).copy(alpha = 0.15f), Color(0xFF10B981))
        AgentStatus.THINKING -> Triple("THINKING", Color(0xFFE5A93C).copy(alpha = 0.15f), Color(0xFFE5A93C))
        AgentStatus.VERIFYING -> Triple("VERIFYING", Color(0xFF2DD4BF).copy(alpha = 0.15f), Color(0xFF2DD4BF))
        AgentStatus.IDLE -> Triple("IDLE", Color(0xFF64748B).copy(alpha = 0.15f), Color(0xFF94A3B8))
        AgentStatus.PAUSED -> Triple("PAUSED", Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFF59E0B))
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = fg
        )
    }
}
