package com.abk.kernel.utils

import com.abk.kernel.data.model.BuildParameterSummary
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.data.model.isKernelBuild
import com.abk.kernel.data.model.isManagerBuild
import com.abk.kernel.data.model.isManagerDevBuild
import com.abk.kernel.data.model.isPureManagerBuild
import com.abk.kernel.data.model.workflowNameIndicatesManagerDev

enum class FlashFilterKernelKind { ResuKisu, SukiSu, Official, None }

enum class FlashFilterManagerKind { Release, Dev }

enum class FlashFilterWorkflowState { Running, Finished }

data class FlashFilter(
    val kernelEnabled: Boolean = true,
    val kernelKinds: Set<FlashFilterKernelKind> = emptySet(),
    val managerEnabled: Boolean = true,
    val managerKinds: Set<FlashFilterManagerKind> = setOf(FlashFilterManagerKind.Release),
    val workflowStates: Set<FlashFilterWorkflowState> = emptySet(),
)

enum class WorkflowPrimary { Kernel, Manager, Unknown }

object FlashWorkflowFilter {

    fun primaryKind(
        run: WorkflowRun?,
        runTitle: String,
        hasKernelArtifact: Boolean,
        hasManagerArtifact: Boolean
    ): WorkflowPrimary {
        if (run != null) {
            if (run.isManagerBuild()) return WorkflowPrimary.Manager
            if (run.isKernelBuild()) return WorkflowPrimary.Kernel
        }
        // Without a WorkflowRun (trimmed from recentRuns), artifacts are more reliable
        // than runTitle — kernel workflows often bundle a manager APK.
        if (hasKernelArtifact) return WorkflowPrimary.Kernel
        if (runTitle.titleLooksLikeKernel()) return WorkflowPrimary.Kernel
        if (hasManagerArtifact) return WorkflowPrimary.Manager
        if (runTitle.titleLooksLikeManager()) return WorkflowPrimary.Manager
        return WorkflowPrimary.Unknown
    }

    fun managerKind(
        run: WorkflowRun?,
        runTitle: String,
        remoteArtifactNames: List<String>,
        localArtifactNames: List<String>,
        summary: BuildParameterSummary?
    ): FlashFilterManagerKind? {
        val hasDevArtifact = (remoteArtifactNames + localArtifactNames).any(::artifactNameIndicatesManagerDev)
        val fallbackRunTitleIsManager = run == null && runTitle.titleLooksLikeManager()
        val runIsManagerWorkflow = run?.isManagerBuild() == true || fallbackRunTitleIsManager
        if (runIsManagerWorkflow) {
            val branch = summary?.ksuBranch.orEmpty()
            val isDev = when (run) {
                null -> workflowNameIndicatesManagerDev(runTitle)
                else -> run.isManagerDevBuild()
            } || hasDevArtifact || ksuBranchIndicatesDev(branch)
            return if (isDev) FlashFilterManagerKind.Dev else FlashFilterManagerKind.Release
        }
        if (summary == null && !hasDevArtifact) return null
        val branch = summary?.ksuBranch.orEmpty()
        return when {
            ksuBranchIndicatesDev(branch) || hasDevArtifact -> FlashFilterManagerKind.Dev
            else -> FlashFilterManagerKind.Release
        }
    }

    fun kernelKind(
        summary: BuildParameterSummary?,
        fallbackVariant: String?
    ): FlashFilterKernelKind? {
        val raw = summary?.ksuVariant.orEmpty().ifBlank { fallbackVariant.orEmpty() }
        val v = raw.lowercase()
        if (v.isBlank()) return null
        return when {
            "resuki" in v || "re-suki" in v || "resukisu" in v -> FlashFilterKernelKind.ResuKisu
            "sukisu" in v -> FlashFilterKernelKind.SukiSu
            "kernelsu" in v || "official" in v -> FlashFilterKernelKind.Official
            else -> FlashFilterKernelKind.None
        }
    }

