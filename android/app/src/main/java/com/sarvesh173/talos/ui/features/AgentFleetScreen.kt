package com.sarvesh173.talos.ui.features

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.sarvesh173.talos.data.AgentInfo
import com.sarvesh173.talos.data.DelegateInfo
import com.sarvesh173.talos.network.TalosClient
import com.sarvesh173.talos.network.fetchAgents
import com.sarvesh173.talos.network.switchAgentModel
import com.sarvesh173.talos.ui.*
import com.sarvesh173.talos.ui.agent.AgentKind
import com.sarvesh173.talos.ui.agent.AgentRuntime
import com.sarvesh173.talos.ui.agent.AgentRuntimeCard
import com.sarvesh173.talos.ui.agent.AgentSummonSheet
import com.sarvesh173.talos.ui.agent.ReasoningEffort
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun AgentFleetScreen(client: TalosClient) {
    val isDark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    var agents by remember { mutableStateOf<List<AgentInfo>>(emptyList()) }
    var delegates by remember { mutableStateOf<List<DelegateInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showSummonSheet by remember { mutableStateOf(false) }
    var selectedAgentForModel by remember { mutableStateOf<AgentInfo?>(null) }

    suspend fun doRefresh() {
        val fAgents = client.fetchAgents()
        val fDelegates = client.fetchDelegates()
        agents = fAgents
        delegates = fDelegates
    }

    fun refresh() {
        scope.launch {
            isLoading = true
            doRefresh()
            isLoading = false
        }
    }

    // Auto-refresh loop every 3 seconds to keep ticking durations updated
    LaunchedEffect(Unit) {
        while (isActive) {
            doRefresh()
            delay(3000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Action Bar
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
                    color = if (isDark) Color.White else Color(0xFF1D1D1F),
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = "${delegates.size} active delegates · ${agents.size} system engines",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Color.White.copy(0.60f) else Color.Black.copy(0.55f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Summon Button
                Box(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF007AFF))
                        .applePressable(0.96f, pressHaptic = true) {
                            showSummonSheet = true
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Summon",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Summon",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                MetalIconButton(
                    icon = Icons.Default.Refresh,
                    onClick = { refresh() },
                    contentDescription = "Refresh fleet"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading && agents.isEmpty() && delegates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Active Delegates Section
                if (delegates.isNotEmpty()) {
                    item {
                        Text(
                            text = "RUNNING DELEGATES (${delegates.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White.copy(0.50f) else Color.Black.copy(0.45f),
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    items(delegates, key = { it.id }) { delegate ->
                        AgentRuntimeCard(
                            agent = delegate.toRuntime(),
                            isDark = isDark,
                            accent = accentFor(delegate.id),
                            models = DELEGATE_MODEL_CHOICES,
                            onModelChange = { },
                            onReasoningChange = { },
                            onTaskChange = { },
                            onStop = {
                                scope.launch {
                                    client.cancelDelegate(delegate.id)
                                    doRefresh()
                                }
                            },
                            onSendTask = {
                                scope.launch {
                                    client.pauseDelegate(delegate.id)
                                    doRefresh()
                                }
                            }
                        )
                    }
                }

                // System Agents Section
                item {
                    Text(
                        text = "SYSTEM ENGINES & CLI AGENTS (${agents.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White.copy(0.50f) else Color.Black.copy(0.45f),
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                if (agents.isEmpty()) {
                    item {
                        MetalCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "No installed agents discovered on the core mesh.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    items(agents, key = { it.id }) { agent ->
                        val emblem = emblemByKey(agent.id)
                        val shape = RoundedCornerShape(18.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(if (isDark) Color(0xFF161822) else Color.White)
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f)
                                    ),
                                    shape
                                )
                                .appleLiquidGlassOptics(
                                    isDark = isDark,
                                    chromaticGlow = emblem.accent,
                                    shape = shape
                                )
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(emblem.accent.copy(alpha = if (isDark) 0.20f else 0.12f))
                                        .border(1.dp, emblem.accent.copy(alpha = 0.40f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AgentEmblemIcon(emblem = emblem, size = 22.dp, tint = null)
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = agent.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDark) Color.White else Color(0xFF1D1D1F)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (agent.installed) Color(0xFF34C759).copy(alpha = 0.18f)
                                                    else Color(0xFFFF9500).copy(alpha = 0.18f)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (agent.installed) "Installed" else "Missing",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (agent.installed) Color(0xFF34C759) else Color(0xFFFF9500)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = agent.currentModel ?: "Default model",
                                        fontSize = 12.sp,
                                        color = emblem.accent,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                MetalIconButton(
                                    icon = Icons.Default.Tune,
                                    onClick = { selectedAgentForModel = agent },
                                    contentDescription = "Configure agent"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Summon Bottom Sheet
    if (showSummonSheet) {
        AgentSummonSheet(
            isDark = isDark,
            onDismiss = { showSummonSheet = false },
            onSummon = { name, _, model, reasoning, ttl, prompt, _ ->
                scope.launch {
                    client.delegateChildAgent(
                        parentId = "main",
                        name = name,
                        task = prompt,
                        limit = 50,
                        model = model,
                        reasoningEffort = reasoning,
                        ttlMinutes = ttl
                    )
                    showSummonSheet = false
                    doRefresh()
                }
            }
        )
    }

    selectedAgentForModel?.let { agent ->
        AlertDialog(
            onDismissRequest = { selectedAgentForModel = null },
            title = {
                Text(text = "Switch model for ${agent.name}", color = MaterialTheme.colorScheme.onSurface)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Supported models:",
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
                TextButton(
                    onClick = { selectedAgentForModel = null },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Close", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}

/** Models a delegate can be summoned with; the first is the pinned default. */
private val DELEGATE_MODEL_CHOICES = listOf(
    "stealth/crouton-alpha",
    "deepseek/deepseek-v4.1-flash",
    "nvidia/nemotron-3.5-super-vl",
    "minimax/minimax-m3.1-flash"
)

private val DELEGATE_ACCENTS = listOf(
    Color(0xFF007AFF), Color(0xFF5856D6), Color(0xFF34C759), Color(0xFFFF9500),
    Color(0xFFAF52DE), Color(0xFFFF375F), Color(0xFF32ADE6)
)

/** A stable accent per delegate id, so cards stay distinguishable at a glance. */
private fun accentFor(id: String): Color {
    val h = id.hashCode() and Int.MAX_VALUE
    return DELEGATE_ACCENTS[h % DELEGATE_ACCENTS.size]
}

/** Wire DTO → the card's runtime model. The card owns its own tick from here. */
private fun DelegateInfo.toRuntime(): AgentRuntime = AgentRuntime(
    id = id,
    name = name,
    kind = AgentKind.Delegate,
    model = model,
    reasoning = ReasoningEffort.entries.firstOrNull { it.wireValue() == reasoningEffort } ?: ReasoningEffort.High,
    task = task,
    startedAtMillis = (startedAt * 1000).toLong(),
    ttlSeconds = ttlSeconds.toLong().takeIf { it > 0L }
)
