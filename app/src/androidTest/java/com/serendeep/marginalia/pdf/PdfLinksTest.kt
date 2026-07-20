package com.serendeep.marginalia.pdf

import android.content.Context
import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class PdfLinksTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun linklessPdfHasNoLinksAndNoOutline() = runBlocking {
        val source = PdfDocumentSource.open(context, writeLinklessPdf())
        try {
            assertTrue(source.pageLinks(0).isEmpty())
            assertTrue(source.outline().isEmpty())
        } finally {
            source.close()
        }
    }

    @Test
    fun pageLinksOutOfRangeDoesNotCrash() = runBlocking {
        val source = PdfDocumentSource.open(context, writeLinklessPdf())
        try {
            assertTrue(source.pageLinks(99).isEmpty())
        } finally {
            source.close()
        }
    }

    @Test
    fun uriLinkBoundsAreTopLeftFractions() = runBlocking {
        val source = PdfDocumentSource.open(context, writeLinkPdf())
        try {
            val links = source.pageLinks(0)
            assertEquals(1, links.size)
            val link = links[0]
            assertEquals("https://example.com", link.uri)
            assertNull(link.destPage)
            // The annotation rect is [100 700 300 760] on a 600x800 page.
            // In PDF space y grows upward, so that band sits near the top of
            // the page and must normalize to small top-left-origin fractions.
            assertEquals(100f / 600f, link.bounds.left, 0.02f)
            assertEquals(300f / 600f, link.bounds.right, 0.02f)
            assertEquals((800f - 760f) / 800f, link.bounds.top, 0.02f)
            assertEquals((800f - 700f) / 800f, link.bounds.bottom, 0.02f)
        } finally {
            source.close()
        }
    }

    private fun writeLinklessPdf(): File {
        val doc = PdfDocument()
        val info = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        doc.finishPage(doc.startPage(info))
        val out = File(context.cacheDir, "linkless-test.pdf")
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
        return out
    }

    // Smallest PDF with one URI link annotation; android.graphics.pdf cannot
    // embed links, so the bytes are assembled here with computed xref offsets.
    private fun writeLinkPdf(): File {
        val objects = listOf(
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
            "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
            "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 600 800] /Annots [4 0 R] >>\nendobj\n",
            "4 0 obj\n<< /Type /Annot /Subtype /Link /Rect [100 700 300 760] /Border [0 0 0] " +
                "/A << /S /URI /URI (https://example.com) >> >>\nendobj\n",
        )
        val body = StringBuilder("%PDF-1.4\n")
        val offsets = objects.map { obj ->
            val at = body.length
            body.append(obj)
            at
        }
        val xrefAt = body.length
        body.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { body.append(String.format("%010d 00000 n \n", it)) }
        body.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefAt\n%%EOF\n")
        val out = File(context.cacheDir, "link-test.pdf")
        out.writeBytes(body.toString().toByteArray(Charsets.US_ASCII))
        return out
    }
}
