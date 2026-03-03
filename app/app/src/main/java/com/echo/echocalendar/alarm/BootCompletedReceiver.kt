package com.echo.echocalendar.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.echo.echocalendar.AppContainer
import kotlinx.coroutines.runBlocking

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val pendingResult = goAsync()
        Thread {
            try {
                val appContext = context.applicationContext
                val container = AppContainer(appContext)
                container.eventAlarmScheduler.ensureNotificationChannels()
                runBlocking {
                    val alarms = container.database.eventAlarmDao().getEnabledAlarms()
                    val now = System.currentTimeMillis()
                    alarms.forEach { alarm ->
                        if (!alarm.isEnabled || alarm.triggerAt <= now) return@forEach
                        val event = container.database.eventDao().getById(alarm.eventId) ?: return@forEach
                        container.eventAlarmScheduler.schedule(
                            eventId = alarm.eventId,
                            triggerAt = alarm.triggerAt,
                            summary = event.summary
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
