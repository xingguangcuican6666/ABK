package com.abk.kernel.viewmodel

import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.data.model.WorkflowStatuses
import com.abk.kernel.data.model.isPureManagerBuild

internal fun runsNeedingArtifactRefresh(
    runs: List<WorkflowRun>,
    includeCompleted: Boolean,
    includeCompletedPureManagers: Boolean,
): List<WorkflowRun> {
    return runs.filter { run ->
        run.status in WorkflowStatuses.ACTIVE ||
            (includeCompleted && run.status == "completed") ||
            (includeCompletedPureManagers &&
                run.isPureManagerBuild() &&
                run.status == "completed")
    }
}
