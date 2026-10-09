package com.serendeep.marginalia.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class BackupArchiveTest {
    @get:Rule val tmp = TemporaryFolder()

    private val counts = BackupCounts(2, 3, 4, 5, 6)

    private fun template(schema: Int = 9) = BackupManifest(BACKUP_FORMAT, "1.2.0", 7, schema, 1_700_000_000_000, counts, emptyList())

    private fun entry(path: String, text: String) = BackupEntry(path) { ByteArrayInputStream(text.toByteArray()) }

    private fun exportTo(file: File, schema: Int = 9) = file.outputStream().use {
        BackupArchive.write(
            it,
            listOf(entry(DB_ENTRY, "sqlite-bytes"), entry("files/pdfs/a.pdf", "pdf"), entry(PREFS_ENTRY, "{}")),
            template(schema),
        )
    }

    private fun rewrite(src: File, dst: File, mutate: (String, ByteArray) -> ByteArray) {
        ZipFile(src).use { zip ->
            ZipOutputStream(dst.outputStream()).use { out ->
                zip.entries().asSequence().forEach { e ->
                    out.putNextEntry(ZipEntry(e.name))
                    out.write(mutate(e.name, zip.getInputStream(e).readBytes()))
                    out.closeEntry()
                }
            }
        }
    }

    @Test fun manifestRoundTrips() {
        val m = template().copy(files = listOf(BackupFile("db/marginalia.db", 12, "ab")))
        assertEquals(m, BackupManifest.parse(m.toJson()))
    }

    @Test fun exportThenVerifyRoundTrips() {
        val zip = tmp.newFile("b.zip")
        val written = exportTo(zip)
        val read = BackupArchive.verify(zip, currentSchema = 9)
        assertEquals(written, read)
        assertEquals(counts, read.counts)
        assertEquals(listOf(DB_ENTRY, "files/pdfs/a.pdf", PREFS_ENTRY), read.files.map { it.path })
        ZipFile(zip).use { assertEquals(BackupArchive.MANIFEST, it.entries().asSequence().last().name) }

        val dest = tmp.newFolder("out")
        BackupArchive.extract(zip, read, dest)
        assertEquals("sqlite-bytes", File(dest, DB_ENTRY).readText())
        assertEquals("pdf", File(dest, "files/pdfs/a.pdf").readText())
    }

    @Test fun olderSchemaIsAccepted() {
        val zip = tmp.newFile("old.zip")
        exportTo(zip, schema = 5)
        assertEquals(5, BackupArchive.verify(zip, currentSchema = 9).schemaVersion)
    }

    @Test fun checksumMismatchIsRefused() {
        val good = tmp.newFile("good.zip")
        exportTo(good)
        val bad = tmp.newFile("bad.zip")
        rewrite(good, bad) { name, bytes -> if (name == "files/pdfs/a.pdf") "PDF".toByteArray() else bytes }
        val e = failing { BackupArchive.verify(bad, 9) }
        assertTrue(e.message, e.message!!.contains("checksum"))
    }

    @Test fun newerSchemaIsRefused() {
        val zip = tmp.newFile("new.zip")
        exportTo(zip, schema = 10)
        val e = failing { BackupArchive.verify(zip, 9) }
        assertTrue(e.message, e.message!!.contains("newer version"))
    }

    @Test fun missingManifestAndNonZipAreRefused() {
        val noManifest = tmp.newFile("nm.zip")
        ZipOutputStream(noManifest.outputStream()).use { it.putNextEntry(ZipEntry("x")); it.closeEntry() }
        failing { BackupArchive.verify(noManifest, 9) }
        val junk = tmp.newFile("junk.zip").apply { writeText("not a zip") }
        failing { BackupArchive.verify(junk, 9) }
    }

    @Test fun pathTraversalIsRefused() {
        val m = template().copy(files = listOf(BackupFile(DB_ENTRY, 0, "x"), BackupFile("../evil", 0, "x")))
        val zip = tmp.newFile("evil.zip")
        ZipOutputStream(zip.outputStream()).use {
            it.putNextEntry(ZipEntry(BackupArchive.MANIFEST))
            it.write(m.toJson().toByteArray())
            it.closeEntry()
        }
        failing { BackupArchive.verify(zip, 9) }
    }

    @Test fun prefsFilteringExcludesSecretsAndDeviceState() {
        val all = mapOf(
            "daily_goal_min" to 45,
            "handwriting_search" to false,
            "pen_width" to 2.5f,
            "recent_searches" to "a\nb",
            "reminder_time_min" to 1200L,
            "ai_api_key" to "SECRET",
            "ai_provider" to "COMPATIBLE",
            "update_latest_json" to "{}",
            "remote_config_json" to "{}",
            "backup_auto_tree" to "content://x",
        )
        val kept = BackupPrefs.filter(all)
        assertEquals(setOf("daily_goal_min", "handwriting_search", "pen_width", "recent_searches", "reminder_time_min", "ai_provider"), kept.keys)

        val encoded = BackupPrefs.encode(all)
        assertFalse(encoded.toString(Charsets.UTF_8).contains("SECRET"))
        assertEquals(kept, BackupPrefs.decode(encoded))
    }

    @Test fun decodeDropsExcludedKeysEvenIfPresent() {
        val json = """{"values":[{"k":"ai_api_key","t":"s","v":"x"},{"k":"daily_goal_min","t":"i","v":30}]}"""
        assertEquals(mapOf<String, Any>("daily_goal_min" to 30), BackupPrefs.decode(json.toByteArray()))
    }

    private fun failing(block: () -> Unit): BackupException {
        try {
            block()
        } catch (e: BackupException) {
            return e
        }
        fail("expected BackupException")
        throw AssertionError()
    }
}
