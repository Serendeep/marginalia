package com.serendeep.marginalia.ai

import org.json.JSONArray

data class PageHit(val title: String, val page: Int, val text: String)

data class CardDraft(val front: String, val back: String)

data class Citation(val title: String, val page: Int)

object Prompts {
    const val CONTEXT_BUDGET = 12_000
    const val MAX_CARDS = 12

    fun explainPage(pageText: String, imagePng: ByteArray? = null): AiRequest = AiRequest(
        instructions = "You are a patient tutor helping a student read a technical document. Explain the page clearly: " +
            "state the main idea first, then unpack definitions, equations and steps in plain language. Be concise.",
        text = "Explain this page.\n\n" + fairTruncate(listOf(pageText), CONTEXT_BUDGET).first(),
        imagePng = imagePng,
    )

    fun summarizeDocument(pages: List<String>, title: String): AiRequest {
        val texts = fairTruncate(pages, CONTEXT_BUDGET)
        val body = texts.mapIndexedNotNull { i, t -> if (t.isBlank()) null else "[p.${i + 1}]\n$t" }.joinToString("\n\n")
        return AiRequest(
            instructions = "You summarize documents for students. Produce a short overview followed by a bulleted list of " +
                "key points, and mention page numbers like p.4 where useful.",
            text = "Summarize \"$title\".\n\n$body",
        )
    }

    fun generateCards(text: String): AiRequest = AiRequest(
        instructions = "You write flashcards for spaced repetition. Reply with ONLY a JSON array, no prose and no code fences: " +
            "[{\"front\": \"question\", \"back\": \"answer\"}]. At most $MAX_CARDS cards. Each card tests one atomic idea, " +
            "the front is a self-contained question, the back is short. Skip trivia, dates and anything not worth remembering.",
        text = fairTruncate(listOf(text), CONTEXT_BUDGET).first(),
    )

    fun askLibrary(question: String, hits: List<PageHit>): AiRequest {
        val texts = fairTruncate(hits.map { it.text }, CONTEXT_BUDGET)
        val context = hits.indices.joinToString("\n\n") { "[${hits[it].title} p.${hits[it].page}]\n${texts[it]}" }
        return AiRequest(
            instructions = "Answer the question using only the excerpts provided. Cite every claim with the source in the form " +
                "[Title p.N], using the exact bracketed headers of the excerpts. If the excerpts don't contain the answer, say so.",
            text = "Excerpts:\n\n$context\n\nQuestion: $question",
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

    private fun extractArray(raw: String): JSONArray? {
        val start = raw.indexOf('[')
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
                '[' -> depth++
                ']' -> if (--depth == 0) return try { JSONArray(raw.substring(start, i + 1)) } catch (_: Exception) { null }
            }
        }
        return null
    }

    private val citation = Regex("""\[([^\[\]]+?)\s+p\.\s*(\d+)[^\]]*]""")

    fun parseCitations(answer: String): List<Citation> =
        citation.findAll(answer).map { Citation(it.groupValues[1].trim(), it.groupValues[2].toInt()) }.distinct().toList()
}
