package com.lifepowers.app.ui.screens.privacy

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepowers.app.domain.AssistantCallLog
import com.lifepowers.app.domain.AssistantFeatures
import com.lifepowers.app.ui.components.BodyLine
import com.lifepowers.app.ui.components.CardTitle
import com.lifepowers.app.ui.components.CloudPresets
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.Dot
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.PinkSwitch
import com.lifepowers.app.ui.components.PrimaryButton
import com.lifepowers.app.ui.components.ScreenHeader
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.navigation.Routes
import com.lifepowers.app.ui.theme.BodyText
import com.lifepowers.app.ui.theme.DeepPink
import com.lifepowers.app.ui.theme.HairLine
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.Lavender
import com.lifepowers.app.ui.theme.LightPink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.app.ui.theme.White
import com.lifepowers.app.vault.VaultCodec
import com.lifepowers.core.BackupCrypto
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Privacy and data: what stays here, what has left, backup, restore, export, delete everything. */
@Composable
fun PrivacyScreen(onNavigate: (String) -> Unit, onBack: () -> Unit, viewModel: PrivacyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val log by viewModel.log.collectAsStateWithLifecycle()
    val busy = state.busy != null

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        viewModel.backupTo(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.restoreChosen(uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        viewModel.exportTo(uri)
    }

    var askDelete by remember { mutableStateOf(false) }

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
            ScreenHeader(title = "Privacy and data", titleSize = 28.sp, subline = "What stays here, what has left, and your own copies") {
                TextLink(text = "Back", onClick = onBack)
            }

            StatusCard(
                busy = state.busy,
                result = state.result,
                ok = state.resultOk,
                deleted = state.deleted,
                onDismiss = { viewModel.dismissResult() },
                onStartAgain = { onNavigate(Routes.WELCOME) },
            )

            StaysCard()
            LeftCard(log)

            BackupCard(
                busy = busy,
                onSave = { password, photos ->
                    viewModel.stageBackup(password, photos)
                    backupLauncher.launch(VaultCodec.backupFileName(LocalDate.now()))
                },
            )

            RestoreCard(
                busy = busy,
                onChoose = { password ->
                    viewModel.stageRestore(password)
                    restoreLauncher.launch(arrayOf("*/*"))
                },
            )

            SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
                CardTitle("Export for spreadsheets")
                BodyLine(
                    text = "A zip of plain CSV files: daily measures, moments on the timeline, goals and habit checks, " +
                        "breathing sessions, and experiments. These files are not encrypted, so keep them somewhere you trust.",
                )
                PrimaryButton(
                    text = "Choose where to save",
                    onClick = { exportLauncher.launch(VaultCodec.exportFileName(LocalDate.now())) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
                CardTitle("Delete everything")
                BodyLine(
                    text = "Removes every record, photo, recording, setting, key, and token from this phone. " +
                        "Backups you saved elsewhere are not touched. This cannot be undone.",
                )
                PillButton(
                    text = "Delete everything",
                    onClick = { askDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    background = Ink,
                    textColor = White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    enabled = !busy,
                )
            }
        }
    }

    val pending = state.pendingRestore
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelRestore() },
            confirmButton = { TextLink(text = "Replace", onClick = { viewModel.confirmRestore() }, fontSize = 14.sp) },
            dismissButton = { TextLink(text = "Keep what is here", onClick = { viewModel.cancelRestore() }, fontSize = 14.sp) },
            title = { Text(text = "Replace what is on this phone?", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Black) },
            text = {
                Text(
                    text = "Everything recorded on this phone will be replaced by what is in the backup. " +
                        "Keys and tokens on this phone stay as they are.",
                    color = BodyText, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
                )
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = White,
        )
    }

    if (askDelete) {
        DeleteDialog(
            onConfirm = {
                askDelete = false
                viewModel.deleteEverything()
            },
            onDismiss = { askDelete = false },
        )
    }
}

@Composable
private fun StatusCard(busy: String?, result: String?, ok: Boolean, deleted: Boolean, onDismiss: () -> Unit, onStartAgain: () -> Unit) {
    if (busy == null && result == null) return
    SoftCard(modifier = Modifier.fillMaxWidth(), color = if (busy != null || ok) LightPink else Lavender, shadowColor = HotPink, shadowAlpha = 0.18f, gap = 10.dp) {
        if (busy != null) {
            Text(text = busy, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = DeepPink, trackColor = White)
            Text(text = "Keep this screen open until it finishes.", color = BodyText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        } else if (result != null) {
            Text(text = result, color = Ink, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (deleted) TextLink(text = "Start again", onClick = onStartAgain)
                TextLink(text = "Done", onClick = onDismiss)
            }
        }
    }
}

private val staysLines = listOf(
    "Everything you record is stored encrypted on this phone, with a key held in the phone's secure hardware.",
    "No analytics, no ads, and no crash reports that carry health data.",
    "Voice check-ins are turned into words and measures on the phone. The audio is then deleted, unless you choose to keep it.",
    "Photos are stored encrypted beside their entries and leave only when you tap to ask about one.",
    "Tokens and keys for connected accounts are kept in encrypted storage and never go into a backup.",
    "Backups are sealed with your password. The app never keeps a cloud copy.",
)

@Composable
private fun StaysCard() {
    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
        CardTitle("What stays on this phone")
        for (line in staysLines) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Dot(color = LightPink, size = 9.dp, modifier = Modifier.padding(top = 5.dp))
                BodyLine(text = line, modifier = Modifier.weight(1f))
            }
        }
    }
}

