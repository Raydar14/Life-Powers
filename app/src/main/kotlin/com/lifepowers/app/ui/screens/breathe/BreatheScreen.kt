package com.lifepowers.app.ui.screens.breathe

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepowers.app.domain.HeartDevice
import com.lifepowers.app.domain.HeartLinkState
import com.lifepowers.app.domain.SavedBreathPattern
import com.lifepowers.app.ui.components.BodyLine
import com.lifepowers.app.ui.components.CardTitle
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.Fmt
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.PrimaryButton
import com.lifepowers.app.ui.components.ScreenHeader
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.components.WrapRow
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.LightPink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.core.Breathing

private const val HonestLine =
    "Live data needs a sensor that shares heart rate over Bluetooth, such as a chest strap. Oura and most watches do not stream live to other apps."

/** What to do once Bluetooth permission is granted. */
private enum class AfterGrant { SCAN, REMEMBERED }

@Composable
fun BreatheScreen(onNavigate: (String) -> Unit, onBack: () -> Unit, viewModel: BreatheViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var finding by rememberSaveable { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var afterGrant by remember { mutableStateOf(AfterGrant.SCAN) }
    var toDelete by remember { mutableStateOf<SavedBreathPattern?>(null) }

    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (finding) viewModel.startScan()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result.isNotEmpty() && result.values.all { it }
        denied = !granted
        if (granted) {
            when (afterGrant) {
                AfterGrant.SCAN -> viewModel.startScan()
                AfterGrant.REMEMBERED -> viewModel.connectRemembered()
            }
        }
    }
    val withPermission: (AfterGrant) -> Unit = { action ->
        if (viewModel.hasPermissions()) {
            denied = false
            when (action) {
                AfterGrant.SCAN -> viewModel.startScan()
                AfterGrant.REMEMBERED -> viewModel.connectRemembered()
            }
        } else {
            afterGrant = action
            permissionLauncher.launch(bluetoothPermissions())
        }
    }
    val turnOnBluetooth: () -> Unit = {
        if (viewModel.hasPermissions()) requestBluetoothOn(context, enableLauncher)
        else {
            afterGrant = AfterGrant.SCAN
            permissionLauncher.launch(bluetoothPermissions())
        }
    }

    LaunchedEffect(finding) { if (finding) withPermission(AfterGrant.SCAN) }
    LaunchedEffect(state.heart.state) {
        if (finding && state.heart.state == HeartLinkState.CONNECTED) finding = false
    }
    DisposableEffect(Unit) { onDispose { viewModel.stopScan() } }

    val startable = state.running == null
    val start: (SavedBreathPattern) -> Unit = { p -> if (viewModel.start(p)) onNavigate(BreatheRoutes.SESSION) }

    Box(modifier = Modifier.fillMaxSize()) {
        Clouds(BreatheClouds.home)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(
                title = "Breathe",
                titleSize = 28.sp,
                subline = "Connect a heart sensor to watch your heart answer your breath, live.",
            ) {
                TextLink(text = "Back", onClick = onBack)
            }

            val running = state.running
            if (running != null) {
                SoftCard(modifier = Modifier.fillMaxWidth(), color = LightPink, shadowColor = HotPink, shadowAlpha = 0.2f, gap = 8.dp) {
                    CardTitle(text = if (running.paused) "${running.name} is paused" else "${running.name} is running")
                    BodyLine(text = "${running.describe}. Finish it to start another.", color = Ink)
                    PrimaryButton(text = "Return to it", onClick = { onNavigate(BreatheRoutes.SESSION) }, modifier = Modifier.fillMaxWidth())
                }
            }

            HeartCard(
                heart = state.heart,
                onFind = { finding = true },
                onTurnOn = turnOnBluetooth,
                onReconnect = { withPermission(AfterGrant.REMEMBERED) },
                onDisconnect = { viewModel.disconnect() },
            )

            SectionTitle("Patterns to start with")
            for (preset in Breathing.PRESETS) {
                PresetCard(
                    preset = preset,
                    startable = startable,
                    onStart = { if (viewModel.startPreset(preset)) onNavigate(BreatheRoutes.SESSION) },
                    onAdjust = { onNavigate(BreatheRoutes.edit(0, preset.name)) },
                )
            }

            SectionTitle("Your patterns")
            if (state.loaded && state.patterns.isEmpty()) {
                MutedLine(text = "Patterns you make and save show here.", fontSize = 13.sp, modifier = Modifier.padding(horizontal = 6.dp))
            }
            for (p in state.patterns) {
                SavedCard(
                    saved = p,
                    startable = startable,
                    onStart = { start(p) },
                    onEdit = { onNavigate(BreatheRoutes.edit(p.id)) },
                    onDelete = { toDelete = p },
                )
            }

            PrimaryButton(text = "Make your own", onClick = { onNavigate(BreatheRoutes.edit(0)) }, modifier = Modifier.fillMaxWidth())
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextLink(text = "Past sessions", onClick = { onNavigate(BreatheRoutes.HISTORY) }, fontSize = 13.sp)
            }
        }
    }

    if (finding) {
        FindSensorDialog(
            heart = state.heart,
            denied = denied,
            onAllow = { afterGrant = AfterGrant.SCAN; permissionLauncher.launch(bluetoothPermissions()) },
            onOpenSettings = { openAppSettings(context) },
            onTurnOn = turnOnBluetooth,
            onSearchAgain = { withPermission(AfterGrant.SCAN) },
            onConnect = { device ->
                viewModel.connect(device)
                finding = false
            },
            onClose = {
                viewModel.stopScan()
                finding = false
            },
        )
    }

    val deleting = toDelete
    if (deleting != null) {
        BreathConfirmDialog(
            title = "Delete ${deleting.name}?",
            body = "The pattern goes. Sessions you breathed with it stay in past sessions.",
            confirmText = "Delete",
            onConfirm = {
                viewModel.deletePattern(deleting.id)
                toDelete = null
            },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Ink,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun HeartCard(
    heart: HeartUi,
    onFind: () -> Unit,
    onTurnOn: () -> Unit,
    onReconnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
        val name = heart.connected?.name?.takeIf { it.isNotBlank() }
        when (heart.state) {
            HeartLinkState.CONNECTED -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        CardTitle(text = name ?: "Heart sensor")
                        MutedLine(text = "Connected. Your heart rate shows live while you breathe.")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = Fmt.num(heart.bpm), color = HotPink, fontSize = 40.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black, maxLines = 1)
                        Text(text = "bpm", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                PillButton(text = "Disconnect", onClick = onDisconnect, shadowAlpha = 0.08f)
            }
            HeartLinkState.CONNECTING -> {
                CardTitle(text = if (name != null) "Connecting to $name" else "Connecting to your sensor")
                MutedLine(text = "Keep it on and close by. This takes a few seconds.")
            }
            HeartLinkState.SCANNING -> {
                CardTitle(text = "Looking for sensors")
                PillButton(text = "Show what is nearby", onClick = onFind, background = LightPink)
            }
            HeartLinkState.BLUETOOTH_OFF -> {
                CardTitle(text = "Bluetooth is off")
                MutedLine(text = "Turn it on to use a heart sensor.")
                PillButton(text = "Turn on Bluetooth", onClick = onTurnOn, background = LightPink)
            }
            HeartLinkState.LOST -> {
                val lost = name ?: heart.remembered?.name?.takeIf { it.isNotBlank() }
                CardTitle(text = "Lost the sensor")
                MutedLine(text = if (lost != null) "$lost stopped sending. Check it is on and close by." else "The sensor stopped sending. Check it is on and close by.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(text = "Reconnect", onClick = onReconnect, background = LightPink)
                    PillButton(text = "Find a sensor", onClick = onFind, shadowAlpha = 0.08f)
                }
            }
            HeartLinkState.OFF, HeartLinkState.NEEDS_PERMISSION -> {
                CardTitle(text = "No heart sensor connected")
                val remembered = heart.remembered
                WrapRow {
                    PillButton(text = "Find a sensor", onClick = onFind, background = LightPink)
                    if (remembered != null) {
                        val label = remembered.name.takeIf { it.isNotBlank() } ?: "your last sensor"
                        PillButton(text = "Connect $label", onClick = onReconnect, shadowAlpha = 0.08f)
                    }
                }
            }
        }
        MutedLine(text = HonestLine)
    }
}

