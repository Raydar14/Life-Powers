package com.lifepowers.app.ui.screens.goals

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lifepowers.app.ui.components.BodyLine
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.PrimaryButton
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.components.TierDot
import com.lifepowers.app.ui.components.softShadow
import com.lifepowers.app.ui.theme.Ground
import com.lifepowers.app.ui.theme.HairLine
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.LightPink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.app.ui.theme.White
import com.lifepowers.core.PeriodProgress
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/*
 * Small pieces shared by the goals screens. Kept in this package so the shared components stay
 * untouched. Words only, no icons; status is color and shape.
 */

/** A white pill track with one light pink segment selected, like the cadence filter in the mockup. */
@Composable
internal fun SegmentPills(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    track: Color = White,
    shadowAlpha: Float = 0.07f,
) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .softShadow(shape, Ink, shadowAlpha, if (shadowAlpha > 0f) 8.dp else 0.dp)
            .clip(shape)
            .background(track)
            .padding(5.dp),
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
                Text(
                    text = label,
                    color = if (on) Ink else Muted,
                    fontSize = 12.sp,
                    fontWeight = if (on) FontWeight.Black else FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * One soft filled circle per period in the forecast colors, oldest first. The open period wears
 * the ink ring. Untouched periods are pale cream with a dashed lavender edge, as TierDot draws them.
 */
@Composable
internal fun PeriodDots(history: List<PeriodProgress>, today: LocalDate, modifier: Modifier = Modifier, maxDot: Dp = 18.dp) {
    if (history.isEmpty()) return
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val gap = 5.dp
        val ring = 8.dp
        val n = history.size
        val fit = (maxWidth - gap * (n - 1) - ring) / n
        val dot = minOf(maxDot, fit).coerceAtLeast(6.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            history.forEachIndexed { i, p ->
                val current = i == history.lastIndex && p.period.contains(today)
                TierDot(tier = p.state.tier, size = if (current) dot + ring else dot, ringed = current, ringGap = 2.dp)
            }
        }
    }
}

/** One day in the four week grid: hot pink when checked, pale with a hairline when not. */
@Composable
internal fun GridDot(checked: Boolean, today: Boolean, diameter: Dp) {
    Canvas(modifier = Modifier.size(diameter)) {
        val full = this.size.minDimension / 2f
        val ring = if (today) 2.dp.toPx() else 0f
        val gap = if (today) 2.dp.toPx() else 0f
        val r = full - ring - gap
        if (checked) {
            drawCircle(color = HotPink, radius = r)
        } else {
            drawCircle(color = Ground, radius = r)
            drawCircle(color = HairLine, radius = r - 0.75.dp.toPx(), style = Stroke(width = 1.5.dp.toPx()))
        }
        if (today) drawCircle(color = Ink, radius = full - ring / 2f, style = Stroke(width = ring))
    }
}

/** A large friendly numeral between Less and More. */
@Composable
internal fun Stepper(
    value: String,
    unit: String,
    onLess: () -> Unit,
    onMore: () -> Unit,
    lessEnabled: Boolean = true,
    moreEnabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillButton(text = "Less", onClick = onLess, enabled = lessEnabled, shadowAlpha = 0.1f)
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = value, color = HotPink, fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(text = unit, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        }
        PillButton(text = "More", onClick = onMore, enabled = moreEnabled, shadowAlpha = 0.1f)
    }
}

@Composable
internal fun PinkSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
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
internal fun SwitchRow(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, color = Ink, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.ExtraBold)
            if (detail != null) MutedLine(text = detail)
        }
        PinkSwitch(checked = checked, onChange = onChange)
    }
}

/** M T W T F S S. An empty set means every day, so every pill shows on. */
@Composable
internal fun DayPills(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    val letters = listOf("M", "T", "W", "T", "F", "S", "S")
    val shape = RoundedCornerShape(999.dp)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.values().forEachIndexed { i, day ->
            val on = selected.isEmpty() || day in selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(shape)
                    .background(if (on) HotPink else HairLine)
                    .clickable { onToggle(day) },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = letters[i], color = if (on) White else Muted, fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

/** The Material3 clock picker inside a soft card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimePickDialog(initial: LocalTime, confirmText: String, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val pickerState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SoftCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            gap = 14.dp,
        ) {
            Text(text = "Remind me at", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(
                    state = pickerState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = Ground,
                        selectorColor = HotPink,
                        containerColor = White,
                        periodSelectorSelectedContainerColor = LightPink,
                        timeSelectorSelectedContainerColor = LightPink,
                        timeSelectorUnselectedContainerColor = Ground,
                    ),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(text = confirmText, onClick = { onPick(LocalTime.of(pickerState.hour, pickerState.minute)) })
                PillButton(text = "Not now", onClick = onDismiss, shadowAlpha = 0.08f)
            }
        }
    }
}

/** A quiet confirm for archive and delete. */
@Composable
internal fun ConfirmDialog(title: String, body: String, confirmText: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        SoftCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            gap = 10.dp,
        ) {
            Text(text = title, color = Ink, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black)
            BodyLine(text = body)
            Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(text = confirmText, onClick = onConfirm)
                PillButton(text = "Keep it", onClick = onDismiss, shadowAlpha = 0.08f)
            }
        }
    }
}
