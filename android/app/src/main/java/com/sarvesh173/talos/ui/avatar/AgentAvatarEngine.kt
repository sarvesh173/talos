package com.sarvesh173.talos.ui.avatar

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Per-agent avatar: a small bot head that expresses what the agent is doing.
 *
 * This is an **avatar**, not a thinking indicator. The distinction matters:
 * a thinking spinner says "wait"; an avatar says "who is doing this and how is
 * it going". Every agent gets its own, so a room with four delegates is
 * readable at a glance — you can see which one is stuck before reading a name.
 *
 * The design borrows the geometry language of the Grok Bot component
 * (scrya-com/grokbot-animation) and the dotted-logo engine
 * (tifycloud/ThinkingLogoPro): springs for every animated value, a state
 * machine with per-state personality, and expression interpolation rather than
 * discrete sprites. It does not ship their assets — the shapes here are drawn
 * from primitives so the avatar can be recoloured per agent and remains crisp
 * at 16dp.
 *
 * Everything in [AgentAvatarEngine] is pure arithmetic over (state, time), so
 * it is unit-testable without a device and renders identically on every frame.
 */
enum class AvatarState {
    /** Nothing happening; the bot idles and blinks. */
    Idle,
    /** The user is speaking or the mic is open. */
    Listening,
    /** Reasoning. The bot narrows its eyes and looks inward. */
    Thinking,
    /** Running tools. Body leans forward, eyes track the work. */
    Working,
    /** Scanning a codebase. Eyes sweep. */
    Searching,
    /** Finished well. A short bounce. */
    Happy,
    /** Blocked or failed. Squash, brows down. */
    Error,
    /** Waiting on the user. Head tilts, eyes up. */
    Waiting
}

/**
 * A colour identity for one agent.
 *
 * Assigned from the agent id rather than stored, so a new delegate gets a
 * stable look with no configuration. Hues are spaced by the golden angle so
 * any two agents that appear together are visually distinct.
 */
data class AvatarIdentity(
    val agentId: String,
    val name: String,
    /** 0..360 */
    val hue: Float,
    /** 0..1, how saturated the ink is. Lower for quieter agents. */
    val saturation: Float = 0.62f,
    /** Optional custom artwork: a logo the user supplied for this agent. */
    val customLogoPath: String? = null
) {
    companion object {
        private const val GOLDEN_ANGLE = 137.508f

        /**
         * A stable identity for an agent id.
         *
         * Hash, then multiply by the golden angle: this distributes an
         * arbitrary number of agents evenly around the hue wheel with no two
         * neighbours landing on the same colour, which a hash-modulo-360 would
         * not guarantee.
         */
        fun of(agentId: String, name: String = agentId): AvatarIdentity {
            val h = stableHash(agentId)
            return AvatarIdentity(
                agentId = agentId,
                name = name,
                hue = (h * GOLDEN_ANGLE) % 360f
            )
        }

        /** FNV-1a: stable across processes and platforms, unlike hashCode. */
        private fun stableHash(s: String): Float {
            var h = 2166136261u
            for (c in s) {
                h = h xor c.code.toUInt()
                h *= 16777619u
            }
            return (h and 0xFFFFFFu).toFloat() / 0xFFFFFF.toFloat()
        }
    }
}

/** A value that eases toward its target. */
class SpringValue(
    var x: Float = 0f,
    var target: Float = 0f,
    var velocity: Float = 0f
) {
    /**
     * Integrate one step.
     *
     * Semi-implicit Euler at a fixed sub-step. Using the raw frame delta makes
     * a spring explode when the app is resumed after being backgrounded (the
     * delta can be seconds), so the caller passes a clamped delta and this
     * splits anything long into sub-steps.
     */
    fun step(dt: Float, stiffness: Float = 180f, damping: Float = 22f) {
        var remaining = dt.coerceIn(0f, 0.1f)
        val h = 1f / 240f
        while (remaining > 0f) {
            val step = minOf(h, remaining)
            val force = -stiffness * (x - target) - damping * velocity
            velocity += force * step
            x += velocity * step
            remaining -= step
        }
        // Guard against a NaN poison in the integrator: one bad frame must not
        // permanently freeze the avatar.
        if (x.isNaN()) x = target
        if (velocity.isNaN()) velocity = 0f
    }

    fun snap(v: Float) {
        x = v
        target = v
        velocity = 0f
    }

    val isSettled: Boolean
        get() = abs(x - target) < 0.002f && abs(velocity) < 0.02f
}

/**
 * The avatar's animated state. One instance per agent, advanced once per frame.
 */
