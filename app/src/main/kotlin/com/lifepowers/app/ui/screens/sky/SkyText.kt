package com.lifepowers.app.ui.screens.sky

import com.lifepowers.app.domain.LocationMode
import com.lifepowers.app.domain.LocationSetting
import com.lifepowers.app.domain.WorldEvent
import com.lifepowers.core.AuroraChance
import com.lifepowers.core.DailyWeather
import com.lifepowers.core.EclipseEvent
import com.lifepowers.core.MoonInfo
import com.lifepowers.core.Pairing
import com.lifepowers.core.PlanetSighting
import com.lifepowers.core.ShowerNight
import com.lifepowers.core.Sky
import com.lifepowers.core.SkyWindow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Calm sentences for the sky, the weather and the ground. Shared by the Sky and Today screens. */
object SkyText {
    private val monthDayFormat = DateTimeFormatter.ofPattern("MMMM d", Locale.getDefault())
    private val shortMonthDay = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    private val weekday = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())
    private val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    private fun zone(): ZoneId = ZoneId.systemDefault()
    fun localDate(utcMs: Long): LocalDate = Instant.ofEpochMilli(utcMs).atZone(zone()).toLocalDate()
    fun time(utcMs: Long): String = Instant.ofEpochMilli(utcMs).atZone(zone()).format(clock).lowercase(Locale.getDefault())
    fun monthDay(date: LocalDate): String = date.format(monthDayFormat)

    fun capitalized(s: String): String = s.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

    /** "Portland, about 10 km", the city, or "No place set". */
    fun locationLine(location: LocationSetting): String {
        val place = location.label?.takeIf { it.isNotBlank() }
        return when (location.mode) {
            LocationMode.PRECISE -> place ?: "Your precise location"
            LocationMode.APPROXIMATE -> "${place?.substringBefore(',')?.trim() ?: "Your area"}, about 10 km"
            LocationMode.CITY -> place ?: "A city you chose"
            LocationMode.NONE -> "No place set"
        }
    }

    // Moon

    fun percent(fraction: Double): Int = (fraction * 100).roundToInt().coerceIn(0, 100)

    /** "Waxing gibbous, 78 percent lit". */
    fun moonShort(moon: MoonInfo): String = "${capitalized(moon.name.label)}, ${percent(moon.illumination)} percent lit"

    /** "Full in 4 days", "Full moon tonight", or "Next full moon October 25", with a supermoon note. */
    fun nextFull(moon: MoonInfo, today: LocalDate): String {
        val date = localDate(moon.nextFullUtcMs)
        val days = ChronoUnit.DAYS.between(today, date)
        val base = when {
            days <= 0L -> "Full moon tonight"
            days == 1L -> "Full tomorrow"
            days < 7L -> "Full in $days days"
            else -> "Next full moon ${monthDay(date)}"
        }
        return if (moon.nextFullIsSupermoon) "$base, a supermoon" else base
    }

    // Transits

    private const val DAY_MS = 86_400_000L

    /** "Mercury retrograde through November 9", "Mercury retrograde begins today", "Mercury stationing today". */
    fun retrogradeLine(status: Sky.RetrogradeStatus, nowMs: Long = System.currentTimeMillis()): String {
        val p = status.period
        val end = monthDay(localDate(p.endUtcMs))
        return when {
            status.stationing && abs(nowMs - p.startUtcMs) < 2 * DAY_MS -> "${p.planet} retrograde begins today, through $end"
            status.stationing && abs(p.endUtcMs - nowMs) < 2 * DAY_MS -> "${p.planet} turns direct today"
            status.stationing -> "${p.planet} stationing today"
            else -> "${p.planet} retrograde through $end"
        }
    }

    // Weather

    fun signed(value: Double): String {
        val r = value.roundToInt()
        return if (r > 0) "+$r" else r.toString()
    }

    fun degrees(value: Double?): String = value?.let { "${it.roundToInt()}°" } ?: "--"

    /** "9 h 40" from minutes of daylight. */
    fun daylight(minutes: Double?): String {
        if (minutes == null) return "--"
        val m = minutes.roundToInt().coerceAtLeast(0)
        return "${m / 60} h ${(m % 60).toString().padStart(2, '0')}"
    }

    /** "-9 hPa in 24 hours" or null when there is no reading. */
    fun pressureLine(weather: DailyWeather?): String? =
        weather?.pressureChange24h?.let { "pressure ${signed(it)} hPa in 24 hours" }

    /** A word for the US AQI, calm on purpose. */
    fun airWord(aqi: Double?): String = when {
        aqi == null -> "--"
        aqi <= 50 -> "Clear"
        aqi <= 100 -> "Fair"
        aqi <= 150 -> "Hazy"
        aqi <= 200 -> "Heavy"
        else -> "Very heavy"
    }

    fun airDetail(weather: DailyWeather?): String {
        val pm = weather?.pm25Mean
        val aqi = weather?.aqiMax
        return when {
            pm != null -> "PM2.5 at ${pm.roundToInt()}"
            aqi != null -> "air index ${aqi.roundToInt()}"
            else -> "no reading yet"
        }
    }

    // Worth stepping outside for

    /** "Orionids peak tonight", "Orionids peak Tuesday night", or "Orionids peak, October 21". */
    fun showerTitle(s: ShowerNight, today: LocalDate): String {
        val name = s.shower.name
        val days = ChronoUnit.DAYS.between(today, s.peakDate)
        return when {
            days <= 0L -> "$name peak tonight"
            days == 1L -> "$name peak tomorrow night"
            days < 7L -> "$name peak ${s.peakDate.format(weekday)} night"
            else -> "$name peak, ${monthDay(s.peakDate)}"
        }
    }

    fun showerBody(s: ShowerNight): String {
        val moonSet = s.moonSetUtcMs
        val moon = when {
            s.moonIllumination < 0.25 -> "Little moonlight, so the sky will be dark."
            moonSet != null -> "The moon sets at ${time(moonSet)}, so it is darker after that."
            s.moonIllumination > 0.75 -> "A bright moon will wash out the fainter ones."
            else -> "Some moonlight, so the brightest ones will show best."
        }
        return "$moon Up to about ${s.shower.zhr} an hour from a dark spot."
    }

    fun eclipseTitle(e: EclipseEvent): String =
        "${capitalized(e.kind)} ${if (e.solar) "solar" else "lunar"} eclipse, ${monthDay(localDate(e.peakUtcMs))}"

    fun eclipseBody(e: EclipseEvent): String {
        val start = if (e.startUtcMs > 0L) "Starts ${time(e.startUtcMs)}, " else ""
        val peak = if (start.isEmpty()) "Deepest at ${time(e.peakUtcMs)}." else "deepest at ${time(e.peakUtcMs)}."
        return "Visible from here. $start$peak"
    }

    private fun bodyName(name: String): String = if (name.equals("Moon", ignoreCase = true)) "the moon" else name

    /** "Venus, low in the west after sunset". */
    fun planetTitle(p: PlanetSighting): String {
        val height = when {
            p.altitudeDeg < 20.0 -> "low "
            p.altitudeDeg > 45.0 -> "high "
            else -> ""
        }
        val window = if (p.window == SkyWindow.EVENING) "after sunset" else "before sunrise"
        return "${p.planet}, ${height}in the ${p.direction} $window"
    }

    fun planetBody(p: PlanetSighting): String {
        val bright = if (p.magnitude < 0.0) "Bright enough to find first." else "Visible to the eye."
        return "Around ${time(p.utcMs)}. $bright"
    }

    fun pairingTitle(p: Pairing): String =
        "${capitalized(bodyName(p.a))} and ${bodyName(p.b)} close ${if (p.window == SkyWindow.EVENING) "at dusk" else "at dawn"}"

    fun pairingBody(p: Pairing): String {
        val sep = p.separationDeg
        val apart = if (sep < 2.0) "About a finger apart" else "About ${sep.roundToInt()} degrees apart"
        return "$apart, around ${time(p.utcMs)}."
    }

    fun auroraTitle(chance: AuroraChance): String = "Aurora ${chance.label} tonight"

    fun auroraBody(kpNow: Double?, kpTonightMax: Double?, kpNeeded: Double?, notify: Boolean): String {
        val now = kpNow?.let { "K index ${formatKp(it)} now" }
        val tonight = kpTonightMax?.let { "up to ${formatKp(it)} tonight" }
        val reading = listOfNotNull(now, tonight).joinToString(", ").let { if (it.isEmpty()) "" else capitalized(it) + ". " }
        val need = kpNeeded?.let {
            if (notify) "You will hear when it reaches ${formatKp(it)} at your latitude." else "It needs about ${formatKp(it)} at your latitude."
        } ?: ""
        return (reading + need).trim()
    }

    private fun formatKp(kp: Double): String = if (kp % 1.0 == 0.0) kp.roundToInt().toString() else String.format(Locale.getDefault(), "%.1f", kp)

    // Ground

    fun groundWhen(utcMs: Long, today: LocalDate): String {
        val date = localDate(utcMs)
        val day = when (ChronoUnit.DAYS.between(date, today)) {
            0L -> "Today"
            1L -> "Yesterday"
            else -> date.format(shortMonthDay)
        }
        return "$day ${time(utcMs)}"
    }

    fun groundDetail(e: WorldEvent, today: LocalDate): String {
        val parts = mutableListOf<String>()
        if (e.utcMs > 0L) parts.add(groundWhen(e.utcMs, today))
        val distance = e.distanceKm
        when {
            distance != null -> parts.add("${distance.roundToInt()} km from you")
            e.ringOfFire -> parts.add("on the Ring of Fire")
        }
        if (e.kind == "volcano" && parts.isEmpty()) parts.add("weekly report")
        return parts.joinToString(", ")
    }
}
