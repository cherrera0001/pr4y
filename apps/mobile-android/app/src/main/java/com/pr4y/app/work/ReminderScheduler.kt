package com.pr4y.app.work

import android.content.Context
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    private const val WORK_NAME = "pr4y_daily_reminder"

    /** Programa el recordatorio con hora y días configurables (sincronizado con API). */
    fun scheduleFromPreferences(context: Context, time: String, daysOfWeek: List<Int>, enabled: Boolean) {
        createNotificationChannelIfNeeded(context)
        if (!enabled || daysOfWeek.isEmpty()) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            return
        }
        val (hour, minute) = parseTime(time)
        val initialDelay = delayUntilNext(context, hour, minute, daysOfWeek)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Mantiene compatibilidad: programa a las 9:00. */
    fun scheduleDaily(context: Context) {
        scheduleFromPreferences(context, "09:00", listOf(1, 2, 3, 4, 5, 6), true)
    }

    private fun parseTime(time: String): Pair<Int, Int> {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return hour to minute
    }

    private fun delayUntilNext(context: Context, hour: Int, minute: Int, daysOfWeek: List<Int>): Long {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        var next = cal.timeInMillis
        if (next <= now) cal.add(Calendar.DAY_OF_MONTH, 1).let { next = cal.timeInMillis }
        var dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
        if (dayOfWeek == -1) dayOfWeek = 0
        var advances = 0
        while (advances < 8 && !daysOfWeek.contains(dayOfWeek)) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
            next = cal.timeInMillis
            advances++
            dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
            if (dayOfWeek == -1) dayOfWeek = 0
        }
        return (next - now).coerceAtLeast(60_000L)
    }

    private fun createNotificationChannelIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                ReminderWorker.CHANNEL_ID,
                "Recordatorio",
                android.app.NotificationManager.IMPORTANCE_DEFAULT,
            )
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
                .createNotificationChannel(channel)
        }
    }
}
