package com.serendeep.marginalia.ink

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Hold-to-shape: when a stroke ends with a pause, recognises a line, arrow, ellipse,
 * rectangle or triangle and returns a clean point list in its place. Pure and
 * allocation-happy, so it only ever runs after the pen has lifted.
 */
object ShapeSnap {
    const val HOLD_MS = 500L
    const val HOLD_RADIUS_PX = 6f
    const val PRESSURE = 0.6f
    const val SPACING_PX = 4f

    private const val MIN_PATH_PX = 24f
    private const val LINE_RATIO = 0.92f
    private const val CLOSE_GAP = 0.15f
    private const val ELLIPSE_STD = 0.18f
    private const val CORNER_DEG = 60f
    private const val AXIS_TOLERANCE_DEG = 10f
    private const val SAMPLES = 96
    private const val CORNER_WINDOW = SAMPLES / 16

    /** The points to replace [points] with, or null when the stroke has no hold or matches no shape. */
    fun snap(points: List<InkPt>): List<InkPt>? {
        val body = withoutHold(points) ?: return null
        val shape = classify(body) ?: return null
        val duration = max(body.last().t - body.first().t, 1L)
        return densify(shape, duration)
    }

    /** [points] up to where the pen came to rest, or null when it never did. */
    fun withoutHold(points: List<InkPt>): List<InkPt>? {
        if (points.size < 3) return null
        val last = points.last()
        var i = points.lastIndex
        while (i > 0 && dist(points[i - 1].x, points[i - 1].y, last.x, last.y) <= HOLD_RADIUS_PX) i--
        if (last.t - points[i].t < HOLD_MS) return null
        val rest = points.subList(i, points.size)
        val anchor = rest.first()
        val settled = InkPt(rest.map { it.x }.average().toFloat(), rest.map { it.y }.average().toFloat(), anchor.t)
        return points.subList(0, i) + settled
    }

    private fun classify(body: List<InkPt>): List<V>? {
        val len = pathLength(body)
        if (len < MIN_PATH_PX) return null
        val first = body.first()
        val last = body.last()
        val chord = dist(first.x, first.y, last.x, last.y)
        if (chord >= LINE_RATIO * len) return listOf(V(first.x, first.y), V(last.x, last.y))
        arrow(body, len)?.let { return it }
        val box = Extent.of(body)
        if (box.diagonal < 16f || chord > CLOSE_GAP * box.diagonal) return null
        return closedShape(body, len, box)
    }

    private fun arrow(body: List<InkPt>, len: Float): List<V>? {
        val v = rdp(body.map { V(it.x, it.y) }, (0.025f * len).coerceIn(4f, 12f))
        if (v.size < 4 || v.size > 9) return null
        val s = v[0]
        val reach = v.maxOf { dist(it.x, it.y, s.x, s.y) }
        val tipIdx = v.indexOfFirst { dist(it.x, it.y, s.x, s.y) >= 0.9f * reach }
        if (tipIdx < 1 || tipIdx > v.size - 3) return null
        val tip = v[tipIdx]
        val shaft = dist(s.x, s.y, tip.x, tip.y)
        if (shaft < 40f) return null

        val tipBodyIdx = body.indices.minBy { dist(body[it].x, body[it].y, tip.x, tip.y) }
        val tolerance = max(6f, 0.08f * shaft)
        for (i in 0..tipBodyIdx) if (lineDistance(body[i].x, body[i].y, s, tip) > tolerance) return null

        val tail = v.subList(tipIdx + 1, v.size)
        if (tail.any { dist(it.x, it.y, tip.x, tip.y) > 0.4f * shaft }) return null
        val wings = tail.filter { dist(it.x, it.y, tip.x, tip.y) >= 0.10f * shaft }
        if (wings.size < 2) return null
        val w1 = wings.first()
        val w2 = wings.last()
        if (tail.last() !== w2) return null
        val between = tail.subList(tail.indexOf(w1) + 1, tail.indexOf(w2))
        if (between.none { dist(it.x, it.y, tip.x, tip.y) <= 0.12f * shaft }) return null

        val axisX = s.x - tip.x
        val axisY = s.y - tip.y
        val sides = wings.map { side(axisX, axisY, it.x - tip.x, it.y - tip.y) }
        if (side(axisX, axisY, w1.x - tip.x, w1.y - tip.y) == side(axisX, axisY, w2.x - tip.x, w2.y - tip.y)) return null
        if (sides.any { it == 0 }) return null
        for (w in listOf(w1, w2)) {
            val wl = dist(w.x, w.y, tip.x, tip.y)
            if (wl < 0.10f * shaft || wl > 0.35f * shaft) return null
            val deg = angleBetween(axisX, axisY, w.x - tip.x, w.y - tip.y)
            if (deg < 20f || deg > 60f) return null
        }
        return listOf(s, tip, w1, tip, w2)
    }

