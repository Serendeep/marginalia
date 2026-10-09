package com.serendeep.marginalia.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class SafetyNetTest {
    private val utc = ZoneId.of("UTC")
    private fun at(local: String, zone: ZoneId = utc): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()

    @Test fun delayTodayWhenTimeIsAhead() {
        assertEquals(Duration.ofHours(2), nextRunDelay(at("2026-03-10T01:00:00"), 180, utc))
    }

    @Test fun delayTomorrowWhenTimeHasPassedOrIsNow() {
        assertEquals(Duration.ofHours(23), nextRunDelay(at("2026-03-10T04:00:00"), 180, utc))
        assertEquals(Duration.ofHours(24), nextRunDelay(at("2026-03-10T03:00:00"), 180, utc))
    }

    @Test fun delaySpansDstSpringForward() {
        val ny = ZoneId.of("America/New_York")
        // 01:00 local on the spring-forward night: 03:00 is one real hour away because 02:00 is skipped.
        assertEquals(Duration.ofHours(1), nextRunDelay(at("2026-03-08T01:00:00", ny), 180, ny))
        assertEquals(Duration.ofHours(23), nextRunDelay(at("2026-03-08T04:00:00", ny), 180, ny))
    }

    @Test fun delayInDstGapResolvesForward() {
        val ny = ZoneId.of("America/New_York")
        // 02:30 does not exist on 2026-03-08; it resolves to 03:30 local, 30 real minutes after 03:00.
        assertEquals(Duration.ofMinutes(60), nextRunDelay(at("2026-03-08T01:30:00", ny), 150, ny))
    }

    @Test fun nameRoundTrips() {
        val stamp = "20261010-030000"
        for (n in listOf(
            BackupName(BackupKind.NIGHTLY, null, stamp),
            BackupName(BackupKind.FOLDER, null, stamp),
            BackupName(BackupKind.BEFORE_UPDATE, "1.3.0-nightly.5", stamp),
            BackupName(BackupKind.BEFORE_NIGHTLY, null, stamp),
            BackupName(BackupKind.BEFORE_RESTORE, null, stamp),
        )) assertEquals(n, BackupName.parse(n.fileName))
        assertNull(BackupName.parse("notes.zip"))
        assertNull(BackupName.parse("nightly-20261010-030000.zip.part"))
    }

    @Test fun labelsMapToNames() {
        val stamp = "20261010-030000"
        assertEquals("Before update to 1.3.0", BackupName.forLabel("before update to 1.3.0", stamp)?.title)
        assertEquals(BackupKind.BEFORE_NIGHTLY, BackupName.forLabel("before Nightly", stamp)?.kind)
        assertEquals(BackupKind.BEFORE_RESTORE, BackupName.forLabel("before restore", stamp)?.kind)
        assertNull(BackupName.forLabel("something else", stamp))
        assertEquals("1.3_0", BackupName.forLabel("before update to 1.3/0", stamp)?.version)
    }

    @Test fun retentionIsPerKind() {
        val names = (1..5).map { "before-update-to-1.$it-2026101$it-000000.zip" } +
            (1..4).map { "before-nightly-2026101$it-000000.zip" } +
            (1..9).map { "nightly-2026101$it-030000.zip" } + "foreign.zip"
        assertEquals(
            listOf("before-update-to-1.2-20261012-000000.zip", "before-update-to-1.1-20261011-000000.zip"),
            staleNames(names, BackupKind.BEFORE_UPDATE, 3),
        )
        assertEquals(2, staleNames(names, BackupKind.BEFORE_NIGHTLY, 2).size)
        assertEquals(2, staleNames(names, BackupKind.NIGHTLY, 7).size)
        assertTrue(staleNames(names, BackupKind.BEFORE_RESTORE, 2).isEmpty())
    }

    @Test fun freshInstallDefaultsToNightlyInAppStorage() {
        val c = autoBackupConfigFrom(emptyMap<String, Any>())
        assertEquals(BackupSchedule.NIGHTLY, c.schedule)
        assertEquals(BackupDestination.APP, c.destination)
        assertEquals(180, c.minuteOfDay)
        assertTrue(c.onlyCharging)
        assertEquals(7, c.keep)
        assertTrue(c.active)
    }

    @Test fun legacyFolderBackupsStayInTheFolder() {
        val c = autoBackupConfigFrom(
            mapOf("backup_auto_enabled" to true, "backup_auto_tree" to "content://t", "backup_auto_frequency" to "WEEKLY", "backup_auto_keep" to 5),
        )
        assertEquals(BackupDestination.FOLDER, c.destination)
        assertEquals(BackupSchedule.WEEKLY, c.schedule)
        assertEquals("content://t", c.treeUri)
        assertEquals(5, c.keep)
    }

    @Test fun legacyDisabledFallsBackToDefaults() {
        val c = autoBackupConfigFrom(mapOf("backup_auto_enabled" to false, "backup_auto_tree" to "content://t"))
        assertEquals(BackupDestination.APP, c.destination)
        assertEquals(BackupSchedule.NIGHTLY, c.schedule)
    }

    @Test fun migratedPrefsAreReadBack() {
        val c = autoBackupConfigFrom(
            mapOf("backup_auto_schedule" to "OFF", "backup_auto_destination" to "FOLDER", "backup_auto_minute" to 90, "backup_auto_charging" to false),
        )
        assertEquals(BackupSchedule.OFF, c.schedule)
        assertEquals(90, c.minuteOfDay)
        assertFalse(c.onlyCharging)
        assertFalse(c.active)
    }

    private fun item(stamp: String, kind: BackupKind, app: Boolean = true) =
        BackupItem(BackupName(kind, if (kind == BackupKind.BEFORE_UPDATE) "1.3.0" else null, stamp), 10, "/x/$stamp", app)

    @Test fun listingMergesNewestFirstAndSurvivesFailingSource() {
        val a = BackupSource { listOf(item("20261008-030000", BackupKind.NIGHTLY), item("20261010-030000", BackupKind.NIGHTLY)) }
        val b = BackupSource { listOf(item("20261009-120000", BackupKind.BEFORE_UPDATE)) }
        val c = BackupSource { listOf(item("20261009-130000", BackupKind.FOLDER, app = false)) }
        val broken = BackupSource { error("folder gone") }
        val out = listBackups(listOf(a, broken, b, c))
        assertEquals(
            listOf("20261010-030000", "20261009-130000", "20261009-120000", "20261008-030000"),
            out.map { it.name.stamp },
        )
    }

    @Test fun statusLine() {
        val now = at("2026-10-10T12:00:00")
        assertEquals("Next backup at 03:00", backupStatusLine(AutoBackupConfig(), now, utc))
        val ran = AutoBackupConfig(lastRunAt = at("2026-10-10T03:00:00").toEpochMilli(), kept = 7)
        assertEquals("Last backup today at 03:00 · 7 kept", backupStatusLine(ran, now, utc))
        assertEquals("Last backup yesterday at 03:00 · 7 kept", backupStatusLine(ran, at("2026-10-11T12:00:00"), utc))
        assertEquals("Last backup failed: boom", backupStatusLine(ran.copy(lastError = "boom"), now, utc))
    }
}
