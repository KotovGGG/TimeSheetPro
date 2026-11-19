package com.example.ui.screens.timer

import com.example.appLocale
import com.example.R
import com.example.appText
import com.example.ui.theme.keepFocusedFieldVisible
import com.example.ui.theme.AppButton as Button
import com.example.ui.theme.AppAction
import com.example.ui.theme.AppSpacing
import com.example.ui.theme.AppSize
import com.example.ui.theme.LocalStatusPalette
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.example.TimeSheetApp
import com.example.data.local.entity.AmountRoundingMode
import com.example.data.local.entity.AppSettings
import com.example.data.local.entity.Client
import com.example.data.local.entity.billingRate
import com.example.data.local.entity.TimeEntry
import com.example.data.local.entity.TimeEntryStatus
import com.example.ui.AppViewModelProvider
import com.example.ui.theme.LedgerStyles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    modifier: Modifier = Modifier,
    viewModel: TimerViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val context = LocalContext.current
    val activeClients by viewModel.activeClients.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val timerState by viewModel.timerState.collectAsStateWithLifecycle()
    val dailySummary by viewModel.dailySummary.collectAsStateWithLifecycle()
    val showConfirmDialog by TimerStateManager.showConfirmDialog.collectAsStateWithLifecycle()

    var selectedClientId by rememberSaveable { mutableStateOf<Long?>(null) }
    var taskComment by rememberSaveable { mutableStateOf("") }
    var dropdownExpanded by rememberSaveable { mutableStateOf(false) }
    var showManualDialog by rememberSaveable { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Auto-select last selected client (or first client) if none selected
    LaunchedEffect(activeClients) {
        if (selectedClientId == null && activeClients.isNotEmpty()) {
            val lastId = TimerStateManager.getLastSelectedClientId()
            val hasLastActive = lastId != null && activeClients.any { it.id == lastId }
            selectedClientId = if (hasLastActive) lastId else activeClients.first().id
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            val parentHeight = maxHeight
            val parentWidth = maxWidth
            val dialSize = if (parentWidth - 40.dp < parentHeight - 280.dp) {
                parentWidth - 40.dp
            } else {
                parentHeight - 280.dp
            }.coerceIn(AppSize.dialMin, AppSize.dialMax)

            val timerControls: @Composable () -> Unit = {
            // --- State-Dependent Control Actions ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                when (timerState.status) {
                    TimerStatus.IDLE -> {
                        // Start tracking button
                        Button(
                            onClick = {
                                viewModel.startTimer(context, selectedClientId)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = AppSize.primaryAction)
                                .testTag("control_timer_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(AppSpacing.sm))
                            Text(appText(R.string.copy_start), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }

                        // Manual entry action
                        Button(
                            onClick = { showManualDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = AppSize.primaryAction)
                                .testTag("manual_entry_button"),
                            variant = AppAction.Secondary
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(AppSpacing.sm))
                            Text(appText(R.string.copy_add_record), fontWeight = FontWeight.Medium)
                        }
                    }

                    TimerStatus.RUNNING -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                        ) {
                            Button(
                                onClick = { viewModel.pauseTimer(context) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = AppSize.primaryAction)
                                    .testTag("pause_timer_button"),
                                variant = AppAction.Tonal
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null)
                                Spacer(modifier = Modifier.width(AppSpacing.sm))
                                Text(appText(R.string.copy_pause), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }
                            Button(
                                onClick = {
                                    TimerStateManager.setShowConfirmDialog(true)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = AppSize.primaryAction)
                                    .testTag("stop_timer_button"),
                                variant = AppAction.Tonal
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(AppSpacing.sm))
                                Text(appText(R.string.copy_finish), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }

                    TimerStatus.PAUSED -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                        ) {
                            Button(
                                onClick = { viewModel.resumeTimer(context) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = AppSize.primaryAction)
                                    .testTag("resume_timer_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(AppSpacing.sm))
                                Text(appText(R.string.copy_resume), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }
                            Button(
                                onClick = {
                                    TimerStateManager.setShowConfirmDialog(true)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = AppSize.primaryAction)
                                    .testTag("stop_timer_button"),
                                variant = AppAction.Tonal
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(AppSpacing.sm))
                                Text(appText(R.string.copy_finish), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }

            }
            if (parentHeight < 500.dp && parentWidth > parentHeight) {
                Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = AppSpacing.page, vertical = AppSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xl),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        LiveTimerDial(viewModel, timerState.status, 160.dp)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                        com.example.ui.theme.AppCompactLedgerSummary(appText(R.string.copy_today), formatHours(dailySummary.seconds),
                            formatCurrencyTotals(dailySummary.totals, settings.currency))
                        timerControls()
                    }
                }
            } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppSpacing.page, vertical = AppSpacing.lg)
                    .heightIn(min = parentHeight - AppSpacing.xxl),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xl)
            ) {
                com.example.ui.theme.AppLedgerSummary(appText(R.string.copy_today), formatHours(dailySummary.seconds),
                    formatCurrencyTotals(dailySummary.totals, settings.currency), Modifier.testTag("summary_card"))

                // --- Central Circular Ring Dial and Live Status ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppSpacing.md)
                    .testTag("timer_card"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppSpacing.page)
            ) {
                    // The Circular Dial
                    val clientColor = MaterialTheme.colorScheme.primary
                    LiveTimerDial(viewModel, timerState.status, dialSize)

                }

            Spacer(modifier = Modifier.weight(1f))

            timerControls()

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
    }

    // --- Add Manual Record Dialog ---
    if (showManualDialog) {
        AddManualRecordDialog(
            activeClients = activeClients,
            onDismiss = { showManualDialog = false },
            onSave = { clientId, startTimeMs, endTimeMs, durationSeconds, comment ->
                showManualDialog = false
                val app = context.applicationContext as TimeSheetApp
                insertManualEntry(
                    clientId = clientId,
                    startTimeMs = startTimeMs,
                    endTimeMs = endTimeMs,
                    durationSeconds = durationSeconds,
                    comment = comment,
                    scope = coroutineScope,
                    app = app,
                    onSuccess = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(appText(R.string.copy_record_added))
                        }
                    },
                    onError = { errorMsg ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(appText(R.string.copy_error_1, errorMsg))
                        }
                    }
                )
            }
        )
    }

    // --- Confirm Finish Recording Dialog ---
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { TimerStateManager.setShowConfirmDialog(false) },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(appText(R.string.copy_finish_the_session), fontWeight = FontWeight.Bold) },
            text = { Text(appText(R.string.copy_finish_the_current_time_tracking_session)) },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { TimerStateManager.setShowConfirmDialog(false) },
                        variant = AppAction.Quiet
                    ) {
                        Text(appText(R.string.copy_resume))
                    }
                    Button(
                        onClick = {
                            TimerStateManager.setShowConfirmDialog(false)
                            viewModel.stopTimer(context)
                        },
                        modifier = Modifier.testTag("confirm_finish_timer"),
                        variant = AppAction.Primary
                    ) {
                        Text(appText(R.string.copy_finish))
                    }
                }
            },
            dismissButton = null
        )
    }

    // --- Save Tracked Session Dialog ---
    timerState.pendingSaveDuration?.let { pendingSeconds ->
        SaveSessionDialog(
            elapsedSeconds = pendingSeconds,
            pauseSeconds = timerState.pendingSavePauseDuration ?: 0L,
            initialClientId = timerState.clientId,
            initialComment = timerState.comment,
            activeClients = activeClients,
            globalHourlyRate = settings.globalHourlyRate,
            currency = settings.currency,
            onDismiss = {
                TimerStateManager.clearPending()
            },
            onSave = { clientId, comment, hourlyRate, finalDurationSeconds ->
                TimerStateManager.clearPending()
                val app = context.applicationContext as TimeSheetApp
                saveTrackedSessionEntry(
                    clientId = clientId,
                    comment = comment,
                    rate = hourlyRate,
                    durationSeconds = finalDurationSeconds,
                    scope = coroutineScope,
                    app = app,
                    settings = settings,
                    onSuccess = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(appText(R.string.copy_session_saved))
                        }
                    }
                )
            }
        )
    }
}

