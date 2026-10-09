package com.serendeep.marginalia.ai.agent

import com.serendeep.marginalia.ai.AiError
import org.json.JSONObject

/** One entry of a provider-level conversation. */
sealed interface Item {
    class UserText(val text: String, val images: List<ByteArray> = emptyList()) : Item
    data class AssistantText(val text: String) : Item

    /** [providerItem] is the item exactly as the provider sent it, replayed verbatim when present. */
    data class ToolCall(val callId: String, val name: String, val argsJson: String, val providerItem: JSONObject? = null) : Item
    data class ToolResult(val callId: String, val output: String) : Item

    /** Opaque reasoning state; only the Responses API produces it and it must be sent back unchanged. */
    class Reasoning(val raw: JSONObject) : Item
}

class ToolSpec(val name: String, val description: String, val parametersJsonSchema: JSONObject)

sealed interface TurnEvent {
    data class TextDelta(val text: String) : TurnEvent
    data object TextDone : TurnEvent
    class ToolCall(val callId: String, val name: String, val argsJson: String, val providerItem: JSONObject? = null) : TurnEvent
    class Reasoning(val raw: JSONObject) : TurnEvent
    data object Completed : TurnEvent
    data class Failed(val error: AiError) : TurnEvent
}
