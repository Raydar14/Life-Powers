package com.lifepowers.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifepowers.app.ui.theme.Ground
import com.lifepowers.app.ui.theme.HairLine
import com.lifepowers.app.ui.theme.Ink
import com.lifepowers.app.ui.theme.Lavender
import com.lifepowers.app.ui.theme.White
import com.lifepowers.app.ui.theme.color
import com.lifepowers.core.Tier

/**
 * One forecast dot. UNKNOWN is a pale cream circle with a dashed lavender edge; [ringed] draws the
 * ink ring that marks today; [hollow] is a future day, white with a hairline.
 */
@Composable
fun TierDot(
    tier: Tier,
    size: Dp,
    modifier: Modifier = Modifier,
    ringed: Boolean = false,
    hollow: Boolean = false,
    ringGap: Dp = 0.dp,
) {
    Box(modifier = modifier.size(size)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val full = this.size.minDimension / 2f
            val ringWidth = if (ringed) 2.dp.toPx() else 0f
            val gap = if (ringed) ringGap.toPx() else 0f
            val r = full - ringWidth - gap
            when {
                hollow -> {
                    drawCircle(color = White, radius = r)
                    drawCircle(color = HairLine, radius = r - 1.dp.toPx() / 2f, style = Stroke(width = 1.5.dp.toPx()))
                }
                tier == Tier.UNKNOWN -> {
                    drawCircle(color = Ground, radius = r)
                    drawCircle(
                        color = Lavender,
                        radius = r - 1.dp.toPx(),
                        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f)),
                    )
                }
                else -> drawCircle(color = tier.color(), radius = r)
            }
            if (ringed) {
                if (gap > 0f) drawCircle(color = White, radius = full - ringWidth - gap / 2f, style = Stroke(width = gap))
                drawCircle(color = Ink, radius = full - ringWidth / 2f, style = Stroke(width = ringWidth))
            }
        }
    }
}
