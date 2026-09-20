package com.abk.kernel.utils

import com.abk.kernel.data.model.BuildProgress
import com.abk.kernel.data.model.BuildStatus
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.data.model.isActive
import com.abk.kernel.data.model.isKernelBuild
import com.abk.kernel.data.model.isManagerBuild

internal data class BuildDisplaySnapshot(
    val status: BuildStatus,
    val currentRun: WorkflowRun?,
    val progress: BuildProgress
)

internal fun buildDisplaySnapshot(
    activeRuns: List<WorkflowRun>,
    progressByRunId: Map<Long, BuildProgress>,
    fallbackRun: WorkflowRun?,
    fallbackStatus: BuildStatus,
    fallbackProgress: BuildProgress,
    descriptors: Map<Long, BuildProgressUtils.RunDescriptor> = emptyMap()
): BuildDisplaySnapshot {
    val sortedRuns = activeRuns
        .filter { it.isActive() }
        .distinctBy { it.id }
        .sortedByDescending { it.id }
    if (sortedRuns.isEmpty()) {
        return BuildDisplaySnapshot(fallbackStatus, fallbackRun, fallbackProgress)
    }
    val status = if (sortedRuns.any { it.status == "in_progress" }) {
        BuildStatus.IN_PROGRESS
    } else {
        BuildStatus.QUEUED
    }
    return BuildDisplaySnapshot(
        status = status,
        currentRun = sortedRuns.firstOrNull(),
        progress = BuildProgressUtils.merge(sortedRuns, progressByRunId, descriptors)
    )
}

internal fun computeKindBuildProgress(
    forKernel: Boolean,
    activeRuns: List<WorkflowRun>,
    progressByRunId: Map<Long, BuildProgress>,
    fallbackRun: WorkflowRun?,
    fallbackStatus: BuildStatus,
    fallbackProgress: BuildProgress,
    descriptors: Map<Long, BuildProgressUtils.RunDescriptor> = emptyMap()
): BuildProgress {
    val kindRuns = activeRuns.filter { if (forKernel) it.isKernelBuild() else it.isManagerBuild() }
    return buildDisplaySnapshot(
        activeRuns = kindRuns,
        progressByRunId = progressByRunId,
        fallbackRun = fallbackRun,
        fallbackStatus = fallbackStatus,
        fallbackProgress = fallbackProgress,
        descriptors = descriptors
    ).progress
}
