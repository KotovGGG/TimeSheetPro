package com.example.di

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.repository.*

interface AppContainer {
    val clientRepository: ClientRepository
    val rateHistoryRepository: RateHistoryRepository
    val timeEntryRepository: TimeEntryRepository
    val settingsRepository: SettingsRepository
    val reportRepository: ReportRepository
}

class AppDataContainer(private val context: Context) : AppContainer {
    private val database: AppDatabase by lazy {
        AppDatabase.getDatabase(context)
    }

    override val clientRepository: ClientRepository by lazy {
        ClientRepositoryImpl(database.clientDao())
    }

    override val rateHistoryRepository: RateHistoryRepository by lazy {
        RateHistoryRepositoryImpl(database.rateHistoryDao(), database.appSettingsDao())
    }

    override val timeEntryRepository: TimeEntryRepository by lazy {
        TimeEntryRepositoryImpl(database.timeEntryDao())
    }

    override val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(database.appSettingsDao())
    }

    override val reportRepository: ReportRepository by lazy {
        ReportRepositoryImpl(database.reportDao())
    }
}
