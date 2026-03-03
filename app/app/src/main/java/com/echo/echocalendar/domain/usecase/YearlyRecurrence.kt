package com.echo.echocalendar.domain.usecase

import com.echo.echocalendar.data.local.EventEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

internal fun materializeYearlyRecurringForDate(
    recurringEvents: List<EventEntity>,
    targetDate: LocalDate,
    zoneId: ZoneId
): List<EventEntity> {
    return recurringEvents.mapNotNull { source ->
        val sourceDateTime = Instant.ofEpochMilli(source.occurredAt).atZone(zoneId).toLocalDateTime()
        val recurringDate = yearlyRecurringDateForYear(sourceDateTime.toLocalDate(), targetDate.year) ?: return@mapNotNull null
        if (recurringDate != targetDate) return@mapNotNull null
        if (sourceDateTime.toLocalDate() == targetDate) return@mapNotNull null
        val recurringDateTime = recurringDate.atTime(sourceDateTime.toLocalTime())
        source.copy(occurredAt = recurringDateTime.atZone(zoneId).toInstant().toEpochMilli())
    }
}

internal fun materializeYearlyRecurringForMonth(
    recurringEvents: List<EventEntity>,
    month: YearMonth,
    zoneId: ZoneId
): List<EventEntity> {
    return recurringEvents.mapNotNull { source ->
        val sourceDateTime = Instant.ofEpochMilli(source.occurredAt).atZone(zoneId).toLocalDateTime()
        val recurringDate = yearlyRecurringDateForYear(sourceDateTime.toLocalDate(), month.year) ?: return@mapNotNull null
        if (YearMonth.from(recurringDate) != month) return@mapNotNull null
        if (YearMonth.from(sourceDateTime.toLocalDate()) == month) return@mapNotNull null
        val recurringDateTime = recurringDate.atTime(sourceDateTime.toLocalTime())
        source.copy(occurredAt = recurringDateTime.atZone(zoneId).toInstant().toEpochMilli())
    }
}

private fun yearlyRecurringDateForYear(baseDate: LocalDate, year: Int): LocalDate? {
    return when {
        baseDate.monthValue == 2 && baseDate.dayOfMonth == 29 && !java.time.Year.isLeap(year.toLong()) ->
            LocalDate.of(year, 2, 28)
        else -> runCatching { LocalDate.of(year, baseDate.monthValue, baseDate.dayOfMonth) }.getOrNull()
    }
}
