package com.serendeep.marginalia.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.serendeep.marginalia.ai.AiRouter
import com.serendeep.marginalia.ai.AiSettings
import com.serendeep.marginalia.ai.ChatGptAuth
import com.serendeep.marginalia.ai.Citation
import com.serendeep.marginalia.ai.PageHit
import com.serendeep.marginalia.ai.Prompts
import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.data.SNIPPET_CLOSE
import com.serendeep.marginalia.data.SNIPPET_OPEN
import com.serendeep.marginalia.ui.components.GlassButton
import com.serendeep.marginalia.ui.components.GlassTextButton
import com.serendeep.marginalia.ui.components.glassTextFieldColors
import com.serendeep.marginalia.ui.theme.BodyFamily
import com.serendeep.marginalia.ui.theme.MonoFamily
import com.serendeep.marginalia.ui.theme.Violet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

private const val TOP_PAGES = 6
private const val TOP_HIGHLIGHTS = 2
private const val NO_MATCH = "Nothing in your library matches that. Try different words."

data class CitationChip(val title: String, val page: Int, val lectureId: String?) {
    val label: String get() = "$title · p.$page"
}

@HiltViewModel
class AskLibraryViewModel @Inject constructor(
    router: AiRouter,
    settings: AiSettings,
    auth: ChatGptAuth,
    private val repository: MarginaliaRepository,
) : ViewModel() {

    private val runner = AiRunner(viewModelScope, router::active)

    val state: StateFlow<AiRunState> = runner.state
    val text: StateFlow<String> = runner.text

    val ready: StateFlow<Boolean> = combine(settings.config, auth.status, ::aiReady)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _citations = MutableStateFlow<List<CitationChip>>(emptyList())
    val citations: StateFlow<List<CitationChip>> = _citations.asStateFlow()

    private var lectureByTitle: Map<String, String> = emptyMap()

    init {
        viewModelScope.launch {
            runner.state.collect { s ->
                _citations.value = if (s == AiRunState.Done) {
                    Prompts.parseCitations(runner.text.value).map { c -> CitationChip(c.title, c.page, resolve(c)) }
                } else {
                    emptyList()
                }
            }
        }
    }

    fun ask(question: String) {
        val q = question.trim()
        if (q.isEmpty()) return
        runner.start(NO_MATCH) {
            val terms = askTerms(q)
            val results = terms.map { repository.search(it) }
            val pages = rankPages(results.map { it.pages }, TOP_PAGES)
            val highlights = results.flatMap { it.highlights }.distinctBy { it.highlight.id }.take(TOP_HIGHLIGHTS)
            val titles = HashMap<String, String>()
            val hits = ArrayList<PageHit>()
            pages.forEach { p ->
                val text = repository.indexedPageText(p.lectureId, p.page) ?: strip(p.snippet)
                if (text.isNotBlank()) {
                    hits += PageHit(p.title, p.page + 1, text)
                    titles[p.title] = p.lectureId
                }
            }
            highlights.forEach { h ->
                hits += PageHit(h.lectureTitle, h.highlight.page + 1, h.highlight.text)
                titles[h.lectureTitle] = h.highlight.lectureId
            }
            lectureByTitle = titles
            if (hits.isEmpty()) null else Prompts.askLibrary(q, hits)
        }
    }

    fun dismiss() = runner.reset()

    fun stop() = runner.stop()

    private fun resolve(c: Citation): String? =
        lectureByTitle[c.title] ?: lectureByTitle.entries.firstOrNull { it.key.equals(c.title, ignoreCase = true) }?.value

    private fun strip(snippet: String) = snippet.replace(SNIPPET_OPEN, "").replace(SNIPPET_CLOSE, "").replace('\n', ' ')
}

/** Right-column card on Today. Opens a cited page through [onOpenAt] (zero-based page). */
@Composable
fun AskChatGptCard(
    onOpenAt: (lectureId: String, page: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AskLibraryViewModel = hiltViewModel(),
) {
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val citations by viewModel.citations.collectAsStateWithLifecycle()
    var settingsOpen by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { viewModel.dismiss() }
    }
    if (settingsOpen) AiSettingsSheet(onDismiss = { settingsOpen = false })
    AskChatGptCardContent(
        ready = ready,
        state = state,
        text = viewModel.text,
        citations = citations,
        onAsk = viewModel::ask,
        onStop = viewModel::stop,
        onClose = viewModel::dismiss,
        onOpenAt = onOpenAt,
        onSetup = { settingsOpen = true },
        modifier = modifier,
        modelChip = { ModelChip() },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskChatGptCardContent(
    ready: Boolean,
    state: AiRunState,
    text: StateFlow<String>,
    citations: List<CitationChip>,
    onAsk: (String) -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit,
    onOpenAt: (String, Int) -> Unit,
    onSetup: () -> Unit,
    modifier: Modifier = Modifier,
    modelChip: @Composable () -> Unit = {},
) {
    var question by remember { mutableStateOf("") }
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "ASK CHATGPT",
                fontFamily = MonoFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 10.5.sp,
                letterSpacing = 1.26.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ready) modelChip()
        }
        Text(
            "Ask a question across your notes and PDFs. Answers cite the pages. Uses your ChatGPT plan.",
            fontFamily = BodyFamily,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!ready) {
            GlassButton("Connect ChatGPT", onClick = onSetup)
        } else {
            val busy = state == AiRunState.Streaming
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                placeholder = { Text("Ask about your library…", fontSize = 13.sp) },
                singleLine = true,
                enabled = !busy,
                colors = glassTextFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (question.isNotBlank()) onAsk(question) }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state != AiRunState.Idle) {
                if (state !is AiRunState.Failed) {
                    StreamingText(
                        text,
                        busy,
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
                if (state is AiRunState.Failed) AiErrorBlock(state.error, onSetup)
                if (citations.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        citations.forEach { c -> CitationPill(c, onOpenAt) }
                    }
                }
                Row {
                    if (busy) GlassTextButton("Stop", onClick = onStop) else GlassTextButton("Clear", onClick = onClose)
                }
            }
        }
    }
}

@Composable
private fun CitationPill(c: CitationChip, onOpenAt: (String, Int) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Text(
        c.label.uppercase(Locale.ROOT),
        fontFamily = MonoFamily,
        fontSize = 10.5.sp,
        letterSpacing = 0.5.sp,
        color = Violet,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(Violet.copy(alpha = 0.13f))
            .clickable(enabled = c.lectureId != null) { c.lectureId?.let { onOpenAt(it, c.page - 1) } }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
