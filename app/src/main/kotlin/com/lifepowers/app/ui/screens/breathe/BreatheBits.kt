package com.lifepowers.app.ui.screens.breathe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lifepowers.app.ui.components.BodyLine
import com.lifepowers.app.ui.components.CloudSpec
import com.lifepowers.app.ui.components.Fmt
import com.lifepowers.app.ui.components.MutedLine
import com.lifepowers.app.ui.components.PillButton
import com.lifepowers.app.ui.components.PrimaryButton
import com.lifepowers.app.ui.components.SoftCard
import com.lifepowers.app.ui.theme.BodyText
import com.lifepowers.app.ui.theme.DeepLavenderLine
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.Lavender
import com.lifepowers.app.ui.theme.LightPink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.app.ui.theme.SoftBlue
import com.lifepowers.core.BreathIssue
import com.lifepowers.core.BreathSeverity
import kotlin.math.ceil
import kotlin.math.floor

/*
 * Small pieces shared by the breathing screens. Kept in this package so the shared components stay
 * untouched. Words only, no icons; status is color and shape.
 */

internal object BreatheClouds {
    val home = listOf(
        CloudSpec(LightPink, 240.dp, x = (-70).dp, y = (-90).dp, alpha = 0.42f, fromEnd = true),
        CloudSpec(SoftBlue, 200.dp, x = (-110).dp, y = 70.dp, alpha = 0.3f),
    )
    val edit = listOf(
        CloudSpec(Lavender, 230.dp, x = (-70).dp, y = (-90).dp, alpha = 0.4f, fromEnd = true),
    )
    val calm = listOf(
        CloudSpec(LightPink, 300.dp, x = (-110).dp, y = (-80).dp, alpha = 0.45f, fromEnd = true),
        CloudSpec(Lavender, 260.dp, x = (-120).dp, y = 260.dp, alpha = 0.35f),
        CloudSpec(SoftBlue, 280.dp, x = (-90).dp, y = (-60).dp, alpha = 0.35f, fromEnd = true, fromBottom = true),
    )
    val dim = listOf(
        CloudSpec(Lavender, 300.dp, x = (-110).dp, y = (-80).dp, alpha = 0.12f, fromEnd = true),
        CloudSpec(LightPink, 260.dp, x = (-120).dp, y = (-60).dp, alpha = 0.08f, fromBottom = true),
    )
    val summary = listOf(
        CloudSpec(LightPink, 240.dp, x = (-60).dp, y = (-90).dp, alpha = 0.4f),
        CloudSpec(Lavender, 200.dp, x = (-90).dp, y = 40.dp, alpha = 0.3f, fromEnd = true),
    )
}

/**
 * The breath, drawn: a soft light pink circle that swells on the in breath and settles on the out
 * breath, inside a faint lavender ring that marks a full breath. [fill] is read while drawing, so a
 * frame loop only redraws the shape.
 */
@Composable
internal fun BreathOrb(fill: () -> Float, modifier: Modifier = Modifier, dim: Boolean = false) {
    Canvas(modifier = modifier) {
        val maxR = size.minDimension / 2f
        val full = maxR * 0.86f
        val minR = maxR * 0.4f
        val f = fill().coerceIn(0f, 1f)
        val r = minR + (full - minR) * f
        val a = if (dim) 0.35f else 1f
        drawCircle(color = Lavender.copy(alpha = 0.5f * a), radius = full, style = Stroke(width = 2.dp.toPx()))
        drawCircle(color = LightPink.copy(alpha = 0.35f * a), radius = r * 1.15f)
        drawCircle(color = LightPink.copy(alpha = a), radius = r)
        drawCircle(color = HotPink.copy(alpha = (0.1f + 0.2f * f) * a), radius = r * 0.62f)
    }
}

/**
 * A smooth heart rate line with the area under it filled in light pink. Gaps (nulls) break the line.
 * When [slots] is larger than the data, the line sits at the right edge, as on a live trace.
 */
