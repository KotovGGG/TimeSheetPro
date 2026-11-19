package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.TimeEntry
import com.example.data.local.entity.TimeEntryStatus
import kotlinx.coroutines.flow.Flow

data class ClientUnpaidBalanceHelper(
    val clientId: Long,
    val unpaidAmount: Double,
    val currencyCode: String = "USD"
)

@Dao
interface TimeEntryDao {
    @Query("SELECT * FROM time_entries ORDER BY startTime DESC")
    fun getAllTimeEntries(): Flow<List<TimeEntry>>

    @Query("SELECT * FROM time_entries WHERE id = :id")
    fun getTimeEntryById(id: Long): Flow<TimeEntry?>

    @Query("SELECT * FROM time_entries WHERE endTime IS NULL LIMIT 1")
    fun getActiveTimeEntry(): Flow<TimeEntry?>

    @Query("""
        SELECT * FROM time_entries 
        WHERE (:hasSearch = 0 OR comment LIKE :searchQuery)
          AND (:filterAllClients = 1 OR clientId IN (:clientIds))
          AND (:filterAllStatuses = 1 OR status IN (:statuses))
          AND endTime IS NOT NULL
        ORDER BY startTime DESC
    """)
    fun getEntriesSortedByTimeDesc(
        hasSearch: Int,
        searchQuery: String,
        filterAllClients: Int,
        clientIds: List<Long>,
        filterAllStatuses: Int,
        statuses: List<TimeEntryStatus>
    ): Flow<List<TimeEntry>>

    @Query("""
        SELECT * FROM time_entries 
        WHERE (:hasSearch = 0 OR comment LIKE :searchQuery)
          AND (:filterAllClients = 1 OR clientId IN (:clientIds))
          AND (:filterAllStatuses = 1 OR status IN (:statuses))
          AND endTime IS NOT NULL
        ORDER BY startTime ASC
    """)
    fun getEntriesSortedByTimeAsc(
        hasSearch: Int,
        searchQuery: String,
        filterAllClients: Int,
        clientIds: List<Long>,
        filterAllStatuses: Int,
        statuses: List<TimeEntryStatus>
    ): Flow<List<TimeEntry>>

    @Query("""
        SELECT t.* FROM time_entries t
        LEFT JOIN clients c ON t.clientId = c.id
        WHERE (:hasSearch = 0 OR t.comment LIKE :searchQuery)
          AND (:filterAllClients = 1 OR t.clientId IN (:clientIds))
          AND (:filterAllStatuses = 1 OR t.status IN (:statuses))
          AND t.endTime IS NOT NULL
        ORDER BY c.name ASC, t.startTime DESC
    """)
    fun getEntriesSortedByClient(
        hasSearch: Int,
        searchQuery: String,
        filterAllClients: Int,
        clientIds: List<Long>,
        filterAllStatuses: Int,
        statuses: List<TimeEntryStatus>
    ): Flow<List<TimeEntry>>

    @Query("UPDATE time_entries SET status = :status, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun updateStatuses(ids: List<Long>, status: TimeEntryStatus, updatedAt: Long)

    @Query("DELETE FROM time_entries WHERE id IN (:ids)")
    suspend fun deleteEntries(ids: List<Long>)

    @Query("""
        SELECT clientId, currencyCode, SUM(amount) as unpaidAmount
        FROM time_entries 
        WHERE status = 'NOT_PAID' AND endTime IS NOT NULL
        GROUP BY clientId, currencyCode
    """)
    fun getUnpaidBalancesDirect(): Flow<List<ClientUnpaidBalanceHelper>>

    @Query("""
        SELECT id FROM time_entries
        WHERE clientId = :clientId AND startTime = :sessionStart AND createdAt = :sessionStart
        ORDER BY id LIMIT 1
    """)
    suspend fun findSavedTimerSessionId(clientId: Long, sessionStart: Long): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimeEntry(entry: TimeEntry): Long

    @Update
    suspend fun updateTimeEntry(entry: TimeEntry)

    @Delete
    suspend fun deleteTimeEntry(entry: TimeEntry)
}
