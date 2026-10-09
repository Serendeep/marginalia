package com.serendeep.marginalia.backup

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serendeep.marginalia.ui.components.GlassButton
import com.serendeep.marginalia.ui.components.GlassDropdownMenu
import com.serendeep.marginalia.ui.components.GlassMenuItem
import com.serendeep.marginalia.ui.components.GlassTextButton
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import java.time.LocalDate

@Composable
fun BackupSectionContent(vm: BackupViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val cfg by vm.autoConfig.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    var showAll by remember { mutableStateOf(false) }
    var editTime by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf<BackupItem?>(null) }
    val working = state is BackupState.Working

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.export(uri)
    }
    val itemPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        exporting?.let { item -> if (uri != null) vm.export(item, uri) }
        exporting = null
    }
    LaunchedEffect(cfg.lastRunAt, cfg.treeUri, cfg.destination, working) { if (!working) vm.refreshRecent() }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.inspect(uri)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            vm.setAuto { it.copy(destination = BackupDestination.FOLDER, treeUri = uri.toString(), lastError = null) }
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassButton("Export now", enabled = !working, onClick = { exportPicker.launch("marginalia-backup-${LocalDate.now()}.zip") })
        GlassTextButton(
            "Import…",
            enabled = !working,
            onClick = { importPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
        )
    }

    when (val s = state) {
        is BackupState.Working -> {
            Text(s.label, style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        is BackupState.Confirm -> {
            val m = s.summary
            Text("Replace your library with this backup?", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Made ${DateUtils.formatDateTime(context, m.createdAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME)}" +
                    " · ${Formatter.formatFileSize(context, m.sizeBytes)}\n" +
                    "${m.counts.lectures} notebooks · ${m.counts.documents} documents · ${m.counts.strokes} strokes · " +
                    "${m.counts.cards} cards · ${m.counts.highlights} highlights\n" +
                    "A copy of your current library is saved first, then Marginalia restarts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassButton("Replace library", containerColor = MaterialTheme.colorScheme.error, onClick = vm::replaceLibrary)
                GlassTextButton("Cancel", onClick = vm::cancelImport)
            }
        }
        is BackupState.Done -> Text(s.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        is BackupState.Failed -> Text(s.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        BackupState.Idle -> Unit
    }

    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Automatic backups", modifier = Modifier.weight(1f))
        Choice(cfg.schedule.label, BackupSchedule.entries.map { it.label to it }, cfg.schedule) { sch ->
            vm.setAuto { it.copy(schedule = sch) }
        }
    }
    if (cfg.schedule != BackupSchedule.OFF) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Time", modifier = Modifier.weight(1f))
            GlassTextButton("%02d:%02d".format(cfg.minuteOfDay / 60, cfg.minuteOfDay % 60) + if (editTime) " ▴" else " ▾", onClick = { editTime = !editTime })
        }
        if (editTime) BackupTimeInput(cfg.minuteOfDay) { m -> vm.setAuto { it.copy(minuteOfDay = m) } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Save to", modifier = Modifier.weight(1f))
            if (cfg.destination == BackupDestination.FOLDER) GlassTextButton("Change folder", onClick = { folderPicker.launch(null) })
            Choice(cfg.destination.label, BackupDestination.entries.map { it.label to it }, cfg.destination) { d ->
                if (d == BackupDestination.FOLDER && cfg.treeUri == null) folderPicker.launch(null)
                else vm.setAuto { it.copy(destination = d, lastError = null) }
            }
        }
        if (cfg.destination == BackupDestination.APP) {
            Text(
                "Backups in app storage are removed if Marginalia is uninstalled. Pick a folder to keep them safe.",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Only while charging", modifier = Modifier.weight(1f))
            Switch(checked = cfg.onlyCharging, onCheckedChange = { on -> vm.setAuto { it.copy(onlyCharging = on) } })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Keep last", modifier = Modifier.weight(1f))
            Choice(cfg.keep.toString(), listOf(3, 7, 14, 30).map { it.toString() to it }, cfg.keep) { n ->
                vm.setAuto { it.copy(keep = n) }
            }
        }
        Text(
            backupStatusLine(cfg),
            style = MaterialTheme.typography.bodySmall,
            color = if (cfg.lastError != null) MaterialTheme.colorScheme.error else muted,
        )
    }

    if (recent.isNotEmpty()) {
        Text("Recent backups", style = MaterialTheme.typography.labelLarge)
        recent.take(if (showAll) recent.size else RECENT_LIMIT).forEach { item ->
            val details by produceState<BackupDetails?>(null, item.location, item.sizeBytes) { value = vm.details(item) }
            Column(Modifier.fillMaxWidth()) {
                Text(item.name.title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    DateUtils.formatDateTime(context, item.timeMillis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME) +
                        details?.let { " · v${it.appVersionName} · ${it.counts.lectures} notebooks · ${it.counts.documents} documents" }.orEmpty() +
                        " · ${Formatter.formatFileSize(context, item.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassTextButton("Restore", enabled = !working, onClick = { vm.inspect(item) })
                    if (item.inAppStorage) GlassTextButton("Export", enabled = !working, onClick = {
                        exporting = item
                        itemPicker.launch(item.name.fileName)
                    })
                }
            }
        }
        if (recent.size > RECENT_LIMIT && !showAll) GlassTextButton("Show all (${recent.size})", onClick = { showAll = true })
    }
}

private const val RECENT_LIMIT = 10

@Composable
private fun <T> Choice(current: String, options: List<Pair<String, T>>, selected: T, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        GlassTextButton("$current ▾", onClick = { open = true })
        GlassDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (label, value) ->
                GlassMenuItem(label, selected = value == selected, onClick = {
                    open = false
                    onPick(value)
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
private fun BackupTimeInput(minuteOfDay: Int, onChange: (Int) -> Unit) {
    val state = rememberTimePickerState(minuteOfDay / 60, minuteOfDay % 60, is24Hour = true)
    LaunchedEffect(state) {
        snapshotFlow { state.hour * 60 + state.minute }.drop(1).debounce(800).collect(onChange)
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimeInput(state = state) }
}
