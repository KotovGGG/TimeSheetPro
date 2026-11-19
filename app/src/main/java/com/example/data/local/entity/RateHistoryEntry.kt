package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "rate_history")
data class RateHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long?, // Null means global rate
    val rate: Double,
    val effectiveFrom: Long,
    @ColumnInfo(defaultValue = "'USD'") val currencyCode: String = "USD"
)
