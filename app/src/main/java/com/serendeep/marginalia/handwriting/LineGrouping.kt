package com.serendeep.marginalia.handwriting

import com.serendeep.marginalia.data.Box
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val SAME_LINE_OVERLAP = 0.4f
private const val SAME_LINE_CENTRE = 0.6f
private const val BLOCK_GAP = 1.2f
private const val BLOCK_WINDOW_PX = 600f

private class Line<T>(first: T, box: Box) {
    val items = mutableListOf(first)
    var top = box.top
    var bottom = box.bottom
    val height get() = max(bottom - top, 1f)
    val centre get() = (top + bottom) / 2f

    fun add(item: T, box: Box) {
        items += item
        top = min(top, box.top)
        bottom = max(bottom, box.bottom)
    }
}

/** Strokes split into lines (top to bottom, each left to right) by vertical overlap. */
fun <T> groupLines(items: List<T>, box: (T) -> Box): List<List<T>> {
    if (items.isEmpty()) return emptyList()
    val heights = items.map { max(box(it).bottom - box(it).top, 1f) }.sorted()
    val median = heights[heights.size / 2]
    val lines = ArrayList<Line<T>>()
    for (item in items.sortedBy { box(it).top }) {
        val b = box(item)
        val h = max(b.bottom - b.top, 1f)
        val centre = (b.top + b.bottom) / 2f
        val home = lines.filter { line ->
            val overlap = min(b.bottom, line.bottom) - max(b.top, line.top)
            overlap / min(h, line.height) >= SAME_LINE_OVERLAP ||
                abs(centre - line.centre) <= SAME_LINE_CENTRE * median
        }.minByOrNull { abs(centre - it.centre) }
        if (home != null) home.add(item, b) else lines += Line(item, b)
    }
    return lines.sortedBy { it.top }.map { line -> line.items.sortedBy { box(it).left } }
}

/** Consecutive lines merged into blocks when close together and anchored alike or within one window. */
fun <T> groupBlocks(lines: List<List<T>>, box: (T) -> Box, anchor: (T) -> String?): List<List<List<T>>> {
    val blocks = ArrayList<MutableList<List<T>>>()
    var prevTop = 0f
    var prevBottom = 0f
    var blockTop = 0f
    var blockAnchors = emptySet<String>()
    for (line in lines) {
        val top = line.minOf { box(it).top }
        val bottom = line.maxOf { box(it).bottom }
        val anchors = line.mapNotNull(anchor).toSet()
        val joins = blocks.isNotEmpty() &&
            top - prevBottom < BLOCK_GAP * max(prevBottom - prevTop, 1f) &&
            (anchors.any { it in blockAnchors } || top - blockTop < BLOCK_WINDOW_PX)
        if (joins) {
            blocks.last() += line
            blockAnchors = blockAnchors + anchors
        } else {
            blocks += mutableListOf(line)
            blockTop = top
            blockAnchors = anchors
        }
        prevTop = top
        prevBottom = bottom
    }
    return blocks
}

/** Identity of a lecture's margin strokes: changes when any is added or removed. */
fun strokesHash(ids: Collection<String>): String {
    val digest = MessageDigest.getInstance("SHA-1")
    digest.update(ids.size.toString().toByteArray())
    ids.sorted().forEach {
        digest.update(it.toByteArray())
        digest.update(0)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
