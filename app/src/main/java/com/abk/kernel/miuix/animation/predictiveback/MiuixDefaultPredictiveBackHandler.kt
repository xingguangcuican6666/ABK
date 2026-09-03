package com.abk.kernel.miuix.animation.predictiveback

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec
import androidx.navigationevent.NavigationEventTransitionState

/**
 * ON behavior — delegates all transitions to MIUIX library defaults:
 * [defaultPredictivePopTransitionSpec] / [defaultPopTransitionSpec] / [defaultTransitionSpec].
 *
 * NavDisplay handles the predictive gesture via its internal machinery and
 * `NavDisplayTransitionEffects` (corner clipping, dimming). No custom
 * [predictiveBackAnnotation] needed — we just return `this`.
 */
class MiuixDefaultPredictiveBackHandler : PredictiveBackHandler {
    override suspend fun onBackPressed(
        transitionState: NavigationEventTransitionState?,
        currentPageKey: NavKey?,
    ) {
        // NavDisplay already synchronizes predictive back gesture progress with the
        // transition engine via NavigationEventTransitionState. Nothing for us to do here.
    }

    @Composable
    override fun Modifier.predictiveBackAnnotation(
        transitionState: NavigationEventTransitionState?,
        contentPageKey: Any,
        currentPageKey: NavKey?,
    ): Modifier = this

    override fun AnimatedContentTransitionScope<Scene<NavKey>>.onPredictivePopTransitionSpec(
        swipeEdge: Int
    ): ContentTransform = defaultPredictivePopTransitionSpec<NavKey>().invoke(this, swipeEdge)

    override fun AnimatedContentTransitionScope<Scene<NavKey>>.onPopTransitionSpec(): ContentTransform =
        defaultPopTransitionSpec<NavKey>().invoke(this)

    override fun AnimatedContentTransitionScope<Scene<NavKey>>.onTransitionSpec(): ContentTransform =
        defaultTransitionSpec<NavKey>().invoke(this)
}
