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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
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
    val initialMonth = remember { YearMonth.from(calendarViewModel.selectedDate) }
    var shownMonth by remember { mutableStateOf(initialMonth) }
    var isJumpDialogOpen by remember { mutableStateOf(false) }
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
    }

    Column(modifier = Modifier.padding(16.dp)) {
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
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val month = initialMonth.plusMonths((page - 1200).toLong())
            MonthGrid(
                month = month,
                today = today,
                selectedDate = calendarViewModel.selectedDate,
                onDateClick = calendarViewModel::onDateSelected
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "선택된 날짜: ${calendarViewModel.selectedDate.format(dateFormatter)}",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column {
            if (calendarViewModel.eventsOfDay.isEmpty()) {
                Text(text = "해당 날짜의 이벤트가 없습니다.")
            } else {
                calendarViewModel.eventsOfDay.forEach { event ->
                    val eventDate = Instant.ofEpochMilli(event.occurredAt)
                        .atZone(zoneId)
                        .toLocalDate()
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = event.summary, style = MaterialTheme.typography.titleSmall)
                            Text(text = eventDate.format(dateFormatter))
                            event.placeText?.let { place ->
                                Text(text = place)
                            }
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
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate,
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
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clickable { onDateClick(date) },
                        border = border
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(background),
                            contentAlignment = Alignment.TopStart
                        ) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                modifier = Modifier
                                    .padding(6.dp)
                                    .alpha(if (isInMonth) 1f else 0.4f),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
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
