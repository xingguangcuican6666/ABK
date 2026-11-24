package com.abk.kernel.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import io.github.xingguangcuican6666.abk.ui.components.AbkHeading
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
 *
 * The body is a list of sections, not a list of loose controls: every child of
 * [content] is separated by one generous gap ([AbkSpacing.Xxl]), and a heading
 * travels with the rows it names inside a [PageSection] rather than floating
 * halfway between the previous block and its own.
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
            verticalArrangement = Arrangement.spacedBy(AbkSpacing.Xxl),
        ) {
            AbkHero(title = title, description = description, actions = heroActions)
            content()
        }
    }
}

/**
 * A heading and the rows it names, held together as one block.
 *
 * Without this, a heading and a card are siblings and the page has to choose a
 * single gap for both, which is either too loose between the heading and its
 * card or too tight between sections. Here the gap inside the block is tight
 * ([AbkSpacing.Md]) and the page's gap between blocks stays wide.
 */
@Composable
fun PageSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AbkSpacing.Md), content = content)
}

/** A [PageSection] that opens with a heading. */
@Composable
fun PageSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    PageSection(modifier) {
        AbkHeading(title)
        content()
    }
}
