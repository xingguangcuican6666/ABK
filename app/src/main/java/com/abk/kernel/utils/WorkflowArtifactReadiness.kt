package com.abk.kernel.utils

import com.abk.kernel.data.model.ArtifactType
import com.abk.kernel.data.model.BuildArtifact
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.data.model.isKernelBuild
import com.abk.kernel.data.model.isPureManagerBuild

/**
 * Whether an still-active workflow's remote artifacts are complete enough to
 * start a short burst of GitHub run-status polling (kernel remote or manager only).
 */
fun isWorkflowArtifactSetComplete(run: WorkflowRun, remoteArtifacts: List<BuildArtifact>): Boolean {
    if (remoteArtifacts.isEmpty()) return false
    val remoteTypes = remoteArtifacts.map { DownloadUtils.classifyArtifact(it.name) }
    val hasRemoteKernel = remoteTypes.any {
        it == ArtifactType.KERNEL_PACKAGE || it == ArtifactType.KERNEL_IMG || it == ArtifactType.ANYKERNEL3
    }
    val hasManager = remoteTypes.any {
        it == ArtifactType.ABK_MANAGER || it == ArtifactType.KSU_MANAGER
    }
    return when {
        run.isKernelBuild() -> hasRemoteKernel
        run.isPureManagerBuild() -> hasManager
        else -> false
    }
}
