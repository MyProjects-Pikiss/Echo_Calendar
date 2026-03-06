package com.echo.echocalendar.ui.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.echocalendar.data.local.EventEntity
import com.echo.echocalendar.domain.usecase.MAX_LABELS_PER_EVENT
import com.echo.echocalendar.domain.usecase.SearchEventsUseCase.Companion.SORT_ASC
import com.echo.echocalendar.domain.usecase.SearchEventsUseCase.Companion.SORT_DESC
import com.echo.echocalendar.domain.usecase.SearchEventsUseCase
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    var hasSearched by mutableStateOf(false)
        private set
    var dateFromFilter by mutableStateOf<String?>(null)
        private set
    var dateToFilter by mutableStateOf<String?>(null)
        private set
    var categoryFilters by mutableStateOf<List<String>>(emptyList())
        private set
    var labelFilters by mutableStateOf<List<String>>(emptyList())
        private set
    var sortOrderFilter by mutableStateOf(SORT_DESC)
        private set
    var strategyFilter by mutableStateOf(AiSearchStrategy.Combined)
        private set
    var aiFiltersApplied by mutableStateOf(false)
        private set

    private var searchJob: Job? = null
    private var pendingRefreshJob: Job? = null
    private var lastExecutedKey: SearchRequestKey? = null

    fun onQueryChange(newQuery: String) {
        query = newQuery
        if (strategyFilter == AiSearchStrategy.AllEvents && newQuery.trim() != "*") {
            strategyFilter = AiSearchStrategy.Combined
            aiFiltersApplied = false
        }
    }

    fun onSearchSubmit() {
        val currentQuery = query.trim()
        if (currentQuery.isEmpty() && !hasActiveFilters()) {
            searchJob?.cancel()
            pendingRefreshJob?.cancel()
            lastExecutedKey = null
            results = emptyList()
            error = "검색어 또는 필터를 입력해 주세요."
            isLoading = false
            hasSearched = true
            return
        }

        val validationError = validateFilters(dateFromFilter, dateToFilter)
        if (validationError != null) {
            error = validationError
            return
        }

        val requestKey = SearchRequestKey(
            query = currentQuery,
            dateFrom = dateFromFilter,
            dateTo = dateToFilter,
            sortOrder = sortOrderFilter,
            strategy = strategyFilter,
            categoryIds = categoryFilters.sorted(),
            labelNames = labelFilters
                .sorted()
        )

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            isLoading = true
            hasSearched = true
            error = null
            try {
                results = searchEventsUseCase(
                    query = requestKey.query,
                    dateFrom = requestKey.dateFrom,
                    dateTo = requestKey.dateTo,
                    sortOrder = requestKey.sortOrder,
                    strategy = requestKey.strategy.value,
                    categoryIds = requestKey.categoryIds,
                    labelNames = requestKey.labelNames
                )
                lastExecutedKey = requestKey
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
        strategyFilter = suggestion.strategy
        dateFromFilter = suggestion.dateFrom?.trim()?.ifBlank { null }
        dateToFilter = suggestion.dateTo?.trim()?.ifBlank { null }
        sortOrderFilter = normalizeSortOrder(suggestion.sortOrder)
        categoryFilters = suggestion.categoryIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
        labelFilters = suggestion.labelNames
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_LABELS_PER_EVENT)
        aiFiltersApplied = hasActiveFilters()
        onSearchSubmit()
    }

    fun onDateFromFilterChange(value: String) {
        dateFromFilter = value.trim().ifBlank { null }
        aiFiltersApplied = false
        refreshAfterFilterMutation()
    }

    fun clearDateFromFilter() {
        dateFromFilter = null
        aiFiltersApplied = false
        refreshAfterFilterMutation()
    }

    fun onDateToFilterChange(value: String) {
        dateToFilter = value.trim().ifBlank { null }
        aiFiltersApplied = false
        refreshAfterFilterMutation()
    }

    fun clearDateToFilter() {
        dateToFilter = null
        aiFiltersApplied = false
        refreshAfterFilterMutation()
    }

    fun onCategoryFiltersChange(rawValue: String) {
        categoryFilters = rawValue
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        aiFiltersApplied = false
        refreshAfterFilterMutation()
    }

    fun onLabelFiltersChange(rawValue: String) {
        labelFilters = rawValue
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_LABELS_PER_EVENT)
        aiFiltersApplied = false
        refreshAfterFilterMutation()
    }

    fun onSortOrderChange(order: String) {
        val normalized = normalizeSortOrder(order)
        if (normalized == sortOrderFilter) return
        sortOrderFilter = normalized
        aiFiltersApplied = false
        refreshAfterFilterMutation()
    }

    fun clearAllEventsStrategy() {
        if (strategyFilter != AiSearchStrategy.AllEvents) return
        strategyFilter = AiSearchStrategy.Combined
        if (query.trim() == "*") {
            query = ""
        }
        aiFiltersApplied = false
        refreshAfterFilterMutation()
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
        refreshAfterFilterMutation()
    }

    fun removeCategoryFilter(categoryId: String) {
        val id = categoryId.trim()
        if (id.isBlank()) return
        categoryFilters = categoryFilters - id
        aiFiltersApplied = false
        refreshAfterFilterMutation()
    }

    fun removeLabelFilter(labelName: String) {
        val normalized = labelName.trim()
        if (normalized.isBlank()) return
        labelFilters = labelFilters.filterNot { it.equals(normalized, ignoreCase = true) }
        aiFiltersApplied = false
        refreshAfterFilterMutation()
    }

    fun clearFilters() {
        dateFromFilter = null
        dateToFilter = null
        categoryFilters = emptyList()
        labelFilters = emptyList()
        sortOrderFilter = SORT_DESC
        strategyFilter = AiSearchStrategy.Combined
        aiFiltersApplied = false
        refreshAfterFilterMutation()
    }

    fun resetSearch() {
        searchJob?.cancel()
        pendingRefreshJob?.cancel()
        query = ""
        results = emptyList()
        isLoading = false
        hasSearched = false
        error = null
        dateFromFilter = null
        dateToFilter = null
        categoryFilters = emptyList()
        labelFilters = emptyList()
        sortOrderFilter = SORT_DESC
        strategyFilter = AiSearchStrategy.Combined
        aiFiltersApplied = false
        lastExecutedKey = null
    }

    private fun refreshAfterFilterMutation() {
        if (query.isBlank() && !hasActiveFilters()) return
        pendingRefreshJob?.cancel()
        pendingRefreshJob = viewModelScope.launch {
            delay(200)
            onSearchSubmit()
        }
    }

    private fun hasActiveFilters(): Boolean {
        return dateFromFilter != null ||
            dateToFilter != null ||
            sortOrderFilter != SORT_DESC ||
            strategyFilter != AiSearchStrategy.Combined ||
            categoryFilters.isNotEmpty() ||
            labelFilters.isNotEmpty()
    }

    private fun normalizeSortOrder(value: String?): String {
        return when (value?.trim()?.lowercase()) {
            SORT_ASC -> SORT_ASC
            else -> SORT_DESC
        }
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

data class SearchRequestKey(
    val query: String,
    val dateFrom: String?,
    val dateTo: String?,
    val sortOrder: String,
    val strategy: AiSearchStrategy,
    val categoryIds: List<String>,
    val labelNames: List<String>
)
