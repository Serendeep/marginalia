package com.serendeep.marginalia.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class BackupDetails(val appVersionName: String, val counts: BackupCounts)

/** Backups kept in app storage, their listing, and the automatic safety snapshots. */
@Singleton
class BackupStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exporter: BackupExporter,
) : SafetySnapshot {
    private val root = File(context.filesDir, "backups")
    private val autoDir = File(root, "auto")
    private val snapshotDir = File(root, "snapshots")
    private val details = ConcurrentHashMap<String, BackupDetails>()

    init {
        // Safety exports used to sit directly under backups/.
        root.listFiles { f -> f.isFile && BackupName.parse(f.name) != null }?.forEach {
            snapshotDir.mkdirs()
            it.renameTo(File(snapshotDir, it.name))
        }
    }

    /** Blocking. Writes into [dir] via a temporary file so a half-written zip is never listed. */
    private fun write(dir: File, name: BackupName): File {
        dir.mkdirs()
        val part = File(dir, name.fileName + ".part")
        try {
            part.outputStream().use { exporter.export(it) }
            val target = File(dir, name.fileName)
            if (!part.renameTo(target)) throw BackupException("Couldn't save the backup")
            return target
        } finally {
            part.delete()
        }
    }

    private fun trim(dir: File, kind: BackupKind, keep: Int) {
        staleNames(dir.list().orEmpty().toList(), kind, keep).forEach { File(dir, it).delete() }
    }

    fun exportTo(out: java.io.OutputStream) {
        exporter.export(out)
    }

    fun nightlyCount() = autoDir.list().orEmpty().count { BackupName.parse(it)?.kind == BackupKind.NIGHTLY }

    /** Blocking; throws on failure. */
    fun writeNightly(keep: Int) {
        write(autoDir, BackupName(BackupKind.NIGHTLY, null, BackupName.stampOf(System.currentTimeMillis())))
        trim(autoDir, BackupKind.NIGHTLY, keep)
    }

    /** Blocking; throws on failure. */
    fun writeSnapshot(name: BackupName) {
        write(snapshotDir, name)
        name.kind.keep?.let { trim(snapshotDir, name.kind, it) }
    }

    override suspend fun take(label: String) {
        val name = BackupName.forLabel(label, BackupName.stampOf(System.currentTimeMillis()))
        if (name == null) {
            Log.w(TAG, "Unknown snapshot label: $label")
            return
        }
        try {
            withContext(Dispatchers.IO) { writeSnapshot(name) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Snapshot '$label' failed", e)
        }
    }

    fun appSource() = BackupSource {
        listOf(autoDir, snapshotDir).flatMap { dir ->
            dir.listFiles().orEmpty().mapNotNull { f ->
                BackupName.parse(f.name)?.let { BackupItem(it, f.length(), f.path, inAppStorage = true) }
            }
        }
    }

    fun folderSource(treeUri: String?) = BackupSource {
        val tree = treeUri?.let { DocumentFile.fromTreeUri(context, Uri.parse(it)) } ?: return@BackupSource emptyList()
        tree.listFiles().mapNotNull { f ->
            BackupName.parse(f.name.orEmpty())?.takeIf { it.kind == BackupKind.FOLDER }
                ?.let { BackupItem(it, f.length(), f.uri.toString(), inAppStorage = false) }
        }
    }

    suspend fun list(treeUri: String?): List<BackupItem> =
        withContext(Dispatchers.IO) { listBackups(listOf(appSource(), folderSource(treeUri))) }

    /** Reads the manifest once per file; null when it can't be read. */
    suspend fun details(item: BackupItem): BackupDetails? {
        val key = "${item.location}#${item.sizeBytes}"
        details[key]?.let { return it }
        return withContext(Dispatchers.IO) {
            val manifest = if (item.inAppStorage) BackupArchive.readManifest(File(item.location)) else readFromUri(Uri.parse(item.location))
            manifest?.let { BackupDetails(it.appVersionName, it.counts) }?.also { details[key] = it }
        }
    }

    // A seekable descriptor lets ZipFile find the manifest without reading the whole archive.
    private fun readFromUri(uri: Uri): BackupManifest? = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { BackupArchive.readManifest(File("/proc/self/fd/${it.fd}")) }
    } catch (_: Exception) {
        null
    }

    /** Copies an app-storage backup to [uri]. Blocking. */
    fun copyTo(item: BackupItem, uri: Uri) {
        val out = context.contentResolver.openOutputStream(uri) ?: throw BackupException("Couldn't write to that location")
        out.use { o -> File(item.location).inputStream().use { it.copyTo(o) } }
    }

    fun trimFolder(tree: DocumentFile, keep: Int): Int {
        val files = tree.listFiles().filter { BackupName.parse(it.name.orEmpty())?.kind == BackupKind.FOLDER }
        val stale = staleNames(files.map { it.name.orEmpty() }, BackupKind.FOLDER, keep).toSet()
        files.filter { it.name in stale }.forEach { it.delete() }
        return files.size - stale.size
    }

    private companion object {
        const val TAG = "BackupStore"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SafetySnapshotModule {
    @Binds abstract fun snapshot(store: BackupStore): SafetySnapshot
}
