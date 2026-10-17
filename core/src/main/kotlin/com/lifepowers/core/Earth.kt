package com.lifepowers.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Volcano(val name: String, val country: String, val lat: Double, val lon: Double)

data class Quake(val id: String, val magnitude: Double, val place: String, val utcMs: Long, val lat: Double, val lon: Double, val depthKm: Double, val url: String?)

data class VolcanoReport(val name: String, val country: String, val title: String, val summary: String, val publishedUtcMs: Long?, val link: String?)

/** One ground event after filtering, with its distance from the person when a location is set. */
data class GroundEvent(val kind: String, val id: String, val title: String, val utcMs: Long, val magnitude: Double?, val distanceKm: Double?, val ringOfFire: Boolean, val link: String?)

data class KpReading(val utcMs: Long, val kp: Double, val predicted: Boolean)

enum class AuroraChance(val label: String) { LIKELY("likely"), POSSIBLE("possible"), UNLIKELY("unlikely") }

object Earth {
    const val EARTH_RADIUS_KM = 6371.0
    const val WORLD_MIN_MAG = 4.5
    const val NEARBY_MIN_MAG = 2.5
    const val RING_OF_FIRE_KM = 500.0

    /** Geomagnetic north pole, IGRF 2025 dipole. */
    private const val POLE_LAT = 80.8
    private const val POLE_LON = -72.8

    /** A bundled list of Ring of Fire volcanoes used to name reports and to test whether a quake is on the ring. */
    val RING_OF_FIRE = listOf(
        Volcano("Mount St. Helens", "United States", 46.20, -122.18), Volcano("Mount Rainier", "United States", 46.85, -121.76),
        Volcano("Mount Hood", "United States", 45.37, -121.70), Volcano("Mount Baker", "United States", 48.78, -121.81),
        Volcano("Mount Shasta", "United States", 41.41, -122.19), Volcano("Lassen Peak", "United States", 40.49, -121.51),
        Volcano("Redoubt", "United States", 60.49, -152.74), Volcano("Augustine", "United States", 59.36, -153.43),
        Volcano("Spurr", "United States", 61.30, -152.25), Volcano("Shishaldin", "United States", 54.76, -163.97),
        Volcano("Pavlof", "United States", 55.42, -161.89), Volcano("Cleveland", "United States", 52.82, -169.94),
        Volcano("Okmok", "United States", 53.43, -168.13), Volcano("Great Sitkin", "United States", 52.08, -176.13),
        Volcano("Klyuchevskoy", "Russia", 56.06, 160.64), Volcano("Sheveluch", "Russia", 56.65, 161.36),
        Volcano("Bezymianny", "Russia", 55.97, 160.59), Volcano("Sarychev Peak", "Russia", 48.09, 153.20),
        Volcano("Fujisan", "Japan", 35.36, 138.73), Volcano("Sakurajima", "Japan", 31.59, 130.66),
        Volcano("Asosan", "Japan", 32.88, 131.10), Volcano("Asamayama", "Japan", 36.41, 138.52),
        Volcano("Suwanosejima", "Japan", 29.64, 129.71), Volcano("Taal", "Philippines", 14.00, 120.99),
        Volcano("Mayon", "Philippines", 13.26, 123.69), Volcano("Pinatubo", "Philippines", 15.13, 120.35),
        Volcano("Kanlaon", "Philippines", 10.41, 123.13), Volcano("Krakatau", "Indonesia", -6.10, 105.42),
        Volcano("Merapi", "Indonesia", -7.54, 110.45), Volcano("Semeru", "Indonesia", -8.11, 112.92),
        Volcano("Agung", "Indonesia", -8.34, 115.51), Volcano("Lewotobi", "Indonesia", -8.54, 122.78),
        Volcano("Ibu", "Indonesia", 1.49, 127.63), Volcano("Rabaul", "Papua New Guinea", -4.27, 152.20),
        Volcano("Manam", "Papua New Guinea", -4.08, 145.04), Volcano("Ulawun", "Papua New Guinea", -5.05, 151.33),
        Volcano("Yasur", "Vanuatu", -19.53, 169.44), Volcano("Ambrym", "Vanuatu", -16.25, 168.12),
        Volcano("Hunga Tonga-Hunga Ha'apai", "Tonga", -20.55, -175.39), Volcano("Ruapehu", "New Zealand", -39.28, 175.57),
        Volcano("Whakaari/White Island", "New Zealand", -37.52, 177.18), Volcano("Taupo", "New Zealand", -38.82, 176.00),
        Volcano("Popocatepetl", "Mexico", 19.02, -98.62), Volcano("Colima", "Mexico", 19.51, -103.62),
        Volcano("Fuego", "Guatemala", 14.47, -90.88), Volcano("Santa Maria", "Guatemala", 14.76, -91.55),
        Volcano("Pacaya", "Guatemala", 14.38, -90.60), Volcano("Masaya", "Nicaragua", 11.98, -86.16),
        Volcano("San Cristobal", "Nicaragua", 12.70, -87.00), Volcano("Poas", "Costa Rica", 10.20, -84.23),
        Volcano("Rincon de la Vieja", "Costa Rica", 10.83, -85.32), Volcano("Nevado del Ruiz", "Colombia", 4.89, -75.32),
        Volcano("Galeras", "Colombia", 1.22, -77.37), Volcano("Reventador", "Ecuador", -0.08, -77.66),
        Volcano("Cotopaxi", "Ecuador", -0.68, -78.44), Volcano("Sangay", "Ecuador", -2.00, -78.34),
        Volcano("Sabancaya", "Peru", -15.79, -71.86), Volcano("Ubinas", "Peru", -16.36, -70.90),
        Volcano("Lascar", "Chile", -23.37, -67.73), Volcano("Villarrica", "Chile", -39.42, -71.93),
        Volcano("Calbuco", "Chile", -41.33, -72.61), Volcano("Copahue", "Chile-Argentina", -37.86, -71.17),
    )

    fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
        val r = Math.PI / 180
        val dLat = (b.lat - a.lat) * r
        val dLon = (b.lon - a.lon) * r
        val h = sin(dLat / 2).let { it * it } + cos(a.lat * r) * cos(b.lat * r) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_KM * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    fun onRingOfFire(p: GeoPoint): Boolean = RING_OF_FIRE.any { distanceKm(p, GeoPoint(it.lat, it.lon)) <= RING_OF_FIRE_KM }

    private fun normalize(s: String) = s.lowercase().replace(Regex("[^a-z]"), "")

    fun findVolcano(name: String): Volcano? {
        val n = normalize(name)
        if (n.isEmpty()) return null
        return RING_OF_FIRE.firstOrNull { normalize(it.name) == n }
            ?: RING_OF_FIRE.firstOrNull { val v = normalize(it.name); v.startsWith(n) || n.startsWith(v) }
    }

    /**
     * Keeps magnitude 4.5 and above on the Ring of Fire anywhere, and 2.5 and above within [radiusKm]
     * of [home]. Without a home only the Ring of Fire filter applies.
     */
    fun filterQuakes(quakes: List<Quake>, home: GeoPoint?, radiusKm: Double): List<GroundEvent> = quakes.mapNotNull { q ->
        val p = GeoPoint(q.lat, q.lon)
        val dist = home?.let { distanceKm(it, p) }
        val ring = onRingOfFire(p)
        val keep = (q.magnitude >= WORLD_MIN_MAG && ring) || (dist != null && dist <= radiusKm && q.magnitude >= NEARBY_MIN_MAG)
        if (!keep) null else GroundEvent("quake", q.id, "Magnitude ${"%.1f".format(q.magnitude)}, ${q.place}", q.utcMs, q.magnitude, dist, ring, q.url)
    }

    /** Volcano reports tagged with the Ring of Fire and distance when the volcano is in the bundled list. */
    fun tagVolcanoReports(reports: List<VolcanoReport>, home: GeoPoint?): List<GroundEvent> = reports.map { r ->
        val v = findVolcano(r.name)
        val dist = if (v != null && home != null) distanceKm(home, GeoPoint(v.lat, v.lon)) else null
        GroundEvent("volcano", "gvp:${normalize(r.name)}:${r.publishedUtcMs ?: 0}", r.title, r.publishedUtcMs ?: 0L, null, dist, v != null, r.link)
    }

    /** Notify only above a magnitude and within a distance the person sets; volcano reports only when nearby. */
    fun shouldNotify(e: GroundEvent, minMagnitude: Double, maxDistanceKm: Double): Boolean {
        val d = e.distanceKm ?: return false
        if (d > maxDistanceKm) return false
        return e.kind != "quake" || (e.magnitude ?: 0.0) >= minMagnitude
    }

