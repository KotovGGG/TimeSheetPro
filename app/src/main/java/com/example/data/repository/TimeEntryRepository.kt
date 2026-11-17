package com.example.data.repository

import com.example.data.local.dao.ClientUnpaidBalanceHelper
import com.example.data.local.dao.TimeEntryDao
import com.example.data.local.entity.TimeEntry
import com.example.data.local.entity.TimeEntryStatus
import kotlinx.coroutines.flow.Flow

enum class SortOrder {
    TIME_DESC,
    TIME_ASC,
    CLIENT
}

interface TimeEntryRepository {
    fun getAllTimeEntries(): Flow<List<TimeEntry>>
    fun getFilteredTimeEntries(
        searchQuery: String,
        clientIds: List<Long>?,
        statuses: List<TimeEntryStatus>?,
        sortOrder: SortOrder
    ): Flow<List<TimeEntry>>
    fun getTimeEntryById(id: Long): Flow<TimeEntry?>
    fun getActiveTimeEntry(): Flow<TimeEntry?>
    suspend fun insertTimeEntry(entry: TimeEntry): Long
    suspend fun updateTimeEntry(entry: TimeEntry)
    suspend fun deleteTimeEntry(entry: TimeEntry)
    suspend fun updateStatuses(ids: List<Long>, status: TimeEntryStatus)
    suspend fun deleteEntries(ids: List<Long>)
    fun getUnpaidBalances(): Flow<List<ClientUnpaidBalanceHelper>>
}

class TimeEntryRepositoryImpl(private val timeEntryDao: TimeEntryDao) : TimeEntryRepository {
    override fun getAllTimeEntries(): Flow<List<TimeEntry>> = timeEntryDao.getAllTimeEntries()
    
    override fun getFilteredTimeEntries(
        searchQuery: String,
        clientIds: List<Long>?,
        statuses: List<TimeEntryStatus>?,
        sortOrder: SortOrder
    ): Flow<List<TimeEntry>> {
        val hasSearch = if (searchQuery.isBlank()) 0 else 1
        val formattedSearch = if (hasSearch == 1) "%$searchQuery%" else ""
        
        val filterAllClients = if (clientIds.isNullOrEmpty()) 1 else 0
        val nonNullClientIds = clientIds ?: emptyList()
        
        val filterAllStatuses = if (statuses.isNullOrEmpty()) 1 else 0
        val nonNullStatuses = statuses ?: emptyList()
        
        return when (sortOrder) {
            SortOrder.TIME_DESC -> timeEntryDao.getEntriesSortedByTimeDesc(
                hasSearch = hasSearch,
                searchQuery = formattedSearch,
                filterAllClients = filterAllClients,
                clientIds = nonNullClientIds,
                filterAllStatuses = filterAllStatuses,
                statuses = nonNullStatuses
            )
            SortOrder.TIME_ASC -> timeEntryDao.getEntriesSortedByTimeAsc(
                hasSearch = hasSearch,
                searchQuery = formattedSearch,
                filterAllClients = filterAllClients,
                clientIds = nonNullClientIds,
                filterAllStatuses = filterAllStatuses,
                statuses = nonNullStatuses
            )
            SortOrder.CLIENT -> timeEntryDao.getEntriesSortedByClient(
                hasSearch = hasSearch,
                searchQuery = formattedSearch,
                filterAllClients = filterAllClients,
                clientIds = nonNullClientIds,
                filterAllStatuses = filterAllStatuses,
                statuses = nonNullStatuses
            )
        }
    }

    override fun getTimeEntryById(id: Long): Flow<TimeEntry?> = timeEntryDao.getTimeEntryById(id)
    override fun getActiveTimeEntry(): Flow<TimeEntry?> = timeEntryDao.getActiveTimeEntry()
    override suspend fun insertTimeEntry(entry: TimeEntry): Long = timeEntryDao.insertTimeEntry(entry)
    override suspend fun updateTimeEntry(entry: TimeEntry) = timeEntryDao.updateTimeEntry(entry)
    override suspend fun deleteTimeEntry(entry: TimeEntry) = timeEntryDao.deleteTimeEntry(entry)
    
    override suspend fun updateStatuses(ids: List<Long>, status: TimeEntryStatus) {
        timeEntryDao.updateStatuses(ids, status, System.currentTimeMillis())
    }

    override suspend fun deleteEntries(ids: List<Long>) {
        timeEntryDao.deleteEntries(ids)
    }

    override fun getUnpaidBalances(): Flow<List<ClientUnpaidBalanceHelper>> = timeEntryDao.getUnpaidBalancesDirect()
}
