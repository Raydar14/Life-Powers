package com.lifepowers.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.abs

/** One local day of weather and air, as the rollup wants it. Any field can be missing. */
data class DailyWeather(
    val date: LocalDate,
    val pressureMean: Double? = null,
    /** The signed 24 hour change with the largest size ending inside this day. */
    val pressureChange24h: Double? = null,
    val tempMax: Double? = null,
    val tempMin: Double? = null,
    val humidityMean: Double? = null,
    val daylightMin: Double? = null,
    val uvMax: Double? = null,
    val pm25Mean: Double? = null,
    val aqiMax: Double? = null,
)

/**
 * Builds Open-Meteo requests and parses their responses. The coordinate is always rounded to one
 * decimal place before it is put in a URL, so the service learns the town and not the street.
 */
object OpenMeteo {
    const val SOURCE = "open_meteo"
    private const val HOURLY = "surface_pressure,relative_humidity_2m"
    private const val DAILY = "temperature_2m_max,temperature_2m_min,daylight_duration"

    private fun coord(p: GeoPoint) = p.rounded().let { "latitude=${it.lat}&longitude=${it.lon}" }

    fun forecastUrl(p: GeoPoint, pastDays: Int = 7): String =
        "https://api.open-meteo.com/v1/forecast?${coord(p)}&hourly=$HOURLY&daily=$DAILY,uv_index_max&past_days=$pastDays&forecast_days=2&timezone=auto"

    fun archiveUrl(p: GeoPoint, from: LocalDate, to: LocalDate): String =
        "https://archive-api.open-meteo.com/v1/archive?${coord(p)}&start_date=$from&end_date=$to&hourly=$HOURLY&daily=$DAILY&timezone=auto"

    fun airUrl(p: GeoPoint, pastDays: Int = 7): String =
        "https://air-quality-api.open-meteo.com/v1/air-quality?${coord(p)}&hourly=pm2_5,us_aqi&past_days=$pastDays&forecast_days=1&timezone=auto"

    fun geocodeUrl(name: String): String =
        "https://geocoding-api.open-meteo.com/v1/search?name=${java.net.URLEncoder.encode(name, "UTF-8")}&count=5&language=en&format=json"

    data class Place(val name: String, val region: String?, val country: String?, val point: GeoPoint)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun JsonObject.series(group: String, key: String): List<Double?> =
        ((this[group] as? JsonObject)?.get(key) as? JsonArray)?.map { (it as? JsonPrimitive)?.doubleOrNull } ?: emptyList()

    private fun JsonObject.times(group: String): List<String> =
        ((this[group] as? JsonObject)?.get("time") as? JsonArray)?.map { (it as JsonPrimitive).content } ?: emptyList()

    fun utcOffsetMin(body: String): Int =
        ((json.parseToJsonElement(body).jsonObject["utc_offset_seconds"] as? JsonPrimitive)?.intOrNull ?: 0) / 60

    /** Parses a forecast or archive response into one record per local day. */
    fun parseWeather(body: String): List<DailyWeather> {
        val root = json.parseToJsonElement(body).jsonObject
        val hTimes = root.times("hourly").map { LocalDateTime.parse(it) }
        val pressure = root.series("hourly", "surface_pressure")
        val humidity = root.series("hourly", "relative_humidity_2m")
        val byDay = hTimes.indices.groupBy { hTimes[it].toLocalDate() }

        val dTimes = root.times("daily").map { LocalDate.parse(it) }
        val tMax = root.series("daily", "temperature_2m_max")
        val tMin = root.series("daily", "temperature_2m_min")
        val daylight = root.series("daily", "daylight_duration")
        val uv = root.series("daily", "uv_index_max")

        val days = (byDay.keys + dTimes).toSortedSet()
        return days.map { date ->
            val idx = byDay[date].orEmpty()
            val p = idx.mapNotNull { pressure.getOrNull(it) }
            val h = idx.mapNotNull { humidity.getOrNull(it) }
            val change = idx.mapNotNull { i -> val a = pressure.getOrNull(i); val b = pressure.getOrNull(i - 24); if (i >= 24 && a != null && b != null) a - b else null }
                .maxByOrNull { abs(it) }
            val di = dTimes.indexOf(date)
            DailyWeather(
                date = date,
                pressureMean = p.takeIf { it.size >= 12 }?.average(),
                pressureChange24h = change,
                tempMax = tMax.getOrNull(di),
                tempMin = tMin.getOrNull(di),
                humidityMean = h.takeIf { it.size >= 12 }?.average(),
                daylightMin = daylight.getOrNull(di)?.let { it / 60.0 },
                uvMax = uv.getOrNull(di),
            )
        }
    }

