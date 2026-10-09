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
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

const val DEFAULT_BACKUP_MINUTE = 3 * 60

data class AutoBackupConfig(
    val schedule: BackupSchedule = BackupSchedule.NIGHTLY,
    val destination: BackupDestination = BackupDestination.APP,
    val minuteOfDay: Int = DEFAULT_BACKUP_MINUTE,
    val onlyCharging: Boolean = true,
    val treeUri: String? = null,
    val keep: Int = 7,
    val lastRunAt: Long = 0,
    val lastError: String? = null,
    val kept: Int = 0,
) {
    /** A folder destination without a folder can't run. */
    val active get() = schedule != BackupSchedule.OFF && (destination == BackupDestination.APP || treeUri != null)
}

private const val WORK_NAME = "auto-backup"
private const val KEY_SCHEDULE = "backup_auto_schedule"
private const val KEY_DESTINATION = "backup_auto_destination"
private const val KEY_MINUTE = "backup_auto_minute"
private const val KEY_CHARGING = "backup_auto_charging"
private const val KEY_TREE = "backup_auto_tree"
private const val KEY_KEEP = "backup_auto_keep"
private const val KEY_LAST_RUN = "backup_auto_last_run"
private const val KEY_LAST_ERROR = "backup_auto_last_error"
private const val KEY_KEPT = "backup_auto_kept"

// Earlier layout: an on/off switch plus a daily/weekly choice, always into a folder.
private const val KEY_LEGACY_ENABLED = "backup_auto_enabled"
private const val KEY_LEGACY_FREQUENCY = "backup_auto_frequency"

/** Reads the config from raw prefs, upgrading the folder-only layout; fresh installs get nightly into app storage. */
fun autoBackupConfigFrom(p: Map<String, *>): AutoBackupConfig {
    val tree = p[KEY_TREE] as? String
    val base = AutoBackupConfig(
        treeUri = tree,
        keep = p[KEY_KEEP] as? Int ?: 7,
        lastRunAt = p[KEY_LAST_RUN] as? Long ?: 0,
        lastError = p[KEY_LAST_ERROR] as? String,
        kept = p[KEY_KEPT] as? Int ?: 0,
    )
    val schedule = (p[KEY_SCHEDULE] as? String)?.let { runCatching { BackupSchedule.valueOf(it) }.getOrNull() }
    if (schedule == null) {
        val legacyFolder = p[KEY_LEGACY_ENABLED] == true && tree != null
        return if (!legacyFolder) base else base.copy(
            destination = BackupDestination.FOLDER,
            schedule = if (p[KEY_LEGACY_FREQUENCY] == "WEEKLY") BackupSchedule.WEEKLY else BackupSchedule.NIGHTLY,
        )
    }
    return base.copy(
        schedule = schedule,
        destination = (p[KEY_DESTINATION] as? String)?.let { runCatching { BackupDestination.valueOf(it) }.getOrNull() }
            ?: BackupDestination.APP,
        minuteOfDay = (p[KEY_MINUTE] as? Int)?.takeIf { it in 0 until 24 * 60 } ?: DEFAULT_BACKUP_MINUTE,
        onlyCharging = p[KEY_CHARGING] as? Boolean ?: true,
    )
}

/** On by default and silent: no notification, no foreground service. */
@Singleton
class AutoBackup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: BackupStore,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(autoBackupConfigFrom(prefs.all))
    val config: StateFlow<AutoBackupConfig> = _config.asStateFlow()

    init {
        save(_config.value)
    }

    @Synchronized
    fun update(transform: (AutoBackupConfig) -> AutoBackupConfig) {
        val next = transform(_config.value)
        save(next)
        _config.value = next
        schedule(next)
    }

    /** Called at launch so the default schedule exists without the user opening Settings. */
    fun ensureScheduled() = schedule(_config.value)

    /** Writes one backup to the chosen destination and trims old ones. Never throws. */
    suspend fun run() {
        val cfg = _config.value
        if (!cfg.active) return
        withContext(Dispatchers.IO) {
            try {
                val kept = when (cfg.destination) {
                    BackupDestination.APP -> {
                        store.writeNightly(cfg.keep)
                        store.nightlyCount()
                    }
                    BackupDestination.FOLDER -> writeToFolder(cfg)
                }
                update { it.copy(lastRunAt = System.currentTimeMillis(), lastError = null, kept = kept) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                update { it.copy(lastRunAt = System.currentTimeMillis(), lastError = e.message ?: "Backup failed") }
            }
        }
    }

    private fun writeToFolder(cfg: AutoBackupConfig): Int {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(cfg.treeUri))
        if (tree == null || !tree.canWrite()) throw BackupException("The backup folder isn't available")
        val name = BackupName(BackupKind.FOLDER, null, BackupName.stampOf(System.currentTimeMillis())).fileName
        val created = tree.createFile("application/zip", name) ?: throw BackupException("Couldn't create a file in the backup folder")
        try {
            val out = context.contentResolver.openOutputStream(created.uri) ?: throw BackupException("Couldn't write to the backup folder")
            out.use { store.exportTo(it) }
        } catch (e: Exception) {
            created.delete()
            throw e
        }
        return store.trimFolder(tree, cfg.keep)
    }

    private fun schedule(cfg: AutoBackupConfig) {
        val work = WorkManager.getInstance(context)
        if (!cfg.active) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiresCharging(cfg.onlyCharging)
            .setRequiresStorageNotLow(true)
            .build()
        val days = if (cfg.schedule == BackupSchedule.WEEKLY) 7L else 1L
        val delay = nextRunDelay(Instant.now(), cfg.minuteOfDay, ZoneId.systemDefault())
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(days, TimeUnit.DAYS)
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun save(c: AutoBackupConfig) {
        prefs.edit()
            .putString(KEY_SCHEDULE, c.schedule.name)
            .putString(KEY_DESTINATION, c.destination.name)
            .putInt(KEY_MINUTE, c.minuteOfDay)
            .putBoolean(KEY_CHARGING, c.onlyCharging)
            .putString(KEY_TREE, c.treeUri)
            .putInt(KEY_KEEP, c.keep)
            .putLong(KEY_LAST_RUN, c.lastRunAt)
            .putString(KEY_LAST_ERROR, c.lastError)
            .putInt(KEY_KEPT, c.kept)
            .remove(KEY_LEGACY_ENABLED)
            .remove(KEY_LEGACY_FREQUENCY)
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
