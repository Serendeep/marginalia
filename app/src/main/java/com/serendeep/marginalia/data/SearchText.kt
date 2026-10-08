package com.serendeep.marginalia.data

// Markers around matched terms in FTS snippets; chosen to never occur in PDF text.
const val SNIPPET_OPEN = "⟦"
const val SNIPPET_CLOSE = "⟧"

/**
 * pdfium marks a word hyphenated across lines with U+FFFE and leaves control
 * characters in place; joining and blanking them keeps split words searchable.
 */
fun cleanPdfText(raw: String): String =
    raw.replace(Regex("\uFFFE\\s*"), "").replace(Regex("[\\x00-\\x08\\x0B-\\x1F]"), " ")

/** A run of snippet text; [match] runs are the query terms to emphasise. */
data class SnippetSpan(val text: String, val match: Boolean)

fun parseSnippet(snippet: String): List<SnippetSpan> {
    val spans = ArrayList<SnippetSpan>()
    var rest = snippet.replace('\n', ' ').replace('\r', ' ')
    while (rest.isNotEmpty()) {
        val open = rest.indexOf(SNIPPET_OPEN)
        if (open < 0) {
            spans += SnippetSpan(rest, false)
            break
        }
        if (open > 0) spans += SnippetSpan(rest.substring(0, open), false)
        val close = rest.indexOf(SNIPPET_CLOSE, open + 1)
        if (close < 0) {
            spans += SnippetSpan(rest.substring(open + 1), false)
            break
        }
        spans += SnippetSpan(rest.substring(open + 1, close), true)
        rest = rest.substring(close + 1)
    }
    return spans
}

/** FTS MATCH expression: every word becomes a quoted prefix term, all required. Null when nothing searchable. */
fun ftsQuery(input: String): String? {
    val terms = input.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return null
    return terms.joinToString(" ") { "\"$it*\"" }
}

/** LIKE pattern matching [input] anywhere; pair with ESCAPE '\'. Null when blank. */
fun likePattern(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val escaped = trimmed.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    return "%$escaped%"
}

/** Newest-first list of recent searches, deduplicated ignoring case and capped at [max]. */
fun pushRecent(recent: List<String>, query: String, max: Int = 5): List<String> {
    val q = query.trim()
    if (q.isEmpty()) return recent
    return (listOf(q) + recent.filterNot { it.equals(q, ignoreCase = true) }).take(max)
}

class MarkdownHighlight(val page: Int, val text: String)

/** One document's highlights as a Markdown note: title, a quote per highlight with its page, anchor count. */
fun highlightsMarkdown(title: String, highlights: List<MarkdownHighlight>, anchorCount: Int): String = buildString {
    append("# ").append(title.trim()).append("\n\n")
    highlights.forEach { h ->
        val body = h.text.replace(Regex("\\s+"), " ").trim()
        append("> ").append(body).append(" (p. ").append(h.page + 1).append(")\n\n")
    }
    append("Anchors: ").append(anchorCount).append('\n')
}

/** [text] split into runs, with case-insensitive occurrences of the query's words marked as matches. */
fun markTerms(text: String, query: String): List<SnippetSpan> {
    val terms = query.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return listOf(SnippetSpan(text, false))
    val pattern = Regex(terms.joinToString("|") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
    val spans = ArrayList<SnippetSpan>()
    var last = 0
    for (m in pattern.findAll(text)) {
        if (m.range.first > last) spans += SnippetSpan(text.substring(last, m.range.first), false)
        spans += SnippetSpan(m.value, true)
        last = m.range.last + 1
    }
    if (last < text.length) spans += SnippetSpan(text.substring(last), false)
    return spans
}
