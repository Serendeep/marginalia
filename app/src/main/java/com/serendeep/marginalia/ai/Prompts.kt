package com.serendeep.marginalia.ai

import org.json.JSONArray
import org.json.JSONObject

data class PageHit(val title: String, val page: Int, val text: String)

data class CardDraft(val front: String, val back: String)

data class Citation(val title: String, val page: Int)

/** What the model chose for one document; [course] names an existing course, [newCourse] asks for a new one. */
data class SortDecision(
    val course: String?,
    val newCourse: String?,
    val newCourseEmoji: String?,
    val tags: List<String>,
    val title: String?,
)

private val arxivTitle = Regex("""(?i)(arxiv[:\s-]*)?\d{4}\.\d{4,5}(v\d+)?""")

/** True when [title] reads like a file name rather than a human title. */
fun looksLikeFilename(title: String): Boolean {
    val t = title.trim()
    // A single plain word ("xai", "Thermo") is a name someone chose; only machine-looking tokens count.
    return t.isEmpty() || t.endsWith(".pdf", ignoreCase = true) || arxivTitle.matches(t) ||
        (t.none { it.isWhitespace() } && t.any { it == '_' || it == '-' || it == '.' || it.isDigit() })
}

object Prompts {
    const val CONTEXT_BUDGET = 12_000
    const val MAX_CARDS = 12
    const val SORT_BUDGET = 4_000
    const val FORMAT_GUIDE = "Format with Markdown and use headings sparingly. Write math as \$...\$ inline or \$\$...\$\$ for display, " +
        "use pipe tables for comparisons, and add a ```mermaid block only when a diagram genuinely helps."
    private const val MAX_SORT_TAGS = 3

    fun explainPage(pageText: String, imagePng: ByteArray? = null): AiRequest = AiRequest(
        instructions = "You are a patient tutor helping a student read a technical document. Explain the page clearly: " +
            "state the main idea first, then unpack definitions, equations and steps in plain language. Be concise. " + FORMAT_GUIDE,
        text = "Explain this page.\n\n" + fairTruncate(listOf(pageText), CONTEXT_BUDGET).first(),
        imagePng = imagePng,
        task = AiTask.EXPLAIN,
    )

    fun summarizeDocument(pages: List<String>, title: String): AiRequest {
        val texts = fairTruncate(pages, CONTEXT_BUDGET)
        val body = texts.mapIndexedNotNull { i, t -> if (t.isBlank()) null else "[p.${i + 1}]\n$t" }.joinToString("\n\n")
        return AiRequest(
            instructions = "You summarize documents for students. Produce a short overview followed by a bulleted list of " +
                "key points, and mention page numbers like p.4 where useful. " + FORMAT_GUIDE,
            text = "Summarize \"$title\".\n\n$body",
            task = AiTask.SUMMARIZE,
        )
    }

    fun generateCards(text: String): AiRequest = AiRequest(
        instructions = "You write flashcards for spaced repetition. Reply with ONLY a JSON array, no prose and no code fences: " +
            "[{\"front\": \"question\", \"back\": \"answer\"}]. At most $MAX_CARDS cards. Each card tests one atomic idea, " +
            "the front is a self-contained question, the back is short. Skip trivia, dates and anything not worth remembering.",
        text = fairTruncate(listOf(text), CONTEXT_BUDGET).first(),
        task = AiTask.CARDS,
    )

    fun sortDocument(
        courses: List<String>,
        title: String,
        fileName: String,
        identifier: String?,
        pages: List<String>,
    ): AiRequest {
        val excerpt = fairTruncate(pages, SORT_BUDGET).joinToString("\n\n").take(SORT_BUDGET)
        val known = if (courses.isEmpty()) "(none)" else courses.joinToString("\n") { "- $it" }
        return AiRequest(
            instructions = "You file documents into a student's library. Reply with ONLY a JSON object, no prose and no code " +
                "fences: {\"course\": \"<existing course name>\"|null, \"newCourse\": {\"name\": \"...\", \"emoji\": \"...\"}|null, " +
                "\"tags\": [\"up to $MAX_SORT_TAGS lowercase topic tags\"], \"title\": \"<clean document title>\"|null}. " +
                "Set \"course\" to a name copied exactly from the existing courses when one fits; otherwise set it to null and " +
                "suggest \"newCourse\" with a short name and one emoji. Set \"title\" only when the current title looks like a file name.",
            text = "Existing courses:\n$known\n\nCurrent title: $title\nFile name: $fileName\n" +
                (identifier?.let { "Identifier: $it\n" } ?: "") + "\nFirst pages:\n$excerpt",
            task = AiTask.AUTO_SORT,
        )
    }

