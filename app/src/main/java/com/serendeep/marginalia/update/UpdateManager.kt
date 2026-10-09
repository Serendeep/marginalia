package com.serendeep.marginalia.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.serendeep.marginalia.BuildConfig
import com.serendeep.marginalia.MainActivity
import com.serendeep.marginalia.backup.SafetySnapshot
import com.serendeep.marginalia.shell.PREFS
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

const val UPDATE_CHANNEL = "update_download"
private const val LEGACY_CHANNEL = "updates"
private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
private const val PERIODIC_HOURS = 12L
private const val WORK_DOWNLOAD = "update-download"
private const val WORK_PERIODIC = "update-check"

private const val KEY_CHECK_AUTO = "update_check_auto"
private const val KEY_WIFI_ONLY = "update_wifi_only"
private const val KEY_AUTO_DOWNLOAD = "update_auto_download"
private const val KEY_LAST_CHECK = "update_last_check"
private const val KEY_ETAG = "update_feed_etag"
private const val KEY_LATEST = "update_latest_json"
private const val KEY_LAST_SEEN = "update_last_seen_code"
private const val KEY_WHATS_NEW_UNTIL = "update_whats_new_until"
private const val WHATS_NEW_MS = 24 * 60 * 60 * 1000L
private const val RELEASES_URL = "https://github.com/Serendeep/marginalia/releases/tag/v"

data class UpdateSettings(
    val checkAutomatically: Boolean = true,
    val wifiOnly: Boolean = true,
    val downloadAutomatically: Boolean = true,
)

enum class UpdatePhase { UP_TO_DATE, CHECKING, AVAILABLE, DOWNLOADING, READY, INSTALLING, FAILED }

data class UpdateStatus(
    val phase: UpdatePhase = UpdatePhase.UP_TO_DATE,
    val info: UpdateInfo? = null,
    val progress: Int = 0,
    val checkedAt: Long = 0,
    val error: String? = null,
    val fullFallback: Boolean = false,
)

/** Whether this launch is the first of a version newer than the one last seen; a fresh install has nothing to announce. */
internal fun justUpdated(lastSeen: Long, installed: Long): Boolean = lastSeen in 1 until installed

/** Release notes for [versionName]: the feed's link when the feed describes that version, else its GitHub release page. */
internal fun whatsNewUrl(feed: UpdateInfo?, installed: Long, versionName: String): String =
    feed?.takeIf { it.versionCode == installed }?.notesUrl ?: (RELEASES_URL + versionName)

/** The download was fetched but must not be installed; retrying will not help. */
class UpdateRejected(message: String) : Exception(message)

