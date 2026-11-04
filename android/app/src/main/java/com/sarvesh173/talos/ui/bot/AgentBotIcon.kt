package com.sarvesh173.talos.ui.bot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sarvesh173.talos.ui.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** PARTICLE belt colours from particle-system.js. */
private val BELT_COLORS = listOf(
    Color(0xFFF9705C), Color(0xFF5B95F0), Color(0xFF3FBE86),
    Color(0xFFF5B13F), Color(0xFF9A72EE), Color(0xFF35C3BD)
)
private const val STAR_GOLD = 0xFFF4C34E

/**
 * Animated agent bot SVG icon — the Grok bot character logo.
 * THIRD identity: shows what each agent IS. Not ThinkingOrb (ui/orb),
 * not AgentAvatar (ui/avatar). Canvas-drawn from BotEngine springs so it
 * stays crisp at any dp and recolourable per agent.
 *
 * Usage: AgentBotIcon(state=…, engine=…, shape=…, ink=…, size=…).
 * Telemetry overload maps live phase/tool flags straight to a state.
 */
@Composable
fun AgentBotIcon(
    state: BotState = BotState.Idle,
    engine: BotEngine? = null,
    shape: BotShape = BotShape.Blob,
    ink: Color = Color(0xFF0B0B0B),
    eyeInk: Color = Color(0xFFFFFFFF),
    size: Dp = 96.dp,
    label: String? = null,
    announce: Boolean = true,
    particles: Boolean = true,
    modifier: Modifier = Modifier
) {
    val eng = engine ?: remember { BotEngine() }
    val reduced = rememberReducedMotion()
    var frame by remember { mutableStateOf(0L) }
    var lastMs by remember { mutableStateOf(0L) }
    LaunchedEffect(eng, reduced) {
        if (reduced) { eng.setState(state, 0L); eng.frame(0L, 0f, true); frame = 1L; return@LaunchedEffect }
        eng.startedAt = System.currentTimeMillis(); eng.setState(state, eng.startedAt); lastMs = eng.startedAt
        while (true) {
            withFrameMillis { ms ->
                val delta = ((ms - lastMs) / 1000f).coerceAtLeast(0f); lastMs = ms
                // keep simulation clock in engine-time (ms since start)
                eng.frame(ms - eng.startedAt, delta, false)
                frame = ms
            }
        }
    }
    LaunchedEffect(state) { if (frame > 0L) eng.setState(state, (if (lastMs > 0) lastMs else System.currentTimeMillis()) - eng.startedAt + eng.startedAt) }
    // sync shape
    if (eng.shape != shape) eng.setShape(shape)
    val sem = if (announce) Modifier.semantics { contentDescription = label ?: "Agent ${state.name.lowercase()}" } else Modifier
    Canvas(modifier.size(size).then(sem)) {
        drawBot(eng, ink, eyeInk, particles, (frame / 1000f).toFloat())
    }
}

/** Telemetry-driven overload: phase/flags from TalosClient → BotState. */
@Composable
fun AgentBotIcon(
    phase: String?,
    engine: BotEngine? = null,
    shape: BotShape = BotShape.Blob,
    ink: Color = Color(0xFF0B0B0B),
    eyeInk: Color = Color(0xFFFFFFFF),
    size: Dp = 96.dp,
    isTyping: Boolean = false,
    isListening: Boolean = false,
    isSearching: Boolean = false,
    hasError: Boolean = false,
    isLoading: Boolean = false,
    isSending: Boolean = false,
    isReceiving: Boolean = false,
    isWriting: Boolean = false,
    isUploading: Boolean = false,
    isNotifying: Boolean = false,
    modifier: Modifier = Modifier
) {
    val mapped = remember(phase, isTyping, isListening, isSearching, hasError, isLoading, isSending, isReceiving, isWriting, isUploading, isNotifying) {
        BotState.fromTelemetry(phase, isTyping, isListening, isSearching, hasError, false, isLoading, isSending, isReceiving, isWriting, isUploading, isNotifying)
    }
    AgentBotIcon(mapped, engine, shape, ink, eyeInk, size, null, true, true, modifier)
}

