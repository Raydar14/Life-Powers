package com.lifepowers.app.ui.screens.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepowers.app.domain.GoalView
import com.lifepowers.app.ui.components.BodyLine
import com.lifepowers.app.ui.components.CloudSpec
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.DashedCard
import com.lifepowers.app.ui.components.LightPinkChip
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.PrimaryButton
import com.lifepowers.app.ui.components.ScreenHeader
import com.lifepowers.app.ui.components.SectionLabel
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.WrapRow
import com.lifepowers.app.ui.components.softShadow
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.Lavender
import com.lifepowers.app.ui.theme.LightPink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.app.ui.theme.Plum
import com.lifepowers.app.ui.theme.White
import com.lifepowers.core.Cadence
import com.lifepowers.core.PeriodProgress
import com.lifepowers.core.PeriodState
import com.lifepowers.core.TargetKind
import java.time.LocalDate

private val GoalsClouds = listOf(
    CloudSpec(HotPink, 240.dp, x = (-80).dp, y = (-90).dp, alpha = 0.16f, fromEnd = true),
    CloudSpec(Lavender, 200.dp, x = (-110).dp, y = 300.dp, alpha = 0.22f),
)

private val Filters: List<Pair<String, Cadence?>> = listOf(
    "All" to null,
    "Daily" to Cadence.DAILY,
    "Weekly" to Cadence.WEEKLY,
    "Monthly" to Cadence.MONTHLY,
    "Yearly" to Cadence.YEARLY,
)

private val QuickStarts = listOf("Walk 3 times a week", "Meditate 10 minutes a day", "Phone down by 10 pm")

@Composable
fun GoalsScreen(onNavigate: (String) -> Unit, viewModel: GoalsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Clouds(GoalsClouds)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 190.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ScreenHeader(title = "Goals", titleSize = 28.sp)
                MutedLine(text = summary(state), fontSize = 13.sp)
            }

            if (state.all.isNotEmpty()) {
                SegmentPills(
                    options = Filters.map { it.first },
                    selected = Filters.indexOfFirst { it.second == state.filter }.coerceAtLeast(0),
                    onSelect = { viewModel.setFilter(Filters[it].second) },
                )
            }

            when {
                !state.loaded -> Unit
                state.all.isEmpty() -> EmptyGoals(onNavigate)
                state.shown.isEmpty() -> {
                    val word = Filters.firstOrNull { it.second == state.filter }?.first?.lowercase() ?: ""
                    DashedCard(modifier = Modifier.fillMaxWidth(), radius = 28.dp, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                        MutedLine(text = "No $word goals yet. The New goal button makes one.", fontSize = 13.sp)
                    }
                }
                else -> {
                    for (view in state.shown) {
                        GoalCard(
                            view = view,
                            today = state.today,
                            onOpen = { onNavigate(GoalRoutes.goal(view.goal.id)) },
                            onCheck = { viewModel.check(view.goal.id) },
                            onTimer = { onNavigate(GoalRoutes.timer(view.goal.id)) },
                        )
                    }
                }
            }
        }

        PrimaryButton(
            text = "New goal",
            onClick = { onNavigate(GoalRoutes.edit(0)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 92.dp),
        )
    }
}

private fun summary(state: GoalsUiState): String = when {
    !state.loaded -> " "
    state.all.isEmpty() -> "Small things, kept gently."
    else -> "${state.onTrack} of ${state.all.size} on track right now"
}

@Composable
private fun EmptyGoals(onNavigate: (String) -> Unit) {
    DashedCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 28.dp,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        gap = 8.dp,
    ) {
        Text(text = "Your first goal", color = Ink, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black)
        BodyLine(
            text = "Pick something small you would like to do more often. The app keeps count, reminds you when you ask, " +
                "and a missed day is simply pale, never a mark against you.",
        )
        SectionLabel(text = "Quick starts", modifier = Modifier.padding(top = 4.dp))
        WrapRow {
            for (name in QuickStarts) {
                LightPinkChip(text = name, onClick = { onNavigate(GoalRoutes.edit(0, name)) })
            }
        }
    }
}

@Composable
private fun GoalCard(view: GoalView, today: LocalDate, onOpen: () -> Unit, onCheck: () -> Unit, onTimer: () -> Unit) {
    val goal = view.goal
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        shadowAlpha = 0.1f,
        elevation = 12.dp,
        gap = 12.dp,
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = goal.name, color = Ink, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Black)
                MutedLine(text = GoalWords.subline(goal))
            }
            GoalAction(view = view, onCheck = onCheck, onTimer = onTimer)
        }
        PeriodDots(history = view.history, today = today)
        MutedLine(text = GoalWords.statusLine(view))
        val closing = view.closingLine
        if (closing != null) BodyLine(text = closing, color = Plum)
    }
}

@Composable
private fun GoalAction(view: GoalView, onCheck: () -> Unit, onTimer: () -> Unit) {
    val goal = view.goal
    val current = view.current
    when {
        goal.kind == TargetKind.MINUTES -> PillButton(
            text = "Start timer", onClick = onTimer, background = Ink, textColor = White,
            fontWeight = FontWeight.Black, shadowAlpha = 0.25f,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
        )
        goal.kind == TargetKind.CHECK && current.state == PeriodState.MET -> DoneMark()
        goal.kind == TargetKind.METRIC && goal.autoSource != null -> MetricCount(current)
        else -> PillButton(
            text = if (goal.kind == TargetKind.COUNT) "Add one" else "Check",
            onClick = onCheck, background = LightPink, shadowColor = HotPink, shadowAlpha = 0.25f,
            fontWeight = FontWeight.Black, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
        )
    }
}

/** The met state for a done or not goal: a filled hot pink circle that says so. */
@Composable
private fun DoneMark() {
    Box(
        modifier = Modifier
            .size(52.dp)
            .softShadow(RoundedCornerShape(999.dp), HotPink, 0.3f, 8.dp)
            .clip(CircleShape)
            .background(HotPink),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Done", color = White, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun MetricCount(current: PeriodProgress) {
    Column(horizontalAlignment = Alignment.End) {
        Text(text = GoalWords.amount(current.value), color = HotPink, fontSize = 24.sp, lineHeight = 26.sp, fontWeight = FontWeight.Black)
        Text(text = "of ${GoalWords.amount(current.target)}", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}
