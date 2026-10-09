package com.serendeep.marginalia.ai.ui

import androidx.compose.ui.text.TextStyle
import com.serendeep.marginalia.ui.components.PrivateText
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serendeep.marginalia.ai.AiConfig
import com.serendeep.marginalia.ai.AiTask
import com.serendeep.marginalia.ai.Effort
import com.serendeep.marginalia.ai.TaskModel
import com.serendeep.marginalia.ai.ChatGptStatus
import com.serendeep.marginalia.ai.ProviderChoice
import com.serendeep.marginalia.ui.components.GlassButton
import com.serendeep.marginalia.ui.components.GlassDropdownMenu
import com.serendeep.marginalia.ui.components.GlassMenuItem
import com.serendeep.marginalia.ui.components.GlassTextButton
import com.serendeep.marginalia.ui.components.PanelDivider
import com.serendeep.marginalia.ui.components.PanelSection
import com.serendeep.marginalia.ui.components.SidePanel
import com.serendeep.marginalia.ui.components.glassTextFieldColors
import com.serendeep.marginalia.ui.theme.BodyFamily
import com.serendeep.marginalia.ui.theme.DimInkDark
import com.serendeep.marginalia.ui.theme.MonoFamily
import com.serendeep.marginalia.ui.theme.Violet

const val CUSTOM_URL_PLACEHOLDER = "https://cursedpc.tailbd2879.ts.net/v1"
const val PRIVACY_NOTE = "Nothing is sent unless you tap an AI action. Requests don't store your data (store:false)."

@Composable
fun AiSettingsSheet(onDismiss: () -> Unit, viewModel: AiSettingsViewModel = hiltViewModel()) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val autoSort by viewModel.autoSort.collectAsStateWithLifecycle()
    val taskModels by viewModel.taskModels.collectAsStateWithLifecycle()
    val selectedForDefault by viewModel.selected.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val connected = status is ChatGptStatus.Connected && config.provider == ProviderChoice.CHATGPT
    LaunchedEffect(connected) { if (connected) viewModel.loadModels() }
    DisposableEffect(Unit) {
        onDispose { viewModel.cancelSignIn() }
    }
    val selected = when (config.provider) {
        ProviderChoice.CHATGPT -> (status as? ChatGptStatus.Connected)?.model
        ProviderChoice.COMPATIBLE -> config.model.ifEmpty { null }
    }
    SidePanel(onDismiss = onDismiss, title = "AI", eyebrow = "Settings") {
        AiSettingsContent(
            config = config,
            status = status,
            models = models,
            selectedSlug = selected,
            notice = notice,
            onProvider = viewModel::setProvider,
            onConnect = {
                viewModel.connect { url ->
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: ActivityNotFoundException) {
                        viewModel.cancelSignIn()
                    }
                }
            },
            onCancelSignIn = viewModel::cancelSignIn,
            onDisconnect = viewModel::disconnect,
            onSelectModel = viewModel::selectModel,
            onTest = viewModel::testCustom,
            onCustomEdited = viewModel::saveCustom,
            autoSort = autoSort,
            onAutoSort = viewModel::setAutoSort,
            taskModels = taskModels,
            defaultModelFor = { task -> viewModel.defaultFor(task, (models as? ModelsState.Loaded)?.models.orEmpty()) ?: selectedForDefault },
            onTaskModel = viewModel::setTaskModel,
        )
    }
}

