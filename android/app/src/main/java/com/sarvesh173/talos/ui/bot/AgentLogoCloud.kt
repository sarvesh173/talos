package com.sarvesh173.talos.ui.bot

import android.content.Context
import android.content.res.XmlResourceParser
import androidx.annotation.DrawableRes
import com.sarvesh173.talos.ui.emblemByKey
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Baked point-cloud identity + the seven ThinkingLogoPro motion profiles.
 *
 * Port of /home/kira/talos-refs/ThinkingLogoPro (MIT) — engine/logo.ts,
 * engine/logoDeform.ts, engine/core.ts, engine/lattice.ts, engine/cloud.ts —
 * into plain Kotlin so it is unit-testable without Compose. No Compose types
 * appear in this file.
 *
 * The rule that shapes every profile: a sphere reads from every angle, a mark
 * has exactly ONE correct appearance, so the mark is always reached face-on
 * (whole-turn rotation) and left immediately. Cycle = dwell + 2 * morph.
 */

/** The seven logo loading states. Names mirror BotState where they overlap. */
enum class AgentLogoState(
    val dwell: Float,
    val morph: Float,
    val turns: Float,
    val settle: Float,
    val expo: Float
) {
    // logo.ts frameLogoAssemble — sphere <-> logo (stagger/arc/churn defaults).
    Thinking(5.5f, 1.9f, 1f, 0.45f, 0.3f),
    // logoDeform.ts frameLogoSolve — logo -> cube -> rubik solve -> logo.
    Solving(5.5f, 1.9f, 1f, 0.45f, 0.3f),
    // logoDeform.ts frameLogoWave — logo -> pulsing body -> logo.
    Listening(5.5f, 1.9f, 0f, 0.45f, 0.3f),
    // logo.ts frameLogoScan — logo -> meridian-swept sphere -> logo.
    Searching(5.5f, 1.9f, 1f, 0.1f, 0.3f),
    // logo.ts frameLogoWork — logo -> thread wound into a knot -> logo.
    Working(5.5f, 1.9f, 0f, 0.1f, 0.3f),
    // logoDeform.ts frameLogoWait — logo -> bellows of stacked rings -> logo.
    Waiting(5.5f, 1.9f, 0f, 0.1f, 0.3f),
    // logoDeform.ts frameLogoCrystal — crystal -> stitched -> logo -> crystal.
    Generating(5.5f, 1.9f, 0f, 0.1f, 0.3f);

    companion object {
        fun fromId(id: String): AgentLogoState =
            entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: Thinking
    }
}

/**
 * One projected dot in unit space (origin-centred, y-up, mark inscribed in
 * radius 1). [x]/[y] are post-rotation screen-plane coords, [z] is depth.
 * [r] is in tuned units (scaled by radiusScale in the composable).
 * [white] is the ink value (0 = darkest ink); depth IS the ink value — there
 * is no separate shading pass. [alpha] is visibility, [tintK] is how much of
 * the brand tint the dot carries (0 = neutral grey, used by Generating).
 */
data class LogoDot(
    val x: Float,
    val y: Float,
    val z: Float,
    val r: Float,
    val white: Float,
    val alpha: Float = 1f,
    val tintK: Float = 1f
)

/**
 * A baked mark: xyz triples in unit-sphere space, each point's normalised
 * distance from the silhouette edge ([edges], 0 = on the edge, 1 = deepest),
 * and the seat map (logo index -> Fibonacci sphere index) pairing every dot
 * with the sphere seat it flies home from.
 */
class AgentLogoCloud(
    val points: FloatArray,
    val edges: FloatArray,
    val seats: IntArray,
    val count: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AgentLogoCloud) return false
        return count == other.count &&
            points.contentEquals(other.points) &&
            edges.contentEquals(other.edges) &&
            seats.contentEquals(other.seats)
    }

    override fun hashCode(): Int {
        var h = count
        h = 31 * h + points.contentHashCode()
        h = 31 * h + edges.contentHashCode()
        h = 31 * h + seats.contentHashCode()
        return h
    }
}

// ---------------------------------------------------------------------------
// Baking
// ---------------------------------------------------------------------------

object AgentLogoBaker {

    const val DEFAULT_COUNT = 220
    const val DEFAULT_SEED = 1L
    const val DEFAULT_DEPTH = 0.34f

    /**
     * Bake flattened silhouette contours ([Pt] lists in viewport coords, y
     * down) into a dome-lifted cloud. Deterministic for a fixed [seed]:
     * same contours + same seed bake identically, every time.
     *
     * Pipeline mirrors the reference: fill the silhouette (even-odd across
     * all contours), blue-noise-ish Poisson-disk sample it with a seeded
     * RNG, approximate a distance transform (min distance to the contour
     * samples) for the edge value, then lift z by that distance so the
     * centre of the mark domes toward the viewer.
     */
    fun bakeFromContours(
        contours: List<List<Pt>>,
        count: Int = DEFAULT_COUNT,
        seed: Long = DEFAULT_SEED,
        depth: Float = DEFAULT_DEPTH,
        fillType: String = "evenOdd"
    ): AgentLogoCloud {
        val clean = contours.filter { it.size >= 3 }
        if (clean.isEmpty() || count <= 0) return emptyCloud()
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (c in clean) for (p in c) {
            minX = min(minX, p.x); maxX = max(maxX, p.x)
            minY = min(minY, p.y); maxY = max(maxY, p.y)
        }
        val w = (maxX - minX).coerceAtLeast(1e-6f)
        val h = (maxY - minY).coerceAtLeast(1e-6f)
        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        val scale = 2f / max(w, h) // inscribe in radius 1

        // Jittered-grid candidates inside the silhouette: blue-noise-ish.
        // Grid density oversamples the target ~2.2x; greedy min-distance
        // rejection with a seeded shuffle keeps it deterministic.
        val grid = sqrt(count.toFloat() * 2.2f).toInt().coerceAtLeast(8)
        val cellW = w / grid
        val cellH = h / grid
        val rng = kotlin.random.Random(seed)
        val candidates = ArrayList<Pt>(grid * grid)
        for (gy in 0 until grid) {
            for (gx in 0 until grid) {
                val x = minX + (gx + 0.15f + 0.7f * rng.nextFloat()) * cellW
                val y = minY + (gy + 0.15f + 0.7f * rng.nextFloat()) * cellH
                if (inside(x, y, clean, fillType)) candidates.add(Pt(x, y))
            }
        }
        candidates.shuffle(rng)
        val minDist = sqrt(w * h / count.toFloat()) * 0.82f
        val accepted = ArrayList<Pt>(count)
        val minDistSq = minDist * minDist
        outer@ for (cand in candidates) {
            for (a in accepted) {
                val dx = a.x - cand.x
                val dy = a.y - cand.y
                if (dx * dx + dy * dy < minDistSq) continue@outer
            }
            accepted.add(cand)
            if (accepted.size >= count) break
        }
        // Thin marks hit a floor (README): accept whatever fits.
        val picked = if (accepted.isEmpty()) candidates.take(count) else accepted
        if (picked.isEmpty()) return emptyCloud()

        // Contour samples for the distance transform (edge value).
        val contourPts = ArrayList<Pt>(clean.sumOf { it.size })
        for (c in clean) contourPts.addAll(c)
        val edgeRaw = FloatArray(picked.size)
        var maxEdge = 1e-6f
        for (i in picked.indices) {
            var best = Float.MAX_VALUE
            val p = picked[i]
            for (q in contourPts) {
                val dx = p.x - q.x
                val dy = p.y - q.y
                val d2 = dx * dx + dy * dy
                if (d2 < best) best = d2
            }
            edgeRaw[i] = sqrt(best)
            if (edgeRaw[i] > maxEdge) maxEdge = edgeRaw[i]
        }

        val n = picked.size
        val points = FloatArray(n * 3)
        val edges = FloatArray(n)
        for (i in 0 until n) {
            val p = picked[i]
            val e = (edgeRaw[i] / maxEdge).coerceIn(0f, 1f)
            edges[i] = e
            points[i * 3] = (p.x - cx) * scale
            points[i * 3 + 1] = -(p.y - cy) * scale // y-up
            points[i * 3 + 2] = depth * sqrt(e) // dome lift
        }
        return AgentLogoCloud(points, edges, seatMap(points, n), n)
    }

