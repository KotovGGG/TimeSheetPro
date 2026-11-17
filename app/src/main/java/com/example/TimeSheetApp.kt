package com.example

import android.app.Application
import com.example.di.AppContainer
import com.example.di.AppDataContainer

class TimeSheetApp : Application() {
    // Display cache for Activity recreation; Room remains the source of truth.
    internal var lastKnownThemeMode: com.example.data.local.entity.ThemeMode? = null
    private var _container: AppContainer? = null
    val container: AppContainer
        get() {
            if (_container == null) {
                _container = AppDataContainer(this)
            }
            return _container!!
        }

    override fun onCreate() {
        super.onCreate()
        _container = AppDataContainer(this)
        com.example.ui.screens.timer.TimerStateManager.initialize(this)
        if (!BuildConfig.DEBUG) com.example.utils.AutoBackupManager.checkAndRestoreOnFirstRun(this)
    }
}
