package com.serendeep.marginalia.ai.agent

import com.serendeep.marginalia.ai.CardDraft
import com.serendeep.marginalia.ai.Prompts
import com.serendeep.marginalia.ai.ui.askTerms
import com.serendeep.marginalia.ai.ui.rankPages
import com.serendeep.marginalia.data.SNIPPET_CLOSE
import com.serendeep.marginalia.data.SNIPPET_OPEN
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** [image] is shown to the model right after the result; [drafts] go to the user for review, never to storage. */
class ToolOutput(val text: String, val drafts: List<CardDraft> = emptyList(), val image: ByteArray? = null)

interface ToolExecutor {
    fun specs(pageImages: Boolean): List<ToolSpec>

    /** The human status line shown while the tool runs. */
    suspend fun label(name: String, args: JSONObject): String

    /** Throws [IllegalArgumentException] with a message the model can act on when the call is invalid. */
    suspend fun execute(name: String, args: JSONObject): ToolOutput
}

@Singleton
class AgentTools @Inject constructor(private val data: AgentData) : ToolExecutor {

    override fun specs(pageImages: Boolean): List<ToolSpec> = buildList {
        add(
            spec(
                SEARCH, "Full-text search across the user's documents, highlights and handwritten notes. Returns matching pages with snippets.",
                "query" to string("Words to look for"), "limit" to int("Maximum results, at most $MAX_SEARCH"),
                required = listOf("query"),
            ),
        )
        add(
            spec(
                LIST, "List the user's documents with course, tags, page count, reading status and last opened date.",
                "course" to string("Only documents in a course whose name contains this"),
                "tag" to string("Only documents with a tag containing this"),
                "status" to string("to_read, reading or done"),
            ),
        )
        add(
            spec(
                READ, "Read the text of consecutive pages of a document (at most $MAX_PAGES per call). Pages are 1-based.",
                "document_id" to string("Id from search_library or list_documents"),
                "from_page" to int("First page"), "to_page" to int("Last page"),
                required = listOf("document_id", "from_page"),
            ),
        )
        add(
            spec(
                HIGHLIGHTS, "The user's highlights, for one document or the most recent across the library.",
                "document_id" to string("Limit to this document"),
            ),
        )
        add(
            spec(
                NOTES, "The user's recognised handwritten notes in a document, optionally for one page (1-based).",
                "document_id" to string("Id from search_library or list_documents"), "page" to int("Page number"),
                required = listOf("document_id"),
            ),
        )
        if (pageImages) {
            add(
                spec(
                    VIEW, "Look at a page as an image, for figures, diagrams, equations and layout. Pages are 1-based.",
                    "document_id" to string("Id from search_library or list_documents"), "page" to int("Page number"),
                    required = listOf("document_id", "page"),
                ),
            )
        }
        add(
            ToolSpec(
                DRAFT,
                "Propose flashcards for the user to review. Nothing is saved until they approve.",
                JSONObject().put("type", "object").put(
                    "properties",
                    JSONObject().put(
                        "cards",
                        JSONObject().put("type", "array").put("description", "At most ${Prompts.MAX_CARDS} cards, one idea each").put(
                            "items",
                            JSONObject().put("type", "object")
                                .put("properties", JSONObject().put("front", string("A self-contained question")).put("back", string("A short answer")))
                                .put("required", JSONArray().put("front").put("back")),
                        ),
                    ),
                ).put("required", JSONArray().put("cards")),
            ),
        )
    }

    override suspend fun label(name: String, args: JSONObject): String = when (name) {
        SEARCH -> "Searching your library for \"${args.optString("query").trim()}\""
        LIST -> "Browsing your library"
        READ -> {
            val from = args.optInt("from_page", 1)
            val to = args.optInt("to_page", from).coerceAtLeast(from)
            "Reading ${titleOf(args)} ${pageRange(from, to)}"
        }
        HIGHLIGHTS -> "Checking your highlights"
        NOTES -> "Reading your handwritten notes"
        VIEW -> "Looking at p.${args.optInt("page", 1)}"
        DRAFT -> "Drafting flashcards"
        else -> name
    }

