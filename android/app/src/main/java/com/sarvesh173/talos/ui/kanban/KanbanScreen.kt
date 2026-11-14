package com.sarvesh173.talos.ui.kanban

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Live operations board for the tool-call ledger.
 *
 * Wire-up (toolbar side — not edited here, by file-ownership rule):
 *   KanbanScreen(client = TalosClient.shared(context) ?: return@Composable,
 *                roomId = currentRoomId, onBack = { /* pop back */ })
 * Token and base URL are read from [TalosClient.baseUrl] / [TalosClient.apiToken]
 * on every poll, so re-pairing or a mesh URL change is picked up automatically.
 * The 2s poller lives in a LaunchedEffect keyed on the client/room, so it is
 * cancelled the moment this screen leaves composition.
 */
object KanbanApi {
    const val BOARD_PATH: String = "/api/kanban/board"
    const val STATS_PATH: String = "/api/kanban/stats"
    const val DEFAULT_ROOM_ID: String = "the-council"
    const val POLL_INTERVAL_MS: Long = 2_000L

    fun boardUrl(baseUrl: String, roomId: String): String =
        baseUrl.trimEnd('/') + BOARD_PATH + "?room_id=" + java.net.URLEncoder.encode(roomId, "UTF-8")
}

private val CardFill = Color(0xFF1C1C1E)
private val Accent = MaterialTheme.colorScheme.primary
private val OkGreen = Color(0xFF30D158)
private val ErrorRed = Color(0xFFFF453A)
private val RunningAmber = Color(0xFFFF9F0A)
private val PrimaryText = Color(0xFFFFFFFF)
private val SecondaryText = Color(0xFF98989D)
private val Hairline = Color.White.copy(alpha = 0.08f)

private val Tnum = TextStyle(fontFeatureSettings = "tnum")
private val CardShape = RoundedCornerShape(12.dp)
private val PageMargin = 16.dp
private val GridUnit = 4.dp

private val kanbanJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

private val kanbanHttp = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.SECONDS)
    .build()

private suspend fun fetchBoard(client: TalosClient, roomId: String): KanbanBoardDto =
    withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(KanbanApi.boardUrl(client.baseUrl, roomId)).get()
        if (client.apiToken.isNotBlank()) {
            builder.header("Authorization", "Bearer " + client.apiToken)
        }
        kanbanHttp.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP " + response.code)
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IllegalStateException("Empty board response")
            kanbanJson.decodeFromString(KanbanBoardDto.serializer(), body)
        }
    }

