package com.abk.kernel.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.abk.kernel.BuildConfig
import com.abk.kernel.data.model.*
import com.abk.kernel.data.repository.GitHubRepository
import com.abk.kernel.data.repository.PreferencesRepository
import com.abk.kernel.data.repository.Result
import com.abk.kernel.utils.BuildMonitorService
import com.abk.kernel.utils.BuildProgressUtils
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.NotificationUtils
import com.abk.kernel.utils.RootUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

// ── UI State ─────────────────────────────────────────────────────────────────

enum class AuthStep { CHECK_ROOT, LOGIN, FORK_CHECK, READY }

data class WorkflowEnablementPrompt(
    val message: String,
    val actionUrl: String
)

data class MainUiState(
    val authStep: AuthStep = AuthStep.LOGIN,
    val rootGranted: Boolean = false,
    val isLoggedIn: Boolean = false,
    val user: GitHubUser? = null,
    val forkRepo: GitHubRepo? = null,
    val behindBy: Int = 0,
    val showSyncDialog: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    // Device-flow OAuth
    val deviceCode: String? = null,
    val userCode: String? = null,
    val verificationUri: String? = null,
    val isPollingToken: Boolean = false,
    // Build
    val buildStatus: BuildStatus = BuildStatus.IDLE,
    val currentRun: WorkflowRun? = null,
    val recentRuns: List<WorkflowRun> = emptyList(),
    val buildProgress: BuildProgress = BuildProgress(),
    val buildConfig: KernelBuildConfig = KernelBuildConfig(),
    val recommendedBuildConfig: KernelBuildConfig? = null,
    val workflowEnablementPrompt: WorkflowEnablementPrompt? = null,
    // Download
    val downloadedArtifacts: List<DownloadedArtifact> = emptyList(),
    val artifacts: List<BuildArtifact> = emptyList(),
    val isDownloading: Boolean = false,
    val downloadProgress: Map<Long, Int> = emptyMap(),
    val pendingAutoDownloadRunId: Long = -1L,
    val deletingWorkflowRunId: Long? = null,
    // Settings
    val termsLoaded: Boolean = false,
    val termsAccepted: Boolean = false,
    val autoDownload: Boolean = true,
    val notifyBuild: Boolean = true,
    val themeMode: String = "dark",
    val downloadMirrorBaseUrl: String = ""
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesRepository(application)
    val github = GitHubRepository()
    private val gson = Gson()
    private var hasSavedBuildConfig = false
    private var monitoredRunId: Long = -1L
    private val preparedMirrorArtifacts = mutableMapOf<Long, Set<String>>()
    private val artifactDownloadJobs = mutableMapOf<Long, Job>()
    private var hasCheckedWorkflowEnablementThisLaunch = false

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra(BuildMonitorService.EXTRA_STATUS) ?: return
            val runJson = intent.getStringExtra(BuildMonitorService.EXTRA_RUN) ?: return
            try {
                val run = com.google.gson.Gson().fromJson(runJson, WorkflowRun::class.java)
                val progress = intent.getStringExtra(BuildMonitorService.EXTRA_PROGRESS)?.let {
                    runCatching { gson.fromJson(it, BuildProgress::class.java) }.getOrNull()
                } ?: _uiState.value.buildProgress
                val bs = when (status) {
                    "queued", "waiting", "requested" -> BuildStatus.QUEUED
                    "in_progress" -> BuildStatus.IN_PROGRESS
                    "completed" -> if (run.conclusion == "success") BuildStatus.SUCCESS else BuildStatus.FAILURE
                    else -> BuildStatus.IDLE
                }
                _uiState.update { it.copy(buildStatus = bs, currentRun = run, buildProgress = progress) }
                if (bs == BuildStatus.SUCCESS) {
                    loadArtifacts(run.id, autoDownload = true)
                }
            } catch (_: Exception) {}
        }
    }

    init {
        observePreferences()
        registerStatusReceiver()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            combine(
                prefs.accessToken,
                prefs.username,
                prefs.avatarUrl,
                prefs.autoDownload,
                prefs.notifyBuild
            ) { token, name, avatar, autoDl, notify ->
                Quintuple(token, name, avatar, autoDl, notify)
            }.collect { (token, name, avatar, autoDl, notify) ->
                if (!token.isNullOrBlank()) {
                    github.updateToken(token)
                    val shouldResumeSetup = !_uiState.value.isPollingToken &&
                        _uiState.value.authStep in setOf(AuthStep.CHECK_ROOT, AuthStep.LOGIN)
                    _uiState.update {
                        it.copy(
                            isLoggedIn = true,
                            autoDownload = autoDl,
                            notifyBuild = notify
                        )
                    }
                    if (!name.isNullOrBlank()) {
                        _uiState.update { state ->
                            state.copy(
                                user = state.user?.copy(login = name, avatarUrl = avatar ?: "")
                                    ?: GitHubUser(name, 0, name, avatar ?: "", "")
                            )
                        }
                    }
                    if (shouldResumeSetup) {
                        if (name.isNullOrBlank()) {
                            fetchUserAndContinue()
                        } else {
                            advanceStep()
                        }
                    }
                } else {
                    hasCheckedWorkflowEnablementThisLaunch = false
                    _uiState.update {
                        it.copy(
                            isLoggedIn = false,
                            user = null,
                            autoDownload = autoDl,
                            notifyBuild = notify
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            prefs.termsAcceptedVersion.collect { version ->
                _uiState.update {
                    it.copy(
                        termsLoaded = true,
                        termsAccepted = version >= PreferencesRepository.CURRENT_TERMS_VERSION
                    )
                }
            }
        }
        viewModelScope.launch {
            prefs.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            prefs.downloadMirrorBaseUrl.collect { url ->
                _uiState.update { it.copy(downloadMirrorBaseUrl = url) }
            }
        }
        viewModelScope.launch {
            prefs.buildConfigJson.collect { json ->
                if (!json.isNullOrBlank()) {
                    runCatching { gson.fromJson(json, KernelBuildConfig::class.java) }
                        .getOrNull()
                        ?.let { config ->
                            hasSavedBuildConfig = true
                            _uiState.update { it.copy(buildConfig = config) }
                        }
                }
            }
        }
        viewModelScope.launch {
            prefs.downloadedArtifactsJson.collect { json ->
                val restored = parseDownloadedArtifacts(json)
                    .distinctBy { it.filePath }
                    .filter { File(it.filePath).exists() }
                _uiState.update { it.copy(downloadedArtifacts = restored) }
            }
        }
        viewModelScope.launch {
            prefs.remoteArtifactsJson.collect { json ->
                if (json.isNullOrBlank()) {
                    _uiState.update { it.copy(artifacts = emptyList()) }
                } else {
                    val restored = parseBuildArtifacts(json)
                        .distinctBy { it.id }
                        .sortedForDisplay()
                    _uiState.update { it.copy(artifacts = mergeRemoteArtifacts(it.artifacts, restored)) }
                }
            }
        }
        viewModelScope.launch {
            prefs.pendingAutoDownloadRunId.collect { runId ->
                _uiState.update { it.copy(pendingAutoDownloadRunId = runId) }
            }
        }
    }

    private fun registerStatusReceiver() {
        val filter = IntentFilter(BuildMonitorService.BROADCAST_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplication<Application>().registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            getApplication<Application>().registerReceiver(statusReceiver, filter)
        }
    }

    // ── Root ──────────────────────────────────────────────────────────────

    fun checkRoot() {
        viewModelScope.launch {
            val shouldAdvance = _uiState.value.authStep != AuthStep.READY
            val granted = RootUtils.isRootAvailable()
            val recommended = detectRecommendedBuildConfig()
            val initialConfig = applyInitialBuildConfigIfNeeded(recommended)
            _uiState.update {
                it.copy(
                    rootGranted = granted,
                    recommendedBuildConfig = recommended,
                    buildConfig = initialConfig ?: it.buildConfig
                )
            }
            if (shouldAdvance) advanceStep()
        }
    }

    fun requestRoot() {
        viewModelScope.launch {
            val shouldAdvance = _uiState.value.authStep != AuthStep.READY
            _uiState.update { it.copy(isLoading = true) }
            val granted = RootUtils.requestRoot()
            val recommended = detectRecommendedBuildConfig()
            val initialConfig = applyInitialBuildConfigIfNeeded(recommended)
            _uiState.update {
                it.copy(
                    rootGranted = granted,
                    isLoading = false,
                    recommendedBuildConfig = recommended,
                    buildConfig = initialConfig ?: it.buildConfig
                )
            }
            if (shouldAdvance) advanceStep()
        }
    }

    private suspend fun applyInitialBuildConfigIfNeeded(recommended: KernelBuildConfig?): KernelBuildConfig? {
        if (recommended == null || hasSavedBuildConfig) return null
        val savedJson = prefs.buildConfigJson.first()
        if (!savedJson.isNullOrBlank()) {
            hasSavedBuildConfig = true
            return null
        }
        val normalized = KernelSupport.normalize(recommended)
        hasSavedBuildConfig = true
        prefs.saveBuildConfigJson(gson.toJson(normalized))
        return normalized
    }

    private fun advanceStep() {
        val state = _uiState.value
        when {
            !state.isLoggedIn -> _uiState.update { it.copy(authStep = AuthStep.LOGIN) }
            state.user == null -> {
                _uiState.update { it.copy(authStep = AuthStep.LOGIN) }
                viewModelScope.launch { fetchUserAndContinue() }
            }
            else -> {
                _uiState.update { it.copy(authStep = AuthStep.FORK_CHECK) }
                checkFork()
            }
        }
    }

    // ── GitHub Auth (Device Flow) ─────────────────────────────────────────

    fun startDeviceFlow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val r = github.requestDeviceCode()) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            deviceCode = r.data.deviceCode,
                            userCode = r.data.userCode,
                            verificationUri = r.data.verificationUri,
                            isPollingToken = true
                        )
                    }
                    pollToken(r.data.deviceCode, r.data.interval.toLong())
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = r.message) }
                else -> {}
            }
        }
    }

    private fun pollToken(deviceCode: String, intervalSeconds: Long) {
        viewModelScope.launch {
            while (_uiState.value.isPollingToken) {
                delay(intervalSeconds * 1000)
                when (val r = github.pollToken(deviceCode)) {
                    is Result.Success -> {
                        val tokenResp = r.data
                        when (tokenResp.error) {
                            null -> {
                                val token = tokenResp.accessToken ?: continue
                                prefs.saveToken(token)
                                github.updateToken(token)
                                _uiState.update { it.copy(isPollingToken = false) }
                                fetchUserAndContinue()
                            }
                            "authorization_pending", "slow_down" -> {
                                if (tokenResp.error == "slow_down") delay(5000)
                            }
                            "expired_token", "access_denied" -> {
                                _uiState.update {
                                    it.copy(
                                        isPollingToken = false,
                                        error = "授权失败: ${tokenResp.error}"
                                    )
                                }
                            }
                            else -> _uiState.update { it.copy(isPollingToken = false, error = tokenResp.error) }
                        }
                    }
                    is Result.Error -> delay(intervalSeconds * 1000)
                    else -> {}
                }
            }
        }
    }

    private suspend fun fetchUserAndContinue() {
        when (val r = github.getAuthenticatedUser()) {
            is Result.Success -> {
                val user = r.data
                prefs.saveUsername(user.login)
                prefs.saveAvatarUrl(user.avatarUrl)
                _uiState.update { it.copy(user = user, isLoggedIn = true) }
                advanceStep()
            }
            is Result.Error -> _uiState.update { it.copy(error = r.message) }
            else -> {}
        }
    }

    fun logout() {
        viewModelScope.launch {
            prefs.clearAuth()
            hasCheckedWorkflowEnablementThisLaunch = false
            _uiState.update {
                MainUiState(
                    rootGranted = it.rootGranted,
                    authStep = AuthStep.LOGIN,
                    termsLoaded = it.termsLoaded,
                    termsAccepted = it.termsAccepted,
                    autoDownload = it.autoDownload,
                    notifyBuild = it.notifyBuild,
                    themeMode = it.themeMode,
                    downloadMirrorBaseUrl = it.downloadMirrorBaseUrl
                )
            }
        }
    }

    // ── Fork Management ───────────────────────────────────────────────────

    fun checkFork() {
        val username = _uiState.value.user?.login ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val forkResult = github.getUserFork(
                BuildConfig.SOURCE_REPO_OWNER, BuildConfig.SOURCE_REPO_NAME, username
            )
            when (forkResult) {
                is Result.Success -> {
                    if (forkResult.data == null) {
                        _uiState.update { it.copy(isLoading = false, forkRepo = null) }
                    } else {
                        val fork = forkResult.data
                        val upstreamBranch = fork.parent?.defaultBranch ?: fork.defaultBranch
                        prefs.saveForkRepoName(fork.name)
                        val compareResult = github.checkBehind(
                            BuildConfig.SOURCE_REPO_OWNER,
                            BuildConfig.SOURCE_REPO_NAME,
                            upstreamBranch,
                            username,
                            fork.defaultBranch
                        )
                        val behind = if (compareResult is Result.Success) compareResult.data.behindBy else 0
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                forkRepo = fork,
                                behindBy = behind,
                                showSyncDialog = behind > 0
                            )
                        }
                        ensureBuildWorkflowEnabled()
                        if (behind == 0) finishSetup()
                    }
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = forkResult.message) }
                else -> {}
            }
        }
    }

    fun forkRepo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val r = github.forkRepo(BuildConfig.SOURCE_REPO_OWNER, BuildConfig.SOURCE_REPO_NAME)) {
                is Result.Success -> {
                    prefs.saveForkRepoName(r.data.name)
                    _uiState.update { it.copy(isLoading = false, forkRepo = r.data) }
                    finishSetup()
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = r.message) }
                else -> {}
            }
        }
    }

    fun syncFork() {
        val state = _uiState.value
        val username = state.user?.login ?: return
        val fork = state.forkRepo ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showSyncDialog = false) }
            when (val r = github.syncFork(username, fork.name, fork.defaultBranch)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoading = false, behindBy = 0) }
                    finishSetup()
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = r.message) }
                else -> {}
            }
        }
    }

    fun dismissSyncDialog() {
        _uiState.update { it.copy(showSyncDialog = false) }
        finishSetup()
    }

    private fun finishSetup() {
        _uiState.update { it.copy(authStep = AuthStep.READY) }
        loadRecentRuns()
        ensureBuildWorkflowEnabled()
    }

    private fun ensureBuildWorkflowEnabled() {
        if (hasCheckedWorkflowEnablementThisLaunch) return
        val state = _uiState.value
        val owner = state.user?.login ?: return
        val repo = state.forkRepo ?: return
        hasCheckedWorkflowEnablementThisLaunch = true
        viewModelScope.launch {
            ensureBuildWorkflowEnabled(owner, repo.name, reportError = false)
        }
    }

    private suspend fun ensureBuildWorkflowEnabled(
        owner: String,
        repoName: String,
        reportError: Boolean
    ): Long? {
        val actionUrl = workflowActionsUrl(owner, repoName)
        return when (val workflow = github.getWorkflow(owner, repoName, KERNEL_WORKFLOW_FILE)) {
            is Result.Success -> {
                if (workflow.data.state != "active") {
                    when (val enabled = github.enableWorkflow(owner, repoName, workflow.data.id)) {
                        is Result.Success -> {
                            delay(1000)
                            when (val refreshed = github.getWorkflow(owner, repoName, KERNEL_WORKFLOW_FILE)) {
                                is Result.Success -> {
                                    if (refreshed.data.state == "active") {
                                        return refreshed.data.id
                                    }
                                    if (reportError) {
                                        showWorkflowEnablementPrompt(
                                            "工作流仍未启用，当前状态: ${refreshed.data.state}",
                                            actionUrl
                                        )
                                    }
                                    return null
                                }
                                is Result.Error -> {
                                    if (reportError) {
                                        showWorkflowEnablementPrompt(refreshed.message, actionUrl)
                                    }
                                    return null
                                }
                                Result.Loading -> return null
                            }
                        }
                        is Result.Error -> {
                            if (reportError) {
                                showWorkflowEnablementPrompt(enabled.message, actionUrl)
                            }
                            return null
                        }
                        Result.Loading -> {}
                    }
                }
                workflow.data.id
            }
            is Result.Error -> {
                if (reportError) {
                    showWorkflowEnablementPrompt(workflow.message, actionUrl)
                }
                null
            }
            Result.Loading -> null
        }
    }

    private fun showWorkflowEnablementPrompt(reason: String, actionUrl: String) {
        _uiState.update {
            it.copy(
                isLoading = false,
                error = null,
                workflowEnablementPrompt = WorkflowEnablementPrompt(
                    message = reason,
                    actionUrl = actionUrl
                )
            )
        }
    }

    fun dismissWorkflowEnablementPrompt() {
        _uiState.update { it.copy(workflowEnablementPrompt = null) }
    }

    // ── Build ─────────────────────────────────────────────────────────────

    fun dispatchBuild(config: KernelBuildConfig) {
        val state = _uiState.value
        val buildConfig = KernelSupport.normalize(config)
        val username = state.user?.login ?: return
        val repoName = state.forkRepo?.name ?: BuildConfig.SOURCE_REPO_NAME
        val ref = state.forkRepo?.defaultBranch ?: "main"
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val wfId = ensureBuildWorkflowEnabled(username, repoName, reportError = true) ?: return@launch
            val previousRunId = when (val prior = github.listRecentRuns(username, repoName, 1, wfId)) {
                is Result.Success -> prior.data.firstOrNull()?.id
                else -> null
            }
            val inputs = buildConfig.toInputMap()
            when (val r = github.dispatchWorkflow(username, repoName, wfId, inputs, ref)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            buildStatus = BuildStatus.QUEUED,
                            buildProgress = BuildProgress(percent = 0, currentStep = "构建已排队")
                        )
                    }
                    delay(5000) // wait for GH to create the run
                    findAndMonitorLatestRun(username, repoName, wfId, previousRunId)
                }
                is Result.Error -> {
                    if (r.code == 403 || r.code == 404) {
                        showWorkflowEnablementPrompt("触发工作流失败: ${r.message}", workflowActionsUrl(username, repoName))
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = r.message) }
                    }
                }
                else -> {}
            }
        }
    }

    private suspend fun findAndMonitorLatestRun(
        owner: String,
        repo: String,
        workflowId: Long,
        previousRunId: Long?
    ) {
        repeat(6) { attempt ->
            when (val r = github.listRecentRuns(owner, repo, 5, workflowId)) {
                is Result.Success -> {
                    val run = r.data.firstOrNull {
                        previousRunId == null || it.id > previousRunId
                    }
                    if (run != null) {
                        prefs.saveLastRunId(run.id)
                        if (_uiState.value.autoDownload) {
                            prefs.savePendingAutoDownloadRunId(run.id)
                        } else {
                            prefs.clearPendingAutoDownloadRunId()
                        }
                        _uiState.update { it.copy(currentRun = run, buildStatus = BuildStatus.QUEUED) }
                        monitoredRunId = run.id
                        BuildMonitorService.startMonitoring(getApplication(), owner, repo, run.id)
                        return
                    }
                }
                else -> {}
            }
            if (attempt < 5) delay(5_000)
        }
        _uiState.update {
            it.copy(error = "已提交构建，但暂未找到工作流运行，请稍后刷新最近构建。")
        }
    }

    fun loadRecentRuns() {
        val state = _uiState.value
        val username = state.user?.login ?: return
        val repoName = state.forkRepo?.name ?: return
        viewModelScope.launch {
            when (val r = github.listRecentRuns(username, repoName, perPage = 30)) {
                is Result.Success -> {
                    _uiState.update { it.copy(recentRuns = r.data) }
                    autoMonitorRunningCustomBuild(username, repoName, r.data)
                    refreshArtifactsForRuns(username, repoName, r.data)
                }
                else -> {}
            }
        }
    }

    private suspend fun autoMonitorRunningCustomBuild(
        owner: String,
        repoName: String,
        recentRuns: List<WorkflowRun>
    ) {
        val workflowId = when (val wf = github.getWorkflowId(owner, repoName, KERNEL_WORKFLOW_FILE)) {
            is Result.Success -> wf.data
            else -> return
        }
        val workflowRuns = recentRuns.filter { it.workflowId == workflowId }.ifEmpty {
            when (val customRuns = github.listRecentRuns(owner, repoName, perPage = 10, workflowId = workflowId)) {
                is Result.Success -> customRuns.data
                else -> emptyList()
            }
        }
        val running = workflowRuns.firstOrNull { it.isActiveBuildRun() } ?: return
        if (monitoredRunId == running.id && _uiState.value.currentRun?.id == running.id) return

        monitoredRunId = running.id
        prefs.saveLastRunId(running.id)
        if (_uiState.value.autoDownload) {
            prefs.savePendingAutoDownloadRunId(running.id)
        }
        _uiState.update {
            it.copy(
                currentRun = running,
                buildStatus = running.toBuildStatus(),
                buildProgress = BuildProgress(
                    percent = if (running.status == "in_progress") 5 else 0,
                    currentStep = if (running.status == "in_progress") {
                        "已接管运行中的工作流"
                    } else {
                        "发现运行中的工作流，等待 Runner"
                    }
                )
            )
        }
        BuildMonitorService.startMonitoring(getApplication(), owner, repoName, running.id)
    }

    fun loadArtifacts(runId: Long, autoDownload: Boolean = false) {
        val state = _uiState.value
        val username = state.user?.login ?: return
        val repoName = state.forkRepo?.name ?: return
        viewModelScope.launch {
            when (val r = listArtifactsWithRetry(username, repoName, runId, retryWhenEmpty = autoDownload)) {
                is Result.Success -> {
                    val run = state.recentRuns.find { it.id == runId }
                        ?: state.currentRun?.takeIf { it.id == runId }
                        ?: when (val runResult = github.getWorkflowRun(username, repoName, runId)) {
                            is Result.Success -> runResult.data
                            else -> null
                        }
                    val buildArtifacts = r.data.map { artifact ->
                        if (run != null) artifact.withRun(run) else artifact.toBuildArtifact(runId)
                    }
                    val merged = mergeRemoteArtifacts(_uiState.value.artifacts, buildArtifacts)
                    _uiState.update { it.copy(artifacts = merged) }
                    prefs.saveRemoteArtifactsJson(gson.toJson(merged))
                    maybeAutoDownloadRun(runId, buildArtifacts, autoDownload)
                }
                else -> {}
            }
        }
    }

    private suspend fun listArtifactsWithRetry(
        owner: String,
        repoName: String,
        runId: Long,
        retryWhenEmpty: Boolean
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

    fun downloadArtifact(
        artifact: BuildArtifact
    ) {
        startArtifactDownload(artifact)
    }

    fun deleteDownloadedArtifact(filePath: String) {
        viewModelScope.launch {
            val current = _uiState.value.downloadedArtifacts
            val target = current.firstOrNull { it.filePath == filePath } ?: return@launch
            deleteDownloadedFile(target)
            val updated = current
                .filterNot { it.filePath == filePath }
                .sortedDownloadedForDisplay()
            _uiState.update { it.copy(downloadedArtifacts = updated) }
            prefs.saveDownloadedArtifactsJson(gson.toJson(updated))
        }
    }

    fun deleteWorkflowArtifacts(runId: Long, deleteRemoteRun: Boolean) {
        val shouldDeleteRemoteRun = deleteRemoteRun
        viewModelScope.launch {
            _uiState.update { it.copy(deletingWorkflowRunId = runId, error = null) }
            try {
                if (shouldDeleteRemoteRun) {
                    val owner = _uiState.value.user?.login
                    val repoName = _uiState.value.forkRepo?.name
                    if (owner.isNullOrBlank() || repoName.isNullOrBlank()) {
                        _uiState.update { it.copy(error = "无法删除远程工作流记录: 仓库信息不完整") }
                        return@launch
                    }
                    when (val result = github.deleteWorkflowRun(owner, repoName, runId)) {
                        is Result.Error -> {
                            _uiState.update { it.copy(error = "删除远程工作流记录失败: ${result.message}") }
                            return@launch
                        }
                        else -> {}
                    }
                }

                val currentDownloads = _uiState.value.downloadedArtifacts
                currentDownloads
                    .filter { it.runId == runId }
                    .forEach(::deleteDownloadedFile)

                val updatedDownloads = currentDownloads
                    .filterNot { it.runId == runId }
                    .sortedDownloadedForDisplay()
                val removedRemoteIds = _uiState.value.artifacts
                    .filter { it.runId == runId }
                    .map { it.id }
                    .toSet()
                removedRemoteIds.forEach { artifactId ->
                    artifactDownloadJobs[artifactId]?.cancel()
                    artifactDownloadJobs.remove(artifactId)
                }
                val updatedRemote = _uiState.value.artifacts
                    .filterNot { it.runId == runId }
                    .sortedForDisplay()

                _uiState.update { state ->
                    state.copy(
                        downloadedArtifacts = updatedDownloads,
                        artifacts = updatedRemote,
                        downloadProgress = state.downloadProgress.filterKeys { it !in removedRemoteIds },
                        recentRuns = state.recentRuns.filterNot { it.id == runId },
                        currentRun = state.currentRun?.takeUnless { it.id == runId },
                        buildStatus = if (state.currentRun?.id == runId) BuildStatus.IDLE else state.buildStatus
                    )
                }
                if (_uiState.value.pendingAutoDownloadRunId == runId) {
                    prefs.clearPendingAutoDownloadRunId()
                }
                prefs.saveDownloadedArtifactsJson(gson.toJson(updatedDownloads))
                prefs.saveRemoteArtifactsJson(gson.toJson(updatedRemote))
            } finally {
                _uiState.update {
                    if (it.deletingWorkflowRunId == runId) it.copy(deletingWorkflowRunId = null) else it
                }
            }
        }
    }

    private fun startArtifactDownload(artifact: BuildArtifact) {
        artifactDownloadJobs[artifact.id]?.cancel()
        artifactDownloadJobs[artifact.id] = viewModelScope.launch {
            try {
                downloadArtifactNow(artifact)
            } finally {
                artifactDownloadJobs.remove(artifact.id)
            }
        }
    }

    private suspend fun downloadArtifactNow(artifact: BuildArtifact) {
        val token = prefs.accessToken.first()
        if (token.isNullOrBlank()) {
            _uiState.update { it.copy(isDownloading = false, error = "未登录，无法下载构建产物") }
            return
        }
        _uiState.update {
            it.copy(
                isDownloading = true,
                error = null,
                downloadProgress = it.downloadProgress + (artifact.id to 0)
            )
        }
        NotificationUtils.notifyDownloadProgress(getApplication(), 0, artifact.name)
        val mirrorBaseUrl = prefs.downloadMirrorBaseUrl.first()
        val mirrorEnabled = mirrorBaseUrl.isNotBlank()
        val downloadUrl = if (mirrorEnabled) {
            monitorMirrorAndResolveDownloadUrl(artifact, mirrorBaseUrl) ?: run {
                finishArtifactDownloadWithError(artifact.id, "镜像下载准备失败: ${artifact.name}")
                return
            }
        } else {
            null
        }
        val results = DownloadUtils.downloadArtifact(
            getApplication(),
            if (downloadUrl == null) token else null,
            artifact.toArtifact(),
            artifact.toWorkflowRun(),
            downloadUrl
        ) { pct ->
            val displayProgress = if (mirrorEnabled) {
                (50 + pct / 2).coerceIn(50, 100)
            } else {
                pct
            }
            NotificationUtils.notifyDownloadProgress(getApplication(), displayProgress, artifact.name)
            _uiState.update { s ->
                s.copy(downloadProgress = s.downloadProgress + (artifact.id to displayProgress))
            }
        }
        if (results.isNotEmpty()) {
            NotificationUtils.notifyDownloadDone(getApplication(), artifact.name)
            val updated = (_uiState.value.downloadedArtifacts + results)
                .distinctBy { it.filePath }
                .sortedDownloadedForDisplay()
            _uiState.update { s ->
                s.copy(
                    isDownloading = false,
                    error = null,
                    downloadedArtifacts = updated,
                    downloadProgress = s.downloadProgress - artifact.id
                )
            }
            prefs.saveDownloadedArtifactsJson(gson.toJson(updated))
        } else {
            finishArtifactDownloadWithError(artifact.id, "下载失败: ${artifact.name}")
        }
    }

    private fun finishArtifactDownloadWithError(artifactId: Long, message: String) {
        _uiState.update {
            it.copy(
                isDownloading = false,
                error = it.error ?: message,
                downloadProgress = it.downloadProgress - artifactId
            )
        }
    }

    private fun deleteDownloadedFile(artifact: DownloadedArtifact) {
        runCatching {
            val file = File(artifact.filePath)
            if (file.exists()) file.delete()
            val parent = file.parentFile
            if (parent?.listFiles()?.isEmpty() == true) {
                parent.delete()
            }
        }
    }

    private suspend fun monitorMirrorAndResolveDownloadUrl(
        artifact: BuildArtifact,
        mirrorBaseUrl: String
    ): String? {
        val state = _uiState.value
        val username = state.user?.login ?: return null
        val repoName = state.forkRepo?.name ?: return null
        val ref = state.forkRepo?.defaultBranch ?: "main"
        val cached = preparedMirrorArtifacts[artifact.runId]?.contains(artifact.name) == true
        if (cached) {
            markMirrorProgress(artifact.id, 50)
            return normalizeMirrorBaseUrl(mirrorBaseUrl) + releaseAssetUrl(username, repoName, artifact.runId, artifact.name)
        }
        val existingAssetUrl = findMirrorReleaseAssetUrl(username, repoName, artifact.runId, artifact.name)
        if (existingAssetUrl != null) {
            markMirrorProgress(artifact.id, 50)
            preparedMirrorArtifacts[artifact.runId] = (preparedMirrorArtifacts[artifact.runId].orEmpty() + artifact.name).toSet()
            return normalizeMirrorBaseUrl(mirrorBaseUrl) + existingAssetUrl
        }
        val targetNames = mirrorTargetArtifactNames(artifact)
        if (targetNames.isEmpty()) {
            _uiState.update { it.copy(error = "没有可镜像的构建产物: ${artifact.name}") }
            return null
        }

        markMirrorProgress(artifact.id, 1)
        val workflowId = when (val wf = github.getWorkflowId(username, repoName, MIRROR_WORKFLOW_FILE)) {
            is Result.Success -> wf.data
            is Result.Error -> {
                _uiState.update { it.copy(error = "镜像工作流不存在，请同步 Fork: ${wf.message}") }
                return null
            }
            else -> return null
        }
        github.enableWorkflow(username, repoName, workflowId)
        val previousRunId = when (val prior = github.listRecentRuns(username, repoName, 1, workflowId)) {
            is Result.Success -> prior.data.firstOrNull()?.id
            else -> null
        }
        val inputs = mapOf(
            "source_run_id" to artifact.runId.toString(),
            "artifact_names" to targetNames.joinToString("\n")
        )
        when (val dispatch = github.dispatchWorkflow(username, repoName, workflowId, inputs, ref)) {
            is Result.Error -> {
                _uiState.update { it.copy(error = "触发镜像工作流失败: ${dispatch.message}") }
                return null
            }
            else -> {}
        }
        delay(5_000)
        val run = findMirrorWorkflowRun(username, repoName, workflowId, previousRunId) ?: run {
            _uiState.update { it.copy(error = "已触发镜像工作流，但暂未找到运行记录") }
            return null
        }
        val completed = waitForMirrorWorkflow(username, repoName, run.id, artifact.id) ?: return null
        if (completed.conclusion != "success") {
            _uiState.update { it.copy(error = "镜像工作流失败: ${completed.conclusion ?: "unknown"}") }
            return null
        }
        markMirrorProgress(artifact.id, 50)
        val releaseAssetUrl = findMirrorReleaseAssetUrlWithRetry(username, repoName, artifact.runId, artifact.name) ?: run {
            _uiState.update { it.copy(error = "镜像 Release 已创建，但未找到产物: ${artifact.name}.zip") }
            return null
        }
        preparedMirrorArtifacts[artifact.runId] = (preparedMirrorArtifacts[artifact.runId].orEmpty() + targetNames).toSet()
        return normalizeMirrorBaseUrl(mirrorBaseUrl) + releaseAssetUrl
    }

    private fun mirrorTargetArtifactNames(artifact: BuildArtifact): List<String> {
        val sameRunTargets = _uiState.value.artifacts
            .filter { it.runId == artifact.runId && !it.expired && DownloadUtils.shouldAutoDownload(it) }
            .map { it.name }
        return (sameRunTargets + artifact.name).distinct()
    }

    private suspend fun findMirrorWorkflowRun(
        owner: String,
        repoName: String,
        workflowId: Long,
        previousRunId: Long?
    ): WorkflowRun? {
        repeat(6) { attempt ->
            when (val runs = github.listRecentRuns(owner, repoName, 5, workflowId)) {
                is Result.Success -> {
                    val run = runs.data.firstOrNull { previousRunId == null || it.id > previousRunId }
                    if (run != null) return run
                }
                else -> {}
            }
            if (attempt < 5) delay(5_000)
        }
        return null
    }

    private suspend fun waitForMirrorWorkflow(
        owner: String,
        repoName: String,
        runId: Long,
        artifactId: Long
    ): WorkflowRun? {
        repeat(MIRROR_WORKFLOW_MAX_POLLS) { attempt ->
            when (val run = github.getWorkflowRun(owner, repoName, runId)) {
                is Result.Success -> {
                    val data = run.data
                    if (data.status == "completed") {
                        markMirrorProgress(artifactId, 50)
                        return data
                    }
                    val progress = when (val jobs = github.listRunJobs(owner, repoName, runId)) {
                        is Result.Success -> {
                            val stepProgress = BuildProgressUtils.from(data, jobs.data).percent
                            (stepProgress / 2).coerceIn(
                                if (data.status in setOf("queued", "waiting", "requested", "pending")) 0 else 1,
                                49
                            )
                        }
                        else -> if (data.status in setOf("queued", "waiting", "requested", "pending")) 0 else 1
                    }
                    markMirrorProgress(artifactId, progress)
                }
                is Result.Error -> {
                    _uiState.update { it.copy(error = "查询镜像工作流失败: ${run.message}") }
                    return null
                }
                else -> {}
            }
            if (attempt < MIRROR_WORKFLOW_MAX_POLLS - 1) delay(15_000)
        }
        _uiState.update { it.copy(error = "镜像工作流等待超时") }
        return null
    }

    private fun markMirrorProgress(artifactId: Long, progress: Int) {
        _uiState.update { s ->
            s.copy(downloadProgress = s.downloadProgress + (artifactId to progress.coerceIn(0, 100)))
        }
    }

    private suspend fun findMirrorReleaseAssetUrl(
        owner: String,
        repoName: String,
        runId: Long,
        artifactName: String
    ): String? {
        val tag = mirrorReleaseTag(runId)
        return when (val release = github.getReleaseByTag(owner, repoName, tag)) {
            is Result.Success -> release.data?.assets
                ?.firstOrNull { it.name == "$artifactName.zip" }
                ?.browserDownloadUrl
            is Result.Error -> {
                _uiState.update { it.copy(error = "查询镜像 Release 失败: ${release.message}") }
                null
            }
            else -> null
        }
    }

    private suspend fun findMirrorReleaseAssetUrlWithRetry(
        owner: String,
        repoName: String,
        runId: Long,
        artifactName: String
    ): String? {
        repeat(MIRROR_RELEASE_ASSET_MAX_POLLS) { attempt ->
            val url = findMirrorReleaseAssetUrl(owner, repoName, runId, artifactName)
            if (url != null) return url
            if (attempt < MIRROR_RELEASE_ASSET_MAX_POLLS - 1) delay(5_000)
        }
        return null
    }

    private suspend fun refreshArtifactsForRuns(
        owner: String,
        repoName: String,
        runs: List<WorkflowRun>
    ) {
        val completedRuns = runs
            .filter { it.status == "completed" }
            .take(MAX_REMOTE_ARTIFACT_RUNS)
        if (completedRuns.isEmpty()) return

        val collected = completedRuns.flatMap { run ->
            when (val artifacts = github.listArtifacts(owner, repoName, run.id)) {
                is Result.Success -> artifacts.data.map { it.withRun(run) }
                else -> emptyList()
            }
        }
        val merged = mergeRemoteArtifacts(_uiState.value.artifacts, collected)
        _uiState.update { it.copy(artifacts = merged) }
        prefs.saveRemoteArtifactsJson(gson.toJson(merged))

        val pendingRunId = prefs.pendingAutoDownloadRunId.first()
        if (pendingRunId > 0L && completedRuns.any { it.id == pendingRunId }) {
            maybeAutoDownloadRun(
                pendingRunId,
                merged.filter { it.runId == pendingRunId },
                requestedByMonitor = true
            )
        }
    }

    private suspend fun maybeAutoDownloadRun(
        runId: Long,
        artifacts: List<BuildArtifact>,
        requestedByMonitor: Boolean
    ) {
        if (!requestedByMonitor || !_uiState.value.autoDownload) return
        if (prefs.pendingAutoDownloadRunId.first() != runId) return
        if (artifacts.isEmpty()) return

        val targets = artifacts
            .filter { !it.expired && DownloadUtils.shouldAutoDownload(it) }
            .filterNot { candidate ->
                _uiState.value.downloadedArtifacts.any { DownloadUtils.matchesDownloadedArtifact(it, candidate) }
            }

        if (targets.isEmpty()) {
            prefs.clearPendingAutoDownloadRunId()
            return
        }
        prefs.clearPendingAutoDownloadRunId()
        targets.forEach { startArtifactDownload(it) }
    }

    // ── Settings ──────────────────────────────────────────────────────────

    fun setAutoDownload(v: Boolean) = viewModelScope.launch {
        prefs.setAutoDownload(v)
        if (!v) prefs.clearPendingAutoDownloadRunId()
    }
    fun setNotifyBuild(v: Boolean) = viewModelScope.launch { prefs.setNotifyBuild(v) }
    fun setThemeMode(mode: String) = viewModelScope.launch { prefs.setThemeMode(mode) }
    fun acceptTerms() = viewModelScope.launch { prefs.acceptCurrentTerms() }
    fun setDownloadMirrorBaseUrl(url: String) = viewModelScope.launch {
        prefs.setDownloadMirrorBaseUrl(url.trim())
    }
    fun updateBuildConfig(config: KernelBuildConfig) {
        val normalized = KernelSupport.normalize(config)
        hasSavedBuildConfig = true
        _uiState.update { it.copy(buildConfig = normalized) }
        viewModelScope.launch { prefs.saveBuildConfigJson(gson.toJson(normalized)) }
    }

    private fun parseDownloadedArtifacts(json: String?): List<DownloadedArtifact> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching<List<DownloadedArtifact>> {
            val type = object : TypeToken<List<DownloadedArtifact>>() {}.type
            gson.fromJson<List<DownloadedArtifact>>(json, type).orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun parseBuildArtifacts(json: String?): List<BuildArtifact> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching<List<BuildArtifact>> {
            val type = object : TypeToken<List<BuildArtifact>>() {}.type
            gson.fromJson<List<BuildArtifact>>(json, type).orEmpty()
        }.getOrDefault(emptyList())
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(statusReceiver) }
        super.onCleared()
    }
}

