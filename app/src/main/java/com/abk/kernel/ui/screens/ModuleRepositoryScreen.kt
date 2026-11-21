@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import com.abk.kernel.ui.blur.BlurScreenScaffold
import com.abk.kernel.ui.blur.blurredCardBackground
import com.abk.kernel.ui.blur.blurredCardSurfaceColor
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.state.ToggleableState
import io.github.xingguangcuican6666.abk.ui.components.*
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkShapes
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone
import com.abk.kernel.R
import com.abk.kernel.data.model.CustomExternalModuleStage
import com.abk.kernel.data.model.ExternalModuleMetadata
import com.abk.kernel.data.model.ModuleCatalogItemKind
import com.abk.kernel.data.model.ModuleCatalogItem
import com.abk.kernel.data.model.ModuleCatalogRepository
import com.abk.kernel.data.model.RuntimeModuleCatalogItem
import com.abk.kernel.data.model.RuntimeModuleRepository
import com.abk.kernel.data.model.downloadFileName
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.AppPageBackground
import com.abk.kernel.ui.components.ObserveChildPageVisibility
import com.abk.kernel.ui.components.childPageOverlayEnterTransition
import com.abk.kernel.ui.components.childPageOverlayExitTransition
import com.abk.kernel.ui.components.childPageScrimExitTransition
import com.abk.kernel.ui.components.rememberChildPageBackController
import com.abk.kernel.ui.components.rememberChildPageOverlayTransition
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.theme.appPageBackgroundColor
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.LocaleHelper
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.viewmodel.MainViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val RUNTIME_MODULE_DOWNLOAD_RUN_ID = -2_000_000_001L

enum class ModuleRepositoryMode {
    BUILD_ABK,
    RUNTIME_STANDARD
}

