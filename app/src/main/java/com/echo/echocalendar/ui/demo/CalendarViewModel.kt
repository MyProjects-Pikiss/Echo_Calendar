package com.echo.echocalendar.ui.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.echocalendar.data.local.EventEntity
import com.echo.echocalendar.domain.usecase.GetEventsByDateUseCase
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

class CalendarViewModel(
    private val getEventsByDateUseCase: GetEventsByDateUseCase
) : ViewModel() {
    private val zoneId = ZoneId.of("Asia/Seoul")

    var selectedDate by mutableStateOf(LocalDate.now(zoneId))
        private set
    var eventsOfDay by mutableStateOf<List<EventEntity>>(emptyList())
        private set

    init {
        loadEvents(selectedDate)
    }

    fun onDateSelected(date: LocalDate) {
        selectedDate = date
        loadEvents(date)
    }

    private fun loadEvents(date: LocalDate) {
        viewModelScope.launch {
            eventsOfDay = getEventsByDateUseCase(date, zoneId)
        }
    }
}
