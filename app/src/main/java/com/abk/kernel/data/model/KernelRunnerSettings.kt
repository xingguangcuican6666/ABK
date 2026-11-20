package com.abk.kernel.data.model

import com.google.gson.annotations.SerializedName

enum class KernelRunnerTarget(
    val runnerVariable: String,
    val switchVariable: String,
    val workflowPath: String
) {
    GKI("KERNEL_RUNNER", "KERNEL_SELF_HOSTED", ".github/workflows/build.yml"),
    ONEPLUS("ONEPLUS_RUNNER", "ONEPLUS_SELF_HOSTED", ".github/workflows/oneplus-build.yml")
}

data class KernelRunnerConfig(
    val labels: String = "",
    val selfHostedEnabled: Boolean = false,
    val supported: Boolean = false
)

data class KernelRunnerSettings(
    val gki: KernelRunnerConfig = KernelRunnerConfig(),
    val oneplus: KernelRunnerConfig = KernelRunnerConfig()
)

data class GitHubRepositoryVariable(val name: String, val value: String)

data class GitHubRepositoryVariablesResponse(
    @SerializedName("total_count") val totalCount: Int,
    val variables: List<GitHubRepositoryVariable>
)

data class RepositoryVariableRequest(val name: String, val value: String)
