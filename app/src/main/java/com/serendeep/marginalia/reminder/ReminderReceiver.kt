package com.serendeep.marginalia.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.serendeep.marginalia.MainActivity
import com.serendeep.marginalia.R
import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.shell.DEFAULT_GOAL_MIN
import com.serendeep.marginalia.shell.GOAL_KEY
import com.serendeep.marginalia.shell.PREFS
import com.serendeep.marginalia.study.dueQueue
import com.serendeep.marginalia.study.estimateMinutes
import com.serendeep.marginalia.study.minutesByDay
import com.serendeep.marginalia.study.startOfDay
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

private const val NOTIFICATION_ID = 7002

/** Fires the daily nudge, and re-arms the alarm after a reboot. */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: MarginaliaRepository

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                    ReminderScheduler.arm(app)
                } else {
                    notifyIfWorthIt(app)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun notifyIfWorthIt(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(REMINDER_ENABLED_KEY, true)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val now = System.currentTimeMillis()
        val due = dueQueue(repository.allCards(), now, repository.newIntroducedSince(startOfDay(now))).size
        val zone = ZoneId.systemDefault()
        val minutes = minutesByDay(repository.observeSessions().first(), zone)[LocalDate.now(zone)] ?: 0
        val goal = prefs.getInt(GOAL_KEY, DEFAULT_GOAL_MIN)
        val text = reminderText(due, minutes, goal, estimateMinutes(due)) ?: return

        ensureStudyChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_REVIEW, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, STUDY_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_marginalia)
            .setColor(0xFF8B7CF6.toInt())
            .setContentTitle("Marginalia")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
