package com.serendeep.marginalia.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class ShapeSnapTest {

    private fun snapped(points: List<InkPt>) = ShapeSnap.snap(Traces.hold(points))

    @Test
    fun straightLineWithHoldBecomesTwoEndpointLine() {
        val raw = Traces.walk(listOf(50f to 400f, 350f to 380f), noise = 1.5f)
        val out = snapped(raw)!!
        assertEquals(raw.first().x, out.first().x, 3f)
        assertEquals(out.first().y, out.first().y, 0.01f)
        // Every synthesized point sits on the chord between the endpoints.
        val a = out.first()
        val b = out.last()
        out.forEach { assertTrue(distanceToLine(it, a, b) < 0.5f) }
        assertTrue(maxStep(out) <= ShapeSnap.SPACING_PX + 0.01f)
    }

    @Test
    fun holdTailIsStrippedFromTheOutput() {
        val raw = Traces.walk(listOf(50f to 400f, 350f to 400f))
        val out = snapped(raw)!!
        assertEquals(raw.last().x, out.last().x, 3f)
    }

    @Test
    fun noHoldMeansNoSnap() {
        assertNull(ShapeSnap.snap(Traces.walk(listOf(50f to 400f, 350f to 400f))))
        assertNull(ShapeSnap.snap(Traces.circle(300f, 300f, 80f)))
    }

    @Test
    fun shortHoldIsNotEnough() {
        val raw = Traces.walk(listOf(50f to 400f, 350f to 400f))
        assertNull(ShapeSnap.snap(Traces.hold(raw, ms = 300L)))
    }

    @Test
    fun slowDriftIsNotAHold() {
        val raw = Traces.walk(listOf(50f to 400f, 350f to 400f), msPerPoint = 8)
        val drifting = raw + (1..10).map { InkPt(raw.last().x + it * 8f, raw.last().y, raw.last().t + it * 80L) }
        assertNull(ShapeSnap.snap(drifting))
    }

    @Test
    fun arrowKeepsShaftAndBothHeadSegments() {
        val out = snapped(Traces.arrow())!!
        val box = Extent.of(out)
        assertEquals(100f, box.left, 4f)
        assertEquals(400f, box.right, 4f)
        // The head fans out on both sides of the shaft.
        assertTrue(box.top < 280f)
        assertTrue(box.bottom > 320f)
    }

    @Test
    fun circleBecomesAClosedRound() {
        val out = snapped(Traces.circle(300f, 300f, 80f))!!
        val box = Extent.of(out)
        assertEquals(160f, box.width, 8f)
        assertEquals(160f, box.height, 8f)
        out.forEach { assertEquals(80f, hypot(it.x - 300f, it.y - 300f), 8f) }
        assertEquals(out.first().x, out.last().x, 0.5f)
        assertEquals(out.first().y, out.last().y, 0.5f)
    }

    @Test
    fun ellipseKeepsItsAspect() {
        val out = snapped(Traces.ellipse(300f, 300f, 120f, 60f))!!
        val box = Extent.of(out)
        assertEquals(240f, box.width, 10f)
        assertEquals(120f, box.height, 10f)
    }

    @Test
    fun squareBecomesAxisAlignedRectangle() {
        val sq = listOf(100f to 100f, 260f to 104f, 258f to 262f, 98f to 258f)
        val out = snapped(Traces.polygon(sq))!!
        val box = Extent.of(out)
        assertEquals(160f, box.width, 10f)
        assertEquals(160f, box.height, 10f)
        // Straight edges: almost every point lies on the bounding box outline.
        val onEdge = out.count {
            abs(it.x - box.left) < 0.5f || abs(it.x - box.right) < 0.5f ||
                abs(it.y - box.top) < 0.5f || abs(it.y - box.bottom) < 0.5f
        }
        assertEquals(out.size, onEdge)
    }

    @Test
    fun wideRectangleSnaps() {
        val rect = listOf(80f to 100f, 380f to 100f, 380f to 200f, 80f to 200f)
        val out = snapped(Traces.polygon(rect))!!
        val box = Extent.of(out)
        assertEquals(300f, box.width, 10f)
        assertEquals(100f, box.height, 10f)
    }

    @Test
    fun clearlyRotatedRectangleStaysRotated() {
        val base = listOf(100f to 100f, 300f to 100f, 300f to 200f, 100f to 200f)
        val rot = Traces.rotated(base, 30f, 200f, 150f)
        val out = snapped(Traces.polygon(rot))!!
        val box = Extent.of(out)
        // An axis-aligned fit of this shape would be 100*cos+200*sin wide in the unrotated sense; a rotated one is wider than the sides.
        assertTrue(box.width > 215f)
        val horizontalEdges = out.zipWithNext().count { (a, b) -> abs(b.y - a.y) < 0.01f && abs(b.x - a.x) > 1f }
        assertTrue("rotated rectangle has no axis-aligned runs", horizontalEdges < out.size / 10)
    }

    @Test
    fun slightlyTiltedRectangleIsStraightened() {
        val base = listOf(100f to 100f, 300f to 100f, 300f to 200f, 100f to 200f)
        val rot = Traces.rotated(base, 5f, 200f, 150f)
        val out = snapped(Traces.polygon(rot))!!
        val box = Extent.of(out)
        val onEdge = out.count {
            abs(it.x - box.left) < 0.5f || abs(it.x - box.right) < 0.5f ||
                abs(it.y - box.top) < 0.5f || abs(it.y - box.bottom) < 0.5f
        }
        assertEquals(out.size, onEdge)
    }

    @Test
    fun triangleBecomesThreeCornerPolygon() {
        val tri = listOf(200f to 80f, 330f to 300f, 70f to 300f)
        val out = snapped(Traces.polygon(tri))!!
        val box = Extent.of(out)
        assertEquals(260f, box.width, 14f)
        assertEquals(220f, box.height, 14f)
        // Three straight runs: direction changes only near three places.
        var turns = 0
        for (i in 2 until out.size) {
            val a = out[i - 1]
            val b = out[i]
            val c = out[i - 2]
            val cross = (a.x - c.x) * (b.y - a.y) - (a.y - c.y) * (b.x - a.x)
            if (abs(cross) > 3f) turns++
        }
        assertTrue("only the corners turn", turns <= 6)
    }

    @Test
    fun scribbleDoesNotSnap() {
        for (seed in 1..8) assertNull("seed $seed", snapped(Traces.scribble(seed)))
    }

    @Test
    fun zigzagDoesNotSnap() {
        assertNull(snapped(Traces.zigzag()))
    }

    @Test
    fun openCurveDoesNotSnap() {
        val arc = (0..40).map { 100f + it * 8f to 300f - 90f * kotlin.math.sin(it / 40f * 2.6f) }
        assertNull(snapped(Traces.walk(arc)))
    }

    @Test
    fun tinyHoldDotDoesNotSnap() {
        val dot = Traces.walk(listOf(100f to 100f, 106f to 102f))
        assertNull(snapped(dot))
    }

    @Test
    fun outputTimingIsMonotonicAndPressureFree() {
        val out = snapped(Traces.circle(300f, 300f, 80f))!!
        assertNotNull(out)
        out.zipWithNext().forEach { (a, b) -> assertTrue(b.t >= a.t) }
        assertTrue(out.last().t > out.first().t)
    }

    private fun distanceToLine(p: InkPt, a: InkPt, b: InkPt): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return abs(dx * (a.y - p.y) - dy * (a.x - p.x)) / hypot(dx, dy)
    }

    private fun maxStep(p: List<InkPt>) = p.zipWithNext().maxOf { (a, b) -> hypot(b.x - a.x, b.y - a.y) }
}
