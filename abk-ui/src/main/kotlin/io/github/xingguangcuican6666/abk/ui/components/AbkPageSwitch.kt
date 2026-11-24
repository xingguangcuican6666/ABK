package io.github.xingguangcuican6666.abk.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import io.github.xingguangcuican6666.abk.ui.theme.AbkMotion
import io.github.xingguangcuican6666.abk.ui.theme.abkTween

/**
 * Swaps whole pages with a directional push.
 *
 * The incoming page enters from the far end of the reading direction and the
 * outgoing page leaves towards the near end, so moving forward reads as "the
 * next page slides in" and moving back reads as the mirror image.
 * [SlideDirection.Start] and [SlideDirection.End] are the layout-direction aware
 * directions, so the page travels the other way in right-to-left locales
 * instead of always moving left.
 *
 * Each page keeps its own saveable state: the holder stores the state of a
 * page when it leaves the composition and restores it when the page comes
 * back, so a half-typed field survives a trip to another destination.
 *
 * [forward] decides which way the pair moves; it is given the outgoing and the
 * incoming page.
 */
@Composable
public fun <S : Any> AbkPageSwitch(
    target: S,
    modifier: Modifier = Modifier,
    forward: (from: S, to: S) -> Boolean = { _, _ -> true },
    content: @Composable AnimatedContentScope.(S) -> Unit,
) {
    val holder = rememberSaveableStateHolder()
    // Reduced motion collapses these to zero, so the swap still happens — it just does not travel.
    val enterFade = abkTween<Float>(AbkMotion.Base, AbkMotion.EaseOut)
    val enterSlide = abkTween<IntOffset>(AbkMotion.Base, AbkMotion.EaseInOut)
    val exitFade = abkTween<Float>(AbkMotion.Fast, AbkMotion.EaseOut)
    val exitSlide = abkTween<IntOffset>(AbkMotion.Fast, AbkMotion.EaseInOut)
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        transitionSpec = {
            // Both halves travel the same way, the way a stack of pages does: the incoming page
            // comes in over the outgoing one and pushes it off the far edge.
            val direction =
                if (forward(initialState, targetState)) SlideDirection.Start else SlideDirection.End
            (fadeIn(enterFade) + slideIntoContainer(direction, enterSlide))
                .togetherWith(fadeOut(exitFade) + slideOutOfContainer(direction, exitSlide))
                // The pages fill the same box, so there is no size to animate; a spring on the
                // container size would fight the reduced-motion contract.
                .using(null)
        },
    ) { state ->
        val scope = this
        holder.SaveableStateProvider(state) { scope.content(state) }
    }
}