private data class ModuleListComputation<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleRepositoryScreen(
    vm: MainViewModel,
    mode: ModuleRepositoryMode,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onRepositoryPageVisibleChange: (Boolean) -> Unit = {}
) {
    if (mode == ModuleRepositoryMode.BUILD_ABK) {
        BuildModuleRepositoryScreenContent(
            vm = vm,
            outerPadding = outerPadding,
            onRepositoryPageVisibleChange = onRepositoryPageVisibleChange
        )
        return
    }

    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val motionScheme = MaterialTheme.motionScheme
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showRepositorySettings by rememberSaveable { mutableStateOf(false) }
    val repositoryPageTransition = rememberChildPageOverlayTransition(
        visible = showRepositorySettings,
        label = "runtime-module-repository-settings"
    )
    var pendingInstallModule by remember { mutableStateOf<MergedRuntimeCatalogModule?>(null) }
    var installDialogVisible by remember { mutableStateOf(false) }
    var installRunning by remember { mutableStateOf(false) }
    var installSuccess by remember { mutableStateOf<Boolean?>(null) }
    var installLog by remember { mutableStateOf<List<String>>(emptyList()) }
    val mergedModulesState by produceState(
        initialValue = ModuleListComputation<MergedRuntimeCatalogModule>(
            loading = state.runtimeModuleRepositories.isNotEmpty()
        ),
        key1 = state.runtimeModuleRepositories
    ) {
        if (state.runtimeModuleRepositories.isEmpty()) {
            value = ModuleListComputation(items = emptyList(), loading = false)
            return@produceState
        }
        value = ModuleListComputation(loading = true)
        val merged = withContext(Dispatchers.Default) {
            mergeRuntimeCatalogModules(state.runtimeModuleRepositories)
        }
        value = ModuleListComputation(items = merged, loading = false)
    }
    val mergedModules = mergedModulesState.items
    val filteredModulesState by produceState(
        initialValue = ModuleListComputation<MergedRuntimeCatalogModule>(
            loading = mergedModulesState.loading
        ),
        key1 = mergedModulesState,
        key2 = searchQuery
    ) {
        if (mergedModulesState.loading) {
            value = ModuleListComputation(loading = true)
            return@produceState
        }
        value = ModuleListComputation(loading = true)
        val filtered = withContext(Dispatchers.Default) {
            if (searchQuery.isBlank()) {
                mergedModules
            } else {
                mergedModules.filter { it.matchesQuery(searchQuery) }
            }
        }
        value = ModuleListComputation(items = filtered, loading = false)
    }
    val filteredModules = filteredModulesState.items
    val listComputing = mergedModulesState.loading || filteredModulesState.loading

    fun closeRepositorySettings() {
        showRepositorySettings = false
    }

    val childPageBack = rememberChildPageBackController(
        enabled = showRepositorySettings,
        predictiveBackEnabled = state.predictiveBackEnabled,
        onBack = ::closeRepositorySettings,
    )

    fun openRepositorySettings() {
        childPageBack.resetProgress()
        showRepositorySettings = true
    }

    ObserveChildPageVisibility(
        transition = repositoryPageTransition,
        onVisibleChange = onRepositoryPageVisibleChange,
        onAfterExitAnimation = { childPageBack.resetProgress() }
    )

    fun appendInstallLog(line: String) {
        installLog = installLog + line
    }

    fun startInstall(module: MergedRuntimeCatalogModule) {
        if (installRunning) return
        pendingInstallModule = null
        installDialogVisible = true
        installRunning = true
        installSuccess = null
        installLog = listOf(
            "$ module install",
            "name: ${module.module.name}",
            "source: ${module.module.zipUrl}",
            "",
            runtimeRepoDownloadingLabel(context)
        )
        scope.launch {
            val downloadName = module.module.downloadFileName()
            val downloadResult = withContext(Dispatchers.IO) {
                DownloadUtils.downloadRuntimeModuleAsset(
                    context = context,
                    token = null,
                    url = module.module.zipUrl,
                    name = downloadName,
                    sizeBytes = 0L,
                    runId = RUNTIME_MODULE_DOWNLOAD_RUN_ID,
                    runTitle = module.sources.firstOrNull().orEmpty().ifBlank {
                        runtimeRepoUnknownSourceLabel(context)
                    },
                    downloadDirectoryPath = state.downloadDirectory,
                    downloadThreadCount = state.downloadThreadCount
                )
            }
            val downloadedFile = downloadResult.artifacts.firstOrNull()?.filePath?.let(::File)
            if (downloadedFile == null || !downloadedFile.exists()) {
                installRunning = false
                installSuccess = false
                installLog = installLog + listOf(
                    "",
                    downloadResult.errorMessage ?: runtimeRepoDownloadFailedLabel(context)
                )
                return@launch
            }

            appendInstallLog("file: ${downloadedFile.absolutePath}")
            appendInstallLog(context.getString(R.string.runtime_wait_root_shell))
            val result = withContext(Dispatchers.IO) {
                if (!RootUtils.refreshRootState()) {
                    RootUtils.ShellResult(false, listOf(context.getString(R.string.runtime_manager_inactive)))
                } else {
                    RootUtils.installModule(downloadedFile.absolutePath) { line ->
                        scope.launch(Dispatchers.Main.immediate) {
                            appendInstallLog(line)
                        }
                    }
                }
            }
            installRunning = false
            installSuccess = result.success
            installLog = listOf(
                "$ module install ${downloadedFile.name}",
                "file: ${downloadedFile.absolutePath}",
                ""
            ) + result.output.ifEmpty {
                listOf(
                    if (result.success) {
                        context.getString(R.string.runtime_module_install_done_no_output)
                    } else {
                        context.getString(R.string.runtime_module_install_failed_no_log)
                    }
                )
            }
            if (result.success) vm.refreshAbkRuntimeStatus()
        }
    }

    DisposableEffect(Unit) {
        onDispose { onRepositoryPageVisibleChange(false) }
    }

    pendingInstallModule?.let { merged ->
        RuntimeRepositoryInstallConfirmDialog(
            module = merged,
            onDismiss = { pendingInstallModule = null },
            onConfirm = { startInstall(merged) }
        )
    }

    if (installDialogVisible) {
        RuntimeRepositoryInstallDialog(
            running = installRunning,
            success = installSuccess,
            logLines = installLog,
            onClose = { if (!installRunning) installDialogVisible = false },
            onReboot = {
                if (!installRunning) {
                    scope.launch(Dispatchers.IO) { RootUtils.reboot() }
                }
            }
        )
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
                    title = runtimeRepoTitleLabel(context),
                    scrollBehavior = scrollBehavior,
                    enableBlur = state.blurEnabled,
                    actions = {
                        AbkIconButton(AbkIcons.Settings, runtimeRepoConfigureLabel(context), ::openRepositorySettings)
                    }
                )
            }
        ) { topBarHeight ->
            RuntimeModuleRepositoryListContent(
                topBarHeight = topBarHeight,
                modules = filteredModules,
                totalModules = mergedModules.size,
                computing = listComputing,
                repositories = state.runtimeModuleRepositories,
                refreshing = state.refreshingRuntimeModuleRepositoryIds.isNotEmpty(),
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onOpenRepositorySettings = ::openRepositorySettings,
                onOpenModule = { module ->
                    val url = module.module.preferredOpenUrl()
                    if (url.isBlank()) {
                        Toast.makeText(context, context.getString(R.string.module_repo_open_failed), Toast.LENGTH_SHORT).show()
                    } else {
                        runCatching { uriHandler.openUri(url) }
                            .onFailure {
                                Toast.makeText(context, context.getString(R.string.module_repo_open_failed), Toast.LENGTH_SHORT).show()
                            }
                    }
                },
                onInstallModule = { module ->
                    if (module.module.zipUrl.isBlank()) {
                        Toast.makeText(context, runtimeRepoNoZipLabel(context), Toast.LENGTH_SHORT).show()
                    } else {
                        pendingInstallModule = module
                    }
                },
                scrollBehavior = scrollBehavior,
                bottomPadding = outerPadding.calculateBottomPadding()
            )
        }

        repositoryPageTransition.AnimatedVisibility(
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

        repositoryPageTransition.AnimatedVisibility(
            visible = { it },
            enter = childPageOverlayEnterTransition(state.predictiveBackEnabled, motionScheme),
            exit = childPageOverlayExitTransition(state.predictiveBackEnabled, motionScheme),
            modifier = childPageModifier
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(childPageBack.backTransformModifier())
            ) {
                ModuleRepositoryPageBackground(
                    backgroundUri = state.customBackgroundUri,
                    backgroundImageEnabled = state.backgroundImageEnabled
                )
                BlurScreenScaffold(
                    blurConfig = state.blurConfig,
                    containerColor = Color.Transparent,
                    topBar = {
                        ExpressiveTopBar(
                            title = runtimeRepoCentralLabel(context),
                            navigationIcon = {
                                AbkIconButton(AbkIcons.Back, stringResource(R.string.module_repo_back), childPageBack::requestDismiss)
                            },
                            enableBlur = state.blurEnabled
                        )
                    }
                ) { topBarHeight ->
                    RuntimeModuleRepositorySettingsPage(
                        topBarHeight = topBarHeight,
                        repositories = state.runtimeModuleRepositories,
                        refreshingRepositoryIds = state.refreshingRuntimeModuleRepositoryIds,
                        onAddRepository = vm::addRuntimeModuleRepository,
                        onRefreshAll = vm::refreshAllRuntimeModuleRepositories,
                        onRefreshRepository = vm::refreshRuntimeModuleRepository,
                        onDeleteRepository = vm::deleteRuntimeModuleRepository
                    )
                }
            }
        }
    }
}

