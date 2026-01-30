package com.echo.echocalendar.domain.usecase

import androidx.room.withTransaction
import com.echo.echocalendar.data.local.AppDatabase

class DeleteEventUseCase(
    private val database: AppDatabase
) {
    suspend operator fun invoke(eventId: String) {
        database.withTransaction {
            database.eventLabelDao().deleteByEventId(eventId)
            database.eventDao().deleteById(eventId)
        }
    }
}
