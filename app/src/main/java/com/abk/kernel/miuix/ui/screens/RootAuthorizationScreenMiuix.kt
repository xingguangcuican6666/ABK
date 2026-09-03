package com.abk.kernel.miuix.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.R
import com.abk.kernel.data.model.RootGrantApp
import com.abk.kernel.miuix.component.SearchBarFake
import com.abk.kernel.miuix.component.SearchBox
import com.abk.kernel.miuix.component.SearchPager
import com.abk.kernel.miuix.component.SearchStatus
import com.abk.kernel.miuix.util.BlurredBar
import com.abk.kernel.miuix.util.rememberBlurBackdrop
import com.abk.kernel.ui.navigation3.LocalNavigator
import com.abk.kernel.ui.navigation3.Route
import com.abk.kernel.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

// ── Data classes ──

private data class GroupedApp(
    val uid: Int,
    val label: String,
    val packageName: String,
    val apps: List<RootGrantApp>,
    val primary: RootGrantApp,
    val anyAllowSu: Boolean,
    val anyUmount: Boolean,
    val anyCustom: Boolean,
)

private enum class SortOption {
    DEFAULT,
    NAME_ASC,
    NAME_DESC,
    UID_ASC,
    UID_DESC,
}

@Composable
private fun SortOption.toLabel(): String {
    val labelRes = when (this) {
        SortOption.DEFAULT -> R.string.sort_default
        SortOption.NAME_ASC -> R.string.sort_name_asc
        SortOption.NAME_DESC -> R.string.sort_name_desc
        else -> null
    }
    return when (this) {
        SortOption.UID_ASC -> "UID ↑"
        SortOption.UID_DESC -> "UID ↓"
        else -> if (labelRes != null) stringResource(labelRes) else ""
    }
}// ── Main screen ──

