package com.serendeep.marginalia.spike

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/** Generates a text PDF at runtime and checks that pdfium renders it and reads the text back. */
@RunWith(AndroidJUnit4::class)
class PdfiumSpikeTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun rendersAndExtractsText_fromGeneratedPdf() {
        val file = writeTextPdf(pages = 3)
        openPfd(file).use { pfd ->
            val report = PdfiumSpike.run(context, pfd, file.name, file.length())

            assertNull("spike should not error on a valid PDF", report.error)
            assertEquals("page count", 3, report.pageCount)
            assertTrue("should sample at least one page", report.sampledPages.isNotEmpty())

            report.sampledPages.forEach { p ->
                assertNull("page ${p.index} rendered without error", p.error)
                assertTrue("page ${p.index} produced a bitmap", p.bitmapW > 0 && p.bitmapH > 0)
                assertTrue("page ${p.index} render time recorded", p.renderMs >= 0)
            }

            assertTrue(
                "generated PDF has embedded text -> pdfium must extract it (text layer)",
                report.hasTextLayer,
            )
        }
    }

    @Test
    fun renderPage_returnsBitmap() {
        val file = writeTextPdf(pages = 1)
        openPfd(file).use { pfd ->
            val bmp = PdfiumSpike.renderPage(context, pfd, index = 0, targetWidthPx = 640)
            assertNotNull("page 0 should render to a bitmap", bmp)
            assertEquals("preview width honored", 640, bmp!!.width)
            assertTrue("preview has positive height", bmp.height > 0)
            bmp.recycle()
        }
    }

    @Test
    fun sampleIndices_coversEndsAndQuartiles_forLargeDoc() {
        assertEquals(listOf(0, 75, 150, 225, 299), PdfiumSpike.sampleIndices(300))
        assertEquals(listOf(0, 1, 2), PdfiumSpike.sampleIndices(3))
        assertEquals(emptyList<Int>(), PdfiumSpike.sampleIndices(0))
    }

    private fun writeTextPdf(pages: Int): File {
        val doc = PdfDocument()
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
        }
        repeat(pages) { i ->
            // A4 in PDF points (72 dpi): 595 x 842.
            val info = PdfDocument.PageInfo.Builder(595, 842, i + 1).create()
            val page = doc.startPage(info)
            page.canvas.drawText("Marginalia pdfium spike page ${i + 1}", 40f, 80f, paint)
            page.canvas.drawText("The quick brown fox extracts text.", 40f, 120f, paint)
            doc.finishPage(page)
        }
        val out = File(context.cacheDir, "spike-generated.pdf")
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
        return out
    }

    private fun openPfd(file: File): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
}
