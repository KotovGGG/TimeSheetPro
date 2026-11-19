package com.example.ui.screens.settings

import com.example.R
import com.example.appText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AmountRoundingMode
import com.example.data.local.entity.AppSettings
import com.example.data.local.entity.FirstDayOfWeek
import com.example.data.local.entity.ThemeMode
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import com.example.data.local.entity.Client
import com.example.data.local.entity.TimeEntry
import com.example.data.local.entity.RateHistoryEntry
import com.example.data.repository.ClientRepository
import com.example.data.repository.TimeEntryRepository
import com.example.data.repository.RateHistoryRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

@Serializable
data class BackupPayload(
    val schemaVersion: Int,
    val clients: List<Client>,
    val rateHistory: List<RateHistoryEntry>,
    val timeEntries: List<TimeEntry>,
    val appSettings: AppSettings?
)

internal const val BACKUP_SCHEMA_VERSION = 2

internal fun BackupPayload.withCurrentCurrencies(): BackupPayload {
    require(schemaVersion in 1..BACKUP_SCHEMA_VERSION)
    if (schemaVersion == BACKUP_SCHEMA_VERSION) return this
    val code = appSettings?.currency ?: AppSettings().currency
    return copy(
        schemaVersion = BACKUP_SCHEMA_VERSION,
        clients = clients.map { it.copy(currencyCode = null) },
        timeEntries = timeEntries.map { it.copy(currencyCode = code) },
        rateHistory = rateHistory.map { it.copy(currencyCode = code) }
    )
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val clientRepository: ClientRepository,
    private val timeEntryRepository: TimeEntryRepository,
    private val rateHistoryRepository: RateHistoryRepository,
    private val database: AppDatabase
) : ViewModel() {

    // Expose Settings UI State, initialize with default if null in db
    val settingsState: StateFlow<AppSettings> = settingsRepository.getSettingsFlow()
        .map { settings ->
            if (settings == null) {
                val defaultSettings = AppSettings()
                viewModelScope.launch {
                    settingsRepository.saveSettings(defaultSettings)
                }
                defaultSettings
            } else {
                settings
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    val clientsState: StateFlow<List<Client>> = clientRepository.getAllClients()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val timeEntriesState: StateFlow<List<TimeEntry>> = timeEntryRepository.getAllTimeEntries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateGlobalRate(rate: Double) {
        viewModelScope.launch {
            val current = settingsState.value
            database.withTransaction {
                settingsRepository.saveSettings(current.copy(globalHourlyRate = rate))
                if (rate != current.globalHourlyRate) {
                    rateHistoryRepository.insertEntry(RateHistoryEntry(
                        clientId = null, rate = rate, effectiveFrom = System.currentTimeMillis(),
                        currencyCode = current.currency
                    ))
                }
            }
        }
    }

    fun updateCurrency(currency: String) {
        viewModelScope.launch {
            val current = settingsState.value
            if (currency == current.currency) return@launch
            database.withTransaction {
                settingsRepository.saveSettings(current.copy(currency = currency))
                rateHistoryRepository.insertEntry(RateHistoryEntry(
                    clientId = null, rate = current.globalHourlyRate, effectiveFrom = System.currentTimeMillis(),
                    currencyCode = currency
                ))
            }
        }
    }

    fun updateTimeRounding(minutes: Int) {
        viewModelScope.launch {
            val current = settingsState.value
            settingsRepository.saveSettings(current.copy(timeRoundingMinutes = minutes))
        }
    }

    fun updateFirstDayOfWeek(day: FirstDayOfWeek) {
        viewModelScope.launch {
            val current = settingsState.value
            settingsRepository.saveSettings(current.copy(firstDayOfWeek = day))
        }
    }

    fun updateAmountRoundingMode(mode: AmountRoundingMode) {
        viewModelScope.launch {
            val current = settingsState.value
            settingsRepository.saveSettings(current.copy(amountRoundingMode = mode))
        }
    }

    fun updateAmountRoundingStep(step: Double) {
        viewModelScope.launch {
            val current = settingsState.value
            settingsRepository.saveSettings(current.copy(amountRoundingStep = step))
        }
    }

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            val current = settingsState.value
            settingsRepository.saveSettings(current.copy(themeMode = mode))
        }
    }

    suspend fun createBackupJson(): String = withContext(Dispatchers.IO) {
        // A consistent database snapshot; UI flows can still contain initial empty values.
        val payload = database.withTransaction {
            BackupPayload(
                schemaVersion = BACKUP_SCHEMA_VERSION,
                clients = clientRepository.getAllClients().first(),
                rateHistory = rateHistoryRepository.getAllHistory().first(),
                timeEntries = timeEntryRepository.getAllTimeEntries().first(),
                appSettings = settingsRepository.getSettingsFlow().first()
            )
        }
        Json.encodeToString(BackupPayload.serializer(), payload)
    }

    suspend fun restoreBackupJson(jsonString: String): Result<Unit> {
        return try {
            val payload = withContext(Dispatchers.Default) { Json.decodeFromString(BackupPayload.serializer(), jsonString) }
            if (payload.schemaVersion !in 1..BACKUP_SCHEMA_VERSION) {
                return Result.failure(Exception(appText(R.string.copy_this_backup_version_is_unsupported_choose_another)))
            }
            val current = payload.withCurrentCurrencies()
            database.restoreBackup(
                clientsList = current.clients,
                rateHistoryList = current.rateHistory,
                timeEntriesList = current.timeEntries,
                settings = current.appSettings
            )
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (invalid: kotlinx.serialization.SerializationException) {
            Result.failure(IllegalArgumentException(
                appText(R.string.copy_this_file_cannot_be_read_as_a), invalid))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
