package com.sarvesh173.talos.ui.orb

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sarvesh173.talos.ui.avatar.BlobAnimationLibrary
import com.sarvesh173.talos.ui.avatar.archetypeOmega
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Fur orb — a deterministic strand field for the thinking orb.
 *
 * TALOS-original 2D canvas interpretation of a fur shell: N hair strands
 * radiate from the bot-mark core as cubic strokes. No 3D mesh, no
 * RuntimeShader, no copied shader. Every number derives from TALOS's own
 * measured throughput curve ([orbIntensity] in AdaptiveOrbSpeed.kt) and
 * the existing reasoning-effort tiers (ReasoningEffortCurve).
 *
 * ## Intensity anchors (read from AdaptiveOrbSpeed.kt, not invented)
 *
 * - floor intensity 0.55 ([OrbSpeedCalibration.DEFAULT_MIN_INTENSITY])
 * - maximum intensity 2.6 ([OrbSpeedCalibration.DEFAULT_MAX_INTENSITY])
 * - knee fraction 0.92 ([OrbSpeedCalibration.DEFAULT_KNEE_FRACTION]),
 *   knee intensity 2.436 at the 20,000 tok/s ceiling
 * - max supported throughput 1,000,000 tok/s
 *   ([OrbSpeedCalibration.MAX_SUPPORTED_TPS])
 *
 * Hair length and strand count saturate at the intensity produced by
 * 1,000,000 tok/s ([INTENSITY_AT_MAX_SUPPORTED_TPS], about 2.50 under the
 * default calibration), so at 1M tok/s the hair is exactly at its
 * documented maximum, and any intensity above that (up to and past the
 * 2.6 asymptote) is clamped to the same maximum and never exceeds it.
 *
 * ## Strand-count formula (pure, testable)
 *
 *     normalised = clamp((intensity - 0.55) / (I_sat - 0.55), 0, 1)
 *     countForIntensity(intensity) = round(48 + (192 - 48) * normalised)
 *
 * where I_sat = [INTENSITY_AT_MAX_SUPPORTED_TPS]. Floor (0.55) gives 48
 * strands, the 1M saturation point and the 2.6 maximum both give the
 * capped ceiling of 192 strands. The function is monotonic
 * non-decreasing in intensity and capped at [MAX_STRAND_COUNT].
 *
 * ## Hair-length formula
 *
 *     hairLengthFraction(intensity) =
 *         0.18 + (0.42 - 0.18) * normalised(intensity)
 *
 * as a fraction of the orb radius (half the smaller canvas side).
 * Floor gives 0.18, the 1M saturation point and the 2.6 maximum both
 * give the documented maximum 0.42, never more.
 *
 * ## Flow mapping (reasoning effort -> comb direction)
 *
 * `flow` is a single exposed parameter in 0..1, clamped. It is derived
 * from the effort tier by [ReasoningEffortFur.flowForTier]:
 * flow = tierIndex / 5, so Off = 0.0, Low = 0.2, Medium = 0.4,
 * High = 0.6, Max = 0.8, Ultra = 1.0.
 *
 * - sweep angle = flow * 60 degrees ([MAX_SWEEP_RADIANS], 1.0472 rad):
 *   at flow 0 the hair combs straight back along the radial direction
 *   (calm); at flow 1 it sweeps 60 degrees sideways.
 * - curvature = flow * 0.85 ([MAX_CURVATURE]): at flow 0 strands are
 *   straight; at flow 1 they curl sideways and outward (agitation).
 * - per-strand sway speed reuses the BlobMath catalogue
 *   ([BlobAnimationLibrary] presets + [archetypeOmega]) instead of a
 *   second motion system: strand i sways at the archetype omega of
 *   preset i.
 *
 * ## Agitation slew (never snaps)
 *
 * [advanceAgitation] moves a normalised agitation value (0..1) toward
 * its target at at most [AGITATION_RISE_PER_SECOND] (1.6/s) rising and
 * [AGITATION_FALL_PER_SECOND] (0.55/s) falling. Those fractions mirror
 * the RISE_PER_SECOND / FALL_PER_SECOND slew fractions in
 * AdaptiveOrbSpeed.kt (rise quicker than fall, so a surge reads as
 * responsiveness and decay reads as settling). Rising is deliberately
 * allowed to be faster than falling; the fur therefore never snaps.
 *
 * ## ReasoningEffortFur — RGB-split threshold
 *
 * The RGB-split ghost is OFF below [ReasoningEffortFur.RGB_SPLIT_THRESHOLD_TIER]
 * (Max) and ON at Max and Ultra only. The threshold is Max because
 * [ReasoningEffortCurve] already calibrates the split there (1.2dp at
 * Max, 2.4dp at Ultra, 0dp below): below Max a chromatic ghost would
 * break the monochrome instrument look and read as an error, while at
 * Max/Ultra the effort is genuinely strained, so a small red/blue
 * offset signals overload rather than a new theme. Above the threshold
 * the aura also thins ([ReasoningEffortFur.auraThinFactor]: 1.0 below,
 * about 0.72 at Max, 0.45 at Ultra) so the field looks stretched
 * instead of merely brighter.
 *
 * ## Frame budget (explicit and enforced)
 *
 * - At most [MAX_STRAND_COUNT] (192) strands, each one cubic path:
 *   192 drawPath calls in the normal case, 576 when the RGB ghost is
 *   on (3 passes over the same geometry, red/blue at low alpha).
 * - Single pass over the strand index range; one reusable [Path]
 *   object per draw, reset per strand — no per-frame list or array
 *   allocation in the draw loop.
 * - The composable draws nothing when [shouldDrawFur] is false, i.e.
 *   when the orb is off-screen (`visible` = false) or the system
 *   animator duration scale is 0. The animation clock also stops in
 *   those cases.
 * - 192 is below the agent point-cloud bake size
 *   (AgentLogoCloud DEFAULT_COUNT = 220), so the fur never costs
 *   more strokes than the mark it decorates.
 *
 * ## Apple HIG / presentation
 *
 * Monochrome fur by default (theme ink at varying alpha), one accent
 * colour for the single active strand tip only, a single rim-light
 * arc as the only non-flat treatment (no gradients, no blur, no
 * shadow), and a 4dp grid inset for the core. The renderer reads as
 * an instrument, not a toy. Seeds are deterministic per agent, so
 * the same agent looks the same across restarts.
 */

