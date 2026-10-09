package com.serendeep.marginalia.ai.agent

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContextBuilder @Inject constructor(private val data: AgentData) {

    /**
     * [page] is 1-based. [pageText] is the live text of the page when the caller has it; otherwise the indexed text is used.
     * [selectionPng] is the lasso crop, if one is active.
     */
    suspend fun notebook(lectureId: String, page: Int, pageText: String? = null, selectionPng: ByteArray? = null): AgentContext {
        val doc = data.documents().firstOrNull { it.id == lectureId }
        val text = pageText ?: data.pageText(lectureId, page - 1)
        val ink = data.handwriting(lectureId, page - 1).joinToString("\n") { it.text }
        return AgentContext(
            renderNotebook(doc, lectureId, page, text.orEmpty(), ink, selectionPng != null),
            listOfNotNull(selectionPng),
        )
    }

    suspend fun library(): AgentContext = AgentContext(renderLibrary(data.documents()))

    companion object {
        const val PAGE_TEXT_CHARS = 4_000
        const val HANDWRITING_CHARS = 2_000
        const val RECENT_DOCS = 8

        fun renderNotebook(doc: DocInfo?, id: String, page: Int, pageText: String, handwriting: String, hasSelection: Boolean): String =
            buildString {
                appendLine("<context>")
                appendLine("The user is reading a document in their notebook.")
                appendLine("Document: \"${doc?.title ?: "Untitled"}\" (document_id: $id)")
                appendLine("Current page: $page${doc?.pageCount?.takeIf { it > 0 }?.let { " of $it" }.orEmpty()}")
                doc?.let { appendLine("Reading status: ${it.status.name.lowercase(Locale.ROOT)}") }
                appendLine("Text of this page:")
                appendLine(pageText.trim().take(PAGE_TEXT_CHARS).ifEmpty { "(none indexed)" })
                if (handwriting.isNotBlank()) {
                    appendLine("Their handwritten notes on this page:")
                    appendLine(handwriting.trim().take(HANDWRITING_CHARS))
                }
                if (hasSelection) appendLine("They have selected a region of the page; it is attached as an image.")
                append("</context>")
            }

        fun renderLibrary(docs: List<DocInfo>): String = buildString {
            appendLine("<context>")
            appendLine("The user is on the Ask screen, not inside a document.")
            val courses = docs.mapNotNull { it.course }.distinct()
            appendLine("Library: ${docs.size} documents${if (courses.isEmpty()) "" else "; courses: " + courses.joinToString(", ")}")
            val recent = docs.filter { it.lastOpenedAt != null }.sortedByDescending { it.lastOpenedAt }.take(RECENT_DOCS)
            if (recent.isNotEmpty()) {
                appendLine("Recently opened:")
                recent.forEach { appendLine("- \"${it.title}\" (document_id: ${it.id})") }
            }
            append("</context>")
        }
    }
}
