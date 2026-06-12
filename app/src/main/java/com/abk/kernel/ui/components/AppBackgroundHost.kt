package com.abk.kernel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.MaterialTheme
import coil.compose.AsyncImage
import com.abk.kernel.ui.theme.LocalAppBackgroundEnabled
import com.abk.kernel.ui.theme.LocalUiSurfaceAlpha

@Composable
fun AppBackgroundHost(
    backgroundUri: String?,
    backgroundEnabled: Boolean,
    uiSurfaceAlpha: Float,
    content: @Composable () -> Unit
) {
    val hasBackground = backgroundEnabled && !backgroundUri.isNullOrBlank()
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
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
            LocalUiSurfaceAlpha provides if (hasBackground) uiSurfaceAlpha.coerceIn(0f, 1f) else 1f,
            LocalAppBackgroundEnabled provides hasBackground
        ) {
            content()
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
