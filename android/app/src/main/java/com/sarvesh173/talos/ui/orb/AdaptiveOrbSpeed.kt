package com.sarvesh173.talos.ui.orb

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Agent Speed → Orb Speed.
 *
 * A fixed-speed spinner lies: it looks identical whether the agent is idle,
 * streaming one token a second, or firing a 200-token burst. The orb should
 * read as a tachometer for the agent's actual output rate.
 *
 * [rememberAdaptiveOrbSpeed] watches how much progress lands per unit of time
 * and returns a speed multiplier:
 *
 *  - nothing arriving → eases toward the resting pace (not zero — the orb must
 *    still visibly mean "alive but quiet");
 *  - steady output → ~1×, the tuned baseline;
 *  - a burst → climbs fast, but saturates at [maxSpeed]. Past a point a faster
 *    animation stops reading as "busy" and starts reading as "broken".
 *
 * Decay is slower than attack on purpose: the eye should see the surge, then
 * watch it settle, rather than flicker on every token.
 *
 * [chunkCount] is any monotonically increasing measure of progress — character
 * count, message count, tool-call count. The caller decides what "work" means.
 */
@Composable
fun rememberAdaptiveOrbSpeed(
    chunkCount: Int,
    idleSeconds: Float = 4f,
    minSpeed: Float = 0.55f,
    maxSpeed: Float = 2.6f,
    pollMillis: Long = 120L
): Float {
    var speed by remember { mutableFloatStateOf(1f) }
    var lastCount by remember { mutableIntStateOf(chunkCount) }
    var lastChangeAt by remember { mutableLongStateOf(nowMillis()) }
    var rate by remember { mutableFloatStateOf(0f) }

    // A change in the counter is the signal; the rate is measured against the
    // wall clock so a burst and a trickle produce different numbers.
    LaunchedEffect(chunkCount) {
        val now = nowMillis()
        val delta = (chunkCount - lastCount).coerceAtLeast(0)
        if (delta > 0) {
            val elapsed = ((now - lastChangeAt).coerceAtLeast(1L)).toFloat() / 1000f
            val instant = delta / elapsed
            rate = if (rate == 0f) instant else rate * 0.55f + instant * 0.45f
            lastChangeAt = now
            lastCount = chunkCount
        }
    }

    // The ticker keeps moving even when no new chunks arrive — that is exactly
    // the idle case the orb has to show.
    LaunchedEffect(Unit) {
        while (true) {
            delay(pollMillis)
            val sinceLast = (nowMillis() - lastChangeAt).toFloat() / 1000f
            val target = if (sinceLast >= idleSeconds) {
                minSpeed
            } else {
                orbSpeedTarget(rate, minSpeed, maxSpeed)
            }
            // Asymmetric smoothing: fast up, slow down.
            val factor = if (target > speed) 0.35f else 0.08f
            speed += (target - speed) * factor
        }
    }

    return speed
}

/**
 * Map a measured chunk rate to an orb speed multiplier.
 *
 * Calibration is against measured per-user output speed, not localhost and not
 * aggregate throughput. Verified 4 Nov 2026 from independent provider
 * benchmarks:
 *
 *     Cerebras Llama 4 Scout  2623  (fastest measured per-user)
 *     Celeris-1                1581-1712
 *     Mercury 2.5              1178
 *     GLM-5.3-Flash (Inco)      593  (best public provider, AA-verified)
 *     Groq Llama 4 Scout        621
 *     SambaNova Llama 4 Scout   725
 *     Nemotron 3.5 Super VL     428-485
 *     Gemini 3.5 Flash Lite     402
 *     GLM-5.3-Flash (Lambda)    140  per user at interactive latency
 *     GLM-5.3-Flash (Petronella) 238  single user, 4x H200 FP8
 *     DeepSeek V4.1 Flash       248
 *     -> slow tail on local proxies: 5-70 tok/s
 *
 * Aggregate numbers are traps and were deliberately excluded. "Lane0 1518",
 * "Blackwell 10,000", "GLM-5.3-Flash 2591" are all *total server* throughput
 * across many concurrent users — Lambda's own table shows the same model at
 * 140 tok/s per user at C1 and 21 tok/s per user at C256. A per-user animation
 * must be calibrated on per-user numbers or it lies.
 *
 * Real per-user spread: 5 → 3000 tok/s, ~9.2 octaves. log2 with 0.14/octave
 * maps it onto a legible 1.0 → 2.6 curve:
 *
 *     rate =    5   → 1.33  (slow local proxy)
 *     rate =   60   → 1.83
 *     rate =  140   → 2.00  (GLM-5.3-Flash at interactive latency)
 *     rate =  248   → 2.11  (DeepSeek V4.1 Flash)
 *     rate =  621   → 2.30  (Llama 4 Scout on Groq)
 *     rate =  725   → 2.33  (Llama 4 Scout on SambaNova)
 *     rate = 1518   → 2.48  (Celeris-1)
 *     rate = 2623   → 2.59  (Llama 4 Scout on Cerebras — the real ceiling)
 *     rate >= 3000  → 2.60  (saturated)
 *
 * Saturation at the top is deliberate — that is the user's requirement:
 * "extreme fast nahi, obviously uski bhi ek limit honi chahiye." Past the cap a
 * faster animation stops reading as busy and starts reading as broken.
 */
internal fun orbSpeedTarget(rate: Float, minSpeed: Float, maxSpeed: Float): Float {
    if (rate <= 0f) return minSpeed
    // log2 puts each doubling on one unit; 0.14/octave spreads 5-3000 tok/s
    // across the full 1.0-2.6 range without pinning early.
    val octaves = (kotlin.math.ln(rate.toDouble()) / kotlin.math.ln(2.0)).toFloat()
    val target = 1f + (octaves.coerceAtLeast(0f) * 0.14f)
    return target.coerceIn(minSpeed, maxSpeed)
}

private fun nowMillis(): Long = System.nanoTime() / 1_000_000
