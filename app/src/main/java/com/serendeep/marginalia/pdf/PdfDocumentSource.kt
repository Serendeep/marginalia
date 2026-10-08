package com.serendeep.marginalia.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.os.ParcelFileDescriptor
import com.serendeep.marginalia.data.cleanPdfText
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// Closing must wait for any in-flight render holding the document lock, but
// callers close from non-suspend contexts; releases run here instead.
private val closeScope = CoroutineScope(Dispatchers.IO)

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
    private var closed = false
    // Guards the maps below so the main thread can peek at finished work without
    // queueing behind a render that holds the document lock.
    private val cacheGuard = Any()
    private val aspectRatios = HashMap<Int, Float>()
    private val pageLinks = HashMap<Int, List<PageLink>>()

    // Scrolling back to a page must not pay a full native re-render; recent
    // full-page bitmaps cover the visible pages plus the prefetched neighbours.
    private val pageCache = object : LinkedHashMap<Long, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Bitmap>): Boolean =
            size > PAGE_CACHE_SIZE
    }

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
        if (closed) return@withLock emptyList()
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
        if (closed) return@withLock 1f
        cachedAspect(index) ?: document.openPage(index).use { page ->
            val w = page.getPageWidthPoint().coerceAtLeast(1)
            val h = page.getPageHeightPoint().coerceAtLeast(1)
            (w.toFloat() / h.toFloat()).also { synchronized(cacheGuard) { aspectRatios[index] = it } }
        }
    }

    /** Aspect ratio if already known; never blocks on pdfium. */
    fun cachedAspect(index: Int): Float? = synchronized(cacheGuard) { aspectRatios[index] }

    /** A finished render for this page and width, or null; never blocks on pdfium. */
    fun cachedPage(index: Int, widthPx: Int): Bitmap? =
        synchronized(cacheGuard) { pageCache[pageKey(index, widthPx)] }

    /** Warms the aspect and bitmap caches for a page that is about to scroll into view. */
    suspend fun prefetch(index: Int, widthPx: Int) {
        if (index !in 0 until pageCount || widthPx <= 0) return
        if (cachedAspect(index) == null) pageAspectRatio(index)
        if (cachedPage(index, widthPx) == null) renderFullPage(index, widthPx)
    }

    private fun pageKey(index: Int, widthPx: Int): Long = index.toLong() shl 32 or widthPx.toLong()

    /** Render a whole page at [widthPx] wide, height following the page aspect. */
    suspend fun renderFullPage(index: Int, widthPx: Int): Bitmap = lock.withLock {
        if (closed) return@withLock blank()
        val key = pageKey(index, widthPx)
        cachedPage(index, widthPx)?.let { return@withLock it }
        document.openPage(index).use { page ->
            val w = page.getPageWidthPoint().coerceAtLeast(1)
            val h = page.getPageHeightPoint().coerceAtLeast(1)
            val heightPx = (widthPx.toLong() * h / w).toInt().coerceAtLeast(1)
            synchronized(cacheGuard) { aspectRatios[index] = w.toFloat() / h.toFloat() }
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            page.renderPageBitmap(bitmap, 0, 0, widthPx, heightPx)
            // Uploads the texture on the render thread now, not inside the first frame that shows it.
            bitmap.prepareToDraw()
            synchronized(cacheGuard) { pageCache[key] = bitmap }
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
        if (closed) return@withLock blank()
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

    /** All text on a page in reading order; empty when the page has no text layer. */
    suspend fun pageText(index: Int): String = lock.withLock {
        if (closed) return@withLock ""
        runCatching {
            document.openPage(index).use { page ->
                page.openTextPage().use { text ->
                    val count = text.textPageCountChars()
                    if (count > 0) cleanPdfText(text.textPageGetText(0, count).orEmpty()) else ""
                }
            }
        }.getOrDefault("")
    }

    /**
     * Text under [area], given as top-left-origin fractions of the page. pdfium works in
     * bottom-left-origin PDF points, so the area is mapped through the page transform
     * (which also covers rotation and cropped origins) before the lookup.
     */
    suspend fun textIn(index: Int, area: RectF): String = lock.withLock {
        if (closed) return@withLock ""
        runCatching {
            document.openPage(index).use { page ->
                val w = page.getPageWidthPoint().coerceAtLeast(1) * TEXT_RES
                val h = page.getPageHeightPoint().coerceAtLeast(1) * TEXT_RES
                val device = Rect(
                    floor(area.left * w).toInt(),
                    floor(area.top * h).toInt(),
                    ceil(area.right * w).toInt(),
                    ceil(area.bottom * h).toInt(),
                )
                val r = page.mapRectToPage(0, 0, w, h, 0, device)
                // pdfium's bounded lookup wants top above bottom.
                val bounds = RectF(
                    min(r.left, r.right), max(r.top, r.bottom),
                    max(r.left, r.right), min(r.top, r.bottom),
                )
                page.openTextPage().use { text -> cleanPdfText(text.textPageGetBoundedText(bounds, MAX_BOUNDED_CHARS).orEmpty()) }
            }
        }.getOrDefault("")
    }

    /**
     * Text lines touching [area] (top-left-origin page fractions), each as the box around
     * its characters, in the same fractions. Characters are grouped into a line while they
     * keep overlapping its baseline band without a wide gap, so columns stay separate.
     */
    suspend fun textLineRects(index: Int, area: RectF): List<RectF> = lock.withLock {
        if (closed) return@withLock emptyList()
        runCatching {
            document.openPage(index).use { page ->
                val w = page.getPageWidthPoint().coerceAtLeast(1) * TEXT_RES
                val h = page.getPageHeightPoint().coerceAtLeast(1) * TEXT_RES
                page.openTextPage().use { text ->
                    val lines = ArrayList<RectF>()
                    var line: RectF? = null
                    for (i in 0 until text.textPageCountChars()) {
                        if (text.textPageGetUnicode(i).isWhitespace()) continue
                        val charBox = text.textPageGetCharBox(i) ?: continue
                        val device = page.mapRectToDevice(0, 0, w, h, 0, charBox)
                        val box = RectF(device.left / w.toFloat(), device.top / h.toFloat(), device.right / w.toFloat(), device.bottom / h.toFloat())
                        box.sort()
                        if (box.width() <= 0f || box.height() <= 0f) continue
                        val cur = line
                        if (cur != null && sameLine(cur, box)) {
                            cur.union(box)
                        } else {
                            if (cur != null) lines.add(cur)
                            line = RectF(box)
                        }
                    }
                    line?.let(lines::add)
                    lines.filter { RectF.intersects(it, area) }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun sameLine(line: RectF, box: RectF): Boolean {
        val overlap = min(line.bottom, box.bottom) - max(line.top, box.top)
        if (overlap < 0.5f * min(line.height(), box.height())) return false
        return box.left - line.right <= LINE_GAP * line.height() && line.left - box.right <= LINE_GAP * line.height()
    }

    fun close() {
        closeScope.launch {
            lock.withLock {
                if (closed) return@withLock
                closed = true
                synchronized(cacheGuard) { pageCache.clear() }
                runCatching { document.close() }
                runCatching { pfd.close() }
            }
        }
    }

    private fun blank(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    companion object {
        private const val PAGE_CACHE_SIZE = 10
        private const val TEXT_RES = 8
        private const val LINE_GAP = 2.5f
        private const val MAX_BOUNDED_CHARS = 1024

        fun open(context: Context, file: File): PdfDocumentSource =
            open(context, ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))

        fun open(context: Context, pfd: ParcelFileDescriptor): PdfDocumentSource {
            val core = PdfiumCore(context)
            val document = try {
                core.newDocument(pfd)
            } catch (t: Throwable) {
                runCatching { pfd.close() }
                throw t
            }
            return PdfDocumentSource(pfd, document)
        }
    }
}
