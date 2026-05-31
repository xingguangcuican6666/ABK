package com.abk.kernel.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import androidx.annotation.StringRes
import com.abk.kernel.utils.LocaleHelper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.abk.kernel.BuildConfig
import com.abk.kernel.R
import com.abk.kernel.data.model.*
import com.abk.kernel.data.repository.GitHubRepository
import com.abk.kernel.data.repository.PreferencesRepository
import com.abk.kernel.data.repository.Result
import com.abk.kernel.utils.BuildMonitorService
import com.abk.kernel.utils.BuildProgressUtils
import com.abk.kernel.utils.DownloadDirectoryUtils
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.NotificationUtils
import com.abk.kernel.utils.RootUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

// ── UI State ─────────────────────────────────────────────────────────────────

enum class AuthStep { INTRO, LOGIN, FORK_CHECK }

enum class ManagerAccessState {
    UNKNOWN,
    NATIVE_MANAGER,
    ROOT_ONLY,
    NO_ROOT,
    NATIVE_KERNEL_NO_MANAGER
}

data class WorkflowEnablementPrompt(
    val message: String,
    val actionUrl: String
)

enum class BuildPlanShareScope { FULL, FEATURES_ONLY }

data class BuildPlanImportPreview(
    val plan: BuildPlan,
    val scope: BuildPlanShareScope
)

