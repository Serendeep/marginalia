package com.serendeep.marginalia.sync

import kotlin.math.abs

/**
 * One recorded correspondence: while the PDF showed [pdfPos] (page index plus
 * the fraction scrolled into it), the note sheet sat at [canvasPos] px.
 */
data class SyncPair(val pdfPos: Float, val canvasPos: Float)

/**
 * Maps between canvas scroll offset and continuous PDF position. Pairs are
 * grouped into runs of non-decreasing pdf order down the canvas; positions
 * interpolate linearly inside a run and across gaps. Beyond recorded pairs the
 * mapping extends at one [pageHeightPx] per page; with no pairs at all it is
 * fully proportional. Revisited material resolves through the run written last.
 */
class ScrollSync(
    pairs: List<SyncPair>,
    private val pageCount: Int,
    private val pageHeightPx: Float,
) {
    private class Run(val pairs: List<SyncPair>, val canvasStart: Float, val canvasEnd: Float)

    private val runs: List<Run>

    init {
        val sorted = pairs.sortedBy { it.canvasPos }
        val built = ArrayList<Run>()
        var current = ArrayList<SyncPair>()
        for (p in sorted) {
            if (current.isNotEmpty() && p.pdfPos < current.last().pdfPos) {
                built.add(Run(current, current.first().canvasPos, current.last().canvasPos))
                current = ArrayList()
            }
            current.add(p)
        }
        if (current.isNotEmpty()) {
            built.add(Run(current, current.first().canvasPos, current.last().canvasPos))
        }
        runs = built
    }

    fun canvasForPdf(pdfPos: Float): Float {
        val p = pdfPos.coerceIn(0f, (pageCount - 1).coerceAtLeast(0).toFloat())
        for (run in runs.asReversed()) {
            val a = run.pairs
            if (p < a.first().pdfPos || p > a.last().pdfPos) continue
            val hiIdx = a.indexOfFirst { it.pdfPos >= p }
            val hi = a[hiIdx]
            if (hi.pdfPos == p || hiIdx == 0) return hi.canvasPos
            val lo = a[hiIdx - 1]
            if (hi.pdfPos <= lo.pdfPos) return lo.canvasPos
            val t = (p - lo.pdfPos) / (hi.pdfPos - lo.pdfPos)
            return lo.canvasPos + t * (hi.canvasPos - lo.canvasPos)
        }
        val nearest = runs.asReversed().flatMap { it.pairs }.minByOrNull { abs(it.pdfPos - p) }
            ?: return p * pageHeightPx
        return (nearest.canvasPos + (p - nearest.pdfPos) * pageHeightPx).coerceAtLeast(0f)
    }

    fun pdfForCanvas(offset: Float): Float {
        if (pageCount <= 0) return 0f
        val o = offset.coerceAtLeast(0f)
        // The top of the canvas is always the document's start: the forward
        // mapping clamps early material to offset zero, so zero inverts to it.
        if (o == 0f) return 0f
        val within = runs.asReversed().firstOrNull { o >= it.canvasStart && o <= it.canvasEnd }
        if (within != null) return clampPos(pdfWithin(within, o))
        val prev = runs.filter { it.canvasEnd < o }.maxByOrNull { it.canvasEnd }
        val next = runs.filter { it.canvasStart > o }.minByOrNull { it.canvasStart }
        val pos = when {
            prev != null && next != null -> {
                val lo = prev.pairs.last().pdfPos
                val hi = next.pairs.first().pdfPos
                val t = (o - prev.canvasEnd) / (next.canvasStart - prev.canvasEnd)
                lo + t * (hi - lo)
            }
            next != null -> next.pairs.first().pdfPos - (next.canvasStart - o) / pageHeightPx
            prev != null -> prev.pairs.last().pdfPos + (o - prev.canvasEnd) / pageHeightPx
            else -> o / pageHeightPx
        }
        return clampPos(pos)
    }

    private fun pdfWithin(run: Run, o: Float): Float {
        val a = run.pairs
        val hiIdx = a.indexOfFirst { it.canvasPos > o }
        if (hiIdx == 0) return a.first().pdfPos
        if (hiIdx < 0) return a.last().pdfPos
        val lo = a[hiIdx - 1]
        val hi = a[hiIdx]
        if (hi.canvasPos <= lo.canvasPos) return lo.pdfPos
        val t = (o - lo.canvasPos) / (hi.canvasPos - lo.canvasPos)
        return lo.pdfPos + t * (hi.pdfPos - lo.pdfPos)
    }

    private fun clampPos(pos: Float): Float =
        pos.coerceIn(0f, (pageCount - 1).coerceAtLeast(0).toFloat())
}
