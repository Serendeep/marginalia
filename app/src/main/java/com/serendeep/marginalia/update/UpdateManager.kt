package com.serendeep.marginalia.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
import com.serendeep.marginalia.R
import com.serendeep.marginalia.shell.PREFS
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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

const val UPDATE_CHANNEL = "updates"
private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
private const val PERIODIC_HOURS = 12L
private const val WORK_DOWNLOAD = "update-download"
private const val WORK_PERIODIC = "update-check"
private const val NOTIFICATION_CONFIRM = 7011

private const val KEY_CHECK_AUTO = "update_check_auto"
private const val KEY_WIFI_ONLY = "update_wifi_only"
private const val KEY_AUTO_DOWNLOAD = "update_auto_download"
private const val KEY_AUTO_INSTALL = "update_auto_install"
private const val KEY_LAST_CHECK = "update_last_check"
private const val KEY_ETAG = "update_feed_etag"
private const val KEY_LATEST = "update_latest_json"
private const val KEY_FAILED_CODE = "update_failed_code"

data class UpdateSettings(
    val checkAutomatically: Boolean = true,
    val wifiOnly: Boolean = true,
    val downloadAutomatically: Boolean = true,
    val installWhenIdle: Boolean = true,
)

enum class UpdatePhase { UP_TO_DATE, CHECKING, AVAILABLE, DOWNLOADING, READY, INSTALLING, FAILED }

data class UpdateStatus(
    val phase: UpdatePhase = UpdatePhase.UP_TO_DATE,
    val info: UpdateInfo? = null,
    val progress: Int = 0,
    val checkedAt: Long = 0,
    val error: String? = null,
)

/** The download was fetched but must not be installed; retrying will not help. */
class UpdateRejected(message: String) : Exception(message)

@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    val remote: RemoteConfigStore,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val checkLock = Mutex()
    private var autoInstallJob: Job? = null
    @Volatile private var inForeground = false

    val installedVersionCode: Long by lazy { context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode }
    val installedVersionName: String by lazy { BuildConfig.VERSION_NAME }

    private val _settings = MutableStateFlow(
        UpdateSettings(
            prefs.getBoolean(KEY_CHECK_AUTO, true),
            prefs.getBoolean(KEY_WIFI_ONLY, true),
            prefs.getBoolean(KEY_AUTO_DOWNLOAD, true),
            prefs.getBoolean(KEY_AUTO_INSTALL, true),
        ),
    )
    val settings: StateFlow<UpdateSettings> = _settings.asStateFlow()

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
                maybeAutoInstall()
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
            .putBoolean(KEY_AUTO_INSTALL, next.installWhenIdle)
            .apply()
        scope.launch { schedulePeriodic() }
        maybeAutoInstall()
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

    /** Checks the feed, then fetches and installs per the settings. Called by the app start check and the periodic worker. */
    suspend fun checkAndDownload() {
        check()
        val st = _status.value
        if (st.phase == UpdatePhase.AVAILABLE && _settings.value.downloadAutomatically) enqueueDownload(userInitiated = false)
        if (st.phase == UpdatePhase.READY) maybeAutoInstall()
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

    /** Fetches [info] into place; the file only gets its final name once every check passed. */
    fun download(info: UpdateInfo, cancelled: () -> Boolean, onPercent: (Int) -> Unit) {
        val final = apkFile(info)
        if (final.exists()) return
        val part = File(final.path + ".part")
        val etag = File(final.path + ".part.etag")
        var last = -1
        try {
            _status.update { it.copy(phase = UpdatePhase.DOWNLOADING, info = info, progress = 0, error = null) }
            val sha = UpdateHttp.download(info.apkUrl, part, etag, { done, total ->
                val pct = if (total > 0) (done * 100 / total).toInt() else 0
                if (pct != last) {
                    last = pct
                    _status.update { it.copy(progress = pct) }
                    onPercent(pct)
                }
            }, cancelled)
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

    fun fail(message: String) = refresh(error = message)

    /** Installs the ready update at once; the user asked for it. */
    fun installNow() {
        scope.launch { install() }
    }

    private fun install() {
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
        try {
            UpdateInstaller.install(context, file)
        } catch (e: Exception) {
            onInstallFailed("Install failed: ${e.message ?: e.javaClass.simpleName}", aborted = false)
        }
    }

    /** Installs when the app is in the background and the pen has been still, if the setting allows. */
    fun maybeAutoInstall() {
        if (!BuildConfig.UPDATES_ENABLED || !_settings.value.installWhenIdle) return
        val st = _status.value
        if (st.phase != UpdatePhase.READY || inForeground) return
        if (st.info?.versionCode == prefs.getLong(KEY_FAILED_CODE, 0)) return
        if (!context.packageManager.canRequestPackageInstalls()) return
        autoInstallJob?.cancel()
        val wait = PenActivity.msUntilIdle()
        autoInstallJob = scope.launch {
            if (wait > 0) delay(wait)
            if (!inForeground && _status.value.phase == UpdatePhase.READY && PenActivity.msUntilIdle() == 0L) install()
        }
    }

    fun onInstallFailed(message: String, aborted: Boolean) {
        latest()?.let { prefs.edit().putLong(KEY_FAILED_CODE, it.versionCode).apply() }
        refresh(error = if (aborted) null else message)
    }

    /** The system wants the user to confirm: show its screen now, or leave a notification when we are in the background. */
    fun confirmInstall(confirm: Intent) {
        if (inForeground) {
            context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        ensureUpdateChannel(context)
        val tap = PendingIntent.getActivity(
            context,
            NOTIFICATION_CONFIRM,
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_marginalia)
            .setColor(0xFF8B7CF6.toInt())
            .setContentTitle("Marginalia update ready")
            .setContentText("Tap to finish updating")
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_CONFIRM, notification)
        } catch (_: SecurityException) {
            // Notifications are off; the Updates row in the app still offers the install.
        }
    }

    companion object {
        private val BUSY = setOf(UpdatePhase.DOWNLOADING, UpdatePhase.INSTALLING)
    }
}

fun ensureUpdateChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(UPDATE_CHANNEL) == null) {
        manager.createNotificationChannel(
            NotificationChannel(UPDATE_CHANNEL, "App updates", NotificationManager.IMPORTANCE_LOW),
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
