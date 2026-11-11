package com.sarvesh173.talos.ui.cron

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient
import com.sarvesh173.talos.ui.AppleToggleSwitch
import com.sarvesh173.talos.ui.LocalIsDark
import com.sarvesh173.talos.ui.applePressable
import com.sarvesh173.talos.ui.talosTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Scheduled jobs — "Work while you sleep".
 *
 * Colours: strictly monochrome surfaces/text plus exactly two accents:
 * green for success, red for failed. 4dp grid, sentence case, no emoji,
 * no shadows (bordered flat surfaces only).
 *
 * This screen is self-contained: the job API client surface is accessed
 * through plain fetch calls made by the caller wiring (see TALOS jobs
 * endpoints). The screen takes injected state so it stays previewable
 * without a server.
 */
data class JobUi(
    val id: String = "",
    val name: String = "",
    val prompt: String = "",
    val cronExpr: String = "0 6 * * *",
    val deliver: String = "local",
    val profile: String = "maintainer",
    val notifyOnRun: Boolean = true,
    val enabled: Boolean = true,
    val running: Boolean = false,
    val runningSinceEpochMs: Long = 0L,
    val nextRunLabel: String = ""
)

data class RunUi(
    val startedAtEpochMs: Long = 0L,
    val durationSeconds: Long = 0L,
    val outputBytes: Long = 0L,
    val status: String = "success"
)

private val successGreen = Color(0xFF30D158)
private val failedRed = Color(0xFFFF453A)
private val mono = FontFamily.Monospace
private val stampFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")

// -- Live job API -------------------------------------------------------------
// The screen previously took `jobs` as a parameter that every caller left empty,
// so a working scheduler rendered as "No scheduled jobs yet" forever. These
// mirror the server contract in server/job_scheduler.py; unknown fields are
// ignored so a backend addition can never crash the screen.

private val jobJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

private val jobHttp = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .build()

@Serializable
private data class JobDto(
    val id: String = "",
    val name: String = "",
    val prompt: String = "",
    val cron_expr: String = "",
    val deliver: String = "local",
    val profile: String = "maintainer",
    val notify_on_run: Boolean = true,
    val enabled: Boolean = true,
    val running: Boolean = false,
    val running_since: Double? = null,
    val next_run_at: String? = null,
)

@Serializable
private data class RunDto(
    val started_at: String? = null,
    val duration_seconds: Double = 0.0,
    val output_bytes: Long = 0L,
    val status: String = "success",
)

private fun jobRequest(client: TalosClient, path: String, method: String = "GET", body: String? = null): Request {
    val builder = Request.Builder().url(client.baseUrl.trimEnd('/') + path)
    if (client.apiToken.isNotBlank()) {
        builder.header("Authorization", "Bearer " + client.apiToken)
    }
    val jsonBody = (body ?: "{}").toRequestBody("application/json".toMediaType())
    when (method) {
        "POST" -> builder.post(jsonBody)
        "PATCH" -> builder.patch(jsonBody)
        else -> builder.get()
    }
    return builder.build()
}

private fun jobPayload(client: TalosClient, path: String, method: String = "GET", body: String? = null): String =
    jobHttp.newCall(jobRequest(client, path, method, body)).execute().use { response ->
        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP " + response.code)
        }
        response.body?.string().orEmpty()
    }

private suspend fun fetchJobs(client: TalosClient): List<JobUi> = withContext(Dispatchers.IO) {
    val body = jobPayload(client, "/api/jobs")
    if (body.isBlank()) return@withContext emptyList()
    jobJson.decodeFromString<List<JobDto>>(body).map { dto ->
        JobUi(
            id = dto.id,
            name = dto.name.ifBlank { dto.prompt.take(40) },
            prompt = dto.prompt,
            cronExpr = dto.cron_expr,
            deliver = dto.deliver,
            profile = dto.profile,
            notifyOnRun = dto.notify_on_run,
            enabled = dto.enabled,
            running = dto.running,
            runningSinceEpochMs = dto.running_since?.let { (it * 1000).toLong() }
                ?: dto.next_run_at?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: 0L,
            nextRunLabel = nextRunLabel(dto.cron_expr, LocalDateTime.now()),
        )
    }
}

