package com.serendeep.marginalia.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.ParcelFileDescriptor
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * A tappable region on a page. [bounds] is in fractions of page width/height
 * with the origin at the top-left, matching how anchors are stored.
 */
data class PageLink(
    val bounds: RectF,
    val destPage: Int?,
    val uri: String?,
)

/** One outline (bookmark) entry, flattened with its nesting depth. */
data class OutlineNode(
    val title: String,
    val pageIndex: Int,
    val depth: Int,
)

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
    private val pageLinks = HashMap<Int, List<PageLink>>()

    val pageCount: Int = document.getPageCount()

    // Read once at open time, before the document is shared across coroutines,
    // so the non-suspend accessor below never touches pdfium.
    private val outline: List<OutlineNode> = runCatching {
        val flat = mutableListOf<OutlineNode>()
        fun walk(nodes: List<PdfDocument.Bookmark>, depth: Int) {
            if (depth >= 3) return
            for (node in nodes) {
                // Skip bookmarks whose destination never resolved.
                val page = node.pageIdx.toInt()
                if (page in 0 until pageCount) {
                    flat += OutlineNode(node.title.orEmpty(), page, depth)
                }
                walk(node.children, depth + 1)
            }
        }
        walk(document.getTableOfContents(), 0)
        flat.toList()
    }.getOrDefault(emptyList())

    /** Bookmarks flattened in reading order; empty when the PDF has none. */
    fun outline(): List<OutlineNode> = outline

    /** Link annotations on a page, with bounds as top-left-origin fractions. */
    suspend fun pageLinks(index: Int): List<PageLink> = lock.withLock {
        pageLinks.getOrPut(index) {
            runCatching {
                document.openPage(index).use { page ->
                    val w = page.getPageWidthPoint().coerceAtLeast(1)
                    val h = page.getPageHeightPoint().coerceAtLeast(1)
                    page.getPageLinks().mapNotNull { link ->
                        // URI-only links report a destination index of -1; treat
                        // anything out of range as no internal destination.
                        val dest = link.destPageIdx?.takeIf { it in 0 until pageCount }
                        if (dest == null && link.uri == null) return@mapNotNull null
                        // Map page-space bounds (bottom-left origin, PDF points) to a
                        // device space the same size as the page; device space is
                        // top-left origin, so dividing by the page size gives fractions.
                        val device = page.mapRectToDevice(0, 0, w, h, 0, link.bounds)
                        val bounds = RectF(
                            device.left / w.toFloat(),
                            device.top / h.toFloat(),
                            device.right / w.toFloat(),
                            device.bottom / h.toFloat(),
                        )
                        bounds.sort()
                        PageLink(bounds, dest, link.uri)
                    }
                }
            }.getOrDefault(emptyList())
        }
    }

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
