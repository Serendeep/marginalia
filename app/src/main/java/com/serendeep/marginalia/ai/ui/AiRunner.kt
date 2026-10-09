package com.serendeep.marginalia.ai.ui

import android.util.Log
import com.serendeep.marginalia.BuildConfig
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

fun aiReady(config: AiConfig, status: ChatGptStatus, allowed: Boolean = true): Boolean = allowed && when (config.provider) {
    ProviderChoice.CHATGPT -> status is ChatGptStatus.Connected
    ProviderChoice.COMPATIBLE -> config.baseUrl.isNotBlank()
}

fun AiError.needsSetup(): Boolean =
    kind == AiErrorKind.NOT_CONNECTED || kind == AiErrorKind.UNAUTHORIZED || kind == AiErrorKind.NO_MODEL

const val PUBLISH_TIMEOUT_MS = 2_500L

/**
 * The part of [buffer] that is safe to show while streaming: everything up to and including the last blank line
 * that isn't inside a code fence or a `$$` block. With no such boundary for [msSinceLastPublish] >= [PUBLISH_TIMEOUT_MS],
 * the whole buffer is returned.
 */
fun publishable(buffer: String, msSinceLastPublish: Long): String {
    var boundary = 0
    var fenced = false
    var math = false
    var start = 0
    while (true) {
        val nl = buffer.indexOf('\n', start)
        if (nl < 0) break
        val line = buffer.substring(start, nl).trim()
        when {
            fenced -> if (line.startsWith("```")) fenced = false
            line.startsWith("```") -> fenced = true
            else -> {
                if ((line.split("\$\$").size - 1) % 2 == 1) math = !math
                if (line.isEmpty() && !math && start > 0) boundary = nl + 1
            }
        }
        start = nl + 1
    }
    return if (boundary == 0 && msSinceLastPublish >= PUBLISH_TIMEOUT_MS) buffer else buffer.substring(0, boundary)
}

/**
 * Runs one AI request at a time. Streamed text is buffered and published to [text] at most every
 * [flushMs], and only up to a paragraph boundary, so collectors recompose per block rather than per token.
 */
class AiRunner(
    private val scope: CoroutineScope,
    private val provider: () -> AiProvider,
    private val flushMs: Long = 33,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow<AiRunState>(AiRunState.Idle)
    val state: StateFlow<AiRunState> = _state.asStateFlow()

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val buffer = StringBuilder()
    private var dirty = false
    private var published = 0
    private var lastPublishAt = 0L
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
                    flush(final = _state.value != AiRunState.Streaming)
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
                var lastDeltaAt = now()
                var textDoneAt = 0L
                provider().stream(request).collect { event ->
                    when (event) {
                        is AiEvent.Delta -> {
                            lastDeltaAt = now()
                            synchronized(buffer) {
                                buffer.append(event.text)
                                dirty = true
                            }
                        }
                        AiEvent.TextDone -> {
                            textDoneAt = now()
                            timing("last delta -> text done", textDoneAt - lastDeltaAt)
                            finish(AiRunState.Done)
                        }
                        AiEvent.Completed, is AiEvent.Incomplete -> {
                            if (textDoneAt != 0L) timing("text done -> completed", now() - textDoneAt)
                            else timing("last delta -> completed", now() - lastDeltaAt)
                            finish(AiRunState.Done)
                        }
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
            flush(final = true)
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
            published = 0
        }
        lastPublishAt = now()
        _text.value = ""
    }

    private fun finish(state: AiRunState) {
        // Once the text is complete, a dropped connection while draining must not replace the answer.
        if (_state.value == AiRunState.Done && state is AiRunState.Failed) return
        flush(final = true)
        _state.value = state
    }

    private fun timing(label: String, ms: Long) {
        if (BuildConfig.DEBUG) Log.d("AiTiming", "$label: $ms ms")
    }

    private fun flush(final: Boolean) {
        val snapshot = synchronized(buffer) {
            if (!dirty) return
            val all = buffer.toString()
            val next = if (final) {
                all
            } else {
                val since = now() - lastPublishAt
                val part = publishable(all, since)
                if (part.length > published) part else if (since >= PUBLISH_TIMEOUT_MS) all else part
            }
            if (next.length <= published && !final) return
            published = next.length
            dirty = published < all.length
            next
        }
        lastPublishAt = now()
        _text.value = snapshot
    }
}
