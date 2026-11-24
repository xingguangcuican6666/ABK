package io.github.xingguangcuican6666.abk.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * Motion tokens.
 *
 * The durations mirror the web catalogue's `--dur-fast/base/slow` scale and the
 * easings mirror its `--ease-out`, `--ease-in-out` and `--ease-spring` curves,
 * so both catalogues move the same way.
 */
public object AbkMotion {
    public const val Fast: Int = 120
    public const val Base: Int = 220
    public const val Slow: Int = 360

    /** Decelerating curve for entrances and exits: `cubic-bezier(.22, .61, .36, 1)`. */
    public val EaseOut: Easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)

    /** Symmetric curve for two-way moves: `cubic-bezier(.65, 0, .35, 1)`. */
    public val EaseInOut: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

    /** Overshooting curve for toggles and indicators: `cubic-bezier(.34, 1.56, .64, 1)`. */
    public val EaseSpring: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
}

/**
 * A tween on the ABK duration scale.
 *
 * Reduced motion collapses the duration to zero instead of removing the
 * transition, so the end state is still reached and nothing has to special-case
 * the flag — the same contract the web catalogue keeps with
 * `prefers-reduced-motion`.
 */
@Composable
public fun <T> abkTween(
    durationMs: Int = AbkMotion.Base,
    easing: Easing = AbkMotion.EaseOut,
): TweenSpec<T> = tween(if (AbkTheme.reduceMotion) 0 else durationMs, easing = easing)

/**
 * Material's motion scheme, filled with the ABK durations and easings.
 *
 * Material components read their timing from the scheme — the snackbar's fade
 * and scale, the modal sheet's slide, the menu's scale — so publishing it here
 * gives them the catalogue's motion instead of Material's spring defaults.
 * Spatial specs move things (the sheet slides), effect specs change how things
 * look (the snackbar fades), which is how the web catalogue uses its two
 * curve families as well.
 */
@Immutable
public class AbkMotionScheme(private val reducedMotion: Boolean) : MotionScheme {
    private fun <T> spec(durationMs: Int, easing: Easing): FiniteAnimationSpec<T> =
        tween(if (reducedMotion) 0 else durationMs, easing = easing)

    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = spec(AbkMotion.Base, AbkMotion.EaseOut)

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = spec(AbkMotion.Fast, AbkMotion.EaseOut)

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = spec(AbkMotion.Slow, AbkMotion.EaseOut)

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = spec(AbkMotion.Base, AbkMotion.EaseSpring)

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = spec(AbkMotion.Fast, AbkMotion.EaseSpring)

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = spec(AbkMotion.Slow, AbkMotion.EaseSpring)
}
