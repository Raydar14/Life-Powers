package com.lifepowers.app.ui.screens.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifepowers.app.domain.AssistantPreview
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.theme.BodyText
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.app.ui.theme.White

/**
 * Shown before every assistant call: exactly what will leave the phone, and what never does.
 * Nothing is sent until the person taps Send.
 */
@Composable
fun AssistantPreviewDialog(
    preview: AssistantPreview,
    sending: Boolean,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!sending) onCancel() },
        confirmButton = {
            if (sending) {
                Text(text = "Sending", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Black)
            } else {
                TextLink(text = "Send", onClick = onSend, fontSize = 14.sp)
            }
        },
        dismissButton = {
            if (!sending) TextLink(text = "Not now", onClick = onCancel, fontSize = 14.sp)
        },
        title = { Text(text = preview.title, color = Ink, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GroupTitle("What will be sent to Claude")
                for (line in preview.sends) PlainLine(line)
                if (preview.neverSends.isNotEmpty()) {
                    GroupTitle("What is never sent", top = true)
                    for (line in preview.neverSends) PlainLine(line)
                }
                MutedLine(
                    text = "It goes to Anthropic's Claude with your key, once, and the answer comes back here. A note of what was sent is kept on this phone.",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = White,
    )
}

@Composable
private fun GroupTitle(text: String, top: Boolean = false) {
    Text(
        text = text,
        color = Ink,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.fillMaxWidth().padding(top = if (top) 8.dp else 0.dp),
    )
}

@Composable
private fun PlainLine(text: String) {
    Text(text = text, color = BodyText, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
}
