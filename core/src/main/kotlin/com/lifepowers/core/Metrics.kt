package com.lifepowers.core

/** How a sample is assigned to a local day. */
enum class DayRule { CALENDAR, NIGHT_ENDING }

/** Aggregations a daily rollup can produce. */
enum class Agg { MEAN, MIN, MAX, SUM, LAST, COUNT, CV, TIR_70_140 }

data class MetricSpec(
    val key: String,
    val unit: String,
    val dayRule: DayRule,
    val aggs: List<Agg>,
    /** Higher values are better for the person. Null when neither direction is good. */
    val higherIsBetter: Boolean? = null,
)

/** Canonical metric keys. Mirrors docs/DATA_MODEL.md. */
object Metrics {
    const val SLEEP_TOTAL_MIN = "sleep.total_min"
    const val SLEEP_DEEP_MIN = "sleep.deep_min"
    const val SLEEP_REM_MIN = "sleep.rem_min"
    const val SLEEP_LIGHT_MIN = "sleep.light_min"
    const val SLEEP_AWAKE_MIN = "sleep.awake_min"
    const val SLEEP_ONSET_LATENCY_MIN = "sleep.onset_latency_min"
    const val SLEEP_BEDTIME_UTC = "sleep.bedtime_utc"
    const val SLEEP_WAKE_UTC = "sleep.wake_utc"
    const val SLEEP_EFFICIENCY = "sleep.efficiency"
    const val HRV_NIGHT_AVG = "hrv.night_avg"
    const val HR_RESTING = "hr.resting"
    const val HR_SAMPLE = "hr.sample"
    const val RESP_RATE_NIGHT = "resp.rate_night"
    const val TEMP_DEVIATION = "temp.deviation"
    const val SPO2_NIGHT_AVG = "spo2.night_avg"
    const val SCORE_READINESS = "score.readiness"
    const val SCORE_SLEEP = "score.sleep"
    const val SCORE_ACTIVITY = "score.activity"
    const val STRESS_DAY_HIGH_MIN = "stress.day_high_min"
    const val ACTIVITY_STEPS = "activity.steps"
    const val ACTIVITY_ACTIVE_KCAL = "activity.active_kcal"
    const val WORKOUT_SESSION = "workout.session"
    const val GLUCOSE_SAMPLE = "glucose.sample"
    const val GLUCOSE_FASTING = "glucose.fasting"
    const val GLUCOSE_OVERNIGHT_MEAN = "glucose.overnight_mean"
    const val WEIGHT = "weight"
    const val BODY_FAT_PCT = "body.fat_pct"
    const val BP_SYSTOLIC = "bp.systolic"
    const val BP_DIASTOLIC = "bp.diastolic"
    const val BP_PULSE = "bp.pulse"
    const val HORMONE_LH = "hormone.lh"
    const val HORMONE_E3G = "hormone.e3g"
    const val HORMONE_PDG = "hormone.pdg"
    const val HORMONE_FSH = "hormone.fsh"
    const val SCREEN_TOTAL_MIN = "screen.total_min"
    const val SCREEN_AFTER_21_MIN = "screen.after_21_min"
    const val SCREEN_LAST_USE_UTC = "screen.last_use_utc"
    const val VOICE_ENERGY_INDEX = "voice.energy_index"
    const val VOICE_FLATNESS_INDEX = "voice.flatness_index"
    const val VOICE_TENSION_INDEX = "voice.tension_index"
    const val WORDS_VALENCE = "words.valence"
    const val WORDS_OPTIMISM = "words.optimism"
    const val WORDS_ANXIETY = "words.anxiety_markers"
    const val WORDS_SELF_FOCUS = "words.self_focus"
    const val WORDS_ENERGY_SELF_REPORT = "words.energy_self_report"
    const val FEEL_ENERGY = "feel.energy"
    const val FEEL_EASE = "feel.ease"
    const val FEEL_TEMPO = "feel.tempo"
    const val FEEL_TINT_HUE = "feel.tint_hue"
    const val FEEL_TINT_LIGHT = "feel.tint_light"
    const val FORECAST_SCORE = "forecast.score"
    const val WEATHER_PRESSURE_CHANGE_24H = "weather.pressure_change_24h"
    const val WEATHER_DAYLIGHT_MIN = "weather.daylight_min"
    const val MOON_ILLUMINATION = "moon.illumination"
    const val WEATHER_PRESSURE_MEAN = "weather.pressure_mean"
    const val WEATHER_PRESSURE_SWING = "weather.pressure_swing"
    const val WEATHER_TEMP_MAX = "weather.temp_max"
    const val WEATHER_TEMP_MIN = "weather.temp_min"
    const val WEATHER_HUMIDITY_MEAN = "weather.humidity_mean"
    const val WEATHER_UV_MAX = "weather.uv_max"
    const val AIR_PM25 = "air.pm25"
    const val AIR_AQI_MAX = "air.aqi_max"
    const val SPACE_KP_MAX = "space.kp_max"
    const val HABIT_MINUTES = "habit.minutes"
    const val BREATH_MINUTES = "breath.minutes"
    const val ASSIST_ENERGY = "assist.energy"
    const val ASSIST_OPTIMISM = "assist.optimism"
    const val ASSIST_TENSION = "assist.tension"
    const val ASSIST_WARMTH = "assist.warmth"

