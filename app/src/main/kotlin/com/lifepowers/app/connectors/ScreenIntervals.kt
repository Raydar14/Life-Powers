package com.lifepowers.app.connectors

import java.time.Duration

/** A foreground or screen event, already stripped of Android types so the merger is testable. */
data class ScreenEvent(val timeMs: Long, val packageName: String, val type: Type) {
    enum class Type { FOREGROUND, BACKGROUND, SCREEN_OFF }
}

data class Interval(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

data class ScreenDay(val totalMin: Double, val after21Min: Double, val lastUseUtcMs: Long?)

/** Pure functions that turn usage events into screen-on-with-app intervals and day summaries. */
object ScreenIntervals {
    private val DAY_MS = Duration.ofHours(24).toMillis()
    private val H21_MS = Duration.ofHours(21).toMillis()
    private val H28_MS = Duration.ofHours(28).toMillis()

    /**
     * Rebuilds per package foreground intervals. A package is in the foreground from its first
     * FOREGROUND event until its matching BACKGROUND event; nested resumes within one package are
     * counted so activity transitions do not close the interval early. SCREEN_OFF closes
     * everything. Anything still open is closed at [rangeEndMs].
     */
    fun reconstruct(events: List<ScreenEvent>, rangeEndMs: Long): List<Interval> {
        val open = HashMap<String, Long>()
        val depth = HashMap<String, Int>()
        val out = ArrayList<Interval>()
        for (e in events.sortedBy { it.timeMs }) {
            when (e.type) {
                ScreenEvent.Type.FOREGROUND -> {
                    val d = depth[e.packageName] ?: 0
                    if (d == 0) open[e.packageName] = e.timeMs
                    depth[e.packageName] = d + 1
                }
                ScreenEvent.Type.BACKGROUND -> {
                    val d = (depth[e.packageName] ?: 0) - 1
                    if (d <= 0) {
                        depth.remove(e.packageName)
                        open.remove(e.packageName)?.let { start -> if (e.timeMs > start) out += Interval(start, e.timeMs) }
                    } else depth[e.packageName] = d
                }
                ScreenEvent.Type.SCREEN_OFF -> {
                    open.forEach { (_, start) -> if (e.timeMs > start) out += Interval(start, e.timeMs) }
                    open.clear()
                    depth.clear()
                }
            }
        }
        open.forEach { (_, start) -> if (rangeEndMs > start) out += Interval(start, rangeEndMs) }
        return merge(out)
    }

    /** Unions overlapping or touching intervals into a sorted disjoint list. */
    fun merge(intervals: List<Interval>): List<Interval> {
        if (intervals.isEmpty()) return emptyList()
        val sorted = intervals.filter { it.endMs > it.startMs }.sortedBy { it.startMs }
        val out = ArrayList<Interval>()
        var cur: Interval? = null
        for (i in sorted) {
            val c = cur
            cur = if (c == null) i
            else if (i.startMs <= c.endMs) Interval(c.startMs, maxOf(c.endMs, i.endMs))
            else { out += c; i }
        }
        cur?.let { out += it }
        return out
    }

    /**
     * Minutes within the local day starting at [dayStartMs], minutes after 21:00 of that day, and
     * the end of the last interval that begins before 04:00 the next morning.
     */
    fun summarize(intervals: List<Interval>, dayStartMs: Long): ScreenDay {
        val dayEnd = dayStartMs + DAY_MS
        val lateStart = dayStartMs + H21_MS
        val cutoff = dayStartMs + H28_MS
        val merged = merge(intervals)
        val total = merged.sumOf { overlapMs(it, dayStartMs, dayEnd) }
        val late = merged.sumOf { overlapMs(it, lateStart, dayEnd) }
        val lastUse = merged.filter { it.startMs in dayStartMs until cutoff }
            .maxOfOrNull { minOf(it.endMs, cutoff) }
        return ScreenDay(total / 60000.0, late / 60000.0, lastUse)
    }

    private fun overlapMs(i: Interval, from: Long, to: Long): Long =
        (minOf(i.endMs, to) - maxOf(i.startMs, from)).coerceAtLeast(0L)
}