private fun DrawScope.drawBot(eng: BotEngine, ink: Color, eyeInk: Color, particles: Boolean, runtimeSec: Float) {
    val s = this.size.minDimension / 2f
    val scale = s / 140f // view ~280 units wide maps to canvas
    val (ring, face, _belt) = eng.resolvedShape()
    val morphAmt = clampF(eng.morph.x, 0f, 1f)
    val normal = 1f - morphAmt
    val tx = eng.headX.x * normal + eng.directX * normal
    val ty = eng.headY.x * normal + eng.directY * normal
    val rotDeg = eng.rotation.x * 180f / PI.toFloat() * eng.shape.tiltScale * normal + eng.directRotation * normal
    val sx = normal + (morphAmt * (MorphCatalog.sizes[eng.morphEffect] ?: 19f) / HEAD_C)
    val sy = eng.scaleY.x * normal + (morphAmt * (MorphCatalog.sizes[eng.morphEffect] ?: 19f) / HEAD_C)

    cx(s).let { }
    // particle belt (particle-system.js: tilted orbit rings)
    if (particles) drawParticleBelt(eng, s, scale, runtimeSec)
    if (eng.humming.x > 0.01f) drawHumming(eng, s, scale, ink)

    // head. Compose has no `transform` parameter on drawPath — withTransform
    // is the supported way to apply scale/rotate/translate around a pivot.
    val headPath = ringToPath(ring)
    val cx = s + tx * scale
    val cy = s + ty * scale
    val pivot = Offset(HEAD_C * scale, HEAD_C * scale)
    withTransform({
        scale(sx * scale, sy * scale, pivot = pivot)
        rotate(rotDeg, pivot = pivot)
        translate(cx - HEAD_C * scale, cy - HEAD_C * scale)
    }) {
        drawPath(headPath, ink)
    }
    // eyes (svg-renderer.js renderEyes: lerp + blink scale + aim)
    if (morphAmt < 0.5f) drawEyes(eng, face, s, scale, eyeInk, tx, ty)
    // morph overlay
    if (morphAmt > 0.004f) drawMorph(eng, morphAmt, s, scale, ink)
    // notify badge
    val badge = clampF(eng.notify.x, 0f, 1.4f)
    if (badge > 0.01f) {
        val anchor = ring[(ring.size * 7 / 8) % ring.size]
        drawCircle(Color(0xFF1D9BF0), 20f * scale * badge, Offset(s + anchor.x * scale, s + anchor.y * scale))
    }
}

private fun DrawScope.cx(s: Float) = s // noop anchor for clarity

private fun DrawScope.drawParticleBelt(eng: BotEngine, s: Float, scale: Float, t: Float) {
    val belt = eng.currentBeltRadius()
    val spin = eng.spinAngle
    val count = if (eng.celebrateWildActive) 5 else 4
    val hueBase = (t * 20f) % 360f
    for (i in 0 until count) {
        val angle = spin + i * (2f * PI.toFloat() / count) + t * 0.4f
        val tilt = 0.16f + 0.34f * (i.toFloat() / count)
        val roll = t * 0.15f + i * 0.7f
        val horiz = belt * 1.02f * sin(angle)
        val vert = -belt * 1.02f * cos(angle) * sin(tilt)
        val cosR = cos(roll); val sinR = sin(roll)
        val x = s + (horiz * cosR - vert * sinR) * scale
        val y = s + (horiz * sinR + vert * cosR) * scale
        val depth = maxOf(0.72f + 0.28f * cos(angle) * cos(tilt), 0.3f)
        val r = (7f * depth * scale).coerceAtLeast(1f)
        val col = BELT_COLORS[i % BELT_COLORS.size]
        drawCircle(col.copy(alpha = 0.85f), r, Offset(x, y))
        if (i == 0) drawCircle(Color(STAR_GOLD).copy(alpha = 0.9f), (r * 0.55f).coerceAtLeast(1f), Offset(x, y))
    }
}

private fun DrawScope.drawHumming(eng: BotEngine, s: Float, scale: Float, ink: Color) {
    val amt = clampF(eng.humming.x, 0f, 1f)
    for (i in 0..1) {
        val angle = 0.85f * eng.spinAngle + i * PI.toFloat()
        val radius = 1.3f * 114.3f
        val depth = 0.55f + 0.45f * clampF((cos(angle) + 1f) / 2f, 0f, 1f)
        val x = s + radius * sin(angle) * scale
        val y = s + (-0.38f * radius * cos(angle) - 8f) * scale
        drawCircle(ink.copy(alpha = (0.3f + 0.7f * depth) * amt), 7.5f * depth * amt * scale, Offset(x, y))
    }
}

