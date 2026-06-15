package com.abk.kernel.data.model

import com.google.gson.annotations.SerializedName

data class GitHubUser(
    val login: String,
    val id: Long,
    val name: String?,
    @SerializedName("avatar_url") val avatarUrl: String,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("public_repos") val publicRepos: Int = 0
)

data class GitHubRepo(
    val id: Long,
    val name: String,
    @SerializedName("full_name") val fullName: String,
    val fork: Boolean,
    val private: Boolean,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("default_branch") val defaultBranch: String = "main",
    val parent: GitHubRepo? = null,
    val owner: GitHubUser? = null
)

data class ForkRequest(
    @SerializedName("default_branch_only") val defaultBranchOnly: Boolean = false
)

data class SyncForkRequest(
    val branch: String,
    @SerializedName("force") val force: Boolean = false
)

data class SyncForkResponse(
    val message: String,
    @SerializedName("merge_type") val mergeType: String?,
    @SerializedName("base_branch") val baseBranch: String?
)

data class CompareResult(
    val status: String,
    @SerializedName("ahead_by") val aheadBy: Int,
    @SerializedName("behind_by") val behindBy: Int,
    @SerializedName("total_commits") val totalCommits: Int
)

data class WorkflowDispatchRequest(
    val ref: String = "main",
    val inputs: Map<String, String>
)

data class WorkflowRun(
    val id: Long,
    val name: String?,
    val status: String,
    val conclusion: String?,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("run_number") val runNumber: Int,
    @SerializedName("workflow_id") val workflowId: Long,
    @SerializedName("head_branch") val headBranch: String?,
    @SerializedName("display_title") val displayTitle: String?
)

data class WorkflowRunsResponse(
    @SerializedName("total_count") val totalCount: Int,
    @SerializedName("workflow_runs") val workflowRuns: List<WorkflowRun>
)

data class WorkflowJobsResponse(
    @SerializedName("total_count") val totalCount: Int,
    val jobs: List<WorkflowJob> = emptyList()
)

data class WorkflowJob(
    val id: Long,
    val name: String,
    val status: String?,
    val conclusion: String?,
    val steps: List<WorkflowStep>? = emptyList()
)

data class WorkflowStep(
    val name: String,
    val status: String?,
    val conclusion: String?,
    val number: Int
)

data class BuildStepProgress(
    val name: String,
    val status: String,
    val conclusion: String?,
    val index: Int
)

data class BuildProgress(
    val percent: Int = 0,
    val currentStep: String = "",
    val completedSteps: Int = 0,
    val totalSteps: Int = 0,
    val steps: List<BuildStepProgress> = emptyList()
)

data class BuildParameterSummary(
    val runId: Long,
    val runNumber: Int = 0,
    val runTitle: String = "",
    val runCreatedAt: String = "",
    val runHtmlUrl: String = "",
    val androidVersion: String = "",
    val kernelVersion: String = "",
    val subLevel: String = "",
    val osPatchLevel: String = "",
    val ksuVariant: String = "",
    val ksuBranch: String = "",
    val buildTime: String = "",
    val susfsEnabled: String = "",
    val zramEnabled: String = "",
    val zramFullAlgo: String = "",
    val zramExtraAlgos: String = "",
    val bbgEnabled: String = "",
    val ddkLsm: String = "",
    val ntsyncEnabled: String = "",
    val networkingEnabled: String = "",
    val kpmEnabled: String = "",
    val kpmPassword: String = "",
    val reKernelEnabled: String = "",
    val virtualizationSupport: String = "",
    val customInjection: String = "",
    val stockConfig: String = "",
    val source: String = "workflow_log",
    val extraRows: Map<String, String>? = null
)

data class Artifact(
    val id: Long,
    val name: String,
    @SerializedName("size_in_bytes") val sizeInBytes: Long,
    @SerializedName("archive_download_url") val archiveDownloadUrl: String,
    val expired: Boolean,
    @SerializedName("created_at") val createdAt: String
)

