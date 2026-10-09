package com.serendeep.marginalia.ai

import com.serendeep.marginalia.ai.agent.TurnEvent
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
                "response.output_text.done" -> yield(AiEvent.TextDone)
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

    /** Responses stream for a tool-calling turn: function calls and reasoning arrive as finished output items. */
    fun responsesTurn(lines: Sequence<String>): Sequence<TurnEvent> = sequence {
        for (e in events(lines)) {
            if (e.data == "[DONE]") continue
            val json = try { JSONObject(e.data) } catch (_: Exception) { continue }
            when (json.optString("type").ifEmpty { e.event.orEmpty() }) {
                "response.output_text.delta" -> {
                    val d = json.optString("delta")
                    if (d.isNotEmpty()) yield(TurnEvent.TextDelta(d))
                }
                "response.output_text.done" -> yield(TurnEvent.TextDone)
                "response.output_item.done" -> {
                    val item = json.optJSONObject("item") ?: continue
                    when (item.optString("type")) {
                        "function_call" -> yield(
                            TurnEvent.ToolCall(item.optString("call_id"), item.optString("name"), item.optString("arguments").ifEmpty { "{}" }, item),
                        )
                        "reasoning" -> yield(TurnEvent.Reasoning(item))
                    }
                }
                "response.completed", "response.incomplete" -> {
                    yield(TurnEvent.Completed)
                    return@sequence
                }
                "response.failed", "error" -> {
                    val err = json.optJSONObject("response")?.optJSONObject("error") ?: json.optJSONObject("error") ?: json
                    val code = err.optString("code").ifEmpty { null }
                    yield(TurnEvent.Failed(usageError(code, err.optString("message").ifEmpty { "Request failed" })))
                    return@sequence
                }
            }
        }
        yield(TurnEvent.Failed(protocolEnd))
    }

    private class PendingCall(var id: String = "", var name: String = "", val args: StringBuilder = StringBuilder())

    /** Chat-completions stream for a tool-calling turn; call fragments are joined by their `index`. */
    fun chatTurn(lines: Sequence<String>): Sequence<TurnEvent> = sequence {
        val calls = sortedMapOf<Int, PendingCall>()
        var finish: String? = null
        var sawText = false
        suspend fun SequenceScope<TurnEvent>.finishUp() {
            if (sawText) yield(TurnEvent.TextDone)
            for ((i, c) in calls) {
                yield(TurnEvent.ToolCall(c.id.ifEmpty { "call_$i" }, c.name, c.args.toString().ifEmpty { "{}" }))
            }
            calls.clear()
            sawText = false
        }
        for (e in events(lines)) {
            if (e.data == "[DONE]") break
            val json = try { JSONObject(e.data) } catch (_: Exception) { continue }
            val error = json.optJSONObject("error")
            if (error != null) {
                yield(TurnEvent.Failed(AiError(AiErrorKind.SERVER, error.optString("message").ifEmpty { "Request failed" })))
                return@sequence
            }
            val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: continue
            val delta = choice.optJSONObject("delta")
            val content = delta?.optString("content").orEmpty()
            if (content.isNotEmpty()) {
                sawText = true
                yield(TurnEvent.TextDelta(content))
            }
            val fragments = delta?.optJSONArray("tool_calls")
            if (fragments != null) {
                for (k in 0 until fragments.length()) {
                    val f = fragments.optJSONObject(k) ?: continue
                    val call = calls.getOrPut(f.optInt("index", k)) { PendingCall() }
                    f.optString("id").takeIf { it.isNotEmpty() }?.let { call.id = it }
                    val fn = f.optJSONObject("function") ?: continue
                    fn.optString("name").takeIf { it.isNotEmpty() }?.let { call.name = it }
                    call.args.append(fn.optString("arguments"))
                }
            }
            val reason = choice.optString("finish_reason")
            if (reason.isNotEmpty() && reason != "null") {
                finish = reason
                finishUp()
            }
        }
        if (finish == null) yield(TurnEvent.Failed(protocolEnd)) else yield(TurnEvent.Completed)
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
