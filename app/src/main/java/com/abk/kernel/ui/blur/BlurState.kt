package com.abk.kernel.ui.blur

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported

/**
 * Composition local that exposes the active [LayerBackdrop] to blur consumers.
 *
 * Content that should be captured as the blur source attaches [Modifier.blurSource]
 * (wraps [top.yukonga.miuix.kmp.blur.layerBackdrop]); bars that should show a
 * frosted-glass effect attach [Modifier.blurEffect] (wraps
 * [top.yukonga.miuix.kmp.blur.textureBlur]). Both no-op when no backdrop is provided
 * (which is always the case below API 33, see [isBlurCapableDevice]).
 */
val LocalBlurState = compositionLocalOf<LayerBackdrop?> { null }

/**
 * Immutable snapshot of the blur feature's preferences for one screen.
 *
 * Collapses the values every [BlurScreenScaffold] call site needs into a single
 * argument, so future toggles touch one place instead of every screen.
 */
data class BlurConfig(
    val blurEnabled: Boolean,
    val backgroundExpEnabled: Boolean,
    val backgroundUri: String?,
    val backgroundImageEnabled: Boolean,
) {
    /** True when a custom background should be drawn into the blur source. */
    val wantsBackgroundPainter: Boolean
        get() = blurEnabled && backgroundExpEnabled && backgroundImageEnabled && !backgroundUri.isNullOrBlank()
}

/**
 * Whether the device can actually render the miuix frosted-glass path.
 *
 * miuix's `textureBlur` runs on AGSL runtime shaders, which exist from API 33
 * onward. This is the single gate both backdrop creation ([BlurBackdrop]) and
 * [isBlurActive] use, so the two cannot drift apart.
 */
internal fun isBlurCapableDevice(): Boolean = isRuntimeShaderSupported()

/**
 * Whether frosted-glass will actually render for [enableBlur] in the current composition.
 *
 * The miuix render path needs the user toggle, an active backdrop (which itself is
 * only created when the device is blur-capable), and [isBlurCapableDevice]. Callers
 * must use this to decide between a transparent bar and the opaque fallback surface.
 */
@Composable
fun isBlurActive(enableBlur: Boolean): Boolean =
    enableBlur && LocalBlurState.current != null && isBlurCapableDevice()
