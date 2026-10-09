package com.serendeep.marginalia.ai.ui

import com.serendeep.marginalia.data.PageHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {

    @Test
    fun splitsParagraphsOnBlankLinesAndJoinsWrappedLines() {
        val blocks = parseMarkdown("first line\nsecond line\n\nnext paragraph")
        assertEquals(
            listOf(
                MdBlock.Paragraph(listOf(MdSpan("first line second line"))),
                MdBlock.Paragraph(listOf(MdSpan("next paragraph"))),
            ),
            blocks,
        )
    }

    @Test
    fun bulletsKeepNumberedMarkers() {
        val blocks = parseMarkdown("- one\n* two\n1. three")
        assertEquals(
            listOf(
                MdBlock.Bullet("•", listOf(MdSpan("one"))),
                MdBlock.Bullet("•", listOf(MdSpan("two"))),
                MdBlock.Bullet("1.", listOf(MdSpan("three"))),
            ),
            blocks,
        )
    }

    @Test
    fun boldAndInlineCode() {
        assertEquals(
            listOf(MdSpan("a "), MdSpan("b", bold = true), MdSpan(" and "), MdSpan("x+1", code = true)),
            parseInline("a **b** and `x+1`"),
        )
    }

    @Test
    fun unclosedBoldRunsToTheEndAndLoneBacktickIsLiteral() {
        assertEquals(listOf(MdSpan("hi "), MdSpan("there", bold = true)), parseInline("hi **there"))
        assertEquals(listOf(MdSpan("a`b")), parseInline("a`b"))
    }

    @Test
    fun headingsBecomeBoldParagraphs() {
        assertEquals(listOf(MdBlock.Paragraph(listOf(MdSpan("Title", bold = true)))), parseMarkdown("## Title"))
    }

    @Test
    fun fencedCodeIsVerbatimEvenWhileUnclosed() {
        assertEquals(listOf(MdBlock.Code("a **b**\n- c")), parseMarkdown("```kotlin\na **b**\n- c\n```"))
        assertEquals(listOf(MdBlock.Code("x")), parseMarkdown("```\nx"))
    }

    @Test
    fun emptyInputHasNoBlocks() {
        assertTrue(parseMarkdown("").isEmpty())
    }

    @Test
    fun askTermsDropStopWordsAndPreferLongWords() {
        assertEquals(listOf("negative", "dijkstra", "weights"), askTerms("What are the negative weights in Dijkstra?", 3))
    }

    @Test
    fun rankPagesPutsMultiTermPagesFirst() {
        val a = PageHit("l1", "A", 2, "")
        val b = PageHit("l1", "A", 5, "")
        val c = PageHit("l2", "B", 1, "")
        assertEquals(listOf(b, a, c), rankPages(listOf(listOf(a, b, c), listOf(b)), 3))
        assertEquals(listOf(b), rankPages(listOf(listOf(a, b, c), listOf(b)), 1))
    }

    @Test
    fun quoteLinesBecomeQuoteBlocks() {
        val blocks = parseMarkdown("For example:\n\n> English sentence -> German translation")
        assertEquals(MdBlock.Quote(listOf(MdSpan("English sentence -> German translation"))), blocks.last())
    }
}