/** Strand count at the intensity floor. */
const val MIN_STRAND_COUNT = 48

/** Strand count ceiling, reached at the 1M tok/s saturation point. */
const val MAX_STRAND_COUNT = 192

/** Hair length at the floor, as a fraction of the orb radius. */
const val MIN_HAIR_LENGTH_FRACTION = 0.18f

/** Documented maximum hair length, as a fraction of the orb radius. */
const val MAX_HAIR_LENGTH_FRACTION = 0.42f

/** Agitation rise slew, fraction of full range per second. */
const val AGITATION_RISE_PER_SECOND = 1.6f

/** Agitation fall slew, fraction of full range per second. */
const val AGITATION_FALL_PER_SECOND = 0.55f

/** Sideways sweep at flow = 1 (60 degrees). */
const val MAX_SWEEP_RADIANS = 1.0472f

/** Curl amount at flow = 1 (0 = straight). */
const val MAX_CURVATURE = 0.85f

/**
 * Intensity produced at the maximum supported throughput under the
 * default calibration. Hair length and strand count saturate here, so
 * 1M tok/s is exactly the documented maximum and nothing exceeds it.
 */
val INTENSITY_AT_MAX_SUPPORTED_TPS: Float =
    orbIntensity(OrbSpeedCalibration.MAX_SUPPORTED_TPS, OrbSpeedCalibration.Default)

/** Normalised intensity in 0..1; non-finite input is the idle floor. */
fun normalizedIntensity(intensity: Float): Float {
    if (!intensity.isFinite()) return 0f
    val span = INTENSITY_AT_MAX_SUPPORTED_TPS - OrbSpeedCalibration.DEFAULT_MIN_INTENSITY
    if (span <= 0f) return 0f
    return ((intensity - OrbSpeedCalibration.DEFAULT_MIN_INTENSITY) / span).coerceIn(0f, 1f)
}

/**
 * Pure strand-count function: 48 at the floor, 192 at and above the
 * 1M saturation point (including the 2.6 maximum), monotonic and
 * capped at [MAX_STRAND_COUNT].
 */
fun countForIntensity(intensity: Float): Int {
    val normalised = normalizedIntensity(intensity)
    return (MIN_STRAND_COUNT + (MAX_STRAND_COUNT - MIN_STRAND_COUNT) * normalised)
        .roundToInt()
        .coerceIn(MIN_STRAND_COUNT, MAX_STRAND_COUNT)
}

