package com.example.ui.screens.records

import com.example.appLocale
import com.example.R
import com.example.appText
import com.example.ui.theme.keepFocusedFieldVisible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import com.example.ui.theme.AppButton as Button
import com.example.ui.theme.AppAction
import com.example.ui.theme.AppCheckboxRow
import com.example.ui.theme.AppIconAction
import com.example.ui.theme.AppChoiceSheet
import com.example.ui.theme.AppSpacing
import com.example.ui.theme.AppSize
import com.example.ui.theme.LocalStatusPalette
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.app.DatePickerDialog
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.SizeTransform
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.example.data.local.entity.Client
import com.example.data.local.entity.Report
import com.example.data.local.entity.ReportStatus
import com.example.data.local.entity.TimeEntry
import com.example.data.local.entity.TimeEntryStatus
import androidx.compose.ui.window.Dialog
import com.example.data.repository.SortOrder
import com.example.ui.AppViewModelProvider
import com.example.ui.screens.timer.TimePickerDialog
import com.example.ui.screens.timer.ComposeDatePickerDialog
import com.example.ui.screens.timer.AppDurationPickerDialog
import com.example.ui.screens.timer.TimerStateManager
import com.example.ui.screens.timer.clockFormatter
import com.example.ui.screens.timer.formatClockTime
import com.example.ui.screens.timer.formatCurrency
import com.example.ui.screens.timer.formatCurrencyTotals
import com.example.data.local.entity.currencyTotals
import com.example.data.local.entity.billingRate
import com.example.ui.screens.timer.formatHours
import com.example.ui.screens.timer.safeParseColor
import com.example.ui.theme.LedgerStyles
import com.example.utils.export.PdfGenerator
import com.example.utils.export.XlsxColumn
import com.example.utils.export.XlsxGenerator
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay

private val paymentStatusChoices = TimeEntryStatus.entries.map { status ->
    status to when (status) {
        TimeEntryStatus.PAID -> appText(R.string.copy_paid_2)
        TimeEntryStatus.INVOICED -> appText(R.string.copy_invoiced)
        TimeEntryStatus.NOT_PAID -> appText(R.string.copy_unpaid)
    }
}

