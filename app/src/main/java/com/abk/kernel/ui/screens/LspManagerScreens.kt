@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.abk.kernel.ui.screens

import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.abk.kernel.R
import com.abk.kernel.data.model.LspInstalledModule
import com.abk.kernel.data.model.LspScopeApp
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.ExpressiveHeroCard
import com.abk.kernel.ui.components.ExpressiveListItem
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LspManagerHomeScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onOpenManagerSurfacePicker: () -> Unit
) {
    val state by vm.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) {
        vm.refreshAbkRuntimeStatus()
        vm.refreshLspInstalledModules()
    }

    Scaffold(
        containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
        topBar = {
            ExpressiveTopBar(
                title = "LSP 管理器",
                compactTitle = true,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = vm::refreshAbkRuntimeStatus) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.runtime_refresh))
                    }
                    IconButton(onClick = onOpenManagerSurfacePicker) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "切换管理侧")
                    }
                }
            )
        }
    ) { padding ->
        val bridge = state.lspBridgeStatus
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AbkScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExpressiveHeroCard(
                title = state.abkRuntimeStatus?.manager?.displayName?.ifBlank { "ABK LSP Bridge" } ?: "ABK LSP Bridge",
                subtitle = state.abkRuntimeStatus?.runtimeBackend?.version?.ifBlank { "未提供版本" } ?: "未连接到 LSP bridge",
                icon = Icons.Default.Memory,
                badge = {
                    ExpressiveStatusChip(
                        label = state.abkRuntimeStatus?.runtimeBackend?.backend?.ifBlank { "lsp_bridge" } ?: "inactive",
                        icon = Icons.Default.Tune,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ExpressiveStatusChip(
                        label = if (bridge?.safeMode == true) "安全模式" else "桥接活动",
                        icon = Icons.Default.Security,
                        color = if (bridge?.safeMode == true) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        }
                    )
                    ExpressiveStatusChip(
                        label = "模块 ${state.lspInstalledModules.count { it.enabled }} / ${state.lspInstalledModules.size}",
                        icon = Icons.Default.Extension,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            ) {}

            if (state.abkRuntimeLoading || state.lspInstalledModulesLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            ExpressiveSectionCard(
                title = "Bridge 状态",
                subtitle = "当前 LSP bridge / helper / daemon 运行状态",
                icon = Icons.Default.Tune
            ) {
                LspStatusLine("安全模式", if (bridge?.safeMode == true) "开启" else "关闭")
                LspStatusLine("Helper", if (bridge?.helperActive == true) "active" else "inactive")
                LspStatusLine("Daemon", if (bridge?.daemonActive == true) "active" else "inactive")
                LspStatusLine("Zygote", if (bridge?.zygoteAttached == true) "attached" else "detached")
                LspStatusLine("Payload", if (bridge?.payloadReady == true) "ready" else "not ready")
                LspStatusLine("Runtime", if (bridge?.runtimeReady == true) "ready" else "not ready")
                LspStatusLine("模块", "${bridge?.managedModuleCount ?: 0}")
                LspStatusLine("作用域", "${bridge?.scopeCount ?: 0}")
                LspStatusLine("目标进程", "${bridge?.targetStateCount ?: 0}")
                LspStatusLine("已加载", "${bridge?.loadedModuleCount ?: 0}")
                LspStatusLine("Hook", "${bridge?.activeHookCount ?: 0}")
                if (bridge != null && !bridge.runtimeReady) {
                    Text(
                        text = "配置启用不等于模块已激活；目标应用内显示已激活需要 payload 注入并完成 ART/Xposed hook。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (!bridge?.lastError.isNullOrBlank()) {
                    Text(
                        text = "最近错误: ${bridge?.lastError}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (bridge?.diagnostics?.isNotEmpty() == true) {
                    Text(
                        text = bridge.diagnostics.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (state.abkRuntimeError != null) {
                    Text(
                        text = state.abkRuntimeError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = vm::syncLspBridgeConfiguration,
                    enabled = !state.abkRuntimeLoading
                ) {
                    Text("同步模块配置到 Bridge")
                }
            }

            ExpressiveSectionCard(
                title = "模块概览",
                subtitle = "已识别的 LSPosed/Xposed 模块与当前启用态",
                icon = Icons.Default.Extension
            ) {
                val enabledCount = state.lspInstalledModules.count { it.enabled }
                Text(
                    text = "已识别 ${state.lspInstalledModules.size} 个模块，当前启用 $enabledCount 个。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.lspInstalledModules.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {},
                            label = { Text("作用域 ${bridge?.scopeCount ?: 0}") }
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text("插件 ${bridge?.pluginCount ?: 0}") }
                        )
                    }
                }
            }

            Spacer(Modifier.height(80.dp + outerPadding.calculateBottomPadding()))
        }
    }
}

@Composable
fun LspModulesScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onOpenScope: (String) -> Unit
) {
    val state by vm.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var query by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable { mutableStateOf(LspModuleSortMode.NAME) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.refreshLspInstalledModules()
        vm.refreshAbkRuntimeStatus()
    }

    val modules = remember(state.lspInstalledModules, query, sortMode) {
        state.lspInstalledModules
            .asSequence()
            .filter { module ->
                val needle = query.trim().lowercase()
                needle.isBlank() ||
                    module.label.lowercase().contains(needle) ||
                    module.packageName.lowercase().contains(needle)
            }
            .sortedWith(moduleComparator(sortMode))
            .toList()
    }

    Scaffold(
        containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
        topBar = {
            ExpressiveTopBar(
                title = "LSP 模块",
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = {
                        vm.refreshLspInstalledModules()
                        vm.refreshAbkRuntimeStatus()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.runtime_refresh))
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "模块排序")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            LspModuleSortMode.values().forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label) },
                                    leadingIcon = if (sortMode == mode) {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        sortMode = mode
                                        menuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AbkScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.lspInstalledModulesLoading || state.abkRuntimeLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("搜索模块") },
                placeholder = { Text("应用名或包名") }
            )
            state.lspInstalledModulesError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (state.lspInstalledModules.isEmpty() && !state.lspInstalledModulesLoading) {
                Text(
                    text = "未发现兼容的 LSPosed/Xposed 模块 APK",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.lspInstalledModules.isNotEmpty() && modules.isEmpty()) {
                Text(
                    text = "没有匹配的模块",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            modules.forEach { module ->
                LspModuleListCard(
                    module = module,
                    actionInFlight = state.lspModuleActionPackage == module.packageName,
                    onEnabledChange = { enabled -> vm.setLspModuleEnabled(module.packageName, enabled) },
                    onOpenDetail = {
                        vm.setSelectedLspModulePackage(module.packageName)
                        onOpenScope(module.packageName)
                    }
                )
            }
            Spacer(Modifier.height(80.dp + outerPadding.calculateBottomPadding()))
        }
    }
}

@Composable
fun LspScopeScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onBack: () -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var query by rememberSaveable { mutableStateOf("") }
    var showSystemApps by rememberSaveable { mutableStateOf(true) }
    var sortMode by rememberSaveable { mutableStateOf(LspScopeSortMode.NAME) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.refreshLspInstalledModules()
        vm.refreshLspScopeApps()
        vm.refreshAbkRuntimeStatus()
    }

    val modules = state.lspInstalledModules
    val selectedModule = modules.firstOrNull { it.packageName == state.selectedLspModulePackage } ?: modules.firstOrNull()
    var draftScope by remember(selectedModule?.packageName, selectedModule?.selectedScope) {
        mutableStateOf(selectedModule?.selectedScope?.toSet().orEmpty())
    }
    val recommendedScope = remember(selectedModule?.packageName, selectedModule?.scopeHints) {
        selectedModule?.scopeHints.orEmpty().filter { it.isNotBlank() }.toSet()
    }
    val filteredApps = remember(state.lspScopeApps, query, showSystemApps, sortMode, recommendedScope) {
        state.lspScopeApps
            .asSequence()
            .filter { showSystemApps || !it.isSystemApp }
            .filter { app ->
                val needle = query.trim().lowercase()
                needle.isBlank() ||
                    app.label.lowercase().contains(needle) ||
                    app.packageName.lowercase().contains(needle)
            }
            .sortedWith(scopeAppComparator(sortMode, recommendedScope))
            .toList()
    }

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
        topBar = {
            ExpressiveTopBar(
                title = selectedModule?.let { module -> module.label.ifBlank { module.packageName } } ?: "LSP 模块详情",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回模块列表")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        vm.refreshLspInstalledModules()
                        vm.refreshLspScopeApps()
                        vm.refreshAbkRuntimeStatus()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.runtime_refresh))
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "作用域菜单")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("勾选推荐") },
                                enabled = selectedModule != null &&
                                    recommendedScope.isNotEmpty() &&
                                    state.lspModuleActionPackage == null,
                                onClick = {
                                    selectedModule?.let { module ->
                                        val nextScope = draftScope + recommendedScope
                                        draftScope = nextScope
                                        vm.setLspModuleScope(module.packageName, nextScope)
                                    }
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("清空作用域") },
                                enabled = selectedModule != null &&
                                    draftScope.isNotEmpty() &&
                                    state.lspModuleActionPackage == null,
                                onClick = {
                                    selectedModule?.let { module ->
                                        draftScope = emptySet()
                                        vm.setLspModuleScope(module.packageName, emptySet())
                                    }
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (showSystemApps) "隐藏系统应用" else "显示系统应用") },
                                onClick = {
                                    showSystemApps = !showSystemApps
                                    menuExpanded = false
                                }
                            )
                            LspScopeSortMode.values().forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label) },
                                    leadingIcon = if (sortMode == mode) {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        sortMode = mode
                                        menuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AbkScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.lspScopeAppsLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (selectedModule == null) {
                Text(
                    text = "当前没有可配置作用域的模块。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val module = selectedModule
                LspModuleDetailHeader(
                    module = module,
                    actionInFlight = state.lspModuleActionPackage == module.packageName,
                    onEnabledChange = { enabled -> vm.setLspModuleEnabled(module.packageName, enabled) }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text("推荐 ${recommendedScope.size}") })
                    AssistChip(onClick = {}, label = { Text("已选 ${draftScope.size}") })
                    AssistChip(onClick = {}, label = { Text("显示 ${filteredApps.size}") })
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("搜索作用域应用") },
                    placeholder = { Text("应用名或包名") }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {
                            val nextScope = draftScope + recommendedScope
                            draftScope = nextScope
                            vm.setLspModuleScope(module.packageName, nextScope)
                        },
                        enabled = state.lspModuleActionPackage == null && recommendedScope.isNotEmpty(),
                        label = { Text("勾选推荐") }
                    )
                    AssistChip(
                        onClick = {
                            draftScope = emptySet()
                            vm.setLspModuleScope(module.packageName, emptySet())
                        },
                        enabled = state.lspModuleActionPackage == null && draftScope.isNotEmpty(),
                        label = { Text("清空") }
                    )
                    AssistChip(
                        onClick = { showSystemApps = !showSystemApps },
                        label = { Text(if (showSystemApps) "系统应用: 显示" else "系统应用: 隐藏") }
                    )
                }

                if (filteredApps.isEmpty()) {
                    Text(
                        text = "没有匹配的应用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                filteredApps.forEach { app ->
                    val checked = app.packageName in draftScope
                    ExpressiveListItem(
                        title = app.label.ifBlank { app.packageName },
                        subtitle = buildString {
                            append(app.packageName)
                            append("\n")
                            append(scopeAppTags(app, recommendedScope))
                        },
                        leadingContent = {
                            LspAppIcon(
                                packageName = app.packageName,
                                label = app.label,
                                modifier = Modifier.size(44.dp),
                                cornerSize = 12.dp,
                                fallbackIcon = if (app.isSystemApp) Icons.Default.Security else Icons.Default.Extension
                            )
                        },
                        selected = checked,
                        trailingContent = {
                            Switch(
                                checked = checked,
                                onCheckedChange = { enabled ->
                                    val nextScope = if (enabled) {
                                        draftScope + app.packageName
                                    } else {
                                        draftScope - app.packageName
                                    }
                                    draftScope = nextScope
                                    vm.setLspModuleScope(module.packageName, nextScope)
                                },
                                enabled = state.lspModuleActionPackage == null
                            )
                        },
                        onClick = {
                            if (state.lspModuleActionPackage == null) {
                                val nextScope = if (checked) {
                                    draftScope - app.packageName
                                } else {
                                    draftScope + app.packageName
                                }
                                draftScope = nextScope
                                vm.setLspModuleScope(module.packageName, nextScope)
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(80.dp + outerPadding.calculateBottomPadding()))
        }
    }
}

@Composable
fun LspLogsScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp)
) {
    val state by vm.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) {
        vm.refreshAbkRuntimeStatus()
    }

    Scaffold(
        containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
        topBar = {
            ExpressiveTopBar(
                title = "LSP 日志",
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = vm::refreshAbkRuntimeStatus) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.runtime_refresh))
                    }
                }
            )
        }
    ) { padding ->
        val bridge = state.lspBridgeStatus
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AbkScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ExpressiveSectionCard(
                title = "Runtime Diagnostics",
                subtitle = "bridge / helper / daemon 当前诊断信息",
                icon = Icons.Default.Article
            ) {
                val diagnostics = buildList {
                    addAll(bridge?.diagnostics.orEmpty())
                    addAll(state.abkRuntimeStatus?.manager?.diagnostics.orEmpty())
                    addAll(state.abkRuntimeStatus?.runtimeBackend?.diagnostics.orEmpty())
                }.distinct()
                Text(
                    text = diagnostics.joinToString("\n").ifBlank { state.abkRuntimeError ?: "暂无可用诊断" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ExpressiveSectionCard(
                title = "Bridge Logs",
                subtitle = "最近一次 bridge 操作与状态变更日志",
                icon = Icons.Default.Article
            ) {
                Text(
                    text = bridge?.logs?.joinToString("\n").orEmpty().ifBlank { "暂无 bridge 日志" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (bridge?.targetStates?.isNotEmpty() == true) {
                ExpressiveSectionCard(
                    title = "Target Runtime",
                    subtitle = "目标进程内 payload / runtime / hook 状态",
                    icon = Icons.Default.Memory
                ) {
                    Text(
                        text = bridge.targetStates.joinToString("\n") { target ->
                            buildString {
                                append(target.packageName.ifBlank { "unknown" })
                                append(" / ")
                                append(target.processName.ifBlank { target.packageName })
                                append(" pid=")
                                append(target.pid)
                                append(" payload=")
                                append(if (target.payloadInjected) "yes" else "no")
                                append(" runtime=")
                                append(if (target.runtimeReady) "ready" else "no")
                                append(" hooks=")
                                append(target.activeHookCount)
                                if (target.lastError.isNotBlank()) {
                                    append(" error=")
                                    append(target.lastError)
                                }
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            state.lspInstalledModules.filter { it.lastError.isNotBlank() }.forEach { module ->
                ExpressiveSectionCard(
                    title = module.label.ifBlank { module.packageName },
                    subtitle = module.packageName,
                    icon = Icons.Default.Extension
                ) {
                    Text(
                        text = module.lastError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(80.dp + outerPadding.calculateBottomPadding()))
        }
    }
}

@Composable
private fun LspModuleListCard(
    module: LspInstalledModule,
    actionInFlight: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onOpenDetail: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpenDetail,
        colors = CardDefaults.cardColors(
            containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainer)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LspModuleAvatar(module)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = module.label.ifBlank { module.packageName },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = module.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(lspModuleApiLabel(module)) }
                    )
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                if (module.selectedScope.isEmpty()) {
                                    "无作用域"
                                } else {
                                    "作用域 ${module.selectedScope.size}"
                                }
                            )
                        }
                    )
                    if (module.hookActive) {
                        AssistChip(
                            onClick = {},
                            label = { Text("已激活") }
                        )
                    } else if (module.loaded) {
                        AssistChip(
                            onClick = {},
                            label = { Text("已加载") }
                        )
                    }
                }
                Text(
                    text = buildString {
                        append("版本 ")
                        append(module.versionName.ifBlank { module.versionCode.toString() })
                        append(" · ")
                        append(if (module.enabled) "配置启用" else "未启用")
                        if (module.lastError.isNotBlank()) {
                            append(" · 有错误")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (module.lastError.isNotBlank()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = module.enabled,
                onCheckedChange = onEnabledChange,
                enabled = !actionInFlight
            )
        }
    }
}

@Composable
private fun LspModuleDetailHeader(
    module: LspInstalledModule,
    actionInFlight: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    ExpressiveSectionCard(
        title = module.label.ifBlank { module.packageName },
        subtitle = module.packageName,
        icon = Icons.Default.Extension
    ) {
        ExpressiveListItem(
            title = "启用模块",
            subtitle = if (module.enabled) {
                "已写入 LSP bridge 配置；真正激活仍取决于 runtime hook 状态。"
            } else {
                "关闭后该模块不会进入 LSP bridge 作用域配置。"
            },
            leadingIcon = Icons.Default.Tune,
            trailingContent = {
                Switch(
                    checked = module.enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = !actionInFlight
                )
            },
            onClick = {
                if (!actionInFlight) onEnabledChange(!module.enabled)
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = {}, label = { Text(lspModuleApiLabel(module)) })
            AssistChip(onClick = {}, label = { Text("版本 ${module.versionName.ifBlank { module.versionCode.toString() }}") })
            AssistChip(onClick = {}, label = { Text(if (module.hookActive) "已激活" else if (module.loaded) "已加载" else "未激活") })
        }
        if (module.entryPoints.isNotEmpty() || module.compatEntryPoints.isNotEmpty()) {
            Text(
                text = buildString {
                    if (module.entryPoints.isNotEmpty()) {
                        append("入口: ")
                        append(module.entryPoints.joinToString(", "))
                    }
                    if (module.compatEntryPoints.isNotEmpty()) {
                        if (isNotEmpty()) append("\n")
                        append("兼容入口: ")
                        append(module.compatEntryPoints.joinToString(", "))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (module.description.isNotBlank()) {
            Text(
                text = module.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (module.lastError.isNotBlank()) {
            Text(
                text = "最近错误: ${module.lastError}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun LspModuleAvatar(module: LspInstalledModule) {
    LspAppIcon(
        packageName = module.packageName,
        label = module.label,
        modifier = Modifier.size(52.dp),
        cornerSize = 14.dp,
        fallbackIcon = Icons.Default.Extension
    )
}

@Composable
private fun LspAppIcon(
    packageName: String,
    label: String,
    modifier: Modifier = Modifier.size(52.dp),
    cornerSize: Dp = 14.dp,
    fallbackIcon: ImageVector = Icons.Default.Extension
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
        return
    }

    Box(
        modifier = iconModifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp)
        )
    }
}

private enum class LspModuleSortMode(val label: String) {
    NAME("按应用名"),
    PACKAGE("按包名"),
    INSTALL_TIME("按安装时间"),
    UPDATE_TIME("按更新时间")
}

private enum class LspScopeSortMode(val label: String) {
    NAME("按应用名"),
    PACKAGE("按包名"),
    INSTALL_TIME("按安装时间"),
    UPDATE_TIME("按更新时间")
}

private fun moduleComparator(mode: LspModuleSortMode): Comparator<LspInstalledModule> = when (mode) {
    LspModuleSortMode.NAME -> compareBy<LspInstalledModule> { it.label.lowercase() }.thenBy { it.packageName }
    LspModuleSortMode.PACKAGE -> compareBy { it.packageName }
    LspModuleSortMode.INSTALL_TIME -> compareByDescending<LspInstalledModule> { it.installTime }.thenBy { it.label.lowercase() }
    LspModuleSortMode.UPDATE_TIME -> compareByDescending<LspInstalledModule> { it.updateTime }.thenBy { it.label.lowercase() }
}

private fun scopeAppComparator(
    mode: LspScopeSortMode,
    recommendedScope: Set<String>
): Comparator<LspScopeApp> {
    val recommendationOrder = compareByDescending<LspScopeApp> { it.packageName in recommendedScope }
    val selectedMode = when (mode) {
        LspScopeSortMode.NAME -> compareBy<LspScopeApp> { it.label.lowercase() }.thenBy { it.packageName }
        LspScopeSortMode.PACKAGE -> compareBy { it.packageName }
        LspScopeSortMode.INSTALL_TIME -> compareByDescending<LspScopeApp> { it.installTime }.thenBy { it.label.lowercase() }
        LspScopeSortMode.UPDATE_TIME -> compareByDescending<LspScopeApp> { it.updateTime }.thenBy { it.label.lowercase() }
    }
    return recommendationOrder.then(selectedMode)
}

private fun lspModuleApiLabel(module: LspInstalledModule): String = when {
    module.modern && module.legacy -> "API ${module.targetVersion.takeIf { it > 0 } ?: module.minVersion} + legacy"
    module.modern -> "API ${module.targetVersion.takeIf { it > 0 } ?: module.minVersion}"
    module.legacy -> "legacy"
    else -> "unknown"
}

private fun scopeAppTags(app: LspScopeApp, recommendedScope: Set<String>): String =
    buildList {
        add(if (app.isSystemApp) "系统应用" else "用户应用")
        if (app.packageName in recommendedScope) add("推荐应用")
        if (app.isGame) add("游戏")
        if (app.isModule) add("模块")
        if (app.versionName.isNotBlank()) add("版本 ${app.versionName}")
    }.joinToString(" · ")

@Composable
private fun LspStatusLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(88.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
