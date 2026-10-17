package com.lifepowers.app.ui.screens.goals

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepowers.app.ui.components.BodyLine
import com.lifepowers.app.ui.components.Chip
import com.lifepowers.app.ui.components.CloudSpec
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.Fmt
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.PrimaryButton
import com.lifepowers.app.ui.components.ScreenHeader
import com.lifepowers.app.ui.components.SectionLabel
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.components.WrapRow
import com.lifepowers.app.ui.theme.Ground
import com.lifepowers.app.ui.theme.HairLine
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.LightPink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.app.ui.theme.Plum
import com.lifepowers.app.ui.theme.White
import com.lifepowers.core.Cadence
import com.lifepowers.core.Comparison
import com.lifepowers.core.Metrics
import com.lifepowers.core.TargetKind
import java.time.DayOfWeek
import java.time.LocalTime

private val EditClouds = listOf(
    CloudSpec(LightPink, 230.dp, x = (-70).dp, y = (-90).dp, alpha = 0.4f, fromEnd = true),
)

private val Cadences = listOf(Cadence.DAILY to "Daily", Cadence.WEEKLY to "Weekly", Cadence.MONTHLY to "Monthly", Cadence.YEARLY to "Yearly")

private val Kinds = listOf(
    TargetKind.CHECK to "Done or not",
    TargetKind.COUNT to "A count",
    TargetKind.MINUTES to "Minutes",
    TargetKind.METRIC to "A measurement",
)

private enum class Confirm { ARCHIVE, DELETE }

@Composable
fun GoalEditScreen(
    goalId: Long,
    prefillName: String?,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: GoalEditViewModel = hiltViewModel(),
) {
    LaunchedEffect(goalId, prefillName) { viewModel.load(goalId, prefillName) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }

    /** Index of the reminder being changed, or -1 while adding one. Null when closed. */
    var timeEdit by remember { mutableStateOf<Int?>(null) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val form = state.form

    Box(modifier = Modifier.fillMaxSize()) {
        Clouds(EditClouds)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(title = if (state.editing) "Edit goal" else "New goal", titleSize = 28.sp) {
                TextLink(text = "Back", onClick = onBack)
            }

            if (!state.loaded) {
                MutedLine(text = "Opening", fontSize = 13.sp)
            } else {
                NameCard(name = form.name, onChange = { v -> viewModel.update { it.copy(name = v) } })

                SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
                    SectionLabel("How often")
                    SegmentPills(
                        options = Cadences.map { it.second },
                        selected = Cadences.indexOfFirst { it.first == form.cadence }.coerceAtLeast(0),
                        onSelect = { viewModel.setCadence(Cadences[it].first) },
                        track = Ground,
                        shadowAlpha = 0f,
                    )
                }

                KindCard(form = form, viewModel = viewModel)

                if (form.kind == TargetKind.CHECK || form.kind == TargetKind.METRIC) {
                    AutoCard(form = form, viewModel = viewModel)
                }

                RemindersCard(
                    form = form,
                    onAdd = { timeEdit = -1 },
                    onChange = { timeEdit = it },
                    onRemove = { viewModel.removeTime(it) },
                    onToggleDay = { viewModel.toggleDay(it) },
                    onClosing = { on -> viewModel.update { it.copy(closingNotice = on) } },
                )

                PrimaryButton(
                    text = if (state.saving) "Saving" else "Save",
                    onClick = { viewModel.save() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.canSave,
                )
                if (form.name.isBlank()) {
                    MutedLine(text = "Give it a name to save it.", modifier = Modifier.padding(start = 6.dp))
                }

                if (state.editing) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TextLink(text = "Archive", onClick = { confirm = Confirm.ARCHIVE })
                        TextLink(text = "Delete", onClick = { confirm = Confirm.DELETE })
                    }
                }
            }
        }
    }

    val editing = timeEdit
    if (editing != null) {
        TimePickDialog(
            initial = form.reminderTimes.getOrNull(editing) ?: LocalTime.of(20, 0),
            confirmText = if (editing >= 0) "Change" else "Add",
            onDismiss = { timeEdit = null },
            onPick = { t ->
                viewModel.setTime(editing, t)
                timeEdit = null
            },
        )
    }

    when (confirm) {
        Confirm.ARCHIVE -> ConfirmDialog(
            title = "Archive this goal?",
            body = "It leaves the Goals tab and stops reminding you. Its history stays in your data.",
            confirmText = "Archive",
            onConfirm = { confirm = null; viewModel.archive() },
            onDismiss = { confirm = null },
        )
        Confirm.DELETE -> ConfirmDialog(
            title = "Delete this goal?",
            body = "The goal and its checks are removed for good. Archive keeps the history instead.",
            confirmText = "Delete",
            onConfirm = { confirm = null; viewModel.delete() },
            onDismiss = { confirm = null },
        )
        null -> Unit
    }
}

@Composable
private fun NameCard(name: String, onChange: (String) -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
        SectionLabel("What it is")
        OutlinedTextField(
            value = name,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(text = "Walk after lunch", color = Muted, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            singleLine = true,
            textStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold),
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Ink,
                unfocusedTextColor = Ink,
                focusedContainerColor = White,
                unfocusedContainerColor = Ground,
                focusedBorderColor = HotPink,
                unfocusedBorderColor = HairLine,
                cursorColor = HotPink,
            ),
        )
    }
}

