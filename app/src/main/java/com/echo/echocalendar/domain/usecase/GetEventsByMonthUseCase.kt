package com.echo.echocalendar.domain.usecase

import com.echo.echocalendar.data.local.AppDatabase
import com.echo.echocalendar.data.local.EventEntity
import java.time.YearMonth
import java.time.ZoneId

class GetEventsByMonthUseCase(
    private val database: AppDatabase
) {
    suspend operator fun invoke(
        month: YearMonth,
        zoneId: ZoneId = ZoneId.of("Asia/Seoul")
    ): List<EventEntity> {
        val start = month.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        return database.eventDao().getByOccurredAtRange(start, end)
    }
}
