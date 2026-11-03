package com.sarvesh173.talos.ui.orb

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Thinking Orb geometry engine — a direct Kotlin port of the geometry in
 * Jakubantalik/thinking-orbs (`src/engine`), which is MIT licensed.
 *
 * The upstream library is React + canvas. Porting the *geometry* rather than
 * redrawing it keeps parity: the same deterministic hash, the same Fibonacci
 * lattice, the same z-sort. Every mode is pure math over (size, time, opts) —
 * no Android types here — so it is testable on the JVM and portable.
 *
 * Ported modes: orbits (working), globe (searching), rubik (solving),
 * wave (listening). Those are the four states an agent actually passes
 * through, which is what the chat needs. Upstream's decorative states
 * (weaving / composing / breathing / shaping) are intentionally not ported.
 */

/** One dot in a finished frame. `white` is ink: 0 = darkest, 1 = lightest. */
data class OrbDot(
    val x: Float,
    val y: Float,
    val z: Float,
    val r: Float,
    val white: Float,
    val alpha: Float = 1f
)

/** One rendered instant: dots already z-sorted into draw order. */
data class OrbFrame(val dots: List<OrbDot>)

/** Which animation to run. */
enum class OrbState { WORKING, SEARCHING, SOLVING, LISTENING }

/** Tunables. Mirrors upstream `ModeOpts`; `null` means "use profile default". */
data class OrbOpts(
    val orbitN: Int? = null,
    val ghostN: Int? = null,
    val ghostR: Float? = null,
    val ghostA: Float? = null,
    val particles: Int? = null,
    val partR: Float? = null,
    val partRDepth: Float? = null,
    val latRings: Int? = null,
    val lonDensity: Int? = null,
    val moveCount: Int? = null,
    val rings: Int? = null,
    val rBase: Float? = null,
    val rDepth: Float? = null,
    val rActive: Float? = null,
    val rBoost: Float? = null,
    val inkFar: Float? = null,
    val inkSpan: Float? = null,
    val rsPow: Float? = null,
    val rMin: Float? = null,
    val scanMul: Float? = null,
    val dimBase: Float? = null
)

internal const val GOLDEN = PI * (3 - sqrt(5.0))

/** Deterministic hash in [0, 1). Same constant as upstream, so layouts match. */
internal fun hashD(a: Number, b: Number): Float {
    val h = sin(a.toDouble() * 12.9898 + b.toDouble() * 78.233) * 43758.5453
    return (h - floor(h)).toFloat()
}

/** Shortest signed angular distance, wrapped to (-PI, PI]. */
internal fun angleDelta(a: Float, b: Float): Float =
    kotlin.math.atan2(sin(a - b).toDouble(), cos(a - b).toDouble()).toFloat()

/** Dot radii were tuned for a 300pt frame; sub-linear scaling keeps small spinners legible. */
internal fun radiusScale(size: Float, pow: Float): Float =
    (size / 300f).toDouble().pow(pow).toFloat()

private fun Double.pow(exp: Float): Double = Math.pow(this, exp.toDouble())

/**
 * Projector: shared spin + tilt + orthographic projection. Returns a lambda so
 * the per-mode inner loops stay allocation-free apart from the frame itself.
 */
internal fun makeProj(
    yaw: Float,
    tilt: Float,
    cx: Float,
    cy: Float,
    scale: Float
): (Float, Float, Float) -> FloatArray {
    val st = sin(tilt)
    val ct = cos(tilt)
    val sy = sin(yaw)
    val cyw = cos(yaw)
    return { x, y, z ->
        val x1 = x * cyw + z * sy
        val z1 = -x * sy + z * cyw
        val y1 = y * ct - z1 * st
        val z2 = y * st + z1 * ct
        floatArrayOf(cx + x1 * scale, cy - y1 * scale, z2)
    }
}

/**
 * Drop invisible marks, clamp radii to the mode's floor, and z-sort far→near.
 * Sorting here (not at paint time) is what makes a frame a complete,
 * final draw list.
 */
internal fun finalizeFrame(dots: MutableList<OrbDot>, rMin: Float): OrbFrame {
    val visible = dots.filter { it.alpha >= 0.02f }.map { it.copy(r = max(rMin, it.r)) }
    return OrbFrame(visible.sortedBy { it.z })
}

// ---------------------------------------------------------------------------
// Mode: orbits — particles on tilted orbits ("working")
// ---------------------------------------------------------------------------

