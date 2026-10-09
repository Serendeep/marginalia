package com.serendeep.marginalia.ai.agent

import com.serendeep.marginalia.ai.AiError
import com.serendeep.marginalia.ai.AiErrorKind
import com.serendeep.marginalia.ai.CardDraft
import org.json.JSONArray
import org.json.JSONObject

/** JSON form of a finished exchange: the turn as displayed plus the provider items it added. Pasted images are not kept. */
object ChatCodec {
    fun item(item: Item): JSONObject = when (item) {
        is Item.UserText -> JSONObject().put("type", "user").put("text", item.text)
        is Item.AssistantText -> JSONObject().put("type", "assistant").put("text", item.text)
        is Item.ToolCall -> JSONObject().put("type", "call").put("callId", item.callId).put("name", item.name)
            .put("args", item.argsJson).put("provider", item.providerItem)
        is Item.ToolResult -> JSONObject().put("type", "result").put("callId", item.callId).put("output", item.output)
        is Item.Reasoning -> JSONObject().put("type", "reasoning").put("raw", item.raw)
    }

    fun item(json: JSONObject): Item = when (json.getString("type")) {
        "user" -> Item.UserText(json.getString("text"))
        "assistant" -> Item.AssistantText(json.getString("text"))
        "call" -> Item.ToolCall(json.getString("callId"), json.getString("name"), json.getString("args"), json.optJSONObject("provider"))
        "result" -> Item.ToolResult(json.getString("callId"), json.getString("output"))
        "reasoning" -> Item.Reasoning(json.getJSONObject("raw"))
        else -> throw IllegalArgumentException("unknown item")
    }

    fun turn(turn: ChatTurn, items: List<Item>): String {
        val entries = JSONArray()
        turn.entries.forEach {
            entries.put(
                when (it) {
                    is ChatEntry.Text -> JSONObject().put("type", "text").put("markdown", it.markdown)
                    is ChatEntry.Status -> JSONObject().put("type", "status").put("label", it.label).put("tool", it.toolName)
                },
            )
        }
        val drafts = JSONArray()
        turn.drafts.forEach { drafts.put(JSONObject().put("front", it.front).put("back", it.back)) }
        val json = JSONObject().put("user", turn.user).put("entries", entries).put("drafts", drafts)
        turn.error?.let { json.put("error", JSONObject().put("kind", it.kind.name).put("message", it.message)) }
        json.put("items", JSONArray().also { array -> items.forEach { array.put(item(it)) } })
        return json.toString()
    }

    /** The turn with [id], no longer streaming, and the items it contributed to the model's history. */
    fun turn(id: Int, raw: String): Pair<ChatTurn, List<Item>> {
        val json = JSONObject(raw)
        val entries = json.getJSONArray("entries").objects().map {
            if (it.getString("type") == "text") ChatEntry.Text(it.getString("markdown")) else ChatEntry.Status(it.getString("label"), it.getString("tool"))
        }
        val drafts = json.optJSONArray("drafts")?.objects().orEmpty().map { CardDraft(it.getString("front"), it.getString("back")) }
        val error = json.optJSONObject("error")?.let {
            AiError(runCatching { AiErrorKind.valueOf(it.getString("kind")) }.getOrDefault(AiErrorKind.SERVER), it.getString("message"))
        }
        val turn = ChatTurn(id, json.getString("user"), entries, drafts, error, streaming = false)
        return turn to json.getJSONArray("items").objects().map(::item)
    }

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
}
