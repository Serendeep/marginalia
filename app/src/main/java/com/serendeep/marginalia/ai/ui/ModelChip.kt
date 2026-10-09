package com.serendeep.marginalia.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serendeep.marginalia.ai.AiTask
import com.serendeep.marginalia.ai.Effort
import com.serendeep.marginalia.ai.TaskModel
import com.serendeep.marginalia.ui.components.GlassDropdownMenu
import com.serendeep.marginalia.ui.components.GlassMenuItem
import com.serendeep.marginalia.ui.components.MenuDivider
import com.serendeep.marginalia.ui.components.MenuLabel
import com.serendeep.marginalia.ui.components.glassBorder
import com.serendeep.marginalia.ui.theme.MonoFamily
import java.util.Locale

fun Effort.label(): String = when (this) {
    Effort.MINIMAL -> "Minimal"
    Effort.LOW -> "Low"
    Effort.MEDIUM -> "Medium"
    Effort.HIGH -> "High"
    Effort.XHIGH -> "Extra high"
    Effort.MAX -> "Max"
    Effort.ULTRA -> "Ultra"
}

/** Model and reasoning effort for [task]; choices are saved as that action's override. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelEffortChip(
    modifier: Modifier = Modifier,
    task: AiTask = AiTask.ASK,
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
    val models by viewModel.models.collectAsStateWithLifecycle()
    val overrides by viewModel.taskModels.collectAsStateWithLifecycle()
    val global by viewModel.selected.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.loadModels() }
    val list = (models as? ModelsState.Loaded)?.models.orEmpty()
    val current = overrides[task] ?: TaskModel()
    val defaultSlug = remember(list, global, task) { viewModel.defaultFor(task, list) }
    val defaultName = list.firstOrNull { it.slug == defaultSlug }?.displayName ?: defaultSlug
    val active = list.firstOrNull { it.slug == (current.model ?: defaultSlug) }
    val efforts = active?.efforts.orEmpty().ifEmpty { Effort.entries }
    val modelName = active?.displayName ?: current.model ?: defaultName ?: "Model"
    val shape = RoundedCornerShape(10.dp)
    Box(modifier) {
        Text(
            "${modelName.uppercase(Locale.ROOT)} · ${(current.effort?.label() ?: "Auto").uppercase(Locale.ROOT)} ▾",
            fontFamily = MonoFamily,
            fontSize = 10.5.sp,
            letterSpacing = 0.6.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, glassBorder(), shape)
                .clickable(enabled = list.isNotEmpty()) { open = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        // Stays open after a pick so model and effort can be chosen in one go.
        GlassDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuLabel("Model")
            GlassMenuItem(
                label = "Default" + (defaultName?.let { " · $it" } ?: ""),
                supporting = "Follows your default in AI settings",
                selected = current.model == null,
                onClick = { viewModel.setTaskModel(task, current.copy(model = null)) },
            )
            list.forEach { model ->
                GlassMenuItem(
                    label = model.displayName,
                    supporting = model.description.ifEmpty { null },
                    selected = current.model == model.slug,
                    onClick = {
                        val keep = current.effort?.takeIf { model.efforts.isEmpty() || it in model.efforts }
                        viewModel.setTaskModel(task, TaskModel(model.slug, keep))
                    },
                )
            }
            MenuDivider()
            MenuLabel("Effort")
            FlowRow(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AiChip("Auto", onClick = { viewModel.setTaskModel(task, current.copy(effort = null)) }, selected = current.effort == null)
                efforts.forEach { effort ->
                    AiChip(effort.label(), onClick = { viewModel.setTaskModel(task, current.copy(effort = effort)) }, selected = current.effort == effort)
                }
            }
        }
    }
}
