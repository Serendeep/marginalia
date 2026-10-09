package com.serendeep.marginalia.ai.agent

import com.serendeep.marginalia.ai.AiError
import com.serendeep.marginalia.ai.AiTask
import com.serendeep.marginalia.ai.CardDraft
import com.serendeep.marginalia.ai.ui.publishable
import com.serendeep.marginalia.di.AppScope
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.UUID
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

/**
 * The conversation shared by the Ask screen and the notebook panel. Each finished exchange is saved to [store];
 * [enter] and [open] bring a saved chat back, and [newChat] starts a fresh one.
 */
@Singleton
class ChatSession internal constructor(
    private val agent: Agent,
    private val store: ChatStore,
    private val scope: CoroutineScope,
    private val now: () -> Long,
) {
    @Inject
    constructor(agent: Agent, store: ChatStore, @AppScope scope: CoroutineScope) : this(agent, store, scope, System::currentTimeMillis)

    private val _turns = MutableStateFlow<List<ChatTurn>>(emptyList())
    val turns: StateFlow<List<ChatTurn>> = _turns.asStateFlow()

    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming.asStateFlow()

    private val _currentChatId = MutableStateFlow<String?>(null)
    val currentChatId: StateFlow<String?> = _currentChatId.asStateFlow()

    private val lock = Any()
    private var chatId: String? = null
    private var chatScope: String? = null
    private var title = ""
    private var createdAt = 0L
    private val saves = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var history = emptyList<Item>()
    private var nextId = 0
    private var live: Int? = null
    private var job: Job? = null
    private val buffer = StringBuilder()
    private var published = 0
    private var lastPublishAt = 0L

    init {
        // One writer keeps saves, renames and deletes in the order they were asked for.
        scope.launch {
            for (work in saves) {
                try {
                    work()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("ChatSession", "store failed: ${e.javaClass.simpleName}")
                }
            }
        }
    }

    /** Ignored while a reply is still streaming or when [text] is blank. A chat held in another [chatScope] is left and a new one started. */
    fun send(text: String, context: AgentContext? = null, task: AiTask = AiTask.ASK, chatScope: String = LIBRARY_SCOPE) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val id: Int
        val base: List<Item>
        synchronized(lock) {
            if (live != null) return
            if (this.chatScope != chatScope) reset(chatScope)
            if (chatId == null) {
                chatId = UUID.randomUUID().toString()
                createdAt = now()
                title = chatTitle(clean)
                _currentChatId.value = chatId
            }
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
                    is AgentEvent.Done -> finish(id, null, listOf(user) + event.newItems)
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
        synchronized(lock) { live?.let { finishLocked(it, null, null) } }
    }

    /** Starts an empty chat, in [chatScope] when given. */
    fun newChat(chatScope: String? = null) {
        stop()
        synchronized(lock) { reset(chatScope ?: this.chatScope) }
    }

    /** Switches to [chatScope], resuming its most recent chat; does nothing when that scope is already open. */
    suspend fun enter(chatScope: String) {
        if (synchronized(lock) { this.chatScope == chatScope }) return
        val info = store.latest(chatScope)
        val turns = info?.let { decode(it.id) }.orEmpty()
        synchronized(lock) {
            if (this.chatScope == chatScope) return
            stop()
            if (info == null) reset(chatScope) else restore(info, turns)
        }
    }

    /** Brings back a saved chat so it can be continued. */
    fun open(chatId: String) {
        stop()
        scope.launch {
            val info = store.get(chatId) ?: return@launch
            val turns = decode(chatId)
            synchronized(lock) { if (live == null) restore(info, turns) }
        }
    }

    fun rename(chatId: String, title: String) {
        val clean = title.trim().ifEmpty { return }
        synchronized(lock) { if (chatId == this.chatId) this.title = clean }
        saves.trySend { store.rename(chatId, clean) }
    }

    fun delete(chatId: String) {
        if (chatId == this.chatId) newChat()
        saves.trySend { store.delete(chatId) }
    }

    fun chats(chatScope: String?): Flow<List<ChatInfo>> = store.observe(chatScope)

    /** Completes once every save asked for so far has been written. */
    internal suspend fun settled() {
        val done = CompletableDeferred<Unit>()
        saves.trySend { done.complete(Unit) }
        done.await()
    }

    private suspend fun decode(chatId: String) = store.turns(chatId).mapNotNull { row ->
        try {
            ChatCodec.turn(row.idx, row.json)
        } catch (e: Exception) {
            null
        }
    }

    private fun reset(chatScope: String?) {
        this.chatScope = chatScope
        chatId = null
        title = ""
        history = emptyList()
        _turns.value = emptyList()
        _currentChatId.value = null
        resetText()
    }

    private fun restore(info: ChatInfo, turns: List<Pair<ChatTurn, List<Item>>>) {
        chatScope = info.scope
        chatId = info.id
        title = info.title
        createdAt = info.createdAt
        history = turns.flatMap { it.second }
        nextId = maxOf(nextId, (turns.maxOfOrNull { it.first.id } ?: -1) + 1)
        _turns.value = turns.map { it.first }
        _currentChatId.value = info.id
        resetText()
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

    private fun finish(id: Int, error: AiError?, items: List<Item>? = null): Unit = synchronized(lock) { finishLocked(id, error, items) }

    /** [items] are what the model gets to remember of this exchange; null for one that failed or was stopped. */
    private fun finishLocked(id: Int, error: AiError?, items: List<Item>?) {
        if (live != id) return
        flush(id)
        if (items != null) history = history + items
        live = null
        update(id) { it.copy(streaming = false, error = error) }
        _streaming.value = false
        val chat = ChatInfo(chatId ?: return, title, chatScope ?: return, createdAt, now())
        val json = ChatCodec.turn(_turns.value.first { it.id == id }, items.orEmpty())
        saves.trySend { store.save(chat, id, json) }
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
