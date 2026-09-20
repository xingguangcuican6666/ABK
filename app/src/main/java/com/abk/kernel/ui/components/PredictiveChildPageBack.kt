package com.abk.kernel.ui.components

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

const val CHILD_PAGE_BACK_VISUAL_EXPONENT = 1.8f
const val CHILD_PAGE_BACK_SCALE_DELTA = 0.09f
const val CHILD_PAGE_BACK_SCRIM_ALPHA = 0.32f
val CHILD_PAGE_BACK_MAX_OFFSET = 56.dp
val CHILD_PAGE_BACK_MAX_CORNER = 32.dp

private fun visualProgressFor(dismissProgress: Float, predictiveBackEnabled: Boolean): Float {
    val peek = if (predictiveBackEnabled) {
        childPageBackPeekAmount(dismissProgress)
    } else {
        dismissProgress.coerceIn(0f, 1f)
    }
    return peek.toDouble().pow(CHILD_PAGE_BACK_VISUAL_EXPONENT.toDouble()).toFloat()
}

@Stable
class ChildPageBackController internal constructor(
    internal val translationXPx: Float,
    internal val visualProgress: Float,
    internal val backCorner: Dp,
    internal val scrimAlpha: Float,
    private val requestDismissInternal: () -> Unit,
    private val resetProgressInternal: () -> Unit,
) {
    fun requestDismiss() = requestDismissInternal.invoke()

    fun resetProgress() = resetProgressInternal.invoke()

    fun backTransformModifier(): Modifier = Modifier.graphicsLayer {
        translationX = translationXPx
        scaleX = 1f - CHILD_PAGE_BACK_SCALE_DELTA * visualProgress
        scaleY = 1f - CHILD_PAGE_BACK_SCALE_DELTA * visualProgress
        alpha = 1f - 0.06f * visualProgress
        shape = RoundedCornerShape(backCorner)
        clip = visualProgress > 0.01f
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberChildPageBackController(
    enabled: Boolean,
    predictiveBackEnabled: Boolean,
    onBack: () -> Unit,
): ChildPageBackController {
    val motionScheme = MaterialTheme.motionScheme
    val spatialSpec: AnimationSpec<Float> = if (predictiveBackEnabled) {
        motionScheme.defaultSpatialSpec()
    } else {
        motionScheme.fastSpatialSpec()
    }
    val animatable = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dismissJobs = remember { mutableSetOf<Job>() }

    suspend fun animateToDismissed() {
        val current = animatable.value.coerceIn(0f, 1f)
        if (current < 1f && predictiveBackEnabled) {
            animatable.animateChildPageBackDismiss(motionScheme)
        }
        onBack()
    }

    suspend fun animateCancel() {
        animatable.animateTo(0f, spatialSpec)
    }

    fun launchDismiss() {
        dismissJobs.forEach { it.cancel() }
        val job = scope.launch {
            try {
                animateToDismissed()
            } finally {
                dismissJobs.remove(coroutineContext.job)
            }
        }
        dismissJobs.add(job)
    }

    PredictiveBackHandler(enabled = enabled && predictiveBackEnabled) { progress ->
        try {
            progress.collect { backEvent ->
                val gestureProgress = backEvent.progress.coerceIn(0f, 1f)
                animatable.snapTo(gestureProgress * CHILD_PAGE_BACK_DISMISS_PEEK_FRACTION)
            }
            animateToDismissed()
        } catch (e: CancellationException) {
            animateCancel()
            throw e
        }
    }

    BackHandler(enabled = enabled && !predictiveBackEnabled) {
        launchDismiss()
    }

    val dismissProgress = animatable.value.coerceIn(0f, 1f)
    val visualProgress = visualProgressFor(dismissProgress, predictiveBackEnabled)
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val peekPx = with(density) { CHILD_PAGE_BACK_MAX_OFFSET.toPx() }
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val translationXPx = if (predictiveBackEnabled) {
        childPageBackTranslationX(
            dismissProgress = dismissProgress,
            peekPx = peekPx,
            screenWidthPx = screenWidthPx,
            visualExponent = CHILD_PAGE_BACK_VISUAL_EXPONENT,
        )
    } else {
        peekPx * dismissProgress.coerceIn(0f, 1f).pow(CHILD_PAGE_BACK_VISUAL_EXPONENT)
    }
    val backCorner = with(density) { (CHILD_PAGE_BACK_MAX_CORNER.toPx() * visualProgress).toDp() }
    val scrimAlpha = if (predictiveBackEnabled) {
        childPageBackScrimAlpha(
            dismissProgress = dismissProgress,
            maxAlpha = CHILD_PAGE_BACK_SCRIM_ALPHA,
            visualExponent = CHILD_PAGE_BACK_VISUAL_EXPONENT,
        )
    } else {
        CHILD_PAGE_BACK_SCRIM_ALPHA * visualProgress
    }

    return ChildPageBackController(
        translationXPx = translationXPx,
        visualProgress = visualProgress,
        backCorner = backCorner,
        scrimAlpha = scrimAlpha,
        requestDismissInternal = ::launchDismiss,
        resetProgressInternal = { scope.launch { animatable.snapTo(0f) } },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun childPageOverlayEnterTransition(
    predictiveBackEnabled: Boolean,
    motionScheme: MotionScheme = MaterialTheme.motionScheme,
): EnterTransition =
    fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) +
        if (predictiveBackEnabled) {
            EnterTransition.None
        } else {
            slideInHorizontally(animationSpec = motionScheme.defaultSpatialSpec()) { width -> width / 4 }
        }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun childPageScrimExitTransition(
    predictiveBackEnabled: Boolean,
    motionScheme: MotionScheme = MaterialTheme.motionScheme,
): ExitTransition =
    if (predictiveBackEnabled) {
        ExitTransition.None
    } else {
        fadeOut(animationSpec = motionScheme.fastEffectsSpec())
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun childPageOverlayExitTransition(
    predictiveBackEnabled: Boolean,
    motionScheme: MotionScheme = MaterialTheme.motionScheme,
): ExitTransition =
    if (predictiveBackEnabled) {
        ExitTransition.None
    } else {
        fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
            slideOutHorizontally(animationSpec = motionScheme.fastSpatialSpec()) { width -> width }
    }

@Composable
fun ChildPageBackSurface(
    enabled: Boolean,
    predictiveBackEnabled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundContent: @Composable BoxScope.() -> Unit = {},
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    val controller = rememberChildPageBackController(
        enabled = enabled,
        predictiveBackEnabled = predictiveBackEnabled,
        onBack = onBack,
    )
    Box(modifier = modifier.fillMaxSize()) {
        backgroundContent()
        if (controller.scrimAlpha > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = controller.scrimAlpha))
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(controller.backTransformModifier())
        ) {
            content(controller::requestDismiss)
        }
    }
}
