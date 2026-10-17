package com.lifepowers.app.world

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lifepowers.app.domain.LocationMode
import com.lifepowers.app.domain.LocationSetting
import com.lifepowers.app.domain.SkySettings
import com.lifepowers.core.DailyWeather
import com.lifepowers.core.GeoPoint
import com.lifepowers.core.KpReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
internal data class WeatherDto(
    val date: String,
    val pressureMean: Double? = null,
    val pressureChange24h: Double? = null,
    val tempMax: Double? = null,
    val tempMin: Double? = null,
    val humidityMean: Double? = null,
    val daylightMin: Double? = null,
    val uvMax: Double? = null,
    val pm25Mean: Double? = null,
    val aqiMax: Double? = null,
)

@Serializable
internal data class KpDto(val t: Long, val kp: Double, val p: Boolean)

/** Earth and sky state kept in the shared settings DataStore. Every key starts with "world_". */
@Singleton
class WorldPrefs @Inject constructor(
    private val store: DataStore<Preferences>,
    private val json: Json,
) {
    private val data: Flow<Preferences> = store.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    val location: Flow<LocationSetting> = data.map { it.toLocation() }.distinctUntilChanged()

    val skySettings: Flow<SkySettings> = data.map { it.toSkySettings() }.distinctUntilChanged()

    suspend fun saveLocation(s: LocationSetting) {
        store.edit { p ->
            p[LOC_MODE] = s.mode.name
            if (s.label.isNullOrBlank()) p.remove(LOC_LABEL) else p[LOC_LABEL] = s.label
            val point = s.point
            if (point == null) {
                p.remove(LOC_LAT); p.remove(LOC_LON)
            } else {
                p[LOC_LAT] = point.lat; p[LOC_LON] = point.lon
            }
            p[LOC_UPDATED] = s.updatedUtcMs
        }
    }

    suspend fun saveSkySettings(s: SkySettings) {
        store.edit { p ->
            p[TRANSITS_ON] = s.transitsOn
            p[NOTIFY_SKY] = s.notifySky
            p[NOTIFY_AURORA] = s.notifyAurora
            p[NOTIFY_GROUND] = s.notifyGround
            p[GROUND_MIN_MAG] = s.groundMinMagnitude
            p[GROUND_RADIUS_KM] = s.groundRadiusKm
            p[EVENING_HOUR] = s.eveningHour.coerceIn(0, 23)
        }
    }

    /** Today's and tomorrow's weather as last fetched, partial values included. */
    suspend fun weather(): List<DailyWeather> {
        val raw = data.first()[WEATHER_JSON] ?: return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(WeatherDto.serializer()), raw) }.getOrDefault(emptyList())
            .mapNotNull { it.toDomain() }
    }

    suspend fun saveWeather(days: List<DailyWeather>) {
        val raw = json.encodeToString(ListSerializer(WeatherDto.serializer()), days.map { it.toDto() })
        store.edit { it[WEATHER_JSON] = raw }
    }

    suspend fun kpReadings(): List<KpReading> {
        val raw = data.first()[KP_JSON] ?: return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(KpDto.serializer()), raw) }.getOrDefault(emptyList())
            .map { KpReading(it.t, it.kp, it.p) }
    }

    suspend fun saveKp(readings: List<KpReading>) {
        val raw = json.encodeToString(ListSerializer(KpDto.serializer()), readings.map { KpDto(it.utcMs, it.kp, it.predicted) })
        store.edit { it[KP_JSON] = raw }
    }

    suspend fun date(key: Preferences.Key<String>): LocalDate? =
        data.first()[key]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    suspend fun setDate(key: Preferences.Key<String>, date: LocalDate) {
        store.edit { it[key] = date.toString() }
    }

    private fun Preferences.toLocation(): LocationSetting {
        val lat = this[LOC_LAT]
        val lon = this[LOC_LON]
        return LocationSetting(
            mode = this[LOC_MODE]?.let { m -> runCatching { LocationMode.valueOf(m) }.getOrNull() } ?: LocationMode.NONE,
            label = this[LOC_LABEL],
            point = if (lat != null && lon != null) GeoPoint(lat, lon) else null,
            updatedUtcMs = this[LOC_UPDATED] ?: 0L,
        )
    }

    private fun Preferences.toSkySettings(): SkySettings {
        val d = SkySettings()
        return SkySettings(
            transitsOn = this[TRANSITS_ON] ?: d.transitsOn,
            notifySky = this[NOTIFY_SKY] ?: d.notifySky,
            notifyAurora = this[NOTIFY_AURORA] ?: d.notifyAurora,
            notifyGround = this[NOTIFY_GROUND] ?: d.notifyGround,
            groundMinMagnitude = this[GROUND_MIN_MAG] ?: d.groundMinMagnitude,
            groundRadiusKm = this[GROUND_RADIUS_KM] ?: d.groundRadiusKm,
            eveningHour = this[EVENING_HOUR] ?: d.eveningHour,
        )
    }

    private fun DailyWeather.toDto() = WeatherDto(
        date.toString(), pressureMean, pressureChange24h, tempMax, tempMin, humidityMean, daylightMin, uvMax, pm25Mean, aqiMax,
    )

    private fun WeatherDto.toDomain(): DailyWeather? {
        val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
        return DailyWeather(d, pressureMean, pressureChange24h, tempMax, tempMin, humidityMean, daylightMin, uvMax, pm25Mean, aqiMax)
    }

    companion object {
        val LOC_MODE = stringPreferencesKey("world_loc_mode")
        val LOC_LABEL = stringPreferencesKey("world_loc_label")
        val LOC_LAT = doublePreferencesKey("world_loc_lat")
        val LOC_LON = doublePreferencesKey("world_loc_lon")
        val LOC_UPDATED = longPreferencesKey("world_loc_updated")

        val TRANSITS_ON = booleanPreferencesKey("world_transits_on")
        val NOTIFY_SKY = booleanPreferencesKey("world_notify_sky")
        val NOTIFY_AURORA = booleanPreferencesKey("world_notify_aurora")
        val NOTIFY_GROUND = booleanPreferencesKey("world_notify_ground")
        val GROUND_MIN_MAG = doublePreferencesKey("world_ground_min_mag")
        val GROUND_RADIUS_KM = doublePreferencesKey("world_ground_radius_km")
        val EVENING_HOUR = intPreferencesKey("world_evening_hour")

        val WEATHER_JSON = stringPreferencesKey("world_weather_json")
        val KP_JSON = stringPreferencesKey("world_kp_json")

        /** Last day the weather archive has been fetched through. */
        val BACKFILL_THROUGH = stringPreferencesKey("world_backfill_through")
        /** Last day a moon illumination sample has been written for. */
        val MOON_THROUGH = stringPreferencesKey("world_moon_through")
        /** The evening date an aurora notice was last posted for. */
        val AURORA_NIGHT = stringPreferencesKey("world_aurora_night")
    }
}
