@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RunCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.abk.kernel.R
import com.abk.kernel.data.model.ArtifactCategory
import com.abk.kernel.data.model.ArtifactType
import com.abk.kernel.data.model.BuildArtifact
import com.abk.kernel.data.model.BuildParameterSummary
import com.abk.kernel.data.model.BuildStatus
import com.abk.kernel.data.model.DownloadedArtifact
import com.abk.kernel.data.model.KernelBuildConfig
import com.abk.kernel.data.model.KernelSupport
import com.abk.kernel.data.model.PREBUILT_GKI_RUN_ID
import com.abk.kernel.data.model.PrebuiltGkiAsset
import com.abk.kernel.data.model.PrebuiltGkiRelease
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.ExpressiveEmptyState
import com.abk.kernel.ui.components.ExpressiveHeroCard
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.viewmodel.MainViewModel
import kotlin.math.pow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FLASH_DETAIL_BACK_VISUAL_EXPONENT = 1.8f
private const val FLASH_DETAIL_BACK_SCALE_DELTA = 0.09f
private const val FLASH_DETAIL_BACK_SCRIM_ALPHA = 0.32f
private val FLASH_DETAIL_BACK_MAX_OFFSET = 56.dp
private val FLASH_DETAIL_BACK_MAX_CORNER = 32.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlashScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onDetailPageVisibleChange: (Boolean) -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeContentTab by rememberSaveable { mutableStateOf(FlashContentTab.Workflows) }
    val navController = rememberNavController()
    var selectedRunId by remember { mutableStateOf<Long?>(null) }
    var selectedPrebuiltReleaseId by remember { mutableStateOf<Long?>(null) }
    var selectedItem by remember { mutableStateOf<DownloadedArtifact?>(null) }
    var deleteFileTarget by remember { mutableStateOf<DownloadedArtifact?>(null) }
    var deleteWorkflowTarget by remember { mutableStateOf<WorkflowArtifactGroup?>(null) }
    var parameterTarget by remember { mutableStateOf<WorkflowArtifactGroup?>(null) }
    var prebuiltParameterTarget by remember { mutableStateOf<PrebuiltGkiRelease?>(null) }
    var deleteRemoteWorkflowRun by remember { mutableStateOf(false) }
    var showFlashConfirm by remember { mutableStateOf(false) }
    var showTerminal by remember { mutableStateOf(false) }
    var terminalTitle by remember { mutableStateOf("终端") }
    var terminalCanReboot by remember { mutableStateOf(false) }
    var terminalRunning by remember { mutableStateOf(false) }
    var terminalLog by remember { mutableStateOf<List<String>>(emptyList()) }
    var terminalSuccess by remember { mutableStateOf<Boolean?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val rootGranted = state.rootGranted
    val currentContentTab = if (state.prebuiltGkiEnabled) activeContentTab else FlashContentTab.Workflows

    val remoteArtifacts = remember(state.artifacts) {
        state.artifacts.filter {
            !it.expired && DownloadUtils.classifyCategory(DownloadUtils.classifyArtifact(it.name)) != null
        }
    }
    val workflowDownloadedArtifacts = remember(state.downloadedArtifacts, state.prebuiltGkiEnabled) {
        if (state.prebuiltGkiEnabled) {
            state.downloadedArtifacts.filterNot { it.runId == PREBUILT_GKI_RUN_ID }
        } else {
            state.downloadedArtifacts
        }
    }
    val workflowGroups = remember(remoteArtifacts, workflowDownloadedArtifacts) {
        buildWorkflowGroups(remoteArtifacts, workflowDownloadedArtifacts)
    }
    val recentRunById = remember(state.recentRuns) { state.recentRuns.associateBy { it.id } }
    val selectedGroup = selectedRunId?.let { id -> workflowGroups.firstOrNull { it.runId == id } }
    val selectedPrebuiltRelease = selectedPrebuiltReleaseId?.let { id ->
        state.prebuiltGkiReleases.firstOrNull { it.id == id }
    }

    fun returnToWorkflowList() {
        selectedRunId = null
        navController.popBackStack()
    }

    fun returnToPrebuiltReleaseList() {
        selectedPrebuiltReleaseId = null
        navController.popBackStack()
    }

    fun returnToTopList() {
        selectedRunId = null
        selectedPrebuiltReleaseId = null
        navController.navigate(FLASH_ROUTE_LIST) {
            popUpTo(FLASH_ROUTE_LIST) { inclusive = false }
            launchSingleTop = true
        }
    }

    LaunchedEffect(state.forkRepo?.fullName) {
        if (state.forkRepo != null) vm.loadRecentRuns()
    }

    LaunchedEffect(state.prebuiltGkiEnabled) {
        if (!state.prebuiltGkiEnabled) {
            activeContentTab = FlashContentTab.Workflows
            selectedPrebuiltReleaseId = null
            navController.navigate(FLASH_ROUTE_LIST) {
                popUpTo(FLASH_ROUTE_LIST) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(currentContentTab, state.prebuiltGkiEnabled, state.isLoggedIn) {
        if (currentContentTab == FlashContentTab.PrebuiltGki && state.prebuiltGkiEnabled && state.isLoggedIn) {
            vm.loadPrebuiltGkiReleases()
        }
    }

    LaunchedEffect(workflowGroups, selectedRunId) {
        if (selectedRunId != null && selectedGroup == null) returnToTopList()
    }

    LaunchedEffect(state.prebuiltGkiReleases, selectedPrebuiltReleaseId) {
        if (selectedPrebuiltReleaseId != null && selectedPrebuiltRelease == null) returnToTopList()
    }

    fun showFailure(title: String, lines: List<String>) {
        terminalTitle = title
        terminalCanReboot = false
        terminalRunning = false
        terminalSuccess = false
        terminalLog = lines
        showTerminal = true
    }

    fun copyDownloadedFilePath(item: DownloadedArtifact) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(item.name, item.filePath))
        Toast.makeText(context, "已复制文件路径", Toast.LENGTH_SHORT).show()
    }

    fun appendTerminalOutput(line: String) {
        scope.launch(Dispatchers.Main.immediate) {
            terminalLog = terminalLog + line
        }
    }

    fun installManager(item: DownloadedArtifact) {
        if (!rootGranted) {
            showFailure(
                "Root 未授权",
                listOf(
                    "${'$'} pm install -r ${item.name}",
                    "当前处于部分激活状态，文件页只允许查看已下载文件。",
                    "如需直接安装管理器应用，请先授予 Root 权限。"
                )
            )
            return
        }
        terminalTitle = "安装管理器 APK"
        terminalCanReboot = false
        terminalRunning = true
        terminalSuccess = null
        terminalLog = listOf(
            "${'$'} pm install -r ${item.name}",
            "file: ${item.filePath}",
            "",
            "等待 root shell 返回，请不要退出应用..."
        )
        showTerminal = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    RootUtils.installApk(context, item.filePath, ::appendTerminalOutput)
                }.getOrElse { error ->
                    RootUtils.ShellResult(false, listOf(error.message ?: error::class.java.simpleName))
                }
            }
            terminalRunning = false
            terminalSuccess = result.success
            terminalLog = listOf(
                "${'$'} pm install -r ${item.name}",
                "file: ${item.filePath}",
                ""
            ) + result.output.ifEmpty {
                listOf(if (result.success) "命令执行完成，无输出。" else "命令执行失败，但未返回日志。")
            }
        }
    }

    fun startFlash(item: DownloadedArtifact) {
        if (!rootGranted) {
            showFailure(
                "Root 未授权",
                listOf(
                    "${'$'} ${flashCommandPreview(item)}",
                    "当前处于部分激活状态，文件页只允许查看已下载文件。",
                    "如需刷写或安装模块，请先授予 Root 权限。"
                )
            )
            return
        }
        terminalTitle = flashOperationLabel(item.type)
        terminalCanReboot = true
        terminalRunning = true
        terminalSuccess = null
        terminalLog = listOf(
            "${'$'} ${flashCommandPreview(item)}",
            "file: ${item.filePath}",
            "",
            "等待 root shell 返回，请不要退出应用..."
        )
        showTerminal = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    when (item.type) {
                        ArtifactType.KERNEL_IMG -> RootUtils.flashImage(item.filePath, onOutput = ::appendTerminalOutput)
                        ArtifactType.ANYKERNEL3 -> RootUtils.flashAnyKernel3(context, item.filePath, ::appendTerminalOutput)
                        ArtifactType.SUSFS_MODULE -> RootUtils.installModule(item.filePath, ::appendTerminalOutput)
                        ArtifactType.KSU_MANAGER -> RootUtils.installApk(context, item.filePath, ::appendTerminalOutput)
                        else -> RootUtils.ShellResult(false, listOf("不支持此文件类型的自动刷写"))
                    }
                }.getOrElse { error ->
                    RootUtils.ShellResult(false, listOf(error.message ?: error::class.java.simpleName))
                }
            }
            terminalRunning = false
            terminalSuccess = result.success
            terminalLog = listOf(
                "${'$'} ${flashCommandPreview(item)}",
                "file: ${item.filePath}",
                ""
            ) + result.output.ifEmpty {
                listOf(if (result.success) "命令执行完成，无输出。" else "命令执行失败，但未返回日志。")
            }
        }
    }

    if (showFlashConfirm && selectedItem != null) {
        val item = selectedItem!!
        AlertDialog(
            onDismissRequest = { showFlashConfirm = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.flash_confirm)) },
            text = { Text(stringResource(R.string.flash_confirm_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        showFlashConfirm = false
                        startFlash(item)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.flash_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showFlashConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            icon = { Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("操作失败") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { vm.clearError() }) {
                    Text("知道了")
                }
            }
        )
    }

    deleteFileTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteFileTarget = null },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("删除文件") },
            text = { Text("将删除本地文件记录和已下载文件：\n${item.name}") },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteDownloadedArtifact(item.filePath)
                        deleteFileTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteFileTarget = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    deleteWorkflowTarget?.let { group ->
        AlertDialog(
            onDismissRequest = { deleteWorkflowTarget = null },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(if (group.runId == PREBUILT_GKI_RUN_ID) "删除预编译 GKI 文件" else "删除工作流记录") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (group.runId == PREBUILT_GKI_RUN_ID) {
                            "将删除本地已下载的预编译 GKI 文件。"
                        } else {
                            "将删除此工作流在 ABK 中缓存的产物记录，并删除本地已下载文件。\n\n工作流 ${
                                if (group.runNumber > 0) "#${group.runNumber}" else "#${group.runId}"
                            }"
                        }
                    )
                    if (group.runId > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { deleteRemoteWorkflowRun = !deleteRemoteWorkflowRun },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = deleteRemoteWorkflowRun,
                                onCheckedChange = { deleteRemoteWorkflowRun = it }
                            )
                            Text("同时删除远程 GitHub Actions 工作流记录")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetRunId = group.runId
                        val shouldDeleteRemoteRun = deleteRemoteWorkflowRun
                        vm.deleteWorkflowArtifacts(targetRunId, shouldDeleteRemoteRun)
                        if (selectedRunId == targetRunId) returnToTopList()
                        deleteWorkflowTarget = null
                        deleteRemoteWorkflowRun = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = {
                    deleteWorkflowTarget = null
                    deleteRemoteWorkflowRun = false
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showTerminal) {
        FlashTerminalDialog(
            title = terminalTitle,
            running = terminalRunning,
            success = terminalSuccess,
            logLines = terminalLog,
            canReboot = terminalCanReboot,
            onClose = { if (!terminalRunning) showTerminal = false },
            onReboot = {
                if (!terminalRunning) {
                    scope.launch(Dispatchers.IO) { RootUtils.reboot() }
                }
            }
        )
    }

    parameterTarget?.let { group ->
        val runId = group.runId
        LaunchedEffect(runId) {
            vm.loadBuildParameterSummary(runId)
        }
        BuildParameterSummaryDialog(
            group = group,
            summary = state.buildParameterSummaries[runId],
            loading = runId in state.loadingBuildParameterRunIds,
            error = state.buildParameterErrors[runId],
            onDismiss = { parameterTarget = null },
            onRetry = { vm.loadBuildParameterSummary(runId, force = true) }
        )
    }

    prebuiltParameterTarget?.let { release ->
        PrebuiltParameterSummaryDialog(
            release = release,
            summary = remember(release.id, release.body) { parsePrebuiltGkiParameterSummary(release) },
            onDismiss = { prebuiltParameterTarget = null }
        )
    }

    @Composable
    fun FlashListContent() {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                ExpressiveTopBar(
                    title = if (rootGranted) stringResource(R.string.flash_title) else "文件",
                    scrollBehavior = scrollBehavior
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = AbkScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                item {
                    FlashHero(
                        buildStatus = state.buildStatus,
                        availableCount = remoteArtifacts.size,
                        downloadedCount = workflowDownloadedArtifacts.size,
                        rootGranted = rootGranted
                    )
                }

                if (state.prebuiltGkiEnabled) {
                    item {
                        FlashContentTabs(
                            active = activeContentTab,
                            onSelect = { activeContentTab = it }
                        )
                    }
                }

                when (currentContentTab) {
                    FlashContentTab.Workflows -> {
                        item {
                            OutlinedButton(
                                onClick = { vm.loadRecentRuns() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("联网刷新构建产物")
                            }
                        }

                        if (workflowGroups.isNotEmpty()) {
                            items(workflowGroups, key = { "workflow-${it.runId}" }) { group ->
                                val run = recentRunById[group.runId]
                                WorkflowRunCard(
                                    group = group,
                                    active = run?.isActiveFlashRun() == true,
                                    cancelling = group.runId in state.cancellingWorkflowRunIds,
                                    onClick = {
                                        selectedRunId = group.runId
                                        selectedPrebuiltReleaseId = null
                                        navController.navigate(flashWorkflowRoute(group.runId))
                                    },
                                    onShowParameters = { parameterTarget = group },
                                    onDelete = {
                                        deleteWorkflowTarget = group
                                        deleteRemoteWorkflowRun = false
                                    },
                                    onCancel = { vm.cancelWorkflowRun(group.runId) }
                                )
                            }
                        } else {
                            item {
                                ExpressiveEmptyState(
                                    title = if (rootGranted) "暂无可刷写产物" else "暂无可查看文件",
                                    subtitle = if (rootGranted) {
                                        "构建成功后，ABK 会联网同步并按工作流整理产物。"
                                    } else {
                                        "构建成功后，可在这里下载并查看产物文件。"
                                    },
                                    icon = Icons.Default.Inbox
                                )
                            }
                        }
                    }

                    FlashContentTab.PrebuiltGki -> {
                        if (state.prebuiltGkiEnabled) {
                            item {
                                PrebuiltReleaseListHeader(
                                    releaseCount = state.prebuiltGkiReleases.size,
                                    isLoading = state.isLoadingPrebuiltGkiReleases,
                                    onRefresh = { vm.loadPrebuiltGkiReleases(force = true) }
                                )
                            }

                            when {
                                state.isLoadingPrebuiltGkiReleases -> {
                                    item {
                                        LoadingRow("正在获取 Release")
                                    }
                                }
                                state.prebuiltGkiReleases.isEmpty() -> {
                                    item {
                                        ExpressiveEmptyState(
                                            title = "暂无预编译 GKI Release",
                                            subtitle = "本仓库 Release 中暂未发现可浏览的版本。",
                                            icon = Icons.Default.CloudDownload
                                        )
                                    }
                                }
                                else -> {
                                    items(state.prebuiltGkiReleases, key = { "release-${it.id}" }) { release ->
                                        PrebuiltReleaseCard(
                                            release = release,
                                            onClick = {
                                                selectedPrebuiltReleaseId = release.id
                                                selectedRunId = null
                                                navController.navigate(flashPrebuiltRoute(release.id))
                                            }
                                        )
                                    }
                                }
                            }

                            val localPrebuiltFiles = state.downloadedArtifacts.filter {
                                it.runId == PREBUILT_GKI_RUN_ID
                            }
                            if (localPrebuiltFiles.isNotEmpty()) {
                                item {
                                    CategoryHeader(ArtifactCategory.KERNEL)
                                }
                                items(localPrebuiltFiles, key = { "prebuilt-local-${it.filePath}" }) { artifact ->
                                    LocalOnlyArtifactCard(
                                        artifact = artifact,
                                        onCopyPath = ::copyDownloadedFilePath,
                                        onInstall = ::installManager,
                                        onFlash = {
                                            selectedItem = it
                                            showFlashConfirm = true
                                        },
                                        onDelete = { deleteFileTarget = it },
                                        allowRootActions = rootGranted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val motionScheme = MaterialTheme.motionScheme
    fun navEnter(forward: Boolean) = if (state.predictiveBackEnabled) {
        fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) +
            slideInHorizontally(animationSpec = motionScheme.defaultSpatialSpec()) { width ->
                if (forward) width / 3 else -width / 3
            }
    } else {
        fadeIn(animationSpec = motionScheme.fastEffectsSpec())
    }
    fun navExit(forward: Boolean) = if (state.predictiveBackEnabled) {
        fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
            slideOutHorizontally(animationSpec = motionScheme.fastSpatialSpec()) { width ->
                if (forward) -width / 3 else width / 3
            }
    } else {
        fadeOut(animationSpec = motionScheme.fastEffectsSpec())
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = FLASH_ROUTE_LIST,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { navEnter(forward = true) },
            exitTransition = { navExit(forward = true) },
            popEnterTransition = {
                if (state.predictiveBackEnabled) {
                    fadeIn(animationSpec = motionScheme.fastEffectsSpec())
                } else {
                    navEnter(forward = false)
                }
            },
            popExitTransition = {
                if (state.predictiveBackEnabled) {
                    fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                        slideOutHorizontally(animationSpec = motionScheme.fastSpatialSpec()) { width -> width }
                } else {
                    navExit(forward = false)
                }
            }
        ) {
            composable(FLASH_ROUTE_LIST) {
                LaunchedEffect(Unit) {
                    selectedRunId = null
                    selectedPrebuiltReleaseId = null
                }
                FlashListContent()
            }
            composable(
                route = FLASH_ROUTE_WORKFLOW,
                arguments = listOf(navArgument(FLASH_ARG_RUN_ID) { type = NavType.LongType })
            ) { entry ->
                val routeRunId = entry.arguments?.getLong(FLASH_ARG_RUN_ID) ?: return@composable
                val group = workflowGroups.firstOrNull { it.runId == routeRunId }
                LaunchedEffect(routeRunId) {
                    selectedRunId = routeRunId
                    selectedPrebuiltReleaseId = null
                }
                FlashDetailBackSurface(
                    predictiveBackEnabled = state.predictiveBackEnabled,
                    outerPadding = outerPadding,
                    backgroundUri = state.customBackgroundUri,
                    backgroundImageEnabled = state.backgroundImageEnabled,
                    onBack = ::returnToWorkflowList,
                    onVisibleChange = onDetailPageVisibleChange,
                    backgroundContent = { FlashListContent() }
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(horizontal = AbkScreenHorizontalPadding),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        if (group != null) {
                            item {
                                WorkflowDetailHeader(
                                    group = group,
                                    onBack = ::returnToWorkflowList,
                                    onShowParameters = { parameterTarget = group },
                                    onDelete = {
                                        deleteWorkflowTarget = group
                                        deleteRemoteWorkflowRun = false
                                    }
                                )
                            }

                            artifactCategoryOrder.forEach { category ->
                                val remoteInCategory = group.remote.filter {
                                    DownloadUtils.classifyCategory(DownloadUtils.classifyArtifact(it.name)) == category
                                }
                                val matchedLocalPaths = remoteInCategory
                                    .flatMap { source -> group.local.filter { DownloadUtils.matchesDownloadedArtifact(it, source) } }
                                    .map { it.filePath }
                                    .toSet()
                                val localOnly = group.local
                                    .filter { it.category == category && it.filePath !in matchedLocalPaths }

                                if (remoteInCategory.isNotEmpty() || localOnly.isNotEmpty()) {
                                    item("category-${group.runId}-${category.name}") {
                                        CategoryHeader(category)
                                    }
                                }

                                items(remoteInCategory, key = { "source-${it.id}" }) { artifact ->
                                    ArtifactSourceCard(
                                        artifact = artifact,
                                        downloadedFiles = group.local.filter {
                                            DownloadUtils.matchesDownloadedArtifact(it, artifact)
                                        },
                                        progress = state.downloadProgress[artifact.id],
                                        autoDownloadEligible = state.autoDownload &&
                                            state.pendingAutoDownloadRunId == artifact.runId &&
                                            DownloadUtils.shouldAutoDownload(artifact),
                                        onDownload = { vm.downloadArtifact(artifact) },
                                        onCopyPath = ::copyDownloadedFilePath,
                                        onInstall = ::installManager,
                                        onFlash = {
                                            selectedItem = it
                                            showFlashConfirm = true
                                        },
                                        onDelete = { deleteFileTarget = it },
                                        allowRootActions = rootGranted
                                    )
                                }

                                items(localOnly, key = { "local-${it.filePath}" }) { artifact ->
                                    LocalOnlyArtifactCard(
                                        artifact = artifact,
                                        onCopyPath = ::copyDownloadedFilePath,
                                        onInstall = ::installManager,
                                        onFlash = {
                                            selectedItem = it
                                            showFlashConfirm = true
                                        },
                                        onDelete = { deleteFileTarget = it },
                                        allowRootActions = rootGranted
                                    )
                                }
                            }
                        } else {
                            item {
                                ExpressiveEmptyState(
                                    title = "工作流记录不可用",
                                    subtitle = "该工作流产物已被刷新或删除。",
                                    icon = Icons.Default.Inbox
                                )
                            }
                        }
                    }
                }
            }
            composable(
                route = FLASH_ROUTE_PREBUILT,
                arguments = listOf(navArgument(FLASH_ARG_RELEASE_ID) { type = NavType.LongType })
            ) { entry ->
                val releaseId = entry.arguments?.getLong(FLASH_ARG_RELEASE_ID) ?: return@composable
                val release = state.prebuiltGkiReleases.firstOrNull { it.id == releaseId }
                val selectedPrebuiltAssets = release?.let {
                    state.prebuiltGkiAssetsByReleaseId[it.id].orEmpty()
                }.orEmpty()
                val selectedPrebuiltAssetsLoading = release?.id
                    ?.let { it in state.loadingPrebuiltGkiAssetReleaseIds } == true
                var prebuiltFilter by remember(release?.id) {
                    mutableStateOf(defaultPrebuiltFilter())
                }
                val filteredPrebuiltAssets = remember(selectedPrebuiltAssets, prebuiltFilter) {
                    val candidates = selectedPrebuiltAssets.filter(::isPrebuiltGkiCandidateUi)
                    if (prebuiltFilter.onlyMatches) {
                        candidates.filter { prebuiltAssetMatchesFilter(it, prebuiltFilter) }
                    } else {
                        candidates
                    }
                }
                val recommendedPrebuiltIds = remember(filteredPrebuiltAssets, state.recommendedBuildConfig) {
                    recommendedPrebuiltAssetIdsForUi(filteredPrebuiltAssets, state.recommendedBuildConfig)
                }
                LaunchedEffect(releaseId) {
                    selectedPrebuiltReleaseId = releaseId
                    selectedRunId = null
                }
                LaunchedEffect(release?.id, state.prebuiltGkiEnabled, state.isLoggedIn) {
                    if (release != null && state.prebuiltGkiEnabled && state.isLoggedIn) {
                        vm.loadPrebuiltGkiAssets(release)
                    }
                }
                FlashDetailBackSurface(
                    predictiveBackEnabled = state.predictiveBackEnabled,
                    outerPadding = outerPadding,
                    backgroundUri = state.customBackgroundUri,
                    backgroundImageEnabled = state.backgroundImageEnabled,
                    onBack = ::returnToPrebuiltReleaseList,
                    onVisibleChange = onDetailPageVisibleChange,
                    backgroundContent = { FlashListContent() }
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(horizontal = AbkScreenHorizontalPadding),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        if (release != null) {
                            item {
                                PrebuiltReleaseDetailHeader(
                                    release = release,
                                    sourceCount = selectedPrebuiltAssets.size,
                                    visibleCount = filteredPrebuiltAssets.size,
                                    onBack = ::returnToPrebuiltReleaseList,
                                    onShowParameters = { prebuiltParameterTarget = release },
                                    onRefresh = { vm.loadPrebuiltGkiAssets(release, force = true) }
                                )
                            }

                            item {
                                PrebuiltGkiFilterCard(
                                    filter = prebuiltFilter,
                                    onFilterChange = { prebuiltFilter = it }
                                )
                            }

                            when {
                                selectedPrebuiltAssetsLoading -> {
                                    item {
                                        LoadingRow("正在获取 ${release.name} 的预编译 GKI")
                                    }
                                }
                                filteredPrebuiltAssets.isEmpty() -> {
                                    item {
                                        ExpressiveEmptyState(
                                            title = "未找到匹配资产",
                                            subtitle = if (prebuiltFilter.onlyMatches) {
                                                "当前 release 没有匹配筛选条件的 GKI、boot、img 或 AK3 资产。"
                                            } else {
                                                "当前 release 没有可识别的预编译 GKI 资产。"
                                            },
                                            icon = Icons.Default.Inbox
                                        )
                                    }
                                }
                                else -> {
                                    items(filteredPrebuiltAssets, key = { "prebuilt-${it.id}" }) { asset ->
                                        PrebuiltGkiAssetCard(
                                            asset = asset,
                                            recommended = asset.id in recommendedPrebuiltIds,
                                            downloadedFiles = state.downloadedArtifacts.filter {
                                                DownloadUtils.matchesDownloadedPrebuilt(it, asset)
                                            },
                                            progress = state.downloadProgress[DownloadUtils.prebuiltProgressKey(asset.id)],
                                            onDownload = { vm.downloadPrebuiltGki(asset) },
                                            onCopyPath = ::copyDownloadedFilePath,
                                            onInstall = ::installManager,
                                            onFlash = {
                                                selectedItem = it
                                                showFlashConfirm = true
                                            },
                                            onDelete = { deleteFileTarget = it },
                                            allowRootActions = rootGranted
                                        )
                                    }
                                }
                            }
                        } else {
                            item {
                                ExpressiveEmptyState(
                                    title = "Release 不可用",
                                    subtitle = "该预编译 GKI Release 已被刷新或删除。",
                                    icon = Icons.Default.CloudDownload
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlashDetailBackSurface(
    predictiveBackEnabled: Boolean,
    outerPadding: PaddingValues,
    backgroundUri: String?,
    backgroundImageEnabled: Boolean,
    onBack: () -> Unit,
    onVisibleChange: (Boolean) -> Unit,
    backgroundContent: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val motionScheme = MaterialTheme.motionScheme
    var backProgress by remember { mutableFloatStateOf(0f) }
    val animatedBackProgress by animateFloatAsState(
        targetValue = backProgress.coerceIn(0f, 1f),
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "flash-detail-back-progress"
    )
    val visualBackProgress = animatedBackProgress
        .coerceIn(0f, 1f)
        .pow(FLASH_DETAIL_BACK_VISUAL_EXPONENT)
    val density = LocalDensity.current
    val backOffsetPx = with(density) { FLASH_DETAIL_BACK_MAX_OFFSET.toPx() }
    val backCorner = with(density) { (FLASH_DETAIL_BACK_MAX_CORNER.toPx() * visualBackProgress).toDp() }

    DisposableEffect(Unit) {
        onVisibleChange(true)
        onDispose { onVisibleChange(false) }
    }

    PredictiveBackHandler(enabled = predictiveBackEnabled) { progress ->
        try {
            progress.collect { backEvent ->
                backProgress = backEvent.progress.coerceIn(0f, 1f)
            }
            onBack()
        } catch (_: CancellationException) {
            backProgress = 0f
        }
    }

    BackHandler(enabled = !predictiveBackEnabled) {
        onBack()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val childPageTopInset = outerPadding.calculateTopPadding()
        val childPageBottomInset = outerPadding.calculateBottomPadding()
        val childPageModifier = Modifier
            .fillMaxWidth()
            .height(maxHeight + childPageTopInset + childPageBottomInset)
            .offset(y = -childPageTopInset)
        backgroundContent()
        Box(
            childPageModifier
                .background(Color.Black.copy(alpha = FLASH_DETAIL_BACK_SCRIM_ALPHA * visualBackProgress))
        )
        Box(
            modifier = childPageModifier
                .graphicsLayer {
                    translationX = backOffsetPx * visualBackProgress
                    scaleX = 1f - FLASH_DETAIL_BACK_SCALE_DELTA * visualBackProgress
                    scaleY = 1f - FLASH_DETAIL_BACK_SCALE_DELTA * visualBackProgress
                    alpha = 1f - 0.06f * visualBackProgress
                    shape = RoundedCornerShape(backCorner)
                    clip = visualBackProgress > 0.01f
                }
        ) {
            FlashDetailPageBackground(
                backgroundUri = backgroundUri,
                backgroundImageEnabled = backgroundImageEnabled
            )
            content()
        }
    }
}

@Composable
private fun FlashDetailPageBackground(
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

@Composable
private fun FlashHero(
    buildStatus: BuildStatus,
    availableCount: Int,
    downloadedCount: Int,
    rootGranted: Boolean
) {
    ExpressiveHeroCard(
        title = if (rootGranted) "产物中心" else "文件中心",
        subtitle = if (rootGranted) {
            "先选工作流，再处理内核、管理器和模块。"
        } else {
            "部分激活状态下只提供产物下载和文件查看。"
        },
        icon = if (rootGranted) Icons.Default.FlashOn else Icons.Default.FolderOpen,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        badge = {
            ExpressiveStatusChip(
                label = "$availableCount 个源产物",
                icon = Icons.Default.CloudDownload,
                color = MaterialTheme.colorScheme.tertiary
            )
            ExpressiveStatusChip(
                label = "$downloadedCount 个已下载",
                icon = Icons.Default.Inventory2,
                color = MaterialTheme.colorScheme.secondary
            )
            ExpressiveStatusChip(
                label = when (buildStatus) {
                    BuildStatus.SUCCESS -> "构建成功"
                    BuildStatus.IN_PROGRESS -> "构建中"
                    BuildStatus.QUEUED -> "排队中"
                    BuildStatus.FAILURE -> "构建失败"
                    BuildStatus.CANCELLED -> "已取消"
                    BuildStatus.IDLE -> "等待构建"
                },
                icon = Icons.Default.RunCircle,
                color = when (buildStatus) {
                    BuildStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                    BuildStatus.FAILURE -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outline
                }
            )
        }
    )
}

@Composable
private fun BuildParameterSummaryDialog(
    group: WorkflowArtifactGroup,
    summary: BuildParameterSummary?,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Tune, contentDescription = null) },
        title = { Text("参数详情") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ParameterSection("工作流") {
                    ParameterRow("编号", if (group.runNumber > 0) "#${group.runNumber}" else "#${group.runId}")
                    ParameterRow("标题", group.runTitle)
                    ParameterRow("产物", "${group.remote.size} 个源产物 / ${group.local.size} 个已下载")
                }
                when {
                    summary != null -> {
                        ParameterSummarySections(summary)
                    }
                    loading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LoadingIndicator(Modifier.size(24.dp))
                            Text("正在读取构建信息摘要")
                        }
                    }
                    error != null -> {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {
                        Text(
                            text = "暂无参数详情",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = if (error != null && !loading) {
            { TextButton(onClick = onRetry) { Text("重试") } }
        } else {
            null
        }
    )
}

@Composable
private fun PrebuiltParameterSummaryDialog(
    release: PrebuiltGkiRelease,
    summary: BuildParameterSummary?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Tune, contentDescription = null) },
        title = { Text("参数详情") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ParameterSection("Release") {
                    ParameterRow("名称", release.name)
                    ParameterRow("Tag", release.tagName)
                    ParameterRow("发布时间", releaseDateLabel(release.publishedAt))
                    ParameterRow("资产", if (release.assetCount > 0) "${release.assetCount} 个资产" else "未知")
                }
                if (summary != null) {
                    ParameterSummarySections(summary)
                } else {
                    Text(
                        text = "Release 内容中没有可解析的参数矩阵",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun ParameterSummarySections(summary: BuildParameterSummary) {
    ParameterSection("版本参数") {
        ParameterRow("Android 版本", summary.androidVersion)
        ParameterRow("内核版本", summary.kernelVersion)
        ParameterRow("子版本号", summary.subLevel)
        ParameterRow("补丁级别", summary.osPatchLevel)
        ParameterRow("构建时间", summary.buildTime)
    }
    ParameterSection("KernelSU") {
        ParameterRow("KSU 变体", summary.ksuVariant)
        ParameterRow("KSU 分支", summary.ksuBranch)
        ParameterRow("SUSFS 状态", summary.susfsEnabled)
    }
    ParameterSection("补丁与功能") {
        ParameterRow("ZRAM 增强", summary.zramEnabled)
        ParameterRow("ZRAM 完整算法", summary.zramFullAlgo)
        ParameterRow("ZRAM 额外算法", summary.zramExtraAlgos)
        ParameterRow("BBG 补丁", summary.bbgEnabled)
        ParameterRow("DDK LSM", summary.ddkLsm)
        ParameterRow("NTsync 补丁", summary.ntsyncEnabled)
        ParameterRow("网络增强", summary.networkingEnabled)
        ParameterRow("KPM 功能", summary.kpmEnabled)
        ParameterRow("KPM 密码", summary.kpmPassword)
        ParameterRow("Re-Kernel", summary.reKernelEnabled)
        ParameterRow("虚拟化支持", summary.virtualizationSupport)
        ParameterRow("自定义注入", summary.customInjection)
        ParameterRow("Stock Config", summary.stockConfig)
    }
    val extraRows = summary.extraRows.orEmpty()
    if (extraRows.isNotEmpty()) {
        ParameterSection("额外信息") {
            extraRows.forEach { (label, value) ->
                ParameterRow(label, value)
            }
        }
    }
}

@Composable
private fun ParameterSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        content()
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ParameterRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = parameterDisplayValue(value),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun parameterDisplayValue(value: String): String {
    val trimmed = value.trim()
    return when (trimmed.lowercase()) {
        "" -> "未知"
        "true" -> "启用"
        "false" -> "关闭"
        "none" -> "无"
        else -> trimmed
    }
}

private fun parsePrebuiltGkiParameterSummary(release: PrebuiltGkiRelease): BuildParameterSummary? {
    val values = linkedMapOf<String, String>()
    val extraRows = linkedMapOf<String, String>()
    parseReleaseBodyParameterRows(release.body).forEach { (label, rawValue) ->
        val value = rawValue.trim()
        val key = normalizeReleaseParameterLabel(label)
        if (key != null) {
            values[key] = sanitizeReleaseParameterValue(key, value)
        } else if (isReleaseExtraParameterLabel(label)) {
            extraRows[label.trim()] = value.ifBlank { "无" }
        }
    }
    if (values.isEmpty() && extraRows.isEmpty()) return null

    val inferredVersion = inferPrebuiltVersionFields(release)
    return BuildParameterSummary(
        runId = -release.id,
        runNumber = 0,
        runTitle = release.name,
        runCreatedAt = release.publishedAt,
        runHtmlUrl = release.htmlUrl,
        androidVersion = values["androidVersion"].orEmpty().ifBlank { inferredVersion.androidVersion },
        kernelVersion = values["kernelVersion"].orEmpty().ifBlank { inferredVersion.kernelVersion },
        subLevel = values["subLevel"].orEmpty().ifBlank { inferredVersion.subLevel },
        osPatchLevel = values["osPatchLevel"].orEmpty(),
        ksuVariant = values["ksuVariant"].orEmpty(),
        ksuBranch = values["ksuBranch"].orEmpty(),
        buildTime = values["buildTime"].orEmpty(),
        susfsEnabled = values["susfsEnabled"].orEmpty(),
        zramEnabled = values["zramEnabled"].orEmpty(),
        zramFullAlgo = values["zramFullAlgo"].orEmpty(),
        zramExtraAlgos = values["zramExtraAlgos"].orEmpty(),
        bbgEnabled = values["bbgEnabled"].orEmpty(),
        ddkLsm = values["ddkLsm"].orEmpty(),
        ntsyncEnabled = values["ntsyncEnabled"].orEmpty(),
        networkingEnabled = values["networkingEnabled"].orEmpty(),
        kpmEnabled = values["kpmEnabled"].orEmpty(),
        kpmPassword = values["kpmPassword"].orEmpty(),
        reKernelEnabled = values["reKernelEnabled"].orEmpty(),
        virtualizationSupport = values["virtualizationSupport"].orEmpty(),
        customInjection = values["customInjection"].orEmpty(),
        stockConfig = values["stockConfig"].orEmpty(),
        source = "release_body",
        extraRows = extraRows
    )
}

private fun parseReleaseBodyParameterRows(body: String): List<Pair<String, String>> {
    if (body.isBlank()) return emptyList()
    return body.lineSequence()
        .mapNotNull(::parseReleaseBodyParameterRow)
        .filterNot { (label, value) ->
            val normalized = label.replace(Regex("\\s+"), "")
            normalized == "项目" && value.replace(Regex("\\s+"), "") == "内容"
        }
        .toList()
}

private fun parseReleaseBodyParameterRow(line: String): Pair<String, String>? {
    val trimmed = line.trim()
    if (trimmed.isBlank()) return null
    if (trimmed.startsWith("|")) {
        val cells = trimmed.trim('|')
            .split('|')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (cells.size >= 2 && !cells[0].all { it == '-' || it == ':' }) {
            return cells[0] to cells.drop(1).joinToString(" | ")
        }
    }
    val separated = trimmed.split(Regex("\\t+| {2,}"), limit = 2)
    if (separated.size == 2) return separated[0].trim() to separated[1].trim()
    val colonIndex = listOf(trimmed.indexOf(':'), trimmed.indexOf('：'))
        .filter { it >= 0 }
        .minOrNull()
    if (colonIndex != null) {
        return trimmed.substring(0, colonIndex).trim() to trimmed.substring(colonIndex + 1).trim()
    }
    return RELEASE_PARAMETER_LABELS.firstOrNull { trimmed.startsWith(it) }?.let { label ->
        label to trimmed.removePrefix(label).trim().trimStart(':', '：').trim()
    }
}

private fun normalizeReleaseParameterLabel(label: String): String? {
    val compact = label.replace(Regex("\\s+"), "").lowercase()
    return when {
        compact.contains("android版本") -> "androidVersion"
        compact.contains("内核版本") -> "kernelVersion"
        compact.contains("子版本号") -> "subLevel"
        compact.contains("补丁级别") -> "osPatchLevel"
        compact.contains("ksu变体") -> "ksuVariant"
        compact.contains("ksu分支") -> "ksuBranch"
        compact.contains("构建时间") -> "buildTime"
        compact.contains("susfs状态") -> "susfsEnabled"
        compact.contains("zram增强") -> "zramEnabled"
        compact.contains("zram完整算法") -> "zramFullAlgo"
        compact.contains("zram额外算法") -> "zramExtraAlgos"
        compact.contains("bbg补丁") -> "bbgEnabled"
        compact.contains("ddklsm") -> "ddkLsm"
        compact.contains("ntsync补丁") -> "ntsyncEnabled"
        compact.contains("网络增强") || compact.contains("networking增强") || compact.contains("networing增强") -> "networkingEnabled"
        compact.contains("kpm功能") -> "kpmEnabled"
        compact.contains("kpm密码") -> "kpmPassword"
        compact.contains("re-kernel") || compact.contains("rekernel") -> "reKernelEnabled"
        compact.contains("虚拟化支持") -> "virtualizationSupport"
        compact == "自定义注入" -> "customInjection"
        compact.contains("stockconfig") -> "stockConfig"
        else -> null
    }
}

private fun sanitizeReleaseParameterValue(key: String, value: String): String {
    if (key != "kpmPassword") return value.ifBlank { "无" }
    val normalized = value.trim().lowercase()
    return when {
        normalized.isBlank() -> "默认"
        normalized in setOf("默认", "default", "无", "none", "not set") -> "默认"
        else -> "已设置"
    }
}

private fun isReleaseExtraParameterLabel(label: String): Boolean {
    val compact = label.replace(Regex("\\s+"), "").lowercase()
    return RELEASE_EXTRA_PARAMETER_LABELS.any { compact == it }
}

private fun inferPrebuiltVersionFields(release: PrebuiltGkiRelease): PrebuiltVersionFields {
    val source = "${release.name}\n${release.tagName}\n${release.body}"
    val androidKernel = Regex("android\\s*(\\d+)\\s*/\\s*(\\d+\\.\\d+)(?:\\.(\\d+))?", RegexOption.IGNORE_CASE)
        .find(source)
    if (androidKernel != null) {
        return PrebuiltVersionFields(
            androidVersion = "android${androidKernel.groupValues[1]}",
            kernelVersion = androidKernel.groupValues[2],
            subLevel = androidKernel.groupValues.getOrNull(3).orEmpty()
        )
    }
    val android = Regex("android\\s*(\\d+)", RegexOption.IGNORE_CASE)
        .find(source)
        ?.groupValues
        ?.getOrNull(1)
        ?.let { "android$it" }
        .orEmpty()
    return PrebuiltVersionFields(androidVersion = android)
}

private data class PrebuiltVersionFields(
    val androidVersion: String = "",
    val kernelVersion: String = "",
    val subLevel: String = ""
)

private val RELEASE_PARAMETER_LABELS = listOf(
    "自定义注入参数列表",
    "网络增强 (IPSet + BBR)",
    "Release asset 数",
    "5.10 修订版本",
    "自定义版本名",
    "一加 8E 支持",
    "Android 版本",
    "Stock Config",
    "ZRAM 完整算法",
    "ZRAM 额外算法",
    "NTsync 补丁",
    "虚拟化支持",
    "自定义注入",
    "内核版本",
    "子版本号",
    "补丁级别",
    "KSU 变体",
    "KSU 分支",
    "构建时间",
    "SUSFS 状态",
    "ZRAM 增强",
    "BBG 补丁",
    "DDK LSM",
    "网络增强",
    "KPM 功能",
    "KPM 密码",
    "Re-Kernel",
    "Artifact 数",
    "源 commit",
    "源 run"
).sortedByDescending { it.length }

private val RELEASE_EXTRA_PARAMETER_LABELS = setOf(
    "源run",
    "源commit",
    "artifact数",
    "releaseasset数",
    "自定义版本名",
    "5.10修订版本",
    "一加8e支持",
    "自定义注入参数列表"
)

@Composable
private fun FlashContentTabs(
    active: FlashContentTab,
    onSelect: (FlashContentTab) -> Unit
) {
    TabRow(
        selectedTabIndex = FlashContentTab.entries.indexOf(active),
        containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainer),
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        FlashContentTab.entries.forEach { tab ->
            Tab(
                selected = active == tab,
                onClick = { onSelect(tab) },
                text = { Text(tab.label) },
                icon = {
                    Icon(
                        if (tab == FlashContentTab.Workflows) Icons.Default.FolderSpecial else Icons.Default.CloudDownload,
                        null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun PrebuiltReleaseListHeader(
    releaseCount: Int,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.CloudDownload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text("预编译 GKI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "$releaseCount 个 Release · 进入子页面后筛选资产",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(onClick = onRefresh, enabled = !isLoading) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("刷新")
        }
    }
}

@Composable
private fun PrebuiltReleaseCard(
    release: PrebuiltGkiRelease,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainer)
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CloudDownload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        release.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${release.tagName} · ${releaseDateLabel(release.publishedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ExpressiveStatusChip(
                    label = if (release.assetCount > 0) "${release.assetCount} 个资产" else "点进后加载资产",
                    color = MaterialTheme.colorScheme.primary
                )
                ExpressiveStatusChip(label = "手动下载", color = MaterialTheme.colorScheme.secondary)
                ExpressiveStatusChip(label = "分 Release 筛选", color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun PrebuiltReleaseDetailHeader(
    release: PrebuiltGkiRelease,
    sourceCount: Int,
    visibleCount: Int,
    onBack: () -> Unit,
    onShowParameters: () -> Unit,
    onRefresh: () -> Unit
) {
    ExpressiveSectionCard(
        title = release.name,
        subtitle = "${release.tagName} · ${releaseDateLabel(release.publishedAt)}",
        icon = Icons.Default.CloudDownload
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "$visibleCount / $sourceCount 个可见资产",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onShowParameters) {
                Icon(Icons.Default.Tune, contentDescription = "参数详情")
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新资产")
            }
        }
    }
}

@Composable
private fun PrebuiltGkiFilterCard(
    filter: PrebuiltGkiFilter,
    onFilterChange: (PrebuiltGkiFilter) -> Unit
) {
    val androidOptions = remember { listOf("") + KernelSupport.androidVersions() }
    val kernelOptions = remember { listOf("") + KernelSupport.kernelVersions() }
    val subLevelOptions = remember(filter.androidVersion, filter.kernelVersion) {
        listOf("") + prebuiltSubLevelOptions(filter.androidVersion, filter.kernelVersion)
    }
    val patchOptions = remember(filter.androidVersion, filter.kernelVersion, filter.subLevel) {
        listOf("") + prebuiltPatchOptions(filter.androidVersion, filter.kernelVersion, filter.subLevel)
    }

    fun updateFilter(next: PrebuiltGkiFilter) {
        onFilterChange(sanitizePrebuiltFilter(next))
    }

    ExpressiveSectionCard(
        title = "筛选器",
        subtitle = "未选择的条件视为不限",
        icon = Icons.Default.Tune
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrebuiltDropdownField(
                label = "Android 版本",
                value = filter.androidVersion,
                options = androidOptions,
                onSelect = { updateFilter(filter.copy(androidVersion = it)) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrebuiltDropdownField(
                    label = "内核版本",
                    value = filter.kernelVersion,
                    options = kernelOptions,
                    onSelect = { updateFilter(filter.copy(kernelVersion = it)) },
                    modifier = Modifier.weight(1f)
                )
                PrebuiltDropdownField(
                    label = "小版本",
                    value = filter.subLevel,
                    options = subLevelOptions,
                    onSelect = { updateFilter(filter.copy(subLevel = it)) },
                    modifier = Modifier.weight(1f)
                )
            }
            PrebuiltDropdownField(
                label = "补丁级别",
                value = filter.osPatchLevel,
                options = patchOptions,
                onSelect = { updateFilter(filter.copy(osPatchLevel = it)) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    updateFilter(filter.copy(onlyMatches = !filter.onlyMatches))
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = filter.onlyMatches,
                    onCheckedChange = { updateFilter(filter.copy(onlyMatches = it)) }
                )
                Text("只看匹配当前筛选条件的资产")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrebuiltDropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = prebuiltOptionLabel(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.distinct().forEach { option ->
                DropdownMenuItem(
                    text = { Text(prebuiltOptionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@Composable
private fun LoadingRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        LoadingIndicator(modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
private fun PrebuiltGkiAssetCard(
    asset: PrebuiltGkiAsset,
    recommended: Boolean,
    downloadedFiles: List<DownloadedArtifact>,
    progress: Int?,
    onDownload: () -> Unit,
    onCopyPath: (DownloadedArtifact) -> Unit,
    onInstall: (DownloadedArtifact) -> Unit,
    onFlash: (DownloadedArtifact) -> Unit,
    onDelete: (DownloadedArtifact) -> Unit,
    allowRootActions: Boolean
) {
    val type = prebuiltArtifactType(asset)
    val animatedProgress by animateFloatAsState(
        targetValue = ((progress ?: 0) / 100f).coerceIn(0f, 1f),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "prebuilt-gki-download"
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainer)
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ArtifactHeader(
                icon = artifactIcon(type),
                title = asset.name,
                subtitle = "${asset.releaseTag} · ${DownloadUtils.formatSize(asset.sizeBytes)}",
                chip = if (recommended) "设备推荐" else "Release"
            )

            when {
                progress != null -> {
                    LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth())
                    Text("下载中 $progress%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                downloadedFiles.isEmpty() -> {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("下载预编译 GKI")
                    }
                }
                else -> {
                    downloadedFiles.forEachIndexed { index, file ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DownloadedOutputRow(
                            artifact = file,
                            onCopyPath = { onCopyPath(file) },
                            onInstall = { onInstall(file) },
                            onFlash = { onFlash(file) },
                            onDelete = { onDelete(file) },
                            allowRootActions = allowRootActions
                        )
                    }
                }
            }
        }
    }
}

private fun prebuiltArtifactType(asset: PrebuiltGkiAsset): ArtifactType {
    val type = DownloadUtils.classifyArtifact(asset.name)
    return if (type == ArtifactType.OTHER) ArtifactType.KERNEL_PACKAGE else type
}

@Composable
private fun WorkflowRunCard(
    group: WorkflowArtifactGroup,
    active: Boolean,
    cancelling: Boolean,
    onClick: () -> Unit,
    onShowParameters: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    val sourceCount = group.remote.size
    val downloadedCount = group.local.size
    val categories = artifactCategoryOrder.filter { category ->
        group.remote.any { DownloadUtils.classifyCategory(DownloadUtils.classifyArtifact(it.name)) == category } ||
            group.local.any { it.category == category }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainer)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.FolderSpecial, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (group.runId == PREBUILT_GKI_RUN_ID) {
                            "预编译 GKI"
                        } else {
                            "工作流 ${if (group.runNumber > 0) "#${group.runNumber}" else "#${group.runId}"}"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = group.runTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onShowParameters) {
                    Icon(Icons.Default.Tune, contentDescription = "参数详情")
                }
                if (active) {
                    IconButton(onClick = onCancel, enabled = !cancelling) {
                        if (cancelling) {
                            LoadingIndicator(Modifier.size(20.dp))
                        } else {
                            Icon(
                                Icons.Default.Cancel,
                                contentDescription = "取消工作流",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除工作流")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ExpressiveStatusChip(label = "$sourceCount 个源产物", color = MaterialTheme.colorScheme.primary)
                ExpressiveStatusChip(label = "$downloadedCount 个已下载", color = MaterialTheme.colorScheme.secondary)
                categories.forEach {
                    ExpressiveStatusChip(label = it.label(), color = MaterialTheme.colorScheme.surfaceTint)
                }
            }
        }
    }
}

@Composable
private fun WorkflowDetailHeader(
    group: WorkflowArtifactGroup,
    onBack: () -> Unit,
    onShowParameters: () -> Unit,
    onDelete: () -> Unit
) {
    ExpressiveSectionCard(
        title = if (group.runId == PREBUILT_GKI_RUN_ID) {
            "预编译 GKI"
        } else {
            "工作流 ${if (group.runNumber > 0) "#${group.runNumber}" else "#${group.runId}"}"
        },
        subtitle = group.runTitle,
        icon = Icons.Default.FolderSpecial
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "${group.remote.size} 个源产物 / ${group.local.size} 个已下载",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onShowParameters) {
                Icon(Icons.Default.Tune, contentDescription = "参数详情")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除工作流")
            }
        }
    }
}

@Composable
private fun CategoryHeader(category: ArtifactCategory) {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(category.icon(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(
            category.label(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ArtifactSourceCard(
    artifact: BuildArtifact,
    downloadedFiles: List<DownloadedArtifact>,
    progress: Int?,
    autoDownloadEligible: Boolean,
    onDownload: () -> Unit,
    onCopyPath: (DownloadedArtifact) -> Unit,
    onInstall: (DownloadedArtifact) -> Unit,
    onFlash: (DownloadedArtifact) -> Unit,
    onDelete: (DownloadedArtifact) -> Unit,
    allowRootActions: Boolean
) {
    val type = DownloadUtils.classifyArtifact(artifact.name)
    val animatedProgress by animateFloatAsState(
        targetValue = ((progress ?: 0) / 100f).coerceIn(0f, 1f),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "artifact-download"
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainer)
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ArtifactHeader(
                icon = artifactIcon(type),
                title = artifact.name,
                subtitle = "${artifactTypeLabel(type)} · ${DownloadUtils.formatSize(artifact.sizeInBytes)}",
                chip = if (autoDownloadEligible) "下次自动" else artifactTypeLabel(type)
            )

            when {
                progress != null -> {
                    LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth())
                    Text("下载中 $progress%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                downloadedFiles.isEmpty() -> {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("下载")
                    }
                }
                else -> {
                    downloadedFiles.forEachIndexed { index, file ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DownloadedOutputRow(
                            artifact = file,
                            onCopyPath = { onCopyPath(file) },
                            onInstall = { onInstall(file) },
                            onFlash = { onFlash(file) },
                            onDelete = { onDelete(file) },
                            allowRootActions = allowRootActions
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalOnlyArtifactCard(
    artifact: DownloadedArtifact,
    onCopyPath: (DownloadedArtifact) -> Unit,
    onInstall: (DownloadedArtifact) -> Unit,
    onFlash: (DownloadedArtifact) -> Unit,
    onDelete: (DownloadedArtifact) -> Unit,
    allowRootActions: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainer)
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ArtifactHeader(
                icon = artifactIcon(artifact.type),
                title = artifact.name,
                subtitle = "${artifactTypeLabel(artifact.type)} · ${DownloadUtils.formatSize(artifact.sizeBytes)}",
                chip = "本地文件"
            )
            DownloadedOutputRow(
                artifact = artifact,
                onCopyPath = { onCopyPath(artifact) },
                onInstall = { onInstall(artifact) },
                onFlash = { onFlash(artifact) },
                onDelete = { onDelete(artifact) },
                allowRootActions = allowRootActions
            )
        }
    }
}

@Composable
private fun ArtifactHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    chip: String
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            icon,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
            style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        ExpressiveStatusChip(label = chip, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun DownloadedOutputRow(
    artifact: DownloadedArtifact,
    onCopyPath: () -> Unit,
    onInstall: () -> Unit,
    onFlash: () -> Unit,
    onDelete: () -> Unit,
    allowRootActions: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.Default.CheckCircle,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    artifact.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${artifactTypeLabel(artifact.type)} · ${DownloadUtils.formatSize(artifact.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除文件",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onCopyPath,
                modifier = Modifier.weight(1f).height(40.dp)
            ) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("复制路径")
            }
            if (allowRootActions) {
                when (artifact.type) {
                    ArtifactType.KERNEL_IMG,
                    ArtifactType.ANYKERNEL3,
                    ArtifactType.SUSFS_MODULE -> Button(
                        onClick = onFlash,
                        modifier = Modifier.weight(1f).height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (artifact.type == ArtifactType.KERNEL_IMG) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    ) {
                        Icon(
                            if (artifact.type == ArtifactType.SUSFS_MODULE) Icons.Default.Extension else Icons.Default.FlashOn,
                            null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(flashButtonLabel(artifact.type))
                    }
                    ArtifactType.KSU_MANAGER -> Button(
                        onClick = onInstall,
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Icon(Icons.Default.InstallMobile, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("安装")
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun FlashTerminalDialog(
    title: String,
    running: Boolean,
    success: Boolean?,
    logLines: List<String>,
    canReboot: Boolean,
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
    val terminalTextColor = colorScheme.onSurface
    val terminalCommandColor = colorScheme.primary
    LaunchedEffect(logLines.size) {
        terminalScroll.animateScrollTo(terminalScroll.maxValue)
    }
    AlertDialog(
        onDismissRequest = { if (!running) onClose() },
        icon = {
            when {
                running -> LoadingIndicator(modifier = Modifier.size(24.dp))
                success == true -> Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                success == false -> Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                else -> Icon(Icons.Default.Terminal, null)
            }
        },
        title = { Text(if (running) "正在执行 · $title" else title) },
        text = {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp, max = 360.dp),
                shape = MaterialTheme.shapes.large,
                color = terminalContainer,
                contentColor = terminalTextColor,
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(terminalScroll)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    logLines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (line.startsWith("${'$'}")) {
                                terminalCommandColor
                            } else {
                                terminalTextColor
                            }
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
                    TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
                    if (canReboot) {
                        Button(
                            onClick = onReboot,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
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

private fun buildWorkflowGroups(
    remoteArtifacts: List<BuildArtifact>,
    downloadedArtifacts: List<DownloadedArtifact>
): List<WorkflowArtifactGroup> {
    val runIds = (remoteArtifacts.map { it.runId } + downloadedArtifacts.map { it.runId }).distinct()
    return runIds.map { runId ->
        val remote = remoteArtifacts.filter { it.runId == runId }
        val local = downloadedArtifacts.filter { it.runId == runId }
        val firstRemote = remote.firstOrNull()
        val firstLocal = local.firstOrNull()
        WorkflowArtifactGroup(
            runId = runId,
            runTitle = firstRemote?.runTitle?.ifBlank { null }
                ?: firstLocal?.runTitle?.ifBlank { null }
                ?: "未关联工作流",
            runNumber = firstRemote?.runNumber ?: firstLocal?.runNumber ?: 0,
            remote = remote,
            local = local
        )
    }.sortedWith(
        compareByDescending<WorkflowArtifactGroup> { it.runNumber }
            .thenByDescending { it.runId }
    )
}

private data class WorkflowArtifactGroup(
    val runId: Long,
    val runTitle: String,
    val runNumber: Int,
    val remote: List<BuildArtifact>,
    val local: List<DownloadedArtifact>
)

private fun WorkflowRun.isActiveFlashRun(): Boolean =
    status in setOf("queued", "waiting", "requested", "pending", "in_progress")

private fun artifactIcon(type: ArtifactType) = when (type) {
    ArtifactType.KERNEL_PACKAGE -> Icons.Default.Inventory2
    ArtifactType.KERNEL_IMG -> Icons.Default.Memory
    ArtifactType.ANYKERNEL3 -> Icons.Default.Archive
    ArtifactType.KSU_MANAGER -> Icons.Default.Shield
    ArtifactType.SUSFS_MODULE -> Icons.Default.Extension
    ArtifactType.OTHER -> Icons.Default.InsertDriveFile
}

private fun artifactTypeLabel(type: ArtifactType) = when (type) {
    ArtifactType.KERNEL_PACKAGE -> "内核构建包"
    ArtifactType.KERNEL_IMG -> "内核镜像"
    ArtifactType.ANYKERNEL3 -> "AnyKernel3 刷写包"
    ArtifactType.KSU_MANAGER -> "KernelSU 管理器"
    ArtifactType.SUSFS_MODULE -> "SUSFS 模块"
    ArtifactType.OTHER -> "其他文件"
}

private fun flashButtonLabel(type: ArtifactType) = when (type) {
    ArtifactType.KERNEL_IMG -> "刷写"
    ArtifactType.ANYKERNEL3 -> "刷入 AK3"
    ArtifactType.SUSFS_MODULE -> "安装模块"
    else -> "执行"
}

private fun flashOperationLabel(type: ArtifactType) = when (type) {
    ArtifactType.KERNEL_IMG -> "刷写 boot 镜像"
    ArtifactType.ANYKERNEL3 -> "刷入 AnyKernel3"
    ArtifactType.SUSFS_MODULE -> "安装模块"
    else -> "执行"
}

private fun flashCommandPreview(item: DownloadedArtifact) = when (item.type) {
    ArtifactType.KERNEL_IMG -> "dd boot <- ${item.name}"
    ArtifactType.ANYKERNEL3 -> "flash-ak3 ${item.name}"
    ArtifactType.SUSFS_MODULE -> "install-module ${item.name}"
    else -> "run ${item.name}"
}

private const val FLASH_ROUTE_LIST = "flash_list"
private const val FLASH_ARG_RUN_ID = "runId"
private const val FLASH_ARG_RELEASE_ID = "releaseId"
private const val FLASH_ROUTE_WORKFLOW = "workflow/{$FLASH_ARG_RUN_ID}"
private const val FLASH_ROUTE_PREBUILT = "prebuilt/{$FLASH_ARG_RELEASE_ID}"

private fun flashWorkflowRoute(runId: Long) = "workflow/$runId"

private fun flashPrebuiltRoute(releaseId: Long) = "prebuilt/$releaseId"

private enum class FlashContentTab(val label: String) {
    Workflows("构建产物"),
    PrebuiltGki("预编译 GKI")
}

private data class PrebuiltGkiFilter(
    val androidVersion: String,
    val kernelVersion: String,
    val subLevel: String,
    val osPatchLevel: String,
    val onlyMatches: Boolean = true
)

private fun defaultPrebuiltFilter(): PrebuiltGkiFilter = PrebuiltGkiFilter(
    androidVersion = "",
    kernelVersion = "",
    subLevel = "",
    osPatchLevel = ""
)

private fun sanitizePrebuiltFilter(filter: PrebuiltGkiFilter): PrebuiltGkiFilter {
    val subOptions = prebuiltSubLevelOptions(filter.androidVersion, filter.kernelVersion)
    val subLevel = filter.subLevel.takeIf { it.isBlank() || it in subOptions }.orEmpty()
    val patchOptions = prebuiltPatchOptions(filter.androidVersion, filter.kernelVersion, subLevel)
    val patch = filter.osPatchLevel.takeIf { it.isBlank() || it in patchOptions }.orEmpty()
    return filter.copy(subLevel = subLevel, osPatchLevel = patch)
}

private fun prebuiltSubLevelOptions(androidVersion: String, kernelVersion: String): List<String> =
    KernelSupport.entries
        .filter { androidVersion.isBlank() || it.androidVersion == androidVersion }
        .filter { kernelVersion.isBlank() || it.kernelVersion == kernelVersion }
        .map { it.subLevel }
        .distinct()
        .sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }

private fun prebuiltPatchOptions(androidVersion: String, kernelVersion: String, subLevel: String): List<String> =
    KernelSupport.entries
        .filter { androidVersion.isBlank() || it.androidVersion == androidVersion }
        .filter { kernelVersion.isBlank() || it.kernelVersion == kernelVersion }
        .filter { subLevel.isBlank() || it.subLevel == subLevel }
        .map { it.osPatchLevel }
        .distinct()
        .sortedBy(::patchMonthIndexForUi)

private fun prebuiltOptionLabel(value: String): String = value.ifBlank { "不限" }

private fun patchMonthIndexForUi(value: String): Int {
    val parts = value.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return Int.MAX_VALUE
    val month = parts.getOrNull(1)?.toIntOrNull() ?: return Int.MAX_VALUE
    return year * 12 + month
}

private fun releaseDateLabel(value: String): String =
    value.takeIf { it.length >= 10 }?.take(10) ?: "未知日期"

private fun isPrebuiltGkiCandidateUi(asset: PrebuiltGkiAsset): Boolean {
    val lower = asset.name.lowercase()
    val type = DownloadUtils.classifyArtifact(asset.name)
    return type in setOf(ArtifactType.KERNEL_PACKAGE, ArtifactType.KERNEL_IMG, ArtifactType.ANYKERNEL3) ||
        ((lower.endsWith(".img") || lower.endsWith(".zip")) &&
            listOf("gki", "kernel", "boot", "anykernel", "ak3").any { lower.contains(it) })
}

private fun prebuiltAssetMatchesFilter(asset: PrebuiltGkiAsset, filter: PrebuiltGkiFilter): Boolean {
    val haystack = prebuiltHaystack(asset)
    return prebuiltAndroidMatches(haystack, filter.androidVersion) &&
        prebuiltKernelMatches(haystack, filter.kernelVersion, filter.subLevel) &&
        prebuiltTextMatches(haystack, filter.osPatchLevel)
}

private fun recommendedPrebuiltAssetIdsForUi(
    assets: List<PrebuiltGkiAsset>,
    recommended: KernelBuildConfig?
): Set<Long> {
    if (recommended == null) return emptySet()
    val scored = assets.map { it to prebuiltRecommendationScoreForUi(it, recommended) }
        .filter { it.second > 0 }
    val best = scored.maxOfOrNull { it.second } ?: return emptySet()
    return scored.filter { it.second == best }.map { it.first.id }.toSet()
}

private fun prebuiltRecommendationScoreForUi(asset: PrebuiltGkiAsset, recommended: KernelBuildConfig?): Int {
    recommended ?: return 0
    if (recommended.subLevel == "X") return 0
    val haystack = prebuiltHaystack(asset)
    val kernelSub = Regex(
        """(^|[^0-9])${Regex.escape(recommended.kernelVersion)}[.-]?${Regex.escape(recommended.subLevel)}([^0-9]|$)"""
    ).containsMatchIn(haystack)
    if (!kernelSub) return 0

    val androidNumber = recommended.androidVersion.removePrefix("android")
    val hasAndroid = haystack.contains(recommended.androidVersion.lowercase()) ||
        haystack.contains("android-$androidNumber") ||
        haystack.contains("a$androidNumber")
    val hasPatch = recommended.osPatchLevel.isNotBlank() && haystack.contains(recommended.osPatchLevel.lowercase())
    return 10 + (if (hasAndroid) 5 else 0) + (if (hasPatch) 8 else 0)
}

private fun prebuiltHaystack(asset: PrebuiltGkiAsset): String =
    listOf(asset.name, asset.releaseTag, asset.releaseName, asset.releaseBody)
        .joinToString(" ")
        .lowercase()
        .replace('_', '-')

private fun prebuiltAndroidMatches(haystack: String, value: String): Boolean {
    val android = value.trim().lowercase().replace('_', '-')
    if (android.isBlank()) return true
    val number = android.removePrefix("android").removePrefix("-")
    return haystack.contains(android) ||
        (number.isNotBlank() && (
            haystack.contains("android$number") ||
                haystack.contains("android-$number") ||
                haystack.contains("a$number")
            ))
}

private fun prebuiltKernelMatches(haystack: String, kernelVersion: String, subLevel: String): Boolean {
    val kernel = kernelVersion.trim()
    val sub = subLevel.trim()
    if (kernel.isBlank()) return true
    if (sub.isBlank()) return haystack.contains(kernel.lowercase())
    return Regex(
        """(^|[^0-9])${Regex.escape(kernel)}[.-]?${Regex.escape(sub)}([^0-9]|$)"""
    ).containsMatchIn(haystack)
}

private fun prebuiltTextMatches(haystack: String, value: String): Boolean {
    val text = value.trim().lowercase().replace('_', '-')
    return text.isBlank() || haystack.contains(text)
}

private val artifactCategoryOrder = listOf(
    ArtifactCategory.KERNEL,
    ArtifactCategory.MANAGER,
    ArtifactCategory.MODULE
)

private fun ArtifactCategory.label(): String = when (this) {
    ArtifactCategory.KERNEL -> "内核产物"
    ArtifactCategory.MANAGER -> "管理器"
    ArtifactCategory.MODULE -> "模块"
}

private fun ArtifactCategory.icon(): ImageVector = when (this) {
    ArtifactCategory.KERNEL -> Icons.Default.Memory
    ArtifactCategory.MANAGER -> Icons.Default.Shield
    ArtifactCategory.MODULE -> Icons.Default.Extension
}
