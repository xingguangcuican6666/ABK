package com.abk.kernel.viewmodel

import com.abk.kernel.data.model.BuildProgress
import com.abk.kernel.data.model.BuildStatus
import com.abk.kernel.data.model.KSU_VARIANT_NONE
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.data.model.isActive
import com.abk.kernel.data.model.isFailedFlashRun
import com.abk.kernel.data.model.isKernelBuild
import com.abk.kernel.data.model.isManagerBuild
import com.abk.kernel.data.model.isManagerDevBuild
import com.abk.kernel.utils.BuildProgressUtils
import com.abk.kernel.utils.buildDisplaySnapshot
import com.abk.kernel.utils.computeKindBuildProgress

internal fun MainUiState.withBuildRunDisplay(
    run: WorkflowRun,
    status: BuildStatus,
    progress: BuildProgress,
    cancellingWorkflowRunIds: Set<Long> = this.cancellingWorkflowRunIds,
): MainUiState {
    val updatedRuns = if (run.isActive()) {
        (activeBuildRuns.filterNot { it.id == run.id } + run)
            .distinctBy { it.id }
            .sortedByDescending { it.id }
    } else {
        activeBuildRuns.filterNot { it.id == run.id }
    }
    val updatedProgressByRunId = if (run.isActive()) {
        buildProgressByRunId + (run.id to progress)
    } else {
        buildProgressByRunId - run.id
    }
    val display = buildDisplaySnapshot(
        activeRuns = updatedRuns,
        progressByRunId = updatedProgressByRunId,
        fallbackRun = run,
        fallbackStatus = status,
        fallbackProgress = progress,
        descriptors = buildRunDescriptors(updatedRuns),
    )
    val kernelActive = updatedRuns.filter { it.isKernelBuild() }
    val kernelDisplay = kernelBuildDisplaySnapshot(
        kernelActiveRuns = kernelActive,
        fallbackRun = if (run.isKernelBuild()) run else kernelCurrentRun,
        fallbackStatus = if (run.isKernelBuild()) status else kernelBuildStatus,
    )
    val managerActive = updatedRuns.filter { it.isManagerBuild() }
    val managerDisplay = kernelBuildDisplaySnapshot(
        kernelActiveRuns = managerActive,
        fallbackRun = if (run.isManagerBuild()) run else managerCurrentRun,
        fallbackStatus = if (run.isManagerBuild()) status else managerBuildStatus,
    )
    val descriptors = buildRunDescriptors(updatedRuns)
    val kernelBuildProgressMerged = computeKindBuildProgress(
        forKernel = true,
        activeRuns = updatedRuns,
        progressByRunId = updatedProgressByRunId,
        fallbackRun = if (run.isKernelBuild()) run else kernelCurrentRun,
        fallbackStatus = if (run.isKernelBuild()) status else kernelBuildStatus,
        fallbackProgress = if (run.isKernelBuild()) progress else kernelBuildProgress,
        descriptors = descriptors,
    )
    val managerBuildProgressMerged = computeKindBuildProgress(
        forKernel = false,
        activeRuns = updatedRuns,
        progressByRunId = updatedProgressByRunId,
        fallbackRun = if (run.isManagerBuild()) run else managerCurrentRun,
        fallbackStatus = if (run.isManagerBuild()) status else managerBuildStatus,
        fallbackProgress = if (run.isManagerBuild()) progress else managerBuildProgress,
        descriptors = descriptors,
    )
    val withDisplay = copy(
        buildStatus = display.status,
        currentRun = display.currentRun,
        buildProgress = display.progress,
        activeBuildRuns = updatedRuns,
        buildProgressByRunId = updatedProgressByRunId,
        cancellingWorkflowRunIds = cancellingWorkflowRunIds,
        kernelBuildStatus = kernelDisplay.status,
        kernelCurrentRun = kernelDisplay.currentRun,
        kernelActiveBuildRuns = kernelActive,
        kernelBuildProgress = kernelBuildProgressMerged,
        managerBuildStatus = managerDisplay.status,
        managerCurrentRun = managerDisplay.currentRun,
        managerActiveBuildRuns = managerActive,
        managerBuildProgress = managerBuildProgressMerged,
    )
    return when {
        status == BuildStatus.FAILURE && run.isFailedFlashRun() && run.id !in dismissedFailedRunIds -> {
            withDisplay.copy(sessionGhostFailedRuns = withDisplay.sessionGhostFailedRuns + (run.id to run))
        }
        run.id in withDisplay.sessionGhostFailedRuns &&
            (status != BuildStatus.FAILURE || !run.isFailedFlashRun()) -> {
            withDisplay.copy(sessionGhostFailedRuns = withDisplay.sessionGhostFailedRuns - run.id)
        }
        else -> withDisplay
    }
}

