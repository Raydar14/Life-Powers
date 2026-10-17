package com.lifepowers.core

import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Direction
import io.github.cosinekitty.astronomy.EclipseKind
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Refraction
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.KM_PER_AU
import io.github.cosinekitty.astronomy.eclipticGeoMoon
import io.github.cosinekitty.astronomy.equator
import io.github.cosinekitty.astronomy.equatorialToEcliptic
import io.github.cosinekitty.astronomy.geoVector
import io.github.cosinekitty.astronomy.horizon
import io.github.cosinekitty.astronomy.illumination
import io.github.cosinekitty.astronomy.moonPhase
import io.github.cosinekitty.astronomy.nextLocalSolarEclipse
import io.github.cosinekitty.astronomy.nextLunarEclipse
import io.github.cosinekitty.astronomy.nextMoonQuarter
import io.github.cosinekitty.astronomy.searchLocalSolarEclipse
import io.github.cosinekitty.astronomy.searchLunarEclipse
import io.github.cosinekitty.astronomy.searchMoonQuarter
import io.github.cosinekitty.astronomy.searchRiseSet
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.abs

/** A place on Earth in degrees. Weather services only ever see it rounded by [rounded]. */
data class GeoPoint(val lat: Double, val lon: Double) {
    /** Rounded to one decimal place, about 10 km, before it leaves the phone. */
    fun rounded(): GeoPoint = GeoPoint(Math.round(lat * 10) / 10.0, Math.round(lon * 10) / 10.0)
}

enum class MoonPhaseName(val label: String) {
    NEW("new moon"), WAXING_CRESCENT("waxing crescent"), FIRST_QUARTER("first quarter"), WAXING_GIBBOUS("waxing gibbous"),
    FULL("full moon"), WANING_GIBBOUS("waning gibbous"), LAST_QUARTER("last quarter"), WANING_CRESCENT("waning crescent"),
}

data class MoonInfo(
    /** 0 new, 90 first quarter, 180 full, 270 last quarter. */
    val phaseAngle: Double,
    /** Fraction of the disc lit, 0 to 1. */
    val illumination: Double,
    val name: MoonPhaseName,
    /** Tropical sign the Moon is in. */
    val sign: String,
    val distanceKm: Double,
    val nextNewUtcMs: Long,
    val nextFullUtcMs: Long,
    val nextFullIsSupermoon: Boolean,
    val riseUtcMs: Long?,
    val setUtcMs: Long?,
)

/** A retrograde period: apparent backward motion in geocentric ecliptic longitude between two stations. */
data class RetrogradePeriod(val planet: String, val startUtcMs: Long, val endUtcMs: Long) {
    fun contains(utcMs: Long) = utcMs in startUtcMs..endUtcMs
}

enum class SkyWindow { EVENING, MORNING }

data class PlanetSighting(val planet: String, val window: SkyWindow, val utcMs: Long, val altitudeDeg: Double, val direction: String, val magnitude: Double)

data class Pairing(val a: String, val b: String, val separationDeg: Double, val window: SkyWindow, val utcMs: Long)

data class EclipseEvent(
    val solar: Boolean,
    val kind: String,
    val peakUtcMs: Long,
    val startUtcMs: Long,
    val endUtcMs: Long,
    /** True when visible from the observer, false when not, null when no location is set. */
    val visibleHere: Boolean?,
    val obscuration: Double,
)

data class MeteorShower(val name: String, val peakMonth: Int, val peakDay: Int, val zhr: Int, val radiantDec: Double)

data class ShowerNight(
    val shower: MeteorShower,
    val peakDate: LocalDate,
    val moonIllumination: Double,
    val moonSetUtcMs: Long?,
    /** False when the radiant never climbs high enough from the observer's latitude. */
    val visibleHere: Boolean?,
)

object Sky {
    const val MS_PER_DAY = 86_400_000L
    val SIGNS = listOf("Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo", "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces")
    val NAKED_EYE_PLANETS = listOf(Body.Mercury, Body.Venus, Body.Mars, Body.Jupiter, Body.Saturn)
    val RETROGRADE_PLANETS = listOf(Body.Mercury, Body.Venus, Body.Mars, Body.Jupiter, Body.Saturn)
    const val SUPERMOON_KM = 360_000.0

