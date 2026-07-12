package com.abk.kernel.viewmodel

import com.abk.kernel.data.model.AbkRuntimeModule

internal fun sortRuntimeModulesForDisplay(modules: List<AbkRuntimeModule>): List<AbkRuntimeModule> =
    modules.sortedWith(
        compareBy<AbkRuntimeModule> { it.runtimeTypeOrder() }
            .thenBy { !it.enabled }
            .thenBy { it.runtimeDisplayName().lowercase() }
    )

internal fun AbkRuntimeModule.runtimeDisplayName(): String =
    name.ifBlank { id.ifBlank { runtimeRepoName() } }

private fun AbkRuntimeModule.runtimeRepoName(): String =
    repoUrl
        .trim()
        .trimEnd('/')
        .removeSuffix(".git")
        .substringAfterLast('/')
        .ifBlank { "unknown" }

private fun AbkRuntimeModule.runtimeTypeOrder(): Int = when (normalizedType()) {
    "builtin" -> 0
    "standard" -> 1
    "kpm" -> 2
    else -> 3
}
