package com.abk.kernel.viewmodel

import com.abk.kernel.data.model.Artifact
import com.abk.kernel.data.model.BuildArtifact
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.data.model.isActive
import com.abk.kernel.data.model.isFailedFlashRun
import com.abk.kernel.data.model.isManagerBuild
import com.abk.kernel.data.model.withRun
import com.abk.kernel.data.repository.GitHubRepository
import com.abk.kernel.data.repository.PreferencesRepository
import com.abk.kernel.data.repository.Result
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

private const val ACTIVE_RUN_ARTIFACT_REFRESH_MS = 20_000L

class WorkflowArtifactCoordinator(
    private val scope: CoroutineScope,
    private val github: GitHubRepository,
    private val prefs: PreferencesRepository,
    private val gson: Gson,
    private val readState: () -> MainUiState,
    private val updateState: ((MainUiState) -> MainUiState) -> Unit,
    private val workflowRunTitle: (Long) -> String,
    private val toBuildArtifact: (Artifact, Long, String) -> BuildArtifact,
    private val burstMaybeStart: (Long, WorkflowRun) -> Unit,
    private val maybeAutoDownload: suspend (Long, List<BuildArtifact>, Boolean) -> Unit,
) {
    private val loadJobs = mutableMapOf<Long, Job>()
    private val loadGenerations = mutableMapOf<Long, Int>()
    private val lastRefreshAt = mutableMapOf<Long, Long>()

    fun loadArtifacts(
        runId: Long,
        autoDownload: Boolean = false,
        retryWhenEmpty: Boolean = autoDownload,
        force: Boolean = false,
    ) {
        if (runId <= 0L) return
        val state = readState()
        val username = state.user?.login ?: return
        val repoName = state.forkRepo?.name ?: return
        if (!force && loadJobs[runId]?.isActive == true) return

        loadJobs[runId]?.cancel()
        val generation = (loadGenerations[runId] ?: 0) + 1
        loadGenerations[runId] = generation
        loadJobs[runId] = scope.launch {
            try {
                when (val r = listArtifactsWithRetry(username, repoName, runId, retryWhenEmpty)) {
                    is Result.Success -> {
                        if (generation != loadGenerations[runId]) return@launch
                        val run = readState().recentRuns.find { it.id == runId }
                            ?: readState().currentRun?.takeIf { it.id == runId }
                            ?: when (val runResult = github.getWorkflowRun(username, repoName, runId)) {
                                is Result.Success -> runResult.data
                                else -> null
                            }
                        val buildArtifacts = r.data.map { artifact ->
                            if (run != null) {
                                artifact.withRun(run)
                            } else {
                                toBuildArtifact(artifact, runId, workflowRunTitle(runId))
                            }
                        }
                        val merged = mergeRemoteArtifacts(readState().artifacts, buildArtifacts)
                        updateState { it.copy(artifacts = merged) }
                        prefs.saveRemoteArtifactsJson(gson.toJson(merged))
                        maybeAutoDownload(runId, buildArtifacts, autoDownload)
                        if (run != null) {
                            burstMaybeStart(runId, run)
                        }
                    }
                    else -> {}
                }
            } finally {
                if (generation == loadGenerations[runId]) {
                    loadJobs.remove(runId)
                }
            }
        }
    }

    fun maybeLoadWhileRunning(runId: Long) {
        if (runId <= 0L) return
        val now = System.currentTimeMillis()
        val last = lastRefreshAt[runId] ?: 0L
        if (now - last < ACTIVE_RUN_ARTIFACT_REFRESH_MS) return
        lastRefreshAt[runId] = now
        loadArtifacts(runId, autoDownload = false)
    }

    suspend fun refreshForRuns(
        owner: String,
        repoName: String,
        runs: List<WorkflowRun>,
        includeCompleted: Boolean = true,
        includeCompletedPureManagers: Boolean = false,
    ) {
        val runsToRefresh = runsNeedingArtifactRefresh(
            runs,
            includeCompleted = includeCompleted,
            includeCompletedPureManagers = includeCompletedPureManagers,
        ).take(MAX_REMOTE_ARTIFACT_RUNS)
        if (runsToRefresh.isEmpty()) return

        val existingArtifacts = readState().artifacts
        val (priorityRuns, otherRuns) = runsToRefresh.partition { run ->
            run.isActive() || run.isManagerBuild()
        }
        val (merged, pendingRunId) = withContext(Dispatchers.IO) {
            suspend fun fetchArtifacts(batch: List<WorkflowRun>): List<BuildArtifact> = coroutineScope {
                batch.map { run ->
                    async {
                        when (val artifacts = github.listArtifacts(owner, repoName, run.id)) {
                            is Result.Success -> artifacts.data.map { it.withRun(run) }
                            else -> emptyList()
                        }
                    }
                }.awaitAll().flatten()
            }

            var mergedArtifacts = existingArtifacts
            mergedArtifacts = mergeRemoteArtifacts(mergedArtifacts, fetchArtifacts(priorityRuns))
            prefs.saveRemoteArtifactsJson(gson.toJson(mergedArtifacts))
            updateState { it.copy(artifacts = mergedArtifacts) }

            ensureActive()
            if (otherRuns.isNotEmpty()) {
                mergedArtifacts = mergeRemoteArtifacts(mergedArtifacts, fetchArtifacts(otherRuns))
                prefs.saveRemoteArtifactsJson(gson.toJson(mergedArtifacts))
            }
            mergedArtifacts to prefs.pendingAutoDownloadRunId.first()
        }
        updateState { it.copy(artifacts = merged) }

        if (pendingRunId > 0L && runsToRefresh.any { it.id == pendingRunId }) {
            val pendingRun = runsToRefresh.firstOrNull { it.id == pendingRunId }
            if (pendingRun?.isFailedFlashRun() != true) {
                maybeAutoDownload(
                    pendingRunId,
                    merged.filter { it.runId == pendingRunId },
                    true,
                )
            }
        }
        runsToRefresh.forEach { run -> burstMaybeStart(run.id, run) }
    }

    private suspend fun listArtifactsWithRetry(
        owner: String,
        repoName: String,
        runId: Long,
        retryWhenEmpty: Boolean,
    ): Result<List<Artifact>> {
        var result = github.listArtifacts(owner, repoName, runId)
        if (!retryWhenEmpty) return result
        repeat(3) {
            when (val current = result) {
                is Result.Success -> if (current.data.isNotEmpty()) return current
                else -> {}
            }
            delay(5_000)
            result = github.listArtifacts(owner, repoName, runId)
        }
        return result
    }
}
