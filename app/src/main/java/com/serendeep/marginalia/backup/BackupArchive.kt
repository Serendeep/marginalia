package com.serendeep.marginalia.backup

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

const val BACKUP_FORMAT = 1
const val DB_ENTRY = "db/marginalia.db"
const val PREFS_ENTRY = "prefs/settings.json"
const val FILES_PREFIX = "files/"

/** Per-library directories under filesDir that travel with a backup. */
val LIBRARY_DIRS = listOf("pdfs", "cards")

class BackupException(message: String) : Exception(message)

data class BackupCounts(val lectures: Int, val documents: Int, val strokes: Int, val cards: Int, val highlights: Int)

data class BackupFile(val path: String, val size: Long, val sha256: String)

data class BackupManifest(
    val format: Int,
    val appVersionName: String,
    val versionCode: Long,
    val schemaVersion: Int,
    val createdAt: Long,
    val counts: BackupCounts,
    val files: List<BackupFile>,
) {
    fun toJson(): String = JSONObject()
        .put("format", format)
        .put("appVersionName", appVersionName)
        .put("versionCode", versionCode)
        .put("schemaVersion", schemaVersion)
        .put("createdAt", createdAt)
        .put(
            "counts",
            JSONObject()
                .put("lectures", counts.lectures)
                .put("documents", counts.documents)
                .put("strokes", counts.strokes)
                .put("cards", counts.cards)
                .put("highlights", counts.highlights),
        )
        .put(
            "files",
            JSONArray().apply {
                files.forEach { put(JSONObject().put("path", it.path).put("size", it.size).put("sha256", it.sha256)) }
            },
        )
        .toString()

    companion object {
        fun parse(json: String): BackupManifest {
            val o = JSONObject(json)
            val c = o.getJSONObject("counts")
            val f = o.getJSONArray("files")
            return BackupManifest(
                format = o.getInt("format"),
                appVersionName = o.getString("appVersionName"),
                versionCode = o.getLong("versionCode"),
                schemaVersion = o.getInt("schemaVersion"),
                createdAt = o.getLong("createdAt"),
                counts = BackupCounts(
                    c.getInt("lectures"), c.getInt("documents"), c.getInt("strokes"), c.getInt("cards"), c.getInt("highlights"),
                ),
                files = (0 until f.length()).map {
                    val e = f.getJSONObject(it)
                    BackupFile(e.getString("path"), e.getLong("size"), e.getString("sha256"))
                },
            )
        }
    }
}

class BackupEntry(val path: String, val open: () -> InputStream)

object BackupArchive {
    const val MANIFEST = "manifest.json"

    /** Streams [entries] into a zip on [out], then the manifest last; returns it. [out] is left open. */
    fun write(out: OutputStream, entries: List<BackupEntry>, template: BackupManifest): BackupManifest {
        val zip = ZipOutputStream(out)
        val files = ArrayList<BackupFile>(entries.size)
        val buffer = ByteArray(64 * 1024)
        for (entry in entries) {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            zip.putNextEntry(ZipEntry(entry.path))
            entry.open().use { input ->
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    zip.write(buffer, 0, n)
                    digest.update(buffer, 0, n)
                    size += n
                }
            }
            zip.closeEntry()
            files += BackupFile(entry.path, size, digest.hex())
        }
        val manifest = template.copy(files = files)
        zip.putNextEntry(ZipEntry(MANIFEST))
        zip.write(manifest.toJson().toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        zip.finish()
        zip.flush()
        return manifest
    }

    /** The manifest alone, unverified; null if the file isn't a readable backup. */
    fun readManifest(file: File): BackupManifest? = try {
        ZipFile(file).use { zip ->
            zip.getEntry(MANIFEST)?.let { BackupManifest.parse(zip.getInputStream(it).readBytes().toString(Charsets.UTF_8)) }
        }
    } catch (_: Exception) {
        null
    }

    /** Checks the manifest, schema and every checksum; throws [BackupException] with a user-facing reason. */
    fun verify(file: File, currentSchema: Int): BackupManifest {
        val zip = try {
            ZipFile(file)
        } catch (_: ZipException) {
            throw BackupException("That file isn't a Marginalia backup")
        }
        zip.use {
            val manifestEntry = zip.getEntry(MANIFEST) ?: throw BackupException("That file isn't a Marginalia backup")
            val manifest = try {
                BackupManifest.parse(zip.getInputStream(manifestEntry).readBytes().toString(Charsets.UTF_8))
            } catch (_: Exception) {
                throw BackupException("The backup's manifest is unreadable")
            }
            if (manifest.format != BACKUP_FORMAT) throw BackupException("This backup uses an unsupported format")
            if (manifest.schemaVersion > currentSchema) {
                throw BackupException("This backup was made by a newer version of Marginalia. Update the app, then try again")
            }
            if (manifest.files.none { it.path == DB_ENTRY }) throw BackupException("The backup has no library database")
            for (f in manifest.files) {
                if (f.path.startsWith("/") || f.path.split('/').any { it == ".." }) {
                    throw BackupException("The backup contains an unsafe path")
                }
                val entry = zip.getEntry(f.path) ?: throw BackupException("The backup is missing ${f.path}")
                val (size, sha) = zip.getInputStream(entry).use(::digestOf)
                if (size != f.size || sha != f.sha256) throw BackupException("The backup is damaged: ${f.path} failed its checksum")
            }
            return manifest
        }
    }

    /** Unpacks every manifest file under [dest]; call after [verify]. */
    fun extract(file: File, manifest: BackupManifest, dest: File) {
        val root = dest.canonicalFile
        ZipFile(file).use { zip ->
            for (f in manifest.files) {
                val target = File(root, f.path).canonicalFile
                if (!target.path.startsWith(root.path + File.separator)) throw BackupException("The backup contains an unsafe path")
                target.parentFile?.mkdirs()
                zip.getInputStream(zip.getEntry(f.path)).use { input -> target.outputStream().use { input.copyTo(it) } }
            }
        }
    }

    private fun digestOf(input: InputStream): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var size = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
            size += n
        }
        return size to digest.hex()
    }

    private fun MessageDigest.hex() = digest().joinToString("") { "%02x".format(it) }
}
