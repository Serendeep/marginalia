package com.serendeep.marginalia.ai.agent

import com.serendeep.marginalia.ai.AiError
import com.serendeep.marginalia.ai.AiErrorKind
import com.serendeep.marginalia.ai.AiEvent
import com.serendeep.marginalia.ai.AiProvider
import com.serendeep.marginalia.ai.AiRequest
import com.serendeep.marginalia.ai.AiTask
import com.serendeep.marginalia.ai.ChatModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class ChatProvider(private val replies: (round: Int, input: List<Item>) -> Flow<TurnEvent>) : AiProvider {
    val inputs = mutableListOf<List<Item>>()
    override val name = "chat"
    override fun stream(request: AiRequest): Flow<AiEvent> = emptyFlow()
    override suspend fun models() = emptyList<ChatModel>()
    override fun turn(input: List<Item>, tools: List<ToolSpec>, instructions: String, task: AiTask): Flow<TurnEvent> {
        inputs += input
        return replies(inputs.size - 1, input)
    }
}

class ChatSessionTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tools = object : ToolExecutor {
        override fun specs(pageImages: Boolean) = listOf(ToolSpec("echo", "e", JSONObject()))
        override suspend fun label(name: String, args: JSONObject) = "Echoing"
        override suspend fun execute(name: String, args: JSONObject) = ToolOutput("echoed")
    }

    @After
    fun tearDown() = scope.cancel()

    private fun session(provider: ChatProvider) = ChatSession(Agent({ provider }, tools, 5_000, 8), scope) { 0L }

    private fun ChatSession.awaitIdle() = runBlocking { withTimeout(5_000) { streaming.first { !it } } }

    private fun ChatSession.sendAndWait(text: String) {
        send(text)
        awaitIdle()
    }

    @Test
    fun publishesWholeParagraphsAndRemembersTheExchange() {
        val gate = CompletableDeferred<Unit>()
        val provider = ChatProvider { round, _ ->
            flow {
                if (round == 0) {
                    emit(TurnEvent.TextDelta("First para."))
                    emit(TurnEvent.TextDelta("\n\nSecond"))
                    gate.await()
                    emit(TurnEvent.TextDelta(" para."))
                } else {
                    emit(TurnEvent.TextDelta("Again"))
                }
                emit(TurnEvent.Completed)
            }
        }
        val chat = session(provider)
        chat.send("hello")
        runBlocking { withTimeout(5_000) { chat.turns.first { (it.singleOrNull()?.entries?.size ?: 0) == 1 } } }
        val midway = chat.turns.value.single()
        assertEquals(listOf<ChatEntry>(ChatEntry.Text("First para.\n\n")), midway.entries)
        assertTrue(midway.streaming)
        assertTrue(chat.streaming.value)

        gate.complete(Unit)
        chat.awaitIdle()
        val done = chat.turns.value.single()
        assertEquals(listOf<ChatEntry>(ChatEntry.Text("First para.\n\nSecond para.")), done.entries)
        assertFalse(done.streaming)

        chat.sendAndWait("and then?")
        assertEquals(2, chat.turns.value.size)
        assertEquals(listOf("UserText", "AssistantText", "UserText"), provider.inputs.last().map { it::class.simpleName })
    }

    @Test
    fun statusLinesSplitTheTranscript() {
        val provider = ChatProvider { round, _ ->
            flow {
                if (round == 0) {
                    emit(TurnEvent.TextDelta("Checking."))
                    emit(TurnEvent.ToolCall("c1", "echo", "{}"))
                } else {
                    emit(TurnEvent.TextDelta("Result."))
                }
                emit(TurnEvent.Completed)
            }
        }
        val chat = session(provider)
        chat.sendAndWait("q")
        assertEquals(
            listOf(ChatEntry.Text("Checking."), ChatEntry.Status("Echoing", "echo"), ChatEntry.Text("Result.")),
            chat.turns.value.single().entries,
        )
    }

    @Test
    fun failureKeepsPartialTextAndForgetsTheExchange() {
        val provider = ChatProvider { _, _ ->
            flow {
                emit(TurnEvent.TextDelta("Partial"))
                emit(TurnEvent.Failed(AiError(AiErrorKind.SERVER, "down")))
            }
        }
        val chat = session(provider)
        chat.sendAndWait("q")
        val turn = chat.turns.value.single()
        assertEquals("down", turn.error?.message)
        assertEquals(listOf<ChatEntry>(ChatEntry.Text("Partial")), turn.entries)
        assertFalse(turn.streaming)
        chat.sendAndWait("retry")
        assertEquals(1, provider.inputs.last().size)
    }

    @Test
    fun newChatClearsTurnsAndHistory() {
        val provider = ChatProvider { _, _ -> flow { emit(TurnEvent.TextDelta("a")); emit(TurnEvent.Completed) } }
        val chat = session(provider)
        chat.sendAndWait("one")
        chat.newChat()
        assertTrue(chat.turns.value.isEmpty())
        chat.sendAndWait("two")
        assertEquals(1, provider.inputs.last().size)
    }

    @Test
    fun stopEndsTheReplyAndBlankSendIsIgnored() {
        val never = CompletableDeferred<Unit>()
        val provider = ChatProvider { _, _ -> flow { emit(TurnEvent.TextDelta("Hi.\n\n")); never.await() } }
        val chat = session(provider)
        chat.send("   ")
        assertTrue(chat.turns.value.isEmpty())
        chat.send("q")
        runBlocking { withTimeout(5_000) { chat.turns.first { it.singleOrNull()?.entries?.isNotEmpty() == true } } }
        chat.stop()
        assertFalse(chat.streaming.value)
        assertFalse(chat.turns.value.single().streaming)
        assertEquals(listOf<ChatEntry>(ChatEntry.Text("Hi.\n\n")), chat.turns.value.single().entries)
    }
}
