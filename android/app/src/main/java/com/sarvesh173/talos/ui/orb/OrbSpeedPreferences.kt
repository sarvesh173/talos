package com.sarvesh173.talos.ui.orb

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists the orb calibration ceiling.
 *
 * The ceiling is an operational setting, not a build-time constant: when a
 * 40,000 tok/s model ships, the operator moves it in Settings instead of waiting
 * for an animation-code change. This file owns the storage only — the maths
 * stays in [OrbSpeedSettings] and remains pure.
 *
 * Stored as a plain float in its own preferences file so a corrupt value can be
 * dropped without touching theme, auth, or any other setting.
 */
object OrbSpeedPreferences {

    private const val PREFS = "talos_orb_speed"
    private const val KEY_CEILING = "ceiling_tps"
    private const val KEY_LAST_MEASURED = "last_measured_tps"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Read the stored ceiling and push it into the live calibration. */
    fun load(context: Context): OrbSpeedCalibration {
        val stored = runCatching { prefs(context).getFloat(KEY_CEILING, Float.NaN) }
            .getOrDefault(Float.NaN)
        if (stored.isFinite() && stored > 0f) {
            OrbSpeedSettings.setCeilingTps(stored)
        } else {
            OrbSpeedSettings.reset()
        }
        return OrbSpeedSettings.current()
    }

    /** Persist a new ceiling and apply it immediately. */
    fun saveCeiling(context: Context, tps: Float): OrbSpeedCalibration {
        OrbSpeedSettings.setCeilingTps(tps)
        val applied = OrbSpeedSettings.current()
        runCatching {
            prefs(context).edit().putFloat(KEY_CEILING, applied.ceilingTps).apply()
        }
        return applied
    }

    fun reset(context: Context): OrbSpeedCalibration {
        OrbSpeedSettings.reset()
        runCatching { prefs(context).edit().remove(KEY_CEILING).apply() }
        return OrbSpeedSettings.current()
    }

    /**
     * Record the highest rate seen, so Settings can show "your fastest model
     * runs at X tok/s" and suggest a sensible ceiling instead of a bare number.
     */
    fun recordMeasured(context: Context, tps: Float) {
        if (!tps.isFinite() || tps <= 0f) return
        val previous = runCatching { prefs(context).getFloat(KEY_LAST_MEASURED, 0f) }
            .getOrDefault(0f)
        if (tps <= previous) return
        runCatching {
            prefs(context).edit().putFloat(KEY_LAST_MEASURED, tps).apply()
        }
    }

    fun lastMeasured(context: Context): Float =
        runCatching { prefs(context).getFloat(KEY_LAST_MEASURED, 0f) }
            .getOrDefault(0f)

    /**
     * A ceiling that gives the fastest observed model visible headroom rather
     * than sitting right at the knee. Rounds up to a friendly number.
     */
    fun suggestedCeiling(context: Context): Float {
        val fastest = lastMeasured(context)
        if (fastest <= 0f) return OrbSpeedCalibration.DEFAULT_CEILING_TPS
        val want = fastest * 1.5f
        val steps = floatArrayOf(
            1_000f, 5_000f, 10_000f, 20_000f, 40_000f, 80_000f, 100_000f,
            200_000f, 500_000f, OrbSpeedCalibration.MAX_SUPPORTED_TPS,
        )
        return steps.firstOrNull { it >= want } ?: OrbSpeedCalibration.MAX_SUPPORTED_TPS
    }
}
