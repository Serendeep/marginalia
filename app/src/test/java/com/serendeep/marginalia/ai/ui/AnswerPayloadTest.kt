package com.serendeep.marginalia.ai.ui

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerPayloadTest {
    @Test
    fun serializesMessagesWithLowercaseRoles() {
        val json = JSONObject(
            answerPayload(
                listOf(
                    AnswerMessage("1", AnswerRole.USER, "why?"),
                    AnswerMessage("2", AnswerRole.ASSISTANT, "Because \$x^2\$\n\"quoted\" \\ done"),
                    AnswerMessage("3", AnswerRole.STATUS, "Searching…"),
                ),
                streaming = true,
            ),
        )
        assertTrue(json.getBoolean("streaming"))
        val messages = json.getJSONArray("messages")
        assertEquals(listOf("user", "assistant", "status"), (0 until 3).map { messages.getJSONObject(it).getString("role") })
        assertEquals("Because \$x^2\$\n\"quoted\" \\ done", messages.getJSONObject(1).getString("markdown"))
    }

    @Test
    fun emptyTranscriptIsAnEmptyArray() {
        assertEquals(0, JSONObject(answerPayload(emptyList(), false)).getJSONArray("messages").length())
    }
}