@Composable
private fun KindCard(form: GoalForm, viewModel: GoalEditViewModel) {
    val per = GoalWords.per(form.cadence)
    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 12.dp) {
        SectionLabel("How it counts")
        WrapRow {
            for ((kind, label) in Kinds) {
                Chip(text = label, selected = form.kind == kind, onClick = { viewModel.setKind(kind) })
            }
        }
        when (form.kind) {
            TargetKind.CHECK -> MutedLine(text = "One check and ${GoalWords.thisPeriod(form.cadence)} is met.")
            TargetKind.COUNT -> Stepper(
                value = form.count.toString(),
                unit = "${GoalWords.plural("time", form.count)} $per",
                onLess = { viewModel.update { it.copy(count = (it.count - 1).coerceAtLeast(1)) } },
                onMore = { viewModel.update { it.copy(count = (it.count + 1).coerceAtMost(99)) } },
                lessEnabled = form.count > 1,
                moreEnabled = form.count < 99,
            )
            TargetKind.MINUTES -> Stepper(
                value = form.minutes.toString(),
                unit = "${GoalWords.plural("minute", form.minutes)} $per",
                onLess = { viewModel.update { it.copy(minutes = (it.minutes - 5).coerceAtLeast(5)) } },
                onMore = { viewModel.update { it.copy(minutes = (it.minutes + 5).coerceAtMost(600)) } },
                lessEnabled = form.minutes > 5,
                moreEnabled = form.minutes < 600,
            )
            TargetKind.METRIC -> {
                MeasurementBlock(form = form, viewModel = viewModel)
                if (form.cadence != Cadence.DAILY) {
                    val max = maxDays(form.cadence)
                    val day = GoalWords.dayWord(form.metric)
                    SectionLabel("How many ${day}s")
                    Stepper(
                        value = form.days.toString(),
                        unit = "${GoalWords.plural(day, form.days)} $per",
                        onLess = { viewModel.update { it.copy(days = (it.days - 1).coerceAtLeast(1)) } },
                        onMore = { viewModel.update { it.copy(days = (it.days + 1).coerceAtMost(max)) } },
                        lessEnabled = form.days > 1,
                        moreEnabled = form.days < max,
                    )
                }
            }
        }
        BodyLine(text = GoalWords.targetWords(form.toGoal(null)), color = Plum)
    }
}

@Composable
private fun MeasurementBlock(form: GoalForm, viewModel: GoalEditViewModel) {
    SectionLabel("Which measurement")
    WrapRow {
        for (c in GoalWords.metricChoices) {
            Chip(
                text = c.label.replaceFirstChar { it.uppercase() },
                selected = form.metric == c.key,
                onClick = { viewModel.pickMetric(c.key) },
            )
        }
    }
    val choice = GoalWords.choice(form.metric) ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(
            text = "At least",
            background = if (form.comparison == Comparison.AT_LEAST) LightPink else Ground,
            onClick = { viewModel.update { it.copy(comparison = Comparison.AT_LEAST) } },
        )
        Chip(
            text = "At most",
            background = if (form.comparison == Comparison.AT_MOST) LightPink else Ground,
            onClick = { viewModel.update { it.copy(comparison = Comparison.AT_MOST) } },
        )
    }
    Stepper(
        value = GoalWords.amount(form.threshold),
        unit = choice.unitWord,
        onLess = { viewModel.stepThreshold(up = false) },
        onMore = { viewModel.stepThreshold(up = true) },
        lessEnabled = form.threshold > choice.min,
        moreEnabled = form.threshold < choice.max,
    )
}

private fun autoDetail(form: GoalForm): String = when {
    !form.auto -> "You check it by hand, with one tap."
    form.kind == TargetKind.CHECK && form.checkSource == CheckSource.JOURNAL -> "Any journal entry or voice check-in that day counts."
    form.metric == Metrics.SCREEN_TOTAL_MIN -> "Phone time comes from usage stats, so the circle fills itself."
    else -> "The measurement fills the circle by itself when it lands on the right side."
}

@Composable
private fun AutoCard(form: GoalForm, viewModel: GoalEditViewModel) {
    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 12.dp) {
        SwitchRow(
            title = "Let the data check it",
            detail = autoDetail(form),
            checked = form.auto,
            onChange = { viewModel.setAuto(it) },
        )
        if (form.kind == TargetKind.CHECK && form.auto) {
            WrapRow {
                Chip(
                    text = "A measurement",
                    selected = form.checkSource == CheckSource.MEASUREMENT,
                    onClick = { viewModel.setCheckSource(CheckSource.MEASUREMENT) },
                )
                Chip(
                    text = "Write or talk in the journal",
                    selected = form.checkSource == CheckSource.JOURNAL,
                    onClick = { viewModel.setCheckSource(CheckSource.JOURNAL) },
                )
            }
            if (form.checkSource == CheckSource.MEASUREMENT) MeasurementBlock(form = form, viewModel = viewModel)
        }
    }
}

@Composable
private fun RemindersCard(
    form: GoalForm,
    onAdd: () -> Unit,
    onChange: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onClosing: (Boolean) -> Unit,
) {
    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
        SectionLabel("Reminders")
        if (form.reminderTimes.isEmpty()) {
            MutedLine(text = "No reminders yet. Add a time and the app nudges you quietly.")
        }
        form.reminderTimes.forEachIndexed { i, t ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = Fmt.timeOfDay(t), color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f),
                )
                TextLink(text = "Change", onClick = { onChange(i) })
                TextLink(text = "Remove", onClick = { onRemove(i) })
            }
        }
        PillButton(
            text = "Add a time", onClick = onAdd, background = LightPink, shadowColor = HotPink, shadowAlpha = 0.2f,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        )
        if (form.reminderTimes.isNotEmpty()) {
            SectionLabel(text = "On these days", modifier = Modifier.padding(top = 4.dp))
            DayPills(selected = form.reminderDays, onToggle = onToggleDay)
        }
        SwitchRow(
            title = "Gentle note when a period is ending",
            detail = "On the last day of a week, month, or year that is not met yet.",
            checked = form.closingNotice,
            onChange = onClosing,
        )
    }
}