data class BuildArtifact(
    val id: Long,
    val name: String,
    val sizeInBytes: Long,
    val archiveDownloadUrl: String,
    val expired: Boolean,
    val createdAt: String,
    val runId: Long,
    val runTitle: String,
    val runNumber: Int,
    val runCreatedAt: String,
    val runHtmlUrl: String = ""
)

fun Artifact.withRun(run: WorkflowRun): BuildArtifact = BuildArtifact(
    id = id,
    name = name,
    sizeInBytes = sizeInBytes,
    archiveDownloadUrl = archiveDownloadUrl,
    expired = expired,
    createdAt = createdAt,
    runId = run.id,
    runTitle = run.displayTitle ?: run.name ?: "#${run.runNumber}",
    runNumber = run.runNumber,
    runCreatedAt = run.createdAt,
    runHtmlUrl = run.htmlUrl
)

fun BuildArtifact.toArtifact(): Artifact = Artifact(
    id = id,
    name = name,
    sizeInBytes = sizeInBytes,
    archiveDownloadUrl = archiveDownloadUrl,
    expired = expired,
    createdAt = createdAt
)

fun BuildArtifact.toWorkflowRun(): WorkflowRun = WorkflowRun(
    id = runId,
    name = runTitle,
    status = "completed",
    conclusion = "success",
    htmlUrl = runHtmlUrl,
    createdAt = runCreatedAt,
    updatedAt = runCreatedAt,
    runNumber = runNumber,
    workflowId = 0L,
    headBranch = null,
    displayTitle = runTitle
)

data class ArtifactsResponse(
    @SerializedName("total_count") val totalCount: Int,
    val artifacts: List<Artifact>
)

data class GitHubRelease(
    val id: Long = 0L,
    val name: String? = null,
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("published_at") val publishedAt: String? = null,
    val body: String? = null,
    @SerializedName("assets_url") val assetsUrl: String? = null,
    val assets: List<ReleaseAsset> = emptyList()
)

data class GitHubReleaseSummary(
    val id: Long = 0L,
    val name: String? = null,
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("published_at") val publishedAt: String? = null,
    val body: String? = null,
    @SerializedName("assets_url") val assetsUrl: String? = null
)

data class ReleaseAsset(
    val id: Long = 0L,
    val name: String,
    val size: Long = 0L,
    @SerializedName("content_type") val contentType: String? = null,
    @SerializedName("browser_download_url") val browserDownloadUrl: String
)

data class PrebuiltGkiAsset(
    val id: Long,
    val name: String,
    val sizeBytes: Long,
    val browserDownloadUrl: String,
    val contentType: String? = null,
    val releaseTag: String,
    val releaseName: String,
    val releaseHtmlUrl: String,
    val publishedAt: String,
    val releaseBody: String = ""
)

data class PrebuiltGkiRelease(
    val id: Long,
    val apiId: Long = id,
    val tagName: String,
    val name: String,
    val htmlUrl: String,
    val publishedAt: String,
    val body: String = "",
    val assetCount: Int = 0
)

const val APP_UPDATE_STABILITY_STABLE = "stable"
const val APP_UPDATE_STABILITY_UNSTABLE = "unstable"
const val APP_UPDATE_LINE_NORMAL = "normal"
const val APP_UPDATE_LINE_DEV = "dev"

val APP_UPDATE_STABILITY_OPTIONS = listOf(
    APP_UPDATE_STABILITY_STABLE,
    APP_UPDATE_STABILITY_UNSTABLE,
)

val APP_UPDATE_LINE_OPTIONS = listOf(
    APP_UPDATE_LINE_NORMAL,
    APP_UPDATE_LINE_DEV,
)

