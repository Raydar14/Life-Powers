package com.lifepowers.app.ui.screens.checkin

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepowers.app.domain.CheckinRecord
import com.lifepowers.app.ui.components.BodyLine
import com.lifepowers.app.ui.components.Chip
import com.lifepowers.app.ui.components.CloudPresets
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.Fmt
import com.lifepowers.app.ui.components.InkButton
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.SectionLabel
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.components.WrapRow
import com.lifepowers.app.ui.components.softShadow
import com.lifepowers.app.ui.theme.DeepPink
import com.lifepowers.app.ui.theme.Ground
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.Lavender
import com.lifepowers.app.ui.theme.LightPink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.app.ui.theme.SoftBlue
import com.lifepowers.app.ui.theme.White
import com.lifepowers.app.voice.CheckinSessionController

@Composable
fun CheckinScreen(onBack: () -> Unit, viewModel: CheckinViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()
    val symptomsSaved by viewModel.symptomsSaved.collectAsStateWithLifecycle()
    val behaviorsLogged by viewModel.behaviorsLogged.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.start() else viewModel.permissionDenied()
    }
    val startWithPermission: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.start() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Clouds(CloudPresets.checkin)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextLink(text = "Back", onClick = onBack)
                if (state is CheckinSessionController.State.Idle) TextLink(text = "Another prompt", onClick = { viewModel.nextPrompt() })
            }

            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = prompt, color = Ink, fontSize = 26.sp, lineHeight = 31.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                MutedLine(text = "Talk for a minute. The audio never leaves your phone.", fontSize = 13.sp, textAlign = TextAlign.Center)
            }

            val current = state
            if (current is CheckinSessionController.State.Done) {
                current.crisisResources?.let { CrisisCard(it) }
            }

            TalkButton(state = current, onStart = startWithPermission, onStop = { viewModel.stop() }, onReset = { viewModel.reset() })

            if (notice != null) MutedLine(text = notice ?: "", fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

            when (current) {
                is CheckinSessionController.State.Idle -> {
                    MutedLine(
                        text = "Not a clinical instrument. What you hear back is how your own voice compares with your own usual.",
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    )
                }
                is CheckinSessionController.State.Recording -> {
                    SoftCard(modifier = Modifier.fillMaxWidth()) {
                        SectionLabel("Hearing you")
                        BodyLine(text = if (current.partialTranscript.isBlank()) "..." else current.partialTranscript, fontSize = 14.sp, lineHeight = 21.sp)
                    }
                }
                is CheckinSessionController.State.Processing -> {
                    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
                        SectionLabel("Listening back, on your phone")
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)),
                            color = LightPink,
                            trackColor = Ground,
                        )
                    }
                }
                is CheckinSessionController.State.Done -> {
                    DoneContent(
                        record = current.record,
                        suggestions = viewModel.suggestions(current.record),
                        symptomsSaved = symptomsSaved,
                        behaviorsLogged = behaviorsLogged,
                        onSaveSymptoms = { kinds -> viewModel.saveSymptoms(current.record, kinds) },
                        onLogBehaviors = { kinds -> viewModel.logBehaviors(current.record, kinds) },
                    )
                }
                is CheckinSessionController.State.Error -> {
                    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
                        SectionLabel("That did not work")
                        BodyLine(text = current.message, fontSize = 14.sp)
                        PillButton(text = "Try again", onClick = { viewModel.reset() }, background = DeepPink, textColor = White, fontWeight = FontWeight.Black, shadowColor = DeepPink, shadowAlpha = 0.3f)
                    }
                }
            }
        }
    }
}

