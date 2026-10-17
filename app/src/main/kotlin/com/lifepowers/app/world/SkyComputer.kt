package com.lifepowers.app.world

import com.lifepowers.app.domain.LocationSetting
import com.lifepowers.app.domain.SkyToday
import com.lifepowers.core.DailyWeather
import com.lifepowers.core.Earth
import com.lifepowers.core.KpReading
import com.lifepowers.core.Sky
import com.lifepowers.core.Tier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Computes [SkyToday] on the phone. Results are kept per (date, location, transits switch, refresh
 * tick) because the retrograde scan costs a fraction of a second. Both the Sky screen and the
 * forecast's sky tier read from here, so they always agree.
 */
@Singleton
class SkyComputer @Inject constructor(private val prefs: WorldPrefs) {

    private val _tick = MutableStateFlow(0L)
    /** Bumped after each network refresh so cached days pick up new K index and weather. */
    val tick: StateFlow<Long> = _tick.asStateFlow()

    fun bump() = _tick.update { it + 1 }

    private data class Key(val date: LocalDate, val location: LocationSetting, val transitsOn: Boolean, val tick: Long)

    private val lock = Mutex()
    private val cache = object : LinkedHashMap<Key, SkyToday>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, SkyToday>?): Boolean = size > MAX_CACHED
    }

    suspend fun compute(date: LocalDate): SkyToday =
        compute(date, prefs.location.first(), prefs.skySettings.first().transitsOn)

    suspend fun compute(date: LocalDate, location: LocationSetting, transitsOn: Boolean): SkyToday = lock.withLock {
        val key = Key(date, location, transitsOn, _tick.value)
        cache[key] ?: run {
            val readings = prefs.kpReadings()
            val weather = if (location.point == null) null else prefs.weather().firstOrNull { it.date == date }
            val result = withContext(Dispatchers.Default) {
                build(date, location, transitsOn, readings, weather, System.currentTimeMillis(), ZoneId.systemDefault())
            }
            cache[key] = result
            result
        }
    }

    companion object {
        private const val MAX_CACHED = 8

        /**
         * Builds one day. Retrogrades are only scanned with transits on, since nothing else reads
         * them. Eclipses are searched from a day before that evening so the tier sees one that
         * peaked last night, while the listed twelve months start now (or at the date's midnight).
         */
        fun build(
            date: LocalDate,
            location: LocationSetting,
            transitsOn: Boolean,
            readings: List<KpReading>,
            weather: DailyWeather?,
            nowMs: Long,
            zone: ZoneId,
        ): SkyToday {
            val point = location.point
            val offsetMin = WorldMath.offsetMin(date, zone)
            val noon = WorldMath.localNoonUtc(date, zone)
            val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()

            val moon = Sky.moon(noon, point)
            val retro = if (transitsOn) Sky.retrogradeNow(noon) else emptyList()
            val planets = point?.let { Sky.visiblePlanets(date, offsetMin, it) } ?: emptyList()
            val pairings = point?.let { Sky.pairings(date, offsetMin, it) } ?: emptyList()

            val listFrom = if (date == today) nowMs else date.atStartOfDay(zone).toInstant().toEpochMilli()
            val searchFrom = minOf(listFrom, WorldMath.eveningUtc(date, zone) - WorldMath.DAY)
            val searchDays = ((listFrom - searchFrom) / WorldMath.DAY).toInt() + 366
            val allEclipses = Sky.eclipses(searchFrom, searchDays, point)
            val eclipses = allEclipses.filter { it.endUtcMs >= listFrom && it.peakUtcMs <= listFrom + 365 * WorldMath.DAY }
            val showers = Sky.showers(date, date.plusDays(60), offsetMin, point)

            val kpNow = WorldMath.kpNow(readings, nowMs)
            val kpTonight = WorldMath.nightMaxKp(readings, date, zone)
            val kpForAurora = kpTonight ?: if (date == today) kpNow else null
            val aurora = if (point != null && kpForAurora != null) Earth.auroraChance(kpForAurora, point) else null
            val kpNeeded = point?.let { Earth.kpNeeded(it) }

            val notable = WorldMath.notableReasons(date, zone, showers, allEclipses, aurora, pairings)
            val (tier, reasons) = Sky.tier(transitsOn, retro, notable.isNotEmpty())

            return SkyToday(
                date = date,
                location = location,
                moon = moon,
                retrogrades = retro,
                planets = planets,
                pairings = pairings,
                eclipses = eclipses,
                showers = showers,
                kpNow = kpNow,
                kpTonightMax = kpTonight,
                aurora = aurora,
                kpNeeded = kpNeeded,
                weather = weather?.takeIf { it.date == date },
                tier = tier,
                tierReasons = if (tier == Tier.BEST && notable.isNotEmpty()) notable else reasons,
                transitsOn = transitsOn,
            )
        }
    }
}
