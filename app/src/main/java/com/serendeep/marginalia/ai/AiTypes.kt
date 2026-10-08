package com.serendeep.marginalia.ai

class AiRequest(
    val instructions: String,
    val text: String,
    val imagePng: ByteArray? = null,
    val model: String? = null,
)

data class ChatModel(val slug: String, val displayName: String)

enum class AiErrorKind { NOT_CONNECTED, UNAUTHORIZED, USAGE_LIMIT, USAGE_UNAVAILABLE, NO_MODEL, HTTP, NETWORK, SERVER, PROTOCOL }

data class AiError(val kind: AiErrorKind, val message: String)

class AiException(val error: AiError) : Exception(error.message)

sealed interface AiEvent {
    data class Delta(val text: String) : AiEvent
    data object Completed : AiEvent
    data class Incomplete(val reason: String) : AiEvent
    data class Failed(val error: AiError) : AiEvent
}

fun usageError(code: String?, fallback: String): AiError = when (code) {
    "subscription_sharing_usage_limit_exceeded" ->
        AiError(AiErrorKind.USAGE_LIMIT, "ChatGPT plan limit reached — resets on your plan's schedule")
    "subscription_sharing_usage_unavailable" ->
        AiError(AiErrorKind.USAGE_UNAVAILABLE, "ChatGPT usage is temporarily unavailable")
    else -> AiError(AiErrorKind.SERVER, fallback)
}
