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
import com.echo.echocalendar.data.local.LABEL_FILTER_INCLUDE_MODE_ALL
import com.echo.echocalendar.data.local.LABEL_FILTER_INCLUDE_MODE_ANY
import com.echo.echocalendar.data.local.LABEL_FILTER_RULE_EXCLUDE
import com.echo.echocalendar.data.local.LABEL_FILTER_RULE_INCLUDE
import com.echo.echocalendar.data.local.LabelDao
import com.echo.echocalendar.data.local.LabelFilterPresetDao
import com.echo.echocalendar.data.local.LabelFilterPresetSummary
import com.echo.echocalendar.data.local.LabelWithEventCount
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
    private val eventRawInputDao: EventRawInputDao,
    private val labelDao: LabelDao,
    private val labelFilterPresetDao: LabelFilterPresetDao
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
    var labelStats by mutableStateOf<List<LabelWithEventCount>>(emptyList())
        private set
    var eventsByLabelId by mutableStateOf<Map<Long, List<EventEntity>>>(emptyMap())
        private set
    var labelFilterPresets by mutableStateOf<List<LabelFilterPresetSummary>>(emptyList())
        private set
    var eventsByLabelFilterPresetId by mutableStateOf<Map<Long, List<EventEntity>>>(emptyMap())
        private set
    var labelOperationMessage by mutableStateOf<String?>(null)
        private set

    private var loadedMonth: YearMonth? = null

    init {
        loadEvents(selectedDate)
        loadEventsForMonth(YearMonth.from(selectedDate))
        loadAllEvents()
        loadLabelStats()
        loadLabelFilterPresets()
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
        labelsCreatedByAi: Boolean = false,
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
                labels = labels,
                labelsCreatedByAi = labelsCreatedByAi
            )
            upsertRawInput(eventId, rawInputText)
            syncEventAlarm(eventId = eventId, occurredAt = occurredAt, summary = summary, enabled = alarmEnabled)
            eventsByLabelId = emptyMap()
            eventsByLabelFilterPresetId = emptyMap()
            loadEvents(date)
            loadEventsForMonth(YearMonth.from(date))
            loadAllEvents()
            loadLabelStats()
        }
    }

    fun deleteEvent(event: EventEntity) {
        viewModelScope.launch {
            deleteEventUseCase(event.id)
            eventAlarmDao.deleteByEventId(event.id)
            eventAlarmScheduler.cancel(event.id)
            labelsByEventId = labelsByEventId - event.id
            eventsByLabelId = emptyMap()
            eventsByLabelFilterPresetId = emptyMap()
            alarmEnabledByEventId = alarmEnabledByEventId - event.id
            rawInputByEventId = rawInputByEventId - event.id
            val date = Instant.ofEpochMilli(event.occurredAt).atZone(zoneId).toLocalDate()
            loadEvents(date)
            loadEventsForMonth(YearMonth.from(date))
            loadAllEvents()
            loadLabelStats()
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
        labelsCreatedByAi: Boolean = false,
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
                labels = labels,
                labelsCreatedByAi = labelsCreatedByAi
            )
            upsertRawInput(eventId, rawInputText)
            syncEventAlarm(eventId = eventId, occurredAt = occurredAt, summary = summary, enabled = alarmEnabled)
            labelsByEventId = labelsByEventId + (eventId to labels)
            eventsByLabelId = emptyMap()
            eventsByLabelFilterPresetId = emptyMap()
            loadEvents(date)
            loadEventsForMonth(YearMonth.from(date))
            loadAllEvents()
            loadLabelStats()
        }
    }

    fun loadLabelStats() {
        viewModelScope.launch {
            labelStats = labelDao.getAllWithEventCounts()
        }
    }

    fun loadEventsForLabel(labelId: Long) {
        viewModelScope.launch {
            eventsByLabelId = eventsByLabelId + (labelId to labelDao.getEventsByLabelId(labelId))
        }
    }

    fun loadLabelFilterPresets() {
        viewModelScope.launch {
            val labelsById = labelDao.getAllWithEventCounts().associateBy { it.id }
            val rulesByPresetId = labelFilterPresetDao.getAllRules().groupBy { it.presetId }
            labelFilterPresets = labelFilterPresetDao.getAllPresets().map { preset ->
                val rules = rulesByPresetId[preset.id].orEmpty()
                LabelFilterPresetSummary(
                    preset = preset,
                    includeLabels = rules
                        .filter { it.role == LABEL_FILTER_RULE_INCLUDE }
                        .mapNotNull { labelsById[it.labelId] }
                        .sortedBy { it.name },
                    excludeLabels = rules
                        .filter { it.role == LABEL_FILTER_RULE_EXCLUDE }
                        .mapNotNull { labelsById[it.labelId] }
                        .sortedBy { it.name }
                )
            }
        }
    }

    fun saveLabelFilterPreset(
        presetId: Long?,
        name: String,
        includeMode: String,
        includeLabelIds: Set<Long>,
        excludeLabelIds: Set<Long>,
        onResult: (Boolean) -> Unit = {}
    ) {
        val normalizedName = normalizePresetName(name)
        if (normalizedName == null) {
            labelOperationMessage = "프리셋 이름은 1~30자로 입력해 주세요."
            onResult(false)
            return
        }
        val normalizedIncludeMode = if (includeMode == LABEL_FILTER_INCLUDE_MODE_ALL) {
            LABEL_FILTER_INCLUDE_MODE_ALL
        } else {
            LABEL_FILTER_INCLUDE_MODE_ANY
        }
        val includes = includeLabelIds.toList()
        val excludes = excludeLabelIds.filterNot { it in includeLabelIds }
        if (includes.isEmpty() && excludes.isEmpty()) {
            labelOperationMessage = "포함 또는 제외 라벨을 하나 이상 선택해 주세요."
            onResult(false)
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val saved = if (presetId == null) {
                labelFilterPresetDao.createPreset(
                    name = normalizedName,
                    includeMode = normalizedIncludeMode,
                    includeLabelIds = includes,
                    excludeLabelIds = excludes,
                    now = now
                ) != null
            } else {
                labelFilterPresetDao.editPreset(
                    presetId = presetId,
                    name = normalizedName,
                    includeMode = normalizedIncludeMode,
                    includeLabelIds = includes,
                    excludeLabelIds = excludes,
                    updatedAt = now
                )
            }
            labelOperationMessage = if (saved) {
                "라벨 필터 프리셋을 저장했어요."
            } else {
                "같은 이름의 프리셋이 있거나 프리셋을 찾지 못했어요."
            }
            eventsByLabelFilterPresetId = emptyMap()
            loadLabelFilterPresets()
            onResult(saved)
        }
    }

    fun deleteLabelFilterPreset(presetId: Long) {
        viewModelScope.launch {
            labelFilterPresetDao.deletePresetById(presetId)
            eventsByLabelFilterPresetId = eventsByLabelFilterPresetId - presetId
            labelOperationMessage = "라벨 필터 프리셋을 삭제했어요."
            loadLabelFilterPresets()
        }
    }

    fun loadEventsForLabelFilterPreset(preset: LabelFilterPresetSummary) {
        viewModelScope.launch {
            val includeLabelIds = preset.includeLabels.map { it.id }.distinct()
            val excludeLabelIds = preset.excludeLabels.map { it.id }.distinct()
            val all = getAllEventsUseCase()
            val includeEventIds = when {
                includeLabelIds.isEmpty() -> all.map { it.id }.toSet()
                preset.preset.includeMode == LABEL_FILTER_INCLUDE_MODE_ALL ->
                    labelFilterPresetDao
                        .getEventIdsContainingAllLabelIds(includeLabelIds, includeLabelIds.size)
                        .toSet()
                else ->
                    labelFilterPresetDao
                        .getEventIdsMatchingAnyLabelIds(includeLabelIds)
                        .toSet()
            }
            val excludeEventIds = if (excludeLabelIds.isEmpty()) {
                emptySet()
            } else {
                labelFilterPresetDao.getEventIdsMatchingAnyLabelIds(excludeLabelIds).toSet()
            }
            val matchedEventIds = includeEventIds - excludeEventIds
            eventsByLabelFilterPresetId = eventsByLabelFilterPresetId + (
                preset.preset.id to all.filter { it.id in matchedEventIds }
            )
        }
    }

    fun clearLabelOperationMessage() {
        labelOperationMessage = null
    }

    fun addLabel(name: String) {
        val normalized = normalizeLabelName(name)
        if (normalized == null) {
            labelOperationMessage = "라벨 이름은 1~20자이며 쉼표를 포함할 수 없어요."
            return
        }
        viewModelScope.launch {
            val added = labelDao.addLabel(normalized, System.currentTimeMillis())
            labelOperationMessage = if (added) {
                "라벨을 추가했어요."
            } else {
                "이미 같은 이름의 라벨이 있어요."
            }
            loadLabelStats()
        }
    }

    fun deleteLabel(labelId: Long) {
        viewModelScope.launch {
            val label = labelDao.getById(labelId)
            if (label == null) {
                labelOperationMessage = "라벨을 찾지 못했어요."
                loadLabelStats()
                return@launch
            }
            labelDao.deleteById(labelId)
            labelsByEventId = emptyMap()
            eventsByLabelId = eventsByLabelId - labelId
            eventsByLabelFilterPresetId = emptyMap()
            labelOperationMessage = "라벨을 삭제했어요."
            loadLabelStats()
            loadLabelFilterPresets()
            loadAllEvents()
        }
    }

    fun renameLabel(labelId: Long, name: String) {
        val normalized = normalizeLabelName(name)
        if (normalized == null) {
            labelOperationMessage = "라벨 이름은 1~20자이며 쉼표를 포함할 수 없어요."
            return
        }
        viewModelScope.launch {
            val renamed = labelDao.renameLabel(labelId, normalized)
            labelsByEventId = emptyMap()
            eventsByLabelId = eventsByLabelId - labelId
            eventsByLabelFilterPresetId = emptyMap()
            labelOperationMessage = if (renamed) {
                "라벨 이름을 변경했어요."
            } else {
                "같은 이름의 라벨이 있거나 라벨을 찾지 못했어요."
            }
            loadLabelStats()
            loadLabelFilterPresets()
        }
    }

    fun mergeLabels(sourceLabelId: Long, targetLabelId: Long) {
        viewModelScope.launch {
            val merged = labelDao.mergeLabels(sourceLabelId, targetLabelId)
            labelsByEventId = emptyMap()
            eventsByLabelId = eventsByLabelId - sourceLabelId - targetLabelId
            eventsByLabelFilterPresetId = emptyMap()
            labelOperationMessage = if (merged) {
                "라벨을 병합했어요."
            } else {
                "병합할 라벨을 확인해 주세요."
            }
            loadLabelStats()
            loadLabelFilterPresets()
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

    private fun normalizeLabelName(raw: String): String? {
        val normalized = raw
            .trim()
            .removePrefix("#")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (normalized.isBlank() || normalized.length > 20 || "," in normalized) return null
        return normalized
    }

    private fun normalizePresetName(raw: String): String? {
        val normalized = raw
            .trim()
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (normalized.isBlank() || normalized.length > 30) return null
        return normalized
    }
}
