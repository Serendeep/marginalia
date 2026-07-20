package com.serendeep.marginalia.pdf

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class PdfDocumentSourceTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var source: PdfDocumentSource

    @Before
    fun setup() {
        source = PdfDocumentSource.open(context, writePdf(pages = 4))
    }

    @After
    fun teardown() = source.close()

    @Test
    fun reportsPageCount() {
        assertEquals(4, source.pageCount)
    }

    @Test
    fun aspectRatioMatchesA4() = runBlocking {
        // A4 is 595 x 842 points -> ~0.707 wide-to-tall.
        assertEquals(595f / 842f, source.pageAspectRatio(0), 0.01f)
    }

    @Test
    fun rendersFullPageAtRequestedWidth() = runBlocking {
        val bmp = source.renderFullPage(0, widthPx = 800)
        assertEquals(800, bmp.width)
        assertTrue("height follows aspect", bmp.height in 1000..1300)
        bmp.recycle()
    }

    @Test
    fun rendersRegionAtViewportSize() = runBlocking {
        // Ask for a 400x400 slice of a page drawn at 1600x2263; memory stays viewport-sized.
        val bmp = source.renderRegion(
            index = 0,
            scaledPageWidthPx = 1600,
            scaledPageHeightPx = 2263,
            srcLeftPx = 100,
            srcTopPx = 100,
            outWidthPx = 400,
            outHeightPx = 400,
        )
        assertEquals(400, bmp.width)
        assertEquals(400, bmp.height)
        bmp.recycle()
    }

    private fun writePdf(pages: Int): File {
        val doc = PdfDocument()
        val paint = Paint().apply { color = Color.BLACK; textSize = 24f }
        repeat(pages) { i ->
            val info = PdfDocument.PageInfo.Builder(595, 842, i + 1).create()
            val page = doc.startPage(info)
            page.canvas.drawText("Page ${i + 1}", 40f, 80f, paint)
            doc.finishPage(page)
        }
        val out = File(context.cacheDir, "pdf-source-test.pdf")
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
        return out
    }
}
