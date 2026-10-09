package com.serendeep.marginalia.ai.agent

import com.serendeep.marginalia.ai.AiError
import com.serendeep.marginalia.ai.AiTask
import com.serendeep.marginalia.ai.CardDraft
import com.serendeep.marginalia.ai.ui.publishable
import com.serendeep.marginalia.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ChatEntry {
    /** Assistant markdown; grows paragraph by paragraph while streaming. */
    data class Text(val markdown: String) : ChatEntry
    data class Status(val label: String, val toolName: String) : ChatEntry
}

/** One user message and everything the assistant did in reply. */
data class ChatTurn(
    val id: Int,
    val user: String,
    val entries: List<ChatEntry> = emptyList(),
    val drafts: List<CardDraft> = emptyList(),
    val error: AiError? = null,
    val streaming: Boolean = true,
)

/** The conversation shared by the Ask screen and the notebook panel; it lives until [newChat] or the app closes. */
@Singleton
class ChatSession internal constructor(
    private val agent: Agent,
    private val scope: CoroutineScope,
    private val now: () -> Long,
) {
    @Inject
    constructor(agent: Agent, @AppScope scope: CoroutineScope) : this(agent, scope, System::currentTimeMillis)

    private val _turns = MutableStateFlow<List<ChatTurn>>(emptyList())
    val turns: StateFlow<List<ChatTurn>> = _turns.asStateFlow()

    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming.asStateFlow()

    private val lock = Any()
    private var history = emptyList<Item>()
    private var nextId = 0
    private var live: Int? = null
    private var job: Job? = null
    private val buffer = StringBuilder()
    private var published = 0
    private var lastPublishAt = 0L

    /** Ignored while a reply is still streaming or when [text] is blank. */
    fun send(text: String, context: AgentContext? = null, task: AiTask = AiTask.ASK) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val id: Int
        val base: List<Item>
        synchronized(lock) {
            if (live != null) return
            id = nextId++
            live = id
            base = history
            resetText()
            _turns.update { it + ChatTurn(id, clean) }
            _streaming.value = true
        }
        val user = Item.UserText(clean)
        job = scope.launch {
            agent.run(base + user, context, task).collect { event ->
                when (event) {
                    is AgentEvent.Text -> onText(id, event.delta)
                    AgentEvent.TextDone -> synchronized(lock) { if (live == id) flush(id) }
                    is AgentEvent.Status -> onStatus(id, event)
                    is AgentEvent.Drafts -> update(id) { it.copy(drafts = it.drafts + event.cards) }
                    is AgentEvent.Done -> finish(id, null) { history = base + user + event.newItems }
                    is AgentEvent.Failed -> finish(id, event.error)
                }
            }
            finish(id, null)
        }
    }

    /** Ends the running reply, keeping what was shown; the stopped exchange is not remembered by the model. */
    fun stop() {
        job?.cancel()
        job = null
        synchronized(lock) { live?.let { finishLocked(it, null) {} } }
    }

    fun newChat() {
        stop()
        synchronized(lock) {
            history = emptyList()
            _turns.value = emptyList()
        }
    }

    private fun onText(id: Int, delta: String): Unit = synchronized(lock) {
        if (live != id) return@synchronized
        // The gap the agent puts between rounds is a status line here, so a segment never opens with blank space.
        val piece = if (buffer.isEmpty()) delta.trimStart() else delta
        if (piece.isEmpty()) return@synchronized
        buffer.append(piece)
        val part = publishable(buffer.toString(), now() - lastPublishAt)
        if (part.length > published) publish(id, part)
    }

    private fun onStatus(id: Int, status: AgentEvent.Status): Unit = synchronized(lock) {
        if (live != id) return@synchronized
        flush(id)
        resetText()
        update(id) { it.copy(entries = it.entries + ChatEntry.Status(status.label, status.toolName)) }
    }

    private fun finish(id: Int, error: AiError?, remember: () -> Unit = {}): Unit = synchronized(lock) { finishLocked(id, error, remember) }

    private fun finishLocked(id: Int, error: AiError?, remember: () -> Unit) {
        if (live != id) return
        flush(id)
        remember()
        live = null
        _streaming.value = false
        update(id) { it.copy(streaming = false, error = error) }
    }

    private fun flush(id: Int) {
        if (buffer.length > published) publish(id, buffer.toString())
    }

    private fun publish(id: Int, markdown: String) {
        val append = published == 0
        published = markdown.length
        lastPublishAt = now()
        update(id) {
            val entry = ChatEntry.Text(markdown)
            it.copy(entries = if (append) it.entries + entry else it.entries.dropLast(1) + entry)
        }
    }

    private fun resetText() {
        buffer.setLength(0)
        published = 0
        lastPublishAt = now()
    }

    private fun update(id: Int, change: (ChatTurn) -> ChatTurn) =
        _turns.update { turns -> turns.map { if (it.id == id) change(it) else it } }
}
