package com.serendeep.marginalia.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResponsesClient @Inject constructor(private val auth: ChatGptAuth) {

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

    fun stream(request: AiRequest): Flow<AiEvent> {
        val model = request.model ?: auth.selectedModel()
            ?: return flow { emit(AiEvent.Failed(AiError(AiErrorKind.NO_MODEL, "Pick a ChatGPT model in settings"))) }
        return sseFlow(Sse::responses, retryOnUnauthorized = true) { retry ->
            Http.postJson("$BASE/responses", auth.validAccessToken(force = retry), requestBody(model, request))
        }
    }

    companion object {
        private const val BASE = AuthFlow.RESOURCE

        fun requestBody(model: String, r: AiRequest): JSONObject {
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
        }

        fun parseModels(body: String): List<ChatModel> {
            val arr = JSONObject(body).optJSONArray("models") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                .filter { it.optString("visibility") == "list" && it.optString("slug").isNotEmpty() }
                .map { ChatModel(it.optString("slug"), it.optString("display_name").ifEmpty { it.optString("slug") }) }
        }
    }
}
