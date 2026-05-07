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
    val currentStep: String = "等待 GitHub 分配 Runner",
    val completedSteps: Int = 0,
    val totalSteps: Int = 0,
    val steps: List<BuildStepProgress> = emptyList()
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
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("html_url") val htmlUrl: String,
    val assets: List<ReleaseAsset> = emptyList()
)

data class ReleaseAsset(
    val name: String,
    @SerializedName("browser_download_url") val browserDownloadUrl: String
)

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
    val stage: String = CustomExternalModuleStage.AFTER_PATCH
)

// App-level build config model (mirrors kernel-custom.yml inputs)
data class KernelBuildConfig(
    val androidVersion: String = "android12",
    val kernelVersion: String = "5.10",
    val subLevel: String = "66",
    val osPatchLevel: String = "2022-01",
    val revision: String = "r11",
    val kernelsuVariant: String = "ReSukiSU",
    val kernelsuBranch: String = "Stable(标准)",
    val version: String = "",
    val buildTime: String = "",
    val useZram: Boolean = false,
    val useBbg: Boolean = false,
    val useDdk: Boolean = false,
    val useKpm: Boolean = false,
    val useRekernel: Boolean = false,
    val cancelSusfs: Boolean = false,
    val suppOp: Boolean = false,
    val zramFullAlgo: Boolean = false,
    val zramExtraAlgos: String = "",
    val kpmPassword: String = "",
    val droidspaces: String = "off",
    val useCustomExternalModules: Boolean = false,
    val customExternalModules: List<CustomExternalModule> = emptyList()
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
    val category: ArtifactCategory = type.toArtifactCategory()
)

enum class ArtifactType {
    KERNEL_PACKAGE,
    KERNEL_IMG,
    ANYKERNEL3,
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
    ArtifactType.KSU_MANAGER -> ArtifactCategory.MANAGER
    ArtifactType.SUSFS_MODULE -> ArtifactCategory.MODULE
    ArtifactType.OTHER -> ArtifactCategory.MODULE
}

enum class BuildStatus {
    IDLE, QUEUED, IN_PROGRESS, SUCCESS, FAILURE, CANCELLED
}
