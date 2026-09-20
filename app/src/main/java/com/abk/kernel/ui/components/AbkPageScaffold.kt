@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.abk.kernel.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abk.kernel.ui.blur.BlurConfig
import com.abk.kernel.ui.blur.BlurScreenScaffold
import com.abk.kernel.ui.theme.appPageBackgroundColor
import com.abk.kernel.ui.theme.uiSurfaceColor

/**
 * The single page scaffold every top-level and child screen builds on. Before this,
 * each screen hand-rolled a `BlurScreenScaffold { ExpressiveTopBar(...) }` pair — 27
 * near-identical copies across the app, each free to drift in container color, blur
 * wiring and top-bar options. This bundles that pair once so screens declare only
 * *what* their bar says, not *how* to assemble it, matching Android 17's uniform
 * one-surface page chrome.
 *
 * [content] receives the measured top-bar height so the body can reserve its top inset
 * (pair with `AbkInsets.contentTopGap`).
 */
@Composable
fun AbkPageScaffold(
    title: String,
    blurConfig: BlurConfig,
    modifier: Modifier = Modifier,
    blurEnabled: Boolean = false,
    navigationIcon: @Composable (() -> Unit)? = null,
    compactTitle: Boolean = false,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    /** Transparent when the page paints its own wallpaper backdrop underneath. */
    containerColor: Color = appPageBackgroundColor(uiSurfaceColor(MaterialTheme.colorScheme.surface)),
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (topBarHeight: Dp) -> Unit,
) {
    BlurScreenScaffold(
        blurConfig = blurConfig,
        modifier = modifier,
        containerColor = containerColor,
        topBar = {
            ExpressiveTopBar(
                title = title,
                navigationIcon = navigationIcon,
                compactTitle = compactTitle,
                scrollBehavior = scrollBehavior,
                enableBlur = blurEnabled,
                actions = actions,
            )
        },
        content = content,
    )
}

/**
 * Android-17-style page entrance: a one-shot fade + gentle upward settle applied to a
 * whole screen body the first time it composes. Kept subtle (12dp rise, spatial spring)
 * so it reads as the surface arriving, never as a distracting slide.
 */
@Composable
fun Modifier.abkPageEnter(): Modifier {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "abk-page-enter",
    )
    val density = LocalDensity.current
    return this.graphicsLayer {
        alpha = progress
        translationY = with(density) { (12.dp.toPx()) * (1f - progress) }
    }
}
