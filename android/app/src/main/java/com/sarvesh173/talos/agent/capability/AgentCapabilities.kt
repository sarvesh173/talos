package com.sarvesh173.talos.agent.capability

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * What the agent is allowed to touch, described as data.
 *
 * The permission list is not cosmetic. An agent that can read a calendar will
 * cheerfully conclude it may also write one unless the two are separate
 * capabilities with separate grants, so every privileged surface gets its own
 * [Capability] with its own Android permissions and its own rationale.
 */
private val HEALTH_READ = listOf(
    "android.permission.health.READ_STEPS",
    "android.permission.health.READ_HEART_RATE",
    "android.permission.health.READ_SLEEP",
    "android.permission.health.READ_EXERCISE",
    "android.permission.health.READ_DISTANCE",
    "android.permission.health.READ_TOTAL_CALORIES_BURNED",
    "android.permission.health.READ_ACTIVE_CALORIES_BURNED",
    "android.permission.health.READ_BLOOD_PRESSURE",
    "android.permission.health.READ_BLOOD_GLUCOSE",
    "android.permission.health.READ_BODY_TEMPERATURE",
    "android.permission.health.READ_OXYGEN_SATURATION",
    "android.permission.health.READ_WEIGHT",
    "android.permission.health.READ_HEIGHT",
    "android.permission.health.READ_RESTING_HEART_RATE",
    "android.permission.health.READ_RESPIRATORY_RATE"
)

private val HEALTH_WRITE = listOf(
    "android.permission.health.WRITE_EXERCISE",
    "android.permission.health.WRITE_SLEEP",
    "android.permission.health.WRITE_WEIGHT"
)

enum class Capability(
    val id: String,
    val title: String,
    val why: String,
    val permissions: List<String>
) {
    Calendar(
        id = "calendar",
        title = "Calendar",
        why = "Read the week to schedule around it, and add events you approve.",
        permissions = listOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
    ),
    Health(
        id = "health",
        title = "Health",
        why = "Read steps, sleep, heart rate and workouts so answers can use them.",
        permissions = HEALTH_READ
    ),
    HealthWrite(
        id = "health_write",
        title = "Health (write)",
        why = "Log a workout or sleep session the agent recorded.",
        permissions = HEALTH_WRITE
    ),
    Alarms(
        id = "alarms",
        title = "Alarms",
        why = "Set alarms and reminders, including repeating ones.",
        permissions = listOf(
            Manifest.permission.SET_ALARM,
            Manifest.permission.READ_MEDIA_AUDIO
        )
    ),
    UsageStats(
        id = "usage",
        title = "Usage access",
        why = "See which apps and agents you actually use, for the contribution graph.",
        // Usage access is not a runtime permission: the user grants it in
        // Settings, so it can only be checked, never requested.
        permissions = emptyList()
    );

    
}

/** Whether a capability is currently usable. */
data class CapabilityState(
    val capability: Capability,
    val granted: Boolean,
    /** True when the user must fix it in Settings rather than in a dialog. */
    val needsSettings: Boolean
)

/**
 * Reads the user's calendar.
 *
 * Deliberately returns plain data, not a Cursor: the agent should never hold a
 * cursor across a suspension point, and the caller should not need to know the
 * CalendarContract schema.
 */
class CalendarReader(private val context: Context) {

    data class Event(
        val id: Long,
        val title: String,
        val startMillis: Long,
        val endMillis: Long,
        val allDay: Boolean,
        val location: String?,
        val calendarName: String?
    )

    fun hasPermission(): Boolean = Capability.Calendar.permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Events overlapping [from, to].
     *
     * The selection is the standard overlap test rather than a simple
     * start-time range: an all-day event or a meeting that started yesterday
     * and runs into today still matters today.
     */
    fun eventsBetween(from: Long, to: Long, limit: Int = 250): List<Event> {
        if (!hasPermission()) return emptyList()
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.CALENDAR_DISPLAY_NAME
        )
        val selection = "${CalendarContract.Events.DTSTART} < ? AND " +
            "(${CalendarContract.Events.DTEND} > ? OR ${CalendarContract.Events.DTEND} IS NULL)"
        val out = mutableListOf<Event>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                arrayOf(to.toString(), from.toString()),
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val start = c.getLong(2)
                    val end = if (c.isNull(3)) start else c.getLong(3)
                    out += Event(
                        id = c.getLong(0),
                        title = c.getString(1).orEmpty(),
                        startMillis = start,
                        endMillis = end,
                        allDay = c.getInt(4) == 1,
                        location = c.getString(5),
                        calendarName = c.getString(6)
                    )
                }
            }
        }
        return out
    }

    /** Today's events in the device timezone. */
    fun today(zone: ZoneId = ZoneId.systemDefault()): List<Event> {
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = start + 86_400_000L
        return eventsBetween(start, end)
    }
}

/**
 * An alarm the agent can actually schedule.
 *
 * [daysOfWeek] is a Firebase-style mask rather than a list so a repeating alarm
 * survives the hand-off to the system clock app, which is the only component
 * allowed to fire while the app is dead.
 *
 * [daysOn]/[daysOff] model the alternating pattern the user asked for ("every
 * other day"): the alarm fires for [daysOn] days, then skips [daysOff]. That
 * cannot be expressed as a weekday mask, so it is expanded into concrete dates
 * by [occurrences] instead of being handed to AlarmClock.INTERVAL_DAY.
 */