class AgentAvatarEngine(
    val identity: AvatarIdentity,
    initialState: AvatarState = AvatarState.Idle
) {
    var state: AvatarState = initialState
        private set
    private var stateEnteredAt: Float = 0f

    /** Seconds since the engine began. */
    var clock: Float = 0f
        private set

    // -- animated channels --------------------------------------------------
    val eyeOpen = SpringValue(1f, 1f)
    val eyeScaleX = SpringValue(1f, 1f)
    val gazeX = SpringValue(0f, 0f)
    val gazeY = SpringValue(0f, 0f)
    val bobY = SpringValue(0f, 0f)
    val squash = SpringValue(0f, 0f)
    val tilt = SpringValue(0f, 0f)
    val browAngle = SpringValue(0f, 0f)

    /** 0 = the resting head, 1 = fully morphed into the working form. */
    val morph = SpringValue(0f, 0f)

    /**
     * How fast the avatar should be breathing/moving.
     *
     * Driven by the agent's own output rate so a fast agent looks fast. The
     * caller sets it; it is clamped to [0.35, 2.2] because below that the
     * avatar looks broken and above that it looks like a glitch.
     */
    var tempo: Float = 1f
        private set

    private var blinkAt: Float = 2.5f
    private var nextBlinkIn: Float = 3f
    private var stepAccumulator: Float = 0f

    fun setState(next: AvatarState) {
        if (next == state) return
        state = next
        stateEnteredAt = clock
        when (next) {
            AvatarState.Happy -> squash.target = -0.18f // stretch up
            AvatarState.Error -> squash.target = 0.16f // flatten
            AvatarState.Waiting -> tilt.target = 0.14f
            else -> {
                squash.target = 0f
                tilt.target = 0f
            }
        }
        when (next) {
            AvatarState.Thinking -> {
                eyeOpen.target = 0.62f
                browAngle.target = -0.22f
                morph.target = 0.35f
            }
            AvatarState.Working -> {
                eyeOpen.target = 0.88f
                browAngle.target = 0.10f
                morph.target = 0.65f
            }
            AvatarState.Searching -> {
                eyeOpen.target = 0.95f
                morph.target = 0.5f
            }
            AvatarState.Listening -> {
                eyeOpen.target = 1.06f
                morph.target = 0.2f
            }
            AvatarState.Happy -> {
                eyeOpen.target = 0.30f // a smile needs the eyes shut a little
                browAngle.target = -0.30f
                morph.target = 0f
            }
            AvatarState.Error -> {
                eyeOpen.target = 0.55f
                browAngle.target = 0.34f
                morph.target = 0f
            }
            AvatarState.Waiting -> {
                eyeOpen.target = 1f
                browAngle.target = -0.12f
                morph.target = 0f
            }
            AvatarState.Idle -> {
                eyeOpen.target = 1f
                browAngle.target = 0f
                morph.target = 0f
            }
        }
    }

    /** Set the animation tempo from the agent's output rate, in tokens/s. */
    fun setTempoFromRate(tokensPerSecond: Float) {
        // 8 tok/s reads as "normal". Below that the avatar slows down but never
        // stops; a frozen avatar is indistinguishable from a crashed agent.
        tempo = (tokensPerSecond / 8f).coerceIn(0.35f, 2.2f)
    }

    fun setTempo(value: Float) {
        tempo = value.coerceIn(0.35f, 2.2f)
    }

    /**
     * Advance by [dt] seconds (already clamped by the caller).
     *
     * Returns nothing: the caller reads the channels. Kept side-effect-only so
     * a test can step the engine and assert on values without a renderer.
     */
    fun update(dt: Float) {
        val d = dt.coerceIn(0f, 0.05f)
        clock += d * tempo

        // Blink scheduling. Deliberately random rather than periodic: a
        // metronome blink reads as a loading animation, not a face.
        blinkAt -= d
        if (blinkAt <= 0f && state != AvatarState.Happy) {
            eyeOpen.snap(1f)
            eyeOpen.target = 0.08f
            blinkAt = 0.09f
            nextBlinkIn = 2.2f + pseudoRandom(clock) * 4.5f
        } else if (blinkAt <= 0f && state == AvatarState.Happy) {
            // Happy holds the eyes shut; release and reschedule.
            blinkAt = nextBlinkIn
        } else if (eyeOpen.target < 0.5f && blinkAt > 0.02f && state != AvatarState.Thinking) {
            // Reopen once the lid has been down long enough.
            eyeOpen.target = when (state) {
                AvatarState.Happy -> 0.30f
                AvatarState.Error -> 0.55f
                else -> 1f
            }
        }
        if (blinkAt <= 0f) blinkAt = nextBlinkIn

        // Idle breathing, and a slower drift when the agent is deep in thought.
        val breathe = when (state) {
            AvatarState.Working -> 0.9f
            AvatarState.Thinking -> 0.5f
            AvatarState.Idle -> 1f
            else -> 1.2f
        }
        bobY.target = sin(clock * 1.5f * breathe) * 0.018f

        // Gaze. Searching sweeps; thinking looks up-left; waiting looks at the
        // user (straight out); working tracks a small figure-eight that reads
        // as reading code.
        when (state) {
            AvatarState.Searching -> {
                gazeX.target = sin(clock * 2.1f) * 0.34f
                gazeY.target = cos(clock * 1.3f) * 0.10f
            }
            AvatarState.Thinking -> {
                gazeX.target = -0.18f + sin(clock * 0.7f) * 0.05f
                gazeY.target = -0.16f
            }
            AvatarState.Working -> {
                gazeX.target = sin(clock * 3.4f) * 0.18f
                gazeY.target = sin(clock * 6.8f) * 0.07f
            }
            AvatarState.Waiting -> {
                gazeX.target = 0f
                gazeY.target = -0.08f
            }
            AvatarState.Error -> {
                gazeX.target = 0f
                gazeY.target = 0.14f
            }
            else -> {
                gazeX.target = sin(clock * 0.5f) * 0.06f
                gazeY.target = cos(clock * 0.4f) * 0.03f
            }
        }

        // A short settle after entering Happy: stretch, then relax. This is the
        // only place the avatar overshoots on purpose — a bounce is a reward.
        if (state == AvatarState.Happy) {
            val since = clock - stateEnteredAt
            squash.target = if (since < 0.18f) -0.22f else if (since < 0.42f) 0.08f else 0f
        }

        eyeOpen.step(d, stiffness = 320f, damping = 26f)
        eyeScaleX.step(d)
        gazeX.step(d, stiffness = 140f, damping = 20f)
        gazeY.step(d, stiffness = 140f, damping = 20f)
        bobY.step(d, stiffness = 90f, damping = 14f)
        squash.step(d, stiffness = 260f, damping = 18f)
        tilt.step(d, stiffness = 120f, damping = 16f)
        browAngle.step(d, stiffness = 200f, damping = 20f)
        morph.step(d, stiffness = 90f, damping = 18f)
    }

    /** Cheap deterministic noise, enough to vary blink timing. */
    private fun pseudoRandom(t: Float): Float {
        val s = sin(t * 12.9898f) * 43758.5453f
        return s - kotlin.math.floor(s)
    }

    /**
     * Radius of the orbiting dots at [index] for the current morph amount.
     *
     * The avatar's "working" form is a ring of dots around the head that
     * multiplies as the agent gets busier. Returning plain coordinates keeps
     * the renderer dumb and the geometry testable.
     */
    fun orbitDot(index: Int, total: Int, radius: Float): Triple<Float, Float, Float> {
        val golden = 2.399963f
        val angle = index * golden + clock * 0.9f
        val r = radius * (0.75f + 0.25f * morph.x)
        return Triple(cos(angle) * r, sin(angle) * r, angle)
    }
}

