package com.abk.kernel.ui.blur

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter

/**
 * Shared custom-background painter, provided by [com.abk.kernel.ui.components.AppBackgroundHost]
 * which already renders it. Blur hosts reuse it so the same URI decodes once instead of
 * once per screen scaffold (N+1 Coil requests for the same image).
 */
val LocalBlurredBackgroundPainter = compositionLocalOf<Painter?> { null }

/** Returns the custom background image painter for the blur backdrop. */
@Composable
fun rememberBlurBackgroundPainter(config: BlurConfig): Painter? {
    if (!config.wantsBackgroundPainter) return null
    return LocalBlurredBackgroundPainter.current
        ?: rememberAsyncImagePainter(
            model = config.backgroundUri,
            contentScale = ContentScale.Crop,
        )
}
