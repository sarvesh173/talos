package com.sarvesh173.talos.ui.bot

import android.content.Context
import com.sarvesh173.talos.ui.emblemByKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Async, deduplicated, single-flight bake path for point clouds.
 *
 * ## Cost model
 *
 * [AgentLogoBaker.bakeFromContours] does grid sampling + Poisson-disk
 * rejection + a full O(P × C) distance transform; its cost grows with the
 * requested point count and it must never run on the UI thread. This queue
 * bakes on a single-threaded dispatcher (`limitedParallelism(1)`) so the
 * whole app never has more than one bake in flight, no matter how many
 * marks are visible at once. Successful bakes are stored in the LRU-capped
 * [AgentLogoClouds] cache, so a re-entered screen never re-bakes.
 *
 * ## Deduplication + cancellation
 *
 * Concurrent calls to [request] for the same agent id (case-insensitive)
 * share one bake: their callbacks all fire with the same cloud. Each call
 * returns its own [Job]; cancelling a returned job drops only that caller's
 * callback. When the last waiter for a bake cancels before completion, the
 * bake job itself is cancelled — nothing runs that nobody is listening to.
 * A cancelled caller never receives the callback, and a finished bake that
 * lands after its last waiter left is still cached for reuse.
 *
 * Callbacks always run on `Dispatchers.Main.immediate` so Compose state can
 * be set directly. If a bake fails (e.g. a malformed SVG), no callback is
 * delivered; cache it as a miss so a retry starts a fresh request.
 *
 * ## Exact adoption steps for `AgentLogoMark.kt`
 *
 * 1. At the top of the composable, replace the synchronous bake
 *    `val cloud = remember(agentId, context) { AgentLogoClouds.cloudFor(...) }`
 *    with:
 *    ```kotlin
 *    var cloud by remember { mutableStateOf<AgentLogoCloud?>(null) }
 *    LaunchedEffect(agentId, userSvg) {
 *        val job = AgentLogoQueue.request(context, agentId, userSvg) { cloud = it }
 *        try {
 *            awaitCancellation() // wait for dispose / key change to stop us
 *        } finally {
 *            job.cancel() // drops our callback; bake keeps going for others
 *        }
 *    }
 *    ```
 *    Guard the existing `LogoMotion.frame(...)` + `Canvas` calls with
 *    `cloud?.let { ... }` (or a placeholder dot) while `cloud == null`.
 *    New imports: `androidx.compose.runtime.mutableStateOf`,
 *    `kotlinx.coroutines.awaitCancellation`.
 * 2. Frame budget (inside the composable, not the `Canvas` block): keep
 *    `var budget by remember { mutableIntStateOf(220) }`, and inside the
 *    existing `withFrameNanos` loop pass the previous frame's duration:
 *    ```kotlin
 *    val prevMs = (now - lastNanos) / 1_000_000f
 *    budget = computeFrameBudget(prevMs, budget, cloud?.count ?: budget)
 *    ```
 *    Then draw by striding — never a prefix `.take(budget)`, because dots
 *    are sorted far-to-near and a prefix would drop the nearest, largest
 *    dots first:
 *    ```kotlin
 *    val step = (dots.size.toDouble() / budget.coerceAtLeast(1)).coerceAtLeast(1.0)
 *    for (i in 0 until minOf(budget, dots.size)) {
 *        val dot = dots[(i * step).toInt().coerceAtMost(dots.size - 1)]
 *        // ...existing drawCircle for dot...
 *    }
 *    ```
 *    New import: `androidx.compose.runtime.mutableIntStateOf`.
 * 3. Activity gate: replace the bare `if (paused || animatorScale <= 0f)`
 *    early-return in the `LaunchedEffect` with
 *    ```kotlin
 *    val lifecycle = LocalLifecycleOwner.current.lifecycle
 *    val inBackground = !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
 *    var visible by remember { mutableStateOf(true) }
 *    // set `visible` from onGloballyPositioned / LazyList viewport state:
 *    // visible = boundsInWindow intersects window
 *    LaunchedEffect(state, intensity, animatorScale, paused, visible, inBackground) {
 *        if (!isActive(paused, visible, inBackground, animatorScale)) return@LaunchedEffect
 *        // ...existing withFrameNanos loop unchanged...
 *    }
 *    ```
 *    Include `visible` and `inBackground` in the `LaunchedEffect` keys so
 *    the loop restarts when they change. New imports:
 *    `androidx.lifecycle.Lifecycle`, `androidx.lifecycle.compose.LocalLifecycleOwner`
 *    (or `androidx.compose.ui.platform.LocalLifecycleOwner` on older versions),
 *    `androidx.compose.ui.layout.onGloballyPositioned` if using viewport
 *    geometry for `visible`.
 */
