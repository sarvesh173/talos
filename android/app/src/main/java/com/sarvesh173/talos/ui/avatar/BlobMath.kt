package com.sarvesh173.talos.ui.avatar

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * BlobMath — mathematically pure deformation core for the blob identity form.
 * r(theta, t) is a finite Fourier sum in theta of a time-varying envelope —
 * automatically C1 (analytic) in theta and t, so no visible seams.
 * Pure Kotlin, no Compose types, fully unit-testable.
 */

enum class BlobArchetype {
    BREATHE,
    PULSE,
    SLOSH,
    RIPPLE,
    CHURN,
    DRIFT,
    KNEAD,
    BOUNCE,
    DRIP,
    BLOOM,
    TIGHTEN,
    SHEAR
}

enum class BlobSymmetry { SYMMETRIC, ASYMMETRIC, TRAVELLING }

enum class BlobSpeedBand {
    SLOW, SLOW_MEDIUM, MEDIUM, MEDIUM_FAST, FAST;

    val multiplier: Float
        get() = when (this) {
            SLOW -> 0.4f
            SLOW_MEDIUM -> 0.7f
            MEDIUM -> 1f
            MEDIUM_FAST -> 1.5f
            FAST -> 2.2f
        }
}

data class BlobMotionParams(
    val archetype: BlobArchetype,
    val lobeCount: Int,
    val speedBand: BlobSpeedBand,
    val symmetry: BlobSymmetry,
    val baseRadius: Float = 1f,
    val amplitude: Float = 0.22f,
    val seed: Float = 0f
) {
    init {
        require(lobeCount in 4..12) { "lobeCount $lobeCount out of 4..12" }
        require(baseRadius > 0f && baseRadius.isFinite()) { "baseRadius must be positive finite" }
        require(amplitude in 0f..0.45f) { "amplitude keeps radius positive" }
    }

    val timeScale: Float get() = speedBand.multiplier
}

fun archetypeOmega(a: BlobArchetype): Float = when (a) {
    BlobArchetype.BREATHE -> 1.5708f
    BlobArchetype.PULSE -> 3.1416f
    BlobArchetype.SLOSH -> 2.0944f
    BlobArchetype.RIPPLE -> 6.2832f
    BlobArchetype.CHURN -> 2.618f
    BlobArchetype.DRIFT -> 0.5236f
    BlobArchetype.KNEAD -> 1.2566f
    BlobArchetype.BOUNCE -> 4.1888f
    BlobArchetype.DRIP -> 1.0472f
    BlobArchetype.BLOOM -> 0.7854f
    BlobArchetype.TIGHTEN -> 1.0472f
    BlobArchetype.SHEAR -> 1.5708f
}

fun lobePhase(lobe: Int, seed: Float): Float {
    val x = sin(lobe * 127.1f + seed * 311.7f) * 43758.5453f
    val frac = x - floor(x)
    return frac * 2f * PI.toFloat()
}

fun lobeWeight(lobe: Int, lobeCount: Int): Float = 1f / (1f + 0.35f * (lobe - 1))

private fun impulseBump(frac: Float, sharpness: Float): Float {
    val u = 2f * PI.toFloat() * frac
    return exp(-sharpness * (1f - cos(u)))
}

private fun smoothPulse(frac: Float): Float {
    var s = sin(2f * PI.toFloat() * frac)
    s = s.coerceIn(-1f, 1f)
    val u = 0.5f * (s + 1f)
    return u * u * (3f - 2f * u)
}

private fun envelopeAndPhase(a: BlobArchetype, t: Float, speed: Float): Pair<Float, Float> {
    val w = archetypeOmega(a) * speed
    return when (a) {
        BlobArchetype.BREATHE -> Pair(0.5f + 0.5f * sin(w * t), 0f)
        BlobArchetype.PULSE -> {
            val s = 0.5f + 0.5f * sin(w * t)
            Pair(s * s * (0.25f + 0.75f * s), 0f)
        }
        BlobArchetype.SLOSH -> Pair(0.5f + 0.5f * sin(w * t), 1.6f)
        BlobArchetype.RIPPLE -> Pair(0.8f + 0.2f * sin(w * t), -2.2f)
        BlobArchetype.CHURN -> {
            val e = 0.6f + 0.4f * sin(w * t + 1.3f * sin(0.7f * w * t))
            Pair(e, 0.9f)
        }
        BlobArchetype.DRIFT -> Pair(0.5f + 0.5f * sin(w * t + 2f * sin(0.31f * w * t)), 0.25f)
        BlobArchetype.KNEAD -> {
            val frac = (w * t / (2f * PI.toFloat())).let { it - floor(it) }
            Pair(smoothPulse(frac), 0.7f)
        }
        BlobArchetype.BOUNCE -> {
            val frac = (w * t / (2f * PI.toFloat())).let { it - floor(it) }
            Pair(impulseBump(frac, 2.5f), 2.8f)
        }
        BlobArchetype.DRIP -> {
            val frac = (w * t / (2f * PI.toFloat())).let { it - floor(it) }
            Pair(1f - 0.6f * smoothPulse(frac), 0.5f)
        }
        BlobArchetype.BLOOM -> Pair(0.5f + 0.5f * sin(w * t), 0.35f)
        BlobArchetype.TIGHTEN -> {
            val s = 0.5f + 0.5f * sin(w * t)
            Pair(1f - s * s, 0f)
        }
        BlobArchetype.SHEAR -> Pair(0.5f + 0.5f * sin(w * t), 1.1f)
    }
}

