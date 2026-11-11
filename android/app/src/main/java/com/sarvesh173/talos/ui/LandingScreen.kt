package com.sarvesh173.talos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.R
import com.sarvesh173.talos.network.TalosClient
import com.sarvesh173.talos.network.SearchHitDto
import com.sarvesh173.talos.ui.agents.AgentRoster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * TALOS agent hub — renders ONLY real data from GET /api/agents/roster.
 *
 * No fabricated agents, sessions, ids, titles or counts exist in this file.
 * On request failure the hub shows an honest empty state and never falls
 * back to invented rows. Not-installed agents render as unavailable.
 */

// Apple HIG surface ramp and single accent.
private val CanvasDark = Color(0xFF000000)
private val CanvasLight = Color(0xFFF2F2F7)
private val CardDark = Color(0xFF1C1C1E)
private val CardLight = Color(0xFFFFFFFF)
private val ElevatedDark = Color(0xFF2C2C2E)
private val ElevatedLight = Color(0xFFE5E5EA)
private val AccentDark = Color(0xFF0A84FF)
private val AccentLight = Color(0xFF0071E3)
private val LandingStatusGreen = Color(0xFF30D158)
private val MutedDark = Color(0xFF98989D)

private val Tnum = TextStyle(fontFeatureSettings = "tnum")

/** One real session row from the roster endpoint. */
private data class RealSession(
    val id: String,
    val title: String,
    val ageSeconds: Long?,
    val status: String?,
    val messageCount: Int?,
)

/** One real agent from the roster endpoint, enriched with roster metadata. */
private data class RealAgent(
    val id: String,
    val name: String,
    val installed: Boolean,
    val sessionCount: Int,
    val sessions: List<RealSession>,
    val sessionsError: String?,
)

private fun iconForAgent(agentId: String): Int = when (agentId) {
    "opencode" -> R.drawable.ic_opencode
    "hermes" -> R.drawable.ic_hermes
    "claude-code", "claudecode" -> R.drawable.ic_claude
    "codex" -> R.drawable.ic_codex
    "omnirush" -> R.drawable.ic_omnirush
    "openclaw" -> R.drawable.ic_openclaw
    else -> R.drawable.ic_core
}

private fun sessionRoomId(agentId: String): String = when (agentId) {
    "hermes", "alya", "openclaw" -> "the-council"
    "opencode", "claudecode", "claude-code", "codex", "omnirush" -> "war-room"
    else -> "the-council"
}

private fun formatAge(ageSeconds: Long?): String? {
    if (ageSeconds == null || ageSeconds < 0) return null
    return when {
        ageSeconds < 60 -> "now"
        ageSeconds < 3600 -> "${ageSeconds / 60}m"
        ageSeconds < 86400 -> "${ageSeconds / 3600}h"
        else -> "${ageSeconds / 86400}d"
    }
}

private fun isActiveStatus(status: String?): Boolean =
    status != null && (status.equals("active", ignoreCase = true) || status.equals("running", ignoreCase = true))

private fun parseRoster(body: String): List<RealAgent> {
    val agentsArray: JSONArray = JSONObject(body).optJSONArray("agents") ?: return emptyList()
    val out = ArrayList<RealAgent>(agentsArray.length())
    for (i in 0 until agentsArray.length()) {
        val a = agentsArray.optJSONObject(i) ?: continue
        val sessions = ArrayList<RealSession>()
        val sessionsArray = a.optJSONArray("sessions")
        if (sessionsArray != null) {
            for (j in 0 until sessionsArray.length()) {
                val s = sessionsArray.optJSONObject(j) ?: continue
                sessions.add(
                    RealSession(
                        id = s.optString("id", ""),
                        title = s.optString("title", "Untitled session").ifBlank { "Untitled session" },
                        ageSeconds = if (s.has("age_seconds") && !s.isNull("age_seconds")) s.optLong("age_seconds") else null,
                        status = if (s.has("status") && !s.isNull("status")) s.optString("status") else null,
                        messageCount = if (s.has("message_count") && !s.isNull("message_count")) s.optInt("message_count") else null,
                    )
                )
            }
        }
        out.add(
            RealAgent(
                id = a.optString("id", ""),
                name = a.optString("name", a.optString("id", "Agent")).ifBlank { a.optString("id", "Agent") },
                installed = a.optBoolean("installed", false),
                sessionCount = a.optInt("session_count", sessions.size),
                sessions = sessions,
                sessionsError = if (a.has("sessions_error") && !a.isNull("sessions_error")) a.optString("sessions_error") else null,
            )
        )
    }
    return out
}

