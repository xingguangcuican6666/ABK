package com.abk.kernel.data.repository

import com.abk.kernel.BuildConfig
import com.abk.kernel.data.api.GitHubApiService
import com.abk.kernel.data.api.GitHubAuthService
import com.abk.kernel.data.api.NetworkClient
import com.abk.kernel.data.model.*
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

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
    private val publicHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

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

    // ── Module Catalogs ───────────────────────────────────────────────────

    suspend fun fetchExternalModuleMetadata(repositoryUrl: String): Result<ExternalModuleMetadata> =
        withContext(Dispatchers.IO) {
            val candidates = externalModuleConfCandidates(repositoryUrl)
            if (candidates.isEmpty()) {
                return@withContext Result.Error("模块仓库链接格式不支持")
            }

            var lastError = ""
            for (confUrl in candidates) {
                val request = Request.Builder()
                    .url(confUrl)
                    .header("Accept", "text/plain,*/*")
                    .build()
                val response = runCatching { publicHttpClient.newCall(request).execute() }
                    .getOrElse {
                        lastError = it.message ?: "网络请求失败"
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
                            onFailure = { Result.Error("module.conf 无效: ${it.message ?: "格式错误"}") }
                        )
                }
            }

            Result.Error("无法读取 module.conf: $lastError")
        }

    suspend fun fetchModuleCatalog(repositoryUrl: String): Result<ModuleCatalogFetchResult> =
        withContext(Dispatchers.IO) {
            val candidates = moduleCatalogIndexCandidates(repositoryUrl)
            if (candidates.isEmpty()) {
                return@withContext Result.Error("模块仓库链接格式不支持")
            }

            var lastError = ""
            for (indexUrl in candidates) {
                val request = Request.Builder()
                    .url(indexUrl)
                    .header("Accept", "application/json,text/plain,*/*")
                    .build()
                val response = runCatching { publicHttpClient.newCall(request).execute() }
                    .getOrElse {
                        lastError = it.message ?: "网络请求失败"
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
                            lastError = "JSON 解析失败: ${it.message ?: "格式错误"}"
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

            Result.Error("无法读取模块仓库 JSON: $lastError")
        }

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

    suspend fun cancelWorkflowRun(owner: String, repo: String, runId: Long): Result<Unit> {
        val api = apiService ?: return Result.Error("Not authenticated")
        return runCatching {
            val resp = api.cancelWorkflowRun(owner, repo, runId)
            when {
                resp.isSuccessful || resp.code() == 409 -> Result.Success(Unit)
                else -> Result.Error("Cancel run failed: ${resp.code()}", resp.code())
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

    suspend fun downloadJobLogs(owner: String, repo: String, jobId: Long): Result<String> {
        val api = apiService ?: return Result.Error("Not authenticated")
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
        val api = apiService ?: return Result.Error("Not authenticated")
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

    suspend fun listReleases(owner: String, repo: String, perPage: Int = 100): Result<List<GitHubReleaseSummary>> {
        val api = apiService ?: return Result.Error("Not authenticated")
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

    suspend fun listReleaseAssets(
        owner: String,
        repo: String,
        releaseId: Long,
        perPage: Int = 100
    ): Result<List<ReleaseAsset>> {
        val api = apiService ?: return Result.Error("Not authenticated")
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

    private fun moduleCatalogIndexCandidates(repositoryUrl: String): List<String> {
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

    private fun externalModuleConfCandidates(repositoryUrl: String): List<String> {
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

    private fun parseGithubRepository(url: String): GithubRepositoryParts? {
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

    private fun parseModuleCatalogDocument(body: String, repositoryUrl: String): ParsedModuleCatalogDocument {
        val root = JsonParser.parseString(body)
        val document = root.asJsonObjectOrNull() ?: error("根节点必须是 JSON 对象")
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

    private fun sanitizeCatalogItem(raw: JsonObject): ModuleCatalogItem? {
        val repoUrl = raw.stringOrEmpty("repoUrl")
        if (repoUrl.isBlank()) return null
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
            repoUrl = repoUrl,
            defaultStage = defaultStage,
            supportedStages = supportedStages,
            recommendedStages = recommendedStages,
            author = raw.stringOrEmpty("author"),
            homepage = raw.stringOrEmpty("homepage")
        )
    }

    private fun parseExternalModuleConf(body: String): ExternalModuleMetadata {
        val values = parseShellLikeConf(body)
        val name = values["ABK_MODULE_NAME"].orEmpty().trim()
        if (name.isBlank()) error("缺少 ABK_MODULE_NAME")
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
        return ExternalModuleMetadata(
            name = name,
            version = values["ABK_MODULE_VERSION"].orEmpty().trim(),
            description = values["ABK_MODULE_DESCRIPTION"].orEmpty().trim(),
            supportedStages = supportedStages,
            defaultStage = defaultStage,
            recommendedStages = recommendedStages
        )
    }

    private fun parseShellLikeConf(body: String): Map<String, String> =
        body.lineSequence()
            .mapNotNull { line ->
                val clean = line.substringBefore('#').trim()
                if (clean.isBlank() || '=' !in clean) return@mapNotNull null
                val key = clean.substringBefore('=').trim()
                val value = clean.substringAfter('=').trim().trimShellQuotes()
                if (key.isBlank()) null else key to value
            }
            .toMap()

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

    private fun JsonObject.arrayOrEmpty(name: String): JsonArray =
        get(name)?.takeIf { !it.isJsonNull && it.isJsonArray }?.asJsonArray ?: JsonArray()

    private fun String.toCatalogFallbackName(): String = trim()
        .trimEnd('/')
        .substringAfterLast('/')
        .removeSuffix(".git")
        .ifBlank { "模块仓库" }

    private companion object {
        const val DEFAULT_LOG_BUFFER_SIZE = 8 * 1024
        const val MODULE_CATALOG_INDEX_FILE = "abk-modules.json"
    }
}

private data class GithubRepositoryParts(
    val owner: String,
    val repo: String,
    val branch: String?
)

private data class ParsedModuleCatalogDocument(
    val name: String,
    val modules: List<ModuleCatalogItem>,
    val skippedCount: Int
)
