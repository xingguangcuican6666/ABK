package com.abk.kernel.ui.blur

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter

/** Returns the custom background image painter for the blur backdrop. */
@Composable
fun rememberBlurBackgroundPainter(config: BlurConfig): Painter? {
    if (!config.wantsBackgroundPainter) return null
    return rememberAsyncImagePainter(
        model = config.backgroundUri,
        contentScale = ContentScale.Crop,
    )
}
