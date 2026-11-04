package com.sarvesh173.talos.ui.bot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Unit tests for [SvgPathParser]. Kotlin cannot run in every environment, so
 * the same fixtures are mirrored in tests/test_svg_path_parser.py (Python
 * reference implementation, run with `python3 -m pytest tests/ -q`).
 */
class SvgPathParserTest {

    private fun bbox(contour: List<Pt>): FloatArray {
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (p in contour) {
            minX = minOf(minX, p.x); maxX = maxOf(maxX, p.x)
            minY = minOf(minY, p.y); maxY = maxOf(maxY, p.y)
        }
        return floatArrayOf(minX, maxX, minY, maxY)
    }

    /** Mirrors AgentLogoBaker.inside() for the even-odd fill rule. */
    private fun insideEvenOdd(x: Float, y: Float, contours: List<List<Pt>>): Boolean {
        var crossings = 0
        for (c in contours) {
            for (i in c.indices) {
                val a = c[i]
                val b = c[(i + 1) % c.size]
                if ((a.y <= y) != (b.y <= y)) {
                    val xi = a.x + (b.x - a.x) * (y - a.y) / (b.y - a.y)
                    if (xi > x) crossings++
                }
            }
        }
        return crossings % 2 == 1
    }

    @Test
    fun triangleFullSvg() {
        val r = SvgPathParser.parseSvg(
            "<svg viewBox=\"0 0 100 100\"><path d=\"M10 90 L50 10 L90 90 Z\"/></svg>"
        )
        assertTrue(r.ok)
        assertTrue(!r.truncated)
        assertEquals(1, r.contours.size)
        val c = r.contours[0]
        assertEquals(Pt(10f, 90f), c[0])
        assertTrue(c.contains(Pt(50f, 10f)))
        assertTrue(c.contains(Pt(90f, 90f)))
        assertEquals(c[0], c.last()) // Z closes to subpath start
        val box = bbox(c)
        assertEquals(10f, box[0], 1e-6f)
        assertEquals(90f, box[1], 1e-6f)
        assertEquals(10f, box[2], 1e-6f)
        assertEquals(90f, box[3], 1e-6f)
        assertTrue(insideEvenOdd(50f, 70f, r.contours))
        assertTrue(!insideEvenOdd(15f, 15f, r.contours))
    }

    @Test
    fun roundedRectViaArcs() {
        val d = "M20 10 H80 A10 10 0 0 1 90 20 V80 A10 10 0 0 1 80 90 " +
            "H20 A10 10 0 0 1 10 80 V20 A10 10 0 0 1 20 10 Z"
        val r = SvgPathParser.parseSvg(
            "<svg viewBox=\"0 0 100 100\"><path d=\"$d\"/></svg>"
        )
        assertTrue(r.ok)
        assertEquals(1, r.contours.size)
        val c = r.contours[0]
        val box = bbox(c)
        assertEquals(10f, box[0], 1e-6f)
        assertEquals(90f, box[1], 1e-6f)
        assertEquals(10f, box[2], 1e-6f)
        assertEquals(90f, box[3], 1e-6f)
        assertTrue(c.size > 12) // arcs flatten to more than corner points
        val diag = Pt(80f + 10f / sqrt(2f), 20f - 10f / sqrt(2f))
        assertTrue(c.any { kotlin.math.abs(it.x - diag.x) < 0.6f && kotlin.math.abs(it.y - diag.y) < 0.6f })
        assertTrue(insideEvenOdd(50f, 50f, r.contours))
        assertTrue(!insideEvenOdd(11f, 11f, r.contours))
    }

    @Test
    fun cubicCircleApproximation() {
        val k = 0.5522847498f * 40f
        val d = "M90 50 C90 ${50 + k} ${50 + k} 90 50 90 " +
            "C${50 - k} 90 10 ${50 + k} 10 50 " +
            "C10 ${50 - k} ${50 - k} 10 50 10 " +
            "C${50 + k} 10 90 ${50 - k} 90 50 Z"
        val r = SvgPathParser.parseSvg(d)
        assertTrue(r.ok)
        val c = r.contours[0]
        assertTrue(c.size in 8..60) // adaptive: far below 4x10 fixed samples
        val box = bbox(c)
        assertEquals(10f, box[0], 0.5f)
        assertEquals(90f, box[1], 0.5f)
        assertEquals(10f, box[2], 0.5f)
        assertEquals(90f, box[3], 0.5f)
        for (p in c) {
            val rad = sqrt((p.x - 50f) * (p.x - 50f) + (p.y - 50f) * (p.y - 50f))
            assertTrue(rad in 38.5f..41f)
        }
        assertTrue(insideEvenOdd(50f, 50f, r.contours))
    }

    @Test
    fun straightCubicIsOneSegment() {
        val r = SvgPathParser.parseSvg("M0 0 C3 0 7 0 10 0 L10 10")
        assertTrue(r.ok)
        assertEquals(listOf(Pt(0f, 0f), Pt(10f, 0f), Pt(10f, 10f)), r.contours[0])
    }

    @Test
    fun rotateTransformWithCentre() {
        val r = SvgPathParser.parseSvg(
            "<svg><path transform=\"rotate(90 50 50)\" d=\"M10 10 L30 10 L30 30 Z\"/></svg>"
        )
        assertTrue(r.ok)
        val c = r.contours[0]
        assertEquals(90f, c[0].x, 1e-4f)
        assertEquals(10f, c[0].y, 1e-4f)
        assertEquals(90f, c[1].x, 1e-4f)
        assertEquals(30f, c[1].y, 1e-4f)
    }

