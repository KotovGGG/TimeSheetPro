package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class TimeEntryStatus {
    NOT_PAID,
    INVOICED,
    PAID
}

@Serializable
@Entity(tableName = "time_entries")
data class TimeEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val startTime: Long,
    val endTime: Long?,
    val durationSeconds: Long,
    val rawDurationSeconds: Long,
    val comment: String?,
    val appliedRate: Double,
    val amount: Double,
    val status: TimeEntryStatus = TimeEntryStatus.NOT_PAID,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "'USD'") val currencyCode: String = "USD"
)
