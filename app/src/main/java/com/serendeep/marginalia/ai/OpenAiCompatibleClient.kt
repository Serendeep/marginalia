package com.serendeep.marginalia.ai

import android.util.Base64
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
            sseFlow(Sse::chatCompletions, retryOnUnauthorized = false) {
                Http.postJson(baseOf(cfg.baseUrl) + "/chat/completions", cfg.apiKey, requestBody(model, request, effort))
            },
        )
    }

    companion object {
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