private suspend fun fetchJobRuns(client: TalosClient, jobId: String): List<RunUi> = withContext(Dispatchers.IO) {
    val body = jobPayload(client, "/api/jobs/" + jobId + "/runs")
    if (body.isBlank()) return@withContext emptyList()
    jobJson.decodeFromString<List<RunDto>>(body).map { dto ->
        RunUi(
            startedAtEpochMs = dto.started_at?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L,
            durationSeconds = dto.duration_seconds.toLong(),
            outputBytes = dto.output_bytes,
            status = dto.status,
        )
    }
}

private fun runJobNow(client: TalosClient, jobId: String) {
    jobPayload(client, "/api/jobs/" + jobId + "/run", method = "POST")
}

/** Server-side `/pause` flips `enabled`; no body, no client-side toggle state. */
private fun setJobPaused(client: TalosClient, jobId: String) {
    jobPayload(client, "/api/jobs/" + jobId + "/pause", method = "POST")
}

/** Small cron next-run calculator (5 fields: minute hour dom month dow). */
fun nextRunLabel(cronExpr: String, from: LocalDateTime): String {
    val fields = cronExpr.trim().split(Regex("\\s+"))
    if (fields.size != 5) return "Invalid schedule"
    val sets = try {
        fields.mapIndexed { index, raw ->
            val (lo, hi) = listOf(0 to 59, 0 to 23, 1 to 31, 1 to 12, 0 to 7)[index]
            parseCronField(raw, lo, hi)
        }
    } catch (e: IllegalArgumentException) {
        return "Invalid schedule: ${e.message}"
    }
    var cursor = from.withSecond(0).withNano(0).plusMinutes(1)
    val deadline = cursor.plusYears(5)
    while (!cursor.isAfter(deadline)) {
        val cronDow = cursor.dayOfWeek.value % 7
        if (cursor.minute in sets[0] && cursor.hour in sets[1] &&
            cursor.dayOfMonth in sets[2] && cursor.monthValue in sets[3] &&
            cronDow in sets[4]
        ) {
            return cursor.format(stampFormat)
        }
        cursor = cursor.plusMinutes(1)
    }
    return "No run within 5 years"
}

private fun parseCronField(raw: String, lo: Int, hi: Int): Set<Int> {
    require(raw.isNotEmpty()) { "empty field" }
    val out = mutableSetOf<Int>()
    for (part in raw.split(",")) {
        val slash = part.split("/")
        require(slash.size <= 2) { "bad step in $part" }
        val step = if (slash.size == 2) slash[1].toInt() else 1
        require(step > 0) { "bad step in $part" }
        val base = slash[0]
        val (start, end) = when {
            base == "*" -> lo to hi
            "-" in base -> {
                val p = base.split("-")
                require(p.size == 2) { "bad range in $part" }
                p[0].toInt() to p[1].toInt()
            }
            else -> base.toInt() to base.toInt()
        }
        require(start in lo..hi && end in lo..hi && start <= end) { "out of range in $part" }
        var v = start
        while (v <= end) {
            out.add(if (hi == 7 && v == 7) 0 else v)
            v += step
        }
    }
    return out
}

