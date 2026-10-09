package com.serendeep.marginalia.ai.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serendeep.marginalia.ui.components.GlassButton
import com.serendeep.marginalia.ui.theme.BodyFamily
import com.serendeep.marginalia.ui.theme.MonoFamily

/** Right-column card on Today. Sending a question opens the Ask screen with it. */
@Composable
fun AskChatGptCard(
    onAsk: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    var settingsOpen by remember { mutableStateOf(false) }
    if (settingsOpen) AiSettingsSheet(onDismiss = { settingsOpen = false })
    AskChatGptCardContent(
        ready = ready,
        onAsk = onAsk,
        onSetup = { settingsOpen = true },
        modifier = modifier,
        modelChip = { ModelEffortChip() },
    )
}

@Composable
fun AskChatGptCardContent(
    ready: Boolean,
    onAsk: (String) -> Unit,
    onSetup: () -> Unit,
    modifier: Modifier = Modifier,
    modelChip: @Composable () -> Unit = {},
) {
    var question by remember { mutableStateOf("") }
    val send = {
        if (question.isNotBlank()) {
            onAsk(question.trim())
            question = ""
        }
    }
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "ASK CHATGPT",
            fontFamily = MonoFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 10.5.sp,
            letterSpacing = 1.26.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            ComposerBox(
                text = question,
                onText = { question = it },
                placeholder = "Ask about your library…",
                streaming = false,
                onSend = send,
                onStop = {},
                modelChip = modelChip,
                minLines = 1,
            )
        }
    }
}
