package com.serendeep.marginalia.backup

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Taken just before something risky; implementations never throw. */
fun interface SafetySnapshot {
    suspend fun take(label: String)
}

enum class BackupKind(val keep: Int?) {
    NIGHTLY(null),
    FOLDER(null),
    BEFORE_UPDATE(3),
    BEFORE_NIGHTLY(2),
    BEFORE_RESTORE(2),
}

/** Parsed from a backup's file name, which carries the kind, an optional version and the local timestamp. */
data class BackupName(val kind: BackupKind, val version: String?, val stamp: String) {
    val fileName: String
        get() = when (kind) {
            BackupKind.NIGHTLY -> "nightly-$stamp.zip"
            BackupKind.FOLDER -> "marginalia-backup-$stamp.zip"
            BackupKind.BEFORE_UPDATE -> "before-update-to-${version.orEmpty()}-$stamp.zip"
            BackupKind.BEFORE_NIGHTLY -> "before-nightly-$stamp.zip"
            BackupKind.BEFORE_RESTORE -> "before-restore-$stamp.zip"
        }

    val title: String
        get() = when (kind) {
            BackupKind.NIGHTLY -> "Automatic"
            BackupKind.FOLDER -> "Folder"
            BackupKind.BEFORE_UPDATE -> "Before update to $version"
            BackupKind.BEFORE_NIGHTLY -> "Before Nightly"
            BackupKind.BEFORE_RESTORE -> "Before restore"
        }

    fun epochMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDateTime.parse(stamp, STAMP).atZone(zone).toInstant().toEpochMilli()

    companion object {
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        private val pattern = Regex("""^(nightly|marginalia-backup|before-update-to-(.+)|before-nightly|before-restore)-(\d{8}-\d{6})\.zip$""")

        fun stampOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
            STAMP.format(Instant.ofEpochMilli(millis).atZone(zone))

        fun parse(fileName: String): BackupName? {
            val m = pattern.matchEntire(fileName) ?: return null
            val head = m.groupValues[1]
            val kind = when {
                head == "nightly" -> BackupKind.NIGHTLY
                head == "marginalia-backup" -> BackupKind.FOLDER
                head == "before-nightly" -> BackupKind.BEFORE_NIGHTLY
                head == "before-restore" -> BackupKind.BEFORE_RESTORE
                else -> BackupKind.BEFORE_UPDATE
            }
            return BackupName(kind, m.groupValues[2].takeIf { kind == BackupKind.BEFORE_UPDATE }, m.groupValues[3])
        }

        /** Maps a snapshot label such as "before update to 1.3.0" to a name; null if the label is unknown. */
        fun forLabel(label: String, stamp: String): BackupName? {
            val l = label.trim()
            val update = "before update to "
            return when {
                l.startsWith(update, ignoreCase = true) -> {
                    val v = l.substring(update.length).replace(Regex("[^A-Za-z0-9._+-]"), "_").ifEmpty { return null }
                    BackupName(BackupKind.BEFORE_UPDATE, v, stamp)
                }
                l.equals("before Nightly", ignoreCase = true) -> BackupName(BackupKind.BEFORE_NIGHTLY, null, stamp)
                l.equals("before restore", ignoreCase = true) -> BackupName(BackupKind.BEFORE_RESTORE, null, stamp)
                else -> null
            }
        }
    }
}

/** File names that fall outside the newest [keep] of [kind]; other kinds and foreign files are left alone. */
fun staleNames(fileNames: List<String>, kind: BackupKind, keep: Int): List<String> =
    fileNames.mapNotNull { n -> BackupName.parse(n)?.takeIf { it.kind == kind }?.let { n to it.stamp } }
        .sortedByDescending { it.second }
        .drop(keep)
        .map { it.first }

class BackupItem(
    val name: BackupName,
    val sizeBytes: Long,
    /** Absolute path for app storage, content uri for a folder. */
    val location: String,
    val inAppStorage: Boolean,
    val timeMillis: Long = name.epochMillis(),
)

fun interface BackupSource {
    fun list(): List<BackupItem>
}

/** Newest first; a source that fails contributes nothing. */
fun listBackups(sources: List<BackupSource>): List<BackupItem> =
    sources.flatMap { runCatching { it.list() }.getOrDefault(emptyList()) }.sortedByDescending { it.timeMillis }

enum class BackupSchedule(val label: String) { NIGHTLY("Every night"), WEEKLY("Weekly"), OFF("Off") }

enum class BackupDestination(val label: String) { APP("App storage"), FOLDER("Folder") }

/** Delay until the next local [minuteOfDay] strictly after [now]; java.time resolves DST gaps and overlaps. */
fun nextRunDelay(now: Instant, minuteOfDay: Int, zone: ZoneId): Duration {
    val at = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
    val local = now.atZone(zone)
    var next = ZonedDateTime.of(local.toLocalDate(), at, zone)
    if (!next.toInstant().isAfter(now)) next = ZonedDateTime.of(local.toLocalDate().plusDays(1), at, zone)
    return Duration.between(now, next.toInstant())
}


/** "Last backup today at 03:00 · 7 kept", or when none has run yet, "Next backup at 03:00". */
fun backupStatusLine(cfg: AutoBackupConfig, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val time = "%02d:%02d".format(cfg.minuteOfDay / 60, cfg.minuteOfDay % 60)
    if (cfg.lastError != null) return "Last backup failed: ${cfg.lastError}"
    if (cfg.lastRunAt == 0L) return "Next backup at $time"
    val last = Instant.ofEpochMilli(cfg.lastRunAt).atZone(zone)
    val days = java.time.temporal.ChronoUnit.DAYS.between(last.toLocalDate(), now.atZone(zone).toLocalDate())
    val day = when (days) {
        0L -> "today"
        1L -> "yesterday"
        else -> DateTimeFormatter.ofPattern("MMM d", java.util.Locale.getDefault()).format(last)
    }
    return "Last backup $day at ${DateTimeFormatter.ofPattern("HH:mm").format(last)} · ${cfg.kept} kept"
}
