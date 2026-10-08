package com.serendeep.marginalia.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serendeep.marginalia.pdf.PdfDocumentSource
import com.serendeep.marginalia.ui.components.GlassButton
import com.serendeep.marginalia.ui.components.GlassTextButton
import com.serendeep.marginalia.ui.components.MarginLabel
import com.serendeep.marginalia.ui.components.glassBorder
import com.serendeep.marginalia.ui.components.glassTextFieldColors
import com.serendeep.marginalia.ui.theme.BodyFamily
import com.serendeep.marginalia.ui.theme.MonoFamily
import com.serendeep.marginalia.ui.theme.Violet
import kotlinx.coroutines.flow.StateFlow

/** "Ask" pill beside the page indicator; owns the AI sheet. [page] is the zero-based current page. */
@Composable
fun NotebookAskPill(
    lectureId: String,
    documentId: String?,
    title: String,
    page: Int,
    pageCount: Int,
    source: PdfDocumentSource?,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Text(
        "✦ ASK",
        fontFamily = MonoFamily,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
        color = Violet,
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .border(1.dp, glassBorder(), shape)
            .clickable { open = true }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
    if (open) NotebookAiSheet(lectureId, documentId, title, page, pageCount, source, onDismiss = { open = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotebookAiSheet(
    lectureId: String,
    documentId: String?,
    title: String,
    page: Int,
    pageCount: Int,
    source: PdfDocumentSource?,
    onDismiss: () -> Unit,
    viewModel: AiViewModel = hiltViewModel(),
) {
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    val saved by viewModel.savedCount.collectAsStateWithLifecycle()
    var settingsOpen by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        viewModel.reset()
        onDispose { viewModel.stop() }
    }
    if (settingsOpen) AiSettingsSheet(onDismiss = { settingsOpen = false })
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        NotebookAiContent(
            ready = ready,
            hasPdf = source != null && pageCount > 0,
            action = action,
            state = state,
            text = viewModel.text,
            drafts = drafts,
            savedCount = saved,
            pageLabel = "p.${page + 1}",
            onExplain = { source?.let { viewModel.explain(it, page) } },
            onSummarize = { source?.let { viewModel.summarize(it, pageCount, title) } },
            onCards = { source?.let { viewModel.generateCards(it, page) } },
            onAsk = { viewModel.ask(it, lectureId, title, pageCount) },
            onStop = viewModel::stop,
            onSetup = { settingsOpen = true },
            onDraft = viewModel::updateDraft,
            onSave = { viewModel.saveCards(lectureId, documentId, page) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotebookAiContent(
    ready: Boolean,
    hasPdf: Boolean,
    action: AiAction?,
    state: AiRunState,
    text: StateFlow<String>,
    drafts: List<DraftCard>,
    savedCount: Int,
    pageLabel: String,
    onExplain: () -> Unit,
    onSummarize: () -> Unit,
    onCards: () -> Unit,
    onAsk: (String) -> Unit,
    onStop: () -> Unit,
    onSetup: () -> Unit,
    onDraft: (id: Int, front: String?, back: String?, keep: Boolean?) -> Unit,
    onSave: () -> Unit,
) {
    var question by remember { mutableStateOf("") }
    val busy = state == AiRunState.Streaming
    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MarginLabel("Ask")
        if (!ready) {
            Text(
                "Connect ChatGPT to explain pages, summarize and make cards from this notebook.",
                fontFamily = BodyFamily,
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GlassButton("Connect ChatGPT", onClick = onSetup)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AiChip("Explain this page", onExplain, selected = action == AiAction.EXPLAIN, enabled = hasPdf && !busy)
                AiChip("Summarize document", onSummarize, selected = action == AiAction.SUMMARIZE, enabled = hasPdf && !busy)
                AiChip("Generate cards from $pageLabel", onCards, selected = action == AiAction.CARDS, enabled = hasPdf && !busy)
            }
            if (!hasPdf) {
                Text(
                    "Open a PDF in this notebook to use page actions.",
                    fontFamily = BodyFamily,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                placeholder = { Text("Ask about this document…") },
                singleLine = true,
                enabled = hasPdf && !busy,
                colors = glassTextFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (question.isNotBlank()) onAsk(question) }),
                trailingIcon = {
                    IconButton(onClick = { if (question.isNotBlank()) onAsk(question) }, enabled = hasPdf && !busy && question.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Ask", tint = Violet)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Output(action, state, text, drafts, savedCount, onStop, onSetup, onDraft, onSave)
        }
        MonoNote(PRIVACY_NOTE)
        Spacer(Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun Output(
    action: AiAction?,
    state: AiRunState,
    text: StateFlow<String>,
    drafts: List<DraftCard>,
    savedCount: Int,
    onStop: () -> Unit,
    onSetup: () -> Unit,
    onDraft: (Int, String?, String?, Boolean?) -> Unit,
    onSave: () -> Unit,
) {
    if (action == null) return
    val streaming = state == AiRunState.Streaming
    val cards = action == AiAction.CARDS
    if (streaming || (!cards && state != AiRunState.Idle)) {
        if (cards) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Violet)
                Text("Writing cards…", fontFamily = BodyFamily, fontSize = 13.5.sp)
            }
        } else {
            val scroll = rememberScrollState()
            StreamingText(
                text,
                streaming,
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .verticalScroll(scroll),
            )
        }
    }
    if (streaming) GlassTextButton("Stop", onClick = onStop)
    if (state is AiRunState.Failed) AiErrorBlock(state.error, onSetup)
    if (cards && state == AiRunState.Done) {
        if (drafts.isEmpty() && savedCount == 0) {
            Text("Couldn't find cards in the reply — try again.", fontFamily = BodyFamily, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DraftList(drafts, onDraft, onSave)
    }
    if (savedCount > 0) {
        Text("Saved $savedCount cards", fontFamily = MonoFamily, fontSize = 12.sp, color = Violet)
    }
}

@Composable
private fun DraftList(drafts: List<DraftCard>, onDraft: (Int, String?, String?, Boolean?) -> Unit, onSave: () -> Unit) {
    if (drafts.isEmpty()) return
    val count = drafts.count { it.keep && it.front.isNotBlank() && it.back.isNotBlank() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        drafts.forEach { d ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Checkbox(
                    checked = d.keep,
                    onCheckedChange = { onDraft(d.id, null, null, it) },
                    colors = CheckboxDefaults.colors(checkedColor = Violet),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = d.front,
                        onValueChange = { onDraft(d.id, it, null, null) },
                        label = { Text("Front") },
                        colors = glassTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = d.back,
                        onValueChange = { onDraft(d.id, null, it, null) },
                        label = { Text("Back") },
                        colors = glassTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GlassButton("Save $count cards", enabled = count > 0, onClick = onSave)
        }
    }
}
