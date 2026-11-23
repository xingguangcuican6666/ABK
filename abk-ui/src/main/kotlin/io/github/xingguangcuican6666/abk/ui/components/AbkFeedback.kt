package io.github.xingguangcuican6666.abk.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkShapes
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun abkStatusIcon(tone: AbkTone): ImageVector = when (tone) {
    AbkTone.Success -> AbkIcons.Check
    AbkTone.Warning -> AbkIcons.Warning
    AbkTone.Danger -> AbkIcons.Close
    AbkTone.Info -> AbkIcons.Info
    else -> AbkIcons.Dot
}

/**
 * Outlined tone glyph that names a state. It is drawn without a filled disc so a container that already
 * carries the tone does not repeat it; neutral has no state to name and keeps the subdued dot badge.
 */
@Composable
internal fun AbkStatusGlyph(tone: AbkTone, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val status = AbkTheme.colors.status(tone)
    val icon = abkStatusIcon(tone)
    if (tone == AbkTone.Neutral) {
        Box(modifier.size(size).background(status.container, CircleShape), contentAlignment = Alignment.Center) {
            AbkIcon(icon, null, tint = status.color, size = size * 0.6f)
        }
    } else {
        AbkIcon(icon, null, modifier, tint = status.color, size = size)
    }
}

@Composable
public fun AbkAlert(
    title: String, message: String, modifier: Modifier = Modifier, tone: AbkTone = AbkTone.Info,
    actions: (@Composable () -> Unit)? = null,
) {
    val c = AbkTheme.colors.status(tone)
    Surface(modifier.semantics { liveRegion = LiveRegionMode.Polite }, shape = AbkShapes.Medium,
        color = c.container, border = BorderStroke(1.dp, c.color.copy(alpha = 0.3f))) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // The outlined glyph marks the state on top of the tone container, so the tray stays quiet.
            AbkStatusGlyph(tone, Modifier.padding(top = 2.dp), size = 20.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AbkText(title, style = MaterialTheme.typography.titleSmall, color = c.onContainer)
                AbkText(message, style = MaterialTheme.typography.bodyMedium, color = c.onContainer)
                actions?.invoke()
            }
        }
    }
}

/** Null progress is indeterminate. Reduced motion leaves a static segment and meaningful semantics. */
@Composable
public fun AbkLinearProgress(
    progress: Float?, modifier: Modifier = Modifier, tone: AbkTone = AbkTone.Accent,
    striped: Boolean = false, label: String = "进度",
) {
    val value = progress?.coerceIn(0f, 1f)
    require(progress == null || progress.isFinite()) { "Progress must be finite or null" }
    val c = AbkTheme.colors
    val phase = loopingPhase(1400, progress == null || striped)
    Canvas(modifier.fillMaxWidth().height(8.dp).clip(AbkShapes.Pill).semantics {
        contentDescription = label
        progressBarRangeInfo = if (value == null) ProgressBarRangeInfo.Indeterminate else ProgressBarRangeInfo(value, 0f..1f)
    }) {
        drawRect(c.sunken)
        val segment = if (value == null) size.width * 0.35f else size.width * value
        val start = if (value == null) (size.width - segment) * phase else 0f
        val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
        val left = if (rtl) size.width - start - segment else start
        drawRect(c.status(tone).color, Offset(left, 0f), Size(segment, size.height))
        if (striped && segment > 0f) clipRect(left, 0f, left + segment, size.height) {
            val step = 16.dp.toPx()
            var x = left - step + step * phase
            while (x < left + segment + step) {
                val path = Path().apply {
                    moveTo(x, 0f); lineTo(x + step / 2, 0f)
                    lineTo(x + step / 2 - size.height, size.height); lineTo(x - size.height, size.height); close()
                }
                drawPath(path, c.status(tone).onColor.copy(alpha = 0.2f))
                x += step
            }
        }
    }
}

