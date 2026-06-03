package com.abk.kernel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Progress bar: solid [primary] fill + animated shimmer on the unfilled track.
 */
@Composable
fun ShimmerLinearProgress(
    progress: () -> Float?,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    val accent = MaterialTheme.colorScheme.primary
    val phase = rememberLiveWorkflowShimmerPhase(enabled = true)
    val shape = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape),
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(liveWorkflowProgressShimmerBrush(size, phase, accent))
                },
        )
        progress()?.let { raw ->
            val fraction = raw.coerceIn(0f, 1f)
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .background(color = accent, shape = shape),
                )
            }
        }
    }
}