private suspend fun fetchRoster(client: TalosClient): List<RealAgent> = withContext(Dispatchers.IO) {
    val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    val builder = Request.Builder().url(client.baseUrl.trimEnd('/') + "/api/agents/roster").get()
    if (client.apiToken.isNotBlank()) builder.header("Authorization", "Bearer " + client.apiToken)
    http.newCall(builder.build()).execute().use { resp ->
        if (!resp.isSuccessful) error("HTTP " + resp.code)
        val body = resp.body?.string() ?: error("Empty roster response")
        parseRoster(body)
    }
}

@Composable
fun LandingScreen(
    client: TalosClient,
    isDark: Boolean = true,
    onToggleTheme: () -> Unit = {},
    onGetStarted: () -> Unit = {},
    onConnect: ((String, String) -> Unit)? = null,
    onOpenWizard: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onOpenAgentChat: ((String, String, String, Int, Color) -> Unit)? = null,
) {
    val accent = if (isDark) AccentDark else AccentLight
    val canvas = if (isDark) CanvasDark else CanvasLight
    val card = if (isDark) CardDark else CardLight
    val elevated = if (isDark) ElevatedDark else ElevatedLight
    val hairline = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
    val primaryText = if (isDark) Color.White else Color(0xFF1D1D1F)
    val secondaryText = if (isDark) Color.White.copy(alpha = 0.60f) else Color.Black.copy(alpha = 0.60f)
    val tertiaryText = if (isDark) Color.White.copy(alpha = 0.40f) else Color.Black.copy(alpha = 0.40f)

    var selectedAgentId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchHits by remember { mutableStateOf<List<SearchHitDto>>(emptyList()) }

    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim()
        if (q.length >= 2) {
            kotlinx.coroutines.delay(250)
            searchHits = client.searchConversations(q)?.hits ?: emptyList()
        } else {
            searchHits = emptyList()
        }
    }
    var agents by remember { mutableStateOf<List<RealAgent>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshTick) {
        loading = true
        failed = false
        try {
            agents = fetchRoster(client)
        } catch (_: Exception) {
            agents = emptyList()
            failed = true
        }
        loading = false
    }

    val selectedAgent = remember(selectedAgentId, agents) {
        agents.firstOrNull { it.id == selectedAgentId }
    }

    BackHandler(enabled = selectedAgentId != null) {
        selectedAgentId = null
    }

    fun openChat(agent: RealAgent, title: String) {
        onOpenAgentChat?.invoke(sessionRoomId(agent.id), title, agent.name, iconForAgent(agent.id), accent)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(canvas)
    ) {
        AnimatedContent(
            targetState = selectedAgentId,
            transitionSpec = {
                (fadeIn(animationSpec = tween(180)) + slideInHorizontally(initialOffsetX = { it / 4 }))
                    .togetherWith(
                        fadeOut(animationSpec = tween(180)) + slideOutHorizontally(targetOffsetX = { -it / 4 })
                    )
            },
            modifier = Modifier.fillMaxSize(),
        ) { agentId ->
            val agent = agents.firstOrNull { it.id == agentId }
            if (agent == null) {
                // Root hub: real agents only.
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 96.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Agents",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryText,
                                letterSpacing = (-0.25).sp,
                            )
                            if (!loading && !failed) {
                                Text(
                                    text = "${agents.count { it.installed }} of ${agents.size} installed",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = secondaryText,
                                    style = Tnum,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (loading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = accent, modifier = Modifier.size(24.dp))
                            }
                        }
                    } else if (failed) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = card,
                                border = BorderStroke(0.5.dp, hairline),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = "Couldn't reach the host",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryText,
                                        letterSpacing = (-0.15).sp,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "No agents were loaded. Check the connection and try again.",
                                        fontSize = 14.sp,
                                        color = secondaryText,
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(elevated)
                                            .clickable { refreshTick++ }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Retry",
                                            tint = accent,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Retry",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = accent,
                                            letterSpacing = (-0.15).sp,
                                        )
                                    }
                                }
                            }
                        }
                    } else if (agents.isEmpty()) {
                        item {
                            Text(
                                text = "No agents reported by the host.",
                                fontSize = 14.sp,
                                color = secondaryText,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }

                    if (searchHits.isNotEmpty()) {
                        item {
                            Text(
                                text = "Conversation hits",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = secondaryText,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                            )
                        }
                        items(searchHits, key = { it.message_id }) { hit ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = card,
                                border = BorderStroke(0.5.dp, hairline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        client.switchRoom(hit.room_id)
                                        onOpenAgentChat?.invoke(
                                            hit.room_id,
                                            hit.room_name.ifBlank { "Search Result" },
                                            hit.sender_name.ifBlank { "Agent" },
                                            iconForAgent(hit.room_id),
                                            accent
                                        )
                                    }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = hit.room_name.ifBlank { hit.room_id },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = accent,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = hit.sender_name,
                                            fontSize = 12.sp,
                                            color = tertiaryText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = hit.snippet,
                                        fontSize = 14.sp,
                                        color = primaryText,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    } else {
                        val visible = if (searchQuery.isBlank()) agents else agents.filter {
                            val rosterEntry = AgentRoster.byId(it.id)
                            it.name.contains(searchQuery, ignoreCase = true) ||
                                (rosterEntry?.subtitle?.contains(searchQuery, ignoreCase = true) == true)
                        }
                        items(visible, key = { it.id }) { realAgent ->
                            val rosterEntry = AgentRoster.byId(realAgent.id)
                            val subtitle = rosterEntry?.subtitle ?: "CLI agent"
                            val lifecycleText = rosterEntry?.lifecycle?.label
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = card,
                                border = BorderStroke(0.5.dp, hairline),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = realAgent.installed) { selectedAgentId = realAgent.id }
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .padding(end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(elevated),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            painter = painterResource(id = iconForAgent(realAgent.id)),
                                            contentDescription = realAgent.name,
                                            tint = if (realAgent.installed) primaryText else tertiaryText,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = realAgent.name,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (realAgent.installed) primaryText else tertiaryText,
                                            letterSpacing = (-0.15).sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (realAgent.installed) {
                                                if (lifecycleText != null) "$subtitle · $lifecycleText" else subtitle
                                            } else {
                                                "Not installed on this host"
                                            },
                                            fontSize = 12.sp,
                                            color = secondaryText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (realAgent.installed) {
                                        Text(
                                            text = "${realAgent.sessionCount} sessions",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = secondaryText,
                                            style = Tnum,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Agent detail: real sessions only.
                val query = searchQuery.trim()
                val visibleSessions = if (query.isBlank()) agent.sessions else agent.sessions.filter {
                    it.title.contains(query, ignoreCase = true)
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 96.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // The glyph is small; the target must not be.
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable { selectedAgentId = null },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back to agents",
                                    tint = accent,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = agent.name,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryText,
                                letterSpacing = (-0.25).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${agent.sessions.size} sessions",
                            fontSize = 12.sp,
                            color = secondaryText,
                            style = Tnum,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (agent.sessions.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = card,
                                border = BorderStroke(0.5.dp, hairline),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = agent.sessionsError ?: "No sessions yet for this agent.",
                                    fontSize = 14.sp,
                                    color = secondaryText,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                    } else {
                        items(visibleSessions, key = { it.id }) { session ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = card,
                                border = BorderStroke(0.5.dp, hairline),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { openChat(agent, session.title) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (isActiveStatus(session.status)) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(LandingStatusGreen),
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = session.title,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = primaryText,
                                            letterSpacing = (-0.15).sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            val age = formatAge(session.ageSeconds)
                                            if (age != null) {
                                                Text(
                                                    text = age,
                                                    fontSize = 12.sp,
                                                    color = if (isDark) MutedDark else secondaryText,
                                                    style = Tnum,
                                                )
                                            }
                                            if (session.messageCount != null) {
                                                Text(
                                                    text = "${session.messageCount} messages",
                                                    fontSize = 12.sp,
                                                    color = secondaryText,
                                                    style = Tnum,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Top bar.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = card,
                border = BorderStroke(0.5.dp, hairline),
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectedAgentId != null) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedAgentId = null }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = primaryText,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Back",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = primaryText,
                                letterSpacing = (-0.15).sp,
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedAgent?.let { openChat(it, "Active conversation") }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Chat",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = primaryText,
                                letterSpacing = (-0.15).sp,
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = card,
                border = BorderStroke(0.5.dp, hairline),
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 32.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onToggleTheme() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Toggle theme",
                            tint = primaryText,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 32.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenSettings?.invoke() ?: onOpenWizard?.invoke() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "More options",
                            tint = primaryText,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // Bottom search + new-session controls.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = card,
                border = BorderStroke(0.5.dp, hairline),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = tertiaryText,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = primaryText,
                            letterSpacing = (-0.1).sp,
                        ),
                        cursorBrush = SolidColor(accent),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = if (selectedAgentId != null) "Search sessions" else "Search agents",
                                    fontSize = 14.sp,
                                    color = tertiaryText,
                                    letterSpacing = (-0.1).sp,
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = accent,
                modifier = Modifier
                    .size(48.dp)
                    .clickable(enabled = selectedAgent != null) {
                        selectedAgent?.let { openChat(it, "New ${it.name} session") }
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New session",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
