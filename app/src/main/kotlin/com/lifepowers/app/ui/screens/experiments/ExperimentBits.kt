package com.lifepowers.app.ui.screens.experiments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lifepowers.app.experiments.ExperimentMath
import com.lifepowers.app.ui.components.BodyLine
import com.lifepowers.app.ui.components.Chip
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.PrimaryButton
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.WrapRow
import com.lifepowers.app.ui.components.softShadow
import com.lifepowers.app.ui.screens.patterns.PatternLabels
import com.lifepowers.app.ui.theme.Ground
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.Lavender
import com.lifepowers.app.ui.theme.LightPink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.app.ui.theme.SoftBlue
import com.lifepowers.app.ui.theme.White
import com.lifepowers.core.Condition
import com.lifepowers.core.Metrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDate

/*
 * Small pieces shared by the experiment screens. Words only, no icons; status is color and shape:
 * on days hot pink, off days soft blue, days not followed a dashed lavender ring, days to come pale.
 */

internal object ExperimentChoices {
    val behaviors = listOf(
        "No screens after 9 pm",
        "No caffeine after noon",
        "A 10 minute walk after dinner",
        "Breathe 5 minutes before bed",
        "No alcohol",
    )

    val outcomes = listOf(
        Metrics.SLEEP_DEEP_MIN,
        Metrics.SLEEP_TOTAL_MIN,
        Metrics.HRV_NIGHT_AVG,
        Metrics.HR_RESTING,
        Metrics.SLEEP_ONSET_LATENCY_MIN,
        Metrics.SCORE_READINESS,
        Metrics.GLUCOSE_OVERNIGHT_MEAN,
        Metrics.GLUCOSE_FASTING,
        Metrics.VOICE_ENERGY_INDEX,
        Metrics.FEEL_EASE,
        Metrics.BREATH_MINUTES,
    )

    val blockDays = listOf(2, 3, 4)
    val lengths = listOf(14, 28, 42)

    /** Reminder hours offered, with null for no daily notice. */
    val reminderHours: List<Int?> = listOf(null, 7, 8, 9, 12, 18, 20)
}

/** "Deep sleep", "Overnight HRV": the friendly label, sentence case. */
internal fun outcomeName(key: String): String = ExperimentMath.upperFirst(PatternLabels.label(key))

internal fun hourWord(hour: Int?): String = when {
    hour == null -> "None"
    hour == 0 -> "12 am"
    hour < 12 -> "$hour am"
    hour == 12 -> "12 pm"
    else -> "${hour - 12} pm"
}

/** Today's date, re-emitted when the day turns over while a screen is open. */
internal fun experimentDays(): Flow<LocalDate> = flow {
    while (true) {
        emit(LocalDate.now())
        delay(60_000L)
    }
}.distinctUntilChanged()

internal val OnColor: Color = HotPink
internal val OffColor: Color = SoftBlue

/** "On day" in a hot pink pill, "Off day" in a soft blue pill, "Complete" in light pink. */
@Composable
internal fun ConditionPill(condition: Condition?, modifier: Modifier = Modifier, large: Boolean = false) {
    val (text, bg, fg) = when (condition) {
        Condition.ON -> Triple("On day", OnColor, White)
        Condition.OFF -> Triple("Off day", OffColor, Ink)
        null -> Triple("Complete", LightPink, Ink)
    }
    val shape = RoundedCornerShape(999.dp)
    Text(
        text = text,
        color = fg,
        fontSize = if (large) 16.sp else 12.sp,
        fontWeight = FontWeight.Black,
        modifier = modifier
            .softShadow(shape, if (condition == Condition.ON) HotPink else Ink, if (condition == Condition.ON) 0.25f else 0.08f, 6.dp)
            .clip(shape)
            .background(bg)
            .padding(horizontal = if (large) 16.dp else 12.dp, vertical = if (large) 8.dp else 6.dp),
    )
}

/**
 * Yes or the gentle no for one day. Tapping the answer already given clears it, since a day with no
 * answer counts as followed anyway.
 */
