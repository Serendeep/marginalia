package com.serendeep.marginalia.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchTextTest {

    @Test
    fun ftsQuery_quotesEachWordAsPrefix() {
        assertEquals("\"Foo*\" \"bar*\" \"baz*\"", ftsQuery("Foo, bar-baz"))
    }

    @Test
    fun ftsQuery_neutralisesOperators() {
        assertEquals("\"OR*\" \"x*\"", ftsQuery("OR \"x"))
        assertNull(ftsQuery("  \"-*  "))
    }

    @Test
    fun likePattern_escapesWildcards() {
        assertEquals("%100\\% a\\_b%", likePattern(" 100% a_b "))
        assertNull(likePattern("   "))
    }

    @Test
    fun parseSnippet_splitsMatchRuns() {
        val spans = parseSnippet("…the ${SNIPPET_OPEN}entropy${SNIPPET_CLOSE} of a\nsystem")
        assertEquals(
            listOf(
                SnippetSpan("…the ", false),
                SnippetSpan("entropy", true),
                SnippetSpan(" of a system", false),
            ),
            spans,
        )
    }

    @Test
    fun parseSnippet_toleratesUnclosedMarker() {
        assertEquals(listOf(SnippetSpan("a ", false), SnippetSpan("b", false)), parseSnippet("a ${SNIPPET_OPEN}b"))
    }

    @Test
    fun markTerms_isCaseInsensitiveAndKeepsOriginalCase() {
        assertEquals(
            listOf(SnippetSpan("Gibbs ", false), SnippetSpan("Free", true), SnippetSpan(" Energy", false)),
            markTerms("Gibbs Free Energy", "free"),
        )
        assertEquals(listOf(SnippetSpan("abc", false)), markTerms("abc", " - "))
    }

    @Test
    fun pushRecent_dedupesMovesToFrontAndCaps() {
        assertEquals(listOf("B", "a"), pushRecent(listOf("a", "b"), " B "))
        assertEquals(listOf("6", "5", "4", "3", "2"), pushRecent(listOf("5", "4", "3", "2", "1"), "6"))
        assertEquals(listOf("a"), pushRecent(listOf("a"), "  "))
    }

    @Test
    fun highlightsMarkdown_quotesWithPageAndAnchorCount() {
        val md = highlightsMarkdown(
            " Thermo ",
            listOf(MarkdownHighlight(13, "Entropy\n never   decreases"), MarkdownHighlight(0, "Intro")),
            anchorCount = 3,
        )
        assertEquals(
            "# Thermo\n\n> Entropy never decreases (p. 14)\n\n> Intro (p. 1)\n\nAnchors: 3\n",
            md,
        )
    }

    @Test
    fun cleanPdfTextJoinsHyphenatedWordsAndBlanksControls() {
        assertEquals("generalization under", cleanPdfText("generaliza\uFFFE\r\ntion under"))
        assertEquals("a b\nc", cleanPdfText("a\u0002b\nc"))
    }
}
