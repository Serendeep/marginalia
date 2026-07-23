package com.serendeep.marginalia.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import com.serendeep.marginalia.ui.components.GlassButton
import com.serendeep.marginalia.ui.components.GlassDialog
import com.serendeep.marginalia.ui.components.GlassTextButton
import com.serendeep.marginalia.ui.components.MarginLabel
import com.serendeep.marginalia.ui.theme.CoursePalette

@Composable
fun CourseEditorDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, colorIndex: Int, emoji: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var colorIndex by remember { mutableStateOf(0) }
    var emoji by remember { mutableStateOf<String?>(null) }
    var pickingEmoji by remember { mutableStateOf(false) }

    GlassDialog(onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            MarginLabel("New course")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Course name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CoursePalette.swatches.forEachIndexed { i, c ->
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(c)
                            .then(
                                if (i == colorIndex) Modifier.border(
                                    2.dp, MaterialTheme.colorScheme.onSurface, CircleShape
                                ) else Modifier
                            )
                            .clickable { colorIndex = i }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji ?: "Pick an emoji", Modifier.weight(1f))
                GlassTextButton(
                    text = if (emoji == null) "Choose" else "Change",
                    onClick = { pickingEmoji = !pickingEmoji },
                )
            }
            if (pickingEmoji) {
                AndroidView(
                    factory = { ctx ->
                        EmojiPickerView(ctx).apply {
                            setOnEmojiPickedListener {
                                emoji = it.emoji
                                pickingEmoji = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.End),
            ) {
                GlassTextButton(text = "Cancel", onClick = onDismiss)
                GlassButton(
                    text = "Create",
                    onClick = { onSave(name.trim(), colorIndex, emoji) },
                    enabled = name.isNotBlank(),
                )
            }
        }
    }
}