private fun detectRecommendedBuildConfig(): KernelBuildConfig? {
    val kernelVersion = RootUtils.getKernelVersion()
    if (kernelVersion.isBlank() || kernelVersion.equals("Unknown", ignoreCase = true)) return null
    return KernelSupport.recommendedFromKernel(kernelVersion)
}

private fun WorkflowRun.isActiveBuildRun(): Boolean =
    status in setOf("queued", "waiting", "requested", "pending", "in_progress")

private fun WorkflowRun.toBuildStatus(): BuildStatus = when (status) {
    "queued", "waiting", "requested", "pending" -> BuildStatus.QUEUED
    "in_progress" -> BuildStatus.IN_PROGRESS
    "completed" -> if (conclusion == "success") BuildStatus.SUCCESS else BuildStatus.FAILURE
    else -> BuildStatus.IDLE
}

// Helper to convert KernelBuildConfig to workflow dispatch inputs map
private fun KernelBuildConfig.toInputMap(): Map<String, String> = mapOf(
    "android_version" to androidVersion,
    "kernel_version" to kernelVersion,
    "sub_level" to subLevel,
    "os_patch_level" to osPatchLevel,
    "revision" to revision,
    "kernelsu_variant" to kernelsuVariant,
    "kernelsu_branch" to kernelsuBranch.takeIf { it in setOf("Stable(标准)", "Dev(开发)") }.orEmpty()
        .ifBlank { "Stable(标准)" },
    "version" to version,
    "build_time" to buildTime,
    "use_zram" to useZram.toString(),
    "use_bbg" to useBbg.toString(),
    "use_ddk" to useDdk.toString(),
    "use_kpm" to useKpm.toString(),
    "use_rekernel" to useRekernel.toString(),
    "cancel_susfs" to cancelSusfs.toString(),
    "supp_op" to suppOp.toString(),
    "zram_full_algo" to zramFullAlgo.toString(),
    "zram_extra_algos" to zramExtraAlgos,
    "kpm_password" to kpmPassword,
    "droidspaces" to droidspaces,
    "use_custom_external_modules" to useCustomExternalModules.toString(),
    "custom_external_modules" to if (useCustomExternalModules) customExternalModules.toWorkflowInput() else ""
)

