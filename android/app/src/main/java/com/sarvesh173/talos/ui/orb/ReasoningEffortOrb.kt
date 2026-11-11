package com.sarvesh173.talos.ui.orb

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sarvesh173.talos.ui.avatar.Family
import com.sarvesh173.talos.ui.applePressable
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Reasoning effort -> orb escalation.
 *
 * ## Two axes, deliberately separate
 *
 * [orbIntensity] (AdaptiveOrbSpeed.kt) maps measured token throughput to an
 * intensity in `[0.55, 2.6]`. This file maps *reasoning effort* to a second
 * multiplier applied *after* it:
 *
 *     effectiveIntensity = orbIntensity(tps) * bundle.intensityMultiplier
 *
 * They must stay separate because effort and throughput are independent:
 * an ultra-reasoning run can be slow (deep chain of thought, few tokens
 * per second) and a low-effort run can be fast (short answer, high rate).
 * Folding effort into the throughput calibration would make a slow ultra
 * run look calm and a fast low run look frantic — the opposite of what the
 * user selected. The throughput curve stays untouched; this curve only
 * scales its output. The product is intentionally not re-clamped to
 * `[0.55, 2.6]`: that range belongs to throughput alone. Maximum product
 * is `2.6 * 1.80 = 4.68`, still bounded and strobe-free because both
 * factors are bounded and slew-limited.
 *
 * ## Curve shape: sub-linear, diminishing returns
 *
 * Each step up a tier buys less than the previous step. Tier values below
 * use decreasing increments (see the table in [ReasoningEffortCurve]), a
 * hand-tuned concave schedule in the spirit of `sqrt(tierIndex)`: going
 * low -> medium is clearly visible, ultra only edges past max. Ultra is
 * not 6x low — the intensity ratio ultra/low is 1.5, capped at 2.0
 * ([ReasoningEffortCurve.ULTRA_TO_LOW_INTENSITY_CAP]). A linear schedule
 * would make the top tiers scream and the bottom tiers indistinguishable,
 * and would invite the strobing the throughput curve was built to avoid.
 *
 * ## Off is disabled, not frozen
 *
 * [ReasoningEffortTier.Off] has a neutral intensity multiplier (1.0), the
 * baseline dot count (0.5x of 220), no aura and no RGB split. The orb
 * keeps rendering at the raw throughput intensity — it is never left
 * frozen at a previous tier's high values.
 *
 * ## Apple HIG note
 *
 * The orb is monochrome (white ink on dark) with a single accent. The
 * RGB split at max/ultra is a deliberate, small exception to that rule:
 * a 1.2–2.4dp chromatic ghost, drawn only at the two highest tiers, so
 * the top of the scale reads as strained rather than re-themed. No
 * gradients, no shadow layers, no blur; path building only. Spacing and
 * offsets sit on a 4dp grid. Labels are sentence case.
 *
 * ## Authorship note
 *
 * The *concept* — an orb whose agitation rises with reasoning effort — is
 * a common pattern. Every number here is TALOS-original: multipliers are
 * chosen against TALOS's own measured speeds (the [orbIntensity] anchors
 * at 5, 250, 2,600, 17,000 tok/s) and the documented caps, not copied
 * from any reference app.
 */

/** Six effort tiers. TALOS's picker exposes Off..Max; Ultra is the top step. */
enum class ReasoningEffortTier {
    Off, Low, Medium, High, Max, Ultra;

    /** Map the existing `ui.agent.ReasoningEffort` picker value onto a tier. */
    fun fromExistingName(name: String): ReasoningEffortTier = when (name.lowercase()) {
        "off" -> Off
        "low" -> Low
        "medium" -> Medium
        "high" -> High
        "max" -> Max
        "ultra" -> Ultra
        else -> Medium
    }
}

/**
 * One tier's worth of escalation. All float fields are multipliers or
 * fractions in documented units; [auraParticleDensity] is a particle count.
 */
