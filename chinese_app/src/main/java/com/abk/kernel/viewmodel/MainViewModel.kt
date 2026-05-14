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

enum class AuthStep { CHECK_ROOT, LOGIN, FORK_CHECK, READY }

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
    val buildPlans: List<BuildPlan> = emptyList(),
    val moduleCatalogRepositories: List<ModuleCatalogRepository> = emptyList(),
    val refreshingModuleCatalogRepositoryIds: Set<String> = emptySet(),
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
    val downloadMirrorBaseUrl: String = "",
    val prebuiltGkiEnabled: Boolean = true,
    val predictiveBackEnabled: Boolean = true,
    val runtimeNavigationEnabled: Boolean = false,
    val abkRuntimeStatus: AbkRuntimeStatus? = null,
    val abkRuntimeLoading: Boolean = false,
    val abkRuntimeError: String? = null,
    val abkRuntimeModuleActionId: String? = null,
    val abkRuntimeModuleActionTitle: String? = null,
    val abkRuntimeModuleActionOutput: List<String> = emptyList(),
    val rootGrantApps: List<RootGrantApp> = emptyList(),
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
            prefs.moduleCatalogRepositoriesJson.collect { json ->
                _uiState.update { it.copy(moduleCatalogRepositories = parseModuleCatalogRepositories(json)) }
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

    fun setRuntimeNavigationEnabled(enabled: Boolean) {
        _uiState.update { it.copy(runtimeNavigationEnabled = enabled) }
        viewModelScope.launch { prefs.setRuntimeNavigationEnabled(enabled) }
        if (enabled) refreshAbkRuntimeStatus()
    }

    fun refreshAbkRuntimeStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(abkRuntimeLoading = true, abkRuntimeError = null) }
            val (runtimeStatus, runtimeError) = withContext(Dispatchers.IO) {
                if (!RootUtils.isNativeManagerActive()) {
                    RootUtils.refreshRootState()
                }
                val snapshot = RootUtils.readManagerRuntimeSnapshot()
                if (!snapshot.manager.active) {
                    null to snapshot.manager.diagnostics.firstOrNull()
                } else {
                    mergeRuntimeStatus(
                        manager = snapshot.manager,
                        controlJson = snapshot.controlStatusJson,
                        ksuModulesJson = snapshot.ksuModulesJson
                    ) to null
                }
            }
            _uiState.update {
                if (runtimeStatus != null) {
                    it.copy(
                        rootGranted = true,
                        abkRuntimeStatus = runtimeStatus,
                        abkRuntimeLoading = false,
                        abkRuntimeError = null
                    )
                } else {
                    it.copy(
                        abkRuntimeStatus = null,
                        abkRuntimeLoading = false,
                        abkRuntimeError = runtimeError ?: "管理器未激活"
                    )
                }
            }
        }
    }

    fun refreshRootGrantApps() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(rootGrantLoading = true, rootGrantError = null)
            }
            val (active, apps, diagnostic) = withContext(Dispatchers.IO) {
                val nativeActive = RootUtils.isNativeManagerActive()
                val rootGrantApps = if (nativeActive) {
                    RootUtils.listRootGrantApps(getApplication<Application>())
                } else {
                    emptyList()
                }
                val inactiveDiagnostic = if (nativeActive) {
                    null
                } else {
                    RootUtils.refreshRootState()
                    RootUtils.readManagerRuntimeSnapshot().manager.diagnostics.firstOrNull()
                }
                Triple(nativeActive, rootGrantApps, inactiveDiagnostic)
            }
            _uiState.update {
                if (!active) {
                    it.copy(
                        rootGrantApps = emptyList(),
                        rootGrantLoading = false,
                        rootGrantError = diagnostic ?: "管理器未激活"
                    )
                } else {
                    it.copy(
                        rootGrantApps = apps,
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
            val result = withContext(Dispatchers.IO) {
                RootUtils.setRootGrantProfile(profile.copy(name = cleanPackage))
            }
            _uiState.update { state ->
                if (result) {
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
                        rootGrantError = "保存失败"
                    )
                }
            }
            if (result) refreshRootGrantApps()
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
            schema = maxOf(controlStatus?.schema ?: 0, 3),
            abkVersion = controlStatus?.abkVersion?.ifBlank { BuildConfig.VERSION_NAME } ?: BuildConfig.VERSION_NAME,
            manager = managerInfo,
            runtimeBackend = runtimeBackendInfo,
            modules = mergedModules
        )
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
                _uiState.update { it.copy(abkRuntimeError = "操作未完成") }
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
                if (module?.isKsuBacked() == true) {
                    RootUtils.setKsuModuleEnabled(cleanId, enabled)
                } else {
                    val command = if (enabled) "enable $cleanId" else "disable $cleanId"
                    RootUtils.writeAbkControlCommand(command)
                }
            }
            if (!result.success) {
                _uiState.update {
                    it.copy(
                        abkRuntimeModuleActionId = null,
                        abkRuntimeError = "操作未完成"
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
        if (cleanId.isBlank() || !module.actionSupported || _uiState.value.abkRuntimeModuleActionId != null) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    abkRuntimeModuleActionId = cleanId,
                    abkRuntimeModuleActionTitle = "${module.displayNameForRuntime()} Action",
                    abkRuntimeModuleActionOutput = emptyList(),
                    abkRuntimeError = null
                )
            }
            val result = RootUtils.runKsuModuleAction(cleanId) { line ->
                _uiState.update { state ->
                    state.copy(abkRuntimeModuleActionOutput = state.abkRuntimeModuleActionOutput + line)
                }
            }
            _uiState.update { state ->
                val output = state.abkRuntimeModuleActionOutput.ifEmpty { result.output }
                state.copy(
                    abkRuntimeModuleActionId = null,
                    abkRuntimeModuleActionOutput = output,
                    abkRuntimeError = if (result.success) null else "操作未完成"
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
        name.ifBlank { id.ifBlank { "模块" } }

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
                    dynamicColorEnabled = it.dynamicColorEnabled,
                    customThemeColorArgb = it.customThemeColorArgb,
                    customAccentColorArgb = it.customAccentColorArgb,
                    customBackgroundUri = it.customBackgroundUri,
                    backgroundImageEnabled = it.backgroundImageEnabled,
                    uiSurfaceAlpha = it.uiSurfaceAlpha,
                    downloadMirrorBaseUrl = it.downloadMirrorBaseUrl,
                    prebuiltGkiEnabled = it.prebuiltGkiEnabled,
                    predictiveBackEnabled = it.predictiveBackEnabled,
                    moduleCatalogRepositories = it.moduleCatalogRepositories
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

    fun loadPrebuiltGkiReleases(force: Boolean = false) {
        val state = _uiState.value
        if (!state.prebuiltGkiEnabled || !state.isLoggedIn) return
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
                    it.copy(isLoadingPrebuiltGkiReleases = false, error = "获取预编译 GKI Release 失败: ${result.message}")
                }
                else -> _uiState.update { it.copy(isLoadingPrebuiltGkiReleases = false) }
            }
        }
    }

    fun loadPrebuiltGkiAssets(release: PrebuiltGkiRelease, force: Boolean = false) {
        val state = _uiState.value
        if (!state.prebuiltGkiEnabled || !state.isLoggedIn) return
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
                        error = "获取 ${release.name} 资产失败: ${result.message}"
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
        artifactDownloadJobs[key]?.cancel()
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
                val updatedParameterSummaries = _uiState.value.buildParameterSummaries - runId

                _uiState.update { state ->
                    state.copy(
                        downloadedArtifacts = updatedDownloads,
                        artifacts = updatedRemote,
                        buildParameterSummaries = updatedParameterSummaries,
                        loadingBuildParameterRunIds = state.loadingBuildParameterRunIds - runId,
                        buildParameterErrors = state.buildParameterErrors - runId,
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
                prefs.saveBuildParameterSummariesJson(gson.toJson(updatedParameterSummaries.values.sortedByDescending { it.runNumber }))
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

    private suspend fun downloadPrebuiltGkiNow(asset: PrebuiltGkiAsset, progressKey: Long) {
        if (!_uiState.value.prebuiltGkiEnabled) return
        val token = prefs.accessToken.first()
        _uiState.update {
            it.copy(
                isDownloading = true,
                error = null,
                downloadProgress = it.downloadProgress + (progressKey to 0)
            )
        }
        NotificationUtils.notifyDownloadProgress(getApplication(), 0, asset.name)
        val results = DownloadUtils.downloadDirectAsset(
            getApplication(),
            token,
            asset.browserDownloadUrl,
            asset.name,
            asset.sizeBytes,
            PREBUILT_GKI_RUN_ID,
            "预编译 GKI"
        ) { pct ->
            NotificationUtils.notifyDownloadProgress(getApplication(), pct, asset.name)
            _uiState.update { s ->
                s.copy(downloadProgress = s.downloadProgress + (progressKey to pct))
            }
        }
        if (!_uiState.value.prebuiltGkiEnabled) {
            _uiState.update { it.copy(isDownloading = false, downloadProgress = it.downloadProgress - progressKey) }
            return
        }
        if (results.isNotEmpty()) {
            NotificationUtils.notifyDownloadDone(getApplication(), asset.name)
            val updated = (_uiState.value.downloadedArtifacts + results)
                .distinctBy { it.filePath }
                .sortedDownloadedForDisplay()
            _uiState.update { s ->
                s.copy(
                    isDownloading = false,
                    error = null,
                    downloadedArtifacts = updated,
                    downloadProgress = s.downloadProgress - progressKey
                )
            }
            prefs.saveDownloadedArtifactsJson(gson.toJson(updated))
        } else {
            finishArtifactDownloadWithError(progressKey, "下载预编译 GKI 失败: ${asset.name}")
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
            )
        }
        prefs.setPrebuiltGkiEnabled(v)
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
                scope = scope
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
            "旧版 ABKP1 方案码太长，已不再支持，请重新分享"
        }
        require(compact.startsWith(BUILD_PLAN_CODE_PREFIX)) { "方案码格式不正确" }
        val payload = compact.removePrefix(BUILD_PLAN_CODE_PREFIX)
        require(payload.isNotBlank()) { "方案码为空" }
        val decoded = decodeBuildPlanPayload(
            bytes = Base64.getUrlDecoder().decode(padBase64Url(payload)),
            baseConfig = KernelSupport.normalize(baseConfig)
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

    fun addModuleCatalogRepository(url: String) {
        val cleanUrl = normalizeModuleCatalogUrl(url)
        if (cleanUrl.isBlank()) {
            _uiState.update { it.copy(error = "模块仓库链接不能为空") }
            return
        }

        val current = _uiState.value.moduleCatalogRepositories
        val existing = current.firstOrNull { it.url.equals(cleanUrl, ignoreCase = true) }
        if (existing != null) {
            refreshModuleCatalogRepository(existing.id)
            return
        }

        val repository = ModuleCatalogRepository(
            id = UUID.randomUUID().toString(),
            url = cleanUrl,
            name = cleanUrl.moduleCatalogFallbackName()
        )
        saveModuleCatalogRepositories(current + repository)
        refreshModuleCatalogRepository(repository.id)
    }

    fun deleteModuleCatalogRepository(id: String) {
        saveModuleCatalogRepositories(_uiState.value.moduleCatalogRepositories.filterNot { it.id == id })
    }

    fun refreshModuleCatalogRepository(id: String) {
        val repository = _uiState.value.moduleCatalogRepositories.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(refreshingModuleCatalogRepositoryIds = it.refreshingModuleCatalogRepositoryIds + id)
            }
            when (val result = github.fetchModuleCatalog(repository.url)) {
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
                    saveModuleCatalogRepositories(
                        _uiState.value.moduleCatalogRepositories.map {
                            if (it.id == id) updated else it
                        }
                    )
                }
                is Result.Error -> {
                    val updated = repository.copy(error = result.message)
                    saveModuleCatalogRepositories(
                        _uiState.value.moduleCatalogRepositories.map {
                            if (it.id == id) updated else it
                        }
                    )
                }
                Result.Loading -> Unit
            }
            _uiState.update {
                it.copy(refreshingModuleCatalogRepositoryIds = it.refreshingModuleCatalogRepositoryIds - id)
            }
        }
    }

    fun refreshAllModuleCatalogRepositories() {
        _uiState.value.moduleCatalogRepositories.forEach { repository ->
            refreshModuleCatalogRepository(repository.id)
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

    suspend fun checkCustomExternalModuleMetadata(url: String): ExternalModuleMetadata? {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) {
            _uiState.update { it.copy(customExternalModuleError = "模块仓库链接不能为空") }
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
            _uiState.update { it.copy(customExternalModuleError = "模块仓库链接不能为空") }
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

    suspend fun addCustomExternalModuleFromUrl(url: String, stage: String): Boolean {
        val metadata = checkCustomExternalModuleMetadata(url) ?: return false
        val normalizedStage = CustomExternalModuleStage.normalize(stage)
        return if (normalizedStage in metadata.supportedStages) {
            addCustomExternalModulesFromUrl(url, listOf(normalizedStage))
        } else {
            _uiState.update { it.copy(customExternalModuleError = "该模块不支持 $normalizedStage") }
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

    private fun saveModuleCatalogRepositories(repositories: List<ModuleCatalogRepository>) {
        val sanitized = sanitizeModuleCatalogRepositories(repositories)
        _uiState.update { it.copy(moduleCatalogRepositories = sanitized) }
        viewModelScope.launch { prefs.saveModuleCatalogRepositoriesJson(gson.toJson(sanitized)) }
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
                    if (it == null) firstFailure = "未找到“$BUILD_SUMMARY_STEP_NAME”步骤"
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
                        val summary = parseBuildParameterSummary(logsResult.data, runId, run)
                        if (summary != null) {
                            saveBuildParameterSummary(runId, summary)
                            return@launch
                        }
                        firstFailure = "job 日志中没有可解析的构建信息摘要"
                    }
                    is Result.Error -> firstFailure = logsResult.message
                    Result.Loading -> Unit
                }
            }

            when (val runLogsResult = github.downloadRunLogs(username, repoName, runId)) {
                is Result.Success -> {
                    val summary = parseBuildParameterSummary(runLogsResult.data, runId, run)
                    if (summary != null) {
                        saveBuildParameterSummary(runId, summary)
                        return@launch
                    }
                    val prefix = firstFailure?.let { "$it；" }.orEmpty()
                    setBuildParameterLoadError(runId, "${prefix}workflow 日志中没有可解析的构建信息摘要")
                }
                is Result.Error -> {
                    val prefix = firstFailure?.let { "job 日志读取失败：$it；" }.orEmpty()
                    setBuildParameterLoadError(runId, "${prefix}workflow 日志读取失败：${runLogsResult.message}")
                }
                Result.Loading -> setBuildParameterLoadError(runId, "日志读取尚未完成")
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

    private fun parseModuleCatalogRepositories(json: String?): List<ModuleCatalogRepository> {
        if (json.isNullOrBlank()) return defaultModuleCatalogRepositories()
        return runCatching<List<ModuleCatalogRepository>> {
            val type = object : TypeToken<List<ModuleCatalogRepository>>() {}.type
            sanitizeModuleCatalogRepositories(
                gson.fromJson<List<ModuleCatalogRepository>>(json, type).orEmpty()
            )
        }.getOrDefault(defaultModuleCatalogRepositories())
    }

    private fun sanitizeModuleCatalogRepositories(
        repositories: List<ModuleCatalogRepository>
    ): List<ModuleCatalogRepository> {
        return repositories
            .mapNotNull { repository ->
                val url = normalizeModuleCatalogUrl(repository.url)
                if (url.isBlank()) return@mapNotNull null
                val modules = repository.modules
                    .mapNotNull(::sanitizeModuleCatalogItem)
                    .distinctBy { it.repoUrl.trim().lowercase() }
                    .sortedBy { it.name.lowercase() }
                repository.copy(
                    id = repository.id.ifBlank { UUID.randomUUID().toString() },
                    url = url,
                    indexJsonUrl = repository.indexJsonUrl.trim(),
                    name = repository.name.trim().ifBlank { url.moduleCatalogFallbackName() },
                    modules = modules,
                    lastUpdated = repository.lastUpdated.takeIf { it > 0L } ?: 0L,
                    error = repository.error?.takeIf { it.isNotBlank() },
                    skippedCount = repository.skippedCount.coerceAtLeast(0)
                )
            }
            .distinctBy { it.url.lowercase() }
            .sortedWith(compareByDescending<ModuleCatalogRepository> {
                if (it.url == OFFICIAL_MODULE_CATALOG_URL) 1 else 0
            }
                .thenBy { it.name.lowercase() })
    }

    private fun sanitizeModuleCatalogItem(item: ModuleCatalogItem): ModuleCatalogItem? {
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
            name = item.name.trim().ifBlank { repoUrl.moduleCatalogFallbackName() },
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

    private fun defaultModuleCatalogRepositories(): List<ModuleCatalogRepository> = listOf(
        ModuleCatalogRepository(
            id = OFFICIAL_MODULE_CATALOG_ID,
            url = OFFICIAL_MODULE_CATALOG_URL,
            name = "ABK 官方模块仓库"
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

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun clearCustomExternalModuleError() = _uiState.update { it.copy(customExternalModuleError = null) }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(statusReceiver) }
        super.onCleared()
    }
}

private fun sanitizeBuildPlanName(name: String, config: KernelBuildConfig): String =
    name.trim().ifBlank { defaultBuildPlanName(config) }.take(BUILD_PLAN_NAME_LIMIT)

private fun defaultBuildPlanName(config: KernelBuildConfig): String {
    val android = config.androidVersion.removePrefix("android").ifBlank { config.androidVersion }
    return listOf("${config.kernelVersion}.${config.subLevel}", "Android $android", config.kernelsuVariant)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}

private fun normalizeModuleCatalogUrl(url: String): String = url.trim().trimEnd('/')

private fun String.moduleCatalogFallbackName(): String = trim()
    .trimEnd('/')
    .substringAfterLast('/')
    .removeSuffix(".git")
    .removeSuffix(".json")
    .ifBlank { "模块仓库" }

private fun padBase64Url(value: String): String =
    value + "=".repeat((4 - value.length % 4) % 4)

private data class DecodedBuildPlanCode(
    val name: String,
    val config: KernelBuildConfig,
    val scope: BuildPlanShareScope
)

private fun encodeBuildPlanPayload(
    config: KernelBuildConfig,
    name: String,
    scope: BuildPlanShareScope
): ByteArray {
    val writer = BuildPlanBinaryWriter()
    writer.writeByte(BUILD_PLAN_CODE_VERSION)
    writer.writeByte(scope.toWireValue())
    writer.writeString(name)
    if (scope == BuildPlanShareScope.FULL) {
        writer.writeString(config.androidVersion)
        writer.writeString(config.kernelVersion)
        writer.writeString(config.subLevel)
        writer.writeString(config.osPatchLevel)
        writer.writeString(config.revision)
    }
    writer.writeByte(BUILD_PLAN_KSU_VARIANTS.indexOrZero(config.kernelsuVariant))
    writer.writeByte(BUILD_PLAN_KSU_BRANCHES.indexOrZero(config.kernelsuBranch))
    writer.writeByte(BUILD_PLAN_VIRTUALIZATION_OPTIONS.indexOrZero(config.virtualizationSupport))
    writer.writeVarInt(config.toBuildPlanFeatureMask())
    writer.writeString(config.version)
    writer.writeString(config.buildTime)
    writer.writeString(config.zramExtraAlgos)
    writer.writeString(config.kpmPassword)
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

private fun decodeBuildPlanPayload(bytes: ByteArray, baseConfig: KernelBuildConfig): DecodedBuildPlanCode {
    val reader = BuildPlanBinaryReader(bytes)
    val version = reader.readByte()
    require(version == BUILD_PLAN_CODE_VERSION) { "不支持的方案码版本" }
    val scope = buildPlanShareScopeFromWireValue(reader.readByte())
    val name = reader.readString()
    val versionBase = if (scope == BuildPlanShareScope.FULL) {
        baseConfig.copy(
            androidVersion = reader.readString(),
            kernelVersion = reader.readString(),
            subLevel = reader.readString(),
            osPatchLevel = reader.readString(),
            revision = reader.readString()
        )
    } else {
        baseConfig
    }
    val ksuVariant = BUILD_PLAN_KSU_VARIANTS.valueOrDefault(reader.readByte(), versionBase.kernelsuVariant)
    val ksuBranch = BUILD_PLAN_KSU_BRANCHES.valueOrDefault(reader.readByte(), versionBase.kernelsuBranch)
    val virtualizationSupport = BUILD_PLAN_VIRTUALIZATION_OPTIONS.valueOrDefault(
        reader.readByte(),
        versionBase.virtualizationSupport
    )
    val featureMask = reader.readVarInt()
    val versionName = reader.readString()
    val buildTime = reader.readString()
    val zramExtraAlgos = reader.readString()
    val kpmPassword = reader.readString()
    val moduleCount = reader.readVarInt()
    require(moduleCount in 0..BUILD_PLAN_MAX_MODULES) { "外部模块数量超出限制" }
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
        virtualizationSupport = virtualizationSupport,
        useCustomExternalModules = featureMask.hasBuildPlanFlag(10),
        customExternalModules = modules
    )
    return DecodedBuildPlanCode(
        name = name,
        config = KernelSupport.normalize(merged),
        scope = scope
    )
}

private class BuildPlanBinaryWriter {
    private val output = ByteArrayOutputStream()

    fun writeByte(value: Int) {
        output.write(value and 0xff)
    }

    fun writeVarInt(value: Int) {
        require(value >= 0) { "负数无法写入方案码" }
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
        require(bytes.size <= BUILD_PLAN_MAX_STRING_BYTES) { "方案字段过长" }
        writeVarInt(bytes.size)
        output.write(bytes)
    }

    fun toByteArray(): ByteArray = output.toByteArray()
}

private class BuildPlanBinaryReader(private val bytes: ByteArray) {
    private var position = 0

    fun readByte(): Int {
        require(position < bytes.size) { "方案码内容不完整" }
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
        throw IllegalArgumentException("方案码数字字段异常")
    }

    fun readString(): String {
        val length = readVarInt()
        require(length in 0..BUILD_PLAN_MAX_STRING_BYTES) { "方案字段过长" }
        require(position + length <= bytes.size) { "方案码内容不完整" }
        val value = String(bytes, position, length, StandardCharsets.UTF_8)
        position += length
        return value
    }

    fun requireFullyRead() {
        require(position == bytes.size) { "方案码包含无法识别的数据" }
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
    return mask
}

private fun Int.hasBuildPlanFlag(bit: Int): Boolean = this and (1 shl bit) != 0

private fun List<String>.indexOrZero(value: String): Int =
    indexOf(value).takeIf { it >= 0 } ?: 0

private fun List<String>.valueOrDefault(index: Int, fallback: String): String =
    getOrNull(index) ?: fallback

private fun buildPlanShareScopeFromWireValue(value: Int): BuildPlanShareScope = when (value) {
    0 -> BuildPlanShareScope.FULL
    1 -> BuildPlanShareScope.FEATURES_ONLY
    else -> throw IllegalArgumentException("不支持的方案分享类型")
}

private const val BUILD_PLAN_CODE_PREFIX = "ABKP2:"
private const val BUILD_PLAN_LEGACY_CODE_PREFIX = "ABKP1:"
private const val BUILD_PLAN_CODE_VERSION = 2
private const val BUILD_PLAN_NAME_LIMIT = 80
private const val BUILD_PLAN_MAX_STRING_BYTES = 4096
private const val BUILD_PLAN_MAX_MODULES = 32
private const val OFFICIAL_MODULE_CATALOG_ID = "official-abk-module-catalog"
private const val OFFICIAL_MODULE_CATALOG_URL = "https://github.com/xingguangcuican6666/ABK_repo"

private val BUILD_PLAN_KSU_VARIANTS = listOf("Official", "SukiSU", "ReSukiSU")
private val BUILD_PLAN_KSU_BRANCHES = listOf("Stable(标准)", "Dev(开发)")
private val BUILD_PLAN_VIRTUALIZATION_OPTIONS = listOf("off", "on", "678", "123", "345")
private val BUILD_PLAN_MODULE_STAGES = listOf(
    CustomExternalModuleStage.AFTER_PATCH,
    CustomExternalModuleStage.BEFORE_BUILD
)

private const val BUILD_SUMMARY_STEP_NAME = "构建信息摘要"

private fun parseBuildParameterSummary(
    logs: String,
    runId: Long,
    run: WorkflowRun?
): BuildParameterSummary? {
    val values = mutableMapOf<String, String>()
    var summarySeen = false
    logs.lineSequence()
        .map(::cleanBuildSummaryLogLine)
        .forEach { line ->
            if (line.contains("内核构建配置摘要")) {
                summarySeen = true
                return@forEach
            }
            if (!summarySeen && !line.contains("Android 版本")) return@forEach
            if (summarySeen && values.isNotEmpty() && line.all { it == '=' || it.isWhitespace() }) return@forEach

            val separator = listOf(line.indexOf(':'), line.indexOf('：'))
                .filter { it >= 0 }
                .minOrNull() ?: return@forEach
            val label = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()
            val key = normalizeBuildSummaryLabel(label) ?: return@forEach
            values[key] = sanitizeBuildSummaryValue(key, value)
        }
    if (values.isEmpty()) return null

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
        stockConfig = values["stockConfig"].orEmpty()
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
        compact.contains("android版本") -> "androidVersion"
        compact.contains("内核版本") -> "kernelVersion"
        compact.contains("子版本号") -> "subLevel"
        compact.contains("补丁级别") -> "osPatchLevel"
        compact.contains("ksu变体") -> "ksuVariant"
        compact.contains("ksu分支") -> "ksuBranch"
        compact.contains("构建时间") -> "buildTime"
        compact.contains("susfs状态") -> "susfsEnabled"
        compact.contains("zram增强") -> "zramEnabled"
        compact.contains("zram完整算法") -> "zramFullAlgo"
        compact.contains("zram额外算法") -> "zramExtraAlgos"
        compact.contains("bbg补丁") -> "bbgEnabled"
        compact.contains("ddklsm") -> "ddkLsm"
        compact.contains("ntsync补丁") -> "ntsyncEnabled"
        compact.contains("networking增强") || compact.contains("networing增强") -> "networkingEnabled"
        compact.contains("kpm功能") -> "kpmEnabled"
        compact.contains("kpm密码") -> "kpmPassword"
        compact.contains("re-kernel") || compact.contains("rekernel") -> "reKernelEnabled"
        compact.contains("虚拟化支持") -> "virtualizationSupport"
        compact.contains("自定义注入") -> "customInjection"
        compact.contains("stockconfig") -> "stockConfig"
        else -> null
    }
}

private fun sanitizeBuildSummaryValue(key: String, value: String): String {
    if (key != "kpmPassword") return value.ifBlank { "无" }
    val normalized = value.trim().lowercase()
    return when {
        normalized.isBlank() -> "默认"
        normalized in setOf("默认", "default", "无", "none", "not set") -> "默认"
        else -> "已设置"
    }
}

private fun detectRecommendedBuildConfig(): KernelBuildConfig? {
    val kernelVersion = RootUtils.getKernelVersion()
    if (kernelVersion.isBlank() || kernelVersion.equals("Unknown", ignoreCase = true)) return null
    return KernelSupport.recommendedFromKernel(kernelVersion)
}

private fun prebuiltGkiReleaseFromGitHub(release: GitHubReleaseSummary): PrebuiltGkiRelease {
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

private fun prebuiltGkiAssetsFromReleaseAssets(
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

private fun prebuiltGkiReleaseComparator(): Comparator<PrebuiltGkiRelease> =
    compareByDescending<PrebuiltGkiRelease> { it.publishedAt }
        .thenBy { it.name }

private fun isPrebuiltGkiReleaseCandidate(release: GitHubReleaseSummary): Boolean {
    val haystack = listOf(release.tagName, release.name.orEmpty(), release.body.orEmpty())
        .joinToString(" ")
        .lowercase()
        .replace('_', '-')
    val strongPrebuiltTerms = listOf(
        "gki",
        "prebuilt",
        "pre-built",
        "预编译",
        "boot.img",
        "anykernel",
        "ak3",
        "kernel image",
        "内核镜像",
        "刷写包"
    )
    if (strongPrebuiltTerms.any { haystack.contains(it) }) return true

    val appReleaseTerms = listOf(
        ".apk",
        "apk",
        "app",
        "android application",
        "应用",
        "客户端",
        "abk"
    )
    return appReleaseTerms.none { haystack.contains(it) } &&
        listOf("boot-", "boot_", "image", "img").any { haystack.contains(it) }
}

private fun isPrebuiltGkiCandidate(asset: PrebuiltGkiAsset): Boolean {
    val lower = asset.name.lowercase()
    val type = DownloadUtils.classifyArtifact(asset.name)
    return type in setOf(ArtifactType.KERNEL_PACKAGE, ArtifactType.KERNEL_IMG, ArtifactType.ANYKERNEL3) ||
        ((lower.endsWith(".img") || lower.endsWith(".zip")) &&
            listOf("gki", "kernel", "boot", "anykernel", "ak3").any { lower.contains(it) })
}

private fun prebuiltGkiComparator(
    recommended: KernelBuildConfig?
): Comparator<PrebuiltGkiAsset> =
    compareByDescending<PrebuiltGkiAsset> { prebuiltRecommendationScore(it, recommended) }
        .thenByDescending { it.publishedAt }
        .thenBy { it.name }

private fun prebuiltRecommendationScore(asset: PrebuiltGkiAsset, recommended: KernelBuildConfig?): Int {
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
    "use_ntsync" to useNtsync.toString(),
    "use_networking" to useNetworking.toString(),
    "use_kpm" to useKpm.toString(),
    "use_rekernel" to useRekernel.toString(),
    "cancel_susfs" to cancelSusfs.toString(),
    "supp_op" to suppOp.toString(),
    "zram_full_algo" to zramFullAlgo.toString(),
    "zram_extra_algos" to zramExtraAlgos,
    "kpm_password" to kpmPassword,
    "virtualization_support" to virtualizationSupport,
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

private operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component1() = a
private operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component2() = b
private operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component3() = c
private operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component4() = d
private operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component5() = e
