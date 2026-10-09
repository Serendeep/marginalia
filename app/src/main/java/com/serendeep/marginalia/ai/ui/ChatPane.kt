package com.serendeep.marginalia.ai.ui

import com.serendeep.marginalia.ui.components.glassBorder
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serendeep.marginalia.ui.components.GlassButton
import com.serendeep.marginalia.ui.components.GlassTextButton
import com.serendeep.marginalia.ui.components.WebPopup
import com.serendeep.marginalia.ui.components.glassTextFieldColors
import com.serendeep.marginalia.ui.theme.BodyFamily
import com.serendeep.marginalia.ui.theme.DisplayFamily
import com.serendeep.marginalia.ui.theme.MonoFamily
import com.serendeep.marginalia.ui.theme.Violet

/** The shared conversation: transcript, card drafts and composer. [onSend] decides what context a message carries. */
@Composable
fun ChatPane(
    onSend: (String) -> Unit,
    onCitation: (title: String, page: Int) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Ask about your library…",
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val streaming by viewModel.streaming.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val needsSetup by viewModel.needsSetup.collectAsStateWithLifecycle()
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    val saved by viewModel.savedCount.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    var settingsOpen by remember { mutableStateOf(false) }
    if (settingsOpen) AiSettingsSheet(onDismiss = { settingsOpen = false })
    ChatPaneContent(
        messages = messages,
        streaming = streaming,
        ready = ready,
        needsSetup = needsSetup,
        drafts = drafts,
        savedCount = saved,
        suggestions = suggestions,
        onSend = onSend,
        onStop = viewModel::stop,
        onNewChat = viewModel::newChat,
        onCitation = onCitation,
        onDraft = viewModel::updateDraft,
        onSaveDrafts = viewModel::saveDrafts,
        onSetup = { settingsOpen = true },
        modifier = modifier,
        placeholder = placeholder,
        modelChip = { ModelEffortChip() },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatPaneContent(
    messages: List<AnswerMessage>,
    streaming: Boolean,
    ready: Boolean,
    needsSetup: Boolean,
    drafts: List<DraftCard>,
    savedCount: Int,
    suggestions: List<String>,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onNewChat: () -> Unit,
    onCitation: (String, Int) -> Unit,
    onDraft: (id: Int, front: String?, back: String?, keep: Boolean?) -> Unit,
    onSaveDrafts: () -> Unit,
    onSetup: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Ask about your library…",
    modelChip: @Composable () -> Unit = {},
) {
    var text by remember { mutableStateOf("") }
    var popup by remember { mutableStateOf<String?>(null) }
    val send = {
        if (text.isNotBlank() && !streaming) {
            onSend(text.trim())
            text = ""
        }
    }
    Column(modifier.fillMaxSize()) {
        if (messages.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                GlassTextButton("New chat", onClick = onNewChat)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                EmptyChat(ready, suggestions, onSend, onSetup)
            } else {
                AnswerView(
                    messages = messages,
                    streaming = streaming,
                    onCitation = onCitation,
                    onLink = { popup = it },
                    modifier = Modifier.fillMaxSize(),
                )
                if (streaming && messages.last().role == AnswerRole.USER) {
                    ThinkingLabel(Modifier.align(Alignment.BottomStart).padding(bottom = 6.dp))
                }
            }
        }
        if (needsSetup) GlassButton("Open AI settings", onClick = onSetup, modifier = Modifier.padding(top = 8.dp))
        Drafts(drafts, savedCount, onDraft, onSaveDrafts)
        if (ready) {
            Composer(text, { text = it }, streaming, placeholder, send, onStop, modelChip)
        }
    }
    popup?.let { WebPopup(it) { popup = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyChat(ready: Boolean, suggestions: List<String>, onSend: (String) -> Unit, onSetup: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "ASK",
            fontFamily = MonoFamily,
            fontSize = 10.5.sp,
            letterSpacing = 1.26.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Ask across your notes and PDFs",
            fontFamily = DisplayFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            letterSpacing = (-0.4).sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            "Answers are grounded in your library and cite the pages they come from.",
            fontFamily = BodyFamily,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
        )
        if (!ready) {
            GlassButton("Connect ChatGPT", onClick = onSetup)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { AiChip(it, onClick = { onSend(it) }) }
            }
        }
    }
}

@Composable
private fun Composer(
    text: String,
    onText: (String) -> Unit,
    streaming: Boolean,
    placeholder: String,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modelChip: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val send = { if (text.isNotBlank()) onSend() }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, glassBorder(), shape),
        ) {
            TextField(
                value = text,
                onValueChange = onText,
                placeholder = { Text(placeholder, fontFamily = BodyFamily, fontSize = 14.sp) },
                textStyle = LocalTextStyle.current.copy(fontFamily = BodyFamily, fontSize = 14.sp, lineHeight = 21.sp),
                minLines = 2,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = Violet,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                modelChip()
                Spacer(Modifier.weight(1f))
                if (streaming) {
                    IconButton(onClick = onStop) { Icon(Icons.Filled.Stop, "Stop", tint = Violet) }
                } else {
                    IconButton(onClick = send, enabled = text.isNotBlank()) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            "Send",
                            tint = if (text.isNotBlank()) Violet else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Text(
            "Reads your library to answer and cites the pages it used. Requests aren't stored, and nothing is saved to your notes unless you approve it.",
            fontFamily = MonoFamily,
            fontSize = 10.sp,
            lineHeight = 15.sp,
            letterSpacing = 0.3.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Drafts(drafts: List<DraftCard>, savedCount: Int, onDraft: (Int, String?, String?, Boolean?) -> Unit, onSave: () -> Unit) {
    if (drafts.isEmpty()) {
        if (savedCount > 0) {
            Text("Saved $savedCount cards", fontFamily = MonoFamily, fontSize = 12.sp, color = Violet, modifier = Modifier.padding(top = 8.dp))
        }
        return
    }
    val count = drafts.count { it.keep && it.front.isNotBlank() && it.back.isNotBlank() }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${drafts.size} CARDS DRAFTED · UNTICK ANY YOU DON'T WANT",
                fontFamily = MonoFamily,
                fontSize = 10.5.sp,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            GlassButton("Save $count cards", enabled = count > 0, onClick = onSave)
        }
        Column(
            Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            drafts.forEach { d ->
                val shape = RoundedCornerShape(12.dp)
                Row(
                    Modifier.fillMaxWidth().clip(shape).border(1.dp, MaterialTheme.colorScheme.outline, shape).padding(8.dp),
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
        }
    }
}