@Composable
private fun TalkButton(state: CheckinSessionController.State, onStart: () -> Unit, onStop: () -> Unit, onReset: () -> Unit) {
    val label: String
    val caption: String
    val action: () -> Unit
    val color: Color
    when (state) {
        is CheckinSessionController.State.Idle -> { label = "Talk"; caption = "Thirty seconds to a minute is plenty"; action = onStart; color = HotPink }
        is CheckinSessionController.State.Recording -> { label = "Stop"; caption = Fmt.duration(state.seconds.toDouble()) + " so far"; action = onStop; color = Ink }
        is CheckinSessionController.State.Processing -> { label = "..."; caption = "A moment"; action = {}; color = Lavender }
        is CheckinSessionController.State.Done -> { label = "Again"; caption = "Recorded ${Fmt.duration(state.record.durationS)} just now"; action = onReset; color = HotPink }
        is CheckinSessionController.State.Error -> { label = "Talk"; caption = "Ready when you are"; action = onReset; color = HotPink }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .size(124.dp)
                .softShadow(RoundedCornerShape(999.dp), color, 0.35f, 18.dp)
                .clip(CircleShape)
                .background(color)
                .clickable(onClick = action),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, color = White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
        Text(text = caption, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CrisisCard(text: String) {
    SoftCard(modifier = Modifier.fillMaxWidth(), color = Ink, shadowAlpha = 0.28f, elevation = 14.dp, gap = 8.dp) {
        Text(text = text, color = White, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold)
        Text(text = "This recording is not scored. You matter more than a data point.", color = Lavender, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DoneContent(
    record: CheckinRecord,
    suggestions: List<String>,
    symptomsSaved: Boolean,
    behaviorsLogged: Boolean,
    onSaveSymptoms: (List<String>) -> Unit,
    onLogBehaviors: (List<String>) -> Unit,
) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        SectionLabel("What you said")
        BodyLine(text = record.transcript?.takeIf { it.isNotBlank() }?.let { "\"$it\"" } ?: "No words came through clearly this time.", fontSize = 14.sp, lineHeight = 21.sp)
    }

    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 12.dp) {
        SectionLabel("How you sounded, against your usual")
        val indices = record.indices
        IndexBar(label = "Energy", z = indices?.energy, alert = false)
        IndexBar(label = "Flatness", z = indices?.flatness, alert = (indices?.flatness ?: 0.0) > 1.5)
        IndexBar(label = "Tension", z = indices?.tension, alert = (indices?.tension ?: 0.0) > 1.5)
        val note = when {
            !record.qualityOk -> record.qualityNote?.let { "This one was hard to read ($it), so it stays out of your baseline." } ?: "This one was hard to read, so it stays out of your baseline."
            indices == null -> "Your baseline forms over the first fourteen check-ins. Until then the bars stay quiet."
            else -> "An observation about change in your own voice, not a diagnosis."
        }
        MutedLine(text = note)
    }

    if (record.symptoms.isNotEmpty()) {
        var picked by remember(record.id) { mutableStateOf(record.symptoms.map { it.kind }.toSet()) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(text = "Heard in your words. Log them?", modifier = Modifier.padding(start = 6.dp))
            WrapRow {
                for (s in record.symptoms) {
                    val on = s.kind in picked
                    Chip(
                        text = "${Fmt.kindName(s.kind)}, ${s.severity}",
                        selected = on,
                        background = White,
                        textColor = Muted,
                        onClick = if (symptomsSaved) null else ({ picked = if (on) picked - s.kind else picked + s.kind }),
                    )
                }
                if (symptomsSaved) {
                    Chip(text = "Saved", background = LightPink)
                } else {
                    InkButton(text = if (picked.size > 1) "Save all" else "Save", onClick = { onSaveSymptoms(picked.toList()) })
                }
            }
        }
    }

    if (suggestions.isNotEmpty()) {
        var picked by remember(record.id) { mutableStateOf(suggestions.toSet()) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(text = "You mentioned these. Log them?", modifier = Modifier.padding(start = 6.dp))
            WrapRow {
                for (kind in suggestions) {
                    val on = kind in picked
                    Chip(
                        text = Fmt.kindName(kind),
                        selected = false,
                        background = if (on) LightPink else White,
                        textColor = if (on) Ink else Muted,
                        onClick = if (behaviorsLogged) null else ({ picked = if (on) picked - kind else picked + kind }),
                    )
                }
                if (behaviorsLogged) {
                    Chip(text = "Logged", background = LightPink)
                } else {
                    InkButton(text = "Log", onClick = { onLogBehaviors(picked.toList()) })
                }
            }
        }
    }
}

/** Centered baseline tick, bar from the center in the direction of the index. */
@Composable
private fun IndexBar(label: String, z: Double?, alert: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                text = Fmt.voiceWord(z),
                color = if (alert) DeepPink else Muted,
                fontSize = 13.sp,
                fontWeight = if (alert) FontWeight.ExtraBold else FontWeight.Bold,
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(999.dp)).background(Ground)) {
            Box(modifier = Modifier.align(Alignment.Center).width(2.dp).height(10.dp).background(Lavender, RoundedCornerShape(1.dp)))
            if (z != null) {
                val frac = (Fmt.absFraction(z) * 0.5f).coerceAtLeast(0.02f)
                val left = if (z >= 0) 0.5f else 0.5f - frac
                val right = if (z >= 0) 0.5f - frac else 0.5f
                Row(modifier = Modifier.fillMaxSize()) {
                    if (left > 0.001f) Spacer(modifier = Modifier.weight(left))
                    Box(modifier = Modifier.weight(frac).fillMaxHeight().background(if (alert) HotPink else SoftBlue, RoundedCornerShape(999.dp)))
                    if (right > 0.001f) Spacer(modifier = Modifier.weight(right))
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Lavender.copy(alpha = 0.35f)))
            }
        }
    }
}
