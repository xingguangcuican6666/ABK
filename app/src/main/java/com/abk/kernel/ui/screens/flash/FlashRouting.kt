@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens.flash

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
import com.abk.kernel.viewmodel.MainViewModel
import com.abk.kernel.viewmodel.mergeWorkflowActiveDownloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


internal fun emptyWorkflowGroupFor(
    run: WorkflowRun,
    unlinkedWorkflowTitle: String,
): WorkflowArtifactGroup {
    val runTitle = run.displayTitle?.ifBlank { null }
        ?: run.name?.ifBlank { null }
        ?: unlinkedWorkflowTitle
    return WorkflowArtifactGroup(
        runId = run.id,
        runTitle = runTitle,
        runNumber = run.runNumber,
        runCreatedAt = run.createdAt,
        runUpdatedAt = run.updatedAt,
        remote = emptyList(),
        local = emptyList(),
        categories = emptySet(),
        cachedHasRemoteManagerArtifact = false,
        cachedHasManagerArtifact = false,
        cachedHasKernelArtifact = false,
        cachedHasRemoteKernelArtifact = false,
        cachedHasSusfsModuleArtifact = false,
    )
}

internal fun buildWorkflowGroups(
    remoteArtifacts: List<BuildArtifact>,
    downloadedArtifacts: List<DownloadedArtifact>,
    unlinkedWorkflowTitle: String,
    runs: Map<Long, WorkflowRun> = emptyMap()
): List<WorkflowArtifactGroup> {
    val remoteByRunId = remoteArtifacts.groupBy { it.runId }
    val localByRunId = downloadedArtifacts.groupBy { it.runId }
    val runIds = (remoteByRunId.keys + localByRunId.keys).distinct()
    return runIds.map { runId ->
        val remote = remoteByRunId[runId].orEmpty()
        val local = localByRunId[runId].orEmpty()
        val firstRemote = remote.firstOrNull()
        val firstLocal = local.firstOrNull()
        val runTitle = firstRemote?.runTitle?.ifBlank { null }
            ?: firstLocal?.runTitle?.ifBlank { null }
            ?: runs[runId]?.displayTitle?.ifBlank { null }
            ?: runs[runId]?.name?.ifBlank { null }
            ?: unlinkedWorkflowTitle
        val filteredRemote = remote.filterNot { shouldHideManagerCertArtifact(runTitle, it.name) }
        val filteredLocal = local.filterNot { shouldHideManagerCertArtifact(runTitle, it.name) }
        val runCreatedAt = runs[runId]?.createdAt
            ?: firstRemote?.runCreatedAt
            ?: ""
        val runUpdatedAt = runs[runId]?.updatedAt.orEmpty()
        val remoteTypes = filteredRemote.map { DownloadUtils.classifyArtifact(it.name) }
        val remoteCategories = remoteTypes.mapNotNull(DownloadUtils::classifyCategory).toSet()
        val localCategories = filteredLocal.map { it.category }.toSet()
        val categories = (remoteCategories + localCategories)
        WorkflowArtifactGroup(
            runId = runId,
            runTitle = runTitle,
            runNumber = firstRemote?.runNumber ?: firstLocal?.runNumber ?: 0,
            runCreatedAt = runCreatedAt,
            runUpdatedAt = runUpdatedAt,
            remote = filteredRemote,
            local = filteredLocal,
            categories = categories,
            cachedHasRemoteManagerArtifact = remoteTypes.any { it.isManagerArtifactType() },
            cachedHasManagerArtifact = remoteTypes.any { it.isManagerArtifactType() } ||
                filteredLocal.any { it.type.isManagerArtifactType() },
            cachedHasKernelArtifact = remoteTypes.any {
                it == ArtifactType.KERNEL_PACKAGE || it == ArtifactType.KERNEL_IMG || it == ArtifactType.ANYKERNEL3
            } || filteredLocal.any { it.type in setOf(ArtifactType.KERNEL_PACKAGE, ArtifactType.KERNEL_IMG, ArtifactType.ANYKERNEL3) },
            cachedHasRemoteKernelArtifact = remoteTypes.any {
                it == ArtifactType.KERNEL_PACKAGE || it == ArtifactType.KERNEL_IMG || it == ArtifactType.ANYKERNEL3
            },
            cachedHasSusfsModuleArtifact = remoteTypes.any { it == ArtifactType.SUSFS_MODULE } ||
                filteredLocal.any { it.type == ArtifactType.SUSFS_MODULE }
        )
    }.sortedForWorkflowDisplay(runs)
}

internal fun shouldHideManagerCertArtifact(runTitle: String, artifactName: String): Boolean {
    val lowerArtifact = artifactName.lowercase()
    if (lowerArtifact != "abk-manager-cert.generated.env") return false
    val lowerTitle = runTitle.lowercase()
    return listOf("abk app", "abk-app", "build app", "manager", "管理器", "getmanager")
        .any { it in lowerTitle }
}

internal data class WorkflowArtifactGroup(
    val runId: Long,
    val runTitle: String,
    val runNumber: Int,
    val runCreatedAt: String,
    val runUpdatedAt: String,
    val remote: List<BuildArtifact>,
    val local: List<DownloadedArtifact>,
    val categories: Set<ArtifactCategory>,
    val cachedHasRemoteManagerArtifact: Boolean,
    val cachedHasManagerArtifact: Boolean,
    val cachedHasKernelArtifact: Boolean,
    val cachedHasRemoteKernelArtifact: Boolean,
    val cachedHasSusfsModuleArtifact: Boolean
)

