package com.abk.kernel.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val AbkLoadingDarkContainer = Color(0xFF2B2E29)
private val AbkLoadingDarkContent = Color(0xFFF5F6EE)
private val AbkLoadingLightContainer = Color(0xFFF0ECE4)
private val AbkLoadingLightContent = Color(0xFF2A2B27)

@Composable
fun AbkLoadingPill(
    text: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val lightTheme = colors.surface.luminance() > 0.5f
    val containerColor = if (lightTheme) {
        AbkLoadingLightContainer
    } else {
        AbkLoadingDarkContainer
    }
    val contentColor = if (lightTheme) {
        AbkLoadingLightContent
    } else {
        AbkLoadingDarkContent
    }
    val accentColor = colors.secondary
    val horizontalPadding = if (compact) 14.dp else 18.dp
    val verticalPadding = if (compact) 10.dp else 12.dp
    val glyphSize = if (compact) 16.dp else 18.dp

    Surface(
        modifier = modifier.widthIn(min = if (compact) 0.dp else 148.dp),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AbkLoadingGlyph(
                modifier = Modifier.size(glyphSize),
                tint = accentColor
            )
            Text(
                text = text,
                color = contentColor,
                style = if (compact) {
                    MaterialTheme.typography.bodyLarge
                } else {
                    MaterialTheme.typography.titleMedium
                },
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AbkCenteredLoadingTransition(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AbkLoadingPill(text = text)
    }
}

data class AbkInteractiveRefreshPresentation(
    val showLoading: Boolean,
    val beginRefresh: () -> Unit
)

@Composable
fun rememberAbkInteractiveRefreshPresentation(
    loading: Boolean,
    minVisibleMillis: Long = 1_000L
): AbkInteractiveRefreshPresentation {
    var refreshGeneration by remember { mutableIntStateOf(0) }
    var visibleSinceMs by remember { mutableLongStateOf(0L) }
    var waitingForLoadStart by remember { mutableStateOf(false) }
    var showLoading by remember { mutableStateOf(false) }

    val beginRefresh = remember(loading) {
        {
            refreshGeneration += 1
            visibleSinceMs = SystemClock.elapsedRealtime()
            waitingForLoadStart = !loading
            showLoading = true
        }
    }

    LaunchedEffect(refreshGeneration, loading) {
        if (!showLoading) return@LaunchedEffect
        if (waitingForLoadStart) {
            if (!loading) return@LaunchedEffect
            waitingForLoadStart = false
        }
        if (loading) return@LaunchedEffect

        val elapsed = SystemClock.elapsedRealtime() - visibleSinceMs
        val remaining = (minVisibleMillis - elapsed).coerceAtLeast(0L)
        if (remaining > 0L) delay(remaining)
        showLoading = false
    }

    return AbkInteractiveRefreshPresentation(
        showLoading = showLoading,
        beginRefresh = beginRefresh
    )
}

@Composable
fun AbkInlineLoadingPill(
    text: String,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 4.dp else 12.dp),
        contentAlignment = Alignment.Center
    ) {
        AbkLoadingPill(
            text = text,
            compact = compact
        )
    }
}

@Composable
private fun AbkLoadingGlyph(
    modifier: Modifier = Modifier,
    tint: Color
) {
    val transition = rememberInfiniteTransition(label = "abk-loading-glyph")
    val rotationDegrees by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6400, easing = LinearEasing)
        ),
        label = "abk-loading-rotation"
    )
    val scale by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "abk-loading-scale"
    )

    Canvas(
        modifier = modifier.graphicsLayer {
            rotationZ = rotationDegrees
            scaleX = scale
            scaleY = scale
        }
    ) {
        val radius = size.minDimension
        val orbit = radius * 0.24f
        val petalRadius = radius * 0.14f
        val centerRadius = radius * 0.22f

        repeat(8) { index ->
            val angle = (index / 8f) * (2f * PI.toFloat())
            val petalCenter = Offset(
                x = center.x + cos(angle.toDouble()).toFloat() * orbit,
                y = center.y + sin(angle.toDouble()).toFloat() * orbit
            )
            drawCircle(
                color = tint,
                radius = if (index % 2 == 0) petalRadius else petalRadius * 0.9f,
                center = petalCenter
            )
        }

        drawCircle(
            color = tint,
            radius = centerRadius,
            center = center
        )
    }
}
