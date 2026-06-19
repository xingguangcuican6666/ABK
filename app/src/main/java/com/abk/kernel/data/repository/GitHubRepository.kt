package com.abk.kernel.data.repository
import com.abk.kernel.tr
import com.abk.kernel.R

import com.abk.kernel.BuildConfig
import com.abk.kernel.data.api.GitHubApiService
import com.abk.kernel.data.api.GitHubAuthService
import com.abk.kernel.data.api.NetworkClient
import com.abk.kernel.data.model.*
import com.abk.kernel.utils.ForkSigningManager
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val code: Int = -1) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

open class GitHubRepository(
    private val authService: GitHubAuthService = NetworkClient.createAuthService(),
    private var apiService: GitHubApiService = NetworkClient.createApiService()
) {
    private val clientId = BuildConfig.GITHUB_CLIENT_ID
    private var currentToken: String? = null
    private val publicHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun updateToken(token: String?) {
        currentToken = token
        apiService = NetworkClient.createApiService(token)
    }

    // ── Auth ──────────────────────────────────────────────────────────────

    open suspend fun requestDeviceCode(): Result<DeviceCodeResponse> = runCatching {
        val resp = authService.requestDeviceCode(clientId)
        if (resp.isSuccessful && resp.body() != null) {
            Result.Success(resp.body()!!)
        } else {
            Result.Error("Device code request failed: ${resp.code()}", resp.code())
        }
    }.getOrElse { Result.Error(it.message ?: "Unknown error") }

    open suspend fun pollToken(deviceCode: String): Result<AccessTokenResponse> = runCatching {
        val resp = authService.pollAccessToken(clientId, deviceCode)
        if (resp.isSuccessful && resp.body() != null) {
            Result.Success(resp.body()!!)
        } else {
            Result.Error("Token poll failed: ${resp.code()}", resp.code())
        }
    }.getOrElse { Result.Error(it.message ?: "Unknown error") }

    // ── Module Catalogs ───────────────────────────────────────────────────

    suspend fun fetchExternalModuleMetadata(repositoryUrl: String): Result<ExternalModuleMetadata> =
        withContext(Dispatchers.IO) {
            val candidates = externalModuleConfCandidates(repositoryUrl)
            if (candidates.isEmpty()) {
                return@withContext Result.Error(tr(R.string.gh_repo_link_unsupported))
            }

            var lastError = ""
            for (confUrl in candidates) {
                val request = Request.Builder()
                    .url(confUrl)
                    .header("Accept", "text/plain,*/*")
                    .build()
                val response = runCatching { publicHttpClient.newCall(request).execute() }
                    .getOrElse {
                        lastError = it.message ?: tr(R.string.gh_network_request_failed)
                        null
                    } ?: continue

                response.use { resp ->
                    if (!resp.isSuccessful) {
                        lastError = "HTTP ${resp.code}"
                        return@use
                    }

                    val body = resp.body?.string().orEmpty()
                    return@withContext runCatching { parseExternalModuleConf(body) }
                        .fold(
                            onSuccess = { Result.Success(it) },
                            onFailure = { Result.Error(tr(R.string.gh_module_conf_invalid, it.message ?: tr(R.string.gh_format_error))) }
                        )
                }
            }

            Result.Error(tr(R.string.gh_module_conf_unreadable, lastError))
        }

    suspend fun fetchBuildModuleCatalog(repositoryUrl: String): Result<ModuleCatalogFetchResult> =
        withContext(Dispatchers.IO) {
            val candidates = moduleCatalogIndexCandidates(repositoryUrl)
            if (candidates.isEmpty()) {
                return@withContext Result.Error(tr(R.string.gh_repo_link_unsupported))
            }

            var lastError = ""
            for (indexUrl in candidates) {
                val request = Request.Builder()
                    .url(indexUrl)
                    .header("Accept", "application/json,text/plain,*/*")
                    .build()
                val response = runCatching { publicHttpClient.newCall(request).execute() }
                    .getOrElse {
                        lastError = it.message ?: tr(R.string.gh_network_request_failed)
                        null
                    } ?: continue

                response.use { resp ->
                    if (!resp.isSuccessful) {
                        lastError = "HTTP ${resp.code}"
                        return@use
                    }

                    val body = resp.body?.string().orEmpty()
                    val catalog = runCatching { parseModuleCatalogDocument(body, repositoryUrl) }
                        .getOrElse {
                            lastError = tr(R.string.gh_json_parse_failed, it.message ?: tr(R.string.gh_format_error))
                            return@use
                        }

                    return@withContext Result.Success(
                        ModuleCatalogFetchResult(
                            name = catalog.name,
                            indexUrl = indexUrl,
                            modules = catalog.modules,
                            skippedCount = catalog.skippedCount
                        )
                    )
                }
            }

            Result.Error(tr(R.string.gh_catalog_json_unreadable, lastError))
        }

    suspend fun fetchRuntimeModuleCatalog(repositoryUrl: String): Result<RuntimeModuleCatalogFetchResult> =
        withContext(Dispatchers.IO) {
            val candidates = runtimeModuleCatalogCandidates(repositoryUrl)
            if (candidates.isEmpty()) {
                return@withContext Result.Error(tr(R.string.gh_repo_link_unsupported))
            }

            var lastError = ""
            for (indexUrl in candidates) {
                val request = Request.Builder()
                    .url(indexUrl)
                    .header("Accept", "application/json,text/plain,*/*")
                    .build()
                val response = runCatching { publicHttpClient.newCall(request).execute() }
                    .getOrElse {
                        lastError = it.message ?: tr(R.string.gh_network_request_failed)
                        null
                    } ?: continue

                response.use { resp ->
                    if (!resp.isSuccessful) {
                        lastError = "HTTP ${resp.code}"
                        return@use
                    }

                    val body = resp.body?.string().orEmpty()
                    val catalog = runCatching { parseRuntimeModuleCatalogDocument(body, repositoryUrl) }
                        .getOrElse {
                            lastError = tr(R.string.gh_json_parse_failed, it.message ?: tr(R.string.gh_format_error))
                            return@use
                        }

                    return@withContext Result.Success(
                        RuntimeModuleCatalogFetchResult(
                            name = catalog.name,
                            indexUrl = indexUrl,
                            modules = catalog.modules,
                            skippedCount = catalog.skippedCount
                        )
                    )
                }
            }

            Result.Error(tr(R.string.gh_catalog_json_unreadable, lastError))
        }

    suspend fun fetchAppUpdateMetadata(
        metadataUrl: String = BuildConfig.APP_UPDATE_METADATA_URL
    ): Result<AppUpdateMetadata> = withContext(Dispatchers.IO) {
        val url = metadataUrl.trim()
        if (url.isBlank()) {
            return@withContext Result.Error("App update metadata URL is empty")
        }

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json,text/plain,*/*")
            .build()

        val response = runCatching { publicHttpClient.newCall(request).execute() }
            .getOrElse { return@withContext Result.Error(it.message ?: tr(R.string.gh_network_request_failed)) }

        response.use { resp ->
            if (!resp.isSuccessful) {
                return@withContext Result.Error("HTTP ${resp.code}")
            }

            val body = resp.body?.string().orEmpty()
            return@withContext runCatching { parseAppUpdateMetadata(body) }
                .fold(
                    onSuccess = { Result.Success(it) },
                    onFailure = { Result.Error(it.message ?: tr(R.string.gh_format_error)) }
                )
        }
    }

    // ── User ──────────────────────────────────────────────────────────────

    suspend fun getAuthenticatedUser(): Result<GitHubUser> {
        val api = apiService
        return runCatching {
            val resp = api.getAuthenticatedUser()
            if (resp.isSuccessful && resp.body() != null) Result.Success(resp.body()!!)
            else Result.Error("Failed to get user: ${resp.code()}", resp.code())
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    // ── Fork ──────────────────────────────────────────────────────────────

    open suspend fun getUserFork(sourceOwner: String, sourceRepo: String, username: String): Result<GitHubRepo?> {
        val api = apiService
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

    open suspend fun forkRepo(owner: String, repo: String): Result<GitHubRepo> {
        val api = apiService
        return runCatching {
            val resp = api.forkRepo(owner, repo)
            if (resp.isSuccessful && resp.body() != null) Result.Success(resp.body()!!)
            else Result.Error("Fork failed: ${resp.code()}", resp.code())
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    suspend fun getRepositorySecretPublicKey(owner: String, repo: String): Result<GitHubSecretPublicKey> {
        val api = apiService
        return runCatching {
            val resp = api.getRepositorySecretPublicKey(owner, repo)
            if (resp.isSuccessful && resp.body() != null) Result.Success(resp.body()!!)
            else Result.Error("Get repo secret public key failed: ${resp.code()}", resp.code())
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    suspend fun listRepositorySecrets(owner: String, repo: String): Result<List<GitHubRepositorySecret>> {
        val api = apiService
        return runCatching {
            val resp = api.listRepositorySecrets(owner, repo)
            if (resp.isSuccessful) {
                Result.Success(resp.body()?.secrets.orEmpty())
            } else {
                Result.Error("List repo secrets failed: ${resp.code()}", resp.code())
            }
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    suspend fun createOrUpdateRepositorySecret(
        owner: String,
        repo: String,
        secretName: String,
        secretValue: String
    ): Result<Unit> {
        val publicKey = when (val result = getRepositorySecretPublicKey(owner, repo)) {
            is Result.Success -> result.data
            is Result.Error -> return result
            Result.Loading -> return Result.Error("Repository secret public key is still loading")
        }
        val encryptedValue = ForkSigningManager.encryptSecretForGitHub(secretValue, publicKey)
        val api = apiService
        return runCatching {
            val resp = api.createOrUpdateRepositorySecret(
                owner,
                repo,
                secretName,
                CreateOrUpdateRepositorySecretRequest(
                    encryptedValue = encryptedValue,
                    keyId = publicKey.keyId
                )
            )
            if (resp.isSuccessful) Result.Success(Unit)
            else Result.Error("Update repo secret failed: ${resp.code()}", resp.code())
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    open suspend fun checkBehind(
        sourceOwner: String,
        sourceRepo: String,
        baseBranch: String,
        headOwner: String,
        headBranch: String
    ): Result<CompareResult> {
        val api = apiService
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
        val api = apiService
        return runCatching {
            val resp = api.syncFork(username, repo, SyncForkRequest(branch))
            if (resp.isSuccessful && resp.body() != null) Result.Success(resp.body()!!)
            else Result.Error("Sync failed: ${resp.code()}", resp.code())
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    // ── Workflows ─────────────────────────────────────────────────────────

    suspend fun getWorkflow(owner: String, repo: String, workflowFile: String): Result<Workflow> {
        val api = apiService
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
        val api = apiService
        return runCatching {
            val resp = api.dispatchWorkflow(owner, repo, workflowId.toString(), WorkflowDispatchRequest(ref, inputs))
            if (resp.isSuccessful) Result.Success(Unit)
            else Result.Error("Dispatch failed: ${resp.code()}", resp.code())
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    suspend fun enableWorkflow(owner: String, repo: String, workflowId: Long): Result<Unit> {
        val api = apiService
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
        val api = apiService
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
        val api = apiService
        return runCatching {
            val resp = api.getWorkflowRun(owner, repo, runId)
            if (resp.isSuccessful && resp.body() != null) Result.Success(resp.body()!!)
            else Result.Error("Get run failed: ${resp.code()}", resp.code())
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    suspend fun deleteWorkflowRun(owner: String, repo: String, runId: Long): Result<Unit> {
        val api = apiService
        return runCatching {
            val resp = api.deleteWorkflowRun(owner, repo, runId)
            when {
                resp.isSuccessful || resp.code() == 404 -> Result.Success(Unit)
                else -> Result.Error("Delete run failed: ${resp.code()}", resp.code())
            }
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    suspend fun cancelWorkflowRun(owner: String, repo: String, runId: Long): Result<Unit> {
        val api = apiService
        return runCatching {
            val resp = api.cancelWorkflowRun(owner, repo, runId)
            when {
                resp.isSuccessful || resp.code() == 409 -> Result.Success(Unit)
                else -> Result.Error("Cancel run failed: ${resp.code()}", resp.code())
            }
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    suspend fun listRunJobs(owner: String, repo: String, runId: Long): Result<List<WorkflowJob>> {
        val api = apiService
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

    suspend fun downloadJobLogs(owner: String, repo: String, jobId: Long): Result<String> {
        val api = apiService
        return runCatching<Result<String>> {
            val resp = api.downloadJobLogs(owner, repo, jobId)
            if (resp.isSuccessful) {
                val logs = resp.body()?.use { it.string() }.orEmpty()
                Result.Success(logs)
            } else {
                Result.Error("Download job logs failed: ${resp.code()}", resp.code())
            }
        }.getOrElse { Result.Error(it.toDownloadLogMessage("Download job logs failed")) }
    }

    suspend fun downloadRunLogs(owner: String, repo: String, runId: Long): Result<String> {
        val api = apiService
        return runCatching<Result<String>> {
            val resp = api.downloadRunLogs(owner, repo, runId)
            if (resp.isSuccessful) {
                val logs = resp.body()?.use { it.readZipText() }.orEmpty()
                Result.Success(logs)
            } else {
                Result.Error("Download run logs failed: ${resp.code()}", resp.code())
            }
        }.getOrElse { Result.Error(it.toDownloadLogMessage("Download run logs failed")) }
    }

    suspend fun listArtifacts(owner: String, repo: String, runId: Long): Result<List<Artifact>> {
        val api = apiService
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

    suspend fun listReleases(owner: String, repo: String, perPage: Int = 100): Result<List<GitHubReleaseSummary>> {
        val api = apiService
        return runCatching<Result<List<GitHubReleaseSummary>>> {
            val collected = mutableListOf<GitHubReleaseSummary>()
            var page = 1
            while (true) {
                val resp = api.listReleases(owner, repo, perPage = perPage, page = page)
                if (!resp.isSuccessful) {
                    return@runCatching Result.Error("List releases failed: ${resp.code()}", resp.code())
                }
                val releases = resp.body().orEmpty()
                collected += releases
                if (releases.size < perPage) break
                page += 1
            }
            Result.Success(collected)
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    suspend fun getReleaseByTag(owner: String, repo: String, tag: String): Result<GitHubRelease?> {
        val api = apiService
        return runCatching<Result<GitHubRelease?>> {
            val resp = api.getReleaseByTag(owner, repo, tag)
            when {
                resp.isSuccessful -> Result.Success(resp.body())
                resp.code() == 404 -> Result.Success(null)
                else -> Result.Error("Get release failed: ${resp.code()}", resp.code())
            }
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    suspend fun listReleaseAssets(
        owner: String,
        repo: String,
        releaseId: Long,
        perPage: Int = 100
    ): Result<List<ReleaseAsset>> {
        val api = apiService
        return runCatching<Result<List<ReleaseAsset>>> {
            val collected = mutableListOf<ReleaseAsset>()
            var page = 1
            while (true) {
                val resp = api.listReleaseAssets(owner, repo, releaseId, perPage = perPage, page = page)
                if (!resp.isSuccessful) {
                    return@runCatching Result.Error("List release assets failed: ${resp.code()}", resp.code())
                }
                val assets = resp.body().orEmpty()
                collected += assets
                if (assets.size < perPage) break
                page += 1
            }
            Result.Success(collected)
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    suspend fun createRelease(owner: String, repo: String, request: CreateReleaseRequest): Result<GitHubRelease> {
        val api = apiService
        return runCatching {
            val resp = api.createRelease(owner, repo, request)
            if (resp.isSuccessful && resp.body() != null) Result.Success(resp.body()!!)
            else Result.Error("Create release failed: ${resp.code()}", resp.code())
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    suspend fun updateRelease(owner: String, repo: String, releaseId: Long, request: CreateReleaseRequest): Result<GitHubRelease> {
        val api = apiService
        return runCatching {
            val resp = api.updateRelease(owner, repo, releaseId, request)
            if (resp.isSuccessful && resp.body() != null) Result.Success(resp.body()!!)
            else Result.Error("Update release failed: ${resp.code()}", resp.code())
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    suspend fun deleteReleaseAsset(owner: String, repo: String, assetId: Long): Result<Unit> {
        val api = apiService
        return runCatching {
            val resp = api.deleteReleaseAsset(owner, repo, assetId)
            when {
                resp.isSuccessful || resp.code() == 404 -> Result.Success(Unit)
                else -> Result.Error("Delete release asset failed: ${resp.code()}", resp.code())
            }
        }.getOrElse { Result.Error(it.message ?: "Unknown error") }
    }

    suspend fun uploadReleaseAsset(
        uploadUrlTemplate: String,
        fileName: String,
        contentType: String,
        content: ByteArray
    ): Result<ReleaseAsset> = withContext(Dispatchers.IO) {
        val uploadUrl = uploadUrlTemplate.substringBefore('{')
        val encodedName = java.net.URLEncoder.encode(fileName, "UTF-8")
        val request = Request.Builder()
            .url("$uploadUrl?name=$encodedName")
            .header("Accept", "application/vnd.github+json")
            .apply {
                currentToken?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
            }
            .post(content.toRequestBody(contentType.toMediaType()))
            .build()
        val response = runCatching { publicHttpClient.newCall(request).execute() }
            .getOrElse { return@withContext Result.Error(it.message ?: "Unknown error") }
        response.use { resp ->
            if (!resp.isSuccessful) {
                return@withContext Result.Error("Upload release asset failed: ${resp.code}", resp.code)
            }
            val body = resp.body?.string().orEmpty()
            val json = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
                ?: return@withContext Result.Error("Upload release asset returned invalid JSON")
            Result.Success(
                ReleaseAsset(
                    id = json.get("id")?.asLong ?: 0L,
                    name = json.get("name")?.asString ?: fileName,
                    size = json.get("size")?.asLong ?: content.size.toLong(),
                    contentType = json.get("content_type")?.asString,
                    browserDownloadUrl = json.get("browser_download_url")?.asString.orEmpty()
                )
            )
        }
    }

    suspend fun downloadReleaseAssetText(owner: String, repo: String, releaseId: Long, assetName: String): Result<String> {
        return when (val assets = listReleaseAssets(owner, repo, releaseId)) {
            is Result.Success -> {
                val asset = assets.data.firstOrNull { it.name == assetName }
                    ?: return Result.Error("Release asset $assetName not found")
                val api = apiService
                runCatching {
                    val resp = api.downloadReleaseAssetById(owner, repo, asset.id)
                    if (resp.isSuccessful) {
                        Result.Success(resp.body()?.string().orEmpty())
                    } else {
                        Result.Error("Download release asset failed: ${resp.code()}", resp.code())
                    }
                }.getOrElse { Result.Error(it.message ?: "Unknown error") }
            }
            is Result.Error -> assets
            Result.Loading -> Result.Loading
        }
    }

    private fun ResponseBody.readZipText(): String {
        val output = StringBuilder()
        ZipInputStream(byteStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    output.appendLine("===== ${entry.name} =====")
                    val buffer = ByteArray(DEFAULT_LOG_BUFFER_SIZE)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read <= 0) break
                        output.append(String(buffer, 0, read, Charsets.UTF_8))
                    }
                    output.appendLine()
                }
                zip.closeEntry()
            }
        }
        return output.toString()
    }

    private fun Throwable.toDownloadLogMessage(prefix: String): String {
        val type = this::class.java.simpleName.ifBlank { "Exception" }
        val detail = message?.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()
        return "$prefix: $type$detail"
    }

    internal fun moduleCatalogIndexCandidates(repositoryUrl: String): List<String> {
        val clean = repositoryUrl.trim().trimEnd('/')
        if (clean.isBlank()) return emptyList()
        if (clean.endsWith(".json", ignoreCase = true)) return listOf(clean)
        if (clean.startsWith("https://raw.githubusercontent.com/")) {
            return listOf("$clean/$MODULE_CATALOG_INDEX_FILE")
        }

        parseGithubRepository(clean)?.let { github ->
            val branches = if (github.branch.isNullOrBlank()) {
                listOf("main", "master")
            } else {
                listOf(github.branch)
            }
            return branches.map { branch ->
                "https://raw.githubusercontent.com/${github.owner}/${github.repo}/$branch/$MODULE_CATALOG_INDEX_FILE"
            }
        }

        return emptyList()
    }

    internal fun runtimeModuleCatalogCandidates(repositoryUrl: String): List<String> {
        val clean = repositoryUrl.trim().trimEnd('/')
        if (clean.isBlank()) return emptyList()
        if (clean.endsWith(".json", ignoreCase = true)) return listOf(clean)
        return emptyList()
    }

    internal fun externalModuleConfCandidates(repositoryUrl: String): List<String> {
        val clean = repositoryUrl.trim().trimEnd('/')
        if (clean.isBlank()) return emptyList()
        if (clean.endsWith("/module.conf", ignoreCase = true)) return listOf(clean)
        if (clean.startsWith("https://raw.githubusercontent.com/")) {
            return listOf("$clean/module.conf")
        }

        parseGithubRepository(clean)?.let { github ->
            val branches = if (github.branch.isNullOrBlank()) {
                listOf("main", "master")
            } else {
                listOf(github.branch)
            }
            return branches.map { branch ->
                "https://raw.githubusercontent.com/${github.owner}/${github.repo}/$branch/module.conf"
            }
        }

        return emptyList()
    }

    internal fun parseGithubRepository(url: String): GithubRepositoryParts? {
        val cleaned = url.trim().trimEnd('/')
        val path = when {
            cleaned.startsWith("git@github.com:") -> cleaned.removePrefix("git@github.com:")
            cleaned.startsWith("https://github.com/") -> cleaned.removePrefix("https://github.com/")
            cleaned.startsWith("http://github.com/") -> cleaned.removePrefix("http://github.com/")
            cleaned.startsWith("github.com/") -> cleaned.removePrefix("github.com/")
            else -> return null
        }
        val parts = path.split('/').filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val owner = parts[0]
        val repo = parts[1].removeSuffix(".git")
        if (owner.isBlank() || repo.isBlank()) return null
        val branch = if (parts.size >= 4 && parts[2] == "tree") {
            parts.drop(3).joinToString("/")
        } else {
            null
        }
        return GithubRepositoryParts(owner, repo, branch)
    }

    internal fun parseModuleCatalogDocument(body: String, repositoryUrl: String): ParsedModuleCatalogDocument {
        val root = JsonParser.parseString(body)
        val document = root.asJsonObjectOrNull() ?: error(tr(R.string.gh_root_must_be_object))
        val rawModules = document.arrayOrEmpty("modules")
        val modules = rawModules.mapNotNull { element ->
            element.asJsonObjectOrNull()?.let(::sanitizeCatalogItem)
        }.distinctBy { it.repoUrl.trim().lowercase() }
        return ParsedModuleCatalogDocument(
            name = document.stringOrEmpty("name").ifBlank { repositoryUrl.toCatalogFallbackName() },
            modules = modules,
            skippedCount = (rawModules.size() - modules.size).coerceAtLeast(0)
        )
    }

    internal fun parseRuntimeModuleCatalogDocument(
        body: String,
        repositoryUrl: String
    ): ParsedRuntimeModuleCatalogDocument {
        val root = JsonParser.parseString(body)
        val document = root.asJsonObjectOrNull() ?: error(tr(R.string.gh_root_must_be_object))
        val rawModules = document.arrayOrEmpty("modules")
        val modules = rawModules.mapNotNull { element ->
            element.asJsonObjectOrNull()?.let(::sanitizeRuntimeCatalogItem)
        }.distinctBy { item ->
            item.id.trim().lowercase().ifBlank { item.name.trim().lowercase() }
        }
        return ParsedRuntimeModuleCatalogDocument(
            name = document.stringOrEmpty("name").ifBlank { repositoryUrl.toCatalogFallbackName() },
            modules = modules,
            skippedCount = (rawModules.size() - modules.size).coerceAtLeast(0)
        )
    }

    internal fun parseAppUpdateMetadata(body: String): AppUpdateMetadata {
        val root = JsonParser.parseString(body)
        val document = root.asJsonObjectOrNull() ?: error(tr(R.string.gh_root_must_be_object))
        return AppUpdateMetadata(
            stable = parseAppUpdateChannelEntries(document.objectOrNull("stable")),
            unstable = parseAppUpdateChannelEntries(document.objectOrNull("unstable"))
        )
    }

    private fun parseAppUpdateChannelEntries(raw: JsonObject?): AppUpdateChannelEntries =
        AppUpdateChannelEntries(
            normal = raw?.objectOrNull("normal")?.let(::sanitizeAppUpdateEntry),
            dev = raw?.objectOrNull("dev")?.let(::sanitizeAppUpdateEntry)
        )

    private fun sanitizeAppUpdateEntry(raw: JsonObject): AppUpdateEntry? {
        val versionCode = raw.longOrZero("versionCode")
            .takeIf { it > 0L }
            ?: raw.longOrZero("version_code").takeIf { it > 0L }
            ?: return null
        val versionName = raw.stringOrEmpty("versionName")
            .ifBlank { raw.stringOrEmpty("version_name") }
            .ifBlank { versionCode.toString() }
        return AppUpdateEntry(
            versionName = versionName,
            versionCode = versionCode,
            downloadUrl = raw.stringOrEmpty("downloadUrl").ifBlank { raw.stringOrEmpty("download_url") },
            publishedAt = raw.stringOrEmpty("publishedAt").ifBlank { raw.stringOrEmpty("published_at") },
            buildTimestampEpochMillis = raw.longOrZero("buildTimestampEpochMillis")
                .takeIf { it > 0L }
                ?: raw.longOrZero("build_timestamp_epoch_millis"),
            sourceWorkflow = raw.stringOrEmpty("sourceWorkflow").ifBlank { raw.stringOrEmpty("source_workflow") },
            commitSha = raw.stringOrEmpty("commitSha").ifBlank { raw.stringOrEmpty("commit_sha") },
            runId = raw.longOrZero("runId").takeIf { it > 0L } ?: raw.longOrZero("run_id")
        )
    }

    private fun sanitizeCatalogItem(raw: JsonObject): ModuleCatalogItem? {
        val repoUrl = raw.stringOrEmpty("repoUrl")
        if (repoUrl.isBlank()) return null
        val kind = ModuleCatalogItemKind.normalize(
            raw.stringOrEmpty("kind").ifBlank { raw.stringOrEmpty("type") }
        )
        val moduleSetId = raw.stringOrEmpty("moduleSetId")
            .ifBlank { raw.stringOrEmpty("module_set_id") }
            .ifBlank { if (kind == ModuleCatalogItemKind.MODULE_SET) repoUrl.toCatalogFallbackName() else "" }
        val supportedStages = raw.stringList("supportedStages")
            .map { CustomExternalModuleStage.normalize(it) }
            .distinct()
            .ifEmpty { listOf(CustomExternalModuleStage.AFTER_PATCH) }
        val defaultStage = CustomExternalModuleStage.normalize(raw.stringOrEmpty("defaultStage"))
            .takeIf { it in supportedStages }
            ?: supportedStages.first()
        val recommendedStages = raw.stringList("recommendedStages")
            .ifEmpty { raw.stringList("recommend") }
            .ifEmpty { raw.csvString("recommendedStage") }
            .ifEmpty { raw.csvString("recommend") }
            .ifEmpty { listOf(defaultStage) }
            .map { CustomExternalModuleStage.normalize(it) }
            .distinct()
            .filter { it in supportedStages }
            .ifEmpty { listOf(defaultStage) }

        return ModuleCatalogItem(
            name = raw.stringOrEmpty("name").ifBlank { repoUrl.toCatalogFallbackName() },
            version = raw.stringOrEmpty("version"),
            description = raw.stringOrEmpty("description"),
            kind = kind,
            moduleSetId = moduleSetId,
            repoUrl = repoUrl,
            defaultStage = defaultStage,
            supportedStages = supportedStages,
            recommendedStages = recommendedStages,
            author = raw.stringOrEmpty("author"),
            homepage = raw.stringOrEmpty("homepage")
        )
    }

    private fun sanitizeRuntimeCatalogItem(raw: JsonObject): RuntimeModuleCatalogItem? {
        val versions = raw.arrayOrEmpty("versions")
        val latestVersion = versions.firstOrNull()?.asJsonObjectOrNull()
        val zipUrl = latestVersion?.stringOrEmpty("zipUrl").orEmpty()
        val name = raw.stringOrEmpty("name")
        if (name.isBlank() || zipUrl.isBlank()) return null
        return RuntimeModuleCatalogItem(
            id = raw.stringOrEmpty("id").ifBlank { name.lowercase().replace(' ', '_') },
            name = name,
            version = latestVersion?.stringOrEmpty("version").orEmpty().ifBlank { raw.stringOrEmpty("version") },
            versionCode = latestVersion?.longOrZero("versionCode").takeIf { it != null && it > 0L }
                ?: raw.longOrZero("versionCode"),
            author = raw.stringOrEmpty("author"),
            description = raw.stringOrEmpty("description"),
            zipUrl = zipUrl,
            changelog = latestVersion?.stringOrEmpty("changelog").orEmpty(),
            support = raw.stringOrEmpty("support"),
            donate = raw.stringOrEmpty("donate"),
            website = raw.stringOrEmpty("website"),
            cover = raw.stringOrEmpty("cover"),
            icon = raw.stringOrEmpty("icon"),
            verified = raw.booleanOrFalse("verified"),
            minApi = raw.intOrNull("minApi"),
            maxApi = raw.intOrNull("maxApi")
        )
    }

    internal fun parseExternalModuleConf(body: String): ExternalModuleMetadata {
        val values = parseShellLikeConf(body)
        val kind = ModuleCatalogItemKind.normalize(values["ABK_MODULE_KIND"])
        val name = (
            if (kind == ModuleCatalogItemKind.MODULE_SET) {
                values["ABK_MODULE_SET_NAME"]
            } else {
                values["ABK_MODULE_NAME"]
            }
        ).orEmpty().trim()
        if (name.isBlank()) error(tr(R.string.gh_missing_module_name))
        val moduleSetId = values["ABK_MODULE_SET_ID"].orEmpty().trim()
        val supportedStages = values["ABK_MODULE_SUPPORTED_STAGES"]
            ?.takeIf { it.isNotBlank() }
            ?.split(',')
            ?.map { CustomExternalModuleStage.normalize(it) }
            ?.distinct()
            .orEmpty()
            .ifEmpty { CustomExternalModuleStage.options }
        val defaultStage = CustomExternalModuleStage.normalize(
            values["ABK_MODULE_DEFAULT_STAGE"]
                ?: values["ABK_MODULE_RECOMMENDED_STAGE"]
                ?: values["ABK_MODULE_STAGE"]
                ?: ""
        ).takeIf { it in supportedStages } ?: supportedStages.first()
        val recommendedStages = (
            values["ABK_MODULE_RECOMMENDED_STAGES"]
                ?: values["ABK_MODULE_RECOMMEND_STAGES"]
                ?: values["ABK_MODULE_RECOMMENDED_STAGE"]
                ?: values["ABK_MODULE_RECOMMEND_STAGE"]
                ?: values["ABK_MODULE_RECOMMEND"]
                ?: values["ABK_MODULE_DEFAULT_STAGE"]
                ?: values["ABK_MODULE_STAGE"]
        )
            ?.split(',')
            ?.map { CustomExternalModuleStage.normalize(it) }
            ?.distinct()
            ?.filter { it in supportedStages }
            .orEmpty()
            .ifEmpty { listOf(defaultStage) }
        val children = if (kind == ModuleCatalogItemKind.MODULE_SET) {
            parseModuleSetChildren(values["ABK_MODULE_SET_ITEMS"].orEmpty())
        } else {
            emptyList()
        }
        if (kind == ModuleCatalogItemKind.MODULE_SET && children.isEmpty()) {
            error(tr(R.string.gh_missing_module_name))
        }
        return ExternalModuleMetadata(
            name = name,
            version = (
                if (kind == ModuleCatalogItemKind.MODULE_SET) {
                    values["ABK_MODULE_SET_VERSION"]
                } else {
                    values["ABK_MODULE_VERSION"]
                }
            ).orEmpty().trim(),
            description = (
                if (kind == ModuleCatalogItemKind.MODULE_SET) {
                    values["ABK_MODULE_SET_DESCRIPTION"]
                } else {
                    values["ABK_MODULE_DESCRIPTION"]
                }
            ).orEmpty().trim(),
            kind = kind,
            moduleSetId = moduleSetId,
            supportedStages = supportedStages,
            defaultStage = defaultStage,
            recommendedStages = recommendedStages,
            children = children,
            magiskModuleName = values["ABK_MAGISK_MODULE_NAME"].orEmpty().trim(),
            magiskModuleDownloadUrl = values["ABK_MAGISK_MODULE_DOWNLOAD_URL"].orEmpty().trim()
        )
    }

    private fun parseModuleSetChildren(raw: String): List<ModuleSetChildMetadata> =
        raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val parts = line.split('|')
                if (parts.size < 6) return@mapNotNull null
                val id = parts.getOrNull(0).orEmpty().trim()
                val name = parts.getOrNull(1).orEmpty().trim()
                val description = parts.getOrNull(2).orEmpty().trim()
                val repoUrl = parts.getOrNull(3).orEmpty().trim()
                if (id.isBlank() || name.isBlank() || repoUrl.isBlank()) return@mapNotNull null
                val supportedStages = parts.getOrNull(4)
                    .orEmpty()
                    .split(',')
                    .map { CustomExternalModuleStage.normalize(it) }
                    .filter { it in CustomExternalModuleStage.options }
                    .distinct()
                    .ifEmpty { listOf(CustomExternalModuleStage.AFTER_PATCH) }
                val defaultStage = CustomExternalModuleStage.normalize(parts.getOrNull(5).orEmpty())
                    .takeIf { it in supportedStages }
                    ?: supportedStages.first()
                val recommendedStages = parts.getOrNull(6)
                    .orEmpty()
                    .split(',')
                    .mapNotNull { token ->
                        token.trim().takeIf { it.isNotBlank() }?.let(CustomExternalModuleStage::normalize)
                    }
                    .filter { it in supportedStages }
                    .distinct()
                    .ifEmpty { listOf(defaultStage) }
                val groupRole = parts.getOrNull(7).orEmpty().trim()
                val controllable = parts.getOrNull(8).orEmpty().trim().lowercase() in setOf("1", "true", "yes", "on")
                val hasWebUi = parts.getOrNull(9).orEmpty().trim().lowercase() in setOf("1", "true", "yes", "on")
                val magiskModuleName = parts.getOrNull(10).orEmpty().trim()
                val magiskModuleDownloadUrl = parts.getOrNull(11).orEmpty().trim()
                ModuleSetChildMetadata(
                    id = id,
                    name = name,
                    description = description,
                    repoUrl = repoUrl,
                    supportedStages = supportedStages,
                    defaultStage = defaultStage,
                    recommendedStages = recommendedStages,
                    groupRole = groupRole,
                    controllable = controllable,
                    hasWebUi = hasWebUi,
                    magiskModuleName = magiskModuleName,
                    magiskModuleDownloadUrl = magiskModuleDownloadUrl
                )
            }
            .distinctBy { it.id.lowercase() }
            .toList()

    private fun parseShellLikeConf(body: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val lines = body.lines()
        var index = 0
        while (index < lines.size) {
            val clean = lines[index].substringBefore('#').trim()
            if (clean.isBlank() || '=' !in clean) {
                index++
                continue
            }
            val key = clean.substringBefore('=').trim()
            var value = clean.substringAfter('=').trim()
            if (key.isBlank()) {
                index++
                continue
            }
            if ((value == "'" || value == "\"") && index + 1 < lines.size) {
                val quote = value
                val collected = mutableListOf<String>()
                index++
                while (index < lines.size) {
                    val rawLine = lines[index]
                    if (rawLine.trim() == quote) break
                    collected += rawLine
                    index++
                }
                value = collected.joinToString("\n")
            } else {
                value = value.trimShellQuotes()
            }
            result[key] = value
            index++
        }
        return result
    }

    private fun String.trimShellQuotes(): String {
        val clean = trim()
        return if (clean.length >= 2 &&
            ((clean.first() == '"' && clean.last() == '"') || (clean.first() == '\'' && clean.last() == '\''))
        ) {
            clean.substring(1, clean.length - 1)
        } else {
            clean
        }
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? =
        takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.objectOrNull(name: String): JsonObject? =
        get(name)?.asJsonObjectOrNull()

    private fun JsonObject.stringOrEmpty(name: String): String =
        get(name)
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
            ?.asString
            ?.trim()
            .orEmpty()

    private fun JsonObject.stringList(name: String): List<String> =
        get(name)
            ?.takeIf { !it.isJsonNull && it.isJsonArray }
            ?.asJsonArray
            ?.mapNotNull { element ->
                element.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString?.trim()
            }
            ?.filter { it.isNotBlank() }
            .orEmpty()

    private fun JsonObject.csvString(name: String): List<String> =
        stringOrEmpty(name)
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun JsonObject.intOrNull(name: String): Int? =
        get(name)
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
            ?.asInt

    private fun JsonObject.longOrZero(name: String): Long =
        get(name)
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
            ?.asLong
            ?: 0L

    private fun JsonObject.booleanOrFalse(name: String): Boolean =
        get(name)
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
            ?.asBoolean
            ?: false

    private fun JsonObject.arrayOrEmpty(name: String): JsonArray =
        get(name)?.takeIf { !it.isJsonNull && it.isJsonArray }?.asJsonArray ?: JsonArray()

    private fun String.toCatalogFallbackName(): String = trim()
        .trimEnd('/')
        .substringAfterLast('/')
        .removeSuffix(".git")
        .ifBlank { tr(R.string.gh_catalog_fallback_name) }

    private companion object {
        const val DEFAULT_LOG_BUFFER_SIZE = 8 * 1024
        const val MODULE_CATALOG_INDEX_FILE = "abk-modules.json"
    }
}

internal data class GithubRepositoryParts(
    val owner: String,
    val repo: String,
    val branch: String?
)

internal data class ParsedModuleCatalogDocument(
    val name: String,
    val modules: List<ModuleCatalogItem>,
    val skippedCount: Int
)

internal data class ParsedRuntimeModuleCatalogDocument(
    val name: String,
    val modules: List<RuntimeModuleCatalogItem>,
    val skippedCount: Int
)
