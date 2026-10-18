package com.lifepowers.app.ui.screens.journal

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepowers.app.ui.components.BodyLine
import com.lifepowers.app.ui.components.Chip
import com.lifepowers.app.ui.components.CloudPresets
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.PrimaryButton
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.components.WrapRow
import com.lifepowers.app.ui.screens.assistant.AssistantPreviewDialog
import com.lifepowers.app.ui.theme.DeepLavenderLine
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.Lavender
import com.lifepowers.app.ui.theme.LightPink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.core.Tarot

private const val MAJOR_GROUP = "Major arcana"

/**
 * Record one card. pullId 0 is a new pull for today, attached to [entryId] when it is not 0.
 * The app names cards only; what a card means is the person's to write.
 */
@Composable
fun TarotPullScreen(
    pullId: Long,
    entryId: Long,
    onBack: () -> Unit,
    viewModel: TarotPullViewModel = hiltViewModel(),
) {
    LaunchedEffect(pullId, entryId) { viewModel.load(pullId, entryId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form = state.form
    LaunchedEffect(state.closed) { if (state.closed) onBack() }

    var confirmDelete by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Clouds(CloudPresets.journal)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextLink(text = "Back", onClick = onBack, fontSize = 13.sp)
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                JournalTitle(title = if (pullId == 0L) "Pull a card" else "Your pull")
                val whenLine = JournalDates.day(form.localDate)
                MutedLine(text = if (form.entryId != null) "$whenLine, kept with a journal entry" else whenLine, fontSize = 13.sp)
            }

            if (state.loaded && state.missing) {
                SoftCard(modifier = Modifier.fillMaxWidth(), gap = 4.dp) {
                    CardTitle("This pull is not here any more")
                    MutedLine(text = "It may have been deleted. Go back to start a new one.")
                }
            } else if (state.loaded) {
                PullBody(state = state, viewModel = viewModel, onDelete = { confirmDelete = true })
            }
        }
    }

    state.assistPreview?.let { preview ->
        AssistantPreviewDialog(
            preview = preview,
            sending = state.assistSending,
            onSend = { viewModel.sendToAssistant() },
            onCancel = { viewModel.cancelAssistant() },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this pull?",
            body = "The card and your note are deleted from the phone.",
            confirm = "Delete",
            onConfirm = { confirmDelete = false; viewModel.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun PullBody(state: PullUiState, viewModel: TarotPullViewModel, onDelete: () -> Unit) {
    val form = state.form

    // The chosen card and its orientation.
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        color = if (form.card.isBlank()) com.lifepowers.app.ui.theme.White else Lavender,
        shadowColor = if (form.card.isBlank()) Ink else DeepLavenderLine,
        gap = 10.dp,
    ) {
        if (form.card.isBlank()) {
            CardTitle("Which card did you draw?", fontSize = 18.sp)
            MutedLine(text = "Search or browse below to choose it.")
        } else {
            Text(text = form.card, color = Ink, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(text = "Upright", selected = !form.reversed, onClick = { viewModel.setReversed(false) })
            Chip(text = "Reversed", selected = form.reversed, onClick = { viewModel.setReversed(true) })
        }
    }

    CardPicker(selected = form.card, onPick = { viewModel.setCard(it) })

    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
        CardTitle("Spread")
        WrapRow {
            for (spread in Tarot.SPREADS) {
                Chip(
                    text = spread,
                    background = if (form.spread == spread) LightPink else com.lifepowers.app.ui.theme.Ground,
                    onClick = { viewModel.setSpread(if (form.spread == spread) "" else spread) },
                    fontSize = 12.sp,
                )
            }
        }
        PullField(value = form.spread, onChange = { viewModel.setSpread(it) }, placeholder = "Or name your own spread")
        PullField(value = form.position, onChange = { viewModel.setPosition(it) }, placeholder = "Position, for example past")
        PullField(value = form.deck, onChange = { viewModel.setDeck(it) }, placeholder = "Deck name")
        MutedLine(text = "The deck name carries over from your last pull.")
    }

    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 8.dp) {
        CardTitle("What it meant to you")
        OutlinedTextField(
            value = form.note,
            onValueChange = { viewModel.setNote(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(text = "In your own words", color = Muted, fontSize = 14.sp) },
            minLines = 4,
            shape = RoundedCornerShape(20.dp),
            textStyle = TextStyle(color = Ink, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = softFieldColors(),
        )
    }

    val interpretation = form.interpretation
    if (!interpretation.isNullOrBlank()) {
        SoftCard(modifier = Modifier.fillMaxWidth(), color = Lavender.copy(alpha = 0.35f), shadowColor = DeepLavenderLine, shadowAlpha = 0.14f, gap = 6.dp) {
            CardTitle(if (form.interpretationFromAssistant) "From the assistant" else "Meaning")
            BodyLine(text = interpretation, color = Ink, fontSize = 15.sp, lineHeight = 22.sp)
            if (form.interpretationFromAssistant) MutedLine(text = "Traditional meanings and prompts for reflection, not a prediction.")
        }
    }

    if (state.cardAssist && form.id != 0L && form.card.isNotBlank()) {
        AssistantCardAsk(state = state, viewModel = viewModel)
    }

    state.notice?.let { notice ->
        SoftCard(modifier = Modifier.fillMaxWidth(), color = LightPink, shadowColor = HotPink, shadowAlpha = 0.14f, gap = 2.dp) {
            BodyLine(text = notice, color = Ink, fontSize = 13.sp)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PrimaryButton(
            text = if (state.saving) "Saving" else "Save",
            onClick = { viewModel.save() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.saving,
        )
        if (form.id != 0L) {
            TextLink(text = "Delete this pull", onClick = onDelete, fontSize = 13.sp)
        }
    }
}

/** The assistant button for a saved pull. Shown only while the card feature is available. */
@Composable
private fun AssistantCardAsk(state: PullUiState, viewModel: TarotPullViewModel) {
    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                CardTitle("Include my note")
                MutedLine(text = if (state.form.note.isBlank()) "There is no note on this pull yet." else "Your note goes with the card only when this is on.")
            }
            SoftSwitch(checked = state.includeNote, onChange = { viewModel.setIncludeNote(it) })
        }
        val tier = state.dayTier
        if (tier != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    CardTitle("Include today's forecast color")
                    MutedLine(text = "Today's color is \"$tier\". Only that word is sent.")
                }
                SoftSwitch(checked = state.includeTier, onChange = { viewModel.setIncludeTier(it) })
            }
        }
        PillButton(
            text = if (state.assistSending) "Asking" else "Ask the assistant about this card",
            onClick = { viewModel.askAssistant() },
            modifier = Modifier.fillMaxWidth(),
            background = LightPink,
            shadowColor = HotPink,
            shadowAlpha = 0.16f,
            enabled = !state.assistSending,
        )
        MutedLine(text = "You see exactly what will be sent before anything leaves the phone.")
        state.assistError?.let { BodyLine(text = it, color = Ink) }
    }
}

