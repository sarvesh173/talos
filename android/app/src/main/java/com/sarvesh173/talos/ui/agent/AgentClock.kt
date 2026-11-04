package com.sarvesh173.talos.ui.agent

import kotlin.math.abs

/**
 * Elapsed-time formatting and the notification ladder.
 *
 * Two different jobs, deliberately separated because they have opposite
 * requirements:
 *
 * **In-app display** must be exact and live — "00:01:43" then "00:01:44". A
 * human watching the screen wants the second to tick. It has to keep working
 * past an hour, past a day, past a month, and the same formatter must not
 * produce "1440 minutes" for a day.
 *
 * **Notifications** must be quiet. Pinging every minute trains the user to
 * ignore notifications. Hermes' behaviour — and what this mirrors — is a
 * *doubling* ladder: 3m → 6m → 12m → 24m → 48m → 1h36 → 3h12 … so the user hears
 * about a long job a handful of times rather than sixty. Each step is exactly
 * twice the previous, which is the "3 + 3 = 6, 6 + 6 = 12" cadence.
 */
object AgentClock {

    /**
     * Notification ladder in seconds: 3m, 6m, 12m, 24m, 48m, then it keeps
     * doubling. Computed rather than listed so it stays correct however long
     * the job runs — the sequence never terminates and never repeats.
     *
     * @param step index into the ladder (0 = first notification at 3 minutes)
     */
    fun ladderSeconds(step: Int): Long {
        val base = 3L * 60L
        if (step <= 0) return base
        // Cap the shift so an absurd step cannot overflow into a negative time.
        val shift = step.coerceAtMost(40)
        return base shl shift
    }

    /**
     * The notification steps that have already elapsed at [elapsedSeconds].
     *
     * Returns the count of *crossed* rungs. The caller compares it against the
     * last-notified count to decide whether to fire — that keeps the decision
     * idempotent even if the timer ticks irregularly or the app resumes after
     * being backgrounded.
     */
    fun crossedSteps(elapsedSeconds: Long): Int {
        if (elapsedSeconds < ladderSeconds(0)) return 0
        var steps = 0
        while (elapsedSeconds >= ladderSeconds(steps) && steps < 40) {
            steps++
        }
        return steps
    }

    /**
     * Live elapsed label for the UI.
     *
     * Precision degrades with magnitude on purpose: seconds matter in the first
     * minute, hours do not need seconds, and a month-old job does not need
     * hours. Every branch keeps the *most significant* unit readable.
     */
    fun format(elapsedSeconds: Long): String {
        val s = abs(elapsedSeconds)
        return when {
            s < 60 -> "${s}s"
            s < 3_600 -> "%d:%02d".format(s / 60, s % 60)
            s < 86_400 -> {
                val h = s / 3_600
                val m = (s % 3_600) / 60
                val sec = s % 60
                "%d:%02d:%02d".format(h, m, sec)
            }
            s < 2_592_000L -> { // < 30 days
                val d = s / 86_400
                val h = (s % 86_400) / 3_600
                val m = (s % 3_600) / 60
                "${d}d ${h}h ${m}m"
            }
            s < 31_536_000L -> { // < 365 days
                val mo = s / 2_592_000L
                val d = (s % 2_592_000L) / 86_400
                "${mo}mo ${d}d"
            }
            else -> {
                // Years, then centuries. Long-duration correctness costs one
                // branch and means a runner started in 2025 still reads right
                // in 2125 instead of overflowing into nonsense.
                val y = s / 31_536_000L
                val mo = (s % 31_536_000L) / 2_592_000L
                if (y < 100) "${y}y ${mo}mo" else "${y / 100}c ${y % 100}y"
            }
        }
    }

