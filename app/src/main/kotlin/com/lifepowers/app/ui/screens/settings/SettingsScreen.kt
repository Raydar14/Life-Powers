package com.lifepowers.app.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepowers.app.connectors.HealthConnectConnector
import com.lifepowers.app.connectors.UsageStatsConnector
import com.lifepowers.app.domain.SyncState
import com.lifepowers.app.domain.SyncStatus
import com.lifepowers.app.ui.components.CloudPresets
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.Fmt
import com.lifepowers.app.ui.components.InkButton
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.PrimaryButton
import com.lifepowers.app.ui.components.ScreenHeader
import com.lifepowers.app.ui.components.SectionLabel
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.navigation.Routes
import com.lifepowers.app.ui.theme.DeepPink
import com.lifepowers.app.ui.theme.HairLine
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.Lavender
import com.lifepowers.app.ui.theme.LightPink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.app.ui.theme.SoftBlue
import com.lifepowers.app.ui.theme.White
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsScreen(onNavigate: (String) -> Unit, onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val healthNote by viewModel.healthConnectNote.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val healthPermissions = HealthConnectConnector.PERMISSIONS
    val healthLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        viewModel.healthConnectResult(granted, healthPermissions.size)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Clouds(CloudPresets.settings)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(kicker = null, title = "Settings", titleSize = 28.sp) {
                Column(horizontalAlignment = Alignment.End) {
                    TextLink(text = "Back", onClick = onBack)
                    TextLink(
                        text = if (state.setupLeft > 0) "Finish setup, ${state.setupLeft} left" else "Setup again",
                        onClick = { onNavigate(Routes.WELCOME) },
                    )
                }
            }

            AssistantCard(on = state.assistantOn, onToggle = { viewModel.setAssistant(it) })

            SoftCard(modifier = Modifier.fillMaxWidth(), gap = 12.dp) {
                SectionLabel("Connections")

                ConnectionRow(title = "Health Connect", detail = healthNote ?: "Sleep, heart, steps, glucose, pressure, weight") {
                    InkButton(text = "Connect", onClick = {
                        val ok = runCatching { healthLauncher.launch(healthPermissions) }.isSuccess
                        if (!ok) viewModel.healthConnectUnavailable()
                    })
                }

                OuraRow(saved = state.ouraToken, onSave = { viewModel.saveOuraToken(it) })

                ConnectionRow(title = "Phone usage", detail = "Screen time and last use before sleep") {
                    InkButton(text = "Open settings", onClick = {
                        runCatching { context.startActivity(UsageStatsConnector.settingsIntent()) }
                    })
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Sync")
                    PillButton(
                        text = if (syncing) "Syncing" else "Sync now", onClick = { viewModel.syncNow() },
                        background = DeepPink, textColor = White, fontWeight = FontWeight.Black, shadowColor = DeepPink, shadowAlpha = 0.3f,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), enabled = !syncing,
                    )
                }
                if (state.sync.isEmpty()) MutedLine(text = "No connectors have reported yet.")
                for (s in state.sync) SyncRow(s)
            }

            SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
                SectionLabel("Journal and photos")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = "Keep audio", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        MutedLine(text = if (state.keepAudio) "Recordings stay, encrypted, on this phone." else "Audio is deleted once the transcript and features are stored.")
                    }
                    PinkSwitch(checked = state.keepAudio, onChange = { viewModel.setKeepAudio(it) })
                }
            }

            SoftCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp), gap = 0.dp) {
                LinkRow(title = "Profile and hormones", detail = state.modelName) { onNavigate(Routes.PROFILE) }
                LinkRow(title = "Feelings, your words", detail = "texture ring") { onNavigate(Routes.FEEL) }
                LinkRow(title = "Location and world", detail = "next phase", enabled = false) {}
                LinkRow(title = "Goals and reminders", detail = "next phase", enabled = false) {}
                LinkRow(title = "Forecast", detail = "8 panels on Today", enabled = false) {}
                LinkRow(title = "Privacy and data", detail = "next phase", enabled = false) {}
                LinkRow(title = "Appearance", detail = "next phase", enabled = false) {}
            }
        }
    }
}

@Composable
private fun AssistantCard(on: Boolean, onToggle: (Boolean) -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), color = Ink, shadowAlpha = 0.26f, elevation = 14.dp, gap = 10.dp) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = "Assistant features", color = White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text(text = "Off means every assistant button disappears.", color = Lavender, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Switch(
                checked = on,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = White, checkedTrackColor = HotPink, checkedBorderColor = HotPink,
                    uncheckedThumbColor = White, uncheckedTrackColor = White.copy(alpha = 0.2f), uncheckedBorderColor = White.copy(alpha = 0.2f),
                ),
            )
        }
        if (on) {
            Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistantRow("Interpret tarot cards", "next phase")
                AssistantRow("Ask about photos", "next phase")
                AssistantRow("Richer check-in ratings", "off")
                AssistantRow("Weekly narrative", "off")
                Text(
                    text = "Each call shows what it will send before it sends it. Nothing has left the phone yet.",
                    color = SoftBlue, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun AssistantRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = if (value == "on") LightPink else Lavender, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun PinkSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = White, checkedTrackColor = HotPink, checkedBorderColor = HotPink,
            uncheckedThumbColor = White, uncheckedTrackColor = HairLine, uncheckedBorderColor = HairLine,
        ),
    )
}

@Composable
private fun ConnectionRow(title: String, detail: String, action: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            MutedLine(text = detail)
        }
        action()
    }
}

@Composable
private fun OuraRow(saved: String?, onSave: (String) -> Unit) {
    var token by remember { mutableStateOf(saved ?: "") }
    LaunchedEffect(saved) { if (saved != null && token.isEmpty()) token = saved }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "Oura", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        MutedLine(text = if (saved.isNullOrBlank()) "Paste a personal access token from the Oura site." else "Token saved. Paste a new one to replace it.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Personal access token", color = Muted, fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HotPink, unfocusedBorderColor = HairLine, cursorColor = HotPink),
            )
            InkButton(text = "Save", onClick = { onSave(token) })
        }
    }
}

private val syncClock = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())

@Composable
private fun SyncRow(status: SyncStatus) {
    val (word, color) = when (status.state) {
        SyncState.IDLE -> "idle" to Muted
        SyncState.RUNNING -> "syncing" to Ink
        SyncState.OK -> "ok" to DeepPink
        SyncState.ERROR -> "error" to DeepPink
        SyncState.NOT_CONNECTED -> "not connected" to Muted
    }
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = status.connectorId.replace('_', ' '), color = Ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Text(text = word, color = color, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
        val last = status.lastSuccessUtcMs
        val detail = when {
            status.state == SyncState.ERROR && !status.lastError.isNullOrBlank() -> status.lastError ?: ""
            last != null -> "last synced " + Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault()).format(syncClock).lowercase(Locale.getDefault())
            else -> "never synced"
        }
        MutedLine(text = detail, fontSize = 11.sp)
    }
}

@Composable
private fun LinkRow(title: String, detail: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, color = if (enabled) Ink else Muted, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        Text(text = detail, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
