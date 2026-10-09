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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import java.time.LocalDate

/** One-line summary under the automatic backup toggle. */
fun backupStatusLine(cfg: AutoBackupConfig, now: Long = System.currentTimeMillis()): String = when {
    cfg.lastError != null -> "Last backup failed: ${cfg.lastError}"
    cfg.lastRunAt == 0L -> "No automatic backup yet"
    else -> "Last backup " +
        DateUtils.getRelativeTimeSpanString(cfg.lastRunAt, now, DateUtils.MINUTE_IN_MILLIS) + " · ${cfg.kept} kept"
}

@Composable
fun BackupSectionContent(vm: BackupViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val cfg by vm.autoConfig.collectAsStateWithLifecycle()
    var frequencyMenu by remember { mutableStateOf(false) }
    val working = state is BackupState.Working

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.export(uri)
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.inspect(uri)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            vm.setAuto { it.copy(enabled = true, treeUri = uri.toString(), lastError = null) }
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

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Automatic backups", modifier = Modifier.weight(1f))
        Switch(
            checked = cfg.enabled,
            onCheckedChange = { on ->
                if (!on) vm.setAuto { it.copy(enabled = false) }
                else if (cfg.treeUri != null) vm.setAuto { it.copy(enabled = true) }
                else folderPicker.launch(null)
            },
        )
    }
    if (cfg.enabled) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                GlassTextButton(cfg.frequency.label + " ▾", onClick = { frequencyMenu = true })
                GlassDropdownMenu(expanded = frequencyMenu, onDismissRequest = { frequencyMenu = false }) {
                    BackupFrequency.entries.forEach { f ->
                        GlassMenuItem(f.label, selected = f == cfg.frequency, onClick = {
                            frequencyMenu = false
                            vm.setAuto { it.copy(frequency = f) }
                        })
                    }
                }
            }
            GlassTextButton("Change folder", onClick = { folderPicker.launch(null) })
        }
        Text(
            backupStatusLine(cfg),
            style = MaterialTheme.typography.bodySmall,
            color = if (cfg.lastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Runs quietly while the tablet is charging and idle.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
