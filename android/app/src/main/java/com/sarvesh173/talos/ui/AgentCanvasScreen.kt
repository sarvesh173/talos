package com.sarvesh173.talos.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient

data class CanvasAgentNode(
    val id: String,
    val name: String,
    val role: String,
    val cliEngine: String,
    val model: String,
    val reasoningEffort: String,
    val status: AgentStatus,
    val turnsUsed: Int,
    var commandLimit: Int,
    val parentId: String? = null
)

enum class AgentStatus {
    RUNNING, IDLE, THINKING, VERIFYING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentCanvasScreen(
    client: TalosClient,
    onBack: () -> Unit
) {
    var selectedNode by remember { mutableStateOf<CanvasAgentNode?>(null) }
    var selectedFilter by remember { mutableStateOf("All") }

    val agents = remember {
        mutableStateListOf(
            CanvasAgentNode(
                id = "root",
                name = "TALOS Core Orchestrator",
                role = "Gateway Parent",
                cliEngine = "Hermes Daemon",
                model = "agy/gemini-3.8-flash-tiered",
                reasoningEffort = "Medium",
                status = AgentStatus.RUNNING,
                turnsUsed = 14,
                commandLimit = 50
            ),
            CanvasAgentNode(
                id = "omnirush",
                name = "OmniRush Runner",
                role = "Delegate Engine",
                cliEngine = "OmniRush CLI",
                model = "agy/gemini-3.8-flash-tiered",
                reasoningEffort = "Medium",
                status = AgentStatus.RUNNING,
                turnsUsed = 22,
                commandLimit = 50,
                parentId = "root"
            ),
            CanvasAgentNode(
                id = "opencode",
                name = "OpenCode Zen Bridge",
                role = "Coding Specialist",
                cliEngine = "OpenCode CLI",
                model = "space-bunny-free",
                reasoningEffort = "High",
                status = AgentStatus.THINKING,
                turnsUsed = 8,
                commandLimit = 40,
                parentId = "root"
            ),
            CanvasAgentNode(
                id = "sub_compose",
                name = "Compose Motion Subagent",
                role = "Child Worker",
                cliEngine = "OmniRush",
                model = "agy/gemini-3.8-flash-tiered",
                reasoningEffort = "Medium",
                status = AgentStatus.RUNNING,
                turnsUsed = 18,
                commandLimit = 50,
                parentId = "omnirush"
            ),
            CanvasAgentNode(
                id = "sub_verifier",
                name = "Adversarial Verifier",
                role = "QA & CI Gate",
                cliEngine = "OpenCode",
                model = "nemotron-3-ultra-free",
                reasoningEffort = "High",
                status = AgentStatus.VERIFYING,
                turnsUsed = 5,
                commandLimit = 30,
                parentId = "opencode"
            ),
            CanvasAgentNode(
                id = "sub_rust",
                name = "Rust Core Engine",
                role = "Child Worker",
                cliEngine = "Hermes Subagent",
                model = "agy/gemini-3.8-flash-tiered",
                reasoningEffort = "Medium",
                status = AgentStatus.IDLE,
                turnsUsed = 12,
                commandLimit = 50,
                parentId = "root"
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Agent Canvas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "6 active agents in parallel mesh",
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
                    IconButton(onClick = {
                        // Spawn new subagent node
                        val newId = "agent_${System.currentTimeMillis() % 1000}"
                        agents.add(
                            CanvasAgentNode(
                                id = newId,
                                name = "Delegated Worker #$newId",
                                role = "Child Subagent",
                                cliEngine = "OmniRush",
                                model = "agy/gemini-3.8-flash-tiered",
                                reasoningEffort = "Medium",
                                status = AgentStatus.RUNNING,
                                turnsUsed = 1,
                                commandLimit = 50,
                                parentId = "omnirush"
                            )
                        )
                    }) {
                        Icon(
                            imageVector = Icons.Default.AddCircleOutline,
                            contentDescription = "Spawn Agent",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Filter Pills Row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(listOf("All", "Running", "Thinking", "Verifying", "Idle")) { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryAccent.copy(alpha = 0.2f),
                            selectedLabelColor = PrimaryAccent
                        )
                    )
                }
            }

            // Spatial Agent Cards Feed with Leader Connectors
            val filteredAgents = when (selectedFilter) {
                "Running" -> agents.filter { it.status == AgentStatus.RUNNING }
                "Thinking" -> agents.filter { it.status == AgentStatus.THINKING }
                "Verifying" -> agents.filter { it.status == AgentStatus.VERIFYING }
                "Idle" -> agents.filter { it.status == AgentStatus.IDLE }
                else -> agents
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredAgents, key = { it.id }) { agent ->
                    AgentNodeCard(
                        agent = agent,
                        isSelected = selectedNode?.id == agent.id,
                        onClick = { selectedNode = agent }
                    )
                }
            }
        }
    }

    // Node Inspector & Custom Command Limits Modal
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
                    Column {
                        Text(
                            text = node.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${node.role} • ${node.cliEngine}",
                            style = MaterialTheme.typography.bodySmall,
                            color = PrimaryAccent
                        )
                    }

                    StatusPill(status = node.status)
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Custom Command Limit Slider
                var commandLimit by remember(node.id) { mutableFloatStateOf(node.commandLimit.toFloat()) }
                Text(
                    text = "Custom Command Limit: ${commandLimit.toInt()} Turns",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Bounds agent execution loop before auto-pause or consensus check.",
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
                        thumbColor = PrimaryAccent,
                        activeTrackColor = PrimaryAccent
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Model & Reasoning Effort Information
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
                            Text(node.reasoningEffort, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = PrimaryAccent)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Turn Budget Used", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${node.turnsUsed} / ${node.commandLimit}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { selectedNode = null },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply & Close", color = MaterialTheme.colorScheme.onPrimary)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
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
            if (isSelected) PrimaryAccent else MaterialTheme.colorScheme.outlineVariant
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
                                    AgentStatus.THINKING -> PrimaryAccent.copy(alpha = pulseGlow)
                                    AgentStatus.VERIFYING -> Color(0xFF2DD4BF).copy(alpha = pulseGlow)
                                    AgentStatus.IDLE -> Color(0xFF64748B)
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

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${agent.role} • ${agent.cliEngine}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar representing command limit quota
            val progress = (agent.turnsUsed.toFloat() / agent.commandLimit.toFloat()).coerceIn(0f, 1f)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape),
                    color = PrimaryAccent,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerLowest
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
        AgentStatus.THINKING -> Triple("THINKING", PrimaryAccent.copy(alpha = 0.15f), PrimaryAccent)
        AgentStatus.VERIFYING -> Triple("VERIFYING", Color(0xFF2DD4BF).copy(alpha = 0.15f), Color(0xFF2DD4BF))
        AgentStatus.IDLE -> Triple("IDLE", Color(0xFF64748B).copy(alpha = 0.15f), Color(0xFF94A3B8))
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
