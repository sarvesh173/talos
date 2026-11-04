package com.sarvesh173.talos.ui.avatar

import com.sarvesh173.talos.ui.bot.BotState

/**
 * 500-expression library for agent avatars.
 *
 * GENERATION SCHEME (documented deterministic grammar, not 500 hand-drawn assets):
 * 10 families x 10 eye states x 5 mouth states = 500 expressions, id =
 * familyOrdinal * 50 + eyeIndex * 5 + mouthIndex (0..499, bijective). Each
 * expression is a parameter set (eyes/mouth/motion/aura) driving the existing
 * bot/blob renderer: eye geometry stays in the renderer's ~0..220 face-unit
 * box like ui.bot eye specs; motion/aura are scalar renderer params.
 *
 * SELECTION (automatic trigger, anti-flicker):
 * forAgent: window = floorDiv(tick, TICK_WINDOW=8); base = fnv1a(agentId) % 50;
 * offset = mod(base + window * STEP (=3), 50); id = familyForState(state).ordinal*50 + offset.
 * Same agent + state + tick window always gives the same expression. Step 3 is
 * coprime to 50, so the offset walks all 50 slots of the family block without
 * repetition in a block period; over 200 ticks (25 windows) one agent reaches
 * >= 25 distinct expressions per fixed family block. Adjacent-tick change is
 * bounded: only at window boundaries, offset moves by exactly 3 cyclically and
 * otherwise identical (no thrashing). Different agents differ via fnv1a(agentId),
 * so parallel agents show distinct expressions. forTrigger uses the same base
 * offset with a fixed family per event.
 */

enum class Family {
    Calm, Focused, Curious, Delighted, Confused,
    Struggling, Relieved, Alert, Sleepy, Celebrating
}

enum class Trigger {
    ThinkingStarted, ToolCall, Success, Error, DelegateSpawned, IdleTooLong
}

/** Left+right eye ellipses in renderer face units, plus a 0..1 openness scalar. */
data class EyeSpec(
    val leftCx: Float, val leftCy: Float, val leftW: Float, val leftH: Float,
    val rightCx: Float, val rightCy: Float, val rightW: Float, val rightH: Float,
    val openness: Float
)

data class MouthSpec(
    val width: Float, val cornerY: Float, val openness: Float, val curve: Float, val jawDrop: Float
)

data class MotionSpec(
    val bob: Float, val sway: Float, val tilt: Float, val squashFreq: Float, val amplitude: Float
)

data class AuraSpec(
    val intensity: Float, val pulse: Float, val ringCount: Int, val opacity: Float
)

data class Expression(
    val id: Int,
    val family: Family,
    val name: String,
    val eyes: EyeSpec,
    val mouth: MouthSpec,
    val motion: MotionSpec,
    val aura: AuraSpec
)

object ExpressionLibrary {
    const val EYE_COUNT = 10
    const val MOUTH_COUNT = 5
    const val PER_FAMILY = EYE_COUNT * MOUTH_COUNT
    const val TOTAL = 500
    const val TICK_WINDOW = 8L
    const val STEP = 3

    // 10 distinct eye states (eyeIndex -> geometry) in the renderer's
    // ~0..220 face-unit box so eyes can be used directly as eye ellipses.
    private val EYE_STATES: List<EyeSpec> = listOf(
        EyeSpec(100f, 120f, 34f, 56f, 160f, 120f, 34f, 56f, 1.0f),
        EyeSpec(100f, 124f, 36f, 40f, 160f, 124f, 36f, 40f, 0.72f),
        EyeSpec(100f, 128f, 38f, 22f, 160f, 128f, 38f, 22f, 0.38f),
        EyeSpec(100f, 116f, 30f, 66f, 160f, 116f, 30f, 66f, 1.0f),
        EyeSpec(100f, 132f, 40f, 16f, 160f, 132f, 40f, 16f, 0.24f),
        EyeSpec(102f, 120f, 32f, 50f, 158f, 118f, 32f, 50f, 0.92f),
        EyeSpec(96f, 116f, 30f, 52f, 164f, 120f, 30f, 52f, 0.96f),
        EyeSpec(100f, 126f, 40f, 28f, 160f, 126f, 40f, 28f, 0.5f),
        EyeSpec(100f, 114f, 28f, 60f, 160f, 114f, 28f, 60f, 1.0f),
        EyeSpec(100f, 122f, 34f, 48f, 160f, 122f, 34f, 48f, 0.84f)
    )

    private val EYE_NAMES: List<String> = listOf(
        "OpenRound", "HalfOpen", "Narrow", "Wide", "Sleepy",
        "Focused", "Curious", "Squint", "Bright", "Soft"
    )

    // 5 distinct mouth states.
    private val MOUTH_STATES: List<MouthSpec> = listOf(
        MouthSpec(44f, 176f, 0.2f, 0.55f, 0f),
        MouthSpec(36f, 178f, 0.85f, 0.1f, 14f),
        MouthSpec(40f, 180f, 0.08f, 0.0f, 0f),
        MouthSpec(42f, 182f, 0.15f, -0.5f, 0f),
        MouthSpec(20f, 180f, 0.45f, 0.2f, 6f)
    )

    private val MOUTH_NAMES: List<String> = listOf("Smile", "Open", "Flat", "Frown", "SmallO")

