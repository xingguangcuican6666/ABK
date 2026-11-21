package com.abk.kernel.ui.screens

import com.abk.kernel.data.model.AbkRuntimeModule

internal fun AbkRuntimeModule.matchesRuntimeModuleQuery(query: String): Boolean {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) return true
    return listOf(id, name, version, description, repoUrl, stage, type, source, author).any {
        it.contains(cleanQuery, ignoreCase = true)
    }
}

internal fun AbkRuntimeModule.displayName(): String = name.ifBlank { id.ifBlank { repoName() } }

internal fun AbkRuntimeModule.repoName(): String =
    repoUrl.trim().trimEnd('/').removeSuffix(".git").substringAfterLast('/').ifBlank { "unknown" }

internal fun AbkRuntimeModule.normalizedType(): String =
    type.ifBlank {
        when {
            source.split(',').any { it.trim() == "kpm" } -> "kpm"
            source.split(',').any { it.trim() == "ksud" } -> "standard"
            else -> "builtin"
        }
    }

internal fun AbkRuntimeModule.canUninstallRuntimeModule(): Boolean =
    normalizedType() == "standard" && controllable && !readonly

internal data class RuntimeModuleDisplayGroup(
    val groupName: String? = null,
    val groupDescription: String? = null,
    val modules: List<AbkRuntimeModule> = emptyList(),
)

internal fun groupRuntimeModulesForDisplay(
    modules: List<AbkRuntimeModule>
): List<RuntimeModuleDisplayGroup> =
    modules
        .groupBy { module ->
            module.groupRepoUrl.trim().takeIf { it.isNotBlank() }?.let { "repo:${it.lowercase()}" }
                ?: module.groupId.trim().ifBlank {
                    module.groupName
                        .trim()
                        .takeIf { it.isNotBlank() }
                        ?.let { "name:${it.lowercase()}" } ?: "single:${module.id}"
                }
        }
        .values
        .map { grouped ->
            val first = grouped.first()
            RuntimeModuleDisplayGroup(
                groupName = first.groupName.trim().takeIf { it.isNotBlank() },
                groupDescription = first.groupDescription.trim().takeIf { it.isNotBlank() },
                modules = grouped.sortedBy { it.displayName().lowercase() },
            )
        }
        .sortedBy { it.groupName.orEmpty().lowercase() }
