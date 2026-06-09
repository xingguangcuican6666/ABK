package com.abk.kernel.ui.screens.miuix

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.R
import com.abk.kernel.data.model.CustomExternalModuleStage
import com.abk.kernel.data.model.ModuleCatalogItem
import com.abk.kernel.data.model.ModuleCatalogRepository
import com.abk.kernel.data.model.RuntimeModuleCatalogItem
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.File
import androidx.core.net.toUri

private const val RUNTIME_MODULE_DOWNLOAD_RUN_ID = -2_000_000_001L

// ── BUILD_ABK mode data types ──
private data class MergedMod(val item: ModuleCatalogItem, val repo: ModuleCatalogRepository)

// ── RUNTIME_STANDARD mode data types ──
private data class MergedRtModule(val module: RuntimeModuleCatalogItem, val sources: List<String>)

@Composable
fun MiuixModuleRepositoryScreen(vm: MainViewModel, outerPadding: PaddingValues) {
    val state by vm.uiState.collectAsState()
    if (state.runtimeNavigationEnabled) {
        MiuixRuntimeModuleRepoContent(vm, outerPadding)
    } else {
        MiuixBuildModuleRepoContent(vm, outerPadding)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// BUILD_ABK — 构建模块仓库
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun MiuixBuildModuleRepoContent(vm: MainViewModel, outerPadding: PaddingValues) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val scrollBehavior = LocalMiuixScrollBehavior.current
    var searchQuery by remember { mutableStateOf("") }
    var pendingModule by remember { mutableStateOf<ModuleCatalogItem?>(null) }
    var selectedStages by remember { mutableStateOf(emptyList<String>()) }
    val addedToBuildMsg = stringResource(R.string.module_repo_added_to_build)

    val repos = state.buildModuleRepositories
    val allModules = remember(repos) { repos.flatMap { r -> r.modules.map { MergedMod(it, r) } } }
    val selectedUrls = remember(state.buildConfig.customExternalModules) {
        state.buildConfig.customExternalModules.map { it.url.trim().lowercase() to CustomExternalModuleStage.normalize(it.stage) }.toSet()
    }
    val filtered = remember(allModules, searchQuery) {
        if (searchQuery.isBlank()) allModules
        else allModules.filter { (m, _) -> m.name.contains(searchQuery, ignoreCase = true) || m.description.contains(searchQuery, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().padding(outerPadding).padding(horizontal = 12.dp).then(
        if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier
    )) {
        Spacer(Modifier.height(8.dp))
        var searchExpanded by remember { mutableStateOf(false) }
        SearchBar(
            modifier = Modifier.fillMaxWidth(),
            inputField = {
                InputField(
                    query = searchQuery, onQueryChange = { searchQuery = it },
                    onSearch = { searchExpanded = false },
                    expanded = searchExpanded, onExpandedChange = { searchExpanded = it },
                    label = stringResource(R.string.module_repo_search)
                )
            },
            expanded = searchExpanded, onExpandedChange = { searchExpanded = it }
        ) {}
        Spacer(Modifier.height(8.dp))

        if (allModules.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Extension, null, modifier = Modifier.size(48.dp), tint = colorScheme.onSurfaceVariantActions)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.module_repo_refresh_hint), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.item.repoUrl + it.item.name }) { (module, _) ->
                    val supportedStages = module.supportedStages.map { CustomExternalModuleStage.normalize(it) }.distinct()
                    val allStagesAdded = supportedStages.all { stage -> module.repoUrl.trim().lowercase() to stage in selectedUrls }

                    Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(module.buildDisplayName(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val meta = module.buildMetaLine()
                                if (meta.isNotBlank()) Text(meta, fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                            }
                            if (module.description.isNotBlank()) Text(module.description, fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                BModTagChip(module.repoUrl.repoName(), secondary = false)
                                supportedStages.take(2).forEach { stage -> BModTagChip(stage, secondary = true) }
                                if (allStagesAdded) BModTagChip(stringResource(R.string.module_repo_joined), secondary = true)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                BModActionButton(Icons.Filled.OpenInBrowser, stringResource(R.string.module_repo_open_repo)) {
                                    val url = module.homepage.ifBlank { module.repoUrl }
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                                }
                                Spacer(Modifier.width(6.dp))
                                BModActionButton(
                                    icon = if (allStagesAdded) Icons.Filled.CheckCircle else Icons.Filled.Add,
                                    contentDescription = if (allStagesAdded) stringResource(R.string.module_repo_joined) else stringResource(R.string.module_repo_add_to_build),
                                    enabled = !allStagesAdded
                                ) {
                                    pendingModule = module
                                    selectedStages = module.initialStageSelection(selectedUrls)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Stage selection dialog
    pendingModule?.let { module ->
        val stages = module.supportedStages.map { CustomExternalModuleStage.normalize(it) }.distinct()
        val recommended = module.recommendedStages.map { CustomExternalModuleStage.normalize(it) }.toSet()
        WindowDialog(title = stringResource(R.string.module_repo_select_stage), show = true, onDismissRequest = { pendingModule = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(module.buildDisplayName(), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                if (module.version.isNotBlank()) Text(stringResource(R.string.module_repo_version, module.version), fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
                stages.forEach { stage ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (stage in recommended) "$stage ★" else stage, Modifier.weight(1f), fontSize = 14.sp, color = colorScheme.onSurface)
                        Switch(checked = stage in selectedStages, onCheckedChange = { c -> selectedStages = if (c) (selectedStages + stage).distinct() else selectedStages - stage })
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = stringResource(R.string.module_repo_all_stages), onClick = { vm.addCustomExternalModulesFromUrl(module.repoUrl, stages); pendingModule = null; Toast.makeText(context, addedToBuildMsg, Toast.LENGTH_SHORT).show() }, modifier = Modifier.weight(1f))
                Button(onClick = { val s = selectedStages.filter { it in stages }; vm.addCustomExternalModulesFromUrl(module.repoUrl, s.ifEmpty { listOf(module.defaultStage) }); pendingModule = null; Toast.makeText(context, addedToBuildMsg, Toast.LENGTH_SHORT).show() }, colors = ButtonDefaults.buttonColorsPrimary(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.module_repo_add_selected)) }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// RUNTIME_STANDARD — 标准模块仓库（支持在线安装）
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun MiuixRuntimeModuleRepoContent(vm: MainViewModel, outerPadding: PaddingValues) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = LocalMiuixScrollBehavior.current
    var searchQuery by remember { mutableStateOf("") }
    var pendingInstallModule by remember { mutableStateOf<MergedRtModule?>(null) }
    var installDialogVisible by remember { mutableStateOf(false) }
    var installRunning by remember { mutableStateOf(false) }
    var installSuccess by remember { mutableStateOf<Boolean?>(null) }
    var installLog by remember { mutableStateOf<List<String>>(emptyList()) }
    val waitRootShellMsg = stringResource(R.string.runtime_wait_root_shell)
    val managerInactiveMsg = stringResource(R.string.runtime_manager_inactive)
    val installDoneMsg = stringResource(R.string.runtime_module_install_done_no_output)
    val installFailedMsg = stringResource(R.string.runtime_module_install_failed_no_log)
    val openRepoMsg = stringResource(R.string.module_repo_open_repo)
    val openFailedMsg = stringResource(R.string.module_repo_open_failed)
    val installModuleMsg = stringResource(R.string.runtime_install_module)

    val repos = state.runtimeModuleRepositories
    val allModules = remember(repos) {
        if (repos.isEmpty()) emptyList()
        else repos.flatMap { r -> r.modules.map { r.name.ifBlank { r.url } to it } }
            .groupBy({ it.second.id.ifBlank { it.second.name } }) { it.first to it.second }
            .map { (_, items) ->
                val m = items.first().second
                MergedRtModule(m, items.map { it.first }.distinct().sorted())
            }
            .sortedBy { it.module.name.lowercase() }
    }
    val listComputing = repos.isNotEmpty() && allModules.isEmpty()
    val filtered = remember(allModules, searchQuery) {
        if (searchQuery.isBlank()) allModules
        else allModules.filter { (m, _) -> m.name.contains(searchQuery, ignoreCase = true) || m.description.contains(searchQuery, ignoreCase = true) }
    }

    fun startInstall(merged: MergedRtModule) {
        if (installRunning) return
        pendingInstallModule = null
        installDialogVisible = true; installRunning = true; installSuccess = null
        installLog = listOf("$ module install", "name: ${merged.module.name}", "source: ${merged.module.zipUrl}", "", "Downloading…")
        scope.launch {
            val downloadResult = withContext(Dispatchers.IO) {
                DownloadUtils.downloadDirectAsset(
                    context = context, token = null, url = merged.module.zipUrl,
                    name = merged.module.downloadFileName(), sizeBytes = 0L,
                    runId = RUNTIME_MODULE_DOWNLOAD_RUN_ID,
                    runTitle = merged.sources.firstOrNull().orEmpty().ifBlank { "Unknown" },
                    downloadDirectoryPath = state.downloadDirectory
                )
            }
            val downloadedFile = downloadResult.artifacts.firstOrNull()?.filePath?.let(::File)
            if (downloadedFile == null || !downloadedFile.exists()) {
                installRunning = false; installSuccess = false
                installLog = installLog + listOf("", downloadResult.errorMessage ?: "Download failed")
                return@launch
            }
            installLog = installLog + listOf("file: ${downloadedFile.absolutePath}", waitRootShellMsg)
            val result = withContext(Dispatchers.IO) {
                if (!RootUtils.refreshRootState()) RootUtils.ShellResult(false, listOf(managerInactiveMsg))
                else RootUtils.installModule(downloadedFile.absolutePath) { line -> scope.launch(Dispatchers.Main.immediate) { installLog = installLog + line } }
            }
            installRunning = false; installSuccess = result.success
            installLog = listOf($$"$ module install $${downloadedFile.name}", "file: ${downloadedFile.absolutePath}", "") +
                result.output.ifEmpty { listOf(if (result.success) installDoneMsg else installFailedMsg) }
            if (result.success) vm.refreshAbkRuntimeStatus()
        }
    }

    Column(Modifier.fillMaxSize().padding(outerPadding).padding(horizontal = 12.dp).then(
        if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier
    )) {
        Spacer(Modifier.height(8.dp))
        var searchExpanded by remember { mutableStateOf(false) }
        SearchBar(
            modifier = Modifier.fillMaxWidth(),
            inputField = {
                InputField(
                    query = searchQuery, onQueryChange = { searchQuery = it },
                    onSearch = { searchExpanded = false },
                    expanded = searchExpanded, onExpandedChange = { searchExpanded = it },
                    label = stringResource(R.string.module_repo_search)
                )
            },
            expanded = searchExpanded, onExpandedChange = { searchExpanded = it }
        ) {}
        Spacer(Modifier.height(8.dp))

        if (listComputing) {
            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(size = 36.dp, strokeWidth = 3.dp)
                    Text(stringResource(R.string.module_repo_building_list), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                }
            }
        } else if (repos.isEmpty() || allModules.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Extension, null, modifier = Modifier.size(48.dp), tint = colorScheme.onSurfaceVariantActions)
                    Spacer(Modifier.height(12.dp))
                    Text(if (repos.isEmpty()) "暂无运行时模块仓库，请在仓库管理中配置" else stringResource(R.string.module_repo_refresh_hint), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { "${it.module.id}-${it.sources.joinToString("|")}" }) { merged ->
                    val module = merged.module
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(module.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    val meta = module.metaLine()
                                    if (meta.isNotBlank()) Text(meta, fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                                }
                                if (merged.sources.size > 1) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Icon(Icons.Filled.Source, null, tint = colorScheme.onSurfaceVariantActions, modifier = Modifier.size(16.dp))
                                        Text("${merged.sources.size}", fontSize = 13.sp, color = colorScheme.onSurfaceVariantActions)
                                    }
                                }
                            }
                            if (module.description.isNotBlank()) Text(module.description, fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                RModTagChip(module.id.ifBlank { module.name })
                                module.minApi?.let { RModTagChip("API >= $it", secondary = true) }
                                module.maxApi?.let { RModTagChip("API <= $it", secondary = true) }
                                if (module.verified) RModTagChip("Verified", secondary = true)
                                if (merged.sources.size > 1) RModTagChip(stringResource(R.string.module_repo_source_count, merged.sources.size), secondary = true)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                RModActionButton(Icons.Filled.OpenInBrowser, openRepoMsg) {
                                    val url = module.preferredOpenUrl()
                                    if (url.isBlank()) Toast.makeText(context, openFailedMsg, Toast.LENGTH_SHORT).show()
                                    else runCatching { uriHandler.openUri(url) }.onFailure { Toast.makeText(context, openFailedMsg, Toast.LENGTH_SHORT).show() }
                                }
                                Spacer(Modifier.width(6.dp))
                                RModActionButton(Icons.Filled.UploadFile, installModuleMsg, enabled = module.zipUrl.isNotBlank()) {
                                    if (module.zipUrl.isBlank()) Toast.makeText(context, "No zip URL available", Toast.LENGTH_SHORT).show()
                                    else pendingInstallModule = merged
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Dialogs ──
    pendingInstallModule?.let { merged ->
        WindowDialog(title = stringResource(R.string.runtime_confirm_flash_module), show = true, onDismissRequest = { pendingInstallModule = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(merged.module.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface)
                Text(merged.module.zipUrl, fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.runtime_confirm_flash_module_desc), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = stringResource(R.string.cancel), onClick = { pendingInstallModule = null }, modifier = Modifier.weight(1f))
                Button(onClick = { startInstall(merged) }, colors = ButtonDefaults.buttonColorsPrimary(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.runtime_confirm_flash)) }
            }
        }
    }

    if (installDialogVisible) {
        WindowDialog(
            title = if (installRunning) stringResource(R.string.runtime_installing_module) else stringResource(R.string.runtime_install_module),
            show = true, onDismissRequest = { if (!installRunning) installDialogVisible = false }
        ) {
            val scrollState = rememberScrollState()
            LaunchedEffect(installLog.size) { scrollState.animateScrollTo(scrollState.maxValue) }
            Box(
                Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp)
                    .clip(RoundedCornerShape(8.dp)).background(colorScheme.surfaceContainer).padding(12.dp)
                    .verticalScroll(scrollState)
            ) {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        installLog.ifEmpty { listOf(stringResource(R.string.runtime_waiting_output)) }.forEach { line ->
                            Text(line, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                color = if (line.startsWith("$")) colorScheme.primary else colorScheme.onSurfaceVariantSummary)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (installRunning) TextButton(text = stringResource(R.string.runtime_running), onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
                else {
                    TextButton(text = stringResource(R.string.close), onClick = { installDialogVisible = false }, modifier = Modifier.weight(1f))
                    if (installSuccess == true) Button(onClick = { scope.launch(Dispatchers.IO) { RootUtils.reboot() } }, colors = ButtonDefaults.buttonColors(
                        colorScheme.error), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.runtime_reboot)) }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Shared chip / button helpers
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun BModTagChip(label: String, secondary: Boolean) {
    val bg = if (secondary) colorScheme.primary.copy(alpha = 0.12f) else colorScheme.primary.copy(alpha = 0.18f)
    val fg = if (secondary) colorScheme.onSurfaceVariantSummary else colorScheme.primary
    Box(Modifier.background(bg, RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(label, fontSize = 11.sp, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RModTagChip(label: String, secondary: Boolean = false) {
    val fg = if (secondary) colorScheme.onSurfaceVariantSummary else colorScheme.primary
    Box(Modifier.background(fg.copy(alpha = if (secondary) 0.12f else 0.14f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(label, fontSize = 11.sp, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BModActionButton(icon: ImageVector, contentDescription: String, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = colorScheme.primary.copy(alpha = if (enabled) 0.82f else 0.44f)
    val fg = if (enabled) colorScheme.onPrimary else colorScheme.onSurfaceVariantActions
    Box(Modifier.size(width = 42.dp, height = 36.dp).background(bg, RoundedCornerShape(18.dp)).clickable(enabled = enabled) { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription, modifier = Modifier.size(19.dp), tint = fg)
    }
}

@Composable
private fun RModActionButton(icon: ImageVector, contentDescription: String, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = colorScheme.primary.copy(alpha = if (enabled) 0.82f else 0.44f)
    val fg = if (enabled) colorScheme.onPrimary else colorScheme.onSurfaceVariantActions
    Box(Modifier.size(width = 42.dp, height = 36.dp).background(bg, RoundedCornerShape(18.dp)).clickable(enabled = enabled) { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription, modifier = Modifier.size(19.dp), tint = fg)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Extension: module display helpers
// ═══════════════════════════════════════════════════════════════════════════

private fun ModuleCatalogItem.buildDisplayName(): String = name.ifBlank { repoUrl.trim().trimEnd('/').removeSuffix(".git").substringAfterLast('/') }

@Composable
private fun ModuleCatalogItem.buildMetaLine(): String = listOfNotNull(
    version.takeIf { it.isNotBlank() }?.let { stringResource(R.string.module_repo_version, it) },
    author.takeIf { it.isNotBlank() }?.let { stringResource(R.string.runtime_module_author, it) }
).joinToString(" · ")

private fun String.repoName(): String = trim().trimEnd('/').removeSuffix(".git").substringAfterLast('/').ifBlank { trim().trimEnd('/').substringAfterLast('/') }

private fun ModuleCatalogItem.initialStageSelection(selectedModules: Set<Pair<String, String>>): List<String> {
    val moduleUrl = repoUrl.trim().lowercase()
    val normalizedRecommended = recommendedStages.map { CustomExternalModuleStage.normalize(it) }.distinct()
    val normalizedSupported = supportedStages.map { CustomExternalModuleStage.normalize(it) }.distinct()
    val remainingRecommended = normalizedRecommended.filterNot { stage -> moduleUrl to stage in selectedModules }
    val remainingSupported = normalizedSupported.filterNot { stage -> moduleUrl to stage in selectedModules }
    return remainingRecommended.ifEmpty { remainingSupported.take(1) }.ifEmpty { normalizedRecommended }
}

@Composable
private fun RuntimeModuleCatalogItem.metaLine(): String = listOfNotNull(
    version.takeIf { it.isNotBlank() }?.let { stringResource(R.string.module_repo_version, it) },
    author.takeIf { it.isNotBlank() }?.let { stringResource(R.string.runtime_module_author, it) }
).joinToString(" · ")

private fun RuntimeModuleCatalogItem.preferredOpenUrl(): String =
    support.takeIf { it.isNotBlank() }
        ?: website.takeIf { it.isNotBlank() }
        ?: donate.takeIf { it.isNotBlank() }
        ?: zipUrl

private fun RuntimeModuleCatalogItem.downloadFileName(): String {
    val base = name.ifBlank { id }.ifBlank { "module" }
    val ext = zipUrl.substringAfterLast('.').substringBefore('?').ifBlank { "zip" }
    return "${base}.$ext"
}