    val catalog: Map<String, MetricSpec> = listOf(
        MetricSpec(SLEEP_TOTAL_MIN, "min", DayRule.NIGHT_ENDING, listOf(Agg.LAST), true),
        MetricSpec(SLEEP_DEEP_MIN, "min", DayRule.NIGHT_ENDING, listOf(Agg.LAST), true),
        MetricSpec(SLEEP_REM_MIN, "min", DayRule.NIGHT_ENDING, listOf(Agg.LAST), true),
        MetricSpec(SLEEP_LIGHT_MIN, "min", DayRule.NIGHT_ENDING, listOf(Agg.LAST)),
        MetricSpec(SLEEP_AWAKE_MIN, "min", DayRule.NIGHT_ENDING, listOf(Agg.LAST), false),
        MetricSpec(SLEEP_ONSET_LATENCY_MIN, "min", DayRule.NIGHT_ENDING, listOf(Agg.LAST), false),
        MetricSpec(SLEEP_BEDTIME_UTC, "epoch_ms", DayRule.NIGHT_ENDING, listOf(Agg.LAST)),
        MetricSpec(SLEEP_WAKE_UTC, "epoch_ms", DayRule.NIGHT_ENDING, listOf(Agg.LAST)),
        MetricSpec(SLEEP_EFFICIENCY, "ratio", DayRule.NIGHT_ENDING, listOf(Agg.LAST), true),
        MetricSpec(HRV_NIGHT_AVG, "ms", DayRule.NIGHT_ENDING, listOf(Agg.LAST), true),
        MetricSpec(HR_RESTING, "bpm", DayRule.NIGHT_ENDING, listOf(Agg.LAST), false),
        MetricSpec(HR_SAMPLE, "bpm", DayRule.CALENDAR, listOf(Agg.MEAN, Agg.MIN, Agg.MAX)),
        MetricSpec(RESP_RATE_NIGHT, "brpm", DayRule.NIGHT_ENDING, listOf(Agg.LAST)),
        MetricSpec(TEMP_DEVIATION, "degC", DayRule.NIGHT_ENDING, listOf(Agg.LAST)),
        MetricSpec(SPO2_NIGHT_AVG, "pct", DayRule.NIGHT_ENDING, listOf(Agg.LAST), true),
        MetricSpec(SCORE_READINESS, "score", DayRule.CALENDAR, listOf(Agg.LAST), true),
        MetricSpec(SCORE_SLEEP, "score", DayRule.CALENDAR, listOf(Agg.LAST), true),
        MetricSpec(SCORE_ACTIVITY, "score", DayRule.CALENDAR, listOf(Agg.LAST), true),
        MetricSpec(STRESS_DAY_HIGH_MIN, "min", DayRule.CALENDAR, listOf(Agg.LAST), false),
        MetricSpec(ACTIVITY_STEPS, "count", DayRule.CALENDAR, listOf(Agg.SUM), true),
        MetricSpec(ACTIVITY_ACTIVE_KCAL, "kcal", DayRule.CALENDAR, listOf(Agg.SUM)),
        MetricSpec(WORKOUT_SESSION, "min", DayRule.CALENDAR, listOf(Agg.SUM, Agg.COUNT)),
        MetricSpec(GLUCOSE_SAMPLE, "mg/dL", DayRule.CALENDAR, listOf(Agg.MEAN, Agg.MIN, Agg.MAX, Agg.CV, Agg.TIR_70_140)),
        MetricSpec(GLUCOSE_FASTING, "mg/dL", DayRule.CALENDAR, listOf(Agg.LAST), false),
        MetricSpec(GLUCOSE_OVERNIGHT_MEAN, "mg/dL", DayRule.NIGHT_ENDING, listOf(Agg.LAST), false),
        MetricSpec(WEIGHT, "kg", DayRule.CALENDAR, listOf(Agg.MEAN)),
        MetricSpec(BODY_FAT_PCT, "pct", DayRule.CALENDAR, listOf(Agg.MEAN)),
        MetricSpec(BP_SYSTOLIC, "mmHg", DayRule.CALENDAR, listOf(Agg.MEAN, Agg.COUNT), false),
        MetricSpec(BP_DIASTOLIC, "mmHg", DayRule.CALENDAR, listOf(Agg.MEAN, Agg.COUNT), false),
        MetricSpec(BP_PULSE, "bpm", DayRule.CALENDAR, listOf(Agg.MEAN)),
        MetricSpec(HORMONE_LH, "mIU/mL", DayRule.CALENDAR, listOf(Agg.LAST)),
        MetricSpec(HORMONE_E3G, "ng/mL", DayRule.CALENDAR, listOf(Agg.LAST)),
        MetricSpec(HORMONE_PDG, "ug/mL", DayRule.CALENDAR, listOf(Agg.LAST)),
        MetricSpec(HORMONE_FSH, "mIU/mL", DayRule.CALENDAR, listOf(Agg.LAST)),
        MetricSpec(SCREEN_TOTAL_MIN, "min", DayRule.CALENDAR, listOf(Agg.SUM), false),
        MetricSpec(SCREEN_AFTER_21_MIN, "min", DayRule.CALENDAR, listOf(Agg.SUM), false),
        MetricSpec(SCREEN_LAST_USE_UTC, "epoch_ms", DayRule.NIGHT_ENDING, listOf(Agg.LAST)),
        MetricSpec(VOICE_ENERGY_INDEX, "z", DayRule.CALENDAR, listOf(Agg.LAST), true),
        MetricSpec(VOICE_FLATNESS_INDEX, "z", DayRule.CALENDAR, listOf(Agg.LAST), false),
        MetricSpec(VOICE_TENSION_INDEX, "z", DayRule.CALENDAR, listOf(Agg.LAST), false),
        MetricSpec(WORDS_VALENCE, "0..1", DayRule.CALENDAR, listOf(Agg.LAST), true),
        MetricSpec(WORDS_OPTIMISM, "0..1", DayRule.CALENDAR, listOf(Agg.LAST), true),
        MetricSpec(WORDS_ANXIETY, "0..1", DayRule.CALENDAR, listOf(Agg.LAST), false),
        MetricSpec(WORDS_SELF_FOCUS, "0..1", DayRule.CALENDAR, listOf(Agg.LAST), false),
        MetricSpec(WORDS_ENERGY_SELF_REPORT, "0..10", DayRule.CALENDAR, listOf(Agg.LAST), true),
        MetricSpec(FEEL_ENERGY, "-1..1", DayRule.CALENDAR, listOf(Agg.MEAN), true),
        MetricSpec(FEEL_EASE, "-1..1", DayRule.CALENDAR, listOf(Agg.MEAN), true),
        MetricSpec(FEEL_TEMPO, "1..4", DayRule.CALENDAR, listOf(Agg.MEAN)),
        MetricSpec(FEEL_TINT_HUE, "deg", DayRule.CALENDAR, listOf(Agg.LAST)),
        MetricSpec(FEEL_TINT_LIGHT, "0..1", DayRule.CALENDAR, listOf(Agg.LAST)),
        MetricSpec(FORECAST_SCORE, "z", DayRule.CALENDAR, listOf(Agg.LAST), true),
        MetricSpec(WEATHER_PRESSURE_CHANGE_24H, "hPa", DayRule.CALENDAR, listOf(Agg.LAST)),
        MetricSpec(WEATHER_DAYLIGHT_MIN, "min", DayRule.CALENDAR, listOf(Agg.LAST), true),
        MetricSpec(MOON_ILLUMINATION, "0..1", DayRule.CALENDAR, listOf(Agg.LAST)),
        MetricSpec(WEATHER_PRESSURE_MEAN, "hPa", DayRule.CALENDAR, listOf(Agg.LAST)),
        MetricSpec(WEATHER_PRESSURE_SWING, "hPa", DayRule.CALENDAR, listOf(Agg.LAST), false),
        MetricSpec(WEATHER_TEMP_MAX, "C", DayRule.CALENDAR, listOf(Agg.LAST)),
        MetricSpec(WEATHER_TEMP_MIN, "C", DayRule.CALENDAR, listOf(Agg.LAST)),
        MetricSpec(WEATHER_HUMIDITY_MEAN, "%", DayRule.CALENDAR, listOf(Agg.LAST)),
        MetricSpec(WEATHER_UV_MAX, "index", DayRule.CALENDAR, listOf(Agg.LAST)),
        MetricSpec(AIR_PM25, "ug/m3", DayRule.CALENDAR, listOf(Agg.LAST), false),
        MetricSpec(AIR_AQI_MAX, "index", DayRule.CALENDAR, listOf(Agg.LAST), false),
        MetricSpec(SPACE_KP_MAX, "Kp", DayRule.CALENDAR, listOf(Agg.LAST)),
        MetricSpec(HABIT_MINUTES, "min", DayRule.CALENDAR, listOf(Agg.SUM), true),
        MetricSpec(BREATH_MINUTES, "min", DayRule.CALENDAR, listOf(Agg.SUM), true),
        MetricSpec(ASSIST_ENERGY, "0..10", DayRule.CALENDAR, listOf(Agg.LAST), true),
        MetricSpec(ASSIST_OPTIMISM, "0..10", DayRule.CALENDAR, listOf(Agg.LAST), true),
        MetricSpec(ASSIST_TENSION, "0..10", DayRule.CALENDAR, listOf(Agg.LAST), false),
        MetricSpec(ASSIST_WARMTH, "0..10", DayRule.CALENDAR, listOf(Agg.LAST), true),
    ).associateBy { it.key }

    fun spec(key: String): MetricSpec = catalog[key] ?: MetricSpec(key, "", DayRule.CALENDAR, listOf(Agg.LAST))
}
