package com.lifepowers.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifepowers.app.ui.theme.Ground
import com.lifepowers.app.ui.theme.Ink
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * The moon as two overlapping circles: a lit disc, and a second disc in the panel color slid across it.
 * The slide is chosen so the visible lit area matches [illumination]. [waxing] lights the right side.
 */
@Composable
fun MoonDisc(
    illumination: Double,
    waxing: Boolean,
    shadowColor: Color,
    modifier: Modifier = Modifier,
    litColor: Color = Ground,
    size: Dp = 56.dp,
) {
    val slide = remember(illumination) { shadowSlide(illumination.coerceIn(0.0, 1.0)) }
    Canvas(
        modifier = modifier
            .size(size)
            .softShadow(RoundedCornerShape(50), Ink, 0.15f, 6.dp),
    ) {
        val r = this.size.minDimension / 2f
        val c = center
        drawCircle(color = litColor, radius = r, center = c)
        if (slide < 2f) {
            val dx = r * slide * (if (waxing) -1f else 1f)
            val disc = Path().apply { addOval(Rect(center = c, radius = r)) }
            clipPath(disc) {
                drawCircle(color = shadowColor, radius = r, center = Offset(c.x + dx, c.y))
            }
        }
    }
}

/**
 * Distance between the two circle centers, in radii, so that the part of the lit disc not covered by
 * the shadow disc is [lit] of its area. 0 is a new moon (fully covered), 2 a full moon.
 */
internal fun shadowSlide(lit: Double): Float {
    if (lit <= 0.005) return 0f
    if (lit >= 0.995) return 2f
    val targetOverlap = 1.0 - lit
    var lo = 0.0
    var hi = 2.0
    repeat(30) {
        val mid = (lo + hi) / 2.0
        if (lensFraction(mid) > targetOverlap) lo = mid else hi = mid
    }
    return ((lo + hi) / 2.0).toFloat()
}

/** Overlap of two unit circles whose centers are [d] apart, as a fraction of one circle's area. */
private fun lensFraction(d: Double): Double {
    val half = d / 2.0
    val area = 2.0 * acos(half) - half * sqrt((4.0 - d * d).coerceAtLeast(0.0))
    return area / PI
}