    /** Seat permutation: pair logo dots with sphere seats by polar angle. */
    fun seatMap(points: FloatArray, n: Int): IntArray {
        val logoAng = FloatArray(n)
        val seatAng = FloatArray(n)
        for (i in 0 until n) {
            logoAng[i] = atan2(points[i * 3 + 1], points[i * 3])
            val (sx, sy, _) = fibDir(i, n)
            seatAng[i] = atan2(sy, sx)
        }
        val byLogoBoxed = Array(n) { it }
        val bySeatBoxed = Array(n) { it }
        byLogoBoxed.sortWith(compareBy { logoAng[it] })
        bySeatBoxed.sortWith(compareBy { seatAng[it] })
        val seats = IntArray(n)
        for (k in 0 until n) seats[byLogoBoxed[k]] = bySeatBoxed[k]
        return seats
    }

    /**
     * Bake a VectorDrawable resource: parse its XML, extract every pathData
     * (applying group transforms), flatten curves to polylines, then
     * [bakeFromContours]. Approximation note: the reference rasterises and
     * contour-traces; here we sample the path geometry directly (the same
     * role PathMeasure sampling plays on a loaded drawable) and fill it.
     */
    fun bakeFromVectorDrawable(
        context: Context,
        @DrawableRes drawableRes: Int,
        count: Int = DEFAULT_COUNT,
        seed: Long = DEFAULT_SEED,
        depth: Float = DEFAULT_DEPTH
    ): AgentLogoCloud {
        val (contours, fillType) = parseVectorDrawable(context, drawableRes)
        return bakeFromContours(contours, count, seed, depth, fillType)
    }

    private fun emptyCloud(): AgentLogoCloud =
        AgentLogoCloud(FloatArray(0), FloatArray(0), IntArray(0), 0)

    private fun inside(x: Float, y: Float, contours: List<List<Pt>>, fillType: String): Boolean {
        var winding = 0
        var crossings = 0
        for (c in contours) {
            val m = c.size
            for (i in 0 until m) {
                val a = c[i]
                val b = c[(i + 1) % m]
                if ((a.y <= y) != (b.y <= y)) {
                    val xi = a.x + (b.x - a.x) * (y - a.y) / (b.y - a.y)
                    if (xi > x) {
                        crossings++
                        winding += if (b.y > a.y) 1 else -1
                    }
                }
            }
        }
        return if (fillType.equals("nonZero", ignoreCase = true)) winding != 0 else crossings % 2 == 1
    }

    // --- VectorDrawable XML parsing ---------------------------------------

    private data class Transform(
        val sx: Float = 1f, val sy: Float = 1f,
        val px: Float = 0f, val py: Float = 0f,
        val rot: Float = 0f,
        val tx: Float = 0f, val ty: Float = 0f
    ) {
        fun apply(p: Pt): Pt {
            var x = (p.x - px) * sx
            var y = (p.y - py) * sy
            if (rot != 0f) {
                val r = Math.toRadians(rot.toDouble()).toFloat()
                val c = cos(r)
                val s = sin(r)
                val nx = x * c - y * s
                val ny = x * s + y * c
                x = nx; y = ny
            }
            return Pt(x + px + tx, y + py + ty)
        }
    }

