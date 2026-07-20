package com.serendeep.marginalia.sync

import kotlin.math.abs

/** One stroke's footprint: the PDF page it was written against and its canvas span. */
data class SyncEntry(val pdfPage: Int, val canvasTop: Float, val canvasBottom: Float)

/**
 * Maps between canvas scroll offset and PDF page. Entries are grouped into runs of
 * non-decreasing page order down the canvas; positions interpolate linearly inside
 * and between anchors. Pages without ink extend proportionally from the nearest
 * inked page at one [pageHeightPx] per page; with no ink at all the whole mapping
 * is proportional. When pages were revisited, the run written later owns its own
 * canvas range and its pages' positions.
 */
class ScrollSync(
    entries: List<SyncEntry>,
    private val pageCount: Int,
    private val pageHeightPx: Float,
) {
    private class Anchor(val page: Int, val pos: Float)
    private class Run(val anchors: List<Anchor>, val canvasStart: Float, val canvasEnd: Float)

    private val runs: List<Run>

    init {
        val sorted = entries.sortedBy { it.canvasTop }
        val built = ArrayList<Run>()
        var current = ArrayList<SyncEntry>()
        for (e in sorted) {
            if (current.isNotEmpty() && e.pdfPage < current.last().pdfPage) {
                built.add(toRun(current))
                current = ArrayList()
            }
            current.add(e)
        }
        if (current.isNotEmpty()) built.add(toRun(current))
        runs = built
    }

    private fun toRun(run: List<SyncEntry>): Run {
        val anchors = ArrayList<Anchor>()
        for (e in run) {
            if (anchors.isEmpty() || anchors.last().page != e.pdfPage) {
                anchors.add(Anchor(e.pdfPage, e.canvasTop))
            }
        }
        return Run(anchors, run.first().canvasTop, run.maxOf { it.canvasBottom })
    }

    fun canvasOffsetForPage(page: Int): Float {
        val p = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        for (run in runs.asReversed()) {
            val anchors = run.anchors
            if (p < anchors.first().page || p > anchors.last().page) continue
            val hiIdx = anchors.indexOfFirst { it.page >= p }
            val hi = anchors[hiIdx]
            if (hi.page == p) return hi.pos
            val lo = anchors[hiIdx - 1]
            val t = (p - lo.page).toFloat() / (hi.page - lo.page)
            return lo.pos + t * (hi.pos - lo.pos)
        }
        // No run holds this page: extend from the nearest inked page, one screen per page.
        val nearest = runs.asReversed().flatMap { it.anchors }.minByOrNull { abs(it.page - p) }
            ?: return p * pageHeightPx
        return (nearest.pos + (p - nearest.page) * pageHeightPx).coerceAtLeast(0f)
    }

    fun pageForCanvasOffset(offset: Float): Int {
        if (pageCount <= 0) return 0
        val o = offset.coerceAtLeast(0f)
        // The top of the canvas is always the first page: forward mapping clamps
        // early pages to offset zero, so zero must invert to page zero.
        if (o == 0f) return 0
        val within = runs.asReversed().firstOrNull { o >= it.canvasStart && o <= it.canvasEnd }
        if (within != null) return clampPage(pageWithin(within, o))
        val prev = runs.filter { it.canvasEnd < o }.maxByOrNull { it.canvasEnd }
        val next = runs.filter { it.canvasStart > o }.minByOrNull { it.canvasStart }
        val page = when {
            prev != null && next != null -> {
                val loPage = prev.anchors.last().page
                val hiPage = next.anchors.first().page
                val t = (o - prev.canvasEnd) / (next.canvasStart - prev.canvasEnd)
                loPage + t * (hiPage - loPage)
            }
            next != null -> next.anchors.first().page - (next.canvasStart - o) / pageHeightPx
            prev != null -> prev.anchors.last().page + (o - prev.canvasEnd) / pageHeightPx
            else -> o / pageHeightPx
        }
        return clampPage(page.toInt())
    }

    private fun pageWithin(run: Run, o: Float): Int {
        val anchors = run.anchors
        val hiIdx = anchors.indexOfFirst { it.pos > o }
        if (hiIdx == 0) return anchors.first().page
        if (hiIdx < 0) return anchors.last().page
        val lo = anchors[hiIdx - 1]
        val hi = anchors[hiIdx]
        if (hi.pos <= lo.pos) return lo.page
        val t = (o - lo.pos) / (hi.pos - lo.pos)
        return (lo.page + t * (hi.page - lo.page)).toInt()
    }

    private fun clampPage(page: Int): Int = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
}
