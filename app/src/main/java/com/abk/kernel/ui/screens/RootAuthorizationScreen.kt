@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.abk.kernel.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.abk.kernel.R
import com.abk.kernel.ui.blur.BlurScreenScaffold
import com.abk.kernel.ui.components.AbkCenteredLoadingTransition
import com.abk.kernel.ui.components.AbkInlineLoadingPill
import com.abk.kernel.ui.components.AbkLoadingPill
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.AppPageBackground
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.components.ObserveChildPageVisibility
import com.abk.kernel.ui.components.childPageOverlayEnterTransition
import com.abk.kernel.ui.components.childPageOverlayExitTransition
import com.abk.kernel.ui.components.childPageScrimExitTransition
import com.abk.kernel.ui.components.rememberAbkInteractiveRefreshPresentation
import com.abk.kernel.ui.components.rememberChildPageBackController
import com.abk.kernel.ui.components.rememberChildPageOverlayTransition
import com.abk.kernel.ui.theme.appPageBackgroundColor
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.viewmodel.MainViewModel
import io.github.xingguangcuican6666.abk.ui.components.AbkEmptyState
import io.github.xingguangcuican6666.abk.ui.components.AbkIconButton
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun RootAuthorizationScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onDetailPageVisibleChange: (Boolean) -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var showSystemApps by rememberSaveable { mutableStateOf(false) }
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    val motionScheme = MaterialTheme.motionScheme
    val apps = remember(state.rootGrantApps, query, showSystemApps) {
        state.rootGrantApps
            .filter { showSystemApps || !it.isSystemApp }
            .filter { app ->
                val needle = query.trim()
                needle.isBlank() ||
                    app.label.contains(needle, ignoreCase = true) ||
                    app.packageName.contains(needle, ignoreCase = true) ||
                    app.uid.toString().contains(needle)
            }
    }
    val selectedListApp = remember(state.rootGrantApps, selectedPackage) {
        selectedPackage?.let { packageName ->
            state.rootGrantApps.firstOrNull { it.packageName == packageName }
        }
    }
    val selectedDetailApp = remember(state.rootGrantDetailApp, selectedPackage) {
        state.rootGrantDetailApp?.takeIf { it.packageName == selectedPackage }
    }
    val detailPageVisible = selectedPackage != null
    val detailPageTransition = rememberChildPageOverlayTransition(
        visible = detailPageVisible,
        label = "root-auth-detail"
    )
    val canLeaveDetail = state.rootGrantSavingPackage == null && !state.rootGrantDetailLoading
    val showInitialLoading = state.rootGrantLoading && state.rootGrantApps.isEmpty()
    val refreshPresentation = rememberAbkInteractiveRefreshPresentation(loading = state.rootGrantLoading)
    val showRefreshListLoading = refreshPresentation.showLoading && !showInitialLoading
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(state.runtimeNavigationEnabled, state.abkRuntimeStatus?.runtimeBackend?.backend) {
        if (state.runtimeNavigationEnabled) vm.refreshRootGrantApps()
    }

    fun closeDetailPage() {
        if (canLeaveDetail) {
            selectedPackage = null
            vm.clearRootGrantDetail()
        }
    }

    val childPageBack = rememberChildPageBackController(
        enabled = detailPageVisible && canLeaveDetail,
        predictiveBackEnabled = state.predictiveBackEnabled,
        onBack = ::closeDetailPage,
    )

    ObserveChildPageVisibility(
        transition = detailPageTransition,
        onVisibleChange = onDetailPageVisibleChange,
        onAfterExitAnimation = { childPageBack.resetProgress() }
    )

    DisposableEffect(Unit) {
        onDispose { onDetailPageVisibleChange(false) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val childPageTopInset = outerPadding.calculateTopPadding()
        val childPageBottomInset = outerPadding.calculateBottomPadding()
        val childPageModifier = Modifier
            .fillMaxWidth()
            .height(maxHeight + childPageTopInset + childPageBottomInset)
            .offset(y = -childPageTopInset)

        BlurScreenScaffold(
            blurConfig = state.blurConfig,
            containerColor = appPageBackgroundColor(uiSurfaceColor(MaterialTheme.colorScheme.surface)),
            topBar = {
                ExpressiveTopBar(
                    title = stringResource(R.string.root_auth_title),
                    scrollBehavior = scrollBehavior,
                    enableBlur = state.blurEnabled,
                    actions = {
                        AbkIconButton(
                            icon = AbkIcons.Refresh,
                            contentDescription = stringResource(R.string.root_auth_refresh_list),
                            onClick = {
                                refreshPresentation.beginRefresh()
                                vm.refreshRootGrantApps(force = true)
                            },
                            enabled = !state.rootGrantLoading
                        )
                    }
                )
            }
        ) { topBarHeight ->
            if (showInitialLoading) {
                RootGrantInitialLoadingScreen(
                    topBarHeight = topBarHeight,
                    outerPadding = outerPadding,
                    query = query,
                    onQueryChange = { query = it },
                    showSystemApps = showSystemApps,
                    onShowSystemAppsChange = { showSystemApps = it },
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                )
                return@BlurScreenScaffold
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = AbkScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Spacer(Modifier.height(topBarHeight + 16.dp))
                RootGrantSearchField(
                    query = query,
                    onQueryChange = { query = it }
                )

                RootGrantControlsCard(
                    showSystemApps = showSystemApps,
                    onShowSystemAppsChange = { showSystemApps = it }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (showInitialLoading) {
                        RootGrantInitialLoading(modifier = Modifier.fillMaxSize())
                    } else {
                        Crossfade(
                            targetState = showRefreshListLoading,
                            label = "root-grant-refresh"
                        ) { refreshing ->
                            if (refreshing) {
                                RootGrantRefreshingRow(
                                    text = stringResource(R.string.root_auth_refreshing_list),
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        bottom = 80.dp + outerPadding.calculateBottomPadding()
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    state.rootGrantError?.let {
                                        item(key = "error") {
                                            RootGrantMessageCard(it) {
                                                refreshPresentation.beginRefresh()
                                                vm.refreshRootGrantApps(force = true)
                                            }
                                        }
                                    }

                                    if (!state.rootGrantLoading && apps.isEmpty()) {
                                        item(key = "empty") {
                                            AbkEmptyState(
                                                title = stringResource(R.string.root_auth_title),
                                                description = stringResource(
                                                    if (query.isBlank()) R.string.root_auth_no_apps
                                                    else R.string.root_auth_no_matching_apps
                                                ),
                                                icon = AbkIcons.Controls,
                                            )
                                        }
                                    }

                                    items(
                                        items = apps,
                                        key = { app -> "${app.uid}:${app.packageName}" }
                                    ) { app ->
                                        RootGrantAppCard(
                                            app = app,
                                            saving = state.rootGrantSavingPackage == app.packageName,
                                            anySaving = state.rootGrantSavingPackage != null,
                                            onToggle = { allowed -> vm.setRootGrantAllowed(app.packageName, allowed) },
                                            onOpen = {
                                                childPageBack.resetProgress()
                                                selectedPackage = app.packageName
                                                vm.openRootGrantProfile(app.packageName)
                                            },
                                            icon = {
                                                AppIcon(app.packageName, app.label)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        detailPageTransition.AnimatedVisibility(
            visible = { it },
            enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
            exit = childPageScrimExitTransition(state.predictiveBackEnabled, motionScheme),
            modifier = childPageModifier
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = childPageBack.scrimAlpha))
            )
        }

        detailPageTransition.AnimatedVisibility(
            visible = { it },
            enter = childPageOverlayEnterTransition(state.predictiveBackEnabled, motionScheme),
            exit = childPageOverlayExitTransition(state.predictiveBackEnabled, motionScheme),
            modifier = childPageModifier
        ) {
            selectedPackage?.let { packageName ->
                val headerApp = selectedDetailApp ?: selectedListApp
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(childPageBack.backTransformModifier())
                ) {
                    RootGrantDetailPageBackground(
                        backgroundUri = state.customBackgroundUri,
                        backgroundImageEnabled = state.backgroundImageEnabled
                    )
                    BlurScreenScaffold(
                        blurConfig = state.blurConfig,
                        containerColor = Color.Transparent,
                        topBar = {
                            ExpressiveTopBar(
                                title = headerApp?.label?.ifBlank { packageName } ?: packageName,
                                navigationIcon = {
                                    AbkIconButton(
                                        icon = AbkIcons.Back,
                                        contentDescription = stringResource(R.string.root_auth_back_to_list),
                                        enabled = canLeaveDetail,
                                        onClick = childPageBack::requestDismiss,
                                    )
                                },
                                enableBlur = state.blurEnabled
                            )
                        }
                    ) { topBarHeight ->
                        when {
                            state.rootGrantDetailLoading -> RootGrantDetailLoadingPage(topBarHeight = topBarHeight)
                            selectedDetailApp != null -> RootGrantProfilePage(
                                app = selectedDetailApp,
                                topBarHeight = topBarHeight,
                                saving = state.rootGrantSavingPackage == selectedDetailApp.packageName,
                                warning = state.rootGrantDetailWarning,
                                onSave = { profile ->
                                    vm.saveRootGrantProfile(profile)
                                },
                                icon = {
                                    AppIcon(selectedDetailApp.packageName, selectedDetailApp.label, Modifier.size(64.dp), 16.dp)
                                },
                            )
                            else -> RootGrantDetailMessagePage(
                                topBarHeight = topBarHeight,
                                message = state.rootGrantError ?: stringResource(R.string.runtime_manager_inactive)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RootGrantInitialLoadingScreen(
    topBarHeight: Dp,
    outerPadding: PaddingValues,
    query: String,
    onQueryChange: (String) -> Unit,
    showSystemApps: Boolean,
    onShowSystemAppsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = AbkScreenHorizontalPadding,
                top = topBarHeight + 16.dp,
                end = AbkScreenHorizontalPadding,
                bottom = 80.dp + outerPadding.calculateBottomPadding()
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RootGrantSearchField(
            query = query,
            onQueryChange = onQueryChange
        )
        RootGrantControlsCard(
            showSystemApps = showSystemApps,
            onShowSystemAppsChange = onShowSystemAppsChange
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            RootGrantInitialLoading()
        }
    }
}

@Composable
private fun RootGrantInitialLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AbkLoadingPill(text = stringResource(R.string.loading))
    }
}

@Composable
private fun RootGrantRefreshingRow(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AbkInlineLoadingPill(text = text)
    }
}

@Composable
private fun RootGrantDetailLoadingPage(
    topBarHeight: Dp
) {
    AbkCenteredLoadingTransition(
        text = stringResource(R.string.loading),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AbkScreenHorizontalPadding)
            .padding(top = topBarHeight + 16.dp)
    )
}

@Composable
private fun RootGrantDetailMessagePage(
    topBarHeight: Dp,
    message: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AbkScreenHorizontalPadding)
            .padding(top = topBarHeight + 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RootGrantDetailPageBackground(
    backgroundUri: String?,
    backgroundImageEnabled: Boolean
) {
    AppPageBackground(
        backgroundUri = backgroundUri,
        backgroundImageEnabled = backgroundImageEnabled
    )
}

@Composable
private fun AppIcon(
    packageName: String,
    label: String,
    modifier: Modifier = Modifier.size(56.dp),
    cornerSize: Dp = 14.dp
) {
    val context = LocalContext.current
    var drawable by remember(packageName) { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(packageName) {
        drawable = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
        }
    }
    val iconModifier = modifier.clip(RoundedCornerShape(cornerSize))
    if (drawable != null) {
        AsyncImage(
            model = drawable,
            contentDescription = label.ifBlank { packageName },
            contentScale = ContentScale.Crop,
            modifier = iconModifier
        )
    } else {
        Box(
            modifier = iconModifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            AbkIcon(AbkIcons.Modules, null, size = 28.dp)
        }
    }
}
