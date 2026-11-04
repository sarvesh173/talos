package com.sarvesh173.talos.ui.bot

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Ported from component/grok-bot-engine.js + runtime/state-behavior-system.js +
 * physics-system.js + morph-system.js. Pure Kotlin, unit-testable.
 * All spring frequencies/damping match physics-system.js exactly.
 */
class BotEngine {
    var state: BotState = BotState.Idle
        private set
    var shape: BotShape = BotShape.Blob
    var stateStartedAt = 0L
    var startedAt = 0L
    private var paused = false
    private var playbackRate = 1f

    // springs (physics-system.js)
    val expressionSpring = SpringValue(1f)
    val rotation = SpringValue(0f)
    val headX = SpringValue(0f)
    val headY = SpringValue(0f)
    val scaleY = SpringValue(1f)
    val eyeOpen = SpringValue(1f)
    val eyeScale = SpringValue(1f)
    val aimX = SpringValue(0f)
    val aimY = SpringValue(0f)
    val morph = SpringValue(0f)
    val morphBlend = SpringValue(1f)
    val shapeBlend = SpringValue(1f)
    val turn = SpringValue(0f)
    val notify = SpringValue(0f)
    val humming = SpringValue(0f)
    var spinSpring: SpringValue? = null

    var expressionIndex = 0
    var expressionFrequency = 7f
    var expressionFrom: Pair<List<Pt>, List<Pt>> = expressionRings(0)
    var expressionTo: Pair<List<Pt>, List<Pt>> = expressionFrom
    var expressionNext = 0L; var blinkNext = 0L; var gazeNext = 0L
    var listenNodNext = 0L; var listenNodUntil = 0L
    var impulseNext = 0L; var impulseUntil = 0L
    var behaviorNext = 0L; var winkNext = 0L; var winkAt = Long.MIN_VALUE / 2; var winkEye = 0
    var ambientNext = 0L; var drowsyStartedAt = 0L; var dragCycle = -1
    var notifyTriggered = false; var wakeBurst = false
    var spinAngle = 0f; var turnDirection = 1
    var celebrateCycle = -1; var celebrateWildActive = false
    var directTurn = 0f; var directRotation = 0f; var directX = 0f; var directY = 0f
    var directGazeX = 0f; var directGazeY = 0f
    var morphEffect: String? = null; var previousMorphEffect: String? = null
    var requestedMorphEffect: String? = null; var morphStartedAt = 0L
    var morphShotStartedAt = 0L; var morphRestStartedAt = 0L; var oneShotResting = false
    var morphPreview: Preview? = null
    var shapeFromRing: List<Pt> = shape.ring()
    var shapeFromFace = shape.face
    var shapeFromBelt = shape.beltRadius; var shapeChangeCycle = 0
    var bounceStartedAt = -1L
    var gesture: Gesture? = null
    var blinkTarget: Float? = null
    val blinkQueue = ArrayDeque<Pair<Long, Float>>()

    data class Preview(val effect: String, val duration: Long, var phase: String, var phaseStartedAt: Long)
    data class Gesture(val kind: String, val startedAt: Long, val direction: Int, val turns: Int)

    fun setState(s: BotState, now: Long) {
        if (s == state && now != stateStartedAt) return
        state = s; stateStartedAt = now
        expressionNext = now + randF(s.cadenceMs.first.toFloat(), s.cadenceMs.second.toFloat()).toLong()
        blinkNext = now + randF(1500f, 7000f).toLong()
        gazeNext = now + randF(500f, 1400f).toLong()
        listenNodNext = now + randF(1200f, 2200f).toLong()
        impulseNext = now + randF(500f, 1200f).toLong()
        behaviorNext = now + when (s) {
            BotState.Excited -> randF(400f, 1100f)
            BotState.Searching -> randF(800f, 1600f)
            BotState.Working -> randF(1200f, 2400f)
            else -> randF(6000f, 10000f)
        }.toLong()
        winkNext = now + randF(3000f, 8000f).toLong()
        blinkQueue.clear(); blinkTarget = null; wakeBurst = false
        drowsyStartedAt = 0; dragCycle = -1; notifyTriggered = false
        oneShotResting = false; morphPreview = null; celebrateWildActive = false
        morphShotStartedAt = now
        if (s == BotState.Celebrate) { turnDirection = if (kotlin.random.Random.nextFloat() < 0.5f) 1 else -1; celebrateCycle = -1 }
        if (s != BotState.Waking && s != BotState.Sleeping) {
            if (s != BotState.Drowsy) scheduleBlink(now)
            setExpression(s.exprPool.firstOrNull() ?: 0, if (s == BotState.Excited) 10f else 8f)
        }
    }