@Composable
fun KanbanScreen(
    client: TalosClient,
    roomId: String = KanbanApi.DEFAULT_ROOM_ID,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var board by remember { mutableStateOf<KanbanBoardDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(client, roomId, refreshTick) {
        while (true) {
            try {
                board = fetchBoard(client, roomId)
                error = null
            } catch (e: Exception) {
                // Keep the last good board on screen; only surface the error when
                // there is nothing real to show.
                if (board == null) error = e.message ?: "Host unreachable"
            }
            delay(KanbanApi.POLL_INTERVAL_MS)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PageMargin),
            verticalArrangement = Arrangement.spacedBy(PageMargin),
        ) {
            KanbanHeader(
                board = board,
                onBack = onBack,
            )
            when {
                board == null && error != null -> UnreachableState(
                    onRetry = { refreshTick += 1 },
                )
                board == null -> LoadingState()
                else -> {
                    val lanes = board!!.lanes
                    val allEmpty = lanes.running.isEmpty() &&
                        lanes.recent.isEmpty() &&
                        lanes.failures.isEmpty()
                    if (allEmpty) {
                        EmptyState(text = "No tool calls yet")
                    } else {
                        KanbanLanes(
                            board = board!!,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KanbanHeader(board: KanbanBoardDto?, onBack: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(CardFill)
            .border(1.dp, Hairline, CardShape)
            .padding(PageMargin),
        verticalArrangement = Arrangement.spacedBy(PageMargin),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tool calls",
                    color = PrimaryText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Live operations board",
                    color = SecondaryText,
                    fontSize = 13.sp,
                )
            }
            if (onBack != null) {
                TextButton(onClick = onBack) {
                    Text(text = "Back", color = Accent, fontSize = 14.sp)
                }
            }
        }

        val stats = board?.stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PageMargin),
        ) {
            CounterCell(label = "Total", value = stats?.total_calls ?: 0, modifier = Modifier.weight(1f))
            CounterCell(label = "Running", value = stats?.running ?: 0, modifier = Modifier.weight(1f))
            CounterCell(label = "Failures", value = stats?.failures ?: 0, modifier = Modifier.weight(1f))
            CounterCell(label = "Agents", value = stats?.agents?.size ?: 0, modifier = Modifier.weight(1f))
        }

        val topTools = stats?.tools
            ?.entries
            ?.sortedByDescending { it.value }
            ?.take(5)
            .orEmpty()
        if (topTools.isNotEmpty()) {
            val maxCount = topTools.first().value.coerceAtLeast(1)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(GridUnit),
            ) {
                Text(
                    text = "Tools by calls",
                    color = SecondaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                topTools.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = entry.key,
                            color = PrimaryText,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = entry.value.toString(),
                            color = SecondaryText,
                            fontSize = 12.sp,
                            style = Tnum,
                        )
                        Spacer(modifier = Modifier.width(GridUnit))
                        Box(
                            modifier = Modifier
                                .width(72.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.12f)),
                        ) {
                            val fraction = (entry.value.toFloat() / maxCount.toFloat())
                                .coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Accent),
                            )
                        }
                    }
                }
            }
        }

        val agents = stats?.agents.orEmpty()
        if (agents.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(GridUnit),
            ) {
                Text(
                    text = "Agents",
                    color = SecondaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                agents.sortedByDescending { it.calls }.take(6).forEach { agent ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (agent.is_delegate) "D" else "M",
                            color = if (agent.is_delegate) Accent else SecondaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = Tnum,
                        )
                        Spacer(modifier = Modifier.width(GridUnit))
                        Text(
                            text = agent.agent_name.ifBlank { agent.agent_id },
                            color = PrimaryText,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = agent.calls.toString() + " calls",
                            color = SecondaryText,
                            fontSize = 12.sp,
                            style = Tnum,
                        )
                        if (agent.errors > 0) {
                            Spacer(modifier = Modifier.width(GridUnit))
                            Text(
                                text = agent.errors.toString() + " err",
                                color = ErrorRed,
                                fontSize = 12.sp,
                                style = Tnum,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CounterCell(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = SecondaryText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value.toString(),
            color = PrimaryText,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            style = Tnum,
        )
    }
}

@Composable
private fun KanbanLanes(board: KanbanBoardDto, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val wide = maxWidth >= 720.dp
        val lanes = board.lanes
        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(PageMargin),
            ) {
                Lane(
                    title = "Running",
                    rows = lanes.running,
                    emptyText = "No running calls",
                    serverTime = board.server_time,
                    modifier = Modifier.weight(1f),
                )
                Lane(
                    title = "Recent",
                    rows = lanes.recent,
                    emptyText = "No recent calls",
                    serverTime = board.server_time,
                    modifier = Modifier.weight(1f),
                )
                Lane(
                    title = "Failures",
                    rows = lanes.failures,
                    emptyText = "No failures",
                    serverTime = board.server_time,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PageMargin),
            ) {
                LaneContent(
                    title = "Running",
                    rows = lanes.running,
                    emptyText = "No running calls",
                    serverTime = board.server_time,
                )
                LaneContent(
                    title = "Recent",
                    rows = lanes.recent,
                    emptyText = "No recent calls",
                    serverTime = board.server_time,
                )
                LaneContent(
                    title = "Failures",
                    rows = lanes.failures,
                    emptyText = "No failures",
                    serverTime = board.server_time,
                )
            }
        }
    }
}

@Composable
private fun Lane(
    title: String,
    rows: List<ToolCallDto>,
    emptyText: String,
    serverTime: Double,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(CardShape)
            .background(CardFill)
            .border(1.dp, Hairline, CardShape)
            .padding(PageMargin),
        verticalArrangement = Arrangement.spacedBy(GridUnit),
    ) {
        LaneTitle(title = title, count = rows.size)
        if (rows.isEmpty()) {
            Text(text = emptyText, color = SecondaryText, fontSize = 13.sp)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(GridUnit),
            ) {
                items(rows, key = { it.id.ifBlank { it.tool + it.started_at.toString() } }) { row ->
                    ToolCallCard(row = row, serverTime = serverTime)
                }
            }
        }
    }
}