@Composable
internal fun HeartLine(values: List<Double?>, modifier: Modifier = Modifier, slots: Int = values.size, line: Color = HotPink, area: Color = LightPink) {
    val clean = values.map { v -> if (v == null || v.isNaN() || v <= 0.0) null else v }
    val finite = clean.filterNotNull()
    if (finite.isEmpty()) return
    var lo = floor(finite.min() - 3.0)
    var hi = ceil(finite.max() + 3.0)
    if (hi - lo < 10.0) {
        val mid = (hi + lo) / 2.0
        lo = mid - 5.0
        hi = mid + 5.0
    }
    val total = maxOf(slots, clean.size, 2)
    val lead = total - clean.size
    Canvas(modifier = modifier) {
        val strokeW = 3.dp.toPx()
        val top = strokeW
        val bottom = size.height - strokeW / 2f
        val stepX = size.width / (total - 1).toFloat()
        fun y(v: Double): Float = bottom - ((v - lo) / (hi - lo)).toFloat() * (bottom - top)
        var seg = ArrayList<Offset>()
        fun flush() {
            if (seg.size >= 2) drawSmooth(seg, line, area, strokeW)
            else if (seg.size == 1) drawCircle(color = line, radius = strokeW * 0.8f, center = seg[0])
            seg = ArrayList()
        }
        clean.forEachIndexed { i, v ->
            if (v == null) flush() else seg.add(Offset((lead + i) * stepX, y(v)))
        }
        flush()
    }
}

private fun DrawScope.drawSmooth(points: List<Offset>, line: Color, area: Color, strokeW: Float) {
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        val p = points[i - 1]
        val c = points[i]
        val midX = (p.x + c.x) / 2f
        path.cubicTo(midX, p.y, midX, c.y, c.x, c.y)
    }
    val fillPath = Path()
    fillPath.addPath(path)
    fillPath.lineTo(points.last().x, size.height)
    fillPath.lineTo(points.first().x, size.height)
    fillPath.close()
    drawPath(path = fillPath, color = area.copy(alpha = 0.55f))
    drawPath(path = path, color = line, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** "62 to 78 bpm" under a chart, from the values it drew. */
internal fun rangeLine(values: List<Double?>): String? {
    val finite = values.filterNotNull().filter { !it.isNaN() && it > 0.0 }
    if (finite.isEmpty()) return null
    return "From ${Fmt.num(finite.min())} to ${Fmt.num(finite.max())} bpm"
}

/** A large numeral in seconds between Less and More. A zero hold reads "No hold". */
@Composable
internal fun SecondsStepper(
    value: Double,
    isHold: Boolean,
    onLess: () -> Unit,
    onMore: () -> Unit,
    lessEnabled: Boolean,
    moreEnabled: Boolean,
) {
    val none = isHold && value <= 0.0
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillButton(text = "Less", onClick = onLess, enabled = lessEnabled, shadowAlpha = 0.1f)
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            val size: TextUnit = if (none) 28.sp else 44.sp
            Text(
                text = if (none) "No hold" else BreathWords.secs(value),
                color = if (none) Muted else HotPink,
                fontSize = size,
                lineHeight = 48.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            Text(
                text = if (none) "straight on" else if (value == 1.0) "second" else "seconds",
                color = Muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
            )
        }
        PillButton(text = "More", onClick = onMore, enabled = moreEnabled, shadowAlpha = 0.1f)
    }
}

/**
 * The findings from core Breathing.validate: a lavender card for each pattern that cannot be used, a
 * light pink card for each caution, and a plain muted line for notes.
 */
@Composable
internal fun IssueList(issues: List<BreathIssue>, onMatchOut: (() -> Unit)?) {
    val blocks = issues.filter { it.severity == BreathSeverity.BLOCK }
    val cautions = issues.filter { it.severity == BreathSeverity.CAUTION }
    val notes = issues.filter { it.severity == BreathSeverity.NOTE }
    for (b in blocks) {
        SoftCard(
            modifier = Modifier.fillMaxWidth(),
            color = Lavender,
            shadowColor = DeepLavenderLine,
            shadowAlpha = 0.2f,
            gap = 8.dp,
        ) {
            Text(text = "This pattern is not offered", color = Ink, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Black)
            BodyLine(text = b.message, color = Ink)
            if (b.code == "inhale_longer" && onMatchOut != null) {
                PillButton(text = "Match the out breath", onClick = onMatchOut, shadowColor = DeepLavenderLine, shadowAlpha = 0.18f)
            }
        }
    }
    for (c in cautions) {
        SoftCard(
            modifier = Modifier.fillMaxWidth(),
            color = LightPink,
            shadowColor = HotPink,
            shadowAlpha = 0.18f,
            gap = 6.dp,
        ) {
            Text(text = "Go gently", color = Ink, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Black)
            BodyLine(text = c.message, color = BodyText)
        }
    }
    for (n in notes) {
        MutedLine(text = n.message, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 6.dp))
    }
}

/** A quiet confirm for deleting. */
@Composable
internal fun BreathConfirmDialog(title: String, body: String, confirmText: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
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