    override suspend fun execute(name: String, args: JSONObject): ToolOutput = when (name) {
        SEARCH -> ToolOutput(search(args))
        LIST -> ToolOutput(list(args))
        READ -> ToolOutput(read(args))
        HIGHLIGHTS -> ToolOutput(highlights(args))
        NOTES -> ToolOutput(notes(args))
        VIEW -> view(args)
        DRAFT -> draft(args)
        else -> throw IllegalArgumentException("Unknown tool \"$name\"")
    }

    private suspend fun search(args: JSONObject): String {
        val query = args.optString("query").trim()
        require(query.isNotEmpty()) { "query is required" }
        val limit = args.optInt("limit", DEFAULT_SEARCH).coerceIn(1, MAX_SEARCH)
        val results = askTerms(query).ifEmpty { listOf(query) }.map { data.search(it) }
        val pages = rankPages(results.map { it.pages }, limit)
        val highlights = results.flatMap { it.highlights }.distinctBy { it.highlight.id }.take(limit)
        val ink = results.flatMap { it.ink }.distinctBy { it.lectureId to it.blockKey }.take(limit)
        val documents = results.flatMap { it.documents }.distinctBy { it.lectureId }.take(limit)
        val out = JSONObject()
            .put("pages", array(pages) { JSONObject().put("document_id", it.lectureId).put("title", it.title).put("page", it.page + 1).put("snippet", clean(it.snippet)) })
            .put(
                "highlights",
                array(highlights) { JSONObject().put("document_id", it.highlight.lectureId).put("title", it.lectureTitle).put("page", it.highlight.page + 1).put("text", it.highlight.text) },
            )
            .put(
                "handwriting",
                array(ink) { JSONObject().put("document_id", it.lectureId).put("title", it.title).put("page", it.page?.let { p -> p + 1 } ?: JSONObject.NULL).put("snippet", clean(it.snippet)) },
            )
            .put("documents", array(documents) { JSONObject().put("document_id", it.lectureId).put("title", it.title) })
        if (pages.isEmpty() && highlights.isEmpty() && ink.isEmpty() && documents.isEmpty()) out.put("note", "No matches. Try other words or list_documents.")
        return out.toString()
    }

    private suspend fun list(args: JSONObject): String {
        val course = args.optString("course").trim()
        val tag = args.optString("tag").trim()
        val status = args.optString("status").trim().lowercase(Locale.ROOT)
        val all = data.documents().filter { d ->
            (course.isEmpty() || d.course?.contains(course, ignoreCase = true) == true) &&
                (tag.isEmpty() || d.tags.any { it.contains(tag, ignoreCase = true) }) &&
                (status.isEmpty() || d.status.name.lowercase(Locale.ROOT) == status)
        }.sortedByDescending { it.lastOpenedAt ?: 0L }
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        val out = JSONObject().put("total", all.size).put(
            "documents",
            array(all.take(MAX_DOCS)) {
                JSONObject().put("document_id", it.id).put("title", it.title).put("course", it.course ?: JSONObject.NULL)
                    .put("tags", JSONArray(it.tags)).put("page_count", it.pageCount)
                    .put("status", it.status.name.lowercase(Locale.ROOT))
                    .put("last_opened", it.lastOpenedAt?.let { t -> date.format(Date(t)) } ?: JSONObject.NULL)
            },
        )
        if (all.size > MAX_DOCS) out.put("note", "Showing the $MAX_DOCS most recently opened.")
        return out.toString()
    }

    private suspend fun read(args: JSONObject): String {
        val doc = doc(args)
        require(doc.pageCount > 0) { "\"${doc.title}\" has no pages" }
        val from = args.optInt("from_page", 1).coerceIn(1, doc.pageCount)
        var to = args.optInt("to_page", from).coerceIn(from, doc.pageCount)
        var note: String? = null
        if (to - from + 1 > MAX_PAGES) {
            to = from + MAX_PAGES - 1
            note = "Limited to $MAX_PAGES pages per call; call again from page ${to + 1} for more."
        }
        val budget = READ_BUDGET / (to - from + 1)
        val pages = (from..to).map { p ->
            val text = data.pageText(doc.id, p - 1)?.trim().orEmpty()
            JSONObject().put("page", p).put("text", if (text.isEmpty()) "(no indexed text on this page)" else cap(text, budget))
        }
        val out = JSONObject().put("document_id", doc.id).put("title", doc.title).put("page_count", doc.pageCount).put("pages", JSONArray(pages))
        if (note != null) out.put("note", note)
        return out.toString()
    }

