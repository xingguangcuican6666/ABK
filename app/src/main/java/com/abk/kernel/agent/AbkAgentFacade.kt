package com.abk.kernel.agent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import com.abk.kernel.BuildConfig
import com.abk.kernel.R
import com.abk.kernel.data.model.AbkRuntimeModule
import com.abk.kernel.data.model.AbkRuntimeStatus
import com.abk.kernel.data.model.RootGrantApp
import com.abk.kernel.data.model.SusfsConfig
import com.abk.kernel.data.model.SusfsRuntimeStatus
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.utils.defaultSusfsConfig
import com.abk.kernel.utils.normalizeSusfsConfig
import com.abk.kernel.viewmodel.MainUiState
import com.abk.kernel.viewmodel.RuntimeModuleActionBackend
import com.abk.kernel.viewmodel.RuntimeModuleControlBackend
import com.abk.kernel.viewmodel.exportDiagnosticBundle
import com.abk.kernel.viewmodel.isAbkMetaMount
import com.abk.kernel.viewmodel.isKsuBacked
import com.abk.kernel.viewmodel.mergeRuntimeStatus
import com.abk.kernel.viewmodel.normalizedType
import com.abk.kernel.viewmodel.preferredActionBackend
import com.abk.kernel.viewmodel.preferredControlBackend
import com.abk.kernel.viewmodel.sortRuntimeModulesForDisplay
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayOutputStream
import org.json.JSONArray
import org.json.JSONObject

internal data class AbkAgentSessionResponse(
    @SerializedName("protocolVersion") val protocolVersion: String,
    @SerializedName("appVersion") val appVersion: String,
    @SerializedName("appVersionCode") val appVersionCode: Long,
    @SerializedName("packageName") val packageName: String,
    @SerializedName("serviceHost") val serviceHost: String,
    @SerializedName("servicePort") val servicePort: Int,
    @SerializedName("rootGranted") val rootGranted: Boolean,
    @SerializedName("managerAccessKind") val managerAccessKind: String,
    @SerializedName("managerDiagnostic") val managerDiagnostic: String? = null,
    @SerializedName("capabilities")
    val capabilities: List<String> = emptyList(),
)

internal data class AbkAgentRuntimeResponse(
    @SerializedName("rootGranted") val rootGranted: Boolean,
    @SerializedName("managerAccessKind") val managerAccessKind: String,
    @SerializedName("managerDiagnostic") val managerDiagnostic: String? = null,
    @SerializedName("runtimeStatus") val runtimeStatus: AbkRuntimeStatus? = null,
)

internal data class AbkAgentRootGrantResponse(
    @SerializedName("rootGranted") val rootGranted: Boolean,
    @SerializedName("managerAccessKind") val managerAccessKind: String,
    @SerializedName("managerDiagnostic") val managerDiagnostic: String? = null,
    @SerializedName("apps") val apps: List<RootGrantApp> = emptyList(),
)

internal data class AbkAgentPackageInfo(
    @SerializedName("packageName") val packageName: String,
    @SerializedName("versionName") val versionName: String,
    @SerializedName("versionCode") val versionCode: Long,
    @SerializedName("appLabel") val appLabel: String,
    @SerializedName("isSystem") val isSystem: Boolean,
    @SerializedName("uid") val uid: Int,
)

internal data class AbkAgentSusfsResponse(
    @SerializedName("rootGranted") val rootGranted: Boolean,
    @SerializedName("status") val status: SusfsRuntimeStatus? = null,
    @SerializedName("config") val config: SusfsConfig = defaultSusfsConfig(),
    @SerializedName("error") val error: String? = null,
)

internal data class AbkAgentKernelFeaturesResponse(
    @SerializedName("rootGranted") val rootGranted: Boolean,
    @SerializedName("managerAccessKind") val managerAccessKind: String,
    @SerializedName("managerDiagnostic") val managerDiagnostic: String? = null,
    @SerializedName("items") val items: List<AbkAgentKernelFeatureItem> = emptyList(),
)

internal data class AbkAgentKernelFeatureItem(
    @SerializedName("id") val id: String,
    @SerializedName("checked") val checked: Boolean,
    @SerializedName("enabled") val enabled: Boolean,
    @SerializedName("status") val status: String,
)