    // Motion/aura are per-family (10 distinct pairs, indexed by Family.ordinal).
    private val FAMILY_MOTIONS: List<MotionSpec> = listOf(
        MotionSpec(1.2f, 0.4f, 1.5f, 0.5f, 1.0f),
        MotionSpec(0.6f, 0.3f, -6f, 0.8f, 0.7f),
        MotionSpec(1.8f, 0.9f, 8f, 1.2f, 1.1f),
        MotionSpec(3.2f, 0.7f, 3f, 1.6f, 1.3f),
        MotionSpec(1.0f, 1.2f, 12f, 0.9f, 0.9f),
        MotionSpec(2.4f, 0.5f, 4f, 2.4f, 1.2f),
        MotionSpec(1.4f, 0.5f, 0f, 0.7f, 0.9f),
        MotionSpec(2.8f, 0.6f, -3f, 2.8f, 1.4f),
        MotionSpec(0.4f, 0.2f, 2.5f, 0.3f, 0.8f),
        MotionSpec(4.2f, 1.0f, 5f, 2.2f, 1.5f)
    )

    private val FAMILY_AURAS: List<AuraSpec> = listOf(
        AuraSpec(0.1f, 0.05f, 0, 0.0f),
        AuraSpec(0.25f, 0.15f, 1, 0.35f),
        AuraSpec(0.3f, 0.2f, 1, 0.4f),
        AuraSpec(0.45f, 0.3f, 1, 0.55f),
        AuraSpec(0.35f, 0.25f, 1, 0.45f),
        AuraSpec(0.5f, 0.6f, 1, 0.6f),
        AuraSpec(0.2f, 0.1f, 0, 0.25f),
        AuraSpec(0.6f, 0.7f, 2, 0.7f),
        AuraSpec(0.08f, 0.04f, 0, 0.15f),
        AuraSpec(0.7f, 0.5f, 2, 0.8f)
    )

    val ALL: List<Expression> = buildList {
        for (family in Family.entries) {
            for (eyeIndex in 0 until EYE_COUNT) {
                for (mouthIndex in 0 until MOUTH_COUNT) {
                    val id = family.ordinal * PER_FAMILY + eyeIndex * MOUTH_COUNT + mouthIndex
                    add(
                        Expression(
                            id = id,
                            family = family,
                            name = family.name + "-" + EYE_NAMES[eyeIndex] + "-" + MOUTH_NAMES[mouthIndex],
                            eyes = EYE_STATES[eyeIndex],
                            mouth = MOUTH_STATES[mouthIndex],
                            motion = FAMILY_MOTIONS[family.ordinal],
                            aura = FAMILY_AURAS[family.ordinal]
                        )
                    )
                }
            }
        }
    }

    /** Activity-state bias: every BotState maps to exactly one family; all 10 families covered. */
    fun familyForState(state: BotState): Family = when (state) {
        BotState.Idle, BotState.Orbit, BotState.Radar, BotState.Humming -> Family.Calm
        BotState.Thinking, BotState.Searching, BotState.Working, BotState.Writing,
        BotState.Loading, BotState.Progress -> Family.Focused
        BotState.Curious, BotState.Listening, BotState.Dictating, BotState.Spawning -> Family.Curious
        BotState.Happy, BotState.Excited, BotState.Laughing, BotState.Playful,
        BotState.Proud, BotState.Bouncing, BotState.Shy -> Family.Delighted
        BotState.Confused, BotState.Suspicious, BotState.Bored -> Family.Confused
        BotState.Angry, BotState.Dragging, BotState.Sad -> Family.Struggling
        BotState.Waking, BotState.Receiving, BotState.Sending, BotState.Uploading,
        BotState.Notifying -> Family.Relieved
        BotState.Alerting, BotState.Scared, BotState.Surprised -> Family.Alert
        BotState.Sleeping, BotState.Drowsy, BotState.PoweringDown -> Family.Sleepy
        BotState.Celebrate -> Family.Celebrating
    }

    fun familyForTrigger(trigger: Trigger): Family = when (trigger) {
        Trigger.ThinkingStarted -> Family.Focused
        Trigger.ToolCall -> Family.Curious
        Trigger.Success -> Family.Delighted
        Trigger.Error -> Family.Struggling
        Trigger.DelegateSpawned -> Family.Alert
        Trigger.IdleTooLong -> Family.Sleepy
    }

    private fun agentHash(agentId: String): Int {
        var h = -2128831035
        for (c in agentId) {
            h = h xor c.code
            h *= 0x01000193
        }
        return h
    }

    /** Automatic trigger. Deterministic per (agentId, state, tick window). */
    fun forAgent(agentId: String, state: BotState, tick: Long): Expression {
        val family = familyForState(state)
        val base = Math.floorMod(agentHash(agentId), PER_FAMILY)
        val window = Math.floorDiv(tick, TICK_WINDOW)
        val offset = Math.floorMod(base.toLong() + window * STEP.toLong(), PER_FAMILY.toLong()).toInt()
        return ALL[family.ordinal * PER_FAMILY + offset]
    }

    /** Event trigger. Deterministic per (trigger, agentId). */
    fun forTrigger(trigger: Trigger, agentId: String): Expression {
        val family = familyForTrigger(trigger)
        val base = Math.floorMod(agentHash(agentId), PER_FAMILY)
        return ALL[family.ordinal * PER_FAMILY + base]
    }

    fun byId(id: Int): Expression = ALL[Math.floorMod(id, TOTAL)]
}