    private fun side(ax: Float, ay: Float, bx: Float, by: Float): Int {
        val c = ax * by - ay * bx
        return if (c > 0) 1 else if (c < 0) -1 else 0
    }

    private fun closedShape(body: List<InkPt>, len: Float, box: Extent): List<V>? {
        val ring = resampleClosed(body)
        val corners = corners(ring)
        val startIdx = ring.indices.minBy { dist(ring[it].x, ring[it].y, body.first().x, body.first().y) }
        val clockwise = signedArea(ring) > 0
        // The tight ends of a wide oval (circling a word) can read as corners; when the polygon
        // doesn't fit, the shape is still a candidate for an ellipse.
        val polygon = when (corners.size) {
            4 -> rectangle(ring, corners, startIdx, clockwise)
            3 -> triangle(ring, corners, startIdx, clockwise, box)
            else -> null
        }
        return polygon ?: ellipse(ring, len, body.first(), clockwise)
    }

    private fun resampleClosed(body: List<InkPt>): List<V> {
        val loop = body.map { V(it.x, it.y) } + V(body.first().x, body.first().y)
        val cum = FloatArray(loop.size)
        for (i in 1 until loop.size) cum[i] = cum[i - 1] + dist(loop[i - 1].x, loop[i - 1].y, loop[i].x, loop[i].y)
        val total = cum.last()
        val out = ArrayList<V>(SAMPLES)
        var seg = 1
        for (k in 0 until SAMPLES) {
            val target = total * k / SAMPLES
            while (seg < loop.size - 1 && cum[seg] < target) seg++
            val span = cum[seg] - cum[seg - 1]
            val f = if (span > 0f) (target - cum[seg - 1]) / span else 0f
            out.add(V(loop[seg - 1].x + (loop[seg].x - loop[seg - 1].x) * f, loop[seg - 1].y + (loop[seg].y - loop[seg - 1].y) * f))
        }
        return out
    }

    private fun corners(ring: List<V>): List<Int> {
        val n = ring.size
        val k = CORNER_WINDOW
        val turn = FloatArray(n)
        for (i in 0 until n) {
            val a = ring[(i - k + n) % n]
            val b = ring[i]
            val c = ring[(i + k) % n]
            turn[i] = angleBetween(b.x - a.x, b.y - a.y, c.x - b.x, c.y - b.y)
        }
        val kept = ArrayList<Int>()
        for (i in turn.indices.sortedByDescending { turn[it] }) {
            if (turn[i] <= CORNER_DEG) break
            if (kept.all { cyclicGap(it, i, n) >= k }) kept.add(i)
        }
        return kept.sorted()
    }

    private fun cyclicGap(a: Int, b: Int, n: Int): Int {
        val d = abs(a - b)
        return min(d, n - d)
    }

    private fun rectangle(ring: List<V>, corners: List<Int>, startIdx: Int, clockwise: Boolean): List<V>? {
        val c = corners.map { ring[it] }
        val edges = List(4) { V(c[(it + 1) % 4].x - c[it].x, c[(it + 1) % 4].y - c[it].y) }
        val lengths = edges.map { hypot(it.x, it.y) }
        if (lengths.any { it < 8f }) return null
        for (i in 0 until 4) {
            val e = edges[i]
            val f = edges[(i + 1) % 4]
            if (abs(angleBetween(e.x, e.y, f.x, f.y) - 90f) > 28f) return null
        }
        if (min(lengths[0], lengths[2]) / max(lengths[0], lengths[2]) < 0.6f) return null
        if (min(lengths[1], lengths[3]) / max(lengths[1], lengths[3]) < 0.6f) return null

        var s4 = 0f
        var c4 = 0f
        for (e in edges) {
            val th = atan2(e.y, e.x)
            s4 += sin(4 * th)
            c4 += cos(4 * th)
        }
        val phi = atan2(s4, c4) / 4f
        val deg = phi * 180f / PI.toFloat()
        val angle = if (abs(deg) <= AXIS_TOLERANCE_DEG) 0f else phi
        val ux = cos(angle)
        val uy = sin(angle)
        var minU = Float.MAX_VALUE
        var maxU = -Float.MAX_VALUE
        var minW = Float.MAX_VALUE
        var maxW = -Float.MAX_VALUE
        for (p in ring) {
            val u = p.x * ux + p.y * uy
            val w = -p.x * uy + p.y * ux
            minU = min(minU, u); maxU = max(maxU, u)
            minW = min(minW, w); maxW = max(maxW, w)
        }
        fun at(u: Float, w: Float) = V(u * ux - w * uy, u * uy + w * ux)
        val quad = listOf(at(minU, minW), at(maxU, minW), at(maxU, maxW), at(minU, maxW))
        val ordered = if (clockwise) quad else quad.reversed()
        val from = ordered.indices.minBy { dist(ordered[it].x, ordered[it].y, ring[startIdx].x, ring[startIdx].y) }
        val loop = List(5) { ordered[(from + it) % 4] }
        return loop
    }

