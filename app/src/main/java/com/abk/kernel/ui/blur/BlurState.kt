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
    val wantsBackgroundPainter: Boolean
        get() = blurEnabled && backgroundExpEnabled && backgroundImageEnabled && !backgroundUri.isNullOrBlank()
}

internal fun isBlurCapableDevice(): Boolean = isRuntimeShaderSupported()

@Composable
fun isBlurActive(enableBlur: Boolean): Boolean =
    enableBlur && LocalBlurState.current != null && isBlurCapableDevice()