    /** Bundled International Meteor Organization peaks for the major showers. Dates move by a day at most. */
    val SHOWERS = listOf(
        MeteorShower("Quadrantids", 1, 3, 110, 49.0),
        MeteorShower("Lyrids", 4, 22, 18, 33.0),
        MeteorShower("Eta Aquariids", 5, 6, 50, -1.0),
        MeteorShower("Southern Delta Aquariids", 7, 30, 25, -16.0),
        MeteorShower("Perseids", 8, 12, 100, 58.0),
        MeteorShower("Draconids", 10, 8, 10, 54.0),
        MeteorShower("Orionids", 10, 21, 20, 16.0),
        MeteorShower("Leonids", 11, 17, 15, 22.0),
        MeteorShower("Geminids", 12, 14, 150, 33.0),
        MeteorShower("Ursids", 12, 22, 10, 75.0),
    )

    fun time(utcMs: Long): Time = Time.fromMillisecondsSince1970(utcMs)
    private fun observer(p: GeoPoint) = Observer(p.lat, p.lon, 0.0)
    fun sign(eclipticLongitude: Double): String = SIGNS[(((eclipticLongitude % 360) + 360) % 360 / 30).toInt().coerceIn(0, 11)]

    fun phaseName(angle: Double): MoonPhaseName {
        val a = ((angle % 360) + 360) % 360
        return MoonPhaseName.values()[(((a + 22.5) % 360) / 45).toInt().coerceIn(0, 7)]
    }

    fun moonDistanceKm(utcMs: Long): Double = geoVector(Body.Moon, time(utcMs), Aberration.None).length() * KM_PER_AU

    fun moon(utcMs: Long, at: GeoPoint? = null): MoonInfo {
        val t = time(utcMs)
        val angle = moonPhase(t)
        var q = searchMoonQuarter(t)
        var nextNew: Long? = null
        var nextFull: Long? = null
        while (nextNew == null || nextFull == null) {
            if (q.quarter == 0 && nextNew == null) nextNew = q.time.toMillisecondsSince1970()
            if (q.quarter == 2 && nextFull == null) nextFull = q.time.toMillisecondsSince1970()
            q = nextMoonQuarter(q)
        }
        val obs = at?.let { observer(it) }
        val start = t.addDays(-0.5)
        val rise = obs?.let { searchRiseSet(Body.Moon, it, Direction.Rise, start, 1.5)?.toMillisecondsSince1970() }
        val set = obs?.let { searchRiseSet(Body.Moon, it, Direction.Set, start, 1.5)?.toMillisecondsSince1970() }
        return MoonInfo(
            phaseAngle = angle,
            illumination = illumination(Body.Moon, t).phaseFraction,
            name = phaseName(angle),
            sign = sign(eclipticGeoMoon(t).lon),
            distanceKm = moonDistanceKm(utcMs),
            nextNewUtcMs = nextNew,
            nextFullUtcMs = nextFull,
            nextFullIsSupermoon = moonDistanceKm(nextFull) < SUPERMOON_KM,
            riseUtcMs = rise,
            setUtcMs = set,
        )
    }

    /** Geocentric apparent ecliptic longitude of a planet, true ecliptic of date. */
    fun geocentricLongitude(body: Body, utcMs: Long): Double =
        equatorialToEcliptic(geoVector(body, time(utcMs), Aberration.Corrected)).elon

    private fun dailyMotion(body: Body, utcMs: Long): Double {
        val a = geocentricLongitude(body, utcMs - MS_PER_DAY / 4)
        val b = geocentricLongitude(body, utcMs + MS_PER_DAY / 4)
        var d = b - a
        if (d > 180) d -= 360
        if (d < -180) d += 360
        return d * 2
    }

