package com.echo.echocalendar.ui.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.echocalendar.data.local.EventEntity
import com.echo.echocalendar.domain.usecase.GetEventsByDateUseCase
import com.echo.echocalendar.domain.usecase.GetEventsByMonthUseCase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import kotlinx.coroutines.launch

class CalendarViewModel(
    private val getEventsByDateUseCase: GetEventsByDateUseCase,
    private val getEventsByMonthUseCase: GetEventsByMonthUseCase
) : ViewModel() {
    private val zoneId = ZoneId.of("Asia/Seoul")

    var selectedDate by mutableStateOf(LocalDate.now(zoneId))
        private set
    var eventsOfDay by mutableStateOf<List<EventEntity>>(emptyList())
        private set
    var eventsByDate by mutableStateOf<Map<LocalDate, List<EventEntity>>>(emptyMap())
        private set

    private var loadedMonth: YearMonth? = null

    init {
        loadEvents(selectedDate)
        loadEventsForMonth(YearMonth.from(selectedDate))
    }

    fun onDateSelected(date: LocalDate) {
        selectedDate = date
        loadEvents(date)
    }

    fun onMonthShown(month: YearMonth) {
        if (loadedMonth == month) {
            return
        }
        loadEventsForMonth(month)
    }

    private fun loadEvents(date: LocalDate) {
        viewModelScope.launch {
            eventsOfDay = getEventsByDateUseCase(date, zoneId)
        }
    }

    private fun loadEventsForMonth(month: YearMonth) {
        loadedMonth = month
        viewModelScope.launch {
            val events = getEventsByMonthUseCase(month, zoneId)
            eventsByDate = events.groupBy {
                Instant.ofEpochMilli(it.occurredAt).atZone(zoneId).toLocalDate()
            }
        }
    }
}
