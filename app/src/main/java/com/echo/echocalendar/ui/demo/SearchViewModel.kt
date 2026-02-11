package com.echo.echocalendar.ui.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.echocalendar.data.local.EventEntity
import com.echo.echocalendar.domain.usecase.SearchEventsUseCase
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
        onSearchSubmit()
    }

    fun resetSearch() {
        query = ""
        results = emptyList()
        isLoading = false
        error = null
        dateFromFilter = null
        dateToFilter = null
        categoryFilters = emptyList()
    }
}
