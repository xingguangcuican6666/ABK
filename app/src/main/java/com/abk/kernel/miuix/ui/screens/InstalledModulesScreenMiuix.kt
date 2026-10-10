package com.abk.kernel.miuix.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.R
import com.abk.kernel.data.model.AbkRuntimeModule
import com.abk.kernel.data.model.AbkRuntimeStatus
import com.abk.kernel.miuix.component.SearchBarFake
import com.abk.kernel.miuix.component.SearchBox
import com.abk.kernel.miuix.component.SearchPager
import com.abk.kernel.miuix.component.SearchStatus
import com.abk.kernel.ui.screens.MODULE_INSTALL_MIME_TYPES
import com.abk.kernel.ui.screens.RuntimeModuleDisplayGroup
import com.abk.kernel.ui.screens.canUninstallRuntimeModule
import com.abk.kernel.ui.screens.displayName
import com.abk.kernel.ui.screens.groupRuntimeModulesForDisplay
import com.abk.kernel.ui.screens.hasRuntimeModuleFileAccess
import com.abk.kernel.ui.screens.matchesRuntimeModuleQuery
import com.abk.kernel.ui.screens.normalizedType
import com.abk.kernel.ui.screens.runtimeModuleUriDisplayName
import com.abk.kernel.ui.screens.typeOrder
import com.abk.kernel.ui.navigation3.LocalNavigator
import com.abk.kernel.ui.navigation3.Route
import com.abk.kernel.miuix.ui.screens.runtime.ModuleInstallParams
import com.abk.kernel.miuix.ui.screens.runtime.ModuleActionTerminalParams
import com.abk.kernel.ui.webui.ModuleWebUiActivity
import com.abk.kernel.viewmodel.MainViewModel
import androidx.compose.runtime.CompositionLocalProvider
import com.abk.kernel.ui.blur.LocalBlurState
import com.abk.kernel.ui.blur.blurEffect
import com.abk.kernel.ui.blur.rememberBlurBackdrop
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun InstalledModulesScreenMiuix(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    pendingModuleInstallUri: String? = null,
    onPendingModuleInstallUriConsumed: () -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current
    val navigator = LocalNavigator.current
    var searchStatus by remember { mutableStateOf(SearchStatus("")) }
    var pendingInstallUri by remember { mutableStateOf<Uri?>(null) }
    var showAllFilesAccessPrompt by remember { mutableStateOf(false) }
    var resumeModulePickerAfterPermission by remember { mutableStateOf(false) }
    var uninstallTarget by remember { mutableStateOf<AbkRuntimeModule?>(null) }

    val query = searchStatus.searchText
    val modules = remember(state.abkRuntimeStatus?.modules, query) {
        state.abkRuntimeStatus?.modules.orEmpty()
            .filter { it.matchesRuntimeModuleQuery(query) }
            .sortedWith(
                compareBy<AbkRuntimeModule> { it.typeOrder() }
                    .thenBy { it.displayName().lowercase() }
            )
    }
    val groupedModules = remember(modules) { groupRuntimeModulesForDisplay(modules) }

    val scrollDistance = remember { mutableFloatStateOf(0f) }
    var fabVisible by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    val nestedScrollConnection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val canScrollForward = listState.canScrollForward
                if (!canScrollForward) return Offset.Zero

                scrollDistance.floatValue += available.y

                if (scrollDistance.floatValue <= -50f && fabVisible) {
                    fabVisible = false
                    scrollDistance.floatValue = 0f
                    return Offset(0f, available.y)
                }

                if (scrollDistance.floatValue >= 50f && !fabVisible) {
                    fabVisible = true
                    scrollDistance.floatValue = 0f
                    return Offset(0f, available.y)
                }

                return Offset.Zero
            }
        }
    }

    val offsetHeight by animateDpAsState(
        targetValue = if (fabVisible) 0.dp else 180.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        animationSpec = tween(durationMillis = 350)
    )

    val modulePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingInstallUri = uri
    }
    val allFilesAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (resumeModulePickerAfterPermission) {
            if (hasRuntimeModuleFileAccess()) {
                resumeModulePickerAfterPermission = false
                modulePicker.launch(MODULE_INSTALL_MIME_TYPES)
            } else {
                showAllFilesAccessPrompt = true
            }
        }
    }

    fun launchModulePickerWithPermissionCheck() {
        modulePicker.launch(MODULE_INSTALL_MIME_TYPES)
    }

    fun openAllFilesAccessSettings() {
        showAllFilesAccessPrompt = false
        resumeModulePickerAfterPermission = true
        val packageUri = Uri.parse("package:${context.packageName}")
        val appSettings = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri)
        val allFilesSettings = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        runCatching {
            allFilesAccessLauncher.launch(appSettings)
        }.getOrElse {
            runCatching { allFilesAccessLauncher.launch(allFilesSettings) }
                .onFailure { showAllFilesAccessPrompt = true }
        }
    }

    fun launchModulePickerFallback() {
        showAllFilesAccessPrompt = false
        resumeModulePickerAfterPermission = false
        modulePicker.launch(MODULE_INSTALL_MIME_TYPES)
    }

    LaunchedEffect(pendingModuleInstallUri) {
        if (!pendingModuleInstallUri.isNullOrBlank()) {
            runCatching { Uri.parse(pendingModuleInstallUri) }.getOrNull()?.let { uri ->
                pendingInstallUri = uri
            }
            onPendingModuleInstallUriConsumed()
        }
    }

    LaunchedEffect(state.runtimeNavigationEnabled, state.rootGranted) {
        if (state.runtimeNavigationEnabled && state.rootGranted) vm.refreshAbkRuntimeStatus()
    }

    val scrollBehavior = MiuixScrollBehavior()

    val dynamicTopPadding by remember {
        derivedStateOf { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }

    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberBlurBackdrop(state.miuixBlurEnabled, surfaceColor)
    val barColor = if (backdrop != null) Color.Transparent else surfaceColor

    Scaffold(
        topBar = {
            CompositionLocalProvider(LocalBlurState provides backdrop) {
                searchStatus.TopAppBarAnim(
                    modifier = Modifier.blurEffect(surfaceColor.copy(0.87f)),
                    backgroundColor = barColor,
                ) {
                    TopAppBar(
                        color = barColor,
                        title = stringResource(R.string.runtime_installed_modules_title),
                        actions = {},
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
                                        } else Modifier
                                    )
                            ) {
                                SearchBarFake(searchStatus.label, dynamicTopPadding)
                            }
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(visible = fabVisible) {
                FloatingActionButton(
                    modifier = Modifier
                        .offset { IntOffset(0, offsetHeight.roundToPx()) }
                        .padding(bottom = outerPadding.calculateBottomPadding() + 20.dp, end = 20.dp)
                        .border(0.05.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape),
                    shadowElevation = 0.dp,
                    onClick = { launchModulePickerWithPermissionCheck() },
                    content = {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.runtime_install_module),
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                )
            }
        },
        popupHost = {
            searchStatus.SearchPager(
                onSearchStatusChange = { searchStatus = it },
                defaultResult = {},
                searchBarTopPadding = dynamicTopPadding,
            ) {
                val layoutDirection = LocalLayoutDirection.current
                ModuleListContent(
                    abkRuntimeError = state.abkRuntimeError,
                    abkRuntimeStatus = state.abkRuntimeStatus,
                    hasNativeManagerPermission = state.hasNativeManagerPermission,
                    abkRuntimeModuleActionId = state.abkRuntimeModuleActionId,
                    vm = vm,
                    groupedModules = groupedModules,
                    query = query,
                    showEmptyMessage = state.abkRuntimeStatus != null && groupedModules.isEmpty() && query.isBlank(),
                    showNoMatchMessage = state.abkRuntimeStatus != null && groupedModules.isEmpty() && query.isNotBlank(),
                    scrollBehavior = scrollBehavior,
                    nestedScrollConnection = nestedScrollConnection,
                    listState = listState,
                    innerPadding = PaddingValues(0.dp),
                    bottomPadding = outerPadding.calculateBottomPadding(),
                    layoutDirection = layoutDirection,
                    context = context,
                    onOpenWebUi = { module ->
                        context.startActivity(
                            Intent(context, ModuleWebUiActivity::class.java)
                                .putExtra(ModuleWebUiActivity.EXTRA_MODULE_ID, module.id)
                                .putExtra(ModuleWebUiActivity.EXTRA_MODULE_NAME, module.displayName())
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    onRequestUninstall = { module -> uninstallTarget = module },
                    onRunAction = { module ->
                        navigator.push(Route.ModuleActionTerminal(ModuleActionTerminalParams(
                            moduleId = module.id,
                            moduleName = module.displayName(),
                            moduleDir = module.moduleDir.ifBlank { "/data/adb/modules/${module.id}" }
                        )))
                    },
                    onSetEnabled = { moduleId, enabled -> vm.setAbkRuntimeModuleEnabled(moduleId, enabled) }
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current

        searchStatus.SearchBox {
            Box(
                modifier = Modifier.then(
                    if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier
                )
            ) {
                ModuleListContent(
                    abkRuntimeError = state.abkRuntimeError,
                    abkRuntimeStatus = state.abkRuntimeStatus,
                    hasNativeManagerPermission = state.hasNativeManagerPermission,
                    abkRuntimeModuleActionId = state.abkRuntimeModuleActionId,
                    vm = vm,
                    groupedModules = groupedModules,
                    query = query,
                    showEmptyMessage = state.abkRuntimeStatus != null && modules.isEmpty() && query.isBlank(),
                    showNoMatchMessage = state.abkRuntimeStatus != null && modules.isEmpty() && query.isNotBlank(),
                    scrollBehavior = scrollBehavior,
                    nestedScrollConnection = nestedScrollConnection,
                    listState = listState,
                    innerPadding = innerPadding,
                    bottomPadding = outerPadding.calculateBottomPadding(),
                    layoutDirection = layoutDirection,
                    context = context,
                    onOpenWebUi = { module ->
                        context.startActivity(
                            Intent(context, ModuleWebUiActivity::class.java)
                                .putExtra(ModuleWebUiActivity.EXTRA_MODULE_ID, module.id)
                                .putExtra(ModuleWebUiActivity.EXTRA_MODULE_NAME, module.displayName())
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    onRequestUninstall = { module -> uninstallTarget = module },
                    onRunAction = { module ->
                        navigator.push(Route.ModuleActionTerminal(ModuleActionTerminalParams(
                            moduleId = module.id,
                            moduleName = module.displayName(),
                            moduleDir = module.moduleDir.ifBlank { "/data/adb/modules/${module.id}" }
                        )))
                    },
                    onSetEnabled = { moduleId, enabled -> vm.setAbkRuntimeModuleEnabled(moduleId, enabled) }
                )
            }
        }
    }

    // BackHandler to collapse search when pressing back while search is expanded.
    // Must be at the screen level (not inside popupHost subcomposition) so that it
    // properly registers with the Activity's OnBackPressedDispatcher.
    BackHandler(enabled = searchStatus.shouldExpand() && navigator.backStackSize() <= 1) {
        searchStatus = searchStatus.copy(
            searchText = "",
            resultStatus = SearchStatus.ResultStatus.DEFAULT,
            current = SearchStatus.Status.COLLAPSING
        )
    }

    if (state.abkRuntimeModuleActionTitle != null) {
        WindowDialog(
            show = true,
            title = state.abkRuntimeModuleActionTitle,
            onDismissRequest = { vm.dismissRuntimeModuleActionOutput() }
        ) {
            Column {
                if (state.abkRuntimeModuleActionId != null) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        progress = null
                    )
                }
                Text(
                    text = state.abkRuntimeModuleActionOutput.ifEmpty {
                        listOf(stringResource(R.string.runtime_waiting_output))
                    }.joinToString("\n"),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    TextButton(
                        text = stringResource(R.string.close),
                        onClick = { vm.dismissRuntimeModuleActionOutput() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    WindowDialog(
        show = showAllFilesAccessPrompt,
        title = stringResource(R.string.runtime_file_access_required),
        onDismissRequest = {
            showAllFilesAccessPrompt = false
            resumeModulePickerAfterPermission = false
        }
    ) {
        Column {
            Text(
                text = stringResource(R.string.runtime_file_access_vendor_picker_warning),
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.runtime_file_access_desc),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    text = stringResource(R.string.runtime_system_picker),
                    onClick = { launchModulePickerFallback() },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(20.dp))
                TextButton(
                    text = stringResource(R.string.runtime_grant_permission),
                    onClick = { openAllFilesAccessSettings() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }

    pendingInstallUri?.let { uri ->
        val uriDisplayName = remember(context, uri) { runtimeModuleUriDisplayName(context, uri) }
        WindowDialog(
            show = true,
            title = stringResource(R.string.runtime_confirm_flash_module),
            onDismissRequest = { pendingInstallUri = null }
        ) {
            Column {
                Text(
                    text = uriDisplayName,
                    style = MiuixTheme.textStyles.subtitle,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = uri.toString(),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.runtime_confirm_flash_module_desc),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = { pendingInstallUri = null },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                    TextButton(
                        text = stringResource(R.string.runtime_confirm_flash),
                        onClick = {
                            pendingInstallUri = null
                            navigator.push(Route.ModuleInstallLog(
                                params = ModuleInstallParams(
                                    uri = uri.toString(),
                                    displayName = uriDisplayName
                                )
                            ))
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    }

    uninstallTarget?.let { module ->
        val pending = !module.remove
        WindowDialog(
            show = true,
            title = stringResource(R.string.runtime_uninstall),
            onDismissRequest = { uninstallTarget = null }
        ) {
            Column {
                Text(
                    text = if (pending) {
                        String.format("确定要卸载模块 \"%s\" 吗？", module.displayName())
                    } else {
                        String.format("确定要撤销模块 \"%s\" 的卸载标记吗？", module.displayName())
                    },
                    color = MiuixTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = { uninstallTarget = null },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                    TextButton(
                        text = if (pending) stringResource(R.string.runtime_uninstall) else stringResource(R.string.runtime_revoke),
                        onClick = {
                            vm.setAbkRuntimeModulePendingUninstall(module.id, !module.remove)
                            uninstallTarget = null
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    }

}

@Composable
private fun ModuleListContent(
    abkRuntimeError: String?,
    abkRuntimeStatus: AbkRuntimeStatus?,
    hasNativeManagerPermission: Boolean,
    abkRuntimeModuleActionId: String?,
    vm: MainViewModel,
    groupedModules: List<RuntimeModuleDisplayGroup>,
    query: String,
    showEmptyMessage: Boolean,
    showNoMatchMessage: Boolean,
    scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior,
    nestedScrollConnection: NestedScrollConnection,
    listState: LazyListState,
    innerPadding: PaddingValues,
    bottomPadding: androidx.compose.ui.unit.Dp,
    layoutDirection: LayoutDirection,
    context: android.content.Context,
    onOpenWebUi: (AbkRuntimeModule) -> Unit,
    onRequestUninstall: (AbkRuntimeModule) -> Unit,
    onRunAction: (AbkRuntimeModule) -> Unit,
    onSetEnabled: (String, Boolean) -> Unit
) {

    val refreshPulling = stringResource(R.string.runtime_refresh_installed_modules)
    val refreshRelease = stringResource(R.string.runtime_refresh_installed_modules)
    val refreshRefresh = stringResource(R.string.runtime_refresh_installed_modules)
    val refreshComplete = stringResource(R.string.runtime_refresh_installed_modules)

    var isRefreshing by rememberSaveable { mutableStateOf(false) }
    val refreshTexts = remember {
        listOf(refreshPulling, refreshRelease, refreshRefresh, refreshComplete)
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            delay(350)
            vm.refreshAbkRuntimeStatus()
            isRefreshing = false
        }
    }

    // 刷新完成后，如果兼容层提示磁贴可见，确保它不被顶栏遮挡
    LaunchedEffect(abkRuntimeError, hasNativeManagerPermission, isRefreshing) {
        if (!isRefreshing && abkRuntimeError != null && !hasNativeManagerPermission) {
            delay(100)
            listState.animateScrollToItem(0)
        }
    }

    PullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = { if (!isRefreshing) isRefreshing = true },
        refreshTexts = refreshTexts,
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 6.dp,
            start = innerPadding.calculateStartPadding(layoutDirection),
            end = innerPadding.calculateEndPadding(layoutDirection),
        ),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxHeight()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .nestedScroll(nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 6.dp,
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
            ),
            overscrollEffect = null,
        ) {

            abkRuntimeError?.let { error ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (!hasNativeManagerPermission) {
                                    error
                                } else {
                                    stringResource(R.string.runtime_operation_incomplete_retry)
                                },
                                color = MiuixTheme.colorScheme.error,
                                style = MiuixTheme.textStyles.body2
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { vm.refreshAbkRuntimeStatus() },
                                colors = ButtonDefaults.buttonColorsPrimary()
                            ) {
                                Text(stringResource(R.string.runtime_refresh_installed_modules))
                            }
                        }
                    }
                }
            }

            if (showEmptyMessage) {
                item {
                    Text(
                        text = stringResource(R.string.runtime_no_reported_modules),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                    )
                }
            }

            if (showNoMatchMessage) {
                item {
                    Text(
                        text = stringResource(R.string.runtime_no_matching_modules),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                    )
                }
            }

            groupedModules.forEach { group ->
                group.groupName?.let { groupName ->
                    item(key = "group-${groupName}") {
                        Text(
                            text = groupName,
                            style = MiuixTheme.textStyles.subtitle,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        group.groupDescription?.takeIf { it.isNotBlank() }?.let { desc ->
                            Text(
                                text = desc,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }

                items(
                    items = group.modules,
                    key = { "module-${it.id}" }
                ) { module ->
                    InstalledModuleCardMiuix(
                        module = module,
                        actionInFlight = abkRuntimeModuleActionId == module.id,
                        onSetEnabled = { enabled -> onSetEnabled(module.id, enabled) },
                        onRequestUninstall = { onRequestUninstall(module) },
                        onRunAction = { onRunAction(module) },
                        onOpenWebUi = { onOpenWebUi(module) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(bottomPadding + 80.dp))
            }
        }
    }
}

@Composable
private fun InstalledModuleCardMiuix(
    module: AbkRuntimeModule,
    actionInFlight: Boolean,
    onSetEnabled: (Boolean) -> Unit,
    onRequestUninstall: () -> Unit,
    onRunAction: () -> Unit,
    onOpenWebUi: () -> Unit
) {
    val canUninstall = module.canUninstallRuntimeModule()
    val isDark = isSystemInDarkTheme()
    val onSurface = MiuixTheme.colorScheme.onSurface
    val secondaryContainer = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
    val actionIconTint = remember(isDark) { onSurface.copy(alpha = if (isDark) 0.7f else 0.9f) }
    val typeLabel = miuixRuntimeModuleTypeLabel(module)
    val textDecoration = if (module.remove) TextDecoration.LineThrough else null

    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        insideMargin = PaddingValues(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                SubcomposeLayout { constraints ->
                    val namePlaceable = subcompose("name") {
                        Text(
                            text = module.displayName(),
                            fontSize = 17.sp,
                            fontWeight = FontWeight(550),
                            color = MiuixTheme.colorScheme.onSurface,
                            textDecoration = textDecoration,
                            onTextLayout = { }
                        )
                    }.first().measure(constraints)

                    layout(namePlaceable.width, namePlaceable.height) {
                        namePlaceable.placeRelative(0, 0)
                    }
                }
                if (module.version.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.runtime_module_version, module.version),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp),
                        fontWeight = FontWeight(550),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textDecoration = textDecoration
                    )
                }
                if (module.author.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.runtime_module_author, module.author),
                        fontSize = 12.sp,
                        fontWeight = FontWeight(550),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textDecoration = textDecoration
                    )
                }
                Text(
                    text = "Type: $typeLabel",
                    fontSize = 12.sp,
                    fontWeight = FontWeight(550),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textDecoration = textDecoration
                )
            }
            if (module.controllable && !module.readonly) {
                Switch(
                    checked = module.enabled,
                    onCheckedChange = onSetEnabled,
                    enabled = !actionInFlight && !module.remove
                )
            }
        }

        if (module.description.isNotBlank()) {
            Text(
                text = module.description,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 4.dp),
                overflow = TextOverflow.Ellipsis,
                maxLines = 4,
                textDecoration = textDecoration
            )
        }

        if (module.repoUrl.isNotBlank()) {
            Text(
                text = module.repoUrl,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 2.dp),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                textDecoration = textDecoration
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = 0.5.dp,
            color = MiuixTheme.colorScheme.outline.copy(alpha = 0.5f)
        )

        Row {
            AnimatedVisibility(
                visible = (module.actionSupported || module.hasActionScript) && !module.remove,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                IconButton(
                    backgroundColor = secondaryContainer,
                    minHeight = 35.dp,
                    minWidth = 35.dp,
                    onClick = onRunAction
                ) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = Icons.Rounded.Settings,
                        tint = actionIconTint,
                        contentDescription = stringResource(R.string.runtime_run_action)
                    )
                }
            }

            AnimatedVisibility(
                visible = module.hasWebUi && !module.remove,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                IconButton(
                    backgroundColor = secondaryContainer,
                    minHeight = 35.dp,
                    minWidth = 35.dp,
                    onClick = onOpenWebUi
                ) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = Icons.Rounded.Code,
                        tint = actionIconTint,
                        contentDescription = stringResource(R.string.runtime_open_webui)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            if (canUninstall) {
                IconButton(
                    minHeight = 35.dp,
                    minWidth = 35.dp,
                    onClick = onRequestUninstall,
                    backgroundColor = secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = if (module.remove) Icons.Rounded.Refresh else Icons.Rounded.Delete,
                            tint = actionIconTint,
                            contentDescription = null
                        )
                        Text(
                            modifier = Modifier.padding(start = 4.dp, end = 3.dp),
                            text = if (module.remove) {
                                stringResource(R.string.runtime_revoke)
                            } else {
                                stringResource(R.string.runtime_uninstall)
                            },
                            color = actionIconTint,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun miuixRuntimeModuleTypeLabel(module: AbkRuntimeModule): String =
    when (module.normalizedType()) {
        "standard" -> stringResource(R.string.runtime_module_type_standard)
        "builtin" -> stringResource(R.string.runtime_module_type_builtin)
        "kpm" -> "KPM"
        else -> module.normalizedType()
    }
