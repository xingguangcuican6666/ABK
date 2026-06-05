package com.abk.kernel.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Outer ring + inner face from [androidx.compose.material.icons.filled.Schedule]. */
private val ScheduleDial: ImageVector by lazy {
    ImageVector.Builder(
        name = "ScheduleDial",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            moveTo(11.99f, 2f)
            curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
            curveTo(2f, 17.52f, 6.47f, 22f, 11.99f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            curveTo(22f, 6.48f, 17.52f, 2f, 11.99f, 2f)
            close()
            moveTo(12f, 20f)
            curveToRelative(-4.42f, 0f, -8f, -3.58f, -8f, -8f)
            reflectiveCurveToRelative(3.58f, -8f, 8f, -8f)
            reflectiveCurveToRelative(8f, 3.58f, 8f, 8f)
            reflectiveCurveToRelative(-3.58f, 8f, -8f, 8f)
            close()
        }
    }.build()
}

/** Hour hand only (same geometry as Schedule, without the minute hand). */
private val ScheduleHourHand: ImageVector by lazy {
    ImageVector.Builder(
        name = "ScheduleHourHand",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12.5f, 7f)
            horizontalLineTo(11f)
            verticalLineTo(13f)
            horizontalLineTo(12.5f)
            close()
        }
    }.build()
}

/** Minute hand only — rotated for the live workflow chip. */
private val ScheduleMinuteHand: ImageVector by lazy {
    ImageVector.Builder(
        name = "ScheduleMinuteHand",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            moveTo(11f, 13f)
            lineToRelative(5.25f, 3.15f)
            lineToRelative(0.75f, -1.23f)
            lineToRelative(-4.5f, -2.67f)
            close()
        }
    }.build()
}

/**
 * Same clock as [androidx.compose.material.icons.filled.Schedule] on completed workflows;
 * only the minute hand rotates by [minuteHandRotationDegrees] around the dial center.
 */
@Composable
fun LiveDurationScheduleIcon(
    minuteHandRotationDegrees: Float,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(14.dp)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = ScheduleDial,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.fillMaxSize(),
        )
        Icon(
            imageVector = ScheduleHourHand,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.fillMaxSize(),
        )
        Icon(
            imageVector = ScheduleMinuteHand,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = minuteHandRotationDegrees
                    transformOrigin = TransformOrigin.Center
                },
        )
    }
}