@Composable
private fun LaneContent(
    title: String,
    rows: List<ToolCallDto>,
    emptyText: String,
    serverTime: Double,
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
        LaneTitle(title = title, count = rows.size)
        if (rows.isEmpty()) {
            Text(text = emptyText, color = SecondaryText, fontSize = 13.sp)
        } else {
            rows.forEach { row -> ToolCallCard(row = row, serverTime = serverTime) }
        }
    }
}

@Composable
private fun LaneTitle(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = PrimaryText,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            color = SecondaryText,
            fontSize = 13.sp,
            style = Tnum,
        )
    }
}

@Composable
private fun ToolCallCard(row: ToolCallDto, serverTime: Double) {
    val statusColor = when {
        row.isFailure -> ErrorRed
        row.isRunning -> RunningAmber
        else -> OkGreen
    }
    val borderColor = if (row.is_delegate) Accent else Hairline
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, borderColor, CardShape)
            .padding(PageMargin),
        verticalArrangement = Arrangement.spacedBy(GridUnit),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(modifier = Modifier.size(8.dp)) {
                drawCircle(color = statusColor)
            }
            Spacer(modifier = Modifier.width(GridUnit))
            Text(
                text = row.tool.ifBlank { "(unknown tool)" },
                color = PrimaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = relativeDuration(row, serverTime),
                color = SecondaryText,
                fontSize = 12.sp,
                style = Tnum,
            )
        }

        val attribution = when {
            row.is_delegate && row.parent_agent_id.isNotBlank() ->
                "Delegate " + displayName(row) + " via " + row.parent_agent_id
            row.is_delegate -> "Delegate " + displayName(row)
            else -> "Main agent " + displayName(row)
        }
        Text(
            text = attribution,
            color = if (row.is_delegate) Accent else SecondaryText,
            fontSize = 12.sp,
            fontWeight = if (row.is_delegate) FontWeight.SemiBold else FontWeight.Normal,
        )

        val preview = row.previewText()
        if (preview.isNotBlank()) {
            Text(
                text = preview,
                color = SecondaryText,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        if (row.isFailure && row.error.isNotBlank()) {
            Text(
                text = row.error,
                color = ErrorRed,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        } else if (row.result_summary.isNotBlank() && !row.isRunning) {
            Text(
                text = row.result_summary,
                color = SecondaryText,
                fontSize = 12.sp,
            )
        }
        Text(
            text = row.status.ifBlank { "unknown" },
            color = statusColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun displayName(row: ToolCallDto): String =
    row.agent_name.ifBlank { row.agent_id.ifBlank { "(unknown agent)" } }

private fun relativeDuration(row: ToolCallDto, serverTime: Double): String {
    val elapsedMs: Long? = when {
        row.duration_ms != null -> row.duration_ms
        row.isRunning && row.started_at > 0.0 -> {
            val now = if (serverTime > 0.0) serverTime else System.currentTimeMillis() / 1000.0
            ((now - row.started_at) * 1000.0).toLong().coerceAtLeast(0L)
        }
        row.finished_at != null && row.started_at > 0.0 ->
            ((row.finished_at - row.started_at) * 1000.0).toLong().coerceAtLeast(0L)
        else -> null
    }
    if (elapsedMs == null) return "—"
    return when {
        elapsedMs < 1_000L -> "<1s"
        elapsedMs < 60_000L -> (elapsedMs / 1_000L).toString() + "s"
        else -> {
            val minutes = elapsedMs / 60_000L
            val seconds = (elapsedMs % 60_000L) / 1_000L
            minutes.toString() + "m " + seconds.toString() + "s"
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = Accent)
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.EmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .clip(CardShape)
            .background(CardFill)
            .border(1.dp, Hairline, CardShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = SecondaryText, fontSize = 14.sp)
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.UnreachableState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .clip(CardShape)
            .background(CardFill)
            .border(1.dp, Hairline, CardShape)
            .padding(PageMargin),
        verticalArrangement = Arrangement.spacedBy(PageMargin, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Host unreachable",
            color = PrimaryText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "The Kanban board could not be loaded from " + KanbanApi.BOARD_PATH,
            color = SecondaryText,
            fontSize = 13.sp,
        )
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                contentColor = PrimaryText,
            ),
        ) {
            Text(text = "Retry", fontSize = 14.sp)
        }
    }
}
