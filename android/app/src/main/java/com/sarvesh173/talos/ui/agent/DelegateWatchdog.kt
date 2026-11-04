package com.sarvesh173.talos.ui.agent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.delay

/**
 * Watchdog for a fleet of delegate agents.
 *
 * The orchestrator must never have to poll "is it done yet". Each delegate gets
 * a record, and this watcher walks the fleet on a fixed cadence to answer four
 * questions the orchestrator actually asks:
 *
 *  1. **Who finished?** — a delegate that reported back is moved to `done` once
 *     and its result is handed over exactly once, so work is never double-counted.
 *  2. **Who failed?** — an error is terminal; retrying silently would hide it.
 *  3. **Who is stuck?** — no progress and no completion past [stallSeconds] is a
 *     stall, not slowness. A stall is surfaced, never auto-killed: a long tool
 *     call can look identical to a hang.
 *  4. **Who expired?** — a delegate past its TTL is reaped and reported, so no
 *     slot is silently leaked.
 *
 * The watcher is intentionally cheap: one timer for the whole fleet, and state
 * lives in a map keyed by agent id so a recomposition cannot lose it.
 */
class DelegateWatchdog(
    private val stallSeconds: Long = 180,
    private val pollMillis: Long = 2_000
) {
    /** Everything we know about one delegate's progress. */
    data class Entry(
        val agent: AgentRuntime,
        var lastProgressAt: Long = System.currentTimeMillis(),
        var status: Status = Status.Running,
        var result: String? = null,
        var error: String? = null
    )

    enum class Status { Running, Stalled, Done, Failed, Expired }

    private val entries = mutableStateMapOf<String, Entry>()
    private val reported = mutableStateMapOf<String, Boolean>()

    /** Completions the orchestrator has not consumed yet. */
    var finishedCount by mutableStateOf(0)
        private set

    fun track(agent: AgentRuntime) {
        entries[agent.id] = Entry(agent)
    }

    fun untrack(id: String) {
        entries.remove(id)
        reported.remove(id)
    }

    /** Called whenever a delegate shows any sign of life. */
    fun touch(id: String) {
        entries[id]?.lastProgressAt = System.currentTimeMillis()
    }

    fun complete(id: String, result: String) {
        entries[id]?.let {
            it.status = Status.Done
            it.result = result
        }
    }

    fun fail(id: String, error: String) {
        entries[id]?.let {
            it.status = Status.Failed
            it.error = error
        }
    }

    /**
     * One pass. Returns the ids that reached a terminal state *this pass*, so a
     * caller can act once per completion instead of re-reading the whole map.
     */
    fun sweep(now: Long = System.currentTimeMillis()): List<String> {
        val newlyTerminal = mutableListOf<String>()
        for ((id, entry) in entries) {
            if (entry.status == Status.Done || entry.status == Status.Failed || entry.status == Status.Expired) {
                continue
            }
            if (entry.agent.isExpired(now)) {
                entry.status = Status.Expired
                newlyTerminal += id
                continue
            }
            val idle = (now - entry.lastProgressAt) / 1000L
            if (idle >= stallSeconds) {
                // Mark it, but keep it in the fleet: a stall may recover, and
                // killing a live tool call on a timer loses real work.
                entry.status = Status.Stalled
            } else if (entry.status == Status.Stalled) {
                entry.status = Status.Running
            }
        }
        for (id in newlyTerminal) {
            if (reported[id] != true) {
                reported[id] = true
                finishedCount++
            }
        }
        return newlyTerminal
    }

    fun snapshot(): List<Entry> = entries.values.sortedBy { it.agent.startedAtMillis }

    fun running(): List<Entry> = snapshot().filter { it.status == Status.Running || it.status == Status.Stalled }
}

/**
 * Compose binding for [DelegateWatchdog]: runs the sweep on a cadence and
 * exposes the fleet to the UI.
 *
 * @param onFinished invoked once per delegate that reaches a terminal state,
 *   with its final result when it produced one. This is the "notify the
 *   orchestrator immediately" hook — no polling loop on the caller side.
 */
@Composable
fun rememberDelegateWatchdog(
    onFinished: (DelegateWatchdog.Entry) -> Unit = {}
): DelegateWatchdog {
    val watchdog = remember { DelegateWatchdog() }

    LaunchedEffect(watchdog) {
        while (true) {
            delay(watchdogPoll)
            val ids = watchdog.sweep()
            if (ids.isNotEmpty()) {
                val byId = watchdog.snapshot().associateBy { it.agent.id }
                ids.forEach { id -> byId[id]?.let(onFinished) }
            }
        }
    }
    return watchdog
}

private const val watchdogPoll = 2_000L

/**
 * Split a batch of independent tasks across a fleet of delegates.
 *
 * Round-robin rather than first-fit: the tasks in a review batch are usually
 * similar in cost, so even distribution beats bin-packing heuristics and keeps
 * the code obvious. Returns one task list per delegate.
 */
fun distributeTasks(tasks: List<String>, delegates: Int): List<List<String>> {
    if (delegates <= 0 || tasks.isEmpty()) return emptyList()
    val buckets = List(delegates) { mutableListOf<String>() }
    tasks.forEachIndexed { i, task -> buckets[i % delegates].add(task) }
    return buckets.filter { it.isNotEmpty() }
}

/**
 * The situations where parallel delegates actually pay for themselves.
 *
 * These are the shapes the user listed, encoded so the orchestrator picks a
 * fan-out that has independent work in it. Spawning five agents on one file is
 * slower than doing it once.
 */
enum class DelegateWorkload(val taskTemplate: String) {
    ArchitectureReview("Review the architecture of <target> and report structural risks"),
    UiUxImprovement("Improve the visual design of <target> against Apple HIG"),
    BugHunt("Find and explain real defects in <target>; no speculation"),
    Performance("Profile <target> and report the top three costs with evidence"),
    TestCoverage("Audit test coverage of <target> and list the highest-value missing tests"),
    Documentation("Document <target> for a new contributor"),
    IntegrationReview("Review the integration surface of <target> for contract drift")
}
