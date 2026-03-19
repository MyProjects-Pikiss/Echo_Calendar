package com.echo.echocalendar.alarm

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.echo.echocalendar.MainActivity
import com.echo.echocalendar.R
import com.echo.echocalendar.SecureSettings
import com.echo.echocalendar.SettingsKeys

class EventAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val summary = intent.getStringExtra(EventAlarmScheduler.EXTRA_EVENT_SUMMARY).orEmpty()
        val eventId = intent.getStringExtra(EventAlarmScheduler.EXTRA_EVENT_ID).orEmpty()
        if (eventId.isBlank()) return

        val mode = resolveAlarmMode(context)
        val channelId = when (mode) {
            SettingsKeys.ALARM_ALERT_MODE_VIBRATE -> EventAlarmScheduler.EVENT_ALARM_CHANNEL_VIBRATE
            SettingsKeys.ALARM_ALERT_MODE_SILENT -> EventAlarmScheduler.EVENT_ALARM_CHANNEL_SILENT
            else -> EventAlarmScheduler.EVENT_ALARM_CHANNEL_SOUND
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EventAlarmScheduler.EXTRA_EVENT_ID, eventId)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (summary.isBlank()) "일정 알림" else summary
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("설정한 일정 시간이 되었습니다.")
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            when (mode) {
                SettingsKeys.ALARM_ALERT_MODE_VIBRATE -> {
                    builder.setPriority(NotificationCompat.PRIORITY_HIGH)
                    builder.setVibrate(longArrayOf(0, 250, 250, 250))
                    builder.setDefaults(0)
                }
                SettingsKeys.ALARM_ALERT_MODE_SILENT -> {
                    builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    builder.setSilent(true)
                }
                else -> {
                    builder.setPriority(NotificationCompat.PRIORITY_HIGH)
                    builder.setDefaults(Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE)
                }
            }
        } else {
            builder.setPriority(
                if (mode == SettingsKeys.ALARM_ALERT_MODE_SILENT) {
                    NotificationCompat.PRIORITY_DEFAULT
                } else {
                    NotificationCompat.PRIORITY_HIGH
                }
            )
        }

        val notification = builder.build()

        NotificationManagerCompat.from(context).notify(eventId.hashCode(), notification)
    }

    private fun resolveAlarmMode(context: Context): String {
        val prefs = SecureSettings.getPreferences(context)
        return prefs.getString(
            SettingsKeys.KEY_ALARM_ALERT_MODE,
            SettingsKeys.ALARM_ALERT_MODE_SOUND
        ) ?: SettingsKeys.ALARM_ALERT_MODE_SOUND
    }
}
