package com.abk.kernel.viewmodel

import com.abk.kernel.data.model.AbkRuntimeModule

internal enum class RuntimeModuleControlBackend {
    ABK_CONTROL,
    KSU,
    NONE
}

internal enum class RuntimeModuleActionBackend {
    ABK_ACTION_SCRIPT,
    KSU_ACTION,
    NONE
}

internal fun AbkRuntimeModule.preferredControlBackend(): RuntimeModuleControlBackend =
    when {
        controllable && hasRuntimeSource("abk") -> RuntimeModuleControlBackend.ABK_CONTROL
        normalizedRuntimeModuleType() == "standard" || hasRuntimeSource("ksud") -> RuntimeModuleControlBackend.KSU
        else -> RuntimeModuleControlBackend.NONE
    }

internal fun AbkRuntimeModule.preferredActionBackend(): RuntimeModuleActionBackend =
    when {
        !actionSupported && !hasActionScript -> RuntimeModuleActionBackend.NONE
        hasRuntimeSource("abk") -> RuntimeModuleActionBackend.ABK_ACTION_SCRIPT
        normalizedRuntimeModuleType() == "standard" || hasRuntimeSource("ksud") -> RuntimeModuleActionBackend.KSU_ACTION
        else -> RuntimeModuleActionBackend.ABK_ACTION_SCRIPT
    }

internal fun AbkRuntimeModule.isAbkMetaMount(): Boolean =
    id.trim() == "meta-abk-mount"

private fun AbkRuntimeModule.normalizedRuntimeModuleType(): String =
    type.ifBlank {
        when {
            hasRuntimeSource("kpm") -> "kpm"
            hasRuntimeSource("ksud") -> "standard"
            else -> "builtin"
        }
    }

private fun AbkRuntimeModule.hasRuntimeSource(value: String): Boolean =
    source.split(',').any { it.trim() == value }