@Composable
fun JobListScreen(
    client: TalosClient,
    jobs: List<JobUi> = emptyList(),
    runs: List<RunUi> = emptyList(),
    onBack: () -> Unit = {},
    onRunNow: (JobUi) -> Unit = {},
    onToggle: (JobUi) -> Unit = {}
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var liveJobs by remember { mutableStateOf<List<JobUi>>(jobs) }
    var liveRuns by remember { mutableStateOf<List<RunUi>>(runs) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(client, refreshTick) {
        loading = true
        try {
            liveJobs = fetchJobs(client)
            loadError = null
        } catch (e: Exception) {
            // Never fall back to fabricated rows: an unreachable host is an
            // error state, not an empty schedule.
            loadError = e.message ?: "Couldn't reach the host"
            liveJobs = emptyList()
        } finally {
            loading = false
        }
    }

    val selected = liveJobs.firstOrNull { it.id == selectedId }

    LaunchedEffect(selectedId, client) {
        val id = selectedId ?: return@LaunchedEffect
        liveRuns = runCatching { fetchJobRuns(client, id) }.getOrDefault(emptyList())
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            JobTopBar(title = if (selected == null) "Scheduled jobs" else "Work while you sleep", onBack = {
                if (selected == null) onBack() else selectedId = null
            })
            when {
                loading && liveJobs.isEmpty() -> JobLoading()
                loadError != null && liveJobs.isEmpty() -> JobUnreachable(
                    message = loadError ?: "Couldn't reach the host",
                    onRetry = { refreshTick++ }
                )
                selected == null -> JobListColumn(
                    jobs = liveJobs,
                    onOpen = { selectedId = it.id },
                    modifier = Modifier.weight(1f)
                )
                else -> JobDetailColumn(
                    job = selected,
                    runs = liveRuns,
                    onRunNow = {
                        onRunNow(selected)
                        runCatching { runJobNow(client, selected.id) }
                        refreshTick++
                    },
                    onToggle = {
                        onToggle(selected)
                        runCatching { setJobPaused(client, selected.id) }
                        refreshTick++
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun JobLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun JobUnreachable(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Couldn't reach the host",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = message, fontSize = 13.sp, color = talosTextMuted)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun JobTopBar(title: String, onBack: () -> Unit) {
    val isDark = LocalIsDark.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(0.6.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "‹ Back",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.applePressable(0.94f) { onBack() }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun JobListColumn(jobs: List<JobUi>, onOpen: (JobUi) -> Unit, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (jobs.isEmpty()) {
            Text(text = "No scheduled jobs yet", fontSize = 15.sp, color = talosTextMuted)
            Text(
                text = "Create a job to run while you sleep.",
                fontSize = 13.sp, color = talosTextMuted
            )
        }
        jobs.forEach { job ->
            SectionCard {
                Column(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(job) }.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusDot(status = if (job.running) "running" else if (job.enabled) "success" else "failed", small = true)
                        Text(text = job.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(text = job.cronExpr, fontSize = 13.sp, fontFamily = mono, color = talosTextMuted)
                    if (job.nextRunLabel.isNotEmpty()) {
                        Text(text = "Next run ${job.nextRunLabel}", fontSize = 13.sp, color = talosTextMuted)
                    }
                }
            }
        }
        CronFormCard()
    }
}

@Composable
private fun JobDetailColumn(
    job: JobUi,
    runs: List<RunUi>,
    onRunNow: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(job.running) {
        while (job.running) {
            delay(1000)
            nowMs = System.currentTimeMillis()
        }
    }
    val elapsedSec = if (job.running && job.runningSinceEpochMs > 0L) {
        maxOf(0L, (nowMs - job.runningSinceEpochMs) / 1000L)
    } else 0L

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionCard {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = job.name, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    StateBadge(enabled = job.enabled, running = job.running)
                }
                if (job.running) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface)
                        Text(text = formatElapsed(elapsedSec), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                } else if (job.nextRunLabel.isNotEmpty()) {
                    Text(text = "Next run ${job.nextRunLabel}", fontSize = 14.sp, color = talosTextMuted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onRunNow, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface)) {
                        Text(text = "Run now")
                    }
                    OutlinedButton(onClick = onToggle) {
                        Text(text = if (job.enabled) "Pause" else "Resume")
                    }
                }
            }
        }

        SectionLabel(text = "Prompt")
        SectionCard {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val context = LocalContext.current
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Instruction", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = talosTextMuted)
                    Text(
                        text = "Copy",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            val board = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            board.setPrimaryClip(ClipData.newPlainText("prompt", job.prompt))
                        }
                    )
                }
                Text(text = job.prompt, fontSize = 14.sp, fontFamily = mono, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        SectionLabel(text = "Configuration")
        SectionCard {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ConfigRow(label = "Schedule", value = humanSchedule(job.cronExpr))
                ConfigRow(label = "Cron", value = job.cronExpr)
                ConfigRow(label = "Deliver", value = job.deliver)
                ConfigRow(label = "Profile", value = job.profile)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Notifications", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    AppleToggleSwitch(checked = job.notifyOnRun, onCheckedChange = {})
                }
            }
        }

        SectionLabel(text = "Run history")
        SectionCard {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (runs.isEmpty()) {
                    Text(text = "No runs yet", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    if (job.nextRunLabel.isNotEmpty()) {
                        Text(text = "Next scheduled run ${job.nextRunLabel}", fontSize = 13.sp, color = talosTextMuted)
                    }
                }
                runs.forEach { run -> RunRow(run = run) }
            }
        }
    }
}

