@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.abk.kernel.ui.components.ShimmerLinearProgress
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.abk.kernel.R
import com.abk.kernel.data.model.CustomExternalModuleStage
import com.abk.kernel.data.model.ExternalModuleMetadata
import com.abk.kernel.data.model.ModuleCatalogItemKind
import com.abk.kernel.data.model.ModuleCatalogItem
import com.abk.kernel.data.model.ModuleCatalogRepository
import com.abk.kernel.data.model.RuntimeModuleCatalogItem
import com.abk.kernel.data.model.RuntimeModuleRepository
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
import com.abk.kernel.utils.LocaleHelper
import com.abk.kernel.utils.DownloadUtils
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
    val listComputing = mergedModulesState.loading

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
                DownloadUtils.downloadDirectAsset(
                    context = context,
                    token = null,
                    url = module.module.zipUrl,
                    name = downloadName,
                    sizeBytes = 0L,
                    runId = RUNTIME_MODULE_DOWNLOAD_RUN_ID,
                    runTitle = module.sources.firstOrNull().orEmpty().ifBlank {
                        runtimeRepoUnknownSourceLabel(context)
                    },
                    downloadDirectoryPath = state.downloadDirectory
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

        Scaffold(
            containerColor = appPageBackgroundColor(uiSurfaceColor(MaterialTheme.colorScheme.surface)),
            topBar = {
                ExpressiveTopBar(
                    title = runtimeRepoTitleLabel(context),
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = ::openRepositorySettings) {
                            Icon(
                                Icons.Default.Dns,
                                contentDescription = runtimeRepoConfigureLabel(context)
                            )
                        }
                    }
                )
            }
        ) { padding ->
            RuntimeModuleRepositoryListContent(
                padding = padding,
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
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        ExpressiveTopBar(
                            title = runtimeRepoCentralLabel(context),
                            navigationIcon = {
                                IconButton(onClick = childPageBack::requestDismiss) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.module_repo_back))
                                }
                            }
                        )
                    }
                ) { padding ->
                    RuntimeModuleRepositorySettingsPage(
                        padding = padding,
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
    val listComputing = mergedModulesState.loading
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
        if (module.kind == ModuleCatalogItemKind.MODULE_SET) {
            val metadata = pendingModuleSetMetadata
            if (metadata != null) {
                val children = metadata.children
                val addToBuildLabel = stringResource(R.string.module_repo_add_to_build)
                AlertDialog(
                    onDismissRequest = {
                        pendingCatalogModule = null
                        pendingModuleSetMetadata = null
                        selectedModuleSetChildren = emptyList()
                        moduleSetStageSelections = emptyMap()
                    },
                    icon = { Icon(Icons.Default.Extension, null) },
                    title = { Text(module.buildDisplayName()) },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = if (module.description.isNotBlank()) module.description else addToBuildLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            children.forEach { child ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = child.id in selectedModuleSetChildren,
                                        onCheckedChange = { checked ->
                                            selectedModuleSetChildren = if (checked) {
                                                (selectedModuleSetChildren + child.id).distinct()
                                            } else {
                                                selectedModuleSetChildren - child.id
                                            }
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(child.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        if (child.description.isNotBlank()) {
                                            Text(
                                                child.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (child.id in selectedModuleSetChildren) {
                                            val options = child.supportedStages
                                            val initialStages = child.recommendedStages
                                                .filter { it in options }
                                                .ifEmpty { listOf(child.defaultStage) }
                                            val selectedStages = moduleSetStageSelections[child.id] ?: initialStages
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                options.forEach { stage ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Checkbox(
                                                            checked = stage in selectedStages,
                                                            onCheckedChange = { checked ->
                                                                val updatedStages = if (checked) {
                                                                    (selectedStages + stage).distinct()
                                                                } else {
                                                                    selectedStages - stage
                                                                }
                                                                moduleSetStageSelections = moduleSetStageSelections + (child.id to updatedStages)
                                                            }
                                                        )
                                                        Text(
                                                            text = buildString {
                                                                append(stage)
                                                                if (stage in child.recommendedStages) {
                                                                    append(stringResource(R.string.module_repo_recommended))
                                                                }
                                                            },
                                                            style = MaterialTheme.typography.bodySmall
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val selections = children
                                    .filter { it.id in selectedModuleSetChildren }
                                    .map { child ->
                                        child to (
                                            moduleSetStageSelections[child.id]
                                                ?.distinct()
                                                ?.filter { stage -> stage in child.supportedStages }
                                                ?.ifEmpty {
                                                    child.recommendedStages
                                                        .filter { stage -> stage in child.supportedStages }
                                                        .ifEmpty { listOf(child.defaultStage) }
                                                }
                                                ?: child.recommendedStages
                                                    .filter { stage -> stage in child.supportedStages }
                                                    .ifEmpty { listOf(child.defaultStage) }
                                        )
                                    }
                                    .filter { (_, stages) -> stages.isNotEmpty() }
                                if (vm.replaceModuleSetSelection(module.repoUrl, metadata, selections)) {
                                    pendingCatalogModule = null
                                    pendingModuleSetMetadata = null
                                    selectedModuleSetChildren = emptyList()
                                    moduleSetStageSelections = emptyMap()
                                    Toast.makeText(context, context.getString(R.string.module_repo_added_to_build), Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = selectedModuleSetChildren.isNotEmpty() && children
                                .filter { it.id in selectedModuleSetChildren }
                                .all { child ->
                                    val selectedStages = moduleSetStageSelections[child.id]
                                        ?: child.recommendedStages
                                            .filter { stage -> stage in child.supportedStages }
                                            .ifEmpty { listOf(child.defaultStage) }
                                    selectedStages.any { stage -> stage in child.supportedStages }
                                }
                        ) {
                            Text(stringResource(R.string.module_repo_add_selected))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                pendingCatalogModule = null
                                pendingModuleSetMetadata = null
                                selectedModuleSetChildren = emptyList()
                                moduleSetStageSelections = emptyMap()
                            }
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }
            return@let
        }
        val supportedStages = module.buildNormalizedSupportedStages()
        val recommendedStages = module.buildNormalizedRecommendedStages().toSet()
        val addedStages = module.addedStages(selectedModules).toSet()
        val selectedStages = supportedStages.filter {
            it in selectedCatalogModuleStages && it !in addedStages
        }
        AlertDialog(
            onDismissRequest = {
                pendingCatalogModule = null
                selectedCatalogModuleStages = emptyList()
            },
            icon = { Icon(Icons.Default.Extension, null) },
            title = { Text(stringResource(R.string.module_repo_select_stage)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = module.buildDisplayName(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (module.version.isNotBlank() || module.description.isNotBlank()) {
                        Text(
                            text = buildString {
                                if (module.version.isNotBlank()) append(context.getString(R.string.module_repo_version, module.version))
                                if (module.version.isNotBlank() && module.description.isNotBlank()) appendLine()
                                if (module.description.isNotBlank()) append(module.description)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    supportedStages.forEach { stage ->
                        val alreadyAdded = stage in addedStages
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = alreadyAdded || stage in selectedCatalogModuleStages,
                                enabled = !alreadyAdded,
                                onCheckedChange = { checked ->
                                    selectedCatalogModuleStages = if (checked) {
                                        (selectedCatalogModuleStages + stage).distinct()
                                    } else {
                                        selectedCatalogModuleStages - stage
                                    }
                                }
                            )
                            Text(
                                text = buildString {
                                    append(stage)
                                    if (stage in recommendedStages) append(stringResource(R.string.module_repo_recommended))
                                    if (alreadyAdded) append(stringResource(R.string.module_repo_added_suffix))
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (vm.addCustomExternalModulesFromUrl(module.repoUrl, selectedStages)) {
                            pendingCatalogModule = null
                            selectedCatalogModuleStages = emptyList()
                            Toast.makeText(context, context.getString(R.string.module_repo_added_to_build), Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = selectedStages.isNotEmpty()
                ) {
                    Text(stringResource(R.string.module_repo_add_selected))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            val remainingStages = supportedStages.filterNot { it in addedStages }
                            if (vm.addCustomExternalModulesFromUrl(module.repoUrl, remainingStages)) {
                                pendingCatalogModule = null
                                selectedCatalogModuleStages = emptyList()
                                Toast.makeText(context, context.getString(R.string.module_repo_added_to_build), Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = supportedStages.any { it !in addedStages }
                    ) {
                        Text(stringResource(R.string.module_repo_all_stages))
                    }
                    TextButton(
                        onClick = {
                            pendingCatalogModule = null
                            selectedCatalogModuleStages = emptyList()
                        }
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
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

        Scaffold(
            containerColor = appPageBackgroundColor(uiSurfaceColor(MaterialTheme.colorScheme.surface)),
            topBar = {
                ExpressiveTopBar(
                    title = buildRepoTitleLabel(context),
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = ::openRepositorySettings) {
                            Icon(Icons.Default.Dns, contentDescription = buildRepoManageLabel(context))
                        }
                    }
                )
            }
        ) { padding ->
            BuildModuleRepositoryListContent(
                padding = padding,
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
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        ExpressiveTopBar(
                            title = buildRepoCentralLabel(context),
                            navigationIcon = {
                                IconButton(onClick = childPageBack::requestDismiss) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.module_repo_back))
                                }
                            }
                        )
                    }
                ) { padding ->
                    BuildModuleRepositorySettingsPage(
                        padding = padding,
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
    padding: PaddingValues,
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
            .padding(padding)
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = bottomPadding + 24.dp)
    ) {
        item(key = "search") {
            CompactModuleSearchField(
                value = searchQuery,
                onValueChange = onSearchQueryChange
            )
        }

        if (refreshing && !showInitialLoading) {
            item(key = "refreshing") {
                ShimmerLinearProgress(
                    progress = { null },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (showInitialLoading) {
            item(key = "initial-loading") {
                ModuleRepositoryInitialLoading()
            }
        } else if (modules.isEmpty()) {
            item(key = "empty") {
                RuntimeModuleRepositoryEmptyState(
                    totalModules = totalModules,
                    repositoryCount = repositories.size,
                    hasQuery = searchQuery.isNotBlank(),
                    onOpenRepositorySettings = onOpenRepositorySettings
                )
            }
        } else {
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
private fun ModuleRepositoryInitialLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LoadingIndicator(Modifier.size(42.dp))
            Text(
                text = stringResource(R.string.module_repo_building_list),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RuntimeModuleRepositoryEmptyState(
    totalModules: Int,
    repositoryCount: Int,
    hasQuery: Boolean,
    onOpenRepositorySettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Extension,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(38.dp)
        )
        Text(
            text = when {
                hasQuery -> stringResource(R.string.module_repo_no_matching)
                repositoryCount == 0 -> runtimeRepoEmptyTitleLabel(LocalContext.current)
                totalModules == 0 -> runtimeRepoEmptyDescLabel(LocalContext.current)
                else -> stringResource(R.string.module_repo_no_display)
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        TextButton(onClick = onOpenRepositorySettings) {
            Icon(Icons.Default.Dns, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(runtimeRepoManageLabel(LocalContext.current))
        }
    }
}

@Composable
private fun RuntimeModuleRepositoryListItem(
    merged: MergedRuntimeCatalogModule,
    onOpen: () -> Unit,
    onInstall: () -> Unit
) {
    val context = LocalContext.current
    val module = merged.module
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainer)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = module.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val meta = module.metaLine()
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                if (merged.sources.size > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            Icons.Default.Source,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = merged.sources.size.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (module.description.isNotBlank()) {
                Text(
                    text = module.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                ModuleTagChip(label = module.id.ifBlank { module.name }, maxWidth = 170.dp)
                module.minApi?.let { ModuleTagChip(label = "API >= $it", secondary = true) }
                module.maxApi?.let { ModuleTagChip(label = "API <= $it", secondary = true) }
                if (module.verified) {
                    ModuleTagChip(label = runtimeRepoVerifiedLabel(LocalContext.current), secondary = true)
                }
                if (merged.sources.size > 1) {
                    ModuleTagChip(label = stringResource(R.string.module_repo_source_count, merged.sources.size), secondary = true)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactModuleActionButton(
                    icon = Icons.Default.OpenInBrowser,
                    contentDescription = context.getString(R.string.module_repo_open_repo),
                    onClick = onOpen
                )
                Spacer(Modifier.width(6.dp))
                CompactModuleActionButton(
                    icon = Icons.Default.UploadFile,
                    contentDescription = context.getString(R.string.runtime_install_module),
                    enabled = module.zipUrl.isNotBlank(),
                    onClick = onInstall
                )
            }
        }
    }
}

@Composable
private fun CompactModuleSearchField(
    value: String,
    onValueChange: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.72f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isBlank()) {
                    Text(
                        text = stringResource(R.string.module_repo_search),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun CompactModuleActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(width = 42.dp, height = 36.dp),
        shape = RoundedCornerShape(18.dp),
        color = colors.secondaryContainer.copy(alpha = if (enabled) 0.82f else 0.44f),
        contentColor = if (enabled) colors.onSecondaryContainer else colors.onSurfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun ModuleTagChip(
    label: String,
    secondary: Boolean = false,
    maxWidth: Dp = 140.dp
) {
    val color = if (secondary) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }
    val contentColor = if (secondary) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimary
    }
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = color.copy(alpha = if (secondary) 0.78f else 0.88f),
        contentColor = contentColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = maxWidth)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun RuntimeModuleRepositorySettingsPage(
    padding: PaddingValues,
    repositories: List<RuntimeModuleRepository>,
    refreshingRepositoryIds: Set<String>,
    onAddRepository: (String) -> Unit,
    onRefreshAll: () -> Unit,
    onRefreshRepository: (String) -> Unit,
    onDeleteRepository: (String) -> Unit
) {
    var repositoryUrl by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ExpressiveSectionCard(
            title = runtimeRepoCentralLabel(LocalContext.current),
            subtitle = runtimeRepoCentralDescLabel(LocalContext.current),
            icon = Icons.Default.Dns
        ) {
            OutlinedTextField(
                value = repositoryUrl,
                onValueChange = { repositoryUrl = it },
                label = { Text(runtimeRepoUrlLabel(LocalContext.current)) },
                placeholder = { Text("https://example.com/modules.json") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onAddRepository(repositoryUrl)
                        repositoryUrl = ""
                    },
                    enabled = repositoryUrl.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.add))
                }
                OutlinedButton(
                    onClick = onRefreshAll,
                    enabled = repositories.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.refresh_all))
                }
            }
        }

        if (repositories.isEmpty()) {
            ExpressiveSectionCard(
                title = runtimeRepoEmptyTitleLabel(LocalContext.current),
                subtitle = runtimeRepoEmptyDescLabel(LocalContext.current),
                icon = Icons.Default.Extension
            ) {
                Text(
                    text = runtimeRepoCentralDescLabel(LocalContext.current),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            repositories.forEach { repository ->
                RuntimeModuleRepositoryCard(
                    repository = repository,
                    refreshing = repository.id in refreshingRepositoryIds,
                    onRefresh = { onRefreshRepository(repository.id) },
                    onDelete = { onDeleteRepository(repository.id) }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RuntimeModuleRepositoryCard(
    repository: RuntimeModuleRepository,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    ExpressiveSectionCard(
        title = repository.name.ifBlank { repository.url },
        subtitle = repository.url,
        icon = Icons.Default.Dns
    ) {
        if (refreshing) {
            ShimmerLinearProgress(
                progress = { null },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExpressiveStatusChip(
                label = stringResource(R.string.module_repo_module_count, repository.modules.size),
                icon = Icons.Default.Extension,
                color = MaterialTheme.colorScheme.primary
            )
            if (repository.skippedCount > 0) {
                ExpressiveStatusChip(
                    label = stringResource(R.string.module_repo_skipped_count, repository.skippedCount),
                    icon = Icons.Default.Link,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        repository.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        val indexUrl = repository.indexJsonUrl.ifBlank { repository.url }
        Text(
            text = indexUrl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onRefresh,
                enabled = !refreshing,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
            ) {
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.refresh))
            }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
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
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.UploadFile, null) },
        title = { Text(runtimeRepoConfirmInstallTitle(context)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = module.module.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                module.module.metaLine().takeIf { it.isNotBlank() }?.let { meta ->
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${context.getString(R.string.runtime_source)}: ${
                        module.sources.firstOrNull() ?: runtimeRepoUnknownSourceLabel(context)
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = module.module.zipUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                if (module.module.description.isNotBlank()) {
                    Text(
                        text = module.module.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = context.getString(R.string.runtime_confirm_flash_module_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text(context.getString(R.string.runtime_install_module))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun RuntimeRepositoryInstallDialog(
    running: Boolean,
    success: Boolean?,
    logLines: List<String>,
    onClose: () -> Unit,
    onReboot: () -> Unit
) {
    val terminalScroll = rememberScrollState()
    val colorScheme = MaterialTheme.colorScheme
    val isLightTheme = colorScheme.surface.luminance() > 0.5f
    val terminalContainer = if (isLightTheme) {
        colorScheme.surfaceContainerHighest
    } else {
        colorScheme.surfaceContainerLowest
    }

    LaunchedEffect(logLines.size) {
        terminalScroll.animateScrollTo(terminalScroll.maxValue)
    }

    AlertDialog(
        onDismissRequest = { if (!running) onClose() },
        icon = {
            when {
                running -> LoadingIndicator(modifier = Modifier.size(24.dp))
                success == true -> Icon(Icons.Default.CheckCircle, null, tint = colorScheme.primary)
                success == false -> Icon(Icons.Default.Error, null, tint = colorScheme.error)
                else -> Icon(Icons.Default.UploadFile, null)
            }
        },
        title = {
            Text(
                if (running) {
                    stringResource(R.string.runtime_installing_module)
                } else {
                    stringResource(R.string.runtime_install_module)
                }
            )
        },
        text = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 190.dp, max = 360.dp),
                shape = RoundedCornerShape(12.dp),
                color = terminalContainer,
                contentColor = colorScheme.onSurface,
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(terminalScroll)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    logLines.ifEmpty { listOf(stringResource(R.string.runtime_waiting_output)) }.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (line.startsWith("$")) colorScheme.primary else colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (running) {
                TextButton(onClick = {}, enabled = false) {
                    Text(stringResource(R.string.runtime_running))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onClose) {
                        Text(stringResource(R.string.close))
                    }
                    if (success == true) {
                        Button(
                            onClick = onReboot,
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error)
                        ) {
                            Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.runtime_reboot))
                        }
                    }
                }
            }
        }
    )
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

private fun RuntimeModuleCatalogItem.downloadFileName(): String {
    val base = id.ifBlank { name }
        .replace(Regex("""[^A-Za-z0-9._-]"""), "_")
        .trim('_')
        .ifBlank { "module" }
    return if (base.endsWith(".zip", ignoreCase = true)) base else "${base}-module.zip"
}

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
    padding: PaddingValues,
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
            .padding(padding)
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = bottomPadding + 24.dp)
    ) {
        item(key = "search") {
            CompactModuleSearchField(
                value = searchQuery,
                onValueChange = onSearchQueryChange
            )
        }

        if (refreshing && !showInitialLoading) {
            item(key = "refreshing") {
                ShimmerLinearProgress(
                    progress = { null },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (showInitialLoading) {
            item(key = "initial-loading") {
                ModuleRepositoryInitialLoading()
            }
        } else if (modules.isEmpty()) {
            item(key = "empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(38.dp)
                    )
                    Text(
                        text = when {
                            searchQuery.isNotBlank() -> context.getString(R.string.module_repo_no_matching)
                            repositories.isEmpty() -> buildRepoEmptyTitleLabel(context)
                            totalModules == 0 -> context.getString(R.string.module_repo_refresh_hint)
                            else -> context.getString(R.string.module_repo_no_display)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = onOpenRepositorySettings) {
                        Icon(Icons.Default.Dns, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(buildRepoManageLabel(context))
                    }
                }
            }
        } else {
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainer)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = module.buildDisplayName(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val meta = module.buildMetaLine()
                                if (meta.isNotBlank()) {
                                    Text(
                                        text = meta,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            if (merged.sources.size > 1) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Source,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = merged.sources.size.toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (module.description.isNotBlank()) {
                            Text(
                                text = module.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            ModuleTagChip(label = module.repoUrl.repoName(), maxWidth = 170.dp)
                            module.buildNormalizedSupportedStages().take(2).forEach { stage ->
                                ModuleTagChip(label = stage, secondary = true)
                            }
                            if (allStagesAdded) {
                                ModuleTagChip(label = context.getString(R.string.module_repo_joined), secondary = true)
                            }
                            if (merged.sources.size > 1) {
                                ModuleTagChip(
                                    label = context.getString(R.string.module_repo_source_count, merged.sources.size),
                                    secondary = true
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CompactModuleActionButton(
                                icon = Icons.Default.OpenInBrowser,
                                contentDescription = context.getString(R.string.module_repo_open_repo),
                                onClick = { onOpenModule(module) }
                            )
                            Spacer(Modifier.width(6.dp))
                            CompactModuleActionButton(
                                icon = if (allStagesAdded) Icons.Default.CheckCircle else Icons.Default.Add,
                                contentDescription = if (allStagesAdded) {
                                    context.getString(R.string.module_repo_joined)
                                } else {
                                    context.getString(R.string.module_repo_add_to_build)
                                },
                                enabled = !allStagesAdded,
                                onClick = { onAddModule(module) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildModuleRepositorySettingsPage(
    padding: PaddingValues,
    repositories: List<ModuleCatalogRepository>,
    refreshingRepositoryIds: Set<String>,
    onAddRepository: (String) -> Unit,
    onRefreshAll: () -> Unit,
    onRefreshRepository: (String) -> Unit,
    onDeleteRepository: (String) -> Unit
) {
    val context = LocalContext.current
    var repositoryUrl by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ExpressiveSectionCard(
            title = buildRepoCentralLabel(context),
            subtitle = buildRepoCentralDescLabel(context),
            icon = Icons.Default.Dns
        ) {
            OutlinedTextField(
                value = repositoryUrl,
                onValueChange = { repositoryUrl = it },
                label = { Text(buildRepoUrlLabel(context)) },
                placeholder = { Text("https://github.com/user/abk-module-catalog") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onAddRepository(repositoryUrl)
                        repositoryUrl = ""
                    },
                    enabled = repositoryUrl.isNotBlank(),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.add))
                }
                OutlinedButton(
                    onClick = onRefreshAll,
                    enabled = repositories.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.refresh_all))
                }
            }
        }

        if (repositories.isEmpty()) {
            ExpressiveSectionCard(
                title = buildRepoEmptyTitleLabel(context),
                subtitle = buildRepoEmptyDescLabel(context),
                icon = Icons.Default.LibraryBooks
            ) {
                Text(
                    text = context.getString(R.string.module_repo_delete_keep_modules),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            repositories.forEach { repository ->
                BuildModuleCatalogRepositoryCard(
                    repository = repository,
                    refreshing = repository.id in refreshingRepositoryIds,
                    onRefresh = { onRefreshRepository(repository.id) },
                    onDelete = { onDeleteRepository(repository.id) }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BuildModuleCatalogRepositoryCard(
    repository: ModuleCatalogRepository,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    ExpressiveSectionCard(
        title = repository.name.ifBlank { repository.url },
        subtitle = repository.url,
        icon = Icons.Default.Dns
    ) {
        if (refreshing) {
            ShimmerLinearProgress(
                progress = { null },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExpressiveStatusChip(
                label = stringResource(R.string.module_repo_module_count, repository.modules.size),
                icon = Icons.Default.Extension,
                color = MaterialTheme.colorScheme.primary
            )
            if (repository.skippedCount > 0) {
                ExpressiveStatusChip(
                    label = stringResource(R.string.module_repo_skipped_count, repository.skippedCount),
                    icon = Icons.Default.Link,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        repository.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        val indexUrl = repository.indexJsonUrl.ifBlank { repository.url }
        Text(
            text = indexUrl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onRefresh,
                enabled = !refreshing,
                modifier = Modifier.weight(1f).height(42.dp)
            ) {
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.refresh))
            }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f).height(42.dp)
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.delete))
            }
        }
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
