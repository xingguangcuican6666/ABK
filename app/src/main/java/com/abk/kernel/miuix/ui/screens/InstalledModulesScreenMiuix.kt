package com.abk.kernel.miuix.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.sp
import com.abk.kernel.R
import com.abk.kernel.data.model.AbkRuntimeModule
import com.abk.kernel.data.model.AbkRuntimeStatus
import com.abk.kernel.miuix.component.SearchBarFake
import com.abk.kernel.miuix.component.SearchBox
import com.abk.kernel.miuix.component.SearchPager
import com.abk.kernel.miuix.component.SearchStatus
import com.abk.kernel.miuix.ui.screens.runtime.ModuleActionTerminalParams
import com.abk.kernel.miuix.ui.screens.runtime.ModuleInstallParams
import com.abk.kernel.ui.navigation3.LocalNavigator
import com.abk.kernel.ui.navigation3.Route
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
import com.abk.kernel.ui.webui.ModuleWebUiActivity
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.viewmodel.MainViewModel
import com.abk.kernel.viewmodel.RuntimeModuleUpdateTarget
import com.abk.kernel.viewmodel.resolveRuntimeModuleChangelog
import com.abk.kernel.viewmodel.findRuntimeModuleUpdateTarget
import com.abk.kernel.miuix.util.BlurredBar
import com.abk.kernel.miuix.util.rememberBlurBackdrop
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

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

    var updateTarget by remember { mutableStateOf<RuntimeModuleUpdateTarget?>(null) }
    var runtimeUpdateCandidates by remember { mutableStateOf<Map<String, RuntimeModuleUpdateTarget>>(emptyMap()) }

    val query = searchStatus.searchText
    val modules = remember(state.abkRuntimeStatus?.modules, query) {
        state.abkRuntimeStatus?.modules.orEmpty()
            .filter { it.matchesRuntimeModuleQuery(query) }
            .sortedWith(
                compareByDescending<AbkRuntimeModule> { it.metamodule && it.enabled }
                    .thenBy { it.typeOrder() }
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

    val scope = rememberCoroutineScope()

    val runtimeModulesForUpdates = remember(state.abkRuntimeStatus?.modules) {
        state.abkRuntimeStatus?.modules.orEmpty()
    }
    LaunchedEffect(runtimeModulesForUpdates) {
        val targetsMap = mutableMapOf<String, RuntimeModuleUpdateTarget>()
        for (module in runtimeModulesForUpdates) {
            findRuntimeModuleUpdateTarget(module)?.let { target ->
                targetsMap[module.id] = target
            }
        }
        runtimeUpdateCandidates = targetsMap
        if (updateTarget?.let { it.module.id !in targetsMap } == true) {
            updateTarget = null
        }
    }

    fun installModuleUpdate(target: RuntimeModuleUpdateTarget) {
        scope.launch {
            val downloadResult = withContext(Dispatchers.IO) {
                DownloadUtils.downloadRuntimeModuleAsset(
                    context = context,
                    token = null,
                    url = target.updateInfo.zipUrl,
                    name = target.module.displayName(),
                    sizeBytes = 0L,
                    runTitle = target.module.displayName(),
                    downloadDirectoryPath = state.downloadDirectory,
                    downloadThreadCount = state.downloadThreadCount
                )
            }
            val downloadedFile = downloadResult.artifacts.firstOrNull()?.filePath?.let(::File)
            if (downloadedFile != null && downloadedFile.exists()) {
                runtimeUpdateCandidates = runtimeUpdateCandidates - target.module.id
                vm.refreshAbkRuntimeStatus()
                navigator.push(
                    Route.ModuleInstallLog(
                        params = ModuleInstallParams(
                            uri = android.net.Uri.fromFile(downloadedFile).toString(),
                            displayName = target.module.displayName()
                        )
                    )
                )
            }
        }
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
            BlurredBar(backdrop, surfaceColor) {
                searchStatus.TopAppBarAnim(backgroundColor = barColor) {
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
                    runtimeUpdateCandidates = runtimeUpdateCandidates,
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
                    onRequestUpdate = { candidate -> updateTarget = candidate },
                    onRunAction = { module ->
                        navigator.push(
                            Route.ModuleActionTerminal(
                                ModuleActionTerminalParams(
                                    moduleId = module.id,
                                    moduleName = module.displayName(),
                                    moduleDir = module.moduleDir.ifBlank { "/data/adb/modules/${module.id}" }
                                )
                            )
                        )
                    },
                    onSetEnabled = { moduleId, enabled ->
                        vm.setAbkRuntimeModuleEnabled(moduleId, enabled)
                    }
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
                    runtimeUpdateCandidates = runtimeUpdateCandidates,
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
                    onRequestUpdate = { candidate -> updateTarget = candidate },
                    onRunAction = { module ->
                        navigator.push(
                            Route.ModuleActionTerminal(
                                ModuleActionTerminalParams(
                                    moduleId = module.id,
                                    moduleName = module.displayName(),
                                    moduleDir = module.moduleDir.ifBlank { "/data/adb/modules/${module.id}" }
                                )
                            )
                        )
                    },
                    onSetEnabled = { moduleId, enabled ->
                        vm.setAbkRuntimeModuleEnabled(moduleId, enabled)
                    }
                )
            }
        }
    }

    BackHandler(enabled = searchStatus.isExpand() && navigator.backStackSize() <= 1) {
        searchStatus = searchStatus.copy(current = SearchStatus.Status.COLLAPSING)
    }

    showAllFilesAccessPrompt.let { show ->
        if (show) {
            WindowDialog(
                show = true,
                title = stringResource(R.string.runtime_file_access_required),
                onDismissRequest = {
                    showAllFilesAccessPrompt = false
                    resumeModulePickerAfterPermission = false
                }
            ) {
                Column {
                    Text(
                    text = stringResource(R.string.runtime_file_access_desc),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            text = stringResource(R.string.cancel),
                            onClick = { showAllFilesAccessPrompt = false },
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
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = 12.dp),
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
                            navigator.push(
                                Route.ModuleInstallLog(
                                    params = ModuleInstallParams(
                                        uri = uri.toString(),
                                        displayName = uriDisplayName
                                    )
                                )
                            )
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    imageVector = if (pending) Icons.Rounded.Delete else Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (pending) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary
                )
                Text(
                    text = module.displayName(),
                    style = MiuixTheme.textStyles.subtitle,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = module.id,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val message = if (pending) {
                    stringResource(R.string.runtime_confirm_uninstall_module_desc)
                } else {
                    stringResource(R.string.runtime_revoke_uninstall_module_desc)
                }
                Text(
                    text = message,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = { uninstallTarget = null },
                        modifier = Modifier.weight(1f)
                    )
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

    updateTarget?.let { target ->
        RuntimeModuleUpdateConfirmDialogMiuix(
            target = target,
            downloadDirectoryPath = state.downloadDirectory,
            onDismiss = { updateTarget = null },
            onConfirm = {
                updateTarget = null
                installModuleUpdate(target)
            }
        )
    }
}

@Composable
private fun ModuleListContent(
    abkRuntimeError: String?,
    abkRuntimeStatus: AbkRuntimeStatus?,
    hasNativeManagerPermission: Boolean,
    abkRuntimeModuleActionId: String?,
    vm: MainViewModel,
    runtimeUpdateCandidates: Map<String, RuntimeModuleUpdateTarget>,
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
    onRequestUpdate: (RuntimeModuleUpdateTarget) -> Unit,
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
                        updateCandidate = runtimeUpdateCandidates[module.id],
                        actionInFlight = abkRuntimeModuleActionId == module.id,
                        onSetEnabled = { enabled -> onSetEnabled(module.id, enabled) },
                        onRequestUninstall = { onRequestUninstall(module) },
                        onRequestUpdate = {
                            runtimeUpdateCandidates[module.id]?.let { cand ->
                                onRequestUpdate(cand)
                            }
                        },
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
    updateCandidate: RuntimeModuleUpdateTarget?,
    onSetEnabled: (Boolean) -> Unit,
    onRequestUninstall: () -> Unit,
    onRequestUpdate: () -> Unit,
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

    val updateBg = MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)
    val updateTint = MiuixTheme.colorScheme.primary
    val hasUpdate = updateCandidate != null && !module.remove && !module.update

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
                    val spacingPx = 6.dp.roundToPx()
                    var nameTextLayout: TextLayoutResult? = null
                    val metaPlaceable = if (module.metamodule) {
                        subcompose("meta") {
                            Text(
                                text = "META",
                                fontSize = 12.sp,
                                color = updateTint,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(updateBg)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                fontWeight = FontWeight(750),
                                maxLines = 1,
                                softWrap = false
                            )
                        }.first().measure(
                            Constraints(0, constraints.maxWidth, 0, constraints.maxHeight)
                        )
                    } else null

                    val reserved = (metaPlaceable?.width ?: 0) + if (metaPlaceable != null) spacingPx else 0
                    val nameMax = (constraints.maxWidth - reserved).coerceAtLeast(0)
                    val namePlaceable = subcompose("name") {
                        Text(
                            text = module.displayName(),
                            fontSize = 17.sp,
                            fontWeight = FontWeight(550),
                            color = MiuixTheme.colorScheme.onSurface,
                            textDecoration = textDecoration,
                            onTextLayout = { nameTextLayout = it }
                        )
                    }.first().measure(
                        Constraints(constraints.minWidth, nameMax, constraints.minHeight, constraints.maxHeight)
                    )

                    val width = (namePlaceable.width + reserved).coerceIn(constraints.minWidth, constraints.maxWidth)
                    val height = maxOf(namePlaceable.height, metaPlaceable?.height ?: 0)

                    layout(width, height) {
                        namePlaceable.placeRelative(0, 0)
                        val endX = nameTextLayout?.let { layoutRes ->
                            val last = (layoutRes.lineCount - 1).coerceAtLeast(0)
                            layoutRes.getLineRight(last).toInt()
                        } ?: namePlaceable.width
                        metaPlaceable?.placeRelative(
                            endX + spacingPx,
                            (height - (metaPlaceable.height)) / 2
                        )
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
                    enabled = !actionInFlight && !module.remove && !module.update
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
                visible = (module.actionSupported || module.hasActionScript || module.hasWebUi) && !module.remove && !module.update,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    if (module.actionSupported || module.hasActionScript) {
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

                    if (module.hasWebUi) {
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
                }
            }

            Spacer(Modifier.weight(1f))

            AnimatedVisibility(
                visible = hasUpdate,
                enter = expandHorizontally() + slideInHorizontally(initialOffsetX = { it }),
                exit = shrinkHorizontally() + slideOutHorizontally(targetOffsetX = { it })
            ) {
                IconButton(
                    backgroundColor = updateBg,
                    modifier = Modifier.padding(end = 8.dp),
                    enabled = !module.remove && !actionInFlight,
                    minHeight = 35.dp,
                    minWidth = 35.dp,
                    onClick = onRequestUpdate
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = MiuixIcons.UploadCloud,
                            tint = updateTint,
                            contentDescription = stringResource(R.string.runtime_update_module)
                        )
                        Text(
                            modifier = Modifier.padding(start = 4.dp, end = 3.dp),
                            text = stringResource(R.string.module_update),
                            color = updateTint,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            if (canUninstall) {
                IconButton(
                    minHeight = 35.dp,
                    minWidth = 35.dp,
                    onClick = onRequestUninstall,
                    backgroundColor = secondaryContainer
                ) {
                    val animatedPadding by animateDpAsState(
                        targetValue = if (!hasUpdate) 10.dp else 0.dp,
                        animationSpec = tween(durationMillis = 300)
                    )
                    Row(
                        modifier = Modifier.padding(horizontal = animatedPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = if (module.remove) Icons.Rounded.Refresh else Icons.Rounded.Delete,
                            tint = actionIconTint,
                            contentDescription = null
                        )
                        AnimatedVisibility(
                            visible = !hasUpdate,
                            enter = expandHorizontally(),
                            exit = shrinkHorizontally()
                        ) {
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
}
@Composable
private fun RuntimeModuleUpdateConfirmDialogMiuix(
    target: RuntimeModuleUpdateTarget,
    downloadDirectoryPath: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val module = target.module
    val updateInfo = target.updateInfo
    val changelogScroll = rememberScrollState()
    var changelogText by remember(updateInfo.changelog) { mutableStateOf(updateInfo.changelog) }

    val context = LocalContext.current

    LaunchedEffect(updateInfo.changelog) {
        changelogText = try {
            if (updateInfo.changelog.isBlank()) {
                ""
            } else {
                resolveRuntimeModuleChangelog(updateInfo.changelog)
            }
        } catch (_: Exception) {
            context.getString(R.string.runtime_update_changelog_unavailable)
        }
    }

    WindowDialog(
        show = true,
        title = stringResource(R.string.runtime_update_module),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = module.displayName(),
                style = MiuixTheme.textStyles.subtitle,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.runtime_update_version_change,
                    module.version.ifBlank { "unknown" },
                    updateInfo.version.ifBlank { "unknown" }
                ),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            AnimatedVisibility(
                visible = changelogText.isNotBlank(),
                enter = fadeIn() + expandVertically(
                    animationSpec = tween(
                        durationMillis = 250,
                        easing = FastOutSlowInEasing
                    )
                ),
                exit = fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(6.dp))
                    RuntimeModuleUpdateChangelogMiuix(
                        changelog = changelogText,
                        scrollState = changelogScroll
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            Text(
                text = stringResource(R.string.runtime_confirm_update_module_desc, downloadDirectoryPath),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = stringResource(R.string.runtime_confirm_flash),
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
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

private const val RUNTIME_MARKDOWN_URL_TAG = "runtime_markdown_url"
private val RUNTIME_MARKDOWN_ORDERED_LIST_REGEX = Regex("""^\d+\.\s+""")
private val RUNTIME_MARKDOWN_BARE_URL_REGEX = Regex("""https?://[^\s)]+""")

@Composable
private fun RuntimeModuleUpdateChangelogMiuix(
    changelog: String,
    scrollState: ScrollState
) {
    val colorScheme = MiuixTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val annotatedChangelog = remember(changelog, colorScheme.primary, colorScheme.surfaceVariant) {
        buildRuntimeMarkdownAnnotatedString(
            markdown = changelog.ifBlank { "-" },
            linkColor = colorScheme.primary,
            codeBackground = colorScheme.surfaceVariant,
        )
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp, max = 320.dp),
        insideMargin = PaddingValues(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 320.dp)
                .animateContentSize(tween(durationMillis = 250, easing = FastOutSlowInEasing))
                .verticalScroll(scrollState)
                .padding(12.dp),
        ) {
            Text(
                text = stringResource(R.string.runtime_update_changelog),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontWeight = FontWeight.Medium
            )
            ClickableText(
                text = annotatedChangelog,
                style = MiuixTheme.textStyles.body2.copy(color = MiuixTheme.colorScheme.onSurface),
                onClick = { offset: Int ->
                    annotatedChangelog
                        .getStringAnnotations(RUNTIME_MARKDOWN_URL_TAG, offset, offset)
                        .firstOrNull()
                        ?.let { annotation -> uriHandler.openUri(annotation.item) }
                }
            )
        }
    }
}

private fun buildRuntimeMarkdownAnnotatedString(
    markdown: String,
    linkColor: Color,
    codeBackground: Color,
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').lines()
    var inCodeBlock = false

    lines.forEachIndexed { index, rawLine ->
        val trimmed = rawLine.trimStart()
        if (trimmed.startsWith("```")) {
            inCodeBlock = !inCodeBlock
            if (index != lines.lastIndex) builder.append('\n')
            return@forEachIndexed
        }

        val lineStart = builder.length
        if (inCodeBlock) {
            builder.append(rawLine.ifBlank { " " })
            if (builder.length > lineStart) {
                builder.addStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBackground,
                    ),
                    lineStart,
                    builder.length,
                )
            }
        } else {
            val headingLevel = trimmed.takeWhile { it == '#' }.length
            when {
                headingLevel in 1..6 && trimmed.getOrNull(headingLevel) == ' ' -> {
                    appendRuntimeMarkdownInline(builder, trimmed.drop(headingLevel + 1), linkColor, codeBackground)
                    if (builder.length > lineStart) {
                        builder.addStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = when (headingLevel) {
                                    1 -> 20.sp
                                    2 -> 18.sp
                                    3 -> 16.sp
                                    else -> 14.sp
                                },
                            ),
                            lineStart,
                            builder.length,
                        )
                    }
                }
                trimmed.startsWith(">") -> {
                    appendRuntimeMarkdownInline(builder, trimmed.removePrefix("> ").removePrefix(">"), linkColor, codeBackground)
                    if (builder.length > lineStart) {
                        builder.addStyle(
                            SpanStyle(
                                fontStyle = FontStyle.Italic,
                                color = Color.Gray,
                            ),
                            lineStart,
                            builder.length,
                        )
                    }
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                    builder.append("• ")
                    appendRuntimeMarkdownInline(builder, trimmed.drop(2), linkColor, codeBackground)
                }
                RUNTIME_MARKDOWN_ORDERED_LIST_REGEX.containsMatchIn(trimmed) -> {
                    appendRuntimeMarkdownInline(builder, trimmed, linkColor, codeBackground)
                }
                else -> {
                    appendRuntimeMarkdownInline(builder, rawLine, linkColor, codeBackground)
                }
            }
        }

        if (index != lines.lastIndex) builder.append('\n')
    }

    return builder.toAnnotatedString()
}

private fun appendRuntimeMarkdownInline(
    builder: AnnotatedString.Builder,
    text: String,
    linkColor: Color,
    codeBackground: Color,
) {
    var index = 0
    while (index < text.length) {
        val markdownLink = runtimeMarkdownLinkAt(text, index)
        if (markdownLink != null) {
            builder.pushStringAnnotation(RUNTIME_MARKDOWN_URL_TAG, markdownLink.second)
            builder.pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
            builder.append(markdownLink.first)
            builder.pop()
            builder.pop()
            index = markdownLink.third
            continue
        }

        val bareUrl = RUNTIME_MARKDOWN_BARE_URL_REGEX.find(text, index)
        if (bareUrl != null && bareUrl.range.first == index) {
            val url = bareUrl.value
            builder.pushStringAnnotation(RUNTIME_MARKDOWN_URL_TAG, url)
            builder.pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
            builder.append(url)
            builder.pop()
            builder.pop()
            index = bareUrl.range.last + 1
            continue
        }

        val bold = runtimeMarkdownDelimitedSegment(text, index, "**")
        if (bold != null) {
            builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            builder.append(bold.first)
            builder.pop()
            index = bold.second
            continue
        }

        val code = runtimeMarkdownDelimitedSegment(text, index, "`")
        if (code != null) {
            builder.pushStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = codeBackground,
                )
            )
            builder.append(code.first)
            builder.pop()
            index = code.second
            continue
        }

        val italicStar = runtimeMarkdownDelimitedSegment(text, index, "*")
        if (italicStar != null) {
            builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
            builder.append(italicStar.first)
            builder.pop()
            index = italicStar.second
            continue
        }

        val italicUnderline = runtimeMarkdownDelimitedSegment(text, index, "_")
        if (italicUnderline != null) {
            builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
            builder.append(italicUnderline.first)
            builder.pop()
            index = italicUnderline.second
            continue
        }

        builder.append(text[index])
        index += 1
    }
}

private fun runtimeMarkdownLinkAt(text: String, index: Int): Triple<String, String, Int>? {
    if (text.getOrNull(index) != '[') return null
    val labelEnd = text.indexOf(']', startIndex = index + 1)
    if (labelEnd <= index + 1 || text.getOrNull(labelEnd + 1) != '(') return null
    val urlEnd = text.indexOf(')', startIndex = labelEnd + 2)
    if (urlEnd <= labelEnd + 2) return null
    val label = text.substring(index + 1, labelEnd)
    val url = text.substring(labelEnd + 2, urlEnd)
    if (!url.startsWith("https://") && !url.startsWith("http://")) return null
    return Triple(label, url, urlEnd + 1)
}

private fun runtimeMarkdownDelimitedSegment(
    text: String,
    index: Int,
    delimiter: String,
): Pair<String, Int>? {
    if (!text.startsWith(delimiter, startIndex = index)) return null
    val end = text.indexOf(delimiter, startIndex = index + delimiter.length)
    if (end <= index + delimiter.length) return null
    return text.substring(index + delimiter.length, end) to (end + delimiter.length)
}
