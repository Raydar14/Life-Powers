package com.lifepowers.app.ui.screens.journal

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepowers.app.domain.Photo
import com.lifepowers.app.domain.TarotPull
import com.lifepowers.app.ui.components.BodyLine
import com.lifepowers.app.ui.components.Chip
import com.lifepowers.app.ui.components.CloudPresets
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.HotButton
import com.lifepowers.app.ui.components.InkButton
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.PrimaryButton
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.components.WrapRow
import com.lifepowers.app.ui.components.softShadow
import com.lifepowers.app.ui.screens.assistant.AssistantPreviewDialog
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.LightPink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.app.ui.theme.White
import kotlinx.coroutines.launch

/**
 * One journal entry: words, a voice note, tags, photos, and the cards pulled for it.
 *
 * [onNavigate] is optional so the navigation owner can wire the screen as JournalEntryScreen(entryId, onBack).
 * When it is passed, attached cards open and "Add a card to this entry" appears.
 */
@Composable
fun JournalEntryScreen(
    entryId: Long,
    onBack: () -> Unit,
    onNavigate: ((String) -> Unit)? = null,
    viewModel: JournalEntryViewModel = hiltViewModel(),
) {
    LaunchedEffect(entryId) { viewModel.load(entryId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form = state.form
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.closed) { if (state.closed) onBack() }
    BackHandler(enabled = !state.closed) { viewModel.back() }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startListening() else viewModel.micDenied()
    }
    val startTalking: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startListening() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.addPhotoFromUri(uri)
    }
    val takeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) viewModel.addPhotoFromBitmap(bitmap)
    }

    var confirmDelete by remember { mutableStateOf(false) }
    var photoToRemove by remember { mutableStateOf<Photo?>(null) }

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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextLink(text = "Back", onClick = { viewModel.back() }, fontSize = 13.sp)
                if (state.saving) MutedLine(text = "Saving")
            }

            JournalTitle(title = JournalDates.day(form.localDate)) {
                if (state.listening) {
                    HotButton(text = "Stop", onClick = { viewModel.stopListening() })
                } else {
                    PillButton(
                        text = "Talk", onClick = startTalking, background = LightPink, shadowColor = HotPink, shadowAlpha = 0.18f,
                        fontSize = 14.sp, fontWeight = FontWeight.Black,
                    )
                }
            }

            if (state.loaded && state.missing) {
                SoftCard(modifier = Modifier.fillMaxWidth(), gap = 4.dp) {
                    CardTitle("This entry is not here any more")
                    MutedLine(text = "It may have been deleted. Go back to the journal to start a new one.")
                }
            } else if (state.loaded) {
                EntryBody(
                    state = state,
                    viewModel = viewModel,
                    onAddPhoto = { pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onTakePhoto = {
                        val launched = runCatching { takeLauncher.launch(null) }.isSuccess
                        if (!launched) viewModel.cameraUnavailable()
                    },
                    onRemovePhoto = { photoToRemove = it },
                    onOpenPull = onNavigate?.let { nav -> { pull: TarotPull -> nav(JournalRoutes.pull(pull.id)) } },
                    onAddPull = onNavigate?.let { nav ->
                        {
                            scope.launch {
                                val id = viewModel.ensureSaved()
                                if (id != null) nav(JournalRoutes.pullForEntry(id))
                            }
                            Unit
                        }
                    },
                    onDelete = { confirmDelete = true },
                )
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this entry?",
            body = "The words, the voice note, and its photos are deleted from the phone.",
            confirm = "Delete",
            onConfirm = { confirmDelete = false; viewModel.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
    state.photoAsk?.preview?.let { preview ->
        AssistantPreviewDialog(
            preview = preview,
            sending = state.photoAsk?.sending == true,
            onSend = { viewModel.sendPhotoAsk() },
            onCancel = { viewModel.cancelPhotoAsk() },
        )
    }
    photoToRemove?.let { photo ->
        ConfirmDialog(
            title = "Remove this photo?",
            body = "It is deleted from the phone.",
            confirm = "Remove",
            onConfirm = { photoToRemove = null; viewModel.removePhoto(photo) },
            onDismiss = { photoToRemove = null },
        )
    }
}

/** Everything below the title, emitted into the screen's column so its spacing applies. */
@Composable
private fun EntryBody(
    state: EntryUiState,
    viewModel: JournalEntryViewModel,
    onAddPhoto: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemovePhoto: (Photo) -> Unit,
    onOpenPull: ((TarotPull) -> Unit)?,
    onAddPull: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    val form = state.form

    state.notice?.let { notice ->
        SoftCard(modifier = Modifier.fillMaxWidth(), color = LightPink, shadowColor = HotPink, shadowAlpha = 0.14f, gap = 2.dp) {
            BodyLine(text = notice, color = Ink, fontSize = 13.sp)
            TextLink(text = "Okay", onClick = { viewModel.clearNotice() })
        }
    }

    if (state.listening) {
        SoftCard(modifier = Modifier.fillMaxWidth(), gap = 6.dp) {
            CardTitle("Listening")
            BodyLine(text = state.partial.ifBlank { "..." }, fontSize = 15.sp, lineHeight = 22.sp)
            MutedLine(text = "Tap Stop when you are done. Your words join this entry.")
        }
    }

    WritingField(text = form.text, onChange = { viewModel.setText(it) })

    if (form.voiceTranscript.isNotBlank()) {
        SoftCard(modifier = Modifier.fillMaxWidth(), gap = 8.dp) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                CardTitle("What you said")
                TextLink(text = "Clear", onClick = { viewModel.setTranscript("") })
            }
            OutlinedTextField(
                value = form.voiceTranscript,
                onValueChange = { viewModel.setTranscript(it) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = RoundedCornerShape(20.dp),
                textStyle = TextStyle(color = Ink, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
                colors = softFieldColors(),
            )
        }
    }

    TagsCard(tags = form.tags, onAdd = { viewModel.addTags(it) }, onRemove = { viewModel.removeTag(it) })

    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 4.dp) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                CardTitle("Let the word markers read this entry")
                MutedLine(text = "Read on this phone only. The entry never leaves the phone.")
            }
            SoftSwitch(checked = form.scoreWords, onChange = { viewModel.setScoreWords(it) })
        }
    }

    PhotosCard(
        photos = form.photos,
        busy = state.photoBusy,
        thumb = { photo -> viewModel.thumb(photo) },
        onAdd = onAddPhoto,
        onTake = onTakePhoto,
        onRemove = onRemovePhoto,
        onAsk = if (state.photoAssist) ({ photo: Photo -> viewModel.askAboutPhoto(photo) }) else null,
        preparingPhotoId = state.photoAsk?.takeIf { it.preparing || it.sending }?.photoId,
    )

    val ask = state.photoAsk
    if (ask != null && ask.questions.isNotEmpty()) {
        PhotoQuestionsCard(
            ask = ask,
            photo = form.photos.firstOrNull { it.id == ask.photoId },
            thumb = { photo -> viewModel.thumb(photo) },
            onAnswer = { i, text -> viewModel.setPhotoAnswer(i, text) },
            onAdd = { viewModel.addAnswersToEntry() },
            onDismiss = { viewModel.cancelPhotoAsk() },
        )
    }

    PullsSection(pulls = form.pulls, onOpen = onOpenPull, onAdd = onAddPull)

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PrimaryButton(text = if (state.saving) "Saving" else "Save", onClick = { viewModel.save() }, modifier = Modifier.fillMaxWidth(), enabled = !state.saving)
        if (form.id != 0L) {
            TextLink(text = "Delete this entry", onClick = onDelete, fontSize = 13.sp)
        }
    }
}

