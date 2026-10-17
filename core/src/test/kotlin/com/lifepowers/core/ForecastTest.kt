package com.lifepowers.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ForecastTest {
    @Test fun tiersFollowThresholds() {
        assertEquals(Tier.BEST, Forecast.tier(1.0, 3))
        assertEquals(Tier.GOOD, Forecast.tier(0.5, 3))
        assertEquals(Tier.NOT_GREAT, Forecast.tier(-0.5, 3))
        assertEquals(Tier.LIKELY_HARD, Forecast.tier(-1.0, 3))
    }

    @Test fun deadBandFollowsPreviousTier() {
        assertEquals(Tier.GOOD, Forecast.tier(0.0, 3, Tier.BEST))
        assertEquals(Tier.GOOD, Forecast.tier(0.0, 3, Tier.GOOD))
        assertEquals(Tier.NOT_GREAT, Forecast.tier(0.0, 3, Tier.LIKELY_HARD))
        assertEquals(Tier.NOT_GREAT, Forecast.tier(0.0, 3, null))
    }

    @Test fun tooFewInputsIsUnknown() {
        assertEquals(Tier.UNKNOWN, Forecast.tier(2.0, 2))
        assertEquals(Tier.UNKNOWN, Forecast.tier(null, 10))
    }

    @Test fun orientationFlipsBadMetrics() {
        assertEquals(1.0, Forecast.orient(Metrics.HRV_NIGHT_AVG, 1.0))
        assertEquals(-1.0, Forecast.orient(Metrics.HR_RESTING, 1.0))
        assertEquals(null, Forecast.orient(Metrics.WEIGHT, 1.0))
    }

    @Test fun dayCombinesComponents() {
        val recovery = Forecast.compute(Component.RECOVERY, listOf(
            ForecastInput(Metrics.HRV_NIGHT_AVG, 1.2, 1.5), ForecastInput(Metrics.HR_RESTING, 0.8), ForecastInput(Metrics.SLEEP_TOTAL_MIN, 0.9)))
        val metabolic = Forecast.compute(Component.METABOLIC, listOf(
            ForecastInput(Metrics.GLUCOSE_OVERNIGHT_MEAN, -0.6), ForecastInput(Metrics.GLUCOSE_FASTING, -0.4), ForecastInput(Metrics.GLUCOSE_SAMPLE, -0.5)))
        assertEquals(Tier.BEST, recovery.tier)
        assertEquals(Tier.NOT_GREAT, metabolic.tier)
        val day = Forecast.day(listOf(recovery, metabolic))
        assertEquals(Tier.GOOD, day.tier)
    }

    @Test fun smallComponentsCanStillShowATier() {
        val metabolic = Forecast.compute(Component.METABOLIC, listOf(
            ForecastInput(Metrics.GLUCOSE_OVERNIGHT_MEAN, -0.6), ForecastInput(Metrics.GLUCOSE_FASTING, -0.4)))
        assertEquals(Tier.NOT_GREAT, metabolic.tier)
        val hormonal = Forecast.compute(Component.HORMONAL, listOf(ForecastInput("phase", -0.3)))
        assertEquals(Tier.NOT_GREAT, hormonal.tier)
        val recoveryTooThin = Forecast.compute(Component.RECOVERY, listOf(ForecastInput(Metrics.HRV_NIGHT_AVG, 1.0)))
        assertEquals(Tier.UNKNOWN, recoveryTooThin.tier)
        assertEquals(3, Forecast.minInputs(Component.DAY))
    }
}
