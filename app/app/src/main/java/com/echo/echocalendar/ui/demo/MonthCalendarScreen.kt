package com.echo.echocalendar.ui.demo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import com.echo.echocalendar.SettingsKeys
import com.echo.echocalendar.data.local.CategoryDefaults
import com.echo.echocalendar.domain.usecase.MAX_LABELS_PER_EVENT
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MonthCalendarScreen(
    calendarViewModel: CalendarViewModel,
    searchViewModel: SearchViewModel,
    aiSearchViewModel: SearchViewModel,
    aiAssistantService: AiAssistantService,
    isOnline: Boolean,
    openEventId: String? = null,
    onOpenEventHandled: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val zoneId = remember { ZoneId.of("Asia/Seoul") }
    val today = remember { LocalDate.now(zoneId) }
    val monthFormatter = remember { DateTimeFormatter.ofPattern("yyyy년 M월") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val inputTimeFormatter = remember { DateTimeFormatter.ofPattern("H:mm") }
    val initialMonth = remember { YearMonth.from(calendarViewModel.selectedDate) }
    var shownMonth by remember { mutableStateOf(initialMonth) }
    var isJumpDialogOpen by remember { mutableStateOf(false) }
    var selectedEvent by remember { mutableStateOf<com.echo.echocalendar.data.local.EventEntity?>(null) }
    var pendingEdit by remember { mutableStateOf<PendingEdit?>(null) }
    var pendingDelete by remember { mutableStateOf<com.echo.echocalendar.data.local.EventEntity?>(null) }
    var isCategoryMenuOpen by remember { mutableStateOf(false) }
    var editError by remember { mutableStateOf<String?>(null) }
    var isActionPickerOpen by remember { mutableStateOf(false) }
    var activeTrigger by remember { mutableStateOf(InputTrigger.Keyboard) }
    var isSearchOpen by remember { mutableStateOf(false) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isProfileOpen by remember { mutableStateOf(false) }
    var isAiSearchResultOpen by remember { mutableStateOf(false) }
    var aiSearchSuggestion by remember { mutableStateOf<AiSearchSuggestion?>(null) }
    var pendingAiAction by remember { mutableStateOf<AiAction?>(null) }
    var aiErrorMessage by remember { mutableStateOf<String?>(null) }
    var aiStatusMessage by remember { mutableStateOf<String?>(null) }
    var processingAiAction by remember { mutableStateOf<AiAction?>(null) }
    var isAiProcessing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var syncedHolidayBadges by remember { mutableStateOf<Map<LocalDate, HolidayBadge>>(emptyMap()) }

    val context = LocalContext.current
    val settingsPrefs = remember(context) {
        context.getSharedPreferences(SettingsKeys.SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    }
    var alarmAlertMode by remember {
        mutableStateOf(
            settingsPrefs.getString(
                SettingsKeys.KEY_ALARM_ALERT_MODE,
                SettingsKeys.ALARM_ALERT_MODE_SOUND
            ) ?: SettingsKeys.ALARM_ALERT_MODE_SOUND
        )
    }
    var usageAccessToken by remember {
        mutableStateOf(settingsPrefs.getString(SettingsKeys.KEY_USAGE_ACCESS_TOKEN, "").orEmpty())
    }
    var myUsageSummary by remember { mutableStateOf<UsageMySummary?>(null) }
    var usageStatusMessage by remember { mutableStateOf<String?>(null) }
    var isUsageLoading by remember { mutableStateOf(false) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var isListening by remember { mutableStateOf(false) }
    var partialTranscript by remember { mutableStateOf("") }
    var lastRecognizedTranscript by remember { mutableStateOf("") }

    fun actionLabel(action: AiAction?): String = when (action) {
        is AiAction.Input -> "AI 입력"
        is AiAction.Search -> "AI 검색"
        is AiAction.Modify -> "AI 수정"
        is AiAction.RefineField -> "필드 보완"
        null -> "음성 인식"
    }

    fun handleRecognizedTranscript(transcript: String) {
        if (transcript.isBlank()) {
            aiErrorMessage = "음성을 텍스트로 변환하지 못했어요. 다시 시도해 주세요."
            pendingAiAction = null
            return
        }
        when (val action = pendingAiAction) {
            is AiAction.Input, is AiAction.Modify -> {
                val isModifyOnly = action is AiAction.Modify
                processingAiAction = action
                isAiProcessing = true
                coroutineScope.launch {
                    try {
                        val result = aiAssistantService.suggestInput(
                            transcript = transcript,
                            selectedDate = calendarViewModel.selectedDate
                        )
                        val suggestion = result.suggestion
                        val wantsAlarm = wantsAlarmFromTranscript(transcript)
                        val wantsYearlyRecurring = wantsYearlyRecurringFromTranscript(transcript)
                        val suggestionRepeatYearly = suggestion.repeatYearly ?: if (wantsYearlyRecurring) true else null
                        val isAutoTimeApplied = suggestion.timeText.isBlank()
                        val resolvedTimeText = suggestion.timeText.trim().ifBlank {
                            LocalTime.now(zoneId).format(timeFormatter)
                        }
                        val suggestionDraft = EventDraft(
                            summary = suggestion.summary,
                            timeText = resolvedTimeText,
                            isYearlyRecurring = suggestionRepeatYearly ?: false,
                            categoryId = normalizeCategoryIdOrNull(suggestion.categoryId) ?: "other",
                            placeText = suggestion.placeText,
                            body = suggestion.body,
                            labelsText = suggestion.labelsText
                        )
                        val candidates = candidateEventsForDate(
                            date = suggestion.date,
                            selectedDate = calendarViewModel.selectedDate,
                            eventsByDate = calendarViewModel.eventsByDate,
                            eventsOfSelectedDate = calendarViewModel.eventsOfDay
                        )

                        when (suggestion.intent) {
                            AiCrudIntent.Create -> {
                                if (isModifyOnly) {
                                    aiErrorMessage = "AI 수정에서는 수정/삭제 요청만 처리할 수 있어요. 예: '내일 회의 내용 바꿔줘', '내일 회의 삭제해줘'"
                                    return@launch
                                }
                                pendingEdit = PendingEdit(
                                    action = CrudAction.Create,
                                    eventId = null,
                                    date = suggestion.date,
                                    draft = suggestionDraft,
                                    alarmEnabled = wantsAlarm,
                                    rawInputText = transcript
                                )
                                isCategoryMenuOpen = false
                                val missingRequired = if (isAutoTimeApplied) {
                                    suggestion.missingRequired.filterNot { it.trim() == "시간" }
                                } else {
                                    suggestion.missingRequired
                                }
                                editError = missingRequired
                                    .takeIf { it.isNotEmpty() }
                                    ?.joinToString(prefix = "필수 항목을 채워주세요: ", separator = ", ")
                            }
                            AiCrudIntent.Update -> {
                                val target = findBestTargetEvent(candidates, suggestion.summary, suggestion.body)
                                if (target == null) {
                                    aiErrorMessage = "수정할 이벤트를 찾지 못했어요. 제목을 더 구체적으로 말해 주세요."
                                    return@launch
                                }
                                val dateTime = Instant.ofEpochMilli(target.occurredAt).atZone(zoneId).toLocalDateTime()
                                val existingLabels = calendarViewModel.labelsByEventId[target.id].orEmpty()
                                val existingAlarmEnabled = calendarViewModel.alarmEnabledByEventId[target.id] == true
                                val existingDraft = EventDraft(
                                    summary = target.summary,
                                    timeText = timeFormatter.format(dateTime),
                                    isYearlyRecurring = target.isYearlyRecurring,
                                    categoryId = target.categoryId,
                                    placeText = target.placeText.orEmpty(),
                                    body = target.body,
                                    labelsText = existingLabels.joinToString(", ")
                                )
                                pendingEdit = PendingEdit(
                                    action = CrudAction.Update,
                                    eventId = target.id,
                                    date = dateTime.toLocalDate(),
                                    draft = if (isModifyOnly) {
                                        buildDraftForModify(
                                            transcript = transcript,
                                            existing = existingDraft,
                                            selectedDate = dateTime.toLocalDate(),
                                            aiAssistantService = aiAssistantService,
                                            currentRawText = calendarViewModel.rawInputByEventId[target.id]
                                        )
                                    } else {
                                        mergeDraftForUpdate(
                                            existing = existingDraft,
                                            suggestion = suggestionDraft,
                                            repeatYearlyOverride = suggestionRepeatYearly
                                        )
                                    },
                                    alarmEnabled = if (wantsAlarm) true else existingAlarmEnabled,
                                    originalDraft = existingDraft,
                                    originalAlarmEnabled = existingAlarmEnabled,
                                    rawInputText = transcript
                                )
                                isCategoryMenuOpen = false
                                editError = null
                                aiStatusMessage = if (isModifyOnly) {
                                    "수정 후보를 찾았어요. 확인 후 반영해 주세요."
                                } else {
                                    "수정 후보를 찾았어요. 확인 후 반영해 주세요."
                                }
                            }
                            AiCrudIntent.Delete -> {
                                val target = findBestTargetEvent(candidates, suggestion.summary, suggestion.body)
                                if (target == null) {
                                    aiErrorMessage = "삭제할 이벤트를 찾지 못했어요. 제목을 더 구체적으로 말해 주세요."
                                    return@launch
                                }
                                pendingDelete = target
                                aiStatusMessage = "삭제 후보를 찾았어요. 확인 후 삭제해 주세요."
                            }
                        }
                    } catch (error: AiRemoteException) {
                        aiErrorMessage = error.userMessage
                        aiStatusMessage = null
                    } catch (_: Exception) {
                        aiErrorMessage = "AI 기능을 사용할 수 없어요. 인터넷 연결 또는 서버 상태를 확인해 주세요."
                        aiStatusMessage = null
                    } finally {
                        isAiProcessing = false
                        processingAiAction = null
                    }
                }
            }
            is AiAction.Search -> {
                processingAiAction = action
                isAiProcessing = true
                coroutineScope.launch {
                    try {
                        val result = aiAssistantService.suggestSearch(transcript)
                        val suggestion = result.suggestion
                        if (suggestion.query.isBlank() &&
                            suggestion.dateFrom.isNullOrBlank() &&
                            suggestion.dateTo.isNullOrBlank() &&
                            suggestion.categoryIds.isEmpty() &&
                            suggestion.labelNames.isEmpty()
                        ) {
                            aiErrorMessage = "검색어를 만들지 못했어요. 다시 말해 주세요."
                            return@launch
                        }
                        aiSearchSuggestion = suggestion
                        aiSearchViewModel.applyAiSearchSuggestion(suggestion)
                        isAiSearchResultOpen = true
                    } catch (error: AiRemoteException) {
                        aiErrorMessage = error.userMessage
                        aiStatusMessage = null
                    } catch (_: Exception) {
                        aiErrorMessage = "AI 기능을 사용할 수 없어요. 인터넷 연결 또는 서버 상태를 확인해 주세요."
                        aiStatusMessage = null
                    } finally {
                        isAiProcessing = false
                        processingAiAction = null
                    }
                }
            }
            is AiAction.RefineField -> {
                val editState = pendingEdit
                if (editState == null) {
                    aiErrorMessage = "편집 중인 항목이 없어요."
                } else {
                    val currentValue = valueOfField(editState.draft, action.field)
                    processingAiAction = action
                    isAiProcessing = true
                    coroutineScope.launch {
                        try {
                            val result = aiAssistantService.refineField(
                                transcript = transcript,
                                field = action.field,
                                currentValue = currentValue,
                                selectedDate = editState.date
                            )
                            val refined = result.suggestion
                            pendingEdit = editState.copy(
                                draft = applyFieldValue(editState.draft, refined.field, refined.value)
                            )
                            editError = refined.missingRequired
                                .takeIf { it.isNotEmpty() }
                                ?.joinToString(prefix = "필수 항목을 채워주세요: ", separator = ", ")
                            aiStatusMessage = "AI 서버가 ${fieldLabel(action.field)} 항목 보완을 완료했어요."
                        } catch (error: AiRemoteException) {
                            aiErrorMessage = error.userMessage
                            aiStatusMessage = null
                        } catch (_: Exception) {
                            aiErrorMessage = "AI 기능을 사용할 수 없어요. 인터넷 연결 또는 서버 상태를 확인해 주세요."
                            aiStatusMessage = null
                        } finally {
                            isAiProcessing = false
                            processingAiAction = null
                        }
                    }
                }
            }
            null -> Unit
        }
        pendingAiAction = null
    }

    fun startVoiceRecognition() {
        if (pendingAiAction == null) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            aiErrorMessage = "이 기기에서는 음성 인식을 사용할 수 없어요."
            pendingAiAction = null
            return
        }
        partialTranscript = ""
        aiErrorMessage = null
        val recognitionLanguageTag = "ko-KR"
        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLanguageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, recognitionLanguageTag)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
        }
        speechRecognizer?.startListening(speechIntent)
        isListening = true
    }

    fun stopVoiceRecognition() {
        if (!isListening) return
        speechRecognizer?.stopListening()
        isListening = false
    }

    DisposableEffect(context) {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    isListening = false
                    partialTranscript = ""
                    if (error == SpeechRecognizer.ERROR_CLIENT) {
                        pendingAiAction = null
                        return
                    }
                    Log.w("MonthCalendarScreen", "speech_recognition_failed code=$error")
                    aiErrorMessage = speechRecognitionErrorMessage(error)
                    pendingAiAction = null
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val transcript = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    lastRecognizedTranscript = transcript
                    partialTranscript = transcript
                    handleRecognizedTranscript(transcript)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    partialTranscript = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
        speechRecognizer = recognizer
        onDispose {
            recognizer.destroy()
            speechRecognizer = null
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            aiErrorMessage = "음성 인식을 사용하려면 마이크 권한이 필요해요."
            pendingAiAction = null
            return@rememberLauncherForActivityResult
        }
        startVoiceRecognition()
    }

    fun triggerVoiceAction(action: AiAction) {
        if (!isOnline) {
            aiErrorMessage = "오프라인 상태에서는 AI 음성 기능을 사용할 수 없어요."
            pendingAiAction = null
            return
        }
        if (isListening && pendingAiAction == action) {
            stopVoiceRecognition()
            return
        }
        if (isListening) {
            stopVoiceRecognition()
        }
        pendingAiAction = action
        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val pagerState = rememberPagerState(initialPage = 1200, pageCount = { 2400 })
    val bottomBarHeight = 72.dp
    val popupWidth = 240.dp
    val density = LocalDensity.current

    LaunchedEffect(pagerState.currentPage) {
        val offset = pagerState.currentPage - 1200
        val candidate = initialMonth.plusMonths(offset.toLong())
        if (candidate != shownMonth) {
            shownMonth = candidate
        }
    }

    LaunchedEffect(shownMonth) {
        val offset = YearMonth.from(initialMonth)
            .until(shownMonth, java.time.temporal.ChronoUnit.MONTHS)
        val targetPage = 1200 + offset.toInt()
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
        calendarViewModel.onMonthShown(shownMonth)
    }

    LaunchedEffect(calendarViewModel.selectedDate) {
        val selectedMonth = YearMonth.from(calendarViewModel.selectedDate)
        if (shownMonth != selectedMonth) {
            shownMonth = selectedMonth
        }
    }

    LaunchedEffect(selectedEvent?.id) {
        selectedEvent?.let { event ->
            calendarViewModel.loadLabelsForEvent(event.id)
        }
    }

    LaunchedEffect(openEventId) {
        val eventId = openEventId ?: return@LaunchedEffect
        calendarViewModel.openEventById(eventId) { event ->
            if (event != null) {
                selectedEvent = event
            } else {
                aiErrorMessage = "알림으로 연 이벤트를 찾지 못했어요."
            }
            onOpenEventHandled()
        }
    }

    LaunchedEffect(Unit) {
        val cached = HolidaySyncStore.loadCachedOnly(context)
        syncedHolidayBadges = toHolidayBadgeMap(cached)

        val refreshed = HolidaySyncStore.refreshAndLoad(context)
        syncedHolidayBadges = toHolidayBadgeMap(refreshed)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val popupWidthPx = with(density) { popupWidth.toPx() }
        val anchorFraction = if (activeTrigger == InputTrigger.Keyboard) 0.25f else 0.75f
        val desiredOffsetPx = maxWidthPx * anchorFraction - popupWidthPx / 2
        val clampedOffsetPx = desiredOffsetPx.coerceIn(0f, maxWidthPx - popupWidthPx)
        val popupYOffsetPx = with(density) { -bottomBarHeight.toPx() }
        val animatedOffset by animateIntOffsetAsState(
            targetValue = androidx.compose.ui.unit.IntOffset(
                x = clampedOffsetPx.roundToInt(),
                y = popupYOffsetPx.roundToInt()
            ),
            animationSpec = tween(durationMillis = 180),
            label = "ActionPickerOffset"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(bottom = bottomBarHeight)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { isProfileOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "프로필"
                    )
                }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { shownMonth = shownMonth.minusMonths(1) }) {
                        Text(text = "◀")
                    }
                    Text(
                        text = monthFormatter.format(shownMonth.atDay(1)),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.clickable { isJumpDialogOpen = true }
                    )
                    IconButton(onClick = { shownMonth = shownMonth.plusMonths(1) }) {
                        Text(text = "▶")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            shownMonth = YearMonth.from(today)
                            calendarViewModel.onDateSelected(today)
                        }
                    ) {
                        Text(text = "오늘")
                    }
                    IconButton(onClick = { isSettingsOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "설정"
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf(
                    DayOfWeek.SUNDAY,
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY,
                    DayOfWeek.SATURDAY
                ).forEach { dayOfWeek ->
                    val label = when (dayOfWeek) {
                        DayOfWeek.SUNDAY -> "일"
                        DayOfWeek.MONDAY -> "월"
                        DayOfWeek.TUESDAY -> "화"
                        DayOfWeek.WEDNESDAY -> "수"
                        DayOfWeek.THURSDAY -> "목"
                        DayOfWeek.FRIDAY -> "금"
                        DayOfWeek.SATURDAY -> "토"
                    }
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                val month = initialMonth.plusMonths((page - 1200).toLong())
                MonthGrid(
                    month = month,
                    today = today,
                    selectedDate = calendarViewModel.selectedDate,
                    eventsByDate = calendarViewModel.eventsByDate,
                    syncedHolidayBadges = syncedHolidayBadges,
                    onDateClick = {
                        calendarViewModel.onDateSelected(it)
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "선택된 날짜: ${calendarViewModel.selectedDate.format(dateFormatter)}",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Spacer(modifier = Modifier.height(8.dp))
            if (calendarViewModel.eventsOfDay.isEmpty()) {
                Text(text = "해당 날짜의 이벤트가 없습니다.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(calendarViewModel.eventsOfDay) { event ->
                        val eventDateTime = Instant.ofEpochMilli(event.occurredAt)
                            .atZone(zoneId)
                            .toLocalDateTime()
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedEvent = event }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = event.summary,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = timeFormatter.format(eventDateTime),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isActionPickerOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isActionPickerOpen = false
                    }
            )
        }

        AnimatedVisibility(
            visible = isActionPickerOpen,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset { animatedOffset },
            enter = fadeIn(animationSpec = tween(160)) + slideInVertically(
                animationSpec = tween(160),
                initialOffsetY = { it / 2 }
            ),
            exit = fadeOut(animationSpec = tween(140)) + slideOutVertically(
                animationSpec = tween(140),
                targetOffsetY = { it / 2 }
            )
        ) {
            Surface(
                modifier = Modifier.width(popupWidth),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    AnimatedVisibility(
                        visible = activeTrigger == InputTrigger.Keyboard,
                        enter = fadeIn(animationSpec = tween(160)) + slideInVertically(
                            animationSpec = tween(160),
                            initialOffsetY = { it / 4 }
                        ),
                        exit = fadeOut(animationSpec = tween(120)) + slideOutVertically(
                            animationSpec = tween(120),
                            targetOffsetY = { it / 4 }
                        )
                    ) {
                        ActionPickerRow(
                            firstLabel = "입력",
                            firstIcon = Icons.Default.Edit,
                            secondLabel = "검색",
                            secondIcon = Icons.Default.Search,
                            onFirstActionSelected = {
                                editError = null
                                isCategoryMenuOpen = false
                                pendingEdit = PendingEdit(
                                    action = CrudAction.Create,
                                    eventId = null,
                                    date = calendarViewModel.selectedDate,
                                    draft = EventDraft(
                                        summary = "",
                                        timeText = LocalTime.now(zoneId).format(timeFormatter),
                                        isYearlyRecurring = false,
                                        categoryId = "other",
                                        placeText = "",
                                        body = "",
                                        labelsText = ""
                                    ),
                                    alarmEnabled = false
                                )
                                isActionPickerOpen = false
                            },
                            onSecondActionSelected = {
                                isActionPickerOpen = false
                                searchViewModel.resetSearch()
                                isSearchOpen = true
                            }
                        )
                    }
                    AnimatedVisibility(
                        visible = activeTrigger == InputTrigger.Microphone,
                        enter = fadeIn(animationSpec = tween(160)) + slideInVertically(
                            animationSpec = tween(160),
                            initialOffsetY = { it / 4 }
                        ),
                        exit = fadeOut(animationSpec = tween(120)) + slideOutVertically(
                            animationSpec = tween(120),
                            targetOffsetY = { it / 4 }
                        )
                    ) {
                        Column {
                            if (isListening) {
                                Text(
                                    text = "${actionLabel(pendingAiAction)} 듣는 중... 같은 버튼을 다시 누르면 종료돼요.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                            if (isAiProcessing) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = "${actionLabel(processingAiAction)} 응답 기다리는 중...",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            val displayTranscript = when {
                                partialTranscript.isNotBlank() -> partialTranscript
                                isAiProcessing -> lastRecognizedTranscript
                                else -> ""
                            }
                            if (displayTranscript.isNotBlank()) {
                                Text(
                                    text = "인식된 음성: $displayTranscript",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                            ActionPickerRowThree(
                                firstLabel = "AI 입력",
                                firstIcon = Icons.Default.Mic,
                                secondLabel = "AI 검색",
                                secondIcon = Icons.Default.Search,
                                thirdLabel = "AI 수정",
                                thirdIcon = Icons.Default.Edit,
                                onFirstActionSelected = {
                                    triggerVoiceAction(AiAction.Input)
                                },
                                onSecondActionSelected = {
                                    triggerVoiceAction(AiAction.Search)
                                },
                                onThirdActionSelected = {
                                    triggerVoiceAction(AiAction.Modify)
                                }
                            )
                            if (!isOnline) {
                                Text(
                                    text = "오프라인 상태에서는 AI 입력/검색/수정을 사용할 수 없어요.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomBarHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomBarButton(
                    icon = Icons.Default.Keyboard,
                    label = "키보드",
                    enabled = true,
                    onClick = {
                        if (activeTrigger == InputTrigger.Keyboard) {
                            isActionPickerOpen = !isActionPickerOpen
                        } else {
                            activeTrigger = InputTrigger.Keyboard
                            isActionPickerOpen = true
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                HorizontalDivider(
                    modifier = Modifier
                        .height(bottomBarHeight * 0.6f)
                        .width(1.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
                BottomBarButton(
                    icon = Icons.Default.Mic,
                    label = "마이크",
                    enabled = isOnline,
                    onClick = {
                        if (activeTrigger == InputTrigger.Microphone) {
                            isActionPickerOpen = !isActionPickerOpen
                        } else {
                            activeTrigger = InputTrigger.Microphone
                            isActionPickerOpen = true
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (isJumpDialogOpen) {
        MonthJumpDialog(
            initialMonth = shownMonth,
            onDismiss = { isJumpDialogOpen = false },
            onConfirm = { year, month ->
                shownMonth = YearMonth.of(year, month)
                isJumpDialogOpen = false
            }
        )
    }

    if (isProfileOpen) {
        LaunchedEffect(isProfileOpen) {
            usageAccessToken = settingsPrefs.getString(SettingsKeys.KEY_USAGE_ACCESS_TOKEN, "").orEmpty()
            if (usageAccessToken.isNotBlank()) {
                isUsageLoading = true
                try {
                    myUsageSummary = aiAssistantService.fetchMyUsage(usageAccessToken)
                    usageStatusMessage = "내 사용량을 불러왔어요."
                } catch (error: AiRemoteException) {
                    usageStatusMessage = error.userMessage
                    myUsageSummary = null
                } catch (_: Exception) {
                    usageStatusMessage = "사용량 정보를 불러오지 못했어요."
                    myUsageSummary = null
                } finally {
                    isUsageLoading = false
                }
            } else {
                myUsageSummary = null
            }
        }
        Dialog(onDismissRequest = { isProfileOpen = false }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp, max = 560.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "프로필",
                        style = MaterialTheme.typography.titleLarge
                    )
                    myUsageSummary?.let { summary ->
                        Text("계정: ${summary.username}", style = MaterialTheme.typography.bodyLarge)
                        Text("요청 수: ${summary.requestCount}", style = MaterialTheme.typography.bodyMedium)
                        Text("총 토큰: ${summary.totalTokens}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "평균 토큰/요청: ${"%.1f".format(summary.avgTokensPerRequest)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "성공률: ${"%.1f".format(summary.successRate * 100)}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } ?: run {
                        Text(
                            text = "계정 정보를 불러오지 못했어요.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (isUsageLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("조회 중...")
                        }
                    }
                    usageStatusMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        TextButton(
                            onClick = {
                                if (usageAccessToken.isBlank()) {
                                    usageStatusMessage = "로그인 토큰이 없어요."
                                    return@TextButton
                                }
                                coroutineScope.launch {
                                    isUsageLoading = true
                                    try {
                                        myUsageSummary = aiAssistantService.fetchMyUsage(usageAccessToken)
                                        usageStatusMessage = "내 사용량 갱신 완료"
                                    } catch (error: AiRemoteException) {
                                        usageStatusMessage = error.userMessage
                                        myUsageSummary = null
                                    } catch (_: Exception) {
                                        usageStatusMessage = "사용량 조회에 실패했어요."
                                        myUsageSummary = null
                                    } finally {
                                        isUsageLoading = false
                                    }
                                }
                            }
                        ) {
                            Text("새로고침")
                        }
                        TextButton(
                            onClick = {
                                settingsPrefs.edit()
                                    .remove(SettingsKeys.KEY_USAGE_ACCESS_TOKEN)
                                    .apply()
                                isProfileOpen = false
                                onLogout()
                            }
                        ) {
                            Text("로그아웃")
                        }
                        TextButton(onClick = { isProfileOpen = false }) {
                            Text("닫기")
                        }
                    }
                }
            }
        }
    }

    if (isSettingsOpen) {
        Dialog(onDismissRequest = { isSettingsOpen = false }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp, max = 620.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "설정",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "시간을 말하지 않으면 기기 현재 시간이 자동으로 입력됩니다.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "알림 방식",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "이벤트 알림의 사운드/진동 동작을 선택",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AlarmModeTile(
                                label = "사운드",
                                selected = alarmAlertMode == SettingsKeys.ALARM_ALERT_MODE_SOUND,
                                onSelect = {
                                    alarmAlertMode = SettingsKeys.ALARM_ALERT_MODE_SOUND
                                    settingsPrefs.edit()
                                        .putString(SettingsKeys.KEY_ALARM_ALERT_MODE, alarmAlertMode)
                                        .apply()
                                },
                                modifier = Modifier.weight(1f)
                            )
                            AlarmModeTile(
                                label = "진동",
                                selected = alarmAlertMode == SettingsKeys.ALARM_ALERT_MODE_VIBRATE,
                                onSelect = {
                                    alarmAlertMode = SettingsKeys.ALARM_ALERT_MODE_VIBRATE
                                    settingsPrefs.edit()
                                        .putString(SettingsKeys.KEY_ALARM_ALERT_MODE, alarmAlertMode)
                                        .apply()
                                },
                                modifier = Modifier.weight(1f)
                            )
                            AlarmModeTile(
                                label = "무음",
                                selected = alarmAlertMode == SettingsKeys.ALARM_ALERT_MODE_SILENT,
                                onSelect = {
                                    alarmAlertMode = SettingsKeys.ALARM_ALERT_MODE_SILENT
                                    settingsPrefs.edit()
                                        .putString(SettingsKeys.KEY_ALARM_ALERT_MODE, alarmAlertMode)
                                        .apply()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        TextButton(onClick = { isSettingsOpen = false }) {
                            Text(text = "닫기")
                        }
                    }
                }
            }
        }
    }

    if (isSearchOpen) {
        Dialog(onDismissRequest = {
            searchViewModel.resetSearch()
            isSearchOpen = false
        }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 320.dp, max = 640.dp)
            ) {
                SearchDemoScreen(
                    searchViewModel = searchViewModel,
                    calendarViewModel = calendarViewModel,
                    onEventSelected = {
                        searchViewModel.resetSearch()
                        isSearchOpen = false
                    },
                    showSelectedDateSummary = false
                )
            }
        }
    }

    if (isAiSearchResultOpen) {
        Dialog(onDismissRequest = {
            aiSearchViewModel.resetSearch()
            isAiSearchResultOpen = false
            aiSearchSuggestion = null
        }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp, max = 640.dp)
            ) {
                AiSearchResultContent(
                    searchViewModel = aiSearchViewModel,
                    suggestion = aiSearchSuggestion,
                    zoneId = zoneId,
                    dateFormatter = dateFormatter,
                    onClose = {
                        aiSearchViewModel.resetSearch()
                        isAiSearchResultOpen = false
                        aiSearchSuggestion = null
                    },
                    onEventSelected = { eventDate ->
                        calendarViewModel.onDateSelected(eventDate)
                        aiSearchViewModel.resetSearch()
                        isAiSearchResultOpen = false
                        aiSearchSuggestion = null
                    }
                )
            }
        }
    }


    aiErrorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { aiErrorMessage = null },
            confirmButton = {
                TextButton(onClick = { aiErrorMessage = null }) {
                    Text(text = "확인")
                }
            },
            title = { Text(text = "AI 처리 안내") },
            text = { Text(text = message) }
        )
    }

    aiStatusMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { aiStatusMessage = null },
            confirmButton = {
                TextButton(onClick = { aiStatusMessage = null }) {
                    Text(text = "확인")
                }
            },
            title = { Text(text = "AI 처리 결과") },
            text = { Text(text = message) }
        )
    }

    pendingEdit?.let { editState ->
        AlertDialog(
            onDismissRequest = { pendingEdit = null },
            confirmButton = {
                Button(onClick = {
                    val validationResult = validateDraft(
                        draft = editState.draft,
                        inputTimeFormatter = inputTimeFormatter
                    )
                    if (validationResult.errorMessage != null) {
                        editError = validationResult.errorMessage
                        return@Button
                    }
                    val parsedTime = validationResult.parsedTime ?: return@Button
                    val summary = editState.draft.summary.trim()
                    val body = editState.draft.body.trim()
                    val labels = editState.draft.labelsText
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .take(MAX_LABELS_PER_EVENT)
                    val placeText = editState.draft.placeText.trim().ifBlank { null }
                    when (editState.action) {
                        CrudAction.Create -> {
                            calendarViewModel.addEvent(
                                date = editState.date,
                                time = parsedTime,
                                categoryId = editState.draft.categoryId.trim(),
                                isYearlyRecurring = editState.draft.isYearlyRecurring,
                                summary = summary,
                                body = body,
                                placeText = placeText,
                                labels = labels,
                                alarmEnabled = editState.alarmEnabled,
                                rawInputText = editState.rawInputText
                            )
                        }
                        CrudAction.Update -> {
                            val eventId = editState.eventId ?: return@Button
                            calendarViewModel.updateEvent(
                                eventId = eventId,
                                date = editState.date,
                                time = parsedTime,
                                categoryId = editState.draft.categoryId.trim(),
                                isYearlyRecurring = editState.draft.isYearlyRecurring,
                                summary = summary,
                                body = body,
                                placeText = placeText,
                                labels = labels,
                                alarmEnabled = editState.alarmEnabled,
                                rawInputText = editState.rawInputText
                            )
                        }
                        CrudAction.Delete -> return@Button
                    }
                    pendingEdit = null
                    editError = null
                }) {
                    Text(text = "확인")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingEdit = null
                    editError = null
                }) {
                    Text(text = "취소")
                }
            },
            title = {
                Text(
                    text = when (editState.action) {
                        CrudAction.Create -> "이벤트 생성 확인"
                        CrudAction.Update -> "이벤트 수정 확인"
                        CrudAction.Delete -> "이벤트 삭제 확인"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "날짜: ${editState.date.format(dateFormatter)}")
                    if (editState.action == CrudAction.Update) {
                        val changes = updateChangesPreview(editState)
                        Text(
                            text = "변경 예정",
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (changes.isEmpty()) {
                            Text(
                                text = "현재 변경된 항목이 없습니다.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                changes.forEach { change ->
                                    Text(
                                        text = change.label,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(
                                        text = "변경 전: ${change.before}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "변경 후: ${change.after}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = editState.draft.summary,
                        onValueChange = {
                            pendingEdit = editState.copy(
                                draft = editState.draft.copy(summary = it)
                            )
                            editError = null
                        },
                        label = { Text(text = "제목") },
                        trailingIcon = {
                            IconButton(
                                enabled = isOnline,
                                onClick = {
                                triggerVoiceAction(AiAction.RefineField(DraftField.Summary))
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Mic, contentDescription = "제목 음성 보완")
                            }
                        },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editState.draft.timeText,
                        onValueChange = {
                            pendingEdit = editState.copy(
                                draft = editState.draft.copy(timeText = it)
                            )
                            editError = null
                        },
                        label = { Text(text = "시간 (HH:mm)") },
                        trailingIcon = {
                            IconButton(
                                enabled = isOnline,
                                onClick = {
                                triggerVoiceAction(AiAction.RefineField(DraftField.Time))
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Mic, contentDescription = "시간 음성 보완")
                            }
                        },
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "알림",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Switch(
                            checked = editState.alarmEnabled,
                            onCheckedChange = { enabled ->
                                pendingEdit = editState.copy(alarmEnabled = enabled)
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "매년 반복",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Switch(
                            checked = editState.draft.isYearlyRecurring,
                            onCheckedChange = { enabled ->
                                pendingEdit = editState.copy(
                                    draft = editState.draft.copy(isYearlyRecurring = enabled)
                                )
                            }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { isCategoryMenuOpen = true }) {
                            val selectedCategory = CategoryDefaults.categories
                                .firstOrNull { it.id == editState.draft.categoryId }
                            val label = selectedCategory?.displayName ?: editState.draft.categoryId
                            Text(text = "카테고리: $label")
                        }
                        IconButton(
                            enabled = isOnline,
                            onClick = {
                            triggerVoiceAction(AiAction.RefineField(DraftField.Category))
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Mic, contentDescription = "카테고리 음성 보완")
                        }
                        DropdownMenu(
                            expanded = isCategoryMenuOpen,
                            onDismissRequest = { isCategoryMenuOpen = false }
                        ) {
                            CategoryDefaults.categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(text = category.displayName) },
                                    onClick = {
                                        pendingEdit = editState.copy(
                                            draft = editState.draft.copy(categoryId = category.id)
                                        )
                                        isCategoryMenuOpen = false
                                        editError = null
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = editState.draft.placeText,
                        onValueChange = {
                            pendingEdit = editState.copy(
                                draft = editState.draft.copy(placeText = it)
                            )
                            editError = null
                        },
                        label = { Text(text = "장소") },
                        trailingIcon = {
                            IconButton(
                                enabled = isOnline,
                                onClick = {
                                triggerVoiceAction(AiAction.RefineField(DraftField.Place))
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Mic, contentDescription = "장소 음성 보완")
                            }
                        },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editState.draft.labelsText,
                        onValueChange = {
                            pendingEdit = editState.copy(
                                draft = editState.draft.copy(labelsText = it)
                            )
                            editError = null
                        },
                        label = { Text(text = "라벨 (쉼표로 구분)") },
                        trailingIcon = {
                            IconButton(
                                enabled = isOnline,
                                onClick = {
                                triggerVoiceAction(AiAction.RefineField(DraftField.Labels))
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Mic, contentDescription = "라벨 음성 보완")
                            }
                        },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editState.draft.body,
                        onValueChange = {
                            pendingEdit = editState.copy(
                                draft = editState.draft.copy(body = it)
                            )
                            editError = null
                        },
                        label = { Text(text = "내용") },
                        trailingIcon = {
                            IconButton(
                                enabled = isOnline,
                                onClick = {
                                triggerVoiceAction(AiAction.RefineField(DraftField.Body))
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Mic, contentDescription = "내용 음성 보완")
                            }
                        }
                    )
                    val processingRefine = processingAiAction as? AiAction.RefineField
                    if (isAiProcessing && processingRefine != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "${fieldLabel(processingRefine.field)} 항목 AI 응답 기다리는 중...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    editError?.let { message ->
                        Text(text = message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }

    pendingDelete?.let { event ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            confirmButton = {
                Button(onClick = {
                    calendarViewModel.deleteEvent(event)
                    pendingDelete = null
                }) {
                    Text(text = "삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingDelete = null
                }) {
                    Text(text = "취소")
                }
            },
            title = { Text(text = "이벤트 삭제 확인") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val occurredAt = Instant.ofEpochMilli(event.occurredAt)
                        .atZone(zoneId)
                        .toLocalDateTime()
                    val labels = calendarViewModel.labelsByEventId[event.id].orEmpty()
                    Text(text = "날짜: ${occurredAt.format(dateFormatter)}")
                    Text(text = "시간: ${occurredAt.format(timeFormatter)}")
                    Text(text = "제목: ${event.summary}")
                    val categoryName = CategoryDefaults.categories
                        .firstOrNull { it.id == event.categoryId }
                        ?.displayName
                        ?: event.categoryId
                    Text(text = "카테고리: $categoryName")
                    Text(text = "장소: ${event.placeText ?: "없음"}")
                    Text(text = "라벨: ${labels.joinToString(", ").ifBlank { "없음" }}")
                    Text(text = "내용: ${event.body.ifBlank { "없음" }}")
                }
            }
        )
    }

    selectedEvent?.let { event ->
        AlertDialog(
            onDismissRequest = { selectedEvent = null },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val eventDateTime = Instant.ofEpochMilli(event.occurredAt)
                            .atZone(zoneId)
                            .toLocalDateTime()
                        val labels = calendarViewModel.labelsByEventId[event.id].orEmpty()
                        val existingAlarmEnabled = calendarViewModel.alarmEnabledByEventId[event.id] == true
                        val existingDraft = EventDraft(
                            summary = event.summary,
                            timeText = timeFormatter.format(eventDateTime),
                            isYearlyRecurring = event.isYearlyRecurring,
                            categoryId = event.categoryId,
                            placeText = event.placeText.orEmpty(),
                            body = event.body,
                            labelsText = labels.joinToString(", ")
                        )
                        pendingEdit = PendingEdit(
                            action = CrudAction.Update,
                            eventId = event.id,
                            date = eventDateTime.toLocalDate(),
                            draft = existingDraft,
                            alarmEnabled = existingAlarmEnabled,
                            originalDraft = existingDraft,
                            originalAlarmEnabled = existingAlarmEnabled,
                            rawInputText = calendarViewModel.rawInputByEventId[event.id]
                        )
                        isCategoryMenuOpen = false
                        editError = null
                        selectedEvent = null
                    }) {
                        Text(text = "수정")
                    }
                    TextButton(onClick = {
                        pendingDelete = event
                        selectedEvent = null
                    }) {
                        Text(text = "삭제")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedEvent = null }) {
                    Text(text = "닫기")
                }
            },
            title = {
                Text(text = "이벤트 상세")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val occurredAt = Instant.ofEpochMilli(event.occurredAt)
                        .atZone(zoneId)
                        .toLocalDateTime()
                    val labels = calendarViewModel.labelsByEventId[event.id].orEmpty()
                    val createdAt = Instant.ofEpochMilli(event.createdAt)
                        .atZone(zoneId)
                        .toLocalDateTime()
                    val updatedAt = Instant.ofEpochMilli(event.updatedAt)
                        .atZone(zoneId)
                        .toLocalDateTime()
                    val categoryName = CategoryDefaults.categories
                        .firstOrNull { it.id == event.categoryId }
                        ?.displayName
                        ?: event.categoryId
                    Text(text = "ID: ${event.id}")
                    Text(text = "카테고리: $categoryName")
                    Text(text = "일시: ${occurredAt.format(dateFormatter)} ${occurredAt.format(timeFormatter)}")
                    Text(text = "제목: ${event.summary}")
                    Text(text = "장소: ${event.placeText ?: "없음"}")
                    Text(text = "라벨: ${labels.joinToString(", ").ifBlank { "없음" }}")
                    Text(text = "내용: ${event.body.ifBlank { "없음" }}")
                    Text(text = "반복: ${if (event.isYearlyRecurring) "매년" else "반복 안 함"}")
                    Text(text = "원문: ${calendarViewModel.rawInputByEventId[event.id]?.ifBlank { "없음" } ?: "없음"}")
                    Text(text = "알림: ${if (calendarViewModel.alarmEnabledByEventId[event.id] == true) "켜짐" else "꺼짐"}")
                    Text(text = "생성: ${createdAt.format(dateFormatter)} ${createdAt.format(timeFormatter)}")
                    Text(text = "수정: ${updatedAt.format(dateFormatter)} ${updatedAt.format(timeFormatter)}")
                }
            }
        )
    }
}

@Composable
private fun ActionChoiceTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 0.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier
                    .padding(8.dp)
                    .size(20.dp)
            )
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun AlarmModeTile(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(56.dp)
            .clickable { onSelect() },
        shape = MaterialTheme.shapes.medium,
        tonalElevation = if (selected) 2.dp else 0.dp,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            }
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
private fun ActionPickerRow(
    firstLabel: String,
    firstIcon: androidx.compose.ui.graphics.vector.ImageVector,
    secondLabel: String,
    secondIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onFirstActionSelected: () -> Unit,
    onSecondActionSelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(12.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionChoiceTile(
            label = firstLabel,
            icon = firstIcon,
            onClick = onFirstActionSelected,
            modifier = Modifier.weight(1f)
        )
        HorizontalDivider(
            modifier = Modifier
                .height(48.dp)
                .width(1.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
        ActionChoiceTile(
            label = secondLabel,
            icon = secondIcon,
            onClick = onSecondActionSelected,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ActionPickerRowThree(
    firstLabel: String,
    firstIcon: androidx.compose.ui.graphics.vector.ImageVector,
    secondLabel: String,
    secondIcon: androidx.compose.ui.graphics.vector.ImageVector,
    thirdLabel: String,
    thirdIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onFirstActionSelected: () -> Unit,
    onSecondActionSelected: () -> Unit,
    onThirdActionSelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(12.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionChoiceTile(
            label = firstLabel,
            icon = firstIcon,
            onClick = onFirstActionSelected,
            modifier = Modifier.weight(1f)
        )
        HorizontalDivider(
            modifier = Modifier
                .height(48.dp)
                .width(1.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        )
        ActionChoiceTile(
            label = secondLabel,
            icon = secondIcon,
            onClick = onSecondActionSelected,
            modifier = Modifier.weight(1f)
        )
        HorizontalDivider(
            modifier = Modifier
                .height(48.dp)
                .width(1.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        )
        ActionChoiceTile(
            label = thirdLabel,
            icon = thirdIcon,
            onClick = onThirdActionSelected,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BottomBarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tint
        )
    }
}

private enum class InputTrigger {
    Keyboard,
    Microphone
}

private sealed interface AiAction {
    data object Input : AiAction
    data object Search : AiAction
    data object Modify : AiAction
    data class RefineField(val field: DraftField) : AiAction
}

private enum class CrudAction {
    Create,
    Update,
    Delete
}

private data class EventDraft(
    val summary: String,
    val timeText: String,
    val isYearlyRecurring: Boolean,
    val categoryId: String,
    val placeText: String,
    val body: String,
    val labelsText: String
)

private data class PendingEdit(
    val action: CrudAction,
    val eventId: String?,
    val date: LocalDate,
    val draft: EventDraft,
    val alarmEnabled: Boolean,
    val originalDraft: EventDraft? = null,
    val originalAlarmEnabled: Boolean? = null,
    val rawInputText: String? = null
)

private data class DraftValidationResult(
    val parsedTime: LocalTime?,
    val errorMessage: String?
)

private fun validateDraft(
    draft: EventDraft,
    inputTimeFormatter: DateTimeFormatter
): DraftValidationResult {
    val summary = draft.summary.trim()
    if (summary.isBlank()) {
        return DraftValidationResult(parsedTime = null, errorMessage = "제목을 입력하세요.")
    }
    if (draft.categoryId.isBlank()) {
        return DraftValidationResult(parsedTime = null, errorMessage = "카테고리를 선택하세요.")
    }
    if (draft.categoryId.trim() !in CategoryDefaults.categoryIds) {
        return DraftValidationResult(parsedTime = null, errorMessage = "유효한 카테고리를 선택하세요.")
    }
    val parsedTime = runCatching {
        LocalTime.parse(draft.timeText.trim(), inputTimeFormatter)
    }.getOrNull()
    if (parsedTime == null) {
        return DraftValidationResult(parsedTime = null, errorMessage = "시간 형식은 HH:mm 입니다.")
    }
    val body = draft.body.trim()
    if (body.isBlank()) {
        return DraftValidationResult(parsedTime = null, errorMessage = "내용을 입력하세요.")
    }
    return DraftValidationResult(parsedTime = parsedTime, errorMessage = null)
}

private fun valueOfField(draft: EventDraft, field: DraftField): String = when (field) {
    DraftField.Summary -> draft.summary
    DraftField.Time -> draft.timeText
    DraftField.Category -> draft.categoryId
    DraftField.Place -> draft.placeText
    DraftField.Labels -> draft.labelsText
    DraftField.Body -> draft.body
}

private fun applyFieldValue(draft: EventDraft, field: DraftField, value: String): EventDraft = when (field) {
    DraftField.Summary -> draft.copy(summary = value)
    DraftField.Time -> draft.copy(timeText = value)
    DraftField.Category -> draft.copy(
        categoryId = normalizeCategoryIdOrNull(value) ?: draft.categoryId
    )
    DraftField.Place -> draft.copy(placeText = value)
    DraftField.Labels -> draft.copy(labelsText = value)
    DraftField.Body -> draft.copy(body = value)
}

private fun normalizeCategoryIdOrNull(raw: String): String? {
    val normalized = raw.trim()
    if (normalized.isBlank()) return null
    val category = CategoryDefaults.categories.firstOrNull { candidate ->
        candidate.id.equals(normalized, ignoreCase = true) ||
            candidate.displayName.equals(normalized, ignoreCase = true)
    }
    return category?.id
}

private fun candidateEventsForDate(
    date: LocalDate,
    selectedDate: LocalDate,
    eventsByDate: Map<LocalDate, List<com.echo.echocalendar.data.local.EventEntity>>,
    eventsOfSelectedDate: List<com.echo.echocalendar.data.local.EventEntity>
): List<com.echo.echocalendar.data.local.EventEntity> {
    return if (date == selectedDate) {
        eventsOfSelectedDate
    } else {
        eventsByDate[date].orEmpty()
    }
}

private fun mergeDraftForUpdate(
    existing: EventDraft,
    suggestion: EventDraft,
    repeatYearlyOverride: Boolean? = null
): EventDraft {
    return existing.copy(
        summary = suggestion.summary.trim().ifBlank { existing.summary },
        timeText = suggestion.timeText.trim().ifBlank { existing.timeText },
        isYearlyRecurring = repeatYearlyOverride ?: existing.isYearlyRecurring,
        categoryId = suggestion.categoryId.trim().ifBlank { existing.categoryId },
        placeText = suggestion.placeText.trim().ifBlank { existing.placeText },
        body = suggestion.body.trim().ifBlank { existing.body },
        labelsText = suggestion.labelsText.trim().ifBlank { existing.labelsText }
    )
}

private suspend fun buildDraftForModify(
    transcript: String,
    existing: EventDraft,
    selectedDate: LocalDate,
    aiAssistantService: AiAssistantService,
    currentRawText: String?
): EventDraft {
    val patchResult = aiAssistantService.suggestModifyPatch(
        transcript = transcript,
        selectedDate = selectedDate,
        currentSummary = existing.summary,
        currentTimeText = existing.timeText,
        currentCategoryId = existing.categoryId,
        currentPlaceText = existing.placeText,
        currentBody = existing.body,
        currentLabelsText = existing.labelsText,
        currentRawText = currentRawText
    )
    val patch = patchResult.suggestion

    return existing.copy(
        summary = patch.summary ?: existing.summary,
        timeText = patch.timeText ?: existing.timeText,
        categoryId = patch.categoryId ?: existing.categoryId,
        placeText = patch.placeText ?: existing.placeText,
        body = patch.body ?: existing.body,
        labelsText = patch.labelsText ?: existing.labelsText
    )
}

private data class UpdateChange(
    val label: String,
    val before: String,
    val after: String
)

private fun updateChangesPreview(editState: PendingEdit): List<UpdateChange> {
    if (editState.action != CrudAction.Update) return emptyList()
    val original = editState.originalDraft ?: return emptyList()
    val now = editState.draft
    val changes = mutableListOf<UpdateChange>()

    if (original.summary.trim() != now.summary.trim()) {
        changes += UpdateChange(
            label = "제목",
            before = previewValue(original.summary),
            after = previewValue(now.summary)
        )
    }
    if (original.timeText.trim() != now.timeText.trim()) {
        changes += UpdateChange(
            label = "시간",
            before = previewValue(original.timeText),
            after = previewValue(now.timeText)
        )
    }
    if (original.isYearlyRecurring != now.isYearlyRecurring) {
        changes += UpdateChange(
            label = "반복",
            before = if (original.isYearlyRecurring) "\"매년\"" else "\"반복 안 함\"",
            after = if (now.isYearlyRecurring) "\"매년\"" else "\"반복 안 함\""
        )
    }
    if (original.categoryId.trim() != now.categoryId.trim()) {
        changes += UpdateChange(
            label = "카테고리",
            before = previewCategory(original.categoryId),
            after = previewCategory(now.categoryId)
        )
    }
    if (original.placeText.trim() != now.placeText.trim()) {
        changes += UpdateChange(
            label = "장소",
            before = previewValue(original.placeText),
            after = previewValue(now.placeText)
        )
    }
    if (original.labelsText.trim() != now.labelsText.trim()) {
        changes += UpdateChange(
            label = "라벨",
            before = previewValue(original.labelsText),
            after = previewValue(now.labelsText)
        )
    }
    if (original.body.trim() != now.body.trim()) {
        changes += UpdateChange(
            label = "내용",
            before = previewValue(original.body),
            after = previewValue(now.body)
        )
    }

    val originalAlarmEnabled = editState.originalAlarmEnabled
    if (originalAlarmEnabled != null && originalAlarmEnabled != editState.alarmEnabled) {
        changes += UpdateChange(
            label = "알림",
            before = if (originalAlarmEnabled) "켜짐" else "꺼짐",
            after = if (editState.alarmEnabled) "켜짐" else "꺼짐"
        )
    }
    return changes
}

private fun previewValue(value: String): String {
    val normalized = value.trim().ifBlank { "없음" }
    return "\"$normalized\""
}

private fun previewCategory(categoryId: String): String {
    val normalized = categoryId.trim().ifBlank { "other" }
    val display = CategoryDefaults.categories
        .firstOrNull { it.id == normalized }
        ?.displayName
        ?: normalized
    return "\"$display\""
}

private fun wantsAlarmFromTranscript(transcript: String): Boolean {
    if (transcript.isBlank()) return false
    val normalized = transcript.lowercase()
    return normalized.contains("알려줘") ||
        normalized.contains("알림") ||
        normalized.contains("알람") ||
        normalized.contains("깨워줘") ||
        normalized.contains("리마인드")
}

private fun wantsYearlyRecurringFromTranscript(transcript: String): Boolean {
    if (transcript.isBlank()) return false
    val normalized = transcript.lowercase()
    return normalized.contains("생일") ||
        normalized.contains("매년") ||
        normalized.contains("해마다") ||
        normalized.contains("매 해") ||
        normalized.contains("birthday") ||
        normalized.contains("anniversary")
}

private fun findBestTargetEvent(
    candidates: List<com.echo.echocalendar.data.local.EventEntity>,
    summaryHint: String,
    bodyHint: String
): com.echo.echocalendar.data.local.EventEntity? {
    if (candidates.isEmpty()) return null
    val summaryNorm = normalizeForMatch(summaryHint)
    if (summaryNorm.isNotBlank()) {
        val bySummary = candidates.firstOrNull { candidate ->
            val target = normalizeForMatch(candidate.summary)
            target.contains(summaryNorm) || summaryNorm.contains(target)
        }
        if (bySummary != null) return bySummary
    }

    val bodyTokens = normalizeForMatch(bodyHint)
        .split(Regex("\\s+"))
        .filter { it.length >= 2 }
    if (bodyTokens.isNotEmpty()) {
        val byBody = candidates.firstOrNull { candidate ->
            val haystack = normalizeForMatch("${candidate.summary} ${candidate.body} ${candidate.placeText.orEmpty()}")
            bodyTokens.any { token -> haystack.contains(token) }
        }
        if (byBody != null) return byBody
    }
    return null
}

private fun normalizeForMatch(source: String): String {
    return source
        .lowercase()
        .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun fieldLabel(field: DraftField): String = when (field) {
    DraftField.Summary -> "제목"
    DraftField.Time -> "시간"
    DraftField.Category -> "카테고리"
    DraftField.Place -> "장소"
    DraftField.Labels -> "라벨"
    DraftField.Body -> "내용"
}

private enum class HolidayKind {
    PublicHoliday,
    Commemorative
}

private data class HolidayBadge(
    val label: String,
    val kind: HolidayKind
)

private fun holidayBadge(
    date: LocalDate,
    syncedHolidayBadges: Map<LocalDate, HolidayBadge>
): HolidayBadge? {
    return syncedHolidayBadges[date]
}

private fun toHolidayBadgeMap(items: List<SyncedHoliday>): Map<LocalDate, HolidayBadge> {
    return items.associate { item ->
        item.date to HolidayBadge(
            label = item.label,
            kind = if (item.kind == SyncedHolidayKind.PUBLIC_HOLIDAY) {
                HolidayKind.PublicHoliday
            } else {
                HolidayKind.Commemorative
            }
        )
    }
}

@Composable
private fun AiSearchResultContent(
    searchViewModel: SearchViewModel,
    suggestion: AiSearchSuggestion?,
    zoneId: ZoneId,
    dateFormatter: DateTimeFormatter,
    onClose: () -> Unit,
    onEventSelected: (LocalDate) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "AI 검색 결과",
            style = MaterialTheme.typography.titleLarge
        )
        suggestion?.let { interpreted ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = buildAiSearchSummary(interpreted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (searchViewModel.isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.height(10.dp))
        }
        searchViewModel.error?.let { errorMessage ->
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
        Text(
            text = "결과 ${searchViewModel.results.size}건",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (searchViewModel.hasSearched && !searchViewModel.isLoading && searchViewModel.results.isEmpty()) {
            Text(
                text = "AI가 찾은 조건으로 일치하는 일정이 없어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(searchViewModel.results) { event ->
                val eventDate = Instant.ofEpochMilli(event.occurredAt).atZone(zoneId).toLocalDate()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEventSelected(eventDate) }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = event.summary,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = eventDate.format(dateFormatter),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onClose) {
                Text(text = "닫기")
            }
        }
    }
}

private fun buildAiSearchSummary(suggestion: AiSearchSuggestion): String {
    val conditions = mutableListOf<String>()
    suggestion.query.takeIf { it.isNotBlank() }?.let { conditions += "검색어: $it" }
    suggestion.dateFrom?.takeIf { it.isNotBlank() }?.let { conditions += "시작일: $it" }
    suggestion.dateTo?.takeIf { it.isNotBlank() }?.let { conditions += "종료일: $it" }
    if (suggestion.categoryIds.isNotEmpty()) {
        conditions += "카테고리: ${suggestion.categoryIds.joinToString(", ")}"
    }
    if (suggestion.labelNames.isNotEmpty()) {
        conditions += "라벨: ${suggestion.labelNames.joinToString(", ")}"
    }
    if (conditions.isEmpty()) return "해석된 조건이 없어 기본 검색 결과를 표시합니다."
    return "해석 조건 - ${conditions.joinToString(" / ")}"
}

@Composable
private fun dayTextColor(
    date: LocalDate,
    isInMonth: Boolean,
    holidayBadge: HolidayBadge?
): Color {
    val baseAlpha = if (isInMonth) 1f else 0.4f
    val baseColor = when {
        holidayBadge?.kind == HolidayKind.PublicHoliday || date.dayOfWeek == DayOfWeek.SUNDAY -> Color(0xFFE53935)
        date.dayOfWeek == DayOfWeek.SATURDAY -> Color(0xFF1E5AFF)
        else -> MaterialTheme.colorScheme.onSurface
    }
    return baseColor.copy(alpha = baseAlpha)
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate,
    eventsByDate: Map<LocalDate, List<com.echo.echocalendar.data.local.EventEntity>>,
    syncedHolidayBadges: Map<LocalDate, HolidayBadge>,
    onDateClick: (LocalDate) -> Unit
) {
    val firstDay = remember(month) { month.atDay(1) }
    val offset = remember(month) { firstDay.dayOfWeek.value % 7 }
    val gridStart = remember(month) { firstDay.minusDays(offset.toLong()) }
    val dates = remember(month) { List(42) { gridStart.plusDays(it.toLong()) } }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        dates.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    val isInMonth = date.month == month.month
                    val isToday = date == today
                    val isSelected = date == selectedDate
                    val scale = if (isSelected) 1.15f else 1f
                    val holidayBadge = holidayBadge(date, syncedHolidayBadges)
                    val border = if (isToday) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    }
                    val background = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.75f)
                            .padding(1.dp)
                            .scale(scale)
                            .zIndex(if (isSelected) 1f else 0f)
                            .clickable { onDateClick(date) },
                        border = border
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(background)
                                .padding(6.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = dayTextColor(date, isInMonth, holidayBadge)
                                )
                                val dayEvents = eventsByDate[date].orEmpty()
                                if (dayEvents.isNotEmpty()) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    Box(
                                        modifier = Modifier
                                            .height(6.dp)
                                            .aspectRatio(1f)
                                            .background(
                                                MaterialTheme.colorScheme.primary,
                                                shape = MaterialTheme.shapes.small
                                            )
                                    )
                                }
                            }
                            if (holidayBadge != null) {
                                Text(
                                    text = holidayBadge.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (holidayBadge.kind == HolidayKind.PublicHoliday) {
                                        Color(0xFFE53935)
                                    } else {
                                        MaterialTheme.colorScheme.secondary
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            val dayEvents = eventsByDate[date].orEmpty()
                            if (isInMonth && dayEvents.isNotEmpty()) {
                                val summary = dayEvents.first().summary
                                Column {
                                    Text(
                                        text = summary,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (dayEvents.size > 1) {
                                        Text(
                                            text = "+${dayEvents.size - 1}개",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthJumpDialog(
    initialMonth: YearMonth,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val years = remember {
        (1970..2080).toList()
    }
    var selectedYear by remember { mutableIntStateOf(initialMonth.year.coerceIn(1970, 2080)) }
    var selectedMonth by remember { mutableIntStateOf(initialMonth.monthValue) }
    var yearExpanded by remember { mutableStateOf(false) }
    var monthExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onConfirm(selectedYear, selectedMonth) }) {
                Text(text = "확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "취소")
            }
        },
        title = {
            Text(text = "년/월 선택")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    TextButton(onClick = { yearExpanded = true }) {
                        Text(text = "${selectedYear}년")
                    }
                    DropdownMenu(
                        expanded = yearExpanded,
                        onDismissRequest = { yearExpanded = false }
                    ) {
                        years.forEach { year ->
                            DropdownMenuItem(
                                text = { Text(text = "${year}년") },
                                onClick = {
                                    selectedYear = year
                                    yearExpanded = false
                                }
                            )
                        }
                    }
                }
                Box {
                    TextButton(onClick = { monthExpanded = true }) {
                        Text(text = "${selectedMonth}월")
                    }
                    DropdownMenu(
                        expanded = monthExpanded,
                        onDismissRequest = { monthExpanded = false }
                    ) {
                        (1..12).forEach { month ->
                            DropdownMenuItem(
                                text = { Text(text = "${month}월") },
                                onClick = {
                                    selectedMonth = month
                                    monthExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    )
}

private fun speechRecognitionErrorMessage(error: Int): String = when (error) {
    SpeechRecognizer.ERROR_AUDIO -> "마이크 오디오 입력 오류가 발생했어요. 다시 시도해 주세요."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한이 없어 음성 인식을 할 수 없어요."
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트워크 문제로 음성 인식에 실패했어요."
    SpeechRecognizer.ERROR_NO_MATCH -> "음성을 인식하지 못했어요. 또렷하게 다시 말해 주세요."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "음성 인식기가 사용 중이에요. 잠시 후 다시 시도해 주세요."
    SpeechRecognizer.ERROR_SERVER -> "음성 인식 서버 오류가 발생했어요. 잠시 후 다시 시도해 주세요."
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "음성이 감지되지 않았어요. 마이크에 대고 다시 말해 주세요."
    else -> "음성을 텍스트로 변환하지 못했어요. 다시 시도해 주세요. (code=$error)"
}
