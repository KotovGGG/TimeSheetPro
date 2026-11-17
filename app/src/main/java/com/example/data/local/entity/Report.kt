package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class ReportStatus {
    INVOICED, // Счёт выставлен
    PAID      // Оплачен
}

@Serializable
@Entity(tableName = "reports")
data class Report(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientNames: String,
    val periodLabel: String,
    val exportFormat: String,
    val totalAmount: Double,
    val totalDurationSeconds: Long,
    val entriesCount: Int,
    val entryIdsJson: String,
    val filePath: String,
    val fileName: String,
    val status: ReportStatus = ReportStatus.INVOICED,
    val paymentPhotoUri: String? = null,
    val paymentConfirmedAt: Long? = null,
    val paymentNotes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