/** The large soft writing field. */
@Composable
private fun WritingField(text: String, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    TextField(
        value = text,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().softShadow(shape, Ink, 0.14f, 10.dp),
        placeholder = { Text(text = "Write anything. How was it, really?", color = Muted, fontSize = 16.sp) },
        minLines = 8,
        shape = shape,
        textStyle = TextStyle(color = Ink, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = White,
            unfocusedContainerColor = White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = HotPink,
            focusedTextColor = Ink,
            unfocusedTextColor = Ink,
        ),
    )
}

@Composable
private fun TagsCard(tags: List<String>, onAdd: (String) -> Unit, onRemove: (String) -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    val add = {
        onAdd(draft)
        draft = ""
    }
    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 8.dp) {
        CardTitle("Tags")
        if (tags.isNotEmpty()) {
            WrapRow {
                for (tag in tags) Chip(text = tag, background = LightPink, onClick = { onRemove(tag) })
            }
            MutedLine(text = "Tap a tag to take it off.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(text = "Add a word", color = Muted, fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(999.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                colors = softFieldColors(),
            )
            InkButton(text = "Add", onClick = { add() })
        }
    }
}

@Composable
private fun PhotosCard(
    photos: List<Photo>,
    busy: Boolean,
    thumb: suspend (Photo) -> ImageBitmap?,
    onAdd: () -> Unit,
    onTake: () -> Unit,
    onRemove: (Photo) -> Unit,
    onAsk: ((Photo) -> Unit)? = null,
    preparingPhotoId: Long? = null,
) {
    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
        CardTitle("Photos")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(text = "Add photo", onClick = onAdd, background = LightPink, shadowColor = HotPink, shadowAlpha = 0.16f, enabled = !busy)
            PillButton(text = "Take photo", onClick = onTake, background = LightPink, shadowColor = HotPink, shadowAlpha = 0.16f, enabled = !busy)
        }
        if (busy) MutedLine(text = "Keeping the photo")
        if (photos.isEmpty() && !busy) {
            MutedLine(text = "Photos are stored encrypted on this phone, beside this entry.")
        }
        for (row in photos.chunked(3)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (photo in row) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        PhotoTile(photo = photo, load = thumb, modifier = Modifier.fillMaxWidth().aspectRatio(1f), radius = 28.dp)
                        if (onAsk != null) {
                            if (preparingPhotoId == photo.id) {
                                MutedLine(text = "Getting it ready", textAlign = TextAlign.Center)
                            } else {
                                TextLink(text = "Ask about this photo", onClick = { onAsk(photo) })
                            }
                        }
                        TextLink(text = "Remove", onClick = { onRemove(photo) })
                    }
                }
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/** The assistant's questions about one photo, a field per question, and "Add to entry". */
@Composable
private fun PhotoQuestionsCard(
    ask: PhotoAskState,
    photo: Photo?,
    thumb: suspend (Photo) -> ImageBitmap?,
    onAnswer: (Int, String) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    SoftCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (photo != null) {
                PhotoTile(photo = photo, load = thumb, modifier = Modifier.weight(0.28f).aspectRatio(1f), radius = 20.dp)
            }
            Column(modifier = Modifier.weight(0.72f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                CardTitle("Questions about this photo")
                MutedLine(text = "From the assistant. Answer any you like; your answers join this entry.")
            }
        }
        ask.questions.forEachIndexed { i, question ->
            BodyLine(text = question, color = Ink, fontSize = 14.sp, lineHeight = 20.sp)
            OutlinedTextField(
                value = ask.answers.getOrElse(i) { "" },
                onValueChange = { onAnswer(i, it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = "Your answer", color = Muted, fontSize = 13.sp) },
                minLines = 2,
                shape = RoundedCornerShape(20.dp),
                textStyle = TextStyle(color = Ink, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = softFieldColors(),
            )
        }
        ask.error?.let { BodyLine(text = it, color = Ink) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(text = "Add to entry", onClick = onAdd, background = LightPink, shadowColor = HotPink, shadowAlpha = 0.16f)
            TextLink(text = "Not now", onClick = onDismiss, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PullsSection(pulls: List<TarotPull>, onOpen: ((TarotPull) -> Unit)?, onAdd: (() -> Unit)?) {
    if (pulls.isEmpty() && onAdd == null) return
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (pull in pulls.sortedBy { it.createdUtcMs }) {
            SoftCard(modifier = Modifier.fillMaxWidth(), gap = 4.dp, onClick = onOpen?.let { open -> { open(pull) } }) {
                CardTitle(cardLine(pull.card, pull.reversed))
                val where = listOfNotNull(pull.spread?.takeIf { it.isNotBlank() }, pull.position?.takeIf { it.isNotBlank() }, pull.deck?.takeIf { it.isNotBlank() })
                if (where.isNotEmpty()) MutedLine(text = where.joinToString(", "))
                val note = pull.note
                if (!note.isNullOrBlank()) BodyLine(text = note, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        if (onAdd != null) {
            PillButton(
                text = "Add a card to this entry", onClick = onAdd,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}
