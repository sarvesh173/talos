package com.sarvesh173.talos.ui.bot

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.sarvesh173.talos.ui.emblemByKey
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Every tuned dial of the ThinkingLogoPro point-cloud animation, per agent.
 *
 * Data provenance (read verbatim from /home/kira/talos-refs/ThinkingLogoPro):
 * - `src/presets.ts` — per state per size `speed` / `count` / `size` multipliers
 *   plus `extra` keys (`scanMul`, `dimBase`, `spin`, `bandMul`, `wobMul`, `spread`).
 * - `src/types.ts` — [AgentLogoState] (7 logo states in the Kotlin port;
 *   9 orb states upstream) and sizes 64 / 20.
 * - `README.md` bake table — `count`, `style`/`shell` (flat/dome/slab),
 *   `depth` (0.34), `resolution` (256), `threshold` (0.5), `margin` (0.06),
 *   `seed` (1); cycle knobs `dwell` + `morph` (full cycle = dwell + 2 x morph).
 * - `AgentLogoCloud.kt` — `DEFAULT_COUNT=220`, `DEFAULT_SEED=1`,
 *   `DEFAULT_DEPTH=0.34`, `bakeFromContours(contours, count, seed, depth, fillType)`.
 *
 * Defaults below are the Kotlin port's shipped values so an untouched agent
 * renders exactly as before.
 */
enum class OpacityCurve { Linear, Sqrt, Exp }

enum class ShellStyle { Flat, Dome, Slab }

enum class TintMode { Brand, MonoWhite, MonoBlack, Custom }

/**
 * Partial per-state override: every field nullable, so a state can be tuned
 * without touching the rest. Null means "inherit the global value".
 */
data class LogoStateOverride(
    val speedMultiplier: Float? = null,
    val count: Int? = null,
    val dotRadius: Float? = null,
    val dwellSeconds: Float? = null,
    val morphSeconds: Float? = null,
    val spinMultiplier: Float? = null,
    val bandMultiplier: Float? = null,
    val wobbleMultiplier: Float? = null,
    val opacityStrength: Float? = null
)

/** Fully resolved parameters for one state (global merged with its override). */
data class ResolvedLogoParams(
    val state: String,
    val count: Int,
    val dotRadius: Float,
    val opacityCurve: OpacityCurve,
    val opacityStrength: Float,
    val shell: ShellStyle,
    val depth: Float,
    val margin: Float,
    val threshold: Float,
    val resolution: Int,
    val seed: Int,
    val dwellSeconds: Float,
    val morphSeconds: Float,
    val speedMultiplier: Float,
    val spinMultiplier: Float,
    val bandMultiplier: Float,
    val wobbleMultiplier: Float,
    val scanMultiplier: Float,
    val dimBase: Float,
    val spreadMultiplier: Float,
    val tintMode: TintMode,
    val customTintHex: String,
    val borderTintHex: String
)