data class MainUiState(
    val authStep: AuthStep = AuthStep.INTRO,
    val rootGranted: Boolean = false,
    val isLoggedIn: Boolean = false,
    val user: GitHubUser? = null,
    val forkRepo: GitHubRepo? = null,
    val behindBy: Int = 0,
    val showSyncPrompt: Boolean = false,
    val showOobe: Boolean = false,
    val oobeCompleted: Boolean = false,
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
    val activeBuildRuns: List<WorkflowRun> = emptyList(),
    val buildProgressByRunId: Map<Long, BuildProgress> = emptyMap(),
    val buildConfig: KernelBuildConfig = KernelBuildConfig(),
    val buildPlans: List<BuildPlan> = emptyList(),
    val buildQueue: List<BuildQueueItem> = emptyList(),
    val buildQueueProcessing: Boolean = false,
    val cancellingWorkflowRunIds: Set<Long> = emptySet(),
    val runtimeModuleRepositories: List<RuntimeModuleRepository> = emptyList(),
    val buildModuleRepositories: List<ModuleCatalogRepository> = emptyList(),
    val refreshingRuntimeModuleRepositoryIds: Set<String> = emptySet(),
    val refreshingBuildModuleRepositoryIds: Set<String> = emptySet(),
    val validatingCustomExternalModule: Boolean = false,
    val customExternalModuleError: String? = null,
    val recommendedBuildConfig: KernelBuildConfig? = null,
    val workflowEnablementPrompt: WorkflowEnablementPrompt? = null,
    val buildParameterSummaries: Map<Long, BuildParameterSummary> = emptyMap(),
    val loadingBuildParameterRunIds: Set<Long> = emptySet(),
    val buildParameterErrors: Map<Long, String> = emptyMap(),
    // Download
    val downloadedArtifacts: List<DownloadedArtifact> = emptyList(),
    val artifacts: List<BuildArtifact> = emptyList(),
    val prebuiltGkiReleases: List<PrebuiltGkiRelease> = emptyList(),
    val isLoadingPrebuiltGkiReleases: Boolean = false,
    val prebuiltGkiAssetsByReleaseId: Map<Long, List<PrebuiltGkiAsset>> = emptyMap(),
    val loadingPrebuiltGkiAssetReleaseIds: Set<Long> = emptySet(),
    val isDownloading: Boolean = false,
    val downloadProgress: Map<Long, Int> = emptyMap(),
    val activeDownloadTasks: List<ActiveDownloadTask> = emptyList(),
    val pendingAutoDownloadRunId: Long = -1L,
    val deletingWorkflowRunId: Long? = null,
    // Settings
    val termsLoaded: Boolean = false,
    val termsAccepted: Boolean = false,
    val autoDownload: Boolean = true,
    val notifyBuild: Boolean = true,
    val themeMode: String = "dark",
    val dynamicColorEnabled: Boolean = true,
    val customThemeColorArgb: Int? = null,
    val customAccentColorArgb: Int? = null,
    val customBackgroundUri: String? = null,
    val backgroundImageEnabled: Boolean = false,
    val uiSurfaceAlpha: Float = 1f,
    val downloadDirectory: String = DownloadDirectoryUtils.defaultDirectoryPath(),
    val downloadMirrorBaseUrl: String = "",
    val prebuiltGkiEnabled: Boolean = true,
    val predictiveBackEnabled: Boolean = true,
    val runtimeNavigationEnabled: Boolean = false,
    val webViewDebugEnabled: Boolean = false,
    val managerAccessState: ManagerAccessState = ManagerAccessState.UNKNOWN,
    val managerAccessError: String? = null,
    val hasNativeManagerPermission: Boolean = false,
    val abkRuntimeStatus: AbkRuntimeStatus? = null,
    val abkRuntimeLoading: Boolean = false,
    val abkRuntimeError: String? = null,
    val abkRuntimeModuleActionId: String? = null,
    val abkRuntimeModuleActionTitle: String? = null,
    val abkRuntimeModuleActionOutput: List<String> = emptyList(),
    val managerSettingsBackend: String? = null,
    val managerSettingsTitle: String = "",
    val managerSettingsItems: List<ManagerSettingItem> = emptyList(),
    val managerSettingsLoading: Boolean = false,
    val managerSettingsError: String? = null,
    val managerSettingActionId: String? = null,
    val managerToolsLoading: Boolean = false,
    val managerToolsError: String? = null,
    val managerToolActionId: String? = null,
    val selinuxEnforcing: Boolean = true,
    val selinuxModeText: String = "",
    val umountPaths: List<String> = emptyList(),
    val appProfileTemplates: List<AppProfileTemplateItem> = emptyList(),
    val appProfileTemplatesLoading: Boolean = false,
    val appProfileTemplatesError: String? = null,
    val selectedAppProfileTemplateId: String? = null,
    val selectedAppProfileTemplateContent: String = "",
    val appProfileTemplateSaving: Boolean = false,
    val rootGrantApps: List<RootGrantApp> = emptyList(),
    val rootGrantRuntimeBackend: String? = null,
    val rootGrantLoading: Boolean = false,
    val rootGrantError: String? = null,
    val rootGrantSavingPackage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesRepository(application)
    val github = GitHubRepository()
    private val gson = Gson()
    private val ksuModuleListType = object : TypeToken<List<Map<String, Any?>>>() {}.type
    private var hasSavedBuildConfig = false
    private val monitoredRunIds = mutableSetOf<Long>()
    private val preparedMirrorArtifacts = mutableMapOf<Long, Set<String>>()
    private val artifactDownloadJobs = mutableMapOf<Long, Job>()
    private var hasCheckedWorkflowEnablementThisLaunch = false
    private var hasShownInitialOobeThisLaunch = false
    private var hasRefreshedGitHubSessionThisLaunch = false
    private var buildQueueJob: Job? = null

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private fun text(@StringRes resId: Int, vararg args: Any): String =
        LocaleHelper.str(resId, *args)

    private fun managerAccessErrorMessage(
        access: RootUtils.ManagerAccessInfo,
        rootGranted: Boolean
    ): String {
        access.diagnostic?.takeIf { it.isNotBlank() }?.let { return it }
        return when (access.kind) {
            RootUtils.ManagerAccessKind.NATIVE_MANAGER -> ""
            RootUtils.ManagerAccessKind.NO_ROOT -> text(R.string.vm_external_manager_no_root)
            RootUtils.ManagerAccessKind.ROOT_ONLY -> text(R.string.vm_external_root_no_native_permission)
            RootUtils.ManagerAccessKind.NATIVE_KERNEL_NO_MANAGER ->
                text(R.string.vm_native_kernel_no_manager)
        }
    }

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
                    "queued", "waiting", "requested", "pending" -> BuildStatus.QUEUED
                    "in_progress" -> BuildStatus.IN_PROGRESS
                    "completed" -> when (run.conclusion) {
                        "success" -> BuildStatus.SUCCESS
                        "cancelled" -> BuildStatus.CANCELLED
                        else -> BuildStatus.FAILURE
                    }
                    else -> BuildStatus.IDLE
                }
                _uiState.update {
                    it.withBuildRunDisplay(
                        run = run,
                        status = bs,
                        progress = progress,
                        cancellingWorkflowRunIds = if (status == "completed") {
                            it.cancellingWorkflowRunIds - run.id
                        } else {
                            it.cancellingWorkflowRunIds
                        }
                    )
                }
                syncBuildQueueWithRun(run, bs)
                if (bs == BuildStatus.SUCCESS) {
                    loadArtifacts(run.id, autoDownload = true)
                }
                if (bs !in ACTIVE_BUILD_STATUSES) {
                    monitoredRunIds.remove(run.id)
                    processBuildQueue()
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
                    if (!_uiState.value.isPollingToken && !hasRefreshedGitHubSessionThisLaunch) {
                        hasRefreshedGitHubSessionThisLaunch = true
                        viewModelScope.launch {
                            refreshGitHubSessionOnLaunch(name.isNullOrBlank())
                        }
                    }
                } else {
                    github.updateToken(null)
                    hasCheckedWorkflowEnablementThisLaunch = false
                    hasRefreshedGitHubSessionThisLaunch = false
                    _uiState.update {
                        it.copy(
                            isLoggedIn = false,
                            user = null,
                            forkRepo = null,
                            behindBy = 0,
                            showSyncPrompt = false,
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
            prefs.oobeCompleted.collect { completed ->
                _uiState.update { state -> state.copy(oobeCompleted = completed) }
            }
        }
        viewModelScope.launch {
            combine(
                prefs.themeMode,
                prefs.dynamicColorEnabled,
                prefs.customThemeColorArgb,
                prefs.customAccentColorArgb
            ) { mode, dynamicColorEnabled, themeColor, accentColor ->
                ThemePreferences(mode, dynamicColorEnabled, themeColor, accentColor)
            }.collect { themePrefs ->
                _uiState.update {
                    it.copy(
                        themeMode = themePrefs.themeMode,
                        dynamicColorEnabled = themePrefs.dynamicColorEnabled,
                        customThemeColorArgb = themePrefs.customThemeColorArgb,
                        customAccentColorArgb = themePrefs.customAccentColorArgb
                    )
                }
            }
        }
        viewModelScope.launch {
            prefs.downloadDirectory.collect { path ->
                _uiState.update { it.copy(downloadDirectory = path) }
            }
        }
        viewModelScope.launch {
            prefs.downloadMirrorBaseUrl.collect { url ->
                _uiState.update { it.copy(downloadMirrorBaseUrl = url) }
            }
        }
        viewModelScope.launch {
            combine(
                prefs.customBackgroundUri,
                prefs.backgroundImageEnabled,
                prefs.uiSurfaceAlpha
            ) { uri, enabled, alpha ->
                BackgroundPreferences(uri, enabled, alpha)
            }.collect { backgroundPrefs ->
                _uiState.update {
                    it.copy(
                        customBackgroundUri = backgroundPrefs.uri,
                        backgroundImageEnabled = backgroundPrefs.enabled,
                        uiSurfaceAlpha = backgroundPrefs.alpha
                    )
                }
            }
        }
        viewModelScope.launch {
            prefs.prebuiltGkiEnabled.collect { enabled ->
                _uiState.update {
                    if (enabled) {
                        it.copy(prebuiltGkiEnabled = true)
                    } else {
                        it.copy(
                            prebuiltGkiEnabled = false,
                            prebuiltGkiReleases = emptyList(),
                            isLoadingPrebuiltGkiReleases = false,
                            prebuiltGkiAssetsByReleaseId = emptyMap(),
                            loadingPrebuiltGkiAssetReleaseIds = emptySet()
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            prefs.predictiveBackEnabled.collect { enabled ->
                _uiState.update { it.copy(predictiveBackEnabled = enabled) }
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
            prefs.buildPlansJson.collect { json ->
                _uiState.update { it.copy(buildPlans = parseBuildPlans(json)) }
            }
        }
        viewModelScope.launch {
            prefs.buildQueueJson.collect { json ->
                _uiState.update { it.copy(buildQueue = parseBuildQueue(json)) }
                processBuildQueue()
            }
        }
        viewModelScope.launch {
            prefs.runtimeModuleRepositoriesJson.collect { json ->
                val repositories = parseRuntimeModuleRepositories(json)
                _uiState.update { it.copy(runtimeModuleRepositories = repositories) }
                refreshStaleRuntimeModuleRepositories(repositories)
            }
        }
        viewModelScope.launch {
            prefs.buildModuleRepositoriesJson.collect { json ->
                val repositories = parseBuildModuleRepositories(json)
                _uiState.update { it.copy(buildModuleRepositories = repositories) }
                refreshStaleBuildModuleRepositories(repositories)
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
            prefs.buildParameterSummariesJson.collect { json ->
                val restored = parseBuildParameterSummaries(json).associateBy { it.runId }
                _uiState.update { it.copy(buildParameterSummaries = restored) }
            }
        }
        viewModelScope.launch {
            prefs.pendingAutoDownloadRunId.collect { runId ->
                _uiState.update { it.copy(pendingAutoDownloadRunId = runId) }
            }
        }
        viewModelScope.launch {
            prefs.runtimeNavigationEnabled.collect { enabled ->
                _uiState.update { it.copy(runtimeNavigationEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            prefs.webViewDebugEnabled.collect { enabled ->
                _uiState.update { it.copy(webViewDebugEnabled = enabled) }
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
        }
    }

    fun requestRoot() {
        viewModelScope.launch {
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
        }
    }

    fun setRuntimeNavigationEnabled(enabled: Boolean) {
        _uiState.update { it.copy(runtimeNavigationEnabled = enabled) }
        viewModelScope.launch { prefs.setRuntimeNavigationEnabled(enabled) }
        if (enabled) refreshAbkRuntimeStatus()
    }

    fun setWebViewDebugEnabled(enabled: Boolean) {
        _uiState.update { it.copy(webViewDebugEnabled = enabled) }
        viewModelScope.launch { prefs.setWebViewDebugEnabled(enabled) }
    }

    fun refreshAbkRuntimeStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(abkRuntimeLoading = true, abkRuntimeError = null) }
            val rootGranted = _uiState.value.rootGranted
            val (access, runtimeStatus, runtimeError) = withContext(Dispatchers.IO) {
                val managerAccess = resolveManagerAccess(rootGranted)
                if (!managerAccess.hasNativeManagerPermission) {
                    val snapshot = if (rootGranted) RootUtils.readManagerRuntimeSnapshot() else null
                    val compatStatus = snapshot
                        ?.takeIf { it.manager.active }
                        ?.let {
                            mergeRuntimeStatus(
                                manager = it.manager,
                                controlJson = it.controlStatusJson,
                                ksuModulesJson = it.ksuModulesJson
                            )
                        }
                    return@withContext Triple(
                        managerAccess,
                        compatStatus,
                        managerAccessErrorMessage(managerAccess, rootGranted)
                    )
                }
                val snapshot = RootUtils.readManagerRuntimeSnapshot()
                if (!snapshot.manager.active) {
                    Triple(
                        managerAccess,
                        null as AbkRuntimeStatus?,
                        snapshot.manager.diagnostics.firstOrNull()
                    )
                } else {
                    Triple(
                        managerAccess,
                        mergeRuntimeStatus(
                            manager = snapshot.manager,
                            controlJson = snapshot.controlStatusJson,
                            ksuModulesJson = snapshot.ksuModulesJson
                        ),
                        null as String?
                    )
                }
            }
            _uiState.update {
                if (runtimeStatus != null) {
                    it.copy(
                        managerAccessState = access.toUiState(),
                        managerAccessError = if (access.hasNativeManagerPermission) access.diagnostic else runtimeError,
                        hasNativeManagerPermission = access.hasNativeManagerPermission,
                        abkRuntimeStatus = runtimeStatus,
                        abkRuntimeLoading = false,
                        abkRuntimeError = if (access.hasNativeManagerPermission) null else runtimeError
                    )
                } else {
                    it.copy(
                        managerAccessState = access.toUiState(),
                        managerAccessError = runtimeError,
                        hasNativeManagerPermission = access.hasNativeManagerPermission,
                        abkRuntimeStatus = null,
                        abkRuntimeLoading = false,
                        abkRuntimeError = runtimeError ?: text(R.string.runtime_manager_inactive)
                    )
                }
            }
        }
    }

    fun refreshRootGrantApps(force: Boolean = false) {
        val current = _uiState.value
        val currentBackend = current.abkRuntimeStatus?.runtimeBackend?.backend
        if (!force && current.rootGrantLoading) return
        if (
            !force &&
            current.rootGrantApps.isNotEmpty() &&
            current.rootGrantRuntimeBackend == currentBackend &&
            current.rootGrantError == null
        ) {
            return
        }

        viewModelScope.launch {
            val backendAtRequest = _uiState.value.abkRuntimeStatus?.runtimeBackend?.backend
            val rootGranted = _uiState.value.rootGranted
            _uiState.update {
                it.copy(rootGrantLoading = true, rootGrantError = null)
            }
            val (access, active, apps, diagnostic) = withContext(Dispatchers.IO) {
                val managerAccess = resolveManagerAccess(rootGranted)
                if (!managerAccess.hasNativeManagerPermission) {
                    return@withContext Quadruple(
                        managerAccess,
                        false,
                        emptyList<RootGrantApp>(),
                        managerAccessErrorMessage(managerAccess, rootGranted)
                    )
                }
                val rootGrantApps = if (managerAccess.hasNativeManagerPermission) {
                    RootUtils.listRootGrantApps(getApplication<Application>())
                } else {
                    emptyList()
                }
                Quadruple(managerAccess, true, rootGrantApps, null as String?)
            }
            _uiState.update {
                if (!active) {
                    it.copy(
                        managerAccessState = access.toUiState(),
                        managerAccessError = diagnostic,
                        hasNativeManagerPermission = access.hasNativeManagerPermission,
                        rootGrantApps = emptyList(),
                        rootGrantRuntimeBackend = backendAtRequest,
                        rootGrantLoading = false,
                        rootGrantError = diagnostic ?: text(R.string.runtime_manager_inactive)
                    )
                } else {
                    it.copy(
                        managerAccessState = access.toUiState(),
                        managerAccessError = null,
                        hasNativeManagerPermission = access.hasNativeManagerPermission,
                        rootGrantApps = apps,
                        rootGrantRuntimeBackend = backendAtRequest,
                        rootGrantLoading = false,
                        rootGrantError = null
                    )
                }
            }
        }
    }

    fun setRootGrantAllowed(packageName: String, allowed: Boolean) {
        val app = _uiState.value.rootGrantApps.firstOrNull { it.packageName == packageName } ?: return
        val updatedProfile = app.profile.copy(
            allowSu = allowed,
            rootUseDefault = true,
            nonRootUseDefault = true,
            name = app.packageName,
            currentUid = app.uid
        )
        saveRootGrantProfile(updatedProfile)
    }

    fun saveRootGrantProfile(profile: RootGrantProfile) {
        val cleanPackage = profile.name.trim()
        if (cleanPackage.isBlank() || _uiState.value.rootGrantSavingPackage != null) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(rootGrantSavingPackage = cleanPackage, rootGrantError = null)
            }
            val rootGranted = _uiState.value.rootGranted
            val result = withContext(Dispatchers.IO) {
                val access = resolveManagerAccess(rootGranted)
                if (!access.hasNativeManagerPermission) {
                    false to managerAccessErrorMessage(access, rootGranted)
                } else {
                    RootUtils.setRootGrantProfile(profile.copy(name = cleanPackage)) to null
                }
            }
            _uiState.update { state ->
                if (result.first) {
                    state.copy(
                        rootGrantSavingPackage = null,
                        rootGrantError = null,
                        rootGrantApps = state.rootGrantApps.map { app ->
                            if (app.packageName == cleanPackage) {
                                app.copy(profile = profile.copy(name = cleanPackage))
                            } else {
                                app
                            }
                        }
                    )
                } else {
                    state.copy(
                        rootGrantSavingPackage = null,
                        rootGrantError = result.second ?: text(R.string.vm_save_failed)
                    )
                }
            }
            if (result.first) refreshRootGrantApps(force = true)
        }
    }

    private fun mergeRuntimeStatus(
        manager: RootUtils.ManagerRuntimeProbe,
        controlJson: String?,
        ksuModulesJson: String?
    ): AbkRuntimeStatus {
        val controlStatus = controlJson?.let { body ->
            runCatching { gson.fromJson(body, AbkRuntimeStatus::class.java) }.getOrNull()
        }
        val ksuModules = parseKsuModules(ksuModulesJson)
        val controlModules = controlStatus?.modules.orEmpty().map { module ->
            module.copy(
                type = module.type.ifBlank { "builtin" },
                source = module.source.ifBlank { "abk" },
                readonly = module.readonly || !module.controllable
            )
        }
        val kpmModules = parseKpmModules()
        val mergedModules = mergeRuntimeModules(controlModules, ksuModules, kpmModules)
        val runtimeBackendInfo = manager.toRuntimeInfo()
        val managerInfo = controlStatus?.manager?.let { compilerManager ->
            val extraCaps = when (manager.backend) {
                "native" -> listOf("native_manager", "root_policy")
                "su", "ksud" -> listOf("root_shell")
                else -> emptyList()
            }
            compilerManager.copy(
                active = true,
                capabilities = (compilerManager.capabilities + extraCaps).distinct(),
                diagnostics = (compilerManager.diagnostics + manager.diagnostics).distinct()
            )
        } ?: runtimeBackendInfo
        return (controlStatus ?: AbkRuntimeStatus()).copy(
            schema = maxOf(controlStatus?.schema ?: 0, 4),
            abkVersion = controlStatus?.abkVersion?.ifBlank { BuildConfig.VERSION_NAME } ?: BuildConfig.VERSION_NAME,
            workMode = resolveRuntimeWorkMode(controlStatus?.workMode, manager),
            manager = managerInfo,
            runtimeBackend = runtimeBackendInfo,
            modules = mergedModules
        )
    }

    private fun resolveRuntimeWorkMode(
        controlWorkMode: String?,
        manager: RootUtils.ManagerRuntimeProbe
    ): String {
        normalizeRuntimeWorkMode(controlWorkMode)?.let { return it }
        normalizeRuntimeWorkMode(manager.workMode)?.let { return it }
        return when {
            manager.capabilities.any { it.equals("lkm", ignoreCase = true) } -> "lkm"
            manager.backend == "native" -> "built-in"
            else -> ""
        }
    }

    private fun normalizeRuntimeWorkMode(value: String?): String? {
        return when (value?.trim()?.lowercase()) {
            "lkm" -> "lkm"
            "builtin", "built-in", "built_in" -> "built-in"
            else -> null
        }
    }

    private fun RootUtils.ManagerRuntimeProbe.toRuntimeInfo(): AbkRuntimeManagerInfo =
        AbkRuntimeManagerInfo(
            displayName = displayName.ifBlank { if (active) "Root" else "" },
            variant = variant,
            backend = backend,
            version = version,
            active = active,
            capabilities = capabilities,
            diagnostics = diagnostics
        )

    private fun parseKsuModules(json: String?): List<AbkRuntimeModule> {
        if (json.isNullOrBlank()) return emptyList()
        val records = runCatching {
            gson.fromJson<List<Map<String, Any?>>>(json, ksuModuleListType)
        }.getOrNull().orEmpty()
        return records.mapNotNull { item ->
            val id = item.runtimeString("id")
            if (id.isBlank()) return@mapNotNull null
            AbkRuntimeModule(
                id = id,
                name = item.runtimeString("name").ifBlank { id },
                author = item.runtimeString("author"),
                type = "standard",
                version = item.runtimeString("version"),
                versionCode = item.runtimeLong("versionCode"),
                description = item.runtimeString("description"),
                stage = "runtime",
                source = "ksud",
                moduleDir = "/data/adb/modules/$id",
                webRoot = "/data/adb/modules/$id/webroot",
                readonly = false,
                controllable = true,
                enabled = item.runtimeBoolean("enabled", true),
                update = item.runtimeBoolean("update"),
                remove = item.runtimeBoolean("remove"),
                hasWebUi = item.runtimeBoolean("web"),
                hasActionScript = item.runtimeBoolean("action"),
                actionSupported = item.runtimeBoolean("action")
            )
        }
    }

    private fun parseKpmModules(): List<AbkRuntimeModule> {
        val listResult = RootUtils.listKpmModules()
        if (!listResult.success) return emptyList()
        return parseKpmModuleNames(listResult.output.joinToString("\n"))
            .map { name ->
                val properties = RootUtils.getKpmModuleInfo(name)
                    .takeIf { it.success }
                    ?.output
                    ?.flatMap { it.lineSequence().toList() }
                    ?.mapNotNull { line ->
                        val clean = line.trim()
                        if (clean.isBlank() || clean.startsWith("#")) return@mapNotNull null
                        val separator = when {
                            "=" in clean -> "="
                            ":" in clean -> ":"
                            else -> return@mapNotNull null
                        }
                        val parts = clean.split(separator, limit = 2)
                        parts[0].trim().lowercase() to parts.getOrElse(1) { "" }.trim()
                    }
                    ?.toMap()
                    .orEmpty()
                AbkRuntimeModule(
                    id = name,
                    name = properties["name"].orEmpty().ifBlank { name },
                    author = properties["author"].orEmpty(),
                    type = "kpm",
                    version = properties["version"].orEmpty(),
                    description = properties["description"].orEmpty(),
                    source = "kpm",
                    readonly = true,
                    controllable = false,
                    enabled = true,
                    kpmArgs = properties["args"].orEmpty()
                )
            }
    }

    private fun parseKpmModuleNames(output: String): List<String> {
        if (output.isBlank()) return emptyList()
        val jsonNames = runCatching {
            val root = gson.fromJson(output, Any::class.java)
            when (root) {
                is List<*> -> root.mapNotNull(::kpmNameFromJsonRecord)
                is Map<*, *> -> {
                    val modules = root["modules"] ?: root["items"] ?: root["data"]
                    if (modules is List<*>) modules.mapNotNull(::kpmNameFromJsonRecord) else null
                }
                else -> null
            }?.distinct()
        }.getOrNull()
        if (jsonNames != null) return jsonNames

        val namePattern = Regex("""^[A-Za-z0-9_.@+-]+$""")
        val keyValuePattern = Regex("""^(?:name|module|id)\s*[:=]\s*(\S+).*$""", RegexOption.IGNORE_CASE)
        return output
            .lineSequence()
            .map { it.trim().trim('-', '*', ' ') }
            .map { line ->
                val keyValue = keyValuePattern.matchEntire(line)?.groupValues?.getOrNull(1)
                keyValue ?: line
                    .replace(Regex("""^\[\d+]\s*"""), "")
                    .replace(Regex("""^\d+[.)]\s*"""), "")
                    .substringBefore('\t')
                    .substringBefore(' ')
                    .trim()
            }
            .filter { it.isNotBlank() && namePattern.matches(it) }
            .filterNot { it.equals("loaded", ignoreCase = true) || it.equals("modules", ignoreCase = true) }
            .distinct()
            .toList()
    }

    private fun kpmNameFromJsonRecord(record: Any?): String? =
        when (record) {
            is String -> record.trim()
            is Map<*, *> -> listOf("name", "id", "module")
                .asSequence()
                .mapNotNull { key -> record[key]?.toString()?.trim()?.takeIf { it.isNotBlank() } }
                .firstOrNull()
            else -> null
        }

    private fun mergeRuntimeModules(
        controlModules: List<AbkRuntimeModule>,
        ksuModules: List<AbkRuntimeModule>,
        kpmModules: List<AbkRuntimeModule>
    ): List<AbkRuntimeModule> {
        val merged = linkedMapOf<String, AbkRuntimeModule>()

        fun put(module: AbkRuntimeModule) {
            val keyId = module.id.ifBlank { module.name }.trim()
            val key = if (module.normalizedType() == "kpm") "kpm:$keyId" else keyId
            if (key.isBlank()) return
            val current = merged[key]
            merged[key] = if (current == null) {
                module
            } else {
                current.copy(
                    name = current.name.ifBlank { module.name },
                    author = current.author.ifBlank { module.author },
                    version = current.version.ifBlank { module.version },
                    versionCode = current.versionCode.takeIf { it > 0 } ?: module.versionCode,
                    description = current.description.ifBlank { module.description },
                    repoUrl = current.repoUrl.ifBlank { module.repoUrl },
                    type = mergeRuntimeModuleType(current, module),
                    stage = listOf(current.stage, module.stage)
                        .flatMap { it.split(',') }
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(","),
                    source = listOf(current.source, module.source)
                        .flatMap { it.split(',') }
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(","),
                    moduleDir = current.moduleDir.ifBlank { module.moduleDir },
                    webRoot = current.webRoot.ifBlank { module.webRoot },
                    readonly = current.readonly && module.readonly,
                    controllable = current.controllable || module.controllable,
                    enabled = current.enabled && module.enabled,
                    update = current.update || module.update,
                    remove = current.remove || module.remove,
                    hasWebUi = current.hasWebUi || module.hasWebUi,
                    hasActionScript = current.hasActionScript || module.hasActionScript,
                    actionSupported = current.actionSupported || module.actionSupported,
                    kpmArgs = current.kpmArgs.ifBlank { module.kpmArgs }
                )
            }
        }

        ksuModules.forEach(::put)
        controlModules.forEach(::put)
        kpmModules.forEach(::put)

        return merged.values.toList()
    }

    private fun mergeRuntimeModuleType(current: AbkRuntimeModule, next: AbkRuntimeModule): String =
        when {
            current.normalizedType() == "kpm" || next.normalizedType() == "kpm" -> "kpm"
            current.normalizedType() == "standard" || next.normalizedType() == "standard" -> "standard"
            else -> "builtin"
        }

    private fun AbkRuntimeModule.normalizedType(): String =
        type.ifBlank {
            when {
                source.split(',').any { it.trim() == "kpm" } -> "kpm"
                source.split(',').any { it.trim() == "ksud" } -> "standard"
                else -> "builtin"
            }
        }

    private fun Map<String, Any?>.runtimeString(key: String): String =
        this[key]?.toString()?.trim().orEmpty()

    private fun Map<String, Any?>.runtimeBoolean(key: String, default: Boolean = false): Boolean {
        val value = this[key] ?: return default
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            else -> when (value.toString().trim().lowercase()) {
                "1", "y", "yes", "true", "on", "enabled" -> true
                "0", "n", "no", "false", "off", "disabled" -> false
                else -> default
            }
        }
    }

    private fun Map<String, Any?>.runtimeLong(key: String): Long {
        val value = this[key] ?: return 0L
        return when (value) {
            is Number -> value.toLong()
            else -> value.toString().trim().toLongOrNull() ?: 0L
        }
    }

    private fun AbkRuntimeModule.isKsuBacked(): Boolean =
        normalizedType() == "standard" || source.split(',').any { it.trim() == "ksud" }

    fun setAbkRuntimeModuleEnabled(moduleId: String, enabled: Boolean) {
        val cleanId = moduleId.trim()
        if (cleanId.isBlank() || _uiState.value.abkRuntimeModuleActionId != null) return

        viewModelScope.launch {
            val hasRoot = _uiState.value.rootGranted || withContext(Dispatchers.IO) {
                RootUtils.refreshRootState()
            }
            if (!hasRoot) {
                _uiState.update { it.copy(abkRuntimeError = text(R.string.settings_operation_incomplete)) }
                return@launch
            }
            val module = _uiState.value.abkRuntimeStatus?.modules?.firstOrNull { it.id == cleanId }
            _uiState.update {
                it.copy(
                    rootGranted = true,
                    abkRuntimeModuleActionId = cleanId,
                    abkRuntimeError = null
                )
            }
            val result = withContext(Dispatchers.IO) {
                when {
                    module?.isAbkMetaMount() == true -> RootUtils.setAbkMetaMountEnabled(enabled)
                    module?.preferredControlBackend() == RuntimeModuleControlBackend.ABK_CONTROL -> {
                        val command = if (enabled) "enable $cleanId" else "disable $cleanId"
                        val controlResult = RootUtils.writeAbkControlCommand(command)
                        if (controlResult.success) {
                            controlResult
                        } else if (module.isKsuBacked()) {
                            RootUtils.setKsuModuleEnabled(cleanId, enabled)
                        } else {
                            controlResult
                        }
                    }
                    module?.preferredControlBackend() == RuntimeModuleControlBackend.KSU -> {
                        RootUtils.setKsuModuleEnabled(cleanId, enabled)
                    }
                    else -> RootUtils.writeAbkControlCommand(
                        if (enabled) "enable $cleanId" else "disable $cleanId"
                    )
                }
            }
            if (!result.success) {
                _uiState.update {
                    it.copy(
                        abkRuntimeModuleActionId = null,
                        abkRuntimeError = text(R.string.settings_operation_incomplete)
                    )
                }
            } else {
                _uiState.update { it.copy(abkRuntimeModuleActionId = null) }
                refreshAbkRuntimeStatus()
            }
        }
    }

    fun setAbkRuntimeModulePendingUninstall(moduleId: String, pending: Boolean) {
        val cleanId = moduleId.trim()
        if (cleanId.isBlank() || _uiState.value.abkRuntimeModuleActionId != null) return

        viewModelScope.launch {
            val hasRoot = _uiState.value.rootGranted || withContext(Dispatchers.IO) {
                RootUtils.refreshRootState()
            }
            if (!hasRoot) {
                _uiState.update { it.copy(abkRuntimeError = text(R.string.settings_operation_incomplete)) }
                return@launch
            }
            val module = _uiState.value.abkRuntimeStatus?.modules?.firstOrNull { it.id == cleanId }
            if (module?.isKsuBacked() != true) {
                _uiState.update { it.copy(abkRuntimeError = text(R.string.vm_runtime_module_uninstall_unsupported)) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    rootGranted = true,
                    abkRuntimeModuleActionId = cleanId,
                    abkRuntimeError = null
                )
            }
            val result = withContext(Dispatchers.IO) {
                RootUtils.setKsuModulePendingUninstall(cleanId, pending)
            }
            if (!result.success) {
                _uiState.update {
                    it.copy(
                        abkRuntimeModuleActionId = null,
                        abkRuntimeError = text(R.string.settings_operation_incomplete)
                    )
                }
            } else {
                _uiState.update { it.copy(abkRuntimeModuleActionId = null) }
                refreshAbkRuntimeStatus()
            }
        }
    }

    fun runRuntimeModuleAction(moduleId: String) {
        val cleanId = moduleId.trim()
        val module = _uiState.value.abkRuntimeStatus?.modules?.firstOrNull { it.id == cleanId } ?: return
        if (cleanId.isBlank() || (!module.actionSupported && !module.hasActionScript) || _uiState.value.abkRuntimeModuleActionId != null) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    abkRuntimeModuleActionId = cleanId,
                    abkRuntimeModuleActionTitle = "${module.displayNameForRuntime()} Action",
                    abkRuntimeModuleActionOutput = emptyList(),
                    abkRuntimeError = null
                )
            }
            val result = when (module.preferredActionBackend()) {
                RuntimeModuleActionBackend.ABK_ACTION_SCRIPT -> {
                    RootUtils.runModuleActionScript(
                        module.moduleDir.ifBlank { "/data/adb/modules/$cleanId" }
                    ) { line ->
                        _uiState.update { state ->
                            state.copy(abkRuntimeModuleActionOutput = state.abkRuntimeModuleActionOutput + line)
                        }
                    }
                }
                RuntimeModuleActionBackend.KSU_ACTION -> {
                    RootUtils.runKsuModuleAction(cleanId) { line ->
                        _uiState.update { state ->
                            state.copy(abkRuntimeModuleActionOutput = state.abkRuntimeModuleActionOutput + line)
                        }
                    }
                }
                RuntimeModuleActionBackend.NONE -> {
                    RootUtils.runModuleActionScript(
                        module.moduleDir.ifBlank { "/data/adb/modules/$cleanId" }
                    ) { line ->
                        _uiState.update { state ->
                            state.copy(abkRuntimeModuleActionOutput = state.abkRuntimeModuleActionOutput + line)
                        }
                    }
                }
            }
            _uiState.update { state ->
                val output = state.abkRuntimeModuleActionOutput.ifEmpty { result.output }
                state.copy(
                    abkRuntimeModuleActionId = null,
                    abkRuntimeModuleActionOutput = output,
                    abkRuntimeError = if (result.success) null else text(R.string.settings_operation_incomplete)
                )
            }
        }
    }

    fun dismissRuntimeModuleActionOutput() {
        _uiState.update {
            it.copy(
                abkRuntimeModuleActionTitle = null,
                abkRuntimeModuleActionOutput = emptyList()
            )
        }
    }

    private fun AbkRuntimeModule.displayNameForRuntime(): String =
        name.ifBlank { id.ifBlank { text(R.string.vm_runtime_module_default_name) } }

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

    fun maybeShowInitialOobe() {
        val state = _uiState.value
        if (!state.termsAccepted || state.oobeCompleted || hasShownInitialOobeThisLaunch) return
        hasShownInitialOobeThisLaunch = true
        _uiState.update {
            it.copy(
                showOobe = true,
                authStep = AuthStep.INTRO,
                error = null
            )
        }
    }

    fun openBuildOobe() {
        val state = _uiState.value
        val nextStep = if (state.isLoggedIn && state.user != null) AuthStep.FORK_CHECK else AuthStep.LOGIN
        _uiState.update {
            it.copy(
                showOobe = true,
                authStep = nextStep,
                error = null
            )
        }
        if (state.isLoggedIn && state.user == null) {
            viewModelScope.launch {
                val user = fetchAuthenticatedUserAndStore() ?: return@launch
                _uiState.update { it.copy(authStep = AuthStep.FORK_CHECK, user = user, isLoggedIn = true) }
                checkFork(showSyncPrompt = true, closeOobeWhenReady = true)
            }
        } else if (nextStep == AuthStep.FORK_CHECK) {
            checkFork(showSyncPrompt = true, closeOobeWhenReady = true)
        }
    }

    fun continueOobeToLogin() {
        _uiState.update {
            it.copy(
                showOobe = true,
                authStep = AuthStep.LOGIN,
                error = null
            )
        }
    }

    fun skipOobe() {
        viewModelScope.launch {
            if (!_uiState.value.oobeCompleted) {
                prefs.setOobeCompleted(true)
            }
            closeOobe()
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
                                hasRefreshedGitHubSessionThisLaunch = true
                                prefs.saveToken(token)
                                github.updateToken(token)
                                _uiState.update { it.copy(isPollingToken = false) }
                                fetchUserAndContinueOobe()
                            }
                            "authorization_pending", "slow_down" -> {
                                if (tokenResp.error == "slow_down") delay(5000)
                            }
                            "expired_token", "access_denied" -> {
                                _uiState.update {
                                    it.copy(
                                        isPollingToken = false,
                                        error = text(R.string.vm_auth_failed, tokenResp.error.orEmpty())
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

    private suspend fun fetchAuthenticatedUserAndStore(reportError: Boolean = true): GitHubUser? {
        when (val r = github.getAuthenticatedUser()) {
            is Result.Success -> {
                val user = r.data
                prefs.saveUsername(user.login)
                prefs.saveAvatarUrl(user.avatarUrl)
                _uiState.update { it.copy(user = user, isLoggedIn = true) }
                return user
            }
            is Result.Error -> if (reportError) {
                _uiState.update { it.copy(error = r.message) }
            }
            Result.Loading -> {}
        }
        return null
    }

    private suspend fun refreshGitHubSessionOnLaunch(fetchUser: Boolean) {
        val user = if (fetchUser || _uiState.value.user == null) {
            fetchAuthenticatedUserAndStore(reportError = false)
        } else {
            _uiState.value.user
        } ?: return
        _uiState.update { it.copy(isLoggedIn = true, user = user) }
        checkFork(showSyncPrompt = true, closeOobeWhenReady = false)
    }

    private suspend fun fetchUserAndContinueOobe() {
        val user = fetchAuthenticatedUserAndStore() ?: return
        _uiState.update {
            it.copy(
                user = user,
                isLoggedIn = true,
                showOobe = true,
                authStep = AuthStep.FORK_CHECK
            )
        }
        checkFork(showSyncPrompt = true, closeOobeWhenReady = true)
    }

    private fun closeOobe() {
        _uiState.update {
            it.copy(
                showOobe = false,
                authStep = AuthStep.INTRO,
                deviceCode = null,
                userCode = null,
                verificationUri = null,
                isPollingToken = false,
                error = null
            )
        }
    }

    private fun completeOobe() {
        if (!_uiState.value.oobeCompleted) {
            viewModelScope.launch { prefs.setOobeCompleted(true) }
        }
        closeOobe()
    }

    fun logout() {
        viewModelScope.launch {
            prefs.clearAuth()
            github.updateToken(null)
            hasCheckedWorkflowEnablementThisLaunch = false
            hasRefreshedGitHubSessionThisLaunch = false
            _uiState.update {
                MainUiState(
                    rootGranted = it.rootGranted,
                    authStep = AuthStep.INTRO,
                    termsLoaded = it.termsLoaded,
                    termsAccepted = it.termsAccepted,
                    oobeCompleted = it.oobeCompleted,
                    autoDownload = it.autoDownload,
                    notifyBuild = it.notifyBuild,
                    themeMode = it.themeMode,
                    dynamicColorEnabled = it.dynamicColorEnabled,
                    customThemeColorArgb = it.customThemeColorArgb,
                    customAccentColorArgb = it.customAccentColorArgb,
                    customBackgroundUri = it.customBackgroundUri,
                    backgroundImageEnabled = it.backgroundImageEnabled,
                    uiSurfaceAlpha = it.uiSurfaceAlpha,
                    downloadDirectory = it.downloadDirectory,
                    downloadMirrorBaseUrl = it.downloadMirrorBaseUrl,
                    prebuiltGkiEnabled = it.prebuiltGkiEnabled,
                    predictiveBackEnabled = it.predictiveBackEnabled,
                    runtimeNavigationEnabled = it.runtimeNavigationEnabled,
                    webViewDebugEnabled = it.webViewDebugEnabled,
                    runtimeModuleRepositories = it.runtimeModuleRepositories,
                    buildModuleRepositories = it.buildModuleRepositories
                )
            }
        }
    }

    // ── Fork Management ───────────────────────────────────────────────────

    fun checkFork(showSyncPrompt: Boolean = true, closeOobeWhenReady: Boolean = false) {
        val username = _uiState.value.user?.login ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    authStep = if (it.showOobe) AuthStep.FORK_CHECK else it.authStep
                )
            }
            val forkResult = github.getUserFork(
                BuildConfig.SOURCE_REPO_OWNER, BuildConfig.SOURCE_REPO_NAME, username
            )
            when (forkResult) {
                is Result.Success -> {
                    if (forkResult.data == null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                forkRepo = null,
                                behindBy = 0,
                                showSyncPrompt = false
                            )
                        }
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
                                showSyncPrompt = showSyncPrompt && behind > 0
                            )
                        }
                        onForkContextReady()
                        if (closeOobeWhenReady) {
                            completeOobe()
                        }
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
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            forkRepo = r.data,
                            behindBy = 0,
                            showSyncPrompt = false
                        )
                    }
                    onForkContextReady()
                    completeOobe()
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
            _uiState.update { it.copy(isLoading = true, showSyncPrompt = false) }
            when (val r = github.syncFork(username, fork.name, fork.defaultBranch)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoading = false, behindBy = 0) }
                    onForkContextReady()
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = r.message) }
                else -> {}
            }
        }
    }

    fun dismissSyncPrompt() {
        _uiState.update { it.copy(showSyncPrompt = false) }
    }

    private fun onForkContextReady() {
        loadRecentRuns()
        ensureBuildWorkflowEnabled()
        processBuildQueue()
    }

    private fun ensureBuildWorkflowEnabled() {
        if (hasCheckedWorkflowEnablementThisLaunch) return
        val state = _uiState.value
        val owner = state.user?.login ?: return
        val repo = state.forkRepo ?: return
        hasCheckedWorkflowEnablementThisLaunch = true
        viewModelScope.launch {
            ensureBuildWorkflowEnabled(owner, repo.name, KERNEL_WORKFLOW_FILE, reportError = false)
        }
    }

    private suspend fun ensureBuildWorkflowEnabled(
        owner: String,
        repoName: String,
        workflowFile: String,
        reportError: Boolean
    ): Long? {
        val actionUrl = workflowActionsUrl(owner, repoName, workflowFile)
        return when (val workflow = github.getWorkflow(owner, repoName, workflowFile)) {
            is Result.Success -> {
                if (workflow.data.state != "active") {
                    when (val enabled = github.enableWorkflow(owner, repoName, workflow.data.id)) {
                        is Result.Success -> {
                            delay(1000)
                            when (val refreshed = github.getWorkflow(owner, repoName, workflowFile)) {
                                is Result.Success -> {
                                    if (refreshed.data.state == "active") {
                                        return refreshed.data.id
                                    }
                                    if (reportError) {
                                        showWorkflowEnablementPrompt(
                                            text(R.string.vm_workflow_still_disabled, refreshed.data.state),
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
        if (!state.isLoggedIn || state.user == null || state.forkRepo == null) {
            _uiState.update { it.copy(error = text(R.string.vm_build_login_required)) }
            return
        }
        enqueueBuild(config)
    }

    private fun enqueueBuild(config: KernelBuildConfig) {
        val state = _uiState.value
        val buildConfig = KernelSupport.normalize(config)
        val now = System.currentTimeMillis()
        val item = BuildQueueItem(
            id = UUID.randomUUID().toString(),
            name = suggestedBuildPlanName(buildConfig),
            config = buildConfig,
            createdAt = now,
            status = BuildQueueItemStatus.PENDING
        )
        saveBuildQueue(state.buildQueue + item)
        _uiState.update { it.copy(error = null) }
        processBuildQueue()
    }

    private fun processBuildQueue() {
        val snapshot = _uiState.value
        if (!snapshot.isLoggedIn || snapshot.user == null || snapshot.forkRepo == null) return
        if (snapshot.buildQueueProcessing || buildQueueJob?.isActive == true) return
        val next = snapshot.buildQueue.firstOrNull { it.status == BuildQueueItemStatus.PENDING } ?: return
        val username = snapshot.user?.login ?: return
        val repoName = snapshot.forkRepo?.name ?: BuildConfig.SOURCE_REPO_NAME
        val ref = snapshot.forkRepo?.defaultBranch ?: "main"
        val workflowFile = workflowFileFor(next.config)

        buildQueueJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(buildQueueProcessing = true, isLoading = true, error = null) }
                val wfId = ensureBuildWorkflowEnabled(username, repoName, workflowFile, reportError = true)
                if (wfId == null) {
                    markBuildQueueItemFailed(next.id, text(R.string.build_workflow_required))
                    return@launch
                }

                updateBuildQueueItem(next.id) {
                    it.copy(status = BuildQueueItemStatus.DISPATCHING, error = null)
                }
                _uiState.update {
                    it.copy(
                        buildStatus = BuildStatus.QUEUED,
                        buildProgress = BuildProgress(percent = 0, currentStep = text(R.string.build_queue_dispatching))
                    )
                }
                val previousRunId = when (val prior = github.listRecentRuns(username, repoName, 1, wfId)) {
                    is Result.Success -> prior.data.firstOrNull()?.id
                    else -> null
                }
                when (val r = github.dispatchWorkflow(username, repoName, wfId, next.config.toInputMap(), ref)) {
                    is Result.Success -> {
                        _uiState.update {
                            it.copy(
                                buildStatus = BuildStatus.QUEUED,
                                buildProgress = BuildProgress(percent = 0, currentStep = text(R.string.build_queued))
                            )
                        }
                        delay(5000)
                        findAndMonitorLatestRun(username, repoName, wfId, previousRunId, next.id)
                    }
                    is Result.Error -> {
                        markBuildQueueItemFailed(next.id, r.message)
                        if (r.code == 403 || r.code == 404) {
                            showWorkflowEnablementPrompt(
                                text(R.string.vm_workflow_dispatch_failed, r.message),
                                workflowActionsUrl(username, repoName, workflowFile)
                            )
                        } else {
                            _uiState.update { it.copy(error = r.message, buildStatus = BuildStatus.FAILURE) }
                        }
                    }
                    Result.Loading -> {
                        markBuildQueueItemFailed(next.id, text(R.string.vm_build_dispatch_no_result))
                        _uiState.update { it.copy(buildStatus = BuildStatus.FAILURE) }
                    }
                }
            } finally {
                _uiState.update { it.copy(buildQueueProcessing = false, isLoading = false) }
                buildQueueJob = null
                processBuildQueue()
            }
        }
    }

    private suspend fun findAndMonitorLatestRun(
        owner: String,
        repo: String,
        workflowId: Long,
        previousRunId: Long?,
        queueItemId: String? = null
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
                        queueItemId?.let { id ->
                            updateBuildQueueItem(id) {
                                it.copy(
                                    status = BuildQueueItemStatus.RUNNING,
                                    runId = run.id,
                                    runNumber = run.runNumber,
                                    error = null
                                )
                            }
                        }
                        _uiState.update {
                            it.withBuildRunDisplay(
                                run = run,
                                status = BuildStatus.QUEUED,
                                progress = BuildProgress(
                                    percent = 0,
                                    currentStep = text(R.string.build_queued),
                                    completedSteps = 0,
                                    totalSteps = 1
                                )
                            )
                        }
                        monitoredRunIds += run.id
                        BuildMonitorService.startMonitoring(getApplication(), owner, repo, run.id)
                        return
                    }
                }
                else -> {}
            }
            if (attempt < 5) delay(5_000)
        }
        _uiState.update {
            it.copy(
                error = text(R.string.vm_build_run_not_found),
                buildStatus = BuildStatus.FAILURE
            )
        }
        queueItemId?.let { markBuildQueueItemFailed(it, text(R.string.vm_build_run_not_found_short)) }
    }

    fun loadRecentRuns() {
        val state = _uiState.value
        val username = state.user?.login ?: return
        val repoName = state.forkRepo?.name ?: return
        viewModelScope.launch {
            when (val r = github.listRecentRuns(username, repoName, perPage = 30)) {
                is Result.Success -> {
                    _uiState.update { it.copy(recentRuns = r.data) }
                    r.data.forEach { run ->
                        syncBuildQueueWithRun(run, run.toBuildStatus())
                        if (_uiState.value.activeBuildRuns.any { it.id == run.id }) {
                            _uiState.update {
                                it.withBuildRunDisplay(
                                    run = run,
                                    status = run.toBuildStatus(),
                                    progress = it.buildProgressByRunId[run.id] ?: BuildProgressUtils.defaultFor(run)
                                )
                            }
                        }
                    }
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
        buildWorkflowFiles.forEach { workflowFile ->
            val workflowId = when (val wf = github.getWorkflowId(owner, repoName, workflowFile)) {
                is Result.Success -> wf.data
                else -> return@forEach
            }
            val workflowRuns = recentRuns.filter { it.workflowId == workflowId }.ifEmpty {
                when (val customRuns = github.listRecentRuns(owner, repoName, perPage = 10, workflowId = workflowId)) {
                    is Result.Success -> customRuns.data
                    else -> emptyList()
                }
            }
            workflowRuns
                .filter { it.isActiveBuildRun() }
                .forEach { run ->
                    if (run.id !in monitoredRunIds || _uiState.value.activeBuildRuns.none { it.id == run.id }) {
                        monitorExistingBuildRun(owner, repoName, run)
                    }
                }
        }
    }

    private suspend fun monitorExistingBuildRun(owner: String, repoName: String, run: WorkflowRun) {
        monitoredRunIds += run.id
        prefs.saveLastRunId(run.id)
        if (_uiState.value.autoDownload) {
            prefs.savePendingAutoDownloadRunId(run.id)
        }
        attachRunToActiveQueueItem(run)
        _uiState.update {
            it.withBuildRunDisplay(
                run = run,
                status = run.toBuildStatus(),
                progress = BuildProgress(
                    percent = if (run.status == "in_progress") 5 else 0,
                    currentStep = if (run.status == "in_progress") {
                        text(R.string.vm_workflow_adopted_running)
                    } else {
                        text(R.string.vm_workflow_waiting_runner)
                    },
                    completedSteps = 0,
                    totalSteps = 1
                )
            )
        }
        BuildMonitorService.startMonitoring(getApplication(), owner, repoName, run.id)
    }

    fun removeBuildQueueItem(itemId: String) {
        val cleanId = itemId.trim()
        if (cleanId.isBlank()) return
        val item = _uiState.value.buildQueue.firstOrNull { it.id == cleanId } ?: return
        if (item.status in setOf(BuildQueueItemStatus.DISPATCHING, BuildQueueItemStatus.RUNNING) && item.runId > 0L) {
            cancelWorkflowRun(item.runId)
            return
        }
        saveBuildQueue(_uiState.value.buildQueue.filterNot { it.id == cleanId })
    }

    fun retryBuildQueueItem(itemId: String) {
        val cleanId = itemId.trim()
        if (cleanId.isBlank()) return
        updateBuildQueueItem(cleanId) {
            it.copy(status = BuildQueueItemStatus.PENDING, runId = 0L, runNumber = 0, error = null)
        }
        processBuildQueue()
    }

    fun clearCompletedBuildQueueItems() {
        saveBuildQueue(
            _uiState.value.buildQueue.filter {
                it.status !in setOf(
                    BuildQueueItemStatus.DONE,
                    BuildQueueItemStatus.FAILED,
                    BuildQueueItemStatus.CANCELLED
                )
            }
        )
    }

    fun cancelWorkflowRun(runId: Long) {
        if (runId <= 0L || runId in _uiState.value.cancellingWorkflowRunIds) return
        val state = _uiState.value
        val owner = state.user?.login ?: return
        val repoName = state.forkRepo?.name ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    cancellingWorkflowRunIds = it.cancellingWorkflowRunIds + runId,
                    error = null
                )
            }
            when (val result = github.cancelWorkflowRun(owner, repoName, runId)) {
                is Result.Success -> {
                    syncBuildQueueWithRunId(runId, BuildQueueItemStatus.CANCELLED)
                    monitoredRunIds.remove(runId)
                    _uiState.update {
                        val affectsDisplay = it.currentRun?.id == runId || it.activeBuildRuns.any { run -> run.id == runId }
                        it.withoutActiveBuildRun(
                            runId = runId,
                            fallbackStatus = if (affectsDisplay) BuildStatus.CANCELLED else it.buildStatus,
                            fallbackProgress = if (affectsDisplay) {
                                it.buildProgress.copy(currentStep = text(R.string.vm_workflow_cancel_requested))
                            } else {
                                it.buildProgress
                            },
                            fallbackRun = it.currentRun
                        )
                    }
                    loadRecentRuns()
                    processBuildQueue()
                }
                is Result.Error -> _uiState.update { it.copy(error = text(R.string.vm_workflow_cancel_failed, result.message)) }
                Result.Loading -> {}
            }
            _uiState.update { it.copy(cancellingWorkflowRunIds = it.cancellingWorkflowRunIds - runId) }
        }
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
                        if (run != null) artifact.withRun(run) else artifact.toBuildArtifact(
                            runId,
                            text(R.string.vm_workflow_run_title, runId)
                        )
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
        startArtifactDownload(artifact, automatic = false)
    }

    fun loadPrebuiltGkiReleases(force: Boolean = false) {
        val state = _uiState.value
        if (!state.prebuiltGkiEnabled) return
        if (state.isLoadingPrebuiltGkiReleases || (!force && state.prebuiltGkiReleases.isNotEmpty())) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPrebuiltGkiReleases = true, error = null) }
            val result = github.listReleases(BuildConfig.SOURCE_REPO_OWNER, BuildConfig.SOURCE_REPO_NAME)
            if (!_uiState.value.prebuiltGkiEnabled) {
                _uiState.update { it.copy(isLoadingPrebuiltGkiReleases = false) }
                return@launch
            }
            when (result) {
                is Result.Success -> {
                    val releases = result.data
                        .filter(::isPrebuiltGkiReleaseCandidate)
                        .map(::prebuiltGkiReleaseFromGitHub)
                        .distinctBy { it.id }
                        .sortedWith(prebuiltGkiReleaseComparator())
                    val releaseIds = releases.map { it.id }.toSet()
                    _uiState.update {
                        it.copy(
                            prebuiltGkiReleases = releases,
                            isLoadingPrebuiltGkiReleases = false,
                            prebuiltGkiAssetsByReleaseId = it.prebuiltGkiAssetsByReleaseId
                                .filterKeys { id -> id in releaseIds }
                        )
                    }
                }
                is Result.Error -> _uiState.update {
                    it.copy(
                        isLoadingPrebuiltGkiReleases = false,
                        error = text(R.string.vm_prebuilt_gki_release_failed, result.message)
                    )
                }
                else -> _uiState.update { it.copy(isLoadingPrebuiltGkiReleases = false) }
            }
        }
    }

    fun loadPrebuiltGkiAssets(release: PrebuiltGkiRelease, force: Boolean = false) {
        val state = _uiState.value
        if (!state.prebuiltGkiEnabled) return
        if (release.id in state.loadingPrebuiltGkiAssetReleaseIds) return
        if (!force && state.prebuiltGkiAssetsByReleaseId.containsKey(release.id)) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    error = null,
                    loadingPrebuiltGkiAssetReleaseIds = it.loadingPrebuiltGkiAssetReleaseIds + release.id
                )
            }
            val result = if (release.apiId > 0L) {
                github.listReleaseAssets(BuildConfig.SOURCE_REPO_OWNER, BuildConfig.SOURCE_REPO_NAME, release.apiId)
            } else {
                when (val fallback = github.getReleaseByTag(
                    BuildConfig.SOURCE_REPO_OWNER,
                    BuildConfig.SOURCE_REPO_NAME,
                    release.tagName
                )) {
                    is Result.Success -> {
                        val fetched = fallback.data
                        if (fetched != null && fetched.id > 0L) {
                            github.listReleaseAssets(BuildConfig.SOURCE_REPO_OWNER, BuildConfig.SOURCE_REPO_NAME, fetched.id)
                        } else {
                            Result.Success(fetched?.assets.orEmpty())
                        }
                    }
                    is Result.Error -> fallback
                    Result.Loading -> Result.Loading
                }
            }
            if (!_uiState.value.prebuiltGkiEnabled) {
                _uiState.update {
                    it.copy(loadingPrebuiltGkiAssetReleaseIds = it.loadingPrebuiltGkiAssetReleaseIds - release.id)
                }
                return@launch
            }
            when (result) {
                is Result.Success -> {
                    val assets = prebuiltGkiAssetsFromReleaseAssets(release, result.data)
                        .filter(::isPrebuiltGkiCandidate)
                        .distinctBy { it.id }
                        .sortedWith(prebuiltGkiComparator(_uiState.value.recommendedBuildConfig))
                    _uiState.update {
                        it.copy(
                            prebuiltGkiAssetsByReleaseId = it.prebuiltGkiAssetsByReleaseId + (release.id to assets),
                            loadingPrebuiltGkiAssetReleaseIds = it.loadingPrebuiltGkiAssetReleaseIds - release.id
                        )
                    }
                }
                is Result.Error -> _uiState.update {
                    it.copy(
                        loadingPrebuiltGkiAssetReleaseIds = it.loadingPrebuiltGkiAssetReleaseIds - release.id,
                        error = text(R.string.vm_prebuilt_gki_assets_failed, release.name, result.message)
                    )
                }
                Result.Loading -> _uiState.update {
                    it.copy(loadingPrebuiltGkiAssetReleaseIds = it.loadingPrebuiltGkiAssetReleaseIds - release.id)
                }
            }
        }
    }

    fun downloadPrebuiltGki(asset: PrebuiltGkiAsset) {
        val key = DownloadUtils.prebuiltProgressKey(asset.id)
        if (key in artifactDownloadJobs) return
        artifactDownloadJobs[key] = viewModelScope.launch {
            try {
                downloadPrebuiltGkiNow(asset, key)
            } finally {
                artifactDownloadJobs.remove(key)
            }
        }
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

    fun cancelDownload(taskKey: Long) {
        artifactDownloadJobs.remove(taskKey)?.cancel()
        finishWorkflowDownloadTask(taskKey)
    }

    fun cancelAutoDownloads(runId: Long) {
        viewModelScope.launch {
            if (_uiState.value.pendingAutoDownloadRunId == runId) {
                prefs.clearPendingAutoDownloadRunId()
            }
            _uiState.value.activeDownloadTasks
                .filter { it.runId == runId && it.automatic }
                .forEach { task -> cancelDownload(task.key) }
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
                        _uiState.update { it.copy(error = text(R.string.vm_remote_workflow_delete_missing_repo)) }
                        return@launch
                    }
                    when (val result = github.deleteWorkflowRun(owner, repoName, runId)) {
                        is Result.Error -> {
                            _uiState.update { it.copy(error = text(R.string.vm_remote_workflow_delete_failed, result.message)) }
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
                    cancelDownload(artifactId)
                }
                val updatedRemote = _uiState.value.artifacts
                    .filterNot { it.runId == runId }
                    .sortedForDisplay()
                val updatedParameterSummaries = _uiState.value.buildParameterSummaries - runId

                _uiState.update { state ->
                    state.copy(
                        downloadedArtifacts = updatedDownloads,
                        artifacts = updatedRemote,
                        buildParameterSummaries = updatedParameterSummaries,
                        loadingBuildParameterRunIds = state.loadingBuildParameterRunIds - runId,
                        buildParameterErrors = state.buildParameterErrors - runId,
                        activeDownloadTasks = state.activeDownloadTasks.filterNot { it.runId == runId },
                        downloadProgress = state.downloadProgress.filterKeys { it !in removedRemoteIds },
                        recentRuns = state.recentRuns.filterNot { it.id == runId },
                        currentRun = state.currentRun?.takeUnless { it.id == runId }
                    ).withoutActiveBuildRun(
                        runId = runId,
                        fallbackStatus = if (state.currentRun?.id == runId) BuildStatus.IDLE else state.buildStatus,
                        fallbackProgress = state.buildProgress,
                        fallbackRun = state.currentRun?.takeUnless { it.id == runId }
                    ).withDownloadState()
                }
                if (_uiState.value.pendingAutoDownloadRunId == runId) {
                    prefs.clearPendingAutoDownloadRunId()
                }
                prefs.saveDownloadedArtifactsJson(gson.toJson(updatedDownloads))
                prefs.saveRemoteArtifactsJson(gson.toJson(updatedRemote))
                prefs.saveBuildParameterSummariesJson(gson.toJson(updatedParameterSummaries.values.sortedByDescending { it.runNumber }))
            } finally {
                _uiState.update {
                    if (it.deletingWorkflowRunId == runId) it.copy(deletingWorkflowRunId = null) else it
                }
            }
        }
    }

    private fun startArtifactDownload(artifact: BuildArtifact, automatic: Boolean) {
        if (artifact.id in artifactDownloadJobs) return
        artifactDownloadJobs[artifact.id] = viewModelScope.launch {
            try {
                downloadArtifactNow(artifact, automatic)
            } finally {
                artifactDownloadJobs.remove(artifact.id)
                finishWorkflowDownloadTask(artifact.id)
            }
        }
    }

    private suspend fun downloadPrebuiltGkiNow(asset: PrebuiltGkiAsset, progressKey: Long) {
        if (!_uiState.value.prebuiltGkiEnabled) return
        val token = prefs.accessToken.first()
        val downloadDirectory = prefs.downloadDirectory.first()
        _uiState.update {
            it.withDownloadState(
                error = null,
                downloadProgress = it.downloadProgress + (progressKey to 0)
            )
        }
        NotificationUtils.notifyDownloadProgress(getApplication(), 0, asset.name)
        try {
            val results = DownloadUtils.downloadDirectAsset(
                getApplication(),
                token,
                asset.browserDownloadUrl,
                asset.name,
                asset.sizeBytes,
                PREBUILT_GKI_RUN_ID,
                text(R.string.vm_prebuilt_gki_label),
                downloadDirectory
            ) { pct ->
                NotificationUtils.notifyDownloadProgress(getApplication(), pct, asset.name)
                _uiState.update { s ->
                    s.withDownloadState(downloadProgress = s.downloadProgress + (progressKey to pct))
                }
            }
            if (!_uiState.value.prebuiltGkiEnabled) {
                _uiState.update {
                    it.withDownloadState(downloadProgress = it.downloadProgress - progressKey)
                }
                return
            }
            if (results.artifacts.isNotEmpty()) {
                NotificationUtils.notifyDownloadDone(getApplication(), asset.name)
                val updated = (_uiState.value.downloadedArtifacts + results.artifacts)
                    .distinctBy { it.filePath }
                    .sortedDownloadedForDisplay()
                _uiState.update { s ->
                    s.withDownloadState(
                        error = null,
                        downloadedArtifacts = updated,
                        downloadProgress = s.downloadProgress - progressKey
                    )
                }
                prefs.saveDownloadedArtifactsJson(gson.toJson(updated))
            } else {
                finishArtifactDownloadWithError(
                    progressKey,
                    results.errorMessage ?: text(R.string.vm_prebuilt_gki_download_failed, asset.name)
                )
            }
        } catch (cancel: CancellationException) {
            NotificationUtils.cancelDownloadNotification(getApplication())
            _uiState.update {
                it.withDownloadState(downloadProgress = it.downloadProgress - progressKey)
            }
            throw cancel
        }
    }

    private suspend fun downloadArtifactNow(artifact: BuildArtifact, automatic: Boolean) {
        val token = prefs.accessToken.first()
        val downloadDirectory = prefs.downloadDirectory.first()
        if (token.isNullOrBlank()) {
            _uiState.update {
                it.withDownloadState(error = text(R.string.vm_artifact_download_login_required))
            }
            return
        }
        startWorkflowDownloadTask(artifact, automatic)
        NotificationUtils.notifyDownloadProgress(getApplication(), 0, artifact.name)
        try {
            val mirrorBaseUrl = prefs.downloadMirrorBaseUrl.first()
            val mirrorEnabled = mirrorBaseUrl.isNotBlank()
            val downloadUrl = if (mirrorEnabled) {
                monitorMirrorAndResolveDownloadUrl(artifact, mirrorBaseUrl) ?: run {
                    finishArtifactDownloadWithError(artifact.id, text(R.string.vm_mirror_prepare_failed, artifact.name))
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
                downloadUrl,
                downloadDirectory
            ) { pct ->
                val displayProgress = if (mirrorEnabled) {
                    (50 + pct / 2).coerceIn(50, 100)
                } else {
                    pct
                }
                NotificationUtils.notifyDownloadProgress(getApplication(), displayProgress, artifact.name)
                updateWorkflowDownloadProgress(artifact.id, displayProgress)
            }
            if (results.artifacts.isNotEmpty()) {
                NotificationUtils.notifyDownloadDone(getApplication(), artifact.name)
                val updated = (_uiState.value.downloadedArtifacts + results.artifacts)
                    .distinctBy { it.filePath }
                    .sortedDownloadedForDisplay()
                _uiState.update { s ->
                    s.withDownloadState(
                        error = null,
                        downloadedArtifacts = updated
                    )
                }
                prefs.saveDownloadedArtifactsJson(gson.toJson(updated))
            } else {
                finishArtifactDownloadWithError(
                    artifact.id,
                    results.errorMessage ?: text(R.string.vm_artifact_download_failed, artifact.name)
                )
            }
        } catch (cancel: CancellationException) {
            NotificationUtils.cancelDownloadNotification(getApplication())
            throw cancel
        }
    }

    private fun finishArtifactDownloadWithError(artifactId: Long, message: String) {
        _uiState.update {
            it.withDownloadState(
                error = it.error ?: message,
                downloadProgress = it.downloadProgress - artifactId
            )
        }
    }

    private fun startWorkflowDownloadTask(artifact: BuildArtifact, automatic: Boolean) {
        val task = artifact.toActiveDownloadTask(automatic = automatic)
        _uiState.update { state ->
            state.withDownloadState(
                error = null,
                activeDownloadTasks = (state.activeDownloadTasks.filterNot { it.key == task.key } + task)
                    .sortedDownloadTasks(),
                downloadProgress = state.downloadProgress + (task.key to task.progress)
            )
        }
    }

    private fun updateWorkflowDownloadProgress(taskKey: Long, progress: Int) {
        _uiState.update { state ->
            state.withDownloadState(
                activeDownloadTasks = state.activeDownloadTasks
                    .map { task ->
                        if (task.key == taskKey) task.copy(progress = progress.coerceIn(0, 100)) else task
                    }
                    .sortedDownloadTasks(),
                downloadProgress = state.downloadProgress + (taskKey to progress.coerceIn(0, 100))
            )
        }
    }

    private fun finishWorkflowDownloadTask(taskKey: Long) {
        _uiState.update { state ->
            state.withDownloadState(
                activeDownloadTasks = state.activeDownloadTasks.filterNot { it.key == taskKey },
                downloadProgress = state.downloadProgress - taskKey
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
            _uiState.update { it.copy(error = text(R.string.vm_mirror_no_artifacts, artifact.name)) }
            return null
        }

        markMirrorProgress(artifact.id, 1)
        val workflowId = when (val wf = github.getWorkflowId(username, repoName, MIRROR_WORKFLOW_FILE)) {
            is Result.Success -> wf.data
            is Result.Error -> {
                _uiState.update { it.copy(error = text(R.string.vm_mirror_workflow_missing, wf.message)) }
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
                _uiState.update { it.copy(error = text(R.string.vm_mirror_workflow_dispatch_failed, dispatch.message)) }
                return null
            }
            else -> {}
        }
        delay(5_000)
        val run = findMirrorWorkflowRun(username, repoName, workflowId, previousRunId) ?: run {
            _uiState.update { it.copy(error = text(R.string.vm_mirror_run_not_found)) }
            return null
        }
        val completed = waitForMirrorWorkflow(username, repoName, run.id, artifact.id) ?: return null
        if (completed.conclusion != "success") {
            _uiState.update { it.copy(error = text(R.string.vm_mirror_workflow_failed, completed.conclusion ?: "unknown")) }
            return null
        }
        markMirrorProgress(artifact.id, 50)
        val releaseAssetUrl = findMirrorReleaseAssetUrlWithRetry(username, repoName, artifact.runId, artifact.name) ?: run {
            _uiState.update { it.copy(error = text(R.string.vm_mirror_release_asset_missing, artifact.name)) }
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
                    _uiState.update { it.copy(error = text(R.string.vm_mirror_workflow_query_failed, run.message)) }
                    return null
                }
                else -> {}
            }
            if (attempt < MIRROR_WORKFLOW_MAX_POLLS - 1) delay(15_000)
        }
        _uiState.update { it.copy(error = text(R.string.vm_mirror_workflow_timeout)) }
        return null
    }

    private fun markMirrorProgress(artifactId: Long, progress: Int) {
        updateWorkflowDownloadProgress(artifactId, progress)
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
                _uiState.update { it.copy(error = text(R.string.vm_mirror_release_query_failed, release.message)) }
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
        targets.forEach { startArtifactDownload(it, automatic = true) }
    }

    // ── Settings ──────────────────────────────────────────────────────────

    fun setAutoDownload(v: Boolean) = viewModelScope.launch {
        prefs.setAutoDownload(v)
        if (!v) prefs.clearPendingAutoDownloadRunId()
    }
    fun setNotifyBuild(v: Boolean) = viewModelScope.launch { prefs.setNotifyBuild(v) }
    fun setThemeMode(mode: String) = viewModelScope.launch { prefs.setThemeMode(mode) }
    fun setDynamicColorEnabled(
        v: Boolean,
        snapshotThemeColorArgb: Int? = null,
        snapshotAccentColorArgb: Int? = null
    ) = viewModelScope.launch {
        prefs.setDynamicColorEnabled(v, snapshotThemeColorArgb, snapshotAccentColorArgb)
    }
    fun setCustomThemeColors(themeColorArgb: Int, accentColorArgb: Int) = viewModelScope.launch {
        prefs.setCustomThemeColors(themeColorArgb, accentColorArgb)
    }
    fun setBackgroundImageUri(uri: String?) = viewModelScope.launch { prefs.setBackgroundImageUri(uri) }
    fun setBackgroundImageEnabled(v: Boolean) = viewModelScope.launch { prefs.setBackgroundImageEnabled(v) }
    fun setUiSurfaceAlpha(alpha: Float) = viewModelScope.launch { prefs.setUiSurfaceAlpha(alpha) }
    fun acceptTerms() = viewModelScope.launch { prefs.acceptCurrentTerms() }
    fun setDownloadDirectory(path: String) = viewModelScope.launch {
        prefs.setDownloadDirectory(path)
    }
    fun setDownloadMirrorBaseUrl(url: String) = viewModelScope.launch {
        prefs.setDownloadMirrorBaseUrl(url.trim())
    }
    fun setPredictiveBackEnabled(v: Boolean) = viewModelScope.launch { prefs.setPredictiveBackEnabled(v) }
    fun setPrebuiltGkiEnabled(v: Boolean) = viewModelScope.launch {
        if (!v) {
            artifactDownloadJobs.keys.filter { it < 0L }.forEach { key ->
                artifactDownloadJobs[key]?.cancel()
                artifactDownloadJobs.remove(key)
            }
        }
        _uiState.update {
            if (v) it.copy(prebuiltGkiEnabled = true) else it.copy(
                prebuiltGkiEnabled = false,
                prebuiltGkiReleases = emptyList(),
                isLoadingPrebuiltGkiReleases = false,
                prebuiltGkiAssetsByReleaseId = emptyMap(),
                loadingPrebuiltGkiAssetReleaseIds = emptySet(),
                downloadProgress = it.downloadProgress.filterKeys { key -> key >= 0L }
            ).withDownloadState()
        }
        prefs.setPrebuiltGkiEnabled(v)
    }

    fun refreshManagerSettings(force: Boolean = false) {
        if (!force && _uiState.value.managerSettingsLoading) return
        viewModelScope.launch {
            val rootGranted = _uiState.value.rootGranted
            _uiState.update { it.copy(managerSettingsLoading = true, managerSettingsError = null) }
            val access = withContext(Dispatchers.IO) { resolveManagerAccess(rootGranted) }
            if (!access.hasNativeManagerPermission) {
                _uiState.update {
                    it.copy(
                        managerAccessState = access.toUiState(),
                        managerAccessError = managerAccessErrorMessage(access, rootGranted),
                        hasNativeManagerPermission = false,
                        managerSettingsBackend = null,
                        managerSettingsTitle = "",
                        managerSettingsItems = emptyList(),
                        managerSettingsLoading = false,
                        managerSettingsError = null,
                        managerSettingActionId = null
                    )
                }
                return@launch
            }
            val loaded = runCatching {
                withContext(Dispatchers.IO) {
                    loadManagerSettings()
                }
            }.getOrElse { error ->
                ManagerSettingsLoad(
                    error = error.message?.takeIf { it.isNotBlank() } ?: text(R.string.settings_manager_load_failed)
                )
            }
            _uiState.update {
                it.copy(
                    managerAccessState = access.toUiState(),
                    managerAccessError = null,
                    hasNativeManagerPermission = true,
                    managerSettingsBackend = loaded.backend?.trim()?.ifBlank { null },
                    managerSettingsTitle = loaded.title.trim(),
                    managerSettingsItems = sanitizeManagerSettingItems(loaded.items),
                    managerSettingsLoading = false,
                    managerSettingsError = loaded.error,
                    managerSettingActionId = null
                )
            }
        }
    }

    fun setManagerSettingChecked(settingId: String, checked: Boolean) {
        if (_uiState.value.managerSettingActionId != null) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(managerSettingActionId = settingId, managerSettingsError = null)
            }
            val rootGranted = _uiState.value.rootGranted
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val access = resolveManagerAccess(rootGranted)
                    if (!access.hasNativeManagerPermission) {
                        return@withContext RootUtils.ShellResult(false, listOf(managerAccessErrorMessage(access, rootGranted)))
                    }
                    when (settingId) {
                        MANAGER_SETTING_KERNEL_UMOUNT -> RootUtils.setKsuFeatureEnabled("kernel_umount", checked)
                        MANAGER_SETTING_SULOG -> RootUtils.setKsuFeatureEnabled("sulog", checked)
                        MANAGER_SETTING_ADB_ROOT -> RootUtils.setKsuFeatureEnabled("adb_root", checked)
                        MANAGER_SETTING_SELINUX_HIDE -> RootUtils.setKsuFeatureEnabled("selinux_hide", checked)
                        MANAGER_SETTING_WEBVIEW_DEBUG -> {
                            prefs.setWebViewDebugEnabled(checked)
                            RootUtils.ShellResult(true, emptyList())
                        }
                        MANAGER_SETTING_DEFAULT_UMOUNT -> {
                            val ok = RootUtils.setDefaultUmountModules(checked)
                            RootUtils.ShellResult(ok, if (ok) emptyList() else listOf(text(R.string.vm_save_failed)))
                        }
                        else -> RootUtils.ShellResult(false, listOf(text(R.string.vm_unsupported_setting)))
                    }
                }
            }.getOrElse { error ->
                RootUtils.ShellResult(false, listOf(error.message ?: text(R.string.vm_operation_failed)))
            }
            if (result.success) {
                refreshManagerSettings(force = true)
            } else {
                _uiState.update {
                    it.copy(
                        managerSettingActionId = null,
                        managerSettingsError = result.output.lastOrNull()?.takeIf { line -> line.isNotBlank() }
                            ?: text(R.string.settings_operation_incomplete)
                    )
                }
            }
        }
    }

    fun setManagerSettingMode(settingId: String, selectedIndex: Int) {
        if (_uiState.value.managerSettingActionId != null) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(managerSettingActionId = settingId, managerSettingsError = null)
            }
            val rootGranted = _uiState.value.rootGranted
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val access = resolveManagerAccess(rootGranted)
                    if (!access.hasNativeManagerPermission) {
                        return@withContext RootUtils.ShellResult(false, listOf(managerAccessErrorMessage(access, rootGranted)))
                    }
                    when (settingId) {
                        MANAGER_SETTING_SU_COMPAT -> RootUtils.setSuCompatMode(selectedIndex.coerceIn(0, 2))
                        else -> RootUtils.ShellResult(false, listOf(text(R.string.vm_unsupported_setting)))
                    }
                }
            }.getOrElse { error ->
                RootUtils.ShellResult(false, listOf(error.message ?: text(R.string.vm_operation_failed)))
            }
            if (result.success) {
                refreshManagerSettings(force = true)
            } else {
                _uiState.update {
                    it.copy(
                        managerSettingActionId = null,
                        managerSettingsError = result.output.lastOrNull()?.takeIf { line -> line.isNotBlank() }
                            ?: text(R.string.settings_operation_incomplete)
                    )
                }
            }
        }
    }

    fun refreshManagerTools(force: Boolean = false) {
        if (!force && _uiState.value.managerToolsLoading) return
        viewModelScope.launch {
            val rootGranted = _uiState.value.rootGranted
            _uiState.update { it.copy(managerToolsLoading = true, managerToolsError = null) }
            val access = withContext(Dispatchers.IO) { resolveManagerAccess(rootGranted) }
            if (!access.hasNativeManagerPermission) {
                _uiState.update {
                    it.copy(
                        managerAccessState = access.toUiState(),
                        managerAccessError = managerAccessErrorMessage(access, rootGranted),
                        hasNativeManagerPermission = false,
                        managerToolsLoading = false,
                        managerToolsError = managerAccessErrorMessage(access, rootGranted),
                        managerToolActionId = null
                    )
                }
                return@launch
            }
            val modeResult = withContext(Dispatchers.IO) { RootUtils.readSelinuxMode() }
            val pathsResult = withContext(Dispatchers.IO) { RootUtils.listUmountPaths() }
            val mode = modeResult.output.lastOrNull { it.isNotBlank() }?.trim().orEmpty()
            _uiState.update {
                it.copy(
                    managerAccessState = access.toUiState(),
                    managerAccessError = null,
                    hasNativeManagerPermission = true,
                    managerToolsLoading = false,
                    selinuxModeText = mode.ifBlank { text(R.string.settings_unknown) },
                    selinuxEnforcing = mode.equals("Enforcing", ignoreCase = true),
                    umountPaths = if (pathsResult.success) {
                        pathsResult.output.map { line -> line.trim() }.filter { line -> line.isNotBlank() }
                    } else {
                        emptyList()
                    },
                    managerToolsError = when {
                        modeResult.success -> null
                        else -> modeResult.output.lastOrNull() ?: text(R.string.vm_tool_status_read_failed)
                    },
                    managerToolActionId = null
                )
            }
        }
    }

    fun setSelinuxEnforcing(enforcing: Boolean) {
        if (_uiState.value.managerToolActionId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(managerToolActionId = MANAGER_TOOL_SELINUX_MODE, managerToolsError = null) }
            val rootGranted = _uiState.value.rootGranted
            val result = withContext(Dispatchers.IO) {
                val access = resolveManagerAccess(rootGranted)
                if (!access.hasNativeManagerPermission) {
                    RootUtils.ShellResult(false, listOf(managerAccessErrorMessage(access, rootGranted)))
                } else {
                    RootUtils.setSelinuxEnforcing(enforcing)
                }
            }
            if (result.success) {
                refreshManagerTools(force = true)
            } else {
                _uiState.update {
                    it.copy(
                        managerToolActionId = null,
                        managerToolsError = result.output.lastOrNull()?.takeIf { line -> line.isNotBlank() }
                            ?: text(R.string.vm_selinux_toggle_failed)
                    )
                }
            }
        }
    }

    fun backupRootGrantAllowlist(uri: Uri) {
        if (_uiState.value.managerToolActionId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(managerToolActionId = MANAGER_TOOL_BACKUP_ALLOWLIST, managerToolsError = null) }
            val rootGranted = _uiState.value.rootGranted
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val access = resolveManagerAccess(rootGranted)
                    if (!access.hasNativeManagerPermission) {
                        error(managerAccessErrorMessage(access, rootGranted))
                    }
                    val profiles = RootUtils.listRootGrantApps(getApplication())
                        .filter { app -> app.profile.allowSu || !app.profile.rootUseDefault }
                        .map { app -> app.profile }
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(gson.toJson(profiles).toByteArray(StandardCharsets.UTF_8))
                    } ?: error(text(R.string.vm_export_open_failed))
                    RootUtils.ShellResult(true, listOf(text(R.string.vm_allowlist_exported, profiles.size)))
                }.getOrElse { error ->
                    RootUtils.ShellResult(false, listOf(error.message ?: text(R.string.vm_export_failed)))
                }
            }
            _uiState.update {
                it.copy(
                    managerToolActionId = null,
                    managerToolsError = if (result.success) null else result.output.lastOrNull() ?: text(R.string.vm_export_failed)
                )
            }
        }
    }

    fun restoreRootGrantAllowlist(uri: Uri) {
        if (_uiState.value.managerToolActionId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(managerToolActionId = MANAGER_TOOL_RESTORE_ALLOWLIST, managerToolsError = null) }
            val rootGranted = _uiState.value.rootGranted
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val access = resolveManagerAccess(rootGranted)
                    if (!access.hasNativeManagerPermission) {
                        error(managerAccessErrorMessage(access, rootGranted))
                    }
                    val json = getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes().toString(StandardCharsets.UTF_8)
                    } ?: error(text(R.string.vm_backup_read_failed))
                    val type = object : TypeToken<List<RootGrantProfile>>() {}.type
                    val profiles: List<RootGrantProfile> = gson.fromJson(json, type) ?: emptyList()
                    var restored = 0
                    profiles.forEach { profile ->
                        if (profile.name.isNotBlank() && RootUtils.setRootGrantProfile(profile)) restored++
                    }
                    if (restored == profiles.size) {
                        RootUtils.ShellResult(true, listOf(text(R.string.vm_allowlist_restored, restored)))
                    } else {
                        RootUtils.ShellResult(false, listOf(text(R.string.vm_allowlist_restored_partial, restored, profiles.size)))
                    }
                }.getOrElse { error ->
                    RootUtils.ShellResult(false, listOf(error.message ?: text(R.string.vm_restore_failed)))
                }
            }
            _uiState.update {
                it.copy(
                    managerToolActionId = null,
                    managerToolsError = if (result.success) null else result.output.lastOrNull() ?: text(R.string.vm_restore_failed)
                )
            }
            if (result.success) refreshRootGrantApps(force = true)
        }
    }

    fun refreshAppProfileTemplates() {
        if (_uiState.value.appProfileTemplatesLoading) return
        viewModelScope.launch {
            val rootGranted = _uiState.value.rootGranted
            _uiState.update { it.copy(appProfileTemplatesLoading = true, appProfileTemplatesError = null) }
            val access = withContext(Dispatchers.IO) { resolveManagerAccess(rootGranted) }
            if (!access.hasNativeManagerPermission) {
                _uiState.update {
                    it.copy(
                        managerAccessState = access.toUiState(),
                        managerAccessError = managerAccessErrorMessage(access, rootGranted),
                        hasNativeManagerPermission = false,
                        appProfileTemplates = emptyList(),
                        selectedAppProfileTemplateId = null,
                        selectedAppProfileTemplateContent = "",
                        appProfileTemplatesLoading = false,
                        appProfileTemplatesError = managerAccessErrorMessage(access, rootGranted)
                    )
                }
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                RootUtils.listAppProfileTemplates()
            }
            _uiState.update {
                if (result.success) {
                    it.copy(
                        managerAccessState = access.toUiState(),
                        managerAccessError = null,
                        hasNativeManagerPermission = true,
                        appProfileTemplates = result.output
                            .map { id -> id.trim() }
                            .filter { id -> id.isNotBlank() }
                            .distinct()
                            .sorted()
                            .map { id -> AppProfileTemplateItem(id = id) },
                        appProfileTemplatesLoading = false,
                        appProfileTemplatesError = null
                    )
                } else {
                    it.copy(
                        managerAccessState = access.toUiState(),
                        managerAccessError = null,
                        hasNativeManagerPermission = true,
                        appProfileTemplatesLoading = false,
                        appProfileTemplatesError = result.output.lastOrNull() ?: text(R.string.settings_manager_load_failed)
                    )
                }
            }
        }
    }

    fun selectAppProfileTemplate(id: String?) {
        val cleanId = id?.trim().orEmpty()
        if (cleanId.isBlank()) {
            _uiState.update {
                it.copy(
                    selectedAppProfileTemplateId = null,
                    selectedAppProfileTemplateContent = "",
                    appProfileTemplatesError = null
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedAppProfileTemplateId = cleanId,
                    selectedAppProfileTemplateContent = "",
                    appProfileTemplatesError = null
                )
            }
            val rootGranted = _uiState.value.rootGranted
            val result = withContext(Dispatchers.IO) {
                val access = resolveManagerAccess(rootGranted)
                if (!access.hasNativeManagerPermission) {
                    RootUtils.ShellResult(false, listOf(managerAccessErrorMessage(access, rootGranted)))
                } else {
                    RootUtils.readAppProfileTemplate(cleanId)
                }
            }
            _uiState.update {
                if (result.success) {
                    it.copy(
                        selectedAppProfileTemplateContent = result.output.joinToString("\n"),
                        appProfileTemplatesError = null
                    )
                } else {
                    it.copy(appProfileTemplatesError = result.output.lastOrNull() ?: text(R.string.vm_template_read_failed))
                }
            }
        }
    }

    fun saveAppProfileTemplate(id: String, content: String) {
        val cleanId = id.trim()
        if (cleanId.isBlank()) {
            _uiState.update { it.copy(appProfileTemplatesError = text(R.string.vm_template_name_empty)) }
            return
        }
        if (_uiState.value.appProfileTemplateSaving) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(appProfileTemplateSaving = true, appProfileTemplatesError = null)
            }
            val rootGranted = _uiState.value.rootGranted
            val result = withContext(Dispatchers.IO) {
                val access = resolveManagerAccess(rootGranted)
                if (!access.hasNativeManagerPermission) {
                    RootUtils.ShellResult(false, listOf(managerAccessErrorMessage(access, rootGranted)))
                } else {
                    RootUtils.writeAppProfileTemplate(cleanId, content)
                }
            }
            _uiState.update {
                it.copy(
                    appProfileTemplateSaving = false,
                    selectedAppProfileTemplateId = if (result.success) cleanId else it.selectedAppProfileTemplateId,
                    selectedAppProfileTemplateContent = if (result.success) content else it.selectedAppProfileTemplateContent,
                    appProfileTemplatesError = if (result.success) null else result.output.lastOrNull() ?: text(R.string.vm_template_save_failed)
                )
            }
            if (result.success) refreshAppProfileTemplates()
        }
    }

    fun deleteAppProfileTemplate(id: String) {
        val cleanId = id.trim()
        if (cleanId.isBlank() || _uiState.value.appProfileTemplateSaving) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(appProfileTemplateSaving = true, appProfileTemplatesError = null)
            }
            val rootGranted = _uiState.value.rootGranted
            val result = withContext(Dispatchers.IO) {
                val access = resolveManagerAccess(rootGranted)
                if (!access.hasNativeManagerPermission) {
                    RootUtils.ShellResult(false, listOf(managerAccessErrorMessage(access, rootGranted)))
                } else {
                    RootUtils.deleteAppProfileTemplate(cleanId)
                }
            }
            _uiState.update {
                it.copy(
                    appProfileTemplateSaving = false,
                    selectedAppProfileTemplateId = if (result.success) null else it.selectedAppProfileTemplateId,
                    selectedAppProfileTemplateContent = if (result.success) "" else it.selectedAppProfileTemplateContent,
                    appProfileTemplatesError = if (result.success) null else result.output.lastOrNull() ?: text(R.string.vm_template_delete_failed)
                )
            }
            if (result.success) refreshAppProfileTemplates()
        }
    }

    private fun loadManagerSettings(): ManagerSettingsLoad =
        runCatching {
            if (!RootUtils.isNativeManagerActive()) {
                return@runCatching ManagerSettingsLoad()
            }
            val snapshot = RootUtils.readManagerRuntimeSnapshot()
            val manager = snapshot.manager.normalizedForManagerSettings()
            if (!manager.active) {
                ManagerSettingsLoad()
            } else {
                when {
                    manager.isReSukiSu() -> ManagerSettingsLoad(
                        backend = "resukisu",
                        title = "ReSukiSU",
                        items = buildReSukiSuSettings()
                    )
                    manager.isSukiSu() -> ManagerSettingsLoad(
                        backend = "sukisu",
                        title = "SukiSU",
                        items = buildSukiSuSettings()
                    )
                    manager.isOfficialKernelSu() -> ManagerSettingsLoad(
                        backend = "kernelsu",
                        title = "KernelSU",
                        items = buildOfficialKernelSuSettings()
                    )
                    else -> ManagerSettingsLoad(
                        backend = manager.backend.takeIf { it.isNotBlank() },
                        title = managerSettingsTitle(manager),
                        error = buildUnknownManagerSettingsError(manager)
                    )
                }
            }
        }.getOrElse { error ->
            ManagerSettingsLoad(
                error = error.message?.takeIf { it.isNotBlank() } ?: text(R.string.settings_manager_load_failed)
            )
        }

    private fun buildReSukiSuSettings(): List<ManagerSettingItem> {
        val suCompat = RootUtils.readKsuFeature("su_compat")
        val kernelUmount = RootUtils.readKsuFeature("kernel_umount")
        val kpmAvailable = RootUtils.isKpmAvailable()
        val sulog = RootUtils.readKsuFeature("sulog")
        val adbRoot = RootUtils.readKsuFeature("adb_root")
        val selinuxHide = RootUtils.readKsuFeature("selinux_hide")
        val nativeProfileAvailable = RootUtils.isNativeManagerActive()
        val suCurrentEnabled = suCompat.value != 0L
        val suCompatMode = when {
            suCompat.configValue == 0L -> 2
            !suCurrentEnabled -> 1
            else -> 0
        }
        return buildList {
            add(
                ManagerSettingItem(
                    id = MANAGER_SETTING_APP_PROFILE_TEMPLATES,
                    title = text(R.string.settings_app_profile_templates),
                    subtitle = text(R.string.settings_app_profile_templates_desc),
                    kind = ManagerSettingKind.NAVIGATION
                )
            )
            add(
                ManagerSettingItem(
                    id = MANAGER_SETTING_SU_COMPAT,
                    title = text(R.string.vm_setting_su_compat_title),
                    subtitle = featureSubtitle(suCompat, text(R.string.vm_setting_su_compat_desc), "ReSukiSU"),
                    kind = ManagerSettingKind.MODE,
                    selectedIndex = suCompatMode,
                    options = managerSuCompatOptions(),
                    enabled = suCompat.support == RootUtils.KsuFeatureSupport.SUPPORTED,
                    status = suCompat.toManagerSettingStatus()
                )
            )
            add(
                ManagerSettingItem(
                    id = MANAGER_SETTING_KERNEL_UMOUNT,
                    title = text(R.string.vm_setting_kernel_umount_title),
                    subtitle = featureSubtitle(kernelUmount, text(R.string.vm_setting_kernel_umount_desc), "ReSukiSU"),
                    checked = kernelUmount.value != 0L,
                    enabled = kernelUmount.support == RootUtils.KsuFeatureSupport.SUPPORTED,
                    status = kernelUmount.toManagerSettingStatus()
                )
            )
            if (kpmAvailable) {
                add(
                    ManagerSettingItem(
                        id = MANAGER_SETTING_KPM,
                        title = "KPM",
                        subtitle = text(R.string.vm_setting_kpm_desc),
                        kind = ManagerSettingKind.NAVIGATION
                    )
                )
            }
            if (selinuxHide.support == RootUtils.KsuFeatureSupport.SUPPORTED) {
                add(
                    ManagerSettingItem(
                        id = MANAGER_SETTING_SELINUX_HIDE,
                        title = text(R.string.vm_setting_selinux_hide_title),
                        subtitle = featureSubtitle(selinuxHide, text(R.string.vm_setting_selinux_hide_desc), "ReSukiSU"),
                        checked = selinuxHide.value != 0L,
                        enabled = true,
                        status = selinuxHide.toManagerSettingStatus()
                    )
                )
            }
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                add(
                    ManagerSettingItem(
                        id = MANAGER_SETTING_ADB_ROOT,
                        title = "ADB Root",
                        subtitle = featureSubtitle(adbRoot, text(R.string.vm_setting_adb_root_desc), "ReSukiSU"),
                        checked = (adbRoot.configValue ?: adbRoot.value ?: 0L) != 0L,
                        enabled = adbRoot.support == RootUtils.KsuFeatureSupport.SUPPORTED,
                        status = adbRoot.toManagerSettingStatus()
                    )
                )
            }
            add(
                ManagerSettingItem(
                    id = MANAGER_SETTING_SULOG,
                    title = text(R.string.vm_setting_sulog_title),
                    subtitle = featureSubtitle(sulog, text(R.string.vm_setting_sulog_desc), "ReSukiSU"),
                    checked = sulog.value != 0L,
                    enabled = sulog.support == RootUtils.KsuFeatureSupport.SUPPORTED,
                    status = sulog.toManagerSettingStatus()
                )
            )
            add(
                ManagerSettingItem(
                    id = MANAGER_SETTING_DEFAULT_UMOUNT,
                    title = text(R.string.vm_setting_default_umount_title),
                    subtitle = if (nativeProfileAvailable) {
                        text(R.string.vm_setting_default_umount_desc)
                    } else {
                        text(R.string.vm_setting_native_manager_required)
                    },
                    checked = nativeProfileAvailable && RootUtils.isDefaultUmountModules(),
                    enabled = nativeProfileAvailable
                )
            )
        }
    }

    private fun buildOfficialKernelSuSettings(): List<ManagerSettingItem> =
        buildKernelSuFamilySettings(
            backendTitle = "KernelSU",
            includeTools = false,
            includeKpm = true,
            includeSelinuxHide = true,
            includeSulog = true,
            includeAdbRoot = true,
            includeWebViewDebug = false,
            kernelUmountTitle = text(R.string.vm_setting_kernel_umount_kernel_title),
            suLogTitle = "SU Log"
        )

    private fun buildSukiSuSettings(): List<ManagerSettingItem> =
        buildKernelSuFamilySettings(
            backendTitle = "SukiSU",
            includeTools = true,
            includeKpm = true,
            includeSelinuxHide = true,
            includeSulog = false,
            includeAdbRoot = false,
            includeWebViewDebug = true,
            kernelUmountTitle = text(R.string.vm_setting_kernel_umount_title),
            suLogTitle = "SU Log"
        )

    private fun buildKernelSuFamilySettings(
        backendTitle: String,
        includeTools: Boolean,
        includeKpm: Boolean,
        includeSelinuxHide: Boolean,
        includeSulog: Boolean,
        includeAdbRoot: Boolean,
        includeWebViewDebug: Boolean,
        kernelUmountTitle: String,
        suLogTitle: String
    ): List<ManagerSettingItem> {
        val suCompat = RootUtils.readKsuFeature("su_compat")
        val kernelUmount = RootUtils.readKsuFeature("kernel_umount")
        val kpmAvailable = includeKpm && RootUtils.isKpmAvailable()
        val sulog = RootUtils.readKsuFeature("sulog")
        val adbRoot = RootUtils.readKsuFeature("adb_root")
        val selinuxHide = RootUtils.readKsuFeature("selinux_hide")
        val nativeProfileAvailable = RootUtils.isNativeManagerActive()
        val suCurrentEnabled = suCompat.value != 0L
        val suCompatMode = when {
            suCompat.configValue == 0L -> 2
            !suCurrentEnabled -> 1
            else -> 0
        }
        return buildList {
            add(
                ManagerSettingItem(
                    id = MANAGER_SETTING_APP_PROFILE_TEMPLATES,
                    title = text(R.string.settings_app_profile_templates),
                    subtitle = text(R.string.settings_app_profile_templates_full_desc),
                    kind = ManagerSettingKind.NAVIGATION
                )
            )
            if (includeTools) {
                add(
                    ManagerSettingItem(
                        id = MANAGER_SETTING_TOOLS,
                        title = text(R.string.settings_tools),
                        subtitle = text(R.string.settings_tools_desc),
                        kind = ManagerSettingKind.NAVIGATION
                    )
                )
            }
            if (kpmAvailable) {
                add(
                    ManagerSettingItem(
                        id = MANAGER_SETTING_KPM,
                        title = "KPM",
                        subtitle = text(R.string.vm_setting_kpm_desc),
                        kind = ManagerSettingKind.NAVIGATION
                    )
                )
            }
            add(
                ManagerSettingItem(
                    id = MANAGER_SETTING_SU_COMPAT,
                    title = text(R.string.vm_setting_su_compat_title),
                    subtitle = featureSubtitle(suCompat, text(R.string.vm_setting_su_compat_desc), backendTitle),
                    kind = ManagerSettingKind.MODE,
                    selectedIndex = suCompatMode,
                    options = managerSuCompatOptions(),
                    enabled = suCompat.support == RootUtils.KsuFeatureSupport.SUPPORTED,
                    status = suCompat.toManagerSettingStatus()
                )
            )
            add(
                ManagerSettingItem(
                    id = MANAGER_SETTING_KERNEL_UMOUNT,
                    title = kernelUmountTitle,
                    subtitle = featureSubtitle(kernelUmount, text(R.string.vm_setting_kernel_umount_desc), backendTitle),
                    checked = kernelUmount.value != 0L,
                    enabled = kernelUmount.support == RootUtils.KsuFeatureSupport.SUPPORTED,
                    status = kernelUmount.toManagerSettingStatus()
                )
            )
            if (includeSelinuxHide && selinuxHide.support == RootUtils.KsuFeatureSupport.SUPPORTED) {
                add(
                    ManagerSettingItem(
                        id = MANAGER_SETTING_SELINUX_HIDE,
                        title = text(R.string.vm_setting_selinux_hide_title),
                        subtitle = featureSubtitle(selinuxHide, text(R.string.vm_setting_selinux_hide_desc), backendTitle),
                        checked = selinuxHide.value != 0L,
                        enabled = true,
                        status = selinuxHide.toManagerSettingStatus()
                    )
                )
            }
            if (includeSulog) {
                add(
                    ManagerSettingItem(
                        id = MANAGER_SETTING_SULOG,
                        title = suLogTitle,
                        subtitle = featureSubtitle(sulog, text(R.string.vm_setting_sulog_desc), backendTitle),
                        checked = sulog.value != 0L,
                        enabled = sulog.support == RootUtils.KsuFeatureSupport.SUPPORTED,
                        status = sulog.toManagerSettingStatus()
                    )
                )
            }
            if (includeAdbRoot && Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                add(
                    ManagerSettingItem(
                        id = MANAGER_SETTING_ADB_ROOT,
                        title = "ADB Root",
                        subtitle = featureSubtitle(adbRoot, text(R.string.vm_setting_adb_root_desc), backendTitle),
                        checked = (adbRoot.configValue ?: adbRoot.value ?: 0L) != 0L,
                        enabled = adbRoot.support == RootUtils.KsuFeatureSupport.SUPPORTED,
                        status = adbRoot.toManagerSettingStatus()
                    )
                )
            }
            add(
                ManagerSettingItem(
                    id = MANAGER_SETTING_DEFAULT_UMOUNT,
                    title = text(R.string.vm_setting_default_umount_title),
                    subtitle = if (nativeProfileAvailable) {
                        text(R.string.vm_setting_default_umount_desc_quoted)
                    } else {
                        text(R.string.vm_setting_native_manager_required)
                    },
                    checked = nativeProfileAvailable && RootUtils.isDefaultUmountModules(),
                    enabled = nativeProfileAvailable
                )
            )
            if (includeWebViewDebug) {
                add(
                    ManagerSettingItem(
                        id = MANAGER_SETTING_WEBVIEW_DEBUG,
                        title = text(R.string.vm_setting_webview_debug_title),
                        subtitle = text(R.string.vm_setting_webview_debug_desc),
                        checked = _uiState.value.webViewDebugEnabled
                    )
                )
            }
        }
    }

    private fun RootUtils.ManagerRuntimeProbe.isReSukiSu(): Boolean {
        val text = listOf(displayName, variant, version).joinToString(" ").lowercase()
        return "resukisu" in text
    }

    private fun RootUtils.ManagerRuntimeProbe.isSukiSu(): Boolean {
        val text = listOf(displayName, variant, version).joinToString(" ").lowercase()
        return "sukisu" in text && "resukisu" !in text
    }

    private fun RootUtils.ManagerRuntimeProbe.isOfficialKernelSu(): Boolean {
        val text = listOf(displayName, variant, version).joinToString(" ").lowercase()
        return "kernelsu" in text ||
            "official" in text ||
            (backend == "native" && "native_manager" in capabilities) ||
            (backend == "ksud" && capabilities.any { it == "features" || it == "module_control" || it == "modules" })
    }

    private fun RootUtils.ManagerRuntimeProbe.normalizedForManagerSettings(): RootUtils.ManagerRuntimeProbe =
        copy(
            displayName = displayName.trim(),
            variant = variant.trim(),
            backend = backend.trim().lowercase(),
            version = version.trim(),
            workMode = workMode.trim(),
            capabilities = capabilities.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            diagnostics = diagnostics.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        )

    private fun managerSuCompatOptions(): List<String> = listOf(
        text(R.string.vm_setting_option_default),
        text(R.string.vm_setting_option_temp_off),
        text(R.string.vm_setting_option_perm_off)
    )

    private fun sanitizeManagerSettingItems(items: List<ManagerSettingItem>): List<ManagerSettingItem> =
        items.map { item ->
            val options = item.options
                .map { it.trim() }
                .filter { it.isNotBlank() }
            when (item.kind) {
                ManagerSettingKind.MODE -> {
                    val hasOptions = options.isNotEmpty()
                    item.copy(
                        title = item.title.ifBlank { text(R.string.vm_setting_unnamed) },
                        subtitle = item.subtitle.trim(),
                        options = options,
                        selectedIndex = if (hasOptions) {
                            item.selectedIndex.coerceIn(0, options.lastIndex)
                        } else {
                            0
                        },
                        enabled = item.enabled && hasOptions
                    )
                }
                else -> item.copy(
                    title = item.title.ifBlank { text(R.string.vm_setting_unnamed) },
                    subtitle = item.subtitle.trim(),
                    options = options
                )
            }
        }

    private fun managerSettingsTitle(manager: RootUtils.ManagerRuntimeProbe): String =
        manager.displayName
            .ifBlank { manager.variant }
            .ifBlank { text(R.string.settings_manager_settings) }

    private fun buildUnknownManagerSettingsError(manager: RootUtils.ManagerRuntimeProbe): String {
        val detail = manager.diagnostics.firstOrNull { it.isNotBlank() }
        val base = text(R.string.vm_unknown_manager_settings_error)
        return if (detail == null) base else "$base $detail"
    }

    private fun featureSubtitle(feature: RootUtils.KsuFeatureState, normal: String, backendTitle: String): String =
        when (feature.support) {
            RootUtils.KsuFeatureSupport.UNSUPPORTED -> text(R.string.vm_feature_unsupported, backendTitle)
            RootUtils.KsuFeatureSupport.MANAGED -> text(R.string.vm_feature_managed)
            RootUtils.KsuFeatureSupport.SUPPORTED -> normal
        }

    private fun RootUtils.KsuFeatureState.toManagerSettingStatus(): ManagerSettingStatus =
        when (support) {
            RootUtils.KsuFeatureSupport.SUPPORTED -> ManagerSettingStatus.SUPPORTED
            RootUtils.KsuFeatureSupport.UNSUPPORTED -> ManagerSettingStatus.UNSUPPORTED
            RootUtils.KsuFeatureSupport.MANAGED -> ManagerSettingStatus.MANAGED
        }

    fun updateBuildConfig(config: KernelBuildConfig) {
        val normalized = KernelSupport.normalize(config)
        hasSavedBuildConfig = true
        _uiState.update { it.copy(buildConfig = normalized) }
        viewModelScope.launch { prefs.saveBuildConfigJson(gson.toJson(normalized)) }
    }

    fun suggestedBuildPlanName(config: KernelBuildConfig = _uiState.value.buildConfig): String =
        defaultBuildPlanName(KernelSupport.normalize(config))

    fun saveCurrentBuildPlan(name: String) {
        val normalized = KernelSupport.normalize(_uiState.value.buildConfig)
        val now = System.currentTimeMillis()
        val plan = BuildPlan(
            id = UUID.randomUUID().toString(),
            name = sanitizeBuildPlanName(name, normalized),
            config = normalized,
            createdAt = now,
            updatedAt = now
        )
        saveBuildPlans((_uiState.value.buildPlans + plan).sortedByDescending { it.updatedAt })
    }

    fun applyBuildPlan(plan: BuildPlan) {
        updateBuildConfig(plan.config)
    }

    fun deleteBuildPlan(id: String) {
        saveBuildPlans(_uiState.value.buildPlans.filterNot { it.id == id })
    }

    fun renameBuildPlan(id: String, name: String) {
        val now = System.currentTimeMillis()
        val renamed = _uiState.value.buildPlans.map { plan ->
            if (plan.id == id) {
                plan.copy(
                    name = sanitizeBuildPlanName(name, plan.config),
                    updatedAt = now
                )
            } else {
                plan
            }
        }
        saveBuildPlans(renamed.sortedByDescending { it.updatedAt })
    }

    fun shareBuildPlanCode(
        config: KernelBuildConfig,
        name: String,
        scope: BuildPlanShareScope
    ): String {
        val normalized = KernelSupport.normalize(config)
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
            encodeBuildPlanPayload(
                config = normalized,
                name = sanitizeBuildPlanName(name, normalized),
                scope = scope,
                messages = buildPlanCodecMessages()
            )
        )
        return "$BUILD_PLAN_CODE_PREFIX$payload"
    }

    fun parseBuildPlanCode(
        code: String,
        baseConfig: KernelBuildConfig = _uiState.value.buildConfig
    ): BuildPlanImportPreview {
        val compact = code.trim().replace(Regex("\\s+"), "")
        require(!compact.startsWith(BUILD_PLAN_LEGACY_CODE_PREFIX)) {
            text(R.string.vm_plan_legacy_too_long)
        }
        require(compact.startsWith(BUILD_PLAN_CODE_PREFIX)) { text(R.string.vm_plan_bad_format) }
        val payload = compact.removePrefix(BUILD_PLAN_CODE_PREFIX)
        require(payload.isNotBlank()) { text(R.string.vm_plan_empty) }
        val decoded = decodeBuildPlanPayload(
            bytes = Base64.getUrlDecoder().decode(padBase64Url(payload)),
            baseConfig = KernelSupport.normalize(baseConfig),
            messages = buildPlanCodecMessages()
        )
        val now = System.currentTimeMillis()
        return BuildPlanImportPreview(
            plan = BuildPlan(
                id = UUID.randomUUID().toString(),
                name = sanitizeBuildPlanName(decoded.name, decoded.config),
                config = decoded.config,
                createdAt = now,
                updatedAt = now
            ),
            scope = decoded.scope
        )
    }

    fun importBuildPlanToLibrary(preview: BuildPlanImportPreview) {
        val now = System.currentTimeMillis()
        val normalized = KernelSupport.normalize(preview.plan.config)
        val stored = preview.plan.copy(
            id = UUID.randomUUID().toString(),
            name = sanitizeBuildPlanName(preview.plan.name, normalized),
            config = normalized,
            createdAt = now,
            updatedAt = now
        )
        saveBuildPlans((_uiState.value.buildPlans + stored).sortedByDescending { it.updatedAt })
    }

    fun importBuildPlanToCurrentConfig(preview: BuildPlanImportPreview) {
        updateBuildConfig(preview.plan.config)
    }

    fun addBuildModuleRepository(url: String) {
        val cleanUrl = normalizeModuleCatalogUrl(url)
        if (cleanUrl.isBlank()) {
            _uiState.update { it.copy(error = text(R.string.vm_module_repo_url_empty)) }
            return
        }

        val current = _uiState.value.buildModuleRepositories
        val existing = current.firstOrNull { it.url.equals(cleanUrl, ignoreCase = true) }
        if (existing != null) {
            refreshBuildModuleRepository(existing.id)
            return
        }

        val repository = ModuleCatalogRepository(
            id = UUID.randomUUID().toString(),
            url = cleanUrl,
            name = cleanUrl.moduleCatalogFallbackName(localizedBuildModuleRepoTitle())
        )
        saveBuildModuleRepositories(current + repository)
        refreshBuildModuleRepository(repository.id)
    }

    fun deleteBuildModuleRepository(id: String) {
        saveBuildModuleRepositories(_uiState.value.buildModuleRepositories.filterNot { it.id == id })
    }

    fun refreshBuildModuleRepository(id: String) {
        val repository = _uiState.value.buildModuleRepositories.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(refreshingBuildModuleRepositoryIds = it.refreshingBuildModuleRepositoryIds + id)
            }
            when (val result = github.fetchBuildModuleCatalog(repository.url)) {
                is Result.Success -> {
                    val data = result.data
                    val updated = repository.copy(
                        indexJsonUrl = data.indexUrl,
                        name = data.name,
                        modules = data.modules,
                        lastUpdated = System.currentTimeMillis(),
                        error = null,
                        skippedCount = data.skippedCount
                    )
                    saveBuildModuleRepositories(
                        _uiState.value.buildModuleRepositories.map {
                            if (it.id == id) updated else it
                        }
                    )
                }
                is Result.Error -> {
                    val updated = repository.copy(error = result.message)
                    saveBuildModuleRepositories(
                        _uiState.value.buildModuleRepositories.map {
                            if (it.id == id) updated else it
                        }
                    )
                }
                Result.Loading -> Unit
            }
            _uiState.update {
                it.copy(refreshingBuildModuleRepositoryIds = it.refreshingBuildModuleRepositoryIds - id)
            }
        }
    }

    fun refreshAllBuildModuleRepositories() {
        _uiState.value.buildModuleRepositories.forEach { repository ->
            refreshBuildModuleRepository(repository.id)
        }
    }

    fun addModuleFromCatalog(module: ModuleCatalogItem, stage: String = module.defaultStage) {
        val cleanUrl = module.repoUrl.trim()
        if (cleanUrl.isBlank()) return
        val normalizedStage = CustomExternalModuleStage.normalize(stage)
        val currentConfig = KernelSupport.normalize(_uiState.value.buildConfig)
        val exists = currentConfig.customExternalModules.any {
            it.url.equals(cleanUrl, ignoreCase = true) &&
                CustomExternalModuleStage.normalize(it.stage) == normalizedStage
        }
        val modules = if (exists) {
            currentConfig.customExternalModules
        } else {
            currentConfig.customExternalModules + CustomExternalModule(
                url = cleanUrl,
                stage = normalizedStage
            )
        }
        updateBuildConfig(
            currentConfig.copy(
                useCustomExternalModules = true,
                customExternalModules = modules
            )
        )
    }

    fun removeCustomExternalModule(url: String, stage: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return
        val normalizedStage = CustomExternalModuleStage.normalize(stage)
        val currentConfig = KernelSupport.normalize(_uiState.value.buildConfig)
        updateBuildConfig(
            currentConfig.copy(
                customExternalModules = currentConfig.customExternalModules.filterNot {
                    it.url.equals(cleanUrl, ignoreCase = true) &&
                        CustomExternalModuleStage.normalize(it.stage) == normalizedStage
                }
            )
        )
    }

    fun setCustomExternalModuleStages(url: String, stages: List<String>) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return
        val normalizedStages = stages
            .map { CustomExternalModuleStage.normalize(it) }
            .filter { it in CustomExternalModuleStage.options }
            .distinct()
        val currentConfig = KernelSupport.normalize(_uiState.value.buildConfig)
        val remainingModules = currentConfig.customExternalModules.filterNot {
            it.url.equals(cleanUrl, ignoreCase = true)
        }
        val updatedModules = remainingModules + normalizedStages.map { stage ->
            CustomExternalModule(url = cleanUrl, stage = stage)
        }
        updateBuildConfig(
            currentConfig.copy(
                customExternalModules = updatedModules
            )
        )
    }

    suspend fun checkCustomExternalModuleMetadata(url: String): ExternalModuleMetadata? {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) {
            _uiState.update { it.copy(customExternalModuleError = text(R.string.vm_module_repo_url_empty)) }
            return null
        }
        _uiState.update { it.copy(validatingCustomExternalModule = true, customExternalModuleError = null) }
        return try {
            when (val result = github.fetchExternalModuleMetadata(cleanUrl)) {
                is Result.Success -> result.data
                is Result.Error -> {
                    _uiState.update { it.copy(customExternalModuleError = result.message) }
                    null
                }
                Result.Loading -> null
            }
        } finally {
            _uiState.update { it.copy(validatingCustomExternalModule = false) }
        }
    }

    fun addCustomExternalModulesFromUrl(url: String, stages: List<String>): Boolean {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) {
            _uiState.update { it.copy(customExternalModuleError = text(R.string.vm_module_repo_url_empty)) }
            return false
        }
        val normalizedStages = stages
            .map { CustomExternalModuleStage.normalize(it) }
            .distinct()
            .ifEmpty { listOf(CustomExternalModuleStage.AFTER_PATCH) }
        val currentConfig = KernelSupport.normalize(_uiState.value.buildConfig)
        val existing = currentConfig.customExternalModules.map {
            it.url.trim().lowercase() to CustomExternalModuleStage.normalize(it.stage)
        }.toSet()
        val modules = currentConfig.customExternalModules + normalizedStages
            .filterNot { stage -> cleanUrl.lowercase() to stage in existing }
            .map { stage -> CustomExternalModule(url = cleanUrl, stage = stage) }
        updateBuildConfig(
            currentConfig.copy(
                useCustomExternalModules = true,
                customExternalModules = modules
            )
        )
        _uiState.update { it.copy(customExternalModuleError = null) }
        return true
    }

    fun addRuntimeModuleRepository(url: String) {
        val cleanUrl = normalizeModuleCatalogUrl(url)
        if (cleanUrl.isBlank()) {
            _uiState.update { it.copy(error = text(R.string.vm_module_repo_url_empty)) }
            return
        }

        val current = _uiState.value.runtimeModuleRepositories
        val existing = current.firstOrNull { it.url.equals(cleanUrl, ignoreCase = true) }
        if (existing != null) {
            refreshRuntimeModuleRepository(existing.id)
            return
        }

        val repository = RuntimeModuleRepository(
            id = UUID.randomUUID().toString(),
            url = cleanUrl,
            name = cleanUrl.moduleCatalogFallbackName(localizedRuntimeModuleRepoTitle())
        )
        saveRuntimeModuleRepositories(current + repository)
        refreshRuntimeModuleRepository(repository.id)
    }

    fun deleteRuntimeModuleRepository(id: String) {
        saveRuntimeModuleRepositories(_uiState.value.runtimeModuleRepositories.filterNot { it.id == id })
    }

    fun refreshRuntimeModuleRepository(id: String) {
        val repository = _uiState.value.runtimeModuleRepositories.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(refreshingRuntimeModuleRepositoryIds = it.refreshingRuntimeModuleRepositoryIds + id)
            }
            when (val result = github.fetchRuntimeModuleCatalog(repository.url)) {
                is Result.Success -> {
                    val data = result.data
                    val updated = repository.copy(
                        indexJsonUrl = data.indexUrl,
                        name = data.name,
                        modules = data.modules,
                        lastUpdated = System.currentTimeMillis(),
                        error = null,
                        skippedCount = data.skippedCount
                    )
                    saveRuntimeModuleRepositories(
                        _uiState.value.runtimeModuleRepositories.map {
                            if (it.id == id) updated else it
                        }
                    )
                }
                is Result.Error -> {
                    val updated = repository.copy(error = result.message)
                    saveRuntimeModuleRepositories(
                        _uiState.value.runtimeModuleRepositories.map {
                            if (it.id == id) updated else it
                        }
                    )
                }
                Result.Loading -> Unit
            }
            _uiState.update {
                it.copy(refreshingRuntimeModuleRepositoryIds = it.refreshingRuntimeModuleRepositoryIds - id)
            }
        }
    }

    fun refreshAllRuntimeModuleRepositories() {
        _uiState.value.runtimeModuleRepositories.forEach { repository ->
            refreshRuntimeModuleRepository(repository.id)
        }
    }

    private fun refreshStaleRuntimeModuleRepositories(repositories: List<RuntimeModuleRepository>) {
        repositories
            .filter { it.lastUpdated <= 0L && it.error == null }
            .forEach { repository -> refreshRuntimeModuleRepository(repository.id) }
    }

    private fun refreshStaleBuildModuleRepositories(repositories: List<ModuleCatalogRepository>) {
        repositories
            .filter { it.lastUpdated <= 0L && it.error == null }
            .forEach { repository -> refreshBuildModuleRepository(repository.id) }
    }

    suspend fun addCustomExternalModuleFromUrl(url: String, stage: String): Boolean {
        val metadata = checkCustomExternalModuleMetadata(url) ?: return false
        val normalizedStage = CustomExternalModuleStage.normalize(stage)
        return if (normalizedStage in metadata.supportedStages) {
            addCustomExternalModulesFromUrl(url, listOf(normalizedStage))
        } else {
            _uiState.update { it.copy(customExternalModuleError = text(R.string.vm_module_stage_unsupported, normalizedStage)) }
            false
        }
    }

    private fun saveBuildPlans(plans: List<BuildPlan>) {
        val sanitized = plans
            .mapNotNull(::sanitizeBuildPlan)
            .distinctBy { it.id }
            .sortedByDescending { it.updatedAt }
        _uiState.update { it.copy(buildPlans = sanitized) }
        viewModelScope.launch { prefs.saveBuildPlansJson(gson.toJson(sanitized)) }
    }

    private fun saveBuildQueue(items: List<BuildQueueItem>) {
        val sanitized = items
            .map(::sanitizeBuildQueueItem)
            .distinctBy { it.id }
        _uiState.update { it.copy(buildQueue = sanitized) }
        viewModelScope.launch { prefs.saveBuildQueueJson(gson.toJson(sanitized)) }
    }

    private fun updateBuildQueueItem(
        itemId: String,
        transform: (BuildQueueItem) -> BuildQueueItem
    ) {
        val updated = _uiState.value.buildQueue.map { item ->
            if (item.id == itemId) sanitizeBuildQueueItem(transform(item)) else item
        }
        saveBuildQueue(updated)
    }

    private fun markBuildQueueItemFailed(itemId: String, message: String) {
        updateBuildQueueItem(itemId) {
            it.copy(status = BuildQueueItemStatus.FAILED, error = message)
        }
    }

    private fun attachRunToActiveQueueItem(run: WorkflowRun) {
        val current = _uiState.value.buildQueue
        val target = current.firstOrNull { it.runId == run.id }
            ?: current.firstOrNull {
                it.status in setOf(BuildQueueItemStatus.DISPATCHING, BuildQueueItemStatus.RUNNING)
            }
            ?: return
        updateBuildQueueItem(target.id) {
            it.copy(
                status = BuildQueueItemStatus.RUNNING,
                runId = run.id,
                runNumber = run.runNumber,
                error = null
            )
        }
    }

    private fun syncBuildQueueWithRun(run: WorkflowRun, status: BuildStatus) {
        val itemStatus = when (status) {
            BuildStatus.SUCCESS -> BuildQueueItemStatus.DONE
            BuildStatus.FAILURE -> BuildQueueItemStatus.FAILED
            BuildStatus.CANCELLED -> BuildQueueItemStatus.CANCELLED
            BuildStatus.QUEUED,
            BuildStatus.IN_PROGRESS -> BuildQueueItemStatus.RUNNING
            BuildStatus.IDLE -> return
        }
        val error = when (itemStatus) {
            BuildQueueItemStatus.FAILED -> text(R.string.vm_workflow_finished, run.conclusion ?: run.status)
            else -> null
        }
        val current = _uiState.value.buildQueue
        if (current.none { it.runId == run.id }) return
        saveBuildQueue(
            current.map { item ->
                if (item.runId == run.id) {
                    item.copy(
                        status = itemStatus,
                        runNumber = run.runNumber,
                        error = error
                    )
                } else {
                    item
                }
            }
        )
    }

    private fun syncBuildQueueWithRunId(runId: Long, status: BuildQueueItemStatus) {
        if (runId <= 0L) return
        val current = _uiState.value.buildQueue
        if (current.none { it.runId == runId }) return
        saveBuildQueue(
            current.map { item ->
                if (item.runId == runId) item.copy(status = status) else item
            }
        )
    }

    private fun saveRuntimeModuleRepositories(repositories: List<RuntimeModuleRepository>) {
        val sanitized = sanitizeRuntimeModuleRepositories(repositories)
        _uiState.update { it.copy(runtimeModuleRepositories = sanitized) }
        viewModelScope.launch { prefs.saveRuntimeModuleRepositoriesJson(gson.toJson(sanitized)) }
    }

    private fun saveBuildModuleRepositories(repositories: List<ModuleCatalogRepository>) {
        val sanitized = sanitizeBuildModuleRepositories(repositories)
        _uiState.update { it.copy(buildModuleRepositories = sanitized) }
        viewModelScope.launch { prefs.saveBuildModuleRepositoriesJson(gson.toJson(sanitized)) }
    }

    fun loadBuildParameterSummary(runId: Long, force: Boolean = false) {
        if (runId <= 0L) return
        val current = _uiState.value
        if (!force && current.buildParameterSummaries.containsKey(runId)) return
        if (runId in current.loadingBuildParameterRunIds) return
        val username = current.user?.login ?: return
        val repoName = current.forkRepo?.name ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loadingBuildParameterRunIds = it.loadingBuildParameterRunIds + runId,
                    buildParameterErrors = it.buildParameterErrors - runId
                )
            }
            val run = findRunForParameterSummary(username, repoName, runId)
            var firstFailure: String? = null
            val summaryJob = when (val jobsResult = github.listRunJobs(username, repoName, runId)) {
                is Result.Success -> jobsResult.data.firstOrNull { job ->
                    job.steps.orEmpty().any { step -> step.name == BUILD_SUMMARY_STEP_NAME }
                }.also {
                    if (it == null) firstFailure = text(R.string.vm_summary_step_missing, BUILD_SUMMARY_STEP_NAME)
                }
                is Result.Error -> {
                    firstFailure = jobsResult.message
                    null
                }
                Result.Loading -> null
            }

            if (summaryJob != null) {
                when (val logsResult = github.downloadJobLogs(username, repoName, summaryJob.id)) {
                    is Result.Success -> {
                        val summary = parseBuildParameterSummaryLocalized(logsResult.data, runId, run)
                        if (summary != null) {
                        saveBuildParameterSummary(runId, summary)
                        return@launch
                    }
                    firstFailure = text(R.string.vm_job_summary_missing)
                }
                    is Result.Error -> firstFailure = logsResult.message
                    Result.Loading -> Unit
                }
            }

            when (val runLogsResult = github.downloadRunLogs(username, repoName, runId)) {
                is Result.Success -> {
                    val summary = parseBuildParameterSummaryLocalized(runLogsResult.data, runId, run)
                    if (summary != null) {
                        saveBuildParameterSummary(runId, summary)
                        return@launch
                    }
                    val prefix = firstFailure?.let { "$it；" }.orEmpty()
                    setBuildParameterLoadError(runId, prefix + text(R.string.vm_workflow_summary_missing))
                }
                is Result.Error -> {
                    val prefix = firstFailure?.let { text(R.string.vm_job_log_read_failed, it) }.orEmpty()
                    setBuildParameterLoadError(runId, prefix + text(R.string.vm_workflow_log_read_failed, runLogsResult.message))
                }
                Result.Loading -> setBuildParameterLoadError(runId, text(R.string.vm_logs_not_ready))
            }
        }
    }

    private suspend fun saveBuildParameterSummary(runId: Long, summary: BuildParameterSummary) {
        val updated = _uiState.value.buildParameterSummaries + (runId to summary)
        _uiState.update {
            it.copy(
                buildParameterSummaries = updated,
                loadingBuildParameterRunIds = it.loadingBuildParameterRunIds - runId,
                buildParameterErrors = it.buildParameterErrors - runId
            )
        }
        prefs.saveBuildParameterSummariesJson(gson.toJson(updated.values.sortedByDescending { it.runNumber }))
    }

    private fun parseDownloadedArtifacts(json: String?): List<DownloadedArtifact> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching<List<DownloadedArtifact>> {
            val type = object : TypeToken<List<DownloadedArtifact>>() {}.type
            gson.fromJson<List<DownloadedArtifact>>(json, type).orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun parseBuildPlans(json: String?): List<BuildPlan> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching<List<BuildPlan>> {
            val type = object : TypeToken<List<BuildPlan>>() {}.type
            gson.fromJson<List<BuildPlan>>(json, type).orEmpty()
                .mapNotNull(::sanitizeBuildPlan)
                .distinctBy { it.id }
                .sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    private fun sanitizeBuildPlan(plan: BuildPlan): BuildPlan? = runCatching {
        val normalized = KernelSupport.normalize(plan.config)
        val now = System.currentTimeMillis()
        val createdAt = plan.createdAt.takeIf { it > 0L } ?: now
        plan.copy(
            id = plan.id.ifBlank { UUID.randomUUID().toString() },
            name = sanitizeBuildPlanName(plan.name, normalized),
            config = normalized,
            createdAt = createdAt,
            updatedAt = plan.updatedAt.takeIf { it > 0L } ?: createdAt
        )
    }.getOrNull()

    private fun parseBuildQueue(json: String?): List<BuildQueueItem> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching<List<BuildQueueItem>> {
            val type = object : TypeToken<List<BuildQueueItem>>() {}.type
            gson.fromJson<List<BuildQueueItem>>(json, type).orEmpty()
                .map(::sanitizeBuildQueueItem)
                .distinctBy { it.id }
        }.getOrDefault(emptyList())
    }

    private fun sanitizeBuildQueueItem(item: BuildQueueItem): BuildQueueItem {
        val normalized = KernelSupport.normalize(item.config)
        val createdAt = item.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        val status = if (
            item.runId <= 0L &&
            item.status in setOf(BuildQueueItemStatus.DISPATCHING, BuildQueueItemStatus.RUNNING)
        ) {
            BuildQueueItemStatus.PENDING
        } else {
            item.status
        }
        return item.copy(
            id = item.id.ifBlank { UUID.randomUUID().toString() },
            name = item.name.ifBlank { sanitizeBuildPlanName("", normalized) },
            config = normalized,
            createdAt = createdAt,
            status = status,
            runId = item.runId.coerceAtLeast(0L),
            runNumber = item.runNumber.coerceAtLeast(0),
            error = item.error?.takeIf { it.isNotBlank() }
        )
    }

    private fun parseRuntimeModuleRepositories(json: String?): List<RuntimeModuleRepository> {
        if (json.isNullOrBlank()) return defaultRuntimeModuleRepositories()
        return runCatching<List<RuntimeModuleRepository>> {
            val type = object : TypeToken<List<RuntimeModuleRepository>>() {}.type
            sanitizeRuntimeModuleRepositories(
                gson.fromJson<List<RuntimeModuleRepository>>(json, type).orEmpty()
            )
        }.getOrDefault(defaultRuntimeModuleRepositories())
    }

    private fun sanitizeRuntimeModuleRepositories(
        repositories: List<RuntimeModuleRepository>
    ): List<RuntimeModuleRepository> {
        return repositories
            .mapNotNull { repository ->
                val url = normalizeModuleCatalogUrl(repository.url)
                if (url.isBlank()) return@mapNotNull null
                val modules = repository.modules
                    .mapNotNull(::sanitizeRuntimeModuleCatalogItem)
                    .distinctBy { it.id.trim().lowercase().ifBlank { it.name.trim().lowercase() } }
                    .sortedBy { it.name.lowercase() }
                repository.copy(
                    id = repository.id.ifBlank { UUID.randomUUID().toString() },
                    url = url,
                    indexJsonUrl = repository.indexJsonUrl.trim(),
                    name = repository.name.trim().ifBlank { url.moduleCatalogFallbackName(localizedRuntimeModuleRepoTitle()) },
                    modules = modules,
                    lastUpdated = repository.lastUpdated.takeIf { it > 0L } ?: 0L,
                    error = repository.error?.takeIf { it.isNotBlank() },
                    skippedCount = repository.skippedCount.coerceAtLeast(0)
                )
            }
            .distinctBy { it.url.lowercase() }
            .sortedWith(compareByDescending<RuntimeModuleRepository> {
                if (it.url == OFFICIAL_RUNTIME_MODULE_REPOSITORY_URL) 1 else 0
            }
                .thenBy { it.name.lowercase() })
    }

    private fun sanitizeRuntimeModuleCatalogItem(item: RuntimeModuleCatalogItem): RuntimeModuleCatalogItem? {
        val name = item.name.trim()
        val zipUrl = item.zipUrl.trim()
        if (name.isBlank() || zipUrl.isBlank()) return null
        return item.copy(
            id = item.id.trim().ifBlank { name.lowercase().replace(' ', '_') },
            name = name,
            version = item.version.trim(),
            author = item.author.trim(),
            description = item.description.trim(),
            zipUrl = zipUrl,
            changelog = item.changelog.trim(),
            support = item.support.trim(),
            donate = item.donate.trim(),
            website = item.website.trim(),
            cover = item.cover.trim(),
            icon = item.icon.trim()
        )
    }

    private fun defaultRuntimeModuleRepositories(): List<RuntimeModuleRepository> = listOf(
        RuntimeModuleRepository(
            id = OFFICIAL_RUNTIME_MODULE_REPOSITORY_ID,
            url = OFFICIAL_RUNTIME_MODULE_REPOSITORY_URL,
            name = localizedRuntimeModuleRepoTitle()
        )
    )

    private fun parseBuildModuleRepositories(json: String?): List<ModuleCatalogRepository> {
        if (json.isNullOrBlank()) return defaultBuildModuleRepositories()
        return runCatching<List<ModuleCatalogRepository>> {
            val type = object : TypeToken<List<ModuleCatalogRepository>>() {}.type
            sanitizeBuildModuleRepositories(
                gson.fromJson<List<ModuleCatalogRepository>>(json, type).orEmpty()
            )
        }.getOrDefault(defaultBuildModuleRepositories())
    }

    private fun sanitizeBuildModuleRepositories(
        repositories: List<ModuleCatalogRepository>
    ): List<ModuleCatalogRepository> {
        return repositories
            .mapNotNull { repository ->
                val url = normalizeModuleCatalogUrl(repository.url)
                if (url.isBlank()) return@mapNotNull null
                val modules = repository.modules
                    .mapNotNull(::sanitizeBuildModuleCatalogItem)
                    .distinctBy { it.repoUrl.trim().lowercase() }
                    .sortedBy { it.name.lowercase() }
                repository.copy(
                    id = repository.id.ifBlank { UUID.randomUUID().toString() },
                    url = url,
                    indexJsonUrl = repository.indexJsonUrl.trim(),
                    name = repository.name.trim().ifBlank { url.moduleCatalogFallbackName(localizedBuildModuleRepoTitle()) },
                    modules = modules,
                    lastUpdated = repository.lastUpdated.takeIf { it > 0L } ?: 0L,
                    error = repository.error?.takeIf { it.isNotBlank() },
                    skippedCount = repository.skippedCount.coerceAtLeast(0)
                )
            }
            .distinctBy { it.url.lowercase() }
            .sortedWith(compareByDescending<ModuleCatalogRepository> {
                if (it.url == OFFICIAL_BUILD_MODULE_CATALOG_URL) 1 else 0
            }
                .thenBy { it.name.lowercase() })
    }

    private fun sanitizeBuildModuleCatalogItem(item: ModuleCatalogItem): ModuleCatalogItem? {
        val repoUrl = item.repoUrl.trim()
        if (repoUrl.isBlank()) return null
        val supportedStages = item.supportedStages
            .map { CustomExternalModuleStage.normalize(it) }
            .distinct()
            .ifEmpty { listOf(CustomExternalModuleStage.AFTER_PATCH) }
        val defaultStage = CustomExternalModuleStage.normalize(item.defaultStage)
            .takeIf { it in supportedStages }
            ?: supportedStages.first()
        val recommendedStages = item.recommendedStages
            .map { CustomExternalModuleStage.normalize(it) }
            .distinct()
            .filter { it in supportedStages }
            .ifEmpty { listOf(defaultStage) }
        return item.copy(
            name = item.name.trim().ifBlank { repoUrl.moduleCatalogFallbackName(localizedBuildModuleRepoTitle()) },
            version = item.version.trim(),
            description = item.description.trim(),
            repoUrl = repoUrl,
            defaultStage = defaultStage,
            supportedStages = supportedStages,
            recommendedStages = recommendedStages,
            author = item.author.trim(),
            homepage = item.homepage.trim()
        )
    }

    private fun defaultBuildModuleRepositories(): List<ModuleCatalogRepository> = listOf(
        ModuleCatalogRepository(
            id = OFFICIAL_BUILD_MODULE_CATALOG_ID,
            url = OFFICIAL_BUILD_MODULE_CATALOG_URL,
            name = text(R.string.vm_official_module_repo)
        )
    )

    private fun parseBuildArtifacts(json: String?): List<BuildArtifact> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching<List<BuildArtifact>> {
            val type = object : TypeToken<List<BuildArtifact>>() {}.type
            gson.fromJson<List<BuildArtifact>>(json, type).orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun parseBuildParameterSummaries(json: String?): List<BuildParameterSummary> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching<List<BuildParameterSummary>> {
            val type = object : TypeToken<List<BuildParameterSummary>>() {}.type
            gson.fromJson<List<BuildParameterSummary>>(json, type).orEmpty()
        }.getOrDefault(emptyList())
    }

    private suspend fun findRunForParameterSummary(
        owner: String,
        repoName: String,
        runId: Long
    ): WorkflowRun? {
        val state = _uiState.value
        return state.recentRuns.firstOrNull { it.id == runId }
            ?: state.currentRun?.takeIf { it.id == runId }
            ?: state.artifacts.firstOrNull { it.runId == runId }?.toWorkflowRun()
            ?: state.downloadedArtifacts.firstOrNull { it.runId == runId }?.let { artifact ->
                WorkflowRun(
                    id = artifact.runId,
                    name = artifact.runTitle,
                    status = "completed",
                    conclusion = null,
                    htmlUrl = "",
                    createdAt = "",
                    updatedAt = "",
                    runNumber = artifact.runNumber,
                    workflowId = 0L,
                    headBranch = null,
                    displayTitle = artifact.runTitle
                )
            }
            ?: when (val runResult = github.getWorkflowRun(owner, repoName, runId)) {
                is Result.Success -> runResult.data
                else -> null
            }
    }

    private suspend fun setBuildParameterLoadError(runId: Long, message: String) {
        _uiState.update {
            it.copy(
                loadingBuildParameterRunIds = it.loadingBuildParameterRunIds - runId,
                buildParameterErrors = it.buildParameterErrors + (runId to message)
            )
        }
    }

    private fun parseBuildParameterSummaryLocalized(
        logs: String,
        runId: Long,
        run: WorkflowRun?
    ): BuildParameterSummary? = parseBuildParameterSummary(
        logs = logs,
        runId = runId,
        run = run,
        emptyValue = text(R.string.vm_value_none),
        defaultValue = text(R.string.vm_value_default),
        setValue = text(R.string.vm_value_set)
    )

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun clearCustomExternalModuleError() = _uiState.update { it.copy(customExternalModuleError = null) }

    private fun buildPlanCodecMessages(): BuildPlanCodecMessages = BuildPlanCodecMessages(
        unsupportedVersion = text(R.string.vm_plan_bad_version),
        tooManyModules = text(R.string.vm_plan_too_many_modules),
        negativeNumber = text(R.string.vm_plan_negative_number),
        fieldTooLong = text(R.string.vm_plan_field_too_long),
        incomplete = text(R.string.vm_plan_incomplete),
        badNumber = text(R.string.vm_plan_bad_number),
        unknownData = text(R.string.vm_plan_unknown_data),
        unsupportedShareType = text(R.string.vm_plan_unsupported_share_type)
    )

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(statusReceiver) }
        super.onCleared()
    }
}