    fun setExpression(index: Int, frequency: Float = 7f) {
        if (index == expressionIndex && expressionSpring.target == 1f) return
        val amount = clampF(expressionSpring.x, 0f, 1f)
        expressionFrom = lerpRing(expressionFrom.first, expressionTo.first, amount) to
            lerpRing(expressionFrom.second, expressionTo.second, amount)
        expressionTo = expressionRings(index)
        expressionIndex = index
        expressionSpring.x = 0f; expressionSpring.v = 0f; expressionSpring.target = 1f
        expressionFrequency = frequency
    }

    fun scheduleBlink(now: Long) {
        blinkTarget = eyeOpen.target
        blinkQueue.add(now to 0.05f); blinkQueue.add(now + 70 to 0.05f)
        blinkQueue.add(now + 150 to 1.08f); blinkQueue.add(now + 300 to 1f)
        if (kotlin.random.Random.nextFloat() < 0.14f) { blinkQueue.add(now + 370 to 0.05f); blinkQueue.add(now + 480 to 1f) }
    }

    fun setShape(s: BotShape) {
        if (s == shape) return
        val prev = shape; val amt = cubicInOut(clampF(shapeBlend.x, 0f, 1f))
        shapeFromRing = if (amt >= 1f) prev.ring() else lerpRing(shapeFromRing, prev.ring(), amt)
        shapeFromFace = if (amt >= 1f) prev.face else lerpFace(shapeFromFace, prev.face, amt)
        shapeFromBelt += (prev.beltRadius - shapeFromBelt) * amt
        shape = s; shapeBlend.x = 0f; shapeBlend.v = 0f; shapeBlend.target = 1f
    }

    fun resolvedShape(): Triple<List<Pt>, FaceParams, Float> {
        val amt = cubicInOut(clampF(shapeBlend.x, 0f, 1f))
        val trans = amt < 0.999f
        val ring = if (trans) lerpRing(shapeFromRing, shape.ring(), amt) else shape.ring()
        val face = if (trans) lerpFace(shapeFromFace, shape.face, amt) else shape.face
        val belt = if (trans) shapeFromBelt + (shape.beltRadius - shapeFromBelt) * amt else shape.beltRadius
        return Triple(ring, face, belt)
    }

    /** One frame: state targets + fixed-step springs. Returns delta applied. */
    fun frame(nowMs: Long, deltaSec: Float, reducedMotion: Boolean): Float {
        val delta = minOf(deltaSec, 0.1f) * playbackRate
        if (!paused) {
            val elapsed = (nowMs - stateStartedAt) / 1000f
            val runtime = (nowMs - startedAt) / 1000f
            updateStateTargets(nowMs, elapsed, runtime, delta, reducedMotion)
            updateMorph(nowMs)
            // physics-system.js
            expressionSpring.step(expressionFrequency, 1f, delta)
            rotation.step(5f, 0.9f, delta); headX.step(3.5f, 1f, delta); headY.step(4f, 1f, delta)
            scaleY.step(10f, 0.8f, delta); eyeOpen.step(26f, 1f, delta); eyeScale.step(9f, 0.85f, delta)
            aimX.step(13f, 1f, delta); aimY.step(13f, 1f, delta); morph.step(14f, 1f, delta)
            morphBlend.step(11f, 1f, delta); shapeBlend.step(10f, 1f, delta); turn.step(14f, 1f, delta)
            notify.step(9f, 0.55f, delta); humming.step(6f, 1f, delta)
            spinSpring?.step(6.2f, 1f, delta)
            if (reducedMotion) { expressionSpring.x = 1f; morph.x = morph.target; morphBlend.x = morphBlend.target; shapeBlend.x = shapeBlend.target; turn.x = turn.target }
        }
        return delta
    }

