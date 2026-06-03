package com.abk.kernel.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

const val LIVE_DURATION_SHIMMER_PERIOD_MS = 6_000

private const val SHIMMER_BASE_ALPHA = 0.14f
private const val SHIMMER_HIGHLIGHT_ALPHA = 0.28f

/** Slightly stronger tail on thin progress bars (still primary-tinted). */
private const val PROGRESS_SHIMMER_BASE_ALPHA = 0.22f
private const val PROGRESS_SHIMMER_HIGHLIGHT_ALPHA = 0.45f

@Composable
fun rememberLiveWorkflowShimmerPhase(enabled: Boolean): Float {
    if (!enabled) return 0f
    val transition = rememberInfiniteTransition(label = "live-workflow-shimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(LIVE_DURATION_SHIMMER_PERIOD_MS, easing = LinearEasing),
        ),
        label = "shimmer-phase",
    )
    return phase
}

fun liveWorkflowShimmerBrush(
    size: Size,
    phase: Float,
    accent: Color,
    baseAlpha: Float = SHIMMER_BASE_ALPHA,
    highlightAlpha: Float = SHIMMER_HIGHLIGHT_ALPHA,
): Brush {
    val base = accent.copy(alpha = baseAlpha)
    val highlight = accent.copy(alpha = highlightAlpha)
    val band = size.width * 1.6f
    val travel = size.width + band
    val offset = (phase * travel) % travel - band
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(offset, 0f),
        end = Offset(offset + band, size.height),
    )
}

fun liveWorkflowProgressShimmerBrush(size: Size, phase: Float, accent: Color): Brush =
    liveWorkflowShimmerBrush(
        size = size,
        phase = phase,
        accent = accent,
        baseAlpha = PROGRESS_SHIMMER_BASE_ALPHA,
        highlightAlpha = PROGRESS_SHIMMER_HIGHLIGHT_ALPHA,
    )

fun DrawScope.drawLiveWorkflowShimmer(phase: Float, accent: Color) {
    drawRect(liveWorkflowShimmerBrush(size, phase, accent))
}

fun DrawScope.drawLiveWorkflowProgressShimmer(phase: Float, accent: Color) {
    drawRect(liveWorkflowProgressShimmerBrush(size, phase, accent))
}
