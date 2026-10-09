package com.serendeep.marginalia.ai.agent

import com.serendeep.marginalia.data.HighlightEntity
import com.serendeep.marginalia.data.HighlightRow
import com.serendeep.marginalia.data.InkHit
import com.serendeep.marginalia.data.InkPageText
import com.serendeep.marginalia.data.PageHit
import com.serendeep.marginalia.data.ReadingStatus
import com.serendeep.marginalia.data.SNIPPET_CLOSE
import com.serendeep.marginalia.data.SNIPPET_OPEN
import com.serendeep.marginalia.data.SearchResults
import com.serendeep.marginalia.data.TitleHit
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private val ATTENTION = DocInfo("d1", "Attention Is All You Need", "ML", listOf("nlp", "transformers"), 15, ReadingStatus.READING, 2_000L)
private val THERMO = DocInfo("d2", "Thermodynamics", "Physics", emptyList(), 3, ReadingStatus.DONE, null)

private class FakeData : AgentData {
    val docs = listOf(ATTENTION, THERMO)
    val pageTexts = HashMap<Pair<String, Int>, String>()
    val searchedFor = mutableListOf<String>()
    var png: ByteArray? = byteArrayOf(7)
    var ink = listOf<InkPageText>()
    var inkPage: Int? = -1

    override suspend fun documents() = docs
    override suspend fun search(query: String): SearchResults {
        searchedFor += query
        return SearchResults(
            documents = listOf(TitleHit("d1", ATTENTION.title)),
            pages = listOf(PageHit("d1", ATTENTION.title, 2, "multi-head ${SNIPPET_OPEN}attention$SNIPPET_CLOSE\nlayers")),
            highlights = listOf(
                HighlightRow(HighlightEntity("h1", "d1", "doc", 4, "scaled dot product", 0, "s", 1L), ATTENTION.title),
            ),
            ink = listOf(InkHit("d1", ATTENTION.title, "b1", null, "my note")),
        )
    }
    override suspend fun pageText(lectureId: String, page: Int) = pageTexts[lectureId to page]
    override suspend fun highlights(lectureId: String?, limit: Int) =
        listOf(HighlightRow(HighlightEntity("h1", lectureId ?: "d2", "doc", 0, "text", 0, "s", 1L), "T"))
    override suspend fun handwriting(lectureId: String, page: Int?): List<InkPageText> {
        inkPage = page
        return ink
    }
    override suspend fun renderPage(lectureId: String, page: Int, widthPx: Int) = png
}

class AgentToolsTest {
    private val data = FakeData()
    private val tools = AgentTools(data)

    private fun exec(name: String, args: String) = runBlocking { tools.execute(name, JSONObject(args)) }
    private fun json(name: String, args: String) = JSONObject(exec(name, args).text)

    @Test
    fun specsHideViewPageWithoutVision() {
        assertFalse(tools.specs(false).any { it.name == "view_page" })
        assertTrue(tools.specs(true).any { it.name == "view_page" })
        assertEquals(7, tools.specs(true).size)
        tools.specs(true).forEach { assertEquals("object", it.parametersJsonSchema.getString("type")) }
    }

    @Test
    fun searchReturnsOneBasedPagesCleanSnippetsAndTitles() {
        val out = json("search_library", """{"query":"what is multi-head attention"}""")
        val page = out.getJSONArray("pages").getJSONObject(0)
        assertEquals("d1", page.getString("document_id"))
        assertEquals("Attention Is All You Need", page.getString("title"))
        assertEquals(3, page.getInt("page"))
        assertEquals("multi-head attention layers", page.getString("snippet"))
        assertEquals(5, out.getJSONArray("highlights").getJSONObject(0).getInt("page"))
        assertTrue(out.getJSONArray("handwriting").getJSONObject(0).isNull("page"))
        assertEquals(1, out.getJSONArray("documents").length())
        assertTrue(data.searchedFor.size > 1)
    }

    @Test
    fun searchRequiresAQuery() {
        try {
            exec("search_library", "{}")
            fail()
        } catch (e: IllegalArgumentException) {
            assertEquals("query is required", e.message)
        }
    }

