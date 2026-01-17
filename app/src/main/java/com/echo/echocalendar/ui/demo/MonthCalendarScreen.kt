package com.echo.echocalendar.ui.demo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MonthCalendarScreen(
    calendarViewModel: CalendarViewModel
) {
    val zoneId = remember { ZoneId.of("Asia/Seoul") }
    val today = remember { LocalDate.now(zoneId) }
    val monthFormatter = remember { DateTimeFormatter.ofPattern("yyyy년 M월") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val initialMonth = remember { YearMonth.from(calendarViewModel.selectedDate) }
    var shownMonth by remember { mutableStateOf(initialMonth) }
    var isJumpDialogOpen by remember { mutableStateOf(false) }
    var selectedEvent by remember { mutableStateOf<com.echo.echocalendar.data.local.EventEntity?>(null) }
    val pagerState = rememberPagerState(initialPage = 1200, pageCount = { 2400 })

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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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

    selectedEvent?.let { event ->
        AlertDialog(
            onDismissRequest = { selectedEvent = null },
            confirmButton = {
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
                    val createdAt = Instant.ofEpochMilli(event.createdAt)
                        .atZone(zoneId)
                        .toLocalDateTime()
                    val updatedAt = Instant.ofEpochMilli(event.updatedAt)
                        .atZone(zoneId)
                        .toLocalDateTime()
                    Text(text = "ID: ${event.id}")
                    Text(text = "카테고리: ${event.categoryId}")
                    Text(text = "일시: ${occurredAt.format(dateFormatter)} ${occurredAt.format(timeFormatter)}")
                    Text(text = "제목: ${event.summary}")
                    Text(text = "장소: ${event.placeText ?: "없음"}")
                    Text(text = "내용: ${event.body.ifBlank { "없음" }}")
                    Text(text = "생성: ${createdAt.format(dateFormatter)} ${createdAt.format(timeFormatter)}")
                    Text(text = "수정: ${updatedAt.format(dateFormatter)} ${updatedAt.format(timeFormatter)}")
                }
            }
        )
    }
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
