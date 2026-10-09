package com.serendeep.marginalia.ai.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serendeep.marginalia.ai.AiSettings
import com.serendeep.marginalia.ai.AiTask
import com.serendeep.marginalia.ai.ChatGptAuth
import com.serendeep.marginalia.ai.Prompts
import com.serendeep.marginalia.ai.agent.AgentContext
import com.serendeep.marginalia.ai.agent.ChatEntry
import com.serendeep.marginalia.ai.agent.ChatSession
import com.serendeep.marginalia.ai.agent.ChatTurn
import com.serendeep.marginalia.ai.agent.ContextBuilder
import com.serendeep.marginalia.data.CardSource
import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.pdf.PdfDocumentSource
import com.serendeep.marginalia.update.RemoteConfigStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@Immutable
data class DraftCard(val id: Int, val front: String, val back: String, val keep: Boolean = true)

/** Where a notebook chat message comes from. [page] is zero-based; [pageText] is the live text of that page when known. */
class NotebookTarget(
    val lectureId: String,
    val documentId: String?,
    val page: Int,
    val pageText: suspend () -> String? = { null },
    val selectionPng: ByteArray? = null,
)

internal fun answerMessages(turns: List<ChatTurn>): List<AnswerMessage> = turns.flatMap { turn ->
    buildList {
        add(AnswerMessage("${turn.id}-u", AnswerRole.USER, turn.user))
        // A run of tool steps reads as one line: the current step while working, a count once the answer follows.
        var steps = 0
        turn.entries.forEachIndexed { index, entry ->
            when (entry) {
                is ChatEntry.Text -> {
                    if (steps > 0) add(AnswerMessage("${turn.id}-s$index", AnswerRole.STATUS, researched(steps)))
                    steps = 0
                    add(AnswerMessage("${turn.id}-$index", AnswerRole.ASSISTANT, entry.markdown))
                }
                is ChatEntry.Status -> {
                    steps++
                    val last = index == turn.entries.lastIndex
                    if (last && turn.streaming) add(AnswerMessage("${turn.id}-s$index", AnswerRole.STATUS, entry.label))
                    else if (last) add(AnswerMessage("${turn.id}-s$index", AnswerRole.STATUS, researched(steps)))
                }
            }
        }
        turn.error?.let { add(AnswerMessage("${turn.id}-e", AnswerRole.ASSISTANT, "**Couldn't finish:** ${it.message}")) }
    }
}

private fun researched(steps: Int) = if (steps == 1) "Researched in 1 step" else "Researched in $steps steps"

/** Exact title first, then ignoring case. */
internal fun resolveLecture(title: String, lectureByTitle: Map<String, String>): String? =
    lectureByTitle[title] ?: lectureByTitle.entries.firstOrNull { it.key.equals(title.trim(), ignoreCase = true) }?.value