private fun List<CustomExternalModule>?.toWorkflowInput(): String = this.orEmpty()
    .mapNotNull { module ->
        val url = module.url.trim()
        if (url.isBlank()) {
            null
        } else {
            "$url;${CustomExternalModuleStage.normalize(module.stage)}"
        }
    }
    .joinToString("|")

private const val MAX_REMOTE_ARTIFACT_RUNS = 30
private const val MAX_PERSISTED_REMOTE_ARTIFACTS = 240
private const val KERNEL_WORKFLOW_FILE = "kernel-custom.yml"
private const val MIRROR_WORKFLOW_FILE = "mirror-custom-artifacts.yml"

private fun workflowActionsUrl(owner: String, repoName: String): String =
    "https://github.com/$owner/$repoName/actions/workflows/$KERNEL_WORKFLOW_FILE"
private const val MIRROR_WORKFLOW_MAX_POLLS = 40
private const val MIRROR_RELEASE_ASSET_MAX_POLLS = 6

private fun normalizeMirrorBaseUrl(url: String): String {
    val trimmed = url.trim()
    if (trimmed.isBlank()) return ""
    return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
}

private fun releaseAssetUrl(owner: String, repoName: String, runId: Long, artifactName: String): String {
    val tag = mirrorReleaseTag(runId)
    val asset = Uri.encode("$artifactName.zip")
    return "https://github.com/$owner/$repoName/releases/download/$tag/$asset"
}

