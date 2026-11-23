package com.abk.kernel.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.xingguangcuican6666.abk.ui.components.AbkHero
import io.github.xingguangcuican6666.abk.ui.components.AbkScaffold
import io.github.xingguangcuican6666.abk.ui.components.AbkTopBar
import io.github.xingguangcuican6666.abk.ui.theme.AbkSpacing

/** Horizontal page gutter shared by every destination. */
val PageGutter = 20.dp

/** Space reserved under the scroll body so the bottom bar never covers the last row. */
private val BottomBarClearance = 96.dp

/**
 * A scrolling destination page: standard top bar, hero (eyebrow/title/description)
 * and a body column. Scaffold insets are applied once, here.
 *
 * [scrolls] is forwarded to the top bar so it can draw its divider only while the
 * page is actually scrolled.
 */
@Composable
fun AbkPage(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    eyebrow: String? = null,
    description: String? = null,
    heroActions: (@Composable () -> Unit)? = null,
    topBarActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    var scrolled by remember { mutableStateOf(false) }
    scrolled = scrollState.value > 0
    AbkScaffold(
        modifier = modifier,
        topBar = {
            AbkTopBar(
                title = title,
                subtitle = subtitle,
                scrolled = scrolled,
                actions = topBarActions,
            )
        },
    ) { _: PaddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = PageGutter)
                .padding(top = AbkSpacing.Sm, bottom = BottomBarClearance),
            verticalArrangement = Arrangement.spacedBy(AbkSpacing.Lg),
        ) {
            AbkHero(title = title, eyebrow = eyebrow, description = description, actions = heroActions)
            content()
        }
    }
}
