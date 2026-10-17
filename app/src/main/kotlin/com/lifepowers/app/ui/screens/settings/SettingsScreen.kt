package com.lifepowers.app.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepowers.app.connectors.HealthConnectConnector
import com.lifepowers.app.connectors.UsageStatsConnector
import com.lifepowers.app.domain.LocationMode
import com.lifepowers.app.domain.SyncState
import com.lifepowers.app.domain.SyncStatus
import com.lifepowers.app.ui.components.CardTitle
import com.lifepowers.app.ui.components.Chip
import com.lifepowers.app.ui.components.CloudPresets
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.Fmt
import com.lifepowers.app.ui.components.InkButton
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.PinkSwitch
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.PrimaryButton
import com.lifepowers.app.ui.components.ScreenHeader
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.components.WrapRow
import com.lifepowers.app.ui.navigation.Routes
import com.lifepowers.app.ui.screens.sky.SkyText
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
import kotlin.math.roundToInt

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
            ScreenHeader(title = "Settings", titleSize = 28.sp) {
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
                CardTitle("Connections")

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
                    Text(text = "Sync", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    PillButton(
                        text = if (syncing) "Syncing" else "Sync now", onClick = { viewModel.syncNow() },
                        background = DeepPink, textColor = White, fontWeight = FontWeight.Black, shadowColor = DeepPink, shadowAlpha = 0.3f,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), enabled = !syncing,
                    )
                }
                if (state.sync.isEmpty()) MutedLine(text = "No connectors have reported yet.")
                for (s in state.sync) SyncRow(s)
            }

            LocationCard(viewModel)

            NoticesCard(viewModel)

            SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
                CardTitle("Journal and photos")
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
                LinkRow(title = "Goals and reminders", detail = "habits, reminders, timer") { onNavigate(Routes.GOALS) }
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

// Location and world

private val locationChoices = listOf(
    LocationMode.PRECISE to "Precise",
    LocationMode.APPROXIMATE to "About 10 km",
    LocationMode.CITY to "A city I type",
    LocationMode.NONE to "No location",
)

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@Composable
private fun LocationCard(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val location by viewModel.location.collectAsStateWithLifecycle()
    val results by viewModel.cityResults.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val note by viewModel.locationNote.collectAsStateWithLifecycle()

    var cityOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var pendingMode by remember { mutableStateOf<LocationMode?>(null) }

    // Android 12 and later ignore a request for fine location alone, so precise asks for both.
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val mode = pendingMode ?: return@rememberLauncherForActivityResult
        pendingMode = null
        val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true || hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        when {
            mode == LocationMode.PRECISE && fine -> viewModel.chooseDeviceLocation(LocationMode.PRECISE, granted = true)
            mode == LocationMode.PRECISE && coarse -> viewModel.chooseDeviceLocation(LocationMode.APPROXIMATE, granted = true, downgraded = true)
            mode == LocationMode.APPROXIMATE && (coarse || fine) -> viewModel.chooseDeviceLocation(LocationMode.APPROXIMATE, granted = true)
            else -> viewModel.chooseDeviceLocation(mode, granted = false)
        }
    }

    fun choose(mode: LocationMode) {
        when (mode) {
            LocationMode.PRECISE -> {
                cityOpen = false
                if (hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
                    viewModel.chooseDeviceLocation(LocationMode.PRECISE, granted = true)
                } else {
                    pendingMode = LocationMode.PRECISE
                    locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }
            LocationMode.APPROXIMATE -> {
                cityOpen = false
                if (hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    viewModel.chooseDeviceLocation(LocationMode.APPROXIMATE, granted = true)
                } else {
                    pendingMode = LocationMode.APPROXIMATE
                    locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }
            LocationMode.CITY -> cityOpen = true
            LocationMode.NONE -> {
                cityOpen = false
                viewModel.clearLocation()
            }
        }
    }

    val shownMode = if (cityOpen) LocationMode.CITY else location.mode

    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 12.dp) {
        CardTitle("Location and world")
        MutedLine(text = "Now: ${SkyText.locationLine(location)}.", fontSize = 13.sp)
        WrapRow(horizontalGap = 8.dp, verticalGap = 8.dp) {
            for ((mode, label) in locationChoices) {
                Chip(text = label, selected = shownMode == mode, onClick = { choose(mode) })
            }
        }
        if (shownMode == LocationMode.CITY) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("A city or town", color = Muted, fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HotPink, unfocusedBorderColor = HairLine, cursorColor = HotPink),
                )
                InkButton(text = if (searching) "Searching" else "Search", onClick = { viewModel.searchCity(query) })
            }
            if (results.isNotEmpty()) {
                WrapRow(horizontalGap = 8.dp, verticalGap = 8.dp) {
                    for (place in results) {
                        Chip(
                            text = SettingsViewModel.placeLabel(place),
                            background = LightPink,
                            onClick = {
                                cityOpen = false
                                query = ""
                                viewModel.chooseCity(place)
                            },
                        )
                    }
                }
            }
        }
        val line = note
        if (line != null) Text(text = line, color = DeepPink, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold)
        MutedLine(text = "Weather and air services only ever see your place rounded to about 10 km. Quake and volcano feeds are fetched for the whole world and filtered on your phone.")
    }
}

