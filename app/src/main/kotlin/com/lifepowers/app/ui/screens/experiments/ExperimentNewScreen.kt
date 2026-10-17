package com.lifepowers.app.ui.screens.experiments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepowers.app.experiments.ExperimentMath
import com.lifepowers.app.ui.components.CardTitle
import com.lifepowers.app.ui.components.Chip
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.PinkSwitch
import com.lifepowers.app.ui.components.PrimaryButton
import com.lifepowers.app.ui.components.ScreenHeader
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.components.WrapRow
import com.lifepowers.app.ui.theme.Ground
import com.lifepowers.app.ui.theme.HairLine
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.app.ui.theme.White
import com.lifepowers.core.Condition
import com.lifepowers.core.Experiments

@Composable
fun ExperimentNewScreen(
    prefillBehavior: String?,
    prefillOutcome: String?,
    onDone: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: ExperimentNewViewModel = hiltViewModel(),
) {
    LaunchedEffect(prefillBehavior, prefillOutcome) { viewModel.prefill(prefillBehavior, prefillOutcome) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.doneId) { state.doneId?.let(onDone) }
    val form = state.form

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
            ScreenHeader(
                title = "New experiment",
                titleSize = 28.sp,
                subline = "One thing to try, one thing to watch.",
            ) {
                TextLink(text = "Back", onClick = onBack)
            }

            SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
                CardTitle("What you will try")
                MutedLine(text = "On days you do it, off days you live as usual.")
                OutlinedTextField(
                    value = form.behavior,
                    onValueChange = viewModel::setBehavior,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(text = "No screens after 9 pm", color = Muted, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
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
                WrapRow {
                    for (pick in ExperimentChoices.behaviors) {
                        Chip(text = pick, selected = form.behavior.trim() == pick, onClick = { viewModel.setBehavior(pick) })
                    }
                }
            }

            SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
                CardTitle("What to watch")
                WrapRow {
                    for (key in state.outcomes) {
                        Chip(text = outcomeName(key), selected = form.outcome == key, onClick = { viewModel.setOutcome(key) })
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = "Shows up the next day", color = Ink, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.ExtraBold)
                        MutedLine(text = "An evening habit lands in that night's sleep, which counts for the morning after.")
                    }
                    PinkSwitch(checked = form.nextDay, onChange = viewModel::setNextDay)
                }
            }

            SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
                CardTitle("Days in a row")
                MutedLine(text = "How many days each block runs before it switches.")
                OptionPills(
                    options = ExperimentChoices.blockDays.map { "$it days" },
                    selected = ExperimentChoices.blockDays.indexOf(form.blockDays).coerceAtLeast(0),
                    onSelect = { viewModel.setBlockDays(ExperimentChoices.blockDays[it]) },
                )
                CardTitle("How long", modifier = Modifier.padding(top = 6.dp))
                MutedLine(text = "About ${Experiments.MIN_PER_ARM} of each kind of day are needed for a first read. Longer is surer.")
                OptionPills(
                    options = ExperimentChoices.lengths.map { "$it days" },
                    selected = ExperimentChoices.lengths.indexOf(form.targetDays).coerceAtLeast(0),
                    onSelect = { viewModel.setTargetDays(ExperimentChoices.lengths[it]) },
                )
            }

            SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
                CardTitle("Daily reminder")
                MutedLine(text = "A quiet notice saying which kind of day today is.")
                WrapRow {
                    for (hour in ExperimentChoices.reminderHours) {
                        Chip(text = hourWord(hour), selected = form.reminderHour == hour, onClick = { viewModel.setReminderHour(hour) })
                    }
                }
            }

            SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
                CardTitle("The first two weeks")
                val preview = state.preview
                DayGrid(
                    looks = preview.map { if (it == Condition.ON) DayLook.ON else DayLook.OFF },
                    ringedIndex = 0,
                    maxDot = 26.dp,
                    gap = 8.dp,
                )
                Legend(listOf(DayLook.ON to "on day", DayLook.OFF to "off day"))
                MutedLine(text = previewLine(preview, form.blockDays))
            }

            PrimaryButton(
                text = if (state.saving) "Starting" else "Start",
                onClick = viewModel::start,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canStart,
            )
            when {
                state.failed -> MutedLine(text = "That did not save. Try once more.", modifier = Modifier.padding(start = 6.dp))
                form.behavior.isBlank() -> MutedLine(text = "Say what you will try to start.", modifier = Modifier.padding(start = 6.dp))
                form.outcome == null -> MutedLine(text = "Pick what to watch to start.", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

private fun previewLine(preview: List<Condition>, blockDays: Int): String {
    val first = preview.firstOrNull() ?: return "Starts today."
    val kind = if (first == Condition.ON) "an on day" else "an off day"
    return "Starts today with $kind. Blocks of $blockDays come in pairs, and a coin decides which half of each pair goes first."
        .let { ExperimentMath.upperFirst(it) }
}