data class EffortBundle(
    /** Applied after [orbIntensity]: `effective = throughput * this`. */
    val intensityMultiplier: Float,
    /** Times [ReasoningEffortCurve.BASE_DOT_COUNT] gives the drawn dot count. */
    val pointCountMultiplier: Float,
    /** Dot radius relative to the base radius. */
    val dotRadiusScale: Float,
    /** Chromatic ghost offset in dp. Zero below max — see file note. */
    val rgbSplitDp: Float,
    /** Rim arc alpha, 0..1. */
    val rimLightStrength: Float,
    /** Aura particle count. Zero when off. */
    val auraParticleDensity: Int,
    /** Expression family bias, calm -> celebrating. Discrete: snaps at midpoint. */
    val expressionFamily: Family,
    /** Dwell / hold time in ms before the next escalation is shown. */
    val dwellMillis: Int,
) {
    init {
        require(intensityMultiplier > 0f && intensityMultiplier.isFinite()) { "intensityMultiplier must be positive finite" }
        require(pointCountMultiplier > 0f && pointCountMultiplier.isFinite()) { "pointCountMultiplier must be positive finite" }
        require(dotRadiusScale > 0f && dotRadiusScale.isFinite()) { "dotRadiusScale must be positive finite" }
        require(rgbSplitDp >= 0f && rgbSplitDp.isFinite()) { "rgbSplitDp must be >= 0" }
        require(rimLightStrength in 0f..1f) { "rimLightStrength must be in 0..1" }
        require(auraParticleDensity >= 0) { "auraParticleDensity must be >= 0" }
        require(dwellMillis >= 0) { "dwellMillis must be >= 0" }
    }
}

/**
 * Totally ordered tier table. Increments shrink as tiers rise (sub-linear):
 * intensity steps are 0.20, 0.20, 0.15, 0.13, 0.12.
 */
object ReasoningEffortCurve {

    /** Base dot count the multipliers are relative to (220). */
    const val BASE_DOT_COUNT = 220

    /** Documented cap: ultra intensity multiplier / low intensity multiplier. */
    const val ULTRA_TO_LOW_INTENSITY_CAP = 2.0f

    /** Worst-case frames to converge from any tier to any tier at 60 fps, scale 1. */
    const val MAX_CONVERGENCE_FRAMES_60FPS = 60

    /** Per-parameter slew rates (units per second). */
    const val INTENSITY_RATE_PER_SECOND = 2.0f
    const val POINT_RATE_PER_SECOND = 3.0f
    const val RADIUS_RATE_PER_SECOND = 1.5f
    const val SPLIT_RATE_PER_SECOND = 4.0f
    const val RIM_RATE_PER_SECOND = 2.0f
    const val AURA_RATE_PER_SECOND = 120f
    const val DWELL_RATE_PER_SECOND = 2000f

    /** Tier table, in ascending effort order. */
    val Tiers: List<Pair<ReasoningEffortTier, EffortBundle>> = listOf(
        ReasoningEffortTier.Off to EffortBundle(1.00f, 0.50f, 0.80f, 0.0f, 0.15f, 0, Family.Calm, 0),
        ReasoningEffortTier.Low to EffortBundle(1.20f, 0.75f, 0.90f, 0.0f, 0.30f, 12, Family.Calm, 300),
        ReasoningEffortTier.Medium to EffortBundle(1.40f, 1.00f, 1.00f, 0.0f, 0.45f, 28, Family.Focused, 500),
        ReasoningEffortTier.High to EffortBundle(1.55f, 1.35f, 1.10f, 0.0f, 0.60f, 48, Family.Curious, 700),
        ReasoningEffortTier.Max to EffortBundle(1.68f, 1.80f, 1.20f, 1.2f, 0.80f, 72, Family.Alert, 900),
        ReasoningEffortTier.Ultra to EffortBundle(1.80f, 2.20f, 1.30f, 2.4f, 1.00f, 100, Family.Celebrating, 1200),
    )

    fun forTier(tier: ReasoningEffortTier): EffortBundle =
        Tiers.first { it.first == tier }.second

    fun tierIndex(tier: ReasoningEffortTier): Int = Tiers.indexOfFirst { it.first == tier }

    /** Drawn dot count for a tier at the default base (220). */
    fun scaledDotCount(tier: ReasoningEffortTier, base: Int = BASE_DOT_COUNT): Int =
        (forTier(tier).pointCountMultiplier * base).roundToInt()

    fun scaledDotCount(bundle: EffortBundle, base: Int = BASE_DOT_COUNT): Int =
        (bundle.pointCountMultiplier * base).roundToInt()

    /** Expression family bias for a tier, calm -> celebrating. */
    fun expressionFamilyFor(tier: ReasoningEffortTier): Family = forTier(tier).expressionFamily

