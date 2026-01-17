package com.echo.echocalendar.ui.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.echo.echocalendar.domain.usecase.GetEventsByDateUseCase
import com.echo.echocalendar.domain.usecase.GetEventsByMonthUseCase
import com.echo.echocalendar.domain.usecase.SaveEventUseCase
import com.echo.echocalendar.domain.usecase.SearchEventsUseCase

class SearchViewModelFactory(
    private val searchEventsUseCase: SearchEventsUseCase
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SearchViewModel(searchEventsUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class CalendarViewModelFactory(
    private val getEventsByDateUseCase: GetEventsByDateUseCase,
    private val getEventsByMonthUseCase: GetEventsByMonthUseCase,
    private val saveEventUseCase: SaveEventUseCase
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CalendarViewModel(
                getEventsByDateUseCase,
                getEventsByMonthUseCase,
                saveEventUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
