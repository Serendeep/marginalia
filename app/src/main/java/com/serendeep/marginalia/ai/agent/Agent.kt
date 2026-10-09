package com.serendeep.marginalia.ai.agent

import com.serendeep.marginalia.ai.AiError
import com.serendeep.marginalia.ai.AiErrorKind
import com.serendeep.marginalia.ai.AiException
import com.serendeep.marginalia.ai.AiProvider
import com.serendeep.marginalia.ai.AiRouter
import com.serendeep.marginalia.ai.AiTask
import com.serendeep.marginalia.ai.CardDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import javax.inject.Inject

sealed interface AgentEvent {
    data class Text(val delta: String) : AgentEvent
    data object TextDone : AgentEvent
    data class Status(val label: String, val toolName: String) : AgentEvent
    data class Drafts(val cards: List<CardDraft>) : AgentEvent

    /** [newItems] are the provider-level items this run produced, excluding the history it was given. */
    class Done(val newItems: List<Item>) : AgentEvent
    data class Failed(val error: AiError) : AgentEvent
}

/** Where the user is, rendered as a `<context>` block for the model, plus an optional selection image. */
class AgentContext(val text: String, val images: List<ByteArray> = emptyList())

/** Runs a conversation to a final answer, letting the model call library tools along the way. */
class Agent internal constructor(
    private val provider: () -> AiProvider,
    private val tools: ToolExecutor,
    private val toolTimeoutMs: Long,
    private val maxRounds: Int,
) {
    @Inject
    constructor(router: AiRouter, tools: AgentTools) : this(router::active, tools, TOOL_TIMEOUT_MS, MAX_ROUNDS)

    fun run(history: List<Item>, context: AgentContext?, task: AiTask = AiTask.ASK): Flow<AgentEvent> = flow {
        val ai = provider()
        val specs = tools.specs(ai.pageImages)
        val conversation = injectContext(history, context)
        val added = mutableListOf<Item>()
        var round = 0
        var separate = false
        while (true) {
            val finalTurn = round++ >= maxRounds
            val calls = mutableListOf<TurnEvent.ToolCall>()
            val pending = StringBuilder()
            val items = mutableListOf<Item>()
            var spoke = false
            var failure: AiError? = null
            fun flushText() {
                if (pending.isNotEmpty()) items += Item.AssistantText(pending.toString())
                pending.clear()
            }
            ai.turn(conversation + added, if (finalTurn) emptyList() else specs, AgentPrompts.INSTRUCTIONS, task).collect { event ->
                when (event) {
                    is TurnEvent.TextDelta -> {
                        if (separate) {
                            emit(AgentEvent.Text("\n\n"))
                            separate = false
                        }
                        spoke = true
                        pending.append(event.text)
                        emit(AgentEvent.Text(event.text))
                    }
                    TurnEvent.TextDone -> emit(AgentEvent.TextDone)
                    is TurnEvent.Reasoning -> {
                        flushText()
                        items += Item.Reasoning(event.raw)
                    }
                    is TurnEvent.ToolCall -> {
                        flushText()
                        calls += event
                        items += Item.ToolCall(event.callId, event.name, event.argsJson, event.providerItem)
                    }
                    TurnEvent.Completed -> Unit
                    is TurnEvent.Failed -> failure = event.error
                }
            }
            failure?.let {
                emit(AgentEvent.Failed(it))
                return@flow
            }
            flushText()
            added += items
            if (calls.isEmpty() || finalTurn) break
            val images = mutableListOf<ByteArray>()
            for (call in calls) {
                val args = parseArgs(call.argsJson)
                val label = if (args == null) call.name else runCatching { tools.label(call.name, args) }.getOrDefault(call.name)
                emit(AgentEvent.Status(label, call.name))
                val out = if (args == null) ToolOutput("Error: arguments were not valid JSON") else execute(call.name, args)
                added += Item.ToolResult(call.callId, cap(out.text))
                out.image?.let { images += it }
                if (out.drafts.isNotEmpty()) emit(AgentEvent.Drafts(out.drafts))
            }
            images.forEach { added += Item.UserText("Page image requested by the tool call above.", listOf(it)) }
            if (spoke) separate = true
        }
        emit(AgentEvent.Done(added))
    }.catch { e ->
        if (e is CancellationException) throw e
        emit(AgentEvent.Failed((e as? AiException)?.error ?: AiError(AiErrorKind.NETWORK, "Something went wrong — try again")))
    }

    private suspend fun execute(name: String, args: JSONObject): ToolOutput = try {
        withContext(Dispatchers.IO) { withTimeout(toolTimeoutMs) { tools.execute(name, args) } }
    } catch (e: TimeoutCancellationException) {
        ToolOutput("Error: the tool timed out")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ToolOutput("Error: ${e.message ?: "the tool failed"}")
    }

    private fun parseArgs(json: String): JSONObject? =
        try { JSONObject(json.ifBlank { "{}" }) } catch (_: Exception) { null }

    private fun injectContext(history: List<Item>, context: AgentContext?): List<Item> {
        val i = history.indexOfLast { it is Item.UserText }
        if (context == null || i < 0) return history
        val user = history[i] as Item.UserText
        return history.toMutableList().also { it[i] = Item.UserText(context.text + "\n\n" + user.text, context.images + user.images) }
    }

    companion object {
        const val MAX_ROUNDS = 8
        const val TOOL_TIMEOUT_MS = 20_000L
        const val MAX_OUTPUT_CHARS = 6_000

        fun cap(text: String) = if (text.length > MAX_OUTPUT_CHARS) text.take(MAX_OUTPUT_CHARS) + "…(truncated)" else text
    }
}