/**
 * Squash-and-stretch, applied to a unit box.
 *
 * Volume is preserved so the head never looks inflated: whatever is added to
 * the width is taken from the height. Without this the "bounce" reads as a
 * zoom, which is the most common way a character animation looks cheap.
 */
fun squashToScale(squash: Float): Pair<Float, Float> {
    val sx = 1f + squash * 0.55f
    val sy = 1f - squash * 0.55f
    // Keep the product near 1 so the apparent volume is constant.
    val norm = 1f / sqrt(maxOf(0.01f, sx * sy))
    return (sx * norm) to (sy * norm)
}

/**
 * Where the pupil sits inside the eye, given a gaze vector.
 *
 * Clamped to the eye's own radius so a strong gaze cannot push the pupil
 * through the sclera — a bug that is invisible at rest and obvious in motion.
 */
fun pupilOffset(gazeX: Float, gazeY: Float, maxOffset: Float): Pair<Float, Float> {
    val mag = sqrt(gazeX * gazeX + gazeY * gazeY)
    if (mag < 1e-4f) return 0f to 0f
    val scale = minOf(1f, mag) / mag * maxOffset
    return gazeX * scale to gazeY * scale
}

/**
 * Eyelid coverage in 0..1 for a given openness and blink phase.
 *
 * Openness is a spring that can overshoot past 1 (a wide-eyed look), so this
 * clamps rather than assuming it stays in range.
 */
fun lidCoverage(openness: Float): Float = (1f - openness.coerceIn(0f, 1.3f)).coerceIn(0f, 1f)

/** How much the brow tilts, in radians, for a mood. */
fun browRadians(state: AvatarState): Float = when (state) {
    AvatarState.Error -> 0.34f
    AvatarState.Thinking -> -0.22f
    AvatarState.Happy -> -0.30f
    AvatarState.Waiting -> -0.12f
    else -> 0f
}

/** Exponential ease used by the renderer for non-spring accents. */
fun easeOutCubic(t: Float): Float {
    val x = 1f - t.coerceIn(0f, 1f)
    return 1f - x * x * x
}

/** A soft settle used for the morph, so it lands rather than stopping. */
fun settle(t: Float): Float = 1f - exp(-4.5f * t.coerceIn(0f, 1f))