data class AppUpdateMetadata(
    val stable: AppUpdateChannelEntries = AppUpdateChannelEntries(),
    val unstable: AppUpdateChannelEntries = AppUpdateChannelEntries()
) {
    fun entryFor(stability: String, line: String): AppUpdateEntry? {
        val normalizedStability = normalizeAppUpdateStability(stability)
        val normalizedLine = normalizeAppUpdateLine(line)
        val channel = when (normalizedStability) {
            APP_UPDATE_STABILITY_UNSTABLE -> unstable
            else -> stable
        }
        return when (normalizedLine) {
            APP_UPDATE_LINE_DEV -> channel.dev
            else -> channel.normal
        }
    }
}

data class AppUpdateChannelEntries(
    val normal: AppUpdateEntry? = null,
    val dev: AppUpdateEntry? = null
)

data class AppUpdateEntry(
    val versionName: String = "",
    val versionCode: Long = 0L,
    val downloadUrl: String = "",
    val publishedAt: String = "",
    val buildTimestampEpochMillis: Long = 0L,
    val sourceWorkflow: String = "",
    val commitSha: String = "",
    val runId: Long = 0L
)

data class AppUpdateCheckResult(
    val stability: String,
    val line: String,
    val currentVersionName: String,
    val currentVersionCode: Long,
    val currentBuildTimestampEpochMillis: Long,
    val remote: AppUpdateEntry,
    val hasUpdate: Boolean
)

fun normalizeAppUpdateStability(value: String): String = when (value.trim().lowercase()) {
    APP_UPDATE_STABILITY_UNSTABLE -> APP_UPDATE_STABILITY_UNSTABLE
    else -> APP_UPDATE_STABILITY_STABLE
}

fun normalizeAppUpdateLine(value: String): String = when (value.trim().lowercase()) {
    APP_UPDATE_LINE_DEV -> APP_UPDATE_LINE_DEV
    else -> APP_UPDATE_LINE_NORMAL
}

fun shouldOfferAppUpdate(
    remote: AppUpdateEntry,
    currentVersionCode: Long,
    currentBuildTimestampEpochMillis: Long
): Boolean = when {
    remote.versionCode > currentVersionCode -> true
    remote.versionCode < currentVersionCode -> false
    remote.buildTimestampEpochMillis > currentBuildTimestampEpochMillis -> true
    else -> false
}

// GitHub Device Flow OAuth
data class DeviceCodeResponse(
    @SerializedName("device_code") val deviceCode: String,
    @SerializedName("user_code") val userCode: String,
    @SerializedName("verification_uri") val verificationUri: String,
    @SerializedName("expires_in") val expiresIn: Int,
    @SerializedName("interval") val interval: Int
)

data class AccessTokenResponse(
    @SerializedName("access_token") val accessToken: String?,
    @SerializedName("token_type") val tokenType: String?,
    @SerializedName("scope") val scope: String?,
    val error: String?,
    @SerializedName("error_description") val errorDescription: String?
)

data class Workflow(
    val id: Long,
    val name: String,
    val path: String,
    val state: String
)

data class WorkflowsResponse(
    @SerializedName("total_count") val totalCount: Int,
    val workflows: List<Workflow>
)

object CustomExternalModuleStage {
    const val AFTER_PATCH = "after_patch"
    const val BEFORE_BUILD = "before_build"

    val options = listOf(AFTER_PATCH, BEFORE_BUILD)

    fun normalize(stage: String): String = when (
        stage.trim().lowercase().replace('-', '_').replace(' ', '_')
    ) {
        AFTER_PATCH -> AFTER_PATCH
        BEFORE_BUILD, "befor_build" -> BEFORE_BUILD
        else -> AFTER_PATCH
    }
}

data class CustomExternalModule(
    val url: String = "",
    val stage: String = CustomExternalModuleStage.AFTER_PATCH,
    val entryKind: String = CustomExternalModuleEntryKind.MODULE,
    val groupRepoUrl: String = "",
    val childId: String = "",
    val childName: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val groupRole: String = "",
    val groupDescription: String = ""
)

object CustomExternalModuleEntryKind {
    const val MODULE = "module"
    const val MODULE_SET_CHILD = "module_set_child"

