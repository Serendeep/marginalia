package com.serendeep.marginalia.ai.ui

import com.serendeep.marginalia.ai.AiError
import com.serendeep.marginalia.ai.AiErrorKind
import com.serendeep.marginalia.ai.agent.ChatEntry
import com.serendeep.marginalia.ai.agent.ChatTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatMappingTest {

    @Test
    fun turnsBecomeMessagesInOrder() {
        val turns = listOf(
            ChatTurn(
                0, "Compare A and B",
                entries = listOf(ChatEntry.Status("Searching", "search_library"), ChatEntry.Text("A is faster [A p.2].")),
                streaming = false,
            ),
            ChatTurn(1, "Why?"),
        )
        val messages = answerMessages(turns)
        assertEquals(listOf("0-u", "0-s1", "0-1", "1-u"), messages.map { it.id })
        assertEquals(listOf(AnswerRole.USER, AnswerRole.STATUS, AnswerRole.ASSISTANT, AnswerRole.USER), messages.map { it.role })
        assertEquals("Researched in 1 step", messages[1].markdown)
    }

    @Test
    fun toolStepsCollapseToTheCurrentStepThenACount() {
        val steps = listOf(ChatEntry.Status("Searching", "search_library"), ChatEntry.Status("Reading A p.2", "read_pages"))
        val live = answerMessages(listOf(ChatTurn(0, "Q", entries = steps)))
        assertEquals(listOf("Q", "Reading A p.2"), live.map { it.markdown })
        val done = answerMessages(listOf(ChatTurn(0, "Q", entries = steps + ChatEntry.Text("Answer."), streaming = false)))
        assertEquals(listOf("Q", "Researched in 2 steps", "Answer."), done.map { it.markdown })
    }

    @Test
    fun errorBecomesAnAssistantLine() {
        val turn = ChatTurn(3, "Hi", error = AiError(AiErrorKind.NETWORK, "No connection"), streaming = false)
        val last = answerMessages(listOf(turn)).last()
        assertEquals("3-e", last.id)
        assertEquals(AnswerRole.ASSISTANT, last.role)
        assertEquals("**Couldn't finish:** No connection", last.markdown)
    }

    @Test
    fun citationsResolveExactThenIgnoringCase() {
        val titles = mapOf("Attention Is All You Need" to "l1", "attention is all you need" to "l2")
        assertEquals("l1", resolveLecture("Attention Is All You Need", titles))
        assertEquals("l2", resolveLecture("attention is all you need", titles))
        assertEquals("l1", resolveLecture("ATTENTION IS ALL YOU NEED ", titles))
        assertNull(resolveLecture("Unknown", titles))
    }

    @Test
    fun suggestionsUseRecentTitles() {
        assertEquals(
            listOf("Quiz me on A", "Compare A and B", "What did I highlight this week?", "Summarize what I read today"),
            suggestionPrompts(listOf("A", "B")),
        )
        assertEquals(3, suggestionPrompts(listOf("A")).size)
        assertEquals(2, suggestionPrompts(emptyList()).size)
    }
}
