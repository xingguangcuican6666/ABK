package com.abk.kernel.ui.components

import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.updateTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

@Deprecated(
    message = "Use CHILD_PAGE_NAV_EXIT_DELAY_MS for NavHost-driven child pages",
    replaceWith = ReplaceWith("CHILD_PAGE_NAV_EXIT_DELAY_MS")
)
const val CHILD_PAGE_EXIT_DELAY_MS = CHILD_PAGE_NAV_EXIT_DELAY_MS

/**
 * Shared [Transition] for overlay child pages. Hoists enter/exit into one
 * [updateTransition] so [ObserveChildPageVisibility] can wait for the transition to
 * settle ([Transition.currentState] == [Transition.targetState]) instead of a fixed delay.
 */
@Composable
fun rememberChildPageOverlayTransition(
    visible: Boolean,
    label: String = "child-page-overlay"
): Transition<Boolean> = updateTransition(targetState = visible, label = label)

/**
 * Syncs bottom navigation with overlay child pages that use
 * [rememberChildPageOverlayTransition] and [Transition.AnimatedVisibility].
 *
 * Hides the nav when the overlay opens; shows it again only after exit
 * animations finish (currentState equals targetState and currentState is false).
 */
@Composable
fun ObserveChildPageVisibility(
    transition: Transition<Boolean>,
    onVisibleChange: (Boolean) -> Unit,
    onAfterExitAnimation: () -> Unit = {}
) {
    var wasOverlayVisible by remember { mutableStateOf(false) }
    val target = transition.targetState
    val current = transition.currentState
    val isTransitionIdle = current == target
    val exitSettled = !target && isTransitionIdle && !current

    LaunchedEffect(target, isTransitionIdle, current) {
        when {
            target -> {
                wasOverlayVisible = true
                onVisibleChange(true)
            }
            wasOverlayVisible && exitSettled -> {
                wasOverlayVisible = false
                onAfterExitAnimation()
                onVisibleChange(false)
            }
            !wasOverlayVisible -> onVisibleChange(false)
        }
    }
}

/**
 * Syncs bottom navigation with NavHost detail routes (no shared overlay transition).
 *
 * @param exitDelayMs Extra wait after [visible] becomes false before [onVisibleChange](false).
 * Use a positive value only when the route is popped **before** the dismiss animation finishes.
 * Do **not** pass [CHILD_PAGE_BACK_DISMISS_TOTAL_MS] when dismiss is handled by
 * [rememberChildPageBackController] (animation completes, then [onBack] pops the route).
 */
@Composable
fun ObserveChildPageVisibility(
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    exitDelayMs: Long = CHILD_PAGE_NAV_EXIT_DELAY_MS,
    onAfterExitAnimation: () -> Unit = {}
) {
    var childWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            childWasVisible = true
            onVisibleChange(true)
        } else {
            if (childWasVisible) {
                if (exitDelayMs > 0L) {
                    delay(exitDelayMs)
                }
                childWasVisible = false
                onAfterExitAnimation()
            }
            onVisibleChange(false)
        }
    }
}