@Composable
fun RootAuthorizationScreenMiuix(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
) {
    val state by vm.uiState.collectAsState()
    val navigator = LocalNavigator.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    // Search state
    var searchStatus by remember { mutableStateOf(SearchStatus("")) }
    val query = searchStatus.searchText

    // Sort & filter state
    var sortOption by remember { mutableStateOf(SortOption.DEFAULT) }
    var showSystemApps by rememberSaveable { mutableStateOf(false) }
    var showSortPopup by remember { mutableStateOf(false) }
    var showMorePopup by remember { mutableStateOf(false) }

    // Blur
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberBlurBackdrop(state.miuixBlurEnabled, surfaceColor)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else surfaceColor

    // Scroll behavior
    val scrollBehavior = MiuixScrollBehavior()
    val dynamicTopPadding by remember {
        derivedStateOf { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }

    // Group & filter apps
    val groupedApps = remember(state.rootGrantApps, sortOption, showSystemApps, query) {
        state.rootGrantApps
            .filter { app ->
                if (!showSystemApps && app.isSystemApp) false
                else if (query.isBlank()) true
                else {
                    app.label.contains(query, ignoreCase = true) ||
                        app.packageName.contains(query, ignoreCase = true)
                }
            }
            .groupBy { it.uid }
            .map { (uid, apps) ->
                val primary = apps.first()
                GroupedApp(
                    uid = uid,
                    label = primary.label,
                    packageName = primary.packageName,
                    apps = apps,
                    primary = primary,
                    anyAllowSu = apps.any { it.profile.allowSu },
                    anyUmount = apps.any { it.profile.umountModules },
                    anyCustom = apps.any {
                        !it.profile.rootUseDefault || it.profile.rootTemplate.isNotBlank()
                    },
                )
            }
            .sortedWith(
                when (sortOption) {
                    SortOption.DEFAULT -> compareByDescending<GroupedApp> { it.anyAllowSu }.thenBy { it.label.lowercase() }
                    SortOption.NAME_ASC -> compareBy { it.label.lowercase() }
                    SortOption.NAME_DESC -> compareByDescending { it.label.lowercase() }
                    SortOption.UID_ASC -> compareBy { it.uid }
                    SortOption.UID_DESC -> compareByDescending { it.uid }
                }
            )
    }

    // Refresh strings (hoisted outside remember for composable safety)
    val refreshListText = stringResource(R.string.root_auth_refresh_list)
    val isRefreshing = state.rootGrantLoading

    // Initial load
    LaunchedEffect(Unit) {
        vm.refreshRootGrantApps()
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop, surfaceColor) {
                searchStatus.TopAppBarAnim(backgroundColor = barColor) {
                    TopAppBar(
                        color = barColor,
                        title = stringResource(R.string.root_auth_title),
                        scrollBehavior = scrollBehavior,
                        bottomContent = {
                            Box(
                                modifier = Modifier
                                    .alpha(if (searchStatus.isCollapsed()) 1f else 0f)
                                    .onGloballyPositioned { coordinates ->
                                        with(density) {
                                            val newOffsetY = coordinates.positionInWindow().y.toDp()
                                            searchStatus = searchStatus.copy(offsetY = newOffsetY)
                                        }
                                    }
                                    .then(
                                        if (searchStatus.isCollapsed()) {
                                            Modifier.pointerInput(Unit) {
                                                detectTapGestures {
                                                    searchStatus = searchStatus.copy(
                                                        current = SearchStatus.Status.EXPANDING
                                                    )
                                                }
                                            }
                                        } else Modifier,
                                    ),
                            ) {
                                SearchBarFake(searchStatus.label, dynamicTopPadding)
                            }
                        },
                        actions = {
                            // Sort button
                            Box {
                                OverlayListPopup(
                                    show = showSortPopup,
                                    popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                                    alignment = PopupPositionProvider.Align.TopEnd,
                                    onDismissRequest = { showSortPopup = false },
                                ) {
                                    ListPopupColumn {
                                        SortOption.entries.forEachIndexed { index, option ->
                                            DropdownImpl(
                                                text = option.toLabel(),
                                                optionSize = SortOption.entries.size,
                                                isSelected = option == sortOption,
                                                index = index,
                                                onSelectedIndexChange = {
                                                    sortOption = SortOption.entries[it]
                                                    showSortPopup = false
                                                },
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { showSortPopup = true },
                                    holdDownState = showSortPopup,
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Sort,
                                        tint = MiuixTheme.colorScheme.onSurface,
                                        contentDescription = "排序",
                                    )
                                }
                            }
                            // More button
                            Box {
                                OverlayListPopup(
                                    show = showMorePopup,
                                    popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                                    alignment = PopupPositionProvider.Align.TopEnd,
                                    onDismissRequest = { showMorePopup = false },
                                ) {
                                    ListPopupColumn {
                                        DropdownImpl(
                                            text = stringResource(R.string.root_auth_show_system_apps),
                                            optionSize = 1,
                                            isSelected = showSystemApps,
                                            index = 0,
                                            onSelectedIndexChange = {
                                                showSystemApps = !showSystemApps
                                                showMorePopup = false
                                            },
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { showMorePopup = true },
                                    holdDownState = showMorePopup,
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.MoreCircle,
                                        tint = MiuixTheme.colorScheme.onSurface,
                                        contentDescription = null,
                                    )
                                }
                            }
                        },
                    )
                }
            }
        },
        popupHost = {
            Box(Modifier.fillMaxSize()) {
                searchStatus.SearchPager(
                    onSearchStatusChange = { searchStatus = it },
                    defaultResult = {},
                    searchBarTopPadding = dynamicTopPadding,
                ) {
                    val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .overScrollVertical(),
                    ) {
                        item { Spacer(Modifier.height(6.dp)) }
                        items(groupedApps, key = { it.uid }, contentType = { "search-group" }) { group ->
                            GroupedAppItem(
                                group = group,
                                onProfileClick = { navigator.push(Route.SuperUserProfile(group.uid)) },
                            )
                        }
                        item {
                            Spacer(Modifier.height(maxOf(outerPadding.calculateBottomPadding(), imeBottomPadding) + 80.dp))
                        }
                        if (groupedApps.isEmpty() && query.isNotBlank()) {
                            item {
                                Text(
                                    text = stringResource(R.string.root_auth_no_matching_apps),
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                                )
                            }
                        }
                    }
                }
                MiuixPopupUtils.MiuixPopupHost()
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        searchStatus.SearchBox {
            Box(
                modifier = Modifier.then(
                    if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier,
                ),
            ) {
                if (state.rootGrantApps.isEmpty() && !state.rootGrantLoading && state.rootGrantError == null) {
                    // Empty state - before first load
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(outerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.root_auth_no_apps),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.body2,
                        )
                    }
                } else if (state.rootGrantError != null) {
                    // Error state
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(outerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = state.rootGrantError.orEmpty(),
                            color = MiuixTheme.colorScheme.error,
                            style = MiuixTheme.textStyles.body2,
                        )
                    }
                } else {
                    PullToRefresh(
                        isRefreshing = isRefreshing,
                        onRefresh = { vm.refreshRootGrantApps(force = true) },
                        refreshTexts = listOf(refreshListText, refreshListText, refreshListText, refreshListText),
                        contentPadding = PaddingValues(
                            top = innerPadding.calculateTopPadding() + 6.dp,
                            start = innerPadding.calculateStartPadding(layoutDirection),
                            end = innerPadding.calculateEndPadding(layoutDirection),
                        ),
                    ) {
                        val listState = rememberLazyListState()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxHeight()
                                .scrollEndHaptic()
                                .overScrollVertical()
                                .nestedScroll(scrollBehavior.nestedScrollConnection),
                            contentPadding = PaddingValues(
                                top = innerPadding.calculateTopPadding() + 6.dp,
                                start = innerPadding.calculateStartPadding(layoutDirection),
                                end = innerPadding.calculateEndPadding(layoutDirection),
                            ),
                            overscrollEffect = null,
                        ) {
                            items(groupedApps, key = { it.uid }, contentType = { "group" }) { group ->
                                GroupedAppItem(
                                    group = group,
                                    onProfileClick = { navigator.push(Route.SuperUserProfile(group.uid)) },
                                )
                            }
                            item {
                                Spacer(Modifier.height(outerPadding.calculateBottomPadding() + 80.dp))
                            }
                            if (groupedApps.isEmpty() && query.isBlank() && state.rootGrantApps.isNotEmpty()) {
                                item {
                                    Text(
                                        text = stringResource(R.string.root_auth_no_matching_apps),
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    BackHandler(enabled = searchStatus.shouldExpand() && navigator.backStackSize() <= 1) {
        searchStatus = searchStatus.copy(
            searchText = "",
            resultStatus = SearchStatus.ResultStatus.DEFAULT,
            current = SearchStatus.Status.COLLAPSING
        )
    }
}

// ── Grouped app item ──

@Composable
private fun GroupedAppItem(
    group: GroupedApp,
    onProfileClick: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val multiApp = group.apps.size > 1

    val isInDark = isSystemInDarkTheme()
    val rootBg = MiuixTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
    val rootFg = MiuixTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
    val unmountBg = if (isInDark) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.3f)
    val unmountFg = if (isInDark) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.8f)
    val customBg = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
    val customFg = MiuixTheme.colorScheme.onSecondaryContainer

    val tags = remember(group.anyAllowSu, group.anyUmount, group.anyCustom) {
        buildList {
            if (group.anyAllowSu) {
                add(TagData("ROOT", rootBg, rootFg))
            } else if (group.anyUmount) {
                add(TagData("UMOUNT", unmountBg, unmountFg))
            }
            if (group.anyCustom) add(TagData("CUSTOM", customBg, customFg))
        }
    }

    Column {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
            onClick = onProfileClick,
            onLongPress = if (multiApp) ({ expanded = !expanded }) else null,
            showIndication = true,
            insideMargin = PaddingValues(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // App icon
                AppIconImage(
                    packageName = group.packageName,
                    label = group.label,
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(48.dp),
                )

                // Text labels
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (multiApp) {
                            "${group.label} (${group.apps.size} apps)"
                        } else {
                            group.label
                        },
                        modifier = Modifier.basicMarquee(),
                        fontWeight = FontWeight(550),
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = if (multiApp) {
                            "${group.apps.size} apps, UID ${group.uid}"
                        } else {
                            group.packageName
                        },
                        modifier = Modifier.basicMarquee(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight(550),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }

                // Status tags
                if (tags.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(start = 16.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        tags.forEach { tag ->
                            StatusTagChip(tag)
                        }
                    }
                }

                // Arrow
                val layoutDirection = LocalLayoutDirection.current
                Image(
                    imageVector = MiuixIcons.ChevronForward,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantActions),
                    modifier = Modifier
                        .graphicsLayer {
                            if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                        }
                        .padding(start = 8.dp)
                        .size(width = 10.dp, height = 16.dp),
                )
            }
        }

        // Expanded sub-apps for multi-app groups
        AnimatedVisibility(
            visible = expanded && multiApp,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp)) {
                group.apps.forEach { app ->
                    SubAppRow(app = app)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ── Sub app row (for multi-app UID groups) ──

@Composable
private fun SubAppRow(app: RootGrantApp) {
    Row(
        modifier = Modifier.padding(start = 32.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MiuixTheme.colorScheme.primaryContainer),
        )
        Spacer(Modifier.width(8.dp))
        AppIconImage(
            packageName = app.packageName,
            label = app.label,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = app.label.ifBlank { app.packageName },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Status tag chip ──

private data class TagData(
    val label: String,
    val background: Color,
    val contentColor: Color,
)

@Composable
private fun StatusTagChip(tag: TagData) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tag.background)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Text(
            text = tag.label,
            fontSize = 9.sp,
            fontWeight = FontWeight(750),
            color = tag.contentColor,
            textAlign = TextAlign.Center,
        )
    }
}

// ── App icon loader ──

@Composable
private fun AppIconImage(
    packageName: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val drawable = remember(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    }
    val iconModifier = modifier.clip(RoundedCornerShape(12.dp))

    if (drawable != null) {
        val bitmap = remember(drawable) {
            val bmp = runCatching {
                Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
            }.getOrNull()
            if (bmp != null) {
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
            }
            bmp
        }
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = label.ifBlank { packageName },
                modifier = iconModifier,
            )
        } else {
            AppIconPlaceholder(modifier = iconModifier, label = label)
        }
    } else {
        AppIconPlaceholder(modifier = iconModifier, label = label)
    }
}

@Composable
private fun AppIconPlaceholder(
    modifier: Modifier = Modifier,
    label: String = "",
) {
    Box(
        modifier = modifier.background(
            MiuixTheme.colorScheme.primaryContainer,
            RoundedCornerShape(12.dp),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = (label.firstOrNull()?.toString() ?: "?").uppercase(),
            color = MiuixTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
    }
}
