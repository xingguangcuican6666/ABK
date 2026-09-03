package com.abk.kernel.miuix.util

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import com.abk.kernel.ui.blur.LocalBlurState
import com.abk.kernel.ui.blur.blurEffect

/**
 * Creates a [LayerBackdrop] that captures content for blur effects. Thin wrapper over
 * [com.abk.kernel.ui.blur.rememberBlurBackdrop] (the shared dev/miuix blur implementation).
 *
 * @param enableBlur Whether blur is enabled. Returns `null` when disabled.
 * @param surfaceColor The surface color to draw as an opaque base to prevent bleed-through.
 * Should match the current theme's surface color (MD3 or MIUIX).
 * @return A backdrop for use with [BlurredBar] and [Modifier.blurSource], or `null`.
 */
@Composable
fun rememberBlurBackdrop(enableBlur: Boolean, surfaceColor: Color) =
    com.abk.kernel.ui.blur.rememberBlurBackdrop(
        enableBlur = enableBlur,
        surfaceColor = surfaceColor,
    )

/**
 * Wraps content with a blur effect when [backdrop] is non-null.
 *
 * Use this to wrap a TopAppBar in the Scaffold's `topBar` slot. Set the TopAppBar's
 * `color` to [Color.Transparent] when the backdrop is active so the blur shows through.
 *
 * @param backdrop The backdrop providing captured content. Pass `null` to skip blur.
 * @param surfaceColor The surface color for the blur blend. Matches the theme surface color.
 * @param blurActive Whether blur is currently active. Defaults to `true`. Set to `false` to
 * temporarily disable blur without removing the backdrop.
 * @param content The composable to wrap (typically a TopAppBar).
 */
@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    surfaceColor: Color,
    blurActive: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (blurActive) {
        CompositionLocalProvider(LocalBlurState provides backdrop) {
            Box(modifier = Modifier.blurEffect(blendColor = surfaceColor.copy(alpha = AbkMiuixBlurTintAlpha))) {
                content()
            }
        }
    } else {
        Box {
            content()
        }
    }
}

/** Tint alpha applied to [BlurredBar]'s frosted glass, mirroring the prior miuix value. */
private const val AbkMiuixBlurTintAlpha = 0.87f