private fun resolveManagerAccess(rootGranted: Boolean): RootUtils.ManagerAccessInfo =
    RootUtils.resolveManagerAccess(rootGranted)

private fun RootUtils.ManagerAccessInfo.toUiState(): ManagerAccessState =
    when (kind) {
        RootUtils.ManagerAccessKind.NATIVE_MANAGER -> ManagerAccessState.NATIVE_MANAGER
        RootUtils.ManagerAccessKind.ROOT_ONLY -> ManagerAccessState.ROOT_ONLY
        RootUtils.ManagerAccessKind.NO_ROOT -> ManagerAccessState.NO_ROOT
        RootUtils.ManagerAccessKind.NATIVE_KERNEL_NO_MANAGER -> ManagerAccessState.NATIVE_KERNEL_NO_MANAGER
    }

internal fun sanitizeBuildPlanName(name: String, config: KernelBuildConfig): String =
    name.trim().ifBlank { defaultBuildPlanName(config) }.take(BUILD_PLAN_NAME_LIMIT)

internal fun defaultBuildPlanName(config: KernelBuildConfig): String {
    if (config.buildTarget == BUILD_TARGET_ONEPLUS) {
        return listOf(KernelSupport.onePlusDeviceLabel(config.onePlusDeviceManifest), config.kernelsuVariant)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }
    val android = config.androidVersion.removePrefix("android").ifBlank { config.androidVersion }
    return listOf("${config.kernelVersion}.${config.subLevel}", "Android $android", config.kernelsuVariant)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}

