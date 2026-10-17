package com.lifepowers.app.world

import com.lifepowers.app.domain.DailyMetricPoint
import com.lifepowers.core.AuroraChance
import com.lifepowers.core.EclipseEvent
import com.lifepowers.core.KpReading
import com.lifepowers.core.Pairing
import com.lifepowers.core.ShowerNight
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

/** Pure helpers for the earth and sky layer. No Android, no I/O. */
object WorldMath {
    const val HOUR = 3_600_000L
    const val DAY = 86_400_000L
    /** A K index reading covers the three hours after its time tag. */
    const val KP_SPAN = 3 * HOUR
    /** Pairings this close count as worth stepping outside for. */
    const val CLOSE_PAIRING_DEG = 3.0

    /** Metric prefixes the world layer writes itself; ignored when looking for the start of a person's own data. */
    val WORLD_PREFIXES = listOf("weather.", "air.", "moon.", "space.", "forecast.")

    fun offsetMin(date: LocalDate, zone: ZoneId): Int = zone.rules.getOffset(date.atTime(LocalTime.NOON)).totalSeconds / 60

    fun atLocal(date: LocalDate, hour: Int, zone: ZoneId): Long = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    fun localNoonUtc(date: LocalDate, zone: ZoneId): Long = atLocal(date, 12, zone)

    /** 18:00 local on [date], the start of that night. */
    fun eveningUtc(date: LocalDate, zone: ZoneId): Long = atLocal(date, 18, zone)

    /** The night that belongs to [date]: 18:00 local to 06:00 local the next morning. */
    fun nightWindow(date: LocalDate, zone: ZoneId): Pair<Long, Long> = eveningUtc(date, zone) to atLocal(date.plusDays(1), 6, zone)

    /** The evening a moment belongs to: before 06:00 it is still last night. */
    fun eveningDateFor(now: ZonedDateTime): LocalDate = if (now.hour < 6) now.toLocalDate().minusDays(1) else now.toLocalDate()

    /** The highest Kp, observed or predicted, whose three hour span touches the night of [date]. */
    fun nightMaxKp(readings: List<KpReading>, date: LocalDate, zone: ZoneId): Double? {
        val (start, end) = nightWindow(date, zone)
        return readings.filter { it.utcMs > start - KP_SPAN && it.utcMs < end }.maxOfOrNull { it.kp }
    }

    /** The latest observed (or estimated) reading at or before [nowMs]. */
    fun kpNow(readings: List<KpReading>, nowMs: Long): Double? =
        readings.filter { !it.predicted && it.utcMs <= nowMs }.maxByOrNull { it.utcMs }?.kp

    /**
     * Merges K index lists by time tag. Later lists win, except that a prediction never replaces an
     * observation. Readings before [keepAfterMs] are dropped.
     */
    fun mergeKp(lists: List<List<KpReading>>, keepAfterMs: Long): List<KpReading> {
        val byTime = HashMap<Long, KpReading>()
        for (list in lists) for (r in list) {
            val prev = byTime[r.utcMs]
            if (prev == null || !r.predicted || prev.predicted) byTime[r.utcMs] = r
        }
        return byTime.values.filter { it.utcMs >= keepAfterMs }.sortedBy { it.utcMs }
    }

    /** Daily maximum of observed Kp for completed local days with at least [minReadings] readings. */
    fun dailyKpMax(readings: List<KpReading>, zone: ZoneId, today: LocalDate, minReadings: Int = 6): Map<LocalDate, Double> =
        readings.filter { !it.predicted }
            .groupBy { Instant.ofEpochMilli(it.utcMs).atZone(zone).toLocalDate() }
            .filter { (d, rows) -> d.isBefore(today) && rows.size >= minReadings }
            .mapValues { (_, rows) -> rows.maxOf { it.kp } }

    /** Splits [from]..[to] into consecutive inclusive ranges of at most [maxDays] days. */
    fun chunks(from: LocalDate, to: LocalDate, maxDays: Int): List<Pair<LocalDate, LocalDate>> {
        require(maxDays > 0)
        val out = ArrayList<Pair<LocalDate, LocalDate>>()
        var start = from
        while (!start.isAfter(to)) {
            val candidate = start.plusDays(maxDays - 1L)
            val end = if (candidate.isAfter(to)) to else candidate
            out += start to end
            start = end.plusDays(1)
        }
        return out
    }

    /** The earliest date with a daily metric that the person's own sources wrote. */
    fun earliestPersonalDate(points: List<DailyMetricPoint>): LocalDate? =
        points.filter { p -> WORLD_PREFIXES.none { p.metric.startsWith(it) } }.minByOrNull { it.localDate.toEpochDay() }?.localDate

    /**
     * The next [hour]:00 local that is more than [marginMin] minutes after [now]. The margin keeps a
     * worker that fires a little early from scheduling the same evening twice.
     */
    fun nextEvening(now: ZonedDateTime, hour: Int, marginMin: Long = 1): ZonedDateTime {
        val h = hour.coerceIn(0, 23)
        val todayAt = now.toLocalDate().atTime(h, 0).atZone(now.zone)
        return if (todayAt.isAfter(now.plusMinutes(marginMin))) todayAt else now.toLocalDate().plusDays(1).atTime(h, 0).atZone(now.zone)
    }

    fun delayToNextEvening(now: ZonedDateTime, hour: Int, marginMin: Long = 1): Duration = Duration.between(now, nextEvening(now, hour, marginMin))

    fun bodyName(name: String): String = if (name == "Moon") "the moon" else name

    /** "Venus and Jupiter", with the moon always second so a sentence can start with the pair. */
    fun pairName(p: Pairing): String =
        if (p.a == "Moon") "${bodyName(p.b)} and ${bodyName(p.a)}" else "${bodyName(p.a)} and ${bodyName(p.b)}"

    /**
     * What makes the night of [date] worth stepping outside for. Empty means nothing does.
     * A shower peaking that night that can be seen from here, with a moon under half lit or setting
     * before 01:00; an eclipse visible here peaking within a day of that evening; aurora possible or
     * likely; or a pairing within three degrees at dusk or dawn.
     */
    fun notableReasons(
        date: LocalDate,
        zone: ZoneId,
        showers: List<ShowerNight>,
        eclipses: List<EclipseEvent>,
        aurora: AuroraChance?,
        pairings: List<Pairing>,
    ): List<String> {
        val out = ArrayList<String>()
        val oneAm = atLocal(date.plusDays(1), 1, zone)
        showers.filter { it.peakDate == date && it.visibleHere != false }
            .filter { it.moonIllumination < 0.5 || (it.moonSetUtcMs ?: Long.MAX_VALUE) <= oneAm }
            .forEach { out += "${it.shower.name} peak tonight" }
        val evening = eveningUtc(date, zone)
        eclipses.filter { it.visibleHere == true && abs(it.peakUtcMs - evening) <= DAY }
            .forEach { out += "${it.kind} ${if (it.solar) "solar" else "lunar"} eclipse" }
        if (aurora != null && aurora != AuroraChance.UNLIKELY) out += "aurora ${aurora.label}"
        pairings.filter { it.separationDeg <= CLOSE_PAIRING_DEG }
            .distinctBy { listOf(it.a, it.b).sorted() }
            .forEach { out += "${pairName(it)} close together" }
        return out
    }
}
