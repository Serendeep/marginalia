package com.serendeep.marginalia.ai.agent

import com.serendeep.marginalia.ai.AiError
import com.serendeep.marginalia.ai.AiErrorKind
import com.serendeep.marginalia.ai.CardDraft
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCodecTest {
    private fun roundTrip(item: Item) = ChatCodec.item(JSONObject(ChatCodec.item(item).toString()))

    @Test
    fun plainItemsRoundTrip() {
        assertEquals("hi", (roundTrip(Item.UserText("hi")) as Item.UserText).text)
        assertEquals(Item.AssistantText("yo"), roundTrip(Item.AssistantText("yo")))
        assertEquals(Item.ToolResult("c1", "out\nline"), roundTrip(Item.ToolResult("c1", "out\nline")))
    }

    @Test
    fun toolCallKeepsTheProviderItemVerbatim() {
        val raw = JSONObject("""{"type":"function_call","id":"fc_1","call_id":"c1","arguments":"{\"q\":1}","extra":{"a":[1,2]}}""")
        val back = roundTrip(Item.ToolCall("c1", "search", "{\"q\":1}", raw)) as Item.ToolCall
        assertEquals("c1", back.callId)
        assertEquals("search", back.name)
        assertEquals("{\"q\":1}", back.argsJson)
        assertEquals(raw.toString(), back.providerItem.toString())
    }

    @Test
    fun toolCallWithoutProviderItemStaysWithout() {
        assertNull((roundTrip(Item.ToolCall("c1", "search", "{}")) as Item.ToolCall).providerItem)
    }

    @Test
    fun reasoningKeepsItsRawJson() {
        val raw = JSONObject("""{"type":"reasoning","id":"rs_1","encrypted_content":"abc==","summary":[]}""")
        val back = roundTrip(Item.Reasoning(raw)) as Item.Reasoning
        assertEquals(raw.toString(), back.raw.toString())
    }

    @Test
    fun turnRoundTripsDisplayAndItems() {
        val turn = ChatTurn(
            id = 3,
            user = "q?",
            entries = listOf(ChatEntry.Text("a"), ChatEntry.Status("Reading", "read_page"), ChatEntry.Text("b")),
            drafts = listOf(CardDraft("f", "b")),
            error = AiError(AiErrorKind.USAGE_LIMIT, "limit"),
            streaming = true,
        )
        val items = listOf(Item.UserText("q?"), Item.Reasoning(JSONObject("""{"id":"r"}""")), Item.AssistantText("ab"))
        val (back, backItems) = ChatCodec.turn(3, ChatCodec.turn(turn, items))
        assertEquals(turn.copy(streaming = false), back)
        assertEquals(listOf("UserText", "Reasoning", "AssistantText"), backItems.map { it::class.simpleName })
        assertEquals("""{"id":"r"}""", (backItems[1] as Item.Reasoning).raw.toString())
    }

    @Test
    fun turnWithoutErrorOrItems() {
        val turn = ChatTurn(0, "q", streaming = false)
        val (back, items) = ChatCodec.turn(0, ChatCodec.turn(turn, emptyList()))
        assertEquals(turn, back)
        assertNull(back.error)
        assertTrue(items.isEmpty())
    }

    @Test
    fun titleIsTheFirstMessageTrimmed() {
        assertEquals("What is entropy?", chatTitle("  What is\n entropy?  "))
        val long = chatTitle("word ".repeat(30))
        assertEquals(60, long.length)
        assertTrue(long.endsWith("…"))
    }
}
