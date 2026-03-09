package com.echo.echocalendar.ui.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.echo.echocalendar.alarm.EventAlarmScheduler
import com.echo.echocalendar.data.local.EventAlarmDao
import com.echo.echocalendar.data.local.EventRawInputDao
import com.echo.echocalendar.domain.usecase.DeleteEventUseCase
import com.echo.echocalendar.domain.usecase.GetAllEventsUseCase
import com.echo.echocalendar.domain.usecase.GetEventByIdUseCase
import com.echo.echocalendar.domain.usecase.GetEventsByDateUseCase
import com.echo.echocalendar.domain.usecase.GetEventsByMonthUseCase
import com.echo.echocalendar.domain.usecase.GetLabelsForEventUseCase
import com.echo.echocalendar.domain.usecase.SaveEventUseCase
import com.echo.echocalendar.domain.usecase.SearchEventsUseCase
import com.echo.echocalendar.domain.usecase.UpdateEventUseCase

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
    private val getEventByIdUseCase: GetEventByIdUseCase,
    private val getEventsByMonthUseCase: GetEventsByMonthUseCase,
    private val getAllEventsUseCase: GetAllEventsUseCase,
    private val getLabelsForEventUseCase: GetLabelsForEventUseCase,
    private val saveEventUseCase: SaveEventUseCase,
    private val deleteEventUseCase: DeleteEventUseCase,
    private val updateEventUseCase: UpdateEventUseCase,
    private val eventAlarmDao: EventAlarmDao,
    private val eventAlarmScheduler: EventAlarmScheduler,
    private val eventRawInputDao: EventRawInputDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CalendarViewModel(
                getEventsByDateUseCase,
                getEventByIdUseCase,
                getEventsByMonthUseCase,
                getAllEventsUseCase,
                getLabelsForEventUseCase,
                saveEventUseCase,
                deleteEventUseCase,
                updateEventUseCase,
                eventAlarmDao,
                eventAlarmScheduler,
                eventRawInputDao
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