private val logClock = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())

private fun featureName(feature: String): String = when (feature) {
    AssistantFeatures.CARD -> "Card interpretation"
    AssistantFeatures.PHOTO -> "Photo question"
    AssistantFeatures.CHECKIN -> "Check-in ratings"
    AssistantFeatures.NARRATIVE -> "Weekly narrative"
    else -> feature.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
private fun LeftCard(log: List<AssistantCallLog>) {
    var showAll by remember { mutableStateOf(false) }
    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
        CardTitle("What has left this phone")
        BodyLine(
            text = "Weather and air quality requests send only your town, rounded to about 10 km. " +
                "Earthquake and volcano feeds are fetched for the whole world without your location, then filtered here.",
        )
        if (log.isEmpty()) {
            MutedLine(text = "Nothing has been sent to the assistant from this phone.")
        } else {
            Text(
                text = if (log.size == 1) "One assistant call" else "${log.size} assistant calls",
                color = Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
            )
            val shown = if (showAll) log else log.take(LOG_PREVIEW)
            for (call in shown) LogRow(call)
            if (log.size > LOG_PREVIEW) {
                TextLink(text = if (showAll) "Show fewer" else "Show all ${log.size}", onClick = { showAll = !showAll })
            }
        }
    }
}

private const val LOG_PREVIEW = 10

@Composable
private fun LogRow(call: AssistantCallLog) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = featureName(call.feature), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                text = if (call.ok) "sent" else "did not go through",
                color = if (call.ok) DeepPink else Muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
            )
        }
        MutedLine(
            text = Instant.ofEpochMilli(call.atUtcMs).atZone(ZoneId.systemDefault()).format(logClock).lowercase(Locale.getDefault()),
            fontSize = 11.sp,
        )
        BodyLine(text = call.sentSummary, fontSize = 13.sp)
    }
}

@Composable
private fun BackupCard(busy: Boolean, onSave: (password: String, includePhotos: Boolean) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var photos by remember { mutableStateOf(false) }
    val problem = if (password.isEmpty()) null else BackupCrypto.passwordProblem(password)
    val mismatch = confirm.isNotEmpty() && confirm != password
    val ready = password.isNotEmpty() && problem == null && confirm == password && !busy

    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
        CardTitle("Make a backup")
        BodyLine(
            text = "One encrypted file you keep yourself, on your own Drive or over a cable. " +
                "It opens only with this password, and the password cannot be recovered.",
        )
        SecretField(value = password, onChange = { password = it }, placeholder = "Password, at least 10 characters")
        SecretField(value = confirm, onChange = { confirm = it }, placeholder = "The same password again")
        val note = problem ?: if (mismatch) "The two passwords are not the same yet." else null
        if (note != null) Text(text = note, color = DeepPink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = "Include photos", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                MutedLine(text = if (photos) "Journal photos go in too. The file will be larger." else "Records and settings only. Photos stay on this phone.")
            }
            PinkSwitch(checked = photos, onChange = { photos = it })
        }
        PrimaryButton(
            text = "Choose where to save",
            onClick = {
                onSave(password, photos)
                password = ""
                confirm = ""
            },
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RestoreCard(busy: Boolean, onChoose: (password: String) -> Unit) {
    var password by remember { mutableStateOf("") }
    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
        CardTitle("Restore from a backup")
        BodyLine(text = "Replaces what is on this phone with what is in a backup file. You will be asked once more before anything changes.")
        SecretField(value = password, onChange = { password = it }, placeholder = "The backup's password")
        PrimaryButton(
            text = "Choose a backup",
            onClick = {
                onChoose(password)
                password = ""
            },
            enabled = password.isNotEmpty() && !busy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SecretField(value: String, onChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = Muted, fontSize = 13.sp) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HotPink, unfocusedBorderColor = HairLine, cursorColor = HotPink),
    )
}

@Composable
private fun DeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val ready = VaultCodec.deleteConfirmed(typed)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            PillButton(
                text = "Delete everything",
                onClick = onConfirm,
                background = Ink,
                textColor = White,
                fontWeight = FontWeight.Black,
                shadowAlpha = 0f,
                enabled = ready,
            )
        },
        dismissButton = { TextLink(text = "Keep it all", onClick = onDismiss, fontSize = 14.sp) },
        title = { Text(text = "Delete everything?", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Every record, photo, recording, setting, key, and token on this phone will be removed. " +
                        "Type delete to confirm.",
                    color = BodyText, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("delete", color = Muted, fontSize = 13.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HotPink, unfocusedBorderColor = HairLine, cursorColor = HotPink),
                )
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = White,
    )
}
