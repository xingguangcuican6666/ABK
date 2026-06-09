package com.abk.kernel.ui.screens.miuix

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.R
import com.abk.kernel.data.model.*
import com.abk.kernel.ui.screens.flash.*
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.FailureLogExtractor
import com.abk.kernel.utils.FlashWorkflowFilter
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowDialog
import kotlin.time.Duration.Companion.milliseconds

// ═══════════════════════════════════════════════════════════════════════════
// Main Detail Screen — orchestrates building / completed / failed states
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun MiuixFlashDetailScreen(vm: MainViewModel, runId: Long, onClose: () -> Unit) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── Load workflow data ──
    val remoteArtifacts = remember(state.artifacts, runId) {
        state.artifacts.filter { it.runId == runId && !it.expired &&
            DownloadUtils.classifyCategory(DownloadUtils.classifyArtifact(it.name)) != null }
    }
    val localArtifacts = remember(state.downloadedArtifacts, runId) {
        state.downloadedArtifacts.filter { it.runId == runId && it.runId != PREBUILT_GKI_RUN_ID }
    }

    val recentRun = remember(state.recentRuns, runId) { state.recentRuns.firstOrNull { it.id == runId } }
    val sessionGhost = remember(state.sessionGhostFailedRuns, runId) { state.sessionGhostFailedRuns[runId] }
    val ghostRun = sessionGhost ?: recentRun

    // Build WorkflowArtifactGroup for category-based display
    val group = remember(remoteArtifacts, localArtifacts, recentRun) {
        val allRemote = remoteArtifacts.filterNot {
            shouldHideManagerCertArtifact(recentRun?.displayTitle ?: recentRun?.name.orEmpty(), it.name) ||
                shouldHideAbkManagerArtifact(it.name)
        }
        val allLocal = localArtifacts.filterNot {
            shouldHideManagerCertArtifact(recentRun?.displayTitle ?: recentRun?.name.orEmpty(), it.name) ||
                it.type == ArtifactType.ABK_MANAGER
        }
        val runTitle = recentRun?.displayTitle?.ifBlank { null }
            ?: recentRun?.name?.ifBlank { null }
            ?: allRemote.firstOrNull()?.runTitle?.ifBlank { null }
            ?: allLocal.firstOrNull()?.runTitle?.ifBlank { null }
            ?: "#$runId"

        val remoteTypes = allRemote.map { DownloadUtils.classifyArtifact(it.name) }
        val remoteCategories = remoteTypes.mapNotNull(DownloadUtils::classifyCategory).toSet()
        val localCategories = allLocal.map { it.category }.toSet()

        WorkflowArtifactGroup(
            runId = runId,
            runTitle = runTitle,
            runNumber = allRemote.firstOrNull()?.runNumber ?: allLocal.firstOrNull()?.runNumber ?: 0,
            runCreatedAt = recentRun?.createdAt ?: allRemote.firstOrNull()?.runCreatedAt ?: "",
            runUpdatedAt = recentRun?.updatedAt ?: "",
            remote = allRemote,
            local = allLocal,
            categories = remoteCategories + localCategories,
            cachedHasRemoteManagerArtifact = remoteTypes.any { it.isManagerArtifactType() },
            cachedHasManagerArtifact = remoteTypes.any { it.isManagerArtifactType() } ||
                allLocal.any { it.type.isManagerArtifactType() },
            cachedHasKernelArtifact = remoteTypes.any {
                it == ArtifactType.KERNEL_PACKAGE || it == ArtifactType.KERNEL_IMG || it == ArtifactType.ANYKERNEL3
            } || allLocal.any { it.type in setOf(ArtifactType.KERNEL_PACKAGE, ArtifactType.KERNEL_IMG, ArtifactType.ANYKERNEL3) },
            cachedHasRemoteKernelArtifact = remoteTypes.any {
                it == ArtifactType.KERNEL_PACKAGE || it == ArtifactType.KERNEL_IMG || it == ArtifactType.ANYKERNEL3
            },
            cachedHasSusfsModuleArtifact = remoteTypes.any { it == ArtifactType.SUSFS_MODULE } ||
                allLocal.any { it.type == ArtifactType.SUSFS_MODULE }
        )
    }

    val isActive = recentRun?.isActive() == true
    val isFailed = ghostRun != null && ghostRun.id == runId &&
        ghostRun.conclusion in setOf("failure", "cancelled", "timed_out")
    val isCancelling = runId in state.cancellingWorkflowRunIds
    val showBuilding = (isActive || (isCancelling && group.remote.isEmpty())) && !isFailed
    val rootGranted = state.rootGranted

    // ── Terminal dialog state ──
    var terminalState by remember { mutableStateOf<MiuixTerminalState?>(null) }

    // ── Failed workflow detail state ──
    LaunchedEffect(runId, isFailed) {
        if (isFailed) {
            vm.loadWorkflowJobs(runId)
            vm.loadFailedRunLogExcerpt(runId)
        }
    }

    // ── Poll artifacts while building ──
    LaunchedEffect(runId, showBuilding) {
        if (!showBuilding) return@LaunchedEffect
        vm.refreshWorkflowArtifacts(runId)
        while (true) {
            val burstActive = vm.isWorkflowStatusBurstActive(runId)
            delay((if (burstActive) 3_000L else 20_000L).milliseconds)
            if (state.recentRuns.firstOrNull { it.id == runId }?.isActive() != true) break
            if (!burstActive) vm.refreshWorkflowArtifacts(runId)
        }
    }

    // ── Determine which mode to show ──
    when {
        isFailed -> {
            MiuixFailedWorkflowDetail(
                run = ghostRun,
                jobs = state.workflowJobsByRunId[runId],
                jobsLoading = runId in state.workflowJobsLoading,
                jobsError = state.workflowJobsErrors[runId],
                logExcerpt = state.failedRunLogExcerpts[runId],
                logLoading = runId in state.failedRunLogLoading,
                onBack = onClose,
                onOpenGitHub = { openGithubRun(context, ghostRun.htmlUrl) },
                onRetryJobs = { vm.loadWorkflowJobs(runId, force = true) }
            )
        }
        showBuilding && recentRun != null -> {
            MiuixBuildingWorkflowDetail(
                run = recentRun,
                group = group,
                progress = state.buildProgressByRunId[runId],
                cancelling = isCancelling,
                downloadProgress = state.downloadProgress,
                autoDownload = state.autoDownload,
                pendingAutoDownloadRunId = state.pendingAutoDownloadRunId,
                onDownload = vm::downloadArtifact,
                onCopyPath = { copyPath(context, it) },
                onInstall = { installManagerApk(context, scope, it, rootGranted) { terminalState = it } },
                onFlash = { item, slot -> flashItem(context, scope, item, rootGranted, slot) { terminalState = it } },
                onDelete = { vm.deleteDownloadedArtifact(it.filePath) },
                allowRootActions = rootGranted,
                onBack = onClose,
                onCancel = { vm.cancelWorkflowRun(runId) },
                onCancelDownload = vm::cancelDownload,
                onCancelAutoDownload = vm::cancelAutoDownloads
            )
        }
        else -> {
            // Completed / idle workflow detail
            MiuixCompletedWorkflowDetail(
                group = group,
                downloadProgress = state.downloadProgress,
                autoDownload = state.autoDownload,
                pendingAutoDownloadRunId = state.pendingAutoDownloadRunId,
                onDownload = vm::downloadArtifact,
                onCancelDownload = vm::cancelDownload,
                onCancelAutoDownload = vm::cancelAutoDownloads,
                onCopyPath = { copyPath(context, it) },
                onInstall = { installManagerApk(context, scope, it, rootGranted) { terminalState = it } },
                onFlash = { item, slot -> flashItem(context, scope, item, rootGranted, slot) { terminalState = it } },
                onDelete = { vm.deleteDownloadedArtifact(it.filePath) },
                allowRootActions = rootGranted,
                onBack = onClose,
                onDeleteWorkflow = {
                    vm.deleteWorkflowArtifacts(runId, false)
                    onClose()
                },
                onShowParameters = {
                    vm.loadBuildParameterSummary(runId)
                },
                parameterSummary = state.buildParameterSummaries[runId],
                parameterLoading = runId in state.loadingBuildParameterRunIds,
                parameterError = state.buildParameterErrors[runId],
                recentRun = recentRun,
                onRefreshArtifacts = { vm.refreshWorkflowArtifacts(runId, force = true) }
            )
        }
    }

    // ── Terminal dialog ──
    terminalState?.let { ts ->
        if (!ts.closed) {
            MiuixTerminalDialog(
                state = ts,
                onClose = { ts.closed = true; terminalState = null },
                onReboot = { ts.closed = true; terminalState = null; scope.launch(Dispatchers.IO) { RootUtils.reboot() } }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Completed Workflow Detail
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun MiuixCompletedWorkflowDetail(
    group: WorkflowArtifactGroup,
    downloadProgress: Map<Long, Int>,
    autoDownload: Boolean,
    pendingAutoDownloadRunId: Long?,
    onDownload: (BuildArtifact) -> Unit,
    onCancelDownload: (Long) -> Unit,
    onCancelAutoDownload: (Long) -> Unit,
    onCopyPath: (DownloadedArtifact) -> Unit,
    onInstall: (DownloadedArtifact) -> Unit,
    onFlash: (DownloadedArtifact, RootUtils.Ak3SlotTarget) -> Unit,
    onDelete: (DownloadedArtifact) -> Unit,
    allowRootActions: Boolean,
    onBack: () -> Unit,
    onDeleteWorkflow: () -> Unit,
    onShowParameters: () -> Unit,
    parameterSummary: BuildParameterSummary?,
    parameterLoading: Boolean,
    parameterError: String?,
    recentRun: WorkflowRun?,
    onRefreshArtifacts: () -> Unit
) {
    val isManagerPrimary = FlashWorkflowFilter.primaryKind(
        run = recentRun,
        runTitle = group.runTitle,
        hasKernelArtifact = group.hasKernelArtifact(),
        hasManagerArtifact = group.hasManagerArtifact()
    ) == com.abk.kernel.utils.WorkflowPrimary.Manager

    val visibleCategories = if (isManagerPrimary) {
        listOf(ArtifactCategory.MANAGER)
    } else {
        artifactCategoryOrder
    }

    val runCreatedAt = recentRun?.createdAt?.takeIf { it.isNotBlank() } ?: group.runCreatedAt
    val runFinishedAt = recentRun?.updatedAt?.takeIf { it.isNotBlank() } ?: group.runUpdatedAt

    val showParams = group.shouldShowParameterDetails(recentRun)

    var showParameterDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topBarState)

    Scaffold(
        topBar = {
            TopAppBar(
                title = if (group.runNumber > 0) "#${group.runNumber}" else "#${group.runId}",
                largeTitle = if (group.runNumber > 0) "#${group.runNumber}" else "#${group.runId}",
                scrollBehavior = scrollBehavior,
                navigationIcon = { IconButton(onClick = onBack) { Icon(MiuixIcons.Back, null) } },
                actions = {
                    if (showParams) {
                        IconButton(onClick = {
                            onShowParameters()
                            showParameterDialog = true
                        }) { Icon(Icons.Filled.Tune, null, tint = colorScheme.onSurfaceVariantActions) }
                    }
                    IconButton(onClick = { onRefreshArtifacts }) {
                        Icon(Icons.Filled.Refresh, null, tint = colorScheme.onSurfaceVariantActions)
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, null, tint = colorScheme.onSurfaceVariantActions)
                    }
                }
            )
        },
        containerColor = colorScheme.surface
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp).nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Header card ──
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.FolderSpecial, null, tint = colorScheme.primary, modifier = Modifier.size(22.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = group.runTitle.ifBlank {
                                        if (group.runNumber > 0) "#${group.runNumber}" else "#${group.runId}"
                                    },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(R.string.flash_artifact_counts, group.remote.size, group.local.size),
                                    fontSize = 13.sp,
                                    color = colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (group.runCreatedAt.isNotBlank()) {
                                MiuixTagChip(group.runCreatedAt.take(10), colorScheme.primary)
                            }
                            MiuixTagChip(stringResource(R.string.flash_source_artifacts_count, group.remote.size), colorScheme.primary)
                            MiuixTagChip(stringResource(R.string.flash_downloaded_count, group.local.size), colorScheme.primary.copy(alpha = 0.7f))
                            group.categories.forEach { cat ->
                                MiuixTagChip(stringResource(cat.labelRes()), colorScheme.primary.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }

            // ── Category sections ──
            val elapsedAnchorCategory = visibleCategories.firstOrNull { cat ->
                group.hasArtifactsInCategory(cat)
            } ?: visibleCategories.first()

            visibleCategories.forEach { category ->
                val remoteInCategory = group.remote.filter {
                    DownloadUtils.classifyCategory(DownloadUtils.classifyArtifact(it.name)) == category
                }
                val matchedLocalPaths = remoteInCategory
                    .flatMap { src -> group.local.filter { DownloadUtils.matchesDownloadedArtifact(it, src) } }
                    .map { it.filePath }.toSet()
                val localOnly = group.local.filter {
                    it.category == category && it.filePath !in matchedLocalPaths
                }

                if (remoteInCategory.isNotEmpty() || localOnly.isNotEmpty()) {
                    item("cat-${group.runId}-${category.name}") {
                        MiuixCategorySection(
                            category = category,
                            showDuration = category == elapsedAnchorCategory,
                            createdAt = runCreatedAt,
                            finishedAt = runFinishedAt,
                            liveDuration = false,
                            group = group,
                            downloadProgress = downloadProgress,
                            autoDownload = autoDownload,
                            pendingAutoDownloadRunId = pendingAutoDownloadRunId,
                            onDownload = onDownload,
                            onCancelDownload = onCancelDownload,
                            onCancelAutoDownload = onCancelAutoDownload,
                            showDownloadCancelActions = true,
                            onCopyPath = onCopyPath,
                            onInstall = onInstall,
                            onFlash = onFlash,
                            onDelete = onDelete,
                            allowRootActions = allowRootActions
                        )
                    }
                }
            }

            // Empty state
            if (group.remote.isEmpty() && group.local.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Inbox, null, modifier = Modifier.size(48.dp), tint = colorScheme.onSurfaceVariantActions)
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.flash_workflow_unavailable), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.flash_workflow_unavailable_desc), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                        }
                    }
                }
            }
        }
    }

    // ── Dialogs ──
    if (showDeleteConfirm) {
        WindowDialog(
            title = stringResource(R.string.flash_delete_workflow_record),
            show = true,
            onDismissRequest = { showDeleteConfirm = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.flash_delete_workflow_msg,
                        if (group.runNumber > 0) "#${group.runNumber}" else "#${group.runId}"),
                    fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = stringResource(R.string.cancel), onClick = { showDeleteConfirm = false }, modifier = Modifier.weight(1f))
                Button(
                    onClick = { onDeleteWorkflow(); showDeleteConfirm = false },
                    colors = ButtonDefaults.buttonColors(color = colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.delete)) }
            }
        }
    }

    if (showParameterDialog) {
        ParameterDialog(
            group = group,
            summary = parameterSummary,
            loading = parameterLoading,
            error = parameterError,
            onDismiss = { showParameterDialog = false },
            onRetry = onShowParameters
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Building Workflow Detail
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun MiuixBuildingWorkflowDetail(
    run: WorkflowRun,
    group: WorkflowArtifactGroup,
    progress: BuildProgress?,
    cancelling: Boolean,
    downloadProgress: Map<Long, Int>,
    autoDownload: Boolean,
    pendingAutoDownloadRunId: Long?,
    onDownload: (BuildArtifact) -> Unit,
    onCopyPath: (DownloadedArtifact) -> Unit,
    onInstall: (DownloadedArtifact) -> Unit,
    onFlash: (DownloadedArtifact, RootUtils.Ak3SlotTarget) -> Unit,
    onDelete: (DownloadedArtifact) -> Unit,
    allowRootActions: Boolean,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onCancelDownload: (Long) -> Unit,
    onCancelAutoDownload: (Long) -> Unit
) {
    var showCancelConfirm by remember { mutableStateOf(false) }
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topBarState)
    val isPureManager = FlashWorkflowFilter.isPureManagerBuild(run)
    val visibleCategories = if (isPureManager) {
        listOf(ArtifactCategory.MANAGER)
    } else {
        artifactCategoryOrder
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = if (run.runNumber > 0) "#${run.runNumber}" else "#${run.id}",
                largeTitle = if (run.runNumber > 0) "#${run.runNumber}" else "#${run.id}",
                scrollBehavior = scrollBehavior,
                navigationIcon = { IconButton(onClick = onBack) { Icon(MiuixIcons.Back, null) } }
            )
        },
        containerColor = colorScheme.surface
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp).nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Header ──
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = run.displayTitle ?: run.name ?: "#${run.id}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (cancelling) stringResource(R.string.flash_cancelling_subtitle)
                                    else stringResource(R.string.flash_building_subtitle),
                                    fontSize = 13.sp,
                                    color = colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }
                        // Live duration chip
                        MiuixDurationChip(createdAt = run.createdAt, live = true)
                    }
                }
            }

            // ── Category sections ──
            visibleCategories.forEach { category ->
                item("cat-build-${category.name}") {
                    MiuixCategorySection(
                        category = category,
                        showDuration = false,
                        createdAt = run.createdAt,
                        liveDuration = true,
                        group = group,
                        downloadProgress = downloadProgress,
                        autoDownload = autoDownload,
                        pendingAutoDownloadRunId = pendingAutoDownloadRunId,
                        onDownload = onDownload,
                        onCancelDownload = onCancelDownload,
                        onCancelAutoDownload = onCancelAutoDownload,
                        showDownloadCancelActions = true,
                        onCopyPath = onCopyPath,
                        onInstall = onInstall,
                        onFlash = onFlash,
                        onDelete = onDelete,
                        allowRootActions = allowRootActions,
                        progress = progress
                    )
                }
            }

            // ── Cancel button ──
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { showCancelConfirm = true },
                    enabled = !cancelling,
                    colors = ButtonDefaults.buttonColors(color = colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (cancelling) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Cancel, null, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.flash_cancel_build), fontSize = 15.sp)
                }
            }
        }
    }

    if (showCancelConfirm) {
        WindowDialog(
            title = stringResource(R.string.flash_cancel_confirm_title),
            show = true,
            onDismissRequest = { showCancelConfirm = false }
        ) {
            Text(stringResource(R.string.flash_cancel_confirm_msg), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = stringResource(R.string.cancel), onClick = { showCancelConfirm = false }, modifier = Modifier.weight(1f))
                Button(
                    onClick = { onCancel(); showCancelConfirm = false },
                    colors = ButtonDefaults.buttonColors(color = colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.flash_cancel_confirm_yes)) }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Failed Workflow Detail
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun MiuixFailedWorkflowDetail(
    run: WorkflowRun,
    jobs: List<WorkflowJob>?,
    jobsLoading: Boolean,
    jobsError: String?,
    logExcerpt: String?,
    logLoading: Boolean,
    onBack: () -> Unit,
    onOpenGitHub: () -> Unit,
    onRetryJobs: () -> Unit
) {
    val steps = remember(jobs) { jobs?.let { flattenFailedWorkflowSteps(it) }.orEmpty() }
    val failureIndex = remember(steps) { steps.indexOfFirst { it.conclusion == "failure" } }
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topBarState)

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.flash_workflow_label,
                    if (run.runNumber > 0) "#${run.runNumber}" else "#${run.id}"),
                largeTitle = stringResource(R.string.flash_workflow_label,
                    if (run.runNumber > 0) "#${run.runNumber}" else "#${run.id}"),
                scrollBehavior = scrollBehavior,
                navigationIcon = { IconButton(onClick = onBack) { Icon(MiuixIcons.Back, null) } }
            )
        },
        containerColor = colorScheme.surface
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp).nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Header ──
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.Error, null, tint = colorScheme.error, modifier = Modifier.size(22.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = run.displayTitle ?: run.name ?: "#${run.id}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(R.string.flash_failed_subtitle),
                                    fontSize = 13.sp,
                                    color = colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }
                        // Failure chip
                        Box(Modifier.background(colorScheme.error.copy(alpha = 0.12f), RoundedCornerShape(5.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(stringResource(R.string.flash_conclusion_failure), fontSize = 12.sp, color = colorScheme.error)
                        }
                    }
                }
            }

            // ── Failed steps ──
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.RunCircle, null, tint = colorScheme.onSurface, modifier = Modifier.size(20.dp))
                            Text(stringResource(R.string.flash_failed_step_list_title), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                        }
                        when {
                            jobsLoading -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text(stringResource(R.string.flash_loading_steps), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                                }
                            }
                            jobsError != null -> {
                                Text(jobsError, fontSize = 14.sp, color = colorScheme.error)
                                TextButton(text = stringResource(R.string.retry), onClick = onRetryJobs, modifier = Modifier.fillMaxWidth())
                            }
                            steps.isEmpty() -> {
                                Text(stringResource(R.string.flash_failed_subtitle), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                            }
                            else -> {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    steps.forEachIndexed { index, step ->
                                        MiuixFailedStepRow(
                                            step = step,
                                            failed = index == failureIndex,
                                            muted = failureIndex in 0..<index
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Error log ──
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.Terminal, null, tint = colorScheme.onSurface, modifier = Modifier.size(20.dp))
                            Text(stringResource(R.string.flash_build_error), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                        }
                        when {
                            logLoading -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text(stringResource(R.string.flash_loading_steps), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                                }
                            }
                            else -> {
                                val text = logExcerpt?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.flash_build_error_unavailable)
                                Box(
                                    Modifier.fillMaxWidth()
                                        .heightIn(max = 320.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colorScheme.surfaceContainer)
                                        .padding(12.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    SelectionContainer {
                                        Text(
                                            text = FailureLogExtractor.sanitizeForDisplay(text),
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = colorScheme.onSurfaceVariantSummary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Open GitHub button ──
            item {
                Button(
                    onClick = onOpenGitHub,
                    colors = ButtonDefaults.buttonColors(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.RunCircle, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.flash_open_github_actions))
                }
            }
        }
    }
}

@Composable
private fun MiuixFailedStepRow(step: WorkflowStep, failed: Boolean, muted: Boolean) {
    val alpha = if (muted) 0.45f else 1f
    val bg = if (failed) colorScheme.error.copy(alpha = 0.08f) else colorScheme.surfaceContainer
    Row(
        Modifier.fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(8.dp))
            .background(bg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (failed) {
            Box(Modifier.width(4.dp).height(32.dp).background(colorScheme.error))
        }
        Row(
            Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = when {
                    failed -> Icons.Filled.Cancel
                    step.conclusion == "success" || step.status == "completed" -> Icons.Filled.CheckCircle
                    else -> Icons.Filled.Schedule
                },
                contentDescription = null,
                tint = when {
                    failed -> colorScheme.error
                    step.conclusion == "success" || step.status == "completed" -> colorScheme.onSurfaceVariantActions
                    else -> colorScheme.onSurfaceVariantActions
                },
                modifier = Modifier.size(16.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    step.name,
                    fontSize = 13.sp,
                    fontWeight = if (failed) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (failed) colorScheme.onError else colorScheme.onSurface
                )
                if (failed && !step.conclusion.isNullOrBlank()) {
                    Text(
                        step.conclusion,
                        fontSize = 11.sp,
                        color = colorScheme.error
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Category Section
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun MiuixCategorySection(
    category: ArtifactCategory,
    showDuration: Boolean,
    createdAt: String,
    finishedAt: String? = null,
    liveDuration: Boolean,
    group: WorkflowArtifactGroup,
    downloadProgress: Map<Long, Int>,
    autoDownload: Boolean,
    pendingAutoDownloadRunId: Long?,
    onDownload: (BuildArtifact) -> Unit,
    onCancelDownload: (Long) -> Unit = {},
    onCancelAutoDownload: (Long) -> Unit = {},
    showDownloadCancelActions: Boolean = false,
    onCopyPath: (DownloadedArtifact) -> Unit,
    onInstall: (DownloadedArtifact) -> Unit,
    onFlash: (DownloadedArtifact, RootUtils.Ak3SlotTarget) -> Unit,
    onDelete: (DownloadedArtifact) -> Unit,
    allowRootActions: Boolean,
    progress: BuildProgress? = null
) {
    val remoteInCategory = group.remote.filter {
        DownloadUtils.classifyCategory(DownloadUtils.classifyArtifact(it.name)) == category
    }
    val matchedLocalPaths = remoteInCategory
        .flatMap { src -> group.local.filter { DownloadUtils.matchesDownloadedArtifact(it, src) } }
        .map { it.filePath }.toSet()
    val localOnly = group.local.filter { it.category == category && it.filePath !in matchedLocalPaths }
    val hasArtifacts = remoteInCategory.isNotEmpty() || localOnly.isNotEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // ── Category header with optional duration ──
        if (hasArtifacts || liveDuration) {
            if (showDuration) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MiuixCategoryHeader(category)
                    Spacer(Modifier.weight(1f))
                    MiuixDurationChip(createdAt = createdAt, finishedAt = finishedAt, live = liveDuration)
                }
            } else {
                MiuixCategoryHeader(category)
            }
        }

        // ── Artifact cards ──
        if (hasArtifacts) {
            remoteInCategory.forEach { artifact ->
                val downloadedFiles = group.local.filter {
                    DownloadUtils.matchesDownloadedArtifact(it, artifact)
                }
                MiuixArtifactSourceCard(
                    artifact = artifact,
                    downloadedFiles = downloadedFiles,
                    progress = downloadProgress[artifact.id],
                    autoDownloadEligible = autoDownload &&
                        pendingAutoDownloadRunId == artifact.runId &&
                        DownloadUtils.shouldAutoDownload(artifact),
                    pendingAutoDownload = pendingAutoDownloadRunId == artifact.runId,
                    showDownloadCancelActions = showDownloadCancelActions,
                    onDownload = { onDownload(artifact) },
                    onCancelDownload = if (showDownloadCancelActions) {
                        { onCancelDownload(artifact.id) }
                    } else null,
                    onCancelAutoDownload = if (showDownloadCancelActions) {
                        { onCancelAutoDownload(artifact.runId) }
                    } else null,
                    onCopyPath = onCopyPath,
                    onInstall = onInstall,
                    onFlash = onFlash,
                    onDelete = onDelete,
                    allowRootActions = allowRootActions
                )
            }
            localOnly.forEach { artifact ->
                MiuixLocalOnlyArtifactCard(
                    artifact = artifact,
                    onCopyPath = onCopyPath,
                    onInstall = onInstall,
                    onFlash = onFlash,
                    onDelete = onDelete,
                    allowRootActions = allowRootActions
                )
            }
        } else {
            // Building progress card
            MiuixCategoryProgressCard(progress = progress)
        }
    }
}

@Composable
private fun MiuixCategoryHeader(category: ArtifactCategory) {
    Row(
        Modifier.padding(start = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(category.icon(), null, tint = colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(
            stringResource(category.labelRes()),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurfaceVariantActions
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Artifact Cards
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun MiuixArtifactSourceCard(
    artifact: BuildArtifact,
    downloadedFiles: List<DownloadedArtifact>,
    progress: Int?,
    autoDownloadEligible: Boolean,
    pendingAutoDownload: Boolean,
    showDownloadCancelActions: Boolean,
    onDownload: () -> Unit,
    onCancelDownload: (() -> Unit)?,
    onCancelAutoDownload: (() -> Unit)?,
    onCopyPath: (DownloadedArtifact) -> Unit,
    onInstall: (DownloadedArtifact) -> Unit,
    onFlash: (DownloadedArtifact, RootUtils.Ak3SlotTarget) -> Unit,
    onDelete: (DownloadedArtifact) -> Unit,
    allowRootActions: Boolean
) {
    val type = DownloadUtils.classifyArtifact(artifact.name)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(artifactTypeIcon(type), null, tint = colorScheme.primary, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(artifact.name.ifBlank { stringResource(R.string.flash_unknown_artifact) },
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${stringResource(artifactTypeLabelRes(type))} · ${DownloadUtils.formatSize(artifact.sizeInBytes)}",
                        fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary
                    )
                }
                MiuixTagChip(
                    if (autoDownloadEligible) stringResource(R.string.flash_auto_next)
                    else stringResource(artifactTypeLabelRes(type)),
                    colorScheme.primary
                )
            }

            // Action area
            when {
                progress != null -> {
                    // Downloading
                    val animatedProgress by animateFloatAsState(
                        targetValue = (progress / 100f).coerceIn(0f, 1f), label = "dl-progress"
                    )
                    LinearProgressIndicator(progress = animatedProgress, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.flash_download_progress, progress), fontSize = 11.sp, color = colorScheme.onSurfaceVariantSummary)
                    if (showDownloadCancelActions && onCancelDownload != null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = onCancelDownload) {
                                Icon(MiuixIcons.Pause, stringResource(R.string.flash_cancel_download), tint = colorScheme.error)
                            }
                        }
                    }
                }
                downloadedFiles.isEmpty() -> {
                    if (pendingAutoDownload && autoDownloadEligible) {
                        // Waiting for auto-download
                        Text(stringResource(R.string.flash_download_waiting_auto), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                        if (showDownloadCancelActions && onCancelAutoDownload != null) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = onCancelAutoDownload) {
                                    Icon(MiuixIcons.Pause, stringResource(R.string.flash_stop_auto_download), tint = colorScheme.error)
                                }
                            }
                        }
                    } else {
                        // Download button
                        Button(
                            onClick = onDownload,
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.flash_download), fontSize = 13.sp)
                        }
                    }
                }
                else -> {
                    // Downloaded files
                    downloadedFiles.forEachIndexed { index, file ->
                        if (index > 0) HorizontalDivider()
                        MiuixDownloadedOutputRow(
                            artifact = file,
                            onCopyPath = { onCopyPath(file) },
                            onInstall = { onInstall(file) },
                            onFlashWithSlot = { slot -> onFlash(file, slot) },
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
private fun MiuixLocalOnlyArtifactCard(
    artifact: DownloadedArtifact,
    onCopyPath: (DownloadedArtifact) -> Unit,
    onInstall: (DownloadedArtifact) -> Unit,
    onFlash: (DownloadedArtifact, RootUtils.Ak3SlotTarget) -> Unit,
    onDelete: (DownloadedArtifact) -> Unit,
    allowRootActions: Boolean
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(artifactTypeIcon(artifact.type), null, tint = colorScheme.primary, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(artifact.name.substringAfterLast("/"),
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${stringResource(artifactTypeLabelRes(artifact.type))} · ${DownloadUtils.formatSize(artifact.sizeBytes)}",
                        fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary
                    )
                }
                MiuixTagChip(stringResource(R.string.flash_local_file), colorScheme.primary)
            }
            MiuixDownloadedOutputRow(
                artifact = artifact,
                onCopyPath = { onCopyPath(artifact) },
                onInstall = { onInstall(artifact) },
                onFlashWithSlot = { slot -> onFlash(artifact, slot) },
                onDelete = { onDelete(artifact) },
                allowRootActions = allowRootActions
            )
        }
    }
}

@Composable
private fun MiuixDownloadedOutputRow(
    artifact: DownloadedArtifact,
    onCopyPath: () -> Unit,
    onInstall: () -> Unit,
    onFlashWithSlot: (RootUtils.Ak3SlotTarget) -> Unit,
    onDelete: () -> Unit,
    allowRootActions: Boolean
) {
    val installable = artifact.isInstallableApk()
    var showInstallConfirm by remember { mutableStateOf(false) }
    var showFlashConfirm by remember { mutableStateOf(false) }
    val isAk3 = artifact.type == ArtifactType.ANYKERNEL3
    var ak3Slot by remember { mutableStateOf(RootUtils.Ak3SlotTarget.CURRENT) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.CheckCircle, null, tint = colorScheme.primary, modifier = Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(artifact.name.substringAfterLast("/"),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${stringResource(artifactTypeLabelRes(artifact.type))} · ${DownloadUtils.formatSize(artifact.sizeBytes)}",
                    fontSize = 11.sp, color = colorScheme.onSurfaceVariantSummary
                )
            }
            // Delete button
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, null, tint = colorScheme.error, modifier = Modifier.size(18.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (installable) {
                Button(
                    onClick = { showInstallConfirm = true },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.InstallMobile, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.flash_install), fontSize = 13.sp)
                }
            } else {
                Button(
                    onClick = onCopyPath,
                    colors = ButtonDefaults.buttonColors(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.flash_copy_path), fontSize = 13.sp)
                }
            }
            if (allowRootActions) {
                when (artifact.type) {
                    ArtifactType.KERNEL_IMG,
                    ArtifactType.ANYKERNEL3,
                    ArtifactType.SUSFS_MODULE -> Button(
                        onClick = { showFlashConfirm = true },
                        colors = ButtonDefaults.buttonColors(
                            color = if (artifact.type == ArtifactType.KERNEL_IMG) colorScheme.error
                            else colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            if (artifact.type == ArtifactType.SUSFS_MODULE) Icons.Filled.Extension else Icons.Filled.FlashOn,
                            null, modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(flashButtonLabelRes(artifact.type)), fontSize = 13.sp)
                    }
                    else -> {}
                }
            }
        }
    }

    // ── Install confirmation dialog ──
    if (showInstallConfirm) {
        WindowDialog(
            title = stringResource(R.string.flash_confirm_install_manager),
            summary = stringResource(R.string.flash_confirm_install_manager_msg),
            show = true,
            onDismissRequest = { showInstallConfirm = false }
        ) {
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(text = stringResource(R.string.cancel), onClick = { showInstallConfirm = false }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(20.dp))
                TextButton(text = stringResource(R.string.confirm), onClick = { showInstallConfirm = false; onInstall() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
            }
        }
    }

    // ── Flash confirmation dialog ──
    if (showFlashConfirm) {
        WindowDialog(
            title = stringResource(R.string.flash_confirm),
            summary = stringResource(R.string.flash_confirm_msg),
            show = true,
            onDismissRequest = { showFlashConfirm = false }
        ) {
            if (isAk3) {
                Card {
                    RadioButtonPreference(
                        title = stringResource(R.string.root_patch_ak3_slot_current),
                        selected = ak3Slot == RootUtils.Ak3SlotTarget.CURRENT,
                        onClick = { ak3Slot = RootUtils.Ak3SlotTarget.CURRENT }
                    )
                    HorizontalDivider()
                    RadioButtonPreference(
                        title = stringResource(R.string.root_patch_ak3_slot_inactive),
                        selected = ak3Slot == RootUtils.Ak3SlotTarget.INACTIVE,
                        onClick = { ak3Slot = RootUtils.Ak3SlotTarget.INACTIVE }
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(text = stringResource(R.string.cancel), onClick = { showFlashConfirm = false }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(20.dp))
                TextButton(text = stringResource(R.string.confirm), onClick = { showFlashConfirm = false; onFlashWithSlot(if (isAk3) ak3Slot else RootUtils.Ak3SlotTarget.CURRENT) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Category Progress Card (for building state)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun MiuixCategoryProgressCard(progress: BuildProgress?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    text = if (progress != null && progress.totalSteps > 0)
                        "${progress.percent}% · ${progress.currentStep}"
                    else stringResource(R.string.flash_building_subtitle),
                    fontSize = 14.sp,
                    color = colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
            if (progress != null && progress.totalSteps > 0) {
                val animProgress by animateFloatAsState(
                    targetValue = (progress.percent / 100f).coerceIn(0f, 1f), label = "cat-progress"
                )
                LinearProgressIndicator(progress = animProgress, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Duration Chip
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun MiuixDurationChip(
    createdAt: String,
    finishedAt: String? = null,
    live: Boolean = finishedAt.isNullOrBlank()
) {
    val startMillis = remember(createdAt) { parseIsoMillis(createdAt) }
    if (startMillis <= 0L) return
    val endMillis = remember(finishedAt) { parseIsoMillis(finishedAt.orEmpty()) }
    val isFinished = !live && endMillis > startMillis
    if (!live && !isFinished) return

    var currentMillis by remember(startMillis, endMillis) {
        mutableLongStateOf(if (isFinished) endMillis else System.currentTimeMillis())
    }
    LaunchedEffect(startMillis, endMillis) {
        if (!isFinished) {
            while (true) {
                currentMillis = System.currentTimeMillis()
                delay(100L.milliseconds)
            }
        }
    }

    val elapsedSec = ((currentMillis - startMillis) / 1000L).coerceAtLeast(0L)
    val h = elapsedSec / 3600
    val m = (elapsedSec % 3600) / 60
    val s = elapsedSec % 60
    val formatted = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)

    Row(
        Modifier.background(colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(percent = 50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(Icons.Filled.Schedule, null, tint = colorScheme.primary, modifier = Modifier.size(14.dp))
        Text(formatted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.primary)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Parameter Dialog
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ParameterDialog(
    group: WorkflowArtifactGroup,
    summary: BuildParameterSummary?,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    WindowDialog(
        title = stringResource(R.string.flash_parameter_details),
        show = true,
        onDismissRequest = onDismiss
    ) {
        Column(Modifier.heightIn(min = 60.dp, max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Workflow info
            MiuixParamSection(stringResource(R.string.flash_workflow)) {
                MiuixParamRow(stringResource(R.string.flash_number), if (group.runNumber > 0) "#${group.runNumber}" else "#${group.runId}")
                MiuixParamRow(stringResource(R.string.flash_title_label), group.runTitle)
                MiuixParamRow(stringResource(R.string.flash_artifacts),
                    stringResource(R.string.flash_artifact_counts, group.remote.size, group.local.size))
            }
            when {
                loading -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.flash_reading_build_summary), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                    }
                    Spacer(Modifier.height(32.dp))
                }
                error != null -> Text(error, fontSize = 14.sp, color = colorScheme.error)
                summary != null -> {
                    MiuixParamSection(stringResource(R.string.flash_version_params)) {
                        MiuixParamRow(stringResource(R.string.build_android_version), summary.androidVersion)
                        MiuixParamRow(stringResource(R.string.build_kernel_version), summary.kernelVersion)
                        MiuixParamRow(stringResource(R.string.build_sub_level), summary.subLevel)
                        MiuixParamRow(stringResource(R.string.runtime_patch_level), summary.osPatchLevel)
                        MiuixParamRow(stringResource(R.string.flash_build_time), summary.buildTime)
                    }
                    MiuixParamSection("KernelSU") {
                        MiuixParamRow(stringResource(R.string.flash_ksu_variant), summary.ksuVariant)
                        MiuixParamRow(stringResource(R.string.flash_ksu_branch), summary.ksuBranch)
                        MiuixParamRow(stringResource(R.string.flash_susfs_status), summary.susfsEnabled)
                    }
                    MiuixParamSection(stringResource(R.string.flash_patches_features)) {
                        MiuixParamRow(stringResource(R.string.flash_zram), summary.zramEnabled)
                        MiuixParamRow(stringResource(R.string.flash_zram_full_algo), summary.zramFullAlgo)
                        MiuixParamRow(stringResource(R.string.flash_zram_extra_algos), summary.zramExtraAlgos)
                        MiuixParamRow(stringResource(R.string.flash_bbg_patch), summary.bbgEnabled)
                        MiuixParamRow("DDK LSM", summary.ddkLsm)
                        MiuixParamRow(stringResource(R.string.flash_ntsync_patch), summary.ntsyncEnabled)
                        MiuixParamRow(stringResource(R.string.runtime_feature_networking), summary.networkingEnabled)
                        MiuixParamRow(stringResource(R.string.flash_kpm_feature), summary.kpmEnabled)
                        MiuixParamRow(stringResource(R.string.flash_kpm_password), summary.kpmPassword)
                        MiuixParamRow("Re-Kernel", summary.reKernelEnabled)
                        MiuixParamRow(stringResource(R.string.runtime_virtualization), summary.virtualizationSupport)
                        MiuixParamRow(stringResource(R.string.flash_custom_injection), summary.customInjection)
                        MiuixParamRow("Stock Config", summary.stockConfig)
                    }
                    val extra = summary.extraRows.orEmpty()
                    if (extra.isNotEmpty()) MiuixParamSection(stringResource(R.string.flash_extra_info)) {
                        extra.forEach { (k, v) -> MiuixParamRow(k, v) }
                    }
                }
                else -> Text(stringResource(R.string.flash_no_parameter_details), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (error != null && !loading) TextButton(text = stringResource(R.string.retry), onClick = onRetry, modifier = Modifier.weight(1f))
            TextButton(text = stringResource(R.string.close), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MiuixParamSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.primary)
        content()
        HorizontalDivider()
    }
}

@Composable
private fun MiuixParamRow(label: String, value: String) {
    val display = miuixParamDisplayValue(value)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Text(label, fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary, modifier = Modifier.width(96.dp))
        Text(display, fontSize = 13.sp, color = colorScheme.onSurface)
    }
}

@Composable
private fun miuixParamDisplayValue(value: String): String {
    val t = value.trim()
    return when (t.lowercase()) {
        "" -> stringResource(R.string.flash_unknown)
        "true" -> stringResource(R.string.build_feature_enabled)
        "false" -> stringResource(R.string.build_virtualization_off)
        "none" -> stringResource(R.string.flash_value_none)
        "default" -> stringResource(R.string.flash_value_default)
        "set" -> stringResource(R.string.flash_value_set)
        else -> t
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Shared Helpers
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun MiuixTagChip(label: String, color: Color = colorScheme.primary) {
    Box(Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(label, fontSize = 11.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun artifactTypeIcon(type: ArtifactType): ImageVector = when (type) {
    ArtifactType.KERNEL_PACKAGE -> Icons.Filled.Inventory2
    ArtifactType.KERNEL_IMG -> Icons.Filled.Memory
    ArtifactType.ANYKERNEL3 -> Icons.Filled.Archive
    ArtifactType.ABK_MANAGER -> Icons.Filled.InstallMobile
    ArtifactType.KSU_MANAGER -> Icons.Filled.Shield
    ArtifactType.SUSFS_MODULE -> Icons.Filled.Extension
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

/**
 * Copy a downloaded file path to clipboard and show a toast.
 */
internal fun copyPath(context: Context, item: DownloadedArtifact) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(item.name, item.filePath))
    Toast.makeText(context, context.getString(R.string.flash_copy_path_done), Toast.LENGTH_SHORT).show()
}

/**
 * Install a manager APK via system intent.
 */
internal fun installManagerApk(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    item: DownloadedArtifact,
    rootGranted: Boolean,
    onTerminal: (MiuixTerminalState) -> Unit
) {
    if (!item.isInstallableApk()) {
        Toast.makeText(context, context.getString(R.string.flash_unsupported_auto_flash), Toast.LENGTH_SHORT).show()
        return
    }
    if (!rootGranted) {
        val state = MiuixTerminalState(
            title = context.getString(R.string.flash_install_manager_apk),
            running = false, success = false,
            logLines = mutableStateListOf(
                "$ pm install -r ${item.name}",
                context.getString(R.string.flash_root_unauthorized),
                context.getString(R.string.flash_grant_root_install_manager)
            )
        )
        onTerminal(state)
        return
    }
    val title = context.getString(R.string.flash_install_manager_apk)
    val logLines = mutableStateListOf(
        "$ pm install -r ${item.name}", "file: ${item.filePath}", "",
        context.getString(R.string.flash_wait_root_shell)
    )
    val state = MiuixTerminalState(title = title, running = true, success = null, logLines = logLines)
    onTerminal(state)
    scope.launch {
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val prepared = DownloadUtils.prepareDownloadedArtifact(context, item)
                try {
                    if (prepared.cleanupDir != null) {
                        withContext(Dispatchers.Main) {
                            logLines.addAll(listOf("[ABK] 已解包下载包到缓存目录", "[ABK] Payload: ${prepared.file.absolutePath}"))
                        }
                    }
                    RootUtils.installApk(context, prepared.file.absolutePath) { line ->
                        scope.launch(Dispatchers.Main) { logLines.add(line) }
                    }
                } finally {
                    prepared.cleanupDir?.deleteRecursively()
                }
            }
        }.getOrElse { error ->
            RootUtils.ShellResult(false, listOf(error.message ?: error::class.java.simpleName))
        }
        withContext(Dispatchers.Main) {
            state.running = false
            state.success = result.success
            if (!result.success) {
                logLines.addAll(listOf("", context.getString(R.string.flash_operation_failed), ""))
                logLines.addAll(result.output)
            }
        }
    }
}

/**
 * Flash/execute a downloaded kernel artifact via root shell.
 * Opens the terminal dialog to show progress.
 * Must be called from a coroutine scope context.
 */
internal fun flashItem(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    item: DownloadedArtifact,
    rootGranted: Boolean,
    anyKernelSlotTarget: RootUtils.Ak3SlotTarget = RootUtils.Ak3SlotTarget.CURRENT,
    onTerminal: (MiuixTerminalState) -> Unit
) {
    if (!rootGranted) {
        Toast.makeText(context, context.getString(R.string.flash_root_unauthorized), Toast.LENGTH_LONG).show()
        return
    }
    val title = context.getString(flashOperationLabelRes(item.type))
    val slotLog = if (item.type == ArtifactType.ANYKERNEL3) {
        listOf(context.getString(R.string.root_patch_log_slot, if (anyKernelSlotTarget == RootUtils.Ak3SlotTarget.INACTIVE) context.getString(R.string.root_patch_ak3_slot_inactive) else context.getString(R.string.root_patch_ak3_slot_current)))
    } else emptyList()
    val logLines = mutableStateListOf("$ $title", "file: ${item.filePath}")
    logLines.addAll(slotLog)
    logLines.addAll(listOf("", context.getString(R.string.flash_wait_root_shell)))
    val state = MiuixTerminalState(title = title, running = true, success = null, logLines = logLines)
    onTerminal(state)
    scope.launch {
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val prepared = DownloadUtils.prepareDownloadedArtifact(context, item)
                try {
                    if (prepared.cleanupDir != null) {
                        withContext(Dispatchers.Main) {
                            logLines.addAll(listOf("[ABK] 已解包下载包到缓存目录", "[ABK] Payload: ${prepared.file.absolutePath}"))
                        }
                    }
                    when (item.type) {
                        ArtifactType.KERNEL_IMG -> RootUtils.flashImage(prepared.file.absolutePath) { line ->
                            scope.launch(Dispatchers.Main) { logLines.add(line) }
                        }
                        ArtifactType.ANYKERNEL3 -> RootUtils.flashAnyKernel3(context, prepared.file.absolutePath, targetSlot = anyKernelSlotTarget, onOutput = { line ->
                            scope.launch(Dispatchers.Main) { logLines.add(line) }
                        })
                        ArtifactType.SUSFS_MODULE -> RootUtils.installModule(prepared.file.absolutePath) { line ->
                            scope.launch(Dispatchers.Main) { logLines.add(line) }
                        }
                        else -> RootUtils.ShellResult(false, listOf(context.getString(R.string.flash_unsupported_auto_flash)))
                    }
                } finally {
                    prepared.cleanupDir?.deleteRecursively()
                }
            }
        }.getOrElse { error ->
            RootUtils.ShellResult(false, listOf(error.message ?: error::class.java.simpleName))
        }
        withContext(Dispatchers.Main) {
            state.running = false
            state.success = result.success
            if (!result.success && result.output.isNotEmpty()) {
                logLines.addAll(listOf("", context.getString(R.string.flash_operation_failed), ""))
                logLines.addAll(result.output)
            }
        }
    }
}

class MiuixTerminalState(
    val title: String,
    var running: Boolean,
    var success: Boolean?,
    val logLines: MutableList<String>,
    var closed: Boolean = false
)

@Composable
fun MiuixTerminalDialog(
    state: MiuixTerminalState,
    onClose: () -> Unit,
    onReboot: () -> Unit
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(state.logLines.size) { scrollState.animateScrollTo(scrollState.maxValue) }
    WindowDialog(
        title = if (state.running) stringResource(R.string.flash_executing_title, state.title) else state.title,
        show = true,
        onDismissRequest = { if (!state.running) onClose() }
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp)
                .clip(RoundedCornerShape(8.dp)).background(colorScheme.surfaceContainer).padding(12.dp)
                .verticalScroll(scrollState)
        ) {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.logLines.forEach { line ->
                        Text(line, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            color = if (line.startsWith("$")) colorScheme.primary else colorScheme.onSurfaceVariantSummary)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.running) {
                TextButton(text = stringResource(R.string.runtime_running), onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
            } else {
                TextButton(text = stringResource(R.string.close), onClick = onClose, modifier = Modifier.weight(1f))
                if (state.success == true) {
                    Button(onClick = onReboot, colors = ButtonDefaults.buttonColors(color = colorScheme.error), modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.runtime_reboot))
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Prebuilt GKI Detail Screen
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun MiuixPrebuiltDetailScreen(vm: MainViewModel, releaseId: Long, onClose: () -> Unit) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rootGranted = state.rootGranted
    var terminalState by remember { mutableStateOf<MiuixTerminalState?>(null) }

    val release = state.prebuiltGkiReleases.find { it.id == releaseId }
    LaunchedEffect(release) { if (release != null) vm.loadPrebuiltGkiAssets(release) }
    val allAssets = state.prebuiltGkiAssetsByReleaseId[releaseId] ?: emptyList()
    val loading = allAssets.isEmpty() && release != null
    var filter by remember(release?.id) { mutableStateOf(defaultPrebuiltFilter()) }

    // Filter candidates (only kernel-related artifacts)
    val candidateAssets = remember(allAssets) {
        allAssets.filter(::isPrebuiltGkiCandidateUi)
    }
    val filteredAssets = remember(candidateAssets, filter) {
        val fromCache = if (filter.onlyMatches) {
            candidateAssets.filter { prebuiltAssetMatchesFilter(it, filter) }
        } else {
            candidateAssets
        }
        fromCache
    }
    val recommendedIds = remember(filteredAssets, state.recommendedBuildConfig) {
        recommendedPrebuiltAssetIdsForUi(filteredAssets, state.recommendedBuildConfig)
    }
    val dlIds = state.downloadedArtifacts.map { it.id }.toSet()

    // Build dropdown options
    val unlimitedLabel = stringResource(R.string.flash_unlimited)
    val androidOpts = remember { listOf(unlimitedLabel) + KernelSupport.androidVersions() }
    val kernelOpts = remember { listOf(unlimitedLabel) + KernelSupport.kernelVersions() }
    val subLevelOpts = remember(filter.androidVersion, filter.kernelVersion) {
        listOf(unlimitedLabel) + prebuiltSubLevelOptions(filter.androidVersion, filter.kernelVersion)
    }
    val patchOpts = remember(filter.androidVersion, filter.kernelVersion, filter.subLevel) {
        listOf(unlimitedLabel) + prebuiltPatchOptions(filter.androidVersion, filter.kernelVersion, filter.subLevel)
    }
    fun selIdx(opts: List<String>, v: String) = if (v.isBlank()) 0 else opts.indexOf(v).coerceAtLeast(0)
    fun selVal(opts: List<String>, i: Int) = if (i <= 0) "" else opts[i]
    fun updateFilter(next: PrebuiltGkiFilter) { filter = sanitizePrebuiltFilter(next) }

    var showParams by remember { mutableStateOf(false) }
    var deleteFileTarget by remember { mutableStateOf<DownloadedArtifact?>(null) }
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = MiuixScrollBehavior(state = topBarState)
    val titleText = release?.name ?: "Release #$releaseId"

    Scaffold(
        topBar = {
            TopAppBar(
                title = titleText,
                largeTitle = titleText,
                scrollBehavior = scrollBehavior,
                navigationIcon = { IconButton(onClick = onClose) { Icon(MiuixIcons.Back, null) } },
                actions = {
                    IconButton(onClick = { showParams = true }) {
                        Icon(Icons.Filled.Tune, null, tint = colorScheme.onSurfaceVariantActions)
                    }
                    IconButton(onClick = {
                        if (release != null) vm.loadPrebuiltGkiAssets(release, force = true)
                    }) {
                        Icon(Icons.Filled.Refresh, null, tint = colorScheme.onSurfaceVariantActions)
                    }
                }
            )
        },
        containerColor = colorScheme.surface
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.flash_loading_release), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                }
            }
            allAssets.isEmpty() && !loading -> Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.flash_no_artifacts), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp).nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ── Header card ──
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Filled.CloudDownload, null, tint = colorScheme.primary, modifier = Modifier.size(22.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(release?.name ?: "Release #$releaseId",
                                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(release?.let { "${it.tagName} · ${releaseDateLabel(it.publishedAt, stringResource(R.string.flash_unknown_date))}" } ?: "",
                                        fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                                }
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val visible = filteredAssets.size
                                val total = candidateAssets.size
                                MiuixTagChip(
                                    stringResource(R.string.flash_visible_assets_count, visible, total),
                                    colorScheme.primary
                                )
                                MiuixTagChip(
                                    stringResource(R.string.flash_downloaded_count,
                                        state.downloadedArtifacts.count { dlIds.contains(it.id) && filteredAssets.any { a -> a.id == it.id } }),
                                    colorScheme.primary.copy(alpha = 0.7f)
                                )
                                MiuixTagChip(stringResource(R.string.flash_prebuilt_gki), colorScheme.primary.copy(alpha = 0.6f))
                            }
                        }
                    }
                }

                // ── Filter card ──
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Tune, null, tint = colorScheme.onSurface, modifier = Modifier.size(18.dp))
                                Text(stringResource(R.string.flash_filters), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                            }
                            top.yukonga.miuix.kmp.preference.WindowDropdownPreference(
                                title = stringResource(R.string.build_android_version),
                                items = androidOpts,
                                selectedIndex = selIdx(androidOpts, filter.androidVersion),
                                onSelectedIndexChange = { i -> updateFilter(filter.copy(androidVersion = selVal(androidOpts, i))) }
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                top.yukonga.miuix.kmp.preference.WindowDropdownPreference(
                                    title = stringResource(R.string.build_kernel_version),
                                    items = kernelOpts,
                                    selectedIndex = selIdx(kernelOpts, filter.kernelVersion),
                                    onSelectedIndexChange = { i -> updateFilter(filter.copy(kernelVersion = selVal(kernelOpts, i))) },
                                    modifier = Modifier.weight(1f)
                                )
                                top.yukonga.miuix.kmp.preference.WindowDropdownPreference(
                                    title = stringResource(R.string.flash_minor_version),
                                    items = subLevelOpts,
                                    selectedIndex = selIdx(subLevelOpts, filter.subLevel),
                                    onSelectedIndexChange = { i -> updateFilter(filter.copy(subLevel = selVal(subLevelOpts, i))) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            top.yukonga.miuix.kmp.preference.WindowDropdownPreference(
                                title = stringResource(R.string.build_security_patch_level),
                                items = patchOpts,
                                selectedIndex = selIdx(patchOpts, filter.osPatchLevel),
                                onSelectedIndexChange = { i -> updateFilter(filter.copy(osPatchLevel = selVal(patchOpts, i))) }
                            )
                            HorizontalDivider()
                            top.yukonga.miuix.kmp.preference.SwitchPreference(
                                title = stringResource(R.string.flash_prebuilt_only_matches),
                                checked = filter.onlyMatches,
                                onCheckedChange = { updateFilter(filter.copy(onlyMatches = it)) }
                            )
                        }
                    }
                }

                // ── Asset list ──
                when {
                    filteredAssets.isEmpty() -> {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.Inbox, null, modifier = Modifier.size(48.dp), tint = colorScheme.onSurfaceVariantActions)
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(R.string.flash_no_matching_assets),
                                        fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = if (filter.onlyMatches) stringResource(R.string.flash_no_matching_assets_filtered)
                                        else stringResource(R.string.flash_no_recognized_prebuilt_assets),
                                        fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        items(filteredAssets, key = { "prebuilt-${it.id}" }) { asset ->
                            val progress = state.downloadProgress[DownloadUtils.prebuiltProgressKey(asset.id)]
                            val downloadedFiles = state.downloadedArtifacts.filter {
                                DownloadUtils.matchesDownloadedPrebuilt(it, asset)
                            }
                            val recommended = asset.id in recommendedIds
                            val type = prebuiltArtifactType(asset)

                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Header
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(artifactTypeIcon(type), null, tint = colorScheme.primary, modifier = Modifier.size(20.dp))
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(asset.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                                color = colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text("${asset.releaseTag} · ${DownloadUtils.formatSize(asset.sizeBytes)}",
                                                fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
                                        }
                                        MiuixTagChip(
                                            if (recommended) stringResource(R.string.flash_device_recommended)
                                            else "Release",
                                            if (recommended) colorScheme.primary else colorScheme.onSurfaceVariantActions
                                        )
                                    }

                                    // Action area
                                    when {
                                        progress != null -> {
                                            val animProgress by animateFloatAsState(
                                                targetValue = (progress / 100f).coerceIn(0f, 1f), label = "prebuilt-dl"
                                            )
                                            LinearProgressIndicator(progress = animProgress, modifier = Modifier.fillMaxWidth())
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text(stringResource(R.string.flash_download_progress, progress),
                                                    fontSize = 11.sp, color = colorScheme.onSurfaceVariantSummary)
                                                IconButton(onClick = { vm.cancelDownload(DownloadUtils.prebuiltProgressKey(asset.id)) }) {
                                                    Icon(MiuixIcons.Pause, stringResource(R.string.flash_cancel_download), tint = colorScheme.error)
                                                }
                                            }
                                        }
                                        downloadedFiles.isEmpty() -> {
                                            Button(
                                                onClick = { vm.downloadPrebuiltGki(asset) },
                                                colors = ButtonDefaults.buttonColorsPrimary(),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(stringResource(R.string.flash_download))
                                            }
                                        }
                                        else -> {
                                            downloadedFiles.forEachIndexed { index, file ->
                                                if (index > 0) HorizontalDivider()
                                                MiuixDownloadedOutputRow(
                                                    artifact = file,
                                                    onCopyPath = { copyPath(context, file) },
                                                    onInstall = { installManagerApk(context, scope, file, rootGranted) { terminalState = it } },
                                                    onFlashWithSlot = { slot -> flashItem(context, scope, file, rootGranted, slot) { terminalState = it } },
                                                    onDelete = { deleteFileTarget = file },
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
            }
        }
    }

    // ── Parameter summary dialog ──
    if (showParams && release != null) {
        val summary = remember(release.id, release.body) { parsePrebuiltGkiParameterSummary(release) }
        WindowDialog(title = stringResource(R.string.flash_parameter_details), show = true, onDismissRequest = { showParams = false }) {
            Column(Modifier.heightIn(min = 60.dp, max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MiuixParamSection("Release") {
                    MiuixParamRow(stringResource(R.string.flash_name), release.name)
                    MiuixParamRow("Tag", release.tagName)
                    MiuixParamRow(stringResource(R.string.flash_published_at), release.publishedAt.take(10))
                    MiuixParamRow(stringResource(R.string.flash_assets),
                        if (release.assetCount > 0) stringResource(R.string.flash_asset_count, release.assetCount) else stringResource(R.string.flash_unknown))
                }
                if (summary != null) {
                    MiuixParamSection(stringResource(R.string.flash_version_params)) {
                        MiuixParamRow(stringResource(R.string.build_android_version), summary.androidVersion)
                        MiuixParamRow(stringResource(R.string.build_kernel_version), summary.kernelVersion)
                        MiuixParamRow(stringResource(R.string.build_sub_level), summary.subLevel)
                        MiuixParamRow(stringResource(R.string.runtime_patch_level), summary.osPatchLevel)
                        MiuixParamRow(stringResource(R.string.flash_build_time), summary.buildTime)
                    }
                    MiuixParamSection("KernelSU") {
                        MiuixParamRow(stringResource(R.string.flash_ksu_variant), summary.ksuVariant)
                        MiuixParamRow(stringResource(R.string.flash_ksu_branch), summary.ksuBranch)
                        MiuixParamRow(stringResource(R.string.flash_susfs_status), summary.susfsEnabled)
                    }
                    MiuixParamSection(stringResource(R.string.flash_patches_features)) {
                        MiuixParamRow(stringResource(R.string.flash_zram), summary.zramEnabled)
                        MiuixParamRow(stringResource(R.string.flash_zram_full_algo), summary.zramFullAlgo)
                        MiuixParamRow(stringResource(R.string.flash_zram_extra_algos), summary.zramExtraAlgos)
                        MiuixParamRow(stringResource(R.string.flash_bbg_patch), summary.bbgEnabled)
                        MiuixParamRow("DDK LSM", summary.ddkLsm)
                        MiuixParamRow(stringResource(R.string.flash_ntsync_patch), summary.ntsyncEnabled)
                        MiuixParamRow(stringResource(R.string.runtime_feature_networking), summary.networkingEnabled)
                        MiuixParamRow(stringResource(R.string.flash_kpm_feature), summary.kpmEnabled)
                        MiuixParamRow(stringResource(R.string.flash_kpm_password), summary.kpmPassword)
                        MiuixParamRow("Re-Kernel", summary.reKernelEnabled)
                        MiuixParamRow(stringResource(R.string.runtime_virtualization), summary.virtualizationSupport)
                        MiuixParamRow(stringResource(R.string.flash_custom_injection), summary.customInjection)
                        MiuixParamRow("Stock Config", summary.stockConfig)
                    }
                    val extra = summary.extraRows.orEmpty()
                    if (extra.isNotEmpty()) MiuixParamSection(stringResource(R.string.flash_extra_info)) {
                        extra.forEach { (k, v) -> MiuixParamRow(k, v) }
                    }
                } else {
                    Text(stringResource(R.string.flash_release_no_matrix), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                }
            }
            TextButton(text = stringResource(R.string.close), onClick = { showParams = false }, modifier = Modifier.fillMaxWidth())
        }
    }

    // ── Terminal dialog ──
    terminalState?.let { ts ->
        if (!ts.closed) {
            MiuixTerminalDialog(
                state = ts,
                onClose = { ts.closed = true; terminalState = null },
                onReboot = { ts.closed = true; terminalState = null; scope.launch(Dispatchers.IO) { RootUtils.reboot() } }
            )
        }
    }

    // ── Delete file confirmation ──
    deleteFileTarget?.let { item ->
        WindowDialog(
            title = stringResource(R.string.flash_delete_file),
            show = true,
            onDismissRequest = { deleteFileTarget = null }
        ) {
            Text(stringResource(R.string.flash_delete_file_msg, item.name), fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = stringResource(R.string.cancel), onClick = { deleteFileTarget = null }, modifier = Modifier.weight(1f))
                Button(
                    onClick = { vm.deleteDownloadedArtifact(item.filePath); deleteFileTarget = null },
                    colors = ButtonDefaults.buttonColors(color = colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.delete)) }
            }
        }
    }
}