    private fun parseVectorDrawable(
        context: Context,
        @DrawableRes drawableRes: Int
    ): Pair<List<List<Pt>>, String> {
        val parser: XmlResourceParser = context.resources.getXml(drawableRes)
        val contours = ArrayList<List<Pt>>()
        var fillType = "nonZero"
        val transformStack = ArrayList<Transform>()
        var current = Transform()
        try {
            var event = parser.next()
            while (event != XmlPullParser.END_DOCUMENT) {
                when (parser.name) {
                    "group" -> {
                        if (event == XmlPullParser.START_TAG) {
                            transformStack.add(current)
                            current = current.composedWith(parseGroup(parser))
                        } else if (event == XmlPullParser.END_TAG) {
                            if (transformStack.isNotEmpty()) current = transformStack.removeAt(transformStack.size - 1)
                        }
                    }
                    "path" -> {
                        if (event == XmlPullParser.START_TAG) {
                            val ft = attr(parser, "fillType")
                            if (ft != null) fillType = ft
                            val data = attr(parser, "pathData")
                            if (!data.isNullOrBlank()) {
                                val raw = flattenPath(data)
                                if (raw.size >= 3) {
                                    contours.add(raw.map { current.apply(it) })
                                }
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } finally {
            parser.close()
        }
        return contours to fillType
    }

    private fun attr(parser: XmlResourceParser, name: String): String? {
        // android: namespace attributes; getAttributeValue(namespace, name).
        val ns = parser.getAttributeNamespace(0)
        val plain = parser.getAttributeValue(null, name)
        if (plain != null) return plain
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i) == name) return parser.getAttributeValue(i)
        }
        @Suppress("UNUSED_VARIABLE") val unused = ns
        return null
    }

    private fun parseGroup(parser: XmlResourceParser): Transform {
        fun f(name: String, def: Float): Float = attr(parser, name)?.toFloatOrNull() ?: def
        return Transform(
            sx = f("scaleX", 1f), sy = f("scaleY", 1f),
            px = f("pivotX", 0f), py = f("pivotY", 0f),
            rot = f("rotation", 0f),
            tx = f("translateX", 0f), ty = f("translateY", 0f)
        )
    }

    private fun Transform.composedWith(child: Transform): Transform {
        // parent o child, evaluated at a sample point (affine, exact enough
        // for the small group transforms our drawables use).
        val probe = listOf(Pt(0f, 0f), Pt(1f, 0f), Pt(0f, 1f)).map { child.apply(it) }
        val mapped = probe.map { this.apply(it) }
        val sx = mapped[1].x - mapped[0].x
        val sy = mapped[2].y - mapped[0].y
        val shear = mapped[1].y - mapped[0].y
        val rot = Math.toDegrees(atan2(shear, sx).toDouble()).toFloat()
        return Transform(sx = sx, sy = sy, px = 0f, py = 0f, rot = rot, tx = mapped[0].x, ty = mapped[0].y)
    }

    /** Flatten SVG path data (M/L/H/V/C/S/Q/T/A/Z, abs + rel) to polylines. */
    fun flattenPath(data: String): List<Pt> {
        val out = ArrayList<Pt>()
        var i = 0
        var cmd = 'M'
        var curX = 0f; var curY = 0f
        var startX = 0f; var startY = 0f
        var prevCx = 0f; var prevCy = 0f
        var prevQx = 0f; var prevQy = 0f
        var hasPrevC = false
        var hasPrevQ = false
        var first = true

        fun skipSep() {
            while (i < data.length && (data[i] == ' ' || data[i] == ',' || data[i] == '\n' || data[i] == '\t')) i++
        }
        fun num(): Float {
            skipSep()
            val start = i
            if (i < data.length && (data[i] == '-' || data[i] == '+')) i++
            var dot = false
            while (i < data.length) {
                val ch = data[i]
                if (ch.isDigit()) i++
                else if (ch == '.' && !dot) { dot = true; i++ }
                else if (ch == 'e' || ch == 'E') {
                    i++
                    if (i < data.length && (data[i] == '-' || data[i] == '+')) i++
                } else break
            }
            return data.substring(start, i).toFloatOrNull() ?: 0f
        }
        fun hasNum(): Boolean {
            skipSep()
            if (i >= data.length) return false
            val ch = data[i]
            return ch.isDigit() || ch == '-' || ch == '+' || ch == '.'
        }

        while (i < data.length) {
            skipSep()
            if (i >= data.length) break
            val ch = data[i]
            if (ch.isLetter()) {
                cmd = ch
                i++
                if (cmd == 'Z' || cmd == 'z') {
                    curX = startX; curY = startY
                    out.add(Pt(curX, curY))
                    hasPrevC = false; hasPrevQ = false
                    continue
                }
            }
            val rel = cmd.isLowerCase()
            when (cmd.uppercaseChar()) {
                'M' -> {
                    val x = num(); val y = num()
                    curX = if (rel) curX + x else x
                    curY = if (rel) curY + y else y
                    startX = curX; startY = curY
                    out.add(Pt(curX, curY))
                    hasPrevC = false; hasPrevQ = false
                    cmd = if (rel) 'l' else 'L' // subsequent pairs are implicit lineto
                }
                'L' -> {
                    val x = num(); val y = num()
                    curX = if (rel) curX + x else x
                    curY = if (rel) curY + y else y
                    out.add(Pt(curX, curY))
                    hasPrevC = false; hasPrevQ = false
                }
                'H' -> {
                    val x = num()
                    curX = if (rel) curX + x else x
                    out.add(Pt(curX, curY))
                    hasPrevC = false; hasPrevQ = false
                }
                'V' -> {
                    val y = num()
                    curY = if (rel) curY + y else y
                    out.add(Pt(curX, curY))
                    hasPrevC = false; hasPrevQ = false
                }
                'C' -> {
                    val c1x = num(); val c1y = num(); val c2x = num(); val c2y = num(); val x = num(); val y = num()
                    val p1x = if (rel) curX + c1x else c1x
                    val p1y = if (rel) curY + c1y else c1y
                    val p2x = if (rel) curX + c2x else c2x
                    val p2y = if (rel) curY + c2y else c2y
                    val ex = if (rel) curX + x else x
                    val ey = if (rel) curY + y else y
                    val sx = curX; val sy = curY
                    val steps = 10
                    for (s in 1..steps) {
                        val t = s.toFloat() / steps
                        val u = 1f - t
                        val bx = u*u*u*sx + 3*u*u*t*p1x + 3*u*t*t*p2x + t*t*t*ex
                        val by = u*u*u*sy + 3*u*u*t*p1y + 3*u*t*t*p2y + t*t*t*ey
                        out.add(Pt(bx, by))
                    }
                    prevCx = p2x; prevCy = p2y
                    curX = ex; curY = ey
                    hasPrevC = true; hasPrevQ = false
                }
                'S' -> {
                    val c2x = num(); val c2y = num(); val x = num(); val y = num()
                    val p1x = if (hasPrevC) prevCx else curX
                    val p1y = if (hasPrevC) prevCy else curY
                    val p2x = if (rel) curX + c2x else c2x
                    val p2y = if (rel) curY + c2y else c2y
                    val ex = if (rel) curX + x else x
                    val ey = if (rel) curY + y else y
                    val sx = curX; val sy = curY
                    val steps = 10
                    for (s in 1..steps) {
                        val t = s.toFloat() / steps
                        val u = 1f - t
                        out.add(Pt(u*u*u*sx + 3*u*u*t*p1x + 3*u*t*t*p2x + t*t*t*ex,
                                   u*u*u*sy + 3*u*u*t*p1y + 3*u*t*t*p2y + t*t*t*ey))
                    }
                    prevCx = p2x; prevCy = p2y
                    curX = ex; curY = ey
                    hasPrevC = true; hasPrevQ = false
                }
                'Q' -> {
                    val qx = num(); val qy = num(); val x = num(); val y = num()
                    val pqx = if (rel) curX + qx else qx
                    val pqy = if (rel) curY + qy else qy
                    val ex = if (rel) curX + x else x
                    val ey = if (rel) curY + y else y
                    val sx = curX; val sy = curY
                    val steps = 8
                    for (s in 1..steps) {
                        val t = s.toFloat() / steps
                        val u = 1f - t
                        out.add(Pt(u*u*sx + 2*u*t*pqx + t*t*ex, u*u*sy + 2*u*t*pqy + t*t*ey))
                    }
                    prevQx = pqx; prevQy = pqy
                    curX = ex; curY = ey
                    hasPrevQ = true; hasPrevC = false
                }
                'T' -> {
                    val x = num(); val y = num()
                    val pqx = if (hasPrevQ) 2f * curX - prevQx else curX
                    val pqy = if (hasPrevQ) 2f * curY - prevQy else curY
                    val ex = if (rel) curX + x else x
                    val ey = if (rel) curY + y else y
                    val sx = curX; val sy = curY
                    val steps = 8
                    for (s in 1..steps) {
                        val t = s.toFloat() / steps
                        val u = 1f - t
                        out.add(Pt(u*u*sx + 2*u*t*pqx + t*t*ex, u*u*sy + 2*u*t*pqy + t*t*ey))
                    }
                    prevQx = pqx; prevQy = pqy
                    curX = ex; curY = ey
                    hasPrevQ = true; hasPrevC = false
                }
                'A' -> {
                    val rx = num(); val ry = num(); num() // rotation (ignored)
                    val large = num(); val sweep = num(); val x = num(); val y = num()
                    val ex = if (rel) curX + x else x
                    val ey = if (rel) curY + y else y
                    // Endpoint -> centre conversion (x-axis rotation ignored,
                    // matching the unrotated arcs in our drawables).
                    val rxSafe = max(abs(rx), 1e-4f)
                    val rySafe = max(abs(ry), 1e-4f)
                    val dx = (curX - ex) / 2f
                    val dy = (curY - ey) / 2f
                    var lambda = (dx * dx) / (rxSafe * rxSafe) + (dy * dy) / (rySafe * rySafe)
                    if (lambda > 1f) lambda = sqrt(lambda)
                    val cxp = lambda * rxSafe * dy / rySafe
                    val cyp = -lambda * rySafe * dx / rxSafe
                    if (large == sweep) { /* sign handled below */ }
                    val sign = if (large != sweep) 1f else -1f
                    val cxA = sign * cxp + (curX + ex) / 2f
                    val cyA = sign * cyp + (curY + ey) / 2f
                    fun ang(ux: Float, uy: Float) = atan2(uy / rySafe, ux / rxSafe)
                    val th1 = ang((dx - cxp) / 1f, (dy - cyp) / 1f)
                    val dTh = ang((-dx - cxp) / 1f, (-dy - cyp) / 1f) - th1
                    var sweepAng = dTh
                    if (sweep == 1f && sweepAng < 0f) sweepAng += 2f * PI.toFloat()
                    if (sweep == 0f && sweepAng > 0f) sweepAng -= 2f * PI.toFloat()
                    val steps = 8
                    for (s in 1..steps) {
                        val a = th1 + sweepAng * s / steps
                        out.add(Pt(cxA + rxSafe * cos(a), cyA + rySafe * sin(a)))
                    }
                    curX = ex; curY = ey
                    hasPrevC = false; hasPrevQ = false
                }
                else -> { i++ }
            }
            first = false
            if (!hasNum() && i < data.length && !data[i].isLetter()) i++
        }
        return out
    }
}

/**
 * Per-agent-id bake cache: re-entering a screen never re-bakes.
 *
 * LRU-capped at [DEFAULT_MAX_CLOUDS] clouds (default 12) so user-supplied
 * SVGs cannot grow memory without bound. Eviction is eager on every put and
 * lookups are thread-safe. Read the current size via [size] (test hook).
 */
object AgentLogoClouds {
    /** Default cap for the per-agent cloud cache. */
    const val DEFAULT_MAX_CLOUDS = 12

    /** Current cap. Lowering it takes effect on the next put. */
    @Volatile var maxClouds: Int = DEFAULT_MAX_CLOUDS

    /** Number of clouds currently held. Exposed for tests. */
    val size: Int get() = synchronized(lock) { cache.size }

    /** Alias for the cap, for callers that prefer the conventional name. */
    val maxSize: Int get() = maxClouds

    private val lock = Any()
    // Access-ordered map: the most-recently-read entry is the last one the
    // iterator yields first, so the head is always the eviction candidate.
    private val cache = LinkedHashMap<String, AgentLogoCloud>(16, 0.75f, true)

    fun cloudFor(agentId: String, context: Context): AgentLogoCloud {
        val key = agentId.lowercase()
        synchronized(lock) { cache[key]?.let { return it } }
        val cloud = AgentLogoBaker.bakeFromVectorDrawable(
            context, emblemByKey(key).drawableRes
        )
        return put(key, cloud)
    }

    /** Non-baking peek: returns the cached cloud for [agentId], or null. */
    fun cached(agentId: String): AgentLogoCloud? = synchronized(lock) {
        cache[agentId.lowercase()]
    }

    /** Insert (or touch) a cloud under [agentId], evicting eldest entries. */
    fun put(agentId: String, cloud: AgentLogoCloud): AgentLogoCloud {
        val key = agentId.lowercase()
        synchronized(lock) {
            val prior = cache[key]
            if (prior != null) return prior
            cache[key] = cloud
            while (cache.size > maxClouds) {
                val eldest = cache.keys.firstOrNull() ?: break
                cache.remove(eldest)
            }
            return cloud
        }
    }

    fun contains(agentId: String): Boolean = synchronized(lock) {
        cache.containsKey(agentId.lowercase())
    }

    fun clear() = synchronized(lock) { cache.clear() }
}

// ---------------------------------------------------------------------------
// Per-frame point budget (testable maths — no Compose, no Canvas) + activity gate
// ---------------------------------------------------------------------------
//
// See AgentLogoQueue's KDoc for the exact, line-level adoption steps in
// AgentLogoMark.kt (async bake, budget wiring, and gate wiring).
//
// ## Hysteresis design
//
// computeFrameBudget is AIMD (additive increase, multiplicative decrease)
// with a wide deadband, which is what stops the budget from oscillating:
//
//  - **Fast drop, slow recover.** One frame over 25 ms cuts the budget by
//    25 % (multiplicative). Recovery only ever rises 10 % per frame, and
//    only when frames are under 12 ms. A budget that is too high sheds load
//    in 1–2 frames; a budget that is too low needs several sustained good
//    frames to come back, so a borderline workload cannot flap up/down
//    every frame.
//  - **13 ms deadband (12–25 ms).** The budget holds unchanged through the
//    whole band, absorbing normal 60 fps jitter around the 16.6 ms target
//    without any movement at all.
//  - **Floors and ceilings.** The budget never drops below
//    [MIN_FRAME_BUDGET_POINTS] (still legible) and never exceeds [maxPoints].
//  - **Total on hostile input.** NaN, infinite or negative frame times hold
//    the current budget (no evidence to change); non-positive [maxPoints]
//    returns 0.
//
// The caller should pass a smoothed frame time (e.g. a 5-frame EMA) rather
// than a raw single-frame delta when it can; this function already tolerates
// raw deltas, but smoothing widens the stable region on jittery frame
// clocks.

const val TARGET_FRAME_MS = 16.6f
const val FRAME_DOWN_THRESHOLD_MS = 25f
const val FRAME_UP_THRESHOLD_MS = 12f
const val MIN_FRAME_BUDGET_POINTS = 24

/**
 * Next frame's point budget given the previous frame's duration.
 *
 * Pure and total: same inputs always give the same output, and it is defined
 * for every input including 0, negative, NaN, and huge frame times.
 *
 * @param previousFrameMs duration of the previous frame in milliseconds
 * @param currentPointBudget budget in force for the previous frame
 * @param maxPoints maximum points available (e.g. cloud.count)
 */
fun computeFrameBudget(
    previousFrameMs: Float,
    currentPointBudget: Int,
    maxPoints: Int
): Int {
    if (maxPoints <= 0) return 0
    val clamped = currentPointBudget.coerceIn(0, maxPoints)
    if (!previousFrameMs.isFinite() || previousFrameMs < 0f) return clamped
    return when {
        previousFrameMs > FRAME_DOWN_THRESHOLD_MS -> {
            val step = maxOf(1, (clamped * 0.25f).toInt())
            (clamped - step).coerceAtLeast(minOf(MIN_FRAME_BUDGET_POINTS, maxPoints))
        }
        previousFrameMs < FRAME_UP_THRESHOLD_MS -> {
            if (clamped >= maxPoints) maxPoints
            else {
                val step = maxOf(1, (clamped * 0.10f).toInt())
                (clamped + step).coerceAtMost(maxPoints)
            }
        }
        else -> clamped
    }
}

/**
 * Total gate for whether the logo animation clock should run.
 *
 * Returns false when the mark is paused, offscreen/not visible, the app is
 * backgrounded, or the system animator duration scale is 0. Callers should
 * keep their withFrameNanos loop and their frame() recomputation inside
 * this gate (see the adoption steps in AgentLogoQueue's KDoc).
 *
 * Pure maths — safe to unit-test without Compose.
 *
 * @param paused caller-supplied pause flag (e.g. AgentLogoMark's `paused`)
 * @param visible whether the mark is currently on screen (viewport check)
 * @param inBackground whether the app is backgrounded (lifecycle check)
 * @param animatorScale system animator duration scale; <= 0 means disabled
 */
fun isActive(
    paused: Boolean,
    visible: Boolean = true,
    inBackground: Boolean = false,
    animatorScale: Float = 1f
): Boolean = !paused && visible && !inBackground && animatorScale > 0f

// ---------------------------------------------------------------------------
// Motion profiles (pure functions of (cloud, t, state) -> dots)
// ---------------------------------------------------------------------------

object LogoMotion {

    private const val TURN = (PI * 2).toFloat()
    private const val R_MIN = 0.3f

    data class Beat(
        val m: Float,       // 0 = working form, 1 = the mark
        val turns: Float,   // whole turns completed (lands on integer at the mark)
        val workT: Float,   // seconds into the working-form dwell (-1 outside)
        val local: Float,
        val cycle: Float
    )

    fun beatAt(t: Float, dwell: Float, morph: Float, turns: Float, settle: Float, expo: Float = 0.3f): Beat {
        val cycle = dwell + morph * 2f
        val local = t % cycle
        val spinSpan = dwell + morph * settle
        val spun = turns * cruise(min(1f, local / spinSpan), 0.22f)
        if (local < dwell) return Beat(0f, spun, local, local, cycle)
        val into = local - dwell
        if (into < morph) return Beat(morphEase(into / morph, expo), spun, -1f, local, cycle)
        return Beat(morphEase(1f - (into - morph) / morph, expo), spun, -1f, local, cycle)
    }

    fun frame(state: AgentLogoState, cloud: AgentLogoCloud, t: Float): List<LogoDot> {
        if (cloud.count == 0) return emptyList()
        return when (state) {
            AgentLogoState.Thinking -> frameAssemble(cloud, t, state)
            AgentLogoState.Searching -> frameScan(cloud, t, state)
            AgentLogoState.Solving -> frameSolve(cloud, t, state)
            AgentLogoState.Listening -> frameWave(cloud, t, state)
            AgentLogoState.Working -> frameWork(cloud, t, state)
            AgentLogoState.Waiting -> frameWait(cloud, t, state)
            AgentLogoState.Generating -> frameCrystal(cloud, t, state)
        }
    }

    // --- shared primitives -------------------------------------------------

    internal fun clamp01(x: Float): Float = x.coerceIn(0f, 1f)

    internal fun smoothE(x: Float): Float = x * x * (3f - 2f * x)

    internal fun smootherE(x: Float): Float = x * x * x * (x * (x * 6f - 15f) + 10f)

    internal fun expoInOut(x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        return if (x < 0.5f) 2f.pow(20f * x - 10f) / 2f else (2f - 2f.pow(-20f * x + 10f)) / 2f
    }

    internal fun morphEase(x: Float, expo: Float): Float {
        val smooth = smootherE(x)
        return smooth + (expoInOut(x) - smooth) * expo
    }

    internal fun cruise(x: Float, edge: Float): Float {
        val a = edge.coerceIn(0.001f, 0.49f)
        val v = 1f / (1f - a)
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        if (x < a) {
            val u = x / a
            return v * a * (u * u * u - (u * u * u * u) / 2f)
        }
        if (x > 1f - a) {
            val u = (1f - x) / a
            return 1f - v * a * (u * u * u - (u * u * u * u) / 2f)
        }
        return v * (a * 0.5f + (x - a))
    }

    /** Deterministic hash in [0, 1). */
    internal fun hashD(a: Float, b: Float): Float {
        val h = sin(a * 12.9898f + b * 78.233f) * 43758.5453f
        return h - floor(h)
    }

    /** Value noise on a 2D lattice — smooth, deterministic, cheap. */
    internal fun vnoise(x: Float, y: Float): Float {
        val xi = floor(x)
        val yi = floor(y)
        var fx = x - xi
        var fy = y - yi
        fx = fx * fx * (3f - 2f * fx)
        fy = fy * fy * (3f - 2f * fy)
        val a = hashD(xi, yi)
        val b = hashD(xi + 1f, yi)
        val c = hashD(xi, yi + 1f)
        val d = hashD(xi + 1f, yi + 1f)
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy
    }

    /** Stable directions on a unit sphere (Fibonacci lattice). */
    internal fun fibDir(i: Int, n: Int): Triple<Float, Float, Float> {
        val golden = PI.toFloat() * (3f - sqrt(5f))
        val y = 1f - (2f * (i + 0.5f)) / n
        val rad = sqrt(max(0f, 1f - y * y))
        val a = i * golden
        return Triple(rad * cos(a), y, rad * sin(a))
    }

    internal fun angleDelta(a: Float, b: Float): Float = atan2(sin(a - b), cos(a - b))

    /** Shared spin + tilt + orthographic projection to unit space. */
    internal fun project(x: Float, y: Float, z: Float, yaw: Float, tilt: Float): Triple<Float, Float, Float> {
        val st = sin(tilt); val ct = cos(tilt)
        val sy = sin(yaw); val cyw = cos(yaw)
        val x1 = x * cyw + z * sy
        val z1 = -x * sy + z * cyw
        val y1 = y * ct - z1 * st
        val z2 = y * st + z1 * ct
        return Triple(x1, y1, z2)
    }

    /** Ink for one projected dot: depth IS the ink value. */
    internal fun inkOf(zx: Float, edge: Float): Float {
        val far = 0.6f
        val span = 0.5f
        val rim = 0.16f
        return far - span * zx - rim * (1f - edge)
    }

    /** Sub-linear radius scaling; radii were tuned for a 300px frame. */
    fun radiusScale(sizePx: Float, pow: Float = 0.6f): Float = (sizePx / 300f).pow(pow)

    /** finalizeFrame: drop invisible dots, clamp radii, z-sort far -> near. */
    private fun finalize(dots: List<LogoDot>): List<LogoDot> =
        dots.filter { it.alpha >= 0.02f }
            .map { if (it.r < R_MIN) it.copy(r = R_MIN) else it }
            .sortedBy { it.z }

    private fun logoAt(cloud: AgentLogoCloud, i: Int): Triple<Float, Float, Float> =
        Triple(cloud.points[i * 3], cloud.points[i * 3 + 1], cloud.points[i * 3 + 2])

    // --- Thinking: sphere <-> logo (logo.ts frameLogoAssemble) --------------

    private fun frameAssemble(cloud: AgentLogoCloud, t: Float, s: AgentLogoState): List<LogoDot> {
        val n = cloud.count
        val b = beatAt(t, s.dwell, s.morph, s.turns, s.settle, s.expo)
        val m = b.m
        val yaw = TURN * b.turns
        val tilt = 0.34f * (1f - m)
        val sphereR = 0.92f
        val churn = 0.09f
        val share = 0.12f
        val dots = ArrayList<LogoDot>(n)
        for (i in 0 until n) {
            val (fx, fy, fz) = fibDir(cloud.seats[i], n)
            val wob = sphereR * (1f + churn * (vnoise(fx * 2f + t * 0.7f, fz * 2f) - 0.5f) * 2f)
            val (lx0, ly0, lz0) = logoAt(cloud, i)
            var lx = lx0; var ly = ly0; var lz = lz0
            var halo = 0f
            if (hashD(i.toFloat(), 6.7f) < share) {
                halo = m
                val osc = sin(t * 0.9f + hashD(i.toFloat(), 8.3f) * TURN)
                val out = 1f + 0.18f * (0.5f + 0.5f * osc) * halo
                lx *= out; ly *= out
                lz += 0.8f * osc * halo
            }
            val x = fx * wob + (lx - fx * wob) * m
            val y = fy * wob + (ly - fy * wob) * m
            val z3 = fz * wob + (lz - fz * wob) * m
            val (px, py, z) = project(x, y, z3, yaw, tilt)
            val zx = clamp01((z + 1f) / 2f)
            val travel = sin(PI.toFloat() * m)
            dots.add(
                LogoDot(
                    px, py, z,
                    r = 0.55f + 1.5f * zx + 0.22f * halo,
                    white = inkOf(zx, cloud.edges[i] * m + (1f - m)),
                    alpha = 1f - 0.25f * travel
                )
            )
        }
        return finalize(dots)
    }

    // --- Searching: meridian-swept sphere (logo.ts frameLogoScan) -----------

    private fun frameScan(cloud: AgentLogoCloud, t: Float, s: AgentLogoState): List<LogoDot> {
        val n = cloud.count
        val b = beatAt(t, s.dwell, s.morph, s.turns, s.settle, s.expo)
        val m = b.m
        val g = 1f - m
        val yaw = TURN * b.turns
        val tilt = 0.34f * g
        val sphereR = 0.94f
        val width = 0.22f
        val scan = yaw + PI.toFloat() / 2f + 1.05f * sin(t * 0.85f)
        val dimBase = 0.4f
        val ease = 1.4f
        val arms = 13
        val armDepth = 0.55f
        val dots = ArrayList<LogoDot>(n)
        for (i in 0 until n) {
            val seat = cloud.seats[i]
            val (ax, ay, az) = fibDir(seat, n)
            val lat = (if (ay < 0f) -1f else 1f) * abs(ay).pow(ease)
            val ring0 = sqrt(max(1e-9f, 1f - ay * ay))
            val ring = sqrt(max(0f, 1f - lat * lat)) / ring0
            val fx = ax * ring
            val fy = lat
            val fz = az * ring
            val tier = (seat % arms) % 3
            val arm = 1f - armDepth * (when (tier) { 0 -> 0f; 1 -> 0.5f; else -> 1f })
            val gx = fx * sphereR
            val gy = fy * sphereR
            val gz = fz * sphereR
            val (lx, ly, lz) = logoAt(cloud, i)
            val x = lx + (gx - lx) * g
            val y = ly + (gy - ly) * g
            val z3 = lz + (gz - lz) * g
            val d = angleDelta(atan2(fz, fx), scan)
            val boost = exp(-(d * d) / width) * g
            val (px, py, z) = project(x, y, z3, yaw, tilt)
            val zx = clamp01((z + 1f) / 2f)
            dots.add(
                LogoDot(
                    px, py, z,
                    r = 0.5f + 1.4f * zx * arm + 1.3f * boost,
                    white = inkOf(zx, cloud.edges[i] * m + (1f - m)) + 0.16f * (1f - arm) * g - 0.3f * boost,
                    alpha = 1f - (1f - dimBase) * g * (1f - min(1f, boost))
                )
            )
        }
        return finalize(dots)
    }

    // --- Solving: cube + rubik palindrome (logoDeform.ts frameLogoSolve) ----

    private data class Move(val axis: Int, val lo: Float, val hi: Float, val ang: Float)

    private fun makeCubeMoves(count: Int, half: Float): List<Move> {
        val band = (2f * half) / 3f
        val moves = ArrayList<Move>(count)
        for (i in 0 until count) {
            val axis = min(2, floor(hashD(i.toFloat(), 2.3f) * 3f).toInt())
            val lo = -half + band * min(2, floor(hashD(i.toFloat(), 5.9f) * 3f).toInt())
            val dir = if (hashD(i.toFloat(), 7.7f) < 0.5f) 1f else -1f
            moves.add(Move(axis, lo, lo + band, dir * PI.toFloat() / 2f))
        }
        return moves
    }

    private fun solveCycle(time: Float, count: Int): Pair<FloatArray, Int> {
        val slotDur = 1f
        val rest = 0f
        val cyc = 2f * count * slotDur + rest
        val tc = if (cyc > 0f) time % cyc else 0f
        val amount = FloatArray(count)
        var active = -1
        if (tc < 2f * count * slotDur) {
            val slot = floor(tc / slotDur).toInt()
            val p = (tc - slot * slotDur) / slotDur
            val cl = min(1f, p / 0.7f)
            val ep = 1f - (1f - cl).pow(3f)
            if (slot < count) {
                for (i in 0 until slot) amount[i] = 1f
                if (slot in amount.indices) amount[slot] = ep
                active = slot
            } else {
                val u = 2 * count - 1 - slot
                for (i in 0 until u) amount[i] = 1f
                if (u in amount.indices) amount[u] = 1f - ep
                active = u
            }
        }
        return amount to active
    }

    private fun applyMoves(
        x0: Float, y0: Float, z0: Float,
        moves: List<Move>, amount: FloatArray, active: Int
    ): Triple<Float, Float, Float> {
        var x = x0; var y = y0; var z = z0
        var inActive = false
        for (i in moves.indices) {
            if (amount[i] <= 0f) continue
            val mv = moves[i]
            val coord = when (mv.axis) { 0 -> x; 1 -> y; else -> z }
            if (coord < mv.lo || coord >= mv.hi) continue
            if (i == active) inActive = true
            val a = mv.ang * amount[i]
            val ca = cos(a); val sa = sin(a)
            when (mv.axis) {
                0 -> { val y2 = y * ca - z * sa; z = y * sa + z * ca; y = y2 }
                1 -> { val x2 = x * ca + z * sa; z = -x * sa + z * ca; x = x2 }
                else -> { val x2 = x * ca - y * sa; y = x * sa + y * ca; x = x2 }
            }
        }
        // inActive is folded into radius by the caller via activeSlab check.
        return Triple(x, y, z)
    }

    private fun frameSolve(cloud: AgentLogoCloud, t: Float, s: AgentLogoState): List<LogoDot> {
        val n = cloud.count
        val b = beatAt(t, s.dwell, s.morph, s.turns, s.settle, s.expo)
        val m = b.m
        val c = 1f - m
        val yaw = TURN * b.turns
        val tilt = 0.36f * c
        val half = 0.62f
        val moveCount = 6
        val solveProgress = clamp01(if (b.workT < 0f) 1f else b.workT / s.dwell)
        val (amount, active) = solveCycle(solveProgress * 2f * moveCount, moveCount)
        val moves = makeCubeMoves(moveCount, half)
        val dots = ArrayList<LogoDot>(n)
        for (i in 0 until n) {
            val (fx, fy, fz) = fibDir(cloud.seats[i], n)
            val mx = max(max(abs(fx), abs(fy)), abs(fz)).coerceAtLeast(1e-6f)
            val (tx, ty, tz) = applyMoves(fx / mx * half, fy / mx * half, fz / mx * half, moves, amount, active)
            val (lx, ly, lz) = logoAt(cloud, i)
            val x = lx + (tx - lx) * c
            val y = ly + (ty - ly) * c
            val z3 = lz + (tz - lz) * c
            val (px, py, z) = project(x, y, z3, yaw, tilt)
            val zx = clamp01((z + 1f) / 2f)
            // Slab under the wrench brightens: re-derive active membership.
            var inActive = false
            val qx = fx / mx * half; val qy = fy / mx * half; val qz = fz / mx * half
            if (active >= 0 && active < moves.size) {
                val mv = moves[active]
                val coord = when (mv.axis) { 0 -> qx; 1 -> qy; else -> qz }
                inActive = coord >= mv.lo && coord < mv.hi
            }
            dots.add(
                LogoDot(
                    px, py, z,
                    r = 0.55f + 1.4f * zx + (if (inActive) 0.3f else 0f) * c,
                    white = inkOf(zx, cloud.edges[i] * m + (1f - m))
                )
            )
        }
        return finalize(dots)
    }

    // --- Listening: pulsing body (logoDeform.ts frameLogoWave) --------------

    private fun frameWave(cloud: AgentLogoCloud, t: Float, s: AgentLogoState): List<LogoDot> {
        val n = cloud.count
        val b = beatAt(t, s.dwell, s.morph, s.turns, s.settle, s.expo)
        val m = b.m
        val c = 1f - m
        val yaw = 0.42f * sin(t * 0.55f) * c
        val tilt = 0.26f * c
        val wide = 1.12f
        val tall = 0.5f
        val k1 = 3.1f
        val k2 = 6.7f
        val rate = 1.9f
        val swing = 0.52f
        val dots = ArrayList<LogoDot>(n)
        for (i in 0 until n) {
            val (fx, fy, fz) = fibDir(cloud.seats[i], n)
            val w = sin(fx * k1 - t * rate) * 0.62f + sin(fx * k2 + t * rate * 0.55f) * 0.38f
            val amp = 1f + swing * w
            val lumpy = 1f + 0.12f * (vnoise(fx * 2f + t * 0.35f, fz * 2f) - 0.5f) * 2f
            val bx = fx * wide * lumpy
            val by = fy * tall * lumpy * amp
            val bz = fz * wide * lumpy
            val (lx, ly, lz) = logoAt(cloud, i)
            val x = lx + (bx - lx) * c
            val y = ly + (by - ly) * c
            val z3 = lz + (bz - lz) * c
            val (px, py, z) = project(x, y, z3, yaw, tilt)
            val zx = clamp01((z + 1f) / 2f)
            val loud = clamp01(w * 0.5f + 0.5f)
            dots.add(
                LogoDot(
                    px, py, z,
                    r = 0.55f + 1.5f * zx + 0.3f * loud * c,
                    white = inkOf(zx, cloud.edges[i] * m + (1f - m)) - 0.14f * loud * c
                )
            )
        }
        return finalize(dots)
    }

    // --- Working: thread wound into a knot (logo.ts frameLogoWork) ----------

    private fun frameWork(cloud: AgentLogoCloud, t: Float, s: AgentLogoState): List<LogoDot> {
        val n = cloud.count
        val b = beatAt(t, s.dwell, s.morph, s.turns, s.settle, s.expo)
        val m = b.m
        val c = 1f - m
        val yaw = 0.4f + 0.3f * sin(t * 0.26f) * c
        val tilt = 0.4f * c
        val into = b.local - s.dwell
        val prog = when {
            b.local < s.dwell -> b.local / s.dwell
            into < s.morph -> 1f
            else -> clamp01(1f - (into - s.morph) / s.morph)
        }
        val head = prog * n
        val feather = max(1f, n * 0.02f)
        val headW = max(1f, n * 0.01f)
        val winding = b.local < s.dwell
        val wraps = 3f
        val knotTurns = 2f
        val major = 0.62f
        val minor = 0.3f
        val spin = t * 0.24f
        val dots = ArrayList<LogoDot>(n)
        for (i in 0 until n) {
            val seat = cloud.seats[i]
            val u = (seat.toFloat() / n) * TURN
            val ring = major + minor * cos(knotTurns * u)
            val kx = ring * cos(wraps * u)
            val ky = minor * sin(knotTurns * u)
            val kz = ring * sin(wraps * u)
            val ca = cos(spin); val sa = sin(spin)
            val bx = kx * ca + kz * sa
            val bz = -kx * sa + kz * ca
            val (lx, ly, lz) = logoAt(cloud, i)
            val x = lx + (bx - lx) * c
            val y = ly + (ky - ly) * c
            val z3 = lz + (bz - lz) * c
            val laid = clamp01((head - seat) / feather)
            val at = if (winding) exp(-(((seat - head) / headW).pow(2f))) else 0f
            val (px, py, z) = project(x, y, z3, yaw, tilt)
            val zx = clamp01((z + 1f) / 2f)
            dots.add(
                LogoDot(
                    px, py, z,
                    r = 0.55f + 1.4f * zx + 1.2f * at * c,
                    white = inkOf(zx, cloud.edges[i] * m + (1f - m)) - 0.4f * at * c,
                    alpha = 1f - (1f - laid) * c
                )
            )
        }
        return finalize(dots)
    }

    // --- Waiting: bellows of stacked rings (logoDeform.ts frameLogoWait) ----

    private fun frameWait(cloud: AgentLogoCloud, t: Float, s: AgentLogoState): List<LogoDot> {
        val n = cloud.count
        val b = beatAt(t, s.dwell, s.morph, s.turns, s.settle, s.expo)
        val m = b.m
        val c = 1f - m
        val yaw = 0.22f * sin(t * 0.3f) * c
        val tilt = 0.42f * c
        val rings = 9
        val perRing = max(1, (n + rings - 1) / rings)
        val breath = sin(t * 0.75f)
        val amp = 0.2f
        val height = 1.5f * (1f + amp * breath) // antiphase: tall <-> narrow
        val wide = 0.82f * (1f - amp * 0.72f * breath) // wide <-> short
        val spin = t * 0.16f
        val dots = ArrayList<LogoDot>(n)
        for (i in 0 until n) {
            val seat = cloud.seats[i]
            val ring = seat % rings
            val u = if (rings > 1) ring.toFloat() / (rings - 1) - 0.5f else 0f
            val taper = cos(u * PI.toFloat() * 0.78f)
            val rad = wide * taper
            val ang = (floor(seat.toFloat() / rings) / perRing) * TURN + spin
            val bx = cos(ang) * rad
            val by = u * height
            val bz = sin(ang) * rad
            val (lx, ly, lz) = logoAt(cloud, i)
            val x = lx + (bx - lx) * c
            val y = ly + (by - ly) * c
            val z3 = lz + (bz - lz) * c
            val (px, py, z) = project(x, y, z3, yaw, tilt)
            val zx = clamp01((z + 1f) / 2f)
            val loud = clamp01(taper)
            dots.add(
                LogoDot(
                    px, py, z,
                    r = 0.55f + 1.4f * zx + 0.25f * loud * c,
                    white = inkOf(zx, cloud.edges[i] * m + (1f - m)) - 0.12f * loud * c
                )
            )
        }
        return finalize(dots)
    }

    // --- Generating: crystal being stitched (logoDeform.ts frameLogoCrystal)

    private fun frameCrystal(cloud: AgentLogoCloud, t: Float, s: AgentLogoState): List<LogoDot> {
        val n = cloud.count
        val b = beatAt(t, s.dwell, s.morph, s.turns, s.settle, s.expo)
        val m = b.m
        val c = 1f - m
        val yaw = 0.5f + 0.24f * sin(t * 0.32f) * c
        val tilt = 0.2f * c
        val half = 0.94f
        val spin = t * 0.3f
        val into = b.local - s.dwell
        val prog = when {
            b.local < s.dwell -> b.local / s.dwell
            into < s.morph -> 1f
            else -> clamp01(1f - (into - s.morph) / s.morph)
        }
        val head = prog * n
        val feather = max(1f, n * 0.03f)
        val headW = max(1f, n * 0.012f)
        val stitching = b.local < s.dwell
        val dots = ArrayList<LogoDot>(n)
        for (i in 0 until n) {
            val seat = cloud.seats[i]
            val (ax, ay, az) = fibDir(seat, n)
            val l1 = max(1e-6f, abs(ax) + abs(ay) + abs(az)) // L1 -> octahedron
            val ca = cos(spin); val sa = sin(spin)
            val ox = (ax / l1) * half
            val oy = (ay / l1) * half
            val oz = (az / l1) * half
            val bx = ox * ca + oz * sa
            val bz = -ox * sa + oz * ca
            val (lx, ly, lz) = logoAt(cloud, i)
            val x = lx + (bx - lx) * c
            val y = ly + (oy - ly) * c
            val z3 = lz + (bz - lz) * c
            val done = clamp01((head - seat) / feather)
            val at = if (stitching) exp(-(((seat - head) / headW).pow(2f))) else 0f
            val (px, py, z) = project(x, y, z3, yaw, tilt)
            val zx = clamp01((z + 1f) / 2f)
            val unlit = (1f - done) * c
            dots.add(
                LogoDot(
                    px, py, z,
                    r = 0.55f + 1.4f * zx + 1.1f * at * c,
                    white = inkOf(zx, cloud.edges[i] * m + (1f - m)) + 0.3f * unlit - 0.4f * at * c,
                    tintK = 1f - unlit
                )
            )
        }
        return finalize(dots)
    }
}
