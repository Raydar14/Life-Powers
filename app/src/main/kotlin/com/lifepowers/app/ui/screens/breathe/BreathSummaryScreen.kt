package com.lifepowers.app.ui.screens.breathe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepowers.app.domain.BreathSessionRecord
import com.lifepowers.app.ui.components.BodyLine
import com.lifepowers.app.ui.components.CardTitle
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.Fmt
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.PrimaryButton
import com.lifepowers.app.ui.components.ScreenHeader
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.Muted

@Composable
fun BreathSummaryScreen(
    sessionId: Long,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: BreathSummaryViewModel = hiltViewModel(),
) {
    LaunchedEffect(sessionId) { viewModel.load(sessionId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val started by viewModel.started.collectAsStateWithLifecycle()
    LaunchedEffect(started) {
        if (started) {
            viewModel.consumeStarted()
            onNavigate(BreatheRoutes.SESSION)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Clouds(BreatheClouds.summary)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val record = state.record
            if (!state.loaded) {
                ScreenHeader(title = "Your session", titleSize = 28.sp)
                MutedLine(text = "Opening", fontSize = 13.sp)
                return@Column
            }
            if (record == null) {
                ScreenHeader(title = "Your session", titleSize = 28.sp)
                BodyLine(text = "This session is no longer saved.")
                PillButton(text = "Done", onClick = onBack, shadowAlpha = 0.08f)
                return@Column
            }

            ScreenHeader(
                title = record.patternName,
                titleSize = 28.sp,
                subline = BreathWords.startedAt(record.startUtcMs, record.zoneOffsetMin),
            )

            val durationMs = (record.endUtcMs - record.startUtcMs).coerceAtLeast(0L)
            SoftCard(modifier = Modifier.fillMaxWidth(), gap = 6.dp) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val (num, unit) = bigDuration(durationMs)
                    Text(text = num, color = HotPink, fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(text = unit, color = Muted, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 8.dp))
                }
                Text(text = record.pattern.describe(), color = Ink, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.ExtraBold)
                MutedLine(text = BreathWords.pace(record.pattern))
                MutedLine(text = if (record.sensor != null) "With ${record.sensor}." else "No heart sensor this time.")
            }

            val hasBio = record.sensor != null && record.summary.readings > 0
            if (hasBio) {
                SummaryHeartCard(record)
                TimelineCard(record)
            } else {
                NoSensorCard(record, durationMs)
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    text = "Breathe again",
                    onClick = { viewModel.breatheAgain() },
                    modifier = Modifier.weight(1f),
                    enabled = state.canBreatheAgain,
                )
                PillButton(text = "Done", onClick = onBack, modifier = Modifier.weight(1f), fontSize = 14.sp, shadowAlpha = 0.1f)
            }
            if (state.sessionRunning) {
                MutedLine(text = "A session is running. Finish it before breathing again.", modifier = Modifier.padding(horizontal = 6.dp))
            }
        }
    }
}

@Composable
private fun SummaryHeartCard(record: BreathSessionRecord) {
    val s = record.summary
    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 8.dp) {
        CardTitle(text = "Your heart")
        val start = s.startBpm
        val end = s.endBpm
        if (start != null && end != null) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${Fmt.num(start)} to ${Fmt.num(end)}",
                    color = HotPink, fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black, maxLines = 1,
                )
                Text(text = "bpm", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 6.dp))
            }
            BodyLine(text = BreathWords.hrChangeLine(start, end))
        } else if (end != null || start != null) {
            BodyLine(text = "Heart rate around ${Fmt.num(end ?: start)} bpm.")
        }
        val r0 = s.startRmssdMs
        val r1 = s.endRmssdMs
        when {
            r0 != null && r1 != null -> BodyLine(text = "Beat to beat variation went from ${Fmt.num(r0)} to ${Fmt.num(r1)} ms.")
            r1 != null -> BodyLine(text = "Beat to beat variation ended at ${Fmt.num(r1)} ms.")
            r0 != null -> BodyLine(text = "Beat to beat variation started at ${Fmt.num(r0)} ms.")
        }
        val swing = s.bestSwingBpm
        if (swing != null) BodyLine(text = "Your biggest swing was ${Fmt.num(swing)} bpm within a breath.")
        val sync = s.meanSync
        if (sync != null) BodyLine(text = "Across the session, your heart was ${BreathWords.sync(sync)}.")
        if (r0 == null && r1 == null) {
            MutedLine(text = "Beat to beat variation needs a sensor that sends each beat, as most chest straps do.")
        }
    }
}

@Composable
private fun TimelineCard(record: BreathSessionRecord) {
    val values = record.hrTimeline
    if (values.count { it != null && !it.isNaN() } < 2) return
    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 8.dp) {
        CardTitle(text = "Heart rate through the session")
        HeartLine(values = values, modifier = Modifier.fillMaxWidth().height(120.dp))
        val range = rangeLine(values)
        MutedLine(text = if (range != null) "$range, every five seconds." else "Every five seconds.")
    }
}

@Composable
private fun NoSensorCard(record: BreathSessionRecord, durationMs: Long) {
    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 8.dp) {
        CardTitle(text = "What you did")
        val minutes = BreathWords.minutesOf(durationMs)
        val span = if (minutes == 1) "a minute" else "$minutes minutes"
        BodyLine(text = "You breathed for $span at ${record.pattern.describe()}, about ${BreathWords.pace(record.pattern)}.")
        if (record.sensor != null) {
            BodyLine(text = "${record.sensor} was connected but sent no readings this time.")
        }
        MutedLine(text = "Next time, connect a heart sensor on the Breathe screen to see how your heart answers your breath.")
    }
}

/** "12" and "minutes", or "45" and "seconds" for short sessions. */
private fun bigDuration(ms: Long): Pair<String, String> {
    val s = ms / 1000
    return if (s < 90) {
        s.toString() to if (s == 1L) "second" else "seconds"
    } else {
        val m = BreathWords.minutesOf(ms)
        m.toString() to "minutes"
    }
}
