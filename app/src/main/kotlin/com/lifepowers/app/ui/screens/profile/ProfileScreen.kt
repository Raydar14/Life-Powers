package com.lifepowers.app.ui.screens.profile

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepowers.app.ui.components.Chip
import com.lifepowers.app.ui.components.CloudPresets
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.Fmt
import com.lifepowers.app.ui.components.Kicker
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.PrimaryButton
import com.lifepowers.app.ui.components.ScreenHeader
import com.lifepowers.app.ui.components.SectionLabel
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.components.WrapRow
import com.lifepowers.app.ui.theme.BodyText
import com.lifepowers.app.ui.theme.DeepLavender
import com.lifepowers.app.ui.theme.DeepLavenderLine
import com.lifepowers.app.ui.theme.HairLine
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.Lavender
import com.lifepowers.app.ui.theme.LightPink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.app.ui.theme.SoftBlue
import com.lifepowers.app.ui.theme.White
import com.lifepowers.core.Contraception
import com.lifepowers.core.MenstrualStatus
import com.lifepowers.core.SexAtBirth

@Composable
fun ProfileScreen(onSaved: () -> Unit, onBack: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val symptoms by viewModel.recentSymptoms.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Clouds(CloudPresets.profile)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(kicker = null, title = "About you", titleSize = 28.sp) {
                TextLink(text = "Back", onClick = onBack)
            }
            MutedLine(text = "This decides how the app reads your hormones. Every change is dated so the past stays true.", fontSize = 13.sp)

            val f = form
            if (f == null) {
                SoftCard(modifier = Modifier.fillMaxWidth()) { MutedLine(text = "Loading your profile.") }
                return@Column
            }

            SoftCard(
                modifier = Modifier.fillMaxWidth(), color = Lavender, shadowColor = DeepLavenderLine, shadowAlpha = 0.22f, elevation = 14.dp,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), gap = 4.dp,
            ) {
                Kicker("How your cycle is read", color = DeepLavender)
                Text(text = f.model.title(), color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text(text = f.model.explain(), color = BodyText, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
            }

            SoftCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp), gap = 4.dp) {
                TextRow(label = "Name", value = f.displayName, placeholder = "What should the app call you", onChange = { v -> viewModel.update { it.copy(displayName = v) } })
                TextRow(label = "Born", value = f.dateOfBirth, placeholder = "1986-03-14", onChange = { v -> viewModel.update { it.copy(dateOfBirth = v) } }, keyboard = KeyboardType.Number)
                PickerRow(
                    label = "Sex at birth", value = f.sexAtBirth?.label() ?: "Choose",
                    options = SexAtBirth.values().toList(), display = { it.label() },
                    onPick = { v -> viewModel.update { it.copy(sexAtBirth = v) } },
                )
                TextRow(label = "Gender", value = f.genderIdentity, placeholder = "In your words", onChange = { v -> viewModel.update { it.copy(genderIdentity = v) } })
                PickerRow(
                    label = "Cycle status", value = f.menstrualStatus?.label() ?: "Choose",
                    options = MenstrualStatus.values().toList(), display = { it.label() },
                    onPick = { v -> viewModel.update { it.copy(menstrualStatus = v) } },
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        TextRow(label = "Usual cycle, days", value = f.cycleLengthDays, placeholder = "29", onChange = { v -> viewModel.update { it.copy(cycleLengthDays = v) } }, keyboard = KeyboardType.Number)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        TextRow(label = "Give or take", value = f.cycleVariabilityDays, placeholder = "2", onChange = { v -> viewModel.update { it.copy(cycleVariabilityDays = v) } }, keyboard = KeyboardType.Number)
                    }
                }
                TextRow(label = "Last period started", value = f.lastPeriodStart, placeholder = "2026-09-26", onChange = { v -> viewModel.update { it.copy(lastPeriodStart = v) } }, keyboard = KeyboardType.Number)
                PickerRow(
                    label = "Birth control", value = f.contraception.label(),
                    options = Contraception.values().toList(), display = { it.label() },
                    onPick = { v -> viewModel.update { it.copy(contraception = v) } },
                )
                TextRow(label = "Hormone therapy", value = f.hrt, placeholder = "None", onChange = { v -> viewModel.update { it.copy(hrt = v) } })
                TextRow(label = "Medications", value = f.medications, placeholder = "Name, since when", onChange = { v -> viewModel.update { it.copy(medications = v) } })
            }

            SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
                SectionLabel("This cycle you have felt")
                if (symptoms.isEmpty()) {
                    MutedLine(text = "Nothing logged in the last thirty days. Say a symptom in a check-in or tap one in the journal.")
                } else {
                    WrapRow {
                        val palette = listOf(LightPink, Lavender, SoftBlue)
                        symptoms.forEachIndexed { i, kind -> Chip(text = Fmt.kindName(kind), background = palette[i % palette.size]) }
                    }
                }
            }

            PrimaryButton(text = if (saving) "Saving" else "Save", onClick = { viewModel.save(onSaved) }, modifier = Modifier.fillMaxWidth(), enabled = !saving)

            MutedLine(
                text = "Start a combined pill and the app switches to pack rhythm: strips stop being read as ovulation and nothing is pooled across the change date.",
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun TextRow(label: String, value: String, placeholder: String, onChange: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = Muted, fontSize = 14.sp) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = HotPink, unfocusedBorderColor = HairLine, cursorColor = HotPink,
                focusedTextColor = Ink, unfocusedTextColor = Ink, focusedContainerColor = White, unfocusedContainerColor = White,
            ),
        )
    }
}

@Composable
private fun <T> PickerRow(label: String, value: String, options: List<T>, display: (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        Box {
            Text(
                text = value,
                color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End,
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable { open = true }.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for (option in options) {
                    DropdownMenuItem(
                        text = { Text(display(option), color = Ink, fontWeight = FontWeight.Bold) },
                        onClick = { onPick(option); open = false },
                    )
                }
            }
        }
    }
}
