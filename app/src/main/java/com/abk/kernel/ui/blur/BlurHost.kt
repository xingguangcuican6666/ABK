package com.abk.kernel.ui.blur

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme

/** Provides a [LocalBlurState] backdrop to screen content. */
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

/** Attaches active backdrop's blur source to body content. */
@Composable
fun Modifier.blurSourceBody(): Modifier =
    if (LocalBlurState.current != null) this.blurSource() else this
