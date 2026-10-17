package com.lifepowers.app.world

import com.lifepowers.app.domain.SkyToday
import com.lifepowers.core.SkyWindow
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * The quiet evening sky notice, built only from what the phone already computed. Returns null when
 * nothing is worth stepping outside for, so most evenings stay silent.
 */
object EveningNotice {
    const val TITLE = "Tonight's sky"

    fun compose(today: SkyToday, tomorrow: SkyToday?, zone: ZoneId, nowUtcMs: Long = System.currentTimeMillis()): String? {
        val lines = ArrayList<String>()
        lines += showerLines(today, zone)
        lines += eclipseLines(today, zone, nowUtcMs)
        lines += pairingLines(today, tomorrow)
        supermoonLine(today, zone)?.let { lines += it }
        return if (lines.isEmpty()) null else lines.joinToString(" ")
    }

    /** "9:14 pm", or "11 pm" on the hour. Always lower case am and pm. */
    fun clock(utcMs: Long, zone: ZoneId): String {
        val t = Instant.ofEpochMilli(utcMs).atZone(zone).toLocalTime()
        val h = if (t.hour % 12 == 0) 12 else t.hour % 12
        val suffix = if (t.hour < 12) "am" else "pm"
        return if (t.minute == 0) "$h $suffix" else "$h:${t.minute.toString().padStart(2, '0')} $suffix"
    }

    internal fun showerLines(today: SkyToday, zone: ZoneId): List<String> {
        val oneAm = WorldMath.atLocal(today.date.plusDays(1), 1, zone)
        val fiveAm = WorldMath.atLocal(today.date.plusDays(1), 5, zone)
        return today.showers.filter { it.peakDate == today.date && it.visibleHere != false }.map { s ->
            val name = s.shower.name
            val set = s.moonSetUtcMs
            when {
                set != null && set <= oneAm -> "$name peak tonight, best after 1 am. The moon sets at ${clock(set, zone)}, so skies will be dark."
                s.moonIllumination < 0.25 -> "$name peak tonight, best after 1 am. The moon is a thin sliver, so skies will be dark."
                set != null && set < fiveAm -> "$name peak tonight, best after ${clock(set, zone)}, once the moon sets."
                else -> "$name peak tonight, best after 1 am, though the moon will hide the fainter ones."
            }
        }
    }

    /** Eclipses seen from here in the next three evenings. Penumbral ones are too faint to mention. */
    internal fun eclipseLines(today: SkyToday, zone: ZoneId, nowUtcMs: Long): List<String> =
        today.eclipses.filter { it.visibleHere == true && it.kind != "penumbral" && it.endUtcMs > nowUtcMs }.mapNotNull { e ->
            val local = Instant.ofEpochMilli(e.startUtcMs).atZone(zone)
            val night = local.hour < 6 || local.hour >= 17
            val day = if (local.hour < 6) local.toLocalDate().minusDays(1) else local.toLocalDate()
            val ahead = ChronoUnit.DAYS.between(today.date, day)
            if (ahead !in 0L..2L) return@mapNotNull null
            val what = "${e.kind.replaceFirstChar { it.uppercase() }} ${if (e.solar) "solar" else "lunar"} eclipse"
            if (e.startUtcMs <= nowUtcMs) return@mapNotNull "$what under way now, from where you are."
            val whenWord = when (ahead) {
                0L -> if (night) "tonight" else "today"
                1L -> if (night) "tomorrow night" else "tomorrow"
                else -> day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
            }
            "$what $whenWord, starts ${clock(e.startUtcMs, zone)} from where you are."
        }

    /** Close pairs at dusk tonight and before dawn tomorrow, at most two. */
    internal fun pairingLines(today: SkyToday, tomorrow: SkyToday?): List<String> {
        val dusk = today.pairings.filter { it.window == SkyWindow.EVENING && it.separationDeg <= WorldMath.CLOSE_PAIRING_DEG }.map { it to "at dusk" }
        val dawn = tomorrow?.pairings.orEmpty().filter { it.window == SkyWindow.MORNING && it.separationDeg <= WorldMath.CLOSE_PAIRING_DEG }.map { it to "before dawn" }
        return (dusk + dawn)
            .distinctBy { (p, w) -> listOf(p.a, p.b).sorted() + w }
            .take(2)
            .map { (p, w) -> "${WorldMath.pairName(p).replaceFirstChar { it.uppercase() }} close together $w." }
    }

    /** A supermoon tonight or tomorrow night. The night of a full moon is the evening nearest the instant. */
    internal fun supermoonLine(today: SkyToday, zone: ZoneId): String? {
        if (!today.moon.nextFullIsSupermoon) return null
        val night = Instant.ofEpochMilli(today.moon.nextFullUtcMs).atZone(zone).minusHours(10).toLocalDate()
        return when (night) {
            today.date -> "Supermoon tonight."
            today.date.plusDays(1) -> "Supermoon tomorrow night."
            else -> null
        }
    }
}
