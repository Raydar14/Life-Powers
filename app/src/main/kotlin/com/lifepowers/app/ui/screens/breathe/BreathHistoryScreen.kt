package com.lifepowers.app.ui.screens.breathe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.lifepowers.app.ui.components.DashedCard
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.ScreenHeader
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.theme.Ink

@Composable
fun BreathHistoryScreen(onNavigate: (String) -> Unit, onBack: () -> Unit, viewModel: BreathHistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<BreathSessionRecord?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Clouds(BreatheClouds.home)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val count = state.sessions.size
            ScreenHeader(
                title = "Past sessions",
                titleSize = 28.sp,
                subline = when {
                    !state.loaded -> "Opening"
                    count == 0 -> "Nothing saved yet"
                    count == 1 -> "1 session"
                    else -> "$count sessions"
                },
            ) {
                TextLink(text = "Back", onClick = onBack)
            }

            if (state.loaded && count == 0) {
                DashedCard(modifier = Modifier.fillMaxWidth()) {
                    BodyLine(text = "Sessions you finish and save show here, with what your heart did when a sensor was connected.")
                }
            }

            for (record in state.sessions) {
                SessionCard(
                    record = record,
                    onOpen = { onNavigate(BreatheRoutes.summary(record.id)) },
                    onDelete = { toDelete = record },
                )
            }
        }
    }

    val deleting = toDelete
    if (deleting != null) {
        BreathConfirmDialog(
            title = "Delete this session?",
            body = "${deleting.patternName}, ${BreathWords.startedAt(deleting.startUtcMs, deleting.zoneOffsetMin)}. This cannot be undone.",
            confirmText = "Delete",
            onConfirm = {
                viewModel.delete(deleting.id)
                toDelete = null
            },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun SessionCard(record: BreathSessionRecord, onOpen: () -> Unit, onDelete: () -> Unit) {
    val durationMs = (record.endUtcMs - record.startUtcMs).coerceAtLeast(0L)
    val s = record.summary
    val start = s.startBpm
    val end = s.endBpm
    val heart = if (start != null && end != null) BreathWords.hrChange(start, end) else null
    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 4.dp, onClick = onOpen) {
        CardTitle(text = BreathWords.startedAt(record.startUtcMs, record.zoneOffsetMin))
        Text(
            text = "${record.patternName}, ${record.pattern.describe()}",
            color = Ink, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.ExtraBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val line = if (heart != null) "${BreathWords.spent(durationMs)}. Heart rate $heart." else BreathWords.spent(durationMs) + "."
            MutedLine(text = line, fontSize = 13.sp, modifier = Modifier.weight(1f))
            PillButton(text = "Delete", onClick = onDelete, shadowAlpha = 0.06f, fontSize = 12.sp)
        }
    }
}