    private fun triangle(ring: List<V>, corners: List<Int>, startIdx: Int, clockwise: Boolean, box: Extent): List<V>? {
        val c = corners.map { ring[it] }
        val sides = List(3) { dist(c[it].x, c[it].y, c[(it + 1) % 3].x, c[(it + 1) % 3].y) }
        val perimeter = sides.sum()
        if (sides.any { it < 0.12f * perimeter }) return null
        val area = abs((c[1].x - c[0].x) * (c[2].y - c[0].y) - (c[2].x - c[0].x) * (c[1].y - c[0].y)) / 2f
        if (area < 0.05f * box.width * box.height) return null
        val ordered = if (clockwise == (signedArea(c) > 0)) c else c.reversed()
        val from = ordered.indices.minBy { dist(ordered[it].x, ordered[it].y, ring[startIdx].x, ring[startIdx].y) }
        return List(4) { ordered[(from + it) % 3] }
    }

    private fun ellipse(ring: List<V>, len: Float, start: InkPt, clockwise: Boolean): List<V>? {
        val l = ring.minOf { it.x }
        val r = ring.maxOf { it.x }
        val t = ring.minOf { it.y }
        val b = ring.maxOf { it.y }
        val a = (r - l) / 2f
        val h = (b - t) / 2f
        if (a < 6f || h < 6f) return null
        val cx = (l + r) / 2f
        val cy = (t + b) / 2f
        val radii = FloatArray(ring.size) { hypot((ring[it].x - cx) / a, (ring[it].y - cy) / h) }
        val (mean, std) = stdOf(radii)
        if (mean <= 0f || std / mean > ELLIPSE_STD) return null
        val perimeter = PI.toFloat() * (3 * (a + h) - kotlin.math.sqrt((3 * a + h) * (a + 3 * h)))
        if (len < 0.75f * perimeter || len > 1.5f * perimeter) return null
        val theta0 = atan2((start.y - cy) / h, (start.x - cx) / a)
        val dir = if (clockwise) 1f else -1f
        val steps = 72
        return List(steps + 1) {
            val th = theta0 + dir * 2f * PI.toFloat() * it / steps
            V(cx + a * cos(th), cy + h * sin(th))
        }
    }

    private fun signedArea(p: List<V>): Float {
        var s = 0f
        for (i in p.indices) {
            val q = p[(i + 1) % p.size]
            s += p[i].x * q.y - q.x * p[i].y
        }
        return s
    }

    private fun angleBetween(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val la = hypot(ax, ay)
        val lb = hypot(bx, by)
        if (la == 0f || lb == 0f) return 0f
        val c = ((ax * bx + ay * by) / (la * lb)).coerceIn(-1f, 1f)
        return acos(c) * 180f / PI.toFloat()
    }

    private fun lineDistance(px: Float, py: Float, a: V, b: V): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val l = hypot(dx, dy)
        if (l == 0f) return dist(px, py, a.x, a.y)
        return abs(dx * (a.y - py) - dy * (a.x - px)) / l
    }

    private fun rdp(p: List<V>, eps: Float): List<V> {
        if (p.size < 3) return p
        var worst = 0f
        var at = 0
        for (i in 1 until p.size - 1) {
            val d = lineDistance(p[i].x, p[i].y, p.first(), p.last())
            if (d > worst) {
                worst = d
                at = i
            }
        }
        if (worst <= eps) return listOf(p.first(), p.last())
        return rdp(p.subList(0, at + 1), eps).dropLast(1) + rdp(p.subList(at, p.size), eps)
    }

    private fun densify(shape: List<V>, durationMs: Long): List<InkPt> {
        val xs = ArrayList<V>()
        xs.add(shape.first())
        for (i in 1 until shape.size) {
            val a = shape[i - 1]
            val b = shape[i]
            val steps = ceil(dist(a.x, a.y, b.x, b.y) / SPACING_PX).toInt().coerceAtLeast(1)
            for (s in 1..steps) xs.add(V(a.x + (b.x - a.x) * s / steps, a.y + (b.y - a.y) * s / steps))
        }
        val n = xs.size
        return xs.mapIndexed { i, p -> InkPt(p.x, p.y, durationMs * i / (n - 1).coerceAtLeast(1)) }
    }
}
