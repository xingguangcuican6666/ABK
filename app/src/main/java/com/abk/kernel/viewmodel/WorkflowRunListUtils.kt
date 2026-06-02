package com.abk.kernel.viewmodel

import com.abk.kernel.data.model.WorkflowRun

internal fun List<WorkflowRun>.replaceRun(run: WorkflowRun): List<WorkflowRun> {
    var replaced = false
    val updated = map { existing ->
        if (existing.id == run.id) {
            replaced = true
            run
        } else {
            existing
        }
    }
    return if (replaced) updated else updated + run
}
