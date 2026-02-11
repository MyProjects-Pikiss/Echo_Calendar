package com.echo.echocalendar.ui.demo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import com.echo.echocalendar.data.local.CategoryDefaults
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MonthCalendarScreen(
    calendarViewModel: CalendarViewModel,
    searchViewModel: SearchViewModel,
    isOnline: Boolean
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
    var pendingAiAction by remember { mutableStateOf<AiAction?>(null) }
    var aiErrorMessage by remember { mutableStateOf<String?>(null) }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            aiErrorMessage = "음성 인식이 취소되었어요."
            return@rememberLauncherForActivityResult
        }
        val matches = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            .orEmpty()
        val transcript = matches.firstOrNull()?.trim().orEmpty()
        if (transcript.isBlank()) {
            aiErrorMessage = "음성을 텍스트로 변환하지 못했어요. 다시 시도해 주세요."
            return@rememberLauncherForActivityResult
        }
        when (pendingAiAction) {
            AiAction.Input -> {
                val suggestion = AiAssistantInterpreter.suggestInput(
                    transcript = transcript,
                    selectedDate = calendarViewModel.selectedDate
                )
                pendingEdit = PendingEdit(
                    action = CrudAction.Create,
                    eventId = null,
                    date = suggestion.date,
                    draft = EventDraft(
                        summary = suggestion.summary,
                        timeText = suggestion.timeText,
                        categoryId = suggestion.categoryId,
                        placeText = suggestion.placeText,
                        body = suggestion.body,
                        labelsText = suggestion.labelsText
                    )
                )
                isCategoryMenuOpen = false
                editError = suggestion.missingRequired
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(prefix = "필수 항목을 채워주세요: ", separator = ", ")
            }
            AiAction.Search -> {
                val suggestion = AiAssistantInterpreter.suggestSearchQuery(transcript)
                if (suggestion.query.isBlank()) {
                    aiErrorMessage = "검색어를 만들지 못했어요. 다시 말해 주세요."
                    return@rememberLauncherForActivityResult
                }
                searchViewModel.onQueryChange(suggestion.query)
                searchViewModel.onSearchSubmit()
                isSearchOpen = true
            }
            null -> Unit
        }
        pendingAiAction = null
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            aiErrorMessage = "음성 인식을 사용하려면 마이크 권한이 필요해요."
            pendingAiAction = null
            return@rememberLauncherForActivityResult
        }
        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "말씀하신 내용을 AI 입력/검색으로 처리할게요")
        }
        speechRecognizerLauncher.launch(speechIntent)
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

    LaunchedEffect(selectedEvent?.id) {
        selectedEvent?.let { event ->
            calendarViewModel.loadLabelsForEvent(event.id)
        }
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
                horizontalArrangement = Arrangement.SpaceBetween,
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
                                        timeText = "09:00",
                                        categoryId = CategoryDefaults.categories.first().id,
                                        placeText = "",
                                        body = "",
                                        labelsText = ""
                                    )
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
                        ActionPickerRow(
                            firstLabel = "AI 입력",
                            firstIcon = Icons.Default.Mic,
                            secondLabel = "AI 검색",
                            secondIcon = Icons.Default.Search,
                            onFirstActionSelected = {
                                isActionPickerOpen = false
                                pendingAiAction = AiAction.Input
                                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            onSecondActionSelected = {
                                isActionPickerOpen = false
                                pendingAiAction = AiAction.Search
                                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        )
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
                Divider(
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
                    val placeText = editState.draft.placeText.trim().ifBlank { null }
                    when (editState.action) {
                        CrudAction.Create -> {
                            calendarViewModel.addEvent(
                                date = editState.date,
                                time = parsedTime,
                                categoryId = editState.draft.categoryId,
                                summary = summary,
                                body = body,
                                placeText = placeText,
                                labels = labels
                            )
                        }
                        CrudAction.Update -> {
                            val eventId = editState.eventId ?: return@Button
                            calendarViewModel.updateEvent(
                                eventId = eventId,
                                date = editState.date,
                                time = parsedTime,
                                categoryId = editState.draft.categoryId,
                                summary = summary,
                                body = body,
                                placeText = placeText,
                                labels = labels
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
                    OutlinedTextField(
                        value = editState.draft.summary,
                        onValueChange = {
                            pendingEdit = editState.copy(
                                draft = editState.draft.copy(summary = it)
                            )
                            editError = null
                        },
                        label = { Text(text = "제목") },
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
                        singleLine = true
                    )
                    Box {
                        TextButton(onClick = { isCategoryMenuOpen = true }) {
                            val selectedCategory = CategoryDefaults.categories
                                .firstOrNull { it.id == editState.draft.categoryId }
                            val label = selectedCategory?.displayName ?: editState.draft.categoryId
                            Text(text = "카테고리: $label")
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
                        label = { Text(text = "내용") }
                    )
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
                        pendingEdit = PendingEdit(
                            action = CrudAction.Update,
                            eventId = event.id,
                            date = eventDateTime.toLocalDate(),
                            draft = EventDraft(
                                summary = event.summary,
                                timeText = timeFormatter.format(eventDateTime),
                                categoryId = event.categoryId,
                                placeText = event.placeText.orEmpty(),
                                body = event.body,
                                labelsText = labels.joinToString(", ")
                            )
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
        Divider(
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

private enum class AiAction {
    Input,
    Search
}

private enum class CrudAction {
    Create,
    Update,
    Delete
}

private data class EventDraft(
    val summary: String,
    val timeText: String,
    val categoryId: String,
    val placeText: String,
    val body: String,
    val labelsText: String
)

private data class PendingEdit(
    val action: CrudAction,
    val eventId: String?,
    val date: LocalDate,
    val draft: EventDraft
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

private val fixedHolidays = mapOf(
    MonthDay.of(1, 1) to "신정",
    MonthDay.of(3, 1) to "삼일절",
    MonthDay.of(5, 5) to "어린이날",
    MonthDay.of(6, 6) to "현충일",
    MonthDay.of(8, 15) to "광복절",
    MonthDay.of(10, 3) to "개천절",
    MonthDay.of(10, 9) to "한글날",
    MonthDay.of(12, 25) to "크리스마스"
)

private fun holidayLabel(date: LocalDate): String? = fixedHolidays[MonthDay.from(date)]

@Composable
private fun dayTextColor(
    date: LocalDate,
    isInMonth: Boolean,
    holidayLabel: String?
): Color {
    val baseAlpha = if (isInMonth) 1f else 0.4f
    val baseColor = when {
        holidayLabel != null || date.dayOfWeek == DayOfWeek.SUNDAY -> Color(0xFFE53935)
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
                    val holidayLabel = holidayLabel(date)
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
                                    color = dayTextColor(date, isInMonth, holidayLabel)
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
                            if (holidayLabel != null) {
                                Text(
                                    text = holidayLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFE53935),
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
        val currentYear = initialMonth.year
        (currentYear - 50..currentYear + 50).toList()
    }
    var selectedYear by remember { mutableIntStateOf(initialMonth.year) }
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
