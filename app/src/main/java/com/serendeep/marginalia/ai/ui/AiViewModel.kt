package com.serendeep.marginalia.ai.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serendeep.marginalia.ai.AiError
import com.serendeep.marginalia.ai.AiErrorKind
import com.serendeep.marginalia.ai.AiException
import com.serendeep.marginalia.ai.AiRequest
import com.serendeep.marginalia.ai.AiRouter
import com.serendeep.marginalia.ai.AiSettings
import com.serendeep.marginalia.ai.ChatGptAuth
import com.serendeep.marginalia.ai.ProviderChoice
import com.serendeep.marginalia.ai.Prompts
import com.serendeep.marginalia.data.CardSource
import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.data.SNIPPET_CLOSE
import com.serendeep.marginalia.data.SNIPPET_OPEN
import com.serendeep.marginalia.pdf.PdfDocumentSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

enum class AiAction { EXPLAIN, SUMMARIZE, CARDS, ASK }

@Immutable
data class DraftCard(val id: Int, val front: String, val back: String, val keep: Boolean = true)

private const val IMAGE_WIDTH_PX = 1024
private const val SUMMARY_CHAR_CAP = 60_000
private const val ASK_PAGES = 6
private const val FALLBACK_PAGES = 3

@HiltViewModel
class AiViewModel @Inject constructor(
    router: AiRouter,
    private val settings: AiSettings,
    auth: ChatGptAuth,
    private val repository: MarginaliaRepository,
) : ViewModel() {

    private val runner = AiRunner(viewModelScope, router::active)

    val state: StateFlow<AiRunState> = runner.state
    val text: StateFlow<String> = runner.text

    val ready: StateFlow<Boolean> = combine(settings.config, auth.status, ::aiReady)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _action = MutableStateFlow<AiAction?>(null)
    val action: StateFlow<AiAction?> = _action.asStateFlow()

    private val _drafts = MutableStateFlow<List<DraftCard>>(emptyList())
    val drafts: StateFlow<List<DraftCard>> = _drafts.asStateFlow()

    private val _savedCount = MutableStateFlow(0)
    val savedCount: StateFlow<Int> = _savedCount.asStateFlow()

    init {
        viewModelScope.launch {
            runner.state.collect { s ->
                if (s == AiRunState.Done && _action.value == AiAction.CARDS && _drafts.value.isEmpty()) {
                    _drafts.value = Prompts.parseCards(runner.text.value).mapIndexed { i, c -> DraftCard(i, c.front, c.back) }
                }
            }
        }
    }

    fun reset() {
        runner.reset()
        _action.value = null
        _drafts.value = emptyList()
        _savedCount.value = 0
    }

    fun stop() = runner.stop()

    private fun begin(action: AiAction, build: suspend () -> AiRequest) {
        _action.value = action
        _drafts.value = emptyList()
        _savedCount.value = 0
        runner.start { withContext(Dispatchers.IO) { build() } }
    }

    fun explain(source: PdfDocumentSource, page: Int) = begin(AiAction.EXPLAIN) {
        val image = if (settings.config.value.provider == ProviderChoice.CHATGPT) pagePng(source, page) else null
        val text = source.pageText(page)
        if (text.isBlank() && image == null) throw AiException(noText())
        Prompts.explainPage(text, image)
    }

    fun summarize(source: PdfDocumentSource, pageCount: Int, title: String) = begin(AiAction.SUMMARIZE) {
        var total = 0
        val pages = List(pageCount) { i ->
            if (total >= SUMMARY_CHAR_CAP) "" else source.pageText(i).also { total += it.length }
        }
        if (total == 0) throw AiException(noText())
        Prompts.summarizeDocument(pages, title)
    }

    fun generateCards(source: PdfDocumentSource, page: Int) = begin(AiAction.CARDS) {
        val text = source.pageText(page)
        if (text.isBlank()) throw AiException(noText())
        Prompts.generateCards(text)
    }

    fun ask(question: String, lectureId: String, title: String, pageCount: Int) = begin(AiAction.ASK) {
        val q = question.trim()
        val ranked = rankPages(askTerms(q).map { term -> repository.search(term).pages.filter { it.lectureId == lectureId } }, ASK_PAGES)
        val hits = ranked.mapNotNull { hit ->
            val text = repository.indexedPageText(lectureId, hit.page) ?: stripMarkers(hit.snippet)
            text.takeIf { it.isNotBlank() }?.let { com.serendeep.marginalia.ai.PageHit(title, hit.page + 1, it) }
        }.ifEmpty {
            (0 until minOf(FALLBACK_PAGES, pageCount)).mapNotNull { i ->
                repository.indexedPageText(lectureId, i)?.takeIf { it.isNotBlank() }
                    ?.let { com.serendeep.marginalia.ai.PageHit(title, i + 1, it) }
            }
        }
        if (hits.isEmpty()) throw AiException(noText())
        Prompts.askLibrary(q, hits)
    }

    fun updateDraft(id: Int, front: String? = null, back: String? = null, keep: Boolean? = null) {
        _drafts.value = _drafts.value.map {
            if (it.id != id) it else it.copy(front = front ?: it.front, back = back ?: it.back, keep = keep ?: it.keep)
        }
    }

    fun saveCards(lectureId: String, documentId: String?, page: Int) {
        val chosen = _drafts.value.filter { it.keep && it.front.isNotBlank() && it.back.isNotBlank() }
        if (chosen.isEmpty()) return
        _drafts.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            chosen.forEach {
                repository.createCard(
                    source = CardSource.AI,
                    lectureId = lectureId,
                    documentId = documentId,
                    page = page,
                    frontText = it.front.trim(),
                    backText = it.back.trim(),
                )
            }
            _savedCount.value = chosen.size
        }
    }

    private suspend fun pagePng(source: PdfDocumentSource, page: Int): ByteArray = withContext(Dispatchers.IO) {
        // The bitmap belongs to the page cache, so it is only read here.
        val bitmap = source.renderFullPage(page, IMAGE_WIDTH_PX)
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun noText() = AiError(AiErrorKind.PROTOCOL, "This page has no readable text.")

    private fun stripMarkers(snippet: String) = snippet.replace(SNIPPET_OPEN, "").replace(SNIPPET_CLOSE, "").replace('\n', ' ')
}