@Composable
private fun BuildModuleRepositoryScreenContent(
    vm: MainViewModel,
    outerPadding: PaddingValues,
    onRepositoryPageVisibleChange: (Boolean) -> Unit
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val motionScheme = MaterialTheme.motionScheme
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showRepositorySettings by rememberSaveable { mutableStateOf(false) }
    val repositoryPageTransition = rememberChildPageOverlayTransition(
        visible = showRepositorySettings,
        label = "build-module-repository-settings"
    )
    var pendingCatalogModule by remember { mutableStateOf<ModuleCatalogItem?>(null) }
    var selectedCatalogModuleStages by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var pendingModuleSetMetadata by remember { mutableStateOf<ExternalModuleMetadata?>(null) }
    var selectedModuleSetChildren by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var moduleSetStageSelections by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    val mergedModulesState by produceState(
        initialValue = ModuleListComputation<BuildPageMergedCatalogModule>(
            loading = state.buildModuleRepositories.isNotEmpty()
        ),
        key1 = state.buildModuleRepositories
    ) {
        if (state.buildModuleRepositories.isEmpty()) {
            value = ModuleListComputation(items = emptyList(), loading = false)
            return@produceState
        }
        value = ModuleListComputation(loading = true)
        val merged = withContext(Dispatchers.Default) {
            mergeBuildPageCatalogModules(state.buildModuleRepositories)
        }
        value = ModuleListComputation(items = merged, loading = false)
    }
    val mergedModules = mergedModulesState.items
    val filteredModulesState by produceState(
        initialValue = ModuleListComputation<BuildPageMergedCatalogModule>(
            loading = mergedModulesState.loading
        ),
        key1 = mergedModulesState,
        key2 = searchQuery
    ) {
        if (mergedModulesState.loading) {
            value = ModuleListComputation(loading = true)
            return@produceState
        }
        value = ModuleListComputation(loading = true)
        val filtered = withContext(Dispatchers.Default) {
            if (searchQuery.isBlank()) {
                mergedModules
            } else {
                mergedModules.filter { it.matchesQuery(searchQuery) }
            }
        }
        value = ModuleListComputation(items = filtered, loading = false)
    }
    val filteredModules = filteredModulesState.items
    val listComputing = mergedModulesState.loading || filteredModulesState.loading
    val selectedModules = remember(state.buildConfig.customExternalModules) {
        state.buildConfig.customExternalModules
            .map { it.url.trim().lowercase() to CustomExternalModuleStage.normalize(it.stage) }
            .toSet()
    }

    fun closeRepositorySettings() {
        showRepositorySettings = false
    }

    val childPageBack = rememberChildPageBackController(
        enabled = showRepositorySettings,
        predictiveBackEnabled = state.predictiveBackEnabled,
        onBack = ::closeRepositorySettings,
    )

    fun openRepositorySettings() {
        childPageBack.resetProgress()
        showRepositorySettings = true
    }

    ObserveChildPageVisibility(
        transition = repositoryPageTransition,
        onVisibleChange = onRepositoryPageVisibleChange,
        onAfterExitAnimation = { childPageBack.resetProgress() }
    )

    DisposableEffect(Unit) {
        onDispose { onRepositoryPageVisibleChange(false) }
    }

    pendingCatalogModule?.let { module ->
        fun dismissSelection() {
            pendingCatalogModule = null
            pendingModuleSetMetadata = null
            selectedCatalogModuleStages = emptyList()
            selectedModuleSetChildren = emptyList()
            moduleSetStageSelections = emptyMap()
        }
        if (module.kind == ModuleCatalogItemKind.MODULE_SET) {
            val metadata = pendingModuleSetMetadata
            if (metadata != null) {
                val children = metadata.children
                val canAdd = selectedModuleSetChildren.isNotEmpty() && children
                    .filter { it.id in selectedModuleSetChildren }.all { child ->
                        val stages = moduleSetStageSelections[child.id] ?: child.recommendedStages
                            .filter { it in child.supportedStages }.ifEmpty { listOf(child.defaultStage) }
                        stages.any { it in child.supportedStages }
                    }
                AbkDialog(
                    title = module.buildDisplayName(), onDismissRequest = ::dismissSelection,
                    dismissDescription = stringResource(R.string.close), wide = true,
                    actions = {
                        AbkButton(::dismissSelection, style = AbkButtonStyle.Ghost) { Text(stringResource(R.string.cancel)) }
                        AbkButton(onClick = {
                            val selections = children.filter { it.id in selectedModuleSetChildren }.map { child ->
                                child to (moduleSetStageSelections[child.id]?.distinct()
                                    ?.filter { it in child.supportedStages }
                                    ?: child.recommendedStages.filter { it in child.supportedStages }
                                        .ifEmpty { listOf(child.defaultStage) })
                            }.filter { (_, stages) -> stages.isNotEmpty() }
                            if (vm.replaceModuleSetSelection(module.repoUrl, metadata, selections)) {
                                dismissSelection()
                                Toast.makeText(context, context.getString(R.string.module_repo_added_to_build), Toast.LENGTH_SHORT).show()
                            }
                        }, style = AbkButtonStyle.Primary, enabled = canAdd) {
                            Text(stringResource(R.string.module_repo_add_selected))
                        }
                    }
                ) {
                    Text(module.description.ifBlank { context.getString(R.string.module_repo_add_to_build) })
                    children.forEach { child ->
                        val selected = child.id in selectedModuleSetChildren
                        AbkCheckbox(if (selected) ToggleableState.On else ToggleableState.Off, {
                            selectedModuleSetChildren = if (selected) selectedModuleSetChildren - child.id
                                else (selectedModuleSetChildren + child.id).distinct()
                        }, label = child.name, description = child.description.takeIf { it.isNotBlank() })
                        if (selected) {
                            val options = child.supportedStages
                            val stages = moduleSetStageSelections[child.id] ?: child.recommendedStages
                                .filter { it in options }.ifEmpty { listOf(child.defaultStage) }
                            Column(Modifier.padding(start = 16.dp)) {
                                options.forEach { stage ->
                                    AbkCheckbox(if (stage in stages) ToggleableState.On else ToggleableState.Off, {
                                        val updated = if (stage in stages) stages - stage else (stages + stage).distinct()
                                        moduleSetStageSelections = moduleSetStageSelections + (child.id to updated)
                                    }, label = stage + if (stage in child.recommendedStages) stringResource(R.string.module_repo_recommended) else "")
                                }
                            }
                        }
                        AbkDivider()
                    }
                }
            }
        } else {
            val supportedStages = module.buildNormalizedSupportedStages()
            val recommendedStages = module.buildNormalizedRecommendedStages().toSet()
            val addedStages = module.addedStages(selectedModules).toSet()
            val selectedStages = supportedStages.filter { it in selectedCatalogModuleStages && it !in addedStages }
            AbkDialog(
                title = stringResource(R.string.module_repo_select_stage), onDismissRequest = ::dismissSelection,
                dismissDescription = stringResource(R.string.close),
                actions = {
                    AbkButton(::dismissSelection, style = AbkButtonStyle.Ghost) { Text(stringResource(R.string.cancel)) }
                    AbkButton(onClick = {
                        val remaining = supportedStages.filterNot { it in addedStages }
                        if (vm.addCustomExternalModulesFromUrl(module.repoUrl, remaining)) {
                            dismissSelection()
                            Toast.makeText(context, context.getString(R.string.module_repo_added_to_build), Toast.LENGTH_SHORT).show()
                        }
                    }, style = AbkButtonStyle.Outline, enabled = supportedStages.any { it !in addedStages }) {
                        Text(stringResource(R.string.module_repo_all_stages))
                    }
                    AbkButton(onClick = {
                        if (vm.addCustomExternalModulesFromUrl(module.repoUrl, selectedStages)) {
                            dismissSelection()
                            Toast.makeText(context, context.getString(R.string.module_repo_added_to_build), Toast.LENGTH_SHORT).show()
                        }
                    }, style = AbkButtonStyle.Primary, enabled = selectedStages.isNotEmpty()) {
                        Text(stringResource(R.string.module_repo_add_selected))
                    }
                }
            ) {
                Text(module.buildDisplayName(), style = MaterialTheme.typography.titleMedium)
                val meta = module.buildMetaLine()
                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = AbkTheme.colors.inkMuted)
                if (module.description.isNotBlank()) Text(module.description)
                supportedStages.forEach { stage ->
                    val added = stage in addedStages
                    AbkCheckbox(if (added || stage in selectedCatalogModuleStages) ToggleableState.On else ToggleableState.Off, {
                        selectedCatalogModuleStages = if (stage in selectedCatalogModuleStages) selectedCatalogModuleStages - stage
                            else (selectedCatalogModuleStages + stage).distinct()
                    }, label = buildString {
                        append(stage)
                        if (stage in recommendedStages) append(stringResource(R.string.module_repo_recommended))
                        if (added) append(stringResource(R.string.module_repo_added_suffix))
                    }, enabled = !added)
                }
            }
        }
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
                    title = buildRepoTitleLabel(context),
                    scrollBehavior = scrollBehavior,
                    enableBlur = state.blurEnabled,
                    actions = {
                        AbkIconButton(AbkIcons.Settings, buildRepoManageLabel(context), ::openRepositorySettings)
                    }
                )
            }
        ) { topBarHeight ->
            BuildModuleRepositoryListContent(
                topBarHeight = topBarHeight,
                modules = filteredModules,
                totalModules = mergedModules.size,
                computing = listComputing,
                repositories = state.buildModuleRepositories,
                refreshing = state.refreshingBuildModuleRepositoryIds.isNotEmpty(),
                selectedModules = selectedModules,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onOpenRepositorySettings = ::openRepositorySettings,
                onAddModule = { module ->
                    if (module.kind == ModuleCatalogItemKind.MODULE_SET) {
                        coroutineScope.launch {
                            val metadata = vm.checkCustomExternalModuleMetadata(module.repoUrl)
                            if (metadata != null) {
                                pendingCatalogModule = module
                                pendingModuleSetMetadata = metadata
                                selectedModuleSetChildren = metadata.children.map { it.id }
                                moduleSetStageSelections = metadata.children.associate { child ->
                                    child.id to child.recommendedStages
                                        .filter { stage -> stage in child.supportedStages }
                                        .ifEmpty { listOf(child.defaultStage) }
                                }
                            }
                        }
                    } else {
                        pendingCatalogModule = module
                        selectedCatalogModuleStages = module.initialStageSelection(selectedModules)
                    }
                },
                onOpenModule = { module ->
                    val url = module.homepage.ifBlank { module.repoUrl }
                    runCatching { uriHandler.openUri(url) }
                        .onFailure { Toast.makeText(context, context.getString(R.string.module_repo_open_failed), Toast.LENGTH_SHORT).show() }
                },
                scrollBehavior = scrollBehavior,
                bottomPadding = outerPadding.calculateBottomPadding()
            )
        }

        repositoryPageTransition.AnimatedVisibility(
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

        repositoryPageTransition.AnimatedVisibility(
            visible = { it },
            enter = childPageOverlayEnterTransition(state.predictiveBackEnabled, motionScheme),
            exit = childPageOverlayExitTransition(state.predictiveBackEnabled, motionScheme),
            modifier = childPageModifier
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(childPageBack.backTransformModifier())
            ) {
                ModuleRepositoryPageBackground(
                    backgroundUri = state.customBackgroundUri,
                    backgroundImageEnabled = state.backgroundImageEnabled
                )
                BlurScreenScaffold(
                    blurConfig = state.blurConfig,
                    containerColor = Color.Transparent,
                    topBar = {
                        ExpressiveTopBar(
                            title = buildRepoCentralLabel(context),
                            navigationIcon = {
                                AbkIconButton(AbkIcons.Back, stringResource(R.string.module_repo_back), childPageBack::requestDismiss)
                            },
                            enableBlur = state.blurEnabled
                        )
                    }
                ) { topBarHeight ->
                    BuildModuleRepositorySettingsPage(
                        topBarHeight = topBarHeight,
                        repositories = state.buildModuleRepositories,
                        refreshingRepositoryIds = state.refreshingBuildModuleRepositoryIds,
                        onAddRepository = vm::addBuildModuleRepository,
                        onRefreshAll = vm::refreshAllBuildModuleRepositories,
                        onRefreshRepository = vm::refreshBuildModuleRepository,
                        onDeleteRepository = vm::deleteBuildModuleRepository
                    )
                }
            }
        }
    }
}