/**
 * Pure hair-length function, as a fraction of the orb radius:
 * 0.18 at the floor, [MAX_HAIR_LENGTH_FRACTION] at the 1M saturation
 * point and at the 2.6 maximum, never more.
 */
fun hairLengthFraction(intensity: Float): Float {
    val normalised = normalizedIntensity(intensity)
    return (MIN_HAIR_LENGTH_FRACTION +
        (MAX_HAIR_LENGTH_FRACTION - MIN_HAIR_LENGTH_FRACTION) * normalised)
        .coerceIn(MIN_HAIR_LENGTH_FRACTION, MAX_HAIR_LENGTH_FRACTION)
}

/** Hair length in pixels for a given orb radius in pixels. */
fun hairLengthPx(intensity: Float, orbRadiusPx: Float): Float =
    hairLengthFraction(intensity) * orbRadiusPx

/** Flow for an effort tier: Off 0.0 .. Ultra 1.0 in equal steps. */
fun flowForTier(tier: ReasoningEffortTier): Float =
    ReasoningEffortCurve.tierIndex(tier).toFloat() / 5f

/** Sideways sweep in radians for a flow value in 0..1. */
fun sweepRadiansForFlow(flow: Float): Float =
    flow.coerceIn(0f, 1f) * MAX_SWEEP_RADIANS

/** Curl amount for a flow value in 0..1. */
fun curvatureForFlow(flow: Float): Float =
    flow.coerceIn(0f, 1f) * MAX_CURVATURE

/**
 * Move normalised agitation toward its target with slew limits:
 * at most [AGITATION_RISE_PER_SECOND]/s rising and
 * [AGITATION_FALL_PER_SECOND]/s falling. Pure and total.
 */
fun advanceAgitation(current: Float, target: Float, dtSeconds: Float): Float {
    val cur = if (current.isFinite()) current.coerceIn(0f, 1f) else 0f
    val tgt = if (target.isFinite()) target.coerceIn(0f, 1f) else 0f
    if (!dtSeconds.isFinite() || dtSeconds <= 0f) return cur
    val limit = if (tgt > cur) AGITATION_RISE_PER_SECOND else AGITATION_FALL_PER_SECOND
    val maxStep = limit * dtSeconds
    val step = (tgt - cur).coerceIn(-maxStep, maxStep)
    return (cur + step).coerceIn(0f, 1f)
}

/**
 * Draw gate: draw only when visible, not paused, and the system
 * animator duration scale is above 0. Mirrors the isActive gate used
 * by the logo cloud.
 */
fun shouldDrawFur(
    visible: Boolean,
    animatorDurationScale: Float,
    paused: Boolean = false,
): Boolean = visible && !paused && animatorDurationScale.isFinite() && animatorDurationScale > 0f

/** Deterministic per-agent seed (String.hashCode is stable across runs). */
fun strandSeed(agentId: String): Int = agentId.hashCode()

/**
 * Deterministic per-strand jitter in 0..1 from (index, seed).
 * Uses the shared [hashD] hash, so no Random and no restart drift.
 */
fun strandJitter(index: Int, seed: Int): Float = hashD(index.toFloat(), seed.toFloat())

/** Golden-angle spiral angle for a strand, in radians. */
fun strandAngleRadians(index: Int, seed: Int): Float =
    (index.toDouble() * GOLDEN + seed.toDouble() * 0.01).toFloat()

/** The single active strand (accent tip) at a given time. */
fun activeStrandIndex(count: Int, timeSeconds: Float): Int {
    if (count <= 0) return -1
    val t = if (timeSeconds.isFinite()) timeSeconds.coerceAtLeast(0f) else 0f
    return floor((t * 1.5).toDouble()).toInt().mod(count)
}

/** One strand's pure geometry inputs. */
data class FurStrand(
    val index: Int,
    val angleRadians: Float,
    val jitter: Float,
    val lengthFraction: Float,
    val sweepRadians: Float,
    val curvature: Float,
)

/** Pure strand geometry for one index: deterministic per (agent, count). */
fun strandFor(index: Int, agentId: String, intensity: Float, flow: Float): FurStrand {
    val seed = strandSeed(agentId)
    return FurStrand(
        index = index,
        angleRadians = strandAngleRadians(index, seed),
        jitter = strandJitter(index, seed),
        lengthFraction = hairLengthFraction(intensity),
        sweepRadians = sweepRadiansForFlow(flow),
        curvature = curvatureForFlow(flow),
    )
}

