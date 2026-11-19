package com.example.ui.screens.timer

import com.example.appText
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.ColorFilter
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.appwidget.SizeMode
import androidx.glance.LocalSize
import com.example.R
import com.example.TimeSheetApp
import kotlinx.coroutines.flow.first
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import androidx.glance.color.ColorProvider as DayNightColorProvider
import com.example.ui.theme.LightColorScheme
import com.example.ui.theme.DarkColorScheme
import com.example.MainActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class TimeSheetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimeSheetWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        Log.d("TimeSheetWidget", "Receiver.onReceive: action = $action")
        
        TimerStateManager.initialize(context)

        if (action == "com.example.UPDATE_WIDGET" || action == Intent.ACTION_USER_PRESENT || action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            MainScope().launch {
                try {
                    Log.d("TimeSheetWidget", "Receiver: Force-updating widget via updateAll")
                    glanceAppWidget.updateAll(context)
                } catch (e: Exception) {
                    Log.e("TimeSheetWidget", "Receiver: Failed to force-update widget", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}

class TimeSheetWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Log.d("TimeSheetWidget", "provideGlance triggered for ID: $id")
        TimerStateManager.initialize(context)

        provideContent {
            val liveState by TimerStateManager.timerState.collectAsState()
            val tick by TimerStateManager.widgetTick.collectAsState()
            
            val statusText = when (liveState.status) {
                TimerStatus.IDLE -> context.getString(R.string.timer_ready)
                TimerStatus.RUNNING -> context.getString(R.string.timer_running)
                TimerStatus.PAUSED -> context.getString(R.string.timer_paused)
            }

            // Always calculate elapsed seconds dynamically
            val elapsed = TimerStateManager.getElapsedSeconds()
            val elapsedStr = if (liveState.status != TimerStatus.IDLE) formatDuration(elapsed) else "00:00:00"

            val pauseSecs = TimerStateManager.getPauseSeconds()
            val pauseStr = if (liveState.status != TimerStatus.IDLE) formatDuration(pauseSecs) else "00:00:00"

            Log.d("TimeSheetWidget", "provideContent: status = ${liveState.status}, elapsed = $elapsedStr, pause = $pauseStr, tick = $tick")

            WidgetContent(
                statusText = statusText,
                elapsedStr = elapsedStr,
                pauseStr = pauseStr,
                status = liveState.status,
                context = context
            )
        }
    }

    @Composable
    private fun WidgetContent(
        statusText: String,
        elapsedStr: String,
        pauseStr: String,
        status: TimerStatus,
        context: Context
    ) {
        val isIdle = status == TimerStatus.IDLE
        val compact = LocalSize.current.height < 72.dp
        val showBreak = LocalSize.current.height >= 112.dp

        // App-aligned light/dark themes using XML resource-based day/night resolution
        val backgroundColor = DayNightColorProvider(LightColorScheme.surfaceContainer, DarkColorScheme.surfaceContainer)
        val textColor = DayNightColorProvider(LightColorScheme.onSurface, DarkColorScheme.onSurface)

        val primaryColor = DayNightColorProvider(LightColorScheme.primary, DarkColorScheme.primary)
        val secondaryColor = DayNightColorProvider(LightColorScheme.secondary, DarkColorScheme.secondary)
        val buttonText = DayNightColorProvider(LightColorScheme.onPrimary, DarkColorScheme.onPrimary)
        val secondaryButtonText = DayNightColorProvider(LightColorScheme.onSecondary, DarkColorScheme.onSecondary)

        val accentColor = when (status) {
            TimerStatus.IDLE -> primaryColor
            TimerStatus.RUNNING -> primaryColor
            TimerStatus.PAUSED -> secondaryColor
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(24.dp)
                .background(backgroundColor)
                .padding(horizontal = if (compact) 10.dp else 16.dp, vertical = if (compact) 0.dp else 6.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: Text info (Status Text + Elapsed Time), left-aligned
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!compact && statusText.isNotEmpty()) {
                        Text(
                            text = statusText,
                            style = TextStyle(
                                color = accentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                    }
                    Text(
                        text = elapsedStr,
                        style = TextStyle(
                            color = textColor,
                            fontSize = if (compact) 18.sp else 22.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    if (showBreak && status != TimerStatus.IDLE) {
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = pauseStr,
                            style = TextStyle(
                                color = secondaryColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal
                            )
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.width(8.dp))

                // Right side: Interactive buttons using beautiful circular icons instead of text
                if (status != TimerStatus.IDLE) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play/Pause circular button
                        val isRunning = status == TimerStatus.RUNNING
                        val playPauseIcon = if (isRunning) {
                            com.example.R.drawable.ic_pause
                        } else {
                            com.example.R.drawable.ic_play
                        }
                        val playPauseBg = if (isRunning) secondaryColor else primaryColor
                        
                        Box(
                            modifier = GlanceModifier
                                .size(48.dp)
                                .cornerRadius(24.dp)
                                .background(playPauseBg)
                                .clickable(actionRunCallback<TogglePlayPauseAction>()),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                provider = ImageProvider(playPauseIcon),
                                contentDescription = context.getString(if (isRunning) R.string.timer_pause else R.string.timer_resume),
                                modifier = GlanceModifier.size(20.dp),
                                colorFilter = ColorFilter.tint(if (isRunning) secondaryButtonText else buttonText)
                            )
                        }

                        Spacer(modifier = GlanceModifier.width(8.dp))

                        // Stop/Save circular button
                        Box(
                            modifier = GlanceModifier
                                .size(48.dp)
                                .cornerRadius(24.dp)
                                .background(secondaryColor)
                                .clickable(actionRunCallback<FinishTimerAction>()),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                provider = ImageProvider(com.example.R.drawable.ic_save),
                                contentDescription = appText(R.string.copy_finish_timer),
                                modifier = GlanceModifier.size(20.dp),
                                colorFilter = ColorFilter.tint(secondaryButtonText)
                            )
                        }
                    }
                } else {
                    // Start circular button when idle
                    Box(
                        modifier = GlanceModifier
                            .size(48.dp)
                            .cornerRadius(24.dp)
                            .background(primaryColor)
                            .clickable(actionRunCallback<StartTimerAction>()),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(com.example.R.drawable.ic_play),
                            contentDescription = appText(R.string.copy_start_timer),
                            modifier = GlanceModifier.size(18.dp),
                            colorFilter = ColorFilter.tint(buttonText)
                        )
                    }
                }
            }
        }
    }

    private fun safeParseColor(hexString: String): Color {
        return try {
            val cleanHex = hexString.removePrefix("#")
            val colorInt = if (cleanHex.length == 6) {
                "FF$cleanHex".toLong(16).toInt()
            } else {
                cleanHex.toLong(16).toInt()
            }
            Color(colorInt)
        } catch (e: Exception) {
            Color(0xFFEAD5A0)
        }
    }
}

class StartTimerAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        Log.d("TimeSheetWidget", "StartTimerAction clicked")
        TimerStateManager.initialize(context)
        val clients = (context.applicationContext as TimeSheetApp).container.clientRepository.getActiveClients().first()
        val lastClient = TimerStateManager.getLastSelectedClientId()
        val client = clients.firstOrNull { it.id == lastClient } ?: clients.firstOrNull()
        TimerStateManager.start(client?.id)
        
        val serviceIntent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_START
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d("TimeSheetWidget", "StartTimerAction: Service triggered successfully")
        } catch (e: Exception) {
            Log.e("TimeSheetWidget", "StartTimerAction: Failed to start service", e)
        }
        
        try {
            TimeSheetWidget().updateAll(context)
            Log.d("TimeSheetWidget", "StartTimerAction: Widget updated successfully")
        } catch (e: Exception) {
            Log.e("TimeSheetWidget", "StartTimerAction: Failed to update widget", e)
        }
    }
}

class TogglePlayPauseAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        Log.d("TimeSheetWidget", "TogglePlayPauseAction clicked")
        TimerStateManager.initialize(context)
        val state = TimerStateManager.timerState.value
        
        val serviceIntent = Intent(context, TimerService::class.java)
        if (state.status == TimerStatus.RUNNING) {
            Log.d("TimeSheetWidget", "TogglePlayPauseAction: Pausing timer state")
            TimerStateManager.pause()
            serviceIntent.action = TimerService.ACTION_PAUSE
        } else {
            Log.d("TimeSheetWidget", "TogglePlayPauseAction: Resuming timer state")
            TimerStateManager.resume()
            serviceIntent.action = TimerService.ACTION_RESUME
        }
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d("TimeSheetWidget", "TogglePlayPauseAction: Service triggered successfully")
        } catch (e: Exception) {
            Log.e("TimeSheetWidget", "TogglePlayPauseAction: Failed to start/notify service", e)
        }
        
        try {
            TimeSheetWidget().updateAll(context)
            Log.d("TimeSheetWidget", "TogglePlayPauseAction: Widget updated successfully")
        } catch (e: Exception) {
            Log.e("TimeSheetWidget", "TogglePlayPauseAction: Failed to update widget", e)
        }
    }
}

class FinishTimerAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        Log.d("TimeSheetWidget", "FinishTimerAction clicked")
        TimerStateManager.initialize(context)
        
        val activityIntent = Intent(context, com.example.MainActivity::class.java).apply {
            action = "ACTION_WIDGET_FINISH"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        try {
            context.startActivity(activityIntent)
            Log.d("TimeSheetWidget", "FinishTimerAction: Started MainActivity with ACTION_WIDGET_FINISH")
        } catch (e: Exception) {
            Log.e("TimeSheetWidget", "FinishTimerAction: Failed to launch MainActivity", e)
        }
    }
}