internal object AbkAgentFacade {
    private val gson = Gson()
    private val ksuModuleListType = object : TypeToken<List<Map<String, Any?>>>() {}.type

    fun health(context: Context, port: Int): Map<String, Any> {
        val rootGranted = RootUtils.isRootAvailable()
        val access = RootUtils.resolveManagerAccess(rootGranted)
        val runtime = currentRuntimeSnapshot(rootGranted, access)
        return mutableMapOf<String, Any>(
            "status" to "ok",
            "protocolVersion" to "abk-agent-v1",
            "port" to port,
            "appVersion" to BuildConfig.VERSION_NAME,
            "appVersionCode" to BuildConfig.APP_VERSION_CODE,
            "rootGranted" to rootGranted,
            "managerAccessKind" to access.kind.name.lowercase(),
            "capabilities" to declaredCapabilities(rootGranted, access, runtime),
        ).apply {
            managerAccessError(context, access, rootGranted)?.let {
                put("managerDiagnostic", it)
            }
        }
    }

    fun session(context: Context, host: String, port: Int): AbkAgentSessionResponse {
        val rootGranted = RootUtils.isRootAvailable()
        val access = RootUtils.resolveManagerAccess(rootGranted)
        val runtime = currentRuntimeSnapshot(rootGranted, access)
        return AbkAgentSessionResponse(
            protocolVersion = "abk-agent-v1",
            appVersion = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.APP_VERSION_CODE,
            packageName = context.packageName,
            serviceHost = host,
            servicePort = port,
            rootGranted = rootGranted,
            managerAccessKind = access.kind.name.lowercase(),
            managerDiagnostic = managerAccessError(context, access, rootGranted),
            capabilities = declaredCapabilities(rootGranted, access, runtime),
        )
    }

    fun runtime(context: Context): AbkAgentRuntimeResponse {
        val rootGranted = RootUtils.isRootAvailable()
        val access = RootUtils.resolveManagerAccess(rootGranted)
        val runtimeStatus = currentRuntimeSnapshot(rootGranted, access)
        return AbkAgentRuntimeResponse(
            rootGranted = rootGranted,
            managerAccessKind = access.kind.name.lowercase(),
            managerDiagnostic = managerAccessError(context, access, rootGranted),
            runtimeStatus = runtimeStatus?.copy(
                modules = sortRuntimeModulesForDisplay(runtimeStatus.modules),
            ),
        )
    }

    fun rootGrants(context: Context): AbkAgentRootGrantResponse {
        val rootGranted = RootUtils.isRootAvailable()
        val access = RootUtils.resolveManagerAccess(rootGranted)
        val apps = if (access.hasNativeManagerPermission) {
            RootUtils.listRootGrantApps(context)
        } else {
            emptyList()
        }
        return AbkAgentRootGrantResponse(
            rootGranted = rootGranted,
            managerAccessKind = access.kind.name.lowercase(),
            managerDiagnostic = managerAccessError(context, access, rootGranted),
            apps = apps,
        )
    }

    fun listPackages(context: Context, type: String): List<String> {
        val normalizedType = type.trim().lowercase()
        val packageManager = context.packageManager
        val applications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(android.content.pm.PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledApplications(0)
        }
        return applications
            .asSequence()
            .filter { application ->
                when (normalizedType) {
                    "user" -> (application.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
                    "system" -> (application.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    else -> true
                }
            }
            .map { it.packageName.orEmpty() }
            .filter { it.isNotBlank() }
            .sorted()
            .toList()
    }

    fun packageInfos(context: Context, packages: List<String>): List<AbkAgentPackageInfo> {
        val packageManager = context.packageManager
        return packages
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { packageName ->
                val info = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.getPackageInfo(
                            packageName,
                            android.content.pm.PackageManager.PackageInfoFlags.of(0),
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.getPackageInfo(packageName, 0)
                    }
                }.getOrNull() ?: return@mapNotNull null
                val appInfo = info.applicationInfo ?: return@mapNotNull null
                AbkAgentPackageInfo(
                    packageName = packageName,
                    versionName = info.versionName.orEmpty(),
                    versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        info.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        info.versionCode.toLong()
                    },
                    appLabel = runCatching {
                        packageManager.getApplicationLabel(appInfo).toString()
                    }.getOrDefault(packageName),
                    isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                    uid = appInfo.uid,
                )
            }
            .toList()
    }

