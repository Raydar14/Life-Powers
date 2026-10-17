package com.lifepowers.app.ui.screens.journal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifepowers.app.domain.JournalRepository
import com.lifepowers.app.domain.Photo
import com.lifepowers.app.ui.components.TextLink
import com.lifepowers.app.ui.theme.BodyText
import com.lifepowers.app.ui.theme.HairLine
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.Lavender
import com.lifepowers.app.ui.theme.White
import com.lifepowers.core.Phase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Small pieces shared by the journal and tarot screens. Kept in this package on purpose so the
 * shared components stay untouched while other screens change around them.
 */

/** Route strings the navigation owner wires. */
internal object JournalRoutes {
    fun entry(id: Long): String = "journal/entry/$id"
    const val TAROT = "tarot"
    fun pull(id: Long): String = "tarot/pull/$id"
    fun pullForEntry(entryId: Long): String = "tarot/pull/0?entryId=$entryId"
}

/** Dates as words, formatted here with java.time. */
internal object JournalDates {
    private val weekdayLong = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
    private val monthDay = DateTimeFormatter.ofPattern("MMMM d", Locale.getDefault())
    private val withYear = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault())

    /** "Today", "Yesterday", "Thursday, October 15", or "October 15, 2025" for another year. */
    fun day(date: LocalDate, today: LocalDate = LocalDate.now()): String = when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.year == today.year -> date.format(weekdayLong)
        else -> date.format(withYear)
    }

    /** "October 15", or "October 15, 2025" for another year. */
    fun short(date: LocalDate, today: LocalDate = LocalDate.now()): String =
        if (date.year == today.year) date.format(monthDay) else date.format(withYear)
}

/** Counts as gentle words: once, twice, 3 times. */
internal fun timesWord(n: Int): String = when (n) {
    1 -> "once"
    2 -> "twice"
    else -> "$n times"
}

internal fun joinWords(parts: List<String>): String = when (parts.size) {
    0 -> ""
    1 -> parts[0]
    2 -> "${parts[0]} and ${parts[1]}"
    else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
}

/** A phase as friendly words for a sentence, or null when the phase is not known. */
internal fun phaseWords(phase: Phase): String? = when (phase) {
    Phase.MENSTRUAL -> "the menstrual phase"
    Phase.FOLLICULAR -> "the follicular phase"
    Phase.OVULATORY -> "the ovulatory phase"
    Phase.LUTEAL -> "the luteal phase"
    Phase.UNKNOWN -> null
    Phase.ACTIVE_1 -> "active week 1"
    Phase.ACTIVE_2 -> "active week 2"
    Phase.ACTIVE_3 -> "active week 3"
    Phase.HORMONE_FREE -> "the hormone free week"
    Phase.DOSE_D0_2 -> "the first days after a dose"
    Phase.DOSE_D3_6 -> "the middle days of a dose"
    Phase.DOSE_D7_PLUS -> "the late days of a dose"
    Phase.GESTATIONAL_WEEK -> "pregnancy"
}

/** "The Star" or "The Star, reversed". */
internal fun cardLine(card: String, reversed: Boolean): String = if (reversed) "$card, reversed" else card

/**
 * Decodes stored photo bytes into small bitmaps off the main thread, sampled down with inSampleSize
 * and turned upright from the EXIF orientation. One cache per view model.
 */
internal class PhotoThumbs(private val journalRepository: JournalRepository, private val targetPx: Int, maxEntries: Int = 48) {
    private val cache = LruCache<Long, ImageBitmap>(maxEntries)

    suspend fun load(photo: Photo): ImageBitmap? {
        cache.get(photo.id)?.let { return it }
        val bytes = withContext(Dispatchers.IO) { runCatching { journalRepository.loadPhoto(photo) }.getOrNull() } ?: return null
        val bitmap = withContext(Dispatchers.Default) { runCatching { decode(bytes, targetPx) }.getOrNull() } ?: return null
        cache.put(photo.id, bitmap)
        return bitmap
    }

    fun forget(photoId: Long) {
        cache.remove(photoId)
    }

    companion object {
        fun decode(bytes: ByteArray, targetPx: Int): ImageBitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw: Bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
            val degrees = runCatching {
                when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }.getOrDefault(0f)
            val upright = if (degrees != 0f) {
                val matrix = Matrix().apply { postRotate(degrees) }
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
            } else {
                raw
            }
            return upright.asImageBitmap()
        }
    }
}

/** A rounded photo tile. Shows a soft lavender fill until the thumbnail has decoded. */
@Composable
internal fun PhotoTile(photo: Photo, load: suspend (Photo) -> ImageBitmap?, modifier: Modifier = Modifier, radius: Dp = 28.dp) {
    val bitmap by produceState<ImageBitmap?>(null, photo.id) { value = load(photo) }
    val shape = RoundedCornerShape(radius)
    Box(modifier = modifier.clip(shape).background(Lavender.copy(alpha = 0.45f))) {
        val b = bitmap
        if (b != null) {
            Image(
                bitmap = b,
                contentDescription = photo.caption ?: "Photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** A heavy title with an optional trailing control. No line above it. */
@Composable
internal fun JournalTitle(title: String, modifier: Modifier = Modifier, size: TextUnit = 26.sp, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title, color = Ink, fontSize = size, lineHeight = (size.value * 1.15f).sp, fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) trailing()
    }
}

/** The title of a card, ink and heavy, never a small label. */
@Composable
internal fun CardTitle(text: String, modifier: Modifier = Modifier, fontSize: TextUnit = 15.sp) {
    Text(text = text, color = Ink, fontSize = fontSize, lineHeight = (fontSize.value * 1.25f).sp, fontWeight = FontWeight.Black, modifier = modifier)
}

@Composable
internal fun softFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = HotPink,
    unfocusedBorderColor = HairLine,
    cursorColor = HotPink,
    focusedContainerColor = White,
    unfocusedContainerColor = White,
    focusedTextColor = Ink,
    unfocusedTextColor = Ink,
)

@Composable
internal fun SoftSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = White, checkedTrackColor = HotPink, checkedBorderColor = HotPink,
            uncheckedThumbColor = White, uncheckedTrackColor = HairLine, uncheckedBorderColor = HairLine,
        ),
    )
}

/** A soft confirm dialog with word buttons. */
@Composable
internal fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextLink(text = confirm, onClick = onConfirm, fontSize = 14.sp) },
        dismissButton = { TextLink(text = "Keep it", onClick = onDismiss, fontSize = 14.sp) },
        title = { Text(text = title, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text(text = body, color = BodyText, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = White,
    )
}
