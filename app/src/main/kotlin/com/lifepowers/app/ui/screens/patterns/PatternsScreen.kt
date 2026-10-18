package com.lifepowers.app.ui.screens.patterns

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepowers.app.ui.components.BodyLine
import com.lifepowers.app.ui.components.CloudSpec
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.DashedCard
import com.lifepowers.app.ui.components.Fmt
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.ScreenHeader
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.components.WrapRow
import com.lifepowers.app.ui.components.dashedBorder
import com.lifepowers.app.ui.screens.assistant.AssistantNarrativeCard
import com.lifepowers.app.ui.screens.experiments.ExperimentRoutes
import com.lifepowers.app.ui.screens.goals.GoalRoutes
import com.lifepowers.app.ui.theme.DeepLavenderLine
import com.lifepowers.app.ui.theme.DeepPink
import com.lifepowers.app.ui.theme.HairLine
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.Lavender
import com.lifepowers.app.ui.theme.LightPink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.app.ui.theme.White
import com.lifepowers.core.Insight
import com.lifepowers.core.Patterns
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val PatternClouds = listOf(
    CloudSpec(Lavender, 230.dp, x = (-80).dp, y = (-70).dp, alpha = 0.4f, fromEnd = true),
)

/* Findings are not forecasts, so the tints stay soft: a light wash of the palette over white. */
private val GoodTint = lerp(White, LightPink, 0.35f)
private val NotGoodTint = lerp(White, Lavender, 0.35f)

@Composable
fun PatternsScreen(onNavigate: (String) -> Unit, viewModel: PatternsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Clouds(PatternClouds)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(title = "Patterns", titleSize = 28.sp) {
                PillButton(
                    text = if (state.looking) "Looking" else "Look again",
                    onClick = { viewModel.recompute() },
                    enabled = !state.looking,
                    fontSize = 12.sp,
                    shadowAlpha = 0.1f,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MutedLine(text = coverageLine(state.daysWithData), fontSize = 13.sp)
                val computed = state.computedUtcMs
                if (computed != null) MutedLine(text = lastLooked(computed))
                TextLink(
                    text = "Your experiments",
                    onClick = { onNavigate(ExperimentRoutes.LIST) },
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            when {
                !state.loaded -> Unit
                !state.enoughDays -> NotEnoughCard(state.daysWithData)
                state.insights.isEmpty() -> QuietCard()
                else -> {
                    val sentences = state.insights.map { insight -> Patterns.sentence(insight) { key -> PatternLabels.label(key) } }
                    AssistantNarrativeCard(findings = sentences, goals = emptyList())
                    state.insights.forEachIndexed { i, insight ->
                        InsightCard(
                            insight = insight,
                            sentence = sentences[i],
                            onGoal = { onNavigate(GoalRoutes.edit(0, PatternLabels.goalSuggestion(insight))) },
                            onExperiment = {
                                onNavigate(ExperimentRoutes.new(behavior = PatternExperiments.behavior(insight), outcome = insight.outcome))
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun coverageLine(days: Int): String = when (days) {
    0 -> "No days of your data yet. Measured against your own usual, strongest first."
    1 -> "Based on 1 day of your data. Measured against your own usual, strongest first."
    else -> "Based on $days days of your data. Measured against your own usual, strongest first."
}

private fun lastLooked(utcMs: Long): String {
    val at = Instant.ofEpochMilli(utcMs).atZone(ZoneId.systemDefault())
    return if (at.toLocalDate() == LocalDate.now()) {
        "Last looked at ${Fmt.timeOfDay(at.toLocalTime())}"
    } else {
        "Last looked on ${Fmt.shortDate(at.toLocalDate())}"
    }
}

@Composable
private fun NotEnoughCard(days: Int) {
    val need = Patterns.MIN_N
    DashedCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 28.dp,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        gap = 8.dp,
    ) {
        Text(text = "Patterns need about two weeks", color = Ink, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black)
        BodyLine(
            text = "Each finding compares your days with each other, so nothing can stand out until there are about " +
                "$need days with data. Keep syncing and checking in and this fills by itself.",
        )
        SoftBar(fraction = days.toFloat() / need, modifier = Modifier.padding(top = 4.dp))
        MutedLine(text = "${days.coerceAtMost(need)} of $need days")
    }
}

@Composable
private fun SoftBar(fraction: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(shape)
            .background(White),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(shape)
                .background(LightPink),
        )
    }
}

@Composable
private fun QuietCard() {
    SoftCard(modifier = Modifier.fillMaxWidth(), shadowAlpha = 0.1f, elevation = 12.dp, gap = 6.dp) {
        Text(text = "Nothing stands out yet", color = Ink, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black)
        BodyLine(
            text = "None of the pairs cleared the bar for size and surety. That is a real answer, not a gap. " +
                "Look again after a few more weeks.",
        )
    }
}

@Composable
private fun InsightCard(insight: Insight, sentence: String, onGoal: () -> Unit, onExperiment: () -> Unit) {
    val tint = when (insight.good) {
        true -> GoodTint
        false -> NotGoodTint
        null -> White
    }
    val shadow = when (insight.good) {
        true -> HotPink
        false -> DeepLavenderLine
        null -> Ink
    }
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        color = tint,
        shadowColor = shadow,
        shadowAlpha = 0.12f,
        elevation = 12.dp,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        gap = 10.dp,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            StrengthPill(insight.strength)
            if (insight.preliminary) EarlyPill()
            Text(text = "${insight.n} days", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text(
            text = sentence,
            color = Ink, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold,
        )
        WrapRow(horizontalGap = 4.dp, verticalGap = 0.dp) {
            TextLink(text = "Turn into a goal", onClick = onGoal)
            TextLink(text = "Test it as an experiment", onClick = onExperiment)
        }
    }
}

@Composable
private fun StrengthPill(strength: String) {
    val (bg, fg) = when (strength) {
        "strong" -> DeepPink to White
        "clear" -> LightPink to Ink
        else -> HairLine to Ink
    }
    SmallPill(text = strength, background = bg, textColor = fg)
}

@Composable
private fun SmallPill(text: String, background: Color, textColor: Color) {
    Text(
        text = text,
        color = textColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun EarlyPill() {
    Text(
        text = "early",
        color = Muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .dashedBorder(cornerRadius = 999.dp)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}
