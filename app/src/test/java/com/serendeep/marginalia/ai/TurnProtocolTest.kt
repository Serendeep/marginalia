package com.serendeep.marginalia.ai

import com.serendeep.marginalia.ai.agent.Item
import com.serendeep.marginalia.ai.agent.ToolSpec
import com.serendeep.marginalia.ai.agent.TurnEvent
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnProtocolTest {
    private fun responses(s: String) = Sse.responsesTurn(s.trimIndent().lines().asSequence()).toList()
    private fun chat(s: String) = Sse.chatTurn(s.trimIndent().lines().asSequence()).toList()

    private val spec = ToolSpec("search_library", "Search", JSONObject("""{"type":"object","properties":{}}"""))

    @Test
    fun responsesStreamYieldsReasoningToolCallAndCompleted() {
        val out = responses(
            """
            data: {"type":"response.output_item.done","item":{"type":"reasoning","id":"rs_1","encrypted_content":"abc","summary":[]}}

            data: {"type":"response.output_text.delta","delta":"Let me look."}

            data: {"type":"response.output_text.done","text":"Let me look."}

            data: {"type":"response.output_item.done","item":{"type":"function_call","id":"fc_1","call_id":"call_9","name":"search_library","arguments":"{\"query\":\"attention\"}"}}

            data: {"type":"response.completed","response":{}}
            """,
        )
        assertEquals(5, out.size)
        assertEquals("rs_1", (out[0] as TurnEvent.Reasoning).raw.getString("id"))
        assertEquals(TurnEvent.TextDelta("Let me look."), out[1])
        assertEquals(TurnEvent.TextDone, out[2])
        val call = out[3] as TurnEvent.ToolCall
        assertEquals("call_9", call.callId)
        assertEquals("search_library", call.name)
        assertEquals("""{"query":"attention"}""", call.argsJson)
        assertEquals("fc_1", call.providerItem!!.getString("id"))
        assertEquals(TurnEvent.Completed, out[4])
    }

    @Test
    fun responsesFailureUsesUsageError() {
        val out = responses(
            """
            data: {"type":"response.failed","response":{"error":{"code":"subscription_sharing_usage_limit_exceeded","message":"x"}}}
            """,
        )
        assertEquals(AiErrorKind.USAGE_LIMIT, (out.single() as TurnEvent.Failed).error.kind)
    }

    @Test
    fun responsesTruncatedStreamFails() {
        val out = responses("""data: {"type":"response.output_text.delta","delta":"a"}""")
        assertTrue(out.last() is TurnEvent.Failed)
    }

    @Test
    fun chatToolCallFragmentsAreJoinedByIndex() {
        val out = chat(
            """
            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c1","function":{"name":"read_pages","arguments":"{\"doc"}},{"index":1,"id":"c2","function":{"name":"get_notes","arguments":""}}]}}]}

            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"ument_id\":\"d\"}"}},{"index":1,"function":{"arguments":"{}"}}]}}]}

            data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}

            data: [DONE]
            """,
        )
        val calls = out.filterIsInstance<TurnEvent.ToolCall>()
        assertEquals(listOf("c1", "c2"), calls.map { it.callId })
        assertEquals("read_pages", calls[0].name)
        assertEquals("""{"document_id":"d"}""", calls[0].argsJson)
        assertEquals("{}", calls[1].argsJson)
        assertEquals(TurnEvent.Completed, out.last())
    }

    @Test
    fun chatTextEndsWithTextDoneThenCompleted() {
        val out = chat(
            """
            data: {"choices":[{"delta":{"content":"Hi"}}]}

            data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

            data: [DONE]
            """,
        )
        assertEquals(listOf(TurnEvent.TextDelta("Hi"), TurnEvent.TextDone, TurnEvent.Completed), out)
    }

    @Test
    fun chatMissingCallIdGetsSyntheticOne() {
        val out = chat(
            """
            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"x","arguments":"{}"}}]},"finish_reason":"tool_calls"}]}

            data: [DONE]
            """,
        )
        assertEquals("call_0", out.filterIsInstance<TurnEvent.ToolCall>().single().callId)
    }

    @Test
    fun responsesBodyMapsEveryItemType() {
        val reasoning = JSONObject("""{"type":"reasoning","id":"rs_1","encrypted_content":"abc"}""")
        val providerCall = JSONObject("""{"type":"function_call","id":"fc_1","call_id":"c1","name":"t","arguments":"{}"}""")
        val body = ResponsesClient.turnBody(
            "gpt-x", Effort.LOW, "be nice",
            listOf(
                Item.UserText("hi", listOf(byteArrayOf(1))),
                Item.Reasoning(reasoning),
                Item.ToolCall("c1", "t", "{}", providerCall),
                Item.ToolResult("c1", "result"),
                Item.ToolCall("c2", "u", "{\"a\":1}"),
                Item.AssistantText("done"),
            ),
            listOf(spec),
        )
        assertEquals("be nice", body.getString("instructions"))
        assertFalse(body.getBoolean("store"))
        assertTrue(body.getBoolean("stream"))
        assertEquals("reasoning.encrypted_content", body.getJSONArray("include").getString(0))
        assertEquals("low", body.getJSONObject("reasoning").getString("effort"))
        val tool = body.getJSONArray("tools").getJSONObject(0)
        assertEquals("function", tool.getString("type"))
        assertEquals("search_library", tool.getString("name"))
        assertFalse(tool.getBoolean("strict"))

        val input = body.getJSONArray("input")
        val content = input.getJSONObject(0).getJSONArray("content")
        assertEquals("input_text", content.getJSONObject(0).getString("type"))
        assertEquals("input_image", content.getJSONObject(1).getString("type"))
        assertSame(reasoning, input.get(1))
        assertSame(providerCall, input.get(2))
        assertEquals("function_call_output", input.getJSONObject(3).getString("type"))
        assertEquals("result", input.getJSONObject(3).getString("output"))
        assertEquals("function_call", input.getJSONObject(4).getString("type"))
        assertEquals("c2", input.getJSONObject(4).getString("call_id"))
        assertEquals("{\"a\":1}", input.getJSONObject(4).getString("arguments"))
        val assistant = input.getJSONObject(5)
        assertEquals("assistant", assistant.getString("role"))
        assertEquals("output_text", assistant.getJSONArray("content").getJSONObject(0).getString("type"))
    }

    @Test
    fun responsesBodyOmitsToolsAndEffortWhenEmpty() {
        val body = ResponsesClient.turnBody("m", null, "i", listOf(Item.UserText("q")), emptyList())
        assertFalse(body.has("tools"))
        assertFalse(body.has("reasoning"))
    }

    @Test
    fun chatMessagesFoldToolCallsIntoOneAssistantMessage() {
        val messages = OpenAiCompatibleClient.messages(
            "sys",
            listOf(
                Item.UserText("q"),
                Item.AssistantText("checking"),
                Item.ToolCall("c1", "a", "{}"),
                Item.ToolCall("c2", "b", "{}"),
                Item.Reasoning(JSONObject()),
                Item.ToolResult("c1", "r1"),
                Item.ToolResult("c2", "r2"),
            ),
        )
        assertEquals(5, messages.length())
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        val assistant = messages.getJSONObject(2)
        assertEquals("checking", assistant.getString("content"))
        assertEquals(2, assistant.getJSONArray("tool_calls").length())
        assertEquals("c2", assistant.getJSONArray("tool_calls").getJSONObject(1).getString("id"))
        val tool = messages.getJSONObject(3)
        assertEquals("tool", tool.getString("role"))
        assertEquals("c2", messages.getJSONObject(4).getString("tool_call_id"))
        assertEquals("r1", tool.getString("content"))
    }

    @Test
    fun chatBodyUsesFunctionToolsAndImageParts() {
        val body = OpenAiCompatibleClient.turnBody(
            "m", Effort.HIGH, "i", listOf(Item.UserText("look", listOf(byteArrayOf(1)))), listOf(spec),
        )
        assertEquals("high", body.getString("reasoning_effort"))
        assertEquals("search_library", body.getJSONArray("tools").getJSONObject(0).getJSONObject("function").getString("name"))
        val parts = body.getJSONArray("messages").getJSONObject(1).getJSONArray("content")
        assertEquals("image_url", parts.getJSONObject(1).getString("type"))
    }
}
