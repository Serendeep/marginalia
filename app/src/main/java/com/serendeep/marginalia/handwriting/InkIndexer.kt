package com.serendeep.marginalia.handwriting

import android.content.Context
import android.os.Process
import com.serendeep.marginalia.data.InkBlockText
import com.serendeep.marginalia.data.InkSurface
import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.shell.PREFS
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

const val HANDWRITING_SEARCH_KEY = "handwriting_search"

private const val SETTLE_MS = 1_500L

/**
 * Keeps the handwriting search index in step with each lecture's margin strokes. It runs only
 * while no notebook is open, only when the recognition model is already on the device, and
 * never downloads anything itself.
 */
@Singleton
class InkIndexer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MarginaliaRepository,
    private val recognizer: InkRecognizer,
) {
    // One low-priority thread, like the PDF indexer, so recognition never competes with the UI.
    private val dispatcher = Executors.newSingleThreadExecutor { task ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            task.run()
        }, "ink-indexer")
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    @Volatile private var notebookOpen = false
    private var job: Job? = null

    /** Stops any run and holds off new ones until [onNotebookClosed]. */
    @Synchronized
    fun onNotebookOpened() {
        notebookOpen = true
        job?.cancel()
    }

    @Synchronized
    fun onNotebookClosed() {
        notebookOpen = false
        schedule(SETTLE_MS)
    }

    /** Re-indexes every lecture whose margin strokes changed since it was last indexed. */
    @Synchronized
    fun schedule(delayMs: Long = 0L) {
        job?.cancel()
        job = scope.launch {
            delay(delayMs)
            indexStale()
        }
    }

    suspend fun indexStale() {
        if (notebookOpen || !enabled() || recognizer.refresh() != ModelState.Ready) return
        val strokeIds = repository.handwritingStrokeIds()
        val indexed = repository.inkIndexHashes()
        for (lectureId in strokeIds.keys + indexed.keys) {
            val hash = strokesHash(strokeIds[lectureId].orEmpty())
            if (hash == indexed[lectureId]) continue
            if (!index(lectureId, hash)) return
            yield()
        }
    }

    private fun enabled() =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(HANDWRITING_SEARCH_KEY, true)

    /** False when recognition stopped working part-way, so the lecture is left to retry later. */
    private suspend fun index(lectureId: String, hash: String): Boolean {
        val all = repository.loadStrokes(lectureId)
        val margin = all.filter { it.surface == InkSurface.MARGIN }
        // Page ink lives in each page's own coordinates, so it is grouped page by page.
        val pages = all.filter { it.surface == InkSurface.PAGE && ((it.brushColor ushr 24) and 0xFFL) == 0xFFL }
            .groupBy { it.pdfPage }.values
        val blocks = (listOf(margin) + pages).filter { it.isNotEmpty() }
            .flatMap { groupBlocks(groupLines(it) { s -> s.bounds }, { s -> s.bounds }, { s -> s.anchorId }) }
        val rows = ArrayList<InkBlockText>()
        for (block in blocks) {
            var failed = false
            val text = block.mapNotNull { line -> recognizer.recognizeLine(line).also { failed = failed || recognizer.lastFailed } }
                .joinToString("\n")
            if (failed || recognizer.state.value != ModelState.Ready) return false
            // Stray dots and debris read as punctuation; only text with letters or digits is worth finding.
            if (text.any { it.isLetterOrDigit() }) {
                val first = block.first().first()
                rows += InkBlockText(first.id, first.pdfPage.takeIf { first.documentId.isNotEmpty() }, text)
            }
            yield()
        }
        withContext(NonCancellable) { repository.replaceInkText(lectureId, hash, rows) }
        return true
    }
}