internal fun normalizeModuleCatalogUrl(url: String): String = url.trim().trimEnd('/')

internal fun String.moduleCatalogFallbackName(fallback: String = "Module repository"): String = trim()
    .trimEnd('/')
    .substringAfterLast('/')
    .removeSuffix(".git")
    .removeSuffix(".json")
    .ifBlank { fallback }

private fun MainViewModel.localizedRuntimeModuleRepoTitle(): String =
    when (LocaleHelper.getLanguage(getApplication())) {
        LocaleHelper.LANG_ZH -> "普通模块仓库"
        LocaleHelper.LANG_RU -> "Репозиторий обычных модулей"
        else -> "Standard Module Repo"
    }

private fun MainViewModel.localizedBuildModuleRepoTitle(): String =
    when (LocaleHelper.getLanguage(getApplication())) {
        LocaleHelper.LANG_ZH -> "ABK 模块仓库"
        LocaleHelper.LANG_RU -> "Репозиторий модулей ABK"
        else -> "ABK Module Repo"
    }

private fun padBase64Url(value: String): String =
    value + "=".repeat((4 - value.length % 4) % 4)

internal data class DecodedBuildPlanCode(
    val name: String,
    val config: KernelBuildConfig,
    val scope: BuildPlanShareScope
)

internal data class BuildPlanCodecMessages(
    val unsupportedVersion: String = "Unsupported plan code version",
    val tooManyModules: String = "External module count exceeds the limit",
    val negativeNumber: String = "Negative numbers can not be written to a plan code",
    val fieldTooLong: String = "Plan field is too long",
    val incomplete: String = "Plan code content is incomplete",
    val badNumber: String = "Plan code numeric field is invalid",
    val unknownData: String = "Plan code contains unrecognized data",
    val unsupportedShareType: String = "Unsupported plan share type"
)

