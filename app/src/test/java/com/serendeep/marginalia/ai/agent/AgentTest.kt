package com.serendeep.marginalia.ai.agent

import com.serendeep.marginalia.ai.AiError
import com.serendeep.marginalia.ai.AiErrorKind
import com.serendeep.marginalia.ai.AiEvent
import com.serendeep.marginalia.ai.AiProvider
import com.serendeep.marginalia.ai.AiRequest
import com.serendeep.marginalia.ai.AiTask
import com.serendeep.marginalia.ai.CardDraft
import com.serendeep.marginalia.ai.ChatModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class TurnCall(val input: List<Item>, val tools: List<ToolSpec>)

private class ScriptedProvider(
    override val pageImages: Boolean = false,
    private val script: (round: Int, tools: List<ToolSpec>) -> List<TurnEvent>,
) : AiProvider {
    val calls = mutableListOf<TurnCall>()
    override val name = "scripted"
    override fun stream(request: AiRequest): Flow<AiEvent> = emptyFlow()
    override suspend fun models() = emptyList<ChatModel>()
    override fun turn(input: List<Item>, tools: List<ToolSpec>, instructions: String, task: AiTask): Flow<TurnEvent> {
        calls += TurnCall(input, tools)
        return script(calls.size - 1, tools).asFlow()
    }
}

private class FakeTools(private val handler: suspend (String, JSONObject) -> ToolOutput) : ToolExecutor {
    val executed = mutableListOf<String>()
    override fun specs(pageImages: Boolean) =
        listOf(ToolSpec("echo", "echo", JSONObject()), ToolSpec("view_page", "v", JSONObject())).filter { pageImages || it.name != "view_page" }
    override suspend fun label(name: String, args: JSONObject) = "Running $name"
    override suspend fun execute(name: String, args: JSONObject): ToolOutput {
        executed += name
        return handler(name, args)
    }
}

private fun call(id: String, name: String = "echo", args: String = "{}") = TurnEvent.ToolCall(id, name, args, JSONObject().put("call_id", id))

class AgentTest {
    private val user = listOf<Item>(Item.UserText("question"))

    private fun run(provider: ScriptedProvider, tools: FakeTools, context: AgentContext? = null, timeoutMs: Long = 5_000, rounds: Int = 8) =
        runBlocking { Agent({ provider }, tools, timeoutMs, rounds).run(user, context).toList() }

    @Test
    fun toolCallThenFinalAnswer() {
        val provider = ScriptedProvider { round, _ ->
            if (round == 0) {
                listOf(TurnEvent.Reasoning(JSONObject().put("id", "rs")), TurnEvent.TextDelta("Looking. "), call("c1", args = """{"q":"x"}"""), TurnEvent.Completed)
            } else {
                listOf(TurnEvent.TextDelta("Answer [A p.1]"), TurnEvent.TextDone, TurnEvent.Completed)
            }
        }
        val tools = FakeTools { _, args -> ToolOutput("got ${args.getString("q")}") }
        val events = run(provider, tools)

        assertEquals(AgentEvent.Status("Running echo", "echo"), events.filterIsInstance<AgentEvent.Status>().single())
        assertEquals("Looking. \n\nAnswer [A p.1]", events.filterIsInstance<AgentEvent.Text>().joinToString("") { it.delta })
        val done = events.last() as AgentEvent.Done
        assertEquals(
            listOf("Reasoning", "AssistantText", "ToolCall", "ToolResult", "AssistantText"),
            done.newItems.map { it::class.simpleName },
        )
        assertEquals("got x", (done.newItems[3] as Item.ToolResult).output)
        assertEquals("c1", (done.newItems[3] as Item.ToolResult).callId)
        assertTrue(provider.calls[1].input.any { it is Item.ToolResult })
        assertEquals(2, provider.calls.size)
    }

    @Test
    fun finalTurnWithoutToolsAfterEightRounds() {
        val provider = ScriptedProvider { _, tools ->
            if (tools.isNotEmpty()) listOf(call("c"), TurnEvent.Completed) else listOf(TurnEvent.TextDelta("Forced"), TurnEvent.Completed)
        }
        val tools = FakeTools { _, _ -> ToolOutput("ok") }
        val events = run(provider, tools)
        assertEquals(9, provider.calls.size)
        assertEquals(8, tools.executed.size)
        assertTrue(provider.calls.last().tools.isEmpty())
        assertTrue(provider.calls.dropLast(1).all { it.tools.isNotEmpty() })
        assertEquals("Forced", events.filterIsInstance<AgentEvent.Text>().last().delta)
        assertTrue(events.last() is AgentEvent.Done)
    }