@Composable
private fun RuntimeModuleRepositoryListContent(
    topBarHeight: Dp,
    modules: List<MergedRuntimeCatalogModule>,
    totalModules: Int,
    computing: Boolean,
    repositories: List<RuntimeModuleRepository>,
    refreshing: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenRepositorySettings: () -> Unit,
    onOpenModule: (MergedRuntimeCatalogModule) -> Unit,
    onInstallModule: (MergedRuntimeCatalogModule) -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    bottomPadding: Dp
) {
    val showInitialLoading = computing || (refreshing && totalModules == 0 && searchQuery.isBlank())
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = topBarHeight + 16.dp, bottom = bottomPadding + 24.dp)
    ) {
        item(key = "protocol") {
            AbkHero(title = runtimeRepoTitleLabel(LocalContext.current),
                description = stringResource(R.string.ui_module_runtime_protocol))
        }
        item(key = "search") {
            CompactModuleSearchField(
                value = searchQuery,
                onValueChange = onSearchQueryChange
            )
        }

        if (refreshing || showInitialLoading) {
            item(key = "loading") {
                ModuleRepositoryLoading(
                    label = stringResource(
                        if (refreshing) R.string.module_repo_refreshing_repository
                        else R.string.module_repo_building_list
                    )
                )
            }
        }
        if (!showInitialLoading && modules.isEmpty()) {
            item(key = "empty") {
                RuntimeModuleRepositoryEmptyState(
                    totalModules = totalModules,
                    repositoryCount = repositories.size,
                    hasQuery = searchQuery.isNotBlank(),
                    onOpenRepositorySettings = onOpenRepositorySettings
                )
            }
        } else if (!showInitialLoading) {
            items(
                items = modules,
                key = { merged ->
                    "${merged.module.id.trim().lowercase().ifBlank { merged.module.name.trim().lowercase() }}-${merged.sources.joinToString("|")}"
                }
            ) { merged ->
                RuntimeModuleRepositoryListItem(
                    merged = merged,
                    onOpen = { onOpenModule(merged) },
                    onInstall = { onInstallModule(merged) }
                )
            }
        }
    }
}

