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
     * cards. The master [blurEnabled] switch stays the overall on/off for every blur
     * surface (AGSL bars on API 33+, software card blur on every API), while
     * [backgroundExpEnabled] opts the card path into using the custom background.
     */
    val wantsBackgroundPainter: Boolean
        get() = blurEnabled && backgroundExpEnabled && backgroundImageEnabled && !backgroundUri.isNullOrBlank()
}

internal fun isBlurCapableDevice(): Boolean = isRuntimeShaderSupported()

@Composable
fun isBlurActive(enableBlur: Boolean): Boolean =
    enableBlur && LocalBlurState.current != null && isBlurCapableDevice()