private fun mirrorReleaseTag(runId: Long): String = "mirror-custom-run-$runId"

private fun Artifact.toBuildArtifact(runId: Long): BuildArtifact = BuildArtifact(
    id = id,
    name = name,
    sizeInBytes = sizeInBytes,
    archiveDownloadUrl = archiveDownloadUrl,
    expired = expired,
    createdAt = createdAt,
    runId = runId,
    runTitle = "工作流 #$runId",
    runNumber = 0,
    runCreatedAt = createdAt
)

private fun mergeRemoteArtifacts(
    existing: List<BuildArtifact>,
    incoming: List<BuildArtifact>
): List<BuildArtifact> {
    val incomingRunIds = incoming.map { it.runId }.toSet()
    return (incoming + existing.filterNot { it.runId in incomingRunIds })
        .distinctBy { it.id }
        .sortedForDisplay()
        .take(MAX_PERSISTED_REMOTE_ARTIFACTS)
}

private fun List<BuildArtifact>.sortedForDisplay(): List<BuildArtifact> =
    sortedWith(
        compareByDescending<BuildArtifact> { it.runNumber }
            .thenByDescending { it.runId }
            .thenBy { it.name }
    )

private fun List<DownloadedArtifact>.sortedDownloadedForDisplay(): List<DownloadedArtifact> =
    sortedWith(
        compareByDescending<DownloadedArtifact> { it.runNumber }
            .thenByDescending { it.runId }
            .thenBy { it.name }
    )

private data class Quintuple<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

private operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component1() = a
private operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component2() = b
private operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component3() = c
private operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component4() = d
private operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component5() = e