@Composable
private fun ModuleRepositoryLoading(label: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AbkSpinner(label = label)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RuntimeModuleRepositoryEmptyState(
    totalModules: Int,
    repositoryCount: Int,
    hasQuery: Boolean,
    onOpenRepositorySettings: () -> Unit
) {
    val context = LocalContext.current
    AbkEmptyState(
        title = when {
            hasQuery -> stringResource(R.string.module_repo_no_matching)
            repositoryCount == 0 -> runtimeRepoEmptyTitleLabel(context)
            totalModules == 0 -> runtimeRepoEmptyDescLabel(context)
            else -> stringResource(R.string.module_repo_no_display)
        }, description = stringResource(R.string.ui_module_runtime_protocol), icon = AbkIcons.Modules,
        actions = {
            AbkButton(onOpenRepositorySettings, style = AbkButtonStyle.Outline) {
                AbkIcon(AbkIcons.Settings, null)
                Spacer(Modifier.width(8.dp))
                Text(runtimeRepoManageLabel(context))
            }
        }
    )
}

@Composable
private fun RuntimeModuleRepositoryListItem(
    merged: MergedRuntimeCatalogModule,
    onOpen: () -> Unit,
    onInstall: () -> Unit
) {
    val module = merged.module
    AbkCard(
        modifier = Modifier.fillMaxWidth().blurredCardBackground(AbkShapes.Large),
        containerColor = blurredCardSurfaceColor(AbkTheme.colors.raised)
    ) {
        Text(module.name.ifBlank { module.id }, style = MaterialTheme.typography.titleMedium)
        val meta = module.metaLine()
        if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = AbkTheme.colors.inkMuted)
        if (module.description.isNotBlank()) Text(module.description, style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ModuleTagChip("ZIP")
            ModuleTagChip(module.id.ifBlank { module.name })
            module.minApi?.let { ModuleTagChip("API ≥ $it", secondary = true) }
            module.maxApi?.let { ModuleTagChip("API ≤ $it", secondary = true) }
            if (module.verified) ModuleTagChip(runtimeRepoVerifiedLabel(LocalContext.current), secondary = true)
        }
        Text(merged.sources.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = AbkTheme.colors.inkMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AbkButton(onOpen, style = AbkButtonStyle.Outline) {
                AbkIcon(AbkIcons.Forward, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.module_repo_open_repo))
            }
            AbkButton(onInstall, style = AbkButtonStyle.Primary, enabled = module.zipUrl.isNotBlank()) {
                AbkIcon(AbkIcons.Upload, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.runtime_install_module))
            }
        }
    }
}

@Composable
private fun CompactModuleSearchField(
    value: String,
    onValueChange: (String) -> Unit
) {
    AbkTextField(value, onValueChange, label = stringResource(R.string.module_repo_search),
        modifier = Modifier.fillMaxWidth(), prefix = { AbkIcon(AbkIcons.Search, null) })
}

@Composable
private fun ModuleTagChip(label: String, secondary: Boolean = false) {
    AbkBadge(label, tone = AbkTone.Neutral, dot = !secondary)
}

@Composable
private fun RuntimeModuleRepositorySettingsPage(
    topBarHeight: Dp,
    repositories: List<RuntimeModuleRepository>,
    refreshingRepositoryIds: Set<String>,
    onAddRepository: (String) -> Unit,
    onRefreshAll: () -> Unit,
    onRefreshRepository: (String) -> Unit,
    onDeleteRepository: (String) -> Unit
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
        .padding(horizontal = AbkScreenHorizontalPadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(topBarHeight + 16.dp))
        RepositorySourceEditor(runtimeRepoCentralLabel(context), runtimeRepoCentralDescLabel(context),
            runtimeRepoUrlLabel(context), "https://example.com/modules.json", repositories.isNotEmpty(),
            refreshingRepositoryIds.isNotEmpty(), onAddRepository, onRefreshAll)
        if (repositories.isEmpty()) AbkEmptyState(runtimeRepoEmptyTitleLabel(context),
            runtimeRepoEmptyDescLabel(context), icon = AbkIcons.Modules)
        repositories.forEach { repository ->
            RepositorySourceCard(repository.name.ifBlank { repository.url }, repository.url,
                repository.indexJsonUrl.ifBlank { repository.url }, repository.modules.size,
                repository.skippedCount, repository.error, repository.id in refreshingRepositoryIds,
                { onRefreshRepository(repository.id) }, { onDeleteRepository(repository.id) })
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RepositorySourceEditor(
    title: String,
    description: String,
    label: String,
    placeholder: String,
    hasRepositories: Boolean,
    refreshing: Boolean,
    onAdd: (String) -> Unit,
    onRefreshAll: () -> Unit
) {
    var repositoryUrl by rememberSaveable { mutableStateOf("") }
    ExpressiveSectionCard(title, subtitle = description, icon = AbkIcons.Modules) {
        AbkTextField(repositoryUrl, { repositoryUrl = it }, label = label,
            placeholder = placeholder, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AbkButton({ onAdd(repositoryUrl); repositoryUrl = "" }, style = AbkButtonStyle.Primary,
                enabled = repositoryUrl.isNotBlank()) {
                AbkIcon(AbkIcons.Plus, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add))
            }
            AbkButton(onRefreshAll, style = AbkButtonStyle.Outline, enabled = hasRepositories && !refreshing,
                loading = refreshing, loadingDescription = stringResource(R.string.module_repo_refreshing_repository)) {
                AbkIcon(AbkIcons.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.refresh_all))
            }
        }
    }
}

@Composable
private fun RepositorySourceCard(
    title: String,
    url: String,
    indexUrl: String,
    moduleCount: Int,
    skippedCount: Int,
    error: String?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    ExpressiveSectionCard(title, subtitle = url, icon = AbkIcons.Modules) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpressiveStatusChip(stringResource(R.string.module_repo_module_count, moduleCount), icon = AbkIcons.Modules)
            if (skippedCount > 0) ExpressiveStatusChip(stringResource(R.string.module_repo_skipped_count, skippedCount),
                icon = AbkIcons.Warning, color = AbkTheme.colors.warning.color)
        }
        if (error != null) AbkAlert(stringResource(R.string.ui_module_source_error), error, tone = AbkTone.Danger)
        Text(indexUrl, style = MaterialTheme.typography.bodySmall, color = AbkTheme.colors.inkMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AbkButton(onRefresh, style = AbkButtonStyle.Outline, enabled = !refreshing,
                loading = refreshing, loadingDescription = stringResource(R.string.module_repo_refreshing_repository)) {
                AbkIcon(AbkIcons.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.refresh))
            }
            AbkButton(onDelete, style = AbkButtonStyle.Ghost) {
                AbkIcon(AbkIcons.Trash, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.delete))
            }
        }
    }
}

