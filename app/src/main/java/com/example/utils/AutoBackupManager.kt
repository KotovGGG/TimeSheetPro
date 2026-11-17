package com.example.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.TimeSheetApp
import com.example.data.local.AppDatabase
import com.example.ui.screens.settings.BackupPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

object AutoBackupManager {
    private const val TAG = "AutoBackupManager"
    private const val FILE_NAME = "timesheet_pro_auto_backup.json"

    private fun getLegacyPublicBackupFile(): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloadsDir, FILE_NAME)
    }

    fun getBackupFile(context: Context): File {
        val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val dir = if (externalDir != null && (externalDir.exists() || externalDir.mkdirs())) {
            externalDir
        } else {
            context.filesDir
        }
        return File(dir, FILE_NAME)
    }

    fun triggerBackup(context: Context) {
        if (com.example.BuildConfig.DEBUG) return
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = (appContext as? TimeSheetApp)?.container ?: return@launch
                val settings = container.settingsRepository.getSettingsFlow().first() ?: com.example.data.local.entity.AppSettings()
                val clients = container.clientRepository.getAllClients().first()
                val timeEntries = container.timeEntryRepository.getAllTimeEntries().first()
                val rateHistory = container.rateHistoryRepository.getAllHistory().first()

                val payload = BackupPayload(
                    schemaVersion = 1,
                    clients = clients,
                    rateHistory = rateHistory,
                    timeEntries = timeEntries,
                    appSettings = settings
                )
                
                val jsonString = Json.encodeToString(BackupPayload.serializer(), payload)
                val file = getBackupFile(appContext)
                file.writeText(jsonString)
                Log.d(TAG, "Automatic local backup saved successfully to: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Error performing auto backup: ${e.message}", e)
            }
        }
    }

    fun checkAndRestoreOnFirstRun(context: Context) {
        if (com.example.BuildConfig.DEBUG) return
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(appContext)
                // Check if current database is completely empty (no clients and no time entries)
                val clientsCount = db.clientDao().getAllClients().first().size
                val entriesCount = db.timeEntryDao().getAllTimeEntries().first().size
                
                if (clientsCount == 0 && entriesCount == 0) {
                    val appFile = getBackupFile(appContext)
                    val legacyFile = try { getLegacyPublicBackupFile() } catch (ignored: Exception) { null }
                    val file = if (appFile.exists()) appFile else (if (legacyFile?.exists() == true) legacyFile else null)
                    if (file != null && file.exists()) {
                        Log.d(TAG, "Empty database and auto-backup file found. Restoring from: ${file.absolutePath}")
                        val jsonString = file.readText()
                        val payload = Json.decodeFromString(BackupPayload.serializer(), jsonString)
                        if (payload.schemaVersion >= 1) {
                            db.restoreBackup(
                                clientsList = payload.clients,
                                rateHistoryList = payload.rateHistory,
                                timeEntriesList = payload.timeEntries,
                                settings = payload.appSettings
                            )
                            Log.d(TAG, "Automatic restore from local backup succeeded!")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring from auto backup: ${e.message}", e)
            }
        }
    }
}
