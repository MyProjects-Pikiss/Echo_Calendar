package com.echo.echocalendar

import android.app.Application
import com.echo.echocalendar.data.seed.DebugSeedData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EchoCalendarApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        if (BuildConfig.DEBUG) {
            applicationScope.launch {
                DebugSeedData.seedIfEmpty(container.database, container.saveEventUseCase)
            }
        }
    }
}
