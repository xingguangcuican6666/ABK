package com.abk.kernel.ui.blur

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A screen scaffold that hosts a [LocalBlurState] backdrop for frosted-glass bars.
 *
 * The body backdrop is laid out full-screen from y=0 while its content keeps the
 * horizontal and bottom safe-drawing insets, so the top-bar region has content to
 * blur. The [topBar] is drawn as a floating overlay **outside** the backdrop's
 * capture region; if the top bar were inside the captured content, its own blur
 * would sample itself and crash the render thread (SIGSEGV).
 *
 * The top bar is measured with a [SubcomposeLayout] before the body, so the body
 * receives the real bar height on its first layout pass.
 *
 * Screens call it with a body that scrolls full-screen and reserves the bar height
 * via a leading [androidx.compose.foundation.layout.Spacer].
 *
 * @param blurConfig Blur preferences for this screen.
 * @param containerColor Background color of the scaffold.
 * @param topBar Floating top bar overlay (draws above the content, outside the backdrop).
 * @param content Body inside horizontal/bottom safe-drawing insets; receives the
 * measured [topBar] height so it can insert a leading Spacer instead of applying
 * top layout padding.
 */
@Composable
fun BlurScreenScaffold(
    blurConfig: BlurConfig,
    modifier: Modifier = Modifier,
    containerColor: Color,
    topBar: @Composable (() -> Unit)? = null,
    content: @Composable (topBarHeight: Dp) -> Unit,
) {
    val density = LocalDensity.current
    BlurHost(blurConfig = blurConfig) {
        SubcomposeLayout(
            modifier = modifier.fillMaxSize().background(containerColor)
        ) { constraints ->
            val barPlaceables = if (topBar != null) {
                subcompose("abk-top-bar", topBar)
                    .map { it.measure(constraints.copy(minHeight = 0)) }
            } else {
                emptyList()
            }
            val barHeightPx = barPlaceables.maxOfOrNull { it.height } ?: 0
            val contentPlaceable = subcompose("abk-body") {
                Box(
                    Modifier
                        .fillMaxSize()
                        .blurSourceBody()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                            )
                        )
                ) {
                    content(with(density) { barHeightPx.toDp() })
                }
            }.first().measure(constraints)
            layout(constraints.maxWidth, constraints.maxHeight) {
                contentPlaceable.place(0, 0)
                barPlaceables.forEach { it.place(0, 0) }
            }
        }
    }
}
