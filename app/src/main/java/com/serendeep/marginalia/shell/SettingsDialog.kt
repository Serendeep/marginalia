package com.serendeep.marginalia.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.serendeep.marginalia.BuildConfig
import com.serendeep.marginalia.handwriting.ModelState
import com.serendeep.marginalia.handwriting.modelStatus
import com.serendeep.marginalia.ink.PencilAction
import com.serendeep.marginalia.ui.components.GlassButton
import com.serendeep.marginalia.ui.components.PanelDivider
import com.serendeep.marginalia.ui.components.PanelSection
import com.serendeep.marginalia.ui.components.SidePanel
import com.serendeep.marginalia.ui.components.GlassTextButton
import com.serendeep.marginalia.ui.components.glassTextFieldColors
import com.serendeep.marginalia.update.UpdatesSectionContent

/** Daily goal and reminder; opened by long-pressing the sidebar goal card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    goalMin: Int,
    reminder: ReminderSettings,
    pencilAction: PencilAction,
    handwritingSearch: Boolean,
    modelState: ModelState,
    onDownloadModel: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (goalMin: Int, reminder: ReminderSettings, pencilAction: PencilAction, handwritingSearch: Boolean) -> Unit,
) {
    var searchInk by remember { mutableStateOf(handwritingSearch) }
    var action by remember { mutableStateOf(pencilAction) }
    var goalText by remember { mutableStateOf(goalMin.toString()) }
    var enabled by remember { mutableStateOf(reminder.enabled) }
    val time = rememberTimePickerState(reminder.minuteOfDay / 60, reminder.minuteOfDay % 60, is24Hour = true)
    val goal = goalText.toIntOrNull()?.takeIf { it in 5..600 }
    SidePanel(
        onDismiss = onDismiss,
        title = "Study settings",
        eyebrow = "Settings",
        footer = {
            GlassTextButton("Cancel", onClick = onDismiss)
            GlassButton("Save", enabled = goal != null, onClick = {
                onSave(goal ?: goalMin, ReminderSettings(enabled, time.hour * 60 + time.minute), action, searchInk)
            })
        },
    ) {
        PanelSection("Daily goal") {
            OutlinedTextField(
                value = goalText,
                onValueChange = { goalText = it.filter(Char::isDigit).take(3) },
                label = { Text("Daily goal (minutes)") },
                singleLine = true,
                isError = goal == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = glassTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Daily reminder", modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            if (enabled) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = time) }
            }
        }
        PanelDivider()
        PanelSection("Pencil") {
            Text("Double-tap", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PencilAction.entries.forEach { option ->
                    FilterChip(
                        selected = option == action,
                        onClick = { action = option },
                        label = { Text(option.label, maxLines = 1) },
                    )
                }
            }
        }
        PanelDivider()
        PanelSection("Handwriting") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Handwriting search", modifier = Modifier.weight(1f))
                Switch(checked = searchInk, onCheckedChange = { searchInk = it })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (modelState) {
                        ModelState.Ready -> "Handwriting model installed"
                        ModelState.NotDownloaded -> "Handwriting model not downloaded (~20 MB)"
                        else -> modelStatus(modelState).orEmpty()
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                if (modelState == ModelState.NotDownloaded) GlassTextButton("Download model", onClick = onDownloadModel)
            }
        }
        if (BuildConfig.UPDATES_ENABLED) {
            PanelDivider()
            PanelSection("Updates") { UpdatesSectionContent() }
        }
    }
}
