package com.serendeep.marginalia.ink

import kotlin.math.abs

/** Recognises a scribble-out gesture: a tight back-and-forth stroke that deletes what it covers. */
object ScratchOut {
    const val MIN_REVERSALS = 4
    const val COVERAGE = 0.6f
    const val INFLATE_PX = 4f

    private const val MIN_SIDE_PX = 8f
    private const val MAX_ASPECT = 6f
    private const val MIN_PATH_RATIO = 3f
    // A scribble swings across most of its own width on every pass; loops in cursive letters do not.
    private const val REVERSAL_FRACTION = 0.6f

    /** The area scratched out, or null when [points] is not a scratch. */
    fun detect(points: List<InkPt>): Extent? {
        if (points.size < 8) return null
        val box = Extent.of(points)
        val minSide = minOf(box.width, box.height)
        val maxSide = maxOf(box.width, box.height)
        if (minSide < MIN_SIDE_PX || maxSide > MAX_ASPECT * minSide) return null
        if (pathLength(points) < MIN_PATH_RATIO * box.diagonal) return null
        val horizontal = box.width >= box.height
        val axis = FloatArray(points.size) { if (horizontal) points[it].x else points[it].y }
        if (reversals(axis, REVERSAL_FRACTION * maxSide) < MIN_REVERSALS) return null
        return box
    }

    /** Whether enough of [points] sits under a scratch over [area] for the stroke to count as covered. */
    fun covers(area: Extent, points: List<InkPt>): Boolean =
        fractionInside(points, area.inflated(INFLATE_PX)) >= COVERAGE

    /** Direction changes along [values], ignoring wobble smaller than [threshold]. */
    fun reversals(values: FloatArray, threshold: Float): Int {
        var dir = 0
        var extreme = values[0]
        var count = 0
        for (v in values) {
            when (dir) {
                0 -> if (abs(v - values[0]) >= threshold) {
                    dir = if (v > values[0]) 1 else -1
                    extreme = v
                }
                1 -> if (v > extreme) {
                    extreme = v
                } else if (extreme - v >= threshold) {
                    dir = -1
                    extreme = v
                    count++
                }
                else -> if (v < extreme) {
                    extreme = v
                } else if (v - extreme >= threshold) {
                    dir = 1
                    extreme = v
                    count++
                }
            }
        }
        return count
    }
}
