package com.lifepowers.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** A row that wraps its children onto new lines, for chips. Stable API only. */
@Composable
fun WrapRow(
    modifier: Modifier = Modifier,
    horizontalGap: Dp = 8.dp,
    verticalGap: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val hGap = horizontalGap.roundToPx()
        val vGap = verticalGap.roundToPx()
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(childConstraints) }
        val positions = ArrayList<IntOffset>(placeables.size)
        var x = 0
        var y = 0
        var rowHeight = 0
        var widest = 0
        for (p in placeables) {
            if (x > 0 && x + p.width > maxWidth) {
                x = 0
                y += rowHeight + vGap
                rowHeight = 0
            }
            positions.add(IntOffset(x, y))
            x += p.width + hGap
            if (x - hGap > widest) widest = x - hGap
            if (p.height > rowHeight) rowHeight = p.height
        }
        val totalHeight = if (placeables.isEmpty()) 0 else y + rowHeight
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else widest.coerceAtLeast(constraints.minWidth)
        val height = totalHeight.coerceIn(constraints.minHeight, if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE)
        layout(width, height) {
            placeables.forEachIndexed { i, p -> p.placeRelative(positions[i].x, positions[i].y) }
        }
    }
}
