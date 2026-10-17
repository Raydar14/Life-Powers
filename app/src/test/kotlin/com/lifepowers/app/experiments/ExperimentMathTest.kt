package com.lifepowers.app.experiments

import com.lifepowers.app.data.personal.ExperimentDayEntity
import com.lifepowers.app.domain.ExperimentRecord
import com.lifepowers.app.ui.screens.patterns.PatternExperiments
import com.lifepowers.core.Condition
import com.lifepowers.core.ExperimentSpec
import com.lifepowers.core.Experiments
import com.lifepowers.core.Insight
import com.lifepowers.core.InsightKind
import com.lifepowers.core.Metrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class ExperimentMathTest {

    private val start = LocalDate.of(2026, 10, 1)

    private val spec = ExperimentSpec(
        id = 5,
        behavior = "No screens after 9 pm",
        outcomeMetric = Metrics.SLEEP_DEEP_MIN,
        startedOn = start,
        blockDays = 3,
        targetDays = 14,
        outcomeLag = 1,
    )

    private val record = ExperimentRecord(spec = spec, name = "No screens after 9 pm", reminderHour = 8)

    private fun firstDay(condition: Condition): LocalDate =
        Experiments.schedule(spec).first { it.second == condition }.first

    @Test
    fun entityRoundTripKeepsEverything() {
        val ended = record.copy(endedOn = start.plusDays(10))
        assertEquals(ended, ExperimentMath.toRecord(ExperimentMath.toEntity(ended)))
        assertEquals(0L, ExperimentMath.toEntity(record, id = 0L).id)
        assertEquals("2026-10-01", ExperimentMath.toEntity(record).startedOn)
    }

    @Test
    fun adherenceSkipsBadDates() {
        val days = listOf(
            ExperimentDayEntity(5, "2026-10-02", true),
            ExperimentDayEntity(5, "2026-10-03", false),
            ExperimentDayEntity(5, "not a date", false),
        )
        assertEquals(mapOf(start.plusDays(1) to true, start.plusDays(2) to false), ExperimentMath.adherence(days))
    }

    @Test
    fun dayIndexCountsFromOneAndStopsAtTarget() {
        assertEquals(0, ExperimentMath.dayIndex(spec, start.minusDays(1)))
        assertEquals(1, ExperimentMath.dayIndex(spec, start))
        assertEquals(14, ExperimentMath.dayIndex(spec, start.plusDays(13)))
        assertEquals(14, ExperimentMath.dayIndex(spec, start.plusDays(40)))
    }

    @Test
    fun outcomeWindowCoversScheduleAndLagButNotTheFuture() {
        val (daysMid, endMid) = ExperimentMath.outcomeWindow(spec, null, start.plusDays(5))
        assertEquals(start.plusDays(5), endMid)
        assertTrue(daysMid >= 6)

        val (daysLate, endLate) = ExperimentMath.outcomeWindow(spec, null, start.plusDays(60))
        // Last scheduled day plus one day of lag.
        assertEquals(start.plusDays(14), endLate)
        assertTrue(endLate.minusDays(daysLate - 1L) <= start)

        val (_, endEnded) = ExperimentMath.outcomeWindow(spec, start.plusDays(4), start.plusDays(60))
        assertEquals(start.plusDays(5), endEnded)
    }

    @Test
    fun onDayNoticeAtReminderHourOnce() {
        val on = firstDay(Condition.ON)
        val notice = ExperimentMath.noticeFor(record, on, 8, notifiedToday = false, completeNotified = false)
        assertEquals(ExperimentNotice.Today(Condition.ON, "Today is an on day: no screens after 9 pm."), notice)
        // The hour after still counts, in case the hourly run drifted.
        assertTrue(ExperimentMath.noticeFor(record, on, 9, notifiedToday = false, completeNotified = false) is ExperimentNotice.Today)
        assertNull(ExperimentMath.noticeFor(record, on, 8, notifiedToday = true, completeNotified = false))
        assertNull(ExperimentMath.noticeFor(record, on, 7, notifiedToday = false, completeNotified = false))
        assertNull(ExperimentMath.noticeFor(record, on, 10, notifiedToday = false, completeNotified = false))
    }

    @Test
    fun offDayNoticeSaysLiveAsUsual() {
        val off = firstDay(Condition.OFF)
        val notice = ExperimentMath.noticeFor(record, off, 8, notifiedToday = false, completeNotified = false)
        assertEquals(ExperimentNotice.Today(Condition.OFF, ExperimentMath.OFF_DAY_TEXT), notice)
    }

    @Test
    fun noDayNoticeWithoutReminderOrAfterEnding() {
        val on = firstDay(Condition.ON)
        assertNull(ExperimentMath.noticeFor(record.copy(reminderHour = null), on, 8, false, false))
        assertNull(ExperimentMath.noticeFor(record.copy(endedOn = on), on, 8, false, false))
        assertNull(ExperimentMath.noticeFor(record, start.minusDays(1), 8, false, false))
    }

    @Test
    fun completeNoticeOnceAfterTheLastOutcomeLands() {
        // Last scheduled day is the 14th; with a lag of one its outcome lands the day after.
        val lastOutcome = start.plusDays(14)
        assertNull(ExperimentMath.noticeFor(record, lastOutcome, 8, false, false))
        val after = lastOutcome.plusDays(1)
        assertEquals(ExperimentNotice.Complete, ExperimentMath.noticeFor(record, after, 8, false, false))
        assertEquals(ExperimentNotice.Complete, ExperimentMath.noticeFor(record, after, 15, true, false))
        assertNull(ExperimentMath.noticeFor(record, after, 7, false, false))
        assertNull(ExperimentMath.noticeFor(record, after, 8, false, true))
        // Even with daily reminders off, completion is said once in the daytime.
        assertEquals(ExperimentNotice.Complete, ExperimentMath.noticeFor(record.copy(reminderHour = null), after, 10, false, false))
        assertNull(ExperimentMath.noticeFor(record.copy(endedOn = lastOutcome), after, 10, false, false))
    }

    @Test
    fun lettersAndAcronyms() {
        assertEquals("no alcohol", ExperimentMath.lowerFirst("No alcohol"))
        assertEquals("HRV breathing", ExperimentMath.lowerFirst("HRV breathing"))
        assertEquals("A walk", ExperimentMath.upperFirst("a walk"))
        assertEquals("", ExperimentMath.lowerFirst(""))
        assertEquals("no screens after 9 pm", ExperimentMath.forSentence(spec.copy(behavior = "No screens after 9 pm.")).behavior)
    }

    @Test
    fun valuesReadInTheOutcomesUnits() {
        val us = Locale.US
        assertEquals("71 min", ExperimentMath.formatValue(Metrics.SLEEP_DEEP_MIN, 71.4, us))
        assertEquals("8.5 min", ExperimentMath.formatValue(Metrics.SLEEP_ONSET_LATENCY_MIN, 8.46, us))
        assertEquals("43 ms", ExperimentMath.formatValue(Metrics.HRV_NIGHT_AVG, 42.6, us))
        assertEquals("96 mg/dL", ExperimentMath.formatValue(Metrics.GLUCOSE_FASTING, 96.2, us))
        assertEquals("82", ExperimentMath.formatValue(Metrics.SCORE_READINESS, 81.7, us))
        assertEquals("+0.40", ExperimentMath.formatValue(Metrics.VOICE_ENERGY_INDEX, 0.4, us))
        assertEquals("-0.12", ExperimentMath.formatValue(Metrics.FEEL_EASE, -0.123, us))
        assertEquals("--", ExperimentMath.formatValue(Metrics.SLEEP_DEEP_MIN, null, us))
    }

    @Test
    fun overnightOutcomesShowUpTheNextDay() {
        assertEquals(1, ExperimentMath.defaultLag(Metrics.SLEEP_DEEP_MIN))
        assertEquals(1, ExperimentMath.defaultLag(Metrics.HRV_NIGHT_AVG))
        assertEquals(1, ExperimentMath.defaultLag(Metrics.GLUCOSE_OVERNIGHT_MEAN))
        assertEquals(0, ExperimentMath.defaultLag(Metrics.SCORE_READINESS))
        assertEquals(0, ExperimentMath.defaultLag(Metrics.FEEL_EASE))
        assertEquals(0, ExperimentMath.defaultLag(Metrics.BREATH_MINUTES))
    }

    @Test
    fun previewFollowsTheSchedule() {
        val preview = ExperimentMath.preview(spec, 14)
        assertEquals(14, preview.size)
        assertEquals(Experiments.schedule(spec).map { it.second }, preview)
        // Three day blocks: the first three days share a condition.
        assertEquals(1, preview.take(3).toSet().size)
    }

    @Test
    fun noticeIdsStayInTheirRange() {
        assertEquals(30_005, ExperimentMath.noticeId(5))
        assertTrue(ExperimentMath.noticeId(123_456) in 30_000 until 40_000)
    }

    private fun insight(input: String, good: Boolean?) = Insight(
        kind = InsightKind.TRENDS_WITH,
        input = input,
        outcome = Metrics.SLEEP_DEEP_MIN,
        lag = 1,
        n = 40,
        effect = -0.5,
        pValue = 0.01,
        qValue = 0.02,
        preliminary = false,
        good = good,
    )

    @Test
    fun findingsBecomeBehaviorPhrases() {
        assertEquals("Evening walk", PatternExperiments.behavior(insight("habit:Evening walk", true)))
        assertEquals("no alcohol", PatternExperiments.behavior(insight("alcohol", false)))
        assertEquals("less phone time after 9 pm", PatternExperiments.behavior(insight(Metrics.SCREEN_AFTER_21_MIN, false)))
        assertEquals("more steps", PatternExperiments.behavior(insight(Metrics.ACTIVITY_STEPS, true)))
    }
}
