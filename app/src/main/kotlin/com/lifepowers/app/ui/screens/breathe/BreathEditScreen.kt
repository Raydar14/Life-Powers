package com.lifepowers.app.ui.screens.breathe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepowers.app.ui.components.BodyLine
import com.lifepowers.app.ui.components.CardTitle
import com.lifepowers.app.ui.components.Chip
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.PrimaryButton
import com.lifepowers.app.ui.components.ScreenHeader
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.components.WrapRow
import com.lifepowers.app.ui.theme.Ground
import com.lifepowers.app.ui.theme.HairLine
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.LightPink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.app.ui.theme.White
import com.lifepowers.core.BreathPattern
import com.lifepowers.core.Breathing

private val Lengths: List<Int?> = listOf(3, 5, 10, 15, 20, null)

private val Guides = listOf(
    GuideOption.VISUAL to "Visual timer",
    GuideOption.TONES to "Tones",
    GuideOption.VOICE to "Spoken cues",
    GuideOption.VIBRATION to "Vibration",
)

private val Rows = listOf(
    Triple(BreathField.INHALE, "In", "Breathing in, through the nose if you can."),
    Triple(BreathField.HOLD_IN, "Hold", "A pause when you are full."),
    Triple(BreathField.EXHALE, "Out", "Breathing out, slow and soft."),
    Triple(BreathField.HOLD_OUT, "Hold", "A pause when you are empty."),
)

@Composable
fun BreathEditScreen(
    patternId: Long,
    presetName: String?,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: BreathEditViewModel = hiltViewModel(),
) {
    LaunchedEffect(patternId, presetName) { viewModel.load(patternId, presetName) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val event by viewModel.event.collectAsStateWithLifecycle()
    LaunchedEffect(event) {
        when (event) {
            EditEvent.STARTED -> {
                viewModel.consumeEvent()
                onNavigate(BreatheRoutes.SESSION)
            }
            EditEvent.SAVED -> {
                viewModel.consumeEvent()
                onBack()
            }
            null -> Unit
        }
    }
    val form = state.form

    Box(modifier = Modifier.fillMaxSize()) {
        Clouds(BreatheClouds.edit)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(
                title = if (state.existing != null) "Edit pattern" else "Your own pattern",
                titleSize = 28.sp,
                subline = "Set each part of the breath in half seconds.",
            ) {
                TextLink(text = "Back", onClick = onBack)
            }

            if (!state.loaded) {
                MutedLine(text = "Opening", fontSize = 13.sp)
                return@Column
            }

            PreviewCard(pattern = form.pattern)

            SoftCard(modifier = Modifier.fillMaxWidth(), gap = 18.dp) {
                for ((field, title, detail) in Rows) {
                    val v = form.value(field)
                    val isHold = field == BreathField.HOLD_IN || field == BreathField.HOLD_OUT
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CardTitle(text = title)
                            MutedLine(text = detail, modifier = Modifier.weight(1f))
                        }
                        SecondsStepper(
                            value = v,
                            isHold = isHold,
                            onLess = { viewModel.step(field, up = false) },
                            onMore = { viewModel.step(field, up = true) },
                            lessEnabled = v > lowest(field),
                            moreEnabled = v < highest(field),
                        )
                    }
                }
            }

            IssueList(issues = state.issues, onMatchOut = { viewModel.matchOut() })

            SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
                CardTitle(text = "Guide me with")
                WrapRow {
                    for ((option, label) in Guides) {
                        Chip(text = label, selected = form.isOn(option), onClick = { viewModel.toggleGuide(option) })
                    }
                }
                MutedLine(text = "The visual timer shows on screen. Tones, spoken cues, and vibration keep going with the screen off. At least one stays on.")
            }

            SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
                CardTitle(text = "How long")
                WrapRow {
                    for (length in Lengths) {
                        Chip(
                            text = if (length == null) "Open" else "$length min",
                            selected = form.sessionMin == length,
                            onClick = { viewModel.setLength(length) },
                        )
                    }
                }
            }

            SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
                CardTitle(text = "Count toward a habit")
                if (state.habits.isEmpty()) {
                    MutedLine(text = "Habits that count minutes, or a simple done, show here once you make one.")
                } else {
                    WrapRow {
                        Chip(text = "No habit", selected = form.goalId == null, onClick = { viewModel.setGoal(null) })
                        for (g in state.habits) {
                            Chip(text = g.name, selected = form.goalId == g.id, onClick = { viewModel.setGoal(g.id) })
                        }
                    }
                    MutedLine(text = "The minutes you breathe count toward it when you finish and save.")
                }
            }

            NameCard(name = form.name, onChange = { viewModel.setName(it) })

            val message = state.message
            if (message != null) {
                SoftCard(modifier = Modifier.fillMaxWidth(), color = LightPink, shadowColor = HotPink, shadowAlpha = 0.18f) {
                    BodyLine(text = message, color = Ink)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    text = if (state.busy) "Starting" else "Start now",
                    onClick = { viewModel.startNow() },
                    modifier = Modifier.weight(1f),
                    enabled = state.canStart,
                )
                PillButton(
                    text = "Save",
                    onClick = { viewModel.save() },
                    modifier = Modifier.weight(1f),
                    background = LightPink,
                    shadowColor = HotPink,
                    shadowAlpha = 0.18f,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    enabled = state.canSave,
                )
            }
            val hint = when {
                state.blocked -> "Start and save open up once the pattern is changed as described above."
                state.sessionRunning -> "A session is already running. Finish it before starting another."
                form.name.isBlank() -> "Give it a name to save it. Start now works without one."
                state.existing == null -> "Start now breathes without saving. Save keeps it on the Breathe screen."
                else -> "Start now keeps your changes and begins."
            }
            MutedLine(text = hint, modifier = Modifier.padding(horizontal = 6.dp))
            MutedLine(text = "Stop if you feel dizzy or short of breath.", modifier = Modifier.padding(horizontal = 6.dp))
        }
    }
}

/** A drawn breath that follows the pattern on a loop, so the numbers can be felt before starting. */
@Composable
private fun PreviewCard(pattern: BreathPattern) {
    var t by remember(pattern) { mutableDoubleStateOf(0.0) }
    LaunchedEffect(pattern) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> t = (now - start) / 1_000_000_000.0 }
        }
    }
    val phase by remember(pattern) { derivedStateOf { Breathing.pointAt(pattern, t).phase } }

    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 8.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BreathOrb(fill = { Breathing.pointAt(pattern, t).fill.toFloat() }, modifier = Modifier.size(132.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = phase.label, color = Ink, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Black)
                Text(text = pattern.describe(), color = Ink, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.ExtraBold)
                MutedLine(text = BreathWords.pace(pattern), fontSize = 13.sp)
                MutedLine(text = "One breath takes ${BreathWords.secs(pattern.cycleS)} seconds.")
            }
        }
    }
}

@Composable
private fun NameCard(name: String, onChange: (String) -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
        CardTitle(text = "Name")
        OutlinedTextField(
            value = name,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(text = "Evening wind down", color = Muted, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            singleLine = true,
            textStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Start),
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
