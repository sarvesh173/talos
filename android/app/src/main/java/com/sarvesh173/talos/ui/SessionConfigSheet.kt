package com.sarvesh173.talos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient
import com.sarvesh173.talos.ui.chat.HarnessAgent
import com.sarvesh173.talos.ui.chat.HarnessModel
import com.sarvesh173.talos.ui.chat.fetchHarnessAgents
import com.sarvesh173.talos.ui.chat.fetchHarnessModels
import com.sarvesh173.talos.ui.chat.fetchSessionConfig
import com.sarvesh173.talos.ui.chat.setSessionAgent
import com.sarvesh173.talos.ui.chat.setSessionModel
import kotlinx.coroutines.launch

/**
 * Model and agent for this session.
 *
 * The user's ask: switching the model a delegate runs on, which the harness
 * supports per session — `POST /api/session/{id}/model` and `/agent`. Both were
 * already reachable, just not from the app.
 *
 * Two lists, no tabs: the agent list is short (two primaries, two subagents)
 * and the model list is what it is. A tab per list would hide one of them
 * behind a tap for no gain.
 */
@Composable
fun SessionConfigSheet(
    sessionId: String,
    client: TalosClient,
    isDark: Boolean,
    brandColor: Color,
    currentModel: String?,
    currentAgent: String?,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    var agents by remember { mutableStateOf<List<HarnessAgent>?>(null) }
    var models by remember { mutableStateOf<List<HarnessModel>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    var liveAgent by remember(sessionId) { mutableStateOf(currentAgent) }
    var liveModel by remember(sessionId) { mutableStateOf(currentModel) }
    var pickedAgent by remember { mutableStateOf<String?>(null) }
    var pickedModel by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        val cfg = runCatching { fetchSessionConfig(client, sessionId) }.getOrNull()
        if (cfg?.agent != null) liveAgent = cfg.agent
        if (cfg?.model != null) liveModel = cfg.model
        agents = runCatching { fetchHarnessAgents(client) }.getOrNull()
        models = runCatching { fetchHarnessModels(client) }.getOrNull()
        if (agents == null && models == null) error = "Could not load agents or models"
    }

    val selectedAgent = pickedAgent ?: liveAgent
    val selectedModel = pickedModel ?: liveModel
    val changed = selectedAgent != liveAgent || selectedModel != liveModel

    val strong = if (isDark) Color.White else Color(0xFF1D1D1F)
    val muted = if (isDark) Color.White.copy(alpha = 0.50f) else Color.Black.copy(alpha = 0.45f)
    val tint = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isDark) Color(0xFF141418) else Color.White)
            .border(
                androidx.compose.foundation.BorderStroke(
                    0.8.dp,
                    if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.07f)
                ),
                shape
            )
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Model and agent",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = strong
                )
                Text(
                    text = "Applies to the next turn in this session",
                    fontSize = 11.sp,
                    color = muted
                )
            }
            Text(
                text = "Close",
                fontSize = 11.sp,
                color = brandColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onDismiss() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.size(10.dp))

        when {
            agents == null && models == null && error == null -> {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = brandColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            error != null -> Text(text = error!!, fontSize = 12.sp, color = muted)
            else -> {
                // Agents: primary and subagent, hidden harness internals dropped.
                val shown = agents!!.filter { !it.hidden }
                if (shown.isNotEmpty()) {
                    SectionLabel("Agent", muted)
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(shown, key = { it.id }) { agent ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (agent.id == pickedAgent) tint else Color.Transparent)
                                    .clickable { pickedAgent = agent.id }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = agent.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = strong
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = agent.mode,
                                            fontSize = 10.sp,
                                            color = muted
                                        )
                                    }
                                    if (agent.description.isNotBlank()) {
                                        Text(
                                            text = agent.description,
                                            fontSize = 11.sp,
                                            color = muted,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (agent.id == selectedAgent) {
                                    Text(text = "✓", fontSize = 13.sp, color = brandColor)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.size(10.dp))
                SectionLabel("Model", muted)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(models!!, key = { it.providerId + "/" + it.id }) { model ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (model.id == selectedModel) tint else Color.Transparent)
                                .clickable { pickedModel = model.id }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = model.name,
                                    fontSize = 13.sp,
                                    color = strong,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = model.providerId,
                                        fontSize = 10.5.sp,
                                        color = muted
                                    )
                                    if (model.vision) {
                                        Text(text = " · ", fontSize = 10.5.sp, color = muted)
                                        Text(text = "vision", fontSize = 10.5.sp, color = muted)
                                    }
                                    if (model.reasoning) {
                                        Text(text = " · ", fontSize = 10.5.sp, color = muted)
                                        Text(text = "reasoning", fontSize = 10.5.sp, color = muted)
                                    }
                                }
                            }
                            if (model.id == selectedModel) {
                                Text(text = "✓", fontSize = 13.sp, color = brandColor)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.size(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (changed) brandColor else tint)
                        .clickable(enabled = changed && !saving) {
                            saving = true
                            // Apply both, then report once: a half-applied switch
                            // would leave the session on a model it did not ask
                            // for, and the user would not know which half failed.
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                var ok = true
                                if (selectedAgent != liveAgent && selectedAgent != null) {
                                    ok = setSessionAgent(client, sessionId, selectedAgent) && ok
                                }
                                if (selectedModel != liveModel && selectedModel != null) {
                                    ok = setSessionModel(client, sessionId, selectedModel) && ok
                                }
                                saving = false
                                if (ok) onApply()
                            }
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            saving -> "Applying…"
                            changed -> "Apply"
                            else -> "No change"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (changed) (if (isDark) Color.Black else Color.White) else muted
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.padding(bottom = 5.dp, start = 2.dp)
    )
}