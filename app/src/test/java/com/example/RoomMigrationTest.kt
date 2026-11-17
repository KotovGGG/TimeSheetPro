package com.example

import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AmountRoundingMode
import com.example.data.local.entity.FirstDayOfWeek
import com.example.data.local.entity.ThemeMode
import com.example.data.local.entity.TimeEntryStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [36])
class RoomMigrationTest {

    private val TEST_DB = "migration-test-db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2AllTablesPreservedAndReportsCreated() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(TEST_DB)

        // 1. Create DB in Version 1 from schema 1.json
        var db = helper.createDatabase(TEST_DB, 1)

        // 2. Insert test data into all 4 V1 tables using raw SQL
        // Test client with Unicode and rate
        db.execSQL("""
            INSERT INTO clients (id, name, colorHex, defaultHourlyRate, isArchived, createdAt)
            VALUES (1, 'Заказчик ООО «Альфа»', '#A080E0', 1500.0, 0, 1000)
        """.trimIndent())
        // Test client with null rate and archived
        db.execSQL("""
            INSERT INTO clients (id, name, colorHex, defaultHourlyRate, isArchived, createdAt)
            VALUES (2, 'Клиент Бета (Без ставки)', '#C4C3EF', NULL, 1, 1050)
        """.trimIndent())

        // Rate history for client 1 and a global rate (null clientId)
        db.execSQL("""
            INSERT INTO rate_history (id, clientId, rate, effectiveFrom)
            VALUES (10, 1, 1500.0, 1000)
        """.trimIndent())
        db.execSQL("""
            INSERT INTO rate_history (id, clientId, rate, effectiveFrom)
            VALUES (11, NULL, 1200.0, 900)
        """.trimIndent())

        // Completed time entry
        db.execSQL("""
            INSERT INTO time_entries (id, clientId, startTime, endTime, durationSeconds, rawDurationSeconds, comment, appliedRate, amount, status, createdAt, updatedAt)
            VALUES (100, 1, 1000, 4600, 3600, 3600, 'Разработка фичи №1', 1500.0, 1500.0, 'PAID', 1000, 4600)
        """.trimIndent())
        // Running time entry (endTime null, comment null)
        db.execSQL("""
            INSERT INTO time_entries (id, clientId, startTime, endTime, durationSeconds, rawDurationSeconds, comment, appliedRate, amount, status, createdAt, updatedAt)
            VALUES (101, 2, 5000, NULL, 1800, 1800, NULL, 1200.0, 600.0, 'NOT_PAID', 5000, 5000)
        """.trimIndent())

        // App settings singleton
        db.execSQL("""
            INSERT INTO app_settings (id, globalHourlyRate, currency, timeRoundingMinutes, amountRoundingMode, amountRoundingStep, firstDayOfWeek, themeMode)
            VALUES (1, 1200.0, 'RUB', 15, 'ROUND_UP', 1.0, 'MONDAY', 'DARK')
        """.trimIndent())

        db.close()

