package com.abk.kernel.extensions

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import com.abk.kernel.data.model.CustomExternalModuleEntryKind
import com.abk.kernel.data.model.AbkRuntimeModule
import com.abk.kernel.data.model.AbkRuntimeStatus
import com.abk.kernel.utils.RootUtils
import com.google.gson.Gson
import org.json.JSONObject

const val ABK_EXTENSION_DISCOVERY_ACTION = "com.abk.kernel.action.ABK_EXTENSION"
const val ABK_EXTENSION_EXTRA_ID = "com.abk.kernel.extra.EXTENSION_ID"
const val ABK_EXTENSION_EXTRA_HOST_PACKAGE = "com.abk.kernel.extra.HOST_PACKAGE"
const val ABK_EXTENSION_EXTRA_HOST_PROVIDER = "com.abk.kernel.extra.HOST_PROVIDER"
const val ABK_EXTENSION_META_ID = "com.abk.kernel.extension.ID"
const val ABK_EXTENSION_META_NAME = "com.abk.kernel.extension.NAME"
const val ABK_EXTENSION_META_OOBE_ACTIVITY = "com.abk.kernel.extension.OOBE_ACTIVITY"
const val ABK_EXTENSION_META_SETTINGS_ACTIVITY = "com.abk.kernel.extension.SETTINGS_ACTIVITY"

data class AbkDiscoveredExtensionApp(
    val extensionId: String,
    val packageName: String,
    val displayName: String,
    val oobeComponent: ComponentName?,
    val settingsComponent: ComponentName?,
)

data class AbkExtensionState(
    val rawJson: String = "",
    val oobeCompleted: Boolean = false,
    val summary: String = "",
    val hasConfiguration: Boolean = false,
)

data class AbkManagedExtension(
    val moduleId: String,
    val extensionId: String,
    val name: String,
    val description: String,
    val companionPackage: String,
    val companionDisplayName: String,
    val companionAssetName: String,
    val companionDownloadUrl: String,
    val requiresCompanionApp: Boolean,
    val settingsSupported: Boolean,
    val perAppSupported: Boolean,
    val oobePriority: Int,
    val installedPackageName: String = "",
    val discoveredApp: AbkDiscoveredExtensionApp? = null,
    val state: AbkExtensionState? = null,
) {
    val isCompanionInstalled: Boolean
        get() = installedPackageName.isNotBlank()

    val needsOobe: Boolean
        get() = requiresInteractiveSetup && state?.hasConfiguration != true

    val summary: String
        get() = state?.summary.orEmpty()

    private val requiresInteractiveSetup: Boolean
        get() = settingsSupported || perAppSupported || discoveredApp?.oobeComponent != null
}

fun abkExtensionHostAuthority(context: Context): String =
    "${context.packageName}.extensionhost"

fun abkLoadManagedExtensions(context: Context): List<AbkManagedExtension> {
    val runtimeStatus = RootUtils.readManagerRuntimeSnapshot().controlStatusJson
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { Gson().fromJson(it, AbkRuntimeStatus::class.java) }.getOrNull() }
        ?: return emptyList()
    val discovered = discoverExtensionApps(context)
    val extensionModules = if (runtimeStatus.extensionModules.isNotEmpty()) {
        runtimeStatus.extensionModules
    } else {
        runtimeStatus.modules
            .filter { abkShouldExposeManagedExtension(it, discovered[it.extensionId] != null) }
    }

    return extensionModules
        .asSequence()
        .filter { it.extensionId.isNotBlank() }
        .groupBy { it.extensionId.trim() }
        .mapNotNull { (extensionId, modules) ->
            val discoveredApp = discovered[extensionId]
            val selectedModule = abkPickManagedExtensionModule(
                modules = modules,
                hasDiscoveredApp = discoveredApp != null
            ) ?: return@mapNotNull null
            toManagedExtension(context, selectedModule, discoveredApp)
        }
        .sortedWith(
            compareByDescending<AbkManagedExtension> { it.needsOobe }
                .thenByDescending { it.oobePriority }
                .thenBy { it.name.lowercase() }
        )
}

internal fun abkShouldExposeManagedExtension(
    module: AbkRuntimeModule,
    hasDiscoveredApp: Boolean,
): Boolean {
    if (module.extensionId.isBlank()) return false
    if (CustomExternalModuleEntryKind.normalize(module.entryKind) == CustomExternalModuleEntryKind.MODULE_SET_CHILD)
        return false
    if (hasDiscoveredApp) return true

    // Some runtime modules reuse extension_id for non-app dependencies. Keep the
    // extensions surface limited to entries that actually declare a companion app.
    return abkHasCompanionMetadata(module)
}