    fun matchesFilter(
        primary: WorkflowPrimary,
        filter: FlashFilter,
        kernelKind: FlashFilterKernelKind?,
        managerKind: FlashFilterManagerKind?,
        workflowState: FlashFilterWorkflowState?
    ): Boolean {
        if (filter.workflowStates.isNotEmpty()) {
            if (workflowState == null || workflowState !in filter.workflowStates) return false
        }
        return when (primary) {
            WorkflowPrimary.Kernel -> matchesKernelKindFilter(filter, kernelKind, workflowState)
            WorkflowPrimary.Manager -> {
                if (!filter.managerEnabled) return false
                if (filter.managerKinds.isEmpty()) return true
                managerKind != null && managerKind in filter.managerKinds
            }
            WorkflowPrimary.Unknown -> {
                if (!filter.kernelEnabled) return false
                matchesKernelKindFilter(filter, kernelKind, workflowState)
            }
        }
    }

    /**
     * Kernel sub-filters (ReSukiSU / SukiSu / …) need a resolved [FlashFilterKernelKind].
     *
     * While a run is [FlashFilterWorkflowState.Running] and [kernelKind] is still null
     * (no log summary yet, or dispatch config not linked), we show it under any active
     * kind filter so the list does not go empty mid-build. Once [kernelKind] is known
     * (queue [KernelBuildConfig] or parsed summary), non-matching kinds are hidden even
     * if GitHub status is still in_progress — e.g. SukiSu build disappears when only
     * ReSukiSU is selected. That flicker is intentional: strict filter beats hiding
     * the active run entirely.
     */
    private fun matchesKernelKindFilter(
        filter: FlashFilter,
        kernelKind: FlashFilterKernelKind?,
        workflowState: FlashFilterWorkflowState?
    ): Boolean {
        if (!filter.kernelEnabled) return false
        if (filter.kernelKinds.isEmpty()) return true
        if (kernelKind != null) return kernelKind in filter.kernelKinds
        return workflowState == FlashFilterWorkflowState.Running
    }

    /** Pending queue config applies only to kernel-primary active runs still linking. */
    fun shouldUsePendingDispatchedConfig(run: WorkflowRun?, hasKernelArtifact: Boolean): Boolean {
        if (run?.isManagerBuild() == true) return false
        return run?.isKernelBuild() == true || (run == null && hasKernelArtifact)
    }

    fun isPureManagerBuild(run: WorkflowRun): Boolean = run.isPureManagerBuild()

    /** Kernel build logs carry the parameter matrix; manager-only workflows do not. */
    fun shouldShowParameterDetails(
        run: WorkflowRun?,
        runTitle: String,
        hasKernelArtifact: Boolean,
        hasManagerArtifact: Boolean,
    ): Boolean = primaryKind(
        run = run,
        runTitle = runTitle,
        hasKernelArtifact = hasKernelArtifact,
        hasManagerArtifact = hasManagerArtifact,
    ) != WorkflowPrimary.Manager
}

private fun String.titleLooksLikeManager(): Boolean {
    val n = lowercase()
    if (n.isBlank() || titleLooksLikeKernel()) return false
    if ("package manager" in n) return false
    return "abk app" in n || "abk-app" in n || "build app" in n || "build-app" in n ||
        "debug apk" in n ||
        "ksu manager" in n || "sukisu manager" in n || "kernelsu manager" in n ||
        "getmanager" in n || "get manager" in n ||
        "管理器" in n ||
        ("manager" in n && "kernel" !in n && "内核" !in n)
}

private fun String.titleLooksLikeKernel(): Boolean {
    val n = lowercase()
    return "kernel" in n || "内核" in n
}

/**
 * Detects dev manager artifacts without matching "device" / "development" substrings.
 * Release Build ABK App bundles also ship debug APKs, so "debug" alone is not a dev signal.
 */
internal fun artifactNameIndicatesManagerDev(name: String): Boolean {
    val lower = name.lowercase()
    return MANAGER_DEV_ARTIFACT_MARKERS.any { lower.contains(it) }
}

/** KSU branch field from build summary — exact dev branch, not "development". */
internal fun ksuBranchIndicatesDev(branch: String): Boolean {
    val b = branch.lowercase().trim()
    if (b.isBlank()) return false
    return b == "dev" || b.endsWith("/dev") || b.startsWith("dev/") || "-dev" in b
}

private val MANAGER_DEV_ARTIFACT_MARKERS = listOf(
    "-dev.apk",
    "_dev.apk",
    "-dev-",
    "_dev_",
    "/dev/",
    "abk-dev",
    "app-dev"
)