    @Test
    fun toolErrorBecomesResultAndRunContinues() {
        val provider = ScriptedProvider { round, _ -> if (round == 0) listOf(call("c1")) else listOf(TurnEvent.TextDelta("ok")) }
        val tools = FakeTools { _, _ -> error("boom") }
        val events = run(provider, tools)
        val result = (events.last() as AgentEvent.Done).newItems.filterIsInstance<Item.ToolResult>().single()
        assertEquals("Error: boom", result.output)
    }

    @Test
    fun invalidArgumentsDoNotReachTheTool() {
        val provider = ScriptedProvider { round, _ -> if (round == 0) listOf(call("c1", args = "{nope")) else listOf(TurnEvent.TextDelta("ok")) }
        val tools = FakeTools { _, _ -> ToolOutput("never") }
        val events = run(provider, tools)
        assertTrue(tools.executed.isEmpty())
        assertTrue((events.last() as AgentEvent.Done).newItems.filterIsInstance<Item.ToolResult>().single().output.startsWith("Error"))
    }

    @Test
    fun longOutputIsTruncated() {
        val provider = ScriptedProvider { round, _ -> if (round == 0) listOf(call("c1")) else listOf(TurnEvent.TextDelta("ok")) }
        val tools = FakeTools { _, _ -> ToolOutput("x".repeat(7_000)) }
        val out = (run(provider, tools).last() as AgentEvent.Done).newItems.filterIsInstance<Item.ToolResult>().single().output
        assertEquals(6_000 + "…(truncated)".length, out.length)
        assertTrue(out.endsWith("…(truncated)"))
    }

    @Test
    fun slowToolTimesOut() {
        val provider = ScriptedProvider { round, _ -> if (round == 0) listOf(call("c1")) else listOf(TurnEvent.TextDelta("ok")) }
        val tools = FakeTools { _, _ -> delay(2_000); ToolOutput("late") }
        val out = (run(provider, tools, timeoutMs = 50).last() as AgentEvent.Done).newItems.filterIsInstance<Item.ToolResult>().single().output
        assertTrue(out.startsWith("Error"))
    }

    @Test
    fun draftsAreEmittedAndImageFollowsResults() {
        val image = byteArrayOf(1, 2)
        val provider = ScriptedProvider(pageImages = true) { round, _ ->
            if (round == 0) listOf(call("c1", "view_page"), call("c2")) else listOf(TurnEvent.TextDelta("ok"))
        }
        val tools = FakeTools { name, _ ->
            if (name == "view_page") ToolOutput("Page image attached below.", image = image) else ToolOutput("d", listOf(CardDraft("f", "b")))
        }
        val events = run(provider, tools)
        assertEquals(listOf(CardDraft("f", "b")), events.filterIsInstance<AgentEvent.Drafts>().single().cards)
        val items = (events.last() as AgentEvent.Done).newItems
        val firstUser = items.indexOfFirst { it is Item.UserText }
        assertTrue(items.subList(0, firstUser).count { it is Item.ToolResult } == 2)
        assertEquals(listOf(image), (items[firstUser] as Item.UserText).images)
    }

    @Test
    fun pageToolIsOfferedOnlyWhenProviderCanSeeImages() {
        val blind = ScriptedProvider(pageImages = false) { _, _ -> listOf(TurnEvent.TextDelta("a")) }
        run(blind, FakeTools { _, _ -> ToolOutput("") })
        assertEquals(listOf("echo"), blind.calls.single().tools.map { it.name })
        val sighted = ScriptedProvider(pageImages = true) { _, _ -> listOf(TurnEvent.TextDelta("a")) }
        run(sighted, FakeTools { _, _ -> ToolOutput("") })
        assertEquals(listOf("echo", "view_page"), sighted.calls.single().tools.map { it.name })
    }

    @Test
    fun failedTurnEndsTheRunWithoutDone() {
        val error = AiError(AiErrorKind.SERVER, "nope")
        val events = run(ScriptedProvider { _, _ -> listOf(TurnEvent.TextDelta("par"), TurnEvent.Failed(error)) }, FakeTools { _, _ -> ToolOutput("") })
        assertEquals(AgentEvent.Failed(error), events.last())
        assertTrue(events.none { it is AgentEvent.Done })
    }

    @Test
    fun contextIsPrependedToTheLatestUserItem() {
        val provider = ScriptedProvider { _, _ -> listOf(TurnEvent.TextDelta("a")) }
        val image = byteArrayOf(9)
        run(provider, FakeTools { _, _ -> ToolOutput("") }, AgentContext("<context>here</context>", listOf(image)))
        val sent = provider.calls.single().input.single() as Item.UserText
        assertEquals("<context>here</context>\n\nquestion", sent.text)
        assertEquals(listOf(image), sent.images)
    }
}