    private suspend fun highlights(args: JSONObject): String {
        val id = args.optString("document_id").trim()
        val rows = data.highlights(id.ifEmpty { null }, MAX_HIGHLIGHTS)
        val out = JSONObject().put(
            "highlights",
            array(rows) { JSONObject().put("document_id", it.highlight.lectureId).put("title", it.lectureTitle).put("page", it.highlight.page + 1).put("text", it.highlight.text) },
        )
        if (rows.isEmpty()) out.put("note", "No highlights found.")
        return out.toString()
    }

    private suspend fun notes(args: JSONObject): String {
        val doc = doc(args)
        val page = if (args.has("page")) args.optInt("page", 1).coerceAtLeast(1) else null
        val blocks = data.handwriting(doc.id, page?.minus(1))
        val out = JSONObject().put("document_id", doc.id).put("title", doc.title).put(
            "notes",
            array(blocks) { JSONObject().put("page", it.page?.let { p -> p + 1 } ?: JSONObject.NULL).put("text", it.text) },
        )
        if (blocks.isEmpty()) out.put("note", "No recognised handwriting${if (page != null) " on page $page" else ""}.")
        return out.toString()
    }

    private suspend fun view(args: JSONObject): ToolOutput {
        val doc = doc(args)
        val page = args.optInt("page", 1)
        require(page in 1..doc.pageCount) { "page must be between 1 and ${doc.pageCount}" }
        val png = data.renderPage(doc.id, page - 1, IMAGE_WIDTH_PX) ?: throw IllegalArgumentException("Couldn't render that page")
        return ToolOutput("Page image attached below.", image = png)
    }

    private fun draft(args: JSONObject): ToolOutput {
        val cards = args.optJSONArray("cards") ?: throw IllegalArgumentException("cards is required")
        val drafts = (0 until cards.length()).mapNotNull { cards.optJSONObject(it) }
            .map { CardDraft(it.optString("front").trim(), it.optString("back").trim()) }
            .filter { it.front.isNotEmpty() && it.back.isNotEmpty() }
            .take(Prompts.MAX_CARDS)
        require(drafts.isNotEmpty()) { "No usable cards: each needs a front and a back" }
        return ToolOutput("Drafted ${drafts.size} cards for the user to review.", drafts)
    }

    private suspend fun doc(args: JSONObject): DocInfo {
        val id = args.optString("document_id").trim()
        require(id.isNotEmpty()) { "document_id is required" }
        return data.documents().firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("No document with id \"$id\". Use search_library or list_documents to find ids.")
    }

    private suspend fun titleOf(args: JSONObject): String =
        runCatching { data.documents().firstOrNull { it.id == args.optString("document_id") }?.title }.getOrNull() ?: "document"

    companion object {
        const val SEARCH = "search_library"
        const val LIST = "list_documents"
        const val READ = "read_pages"
        const val HIGHLIGHTS = "get_highlights"
        const val NOTES = "get_notes"
        const val VIEW = "view_page"
        const val DRAFT = "draft_flashcards"

        const val MAX_SEARCH = 10
        const val MAX_PAGES = 6
        private const val DEFAULT_SEARCH = 5
        private const val MAX_DOCS = 40
        private const val MAX_HIGHLIGHTS = 40
        private const val READ_BUDGET = 5_400
        private const val IMAGE_WIDTH_PX = 1024

        fun pageRange(from: Int, to: Int) = if (to > from) "p.$from–$to" else "p.$from"

        private fun string(description: String) = JSONObject().put("type", "string").put("description", description)

        private fun int(description: String) = JSONObject().put("type", "integer").put("description", description)

        private fun spec(name: String, description: String, vararg props: Pair<String, JSONObject>, required: List<String> = emptyList()) =
            ToolSpec(
                name,
                description,
                JSONObject().put("type", "object")
                    .put("properties", JSONObject().apply { props.forEach { (k, v) -> put(k, v) } })
                    .put("required", JSONArray(required)),
            )

        private fun <T> array(items: List<T>, map: (T) -> JSONObject) = JSONArray().apply { items.forEach { put(map(it)) } }

        private fun clean(snippet: String) = snippet.replace(SNIPPET_OPEN, "").replace(SNIPPET_CLOSE, "").replace('\n', ' ').trim()

        private fun cap(text: String, max: Int) = if (text.length > max) text.take(max) + "…(truncated)" else text
    }
}
