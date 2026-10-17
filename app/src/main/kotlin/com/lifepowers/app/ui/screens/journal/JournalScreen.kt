package com.lifepowers.app.ui.screens.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepowers.app.domain.BehaviorEvent
import com.lifepowers.app.domain.EventKinds
import com.lifepowers.app.domain.JournalEntry
import com.lifepowers.app.domain.Photo
import com.lifepowers.app.ui.components.BodyLine
import com.lifepowers.app.ui.components.Chip
import com.lifepowers.app.ui.components.CloudPresets
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.Fmt
import com.lifepowers.app.ui.components.HotButton
import com.lifepowers.app.ui.components.InkButton
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.PrimaryButton
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.components.WrapRow
import com.lifepowers.app.ui.components.dashedBorder
import com.lifepowers.app.ui.navigation.Routes
import com.lifepowers.app.ui.theme.BodyText
import com.lifepowers.app.ui.theme.HairLine
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.Lavender
import com.lifepowers.app.ui.theme.LightPink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.core.SymptomParser
import java.time.LocalDate

/** What is open inline inside the logging cards. */
private sealed interface Sheet {
    data class Food(val kind: String, val label: String) : Sheet
    data class Symptom(val kind: String) : Sheet
    data class Behavior(val kind: String, val label: String) : Sheet
}

private const val ENTRIES_SHOWN = 5

@Composable
fun JournalScreen(onNavigate: (String) -> Unit, viewModel: JournalViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var showAll by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Clouds(CloudPresets.journal)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            JournalTitle(title = "Journal") {
                HotButton(text = "Check in", onClick = { onNavigate(Routes.CHECKIN) })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(text = "Write or talk", onClick = { onNavigate(JournalRoutes.entry(0)) }, modifier = Modifier.weight(1f))
                PillButton(
                    text = "Pull a card", onClick = { onNavigate(JournalRoutes.pull(0)) },
                    background = LightPink, shadowColor = HotPink, shadowAlpha = 0.18f,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextLink(text = "Tarot log", onClick = { onNavigate(JournalRoutes.TAROT) }, fontSize = 13.sp)
            }

            val current = sheet

            FoodCard(
                cravingCount = state.cravingCount,
                open = current as? Sheet.Food,
                onPick = { kind, label -> sheet = if ((current as? Sheet.Food)?.kind == kind) null else Sheet.Food(kind, label) },
                onSave = { kind, intensity -> viewModel.add(kind, quantity = intensity.toDouble(), unit = "intensity"); sheet = null },
            )

            RecentEntries(
                entries = state.entries,
                loaded = state.loaded,
                today = state.today,
                showAll = showAll,
                onToggleAll = { showAll = !showAll },
                thumb = { photo -> viewModel.thumb(photo) },
                onOpen = { id -> onNavigate(JournalRoutes.entry(id)) },
            )

            SymptomCard(
                open = current as? Sheet.Symptom,
                onPick = { kind -> sheet = if ((current as? Sheet.Symptom)?.kind == kind) null else Sheet.Symptom(kind) },
                onSave = { kind, severity -> viewModel.add(EventKinds.symptom(kind), quantity = severity.toDouble(), unit = "severity"); sheet = null },
            )

            BehaviorCard(
                open = current as? Sheet.Behavior,
                onPick = { kind, label -> sheet = if ((current as? Sheet.Behavior)?.kind == kind) null else Sheet.Behavior(kind, label) },
                onSave = { kind -> viewModel.add(kind, quantity = 1.0); sheet = null },
            )

            TodayEvents(events = state.events, onDelete = { viewModel.delete(it) })
        }
    }
}

