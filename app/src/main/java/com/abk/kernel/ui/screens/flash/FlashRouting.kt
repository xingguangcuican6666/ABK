package com.abk.kernel.ui.screens.flash

import androidx.annotation.StringRes
import com.abk.kernel.R
import com.abk.kernel.data.model.ActiveDownloadTask
import com.abk.kernel.data.model.ArtifactCategory
import com.abk.kernel.data.model.ArtifactType
import com.abk.kernel.data.model.BuildArtifact
import com.abk.kernel.data.model.DownloadedArtifact
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.data.model.isAbkManagerBuild
import com.abk.kernel.data.model.isKernelBuild
import com.abk.kernel.data.model.isManagerBuild
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.RootUtils
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons

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
        val filteredRemote = remote.filterNot {
            shouldHideManagerCertArtifact(runTitle, it.name) || shouldHideAbkManagerArtifact(it.name)
        }
        val filteredLocal = local.filterNot {
            shouldHideManagerCertArtifact(runTitle, it.name) || it.type == ArtifactType.ABK_MANAGER
        }
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

internal fun shouldHideAbkManagerArtifact(artifactName: String): Boolean =
    DownloadUtils.classifyArtifact(artifactName) == ArtifactType.ABK_MANAGER

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

/**
 * A successful kernel run should remain visible while GitHub is still
 * indexing its artifacts (or when the artifact request temporarily fails).
 * Manager-only success runs are intentionally excluded from the flash page.
 */
internal fun WorkflowRun.isSuccessfulKernelFlashRun(): Boolean =
    status == "completed" && conclusion == "success" && isKernelBuild() && !isManagerBuild()

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

internal fun WorkflowRun?.isAbkManagerFlashRun(runTitle: String): Boolean =
    this?.isAbkManagerBuild() == true || runTitle.titleLooksLikeAbkManager()

internal fun artifactIcon(type: ArtifactType) =
    when (type) {
        ArtifactType.KERNEL_PACKAGE -> AbkIcons.Modules
        ArtifactType.KERNEL_IMG -> AbkIcons.Cpu
        ArtifactType.ANYKERNEL3 -> AbkIcons.Modules
        ArtifactType.ABK_MANAGER -> AbkIcons.Download
        ArtifactType.KSU_MANAGER -> AbkIcons.Settings
        ArtifactType.SUSFS_MODULE -> AbkIcons.Modules
        ArtifactType.OTHER -> AbkIcons.Info
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

internal fun String.titleLooksLikeAbkManager(): Boolean {
    val n = lowercase()
    return "abk app" in n || "abk-app" in n || "build abk app" in n
}
