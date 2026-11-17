package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.RateHistoryEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface RateHistoryDao {
    @Query("SELECT * FROM rate_history ORDER BY effectiveFrom DESC")
    fun getAllHistory(): Flow<List<RateHistoryEntry>>

    @Query("SELECT * FROM rate_history WHERE clientId = :clientId ORDER BY effectiveFrom DESC")
    fun getHistoryForClient(clientId: Long): Flow<List<RateHistoryEntry>>

    @Query("SELECT * FROM rate_history WHERE clientId = :clientId AND effectiveFrom <= :atTime ORDER BY effectiveFrom DESC LIMIT 1")
    suspend fun getEffectiveRateEntryForClient(clientId: Long, atTime: Long): RateHistoryEntry?

    @Query("SELECT * FROM rate_history WHERE clientId IS NULL AND effectiveFrom <= :atTime ORDER BY effectiveFrom DESC LIMIT 1")
    suspend fun getEffectiveGlobalRateEntry(atTime: Long): RateHistoryEntry?

    @Query("SELECT * FROM rate_history WHERE clientId IS NULL ORDER BY effectiveFrom DESC")
    fun getGlobalHistory(): Flow<List<RateHistoryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: RateHistoryEntry): Long

    @Update
    suspend fun updateEntry(entry: RateHistoryEntry)

    @Delete
    suspend fun deleteEntry(entry: RateHistoryEntry)
}