@Composable
public fun AbkRingProgress(progress: Float, modifier: Modifier = Modifier, label: String = "进度", tone: AbkTone = AbkTone.Accent) {
    require(progress.isFinite()) { "Progress must be finite" }
    val value = progress.coerceIn(0f, 1f)
    val c = AbkTheme.colors
    Box(modifier.size(72.dp).semantics(mergeDescendants = true) {
        contentDescription = label; progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
    }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2
            val bounds = Size((size.width - stroke).coerceAtLeast(0f), (size.height - stroke).coerceAtLeast(0f))
            drawArc(c.sunken, 0f, 360f, false, Offset(inset, inset), bounds, style = Stroke(stroke))
            drawArc(c.status(tone).color, -90f, 360f * value, false, Offset(inset, inset), bounds, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        AbkText("${(value * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
public fun AbkSpinner(modifier: Modifier = Modifier, size: AbkSize = AbkSize.Medium, label: String = "加载中") {
    val diameter = when (size) { AbkSize.Small -> 14.dp; AbkSize.Medium -> 20.dp; AbkSize.Large -> 32.dp }
    val c = AbkTheme.colors
    val phase = loopingPhase(800)
    Canvas(modifier.size(diameter).semantics { contentDescription = label; progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
        val width = if (size == AbkSize.Large) 3.dp.toPx() else 2.dp.toPx()
        val bounds = Size((this.size.width - width).coerceAtLeast(0f), (this.size.height - width).coerceAtLeast(0f))
        drawArc(c.line, 0f, 360f, false, Offset(width / 2, width / 2), bounds, style = Stroke(width))
        drawArc(c.accent.color, phase * 360f - 90f, 100f, false, Offset(width / 2, width / 2), bounds, style = Stroke(width, cap = StrokeCap.Round))
    }
}

@Composable
public fun AbkLoadingDots(modifier: Modifier = Modifier, label: String = "加载中") {
    val phase = loopingPhase(1200)
    val reduced = AbkTheme.reduceMotion
    val color = AbkTheme.colors.inkMuted
    Canvas(modifier.size(36.dp, 20.dp).semantics { contentDescription = label; progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
        repeat(3) { index ->
            val wave = if (reduced) 0f else sin((phase - index * 0.15f) * 2 * PI).toFloat().coerceAtLeast(0f)
            drawCircle(color.copy(alpha = 0.5f + wave * 0.5f), 3.5.dp.toPx(),
                Offset(4.dp.toPx() + index * 12.dp.toPx(), this.size.height / 2 - wave * 5.dp.toPx()))
        }
    }
}

public enum class AbkSkeletonStyle { Text, Title, Circle }

@Composable
public fun AbkSkeleton(modifier: Modifier = Modifier, style: AbkSkeletonStyle = AbkSkeletonStyle.Text) {
    val c = AbkTheme.colors
    val phase = loopingPhase(1400)
    val shape: Shape = if (style == AbkSkeletonStyle.Circle) CircleShape else AbkShapes.Small
    val sizing = when (style) {
        AbkSkeletonStyle.Text -> Modifier.fillMaxWidth().height(16.dp)
        AbkSkeletonStyle.Title -> Modifier.fillMaxWidth(0.55f).height(24.dp)
        AbkSkeletonStyle.Circle -> Modifier.size(40.dp)
    }
    val reduced = AbkTheme.reduceMotion
    Canvas(modifier.then(sizing).clip(shape)) {
        drawRect(c.sunken)
        if (!reduced) {
            val center = (phase * 2f - 0.5f) * size.width
            drawRect(Brush.linearGradient(listOf(Color.Transparent, c.raised.copy(alpha = 0.75f), Color.Transparent),
                Offset(center - size.width / 2, 0f), Offset(center + size.width / 2, 0f)))
        }
    }
}

@Composable
public fun AbkEmptyState(
    title: String, description: String, modifier: Modifier = Modifier, icon: ImageVector = AbkIcons.Modules,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(88.dp).background(AbkTheme.colors.sunken, AbkShapes.ExtraLarge), contentAlignment = Alignment.Center) {
            AbkIcon(icon, null, tint = AbkTheme.colors.inkFaint, size = 34.dp)
        }
        AbkHeading(title)
        androidx.compose.material3.Text(description, color = AbkTheme.colors.inkMuted,
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        actions?.invoke(this)
    }
}

@Composable
private fun loopingPhase(duration: Int, active: Boolean = true): Float {
    if (!active || AbkTheme.reduceMotion) return 0.5f
    val transition = rememberInfiniteTransition(label = "ABK decoration")
    val phase by transition.animateFloat(0f, 1f,
        infiniteRepeatable(tween(duration, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    return phase
}