@Composable
private fun LiveTimerDial(viewModel: TimerViewModel, status: TimerStatus, dialSize: Dp) {
    val elapsed by viewModel.elapsedSeconds.collectAsStateWithLifecycle()
    val paused by viewModel.pauseSeconds.collectAsStateWithLifecycle()
    TimerDial(elapsedSeconds = elapsed, clientColor = MaterialTheme.colorScheme.primary,
        isRunning = status == TimerStatus.RUNNING, dialSize = dialSize,
        pauseSeconds = if (status != TimerStatus.IDLE) paused else null)
}

@Composable
fun TimerDial(
    elapsedSeconds: Long,
    clientColor: Color,
    modifier: Modifier = Modifier,
    isRunning: Boolean = false,
    dialSize: Dp = 280.dp,
    pauseSeconds: Long? = null
) {
    val compactNumbers = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f || dialSize < 240.dp
    val ringColor by animateColorAsState(
        targetValue = if (isRunning) clientColor else MaterialTheme.colorScheme.outline,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(), label = "Timer running state"
    )
    Box(
        modifier = modifier.width(dialSize).heightIn(min = dialSize),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
            val maxStrokePx = AppSpacing.xl.toPx()
            val strokeWidthPx = AppSize.dialStroke.toPx()
            val diameter = size.minDimension - maxStrokePx
            val radius = diameter / 2f
            val centerOffset = Offset(size.width / 2f, size.height / 2f)

            // Outer Ring (brighter when running, dimmer when paused/idle)
            drawCircle(
                color = ringColor,
                radius = radius,
                center = centerOffset,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidthPx)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = formatDuration(elapsedSeconds),
                style = if (compactNumbers) LedgerStyles.NumberCompact else LedgerStyles.NumberLarge,
                maxLines = 1,
                softWrap = false,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            if (pauseSeconds != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatDuration(pauseSeconds),
                    style = LedgerStyles.NumberSmall.copy(
                        fontWeight = FontWeight.Normal,
                        fontFamily = com.example.ui.theme.LedgerFont,
                        fontFeatureSettings = "tnum"
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddManualRecordDialog(
    activeClients: List<Client>,
    onDismiss: () -> Unit,
    onSave: (clientId: Long, startTimeMs: Long, endTimeMs: Long, durationSeconds: Long, comment: String) -> Unit
) {
    var selectedId by rememberSaveable {
        val lastId = TimerStateManager.getLastSelectedClientId()
        val defaultId = if (lastId != null && activeClients.any { it.id == lastId }) lastId else activeClients.firstOrNull()?.id ?: -1L
        mutableStateOf(defaultId) 
    }

    LaunchedEffect(activeClients) {
        if (selectedId == -1L && activeClients.isNotEmpty()) {
            val lastId = TimerStateManager.getLastSelectedClientId()
            val defaultId = if (lastId != null && activeClients.any { it.id == lastId }) lastId else activeClients.first().id
            selectedId = defaultId
        }
    }

    var comment by rememberSaveable { mutableStateOf("") }
    var dropdownExpanded by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    
    var hours by rememberSaveable { mutableStateOf(1) }
    var minutes by rememberSaveable { mutableStateOf(0) }
    var manualDateMs by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }

    val calNow = Calendar.getInstance()
    var manualStartHours by rememberSaveable { mutableStateOf(calNow.get(Calendar.HOUR_OF_DAY)) }
    var manualStartMinutes by rememberSaveable { mutableStateOf(calNow.get(Calendar.MINUTE)) }
    var showManualStartTimePicker by rememberSaveable { mutableStateOf(false) }
    val formattedStartTime = formatClockTime(manualStartHours, manualStartMinutes)

    val context = LocalContext.current
    val displayFormatter = remember { SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(appLocale(), "yMMMMd"), appLocale()) }

    var showDatePickerState by remember { mutableStateOf(false) }
    var datePickerInitialTime by remember { mutableStateOf(0L) }
    var onDateSelectedCallback by remember { mutableStateOf<((Long) -> Unit)?>(null) }

    fun showDatePicker(initialTime: Long, onDateSelected: (Long) -> Unit) {
        datePickerInitialTime = initialTime
        onDateSelectedCallback = onDateSelected
        showDatePickerState = true
    }

    if (showDatePickerState && onDateSelectedCallback != null) {
        ComposeDatePickerDialog(
            initialTimeMs = datePickerInitialTime,
            onDismissRequest = { showDatePickerState = false },
            onDateSelected = { selectedDate ->
                onDateSelectedCallback?.invoke(selectedDate)
            }
        )
    }

    if (showManualStartTimePicker) {
        val startTimePickerState = rememberTimePickerState(
            initialHour = manualStartHours,
            initialMinute = manualStartMinutes,
            is24Hour = android.text.format.DateFormat.is24HourFormat(androidx.compose.ui.platform.LocalContext.current)
        )
        
        TimePickerDialog(
            onDismissRequest = { showManualStartTimePicker = false },
            confirmButton = {
                Button(
                    onClick = {
                        manualStartHours = startTimePickerState.hour
                        manualStartMinutes = startTimePickerState.minute
                        showManualStartTimePicker = false
                    },
                    variant = AppAction.Quiet
                ) {
                    Text(appText(R.string.copy_done))
                }
            },
            dismissButton = {
                Button(
                    onClick = { showManualStartTimePicker = false },
                    variant = AppAction.Quiet
                ) {
                    Text(appText(R.string.copy_cancel))
                }
            }
        ) {
            MaterialTheme(
                typography = MaterialTheme.typography
            ) {
                com.example.ui.theme.AppTimePicker(state = startTimePickerState)
            }
        }
    }

    if (showTimePicker) {
        AppDurationPickerDialog(hours, minutes, { showTimePicker = false }) { pickedHours, pickedMinutes ->
            hours = pickedHours
            minutes = pickedMinutes
            showTimePicker = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppSpacing.xl)
                    .padding(bottom = AppSpacing.xxl)
            ) {
                Text(
                    text = appText(R.string.copy_add_record),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )

                // Combined Date and Start Time Button
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    Text(
                        text = appText(R.string.copy_start_of_work),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            showDatePicker(manualDateMs) { dateMs ->
                                manualDateMs = dateMs
                                showManualStartTimePicker = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AppSize.primaryAction),
                        variant = AppAction.Secondary
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = appText(R.string.copy_change_date_and_time),
                            modifier = Modifier.size(AppSpacing.page)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = appText(R.string.copy_text_1_at_2, displayFormatter.format(Date(manualDateMs)), formattedStartTime),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Work Duration Button (TimePicker Trigger)
                val formattedTime = String.format(Locale.US, "%02d:%02d", hours, minutes)
                Column {
                    Text(appText(R.string.copy_duration), style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(AppSpacing.xs))
                    Button(
                        onClick = { showTimePicker = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AppSize.primaryAction),
                        variant = AppAction.Secondary
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = appText(R.string.copy_change_work_time),
                            modifier = Modifier.size(AppSpacing.page)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = appText(R.string.copy_time_worked_1, formattedTime),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Comment input
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(appText(R.string.copy_comment)) },
                    placeholder = { Text(appText(R.string.copy_for_example_api_development)) },
                    modifier = Modifier.keepFocusedFieldVisible().fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    maxLines = 3
                )

                // Client selector inside dialog (moved after comment)
                val currentClient = activeClients.find { it.id == selectedId }
                Column {
                    Text(appText(R.string.copy_client_2), style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(AppSpacing.xs))
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        com.example.ui.theme.AppClientField(
                                    value = currentClient?.name ?: appText(R.string.copy_choose_a_client),
                                    color = currentClient?.colorHex?.let { safeParseColor(it) } ?: MaterialTheme.colorScheme.primary,
                                    onClick = { dropdownExpanded = true }, modifier = Modifier.testTag("client_selector_field"))
                    }
                }

                Spacer(modifier = Modifier.height(AppSpacing.sm))

                // Action buttons with equal width
                com.example.ui.theme.AppFormActions {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = AppSize.touchTarget),
                        variant = AppAction.Quiet
                    ) {
                        Text(appText(R.string.copy_cancel))
                    }

                    Button(
                        onClick = {
                            if (selectedId != -1L) {
                                val durationSeconds = (hours * 3600 + minutes * 60).toLong()
                                val calendar = Calendar.getInstance()
                                calendar.timeInMillis = manualDateMs
                                calendar.set(Calendar.HOUR_OF_DAY, manualStartHours)
                                calendar.set(Calendar.MINUTE, manualStartMinutes)
                                calendar.set(Calendar.SECOND, 0)
                                calendar.set(Calendar.MILLISECOND, 0)
                                val startTimeMs = calendar.timeInMillis
                                val endTimeMs = startTimeMs + durationSeconds * 1000L
                                onSave(selectedId, startTimeMs, endTimeMs, durationSeconds, comment)
                            }
                        },
                        enabled = selectedId != -1L && (hours > 0 || minutes > 0),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = AppSize.touchTarget)
                    ) {
                        Text(appText(R.string.copy_save), fontWeight = FontWeight.Bold)
                    }
                }
            }
            InlineClientSelectorSheet(
                expanded = dropdownExpanded,
                onDismiss = { dropdownExpanded = false },
                clients = activeClients,
                selectedClientId = selectedId,
                onClientSelected = { clientId ->
                    selectedId = clientId
                    TimerStateManager.setLastSelectedClientId(clientId)
                    dropdownExpanded = false
                }
            )
        }
    }
}