@Composable
private fun RuntimeRepositoryInstallConfirmDialog(
    module: MergedRuntimeCatalogModule,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current
    AbkDialog(
        title = runtimeRepoConfirmInstallTitle(context), onDismissRequest = onDismiss,
        dismissDescription = stringResource(R.string.close),
        actions = {
            AbkButton(onDismiss, style = AbkButtonStyle.Ghost) { Text(stringResource(R.string.cancel)) }
            AbkButton(onConfirm, style = AbkButtonStyle.Primary) {
                AbkIcon(AbkIcons.Upload, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.runtime_install_module))
            }
        }
    ) {
        Text(module.module.name.ifBlank { module.module.id }, style = MaterialTheme.typography.titleMedium)
        module.module.metaLine().takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = AbkTheme.colors.inkMuted)
        }
        Text("${context.getString(R.string.runtime_source)}: ${module.sources.joinToString(" · ").ifBlank { runtimeRepoUnknownSourceLabel(context) }}")
        Text(module.module.zipUrl, style = MaterialTheme.typography.bodySmall, fontFamily = AbkTheme.monoFont)
        if (module.module.description.isNotBlank()) Text(module.module.description)
        AbkAlert(stringResource(R.string.runtime_install_module),
            stringResource(R.string.runtime_confirm_flash_module_desc), tone = AbkTone.Warning)
    }
}

@Composable
internal fun RuntimeRepositoryInstallDialog(
    running: Boolean,
    success: Boolean?,
    logLines: List<String>,
    onClose: () -> Unit,
    onReboot: () -> Unit
) {
    val terminalScroll = rememberScrollState()
    LaunchedEffect(logLines.size) {
        terminalScroll.scrollTo(terminalScroll.maxValue)
    }
    AbkDialog(
        title = stringResource(if (running) R.string.runtime_installing_module else R.string.runtime_install_module),
        onDismissRequest = { if (!running) onClose() }, dismissible = !running,
        dismissDescription = stringResource(R.string.close), wide = true,
        actions = {
            if (!running) {
                AbkButton(onClose, style = AbkButtonStyle.Ghost) { Text(stringResource(R.string.close)) }
                if (success == true) AbkButton(onReboot, style = AbkButtonStyle.Danger) {
                    AbkIcon(AbkIcons.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.runtime_reboot))
                }
            }
        }
    ) {
        if (running) {
            AbkLinearProgress(null, label = stringResource(R.string.runtime_running))
            Text(stringResource(R.string.runtime_running))
        } else if (success != null) {
            ExpressiveStatusChip(
                stringResource(if (success) R.string.ui_module_install_success else R.string.ui_module_install_failure),
                icon = if (success) AbkIcons.Check else AbkIcons.Warning,
                color = if (success) AbkTheme.colors.success.color else AbkTheme.colors.danger.color)
        }
        AbkCard(modifier = Modifier.fillMaxWidth(), style = AbkCardStyle.Sunken) {
            Column(Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp).verticalScroll(terminalScroll),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                logLines.ifEmpty { listOf(stringResource(R.string.runtime_waiting_output)) }.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = AbkTheme.monoFont)
                }
            }
        }
    }
}

@Composable
private fun ModuleRepositoryPageBackground(
    backgroundUri: String?,
    backgroundImageEnabled: Boolean
) {
    AppPageBackground(
        backgroundUri = backgroundUri,
        backgroundImageEnabled = backgroundImageEnabled
    )
}

private data class MergedRuntimeCatalogModule(
    val module: RuntimeModuleCatalogItem,
    val sources: List<String>
)

private fun mergeRuntimeCatalogModules(repositories: List<RuntimeModuleRepository>): List<MergedRuntimeCatalogModule> =
    repositories
        .flatMap { repository ->
            repository.modules.map { module -> repository.name.ifBlank { repository.url } to module }
        }
        .groupBy { (_, module) -> module.id.trim().lowercase().ifBlank { module.name.trim().lowercase() } }
        .values
        .map { entries ->
            MergedRuntimeCatalogModule(
                module = entries.first().second,
                sources = entries.map { it.first }.distinct()
            )
        }
        .sortedBy { it.module.name.lowercase() }

private fun MergedRuntimeCatalogModule.matchesQuery(query: String): Boolean {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) return true
    val module = module
    return listOf(
        module.id,
        module.name,
        module.version,
        module.author,
        module.description,
        module.support,
        module.website,
        module.zipUrl,
        sources.joinToString(" ")
    ).any { it.contains(cleanQuery, ignoreCase = true) }
}

@Composable
private fun RuntimeModuleCatalogItem.metaLine(): String =
    listOfNotNull(
        version.takeIf { it.isNotBlank() }?.let { stringResource(R.string.module_repo_version, it) },
        author.takeIf { it.isNotBlank() }?.let { stringResource(R.string.runtime_module_author, it) }
    ).joinToString("\n")

private fun RuntimeModuleCatalogItem.preferredOpenUrl(): String =
    support.takeIf { it.isNotBlank() }
        ?: website.takeIf { it.isNotBlank() }
        ?: donate.takeIf { it.isNotBlank() }
        ?: zipUrl

private fun String.repoName(): String =
    trim()
        .trimEnd('/')
        .removeSuffix(".git")
        .substringAfterLast('/')
        .ifBlank { trim().trimEnd('/').substringAfterLast('/') }

private data class BuildPageMergedCatalogModule(
    val module: ModuleCatalogItem,
    val sources: List<String>
)

private fun mergeBuildPageCatalogModules(repositories: List<ModuleCatalogRepository>): List<BuildPageMergedCatalogModule> =
    repositories
        .flatMap { repository ->
            repository.modules.map { module -> repository.name.ifBlank { repository.url } to module }
        }
        .groupBy { (_, module) -> module.repoUrl.trim().lowercase() }
        .values
        .map { entries ->
            BuildPageMergedCatalogModule(
                module = entries.first().second,
                sources = entries.map { it.first }.distinct()
            )
        }
        .sortedBy { it.module.buildDisplayName().lowercase() }

