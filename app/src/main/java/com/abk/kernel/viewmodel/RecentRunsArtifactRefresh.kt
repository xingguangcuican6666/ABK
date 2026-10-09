package com.abk.kernel.viewmodel

import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.data.model.WorkflowStatuses
import com.abk.kernel.data.model.isKernelBuild
import com.abk.kernel.data.model.isPureManagerBuild
import com.abk.kernel.data.model.isManagerBuild

/**
 * Foreground lightweight polling normally skips completed runs to keep the
 * periodic refresh cheap. The flash page can opt in because it needs the
 * artifacts belonging to recently completed kernel builds.
 */
internal fun shouldIncludeCompletedArtifacts(
    lightweight: Boolean,
    includeCompletedArtifacts: Boolean,
): Boolean = includeCompletedArtifacts || !lightweight

internal fun runsNeedingArtifactRefresh(
    runs: List<WorkflowRun>,
    includeCompleted: Boolean,
    includeCompletedPureManagers: Boolean,
): List<WorkflowRun> {
    return runs.filter { run ->
        run.status in WorkflowStatuses.ACTIVE ||
            (includeCompleted && run.status == "completed" &&
                (run.isKernelBuild() || run.isManagerBuild())) ||
            (includeCompletedPureManagers &&
                run.isPureManagerBuild() &&
                run.status == "completed")
    }
}
