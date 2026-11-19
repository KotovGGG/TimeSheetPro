package com.example.ui.screens.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.example.data.local.entity.AppSettings
import com.example.data.local.entity.BillingRate
import com.example.data.local.entity.billingRate
import com.example.data.local.entity.currencyTotals
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.example.data.local.entity.Client
import com.example.data.local.entity.Report
import com.example.data.local.entity.ReportStatus
import com.example.data.local.entity.TimeEntry
import com.example.data.local.entity.TimeEntryStatus
import com.example.data.repository.ClientRepository
import com.example.data.repository.ReportRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.TimeEntryRepository
import com.example.data.repository.SortOrder
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.example.data.local.entity.FirstDayOfWeek
import java.util.Calendar

class RecordsViewModel(
    private val clientRepository: ClientRepository,
    private val timeEntryRepository: TimeEntryRepository,
    private val settingsRepository: SettingsRepository,
    private val reportRepository: ReportRepository
) : ViewModel() {

    val allEntriesForReports = timeEntryRepository.getAllTimeEntries()

    suspend fun findEntry(id: Long): TimeEntry? = timeEntryRepository.getTimeEntryById(id).first()

    val reportsState: StateFlow<List<Report>> = reportRepository.getAllReports()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedClients = MutableStateFlow<Set<Long>>(emptySet())
    val selectedClients = _selectedClients.asStateFlow()

    private val _selectedStatuses = MutableStateFlow<Set<TimeEntryStatus>>(emptySet())
    val selectedStatuses = _selectedStatuses.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.TIME_DESC)
    val sortOrder = _sortOrder.asStateFlow()

    private val _selectedPeriod = MutableStateFlow("MONTH") // "ALL", "WEEK", "MONTH", "CUSTOM"
    val selectedPeriod = _selectedPeriod.asStateFlow()

    private val _customStartDate = MutableStateFlow<Long?>(null)
    val customStartDate = _customStartDate.asStateFlow()

    private val _customEndDate = MutableStateFlow<Long?>(null)
    val customEndDate = _customEndDate.asStateFlow()

    private val _activeMonth = MutableStateFlow(Calendar.getInstance())
    val activeMonth = _activeMonth.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val timeEntriesState: StateFlow<List<TimeEntry>> = combine(
        listOf(
            _searchQuery,
            _selectedClients,
            _selectedStatuses,
            _sortOrder,
            _selectedPeriod,
            _customStartDate,
            _customEndDate,
            _activeMonth
        )
    ) { array ->
        @Suppress("UNCHECKED_CAST")
        EightParams(
            search = array[0] as String,
            clients = (array[1] as Set<Long>).toList(),
            statuses = (array[2] as Set<TimeEntryStatus>).toList(),
            sort = array[3] as SortOrder,
            period = array[4] as String,
            startDate = array[5] as Long?,
            endDate = array[6] as Long?,
            activeMonth = array[7] as Calendar
        )
    }.flatMapLatest { params ->
        timeEntryRepository.getFilteredTimeEntries(
            searchQuery = params.search,
            clientIds = if (params.clients.isEmpty()) null else params.clients,
            statuses = if (params.statuses.isEmpty()) null else params.statuses,
            sortOrder = params.sort
        ).map { entries ->
            val bounds = when (params.period) {
                "WEEK" -> {
                    val cal = Calendar.getInstance()
                    cal.firstDayOfWeek = if (settingsState.value.firstDayOfWeek == FirstDayOfWeek.MONDAY) Calendar.MONDAY else Calendar.SUNDAY
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val diff = cal.firstDayOfWeek - cal.get(Calendar.DAY_OF_WEEK)
                    cal.add(Calendar.DAY_OF_YEAR, if (diff > 0) diff - 7 else diff)
                    cal.timeInMillis to Long.MAX_VALUE
                }
                "MONTH" -> {
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = params.activeMonth.timeInMillis
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val start = cal.timeInMillis
                    cal.add(Calendar.MONTH, 1)
                    start to cal.timeInMillis - 1
                }
                "CUSTOM" -> (params.startDate ?: 0L) to (params.endDate ?: Long.MAX_VALUE)
                else -> Long.MIN_VALUE to Long.MAX_VALUE
            }
            entries.filter { it.startTime in bounds.first..bounds.second }

        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private data class EightParams(
        val search: String,
        val clients: List<Long>,
        val statuses: List<TimeEntryStatus>,
        val sort: SortOrder,
        val period: String,
        val startDate: Long?,
        val endDate: Long?,
        val activeMonth: Calendar
    )

    fun navigateMonth(delta: Int) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = _activeMonth.value.timeInMillis
            add(Calendar.MONTH, delta)
        }
        _activeMonth.value = cal
    }

    val clientsState: StateFlow<List<Client>> = clientRepository.getAllClients()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val unpaidBalancesState: StateFlow<Map<Long, Double>> = timeEntryRepository.getUnpaidBalances()
        .map { list -> list.associate { it.clientId to it.unpaidAmount } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val settingsState: StateFlow<AppSettings> = settingsRepository.getSettingsFlow()
        .map { it ?: AppSettings() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleClientFilter(clientId: Long) {
        val current = _selectedClients.value
        _selectedClients.value = if (current.contains(clientId)) {
            current - clientId
        } else {
            current + clientId
        }
    }

    fun clearClientFilter() {
        _selectedClients.value = emptySet()
    }

    fun toggleStatusFilter(status: TimeEntryStatus) {
        val current = _selectedStatuses.value
        _selectedStatuses.value = if (current.contains(status)) {
            current - status
        } else {
            current + status
        }
    }

    fun clearStatusFilter() {
        _selectedStatuses.value = emptySet()
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun deleteTimeEntry(entry: TimeEntry) {
        viewModelScope.launch {
            timeEntryRepository.deleteTimeEntry(entry)
        }
    }

    fun updateEntryStatus(entry: TimeEntry, status: TimeEntryStatus) {
        viewModelScope.launch {
            timeEntryRepository.updateTimeEntry(entry.copy(
                status = status,
                updatedAt = System.currentTimeMillis()
            ))
        }
    }

    fun addManualEntry(
        clientId: Long,
        startTime: Long,
        endTime: Long,
        comment: String?,
        rate: Double
    ) {
        viewModelScope.launch {
            val rawSeconds = maxOf(0L, (endTime - startTime) / 1000)

            // Apply rounding according to settings
            val roundingMinutes = settingsState.value.timeRoundingMinutes
            val durationSeconds = if (roundingMinutes > 0) {
                val roundingSeconds = roundingMinutes * 60
                val intervals = kotlin.math.round(rawSeconds.toDouble() / roundingSeconds).toLong()
                val finalIntervals = if (rawSeconds > 0 && intervals == 0L) 1L else intervals
                finalIntervals * roundingSeconds
            } else {
                rawSeconds
            }

            val client = clientsState.value.find { it.id == clientId }
            val billing = settingsState.value.billingRate(client, rate)
            val effectiveRate = billing.amount

            val rawAmount = (durationSeconds.toDouble() / 3600.0) * effectiveRate
            val amount = settingsState.value.roundAmount(rawAmount)

            val newEntry = TimeEntry(
                clientId = clientId,
                startTime = startTime,
                endTime = endTime,
                durationSeconds = durationSeconds,
                rawDurationSeconds = rawSeconds,
                comment = comment,
                appliedRate = effectiveRate,
                currencyCode = billing.currencyCode,
                amount = amount,
                status = TimeEntryStatus.NOT_PAID
            )
            timeEntryRepository.insertTimeEntry(newEntry)
        }
    }

    fun updateTimeEntry(
        entry: TimeEntry,
        clientId: Long,
        startTime: Long,
        endTime: Long,
        comment: String?,
        status: TimeEntryStatus,
        recalculateWithCurrentClientRate: Boolean,
        currentClientRate: Double?
    ) {
        viewModelScope.launch {
            val rawSeconds = maxOf(0L, (endTime - startTime) / 1000)

            // Apply rounding according to settings
            val roundingMinutes = settingsState.value.timeRoundingMinutes
            val durationUnchanged = rawSeconds / 60 == entry.durationSeconds / 60
            val durationSeconds = if (durationUnchanged) {
                entry.durationSeconds
            } else if (roundingMinutes > 0) {
                val roundingSeconds = roundingMinutes * 60
                val intervals = kotlin.math.round(rawSeconds.toDouble() / roundingSeconds).toLong()
                val finalIntervals = if (rawSeconds > 0 && intervals == 0L) 1L else intervals
                finalIntervals * roundingSeconds
            } else {
                rawSeconds
            }

            // Decide rate
            val client = clientsState.value.find { it.id == clientId }
            val billing = if (recalculateWithCurrentClientRate) {
                settingsState.value.billingRate(client, currentClientRate)
            } else {
                BillingRate(entry.appliedRate, entry.currencyCode)
            }
            val effectiveRate = billing.amount

            val rawAmount = (durationSeconds.toDouble() / 3600.0) * effectiveRate
            val amount = if (!recalculateWithCurrentClientRate && durationUnchanged) {
                entry.amount
            } else {
                settingsState.value.roundAmount(rawAmount)
            }

            val updatedEntry = entry.copy(
                clientId = clientId,
                startTime = startTime,
                endTime = if (durationUnchanged && entry.endTime != null) {
                    startTime + (entry.endTime - entry.startTime)
                } else endTime,
                durationSeconds = durationSeconds,
                rawDurationSeconds = if (durationUnchanged) entry.rawDurationSeconds else rawSeconds,
                comment = comment,
                appliedRate = effectiveRate,
                currencyCode = billing.currencyCode,
                amount = amount,
                status = status,
                updatedAt = System.currentTimeMillis()
            )
            timeEntryRepository.updateTimeEntry(updatedEntry)
        }
    }

    // Bulk actions
    fun updateBulkStatuses(ids: List<Long>, status: TimeEntryStatus) {
        viewModelScope.launch {
            timeEntryRepository.updateStatuses(ids, status)
        }
    }

    fun deleteBulkEntries(ids: List<Long>) {
        viewModelScope.launch {
            timeEntryRepository.deleteEntries(ids)
        }
    }

    fun applyFilters(
        clients: Set<Long>,
        statuses: Set<TimeEntryStatus>,
        sort: SortOrder,
        period: String,
        startDate: Long?,
        endDate: Long?
    ) {
        _selectedClients.value = clients
        _selectedStatuses.value = statuses
        _sortOrder.value = sort
        _selectedPeriod.value = period
        _customStartDate.value = startDate
        _customEndDate.value = endDate
    }

    fun clearAllFilters() {
        _selectedClients.value = emptySet()
        _selectedStatuses.value = emptySet()
        _sortOrder.value = SortOrder.TIME_DESC
        _selectedPeriod.value = "MONTH"
        _customStartDate.value = null
        _customEndDate.value = null
    }

    suspend fun saveGeneratedReport(
        context: Context,
        clientNames: String,
        periodLabel: String,
        exportFormat: String,
        entries: List<TimeEntry>,
        generatedFileBytes: ByteArray,
        fileName: String
    ) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val totals = entries.currencyTotals()
            val totalDuration = entries.sumOf { it.durationSeconds }
            val entryIds = entries.map { it.id }
            val entryIdsJson = entryIds.joinToString(prefix = "[", postfix = "]")

            // Save file to internal app storage
            val reportsDir = File(context.filesDir, "reports")
            if (!reportsDir.exists()) {
                reportsDir.mkdirs()
            }
            val reportFile = File(reportsDir, fileName)
            reportFile.writeBytes(generatedFileBytes)

            val report = Report(
                clientNames = clientNames,
                periodLabel = periodLabel,
                exportFormat = exportFormat,
                totalAmount = totals.values.singleOrNull() ?: 0.0,
                currencyTotalsJson = Json.encodeToString(totals),
                totalDurationSeconds = totalDuration,
                entriesCount = entries.size,
                entryIdsJson = entryIdsJson,
                filePath = reportFile.absolutePath,
                fileName = fileName,
                status = ReportStatus.INVOICED
            )

            reportRepository.insertReport(report)

            // Update status of all entries included in this report to INVOICED ("счёт выставлен")
            if (entryIds.isNotEmpty()) {
                timeEntryRepository.updateStatuses(entryIds, TimeEntryStatus.INVOICED)
            }
        }
    }

    fun confirmReportPayment(
        report: Report,
        paymentPhotoUri: String?,
        paymentNotes: String?
    ) {
        viewModelScope.launch {
            reportRepository.markAsPaid(
                id = report.id,
                paymentPhotoUri = paymentPhotoUri,
                paymentNotes = paymentNotes
            )

            // Parse entry IDs from JSON string e.g. "[1, 2, 3]"
            val rawJson = report.entryIdsJson.trim().removePrefix("[").removeSuffix("]")
            if (rawJson.isNotBlank()) {
                val entryIds = rawJson.split(",").mapNotNull { it.trim().toLongOrNull() }
                if (entryIds.isNotEmpty()) {
                    timeEntryRepository.updateStatuses(entryIds, TimeEntryStatus.PAID)
                }
            }
        }
    }

    fun deleteReport(report: Report) {
        viewModelScope.launch {
            try {
                val file = File(report.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {}
            reportRepository.deleteReport(report)
        }
    }
}
