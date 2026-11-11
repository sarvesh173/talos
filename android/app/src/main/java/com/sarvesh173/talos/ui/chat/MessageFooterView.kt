package com.sarvesh173.talos.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.ui.applePressable
import kotlin.math.abs

/**
 * The line beneath an agent's message: how long it took, how fast it wrote,
 * how many tokens, how many tool calls, and what it touched.
 *
 * Everything here is collapsed to one compact row by default and expands in
 * place. The reason is that this is *provenance*, not content: a user reading
 * the answer should see it is there and be able to audit it, without the
 * metadata competing with the prose.
 *
 * Numbers are formatted for scanning, not for precision: "1.2s" beats "1187ms",
 * and a token count of "12.4k" beats "12437" at 11sp.
 */
data class MessageStats(
    val latencyMs: Int? = null,
    val ttftMs: Int? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val tokensPerSecond: Float? = null,
    val model: String? = null,
    val reasoningTokens: Int? = null,
    val costUsd: Double? = null,
    val toolCalls: List<ToolCallSummary> = emptyList(),
    val filesTouched: List<FileTouchSummary> = emptyList()
) {
    val hasAnything: Boolean
        get() = latencyMs != null || promptTokens != null || toolCalls.isNotEmpty() ||
            filesTouched.isNotEmpty() || model != null

    val totalAdditions: Int get() = filesTouched.sumOf { it.additions }
    val totalDeletions: Int get() = filesTouched.sumOf { it.deletions }
}

data class ToolCallSummary(
    val name: String,
    val summary: String,
    val status: String,
    val durationMs: Int? = null
)

data class FileTouchSummary(
    val path: String,
    val kind: String,
    val additions: Int = 0,
    val deletions: Int = 0,
    /** Unified diff; null for a pure read. */
    val diff: String? = null,
    /** Thumbnail for an image or PDF the agent looked at. */
    val mediaUrl: String? = null
) {
    val fileName: String get() = path.substringAfterLast('/')
    val isWrite: Boolean get() = kind != "read"
}

/**
 * The footer itself.
 *
 * @param onOpenDiff opens the line-level diff for a file that changed.
 * @param onOpenTool opens the full tool-call detail (arguments + output).
 */
@Composable
fun MessageFooterView(
    stats: MessageStats,
    isDark: Boolean,
    accent: Color,
    onOpenDiff: (FileTouchSummary) -> Unit = {},
    onOpenTool: (ToolCallSummary) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!stats.hasAnything) return

    var expanded by remember { mutableStateOf(false) }
    val strong = if (isDark) Color.White.copy(alpha = 0.92f) else Color(0xFF1D1D1F)
    val muted = if (isDark) Color.White.copy(alpha = 0.42f) else Color.Black.copy(alpha = 0.38f)
    val hairline = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)

    Column(modifier = modifier.fillMaxWidth()) {
        // ---- the one-line summary ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 34.dp)
                .clip(RoundedCornerShape(12.dp))
                .applePressable(0.98f, pressHaptic = true) { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            stats.latencyMs?.let { ms ->
                MetricChip(text = formatDuration(ms), muted = muted)
                Dot(muted)
            }
            stats.tokensPerSecond?.let { tps ->
                MetricChip(text = "${formatRate(tps)} tok/s", muted = muted)
                Dot(muted)
            }
            stats.completionTokens?.let { tokens ->
                MetricChip(text = "${formatCount(tokens)} tok", muted = muted)
                Dot(muted)
            }
            if (stats.toolCalls.isNotEmpty()) {
                MetricChip(
                    text = "${stats.toolCalls.size} ${if (stats.toolCalls.size == 1) "tool" else "tools"}",
                    muted = muted
                )
                Dot(muted)
            }
            if (stats.filesTouched.isNotEmpty()) {
                // Red/green inline: the fastest possible read of "did this
                // change anything, and did it mostly add or remove".
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (stats.totalAdditions > 0) {
                        Text("+${stats.totalAdditions}", fontSize = 11.sp, color = Color(0xFF32D74B), fontFamily = FontFamily.Monospace)
                    }
                    if (stats.totalAdditions > 0 && stats.totalDeletions > 0) {
                        Text(" ", fontSize = 11.sp)
                    }
                    if (stats.totalDeletions > 0) {
                        Text("−${stats.totalDeletions}", fontSize = 11.sp, color = Color(0xFFFF453A), fontFamily = FontFamily.Monospace)
                    }
                }
                Dot(muted)
            }
            if (stats.model != null) {
                Text(
                    text = stats.model.substringAfterLast('/'),
                    fontSize = 11.sp,
                    color = muted.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.width(4.dp))
            // Chevron rotates rather than swapping glyphs so the row does not
            // reflow when it opens.
            val rotation by animateFloatAsState(
                targetValue = if (expanded) 180f else 0f,
                label = "footer_chevron"
            )
            Text(
                text = "⌄",
                fontSize = 11.sp,
                color = muted,
                modifier = Modifier.rotate(rotation)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.8.dp)
                        .background(hairline)
                )
                Spacer(modifier = Modifier.height(8.dp))

                // ---- timing ----
                if (stats.latencyMs != null || stats.ttftMs != null || stats.promptTokens != null) {
                    DetailRow(
                        label = "Timing",
                        value = buildString {
                            stats.latencyMs?.let { append("${formatDuration(it)} total") }
                            stats.ttftMs?.let {
                                if (isNotEmpty()) append(" · ")
                                append("${formatDuration(it)} to first token")
                            }
                        },
                        muted = muted,
                        strong = strong
                    )
                }

                if (stats.promptTokens != null || stats.completionTokens != null) {
                    DetailRow(
                        label = "Tokens",
                        value = buildString {
                            stats.promptTokens?.let { append("${formatCount(it)} in") }
                            stats.completionTokens?.let {
                                if (isNotEmpty()) append(" · ")
                                append("${formatCount(it)} out")
                            }
                            stats.reasoningTokens?.let {
                                if (it > 0) {
                                    if (isNotEmpty()) append(" · ")
                                    append("${formatCount(it)} reasoning")
                                }
                            }
                        },
                        muted = muted,
                        strong = strong
                    )
                }

                if (stats.tokensPerSecond != null) {
                    DetailRow(
                        label = "Speed",
                        value = "${formatRate(stats.tokensPerSecond)} tokens/second",
                        muted = muted,
                        strong = strong
                    )
                }

                stats.costUsd?.let { cost ->
                    DetailRow(
                        label = "Cost",
                        value = "$" + "%.4f".format(cost),
                        muted = muted,
                        strong = strong
                    )
                }

                stats.model?.let { model ->
                    DetailRow(label = "Model", value = model, muted = muted, strong = strong)
                }

                // ---- tool calls ----
                if (stats.toolCalls.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionLabel("Tool calls", muted)
                    stats.toolCalls.forEach { call ->
                        ToolCallRow(call, isDark, muted, strong, accent) { onOpenTool(call) }
                    }
                }

                // ---- files ----
                if (stats.filesTouched.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionLabel("Files", muted)
                    stats.filesTouched.forEach { file ->
                        FileTouchRow(file, isDark, muted, strong, accent) { onOpenDiff(file) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricChip(text: String, muted: Color) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = muted,
        fontFamily = FontFamily.Monospace,
        maxLines = 1
    )
}

@Composable
private fun Dot(muted: Color) {
    Text(" · ", fontSize = 11.sp, color = muted.copy(alpha = 0.55f))
}

@Composable
private fun SectionLabel(text: String, muted: Color) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.7.sp,
        color = muted.copy(alpha = 0.75f),
        modifier = Modifier.padding(bottom = 4.dp, top = 2.dp)
    )
}

