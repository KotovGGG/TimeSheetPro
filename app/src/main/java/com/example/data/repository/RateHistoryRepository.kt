package com.example.data.repository

import com.example.data.local.dao.AppSettingsDao
import com.example.data.local.dao.RateHistoryDao
import com.example.data.local.entity.RateHistoryEntry
import kotlinx.coroutines.flow.Flow

interface RateHistoryRepository {
    fun getAllHistory(): Flow<List<RateHistoryEntry>>
    fun getHistoryForClient(clientId: Long): Flow<List<RateHistoryEntry>>
    fun getGlobalHistory(): Flow<List<RateHistoryEntry>>
    suspend fun insertEntry(entry: RateHistoryEntry): Long
    suspend fun updateEntry(entry: RateHistoryEntry)
    suspend fun deleteEntry(entry: RateHistoryEntry)
    suspend fun getEffectiveRate(clientId: Long?, atTime: Long): Double
}

class RateHistoryRepositoryImpl(
    private val rateHistoryDao: RateHistoryDao,
    private val appSettingsDao: AppSettingsDao
) : RateHistoryRepository {
    override fun getAllHistory(): Flow<List<RateHistoryEntry>> = rateHistoryDao.getAllHistory()
    override fun getHistoryForClient(clientId: Long): Flow<List<RateHistoryEntry>> = rateHistoryDao.getHistoryForClient(clientId)
    override fun getGlobalHistory(): Flow<List<RateHistoryEntry>> = rateHistoryDao.getGlobalHistory()
    override suspend fun insertEntry(entry: RateHistoryEntry): Long = rateHistoryDao.insertEntry(entry)
    override suspend fun updateEntry(entry: RateHistoryEntry) = rateHistoryDao.updateEntry(entry)
    override suspend fun deleteEntry(entry: RateHistoryEntry) = rateHistoryDao.deleteEntry(entry)

    override suspend fun getEffectiveRate(clientId: Long?, atTime: Long): Double {
        if (clientId != null) {
            val personalEntry = rateHistoryDao.getEffectiveRateEntryForClient(clientId, atTime)
            if (personalEntry != null) {
                return personalEntry.rate
            }
        }
        // Fallback to global history entry, then to current app settings
        val globalEntry = rateHistoryDao.getEffectiveGlobalRateEntry(atTime)
        if (globalEntry != null) {
            return globalEntry.rate
        }
        return appSettingsDao.getSettingsDirect()?.globalHourlyRate ?: 0.0
    }
}
