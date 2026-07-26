package com.serendeep.marginalia.data

import androidx.ink.storage.StrokeInputBatchSerialization
import androidx.ink.strokes.StrokeInputBatch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Turns ink geometry into bytes for storage and back. */
object StrokeCodec {

    fun encode(batch: StrokeInputBatch): ByteArray =
        ByteArrayOutputStream().use { out ->
            StrokeInputBatchSerialization.encode(batch, out)
            out.toByteArray()
        }

    fun decode(bytes: ByteArray): StrokeInputBatch =
        ByteArrayInputStream(bytes).use { StrokeInputBatchSerialization.decode(it) }
}

fun InkStroke.toEntity(): StrokeEntity = StrokeEntity(
    id = id,
    lectureId = lectureId,
    documentId = documentId,
    anchorId = anchorId,
    pdfPage = pdfPage,
    viewportLeft = viewport.left,
    viewportTop = viewport.top,
    viewportRight = viewport.right,
    viewportBottom = viewport.bottom,
    boundsLeft = bounds.left,
    boundsTop = bounds.top,
    boundsRight = bounds.right,
    boundsBottom = bounds.bottom,
    startedAt = startedAt,
    endedAt = endedAt,
    brushColor = brushColor,
    brushSizeDp = brushSizeDp,
    inkBlob = StrokeCodec.encode(batch),
    surface = surface.name,
)

fun StrokeEntity.toInkStroke(): InkStroke = InkStroke(
    id = id,
    lectureId = lectureId,
    documentId = documentId,
    anchorId = anchorId,
    pdfPage = pdfPage,
    viewport = Box(viewportLeft, viewportTop, viewportRight, viewportBottom),
    bounds = Box(boundsLeft, boundsTop, boundsRight, boundsBottom),
    startedAt = startedAt,
    endedAt = endedAt,
    brushColor = brushColor,
    brushSizeDp = brushSizeDp,
    batch = StrokeCodec.decode(inkBlob),
    surface = runCatching { InkSurface.valueOf(surface) }.getOrDefault(InkSurface.MARGIN),
)
