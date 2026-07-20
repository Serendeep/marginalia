package com.serendeep.marginalia.data

import androidx.ink.strokes.StrokeInputBatch

/** A rectangle as four edges. Framework-free so the sync logic can stay unit-testable. */
data class Box(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** A stroke as the app works with it: ink geometry plus where and when it was drawn. */
data class InkStroke(
    val id: String,
    val lectureId: String,
    val documentId: String,
    val anchorId: String? = null,
    val pdfPage: Int,
    val viewport: Box,
    val bounds: Box,
    val startedAt: Long,
    val endedAt: Long,
    val brushColor: Long,
    val brushSizeDp: Float,
    val batch: StrokeInputBatch,
)