internal fun workflowRunLabel(run: WorkflowRun): String =
    if (run.runNumber > 0) "#${run.runNumber} · ${run.displayTitle ?: run.name ?: run.id}" else "#${run.id}"

internal fun workflowTaskLabel(task: ActiveDownloadTask): String =
    if (task.runNumber > 0) "#${task.runNumber} · ${task.runTitle}" else "#${task.runId} · ${task.runTitle}"

internal fun WorkflowRun.isActiveFlashRun(): Boolean =
    status in setOf("queued", "waiting", "requested", "pending", "in_progress")

internal fun List<WorkflowArtifactGroup>.sortedForWorkflowDisplay(
    runs: Map<Long, WorkflowRun>
): List<WorkflowArtifactGroup> = sortedWith(
    compareByDescending<WorkflowArtifactGroup> { runs[it.runId]?.isActiveFlashRun() == true }
        .thenByDescending { it.runCreatedAt }
        .thenByDescending { it.runId }
        .thenByDescending { it.runNumber }
)

internal fun ArtifactType.isManagerArtifactType(): Boolean =
    this == ArtifactType.ABK_MANAGER || this == ArtifactType.KSU_MANAGER

internal fun artifactIcon(type: ArtifactType) = when (type) {
    ArtifactType.KERNEL_PACKAGE -> Icons.Default.Inventory2
    ArtifactType.KERNEL_IMG -> Icons.Default.Memory
    ArtifactType.ANYKERNEL3 -> Icons.Default.Archive
    ArtifactType.ABK_MANAGER -> Icons.Default.InstallMobile
    ArtifactType.KSU_MANAGER -> Icons.Default.Shield
    ArtifactType.SUSFS_MODULE -> Icons.Default.Extension
    ArtifactType.OTHER -> Icons.Default.InsertDriveFile
}

@StringRes
internal fun artifactTypeLabelRes(type: ArtifactType) = when (type) {
    ArtifactType.KERNEL_PACKAGE -> R.string.flash_artifact_kernel_package
    ArtifactType.KERNEL_IMG -> R.string.flash_artifact_kernel_img
    ArtifactType.ANYKERNEL3 -> R.string.flash_artifact_anykernel3
    ArtifactType.ABK_MANAGER -> R.string.flash_artifact_abk_manager
    ArtifactType.KSU_MANAGER -> R.string.flash_artifact_ksu_manager
    ArtifactType.SUSFS_MODULE -> R.string.flash_artifact_susfs_module
    ArtifactType.OTHER -> R.string.flash_artifact_other
}

@StringRes
internal fun flashButtonLabelRes(type: ArtifactType) = when (type) {
    ArtifactType.KERNEL_IMG -> R.string.flash_button_flash
    ArtifactType.ANYKERNEL3 -> R.string.flash_button_flash_ak3
    ArtifactType.SUSFS_MODULE -> R.string.flash_button_install_module
    else -> R.string.flash_button_execute
}

@StringRes
internal fun flashOperationLabelRes(type: ArtifactType) = when (type) {
    ArtifactType.KERNEL_IMG -> R.string.flash_operation_flash_boot
    ArtifactType.ANYKERNEL3 -> R.string.flash_operation_flash_ak3
    ArtifactType.SUSFS_MODULE -> R.string.flash_button_install_module
    else -> R.string.flash_button_execute
}

internal fun flashCommandPreview(
    item: DownloadedArtifact,
    anyKernelSlotTarget: RootUtils.Ak3SlotTarget = RootUtils.Ak3SlotTarget.CURRENT
) = when (item.type) {
    ArtifactType.KERNEL_IMG -> "dd boot <- ${item.name}"
    ArtifactType.ANYKERNEL3 -> "flash-ak3 ${item.name} --slot ${anyKernelSlotTarget.slotSelectValue}"
    ArtifactType.SUSFS_MODULE -> "install-module ${item.name}"
    else -> "run ${item.name}"
}

internal fun isFlashDetailRoute(route: String?): Boolean =
    route != null && route != FLASH_ROUTE_LIST

internal const val FLASH_ROUTE_LIST = "flash_list"
internal const val FLASH_ARG_RUN_ID = "runId"
internal const val FLASH_ARG_RELEASE_ID = "releaseId"
internal const val FLASH_ROUTE_WORKFLOW = "workflow/{$FLASH_ARG_RUN_ID}"
internal const val FLASH_ROUTE_PREBUILT = "prebuilt/{$FLASH_ARG_RELEASE_ID}"

internal fun flashWorkflowRoute(runId: Long) = "workflow/$runId"

internal fun flashPrebuiltRoute(releaseId: Long) = "prebuilt/$releaseId"

internal enum class FlashContentTab(@StringRes val labelRes: Int) {
    Workflows(R.string.flash_tab_workflows),
    PrebuiltGki(R.string.flash_prebuilt_gki)
}

internal data class PrebuiltGkiFilter(
    val androidVersion: String,
    val kernelVersion: String,
    val subLevel: String,
    val osPatchLevel: String,
    val onlyMatches: Boolean = true
)

