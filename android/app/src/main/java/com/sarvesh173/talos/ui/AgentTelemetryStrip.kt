package com.sarvesh173.talos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import com.sarvesh173.talos.ui.orb.OrbState
import com.sarvesh173.talos.ui.orb.ThinkingOrb
import com.sarvesh173.talos.ui.orb.rememberAdaptiveOrbSpeed
import com.sarvesh173.talos.network.GitDiffSummaryDto

/**
 * Live agent telemetry, dropped under the header.
 *
 * Answers the four questions the user actually has while waiting on an agent:
 *   1. what is it doing right now (thinking state, via the orb),
 *   2. how much context is it holding,
 *   3. which tools has it touched this turn,
 *   4. what did it change in the working tree — with a line-level diff.
 *
 * Everything is read from the core. An absent value renders as "—" rather than
 * a plausible-looking zero.
 */
@Composable
fun AgentTelemetryStrip(
    agentName: String,
    isDark: Boolean,
    brandColor: Color,
    modelLabel: String,
    client: TalosClient
) {
    val isTyping by client.isAgentTyping.collectAsState()
    val gitDiff by client.gitDiff.collectAsState()
    val delegation by client.delegationHierarchy.collectAsState()
    var showDiff by remember { mutableStateOf(false) }

    // Same tachometer as the message stream: the strip's orb and the in-thread
    // orb must never disagree about how fast the agent is currently working.
    val messages by client.messages.collectAsState()
    val workUnits = remember(messages) {
        messages.sumOf { it.content.length + (it.thought?.length ?: 0) }
    }
    val orbSpeed = rememberAdaptiveOrbSpeed(chunkCount = workUnits)
    val lastThought = remember(messages) {
        messages.lastOrNull { it.thought?.isNotBlank() == true }?.thought.orEmpty()
    }


    val surface = if (isDark) Color(0xFF141418).copy(alpha = 0.97f) else Color.White.copy(alpha = 0.98f)
    val shape = RoundedCornerShape(18.dp)
    val muted = if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.50f)
    val strong = if (isDark) Color.White else Color(0xFF1D1D1F)

    LaunchedEffect(Unit) {
        client.fetchGitDiff()
        client.fetchDelegationHierarchy()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(surface)
            .border(
                BorderStroke(0.8.dp, if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.07f)),
                shape
            )
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThinkingOrb(
                state = if (isTyping) OrbState.WORKING else OrbState.SOLVING,
                size = 22.dp,
                ink = brandColor,
                speed = orbSpeed,
                label = if (isTyping) "$agentName is working" else "$agentName is idle",
                announce = false
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isTyping) "Working" else "Idle",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = strong
                )
                Text(
                    text = modelLabel,
                    fontSize = 10.sp,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (lastThought.isNotBlank()) {
                    Text(
                        text = lastThought.takeLast(180),
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = muted.copy(alpha = 0.75f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Context + tools, side by side.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TelemetryCell(
                label = "Agents",
                value = delegation?.nodes?.size?.toString() ?: "—",
                isDark = isDark,
                modifier = Modifier.weight(1f)
            )
            TelemetryCell(
                label = "Files changed",
                value = gitDiff?.files?.size?.toString() ?: "—",
                isDark = isDark,
                modifier = Modifier.weight(1f)
            )
            TelemetryCell(
                label = "Lines",
                value = gitDiff?.let { "+${it.total_additions} −${it.total_deletions}" } ?: "—",
                isDark = isDark,
                modifier = Modifier.weight(1f)
            )
        }

        val diff = gitDiff
        if (diff != null && diff.files.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brandColor.copy(alpha = 0.12f))
                    .applePressable(0.97f, pressHaptic = true) { showDiff = !showDiff }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showDiff) "Hide changes" else "View changes",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = brandColor,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${diff.files.size} files",
                    fontSize = 11.sp,
                    color = muted
                )
            }

            if (showDiff) {
                Spacer(modifier = Modifier.height(8.dp))
                DiffViewer(diff = diff, isDark = isDark)
            }
        }
    }
}

@Composable
private fun TelemetryCell(
    label: String,
    value: String,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isDark) Color.White else Color(0xFF1D1D1F),
            maxLines = 1
        )
        Text(
            text = label,
            fontSize = 9.sp,
            color = if (isDark) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.40f),
            maxLines = 1
        )
    }
}

/**
 * Line-level diff: removed lines in red, added in green, everything else muted.
 * This is the "exact line it removed" view — the same red/green language the
 * user asked for, driven by the core's real diff rather than a summary string.
 */
@Composable
private fun DiffViewer(diff: GitDiffSummaryDto, isDark: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    val addBg = Color(0xFF22C55E).copy(alpha = if (isDark) 0.16f else 0.12f)
    val delBg = Color(0xFFEF4444).copy(alpha = if (isDark) 0.16f else 0.12f)
    val addInk = if (isDark) Color(0xFF4ADE80) else Color(0xFF15803D)
    val delInk = if (isDark) Color(0xFFF87171) else Color(0xFFB91C1C)

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .clip(shape)
            .background(if (isDark) Color(0xFF0E0E10) else Color(0xFFF7F7FA)),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(diff.files) { file ->
            Text(
                text = file.path,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = if (isDark) Color.White.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.60f),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            file.lines.forEach { line ->
                val (bg, ink, prefix) = when (line.line_type) {
                    "add" -> Triple(addBg, addInk, "+")
                    "del", "delete", "remove" -> Triple(delBg, delInk, "−")
                    else -> Triple(Color.Transparent, if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.55f), " ")
                }
                Text(
                    text = "$prefix ${line.content}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = ink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bg)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