    /** True at max/ultra only, where the RGB ghost is drawn. */
    fun hasRgbSplit(tier: ReasoningEffortTier): Boolean = forTier(tier).rgbSplitDp > 0f
}

/**
 * Compose effort after throughput. Pure and total: a non-finite or
 * non-positive throughput intensity falls back to the throughput minimum
 * (0.55) so a quiet agent still reads as alive, never frozen at a bound.
 */
fun composeEffortIntensity(throughputIntensity: Float, tier: ReasoningEffortTier): Float {
    val safe = if (throughputIntensity.isFinite() && throughputIntensity > 0f) {
        throughputIntensity
    } else {
        OrbSpeedCalibration.DEFAULT_MIN_INTENSITY
    }
    return safe * ReasoningEffortCurve.forTier(tier).intensityMultiplier
}

/** Effective dot radius in px for a given base radius and tier. */
fun scaledDotRadius(baseRadiusPx: Float, tier: ReasoningEffortTier): Float =
    baseRadiusPx * ReasoningEffortCurve.forTier(tier).dotRadiusScale

/**
 * Glide [current] toward [target] by at most the per-parameter rate
 * * [dtSeconds] / [animatorDurationScale]. Pure and total.
 *
 * - Never overshoots: every scalar moves toward the target by a clamped
 *   step, so it lands exactly on the target and stops.
 * - `animatorDurationScale == 0` jumps straight to [target] (the system
 *   "reduce motion / no animations" signal).
 * - Discrete fields ([EffortBundle.expressionFamily]) snap at the halfway
 *   point of the glide instead of blending, which would be meaningless.
 *
 * Convergence bound: the widest scalar range is aura density
 * (0 -> 100 at 120/s = 0.84 s) and intensity (1.00 -> 1.80 at 2.0/s =
 * 0.40 s), so at 60 fps any tier reaches any tier within
 * [ReasoningEffortCurve.MAX_CONVERGENCE_FRAMES_60FPS] (60) frames at
 * animator scale 1. Higher system scales stretch this proportionally,
 * which is the intended, calm behaviour.
 */
fun lerpTowards(
    current: EffortBundle,
    target: EffortBundle,
    dtSeconds: Float,
    animatorDurationScale: Float = 1f,
): EffortBundle {
    if (animatorDurationScale <= 0f) return target
    val dt = if (dtSeconds.isFinite() && dtSeconds > 0f) dtSeconds else 0f
    val stepScale = dt / animatorDurationScale
    fun move(value: Float, goal: Float, rate: Float): Float {
        val delta = goal - value
        val maxStep = rate * stepScale
        return if (delta in -maxStep..maxStep) goal else value + delta.coerceIn(-maxStep, maxStep)
    }
    fun moveInt(value: Int, goal: Int, rate: Float): Int {
        val maxStep = (rate * stepScale).roundToInt()
        val delta = goal - value
        return if (maxStep <= 0) value else if (delta in -maxStep..maxStep) goal else value + delta.coerceIn(-maxStep, maxStep)
    }
    // Discrete family: hold the old family for the first half, then snap.
    val progress = if (target.intensityMultiplier != current.intensityMultiplier) {
        ((move(current.intensityMultiplier, target.intensityMultiplier, ReasoningEffortCurve.INTENSITY_RATE_PER_SECOND) - current.intensityMultiplier) /
            (target.intensityMultiplier - current.intensityMultiplier)).coerceIn(0f, 1f)
    } else {
        1f
    }
    return EffortBundle(
        intensityMultiplier = move(current.intensityMultiplier, target.intensityMultiplier, ReasoningEffortCurve.INTENSITY_RATE_PER_SECOND),
        pointCountMultiplier = move(current.pointCountMultiplier, target.pointCountMultiplier, ReasoningEffortCurve.POINT_RATE_PER_SECOND),
        dotRadiusScale = move(current.dotRadiusScale, target.dotRadiusScale, ReasoningEffortCurve.RADIUS_RATE_PER_SECOND),
        rgbSplitDp = move(current.rgbSplitDp, target.rgbSplitDp, ReasoningEffortCurve.SPLIT_RATE_PER_SECOND),
        rimLightStrength = move(current.rimLightStrength, target.rimLightStrength, ReasoningEffortCurve.RIM_RATE_PER_SECOND),
        auraParticleDensity = moveInt(current.auraParticleDensity, target.auraParticleDensity, ReasoningEffortCurve.AURA_RATE_PER_SECOND),
        expressionFamily = if (progress >= 0.5f) target.expressionFamily else current.expressionFamily,
        dwellMillis = moveInt(current.dwellMillis, target.dwellMillis, ReasoningEffortCurve.DWELL_RATE_PER_SECOND),
    )
}