// Notices

private val eveningHours = listOf(18 to "6 pm", 19 to "7 pm", 20 to "8 pm", 21 to "9 pm", 22 to "10 pm")
private val magnitudes = listOf(4.5 to "4.5 and up", 5.0 to "5 and up", 6.0 to "6 and up")
private val radii = listOf(200.0 to "200 km", 500.0 to "500 km", 1000.0 to "1000 km")

private fun notificationsAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)

@Composable
private fun NoticesCard(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val sky by viewModel.skySettings.collectAsStateWithLifecycle()
    var allowed by remember { mutableStateOf(notificationsAllowed(context)) }
    var askedOnce by remember { mutableStateOf(false) }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted || notificationsAllowed(context)
        askedOnce = true
    }

    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 12.dp) {
        CardTitle("Notices")
        if (!allowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                MutedLine(
                    text = if (askedOnce) "Still off. Notices can also be allowed in the phone's settings for this app." else "The phone has not allowed notices from this app yet.",
                    modifier = Modifier.weight(1f),
                )
                PillButton(
                    text = "Allow notices",
                    onClick = { notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    background = DeepPink, textColor = White, fontWeight = FontWeight.Black, shadowColor = DeepPink, shadowAlpha = 0.3f,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        SwitchRow(
            title = "Evening sky notices",
            detail = "Showers, eclipses, close pairings, the evening before.",
            checked = sky.notifySky,
            onChange = { on -> viewModel.saveSky { it.copy(notifySky = on) } },
        )
        if (sky.notifySky) {
            Text(text = "Evening notice time", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            WrapRow(horizontalGap = 8.dp, verticalGap = 8.dp) {
                for ((hour, label) in eveningHours) {
                    Chip(text = label, selected = sky.eveningHour == hour, onClick = { viewModel.saveSky { it.copy(eveningHour = hour) } })
                }
            }
        }

        SwitchRow(
            title = "Aurora",
            detail = "When the K index is high enough at your latitude.",
            checked = sky.notifyAurora,
            onChange = { on -> viewModel.saveSky { it.copy(notifyAurora = on) } },
        )

        SwitchRow(
            title = "Ground events",
            detail = "Quakes and volcano reports near you, in calm words.",
            checked = sky.notifyGround,
            onChange = { on -> viewModel.saveSky { it.copy(notifyGround = on) } },
        )
        Text(text = "Magnitude", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        WrapRow(horizontalGap = 8.dp, verticalGap = 8.dp) {
            for ((mag, label) in magnitudes) {
                Chip(text = label, selected = sameValue(sky.groundMinMagnitude, mag), onClick = { viewModel.saveSky { it.copy(groundMinMagnitude = mag) } })
            }
        }
        Text(text = "Within", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        WrapRow(horizontalGap = 8.dp, verticalGap = 8.dp) {
            for ((km, label) in radii) {
                Chip(text = label, selected = sameValue(sky.groundRadiusKm, km), onClick = { viewModel.saveSky { it.copy(groundRadiusKm = km) } })
            }
        }

        SwitchRow(
            title = "Transits and retrogrades",
            detail = "Off by default. The astronomy is computed on your phone; whether it means something is yours to decide.",
            checked = sky.transitsOn,
            onChange = { on -> viewModel.saveSky { it.copy(transitsOn = on) } },
        )
    }
}

private fun sameValue(a: Double, b: Double): Boolean = (a * 10).roundToInt() == (b * 10).roundToInt()

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            MutedLine(text = detail)
        }
        PinkSwitch(checked = checked, onChange = onChange)
    }
}
