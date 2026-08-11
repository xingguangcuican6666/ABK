package com.abk.kernel.ui.blur

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme

/**
 * Creates a [LocalBlurState] backdrop for a screen and exposes it to its content.
 *
 * Wrap a screen's scaffold in this composable. The screen attaches
 * [Modifier.blurSource] to its **body** content root (the part that scrolls beneath
 * the bars) so it is captured as the blur source, and passes `enableBlur = true` to
 * its top bar so the bar applies [Modifier.blurEffect].
 *
 * The backdrop MUST NOT include the bars themselves (that would blur the bars onto
 * themselves and crash the render thread), so [Modifier.blurSource] goes on the body
 * only, never on a container that wraps the top bar. When blur is disabled or
 * unsupported this is a no-op passthrough.
 */
@Composable
fun BlurHost(
    blurConfig: BlurConfig,
    content: @Composable () -> Unit,
) {
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val backgroundPainter = rememberBlurBackgroundPainter(blurConfig)
    val backdrop = rememberBlurBackdrop(
        enableBlur = blurConfig.blurEnabled,
        surfaceColor = surfaceColor,
        backgroundPainter = backgroundPainter,
    )
    CompositionLocalProvider(LocalBlurState provides backdrop) {
        content()
    }
}

/**
 * Attaches the active backdrop's blur source to a screen's body content.
 *
 * Use as the root modifier of a screen's scaffold body (the content that scrolls
 * beneath the bars). No-op when no backdrop is provided.
 */
@Composable
fun Modifier.blurSourceBody(): Modifier =
    if (LocalBlurState.current != null) this.blurSource() else this
