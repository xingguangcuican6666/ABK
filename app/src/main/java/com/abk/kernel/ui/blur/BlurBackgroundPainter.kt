package com.abk.kernel.ui.blur

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter

/**
 * Returns the custom background image as a [Painter] to be drawn into the blur
 * backdrop, so the background becomes part of the frosted-glass source.
 *
 * Returns `null` when the background feature is disabled, the URI is empty, or
 * background-into-blur is turned off. Uses Coil (same cache/decoder as
 * [com.abk.kernel.ui.components.AppBackgroundHost]).
 */
@Composable
fun rememberBlurBackgroundPainter(config: BlurConfig): Painter? {
    if (!config.wantsBackgroundPainter) return null
    return rememberAsyncImagePainter(
        model = config.backgroundUri,
        contentScale = ContentScale.Crop,
    )
}
