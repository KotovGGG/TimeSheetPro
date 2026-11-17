package com.example

import com.example.data.local.entity.AmountRoundingMode
import com.example.data.local.entity.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class HourlyRateAndRoundingTest {

    @Test
    fun amountRoundingNoneKeepsTwoDecimals() {
        val settings = AppSettings(amountRoundingMode = AmountRoundingMode.NONE)
        assertEquals(123.45, settings.roundAmount(123.454), 0.001)
        assertEquals(123.46, settings.roundAmount(123.456), 0.001)
    }

    @Test
    fun amountRoundingUpCeilsToStep() {
        // Step = 1.0 (default when step == 0)
        val settingsStep1 = AppSettings(amountRoundingMode = AmountRoundingMode.ROUND_UP, amountRoundingStep = 1.0)
        assertEquals(101.0, settingsStep1.roundAmount(100.01), 0.001)
        assertEquals(100.0, settingsStep1.roundAmount(100.00), 0.001)

        // Step = 5.0
        val settingsStep5 = AppSettings(amountRoundingMode = AmountRoundingMode.ROUND_UP, amountRoundingStep = 5.0)
        assertEquals(105.0, settingsStep5.roundAmount(100.1), 0.001)
        assertEquals(110.0, settingsStep5.roundAmount(105.1), 0.001)
        assertEquals(100.0, settingsStep5.roundAmount(100.0), 0.001)
    }

    @Test
    fun amountRoundingDownFloorsToStep() {
        val settingsStep1 = AppSettings(amountRoundingMode = AmountRoundingMode.ROUND_DOWN, amountRoundingStep = 1.0)
        assertEquals(100.0, settingsStep1.roundAmount(100.99), 0.001)

        val settingsStep10 = AppSettings(amountRoundingMode = AmountRoundingMode.ROUND_DOWN, amountRoundingStep = 10.0)
        assertEquals(140.0, settingsStep10.roundAmount(149.99), 0.001)
        assertEquals(150.0, settingsStep10.roundAmount(150.00), 0.001)
    }

    @Test
    fun amountRoundingNearestRoundsToNearestStep() {
        val settingsStep1 = AppSettings(amountRoundingMode = AmountRoundingMode.ROUND_NEAREST, amountRoundingStep = 1.0)
        assertEquals(100.0, settingsStep1.roundAmount(100.49), 0.001)
        assertEquals(101.0, settingsStep1.roundAmount(100.51), 0.001)

        val settingsStep5 = AppSettings(amountRoundingMode = AmountRoundingMode.ROUND_NEAREST, amountRoundingStep = 5.0)
        assertEquals(100.0, settingsStep5.roundAmount(102.4), 0.001)
        assertEquals(105.0, settingsStep5.roundAmount(102.6), 0.001)
    }

    @Test
    fun timeRoundingCalculationLogic() {
        fun calculateRoundedDuration(rawSeconds: Long, roundingMinutes: Int): Long {
            if (roundingMinutes <= 0) return rawSeconds
            val roundingSeconds = roundingMinutes * 60
            val intervals = kotlin.math.round(rawSeconds.toDouble() / roundingSeconds).toLong()
            val finalIntervals = if (rawSeconds > 0 && intervals == 0L) 1L else intervals
            return finalIntervals * roundingSeconds
        }

        // 0 minutes = exact raw seconds
        assertEquals(127L, calculateRoundedDuration(127L, 0))

        // 15 minutes = 900 seconds
        // 10 seconds -> at least 1 interval (15 min = 900s)
        assertEquals(900L, calculateRoundedDuration(10L, 15))
        // 400 seconds -> rounds to 0 intervals, but floor guard ensures 1 interval (900s)
        assertEquals(900L, calculateRoundedDuration(400L, 15))
        // 500 seconds -> rounds to 1 interval (900s)
        assertEquals(900L, calculateRoundedDuration(500L, 15))
        // 1400 seconds (~23 min) -> rounds to 2 intervals (1800s = 30 min)
        assertEquals(1800L, calculateRoundedDuration(1400L, 15))
        // 0 seconds -> 0 intervals (0s)
        assertEquals(0L, calculateRoundedDuration(0L, 15))
    }

    @Test
    fun effectiveRateHierarchyResolution() {
        fun resolveEffectiveRate(
            appliedRate: Double,
            clientDefaultRate: Double?,
            globalRate: Double
        ): Double {
            return if (appliedRate > 0.0) {
                appliedRate
            } else {
                clientDefaultRate?.takeIf { it > 0.0 } ?: globalRate
            }
        }

        // 1. Explicit applied rate overrides all
        assertEquals(2500.0, resolveEffectiveRate(2500.0, 1500.0, 1000.0), 0.001)

        // 2. Client default rate used if applied rate is 0
        assertEquals(1500.0, resolveEffectiveRate(0.0, 1500.0, 1000.0), 0.001)

        // 3. Global rate used if client default rate is null or 0
        assertEquals(1000.0, resolveEffectiveRate(0.0, null, 1000.0), 0.001)
        assertEquals(1000.0, resolveEffectiveRate(0.0, 0.0, 1000.0), 0.001)

        // 4. Default 0.0 if nothing configured
        assertEquals(0.0, resolveEffectiveRate(0.0, null, 0.0), 0.001)
    }
}