@Composable
private fun DetailRow(label: String, value: String, muted: Color, strong: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.5.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = muted,
            modifier = Modifier.width(58.dp)
        )
        Text(
            text = value,
            fontSize = 11.sp,
            color = strong.copy(alpha = 0.8f),
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ToolCallRow(
    call: ToolCallSummary,
    isDark: Boolean,
    muted: Color,
    strong: Color,
    accent: Color,
    onClick: () -> Unit
) {
    val statusColor = when (call.status) {
        "success" -> Color(0xFF32D74B)
        "failed" -> Color(0xFFFF453A)
        "running" -> accent
        else -> muted
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(8.dp))
            .applePressable(0.99f) { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = call.name,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = strong.copy(alpha = 0.85f),
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = call.summary,
            fontSize = 11.sp,
            color = muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        call.durationMs?.let {
            Text(
                text = formatDuration(it),
                fontSize = 11.sp,
                color = muted.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun FileTouchRow(
    file: FileTouchSummary,
    isDark: Boolean,
    muted: Color,
    strong: Color,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(8.dp))
            .applePressable(0.99f) { if (file.isWrite) onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when (file.kind) {
                "create" -> "+"
                "delete" -> "−"
                "write" -> "±"
                else -> "·"
            },
            fontSize = 11.sp,
            color = if (file.isWrite) accent else muted,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(12.dp)
        )
        Text(
            text = file.fileName,
            fontSize = 11.sp,
            color = strong.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (file.additions > 0) {
            Text("+${file.additions}", fontSize = 11.sp, color = Color(0xFF32D74B), fontFamily = FontFamily.Monospace)
        }
        if (file.additions > 0 && file.deletions > 0) {
            Text(" ", fontSize = 11.sp)
        }
        if (file.deletions > 0) {
            Text("−${file.deletions}", fontSize = 11.sp, color = Color(0xFFFF453A), fontFamily = FontFamily.Monospace)
        }
    }
}

// -- formatting -------------------------------------------------------------

/**
 * Durations read at a glance: keep the unit that carries the information and
 * drop the ones that do not. A 3-minute generation should not render as
 * "0.0h", and a 40ms tool call should not render as "0.0s".
 */
internal fun formatDuration(ms: Int): String {
    val v = abs(ms)
    return when {
        v < 1_000 -> "${v}ms"
        v < 60_000 -> "%.1fs".format(v / 1000.0)
        v < 3_600_000 -> {
            val m = v / 60_000
            val s = (v % 60_000) / 1000
            if (s == 0) "${m}m" else "${m}m ${s}s"
        }
        else -> {
            val h = v / 3_600_000
            val m = (v % 3_600_000) / 60_000
            "${h}h ${m}m"
        }
    }
}

/** Token counts: exact under 1000, then one decimal of k/M. */
internal fun formatCount(n: Int): String = when {
    n < 1_000 -> n.toString()
    n < 1_000_000 -> "%.1fk".format(n / 1000.0)
    else -> "%.2fM".format(n / 1_000_000.0)
}

internal fun formatRate(tps: Float): String = when {
    tps < 10f -> "%.1f".format(tps)
    else -> tps.toInt().toString()
}


