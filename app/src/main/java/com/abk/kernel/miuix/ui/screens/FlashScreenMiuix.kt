package com.abk.kernel.miuix.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.ArtifactCategory
import com.abk.kernel.data.model.ArtifactType
import com.abk.kernel.data.model.BuildQueueItemStatus
import com.abk.kernel.data.model.DownloadedArtifact
import com.abk.kernel.data.model.PREBUILT_GKI_RUN_ID
import com.abk.kernel.data.model.PrebuiltGkiRelease
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.data.model.isActive
import com.abk.kernel.data.model.isFailedFlashRun
import com.abk.kernel.ui.navigation3.LocalNavigator
import com.abk.kernel.ui.navigation3.Route
import com.abk.kernel.ui.screens.flash.FlashContentTab
import com.abk.kernel.ui.screens.flash.FlashFilterSaver
import com.abk.kernel.ui.screens.flash.WorkflowArtifactGroup
import com.abk.kernel.ui.screens.flash.buildWorkflowGroups
import com.abk.kernel.ui.screens.flash.emptyWorkflowGroupFor
import com.abk.kernel.ui.screens.flash.flashOperationLabelRes
import com.abk.kernel.ui.screens.flash.hasDownloadedFilesForRun
import com.abk.kernel.ui.screens.flash.hasKernelArtifact
import com.abk.kernel.ui.screens.flash.hasManagerArtifact
import com.abk.kernel.ui.screens.flash.isAbkManagerFlashRun
import com.abk.kernel.ui.screens.flash.labelRes
import com.abk.kernel.ui.screens.flash.limitWorkflowGroupsForDisplay
import com.abk.kernel.ui.screens.flash.shouldAppearInWorkflowList
import com.abk.kernel.ui.screens.flash.shouldShowParameterDetails
import com.abk.kernel.ui.screens.flash.sortedForWorkflowDisplay
import com.abk.kernel.ui.screens.flash.toFlashFilterOrNull
import com.abk.kernel.ui.screens.flash.toJsonString
import com.abk.kernel.ui.screens.flash.releaseDateLabel
import com.abk.kernel.ui.screens.flash.workflowRunLabel
import com.abk.kernel.ui.screens.flash.workflowState
import com.abk.kernel.miuix.ui.screens.flash.MiuixDismissFailedRunDialog
import com.abk.kernel.miuix.ui.screens.flash.MiuixBuildParameterSummaryDialog
import com.abk.kernel.miuix.ui.screens.flash.MiuixCancelBuildConfirmDialog
import com.abk.kernel.miuix.ui.screens.flash.MiuixDeleteFileDialog
import com.abk.kernel.miuix.ui.screens.flash.MiuixDeleteWorkflowDialog
import com.abk.kernel.miuix.ui.screens.flash.MiuixFlashConfirmDialog
import com.abk.kernel.miuix.ui.screens.flash.MiuixInstallManagerConfirmDialog
import com.abk.kernel.miuix.ui.screens.flash.MiuixLocalOnlyArtifactCard
import com.abk.kernel.miuix.ui.screens.flash.MiuixPrebuiltParameterSummaryDialog
import com.abk.kernel.miuix.ui.screens.flash.MiuixWorkflowDownloadManagementCard
import com.abk.kernel.miuix.ui.screens.flash.common.FlashTerminalParams
import com.abk.kernel.miuix.ui.screens.flash.MiuixWorkflowRunCard
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.FlashFilter
import com.abk.kernel.utils.FlashFilterKernelKind
import com.abk.kernel.utils.FlashFilterWorkflowState
import com.abk.kernel.utils.FlashWorkflowFilter
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.utils.WorkflowPrimary
import com.abk.kernel.viewmodel.MainViewModel
import com.abk.kernel.viewmodel.mergeWorkflowActiveDownloads
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CheckboxDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog
import com.abk.kernel.miuix.util.BlurredBar
import com.abk.kernel.miuix.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FlashScreenMiuix(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onDetailPageVisibleChange: (Boolean) -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val navigator = LocalNavigator.current

    // ── Tab & filter state ──────────────────────────────────────────────
    var activeContentTab by rememberSaveable { mutableStateOf(FlashContentTab.Workflows) }
    var filter by rememberSaveable(stateSaver = FlashFilterSaver) { mutableStateOf(FlashFilter()) }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var filterLoaded by rememberSaveable { mutableStateOf(false) }

    // ── Selection & dialog state ────────────────────────────────────────
    var selectedItem by remember { mutableStateOf<DownloadedArtifact?>(null) }
    var deleteFileTarget by remember { mutableStateOf<DownloadedArtifact?>(null) }
    var deleteWorkflowTarget by remember { mutableStateOf<WorkflowArtifactGroup?>(null) }
    var parameterTarget by remember { mutableStateOf<WorkflowArtifactGroup?>(null) }
    var prebuiltParameterTarget by remember { mutableStateOf<PrebuiltGkiRelease?>(null) }
    var showFlashConfirm by remember { mutableStateOf(false) }
    var showUnverifiedFlashConfirm by remember { mutableStateOf(false) }
    var allowLegacyBundleFallback by remember { mutableStateOf(false) }
    var showInstallManagerConfirm by remember { mutableStateOf(false) }
    var cancelConfirmRunId by remember { mutableStateOf<Long?>(null) }
    var dismissingFailedRunId by remember { mutableStateOf<Long?>(null) }

    // ── AnyKernel slot state ────────────────────────────────────────────
    var selectedAnyKernelSlotTargetName by rememberSaveable {
        mutableStateOf(RootUtils.Ak3SlotTarget.CURRENT.name)
    }
    val selectedAnyKernelSlotTarget = runCatching {
        RootUtils.Ak3SlotTarget.valueOf(selectedAnyKernelSlotTargetName)
    }.getOrDefault(RootUtils.Ak3SlotTarget.CURRENT)
    val flashAnyKernelCurrentSlotLabel = stringResource(R.string.root_patch_ak3_slot_current)
    val flashAnyKernelInactiveSlotLabel = stringResource(R.string.root_patch_ak3_slot_inactive)

    // ── Scroll state ────────────────────────────────────────────────────
    val scrollBehavior = MiuixScrollBehavior()
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberBlurBackdrop(state.miuixBlurEnabled, surfaceColor)
    val barColor = if (backdrop != null) Color.Transparent else surfaceColor
    val flashListScrollState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    // ── Derived state ───────────────────────────────────────────────────
    val rootGranted = state.rootGranted
    val prebuiltOnlyMode = !state.isLoggedIn
    val currentContentTab = when {
        prebuiltOnlyMode -> FlashContentTab.PrebuiltGki
        state.prebuiltGkiEnabled -> activeContentTab
        else -> FlashContentTab.Workflows
    }

    val supportsAnyKernelInactiveSlot by produceState(initialValue = false, rootGranted) {
        value = withContext(Dispatchers.IO) { RootUtils.supportsAnyKernelInactiveSlot() }
    }

    val workflowActiveDownloads = remember(
        state.activeDownloadTasks,
        state.downloadProgress,
        state.artifacts,
    ) {
        mergeWorkflowActiveDownloads(
            tasks = state.activeDownloadTasks,
            progress = state.downloadProgress,
            artifacts = state.artifacts,
        )
    }

    val pendingAutoDownloadRun = remember(state.pendingAutoDownloadRunId, state.recentRuns) {
        state.recentRuns.firstOrNull { it.id == state.pendingAutoDownloadRunId }
    }

    val remoteArtifacts = remember(state.artifacts, state.isLoggedIn) {
        if (!state.isLoggedIn) {
            emptyList()
        } else {
            state.artifacts.filter {
                !it.expired && DownloadUtils.classifyCategory(DownloadUtils.classifyArtifact(it.name)) != null
            }
        }
    }

    val workflowDownloadedArtifacts = remember(state.downloadedArtifacts, state.prebuiltGkiEnabled, state.isLoggedIn) {
        if (!state.isLoggedIn) {
            emptyList()
        } else if (state.prebuiltGkiEnabled) {
            state.downloadedArtifacts.filterNot { it.runId == PREBUILT_GKI_RUN_ID }
        } else {
            state.downloadedArtifacts
        }
    }

    val unlinkedWorkflowTitle = stringResource(R.string.workflow_unlinked)
    val recentRunById = remember(state.recentRuns, state.sessionGhostFailedRuns) {
        state.recentRuns.associateBy { it.id } + state.sessionGhostFailedRuns
    }

    val workflowGroups = remember(remoteArtifacts, workflowDownloadedArtifacts, unlinkedWorkflowTitle, recentRunById) {
        buildWorkflowGroups(remoteArtifacts, workflowDownloadedArtifacts, unlinkedWorkflowTitle, recentRunById)
    }

    val allWorkflowGroups = remember(workflowGroups, state.sessionGhostFailedRuns, state.dismissedFailedRunIds, recentRunById) {
        val activeRunIds = state.recentRuns.filter { it.isActive() }.map { it.id }.toSet()
        val extraGroups = activeRunIds
            .filter { id -> workflowGroups.none { it.runId == id } }
            .mapNotNull { id ->
                val run = recentRunById[id] ?: return@mapNotNull null
                emptyWorkflowGroupFor(run, unlinkedWorkflowTitle)
            }
        val ghostRunIds = state.sessionGhostFailedRuns.keys
            .filter { it !in state.dismissedFailedRunIds }
            .toSet()
        val extraGhostGroups = ghostRunIds
            .filter { id -> workflowGroups.none { it.runId == id } && id !in activeRunIds }
            .mapNotNull { id ->
                val run = recentRunById[id] ?: return@mapNotNull null
                emptyWorkflowGroupFor(run, unlinkedWorkflowTitle)
            }
        (workflowGroups + extraGroups + extraGhostGroups)
            .filter { group ->
                if (group.runId in state.dismissedFailedRunIds) {
                    return@filter false
                }
                val run = recentRunById[group.runId]
                if (run.isAbkManagerFlashRun(group.runTitle)) {
                    return@filter false
                }
                val isActive = run?.isActive() == true
                val isSessionGhost = group.runId in state.sessionGhostFailedRuns
                isActive || isSessionGhost || group.shouldAppearInWorkflowList(run)
            }
            .sortedForWorkflowDisplay(recentRunById)
    }

    // ── Dispatched config tracking (for chip display during builds) ─────
    val dispatchedConfigByRunId = remember(state.buildQueue) {
        state.buildQueue
            .filter { it.runId > 0L }
            .associate { it.runId to it.config }
    }

    val linkingDispatchedConfig = remember(state.buildQueue) {
        state.buildQueue
            .firstOrNull {
                it.status == BuildQueueItemStatus.DISPATCHING && it.runId <= 0L
            }
            ?.config
    }

    val dispatchedVariantByRunId = remember(dispatchedConfigByRunId) {
        dispatchedConfigByRunId.mapValues { it.value.kernelsuVariant }
    }

    // ── Filtered groups (async to keep list smooth) ─────────────────────
    val filteredGroups by produceState(
        initialValue = allWorkflowGroups,
        allWorkflowGroups,
        filter,
        state.buildParameterSummaries,
        recentRunById,
        dispatchedVariantByRunId,
        linkingDispatchedConfig
    ) {
        value = withContext(Dispatchers.Default) {
            allWorkflowGroups.filter { group ->
                val run = recentRunById[group.runId]
                val summary = state.buildParameterSummaries[group.runId]
                val primary = FlashWorkflowFilter.primaryKind(
                    run = run,
                    runTitle = group.runTitle,
                    hasKernelArtifact = group.hasKernelArtifact(),
                    hasManagerArtifact = group.hasManagerArtifact()
                )
                val dispatchedVariantFallback = dispatchedVariantByRunId[group.runId]
                    ?: if (run?.isActive() == true &&
                        FlashWorkflowFilter.shouldUsePendingDispatchedConfig(run, group.hasKernelArtifact())
                    ) {
                        linkingDispatchedConfig?.kernelsuVariant
                    } else {
                        null
                    }
                val kKind = FlashWorkflowFilter.kernelKind(
                    summary = summary,
                    fallbackVariant = dispatchedVariantFallback
                )
                val mKind = FlashWorkflowFilter.managerKind(
                    run = run,
                    runTitle = group.runTitle,
                    remoteArtifactNames = group.remote.map { it.name },
                    localArtifactNames = group.local.map { it.name },
                    summary = summary
                )
                val workflowState = run.workflowState()
                FlashWorkflowFilter.matchesFilter(
                    primary = primary,
                    filter = filter,
                    kernelKind = kKind,
                    managerKind = mKind,
                    workflowState = workflowState
                )
            }
        }
    }

    val visibleWorkflowGroups by produceState(
        initialValue = limitWorkflowGroupsForDisplay(allWorkflowGroups, recentRunById),
        filteredGroups,
        recentRunById
    ) {
        value = withContext(Dispatchers.Default) {
            limitWorkflowGroupsForDisplay(filteredGroups, recentRunById)
        }
    }

    val shouldPrefetchWorkflowSummaries = remember(filter) {
        filter.kernelEnabled && filter.kernelKinds.isNotEmpty()
    }

    // ── LaunchedEffects ─────────────────────────────────────────────────

    // Load persisted flash filter
    LaunchedEffect(Unit) {
        if (!filterLoaded) {
            vm.loadFlashFilterJson()?.toFlashFilterOrNull()?.let { filter = it }
            filterLoaded = true
        }
    }

    // Persist flash filter changes
    LaunchedEffect(filter, filterLoaded) {
        if (filterLoaded) vm.saveFlashFilterJson(filter.toJsonString())
    }

    // Load recent runs when logged in
    LaunchedEffect(state.isLoggedIn, state.forkRepo?.fullName) {
        if (state.isLoggedIn && state.forkRepo != null) {
            vm.loadRecentRuns(showRefreshIndicator = false, lightweight = true)
        }
    }

    // Reset tab when prebuilt GKI is disabled
    LaunchedEffect(state.prebuiltGkiEnabled) {
        if (!state.prebuiltGkiEnabled) {
            activeContentTab = FlashContentTab.Workflows
        }
    }

    // Reset slot target when inactive slot not supported
    LaunchedEffect(supportsAnyKernelInactiveSlot) {
        if (!supportsAnyKernelInactiveSlot) {
            selectedAnyKernelSlotTargetName = RootUtils.Ak3SlotTarget.CURRENT.name
        }
    }

    // Load prebuilt releases when switching to prebuilt tab
    LaunchedEffect(currentContentTab, state.prebuiltGkiEnabled) {
        if (currentContentTab == FlashContentTab.PrebuiltGki && state.prebuiltGkiEnabled) {
            vm.loadPrebuiltGkiReleases()
        }
    }

    // Prefetch workflow summaries when filter requires them
    LaunchedEffect(visibleWorkflowGroups.map { it.runId }, shouldPrefetchWorkflowSummaries) {
        if (!shouldPrefetchWorkflowSummaries) return@LaunchedEffect
        delay(200)
        visibleWorkflowGroups.forEach { group ->
            val run = recentRunById[group.runId]
            if (group.shouldShowParameterDetails(run)) {
                vm.loadBuildParameterSummary(group.runId)
            }
            delay(150)
        }
    }

    // Notify parent when the screen is disposed (close detail page)
    DisposableEffect(Unit) {
        onDispose {
            onDetailPageVisibleChange(false)
        }
    }

    // ── Operational callbacks ───────────────────────────────────────────

    fun copyDownloadedFilePath(item: DownloadedArtifact) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(item.name, item.filePath))
        vm.showSnackbar(context.getString(R.string.flash_copy_path_done))
    }

    fun installManager(item: DownloadedArtifact) {
        if (!rootGranted) {
            vm.showSnackbar(context.getString(R.string.flash_root_unauthorized))
            return
        }
        navigator.push(Route.FlashTerminalLog(FlashTerminalParams(
            artifactPath = item.filePath,
            artifactName = item.name,
            artifactType = item.type.name,
            ak3SlotTarget = null,
            allowHighRiskFallback = false,
            operationTitle = context.getString(R.string.flash_install_manager_apk)
        )))
    }

    fun startFlash(
        item: DownloadedArtifact,
        anyKernelSlotTarget: RootUtils.Ak3SlotTarget = RootUtils.Ak3SlotTarget.CURRENT,
        allowHighRiskFallback: Boolean = false
    ) {
        if (!rootGranted) {
            vm.showSnackbar(context.getString(R.string.flash_root_unauthorized))
            return
        }
        navigator.push(Route.FlashTerminalLog(FlashTerminalParams(
            artifactPath = item.filePath,
            artifactName = item.name,
            artifactType = item.type.name,
            ak3SlotTarget = anyKernelSlotTarget.name,
            allowHighRiskFallback = allowHighRiskFallback,
            operationTitle = context.getString(flashOperationLabelRes(item.type))
        )))
    }

    fun requestFlash(item: DownloadedArtifact) {
        selectedItem = item
        allowLegacyBundleFallback = false
        if ((item.type == ArtifactType.KERNEL_PACKAGE || item.type == ArtifactType.KERNEL_IMG || item.type == ArtifactType.ANYKERNEL3) && !item.verified) {
            showUnverifiedFlashConfirm = true
        } else {
            showFlashConfirm = true
        }
    }

    fun requestInstallManager(item: DownloadedArtifact) {
        selectedItem = item
        showInstallManagerConfirm = true
    }

    // ── Dialogs ─────────────────────────────────────────────────────────

    if (showFlashConfirm) {
        val item = selectedItem
        if (item != null) {
            MiuixFlashConfirmDialogWithSlot(
                item = item,
                supportsAnyKernelInactiveSlot = supportsAnyKernelInactiveSlot,
                selectedSlotTarget = selectedAnyKernelSlotTarget,
                currentSlotLabel = flashAnyKernelCurrentSlotLabel,
                inactiveSlotLabel = flashAnyKernelInactiveSlotLabel,
                onSlotTargetChange = { selectedAnyKernelSlotTargetName = it.name },
                onConfirm = {
                    showFlashConfirm = false
                    startFlash(item, selectedAnyKernelSlotTarget, allowLegacyBundleFallback)
                },
                onDismiss = { showFlashConfirm = false }
            )
        }
    }

    if (showUnverifiedFlashConfirm) {
        val item = selectedItem
        if (item != null) {
            MiuixUnverifiedFlashConfirmDialog(
                summary = item.verificationSummary
                    ?: context.getString(R.string.flash_bundle_unverified_requires_confirmation),
                onConfirm = {
                    showUnverifiedFlashConfirm = false
                    allowLegacyBundleFallback = true
                    showFlashConfirm = true
                },
                onDismiss = { showUnverifiedFlashConfirm = false }
            )
        }
    }

    if (showInstallManagerConfirm) {
        val item = selectedItem
        if (item != null) {
            MiuixInstallManagerConfirmDialog(
                onConfirm = {
                    showInstallManagerConfirm = false
                    installManager(item)
                },
                onDismiss = { showInstallManagerConfirm = false }
            )
        }
    }

    cancelConfirmRunId?.let { runId ->
        MiuixCancelBuildConfirmDialog(
            onConfirm = {
                cancelConfirmRunId = null
                vm.cancelWorkflowRun(runId)
            },
            onDismiss = { cancelConfirmRunId = null }
        )
    }

    dismissingFailedRunId?.let { runId ->
        val hasFiles = hasDownloadedFilesForRun(
            runId = runId,
            downloadedArtifacts = state.downloadedArtifacts,
            workflowGroups = allWorkflowGroups,
            activeDownloadTasks = state.activeDownloadTasks,
        )
        MiuixDismissFailedRunDialog(
            hasDownloadedFiles = hasFiles,
            onConfirm = { deleteFiles ->
                vm.dismissFailedWorkflow(runId, deleteFiles)
                dismissingFailedRunId = null
            },
            onDismiss = { dismissingFailedRunId = null }
        )
    }

    deleteFileTarget?.let { item ->
        MiuixDeleteFileDialog(
            artifact = item,
            onConfirm = {
                vm.deleteDownloadedArtifact(item.filePath)
                deleteFileTarget = null
            },
            onDismiss = { deleteFileTarget = null }
        )
    }

    deleteWorkflowTarget?.let { group ->
        MiuixDeleteWorkflowDialog(
            group = group,
            hasRemote = group.remote.isNotEmpty(),
            onConfirm = { deleteRemote ->
                val targetRunId = group.runId
                vm.deleteWorkflowArtifacts(targetRunId, deleteRemote)
                deleteWorkflowTarget = null
            },
            onDismiss = { deleteWorkflowTarget = null }
        )
    }

    parameterTarget?.let { group ->
        val run = recentRunById[group.runId]
        if (!group.shouldShowParameterDetails(run)) {
            LaunchedEffect(group.runId) { parameterTarget = null }
        } else {
            val runId = group.runId
            LaunchedEffect(runId) {
                vm.loadBuildParameterSummary(runId)
            }
            val summary = state.buildParameterSummaries[runId]
            if (summary != null) {
                MiuixBuildParameterSummaryDialog(
                    summary = summary,
                    onDismiss = { parameterTarget = null }
                )
            }
        }
    }

    prebuiltParameterTarget?.let { release ->
        MiuixPrebuiltParameterSummaryDialog(
            release = release,
            onDismiss = { prebuiltParameterTarget = null }
        )
    }

    if (filterMenuExpanded) {
        MiuixFlashFilterDialog(
            filter = filter,
            onFilterChange = { filter = it },
            onDismiss = { filterMenuExpanded = false }
        )
    }

    // ── Main Scaffold ───────────────────────────────────────────────────

    Scaffold(


        topBar = {
            BlurredBar(backdrop, surfaceColor) {
                TopAppBar(
                    color = barColor,
                    title = if (rootGranted) stringResource(R.string.flash_title)
                    else stringResource(R.string.flash_files_title),
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(
                            onClick = {
                                if (currentContentTab == FlashContentTab.Workflows) {
                                    vm.loadRecentRuns()
                                } else {
                                    vm.loadPrebuiltGkiReleases(force = true)
                                }
                            },
                            enabled = if (currentContentTab == FlashContentTab.Workflows) {
                                !state.isRefreshingRecentRuns
                            } else {
                                !state.isLoadingPrebuiltGkiReleases
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.flash_refresh_artifacts),
                            )
                        }
                        if (currentContentTab == FlashContentTab.Workflows) {
                            IconButton(onClick = { filterMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = stringResource(R.string.flash_filter_title),
                                )
                            }
                        }
                    },
                    bottomContent = {
                        if (state.prebuiltGkiEnabled && state.isLoggedIn) {
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 6.dp),
                            ) {
                                val tabs = FlashContentTab.entries
                                TabRow(
                                    tabs = tabs.map { stringResource(it.labelRes) },
                                    selectedTabIndex = tabs.indexOf(activeContentTab).coerceAtLeast(0),
                                    onTabSelected = { index -> activeContentTab = tabs[index] },
                                    colors = TabRowDefaults.tabRowColors(backgroundColor = barColor),
                                    height = 40.dp,
                                )
                            }
                        }
                    },
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier.then(
                if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier
            )
        ) {
            LazyColumn(
                state = flashListScrollState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .overScrollVertical()
                    .scrollEndHaptic(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 80.dp + outerPadding.calculateBottomPadding()
                ),
                overscrollEffect = null
            ) {
            // ── Content area ────────────────────────────────────────────
            if (currentContentTab == FlashContentTab.Workflows) {
                    item(key = "workflow-header") {
                        MiuixWorkflowListHeader(
                            availableCount = remoteArtifacts.size,
                            downloadedCount = workflowDownloadedArtifacts.size,
                        )
                    }

                    // Active downloads card
                    if (workflowActiveDownloads.isNotEmpty() || state.pendingAutoDownloadRunId > 0L) {
                        item(key = "downloads") {
                            MiuixWorkflowDownloadManagementCard(
                                tasks = workflowActiveDownloads,
                                pendingRunId = state.pendingAutoDownloadRunId.takeIf { it > 0L },
                                pendingRunLabel = pendingAutoDownloadRun?.let(::workflowRunLabel)
                                    ?: state.pendingAutoDownloadRunId
                                        .takeIf { it > 0L }
                                        ?.let { "#$it" },
                                onCancelTask = vm::cancelDownload,
                                onCancelPending = vm::cancelAutoDownloads
                            )
                        }
                    }

                    // Workflow run cards or empty state
                    when {
                        visibleWorkflowGroups.isNotEmpty() -> {
                            items(
                                visibleWorkflowGroups,
                                key = { "workflow-${it.runId}" }
                            ) { group ->
                                val run = recentRunById[group.runId]
                                val active = run?.isActive() == true
                                val dispatchedConfig = dispatchedConfigByRunId[group.runId]
                                    ?: if (active && FlashWorkflowFilter.shouldUsePendingDispatchedConfig(
                                            run,
                                            group.hasKernelArtifact()
                                        )
                                    ) {
                                        linkingDispatchedConfig
                                    } else {
                                        null
                                    }
                                val isManagerPrimary = FlashWorkflowFilter.primaryKind(
                                    run = run,
                                    runTitle = group.runTitle,
                                    hasKernelArtifact = group.hasKernelArtifact(),
                                    hasManagerArtifact = group.hasManagerArtifact()
                                ) == WorkflowPrimary.Manager
                                val showParameterDetails = group.shouldShowParameterDetails(run)
                                val failedGhost = group.runId in state.sessionGhostFailedRuns &&
                                    group.runId !in state.dismissedFailedRunIds
                                MiuixWorkflowRunCard(
                                    runId = group.runId,
                                    runTitle = group.runTitle,
                                    runNumber = group.runNumber,
                                    runCreatedAt = group.runCreatedAt,
                                    sourceCount = group.remote.size,
                                    downloadedCount = group.local.size,
                                    categories = group.categories,
                                    summary = state.buildParameterSummaries[group.runId],
                                    showKernelBuildChips = !isManagerPrimary,
                                    dispatchedKernelVariant = dispatchedConfig?.kernelsuVariant,
                                    dispatchedSusfsEnabled = dispatchedConfig?.let { !it.cancelSusfs },
                                    active = active,
                                    failedGhost = failedGhost,
                                    cancelling = group.runId in state.cancellingWorkflowRunIds,
                                    onClick = {
                                        if (failedGhost) {
                                            dismissingFailedRunId = group.runId
                                        } else {
                                            navigator.push(Route.FlashWorkflowDetail(group.runId))
                                            onDetailPageVisibleChange(true)
                                        }
                                    },
                                    onDelete = {
                                        if (failedGhost) {
                                            dismissingFailedRunId = group.runId
                                        } else {
                                            deleteWorkflowTarget = group
                                        }
                                    },
                                    onCancel = { cancelConfirmRunId = group.runId }
                                )
                            }
                        }
                        allWorkflowGroups.isNotEmpty() -> {
                            item(key = "filter-empty") {
                                MiuixEmptyState(
                                    title = stringResource(R.string.flash_filter_empty),
                                    subtitle = "",
                                    icon = Icons.Default.FilterList
                                )
                            }
                        }
                        else -> {
                            item(key = "empty") {
                                MiuixEmptyState(
                                    title = if (rootGranted) {
                                        stringResource(R.string.flash_empty_flash_title)
                                    } else {
                                        stringResource(R.string.flash_empty_files_title)
                                    },
                                    subtitle = if (rootGranted) {
                                        stringResource(R.string.flash_empty_flash_desc)
                                    } else {
                                        stringResource(R.string.flash_empty_files_desc)
                                    },
                                    icon = Icons.Default.Inbox
                                )
                            }
                        }
                    }
            }

            if (currentContentTab == FlashContentTab.PrebuiltGki) {
                    if (state.prebuiltGkiEnabled) {
                        // Prebuilt release list header
                        item(key = "prebuilt-header") {
                            MiuixPrebuiltReleaseListHeader(
                                releaseCount = state.prebuiltGkiReleases.size,
                            )
                        }

                        when {
                            state.isLoadingPrebuiltGkiReleases -> {
                                item(key = "prebuilt-loading") {
                                    MiuixLoadingRow(stringResource(R.string.flash_loading_release))
                                }
                            }
                            state.prebuiltGkiReleases.isEmpty() -> {
                                item(key = "prebuilt-empty") {
                                    MiuixEmptyState(
                                        title = stringResource(R.string.flash_empty_prebuilt_title),
                                        subtitle = stringResource(R.string.flash_empty_prebuilt_desc),
                                        icon = Icons.Default.CloudDownload
                                    )
                                }
                            }
                            else -> {
                                items(
                                    state.prebuiltGkiReleases,
                                    key = { "release-${it.id}" }
                                ) { release ->
                                    MiuixPrebuiltReleaseCard(
                                        release = release,
                                        onClick = {
                                            navigator.push(Route.FlashPrebuiltDetail(release.id))
                                            onDetailPageVisibleChange(true)
                                        }
                                    )
                                }
                            }
                        }

                        // Local prebuilt files section
                        val localPrebuiltFiles = state.downloadedArtifacts.filter {
                            it.runId == PREBUILT_GKI_RUN_ID
                        }
                        if (localPrebuiltFiles.isNotEmpty()) {
                            item(key = "prebuilt-local-header") {
                                MiuixCategoryHeader(ArtifactCategory.KERNEL)
                            }
                            items(
                                localPrebuiltFiles,
                                key = { "prebuilt-local-${it.filePath}" }
                            ) { artifact ->
                                MiuixLocalOnlyArtifactCard(
                                    artifact = artifact,
                                    onCopyPath = ::copyDownloadedFilePath,
                                    onInstall = ::requestInstallManager,
                                    onFlash = ::requestFlash,
                                    onDelete = { deleteFileTarget = it },
                                    allowRootActions = rootGranted
                                )
                            }
                        }
                    } else {
                        item(key = "prebuilt-disabled") {
                            MiuixEmptyState(
                                title = stringResource(R.string.flash_prebuilt_disabled_title),
                                subtitle = stringResource(R.string.flash_prebuilt_disabled_desc),
                                icon = Icons.Default.CloudOff
                            )
                        }
                    }
            }

            // Bottom spacer
            item(key = "bottom-spacer") {
                Spacer(Modifier.height(24.dp))
            }
        }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal MIUIX components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MiuixWorkflowListHeader(
    availableCount: Int,
    downloadedCount: Int,
) {
    SmallTitle(
        text = buildString {
            append(stringResource(R.string.flash_tab_workflows))
            append(" · ")
            append(stringResource(R.string.flash_artifact_counts, availableCount, downloadedCount))
        },
        insideMargin = PaddingValues(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 0.dp),
    )
}

/**
 * Empty/loading state component for MIUIX flash screen.
 */
@Composable
private fun MiuixEmptyState(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = title,
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    }
}