internal fun encodeBuildPlanPayload(
    config: KernelBuildConfig,
    name: String,
    scope: BuildPlanShareScope,
    messages: BuildPlanCodecMessages = BuildPlanCodecMessages()
): ByteArray {
    val writer = BuildPlanBinaryWriter(messages)
    writer.writeByte(BUILD_PLAN_CODE_VERSION)
    writer.writeByte(scope.toWireValue())
    writer.writeString(name)
    if (scope == BuildPlanShareScope.FULL) {
        writer.writeString(config.androidVersion)
        writer.writeString(config.kernelVersion)
        writer.writeString(config.subLevel)
        writer.writeString(config.osPatchLevel)
        writer.writeString(config.revision)
        writer.writeString(config.buildTarget)
        writer.writeString(config.onePlusCpu)
        writer.writeString(config.onePlusDeviceManifest)
    }
    writer.writeByte(BUILD_PLAN_KSU_VARIANTS.indexOrZero(config.kernelsuVariant))
    writer.writeByte(BUILD_PLAN_KSU_BRANCHES.indexOrZero(config.kernelsuBranch))
    writer.writeByte(BUILD_PLAN_VIRTUALIZATION_OPTIONS.indexOrZero(config.virtualizationSupport))
    writer.writeVarInt(config.toBuildPlanFeatureMask())
    writer.writeString(config.version)
    writer.writeString(config.buildTime)
    writer.writeString(config.zramExtraAlgos)
    writer.writeString(config.kpmPassword)
    writer.writeString(config.customRef)
    val modules = if (config.useCustomExternalModules) {
        config.customExternalModules
            .mapNotNull { module ->
                val url = module.url.trim()
                if (url.isBlank()) {
                    null
                } else {
                    CustomExternalModule(
                        url = url,
                        stage = CustomExternalModuleStage.normalize(module.stage)
                    )
                }
            }
            .take(BUILD_PLAN_MAX_MODULES)
    } else {
        emptyList()
    }
    writer.writeVarInt(modules.size)
    modules.forEach { module ->
        writer.writeString(module.url)
        writer.writeByte(BUILD_PLAN_MODULE_STAGES.indexOrZero(CustomExternalModuleStage.normalize(module.stage)))
    }
    return writer.toByteArray()
}

