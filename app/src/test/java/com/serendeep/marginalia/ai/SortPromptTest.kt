package com.serendeep.marginalia.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SortPromptTest {
    @Test
    fun sortDocumentListsCoursesAndCapsPageText() {
        val req = Prompts.sortDocument(
            listOf("Algorithms", "Biology"),
            "1909.13231v3",
            "paper.pdf",
            "arXiv 1909.13231",
            listOf("a".repeat(9_000), "b".repeat(9_000)),
        )
        assertTrue(req.text.contains("- Algorithms"))
        assertTrue(req.text.contains("- Biology"))
        assertTrue(req.text.contains("Current title: 1909.13231v3"))
        assertTrue(req.text.contains("File name: paper.pdf"))
        assertTrue(req.text.contains("Identifier: arXiv 1909.13231"))
        assertTrue(req.text.substringAfter("First pages:\n").length <= Prompts.SORT_BUDGET)
        assertTrue(Prompts.sortDocument(emptyList(), "t", "f", null, emptyList()).text.contains("(none)"))
    }

    @Test
    fun parsesExistingCourse() {
        val d = Prompts.parseSort("""{"course":"Algorithms","newCourse":null,"tags":["Graphs","dp"],"title":null}""")!!
        assertEquals("Algorithms", d.course)
        assertNull(d.newCourse)
        assertEquals(listOf("graphs", "dp"), d.tags)
        assertNull(d.title)
    }

    @Test
    fun parsesNewCourseInsideFenceWithTrailingText() {
        val raw = "```json\n{\"course\": null, \"newCourse\": {\"name\": \"Quantum\", \"emoji\": \"⚛\"}, " +
            "\"tags\": [\"a\",\"b\",\"c\",\"d\"], \"title\": \"Real {Title}\"}\n```\nDone."
        val d = Prompts.parseSort(raw)!!
        assertNull(d.course)
        assertEquals("Quantum", d.newCourse)
        assertEquals("⚛", d.newCourseEmoji)
        assertEquals(3, d.tags.size)
        assertEquals("Real {Title}", d.title)
    }

    @Test
    fun missingFieldsStillParse() {
        val d = Prompts.parseSort("""{"course":"X"}""")!!
        assertEquals("X", d.course)
        assertTrue(d.tags.isEmpty())
    }

    @Test
    fun garbageIsNull() {
        assertNull(Prompts.parseSort("sorry, I can't"))
        assertNull(Prompts.parseSort("{\"course\": \"X\""))
        assertNull(Prompts.parseSort(""))
    }

    @Test
    fun filenameDetection() {
        assertTrue(looksLikeFilename("1909.13231v3"))
        assertTrue(looksLikeFilename("attention_is_all_you_need"))
        assertTrue(looksLikeFilename("my-notes"))
        assertTrue(looksLikeFilename("lecture3.PDF"))
        assertTrue(!looksLikeFilename("Attention Is All You Need"))
    }
}