// Format duration to HH:MM:SS
fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format(appLocale(), "%02d:%02d:%02d", hours, minutes, seconds)
}

// Format seconds into decimal hours (e.g., 2.50 hrs)
fun formatHours(totalSeconds: Long): String {
    val hours = totalSeconds.toDouble() / 3600.0
    return String.format(appLocale(), appText(R.string.copy_text_2f_hr), hours)
}

// Format currency using locale
@Composable
fun clockFormatter(): java.text.DateFormat {
    val locale = appLocale()
    val use24HourClock = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    return remember(locale, use24HourClock) {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, if (use24HourClock) "Hm" else "hm")
        SimpleDateFormat(pattern, locale)
    }
}

@Composable
fun formatClockTime(hour: Int, minute: Int): String {
    val formatter = clockFormatter()
    val time = Calendar.getInstance().apply {
        clear()
        set(2000, Calendar.JANUARY, 15, hour, minute, 0)
    }.time
    return formatter.format(time)
}

fun formatCurrencyTotals(totals: Map<String, Double>, emptyCurrency: String? = null): String =
    when {
        totals.isEmpty() -> emptyCurrency?.let { formatCurrency(0.0, it) }.orEmpty()
        totals.size == 1 -> totals.entries.first().let { formatCurrency(it.value, it.key) }
        else -> totals.entries.joinToString("\n") { (code, amount) ->
            String.format(appLocale(), "%,.2f %s", amount, code)
        }
    }