@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    val remote: RemoteConfigStore,
    private val snapshots: SafetySnapshot,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val checkLock = Mutex()
    @Volatile private var inForeground = false
    @Volatile private var patchFailedFor = 0L

    val installedVersionCode: Long by lazy { context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode }
    val installedVersionName: String by lazy { BuildConfig.VERSION_NAME }

    private val _settings = MutableStateFlow(
        UpdateSettings(
            prefs.getBoolean(KEY_CHECK_AUTO, true),
            prefs.getBoolean(KEY_WIFI_ONLY, true),
            prefs.getBoolean(KEY_AUTO_DOWNLOAD, true),
        ),
    )
    val settings: StateFlow<UpdateSettings> = _settings.asStateFlow()

    private val _whatsNewUntil = MutableStateFlow(0L)

    /** Until when the sidebar offers the notes of the version that was just installed; 0 when it does not. */
    val whatsNewUntil: StateFlow<Long> = _whatsNewUntil.asStateFlow()

    init {
        val lastSeen = prefs.getLong(KEY_LAST_SEEN, 0)
        val now = System.currentTimeMillis()
        if (justUpdated(lastSeen, installedVersionCode)) prefs.edit().putLong(KEY_WHATS_NEW_UNTIL, now + WHATS_NEW_MS).apply()
        if (lastSeen != installedVersionCode) prefs.edit().putLong(KEY_LAST_SEEN, installedVersionCode).apply()
        _whatsNewUntil.value = prefs.getLong(KEY_WHATS_NEW_UNTIL, 0).takeIf { it > now } ?: 0
    }

    private val _status = MutableStateFlow(UpdateStatus(checkedAt = prefs.getLong(KEY_LAST_CHECK, 0)))
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    /** Call once from [android.app.Application.onCreate]; does nothing in builds without updates. */
    fun start() {
        if (!BuildConfig.UPDATES_ENABLED) return
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                inForeground = true
                checkIfDue()
            }

            override fun onStop(owner: LifecycleOwner) {
                inForeground = false
            }
        })
        scope.launch {
            refresh()
            schedulePeriodic()
        }
    }

    fun updateSettings(transform: (UpdateSettings) -> UpdateSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit()
            .putBoolean(KEY_CHECK_AUTO, next.checkAutomatically)
            .putBoolean(KEY_WIFI_ONLY, next.wifiOnly)
            .putBoolean(KEY_AUTO_DOWNLOAD, next.downloadAutomatically)
            .apply()
        scope.launch { schedulePeriodic() }
    }

    private fun schedulePeriodic() {
        val work = WorkManager.getInstance(context)
        val s = _settings.value
        if (!s.checkAutomatically) {
            work.cancelUniqueWork(WORK_PERIODIC)
            return
        }
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(PERIODIC_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints(s.wifiOnly))
            .build()
        work.enqueueUniquePeriodicWork(WORK_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun constraints(wifiOnly: Boolean) = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    fun checkIfDue() {
        if (!_settings.value.checkAutomatically) return
        if (System.currentTimeMillis() - prefs.getLong(KEY_LAST_CHECK, 0) < CHECK_INTERVAL_MS) return
        scope.launch { checkAndDownload() }
    }

    fun checkNow() {
        scope.launch { checkAndDownload() }
    }

    /** Checks the feed, then fetches per the settings. Called by the app start check and the periodic worker. */
    suspend fun checkAndDownload() {
        check()
        val st = _status.value
        if (st.phase == UpdatePhase.AVAILABLE && _settings.value.downloadAutomatically) enqueueDownload(userInitiated = false)
    }

    /** Fetches the feed and remote config and records what they say. */
    suspend fun check() = checkLock.withLock {
        if (_status.value.phase in BUSY) return@withLock
        _status.update { it.copy(phase = UpdatePhase.CHECKING, error = null) }
        try {
            try {
                (UpdateHttp.fetchText(BuildConfig.REMOTE_CONFIG_URL) as? Fetched.Body)?.let { remote.update(it.text) }
            } catch (e: IOException) {
                // Remote config is optional; the cached or default one stays in force.
            }
            when (val r = UpdateHttp.fetchText(BuildConfig.UPDATE_FEED_URL, prefs.getString(KEY_ETAG, null))) {
                Fetched.NotModified -> Unit
                is Fetched.Body -> {
                    if (UpdateInfo.parse(r.text) == null) throw IOException("The update feed is unreadable")
                    prefs.edit().putString(KEY_LATEST, r.text).putString(KEY_ETAG, r.etag).apply()
                }
            }
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            refresh(error = "Couldn't check for updates: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun latest(): UpdateInfo? =
        prefs.getString(KEY_LATEST, null)?.let(UpdateInfo::parse)?.takeIf { isNewer(it.versionCode, installedVersionCode) }

    /** The newest update that still needs fetching, or null. */
    fun pendingInfo(): UpdateInfo? = latest()?.takeIf { !apkFile(it).exists() }

    private fun updateDir(): File = File(context.getExternalFilesDir(null), "update").apply { mkdirs() }

    internal fun apkFile(info: UpdateInfo) = File(updateDir(), "${info.versionCode}.apk")

    /** Rebuilds the visible status from what is on disk and in the feed, and drops files of other versions. */
    private fun refresh(error: String? = null) {
        val info = latest()
        val keep = info?.let { "${it.versionCode}.apk" }
        File(context.getExternalFilesDir(null), "update").listFiles()?.forEach { f ->
            if (keep == null || !f.name.startsWith(keep)) f.delete()
        }
        val phase = when {
            error != null -> UpdatePhase.FAILED
            info == null -> UpdatePhase.UP_TO_DATE
            apkFile(info).exists() -> UpdatePhase.READY
            else -> UpdatePhase.AVAILABLE
        }
        _status.value = UpdateStatus(phase, info, 0, prefs.getLong(KEY_LAST_CHECK, 0), error)
    }

    fun enqueueDownload(userInitiated: Boolean) {
        val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
            .setConstraints(constraints(_settings.value.wifiOnly && !userInitiated))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_DOWNLOAD,
            if (userInitiated) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /** Fetches [info] into place, as a small patch when the feed has one for this install; the file only gets its final name once every check passed. */
    fun download(info: UpdateInfo, cancelled: () -> Boolean, onPercent: (Int) -> Unit) {
        val final = apkFile(info)
        if (final.exists()) return
        val part = File(final.path + ".part")
        val etag = File(final.path + ".part.etag")
        var last = -1
        val progress = { done: Long, total: Long ->
            val pct = if (total > 0) (done * 100 / total).toInt() else 0
            if (pct != last) {
                last = pct
                _status.update { it.copy(progress = pct) }
                onPercent(pct)
            }
        }
        try {
            _status.update { it.copy(phase = UpdatePhase.DOWNLOADING, info = info, progress = 0, error = null, fullFallback = false) }
            val patch = info.patchFor(installedVersionCode)?.takeIf { !part.exists() && patchFailedFor != info.versionCode }
            if (patch != null) {
                if (applyPatch(info, patch, part, final, progress, cancelled)) return
                patchFailedFor = info.versionCode
                last = -1
                _status.update { it.copy(progress = 0, fullFallback = true) }
            }
            val sha = UpdateHttp.download(info.apkUrl, part, etag, progress, cancelled)
            val problem = if (!sha.equals(info.sha256, ignoreCase = true)) {
                "The download is corrupt (checksum mismatch)"
            } else {
                UpdateVerifier.verify(context, part, info.sha256)
            }
            if (problem != null) {
                part.delete()
                etag.delete()
                throw UpdateRejected(problem)
            }
            if (!part.renameTo(final)) throw IOException("Could not store the update")
            etag.delete()
        } finally {
            refresh(error = null)
        }
    }

    /** Downloads [patch], rebuilds the APK into [part] and stores it as [final]; false means nothing usable was left and the full APK should be fetched. */
    private fun applyPatch(
        info: UpdateInfo,
        patch: UpdatePatch,
        part: File,
        final: File,
        progress: (Long, Long) -> Unit,
        cancelled: () -> Boolean,
    ): Boolean {
        val file = File(final.path + ".patch")
        val etag = File(final.path + ".patch.etag")
        try {
            val sha = UpdateHttp.download(patch.url, file, etag, progress, cancelled)
            if (!sha.equals(patch.sha256, ignoreCase = true)) throw IOException("The patch is corrupt (checksum mismatch)")
            UpdatePatcher.apply(File(context.applicationInfo.sourceDir), file, part, info.sha256)
            if (UpdateVerifier.verify(context, part, info.sha256) != null) throw IOException("The patched update failed verification")
            if (!part.renameTo(final)) throw IOException("Could not store the update")
            return true
        } catch (e: Throwable) {
            // A stopped download is not a bad patch; anything else (including a missing native library) falls back to the full APK.
            if (cancelled()) throw e
            part.delete()
            return false
        } finally {
            file.delete()
            etag.delete()
        }
    }

    fun whatsNewUrl(): String =
        whatsNewUrl(prefs.getString(KEY_LATEST, null)?.let(UpdateInfo::parse), installedVersionCode, installedVersionName)

    fun dismissWhatsNew() {
        prefs.edit().remove(KEY_WHATS_NEW_UNTIL).apply()
        _whatsNewUntil.value = 0
    }

    fun fail(message: String) = refresh(error = message)

    /** Installs the ready update at once; the user asked for it. */
    fun installNow() {
        scope.launch { install() }
    }

    private suspend fun install() {
        val info = latest() ?: return
        val file = apkFile(info)
        if (!file.exists()) return refresh()
        if (!context.packageManager.canRequestPackageInstalls()) {
            return refresh(error = "Allow Marginalia to install apps in Settings first")
        }
        val problem = try {
            UpdateVerifier.verify(context, file, info.sha256)
        } catch (e: Exception) {
            "Could not read the downloaded update"
        }
        if (problem != null) {
            file.delete()
            return refresh(error = problem)
        }
        _status.update { it.copy(phase = UpdatePhase.INSTALLING, error = null) }
        snapshots.take("before update to ${info.versionName}")
        try {
            UpdateInstaller.install(context, file)
        } catch (e: Exception) {
            onInstallFailed("Install failed: ${e.message ?: e.javaClass.simpleName}", aborted = false)
        }
    }

    fun onInstallFailed(message: String, aborted: Boolean) {
        refresh(error = if (aborted) null else message)
    }

    /** The system wants the user to confirm: show its screen if the tap that started this is still on screen, otherwise stay ready. */
    fun confirmInstall(confirm: Intent) {
        if (inForeground) context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) else refresh()
    }

    companion object {
        private val BUSY = setOf(UpdatePhase.DOWNLOADING, UpdatePhase.INSTALLING)
    }
}

fun ensureUpdateChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(UPDATE_CHANNEL) == null) {
        manager.deleteNotificationChannel(LEGACY_CHANNEL)
        manager.createNotificationChannel(
            NotificationChannel(UPDATE_CHANNEL, "Update downloads", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
    }
}

internal fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
    context,
    0,
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UpdateEntryPoint {
    fun updates(): UpdateManager
}

internal fun Context.updates(): UpdateManager =
    EntryPointAccessors.fromApplication(applicationContext, UpdateEntryPoint::class.java).updates()
