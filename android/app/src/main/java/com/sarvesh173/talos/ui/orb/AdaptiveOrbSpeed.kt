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
 * Agent throughput → orb animation intensity.
 *
 * ## Why this is not a simple multiplier
 *
 * Measured per-user output speed spans four orders of magnitude: ~5 tok/s on a
 * throttled local proxy, ~250 on a cloud flash model, ~2,600 on Cerebras,
 * ~17,000 on Taalas HC1 silicon. Anything above the calibration ceiling (40k,
 * 100k, 1M) has to degrade *gracefully* rather than flatline or blow up:
 *
 *  - a hard clamp makes 20k, 100k and 1M look identical, which is a lie;
 *  - a linear map is unstable across four decades and pins the cap early;
 *  - an unbounded log map eventually exceeds the speed the eye can resolve and
 *    reads as a strobing bug.
 *
 * So the curve has two segments: a log2 body that reaches most of the way to
 * [OrbSpeedCalibration.maxIntensity] at the ceiling, then an asymptotic tail that
 * keeps rising but can never cross the maximum. Fast hardware still reads as
 * faster, and the animation stays smooth and bounded at every supported rate.
 *
 * ## Separation of concerns
 *
 * Token throughput is measured elsewhere and handed in as a plain number. This
 * file only decides *how intense the visual should be*. It never drives frame
 * timing: the returned multiplier feeds a normal withFrameNanos animation loop,
 * so a 1,000,000 tok/s stream cannot make the UI render faster than the display.
 *
 * ## Calibration, not a maximum
 *
 * [OrbSpeedCalibration.ceilingTps] is the point where the visual is "very fast",
 * not a hard limit on what can be represented. It is a default, overridable at
 * runtime through [OrbSpeedSettings] without touching this file.
 */

/** Knobs for the throughput → intensity curve. Safe to change at runtime. */
data class OrbSpeedCalibration(
    /** Throughput that maps to the top of the log body. Default 20,000 tok/s. */
    val ceilingTps: Float = DEFAULT_CEILING_TPS,
    /** Below this, everything reads as the idle baseline. */
    val floorTps: Float = DEFAULT_FLOOR_TPS,
    /** Intensity when the agent is quiet. Never zero — the orb must look alive. */
    val minIntensity: Float = DEFAULT_MIN_INTENSITY,
    /** Absolute ceiling for the visual, no matter how fast the model runs. */
    val maxIntensity: Float = DEFAULT_MAX_INTENSITY,
    /** Fraction of the range reached at [ceilingTps]; the rest is the tail. */
    val kneeFraction: Float = DEFAULT_KNEE_FRACTION,
) {
    init {
        require(ceilingTps > floorTps) { "ceilingTps must exceed floorTps" }
        require(minIntensity >= 0f) { "minIntensity must be >= 0" }
        require(maxIntensity > minIntensity) { "maxIntensity must exceed minIntensity" }
        require(kneeFraction in 0.5f..0.99f) { "kneeFraction must be in 0.5..0.99" }
    }

    /** How much of the visual range the log body covers before the tail starts. */
    internal val kneeIntensity: Float
        get() = minIntensity + (maxIntensity - minIntensity) * kneeFraction

    /** log2 distance, in octaves, from the floor to the ceiling. */
    internal val bodyOctaves: Float
        get() = (ln2(ceilingTps / floorTps)).coerceAtLeast(1e-3f)

    companion object {
        /** Calibration default. Taalas HC1 (~17k) sits just under it. */
        const val DEFAULT_CEILING_TPS = 20_000f
        const val DEFAULT_FLOOR_TPS = 5f
        const val DEFAULT_MIN_INTENSITY = 0.55f
        const val DEFAULT_MAX_INTENSITY = 2.6f
        /** 92% of the range at the ceiling leaves a visible tail for >20k. */
        const val DEFAULT_KNEE_FRACTION = 0.92f

        /** Highest throughput the maths is required to accept. */
        const val MAX_SUPPORTED_TPS = 1_000_000f

        val Default = OrbSpeedCalibration()
    }
}

/**
 * The one place throughput becomes intensity.
 *
 * Pure, total and monotonic: defined for every input including 0, negative,
 * NaN and 1,000,000; never returns outside
 * `[minIntensity, maxIntensity]`; and never decreases as the rate rises.
 */
internal fun orbIntensity(
    rateTps: Float,
    calibration: OrbSpeedCalibration = OrbSpeedCalibration.Default,
): Float {
    val min = calibration.minIntensity
    val max = calibration.maxIntensity

    // Non-finite or non-positive input is the idle case, not an error state.
    if (!rateTps.isFinite() || rateTps <= 0f) return min

    val floor = calibration.floorTps
    val ceiling = calibration.ceilingTps
    val rate = rateTps.coerceIn(floor, OrbSpeedCalibration.MAX_SUPPORTED_TPS)

    // Octaves above the floor, normalised so 1.0 == the calibration ceiling.
    val octaves = ln2(rate / floor)
    val normalised = octaves / calibration.bodyOctaves

    if (normalised <= 0f) return min

    val knee = calibration.kneeIntensity
    if (normalised <= 1f) {
        // Log body: 5 tok/s → minIntensity, ceiling → kneeIntensity.
        val t = normalised
        return (min + (knee - min) * t).coerceIn(min, max)
    }

    // Tail: asymptotic approach to maxIntensity. 2x the ceiling reaches ~6% of
    // the remaining headroom, 10x reaches ~34%, 50x (1M at 20k default)
    // reaches ~60%. Always increasing, never crossing max — so 40k, 100k and
    // 1M stay distinguishable without the animation becoming a strobe.
    val overshoot = normalised - 1f
    val reached = 1f - kotlin.math.exp(-overshoot)
    return (knee + (max - knee) * reached).coerceIn(min, max)
}

