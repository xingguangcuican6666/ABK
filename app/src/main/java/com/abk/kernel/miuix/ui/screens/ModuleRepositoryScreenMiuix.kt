package com.abk.kernel.miuix.ui.screens

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.UploadFile
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import com.abk.kernel.R
import com.abk.kernel.data.model.CustomExternalModuleStage
import com.abk.kernel.data.model.ExternalModuleMetadata
import com.abk.kernel.data.model.ModuleCatalogItem
import com.abk.kernel.data.model.ModuleCatalogItemKind
import com.abk.kernel.data.model.ModuleCatalogRepository
import com.abk.kernel.data.model.RuntimeModuleCatalogItem
import com.abk.kernel.data.model.RuntimeModuleRepository
import com.abk.kernel.miuix.component.SearchBarFake
import com.abk.kernel.miuix.component.SearchBox
import com.abk.kernel.miuix.component.SearchPager
import com.abk.kernel.miuix.component.SearchStatus
import com.abk.kernel.miuix.util.BlurredBar
import com.abk.kernel.miuix.util.rememberBlurBackdrop
import com.abk.kernel.ui.navigation3.LocalNavigator
import com.abk.kernel.ui.navigation3.Route
import com.abk.kernel.ui.screens.ModuleRepositoryMode
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.LocaleHelper
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.viewmodel.MainViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

private const val RUNTIME_MODULE_DOWNLOAD_RUN_ID = -2_000_000_001L
private val MODULE_TAG_CHIP_CORNER_RADIUS = 8.dp

