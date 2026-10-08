package com.serendeep.marginalia.search

import android.content.Context
import android.graphics.RectF
import android.os.Process
import com.serendeep.marginalia.data.DocumentEntity
import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.pdf.PdfDocumentSource
import com.serendeep.marginalia.research.findArxivId
import com.serendeep.marginalia.research.findDoi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fills the page-text index and reads text out of PDFs. Every call opens its own
 * short-lived document, so a notebook's render lock is never held on its behalf.
 */
@Singleton
class TextIndexer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MarginaliaRepository,
) {
    // One low-priority thread: indexing a big PDF must not compete with scrolling or ink.
    private val dispatcher = Executors.newSingleThreadExecutor { task ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            task.run()
        }, "text-indexer")
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val queue = Mutex()

    /** Indexes every document that is not indexed yet, in the background. */
    fun schedule() {
        scope.launch { indexPending() }
    }

    suspend fun indexPending() = withContext(dispatcher) {
        queue.withLock {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getInt(VERSION_KEY, 1) < INDEX_VERSION) {
                repository.invalidateIndex()
                prefs.edit().putInt(VERSION_KEY, INDEX_VERSION).apply()
            }
            repository.unindexedDocuments().forEach { index(it) }
        }
    }

    private suspend fun index(document: DocumentEntity) {
        val file = File(document.localPath)
        val source = if (file.exists()) {
            runCatching { PdfDocumentSource.open(context, file) }.getOrNull()
        } else {
            null
        }
        // An unreadable or missing file will never index; record that instead of retrying forever.
        if (source == null) {
            repository.finishIndexing(document.id)
            return
        }
        try {
            repository.beginIndexing(document.id)
            for (page in 0 until source.pageCount) {
                repository.indexPage(document.id, page, source.pageText(page))
                yield()
            }
            repository.finishIndexing(document.id)
            if (source.pageCount > 0) sniffIdentifiers(document, source.pageText(0))
        } finally {
            source.close()
        }
    }

    private suspend fun sniffIdentifiers(document: DocumentEntity, firstPage: String) {
        val arxiv = findArxivId(firstPage, requirePrefix = true) ?: findArxivId(document.fileName)
        repository.fillIdentifiers(document.lectureId, findDoi(firstPage), arxiv)
    }

    /** Text under [area] (top-left-origin page fractions) of a PDF page; empty on any failure. */
    suspend fun textIn(path: String, page: Int, area: RectF): String = withContext(Dispatchers.IO) {
        val source = runCatching { PdfDocumentSource.open(context, File(path)) }.getOrNull()
            ?: return@withContext ""
        try {
            source.textIn(page, area)
        } finally {
            source.close()
        }
    }

    private companion object {
        const val PREFS = "marginalia"
        const val VERSION_KEY = "text_index_version"

        // Bump when extraction changes so existing indexes are rebuilt.
        const val INDEX_VERSION = 4
    }
}