fun formatCurrency(amount: Double, currencyCode: String): String {
    val symbol = when (currencyCode) {
        "USD" -> "$"
        "EUR" -> "€"
        "RUB" -> "₽"
        "GBP" -> "£"
        "KZT" -> "₸"
        "JPY" -> "¥"
        "BYN" -> "Br"
        "CHF" -> "₣"
        else -> currencyCode
    }
    return String.format(appLocale(), "%,.2f %s", amount, symbol)
}

// Safe hex color parser
fun safeParseColor(hexString: String): Color {
    return try {
        val cleanHex = hexString.removePrefix("#")
        val colorInt = if (cleanHex.length == 6) {
            "FF$cleanHex".toLong(16).toInt()
        } else {
            cleanHex.toLong(16).toInt()
        }
        Color(colorInt)
    } catch (e: Exception) {
        Color(0xFFEAD5A0) // Brass accent default
    }
}

fun insertManualEntry(
    clientId: Long,
    startTimeMs: Long,
    endTimeMs: Long,
    durationSeconds: Long,
    comment: String,
    scope: CoroutineScope,
    app: TimeSheetApp,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    scope.launch(Dispatchers.IO) {
        try {
            val settings = app.container.settingsRepository.getSettingsFlow().firstOrNull() ?: AppSettings()
            val clients = app.container.clientRepository.getActiveClients().firstOrNull() ?: emptyList()
            val client = clients.find { it.id == clientId }
            
            // 1. Time rounding UP (ceiling)
            val roundingMinutes = settings.timeRoundingMinutes
            val finalDurationSeconds = if (roundingMinutes > 0) {
                val roundingSeconds = roundingMinutes * 60L
                if (durationSeconds % roundingSeconds == 0L) {
                    durationSeconds
                } else {
                    ((durationSeconds / roundingSeconds) + 1L) * roundingSeconds
                }
            } else {
                durationSeconds
            }
            
            // 2. Applied Rate
            val billing = settings.billingRate(client)
            val appliedRate = billing.amount
            
            // 3. Amount & Rounding
            val rawAmount = (finalDurationSeconds.toDouble() / 3600.0) * appliedRate
            val finalAmount = settings.roundAmount(rawAmount)
            
            val entry = TimeEntry(
                clientId = clientId,
                startTime = startTimeMs,
                endTime = endTimeMs,
                durationSeconds = finalDurationSeconds,
                rawDurationSeconds = durationSeconds,
                comment = comment.ifBlank { null },
                appliedRate = appliedRate,
                currencyCode = billing.currencyCode,
                amount = finalAmount,
                status = TimeEntryStatus.NOT_PAID
            )
            
            app.container.timeEntryRepository.insertTimeEntry(entry)
            com.example.utils.AutoBackupManager.triggerBackup(app)
            
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError(e.message ?: appText(R.string.copy_unexpected_error))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    title: String = appText(R.string.copy_choose_time),
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        BoxWithConstraints(Modifier.imePadding().navigationBarsPadding().padding(AppSpacing.sm)) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.widthIn(max = 560.dp).wrapContentHeight()
            ) {
                val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
                if (landscape && maxHeight < 240.dp) {
                    Row(Modifier.padding(AppSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            content()
                        }
                        Column(Modifier.padding(start = AppSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                            confirmButton()
                            dismissButton()
                        }
                    }
                } else Column(Modifier.padding(AppSpacing.xl)) {
                    Column(
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth().padding(bottom = AppSpacing.page),
                            textAlign = TextAlign.Start
                        )
                        content()
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.sm),
                        horizontalArrangement = Arrangement.End
                    ) {
                        dismissButton()
                        Spacer(Modifier.width(AppSpacing.sm))
                        confirmButton()
                    }
                }
            }
        }
    }
}