internal fun frameOrbits(size: Float, t: Float, o: OrbOpts): OrbFrame {
    val cx = size / 2f
    val cy = size / 2f
    val radius = (size / 2f) * 0.82f
    val pt = makeProj(t * 0.12f, 0.3f, cx, cy, 1f)
    val rs = radiusScale(size, o.rsPow ?: 0.6f)

    val dots = ArrayList<OrbDot>(512)
    val orbitN = o.orbitN ?: 12
    val ghostN = o.ghostN ?: 40
    val particles = o.particles ?: 3
    val ghostR = o.ghostR ?: 0.9f
    val ghostA = o.ghostA ?: 0.5f
    val partR = o.partR ?: 1.2f
    val partRDepth = o.partRDepth ?: 1.6f

    for (orb in 0 until orbitN) {
        val h1 = hashD(orb, 1.7)
        val h2 = hashD(orb, 5.2)
        val h3 = hashD(orb, 8.9)
        val ro = radius * (0.45f + 0.52f * h1)
        val th = h1 * 2f * PI.toFloat()
        val phi = acos((2f * h2 - 1f).toDouble().coerceIn(-1.0, 1.0)).toFloat()

        // orbit plane basis (u, v ⟂ normal n)
        val nx = sin(phi) * cos(th)
        val ny = cos(phi)
        val nz = sin(phi) * sin(th)
        var ux = -ny
        var uy = nx
        val uz = 0f
        val ul = max(1e-6f, sqrt(ux * ux + uy * uy))
        ux /= ul
        uy /= ul
        val vx = ny * uz - nz * uy
        val vy = nz * ux - nx * uz
        val vz = nx * uy - ny * ux
        val speed = (0.25f + 0.55f * h3) * if (h3 > 0.5f) 1f else -1f

        // ghost path
        for (k in 0 until ghostN) {
            val a = (k.toFloat() / ghostN) * 2f * PI.toFloat()
            val p = pt(
                (ux * cos(a) + vx * sin(a)) * ro,
                (uy * cos(a) + vy * sin(a)) * ro,
                (uz * cos(a) + vz * sin(a)) * ro
            )
            val depth = (p[2] / ro + 1f) / 2f
            dots.add(
                OrbDot(
                    x = p[0], y = p[1], z = p[2],
                    r = ghostR * rs,
                    white = 0.72f,
                    alpha = ghostA * (0.4f + 0.6f * depth)
                )
            )
        }

        // the particles doing the work
        for (m in 0 until particles) {
            val a = t * speed + (m.toFloat() / particles) * 2f * PI.toFloat() + h2 * 6f
            val p = pt(
                (ux * cos(a) + vx * sin(a)) * ro,
                (uy * cos(a) + vy * sin(a)) * ro,
                (uz * cos(a) + vz * sin(a)) * ro
            )
            val depth = (p[2] / ro + 1f) / 2f
            dots.add(
                OrbDot(
                    x = p[0], y = p[1], z = p[2],
                    r = (partR + partRDepth * depth) * rs,
                    white = 0.3f - 0.22f * depth
                )
            )
        }
    }
    return finalizeFrame(dots, o.rMin ?: 0.3f)
}

// ---------------------------------------------------------------------------
// Mode: globe — lat/long field with a scan meridian ("searching")
// ---------------------------------------------------------------------------

internal fun frameGlobe(size: Float, t: Float, o: OrbOpts): OrbFrame {
    val spin = 0.5f
    val cx = size / 2f
    val cy = size / 2f
    val radius = (size / 2f) * 0.82f
    val tilt = 0.4f + 0.06f * sin(t * 0.35f)
    val pt = makeProj(t * spin, tilt, cx, cy, radius)
    // scan sweeps relative to the spin; scanMul scales that relative rate
    val scan = t * (spin + (1.7f - spin) * (o.scanMul ?: 1f))
    val rs = radiusScale(size, o.rsPow ?: 0.6f)
    val dimBase = o.dimBase ?: 1f
    val rBase = o.rBase ?: 0.6f
    val rDepth = o.rDepth ?: 1.7f
    val rBoost = o.rBoost ?: 1f
    val inkFar = o.inkFar ?: 0.62f
    val inkSpan = o.inkSpan ?: 0.54f

    val dots = ArrayList<OrbDot>(700)
    val latRings = o.latRings ?: 17
    val lonDensity = o.lonDensity ?: 44

    for (li in 0..latRings) {
        val lat = -PI / 2 + (li.toFloat() / latRings) * PI
        val cosLat = cos(lat)
        val sinLat = sin(lat)
        val lonCount = max(1, (abs(cosLat) * lonDensity).roundToInt())
        for (lj in 0 until lonCount) {
            val lon = (lj.toFloat() / lonCount) * 2f * PI.toFloat()
            val p = pt(
                (cosLat * cos(lon)).toFloat(),
                sinLat.toFloat(),
                (cosLat * sin(lon)).toFloat()
            )
            val z = p[2]
            val depth = (z + 1f) / 2f
            // the scan: a moving meridian read as a size ripple, not a shine
            val d = angleDelta((lon + t * spin).toFloat(), scan)
            val boost = exp(-(d * d) / 0.18f) * max(0f, z)
            dots.add(
                OrbDot(
                    x = p[0], y = p[1], z = z,
                    r = (rBase + rDepth * depth + rBoost * boost) * rs,
                    white = inkFar - inkSpan * depth,
                    // dimBase < 1 fades un-scanned dots so the meridian reads clearly
                    alpha = dimBase + (1f - dimBase) * min(1f, boost)
                )
            )
        }
    }
    return finalizeFrame(dots, o.rMin ?: 0.3f)
}