internal fun decodeBuildPlanPayload(
    bytes: ByteArray,
    baseConfig: KernelBuildConfig,
    messages: BuildPlanCodecMessages = BuildPlanCodecMessages()
): DecodedBuildPlanCode {
    val reader = BuildPlanBinaryReader(bytes, messages)
    val version = reader.readByte()
    require(version in BUILD_PLAN_MIN_SUPPORTED_VERSION..BUILD_PLAN_CODE_VERSION) { messages.unsupportedVersion }
    val scope = buildPlanShareScopeFromWireValue(reader.readByte(), messages)
    val name = reader.readString()
    val versionBase = if (scope == BuildPlanShareScope.FULL) {
        val androidVersion = reader.readString()
        val kernelVersion = reader.readString()
        val subLevel = reader.readString()
        val osPatchLevel = reader.readString()
        val revision = reader.readString()
        if (version >= BUILD_PLAN_ONEPLUS_FIELDS_VERSION) {
            baseConfig.copy(
                androidVersion = androidVersion,
                kernelVersion = kernelVersion,
                subLevel = subLevel,
                osPatchLevel = osPatchLevel,
                revision = revision,
                buildTarget = reader.readString(),
                onePlusCpu = reader.readString(),
                onePlusDeviceManifest = reader.readString()
            )
        } else {
            baseConfig.copy(
                androidVersion = androidVersion,
                kernelVersion = kernelVersion,
                subLevel = subLevel,
                osPatchLevel = osPatchLevel,
                revision = revision
            )
        }
    } else {
        baseConfig
    }
    val ksuVariant = BUILD_PLAN_KSU_VARIANTS.valueOrDefault(reader.readByte(), versionBase.kernelsuVariant)
    val rawKsuBranchWire = reader.readByte()
    val ksuBranch = when {
        version < BUILD_PLAN_KSU_BRANCH_V5_VERSION && rawKsuBranchWire == 2 ->
            KSU_BRANCH_CUSTOM
        else ->
            BUILD_PLAN_KSU_BRANCHES.valueOrDefault(rawKsuBranchWire, versionBase.kernelsuBranch)
    }
    val virtualizationSupport = BUILD_PLAN_VIRTUALIZATION_OPTIONS.valueOrDefault(
        reader.readByte(),
        versionBase.virtualizationSupport
    )
    val featureMask = reader.readVarInt()
    val versionName = reader.readString()
    val buildTime = reader.readString()
    val zramExtraAlgos = reader.readString()
    val kpmPassword = reader.readString()
    val customRef = if (version >= BUILD_PLAN_CUSTOM_REF_VERSION) {
        reader.readString()
    } else {
        ""
    }
    val moduleCount = reader.readVarInt()
    require(moduleCount in 0..BUILD_PLAN_MAX_MODULES) { messages.tooManyModules }
    val modules = List(moduleCount) {
        CustomExternalModule(
            url = reader.readString().trim(),
            stage = BUILD_PLAN_MODULE_STAGES.valueOrDefault(
                reader.readByte(),
                CustomExternalModuleStage.AFTER_PATCH
            )
        )
    }.filter { it.url.isNotBlank() }
    reader.requireFullyRead()
    val merged = versionBase.copy(
        kernelsuVariant = ksuVariant,
        kernelsuBranch = ksuBranch,
        version = versionName,
        buildTime = buildTime,
        useZram = featureMask.hasBuildPlanFlag(0),
        useBbg = featureMask.hasBuildPlanFlag(1),
        useDdk = featureMask.hasBuildPlanFlag(2),
        useNtsync = featureMask.hasBuildPlanFlag(3),
        useNetworking = featureMask.hasBuildPlanFlag(4),
        useKpm = featureMask.hasBuildPlanFlag(5),
        useRekernel = featureMask.hasBuildPlanFlag(6),
        cancelSusfs = featureMask.hasBuildPlanFlag(7),
        suppOp = featureMask.hasBuildPlanFlag(8),
        zramFullAlgo = featureMask.hasBuildPlanFlag(9),
        zramExtraAlgos = zramExtraAlgos,
        kpmPassword = kpmPassword,
        customRef = customRef,
        virtualizationSupport = virtualizationSupport,
        useCustomExternalModules = featureMask.hasBuildPlanFlag(10),
        customExternalModules = modules,
        onePlusUseLz4kd = featureMask.hasBuildPlanFlag(11),
        onePlusUseBbr = featureMask.hasBuildPlanFlag(12),
        onePlusUseProxyOptimization = featureMask.hasBuildPlanFlag(13),
        onePlusUseUnicodeBypass = featureMask.hasBuildPlanFlag(14)
    )
    return DecodedBuildPlanCode(
        name = name,
        config = KernelSupport.normalize(merged),
        scope = scope
    )
}