@Composable
private fun PresetCard(preset: Breathing.Preset, startable: Boolean, onStart: () -> Unit, onAdjust: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 6.dp) {
        CardTitle(text = preset.name)
        PatternLine(describe = preset.pattern.describe(), pace = BreathWords.pace(preset.pattern))
        BodyLine(text = preset.about)
        Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(text = "Start", onClick = onStart, background = LightPink, shadowColor = HotPink, shadowAlpha = 0.18f, enabled = startable)
            PillButton(text = "Adjust", onClick = onAdjust, shadowAlpha = 0.08f)
        }
    }
}

@Composable
private fun SavedCard(saved: SavedBreathPattern, startable: Boolean, onStart: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val allowed = Breathing.isAllowed(saved.pattern)
    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 6.dp) {
        CardTitle(text = saved.name)
        PatternLine(describe = saved.pattern.describe(), pace = BreathWords.pace(saved.pattern))
        MutedLine(text = "${BreathWords.length(saved.sessionMin)}. ${BreathWords.guide(saved.guide)}.")
        if (!allowed) BodyLine(text = "This pattern needs a change before it can be used. Edit it to see why.")
        Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(text = "Start", onClick = onStart, background = LightPink, shadowColor = HotPink, shadowAlpha = 0.18f, enabled = startable && allowed)
            PillButton(text = "Edit", onClick = onEdit, shadowAlpha = 0.08f)
            PillButton(text = "Delete", onClick = onDelete, shadowAlpha = 0.08f)
        }
    }
}

