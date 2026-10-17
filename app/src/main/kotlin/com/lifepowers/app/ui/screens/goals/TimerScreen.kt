package com.lifepowers.app.ui.screens.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepowers.app.domain.TimerSession
import com.lifepowers.app.ui.components.Chip
import com.lifepowers.app.ui.components.CloudSpec
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.PrimaryButton
import com.lifepowers.app.ui.components.ScreenHeader
import com.lifepowers.app.ui.components.SectionLabel
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.components.WrapRow
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.Lavender
import com.lifepowers.app.ui.theme.LightPink
import com.lifepowers.app.ui.theme.SoftBlue
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.ceil

private val PickClouds = listOf(
    CloudSpec(Lavender, 230.dp, x = (-70).dp, y = (-90).dp, alpha = 0.4f, fromEnd = true),
)

/** The calm screen: three soft clouds drifting off the edges. */
private val CalmClouds = listOf(
    CloudSpec(LightPink, 300.dp, x = (-110).dp, y = (-80).dp, alpha = 0.45f, fromEnd = true),
    CloudSpec(Lavender, 260.dp, x = (-120).dp, y = 260.dp, alpha = 0.35f),
    CloudSpec(SoftBlue, 280.dp, x = (-90).dp, y = (-60).dp, alpha = 0.35f, fromEnd = true, fromBottom = true),
)

private val Lengths: List<Int?> = listOf(10, 15, 20, 30, 45, 60, null)

@Composable
fun TimerScreen(goalId: Long?, onDone: () -> Unit, viewModel: TimerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()
    LaunchedEffect(done) { if (done) onDone() }

    val running = state.running
    if (running != null) {
        RunningView(
            session = running,
            name = state.nameOf(running.goalId),
            otherThanAsked = goalId != null && running.goalId != goalId,
            onStop = { viewModel.stop() },
            onLetGo = { viewModel.cancel() },
        )
    } else {
        PickView(
            state = state,
            goalId = goalId,
            onStart = { chosen, minutes -> viewModel.start(chosen, minutes) },
            onClose = onDone,
        )
    }
}

@Composable
private fun PickView(state: TimerUiState, goalId: Long?, onStart: (Long?, Int?) -> Unit, onClose: () -> Unit) {
    var chosen by rememberSaveable(goalId) { mutableStateOf<Long?>(goalId) }
    var minutes by rememberSaveable { mutableStateOf<Int?>(10) }
    val habits = state.goals.filter { it.isHabit }

    Box(modifier = Modifier.fillMaxSize()) {
        Clouds(PickClouds)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(title = "Timer", titleSize = 28.sp) {
                TextLink(text = "Not now", onClick = onClose)
            }
            MutedLine(
                text = "Pick a length or leave it open. The screen goes quiet and shows only the time. Stopping logs the real minutes.",
                fontSize = 13.sp,
            )

            SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
                SectionLabel("For")
                val name = state.nameOf(chosen)
                if (goalId != null && name != null) {
                    Text(text = name, color = Ink, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black)
                } else {
                    WrapRow {
                        for (g in habits) {
                            Chip(text = g.name, selected = chosen == g.id, onClick = { chosen = g.id })
                        }
                        Chip(text = "Just time", selected = chosen == null, onClick = { chosen = null })
                    }
                }
            }

            SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
                SectionLabel("How long")
                WrapRow {
                    for (length in Lengths) {
                        Chip(
                            text = if (length == null) "Open" else "$length min",
                            selected = minutes == length,
                            onClick = { minutes = length },
                        )
                    }
                }
            }

            PrimaryButton(
                text = if (state.starting) "Starting" else "Start",
                onClick = { onStart(chosen, minutes) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.starting,
            )
        }
    }
}

@Composable
private fun RunningView(
    session: TimerSession,
    name: String?,
    otherThanAsked: Boolean,
    onStop: () -> Unit,
    onLetGo: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.id, session.startUtcMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L - (now % 1000L))
        }
    }

    val elapsedMs = (now - session.startUtcMs).coerceAtLeast(0L)
    val planned = session.plannedMin
    val shown: String
    val caption: String
    if (planned != null) {
        val remainingMs = planned * 60_000L - elapsedMs
        shown = clock(ceil(remainingMs.coerceAtLeast(0L) / 1000.0).toLong())
        caption = if (remainingMs > 0) "left of $planned min" else "That is the full $planned min. Stop when you are ready."
    } else {
        shown = clock(elapsedMs / 1000L)
        caption = "open session"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Clouds(CalmClouds)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 28.dp, end = 28.dp, top = 30.dp, bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = name ?: "Quiet time",
                color = Ink, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = shown,
                color = HotPink, fontSize = 84.sp, lineHeight = 88.sp, fontWeight = FontWeight.Black,
                maxLines = 1, textAlign = TextAlign.Center,
            )
            MutedLine(text = caption, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(36.dp))
            PrimaryButton(text = "Stop and log", onClick = onStop, modifier = Modifier.fillMaxWidth(0.72f))
            Spacer(modifier = Modifier.height(10.dp))
            PillButton(text = "Let it go", onClick = onLetGo, shadowAlpha = 0.08f, modifier = Modifier.fillMaxWidth(0.72f))
            if (otherThanAsked) {
                Spacer(modifier = Modifier.height(18.dp))
                MutedLine(
                    text = "This session was already running, so it carries on here. Stop it before starting another.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 9:05, or 1:02:05 past the hour. */
private fun clock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0L)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
}
