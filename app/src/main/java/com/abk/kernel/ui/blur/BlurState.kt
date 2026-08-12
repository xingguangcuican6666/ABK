package com.abk.kernel.ui.blur

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported

/** Exposes active [LayerBackdrop] to blur consumers. */
val LocalBlurState = compositionLocalOf<LayerBackdrop?> { null }

/** Immutable snapshot of blur preferences. */
data class BlurConfig(
    val blurEnabled: Boolean,
    val backgroundExpEnabled: Boolean,
    val backgroundUri: String?,
    val backgroundImageEnabled: Boolean,
) {
    /**
     * Whether the software StackBlur card path should render the custom background into
     * cards. Deliberately independent of [blurEnabled]: the software path needs no
     * runtime shaders, so it must be reachable on API 26-32 where the AGSL bar blur
     * (and its settings toggle) does not exist.
     */
    val wantsBackgroundPainter: Boolean
        get() = backgroundExpEnabled && backgroundImageEnabled && !backgroundUri.isNullOrBlank()
}

internal fun isBlurCapableDevice(): Boolean = isRuntimeShaderSupported()

@Composable
fun isBlurActive(enableBlur: Boolean): Boolean =
    enableBlur && LocalBlurState.current != null && isBlurCapableDevice()
