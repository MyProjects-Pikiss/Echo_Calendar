package com.echo.echocalendar

import android.content.Context
import androidx.room.Room
import com.echo.echocalendar.alarm.EventAlarmScheduler
import com.echo.echocalendar.data.local.AppDatabase
import com.echo.echocalendar.data.local.MIGRATION_3_4
import com.echo.echocalendar.data.local.MIGRATION_4_5
import com.echo.echocalendar.data.local.MIGRATION_5_6
import com.echo.echocalendar.data.local.MIGRATION_6_7
import com.echo.echocalendar.data.local.MIGRATION_7_8
import com.echo.echocalendar.data.local.MIGRATION_8_9
import com.echo.echocalendar.domain.usecase.DeleteEventUseCase
import com.echo.echocalendar.domain.usecase.GetEventsByDateUseCase
import com.echo.echocalendar.domain.usecase.GetEventByIdUseCase
import com.echo.echocalendar.domain.usecase.GetEventsByMonthUseCase
import com.echo.echocalendar.domain.usecase.GetLabelsForEventUseCase
import com.echo.echocalendar.domain.usecase.SaveEventUseCase
import com.echo.echocalendar.domain.usecase.SearchEventsUseCase
import com.echo.echocalendar.domain.usecase.UpdateEventUseCase
import com.echo.echocalendar.ui.demo.AiApiGateway
import com.echo.echocalendar.ui.demo.AiAssistantService
import com.echo.echocalendar.ui.demo.HttpAiApiGateway

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val settingsPrefs = appContext.getSharedPreferences(
        SettingsKeys.SETTINGS_PREFS_NAME,
        Context.MODE_PRIVATE
    )

    val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "echo_calendar.db"
    ).addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
        .build()

    val eventAlarmScheduler: EventAlarmScheduler = EventAlarmScheduler(appContext)

    val deleteEventUseCase: DeleteEventUseCase = DeleteEventUseCase(database)
    val getLabelsForEventUseCase: GetLabelsForEventUseCase = GetLabelsForEventUseCase(database)
    val saveEventUseCase: SaveEventUseCase = SaveEventUseCase(database)
    val updateEventUseCase: UpdateEventUseCase = UpdateEventUseCase(database)
    val searchEventsUseCase: SearchEventsUseCase = SearchEventsUseCase(database)
    val getEventsByDateUseCase: GetEventsByDateUseCase = GetEventsByDateUseCase(database)
    val getEventByIdUseCase: GetEventByIdUseCase = GetEventByIdUseCase(database)
    val getEventsByMonthUseCase: GetEventsByMonthUseCase = GetEventsByMonthUseCase(database)

    val aiApiGateway: AiApiGateway = HttpAiApiGateway(
        usageAccessTokenProvider = {
            settingsPrefs.getString(SettingsKeys.KEY_USAGE_ACCESS_TOKEN, "").orEmpty()
        }
    )
    val aiAssistantService: AiAssistantService = AiAssistantService(aiApiGateway)
}
