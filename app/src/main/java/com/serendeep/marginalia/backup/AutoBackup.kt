package com.serendeep.marginalia.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.serendeep.marginalia.shell.PREFS
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class BackupFrequency(val label: String, val days: Long) { DAILY("Daily", 1), WEEKLY("Weekly", 7) }

data class AutoBackupConfig(
    val enabled: Boolean = false,
    val treeUri: String? = null,
    val frequency: BackupFrequency = BackupFrequency.DAILY,
    val keep: Int = 7,
    val lastRunAt: Long = 0,
    val lastError: String? = null,
    val kept: Int = 0,
)

private const val WORK_NAME = "auto-backup"
private const val NAME_PREFIX = "marginalia-backup-"
private const val KEY_ENABLED = "backup_auto_enabled"
private const val KEY_TREE = "backup_auto_tree"
private const val KEY_FREQUENCY = "backup_auto_frequency"
private const val KEY_KEEP = "backup_auto_keep"
private const val KEY_LAST_RUN = "backup_auto_last_run"
private const val KEY_LAST_ERROR = "backup_auto_last_error"
private const val KEY_KEPT = "backup_auto_kept"

/** Off by default and silent: it only ever writes into the folder the user picked. */
@Singleton
class AutoBackup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exporter: BackupExporter,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(load())
    val config: StateFlow<AutoBackupConfig> = _config.asStateFlow()

    @Synchronized
    fun update(transform: (AutoBackupConfig) -> AutoBackupConfig) {
        val next = transform(_config.value)
        save(next)
        _config.value = next
        schedule(next)
    }

    /** Writes one backup into the chosen folder and trims old ones. Never throws. */
    suspend fun run() {
        val cfg = _config.value
        val tree = cfg.treeUri?.let { DocumentFile.fromTreeUri(context, Uri.parse(it)) }
        if (!cfg.enabled || tree == null) return
        withContext(Dispatchers.IO) {
            var created: DocumentFile? = null
            try {
                if (!tree.canWrite()) throw BackupException("The backup folder isn't available")
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                created = tree.createFile("application/zip", "$NAME_PREFIX$stamp.zip")
                    ?: throw BackupException("Couldn't create a file in the backup folder")
                val out = context.contentResolver.openOutputStream(created.uri) ?: throw BackupException("Couldn't write to the backup folder")
                out.use { exporter.export(it) }
                val all = tree.listFiles()
                    .filter { it.name.orEmpty().let { n -> n.startsWith(NAME_PREFIX) && n.endsWith(".zip") } }
                    .sortedByDescending { it.name }
                all.drop(cfg.keep).forEach { it.delete() }
                update { it.copy(lastRunAt = System.currentTimeMillis(), lastError = null, kept = minOf(all.size, cfg.keep)) }
            } catch (e: CancellationException) {
                created?.delete()
                throw e
            } catch (e: Exception) {
                created?.delete()
                update { it.copy(lastRunAt = System.currentTimeMillis(), lastError = e.message ?: "Backup failed") }
            }
        }
    }

    private fun schedule(cfg: AutoBackupConfig) {
        val work = WorkManager.getInstance(context)
        if (!cfg.enabled || cfg.treeUri == null) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresDeviceIdle(true)
            .setRequiresStorageNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(cfg.frequency.days, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun load() = AutoBackupConfig(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        treeUri = prefs.getString(KEY_TREE, null),
        frequency = prefs.getString(KEY_FREQUENCY, null)?.let { runCatching { BackupFrequency.valueOf(it) }.getOrNull() }
            ?: BackupFrequency.DAILY,
        keep = prefs.getInt(KEY_KEEP, 7),
        lastRunAt = prefs.getLong(KEY_LAST_RUN, 0),
        lastError = prefs.getString(KEY_LAST_ERROR, null),
        kept = prefs.getInt(KEY_KEPT, 0),
    )

    private fun save(c: AutoBackupConfig) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, c.enabled)
            .putString(KEY_TREE, c.treeUri)
            .putString(KEY_FREQUENCY, c.frequency.name)
            .putInt(KEY_KEEP, c.keep)
            .putLong(KEY_LAST_RUN, c.lastRunAt)
            .putString(KEY_LAST_ERROR, c.lastError)
            .putInt(KEY_KEPT, c.kept)
            .apply()
    }
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        EntryPointAccessors.fromApplication(applicationContext, AutoBackupEntryPoint::class.java).autoBackup().run()
        return Result.success()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutoBackupEntryPoint {
    fun autoBackup(): AutoBackup
}
