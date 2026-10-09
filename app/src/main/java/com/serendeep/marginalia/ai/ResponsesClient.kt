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
            sseFlow(Sse::responses, retryOnUnauthorized = true) { retry ->
                Http.postJson("$BASE/responses", auth.validAccessToken(force = retry), requestBody(model, request, effort))
            },
        )
    }

    companion object {
        private const val BASE = AuthFlow.RESOURCE

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
