package com.lifepowers.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifepowers.app.ui.theme.BodyText
import com.lifepowers.app.ui.theme.DeepPink
import com.lifepowers.app.ui.theme.HotPink
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.Lavender
import com.lifepowers.app.ui.theme.LightPink
import com.lifepowers.app.ui.theme.Muted
import com.lifepowers.app.ui.theme.White
import com.lifepowers.app.ui.theme.hexColor
import com.lifepowers.app.ui.theme.shadowColor
import com.lifepowers.core.Tier

/** Soft layered shadow in plum, then the fill. Shadow before clip so it is not cut off. */
fun Modifier.softShadow(shape: RoundedCornerShape, color: Color = Ink, alpha: Float = 0.18f, elevation: Dp = 10.dp): Modifier =
    this.shadow(elevation = elevation, shape = shape, clip = false, ambientColor = color.copy(alpha = alpha), spotColor = color.copy(alpha = alpha))

/** Dashed outline for the "not enough yet" state. */
fun Modifier.dashedBorder(color: Color = Lavender, width: Dp = 2.dp, cornerRadius: Dp = 24.dp): Modifier = this.drawBehind {
    val stroke = Stroke(width = width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f), 0f))
    val inset = width.toPx() / 2f
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
        style = stroke,
    )
}

/** A white (or tinted) card with 28.dp corners. Pass a smaller [radius] for tiles. */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    color: Color = White,
    radius: Dp = 28.dp,
    shadowColor: Color = Ink,
    shadowAlpha: Float = 0.16f,
    elevation: Dp = 10.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    gap: Dp = 6.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    var m = modifier.softShadow(shape, shadowColor, shadowAlpha, elevation).clip(shape).background(color)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(modifier = m.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(gap), content = content)
}

/** A dashed, pale card for the unknown tier. */
@Composable
fun DashedCard(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 15.dp, vertical = 13.dp),
    gap: Dp = 3.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    var m = modifier.clip(shape).background(com.lifepowers.app.ui.theme.Ground).dashedBorder(cornerRadius = radius)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(modifier = m.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(gap), content = content)
}

/** A full pill button. Defaults to the white secondary look; pass colors for primary or hot pink. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = White,
    textColor: Color = Ink,
    fontSize: TextUnit = 13.sp,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    shadowColor: Color = Ink,
    shadowAlpha: Float = 0.12f,
    elevation: Dp = 8.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .softShadow(shape, shadowColor, if (enabled) shadowAlpha else 0f, elevation)
            .clip(shape)
            .background(if (enabled) background else background.copy(alpha = 0.6f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = textColor, fontSize = fontSize, fontWeight = fontWeight, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    PillButton(
        text = text, onClick = onClick, modifier = modifier, background = DeepPink, textColor = White,
        fontSize = 14.sp, fontWeight = FontWeight.Black, shadowColor = DeepPink, shadowAlpha = 0.3f,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp), enabled = enabled,
    )
}

@Composable
fun HotButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, fontSize: TextUnit = 14.sp) {
    PillButton(
        text = text, onClick = onClick, modifier = modifier, background = HotPink, textColor = White,
        fontSize = fontSize, fontWeight = FontWeight.Black, shadowColor = HotPink, shadowAlpha = 0.3f,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 11.dp),
    )
}

@Composable
fun InkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PillButton(
        text = text, onClick = onClick, modifier = modifier, background = Ink, textColor = White,
        fontSize = 13.sp, fontWeight = FontWeight.Black, shadowAlpha = 0f, elevation = 0.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** A word chip. Selected is hot pink with white text; otherwise cream with a hairline, or a given color. */
