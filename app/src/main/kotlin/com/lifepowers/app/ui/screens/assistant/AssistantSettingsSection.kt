package com.lifepowers.app.ui.screens.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepowers.app.domain.AssistantFeatures
import com.lifepowers.app.ui.components.BodyLine
import com.lifepowers.app.ui.components.CardTitle
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.PinkSwitch
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.theme.HairLine
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.app.ui.theme.White

/** The plain name and what each feature sends, shown beside its switch. */
internal object AssistantFeatureWords {
    fun name(feature: String): String = when (feature) {
        AssistantFeatures.CARD -> "Interpret tarot cards"
        AssistantFeatures.PHOTO -> "Ask about photos"
        AssistantFeatures.CHECKIN -> "Richer check-in ratings"
        AssistantFeatures.NARRATIVE -> "Weekly note"
        else -> feature
    }

    fun sends(feature: String): String = when (feature) {
        AssistantFeatures.CARD -> "Sends the card, upright or reversed, its spread and position, and your note only when you choose. Never health data."
        AssistantFeatures.PHOTO -> "Sends one photo at a time, only when you tap, without its location. Never other photos or journal text."
        AssistantFeatures.CHECKIN -> "Sends the words of one check-in. Never the audio or voice measurements."
        AssistantFeatures.NARRATIVE -> "Sends your finding sentences and goal summaries. Never raw measurements."
        else -> ""
    }
}

/**
 * The assistant section of Settings: the master switch, the key, and a switch per feature.
 * Off means every assistant button in the app disappears.
 */
@Composable
fun AssistantSettingsSection(
    modifier: Modifier = Modifier,
    viewModel: AssistantSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Held only in memory, never in saved state, and cleared once saved.
    var draft by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SoftCard(modifier = Modifier.fillMaxWidth(), gap = 4.dp) {
            SwitchRow(
                title = "Assistant features",
                line = "Off means every assistant button disappears.",
                checked = state.master,
                onChange = { viewModel.setMaster(it) },
            )
            if (!state.master) {
                MutedLine(text = "Nothing is sent anywhere while this is off, and nothing waits to be sent later.")
            }
        }

        SoftCard(modifier = Modifier.fillMaxWidth(), gap = 8.dp) {
            CardTitle(text = "Your Claude API key")
            MutedLine(text = "The key stays encrypted on this phone and is used only for calls you confirm.")
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = if (state.hasKey) "A key is saved. Paste a new one to replace it." else "Paste your key",
                        color = Muted,
                        fontSize = 13.sp,
                    )
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(999.dp),
                textStyle = TextStyle(color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.saveKey(draft) { ok -> if (ok) draft = "" } }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HotPink,
                    unfocusedBorderColor = HairLine,
                    cursorColor = HotPink,
                    focusedContainerColor = White,
                    unfocusedContainerColor = White,
                    focusedTextColor = Ink,
                    unfocusedTextColor = Ink,
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton(
                    text = if (state.savingKey) "Saving" else "Save",
                    onClick = { viewModel.saveKey(draft) { ok -> if (ok) draft = "" } },
                    background = Ink,
                    textColor = White,
                    fontWeight = FontWeight.Black,
                    shadowAlpha = 0f,
                    elevation = 0.dp,
                    enabled = draft.isNotBlank() && !state.savingKey,
                )
                if (state.hasKey) TextLink(text = "Remove", onClick = { viewModel.removeKey() }, fontSize = 13.sp)
            }
            MutedLine(text = if (state.hasKey) "A key is saved." else "No key saved yet.")
            state.notice?.let { BodyLine(text = it, color = Ink) }
        }

        if (state.master) {
            SoftCard(modifier = Modifier.fillMaxWidth(), gap = 12.dp) {
                CardTitle(text = "Each feature")
                for (feature in AssistantFeatures.ALL) {
                    SwitchRow(
                        title = AssistantFeatureWords.name(feature),
                        line = AssistantFeatureWords.sends(feature),
                        checked = state.features[feature] == true,
                        onChange = { viewModel.setFeature(feature, it) },
                    )
                }
                MutedLine(text = "Every call shows what it will send and waits for you to tap Send.")
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, line: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).padding(end = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            CardTitle(text = title)
            MutedLine(text = line)
        }
        PinkSwitch(checked = checked, onChange = onChange)
    }
}
