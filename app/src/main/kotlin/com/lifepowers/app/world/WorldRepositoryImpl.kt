package com.lifepowers.app.world

import android.content.Context
import android.util.Log
import com.lifepowers.app.data.Dates
import com.lifepowers.app.data.personal.WorldEventDao
import com.lifepowers.app.data.personal.WorldEventEntity
import com.lifepowers.app.domain.ForecastRepository
import com.lifepowers.app.domain.LocationMode
import com.lifepowers.app.domain.LocationSetting
import com.lifepowers.app.domain.MetricRepository
import com.lifepowers.app.domain.SkySettings
import com.lifepowers.app.domain.SkyToday
import com.lifepowers.app.domain.WorldEvent
import com.lifepowers.app.domain.WorldRepository
import com.lifepowers.app.domain.nowZoneOffsetMin
import com.lifepowers.app.notify.Notices
import com.lifepowers.core.AuroraChance
import com.lifepowers.core.Earth
import com.lifepowers.core.GeoPoint
import com.lifepowers.core.GroundEvent
import com.lifepowers.core.KpReading
import com.lifepowers.core.Metrics
import com.lifepowers.core.OpenMeteo
import com.lifepowers.core.Sample
import com.lifepowers.core.Sky
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Earth and sky: where the person is, what the sky is doing, and the outside world as daily
 * metrics. Weather and air go out with a rounded coordinate; quake, volcano, and K index feeds are
 * fetched globally and filtered on the phone. Every network failure is logged and swallowed.
 */