private data class ModuleListComputation<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false
)

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ModuleRepositoryScreenMiuix(
    vm: MainViewModel,
    mode: ModuleRepositoryMode,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onRepositoryPageVisibleChange: (Boolean) -> Unit = {}
) {
    if (mode == ModuleRepositoryMode.BUILD_ABK) {
        BuildModuleRepositoryScreenMiuix(
            vm = vm,
            outerPadding = outerPadding,
            onRepositoryPageVisibleChange = onRepositoryPageVisibleChange
        )
        return
    }
    RuntimeModuleRepositoryScreenMiuix(
        vm = vm,
        outerPadding = outerPadding,
        onRepositoryPageVisibleChange = onRepositoryPageVisibleChange
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MiuixModuleTagChip(
    label: String,
    primary: Boolean = false,
    maxWidth: Dp = 140.dp
) {
    val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val primaryColors = ButtonDefaults.buttonColorsPrimary()
    val secondaryColors = ButtonDefaults.buttonColors()
    val bgColor = if (primary) {
        if (isDark) Color(0xFF223452) else Color(0xFFE4F3FF)
    } else {
        secondaryColors.color
    }
    val contentColor = if (primary) {
        primaryColors.color
    } else {
        secondaryColors.contentColor
    }
    Box(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .squircleSurface(color = bgColor, cornerRadius = MODULE_TAG_CHIP_CORNER_RADIUS)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = contentColor,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MiuixModuleInitialLoading() {
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
            CircularProgressIndicator(
                modifier = Modifier.size(42.dp),
                progress = null,
                size = 42.dp,
                strokeWidth = 3.dp
            )
            Text(
                text = stringResource(R.string.module_repo_building_list),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BUILD_ABK screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BuildModuleRepositoryScreenMiuix(
    vm: MainViewModel,
    outerPadding: PaddingValues,
    onRepositoryPageVisibleChange: (Boolean) -> Unit
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val navigator = LocalNavigator.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    var searchStatus by remember { mutableStateOf(SearchStatus("")) }
    val query = searchStatus.searchText
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
        // Keep previous items visible while recomputing to avoid UI flicker
        val previousItems = value.items
        value = ModuleListComputation(items = previousItems, loading = false)
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
        key2 = query
    ) {
        if (mergedModulesState.loading) {
            // Keep previous items visible while parent recomputes
            val prevItems = value.items
            value = ModuleListComputation(items = prevItems, loading = false)
            return@produceState
        }
        // Keep previous items visible while filtering
        val prevFiltered = value.items
        value = ModuleListComputation(items = prevFiltered, loading = false)
        val filtered = withContext(Dispatchers.Default) {
            if (query.isBlank()) {
                mergedModules
            } else {
                mergedModules.filter { it.matchesQuery(query) }
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

    fun openRepositorySettings() {
        onRepositoryPageVisibleChange(true)
        navigator.push(Route.BuildModuleRepoSettings)
    }

    DisposableEffect(Unit) {
        onDispose { onRepositoryPageVisibleChange(false) }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────

    pendingCatalogModule?.let { module ->
        if (module.kind == ModuleCatalogItemKind.MODULE_SET) {
            val metadata = pendingModuleSetMetadata
            if (metadata != null) {
                BuildModuleSetSelectionDialogMiuix(
                    module = module,
                    metadata = metadata,
                    selectedChildren = selectedModuleSetChildren,
                    stageSelections = moduleSetStageSelections,
                    onToggleChild = { childId, checked ->
                        selectedModuleSetChildren = if (checked) {
                            (selectedModuleSetChildren + childId).distinct()
                        } else {
                            selectedModuleSetChildren - childId
                        }
                    },
                    onToggleStage = { childId, stage, checked ->
                        val currentStages = moduleSetStageSelections[childId] ?: emptyList()
                        val updatedStages = if (checked) {
                            (currentStages + stage).distinct()
                        } else {
                            currentStages - stage
                        }
                        moduleSetStageSelections = moduleSetStageSelections + (childId to updatedStages)
                    },
                    onDismiss = {
                        pendingCatalogModule = null
                        pendingModuleSetMetadata = null
                        selectedModuleSetChildren = emptyList()
                        moduleSetStageSelections = emptyMap()
                    },
                    onConfirm = {
                        val children = metadata.children
                        val selections = children
                            .filter { it.id in selectedModuleSetChildren }
                            .map { child ->
                                child to (
                                    moduleSetStageSelections[child.id]
                                        ?.distinct()
                                        ?.filter { it in child.supportedStages }
                                        ?.ifEmpty {
                                            child.recommendedStages
                                                .filter { it in child.supportedStages }
                                                .ifEmpty { listOf(child.defaultStage) }
                                        }
                                        ?: child.recommendedStages
                                            .filter { it in child.supportedStages }
                                            .ifEmpty { listOf(child.defaultStage) }
                                    )
                            }
                            .filter { (_, stages) -> stages.isNotEmpty() }
                        if (vm.replaceModuleSetSelection(module.repoUrl, metadata, selections)) {
                            pendingCatalogModule = null
                            pendingModuleSetMetadata = null
                            selectedModuleSetChildren = emptyList()
                            moduleSetStageSelections = emptyMap()
                            vm.showSnackbar(context.getString(R.string.module_repo_added_to_build))
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

        BuildModuleStageSelectionDialogMiuix(
            module = module,
            supportedStages = supportedStages,
            recommendedStages = recommendedStages,
            addedStages = addedStages,
            selectedStages = selectedCatalogModuleStages,
            onToggleStage = { stage, checked ->
                selectedCatalogModuleStages = if (checked) {
                    (selectedCatalogModuleStages + stage).distinct()
                } else {
                    selectedCatalogModuleStages - stage
                }
            },
            onDismiss = {
                pendingCatalogModule = null
                selectedCatalogModuleStages = emptyList()
            },
            onAddSelected = {
                if (vm.addCustomExternalModulesFromUrl(module.repoUrl, selectedStages)) {
                    pendingCatalogModule = null
                    selectedCatalogModuleStages = emptyList()
                    vm.showSnackbar(context.getString(R.string.module_repo_added_to_build))
                }
            },
            onAddAll = {
                val remainingStages = supportedStages.filterNot { it in addedStages }
                if (vm.addCustomExternalModulesFromUrl(module.repoUrl, remainingStages)) {
                    pendingCatalogModule = null
                    selectedCatalogModuleStages = emptyList()
                    vm.showSnackbar(context.getString(R.string.module_repo_added_to_build))
                }
            }
        )
    }

    // ── Scaffold ─────────────────────────────────────────────────────────

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
                        title = buildRepoTitleLabel(context),
                        scrollBehavior = scrollBehavior,
                        actions = {
                            IconButton(onClick = ::openRepositorySettings) {
                                Icon(
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = buildRepoManageLabel(context)
                                )
                            }
                        },
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
                                SearchBarFake(
                                    label = searchStatus.label,
                                    searchBarTopPadding = dynamicTopPadding
                                )
                            }
                        }
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
        popupHost = {
            searchStatus.SearchPager(
                onSearchStatusChange = { searchStatus = it },
                defaultResult = {},
                searchBarTopPadding = dynamicTopPadding,
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical()
                        .scrollEndHaptic(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = 80.dp + outerPadding.calculateBottomPadding()
                    ),
                    overscrollEffect = null
                ) {
                    if (filteredModules.isEmpty() && !listComputing) {
                        item(key = "empty") {
                            BuildModuleRepoEmptyStateMiuix(
                                totalModules = mergedModules.size,
                                repositoryCount = state.buildModuleRepositories.size,
                                hasQuery = query.isNotBlank(),
                                onOpenRepositorySettings = ::openRepositorySettings
                            )
                        }
                    } else {
                        items(
                            items = filteredModules,
                            key = { merged -> merged.module.repoUrl.trim().lowercase() }
                        ) { merged ->
                            val module = merged.module
                            val supportedStages = module.buildNormalizedSupportedStages()
                            val allStagesAdded = supportedStages.all { stage ->
                                module.repoUrl.trim().lowercase() to stage in selectedModules
                            }
                            BuildModuleCardMiuix(
                                merged = merged,
                                allStagesAdded = allStagesAdded,
                                onOpen = {
                                    val url = module.homepage.ifBlank { module.repoUrl }
                                    runCatching { uriHandler.openUri(url) }
                                        .onFailure {
                                            vm.showSnackbar(context.getString(R.string.module_repo_open_failed))
                                        }
                                },
                                onAdd = {
                                    if (module.kind == ModuleCatalogItemKind.MODULE_SET) {
                                        coroutineScope.launch {
                                            val metadata = vm.checkCustomExternalModuleMetadata(module.repoUrl)
                                            if (metadata != null) {
                                                pendingCatalogModule = module
                                                pendingModuleSetMetadata = metadata
                                                selectedModuleSetChildren = metadata.children.map { it.id }
                                                moduleSetStageSelections = metadata.children.associate { child ->
                                                    child.id to child.recommendedStages
                                                        .filter { it in child.supportedStages }
                                                        .ifEmpty { listOf(child.defaultStage) }
                                                }
                                            }
                                        }
                                    } else {
                                        pendingCatalogModule = module
                                        selectedCatalogModuleStages = module.initialStageSelection(selectedModules)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        searchStatus.SearchBox {
            Box(
                modifier = Modifier.then(
                    if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val showInitialLoading = listComputing || (
                        state.refreshingBuildModuleRepositoryIds.isNotEmpty() &&
                            mergedModules.isEmpty() && query.isBlank()
                        )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .overScrollVertical()
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                            .scrollEndHaptic(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(
                            top = innerPadding.calculateTopPadding() + 6.dp,
                            start = 12.dp,
                            end = 12.dp,
                            bottom = 80.dp + outerPadding.calculateBottomPadding()
                        ),
                        overscrollEffect = null
                    ) {
                        if (showInitialLoading) {
                            item(key = "initial-loading") {
                                MiuixModuleInitialLoading()
                            }
                        } else if (filteredModules.isEmpty()) {
                            item(key = "empty") {
                                BuildModuleRepoEmptyStateMiuix(
                                    totalModules = mergedModules.size,
                                    repositoryCount = state.buildModuleRepositories.size,
                                    hasQuery = query.isNotBlank(),
                                    onOpenRepositorySettings = ::openRepositorySettings
                                )
                            }
                        } else {
                            items(
                                items = filteredModules,
                                key = { merged -> merged.module.repoUrl.trim().lowercase() }
                            ) { merged ->
                                val module = merged.module
                                val supportedStages = module.buildNormalizedSupportedStages()
                                val allStagesAdded = supportedStages.all { stage ->
                                    module.repoUrl.trim().lowercase() to stage in selectedModules
                                }
                                BuildModuleCardMiuix(
                                    merged = merged,
                                    allStagesAdded = allStagesAdded,
                                    onOpen = {
                                        val url = module.homepage.ifBlank { module.repoUrl }
                                        runCatching { uriHandler.openUri(url) }
                                            .onFailure {
                                                vm.showSnackbar(context.getString(R.string.module_repo_open_failed))
                                            }
                                    },
                                    onAdd = {
                                        if (module.kind == ModuleCatalogItemKind.MODULE_SET) {
                                            coroutineScope.launch {
                                                val metadata = vm.checkCustomExternalModuleMetadata(module.repoUrl)
                                                if (metadata != null) {
                                                    pendingCatalogModule = module
                                                    pendingModuleSetMetadata = metadata
                                                    selectedModuleSetChildren = metadata.children.map { it.id }
                                                    moduleSetStageSelections = metadata.children.associate { child ->
                                                        child.id to child.recommendedStages
                                                            .filter { it in child.supportedStages }
                                                            .ifEmpty { listOf(child.defaultStage) }
                                                    }
                                                }
                                            }
                                        } else {
                                            pendingCatalogModule = module
                                            selectedCatalogModuleStages = module.initialStageSelection(selectedModules)
                                        }
                                    }
                                )
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

@Composable
private fun BuildModuleRepoEmptyStateMiuix(
    totalModules: Int,
    repositoryCount: Int,
    hasQuery: Boolean,
    onOpenRepositorySettings: () -> Unit
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Extension,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(38.dp)
            )
            Text(
                text = when {
                    hasQuery -> stringResource(R.string.module_repo_no_matching)
                    repositoryCount == 0 -> buildRepoEmptyTitleLabel(context)
                    totalModules == 0 -> stringResource(R.string.module_repo_refresh_hint)
                    else -> stringResource(R.string.module_repo_no_display)
                },
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onSurface
            )
            TextButton(
                text = buildRepoManageLabel(context),
                onClick = onOpenRepositorySettings,
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

@Composable
private fun BuildModuleCardMiuix(
    merged: BuildPageMergedCatalogModule,
    allStagesAdded: Boolean,
    onOpen: () -> Unit,
    onAdd: () -> Unit
) {
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    val module = merged.module
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Title row
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
                        style = MiuixTheme.textStyles.title4,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val meta = module.buildMetaLine()
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                }
                if (merged.sources.size > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Source,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = merged.sources.size.toString(),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            }

            // Description
            if (module.description.isNotBlank()) {
                Text(
                    text = module.description,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Tag chips
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MiuixModuleTagChip(
                    label = module.repoUrl.repoName(),
                    primary = true,
                    maxWidth = 170.dp
                )
                module.buildNormalizedSupportedStages().take(2).forEach { stage ->
                    MiuixModuleTagChip(label = stage, primary = false)
                }
                if (allStagesAdded) {
                    MiuixModuleTagChip(
                        label = context.getString(R.string.module_repo_joined),
                        primary = false
                    )
                }
                if (merged.sources.size > 1) {
                    MiuixModuleTagChip(
                        label = context.getString(R.string.module_repo_source_count, merged.sources.size),
                        primary = false
                    )
                }
            }

            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                HorizontalDivider()
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                        onOpen()
                    },
                    colors = ButtonDefaults.buttonColors(),
                    insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    minWidth = 0.dp,
                    minHeight = 0.dp
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.module_repo_open_repo),
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                        if (!allStagesAdded) onAdd()
                    },
                    colors = if (allStagesAdded) {
                        ButtonDefaults.buttonColors()
                    } else {
                        ButtonDefaults.buttonColorsPrimary()
                    },
                    insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    minWidth = 0.dp,
                    minHeight = 0.dp
                ) {
                    Icon(
                        imageVector = if (allStagesAdded) Icons.Default.CheckCircle else Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (allStagesAdded) {
                            stringResource(R.string.module_repo_joined)
                        } else {
                            stringResource(R.string.module_repo_add_to_build)
                        },
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BUILD_ABK dialogs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BuildModuleStageSelectionDialogMiuix(
    module: ModuleCatalogItem,
    supportedStages: List<String>,
    recommendedStages: Set<String>,
    addedStages: Set<String>,
    selectedStages: List<String>,
    onToggleStage: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onAddSelected: () -> Unit,
    onAddAll: () -> Unit
) {
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    val effectiveStages = supportedStages.filter {
        it in selectedStages && it !in addedStages
    }
    WindowDialog(
        show = true,
        title = stringResource(R.string.module_repo_select_stage),
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = module.buildDisplayName(),
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    if (module.version.isNotBlank() || module.description.isNotBlank()) {
                        Text(
                            text = buildString {
                                if (module.version.isNotBlank()) {
                                    append(context.getString(R.string.module_repo_version, module.version))
                                }
                                if (module.version.isNotBlank() && module.description.isNotBlank()) {
                                    appendLine()
                                }
                                if (module.description.isNotBlank()) {
                                    append(module.description)
                                }
                            },
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
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
                                state = ToggleableState(alreadyAdded || stage in selectedStages),
                                enabled = !alreadyAdded,
                                onClick = { if (!alreadyAdded) onToggleStage(stage, !(stage in selectedStages)) }
                            )
                            Text(
                                text = buildString {
                                    append(stage)
                                    if (stage in recommendedStages) {
                                        append(stringResource(R.string.module_repo_recommended))
                                    }
                                    if (alreadyAdded) {
                                        append(stringResource(R.string.module_repo_added_suffix))
                                    }
                                },
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                        onDismiss()
                    }
                )
                TextButton(
                    text = stringResource(R.string.module_repo_add_selected),
                    modifier = Modifier.weight(1f),
                    enabled = effectiveStages.isNotEmpty(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = {
                        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                        onAddSelected()
                    }
                )
            }
        }
    }
}

@Composable
private fun BuildModuleSetSelectionDialogMiuix(
    module: ModuleCatalogItem,
    metadata: ExternalModuleMetadata,
    selectedChildren: List<String>,
    stageSelections: Map<String, List<String>>,
    onToggleChild: (String, Boolean) -> Unit,
    onToggleStage: (String, String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val children = metadata.children
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    val canConfirm = selectedChildren.isNotEmpty() && children
        .filter { it.id in selectedChildren }
        .all { child ->
            val selStages = stageSelections[child.id]
                ?: child.recommendedStages
                    .filter { it in child.supportedStages }
                    .ifEmpty { listOf(child.defaultStage) }
            selStages.any { it in child.supportedStages }
        }
    WindowDialog(
        show = true,
        title = module.buildDisplayName(),
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val addToBuildLabel = stringResource(R.string.module_repo_add_to_build)
                    Text(
                        text = if (module.description.isNotBlank()) module.description else addToBuildLabel,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    children.forEach { child ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                state = ToggleableState(child.id in selectedChildren),
                                onClick = { onToggleChild(child.id, !(child.id in selectedChildren)) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = child.name,
                                    style = MiuixTheme.textStyles.body1,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                if (child.description.isNotBlank()) {
                                    Text(
                                        text = child.description,
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                }
                                if (child.id in selectedChildren) {
                                    val options = child.supportedStages
                                    val initialStages = child.recommendedStages
                                        .filter { it in options }
                                        .ifEmpty { listOf(child.defaultStage) }
                                    val selectedStages = stageSelections[child.id] ?: initialStages
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        options.forEach { stage ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Checkbox(
                                                    state = ToggleableState(stage in selectedStages),
                                                    onClick = {
                                                        onToggleStage(child.id, stage, !(stage in selectedStages))
                                                    }
                                                )
                                                Text(
                                                    text = buildString {
                                                        append(stage)
                                                        if (stage in child.recommendedStages) {
                                                            append(stringResource(R.string.module_repo_recommended))
                                                        }
                                                    },
                                                    style = MiuixTheme.textStyles.body2,
                                                    color = MiuixTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                        onDismiss()
                    }
                )
                TextButton(
                    text = stringResource(R.string.module_repo_add_selected),
                    modifier = Modifier.weight(1f),
                    enabled = canConfirm,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = {
                        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                        onConfirm()
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RUNTIME_STANDARD screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RuntimeModuleRepositoryScreenMiuix(
    vm: MainViewModel,
    outerPadding: PaddingValues,
    onRepositoryPageVisibleChange: (Boolean) -> Unit
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val navigator = LocalNavigator.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    var searchStatus by remember { mutableStateOf(SearchStatus("")) }
    val query = searchStatus.searchText
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
        // Keep previous items visible while recomputing to avoid UI flicker
        val previousItems = value.items
        value = ModuleListComputation(items = previousItems, loading = false)
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
        key2 = query
    ) {
        if (mergedModulesState.loading) {
            // Keep previous items visible while parent recomputes
            val prevItems = value.items
            value = ModuleListComputation(items = prevItems, loading = false)
            return@produceState
        }
        // Keep previous items visible while filtering
        val prevFiltered = value.items
        value = ModuleListComputation(items = prevFiltered, loading = false)
        val filtered = withContext(Dispatchers.Default) {
            if (query.isBlank()) {
                mergedModules
            } else {
                mergedModules.filter { it.matchesQuery(query) }
            }
        }
        value = ModuleListComputation(items = filtered, loading = false)
    }
    val filteredModules = filteredModulesState.items
    val listComputing = mergedModulesState.loading

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

    fun openRepositorySettings() {
        onRepositoryPageVisibleChange(true)
        navigator.push(Route.RuntimeModuleRepoSettings)
    }

    DisposableEffect(Unit) {
        onDispose { onRepositoryPageVisibleChange(false) }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────

    pendingInstallModule?.let { merged ->
        RuntimeModuleInstallConfirmDialogMiuix(
            module = merged,
            onDismiss = { pendingInstallModule = null },
            onConfirm = { startInstall(merged) }
        )
    }

    if (installDialogVisible) {
        RuntimeModuleInstallProgressDialogMiuix(
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

    // ── Scaffold ─────────────────────────────────────────────────────────

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
                        title = runtimeRepoTitleLabel(context),
                        scrollBehavior = scrollBehavior,
                        actions = {
                            IconButton(onClick = ::openRepositorySettings) {
                                Icon(
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = runtimeRepoConfigureLabel(context)
                                )
                            }
                        },
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
                                SearchBarFake(
                                    label = searchStatus.label,
                                    searchBarTopPadding = dynamicTopPadding
                                )
                            }
                        }
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
        popupHost = {
            searchStatus.SearchPager(
                onSearchStatusChange = { searchStatus = it },
                defaultResult = {},
                searchBarTopPadding = dynamicTopPadding,
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical()
                        .scrollEndHaptic(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = 80.dp + outerPadding.calculateBottomPadding()
                    ),
                    overscrollEffect = null
                ) {
                    if (filteredModules.isEmpty() && !listComputing) {
                        item(key = "empty") {
                            RuntimeModuleRepoEmptyStateMiuix(
                                totalModules = mergedModules.size,
                                repositoryCount = state.runtimeModuleRepositories.size,
                                hasQuery = query.isNotBlank(),
                                onOpenRepositorySettings = ::openRepositorySettings
                            )
                        }
                    } else {
                        items(
                            items = filteredModules,
                            key = { merged ->
                                "${merged.module.id.trim().lowercase().ifBlank { merged.module.name.trim().lowercase() }}-${merged.sources.joinToString("|")}"
                            }
                        ) { merged ->
                            RuntimeModuleCardMiuix(
                                merged = merged,
                                onOpen = {
                                    val url = merged.module.preferredOpenUrl()
                                    if (url.isBlank()) {
                                        vm.showSnackbar(context.getString(R.string.module_repo_open_failed))
                                    } else {
                                        runCatching { uriHandler.openUri(url) }
                                            .onFailure {
                                                vm.showSnackbar(context.getString(R.string.module_repo_open_failed))
                                            }
                                    }
                                },
                                onInstall = {
                                    if (merged.module.zipUrl.isBlank()) {
                                        vm.showSnackbar(runtimeRepoNoZipLabel(context))
                                    } else {
                                        pendingInstallModule = merged
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        searchStatus.SearchBox {
            Box(
                modifier = Modifier.then(
                    if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val showInitialLoading = listComputing || (
                        state.refreshingRuntimeModuleRepositoryIds.isNotEmpty() &&
                            mergedModules.isEmpty() && query.isBlank()
                        )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .overScrollVertical()
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                            .scrollEndHaptic(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(
                            top = innerPadding.calculateTopPadding() + 6.dp,
                            start = 12.dp,
                            end = 12.dp,
                            bottom = 80.dp + outerPadding.calculateBottomPadding()
                        ),
                        overscrollEffect = null
                    ) {
                        if (showInitialLoading) {
                            item(key = "initial-loading") {
                                MiuixModuleInitialLoading()
                            }
                        } else if (filteredModules.isEmpty()) {
                            item(key = "empty") {
                                RuntimeModuleRepoEmptyStateMiuix(
                                    totalModules = mergedModules.size,
                                    repositoryCount = state.runtimeModuleRepositories.size,
                                    hasQuery = query.isNotBlank(),
                                    onOpenRepositorySettings = ::openRepositorySettings
                                )
                            }
                        } else {
                            items(
                                items = filteredModules,
                                key = { merged ->
                                    "${merged.module.id.trim().lowercase().ifBlank { merged.module.name.trim().lowercase() }}-${merged.sources.joinToString("|")}"
                                }
                            ) { merged ->
                                RuntimeModuleCardMiuix(
                                    merged = merged,
                                    onOpen = {
                                        val url = merged.module.preferredOpenUrl()
                                        if (url.isBlank()) {
                                            vm.showSnackbar(context.getString(R.string.module_repo_open_failed))
                                        } else {
                                            runCatching { uriHandler.openUri(url) }
                                                .onFailure {
                                                    vm.showSnackbar(context.getString(R.string.module_repo_open_failed))
                                                }
                                        }
                                    },
                                    onInstall = {
                                        if (merged.module.zipUrl.isBlank()) {
                                            vm.showSnackbar(runtimeRepoNoZipLabel(context))
                                        } else {
                                            pendingInstallModule = merged
                                        }
                                    }
                                )
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

@Composable
private fun RuntimeModuleRepoEmptyStateMiuix(
    totalModules: Int,
    repositoryCount: Int,
    hasQuery: Boolean,
    onOpenRepositorySettings: () -> Unit
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Extension,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(38.dp)
            )
            Text(
                text = when {
                    hasQuery -> stringResource(R.string.module_repo_no_matching)
                    repositoryCount == 0 -> runtimeRepoEmptyTitleLabel(context)
                    totalModules == 0 -> runtimeRepoEmptyDescLabel(context)
                    else -> stringResource(R.string.module_repo_no_display)
                },
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onSurface
            )
            TextButton(
                text = runtimeRepoManageLabel(context),
                onClick = onOpenRepositorySettings,
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

@Composable
private fun RuntimeModuleCardMiuix(
    merged: MergedRuntimeCatalogModule,
    onOpen: () -> Unit,
    onInstall: () -> Unit
) {
    val context = LocalContext.current
    val module = merged.module
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Title row
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
                        style = MiuixTheme.textStyles.title4,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val meta = module.metaLine()
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                }
                if (merged.sources.size > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Source,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = merged.sources.size.toString(),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            }

            // Description
            if (module.description.isNotBlank()) {
                Text(
                    text = module.description,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Tag chips
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MiuixModuleTagChip(
                    label = module.id.ifBlank { module.name },
                    primary = true,
                    maxWidth = 170.dp
                )
                module.minApi?.let { MiuixModuleTagChip(label = "API >= $it") }
                module.maxApi?.let { MiuixModuleTagChip(label = "API <= $it") }
                if (module.verified) {
                    MiuixModuleTagChip(label = runtimeRepoVerifiedLabel(context))
                }
                if (merged.sources.size > 1) {
                    MiuixModuleTagChip(
                        label = stringResource(R.string.module_repo_source_count, merged.sources.size)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onOpen,
                    colors = ButtonDefaults.buttonColors(),
                    insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    minWidth = 0.dp,
                    minHeight = 0.dp
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.module_repo_open_repo),
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onInstall,
                    enabled = module.zipUrl.isNotBlank(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    minWidth = 0.dp,
                    minHeight = 0.dp
                ) {
                    Icon(
                        imageVector = Icons.Default.UploadFile,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.runtime_install_module),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RUNTIME_STANDARD dialogs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RuntimeModuleInstallConfirmDialogMiuix(
    module: MergedRuntimeCatalogModule,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current
    WindowDialog(
        show = true,
        title = runtimeRepoConfirmInstallTitle(context),
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = module.module.name,
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    module.module.metaLine().takeIf { it.isNotBlank() }?.let { meta ->
                        Text(
                            text = meta,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                    Text(
                        text = "${context.getString(R.string.runtime_source)}: ${
                            module.sources.firstOrNull() ?: runtimeRepoUnknownSourceLabel(context)
                        }",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Text(
                        text = module.module.zipUrl,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (module.module.description.isNotBlank()) {
                        Text(
                            text = module.module.description,
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                    Text(
                        text = context.getString(R.string.runtime_confirm_flash_module_desc),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss
                )
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    Icon(
                        imageVector = Icons.Default.UploadFile,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.runtime_install_module))
                }
            }
        }
    }
}

@Composable
private fun RuntimeModuleInstallProgressDialogMiuix(
    running: Boolean,
    success: Boolean?,
    logLines: List<String>,
    onClose: () -> Unit,
    onReboot: () -> Unit
) {
    val terminalScroll = rememberScrollState()
    val surfaceLuminance = MiuixTheme.colorScheme.surface.luminance()
    val isLight = surfaceLuminance > 0.5f
    val terminalBackground = if (isLight) Color(0xFF1E1E2E) else Color(0xFF0D0D0D)
    val terminalTextColor = Color(0xFFE0E0E0)
    val commandColor = Color(0xFF4CAF50)

    LaunchedEffect(logLines.size) {
        terminalScroll.animateScrollTo(terminalScroll.maxValue)
    }

    WindowDialog(
        show = true,
        title = if (running) {
            stringResource(R.string.runtime_installing_module)
        } else {
            stringResource(R.string.runtime_install_module)
        },
        onDismissRequest = { if (!running) onClose() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 190.dp, max = 360.dp)
                    .background(terminalBackground, RoundedCornerShape(8.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(terminalScroll)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    logLines.ifEmpty { listOf(stringResource(R.string.runtime_waiting_output)) }
                        .forEach { line ->
                            Text(
                                text = line,
                                style = MiuixTheme.textStyles.body2,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = if (line.startsWith("$")) commandColor else terminalTextColor
                            )
                        }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (running) {
                    TextButton(
                        text = stringResource(R.string.runtime_running),
                        modifier = Modifier.weight(1f),
                        enabled = false,
                        onClick = {}
                    )
                } else {
                    TextButton(
                        text = stringResource(R.string.close),
                        modifier = Modifier.weight(1f),
                        onClick = onClose
                    )
                    if (success == true) {
                        Button(
                            onClick = onReboot,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                color = MiuixTheme.colorScheme.error,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.runtime_reboot))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Data helpers – Build
// ─────────────────────────────────────────────────────────────────────────────

private data class BuildPageMergedCatalogModule(
    val module: ModuleCatalogItem,
    val sources: List<String>
)

private fun mergeBuildPageCatalogModules(
    repositories: List<ModuleCatalogRepository>
): List<BuildPageMergedCatalogModule> =
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
    val supported = buildNormalizedSupportedStages()
    val normalizedDefaultStage = CustomExternalModuleStage.normalize(defaultStage)
        .takeIf { it in supported }
        ?: supported.first()
    return recommendedStages
        .map { CustomExternalModuleStage.normalize(it) }
        .distinct()
        .filter { it in supported }
        .ifEmpty { listOf(normalizedDefaultStage) }
}

private fun ModuleCatalogItem.addedStages(selectedModules: Set<Pair<String, String>>): List<String> {
    val moduleUrl = repoUrl.trim().lowercase()
    return buildNormalizedSupportedStages().filter { stage -> moduleUrl to stage in selectedModules }
}

private fun ModuleCatalogItem.initialStageSelection(
    selectedModules: Set<Pair<String, String>>
): List<String> {
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

// ─────────────────────────────────────────────────────────────────────────────
// Data helpers – Runtime
// ─────────────────────────────────────────────────────────────────────────────

private data class MergedRuntimeCatalogModule(
    val module: RuntimeModuleCatalogItem,
    val sources: List<String>
)

private fun mergeRuntimeCatalogModules(
    repositories: List<RuntimeModuleRepository>
): List<MergedRuntimeCatalogModule> =
    repositories
        .flatMap { repository ->
            repository.modules.map { module -> repository.name.ifBlank { repository.url } to module }
        }
        .groupBy { (_, module) ->
            module.id.trim().lowercase().ifBlank { module.name.trim().lowercase() }
        }
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
    val m = module
    return listOf(
        m.id,
        m.name,
        m.version,
        m.author,
        m.description,
        m.support,
        m.website,
        m.zipUrl,
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

// ─────────────────────────────────────────────────────────────────────────────
// Locale label functions – Build
// ─────────────────────────────────────────────────────────────────────────────

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

// ─────────────────────────────────────────────────────────────────────────────
// Locale label functions – Runtime
// ─────────────────────────────────────────────────────────────────────────────

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
