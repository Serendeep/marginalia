package com.serendeep.marginalia.ai.ui

import com.serendeep.marginalia.ai.AiError
import com.serendeep.marginalia.ai.AiErrorKind
import com.serendeep.marginalia.ai.AiEvent
import com.serendeep.marginalia.ai.AiProvider
import com.serendeep.marginalia.ai.AiRequest
import com.serendeep.marginalia.ai.ChatModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeProvider(private val events: () -> Flow<AiEvent>) : AiProvider {
    override val name = "fake"
    override fun stream(request: AiRequest) = events()
    override suspend fun models() = emptyList<ChatModel>()
}

class AiRunnerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val request = AiRequest("i", "t")

    @After
    fun tearDown() = scope.cancel()

    private fun runner(events: () -> Flow<AiEvent>) = AiRunner(scope, { FakeProvider(events) }, flushMs = 5)

    private suspend fun AiRunner.settled(): AiRunState = withTimeout(5_000) { state.first { it != AiRunState.Streaming } }

    @Test
    fun streamsDeltasThenCompletes() = runBlocking {
        val r = runner { flow { emit(AiEvent.Delta("Hel")); emit(AiEvent.Delta("lo")); emit(AiEvent.Completed) } }
        assertEquals(AiRunState.Idle, r.state.value)
        r.start { request }
        assertEquals(AiRunState.Done, r.settled())
        assertEquals("Hello", r.text.value)
    }

    @Test
    fun planLimitFailureKeepsPartialTextAndMessage() = runBlocking {
        val error = AiError(AiErrorKind.USAGE_LIMIT, "ChatGPT plan limit reached")
        val r = runner { flow { emit(AiEvent.Delta("par")); emit(AiEvent.Failed(error)) } }
        r.start { request }
        assertEquals(AiRunState.Failed(error), r.settled())
        assertEquals("par", r.text.value)
        assertTrue(!error.needsSetup())
    }

    @Test
    fun notConnectedNeedsSetup() {
        assertTrue(AiError(AiErrorKind.NOT_CONNECTED, "x").needsSetup())
        assertTrue(AiError(AiErrorKind.NO_MODEL, "x").needsSetup())
    }

    @Test
    fun stopCancelsTheStreamAndKeepsText() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val r = runner { flow { emit(AiEvent.Delta("abc")); gate.await(); emit(AiEvent.Delta("never")); emit(AiEvent.Completed) } }
        r.start { request }
        withTimeout(5_000) { r.text.first { it == "abc" } }
        r.stop()
        gate.complete(Unit)
        assertEquals(AiRunState.Stopped, r.state.value)
        Thread.sleep(50)
        assertEquals("abc", r.text.value)
        assertEquals(AiRunState.Stopped, r.state.value)
    }

    @Test
    fun thrownErrorsBecomeFailed() = runBlocking {
        val r = runner { flow { throw IllegalStateException("boom with secrets") } }
        r.start { request }
        val state = r.settled()
        assertTrue(state is AiRunState.Failed)
        assertTrue(!(state as AiRunState.Failed).error.message.contains("secrets"))
    }

    @Test
    fun nullRequestShowsTheLocalMessageWithoutStreaming() = runBlocking {
        val r = runner { flow { error("must not stream") } }
        r.start("nothing found") { null }
        assertEquals(AiRunState.Done, r.settled())
        assertEquals("nothing found", r.text.value)
    }

    @Test
    fun resetReturnsToIdle() = runBlocking {
        val r = runner { flow { emit(AiEvent.Delta("x")); emit(AiEvent.Completed) } }
        r.start { request }
        r.settled()
        r.reset()
        assertEquals(AiRunState.Idle, r.state.value)
        assertEquals("", r.text.value)
    }
}
