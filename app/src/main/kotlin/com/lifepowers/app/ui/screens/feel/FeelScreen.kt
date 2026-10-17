package com.lifepowers.app.ui.screens.feel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifepowers.app.ui.components.Chip
import com.lifepowers.app.ui.components.CloudPresets
import com.lifepowers.app.ui.components.Clouds
import com.lifepowers.app.ui.components.InkButton
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.PrimaryButton
import com.lifepowers.app.ui.components.ScreenHeader
import com.lifepowers.app.ui.components.SectionLabel
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.components.WrapRow
import com.lifepowers.app.ui.components.softShadow
import com.lifepowers.app.ui.theme.Ground
import com.lifepowers.app.ui.theme.HairLine
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.Lavender
import com.lifepowers.app.ui.theme.LightPink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.app.ui.theme.SoftBlue
import com.lifepowers.app.ui.theme.White
import com.lifepowers.core.FeelWords
import kotlin.math.roundToInt

@Composable
fun FeelScreen(onDone: () -> Unit, onSayMore: () -> Unit, viewModel: FeelViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var addingWord by remember { mutableStateOf(false) }
    var ownWord by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        Clouds(CloudPresets.feel)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenHeader(title = "How are you right now, in your words?", titleSize = 28.sp) {
                TextLink(text = "Skip", onClick = onDone)
            }

            SoftCard(modifier = Modifier.fillMaxWidth(), gap = 12.dp) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    SectionLabel("Texture, pick one or two")
                    Text(text = "your words first", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                WrapRow {
                    for (word in state.words) {
                        val on = state.picked.any { it.word == word.word }
                        Chip(
                            text = word.word,
                            selected = on,
                            background = if (word.origin == "user") LightPink else Ground,
                            fontSize = 14.sp,
                            onClick = { viewModel.toggle(word) },
                        )
                    }
                    Chip(text = "my own word", dashed = true, fontSize = 14.sp, onClick = { addingWord = !addingWord })
                }
                if (addingWord) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = ownWord,
                            onValueChange = { ownWord = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("fizzy, so done, a lot", color = Muted) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HotPink, unfocusedBorderColor = HairLine, cursorColor = HotPink),
                        )
                        InkButton(text = "Add", onClick = {
                            viewModel.addOwnWord(ownWord)
                            ownWord = ""
                            addingWord = false
                        })
                    }
                }
            }

            SoftCard(modifier = Modifier.fillMaxWidth(), gap = 12.dp) {
                SectionLabel("Tempo, how fast are you running")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .background(Ground)
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (tempo in FeelWords.Tempo.values()) {
                        val on = state.tempo == tempo
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (on) Ink else Ground)
                                .clickable { viewModel.setTempo(tempo) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = tempo.name.lowercase(), color = if (on) White else Muted,
                                fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            SoftCard(modifier = Modifier.fillMaxWidth(), gap = 12.dp) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    SectionLabel("Tint, the color today feels like")
                    Text(text = "no right answer", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                TintField(x = state.tintX, y = state.tintY, onMove = { x, y -> viewModel.setTint(x, y) })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(
                    text = "Say more", onClick = onSayMore, modifier = Modifier.weight(1f),
                    fontSize = 14.sp, shadowAlpha = 0.08f,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                )
                PrimaryButton(
                    text = "That's me today", onClick = { viewModel.save(onDone) }, modifier = Modifier.weight(1.4f),
                    enabled = state.canSave && !state.saving,
                )
            }
        }
    }
}

/** A soft field blended from the palette. Drag or tap to place the dot. */
@Composable
private fun TintField(x: Float?, y: Float?, onMove: (Float, Float) -> Unit) {
    var fieldSize by remember { mutableStateOf(IntSize.Zero) }
    val stops = arrayOf(0f to HotPink, 0.3f to LightPink, 0.5f to Ground, 0.72f to Lavender, 1f to SoftBlue)
    val dotSize = 28.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(24.dp))
            .drawBehind {
                drawRoundRect(
                    brush = Brush.linearGradient(*stops, start = Offset.Zero, end = Offset(size.width, size.height)),
                    cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                )
            }
            .onSizeChanged { fieldSize = it }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    if (size.width > 0 && size.height > 0) onMove(pos.x / size.width, pos.y / size.height)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    if (size.width > 0 && size.height > 0) onMove(change.position.x / size.width, change.position.y / size.height)
                }
            },
    ) {
        if (x != null && y != null && fieldSize.width > 0) {
            Box(
                modifier = Modifier
                    .offset {
                        val half = dotSize.roundToPx() / 2
                        IntOffset((x * fieldSize.width).roundToInt() - half, (y * fieldSize.height).roundToInt() - half)
                    }
                    .size(dotSize)
                    .softShadow(RoundedCornerShape(999.dp), Ink, 0.3f, 8.dp)
                    .clip(CircleShape)
                    .background(White)
                    .border(3.dp, Ink, CircleShape),
            )
        } else {
            Text(
                text = "touch anywhere",
                color = Ink.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}