/** Search with live results, plus browse pills for the major arcana and each suit. */
@Composable
private fun CardPicker(selected: String, onPick: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf<String?>(null) }

    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
        CardTitle("Find the card")
        PullField(value = query, onChange = { query = it }, placeholder = "Search, for example star or cups", capitalize = false)

        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            val results = Tarot.search(trimmed)
            WrapRow {
                for (card in results) {
                    Chip(text = card, selected = card == selected, onClick = { onPick(card); query = "" })
                }
                val exact = Tarot.DECK.any { it.equals(trimmed, ignoreCase = true) }
                if (!exact) {
                    Chip(text = "Use “$trimmed”", background = LightPink, onClick = { onPick(trimmed); query = "" })
                }
            }
            if (results.isEmpty()) MutedLine(text = "No card by that name in the standard deck. You can still use your own.")
        }

        WrapRow {
            for (name in listOf(MAJOR_GROUP) + Tarot.SUITS) {
                Chip(
                    text = name,
                    background = if (group == name) LightPink else com.lifepowers.app.ui.theme.Ground,
                    onClick = { group = if (group == name) null else name },
                )
            }
        }

        val open = group
        if (open != null) {
            val cards: List<Pair<String, String>> = if (open == MAJOR_GROUP) {
                Tarot.MAJOR.map { it to it }
            } else {
                Tarot.RANKS.map { rank -> "$rank of $open" to rank }
            }
            WrapRow {
                for ((card, label) in cards) {
                    Chip(text = label, selected = card == selected, onClick = { onPick(card) }, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PullField(value: String, onChange: (String) -> Unit, placeholder: String, capitalize: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(text = placeholder, color = Muted, fontSize = 13.sp) },
        singleLine = true,
        shape = RoundedCornerShape(999.dp),
        textStyle = TextStyle(color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        keyboardOptions = KeyboardOptions(
            capitalization = if (capitalize) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
            imeAction = ImeAction.Done,
        ),
        colors = softFieldColors(),
    )
}
