package com.serendeep.marginalia.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.HttpURLConnection

/**
 * Runs [open] on IO, optionally reopening once after a 401, and streams [parse] over the response lines.
 * Cancelling the collector disconnects the socket so the blocking read ends.
 */
internal fun <T> sseFlow(
    parse: (Sequence<String>) -> Sequence<T>,
    failed: (AiError) -> T,
    retryOnUnauthorized: Boolean,
    open: suspend (retry: Boolean) -> HttpURLConnection,
): Flow<T> = channelFlow {
    val conn = java.util.concurrent.atomic.AtomicReference<HttpURLConnection?>()
    val reader: Job = launch(Dispatchers.IO) {
        try {
            var c = open(false).also { conn.set(it) }
            var code = c.responseCode
            if (code == 401 && retryOnUnauthorized) {
                c.disconnect()
                c = open(true).also { conn.set(it) }
                code = c.responseCode
            }
            if (code !in 200..299) {
                send(failed(Http.httpError(code, Http.readBody(c))))
                return@launch
            }
            c.inputStream.bufferedReader().use { r ->
                for (event in parse(r.lineSequence())) send(event)
            }
        } catch (e: AiException) {
            send(failed(e.error))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            if (isActive) send(failed(AiError(AiErrorKind.NETWORK, "Connection problem — check your network")))
        }
    }
    try {
        reader.join()
    } finally {
        conn.get()?.disconnect()
    }
}