object AgentLogoQueue {

    /** One bake at a time across the whole app — six marks can't pin six cores. */
    private val bakeDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val inFlight = mutableMapOf<String, BakeGroup>()

    private class BakeGroup(val deferred: CompletableDeferred<AgentLogoCloud>) {
        var bakeJob: Job? = null
        var waiters: Int = 0
    }

    /**
     * Request the cloud for [agentId], baking off the UI thread if needed.
     *
     * @param context used for resources; only its application context is kept
     * @param agentId agent whose emblem to bake (matched case-insensitively)
     * @param svgOrNull raw SVG (full `<svg>` doc or bare path `d` data) for a
     *   user-supplied mark; null falls back to the agent's VectorDrawable
     * @param onReady invoked on `Dispatchers.Main.immediate` with the cloud
     * @return a [Job] that cancels this caller's subscription when cancelled
     */
    fun request(
        context: Context,
        agentId: String,
        svgOrNull: String?,
        onReady: (AgentLogoCloud) -> Unit
    ): Job {
        val appContext = context.applicationContext
        val key = agentId.lowercase()

        AgentLogoClouds.cached(key)?.let { cloud ->
            return scope.launch(Dispatchers.Main.immediate) { onReady(cloud) }
        }

        val group = synchronized(lock) {
            val existing = inFlight[key]
            val g = existing ?: BakeGroup(CompletableDeferred<AgentLogoCloud>()).also { new ->
                new.bakeJob = scope.launch(bakeDispatcher) {
                    try {
                        val cloud = bake(appContext, key, svgOrNull)
                        AgentLogoClouds.put(key, cloud)
                        new.deferred.complete(cloud)
                    } catch (t: Throwable) {
                        new.deferred.completeExceptionally(t)
                    } finally {
                        synchronized(lock) {
                            if (inFlight[key] === new) inFlight.remove(key)
                        }
                    }
                }
                inFlight[key] = new
            }
            g.waiters++
            g
        }

        val job = scope.launch(Dispatchers.Main.immediate) {
            try {
                onReady(group.deferred.await())
            } catch (t: Throwable) {
                // Caller cancelled, or the bake failed: no callback, no crash.
            }
        }
        job.invokeOnCompletion {
            synchronized(lock) {
                group.waiters--
                if (group.waiters <= 0 && !group.deferred.isCompleted) {
                    if (inFlight[key] === group) inFlight.remove(key)
                    group.bakeJob?.cancel()
                }
            }
        }
        return job
    }

    /** Number of bakes currently queued/running. Test hook. */
    fun inFlightCount(): Int = synchronized(lock) { inFlight.size }

    /** Number of bakes currently queued/running. Test hook. */
    fun pendingCount(): Int = synchronized(lock) { inFlight.size }

    /** Cancel every in-flight bake (e.g. on process shutdown). */
    fun cancelAll() {
        val jobs: List<Job?> = synchronized(lock) {
            val all = inFlight.values.map { it.bakeJob }
            inFlight.clear()
            all
        }
        jobs.forEach { it?.cancel() }
    }

    private fun bake(context: Context, key: String, svgOrNull: String?): AgentLogoCloud {
        if (!svgOrNull.isNullOrBlank()) {
            val contours = extractSvgContours(svgOrNull)
            if (contours.isNotEmpty()) {
                return AgentLogoBaker.bakeFromContours(contours)
            }
        }
        return AgentLogoBaker.bakeFromVectorDrawable(context, emblemByKey(key).drawableRes)
    }

    /** Extract path geometry from a full `<svg>` document or bare path data. */
    internal fun extractSvgContours(svg: String): List<List<Pt>> {
        val pathDatas: List<String> = if ("<" in svg) {
            SVG_PATH_RE.findAll(svg).map { it.groupValues[1] }.toList()
        } else {
            listOf(svg.trim())
        }
        return pathDatas.mapNotNull { d ->
            AgentLogoBaker.flattenPath(d).takeIf { it.size >= 3 }
        }
    }

    // Matches d="..." and d='...' attributes in a full <svg> document.
    private val SVG_PATH_RE = Regex("""d\s*=\s*["']([^"']+)["']""")
}
