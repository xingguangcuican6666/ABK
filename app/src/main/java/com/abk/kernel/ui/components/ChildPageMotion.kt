package com.abk.kernel.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.delay

/** Fraction of hide progress at end of peek (nav + back visual remap). */
const val CHILD_PAGE_MOTION_PEEK_FRACTION = 0.28f

// Bottom nav — browser variant 10 (hide ↓)
const val BOTTOM_NAV_HIDE_PEEK_MS = 160L
const val BOTTOM_NAV_HIDE_HOLD_MS = 80L
const val BOTTOM_NAV_HIDE_SLIDE_MS = 360L

// Bottom nav — browser variant 7 (show ↑)
const val BOTTOM_NAV_SHOW_PEEK_MS = 140L
const val BOTTOM_NAV_SHOW_HOLD_MS = 100L
const val BOTTOM_NAV_SHOW_SLIDE_MS = 560L

// Child-page back dismiss — #36 equal thirds (600 ms) + hold creep (mockup #2+#3)
const val CHILD_PAGE_BACK_DISMISS_PEEK_MS = 200L
const val CHILD_PAGE_BACK_DISMISS_HOLD_MS = 200L
const val CHILD_PAGE_BACK_DISMISS_SLIDE_MS = 200L

/** Soft-start sine creep during hold (mockup #3). */
const val CHILD_PAGE_BACK_HOLD_CREEP_SINE_PX = 10f

/** Smooth cosine drift during hold (mockup #2). */
const val CHILD_PAGE_BACK_HOLD_CREEP_DRIFT_PX = 18f

const val CHILD_PAGE_BACK_DISMISS_TOTAL_MS: Long =
    CHILD_PAGE_BACK_DISMISS_PEEK_MS + CHILD_PAGE_BACK_DISMISS_HOLD_MS + CHILD_PAGE_BACK_DISMISS_SLIDE_MS

/** [Animatable] progress 0 = fully hidden, 1 = fully visible at peek plateau. */
const val CHILD_PAGE_BACK_DISMISS_PEEK_FRACTION: Float =
    CHILD_PAGE_BACK_DISMISS_PEEK_MS.toFloat() / CHILD_PAGE_BACK_DISMISS_TOTAL_MS.toFloat()

/** Progress through dismiss timeline where hold plateau ends. */
const val CHILD_PAGE_BACK_DISMISS_HOLD_END: Float =
    (CHILD_PAGE_BACK_DISMISS_PEEK_MS + CHILD_PAGE_BACK_DISMISS_HOLD_MS).toFloat() /
        CHILD_PAGE_BACK_DISMISS_TOTAL_MS.toFloat()

/** Defer clearing parent [childPageVisible] after NavHost/detail pop (0 = show nav immediately). */
const val CHILD_PAGE_NAV_EXIT_DELAY_MS = 0L

fun childPageBackPeekAmount(dismissProgress: Float): Float {
    val progress = dismissProgress.coerceIn(0f, 1f)
    if (progress <= CHILD_PAGE_BACK_DISMISS_PEEK_FRACTION) {
        return (progress / CHILD_PAGE_BACK_DISMISS_PEEK_FRACTION).coerceIn(0f, 1f)
    }
    if (progress <= CHILD_PAGE_BACK_DISMISS_HOLD_END) {
        return 1f
    }
    return 1f
}

/** Extra X during hold: soft sine start + smooth cosine drift (variants 2+3). */
fun childPageBackHoldCreepPx(dismissProgress: Float): Float {
    val progress = dismissProgress.coerceIn(0f, 1f)
    if (progress <= CHILD_PAGE_BACK_DISMISS_PEEK_FRACTION || progress > CHILD_PAGE_BACK_DISMISS_HOLD_END) {
        return 0f
    }
    val holdSpan = CHILD_PAGE_BACK_DISMISS_HOLD_END - CHILD_PAGE_BACK_DISMISS_PEEK_FRACTION
    if (holdSpan <= 0f) return 0f
    val holdLocal = ((progress - CHILD_PAGE_BACK_DISMISS_PEEK_FRACTION) / holdSpan).coerceIn(0f, 1f)
    val halfPi = (PI / 2.0).toFloat()
    val sinePart = CHILD_PAGE_BACK_HOLD_CREEP_SINE_PX * sin(holdLocal * halfPi)
    val driftPart = CHILD_PAGE_BACK_HOLD_CREEP_DRIFT_PX * (0.5f - 0.5f * cos(holdLocal * PI.toFloat()))
    return sinePart + driftPart
}

