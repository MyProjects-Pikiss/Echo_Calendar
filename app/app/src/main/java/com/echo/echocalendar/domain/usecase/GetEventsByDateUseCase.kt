package com.echo.echocalendar.domain.usecase

import com.echo.echocalendar.data.local.AppDatabase
import com.echo.echocalendar.data.local.EventEntity
import java.time.LocalDate
import java.time.ZoneId

class GetEventsByDateUseCase(
    private val database: AppDatabase
) {
    suspend operator fun invoke(
        date: LocalDate,
        zoneId: ZoneId = ZoneId.of("Asia/Seoul")
    ): List<EventEntity> {
        val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        val directEvents = database.eventDao().getByOccurredAtRange(start, end)
        val recurringSources = database.eventDao().getYearlyRecurringEvents()
        val recurringEvents = materializeYearlyRecurringForDate(
            recurringEvents = recurringSources,
            targetDate = date,
            zoneId = zoneId
        ).filter { recurring -> directEvents.none { it.id == recurring.id } }
        return (directEvents + recurringEvents)
            .sortedWith(compareByDescending<EventEntity> { it.occurredAt }.thenByDescending { it.updatedAt })
    }
}
