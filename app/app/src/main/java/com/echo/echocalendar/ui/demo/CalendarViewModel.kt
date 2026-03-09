package com.echo.echocalendar.ui.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.echocalendar.alarm.EventAlarmScheduler
import com.echo.echocalendar.data.local.EventAlarmDao
import com.echo.echocalendar.data.local.EventAlarmEntity
import com.echo.echocalendar.data.local.EventEntity
import com.echo.echocalendar.data.local.EventRawInputDao
import com.echo.echocalendar.data.local.EventRawInputEntity
import com.echo.echocalendar.domain.usecase.DeleteEventUseCase
import com.echo.echocalendar.domain.usecase.GetAllEventsUseCase
import com.echo.echocalendar.domain.usecase.GetEventByIdUseCase
import com.echo.echocalendar.domain.usecase.GetEventsByDateUseCase
import com.echo.echocalendar.domain.usecase.GetEventsByMonthUseCase
import com.echo.echocalendar.domain.usecase.GetLabelsForEventUseCase
import com.echo.echocalendar.domain.usecase.SaveEventUseCase
import com.echo.echocalendar.domain.usecase.UpdateEventUseCase
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.YearMonth
import kotlinx.coroutines.launch

class CalendarViewModel(
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
) : ViewModel() {
    private val zoneId = ZoneId.of("Asia/Seoul")

    var selectedDate by mutableStateOf(LocalDate.now(zoneId))
        private set
    var eventsOfDay by mutableStateOf<List<EventEntity>>(emptyList())
        private set
    var eventsByDate by mutableStateOf<Map<LocalDate, List<EventEntity>>>(emptyMap())
        private set
    var allEvents by mutableStateOf<List<EventEntity>>(emptyList())
        private set
    var labelsByEventId by mutableStateOf<Map<String, List<String>>>(emptyMap())
        private set
    var alarmEnabledByEventId by mutableStateOf<Map<String, Boolean>>(emptyMap())
        private set
    var rawInputByEventId by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    private var loadedMonth: YearMonth? = null

    init {
        loadEvents(selectedDate)
        loadEventsForMonth(YearMonth.from(selectedDate))
        loadAllEvents()
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

    fun openEventById(eventId: String, onResult: (EventEntity?) -> Unit) {
        viewModelScope.launch {
            val event = getEventByIdUseCase(eventId)
            if (event != null) {
                val date = Instant.ofEpochMilli(event.occurredAt).atZone(zoneId).toLocalDate()
                selectedDate = date
                loadEvents(date)
                loadEventsForMonth(YearMonth.from(date))
                loadLabelsForEvent(event.id)
            }
            onResult(event)
        }
    }

    fun addEvent(
        date: LocalDate,
        time: LocalTime,
        categoryId: String,
        isYearlyRecurring: Boolean,
        summary: String,
        body: String,
        placeText: String?,
        labels: List<String>,
        alarmEnabled: Boolean,
        rawInputText: String? = null
    ) {
        viewModelScope.launch {
            selectedDate = date
            val occurredAt = date.atTime(time).atZone(zoneId).toInstant().toEpochMilli()
            val eventId = saveEventUseCase(
                categoryId = categoryId,
                occurredAt = occurredAt,
                isYearlyRecurring = isYearlyRecurring,
                summary = summary,
                body = body,
                placeText = placeText,
                labels = labels
            )
            upsertRawInput(eventId, rawInputText)
            syncEventAlarm(eventId = eventId, occurredAt = occurredAt, summary = summary, enabled = alarmEnabled)
            loadEvents(date)
            loadEventsForMonth(YearMonth.from(date))
            loadAllEvents()
        }
    }

    fun deleteEvent(event: EventEntity) {
        viewModelScope.launch {
            deleteEventUseCase(event.id)
            eventAlarmDao.deleteByEventId(event.id)
            eventAlarmScheduler.cancel(event.id)
            labelsByEventId = labelsByEventId - event.id
            alarmEnabledByEventId = alarmEnabledByEventId - event.id
            rawInputByEventId = rawInputByEventId - event.id
            val date = Instant.ofEpochMilli(event.occurredAt).atZone(zoneId).toLocalDate()
            loadEvents(date)
            loadEventsForMonth(YearMonth.from(date))
            loadAllEvents()
        }
    }

    fun loadLabelsForEvent(eventId: String) {
        viewModelScope.launch {
            val labels = getLabelsForEventUseCase(eventId)
            labelsByEventId = labelsByEventId + (eventId to labels)
        }
    }

    fun updateEvent(
        eventId: String,
        date: LocalDate,
        time: LocalTime,
        categoryId: String,
        isYearlyRecurring: Boolean,
        summary: String,
        body: String,
        placeText: String?,
        labels: List<String>,
        alarmEnabled: Boolean,
        rawInputText: String? = null
    ) {
        viewModelScope.launch {
            selectedDate = date
            val occurredAt = date.atTime(time).atZone(zoneId).toInstant().toEpochMilli()
            updateEventUseCase(
                eventId = eventId,
                categoryId = categoryId,
                occurredAt = occurredAt,
                isYearlyRecurring = isYearlyRecurring,
                summary = summary,
                body = body,
                placeText = placeText,
                labels = labels
            )
            upsertRawInput(eventId, rawInputText)
            syncEventAlarm(eventId = eventId, occurredAt = occurredAt, summary = summary, enabled = alarmEnabled)
            labelsByEventId = labelsByEventId + (eventId to labels)
            loadEvents(date)
            loadEventsForMonth(YearMonth.from(date))
            loadAllEvents()
        }
    }

    private fun loadAllEvents() {
        viewModelScope.launch {
            allEvents = getAllEventsUseCase()
        }
    }

    private fun loadEvents(date: LocalDate) {
        viewModelScope.launch {
            val events = getEventsByDateUseCase(date, zoneId)
            eventsOfDay = events
            refreshAlarmStates(events)
            refreshRawInputs(events)
        }
    }

    private fun loadEventsForMonth(month: YearMonth) {
        loadedMonth = month
        viewModelScope.launch {
            val events = getEventsByMonthUseCase(month, zoneId)
            eventsByDate = events.groupBy {
                Instant.ofEpochMilli(it.occurredAt).atZone(zoneId).toLocalDate()
            }
            refreshAlarmStates(events)
            refreshRawInputs(events)
        }
    }

    private suspend fun refreshAlarmStates(events: List<EventEntity>) {
        if (events.isEmpty()) {
            return
        }
        val eventIds = events.map { it.id }.distinct()
        val alarms = eventAlarmDao.getByEventIds(eventIds)
        val alarmMapForEvents = alarms.associate { it.eventId to it.isEnabled }
        val updated = alarmEnabledByEventId.toMutableMap()
        eventIds.forEach { eventId ->
            updated[eventId] = alarmMapForEvents[eventId] ?: false
        }
        alarmEnabledByEventId = updated
    }

    private suspend fun refreshRawInputs(events: List<EventEntity>) {
        if (events.isEmpty()) return
        val eventIds = events.map { it.id }.distinct()
        val raws = eventRawInputDao.getByEventIds(eventIds)
        val rawMap = raws.associate { it.eventId to it.rawText }
        val updated = rawInputByEventId.toMutableMap()
        eventIds.forEach { eventId ->
            if (rawMap.containsKey(eventId)) {
                updated[eventId] = rawMap[eventId].orEmpty()
            } else {
                updated.remove(eventId)
            }
        }
        rawInputByEventId = updated
    }

    private suspend fun upsertRawInput(eventId: String, rawInputText: String?) {
        val normalized = rawInputText?.trim().orEmpty()
        if (normalized.isBlank()) return
        eventRawInputDao.upsert(
            EventRawInputEntity(
                eventId = eventId,
                rawText = normalized,
                updatedAt = System.currentTimeMillis()
            )
        )
        rawInputByEventId = rawInputByEventId + (eventId to normalized)
    }

    private suspend fun syncEventAlarm(
        eventId: String,
        occurredAt: Long,
        summary: String,
        enabled: Boolean
    ) {
        if (!enabled || occurredAt <= System.currentTimeMillis()) {
            eventAlarmDao.deleteByEventId(eventId)
            eventAlarmScheduler.cancel(eventId)
            return
        }
        eventAlarmDao.upsert(
            EventAlarmEntity(
                id = eventId,
                eventId = eventId,
                triggerAt = occurredAt,
                isEnabled = true
            )
        )
        eventAlarmScheduler.schedule(eventId = eventId, triggerAt = occurredAt, summary = summary)
    }
}