@Composable
private fun MiuixLoadingRow(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                progress = null,
                size = 22.dp,
                strokeWidth = 2.dp
            )
            Text(
                text = text,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Category header for local-only artifact sections.
 */
@Composable
private fun MiuixCategoryHeader(category: ArtifactCategory) {
    val iconName = when (category) {
        ArtifactCategory.KERNEL -> Icons.Default.Memory
        ArtifactCategory.MANAGER -> Icons.Default.Shield
        ArtifactCategory.MODULE -> Icons.Default.Extension
    }
    val labelRes = when (category) {
        ArtifactCategory.KERNEL -> R.string.flash_category_kernel
        ArtifactCategory.MANAGER -> R.string.flash_category_manager
        ArtifactCategory.MODULE -> R.string.flash_category_module
    }
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = iconName,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = stringResource(labelRes),
            style = MiuixTheme.textStyles.subtitle,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

/**
 * Prebuilt release list header with refresh action.
 */
@Composable
private fun MiuixPrebuiltReleaseListHeader(
    releaseCount: Int,
) {
    SmallTitle(
        text = if (releaseCount > 0) {
            "${stringResource(R.string.flash_prebuilt_gki)} · ${stringResource(R.string.flash_asset_count, releaseCount)}"
        } else {
            stringResource(R.string.flash_prebuilt_gki)
        },
        insideMargin = PaddingValues(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 0.dp),
    )
}

/**
 * MIUIX-styled card for a Prebuilt GKI release in the list.
 * Tapping navigates to the release detail page.
 */
@Composable
private fun MiuixPrebuiltReleaseCard(
    release: PrebuiltGkiRelease,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ArrowPreference(
            title = release.name,
            summary = "${release.tagName} · ${releaseDateLabel(
                release.publishedAt,
                stringResource(R.string.flash_unknown_date),
            )}",
            startAction = {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            },
            endActions = {
                if (release.assetCount > 0) {
                    Text(
                        text = stringResource(R.string.flash_asset_count, release.assetCount),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                }
            },
            onClick = onClick,
        )
    }
}

