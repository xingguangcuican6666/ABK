package com.abk.kernel.miuix.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.abk.kernel.R

/**
 * Ported from SukiSU-Ultra SearchStatus with animation state machine.
 *
 * Represents the search overlay lifecycle: COLLAPSED -> EXPANDING -> EXPANDED -> COLLAPSING -> COLLAPSED.
 */
@Stable
data class SearchStatus(
    val label: String,
    val searchText: String = "",
    val current: Status = Status.COLLAPSED,
    val offsetY: Dp = 0.dp,
    val resultStatus: ResultStatus = ResultStatus.DEFAULT
) {
    fun isExpand(): Boolean = current == Status.EXPANDED
    fun isCollapsed(): Boolean = current == Status.COLLAPSED
    fun shouldExpand(): Boolean = current == Status.EXPANDED || current == Status.EXPANDING
    fun shouldCollapsed(): Boolean = current == Status.COLLAPSED || current == Status.COLLAPSING
    fun isAnimatingExpand(): Boolean = current == Status.EXPANDING

    fun onAnimationComplete(): SearchStatus {
        return when (current) {
            Status.EXPANDING -> copy(current = Status.EXPANDED)
            Status.COLLAPSING -> copy(searchText = "", current = Status.COLLAPSED)
            else -> this
        }
    }

    @Composable
    fun TopAppBarAnim(
        modifier: Modifier = Modifier,
        visible: Boolean = shouldCollapsed(),
        backgroundColor: Color = MiuixTheme.colorScheme.surface,
        content: @Composable () -> Unit
    ) {
        Box(modifier = modifier) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(backgroundColor)
            )
            Box(
                modifier = Modifier
                    .graphicsLayer { alpha = if (visible) 1f else 0f }
            ) {
                content()
            }
        }
    }

    enum class Status {
        EXPANDED, EXPANDING, COLLAPSED, COLLAPSING
    }

    enum class ResultStatus {
        DEFAULT, EMPTY, LOAD, SHOW
    }
}

/**
 * Like `Modifier.padding(top = ...)` but reads [top] in the layout phase, so a frame-rate value
 * relayouts instead of recomposing.
 */
private fun Modifier.deferredTopPadding(top: () -> Dp): Modifier =
    layout { measurable, constraints ->
        val topPx = top().roundToPx().coerceAtLeast(0)
        val placeable = measurable.measure(
            Constraints(
                minWidth = constraints.minWidth,
                maxWidth = constraints.maxWidth,
                minHeight = (constraints.minHeight - topPx).coerceAtLeast(0),
                maxHeight = if (constraints.maxHeight == Constraints.Infinity) {
                    Constraints.Infinity
                } else {
                    (constraints.maxHeight - topPx).coerceAtLeast(0)
                }
            )
        )
        val width = constraints.constrainWidth(placeable.width)
        val height = constraints.constrainHeight(placeable.height + topPx)
        layout(width, height) {
            placeable.place(0, topPx)
        }
    }

/**
 * Wraps the main content; only renders when search is collapsed.
 */
@Composable
fun SearchStatus.SearchBox(
    content: @Composable () -> Unit
) {
    if (shouldCollapsed()) content()
}

/**
 * Search overlay pager — animates TopAppBar expansion and hosts the
 * expanded search input + results.
 */
