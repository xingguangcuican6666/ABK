@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens

import com.abk.kernel.ui.screens.flash.*
import android.content.Intent
import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RunCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.abk.kernel.R
import com.abk.kernel.data.model.ActiveDownloadTask
import com.abk.kernel.data.model.ArtifactCategory
import com.abk.kernel.data.model.ArtifactType
import com.abk.kernel.data.model.BuildArtifact
import com.abk.kernel.data.model.BuildParameterSummary
import com.abk.kernel.data.model.BuildProgress
import com.abk.kernel.utils.BuildProgressUtils
import com.abk.kernel.data.model.BuildQueueItemStatus
import com.abk.kernel.data.model.BuildStatus
import com.abk.kernel.data.model.DownloadedArtifact
import com.abk.kernel.data.model.KernelBuildConfig
import com.abk.kernel.data.model.KernelSupport
import com.abk.kernel.data.model.PREBUILT_GKI_RUN_ID
import com.abk.kernel.data.model.PrebuiltGkiAsset
import com.abk.kernel.data.model.PrebuiltGkiRelease
import com.abk.kernel.data.model.WorkflowJob
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.data.model.WorkflowStep
import com.abk.kernel.data.model.isActive
import com.abk.kernel.data.model.isFailedFlashRun
import com.abk.kernel.utils.FlashFilter
import com.abk.kernel.utils.FlashFilterKernelKind
import com.abk.kernel.utils.FlashFilterManagerKind
import com.abk.kernel.utils.FlashFilterWorkflowState
import com.abk.kernel.utils.FlashWorkflowFilter
import com.abk.kernel.utils.WorkflowPrimary
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.ObserveChildPageVisibility
import com.abk.kernel.ui.components.childPageOverlayEnterTransition
import com.abk.kernel.ui.components.childPageOverlayExitTransition
import com.abk.kernel.ui.components.childPageScrimExitTransition
import com.abk.kernel.ui.components.rememberChildPageBackController
import com.abk.kernel.ui.components.rememberChildPageOverlayTransition
import com.abk.kernel.utils.FailureLogExtractor
import com.abk.kernel.ui.components.LIVE_DURATION_MINUTE_HAND_PERIOD_MS
import com.abk.kernel.ui.components.LiveDurationScheduleIcon
import com.abk.kernel.ui.components.ShimmerLinearProgress
import com.abk.kernel.ui.components.liveWorkflowShimmerBrush
import com.abk.kernel.ui.components.rememberLiveWorkflowShimmerPhase
import com.abk.kernel.ui.components.MinuteHandController
import com.abk.kernel.ui.components.MinuteHandControllerHost
import com.abk.kernel.ui.components.MinuteHandPhase
import com.abk.kernel.ui.components.ExpressiveEmptyState
import com.abk.kernel.ui.components.ExpressiveHeroCard
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.RootUtils
import java.io.File
import com.abk.kernel.viewmodel.MainViewModel
import com.abk.kernel.viewmodel.mergeWorkflowActiveDownloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    // NavHost has no back-stack entry for a frame on first composition — treat
    // null as the list route so we do not flash childPageVisible on tab open.
    val flashDetailRouteActive = isFlashDetailRoute(currentBackStackEntry?.destination?.route)
    var navigatingToFlashDetail by remember { mutableStateOf(false) }
    var selectedRunId by remember { mutableStateOf<Long?>(null) }
    var selectedPrebuiltReleaseId by remember { mutableStateOf<Long?>(null) }
    var selectedItem by remember { mutableStateOf<DownloadedArtifact?>(null) }
    var deleteFileTarget by remember { mutableStateOf<DownloadedArtifact?>(null) }
    var deleteWorkflowTarget by remember { mutableStateOf<WorkflowArtifactGroup?>(null) }
    var parameterTarget by remember { mutableStateOf<WorkflowArtifactGroup?>(null) }
    var prebuiltParameterTarget by remember { mutableStateOf<PrebuiltGkiRelease?>(null) }
    var deleteRemoteWorkflowRun by remember { mutableStateOf(false) }
    var showFlashConfirm by remember { mutableStateOf(false) }
    var showInstallManagerConfirm by remember { mutableStateOf(false) }
    var cancelConfirmRunId by remember { mutableStateOf<Long?>(null) }
    var showTerminal by remember { mutableStateOf(false) }
    var selectedAnyKernelSlotTargetName by rememberSaveable {
        mutableStateOf(RootUtils.Ak3SlotTarget.CURRENT.name)
    }
    var terminalTitle by remember { mutableStateOf(context.getString(R.string.flash_terminal)) }
    var terminalCanReboot by remember { mutableStateOf(false) }
    var terminalRunning by remember { mutableStateOf(false) }
    var terminalLog by remember { mutableStateOf<List<String>>(emptyList()) }
    var terminalSuccess by remember { mutableStateOf<Boolean?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
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
    val selectedAnyKernelSlotTarget = runCatching {
        RootUtils.Ak3SlotTarget.valueOf(selectedAnyKernelSlotTargetName)
    }.getOrDefault(RootUtils.Ak3SlotTarget.CURRENT)
    val flashAnyKernelCurrentSlotLabel = stringResource(R.string.root_patch_ak3_slot_current)
    val flashAnyKernelInactiveSlotLabel = stringResource(R.string.root_patch_ak3_slot_inactive)
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
    var ghostFailedSheetRunId by remember { mutableStateOf<Long?>(null) }
    val ghostFailedVisible = ghostFailedSheetRunId != null
    val ghostFailedPageTransition = rememberChildPageOverlayTransition(
        visible = ghostFailedVisible,
        label = "flash-failed-workflow",
    )
    val flashListScrollState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
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
    var filter by rememberSaveable(stateSaver = FlashFilterSaver) { mutableStateOf(FlashFilter()) }
    var dismissingFailedRunId by remember { mutableStateOf<Long?>(null) }
    // rememberSaveable survives rotation/savedInstanceState but not process
    // death. Persist to DataStore so the filter choice carries across cold
    // starts. Gate the auto-save on `filterLoaded` so the dispatched default
    // FlashFilter() doesn't overwrite the persisted value before the load
    // coroutine finishes.
    var filterLoaded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!filterLoaded) {
            vm.loadFlashFilterJson()?.toFlashFilterOrNull()?.let { filter = it }
            filterLoaded = true
        }
    }
    LaunchedEffect(filter, filterLoaded) {
        if (filterLoaded) vm.saveFlashFilterJson(filter.toJsonString())
    }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    val dispatchedConfigByRunId = remember(state.buildQueue) {
        state.buildQueue
            .filter { it.runId > 0L }
            .associate { it.runId to it.config }
    }
    // While a build is mid-dispatch (queue item exists but runId hasn't been
    // assigned yet via findAndMonitorLatestRun) we can't key by runId, but we
    // still want the kernel / SUSFS chips to reflect the dispatched config.
    // Use the most recent DISPATCHING/RUNNING queue item as a fallback for
    // active runs whose runId isn't in the map yet.
    // Only the workflow currently being linked (DISPATCHING, no runId yet) may
    // borrow queue config. Using the first RUNNING item picked the still-active
    // kernel build when a manager workflow started afterward.
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
    // Summary prefetch is only needed when an active filter depends on summary
    // data. Avoiding eager log fetches keeps the workflow list smooth.
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
    val selectedGroup = selectedRunId?.let { id -> allWorkflowGroups.firstOrNull { it.runId == id } }
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

    val closeGhostFailedWorkflow: () -> Unit = {
        val dismissedRunId = ghostFailedSheetRunId
        ghostFailedSheetRunId = null
        if (dismissedRunId != null && flashDetailRouteActive && selectedRunId == dismissedRunId) {
            returnToWorkflowList()
        }
    }
    val ghostFailedPageBack = rememberChildPageBackController(
        enabled = ghostFailedVisible,
        predictiveBackEnabled = state.predictiveBackEnabled,
        onBack = closeGhostFailedWorkflow,
    )

    ObserveChildPageVisibility(
        visible = flashDetailRouteActive,
        onVisibleChange = { detailVisible ->
            onDetailPageVisibleChange(detailVisible || ghostFailedPageTransition.currentState)
        },
    )

    ObserveChildPageVisibility(
        transition = ghostFailedPageTransition,
        onVisibleChange = { failedVisible ->
            onDetailPageVisibleChange(flashDetailRouteActive || failedVisible)
        },
        onAfterExitAnimation = { ghostFailedPageBack.resetProgress() },
    )

    LaunchedEffect(flashDetailRouteActive) {
        if (flashDetailRouteActive) navigatingToFlashDetail = false
    }

    BackHandler(enabled = navigatingToFlashDetail) {
        navigatingToFlashDetail = false
        if (flashDetailRouteActive) {
            navController.popBackStack()
        } else {
            onDetailPageVisibleChange(ghostFailedPageTransition.currentState)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            navigatingToFlashDetail = false
            onDetailPageVisibleChange(false)
        }
    }

    LaunchedEffect(state.isLoggedIn, state.forkRepo?.fullName) {
        if (state.isLoggedIn && state.forkRepo != null) {
            vm.loadRecentRuns(showRefreshIndicator = false, lightweight = true)
        }
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

    LaunchedEffect(supportsAnyKernelInactiveSlot) {
        if (!supportsAnyKernelInactiveSlot) {
            selectedAnyKernelSlotTargetName = RootUtils.Ak3SlotTarget.CURRENT.name
        }
    }

    LaunchedEffect(currentContentTab, state.prebuiltGkiEnabled) {
        if (currentContentTab == FlashContentTab.PrebuiltGki && state.prebuiltGkiEnabled) {
            vm.loadPrebuiltGkiReleases()
        }
    }

    LaunchedEffect(
        allWorkflowGroups,
        selectedRunId,
        state.sessionGhostFailedRuns,
        state.dismissedFailedRunIds,
        recentRunById,
        flashDetailRouteActive,
    ) {
        val runId = selectedRunId ?: return@LaunchedEffect
        if (selectedGroup != null) return@LaunchedEffect
        if (runId in state.dismissedFailedRunIds) {
            returnToTopList()
            return@LaunchedEffect
        }
        if (runId in state.sessionGhostFailedRuns) return@LaunchedEffect
        val run = recentRunById[runId]
        if (run?.isFailedFlashRun() == true) return@LaunchedEffect
        if (flashDetailRouteActive && run != null) return@LaunchedEffect
        returnToTopList()
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
        Toast.makeText(context, context.getString(R.string.flash_copy_path_done), Toast.LENGTH_SHORT).show()
    }

    fun appendTerminalOutput(line: String) {
        scope.launch(Dispatchers.Main.immediate) {
            terminalLog = terminalLog + line
        }
    }

    fun requestInstallManager(item: DownloadedArtifact) {
        selectedItem = item
        showInstallManagerConfirm = true
    }

    suspend fun executeWithPreparedArtifact(
        item: DownloadedArtifact,
        block: (File) -> RootUtils.ShellResult
    ): RootUtils.ShellResult = withContext(Dispatchers.IO) {
        val prepared = DownloadUtils.prepareDownloadedArtifact(context, item)
        try {
            if (prepared.cleanupDir != null) {
                appendTerminalOutput("[ABK] 已解包下载包到缓存目录")
                appendTerminalOutput("[ABK] Payload: ${prepared.file.absolutePath}")
            }
            block(prepared.file)
        } finally {
            prepared.cleanupDir?.deleteRecursively()
        }
    }

    fun installManager(item: DownloadedArtifact) {
        if (!rootGranted) {
            showFailure(
                context.getString(R.string.flash_root_unauthorized),
                listOf(
                    "${'$'} pm install -r ${item.name}",
                    context.getString(R.string.flash_partial_files_only),
                    context.getString(R.string.flash_grant_root_install_manager)
                )
            )
            return
        }
        terminalTitle = context.getString(R.string.flash_install_manager_apk)
        terminalCanReboot = false
        terminalRunning = true
        terminalSuccess = null
        terminalLog = listOf(
            "${'$'} pm install -r ${item.name}",
            "file: ${item.filePath}",
            "",
            context.getString(R.string.flash_wait_root_shell)
        )
        showTerminal = true
        scope.launch {
            val result = runCatching {
                executeWithPreparedArtifact(item) { preparedFile ->
                    RootUtils.installApk(context, preparedFile.absolutePath, ::appendTerminalOutput)
                }
            }.getOrElse { error ->
                RootUtils.ShellResult(false, listOf(error.message ?: error::class.java.simpleName))
            }
            terminalRunning = false
            terminalSuccess = result.success
            terminalLog = listOf(
                "${'$'} pm install -r ${item.name}",
                "file: ${item.filePath}",
                ""
            ) + result.output.ifEmpty {
                listOf(
                    if (result.success) {
                        context.getString(R.string.flash_command_done_no_output)
                    } else {
                        context.getString(R.string.flash_command_failed_no_log)
                    }
                )
            }
        }
    }

    fun startFlash(
        item: DownloadedArtifact,
        anyKernelSlotTarget: RootUtils.Ak3SlotTarget = RootUtils.Ak3SlotTarget.CURRENT
    ) {
        if (!rootGranted) {
            showFailure(
                context.getString(R.string.flash_root_unauthorized),
                listOf(
                    "${'$'} ${flashCommandPreview(item, anyKernelSlotTarget)}",
                    context.getString(R.string.flash_partial_files_only),
                    context.getString(R.string.flash_grant_root_flash)
                )
            )
            return
        }
        terminalTitle = context.getString(flashOperationLabelRes(item.type))
        terminalCanReboot = true
        terminalRunning = true
        terminalSuccess = null
        val slotLog = if (item.type == ArtifactType.ANYKERNEL3) {
            listOf(
                context.getString(
                    R.string.root_patch_log_slot,
                    when (anyKernelSlotTarget) {
                        RootUtils.Ak3SlotTarget.INACTIVE -> flashAnyKernelInactiveSlotLabel
                        RootUtils.Ak3SlotTarget.CURRENT -> flashAnyKernelCurrentSlotLabel
                    }
                )
            )
        } else {
            emptyList()
        }
        terminalLog = listOf(
            "${'$'} ${flashCommandPreview(item, anyKernelSlotTarget)}",
            "file: ${item.filePath}",
        ) + slotLog + listOf(
            "",
            context.getString(R.string.flash_wait_root_shell)
        )
        showTerminal = true
        scope.launch {
            val result = runCatching {
                executeWithPreparedArtifact(item) { preparedFile ->
                    when (item.type) {
                        ArtifactType.KERNEL_IMG -> RootUtils.flashImage(preparedFile.absolutePath, onOutput = ::appendTerminalOutput)
                        ArtifactType.ANYKERNEL3 -> RootUtils.flashAnyKernel3(
                            context,
                            preparedFile.absolutePath,
                            targetSlot = anyKernelSlotTarget,
                            onOutput = ::appendTerminalOutput
                        )
                        ArtifactType.SUSFS_MODULE -> RootUtils.installModule(preparedFile.absolutePath, ::appendTerminalOutput)
                        ArtifactType.KSU_MANAGER -> RootUtils.installApk(context, preparedFile.absolutePath, ::appendTerminalOutput)
                        ArtifactType.ABK_MANAGER ->
                            RootUtils.ShellResult(false, listOf(context.getString(R.string.flash_unsupported_auto_flash)))
                        else -> RootUtils.ShellResult(false, listOf(context.getString(R.string.flash_unsupported_auto_flash)))
                    }
                }
            }.getOrElse { error ->
                RootUtils.ShellResult(false, listOf(error.message ?: error::class.java.simpleName))
            }
            terminalRunning = false
            terminalSuccess = result.success
            terminalLog = listOf(
                "${'$'} ${flashCommandPreview(item, anyKernelSlotTarget)}",
                "file: ${item.filePath}",
            ) + slotLog + listOf(
                ""
            ) + result.output.ifEmpty {
                listOf(
                    if (result.success) {
                        context.getString(R.string.flash_command_done_no_output)
                    } else {
                        context.getString(R.string.flash_command_failed_no_log)
                    }
                )
            }
        }
    }

    if (showFlashConfirm) {
        val item = selectedItem
        if (item != null) {
        AlertDialog(
            onDismissRequest = { showFlashConfirm = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.flash_confirm)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.flash_confirm_msg))
                    if (item.type == ArtifactType.ANYKERNEL3 && supportsAnyKernelInactiveSlot) {
                        Text(
                            text = stringResource(R.string.root_patch_ak3_slot_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedAnyKernelSlotTargetName = RootUtils.Ak3SlotTarget.CURRENT.name
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedAnyKernelSlotTarget == RootUtils.Ak3SlotTarget.CURRENT,
                                onClick = {
                                    selectedAnyKernelSlotTargetName = RootUtils.Ak3SlotTarget.CURRENT.name
                                }
                            )
                            Text(flashAnyKernelCurrentSlotLabel)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedAnyKernelSlotTargetName = RootUtils.Ak3SlotTarget.INACTIVE.name
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedAnyKernelSlotTarget == RootUtils.Ak3SlotTarget.INACTIVE,
                                onClick = {
                                    selectedAnyKernelSlotTargetName = RootUtils.Ak3SlotTarget.INACTIVE.name
                                }
                            )
                            Text(flashAnyKernelInactiveSlotLabel)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFlashConfirm = false
                        startFlash(item, selectedAnyKernelSlotTarget)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.flash_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showFlashConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
        }
    }

    if (showInstallManagerConfirm) {
        val item = selectedItem
        if (item != null) {
        AlertDialog(
            onDismissRequest = { showInstallManagerConfirm = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.flash_confirm_install_manager)) },
            text = { Text(stringResource(R.string.flash_confirm_install_manager_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        showInstallManagerConfirm = false
                        installManager(item)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.flash_confirm_install_manager)) }
            },
            dismissButton = {
                TextButton(onClick = { showInstallManagerConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
        }
    }

    // Cancel-build confirmation dialog. Styled to match the flash confirm
    // above (Warning icon, error-tinted confirm). Tapping the big cancel
    // button inside the in-progress workflow detail surfaces this rather
    // than firing the cancel request immediately.
    cancelConfirmRunId?.let { confirmRunId ->
        AlertDialog(
            onDismissRequest = { cancelConfirmRunId = null },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.flash_cancel_confirm_title)) },
            text = { Text(stringResource(R.string.flash_cancel_confirm_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        cancelConfirmRunId = null
                        vm.cancelWorkflowRun(confirmRunId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.flash_cancel_confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { cancelConfirmRunId = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    dismissingFailedRunId?.let { runId ->
        val hasFiles = hasDownloadedFilesForRun(
            runId = runId,
            downloadedArtifacts = state.downloadedArtifacts,
            workflowGroups = allWorkflowGroups,
            activeDownloadTasks = state.activeDownloadTasks,
        )
        DismissFailedRunDialog(
            hasDownloadedFiles = hasFiles,
            onConfirm = { deleteFiles ->
                vm.dismissFailedWorkflow(runId, deleteFiles)
                dismissingFailedRunId = null
                if (ghostFailedSheetRunId == runId) {
                    ghostFailedSheetRunId = null
                }
                if (selectedRunId == runId) {
                    returnToWorkflowList()
                }
            },
            onDismiss = { dismissingFailedRunId = null },
        )
    }

    deleteFileTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteFileTarget = null },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.flash_delete_file)) },
            text = { Text(stringResource(R.string.flash_delete_file_msg, item.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteDownloadedArtifact(item.filePath)
                        deleteFileTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete)) }
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
            title = {
                Text(
                    if (group.runId == PREBUILT_GKI_RUN_ID) {
                        stringResource(R.string.flash_delete_prebuilt_files)
                    } else {
                        stringResource(R.string.flash_delete_workflow_record)
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (group.runId == PREBUILT_GKI_RUN_ID) {
                            stringResource(R.string.flash_delete_prebuilt_msg)
                        } else {
                            stringResource(
                                R.string.flash_delete_workflow_msg,
                                if (group.runNumber > 0) "#${group.runNumber}" else "#${group.runId}"
                            )
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
                            Text(stringResource(R.string.flash_delete_remote_workflow))
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
                ) { Text(stringResource(R.string.delete)) }
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
        val run = recentRunById[group.runId]
        if (!group.shouldShowParameterDetails(run)) {
            LaunchedEffect(group.runId) { parameterTarget = null }
        } else {
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
    }

    prebuiltParameterTarget?.let { release ->
        PrebuiltParameterSummaryDialog(
            release = release,
            summary = remember(release.id, release.body) { parsePrebuiltGkiParameterSummary(release) },
            onDismiss = { prebuiltParameterTarget = null }
        )
    }

    BackHandler(enabled = ghostFailedVisible) {
        ghostFailedPageBack.requestDismiss()
    }

    @Composable
    fun FlashListContent(listScrollState: LazyListState) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                ExpressiveTopBar(
                    title = if (rootGranted) stringResource(R.string.flash_title) else stringResource(R.string.flash_files_title),
                    scrollBehavior = scrollBehavior
                )
            }
        ) { padding ->
            LazyColumn(
                state = listScrollState,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = AbkScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 96.dp + outerPadding.calculateBottomPadding())
            ) {
                item {
                    FlashHero(
                        buildStatus = state.buildStatus,
                        availableCount = remoteArtifacts.size,
                        downloadedCount = workflowDownloadedArtifacts.size,
                        rootGranted = rootGranted
                    )
                }

                if (state.prebuiltGkiEnabled && state.isLoggedIn) {
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { vm.loadRecentRuns() },
                                    modifier = Modifier.weight(1f),
                                    enabled = !state.isRefreshingRecentRuns
                                ) {
                                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.flash_refresh_artifacts))
                                }
                                FlashFilterButton(
                                    expanded = filterMenuExpanded,
                                    onExpandedChange = { filterMenuExpanded = it },
                                    filter = filter,
                                    onFilterChange = { filter = it }
                                )
                            }
                        }

                        if (workflowActiveDownloads.isNotEmpty() || state.pendingAutoDownloadRunId > 0L) {
                            item {
                                WorkflowDownloadManagementCard(
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

                        when {
                            visibleWorkflowGroups.isNotEmpty() -> {
                                items(visibleWorkflowGroups, key = { "workflow-${it.runId}" }) { group ->
                                    val run = recentRunById[group.runId]
                                    val active = run?.isActive() == true
                                    // Per-run dispatched config drives the kernel-kind + SUSFS chips.
                                    // Only fall back to pendingDispatchedConfig for runs that ARE
                                    // kernel-named (or have kernel artifacts) — otherwise the chip
                                    // leaks the dispatched ReSuKiSU variant onto Build ABK App /
                                    // GetManager / Auto Trigger runs, making them visually
                                    // indistinguishable from kernel builds in the Manager filter.
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
                                    WorkflowRunCard(
                                        group = group,
                                        summary = state.buildParameterSummaries[group.runId],
                                        showKernelBuildChips = !isManagerPrimary,
                                        showParameterDetails = showParameterDetails,
                                        dispatchedKernelVariant = dispatchedConfig?.kernelsuVariant,
                                        dispatchedSusfsEnabled = dispatchedConfig?.let { !it.cancelSusfs },
                                        active = active,
                                        failedGhost = failedGhost,
                                        cancelling = group.runId in state.cancellingWorkflowRunIds,
                                        onClick = {
                                            if (failedGhost) {
                                                ghostFailedPageBack.resetProgress()
                                                ghostFailedSheetRunId = group.runId
                                            } else {
                                                selectedRunId = group.runId
                                                selectedPrebuiltReleaseId = null
                                                onDetailPageVisibleChange(true)
                                                navigatingToFlashDetail = true
                                                navController.navigate(flashWorkflowRoute(group.runId))
                                            }
                                        },
                                        onShowParameters = {
                                            if (showParameterDetails) parameterTarget = group
                                        },
                                        onDelete = {
                                            if (failedGhost) {
                                                dismissingFailedRunId = group.runId
                                            } else {
                                                deleteWorkflowTarget = group
                                                deleteRemoteWorkflowRun = false
                                            }
                                        },
                                        onCancel = { vm.cancelWorkflowRun(group.runId) }
                                    )
                                }
                            }
                            allWorkflowGroups.isNotEmpty() -> {
                                item {
                                    ExpressiveEmptyState(
                                        title = stringResource(R.string.flash_filter_empty),
                                        subtitle = "",
                                        icon = Icons.Default.FilterList
                                    )
                                }
                            }
                            else -> {
                                item {
                                    ExpressiveEmptyState(
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
                                        LoadingRow(stringResource(R.string.flash_loading_release))
                                    }
                                }
                                state.prebuiltGkiReleases.isEmpty() -> {
                                    item {
                                        ExpressiveEmptyState(
                                            title = stringResource(R.string.flash_empty_prebuilt_title),
                                            subtitle = stringResource(R.string.flash_empty_prebuilt_desc),
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
                                                onDetailPageVisibleChange(true)
                                                navigatingToFlashDetail = true
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
                                        onInstall = ::requestInstallManager,
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
                                    title = stringResource(R.string.flash_prebuilt_disabled_title),
                                    subtitle = stringResource(R.string.flash_prebuilt_disabled_desc),
                                    icon = Icons.Default.CloudOff
                                )
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

    val ghostFailedRunId = ghostFailedSheetRunId
    val ghostFailedRun = ghostFailedRunId?.let { id ->
        state.sessionGhostFailedRuns[id] ?: recentRunById[id]
    }
    LaunchedEffect(ghostFailedRunId, ghostFailedRun) {
        if (ghostFailedRunId != null && ghostFailedRun == null) {
            ghostFailedSheetRunId = null
        }
    }
    LaunchedEffect(ghostFailedRunId) {
        val runId = ghostFailedRunId ?: return@LaunchedEffect
        vm.loadWorkflowJobs(runId)
        vm.loadFailedRunLogExcerpt(runId)
        vm.watchLateArtifactsForFailedRun(runId)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val childPageTopInset = outerPadding.calculateTopPadding()
        val childPageBottomInset = outerPadding.calculateBottomPadding()
        val childPageModifier = Modifier
            .fillMaxWidth()
            .height(maxHeight + childPageTopInset + childPageBottomInset)
            .offset(y = -childPageTopInset)

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
                    ExitTransition.None
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
                FlashListContent(flashListScrollState)
            }
            composable(
                route = FLASH_ROUTE_WORKFLOW,
                arguments = listOf(navArgument(FLASH_ARG_RUN_ID) { type = NavType.LongType })
            ) { entry ->
                val routeRunId = entry.arguments?.getLong(FLASH_ARG_RUN_ID) ?: return@composable
                val group = allWorkflowGroups.firstOrNull { it.runId == routeRunId }
                LaunchedEffect(routeRunId) {
                    selectedRunId = routeRunId
                    selectedPrebuiltReleaseId = null
                }
                val activeRun = recentRunById[routeRunId]?.takeIf { it.isActive() }
                val isCancellingThis = routeRunId in state.cancellingWorkflowRunIds
                // Single FlashDetailBackSurface with a Crossfade inside — so
                // when a workflow finishes while the user is staring at the
                // in-progress detail, the page fades over to the completed
                // detail instead of jumping abruptly. Hold on the building
                // page while a cancel is mid-flight so the user keeps
                // seeing the "Workflow отменяется…" spinner instead of
                // bouncing into the empty-state for a split second.
                val keepBuildingForCancel = isCancellingThis &&
                    (group == null || group.remote.isEmpty())
                val buildingRun = activeRun
                    ?: if (keepBuildingForCancel) recentRunById[routeRunId] else null
                val showBuilding = buildingRun != null &&
                    (activeRun != null || isCancellingThis)
                val minuteHandController = remember(routeRunId) { MinuteHandController() }
                MinuteHandControllerHost(minuteHandController)
                var wasShowingBuilding by remember(routeRunId) { mutableStateOf(false) }
                LaunchedEffect(routeRunId, showBuilding, activeRun?.id) {
                    when {
                        showBuilding && !wasShowingBuilding && activeRun != null ->
                            minuteHandController.beginSpinning()
                        !showBuilding && wasShowingBuilding ->
                            minuteHandController.beginSettle()
                    }
                    if (wasShowingBuilding && !showBuilding) {
                        val finishedRun = recentRunById[routeRunId]
                        if (finishedRun?.isFailedFlashRun() == true) {
                            ghostFailedPageBack.resetProgress()
                            ghostFailedSheetRunId = routeRunId
                        } else {
                            val retryWhenEmpty = when (finishedRun?.conclusion) {
                                "cancelled" -> false
                                "success" -> true
                                else -> finishedRun?.status == "completed"
                            }
                            vm.refreshWorkflowArtifacts(
                                routeRunId,
                                autoDownload = state.autoDownload && retryWhenEmpty,
                                retryWhenEmpty = retryWhenEmpty,
                                force = true,
                            )
                        }
                    }
                    wasShowingBuilding = showBuilding
                    if (!showBuilding) return@LaunchedEffect
                    vm.refreshWorkflowArtifacts(routeRunId)
                    while (true) {
                        val burstActive = vm.isWorkflowStatusBurstActive(routeRunId)
                        delay(if (burstActive) 3_000L else 20_000L)
                        if (recentRunById[routeRunId]?.isActive() != true) break
                        if (!burstActive) {
                            vm.refreshWorkflowArtifacts(routeRunId)
                        }
                    }
                }
                FlashDetailBackSurface(
                    predictiveBackEnabled = state.predictiveBackEnabled,
                    outerPadding = outerPadding,
                    backgroundUri = state.customBackgroundUri,
                    backgroundImageEnabled = state.backgroundImageEnabled,
                    onBack = ::returnToWorkflowList,
                    backgroundContent = { FlashListContent(flashListScrollState) }
                ) { dismiss ->
                    Crossfade(
                        targetState = showBuilding,
                        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
                        label = "flash-detail-build-state",
                    ) { isBuilding ->
                        if (isBuilding && buildingRun != null) {
                            BuildingWorkflowDetail(
                                run = buildingRun,
                                group = group,
                                progress = state.buildProgressByRunId[routeRunId]
                                    ?: BuildProgressUtils.defaultFor(buildingRun),
                                cancelling = isCancellingThis,
                                downloadProgress = state.downloadProgress,
                                autoDownload = state.autoDownload,
                                pendingAutoDownloadRunId = state.pendingAutoDownloadRunId,
                                onDownload = vm::downloadArtifact,
                                onCopyPath = ::copyDownloadedFilePath,
                                onInstall = ::requestInstallManager,
                                onFlash = {
                                    selectedItem = it
                                    showFlashConfirm = true
                                },
                                onDelete = { deleteFileTarget = it },
                                allowRootActions = rootGranted,
                                unlinkedWorkflowTitle = unlinkedWorkflowTitle,
                                onBack = dismiss,
                                onCancel = { cancelConfirmRunId = routeRunId },
                                onCancelDownload = vm::cancelDownload,
                                onCancelAutoDownload = vm::cancelAutoDownloads,
                                minuteHandController = minuteHandController,
                            )
                        } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(horizontal = AbkScreenHorizontalPadding),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        if (group != null) {
                            val detailRun = recentRunById[group.runId]
                            val isManagerPrimary = FlashWorkflowFilter.primaryKind(
                                run = detailRun,
                                runTitle = group.runTitle,
                                hasKernelArtifact = group.hasKernelArtifact(),
                                hasManagerArtifact = group.hasManagerArtifact()
                            ) == WorkflowPrimary.Manager
                            val showParameterDetails = group.shouldShowParameterDetails(detailRun)
                            item {
                                WorkflowDetailHeader(
                                    group = group,
                                    showParameterDetails = showParameterDetails,
                                    onBack = dismiss,
                                    onShowParameters = {
                                        if (showParameterDetails) parameterTarget = group
                                    },
                                    onDelete = {
                                        deleteWorkflowTarget = group
                                        deleteRemoteWorkflowRun = false
                                    }
                                )
                            }

                            val visibleCategories = if (isManagerPrimary) {
                                listOf(ArtifactCategory.MANAGER)
                            } else {
                                artifactCategoryOrder
                            }
                            val runCreatedAt = detailRun?.createdAt?.takeIf { it.isNotBlank() }
                                ?: group.runCreatedAt
                            val runFinishedAt = detailRun?.updatedAt?.takeIf { it.isNotBlank() }
                                ?: group.runUpdatedAt
                            val elapsedAnchorCategory = visibleCategories.firstOrNull { category ->
                                group.hasArtifactsInCategory(category)
                            } ?: visibleCategories.first()

                            visibleCategories.forEach { category ->
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
                                        WorkflowCategorySection(
                                            group = group,
                                            category = category,
                                            showDuration = category == elapsedAnchorCategory,
                                            createdAt = runCreatedAt,
                                            finishedAt = runFinishedAt,
                                            liveDuration = false,
                                            minuteHandController = minuteHandController,
                                            progress = null,
                                            downloadProgress = state.downloadProgress,
                                            autoDownload = state.autoDownload,
                                            pendingAutoDownloadRunId = state.pendingAutoDownloadRunId,
                                            onDownload = vm::downloadArtifact,
                                            onCancelDownload = vm::cancelDownload,
                                            onCancelAutoDownload = vm::cancelAutoDownloads,
                                            showDownloadCancelActions = true,
                                            onCopyPath = ::copyDownloadedFilePath,
                                            onInstall = ::requestInstallManager,
                                            onFlash = {
                                                selectedItem = it
                                                showFlashConfirm = true
                                            },
                                            onDelete = { deleteFileTarget = it },
                                            allowRootActions = rootGranted,
                                        )
                                    }
                                }
                            }
                        } else {
                            item {
                                ExpressiveEmptyState(
                                    title = stringResource(R.string.flash_workflow_unavailable),
                                    subtitle = stringResource(R.string.flash_workflow_unavailable_desc),
                                    icon = Icons.Default.Inbox
                                )
                            }
                        }
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
                LaunchedEffect(release?.id, state.prebuiltGkiEnabled) {
                    if (release != null && state.prebuiltGkiEnabled) {
                        vm.loadPrebuiltGkiAssets(release)
                    }
                }
                FlashDetailBackSurface(
                    predictiveBackEnabled = state.predictiveBackEnabled,
                    outerPadding = outerPadding,
                    backgroundUri = state.customBackgroundUri,
                    backgroundImageEnabled = state.backgroundImageEnabled,
                    onBack = ::returnToPrebuiltReleaseList,
                    backgroundContent = { FlashListContent(flashListScrollState) }
                ) { dismiss ->
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
                                    onBack = dismiss,
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
                                        LoadingRow(stringResource(R.string.flash_loading_prebuilt, release.name))
                                    }
                                }
                                filteredPrebuiltAssets.isEmpty() -> {
                                    item {
                                        ExpressiveEmptyState(
                                            title = stringResource(R.string.flash_no_matching_assets),
                                            subtitle = if (prebuiltFilter.onlyMatches) {
                                                stringResource(R.string.flash_no_matching_assets_filtered)
                                            } else {
                                                stringResource(R.string.flash_no_recognized_prebuilt_assets)
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
                                            onInstall = ::requestInstallManager,
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
                                    title = stringResource(R.string.flash_release_unavailable),
                                    subtitle = stringResource(R.string.flash_release_unavailable_desc),
                                    icon = Icons.Default.CloudDownload
                                )
                            }
                        }
                    }
                }
            }
        }

        ghostFailedPageTransition.AnimatedVisibility(
            visible = { it },
            enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
            exit = childPageScrimExitTransition(state.predictiveBackEnabled, motionScheme),
            modifier = childPageModifier,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = ghostFailedPageBack.scrimAlpha))
            )
        }

        ghostFailedPageTransition.AnimatedVisibility(
            visible = { it },
            enter = childPageOverlayEnterTransition(state.predictiveBackEnabled, motionScheme),
            exit = childPageOverlayExitTransition(state.predictiveBackEnabled, motionScheme),
            modifier = childPageModifier,
        ) {
            val run = ghostFailedRun
            val runId = ghostFailedRunId
            if (run != null && runId != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(ghostFailedPageBack.backTransformModifier())
                ) {
                    FlashDetailPageBackground(
                        backgroundUri = state.customBackgroundUri,
                        backgroundImageEnabled = state.backgroundImageEnabled,
                    )
                    FailedWorkflowDetail(
                        run = run,
                        jobs = state.workflowJobsByRunId[runId],
                        jobsLoading = runId in state.workflowJobsLoading,
                        jobsError = state.workflowJobsErrors[runId],
                        logExcerpt = state.failedRunLogExcerpts[runId],
                        logLoading = runId in state.failedRunLogLoading,
                        onBack = ghostFailedPageBack::requestDismiss,
                        onOpenGitHub = { openGithubRun(context, run.htmlUrl) },
                        onRetryJobs = { vm.loadWorkflowJobs(runId, force = true) },
                    )
                }
            }
        }
    }
}
