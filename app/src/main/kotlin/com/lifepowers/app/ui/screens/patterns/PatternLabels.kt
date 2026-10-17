package com.lifepowers.app.ui.screens.patterns

import com.lifepowers.app.ui.components.Fmt
import com.lifepowers.core.Insight
import com.lifepowers.core.Metrics

/**
 * Turns pattern keys into friendly words. Keys are metric keys ("sleep.deep_min"), habit series
 * ("habit:Walk"), or event kinds ("alcohol", "symptom.headache"). Outcome labels read after "Your",
 * input labels after "on days with more", so both are plain noun phrases.
 */
object PatternLabels {

    private const val HABIT_PREFIX = "habit:"
    private const val EVENT_PREFIX = "event:"

    private val names: Map<String, String> = mapOf(
        Metrics.SLEEP_TOTAL_MIN to "total sleep",
        Metrics.SLEEP_DEEP_MIN to "deep sleep",
        Metrics.SLEEP_REM_MIN to "REM sleep",
        Metrics.SLEEP_LIGHT_MIN to "light sleep",
        Metrics.SLEEP_AWAKE_MIN to "time awake at night",
        Metrics.SLEEP_ONSET_LATENCY_MIN to "time to fall asleep",
        Metrics.SLEEP_BEDTIME_UTC to "bedtime",
        Metrics.SLEEP_WAKE_UTC to "wake time",
        Metrics.SLEEP_EFFICIENCY to "sleep efficiency",
        Metrics.HRV_NIGHT_AVG to "overnight HRV",
        Metrics.HR_RESTING to "resting heart rate",
        Metrics.HR_SAMPLE to "heart rate",
        Metrics.RESP_RATE_NIGHT to "breathing rate at night",
        Metrics.TEMP_DEVIATION to "skin temperature shift",
        Metrics.SPO2_NIGHT_AVG to "overnight oxygen",
        Metrics.SCORE_READINESS to "readiness",
        Metrics.SCORE_SLEEP to "sleep score",
        Metrics.SCORE_ACTIVITY to "activity score",
        Metrics.STRESS_DAY_HIGH_MIN to "high stress time",
        Metrics.ACTIVITY_STEPS to "steps",
        Metrics.ACTIVITY_ACTIVE_KCAL to "active calories",
        Metrics.WORKOUT_SESSION to "workout time",
        Metrics.GLUCOSE_SAMPLE to "glucose",
        Metrics.GLUCOSE_FASTING to "fasting glucose",
        Metrics.GLUCOSE_OVERNIGHT_MEAN to "overnight glucose",
        Metrics.WEIGHT to "weight",
        Metrics.BODY_FAT_PCT to "body fat",
        Metrics.BP_SYSTOLIC to "blood pressure",
        Metrics.BP_DIASTOLIC to "lower blood pressure number",
        Metrics.BP_PULSE to "pulse at the cuff",
        Metrics.SCREEN_TOTAL_MIN to "phone time",
        Metrics.SCREEN_AFTER_21_MIN to "phone time after 9 pm",
        Metrics.SCREEN_LAST_USE_UTC to "late phone use",
        Metrics.VOICE_ENERGY_INDEX to "voice energy",
        Metrics.VOICE_FLATNESS_INDEX to "voice flatness",
        Metrics.VOICE_TENSION_INDEX to "voice tension",
        Metrics.WORDS_VALENCE to "spoken mood",
        Metrics.WORDS_OPTIMISM to "spoken optimism",
        Metrics.WORDS_ANXIETY to "worry in your words",
        Metrics.WORDS_SELF_FOCUS to "self focus in your words",
        Metrics.WORDS_ENERGY_SELF_REPORT to "energy you report",
        Metrics.FEEL_ENERGY to "felt energy",
        Metrics.FEEL_EASE to "felt ease",
        Metrics.FEEL_TEMPO to "felt pace",
        Metrics.FEEL_TINT_HUE to "feeling color",
        Metrics.FEEL_TINT_LIGHT to "feeling lightness",
        Metrics.FORECAST_SCORE to "day forecast",
        Metrics.WEATHER_PRESSURE_CHANGE_24H to "pressure change",
        Metrics.WEATHER_PRESSURE_MEAN to "air pressure",
        Metrics.WEATHER_PRESSURE_SWING to "pressure swings",
        Metrics.WEATHER_DAYLIGHT_MIN to "daylight",
        Metrics.WEATHER_TEMP_MAX to "daytime heat",
        Metrics.WEATHER_TEMP_MIN to "overnight lows",
        Metrics.WEATHER_HUMIDITY_MEAN to "humidity",
        Metrics.WEATHER_UV_MAX to "UV",
        Metrics.AIR_PM25 to "air pollution",
        Metrics.AIR_AQI_MAX to "poor air",
        Metrics.MOON_ILLUMINATION to "moonlight",
        Metrics.SPACE_KP_MAX to "geomagnetic activity",
        Metrics.HABIT_MINUTES to "habit minutes",
    )

    /** Event kinds have no dot ("alcohol", "stress_event"), or are symptoms ("symptom.headache"). */
    fun isEventKind(key: String): Boolean {
        val k = key.removePrefix(EVENT_PREFIX)
        if (k.startsWith(HABIT_PREFIX)) return false
        return k.startsWith("symptom.") || ('.' !in k && k != Metrics.WEIGHT)
    }

    fun label(key: String): String {
        if (key.startsWith(HABIT_PREFIX)) return key.removePrefix(HABIT_PREFIX).trim()
        val k = key.removePrefix(EVENT_PREFIX)
        names[k]?.let { return it }
        if (isEventKind(k)) return Fmt.kindName(k)
        val family = k.substringBefore('.')
        val rest = k.substringAfter('.', "").replace('_', ' ').trim()
        if (rest.isEmpty()) return Fmt.metricName(k)
        return when (family) {
            "voice" -> "voice " + rest.removeSuffix(" index")
            "words" -> rest.removeSuffix(" markers") + " in your words"
            "feel" -> "felt $rest"
            "glucose" -> "$rest glucose".replace(" mean glucose", " glucose")
            "sleep" -> rest.removeSuffix(" min").let { if (it.endsWith("sleep")) it else "$it sleep" }
            "screen" -> "phone " + rest.removeSuffix(" min")
            "weather" -> rest.removeSuffix(" min")
            else -> Fmt.metricName(k)
        }
    }

    /**
     * A short goal name from a finding's input: the habit's own name, "Fewer alcohol days" for an
     * event kind, or "More daylight" and "Less phone time" for measurements by the good direction.
     */
    fun goalSuggestion(insight: Insight): String {
        val key = insight.input
        if (key.startsWith(HABIT_PREFIX)) return key.removePrefix(HABIT_PREFIX).trim()
        val label = label(key)
        if (isEventKind(key)) return if (insight.good == true) "More $label days" else "Fewer $label days"
        return when (insight.good) {
            false -> if (key == Metrics.ACTIVITY_STEPS) "Fewer $label" else "Less $label"
            else -> "More $label"
        }
    }
}
