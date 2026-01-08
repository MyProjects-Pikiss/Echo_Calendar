package com.echo.echocalendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.echo.echocalendar.ui.demo.CalendarViewModel
import com.echo.echocalendar.ui.demo.CalendarViewModelFactory
import com.echo.echocalendar.ui.demo.MonthCalendarScreen
import com.echo.echocalendar.ui.demo.SearchViewModel
import com.echo.echocalendar.ui.demo.SearchDemoScreen
import com.echo.echocalendar.ui.demo.SearchViewModelFactory
import com.echo.echocalendar.ui.theme.EchoCalendarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EchoCalendarTheme {
                val container = (application as EchoCalendarApplication).container
                val searchViewModel = viewModel<SearchViewModel>(
                    factory = SearchViewModelFactory(container.searchEventsUseCase)
                )
                val calendarViewModel = viewModel<CalendarViewModel>(
                    factory = CalendarViewModelFactory(container.getEventsByDateUseCase)
                )
                var selectedTab by remember { mutableIntStateOf(0) }
                Column {
                    TabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text(text = "월간 캘린더") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text(text = "검색 데모") }
                        )
                    }
                    when (selectedTab) {
                        0 -> MonthCalendarScreen(calendarViewModel = calendarViewModel)
                        else -> SearchDemoScreen(
                            searchViewModel = searchViewModel,
                            calendarViewModel = calendarViewModel
                        )
                    }
                }
            }
        }
    }
}