@Composable
private fun CronFormCard() {
    var name by remember { mutableStateOf("Nightly checks") }
    var prompt by remember { mutableStateOf("pytest -q") }
    var cron by remember { mutableStateOf("0 6 * * *") }
    val nextLabel = remember(cron) { nextRunLabel(cron, LocalDateTime.now()) }
    val valid = !nextLabel.startsWith("Invalid") && !nextLabel.startsWith("No run")

    SectionLabel(text = "New job")
    SectionCard {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("Prompt") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None))
            OutlinedTextField(value = cron, onValueChange = { cron = it }, label = { Text("Cron (minute hour day-of-month month day-of-week)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusDot(status = if (valid) "success" else "failed", small = true)
                Text(text = if (valid) "Next run $nextLabel" else nextLabel, fontSize = 13.sp, color = talosTextMuted)
            }
        }
    }
}

@Composable
private fun StateBadge(enabled: Boolean, running: Boolean) {
    val (label, bg) = when {
        running -> "Running" to MaterialTheme.colorScheme.onSurface
        enabled -> "Scheduled" to successGreen
        else -> "Paused" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val fg = if (running || !enabled) MaterialTheme.colorScheme.onSurfaceVariant else Color.Black
    val bgColor = if (running || !enabled) MaterialTheme.colorScheme.surfaceContainer else bg
    Box(
        modifier = Modifier.clip(CircleShape).background(bgColor).padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

@Composable
private fun StatusDot(status: String, small: Boolean = false) {
    val color = when (status) {
        "success" -> successGreen
        "failed" -> failedRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier.size(if (small) 8.dp else 10.dp).clip(CircleShape).background(color)
    )
}

@Composable
private fun RunRow(run: RunUi) {
    val stamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(run.startedAtEpochMs), ZoneId.systemDefault()).format(stampFormat)
    Row(modifier = Modifier.fillMaxWidth().applePressable(0.97f) { }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusDot(status = run.status)
        Column {
            Text(text = stamp, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = "${formatElapsed(run.durationSeconds)} · ${formatBytes(run.outputBytes)}",
                fontSize = 12.sp, color = talosTextMuted
            )
        }
    }
}

@Composable
private fun ConfigRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, fontSize = 14.sp, color = talosTextMuted)
        Text(text = value, fontSize = 14.sp, fontFamily = mono, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.15).sp,
        color = talosTextMuted,
        modifier = Modifier.padding(start = 16.dp)
    )
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    val isDark = LocalIsDark.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(0.6.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}

private fun formatElapsed(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "${m}m ${s.toString().padStart(2, '0')}s"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun humanSchedule(cronExpr: String): String {
    val parts = cronExpr.trim().split(Regex("\\s+"))
    if (parts.size != 5) return cronExpr
    val (minute, hour) = parts[0] to parts[1]
    return if (minute == "0" && hour.all { it.isDigit() }) {
        val h = hour.toInt()
        val suffix = if (h < 12) "AM" else "PM"
        val h12 = when (h % 12) { 0 -> 12; else -> h % 12 }
        "Daily at $h12:00 $suffix"
    } else {
        "Custom schedule"
    }
}
