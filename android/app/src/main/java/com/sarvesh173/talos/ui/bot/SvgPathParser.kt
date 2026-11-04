package com.sarvesh173.talos.ui.bot

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Arbitrary-SVG path parser: turns user-supplied SVG markup (or a bare
 * `<path d="..."/>`, or a raw `d` string) into the [Pt] contours that
 * [AgentLogoBaker.bakeFromContours] consumes, so any dropped/paasted logo
 * can be baked into the point-cloud pipeline. Pure Kotlin, no Android or
 * Compose types — unit-testable on the JVM.
 *
 * Conventions (identical to the baker's input contract):
 * - y down, viewport coords; the baker centres/scales by bounding box.
 * - `fillType` mirrors the baker: "nonZero" (SVG default) or "evenOdd".
 * - The viewBox min is subtracted (outermost viewport mapping, applied
 *   after each element's transform chain) so marks start near the origin.
 * - Curves/arcs are flattened ADAPTIVELY (recursive midpoint-deviation
 *   subdivision): a straight line costs one segment, a tight curve more.
 * - Total sampled points are capped at [MAX_POINTS]; when the cap is hit
 *   parsing stops, partial geometry is kept, and [SvgParseResult.truncated]
 *   reports it. Malformed input returns an error result, never throws.
 *
 * Python reference with the same algorithm and fixtures:
 * tests/svg_path_parser_ref.py + tests/test_svg_path_parser.py.
 */
object SvgPathParser {

    const val MAX_POINTS = 5000
    const val MAX_DEPTH = 12
    const val DEFAULT_TOLERANCE = 0.25f

    /** Result of [parseSvg]: contours ready for [AgentLogoBaker.bakeFromContours]. */
    class SvgParseResult(
        val contours: List<List<Pt>>,
        val fillType: String = "nonZero",
        val truncated: Boolean = false,
        val error: String? = null
    ) {
        val ok: Boolean
            get() = error == null

        /** Bake directly into the point cloud (empty/error input -> empty cloud). */
        fun toCloud(
            count: Int = AgentLogoBaker.DEFAULT_COUNT,
            seed: Long = AgentLogoBaker.DEFAULT_SEED,
            depth: Float = AgentLogoBaker.DEFAULT_DEPTH
        ): AgentLogoCloud =
            AgentLogoBaker.bakeFromContours(contours, count, seed, depth, fillType)
    }

    /** One-shot: parse [source] and bake it. Parse errors yield an empty cloud. */
    fun bakeFromSvg(
        source: String,
        count: Int = AgentLogoBaker.DEFAULT_COUNT,
        seed: Long = AgentLogoBaker.DEFAULT_SEED,
        depth: Float = AgentLogoBaker.DEFAULT_DEPTH,
        tolerance: Float = DEFAULT_TOLERANCE
    ): AgentLogoCloud = parseSvg(source, tolerance).toCloud(count, seed, depth)

    /**
     * Parse [source] as: a full `<svg>...</svg>` document, a bare
     * `<path .../>` element, or a raw path-data (`d`) string.
     * Never throws on bad input — see [SvgParseResult.error].
     */
    fun parseSvg(source: String, tolerance: Float = DEFAULT_TOLERANCE): SvgParseResult {
        val src = source.trim()
        if (src.isEmpty()) return SvgParseResult(emptyList(), error = "empty input")
        if ('<' !in src) {
            return try {
                val contours = parsePathData(src, tolerance, Budget())
                    .filter { it.size >= 3 }
                if (contours.isEmpty()) {
                    SvgParseResult(emptyList(), error = "no contours (need >= 3 points)")
                } else {
                    SvgParseResult(contours)
                }
            } catch (e: BudgetExceeded) {
                val partial = e.partial.filter { it.size >= 3 }
                if (partial.isEmpty()) {
                    SvgParseResult(emptyList(), error = "no contours (need >= 3 points)")
                } else {
                    SvgParseResult(partial, truncated = true)
                }
            } catch (e: ParseError) {
                SvgParseResult(emptyList(), error = e.message)
            }
        }

        val contours = ArrayList<List<Pt>>()
        var fillType = "nonZero"
        var seenPath = false
        var viewBoxOffX = 0f
        var viewBoxOffY = 0f
        var truncated = false
        val budget = Budget()
        val groupStack = ArrayList<Pair<Mat, String?>>()
        try {
            for (tagMatch in TAG.findAll(src)) {
                var inner = tagMatch.value.substring(1, tagMatch.value.length - 1).trim()
                if (inner.isEmpty() || inner.startsWith("?") || inner.startsWith("!")) continue
                val closing = inner.startsWith("/")
                if (closing) inner = inner.substring(1).trim()
                val selfClosing = inner.endsWith("/")
                if (selfClosing) inner = inner.substring(0, inner.length - 1).trim()
                val name = inner.takeWhile { !it.isWhitespace() && it != '/' }
                if (name.isEmpty()) continue
                val attrs = HashMap<String, String>()
                for (attrMatch in ATTR.findAll(inner.substring(name.length))) {
                    val value = if (attrMatch.groupValues[2].isNotEmpty())
                        attrMatch.groupValues[2] else attrMatch.groupValues[3]
                    attrs[attrMatch.groupValues[1]] = value
                }
                when (name.substringAfterLast(":")) {
                    "svg" -> {
                        if (!closing) {
                            val vb = numbersIn(attrs["viewBox"] ?: "")
                            if (vb.size >= 4) {
                                viewBoxOffX = vb[0]
                                viewBoxOffY = vb[1]
                            }
                            fillType = fillRuleOf(attrs["fill-rule"], fillType)
                        }
                    }
                    "g" -> {
                        if (closing) {
                            if (groupStack.isNotEmpty())
                                groupStack.removeAt(groupStack.size - 1)
                        } else if (!selfClosing) {
                            val mat = attrs["transform"]?.let { parseTransform(it) } ?: IDENTITY
                            groupStack.add(mat to attrs["fill-rule"]?.trim()?.lowercase())
                        }
                    }
                    "path" -> {
                        if (closing) continue
                        seenPath = true
                        if (isFillNone(attrs)) continue
                        val d = attrs["d"] ?: ""
                        if (d.isBlank()) continue
                        var rule = attrs["fill-rule"]?.trim()?.lowercase()
                        if (rule.isNullOrEmpty()) {
                            for (i in groupStack.indices.reversed()) {
                                if (!groupStack[i].second.isNullOrEmpty()) {
                                    rule = groupStack[i].second
                                    break
                                }
                            }
                        }
                        fillType = fillRuleOf(rule, fillType)
                        var mat = IDENTITY
                        for ((groupMat, _) in groupStack) mat = mat.mul(groupMat)
                        attrs["transform"]?.let { mat = mat.mul(parseTransform(it)) }
                        val (raw, hitCap) = cappedContours(d, tolerance, budget)
                        if (hitCap) truncated = true
                        for (contour in raw) {
                            contours.add(contour.map { p ->
                                val q = mat.apply(p)
                                Pt(q.x - viewBoxOffX, q.y - viewBoxOffY)
                            })
                        }
                        if (truncated) break // point budget spent; stop parsing
                    }
                }
            }
        } catch (e: ParseError) {
            return SvgParseResult(emptyList(), error = e.message)
        }
        if (!seenPath) return SvgParseResult(emptyList(), error = "no <path> elements found")
        val kept = contours.filter { it.size >= 3 }
        if (kept.isEmpty()) {
            return SvgParseResult(
                emptyList(), fillType, truncated,
                "no fill contours (all paths stroke-only or empty)"
            )
        }
        return SvgParseResult(kept, fillType, truncated)
    }

    // --- path-data parsing ---------------------------------------------------

    private class ParseError(message: String) : Exception(message)

    private class BudgetExceeded(val partial: MutableList<List<Pt>>) : Exception()

    private class Budget(var used: Int = 0)

    private class Tokens(val s: String) {
        var pos = 0

        fun skipSep() {
            while (pos < s.length && s[pos] in " \t\r\n,") pos++
        }

        fun number(): Float {
            skipSep()
            val m = NUMBER.find(s, pos)
            if (m == null || m.range.first != pos) {
                throw ParseError("expected number at offset $pos in $s")
            }
            pos = m.range.last + 1
            return m.value.toFloat()
        }

        fun flag(): Boolean {
            skipSep()
            if (pos < s.length && (s[pos] == '0' || s[pos] == '1')) {
                val v = s[pos] == '1'
                pos++
                return v
            }
            throw ParseError("expected arc flag at offset $pos in $s")
        }
    }

    private fun cappedContours(d: String, tolerance: Float, budget: Budget): Pair<List<List<Pt>>, Boolean> {
        return try {
            parsePathData(d, tolerance, budget) to false
        } catch (e: BudgetExceeded) {
            e.partial to true
        }
    }

    /** Flatten one `d` attribute into contours (y down, viewport coords). */
    private fun parsePathData(d: String, tolerance: Float, budget: Budget): List<List<Pt>> {
        val tok = Tokens(d)
        val contours = ArrayList<List<Pt>>()
        var current = ArrayList<Pt>()
        var cur = Pt(0f, 0f)
        var start = Pt(0f, 0f)
        var prevC: Pt? = null
        var prevQ: Pt? = null
        var cmd: Char? = null

        fun emit(p: Pt) {
            if (budget.used >= MAX_POINTS) {
                if (current.isNotEmpty()) contours.add(current)
                throw BudgetExceeded(contours)
            }
            budget.used++
            current.add(p)
        }

        fun adj(vx: Float, vy: Float, rel: Boolean): Pt =
            if (rel) Pt(cur.x + vx, cur.y + vy) else Pt(vx, vy)

        try {
            while (true) {
                tok.skipSep()
                if (tok.pos >= d.length) break
                val ch = d[tok.pos]
                if (ch.isLetter()) {
                    if (ch !in COMMANDS) throw ParseError("unknown path command '$ch' in $d")
                    cmd = ch
                    tok.pos++
                    if (cmd == 'Z' || cmd == 'z') {
                        if (current.isNotEmpty()) emit(start)
                        cur = start
                        prevC = null
                        prevQ = null
                        cmd = null
                        continue
                    }
                }
                val active = cmd ?: throw ParseError("path data without command: $d")
                val rel = active.isLowerCase()
                when (active.uppercaseChar()) {
                    'M' -> {
                        val x = tok.number(); val y = tok.number()
                        cur = adj(x, y, rel)
                        start = cur
                        if (current.isNotEmpty()) contours.add(current)
                        current = ArrayList()
                        emit(cur)
                        prevC = null; prevQ = null
                        cmd = if (rel) 'l' else 'L' // implicit lineto
                    }
                    'L' -> {
                        cur = adj(tok.number(), tok.number(), rel)
                        emit(cur)
                        prevC = null; prevQ = null
                    }
                    'H' -> {
                        val x = tok.number()
                        cur = if (rel) Pt(cur.x + x, cur.y) else Pt(x, cur.y)
                        emit(cur)
                        prevC = null; prevQ = null
                    }
                    'V' -> {
                        val y = tok.number()
                        cur = if (rel) Pt(cur.x, cur.y + y) else Pt(cur.x, y)
                        emit(cur)
                        prevC = null; prevQ = null
                    }
                    'C' -> {
                        val c1 = adj(tok.number(), tok.number(), rel)
                        val c2 = adj(tok.number(), tok.number(), rel)
                        val end = adj(tok.number(), tok.number(), rel)
                        flatCubic(cur, c1, c2, end, tolerance, 0) { emit(it) }
                        prevC = c2; prevQ = null
                        cur = end
                    }
                    'S' -> {
                        val c1 = prevC?.let { Pt(2f * cur.x - it.x, 2f * cur.y - it.y) } ?: cur
                        val c2 = adj(tok.number(), tok.number(), rel)
                        val end = adj(tok.number(), tok.number(), rel)
                        flatCubic(cur, c1, c2, end, tolerance, 0) { emit(it) }
                        prevC = c2; prevQ = null
                        cur = end
                    }
                    'Q' -> {
                        val q = adj(tok.number(), tok.number(), rel)
                        val end = adj(tok.number(), tok.number(), rel)
                        flatQuad(cur, q, end, tolerance, 0) { emit(it) }
                        prevQ = q; prevC = null
                        cur = end
                    }
                    'T' -> {
                        val q = prevQ?.let { Pt(2f * cur.x - it.x, 2f * cur.y - it.y) } ?: cur
                        val end = adj(tok.number(), tok.number(), rel)
                        flatQuad(cur, q, end, tolerance, 0) { emit(it) }
                        prevQ = q; prevC = null
                        cur = end
                    }
                    'A' -> {
                        val rx = tok.number(); val ry = tok.number(); val rot = tok.number()
                        val large = tok.flag(); val sweep = tok.flag()
                        val end = adj(tok.number(), tok.number(), rel)
                        val arc = arcParams(cur, rx, ry, rot, large, sweep, end)
                        if (arc == null) {
                            emit(end)
                        } else {
                            flatArc(arc, arc.theta1, arc.theta1 + arc.dtheta, tolerance, 0) { emit(it) }
                        }
                        prevC = null; prevQ = null
                        cur = end
                    }
                    else -> throw ParseError("unknown path command '$active'")
                }
            }
        } catch (e: BudgetExceeded) {
            // `current` was already folded into e.partial by emit()
            throw e
        }
        if (current.isNotEmpty()) contours.add(current)
        return contours
    }

    // --- adaptive flattening ---------------------------------------------------

    private fun dist(a: Pt, b: Pt): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun cubicAt(p0: Pt, c1: Pt, c2: Pt, p1: Pt, t: Float): Pt {
        val u = 1f - t
        return Pt(
            u * u * u * p0.x + 3f * u * u * t * c1.x + 3f * u * t * t * c2.x + t * t * t * p1.x,
            u * u * u * p0.y + 3f * u * u * t * c1.y + 3f * u * t * t * c2.y + t * t * t * p1.y
        )
    }

    private fun quadAt(p0: Pt, q: Pt, p1: Pt, t: Float): Pt {
        val u = 1f - t
        return Pt(
            u * u * p0.x + 2f * u * t * q.x + t * t * p1.x,
            u * u * p0.y + 2f * u * t * q.y + t * t * p1.y
        )
    }

    private fun flatCubic(
        p0: Pt, c1: Pt, c2: Pt, p1: Pt, tolerance: Float, depth: Int,
        emit: (Pt) -> Unit
    ) {
        if (depth >= MAX_DEPTH) {
            emit(p1)
            return
        }
        val mid = cubicAt(p0, c1, c2, p1, 0.5f)
        val chordMid = Pt((p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)
        if (dist(mid, chordMid) <= tolerance) {
            emit(p1)
            return
        }
        val m01 = Pt((p0.x + c1.x) / 2f, (p0.y + c1.y) / 2f)
        val m12 = Pt((c1.x + c2.x) / 2f, (c1.y + c2.y) / 2f)
        val m23 = Pt((c2.x + p1.x) / 2f, (c2.y + p1.y) / 2f)
        val m012 = Pt((m01.x + m12.x) / 2f, (m01.y + m12.y) / 2f)
        val m123 = Pt((m12.x + m23.x) / 2f, (m12.y + m23.y) / 2f)
        val m = Pt((m012.x + m123.x) / 2f, (m012.y + m123.y) / 2f)
        flatCubic(p0, m01, m012, m, tolerance, depth + 1, emit)
        flatCubic(m, m123, m23, p1, tolerance, depth + 1, emit)
    }

    private fun flatQuad(
        p0: Pt, q: Pt, p1: Pt, tolerance: Float, depth: Int,
        emit: (Pt) -> Unit
    ) {
        if (depth >= MAX_DEPTH) {
            emit(p1)
            return
        }
        val mid = quadAt(p0, q, p1, 0.5f)
        val chordMid = Pt((p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)
        if (dist(mid, chordMid) <= tolerance) {
            emit(p1)
            return
        }
        val m01 = Pt((p0.x + q.x) / 2f, (p0.y + q.y) / 2f)
        val m12 = Pt((q.x + p1.x) / 2f, (q.y + p1.y) / 2f)
        val m = Pt((m01.x + m12.x) / 2f, (m01.y + m12.y) / 2f)
        flatQuad(p0, m01, m, tolerance, depth + 1, emit)
        flatQuad(m, m12, p1, tolerance, depth + 1, emit)
    }

    private class ArcParams(
        val cx: Float, val cy: Float, val rx: Float, val ry: Float,
        val phi: Float, val theta1: Float, val dtheta: Float
    )

    /** Endpoint -> centre arc conversion (SVG 1.1 F.6.5); null if degenerate. */
    private fun arcParams(
        from: Pt, rxIn: Float, ryIn: Float, rotDeg: Float,
        large: Boolean, sweep: Boolean, to: Pt
    ): ArcParams? {
        var rx = abs(rxIn)
        var ry = abs(ryIn)
        if (rx < 1e-9f || ry < 1e-9f) return null
        val phi = rotDeg * PI.toFloat() / 180f
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)
        val dx = (from.x - to.x) / 2f
        val dy = (from.y - to.y) / 2f
        val x1p = cosPhi * dx + sinPhi * dy
        val y1p = -sinPhi * dx + cosPhi * dy
        val lambda = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry)
        if (lambda > 1f) {
            val s = sqrt(lambda)
            rx *= s
            ry *= s
        }
        val num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
        val den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        var co = if (den > 0f) sqrt(max(0f, num / den)) else 0f
        if (large == sweep) co = -co
        val cxp = co * rx * y1p / ry
        val cyp = -co * ry * x1p / rx
        val cx = cosPhi * cxp - sinPhi * cyp + (from.x + to.x) / 2f
        val cy = sinPhi * cxp + cosPhi * cyp + (from.y + to.y) / 2f
        val theta1 = atan2((y1p - cyp) / ry, (x1p - cxp) / rx)
        var dtheta = atan2((-y1p - cyp) / ry, (-x1p - cxp) / rx) - theta1
        if (!sweep && dtheta > 0f) dtheta -= 2f * PI.toFloat()
        if (sweep && dtheta < 0f) dtheta += 2f * PI.toFloat()
        return ArcParams(cx, cy, rx, ry, phi, theta1, dtheta)
    }

    private fun arcPoint(arc: ArcParams, theta: Float): Pt {
        val cosPhi = cos(arc.phi)
        val sinPhi = sin(arc.phi)
        return Pt(
            arc.cx + arc.rx * cos(theta) * cosPhi - arc.ry * sin(theta) * sinPhi,
            arc.cy + arc.rx * cos(theta) * sinPhi + arc.ry * sin(theta) * cosPhi
        )
    }

    private fun flatArc(
        arc: ArcParams, t1: Float, t2: Float, tolerance: Float, depth: Int,
        emit: (Pt) -> Unit
    ) {
        val p1 = arcPoint(arc, t1)
        val p2 = arcPoint(arc, t2)
        if (depth >= MAX_DEPTH) {
            emit(p2)
            return
        }
        val mid = arcPoint(arc, (t1 + t2) / 2f)
        val chordMid = Pt((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
        if (dist(mid, chordMid) <= tolerance) {
            emit(p2)
            return
        }
        val tm = (t1 + t2) / 2f
        flatArc(arc, t1, tm, tolerance, depth + 1, emit)
        flatArc(arc, tm, t2, tolerance, depth + 1, emit)
    }

    // --- transforms --------------------------------------------------------------

    /** 2D affine: x' = a*x + c*y + e, y' = b*x + d*y + f. */
    private class Mat(
        val a: Float, val b: Float, val c: Float, val d: Float, val e: Float, val f: Float
    ) {
        fun mul(o: Mat): Mat = Mat(
            a * o.a + c * o.b,
            b * o.a + d * o.b,
            a * o.c + c * o.d,
            b * o.c + d * o.d,
            a * o.e + c * o.f + e,
            b * o.e + d * o.f + f
        )

        fun apply(p: Pt): Pt = Pt(a * p.x + c * p.y + e, b * p.x + d * p.y + f)
    }

    private val IDENTITY = Mat(1f, 0f, 0f, 1f, 0f, 0f)

    private fun parseTransform(attr: String): Mat {
        var result = IDENTITY
        var pos = 0
        for (match in TRANSFORM.findAll(attr)) {
            val junk = attr.substring(pos, match.range.first)
            if (junk.any { it !in " \t\r\n," }) throw ParseError("bad transform: $attr")
            val args = numbersIn(match.groupValues[2])
            val m = when (match.groupValues[1]) {
                "translate" -> {
                    if (args.isEmpty()) throw ParseError("translate needs tx")
                    Mat(1f, 0f, 0f, 1f, args[0], args.getOrElse(1) { 0f })
                }
                "scale" -> {
                    if (args.isEmpty()) throw ParseError("scale needs sx")
                    Mat(args[0], 0f, 0f, args.getOrElse(1) { args[0] }, 0f, 0f)
                }
                "rotate" -> {
                    if (args.isEmpty()) throw ParseError("rotate needs angle")
                    val cx = args.getOrElse(1) { 0f }
                    val cy = args.getOrElse(2) { 0f }
                    val r = args[0] * PI.toFloat() / 180f
                    val cos = cos(r)
                    val sin = sin(r)
                    Mat(1f, 0f, 0f, 1f, cx, cy)
                        .mul(Mat(cos, sin, -sin, cos, 0f, 0f))
                        .mul(Mat(1f, 0f, 0f, 1f, -cx, -cy))
                }
                "matrix" -> {
                    if (args.size != 6) throw ParseError("matrix needs 6 numbers")
                    Mat(args[0], args[1], args[2], args[3], args[4], args[5])
                }
                "skewX" -> {
                    if (args.isEmpty()) throw ParseError("skewX needs angle")
                    Mat(1f, 0f, tan(args[0] * PI.toFloat() / 180f), 1f, 0f, 0f)
                }
                "skewY" -> {
                    if (args.isEmpty()) throw ParseError("skewY needs angle")
                    Mat(1f, tan(args[0] * PI.toFloat() / 180f), 0f, 1f, 0f, 0f)
                }
                else -> throw ParseError("unsupported transform: ${match.groupValues[1]}")
            }
            result = result.mul(m)
            pos = match.range.last + 1
        }
        if (attr.substring(pos).any { it !in " \t\r\n," }) {
            throw ParseError("bad transform: $attr")
        }
        return result
    }

    // --- small helpers -------------------------------------------------------------

    private fun numbersIn(s: String): List<Float> =
        NUMBER.findAll(s).map { it.value.toFloat() }.toList()

    private fun fillRuleOf(rule: String?, current: String): String = when (rule) {
        "evenodd" -> "evenOdd"
        "nonzero" -> "nonZero"
        else -> current
    }

    private fun isFillNone(attrs: Map<String, String>): Boolean {
        if (attrs["fill"]?.trim()?.lowercase() == "none") return true
        val style = attrs["style"]?.replace(" ", "") ?: return false
        return style.contains("fill:none")
    }

    private val NUMBER = Regex("[+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?")
    private val TAG = Regex("<[^>]*>")
    private val ATTR = Regex("([A-Za-z_:][\\w:.-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")
    private val TRANSFORM = Regex("([A-Za-z]+)\\s*\\(([^)]*)\\)")
    private val COMMANDS = "MmLlHhVvCcSsQqTtAaZz".toSet()
}
