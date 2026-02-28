package com.echo.echocalendar.domain.usecase

import com.echo.echocalendar.data.local.AppDatabase

class GetLabelsForEventUseCase(
    private val database: AppDatabase
) {
    suspend operator fun invoke(eventId: String): List<String> {
        return database.eventLabelDao()
            .getLabelsForEvent(eventId)
            .map { it.name }
    }
}
