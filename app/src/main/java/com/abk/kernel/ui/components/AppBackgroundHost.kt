package com.abk.kernel.ui.components

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
import androidx.compose.material3.MaterialTheme
import coil.compose.AsyncImage
import com.abk.kernel.ui.blur.LocalBlurBackgroundAnchor
import com.abk.kernel.ui.blur.LocalBlurredCardBackground
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
    var backgroundCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
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
        if (hasBackground) {
            AsyncImage(
                model = backgroundUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        CompositionLocalProvider(
            LocalBlurBackgroundAnchor provides backgroundCoordinates,
        ) {
            val blurredCardBackground = rememberBlurredCardBackground(
                uri = backgroundUri,
                enabled = blurBackgroundEnabled && hasBackground,
            )
            CompositionLocalProvider(
                LocalBlurredCardBackground provides blurredCardBackground,
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