fun childPageBackTranslationX(
    dismissProgress: Float,
    peekPx: Float,
    screenWidthPx: Float,
    visualExponent: Float,
): Float {
    val progress = dismissProgress.coerceIn(0f, 1f)
    if (progress <= CHILD_PAGE_BACK_DISMISS_PEEK_FRACTION) {
        val visual = childPageBackPeekAmount(progress).toDouble().pow(visualExponent.toDouble()).toFloat()
        return peekPx * visual
    }
    if (progress <= CHILD_PAGE_BACK_DISMISS_HOLD_END) {
        val visual = childPageBackPeekAmount(CHILD_PAGE_BACK_DISMISS_PEEK_FRACTION)
            .toDouble()
            .pow(visualExponent.toDouble())
            .toFloat()
        return peekPx * visual + childPageBackHoldCreepPx(progress)
    }
    val slideT = (
        (progress - CHILD_PAGE_BACK_DISMISS_HOLD_END) /
            (1f - CHILD_PAGE_BACK_DISMISS_HOLD_END)
        ).coerceIn(0f, 1f)
    val peekVisual = childPageBackPeekAmount(CHILD_PAGE_BACK_DISMISS_PEEK_FRACTION)
        .toDouble()
        .pow(visualExponent.toDouble())
        .toFloat()
    return peekPx * peekVisual + (screenWidthPx - peekPx * peekVisual) * slideT
}

fun childPageBackScrimAlpha(dismissProgress: Float, maxAlpha: Float, visualExponent: Float): Float {
    val progress = dismissProgress.coerceIn(0f, 1f)
    if (progress <= CHILD_PAGE_BACK_DISMISS_PEEK_FRACTION) {
        val visual = childPageBackPeekAmount(progress).toDouble().pow(visualExponent.toDouble()).toFloat()
        return maxAlpha * visual
    }
    if (progress <= CHILD_PAGE_BACK_DISMISS_HOLD_END) {
        return maxAlpha
    }
    val fadeT = (
        (progress - CHILD_PAGE_BACK_DISMISS_HOLD_END) /
            (1f - CHILD_PAGE_BACK_DISMISS_HOLD_END)
        ).coerceIn(0f, 1f)
    return maxAlpha * (1f - fadeT)
}

/**
 * Dismiss progress 0→1 on a wall-clock timeline (200 + 200 hold + 200 ms = 600 ms).
 * Spatial [MotionScheme] springs settle in ~300ms and ignore the ms remap fractions above.
 */
suspend fun Animatable<Float, *>.animateChildPageBackDismiss(
    @Suppress("UNUSED_PARAMETER") motionScheme: MotionScheme,
) {
    val current = value.coerceIn(0f, 1f)
    if (current >= 1f) return
    val remainingFraction = (1f - current).coerceIn(0f, 1f)
    val durationMillis = (CHILD_PAGE_BACK_DISMISS_TOTAL_MS * remainingFraction)
        .toInt()
        .coerceAtLeast(1)
    animateTo(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = durationMillis,
            easing = LinearEasing,
        ),
    )
}

private const val BOTTOM_NAV_PROGRESS_EPSILON = 0.02f

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
suspend fun Animatable<Float, *>.animateBottomNavHide(motionScheme: MotionScheme) {
    val spec = motionScheme.fastSpatialSpec<Float>()
    val current = value.coerceIn(0f, 1f)
    if (current <= BOTTOM_NAV_PROGRESS_EPSILON) return

    val peekVisible = 1f - CHILD_PAGE_MOTION_PEEK_FRACTION
    if (current > peekVisible + BOTTOM_NAV_PROGRESS_EPSILON) {
        animateTo(peekVisible, spec)
        delay(BOTTOM_NAV_HIDE_HOLD_MS)
    } else if (current > BOTTOM_NAV_PROGRESS_EPSILON) {
        delay(BOTTOM_NAV_HIDE_HOLD_MS)
    }
    animateTo(0f, spec)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
suspend fun Animatable<Float, *>.animateBottomNavShow(motionScheme: MotionScheme) {
    val spec = motionScheme.defaultSpatialSpec<Float>()
    val current = value.coerceIn(0f, 1f)
    if (current >= 1f - BOTTOM_NAV_PROGRESS_EPSILON) return
    animateTo(1f, spec)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
suspend fun Animatable<Float, *>.animateBottomNavForChildPage(
    childPageVisible: Boolean,
    motionScheme: MotionScheme,
) {
    if (childPageVisible) {
        animateBottomNavHide(motionScheme)
    } else {
        animateBottomNavShow(motionScheme)
    }
}
