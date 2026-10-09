package com.serendeep.marginalia.update

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.serendeep.marginalia.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

private const val NOTIFICATION_PROGRESS = 7010
private const val MAX_ATTEMPTS = 4

/** Checks the feed on a schedule and starts the download when the settings allow. */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        applicationContext.updates().checkAndDownload()
        return Result.success()
    }
}

class UpdateDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val updates = applicationContext.updates()
        val info = updates.pendingInfo() ?: return Result.success()
        try {
            setForeground(foreground(0))
        } catch (_: Exception) {
            // Starting a foreground service can be refused from the background; the download still runs.
        }
        return try {
            withContext(Dispatchers.IO) {
                updates.download(info, cancelled = { isStopped }) { pct ->
                    notify(pct)
                }
            }
            updates.maybeAutoInstall()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: UpdateRejected) {
            updates.fail(e.message.orEmpty())
            Result.failure()
        } catch (e: IOException) {
            if (runAttemptCount + 1 < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                updates.fail("Couldn't download the update: ${e.message ?: "network error"}")
                Result.failure()
            }
        }
    }

    // Same id as the foreground notification, so this just moves its progress bar.
    private fun notify(percent: Int) {
        try {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_PROGRESS, foreground(percent).notification)
        } catch (_: SecurityException) {
            // Notifications are off; the download carries on without a progress bar.
        }
    }

    private fun foreground(percent: Int): ForegroundInfo {
        ensureUpdateChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, UPDATE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_marginalia)
            .setColor(0xFF8B7CF6.toInt())
            .setContentTitle("Downloading Marginalia update")
            .setContentText("$percent%")
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(applicationContext))
            .build()
        return ForegroundInfo(NOTIFICATION_PROGRESS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}
