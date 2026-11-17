package com.example.ui.screens.timer

import androidx.compose.runtime.Immutable
import com.example.data.local.entity.TimeEntry
import java.util.Calendar
import java.util.TimeZone

@Immutable
data class DailySummary(val seconds: Long = 0, val amount: Double = 0.0)

internal fun dayStart(now: Long, zone: TimeZone = TimeZone.getDefault()): Long =
    Calendar.getInstance(zone).apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

internal fun summarizeDay(entries: List<TimeEntry>, start: Long): DailySummary {
    var seconds = 0L
    var amount = 0.0
    for (entry in entries) {
        // Preserve the existing eligibility and stored financial totals.
        if (entry.startTime >= start && entry.endTime != null) {
            seconds += entry.durationSeconds
            amount += entry.amount
        }
    }
    return DailySummary(seconds, amount)
}
