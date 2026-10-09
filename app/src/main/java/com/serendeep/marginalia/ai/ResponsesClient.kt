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
class ResponsesClient @Inject constructor(
    private val auth: ChatGptAuth,
    private val resolver: ModelResolver,
) {

    suspend fun listModels(): List<ChatModel> {
        val first = auth.validAccessToken()
        return withContext(Dispatchers.IO) {
            try {
                var (code, body) = Http.get("$BASE/models", first)
                if (code == 401) {
                    val retry = Http.get("$BASE/models", auth.validAccessToken(force = true))
                    code = retry.first
                    body = retry.second
                }
                if (code !in 200..299) throw AiException(Http.httpError(code, body))
                parseModels(body)
            } catch (e: IOException) {
                throw AiException(AiError(AiErrorKind.NETWORK, "Connection problem \u2014 check your network"))
            }
        }
    }

    fun stream(request: AiRequest): Flow<AiEvent> = flow {
        val resolved = resolver.resolve(request.task)
        val model = request.model ?: resolved.model
        if (model == null) {
            emit(AiEvent.Failed(AiError(AiErrorKind.NO_MODEL, "Pick a ChatGPT model in settings")))
            return@flow
        }
        val effort = request.effort ?: resolved.effort
        emitAll(
            sseFlow(Sse::responses, AiEvent::Failed, retryOnUnauthorized = true) { retry ->
                Http.postJson("$BASE/responses", auth.validAccessToken(force = retry), requestBody(model, request, effort))
            },
        )
    }

    fun turn(input: List<Item>, tools: List<ToolSpec>, instructions: String, task: AiTask): Flow<TurnEvent> = flow {
        val resolved = resolver.resolve(task)
        val model = resolved.model
        if (model == null) {
            emit(TurnEvent.Failed(AiError(AiErrorKind.NO_MODEL, "Pick a ChatGPT model in settings")))
            return@flow
        }
        emitAll(
            sseFlow(Sse::responsesTurn, TurnEvent::Failed, retryOnUnauthorized = true) { retry ->
                val body = turnBody(model, resolved.effort, instructions, input, tools)
                Http.postJson("$BASE/responses", auth.validAccessToken(force = retry), body)
            },
        )
    }

    companion object {
        private const val BASE = AuthFlow.RESOURCE

        fun turnBody(model: String, effort: Effort?, instructions: String, input: List<Item>, tools: List<ToolSpec>): JSONObject =
            JSONObject()
                .put("model", model)
                .put("instructions", instructions)
                .put("input", JSONArray().apply { input.forEach { put(inputItem(it)) } })
                .apply {
                    if (tools.isNotEmpty()) {
                        put(
                            "tools",
                            JSONArray().apply {
                                tools.forEach {
                                    put(
                                        JSONObject().put("type", "function").put("name", it.name)
                                            .put("description", it.description).put("parameters", it.parametersJsonSchema)
                                            .put("strict", false),
                                    )
                                }
                            },
                        )
                    }
                }
                .put("store", false)
                .put("stream", true)
                .put("include", JSONArray().put("reasoning.encrypted_content"))
                .apply { if (effort != null) put("reasoning", JSONObject().put("effort", effort.wire)) }

        private fun inputItem(item: Item): JSONObject = when (item) {
            is Item.UserText -> {
                val content = JSONArray().put(JSONObject().put("type", "input_text").put("text", item.text))
                item.images.forEach {
                    val url = "data:image/png;base64," + Base64.encodeToString(it, Base64.NO_WRAP)
                    content.put(JSONObject().put("type", "input_image").put("image_url", url))
                }
                JSONObject().put("role", "user").put("content", content)
            }
            is Item.AssistantText -> JSONObject().put("role", "assistant").put(
                "content",
                JSONArray().put(JSONObject().put("type", "output_text").put("text", item.text)),
            )
            is Item.ToolCall -> item.providerItem ?: JSONObject().put("type", "function_call")
                .put("call_id", item.callId).put("name", item.name).put("arguments", item.argsJson)
            is Item.ToolResult -> JSONObject().put("type", "function_call_output")
                .put("call_id", item.callId).put("output", item.output)
            is Item.Reasoning -> item.raw
        }

        fun requestBody(model: String, r: AiRequest, effort: Effort? = r.effort): JSONObject {
            val content = JSONArray().put(JSONObject().put("type", "input_text").put("text", r.text))
            r.imagePng?.let {
                val url = "data:image/png;base64," + Base64.encodeToString(it, Base64.NO_WRAP)
                content.put(JSONObject().put("type", "input_image").put("image_url", url))
            }
            return JSONObject()
                .put("model", model)
                .put("instructions", r.instructions)
                .put("input", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
                .put("store", false)
                .put("stream", true)
                .apply { if (effort != null) put("reasoning", JSONObject().put("effort", effort.wire)) }
        }

        fun parseModels(body: String): List<ChatModel> {
            val arr = JSONObject(body).optJSONArray("models") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                .filter { it.optString("visibility") == "list" && it.optString("slug").isNotEmpty() }
                .map {
                    ChatModel(
                        it.optString("slug"),
                        it.optString("display_name").ifEmpty { it.optString("slug") },
                        parseEfforts(it.optJSONArray("supported_reasoning_levels")),
                    )
                }
        }

        /** Levels arrive either as strings or as objects with an `effort` field. */
        fun parseEfforts(levels: JSONArray?): List<Effort> {
            if (levels == null) return emptyList()
            return (0 until levels.length()).mapNotNull { i ->
                val item = levels.opt(i)
                Effort.parse(if (item is JSONObject) item.optString("effort") else item as? String)
            }.distinct()
        }
    }
}
