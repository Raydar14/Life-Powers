package com.lifepowers.app.patterns

import com.lifepowers.core.DaySeries
import java.time.LocalDate

/** Pure helpers that turn days and events into series for the core patterns engine. */
object PatternSeries {
    /** A binary series needs at least this many days with the behavior to be tested. */
    const val MIN_ON_DAYS = 5

    /** Every local day from [from] to [to], inclusive. Empty when [to] is before [from]. */
    fun range(from: LocalDate, to: LocalDate): List<LocalDate> {
        if (to.isBefore(from)) return emptyList()
        val out = ArrayList<LocalDate>((to.toEpochDay() - from.toEpochDay() + 1).toInt())
        var d = from
        while (!d.isAfter(to)) { out.add(d); d = d.plusDays(1) }
        return out
    }

    /** The covered range: from the first day any series has a value through [today]. */
    fun coveredRange(series: Collection<Map<LocalDate, Double>>, today: LocalDate): List<LocalDate> {
        val first = series.asSequence().flatMap { it.keys.asSequence() }.filter { !it.isAfter(today) }.minOrNull()
            ?: return emptyList()
        return range(first, today)
    }

    /**
     * 1.0 on days in [range] that are in [onDays], 0.0 on the other days of [range]. Null when fewer than
     * [MIN_ON_DAYS] days are on, so a rare behavior is never tested.
     */
    fun binary(key: String, onDays: Set<LocalDate>, range: List<LocalDate>, higherIsBetter: Boolean? = null): DaySeries? {
        if (range.isEmpty()) return null
        val values = range.associateWith { if (it in onDays) 1.0 else 0.0 }
        if (values.values.count { it > 0.5 } < MIN_ON_DAYS) return null
        return DaySeries(key, values, binary = true, higherIsBetter = higherIsBetter)
    }
}