private class BuildPlanBinaryWriter(
    private val messages: BuildPlanCodecMessages
) {
    private val output = ByteArrayOutputStream()

    fun writeByte(value: Int) {
        output.write(value and 0xff)
    }

    fun writeVarInt(value: Int) {
        require(value >= 0) { messages.negativeNumber }
        var remaining = value
        do {
            var byteValue = remaining and 0x7f
            remaining = remaining ushr 7
            if (remaining != 0) byteValue = byteValue or 0x80
            writeByte(byteValue)
        } while (remaining != 0)
    }

    fun writeString(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= BUILD_PLAN_MAX_STRING_BYTES) { messages.fieldTooLong }
        writeVarInt(bytes.size)
        output.write(bytes)
    }

    fun toByteArray(): ByteArray = output.toByteArray()
}

private class BuildPlanBinaryReader(
    private val bytes: ByteArray,
    private val messages: BuildPlanCodecMessages
) {
    private var position = 0

    fun readByte(): Int {
        require(position < bytes.size) { messages.incomplete }
        return bytes[position++].toInt() and 0xff
    }

    fun readVarInt(): Int {
        var result = 0
        var shift = 0
        while (shift <= 28) {
            val byteValue = readByte()
            result = result or ((byteValue and 0x7f) shl shift)
            if (byteValue and 0x80 == 0) return result
            shift += 7
        }
        throw IllegalArgumentException(messages.badNumber)
    }

    fun readString(): String {
        val length = readVarInt()
        require(length in 0..BUILD_PLAN_MAX_STRING_BYTES) { messages.fieldTooLong }
        require(position + length <= bytes.size) { messages.incomplete }
        val value = String(bytes, position, length, StandardCharsets.UTF_8)
        position += length
        return value
    }

    fun requireFullyRead() {
        require(position == bytes.size) { messages.unknownData }
    }
}

private fun BuildPlanShareScope.toWireValue(): Int = when (this) {
    BuildPlanShareScope.FULL -> 0
    BuildPlanShareScope.FEATURES_ONLY -> 1
}

private fun KernelBuildConfig.toBuildPlanFeatureMask(): Int {
    var mask = 0
    fun set(bit: Int, enabled: Boolean) {
        if (enabled) mask = mask or (1 shl bit)
    }
    set(0, useZram)
    set(1, useBbg)
    set(2, useDdk)
    set(3, useNtsync)
    set(4, useNetworking)
    set(5, useKpm)
    set(6, useRekernel)
    set(7, cancelSusfs)
    set(8, suppOp)
    set(9, zramFullAlgo)
    set(10, useCustomExternalModules)
    set(11, onePlusUseLz4kd)
    set(12, onePlusUseBbr)
    set(13, onePlusUseProxyOptimization)
    set(14, onePlusUseUnicodeBypass)
    return mask
}

private fun Int.hasBuildPlanFlag(bit: Int): Boolean = this and (1 shl bit) != 0

private fun List<String>.indexOrZero(value: String): Int =
    indexOf(value).takeIf { it >= 0 } ?: 0

private fun List<String>.valueOrDefault(index: Int, fallback: String): String =
    getOrNull(index) ?: fallback

private fun buildPlanShareScopeFromWireValue(
    value: Int,
    messages: BuildPlanCodecMessages = BuildPlanCodecMessages()
): BuildPlanShareScope = when (value) {
    0 -> BuildPlanShareScope.FULL
    1 -> BuildPlanShareScope.FEATURES_ONLY
    else -> throw IllegalArgumentException(messages.unsupportedShareType)
}

private const val BUILD_PLAN_CODE_PREFIX = "ABKP2:"
private const val BUILD_PLAN_LEGACY_CODE_PREFIX = "ABKP1:"
private const val BUILD_PLAN_CODE_VERSION = 5
private const val BUILD_PLAN_MIN_SUPPORTED_VERSION = 2
private const val BUILD_PLAN_CUSTOM_REF_VERSION = 3
private const val BUILD_PLAN_ONEPLUS_FIELDS_VERSION = 4
private const val BUILD_PLAN_KSU_BRANCH_V5_VERSION = 5
private const val BUILD_PLAN_NAME_LIMIT = 80
private const val BUILD_PLAN_MAX_STRING_BYTES = 4096
private const val BUILD_PLAN_MAX_MODULES = 32
private const val OFFICIAL_RUNTIME_MODULE_REPOSITORY_ID = "official-runtime-module-repository"
private const val OFFICIAL_RUNTIME_MODULE_REPOSITORY_URL =
    "https://raw.githubusercontent.com/Magisk-Modules-Alt-Repo/json-v2/refs/heads/main/json/modules.json"
private const val OFFICIAL_BUILD_MODULE_CATALOG_ID = "official-abk-module-catalog"
private const val OFFICIAL_BUILD_MODULE_CATALOG_URL = "https://github.com/xingguangcuican6666/ABK_repo"

private val BUILD_PLAN_KSU_VARIANTS = listOf("Official", "SukiSU", "ReSukiSU", "None")
private val BUILD_PLAN_KSU_BRANCHES = KSU_BRANCH_BUILD_PLAN_OPTIONS
private val BUILD_PLAN_VIRTUALIZATION_OPTIONS = listOf("off", "on", "678", "123", "345")
private val BUILD_PLAN_MODULE_STAGES = listOf(
    CustomExternalModuleStage.AFTER_PATCH,
    CustomExternalModuleStage.BEFORE_BUILD
)

private const val BUILD_SUMMARY_STEP_NAME = "\u6784\u5efa\u4fe1\u606f\u6458\u8981"
private const val BUILD_SUMMARY_HEADER = "\u5185\u6838\u6784\u5efa\u914d\u7f6e\u6458\u8981"
private const val BUILD_SUMMARY_ANDROID_VERSION_LINE = "Android \u7248\u672c"
private const val SUMMARY_LABEL_ANDROID_VERSION = "android\u7248\u672c"
private const val SUMMARY_LABEL_KERNEL_VERSION = "\u5185\u6838\u7248\u672c"
private const val SUMMARY_LABEL_SUB_LEVEL = "\u5b50\u7248\u672c\u53f7"
private const val SUMMARY_LABEL_PATCH_LEVEL = "\u8865\u4e01\u7ea7\u522b"
private const val SUMMARY_LABEL_KSU_VARIANT = "ksu\u53d8\u4f53"
private const val SUMMARY_LABEL_KSU_BRANCH = "ksu\u5206\u652f"
private const val SUMMARY_LABEL_BUILD_TIME = "\u6784\u5efa\u65f6\u95f4"
private const val SUMMARY_LABEL_SUSFS_STATUS = "susfs\u72b6\u6001"
private const val SUMMARY_LABEL_ZRAM = "zram\u589e\u5f3a"
private const val SUMMARY_LABEL_ZRAM_FULL_ALGO = "zram\u5b8c\u6574\u7b97\u6cd5"
private const val SUMMARY_LABEL_ZRAM_EXTRA_ALGOS = "zram\u989d\u5916\u7b97\u6cd5"
private const val SUMMARY_LABEL_BBG = "bbg\u8865\u4e01"
private const val SUMMARY_LABEL_NTSYNC = "ntsync\u8865\u4e01"
private const val SUMMARY_LABEL_NETWORKING = "networking\u589e\u5f3a"
private const val SUMMARY_LABEL_NETWORKING_TYPO = "networing\u589e\u5f3a"
private const val SUMMARY_LABEL_KPM = "kpm\u529f\u80fd"
private const val SUMMARY_LABEL_KPM_PASSWORD = "kpm\u5bc6\u7801"
private const val SUMMARY_LABEL_VIRTUALIZATION = "\u865a\u62df\u5316\u652f\u6301"
private const val SUMMARY_LABEL_CUSTOM_INJECTION = "\u81ea\u5b9a\u4e49\u6ce8\u5165"
private const val SUMMARY_VALUE_DEFAULT_ZH = "\u9ed8\u8ba4"
private const val SUMMARY_VALUE_NONE_ZH = "\u65e0"
private const val SUMMARY_VALUE_SET_ZH = "\u5df2\u8bbe\u7f6e"
private const val PREBUILT_TERM_ZH = "\u9884\u7f16\u8bd1"
private const val KERNEL_IMAGE_TERM_ZH = "\u5185\u6838\u955c\u50cf"
private const val FLASH_PACKAGE_TERM_ZH = "\u5237\u5199\u5305"
private const val APP_TERM_ZH = "\u5e94\u7528"
private const val CLIENT_TERM_ZH = "\u5ba2\u6237\u7aef"