// ---------------------------------------------------------------------------
// Mode: rubik — bands twist in quarter turns, scramble then solve ("solving")
// ---------------------------------------------------------------------------

private data class RubikMove(val axis: Int, val lo: Float, val hi: Float, val ang: Float)

/**
 * Rapid eased moves scramble, then replay in reverse (palindrome) so everything
 * clicks back to solved, rests, repeats.
 */
private fun solveCycle(time: Float, count: Int, slotDur: Float, rest: Float): Pair<FloatArray, Int> {
    val cyc = 2 * count * slotDur + rest
    val tc = time % cyc
    val amount = FloatArray(count)
    var active = -1
    if (tc < 2 * count * slotDur) {
        val slot = floor(tc / slotDur).toInt()
        val p = (tc - slot * slotDur) / slotDur
        val cl = min(1f, p / 0.7f)
        val ep = 1f - (1f - cl) * (1f - cl) * (1f - cl) // machine ease-out
        if (slot < count) {
            for (i in 0 until slot) amount[i] = 1f
            amount[slot] = ep
            active = slot
        } else {
            val u = 2 * count - 1 - slot
            if (u in amount.indices) {
                for (i in 0 until u) amount[i] = 1f
                amount[u] = 1f - ep
                active = u
            }
        }
    }
    return amount to active
}

private fun applyMoves(
    x0: Float, y0: Float, z0: Float,
    moves: List<RubikMove>,
    amount: FloatArray,
    active: Int
): FloatArray {
    var x = x0
    var y = y0
    var z = z0
    var inActive = false
    for (i in moves.indices) {
        if (i >= amount.size || amount[i] <= 0f) continue
        val mv = moves[i]
        val coord = if (mv.axis == 0) x else if (mv.axis == 1) y else z
        if (coord < mv.lo || coord >= mv.hi) continue
        if (i == active) inActive = true
        val a = mv.ang * amount[i]
        val ca = cos(a)
        val sa = sin(a)
        when (mv.axis) {
            0 -> {
                val y2 = y * ca - z * sa
                z = y * sa + z * ca
                y = y2
            }
            1 -> {
                val x2 = x * ca + z * sa
                z = -x * sa + z * ca
                x = x2
            }
            else -> {
                val x2 = x * ca - y * sa
                y = x * sa + y * ca
                x = x2
            }
        }
    }
    return floatArrayOf(x, y, z, if (inActive) 1f else 0f)
}

private fun makeMoves(count: Int): List<RubikMove> {
    val moves = ArrayList<RubikMove>(count)
    for (i in 0 until count) {
        val axis = min(2, floor(hashD(i, 2.3) * 3).toInt())
        val lo = -1.0f + 0.5f * min(3, floor(hashD(i, 5.9) * 4).toInt())
        val dir = if (hashD(i, 7.7) < 0.5f) 1 else -1
        moves.add(RubikMove(axis, lo, lo + 0.5f, (dir * PI / 2).toFloat()))
    }
    return moves
}

