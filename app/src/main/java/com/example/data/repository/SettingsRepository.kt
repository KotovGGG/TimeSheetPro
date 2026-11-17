package com.example.data.repository

import com.example.data.local.dao.AppSettingsDao
import com.example.data.local.entity.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getSettingsFlow(): Flow<AppSettings?>
    suspend fun getSettingsDirect(): AppSettings?
    suspend fun saveSettings(settings: AppSettings)
    suspend fun updateGlobalHourlyRate(rate: Double)
}

class SettingsRepositoryImpl(private val appSettingsDao: AppSettingsDao) : SettingsRepository {
    override fun getSettingsFlow(): Flow<AppSettings?> = appSettingsDao.getSettingsFlow()
    override suspend fun getSettingsDirect(): AppSettings? = appSettingsDao.getSettingsDirect()
    override suspend fun saveSettings(settings: AppSettings) = appSettingsDao.saveSettings(settings)
    override suspend fun updateGlobalHourlyRate(rate: Double) {
        val current = getSettingsDirect() ?: AppSettings()
        appSettingsDao.saveSettings(current.copy(globalHourlyRate = rate))
    }
}