@Composable
fun AppDurationPickerDialog(hours: Int, minutes: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    var hourText by rememberSaveable { mutableStateOf(hours.toString()) }
    var minuteText by rememberSaveable { mutableStateOf(minutes.toString()) }
    val hourValue = hourText.toIntOrNull()?.takeIf { it in 0..9999 }
    val minuteValue = minuteText.toIntOrNull()?.takeIf { it in 0..59 }
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = appText(R.string.copy_duration),
        confirmButton = {
            Button(
                onClick = { onConfirm(checkNotNull(hourValue), checkNotNull(minuteValue)) },
                enabled = hourValue != null && minuteValue != null,
                variant = AppAction.Quiet
            ) { Text(appText(R.string.copy_done)) }
        },
        dismissButton = {
            Button(onClick = onDismiss, variant = AppAction.Quiet) { Text(appText(R.string.copy_cancel)) }
        }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            OutlinedTextField(hourText, { hourText = it.filter(Char::isDigit).take(4) },
                label = { Text(appText(R.string.duration_hours)) }, singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).widthIn(max = 140.dp))
            OutlinedTextField(minuteText, { minuteText = it.filter(Char::isDigit).take(2) },
                label = { Text(appText(R.string.duration_minutes)) }, singleLine = true,
                shape = MaterialTheme.shapes.medium,
                isError = minuteText.isNotEmpty() && minuteValue == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).widthIn(max = 140.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveSessionDialog(
    elapsedSeconds: Long,
    pauseSeconds: Long = 0L,
    initialClientId: Long?,
    initialComment: String = "",
    activeClients: List<Client>,
    globalHourlyRate: Double,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (clientId: Long, comment: String, hourlyRate: Double, finalDurationSeconds: Long) -> Unit
) {
    var selectedClientId by rememberSaveable(elapsedSeconds) {
        val lastId = TimerStateManager.getLastSelectedClientId()
        val defaultId = if (lastId != null && activeClients.any { it.id == lastId }) lastId else activeClients.firstOrNull()?.id ?: -1L
        mutableStateOf(initialClientId ?: defaultId) 
    }

    LaunchedEffect(activeClients) {
        if (selectedClientId == -1L && activeClients.isNotEmpty()) {
            val lastId = TimerStateManager.getLastSelectedClientId()
            val defaultId = if (lastId != null && activeClients.any { it.id == lastId }) lastId else activeClients.first().id
            selectedClientId = initialClientId ?: defaultId
        }
    }

    var comment by rememberSaveable(elapsedSeconds) { mutableStateOf(initialComment) }
    
    // Convert elapsed seconds (actual work duration) to hours & minutes
    val initialHours = elapsedSeconds / 3600
    var initialMinutes = (elapsedSeconds % 3600) / 60
    if (elapsedSeconds > 0 && initialHours == 0L && initialMinutes == 0L) {
        initialMinutes = 1
    }
    
    val initialBreakMinutes = maxOf(0L, pauseSeconds / 60)
    
    var hoursStr by rememberSaveable(elapsedSeconds) { mutableStateOf(initialHours.toString()) }
    var minutesStr by rememberSaveable(elapsedSeconds) { mutableStateOf(initialMinutes.toString()) }
    var breakMinutesStr by rememberSaveable(elapsedSeconds) { mutableStateOf(initialBreakMinutes.toString()) }
    
    // Hourly rate state, initialized to the default rate of selected client or global rate
    var hourlyRateStr by rememberSaveable(selectedClientId) {
        val selectedClient = activeClients.find { it.id == selectedClientId }
        val initialRate = selectedClient?.defaultHourlyRate?.takeIf { it > 0.0 } ?: globalHourlyRate
        mutableStateOf(initialRate.toString())
    }
    
    // A restored draft must not be replaced by the default rate after rotation.
    var initializedRateClientId by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(selectedClientId, activeClients) {
        val client = activeClients.find { it.id == selectedClientId }
        if (client != null && initializedRateClientId != selectedClientId) {
            hourlyRateStr = (client.defaultHourlyRate?.takeIf { it > 0.0 } ?: globalHourlyRate).toString()
            initializedRateClientId = selectedClientId
        }
    }
    
    var dropdownExpanded by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    // Parse values for calculations
    val hVal = hoursStr.toLongOrNull() ?: 0L
    val mVal = minutesStr.toLongOrNull() ?: 0L
    val bVal = breakMinutesStr.toLongOrNull() ?: 0L
    val effectiveCurrency = activeClients.find { it.id == selectedClientId }?.currencyCode ?: currency
    val rateVal = hourlyRateStr.replace(',', '.').toDoubleOrNull() ?: 0.0

    val calculatedDurationSeconds = maxOf(0L, (hVal * 3600) + (mVal * 60))
    val calculatedEarnings = (calculatedDurationSeconds.toDouble() / 3600.0) * rateVal

    if (showTimePicker) {
        AppDurationPickerDialog(hoursStr.toIntOrNull() ?: 0, minutesStr.toIntOrNull() ?: 0, { showTimePicker = false }) { pickedHours, pickedMinutes ->
            hoursStr = pickedHours.toString()
            minutesStr = pickedMinutes.toString()
            showTimePicker = false
        }
    }

    var showDiscardConfirmation by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = { showDiscardConfirmation = true },
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.xl).padding(bottom = AppSpacing.xl)) {
            Text(appText(R.string.copy_save_record), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = AppSpacing.lg))
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                ) {
                // 1. Display Earnings Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppSpacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = appText(R.string.copy_session_earnings),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(AppSpacing.xs))
                        Text(
                            text = formatCurrency(calculatedEarnings, effectiveCurrency),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }

                if (activeClients.isNotEmpty() && (rateVal <= 0.0 || calculatedEarnings <= 0.0)) {
                    com.example.ui.theme.AppInfoMessage(appText(R.string.copy_this_record_will_have_a_zero_amount))
                }

                // 2. Rate Input
                OutlinedTextField(
                    value = hourlyRateStr,
                    onValueChange = { hourlyRateStr = it },
                    label = { Text(appText(R.string.copy_hourly_rate_1_hr, effectiveCurrency)) },
                    placeholder = { Text(appText(R.string.copy_for_example_100)) },
                    modifier = Modifier.keepFocusedFieldVisible().fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true
                )

                // 3. Work Duration and Break inputs
                // Break Duration (First!)
                OutlinedTextField(
                    value = breakMinutesStr,
                    onValueChange = { breakMinutesStr = it },
                    label = { Text(appText(R.string.copy_break_time_minutes)) },
                    modifier = Modifier.keepFocusedFieldVisible().fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                // Work Duration Button (Second!)
                val formattedTime = String.format(Locale.US, "%02d:%02d", hVal, mVal)
                Button(
                    onClick = { showTimePicker = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AppSize.primaryAction),
                    variant = AppAction.Secondary
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = appText(R.string.copy_change_work_time),
                        modifier = Modifier.size(AppSpacing.page)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = appText(R.string.copy_time_worked_1, formattedTime),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 4. Comment input
                val keyboardController = LocalSoftwareKeyboardController.current
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(appText(R.string.copy_comment)) },
                    placeholder = { Text(appText(R.string.copy_for_example_layout_design)) },
                    modifier = Modifier.keepFocusedFieldVisible().fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        keyboardType = KeyboardType.Text
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                        }
                    )
                )

                // 5. Client Selector at the bottom
                if (activeClients.isNotEmpty()) {
                    val currentClient = activeClients.find { it.id == selectedClientId }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = appText(R.string.copy_client_2),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = AppSpacing.xs)
                        )
                        
                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            com.example.ui.theme.AppClientField(
                                    value = currentClient?.name ?: appText(R.string.copy_choose_a_client),
                                    color = currentClient?.colorHex?.let { safeParseColor(it) } ?: MaterialTheme.colorScheme.primary,
                                    onClick = { dropdownExpanded = true }, modifier = Modifier.testTag("client_selector_field"))
                        }
                    }
                } else {
                    Text(
                        text = appText(R.string.copy_add_a_client_in_the_clients_tab_2),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action buttons with equal width
                com.example.ui.theme.AppFormActions {
                    Button(
                        onClick = { showDiscardConfirmation = true },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = AppSize.touchTarget),
                        variant = AppAction.DestructiveQuiet
                    ) {
                        Text(appText(R.string.copy_discard))
                    }

                    Button(
                        onClick = {
                            val h = hoursStr.toLongOrNull() ?: 0L
                            val m = minutesStr.toLongOrNull() ?: 0L
                            val finalDurationSeconds = maxOf(0L, (h * 3600) + (m * 60))
                            val selectedClient = activeClients.find { it.id == selectedClientId }
                            val rate = hourlyRateStr.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
                                ?: (selectedClient?.defaultHourlyRate?.takeIf { it > 0.0 } ?: globalHourlyRate)
                            
                            onSave(selectedClientId, comment, rate, finalDurationSeconds)
                        },
                        enabled = selectedClientId != -1L && (hoursStr.isNotBlank() || minutesStr.isNotBlank()),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = AppSize.touchTarget)
                    ) {
                        Text(appText(R.string.copy_save), fontWeight = FontWeight.Bold)
                    }
                }
            }

            InlineClientSelectorSheet(
                expanded = dropdownExpanded,
                onDismiss = { dropdownExpanded = false },
                clients = activeClients,
                selectedClientId = selectedClientId,
                onClientSelected = { clientId ->
                    selectedClientId = clientId
                    TimerStateManager.setLastSelectedClientId(clientId)
                    dropdownExpanded = false
                }
            )
        }
        }
    }
    if (showDiscardConfirmation) AlertDialog(
        onDismissRequest = { showDiscardConfirmation = false },
        title = { Text(appText(R.string.copy_discard_this_record)) },
        text = { Text(appText(R.string.copy_discarding_drops_the_time_tracked_in_this)) },
        confirmButton = { Button(onClick = onDismiss, variant = AppAction.Destructive) { Text(appText(R.string.copy_discard)) } },
        dismissButton = { Button(onClick = { showDiscardConfirmation = false }, variant = AppAction.Quiet) { Text(appText(R.string.copy_back)) } },
    )
}

