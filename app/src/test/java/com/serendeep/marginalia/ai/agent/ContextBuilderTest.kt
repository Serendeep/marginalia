package com.serendeep.marginalia.ai.agent

import com.serendeep.marginalia.data.HighlightRow
import com.serendeep.marginalia.data.InkPageText
import com.serendeep.marginalia.data.ReadingStatus
import com.serendeep.marginalia.data.SearchResults
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun doc(id: String, title: String, course: String?, opened: Long?) =
    DocInfo(id, title, course, emptyList(), 12, ReadingStatus.READING, opened)

private class ContextData(val docs: List<DocInfo>) : AgentData {
    var requestedPage: Int? = null
    override suspend fun documents() = docs
    override suspend fun search(query: String) = SearchResults()
    override suspend fun pageText(lectureId: String, page: Int): String? {
        requestedPage = page
        return "indexed text"
    }
    override suspend fun highlights(lectureId: String?, limit: Int): List<HighlightRow> = emptyList()
    override suspend fun handwriting(lectureId: String, page: Int?) = listOf(InkPageText(page, "my scribble"))
    override suspend fun renderPage(lectureId: String, page: Int, widthPx: Int): ByteArray? = null
}

class ContextBuilderTest {
    private val attention = doc("d1", "Attention Is All You Need", "ML", 5L)

    @Test
    fun notebookContextNamesDocumentPageTextAndHandwriting() {
        val data = ContextData(listOf(attention))
        val ctx = runBlocking { ContextBuilder(data).notebook("d1", page = 4) }
        assertEquals(3, data.requestedPage)
        assertTrue(ctx.text.startsWith("<context>"))
        assertTrue(ctx.text.endsWith("</context>"))
        assertTrue(ctx.text.contains("\"Attention Is All You Need\" (document_id: d1)"))
        assertTrue(ctx.text.contains("Current page: 4 of 12"))
        assertTrue(ctx.text.contains("Reading status: reading"))
        assertTrue(ctx.text.contains("indexed text"))
        assertTrue(ctx.text.contains("my scribble"))
        assertTrue(ctx.images.isEmpty())
    }

    @Test
    fun liveTextAndSelectionOverrideIndexAndAttachImage() {
        val crop = byteArrayOf(1)
        val ctx = runBlocking { ContextBuilder(ContextData(listOf(attention))).notebook("d1", 1, pageText = "live text", selectionPng = crop) }
        assertTrue(ctx.text.contains("live text"))
        assertFalse(ctx.text.contains("indexed text"))
        assertTrue(ctx.text.contains("selected a region"))
        assertEquals(listOf(crop), ctx.images)
    }

    @Test
    fun pageTextIsCappedAtFourThousandChars() {
        val text = ContextBuilder.renderNotebook(attention, "d1", 1, "z".repeat(10_000), "", false)
        assertEquals(4_000, text.count { it == 'z' })
    }

    @Test
    fun libraryContextListsCoursesAndTheEightMostRecent() {
        val docs = (1..10).map { doc("d$it", "Doc $it", if (it % 2 == 0) "Math" else "ML", it.toLong()) } + doc("never", "Unopened", null, null)
        val text = ContextBuilder.renderLibrary(docs)
        assertTrue(text.contains("11 documents; courses: Math, ML") || text.contains("11 documents; courses: ML, Math"))
        assertEquals(8, text.lines().count { it.startsWith("- ") })
        assertTrue(text.contains("(document_id: d10)"))
        assertFalse(text.contains("(document_id: d2)"))
        assertFalse(text.contains("Unopened"))
    }

    @Test
    fun libraryContextToleratesAnEmptyLibrary() {
        val text = ContextBuilder.renderLibrary(emptyList())
        assertTrue(text.contains("0 documents"))
        assertFalse(text.contains("Recently opened"))
    }
}