@Composable
fun Chip(
    text: String,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    background: Color = com.lifepowers.app.ui.theme.Ground,
    textColor: Color = Ink,
    dashed: Boolean = false,
    fontSize: TextUnit = 13.sp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(999.dp)
    val bg = if (selected) HotPink else background
    val fg = if (selected) White else textColor
    var m = modifier
    if (selected) m = m.softShadow(shape, HotPink, 0.3f, 8.dp)
    m = m.clip(shape)
    m = if (dashed) m.dashedBorder(cornerRadius = 999.dp) else m.background(bg)
    if (!dashed && !selected && background == com.lifepowers.app.ui.theme.Ground) {
        m = m.drawBehind {
            drawRoundRect(
                color = com.lifepowers.app.ui.theme.HairLine,
                cornerRadius = CornerRadius(size.height, size.height),
                style = Stroke(width = 1.5.dp.toPx() * 2),
            )
        }
    }
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(modifier = m.padding(horizontal = 14.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
        Text(text = text, color = if (dashed) Muted else fg, fontSize = fontSize, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
fun LightPinkChip(text: String, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Chip(text = text, onClick = onClick, background = LightPink, modifier = modifier)
}

/** Small muted section label, 12sp 800. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Muted) {
    Text(text = text, modifier = modifier, color = color, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
}

@Composable
fun BodyLine(text: String, color: Color = BodyText, fontSize: TextUnit = 13.sp, lineHeight: TextUnit = 19.sp, modifier: Modifier = Modifier) {
    Text(text = text, color = color, fontSize = fontSize, lineHeight = lineHeight, fontWeight = FontWeight.Medium, modifier = modifier)
}

@Composable
fun MutedLine(text: String, fontSize: TextUnit = 12.sp, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(text = text, color = Muted, fontSize = fontSize, lineHeight = (fontSize.value * 1.45f).sp, fontWeight = FontWeight.SemiBold, modifier = modifier, textAlign = textAlign)
}

/**
 * A heavy title with an optional sentence case line under it, and an optional trailing control.
 * Nothing sits above the title.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    titleSize: TextUnit = 26.sp,
    subline: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val line = subline
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, color = Ink, fontSize = titleSize, lineHeight = (titleSize.value * 1.15f).sp, fontWeight = FontWeight.Black)
            if (line != null) Text(text = line, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        if (trailing != null) trailing()
    }
}

/** A card heading inside a white card: ink, 16sp, heavy. Used instead of small labels over content. */
@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier, color: Color = Ink) {
    Text(text = text, modifier = modifier, color = color, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Black)
}

/**
 * A panel that wears a forecast tier: the tier color with its own shadow, or the pale dashed card for
 * UNKNOWN. Color follows the thing it describes.
 */
@Composable
fun TierCard(
    tier: Tier,
    modifier: Modifier = Modifier,
    radius: Dp = 28.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    gap: Dp = 6.dp,
    elevation: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (tier == Tier.UNKNOWN) {
        DashedCard(modifier = modifier, radius = radius, contentPadding = contentPadding, gap = gap, onClick = onClick, content = content)
    } else {
        SoftCard(
            modifier = modifier, color = tier.hexColor(), radius = radius,
            shadowColor = tier.shadowColor(), shadowAlpha = 0.2f, elevation = elevation,
            contentPadding = contentPadding, gap = gap, onClick = onClick, content = content,
        )
    }
}

/** A small filled circle, for list markers. A shape, not an icon. */
@Composable
fun Dot(color: Color, size: Dp = 12.dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(size).clip(CircleShape).background(color))
}

/** The palette switch: hot pink track when on, hairline track when off, white thumb. */
@Composable
fun PinkSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = White, checkedTrackColor = HotPink, checkedBorderColor = HotPink,
            uncheckedThumbColor = White, uncheckedTrackColor = com.lifepowers.app.ui.theme.HairLine,
            uncheckedBorderColor = com.lifepowers.app.ui.theme.HairLine,
        ),
    )
}

/** A plain text link in deep pink, 12sp 900. */
@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, fontSize: TextUnit = 12.sp) {
    Text(
        text = text,
        color = DeepPink,
        fontSize = fontSize,
        fontWeight = FontWeight.Black,
        modifier = modifier.clip(RoundedCornerShape(999.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
    )
}
