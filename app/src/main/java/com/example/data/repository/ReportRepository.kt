package com.example.data.repository

import com.example.data.local.dao.ReportDao
import com.example.data.local.entity.Report
import com.example.data.local.entity.ReportStatus
import kotlinx.coroutines.flow.Flow

interface ReportRepository {
    fun getAllReports(): Flow<List<Report>>
    suspend fun getReportById(id: Long): Report?
    suspend fun insertReport(report: Report): Long
    suspend fun updateReport(report: Report)
    suspend fun deleteReport(report: Report)
    suspend fun markAsPaid(id: Long, paymentPhotoUri: String?, paymentNotes: String?)
}

class ReportRepositoryImpl(private val reportDao: ReportDao) : ReportRepository {
    override fun getAllReports(): Flow<List<Report>> = reportDao.getAllReports()
    override suspend fun getReportById(id: Long): Report? = reportDao.getReportById(id)
    override suspend fun insertReport(report: Report): Long = reportDao.insertReport(report)
    override suspend fun updateReport(report: Report) = reportDao.updateReport(report)
    override suspend fun deleteReport(report: Report) = reportDao.deleteReport(report)
    override suspend fun markAsPaid(id: Long, paymentPhotoUri: String?, paymentNotes: String?) {
        reportDao.markAsPaid(
            id = id,
            status = ReportStatus.PAID,
            paymentPhotoUri = paymentPhotoUri,
            paymentConfirmedAt = System.currentTimeMillis(),
            paymentNotes = paymentNotes
        )
    }
}
