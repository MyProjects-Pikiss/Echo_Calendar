package com.echo.echocalendar.domain.usecase

import com.echo.echocalendar.data.local.AppDatabase
import com.echo.echocalendar.data.local.EventEntity

class SearchEventsUseCase(
    private val database: AppDatabase
) {
    suspend operator fun invoke(query: String): List<EventEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val ftsQuery = trimmed
            .split(Regex("\\s+"))
            .joinToString(" ") { "$it*" }
        return database.eventDao().fullTextSearch(ftsQuery)
    }
}