    @Test
    fun listFiltersByCourseTagAndStatus() {
        assertEquals(2, json("list_documents", "{}").getInt("total"))
        val byTag = json("list_documents", """{"tag":"TRANS"}""").getJSONArray("documents")
        assertEquals("d1", byTag.getJSONObject(0).getString("document_id"))
        assertEquals("reading", byTag.getJSONObject(0).getString("status"))
        assertEquals("d2", json("list_documents", """{"course":"phys"}""").getJSONArray("documents").getJSONObject(0).getString("document_id"))
        assertEquals(1, json("list_documents", """{"status":"done"}""").getInt("total"))
        assertTrue(json("list_documents", "{}").getJSONArray("documents").getJSONObject(1).isNull("last_opened"))
    }

    @Test
    fun readPagesClampsToSixAndSaysSo() {
        (0 until 15).forEach { data.pageTexts["d1" to it] = "text of page ${it + 1}" }
        val out = json("read_pages", """{"document_id":"d1","from_page":2,"to_page":14}""")
        val pages = out.getJSONArray("pages")
        assertEquals(6, pages.length())
        assertEquals(2, pages.getJSONObject(0).getInt("page"))
        assertEquals("text of page 2", pages.getJSONObject(0).getString("text"))
        assertEquals(7, pages.getJSONObject(5).getInt("page"))
        assertTrue(out.getString("note").contains("from page 8"))
    }

    @Test
    fun readPagesClampsToDocumentAndMarksMissingText() {
        val out = json("read_pages", """{"document_id":"d2","from_page":3,"to_page":9}""")
        val pages = out.getJSONArray("pages")
        assertEquals(1, pages.length())
        assertTrue(pages.getJSONObject(0).getString("text").contains("no indexed text"))
        assertFalse(out.has("note"))
    }

    @Test
    fun readPagesKeepsEveryPageWithinTheBudget() {
        (0 until 6).forEach { data.pageTexts["d1" to it] = "y".repeat(5_000) }
        val out = exec("read_pages", """{"document_id":"d1","from_page":1,"to_page":6}""").text
        assertTrue(out.length < 6_000)
        assertEquals(6, JSONObject(out).getJSONArray("pages").length())
    }

    @Test
    fun unknownDocumentIsAHelpfulError() {
        try {
            exec("read_pages", """{"document_id":"nope","from_page":1}""")
            fail()
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("search_library"))
        }
    }

    @Test
    fun notesTranslatePageToZeroBased() {
        data.ink = listOf(InkPageText(3, "derive softmax"))
        val out = json("get_notes", """{"document_id":"d1","page":4}""")
        assertEquals(3, data.inkPage)
        assertEquals(4, out.getJSONArray("notes").getJSONObject(0).getInt("page"))
        data.ink = emptyList()
        assertTrue(json("get_notes", """{"document_id":"d1"}""").getString("note").contains("No recognised"))
        assertEquals(null, data.inkPage)
    }

    @Test
    fun highlightsAreOneBased() {
        val out = json("get_highlights", """{"document_id":"d1"}""")
        assertEquals(1, out.getJSONArray("highlights").getJSONObject(0).getInt("page"))
    }

    @Test
    fun viewPageReturnsImageWithPlaceholderText() {
        val out = exec("view_page", """{"document_id":"d1","page":4}""")
        assertEquals("Page image attached below.", out.text)
        assertNotNull(out.image)
        try {
            exec("view_page", """{"document_id":"d2","page":9}""")
            fail()
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("between 1 and 3"))
        }
    }

    @Test
    fun draftFlashcardsEmitsDraftsAndDropsBlankOnes() {
        val out = exec("draft_flashcards", """{"cards":[{"front":" Q1 ","back":"A1"},{"front":"","back":"x"},{"front":"Q2","back":"A2"}]}""")
        assertEquals("Drafted 2 cards for the user to review.", out.text)
        assertEquals(listOf("Q1", "Q2"), out.drafts.map { it.front })
    }

    @Test
    fun labelsReadHumanly() = runBlocking {
        assertEquals("Searching your library for \"attention\"", tools.label("search_library", JSONObject("""{"query":"attention"}""")))
        assertEquals(
            "Reading Attention Is All You Need p.3–5",
            tools.label("read_pages", JSONObject("""{"document_id":"d1","from_page":3,"to_page":5}""")),
        )
        assertEquals("Reading Thermodynamics p.2", tools.label("read_pages", JSONObject("""{"document_id":"d2","from_page":2}""")))
        assertEquals("Looking at Attention Is All You Need p.4", tools.label("view_page", JSONObject("""{"document_id":"d1","page":4}""")))
        assertEquals("Checking your highlights", tools.label("get_highlights", JSONObject()))
        assertEquals("Reading your handwritten notes", tools.label("get_notes", JSONObject()))
    }
}