    private fun updateStateTargets(now: Long, elapsed: Float, runtime: Float, delta: Float, reducedMotion: Boolean) {
        var eyeOpenT = 1f; var eyeScaleT = 1f; var rot = 0f; var x = 0f; var y = 0f; var sy = 1f
        var gEyeOpen: Float? = null; var gEyeScale: Float? = null
        var gTurn = 0f; var gRot = 0f; var gX = 0f; var gY = 0f; var gBounce = 0f; var gGazeX = 0f; var gGazeY = 0f
        if (!reducedMotion) {
            when (state) {
                BotState.Sleeping -> {
                    if (state.exprPool.contains(expressionIndex)) eyeOpenT = if (expressionSpring.x > 0.85f) 1f else 0.08f
                    else if (elapsed < 1.2f) eyeOpenT = maxOf(0.08f, 1f - minOf(1f, elapsed) * (1f + 0.15f * sin(6.5f * elapsed)))
                    else { eyeOpenT = 0.08f; if (eyeOpen.x < 0.18f) setExpression(13, 11f) }
                    val settle = minOf(elapsed / 2f, 1f)
                    val dip = sin(clampF(elapsed / 0.5f, 0f, 1f) * PI.toFloat())
                    rot = 4f * settle + 2f * sin(0.25f * runtime); x = -2f * settle
                    y = 8f * settle + 3f * sin(0.55f * runtime) - 5f * dip
                    sy = 1f + 0.016f * sin(0.55f * runtime) + 0.05f * dip
                }
                BotState.Waking -> {
                    if (elapsed < 0.5f) { eyeOpenT = 0.07f; setExpression(3, 12f); y = 6f }
                    else if (elapsed < 1.2f) { eyeOpenT = 1f; eyeScaleT = 1.12f; y = -5f; sy = 1.04f; wakeBurst = true }
                    else if (elapsed < 2.2f) { if (blinkQueue.isEmpty() && elapsed < 1.4f) scheduleBlink(now); setExpression(0) }
                    else { val s = minOf((elapsed - 2.2f) / 0.8f, 1f); rot = 6f * sin(s * PI.toFloat() * 3f) * (1f - s); y = 2f * sin(0.9f * runtime) }
                }
                BotState.Idle -> { rot = 1.5f * sin(0.5f * runtime) + 0.6f * sin(0.17f * runtime); x = sin(0.27f * runtime); y = 1.2f * sin(0.85f * runtime); sy = 1f + 0.007f * sin(0.85f * runtime) }
                BotState.Listening -> {
                    rot = 8f + 1.5f * sin(0.5f * runtime); x = 2f; y = -2f + 0.8f * sin(0.8f * runtime); sy = 1.015f
                    if (now >= listenNodNext) { listenNodUntil = now + 380; listenNodNext = now + randF(1800f, 3200f).toLong() }
                    if (now < listenNodUntil) { val ph = 1f - (listenNodUntil - now).toFloat() / 380f; y += 4.5f * sin(ph * PI.toFloat()); rot += 2f * sin(ph * PI.toFloat()) }
                }
                BotState.Thinking -> { rot = -9f + 5f * sin(0.35f * runtime); x = 5f * sin(0.3f * runtime); y = 2.5f * sin(0.6f * runtime) }
                BotState.Searching -> { val w = sin(1.3f * runtime); rot = 13f * w; x = 7f * w; y = 3f * sin(1.7f * runtime) }
                BotState.Working -> { val w = sin(runtime * PI.toFloat() * 3.2f); rot = 4f + 2.5f * w; x = 3f; y = 1.5f + 3f * maxOf(0f, w); sy = 1f - 0.02f * maxOf(0f, w) }
                BotState.Excited -> { val ph = (2.2f * runtime) % 1f; y = -10f * sin(ph * PI.toFloat()) + 2f; sy = if (ph < 0.1f) 0.92f else if (ph < 0.3f) 1.05f else 1f; x = 4f * sin(1.1f * runtime); eyeScaleT = 1.06f; rot = 7f * sin(runtime * PI.toFloat() * 2.2f) }
                BotState.Surprised -> { val s = minOf(elapsed / 1.2f, 1f); x = -4f * (1f - s); y = -8f * (1f - s); sy = if (elapsed < 0.2f) 1.08f else 1f; eyeScaleT = 1.15f - 0.08f * s; rot = 1.5f * sin(11f * runtime) * (1f - s) }
                BotState.Suspicious -> { rot = -6f + 3f * sin(0.3f * runtime); x = -4f * sin(0.25f * runtime); y = 1f + 1.2f * sin(0.45f * runtime); eyeOpenT = 0.85f; if (now >= impulseNext) { rotation.v += 30f * PI.toFloat() / 180f; impulseNext = now + randF(4000f, 7000f).toLong() } }
                BotState.Angry -> { if (now >= impulseNext) { impulseUntil = now + 420; headY.v += 70f; impulseNext = now + randF(1800f, 3200f).toLong() }; rot = if (now < impulseUntil) 4.5f * sin(0.05f * now) else 0f; y = 3.5f; sy = 0.975f }
                BotState.Drowsy -> { rot = 2.5f * sin(0.32f * runtime); x = 1.5f * sin(0.2f * runtime); y = 6f + 2.2f * sin(0.36f * runtime); sy = 1f + 0.022f * sin(0.36f * runtime); eyeOpenT = 0.34f + 0.07f * sin(0.8f * runtime) }
                BotState.Happy -> { val w = sin(2.4f * runtime); rot = 3f * sin(1.2f * runtime); x = 2.5f * sin(1.1f * runtime); y = -3f * abs(w); sy = 1f + 0.02f * w; eyeScaleT = 1.05f }
                BotState.Curious -> { rot = 10f + 6f * sin(0.7f * runtime); x = 5f * sin(0.6f * runtime); y = -2f + 1.5f * sin(0.9f * runtime); sy = 1.01f; eyeScaleT = 1.08f }
                BotState.Confused -> { val w = sin(0.8f * runtime); rot = 12f * w; x = 3f * w; y = 2f * sin(0.5f * runtime); eyeOpenT = 0.9f; if (now >= impulseNext) { rotation.v += 22f * PI.toFloat() / 180f; impulseNext = now + randF(2600f, 4200f).toLong() } }
                BotState.Bored -> { rot = -3f + 4f * sin(0.25f * runtime); x = 4f * sin(0.2f * runtime); y = 5f + 1.5f * sin(0.35f * runtime); sy = 0.99f; eyeOpenT = 0.6f; eyeScaleT = 0.98f }
                BotState.Proud -> { rot = 2.5f * sin(0.4f * runtime); x = 2f * sin(0.35f * runtime); y = -4f + sin(0.6f * runtime); sy = 1.03f; eyeScaleT = 1.02f; eyeOpenT = 0.9f }
                BotState.Shy -> { rot = -8f + 3f * sin(0.5f * runtime); x = -3f + 2f * sin(0.4f * runtime); y = 3f; sy = 0.98f; eyeScaleT = 0.95f; eyeOpenT = 0.85f }
                BotState.Sad -> { rot = 3f + 2f * sin(0.3f * runtime); x = 1.5f * sin(0.25f * runtime); y = 7f + sin(0.4f * runtime); sy = 0.97f; eyeScaleT = 0.97f; eyeOpenT = 0.7f }
                BotState.Laughing -> { val w = sin(runtime * PI.toFloat() * 6.4f); rot = 4f * w; x = 2f * sin(2f * runtime); y = -5f * abs(w); sy = 1f + 0.03f * w; eyeOpenT = 0.7f }
                BotState.Scared -> { rot = 2f * sin(0.04f * now); x = -2f + 1.5f * sin(0.05f * now); y = 2f + sin(1.5f * runtime); sy = 0.97f; eyeScaleT = 1.12f; eyeOpenT = 1.05f }
                BotState.Playful -> { rot = 8f * sin(1.4f * runtime); x = 4f * sin(1.1f * runtime); y = -3f * abs(sin(2.2f * runtime)); sy = 1f + 0.015f * sin(2.2f * runtime); eyeScaleT = 1.06f }
                BotState.Celebrate -> { y = -2.5f * abs(sin(1.6f * runtime)); eyeScaleT = 1.1f; eyeOpenT = 1.1f; val c = celebratePose(elapsed); gTurn = c.first; gRot = c.second }
                BotState.Dragging -> { val ph = (elapsed % 3.4f) / 3.4f; val cyc = kotlin.math.floor(elapsed / 3.4f).toInt(); if (ph < 0.12f) { x = -16f; y = -22f; rot = -5f } else if (ph < 0.62f) { x = -16f + 32f * cubicInOut((ph - 0.12f) / 0.5f); y = -22f + 2f * sin(1.4f * runtime); rot = 6f * sin(2.6f * runtime); eyeScaleT = 1.06f } else { if (cyc != dragCycle) { dragCycle = cyc; headY.v += 90f }; x = 16f } }
                BotState.Humming -> { rot = 2f * sin(0.4f * runtime); x = 1.5f * sin(0.3f * runtime); y = 1.5f * sin(0.7f * runtime) }
                BotState.Notifying -> { if (!notifyTriggered && elapsed > 0.12f) { notifyTriggered = true; headY.v -= 26f; scheduleBlink(now) }; eyeScaleT = 1f + 0.05f * exp(-3f * elapsed); rot = 3f; x = 2f; y = -1f }
                else -> Unit
            }
            val blinkOverride = updateExpressionBlink(now)
            updateAim(now)
            // gestures (spinBounce etc. — simplified: direct turns decay)
            if (gesture != null) {
                val el = (now - gesture!!.startedAt) / 1000f
                if (el > 2f) gesture = null else gTurn = gesture!!.turns * 2f * PI.toFloat() * gesture!!.direction * cubicInOut(el / 0.7f).coerceAtMost(1f)
            }
        } else { setExpression(state.exprPool.firstOrNull() ?: 0) }
        directTurn = if (state == BotState.Celebrate) gTurn else gTurn; directRotation = gRot; directX = gX; directY = gY + gBounce; directGazeX = gGazeX; directGazeY = gGazeY
        rotation.target = (rot) * PI.toFloat() / 180f
        headX.target = x; headY.target = y; scaleY.target = sy
        eyeOpen.target = eyeOpenT; eyeScale.target = eyeScaleT
        notify.target = if (state == BotState.Notifying) 1f else 0f
        humming.target = if (state == BotState.Humming) 1f else 0f
        if (state == BotState.Humming || state == BotState.Loading) {
            val speed = when {
                elapsed < 0.5f -> 7f * cubicInOut(elapsed / 0.5f)
                elapsed < 1.3f -> 7f + ((if (state == BotState.Loading) 3f else 1.6f) - 7f) * cubicInOut((elapsed - 0.5f) / 0.8f)
                else -> (if (state == BotState.Loading) 3f else 1.6f) + 0.3f * sin(0.5f * elapsed)
            }
            spinAngle += speed * delta
        } else if (state != BotState.Celebrate) spinAngle *= 0.94f
    }

