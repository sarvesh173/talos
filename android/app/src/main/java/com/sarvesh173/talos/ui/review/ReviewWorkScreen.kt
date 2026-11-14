package com.sarvesh173.talos.ui.review

import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy

import android.content.Context
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * "Review its work" screen: what one agent changed in one run, driven by the
 * tool-call ledger (GET /api/agents/{agentId}/review) rather than the unscoped
 * working-tree diff shown by CodeDiffViewer.
 *
 * Server shape (server/diff_review.py build_review):
 *   { agent_id, since_ms, summary: { total_files, total_added, total_removed,
 *       files_created, files_deleted, files_renamed, largest_file,
 *       largest_file_lines, truncated_any, is_empty },
 *     changes: [{ path, old_path, status, added_lines, removed_lines,
 *       diff_text, hunks: [{ old_start, old_end, new_start, new_end,
 *       range_label, lines: [{ kind, text, old_num, new_num }] }],
 *       is_binary, truncated, total_diff_lines }] }
 *
 * Wire-up (toolbar side — not edited here, by file-ownership rule):
 *   ReviewWorkScreen(client = TalosClient.shared(context) ?: return@Composable,
 *                    agentId = agent.id, sinceMs = runStartMs,
 *                    onDone = { /* return to the agent */ })
 * Token and base URL are read from [TalosClient.baseUrl] / [TalosClient.apiToken]
 * on every fetch, matching KanbanScreen.
 *
 * Apple HIG: dark monochrome cards, one green accent for added lines only,
 * sentence case, 4dp grid, hairline borders, no drop shadows, no emoji.
 */
object ReviewApi {
    const val REVIEW_PATH: String = "/api/agents"
    const val MAX_DIFF_LINES: Int = 500

    fun reviewUrl(baseUrl: String, agentId: String, sinceMs: Long?): String {
        val encoded = java.net.URLEncoder.encode(agentId, "UTF-8")
        val base = baseUrl.trimEnd('/') + REVIEW_PATH + "/" + encoded + "/review"
        return if (sinceMs != null) base + "?since=" + sinceMs else base
    }
}

@Serializable
data class ReviewResponseDto(
    val agent_id: String = "",
    val since_ms: Long? = null,
    val summary: ReviewSummaryDto = ReviewSummaryDto(),
    val changes: List<FileChangeDto> = emptyList(),
)

@Serializable
data class ReviewSummaryDto(
    val total_files: Int = 0,
    val total_added: Int = 0,
    val total_removed: Int = 0,
    val files_created: Int = 0,
    val files_deleted: Int = 0,
    val files_renamed: Int = 0,
    val largest_file: String? = null,
    val largest_file_lines: Int = 0,
    val truncated_any: Boolean = false,
    val is_empty: Boolean = false,
)

@Serializable
data class FileChangeDto(
    val path: String = "",
    val old_path: String? = null,
    val status: String = "modified",
    val added_lines: Int = 0,
    val removed_lines: Int = 0,
    val diff_text: String = "",
    val hunks: List<HunkDto> = emptyList(),
    val is_binary: Boolean = false,
    val truncated: Boolean = false,
    val total_diff_lines: Int = 0,
)

@Serializable
data class HunkDto(
    val old_start: Int = 0,
    val old_end: Int = 0,
    val new_start: Int = 0,
    val new_end: Int = 0,
    val range_label: String = "",
    val lines: List<DiffLineDto> = emptyList(),
)

@Serializable
data class DiffLineDto(
    val kind: String = "context",
    val text: String = "",
    val old_num: Int? = null,
    val new_num: Int? = null,
)

private val CanvasBlack = Color(0xFF000000)
private val CardFill = Color(0xFF1C1C1E)
private val AccentBlue = MaterialTheme.colorScheme.primary
private val AddedGreen = Color(0xFF30D158)
private val RemovedRed = Color(0xFFFF453A)
private val PrimaryText = Color(0xFFFFFFFF)
private val SecondaryText = Color(0xFF98989D)
private val Hairline = Color.White.copy(alpha = 0.08f)
private val PageMargin = 16.dp
private val GridUnit = 4.dp
private val CardShape = RoundedCornerShape(12.dp)

private val reviewJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

