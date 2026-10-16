package com.lifepowers.core

/** The five tiers and their colors. Fixed by the design. */
enum class Tier(val hex: String, val label: String) {
    BEST("#E6389A", "best"),
    GOOD("#FFB3DD", "good"),
    NOT_GREAT("#CBB2ED", "softer"),
    LIKELY_HARD("#B1C6E1", "go gently"),
    UNKNOWN("#FFFFF4", "not enough yet");
}

enum class Component { DAY, RECOVERY, METABOLIC, CARDIO, HORMONAL, MIND, ENVIRONMENT, SKY, CRAVINGS }

/** One input to a forecast: a z score already oriented so positive is good. */
data class ForecastInput(val metric: String, val orientedZ: Double, val weight: Double = 1.0)

data class ForecastResult(val component: Component, val tier: Tier, val score: Double?, val inputsUsed: Int, val reasons: List<String>)

object Forecast {
    const val MIN_INPUTS = 3
    const val BEST_ABOVE = 0.8
    const val GOOD_ABOVE = 0.2
    const val NOT_GREAT_BELOW = -0.2
    const val HARD_BELOW = -0.8

    /** Orient a raw z so that positive means good for the person, using the metric catalog. */
    fun orient(metric: String, rawZ: Double): Double? = when (Metrics.spec(metric).higherIsBetter) {
        true -> rawZ
        false -> -rawZ
        null -> null
    }

    fun composite(inputs: List<ForecastInput>): Double? {
        if (inputs.isEmpty()) return null
        val w = inputs.sumOf { it.weight }
        if (w <= 0) return null
        return inputs.sumOf { it.orientedZ * it.weight } / w
    }

    /**
     * Maps a composite to a tier. The dead band between -0.2 and +0.2 follows the previous tier
     * so an ordinary morning does not flicker.
     */
    fun tier(score: Double?, inputsUsed: Int, previous: Tier? = null): Tier {
        if (score == null || inputsUsed < MIN_INPUTS) return Tier.UNKNOWN
        return when {
            score > BEST_ABOVE -> Tier.BEST
            score > GOOD_ABOVE -> Tier.GOOD
            score < HARD_BELOW -> Tier.LIKELY_HARD
            score < NOT_GREAT_BELOW -> Tier.NOT_GREAT
            else -> if (previous == Tier.GOOD || previous == Tier.BEST) Tier.GOOD else Tier.NOT_GREAT
        }
    }

    fun compute(component: Component, inputs: List<ForecastInput>, previous: Tier? = null): ForecastResult {
        val score = composite(inputs)
        val t = tier(score, inputs.size, previous)
        val reasons = inputs.sortedByDescending { kotlin.math.abs(it.orientedZ) }.take(3).map { it.metric }
        return ForecastResult(component, t, score, inputs.size, reasons)
    }

    /** Which metrics feed which component, with default weights. Weights are refined from history later. */
    val componentInputs: Map<Component, Map<String, Double>> = mapOf(
        Component.RECOVERY to mapOf(
            Metrics.HRV_NIGHT_AVG to 1.5, Metrics.HR_RESTING to 1.0, Metrics.SLEEP_TOTAL_MIN to 1.0,
            Metrics.SLEEP_DEEP_MIN to 0.8, Metrics.SLEEP_EFFICIENCY to 0.6, Metrics.TEMP_DEVIATION to 0.5,
            Metrics.SCORE_READINESS to 1.0,
        ),
        Component.METABOLIC to mapOf(
            Metrics.GLUCOSE_OVERNIGHT_MEAN to 1.0, Metrics.GLUCOSE_FASTING to 1.0,
        ),
        Component.CARDIO to mapOf(
            Metrics.BP_SYSTOLIC to 1.0, Metrics.BP_DIASTOLIC to 0.8, Metrics.HRV_NIGHT_AVG to 0.6,
        ),
        Component.MIND to mapOf(
            Metrics.VOICE_FLATNESS_INDEX to 1.0, Metrics.VOICE_TENSION_INDEX to 0.8, Metrics.VOICE_ENERGY_INDEX to 0.8,
            Metrics.WORDS_VALENCE to 0.6, Metrics.FEEL_EASE to 0.6, Metrics.FEEL_ENERGY to 0.6,
        ),
        Component.ENVIRONMENT to mapOf(
            Metrics.WEATHER_DAYLIGHT_MIN to 0.8,
        ),
    )

    /** The day forecast weights its components. */
    val dayWeights: Map<Component, Double> = mapOf(
        Component.RECOVERY to 1.5, Component.METABOLIC to 1.0, Component.CARDIO to 0.8,
        Component.MIND to 1.2, Component.ENVIRONMENT to 0.5, Component.SKY to 0.3, Component.HORMONAL to 0.6,
    )

    fun day(components: List<ForecastResult>, previous: Tier? = null): ForecastResult {
        val usable = components.filter { it.score != null && it.component != Component.DAY }
        val inputs = usable.map { ForecastInput(it.component.name, it.score!!, dayWeights[it.component] ?: 0.5) }
        val score = composite(inputs)
        val totalInputs = usable.sumOf { it.inputsUsed }
        val t = tier(score, totalInputs, previous)
        return ForecastResult(Component.DAY, t, score, totalInputs, usable.sortedBy { it.score }.map { it.component.name })
    }
}
