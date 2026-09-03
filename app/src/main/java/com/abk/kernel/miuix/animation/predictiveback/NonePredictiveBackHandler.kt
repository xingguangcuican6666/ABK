package com.abk.kernel.miuix.animation.predictiveback

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec
import androidx.navigationevent.NavigationEventTransitionState
import com.abk.kernel.ui.navigation3.LocalNavigator

/**
 * OFF behavior — completely bypasses NavDisplay's predictive back machinery
 * by installing a plain [BackHandler] in the content decorator that calls the
 * injected back callback the moment the gesture/btn commit happens.
 *
 * Effect:
 * - During the edge-swipe gesture: nothing visible (no preview)
 * - On gesture commit (or back-btn press): BackHandler pops the stack;
 *   the regular [onPopTransitionSpec] (library default slide) still runs.
 * - Forward navigation (push) is unchanged.
 *
 * This matches ReSukiSU's `NoPredictiveBackAnimation` handler semantics.
 */
class NonePredictiveBackHandler(
    private val onBack: () -> Unit,
) : PredictiveBackHandler {
    override suspend fun onBackPressed(
        transitionState: NavigationEventTransitionState?,
        currentPageKey: NavKey?,
    ) {
        // Ignored. The BackHandler installed inside predictiveBackAnnotation
        // intercepts the system back event BEFORE NavDisplay sees it, so this
        // callback is never reached for the predictive gesture flow.
    }

    @Composable
    override fun Modifier.predictiveBackAnnotation(
        transitionState: NavigationEventTransitionState?,
        contentPageKey: Any,
        currentPageKey: NavKey?,
    ): Modifier {
        val navigator = LocalNavigator.current
        val canPop = navigator.backStack.size > 1
        // Using BackHandler (not PredictiveBackHandler) here completely intercepts
        // the system predictive back dispatch: the gesture is treated as a
        // regular back press with no preview phase. The subsequent onBack()
        // triggers the regular popTransitionSpec (default slide) on commit.
        BackHandler(enabled = canPop) {
            onBack()
        }
        return this
    }

    override fun AnimatedContentTransitionScope<Scene<NavKey>>.onPredictivePopTransitionSpec(
        swipeEdge: Int
    ): ContentTransform = ContentTransform(
        targetContentEnter = EnterTransition.None,
        initialContentExit = ExitTransition.None,
        sizeTransform = null
    )

    override fun AnimatedContentTransitionScope<Scene<NavKey>>.onPopTransitionSpec(): ContentTransform =
        defaultPopTransitionSpec<NavKey>().invoke(this)

    override fun AnimatedContentTransitionScope<Scene<NavKey>>.onTransitionSpec(): ContentTransform =
        defaultTransitionSpec<NavKey>().invoke(this)
}