        // 3. Run migration to Version 2 and validate schema against 2.json
        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2)

        // 4. Verify data in all 4 original tables
        db.query("SELECT * FROM clients WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Заказчик ООО «Альфа»", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertEquals("#A080E0", cursor.getString(cursor.getColumnIndexOrThrow("colorHex")))
            assertEquals(1500.0, cursor.getDouble(cursor.getColumnIndexOrThrow("defaultHourlyRate")), 0.001)
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("isArchived")))
            assertEquals(1000L, cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")))
        }

        db.query("SELECT * FROM clients WHERE id = 2").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Клиент Бета (Без ставки)", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("defaultHourlyRate")))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("isArchived")))
        }

        db.query("SELECT * FROM rate_history WHERE id = 10").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(cursor.getColumnIndexOrThrow("clientId")))
            assertEquals(1500.0, cursor.getDouble(cursor.getColumnIndexOrThrow("rate")), 0.001)
            assertEquals(1000L, cursor.getLong(cursor.getColumnIndexOrThrow("effectiveFrom")))
        }

        db.query("SELECT * FROM rate_history WHERE id = 11").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("clientId")))
            assertEquals(1200.0, cursor.getDouble(cursor.getColumnIndexOrThrow("rate")), 0.001)
        }

        db.query("SELECT * FROM time_entries WHERE id = 100").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(cursor.getColumnIndexOrThrow("clientId")))
            assertEquals(1000L, cursor.getLong(cursor.getColumnIndexOrThrow("startTime")))
            assertEquals(4600L, cursor.getLong(cursor.getColumnIndexOrThrow("endTime")))
            assertEquals(3600L, cursor.getLong(cursor.getColumnIndexOrThrow("durationSeconds")))
            assertEquals("Разработка фичи №1", cursor.getString(cursor.getColumnIndexOrThrow("comment")))
            assertEquals("PAID", cursor.getString(cursor.getColumnIndexOrThrow("status")))
            assertEquals(1500.0, cursor.getDouble(cursor.getColumnIndexOrThrow("amount")), 0.001)
        }

        db.query("SELECT * FROM time_entries WHERE id = 101").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2L, cursor.getLong(cursor.getColumnIndexOrThrow("clientId")))
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("endTime")))
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("comment")))
            assertEquals("NOT_PAID", cursor.getString(cursor.getColumnIndexOrThrow("status")))
            assertEquals(600.0, cursor.getDouble(cursor.getColumnIndexOrThrow("amount")), 0.001)
        }

        db.query("SELECT * FROM app_settings WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("RUB", cursor.getString(cursor.getColumnIndexOrThrow("currency")))
            assertEquals("DARK", cursor.getString(cursor.getColumnIndexOrThrow("themeMode")))
            assertEquals("ROUND_UP", cursor.getString(cursor.getColumnIndexOrThrow("amountRoundingMode")))
            assertEquals(15, cursor.getInt(cursor.getColumnIndexOrThrow("timeRoundingMinutes")))
        }

        // 5. Verify reports table exists, is empty, and accepts insertions
        db.query("SELECT count(*) FROM reports").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }

        db.execSQL("""
            INSERT INTO reports (id, clientNames, periodLabel, exportFormat, totalAmount, totalDurationSeconds, entriesCount, entryIdsJson, filePath, fileName, status, paymentPhotoUri, paymentConfirmedAt, paymentNotes, createdAt)
            VALUES (500, 'Заказчик ООО «Альфа»', 'Октябрь 2026', 'PDF', 1500.0, 3600, 1, '[100]', '/path/report.pdf', 'report.pdf', 'INVOICED', NULL, NULL, NULL, 5000)
        """.trimIndent())

        db.query("SELECT * FROM reports WHERE id = 500").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Заказчик ООО «Альфа»", cursor.getString(cursor.getColumnIndexOrThrow("clientNames")))
            assertEquals("INVOICED", cursor.getString(cursor.getColumnIndexOrThrow("status")))
        }

        db.close()

        // 6. Verify Room Database runtime can open the migrated database without errors
        val roomDb = AppDatabase.buildDatabase(context, TEST_DB)
        runBlocking {
            val clients = roomDb.clientDao().getAllClients().first()
            assertEquals(2, clients.size)
            assertEquals(setOf("Заказчик ООО «Альфа»", "Клиент Бета (Без ставки)"), clients.map { it.name }.toSet())
            val client1 = roomDb.clientDao().getClientById(1).first()
            assertNotNull(client1)
            assertEquals("Заказчик ООО «Альфа»", client1?.name)
            assertEquals(1500.0, client1?.defaultHourlyRate ?: 0.0, 0.001)

            val reports = roomDb.reportDao().getAllReports().first()
            assertEquals(1, reports.size)
            assertEquals(500L, reports[0].id)

            val settings = roomDb.appSettingsDao().getSettingsDirect()
            assertNotNull(settings)
            assertEquals("RUB", settings?.currency)
            assertEquals(ThemeMode.DARK, settings?.themeMode)
            assertEquals(AmountRoundingMode.ROUND_UP, settings?.amountRoundingMode)
        }
        roomDb.close()
    }

    @Test
    fun unknownFutureVersionFailsSafelyWithoutDataDestruction() {
        val dbName = "unknown-version-test-db"
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(dbName)

        // Create V1 and insert data
        val db = helper.createDatabase(dbName, 1)
        db.execSQL("INSERT INTO clients (id, name, colorHex, defaultHourlyRate, isArchived, createdAt) VALUES (99, 'Сохранённый Клиент', '#112233', 2000.0, 0, 100)")
        db.close()

        // Manually bump version to 3 (simulating future version)
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.version = 3
        }

        // Room builder without fallbackToDestructiveMigration must throw IllegalStateException
        val roomDb = AppDatabase.buildDatabase(context, dbName)
        assertThrows(IllegalStateException::class.java) {
            roomDb.openHelper.writableDatabase
        }
        roomDb.close()

        // Verify SQLite data on disk is 100% preserved
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
            sqlite.rawQuery("SELECT name FROM clients WHERE id = 99", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Сохранённый Клиент", cursor.getString(0))
            }
            assertEquals(3, sqlite.version)
        }
    }
}
