package com.echo.echocalendar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.echo.echocalendar.alarm.EventAlarmScheduler
import com.echo.echocalendar.ui.demo.AiAssistantService
import com.echo.echocalendar.ui.demo.AiRemoteException
import com.echo.echocalendar.ui.demo.CalendarViewModel
import com.echo.echocalendar.ui.demo.CalendarViewModelFactory
import com.echo.echocalendar.ui.demo.MonthCalendarScreen
import com.echo.echocalendar.ui.demo.SearchViewModel
import com.echo.echocalendar.ui.demo.SearchViewModelFactory
import com.echo.echocalendar.ui.theme.EchoCalendarTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val pendingOpenEventId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingOpenEventId.value = extractOpenEventId(intent)
        maybeRequestNotificationPermission()
        enableEdgeToEdge()
        setContent {
            EchoCalendarTheme {
                val container = (application as EchoCalendarApplication).container
                val calendarViewModel = viewModel<CalendarViewModel>(
                    factory = CalendarViewModelFactory(
                        container.getEventsByDateUseCase,
                        container.getEventByIdUseCase,
                        container.getEventsByMonthUseCase,
                        container.getLabelsForEventUseCase,
                        container.saveEventUseCase,
                        container.deleteEventUseCase,
                        container.updateEventUseCase,
                        container.database.eventAlarmDao(),
                        container.eventAlarmScheduler,
                        container.database.eventRawInputDao()
                    )
                )
                val searchViewModel = viewModel<SearchViewModel>(
                    factory = SearchViewModelFactory(container.searchEventsUseCase)
                )
                val aiSearchViewModel = viewModel<SearchViewModel>(
                    key = "ai_search_view_model",
                    factory = SearchViewModelFactory(container.searchEventsUseCase)
                )
                val isOnlineState = rememberIsOnline()
                val context = LocalContext.current
                val prefs = remember(context) {
                    context.getSharedPreferences(SettingsKeys.SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
                }
                var isAuthenticated by remember {
                    mutableStateOf(
                        prefs.getString(SettingsKeys.KEY_USAGE_ACCESS_TOKEN, "").orEmpty().isNotBlank()
                    )
                }
                if (isAuthenticated) {
                    MonthCalendarScreen(
                        calendarViewModel = calendarViewModel,
                        searchViewModel = searchViewModel,
                        aiSearchViewModel = aiSearchViewModel,
                        aiAssistantService = container.aiAssistantService,
                        isOnline = isOnlineState.value,
                        openEventId = pendingOpenEventId.value,
                        onOpenEventHandled = {
                            pendingOpenEventId.value = null
                        },
                        onLogout = {
                            isAuthenticated = false
                        }
                    )
                } else {
                    LoginRequiredScreen(
                        aiAssistantService = container.aiAssistantService,
                        isOnline = isOnlineState.value,
                        onLoginSuccess = { token ->
                            prefs.edit().putString(SettingsKeys.KEY_USAGE_ACCESS_TOKEN, token).apply()
                            isAuthenticated = true
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOpenEventId.value = extractOpenEventId(intent)
    }

    private fun maybeRequestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
    }

    private fun extractOpenEventId(intent: Intent?): String? {
        return intent?.getStringExtra(EventAlarmScheduler.EXTRA_EVENT_ID)?.trim()?.takeIf { it.isNotBlank() }
    }
}

@Composable
private fun LoginRequiredScreen(
    aiAssistantService: AiAssistantService,
    isOnline: Boolean,
    onLoginSuccess: (String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("로그인 / 회원가입", style = MaterialTheme.typography.headlineMedium)
            Text("처음이면 회원가입 후 바로 로그인됩니다.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("아이디") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("비밀번호") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    if (!isOnline) {
                        message = "오프라인 상태에서는 로그인할 수 없어요."
                        return@Button
                    }
                    val id = username.trim()
                    val pw = password
                    if (id.isBlank() || pw.isBlank()) {
                        message = "아이디와 비밀번호를 입력해 주세요."
                        return@Button
                    }
                    scope.launch {
                        loading = true
                        message = null
                        try {
                            val token = aiAssistantService.loginUsage(id, pw)
                            aiAssistantService.fetchMyUsage(token)
                            onLoginSuccess(token)
                        } catch (error: AiRemoteException) {
                            message = error.userMessage
                        } catch (error: Exception) {
                            message = error.message ?: "로그인에 실패했어요."
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("로그인")
            }
            OutlinedButton(
                onClick = {
                    if (!isOnline) {
                        message = "오프라인 상태에서는 회원가입할 수 없어요."
                        return@OutlinedButton
                    }
                    val id = username.trim()
                    val pw = password
                    if (id.isBlank() || pw.isBlank()) {
                        message = "아이디와 비밀번호를 입력해 주세요."
                        return@OutlinedButton
                    }
                    scope.launch {
                        loading = true
                        message = null
                        try {
                            val token = aiAssistantService.signupUsage(id, pw)
                            aiAssistantService.fetchMyUsage(token)
                            onLoginSuccess(token)
                        } catch (error: AiRemoteException) {
                            message = error.userMessage
                        } catch (error: Exception) {
                            message = error.message ?: "회원가입에 실패했어요."
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("회원가입")
            }
            if (loading) {
                CircularProgressIndicator()
            }
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun rememberIsOnline(): State<Boolean> {
    val context = LocalContext.current
    val connectivityManager = remember {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    val isOnline = remember {
        mutableStateOf(connectivityManager.isCurrentlyOnline())
    }

    DisposableEffect(connectivityManager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOnline.value = true
            }

            override fun onLost(network: Network) {
                isOnline.value = connectivityManager.isCurrentlyOnline()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                isOnline.value = networkCapabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET
                )
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        onDispose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    return isOnline
}

private fun ConnectivityManager.isCurrentlyOnline(): Boolean {
    val activeNetwork = activeNetwork ?: return false
    val capabilities = getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
