package com.abk.kernel.viewmodel

import android.app.Application
import androidx.annotation.StringRes
import com.abk.kernel.R
import com.abk.kernel.data.model.*
import com.abk.kernel.data.repository.GitHubRepository
import com.abk.kernel.data.repository.PreferencesRepository
import com.abk.kernel.data.repository.Result
import com.abk.kernel.utils.LocaleHelper
import com.abk.kernel.utils.RootUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val OFFICIAL_RUNTIME_MODULE_REPOSITORY_ID = "official-runtime-module-repository"
private const val OFFICIAL_RUNTIME_MODULE_REPOSITORY_URL =
    "https://raw.githubusercontent.com/Magisk-Modules-Alt-Repo/json-v2/refs/heads/main/json/modules.json"

private data class RuntimeQuadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

class RuntimeCoordinator(
    private val scope: CoroutineScope,
    private val app: Application,
    private val github: GitHubRepository,
    private val prefs: PreferencesRepository,
    private val gson: Gson,
    private val ksuModuleListType: Type,
    private val readState: () -> MainUiState,
    private val updateState: ((MainUiState) -> MainUiState) -> Unit,
    private val resolveManagerAccess: (Boolean) -> RootUtils.ManagerAccessInfo,
    private val managerAccessErrorMessage: (RootUtils.ManagerAccessInfo, Boolean) -> String,
    private val str: (Int, Array<out Any>) -> String,
) {
    private fun text(@StringRes resId: Int, vararg args: Any): String =
        if (args.isEmpty()) str(resId, emptyArray()) else str(resId, args)

    private fun localizedRuntimeModuleRepoTitle(): String =
        when (LocaleHelper.getLanguage(app)) {
            LocaleHelper.LANG_ZH -> "普通模块仓库"
            LocaleHelper.LANG_RU -> "Репозиторий обычных модулей"
            else -> "Standard Module Repo"
        }

    fun onRuntimeRepositoriesJsonChanged(json: String?) {
        val repositories = parseRuntimeModuleRepositories(json)
        updateState { it.copy(runtimeModuleRepositories = repositories) }
        refreshStaleRuntimeModuleRepositories(repositories)
    }

    fun setRuntimeNavigationEnabled(enabled: Boolean) {
        updateState { it.copy(runtimeNavigationEnabled = enabled) }
        scope.launch { prefs.setRuntimeNavigationEnabled(enabled) }
        if (enabled) refreshAbkRuntimeStatus()
    }
    fun refreshAbkRuntimeStatus() {
        scope.launch {
            updateState { it.copy(abkRuntimeLoading = true, abkRuntimeError = null) }
            val rootGranted = readState().rootGranted
            val (access, runtimeStatus, runtimeError) = withContext(Dispatchers.IO) {
                val managerAccess = resolveManagerAccess(rootGranted)
                if (!managerAccess.hasNativeManagerPermission) {
                    val snapshot = if (rootGranted) RootUtils.readManagerRuntimeSnapshot() else null
                    val compatStatus = snapshot
                        ?.takeIf { it.manager.active }
                        ?.let {
                            mergeRuntimeStatus(
                                gson,
                                ksuModuleListType,
                                manager = it.manager,
                                controlJson = it.controlStatusJson,
                                ksuModulesJson = it.ksuModulesJson,
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
                            gson,
                            ksuModuleListType,
                            manager = snapshot.manager,
                            controlJson = snapshot.controlStatusJson,
                            ksuModulesJson = snapshot.ksuModulesJson,
                        ),
                        null as String?
                    )
                }
            }
            updateState {
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
        val current = readState()
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

        scope.launch {
            val backendAtRequest = readState().abkRuntimeStatus?.runtimeBackend?.backend
            val rootGranted = readState().rootGranted
            updateState {
                it.copy(rootGrantLoading = true, rootGrantError = null)
            }
            val (access, active, apps, diagnostic) = withContext(Dispatchers.IO) {
                val managerAccess = resolveManagerAccess(rootGranted)
                if (!managerAccess.hasNativeManagerPermission) {
                    return@withContext RuntimeQuadruple(
                        managerAccess,
                        false,
                        emptyList<RootGrantApp>(),
                        managerAccessErrorMessage(managerAccess, rootGranted)
                    )
                }
                val rootGrantApps = if (managerAccess.hasNativeManagerPermission) {
                    RootUtils.listRootGrantApps(app)
                } else {
                    emptyList()
                }
                RuntimeQuadruple(managerAccess, true, rootGrantApps, null as String?)
            }
            updateState {
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
        val app = readState().rootGrantApps.firstOrNull { it.packageName == packageName } ?: return
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
        if (cleanPackage.isBlank() || readState().rootGrantSavingPackage != null) return

        scope.launch {
            updateState {
                it.copy(rootGrantSavingPackage = cleanPackage, rootGrantError = null)
            }
            val rootGranted = readState().rootGranted
            val result = withContext(Dispatchers.IO) {
                val access = resolveManagerAccess(rootGranted)
                if (!access.hasNativeManagerPermission) {
                    false to managerAccessErrorMessage(access, rootGranted)
                } else {
                    RootUtils.setRootGrantProfile(profile.copy(name = cleanPackage)) to null
                }
            }
            updateState { state ->
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


    fun setAbkRuntimeModuleEnabled(moduleId: String, enabled: Boolean) {
        val cleanId = moduleId.trim()
        if (cleanId.isBlank() || readState().abkRuntimeModuleActionId != null) return

        scope.launch {
            val hasRoot = readState().rootGranted || withContext(Dispatchers.IO) {
                RootUtils.refreshRootState()
            }
            if (!hasRoot) {
                updateState { it.copy(abkRuntimeError = text(R.string.settings_operation_incomplete)) }
                return@launch
            }
            val module = readState().abkRuntimeStatus?.modules?.firstOrNull { it.id == cleanId }
            updateState {
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
                updateState {
                    it.copy(
                        abkRuntimeModuleActionId = null,
                        abkRuntimeError = text(R.string.settings_operation_incomplete)
                    )
                }
            } else {
                updateState { it.copy(abkRuntimeModuleActionId = null) }
                refreshAbkRuntimeStatus()
            }
        }
    }

    fun setAbkRuntimeModulePendingUninstall(moduleId: String, pending: Boolean) {
        val cleanId = moduleId.trim()
        if (cleanId.isBlank() || readState().abkRuntimeModuleActionId != null) return

        scope.launch {
            val hasRoot = readState().rootGranted || withContext(Dispatchers.IO) {
                RootUtils.refreshRootState()
            }
            if (!hasRoot) {
                updateState { it.copy(abkRuntimeError = text(R.string.settings_operation_incomplete)) }
                return@launch
            }
            val module = readState().abkRuntimeStatus?.modules?.firstOrNull { it.id == cleanId }
            if (module?.isKsuBacked() != true) {
                updateState { it.copy(abkRuntimeError = text(R.string.vm_runtime_module_uninstall_unsupported)) }
                return@launch
            }
            updateState {
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
                updateState {
                    it.copy(
                        abkRuntimeModuleActionId = null,
                        abkRuntimeError = text(R.string.settings_operation_incomplete)
                    )
                }
            } else {
                updateState { it.copy(abkRuntimeModuleActionId = null) }
                refreshAbkRuntimeStatus()
            }
        }
    }

    fun runRuntimeModuleAction(moduleId: String) {
        val cleanId = moduleId.trim()
        val module = readState().abkRuntimeStatus?.modules?.firstOrNull { it.id == cleanId } ?: return
        if (cleanId.isBlank() || (!module.actionSupported && !module.hasActionScript) || readState().abkRuntimeModuleActionId != null) return
        scope.launch(Dispatchers.IO) {
            updateState {
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
                        updateState { state ->
                            state.copy(abkRuntimeModuleActionOutput = state.abkRuntimeModuleActionOutput + line)
                        }
                    }
                }
                RuntimeModuleActionBackend.KSU_ACTION -> {
                    RootUtils.runKsuModuleAction(cleanId) { line ->
                        updateState { state ->
                            state.copy(abkRuntimeModuleActionOutput = state.abkRuntimeModuleActionOutput + line)
                        }
                    }
                }
                RuntimeModuleActionBackend.NONE -> {
                    RootUtils.runModuleActionScript(
                        module.moduleDir.ifBlank { "/data/adb/modules/$cleanId" }
                    ) { line ->
                        updateState { state ->
                            state.copy(abkRuntimeModuleActionOutput = state.abkRuntimeModuleActionOutput + line)
                        }
                    }
                }
            }
            updateState { state ->
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
        updateState {
            it.copy(
                abkRuntimeModuleActionTitle = null,
                abkRuntimeModuleActionOutput = emptyList()
            )
        }
    }
    fun addRuntimeModuleRepository(url: String) {
        val cleanUrl = normalizeModuleCatalogUrl(url)
        if (cleanUrl.isBlank()) {
            updateState { it.copy(error = text(R.string.vm_module_repo_url_empty)) }
            return
        }

        val current = readState().runtimeModuleRepositories
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
        saveRuntimeModuleRepositories(readState().runtimeModuleRepositories.filterNot { it.id == id })
    }

    fun refreshRuntimeModuleRepository(id: String) {
        val repository = readState().runtimeModuleRepositories.firstOrNull { it.id == id } ?: return
        scope.launch {
            updateState {
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
                        readState().runtimeModuleRepositories.map {
                            if (it.id == id) updated else it
                        }
                    )
                }
                is Result.Error -> {
                    val updated = repository.copy(error = result.message)
                    saveRuntimeModuleRepositories(
                        readState().runtimeModuleRepositories.map {
                            if (it.id == id) updated else it
                        }
                    )
                }
                Result.Loading -> Unit
            }
            updateState {
                it.copy(refreshingRuntimeModuleRepositoryIds = it.refreshingRuntimeModuleRepositoryIds - id)
            }
        }
    }

    fun refreshAllRuntimeModuleRepositories() {
        readState().runtimeModuleRepositories.forEach { repository ->
            refreshRuntimeModuleRepository(repository.id)
        }
    }

    fun refreshStaleRuntimeModuleRepositories(repositories: List<RuntimeModuleRepository>) {
        repositories
            .filter { it.lastUpdated <= 0L && it.error == null }
            .forEach { repository -> refreshRuntimeModuleRepository(repository.id) }
    }
    fun saveRuntimeModuleRepositories(repositories: List<RuntimeModuleRepository>) {
        val sanitized = sanitizeRuntimeModuleRepositories(repositories)
        updateState { it.copy(runtimeModuleRepositories = sanitized) }
        scope.launch { prefs.saveRuntimeModuleRepositoriesJson(gson.toJson(sanitized)) }
    }
    fun parseRuntimeModuleRepositories(json: String?): List<RuntimeModuleRepository> {
        if (json.isNullOrBlank()) return defaultRuntimeModuleRepositories()
        return runCatching<List<RuntimeModuleRepository>> {
            val type = object : TypeToken<List<RuntimeModuleRepository>>() {}.type
            sanitizeRuntimeModuleRepositories(
                gson.fromJson<List<RuntimeModuleRepository>>(json, type).orEmpty()
            )
        }.getOrDefault(defaultRuntimeModuleRepositories())
    }

    fun sanitizeRuntimeModuleRepositories(
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

    fun sanitizeRuntimeModuleCatalogItem(item: RuntimeModuleCatalogItem): RuntimeModuleCatalogItem? {
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

    fun defaultRuntimeModuleRepositories(): List<RuntimeModuleRepository> = listOf(
        RuntimeModuleRepository(
            id = OFFICIAL_RUNTIME_MODULE_REPOSITORY_ID,
            url = OFFICIAL_RUNTIME_MODULE_REPOSITORY_URL,
            name = localizedRuntimeModuleRepoTitle()
        )
    )

    private fun AbkRuntimeModule.displayNameForRuntime(): String =
        name.ifBlank { id.ifBlank { text(R.string.vm_runtime_module_default_name) } }
}

private operator fun <A, B, C, D> RuntimeQuadruple<A, B, C, D>.component1() = first
private operator fun <A, B, C, D> RuntimeQuadruple<A, B, C, D>.component2() = second
private operator fun <A, B, C, D> RuntimeQuadruple<A, B, C, D>.component3() = third
private operator fun <A, B, C, D> RuntimeQuadruple<A, B, C, D>.component4() = fourth