    fun digest(input: DigestInput): AiRequest {
        val facts = buildList {
            add("Study time: ${input.studyMinutes} min")
            if (input.byDocument.isNotEmpty()) add("By document: " + input.byDocument.joinToString("; ") { "${it.first} ${it.second} min" })
            if (input.opened.isNotEmpty()) add("Opened: " + input.opened.joinToString("; ") { "${it.title} (left at page ${it.lastPage})" })
            if (input.highlightCount > 0) {
                add("Highlights (${input.highlightCount}):")
                input.highlights.forEach { add("- $it") }
            }
            add("Cards created: ${input.cardsCreated}")
            add("Reviews done: ${input.reviews}" + (input.retentionPct?.let { " (retention $it%)" } ?: ""))
            add("Cards due today: ${input.dueToday}")
        }
        return AiRequest(
            instructions = "You write a short recap of a student's previous study day. Write 3 to 5 warm, factual sentences of plain " +
                "prose: no Markdown, no lists, no headings. Use only the facts given and never invent numbers, titles or topics. " +
                "End with what is due today. Do not ask questions and do not suggest quizzes or anything else to do.",
            text = "Yesterday's facts:\n" + facts.joinToString("\n"),
            task = AiTask.DIGEST,
        )
    }

    fun parseSort(raw: String): SortDecision? {
        val o = extractBalanced(raw, '{', '}')?.let { try { JSONObject(it) } catch (_: Exception) { null } } ?: return null
        fun text(key: String) = if (o.isNull(key)) null else o.optString(key).trim().ifEmpty { null }
        val fresh = o.optJSONObject("newCourse")
        val tags = o.optJSONArray("tags")?.let { a ->
            (0 until a.length()).mapNotNull { a.optString(it).trim().lowercase().ifEmpty { null } }
        }.orEmpty().distinct().take(MAX_SORT_TAGS)
        return SortDecision(
            course = text("course"),
            newCourse = fresh?.optString("name")?.trim()?.ifEmpty { null },
            newCourseEmoji = fresh?.optString("emoji")?.trim()?.ifEmpty { null },
            tags = tags,
            title = text("title"),
        )
    }

    fun askLibrary(question: String, hits: List<PageHit>): AiRequest {
        val texts = fairTruncate(hits.map { it.text }, CONTEXT_BUDGET)
        val context = hits.indices.joinToString("\n\n") { "[${hits[it].title} p.${hits[it].page}]\n${texts[it]}" }
        return AiRequest(
            instructions = "Answer the question using only the excerpts provided. Cite every claim with the source in the form " +
                "[Title p.N], using the exact bracketed headers of the excerpts. If the excerpts don't contain the answer, say so. " + FORMAT_GUIDE,
            text = "Excerpts:\n\n$context\n\nQuestion: $question",
            task = AiTask.ASK,
        )
    }

    /** Shares [budget] characters across [texts]; short texts keep everything and leave room for longer ones. */
    fun fairTruncate(texts: List<String>, budget: Int): List<String> {
        if (texts.isEmpty()) return emptyList()
        val limits = IntArray(texts.size)
        var remaining = budget
        var open = texts.indices.sortedBy { texts[it].length }
        while (open.isNotEmpty()) {
            val share = remaining / open.size
            val i = open.first()
            if (texts[i].length <= share) {
                limits[i] = texts[i].length
                remaining -= texts[i].length
                open = open.drop(1)
            } else {
                open.forEach { limits[it] = share }
                break
            }
        }
        return texts.mapIndexed { i, t -> if (t.length <= limits[i]) t else t.take(limits[i]).trimEnd() + "…" }
    }

    fun parseCards(raw: String): List<CardDraft> {
        val array = extractArray(raw) ?: return emptyList()
        val cards = ArrayList<CardDraft>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val front = o.optString("front").trim()
            val back = o.optString("back").trim()
            if (front.isNotEmpty() && back.isNotEmpty()) cards += CardDraft(front, back)
            if (cards.size == MAX_CARDS) break
        }
        return cards
    }

    private fun extractArray(raw: String): JSONArray? =
        extractBalanced(raw, '[', ']')?.let { try { JSONArray(it) } catch (_: Exception) { null } }

    private fun extractBalanced(raw: String, open: Char, close: Char): String? {
        val start = raw.indexOf(open)
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until raw.length) {
            val c = raw[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                open -> depth++
                close -> if (--depth == 0) return raw.substring(start, i + 1)
            }
        }
        return null
    }

    private val citation = Regex("""\[([^\[\]]+?)\s+p\.\s*(\d+)[^\]]*]""")

    fun parseCitations(answer: String): List<Citation> =
        citation.findAll(answer).map { Citation(it.groupValues[1].trim(), it.groupValues[2].toInt()) }.distinct().toList()
}
