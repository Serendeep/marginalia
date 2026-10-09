package com.serendeep.marginalia.ai.agent

import android.content.Context
import android.graphics.Bitmap
import com.serendeep.marginalia.data.HighlightRow
import com.serendeep.marginalia.data.InkPageText
import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.data.ReadingStatus
import com.serendeep.marginalia.data.SearchResults
import com.serendeep.marginalia.pdf.PdfDocumentSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class DocInfo(
    val id: String,
    val title: String,
    val course: String?,
    val tags: List<String>,
    val pageCount: Int,
    val status: ReadingStatus,
    val lastOpenedAt: Long?,
)

/** What the agent can read from the library. Pages are 0-based here; the tools translate for the model. */
interface AgentData {
    suspend fun documents(): List<DocInfo>
    suspend fun search(query: String): SearchResults
    suspend fun pageText(lectureId: String, page: Int): String?
    suspend fun highlights(lectureId: String?, limit: Int): List<HighlightRow>
    suspend fun handwriting(lectureId: String, page: Int?): List<InkPageText>
    suspend fun renderPage(lectureId: String, page: Int, widthPx: Int): ByteArray?
}

@Singleton
class RepositoryAgentData @Inject constructor(
    private val repository: MarginaliaRepository,
    @ApplicationContext private val context: Context,
) : AgentData {

    override suspend fun documents(): List<DocInfo> {
        val courses = repository.courses().associate { it.id to it.name }
        val tagNames = repository.observeTags().first().associate { it.id to it.name }
        val tagsByLecture = repository.observeTagLinks().first().groupBy({ it.lectureId }, { tagNames[it.tagId] })
        val pages = repository.observeAllDocuments().first().groupBy { it.lectureId }
            .mapValues { (_, v) -> v.maxByOrNull { it.versionIndex }?.pageCount ?: 0 }
        return repository.observeAllLectures().first().map {
            DocInfo(
                id = it.id,
                title = it.title,
                course = courses[it.courseId],
                tags = tagsByLecture[it.id].orEmpty().filterNotNull(),
                pageCount = pages[it.id] ?: 0,
                status = runCatching { ReadingStatus.valueOf(it.readingStatus) }.getOrDefault(ReadingStatus.TO_READ),
                lastOpenedAt = it.lastOpenedAt,
            )
        }
    }

    override suspend fun search(query: String) = repository.search(query)

    override suspend fun pageText(lectureId: String, page: Int) = repository.indexedPageText(lectureId, page)

    override suspend fun highlights(lectureId: String?, limit: Int): List<HighlightRow> =
        if (lectureId == null) repository.observeRecentHighlights(limit).first() else repository.highlightsOf(lectureId, limit)

    override suspend fun handwriting(lectureId: String, page: Int?) = repository.inkText(lectureId, page)

    override suspend fun renderPage(lectureId: String, page: Int, widthPx: Int): ByteArray? {
        val document = repository.latestDocument(lectureId) ?: return null
        val source = PdfDocumentSource.open(context, File(document.localPath))
        return try {
            val bitmap = source.renderFullPage(page, widthPx)
            ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        } finally {
            source.close()
        }
    }
}
