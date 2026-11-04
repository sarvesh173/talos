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
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

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
    val selected = jobs.firstOrNull { it.id == selectedId }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            JobTopBar(title = if (selected == null) "Scheduled jobs" else "Work while you sleep", onBack = {
                if (selected == null) onBack() else selectedId = null
            })
            if (selected == null) {
                JobListColumn(
                    jobs = jobs,
                    onOpen = { selectedId = it.id },
                    modifier = Modifier.weight(1f)
                )
            } else {
                JobDetailColumn(
                    job = selected,
                    runs = runs,
                    onRunNow = { onRunNow(selected) },
                    onToggle = { onToggle(selected) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
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
    Row(modifier = Modifier.fillMaxWidth().applePressable(0.97f) { }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