    fun normalize(value: String?): String = when (value?.trim()?.lowercase()) {
        MODULE_SET_CHILD, "set", "module_set", "module-set", "group_child" -> MODULE_SET_CHILD
        else -> MODULE
    }
}

object ModuleCatalogItemKind {
    const val MODULE = "module"
    const val MODULE_SET = "module_set"

    fun normalize(value: String?): String = when (value?.trim()?.lowercase()) {
        MODULE_SET, "set", "module-set", "moduleset" -> MODULE_SET
        else -> MODULE
    }
}

data class ModuleSetChildMetadata(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val repoUrl: String = "",
    val supportedStages: List<String> = CustomExternalModuleStage.options,
    val defaultStage: String = CustomExternalModuleStage.AFTER_PATCH,
    val recommendedStages: List<String> = listOf(CustomExternalModuleStage.AFTER_PATCH),
    val groupRole: String = "",
    val controllable: Boolean = false,
    val hasWebUi: Boolean = false,
    val magiskModuleName: String = "",
    val magiskModuleDownloadUrl: String = ""
)

data class ExternalModuleMetadata(
    val name: String,
    val version: String = "",
    val description: String = "",
    val kind: String = ModuleCatalogItemKind.MODULE,
    val moduleSetId: String = "",
    val supportedStages: List<String> = CustomExternalModuleStage.options,
    val defaultStage: String = CustomExternalModuleStage.AFTER_PATCH,
    val recommendedStages: List<String> = listOf(CustomExternalModuleStage.AFTER_PATCH),
    val children: List<ModuleSetChildMetadata> = emptyList(),
    val magiskModuleName: String = "",
    val magiskModuleDownloadUrl: String = ""
)

data class ModuleCatalogItem(
    val name: String = "",
    val version: String = "",
    val description: String = "",
    val kind: String = ModuleCatalogItemKind.MODULE,
    val moduleSetId: String = "",
    val repoUrl: String = "",
    val defaultStage: String = CustomExternalModuleStage.AFTER_PATCH,
    val supportedStages: List<String> = listOf(CustomExternalModuleStage.AFTER_PATCH),
    val recommendedStages: List<String> = listOf(CustomExternalModuleStage.AFTER_PATCH),
    val author: String = "",
    val homepage: String = ""
)

data class RuntimeModuleCatalogItem(
    val id: String = "",
    val name: String = "",
    val version: String = "",
    val versionCode: Long = 0L,
    val author: String = "",
    val description: String = "",
    val zipUrl: String = "",
    val changelog: String = "",
    val support: String = "",
    val donate: String = "",
    val website: String = "",
    val cover: String = "",
    val icon: String = "",
    val verified: Boolean = false,
    val minApi: Int? = null,
    val maxApi: Int? = null
)

data class ModuleCatalogRepository(
    val id: String = "",
    val url: String = "",
    val indexJsonUrl: String = "",
    val name: String = "",
    val modules: List<ModuleCatalogItem> = emptyList(),
    val lastUpdated: Long = 0L,
    val error: String? = null,
    val skippedCount: Int = 0
)

data class RuntimeModuleRepository(
    val id: String = "",
    val url: String = "",
    val indexJsonUrl: String = "",
    val name: String = "",
    val modules: List<RuntimeModuleCatalogItem> = emptyList(),
    val lastUpdated: Long = 0L,
    val error: String? = null,
    val skippedCount: Int = 0
)

data class ModuleCatalogFetchResult(
    val name: String,
    val indexUrl: String,
    val modules: List<ModuleCatalogItem>,
    val skippedCount: Int
)

data class RuntimeModuleCatalogFetchResult(
    val name: String,
    val indexUrl: String,
    val modules: List<RuntimeModuleCatalogItem>,
    val skippedCount: Int
)

