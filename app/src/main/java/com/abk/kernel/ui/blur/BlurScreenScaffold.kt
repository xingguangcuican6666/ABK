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

/** Screen scaffold hosting a [LocalBlurState] backdrop for frosted-glass bars. */
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
