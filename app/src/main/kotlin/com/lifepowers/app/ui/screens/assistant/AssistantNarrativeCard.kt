package com.lifepowers.app.ui.screens.assistant

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepowers.app.ui.components.BodyLine
import com.lifepowers.app.ui.components.CardTitle
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.theme.DeepLavenderLine
import com.lifepowers.app.ui.theme.DeepPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.Lavender
import com.lifepowers.app.ui.theme.White

/**
 * A soft card that writes a short note on the week from finding sentences and goal summaries only.
 * Renders nothing unless the weekly note feature is available (master on, feature on, key saved).
 */
@Composable
fun AssistantNarrativeCard(
    findings: List<String>,
    goals: List<String>,
    modifier: Modifier = Modifier,
    viewModel: AssistantNarrativeViewModel = hiltViewModel(),
) {
    val available by viewModel.available.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!available) return
    val hasSomething = findings.any { it.isNotBlank() } || goals.any { it.isNotBlank() }
    if (!hasSomething && state.text == null) return

    SoftCard(
        modifier = modifier.fillMaxWidth(),
        color = Lavender.copy(alpha = 0.35f),
        shadowColor = DeepLavenderLine,
        shadowAlpha = 0.14f,
        gap = 8.dp,
    ) {
        CardTitle(text = "This week's note")
        val text = state.text
        if (text == null) {
            MutedLine(text = "A short note written from your findings and goals. You see what is sent before it goes.")
            PillButton(
                text = "Write this week's note",
                onClick = { viewModel.ask(findings, goals) },
                background = DeepPink,
                textColor = White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                shadowColor = DeepPink,
                shadowAlpha = 0.3f,
                enabled = hasSomething && !state.sending,
            )
        } else {
            BodyLine(text = text, color = Ink, fontSize = 15.sp, lineHeight = 22.sp)
            MutedLine(text = "Written by the assistant from your findings")
            if (hasSomething) TextLink(text = "Write it again", onClick = { viewModel.ask(findings, goals) }, fontSize = 13.sp)
        }
        state.error?.let { BodyLine(text = it, color = Ink) }
    }

    state.preview?.let { preview ->
        AssistantPreviewDialog(
            preview = preview,
            sending = state.sending,
            onSend = { viewModel.send() },
            onCancel = { viewModel.cancel() },
        )
    }
}
