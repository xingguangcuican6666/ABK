package com.abk.kernel.utils

import com.abk.kernel.data.model.LspBridgeManagedModule
import org.json.JSONArray
import org.json.JSONObject

object LspManagerService {
    private const val MODULES_FILE = "modules.json"
    private const val SCOPES_FILE = "scopes.json"
    private const val ERRORS_FILE = "errors.json"

    data class PersistedState(
        val modules: List<LspBridgeManagedModule> = emptyList()
    )

    fun readPersistedState(): PersistedState {
        val moduleMap = linkedMapOf<String, MutableModuleState>()
        readJsonArrayFile(MODULES_FILE)?.let { modules ->
            for (index in 0 until modules.length()) {
                val item = modules.optJSONObject(index) ?: continue
                val packageName = item.optString("package").trim()
                if (packageName.isBlank()) continue
                moduleMap[packageName] = MutableModuleState(
                    packageName = packageName,
                    enabled = item.optBoolean("enabled", false)
                )
            }
        }

        readJsonObjectFile(SCOPES_FILE)?.let { scopes ->
            val keys = scopes.keys()
            while (keys.hasNext()) {
                val packageName = keys.next().trim()
                if (packageName.isBlank()) continue
                val state = moduleMap.getOrPut(packageName) {
                    MutableModuleState(packageName = packageName)
                }
                val entries = scopes.optJSONArray(packageName) ?: continue
                state.selectedScope = buildList {
                    for (index in 0 until entries.length()) {
                        entries.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
                    }
                }.distinct().sorted()
            }
        }

        readJsonObjectFile(ERRORS_FILE)?.let { errors ->
            val keys = errors.keys()
            while (keys.hasNext()) {
                val packageName = keys.next().trim()
                if (packageName.isBlank()) continue
                val state = moduleMap.getOrPut(packageName) {
                    MutableModuleState(packageName = packageName)
                }
                state.lastError = errors.optString(packageName).trim()
            }
        }

        return PersistedState(
            modules = moduleMap.values
                .map {
                    LspBridgeManagedModule(
                        packageName = it.packageName,
                        enabled = it.enabled,
                        selectedScope = it.selectedScope,
                        loaded = it.loaded,
                        hookActive = it.hookActive,
                        lastError = it.lastError
                    )
                }
                .sortedBy { it.packageName }
        )
    }

    fun setModuleEnabled(packageName: String, enabled: Boolean): RootUtils.ShellResult {
        val cleanPackage = sanitizePackage(packageName)
            ?: return RootUtils.ShellResult(false, listOf("invalid package name"))
        val state = readPersistedState()
        val modules = state.modules.toMutableStateMap()
        val current = modules.getOrPut(cleanPackage) { MutableModuleState(packageName = cleanPackage) }
        current.enabled = enabled

        val writeResult = writeState(modules.values.toList())
        if (!writeResult.success) return writeResult

        val bridgeCommand = if (enabled) {
            "lsp module enable $cleanPackage"
        } else {
            "lsp module disable $cleanPackage"
        }
        val bridgeResult = RootUtils.writeAbkControlCommand(bridgeCommand)
        RootUtils.appendLspBridgeLog("${if (enabled) "enabled" else "disabled"} module $cleanPackage")
        val officialResult = syncOfficialLsposedRuntime()
        return if (bridgeResult.success) {
            RootUtils.ShellResult(true, writeResult.output + officialResult.output)
        } else {
            RootUtils.ShellResult(true, writeResult.output + bridgeResult.output + officialResult.output)
        }
    }

    fun setModuleScope(packageName: String, scopePackages: Collection<String>): RootUtils.ShellResult {
        val cleanPackage = sanitizePackage(packageName)
            ?: return RootUtils.ShellResult(false, listOf("invalid package name"))
        val cleanScope = scopePackages.mapNotNull(::sanitizePackage).distinct().sorted()
        val state = readPersistedState()
        val modules = state.modules.toMutableStateMap()
        val current = modules.getOrPut(cleanPackage) { MutableModuleState(packageName = cleanPackage) }
        current.selectedScope = cleanScope

        val writeResult = writeState(modules.values.toList())
        if (!writeResult.success) return writeResult

        val bridgeResult = if (cleanScope.isEmpty()) {
            RootUtils.writeAbkControlCommand("lsp scope clear $cleanPackage")
        } else {
            RootUtils.writeAbkControlCommand("lsp scope set $cleanPackage ${cleanScope.joinToString(",")}")
        }
        RootUtils.appendLspBridgeLog("updated scope for $cleanPackage (${cleanScope.size})")
        val officialResult = syncOfficialLsposedRuntime()
        return if (bridgeResult.success) {
            RootUtils.ShellResult(true, writeResult.output + officialResult.output)
        } else {
            RootUtils.ShellResult(true, writeResult.output + bridgeResult.output + officialResult.output)
        }
    }

