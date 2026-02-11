package com.echo.echocalendar.ui.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.echocalendar.data.local.EventEntity
import com.echo.echocalendar.domain.usecase.SearchEventsUseCase
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.launch

class SearchViewModel(
    private val searchEventsUseCase: SearchEventsUseCase
) : ViewModel() {
    var query by mutableStateOf("")
        private set
    var results by mutableStateOf<List<EventEntity>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var dateFromFilter by mutableStateOf<String?>(null)
        private set
    var dateToFilter by mutableStateOf<String?>(null)
        private set
    var categoryFilters by mutableStateOf<List<String>>(emptyList())
        private set
    var aiFiltersApplied by mutableStateOf(false)
        private set

    fun onQueryChange(newQuery: String) {
        query = newQuery
    }

    fun onSearchSubmit() {
        val currentQuery = query.trim()
        if (currentQuery.isEmpty()) {
            results = emptyList()
            error = null
            return
        }
        val validationError = validateFilters(dateFromFilter, dateToFilter)
        if (validationError != null) {
            error = validationError
            return
        }
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                results = searchEventsUseCase(
                    query = currentQuery,
                    dateFrom = dateFromFilter,
                    dateTo = dateToFilter,
                    categoryIds = categoryFilters
                )
            } catch (e: Exception) {
                error = e.message ?: "Unknown error"
                results = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    fun applyAiSearchSuggestion(suggestion: AiSearchSuggestion) {
        query = suggestion.query
        dateFromFilter = suggestion.dateFrom?.trim()?.ifBlank { null }
        dateToFilter = suggestion.dateTo?.trim()?.ifBlank { null }
        categoryFilters = suggestion.categoryIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
        aiFiltersApplied = dateFromFilter != null || dateToFilter != null || categoryFilters.isNotEmpty()
        onSearchSubmit()
    }

    fun onDateFromFilterChange(value: String) {
        dateFromFilter = value.trim().ifBlank { null }
        aiFiltersApplied = false
    }

    fun clearDateFromFilter() {
        dateFromFilter = null
        aiFiltersApplied = false
    }

    fun onDateToFilterChange(value: String) {
        dateToFilter = value.trim().ifBlank { null }
        aiFiltersApplied = false
    }

    fun clearDateToFilter() {
        dateToFilter = null
        aiFiltersApplied = false
    }

    fun onCategoryFiltersChange(rawValue: String) {
        categoryFilters = rawValue
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        aiFiltersApplied = false
    }

    fun toggleCategoryFilter(categoryId: String) {
        val id = categoryId.trim()
        if (id.isBlank()) return
        categoryFilters = if (id in categoryFilters) {
            categoryFilters - id
        } else {
            categoryFilters + id
        }
        aiFiltersApplied = false
    }

    fun removeCategoryFilter(categoryId: String) {
        val id = categoryId.trim()
        if (id.isBlank()) return
        categoryFilters = categoryFilters - id
        aiFiltersApplied = false
    }

    fun clearFilters() {
        dateFromFilter = null
        dateToFilter = null
        categoryFilters = emptyList()
        aiFiltersApplied = false
        if (query.isNotBlank()) {
            onSearchSubmit()
        }
    }

    fun resetSearch() {
        query = ""
        results = emptyList()
        isLoading = false
        error = null
        dateFromFilter = null
        dateToFilter = null
        categoryFilters = emptyList()
        aiFiltersApplied = false
    }

    private fun validateFilters(dateFrom: String?, dateTo: String?): String? {
        val parsedFrom = when {
            dateFrom.isNullOrBlank() -> null
            else -> parseDate(dateFrom) ?: return "시작일 형식은 yyyy-MM-dd 입니다."
        }
        val parsedTo = when {
            dateTo.isNullOrBlank() -> null
            else -> parseDate(dateTo) ?: return "종료일 형식은 yyyy-MM-dd 입니다."
        }
        if (parsedFrom != null && parsedTo != null && parsedFrom.isAfter(parsedTo)) {
            return "시작일은 종료일보다 늦을 수 없어요."
        }
        return null
    }

    private fun parseDate(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        return try {
            LocalDate.parse(value.trim())
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
