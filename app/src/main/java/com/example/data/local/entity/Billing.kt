package com.example.data.local.entity

import kotlinx.serialization.json.Json

val billingCurrencies = listOf("USD", "EUR", "RUB", "GBP", "CHF", "JPY", "KZT", "BYN")

data class BillingRate(val amount: Double, val currencyCode: String)

fun AppSettings.billingRate(client: Client?, override: Double? = null): BillingRate =
    BillingRate(
        amount = override?.takeIf { it.isFinite() && it > 0.0 }
            ?: client?.defaultHourlyRate?.takeIf { it.isFinite() && it > 0.0 }
            ?: globalHourlyRate,
        currencyCode = client?.currencyCode ?: currency
    )

fun Iterable<TimeEntry>.currencyTotals(): Map<String, Double> {
    val totals = sortedMapOf<String, Double>()
    for (entry in this) {
        totals[entry.currencyCode] = (totals[entry.currencyCode] ?: 0.0) + entry.amount
    }
    return totals
}

fun Report.currencyTotals(legacyCurrency: String): Map<String, Double> =
    currencyTotalsJson?.let { Json.decodeFromString<Map<String, Double>>(it) }
        ?: mapOf(legacyCurrency to totalAmount)
