package com.example.ui.screens.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.AppSettings
import com.example.data.local.entity.Client
import com.example.data.local.entity.RateHistoryEntry
import com.example.data.repository.ClientRepository
import com.example.data.repository.RateHistoryRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.TimeEntryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClientsViewModel(
    private val clientRepository: ClientRepository,
    private val rateHistoryRepository: RateHistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val timeEntryRepository: TimeEntryRepository
) : ViewModel() {

    val unpaidBalancesState: StateFlow<Map<Long, Map<String, Double>>> = timeEntryRepository.getUnpaidBalances()
        .map { list -> list.groupBy { it.clientId }.mapValues { (_, balances) ->
            balances.associate { it.currencyCode to it.unpaidAmount }.toSortedMap()
        } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val clientsState: StateFlow<List<Client>> = clientRepository.getAllClients()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val settingsState: StateFlow<AppSettings> = settingsRepository.getSettingsFlow()
        .map { it ?: AppSettings() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    fun getRateHistoryForClient(clientId: Long): Flow<List<RateHistoryEntry>> {
        return rateHistoryRepository.getHistoryForClient(clientId)
    }

    fun saveClient(client: Client, oldRate: Double?, oldCurrency: String? = null) {
        viewModelScope.launch {
            if (client.id == 0L) {
                // Creation
                val clientId = clientRepository.insertClient(client)
                if (client.defaultHourlyRate != null) {
                    rateHistoryRepository.insertEntry(
                        RateHistoryEntry(
                            clientId = clientId,
                            rate = client.defaultHourlyRate,
                            currencyCode = client.currencyCode ?: settingsState.value.currency,
                            effectiveFrom = client.createdAt
                        )
                    )
                }
            } else {
                // Update
                clientRepository.updateClient(client)
                // Check if rate changed and update history
                if ((client.defaultHourlyRate != oldRate || client.currencyCode != oldCurrency) && client.defaultHourlyRate != null) {
                    rateHistoryRepository.insertEntry(
                        RateHistoryEntry(
                            clientId = client.id,
                            rate = client.defaultHourlyRate,
                            currencyCode = client.currencyCode ?: settingsState.value.currency,
                            effectiveFrom = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    fun updateGlobalHourlyRate(rate: Double) {
        viewModelScope.launch {
            settingsRepository.updateGlobalHourlyRate(rate)
            rateHistoryRepository.insertEntry(
                RateHistoryEntry(
                    clientId = null,
                    rate = rate,
                    currencyCode = settingsState.value.currency,
                    effectiveFrom = System.currentTimeMillis()
                )
            )
        }
    }

    fun toggleArchiveClient(client: Client) {
        viewModelScope.launch {
            clientRepository.updateClient(client.copy(isArchived = !client.isArchived))
        }
    }

    fun deleteClient(client: Client) {
        viewModelScope.launch {
            clientRepository.deleteClient(client)
        }
    }
}
