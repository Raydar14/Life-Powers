package com.lifepowers.app.ui.screens.breathe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepowers.app.domain.BreathLive
import com.lifepowers.app.ui.components.BodyLine
import com.lifepowers.app.ui.components.CardTitle
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.Fmt
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.PrimaryButton
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.LightPink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.app.ui.theme.White
import com.lifepowers.core.BioSnapshot
import com.lifepowers.core.Breathing
import kotlin.math.ceil
import kotlin.math.floor

private const val DizzyLine = "Stop if you feel dizzy or short of breath."

/** Seconds of heart rate the live line spans: the last two minutes. */
private const val LiveSlots = 120

@Composable
fun BreathSessionScreen(onDone: (Long?) -> Unit, viewModel: BreathSessionViewModel = hiltViewModel()) {
    val live by viewModel.live.collectAsStateWithLifecycle()
    val outcome by viewModel.outcome.collectAsStateWithLifecycle()
    val finishing by viewModel.finishing.collectAsStateWithLifecycle()
    LaunchedEffect(outcome) {
        val o = outcome ?: return@LaunchedEffect
        onDone(o.savedId)
    }

    val current = live
    val view = LocalView.current
    val keepOn = current != null && current.guide.visual
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    when {
        current == null -> QuietView(text = if (finishing) "Saving" else "Getting ready")
        current.guide.visual -> VisualView(
            live = current,
            finishing = finishing,
            onPause = { viewModel.pause() },
            onResume = { viewModel.resume() },
            onFinish = { viewModel.finish() },
            onStop = { viewModel.cancel() },
        )
        else -> SoundView(
            live = current,
            finishing = finishing,
            onPause = { viewModel.pause() },
            onResume = { viewModel.resume() },
            onFinish = { viewModel.finish() },
            onStop = { viewModel.cancel() },
        )
    }
}

/** What the words on screen need, changing only when a word or a whole second changes. */
private data class StageView(
    val phase: String,
    val secondsLeft: Int,
    val cycle: Int,
    val elapsedS: Int,
    val remainingS: Int?,
)

private class BreathClock(val view: State<StageView>, val fill: () -> Float)

/**
 * Follows the session smoothly between updates from the controller. The last reported elapsed time
 * is the anchor; while running, the frame clock carries it forward (never more than two seconds) and
 * core Breathing.pointAt gives the eased fill. When paused, the controller's own point is shown.
 */
@Composable
private fun rememberBreathClock(live: BreathLive): BreathClock {
    val moving = live.running && !live.paused
    val pattern = live.pattern
    val planned = live.plannedS
    val anchorS = live.elapsedS
    val anchorNs = remember(anchorS, moving) { System.nanoTime() }
    val now = remember { mutableLongStateOf(System.nanoTime()) }
    LaunchedEffect(moving) {
        if (moving) {
            while (true) {
                withFrameNanos { }
                now.longValue = System.nanoTime()
            }
        }
    }
    val paused = live.point
    return remember(pattern, planned, anchorS, anchorNs, moving, paused) {
        val estimate: () -> Double = {
            if (moving) anchorS + ((now.longValue - anchorNs) / 1_000_000_000.0).coerceIn(0.0, 2.0) else anchorS
        }
        val view = derivedStateOf {
            val t = estimate()
            val p = if (moving) Breathing.pointAt(pattern, t) else paused
            StageView(
                phase = p.phase.label,
                secondsLeft = ceil(p.phaseRemainingS).toInt().coerceAtLeast(1),
                cycle = p.cycle + 1,
                elapsedS = floor(t).toInt(),
                remainingS = planned?.let { ceil(it - t).toInt().coerceAtLeast(0) },
            )
        }
        val fill: () -> Float = {
            val p = if (moving) Breathing.pointAt(pattern, estimate()) else paused
            p.fill.toFloat()
        }
        BreathClock(view, fill)
    }
}

