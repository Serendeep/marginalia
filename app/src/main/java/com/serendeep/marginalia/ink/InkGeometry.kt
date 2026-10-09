package com.serendeep.marginalia.ink

import kotlin.math.hypot
import kotlin.math.sqrt

/** One sampled point of a stroke: position in px and milliseconds since the stroke began. */
data class InkPt(val x: Float, val y: Float, val t: Long = 0L)

internal class V(val x: Float, val y: Float)

internal fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float = hypot(ax - bx, ay - by)

internal fun pathLength(p: List<InkPt>): Float {
    var sum = 0f
    for (i in 1 until p.size) sum += dist(p[i - 1].x, p[i - 1].y, p[i].x, p[i].y)
    return sum
}

/** Axis-aligned extent of a point set. */
data class Extent(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val diagonal get() = hypot(width, height)

    fun inflated(by: Float) = Extent(left - by, top - by, right + by, bottom + by)

    fun contains(x: Float, y: Float) = x >= left && x <= right && y >= top && y <= bottom

    fun intersects(o: Extent) = left <= o.right && right >= o.left && top <= o.bottom && bottom >= o.top

    companion object {
        fun of(p: List<InkPt>): Extent {
            var l = Float.MAX_VALUE
            var t = Float.MAX_VALUE
            var r = -Float.MAX_VALUE
            var b = -Float.MAX_VALUE
            for (q in p) {
                if (q.x < l) l = q.x
                if (q.y < t) t = q.y
                if (q.x > r) r = q.x
                if (q.y > b) b = q.y
            }
            return Extent(l, t, r, b)
        }
    }
}

/** Fraction of [points] lying inside [box]. */
fun fractionInside(points: List<InkPt>, box: Extent): Float {
    if (points.isEmpty()) return 0f
    return points.count { box.contains(it.x, it.y) }.toFloat() / points.size
}

/** Even-odd point-in-polygon test. */
fun insidePolygon(x: Float, y: Float, poly: List<Pair<Float, Float>>): Boolean {
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val (xi, yi) = poly[i]
        val (xj, yj) = poly[j]
        if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
        j = i
    }
    return inside
}

/** Fraction of [points] inside [poly]; zero for polygons with fewer than three corners. */
fun fractionInsidePolygon(points: List<InkPt>, poly: List<Pair<Float, Float>>): Float {
    if (points.isEmpty() || poly.size < 3) return 0f
    return points.count { insidePolygon(it.x, it.y, poly) }.toFloat() / points.size
}

/** Uniform scale then translate: x' = scale * x + dx. */
data class StrokeTransform(val scale: Float = 1f, val dx: Float = 0f, val dy: Float = 0f) {
    fun x(x: Float) = scale * x + dx
    fun y(y: Float) = scale * y + dy

    companion object {
        val IDENTITY = StrokeTransform()

        fun move(dx: Float, dy: Float) = StrokeTransform(1f, dx, dy)

        /** Scale by [s] keeping ([px], [py]) fixed. */
        fun scaleAbout(s: Float, px: Float, py: Float) = StrokeTransform(s, px - s * px, py - s * py)
    }
}

internal fun sqr(v: Float) = v * v

internal fun stdOf(values: FloatArray): Pair<Float, Float> {
    val mean = values.average().toFloat()
    var s = 0f
    for (v in values) s += sqr(v - mean)
    return mean to sqrt(s / values.size)
}