/**
 * Ultra-escalation rules for the fur. The RGB ghost and aura thinning
 * are OFF below [RGB_SPLIT_THRESHOLD_TIER] and turn on at Max/Ultra,
 * reusing the split distances already calibrated in
 * [ReasoningEffortCurve] (0dp below Max, 1.2dp at Max, 2.4dp at Ultra).
 */
object ReasoningEffortFur {

    /** First tier where the RGB ghost appears. Off below this. */
    val RGB_SPLIT_THRESHOLD_TIER: ReasoningEffortTier = ReasoningEffortTier.Max

    /** True only at Max and Ultra. */
    fun hasRgbSplit(tier: ReasoningEffortTier): Boolean =
        ReasoningEffortCurve.hasRgbSplit(tier)

    /** Chromatic ghost offset in dp; exactly zero below the threshold. */
    fun rgbSplitDp(tier: ReasoningEffortTier): Float =
        ReasoningEffortCurve.forTier(tier).rgbSplitDp

    /**
     * Aura thickness factor: 1.0 below the threshold, then thinning
     * toward 0.45 at Ultra as the split grows to its calibrated max.
     */
    fun auraThinFactor(tier: ReasoningEffortTier): Float {
        val split = rgbSplitDp(tier)
        if (split <= 0f) return 1f
        val maxSplit = ReasoningEffortCurve.forTier(ReasoningEffortTier.Ultra).rgbSplitDp
        if (maxSplit <= 0f) return 1f
        return (1f - (split / maxSplit) * 0.55f).coerceIn(0.45f, 1f)
    }

    /** Flow for a tier: tier index / 5 (Off 0.0 .. Ultra 1.0). */
    fun flowForTier(tier: ReasoningEffortTier): Float =
        ReasoningEffortCurve.tierIndex(tier).toFloat() / 5f
}

/**
 * Smoothed agitation that chases [target] at the slew limits, so the
 * fur never snaps when the target jumps. Scale 0 jumps to the target.
 */
@Composable
fun rememberFurAgitation(
    target: Float,
    animatorDurationScale: Float,
): Float {
    var agitation by remember { mutableFloatStateOf(target.coerceIn(0f, 1f)) }
    LaunchedEffect(target, animatorDurationScale) {
        val goal = target.coerceIn(0f, 1f)
        while (true) {
            val next = if (animatorDurationScale <= 0f) goal
            else advanceAgitation(agitation, goal, 0.016f)
            agitation = next
            if (next == goal) break
            kotlinx.coroutines.delay(16L)
        }
    }
    return agitation
}

/**
 * Compose Canvas renderer for the fur orb.
 *
 * @param agentId seeds the strand field; same agent, same field.
 * @param intensity throughput intensity from [orbIntensity] /
 *   rememberAdaptiveOrbSpeed (0.55..2.6).
 * @param agitationTarget normalised agitation target (0..1); smoothed
 *   internally at the slew limits.
 * @param effort reasoning-effort tier; drives flow and the RGB ghost.
 * @param visible false when off-screen: draws nothing.
 * @param animatorDurationScale 0 draws nothing and stops the clock.
 */
