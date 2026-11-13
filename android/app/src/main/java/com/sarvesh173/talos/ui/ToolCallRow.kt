package com.sarvesh173.talos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.sarvesh173.talos.ui.chat.ToolCallSummary

/**
 * A tool call, collapsed to one line.
 *
 * Ported from dsh-Go's tool rows and the ChatGPT "Searched 13 websites" chip.
 * The point is the same in both: a turn can fire twenty tools, and showing all
 * twenty in full makes the reply unreadable. One line per call, grouped, with
 * the detail behind a tap.
 *
 * @param calls every call in this group — grouped because "Read 4 files" is
 *   information, while four identical rows is noise.
 */
@Composable
fun ToolCallRow(
    calls: List<ToolCallSummary>,
    isDark: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (calls.isEmpty()) return

    var expanded by remember(calls.size) { mutableStateOf(false) }
    val secondary = if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.45f)
    val running = calls.count { it.status == "running" }
    val failed = calls.count { it.status == "failed" }

    // Group identical verbs so "Read 4 files" says that, not "Read, Read,
    // Read, Read".
    val label = remember(calls) { groupLabel(calls) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { expanded = !expanded }
                .heightIn(min = 30.dp)
                .minimumInteractiveComponentSize()
                .padding(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 24dp lead column — the same column the thinking glyph sits in,
            // so a turn's reasoning and its tools read as one list.
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when {
                                failed > 0 -> Color(0xFFFF3B30)
                                running > 0 -> Color(0xFFFF9500)
                                else -> secondary.copy(alpha = 0.5f)
                            }
                        )
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (failed > 0) Color(0xFFFF3B30) else secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (expanded) "⌃" else "⌄",
                fontSize = 13.sp,
                color = secondary
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(180)) + fadeIn(tween(140)),
            exit = shrinkVertically(tween(150)) + fadeOut(tween(110))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 2.dp, bottom = 4.dp)
            ) {
                calls.forEach { call ->
                    ToolCallDetail(call, isDark)
                }
            }
        }
    }
}

@Composable
private fun ToolCallDetail(call: ToolCallSummary, isDark: Boolean) {
    val secondary = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.42f)
    val tint = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tint)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = call.name,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = if (isDark) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (call.durationMs != null && call.durationMs > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formatToolDuration(call.durationMs),
                    fontSize = 11.sp,
                    color = secondary
                )
            }
        }
        if (call.summary.isNotBlank() && call.summary != call.name) {
            Text(
                text = call.summary,
                fontSize = 11.5.sp,
                color = secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/**
 * "Read 4 files", "Searched 3 sources", "Edited main.py".
 *
 * Reads as English because a count of identical verbs is the common case, and
 * "read × 4" reads like a debug line.
 */
internal fun groupLabel(calls: List<ToolCallSummary>): String {
    if (calls.size == 1) {
        val only = calls.first()
        return only.summary.ifBlank { only.name }
    }
    val names = calls.map { it.name.lowercase() }
    val first = names.first()
    val allSame = names.all { it == first }
    if (!allSame) return "${calls.size} tool calls"
    val plural = if (calls.size == 1) "" else "s"
    return "${verbPastTense(first)} ${calls.size} ${nounFor(first)}$plural"
}

/** Tool names are verbs in the imperative ("read", "edit"); past tense reads better. */
private fun verbPastTense(name: String): String = when (name) {
    "read" -> "Read"
    "edit" -> "Edited"
    "write" -> "Wrote"
    "bash" -> "Ran"
    "webfetch" -> "Fetched"
    "search" -> "Searched"
    "glob" -> "Matched"
    "grep" -> "Grepped"
    "list" -> "Listed"
    "todowrite" -> "Planned"
    else -> name.replaceFirstChar { it.uppercase() }
}

private fun nounFor(name: String): String = when (name) {
    "read" -> "file"
    "edit", "write" -> "file"
    "bash" -> "command"
    "webfetch" -> "source"
    "search", "grep", "glob" -> "match"
    "list" -> "directory"
    else -> "call"
}

internal fun formatToolDuration(ms: Int): String = when {
    ms < 1000 -> "${ms}ms"
    ms < 60_000 -> "${ms / 1000}s"
    else -> "${ms / 60_000}m ${(ms % 60_000) / 1000}s"
}