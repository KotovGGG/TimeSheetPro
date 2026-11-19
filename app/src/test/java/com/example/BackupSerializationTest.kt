package com.example

import com.example.data.local.entity.*
import com.example.ui.screens.settings.BackupPayload
import com.example.ui.screens.settings.withCurrentCurrencies
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class BackupSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Test
    fun legacyBackupUsesTheSavedGlobalCurrency() {
        val entry = TimeEntry(
            clientId = 1, startTime = 0, endTime = 3600000, durationSeconds = 3600,
            rawDurationSeconds = 3600, comment = null, appliedRate = 100.0, amount = 100.0
        )
        val old = BackupPayload(1, emptyList(), listOf(RateHistoryEntry(clientId = 1, rate = 100.0,
            effectiveFrom = 0)), listOf(entry), AppSettings(currency = "RUB"))
        val restored = old.withCurrentCurrencies()
        assertEquals("RUB", restored.timeEntries.single().currencyCode)
        assertEquals("RUB", restored.rateHistory.single().currencyCode)
        assertEquals(100.0, restored.timeEntries.single().amount, 0.0)
        assertEquals(mapOf("EUR" to 5.0, "RUB" to 100.0),
            (restored.timeEntries + entry.copy(currencyCode = "EUR", amount = 5.0)).currencyTotals())
    }

    @Test
    fun roundTripSerializationPreservesAllFields() {
        val client = Client(
            id = 1,
            name = "ООО «Тест Плюс»",
            colorHex = "#625DA0",
            defaultHourlyRate = 2500.0,
            isArchived = false,
            createdAt = 1700000000L
        )
        val rateEntry = RateHistoryEntry(
            id = 10,
            clientId = 1,
            rate = 2500.0,
            effectiveFrom = 1700000000L
        )
        val timeEntry = TimeEntry(
            id = 100,
            clientId = 1,
            startTime = 1700001000L,
            endTime = 1700004600L,
            durationSeconds = 3600L,
            rawDurationSeconds = 3600L,
            comment = "Программирование функционала бэкапа",
            appliedRate = 2500.0,
            amount = 2500.0,
            status = TimeEntryStatus.PAID,
            createdAt = 1700001000L,
            updatedAt = 1700004600L
        )
        val settings = AppSettings(
            id = 1,
            globalHourlyRate = 2000.0,
            currency = "RUB",
            timeRoundingMinutes = 15,
            amountRoundingMode = AmountRoundingMode.ROUND_UP,
            amountRoundingStep = 1.0,
            firstDayOfWeek = FirstDayOfWeek.MONDAY,
            themeMode = ThemeMode.DARK
        )

        val originalPayload = BackupPayload(
            schemaVersion = 1,
            clients = listOf(client),
            rateHistory = listOf(rateEntry),
            timeEntries = listOf(timeEntry),
            appSettings = settings
        )

        val encoded = json.encodeToString(BackupPayload.serializer(), originalPayload)
        assertTrue(encoded.contains("ООО «Тест Плюс»"))
        assertTrue(encoded.contains("Программирование функционала бэкапа"))

        val decoded = json.decodeFromString(BackupPayload.serializer(), encoded)
        assertEquals(originalPayload.schemaVersion, decoded.schemaVersion)
        assertEquals(1, decoded.clients.size)
        assertEquals("ООО «Тест Плюс»", decoded.clients[0].name)
        assertEquals(2500.0, decoded.clients[0].defaultHourlyRate ?: 0.0, 0.001)

        assertEquals(1, decoded.rateHistory.size)
        assertEquals(2500.0, decoded.rateHistory[0].rate, 0.001)

        assertEquals(1, decoded.timeEntries.size)
        assertEquals("PAID", decoded.timeEntries[0].status.name)
        assertEquals("Программирование функционала бэкапа", decoded.timeEntries[0].comment)

        assertNotNull(decoded.appSettings)
        assertEquals("RUB", decoded.appSettings?.currency)
        assertEquals(ThemeMode.DARK, decoded.appSettings?.themeMode)
    }

    @Test
    fun nullAppSettingsHandledGracefully() {
        val payload = BackupPayload(
            schemaVersion = 1,
            clients = emptyList(),
            rateHistory = emptyList(),
            timeEntries = emptyList(),
            appSettings = null
        )

        val encoded = json.encodeToString(BackupPayload.serializer(), payload)
        val decoded = json.decodeFromString(BackupPayload.serializer(), encoded)
        assertNull(decoded.appSettings)
        assertTrue(decoded.clients.isEmpty())
    }

    @Test
    fun malformedJsonThrowsException() {
        assertThrows(Exception::class.java) {
            json.decodeFromString(BackupPayload.serializer(), "{ broken json: true ")
        }
    }
}
