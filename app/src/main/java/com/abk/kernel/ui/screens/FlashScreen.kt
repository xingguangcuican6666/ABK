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
    var terminalTitle by remember { mutableStateOf("Terminal") }
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
        Toast.makeText(context, "File path copied", Toast.LENGTH_SHORT).show()
    }

    fun appendTerminalOutput(line: String) {
        scope.launch(Dispatchers.Main.immediate) {
            terminalLog = terminalLog + line
        }
    }

    fun installManager(item: DownloadedArtifact) {
        if (!rootGranted) {
            showFailure(
                "Root not authorized",
                listOf(
                    "${'$'} pm install -r ${item.name}",
                    "Partial activation mode — file tab only shows downloaded files.",
                    "To install the manager app directly, please grant root access first."
                )
            )
            return
        }
        terminalTitle = "Install Manager APK"
        terminalCanReboot = false
        terminalRunning = true
        terminalSuccess = null
        terminalLog = listOf(
            "${'$'} pm install -r ${item.name}",
            "file: ${item.filePath}",
            "",
            "Waiting for root shell. Do not close the app…"
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
                listOf(if (result.success) "Command completed, no output." else "Command failed, no log returned.")
            }
        }
    }

    fun startFlash(item: DownloadedArtifact) {
        if (!rootGranted) {
            showFailure(
                "Root not authorized",
                listOf(
                    "${'$'} ${flashCommandPreview(item)}",
                    "Partial activation mode — file tab only shows downloaded files.",
                    "To flash or install modules, please grant root access first."
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
            "Waiting for root shell. Do not close the app…"
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
                        else -> RootUtils.ShellResult(false, listOf("This file type does not support auto-flash"))
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
                listOf(if (result.success) "Command completed, no output." else "Command failed, no log returned.")
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
            title = { Text("Operation failed") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { vm.clearError() }) {
                    Text("Got it")
                }
            }
        )
    }

    deleteFileTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteFileTarget = null },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete file") },
            text = { Text("This will delete the local record and downloaded file:\n${item.name}") },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteDownloadedArtifact(item.filePath)
                        deleteFileTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
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
            title = { Text(if (group.runId == PREBUILT_GKI_RUN_ID) "Delete prebuilt GKI files" else "Delete workflow record") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (group.runId == PREBUILT_GKI_RUN_ID) {
                            "Local prebuilt GKI files will be deleted."
                        } else {
                            "This will delete the artifact cache for this workflow in ABK and remove locally downloaded files.\n\nWorkflow ${if (group.runNumber > 0) "#${group.runNumber}" else "#${group.runId}"}"
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
                            Text("Also delete the remote GitHub Actions workflow run")
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
                ) { Text("Delete") }
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
                    title = if (rootGranted) stringResource(R.string.flash_title) else "Files",
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
                                Text("Refresh build artifacts")
                            }
                        }

                        if (workflowGroups.isNotEmpty()) {
                            items(workflowGroups, key = { "workflow-${it.runId}" }) { group ->
                                WorkflowRunCard(
                                    group = group,
                                    onClick = {
                                        selectedRunId = group.runId
                                        selectedPrebuiltReleaseId = null
                                        navController.navigate(flashWorkflowRoute(group.runId))
                                    },
                                    onShowParameters = { parameterTarget = group },
                                    onDelete = {
                                        deleteWorkflowTarget = group
                                        deleteRemoteWorkflowRun = false
                                    }
                                )
                            }
                        } else {
                            item {
                                ExpressiveEmptyState(
                                    title = if (rootGranted) "No flashable artifacts" else "No files available",
                                    subtitle = if (rootGranted) {
                                        "After a successful build, ABK will sync and organize artifacts by workflow."
                                    } else {
                                        "After a successful build, you can download and view artifact files here."
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
                                        LoadingRow("Fetching releases…")
                                    }
                                }
                                state.prebuiltGkiReleases.isEmpty() -> {
                                    item {
                                        ExpressiveEmptyState(
                                            title = "No prebuilt GKI releases",
                                            subtitle = "No browsable releases found in this repository.",
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
                                    title = "Workflow record unavailable",
                                    subtitle = "This workflow artifact has been refreshed or deleted.",
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
                                        LoadingRow("Fetching prebuilt GKI for ${release.name}…")
                                    }
                                }
                                filteredPrebuiltAssets.isEmpty() -> {
                                    item {
                                        ExpressiveEmptyState(
                                            title = "No matching assets found",
                                            subtitle = if (prebuiltFilter.onlyMatches) {
                                                "No GKI, boot, img or AK3 assets match the current filter."
                                            } else {
                                                "No recognizable prebuilt GKI assets in this release."
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
                                    title = "Release unavailable",
                                    subtitle = "This prebuilt GKI release has been refreshed or deleted.",
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
        title = if (rootGranted) "Artifact Center" else "File Center",
        subtitle = if (rootGranted) {
            "Select a workflow first, then handle the kernel, manager, and modules."
        } else {
            "Partial activation mode: artifact download and file viewing only."
        },
        icon = if (rootGranted) Icons.Default.FlashOn else Icons.Default.FolderOpen,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        badge = {
            ExpressiveStatusChip(
                label = "$availableCount source artifacts",
                icon = Icons.Default.CloudDownload,
                color = MaterialTheme.colorScheme.tertiary
            )
            ExpressiveStatusChip(
                label = "$downloadedCount downloaded",
                icon = Icons.Default.Inventory2,
                color = MaterialTheme.colorScheme.secondary
            )
            ExpressiveStatusChip(
                label = when (buildStatus) {
                    BuildStatus.SUCCESS -> "Success"
                    BuildStatus.IN_PROGRESS -> "Building"
                    BuildStatus.QUEUED -> "Queued"
                    BuildStatus.FAILURE -> "Failed"
                    BuildStatus.CANCELLED -> "Cancelled"
                    BuildStatus.IDLE -> "Awaiting build"
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
        title = { Text("Parameter details") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ParameterSection("Workflow") {
                    ParameterRow("Number", if (group.runNumber > 0) "#${group.runNumber}" else "#${group.runId}")
                    ParameterRow("Title", group.runTitle)
                    ParameterRow("Artifacts", "${group.remote.size} source artifacts / ${group.local.size} downloaded")
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
                            Text("Reading build parameter summary…")
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
                            text = "No parameter details available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Off") }
        },
        dismissButton = if (error != null && !loading) {
            { TextButton(onClick = onRetry) { Text("Retry") } }
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
        title = { Text("Parameter details") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ParameterSection("Release") {
                    ParameterRow("Name", release.name)
                    ParameterRow("Tag", release.tagName)
                    ParameterRow("Published", releaseDateLabel(release.publishedAt))
                    ParameterRow("Assets", if (release.assetCount > 0) "${release.assetCount} assets" else "Unknown")
                }
                if (summary != null) {
                    ParameterSummarySections(summary)
                } else {
                    Text(
                        text = "No parseable parameter matrix in release body",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Off") }
        }
    )
}

@Composable
private fun ParameterSummarySections(summary: BuildParameterSummary) {
    ParameterSection("Version parameters") {
        ParameterRow("Android version", summary.androidVersion)
        ParameterRow("Kernel version", summary.kernelVersion)
        ParameterRow("Sub-level", summary.subLevel)
        ParameterRow("Patch level", summary.osPatchLevel)
        ParameterRow("Build time", summary.buildTime)
    }
    ParameterSection("KernelSU") {
        ParameterRow("KSU variant", summary.ksuVariant)
        ParameterRow("KSU branch", summary.ksuBranch)
        ParameterRow("SUSFS status", summary.susfsEnabled)
    }
    ParameterSection("Patches & Features") {
        ParameterRow("ZRAM enhancement", summary.zramEnabled)
        ParameterRow("ZRAM full algorithms", summary.zramFullAlgo)
        ParameterRow("ZRAM extra algorithms", summary.zramExtraAlgos)
        ParameterRow("BBG patch", summary.bbgEnabled)
        ParameterRow("DDK LSM", summary.ddkLsm)
        ParameterRow("NTsync patch", summary.ntsyncEnabled)
        ParameterRow("Networking", summary.networkingEnabled)
        ParameterRow("KPM feature", summary.kpmEnabled)
        ParameterRow("KPM password", summary.kpmPassword)
        ParameterRow("Re-Kernel", summary.reKernelEnabled)
        ParameterRow("Virtualization support", summary.virtualizationSupport)
        ParameterRow("Custom injection", summary.customInjection)
        ParameterRow("Stock Config", summary.stockConfig)
    }
    val extraRows = summary.extraRows.orEmpty()
    if (extraRows.isNotEmpty()) {
        ParameterSection("Extra info") {
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
        "" -> "Unknown"
        "true" -> "Enabled"
        "false" -> "Off"
        "none" -> "None"
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
            extraRows[label.trim()] = value.ifBlank { "None" }
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
        compact.contains("android版本") || compact.contains("androidversion") -> "androidVersion"
        compact.contains("Kernel version") -> "kernelVersion"
        compact.contains("Sub-level") -> "subLevel"
        compact.contains("Patch level") -> "osPatchLevel"
        compact.contains("ksu变体") || compact.contains("ksuvariant") -> "ksuVariant"
        compact.contains("ksu分支") || compact.contains("ksubranch") -> "ksuBranch"
        compact.contains("Build time") -> "buildTime"
        compact.contains("susfs状态") || compact.contains("susfsenabled") -> "susfsEnabled"
        compact.contains("zram增强") || compact.contains("zramenabled") -> "zramEnabled"
        compact.contains("zram完整算法") || compact.contains("zramfullalgo") -> "zramFullAlgo"
        compact.contains("zram额外算法") || compact.contains("zramextraalgos") -> "zramExtraAlgos"
        compact.contains("bbg补丁") || compact.contains("bbgenabled") -> "bbgEnabled"
        compact.contains("ddklsm") -> "ddkLsm"
        compact.contains("ntsync补丁") || compact.contains("ntsynced") -> "ntsyncEnabled"
        compact.contains("Networking") || compact.contains("networking增强") || compact.contains("networing增强") -> "networkingEnabled"
        compact.contains("kpm功能") || compact.contains("kpmenabled") -> "kpmEnabled"
        compact.contains("kpm密码") || compact.contains("kpmpassword") -> "kpmPassword"
        compact.contains("re-kernel") || compact.contains("rekernel") -> "reKernelEnabled"
        compact.contains("Virtualization support") -> "virtualizationSupport"
        compact == "Custom injection" -> "customInjection"
        compact.contains("stockconfig") -> "stockConfig"
        else -> null
    }
}

private fun sanitizeReleaseParameterValue(key: String, value: String): String {
    if (key != "kpmPassword") return value.ifBlank { "None" }
    val normalized = value.trim().lowercase()
    return when {
        normalized.isBlank() -> "Default"
        normalized in setOf("默认", "default", "None", "none", "not set", "Default") -> "Default"
        else -> "Set"
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
    "Custom injection parameter list",
    "Networking (IPSet + BBR)",
    "Release asset count",
    "5.10 revision",
    "Custom version name",
    "OnePlus 8E support",
    "Android version",
    "Stock Config",
    "ZRAM full algorithms",
    "ZRAM extra algorithms",
    "NTsync patch",
    "Virtualization support",
    "Custom injection",
    "Kernel version",
    "Sub-level",
    "Patch level",
    "KSU variant",
    "KSU branch",
    "Build time",
    "SUSFS status",
    "ZRAM enhancement",
    "BBG patch",
    "DDK LSM",
    "Networking",
    "KPM feature",
    "KPM password",
    "Re-Kernel",
    "Artifact count",
    "Source commit",
    "Source run"
).sortedByDescending { it.length }

private val RELEASE_EXTRA_PARAMETER_LABELS = setOf(
    "sourcerun",
    "sourcecommit",
    "artifactcount",
    "releaseassetcount",
    "Custom version name",
    "5.10revision",
    "oneplus8esupport",
    "Custom injection parameter list"
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
            Text("Prebuilt GKI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "$releaseCount release(s) · Enter to filter assets",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(onClick = onRefresh, enabled = !isLoading) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Refresh")
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
                    label = if (release.assetCount > 0) "${release.assetCount} assets" else "Tap to load assets",
                    color = MaterialTheme.colorScheme.primary
                )
                ExpressiveStatusChip(label = "Manual download", color = MaterialTheme.colorScheme.secondary)
                ExpressiveStatusChip(label = "Filter per release", color = MaterialTheme.colorScheme.tertiary)
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
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "$visibleCount / $sourceCount visible assets",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onShowParameters) {
                Icon(Icons.Default.Tune, contentDescription = "Parameter details")
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh assets")
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
        title = "Filter",
        subtitle = "Unset conditions are treated as unrestricted",
        icon = Icons.Default.Tune
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrebuiltDropdownField(
                label = "Android version",
                value = filter.androidVersion,
                options = androidOptions,
                onSelect = { updateFilter(filter.copy(androidVersion = it)) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrebuiltDropdownField(
                    label = "Kernel version",
                    value = filter.kernelVersion,
                    options = kernelOptions,
                    onSelect = { updateFilter(filter.copy(kernelVersion = it)) },
                    modifier = Modifier.weight(1f)
                )
                PrebuiltDropdownField(
                    label = "Sub-level",
                    value = filter.subLevel,
                    options = subLevelOptions,
                    onSelect = { updateFilter(filter.copy(subLevel = it)) },
                    modifier = Modifier.weight(1f)
                )
            }
            PrebuiltDropdownField(
                label = "Patch level",
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
                Text("Show only assets matching current filter")
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
                chip = if (recommended) "Device recommended" else "Release"
            )

            when {
                progress != null -> {
                    LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth())
                    Text("Downloading $progress%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                downloadedFiles.isEmpty() -> {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Download prebuilt GKI")
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
    onClick: () -> Unit,
    onShowParameters: () -> Unit,
    onDelete: () -> Unit
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
                            "Prebuilt GKI"
                        } else {
                            "Workflow ${if (group.runNumber > 0) "#${group.runNumber}" else "#${group.runId}"}"
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
                    Icon(Icons.Default.Tune, contentDescription = "Parameter details")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete workflow")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ExpressiveStatusChip(label = "$sourceCount source artifact(s)", color = MaterialTheme.colorScheme.primary)
                ExpressiveStatusChip(label = "$downloadedCount downloaded", color = MaterialTheme.colorScheme.secondary)
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
            "Prebuilt GKI"
        } else {
            "Workflow ${if (group.runNumber > 0) "#${group.runNumber}" else "#${group.runId}"}"
        },
        subtitle = group.runTitle,
        icon = Icons.Default.FolderSpecial
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "${group.remote.size} source artifacts / ${group.local.size} downloaded",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onShowParameters) {
                Icon(Icons.Default.Tune, contentDescription = "Parameter details")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete workflow")
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
                chip = if (autoDownloadEligible) "Auto next" else artifactTypeLabel(type)
            )

            when {
                progress != null -> {
                    LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth())
                    Text("Downloading $progress%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                downloadedFiles.isEmpty() -> {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Download")
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
                chip = "Local file"
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
                    contentDescription = "Delete file",
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
                Text("Copy path")
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
                        Text("Install")
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
        title = { Text(if (running) "Running: $title" else title) },
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
                TextButton(onClick = {}, enabled = false) { Text("Running") }
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
                            Text("Reboot")
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
                ?: "Workflow not linked",
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

private fun artifactIcon(type: ArtifactType) = when (type) {
    ArtifactType.KERNEL_PACKAGE -> Icons.Default.Inventory2
    ArtifactType.KERNEL_IMG -> Icons.Default.Memory
    ArtifactType.ANYKERNEL3 -> Icons.Default.Archive
    ArtifactType.KSU_MANAGER -> Icons.Default.Shield
    ArtifactType.SUSFS_MODULE -> Icons.Default.Extension
    ArtifactType.OTHER -> Icons.Default.InsertDriveFile
}

private fun artifactTypeLabel(type: ArtifactType) = when (type) {
    ArtifactType.KERNEL_PACKAGE -> "Kernel build package"
    ArtifactType.KERNEL_IMG -> "Kernel image"
    ArtifactType.ANYKERNEL3 -> "AnyKernel3 flash package"
    ArtifactType.KSU_MANAGER -> "KernelSU manager"
    ArtifactType.SUSFS_MODULE -> "SUSFS module"
    ArtifactType.OTHER -> "Other file"
}

private fun flashButtonLabel(type: ArtifactType) = when (type) {
    ArtifactType.KERNEL_IMG -> "Flash"
    ArtifactType.ANYKERNEL3 -> "Flash AK3"
    ArtifactType.SUSFS_MODULE -> "Install module"
    else -> "Execute"
}

private fun flashOperationLabel(type: ArtifactType) = when (type) {
    ArtifactType.KERNEL_IMG -> "Flash boot image"
    ArtifactType.ANYKERNEL3 -> "Flash AnyKernel3"
    ArtifactType.SUSFS_MODULE -> "Install module"
    else -> "Execute"
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
    Workflows("Build artifacts"),
    PrebuiltGki("Prebuilt GKI")
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

private fun prebuiltOptionLabel(value: String): String = value.ifBlank { "Any" }

private fun patchMonthIndexForUi(value: String): Int {
    val parts = value.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return Int.MAX_VALUE
    val month = parts.getOrNull(1)?.toIntOrNull() ?: return Int.MAX_VALUE
    return year * 12 + month
}

private fun releaseDateLabel(value: String): String =
    value.takeIf { it.length >= 10 }?.take(10) ?: "Unknown date"

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
    ArtifactCategory.KERNEL -> "Kernel artifacts"
    ArtifactCategory.MANAGER -> "Manager"
    ArtifactCategory.MODULE -> "Module"
}

private fun ArtifactCategory.icon(): ImageVector = when (this) {
    ArtifactCategory.KERNEL -> Icons.Default.Memory
    ArtifactCategory.MANAGER -> Icons.Default.Shield
    ArtifactCategory.MODULE -> Icons.Default.Extension
}
