package com.serendeep.marginalia.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.serendeep.marginalia.ui.components.glassBorder
import com.serendeep.marginalia.ui.theme.MonoFamily
import java.util.Locale

/** Current model with a dropdown of the active provider's models; picking one changes it for every surface. */
@Composable
fun ModelChip(modifier: Modifier = Modifier, viewModel: AiSettingsViewModel = hiltViewModel()) {
    val models by viewModel.models.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.loadModels() }
    val list = (models as? ModelsState.Loaded)?.models.orEmpty()
    val shown = list.firstOrNull { it.slug == selected }?.displayName ?: selected ?: "MODEL"
    val shape = RoundedCornerShape(10.dp)
    Box(modifier) {
        Text(
            "${shown.uppercase(Locale.ROOT)} ▾",
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
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            list.forEach { model ->
                DropdownMenuItem(text = { Text(model.displayName) }, onClick = {
                    open = false
                    viewModel.selectModel(model.slug)
                })
            }
        }
    }
}