private val reviewHttp = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .build()

private suspend fun fetchReview(
    client: TalosClient,
    agentId: String,
    sinceMs: Long?,
): ReviewResponseDto = withContext(Dispatchers.IO) {
    val builder = Request.Builder().url(ReviewApi.reviewUrl(client.baseUrl, agentId, sinceMs)).get()
    if (client.apiToken.isNotBlank()) {
        builder.header("Authorization", "Bearer " + client.apiToken)
    }
    reviewHttp.newCall(builder.build()).execute().use { response ->
        if (response.code == 404) {
            throw IllegalStateException("Agent not found")
        }
        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP " + response.code)
        }
        val body = response.body?.string().orEmpty()
        if (body.isBlank()) throw IllegalStateException("Empty review response")
        reviewJson.decodeFromString(ReviewResponseDto.serializer(), body)
    }
}

/** System animator duration scale; 0 means the user disabled animation. */
private fun systemAnimatorDurationScale(context: Context): Float = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
}.getOrDefault(1f)

@Composable
fun ReviewWorkScreen(
    client: TalosClient,
    agentId: String,
    sinceMs: Long? = null,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    // Reduced motion: animator scale 0 expands cards instantly, no animation.
    val reducedMotion = remember { systemAnimatorDurationScale(context) <= 0f }
    var review by remember { mutableStateOf<ReviewResponseDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryTick by remember { mutableStateOf(0) }

    LaunchedEffect(client, agentId, sinceMs, retryTick) {
        try {
            review = fetchReview(client, agentId, sinceMs)
            error = null
        } catch (e: Exception) {
            if (review == null) error = e.message ?: "Host unreachable"
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBlack),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(PageMargin),
            verticalArrangement = Arrangement.spacedBy(PageMargin),
        ) {
            ReviewHeader(
                review = review,
                loading = review == null && error == null,
                onDone = onDone,
                onCopyAll = {
                    val all = review?.changes.orEmpty().joinToString("\n") { it.diff_text }
                    if (all.isNotBlank()) {
                        clipboardManager.setText(AnnotatedString(all))
                        Toast.makeText(context, "All diffs copied", Toast.LENGTH_SHORT).show()
                    }
                },
            )
            when {
                review == null && error != null -> ReviewErrorState(
                    message = error!!,
                    onRetry = { retryTick += 1 },
                )
                review == null -> ReviewLoadingState()
                review!!.changes.isEmpty() -> ReviewEmptyState(
                    agentId = agentId,
                    sinceMs = sinceMs,
                )
                else -> {
                    review!!.changes.forEach { change ->
                        FileChangeCard(
                            change = change,
                            reducedMotion = reducedMotion,
                            onCopyDiff = {
                                clipboardManager.setText(AnnotatedString(change.diff_text))
                                Toast.makeText(context, "Diff copied", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewHeader(
    review: ReviewResponseDto?,
    loading: Boolean,
    onDone: () -> Unit,
    onCopyAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(CardFill)
            .border(1.dp, Hairline, CardShape)
            .padding(PageMargin),
        verticalArrangement = Arrangement.spacedBy(GridUnit),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val summary = review?.summary
                Text(
                    text = if (summary != null) "${summary.total_files} files changed" else "Review its work",
                    color = PrimaryText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when {
                        loading -> "Loading review"
                        summary != null -> "+${summary.total_added} -${summary.total_removed}" +
                            if (summary.truncated_any) " · diff truncated" else ""
                        else -> ""
                    },
                    color = SecondaryText,
                    fontSize = 13.sp,
                )
            }
            TextButton(onClick = onCopyAll, enabled = review != null) {
                Text(text = "Copy all", color = AccentBlue, fontSize = 14.sp)
            }
            TextButton(onClick = onDone) {
                Text(text = "Done", color = AccentBlue, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ReviewLoadingState() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(PageMargin),
    ) {
        CircularProgressIndicator(color = AccentBlue)
    }
}

@Composable
private fun ReviewErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(CardFill)
            .border(1.dp, Hairline, CardShape)
            .padding(PageMargin),
        verticalArrangement = Arrangement.spacedBy(GridUnit),
    ) {
        Text(
            text = if (message == "Agent not found") "Agent not found" else "Review unavailable",
            color = PrimaryText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(text = message, color = SecondaryText, fontSize = 13.sp)
        TextButton(onClick = onRetry) {
            Text(text = "Retry", color = AccentBlue, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ReviewEmptyState(agentId: String, sinceMs: Long?) {
    val window = if (sinceMs != null) {
        "Since " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(sinceMs))
    } else {
        "All recorded time"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(CardFill)
            .border(1.dp, Hairline, CardShape)
            .padding(PageMargin),
        verticalArrangement = Arrangement.spacedBy(GridUnit),
    ) {
        Text(
            text = "No file changes recorded",
            color = PrimaryText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(text = "Run $agentId", color = SecondaryText, fontSize = 13.sp)
        Text(text = window, color = SecondaryText, fontSize = 13.sp)
    }
}

@Composable
private fun FileChangeCard(change: FileChangeDto, reducedMotion: Boolean, onCopyDiff: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(CardFill)
            .border(1.dp, Hairline, CardShape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (expanded) "Collapse ${change.path}" else "Expand ${change.path}",
                    onClick = { expanded = !expanded },
                )
                .padding(PageMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = SecondaryText,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(GridUnit))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (change.old_path != null && change.old_path != change.path) {
                        change.old_path + " → " + change.path
                    } else {
                        change.path
                    },
                    color = PrimaryText,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                )
                if (change.is_binary) {
                    Text(text = "Binary file, not diffed", color = SecondaryText, fontSize = 12.sp)
                }
            }
            Text(
                text = "+${change.added_lines}",
                color = AddedGreen,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(modifier = Modifier.width(GridUnit))
            Text(
                text = "-${change.removed_lines}",
                color = RemovedRed,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(modifier = Modifier.width(GridUnit))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onCopyDiff),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy diff",
                    tint = AccentBlue,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (reducedMotion) {
            // Animator scale 0: expand instantly, no animation at all.
            if (expanded) {
                DiffBody(change = change)
            }
        } else {
            AnimatedVisibility(visible = expanded) {
                DiffBody(change = change)
            }
        }
    }
}

@Composable
private fun DiffBody(change: FileChangeDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PageMargin, end = PageMargin, bottom = PageMargin),
        verticalArrangement = Arrangement.spacedBy(GridUnit),
    ) {
        if (change.is_binary) {
            Text(text = "Binary file, not diffed", color = SecondaryText, fontSize = 13.sp)
            return@Column
        }
        if (change.truncated) {
            Text(
                text = "Diff truncated at ${ReviewApi.MAX_DIFF_LINES} lines",
                color = SecondaryText,
                fontSize = 12.sp,
            )
        }
        // Horizontal scroll is confined to the diff block, never the page.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(CanvasBlack)
                .border(1.dp, Hairline, RoundedCornerShape(8.dp))
                .horizontalScroll(rememberScrollState())
                .padding(GridUnit),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                change.hunks.forEach { hunk ->
                    Text(
                        text = hunk.range_label.ifBlank { "Lines ${hunk.new_start}-${hunk.new_end}" },
                        color = AccentBlue,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = GridUnit),
                    )
                    hunk.lines.forEach { line ->
                        DiffLineRow(line = line)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffLineRow(line: DiffLineDto) {
    val (rowColor, textColor) = when (line.kind) {
        "added" -> AddedGreen.copy(alpha = 0.12f) to PrimaryText
        "removed" -> RemovedRed.copy(alpha = 0.12f) to PrimaryText
        else -> Color.Transparent to SecondaryText
    }
    val prefix = when (line.kind) {
        "added" -> "+"
        "removed" -> "-"
        else -> " "
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .widthIn(min = 320.dp)
            .background(rowColor)
            .padding(horizontal = GridUnit, vertical = 2.dp),
    ) {
        Text(
            text = line.old_num?.toString() ?: "",
            color = SecondaryText,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(28.dp),
        )
        Text(
            text = line.new_num?.toString() ?: "",
            color = SecondaryText,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(28.dp),
        )
        Text(
            text = prefix,
            color = textColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(16.dp),
        )
        Text(
            text = line.text,
            color = textColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            softWrap = false,
            maxLines = 1,
        )
    }
}