fun blobRadius(theta: Float, t: Float, params: BlobMotionParams): Float {
    if (!t.isFinite() || !theta.isFinite()) return params.baseRadius
    val tt = t.coerceAtLeast(0f)
    val (env, phaseRate) = envelopeAndPhase(params.archetype, tt, params.timeScale)
    var sum = 0f
    var weightTotal = 0f
    for (lobe in 1..params.lobeCount) {
        if (params.symmetry == BlobSymmetry.SYMMETRIC && lobe % 2 != 0) continue
        val wgt = lobeWeight(lobe, params.lobeCount)
        val basePhase = lobePhase(lobe, params.seed)
        var phase = basePhase + phaseRate * lobe * tt
        when (params.symmetry) {
            BlobSymmetry.TRAVELLING -> phase = basePhase - 2.2f * lobe * archetypeOmega(params.archetype) * params.timeScale * tt
            BlobSymmetry.SYMMETRIC -> phase = (lobe * 0.5f) + phaseRate * (lobe / 2f) * tt
            BlobSymmetry.ASYMMETRIC -> Unit
        }
        if (params.archetype == BlobArchetype.SHEAR) {
            phase += 1.8f * sin(theta) * env
        }
        var w2 = wgt
        if (params.archetype == BlobArchetype.BLOOM) {
            w2 *= (0.4f + 0.6f * env * (lobe.toFloat() / params.lobeCount.toFloat()))
        }
        sum += w2 * cos(lobe * theta + phase)
        weightTotal += wgt
    }
    val s = if (weightTotal > 0f) sum / weightTotal else 0f
    val deformation = (params.amplitude * env * s).coerceIn(-0.55f, 0.55f)
    val r = params.baseRadius * (1f + deformation)
    return if (r.isFinite() && r > 1e-4f) r else params.baseRadius
}

fun wobbleEnergy(params: BlobMotionParams, t: Float): Float {
    if (!t.isFinite()) return 0f
    val tt = t.coerceAtLeast(0f)
    val (env, _) = envelopeAndPhase(params.archetype, tt, params.timeScale)
    var acc = 0f
    val steps = 8
    for (i in 0 until steps) {
        val th = (i.toFloat() / steps) * 2f * PI.toFloat()
        val d = (blobRadius(th, tt, params) / params.baseRadius - 1f).let { it * it }
        acc += d
    }
    val meanSq = acc / steps
    val e = env * (0.35f + 0.65f * sqrt((meanSq / 0.3f).coerceIn(0f, 1f)))
    return e.coerceIn(0f, 1f)
}

object BlobAnimationLibrary {
    const val ARCHETYPE_COUNT = 12
    const val LOBE_COUNT_VALUES = 9
    const val SPEED_BAND_COUNT = 5
    const val SYMMETRY_COUNT = 3
    const val TOTAL = ARCHETYPE_COUNT * LOBE_COUNT_VALUES * SPEED_BAND_COUNT * SYMMETRY_COUNT

    val presets: List<BlobMotionParams> = run {
        val list = ArrayList<BlobMotionParams>(TOTAL)
        for (a in BlobArchetype.entries) {
            for (lobes in 4..12) {
                for (band in BlobSpeedBand.entries) {
                    for (sym in BlobSymmetry.entries) {
                        list.add(
                            BlobMotionParams(
                                archetype = a,
                                lobeCount = lobes,
                                speedBand = band,
                                symmetry = sym,
                                seed = (a.ordinal * 131 + lobes * 17 + band.ordinal * 7 + sym.ordinal * 3).toFloat()
                            )
                        )
                    }
                }
            }
        }
        list
    }

    val count: Int get() = presets.size

    fun key(p: BlobMotionParams): String = "${p.archetype}|${p.lobeCount}|${p.speedBand}|${p.symmetry}"

    fun byIndex(i: Int): BlobMotionParams = presets[Math.floorMod(i, presets.size)]
}