internal fun suggestionPrompts(recentTitles: List<String>): List<String> = buildList {
    recentTitles.firstOrNull()?.let { add("Quiz me on $it") }
    if (recentTitles.size >= 2) add("Compare ${recentTitles[0]} and ${recentTitles[1]}")
    add("What did I highlight this week?")
    add("Summarize what I read today")
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val session: ChatSession,
    private val contexts: ContextBuilder,
    private val repository: MarginaliaRepository,
    settings: AiSettings,
    auth: ChatGptAuth,
    remote: RemoteConfigStore,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private class DraftTarget(val lectureId: String, val documentId: String?, val page: Int)

    val messages: StateFlow<List<AnswerMessage>> = session.turns.map(::answerMessages)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val streaming: StateFlow<Boolean> = session.streaming

    val ready: StateFlow<Boolean> = combine(settings.config, auth.status, remote.config) { c, s, r -> aiReady(c, s, r.aiAllowed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val needsSetup: StateFlow<Boolean> = session.turns.map { it.lastOrNull()?.error?.needsSetup() == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val lectures = repository.observeAllLectures()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val suggestions: StateFlow<List<String>> = lectures.map { list ->
        suggestionPrompts(list.sortedByDescending { it.lastOpenedAt ?: it.createdAt }.take(2).map { it.title })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), suggestionPrompts(emptyList()))

    private val _drafts = MutableStateFlow<List<DraftCard>>(emptyList())
    val drafts: StateFlow<List<DraftCard>> = _drafts.asStateFlow()

    private val _savedCount = MutableStateFlow(0)
    val savedCount: StateFlow<Int> = _savedCount.asStateFlow()

    private var target: DraftTarget? = null
    private var loadedKey: Pair<Int, Int>? = null

    init {
        viewModelScope.launch {
            session.turns.collect { turns ->
                val latest = turns.lastOrNull()
                val key = latest?.let { it.id to it.drafts.size }
                if (key == loadedKey) return@collect
                loadedKey = key
                _savedCount.value = 0
                _drafts.value = latest?.drafts.orEmpty().mapIndexed { i, d -> DraftCard(i, d.front, d.back) }
            }
        }
    }

    /** Sends [text] with the library as context, or with [notebook]'s page when given. */
    fun send(text: String, task: AiTask = AiTask.ASK, notebook: NotebookTarget? = null) {
        if (text.isBlank() || session.streaming.value) return
        target = notebook?.let { DraftTarget(it.lectureId, it.documentId, it.page) }
        viewModelScope.launch {
            val context = withContext(Dispatchers.IO) { runCatching { buildContext(notebook) }.getOrNull() }
            session.send(text, context, task)
        }
    }

    private suspend fun buildContext(notebook: NotebookTarget?): AgentContext =
        if (notebook == null) {
            contexts.library()
        } else {
            contexts.notebook(notebook.lectureId, notebook.page + 1, notebook.pageText(), notebook.selectionPng)
        }

    fun explainPage(nb: NotebookTarget) = send("Explain page ${nb.page + 1} of this document.", AiTask.EXPLAIN, nb)

    fun summarize(nb: NotebookTarget) = send("Summarize this document.", AiTask.SUMMARIZE, nb)

    fun makeCards(nb: NotebookTarget) = send("Make flashcards from page ${nb.page + 1}.", AiTask.CARDS, nb)

    fun explainSelection(nb: NotebookTarget) = send("Explain the selected region.", AiTask.ASK, nb)

    fun stop() = session.stop()

    fun newChat() {
        target = null
        session.newChat()
    }

    fun resolve(title: String): String? = resolveLecture(title, lectures.value.associate { it.title to it.id })

    fun title(lectureId: String): String? = lectures.value.firstOrNull { it.id == lectureId }?.title

    fun updateDraft(id: Int, front: String? = null, back: String? = null, keep: Boolean? = null) {
        _drafts.value = _drafts.value.map {
            if (it.id != id) it else it.copy(front = front ?: it.front, back = back ?: it.back, keep = keep ?: it.keep)
        }
    }

    fun saveDrafts() {
        val chosen = _drafts.value.filter { it.keep && it.front.isNotBlank() && it.back.isNotBlank() }
        if (chosen.isEmpty()) return
        val fixed = target
        val cited = session.turns.value.lastOrNull()?.let { turn ->
            val text = turn.entries.filterIsInstance<ChatEntry.Text>().joinToString("\n") { it.markdown }
            Prompts.parseCitations(text).firstNotNullOfOrNull { c -> resolve(c.title)?.let { it to c.page - 1 } }
        }
        _drafts.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            chosen.forEach {
                repository.createCard(
                    source = CardSource.AI,
                    lectureId = fixed?.lectureId ?: cited?.first,
                    documentId = fixed?.documentId,
                    page = fixed?.page ?: cited?.second,
                    frontText = it.front.trim(),
                    backText = it.back.trim(),
                )
            }
            _savedCount.value = chosen.size
        }
    }

    /** Renders [page] (zero-based) of the lecture's latest document; null when it has none or fails to open. */
    suspend fun renderPage(lectureId: String, page: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val document = repository.latestDocument(lectureId) ?: return@runCatching null
            val source = PdfDocumentSource.open(appContext, File(document.localPath))
            try {
                // Closing the source frees the page cache's bitmap, so keep a copy.
                source.renderFullPage(page.coerceIn(0, (document.pageCount - 1).coerceAtLeast(0)), widthPx)
                    .copy(Bitmap.Config.ARGB_8888, false)
            } finally {
                source.close()
            }
        }.getOrNull()
    }
}
