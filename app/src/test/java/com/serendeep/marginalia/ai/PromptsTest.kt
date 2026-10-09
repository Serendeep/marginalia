package com.serendeep.marginalia.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptsTest {
    @Test
    fun parsesPlainFencedAndTrailingText() {
        val plain = """[{"front":"Q1","back":"A1"}]"""
        assertEquals(listOf(CardDraft("Q1", "A1")), Prompts.parseCards(plain))
        assertEquals(listOf(CardDraft("Q1", "A1")), Prompts.parseCards("```json\n$plain\n```"))
        assertEquals(listOf(CardDraft("Q1", "A1")), Prompts.parseCards("Here you go:\n$plain\nHope that helps [1]."))
    }

    @Test
    fun bracketsInsideStringsDoNotBreakExtraction() {
        val json = """[{"front":"What is a[i]?","back":"Element \"i\" of ] array"}]"""
        assertEquals(1, Prompts.parseCards("x $json y").size)
    }

    @Test
    fun invalidAndIncompleteGiveNothing() {
        assertTrue(Prompts.parseCards("no json here").isEmpty())
        assertTrue(Prompts.parseCards("""[{"front":"Q","back":"A"}""").isEmpty())
        assertTrue(Prompts.parseCards("""[{"front":"Q"},{"back":"A"},{"front":" ","back":"x"}]""").isEmpty())
    }

    @Test
    fun capsAtTwelve() {
        val many = (1..20).joinToString(",", "[", "]") { """{"front":"q$it","back":"a$it"}""" }
        assertEquals(12, Prompts.parseCards(many).size)
    }

    @Test
    fun citations() {
        val answer = "Gradient descent converges [Deep Learning p.12] and [Notes on ML p. 7-9]. Again [Deep Learning p.12]. [not a cite]"
        assertEquals(
            listOf(Citation("Deep Learning", 12), Citation("Notes on ML", 7)),
            Prompts.parseCitations(answer),
        )
    }

    @Test
    fun truncationIsFairAndBounded() {
        val out = Prompts.fairTruncate(listOf("a".repeat(100), "b".repeat(20_000), "c".repeat(20_000)), 12_000)
        assertEquals(100, out[0].length)
        assertTrue(out[1].length <= 5_951 && out[1].length > 5_000)
        assertTrue(out.sumOf { it.length } <= 12_000 + 2)
    }

    @Test
    fun askLibraryKeepsContextUnderBudgetAndLabelsHits() {
        val hits = (1..10).map { PageHit("Doc$it", it, "x".repeat(5_000)) }
        val req = Prompts.askLibrary("why?", hits)
        assertTrue(req.text.contains("[Doc3 p.3]"))
        assertTrue(req.text.length < 12_000 + 1_500)
    }

    @Test
    fun answerPromptsCarryTheFormatGuide() {
        assertTrue(Prompts.explainPage("p").instructions.contains(Prompts.FORMAT_GUIDE))
        assertTrue(Prompts.summarizeDocument(listOf("p"), "T").instructions.contains(Prompts.FORMAT_GUIDE))
        assertTrue(Prompts.askLibrary("q", listOf(PageHit("D", 1, "t"))).instructions.contains(Prompts.FORMAT_GUIDE))
    }

    @Test
    fun summarizeLabelsPages() {
        val req = Prompts.summarizeDocument(listOf("one", "", "three"), "T")
        assertTrue(req.text.contains("[p.1]\none"))
        assertTrue(req.text.contains("[p.3]\nthree"))
    }
}
