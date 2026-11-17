package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.Report
import com.example.data.local.entity.ReportStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {
    @Query("SELECT * FROM reports ORDER BY createdAt DESC")
    fun getAllReports(): Flow<List<Report>>

    @Query("SELECT * FROM reports WHERE id = :id")
    suspend fun getReportById(id: Long): Report?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: Report): Long

    @Update
    suspend fun updateReport(report: Report)

    @Delete
    suspend fun deleteReport(report: Report)

    @Query("UPDATE reports SET status = :status, paymentPhotoUri = :paymentPhotoUri, paymentConfirmedAt = :paymentConfirmedAt, paymentNotes = :paymentNotes WHERE id = :id")
    suspend fun markAsPaid(id: Long, status: ReportStatus, paymentPhotoUri: String?, paymentConfirmedAt: Long, paymentNotes: String?)
}
