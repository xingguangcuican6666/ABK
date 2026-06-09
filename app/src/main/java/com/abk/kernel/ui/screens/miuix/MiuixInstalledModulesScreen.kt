package com.abk.kernel.ui.screens.miuix

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.abk.kernel.R
import com.abk.kernel.data.model.AbkRuntimeModule
import com.abk.kernel.ui.webui.ModuleWebUiActivity
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.utils.findActivity
import com.abk.kernel.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.File

private val MODULE_INSTALL_MIME_TYPES = arrayOf(
    "application/zip", "application/x-zip", "application/octet-stream",
    "application/x-zip-compressed", "*/*"
)

@Composable
fun MiuixInstalledModulesScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues,
    pendingModuleInstallUri: String? = null,
    onPendingModuleInstallUriConsumed: () -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var pendingInstallUri by remember { mutableStateOf<Uri?>(null) }
    var installDialogVisible by remember { mutableStateOf(false) }
    var installRunning by remember { mutableStateOf(false) }
    var installSuccess by remember { mutableStateOf<Boolean?>(null) }
    var installLog by remember { mutableStateOf<List<String>>(emptyList()) }
    var showAllFilesAccessPrompt by remember { mutableStateOf(false) }
    var resumeModulePickerAfterPermission by remember { mutableStateOf(false) }
    var uninstallTarget by remember { mutableStateOf<AbkRuntimeModule?>(null) }

    val modules = remember(state.abkRuntimeStatus?.modules, query) {
        state.abkRuntimeStatus?.modules.orEmpty()
            .filter { it.matchesQuery(query) }
            .sortedWith(
                compareBy<AbkRuntimeModule> { it.typeOrder() }
                    .thenBy { !it.enabled }
                    .thenBy { it.displayName().lowercase() }
            )
    }

    // ── Module picker ──
    val modulePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pendingInstallUri = uri }

    val allFilesAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (resumeModulePickerAfterPermission) {
            if (hasFullFileAccess()) { resumeModulePickerAfterPermission = false; modulePicker.launch(MODULE_INSTALL_MIME_TYPES) }
            else showAllFilesAccessPrompt = true
        }
    }

    fun launchModulePicker() {
        if (installRunning) return
        if (hasFullFileAccess()) modulePicker.launch(MODULE_INSTALL_MIME_TYPES)
        else { resumeModulePickerAfterPermission = true; showAllFilesAccessPrompt = true }
    }

    @Suppress("NewApi")
    fun openAllFilesSettings() {
        showAllFilesAccessPrompt = false; resumeModulePickerAfterPermission = true
        val appSettings = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:${context.packageName}".toUri())
        runCatching { allFilesAccessLauncher.launch(appSettings) }
            .getOrElse { runCatching { allFilesAccessLauncher.launch(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)) } }
    }

    // Pre-compute resource strings for install coroutine
    val copyingLabel = stringResource(R.string.runtime_copying_module)
    val managerInactiveLabel = stringResource(R.string.runtime_manager_inactive)
    val fileReadFailedLabel = stringResource(R.string.runtime_module_file_read_failed)
    val tempFileMissingLabel = stringResource(R.string.runtime_temp_file_missing)
    val installDoneLabel = stringResource(R.string.runtime_module_install_done_no_output)
    val installFailedLabel = stringResource(R.string.runtime_module_install_failed_no_log)

    @Suppress("NewApi")
    fun installModule(uri: Uri) {
        if (installRunning) return
        installDialogVisible = true; installRunning = true; installSuccess = null
        installLog = listOf("$ module install", "source: $uri", "", copyingLabel)
        scope.launch {
            var stagedName = "module.zip"; var stagedPath = ""
            val result = withContext(Dispatchers.IO) {
                var stagedFile: File? = null
                runCatching {
                    stagedFile = copyModuleUriToCache(context, uri).also { stagedName = it.name; stagedPath = it.absolutePath }
                    if (!RootUtils.refreshRootState()) RootUtils.ShellResult(false, listOf(managerInactiveLabel))
                    else RootUtils.installModule(stagedPath) { line -> scope.launch(Dispatchers.Main.immediate) { installLog += line } }
                }.getOrElse { RootUtils.ShellResult(false, listOf(fileReadFailedLabel)) }
                    .also { stagedFile?.delete() }
            }
            installRunning = false; installSuccess = result.success
            installLog = listOf("$ module install $stagedName", "file: ${stagedPath.ifBlank { tempFileMissingLabel }}") +
                result.output.ifEmpty { listOf(if (result.success) installDoneLabel else installFailedLabel) }
            if (result.success) vm.refreshAbkRuntimeStatus()
        }
    }

    LaunchedEffect(state.runtimeNavigationEnabled, state.rootGranted) {
        if (state.runtimeNavigationEnabled) vm.refreshAbkRuntimeStatus()
    }
    LaunchedEffect(pendingModuleInstallUri) {
        if (!pendingModuleInstallUri.isNullOrBlank()) {
            runCatching { pendingModuleInstallUri.toUri() }.getOrNull()?.let { pendingInstallUri = it }
            onPendingModuleInstallUriConsumed()
        }
    }

    // ── Main layout ──
    val scrollBehavior = LocalMiuixScrollBehavior.current
    Box(Modifier.fillMaxSize().padding(outerPadding)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp).then(
            if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier
        )) {
            Spacer(Modifier.height(8.dp))

            // 搜索框
            var searchExpanded by remember { mutableStateOf(false) }
            SearchBar(
                modifier = Modifier.fillMaxWidth(),
                inputField = {
                    InputField(
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = { searchExpanded = false },
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it },
                        label = stringResource(R.string.runtime_search_installed_modules)
                    )
                },
                expanded = searchExpanded,
                onExpandedChange = { searchExpanded = it }
            ) {}

            Spacer(Modifier.height(8.dp))

            // 模块列表（含状态卡片）
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 加载指示器
                if (state.abkRuntimeLoading) {
                    item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                }

                // 错误卡片
                state.abkRuntimeError?.let { err ->
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(err, fontSize = 14.sp, color = colorScheme.error)
                                Button(
                                    onClick = { vm.refreshAbkRuntimeStatus() },
                                    colors = ButtonDefaults.buttonColors(),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(stringResource(R.string.runtime_recheck)) }
                            }
                        }
                    }
                }

                // 空状态
                if (state.abkRuntimeStatus != null && modules.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.Extension, null, modifier = Modifier.size(48.dp), tint = colorScheme.onSurfaceVariantActions)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    if (query.isBlank()) stringResource(R.string.runtime_no_reported_modules)
                                    else stringResource(R.string.runtime_no_matching_modules),
                                    fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // 模块列表项
                items(modules, key = { it.id }) { module ->
                            ModuleCard(
                                module = module,
                                actionInFlight = state.abkRuntimeModuleActionId == module.id,
                                onSetEnabled = { enabled -> vm.setAbkRuntimeModuleEnabled(module.id, enabled) },
                                onRequestUninstall = { uninstallTarget = module },
                                onRunAction = { vm.runRuntimeModuleAction(module.id) },
                                onOpenWebUi = {
                                    context.findActivity()?.startActivity(
                                        Intent(context, ModuleWebUiActivity::class.java).apply {
                                            putExtra(ModuleWebUiActivity.EXTRA_MODULE_ID, module.id)
                                            putExtra(ModuleWebUiActivity.EXTRA_MODULE_NAME, module.displayName())
                                        }
                                    )
                                }
                            )
                        }
                    }
            }

        // ── FAB 安装按钮 ──
        FloatingActionButton(
            onClick = { launchModulePicker() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 100.dp, end = 16.dp)
        ) {
            Icon(Icons.Filled.Add, stringResource(R.string.runtime_install_module), tint = colorScheme.onPrimary)
        }
    }

    // ── 对话框 ──
    if (state.abkRuntimeModuleActionTitle != null) {
        WindowDialog(title = state.abkRuntimeModuleActionTitle.orEmpty(), show = true, onDismissRequest = vm::dismissRuntimeModuleActionOutput) {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.abkRuntimeModuleActionId != null) { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                Text(
                    text = state.abkRuntimeModuleActionOutput.ifEmpty { listOf(stringResource(R.string.runtime_waiting_output)) }.joinToString("\n"),
                    fontSize = 13.sp, color = colorScheme.onSurface
                )
            }
            TextButton(text = stringResource(R.string.close), onClick = vm::dismissRuntimeModuleActionOutput, modifier = Modifier.fillMaxWidth())
        }
    }

    if (showAllFilesAccessPrompt) {
        WindowDialog(title = stringResource(R.string.runtime_file_access_required), show = true, onDismissRequest = { showAllFilesAccessPrompt = false; resumeModulePickerAfterPermission = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.runtime_file_access_vendor_picker_warning), fontSize = 14.sp, color = colorScheme.onSurface)
                Text(stringResource(R.string.runtime_file_access_desc), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = stringResource(R.string.runtime_system_picker), onClick = { showAllFilesAccessPrompt = false; resumeModulePickerAfterPermission = false; if (!installRunning) modulePicker.launch(MODULE_INSTALL_MIME_TYPES) }, modifier = Modifier.weight(1f))
                Button(onClick = { openAllFilesSettings() }, colors = ButtonDefaults.buttonColorsPrimary(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.runtime_grant_permission)) }
            }
        }
    }

    pendingInstallUri?.let { uri ->
        val displayName = remember(uri) { uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "module.zip" }
        WindowDialog(title = stringResource(R.string.runtime_confirm_flash_module), show = true, onDismissRequest = { if (!installRunning) pendingInstallUri = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(displayName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface)
                Text(uri.toString(), fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.runtime_confirm_flash_module_desc), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = stringResource(R.string.cancel), onClick = { if (!installRunning) pendingInstallUri = null }, modifier = Modifier.weight(1f))
                Button(onClick = { if (!installRunning) { pendingInstallUri = null; installModule(uri) } }, colors = ButtonDefaults.buttonColorsPrimary(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.runtime_confirm_flash)) }
            }
        }
    }

    uninstallTarget?.let { module ->
        val pending = !module.remove
        WindowDialog(
            title = if (pending) stringResource(R.string.runtime_confirm_uninstall_module) else stringResource(R.string.runtime_revoke_uninstall_module),
            show = true, onDismissRequest = { uninstallTarget = null }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(module.displayName(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface)
                Text(module.id, fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (pending) stringResource(R.string.runtime_confirm_uninstall_module_desc) else stringResource(R.string.runtime_revoke_uninstall_module_desc), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = stringResource(R.string.cancel), onClick = { uninstallTarget = null }, modifier = Modifier.weight(1f))
                Button(
                    onClick = { vm.setAbkRuntimeModulePendingUninstall(module.id, pending); uninstallTarget = null },
                    colors = ButtonDefaults.buttonColors(if (pending) colorScheme.error else colorScheme.primary),
                    modifier = Modifier.weight(1f)
                ) { Text(if (pending) stringResource(R.string.runtime_uninstall) else stringResource(R.string.runtime_revoke)) }
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(colorScheme.surfaceContainer)
                    .padding(12.dp)
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
                if (installRunning) {
                    TextButton(text = stringResource(R.string.runtime_running), onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
                } else {
                    TextButton(text = stringResource(R.string.close), onClick = { installDialogVisible = false }, modifier = Modifier.weight(1f))
                    if (installSuccess == true) {
                        Button(onClick = { scope.launch(Dispatchers.IO) { RootUtils.reboot() } }, colors = ButtonDefaults.buttonColors(
                            colorScheme.error), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.runtime_reboot)) }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 模块卡片
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ModuleCard(
    module: AbkRuntimeModule,
    actionInFlight: Boolean,
    onSetEnabled: (Boolean) -> Unit,
    onRequestUninstall: () -> Unit,
    onRunAction: () -> Unit,
    onOpenWebUi: () -> Unit
) {
    val canUninstall = module.canUninstall()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(module.displayName(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (module.version.isNotBlank()) {
                        Text(stringResource(R.string.runtime_module_version, module.version) + if (module.versionCode > 0) " (${module.versionCode})" else "", fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                    }
                    if (module.author.isNotBlank()) {
                        Text(stringResource(R.string.runtime_module_author, module.author), fontSize = 13.sp, color = colorScheme.onSurfaceVariantActions, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (module.controllable && !module.readonly) {
                    Switch(
                        checked = module.enabled,
                        onCheckedChange = onSetEnabled,
                        enabled = !actionInFlight
                    )
                }
            }

            if (module.description.isNotBlank()) {
                Text(module.description, fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }

            // 标签行
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Tag(module.id.ifBlank { module.repoName() })
                Tag(moduleTypeLabel(module), secondary = true)
                if (module.stage.isNotBlank()) Tag(module.stage, secondary = true)
                if (module.source.isNotBlank()) Tag(moduleSourceLabel(module.source), secondary = true)
                Tag(if (module.enabled) stringResource(R.string.runtime_enabled) else stringResource(R.string.runtime_disabled), secondary = !module.enabled)
                if (module.update) Tag(stringResource(R.string.runtime_pending_update), secondary = true)
                if (module.remove) Tag(stringResource(R.string.runtime_pending_remove), secondary = true)
                if (module.hasWebUi) Tag("WebUI", secondary = true)
                if (module.actionSupported || module.hasActionScript) Tag("Action", secondary = true)
                Tag(
                    when { module.readonly -> stringResource(R.string.runtime_readonly); module.controllable -> stringResource(R.string.runtime_controllable); else -> stringResource(R.string.runtime_metadata_only) },
                    secondary = !module.controllable || module.readonly
                )
            }

            if (module.repoUrl.isNotBlank()) {
                Text(module.repoUrl, fontSize = 11.sp, color = colorScheme.onSurfaceVariantActions, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            // 操作按钮行
            if (module.hasWebUi || module.actionSupported || canUninstall || actionInFlight) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (actionInFlight) {
                        CircularProgressIndicator(size = 20.dp, strokeWidth = 2.dp)
                    }
                    if (module.hasWebUi) {
                        IconButton(onClick = onOpenWebUi, enabled = module.enabled && !module.remove && !module.update) {
                            Icon(Icons.Filled.Web, stringResource(R.string.runtime_open_webui), tint = colorScheme.onSurfaceVariantActions, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (module.actionSupported) {
                        IconButton(onClick = onRunAction, enabled = module.enabled && !actionInFlight) {
                            Icon(Icons.Filled.PlayArrow, stringResource(R.string.runtime_run_action), tint = colorScheme.onSurfaceVariantActions, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (canUninstall) {
                        IconButton(onClick = onRequestUninstall, enabled = !actionInFlight) {
                            Icon(
                                if (module.remove) Icons.Filled.RestartAlt else Icons.Filled.Delete,
                                if (module.remove) stringResource(R.string.runtime_reboot) else stringResource(R.string.root_auth_umount_modules),
                                tint = if (module.remove) colorScheme.primary else colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tag(label: String, secondary: Boolean = false) {
    val color = if (secondary) colorScheme.onSurfaceVariantActions else colorScheme.primary
    Box(Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(label, fontSize = 11.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 模块辅助方法
// ═══════════════════════════════════════════════════════════════════════════

private fun AbkRuntimeModule.matchesQuery(query: String): Boolean {
    val q = query.trim(); if (q.isBlank()) return true
    return listOf(id, name, version, description, repoUrl, stage, type, source, author).any { it.contains(q, ignoreCase = true) }
}

private fun AbkRuntimeModule.displayName(): String = name.ifBlank { id.ifBlank { repoName() } }
private fun AbkRuntimeModule.repoName(): String = repoUrl.trim().trimEnd('/').removeSuffix(".git").substringAfterLast('/').ifBlank { "unknown" }
private fun AbkRuntimeModule.canUninstall(): Boolean = normalizedType() == "standard" && controllable && !readonly
private fun AbkRuntimeModule.normalizedType(): String = type.ifBlank { when { source.split(',').any { it.trim() == "kpm" } -> "kpm"; source.split(',').any { it.trim() == "ksud" } -> "standard"; else -> "builtin" } }
private fun AbkRuntimeModule.typeOrder(): Int = when (normalizedType()) { "builtin" -> 0; "standard" -> 1; "kpm" -> 2; else -> 3 }

@Composable
private fun moduleTypeLabel(module: AbkRuntimeModule): String = when (module.normalizedType()) {
    "standard" -> stringResource(R.string.runtime_module_type_standard)
    "builtin" -> stringResource(R.string.runtime_module_type_builtin)
    "kpm" -> "KPM"
    else -> module.normalizedType()
}

private fun moduleSourceLabel(source: String): String = source.split(',').mapNotNull { it -> val t = it.trim(); t.takeIf { it.isNotBlank() }?.let { s -> when (s) { "ksud" -> "KSU"; "abk" -> "ABK"; else -> s } } }.joinToString("+")

// ═══════════════════════════════════════════════════════════════════════════
// 安装辅助
// ═══════════════════════════════════════════════════════════════════════════

private fun hasFullFileAccess(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

private fun copyModuleUriToCache(context: android.content.Context, uri: Uri): File {
    val cacheDir = File(context.cacheDir, "runtime-module-install").apply { mkdirs() }
    cacheDir.listFiles()?.filter { it.isFile && it.name.startsWith("module-") }?.forEach { it.delete() }
    val target = File(cacheDir, "module-${System.currentTimeMillis()}.zip")
    try {
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
            ?: error("module input stream unavailable")
    } catch (e: Throwable) { target.delete(); throw e }
    if (target.length() <= 0L) { target.delete(); error("empty module file") }
    return target
}
