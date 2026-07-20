package com.abk.kernel.miuix

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.NavigationBackHandler
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.NavDisplayTransitionEffects
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationEventState
import androidx.navigationevent.compose.rememberNavigationEventState
import com.abk.kernel.AbkTab
import com.abk.kernel.R
import com.abk.kernel.displayLabel
import com.abk.kernel.miuix.animation.predictiveback.MiuixDefaultPredictiveBackHandler
import com.abk.kernel.miuix.animation.predictiveback.NonePredictiveBackHandler
import com.abk.kernel.miuix.animation.predictiveback.invokePopTransitionSpec
import com.abk.kernel.miuix.animation.predictiveback.invokePredictivePopTransitionSpec
import com.abk.kernel.miuix.animation.predictiveback.invokeTransitionSpec
import com.abk.kernel.miuix.component.AbkMiuixSnackbarHost
import com.abk.kernel.miuix.component.FloatingTabItem
import com.abk.kernel.miuix.component.MiuixFloatingBottomBar
import com.abk.kernel.miuix.component.showAbkMiuixSnackbar
import com.abk.kernel.miuix.ui.screens.AboutScreenMiuix
import com.abk.kernel.miuix.ui.screens.AppProfileTemplatesScreenMiuix
import com.abk.kernel.miuix.ui.screens.BuildPlanLibraryScreenMiuix
import com.abk.kernel.miuix.ui.screens.BuildQueueScreenMiuix
import com.abk.kernel.miuix.ui.screens.BuildModuleRepoSettingsScreenMiuix
import com.abk.kernel.miuix.ui.screens.ExtensionManagerScreenMiuix
import com.abk.kernel.miuix.ui.screens.ManagerPatchScreenMiuix
import com.abk.kernel.miuix.ui.screens.ManagerToolsScreenMiuix
import com.abk.kernel.miuix.ui.screens.SusfsControlScreenMiuix
import com.abk.kernel.miuix.ui.screens.OpenSourceLicensesScreenMiuix
import com.abk.kernel.miuix.ui.screens.RuntimeModuleRepoSettingsScreenMiuix
import com.abk.kernel.miuix.ui.screens.SettingsScreenMiuix
import com.abk.kernel.miuix.ui.screens.SuperUserProfileScreenMiuix
import com.abk.kernel.miuix.ui.screens.ThemeSettingsScreenMiuix
import com.abk.kernel.miuix.ui.screens.flash.FlashPrebuiltDetailScreenMiuix
import com.abk.kernel.miuix.ui.screens.flash.FlashTerminalLogScreenMiuix
import com.abk.kernel.miuix.ui.screens.flash.FlashWorkflowDetailScreenMiuix
import com.abk.kernel.miuix.ui.screens.runtime.ModuleActionTerminalScreenMiuix
import com.abk.kernel.miuix.ui.screens.runtime.ModuleInstallLogScreenMiuix
import com.abk.kernel.miuix.util.BlurredBar
import com.abk.kernel.miuix.util.rememberBlurBackdrop
import com.abk.kernel.miuix.viewmodel.MiuixSettingsViewModel
import com.abk.kernel.ui.navigation3.LocalNavigator
import com.abk.kernel.ui.navigation3.Navigator
import com.abk.kernel.ui.navigation3.Route
import com.abk.kernel.ui.theme.appPageBackgroundColor
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.utils.findActivity
import com.abk.kernel.viewmodel.MainViewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail as MiuixNavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem as MiuixNavigationRailItem
import top.yukonga.miuix.kmp.basic.NavigationRailDisplayMode
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHostState as MiuixSnackbarHostState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils

private const val MIUIX_NAV_TRANSITION_DURATION_MS = 500
private const val MIUIX_PARENT_SCENE_EXIT_FRACTION = 0.25f

private val MiuixNavTransitionEasing = Easing { fraction ->
    val response = 0.8f
    val damping = 0.95f
    val omega = 2.0 * PI / response
    val k = omega * omega
    val c = damping * 4.0 * PI / response
    val w = (sqrt(4.0 * k - c * c) / 2.0).toFloat()
    val r = (-c / 2.0).toFloat()
    val c2 = r / w
    val t = fraction.toDouble()
    val decay = exp(r * t)
    (decay * (-cos(w * t) + c2 * sin(w * t)) + 1.0).toFloat()
}

/**
 * Bridge composable for miuix UI mode.
 * Called from MainActivity.kt when [state.uiStyle] == "miuix".
 */
@Composable
fun AbkMiuixMainContent(
    vm: MainViewModel,
    miuixVm: MiuixSettingsViewModel,
    pendingModuleInstallUri: String?,
    onModuleInstallUriConsumed: () -> Unit,
    openColorAppearanceRequest: Int = 0,
    onColorAppearanceRequestConsumed: () -> Unit = {},
    onUiStyleChangeFromAppearance: (String) -> Unit = { vm.setUiStyle(it) },
) {
    AbkMiuixMainScaffold(
        vm = vm,
        miuixVm = miuixVm,
        pendingModuleInstallUri = pendingModuleInstallUri,
        onModuleInstallUriConsumed = onModuleInstallUriConsumed,
        openColorAppearanceRequest = openColorAppearanceRequest,
        onColorAppearanceRequestConsumed = onColorAppearanceRequestConsumed,
        onUiStyleChangeFromAppearance = onUiStyleChangeFromAppearance,
    )
}

// ---------------------------------------------------------------------------
// Private scaffold — contains ALL miuix UI code extracted from MainActivity.kt
// ---------------------------------------------------------------------------