/** True when every scalar in [current] equals [target] exactly. */
fun isConverged(current: EffortBundle, target: EffortBundle): Boolean =
    current.intensityMultiplier == target.intensityMultiplier &&
        current.pointCountMultiplier == target.pointCountMultiplier &&
        current.dotRadiusScale == target.dotRadiusScale &&
        current.rgbSplitDp == target.rgbSplitDp &&
        current.rimLightStrength == target.rimLightStrength &&
        current.auraParticleDensity == target.auraParticleDensity &&
        current.dwellMillis == target.dwellMillis

/** System animator duration scale; 0 means jump (no animation). */
fun systemAnimatorDurationScale(context: Context): Float = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
}.getOrDefault(1f)

/**
 * Animates the effort bundle toward [target] on a frame-ish ticker
 * (16 ms), honouring the system animator duration scale. Starts at Off
 * so first composition never flashes a high tier.
 */
@Composable
fun rememberAnimatedReasoningEffort(
    target: ReasoningEffortTier,
    animatorDurationScale: Float = systemAnimatorDurationScale(LocalContext.current),
): EffortBundle {
    var current by remember {
        mutableStateOf(ReasoningEffortCurve.forTier(ReasoningEffortTier.Off))
    }
    LaunchedEffect(target, animatorDurationScale) {
        val goal = ReasoningEffortCurve.forTier(target)
        while (true) {
            val next = lerpTowards(current, goal, 0.016f, animatorDurationScale)
            current = next
            if (isConverged(next, goal)) break
            delay(16L)
        }
    }
    return current
}

/**
 * Renders the escalation: dot count scaled by effort, an aura ring whose
 * thickness scales with effort, a rim arc, and an RGB-split ghost at
 * max/ultra only. Cheap by construction: dots and arcs only, no shadow
 * layers, no blur, no gradients. [intensity] is already composed
 * (throughput * effort) — use [composeEffortIntensity] upstream.
 */