internal fun frameRubik(size: Float, t: Float, o: OrbOpts): OrbFrame {
    val cx = size / 2f
    val cy = size / 2f
    val radius = (size / 2f) * 0.82f
    val pt = makeProj(t * 0.55f, 0.35f + 0.1f * sin(t * 0.9f), cx, cy, radius)
    val rs = radiusScale(size, o.rsPow ?: 0.6f)
    val moveCount = o.moveCount ?: 14
    val moves = makeMoves(moveCount)
    val (amount, active) = solveCycle(t, moveCount, 0.42f, 1.2f)

    val rBase = o.rBase ?: 0.6f
    val rDepth = o.rDepth ?: 1.7f
    val rActive = o.rActive ?: 0.3f
    val inkFar = o.inkFar ?: 0.62f
    val inkSpan = o.inkSpan ?: 0.54f

    val dots = ArrayList<OrbDot>(700)
    val latRings = o.latRings ?: 15
    val lonDensity = o.lonDensity ?: 40

    for (li in 0..latRings) {
        val lat = -PI / 2 + (li.toFloat() / latRings) * PI
        val cosLat = cos(lat)
        val sinLat = sin(lat)
        val lonCount = max(1, (abs(cosLat) * lonDensity).roundToInt())
        for (lj in 0 until lonCount) {
            val lon = (lj.toFloat() / lonCount) * 2f * PI.toFloat()
            val m = applyMoves(
                (cosLat * cos(lon)).toFloat(),
                sinLat.toFloat(),
                (cosLat * sin(lon)).toFloat(),
                moves, amount, active
            )
            val inActive = m[3] > 0f
            val p = pt(m[0], m[1], m[2])
            val zr = p[2]
            val depth = (zr + 1f) / 2f
            // the band being turned inks a touch darker — the "hand"
            dots.add(
                OrbDot(
                    x = p[0], y = p[1], z = zr,
                    r = (rBase + rDepth * depth + if (inActive) rActive else 0f) * rs,
                    white = inkFar - inkSpan * depth - if (inActive) 0.14f else 0f
                )
            )
        }
    }
    return finalizeFrame(dots, o.rMin ?: 0.3f)
}

// ---------------------------------------------------------------------------
// Mode: wave — a waveform rolls through the rings ("listening")
// ---------------------------------------------------------------------------

internal fun frameWave(size: Float, t: Float, o: OrbOpts): OrbFrame {
    val cx = size / 2f
    val cy = size / 2f
    // the undulation pulls the sphere inward, so wave is scaled up to match
    val radius = (size / 2f) * 0.874f
    val pt = makeProj(t * 0.18f, 0.38f, cx, cy, 1f)
    val rs = radiusScale(size, o.rsPow ?: 0.6f)
    val rBase = o.rBase ?: 0.6f
    val rDepth = o.rDepth ?: 1.7f

    val dots = ArrayList<OrbDot>(700)
    val rings = o.rings ?: 15
    val lonDensity = o.lonDensity ?: 40

    for (ri in 0..rings) {
        val lat = -PI / 2 + (ri.toFloat() / rings) * PI
        val cosLat = cos(lat)
        val sinLat = sin(lat)
        // two waves, different tempi — organic, never quite repeating
        val w = 0.62f * sin(t * 2.1f - ri * 0.52f) + 0.38f * sin(t * 1.27f + ri * 0.83f)
        val rr = radius * (0.88f + 0.105f * w)
        val lonCount = max(1, (abs(cosLat) * lonDensity).roundToInt())
        for (lj in 0 until lonCount) {
            val lon = (lj.toFloat() / lonCount) * 2f * PI.toFloat()
            val p = pt(
                (cosLat * cos(lon) * rr).toFloat(),
                (sinLat * rr).toFloat(),
                (cosLat * sin(lon) * rr).toFloat()
            )
            val z = p[2]
            val depth = (z / radius + 1f) / 2f
            val crest = max(0f, w)
            dots.add(
                OrbDot(
                    x = p[0], y = p[1], z = z,
                    r = (rBase + rDepth * depth) * (1f + 0.4f * crest) * rs,
                    white = 0.66f - 0.56f * depth - 0.1f * crest
                )
            )
        }
    }
    return finalizeFrame(dots, o.rMin ?: 0.3f)
}

// ---------------------------------------------------------------------------
// Presets — the shipped tunings, state × size, copied from upstream
// ---------------------------------------------------------------------------

/** Resolved per (state, size); the render loop sees plain numbers. */
data class OrbPreset(
    val speed: Float,
    val count: Float,
    val size: Float,
    val opts: OrbOpts = OrbOpts()
)

/**
 * Upstream ships exactly two tuned sizes: 64 (chat-avatar scale) and 20
 * (inline-text scale). They are separate designs, not a scale factor — the
 * 20px rows use different dot counts and speed.
 */