const val KSU_BRANCH_STABLE = "Stable(标准)"
const val KSU_BRANCH_DEV = "Dev(开发)"
const val KSU_BRANCH_LATEST = "Latest(最新)"
const val KSU_BRANCH_CUSTOM = "Custom(自定义)"
const val KSU_VARIANT_NONE = "None"
const val KSU_VARIANT_OFFICIAL = "Official"
const val KSU_VARIANT_SUKISU = "SukiSU"
const val KSU_VARIANT_RESUKISU = "ReSukiSU"
const val BUILD_TARGET_GKI = "gki"
const val BUILD_TARGET_ONEPLUS = "oneplus"

val KSU_BRANCH_STANDARD_OPTIONS = listOf(
    KSU_BRANCH_STABLE,
    KSU_BRANCH_DEV,
    KSU_BRANCH_LATEST,
    KSU_BRANCH_CUSTOM,
)
val KSU_BRANCH_BUILD_PLAN_OPTIONS = KSU_BRANCH_STANDARD_OPTIONS
val KSU_VARIANT_OPTIONS = listOf(
    KSU_VARIANT_OFFICIAL,
    KSU_VARIANT_SUKISU,
    KSU_VARIANT_RESUKISU,
    KSU_VARIANT_NONE
)
val ONEPLUS_KSU_VARIANT_OPTIONS = listOf(
    KSU_VARIANT_OFFICIAL,
    KSU_VARIANT_SUKISU,
    KSU_VARIANT_RESUKISU,
    KSU_VARIANT_NONE
)

// App-level build config model (mirrors kernel-custom.yml inputs)
data class KernelBuildConfig(
    val buildTarget: String = BUILD_TARGET_GKI,
    val androidVersion: String = "android12",
    val kernelVersion: String = "5.10",
    val subLevel: String = "66",
    val osPatchLevel: String = "2022-01",
    val revision: String = "r11",
    val kernelsuVariant: String = KSU_VARIANT_RESUKISU,
    val kernelsuBranch: String = KSU_BRANCH_STABLE,
    val customRef: String = "",
    val version: String = "",
    val buildTime: String = "",
    val useZram: Boolean = false,
    val useBbg: Boolean = false,
    val useDdk: Boolean = false,
    val useNtsync: Boolean = false,
    val useNetworking: Boolean = false,
    val useKpm: Boolean = false,
    val useRekernel: Boolean = false,
    val cancelSusfs: Boolean = false,
    val suppOp: Boolean = false,
    val zramFullAlgo: Boolean = false,
    val zramExtraAlgos: String = "",
    val kpmPassword: String = "",
    val virtualizationSupport: String = "off",
    val useCustomExternalModules: Boolean = false,
    val customExternalModules: List<CustomExternalModule> = emptyList(),
    val onePlusCpu: String = "sm8650",
    val onePlusDeviceManifest: String = "oneplus_12_b",
    val onePlusUseLz4kd: Boolean = false,
    val onePlusUseBbr: Boolean = false,
    val onePlusUseProxyOptimization: Boolean = true,
    val onePlusUseUnicodeBypass: Boolean = false
)

data class AbkRuntimeStatus(
    val schema: Int = 1,
    @SerializedName("abk_version") val abkVersion: String = "",
    @SerializedName("abk_commit") val abkCommit: String = "",
    @SerializedName("work_mode") val workMode: String = "",
    val manager: AbkRuntimeManagerInfo? = null,
    @SerializedName("runtime_backend") val runtimeBackend: AbkRuntimeManagerInfo? = null,
    val build: AbkRuntimeBuildInfo? = null,
    val modules: List<AbkRuntimeModule> = emptyList(),
    @SerializedName("extension_modules") val extensionModules: List<AbkRuntimeModule> = emptyList()
)

data class AbkRuntimeManagerInfo(
    @SerializedName("display_name") val displayName: String = "",
    val variant: String = "",
    val backend: String = "",
    val version: String = "",
    val active: Boolean = false,
    val capabilities: List<String> = emptyList(),
    val diagnostics: List<String> = emptyList()
)