@Composable
fun ReasoningOrb(
    agentId: String,
    effort: ReasoningEffortTier,
    intensity: Float,
    orbSize: Dp,
    modifier: Modifier = Modifier,
    animatorDurationScale: Float = systemAnimatorDurationScale(LocalContext.current),
    accent: Color = Color(0xFF0A84FF),
    /**
     * When false the dot layer is skipped. A caller that already draws the
     * agent's own mark (AgentLogoMark) should pass false and read only the
     * escalation signal (aura, rim, RGB split) from this composable.
     */
    renderDots: Boolean = true,
) {
    val animated = rememberAnimatedReasoningEffort(effort, animatorDurationScale)
    val density = LocalDensity.current
    val orbPx = with(density) { orbSize.toPx() }
    val splitPx = with(density) { animated.rgbSplitDp.dp.toPx() }
    val dotCount = ReasoningEffortCurve.scaledDotCount(animated)
    val seed = agentId.hashCode().toFloat()

    Canvas(modifier = modifier) {
        val canvasSize = size
        val radius = min(canvasSize.width, canvasSize.height) / 2f
        val centre = center
        val safeIntensity = if (intensity.isFinite() && intensity > 0f) intensity else 0.55f
        val baseDot = (radius * 0.055f * animated.dotRadiusScale).coerceAtLeast(1f)

        // Aura ring: thickness scales with effort (auraParticleDensity).
        val auraWidth = (radius * 0.06f * (0.25f + safeIntensity * 0.35f)).coerceAtLeast(1f)
        if (animated.auraParticleDensity > 0) {
            val auraPad = auraWidth * 2f
            drawArc(
                color = accent.copy(alpha = 0.55f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(centre.x - radius + auraPad, centre.y - radius + auraPad),
                size = Size((radius - auraPad) * 2f, (radius - auraPad) * 2f),
                style = Stroke(width = auraWidth),
            )
        }

        // Fibonacci lattice dots, deterministic per agent. Skipped when the
        // caller already draws the agent's own mark, which is how the orb
        // becomes an escalation signal on top of an identity instead of a
        // second identity competing with it.
        if (renderDots && dotCount > 0) {
            for (i in 0 until dotCount) {
                val t = if (dotCount == 1) 0.5f else i.toFloat() / (dotCount - 1).toFloat()
                val y = 1f - t * 2f
                val rRing = kotlin.math.sqrt((1f - y * y).coerceAtLeast(0f).toDouble()).toFloat()
                val theta = GOLDEN * i.toFloat() + seed * 0.01f + safeIntensity * 0.05f
                val x = kotlin.math.cos(theta.toDouble()).toFloat() * rRing
                val z = kotlin.math.sin(theta.toDouble()).toFloat() * rRing
                val depth = 0.5f + 0.5f * z
                val px = centre.x + x * radius * 0.92f
                val py = centre.y + y * radius * 0.92f
                val ink = 0.35f + 0.65f * depth
                drawCircle(
                    color = Color.White.copy(alpha = (0.25f + 0.75f * ink).coerceIn(0f, 1f)),
                    radius = (baseDot * (0.4f + depth)).coerceAtLeast(0.5f),
                    center = Offset(px, py),
                )
            }
        }

        // Rim light: upper arc, alpha = rimLightStrength.
        if (animated.rimLightStrength > 0f) {
            drawArc(
                color = Color.White.copy(alpha = animated.rimLightStrength * 0.8f),
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(centre.x - radius, centre.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = (radius * 0.03f).coerceAtLeast(1f)),
            )
        }

        // RGB split ghost: max/ultra only, small deliberate exception.
        if (splitPx > 0f) {
            val ghostR = radius * 0.55f * animated.dotRadiusScale
            drawCircle(
                color = Color.Red.copy(alpha = 0.30f),
                radius = ghostR,
                center = Offset(centre.x - splitPx, centre.y),
            )
            drawCircle(
                color = Color.Cyan.copy(alpha = 0.30f),
                radius = ghostR,
                center = Offset(centre.x + splitPx, centre.y),
            )
        }
    }
}

/**
 * Reasoning-effort picker for settings and delegate cards.
 *
 * Seven stepped stops: the user drags, and each stop both carries a label and
 * moves the orb. The thumb scale on release is the same accentused "agitation"
 * effect the throughput orb uses — the visual is deliberately louder than
 * the list it sits beside, because the whole point of a 7-tier effort control
 * is that each step must be *visible*, not just stored.
 *
 * Pure UI: the caller owns the selected tier and the wire encoding.
 */
@Composable
fun ReasoningEffortPicker(
    selected: ReasoningEffortTier,
    onSelect: (ReasoningEffortTier) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tiers = ReasoningEffortCurve.Tiers.map { it.first }
    val selectedIndex = tiers.indexOf(selected).coerceAtLeast(0)
    val density = LocalDensity.current
    val animatorScale = systemAnimatorDurationScale(LocalContext.current)

    // Visual agitation on the orb: each settled tier contributes a bounded
    // vibration measured from the tier index, so the selected stop reads
    // as an escalation even before the ReasoningOrb re-tracks it.
    var agitation by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(selectedIndex) {
        if (animatorScale > 0f) {
            val base = selectedIndex.toFloat() / (tiers.size - 1).coerceAtLeast(1)
            // Fast attack, slow decay — the orb should jump on a change and
            // settle over about two seconds, mirroring the throughput orb's
            // rise/fall asymmetry.
            agitation = 0.35f + base * 0.65f
            while (agitation > 0.01f) {
                agitation = (agitation * 0.92f).coerceAtLeast(0f)
                delay(16L)
            }
            agitation = 0f
        } else {
            agitation = 0f
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ReasoningOrb(
            agentId = "effort-picker",
            effort = selected,
            intensity = OrbSpeedCalibration.DEFAULT_MIN_INTENSITY * (0.55f + agitation * 1.1f),
            orbSize = 56.dp,
            renderDots = true,
        )
        Text(
            text = selected.name,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
        )
        // Stepped track. A continuous Slider would imply intermediate values
        // that do not exist; steps are honest about the seven real stops.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tiers.forEachIndexed { index, tier ->
                val isSelected = index == selectedIndex
                val dotSize = if (isSelected) 12.dp else 8.dp
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) {
                                Color(0xFF0A84FF)
                            } else {
                                Color.White.copy(alpha = 0.25f)
                            }
                        )
                        .applePressable(0.92f, pressHaptic = true) {
                            if (enabled) onSelect(tier)
                        },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Min",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.6f),
            )
            Text(
                text = "Ultra",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
    }
}