    /** Parses an air quality response into per day PM2.5 mean and US AQI maximum. */
    fun parseAir(body: String): Map<LocalDate, Pair<Double?, Double?>> {
        val root = json.parseToJsonElement(body).jsonObject
        val times = root.times("hourly").map { LocalDateTime.parse(it) }
        val pm = root.series("hourly", "pm2_5")
        val aqi = root.series("hourly", "us_aqi")
        return times.indices.groupBy { times[it].toLocalDate() }.mapValues { (_, idx) ->
            val p = idx.mapNotNull { pm.getOrNull(it) }
            val a = idx.mapNotNull { aqi.getOrNull(it) }
            (if (p.size >= 12) p.average() else null) to a.maxOrNull()
        }
    }

    fun merge(weather: List<DailyWeather>, air: Map<LocalDate, Pair<Double?, Double?>>): List<DailyWeather> {
        val byDate = weather.associateBy { it.date }.toMutableMap()
        for ((d, a) in air) byDate[d] = (byDate[d] ?: DailyWeather(d)).copy(pm25Mean = a.first, aqiMax = a.second)
        return byDate.values.sortedBy { it.date }
    }

    /**
     * Turns days into samples stamped at local noon. Days after [lastCompleteDay] are skipped so a
     * forecast is never stored as a measurement.
     */
    fun toSamples(days: List<DailyWeather>, zoneOffsetMin: Int, lastCompleteDay: LocalDate): List<Sample> = days.filter { !it.date.isAfter(lastCompleteDay) }.flatMap { d ->
        val noon = d.date.atTime(LocalTime.NOON).toInstant(ZoneOffset.ofTotalSeconds(zoneOffsetMin * 60)).toEpochMilli()
        fun s(metric: String, v: Double?) = v?.let { Sample(metric, it, noon, null, zoneOffsetMin, SOURCE) }
        listOfNotNull(
            s(Metrics.WEATHER_PRESSURE_MEAN, d.pressureMean),
            s(Metrics.WEATHER_PRESSURE_CHANGE_24H, d.pressureChange24h),
            s(Metrics.WEATHER_PRESSURE_SWING, d.pressureChange24h?.let { abs(it) }),
            s(Metrics.WEATHER_TEMP_MAX, d.tempMax),
            s(Metrics.WEATHER_TEMP_MIN, d.tempMin),
            s(Metrics.WEATHER_HUMIDITY_MEAN, d.humidityMean),
            s(Metrics.WEATHER_DAYLIGHT_MIN, d.daylightMin),
            s(Metrics.WEATHER_UV_MAX, d.uvMax),
            s(Metrics.AIR_PM25, d.pm25Mean),
            s(Metrics.AIR_AQI_MAX, d.aqiMax),
        )
    }

    fun parseGeocode(body: String): List<Place> {
        val results = json.parseToJsonElement(body).jsonObject["results"] as? JsonArray ?: return emptyList()
        return results.mapNotNull { r ->
            val o = r as? JsonObject ?: return@mapNotNull null
            val lat = (o["latitude"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val lon = (o["longitude"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            Place((o["name"] as? JsonPrimitive)?.content ?: return@mapNotNull null, (o["admin1"] as? JsonPrimitive)?.content, (o["country"] as? JsonPrimitive)?.content, GeoPoint(lat, lon))
        }
    }
}
