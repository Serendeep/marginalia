package com.serendeep.marginalia.handwriting

import com.serendeep.marginalia.ink.InkPt

class TimedStroke(val startedAt: Long, val points: List<InkPt>)

private const val STROKE_GAP_MS = 1L

/**
 * Absolute timestamps for recognition: each stroke starts at its [TimedStroke.startedAt]
 * but never before the previous one ended, so the sequence always increases.
 */
fun timeline(strokes: List<TimedStroke>): List<List<InkPt>> {
    var cursor: Long? = null
    return strokes.map { stroke ->
        val first = stroke.points.firstOrNull()?.t ?: 0L
        val start = cursor?.let { maxOf(stroke.startedAt, it + STROKE_GAP_MS) } ?: stroke.startedAt
        var last = start
        val out = stroke.points.map { p ->
            last = maxOf(last, start + (p.t - first))
            InkPt(p.x, p.y, last)
        }
        cursor = last
        out
    }
}
