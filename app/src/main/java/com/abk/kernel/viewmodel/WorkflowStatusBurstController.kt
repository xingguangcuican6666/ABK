package com.abk.kernel.viewmodel

import com.abk.kernel.data.model.BuildArtifact
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.data.model.isActive
import com.abk.kernel.data.repository.GitHubRepository
import com.abk.kernel.data.repository.Result
import com.abk.kernel.utils.isWorkflowArtifactSetComplete
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val WORKFLOW_STATUS_BURST_INTERVAL_MS = 3_000L
private const val WORKFLOW_STATUS_BURST_MAX_MS = 30_000L

class WorkflowStatusBurstController(
    private val scope: CoroutineScope,
    private val github: GitHubRepository,
    private val onRunPolled: (WorkflowRun) -> Unit,
) {
    private val jobs = mutableMapOf<Long, Job>()
    private val startedRunIds = mutableSetOf<Long>()

    fun isBurstActive(runId: Long): Boolean = jobs[runId]?.isActive == true

    fun maybeStart(
        runId: Long,
        owner: String,
        repoName: String,
        run: WorkflowRun,
        remoteArtifacts: List<BuildArtifact>,
    ) {
        if (runId <= 0L || runId in startedRunIds) return
        if (!run.isActive()) return
        if (!isWorkflowArtifactSetComplete(run, remoteArtifacts)) return
        startedRunIds += runId
        start(owner, repoName, runId)
    }

    private fun start(owner: String, repoName: String, runId: Long) {
        jobs[runId]?.cancel()
        jobs[runId] = scope.launch {
            val deadlineMs = System.currentTimeMillis() + WORKFLOW_STATUS_BURST_MAX_MS
            try {
                while (currentCoroutineContext().isActive && System.currentTimeMillis() < deadlineMs) {
                    when (val result = github.getWorkflowRun(owner, repoName, runId)) {
                        is Result.Success -> {
                            val run = result.data
                            onRunPolled(run)
                            if (!run.isActive()) break
                        }
                        else -> Unit
                    }
                    delay(WORKFLOW_STATUS_BURST_INTERVAL_MS)
                }
            } finally {
                jobs.remove(runId)
            }
        }
    }
}
