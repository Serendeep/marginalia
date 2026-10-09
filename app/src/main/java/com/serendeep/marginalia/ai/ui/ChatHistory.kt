package com.serendeep.marginalia.ai.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serendeep.marginalia.ai.agent.ChatInfo
import com.serendeep.marginalia.library.relativeTime
import com.serendeep.marginalia.ui.components.GlassDropdownMenu
import com.serendeep.marginalia.ui.components.GlassMenuItem
import com.serendeep.marginalia.ui.components.GlassTextButton
import com.serendeep.marginalia.ui.components.SidePanel
import com.serendeep.marginalia.ui.components.glassBorder
import com.serendeep.marginalia.ui.components.glassTextFieldColors
import com.serendeep.marginalia.ui.theme.BodyFamily
import com.serendeep.marginalia.ui.theme.MonoFamily
import com.serendeep.marginalia.ui.theme.Violet

/** History of the chats held in [scope]; with [scopeToggle] it can widen to every chat. */
@Composable
fun ChatHistory(viewModel: ChatViewModel, scope: String, scopeToggle: Boolean, onDismiss: () -> Unit) {
    var all by remember { mutableStateOf(false) }
    val chats by remember(all) { viewModel.chats(if (all) null else scope) }.collectAsStateWithLifecycle(emptyList())
    val current by viewModel.currentChatId.collectAsStateWithLifecycle()
    ChatHistoryPanel(
        chats = chats,
        currentId = current,
        scopeToggle = scopeToggle,
        all = all,
        onAll = { all = it },
        onOpen = { viewModel.openChat(it); onDismiss() },
        onRename = viewModel::renameChat,
        onDelete = viewModel::deleteChat,
        onDismiss = onDismiss,
    )
}

@Composable
fun ChatHistoryPanel(
    chats: List<ChatInfo>,
    currentId: String?,
    scopeToggle: Boolean,
    all: Boolean,
    onAll: (Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    SidePanel(onDismiss = onDismiss, title = "Chats", eyebrow = "History", width = 420.dp) {
        if (scopeToggle) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiChip("This document", { onAll(false) }, selected = !all)
                AiChip("All", { onAll(true) }, selected = all)
            }
        }
        if (chats.isEmpty()) {
            Text(
                "No chats yet. Conversations you have show up here.",
                fontFamily = BodyFamily,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        chats.forEach { chat ->
            androidx.compose.runtime.key(chat.id) {
                ChatRow(chat, chat.id == currentId, onOpen = { onOpen(chat.id) }, onRename = { onRename(chat.id, it) }, onDelete = { onDelete(chat.id) })
            }
        }
    }
}

private enum class RowMode { Idle, Renaming, Confirming }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(chat: ChatInfo, current: Boolean, onOpen: () -> Unit, onRename: (String) -> Unit, onDelete: () -> Unit) {
    var mode by remember { mutableStateOf(RowMode.Idle) }
    var menu by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(chat.title) }
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).border(1.dp, if (current) Violet else glassBorder(), shape).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (mode) {
            RowMode.Renaming -> {
                val save = { draft.trim().takeIf { it.isNotEmpty() }?.let(onRename); mode = RowMode.Idle }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    colors = glassTextFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    GlassTextButton("Cancel", onClick = { mode = RowMode.Idle })
                    GlassTextButton("Save", onClick = { save() }, enabled = draft.isNotBlank())
                }
            }
            RowMode.Confirming -> {
                Text("Delete this chat?", fontFamily = BodyFamily, fontSize = 14.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    GlassTextButton("Cancel", onClick = { mode = RowMode.Idle })
                    GlassTextButton("Delete", onClick = onDelete)
                }
            }
            RowMode.Idle -> Row(
                Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        chat.title,
                        fontFamily = BodyFamily,
                        fontSize = 14.sp,
                        color = if (current) Violet else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        relativeTime(chat.updatedAt),
                        fontFamily = MonoFamily,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Chat options", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    GlassDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        GlassMenuItem("Rename", onClick = { menu = false; draft = chat.title; mode = RowMode.Renaming })
                        GlassMenuItem("Delete", onClick = { menu = false; mode = RowMode.Confirming }, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