    private fun updateExpressionBlink(now: Long): Float? {
        val s = state
        if (s != BotState.Waking && s != BotState.Sleeping && now >= expressionNext) {
            val pool = s.exprPool
            if (pool.isNotEmpty()) {
                val cursor = (kotlin.random.Random.nextInt(pool.size))
                setExpression(pool[cursor], if (s == BotState.Searching || s == BotState.Excited) 10f else 6f)
            }
            expressionNext = now + randF(s.cadenceMs.first.toFloat(), s.cadenceMs.second.toFloat()).toLong()
        }
        if (s.blinkMs != null && now >= blinkNext) { scheduleBlink(now); blinkNext = now + randF(s.blinkMs.first.toFloat(), s.blinkMs.second.toFloat()).toLong() }
        while (blinkQueue.isNotEmpty() && now >= blinkQueue.first().first) blinkTarget = blinkQueue.removeFirst().second
        if (blinkQueue.isNotEmpty()) return blinkTarget
        if (blinkTarget != null) { val v = blinkTarget; blinkTarget = null; return v }
        return null
    }

    private fun updateAim(now: Long) {
        if (now < gazeNext) return
        val s = state
        fun d() = if (kotlin.random.Random.nextFloat() < 0.5f) -1f else 1f
        var x = 8f * kotlin.random.Random.nextFloat() - 4f; var y = 3f * kotlin.random.Random.nextFloat() - 1.5f
        var min = 2500L; var max = 5000L
        when (s) {
            BotState.Thinking -> { x = d() * randF(7.5f, 15f); y = -randF(3.6f, 9f); min = 1500; max = 2800 }
            BotState.Searching -> { x = d() * randF(10.5f, 15f); y = randF(-9f, 9f); min = 550; max = 1150 }
            BotState.Working -> { x = randF(-6f, 6f); y = randF(3.6f, 9f); min = 1200; max = 2400 }
            BotState.Scared -> { x = d() * randF(10.5f, 15f); y = randF(-5.4f, 5.4f); min = 450; max = 1050 }
            else -> Unit
        }
        aimX.target = x; aimY.target = y; gazeNext = now + randF(min.toFloat(), max.toFloat()).toLong()
    }

