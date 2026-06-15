package com.abk.kernel.viewmodel

import com.abk.kernel.BuildConfig
import com.abk.kernel.data.model.*
import com.abk.kernel.utils.RootUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

internal fun mergeRuntimeStatus(
    gson: Gson,
    ksuModuleListType: Type,
    manager: RootUtils.ManagerRuntimeProbe,
    controlJson: String?,
    ksuModulesJson: String?,
): AbkRuntimeStatus {
    val controlStatus = controlJson?.let { body ->
        runCatching { gson.fromJson(body, AbkRuntimeStatus::class.java) }.getOrNull()
    }
    val ksuModules = parseKsuModules(gson, ksuModuleListType, ksuModulesJson)
    val controlModules = controlStatus?.modules.orEmpty().map { module ->
        module.copy(
            type = module.type.ifBlank { "builtin" },
            source = module.source.ifBlank { "abk" },
            readonly = module.readonly || !module.controllable
        )
    }
    val kpmModules = parseKpmModules(gson)
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

internal fun resolveRuntimeWorkMode(
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

internal fun normalizeRuntimeWorkMode(value: String?): String? {
    return when (value?.trim()?.lowercase()) {
        "lkm" -> "lkm"
        "builtin", "built-in", "built_in" -> "built-in"
        else -> null
    }
}

internal fun RootUtils.ManagerRuntimeProbe.toRuntimeInfo(): AbkRuntimeManagerInfo =
    AbkRuntimeManagerInfo(
        displayName = displayName.ifBlank { if (active) "Root" else "" },
        variant = variant,
        backend = backend,
        version = version,
        active = active,
        capabilities = capabilities,
        diagnostics = diagnostics
    )

internal fun parseKsuModules(gson: Gson, ksuModuleListType: Type, json: String?): List<AbkRuntimeModule> {
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

internal fun parseKpmModules(gson: Gson): List<AbkRuntimeModule> {
    val listResult = RootUtils.listKpmModules()
    if (!listResult.success) return emptyList()
    return parseKpmModuleNames(gson, listResult.output.joinToString("\n"))
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

internal fun parseKpmModuleNames(gson: Gson, output: String): List<String> {
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

internal fun kpmNameFromJsonRecord(record: Any?): String? =
    when (record) {
        is String -> record.trim()
        is Map<*, *> -> listOf("name", "id", "module")
            .asSequence()
            .mapNotNull { key -> record[key]?.toString()?.trim()?.takeIf { it.isNotBlank() } }
            .firstOrNull()
        else -> null
    }

internal fun mergeRuntimeModules(
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
                entryKind = current.entryKind.ifBlank { module.entryKind },
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
                extensionId = current.extensionId.ifBlank { module.extensionId },
                companionPackage = current.companionPackage.ifBlank { module.companionPackage },
                companionDisplayName = current.companionDisplayName.ifBlank { module.companionDisplayName },
                companionAssetName = current.companionAssetName.ifBlank { module.companionAssetName },
                companionDownloadUrl = current.companionDownloadUrl.ifBlank { module.companionDownloadUrl },
                serviceActivity = current.serviceActivity.ifBlank { module.serviceActivity },
                requiresCompanionApp = current.requiresCompanionApp || module.requiresCompanionApp,
                settingsSupported = current.settingsSupported || module.settingsSupported,
                perAppSupported = current.perAppSupported || module.perAppSupported,
                oobePriority = maxOf(current.oobePriority, module.oobePriority),
                groupId = current.groupId.ifBlank { module.groupId },
                groupName = current.groupName.ifBlank { module.groupName },
                groupRole = current.groupRole.ifBlank { module.groupRole },
                groupDescription = current.groupDescription.ifBlank { module.groupDescription },
                groupRepoUrl = current.groupRepoUrl.ifBlank { module.groupRepoUrl },
                kpmArgs = current.kpmArgs.ifBlank { module.kpmArgs }
            )
        }
    }

    ksuModules.forEach(::put)
    controlModules.forEach(::put)
    kpmModules.forEach(::put)

    return merged.values.toList()
}

internal fun mergeRuntimeModuleType(current: AbkRuntimeModule, next: AbkRuntimeModule): String =
    when {
        current.normalizedType() == "kpm" || next.normalizedType() == "kpm" -> "kpm"
        current.normalizedType() == "standard" || next.normalizedType() == "standard" -> "standard"
        else -> "builtin"
    }

internal fun AbkRuntimeModule.normalizedType(): String =
    type.ifBlank {
        when {
            source.split(',').any { it.trim() == "kpm" } -> "kpm"
            source.split(',').any { it.trim() == "ksud" } -> "standard"
            else -> "builtin"
        }
    }

internal fun Map<String, Any?>.runtimeString(key: String): String =
    this[key]?.toString()?.trim().orEmpty()

internal fun Map<String, Any?>.runtimeBoolean(key: String, default: Boolean = false): Boolean {
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

internal fun Map<String, Any?>.runtimeLong(key: String): Long {
    val value = this[key] ?: return 0L
    return when (value) {
        is Number -> value.toLong()
        else -> value.toString().trim().toLongOrNull() ?: 0L
    }
}

internal fun AbkRuntimeModule.isKsuBacked(): Boolean =
    normalizedType() == "standard" || source.split(',').any { it.trim() == "ksud" }
