package com.abk.kernel.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import io.github.xingguangcuican6666.abk.ui.components.AbkHero
import io.github.xingguangcuican6666.abk.ui.components.AbkScaffold
import io.github.xingguangcuican6666.abk.ui.components.AbkTopBar
import io.github.xingguangcuican6666.abk.ui.theme.AbkSpacing

/** Horizontal page gutter shared by every destination. */
val PageGutter = 20.dp

/** Space reserved under the scroll body so the bottom bar never covers the last row. */
private val BottomBarClearance = 96.dp

/**
 * A scrolling destination page: brand bar on top, then the page headline and
 * its body.
 *
 * The scaffold's [PaddingValues] are applied to the body — Material3 lays
 * content out from the window origin, so dropping them hides the headline
 * behind the top bar and the status bar.
 */
@Composable
fun AbkPage(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    heroActions: (@Composable () -> Unit)? = null,
    topBarActions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    AbkScaffold(
        modifier = modifier,
        topBar = {
            AbkTopBar(
                title = stringResource(R.string.app_name),
                scrolled = scrollState.value > 0,
                actions = topBarActions,
            )
        },
    ) { padding: PaddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = PageGutter)
                .padding(top = AbkSpacing.Sm, bottom = BottomBarClearance),
            verticalArrangement = Arrangement.spacedBy(AbkSpacing.Lg),
        ) {
            AbkHero(title = title, description = description, actions = heroActions)
            content()
        }
    }
}
