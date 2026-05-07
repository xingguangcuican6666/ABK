package com.abk.kernel.data.repository

import com.abk.kernel.BuildConfig
import com.abk.kernel.data.api.GitHubApiService
import com.abk.kernel.data.api.GitHubAuthService
import com.abk.kernel.data.api.NetworkClient
import com.abk.kernel.data.model.*

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val code: Int = -1) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

class GitHubRepository(
    private val authService: GitHubAuthService = NetworkClient.createAuthService(),
    private var apiService: GitHubApiService? = null
) {
    private val clientId = BuildConfig.GITHUB_CLIENT_ID

    fun updateToken(token: String) {
        apiService = NetworkClient.createApiService(token)
    }

    // ── Auth ──────────────────────────────────────────────────────────────

    suspend fun requestDeviceCode(): Result<DeviceCodeResponse> = runCatching {
        val resp = authService.requestDeviceCode(clientId)
        if (resp.isSuccessful && resp.body() != null) {
            Result.Success(resp.body()!!)
        } else {
            Result.Error("Device code request failed: ${resp.code()}", resp.code())
        }
    }.getOrElse { Result.Error(it.message ?: "Unknown error") }

    suspend fun pollToken(deviceCode: String): Result<AccessTokenResponse> = runCatching {
        val resp = authService.pollAccessToken(clientId, deviceCode)
        if (resp.isSuccessful && resp.body() != null) {
            Result.Success(resp.body()!!)
        } else {
            Result.Error("Token poll failed: ${resp.code()}", resp.code())
        }
    }.getOrElse { Result.Error(it.message ?: "Unknown error") }

    // ── User ──────────────────────────────────────────────────────────────

    suspend fun getAuthenticatedUser(): Result<GitHubUser> {
        val api = apiService ?: return Result.Error("Not authenticated")
        return runCatching {
            val resp = api.getAuthenticatedUser()
            if (resp.isSuccessful && resp.body() != null) Result.Success(resp.body()!!)
            else Result.Error("Failed to get user: ${resp.code()}", resp.code())
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    // ── Fork ──────────────────────────────────────────────────────────────

    suspend fun getUserFork(sourceOwner: String, sourceRepo: String, username: String): Result<GitHubRepo?> {
        val api = apiService ?: return Result.Error("Not authenticated")
        return runCatching {
            val resp = api.getRepo(username, sourceRepo)
            val repo = resp.body()
            val expectedParent = "$sourceOwner/$sourceRepo"
            when {
                resp.isSuccessful && repo?.fork == true &&
                    (repo.parent == null || repo.parent.fullName == expectedParent) -> Result.Success(repo)
                resp.isSuccessful -> Result.Success(null)
                resp.code() == 404 -> Result.Success(null)
                else -> Result.Error("Failed to check fork: ${resp.code()}", resp.code())
            }
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    suspend fun forkRepo(owner: String, repo: String): Result<GitHubRepo> {
        val api = apiService ?: return Result.Error("Not authenticated")
        return runCatching {
            val resp = api.forkRepo(owner, repo)
            if (resp.isSuccessful && resp.body() != null) Result.Success(resp.body()!!)
            else Result.Error("Fork failed: ${resp.code()}", resp.code())
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    suspend fun checkBehind(
        sourceOwner: String,
        sourceRepo: String,
        baseBranch: String,
        headOwner: String,
        headBranch: String
    ): Result<CompareResult> {
        val api = apiService ?: return Result.Error("Not authenticated")
        return runCatching {
            val resp = api.compareCommits(
                sourceOwner,
                sourceRepo,
                "$baseBranch...$headOwner:$headBranch"
            )
            if (resp.isSuccessful && resp.body() != null) Result.Success(resp.body()!!)
            else Result.Error("Compare failed: ${resp.code()}", resp.code())
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    suspend fun syncFork(username: String, repo: String, branch: String): Result<SyncForkResponse> {
        val api = apiService ?: return Result.Error("Not authenticated")
        return runCatching {
            val resp = api.syncFork(username, repo, SyncForkRequest(branch))
            if (resp.isSuccessful && resp.body() != null) Result.Success(resp.body()!!)
            else Result.Error("Sync failed: ${resp.code()}", resp.code())
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    // ── Workflows ─────────────────────────────────────────────────────────

    suspend fun getWorkflow(owner: String, repo: String, workflowFile: String): Result<Workflow> {
        val api = apiService ?: return Result.Error("Not authenticated")
        return runCatching {
            val resp = api.listWorkflows(owner, repo)
            if (resp.isSuccessful) {
                val wf = resp.body()?.workflows?.find { it.path.endsWith(workflowFile) }
                if (wf != null) Result.Success(wf)
                else Result.Error("Workflow $workflowFile not found")
            } else {
                Result.Error("List workflows failed: ${resp.code()}", resp.code())
            }
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    suspend fun getWorkflowId(owner: String, repo: String, workflowFile: String): Result<Long> {
        return when (val result = getWorkflow(owner, repo, workflowFile)) {
            is Result.Success -> Result.Success(result.data.id)
            is Result.Error -> result
            Result.Loading -> Result.Loading
        }
    }

    suspend fun dispatchWorkflow(
        owner: String,
        repo: String,
        workflowId: Long,
        inputs: Map<String, String>,
        ref: String = "main"
    ): Result<Unit> {
        val api = apiService ?: return Result.Error("Not authenticated")
        return runCatching {
            val resp = api.dispatchWorkflow(owner, repo, workflowId.toString(), WorkflowDispatchRequest(ref, inputs))
            if (resp.isSuccessful) Result.Success(Unit)
            else Result.Error("Dispatch failed: ${resp.code()}", resp.code())
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    suspend fun enableWorkflow(owner: String, repo: String, workflowId: Long): Result<Unit> {
        val api = apiService ?: return Result.Error("Not authenticated")
        return runCatching {
            val resp = api.enableWorkflow(owner, repo, workflowId.toString())
            if (resp.isSuccessful) Result.Success(Unit)
            else Result.Error("Enable workflow failed: ${resp.code()}", resp.code())
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    suspend fun listRecentRuns(
        owner: String,
        repo: String,
        perPage: Int = 10,
        workflowId: Long? = null
    ): Result<List<WorkflowRun>> {
        val api = apiService ?: return Result.Error("Not authenticated")
        return runCatching<Result<List<WorkflowRun>>> {
            val resp = api.listWorkflowRuns(
                owner,
                repo,
                workflowId = workflowId?.toString(),
                perPage = perPage
            )
            if (resp.isSuccessful) {
                val runs: List<WorkflowRun> = resp.body()?.workflowRuns.orEmpty()
                Result.Success(runs)
            } else {
                Result.Error("List runs failed: ${resp.code()}", resp.code())
            }
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    suspend fun getWorkflowRun(owner: String, repo: String, runId: Long): Result<WorkflowRun> {
        val api = apiService ?: return Result.Error("Not authenticated")
        return runCatching {
            val resp = api.getWorkflowRun(owner, repo, runId)
            if (resp.isSuccessful && resp.body() != null) Result.Success(resp.body()!!)
            else Result.Error("Get run failed: ${resp.code()}", resp.code())
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    suspend fun deleteWorkflowRun(owner: String, repo: String, runId: Long): Result<Unit> {
        val api = apiService ?: return Result.Error("Not authenticated")
        return runCatching {
            val resp = api.deleteWorkflowRun(owner, repo, runId)
            when {
                resp.isSuccessful || resp.code() == 404 -> Result.Success(Unit)
                else -> Result.Error("Delete run failed: ${resp.code()}", resp.code())
            }
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    suspend fun listRunJobs(owner: String, repo: String, runId: Long): Result<List<WorkflowJob>> {
        val api = apiService ?: return Result.Error("Not authenticated")
        return runCatching<Result<List<WorkflowJob>>> {
            val resp = api.listRunJobs(owner, repo, runId)
            if (resp.isSuccessful) {
                val jobs: List<WorkflowJob> = resp.body()?.jobs.orEmpty()
                Result.Success(jobs)
            } else {
                Result.Error("List jobs failed: ${resp.code()}", resp.code())
            }
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    suspend fun listArtifacts(owner: String, repo: String, runId: Long): Result<List<Artifact>> {
        val api = apiService ?: return Result.Error("Not authenticated")
        return runCatching<Result<List<Artifact>>> {
            val resp = api.listArtifacts(owner, repo, runId)
            if (resp.isSuccessful) {
                val artifacts: List<Artifact> = resp.body()?.artifacts.orEmpty()
                Result.Success(artifacts)
            } else {
                Result.Error("List artifacts failed: ${resp.code()}", resp.code())
            }
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    suspend fun getReleaseByTag(owner: String, repo: String, tag: String): Result<GitHubRelease?> {
        val api = apiService ?: return Result.Error("Not authenticated")
        return runCatching<Result<GitHubRelease?>> {
            val resp = api.getReleaseByTag(owner, repo, tag)
            when {
                resp.isSuccessful -> Result.Success(resp.body())
                resp.code() == 404 -> Result.Success(null)
                else -> Result.Error("Get release failed: ${resp.code()}", resp.code())
            }
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }
}