/**
 * Flash confirm dialog with optional AnyKernel3 slot selection.
 * Shows radio buttons for current/inactive slot when the device supports it
 * and the artifact is an AnyKernel3 zip.
 */
@Composable
private fun MiuixFlashConfirmDialogWithSlot(
    item: DownloadedArtifact,
    supportsAnyKernelInactiveSlot: Boolean,
    selectedSlotTarget: RootUtils.Ak3SlotTarget,
    currentSlotLabel: String,
    inactiveSlotLabel: String,
    onSlotTargetChange: (RootUtils.Ak3SlotTarget) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val showSlotOption = item.type == ArtifactType.ANYKERNEL3 && supportsAnyKernelInactiveSlot
    if (!showSlotOption) {
        MiuixFlashConfirmDialog(
            onConfirm = onConfirm,
            onDismiss = onDismiss
        )
        return
    }
    WindowDialog(
        show = true,
        title = stringResource(R.string.flash_confirm),
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.flash_confirm_msg),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.root_patch_ak3_slot_title),
                        style = MiuixTheme.textStyles.subtitle,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSlotTargetChange(RootUtils.Ak3SlotTarget.CURRENT) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            state = ToggleableState(selectedSlotTarget == RootUtils.Ak3SlotTarget.CURRENT),
                            onClick = { onSlotTargetChange(RootUtils.Ak3SlotTarget.CURRENT) }
                        )
                        Text(
                            text = currentSlotLabel,
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSlotTargetChange(RootUtils.Ak3SlotTarget.INACTIVE) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            state = ToggleableState(selectedSlotTarget == RootUtils.Ak3SlotTarget.INACTIVE),
                            onClick = { onSlotTargetChange(RootUtils.Ak3SlotTarget.INACTIVE) }
                        )
                        Text(
                            text = inactiveSlotLabel,
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss
                )
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.flash_confirm),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = onConfirm
                )
            }
        }
    }
}