    @Test
    fun matrixAndSkewTransforms() {
        val r = SvgPathParser.parseSvg(
            "<svg><path transform=\"matrix(2 0 0 3 5 7)\" d=\"M1 1 L2 1 L2 2 Z\"/></svg>"
        )
        assertTrue(r.ok)
        assertEquals(Pt(7f, 10f), r.contours[0][0])
        val s = SvgPathParser.parseSvg(
            "<svg><path transform=\"skewX(45)\" d=\"M0 0 L10 0 L10 10 Z\"/></svg>"
        )
        assertTrue(s.ok)
        assertTrue(s.contours[0].contains(Pt(20f, 10f)))
    }

    @Test
    fun nestedGroupTransforms() {
        val r = SvgPathParser.parseSvg(
            "<svg><g transform=\"translate(10 20)\"><g transform=\"scale(2)\">" +
                "<path d=\"M0 0 L10 0 L10 10 Z\"/></g></g></svg>"
        )
        assertTrue(r.ok)
        val c = r.contours[0]
        assertEquals(Pt(10f, 20f), c[0])
        assertEquals(Pt(30f, 20f), c[1])
        assertEquals(Pt(30f, 40f), c[2])
    }

    @Test
    fun viewBoxNormalisation() {
        val r = SvgPathParser.parseSvg(
            "<svg viewBox=\"10 20 80 80\"><path d=\"M10 20 L90 20 L90 100 Z\"/></svg>"
        )
        assertTrue(r.ok)
        assertEquals(Pt(0f, 0f), r.contours[0][0])
    }

    @Test
    fun evenOddDonutHoleStaysHole() {
        val d = "M10 10 H90 V90 H10 Z M30 30 H70 V70 H30 Z"
        val r = SvgPathParser.parseSvg(
            "<svg><path fill-rule=\"evenodd\" d=\"$d\"/></svg>"
        )
        assertTrue(r.ok)
        assertEquals("evenOdd", r.fillType)
        assertEquals(2, r.contours.size)
        assertTrue(insideEvenOdd(20f, 20f, r.contours))
        assertTrue(!insideEvenOdd(50f, 50f, r.contours))
        assertTrue(!insideEvenOdd(95f, 95f, r.contours))
    }

    @Test
    fun rawDString() {
        val r = SvgPathParser.parseSvg("M0 0 L10 0 L0 10 Z")
        assertTrue(r.ok)
        assertEquals(1, r.contours.size)
        assertEquals(4, r.contours[0].size)
        assertEquals(Pt(0f, 0f), r.contours[0][0])
        assertEquals(r.contours[0][0], r.contours[0].last())
    }

    @Test
    fun barePathAndRelativeCommands() {
        val r = SvgPathParser.parseSvg("<path d=\"m5 5 l10 0 l0 10 z\"/>")
        assertTrue(r.ok)
        assertEquals(Pt(5f, 5f), r.contours[0][0])
        assertEquals(Pt(15f, 5f), r.contours[0][1])
        assertEquals(Pt(15f, 15f), r.contours[0][2])
    }

    @Test
    fun quadSmoothCommands() {
        val r = SvgPathParser.parseSvg("M0 0 Q5 10 10 0 T20 0 L20 10 Z")
        assertTrue(r.ok)
        assertEquals(Pt(0f, 0f), r.contours[0][0])
        assertTrue(r.contours[0].any { kotlin.math.abs(it.x - 5f) < 0.5f && kotlin.math.abs(it.y - 5f) < 0.5f })
        val r2 = SvgPathParser.parseSvg("M0 0 C0 10 10 10 10 0 S20 -10 20 0 L20 5 Z")
        assertTrue(r2.ok)
    }

    @Test
    fun strokeOnlyPathIgnored() {
        val r = SvgPathParser.parseSvg(
            "<svg><path fill=\"none\" stroke=\"#000\" d=\"M0 0 L10 0 L10 10 Z\"/></svg>"
        )
        assertTrue(!r.ok)
        assertNotNull(r.error)
        assertTrue(r.contours.isEmpty())
    }

    @Test
    fun malformedReturnsErrorNotThrow() {
        val bad = listOf(
            "",
            "M0",
            "M0 0 Lx y",
            "X10 10",
            "garbage",
            "<svg></svg>",
            "<svg><path d=\"M0 0 L10\"/></svg>"
        )
        for (source in bad) {
            val r = SvgPathParser.parseSvg(source)
            assertTrue("expected error for: $source", r.error != null)
            assertTrue(r.contours.isEmpty())
        }
    }

    @Test
    fun pathologicalPathTruncatedNotHung() {
        val d = buildString {
            append("M0 0 ")
            for (i in 1..100_000) append("L$i ${i % 7} ")
        }
        val r = SvgPathParser.parseSvg(d)
        assertTrue(r.truncated)
        assertTrue(r.contours.isNotEmpty())
        val total = r.contours.sumOf { it.size }
        assertTrue(total <= SvgPathParser.MAX_POINTS + 2)
    }

    @Test
    fun bakeSmokeTest() {
        val cloud = SvgPathParser.bakeFromSvg(
            "<svg viewBox=\"0 0 100 100\"><path d=\"M10 90 L50 10 L90 90 Z\"/></svg>"
        )
        assertTrue(cloud.count > 0)
        assertEquals(cloud.count * 3, cloud.points.size)
    }
}
