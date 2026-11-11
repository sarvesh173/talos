package com.sarvesh173.talos.ui.processes

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Agent process supervisor — every round the orchestrator is running right now.
 *
 * The backend exposes `GET /api/processes` (ids, agent, room, elapsed seconds)
 * and `POST /api/processes/{id}/kill`. Before that existed the screen could
 * only show a count, which is not something a kill button can act on.
 *
 * Polls every two seconds. When the host is unreachable this shows the error
 * and a retry — never an empty "all quiet" list, which would claim the fleet is
 * idle when the truth is we cannot see it.
 */

private const val POLL_INTERVAL_MS = 2_000L

private val supervisorHttp = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .build()

private val supervisorJson = Json { ignoreUnknownKeys = true; isLenient = true }

@Serializable
private data class ProcessDto(
    val id: String = "",
    val kind: String = "round",
    val room_id: String = "",
    val prompt: String = "",
    val agent_id: String? = null,
    val agent_name: String? = null,
    val agent_color: String? = null,
    val turn: Int = 1,
    val total_turns: Int = 1,
    val completed_turns: Int = 0,
    @SerialName("started_at") val started_at: Double = 0.0,
    val elapsed_seconds: Double = 0.0,
    val cancellable: Boolean = false,
)

@Serializable
private data class ProcessListDto(
    val processes: List<ProcessDto> = emptyList(),
    val count: Int = 0,
)

@Serializable
private data class KillResultDto(
    val cancelled: Boolean = false,
    val reason: String? = null,
    val round_id: String = "",
)

data class ProcessUi(
    val id: String,
    val agentName: String,
    val roomId: String,
    val prompt: String,
    val turn: Int,
    val totalTurns: Int,
    val elapsedSeconds: Double,
    val cancellable: Boolean,
    val accent: Color,
)

private fun supervisorRequest(
    client: TalosClient,
    path: String,
    method: String = "GET",
): Request {
    val builder = Request.Builder().url(client.baseUrl.trimEnd('/') + path)
    if (client.apiToken.isNotBlank()) {
        builder.header("Authorization", "Bearer " + client.apiToken)
    }
    if (method == "POST") {
        builder.post("{}".toRequestBody("application/json".toMediaType()))
    } else {
        builder.get()
    }
    return builder.build()
}

private fun supervisorPayload(
    client: TalosClient,
    path: String,
    method: String = "GET",
): String = supervisorHttp.newCall(supervisorRequest(client, path, method)).execute().use { response ->
    if (!response.isSuccessful) throw IllegalStateException("HTTP " + response.code)
    response.body?.string().orEmpty()
}

private fun accentFor(raw: String?): Color {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return Color(0xFF8E8E93)
    val hex = text.removePrefix("#")
    return runCatching {
        when (hex.length) {
            6 -> Color(0xFF000000 or hex.toLong(16))
            8 -> Color(hex.toLong(16))
            else -> Color(0xFF8E8E93)
        }
    }.getOrDefault(Color(0xFF8E8E93))
}

private suspend fun fetchProcesses(client: TalosClient): List<ProcessUi> = withContext(Dispatchers.IO) {
    val body = supervisorPayload(client, "/api/processes")
    if (body.isBlank()) return@withContext emptyList()
    supervisorJson.decodeFromString<ProcessListDto>(body).processes.map { dto ->
        ProcessUi(
            id = dto.id,
            agentName = dto.agent_name?.takeIf { it.isNotBlank() } ?: "Agent",
            roomId = dto.room_id,
            prompt = dto.prompt,
            turn = dto.turn,
            totalTurns = dto.total_turns,
            elapsedSeconds = dto.elapsed_seconds,
            cancellable = dto.cancellable,
            accent = accentFor(dto.agent_color),
        )
    }
}

/** Returns the server's own answer; a refused kill is not reported as a success. */
private suspend fun killProcess(client: TalosClient, id: String): KillResultDto =
    withContext(Dispatchers.IO) {
        val body = supervisorPayload(client, "/api/processes/" + id + "/kill", method = "POST")
        supervisorJson.decodeFromString<KillResultDto>(body)
    }

private fun formatElapsed(seconds: Double): String {
    val total = seconds.coerceAtLeast(0.0).toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}

@Composable
fun ProcessSupervisorScreen(
    client: TalosClient,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var processes by remember { mutableStateOf<List<ProcessUi>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var killMessage by remember { mutableStateOf<String?>(null) }
    var killingId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // Drives the local timer between polls so elapsed time ticks every second
    // instead of jumping in two-second steps.
    var clockTick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(client, refreshTick) {
        loading = true
        try {
            processes = fetchProcesses(client)
            loadError = null
        } catch (e: Exception) {
            processes = emptyList()
            loadError = e.message ?: "Request failed"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(client, refreshTick) {
        while (true) {
            delay(POLL_INTERVAL_MS)
            clockTick++
            runCatching { processes = fetchProcesses(client); loadError = null }
                .onFailure { loadError = it.message ?: "Request failed" }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Back",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .clickable { onBack() },
            )
            Text(
                text = "Processes",
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = when {
                // 'loading' was assigned and never read, so the first paint
                // said "No agent rounds running." before anything had loaded.
                loading -> "Checking for running rounds…"
                loadError != null -> "Counts unknown — host unreachable"
                processes.isEmpty() -> "No agent rounds running."
                else -> processes.size.toString() + " running"
            },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (loading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Reading rounds from the host…",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (loadError != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Couldn't reach the host",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = loadError ?: "",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = "Retry",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable { refreshTick++ },
                )
            }
        }

        if (killMessage != null) {
            Text(
                text = killMessage ?: "",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        processes.forEach { proc ->
            ProcessRow(
                proc = proc,
                clockTick = clockTick,
                killing = proc.id == killingId,
                onKill = {
                    if (killingId != null) return@ProcessRow
                    killingId = proc.id
                    killMessage = null
                    scope.launch {
                        val result = runCatching { killProcess(client, proc.id) }
                        killMessage = result.fold(
                            onSuccess = { answer ->
                                if (answer.cancelled) {
                                    // Stop is real: drop the row now instead of
                                    // waiting for the next poll to agree.
                                    processes = processes.filterNot { it.id == proc.id }
                                    "Stopped " + proc.agentName
                                } else {
                                    answer.reason ?: "The server did not stop it"
                                }
                            },
                            onFailure = { "Could not reach the host: " + (it.message ?: "") },
                        )
                        killingId = null
                        refreshTick++
                    }
                },
            )
        }
    }
}

@Composable
private fun ProcessRow(
    proc: ProcessUi,
    clockTick: Long,
    killing: Boolean,
    onKill: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(proc.accent),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = proc.agentName,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = proc.roomId + " · turn " + proc.turn + "/" + proc.totalTurns,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (proc.prompt.isNotBlank()) {
                Text(
                    text = proc.prompt.take(70),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatElapsed(proc.elapsedSeconds + clockTick * 0.5),
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (killing) "Stopping…" else "Stop",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (proc.cancellable) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.clickable(
                    enabled = proc.cancellable && !killing
                ) { onKill() },
            )
        }
    }
}
