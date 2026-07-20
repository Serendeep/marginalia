package com.serendeep.marginalia.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollSyncTest {

    private val pageHeight = 1000f

    @Test
    fun emptyEntriesMapProportionally() {
        val sync = ScrollSync(emptyList(), pageCount = 10, pageHeightPx = pageHeight)
        assertEquals(0f, sync.canvasOffsetForPage(0), 0.01f)
        assertEquals(3000f, sync.canvasOffsetForPage(3), 0.01f)
        assertEquals(0, sync.pageForCanvasOffset(0f))
        assertEquals(4, sync.pageForCanvasOffset(4500f))
        assertEquals(9, sync.pageForCanvasOffset(99999f))
    }

    @Test
    fun zeroPagesAlwaysResolvesToPageZero() {
        val sync = ScrollSync(emptyList(), pageCount = 0, pageHeightPx = pageHeight)
        assertEquals(0, sync.pageForCanvasOffset(5000f))
        assertEquals(0f, sync.canvasOffsetForPage(7), 0.01f)
    }

    @Test
    fun singleEntry() {
        val sync = ScrollSync(listOf(SyncEntry(2, 500f, 700f)), pageCount = 10, pageHeightPx = pageHeight)
        assertEquals(500f, sync.canvasOffsetForPage(2), 0.01f)
        assertEquals(2, sync.pageForCanvasOffset(600f))
        // Uninked neighbours extend at one screen per page, clamped at the top.
        assertEquals(1500f, sync.canvasOffsetForPage(3), 0.01f)
        assertEquals(0f, sync.canvasOffsetForPage(0), 0.01f)
        assertEquals(3, sync.pageForCanvasOffset(1700f))
        assertEquals(1, sync.pageForCanvasOffset(100f))
    }

    @Test
    fun denseRunInterpolates() {
        val entries = listOf(
            SyncEntry(0, 0f, 200f),
            SyncEntry(1, 300f, 500f),
            SyncEntry(2, 600f, 900f),
            SyncEntry(4, 1000f, 1400f),
        )
        val sync = ScrollSync(entries, pageCount = 10, pageHeightPx = pageHeight)
        assertEquals(300f, sync.canvasOffsetForPage(1), 0.01f)
        // Page 3 has no ink; it interpolates between page 2 (600) and page 4 (1000).
        assertEquals(800f, sync.canvasOffsetForPage(3), 0.01f)
        assertEquals(0, sync.pageForCanvasOffset(100f))
        assertEquals(1, sync.pageForCanvasOffset(450f))
        assertEquals(2, sync.pageForCanvasOffset(700f))
    }

    @Test
    fun sparseEntriesFallBackProportionally() {
        val entries = listOf(SyncEntry(0, 0f, 300f), SyncEntry(8, 400f, 900f))
        val sync = ScrollSync(entries, pageCount = 10, pageHeightPx = pageHeight)
        // Page 9 has no ink and sits past the run: nearest anchor plus one screen.
        assertEquals(1400f, sync.canvasOffsetForPage(9), 0.01f)
        // Past the run's canvas end, pages advance at one screen per page.
        assertEquals(9, sync.pageForCanvasOffset(2000f))
    }

    @Test
    fun revisitLaterRunWinsItsRange() {
        val entries = listOf(
            SyncEntry(0, 0f, 400f),
            SyncEntry(5, 500f, 900f),
            SyncEntry(2, 1000f, 1400f),
            SyncEntry(3, 1500f, 1900f),
        )
        val sync = ScrollSync(entries, pageCount = 10, pageHeightPx = pageHeight)
        // Canvas positions inside the second run resolve to the revisited pages.
        assertEquals(2, sync.pageForCanvasOffset(1100f))
        assertEquals(3, sync.pageForCanvasOffset(1600f))
        // The later visit owns page 2's position.
        assertEquals(1000f, sync.canvasOffsetForPage(2), 0.01f)
        // Page 5 still resolves through the first run.
        assertEquals(500f, sync.canvasOffsetForPage(5), 0.01f)
        // Early canvas still maps through the first run.
        assertEquals(0, sync.pageForCanvasOffset(50f))
    }

    @Test
    fun roundTripStaysOnPage() {
        val entries = listOf(
            SyncEntry(1, 100f, 300f),
            SyncEntry(3, 500f, 800f),
            SyncEntry(6, 900f, 1300f),
        )
        val sync = ScrollSync(entries, pageCount = 10, pageHeightPx = pageHeight)
        for (page in intArrayOf(1, 3, 6)) {
            assertEquals(page, sync.pageForCanvasOffset(sync.canvasOffsetForPage(page)))
        }
    }

    @Test
    fun offsetZeroAlwaysInvertsToFirstPage() {
        val sync = ScrollSync(
            listOf(SyncEntry(2, 500f, 700f)),
            pageCount = 10,
            pageHeightPx = pageHeight,
        )
        assertEquals(0f, sync.canvasOffsetForPage(0), 0.001f)
        assertEquals(0, sync.pageForCanvasOffset(0f))
        assertEquals(0, sync.pageForCanvasOffset(sync.canvasOffsetForPage(0)))
    }
}
