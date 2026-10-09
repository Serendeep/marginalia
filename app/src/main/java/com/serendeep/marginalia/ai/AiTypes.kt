package com.serendeep.marginalia.ai

enum class AiTask { ASK, EXPLAIN, SUMMARIZE, CARDS, AUTO_SORT }

enum class Effort {
    MINIMAL, LOW, MEDIUM, HIGH;

    val wire: String get() = name.lowercase()

    companion object {
        fun parse(raw: String?): Effort? = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
}

/** A per-action override; null fields mean "use the default". */
data class TaskModel(val model: String? = null, val effort: Effort? = null)

class AiRequest(
    val instructions: String,
    val text: String,
    val imagePng: ByteArray? = null,
    val model: String? = null,
    val task: AiTask = AiTask.ASK,
    val effort: Effort? = null,
)

/** [efforts] is empty when the provider doesn't say which reasoning levels the model supports. */
data class ChatModel(val slug: String, val displayName: String, val efforts: List<Effort> = emptyList())

enum class AiErrorKind { NOT_CONNECTED, UNAUTHORIZED, USAGE_LIMIT, USAGE_UNAVAILABLE, NO_MODEL, HTTP, NETWORK, SERVER, PROTOCOL }

data class AiError(val kind: AiErrorKind, val message: String)

class AiException(val error: AiError) : Exception(error.message)

sealed interface AiEvent {
    data class Delta(val text: String) : AiEvent
    data object TextDone : AiEvent
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
