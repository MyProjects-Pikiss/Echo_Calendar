package com.echo.echocalendar.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings

class EventAlarmScheduler(private val context: Context) {
    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun ensureNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val soundUri: Uri = Settings.System.DEFAULT_NOTIFICATION_URI
        val soundAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()

        val soundChannel = NotificationChannel(
            EVENT_ALARM_CHANNEL_SOUND,
            "이벤트 알림(사운드)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "캘린더 이벤트 알림 채널 - 사운드"
            enableVibration(true)
            setSound(soundUri, soundAttrs)
        }
        val vibrateChannel = NotificationChannel(
            EVENT_ALARM_CHANNEL_VIBRATE,
            "이벤트 알림(진동)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "캘린더 이벤트 알림 채널 - 진동"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 250, 250)
            setSound(null, null)
        }
        val silentChannel = NotificationChannel(
            EVENT_ALARM_CHANNEL_SILENT,
            "이벤트 알림(무음)",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "캘린더 이벤트 알림 채널 - 무음"
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(soundChannel)
        manager.createNotificationChannel(vibrateChannel)
        manager.createNotificationChannel(silentChannel)
    }

    fun schedule(eventId: String, triggerAt: Long, summary: String) {
        if (triggerAt <= System.currentTimeMillis()) return
        val pendingIntent = buildPendingIntent(eventId, summary)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            return
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    fun cancel(eventId: String) {
        val pendingIntent = buildPendingIntent(eventId, summary = "")
        alarmManager.cancel(pendingIntent)
    }

    private fun buildPendingIntent(eventId: String, summary: String): PendingIntent {
        val intent = Intent(context, EventAlarmReceiver::class.java).apply {
            action = EVENT_ALARM_ACTION
            putExtra(EXTRA_EVENT_ID, eventId)
            putExtra(EXTRA_EVENT_SUMMARY, summary)
        }
        return PendingIntent.getBroadcast(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val EVENT_ALARM_CHANNEL_SOUND = "event_alarm_channel_sound"
        const val EVENT_ALARM_CHANNEL_VIBRATE = "event_alarm_channel_vibrate"
        const val EVENT_ALARM_CHANNEL_SILENT = "event_alarm_channel_silent"
        const val EVENT_ALARM_ACTION = "com.echo.echocalendar.EVENT_ALARM"
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_EVENT_SUMMARY = "extra_event_summary"
    }
}
