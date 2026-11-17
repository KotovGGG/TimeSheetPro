package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [36])
class DatabasePreservationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val client = Client(id = 42, name = "Заказчик Ё", colorHex = "#C4C3EF", defaultHourlyRate = 1200.0, createdAt = 1)

    @Test fun reopeningV2KeepsIdsAmountsAndReportPaths() = runBlocking {
        val name = "preservation-v2"
        context.deleteDatabase(name)
        val entry = TimeEntry(73, 42, 1000, 3_601_000, 3600, 3600, "Работа", 1200.0, 1200.0, TimeEntryStatus.PAID, 1, 1)
        val report = Report(91, "Заказчик Ё", "Сентябрь", "PDF", 1200.0, 3600, 1, "[73]", "/files/reports/invoice.pdf", "invoice.pdf", ReportStatus.PAID, "/files/payment.jpg", 1, "Оплачено", 1)
        AppDatabase.buildDatabase(context, name).useDatabase { db ->
            db.clientDao().insertClient(client)
            db.timeEntryDao().insertTimeEntry(entry)
            db.appSettingsDao().saveSettings(AppSettings(currency = "RUB", themeMode = ThemeMode.DARK))
            db.reportDao().insertReport(report)
        }
        AppDatabase.buildDatabase(context, name).useDatabase { db ->
            assertEquals(listOf(client), db.clientDao().getAllClients().first())
            assertEquals(listOf(entry), db.timeEntryDao().getAllTimeEntries().first())
            assertEquals(report, db.reportDao().getReportById(91))
            assertEquals("RUB", db.appSettingsDao().getSettingsDirect()?.currency)
        }
    }

    @Test fun migrationOneToTwoPreservesClients() = runBlocking {
        val name = "preservation-v1"
        context.deleteDatabase(name)
        AppDatabase.buildDatabase(context, name).useDatabase { it.clientDao().insertClient(client) }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            // v1 has the same four original tables; reports is the additive v2 migration.
            it.execSQL("DROP TABLE reports")
            it.version = 1
        }
        AppDatabase.buildDatabase(context, name).useDatabase {
            assertEquals(listOf(client), it.clientDao().getAllClients().first())
            assertTrue(it.reportDao().getAllReports().first().isEmpty())
        }
    }

    @Test fun unknownVersionFailsWithoutClearingData() = runBlocking {
        val name = "preservation-unknown"
        context.deleteDatabase(name)
        AppDatabase.buildDatabase(context, name).useDatabase { it.clientDao().insertClient(client) }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = 3 }
        AppDatabase.buildDatabase(context, name).useDatabase { db ->
            assertThrows(IllegalStateException::class.java) { db.openHelper.writableDatabase }
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT name FROM clients WHERE id = 42", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(client.name, cursor.getString(0))
            }
            assertEquals(3, db.version)
        }
    }
}

private inline fun <T> AppDatabase.useDatabase(block: (AppDatabase) -> T): T = try { block(this) } finally { close() }