private fun DrawScope.drawEyes(eng: BotEngine, face: FaceParams, s: Float, scale: Float, eyeInk: Color, tx: Float, ty: Float) {
    val amount = clampF(eng.expressionSpring.x, 0f, 1f)
    val rings = listOf(
        lerpRing(eng.expressionFrom.first, eng.expressionTo.first, amount),
        lerpRing(eng.expressionFrom.second, eng.expressionTo.second, amount)
    )
    val open = clampF(eng.eyeOpen.x, 0.02f, 2.4f)
    val scaleE = clampF(eng.eyeScale.x, 0.2f, 2f) * face.eye
    for ((idx, ring) in rings.withIndex()) {
        val c = centroid(ring)
        val w = (ring.maxOf { it.x } - ring.minOf { it.x }) * scaleE * 0.5f
        val h = (EYE_HALF * open * scaleE) * scale
        val wink = if (idx == eng.winkEye) 1f else 1f
        val cxP = s + (HEAD_C + face.x + (c.x - HEAD_C) * face.sx + eng.aimX.x * 0.4f + tx * 0.3f) * scale
        val cyP = s + (HEAD_C + face.y + (c.y - HEAD_C) * face.sy + eng.aimY.x * 0.4f + ty * 0.3f) * scale
        val path = ringToPath(ring)
        drawPath(path, eyeInk, alpha = 1f)
        // clamp height for squint
        if (open < 0.4f) drawCircle(ink(), (w).coerceAtLeast(0f), Offset(cxP, cyP))
    }
}
private fun ink(): Color = Color(0xFF0B0B0B)

private fun DrawScope.drawMorph(eng: BotEngine, amt: Float, s: Float, scale: Float, ink: Color) {
    val effect = eng.morphEffect ?: return
    val size = MorphCatalog.sizes[effect] ?: 19f
    val r = size * scale * amt
    when (effect) {
        "dots" -> for (i in 0..2) {
            val a = i * 2.1f; val pulse = (sin(eng.spinAngle * 3f + i * 1.2f) + 1f) / 2f
            drawCircle(ink.copy(alpha = 0.5f + 0.5f * pulse), r * 0.35f, Offset(s + cos(a) * r, s + sin(a) * r * 0.6f))
        }
        "orbit" -> { drawCircle(ink.copy(alpha = 0.6f), r * 0.25f, Offset(s + cos(eng.spinAngle) * r, s + sin(eng.spinAngle) * r * 0.4f)); drawCircle(ink.copy(alpha = 0.4f), r * 0.2f, Offset(s - cos(eng.spinAngle) * r, s - sin(eng.spinAngle) * r * 0.4f)) }
        "radar" -> { drawArc(ink, -90f, eng.spinAngle * 57.29f, false, Offset(s - r, s - r), kotlin.math.min(r * 2f, this.size.minDimension), Stroke(2f * scale)); drawCircle(ink, r * 0.2f, Offset(s, s)) }
        "progress" -> drawArc(ink, -90f, 360f * amt, false, Offset(s - r, s - r), kotlin.math.min(r * 2f, this.size.minDimension), Stroke(3f * scale))
        "gather" -> for (i in 0..5) { val a = i * PI.toFloat() / 3f; val d = r * (1f - amt * 0.5f); drawCircle(ink, r * 0.18f, Offset(s + cos(a) * d, s + sin(a) * d)) }
        "wave" -> for (i in 0..6) { val h = r * sin(eng.spinAngle * 4f + i * 0.9f); drawCircle(ink, r * 0.22f, Offset(s - r + i * r / 3f, s + h * 0.5f)) }
        "whirl" -> drawArc(ink, eng.spinAngle * 57f, 270f, false, Offset(s - r, s - r), kotlin.math.min(r * 2f, this.size.minDimension), Stroke(3f * scale))
        "bang" -> { drawCircle(ink, r * 0.2f, Offset(s, s - r * 0.4f)); drawCircle(ink, r * 0.2f, Offset(s, s + r * 0.4f)) }
        "standby" -> drawCircle(ink.copy(alpha = 1f - amt), r * 0.9f, Offset(s, s))
        else -> drawCircle(ink.copy(alpha = amt), r * 0.5f, Offset(s, s))
    }
}

private fun ringToPath(ring: List<Pt>): Path {
    val p = Path()
    if (ring.isEmpty()) return p
    p.moveTo(ring[0].x, ring[0].y)
    val n = ring.size
    for (i in 0 until n) {
        val prev = ring[(i - 1 + n) % n]; val cur = ring[i]
        val next = ring[(i + 1) % n]; val after = ring[(i + 2) % n]
        p.cubicTo(
            cur.x + (next.x - prev.x) / 6f, cur.y + (next.y - prev.y) / 6f,
            next.x - (after.x - cur.x) / 6f, next.y - (after.y - cur.y) / 6f,
            next.x, next.y
        )
    }
    p.close(); return p
}
