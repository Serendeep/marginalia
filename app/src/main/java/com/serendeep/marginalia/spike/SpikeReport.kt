package com.serendeep.marginalia.spike

/** One sampled page's measured results. */
data class PageResult(
    val index: Int,
    val widthPt: Int,
    val heightPt: Int,
    val bitmapW: Int,
    val bitmapH: Int,
    val renderMs: Long,
    val charCount: Int,
    val textMs: Long,
    val textSample: String,
    val error: String? = null,
)

/** Aggregate spike result for one PDF. */
data class SpikeReport(
    val fileName: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val openMs: Long,
    val sampledPages: List<PageResult>,
    val error: String? = null,
) {
    private val ok: List<PageResult> get() = sampledPages.filter { it.error == null }

    val avgRenderMs: Double get() = ok.map { it.renderMs }.averageOrZero()
    val maxRenderMs: Long get() = ok.maxOfOrNull { it.renderMs } ?: 0
    val avgTextMs: Double get() = ok.map { it.textMs }.averageOrZero()
    val pagesWithText: Int get() = sampledPages.count { it.charCount > 0 }

    /** True if the PDF has extractable text; false suggests a scanned or image-only file. */
    val hasTextLayer: Boolean get() = pagesWithText > 0
}

private fun List<Long>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
