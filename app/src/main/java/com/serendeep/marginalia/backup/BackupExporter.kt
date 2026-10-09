package com.serendeep.marginalia.backup

import android.content.Context
import com.serendeep.marginalia.BuildConfig
import com.serendeep.marginalia.data.MarginaliaDatabase
import com.serendeep.marginalia.shell.PREFS
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Writes the whole library to a single zip: database snapshot, PDFs, card images and settings. */
@Singleton
class BackupExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: MarginaliaDatabase,
) {
    /** Blocking; call off the main thread. [out] is left open. */
    fun export(out: OutputStream): BackupManifest {
        val snapshot = File(context.cacheDir, "backup-${System.nanoTime()}.db")
        try {
            val sqlite = db.openHelper.writableDatabase
            sqlite.execSQL("VACUUM INTO '${snapshot.path.replace("'", "''")}'")
            val prefs = BackupPrefs.encode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all)
            val entries = buildList {
                add(BackupEntry(DB_ENTRY) { snapshot.inputStream() })
                add(BackupEntry(PREFS_ENTRY) { prefs.inputStream() })
                for (dir in LIBRARY_DIRS) {
                    val root = File(context.filesDir, dir)
                    root.walkTopDown().filter { it.isFile }.forEach { f ->
                        add(BackupEntry("$FILES_PREFIX$dir/${f.relativeTo(root).invariantSeparatorsPath}") { f.inputStream() })
                    }
                }
            }
            val template = BackupManifest(
                format = BACKUP_FORMAT,
                appVersionName = BuildConfig.VERSION_NAME,
                versionCode = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode,
                schemaVersion = sqlite.version,
                createdAt = System.currentTimeMillis(),
                counts = BackupCounts(
                    count("lectures"), count("documents"), count("strokes"), count("cards"), count("highlights"),
                ),
                files = emptyList(),
            )
            return BackupArchive.write(out, entries, template)
        } finally {
            snapshot.delete()
        }
    }

    private fun count(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { if (it.moveToFirst()) it.getInt(0) else 0 }
}
