package io.github.xingguangcuican6666.abk.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import io.github.xingguangcuican6666.abk.ui.theme.AbkMotion
import io.github.xingguangcuican6666.abk.ui.theme.abkTween

/**
 * The scale a control shrinks to while it is held down.
 *
 * The web catalogue presses its buttons to `scale(.985)` and its icon buttons
 * to `scale(.94)`; the two are kept apart here so the small, dense targets do
 * not shrink as hard as the wide ones.
 */
internal const val AbkPressScaleButton = 0.985f
internal const val AbkPressScaleIcon = 0.94f

/**
 * The pressed scale of a control, animated while the finger is down.
 *
 * Reduced motion collapses the tween to zero, so the control still shrinks but
 * does not glide into the shrink.
 */
@Composable
internal fun abkPressedScale(interactionSource: MutableInteractionSource, pressedScale: Float): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = abkTween(AbkMotion.Fast, AbkMotion.EaseOut),
        label = "abkPressScale",
    )
    return scale
}

/** Applies a pressed scale to a control without changing its layout size. */
@Composable
internal fun Modifier.abkPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float,
): Modifier {
    val scale = abkPressedScale(interactionSource, pressedScale)
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
