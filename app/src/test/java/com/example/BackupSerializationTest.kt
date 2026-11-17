package com.example

import com.example.data.local.entity.*
import com.example.ui.screens.settings.BackupPayload
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
