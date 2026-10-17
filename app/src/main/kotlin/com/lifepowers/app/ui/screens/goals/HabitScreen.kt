package com.lifepowers.app.ui.screens.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepowers.app.domain.GoalView
import com.lifepowers.app.ui.components.BodyLine
import com.lifepowers.app.ui.components.CloudSpec
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.ScreenHeader
import com.lifepowers.app.ui.components.SectionLabel
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.Lavender
import com.lifepowers.app.ui.theme.LightPink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.app.ui.theme.Plum
import com.lifepowers.app.ui.theme.White
import com.lifepowers.core.PeriodState
import com.lifepowers.core.TargetKind
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val HabitClouds = listOf(
    CloudSpec(LightPink, 240.dp, x = (-80).dp, y = (-90).dp, alpha = 0.4f, fromEnd = true),
    CloudSpec(Lavender, 200.dp, x = (-110).dp, y = 380.dp, alpha = 0.25f),
)

@Composable
fun HabitScreen(
    goalId: Long,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: HabitViewModel = hiltViewModel(),
) {
    LaunchedEffect(goalId) { viewModel.setGoal(goalId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view = state.view

    Box(modifier = Modifier.fillMaxSize()) {
        Clouds(HabitClouds)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(title = view?.goal?.name ?: "Habit", titleSize = 28.sp) {
                Column(horizontalAlignment = Alignment.End) {
                    TextLink(text = "Back", onClick = onBack)
                    TextLink(text = "Edit", onClick = { onNavigate(GoalRoutes.edit(goalId)) })
                }
            }

            if (view == null) {
                MutedLine(
                    text = if (state.loaded) "This goal is archived or gone." else "Opening",
                    fontSize = 13.sp,
                )
            } else {
                MutedLine(text = GoalWords.subline(view.goal), fontSize = 13.sp)
                RunCard(view)
                TodayCard(
                    view = view,
                    onCheck = { viewModel.check() },
                    onTimer = { onNavigate(GoalRoutes.timer(view.goal.id)) },
                    onUndo = { viewModel.uncheck(it) },
                )
                GridCard(view = view, today = state.today)
                PeriodsCard(view = view, today = state.today)
            }
        }
    }
}

@Composable
private fun RunCard(view: GoalView) {
    val unit = GoalWords.unit(view.goal.cadence)
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        shadowColor = HotPink,
        shadowAlpha = 0.14f,
        elevation = 14.dp,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        gap = 2.dp,
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = view.currentRun.toString(), color = HotPink, fontSize = 64.sp, lineHeight = 66.sp, fontWeight = FontWeight.Black)
            Text(
                text = "${GoalWords.plural(unit, view.currentRun)} running",
                color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        MutedLine(
            text = if (view.bestRun > 0) "Best run, ${view.bestRun} ${GoalWords.plural(unit, view.bestRun)}" else "Every run starts with one.",
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun TodayCard(view: GoalView, onCheck: () -> Unit, onTimer: () -> Unit, onUndo: (Long) -> Unit) {
    val goal = view.goal
    val doneForPeriod = goal.kind == TargetKind.CHECK && view.current.state == PeriodState.MET
    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
        Text(text = GoalWords.nowLine(view), color = Ink, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black)
        val closing = view.closingLine
        if (closing != null) BodyLine(text = closing, color = Plum)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (goal.kind == TargetKind.MINUTES) {
                PillButton(
                    text = "Start timer", onClick = onTimer, background = Ink, textColor = White,
                    fontWeight = FontWeight.Black, shadowAlpha = 0.25f,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                )
            } else {
                PillButton(
                    text = when {
                        doneForPeriod -> "Done"
                        goal.kind == TargetKind.COUNT -> "Add one"
                        else -> "Check"
                    },
                    onClick = onCheck,
                    enabled = !doneForPeriod,
                    background = if (doneForPeriod) HotPink else LightPink,
                    textColor = if (doneForPeriod) White else Ink,
                    shadowColor = HotPink, shadowAlpha = 0.25f, fontWeight = FontWeight.Black,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                )
                PillButton(text = "Start timer", onClick = onTimer, shadowAlpha = 0.1f)
            }
        }

        if (view.checksToday.isNotEmpty()) {
            SectionLabel(text = "Today's checks", modifier = Modifier.padding(top = 4.dp))
            for (check in view.checksToday) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(text = GoalWords.checkText(check, goal.kind), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                        Text(text = GoalWords.clockOf(check.createdUtcMs), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    TextLink(text = "Undo", onClick = { onUndo(check.id) })
                }
            }
        }
    }
}

@Composable
private fun GridCard(view: GoalView, today: LocalDate) {
    val rows = view.grid.chunked(7)
    val checked = view.grid.count { it.second }
    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionLabel("The last four weeks")
            SectionLabel("$checked of ${view.grid.size} days")
        }
        val first = rows.firstOrNull().orEmpty()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for ((date, _) in first) {
                Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        color = Muted, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
        for (row in rows) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for ((date, on) in row) GridDot(checked = on, today = date == today, diameter = 30.dp)
            }
        }
        MutedLine(text = "Hot pink days hold a check. Today wears the ring.")
    }
}

@Composable
private fun PeriodsCard(view: GoalView, today: LocalDate) {
    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
        SectionLabel(GoalWords.each(view.goal.cadence))
        PeriodDots(history = view.history, today = today, maxDot = 22.dp)
        MutedLine(text = "Hot pink met, light pink mostly, lavender started, pale untouched. A pale circle is only a pause.")
    }
}