@Composable
private fun VisualView(
    live: BreathLive,
    finishing: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onStop: () -> Unit,
) {
    val clock = rememberBreathClock(live)
    val stage by clock.view

    Box(modifier = Modifier.fillMaxSize()) {
        Clouds(BreatheClouds.calm)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = live.name,
                color = Ink, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            MutedLine(text = timesLine(stage), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

            Box(modifier = Modifier.size(250.dp), contentAlignment = Alignment.Center) {
                BreathOrb(fill = clock.fill, modifier = Modifier.fillMaxSize())
                if (!live.paused) {
                    Text(text = stage.secondsLeft.toString(), color = Ink, fontSize = 54.sp, lineHeight = 56.sp, fontWeight = FontWeight.Black)
                }
            }

            Text(
                text = if (live.paused) "Paused" else stage.phase,
                color = Ink, fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            MutedLine(
                text = "Breath ${stage.cycle}. ${live.pattern.describe()}.",
                fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(4.dp))
            val bio = live.bio
            if (bio != null) {
                BioPanel(bio = bio, recentHr = live.recentHr)
            } else {
                MutedLine(
                    text = "Connect a heart sensor on the Breathe screen to see live data.",
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Controls(live = live, finishing = finishing, dark = false, onPause = onPause, onResume = onResume, onFinish = onFinish, onStop = onStop)
            MutedLine(text = DizzyLine, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Guided by sound: a dim, nearly empty screen with only the phase word. */
@Composable
private fun SoundView(
    live: BreathLive,
    finishing: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onStop: () -> Unit,
) {
    val clock = rememberBreathClock(live)
    val stage by clock.view
    val soft = White.copy(alpha = 0.62f)

    Box(modifier = Modifier.fillMaxSize().background(Ink)) {
        Clouds(BreatheClouds.dim)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 28.dp, end = 28.dp, top = 80.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (live.paused) "Paused" else stage.phase,
                color = LightPink.copy(alpha = 0.85f), fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Guided by sound. You can turn the screen off.",
                color = soft, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(text = timesLine(stage), color = soft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(80.dp))
            Controls(live = live, finishing = finishing, dark = true, onPause = onPause, onResume = onResume, onFinish = onFinish, onStop = onStop)
            Text(text = DizzyLine, color = soft, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Controls(
    live: BreathLive,
    finishing: Boolean,
    dark: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onStop: () -> Unit,
) {
    val quietBg = if (dark) White.copy(alpha = 0.14f) else White
    val quietText = if (dark) White else Ink
    Column(
        modifier = Modifier.fillMaxWidth(0.8f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PillButton(
            text = if (live.paused) "Resume" else "Pause",
            onClick = if (live.paused) onResume else onPause,
            modifier = Modifier.fillMaxWidth(),
            background = quietBg,
            textColor = quietText,
            shadowAlpha = if (dark) 0f else 0.1f,
            enabled = !finishing,
        )
        if (dark) {
            PillButton(
                text = if (finishing) "Saving" else "Finish and save",
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth(),
                background = LightPink,
                textColor = Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                shadowAlpha = 0f,
                enabled = !finishing,
            )
        } else {
            PrimaryButton(text = if (finishing) "Saving" else "Finish and save", onClick = onFinish, modifier = Modifier.fillMaxWidth(), enabled = !finishing)
        }
        PillButton(
            text = "Stop without saving",
            onClick = onStop,
            modifier = Modifier.fillMaxWidth(),
            background = quietBg,
            textColor = quietText,
            shadowAlpha = if (dark) 0f else 0.08f,
            enabled = !finishing,
        )
        if (live.elapsedS < 30.0) {
            Text(
                text = "Sessions under 30 seconds are not saved.",
                color = if (dark) White.copy(alpha = 0.55f) else Muted,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BioPanel(bio: BioSnapshot, recentHr: List<Double?>) {
    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 8.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(text = Fmt.num(bio.bpm), color = HotPink, fontSize = 52.sp, lineHeight = 54.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(text = "bpm", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 9.dp))
            Spacer(modifier = Modifier.weight(1f))
            CardTitle(text = "Your heart, live", modifier = Modifier.padding(bottom = 10.dp))
        }
        val enough = recentHr.count { it != null && !it.isNaN() } >= 2
        if (enough) {
            HeartLine(values = recentHr, slots = LiveSlots, modifier = Modifier.fillMaxWidth().height(96.dp))
            MutedLine(text = "The last two minutes.")
        } else {
            MutedLine(text = "The line fills in as readings arrive.")
        }
        val swing = bio.swingBpm
        if (swing != null) BodyLine(text = "Swinging ${Fmt.num(swing)} bpm with each breath.")
        val sync = bio.sync
        if (sync != null) BodyLine(text = "Your heart is ${BreathWords.sync(sync)}.")
        val rmssd = bio.rmssdMs
        if (rmssd != null) BodyLine(text = "Beat to beat variation ${Fmt.num(rmssd)} ms.")
        if (swing == null && sync == null) MutedLine(text = "Swing and rhythm show after a few full breaths.")
    }
}

/** Shown for the moment before the session appears, or while it saves. */
@Composable
private fun QuietView(text: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        Clouds(BreatheClouds.calm)
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BreathOrb(fill = { 0.2f }, modifier = Modifier.size(160.dp), dim = true)
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = text, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        }
    }
}

/** "4:10 in, 5:50 left" or "4:10 in, open session". */
private fun timesLine(stage: StageView): String {
    val elapsed = BreathWords.clock(stage.elapsedS.toDouble())
    val remaining = stage.remainingS
    return when {
        remaining == null -> "$elapsed in, open session"
        remaining > 0 -> "$elapsed in, ${BreathWords.clock(remaining.toDouble())} left"
        else -> "$elapsed in. That is the full time. Finish when you are ready."
    }
}
