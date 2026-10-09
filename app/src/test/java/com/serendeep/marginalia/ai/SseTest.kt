package com.serendeep.marginalia.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SseTest {
    private fun responses(s: String) = Sse.responses(s.trimIndent().lines().asSequence()).toList()
    private fun chat(s: String) = Sse.chatCompletions(s.trimIndent().lines().asSequence()).toList()

    @Test
    fun deltasThenCompleted() {
        val out = responses(
            """
            event: response.output_text.delta
            data: {"type":"response.output_text.delta","delta":"Hel"}

            data: {"type":"response.output_text.delta","delta":"lo"}

            event: response.completed
            data: {"type":"response.completed","response":{}}

            data: {"type":"response.output_text.delta","delta":"ignored"}
            """,
        )
        assertEquals(listOf(AiEvent.Delta("Hel"), AiEvent.Delta("lo"), AiEvent.Completed), out)
    }

    @Test
    fun eventNameWithoutTypeField() {
        val out = responses(
            """
            event: response.output_text.delta
            data: {"delta":"x"}
            event: response.completed
            data: {}
            """,
        )
        assertEquals(listOf(AiEvent.Delta("x"), AiEvent.Completed), out)
    }

    @Test
    fun failedMapsUsageCodes() {
        val limit = responses("""data: {"type":"response.failed","response":{"error":{"code":"subscription_sharing_usage_limit_exceeded","message":"m"}}}""")
        val failed = limit.single() as AiEvent.Failed
        assertEquals(AiErrorKind.USAGE_LIMIT, failed.error.kind)
        assertTrue(failed.error.message.contains("plan limit reached"))

        val unavailable = responses("""data: {"type":"response.failed","response":{"error":{"code":"subscription_sharing_usage_unavailable"}}}""")
        assertEquals(AiErrorKind.USAGE_UNAVAILABLE, (unavailable.single() as AiEvent.Failed).error.kind)

        val other = responses("""data: {"type":"response.failed","response":{"error":{"code":"server_error","message":"boom"}}}""")
        assertEquals(AiError(AiErrorKind.SERVER, "boom"), (other.single() as AiEvent.Failed).error)
    }

    @Test
    fun incompleteIsDistinct() {
        val out = responses("""data: {"type":"response.incomplete","response":{"incomplete_details":{"reason":"max_output_tokens"}}}""")
        assertEquals(listOf(AiEvent.Incomplete("max_output_tokens")), out)
    }

    @Test
    fun missingCompletedIsError() {
        val out = responses("""data: {"type":"response.output_text.delta","delta":"a"}""")
        assertEquals(AiEvent.Delta("a"), out[0])
        assertEquals(AiErrorKind.PROTOCOL, (out[1] as AiEvent.Failed).error.kind)
    }

    @Test
    fun chatCompletionsStream() {
        val out = chat(
            """
            data: {"choices":[{"delta":{"role":"assistant","content":""}}]}

            data: {"choices":[{"delta":{"content":"Hi"}}]}

            data: {"choices":[{"delta":{"content":" there"},"finish_reason":null}]}

            data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

            data: [DONE]
            """,
        )
        assertEquals(listOf(AiEvent.Delta("Hi"), AiEvent.Delta(" there"), AiEvent.Completed), out)
    }

    @Test
    fun chatCompletionsLengthAndTruncation() {
        val length = chat("""
            data: {"choices":[{"delta":{"content":"a"},"finish_reason":"length"}]}
            data: [DONE]
        """)
        assertEquals(listOf(AiEvent.Delta("a"), AiEvent.Incomplete("length")), length)

        val cut = chat("""data: {"choices":[{"delta":{"content":"a"}}]}""")
        assertEquals(AiErrorKind.PROTOCOL, (cut.last() as AiEvent.Failed).error.kind)

        val noDone = chat("""data: {"choices":[{"delta":{"content":"a"},"finish_reason":"stop"}]}""")
        assertEquals(AiEvent.Completed, noDone.last())
    }

    @Test
    fun chatCompletionsErrorObject() {
        val out = chat("""data: {"error":{"message":"model not found"}}""")
        assertEquals(AiError(AiErrorKind.SERVER, "model not found"), (out.single() as AiEvent.Failed).error)
    }

    @Test
    fun modelListParsing() {
        val chatgpt = ResponsesClient.parseModels(
            """{"models":[{"slug":"a","display_name":"A","visibility":"list"},{"slug":"h","visibility":"hide"},{"slug":"b","visibility":"list"}]}""",
        )
        assertEquals(listOf(ChatModel("a", "A"), ChatModel("b", "b")), chatgpt)
        assertEquals(
            listOf(ChatModel("llama3", "llama3")),
            OpenAiCompatibleClient.parseModels("""{"data":[{"id":"llama3"}]}"""),
        )
        assertEquals("https://h/v1", OpenAiCompatibleClient.baseOf("https://h/"))
        assertEquals("https://h/v1", OpenAiCompatibleClient.baseOf("https://h/v1/"))
    }
}
