package com.serendeep.marginalia.backup

import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** What the Backup section shows besides its fixed rows. */
sealed interface BackupState {
    data object Idle : BackupState
    data class Working(val label: String) : BackupState
    data class Confirm(val summary: BackupSummary) : BackupState
    data class Done(val message: String) : BackupState
    data class Failed(val message: String) : BackupState
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exporter: BackupExporter,
    private val importer: BackupImporter,
    private val auto: AutoBackup,
) : ViewModel() {
    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state.asStateFlow()
    val autoConfig: StateFlow<AutoBackupConfig> = auto.config

    fun export(uri: Uri) = launchWork("Exporting…") {
        val stream = context.contentResolver.openOutputStream(uri) ?: throw BackupException("Couldn't write to that location")
        val manifest = stream.use { exporter.export(it) }
        val size = Formatter.formatFileSize(context, manifest.files.sumOf { it.size })
        BackupState.Done("Exported ${manifest.counts.lectures} notebooks · $size")
    }

    fun inspect(uri: Uri) = launchWork("Checking backup…") { BackupState.Confirm(importer.inspect(uri)) }

    fun replaceLibrary() = launchWork("Restoring…") {
        importer.restore()
        BackupState.Idle
    }

    fun cancelImport() {
        importer.discard()
        _state.value = BackupState.Idle
    }

    fun setAuto(transform: (AutoBackupConfig) -> AutoBackupConfig) = auto.update(transform)

    private fun launchWork(label: String, block: suspend () -> BackupState) {
        if (_state.value is BackupState.Working) return
        _state.value = BackupState.Working(label)
        viewModelScope.launch {
            _state.value = try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: BackupException) {
                BackupState.Failed(e.message.orEmpty())
            } catch (e: Exception) {
                BackupState.Failed(e.message ?: "Something went wrong")
            }
        }
    }
}