data class AbkRuntimeBuildInfo(
    @SerializedName("android_version") val androidVersion: String = "",
    @SerializedName("kernel_version") val kernelVersion: String = "",
    @SerializedName("sub_level") val subLevel: String = "",
    @SerializedName("os_patch_level") val osPatchLevel: String = "",
    val revision: String = "",
    @SerializedName("kernelsu_variant") val kernelsuVariant: String = "",
    @SerializedName("kernelsu_branch") val kernelsuBranch: String = "",
    val version: String = "",
    @SerializedName("build_time") val buildTime: String = "",
    @SerializedName("virtualization_support") val virtualizationSupport: String = "",
    @SerializedName("zram_extra_algos") val zramExtraAlgos: String = "",
    val features: Map<String, Boolean> = emptyMap()
)

data class AbkRuntimeModule(
    val id: String = "",
    val name: String = "",
    val author: String = "",
    val type: String = "",
    val version: String = "",
    @SerializedName("version_code") val versionCode: Long = 0L,
    val description: String = "",
    @SerializedName("repo_url") val repoUrl: String = "",
    val stage: String = "",
    @SerializedName("entry_kind") val entryKind: String = "",
    val source: String = "",
    @SerializedName("extension_id") val extensionId: String = "",
    @SerializedName("companion_package") val companionPackage: String = "",
    @SerializedName("companion_display_name") val companionDisplayName: String = "",
    @SerializedName("companion_asset_name") val companionAssetName: String = "",
    @SerializedName("companion_download_url") val companionDownloadUrl: String = "",
    @SerializedName("service_activity") val serviceActivity: String = "",
    @SerializedName("module_dir") val moduleDir: String = "",
    @SerializedName("web_root") val webRoot: String = "",
    val readonly: Boolean = false,
    val controllable: Boolean = false,
    val enabled: Boolean = true,
    val update: Boolean = false,
    val remove: Boolean = false,
    @SerializedName("has_web_ui") val hasWebUi: Boolean = false,
    @SerializedName("has_action_script") val hasActionScript: Boolean = false,
    @SerializedName("action_supported") val actionSupported: Boolean = false,
    @SerializedName("requires_companion_app") val requiresCompanionApp: Boolean = false,
    @SerializedName("settings_supported") val settingsSupported: Boolean = false,
    @SerializedName("per_app_supported") val perAppSupported: Boolean = false,
    @SerializedName("oobe_priority") val oobePriority: Int = 0,
    @SerializedName("kpm_args") val kpmArgs: String = "",
    @SerializedName("group_id") val groupId: String = "",
    @SerializedName("group_name") val groupName: String = "",
    @SerializedName("group_role") val groupRole: String = "",
    @SerializedName("group_description") val groupDescription: String = "",
    @SerializedName("group_repo_url") val groupRepoUrl: String = ""
)

enum class ManagerSettingKind {
    SWITCH,
    MODE,
    NAVIGATION
}

enum class ManagerSettingStatus {
    SUPPORTED,
    UNSUPPORTED,
    MANAGED
}

data class ManagerSettingItem(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val kind: ManagerSettingKind = ManagerSettingKind.SWITCH,
    val checked: Boolean = false,
    val selectedIndex: Int = 0,
    val options: List<String> = emptyList(),
    val enabled: Boolean = true,
    val status: ManagerSettingStatus = ManagerSettingStatus.SUPPORTED
)

data class AppProfileTemplateItem(
    val id: String = "",
    val content: String = ""
)

data class RootGrantApp(
    val packageName: String = "",
    val label: String = "",
    val uid: Int = 0,
    val userName: String = "",
    val isSystemApp: Boolean = false,
    val profile: RootGrantProfile = RootGrantProfile()
)

