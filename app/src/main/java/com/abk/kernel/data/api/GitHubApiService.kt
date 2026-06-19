package com.abk.kernel.data.api

import com.abk.kernel.data.model.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface GitHubApiService {

    // ── User ──────────────────────────────────────────────────────────────
    @GET("user")
    suspend fun getAuthenticatedUser(): Response<GitHubUser>

    // ── Repos ─────────────────────────────────────────────────────────────
    @GET("repos/{owner}/{repo}")
    suspend fun getRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<GitHubRepo>

    @GET("repos/{owner}/{repo}/actions/secrets/public-key")
    suspend fun getRepositorySecretPublicKey(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<GitHubSecretPublicKey>

    @GET("repos/{owner}/{repo}/actions/secrets")
    suspend fun listRepositorySecrets(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 100
    ): Response<GitHubRepositorySecretsResponse>

    @PUT("repos/{owner}/{repo}/actions/secrets/{secret_name}")
    suspend fun createOrUpdateRepositorySecret(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("secret_name") secretName: String,
        @Body body: CreateOrUpdateRepositorySecretRequest
    ): Response<Unit>

    @POST("repos/{owner}/{repo}/forks")
    suspend fun forkRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: ForkRequest = ForkRequest()
    ): Response<GitHubRepo>

    @GET("repos/{owner}/{repo}/compare/{basehead}")
    suspend fun compareCommits(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("basehead") baseHead: String
    ): Response<CompareResult>

    // Sync fork (merge upstream into fork)
    @POST("repos/{owner}/{repo}/merge-upstream")
    suspend fun syncFork(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: SyncForkRequest
    ): Response<SyncForkResponse>

    // ── Workflows ─────────────────────────────────────────────────────────
    @GET("repos/{owner}/{repo}/actions/workflows")
    suspend fun listWorkflows(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<WorkflowsResponse>

    @POST("repos/{owner}/{repo}/actions/workflows/{workflow_id}/dispatches")
    suspend fun dispatchWorkflow(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflow_id") workflowId: String,
        @Body body: WorkflowDispatchRequest
    ): Response<Unit>

    @PUT("repos/{owner}/{repo}/actions/workflows/{workflow_id}/enable")
    suspend fun enableWorkflow(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflow_id") workflowId: String
    ): Response<Unit>

    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun listWorkflowRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("workflow_id") workflowId: String? = null,
        @Query("per_page") perPage: Int = 10,
        @Query("page") page: Int = 1
    ): Response<WorkflowRunsResponse>

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}")
    suspend fun getWorkflowRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): Response<WorkflowRun>

    @DELETE("repos/{owner}/{repo}/actions/runs/{run_id}")
    suspend fun deleteWorkflowRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): Response<Unit>

    @POST("repos/{owner}/{repo}/actions/runs/{run_id}/cancel")
    suspend fun cancelWorkflowRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): Response<Unit>

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/jobs")
    suspend fun listRunJobs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long,
        @Query("per_page") perPage: Int = 100
    ): Response<WorkflowJobsResponse>

    @Streaming
    @GET("repos/{owner}/{repo}/actions/jobs/{job_id}/logs")
    suspend fun downloadJobLogs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("job_id") jobId: Long
    ): Response<ResponseBody>

    @Streaming
    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/logs")
    suspend fun downloadRunLogs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): Response<ResponseBody>

    // ── Artifacts ─────────────────────────────────────────────────────────
    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/artifacts")
    suspend fun listArtifacts(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long,
        @Query("per_page") perPage: Int = 100
    ): Response<ArtifactsResponse>

    // ── Releases ─────────────────────────────────────────────────────────
    @GET("repos/{owner}/{repo}/releases")
    suspend fun listReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): Response<List<GitHubReleaseSummary>>

    @GET("repos/{owner}/{repo}/releases/tags/{tag}")
    suspend fun getReleaseByTag(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("tag") tag: String
    ): Response<GitHubRelease>

    @POST("repos/{owner}/{repo}/releases")
    suspend fun createRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CreateReleaseRequest
    ): Response<GitHubRelease>

    @PATCH("repos/{owner}/{repo}/releases/{release_id}")
    suspend fun updateRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("release_id") releaseId: Long,
        @Body body: CreateReleaseRequest
    ): Response<GitHubRelease>

    @GET("repos/{owner}/{repo}/releases/{release_id}/assets")
    suspend fun listReleaseAssets(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("release_id") releaseId: Long,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): Response<List<ReleaseAsset>>

    @DELETE("repos/{owner}/{repo}/releases/assets/{asset_id}")
    suspend fun deleteReleaseAsset(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("asset_id") assetId: Long
    ): Response<Unit>

    @Streaming
    @GET("repos/{owner}/{repo}/releases/assets/{asset_id}")
    suspend fun downloadReleaseAssetById(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("asset_id") assetId: Long,
        @Header("Accept") accept: String = "application/octet-stream"
    ): Response<ResponseBody>

    @Streaming
    @GET("repos/{owner}/{repo}/actions/artifacts/{artifact_id}/zip")
    suspend fun downloadArtifact(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("artifact_id") artifactId: Long
    ): Response<ResponseBody>
}
