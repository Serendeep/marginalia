package com.serendeep.marginalia.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.serendeep.marginalia.data.MarginaliaDatabase
import com.serendeep.marginalia.shell.PREFS
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class BackupSummary(val createdAt: Long, val appVersionName: String, val counts: BackupCounts, val sizeBytes: Long)

private const val DB_NAME = "marginalia.db"
private const val KEEP_SAFETY_EXPORTS = 2

@Singleton
class BackupImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: MarginaliaDatabase,
    private val exporter: BackupExporter,
) {
    private val staged = File(context.cacheDir, "backup-import.zip")

    /** Copies the picked zip aside and validates it; [restore] then works from that copy. Blocking. */
    fun inspect(uri: Uri): BackupSummary {
        staged.delete()
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw BackupException("Couldn't open that file")
            input.use { src -> staged.outputStream().use { src.copyTo(it) } }
        } catch (e: IOException) {
            staged.delete()
            throw BackupException("Couldn't read that file")
        }
        try {
            val m = BackupArchive.verify(staged, currentSchema())
            return BackupSummary(m.createdAt, m.appVersionName, m.counts, staged.length())
        } catch (e: BackupException) {
            staged.delete()
            throw e
        }
    }

    fun discard() {
        staged.delete()
    }

    /** Swaps in the inspected backup and restarts the process; does not return on success. Blocking. */
    fun restore() {
        if (!staged.exists()) throw BackupException("Pick a backup first")
        val manifest = BackupArchive.verify(staged, currentSchema())
        writeSafetyExport()

        val stage = File(context.filesDir, "restore-staging")
        stage.deleteRecursively()
        stage.mkdirs()
        try {
            BackupArchive.extract(staged, manifest, stage)
        } catch (e: Exception) {
            stage.deleteRecursively()
            throw if (e is BackupException) e else BackupException("Couldn't unpack the backup")
        }

        // Everything below is quick renames so the closed database isn't reopened half-way.
        db.close()
        val dbFile = context.getDatabasePath(DB_NAME)
        listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
        if (!File(stage, DB_ENTRY).renameTo(dbFile)) throw BackupException("Couldn't replace the database")
        for (dir in LIBRARY_DIRS) {
            val target = File(context.filesDir, dir)
            target.deleteRecursively()
            File(stage, "$FILES_PREFIX$dir").takeIf { it.exists() }?.renameTo(target)
        }
        File(stage, PREFS_ENTRY).takeIf { it.exists() }?.let {
            BackupPrefs.restore(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE), BackupPrefs.decode(it.readBytes()))
        }
        stage.deleteRecursively()
        staged.delete()
        restartApp()
    }

    private fun currentSchema() = db.openHelper.readableDatabase.version

    private fun writeSafetyExport() {
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        File(dir, "before-restore-$stamp.zip").outputStream().use { exporter.export(it) }
        dir.listFiles { f -> f.name.startsWith("before-restore-") }
            ?.sortedByDescending { it.name }
            ?.drop(KEEP_SAFETY_EXPORTS)
            ?.forEach { it.delete() }
    }

    private fun restartApp() {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (launch != null) context.startActivity(launch)
        Runtime.getRuntime().exit(0)
    }
}
