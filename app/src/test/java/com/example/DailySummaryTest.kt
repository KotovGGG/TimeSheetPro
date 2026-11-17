package com.example

import com.example.data.local.entity.*
import com.example.ui.screens.timer.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DailySummaryTest {
    private fun entry(start: Long, end: Long? = start + 60_000, amount: Double = 12.34) =
        TimeEntry(clientId = 42, startTime = start, endTime = end, durationSeconds = 60,
            rawDurationSeconds = 60, comment = "Проверка", appliedRate = 740.4, amount = amount)

    @Test fun summaryPreservesStoredAmountsAndSkipsActiveAndPreviousDay() {
        val entries = listOf(entry(999), entry(1000), entry(1001, null), entry(1002, amount = 0.0))
        assertEquals(DailySummary(120, 12.34), summarizeDay(entries, 1000))
        assertEquals(DailySummary(), summarizeDay(emptyList(), 1000))
    }

    @Test fun largeDatasetsKeepTheSameTotals() {
        for (count in listOf(0, 1000, 10_000)) {
            val summary = summarizeDay(List(count) { entry(it.toLong(), amount = 1.25) }, 0)
            assertEquals(count * 60L, summary.seconds)
            assertEquals(count * 1.25, summary.amount, 0.0)
        }
    }

    @Test fun localMidnightHandlesDstAndDateRollover() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val cal = Calendar.getInstance(zone).apply { set(2026, Calendar.MARCH, 8, 12, 0, 0); set(Calendar.MILLISECOND, 0) }
        val start = dayStart(cal.timeInMillis, zone)
        cal.add(Calendar.DAY_OF_MONTH, 1)
        assertEquals(23 * 3_600_000L, dayStart(cal.timeInMillis, zone) - start)
        assertEquals(start, dayStart(start + 1, zone))
        assertTrue(dayStart(start - 1, zone) < start)
    }

    @Test fun roundingContractRemainsUnchanged() {
        assertEquals(12.35, AppSettings().roundAmount(12.3451), 0.00001)
        assertEquals(15.0, AppSettings(amountRoundingMode = AmountRoundingMode.ROUND_UP, amountRoundingStep = 5.0).roundAmount(12.34), 0.0)
        assertEquals(10.0, AppSettings(amountRoundingMode = AmountRoundingMode.ROUND_DOWN, amountRoundingStep = 5.0).roundAmount(12.34), 0.0)
        assertEquals(10.0, AppSettings(amountRoundingMode = AmountRoundingMode.ROUND_NEAREST, amountRoundingStep = 5.0).roundAmount(12.34), 0.0)
    }
}
