package com.sarvesh173.talos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient
import com.sarvesh173.talos.ui.chat.SubagentRun
import com.sarvesh173.talos.ui.chat.fetchSubagents

/**
 * The subagents this session summoned, and what each one did.
 *
 * Why this exists: OpenCode runs every summoned subagent in its own child
 * session, linked to the parent by `parentID`. The parent transcript records
 * only a `task` tool call, so the child's reasoning and tool work — the actual
 * evidence of what was delegated — never reaches the chat. This joins them back.
 *
 * @param sessionId the parent session.
 * @param onBack returns to the chat.
 */
@Composable
fun SubagentSheet(
    sessionId: String,
    client: TalosClient,
    isDark: Boolean,
    brandColor: Color,
    onBack: () -> Unit
) {
    var runs by remember(sessionId) { mutableStateOf<List<SubagentRun>?>(null) }
    var error by remember(sessionId) { mutableStateOf<String?>(null) }

    LaunchedEffect(sessionId) {
        runs = runCatching { fetchSubagents(client, sessionId) }
            .onFailure { error = it.message ?: "Could not load subagents" }
            .getOrNull()
    }

    val secondary = if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.45f)
    val tint = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)

    Column(modifier = Modifier.fillMaxSize().background(if (isDark) Color.Black else Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (isDark) Color.White else Color(0xFF1D1D1F)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Summoned agents",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) Color.White else Color(0xFF1D1D1F)
                )
                val sub = when {
                    runs == null && error == null -> "Loading…"
                    error != null -> error!!
                    runs!!.isEmpty() -> "No subagents summoned in this session"
                    else -> "${runs!!.size} run${if (runs!!.size == 1) "" else "s"}"
                }
                Text(text = sub, fontSize = 11.5.sp, color = secondary, maxLines = 1)
            }
        }

        when {
            runs == null && error == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = brandColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            runs!!.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nothing delegated yet.\nAsk the agent to split a task across subagents.",
                        fontSize = 13.sp,
                        color = secondary,
                        lineHeight = 19.sp
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 14.dp, end = 14.dp, bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(runs!!, key = { it.id }) { run ->
                        SubagentCard(run, isDark, tint, secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SubagentCard(
    run: SubagentRun,
    isDark: Boolean,
    tint: Color,
    secondary: Color
) {
    var expanded by remember(run.id) { mutableStateOf(false) }
    val title = if (isDark) Color.White.copy(alpha = 0.88f) else Color(0xFF1D1D1F)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tint)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (run.text.isNotBlank()) brandOrMuted(isDark) else secondary.copy(alpha = 0.4f))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = run.title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.size(6.dp))

        // What the child actually did. Counts read as evidence; prose is one
        // line because the card is a summary, not a transcript.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Meta(label = "${run.toolCalls} tool${if (run.toolCalls == 1) "" else "s"}", secondary = secondary)
            if (run.reasoningChars > 0) {
                Dot(secondary)
                Meta(label = "thought ${run.reasoningChars} chars", secondary = secondary)
            }
            if (run.agent != null) {
                Dot(secondary)
                Meta(label = run.agent, secondary = secondary, mono = true)
            }
        }

        if (run.tools.isNotEmpty()) {
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = run.tools.take(8).joinToString(" · "),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (run.text.isNotBlank()) {
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = if (expanded) run.text else run.text.take(220),
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = if (isDark) Color.White.copy(alpha = 0.75f) else Color.Black.copy(alpha = 0.72f)
            )
            if (run.text.length > 220) {
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = if (expanded) "Show less" else "Show more",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { expanded = !expanded }
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun brandOrMuted(isDark: Boolean): Color =
    if (isDark) Color(0xFF30D158) else Color(0xFF34C759)

@Composable
private fun Meta(label: String, secondary: Color, mono: Boolean = false) {
    Text(
        text = label,
        fontSize = 11.sp,
        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        color = secondary,
        maxLines = 1
    )
}

@Composable
private fun Dot(secondary: Color) {
    Text(text = " · ", fontSize = 11.sp, color = secondary)
}