data class AgentAlarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val daysOn: Int = 1,
    val daysOff: Int = 0,
    val startDate: LocalDate = LocalDate.now(),
    /** URI of the ringtone to play. Null uses the system default. */
    val soundUri: Uri? = null,
    val vibrate: Boolean = true,
    /** Minutes of early warning before the alarm. */
    val snoozeMinutes: Int = 10
) {
    /**
     * Concrete firing times for the first [count] alarms.
     *
     * Weekly alarms and on/off cycles are both expanded here so the caller has
     * one code path: it schedules what this returns and nothing has to
     * understand the pattern afterwards.
     */
    fun occurrences(count: Int, zone: ZoneId = ZoneId.systemDefault()): List<Long> {
        val out = mutableListOf<Long>()
        var date = startDate
        var cycleDay = 0
        val cycle = (daysOn.coerceAtLeast(1)) + daysOff.coerceAtLeast(0)
        var guard = 0
        while (out.size < count && guard < 4_000) {
            guard++
            val active = if (cycle > 0 && daysOff > 0) {
                cycleDay % cycle < daysOn.coerceAtLeast(1)
            } else {
                true
            }
            val weekdayOk = daysOfWeek.isEmpty() || date.dayOfWeek in daysOfWeek
            if (active && weekdayOk) {
                out += date.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
            }
            date = date.plusDays(1)
            cycleDay++
        }
        return out
    }
}

/**
 * Schedules alarms through the system clock app.
 *
 * Going through [AlarmClock] rather than a private AlarmManager alarm is a
 * choice, not a shortcut: it appears in the user's clock app where they can see
 * and cancel it, and it still fires when the process is dead.
 */
class AlarmScheduler(private val context: Context) {

    fun hasPermission(): Boolean =
        Capability.Alarms.permissions.all {
            it == Manifest.permission.SET_ALARM ||
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * Create the alarm. Returns the intent that was fired so the caller can
     * report what actually happened instead of assuming.
     */
    fun schedule(alarm: AgentAlarm): Result<Intent> = runCatching {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, alarm.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, alarm.minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, alarm.label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            putExtra(AlarmClock.EXTRA_VIBRATE, alarm.vibrate)
            if (alarm.daysOfWeek.isNotEmpty()) {
                // AlarmClock wants Calendar constants (1=Sunday), mapping from
                // java.time so callers never touch that numbering.
                putExtra(
                    AlarmClock.EXTRA_DAYS,
                    ArrayList(
                        alarm.daysOfWeek.map { day ->
                            when (day) {
                                DayOfWeek.SUNDAY -> 1
                                DayOfWeek.MONDAY -> 2
                                DayOfWeek.TUESDAY -> 3
                                DayOfWeek.WEDNESDAY -> 4
                                DayOfWeek.THURSDAY -> 5
                                DayOfWeek.FRIDAY -> 6
                                DayOfWeek.SATURDAY -> 7
                            }
                        }
                    )
                )
            }
            alarm.soundUri?.let { putExtra(AlarmClock.EXTRA_RINGTONE, it.toString()) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        intent
    }

    fun dismiss(label: String) {
        runCatching {
            context.startActivity(
                Intent(AlarmClock.ACTION_DISMISS_ALARM)
                    .putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_LABEL)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, label)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/**
 * Contribution data for the GitHub-style graph.
 *
 * Two dimensions, because they answer different questions: [byAgent] shows
 * where the work went, [byDay] shows when. A single number would answer
 * neither.
 */
class ContributionTracker(private val context: Context) {

    data class DayCell(val epochDay: Long, val count: Int)

    data class AgentSlice(
        val agentId: String,
        val label: String,
        val runs: Int,
        val minutes: Long
    )

    data class Snapshot(
        val byDay: List<DayCell>,
        val byAgent: List<AgentSlice>
    ) {
        /** Most-used agents first. */
        fun mostUsed(limit: Int = 5): List<AgentSlice> = byAgent.sortedByDescending { it.runs }.take(limit)

        /** Least-used agents first, excluding never-used ones. */
        fun leastUsed(limit: Int = 5): List<AgentSlice> =
            byAgent.filter { it.runs > 0 }.sortedBy { it.runs }.take(limit)

        fun totalRuns(): Int = byAgent.sumOf { it.runs }
    }

    private val prefs = context.getSharedPreferences("talos_contributions", Context.MODE_PRIVATE)

    /**
     * Record one finished run.
     *
     * Stored in SharedPreferences as flat counters rather than a database: the
     * graph reads a year of days and a handful of agents, which is a few
     * hundred keys — a schema would cost more than it saves.
     */
    fun record(agentId: String, minutes: Long, atMillis: Long = System.currentTimeMillis()) {
        val dayKey = "day:" + (atMillis / 86_400_000L)
        prefs.edit()
            .putInt(dayKey, prefs.getInt(dayKey, 0) + 1)
            .putInt("agent_runs:$agentId", prefs.getInt("agent_runs:$agentId", 0) + 1)
            .putLong("agent_minutes:$agentId", prefs.getLong("agent_minutes:$agentId", 0L) + minutes)
            .apply()
    }

    fun snapshot(agentLabels: Map<String, String> = emptyMap(), days: Int = 365): Snapshot {
        val today = System.currentTimeMillis() / 86_400_000L
        val byDay = (today - days + 1..today).map { day ->
            DayCell(day, prefs.getInt("day:$day", 0))
        }
        val byAgent = prefs.all.keys
            .filter { it.startsWith("agent_runs:") }
            .map { key ->
                val id = key.removePrefix("agent_runs:")
                AgentSlice(
                    agentId = id,
                    label = agentLabels[id] ?: id,
                    runs = prefs.getInt(key, 0),
                    minutes = prefs.getLong("agent_minutes:$id", 0L)
                )
            }
        return Snapshot(byDay, byAgent)
    }
}
