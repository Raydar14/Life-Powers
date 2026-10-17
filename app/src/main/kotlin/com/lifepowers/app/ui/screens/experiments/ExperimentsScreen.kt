package com.lifepowers.app.ui.screens.experiments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepowers.app.domain.ExperimentView
import com.lifepowers.app.experiments.ExperimentMath
import com.lifepowers.app.ui.components.BodyLine
import com.lifepowers.app.ui.components.CloudSpec
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.DashedCard
import com.lifepowers.app.ui.components.Fmt
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.PrimaryButton
import com.lifepowers.app.ui.components.ScreenHeader
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.app.ui.theme.SoftBlue
import com.lifepowers.core.Condition
import com.lifepowers.core.Experiments
import java.time.LocalDate

internal val ExperimentClouds = listOf(
    CloudSpec(HotPink, 240.dp, x = (-70).dp, y = (-80).dp, alpha = 0.18f, fromEnd = true),
    CloudSpec(SoftBlue, 200.dp, x = (-110).dp, y = 260.dp, alpha = 0.3f),
)

@Composable
fun ExperimentsScreen(
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ExperimentsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
            ScreenHeader(title = "Experiments", titleSize = 28.sp) {
                TextLink(text = "Back", onClick = onBack)
            }

            when {
                !state.loaded -> MutedLine(text = "Opening", fontSize = 13.sp)
                state.empty -> EmptyCard()
                else -> {
                    for (view in state.running) {
                        RunningCard(
                            view = view,
                            today = state.today,
                            onOpen = { onNavigate(ExperimentRoutes.detail(view.record.spec.id)) },
                            onAdhered = { date, value -> viewModel.setAdhered(view.record.spec.id, date, value) },
                        )
                    }
                    if (state.finished.isNotEmpty()) {
                        Text(
                            text = "Finished",
                            color = Ink, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )
                        for (view in state.finished) {
                            FinishedCard(view = view, onOpen = { onNavigate(ExperimentRoutes.detail(view.record.spec.id)) })
                        }
                    }
                }
            }

            if (state.loaded) {
                PrimaryButton(
                    text = "Start an experiment",
                    onClick = { onNavigate(ExperimentRoutes.new()) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyCard() {
    DashedCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 28.dp,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        gap = 8.dp,
    ) {
        Text(text = "Try one thing, watch one thing", color = Ink, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black)
        BodyLine(text = "The app splits the next few weeks into alternating blocks of on days and off days, and reminds you each day which one it is.")
        BodyLine(text = "When enough days are in, it scores the difference and tells you in plain words how sure it is.")
    }
}

@Composable
private fun RunningCard(
    view: ExperimentView,
    today: LocalDate,
    onOpen: () -> Unit,
    onAdhered: (LocalDate, Boolean?) -> Unit,
) {
    val spec = view.record.spec
    val yesterday = today.minusDays(1)
    val yesterdayCondition = Experiments.condition(spec, yesterday)
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        shadowColor = if (view.today == Condition.ON) HotPink else Ink,
        shadowAlpha = 0.12f,
        elevation = 12.dp,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        gap = 10.dp,
        onClick = onOpen,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ConditionPill(condition = view.today, large = true)
            Text(text = "Day ${view.dayIndex} of ${spec.targetDays}", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = view.record.name, color = Ink, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black)
            MutedLine(text = "Watching ${outcomeName(spec.outcomeMetric).let { ExperimentMath.lowerFirst(it) }}", fontSize = 13.sp)
        }

        val todayCondition = view.today
        if (todayCondition != null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "Did you follow today's plan?", color = Ink, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.ExtraBold)
                MutedLine(text = planLine(spec.behavior, todayCondition))
                AdherenceChoice(value = view.adhered[today], noText = "Not today", onSet = { onAdhered(today, it) })
            }
        } else {
            BodyLine(text = "All ${spec.targetDays} days are in. Open it to see what happened, then end it when you are ready.")
        }

        if (yesterdayCondition != null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (yesterdayCondition == Condition.ON) "Yesterday was an on day" else "Yesterday was an off day",
                    color = Muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                )
                AdherenceChoice(value = view.adhered[yesterday], noText = "Not that day", onSet = { onAdhered(yesterday, it) })
            }
        }

        BodyLine(text = view.sentence)
    }
}

/** What today's plan asks: the behavior on an on day, nothing different on an off day. */
internal fun planLine(behavior: String, condition: Condition): String = when (condition) {
    Condition.ON -> "Today: ${ExperimentMath.lowerFirst(behavior.trim().trimEnd('.'))}."
    Condition.OFF -> "Today: live as usual."
}

@Composable
private fun FinishedCard(view: ExperimentView, onOpen: () -> Unit) {
    val ended = view.record.endedOn
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 24.dp,
        shadowAlpha = 0.08f,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        gap = 4.dp,
        onClick = onOpen,
    ) {
        Text(text = view.record.name, color = Ink, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Black)
        val watched = ExperimentMath.lowerFirst(outcomeName(view.record.spec.outcomeMetric))
        MutedLine(text = if (ended != null) "Ended ${Fmt.shortDate(ended)}. Watched $watched." else "Watched $watched.")
        BodyLine(text = view.sentence)
    }
}
