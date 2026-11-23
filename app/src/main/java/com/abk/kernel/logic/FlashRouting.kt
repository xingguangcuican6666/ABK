package com.abk.kernel.logic

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
import com.abk.kernel.utils.FlashFilterWorkflowState
import com.abk.kernel.utils.FlashWorkflowFilter
import com.abk.kernel.utils.WorkflowPrimary

/**
 * Compose-free workflow/artifact grouping used by the flash and build screens.
 * Kept outside the UI package so it can be unit tested without Android resources.
 */
data class WorkflowArtifactGroup(
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

fun emptyWorkflowGroupFor(
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

fun buildWorkflowGroups(
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

fun shouldHideManagerCertArtifact(runTitle: String, artifactName: String): Boolean {
    val lowerArtifact = artifactName.lowercase()
    if (lowerArtifact != "abk-manager-cert.generated.env") return false
    val lowerTitle = runTitle.lowercase()
    return listOf("abk app", "abk-app", "build app", "manager", "管理器", "getmanager")
        .any { it in lowerTitle }
}

fun shouldHideAbkManagerArtifact(artifactName: String): Boolean =
    DownloadUtils.classifyArtifact(artifactName) == ArtifactType.ABK_MANAGER

fun workflowRunLabel(run: WorkflowRun): String =
    if (run.runNumber > 0) "#${run.runNumber} · ${run.displayTitle ?: run.name ?: run.id}" else "#${run.id}"

fun workflowTaskLabel(task: ActiveDownloadTask): String =
    if (task.runNumber > 0) "#${task.runNumber} · ${task.runTitle}" else "#${task.runId} · ${task.runTitle}"

fun WorkflowRun.isActiveFlashRun(): Boolean =
    status in setOf("queued", "waiting", "requested", "pending", "in_progress")

/**
 * A successful kernel run should remain visible while GitHub is still
 * indexing its artifacts (or when the artifact request temporarily fails).
 * Manager-only success runs are intentionally excluded from the flash page.
 */
fun WorkflowRun.isSuccessfulKernelFlashRun(): Boolean =
    status == "completed" && conclusion == "success" && isKernelBuild() && !isManagerBuild()

fun List<WorkflowArtifactGroup>.sortedForWorkflowDisplay(
    runs: Map<Long, WorkflowRun>
): List<WorkflowArtifactGroup> = sortedWith(
    compareByDescending<WorkflowArtifactGroup> { runs[it.runId]?.isActiveFlashRun() == true }
        .thenByDescending { it.runCreatedAt }
        .thenByDescending { it.runId }
        .thenByDescending { it.runNumber }
)

fun ArtifactType.isManagerArtifactType(): Boolean =
    this == ArtifactType.ABK_MANAGER || this == ArtifactType.KSU_MANAGER

fun WorkflowRun?.isAbkManagerFlashRun(runTitle: String): Boolean =
    this?.isAbkManagerBuild() == true || runTitle.titleLooksLikeAbkManager()

fun String.titleLooksLikeAbkManager(): Boolean {
    val n = lowercase()
    return "abk app" in n || "abk-app" in n || "build abk app" in n
}

fun WorkflowArtifactGroup.hasRemoteManagerArtifact(): Boolean =
    cachedHasRemoteManagerArtifact

fun WorkflowArtifactGroup.hasManagerArtifact(): Boolean =
    cachedHasManagerArtifact

fun WorkflowArtifactGroup.hasKernelArtifact(): Boolean =
    cachedHasKernelArtifact

fun WorkflowArtifactGroup.shouldShowParameterDetails(run: WorkflowRun?): Boolean =
    FlashWorkflowFilter.shouldShowParameterDetails(
        run = run,
        runTitle = runTitle,
        hasKernelArtifact = hasKernelArtifact(),
        hasManagerArtifact = hasManagerArtifact(),
    )

fun WorkflowArtifactGroup.hasRemoteKernelArtifact(): Boolean =
    cachedHasRemoteKernelArtifact

fun WorkflowArtifactGroup.hasSusfsModuleArtifact(): Boolean =
    cachedHasSusfsModuleArtifact

/** Manifest notices are scoped to an explicitly opened workflow detail page. */
fun manifestNoticeCandidates(
    flashDetailRouteActive: Boolean,
    selectedRunId: Long?,
    workflowGroups: List<WorkflowArtifactGroup>,
): List<DownloadedArtifact> {
    if (!flashDetailRouteActive || selectedRunId == null) return emptyList()
    return workflowGroups
        .firstOrNull { it.runId == selectedRunId }
        ?.local
        ?.filter { it.verified && it.manifestClientNotice != null }
        .orEmpty()
}

fun WorkflowRun?.workflowState(): FlashFilterWorkflowState? = when {
    this == null -> null
    this.isActiveFlashRun() -> FlashFilterWorkflowState.Running
    else -> FlashFilterWorkflowState.Finished
}

fun hasDownloadedFilesForRun(
    runId: Long,
    downloadedArtifacts: List<DownloadedArtifact>,
    workflowGroups: List<WorkflowArtifactGroup>,
    activeDownloadTasks: List<ActiveDownloadTask>,
): Boolean {
    if (downloadedArtifacts.any { it.runId == runId }) return true
    if (workflowGroups.any { it.runId == runId && it.local.isNotEmpty() }) return true
    if (activeDownloadTasks.any { it.runId == runId }) return true
    return false
}

fun WorkflowArtifactGroup.shouldAppearInWorkflowList(run: WorkflowRun?): Boolean =
    if (run.isAbkManagerFlashRun(runTitle)) {
        false
    } else if (remote.isEmpty() && local.isEmpty()) {
        run?.isSuccessfulKernelFlashRun() == true
    } else when (
        FlashWorkflowFilter.primaryKind(
            run = run,
            runTitle = runTitle,
            hasKernelArtifact = hasKernelArtifact(),
            hasManagerArtifact = hasManagerArtifact()
        )
    ) {
        WorkflowPrimary.Kernel -> hasRemoteKernelArtifact()
        else -> true
    }

fun limitWorkflowGroupsForDisplay(
    groups: List<WorkflowArtifactGroup>,
    runs: Map<Long, WorkflowRun>,
    maxPerCategory: Int = MAX_VISIBLE_WORKFLOWS_PER_CATEGORY,
): List<WorkflowArtifactGroup> {
    val counts = mutableMapOf<WorkflowPrimary, Int>()
    return groups.filter { group ->
        val run = runs[group.runId]
        val bucket = when (
            FlashWorkflowFilter.primaryKind(
                run = run,
                runTitle = group.runTitle,
                hasKernelArtifact = group.hasKernelArtifact(),
                hasManagerArtifact = group.hasManagerArtifact()
            )
        ) {
            WorkflowPrimary.Manager -> WorkflowPrimary.Manager
            else -> WorkflowPrimary.Kernel
        }
        val next = (counts[bucket] ?: 0) + 1
        if (next > maxPerCategory) {
            false
        } else {
            counts[bucket] = next
            true
        }
    }
}

const val MAX_VISIBLE_WORKFLOWS_PER_CATEGORY = 15
