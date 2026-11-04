package com.sarvesh173.talos.ui.agent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Timers, at two deliberately different cadences.
 *
 * The rule the user asked for, stated precisely:
 *
 *  - **A running agent's own clock ticks every second.** This is the number a
 *    person watching the screen is counting in their head; if it lags they
 *    think the app froze.
 *  - **Accumulated totals refresh once a minute.** A total that changes every
 *    second is unreadable — nobody can read a number that is always mid-change,
 *    and it re-lays-out the row 60× more often than it needs to.
 *  - **The session clock is live**, because it is a single number that never
 *    reflows and its whole purpose is to say "we have been at this for a while".
 *
 * Implementing this as one 1s tick with a second, coarser derived value keeps
 * the CPU cost identical to a single timer while giving both behaviours.
 */
class AgentTimers(
    sessionStartedAtMillis: Long = System.currentTimeMillis()
) {
    /** When this session began. Immutable. */
    val sessionStart: Long = sessionStartedAtMillis

    private val startedAt = System.currentTimeMillis()

    /** Advanced every second. Drives the live readouts. */
    var now by mutableLongStateOf(startedAt)
        private set

    /** Advanced every minute. Drives every aggregate that must not flicker. */
    var coarseNow by mutableLongStateOf(startedAt)
        private set

    private var nextCoarse = startedAt + COARSE_PERIOD_MS

    /**
     * One second of forward time. Call once per tick.
     *
     * The coarse value is recomputed from the tick rather than from its own
     * timer so the two can never drift into disagreeing about "now" — a
     * summary that says 4m while the row says 5m looks like a bug even when
     * both are right.
     */
    fun tick(atMillis: Long = System.currentTimeMillis()) {
        now = atMillis
        if (atMillis >= nextCoarse) {
            coarseNow = atMillis
            // Advance by whole periods so a long sleep (backgrounded app) does
            // not cause a burst of coarse updates the moment it resumes.
            while (nextCoarse <= atMillis) nextCoarse += COARSE_PERIOD_MS
        }
    }

    fun sessionElapsedSeconds(useCoarse: Boolean = false): Long {
        val basis = if (useCoarse) coarseNow else now
        return ((basis - sessionStart) / 1000L).coerceAtLeast(0L)
    }

    /**
     * Working time for an agent: live seconds while it runs, and the value
     * frozen at [pausedAt] once it stops.
     *
     * Freezing matters. A delegate that finished twenty minutes ago did not
     * work for those twenty minutes, and a timer that keeps climbing after the
     * agent stops is a lie that compounds — an hour later it claims an
     * hour-long job.
     */
    fun workingSeconds(agent: AgentRuntime, useCoarse: Boolean = false): Long {
        val runningFor = if (agent.isExpired(now) || agent.endedAtMillis != null) {
            val end = agent.endedAtMillis ?: agent.startedAtMillis
            ((end - agent.startedAtMillis) / 1000L)
        } else {
            val basis = if (useCoarse) coarseNow else now
            ((basis - agent.startedAtMillis) / 1000L)
        }
        return runningFor.coerceAtLeast(0L)
    }

    /** Total working time across a fleet, in *live* seconds. */
    fun totalWorkingSeconds(agents: List<AgentRuntime>, useCoarse: Boolean = true): Long =
        agents.sumOf { workingSeconds(it, useCoarse) }

    /** Wall-clock length of the session, live. */
    fun sessionSeconds(): Long = ((now - sessionStart) / 1000L).coerceAtLeast(0L)

    companion object {
        /** Aggregates refresh at this cadence. One minute, as specified. */
        const val COARSE_PERIOD_MS = 60_000L
    }
}

/**
 * Compose binding for [AgentTimers].
 *
 * Returns the timers plus a [live] State that flips on every tick, so any
 * composable that reads it recomposes once a second — and any composable that
 * reads [AgentTimers.coarseNow] instead recomposes once a minute. Reading the
 * wrong one is the only way to get the cadence wrong, so the two are separate
 * properties rather than a parameter.
 */
@Composable
fun rememberAgentTimers(
    sessionStartedAtMillis: Long = remember { System.currentTimeMillis() }
): AgentTimers {
    val timers = remember { AgentTimers(sessionStartedAtMillis) }
    LaunchedEffect(timers) {
        while (true) {
            delay(1_000)
            timers.tick()
        }
    }
    return timers
}

/**
 * A delegate that is no longer running, with the wall-clock it accumulated.
 *
 * Kept as a separate record from [AgentRuntime] because a finished delegate is
 * not a running one with a flag — it has an end time, a result, and a reason,
 * and treating them as the same type is how "still working" leaks into the UI.
 */
data class FinishedAgent(
    val runtime: AgentRuntime,
    val endedAtMillis: Long,
    val outcome: Outcome,
    val summary: String = ""
) {
    val workingSeconds: Long
        get() = ((endedAtMillis - runtime.startedAtMillis) / 1000L).coerceAtLeast(0L)

    enum class Outcome { Completed, Failed, Expired, Stopped }
}

/**
 * Session totals, computed at the coarse cadence.
 *
 * Standalone because it is the one thing on screen that must never tick: it is
 * a *report*, and a report that moves is not readable.
 */
data class SessionSummary(
    val workingSeconds: Long,
    val sessionSeconds: Long,
    val agentsRun: Int,
    val delegatesRun: Int,
    val toolCalls: Int
) {
    /** Share of the session the agents were actually doing something. */
    fun utilisation(): Float =
        if (sessionSeconds <= 0) 0f else (workingSeconds.toFloat() / sessionSeconds).coerceIn(0f, 1f)
}
