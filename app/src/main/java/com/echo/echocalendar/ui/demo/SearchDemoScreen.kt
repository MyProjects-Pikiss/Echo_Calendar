package com.echo.echocalendar.ui.demo

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.echo.echocalendar.data.local.CategoryDefaults
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchDemoScreen(
    searchViewModel: SearchViewModel,
    calendarViewModel: CalendarViewModel,
    onEventSelected: (() -> Unit)? = null,
    showSelectedDateSummary: Boolean = true
) {
    val zoneId = remember { ZoneId.of("Asia/Seoul") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val context = LocalContext.current

    fun showDatePicker(current: String?, onPicked: (String) -> Unit) {
        val initialDate = runCatching { LocalDate.parse(current) }.getOrNull() ?: LocalDate.now(zoneId)
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                onPicked(LocalDate.of(year, month + 1, dayOfMonth).format(dateFormatter))
            },
            initialDate.year,
            initialDate.monthValue - 1,
            initialDate.dayOfMonth
        ).show()
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "검색 데모",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.material3.OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = searchViewModel.query,
                onValueChange = searchViewModel::onQueryChange,
                label = { Text("검색어") }
            )
            Button(onClick = searchViewModel::onSearchSubmit) {
                Text("검색")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "날짜 필터", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    showDatePicker(searchViewModel.dateFromFilter, searchViewModel::onDateFromFilterChange)
                }
            ) {
                Text(searchViewModel.dateFromFilter ?: "시작일 선택")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    showDatePicker(searchViewModel.dateToFilter, searchViewModel::onDateToFilterChange)
                }
            ) {
                Text(searchViewModel.dateToFilter ?: "종료일 선택")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "카테고리 필터",
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategoryDefaults.categories.forEach { category ->
                FilterChip(
                    selected = category.id in searchViewModel.categoryFilters,
                    onClick = { searchViewModel.toggleCategoryFilter(category.id) },
                    label = { Text(category.displayName) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = searchViewModel::onSearchSubmit) {
                Text("필터 적용")
            }
            Button(onClick = searchViewModel::clearFilters) {
                Text("필터 초기화")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        if (searchViewModel.isLoading) {
            CircularProgressIndicator(modifier = Modifier.width(24.dp))
        }
        searchViewModel.error?.let { errorMessage ->
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error
            )
        }
        val filterSummary = buildList {
            searchViewModel.dateFromFilter?.let { add("시작일: $it") }
            searchViewModel.dateToFilter?.let { add("종료일: $it") }
            if (searchViewModel.categoryFilters.isNotEmpty()) {
                val labels = searchViewModel.categoryFilters.map { filter ->
                    CategoryDefaults.categories.firstOrNull { it.id == filter }?.displayName ?: filter
                }
                add("카테고리: ${labels.joinToString(", ")}")
            }
        }
        if (filterSummary.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "AI/사용자 필터 적용됨",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            filterSummary.forEach { summary ->
                Text(text = "• $summary", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "검색 결과",
            style = MaterialTheme.typography.titleMedium
        )
        LazyColumn {
            items(searchViewModel.results) { event ->
                val date = Instant.ofEpochMilli(event.occurredAt).atZone(zoneId).toLocalDate()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            calendarViewModel.onDateSelected(date)
                            onEventSelected?.invoke()
                        }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = event.summary, style = MaterialTheme.typography.titleSmall)
                        Text(text = date.format(dateFormatter))
                        event.placeText?.let { place ->
                            Text(text = place)
                        }
                    }
                }
            }
        }
        if (showSelectedDateSummary) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "선택된 날짜: ${calendarViewModel.selectedDate.format(dateFormatter)}",
                style = MaterialTheme.typography.titleMedium
            )
            Column(modifier = Modifier.padding(top = 8.dp)) {
                if (calendarViewModel.eventsOfDay.isEmpty()) {
                    Text("해당 날짜의 이벤트가 없습니다.")
                } else {
                    calendarViewModel.eventsOfDay.forEach { event ->
                        Text("• ${event.summary}")
                    }
                }
            }
        }
    }
}
