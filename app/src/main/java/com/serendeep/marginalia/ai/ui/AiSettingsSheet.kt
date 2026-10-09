package com.serendeep.marginalia.ai.ui

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serendeep.marginalia.ai.AiConfig
import com.serendeep.marginalia.ai.ChatGptStatus
import com.serendeep.marginalia.ai.ProviderChoice
import com.serendeep.marginalia.ui.components.GlassButton
import com.serendeep.marginalia.ui.components.GlassTextButton
import com.serendeep.marginalia.ui.components.MarginLabel
import com.serendeep.marginalia.ui.components.glassTextFieldColors
import com.serendeep.marginalia.ui.theme.BodyFamily
import com.serendeep.marginalia.ui.theme.DimInkDark
import com.serendeep.marginalia.ui.theme.MonoFamily
import com.serendeep.marginalia.ui.theme.Violet

const val CUSTOM_URL_PLACEHOLDER = "https://cursedpc.tailbd2879.ts.net/v1"
const val PRIVACY_NOTE = "Nothing is sent unless you tap an AI action. Requests don't store your data (store:false)."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsSheet(onDismiss: () -> Unit, viewModel: AiSettingsViewModel = hiltViewModel()) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val autoSort by viewModel.autoSort.collectAsStateWithLifecycle()
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
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
) {
    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MarginLabel("AI")
        Segmented(config.provider, onProvider)
        if (config.provider == ProviderChoice.CHATGPT) {
            ChatGptSection(status, models, selectedSlug, notice, onConnect, onCancelSignIn, onDisconnect, onSelectModel)
        } else {
            CustomSection(config, models, selectedSlug, onTest, onCustomEdited, onSelectModel)
        }
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
        Spacer(Modifier.padding(bottom = 8.dp))
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
    models: ModelsState,
    selectedSlug: String?,
    notice: String?,
    onConnect: () -> Unit,
    onCancel: () -> Unit,
    onDisconnect: () -> Unit,
    onSelectModel: (String) -> Unit,
) {
    when (status) {
        is ChatGptStatus.Connected -> {
            StatusLine(if (status.email.isNotEmpty()) "Connected as ${status.email}" else "Connected", Violet)
            ModelPicker(models, selectedSlug, onSelectModel)
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
    selectedSlug: String?,
    onTest: (String, String) -> Unit,
    onEdited: (String, String) -> Unit,
    onSelectModel: (String) -> Unit,
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
    if (models is ModelsState.Loaded || selectedSlug != null) ModelPicker(models, selectedSlug, onSelectModel)
}

@Composable
private fun ModelPicker(models: ModelsState, selectedSlug: String?, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val list = (models as? ModelsState.Loaded)?.models.orEmpty()
    val shape = RoundedCornerShape(12.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("MODEL", fontFamily = MonoFamily, fontSize = 10.5.sp, letterSpacing = 1.26.sp, color = DimInkDark)
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
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                list.forEach { model ->
                    DropdownMenuItem(text = { Text(model.displayName) }, onClick = {
                        open = false
                        onSelect(model.slug)
                    })
                }
            }
        }
    }
}

/** Sidebar footer row: opens the settings sheet and owns it. */
@Composable
fun AiSidebarItem(viewModel: AiSettingsViewModel = hiltViewModel()) {
    val label by viewModel.label.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
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
