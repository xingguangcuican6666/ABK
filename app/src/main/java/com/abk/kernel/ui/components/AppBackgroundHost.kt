package com.abk.kernel.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.abk.kernel.ui.blur.LocalBlurBackgroundAnchor
import com.abk.kernel.ui.blur.LocalBlurBackgroundSize
import com.abk.kernel.ui.blur.LocalBlurredBackgroundPainter
import com.abk.kernel.ui.blur.LocalBlurredCardBackground
import com.abk.kernel.ui.blur.LocalBlurredCardBackgroundEnabled
import com.abk.kernel.ui.blur.rememberBlurredCardBackground
import com.abk.kernel.ui.theme.LocalAppBackgroundEnabled
import com.abk.kernel.ui.theme.LocalUiSurfaceAlpha

@Composable
fun AppBackgroundHost(
    backgroundUri: String?,
    backgroundEnabled: Boolean,
    uiSurfaceAlpha: Float,
    blurBackgroundEnabled: Boolean = false,
    content: @Composable () -> Unit
) {
    val hasBackground = backgroundEnabled && !backgroundUri.isNullOrBlank()
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var backgroundCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Decode at display size (stable across IME insets), never at the image's original size.
    val displayMetrics = context.resources.displayMetrics
    // Seed the viewport from display metrics so the blurred-card backdrop can start
    // loading on the very first composition instead of waiting for onGloballyPositioned.
    val initialBackgroundSize = remember(displayMetrics.widthPixels, displayMetrics.heightPixels) {
        IntSize(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }
    var backgroundSize by remember(initialBackgroundSize) { mutableStateOf(initialBackgroundSize) }
    val backgroundPainter = if (hasBackground) {
        val request = remember(
            backgroundUri,
            displayMetrics.widthPixels,
            displayMetrics.heightPixels,
        ) {
            ImageRequest.Builder(context)
                .data(backgroundUri)
                .size(displayMetrics.widthPixels, displayMetrics.heightPixels)
                // Software bitmap so the blurred-card backdrop can reuse this exact
                // decoded bitmap (hardware bitmaps can't be sampled by the StackBlur
                // pass). Keeps the wallpaper to a single decode.
                .allowHardware(false)
                // Fade the wallpaper in over the neutral surface instead of popping in.
                .crossfade(true)
                .build()
        }
        rememberAsyncImagePainter(
            model = request,
            contentScale = ContentScale.Crop,
        )
    } else {
        null
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                if (backgroundCoordinates !== coordinates) {
                    backgroundCoordinates = coordinates.takeIf { it.isAttached }
                }
                // LayoutCoordinates.size is not observable; mirror it into state.
                if (coordinates.isAttached && coordinates.size != backgroundSize) {
                    backgroundSize = coordinates.size
                }
            }
            // While a wallpaper is configured, wait on a slightly lighter neutral than
            // surface: on a cold start the decode lands a frame or two later, and pure
            // black behind translucent frosted surfaces reads as a black flash (this
            // mirrors ReSukiSU's use of surfaceContainer as the pre-load color).
            .background(
                if (hasBackground) colorScheme.surfaceContainer else colorScheme.surface
            )
    ) {
        if (backgroundPainter != null) {
            Image(
                painter = backgroundPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        CompositionLocalProvider(
            LocalBlurBackgroundAnchor provides backgroundCoordinates,
            LocalBlurBackgroundSize provides backgroundSize,
            LocalBlurredBackgroundPainter provides backgroundPainter,
        ) {
            val blurredCardBackground = rememberBlurredCardBackground(
                uri = backgroundUri,
                enabled = blurBackgroundEnabled && hasBackground,
            )
            CompositionLocalProvider(
                LocalBlurredCardBackground provides blurredCardBackground,
                LocalBlurredCardBackgroundEnabled provides (blurBackgroundEnabled && hasBackground),
                LocalUiSurfaceAlpha provides if (hasBackground) {
                    uiSurfaceAlpha.coerceIn(0f, 1f)
                } else {
                    1f
                },
                LocalAppBackgroundEnabled provides hasBackground,
            ) {
                Box(Modifier.fillMaxSize()) {
                    content()
                }
            }
        }
    }
}

@Composable
fun AppPageBackground(
    backgroundUri: String?,
    backgroundImageEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val hasBackground = backgroundImageEnabled && !backgroundUri.isNullOrBlank()
    val sharedPainter = LocalBlurredBackgroundPainter.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (hasBackground) {
            // Reuse the wallpaper painter already decoded by the enclosing
            // AppBackgroundHost instead of issuing a second Coil request. A separate
            // AsyncImage here has a distinct cache key (it requests the original size)
            // and no placeholder, so on low-end devices the child page flashes the
            // opaque surface (black in dark theme) before the wallpaper lands. Sharing
            // the painter keeps the wallpaper on screen from the first frame.
            if (sharedPainter != null) {
                Image(
                    painter = sharedPainter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = backgroundUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
