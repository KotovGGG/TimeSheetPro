package com.example.ui.screens.timer

import com.example.data.local.entity.AppSettings
import com.example.data.local.entity.Client
import com.example.data.local.entity.TimeEntry
import com.example.data.local.entity.billingRate

internal fun trackedSessionEntry(
    clientId: Long,
    client: Client?,
    comment: String,
    rate: Double?,
    durationSeconds: Long,
    settings: AppSettings,
    startTime: Long,
    endTime: Long,
    createdAt: Long = endTime
): TimeEntry {
    val interval = settings.timeRoundingMinutes * 60L
    val remainder = if (interval > 0) durationSeconds % interval else 0L
    val billedSeconds = if (remainder == 0L) durationSeconds else durationSeconds + interval - remainder
    val billing = settings.billingRate(client, rate)

    return TimeEntry(
        clientId = clientId,
        startTime = startTime,
        endTime = endTime,
        durationSeconds = billedSeconds,
        rawDurationSeconds = durationSeconds,
        comment = comment.ifBlank { null },
        appliedRate = billing.amount,
        currencyCode = billing.currencyCode,
        amount = settings.roundAmount(billedSeconds / 3600.0 * billing.amount),
        createdAt = createdAt,
        updatedAt = endTime
    )
}
