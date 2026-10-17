package com.lifepowers.app.ui.screens.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.lifepowers.app.domain.BehaviorEvent
import com.lifepowers.app.domain.EventKinds
import com.lifepowers.app.ui.components.BodyLine
import com.lifepowers.app.ui.components.Chip
import com.lifepowers.app.ui.components.CloudPresets
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.Fmt
import com.lifepowers.app.ui.components.HotButton
import com.lifepowers.app.ui.components.InkButton
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.ScreenHeader
import com.lifepowers.app.ui.components.SectionLabel
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.components.WrapRow
import com.lifepowers.app.ui.components.dashedBorder
import com.lifepowers.app.ui.navigation.Routes
import com.lifepowers.app.ui.theme.HairLine
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.LightPink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.core.SymptomParser

/** What is open inline below the cards. */
private sealed interface Sheet {
    data object Write : Sheet
    data class Soon(val what: String) : Sheet
    data class Food(val kind: String, val label: String) : Sheet
    data class Symptom(val kind: String) : Sheet
    data class Behavior(val kind: String, val label: String) : Sheet
}

@Composable
fun JournalScreen(onNavigate: (String) -> Unit, viewModel: JournalViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<Sheet?>(null) }

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
            ScreenHeader(kicker = Fmt.dateKicker(state.today), title = "Journal") {
                HotButton(text = "Talk", onClick = { onNavigate(Routes.CHECKIN) })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickTile("Write", Modifier.weight(1f)) { sheet = if (sheet == Sheet.Write) null else Sheet.Write }
                QuickTile("Photo", Modifier.weight(1f)) { sheet = Sheet.Soon("Photos") }
                QuickTile("Pull a card", Modifier.weight(1f)) { sheet = Sheet.Soon("Tarot pulls") }
                QuickTile("Say a symptom", Modifier.weight(1f), background = LightPink, shadowColor = HotPink) { onNavigate(Routes.CHECKIN) }
            }

            val current = sheet
            if (current == Sheet.Write) {
                WriteCard(onSave = { note -> viewModel.add(EventKinds.JOURNAL, note = note); sheet = null }, onCancel = { sheet = null })
            }
            if (current is Sheet.Soon) {
                SoftCard(modifier = Modifier.fillMaxWidth(), radius = 24.dp, gap = 4.dp) {
                    Text(text = "${current.what} arrive in the next phase", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    MutedLine(text = "The journal keeps its words for now. Tap again to close.", modifier = Modifier.fillMaxWidth())
                    TextLink(text = "Close", onClick = { sheet = null })
                }
            }

            FoodCard(
                cravingCount = state.cravingCount,
                open = current as? Sheet.Food,
                onPick = { kind, label -> sheet = if ((current as? Sheet.Food)?.kind == kind) null else Sheet.Food(kind, label) },
                onSave = { kind, intensity -> viewModel.add(kind, quantity = intensity.toDouble(), unit = "intensity"); sheet = null },
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
private fun QuickTile(
    label: String,
    modifier: Modifier,
    background: androidx.compose.ui.graphics.Color = com.lifepowers.app.ui.theme.White,
    shadowColor: androidx.compose.ui.graphics.Color = Ink,
    onClick: () -> Unit,
) {
    SoftCard(
        modifier = modifier, color = background, radius = 20.dp, shadowColor = shadowColor, shadowAlpha = 0.1f,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp), onClick = onClick,
    ) {
        Text(
            text = label, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
            modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun WriteCard(onSave: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 24.dp, gap = 10.dp) {
        SectionLabel("A plain place to write")
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("How was it, really?", color = Muted) },
            minLines = 3,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HotPink, unfocusedBorderColor = HairLine, cursorColor = HotPink),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InkButton(text = "Save", onClick = { onSave(text) })
            PillButton(text = "Cancel", onClick = onCancel, shadowAlpha = 0.08f, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp))
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
            SectionLabel("Food, no judgment")
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
        SectionLabel("Symptoms, tap one")
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
        SectionLabel("Things you did")
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
        SectionLabel("Today so far")
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
