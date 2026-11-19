package com.example

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.data.local.AppDatabase
import androidx.compose.ui.Modifier
import com.example.data.local.entity.ThemeMode
import com.example.data.local.entity.AppSettings
import com.example.ui.navigation.MainAppNavigation
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.AppSpacing
import com.example.ui.theme.AppSize
import com.example.ui.screens.timer.TimerStateManager
import com.example.ui.screens.timer.TimerService
import com.example.ui.screens.timer.TimerStatus

class MainActivity : ComponentActivity() {
  private var startupState by mutableStateOf<StartupState?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    if (savedInstanceState == null) {
      handleTimerAction(intent)
    } else if (intent?.action == "ACTION_WIDGET_FINISH") {
      intent?.action = null
    }
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
      }
    }
    
    val app = application as TimeSheetApp
    val settingsRepository = app.container.settingsRepository
    val initialThemeMode = app.lastKnownThemeMode ?: ThemeMode.SYSTEM

    // Keep the system launch surface until the database and saved theme are available.
    // Loading stays on IO; failure releases the first draw to show the data-preservation error.
    val content = findViewById<android.view.View>(android.R.id.content)
    content.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
      override fun onPreDraw(): Boolean {
        if (startupState == null) return false
        content.viewTreeObserver.removeOnPreDrawListener(this)
        return true
      }
    })
    lifecycleScope.launch {
        startupState = withContext(Dispatchers.IO) {
          try {
            AppDatabase.getDatabase(applicationContext).openHelper.writableDatabase
            StartupState(true, settingsRepository.getSettingsDirect())
          } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
          } catch (error: Exception) {
            android.util.Log.e("TimeSheetPro", "Unable to open existing database", error)
            StartupState(false, null)
          }
        }
    }

    setContent {
      val startup = startupState
      if (startup?.databaseReady != true) {
        val loadingDarkTheme = when (initialThemeMode) {
          ThemeMode.LIGHT -> false
          ThemeMode.DARK -> true
          ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        }
        SideEffect {
          WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !loadingDarkTheme
            isAppearanceLightNavigationBars = !loadingDarkTheme
          }
        }
        MyApplicationTheme(darkTheme = loadingDarkTheme) {
          Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = AppSize.messageMaxWidth).fillMaxWidth()
              .verticalScroll(rememberScrollState()).padding(AppSpacing.lg),
              verticalArrangement = Arrangement.spacedBy(AppSpacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
              if (startup == null) {
                CircularProgressIndicator()
              } else {
                Text(appText(R.string.copy_could_not_open_data), style = MaterialTheme.typography.headlineSmall)
                Text(appText(R.string.copy_your_database_is_intact_keep_the_app))
              }
            }
            }
          }
        }
        return@setContent
      }
      val settings = settingsRepository.getSettingsFlow().collectAsStateWithLifecycle(initialValue = startup?.settings).value
      val currentTheme = settings?.themeMode ?: initialThemeMode
      val darkTheme = when (currentTheme) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
      }
      SideEffect {
        if (settings != null) app.lastKnownThemeMode = settings.themeMode
        WindowCompat.getInsetsController(window, window.decorView).apply {
          isAppearanceLightStatusBars = !darkTheme
          isAppearanceLightNavigationBars = !darkTheme
        }
      }
      MyApplicationTheme(darkTheme = darkTheme) {
        MainAppNavigation(modifier = Modifier.fillMaxSize())
      }
    }
  }

  private data class StartupState(val databaseReady: Boolean, val settings: AppSettings?)

  override fun onStop() {
    super.onStop()
    if (!BuildConfig.DEBUG) com.example.utils.AutoBackupManager.triggerBackup(this)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    // Widget/notification actions are events; retain the Activity's original launch intent.
    handleTimerAction(intent)
  }

  private fun handleTimerAction(intent: Intent?) {
    if (intent != null && TimerService.requestSaveFromNotification(this, intent)) {
      intent.action = null
      return
    }
    if (intent?.action == "ACTION_WIDGET_FINISH") {
      TimerStateManager.initialize(applicationContext)
      val status = TimerStateManager.timerState.value.status
      if (status == TimerStatus.RUNNING || status == TimerStatus.PAUSED) {
        TimerStateManager.setShowConfirmDialog(true)
      } else {
        TimerStateManager.setShowConfirmDialog(false)
      }
      intent.action = null
    }
  }
}