@Composable
fun AiSettingsContent(
    config: AiConfig,
    status: ChatGptStatus,
    models: ModelsState,
    selectedSlug: String?,
    notice: String?,
    onProvider: (ProviderChoice) -> Unit,
    onConnect: () -> Unit,
    onCancelSignIn: () -> Unit,
    onDisconnect: () -> Unit,
    onSelectModel: (String) -> Unit,
    onTest: (baseUrl: String, apiKey: String) -> Unit,
    onCustomEdited: (baseUrl: String, apiKey: String) -> Unit,
    autoSort: Boolean = true,
    onAutoSort: (Boolean) -> Unit = {},
    taskModels: Map<AiTask, TaskModel> = emptyMap(),
    defaultModelFor: (AiTask) -> String? = { null },
    onTaskModel: (AiTask, TaskModel) -> Unit = { _, _ -> },
) {
    val showModel = if (config.provider == ProviderChoice.CHATGPT) {
        status is ChatGptStatus.Connected
    } else {
        models is ModelsState.Loaded || selectedSlug != null
    }
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        PanelSection("Connection") {
            Segmented(config.provider, onProvider)
            if (config.provider == ProviderChoice.CHATGPT) {
                ChatGptSection(status, notice, onConnect, onCancelSignIn, onDisconnect)
            } else {
                CustomSection(config, models, onTest, onCustomEdited)
            }
        }
        if (showModel) {
            PanelDivider()
            PanelSection("Default model") { ModelPicker(models, selectedSlug, onSelectModel) }
        }
        if (config.provider == ProviderChoice.COMPATIBLE || status is ChatGptStatus.Connected) {
            PanelDivider()
            PanelSection("Models per action") { TaskModelsSection(models, taskModels, defaultModelFor, onTaskModel) }
        }
        PanelDivider()
        PanelSection("Privacy") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-sort imported PDFs", fontFamily = BodyFamily, fontSize = 14.sp)
                    Text(
                        "Files new PDFs into a course with a short request to your model.",
                        fontFamily = BodyFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = autoSort, onCheckedChange = onAutoSort)
            }
            Text(
                PRIVACY_NOTE,
                fontFamily = MonoFamily,
                fontSize = 10.5.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.4.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Segmented(selected: ProviderChoice, onSelect: (ProviderChoice) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(3.dp),
    ) {
        listOf(ProviderChoice.CHATGPT to "ChatGPT plan", ProviderChoice.COMPATIBLE to "Custom endpoint").forEach { (choice, label) ->
            val on = choice == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) Violet else Color.Transparent)
                    .clickable { onSelect(choice) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontFamily = BodyFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChatGptSection(
    status: ChatGptStatus,
    notice: String?,
    onConnect: () -> Unit,
    onCancel: () -> Unit,
    onDisconnect: () -> Unit,
) {
    when (status) {
        is ChatGptStatus.Connected -> {
            if (status.email.isNotEmpty()) {
                PrivateText(
                    text = status.email,
                    label = "email",
                    prefix = "Connected as ",
                    style = TextStyle(fontFamily = BodyFamily, fontSize = 13.5.sp, lineHeight = 19.sp),
                    color = Violet,
                )
            } else {
                StatusLine("Connected", Violet)
            }
            GlassTextButton("Disconnect", onClick = onDisconnect)
        }
        ChatGptStatus.Connecting -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Violet)
                Text("Finish signing in in your browser…", fontFamily = BodyFamily, fontSize = 13.5.sp)
            }
            GlassTextButton("Cancel", onClick = onCancel)
        }
        else -> {
            StatusLine(
                (status as? ChatGptStatus.Error)?.message ?: notice ?: "Not connected",
                if (status is ChatGptStatus.Error || notice != null) Color(0xFFFF8A80) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GlassButton("Connect ChatGPT", onClick = onConnect)
        }
    }
}

@Composable
private fun StatusLine(text: String, color: Color) {
    Text(text, fontFamily = BodyFamily, fontSize = 13.5.sp, lineHeight = 19.sp, color = color)
}

