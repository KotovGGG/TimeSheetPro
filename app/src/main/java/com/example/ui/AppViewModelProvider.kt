package com.example.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.TimeSheetApp
import com.example.ui.screens.clients.ClientsViewModel
import com.example.ui.screens.records.RecordsViewModel
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.screens.timer.TimerViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            TimerViewModel(
                timeSheetApplication().container.clientRepository,
                timeSheetApplication().container.timeEntryRepository,
                timeSheetApplication().container.settingsRepository
            )
        }
        initializer {
            ClientsViewModel(
                clientRepository = timeSheetApplication().container.clientRepository,
                rateHistoryRepository = timeSheetApplication().container.rateHistoryRepository,
                settingsRepository = timeSheetApplication().container.settingsRepository,
                timeEntryRepository = timeSheetApplication().container.timeEntryRepository
            )
        }
        initializer {
            RecordsViewModel(
                timeSheetApplication().container.clientRepository,
                timeSheetApplication().container.timeEntryRepository,
                timeSheetApplication().container.settingsRepository,
                timeSheetApplication().container.reportRepository
            )
        }
        initializer {
            SettingsViewModel(
                settingsRepository = timeSheetApplication().container.settingsRepository,
                clientRepository = timeSheetApplication().container.clientRepository,
                timeEntryRepository = timeSheetApplication().container.timeEntryRepository,
                rateHistoryRepository = timeSheetApplication().container.rateHistoryRepository,
                database = com.example.data.local.AppDatabase.getDatabase(timeSheetApplication())
            )
        }
    }
}

fun CreationExtras.timeSheetApplication(): TimeSheetApp =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TimeSheetApp)