    private fun celebratePose(elapsed: Float): Pair<Float, Float> {
        val ae = elapsed - 0.14f; if (ae < 0f) return 0f to 0f
        val cycle = ae % 6.2f; if (cycle > 5.49f) { celebrateWildActive = false; return 0f to 0f }
        celebrateWildActive = true
        val turns = 9; val dir = turnDirection.toFloat()
        val speed = (turns * 2f * PI.toFloat() + 0.5f) / (0.15f + 2f + 0.3125f)
        val angle = when {
            cycle < 0.24f -> -0.25f * (1f - cos(cycle / 0.24f * PI.toFloat()))
            cycle < 0.54f -> -0.5f + speed * (cycle - 0.24f).pow(2f) / 0.6f
            cycle < 2.54f -> -0.5f + speed * (0.15f + cycle - 0.54f)
            cycle < 3.79f -> -0.5f + speed * 2.15f + 1.25f * speed * (1f - (1f - (cycle - 2.54f) / 1.25f).pow(4f)) / 4f
            else -> turns * 2f * PI.toFloat()
        }
        return angle * dir to 0f
    }

    fun updateMorph(now: Long) {
        var requested = state.morph
        if (morphPreview != null) {
            val p = morphPreview!!
            when (p.phase) {
                "reset" -> { requested = null; if (morph.x < 0.004f) { p.phase = "enter"; p.phaseStartedAt = now; requested = p.effect } }
                "enter" -> { requested = p.effect; if (morph.x > 0.996f) { p.phase = "hold"; p.phaseStartedAt = now } }
                "hold" -> { requested = p.effect; if (now - p.phaseStartedAt >= p.duration) { p.phase = "exit"; p.phaseStartedAt = now; requested = null } }
                "exit" -> { requested = null; if (morph.x < 0.004f) { p.phase = "done" } }
            }
        }
        if (requested != requestedMorphEffect) { requestedMorphEffect = requested; morphShotStartedAt = now; oneShotResting = false }
        var visible = requested != null
        if (morphPreview == null && (state == BotState.Progress || state == BotState.Spawning) && requested != null) {
            val shot = if (state == BotState.Progress) 2500L else 2000L
            if (!oneShotResting && now - morphShotStartedAt > shot) { oneShotResting = true; morphRestStartedAt = now }
            else if (oneShotResting && now - morphRestStartedAt > 1500) { oneShotResting = false; morphShotStartedAt = now }
            visible = !oneShotResting
        }
        morph.target = if (visible) 1f else 0f
        if (requested != null && requested != morphEffect) {
            if (morphEffect != null && morph.x > 0.02f) { previousMorphEffect = morphEffect; morphBlend.x = 0f; morphBlend.v = 0f; morphBlend.target = 1f }
            else { previousMorphEffect = null; morphBlend.x = 1f; morphBlend.v = 0f; morphBlend.target = 1f }
            morphEffect = requested; morphStartedAt = now
        }
        if (requested == null && morph.x < 0.004f) { morphEffect = null; previousMorphEffect = null; morphBlend.x = 1f; morphBlend.v = 0f; morphBlend.target = 1f }
        if (previousMorphEffect != null && morphBlend.x > 0.996f) previousMorphEffect = null
    }

    fun setMorphPreview(effect: String, duration: Long = 2500) {
        if (effect !in MorphCatalog.ids) return
        morphPreview = Preview(effect, duration.coerceIn(100, 20000), "reset", System.currentTimeMillis()); oneShotResting = false
    }
    fun clearMorphPreview() { morphPreview = null }
    fun morphPhase(): String {
        if (morphPreview != null) return morphPreview!!.phase.uppercase()
        if (oneShotResting) return "REST"
        if (morph.target > 0.5f) return if (morph.x < 0.996f) "ENTER" else "HOLD"
        if (morph.x > 0.004f) return "EXIT"
        return "IDLE"
    }
    fun startSpin(turns: Int = 1) { if (spinSpring == null) spinSpring = SpringValue(0f, turns * 2f * PI.toFloat() * turnDirection) }
    fun currentBeltRadius(): Float = resolvedShape().third + if (state == BotState.Loading) (52f - resolvedShape().third) * morph.x else 0f
}
