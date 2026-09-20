package com.abk.kernel.data.model

object WorkflowStatuses {
    val ACTIVE = setOf(
        "queued",
        "waiting",
        "requested",
        "pending",
        "in_progress",
    )
}

fun WorkflowRun.isActive(): Boolean = status in WorkflowStatuses.ACTIVE