internal fun abkPickManagedExtensionModule(
    modules: List<AbkRuntimeModule>,
    hasDiscoveredApp: Boolean,
): AbkRuntimeModule? {
    val candidates = modules.filter { it.extensionId.isNotBlank() }
    if (candidates.isEmpty()) return null

    val rankedCandidates = candidates.sortedWith(
        compareByDescending<AbkRuntimeModule> { abkCompanionMetadataScore(it) }
            .thenByDescending { it.oobePriority }
            .thenByDescending { it.name.isNotBlank() }
            .thenBy { it.name.lowercase() }
            .thenBy { it.id.lowercase() }
    )
    val explicitCompanionModule = rankedCandidates.firstOrNull(::abkHasCompanionMetadata)
    if (explicitCompanionModule != null) return explicitCompanionModule

    return if (hasDiscoveredApp) {
        rankedCandidates.firstOrNull()
    } else {
        null
    }
}

fun abkPickPendingExtension(context: Context): AbkManagedExtension? =
    abkLoadManagedExtensions(context).firstOrNull { it.needsOobe }

fun abkOpenExtensionManager(
    context: Context,
    extensionId: String? = null,
    bootstrapMode: Boolean = false,
): Intent = Intent(context, AbkExtensionManagerActivity::class.java).apply {
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    extensionId?.takeIf { it.isNotBlank() }?.let {
        putExtra(ABK_EXTENSION_EXTRA_ID, it)
    }
    putExtra("bootstrap_mode", bootstrapMode)
}

fun abkLaunchExtensionOobe(activity: Activity, extension: AbkManagedExtension): Boolean {
    val component = extension.discoveredApp?.oobeComponent ?: return false
    activity.startActivity(
        Intent().setComponent(component)
            .putExtra(ABK_EXTENSION_EXTRA_ID, extension.extensionId)
            .putExtra(ABK_EXTENSION_EXTRA_HOST_PACKAGE, activity.packageName)
            .putExtra(ABK_EXTENSION_EXTRA_HOST_PROVIDER, abkExtensionHostAuthority(activity))
    )
    return true
}

fun abkLaunchExtensionSettings(activity: Activity, extension: AbkManagedExtension): Boolean {
    val component = extension.discoveredApp?.settingsComponent ?: return false
    activity.startActivity(
        Intent().setComponent(component)
            .putExtra(ABK_EXTENSION_EXTRA_ID, extension.extensionId)
            .putExtra(ABK_EXTENSION_EXTRA_HOST_PACKAGE, activity.packageName)
            .putExtra(ABK_EXTENSION_EXTRA_HOST_PROVIDER, abkExtensionHostAuthority(activity))
    )
    return true
}

fun abkLaunchExtensionCompanionApp(activity: Activity, extension: AbkManagedExtension): Boolean {
    val packageName = extension.discoveredApp?.packageName
        ?: extension.companionPackage.takeIf { it.isNotBlank() }
        ?: return false
    val launchIntent = activity.packageManager.getLaunchIntentForPackage(packageName) ?: return false
    activity.startActivity(
        launchIntent
            .putExtra(ABK_EXTENSION_EXTRA_ID, extension.extensionId)
            .putExtra(ABK_EXTENSION_EXTRA_HOST_PACKAGE, activity.packageName)
            .putExtra(ABK_EXTENSION_EXTRA_HOST_PROVIDER, abkExtensionHostAuthority(activity))
    )
    return true
}

private fun toManagedExtension(
    context: Context,
    module: AbkRuntimeModule,
    discoveredApp: AbkDiscoveredExtensionApp?,
): AbkManagedExtension {
    val installedPackageName = resolveInstalledCompanionPackage(context, module, discoveredApp)
    val state = RootUtils.readAbkExtensionState(module.extensionId)?.let(::parseExtensionState)
    return AbkManagedExtension(
        moduleId = module.id,
        extensionId = module.extensionId,
        name = module.name.ifBlank { module.extensionId },
        description = module.description,
        companionPackage = module.companionPackage.ifBlank { discoveredApp?.packageName.orEmpty() },
        companionDisplayName = module.companionDisplayName.ifBlank { discoveredApp?.displayName.orEmpty() },
        companionAssetName = module.companionAssetName,
        companionDownloadUrl = module.companionDownloadUrl,
        requiresCompanionApp = module.requiresCompanionApp,
        settingsSupported = module.settingsSupported,
        perAppSupported = module.perAppSupported,
        oobePriority = module.oobePriority,
        installedPackageName = installedPackageName,
        discoveredApp = discoveredApp?.takeIf {
            module.companionPackage.isBlank() || module.companionPackage == it.packageName
        },
        state = state,
    )
}

