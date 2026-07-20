package com.serendeep.marginalia.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollSyncTest {

    private val pageHeight = 1000f

    @Test
    fun emptyPairsMapProportionally() {
        val sync = ScrollSync(emptyList(), pageCount = 10, pageHeightPx = pageHeight)
        assertEquals(0f, sync.canvasForPdf(0f), 0.01f)
        assertEquals(3000f, sync.canvasForPdf(3f), 0.01f)
        assertEquals(0f, sync.pdfForCanvas(0f), 0.01f)
        assertEquals(4.5f, sync.pdfForCanvas(4500f), 0.01f)
        assertEquals(9f, sync.pdfForCanvas(99999f), 0.01f)
    }

    @Test
    fun zeroPagesAlwaysResolvesToStart() {
        val sync = ScrollSync(emptyList(), pageCount = 0, pageHeightPx = pageHeight)
        assertEquals(0f, sync.pdfForCanvas(5000f), 0.01f)
        assertEquals(0f, sync.canvasForPdf(7f), 0.01f)
    }

    @Test
    fun writeTimePairRestoresExactAlignment() {
        // Written while the PDF showed 35% into page 2 and the sheet sat at 1780px:
        // returning to that PDF position must restore that exact sheet offset.
        val sync = ScrollSync(listOf(SyncPair(2.35f, 1780f)), pageCount = 10, pageHeightPx = pageHeight)
        assertEquals(1780f, sync.canvasForPdf(2.35f), 0.01f)
        assertEquals(2.35f, sync.pdfForCanvas(1780f), 0.01f)
    }

    @Test
    fun singlePairExtendsAtOneScreenPerPage() {
        val sync = ScrollSync(listOf(SyncPair(2f, 500f)), pageCount = 10, pageHeightPx = pageHeight)
        assertEquals(500f, sync.canvasForPdf(2f), 0.01f)
        assertEquals(1500f, sync.canvasForPdf(3f), 0.01f)
        assertEquals(0f, sync.canvasForPdf(0f), 0.01f)
        assertEquals(3.2f, sync.pdfForCanvas(1700f), 0.01f)
        assertEquals(1.6f, sync.pdfForCanvas(100f), 0.01f)
    }

    @Test
    fun offsetZeroAlwaysInvertsToStart() {
        val sync = ScrollSync(listOf(SyncPair(2f, 500f)), pageCount = 10, pageHeightPx = pageHeight)
        assertEquals(0f, sync.canvasForPdf(0f), 0.01f)
        assertEquals(0f, sync.pdfForCanvas(0f), 0.01f)
        assertEquals(0f, sync.pdfForCanvas(sync.canvasForPdf(0f)), 0.01f)
    }

    @Test
    fun densePairsInterpolateBothDirections() {
        val pairs = listOf(
            SyncPair(0f, 0f),
            SyncPair(1f, 300f),
            SyncPair(2f, 600f),
            SyncPair(4f, 1000f),
        )
        val sync = ScrollSync(pairs, pageCount = 10, pageHeightPx = pageHeight)
        assertEquals(300f, sync.canvasForPdf(1f), 0.01f)
        assertEquals(800f, sync.canvasForPdf(3f), 0.01f)
        assertEquals(1.5f, sync.pdfForCanvas(450f), 0.01f)
        assertEquals(2.5f, sync.pdfForCanvas(700f), 0.01f)
    }

    @Test
    fun roundTripThroughRecordedPairsIsExact() {
        val pairs = listOf(
            SyncPair(1.2f, 100f),
            SyncPair(3.7f, 500f),
            SyncPair(6.1f, 900f),
        )
        val sync = ScrollSync(pairs, pageCount = 10, pageHeightPx = pageHeight)
        for (p in floatArrayOf(1.2f, 3.7f, 6.1f)) {
            assertEquals(p, sync.pdfForCanvas(sync.canvasForPdf(p)), 0.01f)
        }
    }

    @Test
    fun revisitLaterRunWinsItsRange() {
        val pairs = listOf(
            SyncPair(0f, 0f),
            SyncPair(5f, 500f),
            SyncPair(2f, 1000f),
            SyncPair(3f, 1500f),
        )
        val sync = ScrollSync(pairs, pageCount = 10, pageHeightPx = pageHeight)
        assertEquals(2f, sync.pdfForCanvas(1000f), 0.01f)
        assertEquals(3f, sync.pdfForCanvas(1500f), 0.01f)
        // The later visit owns page 2's canvas position.
        assertEquals(1000f, sync.canvasForPdf(2f), 0.01f)
        // Page 5 still resolves through the first run.
        assertEquals(500f, sync.canvasForPdf(5f), 0.01f)
    }
}
