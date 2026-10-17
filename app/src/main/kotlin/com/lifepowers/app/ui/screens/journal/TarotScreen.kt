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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepowers.app.domain.TarotPull
import com.lifepowers.app.ui.components.BodyLine
import com.lifepowers.app.ui.components.CloudPresets
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.components.TierDot
import com.lifepowers.app.ui.components.WrapRow
import com.lifepowers.app.ui.theme.BodyText
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.LightPink
import com.lifepowers.core.Tarot
import com.lifepowers.core.Tier
import java.time.LocalDate

/** The tarot log: a descriptive mirror of what keeps turning up, then every pull, newest first. */
@Composable
fun TarotScreen(onNavigate: (String) -> Unit, onBack: () -> Unit, viewModel: TarotViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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

            JournalTitle(title = "Tarot log") {
                PillButton(
                    text = "Pull a card", onClick = { onNavigate(JournalRoutes.pull(0)) },
                    background = LightPink, shadowColor = HotPink, shadowAlpha = 0.18f,
                    fontSize = 14.sp, fontWeight = FontWeight.Black,
                )
            }

            Column(modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                CardTitle(text = "What keeps turning up", fontSize = 18.sp)
                MutedLine(text = "A mirror for reflection, not a prediction.", fontSize = 13.sp)
            }

            if (state.loaded && state.mirror.isEmpty()) {
                SoftCard(modifier = Modifier.fillMaxWidth(), gap = 4.dp) {
                    BodyLine(text = "No card has turned up more than once this year.", fontSize = 14.sp)
                    MutedLine(text = "When one does, it shows here with the phases and kinds of days it came on.")
                }
            }
            for (card in state.mirror) MirrorCardView(card)

            CardTitle(text = "Every pull", fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp))
            if (state.loaded && state.pulls.isEmpty()) {
                SoftCard(modifier = Modifier.fillMaxWidth(), gap = 4.dp) {
                    BodyLine(text = "No cards pulled in the last year.", fontSize = 14.sp)
                    MutedLine(text = "Pull a card to start the log. It stays on this phone.")
                }
            }
            for (pull in state.pulls) {
                PullRow(pull = pull, today = state.today, onOpen = { onNavigate(JournalRoutes.pull(pull.id)) })
            }
        }
    }
}

@Composable
private fun MirrorCardView(card: MirrorCard) {
    val c = card.count
    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 6.dp) {
        CardTitle(text = c.card, fontSize = 17.sp)
        BodyLine(text = countLine(c), fontSize = 14.sp, lineHeight = 20.sp)
        val phases = phaseLine(c.byPhase)
        if (phases != null) BodyLine(text = phases, fontSize = 13.sp, lineHeight = 19.sp)
        if (card.days.isNotEmpty()) {
            WrapRow(modifier = Modifier.padding(top = 2.dp), horizontalGap = 5.dp, verticalGap = 5.dp) {
                for ((_, tier) in card.days.takeLast(MAX_DOTS)) TierDot(tier = tier, size = 14.dp)
            }
        }
        val tiers = tierLine(c.byTier)
        if (tiers != null) MutedLine(text = tiers)
    }
}

@Composable
private fun PullRow(pull: TarotPull, today: LocalDate, onOpen: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 4.dp, onClick = onOpen) {
        CardTitle(text = cardLine(pull.card, pull.reversed))
        val details = listOfNotNull(
            JournalDates.short(pull.localDate, today),
            pull.spread?.takeIf { it.isNotBlank() },
            pull.position?.takeIf { it.isNotBlank() },
        )
        MutedLine(text = details.joinToString(", "))
        val note = pull.note
        if (!note.isNullOrBlank()) {
            Text(
                text = note, color = BodyText, fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val MAX_DOTS = 30

/** "Turned up 4 times, twice reversed." */
private fun countLine(c: Tarot.CardCount): String {
    val orientation = when {
        c.reversed == 0 -> "always upright"
        c.reversed == c.count -> if (c.count == 2) "reversed both times" else "reversed every time"
        else -> "${timesWord(c.reversed)} reversed"
    }
    return "Turned up ${timesWord(c.count)}, $orientation."
}

/** "In the luteal phase 3 times and the follicular phase once." Null when no phase was known. */
private fun phaseLine(byPhase: Map<String, Int>): String? {
    if (byPhase.isEmpty()) return null
    val parts = byPhase.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { "${it.key} ${timesWord(it.value)}" }
    return "In ${joinWords(parts)}."
}

/** "On best days twice and softer days once." Null when no tier was recorded. */
private fun tierLine(byTier: Map<Tier, Int>): String? {
    val parts = Tier.values()
        .filter { (byTier[it] ?: 0) > 0 }
        .map { "${tierDays(it)} ${timesWord(byTier[it] ?: 0)}" }
    if (parts.isEmpty()) return null
    return "On ${joinWords(parts)}."
}

private fun tierDays(tier: Tier): String = when (tier) {
    Tier.BEST -> "best days"
    Tier.GOOD -> "good days"
    Tier.NOT_GREAT -> "softer days"
    Tier.LIKELY_HARD -> "go gently days"
    Tier.UNKNOWN -> "days without enough data"
}
