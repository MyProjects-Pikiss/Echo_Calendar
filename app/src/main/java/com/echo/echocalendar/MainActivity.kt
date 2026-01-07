package com.echo.echocalendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.echo.echocalendar.ui.demo.CalendarViewModelFactory
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
                val searchViewModel = viewModel(
                    factory = SearchViewModelFactory(container.searchEventsUseCase)
                )
                val calendarViewModel = viewModel(
                    factory = CalendarViewModelFactory(container.getEventsByDateUseCase)
                )
                SearchDemoScreen(
                    searchViewModel = searchViewModel,
                    calendarViewModel = calendarViewModel
                )
            }
        }
    }
}
