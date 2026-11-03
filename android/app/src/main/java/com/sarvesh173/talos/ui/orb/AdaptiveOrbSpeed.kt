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
                // ~1 chunk/s is the tuned baseline; scale from there and clamp.
                (1f + rate * 0.12f).coerceIn(minSpeed, maxSpeed)
            }
            // Asymmetric smoothing: fast up, slow down.
            val factor = if (target > speed) 0.35f else 0.08f
            speed += (target - speed) * factor
        }
    }

    return speed
}

private fun nowMillis(): Long = System.nanoTime() / 1_000_000
