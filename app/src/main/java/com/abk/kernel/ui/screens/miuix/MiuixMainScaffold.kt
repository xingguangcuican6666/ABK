package com.abk.kernel.ui.screens.miuix

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import android.os.Build
import coil.compose.AsyncImage
import com.abk.kernel.R
import com.abk.kernel.ui.components.LocalMiuixSnackbarHostState
import com.abk.kernel.ui.components.showAbkSnackbar
import com.abk.kernel.ui.screens.miuix.liquid.FloatingBottomBar
import com.abk.kernel.ui.screens.miuix.liquid.FloatingBottomBarItem
import com.abk.kernel.ui.screens.miuix.liquid.rememberMainPagerState
import com.abk.kernel.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

val LocalMiuixScrollBehavior = staticCompositionLocalOf<ScrollBehavior?> { null }
private data class MiuixTab(val key: String, val label: String, val icon: ImageVector)

@Composable
fun MiuixMainScaffold(vm: MainViewModel, pendingModuleInstallUri: String? = null) {
    val state by vm.uiState.collectAsState()
    val runtimeMode = state.runtimeNavigationEnabled; val nativeManagerActive = state.hasNativeManagerPermission
    LaunchedEffect(Unit) { vm.markMainUiEntered() }

    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.snackbarMessage, state.snackbarLongDuration, state.error) {
        when (val snackbar = state.snackbarMessage) {
            null -> {
                val error = state.error ?: return@LaunchedEffect
                snackbarHostState.showAbkSnackbar(message = error, longDuration = true)
                vm.clearError()
            }
            else -> {
                snackbarHostState.showAbkSnackbar(message = snackbar, longDuration = state.snackbarLongDuration)
                vm.clearSnackbar()
                if (state.error != null) vm.clearError()
            }
        }
    }

    // Pre-fetch string resources to avoid @Composable calls inside non-Composable lambdas (remember / buildList)
    val navStatusLabel = stringResource(R.string.nav_status)
    val navBuildLabel = stringResource(R.string.nav_build)
    val navModulesLabel = stringResource(R.string.nav_modules)
    val navFlashLabel = stringResource(R.string.nav_flash)
    val navSettingsLabel = stringResource(R.string.nav_settings)
    val navHomeLabel = stringResource(R.string.nav_home)
    val navInstalledModulesLabel = stringResource(R.string.nav_installed_modules)
    val navRootAuthLabel = stringResource(R.string.nav_root_auth)

    val classicTabs = remember { listOf(MiuixTab("status", navStatusLabel, Icons.Filled.Home), MiuixTab("build", navBuildLabel, Icons.Filled.Build), MiuixTab("modules", navModulesLabel,
        Icons.AutoMirrored.Filled.LibraryBooks
    ), MiuixTab("flash", navFlashLabel, Icons.Filled.FlashOn), MiuixTab("settings", navSettingsLabel, Icons.Filled.Settings)) }
    val runtimeTabs = remember(state.rootGranted, nativeManagerActive) { buildList { add(MiuixTab("runtime_home", navHomeLabel, Icons.Filled.Memory)); if (state.rootGranted) add(MiuixTab("installed_modules", navInstalledModulesLabel, Icons.Filled.Extension)); add(MiuixTab("modules", navModulesLabel,
        Icons.AutoMirrored.Filled.LibraryBooks
    )); if (nativeManagerActive) add(MiuixTab("root_auth", navRootAuthLabel, Icons.Filled.AdminPanelSettings)); add(MiuixTab("settings", navSettingsLabel, Icons.Filled.Settings)) } }
    val tabs = if (runtimeMode) runtimeTabs else classicTabs
    var selectedKey by rememberSaveable { mutableStateOf(if (runtimeMode) "runtime_home" else "status") }
    LaunchedEffect(runtimeMode) { selectedKey = if (runtimeMode) "runtime_home" else "status" }
    LaunchedEffect(pendingModuleInstallUri) { if (!pendingModuleInstallUri.isNullOrBlank()) { if (!runtimeMode) vm.setRuntimeNavigationEnabled(true); selectedKey = "installed_modules" } }
    val scope = rememberCoroutineScope()
    val floatingNavEnabled = state.floatingNavigationBarEnabled
    val glassEffectEnabled = floatingNavEnabled && state.glassNavigationEffectEnabled
    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val isBlurEnabled = glassEffectEnabled && blurSupported

    val pagerState = rememberPagerState(
        initialPage = tabs.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0),
        pageCount = { tabs.size }
    )

    val surfaceColor = colorScheme.surface
    val glassBackdrop = rememberLayerBackdrop { drawRect(surfaceColor); drawContent() }
    val mainPagerState = if (floatingNavEnabled) rememberMainPagerState(pagerState) else null

    // Sync selectedKey ← pagerState (user swipe / animateScrollToPage)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val key = tabs.getOrNull(page)?.key
            if (key != null) selectedKey = key
        }
    }
    // Sync glass bar indicator ← pagerState (user swipe)
    LaunchedEffect(pagerState.currentPage) { mainPagerState?.syncPage() }

    // Sync pagerState ← selectedKey (external jump only: pendingModuleInstallUri, runtimeMode)
    var lastExternalKey by remember { mutableStateOf(selectedKey) }
    LaunchedEffect(selectedKey) {
        if (selectedKey == lastExternalKey) return@LaunchedEffect
        lastExternalKey = selectedKey
        val targetPage = tabs.indexOfFirst { it.key == selectedKey }
        if (targetPage >= 0 && targetPage != pagerState.currentPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    // Secondary navigation
    val secondaryStack = remember { mutableStateListOf<NavKey>(MiuixScreen.Root) }
    val ep = entryProvider<NavKey> {
        entry<MiuixScreen.Root> { Box(Modifier.fillMaxSize()) }
        entry<MiuixScreen.PlanLib> { MiuixBuildPlanLibraryScreen(vm = vm, onClose = { secondaryStack.removeAt(secondaryStack.lastIndex) }) }
        entry<MiuixScreen.BuildQueue> { MiuixBuildQueueScreen(vm = vm, onClose = { secondaryStack.removeAt(secondaryStack.lastIndex) }) }
        entry<MiuixScreen.RepoSettings> { MiuixRepoManageScreen(vm = vm, onClose = { secondaryStack.removeAt(secondaryStack.lastIndex) }) }
        entry<MiuixScreen.RuntimeRepoSettings> { MiuixRuntimeRepoManageScreen(vm = vm, onClose = { secondaryStack.removeAt(secondaryStack.lastIndex) }) }
        entry<MiuixScreen.FlashDetail> { screen -> MiuixFlashDetailScreen(vm = vm, runId = screen.runId, onClose = { secondaryStack.removeAt(secondaryStack.lastIndex) }) }
        entry<MiuixScreen.PrebuiltDetail> { screen -> MiuixPrebuiltDetailScreen(vm = vm, releaseId = screen.releaseId, onClose = { secondaryStack.removeAt(secondaryStack.lastIndex) }) }
        entry<MiuixScreen.ThemeSettings> { MiuixThemeSettingsScreen(vm = vm, onClose = { secondaryStack.removeAt(secondaryStack.lastIndex) }) }
        entry<MiuixScreen.AboutSettings> { MiuixAboutSettingsScreen(onClose = { secondaryStack.removeAt(secondaryStack.lastIndex) }) }
        entry<MiuixScreen.OpenSourceLicenses> { MiuixOpenSourceLicensesScreen(onClose = { secondaryStack.removeAt(secondaryStack.lastIndex) }) }
    }

    CompositionLocalProvider(
        LocalMiuixBackStack provides secondaryStack,
        LocalMiuixSnackbarHostState provides snackbarHostState
    ) {
        BackHandler(enabled = secondaryStack.size > 1) {
            secondaryStack.removeAt(secondaryStack.lastIndex)
        }

        Box(Modifier.fillMaxSize().imePadding()) {
            // Global background image (shared with all Miuix screens)
            val backgroundUri = state.customBackgroundUri
            val backgroundEnabled = state.backgroundImageEnabled
            val hasBackground = backgroundEnabled && !backgroundUri.isNullOrBlank()
            val bg = colorScheme.background
            Box(Modifier.fillMaxSize().background(bg)) {
                if (hasBackground) {
                    AsyncImage(model = backgroundUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    val scrimColor = if (bg.luminance() > 0.5f) bg.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.38f)
                    Box(Modifier.fillMaxSize().background(scrimColor))
                }
            }

            val currentLabel = when (selectedKey) { "status", "runtime_home" -> stringResource(R.string.app_name); "modules" -> if (runtimeMode) stringResource(R.string.runtime_repo_standard_title) else stringResource(R.string.module_repo_abk_title); else -> tabs.getOrNull(pagerState.currentPage)?.label ?: "" }
            val topBarState = rememberTopAppBarState(); val scrollBehavior = MiuixScrollBehavior(state = topBarState)
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = colorScheme.surface,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = { TopAppBar(title = currentLabel, largeTitle = currentLabel, scrollBehavior = scrollBehavior, actions = {
    if (selectedKey == "runtime_home") { IconButton(onClick = { vm.refreshAbkRuntimeStatus() }) { Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.runtime_refresh)) } }
    if (selectedKey in setOf("status", "runtime_home")) { IconButton(onClick = { vm.setRuntimeNavigationEnabled(!runtimeMode) }) { Icon(imageVector = if (runtimeMode) Icons.Filled.SwapHoriz else Icons.Filled.Home, contentDescription = if (runtimeMode) stringResource(R.string.nav_status) else stringResource(R.string.nav_home)) } }
    if (selectedKey == "modules") { IconButton(onClick = { secondaryStack.add(if (runtimeMode) MiuixScreen.RuntimeRepoSettings else MiuixScreen.RepoSettings) }) { Icon(Icons.Filled.Dns, contentDescription = stringResource(R.string.module_repo_manage_central)) } }
}) },
                bottomBar = {
                    if (!floatingNavEnabled) {
                        NavigationBar { tabs.forEachIndexed { i, tab -> NavigationBarItem(selected = pagerState.currentPage == i, onClick = { scope.launch { pagerState.animateScrollToPage(i) } }, icon = tab.icon, label = tab.label) } }
                    }
                },
                content = { pv -> CompositionLocalProvider(LocalMiuixScrollBehavior provides scrollBehavior) {
                    Box(
                        Modifier.fillMaxSize()
                            .then(if (floatingNavEnabled) Modifier.layerBackdrop(glassBackdrop) else Modifier)
                    ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize().padding(
                            top = pv.calculateTopPadding(),
                            bottom = if (floatingNavEnabled) 0.dp else pv.calculateBottomPadding()
                        ),
                        beyondViewportPageCount = 1
                    ) { page ->
                        val pad = PaddingValues(0.dp)
                        when (tabs.getOrNull(page)?.key) {
                            "status" -> MiuixStatusScreen(vm = vm, outerPadding = pad)
                            "build" -> MiuixBuildScreen(vm = vm, outerPadding = pad, onOpenPlanLib = { secondaryStack.add(MiuixScreen.PlanLib) }, onOpenQueue = { secondaryStack.add(MiuixScreen.BuildQueue) })
                            "modules" -> MiuixModuleRepositoryScreen(vm = vm, outerPadding = pad)
                            "flash" -> MiuixFlashScreen(vm = vm, outerPadding = pad, onOpenFlashDetail = { runId -> secondaryStack.add(MiuixScreen.FlashDetail(runId)) }, onOpenPrebuiltDetail = { releaseId -> secondaryStack.add(MiuixScreen.PrebuiltDetail(releaseId)) })
                            "runtime_home" -> MiuixRuntimeHomeScreen(vm = vm, outerPadding = pad)
                            "installed_modules" -> MiuixInstalledModulesScreen(vm = vm, outerPadding = pad)
                            "root_auth" -> MiuixRootAuthScreen(vm = vm, outerPadding = pad)
                            "settings" -> MiuixSettingsScreen(vm = vm, outerPadding = pad, onOpenTheme = { secondaryStack.add(MiuixScreen.ThemeSettings) }, onOpenAbout = { secondaryStack.add(MiuixScreen.AboutSettings) }, onOpenLicenses = { secondaryStack.add(MiuixScreen.OpenSourceLicenses) })
                        }
                    }
                    } // end Box wrapping HorizontalPager
                }}
            )
            // Glass navigation bar overlay
            if (floatingNavEnabled) {
                Box(
                    Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                        .navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    FloatingBottomBar(
                        modifier = Modifier.fillMaxWidth(),
                        selectedIndex = { mainPagerState?.selectedPage ?: pagerState.currentPage },
                        onSelected = { index ->
                            mainPagerState?.animateToPage(index)
                                ?: scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        backdrop = glassBackdrop,
                        tabsCount = tabs.size,
                        isBlurEnabled = isBlurEnabled,
                        content = {
                            tabs.forEachIndexed { i, tab ->
                                FloatingBottomBarItem(
                                    onClick = {
                                        mainPagerState?.animateToPage(i)
                                            ?: scope.launch { pagerState.animateScrollToPage(i) }
                                    }
                                ) {
                                    Icon(tab.icon, null, tint = colorScheme.onSurface, modifier = Modifier.size(26.dp))
                                    Text(tab.label, fontSize = 11.sp, color = colorScheme.onSurface)
                                }
                            }
                        }
                    )
                }
            }
            NavDisplay(backStack = secondaryStack, entryProvider = ep, entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                transitionSpec = { slideInHorizontally(tween(400)) { it } togetherWith slideOutHorizontally(tween(400)) { -it / 3 } },
                popTransitionSpec = { slideInHorizontally(tween(400)) { -it / 3 } togetherWith slideOutHorizontally(tween(400)) { it } },
                onBack = { if (secondaryStack.size > 1) secondaryStack.removeAt(secondaryStack.lastIndex) }
            )
            // Snackbar overlay (bottom, above nav bar)
            com.abk.kernel.ui.components.MiuixAbkSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun MiuixRepoManageScreen(vm: MainViewModel, onClose: () -> Unit) {
    val state by vm.uiState.collectAsState()
    var repoUrl by rememberSaveable { mutableStateOf("") }
    val repos = state.buildModuleRepositories
    val refreshingIds = state.refreshingBuildModuleRepositoryIds
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topBarState)

    Scaffold(
        topBar = { TopAppBar(title = stringResource(R.string.module_repo_abk_central_title), largeTitle = stringResource(R.string.module_repo_abk_central_title), scrollBehavior = scrollBehavior, navigationIcon = { IconButton(onClick = onClose) { Icon(MiuixIcons.Back, null) } }) },
        containerColor = colorScheme.surface
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp).nestedScroll(scrollBehavior.nestedScrollConnection), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header card
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.Dns, null, tint = colorScheme.onSurface, modifier = Modifier.size(20.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.module_repo_abk_central_title), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                                Text(stringResource(R.string.module_repo_abk_central_desc), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                            }
                        }
                        TextField(value = repoUrl, onValueChange = { repoUrl = it }, label = stringResource(R.string.module_repo_abk_central_url), modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { val u = repoUrl.trim(); if (u.isNotEmpty()) { vm.addBuildModuleRepository(u); repoUrl = "" } }, enabled = repoUrl.isNotBlank(), colors = ButtonDefaults.buttonColorsPrimary(), modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Add, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.add)) }
                            Button(onClick = { vm.refreshAllBuildModuleRepositories() }, enabled = repos.isNotEmpty(), colors = ButtonDefaults.buttonColors(), modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.refresh_all)) }
                        }
                    }
                }
            }

            if (repos.isEmpty()) {
                // Empty state
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.AutoMirrored.Filled.LibraryBooks, null, tint = colorScheme.onSurface, modifier = Modifier.size(20.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.module_repo_abk_empty_title), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                                    Text(stringResource(R.string.module_repo_abk_empty_desc), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                                }
                            }
                            Text(stringResource(R.string.module_repo_delete_keep_modules), fontSize = 12.sp, color = colorScheme.onSurfaceVariantActions)
                        }
                    }
                }
            } else {
                items(repos, key = { it.id }) { repo ->
                    val refreshing = repo.id in refreshingIds
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Filled.Dns, null, tint = colorScheme.onSurface, modifier = Modifier.size(20.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(repo.name.ifBlank { repo.url }, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                                    Text(repo.url, fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            // Chips
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ModTagChip(stringResource(R.string.module_repo_module_count, repo.modules.size))
                                if (repo.skippedCount > 0) ModTagChip(stringResource(R.string.module_repo_skipped_count, repo.skippedCount))
                            }
                            repo.error?.let { Text(it, fontSize = 12.sp, color = colorScheme.error) }
                            Text(repo.indexJsonUrl.ifBlank { repo.url }, fontSize = 11.sp, color = colorScheme.onSurfaceVariantActions, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { vm.refreshBuildModuleRepository(repo.id) }, enabled = !refreshing, colors = ButtonDefaults.buttonColors(), modifier = Modifier.weight(1f)) {
                                    if (refreshing) {
                                        CircularProgressIndicator(size = 20.dp, strokeWidth = 4.dp)
                                    } else {
                                        Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(17.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.retry))
                                    }
                                }
                                Button(onClick = { vm.deleteBuildModuleRepository(repo.id) }, colors = ButtonDefaults.buttonColors(
                                    colorScheme.error), modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Delete, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.delete)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── 普通模块仓库管理页面 ──

@Composable
private fun MiuixRuntimeRepoManageScreen(vm: MainViewModel, onClose: () -> Unit) {
    val state by vm.uiState.collectAsState()
    var repoUrl by rememberSaveable { mutableStateOf("") }
    val repos = state.runtimeModuleRepositories
    val refreshingIds = state.refreshingRuntimeModuleRepositoryIds
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topBarState)

    Scaffold(
        topBar = { TopAppBar(title = stringResource(R.string.runtime_repo_standard_central_title), largeTitle = stringResource(R.string.runtime_repo_standard_central_title), scrollBehavior = scrollBehavior, navigationIcon = { IconButton(onClick = onClose) { Icon(MiuixIcons.Back, null) } }) },
        containerColor = colorScheme.surface
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp).nestedScroll(scrollBehavior.nestedScrollConnection), contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.Dns, null, tint = colorScheme.onSurface, modifier = Modifier.size(20.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.runtime_repo_standard_central_title), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                                Text(stringResource(R.string.runtime_repo_standard_central_desc), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                            }
                        }
                        TextField(value = repoUrl, onValueChange = { repoUrl = it }, label = stringResource(R.string.runtime_repo_standard_url), modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { val u = repoUrl.trim(); if (u.isNotEmpty()) { vm.addRuntimeModuleRepository(u); repoUrl = "" } }, enabled = repoUrl.isNotBlank(), colors = ButtonDefaults.buttonColorsPrimary(), modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Add, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.add)) }
                            Button(onClick = { vm.refreshAllRuntimeModuleRepositories() }, enabled = repos.isNotEmpty(), colors = ButtonDefaults.buttonColors(), modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.refresh_all)) }
                        }
                    }
                }
            }

            if (repos.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.AutoMirrored.Filled.LibraryBooks, null, tint = colorScheme.onSurface, modifier = Modifier.size(20.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.runtime_repo_standard_empty_title), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                                    Text(stringResource(R.string.runtime_repo_standard_empty_desc), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                                }
                            }
                        }
                    }
                }
            } else {
                items(repos, key = { it.id }) { repo ->
                    val refreshing = repo.id in refreshingIds
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Filled.Dns, null, tint = colorScheme.onSurface, modifier = Modifier.size(20.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(repo.name.ifBlank { repo.url }, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                                    Text(repo.url, fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ModTagChip(stringResource(R.string.module_repo_module_count, repo.modules.size))
                                if (repo.skippedCount > 0) ModTagChip(stringResource(R.string.module_repo_skipped_count, repo.skippedCount))
                            }
                            repo.error?.let { Text(it, fontSize = 12.sp, color = colorScheme.error) }
                            Text(repo.indexJsonUrl.ifBlank { repo.url }, fontSize = 11.sp, color = colorScheme.onSurfaceVariantActions, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { vm.refreshRuntimeModuleRepository(repo.id) }, enabled = !refreshing, colors = ButtonDefaults.buttonColors(), modifier = Modifier.weight(1f)) {
                                    if (refreshing) CircularProgressIndicator(size = 20.dp, strokeWidth = 4.dp) else Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.retry))
                                }
                                Button(onClick = { vm.deleteRuntimeModuleRepository(repo.id) }, colors = ButtonDefaults.buttonColors(
                                    colorScheme.error), modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Delete, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.delete)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModTagChip(label: String) {
    Box(Modifier.background(colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(label, fontSize = 11.sp, color = colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