    /**
     * A compact label for a card badge: "1m 12s", "2h 05m", "3d 4h".
     * Shorter than [format] because it competes with an agent name for space.
     */
    fun compact(elapsedSeconds: Long): String {
        val s = abs(elapsedSeconds)
        return when {
            s < 60 -> "${s}s"
            s < 3_600 -> {
                val m = s / 60
                val sec = s % 60
                if (sec == 0L) "${m}m" else "${m}m ${sec}s"
            }
            s < 86_400 -> {
                val h = s / 3_600
                val m = (s % 3_600) / 60
                if (m == 0L) "${h}h" else "${h}h ${m}m"
            }
            else -> {
                val d = s / 86_400
                val h = (s % 86_400) / 3_600
                if (h == 0L) "${d}d" else "${d}d ${h}h"
            }
        }
    }

    /**
     * When the next notification is due, as a human label — "next in 4m".
     * Returns null once the ladder has effectively stopped mattering (the
     * caller decides, not this function).
     */
    fun nextNotificationIn(elapsedSeconds: Long): Long? {
        val crossed = crossedSteps(elapsedSeconds)
        if (crossed >= 40) return null
        val next = ladderSeconds(crossed)
        return (next - elapsedSeconds).coerceAtLeast(0)
    }
}

/**
 * A running agent, main or delegate.
 *
 * Delegates are the interesting case: they are spawned with a model, a
 * reasoning effort and a task, they work for a bounded time, and then they
 * expire. Keeping the TTL on the record (rather than in the caller) means the
 * UI and the notification scheduler read the same number.
 */
data class AgentRuntime(
    val id: String,
    val name: String,
    val kind: AgentKind = AgentKind.Main,
    /**
     * Delegate default is OpenRouter's `stealth/crouton-alpha`: 1,048,576-token
     * context, text/image/audio/video/file input, tool calling, structured
     * output, and reasoning_effort — free for a short window. Main agents keep
     * whatever the user paired; a free frontier model is a strict upgrade for
     * subtasks, and a bad default for the orchestrator.
     *
     * Fallbacks when it disappears: nemotron-3.5-super-vl-free (vision, 1M),
     * then deepseek-v4.1-flash-free.
     */
    val model: String = DELEGATE_DEFAULT_MODEL,
    val reasoning: ReasoningEffort = ReasoningEffort.Medium,
    val task: String = "",
    val startedAtMillis: Long = System.currentTimeMillis(),
    /** Delegate lifetime. Null for a main agent, which runs until stopped. */
    val ttlSeconds: Long? = null,
    val parentId: String? = null,
    /** Set when the agent stops. Null while it is still running. */
    val endedAtMillis: Long? = null
) {
    fun elapsedSeconds(nowMillis: Long = System.currentTimeMillis()): Long =
        ((nowMillis - startedAtMillis) / 1000L).coerceAtLeast(0L)

    fun remainingSeconds(nowMillis: Long = System.currentTimeMillis()): Long? {
        val ttl = ttlSeconds ?: return null
        return (ttl - elapsedSeconds(nowMillis)).coerceAtLeast(0L)
    }

    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val ttl = ttlSeconds ?: return false
        return elapsedSeconds(nowMillis) >= ttl
    }
}

enum class AgentKind { Main, Delegate }

/** Preferred delegate model: highest reasoning, multimodal, free tier. */
const val DELEGATE_DEFAULT_MODEL = "stealth/crouton-alpha"

/** Ordered fallbacks if the preferred delegate model stops answering. */
val DELEGATE_FALLBACK_MODELS = listOf(
    "nemotron-3.5-super-vl-free",
    "deepseek-v4.1-flash-free"
)

/**
 * Reasoning effort a delegate runs at. Mirrors what the providers actually
 * expose rather than inventing levels: these map onto `reasoning_effort` /
 * `thinking_budget` at the adapter.
 */
enum class ReasoningEffort(val label: String, val blurb: String) {
    Off("Off", "Answer directly, no chain of thought"),
    Low("Low", "Fast, minimal deliberation"),
    Medium("Medium", "Balanced — the default"),
    High("High", "Longer deliberation, more tool use"),
    Max("Max", "Deepest reasoning, slowest and costliest");

    /** Wire value for OpenAI-compatible providers. */
    fun wireValue(): String = name.lowercase()
}
