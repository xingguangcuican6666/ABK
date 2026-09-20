package com.abk.kernel.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

const val LIVE_DURATION_MINUTE_HAND_PERIOD_MS = 15_000L
const val SETTLE_DECELERATE_MS = 900
const val SETTLE_HOLD_MS = 300
const val SETTLE_SNAP_MS = 150
const val SETTLE_HOLD_OFFSET_DEG = 4f

enum class MinuteHandPhase {
    Spinning,
    Settling,
    Rest,
}

internal fun normalizeDegrees(degrees: Float): Float {
    var d = degrees % 360f
    if (d < 0f) d += 360f
    return d
}

/** Signed shortest delta from [from] to [to], both normalized to [0, 360). */
internal fun shortestDeltaDegrees(from: Float, to: Float): Float {
    val a = normalizeDegrees(from)
    val b = normalizeDegrees(to)
    var delta = b - a
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return delta
}

/** Degrees to hold at before snapping to 0°, along the path from [start]. */
internal fun settleHoldAngleDegrees(start: Float, holdOffsetDegrees: Float = SETTLE_HOLD_OFFSET_DEG): Float {
    val delta = shortestDeltaDegrees(start, 0f)
    if (abs(delta) <= 1f) return 0f
    val sign = if (delta > 0f) 1f else -1f
    return normalizeDegrees(0f - sign * holdOffsetDegrees)
}

internal fun spinRotationDegrees(elapsedMs: Long, periodMs: Long): Float {
    val elapsed = (elapsedMs % periodMs).toFloat()
    return elapsed / periodMs.toFloat() * 360f
}

@Stable
class MinuteHandController {
    var rotationDegrees by mutableFloatStateOf(0f)
        private set

    var phase by mutableStateOf(MinuteHandPhase.Rest)
        private set

    /** Blocks [beginSpinning] after a settle until a new build session starts. */
    private var settledOnDetail = false

    /** Start spin for a new build session (not while settling or after settle completed). */
    fun beginSpinning() {
        when (phase) {
            MinuteHandPhase.Spinning, MinuteHandPhase.Settling -> return
            MinuteHandPhase.Rest -> if (settledOnDetail) return
        }
        settledOnDetail = false
        phase = MinuteHandPhase.Spinning
    }

    fun beginSettle() {
        if (phase == MinuteHandPhase.Settling || phase == MinuteHandPhase.Rest) return
        settledOnDetail = false
        phase = MinuteHandPhase.Settling
    }

    internal fun setRotation(degrees: Float) {
        rotationDegrees = normalizeDegrees(degrees)
    }

    internal fun setPhaseRest() {
        phase = MinuteHandPhase.Rest
        rotationDegrees = 0f
        settledOnDetail = true
    }
}

@Composable
fun MinuteHandControllerHost(controller: MinuteHandController) {
    val phase = controller.phase
    LaunchedEffect(phase) {
        when (phase) {
            MinuteHandPhase.Spinning -> {
                val startTime = withFrameMillis { it }
                while (isActive && controller.phase == MinuteHandPhase.Spinning) {
                    withFrameMillis { frameTime ->
                        val elapsed = frameTime - startTime
                        controller.setRotation(
                            spinRotationDegrees(elapsed, LIVE_DURATION_MINUTE_HAND_PERIOD_MS),
                        )
                    }
                }
            }

            MinuteHandPhase.Settling -> {
                if (controller.phase != MinuteHandPhase.Settling) return@LaunchedEffect
                val startAngle = controller.rotationDegrees
                val holdAngle = settleHoldAngleDegrees(startAngle)
                val decelerateTarget = if (holdAngle == 0f) 0f else holdAngle
                val decelerateDelta = shortestDeltaDegrees(startAngle, decelerateTarget)
                val snapDelta = shortestDeltaDegrees(decelerateTarget, 0f)

                val rot = Animatable(startAngle)
                controller.setRotation(startAngle)
                rot.animateTo(
                    targetValue = startAngle + decelerateDelta,
                    animationSpec = tween(SETTLE_DECELERATE_MS, easing = FastOutSlowInEasing),
                ) {
                    if (controller.phase == MinuteHandPhase.Settling) {
                        controller.setRotation(normalizeDegrees(value))
                    }
                }
                if (controller.phase != MinuteHandPhase.Settling) return@LaunchedEffect
                if (decelerateTarget != 0f && SETTLE_HOLD_MS > 0) {
                    delay(SETTLE_HOLD_MS.toLong())
                }
                if (controller.phase != MinuteHandPhase.Settling) return@LaunchedEffect
                rot.snapTo(normalizeDegrees(decelerateTarget))
                controller.setRotation(rot.value)
                rot.animateTo(
                    targetValue = normalizeDegrees(decelerateTarget + snapDelta),
                    animationSpec = tween(SETTLE_SNAP_MS, easing = LinearOutSlowInEasing),
                ) {
                    if (controller.phase == MinuteHandPhase.Settling) {
                        controller.setRotation(normalizeDegrees(value))
                    }
                }
                if (controller.phase == MinuteHandPhase.Settling) {
                    controller.setPhaseRest()
                }
            }

            MinuteHandPhase.Rest -> Unit
        }
    }
}