@Composable
internal fun AdherenceChoice(value: Boolean?, noText: String, onSet: (Boolean?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Chip(text = "Yes", selected = value == true, onClick = { onSet(if (value == true) null else true) })
        Chip(
            text = noText,
            background = if (value == false) Lavender else Ground,
            onClick = { onSet(if (value == false) null else false) },
        )
    }
}

/** How a day in the schedule is drawn. */
internal enum class DayLook { ON, OFF, MISSED, ON_LATER, OFF_LATER }

internal fun dayLook(condition: Condition, date: LocalDate, today: LocalDate, adhered: Boolean?): DayLook = when {
    date.isAfter(today) -> if (condition == Condition.ON) DayLook.ON_LATER else DayLook.OFF_LATER
    adhered == false -> DayLook.MISSED
    condition == Condition.ON -> DayLook.ON
    else -> DayLook.OFF
}

/** One day: a filled circle, a dashed lavender ring for a day not followed, pale for days to come. */
@Composable
internal fun DayDot(look: DayLook, modifier: Modifier = Modifier, ringed: Boolean = false) {
    Canvas(modifier = modifier) {
        val full = size.minDimension / 2f
        val ring = if (ringed) 2.dp.toPx() else 0f
        val gap = if (ringed) 2.5.dp.toPx() else 0f
        val r = full - ring - gap
        val c = Offset(size.width / 2f, size.height / 2f)
        when (look) {
            DayLook.ON -> drawCircle(color = OnColor, radius = r, center = c)
            DayLook.OFF -> drawCircle(color = OffColor, radius = r, center = c)
            DayLook.ON_LATER -> drawCircle(color = OnColor.copy(alpha = 0.22f), radius = r, center = c)
            DayLook.OFF_LATER -> drawCircle(color = OffColor.copy(alpha = 0.35f), radius = r, center = c)
            DayLook.MISSED -> {
                val stroke = 2.dp.toPx()
                drawCircle(color = Ground, radius = r, center = c)
                drawCircle(
                    color = Lavender,
                    radius = r - stroke / 2f,
                    center = c,
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f), 0f)),
                )
            }
        }
        if (ringed) drawCircle(color = Ink, radius = full - ring / 2f, center = c, style = Stroke(width = ring))
    }
}

/** A grid of seven days per row. [onTap] receives the index of the tapped day, when given. */
@Composable
internal fun DayGrid(
    looks: List<DayLook>,
    ringedIndex: Int?,
    modifier: Modifier = Modifier,
    maxDot: Dp = 34.dp,
    gap: Dp = 8.dp,
    onTap: ((Int) -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        looks.chunked(7).forEachIndexed { row, week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                week.forEachIndexed { col, look ->
                    val index = row * 7 + col
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        var m = Modifier.widthIn(max = maxDot).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(999.dp))
                        if (onTap != null) m = m.clickable { onTap(index) }
                        DayDot(look = look, modifier = m, ringed = index == ringedIndex)
                    }
                }
                repeat(7 - week.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun LegendItem(look: DayLook, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        DayDot(look = look, modifier = Modifier.size(11.dp))
        Text(text = text, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun Legend(items: List<Pair<DayLook, String>>) {
    WrapRow(horizontalGap = 14.dp, verticalGap = 6.dp) {
        for ((look, text) in items) LegendItem(look, text)
    }
}

/** A white or cream pill track with one light pink segment selected. */
@Composable
internal fun OptionPills(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    track: Color = Ground,
) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier.fillMaxWidth().clip(shape).background(track).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (on) LightPink else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = label, color = if (on) Ink else Muted, fontSize = 12.sp, fontWeight = if (on) FontWeight.Black else FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

/** A quiet confirm for ending and deleting. */
@Composable
internal fun ExperimentConfirm(title: String, body: String, confirmText: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        SoftCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp), gap = 10.dp) {
            Text(text = title, color = Ink, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black)
            BodyLine(text = body)
            Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(text = confirmText, onClick = onConfirm)
                PillButton(text = "Keep it", onClick = onDismiss, shadowAlpha = 0.08f)
            }
        }
    }
}