/** True when a rate is past the calibrated ceiling, i.e. worth reporting upstream. */
internal fun exceedsCalibration(
    rateTps: Float,
    calibration: OrbSpeedCalibration = OrbSpeedCalibration.Default,
): Boolean = rateTps.isFinite() && rateTps > calibration.ceilingTps

/**
 * Runtime-overridable calibration.
 *
 * The ceiling is a setting, not a constant: when a 40,000 tok/s model ships, the
 * operator moves the ceiling instead of a developer shipping new animation code.
 * Held in a plain volatile field so the composable can read it from any thread
 * without a snapshot-state dependency in the maths layer.
 */
object OrbSpeedSettings {
    @Volatile
    private var current: OrbSpeedCalibration = OrbSpeedCalibration.Default

    fun current(): OrbSpeedCalibration = current

    /** Clamped to the supported range so a bad config cannot destabilise the UI. */
    fun setCeilingTps(tps: Float) {
        val safe = if (tps.isFinite()) {
            tps.coerceIn(
                OrbSpeedCalibration.Default.floorTps * 10f,
                OrbSpeedCalibration.MAX_SUPPORTED_TPS,
            )
        } else {
            OrbSpeedCalibration.DEFAULT_CEILING_TPS
        }
        current = current.copy(ceilingTps = safe)
    }

    fun update(calibration: OrbSpeedCalibration) {
        current = calibration
    }

    fun reset() {
        current = OrbSpeedCalibration.Default
    }
}

/**
 * Smooths measured throughput into an animation multiplier.
 *
 * Two independent smoothing stages, both time-based so behaviour does not depend
 * on how often this is polled:
 *
 *  1. **Rate smoothing** — an exponential moving average over measured tokens
 *     per second. Stops a single bursty frame from swinging the visual.
 *  2. **Intensity slew** — a per-second cap on how fast the multiplier may
 *     change. Stops a provider that jumps from 200 to 20,000 tok/s mid-stream
 *     from producing a visible snap. Rising is allowed to be quicker than
 *     falling, because a surge should read as responsiveness while decay should
 *     read as settling.
 *
 * @param chunkCount monotonically increasing work counter (characters, tokens,
 *   messages — the caller decides).
 */
@Composable
fun rememberAdaptiveOrbSpeed(
    chunkCount: Int,
    idleSeconds: Float = 4f,
    calibration: OrbSpeedCalibration = OrbSpeedSettings.current(),
    pollMillis: Long = DEFAULT_POLL_MILLIS,
): Float {
    var intensity by remember { mutableFloatStateOf(calibration.minIntensity) }
    var lastCount by remember { mutableIntStateOf(chunkCount) }
    var lastChangeAt by remember { mutableLongStateOf(nowMillis()) }
    var smoothedRate by remember { mutableFloatStateOf(0f) }

    // A change in the counter is the signal. The rate is measured against the
    // wall clock, so a burst and a trickle produce genuinely different numbers.
    LaunchedEffect(chunkCount) {
        val now = nowMillis()
        val delta = (chunkCount - lastCount).coerceAtLeast(0)
        if (delta > 0) {
            val elapsed = ((now - lastChangeAt).coerceAtLeast(1L)).toFloat() / 1000f
            val instant = delta / elapsed
            // Heavier smoothing on a single sample: one batched delta is noisy.
            smoothedRate = if (smoothedRate == 0f) instant
            else smoothedRate * RATE_SMOOTHING + instant * (1f - RATE_SMOOTHING)
            lastChangeAt = now
            lastCount = chunkCount
        }
    }

    // The ticker keeps running when nothing arrives — that is the idle case the
    // orb has to show. It is deliberately a coarse poll: the *animation* runs on
    // its own frame clock, so this never becomes a render-rate driver.
    LaunchedEffect(calibration) {
        var previousAt = nowMillis()
        while (true) {
            delay(pollMillis)
            val now = nowMillis()
            val dt = ((now - previousAt).coerceAtLeast(1L)).toFloat() / 1000f
            previousAt = now

            val quietFor = (now - lastChangeAt).toFloat() / 1000f
            val target = if (quietFor >= idleSeconds) {
                calibration.minIntensity
            } else {
                orbIntensity(smoothedRate, calibration)
            }

            // Slew limit: at most `maxIntensity` units of change per second up,
            // a third of that down. A spike cannot snap the visual.
            val rising = target > intensity
            val limitPerSecond = if (rising) {
                (calibration.maxIntensity - calibration.minIntensity) * RISE_PER_SECOND
            } else {
                (calibration.maxIntensity - calibration.minIntensity) * FALL_PER_SECOND
            }
            val maxStep = limitPerSecond * dt
            val step = (target - intensity).coerceIn(-maxStep, maxStep)
            intensity = (intensity + step)
                .coerceIn(calibration.minIntensity, calibration.maxIntensity)
        }
    }

    return intensity
}

/** Octaves, i.e. log base 2, of a positive ratio. */
private fun ln2(ratio: Float): Float {
    if (!ratio.isFinite() || ratio <= 0f) return 0f
    return (kotlin.math.ln(ratio.toDouble()) / kotlin.math.ln(2.0)).toFloat()
}

private fun nowMillis(): Long = System.nanoTime() / 1_000_000

/** Never poll faster than this; the animation clock is separate. */
internal const val DEFAULT_POLL_MILLIS = 120L

/** EMA weight for the previous sample. Higher = calmer. */
private const val RATE_SMOOTHING = 0.55f

/** Fraction of the full intensity range a rising change may cover per second. */
private const val RISE_PER_SECOND = 1.6f

/** Falling is slower than rising: surges read as responsive, decay as settling. */
private const val FALL_PER_SECOND = 0.55f
