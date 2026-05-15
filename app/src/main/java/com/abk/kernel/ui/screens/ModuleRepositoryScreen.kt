@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.abk.kernel.data.model.CustomExternalModuleStage
import com.abk.kernel.data.model.ModuleCatalogItem
import com.abk.kernel.data.model.ModuleCatalogRepository
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.viewmodel.MainViewModel
import kotlin.math.pow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

private const val MODULE_REPOSITORY_BACK_VISUAL_EXPONENT = 1.8f
private const val MODULE_REPOSITORY_BACK_SCALE_DELTA = 0.09f
private const val MODULE_REPOSITORY_BACK_SCRIM_ALPHA = 0.32f
private const val MODULE_REPOSITORY_PAGE_EXIT_DELAY_MS = 280L
private val MODULE_REPOSITORY_BACK_MAX_OFFSET = 56.dp
private val MODULE_REPOSITORY_BACK_MAX_CORNER = 32.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleRepositoryScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onRepositoryPageVisibleChange: (Boolean) -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val motionScheme = MaterialTheme.motionScheme
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showRepositorySettings by rememberSaveable { mutableStateOf(false) }
    var pendingCatalogModule by remember { mutableStateOf<ModuleCatalogItem?>(null) }
    var selectedCatalogModuleStages by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var repositoryBackProgress by remember { mutableFloatStateOf(0f) }
    val animatedRepositoryBackProgress by animateFloatAsState(
        targetValue = repositoryBackProgress.coerceIn(0f, 1f),
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "module-repository-back-progress"
    )
    val visualRepositoryBackProgress = animatedRepositoryBackProgress
        .coerceIn(0f, 1f)
        .pow(MODULE_REPOSITORY_BACK_VISUAL_EXPONENT)
    val density = LocalDensity.current
    val repositoryBackOffsetPx = with(density) { MODULE_REPOSITORY_BACK_MAX_OFFSET.toPx() }
    val repositoryBackCorner = with(density) {
        (MODULE_REPOSITORY_BACK_MAX_CORNER.toPx() * visualRepositoryBackProgress).toDp()
    }
    val mergedModules = remember(state.moduleCatalogRepositories) {
        mergeCatalogModules(state.moduleCatalogRepositories)
    }
    val filteredModules = remember(mergedModules, searchQuery) {
        mergedModules.filter { it.matchesQuery(searchQuery) }
    }
    val selectedModules = remember(state.buildConfig.customExternalModules) {
        state.buildConfig.customExternalModules
            .map { it.url.trim().lowercase() to CustomExternalModuleStage.normalize(it.stage) }
            .toSet()
    }

    fun openRepositorySettings() {
        repositoryBackProgress = 0f
        onRepositoryPageVisibleChange(true)
        showRepositorySettings = true
    }

    fun closeRepositorySettings() {
        showRepositorySettings = false
    }

    LaunchedEffect(showRepositorySettings) {
        if (showRepositorySettings) {
            onRepositoryPageVisibleChange(true)
        } else {
            delay(MODULE_REPOSITORY_PAGE_EXIT_DELAY_MS)
            repositoryBackProgress = 0f
            onRepositoryPageVisibleChange(false)
        }
    }

    DisposableEffect(Unit) {
        onDispose { onRepositoryPageVisibleChange(false) }
    }

    PredictiveBackHandler(enabled = showRepositorySettings && state.predictiveBackEnabled) { progress ->
        try {
            progress.collect { backEvent ->
                repositoryBackProgress = backEvent.progress.coerceIn(0f, 1f)
            }
            closeRepositorySettings()
        } catch (_: CancellationException) {
            repositoryBackProgress = 0f
        }
    }

    BackHandler(enabled = showRepositorySettings && !state.predictiveBackEnabled) {
        closeRepositorySettings()
    }

    pendingCatalogModule?.let { module ->
        val supportedStages = module.normalizedSupportedStages()
        val recommendedStages = module.normalizedRecommendedStages().toSet()
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
            title = { Text("选择注入阶段") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = module.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (module.version.isNotBlank() || module.description.isNotBlank()) {
                        Text(
                            text = buildString {
                                if (module.version.isNotBlank()) append("版本: ${module.version}")
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
                                    if (stage in recommendedStages) append("（推荐）")
                                    if (alreadyAdded) append("（已加入）")
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
                            Toast.makeText(context, "模块已加入构建配置", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = selectedStages.isNotEmpty()
                ) {
                    Text("添加所选")
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
                                Toast.makeText(context, "模块已加入构建配置", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = supportedStages.any { it !in addedStages }
                    ) {
                        Text("全部阶段")
                    }
                    TextButton(
                        onClick = {
                            pendingCatalogModule = null
                            selectedCatalogModuleStages = emptyList()
                        }
                    ) {
                        Text("取消")
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
            containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
            topBar = {
                ExpressiveTopBar(
                    title = "模块仓库",
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = ::openRepositorySettings) {
                            Icon(Icons.Default.Dns, contentDescription = "配置模块仓库")
                        }
                    }
                )
            }
        ) { padding ->
            ModuleRepositoryListContent(
                padding = padding,
                modules = filteredModules,
                totalModules = mergedModules.size,
                repositories = state.moduleCatalogRepositories,
                refreshing = state.refreshingModuleCatalogRepositoryIds.isNotEmpty(),
                selectedModules = selectedModules,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onOpenRepositorySettings = ::openRepositorySettings,
                onAddModule = { module ->
                    pendingCatalogModule = module
                    selectedCatalogModuleStages = module.initialStageSelection(selectedModules)
                },
                onOpenModule = { module ->
                    val url = module.homepage.ifBlank { module.repoUrl }
                    runCatching { uriHandler.openUri(url) }
                        .onFailure { Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show() }
                },
                scrollBehavior = scrollBehavior,
                bottomPadding = outerPadding.calculateBottomPadding()
            )
        }

        AnimatedVisibility(
            visible = showRepositorySettings,
            enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
            exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()),
            modifier = childPageModifier
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = MODULE_REPOSITORY_BACK_SCRIM_ALPHA * visualRepositoryBackProgress))
            )
        }

        AnimatedVisibility(
            visible = showRepositorySettings,
            enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) +
                slideInHorizontally(animationSpec = motionScheme.defaultSpatialSpec()) { width -> width / 4 },
            exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                slideOutHorizontally(animationSpec = motionScheme.fastSpatialSpec()) { width -> width },
            modifier = childPageModifier
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = repositoryBackOffsetPx * visualRepositoryBackProgress
                        scaleX = 1f - MODULE_REPOSITORY_BACK_SCALE_DELTA * visualRepositoryBackProgress
                        scaleY = 1f - MODULE_REPOSITORY_BACK_SCALE_DELTA * visualRepositoryBackProgress
                        alpha = 1f - 0.06f * visualRepositoryBackProgress
                        shape = RoundedCornerShape(repositoryBackCorner)
                        clip = visualRepositoryBackProgress > 0.01f
                    }
            ) {
                ModuleRepositoryPageBackground(
                    backgroundUri = state.customBackgroundUri,
                    backgroundImageEnabled = state.backgroundImageEnabled
                )
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        ExpressiveTopBar(
                            title = "中央仓库",
                            navigationIcon = {
                                IconButton(onClick = ::closeRepositorySettings) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "返回模块仓库")
                                }
                            }
                        )
                    }
                ) { padding ->
                    ModuleRepositorySettingsPage(
                        padding = padding,
                        repositories = state.moduleCatalogRepositories,
                        refreshingRepositoryIds = state.refreshingModuleCatalogRepositoryIds,
                        onAddRepository = { vm.addModuleCatalogRepository(it) },
                        onRefreshAll = { vm.refreshAllModuleCatalogRepositories() },
                        onRefreshRepository = { vm.refreshModuleCatalogRepository(it) },
                        onDeleteRepository = { vm.deleteModuleCatalogRepository(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleRepositoryListContent(
    padding: PaddingValues,
    modules: List<MergedCatalogModule>,
    totalModules: Int,
    repositories: List<ModuleCatalogRepository>,
    refreshing: Boolean,
    selectedModules: Set<Pair<String, String>>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenRepositorySettings: () -> Unit,
    onAddModule: (ModuleCatalogItem) -> Unit,
    onOpenModule: (ModuleCatalogItem) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    bottomPadding: androidx.compose.ui.unit.Dp
) {
    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CompactModuleSearchField(
            value = searchQuery,
            onValueChange = onSearchQueryChange
        )

        if (refreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (modules.isEmpty()) {
            EmptyModuleRepositoryState(
                totalModules = totalModules,
                repositoryCount = repositories.size,
                hasQuery = searchQuery.isNotBlank(),
                onOpenRepositorySettings = onOpenRepositorySettings
            )
        } else {
            modules.forEach { merged ->
                val module = merged.module
                val supportedStages = module.normalizedSupportedStages()
                val allStagesAdded = supportedStages.all { stage ->
                    module.repoUrl.trim().lowercase() to stage in selectedModules
                }
                ModuleRepositoryListItem(
                    module = module,
                    sources = merged.sources,
                    alreadyAdded = allStagesAdded,
                    onOpen = { onOpenModule(module) },
                    onAdd = { onAddModule(module) }
                )
            }
        }

        Spacer(Modifier.height(bottomPadding + 24.dp))
    }
}

@Composable
private fun EmptyModuleRepositoryState(
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
                hasQuery -> "没有匹配的模块"
                repositoryCount == 0 -> "还没有中央仓库"
                totalModules == 0 -> "刷新中央仓库后会显示模块"
                else -> "没有可显示的模块"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        TextButton(onClick = onOpenRepositorySettings) {
            Icon(Icons.Default.Dns, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("管理中央仓库")
        }
    }
}

@Composable
private fun ModuleRepositoryListItem(
    module: ModuleCatalogItem,
    sources: List<String>,
    alreadyAdded: Boolean,
    onOpen: () -> Unit,
    onAdd: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
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
                        text = module.displayName(),
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
                if (sources.size > 1) {
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
                            text = sources.size.toString(),
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
                module.normalizedSupportedStages().take(2).forEach { stage ->
                    ModuleTagChip(label = stage, secondary = true)
                }
                if (alreadyAdded) {
                    ModuleTagChip(label = "已加入", secondary = true)
                }
                if (sources.size > 1) {
                    ModuleTagChip(label = "来源 ${sources.size}", secondary = true)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactModuleActionButton(
                    icon = Icons.Default.OpenInBrowser,
                    contentDescription = "打开模块仓库",
                    onClick = onOpen
                )
                Spacer(Modifier.width(6.dp))
                CompactModuleActionButton(
                    icon = if (alreadyAdded) Icons.Default.CheckCircle else Icons.Default.Add,
                    contentDescription = if (alreadyAdded) "已加入" else "加入构建配置",
                    enabled = !alreadyAdded,
                    onClick = onAdd
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
                        text = "搜索模块",
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
private fun ModuleRepositorySettingsPage(
    padding: PaddingValues,
    repositories: List<ModuleCatalogRepository>,
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
            title = "中央仓库",
            subtitle = "添加包含 abk-modules.json 的索引仓库。",
            icon = Icons.Default.Dns
        ) {
            OutlinedTextField(
                value = repositoryUrl,
                onValueChange = { repositoryUrl = it },
                label = { Text("中央仓库链接") },
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
                    Text("添加")
                }
                OutlinedButton(
                    onClick = onRefreshAll,
                    enabled = repositories.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("刷新全部")
                }
            }
        }

        if (repositories.isEmpty()) {
            ExpressiveSectionCard(
                title = "暂无中央仓库",
                subtitle = "添加中央仓库后，模块会在主页面合并展示。",
                icon = Icons.Default.LibraryBooks
            ) {
                Text(
                    text = "删除中央仓库不会移除已经加入构建配置的单模块仓库。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            repositories.forEach { repository ->
                ModuleCatalogRepositoryCard(
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
private fun ModuleCatalogRepositoryCard(
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
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExpressiveStatusChip(
                label = "${repository.modules.size} 个模块",
                icon = Icons.Default.Extension,
                color = MaterialTheme.colorScheme.primary
            )
            if (repository.skippedCount > 0) {
                ExpressiveStatusChip(
                    label = "跳过 ${repository.skippedCount}",
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
                Text("刷新")
            }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f).height(42.dp)
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("删除")
            }
        }
    }
}

@Composable
private fun ModuleRepositoryPageBackground(
    backgroundUri: String?,
    backgroundImageEnabled: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    val hasBackground = backgroundImageEnabled && !backgroundUri.isNullOrBlank()
    val scrimColor = if (colorScheme.surface.luminance() > 0.5f) {
        colorScheme.surface.copy(alpha = 0.28f)
    } else {
        Color.Black.copy(alpha = 0.38f)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surface)
    ) {
        if (hasBackground) {
            AsyncImage(
                model = backgroundUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor)
            )
        }
    }
}

private data class MergedCatalogModule(
    val module: ModuleCatalogItem,
    val sources: List<String>
)

private fun mergeCatalogModules(repositories: List<ModuleCatalogRepository>): List<MergedCatalogModule> =
    repositories
        .flatMap { repository ->
            repository.modules.map { module -> repository.name.ifBlank { repository.url } to module }
        }
        .groupBy { (_, module) -> module.repoUrl.trim().lowercase() }
        .values
        .map { entries ->
            val module = entries.first().second
            MergedCatalogModule(
                module = module,
                sources = entries.map { it.first }.distinct()
            )
        }
        .sortedBy { it.module.displayName().lowercase() }

private fun MergedCatalogModule.matchesQuery(query: String): Boolean {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) return true
    val catalogItem = module
    return listOf(
        catalogItem.name,
        catalogItem.version,
        catalogItem.description,
        catalogItem.repoUrl,
        catalogItem.author,
        catalogItem.homepage,
        sources.joinToString(" ")
    ).any { it.contains(cleanQuery, ignoreCase = true) }
}

private fun ModuleCatalogItem.displayName(): String =
    name.ifBlank { repoUrl.trim().trimEnd('/').substringAfterLast('/').removeSuffix(".git") }

private fun ModuleCatalogItem.metaLine(): String =
    listOfNotNull(
        version.takeIf { it.isNotBlank() }?.let { "版本: $it" },
        author.takeIf { it.isNotBlank() }?.let { "作者: $it" }
    ).joinToString("\n")

private fun ModuleCatalogItem.normalizedSupportedStages(): List<String> =
    supportedStages
        .map { CustomExternalModuleStage.normalize(it) }
        .distinct()
        .ifEmpty { listOf(CustomExternalModuleStage.normalize(defaultStage)) }

private fun ModuleCatalogItem.normalizedRecommendedStages(): List<String> {
    val supportedStages = normalizedSupportedStages()
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
    return normalizedSupportedStages().filter { stage -> moduleUrl to stage in selectedModules }
}

private fun ModuleCatalogItem.initialStageSelection(selectedModules: Set<Pair<String, String>>): List<String> {
    val moduleUrl = repoUrl.trim().lowercase()
    val remainingRecommendedStages = normalizedRecommendedStages().filterNot { stage ->
        moduleUrl to stage in selectedModules
    }
    val remainingSupportedStages = normalizedSupportedStages().filterNot { stage ->
        moduleUrl to stage in selectedModules
    }
    return remainingRecommendedStages
        .ifEmpty { remainingSupportedStages.take(1) }
        .ifEmpty { normalizedRecommendedStages() }
}

private fun String.repoName(): String =
    trim()
        .trimEnd('/')
        .removeSuffix(".git")
        .substringAfterLast('/')
        .ifBlank { trim().trimEnd('/').substringAfterLast('/') }
