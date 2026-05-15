@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.abk.kernel.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abk.kernel.data.model.AbkRuntimeBuildInfo
import com.abk.kernel.data.model.AbkRuntimeModule
import com.abk.kernel.data.model.AbkRuntimeStatus
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.ExpressiveSwitch
import com.abk.kernel.ui.components.ExpressiveHeroCard
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.ui.webui.ModuleWebUiActivity
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.viewmodel.MainViewModel
import java.io.File
import kotlin.math.pow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

private const val RUNTIME_PATCH_BACK_VISUAL_EXPONENT = 1.8f
private const val RUNTIME_PATCH_BACK_SCALE_DELTA = 0.09f
private const val RUNTIME_PATCH_BACK_SCRIM_ALPHA = 0.32f
private const val RUNTIME_PATCH_PAGE_EXIT_DELAY_MS = 280L
private val RUNTIME_PATCH_BACK_MAX_OFFSET = 56.dp
private val RUNTIME_PATCH_BACK_MAX_CORNER = 32.dp

@Composable
fun RuntimeHomeScreen(
    vm: MainViewModel,
    onSwitchToClassic: () -> Unit,
    onManagerPatchPageVisibleChange: (Boolean) -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var showManagerPatchPage by rememberSaveable { mutableStateOf(false) }
    var managerPatchBackProgress by remember { mutableFloatStateOf(0f) }
    var managerPatchBackEnabled by remember { mutableStateOf(true) }
    val motionScheme = MaterialTheme.motionScheme
    val animatedManagerPatchBackProgress by animateFloatAsState(
        targetValue = managerPatchBackProgress.coerceIn(0f, 1f),
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "runtime-manager-patch-back-progress"
    )
    val visualManagerPatchBackProgress = animatedManagerPatchBackProgress
        .coerceIn(0f, 1f)
        .pow(RUNTIME_PATCH_BACK_VISUAL_EXPONENT)
    val density = LocalDensity.current
    val managerPatchBackOffsetPx = with(density) { RUNTIME_PATCH_BACK_MAX_OFFSET.toPx() }
    val managerPatchBackCorner = with(density) {
        (RUNTIME_PATCH_BACK_MAX_CORNER.toPx() * visualManagerPatchBackProgress).toDp()
    }

    LaunchedEffect(state.runtimeNavigationEnabled, state.rootGranted) {
        if (state.runtimeNavigationEnabled) vm.refreshAbkRuntimeStatus()
    }

    LaunchedEffect(showManagerPatchPage) {
        onManagerPatchPageVisibleChange(showManagerPatchPage)
        if (!showManagerPatchPage) {
            delay(RUNTIME_PATCH_PAGE_EXIT_DELAY_MS)
            managerPatchBackProgress = 0f
            managerPatchBackEnabled = true
            onManagerPatchPageVisibleChange(false)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onManagerPatchPageVisibleChange(false)
            managerPatchBackEnabled = true
        }
    }

    fun closeManagerPatchPage() {
        if (!managerPatchBackEnabled) return
        showManagerPatchPage = false
    }

    PredictiveBackHandler(enabled = showManagerPatchPage && managerPatchBackEnabled && state.predictiveBackEnabled) { progress ->
        try {
            progress.collect { backEvent ->
                managerPatchBackProgress = backEvent.progress.coerceIn(0f, 1f)
            }
            closeManagerPatchPage()
        } catch (_: CancellationException) {
            managerPatchBackProgress = 0f
        }
    }

    BackHandler(enabled = showManagerPatchPage && managerPatchBackEnabled && !state.predictiveBackEnabled) {
        closeManagerPatchPage()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val childPageModifier = Modifier
            .fillMaxWidth()
            .height(maxHeight)

        Scaffold(
            containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
            topBar = {
                ExpressiveTopBar(
                    title = "AnyBase Kernel",
                    compactTitle = true,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = { vm.refreshAbkRuntimeStatus() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新运行态信息")
                        }
                        IconButton(onClick = onSwitchToClassic) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = "切换到完整导航")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AbkScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RuntimeStatusHeader(
                    runtimeStatus = state.abkRuntimeStatus,
                    loading = state.abkRuntimeLoading,
                    error = state.abkRuntimeError,
                    onRefresh = vm::refreshAbkRuntimeStatus,
                    onOpenManagerPatch = {
                        if (state.abkRuntimeStatus != null) {
                            managerPatchBackProgress = 0f
                            managerPatchBackEnabled = true
                            onManagerPatchPageVisibleChange(true)
                            showManagerPatchPage = true
                        }
                    }
                )

                state.abkRuntimeStatus?.let { runtimeStatus ->
                    RuntimeManagerCard(runtimeStatus)
                    RuntimeBuildParametersCard(runtimeStatus)
                }

                Spacer(Modifier.height(80.dp))
            }
        }

        AnimatedVisibility(
            visible = showManagerPatchPage,
            enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
            exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()),
            modifier = childPageModifier
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = RUNTIME_PATCH_BACK_SCRIM_ALPHA * visualManagerPatchBackProgress))
            )
        }

        AnimatedVisibility(
            visible = showManagerPatchPage,
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
                        translationX = managerPatchBackOffsetPx * visualManagerPatchBackProgress
                        scaleX = 1f - RUNTIME_PATCH_BACK_SCALE_DELTA * visualManagerPatchBackProgress
                        scaleY = 1f - RUNTIME_PATCH_BACK_SCALE_DELTA * visualManagerPatchBackProgress
                        alpha = 1f - 0.06f * visualManagerPatchBackProgress
                        shape = RoundedCornerShape(managerPatchBackCorner)
                        clip = visualManagerPatchBackProgress > 0.01f
                    }
            ) {
                AbkRootPatchScreen(
                    rootGranted = state.rootGranted,
                    runtimeVariant = state.abkRuntimeStatus?.manager?.variant.orEmpty(),
                    backgroundUri = state.customBackgroundUri,
                    backgroundImageEnabled = state.backgroundImageEnabled,
                    onBack = ::closeManagerPatchPage,
                    onBackEnabledChange = { managerPatchBackEnabled = it }
                )
            }
        }
    }
}