@Composable
private fun PatternLine(describe: String, pace: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(text = describe, color = Ink, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.ExtraBold)
        MutedLine(text = pace)
    }
}

@Composable
private fun FindSensorDialog(
    heart: HeartUi,
    denied: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onTurnOn: () -> Unit,
    onSearchAgain: () -> Unit,
    onConnect: (HeartDevice) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose) {
        SoftCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            gap = 10.dp,
        ) {
            Text(text = "Find a sensor", color = Ink, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black)
            BodyLine(text = "Put on your chest strap and wet the contacts, or set your watch to share heart rate. Keep it close.")
            when {
                denied -> {
                    BodyLine(text = "Bluetooth permission is needed to look for a sensor. It is used only to find and read your heart rate.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton(text = "Allow", onClick = onAllow)
                        PillButton(text = "Open settings", onClick = onOpenSettings, shadowAlpha = 0.08f)
                    }
                }
                heart.state == HeartLinkState.BLUETOOTH_OFF -> {
                    BodyLine(text = "Bluetooth is off.")
                    PrimaryButton(text = "Turn on Bluetooth", onClick = onTurnOn)
                }
                else -> {
                    val scanning = heart.state == HeartLinkState.SCANNING
                    if (heart.nearby.isEmpty()) {
                        MutedLine(text = if (scanning) "Looking nearby" else "Nothing found yet.", fontSize = 13.sp)
                    } else {
                        MutedLine(text = "Tap your sensor to connect.", fontSize = 13.sp)
                        Column(modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                            WrapRow {
                                for (d in heart.nearby) {
                                    PillButton(
                                        text = d.name.takeIf { it.isNotBlank() } ?: "Unnamed sensor",
                                        onClick = { onConnect(d) },
                                        background = LightPink,
                                        shadowColor = HotPink,
                                        shadowAlpha = 0.16f,
                                    )
                                }
                            }
                        }
                    }
                    if (!scanning) PillButton(text = "Search again", onClick = onSearchAgain, shadowAlpha = 0.08f)
                }
            }
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextLink(text = "Close", onClick = onClose)
            }
        }
    }
}

/** Android 12 and later ask for nearby devices; Android 9 to 11 tie Bluetooth scans to location. */
private fun bluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

/** Called only once Bluetooth permission is held. Falls back to the Bluetooth settings page. */
@SuppressLint("MissingPermission")
private fun requestBluetoothOn(context: Context, launcher: ActivityResultLauncher<Intent>) {
    try {
        launcher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    } catch (e: ActivityNotFoundException) {
        openBluetoothSettings(context)
    } catch (e: SecurityException) {
        openBluetoothSettings(context)
    }
}

private fun openBluetoothSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
    }
}

private fun openAppSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
    }
}