fun saveTrackedSessionEntry(
    clientId: Long,
    comment: String,
    rate: Double,
    durationSeconds: Long,
    scope: CoroutineScope,
    app: TimeSheetApp,
    settings: AppSettings,
    onSuccess: () -> Unit
) {
    scope.launch(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val startTime = now - (durationSeconds * 1000)
        
        val client = app.container.clientRepository.getClientById(clientId).first()
        val entry = trackedSessionEntry(
            clientId, client, comment, rate, durationSeconds, settings, startTime, now
        )

        app.container.timeEntryRepository.insertTimeEntry(entry)
        com.example.utils.AutoBackupManager.triggerBackup(app)
        
        withContext(Dispatchers.Main) {
            onSuccess()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeDatePickerDialog(
    initialTimeMs: Long,
    onDismissRequest: () -> Unit,
    onDateSelected: (Long) -> Unit
) {
    val utcZone = java.util.TimeZone.getTimeZone("UTC")
    val localDate = java.util.Calendar.getInstance().apply { timeInMillis = initialTimeMs }
    val pickerDate = java.util.Calendar.getInstance(utcZone).apply {
        clear()
        set(localDate.get(java.util.Calendar.YEAR), localDate.get(java.util.Calendar.MONTH),
            localDate.get(java.util.Calendar.DAY_OF_MONTH))
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = pickerDate.timeInMillis)
    TimePickerDialog(
        onDismissRequest = onDismissRequest,
        title = appText(R.string.choose_date),
        confirmButton = {
            Button(
                onClick = {
                    datePickerState.selectedDateMillis?.let { dateUtc ->
                        pickerDate.timeInMillis = dateUtc
                        val selected = java.util.Calendar.getInstance().apply {
                            clear()
                            set(pickerDate.get(java.util.Calendar.YEAR), pickerDate.get(java.util.Calendar.MONTH),
                                pickerDate.get(java.util.Calendar.DAY_OF_MONTH))
                        }
                        onDateSelected(selected.timeInMillis)
                    }
                    onDismissRequest()
                },
                enabled = datePickerState.selectedDateMillis != null,
                variant = AppAction.Quiet
            ) { Text(appText(R.string.copy_done)) }
        },
        dismissButton = {
            Button(onClick = onDismissRequest, variant = AppAction.Quiet) { Text(appText(R.string.copy_cancel)) }
        }
    ) {
        DatePicker(
            state = datePickerState,
            title = null,
            colors = DatePickerDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InlineClientSelectorSheet(
    expanded: Boolean,
    onDismiss: () -> Unit,
    clients: List<Client>,
    selectedClientId: Long,
    onClientSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (expanded) {
        val options = remember(clients) { clients.map {
            com.example.ui.theme.ClientChoice(it.id, it.name, safeParseColor(it.colorHex))
        } }
        com.example.ui.theme.AppClientPickerSheet(options, selectedClientId,
            onClientSelected, onDismiss, modifier)
    }
}
