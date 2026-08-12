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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.abk.kernel.ui.blur.LocalBlurBackgroundAnchor
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
    // One shared painter for the visible background image and every blur backdrop.
    // Decode the wallpaper at full-screen resolution, which is stable across transient
    // insets (soft keyboard / IME resizing the window): a large user image is never
    // decoded at its original size, and an IME opening does not re-decode the wallpaper
    // (which would flash the background to empty while it reloads).
    val displayMetrics = context.resources.displayMetrics
    val backgroundPainter = if (hasBackground) {
        val request = remember(
            backgroundUri,
            displayMetrics.widthPixels,
            displayMetrics.heightPixels,
        ) {
            ImageRequest.Builder(context)
                .data(backgroundUri)
                .size(displayMetrics.widthPixels, displayMetrics.heightPixels)
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
            }
            .background(colorScheme.surface)
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
                content()
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
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (hasBackground) {
            AsyncImage(
                model = backgroundUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
