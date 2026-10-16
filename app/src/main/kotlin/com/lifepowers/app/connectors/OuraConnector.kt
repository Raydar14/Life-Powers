package com.lifepowers.app.connectors

import com.lifepowers.app.domain.BehaviorEvent
import com.lifepowers.app.domain.Connector
import com.lifepowers.app.domain.ConnectorResult
import com.lifepowers.app.domain.SettingsRepository
import com.lifepowers.core.Sample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Oura Cloud API v2 with a personal access token. The cursor is the ISO date last synced; the
 * first run goes 90 days back. Each endpoint is fetched independently so one failing stream does
 * not lose the others; the sync only fails when every stream fails.
 */
@Singleton
class OuraConnector @Inject constructor(
    private val http: OkHttpClient,
    private val settings: SettingsRepository,
) : Connector {

    override val id: String = ID
    override val displayName: String = "Oura"

    override suspend fun isAvailable(): Boolean = !settings.ouraToken.first().isNullOrBlank()

    override suspend fun sync(cursor: String?, zoneOffsetMin: Int): ConnectorResult {
        val token = settings.ouraToken.first()
        if (token.isNullOrBlank()) return ConnectorResult(emptyList())

        val today = LocalDate.now(ZoneOffset.ofTotalSeconds(zoneOffsetMin * 60))
        val start = cursor?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today.minusDays(90)
        val startIso = start.toString()
        val endIso = today.toString()

        val samples = ArrayList<Sample>()
        val events = ArrayList<BehaviorEvent>()
        val errors = ArrayList<Throwable>()

        suspend fun stream(path: String, map: (String) -> Unit) {
            try {
                fetchPages(token, path, startIso, endIso).forEach(map)
            } catch (e: Exception) {
                errors += e
            }
        }

        stream("daily_sleep") { samples += OuraMapper.dailySleep(it, zoneOffsetMin) }
        stream("daily_readiness") { samples += OuraMapper.dailyReadiness(it, zoneOffsetMin) }
        stream("sleep") { samples += OuraMapper.sleep(it, zoneOffsetMin) }
        stream("daily_activity") { samples += OuraMapper.dailyActivity(it, zoneOffsetMin) }
        stream("daily_stress") { samples += OuraMapper.dailyStress(it, zoneOffsetMin) }
        stream("enhanced_tag") { events += OuraMapper.tags(it, zoneOffsetMin) }

        if (errors.size == STREAM_COUNT) throw errors.first()

        // Step back one day so a night that finishes after this sync is refreshed next time.
        return ConnectorResult(samples, events, today.minusDays(1).toString())
    }

    /** Follows next_token until the API stops returning one. Returns every page body. */
    private suspend fun fetchPages(token: String, path: String, startIso: String, endIso: String): List<String> =
        withContext(Dispatchers.IO) {
            val bodies = ArrayList<String>()
            var next: String? = null
            do {
                val builder = (BASE + path).toHttpUrl().newBuilder()
                    .addQueryParameter("start_date", startIso)
                    .addQueryParameter("end_date", endIso)
                val pageToken = next
                if (pageToken != null) builder.addQueryParameter("next_token", pageToken)
                val url: HttpUrl = builder.build()
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
                val body = http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Oura $path HTTP ${response.code}")
                    response.body?.string() ?: throw IOException("Oura $path empty body")
                }
                bodies += body
                next = OuraMapper.nextToken(body)
            } while (!next.isNullOrBlank() && bodies.size < MAX_PAGES)
            bodies
        }

    companion object {
        const val ID = "oura"
        const val BASE = "https://api.ouraring.com/v2/usercollection/"
        private const val STREAM_COUNT = 6
        private const val MAX_PAGES = 50
    }
}
