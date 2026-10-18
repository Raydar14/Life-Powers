package com.lifepowers.app.ui.screens.experiments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepowers.app.domain.ExperimentView
import com.lifepowers.app.experiments.ExperimentMath
import com.lifepowers.app.ui.components.BodyLine
import com.lifepowers.app.ui.components.CardTitle
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.Fmt
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.ScreenHeader
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.screens.patterns.PatternLabels
import com.lifepowers.app.ui.theme.Ground
import com.lifepowers.app.ui.theme.HairLine
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.LightPink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.app.ui.theme.Plum
import com.lifepowers.core.Condition
import com.lifepowers.core.Experiments
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private enum class Confirm { END, DELETE }

@Composable
fun ExperimentScreen(
    id: Long,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ExperimentViewModel = hiltViewModel(),
) {
    LaunchedEffect(id) { viewModel.setExperiment(id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val view = state.view

    Box(modifier = Modifier.fillMaxSize()) {
        Clouds(ExperimentClouds)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                !state.loaded -> {
                    ScreenHeader(title = "Experiment", titleSize = 28.sp) { TextLink(text = "Back", onClick = onBack) }
                    MutedLine(text = "Opening", fontSize = 13.sp)
                }
                view == null -> {
                    ScreenHeader(title = "Experiment", titleSize = 28.sp) { TextLink(text = "Back", onClick = onBack) }
                    BodyLine(text = "This experiment is no longer here.")
                    TextLink(text = "All experiments", onClick = { onNavigate(ExperimentRoutes.LIST) })
                }
                else -> Body(
                    view = view,
                    today = state.today,
                    onBack = onBack,
                    onAdhered = viewModel::setAdhered,
                    onEnd = { confirm = Confirm.END },
                    onDelete = { confirm = Confirm.DELETE },
                )
            }
        }
    }

    when (confirm) {
        Confirm.END -> ExperimentConfirm(
            title = "End this experiment?",
            body = "Reminders stop and the result is kept as it stands today. You can still look at it here.",
            confirmText = "End it",
            onConfirm = { confirm = null; viewModel.end() },
            onDismiss = { confirm = null },
        )
        Confirm.DELETE -> ExperimentConfirm(
            title = "Delete this experiment?",
            body = "The schedule, your answers, and the result are removed for good. Your sleep and other data stay.",
            confirmText = "Delete",
            onConfirm = { confirm = null; viewModel.delete() },
            onDismiss = { confirm = null },
        )
        null -> Unit
    }
}

@Composable
private fun Body(
    view: ExperimentView,
    today: LocalDate,
    onBack: () -> Unit,
    onAdhered: (LocalDate, Boolean?) -> Unit,
    onEnd: () -> Unit,
    onDelete: () -> Unit,
) {
    val record = view.record
    val spec = record.spec
    val running = record.endedOn == null
    val outcome = PatternLabels.label(spec.outcomeMetric)
    val ended = record.endedOn
    val subline = when {
        ended != null -> "Ended ${Fmt.shortDate(ended)}, after ${view.dayIndex} of ${spec.targetDays} days"
        view.today == null -> "All ${spec.targetDays} days are in"
        else -> "Day ${view.dayIndex} of ${spec.targetDays}"
    }

    ScreenHeader(title = record.name, titleSize = 28.sp, subline = subline) {
        TextLink(text = "Back", onClick = onBack)
    }
    MutedLine(text = aboutLine(outcome, spec.blockDays, spec.outcomeLag), fontSize = 13.sp)

    val todayCondition = view.today
    if (running && todayCondition != null) {
        SoftCard(
            modifier = Modifier.fillMaxWidth(),
            shadowColor = if (todayCondition == Condition.ON) HotPink else Ink,
            shadowAlpha = 0.14f,
            elevation = 14.dp,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            gap = 10.dp,
        ) {
            ConditionPill(condition = todayCondition, large = true)
            Text(
                text = if (todayCondition == Condition.ON) ExperimentMath.upperFirst(spec.behavior) else "Live as usual",
                color = Ink, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black,
            )
            Text(text = "Did you follow today's plan?", color = Ink, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.ExtraBold)
            AdherenceChoice(value = view.adhered[today], noText = "Not today", onSet = { onAdhered(today, it) })
        }
    }

    ScheduleCard(view = view, today = today, running = running, onAdhered = onAdhered)
    ResultCard(view = view, outcome = outcome)

    if (running) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillButton(text = "End experiment", onClick = onEnd, modifier = Modifier.weight(1f), shadowAlpha = 0.1f)
            TextLink(text = "Delete", onClick = onDelete, fontSize = 13.sp)
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextLink(text = "Delete", onClick = onDelete, fontSize = 13.sp)
        }
    }
}

