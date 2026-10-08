package com.serendeep.marginalia.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

const val REMINDER_ENABLED_KEY = "reminder_enabled"
const val REMINDER_TIME_KEY = "reminder_time_min"
const val DEFAULT_REMINDER_MIN = 20 * 60
const val STUDY_CHANNEL = "study"
const val ACTION_REMIND = "com.serendeep.marginalia.REMIND"
private const val ALARM_REQUEST = 7001

object ReminderScheduler {

    /** Schedules (or cancels) the daily alarm from the stored preferences. Blocking: call off the main thread. */
    fun arm(context: Context) {
        val prefs = context.getSharedPreferences(com.serendeep.marginalia.shell.PREFS, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(REMINDER_ENABLED_KEY, true)
        val minute = prefs.getInt(REMINDER_TIME_KEY, DEFAULT_REMINDER_MIN)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(context)
        if (!enabled) {
            alarms.cancel(pending)
            return
        }
        // Inexact on purpose: a study nudge doesn't need the exact-alarm permission.
        alarms.setInexactRepeating(AlarmManager.RTC, nextTrigger(minute), AlarmManager.INTERVAL_DAY, pending)
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        ALARM_REQUEST,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun nextTrigger(minuteOfDay: Int, now: Long = System.currentTimeMillis()): Long {
        val zone = ZoneId.systemDefault()
        val at = LocalTime.of(minuteOfDay.coerceIn(0, 1439) / 60, minuteOfDay.coerceIn(0, 1439) % 60)
        var trigger = LocalDate.now(zone).atTime(at).atZone(zone).toInstant().toEpochMilli()
        if (trigger <= now) trigger = LocalDate.now(zone).plusDays(1).atTime(at).atZone(zone).toInstant().toEpochMilli()
        return trigger
    }
}

fun ensureStudyChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(STUDY_CHANNEL) == null) {
        manager.createNotificationChannel(
            NotificationChannel(STUDY_CHANNEL, "Study reminders", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }
}

/** Notification body, or null when there is nothing worth interrupting for. */
fun reminderText(dueCards: Int, minutesToday: Int, goalMin: Int, estimateMin: Int): String? = when {
    dueCards > 0 -> "$dueCards cards due · $estimateMin min"
    minutesToday < goalMin -> "Daily goal: $minutesToday of $goalMin min"
    else -> null
}
