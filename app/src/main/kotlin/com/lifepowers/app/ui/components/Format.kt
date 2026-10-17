package com.lifepowers.app.ui.components

import com.lifepowers.app.domain.EventKinds
import com.lifepowers.core.Agg
import com.lifepowers.core.Component
import com.lifepowers.core.Phase
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Small formatting helpers shared by the screens. */
object Fmt {
    private val dayLine = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
    private val monthOnly = DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())
    private val shortDay = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    private val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    /** "Thursday, October 16", for a sentence case line under a title. */
    fun dateLine(date: LocalDate): String = date.format(dayLine)

    fun month(date: LocalDate): String = date.format(monthOnly)
    fun shortDate(date: LocalDate): String = date.format(shortDay)

    fun timeOfDay(time: LocalTime = LocalTime.now()): String = time.format(clock).lowercase(Locale.getDefault())

    fun timeAt(utcMs: Long, zoneOffsetMin: Int): String =
        Instant.ofEpochMilli(utcMs).atOffset(ZoneOffset.ofTotalSeconds(zoneOffsetMin * 60)).toLocalTime().format(clock).lowercase(Locale.getDefault())

    fun localDateOf(utcMs: Long, zoneOffsetMin: Int): LocalDate =
        Instant.ofEpochMilli(utcMs).atOffset(ZoneOffset.ofTotalSeconds(zoneOffsetMin * 60)).toLocalDate()

    fun startOfDayMs(date: LocalDate): Long = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    fun endOfDayMs(date: LocalDate): Long = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

    fun greeting(time: LocalTime = LocalTime.now()): String = when (time.hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }

    /** Whole number or one decimal, "--" when missing. */
    fun num(value: Double?, decimals: Int = 0): String {
        if (value == null || value.isNaN()) return "--"
        return if (decimals <= 0) value.roundToInt().toString() else String.format(Locale.getDefault(), "%.${decimals}f", value)
    }

    fun duration(seconds: Double): String {
        val s = seconds.roundToInt().coerceAtLeast(0)
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    /** A z score against the trailing baseline, read as words. */
    fun zWord(z: Double?): String = when {
        z == null -> "baseline forming"
        z > 1.5 -> "well above usual"
        z > 0.5 -> "above usual"
        z < -1.5 -> "well below usual"
        z < -0.5 -> "below usual"
        else -> "usual"
    }

    /** The same, softened for the voice bars. */
    fun voiceWord(z: Double?): String = when {
        z == null -> "baseline forming"
        z > 1.5 -> "well above usual"
        z > 0.5 -> "a little high"
        z < -1.5 -> "well below usual"
        z < -0.5 -> "a little low"
        else -> "about usual"
    }

    fun aggWord(agg: Agg): String = when (agg) {
        Agg.MEAN -> "mean"
        Agg.MIN -> "low"
        Agg.MAX -> "high"
        Agg.SUM -> "total"
        Agg.LAST -> "last"
        Agg.COUNT -> "count"
        Agg.CV -> "variability"
        Agg.TIR_70_140 -> "in range"
    }

    /** "glucose.overnight_mean" becomes "glucose overnight mean". */
    fun metricName(key: String): String = key.replace('.', ' ').replace('_', ' ')

    /** "symptom.brain_fog" becomes "brain fog", "urge_to_binge" becomes "urge to binge". */
    fun kindName(kind: String): String = kind.removePrefix("symptom.").replace('_', ' ').replace("stress event", "stress")

    fun isSymptom(kind: String): Boolean = kind.startsWith("symptom.")

    fun componentName(c: Component): String = when (c) {
        Component.DAY -> "the day"
        Component.RECOVERY -> "recovery"
        Component.METABOLIC -> "glucose"
        Component.CARDIO -> "blood pressure"
        Component.HORMONAL -> "hormones"
        Component.MIND -> "mind and voice"
        Component.ENVIRONMENT -> "weather"
        Component.SKY -> "sky"
        Component.CRAVINGS -> "cravings"
    }

    /** Reasons arrive as component names or metric keys. Either reads as words. */
    fun reason(raw: String): String {
        val c = Component.values().firstOrNull { it.name == raw }
        return if (c != null) componentName(c) else metricName(raw)
    }

    fun joinSentence(parts: List<String>): String = when (parts.size) {
        0 -> ""
        1 -> parts[0]
        2 -> "${parts[0]} and ${parts[1]}"
        else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
    }

    fun phaseTitle(phase: Phase, cycleDay: Int?): String {
        val base = when (phase) {
            Phase.MENSTRUAL -> "Menstrual"
            Phase.FOLLICULAR -> "Follicular"
            Phase.OVULATORY -> "Ovulatory"
            Phase.LUTEAL -> "Luteal"
            Phase.UNKNOWN -> "Phase unknown"
            Phase.ACTIVE_1 -> "Active week 1"
            Phase.ACTIVE_2 -> "Active week 2"
            Phase.ACTIVE_3 -> "Active week 3"
            Phase.HORMONE_FREE -> "Hormone free week"
            Phase.DOSE_D0_2 -> "Just after a dose"
            Phase.DOSE_D3_6 -> "Mid dose"
            Phase.DOSE_D7_PLUS -> "Late in the dose"
            Phase.GESTATIONAL_WEEK -> return if (cycleDay != null) "Week $cycleDay" else "Pregnancy"
        }
        return if (cycleDay != null && phase != Phase.UNKNOWN) "$base, day $cycleDay" else base
    }

    fun absFraction(z: Double, span: Double = 3.0): Float = (abs(z) / span).coerceIn(0.0, 1.0).toFloat()

    val behaviorKinds: List<Pair<String, String>> = listOf(
        EventKinds.ALCOHOL to "Alcohol",
        EventKinds.CAFFEINE to "Caffeine",
        EventKinds.MEAL to "Meal",
        EventKinds.SUPPLEMENT to "Supplement",
        EventKinds.STRESS_EVENT to "Stress",
    )
}
