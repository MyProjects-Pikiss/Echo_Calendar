package com.echo.echocalendar.domain.usecase

import android.database.sqlite.SQLiteException
import com.echo.echocalendar.data.local.AppDatabase
import com.echo.echocalendar.data.local.EventEntity
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.Locale

class SearchEventsUseCase(
    private val database: AppDatabase
) {
    companion object {
        const val SORT_DESC = "desc"
        const val SORT_ASC = "asc"
        const val STRATEGY_ALL_EVENTS = "all_events"
    }

    suspend operator fun invoke(
        query: String,
        dateFrom: String? = null,
        dateTo: String? = null,
        sortOrder: String? = null,
        strategy: String? = null,
        categoryIds: List<String> = emptyList(),
        labelNames: List<String> = emptyList(),
        zoneId: ZoneId = ZoneId.of("Asia/Seoul")
    ): List<EventEntity> {
        val trimmed = query.trim()
        val strategyNormalized = strategy?.trim()?.lowercase(Locale.ROOT)
        val baseResults = if (strategyNormalized == STRATEGY_ALL_EVENTS || trimmed.isEmpty() || trimmed == "*") {
            database.eventDao().getAll()
        } else {
            val ftsQuery = buildSafeFtsQuery(trimmed)
            if (ftsQuery.isBlank()) return emptyList()
            try {
                database.eventDao().fullTextSearch(ftsQuery)
            } catch (_: SQLiteException) {
                return emptyList()
            } catch (_: IllegalArgumentException) {
                return emptyList()
            }
        }
        val parsedFrom = dateFrom.toLocalDateOrNull()
        val parsedTo = dateTo.toLocalDateOrNull()
        val categoryIdSet = categoryIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        val existingLabelNames = database.labelDao().getAll().map { it.name }
        val resolvedLabelNames = harmonizeSearchLabels(
            rawLabels = labelNames,
            existingLabelNames = existingLabelNames
        )
        val normalizedLabelNames = resolvedLabelNames
            .map { it.lowercase(Locale.ROOT) }
            .distinct()
        val labelMatchedEventIds = if (normalizedLabelNames.isNotEmpty()) {
            database.eventLabelDao()
                .getEventIdsContainingAllLabels(normalizedLabelNames, normalizedLabelNames.size)
                .toSet()
        } else {
            emptySet()
        }

        val filtered = baseResults.filter { event ->
            if (categoryIdSet.isNotEmpty() && event.categoryId !in categoryIdSet) {
                return@filter false
            }
            if (normalizedLabelNames.isNotEmpty() && event.id !in labelMatchedEventIds) {
                return@filter false
            }
            if (parsedFrom == null && parsedTo == null) {
                return@filter true
            }
            val eventDate = java.time.Instant.ofEpochMilli(event.occurredAt)
                .atZone(zoneId)
                .toLocalDate()
            if (parsedFrom != null && eventDate.isBefore(parsedFrom)) {
                return@filter false
            }
            if (parsedTo != null && eventDate.isAfter(parsedTo)) {
                return@filter false
            }
            true
        }
        return when (sortOrder?.trim()?.lowercase(Locale.ROOT)) {
            SORT_ASC -> filtered.sortedWith(compareBy<EventEntity> { it.occurredAt }.thenBy { it.updatedAt })
            else -> filtered.sortedWith(compareByDescending<EventEntity> { it.occurredAt }.thenByDescending { it.updatedAt })
        }
    }
}

private fun buildSafeFtsQuery(query: String): String {
    val tokens = Regex("""[\p{L}\p{N}_]+""")
        .findAll(query)
        .map { it.value }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()
    return when {
        tokens.isEmpty() -> ""
        tokens.size == 1 -> "${tokens.first()}*"
        else -> tokens.joinToString(" OR ") { "$it*" }
    }
}

private fun String?.toLocalDateOrNull(): LocalDate? {
    if (this.isNullOrBlank()) return null
    return try {
        LocalDate.parse(trim())
    } catch (_: DateTimeParseException) {
        null
    }
}
