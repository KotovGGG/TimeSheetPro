package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.withTransaction
import com.example.data.local.dao.AppSettingsDao
import com.example.data.local.dao.ClientDao
import com.example.data.local.dao.RateHistoryDao
import com.example.data.local.dao.ReportDao
import com.example.data.local.dao.TimeEntryDao
import com.example.data.local.entity.AppSettings
import com.example.data.local.entity.Client
import com.example.data.local.entity.RateHistoryEntry
import com.example.data.local.entity.Report
import com.example.data.local.entity.TimeEntry

@Database(
    entities = [
        Client::class,
        RateHistoryEntry::class,
        TimeEntry::class,
        AppSettings::class,
        Report::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun clientDao(): ClientDao
    abstract fun rateHistoryDao(): RateHistoryDao
    abstract fun timeEntryDao(): TimeEntryDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun reportDao(): ReportDao

    suspend fun restoreBackup(
        clientsList: List<Client>,
        rateHistoryList: List<RateHistoryEntry>,
        timeEntriesList: List<TimeEntry>,
        settings: AppSettings?
    ) {
        withTransaction {
            val sqliteDb = openHelper.writableDatabase
            sqliteDb.execSQL("DELETE FROM clients")
            sqliteDb.execSQL("DELETE FROM rate_history")
            sqliteDb.execSQL("DELETE FROM time_entries")
            sqliteDb.execSQL("DELETE FROM app_settings")

            val clientDao = clientDao()
            val rateHistoryDao = rateHistoryDao()
            val timeEntryDao = timeEntryDao()
            val appSettingsDao = appSettingsDao()

            for (client in clientsList) {
                clientDao.insertClient(client)
            }
            for (rate in rateHistoryList) {
                rateHistoryDao.insertEntry(rate)
            }
            for (entry in timeEntriesList) {
                timeEntryDao.insertTimeEntry(entry)
            }
            if (settings != null) {
                appSettingsDao.saveSettings(settings)
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = buildDatabase(context, "timesheet_pro_database")
                INSTANCE = instance
                instance
            }
        }

        // Shared by production and migration tests. Unknown versions must fail without deleting data.
        internal fun buildDatabase(context: Context, name: String): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2)
                .build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `reports` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `clientNames` TEXT NOT NULL,
                        `periodLabel` TEXT NOT NULL,
                        `exportFormat` TEXT NOT NULL,
                        `totalAmount` REAL NOT NULL,
                        `totalDurationSeconds` INTEGER NOT NULL,
                        `entriesCount` INTEGER NOT NULL,
                        `entryIdsJson` TEXT NOT NULL,
                        `filePath` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `paymentPhotoUri` TEXT,
                        `paymentConfirmedAt` INTEGER,
                        `paymentNotes` TEXT,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
    }
}
