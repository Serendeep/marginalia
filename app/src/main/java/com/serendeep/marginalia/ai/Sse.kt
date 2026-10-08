package com.serendeep.marginalia.ai

import org.json.JSONObject

class SseEvent(val event: String?, val data: String)

object Sse {
    private val protocolEnd = AiError(AiErrorKind.PROTOCOL, "The response ended before it completed")

    /** Each `data:` line is one event; a preceding `event:` line names it. Multi-line data is not joined. */
    fun events(lines: Sequence<String>): Sequence<SseEvent> = sequence {
        var event: String? = null
        for (line in lines) {
            when {
                line.isEmpty() -> event = null
                line.startsWith("event:") -> event = line.substring(6).trim()
                line.startsWith("data:") -> {
                    yield(SseEvent(event, line.substring(5).trim()))
                    event = null
                }
            }
        }
    }

    fun responses(lines: Sequence<String>): Sequence<AiEvent> = sequence {
        for (e in events(lines)) {
            if (e.data == "[DONE]") continue
            val json = try { JSONObject(e.data) } catch (_: Exception) { continue }
            when (json.optString("type").ifEmpty { e.event.orEmpty() }) {
                "response.output_text.delta" -> {
                    val d = json.optString("delta")
                    if (d.isNotEmpty()) yield(AiEvent.Delta(d))
                }
                "response.completed" -> {
                    yield(AiEvent.Completed)
                    return@sequence
                }
                "response.incomplete" -> {
                    val reason = json.optJSONObject("response")?.optJSONObject("incomplete_details")?.optString("reason")
                    yield(AiEvent.Incomplete(reason?.ifEmpty { null } ?: "unknown"))
                    return@sequence
                }
                "response.failed", "error" -> {
                    val err = json.optJSONObject("response")?.optJSONObject("error") ?: json.optJSONObject("error") ?: json
                    val code = err.optString("code").ifEmpty { null }
                    yield(AiEvent.Failed(usageError(code, err.optString("message").ifEmpty { "Request failed" })))
                    return@sequence
                }
            }
        }
        yield(AiEvent.Failed(protocolEnd))
    }

    fun chatCompletions(lines: Sequence<String>): Sequence<AiEvent> = sequence {
        var finish: String? = null
        for (e in events(lines)) {
            if (e.data == "[DONE]") {
                yield(if (finish == "length") AiEvent.Incomplete("length") else AiEvent.Completed)
                return@sequence
            }
            val json = try { JSONObject(e.data) } catch (_: Exception) { continue }
            val error = json.optJSONObject("error")
            if (error != null) {
                yield(AiEvent.Failed(AiError(AiErrorKind.SERVER, error.optString("message").ifEmpty { "Request failed" })))
                return@sequence
            }
            val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: continue
            val content = choice.optJSONObject("delta")?.optString("content").orEmpty()
            if (content.isNotEmpty()) yield(AiEvent.Delta(content))
            val reason = choice.optString("finish_reason")
            if (reason.isNotEmpty() && reason != "null") finish = reason
        }
        yield(
            when (finish) {
                null -> AiEvent.Failed(protocolEnd)
                "length" -> AiEvent.Incomplete("length")
                else -> AiEvent.Completed
            },
        )
    }
}
