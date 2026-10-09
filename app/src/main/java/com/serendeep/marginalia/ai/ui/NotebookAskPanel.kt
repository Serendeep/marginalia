package com.serendeep.marginalia.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.serendeep.marginalia.ai.AiTask
import com.serendeep.marginalia.pdf.PdfDocumentSource
import com.serendeep.marginalia.ui.components.PILL_ALPHA
import com.serendeep.marginalia.ui.components.SidePanel
import com.serendeep.marginalia.ui.components.glassBorder
import com.serendeep.marginalia.ui.theme.MonoFamily
import com.serendeep.marginalia.ui.theme.Violet

/** "Ask" pill beside the page indicator; [NotebookAskPanel] is what it opens. */
@Composable
fun NotebookAskPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Text(
        "✦ ASK",
        fontFamily = MonoFamily,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
        color = Violet,
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = PILL_ALPHA))
            .border(1.dp, glassBorder(), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/**
 * Chat beside the open document. [page] is the zero-based current page; [selectionPng] is a staged lasso crop.
 * A citation of this document jumps with [onJump], any other one opens through [onOpenAt]; both take zero-based pages.
 */
@Composable
fun NotebookAskPanel(
    lectureId: String,
    documentId: String?,
    title: String,
    page: Int,
    pageCount: Int,
    source: PdfDocumentSource?,
    selectionPng: ByteArray?,
    onJump: (page: Int) -> Unit,
    onOpenAt: (lectureId: String, page: Int) -> Unit,
    onDismiss: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val target = remember(lectureId, documentId, page, source, selectionPng) {
        NotebookTarget(
            lectureId, documentId, page,
            pageText = { source?.let { runCatching { it.pageText(page) }.getOrNull() }?.takeIf { it.isNotBlank() } },
            selectionPng = selectionPng,
        )
    }
    LaunchedEffect(lectureId) { viewModel.enter(lectureId) }
    SidePanel(onDismiss = onDismiss, title = title, eyebrow = "ASK", width = 460.dp, scrollable = false) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickActions(
                hasPdf = source != null && pageCount > 0,
                page = page,
                hasSelection = selectionPng != null,
                viewModel = viewModel,
                target = target,
            )
            ChatPane(
                onSend = { viewModel.send(it, AiTask.ASK, target) },
                onCitation = { cited, citedPage ->
                    when (val id = viewModel.resolve(cited)) {
                        null -> Unit
                        lectureId -> onJump(citedPage - 1)
                        else -> onOpenAt(id, citedPage - 1)
                    }
                },
                placeholder = "Ask about this document…",
                historyScope = lectureId,
                scopeToggle = true,
                empty = EmptyCopy(
                    "Ask about this paper",
                    "Knows the page you're on and your notes on it, and can read the rest of your library.",
                    listOf(
                        "What's the key idea on page ${page + 1}?",
                        "Walk me through the equations on page ${page + 1}",
                        "How does this connect to the rest of my library?",
                        "Quiz me on what I've read so far",
                    ),
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActions(hasPdf: Boolean, page: Int, hasSelection: Boolean, viewModel: ChatViewModel, target: NotebookTarget) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (hasSelection) AiChip("Explain selection", { viewModel.explainSelection(target) })
        if (hasPdf) {
            AiChip("Explain page ${page + 1}", { viewModel.explainPage(target) })
            AiChip("Summarize", { viewModel.summarize(target) })
            AiChip("Make cards", { viewModel.makeCards(target) })
        }
    }
}
