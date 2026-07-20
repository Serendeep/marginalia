package com.serendeep.marginalia.spike

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfiumCore

/**
 * Renders sampled pages of a PDF and pulls out their text, timing both, to check
 * whether pdfium is fast enough and whether a file has a searchable text layer.
 */
object PdfiumSpike {

    /** For large docs, sample the first, last, and quartile pages instead of all of them. */
    fun sampleIndices(count: Int): List<Int> {
        if (count <= 0) return emptyList()
        if (count <= 6) return (0 until count).toList()
        return sortedSetOf(0, count / 4, count / 2, (count * 3) / 4, count - 1).toList()
    }

    /** Analyze one PDF. Call off the main thread. */
    fun run(
        context: Context,
        pfd: ParcelFileDescriptor,
        fileName: String,
        sizeBytes: Long,
        targetWidthPx: Int = 1080,
    ): SpikeReport {
        val core = PdfiumCore(context)
        return try {
            val openStart = SystemClock.elapsedRealtime()
            core.newDocument(pfd).use { doc ->
                val openMs = SystemClock.elapsedRealtime() - openStart
                val count = doc.getPageCount()
                val pages = sampleIndices(count).map { renderOne(doc, it, targetWidthPx) }
                SpikeReport(fileName, sizeBytes, count, openMs, pages)
            }
        } catch (t: Throwable) {
            SpikeReport(fileName, sizeBytes, 0, 0, emptyList(), error = t.readableMessage())
        }
    }

    /** Render a single page to a bitmap for on-screen preview. Null on failure. */
    fun renderPage(
        context: Context,
        pfd: ParcelFileDescriptor,
        index: Int,
        targetWidthPx: Int = 1080,
    ): Bitmap? {
        val core = PdfiumCore(context)
        return try {
            core.newDocument(pfd).use { doc ->
                if (index < 0 || index >= doc.getPageCount()) return null
                doc.openPage(index).use { page ->
                    val bmp = page.newBitmap(targetWidthPx)
                    page.renderPageBitmap(bmp, 0, 0, bmp.width, bmp.height)
                    bmp
                }
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun renderOne(doc: PdfDocument, index: Int, targetWidthPx: Int): PageResult {
        return try {
            doc.openPage(index).use { page ->
                val wPt = page.getPageWidthPoint().coerceAtLeast(1)
                val hPt = page.getPageHeightPoint().coerceAtLeast(1)
                val bmp = page.newBitmap(targetWidthPx)

                val renderStart = SystemClock.elapsedRealtime()
                page.renderPageBitmap(bmp, 0, 0, bmp.width, bmp.height)
                val renderMs = SystemClock.elapsedRealtime() - renderStart
                val bmpW = bmp.width
                val bmpH = bmp.height
                bmp.recycle()

                val textStart = SystemClock.elapsedRealtime()
                val (chars, sample) = page.openTextPage().use { tp ->
                    val n = tp.textPageCountChars()
                    val text = if (n > 0) tp.textPageGetText(0, n.coerceAtMost(4000)).orEmpty() else ""
                    n to text.take(120).replace('\n', ' ').trim()
                }
                val textMs = SystemClock.elapsedRealtime() - textStart

                PageResult(index, wPt, hPt, bmpW, bmpH, renderMs, chars, textMs, sample)
            }
        } catch (t: Throwable) {
            PageResult(index, 0, 0, 0, 0, 0, 0, 0, "", error = t.readableMessage())
        }
    }
}

private fun io.legere.pdfiumandroid.PdfPage.newBitmap(targetWidthPx: Int): Bitmap {
    val wPt = getPageWidthPoint().coerceAtLeast(1)
    val hPt = getPageHeightPoint().coerceAtLeast(1)
    val h = (targetWidthPx.toLong() * hPt / wPt).toInt().coerceAtLeast(1)
    return Bitmap.createBitmap(targetWidthPx, h, Bitmap.Config.ARGB_8888)
}

private fun Throwable.readableMessage(): String = message ?: this::class.java.simpleName