private fun BuildPageMergedCatalogModule.matchesQuery(query: String): Boolean {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) return true
    val item = module
    return listOf(
        item.name,
        item.version,
        item.description,
        item.repoUrl,
        item.author,
        item.homepage,
        sources.joinToString(" ")
    ).any { it.contains(cleanQuery, ignoreCase = true) }
}

@Composable
private fun BuildModuleRepositoryListContent(
    topBarHeight: Dp,
    modules: List<BuildPageMergedCatalogModule>,
    totalModules: Int,
    computing: Boolean,
    repositories: List<ModuleCatalogRepository>,
    refreshing: Boolean,
    selectedModules: Set<Pair<String, String>>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenRepositorySettings: () -> Unit,
    onAddModule: (ModuleCatalogItem) -> Unit,
    onOpenModule: (ModuleCatalogItem) -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    bottomPadding: Dp
) {
    val context = LocalContext.current
    val showInitialLoading = computing || (refreshing && totalModules == 0 && searchQuery.isBlank())
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = topBarHeight + 16.dp, bottom = bottomPadding + 24.dp)
    ) {
        item(key = "protocol") {
            AbkHero(title = buildRepoTitleLabel(context),
                description = stringResource(R.string.ui_module_build_protocol))
        }
        item(key = "search") {
            CompactModuleSearchField(
                value = searchQuery,
                onValueChange = onSearchQueryChange
            )
        }

        if (refreshing || showInitialLoading) {
            item(key = "loading") {
                ModuleRepositoryLoading(
                    label = stringResource(
                        if (refreshing) R.string.module_repo_refreshing_repository
                        else R.string.module_repo_building_list
                    )
                )
            }
        }
        if (!showInitialLoading && modules.isEmpty()) {
            item(key = "empty") {
                AbkEmptyState(
                    title = when {
                        searchQuery.isNotBlank() -> context.getString(R.string.module_repo_no_matching)
                        repositories.isEmpty() -> buildRepoEmptyTitleLabel(context)
                        totalModules == 0 -> context.getString(R.string.module_repo_refresh_hint)
                        else -> context.getString(R.string.module_repo_no_display)
                    }, description = stringResource(R.string.ui_module_build_protocol), icon = AbkIcons.Modules,
                    actions = {
                        AbkButton(onOpenRepositorySettings, style = AbkButtonStyle.Outline) {
                            AbkIcon(AbkIcons.Settings, null)
                            Spacer(Modifier.width(8.dp))
                            Text(buildRepoManageLabel(context))
                        }
                    }
                )
            }
        } else if (!showInitialLoading) {
            items(
                items = modules,
                key = { merged ->
                    merged.module.repoUrl.trim().lowercase()
                }
            ) { merged ->
                val module = merged.module
                val supportedStages = module.buildNormalizedSupportedStages()
                val allStagesAdded = supportedStages.all { stage ->
                    module.repoUrl.trim().lowercase() to stage in selectedModules
                }
                AbkCard(
                    modifier = Modifier.fillMaxWidth().blurredCardBackground(AbkShapes.Large),
                    containerColor = blurredCardSurfaceColor(AbkTheme.colors.raised)
                ) {
                    Text(module.buildDisplayName(), style = MaterialTheme.typography.titleMedium)
                    val meta = module.buildMetaLine()
                    if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = AbkTheme.colors.inkMuted)
                    if (module.description.isNotBlank()) Text(module.description, style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModuleTagChip("module.conf")
                        ModuleTagChip(module.repoUrl.repoName(), secondary = true)
                        supportedStages.forEach { ModuleTagChip(it, secondary = true) }
                        if (allStagesAdded) ExpressiveStatusChip(context.getString(R.string.module_repo_joined), icon = AbkIcons.Check)
                    }
                    Text(merged.sources.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = AbkTheme.colors.inkMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AbkButton({ onOpenModule(module) }, style = AbkButtonStyle.Outline) {
                            AbkIcon(AbkIcons.Forward, null)
                            Spacer(Modifier.width(8.dp))
                            Text(context.getString(R.string.module_repo_open_repo))
                        }
                        AbkButton({ onAddModule(module) }, style = AbkButtonStyle.Primary, enabled = !allStagesAdded) {
                            AbkIcon(if (allStagesAdded) AbkIcons.Check else AbkIcons.Plus, null)
                            Spacer(Modifier.width(8.dp))
                            Text(context.getString(if (allStagesAdded) R.string.module_repo_joined else R.string.module_repo_add_to_build))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildModuleRepositorySettingsPage(
    topBarHeight: Dp,
    repositories: List<ModuleCatalogRepository>,
    refreshingRepositoryIds: Set<String>,
    onAddRepository: (String) -> Unit,
    onRefreshAll: () -> Unit,
    onRefreshRepository: (String) -> Unit,
    onDeleteRepository: (String) -> Unit
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
        .padding(horizontal = AbkScreenHorizontalPadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(topBarHeight + 16.dp))
        RepositorySourceEditor(buildRepoCentralLabel(context), buildRepoCentralDescLabel(context),
            buildRepoUrlLabel(context), "https://github.com/user/abk-module-catalog", repositories.isNotEmpty(),
            refreshingRepositoryIds.isNotEmpty(), onAddRepository, onRefreshAll)
        if (repositories.isEmpty()) AbkEmptyState(buildRepoEmptyTitleLabel(context),
            buildRepoEmptyDescLabel(context), icon = AbkIcons.Modules)
        Text(stringResource(R.string.module_repo_delete_keep_modules), style = MaterialTheme.typography.bodySmall,
            color = AbkTheme.colors.inkMuted)
        repositories.forEach { repository ->
            RepositorySourceCard(repository.name.ifBlank { repository.url }, repository.url,
                repository.indexJsonUrl.ifBlank { repository.url }, repository.modules.size,
                repository.skippedCount, repository.error, repository.id in refreshingRepositoryIds,
                { onRefreshRepository(repository.id) }, { onDeleteRepository(repository.id) })
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun ModuleCatalogItem.buildDisplayName(): String =
    name.ifBlank { repoUrl.trim().trimEnd('/').substringAfterLast('/').removeSuffix(".git") }

@Composable
private fun ModuleCatalogItem.buildMetaLine(): String =
    listOfNotNull(
        version.takeIf { it.isNotBlank() }?.let { stringResource(R.string.module_repo_version, it) },
        author.takeIf { it.isNotBlank() }?.let { stringResource(R.string.runtime_module_author, it) }
    ).joinToString("\n")

private fun ModuleCatalogItem.buildNormalizedSupportedStages(): List<String> =
    supportedStages
        .map { CustomExternalModuleStage.normalize(it) }
        .distinct()
        .ifEmpty { listOf(CustomExternalModuleStage.normalize(defaultStage)) }

private fun ModuleCatalogItem.buildNormalizedRecommendedStages(): List<String> {
    val supportedStages = buildNormalizedSupportedStages()
    val normalizedDefaultStage = CustomExternalModuleStage.normalize(defaultStage)
        .takeIf { it in supportedStages }
        ?: supportedStages.first()
    return recommendedStages
        .map { CustomExternalModuleStage.normalize(it) }
        .distinct()
        .filter { it in supportedStages }
        .ifEmpty { listOf(normalizedDefaultStage) }
}

private fun ModuleCatalogItem.addedStages(selectedModules: Set<Pair<String, String>>): List<String> {
    val moduleUrl = repoUrl.trim().lowercase()
    return buildNormalizedSupportedStages().filter { stage -> moduleUrl to stage in selectedModules }
}

private fun ModuleCatalogItem.initialStageSelection(selectedModules: Set<Pair<String, String>>): List<String> {
    val moduleUrl = repoUrl.trim().lowercase()
    val remainingRecommendedStages = buildNormalizedRecommendedStages().filterNot { stage ->
        moduleUrl to stage in selectedModules
    }
    val remainingSupportedStages = buildNormalizedSupportedStages().filterNot { stage ->
        moduleUrl to stage in selectedModules
    }
    return remainingRecommendedStages
        .ifEmpty { remainingSupportedStages.take(1) }
        .ifEmpty { buildNormalizedRecommendedStages() }
}

private fun buildRepoTitleLabel(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "ABK 模块仓库"
        LocaleHelper.LANG_RU -> "Репозиторий модулей ABK"
        else -> "ABK Module Repo"
    }

private fun buildRepoCentralLabel(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "ABK 模块中央仓库"
        LocaleHelper.LANG_RU -> "Центральный репозиторий модулей ABK"
        else -> "ABK module central repository"
    }

private fun buildRepoCentralDescLabel(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "添加包含 abk-modules.json 的 ABK 模块仓库。"
        LocaleHelper.LANG_RU -> "Добавьте репозитории модулей ABK, содержащие abk-modules.json."
        else -> "Add ABK module repositories that contain abk-modules.json."
    }

private fun buildRepoManageLabel(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "管理 ABK 模块仓库"
        LocaleHelper.LANG_RU -> "Управление репозиториями модулей ABK"
        else -> "Manage ABK module repositories"
    }

private fun buildRepoUrlLabel(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "ABK 模块仓库链接"
        LocaleHelper.LANG_RU -> "Ссылка на репозиторий модулей ABK"
        else -> "ABK module repository URL"
    }

private fun buildRepoEmptyTitleLabel(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "暂无 ABK 模块仓库"
        LocaleHelper.LANG_RU -> "Нет репозиториев модулей ABK"
        else -> "No ABK module repositories"
    }

private fun buildRepoEmptyDescLabel(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "添加 ABK 模块仓库后，模块会在这里合并展示并支持加入构建配置。"
        LocaleHelper.LANG_RU -> "После добавления репозитория модулей ABK они будут показаны здесь и смогут добавляться в конфигурацию сборки."
        else -> "After adding an ABK module repository, modules are merged here and can be added to the build configuration."
    }

private fun runtimeRepoTitleLabel(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "普通模块仓库"
        LocaleHelper.LANG_RU -> "Репозиторий обычных модулей"
        else -> "Standard Module Repo"
    }

private fun runtimeRepoConfigureLabel(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "配置普通模块仓库"
        LocaleHelper.LANG_RU -> "Настроить репозитории обычных модулей"
        else -> "Configure standard module repositories"
    }

private fun runtimeRepoCentralLabel(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "普通模块中央仓库"
        LocaleHelper.LANG_RU -> "Центральный репозиторий обычных модулей"
        else -> "Standard module central repository"
    }

private fun runtimeRepoCentralDescLabel(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "添加符合 Magisk 标准 JSON 格式的普通模块仓库。"
        LocaleHelper.LANG_RU -> "Добавьте репозиторий обычных модулей в стандартном формате JSON Magisk."
        else -> "Add standard module repositories that expose the Magisk JSON format."
    }

private fun runtimeRepoUrlLabel(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "普通模块仓库链接"
        LocaleHelper.LANG_RU -> "Ссылка на репозиторий обычных модулей"
        else -> "Standard module repository URL"
    }

private fun runtimeRepoEmptyTitleLabel(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "暂无普通模块仓库"
        LocaleHelper.LANG_RU -> "Нет репозиториев обычных модулей"
        else -> "No standard module repositories"
    }

private fun runtimeRepoEmptyDescLabel(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "添加普通模块仓库后，模块会在这里合并展示并支持下载安装。"
        LocaleHelper.LANG_RU -> "После добавления репозитория обычных модулей они будут показаны здесь и смогут скачиваться для установки."
        else -> "After adding a standard module repository, modules are merged here and can be downloaded for installation."
    }

private fun runtimeRepoManageLabel(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "管理普通模块仓库"
        LocaleHelper.LANG_RU -> "Управление репозиториями обычных модулей"
        else -> "Manage standard module repositories"
    }

private fun runtimeRepoDownloadingLabel(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "正在下载模块..."
        LocaleHelper.LANG_RU -> "Скачивание модуля…"
        else -> "Downloading module..."
    }

private fun runtimeRepoDownloadFailedLabel(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "模块下载失败"
        LocaleHelper.LANG_RU -> "Не удалось скачать модуль"
        else -> "Module download failed"
    }

private fun runtimeRepoUnknownSourceLabel(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "未知来源"
        LocaleHelper.LANG_RU -> "Неизвестный источник"
        else -> "Unknown source"
    }

private fun runtimeRepoNoZipLabel(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "该模块仓库项没有可安装的 ZIP 链接"
        LocaleHelper.LANG_RU -> "У этой записи репозитория нет ZIP для установки"
        else -> "This repository entry does not expose an installable ZIP URL"
    }

private fun runtimeRepoVerifiedLabel(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "已验证"
        LocaleHelper.LANG_RU -> "Проверен"
        else -> "Verified"
    }

private fun runtimeRepoConfirmInstallTitle(context: Context): String =
    when (LocaleHelper.getLanguage(context)) {
        LocaleHelper.LANG_ZH -> "确认下载安装"
        LocaleHelper.LANG_RU -> "Подтвердить скачивание и установку"
        else -> "Confirm download and install"
    }