    /** The moment motion changes sign between [lo] and [hi], refined to about ten minutes. */
    private fun station(body: Body, lo0: Long, hi0: Long): Long {
        var lo = lo0; var hi = hi0
        val loSign = dailyMotion(body, lo) < 0
        while (hi - lo > 10 * 60_000L) {
            val mid = (lo + hi) / 2
            if ((dailyMotion(body, mid) < 0) == loSign) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }

    /** Retrograde periods that overlap [fromUtcMs, toUtcMs]. */
    fun retrogrades(body: Body, fromUtcMs: Long, toUtcMs: Long): List<RetrogradePeriod> {
        val scanFrom = fromUtcMs - 200 * MS_PER_DAY
        val scanTo = toUtcMs + 200 * MS_PER_DAY
        val out = mutableListOf<RetrogradePeriod>()
        var prevT = scanFrom
        var prevRetro = dailyMotion(body, prevT) < 0
        var start: Long? = null
        var t = scanFrom + MS_PER_DAY
        while (t <= scanTo) {
            val retro = dailyMotion(body, t) < 0
            if (retro != prevRetro) {
                val s = station(body, prevT, t)
                if (retro) start = s
                else if (start != null) {
                    if (s >= fromUtcMs && start <= toUtcMs) out.add(RetrogradePeriod(body.name, start, s))
                    start = null
                }
            }
            prevRetro = retro; prevT = t; t += MS_PER_DAY
        }
        return out
    }

    data class RetrogradeStatus(val period: RetrogradePeriod, val stationing: Boolean)

    /** Planets retrograde at [utcMs]. Stationing is within a day of either station. */
    fun retrogradeNow(utcMs: Long, planets: List<Body> = RETROGRADE_PLANETS): List<RetrogradeStatus> =
        planets.flatMap { retrogrades(it, utcMs - MS_PER_DAY, utcMs + MS_PER_DAY) }
            .filter { it.startUtcMs - MS_PER_DAY <= utcMs && utcMs <= it.endUtcMs + MS_PER_DAY }
            .map { RetrogradeStatus(it, abs(utcMs - it.startUtcMs) <= MS_PER_DAY || abs(utcMs - it.endUtcMs) <= MS_PER_DAY) }

    private fun compass(azimuth: Double): String {
        val names = listOf("north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest")
        return names[(((azimuth % 360) + 360 + 22.5) % 360 / 45).toInt().coerceIn(0, 7)]
    }

    private fun localMidnightUtc(date: LocalDate, zoneOffsetMin: Int): Long =
        date.atStartOfDay().toInstant(ZoneOffset.ofTotalSeconds(zoneOffsetMin * 60)).toEpochMilli()

    /** Times 45 minutes after sunset and 45 minutes before sunrise on [date], when the sky is dark enough. */
    fun viewingTimes(date: LocalDate, zoneOffsetMin: Int, at: GeoPoint): Map<SkyWindow, Long> {
        val obs = observer(at)
        val dayStart = time(localMidnightUtc(date, zoneOffsetMin))
        val out = mutableMapOf<SkyWindow, Long>()
        searchRiseSet(Body.Sun, obs, Direction.Set, dayStart, 1.0)?.let { out[SkyWindow.EVENING] = it.toMillisecondsSince1970() + 45 * 60_000L }
        searchRiseSet(Body.Sun, obs, Direction.Rise, dayStart, 1.0)?.let { out[SkyWindow.MORNING] = it.toMillisecondsSince1970() - 45 * 60_000L }
        return out
    }

    private data class Seen(val name: String, val alt: Double, val az: Double, val vec: io.github.cosinekitty.astronomy.Vector)

    private fun look(body: Body, utcMs: Long, obs: Observer): Seen {
        val t = time(utcMs)
        val eq = equator(body, t, obs, EquatorEpoch.OfDate, Aberration.Corrected)
        val hor = horizon(t, obs, eq.ra, eq.dec, Refraction.Normal)
        return Seen(body.name, hor.altitude, hor.azimuth, eq.vec)
    }

    /** Naked eye planets above 8 degrees at dusk or dawn on [date]. */
    fun visiblePlanets(date: LocalDate, zoneOffsetMin: Int, at: GeoPoint): List<PlanetSighting> {
        val obs = observer(at)
        return viewingTimes(date, zoneOffsetMin, at).flatMap { (window, utcMs) ->
            NAKED_EYE_PLANETS.mapNotNull { body ->
                val s = look(body, utcMs, obs)
                val mag = illumination(body, time(utcMs)).mag
                if (s.alt >= 8.0 && mag <= 2.5) PlanetSighting(body.name, window, utcMs, s.alt, compass(s.az), mag) else null
            }
        }
    }

    /** Close pairings, within [maxDeg], among visible planets and the Moon at dusk or dawn. */
    fun pairings(date: LocalDate, zoneOffsetMin: Int, at: GeoPoint, maxDeg: Double = 4.0): List<Pairing> {
        val obs = observer(at)
        val out = mutableListOf<Pairing>()
        for ((window, utcMs) in viewingTimes(date, zoneOffsetMin, at)) {
            val up = (NAKED_EYE_PLANETS + Body.Moon).map { look(it, utcMs, obs) }.filter { it.alt >= 5.0 }
            for (i in up.indices) for (j in i + 1 until up.size) {
                val sep = up[i].vec.angleWith(up[j].vec)
                if (sep <= maxDeg) out.add(Pairing(up[i].name, up[j].name, sep, window, utcMs))
            }
        }
        return out
    }

    private fun kindLabel(k: EclipseKind) = k.name.lowercase()

    /** Lunar eclipses and, with a location, solar eclipses visible here, within [days] of [fromUtcMs]. */
    fun eclipses(fromUtcMs: Long, days: Int, at: GeoPoint?): List<EclipseEvent> {
        val until = fromUtcMs + days * MS_PER_DAY
        val out = mutableListOf<EclipseEvent>()
        var lunar = searchLunarEclipse(time(fromUtcMs))
        while (lunar.peak.toMillisecondsSince1970() <= until) {
            val peak = lunar.peak.toMillisecondsSince1970()
            val semi = (if (lunar.sdPartial > 0) lunar.sdPartial else lunar.sdPenum) * 60_000.0
            val visible = at?.let { look(Body.Moon, peak, observer(it)).alt > 0.0 }
            out.add(EclipseEvent(false, kindLabel(lunar.kind), peak, (peak - semi).toLong(), (peak + semi).toLong(), visible, lunar.obscuration))
            lunar = nextLunarEclipse(lunar.peak)
        }
        if (at != null) {
            val obs = observer(at)
            var solar = searchLocalSolarEclipse(time(fromUtcMs), obs)
            while (solar.peak.time.toMillisecondsSince1970() <= until) {
                out.add(
                    EclipseEvent(
                        true, kindLabel(solar.kind), solar.peak.time.toMillisecondsSince1970(),
                        solar.partialBegin.time.toMillisecondsSince1970(), solar.partialEnd.time.toMillisecondsSince1970(),
                        solar.peak.altitude > 0.0, solar.obscuration,
                    )
                )
                solar = nextLocalSolarEclipse(solar.peak.time, obs)
            }
        }
        return out.sortedBy { it.peakUtcMs }
    }

    /** Meteor shower peaks between [from] and [to], with the Moon's interference on the peak night. */
    fun showers(from: LocalDate, to: LocalDate, zoneOffsetMin: Int, at: GeoPoint?): List<ShowerNight> {
        val out = mutableListOf<ShowerNight>()
        for (year in from.year..to.year) for (s in SHOWERS) {
            val peak = LocalDate.of(year, s.peakMonth, s.peakDay)
            if (peak.isBefore(from) || peak.isAfter(to)) continue
            val lateNight = peak.plusDays(1).atTime(LocalTime.of(2, 0)).toInstant(ZoneOffset.ofTotalSeconds(zoneOffsetMin * 60)).toEpochMilli()
            val illum = illumination(Body.Moon, time(lateNight)).phaseFraction
            val eveningStart = time(localMidnightUtc(peak, zoneOffsetMin) + 18 * 3_600_000L)
            val moonSet = at?.let { searchRiseSet(Body.Moon, observer(it), Direction.Set, eveningStart, 1.0)?.toMillisecondsSince1970() }
            val visible = at?.let { 90.0 - abs(it.lat - s.radiantDec) >= 15.0 }
            out.add(ShowerNight(s, peak, illum, moonSet, visible))
        }
        return out.sortedBy { it.peakDate }
    }

    /**
     * The sky panel's own tier. With transits on, a retrograde station day is likely hard and an ongoing
     * retrograde is softer. Something worth stepping outside for tonight is best. Otherwise good.
     */
    fun tier(transitsOn: Boolean, retro: List<RetrogradeStatus>, notableTonight: Boolean): Pair<Tier, List<String>> {
        if (transitsOn) {
            retro.firstOrNull { it.stationing }?.let { return Tier.LIKELY_HARD to listOf("${it.period.planet} stationing") }
            retro.firstOrNull { it.period.planet == Body.Mercury.name }?.let { return Tier.NOT_GREAT to listOf("Mercury retrograde") }
            if (retro.isNotEmpty() && !notableTonight) return Tier.NOT_GREAT to listOf("${retro.first().period.planet} retrograde")
        }
        if (notableTonight) return Tier.BEST to listOf("worth stepping outside tonight")
        return Tier.GOOD to emptyList()
    }
}