@Composable
private fun getStatusColors(status: TimeEntryStatus): Triple<Color, Color, Color> {
    val palette = LocalStatusPalette.current
    val colors = when (status) {
        TimeEntryStatus.PAID -> palette.paid
        TimeEntryStatus.INVOICED -> palette.invoiced
        TimeEntryStatus.NOT_PAID -> palette.unpaid
    }
    return Triple(colors.container, colors.content, colors.content)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecordsScreen(
    modifier: Modifier = Modifier,
    viewModel: RecordsViewModel = viewModel(factory = AppViewModelProvider.Factory),
    savedSessionId: Long? = null
) {
    val entries by viewModel.timeEntriesState.collectAsStateWithLifecycle()
    val clients by viewModel.clientsState.collectAsStateWithLifecycle()
    val currentLocale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()

    // Search and filter state from ViewModel
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedClients by viewModel.selectedClients.collectAsStateWithLifecycle()
    val selectedStatuses by viewModel.selectedStatuses.collectAsStateWithLifecycle()
    val currentSortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val customStartDate by viewModel.customStartDate.collectAsStateWithLifecycle()
    val customEndDate by viewModel.customEndDate.collectAsStateWithLifecycle()

    // Bulk export state
    var showBulkExportBottomSheet by remember { mutableStateOf(false) }
    var bulkExportFormat by remember { mutableStateOf("XLSX") }
    val bulkExportSelectedXlsxColumns = remember { mutableStateListOf<XlsxColumn>(*XlsxColumn.values().filter { it != XlsxColumn.CURRENCY }.toTypedArray()) }
    var bulkExportSuccessCard by remember { mutableStateOf(false) }

    // Selection mode state
    var selectedEntryIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val isSelectionMode = selectedEntryIds.isNotEmpty()

    // Back Handler for selection mode
    if (isSelectionMode) {
        BackHandler {
            selectedEntryIds = emptySet()
        }
    }

    // Dialog form states
    var showManualAddDialog by rememberSaveable { mutableStateOf(false) }
    var editingEntry by rememberSaveable(stateSaver = com.example.ui.theme.formStateSaver<TimeEntry>()) { mutableStateOf<TimeEntry?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<TimeEntry?>(null) }
    var savedSession by rememberSaveable(stateSaver = com.example.ui.theme.formStateSaver<TimeEntry>()) {
        mutableStateOf<TimeEntry?>(null)
    }

    LaunchedEffect(savedSessionId) {
        if (savedSessionId != null) {
            savedSession = viewModel.findEntry(savedSessionId)
            if (savedSession != null) {
                showManualAddDialog = false
                editingEntry = null
            }
            TimerStateManager.acknowledgeSavedNotificationEntry(savedSessionId)
        }
    }

    // Manual add form state
    var selectedClientId by rememberSaveable { mutableStateOf<Long?>(null) }
    var manualComment by rememberSaveable { mutableStateOf("") }
    var manualHours by rememberSaveable { mutableStateOf(1) }
    var manualMinutes by rememberSaveable { mutableStateOf(0) }
    var manualDateMs by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var manualShowTimePicker by rememberSaveable { mutableStateOf(false) }

    // Auto-select first or last selected client
    LaunchedEffect(clients) {
        if (selectedClientId == null && clients.isNotEmpty()) {
            val lastId = com.example.ui.screens.timer.TimerStateManager.getLastSelectedClientId()
            val hasLastActive = lastId != null && clients.any { it.id == lastId }
            selectedClientId = if (hasLastActive) lastId else clients.first().id
        }
    }

    val totalHoursSeconds = entries.sumOf { it.durationSeconds }

    // Date formatting helpers
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", appLocale()) }
    val displayDateFormatter = remember { SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(appLocale(), "yMMMMEEEEd"), appLocale()) }
    val timeFormatter = clockFormatter()

    // Grouping only applies visually on screen. Under GROUP BY client we can still display date headers 
    // or group them as a single list depending on sort mode.
    val groupedEntries = remember(entries, currentSortOrder) {
        if (currentSortOrder == SortOrder.CLIENT) {
            // Group by Client instead of Date
            entries.groupBy { "client_${it.clientId}" }
        } else {
            entries.groupBy { entry -> dateFormatter.format(Date(entry.startTime)) }
        }
    }

    // Bottom sheets toggle
    var showFilterBottomSheet by remember { mutableStateOf(false) }
    var showExportBottomSheet by remember { mutableStateOf(false) }
    var showReportsHistoryBottomSheet by remember { mutableStateOf(false) }
    var statusDropdownEntry by remember { mutableStateOf<TimeEntry?>(null) }
    val reportsHistory by viewModel.reportsState.collectAsStateWithLifecycle()
    var selectedReportForDetails by rememberSaveable(stateSaver = com.example.ui.theme.formStateSaver<Report>()) { mutableStateOf<Report?>(null) }
    var selectedReportForPayment by rememberSaveable(stateSaver = com.example.ui.theme.formStateSaver<Report>()) { mutableStateOf<Report?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope { Dispatchers.Main.immediate }
    val pageMotion = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val fadeMotion = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    var showDatePickerState by remember { mutableStateOf(false) }
    var datePickerInitialTime by remember { mutableStateOf(0L) }
    var onDateSelectedCallback by remember { mutableStateOf<((Long) -> Unit)?>(null) }

    // Date Picker helper
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

    // --- Export States ---
    var exportFormat by remember { mutableStateOf("XLSX") } // "XLSX" or "PDF"
    val exportSelectedClients = remember { mutableStateListOf<Long>() }
    val exportSelectedStatuses = remember { mutableStateListOf<TimeEntryStatus>(TimeEntryStatus.NOT_PAID) }
    var exportPeriodPreset by remember { mutableStateOf("ALL") } // "ALL", "WEEK", "MONTH", "CUSTOM"
    var exportCustomStartDate by remember { mutableStateOf(System.currentTimeMillis() - 7 * 24 * 3600 * 1000) }
    var exportCustomEndDate by remember { mutableStateOf(System.currentTimeMillis()) }
    val exportSelectedXlsxColumns = remember { mutableStateListOf<XlsxColumn>(*XlsxColumn.values().filter { it != XlsxColumn.CURRENCY }.toTypedArray()) }

    var isExporting by remember { mutableStateOf(false) }
    var generatedFileBytes by remember { mutableStateOf<ByteArray?>(null) }
    var generatedFileName by remember { mutableStateOf("") }
    var generatedMimeType by remember { mutableStateOf("") }
    var showExportSuccessCard by remember { mutableStateOf(false) }

    // SAF File Creator Launcher
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(generatedMimeType),
        onResult = { uri ->
            val bytes = generatedFileBytes
            if (uri != null && bytes != null) {
                coroutineScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            checkNotNull(context.contentResolver.openOutputStream(uri)) { appText(R.string.copy_file_unavailable) }
                                .use { it.write(bytes) }
                        }
                        android.widget.Toast.makeText(context, appText(R.string.copy_file_saved), android.widget.Toast.LENGTH_SHORT).show()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        android.widget.Toast.makeText(context, appText(R.string.copy_could_not_save_the_file_1, error.localizedMessage), android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    )

    if (isExporting) {
        AlertDialog(onDismissRequest = {}, confirmButton = {},
            title = { Text(appText(R.string.copy_creating_report)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(appText(R.string.copy_preparing_and_saving_the_report))
                }
            })
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Surface(
                        tonalElevation = 6.dp,
                        shadowElevation = AppSpacing.sm,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = AppSpacing.md, end = AppSpacing.md, bottom = AppSpacing.md, top = AppSpacing.xs)
                    ) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AppSize.primaryAction)
                            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)
                        ) {
                            IconButton(onClick = { selectedEntryIds = emptySet() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = appText(R.string.copy_cancel),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = appText(R.string.copy_selected_1, selectedEntryIds.size),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
                        ) {
                            // Bulk export report for selected entries
                            IconButton(onClick = {
                                bulkExportSuccessCard = false
                                showBulkExportBottomSheet = true
                            }) {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = appText(R.string.copy_export_report),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            // Status bulk update
                            var showBulkStatusMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showBulkStatusMenu = true }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Label,
                                        contentDescription = appText(R.string.copy_change_status),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            if (showBulkStatusMenu) {
                                AppChoiceSheet(
                                    title = appText(R.string.copy_status_of_selected_records),
                                    options = paymentStatusChoices,
                                    selected = entries.filter { it.id in selectedEntryIds }
                                        .map { it.status }.distinct().singleOrNull(),
                                    onSelect = { status ->
                                        if (status != null) {
                                            viewModel.updateBulkStatuses(selectedEntryIds.toList(), status)
                                            showBulkStatusMenu = false
                                            selectedEntryIds = emptySet()
                                        }
                                    },
                                    onDismiss = { showBulkStatusMenu = false }
                                )
                            }
                                                                   // Bulk delete action
                            var showBulkDeleteConfirm by remember { mutableStateOf(false) }
                            IconButton(onClick = { showBulkDeleteConfirm = true }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = appText(R.string.copy_delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }

                            if (showBulkDeleteConfirm) {
                                AlertDialog(
                                    onDismissRequest = { showBulkDeleteConfirm = false },
                                    title = { Text(appText(R.string.copy_delete_selected_records)) },
                                    text = {
                                        Text(appText(R.string.copy_permanently_delete_1, getRecordsPluralString(selectedEntryIds.size)))
                                    },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                viewModel.deleteBulkEntries(selectedEntryIds.toList())
                                                showBulkDeleteConfirm = false
                                                selectedEntryIds = emptySet()
                                            },
                                            variant = AppAction.Destructive
                                        ) {
                                            Text(appText(R.string.copy_delete))
                                        }
                                    },
                                    dismissButton = {
                                        Button(
                                            onClick = { showBulkDeleteConfirm = false },
                                            variant = AppAction.Quiet
                                        ) {
                                            Text(appText(R.string.copy_cancel))
                                        }
                                    },
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = MaterialTheme.shapes.extraLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(
                    onClick = { showManualAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .padding(bottom = AppSpacing.xs)
                        .size(AppSize.primaryAction)
                        .testTag("add_manual_record_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = appText(R.string.copy_add_a_manual_record),
                        modifier = Modifier.size(AppSpacing.xxl)
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        val activeMonthVal by viewModel.activeMonth.collectAsStateWithLifecycle()
        val monthFormat = remember { SimpleDateFormat("LLLL yyyy", appLocale()) }

        var monthSwipeDirection by remember { mutableIntStateOf(1) }
        var dragOffsetX by remember { mutableFloatStateOf(0f) }
        val animatedDragOffset by animateFloatAsState(
            targetValue = dragOffsetX,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "animatedDragOffset"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = AppSpacing.page, vertical = AppSpacing.lg)
                .then(
                    if (selectedPeriod == "MONTH" && !isSelectionMode) {
                        Modifier.pointerInput(selectedPeriod, isSelectionMode) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragOffsetX = 0f },
                                onDragEnd = {
                                    if (dragOffsetX < -80f) {
                                        monthSwipeDirection = 1
                                        viewModel.navigateMonth(1)
                                    } else if (dragOffsetX > 80f) {
                                        monthSwipeDirection = -1
                                        viewModel.navigateMonth(-1)
                                    }
                                    dragOffsetX = 0f
                                },
                                onDragCancel = { dragOffsetX = 0f },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetX += dragAmount
                                }
                            )
                        }
                    } else {
                        Modifier
                    }
                ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
        ) {
            // --- Header & Search Bar Grouped to prevent spacedBy jump ---
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AppSize.touchTarget),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = appText(R.string.copy_records),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        IconButton(onClick = { showExportSuccessCard = false; showExportBottomSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = appText(R.string.copy_export_reports),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { showReportsHistoryBottomSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = appText(R.string.copy_report_history),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { showFilterBottomSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = appText(R.string.copy_filters),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // --- Dynamic wrapping FlowRow for active filter chips ---
            val hasActiveFilters = selectedClients.isNotEmpty() || selectedStatuses.isNotEmpty() || selectedPeriod != "MONTH"
            if (hasActiveFilters && !isSelectionMode) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Reset all filters chip
                    InputChip(
                        selected = true,
                        onClick = { viewModel.clearAllFilters() },
                        label = { Text(appText(R.string.copy_clear_all), fontWeight = FontWeight.Bold) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(AppSpacing.lg)) },
                        colors = InputChipDefaults.inputChipColors()
                    )

                    // Period active chip
                    if (selectedPeriod != "MONTH") {
                        val periodLabel = when (selectedPeriod) {
                            "WEEK" -> appText(R.string.copy_this_week)
                            "ALL" -> appText(R.string.copy_all_time)
                            "CUSTOM" -> {
                                val fmt = SimpleDateFormat("dd.MM.yy", currentLocale)
                                "${fmt.format(Date(customStartDate ?: 0))}—${fmt.format(Date(customEndDate ?: 0))}"
                            }
                            else -> appText(R.string.copy_this_month)
                        }
                        InputChip(
                            selected = true,
                            onClick = { viewModel.applyFilters(selectedClients, selectedStatuses, currentSortOrder, "MONTH", null, null) },
                            label = { Text(periodLabel) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                    }

                    // Selected Client chips
                    selectedClients.forEach { clientId ->
                        val client = clients.find { it.id == clientId }
                        if (client != null) {
                            InputChip(
                                selected = true,
                                onClick = {
                                    val updated = selectedClients.toMutableSet().apply { remove(clientId) }
                                    viewModel.applyFilters(updated, selectedStatuses, currentSortOrder, selectedPeriod, customStartDate, customEndDate)
                                },
                                label = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(safeParseColor(client.colorHex))
                                        )
                                        Text(client.name)
                                    }
                                },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                        }
                    }

                    // Selected Status chips
                    selectedStatuses.forEach { status ->
                        InputChip(
                            selected = true,
                            onClick = {
                                val updated = selectedStatuses.toMutableSet().apply { remove(status) }
                                viewModel.applyFilters(selectedClients, updated, currentSortOrder, selectedPeriod, customStartDate, customEndDate)
                            },
                            label = {
                                Text(
                                    when (status) {
                                        TimeEntryStatus.PAID -> appText(R.string.copy_paid_2)
                                        TimeEntryStatus.INVOICED -> appText(R.string.copy_invoiced)
                                        TimeEntryStatus.NOT_PAID -> appText(R.string.copy_unpaid)
                                    }
                                )
                            },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                    }
                }
            }

            // --- Month Navigation Card ---
            if (selectedPeriod == "MONTH" && !isSelectionMode) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.md, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                monthSwipeDirection = -1
                                viewModel.navigateMonth(-1)
                            },
                            modifier = Modifier.size(AppSize.touchTarget)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = appText(R.string.copy_previous_month),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(AppSpacing.xl)
                            )
                        }
                        
                        AnimatedContent(
                            targetState = activeMonthVal.timeInMillis,
                            transitionSpec = {
                                if (monthSwipeDirection > 0) {
                                    (slideInHorizontally(animationSpec = pageMotion) { width -> width / 2 } + fadeIn(animationSpec = fadeMotion))
                                        .togetherWith(slideOutHorizontally(animationSpec = pageMotion) { width -> -width / 2 } + fadeOut(animationSpec = fadeMotion))
                                } else {
                                    (slideInHorizontally(animationSpec = pageMotion) { width -> -width / 2 } + fadeIn(animationSpec = fadeMotion))
                                        .togetherWith(slideOutHorizontally(animationSpec = pageMotion) { width -> width / 2 } + fadeOut(animationSpec = fadeMotion))
                                }
                            },
                            label = "MonthLabelAnimation",
                            modifier = Modifier.weight(1f)
                        ) { displayedMonth ->
                            val currentLabel = monthFormat.format(Date(displayedMonth)).replaceFirstChar { it.uppercase() }
                            Text(
                                text = currentLabel,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        IconButton(
                            onClick = {
                                monthSwipeDirection = 1
                                viewModel.navigateMonth(1)
                            },
                            modifier = Modifier.size(AppSize.touchTarget)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = appText(R.string.copy_next_month),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(AppSpacing.xl)
                            )
                        }
                    }
                }
            }

            // --- Historical Accumulations & Records Container with Month Swipe Animation & Touch Drag ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .graphicsLayer { translationX = animatedDragOffset }
            ) {
                AnimatedContent(
                    targetState = (if (selectedPeriod == "MONTH") activeMonthVal.timeInMillis else 0L) to entries,
                    transitionSpec = {
                        if (monthSwipeDirection > 0) {
                            (slideInHorizontally(animationSpec = pageMotion) { width -> width } + fadeIn(animationSpec = fadeMotion))
                                .togetherWith(slideOutHorizontally(animationSpec = pageMotion) { width -> -width } + fadeOut(animationSpec = fadeMotion))
                        } else {
                            (slideInHorizontally(animationSpec = pageMotion) { width -> -width } + fadeIn(animationSpec = fadeMotion))
                                .togetherWith(slideOutHorizontally(animationSpec = pageMotion) { width -> width } + fadeOut(animationSpec = fadeMotion))
                        }
                    },
                    label = "MonthContentAnimation",
                    modifier = Modifier.fillMaxSize()
                ) { displayedMonth ->
                    val entries = displayedMonth.second
                    val totalHoursSeconds = entries.sumOf { it.durationSeconds }
                                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().testTag("records_list"),
                            contentPadding = PaddingValues(bottom = AppSize.primaryAction + AppSpacing.lg),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                        ) {
                            if (!isSelectionMode) {
                                item(key = "summary") {
                                    com.example.ui.theme.AppLedgerSummary(appText(R.string.copy_period_total), formatHours(totalHoursSeconds),
                                        formatCurrencyTotals(entries.currencyTotals(), settings.currency))
                                }
                            }
                            if (entries.isEmpty()) {
                                item(key = "empty") {
                                    Column(Modifier.fillMaxWidth().padding(AppSpacing.xl),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                                        Icon(Icons.Default.HistoryToggleOff, contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(appText(R.string.copy_no_records_found), style = MaterialTheme.typography.titleMedium)
                                        Text(appText(R.string.copy_try_a_different_period_or_filter),
                                            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                    groupedEntries.forEach { (groupKey, groupEntries) ->
                        // Show header for group
                        item(key = "hdr_$groupKey") {
                            val headerText = if (currentSortOrder == SortOrder.CLIENT) {
                                val clientId = groupKey.substringAfter("client_").toLongOrNull() ?: 0L
                                val client = clients.find { it.id == clientId }
                                (client?.name ?: appText(R.string.copy_client_unavailable))
                            } else {
                                val parsedDate = dateFormatter.parse(groupKey) ?: Date()
                                displayDateFormatter.format(parsedDate)
                            }

                            val groupDuration = groupEntries.sumOf { it.durationSeconds }

                            Column(Modifier.fillMaxWidth().padding(vertical = AppSpacing.sm),
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                                Text(headerText, style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
                                    Text(formatHours(groupDuration), style = LedgerStyles.NumberSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(formatCurrencyTotals(groupEntries.currencyTotals(), settings.currency),
                                        style = LedgerStyles.NumberSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                HorizontalDivider(Modifier.padding(top = AppSpacing.xs),
                                    color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }

                        items(groupEntries, key = { it.id }) { entry ->
                            val client = remember(entry.clientId, clients) { clients.find { it.id == entry.clientId } }
                            val defaultColor = MaterialTheme.colorScheme.primary
                            val clientColor = remember(client, defaultColor) { client?.colorHex?.let { safeParseColor(it) } ?: defaultColor }
                            val isSelected = selectedEntryIds.contains(entry.id)
                            val statusColors = getStatusColors(entry.status)

                            Column(
                                modifier = Modifier.testTag("entry_${entry.id}")
                                    .fillMaxWidth().clip(MaterialTheme.shapes.large)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerLow)
                                    .semantics {
                                        if (isSelectionMode) {
                                            role = Role.Checkbox
                                            toggleableState = if (isSelected) ToggleableState.On else ToggleableState.Off
                                        }
                                    }
                                    .combinedClickable(
                                        onClickLabel = if (isSelectionMode) appText(R.string.copy_toggle_record_selection) else appText(R.string.copy_edit_record),
                                        onLongClickLabel = appText(R.string.copy_select_record),
                                        onClick = {
                                            if (isSelectionMode) {
                                                selectedEntryIds = if (isSelected) selectedEntryIds - entry.id
                                                    else selectedEntryIds + entry.id
                                            } else editingEntry = entry
                                        },
                                        onLongClick = {
                                            if (!isSelectionMode) selectedEntryIds = setOf(entry.id)
                                        }
                                    )
                                    .padding(AppSpacing.lg),
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                                    if (isSelectionMode) {
                                        Checkbox(checked = isSelected, onCheckedChange = null)
                                    } else {
                                        Box(Modifier.size(AppSpacing.sm).clip(CircleShape).background(clientColor))
                                    }
                                    Text(client?.name ?: appText(R.string.copy_client_unavailable),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f))
                                }
                                FlowRow(modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg),
                                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                                    Text("${timeFormatter.format(Date(entry.startTime))} – ${timeFormatter.format(Date(entry.endTime!!))}",
                                        style = MaterialTheme.typography.bodyMedium)
                                    Text(formatHours(entry.durationSeconds), style = LedgerStyles.NumberSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (!entry.comment.isNullOrBlank()) {
                                    Text(entry.comment, style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                                    itemVerticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(formatCurrency(entry.amount, entry.currencyCode),
                                        style = LedgerStyles.NumberMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.align(Alignment.CenterVertically))
                                Button(onClick = { statusDropdownEntry = entry }, enabled = !isSelectionMode,
                                    variant = AppAction.Quiet,
                                    modifier = Modifier.testTag("status_badge_${entry.id}")) {
                                    Box(Modifier.size(AppSpacing.sm).clip(CircleShape).background(statusColors.second))
                                    Spacer(Modifier.width(AppSpacing.sm))
                                    Text(when (entry.status) {
                                        TimeEntryStatus.PAID -> appText(R.string.copy_paid_2)
                                        TimeEntryStatus.INVOICED -> appText(R.string.copy_invoiced)
                                        TimeEntryStatus.NOT_PAID -> appText(R.string.copy_unpaid)
                                    }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(AppSpacing.xs))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                }
                            }
                        }
                    }
                }
        }
    }
}
}
}

    savedSession?.let { entry ->
        AlertDialog(
            modifier = Modifier.testTag("saved_timer_session"),
            onDismissRequest = { savedSession = null },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
            title = { Text(appText(R.string.copy_session_saved)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    val client = clients.firstOrNull { it.id == entry.clientId }
                    Text(
                        android.text.format.DateUtils.formatElapsedTime(entry.rawDurationSeconds),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        listOfNotNull(client?.name, displayDateFormatter.format(Date(entry.startTime)))
                            .joinToString(" · ")
                    )
                    Text(formatCurrency(entry.amount, entry.currencyCode))
                }
            },
            confirmButton = {
                Button(onClick = { savedSession = null }, variant = AppAction.Quiet) {
                    Text(appText(R.string.copy_done))
                }
            },
            dismissButton = {
                Button(onClick = {
                    savedSession = null
                    editingEntry = entry
                }, variant = AppAction.Quiet) {
                    Text(appText(R.string.copy_edit_record))
                }
            }
        )
    }

    // --- Edit Record Dialog ---
    editingEntry?.let { entry ->
        val editDateFormatter = remember { SimpleDateFormat("dd.MM.yyyy", appLocale()) }
        val editTimeFormatter = remember { SimpleDateFormat("HH:mm", appLocale()) }

        val calendar = Calendar.getInstance().apply { timeInMillis = entry.startTime }
        var editDateMs by rememberSaveable { mutableStateOf(entry.startTime) }
        var editHours by rememberSaveable { mutableStateOf(calendar.get(Calendar.HOUR_OF_DAY)) }
        var editMinutes by rememberSaveable { mutableStateOf(calendar.get(Calendar.MINUTE)) }
        var showEditTimePicker by rememberSaveable { mutableStateOf(false) }

        val initialDurationMinutes = entry.durationSeconds / 60
        var editDurationHours by rememberSaveable { mutableStateOf((initialDurationMinutes / 60).toInt()) }
        var editDurationMinutes by rememberSaveable { mutableStateOf((initialDurationMinutes % 60).toInt()) }
        var showEditDurationPicker by rememberSaveable { mutableStateOf(false) }

        var commentInput by rememberSaveable { mutableStateOf(entry.comment ?: "") }
        var selectedClientEditId by rememberSaveable { mutableStateOf(entry.clientId) }
        var selectedStatus by rememberSaveable { mutableStateOf(entry.status) }

        var applyCurrentRate by rememberSaveable { mutableStateOf(false) }
        var durationError by rememberSaveable { mutableStateOf(false) }
        var clientDropdownExpanded by rememberSaveable { mutableStateOf(false) }

        val displayDateFormatter = remember { SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(appLocale(), "yMMMMd"), appLocale()) }
        val formattedTime = formatClockTime(editHours, editMinutes)

        if (showEditTimePicker) {
            val timePickerState = rememberTimePickerState(
                initialHour = editHours,
                initialMinute = editMinutes,
                is24Hour = android.text.format.DateFormat.is24HourFormat(androidx.compose.ui.platform.LocalContext.current)
            )
            
            TimePickerDialog(
                onDismissRequest = { showEditTimePicker = false },
                confirmButton = {
                    Button(
                        onClick = {
                            editHours = timePickerState.hour
                            editMinutes = timePickerState.minute
                            showEditTimePicker = false
                        },
                        variant = AppAction.Quiet
                    ) {
                        Text(appText(R.string.copy_done))
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showEditTimePicker = false },
                        variant = AppAction.Quiet
                    ) {
                        Text(appText(R.string.copy_cancel))
                    }
                }
            ) {
                MaterialTheme(
                    typography = MaterialTheme.typography
                ) {
                    com.example.ui.theme.AppTimePicker(state = timePickerState)
                }
            }
        }

        if (showEditDurationPicker) {
            AppDurationPickerDialog(editDurationHours, editDurationMinutes, { showEditDurationPicker = false }) { pickedHours, pickedMinutes ->
                editDurationHours = pickedHours
                editDurationMinutes = pickedMinutes
                showEditDurationPicker = false
            }
        }

        ModalBottomSheet(
            onDismissRequest = { editingEntry = null },
            sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = AppSpacing.xl)
                        .padding(bottom = AppSpacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
                ) {
                    // Header with Title
                    Text(
                        text = appText(R.string.copy_edit_record),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )

                        // Date and Start Time Button Combined
                        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                            Text(
                                text = appText(R.string.copy_start_of_work),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    showDatePicker(editDateMs) { dateMs ->
                                        editDateMs = dateMs
                                        showEditTimePicker = true
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
                                    text = appText(R.string.copy_text_1_at_2, displayDateFormatter.format(Date(editDateMs)), formattedTime),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Duration Picker Button (looks like work time in save session)
                        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                            Text(
                                text = appText(R.string.copy_work_duration),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val formattedDuration = String.format(Locale.US, "%02d:%02d", editDurationHours, editDurationMinutes)
                            Button(
                                onClick = { showEditDurationPicker = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = AppSize.primaryAction),
                                variant = AppAction.Secondary
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = appText(R.string.copy_change_duration),
                                    modifier = Modifier.size(AppSpacing.page)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = appText(R.string.copy_time_worked_1, formattedDuration),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Comment Input
                        OutlinedTextField(
                            value = commentInput,
                            onValueChange = { commentInput = it },
                            label = { Text(appText(R.string.copy_comment)) },
                            modifier = Modifier.keepFocusedFieldVisible().fillMaxWidth().testTag("edit_comment_input"),
                            shape = MaterialTheme.shapes.medium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            singleLine = true
                        )

                        // Client Selector dropdown (moved after comment)
                        val client = clients.find { it.id == selectedClientEditId }

                        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                            Text(
                                text = appText(R.string.copy_client_2),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                            ) {
                                com.example.ui.theme.AppClientField(
                                    value = client?.name ?: appText(R.string.copy_choose_a_client),
                                    color = client?.colorHex?.let { safeParseColor(it) } ?: MaterialTheme.colorScheme.primary,
                                    onClick = { clientDropdownExpanded = true }, modifier = Modifier.testTag("client_selector_field"))
                            }
                        }

                        // Status Selection with chips
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = appText(R.string.copy_payment_status),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                TimeEntryStatus.values().forEach { status ->
                                    val isSelected = selectedStatus == status
                                    val label = when (status) {
                                        TimeEntryStatus.PAID -> appText(R.string.copy_paid_2)
                                        TimeEntryStatus.INVOICED -> appText(R.string.copy_invoiced)
                                        TimeEntryStatus.NOT_PAID -> appText(R.string.copy_unpaid)
                                    }
                                    
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedStatus = status },
                                        label = {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelMedium,
                                                textAlign = TextAlign.Center,
                                            )
                                        },
                                        modifier = Modifier.heightIn(min = AppSize.touchTarget)
                                    )
                                }
                            }
                        }

                        val editTargetClient = clients.find { it.id == selectedClientEditId }
                        val currentBilling = settings.billingRate(editTargetClient)
                        val editRate = if (applyCurrentRate) currentBilling.amount else entry.appliedRate
                        val editCurrency = if (applyCurrentRate) currentBilling.currencyCode else entry.currencyCode
                        val editTotalDurationHours = (editDurationHours * 60 + editDurationMinutes) / 60.0
                        val editCalculatedAmount = settings.roundAmount(editTotalDurationHours * editRate)

                        com.example.ui.theme.AppSettingsRow(
                            appText(R.string.recalculate_with_current_rate),
                            appText(R.string.rate_preview, formatCurrency(editRate, editCurrency),
                                formatCurrency(editCalculatedAmount, editCurrency)),
                            onClick = { applyCurrentRate = !applyCurrentRate },
                            trailingContent = { Switch(checked = applyCurrentRate, onCheckedChange = null) }
                        )
                        if (editRate <= 0.0 || editCalculatedAmount <= 0.0) {
                            com.example.ui.theme.AppInfoMessage(appText(R.string.copy_this_record_will_have_a_zero_amount))
                        }

                        Spacer(modifier = Modifier.height(AppSpacing.sm))

                        // Delete button (2-го плана)
                        Button(
                            onClick = { showDeleteConfirmDialog = entry },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = AppSize.touchTarget),
                            variant = AppAction.DestructiveQuiet
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = appText(R.string.copy_delete), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(AppSpacing.sm))
                            Text(appText(R.string.copy_delete_record), fontWeight = FontWeight.Bold)
                        }

                        // Row of Cancel and Save (aligned by width, equal space)
                        com.example.ui.theme.AppFormActions {
                            Button(
                                onClick = { editingEntry = null },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = AppSize.touchTarget),
                                variant = AppAction.Quiet
                            ) {
                                Text(appText(R.string.copy_cancel))
                            }

                            val totalDurationMinutes = editDurationHours * 60 + editDurationMinutes
                            Button(
                                onClick = {
                                    if (totalDurationMinutes > 0) {
                                        val cal = Calendar.getInstance().apply {
                                            timeInMillis = editDateMs
                                            set(Calendar.HOUR_OF_DAY, editHours)
                                            set(Calendar.MINUTE, editMinutes)
                                            set(Calendar.SECOND, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }

                                        val newStartTime = cal.timeInMillis
                                        val newDurationMs = totalDurationMinutes * 60 * 1000L
                                        val newEndTime = newStartTime + newDurationMs

                                        val targetClient = clients.find { it.id == selectedClientEditId }
                                        val currentRate = targetClient?.defaultHourlyRate?.takeIf { it > 0.0 } ?: settings.globalHourlyRate

                                        viewModel.updateTimeEntry(
                                            entry = entry,
                                            clientId = selectedClientEditId,
                                            startTime = newStartTime,
                                            endTime = newEndTime,
                                            comment = commentInput.ifBlank { null },
                                            status = selectedStatus,
                                            recalculateWithCurrentClientRate = applyCurrentRate,
                                            currentClientRate = currentRate
                                        )

                                        editingEntry = null
                                    }
                                },
                                enabled = (editDurationHours > 0 || editDurationMinutes > 0),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = AppSize.touchTarget)
                            ) {
                                Text(appText(R.string.copy_save), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    InlineClientSelectorSheet(
                        expanded = clientDropdownExpanded,
                        onDismiss = { clientDropdownExpanded = false },
                        clients = clients,
                        selectedClientId = selectedClientEditId,
                        onClientSelected = { clientId ->
                            selectedClientEditId = clientId
                            clientDropdownExpanded = false
                        }
                    )
                }
            }
        }

    statusDropdownEntry?.let { entry ->
        AppChoiceSheet(
            title = appText(R.string.copy_payment_status),
            options = paymentStatusChoices,
            selected = entry.status,
            onSelect = { status ->
                viewModel.updateEntryStatus(entry, status)
                statusDropdownEntry = null
            },
            onDismiss = { statusDropdownEntry = null }
        )
    }

    // --- Delete Confirm Dialog ---
    showDeleteConfirmDialog?.let { entry ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text(appText(R.string.copy_delete_this_record)) },
            text = {
                Text(appText(R.string.copy_permanently_delete_this_record))
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTimeEntry(entry)
                        showDeleteConfirmDialog = null
                        editingEntry = null // Close editor if opened
                    },
                    variant = AppAction.Destructive
                ) {
                    Text(appText(R.string.copy_delete))
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDeleteConfirmDialog = null },
                    variant = AppAction.Quiet
                ) {
                    Text(appText(R.string.copy_cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    // --- Manual Add Entry Dialog ---
    if (showManualAddDialog) {
        val displayFormatter = remember { SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(appLocale(), "yMMMMd"), appLocale()) }

        var manualDropdownExpanded by rememberSaveable { mutableStateOf(false) }

        val calNow = Calendar.getInstance()
        var manualStartHours by rememberSaveable { mutableStateOf(calNow.get(Calendar.HOUR_OF_DAY)) }
        var manualStartMinutes by rememberSaveable { mutableStateOf(calNow.get(Calendar.MINUTE)) }
        var showManualStartTimePicker by rememberSaveable { mutableStateOf(false) }
        val formattedStartTime = formatClockTime(manualStartHours, manualStartMinutes)

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

        if (manualShowTimePicker) {
            AppDurationPickerDialog(manualHours, manualMinutes, { manualShowTimePicker = false }) { pickedHours, pickedMinutes ->
                manualHours = pickedHours
                manualMinutes = pickedMinutes
                manualShowTimePicker = false
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showManualAddDialog = false },
            sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = AppSpacing.xl)
                        .padding(bottom = AppSpacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
                ) {
                    // Header with Title
                    Text(
                        text = appText(R.string.copy_add_record),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (clients.isEmpty()) {
                        Text(
                            text = appText(R.string.copy_add_a_client_in_the_clients_tab),
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {

                        // Combined Date and Start Time Button
                        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                            Text(
                                text = appText(R.string.copy_start_of_work),
                                style = MaterialTheme.typography.labelMedium,
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

                        // Duration Picker Button (looks like work time in save session)
                        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                            Text(
                                text = appText(R.string.copy_work_duration),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val formattedTime = String.format(Locale.US, "%02d:%02d", manualHours, manualMinutes)
                            Button(
                                onClick = { manualShowTimePicker = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = AppSize.primaryAction),
                                variant = AppAction.Secondary
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = appText(R.string.copy_change_duration),
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

                        // Comment/Description Input
                        OutlinedTextField(
                            value = manualComment,
                            onValueChange = { manualComment = it },
                            label = { Text(appText(R.string.copy_work_description)) },
                            placeholder = { Text(appText(R.string.copy_for_example_bug_fixes_or_refactoring)) },
                            modifier = Modifier.keepFocusedFieldVisible()
                                .fillMaxWidth()
                                .testTag("dialog_manual_comment_input"),
                            shape = MaterialTheme.shapes.medium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            singleLine = true
                        )

                        // Client Selector Dropdown (moved after comment)
                        val client = clients.find { it.id == selectedClientId }
                        val manualRate = client?.defaultHourlyRate?.takeIf { it > 0.0 } ?: settings.globalHourlyRate
                        val manualDurationHours = (manualHours * 3600L + manualMinutes * 60L) / 3600.0
                        val manualCalculatedAmount = settings.roundAmount(manualDurationHours * manualRate)

                        if (manualRate <= 0.0 || manualCalculatedAmount <= 0.0) {
                            com.example.ui.theme.AppInfoMessage(appText(R.string.copy_this_record_will_have_a_zero_amount))
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                            Text(
                                text = appText(R.string.copy_client_2),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                            ) {
                                com.example.ui.theme.AppClientField(
                                    value = client?.name ?: appText(R.string.copy_choose_a_client),
                                    color = client?.colorHex?.let { safeParseColor(it) } ?: MaterialTheme.colorScheme.primary,
                                    onClick = { manualDropdownExpanded = true }, modifier = Modifier.testTag("client_selector_field"))
                            }
                        }

                        Spacer(modifier = Modifier.height(AppSpacing.sm))

                        // Action Buttons aligned by width
                        com.example.ui.theme.AppFormActions {
                            Button(
                                onClick = { showManualAddDialog = false },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = AppSize.touchTarget),
                                variant = AppAction.Quiet
                            ) {
                                Text(appText(R.string.copy_cancel))
                            }

                            Button(
                                onClick = {
                                    val client = clients.find { it.id == selectedClientId }
                                    val durationMs = (manualHours * 3600L + manualMinutes * 60L) * 1000L
                                    if (client != null && durationMs > 0) {
                                        val calendar = Calendar.getInstance()
                                        calendar.timeInMillis = manualDateMs
                                        calendar.set(Calendar.HOUR_OF_DAY, manualStartHours)
                                        calendar.set(Calendar.MINUTE, manualStartMinutes)
                                        calendar.set(Calendar.SECOND, 0)
                                        calendar.set(Calendar.MILLISECOND, 0)
                                        val startTime = calendar.timeInMillis
                                        val endTime = startTime + durationMs
                                        val rate = client.defaultHourlyRate?.takeIf { it > 0.0 } ?: settings.globalHourlyRate

                                        viewModel.addManualEntry(
                                            clientId = client.id,
                                            startTime = startTime,
                                            endTime = endTime,
                                            comment = manualComment.ifBlank { null },
                                            rate = rate
                                        )

                                        // Reset
                                        manualComment = ""
                                        manualHours = 1
                                        manualMinutes = 0
                                        manualDateMs = System.currentTimeMillis()
                                        showManualAddDialog = false
                                    }
                                },
                                enabled = clients.isNotEmpty() && (manualHours > 0 || manualMinutes > 0),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = AppSize.touchTarget)
                            ) {
                                Text(appText(R.string.copy_save), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                InlineClientSelectorSheet(
                    expanded = manualDropdownExpanded,
                    onDismiss = { manualDropdownExpanded = false },
                    clients = clients,
                    selectedClientId = selectedClientId ?: 0L,
                    onClientSelected = { clientId ->
                        selectedClientId = clientId
                        com.example.ui.screens.timer.TimerStateManager.setLastSelectedClientId(clientId)
                        manualDropdownExpanded = false
                    }
                )
            }
        }
    }

    // --- 1. FILTER & SORTING MODAL BOTTOM SHEET ---
    if (showFilterBottomSheet) {
        // Local temporary state variables for applying/resetting inside sheet
        var tempClients by remember { mutableStateOf<Set<Long>>(selectedClients) }
        var tempStatuses by remember { mutableStateOf<Set<TimeEntryStatus>>(selectedStatuses) }
        var tempSortOrder by remember { mutableStateOf<SortOrder>(currentSortOrder) }
        var tempPeriod by remember { mutableStateOf<String>(selectedPeriod) }
        var tempStartDate by remember { mutableStateOf<Long>(customStartDate ?: (System.currentTimeMillis() - 30L * 24 * 3600 * 1000)) }
        var tempEndDate by remember { mutableStateOf<Long>(customEndDate ?: System.currentTimeMillis()) }
        var clientSearchQuery by remember { mutableStateOf("") }

        ModalBottomSheet(
            onDismissRequest = { showFilterBottomSheet = false },
            sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppSpacing.xl)
                    .padding(bottom = AppSpacing.xxl),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.page)
            ) {
                // Title Header
                com.example.ui.theme.AppSheetHeader(appText(R.string.copy_filters_and_sorting), { showFilterBottomSheet = false })

                // SORTING SECTION
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    Text(
                        text = appText(R.string.copy_sort_records),
                        style = LedgerStyles.UppercaseSectionHeader,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                    ) {
                        listOf(
                            SortOrder.TIME_DESC to appText(R.string.copy_newest_first),
                            SortOrder.TIME_ASC to appText(R.string.copy_oldest_first)
                        ).forEach { (order, label) ->
                            val isSel = tempSortOrder == order
                            FilterChip(
                                selected = isSel,
                                onClick = { tempSortOrder = order },
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.heightIn(min = AppSize.touchTarget)
                            )
                        }
                    }
                }

                // PERIOD SECTION
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    Text(
                        text = appText(R.string.copy_time_period),
                        style = LedgerStyles.UppercaseSectionHeader,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                    ) {
                        listOf(
                            "ALL" to appText(R.string.copy_all),
                            "WEEK" to appText(R.string.copy_week),
                            "MONTH" to appText(R.string.copy_month),
                            "CUSTOM" to appText(R.string.copy_custom)
                        ).forEach { (p, label) ->
                            val isSel = tempPeriod == p
                            FilterChip(
                                selected = isSel,
                                onClick = { tempPeriod = p },
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.heightIn(min = AppSize.touchTarget)
                            )
                        }
                    }

                    if (tempPeriod == "CUSTOM") {
                        val fmt = remember { SimpleDateFormat("dd.MM.yyyy", appLocale()) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = fmt.format(Date(tempStartDate)),
                                onValueChange = {},
                                label = { Text(appText(R.string.copy_from)) },
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = {
                                        showDatePicker(tempStartDate) { tempStartDate = it }
                                    }) {
                                        Icon(Icons.Default.DateRange, contentDescription = appText(R.string.copy_select_date))
                                    }
                                },
                                modifier = Modifier.keepFocusedFieldVisible().weight(1f),
                                shape = MaterialTheme.shapes.medium
                            )
                            OutlinedTextField(
                                value = fmt.format(Date(tempEndDate)),
                                onValueChange = {},
                                label = { Text(appText(R.string.copy_to)) },
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = {
                                        showDatePicker(tempEndDate) { tempEndDate = it }
                                    }) {
                                        Icon(Icons.Default.DateRange, contentDescription = appText(R.string.copy_select_date))
                                    }
                                },
                                modifier = Modifier.keepFocusedFieldVisible().weight(1f),
                                shape = MaterialTheme.shapes.medium
                            )
                        }
                    }
                }

                // STATUS SECTION
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    Text(
                        text = appText(R.string.copy_payment_status),
                        style = LedgerStyles.UppercaseSectionHeader,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                    ) {
                        TimeEntryStatus.values().forEach { status ->
                            val label = when (status) {
                                TimeEntryStatus.PAID -> appText(R.string.copy_paid_2)
                                TimeEntryStatus.INVOICED -> appText(R.string.copy_invoiced)
                                TimeEntryStatus.NOT_PAID -> appText(R.string.copy_unpaid)
                            }
                            val isSel = tempStatuses.contains(status)
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    tempStatuses = if (isSel) {
                                        tempStatuses.toMutableSet().apply { remove(status) }
                                    } else {
                                        tempStatuses.toMutableSet().apply { add(status) }
                                    }
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.heightIn(min = AppSize.touchTarget)
                            )
                        }
                    }
                }

                // CLIENT SELECTION
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    Text(
                        text = appText(R.string.copy_clients),
                        style = LedgerStyles.UppercaseSectionHeader,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = clientSearchQuery,
                        onValueChange = { clientSearchQuery = it },
                        placeholder = { Text(appText(R.string.copy_search_clients), style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(AppSize.smallIcon)) },
                        trailingIcon = {
                            if (clientSearchQuery.isNotEmpty()) {
                                AppIconAction(appText(R.string.copy_clear_search), Icons.Default.Close, { clientSearchQuery = "" })
                            }
                        },
                        modifier = Modifier.testTag("filter_client_search").keepFocusedFieldVisible().fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        singleLine = true
                    )

                    val filteredClients = remember(clients, clientSearchQuery) {
                        clients.filter { it.name.contains(clientSearchQuery, ignoreCase = true) }
                    }

                    if (filteredClients.isEmpty()) {
                        Text(
                            text = appText(R.string.copy_no_clients_found),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = AppSpacing.sm)
                        )
                    } else {
                        // Display list of checkboxes inside a compact column
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.2f))
                                .verticalScroll(rememberScrollState())
                        ) {
                            filteredClients.forEach { client ->
                                val isChecked = tempClients.contains(client.id)
                                AppCheckboxRow(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        tempClients = if (checked) tempClients + client.id else tempClients - client.id
                                    },
                                    modifier = Modifier.testTag("filter_client_${client.id}")
                                ) {
                                    Box(Modifier.size(AppSpacing.sm).clip(CircleShape)
                                        .background(safeParseColor(client.colorHex)))
                                    Text(client.name, style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // Action buttons footer
                com.example.ui.theme.AppFormActions(Modifier.padding(top = AppSpacing.md)) {
                    Button(
                        onClick = {
                            tempClients = emptySet()
                            tempStatuses = emptySet()
                            tempSortOrder = SortOrder.TIME_DESC
                            tempPeriod = "MONTH"
                            tempStartDate = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
                            tempEndDate = System.currentTimeMillis()
                        },
                        modifier = Modifier.weight(1f),
                        variant = AppAction.Quiet
                    ) {
                        Text(appText(R.string.copy_reset), fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            viewModel.applyFilters(
                                clients = tempClients,
                                statuses = tempStatuses,
                                sort = tempSortOrder,
                                period = tempPeriod,
                                startDate = if (tempPeriod == "CUSTOM") tempStartDate else null,
                                endDate = if (tempPeriod == "CUSTOM") tempEndDate else null
                            )
                            showFilterBottomSheet = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(appText(R.string.copy_apply), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // --- 1.5. EXPORT REPORT FOR SELECTED ENTRIES MODAL BOTTOM SHEET ---
    if (showBulkExportBottomSheet) {
        val selectedEntries = remember(entries, selectedEntryIds) {
            entries.filter { selectedEntryIds.contains(it.id) }
        }
        val totalSeconds = selectedEntries.sumOf { it.durationSeconds }
        val hoursStr = String.format(Locale.US, "%.2f", totalSeconds / 3600.0)

        ModalBottomSheet(
            onDismissRequest = {
                showBulkExportBottomSheet = false
                bulkExportSuccessCard = false
            },
            sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppSpacing.xl)
                    .padding(bottom = AppSpacing.xxl),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.page)
            ) {
                com.example.ui.theme.AppSheetHeader(appText(R.string.copy_report_for_selected_records), {
                    showBulkExportBottomSheet = false
                    bulkExportSuccessCard = false
                })

                if (!bulkExportSuccessCard) {
                    com.example.ui.theme.AppLedgerSummary(
                        appText(R.string.copy_records_selected_1, selectedEntries.size), appText(R.string.copy_text_1_hr, hoursStr),
                        formatCurrencyTotals(selectedEntries.currencyTotals(), settings.currency)
                    )

                    // FORMAT SELECTION
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        Text(
                            text = appText(R.string.copy_export_format),
                            style = LedgerStyles.UppercaseSectionHeader,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                            listOf("XLSX" to "Excel (XLSX)", "PDF" to appText(R.string.copy_invoice_pdf)).forEach { (format, label) ->
                                FilterChip(selected = bulkExportFormat == format,
                                    onClick = { bulkExportFormat = format }, label = { Text(label) })
                            }
                        }
                    }

                    // COLUMNS SELECTION (FOR XLSX)
                    if (bulkExportFormat == "XLSX") {
                        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                            Text(
                                text = appText(R.string.copy_spreadsheet_columns),
                                style = LedgerStyles.UppercaseSectionHeader,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.3f))
                                    .padding(AppSpacing.sm)
                            ) {
                                XlsxColumn.entries.filter { it != XlsxColumn.CURRENCY }.forEach { col ->
                                    val isChecked = bulkExportSelectedXlsxColumns.contains(col)
                                    AppCheckboxRow(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            if (checked) bulkExportSelectedXlsxColumns.add(col)
                                            else if (bulkExportSelectedXlsxColumns.size > 1) bulkExportSelectedXlsxColumns.remove(col)
                                        },
                                        modifier = Modifier.testTag("bulk_column_${col.name}")
                                    ) {
                                        val colLabel = when (col) {
                                            XlsxColumn.DATE -> appText(R.string.copy_date)
                                            XlsxColumn.CLIENT -> appText(R.string.copy_client_2)
                                            XlsxColumn.COMMENT -> appText(R.string.copy_description)
                                            XlsxColumn.SUM -> appText(R.string.copy_amount)
                                            XlsxColumn.CURRENCY -> appText(R.string.copy_currency)
                                        }
                                        Text(
                                            text = colLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // GENERATE BUTTON
                    Button(
                        onClick = {
                            if (isExporting) return@Button
                            isExporting = true
                            coroutineScope.launch {
                                try {

                            if (selectedEntries.isEmpty()) {
                                android.widget.Toast.makeText(context, appText(R.string.copy_select_at_least_one_record), android.widget.Toast.LENGTH_SHORT).show()
                                return@launch
                            }

                            val dateString = SimpleDateFormat("yyyyMMdd_HHmm", appLocale()).format(Date())
                            val distinctClientIds = selectedEntries.map { it.clientId }.distinct()
                            val clientNames = if (distinctClientIds.size == 1) {
                                clients.find { it.id == distinctClientIds.first() }?.name ?: appText(R.string.copy_selected_records)
                            } else {
                                appText(R.string.copy_selected_records_1_clients, distinctClientIds.size)
                            }
                            val dateFmt = SimpleDateFormat("dd.MM.yy", appLocale())
                            val minDate = selectedEntries.minOfOrNull { it.startTime } ?: System.currentTimeMillis()
                            val maxDate = selectedEntries.maxOfOrNull { it.startTime } ?: System.currentTimeMillis()
                            val periodLabel = if (dateFmt.format(Date(minDate)) == dateFmt.format(Date(maxDate))) {
                                dateFmt.format(Date(minDate))
                            } else {
                                "${dateFmt.format(Date(minDate))} — ${dateFmt.format(Date(maxDate))}"
                            }

                            if (bulkExportFormat == "XLSX") {
                                generatedFileBytes = withContext(Dispatchers.Default) { XlsxGenerator.generateXlsx(
                                    entries = selectedEntries,
                                    clients = clients,
                                    selectedColumns = bulkExportSelectedXlsxColumns.toList(),
                                    currencySymbol = settings.currency,
                                    clientNames = clientNames,
                                    periodLabel = periodLabel,
                                    settings = settings
                                ) }
                                generatedFileName = "Selected_Report_$dateString.xlsx"
                                generatedMimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            } else {
                                generatedFileBytes = withContext(Dispatchers.Default) { PdfGenerator.generatePdf(
                                    entries = selectedEntries,
                                    clients = clients,
                                    selectedClientNames = clientNames,
                                    periodLabel = periodLabel,
                                    currencySymbol = settings.currency
                                ) }
                                generatedFileName = "Selected_Invoice_$dateString.pdf"
                                generatedMimeType = "application/pdf"
                            }

                            generatedFileBytes?.let { bytes ->
                                viewModel.saveGeneratedReport(
                                    context = context,
                                    clientNames = clientNames,
                                    periodLabel = periodLabel,
                                    exportFormat = bulkExportFormat,
                                    entries = selectedEntries,
                                    generatedFileBytes = bytes,
                                    fileName = generatedFileName
                                )
                            }

                            bulkExportSuccessCard = true
                            selectedEntryIds = emptySet()

                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    android.widget.Toast.makeText(context, appText(R.string.copy_could_not_create_the_report_1, error.localizedMessage), android.widget.Toast.LENGTH_LONG).show()
                                } finally {
                                    isExporting = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AppSize.primaryAction)
                    ) {
                        Icon(imageVector = Icons.Default.Assessment, contentDescription = appText(R.string.copy_export))
                        Spacer(modifier = Modifier.width(AppSpacing.sm))
                        Text(appText(R.string.copy_create_report), fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Success View Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AppSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(52.dp)
                        )
                        Text(
                            text = appText(R.string.copy_report_ready),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = generatedFileName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(AppSpacing.xs))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                onClick = { coroutineScope.launch {

                                    try {
                                        generatedFileBytes?.let { bytes ->
                                            val cacheFile = File(context.cacheDir, generatedFileName)
                                            withContext(Dispatchers.IO) { cacheFile.writeBytes(bytes) }
                                            val authority = "${context.packageName}.fileprovider"
                                            val uri = FileProvider.getUriForFile(context, authority, cacheFile)

                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = generatedMimeType
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, appText(R.string.copy_share_report)))
                                        }
                                    } catch (cancelled: CancellationException) { throw cancelled } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, appText(R.string.copy_could_not_share_the_file_1, e.message), android.widget.Toast.LENGTH_SHORT).show()
                                    }

} },
                                modifier = Modifier.size(AppSize.touchTarget),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = appText(R.string.copy_share))
                            }

                            Button(
                                onClick = { createDocLauncher.launch(generatedFileName) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = AppSize.touchTarget)
                            ) {
                                Icon(imageVector = Icons.Default.Save, contentDescription = null)
                                Spacer(modifier = Modifier.width(AppSpacing.sm))
                                Text(appText(R.string.copy_save_to_files), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // --- 2. EXPORT REPORTS MODAL BOTTOM SHEET ---
    if (showExportBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showExportBottomSheet = false
                showExportSuccessCard = false
                generatedFileBytes = null
            },
            sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppSpacing.xl)
                    .padding(bottom = AppSpacing.xxl),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.page)
            ) {
                // Title Header
                com.example.ui.theme.AppSheetHeader(appText(R.string.copy_export_reports), { showExportBottomSheet = false; showExportSuccessCard = false; generatedFileBytes = null })

                if (!showExportSuccessCard) {
                    // FORMAT SELECTION
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        Text(
                            text = appText(R.string.copy_export_format),
                            style = LedgerStyles.UppercaseSectionHeader,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                            listOf("XLSX" to "Excel (XLSX)", "PDF" to appText(R.string.copy_invoice_pdf)).forEach { (format, label) ->
                                FilterChip(selected = exportFormat == format,
                                    onClick = { exportFormat = format }, label = { Text(label) })
                            }
                        }
                    }

                    // PERIOD PRESET
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        Text(
                            text = appText(R.string.copy_period_2),
                            style = LedgerStyles.UppercaseSectionHeader,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                        ) {
                            listOf(
                                "ALL" to appText(R.string.copy_all),
                                "WEEK" to appText(R.string.copy_week),
                                "MONTH" to appText(R.string.copy_month),
                                "CUSTOM" to appText(R.string.copy_custom)
                            ).forEach { (p, label) ->
                                val isSel = exportPeriodPreset == p
                                FilterChip(
                                    selected = isSel,
                                    onClick = { exportPeriodPreset = p },
                                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (exportPeriodPreset == "CUSTOM") {
                            val fmt = remember { SimpleDateFormat("dd.MM.yyyy", appLocale()) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = fmt.format(Date(exportCustomStartDate)),
                                    onValueChange = {},
                                    label = { Text(appText(R.string.copy_from)) },
                                    readOnly = true,
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            showDatePicker(exportCustomStartDate) { exportCustomStartDate = it }
                                        }) {
                                            Icon(Icons.Default.DateRange, contentDescription = appText(R.string.copy_select_date))
                                        }
                                    },
                                    modifier = Modifier.keepFocusedFieldVisible().weight(1f),
                                    shape = MaterialTheme.shapes.medium
                                )
                                OutlinedTextField(
                                    value = fmt.format(Date(exportCustomEndDate)),
                                    onValueChange = {},
                                    label = { Text(appText(R.string.copy_to)) },
                                    readOnly = true,
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            showDatePicker(exportCustomEndDate) { exportCustomEndDate = it }
                                        }) {
                                            Icon(Icons.Default.DateRange, contentDescription = appText(R.string.copy_select_date))
                                        }
                                    },
                                    modifier = Modifier.keepFocusedFieldVisible().weight(1f),
                                    shape = MaterialTheme.shapes.medium
                                )
                            }
                        }
                    }

                    // CLIENT CHIPS/CHECKLIST FOR EXPORT
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        Text(
                            text = appText(R.string.copy_clients),
                            style = LedgerStyles.UppercaseSectionHeader,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (clients.isEmpty()) {
                            Text(appText(R.string.copy_no_clients_have_been_added), style = MaterialTheme.typography.bodySmall)
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                            ) {
                                val allSelected = exportSelectedClients.isEmpty()
                                FilterChip(
                                    selected = allSelected,
                                    onClick = { exportSelectedClients.clear() },
                                    label = { Text(appText(R.string.copy_all_clients)) }
                                )
                                clients.forEach { c ->
                                    val isChecked = exportSelectedClients.contains(c.id)
                                    FilterChip(
                                        selected = isChecked,
                                        modifier = Modifier.testTag("export_client_${c.id}"),
                                        onClick = {
                                            if (isChecked) {
                                                exportSelectedClients.remove(c.id)
                                            } else {
                                                exportSelectedClients.add(c.id)
                                            }
                                        },
                                        label = {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(safeParseColor(c.colorHex)))
                                                Text(c.name)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // STATUS FILTER FOR EXPORT
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        Text(
                            text = appText(R.string.copy_payment_statuses),
                            style = LedgerStyles.UppercaseSectionHeader,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                        ) {
                            TimeEntryStatus.values().forEach { status ->
                                val isChecked = exportSelectedStatuses.contains(status)
                                val label = when (status) {
                                    TimeEntryStatus.NOT_PAID -> appText(R.string.copy_unpaid)
                                    TimeEntryStatus.INVOICED -> appText(R.string.copy_invoiced)
                                    TimeEntryStatus.PAID -> appText(R.string.copy_paid_2)
                                }
                                FilterChip(
                                    selected = isChecked,
                                    onClick = {
                                        if (isChecked) {
                                            if (exportSelectedStatuses.size > 1) {
                                                exportSelectedStatuses.remove(status)
                                            }
                                        } else {
                                            exportSelectedStatuses.add(status)
                                        }
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    // XLSX COLUMNS SELECTION (Visible only if XLSX)
                    if (exportFormat == "XLSX") {
                        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                            Text(
                                text = appText(R.string.copy_spreadsheet_columns),
                                style = LedgerStyles.UppercaseSectionHeader,
                                color = MaterialTheme.colorScheme.primary
                            )
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                XlsxColumn.entries.filter { it != XlsxColumn.CLIENT && it != XlsxColumn.CURRENCY }.forEach { col ->
                                    val isChecked = exportSelectedXlsxColumns.contains(col)
                                    val label = when (col) {
                                        XlsxColumn.DATE -> appText(R.string.copy_date)
                                        XlsxColumn.CLIENT -> appText(R.string.copy_client_2)
                                        XlsxColumn.COMMENT -> appText(R.string.copy_description)
                                        XlsxColumn.SUM -> appText(R.string.copy_amount)
                                            XlsxColumn.CURRENCY -> appText(R.string.copy_currency)
                                    }
                                    FilterChip(
                                        selected = isChecked,
                                        onClick = {
                                            if (isChecked) {
                                                if (exportSelectedXlsxColumns.size > 1) {
                                                    exportSelectedXlsxColumns.remove(col)
                                                }
                                            } else {
                                                exportSelectedXlsxColumns.add(col)
                                            }
                                        },
                                        label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(AppSpacing.md))

                    // GENERATE BUTTON
                    Button(
                        onClick = {
                            if (isExporting) return@Button
                            isExporting = true
                            coroutineScope.launch {
                                try {

                            val filtered = entries.filter { entry ->
                                val matchesClient = if (exportSelectedClients.isEmpty()) true else exportSelectedClients.contains(entry.clientId)
                                val matchesStatus = exportSelectedStatuses.contains(entry.status)
                                val matchesPeriod = when (exportPeriodPreset) {
                                    "WEEK" -> {
                                        val cal = Calendar.getInstance()
                                        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                                        cal.set(Calendar.HOUR_OF_DAY, 0)
                                        cal.set(Calendar.MINUTE, 0)
                                        cal.set(Calendar.SECOND, 0)
                                        cal.set(Calendar.MILLISECOND, 0)
                                        entry.startTime >= cal.timeInMillis
                                    }
                                    "MONTH" -> {
                                        val cal = Calendar.getInstance()
                                        cal.set(Calendar.DAY_OF_MONTH, 1)
                                        cal.set(Calendar.HOUR_OF_DAY, 0)
                                        cal.set(Calendar.MINUTE, 0)
                                        cal.set(Calendar.SECOND, 0)
                                        cal.set(Calendar.MILLISECOND, 0)
                                        entry.startTime >= cal.timeInMillis
                                    }
                                    "CUSTOM" -> {
                                        entry.startTime in exportCustomStartDate..exportCustomEndDate
                                    }
                                    else -> true
                                }
                                matchesClient && matchesStatus && matchesPeriod
                            }

                            if (filtered.isEmpty()) {
                                android.widget.Toast.makeText(context, appText(R.string.copy_no_records_match_your_selection), android.widget.Toast.LENGTH_SHORT).show()
                                return@launch
                            }

                            val dateString = SimpleDateFormat("yyyyMMdd_HHmm", appLocale()).format(Date())
                            val clientNames = if (exportSelectedClients.isEmpty()) {
                                val distinctClientIds = filtered.map { it.clientId }.distinct()
                                if (distinctClientIds.size == 1) {
                                    clients.find { it.id == distinctClientIds.first() }?.name ?: appText(R.string.copy_all_clients)
                                } else {
                                    appText(R.string.copy_all_clients)
                                }
                            } else {
                                clients.filter { exportSelectedClients.contains(it.id) }.joinToString { it.name }
                            }
                            val periodLabel = when (exportPeriodPreset) {
                                "ALL" -> appText(R.string.copy_all_time)
                                "WEEK" -> appText(R.string.copy_current_week)
                                "MONTH" -> appText(R.string.copy_current_month)
                                "CUSTOM" -> {
                                    val fmt = SimpleDateFormat("dd.MM.yy", appLocale())
                                    "${fmt.format(Date(exportCustomStartDate))} — ${fmt.format(Date(exportCustomEndDate))}"
                                }
                                else -> appText(R.string.copy_all_time)
                            }

                            if (exportFormat == "XLSX") {
                                generatedFileBytes = withContext(Dispatchers.Default) { XlsxGenerator.generateXlsx(
                                    entries = filtered,
                                    clients = clients,
                                    selectedColumns = exportSelectedXlsxColumns.toList(),
                                    currencySymbol = settings.currency,
                                    clientNames = clientNames,
                                    periodLabel = periodLabel,
                                    settings = settings
                                ) }
                                generatedFileName = "Timesheet_Export_$dateString.xlsx"
                                generatedMimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            } else {
                                generatedFileBytes = withContext(Dispatchers.Default) { PdfGenerator.generatePdf(
                                    entries = filtered,
                                    clients = clients,
                                    selectedClientNames = clientNames,
                                    periodLabel = periodLabel,
                                    currencySymbol = settings.currency
                                ) }
                                generatedFileName = "Invoice_Export_$dateString.pdf"
                                generatedMimeType = "application/pdf"
                            }

                            generatedFileBytes?.let { bytes ->
                                viewModel.saveGeneratedReport(
                                    context = context,
                                    clientNames = clientNames,
                                    periodLabel = periodLabel,
                                    exportFormat = exportFormat,
                                    entries = filtered,
                                    generatedFileBytes = bytes,
                                    fileName = generatedFileName
                                )
                            }

                            showExportSuccessCard = true

                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    android.widget.Toast.makeText(context, appText(R.string.copy_could_not_create_the_report_1, error.localizedMessage), android.widget.Toast.LENGTH_LONG).show()
                                } finally {
                                    isExporting = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AppSize.primaryAction)
                    ) {
                        Icon(imageVector = Icons.Default.Assessment, contentDescription = appText(R.string.copy_export))
                        Spacer(modifier = Modifier.width(AppSpacing.sm))
                        Text(appText(R.string.copy_create_report), fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Success View Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AppSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(52.dp)
                        )
                        Text(
                            text = appText(R.string.copy_report_ready),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = generatedFileName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(AppSpacing.xs))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                onClick = { coroutineScope.launch {

                                    try {
                                        generatedFileBytes?.let { bytes ->
                                            val cacheFile = File(context.cacheDir, generatedFileName)
                                            withContext(Dispatchers.IO) { cacheFile.writeBytes(bytes) }
                                            val authority = "${context.packageName}.fileprovider"
                                            val uri = FileProvider.getUriForFile(context, authority, cacheFile)

                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = generatedMimeType
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, appText(R.string.copy_share_report)))
                                        }
                                    } catch (cancelled: CancellationException) { throw cancelled } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, appText(R.string.copy_could_not_share_the_file_1, e.message), android.widget.Toast.LENGTH_SHORT).show()
                                    }

} },
                                modifier = Modifier.size(AppSize.touchTarget),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = appText(R.string.copy_share))
                            }

                            Button(
                                onClick = { createDocLauncher.launch(generatedFileName) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = AppSize.touchTarget)
                            ) {
                                Icon(imageVector = Icons.Default.Save, contentDescription = null)
                                Spacer(modifier = Modifier.width(AppSpacing.sm))
                                Text(appText(R.string.copy_save_to_files), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // --- 3. REPORTS HISTORY BOTTOM SHEET ---
    if (showReportsHistoryBottomSheet) {
        ReportsHistoryBottomSheet(
            reports = reportsHistory,
            currencySymbol = settings.currency,
            onDismiss = { showReportsHistoryBottomSheet = false },
            onShareReport = { report ->
                shareReportFile(context, report.filePath, report.fileName, if (report.exportFormat == "XLSX") "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" else "application/pdf")
            },
            onConfirmPayment = { report ->
                showReportsHistoryBottomSheet = false
                selectedReportForDetails = null
                selectedReportForPayment = report
            },
            onOpenReport = { report ->
                selectedReportForDetails = report
                showReportsHistoryBottomSheet = false
            }
        )
    }

    // --- 4. CONFIRM PAYMENT DIALOG ---
    selectedReportForPayment?.let { report ->
        ConfirmPaymentDialog(
            report = report,
            currencySymbol = settings.currency,
            onDismiss = { selectedReportForPayment = null },
            onConfirm = { photoUri, notes ->
                viewModel.confirmReportPayment(report, photoUri, notes)
                selectedReportForPayment = null
            }
        )
    }

    // --- 5. REPORT DETAILS DIALOG ---
    selectedReportForDetails?.let { report ->
        val reportEntries by remember(viewModel) { viewModel.allEntriesForReports }.collectAsStateWithLifecycle(emptyList())
        ReportDetailsDialog(
            report = report,
            allEntries = reportEntries,
            currencySymbol = settings.currency,
            onDismiss = { selectedReportForDetails = null },
            onShare = {
                shareReportFile(context, report.filePath, report.fileName, if (report.exportFormat == "XLSX") "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" else "application/pdf")
            },
            onConfirmPayment = {
                selectedReportForPayment = report
                selectedReportForDetails = null
            },
            onDelete = {
                viewModel.deleteReport(report)
                selectedReportForDetails = null
            }
        )
    }
}

fun getRecordsPluralString(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    return when {
        mod100 in 11..14 -> appText(R.string.copy_text_1_records, count)
        mod10 == 1 -> appText(R.string.copy_text_1_record, count)
        mod10 in 2..4 -> appText(R.string.copy_text_1_records_2, count)
        else -> appText(R.string.copy_text_1_records, count)
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

fun shareReportFile(context: android.content.Context, filePath: String, fileName: String, mimeType: String) {
    try {
        val file = File(filePath)
        if (!file.exists()) {
            android.widget.Toast.makeText(context, appText(R.string.copy_file_not_found_on_this_device), android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, appText(R.string.copy_download_or_share_report)))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, appText(R.string.copy_could_not_open_the_file_1, e.message), android.widget.Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsHistoryBottomSheet(
    reports: List<Report>,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onShareReport: (Report) -> Unit,
    onConfirmPayment: (Report) -> Unit,
    onOpenReport: (Report) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.xl)
                .padding(bottom = AppSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.page)
        ) {
            // Title Header (matching Export Reports)
            com.example.ui.theme.AppSheetHeader(appText(R.string.copy_report_history), onDismiss)

            if (reports.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = null,
                            modifier = Modifier.size(AppSpacing.xxl),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = appText(R.string.copy_no_reports_yet),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = appText(R.string.copy_reports_you_create_will_appear_here),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    contentPadding = PaddingValues(bottom = AppSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                ) {
                    items(reports, key = { it.id }) { report ->
                        ReportItemCard(
                            report = report,
                            currencySymbol = currencySymbol,
                            onShare = { onShareReport(report) },
                            onConfirmPayment = { onConfirmPayment(report) },
                            onOpen = { onOpenReport(report) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReportItemCard(
    report: Report, currencySymbol: String, onShare: () -> Unit,
    onConfirmPayment: () -> Unit, onOpen: () -> Unit,
) {
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", appLocale()) }
    val isPaid = report.status == ReportStatus.PAID
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth().testTag("report_${report.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Text("${report.exportFormat} · ${dateFmt.format(Date(report.createdAt))}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(report.clientNames, style = MaterialTheme.typography.titleMedium)
            Text(report.periodLabel, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatCurrencyTotals(report.currencyTotals(currencySymbol), currencySymbol), style = LedgerStyles.NumberMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
                Text(formatHours(report.totalDurationSeconds), style = LedgerStyles.NumberSmall)
                Text(getRecordsPluralString(report.entriesCount), style = MaterialTheme.typography.bodyMedium)
            }
            com.example.ui.theme.AppStatusLabel(if (isPaid) appText(R.string.copy_paid) else appText(R.string.copy_invoiced),
                if (isPaid) LocalStatusPalette.current.paid else LocalStatusPalette.current.invoiced)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Button(onClick = onShare, variant = AppAction.Quiet) { Text(appText(R.string.copy_share)) }
                if (!isPaid) {
                    Button(onClick = onConfirmPayment, variant = AppAction.Secondary,
                        modifier = Modifier.testTag("report_pay_${report.id}")) { Text(appText(R.string.copy_confirm_payment)) }
                }
            }
        }
    }
}

@Composable
fun ConfirmPaymentDialog(
    report: Report,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onConfirm: (photoUri: String?, notes: String?) -> Unit
) {
    val context = LocalContext.current
    var photoUriString by rememberSaveable { mutableStateOf<String?>(null) }

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (error: SecurityException) {
                android.widget.Toast.makeText(context, appText(R.string.copy_could_not_keep_access_to_the_receipt), android.widget.Toast.LENGTH_LONG).show()
                return@rememberLauncherForActivityResult
            }
            photoUriString = it.toString()
        }
    }

    val photoPreview = receiptPreview(photoUriString)
    val imageBitmap = (photoPreview as? ReceiptPreview.Ready)?.bitmap
    var showReceipt by rememberSaveable(photoUriString) { mutableStateOf(false) }

    com.example.ui.theme.AppFormSheet(onDismiss = onDismiss) {
                com.example.ui.theme.AppSheetHeader(appText(R.string.copy_confirm_payment), onDismiss)

                // Info Summary - Flush to left, no card background
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                    ) {
                        Text(
                            text = report.clientNames,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.width(AppSpacing.sm))
                        Text(
                            text = report.periodLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = formatCurrencyTotals(report.currencyTotals(currencySymbol), currencySymbol),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Photo picker section
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    Text(
                        text = appText(R.string.copy_receipt_or_transfer_photo_optional),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (photoPreview == ReceiptPreview.Loading) CircularProgressIndicator(
                        modifier = Modifier.size(AppSpacing.xl))
                    if (photoPreview == ReceiptPreview.Unavailable) Text(appText(R.string.copy_could_not_open_the_receipt_choose_another),
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    if (imageBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = appText(R.string.copy_payment_receipt),
                                modifier = Modifier.fillMaxSize().clickable(role = Role.Button, onClickLabel = appText(R.string.copy_open_receipt)) { showReceipt = true }
                            )
                            ReceiptExpandHint()
                            IconButton(
                                onClick = { photoUriString = null },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(AppSpacing.sm)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .size(AppSize.touchTarget)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = appText(R.string.copy_remove_photo),
                                    tint = Color.White,
                                    modifier = Modifier.size(AppSize.icon)
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = { photoLauncher.launch(arrayOf("image/*")) },
                            modifier = Modifier.fillMaxWidth(),
                            variant = AppAction.Secondary
                        ) {
                            Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null)
                            Spacer(modifier = Modifier.width(AppSpacing.sm))
                            Text(appText(R.string.copy_attach_receipt))
                        }
                    }
                }

                // Action Buttons (Confirm takes all available width)
                com.example.ui.theme.AppFormActions {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = AppSize.touchTarget),
                        variant = AppAction.Quiet
                    ) {
                        Text(appText(R.string.copy_cancel), style = MaterialTheme.typography.labelLarge)
                    }

                    Button(
                        onClick = {
                            onConfirm(photoUriString, null)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = AppSize.touchTarget)
                    ) {
                        Text(appText(R.string.copy_confirm), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }

                // Notice text placed BELOW buttons, with dark/muted secondary color
                Text(
                    text = appText(R.string.copy_confirming_payment_marks_all_records_in_this),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                }
    if (showReceipt && photoUriString != null) ReceiptViewer(photoUriString!!) { showReceipt = false }
}

@Composable
fun ReportDetailsDialog(
    report: Report,
    allEntries: List<TimeEntry>,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onConfirmPayment: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val dateFmt = remember { SimpleDateFormat("dd MMMM yyyy, HH:mm", appLocale()) }

    val includedEntryIds = remember(report.entryIdsJson) {
        val rawJson = report.entryIdsJson.trim().removePrefix("[").removeSuffix("]")
        if (rawJson.isNotBlank()) {
            rawJson.split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()
        } else emptySet()
    }

    val reportEntries = remember(allEntries, includedEntryIds) {
        allEntries.filter { includedEntryIds.contains(it.id) }
    }

    val paymentPreview = receiptPreview(report.paymentPhotoUri)
    val paymentBitmap = (paymentPreview as? ReceiptPreview.Ready)?.bitmap
    var showReceipt by rememberSaveable(report.id, report.paymentPhotoUri) { mutableStateOf(false) }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    com.example.ui.theme.AppFormSheet(onDismiss = onDismiss) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = appText(R.string.copy_report_details),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = appText(R.string.copy_close))
                    }
                }

                // Status banner
                val isPaid = report.status == ReportStatus.PAID
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (isPaid) LocalStatusPalette.current.paid.container else LocalStatusPalette.current.invoiced.container,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(AppSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        Icon(
                            imageVector = if (isPaid) Icons.Default.CheckCircle else Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (isPaid) LocalStatusPalette.current.paid.content else LocalStatusPalette.current.invoiced.content
                        )
                        Column {
                            Text(
                                text = if (isPaid) appText(R.string.copy_paid) else appText(R.string.copy_invoiced),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isPaid) LocalStatusPalette.current.paid.content else LocalStatusPalette.current.invoiced.content
                            )
                            if (isPaid && report.paymentConfirmedAt != null) {
                                Text(
                                    text = appText(R.string.copy_paid_1, dateFmt.format(Date(report.paymentConfirmedAt))),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalStatusPalette.current.paid.content
                                )
                            }
                        }
                    }
                }

                // General Info
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(appText(R.string.copy_client_1, report.clientNames), fontWeight = FontWeight.SemiBold)
                    Text(appText(R.string.copy_period_1, report.periodLabel), style = MaterialTheme.typography.bodyMedium)
                    Text(appText(R.string.copy_created_1, dateFmt.format(Date(report.createdAt))), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(appText(R.string.copy_format_1_2, report.exportFormat, report.fileName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(appText(R.string.copy_total_1, formatCurrencyTotals(report.currencyTotals(currencySymbol), currencySymbol)), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                }

                if (paymentPreview == ReceiptPreview.Loading) CircularProgressIndicator(
                    modifier = Modifier.size(AppSpacing.xl))
                if (paymentPreview == ReceiptPreview.Unavailable) Text(appText(R.string.copy_receipt_unavailable_because_the_file_moved_or),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                // Payment receipt photo if present
                if (paymentBitmap != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(appText(R.string.copy_receipt_or_transfer_photo), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = paymentBitmap,
                                contentDescription = appText(R.string.copy_transfer_receipt),
                                modifier = Modifier.fillMaxSize().clickable(role = Role.Button, onClickLabel = appText(R.string.copy_open_receipt)) { showReceipt = true }
                            )
                            ReceiptExpandHint()
                        }
                        if (!report.paymentNotes.isNullOrBlank()) {
                            Text(appText(R.string.copy_comment_1, report.paymentNotes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Included entries list
                if (reportEntries.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        Text(
                            text = appText(R.string.copy_records_in_this_report_1, reportEntries.size),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        reportEntries.forEach { entry ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.comment?.ifBlank { null } ?: appText(R.string.copy_no_comment),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = formatHours(entry.durationSeconds),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = formatCurrency(entry.amount, entry.currencyCode),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Actions
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    Button(
                        onClick = onShare,
                        variant = AppAction.Secondary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(AppSpacing.sm))
                        Text(appText(R.string.copy_share_report))
                    }

                    if (report.status == ReportStatus.INVOICED) {
                        Button(
                            onClick = onConfirmPayment,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(AppSpacing.sm))
                            Text(appText(R.string.copy_confirm_payment))
                        }
                    }

                    Button(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        variant = AppAction.DestructiveQuiet
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(AppSpacing.sm))
                        Text(appText(R.string.copy_remove_from_history))
                    }
                }
                }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(appText(R.string.copy_remove_this_report_from_history)) },
            text = { Text(appText(R.string.copy_this_removes_the_report_and_its_saved)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                        onDismiss()
                    },
                    variant = AppAction.Destructive
                ) {
                    Text(appText(R.string.copy_delete))
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDeleteConfirm = false },
                    variant = AppAction.Quiet
                ) {
                    Text(appText(R.string.copy_cancel))
                }
            }
        )
    }
    if (showReceipt && report.paymentPhotoUri != null) ReceiptViewer(report.paymentPhotoUri) { showReceipt = false }
}

