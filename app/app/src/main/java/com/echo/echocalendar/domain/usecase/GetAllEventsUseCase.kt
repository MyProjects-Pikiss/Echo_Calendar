package com.echo.echocalendar.domain.usecase

import com.echo.echocalendar.data.local.AppDatabase
import com.echo.echocalendar.data.local.EventEntity

class GetAllEventsUseCase(
    private val database: AppDatabase
) {
    suspend operator fun invoke(): List<EventEntity> {
        return database.eventDao().getAll()
            .sortedWith(compareByDescending<EventEntity> { it.occurredAt }.thenByDescending { it.updatedAt })
    }
}