@Composable
private fun AbkMiuixMainScaffold(
    vm: MainViewModel,
    miuixVm: MiuixSettingsViewModel,
    pendingModuleInstallUri: String? = null,
    onModuleInstallUriConsumed: () -> Unit = {},
    openColorAppearanceRequest: Int = 0,
    onColorAppearanceRequestConsumed: () -> Unit = {},
    onUiStyleChangeFromAppearance: (String) -> Unit = { vm.setUiStyle(it) },
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val restoreColorAppearanceOnEntry = remember { openColorAppearanceRequest != 0 }
    val navigator = rememberSaveable(saver = Navigator.Saver) {
        Navigator(Route.Main).apply {
            if (restoreColorAppearanceOnEntry) {
                push(Route.ThemeSettings)
            }
        }
    }
    val navIsOnSubPage = navigator.backStackSize() > 1

    LaunchedEffect(Unit) {
        vm.markMainUiEntered()
    }

    var selectedTab by rememberSaveable {
        mutableStateOf(
            if (restoreColorAppearanceOnEntry) AbkTab.Settings else AbkTab.Status
        )
    }
    var flashDetailPageVisible by rememberSaveable { mutableStateOf(false) }
    var settingsChildPageVisible by rememberSaveable { mutableStateOf(false) }
    var buildPlanPageVisible by rememberSaveable { mutableStateOf(false) }
    var moduleRepositoryPageVisible by rememberSaveable { mutableStateOf(false) }
    var rootAuthDetailPageVisible by rememberSaveable { mutableStateOf(false) }
    var managerPatchPageVisible by rememberSaveable { mutableStateOf(false) }
    var lastBackAt by remember { mutableStateOf(0L) }
    val runtimeNativeManagerActive = state.hasNativeManagerPermission
    val visibleTabs = remember(state.runtimeNavigationEnabled, state.rootGranted, runtimeNativeManagerActive) {
        if (state.runtimeNavigationEnabled) {
            buildList {
                add(AbkTab.Status)
                if (state.rootGranted) add(AbkTab.InstalledModules)
                add(AbkTab.Modules)
                if (runtimeNativeManagerActive) add(AbkTab.RootAuth)
                add(AbkTab.Settings)
            }
        } else {
            listOf(AbkTab.Status, AbkTab.Build, AbkTab.Modules, AbkTab.Flash, AbkTab.Settings)
        }
    }
    val activeTab = if (selectedTab in visibleTabs) selectedTab else visibleTabs.first()
    LaunchedEffect(openColorAppearanceRequest) {
        if (openColorAppearanceRequest != 0) {
            selectedTab = AbkTab.Settings
            if (navigator.current() != Route.ThemeSettings) {
                navigator.replaceAll(listOf(Route.Main, Route.ThemeSettings))
            }
            onColorAppearanceRequestConsumed()
        }
    }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isTabletLayout = configuration.smallestScreenWidthDp >= 600
    val hasRail = isTabletLayout && !state.miuixFloatingBottomBarEnabled
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val contentPadding = PaddingValues(
        bottom = with(density) { bottomBarHeightPx.toDp() },
    )
    val popBack = { navigator.pop() }
    val childPageVisible = when (activeTab) {
        AbkTab.Build -> navIsOnSubPage || buildPlanPageVisible
        AbkTab.Modules -> navIsOnSubPage
        AbkTab.Flash -> navIsOnSubPage || flashDetailPageVisible
        AbkTab.InstalledModules -> navIsOnSubPage
        AbkTab.Settings -> navIsOnSubPage || settingsChildPageVisible
        AbkTab.RootAuth -> navIsOnSubPage || rootAuthDetailPageVisible
        AbkTab.RuntimeHome -> navIsOnSubPage
        AbkTab.Status -> navIsOnSubPage
        else -> false
    }
    // Mutable state captured by closures; reassigned inside NavDisplay setup so any
    // downstream composable (e.g., bottom bar graphicsLayer) can read the latest
    // gesture state and recompose when it changes.
    var gestureState: NavigationEventState<SceneInfo<NavKey>>? by remember {
        mutableStateOf(null)
    }
    val miuixSnackbarHostState = remember { MiuixSnackbarHostState() }

    // Snackbar dispatcher — handles snackbar messages in miuix mode only
    LaunchedEffect(state.snackbarMessage, state.snackbarLongDuration, state.error) {
        when (val snackbar = state.snackbarMessage) {
            null -> {
                val error = state.error ?: return@LaunchedEffect
                miuixSnackbarHostState.showAbkMiuixSnackbar(message = error, longDuration = true)
                vm.clearError()
            }
            else -> {
                miuixSnackbarHostState.showAbkMiuixSnackbar(
                    message = snackbar,
                    longDuration = state.snackbarLongDuration,
                )
                vm.clearSnackbar()
                if (state.error != null) vm.clearError()
            }
        }
    }

    LaunchedEffect(pendingModuleInstallUri) {
        if (!pendingModuleInstallUri.isNullOrBlank()) {
            if (!state.runtimeNavigationEnabled) vm.setRuntimeNavigationEnabled(true)
            selectedTab = AbkTab.InstalledModules
        }
    }

    LaunchedEffect(activeTab) {
        when (activeTab) {
            AbkTab.Build -> {
                moduleRepositoryPageVisible = false
                flashDetailPageVisible = false
                settingsChildPageVisible = false
                rootAuthDetailPageVisible = false
                managerPatchPageVisible = false
            }
            AbkTab.Flash -> {
                buildPlanPageVisible = false
                moduleRepositoryPageVisible = false
                settingsChildPageVisible = false
                rootAuthDetailPageVisible = false
                managerPatchPageVisible = false
                flashDetailPageVisible = false
            }
            AbkTab.Modules -> {
                buildPlanPageVisible = false
                flashDetailPageVisible = false
                settingsChildPageVisible = false
                rootAuthDetailPageVisible = false
                managerPatchPageVisible = false
            }
            AbkTab.Settings -> {
                buildPlanPageVisible = false
                moduleRepositoryPageVisible = false
                flashDetailPageVisible = false
                rootAuthDetailPageVisible = false
                managerPatchPageVisible = false
            }
            AbkTab.RootAuth -> {
                buildPlanPageVisible = false
                moduleRepositoryPageVisible = false
                flashDetailPageVisible = false
                settingsChildPageVisible = false
                managerPatchPageVisible = false
            }
            AbkTab.RuntimeHome -> {
                buildPlanPageVisible = false
                moduleRepositoryPageVisible = false
                flashDetailPageVisible = false
                settingsChildPageVisible = false
                rootAuthDetailPageVisible = false
            }
            else -> {
                buildPlanPageVisible = false
                moduleRepositoryPageVisible = false
                flashDetailPageVisible = false
                settingsChildPageVisible = false
                rootAuthDetailPageVisible = false
                managerPatchPageVisible = false
            }
        }
    }

    LaunchedEffect(visibleTabs, selectedTab, state.runtimeNavigationEnabled) {
        if (selectedTab !in visibleTabs) {
            selectedTab = AbkTab.Status
        }
    }

    val pressAgainExitLabel = stringResource(R.string.press_again_exit)

    fun handleTopLevelBack() {
        val now = System.currentTimeMillis()
        if (now - lastBackAt <= 2_000L) {
            context.findActivity()?.finish()
        } else {
            lastBackAt = now
            vm.showSnackbar(pressAgainExitLabel)
        }
    }

    if (!childPageVisible) {
        BackHandler(onBack = ::handleTopLevelBack)
    }

    val surfaceColor = MiuixTheme.colorScheme.surface
    val floatingGlassBackdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    val blurEnabledForGlass = state.miuixFloatingBottomBarEnabled && state.miuixLiquidGlassEnabled
    val blurBackdrop = rememberBlurBackdrop(state.miuixBlurEnabled, surfaceColor)

    // Mirrors NavDisplay's parent scene slide: 0f = root position, -1f = -width / 4.
    val barSlideOffset = remember { Animatable(0f) }
    val lastGestureProgress = remember { mutableStateOf(0f) }
    val predictiveBackProgress by remember {
        derivedStateOf {
            val inProgress = gestureState?.transitionState
                as? NavigationEventTransitionState.InProgress
            if (inProgress?.direction == NavigationEventTransitionState.TRANSITIONING_BACK) {
                inProgress.latestEvent.progress
            } else 0f
        }
    }
    val childPageSceneSettled =
        childPageVisible && predictiveBackProgress <= 0f && barSlideOffset.value <= -0.999f
    val tabIcon: @Composable (AbkTab) -> ImageVector = { tab ->
        when (tab) {
            AbkTab.Status -> if (state.runtimeNavigationEnabled) Icons.Default.Memory else Icons.Default.Home
            AbkTab.Build -> Icons.Default.RocketLaunch
            AbkTab.Modules -> Icons.Default.LibraryBooks
            AbkTab.Flash -> if (state.rootGranted) Icons.Default.FlashOn else Icons.Default.FolderOpen
            AbkTab.RuntimeHome -> Icons.Default.Memory
            AbkTab.InstalledModules -> Icons.Default.Extension
            AbkTab.RootAuth -> Icons.Default.AdminPanelSettings
            AbkTab.Settings -> Icons.Default.Settings
        }
    }
    val tabLabel: @Composable (AbkTab) -> String = { tab ->
        if (tab == AbkTab.Status && state.runtimeNavigationEnabled) {
            AbkTab.RuntimeHome.displayLabel(state.rootGranted)
        } else {
            tab.displayLabel(state.rootGranted)
        }
    }
    Scaffold(
        popupHost = {
            MiuixPopupUtils.MiuixPopupHost()
        }
    ) { scaffoldPadding ->
        Box(Modifier.fillMaxSize()) {
            if (!hasRail) {
                LaunchedEffect(childPageVisible, predictiveBackProgress) {
                    if (predictiveBackProgress > 0f) {
                        // Only return the bar during predictive back when popping from the first
                        // sub-page level. Deeper pages keep it parked with the parent scene.
                        if (navigator.backStackSize() <= 2) {
                            barSlideOffset.snapTo(-(1f - predictiveBackProgress))
                            lastGestureProgress.value = predictiveBackProgress
                        } else {
                            barSlideOffset.snapTo(-1f)
                        }
                    } else {
                        val target = if (childPageVisible) -1f else 0f
                        val fromGesture = lastGestureProgress.value > 0f
                        barSlideOffset.animateTo(
                            targetValue = target,
                            animationSpec = tween(
                                durationMillis = MIUIX_NAV_TRANSITION_DURATION_MS,
                                easing = MiuixNavTransitionEasing,
                            ),
                        )
                        if (fromGesture) lastGestureProgress.value = 0f
                    }
                }
            }

            if (hasRail) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(appPageBackgroundColor(uiSurfaceColor(MaterialTheme.colorScheme.surface))),
        ) {
            CompositionLocalProvider(LocalNavigator provides navigator) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                when {
                                    blurEnabledForGlass -> Modifier.layerBackdrop(floatingGlassBackdrop)
                                    blurBackdrop != null -> Modifier.layerBackdrop(blurBackdrop)
                                    else -> Modifier
                                },
                            ),
                    ) {
                        val predictiveBackHandler = remember(state.miuixPredictiveBackEnabled) {
                            if (state.miuixPredictiveBackEnabled) MiuixDefaultPredictiveBackHandler()
                            else NonePredictiveBackHandler(popBack)
                        }
                        val navigationScope = rememberCoroutineScope()
                        val sceneBackgroundColor = MiuixTheme.colorScheme.surface

                        val entries = rememberDecoratedNavEntries(
                                backStack = navigator.backStack,
                                entryDecorators = listOf(
                                    rememberSaveableStateHolderNavEntryDecorator(),
                                    NavEntryDecorator<NavKey>(
                                        onPop = { key ->
                                            predictiveBackHandler.onPagePop(key, navigationScope)
                                        },
                                        decorate = { entry ->
                                            with(predictiveBackHandler) {
                                                Box(
                                                    modifier = Modifier
                                                        .predictiveBackAnnotation(
                                                            gestureState?.transitionState,
                                                            entry.contentKey,
                                                            navigator.current(),
                                                        )
                                                        .background(sceneBackgroundColor),
                                                ) {
                                                    entry.Content()
                                                }
                                            }
                                        },
                                    ),
                                ),
                                entryProvider = entryProvider {
                                    entry<Route.Main> {
                                        val pagerState = rememberPagerState(
                                            initialPage = visibleTabs.indexOf(activeTab).coerceAtLeast(0),
                                            pageCount = { visibleTabs.size },
                                        )
                                        var navigatingToTarget by remember { mutableStateOf(false) }

                                        LaunchedEffect(pagerState.currentPage) {
                                            if (!navigatingToTarget &&
                                                pagerState.currentPage in visibleTabs.indices
                                            ) {
                                                selectedTab = visibleTabs[pagerState.currentPage]
                                            }
                                        }

                                        LaunchedEffect(activeTab) {
                                            val index = visibleTabs.indexOf(activeTab)
                                            if (index >= 0 && pagerState.currentPage != index) {
                                                navigatingToTarget = true
                                                try {
                                                    pagerState.animateScrollToPage(index)
                                                } finally {
                                                    navigatingToTarget = false
                                                }
                                            }
                                        }

                                        Row(modifier = Modifier.fillMaxSize()) {
                                            Box(
                                                modifier = Modifier
                                                    .width(92.dp)
                                                    .fillMaxHeight(),
                                            ) {
                                                key(visibleTabs) {
                                                    BlurredBar(blurBackdrop, surfaceColor, blurActive = false) {
                                                        MiuixNavigationRail(
                                                            modifier = Modifier
                                                                .fillMaxHeight()
                                                                .fillMaxWidth(),
                                                            color = MiuixTheme.colorScheme.surface,
                                                            showDivider = false,
                                                            defaultWindowInsetsPadding = false,
                                                            minWidth = 92.dp,
                                                            mode = NavigationRailDisplayMode.IconAndText,
                                                        ) {
                                                            visibleTabs.forEach { tab ->
                                                                MiuixNavigationRailItem(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    selected = activeTab == tab,
                                                                    onClick = { if (!childPageVisible && tab in visibleTabs) selectedTab = tab },
                                                                    enabled = !childPageVisible,
                                                                    icon = tabIcon(tab),
                                                                    label = tabLabel(tab),
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight(),
                                            ) {
                                                HorizontalPager(
                                                    state = pagerState,
                                                    modifier = Modifier.fillMaxSize(),
                                                    beyondViewportPageCount = visibleTabs.size,
                                                ) { page ->
                                                    when (visibleTabs[page]) {
                                                    AbkTab.Status -> key("homeMode") {
                                                        HomeModeContent(
                                                            runtimeNavigationEnabled = state.runtimeNavigationEnabled,
                                                            vm = vm,
                                                            outerPadding = contentPadding,
                                                            onToggleRuntimeNavigation = { vm.setRuntimeNavigationEnabled(true) },
                                                            onSwitchToClassic = { vm.setRuntimeNavigationEnabled(false) },
                                                            navigator = navigator,
                                                        )
                                                    }
                                                        AbkTab.Build -> com.abk.kernel.miuix.ui.screens.BuildScreenMiuix(
                                                            vm = vm,
                                                            outerPadding = contentPadding,
                                                            onPlanPageVisibleChange = { buildPlanPageVisible = it },
                                                            onNavigateToStatus = { selectedTab = AbkTab.Status },
                                                        )
                                                        AbkTab.Modules -> com.abk.kernel.miuix.ui.screens.ModuleRepositoryScreenMiuix(
                                                            vm = vm,
                                                            mode = if (state.runtimeNavigationEnabled) {
                                                                com.abk.kernel.ui.screens.ModuleRepositoryMode.RUNTIME_STANDARD
                                                            } else {
                                                                com.abk.kernel.ui.screens.ModuleRepositoryMode.BUILD_ABK
                                                            },
                                                            outerPadding = contentPadding,
                                                            onRepositoryPageVisibleChange = { moduleRepositoryPageVisible = it },
                                                        )
                                                        AbkTab.Flash -> com.abk.kernel.miuix.ui.screens.FlashScreenMiuix(
                                                            vm = vm,
                                                            outerPadding = contentPadding,
                                                            onDetailPageVisibleChange = { flashDetailPageVisible = it },
                                                        )
                                                        AbkTab.RuntimeHome -> key("homeMode") {
                                                            HomeModeContent(
                                                                runtimeNavigationEnabled = state.runtimeNavigationEnabled,
                                                                vm = vm,
                                                                outerPadding = contentPadding,
                                                                onToggleRuntimeNavigation = { vm.setRuntimeNavigationEnabled(true) },
                                                                onSwitchToClassic = { vm.setRuntimeNavigationEnabled(false) },
                                                                navigator = navigator,
                                                            )
                                                        }
                                                        AbkTab.InstalledModules -> com.abk.kernel.miuix.ui.screens.InstalledModulesScreenMiuix(
                                                            vm = vm,
                                                            outerPadding = contentPadding,
                                                            pendingModuleInstallUri = pendingModuleInstallUri,
                                                            onPendingModuleInstallUriConsumed = onModuleInstallUriConsumed,
                                                        )
                                                        AbkTab.RootAuth -> com.abk.kernel.miuix.ui.screens.RootAuthorizationScreenMiuix(
                                                            vm = vm,
                                                            outerPadding = contentPadding,
                                                        )
                                                        AbkTab.Settings -> com.abk.kernel.miuix.ui.screens.SettingsScreenMiuix(
                                                            vm = vm,
                                                            outerPadding = contentPadding,
                                                            onOpenInstalledModules = {
                                                                if (!state.runtimeNavigationEnabled) vm.setRuntimeNavigationEnabled(true)
                                                                selectedTab = if (state.rootGranted) {
                                                                    AbkTab.InstalledModules
                                                                } else {
                                                                    AbkTab.Status
                                                                }
                                                            },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    entry<Route.ThemeSettings> {
                                        ThemeSettingsScreenMiuix(
                                            vm = vm,
                                            miuixVm = miuixVm,
                                            onBack = popBack,
                                            onUiStyleChange = onUiStyleChangeFromAppearance,
                                        )
                                    }
                                    entry<Route.AppProfileTemplates> {
                                        AppProfileTemplatesScreenMiuix(vm = vm)
                                    }
                                    entry<Route.ManagerTools> {
                                        ManagerToolsScreenMiuix(vm = vm)
                                    }
                                    entry<Route.About> {
                                        AboutScreenMiuix(vm = vm)
                                    }
                                    entry<Route.OpenSourceLicenses> {
                                        OpenSourceLicensesScreenMiuix(vm = vm)
                                    }
                                    entry<Route.ExtensionManager> {
                                        ExtensionManagerScreenMiuix(
                                            onBack = popBack,
                                            onFeedback = { message, longDuration ->
                                                vm.showSnackbar(message, longDuration)
                                            },
                                        )
                                    }
                                    entry<Route.BuildPlanLibrary> {
                                        BuildPlanLibraryScreenMiuix(vm = vm)
                                    }
                                    entry<Route.BuildQueue> {
                                        BuildQueueScreenMiuix(vm = vm)
                                    }
                                    entry<Route.BuildModuleRepoSettings> {
                                        BuildModuleRepoSettingsScreenMiuix(vm = vm)
                                    }
                                    entry<Route.RuntimeModuleRepoSettings> {
                                        RuntimeModuleRepoSettingsScreenMiuix(vm = vm)
                                    }
                                    entry<Route.FlashWorkflowDetail> { route ->
                                        FlashWorkflowDetailScreenMiuix(
                                            vm = vm,
                                            route = route,
                                            outerPadding = contentPadding,
                                            onBack = popBack,
                                        )
                                    }
                                    entry<Route.FlashPrebuiltDetail> { route ->
                                        FlashPrebuiltDetailScreenMiuix(
                                            vm = vm,
                                            route = route,
                                            outerPadding = contentPadding,
                                            onBack = popBack,
                                        )
                                    }
                                    entry<Route.FlashTerminalLog> { route ->
                                        FlashTerminalLogScreenMiuix(
                                            params = route.params,
                                            onBack = popBack,
                                        )
                                    }
                                    entry<Route.SuperUserProfile> { route ->
                                        SuperUserProfileScreenMiuix(
                                            vm = vm,
                                            uid = route.uid,
                                            onBack = popBack,
                                        )
                                    }
                                    entry<Route.ManagerPatch> {
                                        ManagerPatchScreenMiuix(
                                            rootGranted = state.rootGranted,
                                            hasNativeManagerPermission = state.hasNativeManagerPermission,
                                            runtimeVariant = state.abkRuntimeStatus?.manager?.variant.orEmpty(),
                                            backgroundUri = state.customBackgroundUri,
                                            backgroundImageEnabled = state.backgroundImageEnabled,
                                            onBack = popBack,
                                            onFeedback = { message, longDuration ->
                                                vm.showSnackbar(message, longDuration)
                                            },
                                        )
                                    }
                                    entry<Route.SusfsControl> {
                                        SusfsControlScreenMiuix(
                                            state = state,
                                            showRefreshLoading = state.susfsLoading,
                                            onApply = { vm.applySusfsConfig(it) },
                                            onReset = { vm.resetSusfsConfig() },
                                            onRefresh = { vm.refreshSusfsState(force = true) },
                                            onBack = popBack,
                                        )
                                    }
                                    entry<Route.ModuleInstallLog> { route ->
                                        ModuleInstallLogScreenMiuix(
                                            params = route.params,
                                            vm = vm,
                                            onBack = popBack,
                                        )
                                    }
                                    entry<Route.ModuleActionTerminal> { route ->
                                        ModuleActionTerminalScreenMiuix(
                                            params = route.params,
                                            vm = vm,
                                            onBack = popBack,
                                        )
                                    }
                                },
                            )

                        val sceneState = rememberSceneState(
                                entries = entries,
                                sceneStrategies = listOf(SinglePaneSceneStrategy()),
                                onBack = popBack,
                            )
                        gestureState = rememberNavigationEventState(
                                currentInfo = SceneInfo(sceneState.currentScene),
                                backInfo = sceneState.previousScenes.map { SceneInfo(it) },
                            )

                        NavigationBackHandler(
                                sceneState = sceneState,
                                state = gestureState!!,
                                onBack = popBack,
                            )

                        NavDisplay(
                                sceneState = sceneState,
                                navigationEventState = gestureState!!,
                                transitionSpec = {
                                    val scope: AnimatedContentTransitionScope<Scene<NavKey>> = this
                                    predictiveBackHandler.invokeTransitionSpec(scope)
                                },
                                popTransitionSpec = {
                                    val scope: AnimatedContentTransitionScope<Scene<NavKey>> = this
                                    predictiveBackHandler.invokePopTransitionSpec(scope)
                                },
                                predictivePopTransitionSpec = { swipeEdge ->
                                    val scope: AnimatedContentTransitionScope<Scene<NavKey>> = this
                                    predictiveBackHandler.invokePredictivePopTransitionSpec(scope, swipeEdge)
                                },
                                transitionEffects = NavDisplayTransitionEffects.Default,
                        )
                    }
                }
            }

            val snackbarModifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(4f)
            AbkMiuixSnackbarHost(
                hostState = miuixSnackbarHostState,
                modifier = snackbarModifier,
            )
        }
    } else {
        // Phone layout — original Box layout, UNCHANGED
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(appPageBackgroundColor(uiSurfaceColor(MaterialTheme.colorScheme.surface))),
        ) {
            // Phone bottom bar (existing code, unchanged logic)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .zIndex(if (childPageSceneSettled) 0f else 2f)
                    .drawWithContent {
                        val visibleWidth = size.width * (1f + barSlideOffset.value.coerceIn(-1f, 0f))
                        clipRect(right = visibleWidth) {
                            this@drawWithContent.drawContent()
                        }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            if (!hasRail) {
                                translationX = size.width * MIUIX_PARENT_SCENE_EXIT_FRACTION * barSlideOffset.value
                            }
                        }
                        .onSizeChanged { bottomBarHeightPx = it.height },
                ) {
                    key(visibleTabs) {
                        when {
                            state.miuixFloatingBottomBarEnabled -> {
                                MiuixFloatingBottomBar(
                                    modifier = Modifier
                                        .align(Alignment.Center),
                                    items = visibleTabs.map { tab ->
                                        FloatingTabItem(
                                            label = tabLabel(tab),
                                            icon = tabIcon(tab),
                                            onClick = { if (!childPageVisible && tab in visibleTabs) selectedTab = tab },
                                        )
                                    },
                                    selectedIndex = visibleTabs.indexOf(activeTab).coerceAtLeast(0),
                                    backdrop = floatingGlassBackdrop,
                                    isBlurEnabled = state.miuixLiquidGlassEnabled,
                                    isLiquidGlassEnabled = state.miuixLiquidGlassEnabled,
                                )
                            }
                            else -> {
                                BlurredBar(blurBackdrop, surfaceColor) {
                                    MiuixNavigationBar(
                                        modifier = Modifier.fillMaxWidth().height(80.dp),
                                        color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                                    ) {
                                        visibleTabs.forEach { tab ->
                                            MiuixNavigationBarItem(
                                                modifier = Modifier.weight(1f).height(80.dp),
                                                selected = activeTab == tab,
                                                onClick = { if (!childPageVisible && tab in visibleTabs) selectedTab = tab },
                                                enabled = !childPageVisible,
                                                icon = tabIcon(tab),
                                                label = tabLabel(tab),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Content area with NavDisplay
            CompositionLocalProvider(LocalNavigator provides navigator) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                when {
                                    blurEnabledForGlass -> Modifier.layerBackdrop(floatingGlassBackdrop)
                                    blurBackdrop != null -> Modifier.layerBackdrop(blurBackdrop)
                                    else -> Modifier
                                },
                            ),
                    ) {
                        val predictiveBackHandler = remember(state.miuixPredictiveBackEnabled) {
                            if (state.miuixPredictiveBackEnabled) MiuixDefaultPredictiveBackHandler()
                            else NonePredictiveBackHandler(popBack)
                        }
                        val navigationScope = rememberCoroutineScope()
                        val sceneBackgroundColor = MiuixTheme.colorScheme.surface

                        val entries = rememberDecoratedNavEntries(
                            backStack = navigator.backStack,
                            entryDecorators = listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                NavEntryDecorator<NavKey>(
                                    onPop = { key ->
                                        predictiveBackHandler.onPagePop(key, navigationScope)
                                    },
                                    decorate = { entry ->
                                        with(predictiveBackHandler) {
                                            Box(
                                                modifier = Modifier
                                                    .predictiveBackAnnotation(
                                                        gestureState?.transitionState,
                                                        entry.contentKey,
                                                        navigator.current(),
                                                    )
                                                    .background(sceneBackgroundColor),
                                            ) {
                                                entry.Content()
                                            }
                                        }
                                    },
                                ),
                            ),
                            entryProvider = entryProvider {
                                entry<Route.Main> {
                                    val pagerState = rememberPagerState(
                                        initialPage = visibleTabs.indexOf(activeTab).coerceAtLeast(0),
                                        pageCount = { visibleTabs.size },
                                    )
                                    var navigatingToTarget by remember { mutableStateOf(false) }

                                    LaunchedEffect(pagerState.currentPage) {
                                        if (!navigatingToTarget &&
                                            pagerState.currentPage in visibleTabs.indices
                                        ) {
                                            selectedTab = visibleTabs[pagerState.currentPage]
                                        }
                                    }

                                    LaunchedEffect(activeTab) {
                                        val index = visibleTabs.indexOf(activeTab)
                                        if (index >= 0 && pagerState.currentPage != index) {
                                            navigatingToTarget = true
                                            try {
                                                pagerState.animateScrollToPage(index)
                                            } finally {
                                                navigatingToTarget = false
                                            }
                                        }
                                    }

                                    HorizontalPager(
                                        state = pagerState,
                                        modifier = Modifier.fillMaxSize(),
                                        beyondViewportPageCount = visibleTabs.size,
                                    ) { page ->
                                        when (visibleTabs[page]) {
                                            AbkTab.Status -> key("homeMode") {
                                                HomeModeContent(
                                                    runtimeNavigationEnabled = state.runtimeNavigationEnabled,
                                                    vm = vm,
                                                    outerPadding = contentPadding,
                                                    onToggleRuntimeNavigation = { vm.setRuntimeNavigationEnabled(true) },
                                                    onSwitchToClassic = { vm.setRuntimeNavigationEnabled(false) },
                                                    navigator = navigator,
                                                )
                                            }
                                            AbkTab.Build -> com.abk.kernel.miuix.ui.screens.BuildScreenMiuix(
                                                vm = vm,
                                                outerPadding = contentPadding,
                                                onPlanPageVisibleChange = { buildPlanPageVisible = it },
                                                onNavigateToStatus = { selectedTab = AbkTab.Status },
                                            )
                                            AbkTab.Modules -> com.abk.kernel.miuix.ui.screens.ModuleRepositoryScreenMiuix(
                                                vm = vm,
                                                mode = if (state.runtimeNavigationEnabled) {
                                                    com.abk.kernel.ui.screens.ModuleRepositoryMode.RUNTIME_STANDARD
                                                } else {
                                                    com.abk.kernel.ui.screens.ModuleRepositoryMode.BUILD_ABK
                                                },
                                                outerPadding = contentPadding,
                                                onRepositoryPageVisibleChange = { moduleRepositoryPageVisible = it },
                                            )
                                            AbkTab.Flash -> com.abk.kernel.miuix.ui.screens.FlashScreenMiuix(
                                                vm = vm,
                                                outerPadding = contentPadding,
                                                onDetailPageVisibleChange = { flashDetailPageVisible = it },
                                            )
                                            AbkTab.RuntimeHome -> key("homeMode") {
                                                HomeModeContent(
                                                    runtimeNavigationEnabled = state.runtimeNavigationEnabled,
                                                    vm = vm,
                                                    outerPadding = contentPadding,
                                                    onToggleRuntimeNavigation = { vm.setRuntimeNavigationEnabled(true) },
                                                    onSwitchToClassic = { vm.setRuntimeNavigationEnabled(false) },
                                                    navigator = navigator,
                                                )
                                            }
                                            AbkTab.InstalledModules -> com.abk.kernel.miuix.ui.screens.InstalledModulesScreenMiuix(
                                                vm = vm,
                                                outerPadding = contentPadding,
                                                pendingModuleInstallUri = pendingModuleInstallUri,
                                                onPendingModuleInstallUriConsumed = onModuleInstallUriConsumed,
                                            )
                                            AbkTab.RootAuth -> com.abk.kernel.miuix.ui.screens.RootAuthorizationScreenMiuix(
                                                vm = vm,
                                                outerPadding = contentPadding,
                                            )
                                            AbkTab.Settings -> com.abk.kernel.miuix.ui.screens.SettingsScreenMiuix(
                                                vm = vm,
                                                outerPadding = contentPadding,
                                                onOpenInstalledModules = {
                                                    if (!state.runtimeNavigationEnabled) vm.setRuntimeNavigationEnabled(true)
                                                    selectedTab = if (state.rootGranted) {
                                                        AbkTab.InstalledModules
                                                    } else {
                                                        AbkTab.Status
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                                entry<Route.ThemeSettings> {
                                    ThemeSettingsScreenMiuix(
                                        vm = vm,
                                        miuixVm = miuixVm,
                                        onBack = popBack,
                                        onUiStyleChange = onUiStyleChangeFromAppearance,
                                    )
                                }
                                entry<Route.AppProfileTemplates> {
                                    AppProfileTemplatesScreenMiuix(vm = vm)
                                }
                                entry<Route.ManagerTools> {
                                    ManagerToolsScreenMiuix(vm = vm)
                                }
                                entry<Route.About> {
                                    AboutScreenMiuix(vm = vm)
                                }
                                entry<Route.OpenSourceLicenses> {
                                    OpenSourceLicensesScreenMiuix(vm = vm)
                                }
                                entry<Route.ExtensionManager> {
                                    ExtensionManagerScreenMiuix(
                                        onBack = popBack,
                                        onFeedback = { message, longDuration ->
                                            vm.showSnackbar(message, longDuration)
                                        },
                                    )
                                }
                                entry<Route.BuildPlanLibrary> {
                                    BuildPlanLibraryScreenMiuix(vm = vm)
                                }
                                entry<Route.BuildQueue> {
                                    BuildQueueScreenMiuix(vm = vm)
                                }
                                entry<Route.BuildModuleRepoSettings> {
                                    BuildModuleRepoSettingsScreenMiuix(vm = vm)
                                }
                                entry<Route.RuntimeModuleRepoSettings> {
                                    RuntimeModuleRepoSettingsScreenMiuix(vm = vm)
                                }
                                entry<Route.FlashWorkflowDetail> { route ->
                                    FlashWorkflowDetailScreenMiuix(
                                        vm = vm,
                                        route = route,
                                        outerPadding = contentPadding,
                                        onBack = popBack,
                                    )
                                }
                                entry<Route.FlashPrebuiltDetail> { route ->
                                    FlashPrebuiltDetailScreenMiuix(
                                        vm = vm,
                                        route = route,
                                        outerPadding = contentPadding,
                                        onBack = popBack,
                                    )
                                }
                                entry<Route.FlashTerminalLog> { route ->
                                    FlashTerminalLogScreenMiuix(
                                        params = route.params,
                                        onBack = popBack,
                                    )
                                }
                                entry<Route.SuperUserProfile> { route ->
                                    SuperUserProfileScreenMiuix(
                                        vm = vm,
                                        uid = route.uid,
                                        onBack = popBack,
                                    )
                                }
                                entry<Route.ManagerPatch> {
                                    ManagerPatchScreenMiuix(
                                        rootGranted = state.rootGranted,
                                        hasNativeManagerPermission = state.hasNativeManagerPermission,
                                        runtimeVariant = state.abkRuntimeStatus?.manager?.variant.orEmpty(),
                                        backgroundUri = state.customBackgroundUri,
                                        backgroundImageEnabled = state.backgroundImageEnabled,
                                        onBack = popBack,
                                        onFeedback = { message, longDuration ->
                                            vm.showSnackbar(message, longDuration)
                                        },
                                    )
                                }
                                entry<Route.SusfsControl> {
                                    SusfsControlScreenMiuix(
                                        state = state,
                                        showRefreshLoading = state.susfsLoading,
                                        onApply = { vm.applySusfsConfig(it) },
                                        onReset = { vm.resetSusfsConfig() },
                                        onRefresh = { vm.refreshSusfsState(force = true) },
                                        onBack = popBack,
                                    )
                                }
                                entry<Route.ModuleInstallLog> { route ->
                                    ModuleInstallLogScreenMiuix(
                                        params = route.params,
                                        vm = vm,
                                        onBack = popBack,
                                    )
                                }
                                entry<Route.ModuleActionTerminal> { route ->
                                    ModuleActionTerminalScreenMiuix(
                                        params = route.params,
                                        vm = vm,
                                        onBack = popBack,
                                    )
                                }
                            },
                        )

                        val sceneState = rememberSceneState(
                            entries = entries,
                            sceneStrategies = listOf(SinglePaneSceneStrategy()),
                            onBack = popBack,
                        )
                        gestureState = rememberNavigationEventState(
                            currentInfo = SceneInfo(sceneState.currentScene),
                            backInfo = sceneState.previousScenes.map { SceneInfo(it) },
                        )

                        NavigationBackHandler(
                            sceneState = sceneState,
                            state = gestureState!!,
                            onBack = popBack,
                        )

                        NavDisplay(
                            sceneState = sceneState,
                            navigationEventState = gestureState!!,
                            transitionSpec = {
                                val scope: AnimatedContentTransitionScope<Scene<NavKey>> = this
                                predictiveBackHandler.invokeTransitionSpec(scope)
                            },
                            popTransitionSpec = {
                                val scope: AnimatedContentTransitionScope<Scene<NavKey>> = this
                                predictiveBackHandler.invokePopTransitionSpec(scope)
                            },
                            predictivePopTransitionSpec = { swipeEdge ->
                                val scope: AnimatedContentTransitionScope<Scene<NavKey>> = this
                                predictiveBackHandler.invokePredictivePopTransitionSpec(scope, swipeEdge)
                            },
                            transitionEffects = NavDisplayTransitionEffects.Default,
                        )
                    }
                }
            }

            val snackbarModifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = with(density) { if (childPageVisible) 0.dp else bottomBarHeightPx.toDp() } + 10.dp,
                )
                .zIndex(4f)
            AbkMiuixSnackbarHost(
                hostState = miuixSnackbarHostState,
                modifier = snackbarModifier,
            )
        }  // close non-rail Box
    }  // close else block
        }  // close Box(Modifier.fillMaxSize())
    }  // close Scaffold
}  // close AbkMiuixMainScaffold

// ---------------------------------------------------------------------------
// AnimatedContent wrapper for Status ↔ RuntimeHome transition
// ---------------------------------------------------------------------------

/**
 * Wraps [StatusScreenMiuix] and [RuntimeHomeScreenMiuix] in an [AnimatedContent]
 * that slides horizontally when [runtimeNavigationEnabled] changes.
 *
 * Direction: false→true slides forward (left), true→false slides backward (right).
 */
@Composable
private fun HomeModeContent(
    runtimeNavigationEnabled: Boolean,
    vm: MainViewModel,
    outerPadding: PaddingValues,
    onToggleRuntimeNavigation: () -> Unit,
    onSwitchToClassic: () -> Unit,
    navigator: Navigator,
) {
    AnimatedContent(
        targetState = runtimeNavigationEnabled,
        transitionSpec = {
            val slideSpec = spring<IntOffset>(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
            val fadeSpec = tween<Float>(durationMillis = 140)
            val direction = if (targetState) 1 else -1
            (slideInHorizontally(animationSpec = slideSpec) { width -> direction * width / 5 } + fadeIn(animationSpec = fadeSpec))
                .togetherWith(slideOutHorizontally(animationSpec = slideSpec) { width -> -direction * width / 6 } + fadeOut(animationSpec = fadeSpec))
        },
        label = "homeModeTransition",
    ) { runtimeEnabled ->
        if (runtimeEnabled) {
            com.abk.kernel.miuix.ui.screens.RuntimeHomeScreenMiuix(
                vm = vm,
                outerPadding = outerPadding,
                onSwitchToClassic = onSwitchToClassic,
                navigator = navigator,
            )
        } else {
            com.abk.kernel.miuix.ui.screens.StatusScreenMiuix(
                vm = vm,
                outerPadding = outerPadding,
                runtimeNavigationEnabled = false,
                onToggleRuntimeNavigation = onToggleRuntimeNavigation,
            )
        }
    }
}