    /** A calm sentence for a ground event. Never alarm language. */
    fun describe(e: GroundEvent): String {
        val where = e.distanceKm?.let { ", ${Math.round(it)} km from you" } ?: ""
        return e.title + where
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Parses a USGS earthquake summary GeoJSON feed. */
    fun parseUsgs(body: String): List<Quake> {
        val root = json.parseToJsonElement(body).jsonObject
        val features = root["features"] as? JsonArray ?: return emptyList()
        return features.mapNotNull { f ->
            val o = f as? JsonObject ?: return@mapNotNull null
            val props = o["properties"] as? JsonObject ?: return@mapNotNull null
            val coords = (o["geometry"] as? JsonObject)?.get("coordinates") as? JsonArray ?: return@mapNotNull null
            val mag = props["mag"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val time = props["time"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            Quake(
                id = (o["id"] as? JsonPrimitive)?.content ?: "$time",
                magnitude = mag,
                place = (props["place"] as? JsonPrimitive)?.content ?: "unknown place",
                utcMs = time,
                lat = coords.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null,
                lon = coords.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null,
                depthKm = coords.getOrNull(2)?.jsonPrimitive?.doubleOrNull ?: 0.0,
                url = (props["url"] as? JsonPrimitive)?.content,
            )
        }
    }

    private fun tag(item: String, name: String): String? =
        Regex("<$name(?:\\s[^>]*)?>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(item)?.groupValues?.get(1)
            ?.replace("<![CDATA[", "")?.replace("]]>", "")?.trim()

    private fun stripHtml(s: String) = s.replace(Regex("<[^>]+>"), " ").replace("&amp;", "&").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace(Regex("\\s+"), " ").trim()

    /** Parses the Smithsonian Global Volcanism Program weekly report RSS. Titles read "Name (Country) - Report for ...". */
    fun parseGvpRss(xml: String): List<VolcanoReport> =
        Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(xml).mapNotNull { m ->
            val item = m.groupValues[1]
            val title = tag(item, "title")?.let { stripHtml(it) } ?: return@mapNotNull null
            val head = Regex("^(.*?)\\s*\\(([^)]*)\\)").find(title)
            val name = head?.groupValues?.get(1)?.trim() ?: title.substringBefore(" - ").trim()
            val country = head?.groupValues?.get(2)?.trim() ?: ""
            val published = tag(item, "pubDate")?.let { runCatching { java.time.ZonedDateTime.parse(it, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull() }
            VolcanoReport(name, country, title, stripHtml(tag(item, "description") ?: "").take(400), published, tag(item, "link"))
        }.toList()

    private fun parseTime(s: String): Long? = runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(s.replace(' ', 'T')).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()

    private fun JsonElement.num(): Double? = (this as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }

    /**
     * Parses a NOAA SWPC planetary K index product. Accepts both the table form (a header row, then rows,
     * with an optional "observed" column that reads predicted for forecasts) and the list of objects form.
     */
    fun parseKp(body: String): List<KpReading> {
        val root = json.parseToJsonElement(body) as? JsonArray ?: return emptyList()
        if (root.isEmpty()) return emptyList()
        val first = root.first()
        if (first is JsonArray) {
            val header = first.map { (it as? JsonPrimitive)?.content?.lowercase() ?: "" }
            val iTime = header.indexOf("time_tag").takeIf { it >= 0 } ?: 0
            val iKp = header.indexOfFirst { it == "kp" }.takeIf { it >= 0 } ?: 1
            val iObs = header.indexOf("observed")
            return root.drop(1).mapNotNull { row ->
                val r = row as? JsonArray ?: return@mapNotNull null
                val t = r.getOrNull(iTime)?.jsonPrimitive?.content?.let { parseTime(it) } ?: return@mapNotNull null
                val kp = r.getOrNull(iKp)?.num() ?: return@mapNotNull null
                val predicted = iObs >= 0 && r.getOrNull(iObs)?.jsonPrimitive?.content?.lowercase() == "predicted"
                KpReading(t, kp, predicted)
            }
        }
        return root.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val t = o["time_tag"]?.jsonPrimitive?.content?.let { parseTime(it) } ?: return@mapNotNull null
            val kp = (o["kp"] ?: o["Kp"] ?: o["estimated_kp"] ?: o["kp_index"])?.num() ?: return@mapNotNull null
            val predicted = o["observed"]?.jsonPrimitive?.content?.lowercase() == "predicted"
            KpReading(t, kp, predicted)
        }
    }

    /** Geomagnetic latitude from the dipole approximation. */
    fun geomagneticLatitude(p: GeoPoint): Double {
        val r = Math.PI / 180
        val s = sin(p.lat * r) * sin(POLE_LAT * r) + cos(p.lat * r) * cos(POLE_LAT * r) * cos((p.lon - POLE_LON) * r)
        return asin(s.coerceIn(-1.0, 1.0)) / r
    }

    /** The Kp at which aurora is possible on the horizon from [p]. The oval's edge sits near 66.5 - 2 Kp magnetic. */
    fun kpNeeded(p: GeoPoint): Double = ((66.5 - 6.0 - kotlin.math.abs(geomagneticLatitude(p))) / 2.0).coerceIn(0.0, 9.0)

    fun auroraChance(kp: Double, p: GeoPoint): AuroraChance {
        val mlat = kotlin.math.abs(geomagneticLatitude(p))
        val edge = 66.5 - 2.0 * kp
        return when {
            mlat >= edge -> AuroraChance.LIKELY
            mlat >= edge - 6.0 -> AuroraChance.POSSIBLE
            else -> AuroraChance.UNLIKELY
        }
    }

    /** Initial bearing in degrees, used to say which way a ground event lies. */
    fun bearing(from: GeoPoint, to: GeoPoint): Double {
        val r = Math.PI / 180
        val y = sin((to.lon - from.lon) * r) * cos(to.lat * r)
        val x = cos(from.lat * r) * sin(to.lat * r) - sin(from.lat * r) * cos(to.lat * r) * cos((to.lon - from.lon) * r)
        return ((atan2(y, x) / r) + 360) % 360
    }
}
