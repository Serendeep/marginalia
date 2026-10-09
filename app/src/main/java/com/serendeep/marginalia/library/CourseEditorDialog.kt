package com.serendeep.marginalia.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import com.serendeep.marginalia.ui.components.GlassButton
import com.serendeep.marginalia.ui.components.CenterPanel
import com.serendeep.marginalia.ui.components.GlassTextButton
import com.serendeep.marginalia.ui.components.glassTextFieldColors
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

    CenterPanel(
        onDismiss = onDismiss,
        title = "New course",
        eyebrow = "Library",
        footer = {
            GlassTextButton(text = "Cancel", onClick = onDismiss)
            GlassButton(
                text = "Create",
                onClick = { onSave(name.trim(), colorIndex, emoji) },
                enabled = name.isNotBlank(),
            )
        },
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text("Course name") },
            singleLine = true,
            colors = glassTextFieldColors(),
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
    }
}