object OrbPresets {
    private fun base(state: OrbState): OrbOpts = when (state) {
        OrbState.WORKING -> OrbOpts(
            orbitN = 12, ghostN = 40, ghostR = 0.9f, ghostA = 0.5f,
            particles = 3, partR = 1.2f, partRDepth = 1.6f, rsPow = 0.6f, rMin = 0.3f
        )
        OrbState.SEARCHING -> OrbOpts(
            latRings = 17, lonDensity = 44, rBase = 0.6f, rDepth = 1.7f,
            rBoost = 1f, inkFar = 0.62f, inkSpan = 0.54f, rsPow = 0.6f, rMin = 0.3f
        )
        OrbState.SOLVING -> OrbOpts(
            latRings = 15, lonDensity = 40, moveCount = 14, rBase = 0.6f,
            rDepth = 1.7f, rActive = 0.3f, inkFar = 0.62f, inkSpan = 0.54f,
            rsPow = 0.6f, rMin = 0.3f
        )
        OrbState.LISTENING -> OrbOpts(
            rings = 15, lonDensity = 40, rBase = 0.6f, rDepth = 1.7f,
            rsPow = 0.6f, rMin = 0.3f
        )
    }

    fun resolve(state: OrbState, size: Int): OrbPreset {
        val large = size >= 48
        val b = base(state)
        return when (state) {
            OrbState.WORKING -> if (large) {
                OrbPreset(1.885f, 1f, 1f, b)
            } else {
                OrbPreset(
                    3.9f, 0.238f, 2.4f,
                    b.copy(
                        orbitN = max(1, (12 * 0.238f).roundToInt()),
                        ghostN = max(1, (40 * 0.238f).roundToInt()),
                        ghostR = 0.9f * 2.4f,
                        partR = 1.2f * 2.4f,
                        partRDepth = 1.6f * 2.4f
                    )
                )
            }
            OrbState.SEARCHING -> if (large) {
                OrbPreset(2.015f, 0.42f, 1.15f, b.copy(scanMul = 4.08f, dimBase = 0.45f))
            } else {
                OrbPreset(
                    2.665f, 0.105f, 1.75f,
                    b.copy(
                        latRings = max(2, (17 * sqrt(0.105)).roundToInt()),
                        lonDensity = max(2, (44 * sqrt(0.105)).roundToInt()),
                        rBase = 0.6f * 1.75f,
                        rDepth = 1.7f * 1.75f,
                        scanMul = 4.335f,
                        dimBase = 0.45f
                    )
                )
            }
            OrbState.SOLVING -> if (large) {
                OrbPreset(1.82f, 0.35f, 1.05f, b.copy(moveCount = max(4, (14 * 0.35f).roundToInt())))
            } else {
                OrbPreset(
                    1.95f, 0.088f, 1.9f,
                    b.copy(
                        latRings = max(2, (15 * sqrt(0.088)).roundToInt()),
                        lonDensity = max(2, (40 * sqrt(0.088)).roundToInt()),
                        moveCount = max(4, (14 * 0.088f).roundToInt()),
                        rBase = 0.6f * 1.9f,
                        rDepth = 1.7f * 1.9f
                    )
                )
            }
            OrbState.LISTENING -> if (large) {
                OrbPreset(4.388f, 0.341f, 1f, b.copy(rings = max(2, (15 * sqrt(0.341)).roundToInt())))
            } else {
                OrbPreset(
                    3.998f, 0.105f, 1.6f,
                    b.copy(
                        rings = max(2, (15 * sqrt(0.105)).roundToInt()),
                        lonDensity = max(2, (40 * sqrt(0.105)).roundToInt()),
                        rBase = 0.6f * 1.6f,
                        rDepth = 1.7f * 1.6f
                    )
                )
            }
        }
    }

    /** Build one frame. `time` is in the orb's own clock (seconds * speed). */
    fun frame(state: OrbState, sizePx: Float, time: Float): OrbFrame {
        val preset = resolve(state, sizePx.roundToInt())
        val t = time * preset.speed
        return when (state) {
            OrbState.WORKING -> frameOrbits(sizePx, t, preset.opts)
            OrbState.SEARCHING -> frameGlobe(sizePx, t, preset.opts)
            OrbState.SOLVING -> frameRubik(sizePx, t, preset.opts)
            OrbState.LISTENING -> frameWave(sizePx, t, preset.opts)
        }
    }
}