data class LogoCustomization(
    // Dots
    val count: Int = DEFAULT_COUNT,
    val dotRadius: Float = DEFAULT_DOT_RADIUS,
    val opacityCurve: OpacityCurve = OpacityCurve.Linear,
    val opacityStrength: Float = DEFAULT_OPACITY_STRENGTH,
    // Geometry (bake)
    val shell: ShellStyle = ShellStyle.Dome,
    val depth: Float = DEFAULT_DEPTH,
    val margin: Float = DEFAULT_MARGIN,
    val threshold: Float = DEFAULT_THRESHOLD,
    val resolution: Int = DEFAULT_RESOLUTION,
    val seed: Int = DEFAULT_SEED,
    // Motion (README: full cycle = dwell + 2 x morph; presets: speed multiplier)
    val dwellSeconds: Float = DEFAULT_DWELL_SECONDS,
    val morphSeconds: Float = DEFAULT_MORPH_SECONDS,
    val speedMultiplier: Float = DEFAULT_SPEED_MULTIPLIER,
    val spinMultiplier: Float = DEFAULT_SPIN_MULTIPLIER,
    val bandMultiplier: Float = DEFAULT_BAND_MULTIPLIER,
    val wobbleMultiplier: Float = DEFAULT_WOBBLE_MULTIPLIER,
    // presets.ts `extra` keys
    val scanMultiplier: Float = DEFAULT_SCAN_MULTIPLIER,
    val dimBase: Float = DEFAULT_DIM_BASE,
    val spreadMultiplier: Float = DEFAULT_SPREAD_MULTIPLIER,
    // Colour
    val tintMode: TintMode = TintMode.Brand,
    val customTintHex: String = DEFAULT_CUSTOM_TINT_HEX,
    val borderTintHex: String = DEFAULT_BORDER_TINT_HEX,
    // Per-state partial overrides, keyed by AgentLogoState name (e.g. "Thinking")
    val stateOverrides: Map<String, LogoStateOverride> = emptyMap()
) {
    /** Copy with every numeric dial clamped into its documented range. */
    fun sanitized(): LogoCustomization = copy(
        count = count.coerceIn(MIN_COUNT, MAX_COUNT),
        dotRadius = dotRadius.coerceIn(MIN_DOT_RADIUS, MAX_DOT_RADIUS),
        opacityStrength = opacityStrength.coerceIn(MIN_OPACITY_STRENGTH, MAX_OPACITY_STRENGTH),
        depth = depth.coerceIn(MIN_DEPTH, MAX_DEPTH),
        margin = margin.coerceIn(MIN_MARGIN, MAX_MARGIN),
        threshold = threshold.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD),
        resolution = RESOLUTIONS.firstOrNull { it == resolution } ?: DEFAULT_RESOLUTION,
        seed = seed.coerceAtLeast(MIN_SEED),
        dwellSeconds = dwellSeconds.coerceIn(MIN_DWELL_SECONDS, MAX_DWELL_SECONDS),
        morphSeconds = morphSeconds.coerceIn(MIN_MORPH_SECONDS, MAX_MORPH_SECONDS),
        speedMultiplier = speedMultiplier.coerceIn(MIN_SPEED_MULTIPLIER, MAX_SPEED_MULTIPLIER),
        spinMultiplier = spinMultiplier.coerceIn(MIN_SPIN_MULTIPLIER, MAX_SPIN_MULTIPLIER),
        bandMultiplier = bandMultiplier.coerceIn(MIN_BAND_MULTIPLIER, MAX_BAND_MULTIPLIER),
        wobbleMultiplier = wobbleMultiplier.coerceIn(MIN_WOBBLE_MULTIPLIER, MAX_WOBBLE_MULTIPLIER),
        scanMultiplier = scanMultiplier.coerceIn(MIN_SCAN_MULTIPLIER, MAX_SCAN_MULTIPLIER),
        dimBase = dimBase.coerceIn(MIN_DIM_BASE, MAX_DIM_BASE),
        spreadMultiplier = spreadMultiplier.coerceIn(MIN_SPREAD_MULTIPLIER, MAX_SPREAD_MULTIPLIER),
        customTintHex = if (isValidHex(customTintHex)) customTintHex else DEFAULT_CUSTOM_TINT_HEX,
        borderTintHex = if (isValidHex(borderTintHex)) borderTintHex else DEFAULT_BORDER_TINT_HEX
    )

    /** Resolve the effective parameters for [state] (global + its override). */
    fun effectiveFor(state: AgentLogoState): ResolvedLogoParams {
        val o = stateOverrides[state.name]
        return ResolvedLogoParams(
            state = state.name,
            count = (o?.count ?: count).coerceIn(MIN_COUNT, MAX_COUNT),
            dotRadius = (o?.dotRadius ?: dotRadius).coerceIn(MIN_DOT_RADIUS, MAX_DOT_RADIUS),
            opacityCurve = opacityCurve,
            opacityStrength = (o?.opacityStrength ?: opacityStrength)
                .coerceIn(MIN_OPACITY_STRENGTH, MAX_OPACITY_STRENGTH),
            shell = shell,
            depth = depth,
            margin = margin,
            threshold = threshold,
            resolution = resolution,
            seed = seed,
            dwellSeconds = (o?.dwellSeconds ?: dwellSeconds)
                .coerceIn(MIN_DWELL_SECONDS, MAX_DWELL_SECONDS),
            morphSeconds = (o?.morphSeconds ?: morphSeconds)
                .coerceIn(MIN_MORPH_SECONDS, MAX_MORPH_SECONDS),
            speedMultiplier = (o?.speedMultiplier ?: speedMultiplier)
                .coerceIn(MIN_SPEED_MULTIPLIER, MAX_SPEED_MULTIPLIER),
            spinMultiplier = (o?.spinMultiplier ?: spinMultiplier)
                .coerceIn(MIN_SPIN_MULTIPLIER, MAX_SPIN_MULTIPLIER),
            bandMultiplier = (o?.bandMultiplier ?: bandMultiplier)
                .coerceIn(MIN_BAND_MULTIPLIER, MAX_BAND_MULTIPLIER),
            wobbleMultiplier = (o?.wobbleMultiplier ?: wobbleMultiplier)
                .coerceIn(MIN_WOBBLE_MULTIPLIER, MAX_WOBBLE_MULTIPLIER),
            scanMultiplier = scanMultiplier,
            dimBase = dimBase,
            spreadMultiplier = spreadMultiplier,
            tintMode = tintMode,
            customTintHex = customTintHex,
            borderTintHex = borderTintHex
        )
    }

    /**
     * Opacity for a dot at normalised depth [depth01] (0..1) under the chosen
     * curve, scaled by [opacityStrength]. Curves: linear = identity,
     * sqrt lifts mid-depths, exp is the normalised `1 - exp(-3d)`.
     */
    fun opacityFor(depth01: Float): Float {
        val d = depth01.coerceIn(0f, 1f)
        val curved = when (opacityCurve) {
            OpacityCurve.Linear -> d
            OpacityCurve.Sqrt -> sqrt(d)
            OpacityCurve.Exp -> (1f - exp(-3f * d)) / (1f - exp(-3f))
        }
        return (curved * opacityStrength).coerceIn(0f, 1f)
    }

    /** Border colour for the customize screen's hairline card borders. */
    fun borderTint(): Color = parseHexOr(borderTintHex, Color(0xFF38383A))

    /** Tint to hand to `AgentLogoMark(tint = ...)`. Brand resolves to the accent. */
    fun resolvedTint(agentId: String): Color = when (tintMode) {
        TintMode.Brand -> emblemByKey(agentId.lowercase()).accent
        TintMode.MonoWhite -> Color.White
        TintMode.MonoBlack -> Color.Black
        TintMode.Custom -> parseHexOr(customTintHex, Color.White)
    }

    /**
     * Bake-time depth for [AgentLogoBaker]: the baker only takes a depth scalar,
     * so the shell style scales it (flat reads flatter, slab reads taller).
     */
    fun bakeDepth(): Float = (depth * when (shell) {
        ShellStyle.Flat -> 0.25f
        ShellStyle.Dome -> 1f
        ShellStyle.Slab -> 1.35f
    }).coerceIn(MIN_DEPTH, MAX_DEPTH)

    companion object {
        // Defaults: AgentLogoCloud.kt (DEFAULT_COUNT/DEPTH/SEED) + README bake table.
        const val DEFAULT_COUNT: Int = 220
        const val DEFAULT_DOT_RADIUS: Float = 1f
        const val DEFAULT_OPACITY_STRENGTH: Float = 1f
        const val DEFAULT_DEPTH: Float = 0.34f
        const val DEFAULT_MARGIN: Float = 0.06f
        const val DEFAULT_THRESHOLD: Float = 0.5f
        const val DEFAULT_RESOLUTION: Int = 256
        const val DEFAULT_SEED: Int = 1
        const val DEFAULT_DWELL_SECONDS: Float = 5.5f
        const val DEFAULT_MORPH_SECONDS: Float = 1.9f
        const val DEFAULT_SPEED_MULTIPLIER: Float = 1f
        const val DEFAULT_SPIN_MULTIPLIER: Float = 1f
        const val DEFAULT_BAND_MULTIPLIER: Float = 1f
        const val DEFAULT_WOBBLE_MULTIPLIER: Float = 1f
        const val DEFAULT_SCAN_MULTIPLIER: Float = 1f
        const val DEFAULT_DIM_BASE: Float = 0.4f
        const val DEFAULT_SPREAD_MULTIPLIER: Float = 1f
        const val DEFAULT_CUSTOM_TINT_HEX: String = "#FFFFFF"
        const val DEFAULT_BORDER_TINT_HEX: String = "#38383A"

        // Ranges (documented in the brief / README / presets.ts).
        const val MIN_COUNT: Int = 40
        const val MAX_COUNT: Int = 600
        const val MIN_DOT_RADIUS: Float = 0.5f
        const val MAX_DOT_RADIUS: Float = 2.5f
        const val MIN_OPACITY_STRENGTH: Float = 0f
        const val MAX_OPACITY_STRENGTH: Float = 2f
        const val MIN_DEPTH: Float = 0f
        const val MAX_DEPTH: Float = 0.6f
        const val MIN_MARGIN: Float = 0f
        const val MAX_MARGIN: Float = 0.2f
        const val MIN_THRESHOLD: Float = 0.3f
        const val MAX_THRESHOLD: Float = 0.7f
        const val MIN_SEED: Int = 0
        const val MIN_DWELL_SECONDS: Float = 1f
        const val MAX_DWELL_SECONDS: Float = 12f
        const val MIN_MORPH_SECONDS: Float = 0.5f
        const val MAX_MORPH_SECONDS: Float = 5f
        const val MIN_SPEED_MULTIPLIER: Float = 0.25f
        const val MAX_SPEED_MULTIPLIER: Float = 4f
        const val MIN_SPIN_MULTIPLIER: Float = 0f
        const val MAX_SPIN_MULTIPLIER: Float = 4f
        const val MIN_BAND_MULTIPLIER: Float = 0f
        const val MAX_BAND_MULTIPLIER: Float = 5f
        const val MIN_WOBBLE_MULTIPLIER: Float = 0f
        const val MAX_WOBBLE_MULTIPLIER: Float = 3f
        const val MIN_SCAN_MULTIPLIER: Float = 0f
        const val MAX_SCAN_MULTIPLIER: Float = 5f
        const val MIN_DIM_BASE: Float = 0f
        const val MAX_DIM_BASE: Float = 1f
        const val MIN_SPREAD_MULTIPLIER: Float = 0.5f
        const val MAX_SPREAD_MULTIPLIER: Float = 2f
        val RESOLUTIONS: List<Int> = listOf(128, 256, 512)

        private const val PREFS_NAME = "talos_logo_customization"
        private const val KEY_PREFIX = "logo_customization_"

        /** Default (shipped) customization for [agentId]. */
        fun defaults(agentId: String): LogoCustomization {
            @Suppress("UNUSED_PARAMETER")
            val unused = agentId
            return LogoCustomization()
        }

        /** Load the persisted customization for [agentId], or defaults. */
        fun load(context: Context, agentId: String): LogoCustomization {
            val json = prefs(context).getString(keyFor(agentId), null) ?: return defaults(agentId)
            return decode(json, defaults(agentId))
        }

        /** Persist [value] for [agentId] (sanitised first). */
        fun save(context: Context, agentId: String, value: LogoCustomization) {
            prefs(context).edit().putString(keyFor(agentId), encode(value.sanitized())).apply()
        }

        /** Drop the persisted customization for [agentId] (back to defaults). */
        fun reset(context: Context, agentId: String) {
            prefs(context).edit().remove(keyFor(agentId)).apply()
        }

        /** Serialise to JSON. Unknown keys are tolerated on decode. */
        fun encode(value: LogoCustomization): String {
            val v = value.sanitized()
            val overrides = JSONObject()
            for ((stateName, o) in v.stateOverrides) {
                val obj = JSONObject()
                o.speedMultiplier?.let { obj.put("speed", it.toDouble()) }
                o.count?.let { obj.put("count", it) }
                o.dotRadius?.let { obj.put("dotRadius", it.toDouble()) }
                o.dwellSeconds?.let { obj.put("dwellSeconds", it.toDouble()) }
                o.morphSeconds?.let { obj.put("morphSeconds", it.toDouble()) }
                o.spinMultiplier?.let { obj.put("spin", it.toDouble()) }
                o.bandMultiplier?.let { obj.put("bandMul", it.toDouble()) }
                o.wobbleMultiplier?.let { obj.put("wobMul", it.toDouble()) }
                o.opacityStrength?.let { obj.put("opacityStrength", it.toDouble()) }
                overrides.put(stateName, obj)
            }
            return JSONObject()
                .put("count", v.count)
                .put("dotRadius", v.dotRadius.toDouble())
                .put("opacityCurve", v.opacityCurve.name)
                .put("opacityStrength", v.opacityStrength.toDouble())
                .put("shell", v.shell.name)
                .put("depth", v.depth.toDouble())
                .put("margin", v.margin.toDouble())
                .put("threshold", v.threshold.toDouble())
                .put("resolution", v.resolution)
                .put("seed", v.seed)
                .put("dwellSeconds", v.dwellSeconds.toDouble())
                .put("morphSeconds", v.morphSeconds.toDouble())
                .put("speedMultiplier", v.speedMultiplier.toDouble())
                .put("spinMultiplier", v.spinMultiplier.toDouble())
                .put("bandMul", v.bandMultiplier.toDouble())
                .put("wobMul", v.wobbleMultiplier.toDouble())
                .put("scanMul", v.scanMultiplier.toDouble())
                .put("dimBase", v.dimBase.toDouble())
                .put("spread", v.spreadMultiplier.toDouble())
                .put("tintMode", v.tintMode.name)
                .put("customTintHex", v.customTintHex)
                .put("borderTintHex", v.borderTintHex)
                .put("stateOverrides", overrides)
                .toString()
        }

        /**
         * Parse [json], falling back to [fallback] per-field. Unknown keys are
         * ignored (forward-compatible); missing/invalid values keep the fallback.
         */
        fun decode(json: String, fallback: LogoCustomization = defaults("")): LogoCustomization {
            val root = try {
                JSONObject(json)
            } catch (_: Exception) {
                return fallback
            }
            fun f(key: String, current: Float): Float =
                if (root.has(key)) root.optDouble(key, current.toDouble()).toFloat() else current
            fun i(key: String, current: Int): Int =
                if (root.has(key)) root.optInt(key, current) else current
            fun <T : Enum<T>> e(enumClass: Class<T>, key: String, current: T): T {
                val raw = root.optString(key, "")
                return try {
                    java.lang.Enum.valueOf(enumClass, raw)
                } catch (_: Exception) {
                    current
                }
            }
            val overrides = mutableMapOf<String, LogoStateOverride>()
            val rawOverrides = root.optJSONObject("stateOverrides")
            if (rawOverrides != null) {
                val keys = rawOverrides.keys()
                while (keys.hasNext()) {
                    val stateName = keys.next()
                    val obj = rawOverrides.optJSONObject(stateName) ?: continue
                    fun of(key: String): Float? =
                        if (obj.has(key)) obj.optDouble(key, Double.NaN).toFloat()
                            .takeIf { it.isFinite() } else null
                    fun oi(key: String): Int? =
                        if (obj.has(key)) obj.optInt(key, 0) else null
                    overrides[stateName] = LogoStateOverride(
                        speedMultiplier = of("speed"),
                        count = oi("count")?.takeIf { it > 0 },
                        dotRadius = of("dotRadius"),
                        dwellSeconds = of("dwellSeconds"),
                        morphSeconds = of("morphSeconds"),
                        spinMultiplier = of("spin"),
                        bandMultiplier = of("bandMul"),
                        wobbleMultiplier = of("wobMul"),
                        opacityStrength = of("opacityStrength")
                    )
                }
            }
            return fallback.copy(
                count = i("count", fallback.count),
                dotRadius = f("dotRadius", fallback.dotRadius),
                opacityCurve = e(OpacityCurve::class.java, "opacityCurve", fallback.opacityCurve),
                opacityStrength = f("opacityStrength", fallback.opacityStrength),
                shell = e(ShellStyle::class.java, "shell", fallback.shell),
                depth = f("depth", fallback.depth),
                margin = f("margin", fallback.margin),
                threshold = f("threshold", fallback.threshold),
                resolution = i("resolution", fallback.resolution),
                seed = i("seed", fallback.seed),
                dwellSeconds = f("dwellSeconds", fallback.dwellSeconds),
                morphSeconds = f("morphSeconds", fallback.morphSeconds),
                speedMultiplier = f("speedMultiplier", fallback.speedMultiplier),
                spinMultiplier = f("spinMultiplier", fallback.spinMultiplier),
                bandMultiplier = f("bandMul", fallback.bandMultiplier),
                wobbleMultiplier = f("wobMul", fallback.wobbleMultiplier),
                scanMultiplier = f("scanMul", fallback.scanMultiplier),
                dimBase = f("dimBase", fallback.dimBase),
                spreadMultiplier = f("spread", fallback.spreadMultiplier),
                tintMode = e(TintMode::class.java, "tintMode", fallback.tintMode),
                customTintHex = root.optString("customTintHex", fallback.customTintHex),
                borderTintHex = root.optString("borderTintHex", fallback.borderTintHex),
                stateOverrides = overrides
            ).sanitized()
        }

        fun isValidHex(hex: String): Boolean =
            hex.matches(Regex("^#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$"))

        fun parseHexOr(hex: String, fallback: Color): Color {
            if (!isValidHex(hex)) return fallback
            return try {
                val argb = android.graphics.Color.parseColor(hex)
                Color(argb)
            } catch (_: Exception) {
                fallback
            }
        }

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private fun keyFor(agentId: String): String =
            KEY_PREFIX + agentId.trim().lowercase()
                .replace(Regex("[^a-z0-9._-]"), "_")
                .take(128)
    }
}
