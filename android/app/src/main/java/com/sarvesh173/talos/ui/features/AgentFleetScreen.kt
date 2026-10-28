package com.sarvesh173.talos.ui.features

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.core.motion.MotionTokens
import com.sarvesh173.talos.data.AgentInfo
import com.sarvesh173.talos.network.TalosClient
import com.sarvesh173.talos.network.fetchAgents
import com.sarvesh173.talos.network.switchAgentModel
import com.sarvesh173.talos.ui.*
import kotlinx.coroutines.launch

@Composable
fun AgentFleetScreen(client: TalosClient) {
    val scope = rememberCoroutineScope()
    var agents by remember { mutableStateOf<List<AgentInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedAgentForModel by remember { mutableStateOf<AgentInfo?>(null) }

    fun refresh() {
        scope.launch {
            isLoading = true
            agents = client.fetchAgents()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Agent Fleet",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${agents.size} Registered CLI Agents",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MetalIconButton(
                icon = Icons.Default.Refresh,
                onClick = { refresh() },
                contentDescription = "Refresh Fleet"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (agents.isEmpty() && !isLoading) {
            MetalCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "No agents discovered on the core mesh.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(agents, key = { it.id }) { agent ->
                    MetalCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val emblem = emblemByKey(agent.id)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF0F172A))
                                    .border(1.dp, emblem.accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                AgentEmblemIcon(emblem = emblem, size = 22.dp, tint = null)
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = agent.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    MetalStatusPill(
                                        text = if (agent.installed) "Installed" else "Missing",
                                        isActive = agent.installed
                                    )
                                }
                                Text(
                                    text = agent.currentModel ?: "Default Model",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            MetalIconButton(
                                icon = Icons.Default.Tune,
                                onClick = { selectedAgentForModel = agent },
                                contentDescription = "Configure Agent"
                            )
                        }
                    }
                }
            }
        }
    }

    selectedAgentForModel?.let { agent ->
        AlertDialog(
            onDismissRequest = { selectedAgentForModel = null },
            title = {
                Text(text = "Switch Model for ${agent.name}", color = MaterialTheme.colorScheme.onSurface)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Supported Models:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    agent.supportedModels.forEach { modelName ->
                        MetalButton(
                            text = modelName,
                            onClick = {
                                scope.launch {
                                    client.switchAgentModel(agent.id, modelName)
                                    selectedAgentForModel = null
                                    refresh()
                                }
                            },
                            variant = if (agent.currentModel == modelName) MetalButtonVariant.Primary else MetalButtonVariant.Tonal,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedAgentForModel = null }) {
                    Text("Close")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}
