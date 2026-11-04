package com.sarvesh173.talos.ui.bot

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow

/**
 * Ported from component/runtime/math.js + geometry.js.
 * Single source of truth for the bot's spring constants and easing.
 */
const val HEAD_C = 114.2705f
const val EYE_HALF = 21f
private const val FIXED_STEP = 1f / 120f

fun clampF(v: Float, min: Float, max: Float): Float = v.coerceIn(min, max)
fun randF(min: Float, max: Float): Float = min + kotlin.random.Random.nextFloat() * (max - min)
fun cubicInOut(v: Float): Float =
    if (v < 0.5f) 4f * v * v * v else 1f - ((-2f * v + 2f).pow(3f)) / 2f
fun cubicOut(v: Float): Float = 1f - (1f - v).pow(3f)
fun backOut(v: Float): Float = 1f + 2.70158f * (v - 1f).pow(3f) + 1.70158f * (v - 1f).pow(2f)
fun smoothstep(v: Float): Float = v * v * (3f - 2f * v)

/** Spring identical to JS springValue/stepSpring (semi-implicit Euler). */
class SpringValue(var x: Float, var target: Float = x, var v: Float = 0f) {
    fun step(frequency: Float, damping: Float, dt: Float) {
        // physics-system.js splits delta into FIXED_STEP sub-steps
        val steps = maxOf(1, kotlin.math.ceil(dt / FIXED_STEP).toInt())
        val h = dt / steps
        repeat(steps) { stepOnce(frequency, damping, h) }
    }

    private fun stepOnce(frequency: Float, damping: Float, dt: Float) {
        v += (-2f * damping * frequency * v - frequency * frequency * (x - target)) * dt
        x += v * dt
        if (!x.isFinite() || !v.isFinite()) { x = target; v = 0f }
    }
}

data class FaceParams(val x: Float = 0f, val y: Float = 0f, val sx: Float = 1f, val sy: Float = 1f, val eye: Float = 1f)
data class Pt(val x: Float, val y: Float)

fun lerpRing(from: List<Pt>, to: List<Pt>, amount: Float): List<Pt> {
    val n = minOf(from.size, to.size)
    return List(n) { i -> Pt(from[i].x + (to[i].x - from[i].x) * amount, from[i].y + (to[i].y - from[i].y) * amount) }
}
fun centroid(ring: List<Pt>): Pt {
    var x = 0f; var y = 0f
    for (p in ring) { x += p.x; y += p.y }
    val n = ring.size.coerceAtLeast(1).toFloat()
    return Pt(x / n, y / n)
}
fun lerpFace(from: FaceParams, to: FaceParams, a: Float): FaceParams = FaceParams(
    from.x + (to.x - from.x) * a, from.y + (to.y - from.y) * a,
    from.sx + (to.sx - from.sx) * a, from.sy + (to.sy - from.sy) * a,
    from.eye + (to.eye - from.eye) * a
)
/** spanAt from geometry.js — horizontal span of ring at y. */
fun spanAt(ring: List<Pt>, y: Float): Pair<Float, Float> {
    var left = Float.NEGATIVE_INFINITY; var right = Float.POSITIVE_INFINITY
    val n = ring.size
    for (i in 0 until n) {
        val s = ring[i]; val e = ring[(i + 1) % n]
        if ((s.y <= y) == (e.y <= y)) continue
        val x = s.x + (e.x - s.x) * (y - s.y) / (e.y - s.y)
        if (x <= HEAD_C) left = maxOf(left, x) else right = minOf(right, x)
    }
    return Pair(if (left.isFinite()) left else HEAD_C, if (right.isFinite()) right else HEAD_C)
}
/** Catmull-Rom -> cubic outline, same as ringOutline. */
fun ringOutlineD(ring: List<Pt>): String {
    val n = ring.size; if (n < 2) return ""
    fun r(v: Float) = kotlin.math.round(v * 100f) / 100f
    var path = "M${r(ring[0].x)} ${r(ring[0].y)}"
    for (i in 0 until n) {
        val prev = ring[(i - 1 + n) % n]; val p = ring[i]
        val next = ring[(i + 1) % n]; val after = ring[(i + 2) % n]
        path += "C${r(p.x + (next.x - prev.x) / 6f)} ${r(p.y + (next.y - prev.y) / 6f)} " +
                "${r(next.x - (after.x - p.x) / 6f)} ${r(next.y - (after.y - p.y) / 6f)} ${r(next.x)} ${r(next.y)}"
    }
    return path + "Z"
}

/** Parametric ring generator — replaces the 255KB original-data blob with the
 *  circle + scale profile each shape actually encodes. */
fun shapeRing(sides: Int, sx: Float, sy: Float, aspect: Float = 1f, count: Int = 96): List<Pt> {
    // sides==0 => round (blob/pebble/bean/egg/capsule…). Non-round get a
    // polygon-ish radius modulated by cos term, mimicking turnedShapeRing.
    val out = ArrayList<Pt>(count)
    for (i in 0 until count) {
        val a = i.toFloat() / count * 2f * PI.toFloat()
        var rf = 1f
        if (sides >= 3) {
            val seg = 2f * PI.toFloat() / sides
            val ang = ((a % seg) + seg) % seg - seg / 2f
            val c = kotlin.math.cos(ang) / kotlin.math.cos(seg / 2f)
            rf = 1f + (c - 1f) * 0.45f
        }
        val dx = kotlin.math.cos(a) * rf * sx
        val dy = kotlin.math.sin(a) * rf * sy
        out.add(Pt(HEAD_C + dx * 114.3f * aspect, HEAD_C + dy * 114.3f))
    }
    return out
}
