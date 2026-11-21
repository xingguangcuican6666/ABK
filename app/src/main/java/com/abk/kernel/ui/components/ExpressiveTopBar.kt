@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.abk.kernel.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.ui.blur.blurEffect
import com.abk.kernel.ui.blur.isBlurActive
import com.abk.kernel.ui.theme.uiSurfaceColor

val AbkScreenHorizontalPadding: Dp = 24.dp
private val ExpressiveTopBarActionHeight: Dp = 56.dp

@Composable
fun ExpressiveFlexibleTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    compactTitle: Boolean = false,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    enableBlur: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    ExpressiveTopBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        compactTitle = compactTitle,
        scrollBehavior = scrollBehavior,
        enableBlur = enableBlur,
        actions = actions
    )
}

@Composable
fun ExpressiveTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    largeTitle: Boolean = false,
    compactTitle: Boolean = false,
    collapsing: Boolean = true,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    enableBlur: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val hasNavigation = navigationIcon != null
    val useLargeTitle = largeTitle || !hasNavigation
    val behavior = scrollBehavior
    val density = LocalDensity.current
    val largeTitleStyle = if (compactTitle) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge
    val expandedTitleHeight = with(density) { largeTitleStyle.lineHeight.toDp() } + 24.dp
    val canCollapse = collapsing && useLargeTitle && behavior != null
    val collapseRange = if (canCollapse) expandedTitleHeight else 0.dp

    if (useLargeTitle && behavior != null) {
        SideEffect {
            behavior.state.heightOffsetLimit = -with(density) { collapseRange.toPx() }
            if (!canCollapse) {
                behavior.state.heightOffset = 0f
            }
        }
    }

    val collapsedFraction = if (canCollapse && behavior != null) {
        val limit = behavior.state.heightOffsetLimit
        if (limit != 0f) {
            (behavior.state.heightOffset / limit).coerceIn(0f, 1f)
        } else {
            0f
        }
    } else {
        0f
    }
    val expandedFraction = 1f - collapsedFraction
    val titleCollapseOffsetPx = with(density) { 16.dp.toPx() }
    val collapsedTitleStyle = MaterialTheme.typography.titleLarge
    val blurActive = isBlurActive(enableBlur)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (blurActive) Modifier.blurEffect() else Modifier),
        color = if (blurActive) Color.Transparent else uiSurfaceColor(MaterialTheme.colorScheme.surface),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ExpressiveTopBarActionHeight)
                    .padding(
                        start = if (hasNavigation) 4.dp else AbkScreenHorizontalPadding,
                        end = AbkScreenHorizontalPadding
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (navigationIcon != null) {
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        navigationIcon()
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clipToBounds(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = title,
                        modifier = Modifier
                            .graphicsLayer { alpha = if (useLargeTitle) collapsedFraction else 1f }
                            .then(if (useLargeTitle && collapsedFraction < 0.5f) Modifier.clearAndSetSemantics {} else Modifier.semantics { heading() }),
                        style = collapsedTitleStyle,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
            if (useLargeTitle) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(expandedTitleHeight * expandedFraction)
                        .padding(horizontal = AbkScreenHorizontalPadding)
                        .clipToBounds()
                        .graphicsLayer {
                            alpha = expandedFraction
                            translationY = -titleCollapseOffsetPx * collapsedFraction
                        },
                    contentAlignment = Alignment.BottomStart
                ) {
                    Text(
                        text = title,
                        modifier = if (collapsedFraction >= 0.5f) Modifier.clearAndSetSemantics {} else Modifier.semantics { heading() },
                        style = largeTitleStyle,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