    fun readRootGrantIconPng(context: Context, packageName: String): ByteArray? {
        val cleanPackage = packageName.trim()
        if (cleanPackage.isBlank()) return null
        val packageManager = context.packageManager
        val drawable = runCatching { packageManager.getApplicationIcon(cleanPackage) }.getOrNull()
            ?: return null
        val bitmap = drawable.toBitmap(64)
        return ByteArrayOutputStream().use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) return null
            output.toByteArray()
        }
    }

    fun readRuntimeModuleWebResource(moduleId: String, relativePath: String?): ByteArray? {
        val cleanModuleId = moduleId.trim()
        if (!RootUtils.isSafeModuleIdForPath(cleanModuleId)) return null
        val targetPath = relativePath?.trim().orEmpty().ifBlank { "index.html" }
        return RootUtils.readModuleWebResource(cleanModuleId, targetPath)
            ?: if (targetPath == "index.html") {
                RootUtils.readModuleWebResource(cleanModuleId, "index.htm")
            } else {
                null
            }
    }

    fun runtimeModuleWebInfoJson(moduleId: String): String {
        val cleanModuleId = moduleId.trim()
        if (!RootUtils.isSafeModuleIdForPath(cleanModuleId)) return "{}"
        return RootUtils.moduleInfoJson(cleanModuleId)
    }

    fun executeRuntimeModuleWebCommand(
        moduleId: String,
        command: String,
        optionsJson: String?,
    ): RootUtils.ShellResult {
        val moduleDir = moduleDirForWebUi(moduleId)
            ?: return RootUtils.ShellResult(false, listOf("module webui unavailable"))
        val cleanCommand = command.trim()
        if (cleanCommand.isBlank()) {
            return RootUtils.ShellResult(false, listOf("command missing"))
        }
        val finalCommand = commandWithOptions(cleanCommand, optionsJson, moduleDir)
        return RootUtils.execRootCommandForWebUi(finalCommand, cwd = moduleDir)
    }

    fun spawnRuntimeModuleWebCommand(
        moduleId: String,
        command: String,
        argsJson: String?,
        optionsJson: String?,
    ): RootUtils.ShellResult {
        val moduleDir = moduleDirForWebUi(moduleId)
            ?: return RootUtils.ShellResult(false, listOf("module webui unavailable"))
        val cleanCommand = command.trim()
        if (cleanCommand.isBlank()) {
            return RootUtils.ShellResult(false, listOf("command missing"))
        }
        val argString = runCatching {
            val array = JSONArray(argsJson ?: "[]")
            buildString {
                for (index in 0 until array.length()) {
                    val item = array.optString(index)
                    if (item.isBlank()) continue
                    if (isNotEmpty()) append(' ')
                    append(shellQuote(item))
                }
            }
        }.getOrDefault("")
        val baseCommand = listOf(cleanCommand, argString)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val finalCommand = commandWithOptions(baseCommand, optionsJson, moduleDir)
        return RootUtils.execRootCommandForWebUi(finalCommand, cwd = moduleDir)
    }

    fun setRootGrantAllowed(
        context: Context,
        packageName: String,
        allowed: Boolean,
    ): RootUtils.ShellResult {
        val cleanPackage = packageName.trim()
        if (cleanPackage.isBlank()) {
            return RootUtils.ShellResult(false, listOf("package_name missing"))
        }
        val rootGranted = RootUtils.isRootAvailable()
        val access = RootUtils.resolveManagerAccess(rootGranted)
        if (!access.hasNativeManagerPermission) {
            return RootUtils.ShellResult(
                false,
                listOf(
                    managerAccessError(context, access, rootGranted)
                        ?: "native manager permission unavailable",
                ),
            )
        }
        val app = RootUtils.listRootGrantApps(context).firstOrNull { it.packageName == cleanPackage }
            ?: return RootUtils.ShellResult(false, listOf("package not found: $cleanPackage"))
        val profile = app.profile.copy(
            name = cleanPackage,
            currentUid = app.uid,
            allowSu = allowed,
            rootUseDefault = true,
            nonRootUseDefault = true,
        )
        return if (RootUtils.setRootGrantProfile(profile)) {
            RootUtils.ShellResult(true, listOf("updated $cleanPackage"))
        } else {
            RootUtils.ShellResult(false, listOf("failed to update $cleanPackage"))
        }
    }

    fun susfs(context: Context): AbkAgentSusfsResponse {
        val rootGranted = RootUtils.isRootAvailable()
        if (!rootGranted) {
            return AbkAgentSusfsResponse(
                rootGranted = false,
                config = defaultSusfsConfig(),
            )
        }
        return runCatching {
            AbkAgentSusfsResponse(
                rootGranted = true,
                status = RootUtils.readSusfsRuntimeStatus(),
                config = normalizeSusfsConfig(RootUtils.readSusfsConfig()),
            )
        }.getOrElse { error ->
            AbkAgentSusfsResponse(
                rootGranted = true,
                config = defaultSusfsConfig(),
                error = error.message ?: "susfs load failed",
            )
        }
    }

    fun kernelFeatures(context: Context): AbkAgentKernelFeaturesResponse {
        val rootGranted = RootUtils.isRootAvailable()
        val access = RootUtils.resolveManagerAccess(rootGranted)
        return AbkAgentKernelFeaturesResponse(
            rootGranted = rootGranted,
            managerAccessKind = access.kind.name.lowercase(),
            managerDiagnostic = managerAccessError(context, access, rootGranted),
            items = buildKernelFeatureItems(),
        )
    }

    fun applySusfsConfig(
        config: SusfsConfig,
        onOutput: (String) -> Unit,
    ): RootUtils.ShellResult = RootUtils.applySusfsConfig(normalizeSusfsConfig(config), onOutput)

    fun setRuntimeModuleEnabled(moduleId: String, enabled: Boolean): RootUtils.ShellResult {
        val module = findRuntimeModule(moduleId)
            ?: return RootUtils.ShellResult(false, listOf("module not found: $moduleId"))
        return when {
            module.isAbkMetaMount() -> RootUtils.setAbkMetaMountEnabled(enabled)
            module.preferredControlBackend() == RuntimeModuleControlBackend.ABK_CONTROL -> {
                val command = if (enabled) "enable ${module.id}" else "disable ${module.id}"
                val controlResult = RootUtils.writeAbkControlCommand(command)
                if (controlResult.success) {
                    controlResult
                } else if (module.isKsuBacked()) {
                    RootUtils.setKsuModuleEnabled(module.id, enabled)
                } else {
                    controlResult
                }
            }
            module.preferredControlBackend() == RuntimeModuleControlBackend.KSU ->
                RootUtils.setKsuModuleEnabled(module.id, enabled)
            else ->
                RootUtils.writeAbkControlCommand(
                    if (enabled) "enable ${module.id}" else "disable ${module.id}",
                )
        }
    }

    fun setRuntimeModulePendingUninstall(moduleId: String, pending: Boolean): RootUtils.ShellResult {
        val module = findRuntimeModule(moduleId)
            ?: return RootUtils.ShellResult(false, listOf("module not found: $moduleId"))
        if (!module.isKsuBacked()) {
            return RootUtils.ShellResult(false, listOf("module uninstall unsupported"))
        }
        return RootUtils.setKsuModulePendingUninstall(module.id, pending)
    }

    fun runRuntimeModuleAction(
        moduleId: String,
        onOutput: (String) -> Unit,
    ): RootUtils.ShellResult {
        val module = findRuntimeModule(moduleId)
            ?: return RootUtils.ShellResult(false, listOf("module not found: $moduleId"))
        return when (module.preferredActionBackend()) {
            RuntimeModuleActionBackend.ABK_ACTION_SCRIPT ->
                RootUtils.runModuleActionScript(module.moduleDir.ifBlank { "/data/adb/modules/${module.id}" }, onOutput)
            RuntimeModuleActionBackend.KSU_ACTION ->
                RootUtils.runKsuModuleAction(module.id, onOutput)
            RuntimeModuleActionBackend.NONE ->
                RootUtils.ShellResult(false, listOf("module action unsupported"))
        }
    }

    fun installModule(zipPath: String, onOutput: (String) -> Unit): RootUtils.ShellResult =
        RootUtils.installModule(zipPath, onOutput)

    fun installApk(context: Context, apkPath: String, onOutput: (String) -> Unit): RootUtils.ShellResult =
        RootUtils.installApk(context, apkPath, onOutput)

    fun flashImage(imagePath: String, partition: String, onOutput: (String) -> Unit): RootUtils.ShellResult =
        RootUtils.flashImage(imagePath, partition, onOutput)

    fun setKernelFeatureEnabled(context: Context, featureId: String, enabled: Boolean): RootUtils.ShellResult {
        return when (featureId.trim()) {
            "adb_root", "sulog", "kernel_umount", "selinux_hide" ->
                RootUtils.setKsuFeatureEnabled(featureId.trim(), enabled)
            "default_umount" -> {
                if (!RootUtils.isNativeManagerActive()) {
                    RootUtils.ShellResult(
                        false,
                        listOf(context.getString(R.string.vm_setting_native_manager_required)),
                    )
                } else if (RootUtils.setDefaultUmountModules(enabled)) {
                    RootUtils.ShellResult(
                        true,
                        listOf("updated default_umount"),
                    )
                } else {
                    RootUtils.ShellResult(false, listOf("failed to update default_umount"))
                }
            }
            else -> RootUtils.ShellResult(false, listOf("unknown kernel feature: $featureId"))
        }
    }

    suspend fun exportDiagnostics(context: Context): Pair<java.io.File, List<String>> {
        val rootGranted = RootUtils.isRootAvailable()
        val access = RootUtils.resolveManagerAccess(rootGranted)
        val state = MainUiState(
            rootGranted = rootGranted,
            abkRuntimeStatus = currentRuntimeSnapshot(rootGranted, access),
        )
        val result = exportDiagnosticBundle(context, state)
        return result.zipFile to result.warnings
    }

    private fun currentRuntimeSnapshot(
        rootGranted: Boolean,
        access: RootUtils.ManagerAccessInfo,
    ): AbkRuntimeStatus? {
        if (!access.hasNativeManagerPermission && !rootGranted) return null
        val snapshot = RootUtils.readManagerRuntimeSnapshot()
        if (!snapshot.manager.active) return null
        return mergeRuntimeStatus(
            gson = gson,
            ksuModuleListType = ksuModuleListType,
            manager = snapshot.manager,
            controlJson = snapshot.controlStatusJson,
            ksuModulesJson = snapshot.ksuModulesJson,
        )
    }

    private fun buildKernelFeatureItems(): List<AbkAgentKernelFeatureItem> {
        val items = mutableListOf<AbkAgentKernelFeatureItem>()
        items += featureItem(
            id = "kernel_umount",
            state = RootUtils.readKsuFeature("kernel_umount"),
            checked = { value != 0L },
        )
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            items += featureItem(
                id = "adb_root",
                state = RootUtils.readKsuFeature("adb_root"),
                checked = { (configValue ?: value ?: 0L) != 0L },
            )
        }
        items += featureItem(
            id = "sulog",
            state = RootUtils.readKsuFeature("sulog"),
            checked = { value != 0L },
        )
        val selinuxHide = RootUtils.readKsuFeature("selinux_hide")
        if (selinuxHide.support != RootUtils.KsuFeatureSupport.UNSUPPORTED) {
            items += featureItem(
                id = "selinux_hide",
                state = selinuxHide,
                checked = { value != 0L },
            )
        }
        if (RootUtils.isNativeManagerActive()) {
            items += AbkAgentKernelFeatureItem(
                id = "default_umount",
                checked = RootUtils.isDefaultUmountModules(),
                enabled = true,
                status = "supported",
            )
        }
        return items
    }

    private inline fun featureItem(
        id: String,
        state: RootUtils.KsuFeatureState,
        checked: RootUtils.KsuFeatureState.() -> Boolean,
    ): AbkAgentKernelFeatureItem {
        return AbkAgentKernelFeatureItem(
            id = id,
            checked = state.checked(),
            enabled = state.support == RootUtils.KsuFeatureSupport.SUPPORTED,
            status = when (state.support) {
                RootUtils.KsuFeatureSupport.SUPPORTED -> "supported"
                RootUtils.KsuFeatureSupport.MANAGED -> "managed"
                RootUtils.KsuFeatureSupport.UNSUPPORTED -> "unsupported"
            },
        )
    }

    private fun declaredCapabilities(
        rootGranted: Boolean,
        access: RootUtils.ManagerAccessInfo,
        runtime: AbkRuntimeStatus?,
    ): List<String> {
        val capabilities = mutableListOf(
            "session.read",
            "runtime.read",
            "root.refresh",
            "diagnostics.export",
            "kernel_features.read",
        )
        if (rootGranted) {
            capabilities += listOf(
                "susfs.read",
                "susfs.write",
                "install.module",
                "install.apk",
                "flash.image",
                "kernel_features.write",
            )
        }
        if (runtime?.modules?.isNotEmpty() == true) {
            capabilities += listOf(
                "runtime.module.enable",
                "runtime.module.uninstall",
                "runtime.module.action",
            )
        }
        if (access.hasNativeManagerPermission) {
            capabilities += listOf(
                "root_grants.read",
                "root_grants.write",
            )
        }
        return capabilities.distinct().sorted()
    }

    private fun findRuntimeModule(moduleId: String): AbkRuntimeModule? {
        val rootGranted = RootUtils.isRootAvailable()
        val access = RootUtils.resolveManagerAccess(rootGranted)
        return currentRuntimeSnapshot(rootGranted, access)
            ?.modules
            ?.firstOrNull { it.id == moduleId.trim() }
    }

    private fun managerAccessError(
        context: Context,
        access: RootUtils.ManagerAccessInfo,
        rootGranted: Boolean,
    ): String? {
        access.diagnostic?.takeIf { it.isNotBlank() }?.let { return it }
        val message = when (access.kind) {
            RootUtils.ManagerAccessKind.NATIVE_MANAGER -> ""
            RootUtils.ManagerAccessKind.NO_ROOT -> context.getString(R.string.vm_external_manager_no_root)
            RootUtils.ManagerAccessKind.ROOT_ONLY -> context.getString(R.string.vm_external_root_no_native_permission)
            RootUtils.ManagerAccessKind.NATIVE_KERNEL_NO_MANAGER ->
                context.getString(R.string.vm_native_kernel_no_manager)
        }
        return message.ifBlank { if (rootGranted) null else null }
    }

    private fun android.graphics.drawable.Drawable.toBitmap(sizePx: Int): Bitmap {
        if (this is BitmapDrawable && bitmap != null) {
            return Bitmap.createScaledBitmap(bitmap, sizePx, sizePx, true)
        }
        val width = intrinsicWidth.takeIf { it > 0 } ?: sizePx
        val height = intrinsicHeight.takeIf { it > 0 } ?: sizePx
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return if (width == sizePx && height == sizePx) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, sizePx, sizePx, true)
        }
    }

    private fun moduleDirForWebUi(moduleId: String): String? {
        val cleanModuleId = moduleId.trim()
        if (!RootUtils.isSafeModuleIdForPath(cleanModuleId)) return null
        return "/data/adb/modules/$cleanModuleId"
    }

    private fun commandWithOptions(
        command: String,
        optionsJson: String?,
        moduleDir: String,
    ): String {
        if (optionsJson.isNullOrBlank()) return command
        val options = runCatching { JSONObject(optionsJson) }.getOrNull() ?: return command
        val prefix = buildString {
            options.optJSONObject("env")?.let { env ->
                val keys = env.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    append("export ")
                    append(key)
                    append("=")
                    append(shellQuote(env.optString(key)))
                    append('\n')
                }
            }
            options.optString("cwd")
                .takeIf { it.isNotBlank() }
                ?.let { cwd ->
                    append("cd ")
                    append(shellQuote(cwd))
                    append(" 2>/dev/null || exit 2\n")
                }
                ?: append("cd ${shellQuote(moduleDir)} 2>/dev/null || exit 2\n")
        }
        return prefix + command
    }

    private fun shellQuote(value: String): String =
        "'${value.replace("'", "'\"'\"'")}'"
}