data class RootGrantProfile(
    val name: String = "",
    val currentUid: Int = 0,
    val allowSu: Boolean = false,
    val rootUseDefault: Boolean = true,
    val rootTemplate: String = "",
    val uid: Int = 0,
    val gid: Int = 0,
    val groups: List<Int> = emptyList(),
    val capabilities: List<Int> = emptyList(),
    val context: String = "u:r:ksu:s0",
    val namespace: Int = 0,
    val nonRootUseDefault: Boolean = true,
    val umountModules: Boolean = true,
    val rules: String = ""
)

data class BuildPlan(
    val id: String = "",
    val name: String = "",
    val config: KernelBuildConfig = KernelBuildConfig(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class BuildQueueItem(
    val id: String = "",
    val name: String = "",
    val config: KernelBuildConfig = KernelBuildConfig(),
    val createdAt: Long = 0L,
    val status: BuildQueueItemStatus = BuildQueueItemStatus.PENDING,
    /** GitHub workflow id for the dispatched YAML; used to link runs without stealing another slot. */
    val workflowId: Long = 0L,
    val runId: Long = 0L,
    val runNumber: Int = 0,
    val error: String? = null
)

enum class BuildQueueItemStatus {
    PENDING,
    DISPATCHING,
    RUNNING,
    DONE,
    FAILED,
    CANCELLED
}

data class ActiveDownloadTask(
    val key: Long,
    val artifactId: Long,
    val runId: Long,
    val name: String,
    val runTitle: String,
    val runNumber: Int = 0,
    val progress: Int = 0,
    val automatic: Boolean = false
)

data class DownloadedArtifact(
    val id: Long,
    val name: String,
    val filePath: String,
    val type: ArtifactType,
    val sizeBytes: Long,
    val runId: Long = -1L,
    val runTitle: String = "",
    val runNumber: Int = 0,
    val sourceAssetId: Long = 0L,
    val sourceAssetName: String? = null,
    val category: ArtifactCategory = type.toArtifactCategory()
)

const val PREBUILT_GKI_RUN_ID: Long = -2L

enum class ArtifactType {
    KERNEL_PACKAGE,
    KERNEL_IMG,
    ANYKERNEL3,
    ABK_MANAGER,
    KSU_MANAGER,
    SUSFS_MODULE,
    OTHER
}

enum class ArtifactCategory {
    KERNEL,
    MANAGER,
    MODULE
}

fun ArtifactType.toArtifactCategory(): ArtifactCategory = when (this) {
    ArtifactType.KERNEL_PACKAGE,
    ArtifactType.KERNEL_IMG,
    ArtifactType.ANYKERNEL3 -> ArtifactCategory.KERNEL
    ArtifactType.ABK_MANAGER,
    ArtifactType.KSU_MANAGER -> ArtifactCategory.MANAGER
    ArtifactType.SUSFS_MODULE -> ArtifactCategory.MODULE
    ArtifactType.OTHER -> ArtifactCategory.MODULE
}

enum class BuildStatus {
    IDLE, QUEUED, IN_PROGRESS, SUCCESS, FAILURE, CANCELLED
}

/** Completed run with `conclusion == failure` (Flash failed-card list). */
fun WorkflowRun.isFailedFlashRun(): Boolean =
    status == "completed" && conclusion == "failure"

/**
 * Kernel build vs manager build (KSU Manager, Build ABK App, etc.).
 * Uses workflow [name] first — [displayTitle] can mention the wrong build type.
 * Status "Last build" tile uses this to ignore manager runs.
 */
fun WorkflowRun.isKernelBuild(): Boolean {
    val workflowName = name.orEmpty().lowercase()
    val lower = "${name.orEmpty()} ${displayTitle.orEmpty()}".lowercase()
    if (lower.hasUtilityWorkflowSignal()) return false
    // The workflow name is more reliable than displayTitle, which can contain
    // user/commit text from a different build type.
    if (workflowName.hasManagerBuildSignal()) return false
    if (workflowName.hasKernelBuildSignal()) return true
    // Negative signals: app / manager / certificate / utility workflows.
    if (lower.hasManagerBuildSignal()) return false
    // Positive signals: kernel build.
    if (lower.hasKernelBuildSignal()) return true
    // Unknown — be conservative and exclude it from the kernel-only tile.
    return false
}

/**
 * Heuristic check whether this workflow run is a manager-app build (KSU
 * Manager / SukiSU Manager / Build ABK App). Symmetric to [isKernelBuild];
 * a single run is either kernel-like or manager-like, never both — kernel
 * runs that bundle a manager APK are still classified as kernel.
 */
fun WorkflowRun.isManagerBuild(): Boolean {
    val workflowName = name.orEmpty().lowercase()
    val lower = "${name.orEmpty()} ${displayTitle.orEmpty()}".lowercase()
    if (lower.hasUtilityWorkflowSignal()) return false
    // The GitHub run display title can contain kernel parameters from the
    // triggering commit/title. The workflow name is the primary classifier.
    if (workflowName.hasManagerBuildSignal()) return true
    if (workflowName.hasKernelBuildSignal()) return false
    // Kernel workflows often bundle a manager APK but are not manager-primary.
    if (lower.hasKernelBuildSignal()) return false
    return lower.hasManagerBuildSignal()
}

/** Manager-primary workflow (Build ABK App / Dev), not a kernel build that bundles a manager APK. */
fun WorkflowRun.isPureManagerBuild(): Boolean =
    isManagerBuild() && !isKernelBuild()

/** ABK app workflow only (Build ABK App / Build ABK App Dev). */
fun WorkflowRun.isAbkManagerBuild(): Boolean {
    val workflowName = name.orEmpty().lowercase()
    val lower = "${name.orEmpty()} ${displayTitle.orEmpty()}".lowercase()
    if (lower.hasUtilityWorkflowSignal()) return false
    if (workflowName.hasAbkManagerBuildSignal()) return true
    if (workflowName.hasKernelBuildSignal()) return false
    if (lower.hasKernelBuildSignal()) return false
    return lower.hasAbkManagerBuildSignal()
}

/**
 * Dev manager workflow (e.g. Build ABK App Dev). Uses workflow [name] only — not
 * [WorkflowRun.displayTitle]. Avoids bare `"dev" in text` so "device" does not match.
 */
fun WorkflowRun.isManagerDevBuild(): Boolean =
    workflowNameIndicatesManagerDev(name.orEmpty())

/** Same rules as [WorkflowRun.isManagerDevBuild] for artifact [runTitle] when [WorkflowRun] is missing. */
fun workflowNameIndicatesManagerDev(workflowName: String): Boolean {
    val n = workflowName.lowercase().trim()
    if (n.isBlank()) return false
    if (MANAGER_DEV_WORKFLOW_NAME_MARKERS.any { it in n }) return true
    return MANAGER_DEV_NAME_TOKEN.containsMatchIn(n)
}

private val MANAGER_DEV_WORKFLOW_NAME_MARKERS = listOf(
    "abk app dev",
    "abk-app-dev",
    "build abk app dev",
    "build-app-dev",
    "build app dev"
)

private val MANAGER_DEV_NAME_TOKEN =
    Regex("""(^|[\s_.-])dev($|[\s_.-]|\.apk)""", RegexOption.IGNORE_CASE)

private fun String.hasAbkManagerBuildSignal(): Boolean =
    "abk app" in this || "abk-app" in this ||
        "build abk app" in this

private fun String.hasManagerBuildSignal(): Boolean =
    hasAbkManagerBuildSignal() ||
        "build app" in this || "build-app" in this ||
        "debug apk" in this ||
        "manager" in this || "ksu manager" in this || "sukisu manager" in this ||
        "getmanager" in this || "get manager" in this ||
        "管理器" in this

private fun String.hasKernelBuildSignal(): Boolean =
    "kernel" in this || "内核" in this

private fun String.hasUtilityWorkflowSignal(): Boolean =
    "certificate" in this || "证书" in this ||
        "emergency" in this || "auto trigger" in this