private fun resolveInstalledCompanionPackage(
    context: Context,
    module: AbkRuntimeModule,
    discoveredApp: AbkDiscoveredExtensionApp?,
): String {
    val matchingDiscoveredPackage = discoveredApp?.packageName?.trim()
        ?.takeIf { packageName ->
            module.companionPackage.isBlank() || module.companionPackage == packageName
        }
    val candidates = buildList {
        module.companionPackage.trim().takeIf { it.isNotBlank() }?.let(::add)
        matchingDiscoveredPackage?.takeIf { it.isNotBlank() }?.let(::add)
    }.distinct()
    return candidates.firstOrNull { packageName ->
        isPackageInstalled(context.packageManager, packageName)
    }.orEmpty()
}

private fun abkHasCompanionMetadata(module: AbkRuntimeModule): Boolean =
    module.requiresCompanionApp ||
        module.companionPackage.isNotBlank() ||
        module.companionDisplayName.isNotBlank() ||
        module.companionAssetName.isNotBlank() ||
        module.companionDownloadUrl.isNotBlank()

private fun abkCompanionMetadataScore(module: AbkRuntimeModule): Int {
    var score = 0
    if (module.requiresCompanionApp) score += 16
    if (module.companionPackage.isNotBlank()) score += 8
    if (module.companionDownloadUrl.isNotBlank()) score += 4
    if (module.companionAssetName.isNotBlank()) score += 2
    if (module.companionDisplayName.isNotBlank()) score += 1
    return score
}

private fun parseExtensionState(rawJson: String): AbkExtensionState {
    val json = runCatching { JSONObject(rawJson) }.getOrNull()
        ?: return AbkExtensionState(
            rawJson = rawJson,
            hasConfiguration = rawJson.isNotBlank()
        )
    val summary = json.optString("summary").takeIf { it.isNotBlank() }
        ?: json.optJSONObject("settings")?.optString("mode").orEmpty()
    val oobeCompleted = json.optBoolean("oobe_completed", false)
    val hasSettings = (json.optJSONObject("settings")?.length() ?: 0) > 0
    val hasExtraState = json.keys().asSequence().any { key ->
        key !in setOf("oobe_completed", "summary")
    }
    return AbkExtensionState(
        rawJson = rawJson,
        oobeCompleted = oobeCompleted,
        summary = summary,
        hasConfiguration = oobeCompleted || summary.isNotBlank() || hasSettings || hasExtraState,
    )
}

private fun discoverExtensionApps(context: Context): Map<String, AbkDiscoveredExtensionApp> {
    val intent = Intent(ABK_EXTENSION_DISCOVERY_ACTION)
    val resolves = queryIntentActivities(context.packageManager, intent)
    return resolves.mapNotNull { resolve ->
        val activityInfo = resolve.activityInfo ?: return@mapNotNull null
        val meta = activityInfo.metaData ?: return@mapNotNull null
        val extensionId = meta.getString(ABK_EXTENSION_META_ID)?.trim().orEmpty()
        if (extensionId.isBlank()) return@mapNotNull null
        val packageName = activityInfo.packageName
        val displayName = meta.getString(ABK_EXTENSION_META_NAME)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: activityInfo.loadLabel(context.packageManager)?.toString().orEmpty()
        val oobeComponent = meta.getString(ABK_EXTENSION_META_OOBE_ACTIVITY)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { componentNameFromString(packageName, it) }
        val settingsComponent = meta.getString(ABK_EXTENSION_META_SETTINGS_ACTIVITY)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { componentNameFromString(packageName, it) }
        AbkDiscoveredExtensionApp(
            extensionId = extensionId,
            packageName = packageName,
            displayName = displayName,
            oobeComponent = oobeComponent,
            settingsComponent = settingsComponent,
        )
    }.associateBy { it.extensionId }
}

private fun componentNameFromString(packageName: String, className: String): ComponentName {
    val normalized = if (className.startsWith('.')) "$packageName$className" else className
    return ComponentName(packageName, normalized)
}

@Suppress("DEPRECATION")
private fun isPackageInstalled(
    packageManager: PackageManager,
    packageName: String,
): Boolean {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            packageManager.getPackageInfo(packageName, 0)
        }
    }.isSuccess
}

@Suppress("DEPRECATION")
private fun queryIntentActivities(
    packageManager: PackageManager,
    intent: Intent,
): List<ResolveInfo> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong())
        )
    } else {
        packageManager.queryIntentActivities(intent, PackageManager.GET_META_DATA)
    }
}
