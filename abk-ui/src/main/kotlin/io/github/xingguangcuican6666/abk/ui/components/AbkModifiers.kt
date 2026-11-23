package io.github.xingguangcuican6666.abk.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Optional, deterministic paper speckles; no bitmap, random state or network resource. */
internal fun Modifier.abkPaperGrain(color: Color): Modifier = drawWithCache {
    val step = 11.dp.toPx()
    val radius = 0.6.dp.toPx()
    onDrawWithContent {
        drawContent()
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) step * 0.2f else step * 0.7f
            while (x < size.width) {
                drawCircle(color.copy(alpha = 0.035f), radius, Offset(x, y))
                x += step
            }
            y += step
            row++
        }
    }
}

internal fun Modifier.abkFocusBorder(focused: Boolean, color: Color): Modifier = drawWithCache {
    onDrawWithContent {
        drawContent()
        if (focused) drawRoundRect(
            color, topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
            size = size.copy(width = size.width - 2.dp.toPx(), height = size.height - 2.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
            style = Stroke(2.dp.toPx()),
        )
    }
}