@Composable
fun InstalledModulesScreen(
    vm: MainViewModel,
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val modules = remember(state.abkRuntimeStatus?.modules, query) {
        state.abkRuntimeStatus?.modules.orEmpty()
            .filter { it.matchesRuntimeModuleQuery(query) }
            .sortedWith(
                compareBy<AbkRuntimeModule> { it.typeOrder() }
                    .thenBy { !it.enabled }
                    .thenBy { it.displayName().lowercase() }
            )
    }

    fun appendInstallLog(line: String) {
        scope.launch(Dispatchers.Main.immediate) {
            installLog = installLog + line
        }
    }

    fun installModuleFromUri(uri: Uri) {
        if (installRunning) return
        installDialogVisible = true
        installRunning = true
        installSuccess = null
        installLog = listOf(
            "${'$'} module install",
            "source: $uri",
            "",
            "正在复制模块文件..."
        )
        scope.launch {
            var stagedName = "module.zip"
            var stagedPath = ""
            val result = withContext(Dispatchers.IO) {
                var stagedFile: File? = null
                runCatching {
                    stagedFile = copyRuntimeModuleUriToCache(context, uri).also {
                        stagedName = it.name
                        stagedPath = it.absolutePath
                    }
                    appendInstallLog("file: $stagedPath")
                    appendInstallLog("等待 root shell 返回，请不要退出应用...")
                    if (!RootUtils.refreshRootState()) {
                        RootUtils.ShellResult(false, listOf("管理器未激活"))
                    } else {
                        RootUtils.installModule(stagedPath, ::appendInstallLog)
                    }
                }.getOrElse {
                    RootUtils.ShellResult(false, listOf("模块文件读取失败"))
                }.also {
                    stagedFile?.delete()
                }
            }
            installRunning = false
            installSuccess = result.success
            installLog = listOf(
                "${'$'} module install $stagedName",
                "file: ${stagedPath.ifBlank { "未创建临时文件" }}",
                ""
            ) + result.output.ifEmpty {
                listOf(if (result.success) "模块安装完成，无输出。" else "模块安装失败，但未返回日志。")
            }
            if (result.success) vm.refreshAbkRuntimeStatus()
        }
    }

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
        if (installRunning) return
        if (hasRuntimeModuleFileAccess()) {
            modulePicker.launch(MODULE_INSTALL_MIME_TYPES)
        } else {
            resumeModulePickerAfterPermission = true
            showAllFilesAccessPrompt = true
        }
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
        if (!installRunning) modulePicker.launch(MODULE_INSTALL_MIME_TYPES)
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
        if (state.runtimeNavigationEnabled) vm.refreshAbkRuntimeStatus()
    }

    Scaffold(
        containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
        topBar = {
            ExpressiveTopBar(
                title = "已安装模块",
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { vm.refreshAbkRuntimeStatus() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新已安装模块")
                    }
                }
            )
        },
        floatingActionButton = {
            SmallFloatingActionButton(
                onClick = {
                    launchModulePickerWithPermissionCheck()
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = "安装模块")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AbkScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RuntimeModuleSearchField(query, onValueChange = { query = it })

            if (state.abkRuntimeLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            state.abkRuntimeError?.let {
                RuntimeErrorCard(
                    message = if (state.abkRuntimeStatus == null) it else "操作未完成，请刷新后重试",
                    onRefresh = vm::refreshAbkRuntimeStatus
                )
            }

            if (state.abkRuntimeStatus != null && modules.isEmpty()) {
                Text(
                    text = if (query.isBlank()) "当前内核没有上报 ABK 外部模块" else "没有匹配的模块",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                modules.forEach { module ->
                    InstalledRuntimeModuleCard(
                        module = module,
                        actionInFlight = state.abkRuntimeModuleActionId == module.id,
                        onSetEnabled = { enabled -> vm.setAbkRuntimeModuleEnabled(module.id, enabled) },
                        onRequestUninstall = { uninstallTarget = module },
                        onRunAction = { vm.runRuntimeModuleAction(module.id) },
                        onOpenWebUi = {
                            context.startActivity(
                                Intent(context, ModuleWebUiActivity::class.java)
                                    .putExtra(ModuleWebUiActivity.EXTRA_MODULE_ID, module.id)
                                    .putExtra(ModuleWebUiActivity.EXTRA_MODULE_NAME, module.displayName())
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }

    if (state.abkRuntimeModuleActionTitle != null) {
        AlertDialog(
            onDismissRequest = vm::dismissRuntimeModuleActionOutput,
            confirmButton = {
                TextButton(onClick = vm::dismissRuntimeModuleActionOutput) {
                    Text("关闭")
                }
            },
            title = { Text(state.abkRuntimeModuleActionTitle.orEmpty()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.abkRuntimeModuleActionId != null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        text = state.abkRuntimeModuleActionOutput.ifEmpty { listOf("等待输出...") }.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )
    }

    if (showAllFilesAccessPrompt) {
        RuntimeModuleFileAccessDialog(
            onDismiss = {
                showAllFilesAccessPrompt = false
                resumeModulePickerAfterPermission = false
            },
            onGrantAccess = ::openAllFilesAccessSettings,
            onUseSystemPicker = ::launchModulePickerFallback
        )
    }

    pendingInstallUri?.let { uri ->
        RuntimeModuleInstallConfirmDialog(
            uri = uri,
            displayName = remember(context, uri) { runtimeModuleUriDisplayName(context, uri) },
            onDismiss = { if (!installRunning) pendingInstallUri = null },
            onConfirm = {
                if (!installRunning) {
                    pendingInstallUri = null
                    installModuleFromUri(uri)
                }
            }
        )
    }

    uninstallTarget?.let { module ->
        RuntimeModuleUninstallConfirmDialog(
            module = module,
            pending = !module.remove,
            onDismiss = { uninstallTarget = null },
            onConfirm = {
                vm.setAbkRuntimeModulePendingUninstall(module.id, !module.remove)
                uninstallTarget = null
            }
        )
    }

    if (installDialogVisible) {
        RuntimeModuleInstallDialog(
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
}

@Composable
private fun RuntimeStatusHeader(
    runtimeStatus: AbkRuntimeStatus?,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onOpenManagerPatch: (() -> Unit)? = null
) {
    val clickableModifier = if (runtimeStatus != null && onOpenManagerPatch != null) {
        Modifier.clickable(onClick = onOpenManagerPatch)
    } else {
        Modifier
    }
    ExpressiveHeroCard(
        title = if (runtimeStatus != null) "管理器已激活" else "管理器未激活",
        subtitle = runtimeStatus?.let {
            val managerName = it.manager?.displayName?.takeIf { name -> name.isNotBlank() } ?: "Root"
            "$managerName · ABK ${it.abkVersion.ifBlank { "unknown" }} · ${it.modules.size} 个模块"
        } ?: (error ?: "安装并启用支持管理器的内核后可查看运行态信息"),
        icon = if (runtimeStatus != null) Icons.Default.CheckCircle else Icons.Default.Error,
        containerColor = if (runtimeStatus != null) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (runtimeStatus != null) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = clickableModifier,
        badge = {
            runtimeStatus?.let {
                ExpressiveStatusChip(
                    label = "schema ${it.schema}",
                    icon = Icons.Default.Tune,
                    color = MaterialTheme.colorScheme.primary
                )
                if (it.abkCommit.isNotBlank()) {
                    ExpressiveStatusChip(
                        label = it.abkCommit,
                        icon = Icons.Default.Memory,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    ) {
        if (runtimeStatus == null) {
            Button(
                onClick = onRefresh,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("重新检测")
                }
            }
        }
    }
}

@Composable
private fun RuntimeManagerCard(runtimeStatus: AbkRuntimeStatus) {
    val manager = runtimeStatus.manager ?: return
    val backend = runtimeStatus.runtimeBackend
    ExpressiveSectionCard(
        title = "内核管理器",
        subtitle = "编译身份与当前运行后端",
        icon = Icons.Default.Memory
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            RuntimeInfoRow("类型", manager.displayName.ifBlank { manager.variant })
            RuntimeInfoRow("版本", manager.version)
            RuntimeInfoRow("来源", runtimeBackendLabel(manager.backend))
            if (backend != null && backend != manager) {
                Spacer(Modifier.height(2.dp))
                RuntimeInfoRow("运行后端", backend.displayName.ifBlank { backend.variant })
                RuntimeInfoRow("后端版本", backend.version)
                RuntimeInfoRow("兼容层", runtimeBackendLabel(backend.backend))
            }
            val diagnostics = manager.diagnostics
                .plus(backend?.diagnostics.orEmpty())
                .distinct()
            diagnostics.forEach { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            val chips = manager.capabilities
                .plus(backend?.capabilities.orEmpty())
                .map(::runtimeCapabilityLabel)
                .ifEmpty { listOf("Root Shell") }
                .distinct()
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                chips.forEach { label ->
                    RuntimeModuleChip(label, secondary = true)
                }
            }
        }
    }
}

@Composable
private fun RuntimeBuildParametersCard(runtimeStatus: AbkRuntimeStatus) {
    val build = runtimeStatus.build
    val systemKernelVersion = remember { RootUtils.getKernelVersion() }
    ExpressiveSectionCard(
        title = "当前内核编译参数",
        subtitle = "来自编译器写入的构建记录",
        icon = Icons.Default.Tune
    ) {
        if (build == null) {
            Text(
                text = "当前设备输出为旧版 schema，未包含编译参数。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@ExpressiveSectionCard
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            RuntimeInfoRow("Android", build.androidVersion)
            RuntimeInfoRow("目标内核", listOf(build.kernelVersion, build.subLevel).filter { it.isNotBlank() }.joinToString("."))
            RuntimeInfoRow("内核版本", systemKernelVersion)
            RuntimeInfoRow("补丁级别", build.osPatchLevel)
            RuntimeInfoRow("修订版本", build.revision)
            RuntimeInfoRow("KSU", listOf(build.kernelsuVariant, build.kernelsuBranch).filter { it.isNotBlank() }.joinToString(" / "))
            RuntimeInfoRow("构建时间", build.buildTime)
            RuntimeInfoRow("虚拟化", build.virtualizationSupport)
            RuntimeInfoRow("ZRAM 额外算法", build.zramExtraAlgos)
            RuntimeInfoRow("ABK", listOf(runtimeStatus.abkVersion, runtimeStatus.abkCommit).filter { it.isNotBlank() }.joinToString(" · "))
            RuntimeFeatureChips(build)
        }
    }
}

@Composable
private fun RuntimeFeatureChips(build: AbkRuntimeBuildInfo) {
    val features = build.features
        .filterValues { it }
        .keys
        .map(::runtimeFeatureLabel)
        .ifEmpty { listOf("基础配置") }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        features.forEach { feature ->
            RuntimeModuleChip(feature, secondary = true)
        }
    }
}

@Composable
private fun RuntimeInfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(82.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RuntimeErrorCard(
    message: String,
    onRefresh: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = uiSurfaceColor(MaterialTheme.colorScheme.errorContainer)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text("重新检测")
            }
        }
    }
}

@Composable
private fun RuntimeModuleSearchField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(Icons.Default.Search, null) },
        placeholder = { Text("搜索已安装模块") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun InstalledRuntimeModuleCard(
    module: AbkRuntimeModule,
    actionInFlight: Boolean,
    onSetEnabled: (Boolean) -> Unit,
    onRequestUninstall: () -> Unit,
    onRunAction: () -> Unit,
    onOpenWebUi: () -> Unit
) {
    val canUninstall = module.canUninstallRuntimeModule()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainer)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
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
                    if (module.version.isNotBlank()) {
                        Text(
                            text = buildString {
                                append("版本: ")
                                append(module.version)
                                if (module.versionCode > 0) append(" (${module.versionCode})")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (module.author.isNotBlank()) {
                        Text(
                            text = "作者: ${module.author}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (module.controllable && !module.readonly) {
                    ExpressiveSwitch(
                        checked = module.enabled,
                        enabled = !actionInFlight,
                        onCheckedChange = onSetEnabled
                    )
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
                RuntimeModuleChip(module.id.ifBlank { module.repoName() })
                RuntimeModuleChip(runtimeModuleTypeLabel(module), secondary = true)
                if (module.stage.isNotBlank()) RuntimeModuleChip(module.stage, secondary = true)
                if (module.source.isNotBlank()) RuntimeModuleChip(runtimeModuleSourceLabel(module.source), secondary = true)
                RuntimeModuleChip(if (module.enabled) "已启用" else "已关闭", secondary = !module.enabled)
                if (module.update) RuntimeModuleChip("待更新", secondary = true)
                if (module.remove) RuntimeModuleChip("待卸载", secondary = true)
                if (module.hasWebUi) RuntimeModuleChip("WebUI", secondary = true)
                if (module.actionSupported || module.hasActionScript) RuntimeModuleChip("Action", secondary = true)
                RuntimeModuleChip(
                    when {
                        module.readonly -> "只读"
                        module.controllable -> "可控制"
                        else -> "仅元数据"
                    },
                    secondary = !module.controllable || module.readonly
                )
            }

            if (module.repoUrl.isNotBlank()) {
                Text(
                    text = module.repoUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (module.hasWebUi || module.actionSupported || canUninstall || actionInFlight) {
                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (actionInFlight) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    if (module.hasWebUi) {
                        IconButton(
                            onClick = onOpenWebUi,
                            enabled = module.enabled && !module.remove && !module.update
                        ) {
                            Icon(Icons.Default.Web, contentDescription = "打开 WebUI")
                        }
                    }
                    if (module.actionSupported) {
                        IconButton(
                            onClick = onRunAction,
                            enabled = module.enabled && !actionInFlight
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "执行 Action")
                        }
                    }
                    if (canUninstall) {
                        IconButton(
                            onClick = onRequestUninstall,
                            enabled = !actionInFlight
                        ) {
                            Icon(
                                if (module.remove) Icons.Default.RestartAlt else Icons.Default.Delete,
                                contentDescription = if (module.remove) "撤销卸载" else "卸载模块",
                                tint = if (module.remove) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeModuleFileAccessDialog(
    onDismiss: () -> Unit,
    onGrantAccess: () -> Unit,
    onUseSystemPicker: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.FolderOpen, null) },
        title = { Text("需要文件访问权限") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("部分机型会把模块 zip 选择导向厂商安全选择器，可能无法返回真实文件。")
                Text(
                    text = "授予所有文件访问权限后，ABK 会继续打开模块选择器；如果不想授权，也可以继续使用系统文件选择器。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(onClick = onGrantAccess) {
                Text("授予权限")
            }
        },
        dismissButton = {
            TextButton(onClick = onUseSystemPicker) {
                Text("系统选择器")
            }
        }
    )
}

@Composable
private fun RuntimeModuleInstallConfirmDialog(
    uri: Uri,
    displayName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.UploadFile, null) },
        title = { Text("确认刷写模块") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = uri.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "确认后会调用当前系统可用的模块安装器，安装完成通常需要重启后生效。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text("确认刷写")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun RuntimeModuleUninstallConfirmDialog(
    module: AbkRuntimeModule,
    pending: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val title = if (pending) "确认卸载模块" else "撤销卸载模块"
    val message = if (pending) {
        "确认后会将该普通模块标记为待卸载，重启后由 KernelSU 完成删除。"
    } else {
        "确认后会移除待卸载标记，模块将继续保留。"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (pending) Icons.Default.Delete else Icons.Default.RestartAlt,
                null,
                tint = if (pending) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = module.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = module.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (pending) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Icon(
                    if (pending) Icons.Default.Delete else Icons.Default.RestartAlt,
                    null,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(if (pending) "卸载" else "撤销")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun RuntimeModuleInstallDialog(
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
        title = { Text(if (running) "正在安装模块" else "安装模块") },
        text = {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp, max = 360.dp),
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
                    logLines.ifEmpty { listOf("等待输出...") }.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (line.startsWith("${'$'}")) colorScheme.primary else colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (running) {
                TextButton(onClick = {}, enabled = false) { Text("执行中") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onClose) { Text("关闭") }
                    if (success == true) {
                        Button(
                            onClick = onReboot,
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error)
                        ) {
                            Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("重启")
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun RuntimeModuleChip(label: String, secondary: Boolean = false) {
    val accentColor = if (secondary) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.primary
    }
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = accentColor.copy(alpha = 0.10f),
        contentColor = accentColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.62f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!secondary) {
                Icon(Icons.Default.Extension, null, modifier = Modifier.size(14.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (secondary) FontWeight.Medium else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun AbkRuntimeModule.matchesRuntimeModuleQuery(query: String): Boolean {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) return true
    return listOf(id, name, version, description, repoUrl, stage, type, source, author)
        .any { it.contains(cleanQuery, ignoreCase = true) }
}

private fun AbkRuntimeModule.displayName(): String =
    name.ifBlank { id.ifBlank { repoName() } }

private fun AbkRuntimeModule.repoName(): String =
    repoUrl
        .trim()
        .trimEnd('/')
        .removeSuffix(".git")
        .substringAfterLast('/')
        .ifBlank { "unknown" }

private fun runtimeFeatureLabel(key: String): String = when (key) {
    "use_zram" -> "ZRAM"
    "use_bbg" -> "BBG"
    "use_ddk" -> "DDK"
    "use_ntsync" -> "NTsync"
    "use_networking" -> "网络增强"
    "use_kpm" -> "KPM"
    "use_rekernel" -> "Re-Kernel"
    "enable_susfs" -> "SUSFS"
    "supp_op" -> "SukiSU SUS_SU"
    "zram_full_algo" -> "ZRAM 完整算法"
    "cancel_susfs" -> "SUSFS 已取消"
    else -> key
}

private fun runtimeCapabilityLabel(key: String): String =
    if (key == internalRuntimeControlCapability()) {
        "ABK 控制"
    } else {
        when (key) {
            "root_shell" -> "Root Shell"
            "native_manager" -> "原生管理器"
            "root_policy" -> "授权配置"
            "superuser_profiles" -> "授权列表"
            "lkm" -> "LKM"
            "late_load" -> "Late Load"
            "safe_mode" -> "安全模式"
            "modules" -> "模块列表"
            "module_control" -> "模块控制"
            "susfs" -> "SUSFS"
            "kpm" -> "KPM"
            "features" -> "功能开关"
            else -> key
        }
    }

private fun runtimeBackendLabel(backend: String): String = when (backend) {
    "native" -> "原生控制"
    "ksud" -> "KSU 兼容"
    "su" -> "通用 su"
    "kernel" -> "内核运行态"
    else -> backend
}

private fun runtimeModuleSourceLabel(source: String): String {
    val labels = source
        .split(',')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map {
            when (it) {
                "ksud" -> "KSU"
                "abk" -> "ABK"
                else -> it
            }
        }
    return labels.joinToString("+")
}

private fun runtimeModuleTypeLabel(module: AbkRuntimeModule): String = when (module.normalizedType()) {
    "standard" -> "普通模块"
    "builtin" -> "预编译模块"
    "kpm" -> "KPM"
    else -> module.normalizedType()
}

private fun AbkRuntimeModule.normalizedType(): String =
    type.ifBlank {
        when {
            source.split(',').any { it.trim() == "kpm" } -> "kpm"
            source.split(',').any { it.trim() == "ksud" } -> "standard"
            else -> "builtin"
        }
    }

private fun AbkRuntimeModule.canUninstallRuntimeModule(): Boolean =
    normalizedType() == "standard" && controllable && !readonly

private fun AbkRuntimeModule.typeOrder(): Int = when (normalizedType()) {
    "builtin" -> 0
    "standard" -> 1
    "kpm" -> 2
    else -> 3
}

private fun internalRuntimeControlCapability(): String =
    intArrayOf(97, 98, 107, 95, 99, 111, 110, 116, 114, 111, 108)
        .map { it.toChar() }
        .joinToString("")

private val MODULE_INSTALL_MIME_TYPES = arrayOf(
    "application/zip",
    "application/x-zip",
    "application/octet-stream",
    "application/x-zip-compressed",
    "*/*"
)

private fun hasRuntimeModuleFileAccess(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

private fun runtimeModuleUriDisplayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) {
            cursor.getString(index)?.takeIf { it.isNotBlank() }?.let { return it }
        }
    }
    return uri.lastPathSegment
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() }
        ?: "module.zip"
}

private fun copyRuntimeModuleUriToCache(context: Context, uri: Uri): File {
    val cacheDir = File(context.cacheDir, "runtime-module-install").apply {
        mkdirs()
    }
    cacheDir.listFiles()
        ?.filter { it.isFile && it.name.startsWith("module-") }
        ?.forEach { it.delete() }

    val target = File(cacheDir, "module-${System.currentTimeMillis()}.zip")
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("module input stream unavailable")
    } catch (error: Throwable) {
        target.delete()
        throw error
    }

    if (target.length() <= 0L) {
        target.delete()
        error("empty module file")
    }
    return target
}
