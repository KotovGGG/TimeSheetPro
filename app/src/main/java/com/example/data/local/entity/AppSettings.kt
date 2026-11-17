package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class AmountRoundingMode {
    NONE,
    ROUND_UP,
    ROUND_DOWN,
    ROUND_NEAREST
}

@Serializable
enum class FirstDayOfWeek {
    MONDAY,
    SUNDAY
}

@Serializable
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

@Serializable
@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1, // Fixed ID=1 for single row singleton
    val globalHourlyRate: Double = 0.0,
    val currency: String = "USD",
    val timeRoundingMinutes: Int = 0,
    val amountRoundingMode: AmountRoundingMode = AmountRoundingMode.NONE,
    val amountRoundingStep: Double = 0.0,
    val firstDayOfWeek: FirstDayOfWeek = FirstDayOfWeek.MONDAY,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
) {
    fun roundAmount(rawAmount: Double): Double {
        if (amountRoundingMode == AmountRoundingMode.NONE) {
            return kotlin.math.round(rawAmount * 100.0) / 100.0
        }
        val step = if (amountRoundingStep > 0.0) amountRoundingStep else 1.0
        val factor = 1.0 / step
        return when (amountRoundingMode) {
            AmountRoundingMode.ROUND_UP -> kotlin.math.ceil(rawAmount * factor) / factor
            AmountRoundingMode.ROUND_DOWN -> kotlin.math.floor(rawAmount * factor) / factor
            AmountRoundingMode.ROUND_NEAREST -> kotlin.math.round(rawAmount * factor) / factor
            AmountRoundingMode.NONE -> kotlin.math.round(rawAmount * 100.0) / 100.0
        }
    }
}
