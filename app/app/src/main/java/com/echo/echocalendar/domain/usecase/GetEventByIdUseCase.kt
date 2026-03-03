package com.echo.echocalendar.domain.usecase

import com.echo.echocalendar.data.local.AppDatabase
import com.echo.echocalendar.data.local.EventEntity

class GetEventByIdUseCase(
    private val database: AppDatabase
) {
    suspend operator fun invoke(eventId: String): EventEntity? {
        return database.eventDao().getById(eventId)
    }
}