private fun aboutLine(outcome: String, blockDays: Int, lag: Int): String {
    val scoredBy = if (lag > 0) "Each day is scored by the next day's $outcome." else "Each day is scored by that day's $outcome."
    return "Watching $outcome. Blocks of $blockDays days, on and off. $scoredBy"
}

@Composable
private fun ScheduleCard(view: ExperimentView, today: LocalDate, running: Boolean, onAdhered: (LocalDate, Boolean?) -> Unit) {
    val spec = view.record.spec
    val schedule = Experiments.schedule(spec)
    // After an experiment ends, its remaining days are not coming, but they still read as pale.
    val lastLived = view.record.endedOn?.let { minOf(it, today) } ?: today
    val looks = schedule.map { (date, condition) -> dayLook(condition, date, lastLived, view.adhered[date]) }
    val todayIndex = ChronoUnit.DAYS.between(spec.startedOn, today).toInt().takeIf { running && it in schedule.indices }
    val toggle: (Int) -> Unit = { index ->
        val date = schedule[index].first
        if (!date.isAfter(today)) onAdhered(date, if (view.adhered[date] == false) null else false)
    }
    SoftCard(modifier = Modifier.fillMaxWidth(), shadowAlpha = 0.08f, gap = 12.dp) {
        CardTitle("The schedule")
        DayGrid(
            looks = looks,
            ringedIndex = todayIndex,
            onTap = if (running) toggle else null,
        )
        Legend(
            listOf(
                DayLook.ON to "on day",
                DayLook.OFF to "off day",
                DayLook.MISSED to "not followed",
                DayLook.ON_LATER to "still to come",
            ),
        )
        if (running) MutedLine(text = "Tap a day you did not follow and it is left out of the score. Tap again to put it back.")
    }
}

@Composable
private fun ResultCard(view: ExperimentView, outcome: String) {
    val r = view.result
    val metric = view.record.spec.outcomeMetric
    val days = r.nOn + r.nOff
    SoftCard(modifier = Modifier.fillMaxWidth(), shadowAlpha = 0.08f, gap = 10.dp) {
        CardTitle(if (r.ready) "The result" else "Early read, $days ${if (days == 1) "day" else "days"}")
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MeanTile(
                title = "on days",
                value = ExperimentMath.formatValue(metric, r.meanOn),
                caption = outcome,
                background = LightPink,
                textTone = Plum,
                bordered = false,
                modifier = Modifier.weight(1f),
            )
            MeanTile(
                title = "off days",
                value = ExperimentMath.formatValue(metric, r.meanOff),
                caption = outcome,
                background = Ground,
                textTone = Muted,
                bordered = true,
                modifier = Modifier.weight(1f),
            )
        }
        BodyLine(text = view.sentence)
        MutedLine(
            text = "${r.nOn} on ${if (r.nOn == 1) "day" else "days"} and ${r.nOff} off ${if (r.nOff == 1) "day" else "days"} " +
                "with a reading. Days marked not followed are left out.",
        )
        if (!r.ready) {
            MutedLine(text = "This is an early read. It is scored properly once there are ${Experiments.MIN_PER_ARM} of each kind of day.")
        }
    }
}

@Composable
private fun MeanTile(
    title: String,
    value: String,
    caption: String,
    background: Color,
    textTone: Color,
    bordered: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    var m = modifier.fillMaxHeight().clip(shape).background(background)
    if (bordered) m = m.border(2.dp, HairLine, shape)
    Column(modifier = m.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, color = textTone, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        Text(text = value, color = Ink, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(text = caption, color = textTone, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