@Composable
fun SearchStatus.SearchPager(
    onSearchStatusChange: (SearchStatus) -> Unit,
    defaultResult: @Composable () -> Unit,
    expandBar: @Composable (SearchStatus, (SearchStatus) -> Unit, Dp) -> Unit = { searchStatus, onStatusChange, padding ->
        SearchBar(searchStatus, onStatusChange, padding)
    },
    searchBarTopPadding: Dp = 12.dp,
    result: @Composable () -> Unit
) {
    val searchStatus = this
    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val topPadding by animateDpAsState(
        targetValue = if (searchStatus.shouldExpand()) {
            systemBarsPadding + 5.dp
        } else {
            max(searchStatus.offsetY, 0.dp)
        },
        animationSpec = tween(300, easing = LinearOutSlowInEasing),
        label = "SearchPagerTopPadding"
    ) {
        onSearchStatusChange(searchStatus.onAnimationComplete())
    }
    val surfaceAlpha by animateFloatAsState(
        if (searchStatus.shouldExpand()) 1f else 0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "SearchPagerSurfaceAlpha"
    )
    val surfaceColor = MiuixTheme.colorScheme.surface

    Column(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(5f)
            .drawBehind { drawRect(surfaceColor.copy(alpha = surfaceAlpha)) }
            .semantics { onClick { false } }
            .then(
                if (!searchStatus.isCollapsed()) Modifier.pointerInput(Unit) { } else Modifier
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = topPadding)
                .then(
                    if (!searchStatus.isCollapsed()) Modifier.background(MiuixTheme.colorScheme.surface)
                    else Modifier
                ),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!searchStatus.isCollapsed()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(MiuixTheme.colorScheme.surface)
                ) {
                    expandBar(searchStatus, onSearchStatusChange, searchBarTopPadding)
                }
            }
            AnimatedVisibility(
                visible = searchStatus.isExpand() || searchStatus.isAnimatingExpand(),
                enter = expandHorizontally() + slideInHorizontally(initialOffsetX = { it }),
                exit = shrinkHorizontally() + slideOutHorizontally(targetOffsetX = { it })
            ) {
                Text(
                    text = stringResource(R.string.search_cancel),
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 4.dp, end = 16.dp, top = searchBarTopPadding, bottom = 6.dp)
                        .clickable(
                            interactionSource = null,
                            enabled = searchStatus.isExpand(),
                            indication = null
                        ) {
                            onSearchStatusChange(
                                searchStatus.copy(
                                    searchText = "",
                                    resultStatus = SearchStatus.ResultStatus.DEFAULT,
                                    current = SearchStatus.Status.COLLAPSING
                                )
                            )
                        }
                )
            }
        }
        AnimatedVisibility(
            visible = searchStatus.isExpand(),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            when (searchStatus.resultStatus) {
                SearchStatus.ResultStatus.DEFAULT -> defaultResult()
                SearchStatus.ResultStatus.EMPTY -> {}
                SearchStatus.ResultStatus.LOAD -> {}
                SearchStatus.ResultStatus.SHOW -> result()
            }
        }
    }

    // System back button collapses the search when expanded.
    BackHandler(enabled = searchStatus.shouldExpand()) {
        onSearchStatusChange(
            searchStatus.copy(
                searchText = "",
                resultStatus = SearchStatus.ResultStatus.DEFAULT,
                current = SearchStatus.Status.COLLAPSING
            )
        )
    }
}

/**
 * Actual search input (BasicTextField) rendered inside SearchPager when expanded.
 */
@Composable
fun SearchBar(
    searchStatus: SearchStatus,
    onSearchStatusChange: (SearchStatus) -> Unit,
    searchBarTopPadding: Dp = 12.dp,
) {
    val focusRequester = remember { FocusRequester() }
    var textFieldValue by remember { mutableStateOf(TextFieldValue(searchStatus.searchText)) }

    LaunchedEffect(searchStatus.searchText) {
        if (textFieldValue.text != searchStatus.searchText) {
            textFieldValue = TextFieldValue(searchStatus.searchText)
        }
    }

    BasicTextField(
        value = textFieldValue,
        onValueChange = {
            textFieldValue = it
            onSearchStatusChange(
                searchStatus.copy(
                    searchText = it.text,
                    resultStatus = if (it.text.isNotBlank()) {
                        SearchStatus.ResultStatus.SHOW
                    } else {
                        SearchStatus.ResultStatus.DEFAULT
                    }
                )
            )
        },
        singleLine = true,
        textStyle = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
            color = MiuixTheme.colorScheme.onSurface
        ),
        cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = searchBarTopPadding, bottom = 6.dp)
            .heightIn(min = 45.dp)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .focusRequester(focusRequester),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "search",
                    modifier = Modifier
                        .size(44.dp)
                        .padding(start = 16.dp, end = 8.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                )
                Box(modifier = Modifier.weight(1f)) {
                    innerTextField()
                }
                AnimatedVisibility(
                    visible = searchStatus.searchText.isNotEmpty(),
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        tint = MiuixTheme.colorScheme.onSurface,
                        contentDescription = "clear",
                        modifier = Modifier
                            .size(44.dp)
                            .padding(start = 8.dp, end = 16.dp)
                            .clickable(
                                interactionSource = null,
                                indication = null
                            ) {
                                textFieldValue = TextFieldValue("")
                                onSearchStatusChange(
                                    searchStatus.copy(
                                        searchText = "",
                                        resultStatus = SearchStatus.ResultStatus.DEFAULT
                                    )
                                )
                            },
                    )
                }
            }
        }
    )

    LaunchedEffect(Unit) {
        if (searchStatus.isAnimatingExpand()) {
            focusRequester.requestFocus()
        }
    }
}

/**
 * Fake (non-editable) search input rendered in the TopAppBar bottomContent slot.
 * Acts as a tap target to trigger EXPANDING on the SearchStatus state machine.
 */
@Composable
fun SearchBarFake(
    label: String,
    searchBarTopPadding: Dp = 12.dp,
) {
    InputField(
        query = "",
        onQueryChange = { },
        label = label,
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "search",
                modifier = Modifier
                    .size(44.dp)
                    .padding(start = 16.dp, end = 8.dp),
                tint = MiuixTheme.colorScheme.onSurfaceContainerHigh,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = searchBarTopPadding, bottom = 6.dp),
        onSearch = { },
        enabled = false,
        expanded = false,
        onExpandedChange = { }
    )
}