    fun syncBridgeFromPersistedState(): RootUtils.ShellResult {
        val state = readPersistedState()
        val outputs = mutableListOf<String>()
        var success = true
        state.modules.forEach { module ->
            val moduleResult = RootUtils.writeAbkControlCommand(
                if (module.enabled) {
                    "lsp module enable ${module.packageName}"
                } else {
                    "lsp module disable ${module.packageName}"
                }
            )
            success = success && moduleResult.success
            outputs += moduleResult.output

            val scopeResult = if (module.selectedScope.isEmpty()) {
                RootUtils.writeAbkControlCommand("lsp scope clear ${module.packageName}")
            } else {
                RootUtils.writeAbkControlCommand(
                    "lsp scope set ${module.packageName} ${module.selectedScope.joinToString(",")}"
                )
            }
            success = success && scopeResult.success
            outputs += scopeResult.output
        }
        val officialResult = RootUtils.syncOfficialLsposedDatabaseFromPersistedState(state.modules)
        outputs += officialResult.output
        if (success) RootUtils.appendLspBridgeLog("synced ${state.modules.size} persisted LSP modules")
        return RootUtils.ShellResult(success, outputs)
    }

    private fun syncOfficialLsposedRuntime(): RootUtils.ShellResult {
        val state = readPersistedState()
        return RootUtils.syncOfficialLsposedDatabaseFromPersistedState(state.modules)
    }

    private fun writeState(modules: List<MutableModuleState>): RootUtils.ShellResult {
        val moduleArray = JSONArray()
        val scopes = JSONObject()
        val errors = JSONObject()

        modules.sortedBy { it.packageName }.forEach { module ->
            moduleArray.put(
                JSONObject()
                    .put("package", module.packageName)
                    .put("enabled", module.enabled)
            )
            scopes.put(module.packageName, JSONArray().also { array ->
                module.selectedScope.forEach(array::put)
            })
            if (module.lastError.isNotBlank()) {
                errors.put(module.packageName, module.lastError)
            }
        }

        val modulesResult = RootUtils.writeLspBridgeDataFile(MODULES_FILE, moduleArray.toString(2))
        if (!modulesResult.success) return modulesResult
        val scopesResult = RootUtils.writeLspBridgeDataFile(SCOPES_FILE, scopes.toString(2))
        if (!scopesResult.success) return scopesResult
        val errorsResult = RootUtils.writeLspBridgeDataFile(ERRORS_FILE, errors.toString(2))
        if (!errorsResult.success) return errorsResult
        return RootUtils.ShellResult(true, modulesResult.output + scopesResult.output + errorsResult.output)
    }

    private fun readJsonArrayFile(fileName: String): JSONArray? {
        val result = RootUtils.readLspBridgeDataFile(fileName)
        if (!result.success) return null
        val body = result.output.joinToString("\n").trim()
        return runCatching { JSONArray(body.ifBlank { "[]" }) }.getOrNull()
    }

    private fun readJsonObjectFile(fileName: String): JSONObject? {
        val result = RootUtils.readLspBridgeDataFile(fileName)
        if (!result.success) return null
        val body = result.output.joinToString("\n").trim()
        return runCatching { JSONObject(body.ifBlank { "{}" }) }.getOrNull()
    }

    private fun List<LspBridgeManagedModule>.toMutableStateMap(): LinkedHashMap<String, MutableModuleState> {
        val result = linkedMapOf<String, MutableModuleState>()
        forEach { module ->
            val packageName = sanitizePackage(module.packageName) ?: return@forEach
            result[packageName] = MutableModuleState(
                packageName = packageName,
                enabled = module.enabled,
                selectedScope = module.selectedScope.mapNotNull(::sanitizePackage).distinct().sorted(),
                loaded = module.loaded,
                hookActive = module.hookActive,
                lastError = module.lastError
            )
        }
        return result
    }

    private fun sanitizePackage(value: String?): String? {
        val clean = value?.trim().orEmpty()
        if (clean.isBlank()) return null
        return clean.takeIf { it.matches(Regex("[A-Za-z0-9._:-]+")) }
    }

    private data class MutableModuleState(
        val packageName: String,
        var enabled: Boolean = false,
        var selectedScope: List<String> = emptyList(),
        var loaded: Boolean = false,
        var hookActive: Boolean = false,
        var lastError: String = ""
    )
}