@Composable
private fun MiuixUnverifiedFlashConfirmDialog(
    summary: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    WindowDialog(
        show = true,
        title = stringResource(R.string.flash_confirm),
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss
                )
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.flash_confirm),
                    colors = ButtonDefaults.textButtonColors(
                        textColor = MiuixTheme.colorScheme.error
                    ),
                    onClick = onConfirm
                )
            }
        }
    }
}

@Composable
private fun MiuixFlashFilterDialog(
    filter: FlashFilter,
    onFilterChange: (FlashFilter) -> Unit,
    onDismiss: () -> Unit
) {
    WindowDialog(
        show = true,
        title = stringResource(R.string.flash_filter_title),
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MiuixFilterCheckRow(
                        label = stringResource(R.string.flash_filter_kernel),
                        checked = filter.kernelEnabled,
                        onCheckedChange = { onFilterChange(filter.copy(kernelEnabled = it)) }
                    )
                    FlashFilterKernelKind.entries.forEach { kind ->
                        MiuixFilterCheckRow(
                            label = stringResource(kind.labelRes()),
                            checked = kind in filter.kernelKinds,
                            indent = true,
                            onCheckedChange = { add ->
                                onFilterChange(
                                    filter.copy(
                                        kernelKinds = if (add) filter.kernelKinds + kind
                                        else filter.kernelKinds - kind
                                    )
                                )
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    FlashFilterWorkflowState.entries.forEach { st ->
                        MiuixFilterCheckRow(
                            label = stringResource(st.labelRes()),
                            checked = st in filter.workflowStates,
                            onCheckedChange = { add ->
                                onFilterChange(
                                    filter.copy(
                                        workflowStates = if (add) filter.workflowStates + st
                                        else filter.workflowStates - st
                                    )
                                )
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.close),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = onDismiss
                )
            }
        }
    }
}

@Composable
private fun MiuixFilterCheckRow(
    label: String,
    checked: Boolean,
    indent: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(start = if (indent) 24.dp else 0.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            state = ToggleableState(checked),
            onClick = { onCheckedChange(!checked) }
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface
        )
    }
}
