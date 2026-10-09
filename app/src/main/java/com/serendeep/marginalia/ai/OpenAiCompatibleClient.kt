package com.serendeep.marginalia.ai

import android.util.Base64
import com.serendeep.marginalia.ai.agent.Item
import com.serendeep.marginalia.ai.agent.ToolSpec
import com.serendeep.marginalia.ai.agent.TurnEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAiCompatibleClient @Inject constructor(
    private val settings: AiSettings,
    private val resolver: ModelResolver,
) {

    suspend fun listModels(): List<ChatModel> {
        val cfg = settings.config.value
        if (cfg.baseUrl.isBlank()) throw AiException(AiError(AiErrorKind.NOT_CONNECTED, "Enter a server address in settings"))
        return withContext(Dispatchers.IO) {
            try {
                val (code, body) = Http.get(baseOf(cfg.baseUrl) + "/models", cfg.apiKey)
                if (code !in 200..299) throw AiException(Http.httpError(code, body))
                parseModels(body)
            } catch (e: IOException) {
                throw AiException(AiError(AiErrorKind.NETWORK, "Couldn't reach the server"))
            }
        }
    }

    fun stream(request: AiRequest): Flow<AiEvent> = flow {
        val cfg = settings.config.value
        if (cfg.baseUrl.isBlank()) {
            emit(AiEvent.Failed(AiError(AiErrorKind.NOT_CONNECTED, "Enter a server address in settings")))
            return@flow
        }
        val resolved = resolver.resolve(request.task)
        val model = request.model ?: resolved.model
        if (model == null) {
            emit(AiEvent.Failed(AiError(AiErrorKind.NO_MODEL, "Pick a model in settings")))
            return@flow
        }
        val effort = request.effort ?: resolved.effort
        emitAll(
            sseFlow(Sse::chatCompletions, AiEvent::Failed, retryOnUnauthorized = false) {
                Http.postJson(baseOf(cfg.baseUrl) + "/chat/completions", cfg.apiKey, requestBody(model, request, effort))
            },
        )
    }

    fun turn(input: List<Item>, tools: List<ToolSpec>, instructions: String, task: AiTask): Flow<TurnEvent> = flow {
        val cfg = settings.config.value
        if (cfg.baseUrl.isBlank()) {
            emit(TurnEvent.Failed(AiError(AiErrorKind.NOT_CONNECTED, "Enter a server address in settings")))
            return@flow
        }
        val resolved = resolver.resolve(task)
        val model = resolved.model
        if (model == null) {
            emit(TurnEvent.Failed(AiError(AiErrorKind.NO_MODEL, "Pick a model in settings")))
            return@flow
        }
        emitAll(
            sseFlow(Sse::chatTurn, TurnEvent::Failed, retryOnUnauthorized = false) {
                val body = turnBody(model, resolved.effort, instructions, input, tools)
                Http.postJson(baseOf(cfg.baseUrl) + "/chat/completions", cfg.apiKey, body)
            },
        )
    }

    companion object {
        fun turnBody(model: String, effort: Effort?, instructions: String, input: List<Item>, tools: List<ToolSpec>): JSONObject =
            JSONObject()
                .put("model", model)
                .put("messages", messages(instructions, input))
                .apply {
                    if (tools.isNotEmpty()) {
                        put(
                            "tools",
                            JSONArray().apply {
                                tools.forEach {
                                    put(
                                        JSONObject().put("type", "function").put(
                                            "function",
                                            JSONObject().put("name", it.name).put("description", it.description)
                                                .put("parameters", it.parametersJsonSchema),
                                        ),
                                    )
                                }
                            },
                        )
                    }
                }
                .put("stream", true)
                .apply { if (effort != null) put("reasoning_effort", effort.wire) }

        /** Consecutive tool calls fold into the assistant message before them; reasoning items have no equivalent here. */
        fun messages(instructions: String, input: List<Item>): JSONArray {
            val out = JSONArray().put(JSONObject().put("role", "system").put("content", instructions))
            var lastAssistant: JSONObject? = null
            for (item in input) {
                when (item) {
                    is Item.UserText -> {
                        lastAssistant = null
                        val content: Any = if (item.images.isEmpty()) {
                            item.text
                        } else {
                            JSONArray().put(JSONObject().put("type", "text").put("text", item.text)).also { parts ->
                                item.images.forEach {
                                    val url = "data:image/png;base64," + Base64.encodeToString(it, Base64.NO_WRAP)
                                    parts.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", url)))
                                }
                            }
                        }
                        out.put(JSONObject().put("role", "user").put("content", content))
                    }
                    is Item.AssistantText -> {
                        lastAssistant = JSONObject().put("role", "assistant").put("content", item.text)
                        out.put(lastAssistant)
                    }
                    is Item.ToolCall -> {
                        val msg = lastAssistant ?: JSONObject().put("role", "assistant").put("content", JSONObject.NULL)
                            .also { out.put(it); lastAssistant = it }
                        val calls = msg.optJSONArray("tool_calls") ?: JSONArray().also { msg.put("tool_calls", it) }
                        calls.put(
                            JSONObject().put("id", item.callId).put("type", "function").put(
                                "function",
                                JSONObject().put("name", item.name).put("arguments", item.argsJson),
                            ),
                        )
                    }
                    is Item.ToolResult -> out.put(
                        JSONObject().put("role", "tool").put("tool_call_id", item.callId).put("content", item.output),
                    )
                    is Item.Reasoning -> Unit
                }
            }
            return out
        }

        fun baseOf(url: String): String {
            val trimmed = url.trim().trimEnd('/')
            return if (trimmed.endsWith("/v1")) trimmed else "$trimmed/v1"
        }

        fun requestBody(model: String, r: AiRequest, effort: Effort? = r.effort): JSONObject {
            val user = if (r.imagePng == null) {
                JSONObject().put("role", "user").put("content", r.text)
            } else {
                val url = "data:image/png;base64," + Base64.encodeToString(r.imagePng, Base64.NO_WRAP)
                val parts = JSONArray()
                    .put(JSONObject().put("type", "text").put("text", r.text))
                    .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", url)))
                JSONObject().put("role", "user").put("content", parts)
            }
            return JSONObject()
                .put("model", model)
                .put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", r.instructions)).put(user))
                .put("stream", true)
                .apply { if (effort != null) put("reasoning_effort", effort.wire) }
        }

        fun parseModels(body: String): List<ChatModel> {
            val arr = JSONObject(body).optJSONArray("data") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                .filter { it.optString("id").isNotEmpty() }
                .map {
                    val id = it.optString("id")
                    ChatModel(id, id, ResponsesClient.parseEfforts(it.optJSONArray("supported_reasoning_levels")))
                }
        }
    }
}