@Singleton
class WorldRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
    private val prefs: WorldPrefs,
    private val sky: SkyComputer,
    private val device: DeviceLocation,
    private val metrics: MetricRepository,
    private val forecasts: ForecastRepository,
    private val events: WorldEventDao,
) : WorldRepository {

    private val refreshLock = Mutex()

    override val location: Flow<LocationSetting> = prefs.location

    override val skySettings: Flow<SkySettings> = prefs.skySettings

    /** Stores the setting, rounded for APPROXIMATE and cleared for NONE. Callers refresh afterwards. */
    override suspend fun setLocation(setting: LocationSetting) {
        val point = when (setting.mode) {
            LocationMode.NONE -> null
            LocationMode.APPROXIMATE -> setting.point?.rounded()
            else -> setting.point
        }
        val stored = setting.copy(
            point = point,
            label = if (setting.mode == LocationMode.NONE) null else setting.label,
            updatedUtcMs = System.currentTimeMillis(),
        )
        val previous = prefs.location.first().point
        prefs.saveLocation(stored)
        // Another place's readings must not show under the new one until it refreshes.
        val moved = point == null || previous == null || Earth.distanceKm(previous, point) > PLACE_CHANGE_KM
        if (moved) {
            prefs.clearWeather()
            sky.bump()
        }
    }

    override suspend fun refreshDeviceLocation(): Boolean {
        val current = prefs.location.first()
        if (current.mode != LocationMode.PRECISE && current.mode != LocationMode.APPROXIMATE) return false
        if (!device.hasPermission()) return false
        val fix = device.current() ?: return false
        val raw = GeoPoint(fix.latitude, fix.longitude)
        val point = if (current.mode == LocationMode.APPROXIMATE) raw.rounded() else raw
        val label = device.label(point) ?: current.label ?: "Your area"
        setLocation(current.copy(point = point, label = label))
        return true
    }

    /** Re-reads the device location when it is older than [maxAgeMs]. Used by the periodic worker. */
    suspend fun refreshDeviceLocationIfStale(maxAgeMs: Long = 20 * WorldMath.HOUR) {
        val current = prefs.location.first()
        if (current.mode != LocationMode.PRECISE && current.mode != LocationMode.APPROXIMATE) return
        // A setting that has never produced a point is always stale, so a first miss is retried.
        if (current.point != null && System.currentTimeMillis() - current.updatedUtcMs < maxAgeMs) return
        guard("device location") { refreshDeviceLocation() }
    }

    override suspend fun searchCity(name: String): List<OpenMeteo.Place> {
        if (name.isBlank()) return emptyList()
        val body = get(OpenMeteo.geocodeUrl(name.trim())) ?: return emptyList()
        return parse("geocode") { OpenMeteo.parseGeocode(body) }.orEmpty()
    }

    override suspend fun saveSkySettings(settings: SkySettings) {
        prefs.saveSkySettings(settings)
        WorldWork.rescheduleEvening(context, settings.eveningHour)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun skyToday(date: LocalDate): Flow<SkyToday> =
        combine(prefs.location, prefs.skySettings, sky.tick) { loc, settings, tick -> Triple(loc, settings.transitsOn, tick) }
            .distinctUntilChanged()
            .mapLatest { (loc, transitsOn, _) -> sky.compute(date, loc, transitsOn) }
            .flowOn(Dispatchers.Default)

    override fun groundEvents(sinceUtcMs: Long): Flow<List<WorldEvent>> =
        events.since(sinceUtcMs).map { rows -> rows.map { it.toDomain() } }

    override suspend fun refresh() {
        refreshLock.withLock {
            guard("refresh") { withContext(Dispatchers.IO) { runRefresh() } }
        }
    }

    private suspend fun runRefresh() {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = LocalDate.now(zone)
        val point = prefs.location.first().point
        val settings = prefs.skySettings.first()
        val touched = Touched()

        if (point != null) {
            guard("weather") { refreshWeather(point, touched) }
            guard("backfill") { backfill(point, today, touched) }
        }
        guard("moon") { writeMoon(today, zone, touched) }
        var readings = prefs.kpReadings()
        guard("kp") { readings = refreshKp(readings, today, zone, now, touched) }
        guard("ground") { refreshGround(point, settings, now) }

        sky.bump()
        touched.from?.let { from -> guard("rollup") { metrics.rebuildDaily(from, today) } }
        guard("forecast") { forecasts.recompute(today) }
        guard("ground notices") { notifyGround(settings, now) }
        if (point != null) guard("aurora") { checkAurora(point, settings, readings, zone, now) }
    }

    /** (a) Forecast and air for the last week and today; completed days become samples, today is cached. */
    private suspend fun refreshWeather(point: GeoPoint, touched: Touched) {
        val body = get(OpenMeteo.forecastUrl(point)) ?: return
        val offset = OpenMeteo.utcOffsetMin(body)
        val weather = OpenMeteo.parseWeather(body)
        val air = get(OpenMeteo.airUrl(point))?.let { b -> parse("air") { OpenMeteo.parseAir(b) } }.orEmpty()
        val days = OpenMeteo.merge(weather, air)
        val todayThere = Dates.today(offset)
        val samples = OpenMeteo.toSamples(days, offset, todayThere.minusDays(1))
        metrics.upsertSamples(samples)
        samples.minOfOrNull { it.startUtcMs }?.let { touched.add(Dates.localDate(it, nowZoneOffsetMin())) }
        prefs.saveWeather(days.filter { !it.date.isBefore(todayThere) })
    }

    /**
     * (b) The weather archive from the start of the person's own data (at most a year back) to six
     * days ago, in chunks of 90 days. The marker moves after each chunk, so this runs once and then
     * only fills a gap the seven day forecast window missed.
     */
    private suspend fun backfill(point: GeoPoint, today: LocalDate, touched: Touched) {
        val end = today.minusDays(6)
        val through = prefs.date(WorldPrefs.BACKFILL_THROUGH)
        val start = if (through != null) {
            through.plusDays(1)
        } else {
            val floor = today.minusDays(365)
            val earliest = WorldMath.earliestPersonalDate(metrics.dailyBetween(floor, today).first()) ?: return
            if (earliest.isBefore(floor)) floor else earliest
        }
        if (start.isAfter(end)) {
            if (through == null) prefs.setDate(WorldPrefs.BACKFILL_THROUGH, end)
            return
        }
        for ((from, to) in WorldMath.chunks(start, end, ARCHIVE_CHUNK_DAYS)) {
            val body = get(OpenMeteo.archiveUrl(point, from, to)) ?: return
            val offset = OpenMeteo.utcOffsetMin(body)
            metrics.upsertSamples(OpenMeteo.toSamples(OpenMeteo.parseWeather(body), offset, to))
            touched.add(from)
            prefs.setDate(WorldPrefs.BACKFILL_THROUGH, to)
        }
    }

    /** (c) Moon illumination at local noon for every day of the last year not yet written. Needs no location. */
    private suspend fun writeMoon(today: LocalDate, zone: ZoneId, touched: Touched) = withContext(Dispatchers.Default) {
        val floor = today.minusDays(365)
        val next = prefs.date(WorldPrefs.MOON_THROUGH)?.plusDays(1)
        val first = if (next == null || next.isBefore(floor)) floor else next
        if (first.isAfter(today)) return@withContext
        val samples = ArrayList<Sample>()
        var d = first
        while (!d.isAfter(today)) {
            val noon = WorldMath.localNoonUtc(d, zone)
            samples += Sample(Metrics.MOON_ILLUMINATION, Sky.moon(noon).illumination, noon, null, WorldMath.offsetMin(d, zone), SOURCE_ASTRONOMY)
            d = d.plusDays(1)
        }
        metrics.upsertSamples(samples)
        prefs.setDate(WorldPrefs.MOON_THROUGH, today)
        touched.add(first)
    }

    /** (d) Observed and forecast K index, cached for the Sky screen; daily maxima for completed days. */
    private suspend fun refreshKp(cached: List<KpReading>, today: LocalDate, zone: ZoneId, now: Long, touched: Touched): List<KpReading> {
        val observed = get(KP_OBSERVED_URL)?.let { b -> parse("kp") { Earth.parseKp(b) } }.orEmpty()
        val predicted = get(KP_FORECAST_URL)?.let { b -> parse("kp forecast") { Earth.parseKp(b) } }.orEmpty()
        if (observed.isEmpty() && predicted.isEmpty()) return cached
        val merged = WorldMath.mergeKp(listOf(cached, predicted, observed), now - KP_KEEP_DAYS * WorldMath.DAY)
        prefs.saveKp(merged)
        val daily = WorldMath.dailyKpMax(merged, zone, today)
        metrics.upsertSamples(
            daily.map { (d, kp) -> Sample(Metrics.SPACE_KP_MAX, kp, WorldMath.localNoonUtc(d, zone), null, WorldMath.offsetMin(d, zone), SOURCE_NOAA) },
        )
        daily.keys.minByOrNull { it.toEpochDay() }?.let { touched.add(it) }
        return merged
    }

    /** (e, f) Quakes and volcano reports, filtered on the phone, kept for 30 days. */
    private suspend fun refreshGround(point: GeoPoint?, settings: SkySettings, now: Long) {
        val quakes = listOf(USGS_WEEK_45, USGS_DAY_25)
            .flatMap { url -> get(url)?.let { b -> parse("usgs") { Earth.parseUsgs(b) } }.orEmpty() }
            .distinctBy { it.id }
        val reports = get(GVP_RSS)?.let { b -> parse("gvp") { Earth.parseGvpRss(b) } }.orEmpty()
        val cutoff = now - GROUND_KEEP_DAYS * WorldMath.DAY
        val rows = (Earth.filterQuakes(quakes, point, settings.groundRadiusKm) + Earth.tagVolcanoReports(reports, point))
            .map { if (it.utcMs <= 0L) it.copy(utcMs = now) else it }
            .filter { it.utcMs >= cutoff }
            .map { it.toEntity() }
        if (rows.isNotEmpty()) events.insertNew(rows)
        events.prune(cutoff)
    }

    /**
     * Ground notices for new events above the person's magnitude and inside their radius. Only the
     * last day's events can notify, at most three per refresh, so a first run never floods. Every
     * pending event is marked as processed either way.
     */
    private suspend fun notifyGround(settings: SkySettings, now: Long) {
        val pending = events.unnotified()
        if (pending.isEmpty()) return
        if (settings.notifyGround) {
            pending.map { it.toGround() }
                .filter { it.utcMs >= now - WorldMath.DAY && Earth.shouldNotify(it, settings.groundMinMagnitude, settings.groundRadiusKm) }
                .take(MAX_GROUND_NOTICES)
                .forEach { e ->
                    val title = if (e.kind == "quake") "Earthquake recorded" else "Volcano report"
                    Notices.post(context, Notices.CHANNEL_GROUND, Notices.Ids.ground(e.id), title, Earth.describe(e), route = ROUTE_SKY)
                }
        }
        pending.forEach { events.markNotified(it.id) }
    }

    /** Aurora for the coming night: a timeline moment, and one notice per night when the person wants it. */
    private suspend fun checkAurora(point: GeoPoint, settings: SkySettings, readings: List<KpReading>, zone: ZoneId, now: Long) {
        val night = WorldMath.eveningDateFor(Instant.ofEpochMilli(now).atZone(zone))
        val kp = WorldMath.nightMaxKp(readings, night, zone) ?: return
        val chance = Earth.auroraChance(kp, point)
        if (chance == AuroraChance.UNLIKELY) return
        val key = night.toString()
        val kpText = String.format(Locale.US, "%.1f", kp)
        events.insertNew(
            listOf(
                WorldEventEntity(
                    id = "aurora:$key", kind = "aurora", title = "Aurora ${chance.label} tonight, Kp up to $kpText",
                    utc = WorldMath.eveningUtc(night, zone), magnitude = kp, distanceKm = null, ringOfFire = false, link = null, notified = true,
                ),
            ),
        )
        if (!settings.notifyAurora) return
        if (prefs.date(WorldPrefs.AURORA_NIGHT) == night) return
        val look = if (point.lat >= 0) "north" else "south"
        Notices.post(
            context, Notices.CHANNEL_SKY, Notices.Ids.AURORA,
            "Aurora ${chance.label} tonight",
            "Aurora ${chance.label} tonight at your latitude. Look $look after dark, away from city lights.",
            route = ROUTE_SKY,
        )
        prefs.setDate(WorldPrefs.AURORA_NIGHT, night)
    }

    private suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "${request.url.host} answered ${response.code}")
                    null
                } else {
                    response.body?.string()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "${request.url.host} failed: ${e.javaClass.simpleName}")
            null
        }
    }

    private inline fun <T> parse(what: String, block: () -> T): T? = try {
        block()
    } catch (e: Exception) {
        Log.w(TAG, "could not read $what: ${e.javaClass.simpleName}")
        null
    }

    private suspend fun guard(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$what failed", e)
        }
    }

    /** The earliest local date written during one refresh. */
    private class Touched {
        var from: LocalDate? = null
            private set

        fun add(date: LocalDate) {
            val f = from
            if (f == null || date.isBefore(f)) from = date
        }
    }

    private fun GroundEvent.toEntity() = WorldEventEntity(
        id = id, kind = kind, title = title, utc = utcMs, magnitude = magnitude, distanceKm = distanceKm,
        ringOfFire = ringOfFire, link = link, notified = false,
    )

    private fun WorldEventEntity.toGround() = GroundEvent(kind, id, title, utc, magnitude, distanceKm, ringOfFire, link)

    private fun WorldEventEntity.toDomain() = WorldEvent(
        id = id, kind = kind, title = title, utcMs = utc, magnitude = magnitude, distanceKm = distanceKm,
        ringOfFire = ringOfFire, link = link, notified = notified,
    )

    private companion object {
        const val TAG = "World"
        const val ROUTE_SKY = "sky"
        const val USER_AGENT = "LifePowers/0.1 (Android)"
        const val SOURCE_ASTRONOMY = "astronomy_engine"
        const val SOURCE_NOAA = "noaa_swpc"
        const val ARCHIVE_CHUNK_DAYS = 90
        const val KP_KEEP_DAYS = 10L
        const val GROUND_KEEP_DAYS = 30L
        const val MAX_GROUND_NOTICES = 3
        const val PLACE_CHANGE_KM = 25.0
        const val KP_OBSERVED_URL = "https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json"
        const val KP_FORECAST_URL = "https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json"
        const val USGS_WEEK_45 = "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/4.5_week.geojson"
        const val USGS_DAY_25 = "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_day.geojson"
        const val GVP_RSS = "https://volcano.si.edu/news/WeeklyVolcanoRSS.xml"
    }
}
