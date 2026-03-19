package com.echo.echocalendar

import android.Manifest
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PackageInfoFlags
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
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
import com.echo.echocalendar.ui.demo.AppUpdateInfo
import com.echo.echocalendar.ui.demo.CalendarViewModel
import com.echo.echocalendar.ui.demo.CalendarViewModelFactory
import com.echo.echocalendar.ui.demo.MonthCalendarScreen
import com.echo.echocalendar.ui.demo.SearchViewModel
import com.echo.echocalendar.ui.demo.SearchViewModelFactory
import com.echo.echocalendar.ui.theme.EchoCalendarTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private data class AppVersionInfo(
        val code: Int,
        val name: String
    )

    private val pendingOpenEventId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingOpenEventId.value = extractOpenEventId(intent)
        maybeRequestNotificationPermission()
        maybeRequestExactAlarmPermission()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.parseColor("#2F3338")),
            navigationBarStyle = SystemBarStyle.auto(
                AndroidColor.parseColor("#F2F4F8"),
                AndroidColor.parseColor("#121212")
            )
        )
        setContent {
            val context = LocalContext.current
            val prefs = remember(context) {
                SecureSettings.getPreferences(context)
            }
            var themeMode by remember {
                mutableStateOf(
                    prefs.getString(SettingsKeys.KEY_THEME_MODE, SettingsKeys.THEME_MODE_SYSTEM)
                        ?.trim()
                        .orEmpty()
                        .ifBlank { SettingsKeys.THEME_MODE_SYSTEM }
                )
            }
            val useDarkTheme = when (themeMode) {
                SettingsKeys.THEME_MODE_DARK -> true
                SettingsKeys.THEME_MODE_LIGHT -> false
                else -> isSystemInDarkTheme()
            }

            EchoCalendarTheme(darkTheme = useDarkTheme) {
                val container = (application as EchoCalendarApplication).container
                val calendarViewModel = viewModel<CalendarViewModel>(
                    factory = CalendarViewModelFactory(
                        container.getEventsByDateUseCase,
                        container.getEventByIdUseCase,
                        container.getEventsByMonthUseCase,
                        container.getAllEventsUseCase,
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
                var isAuthenticated by remember {
                    mutableStateOf(
                        prefs.getString(SettingsKeys.KEY_USAGE_ACCESS_TOKEN, "").orEmpty().isNotBlank()
                    )
                }
                var appUpdateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
                var appUpdateChecked by remember { mutableStateOf(false) }
                var currentVersionInfo by remember { mutableStateOf(AppVersionInfo(0, "")) }

                LaunchedEffect(Unit) {
                    currentVersionInfo = currentAppVersionInfo()
                    if (!isOnlineState.value) {
                        appUpdateInfo = null
                        appUpdateChecked = true
                        return@LaunchedEffect
                    }
                    appUpdateInfo = container.aiAssistantService.checkAppUpdate(currentVersionInfo.code)
                    appUpdateChecked = true
                }

                val updateInfo = appUpdateInfo
                if (updateInfo != null && updateInfo.hasUpdate) {
                    AppUpdateRequiredScreen(
                        appUpdateInfo = updateInfo,
                        currentVersionCode = currentVersionInfo.code,
                        currentVersionName = currentVersionInfo.name,
                        onDownloadClick = {
                            val apkUrl = updateInfo.apkDownloadUrl
                            if (apkUrl.isNullOrBlank()) {
                                Toast.makeText(context, "업데이트 링크가 아직 준비되지 않았어요.", Toast.LENGTH_SHORT).show()
                                return@AppUpdateRequiredScreen
                            }
                            openExternalLink(context, apkUrl)
                        },
                        onSkipClick = if (updateInfo.required) {
                            null
                        } else {
                            { appUpdateInfo = null }
                        }
                    )
                } else if (!appUpdateChecked) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .systemBarsPadding()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "버전 확인 중...",
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }
                } else if (isAuthenticated) {
                    MonthCalendarScreen(
                        calendarViewModel = calendarViewModel,
                        searchViewModel = searchViewModel,
                        aiSearchViewModel = aiSearchViewModel,
                        aiAssistantService = container.aiAssistantService,
                        isOnline = isOnlineState.value,
                        themeMode = themeMode,
                        onThemeModeChange = { nextMode ->
                            themeMode = nextMode
                            prefs.edit().putString(SettingsKeys.KEY_THEME_MODE, nextMode).apply()
                        },
                        appVersionName = currentVersionInfo.name,
                        appVersionCode = currentVersionInfo.code,
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
                        signupEnabled = BuildConfig.ALLOW_SIGNUP,
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

    private fun maybeRequestExactAlarmPermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (alarmManager.canScheduleExactAlarms()) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { startActivity(intent) }
    }

    private fun extractOpenEventId(intent: Intent?): String? {
        return intent?.getStringExtra(EventAlarmScheduler.EXTRA_EVENT_ID)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun currentAppVersionInfo(): AppVersionInfo {
        val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }
        val versionName = packageInfo.versionName?.trim().orEmpty().ifBlank { versionCode.toString() }
        return AppVersionInfo(code = versionCode, name = versionName)
    }
}

@Composable
private fun AppUpdateRequiredScreen(
    appUpdateInfo: AppUpdateInfo,
    currentVersionCode: Int,
    currentVersionName: String,
    onDownloadClick: () -> Unit,
    onSkipClick: (() -> Unit)?
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("업데이트가 필요합니다", style = MaterialTheme.typography.headlineMedium)
            Text(
                "새 버전이 출시되었습니다 (현재 버전: $currentVersionName / 새 버전: ${appUpdateInfo.latestVersionName})",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "현재 코드: $currentVersionCode, 최신 코드: ${appUpdateInfo.latestVersionCode}",
                style = MaterialTheme.typography.bodyMedium
            )
            if (appUpdateInfo.required) {
                Text("현재 버전은 더 이상 지원되지 않아 업데이트 후 사용 가능합니다.")
            }
            Button(
                onClick = onDownloadClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("APK 다운로드")
            }
            if (onSkipClick != null) {
                OutlinedButton(
                    onClick = onSkipClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("나중에")
                }
            }
        }
    }
}

@Composable
private fun LoginRequiredScreen(
    aiAssistantService: AiAssistantService,
    isOnline: Boolean,
    signupEnabled: Boolean,
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
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("로그인", style = MaterialTheme.typography.headlineMedium)
            Text(
                if (signupEnabled) {
                    "처음이면 회원가입 후 바로 로그인됩니다."
                } else {
                    "회원가입은 현재 임시 중단 상태입니다."
                },
                style = MaterialTheme.typography.bodyMedium
            )
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
                    if (!signupEnabled) {
                        message = "회원가입이 현재 임시 중단 상태입니다."
                        return@OutlinedButton
                    }
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
                enabled = !loading && signupEnabled,
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

private fun openExternalLink(context: Context, rawUrl: String) {
    val uri = runCatching { Uri.parse(rawUrl.trim()) }.getOrNull() ?: return
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "링크를 열 수 있는 앱이 없어요.", Toast.LENGTH_SHORT).show()
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