internal fun MainUiState.withoutActiveBuildRun(
    runId: Long,
    fallbackStatus: BuildStatus,
    fallbackProgress: BuildProgress,
    fallbackRun: WorkflowRun? = currentRun,
): MainUiState {
    val updatedRuns = activeBuildRuns.filterNot { it.id == runId }
    val updatedProgressByRunId = buildProgressByRunId - runId
    val display = buildDisplaySnapshot(
        activeRuns = updatedRuns,
        progressByRunId = updatedProgressByRunId,
        fallbackRun = fallbackRun,
        fallbackStatus = fallbackStatus,
        fallbackProgress = fallbackProgress,
        descriptors = buildRunDescriptors(updatedRuns),
    )
    val kernelActive = updatedRuns.filter { it.isKernelBuild() }
    val kernelFallbackRun = (fallbackRun?.takeIf { it.isKernelBuild() && it.id != runId })
        ?: kernelCurrentRun?.takeUnless { it.id == runId }
    val kernelFallbackStatus = if (kernelCurrentRun?.id == runId) fallbackStatus else kernelBuildStatus
    val kernelDisplay = kernelBuildDisplaySnapshot(
        kernelActiveRuns = kernelActive,
        fallbackRun = kernelFallbackRun,
        fallbackStatus = kernelFallbackStatus,
    )
    val managerActive = updatedRuns.filter { it.isManagerBuild() }
    val managerFallbackRun = (fallbackRun?.takeIf { it.isManagerBuild() && it.id != runId })
        ?: managerCurrentRun?.takeUnless { it.id == runId }
    val managerFallbackStatus = if (managerCurrentRun?.id == runId) fallbackStatus else managerBuildStatus
    val managerDisplay = kernelBuildDisplaySnapshot(
        kernelActiveRuns = managerActive,
        fallbackRun = managerFallbackRun,
        fallbackStatus = managerFallbackStatus,
    )
    val descriptors = buildRunDescriptors(updatedRuns)
    val kernelBuildProgressMerged = computeKindBuildProgress(
        forKernel = true,
        activeRuns = updatedRuns,
        progressByRunId = updatedProgressByRunId,
        fallbackRun = kernelFallbackRun,
        fallbackStatus = kernelFallbackStatus,
        fallbackProgress = if (kernelCurrentRun?.id == runId) fallbackProgress else kernelBuildProgress,
        descriptors = descriptors,
    )
    val managerBuildProgressMerged = computeKindBuildProgress(
        forKernel = false,
        activeRuns = updatedRuns,
        progressByRunId = updatedProgressByRunId,
        fallbackRun = managerFallbackRun,
        fallbackStatus = managerFallbackStatus,
        fallbackProgress = if (managerCurrentRun?.id == runId) fallbackProgress else managerBuildProgress,
        descriptors = descriptors,
    )
    return copy(
        buildStatus = display.status,
        currentRun = display.currentRun,
        buildProgress = display.progress,
        activeBuildRuns = updatedRuns,
        buildProgressByRunId = updatedProgressByRunId,
        kernelBuildStatus = kernelDisplay.status,
        kernelCurrentRun = kernelDisplay.currentRun,
        kernelActiveBuildRuns = kernelActive,
        kernelBuildProgress = kernelBuildProgressMerged,
        managerBuildStatus = managerDisplay.status,
        managerCurrentRun = managerDisplay.currentRun,
        managerActiveBuildRuns = managerActive,
        managerBuildProgress = managerBuildProgressMerged,
    )
}

private data class KernelBuildDisplaySnapshot(
    val status: BuildStatus,
    val currentRun: WorkflowRun?,
)

private fun kernelBuildDisplaySnapshot(
    kernelActiveRuns: List<WorkflowRun>,
    fallbackRun: WorkflowRun?,
    fallbackStatus: BuildStatus,
): KernelBuildDisplaySnapshot {
    val sortedRuns = kernelActiveRuns
        .filter { it.isActive() }
        .distinctBy { it.id }
        .sortedByDescending { it.id }
    if (sortedRuns.isEmpty()) {
        return KernelBuildDisplaySnapshot(fallbackStatus, fallbackRun)
    }
    val status = if (sortedRuns.any { it.status == "in_progress" }) {
        BuildStatus.IN_PROGRESS
    } else {
        BuildStatus.QUEUED
    }
    return KernelBuildDisplaySnapshot(
        status = status,
        currentRun = sortedRuns.firstOrNull(),
    )
}

internal fun MainUiState.buildRunDescriptors(
    runs: List<WorkflowRun>,
): Map<Long, BuildProgressUtils.RunDescriptor> {
    val queueByRunId = buildQueue.filter { it.runId > 0L }.associateBy { it.runId }
    return runs.mapNotNull { run ->
        val item = queueByRunId[run.id]
        when {
            run.isKernelBuild() && item != null -> {
                val cfg = item.config
                val variant = cfg.kernelsuVariant.takeIf { it != KSU_VARIANT_NONE }.orEmpty()
                val susfs = !cfg.cancelSusfs && cfg.kernelsuVariant != KSU_VARIANT_NONE
                val kernelLabel =
                    "${cfg.kernelVersion}.${cfg.subLevel}-${cfg.androidVersion}-${cfg.osPatchLevel}"
                run.id to BuildProgressUtils.RunDescriptor(
                    isManager = false,
                    ksuVariant = variant,
                    susfs = susfs,
                    kernelLabel = kernelLabel,
                )
            }
            run.isManagerBuild() -> {
                run.id to BuildProgressUtils.RunDescriptor(
                    isManager = true,
                    managerIsDev = run.isManagerDevBuild(),
                )
            }
            else -> null
        }
    }.toMap()
}