@Composable
private fun CustomSection(
    config: AiConfig,
    models: ModelsState,
    onTest: (String, String) -> Unit,
    onEdited: (String, String) -> Unit,
) {
    var url by remember { mutableStateOf(config.baseUrl) }
    var key by remember { mutableStateOf(config.apiKey) }
    DisposableEffect(Unit) {
        onDispose { onEdited(url, key) }
    }
    OutlinedTextField(
        value = url,
        onValueChange = { url = it },
        label = { Text("Base URL") },
        placeholder = { Text(CUSTOM_URL_PLACEHOLDER, color = DimInkDark) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        colors = glassTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = key,
        onValueChange = { key = it },
        label = { Text("API key (optional)") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        colors = glassTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    GlassButton("Test & load models", enabled = url.isNotBlank() && models !is ModelsState.Loading, onClick = { onTest(url, key) })
    when (models) {
        ModelsState.Loading -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Violet)
        is ModelsState.Failed -> StatusLine(models.message, Color(0xFFFF8A80))
        else -> Unit
    }
}

@Composable
private fun ModelPicker(models: ModelsState, selectedSlug: String?, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val list = (models as? ModelsState.Loaded)?.models.orEmpty()
    val shape = RoundedCornerShape(12.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                    .clickable(enabled = list.isNotEmpty()) { open = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val shown = list.firstOrNull { it.slug == selectedSlug }?.displayName
                    ?: selectedSlug
                    ?: if (models is ModelsState.Loading) "Loading models…" else "Choose a model"
                Text(shown, fontFamily = BodyFamily, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Text("▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            GlassDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                list.forEach { model ->
                    GlassMenuItem(
                        label = model.displayName,
                        supporting = model.description.ifEmpty { null },
                        selected = model.slug == selectedSlug,
                        onClick = {
                            open = false
                            onSelect(model.slug)
                        },
                    )
                }
            }
        }
    }
}

private val TASK_LABELS = listOf(
    AiTask.ASK to "Ask",
    AiTask.EXPLAIN to "Explain page",
    AiTask.SUMMARIZE to "Summarize",
    AiTask.CARDS to "Flashcards",
    AiTask.AUTO_SORT to "Auto-sort & rename",
)

@Composable
private fun TaskModelsSection(
    models: ModelsState,
    overrides: Map<AiTask, TaskModel>,
    defaultModelFor: (AiTask) -> String?,
    onChange: (AiTask, TaskModel) -> Unit,
) {
    val list = (models as? ModelsState.Loaded)?.models.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TASK_LABELS.forEach { (task, label) ->
            val current = overrides[task] ?: TaskModel()
            val defaultSlug = defaultModelFor(task)
            val defaultName = list.firstOrNull { it.slug == defaultSlug }?.displayName ?: defaultSlug ?: "none"
            val efforts = (list.firstOrNull { it.slug == (current.model ?: defaultSlug) }?.efforts ?: emptyList())
                .ifEmpty { Effort.entries }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, fontFamily = BodyFamily, fontSize = 13.5.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsDropdown(
                        shown = current.model?.let { slug -> list.firstOrNull { it.slug == slug }?.displayName ?: slug }
                            ?: "Default \u00b7 $defaultName",
                        options = listOf<Pair<String, String?>>("Default \u00b7 $defaultName" to null) +
                            list.map { it.displayName to it.slug },
                        onSelect = { slug ->
                            val target = list.firstOrNull { it.slug == (slug ?: defaultSlug) }
                            val keep = current.effort?.takeIf { target == null || target.efforts.isEmpty() || it in target.efforts }
                            onChange(task, TaskModel(slug, keep))
                        },
                        supporting = { slug -> list.firstOrNull { it.slug == slug }?.description?.ifEmpty { null } },
                        modifier = Modifier.weight(1f),
                    )
                    SettingsDropdown(
                        shown = current.effort?.label() ?: "Auto",
                        options = listOf<Pair<String, Effort?>>("Auto" to null) + efforts.map { it.label() to it },
                        onSelect = { onChange(task, current.copy(effort = it)) },
                        modifier = Modifier.width(120.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> SettingsDropdown(
    shown: String,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    supporting: (T) -> String? = { null },
) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(shown, fontFamily = BodyFamily, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("\u25be", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        GlassDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (label, value) ->
                GlassMenuItem(
                    label = label,
                    supporting = supporting(value),
                    selected = label == shown,
                    onClick = {
                        open = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

/** Sidebar footer row: opens the settings sheet and owns it. */
@Composable
fun AiSidebarItem(viewModel: AiSettingsViewModel = hiltViewModel()) {
    val label by viewModel.label.collectAsStateWithLifecycle()
    val allowed by viewModel.aiAllowed.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    if (!allowed) return
    if (open) AiSettingsSheet(onDismiss = { open = false }, viewModel = viewModel)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable { open = true }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Icon(Icons.Outlined.AutoAwesome, null, tint = Violet, modifier = Modifier.size(16.dp))
        Column {
            Text("AI", fontFamily = BodyFamily, fontSize = 13.5.sp, color = Color(0xFFA0A0AB))
            Text(label, fontFamily = MonoFamily, fontSize = 10.5.sp, letterSpacing = 0.4.sp, color = DimInkDark, maxLines = 1)
        }
    }
}
