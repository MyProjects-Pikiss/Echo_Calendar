package com.echo.echocalendar

import android.content.Context
import androidx.room.Room
import com.echo.echocalendar.data.local.AppDatabase
import com.echo.echocalendar.domain.usecase.DeleteEventUseCase
import com.echo.echocalendar.domain.usecase.GetEventsByDateUseCase
import com.echo.echocalendar.domain.usecase.GetEventsByMonthUseCase
import com.echo.echocalendar.domain.usecase.SaveEventUseCase
import com.echo.echocalendar.domain.usecase.SearchEventsUseCase

class AppContainer(context: Context) {
    val database: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "echo_calendar.db"
    ).fallbackToDestructiveMigration().build()

    val deleteEventUseCase: DeleteEventUseCase = DeleteEventUseCase(database)
    val saveEventUseCase: SaveEventUseCase = SaveEventUseCase(database)
    val searchEventsUseCase: SearchEventsUseCase = SearchEventsUseCase(database)
    val getEventsByDateUseCase: GetEventsByDateUseCase = GetEventsByDateUseCase(database)
    val getEventsByMonthUseCase: GetEventsByMonthUseCase = GetEventsByMonthUseCase(database)
}
