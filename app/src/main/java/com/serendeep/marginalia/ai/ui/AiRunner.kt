package com.serendeep.marginalia.ai.ui

import com.serendeep.marginalia.ai.AiConfig
import com.serendeep.marginalia.ai.AiError
import com.serendeep.marginalia.ai.AiErrorKind
import com.serendeep.marginalia.ai.AiEvent
import com.serendeep.marginalia.ai.AiException
import com.serendeep.marginalia.ai.AiProvider
import com.serendeep.marginalia.ai.AiRequest
import com.serendeep.marginalia.ai.ChatGptStatus
import com.serendeep.marginalia.ai.ProviderChoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AiRunState {
    data object Idle : AiRunState
    data object Streaming : AiRunState
    data object Done : AiRunState
    data object Stopped : AiRunState
    data class Failed(val error: AiError) : AiRunState
}

fun aiReady(config: AiConfig, status: ChatGptStatus): Boolean = when (config.provider) {
    ProviderChoice.CHATGPT -> status is ChatGptStatus.Connected
    ProviderChoice.COMPATIBLE -> config.baseUrl.isNotBlank()
}

fun AiError.needsSetup(): Boolean =
    kind == AiErrorKind.NOT_CONNECTED || kind == AiErrorKind.UNAUTHORIZED || kind == AiErrorKind.NO_MODEL

/**
 * Runs one AI request at a time. Streamed text is buffered and published to [text] at most every
 * [flushMs], so collectors recompose per frame rather than per token.
 */
class AiRunner(
    private val scope: CoroutineScope,
    private val provider: () -> AiProvider,
    private val flushMs: Long = 33,
) {
    private val _state = MutableStateFlow<AiRunState>(AiRunState.Idle)
    val state: StateFlow<AiRunState> = _state.asStateFlow()

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val buffer = StringBuilder()
    private var dirty = false
    private var job: Job? = null

    /**
     * [build] may do slow work (page text, rendering); it runs inside the same cancellable job.
     * Returning null shows [emptyMessage] instead of calling the model.
     */
    fun start(emptyMessage: String = "", build: suspend () -> AiRequest?) {
        job?.cancel()
        clear()
        _state.value = AiRunState.Streaming
        job = scope.launch {
            val ticker = launch {
                while (true) {
                    delay(flushMs)
                    flush()
                }
            }
            try {
                val request = build()
                if (request == null) {
                    synchronized(buffer) {
                        buffer.append(emptyMessage)
                        dirty = true
                    }
                    finish(AiRunState.Done)
                    return@launch
                }
                provider().stream(request).collect { event ->
                    when (event) {
                        is AiEvent.Delta -> synchronized(buffer) {
                            buffer.append(event.text)
                            dirty = true
                        }
                        AiEvent.Completed, is AiEvent.Incomplete -> finish(AiRunState.Done)
                        is AiEvent.Failed -> finish(AiRunState.Failed(event.error))
                    }
                }
                if (_state.value == AiRunState.Streaming) finish(AiRunState.Done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                finish(AiRunState.Failed(e.error))
            } catch (e: Exception) {
                finish(AiRunState.Failed(AiError(AiErrorKind.NETWORK, "Something went wrong — try again")))
            } finally {
                ticker.cancel()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        if (_state.value == AiRunState.Streaming) {
            flush()
            _state.value = AiRunState.Stopped
        }
    }

    fun reset() {
        job?.cancel()
        job = null
        clear()
        _state.value = AiRunState.Idle
    }

    fun showLocal(message: String) {
        reset()
        _text.value = message
        _state.value = AiRunState.Done
    }

    private fun clear() {
        synchronized(buffer) {
            buffer.setLength(0)
            dirty = false
        }
        _text.value = ""
    }

    private fun finish(state: AiRunState) {
        flush()
        _state.value = state
    }

    private fun flush() {
        val snapshot = synchronized(buffer) {
            if (!dirty) return
            dirty = false
            buffer.toString()
        }
        _text.value = snapshot
    }
}