@Composable
private fun RecentEntries(
    entries: List<JournalEntry>,
    loaded: Boolean,
    today: LocalDate,
    showAll: Boolean,
    onToggleAll: () -> Unit,
    thumb: suspend (Photo) -> ImageBitmap?,
    onOpen: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        CardTitle(text = "Recent entries", fontSize = 18.sp, modifier = Modifier.padding(horizontal = 4.dp))
        if (loaded && entries.isEmpty()) {
            SoftCard(modifier = Modifier.fillMaxWidth(), gap = 4.dp) {
                BodyLine(text = "Nothing written in the last 30 days.", fontSize = 14.sp)
                MutedLine(text = "Write a line or say a few words whenever you like. Entries stay on this phone.")
            }
        }
        val shown = if (showAll) entries else entries.take(ENTRIES_SHOWN)
        for (entry in shown) {
            EntryCard(entry = entry, today = today, thumb = thumb, onOpen = { onOpen(entry.id) })
        }
        if (entries.size > ENTRIES_SHOWN) {
            TextLink(
                text = if (showAll) "Show fewer" else "Show all ${entries.size} from the last 30 days",
                onClick = onToggleAll,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun EntryCard(entry: JournalEntry, today: LocalDate, thumb: suspend (Photo) -> ImageBitmap?, onOpen: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 8.dp, onClick = onOpen) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CardTitle(text = JournalDates.day(entry.localDate, today), modifier = Modifier.weight(1f))
            Text(text = Fmt.timeAt(entry.createdUtcMs, entry.zoneOffsetMin), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        val body = listOf(entry.text, entry.voiceTranscript.orEmpty())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        if (body.isNotEmpty()) {
            Text(
                text = body, color = BodyText, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        } else if (entry.photos.isEmpty() && entry.pulls.isEmpty()) {
            MutedLine(text = "An empty page, open it to write.")
        }
        if (entry.photos.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                for (photo in entry.photos.take(4)) {
                    PhotoTile(photo = photo, load = thumb, modifier = Modifier.size(64.dp), radius = 18.dp)
                }
                val more = entry.photos.size - 4
                if (more > 0) Text(text = "and $more more", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (entry.pulls.isNotEmpty()) {
            WrapRow {
                for (pull in entry.pulls) {
                    Chip(text = cardLine(pull.card, pull.reversed), background = Lavender, fontSize = 12.sp)
                }
            }
        }
        if (entry.tags.isNotEmpty()) {
            MutedLine(text = entry.tags.joinToString(", "))
        }
    }
}

@Composable
private fun IntensityRow(label: String, value: Int, onChange: (Int) -> Unit, onSave: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Text(text = "$value of 5", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(1, 5)) },
            valueRange = 1f..5f,
            steps = 3,
            colors = SliderDefaults.colors(thumbColor = HotPink, activeTrackColor = HotPink, inactiveTrackColor = HairLine, activeTickColor = HotPink, inactiveTickColor = HairLine),
        )
        InkButton(text = "Save", onClick = onSave)
    }
}

@Composable
private fun FoodCard(cravingCount: Int, open: Sheet.Food?, onPick: (String, String) -> Unit, onSave: (String, Int) -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 24.dp, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp), gap = 10.dp) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CardTitle("Food, no judgment")
            Text(
                text = if (cravingCount < 14) "pattern forming, $cravingCount of 14" else "$cravingCount logged",
                color = Muted, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.dashedBorder(cornerRadius = 999.dp).padding(horizontal = 9.dp, vertical = 4.dp),
            )
        }
        val options = listOf(EventKinds.CRAVING to "Craving", EventKinds.URGE_TO_BINGE to "Urge to binge", EventKinds.BINGE to "Binge")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((kind, label) in options) {
                Chip(
                    text = label, onClick = { onPick(kind, label) },
                    background = if (open?.kind == kind) LightPink else com.lifepowers.app.ui.theme.Ground,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (open != null) {
            var intensity by remember(open.kind) { mutableStateOf(3) }
            IntensityRow(label = "${open.label}, how strong", value = intensity, onChange = { intensity = it }, onSave = { onSave(open.kind, intensity) })
        } else {
            MutedLine(text = "One tap, a slide for how strong, done. Later, if you want, what helped.")
        }
    }
}

@Composable
private fun SymptomCard(open: Sheet.Symptom?, onPick: (String) -> Unit, onSave: (String, Int) -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 24.dp, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp), gap = 10.dp) {
        CardTitle("Symptoms, tap one")
        WrapRow {
            for (kind in SymptomParser.catalog.keys) {
                Chip(text = Fmt.kindName(kind), selected = open?.kind == kind, onClick = { onPick(kind) })
            }
        }
        if (open != null) {
            var severity by remember(open.kind) { mutableStateOf(3) }
            IntensityRow(label = "${Fmt.kindName(open.kind)}, how bad", value = severity, onChange = { severity = it }, onSave = { onSave(open.kind, severity) })
        }
    }
}

@Composable
private fun BehaviorCard(open: Sheet.Behavior?, onPick: (String, String) -> Unit, onSave: (String) -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 24.dp, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp), gap = 10.dp) {
        CardTitle("Things you did")
        WrapRow {
            for ((kind, label) in Fmt.behaviorKinds) {
                Chip(text = label, background = if (open?.kind == kind) LightPink else com.lifepowers.app.ui.theme.Ground, onClick = { onPick(kind, label) })
            }
        }
        if (open != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "${open.label}, one", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                InkButton(text = "Save", onClick = { onSave(open.kind) })
            }
        }
    }
}

@Composable
private fun TodayEvents(events: List<BehaviorEvent>, onDelete: (Long) -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 24.dp, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp), gap = 8.dp) {
        CardTitle("Today so far")
        if (events.isEmpty()) {
            MutedLine(text = "Nothing logged yet. A missed day is a missing value, not a penalty.")
        }
        for (e in events) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = Fmt.kindName(e.kind), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                        val q = e.quantity
                        if (q != null && e.kind != EventKinds.JOURNAL) {
                            Text(text = if (e.unit != null) "${Fmt.num(q)} ${e.unit}" else Fmt.num(q), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    val note = e.note
                    if (!note.isNullOrBlank()) BodyLine(text = note, fontSize = 13.sp, lineHeight = 18.sp)
                    Text(text = Fmt.timeAt(e.startUtcMs, e.zoneOffsetMin), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                TextLink(text = "Remove", onClick = { onDelete(e.id) })
            }
        }
    }
}
