package com.serendeep.marginalia.pdf

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * An open PDF, rendered by pdfium. Holds the document for its lifetime; call [close]
 * when done. pdfium is not thread-safe, so every native call goes through one lock.
 */
class PdfDocumentSource private constructor(
    private val pfd: ParcelFileDescriptor,
    private val document: PdfDocument,
) {
    private val lock = Mutex()
    private val aspectRatios = HashMap<Int, Float>()

    val pageCount: Int = document.getPageCount()

    /** Page width divided by height, in PDF points. */
    suspend fun pageAspectRatio(index: Int): Float = lock.withLock {
        aspectRatios.getOrPut(index) {
            document.openPage(index).use { page ->
                val w = page.getPageWidthPoint().coerceAtLeast(1)
                val h = page.getPageHeightPoint().coerceAtLeast(1)
                w.toFloat() / h.toFloat()
            }
        }
    }

    /** Render a whole page at [widthPx] wide, height following the page aspect. */
    suspend fun renderFullPage(index: Int, widthPx: Int): Bitmap = lock.withLock {
        document.openPage(index).use { page ->
            val w = page.getPageWidthPoint().coerceAtLeast(1)
            val h = page.getPageHeightPoint().coerceAtLeast(1)
            val heightPx = (widthPx.toLong() * h / w).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            page.renderPageBitmap(bitmap, 0, 0, widthPx, heightPx)
            bitmap
        }
    }

    /**
     * Render only the visible slice of a page. The page is drawn at
     * [scaledPageWidthPx] x [scaledPageHeightPx] but shifted by ([srcLeftPx], [srcTopPx])
     * so just the on-screen region lands in an [outWidthPx] x [outHeightPx] bitmap.
     * Memory stays tied to the viewport, so zoom is sharp without huge bitmaps.
     */
    suspend fun renderRegion(
        index: Int,
        scaledPageWidthPx: Int,
        scaledPageHeightPx: Int,
        srcLeftPx: Int,
        srcTopPx: Int,
        outWidthPx: Int,
        outHeightPx: Int,
    ): Bitmap = lock.withLock {
        document.openPage(index).use { page ->
            val bitmap = Bitmap.createBitmap(
                outWidthPx.coerceAtLeast(1),
                outHeightPx.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            page.renderPageBitmap(bitmap, -srcLeftPx, -srcTopPx, scaledPageWidthPx, scaledPageHeightPx)
            bitmap
        }
    }

    fun close() {
        runCatching { document.close() }
        runCatching { pfd.close() }
    }

    companion object {
        fun open(context: Context, file: File): PdfDocumentSource =
            open(context, ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))

        fun open(context: Context, pfd: ParcelFileDescriptor): PdfDocumentSource {
            val core = PdfiumCore(context)
            return PdfDocumentSource(pfd, core.newDocument(pfd))
        }
    }
}
