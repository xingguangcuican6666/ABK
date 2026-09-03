package com.abk.kernel.miuix.animation.predictiveback

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventTransitionState
import kotlinx.coroutines.CoroutineScope

/**
 * Strategy interface for predictive back animation implementations.
 *
 * Ported from ReSukiSU (https://github.com/ReSukiSU/ReSukiSU). Each style
 * implements onPredictivePopTransitionSpec / onPopTransitionSpec / onTransitionSpec
 * (as member extensions on [AnimatedContentTransitionScope]) plus optional
 * [predictiveBackAnimationDecorator] and [onPagePop] hooks.
 *
 * ABK uses exactly two implementations:
 * - [MiuixDefaultPredictiveBackHandler]: ON behavior — library defaults
 * - [NonePredictiveBackHandler]: OFF behavior — BackHandler intercepts the gesture
 *   so predictivePopTransitionSpec is bypassed; the regular popTransitionSpec
 *   (library default slide) still runs on commit.
 */
interface PredictiveBackHandler {
    /**
     * Called when the back event is committed (gesture completed or back button clicked).
     * Implementations that rely on [predictiveBackAnnotationDecorator]'s own BackHandler
     * interception can leave this empty.
     */
    suspend fun onBackPressed(
        transitionState: NavigationEventTransitionState?,
        currentPageKey: NavKey?,
    )

    /**
     * Called when the page is actually popped from the view tree.
     * Implementations can use [animationScope] to reset any internal animation state
     * (e.g., snap Animatable back to 0).
     */
    fun onPagePop(contentPageKey: Any, animationScope: CoroutineScope) {}

    /**
     * A UI decorator applied to every page's content [androidx.compose.foundation.layout.Box].
     * Allows handlers to install their own [androidx.activity.compose.BackHandler]
     * (e.g., [NonePredictiveBackHandler] intercepts the gesture before NavDisplay sees it)
     * or to apply graphicsLayer / clip modifiers (e.g., AOSP/Scale styles for corner radius).
     */
    @Composable
    fun Modifier.predictiveBackAnnotation(
        transitionState: NavigationEventTransitionState?,
        contentPageKey: Any,
        currentPageKey: NavKey?,
    ): Modifier

    /**
     * Transition spec for predictive back (swipe) gesture.
     */
    fun AnimatedContentTransitionScope<Scene<NavKey>>.onPredictivePopTransitionSpec(
        @NavigationEvent.SwipeEdge swipeEdge: Int
    ): ContentTransform

    /**
     * Transition spec for standard pop (non-gesture back, or gesture commit after back-handler interception).
     */
    fun AnimatedContentTransitionScope<Scene<NavKey>>.onPopTransitionSpec(): ContentTransform

    /**
     * Transition spec for forward navigation (push).
     */
    fun AnimatedContentTransitionScope<Scene<NavKey>>.onTransitionSpec(): ContentTransform
}

/**
 * Helpers to invoke [PredictiveBackHandler] member extensions from inside a
 * NavDisplay lambda, where the [AnimatedContentTransitionScope] is the lambda's
 * receiver. Kotlin's member-extension calling requires BOTH receivers present;
 * these helpers bridge that with `with(handler) { with(scope) { fn() } }`.
 */
fun PredictiveBackHandler.invokeTransitionSpec(
    scope: AnimatedContentTransitionScope<Scene<NavKey>>
): ContentTransform = with(this) { with(scope) { onTransitionSpec() } }

fun PredictiveBackHandler.invokePopTransitionSpec(
    scope: AnimatedContentTransitionScope<Scene<NavKey>>
): ContentTransform = with(this) { with(scope) { onPopTransitionSpec() } }

fun PredictiveBackHandler.invokePredictivePopTransitionSpec(
    scope: AnimatedContentTransitionScope<Scene<NavKey>>,
    swipeEdge: Int
): ContentTransform = with(this) { with(scope) { onPredictivePopTransitionSpec(swipeEdge) } }
