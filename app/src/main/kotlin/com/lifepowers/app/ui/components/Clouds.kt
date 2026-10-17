package com.lifepowers.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifepowers.app.ui.theme.Lavender
import com.lifepowers.app.ui.theme.LightPink
import com.lifepowers.app.ui.theme.SoftBlue

/**
 * One soft cloud: three overlapping circles on a rounded base, drawn in a 240 by 150 box and scaled
 * to [width]. Offsets are measured from the corner chosen by [fromEnd] and [fromBottom], so a cloud can
 * drift off any edge like the svg in the mockups.
 */
data class CloudSpec(
    val color: Color,
    val width: Dp,
    val x: Dp,
    val y: Dp,
    val alpha: Float,
    val fromEnd: Boolean = false,
    val fromBottom: Boolean = false,
)

@Composable
fun Clouds(specs: List<CloudSpec>, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        for (spec in specs) {
            val height = spec.width * (150f / 240f)
            val alignment = when {
                spec.fromEnd && spec.fromBottom -> Alignment.BottomEnd
                spec.fromEnd -> Alignment.TopEnd
                spec.fromBottom -> Alignment.BottomStart
                else -> Alignment.TopStart
            }
            val dx = if (spec.fromEnd) -spec.x else spec.x
            val dy = if (spec.fromBottom) -spec.y else spec.y
            Canvas(
                modifier = Modifier
                    .align(alignment)
                    .offset(x = dx, y = dy)
                    .size(spec.width, height)
                    .alpha(spec.alpha),
            ) {
                val k = size.width / 240f
                drawCircle(color = spec.color, radius = 42f * k, center = Offset(72f * k, 92f * k))
                drawCircle(color = spec.color, radius = 56f * k, center = Offset(120f * k, 66f * k))
                drawCircle(color = spec.color, radius = 46f * k, center = Offset(172f * k, 88f * k))
                drawRoundRect(
                    color = spec.color,
                    topLeft = Offset(30f * k, 92f * k),
                    size = Size(184f * k, 52f * k),
                    cornerRadius = CornerRadius(26f * k, 26f * k),
                )
            }
        }
    }
}

/** Cloud layouts per screen, taken from the mockups. Offsets are the svg positions. */
object CloudPresets {
    val today = listOf(
        CloudSpec(LightPink, 240.dp, x = (-70).dp, y = (-90).dp, alpha = 0.45f, fromEnd = true),
        CloudSpec(Lavender, 200.dp, x = (-110).dp, y = 60.dp, alpha = 0.35f),
    )
    val checkin = listOf(
        CloudSpec(LightPink, 300.dp, x = 45.dp, y = 120.dp, alpha = 0.35f),
        CloudSpec(LightPink, 200.dp, x = 95.dp, y = 170.dp, alpha = 0.45f),
    )
    val journal = listOf(
        CloudSpec(Lavender, 220.dp, x = (-60).dp, y = (-70).dp, alpha = 0.4f, fromEnd = true),
    )
    val days = listOf(
        CloudSpec(SoftBlue, 220.dp, x = (-60).dp, y = (-80).dp, alpha = 0.4f),
    )
    val feel = listOf(
        CloudSpec(LightPink, 240.dp, x = (-80).dp, y = (-80).dp, alpha = 0.4f),
    )
    val profile = listOf(
        CloudSpec(LightPink, 240.dp, x = (-60).dp, y = (-90).dp, alpha = 0.4f),
    )
    val settings = listOf(
        CloudSpec(SoftBlue, 220.dp, x = (-80).dp, y = (-80).dp, alpha = 0.4f, fromEnd = true),
    )
    val welcome = listOf(
        CloudSpec(LightPink, 320.dp, x = (-100).dp, y = (-120).dp, alpha = 0.5f, fromEnd = true),
        CloudSpec(Lavender, 280.dp, x = (-140).dp, y = 120.dp, alpha = 0.4f),
        CloudSpec(SoftBlue, 260.dp, x = (-60).dp, y = (-120).dp, alpha = 0.4f, fromEnd = true, fromBottom = true),
    )
    val placeholder = listOf(
        CloudSpec(SoftBlue, 240.dp, x = (-80).dp, y = (-90).dp, alpha = 0.4f, fromEnd = true),
        CloudSpec(LightPink, 200.dp, x = (-100).dp, y = 140.dp, alpha = 0.35f),
    )
}