internal fun parseBuildParameterSummary(
    logs: String,
    runId: Long,
    run: WorkflowRun?,
    emptyValue: String = SUMMARY_VALUE_NONE_ZH,
    defaultValue: String = SUMMARY_VALUE_DEFAULT_ZH,
    setValue: String = SUMMARY_VALUE_SET_ZH
): BuildParameterSummary? {
    val values = mutableMapOf<String, String>()
    val extraRows = linkedMapOf<String, String>()
    var summarySeen = false
    logs.lineSequence()
        .map(::cleanBuildSummaryLogLine)
        .forEach { line ->
            if (line.contains(BUILD_SUMMARY_HEADER)) {
                summarySeen = true
                return@forEach
            }
            if (!summarySeen && !line.contains(BUILD_SUMMARY_ANDROID_VERSION_LINE)) return@forEach
            if (summarySeen && values.isNotEmpty() && line.all { it == '=' || it.isWhitespace() }) return@forEach

            val separator = listOf(line.indexOf(':'), line.indexOf('：'))
                .filter { it >= 0 }
                .minOrNull() ?: return@forEach
            val label = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()
            val key = normalizeBuildSummaryLabel(label)
            if (key != null) {
                values[key] = sanitizeBuildSummaryValue(key, value, emptyValue, defaultValue, setValue)
            } else if (isBuildSummaryExtraLabel(label)) {
                extraRows[label] = value.ifBlank { emptyValue }
            }
        }
    if (values.isEmpty() && extraRows.isEmpty()) return null

    return BuildParameterSummary(
        runId = runId,
        runNumber = run?.runNumber ?: 0,
        runTitle = run?.displayTitle ?: run?.name.orEmpty(),
        runCreatedAt = run?.createdAt.orEmpty(),
        runHtmlUrl = run?.htmlUrl.orEmpty(),
        androidVersion = values["androidVersion"].orEmpty(),
        kernelVersion = values["kernelVersion"].orEmpty(),
        subLevel = values["subLevel"].orEmpty(),
        osPatchLevel = values["osPatchLevel"].orEmpty(),
        ksuVariant = values["ksuVariant"].orEmpty(),
        ksuBranch = values["ksuBranch"].orEmpty(),
        buildTime = values["buildTime"].orEmpty(),
        susfsEnabled = values["susfsEnabled"].orEmpty(),
        zramEnabled = values["zramEnabled"].orEmpty(),
        zramFullAlgo = values["zramFullAlgo"].orEmpty(),
        zramExtraAlgos = values["zramExtraAlgos"].orEmpty(),
        bbgEnabled = values["bbgEnabled"].orEmpty(),
        ddkLsm = values["ddkLsm"].orEmpty(),
        ntsyncEnabled = values["ntsyncEnabled"].orEmpty(),
        networkingEnabled = values["networkingEnabled"].orEmpty(),
        kpmEnabled = values["kpmEnabled"].orEmpty(),
        kpmPassword = values["kpmPassword"].orEmpty(),
        reKernelEnabled = values["reKernelEnabled"].orEmpty(),
        virtualizationSupport = values["virtualizationSupport"].orEmpty(),
        customInjection = values["customInjection"].orEmpty(),
        stockConfig = values["stockConfig"].orEmpty(),
        extraRows = extraRows
    )
}

private fun cleanBuildSummaryLogLine(line: String): String {
    val withoutAnsi = line.replace(Regex("\u001B\\[[;\\d]*[ -/]*[@-~]"), "")
    return withoutAnsi
        .replace(Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z\\s+"), "")
        .trim()
}

private fun normalizeBuildSummaryLabel(label: String): String? {
    val compact = label.replace(Regex("\\s+"), "").lowercase()
    return when {
        compact.contains(SUMMARY_LABEL_ANDROID_VERSION) -> "androidVersion"
        compact.contains(SUMMARY_LABEL_KERNEL_VERSION) -> "kernelVersion"
        compact.contains(SUMMARY_LABEL_SUB_LEVEL) -> "subLevel"
        compact.contains(SUMMARY_LABEL_PATCH_LEVEL) -> "osPatchLevel"
        compact.contains(SUMMARY_LABEL_KSU_VARIANT) -> "ksuVariant"
        compact.contains(SUMMARY_LABEL_KSU_BRANCH) -> "ksuBranch"
        compact.contains(SUMMARY_LABEL_BUILD_TIME) -> "buildTime"
        compact.contains(SUMMARY_LABEL_SUSFS_STATUS) -> "susfsEnabled"
        compact.contains(SUMMARY_LABEL_ZRAM) -> "zramEnabled"
        compact.contains(SUMMARY_LABEL_ZRAM_FULL_ALGO) -> "zramFullAlgo"
        compact.contains(SUMMARY_LABEL_ZRAM_EXTRA_ALGOS) -> "zramExtraAlgos"
        compact.contains(SUMMARY_LABEL_BBG) -> "bbgEnabled"
        compact.contains("ddklsm") -> "ddkLsm"
        compact.contains(SUMMARY_LABEL_NTSYNC) -> "ntsyncEnabled"
        compact.contains(SUMMARY_LABEL_NETWORKING) || compact.contains(SUMMARY_LABEL_NETWORKING_TYPO) -> "networkingEnabled"
        compact.contains(SUMMARY_LABEL_KPM) -> "kpmEnabled"
        compact.contains(SUMMARY_LABEL_KPM_PASSWORD) -> "kpmPassword"
        compact.contains("re-kernel") || compact.contains("rekernel") -> "reKernelEnabled"
        compact.contains(SUMMARY_LABEL_VIRTUALIZATION) -> "virtualizationSupport"
        compact.contains(SUMMARY_LABEL_CUSTOM_INJECTION) -> "customInjection"
        compact.contains("stockconfig") -> "stockConfig"
        else -> null
    }
}

private fun isBuildSummaryExtraLabel(label: String): Boolean {
    val compact = label.replace(Regex("\\s+"), "").lowercase()
    return compact in setOf(
        "构建目标",
        "机型配置",
        "cpu分支",
        "手机型号",
        "上游xml",
        "unicode绕过"
    )
}

private fun sanitizeBuildSummaryValue(
    key: String,
    value: String,
    emptyValue: String,
    defaultValue: String,
    setValue: String
): String {
    if (key != "kpmPassword") return value.ifBlank { emptyValue }
    val normalized = value.trim().lowercase()
    return when {
        normalized.isBlank() -> defaultValue
        normalized in setOf(SUMMARY_VALUE_DEFAULT_ZH, "default", SUMMARY_VALUE_NONE_ZH, "none", "not set") -> defaultValue
        else -> setValue
    }
}

private fun detectRecommendedBuildConfig(): KernelBuildConfig? {
    val kernelVersion = RootUtils.getKernelVersion()
    if (kernelVersion.isBlank() || kernelVersion.equals("Unknown", ignoreCase = true)) return null
    return KernelSupport.recommendedFromKernel(kernelVersion)
}

internal fun prebuiltGkiReleaseFromGitHub(release: GitHubReleaseSummary): PrebuiltGkiRelease {
    val fallbackId = release.tagName.hashCode().toLong().let { if (it < 0) -it else it }
    return PrebuiltGkiRelease(
        id = if (release.id != 0L) release.id else fallbackId,
        apiId = release.id,
        tagName = release.tagName,
        name = release.name?.takeIf { it.isNotBlank() } ?: release.tagName,
        htmlUrl = release.htmlUrl,
        publishedAt = release.publishedAt.orEmpty(),
        body = release.body.orEmpty(),
        assetCount = 0
    )
}

internal fun prebuiltGkiAssetsFromReleaseAssets(
    release: PrebuiltGkiRelease,
    assets: List<ReleaseAsset>
): List<PrebuiltGkiAsset> =
    assets.map { asset ->
        val fallbackId = "${release.tagName}/${asset.name}".hashCode().toLong().let {
            if (it < 0) -it else it
        }
        PrebuiltGkiAsset(
            id = if (asset.id != 0L) asset.id else fallbackId,
            name = asset.name,
            sizeBytes = asset.size,
            browserDownloadUrl = asset.browserDownloadUrl,
            contentType = asset.contentType,
            releaseTag = release.tagName,
            releaseName = release.name,
            releaseHtmlUrl = release.htmlUrl,
            publishedAt = release.publishedAt,
            releaseBody = release.body
        )
    }

internal fun prebuiltGkiReleaseComparator(): Comparator<PrebuiltGkiRelease> =
    compareByDescending<PrebuiltGkiRelease> { it.publishedAt }
        .thenBy { it.name }

internal fun isPrebuiltGkiReleaseCandidate(release: GitHubReleaseSummary): Boolean {
    val haystack = listOf(release.tagName, release.name.orEmpty(), release.body.orEmpty())
        .joinToString(" ")
        .lowercase()
        .replace('_', '-')
    val strongPrebuiltTerms = listOf(
        "gki",
        "prebuilt",
        "pre-built",
        PREBUILT_TERM_ZH,
        "boot.img",
        "anykernel",
        "ak3",
        "kernel image",
        KERNEL_IMAGE_TERM_ZH,
        FLASH_PACKAGE_TERM_ZH
    )
    if (strongPrebuiltTerms.any { haystack.contains(it) }) return true

    val appReleaseTerms = listOf(
        ".apk",
        "apk",
        "app",
        "android application",
        APP_TERM_ZH,
        CLIENT_TERM_ZH,
        "abk"
    )
    return appReleaseTerms.none { haystack.contains(it) } &&
        listOf("boot-", "boot_", "image", "img").any { haystack.contains(it) }
}

internal fun isPrebuiltGkiCandidate(asset: PrebuiltGkiAsset): Boolean {
    val lower = asset.name.lowercase()
    val type = DownloadUtils.classifyArtifact(asset.name)
    return type in setOf(ArtifactType.KERNEL_PACKAGE, ArtifactType.KERNEL_IMG, ArtifactType.ANYKERNEL3) ||
        ((lower.endsWith(".img") || lower.endsWith(".zip")) &&
            listOf("gki", "kernel", "boot", "anykernel", "ak3").any { lower.contains(it) })
}

internal fun prebuiltGkiComparator(
    recommended: KernelBuildConfig?
): Comparator<PrebuiltGkiAsset> =
    compareByDescending<PrebuiltGkiAsset> { prebuiltRecommendationScore(it, recommended) }
        .thenByDescending { it.publishedAt }
        .thenBy { it.name }

internal fun prebuiltRecommendationScore(asset: PrebuiltGkiAsset, recommended: KernelBuildConfig?): Int {
    recommended ?: return 0
    if (recommended.subLevel == "X") return 0
    val haystack = listOf(asset.name, asset.releaseTag, asset.releaseName, asset.releaseBody)
        .joinToString(" ")
        .lowercase()
        .replace('_', '-')
    val kernelSub = Regex(
        """(^|[^0-9])${Regex.escape(recommended.kernelVersion)}[.-]?${Regex.escape(recommended.subLevel)}([^0-9]|$)"""
    ).containsMatchIn(haystack)
    if (!kernelSub) return 0

    val androidNumber = recommended.androidVersion.removePrefix("android")
    val hasAndroid = haystack.contains(recommended.androidVersion.lowercase()) ||
        haystack.contains("android-$androidNumber") ||
        haystack.contains("a$androidNumber")
    val hasPatch = recommended.osPatchLevel.isNotBlank() && haystack.contains(recommended.osPatchLevel.lowercase())
    return 10 + (if (hasAndroid) 5 else 0) + (if (hasPatch) 8 else 0)
}

private data class BuildDisplaySnapshot(
    val status: BuildStatus,
    val currentRun: WorkflowRun?,
    val progress: BuildProgress
)

private fun MainUiState.withBuildRunDisplay(
    run: WorkflowRun,
    status: BuildStatus,
    progress: BuildProgress,
    cancellingWorkflowRunIds: Set<Long> = this.cancellingWorkflowRunIds
): MainUiState {
    val updatedRuns = if (run.isActiveBuildRun()) {
        (activeBuildRuns.filterNot { it.id == run.id } + run)
            .distinctBy { it.id }
            .sortedByDescending { it.id }
    } else {
        activeBuildRuns.filterNot { it.id == run.id }
    }
    val updatedProgressByRunId = if (run.isActiveBuildRun()) {
        buildProgressByRunId + (run.id to progress)
    } else {
        buildProgressByRunId - run.id
    }
    val display = buildDisplaySnapshot(
        activeRuns = updatedRuns,
        progressByRunId = updatedProgressByRunId,
        fallbackRun = run,
        fallbackStatus = status,
        fallbackProgress = progress
    )
    return copy(
        buildStatus = display.status,
        currentRun = display.currentRun,
        buildProgress = display.progress,
        activeBuildRuns = updatedRuns,
        buildProgressByRunId = updatedProgressByRunId,
        cancellingWorkflowRunIds = cancellingWorkflowRunIds
    )
}

private fun MainUiState.withoutActiveBuildRun(
    runId: Long,
    fallbackStatus: BuildStatus,
    fallbackProgress: BuildProgress,
    fallbackRun: WorkflowRun? = currentRun
): MainUiState {
    val updatedRuns = activeBuildRuns.filterNot { it.id == runId }
    val updatedProgressByRunId = buildProgressByRunId - runId
    val display = buildDisplaySnapshot(
        activeRuns = updatedRuns,
        progressByRunId = updatedProgressByRunId,
        fallbackRun = fallbackRun,
        fallbackStatus = fallbackStatus,
        fallbackProgress = fallbackProgress
    )
    return copy(
        buildStatus = display.status,
        currentRun = display.currentRun,
        buildProgress = display.progress,
        activeBuildRuns = updatedRuns,
        buildProgressByRunId = updatedProgressByRunId
    )
}

private fun buildDisplaySnapshot(
    activeRuns: List<WorkflowRun>,
    progressByRunId: Map<Long, BuildProgress>,
    fallbackRun: WorkflowRun?,
    fallbackStatus: BuildStatus,
    fallbackProgress: BuildProgress
): BuildDisplaySnapshot {
    val sortedRuns = activeRuns
        .filter { it.isActiveBuildRun() }
        .distinctBy { it.id }
        .sortedByDescending { it.id }
    if (sortedRuns.isEmpty()) {
        return BuildDisplaySnapshot(fallbackStatus, fallbackRun, fallbackProgress)
    }
    val status = if (sortedRuns.any { it.status == "in_progress" }) {
        BuildStatus.IN_PROGRESS
    } else {
        BuildStatus.QUEUED
    }
    return BuildDisplaySnapshot(
        status = status,
        currentRun = sortedRuns.firstOrNull(),
        progress = BuildProgressUtils.merge(sortedRuns, progressByRunId)
    )
}

private fun WorkflowRun.isActiveBuildRun(): Boolean =
    status in setOf("queued", "waiting", "requested", "pending", "in_progress")

private fun WorkflowRun.toBuildStatus(): BuildStatus = when (status) {
    "queued", "waiting", "requested", "pending" -> BuildStatus.QUEUED
    "in_progress" -> BuildStatus.IN_PROGRESS
    "completed" -> when (conclusion) {
        "success" -> BuildStatus.SUCCESS
        "cancelled" -> BuildStatus.CANCELLED
        else -> BuildStatus.FAILURE
    }
    else -> BuildStatus.IDLE
}

// Helper to convert KernelBuildConfig to workflow dispatch inputs map
internal fun KernelBuildConfig.toInputMap(): Map<String, String> {
    val config = KernelSupport.normalize(this)
    if (config.buildTarget == BUILD_TARGET_ONEPLUS) {
        return mapOf(
            "cpu" to config.onePlusCpu,
            "device_manifest" to config.onePlusDeviceManifest,
            "android_version" to config.androidVersion,
            "kernel_version" to config.kernelVersion,
            "ksu_variant" to config.kernelsuVariant,
            "enable_susfs" to (!config.cancelSusfs && config.kernelsuVariant != KSU_VARIANT_NONE).toString(),
            "use_kpm" to config.useKpm.toString(),
            "use_lz4kd" to config.onePlusUseLz4kd.toString(),
            "use_bbg" to config.useBbg.toString(),
            "use_bbr" to config.onePlusUseBbr.toString(),
            "use_proxy_optimization" to config.onePlusUseProxyOptimization.toString(),
            "use_unicode_bypass" to config.onePlusUseUnicodeBypass.toString()
        )
    }
    return mapOf(
        "android_version" to config.androidVersion,
        "kernel_version" to config.kernelVersion,
        "sub_level" to config.subLevel,
        "os_patch_level" to config.osPatchLevel,
        "revision" to config.revision,
        "kernelsu_variant" to config.kernelsuVariant,
        "kernelsu_branch" to config.kernelsuBranch,
        "version" to config.version,
        "build_time" to config.buildTime,
        "use_zram" to config.useZram.toString(),
        "use_bbg" to config.useBbg.toString(),
        "use_ddk" to config.useDdk.toString(),
        "use_ntsync" to config.useNtsync.toString(),
        "use_networking" to config.useNetworking.toString(),
        "use_kpm" to config.useKpm.toString(),
        "use_rekernel" to config.useRekernel.toString(),
        "cancel_susfs" to config.cancelSusfs.toString(),
        "supp_op" to config.suppOp.toString(),
        "zram_full_algo" to config.zramFullAlgo.toString(),
        "zram_extra_algos" to config.zramExtraAlgos,
        "kpm_password" to config.kpmPassword,
        "virtualization_support" to config.virtualizationSupport,
        "use_custom_external_modules" to config.useCustomExternalModules.toString(),
        "custom_ref" to if (config.kernelsuBranch == KSU_BRANCH_CUSTOM) {
            config.customRef.trim()
        } else {
            ""
        },
        "custom_external_modules" to if (config.useCustomExternalModules) {
            config.customExternalModules.toWorkflowInput()
        } else {
            ""
        }
    )
}

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
private const val ONEPLUS_WORKFLOW_FILE = "oneplus-custom.yml"
private val buildWorkflowFiles = listOf(KERNEL_WORKFLOW_FILE, ONEPLUS_WORKFLOW_FILE)
private const val MIRROR_WORKFLOW_FILE = "mirror-custom-artifacts.yml"
private val ACTIVE_BUILD_STATUSES = setOf(BuildStatus.QUEUED, BuildStatus.IN_PROGRESS)
private const val MANAGER_SETTING_APP_PROFILE_TEMPLATES = "app_profile_templates"
private const val MANAGER_SETTING_TOOLS = "manager_tools"
private const val MANAGER_SETTING_KPM = "kpm"
private const val MANAGER_SETTING_SU_COMPAT = "su_compat"
private const val MANAGER_SETTING_KERNEL_UMOUNT = "kernel_umount"
private const val MANAGER_SETTING_ADB_ROOT = "adb_root"
private const val MANAGER_SETTING_SULOG = "sulog"
private const val MANAGER_SETTING_SELINUX_HIDE = "selinux_hide"
private const val MANAGER_SETTING_DEFAULT_UMOUNT = "default_umount_modules"
private const val MANAGER_SETTING_WEBVIEW_DEBUG = "webview_debug"
private const val MANAGER_TOOL_SELINUX_MODE = "selinux_mode"
private const val MANAGER_TOOL_BACKUP_ALLOWLIST = "backup_allowlist"
private const val MANAGER_TOOL_RESTORE_ALLOWLIST = "restore_allowlist"

private data class ManagerSettingsLoad(
    val backend: String? = null,
    val title: String = "",
    val items: List<ManagerSettingItem> = emptyList(),
    val error: String? = null
)

private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

private fun workflowFileFor(config: KernelBuildConfig): String =
    if (KernelSupport.normalizeBuildTarget(config.buildTarget) == BUILD_TARGET_ONEPLUS) {
        ONEPLUS_WORKFLOW_FILE
    } else {
        KERNEL_WORKFLOW_FILE
    }

private fun workflowActionsUrl(owner: String, repoName: String, workflowFile: String = KERNEL_WORKFLOW_FILE): String =
    "https://github.com/$owner/$repoName/actions/workflows/$workflowFile"
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

private fun Artifact.toBuildArtifact(runId: Long, runTitle: String): BuildArtifact = BuildArtifact(
    id = id,
    name = name,
    sizeInBytes = sizeInBytes,
    archiveDownloadUrl = archiveDownloadUrl,
    expired = expired,
    createdAt = createdAt,
    runId = runId,
    runTitle = runTitle,
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

internal fun List<ActiveDownloadTask>.sortedDownloadTasks(): List<ActiveDownloadTask> =
    sortedWith(
        compareByDescending<ActiveDownloadTask> { it.runNumber }
            .thenByDescending { it.runId }
            .thenBy { it.name }
    )

internal fun BuildArtifact.toActiveDownloadTask(automatic: Boolean): ActiveDownloadTask =
    ActiveDownloadTask(
        key = id,
        artifactId = id,
        runId = runId,
        name = name,
        runTitle = runTitle,
        runNumber = runNumber,
        progress = 0,
        automatic = automatic
    )

internal fun MainUiState.withDownloadState(
    error: String? = this.error,
    downloadedArtifacts: List<DownloadedArtifact> = this.downloadedArtifacts,
    downloadProgress: Map<Long, Int> = this.downloadProgress,
    activeDownloadTasks: List<ActiveDownloadTask> = this.activeDownloadTasks
): MainUiState = copy(
    error = error,
    downloadedArtifacts = downloadedArtifacts,
    downloadProgress = downloadProgress,
    activeDownloadTasks = activeDownloadTasks,
    isDownloading = downloadProgress.isNotEmpty() || activeDownloadTasks.isNotEmpty()
)

private data class Quintuple<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

private data class ThemePreferences(
    val themeMode: String,
    val dynamicColorEnabled: Boolean,
    val customThemeColorArgb: Int?,
    val customAccentColorArgb: Int?
)

private data class BackgroundPreferences(
    val uri: String?,
    val enabled: Boolean,
    val alpha: Float
)

private operator fun <A, B, C, D> Quadruple<A, B, C, D>.component1() = a
private operator fun <A, B, C, D> Quadruple<A, B, C, D>.component2() = b
private operator fun <A, B, C, D> Quadruple<A, B, C, D>.component3() = c
private operator fun <A, B, C, D> Quadruple<A, B, C, D>.component4() = d
private operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component1() = a
private operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component2() = b
private operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component3() = c
private operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component4() = d
private operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component5() = e