@Composable
fun FurOrbRenderer(
    agentId: String,
    intensity: Float,
    agitationTarget: Float,
    effort: ReasoningEffortTier,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    animatorDurationScale: Float = systemAnimatorDurationScale(
        androidx.compose.ui.platform.LocalContext.current,
    ),
    paused: Boolean = false,
    orbSize: Dp = 96.dp,
    ink: Color = Color.White,
    accent: Color = Color(0xFF0A84FF),
) {
    val flow = ReasoningEffortFur.flowForTier(effort)
    val agitation = rememberFurAgitation(agitationTarget, animatorDurationScale)
    var frameMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(visible, paused, animatorDurationScale) {
        if (!shouldDrawFur(visible, animatorDurationScale, paused)) return@LaunchedEffect
        while (true) {
            withFrameMillis { ms -> frameMs = ms }
        }
    }
    val density = LocalDensity.current
    val splitPx = with(density) { ReasoningEffortFur.rgbSplitDp(effort).dp.toPx() }
    val gridPx = with(density) { 4.dp.toPx() }
    val timeSeconds = frameMs / 1000f
    val draw = shouldDrawFur(visible, animatorDurationScale, paused)

    Canvas(modifier = modifier.size(orbSize)) {
        if (!draw) return@Canvas
        val canvasSize = size
        val orbRadius = min(canvasSize.width, canvasSize.height) / 2f
        if (orbRadius <= 0f) return@Canvas
        val centre = center
        val coreRadius = orbRadius * 0.58f
        val count = countForIntensity(intensity)
        val lengthPx = hairLengthPx(intensity, orbRadius)
        val sweep = sweepRadiansForFlow(flow)
        val curvature = curvatureForFlow(flow)
        val seed = strandSeed(agentId)
        val split = splitPx
        val thin = ReasoningEffortFur.auraThinFactor(effort)
        val rim = ReasoningEffortCurve.forTier(effort).rimLightStrength
        val active = activeStrandIndex(count, timeSeconds)

        // Aura: single ring whose thickness thins above the RGB threshold.
        val auraWidth = (orbRadius * 0.06f * thin).coerceAtLeast(1f)
        drawArc(
            color = ink.copy(alpha = 0.35f * thin),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(centre.x - orbRadius, centre.y - orbRadius),
            size = Size(orbRadius * 2f, orbRadius * 2f),
            style = Stroke(width = auraWidth),
        )

        // Bot-mark core seat (monochrome disc on the 4dp grid inset).
        drawCircle(
            color = ink.copy(alpha = 0.85f),
            radius = (coreRadius - gridPx).coerceAtLeast(1f),
            center = centre,
        )

        // Strand field: single pass, one reusable Path, no allocation
        // in the loop beyond the path reset.
        val path = Path()
        var activeTip: Offset? = null
        for (i in 0 until count) {
            val jitter = strandJitter(i, seed)
            val preset = BlobAnimationLibrary.byIndex(i)
            val swaySpeed = archetypeOmega(preset.archetype)
            val sway = sin(timeSeconds * swaySpeed + jitter * 2f * PI.toFloat()) *
                0.03f * (0.3f + agitation)
            val angle = strandAngleRadians(i, seed) + sway
            val baseRadius = coreRadius * (0.82f + 0.18f * jitter)
            val bx = centre.x + cos(angle) * baseRadius
            val by = centre.y + sin(angle) * baseRadius
            val tipAngle = angle + sweep
            val bend = curvature * lengthPx * 0.35f
            val c1x = bx + cos(angle + sweep * 0.5f) * lengthPx * 0.33f
            val c1y = by + sin(angle + sweep * 0.5f) * lengthPx * 0.33f
            val c2x = bx + cos(tipAngle) * lengthPx * 0.66f - sin(tipAngle) * bend
            val c2y = by + sin(tipAngle) * lengthPx * 0.66f + cos(tipAngle) * bend
            val tipX = bx + cos(tipAngle) * lengthPx - sin(tipAngle) * bend * 0.5f
            val tipY = by + sin(tipAngle) * lengthPx + cos(tipAngle) * bend * 0.5f

            path.reset()
            path.moveTo(bx, by)
            path.cubicTo(c1x, c1y, c2x, c2y, tipX, tipY)

            val alpha = 0.35f + 0.65f * jitter
            val strokeWidth = (orbRadius * 0.018f).coerceAtLeast(1f)
            drawPath(path, ink.copy(alpha = alpha), style = Stroke(width = strokeWidth))

            // RGB-split ghost above the threshold only: same geometry,
            // red/blue offsets, low alpha. Off (zero offset) below Max.
            if (split > 0f) {
                withTransform({ translate(-split, 0f) }) {
                    drawPath(path, Color.Red.copy(alpha = 0.18f), style = Stroke(width = strokeWidth))
                }
                withTransform({ translate(split, 0f) }) {
                    drawPath(path, Color.Cyan.copy(alpha = 0.18f), style = Stroke(width = strokeWidth))
                }
            }
            if (i == active) activeTip = Offset(tipX, tipY)
        }

        // Single accent: the active strand tip only.
        val tip = activeTip
        if (tip != null) {
            drawCircle(
                color = accent,
                radius = (orbRadius * 0.03f).coerceAtLeast(2f),
                center = tip,
            )
        }

        // Single rim light: upper arc only, no gradients.
        if (rim > 0f) {
            drawArc(
                color = ink.copy(alpha = rim * 0.8f),
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(centre.x - orbRadius, centre.y - orbRadius),
                size = Size(orbRadius * 2f, orbRadius * 2f),
                style = Stroke(width = (orbRadius * 0.02f).coerceAtLeast(1f)),
            )
        }
    }
}
