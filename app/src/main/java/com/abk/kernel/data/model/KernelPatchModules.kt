package com.abk.kernel.data.model

import com.abk.kernel.R
import java.net.URI

/** Build-time module.prop repositories, not runtime ZIP modules or module.conf entries. */
object KernelPatchModules {
    const val MAX_MODULES = 32

    fun validationError(config: KernelBuildConfig): Int? {
        val modules = config.ksuhookModules.orEmpty()
        if (modules.isEmpty()) return null
        if (config.buildTarget == BUILD_TARGET_ONEPLUS || config.kernelsuVariant == KSU_VARIANT_NONE) {
            return R.string.build_patch_unavailable
        }
        if (modules.size > MAX_MODULES || modules.any { !isValidSpec(it) }) return R.string.build_patch_invalid_repo
        if (config.ksuhookSdkRef.orEmpty().isNotBlank() && !Regex("[0-9a-fA-F]{40}").matches(config.ksuhookSdkRef.trim())) {
            return R.string.build_patch_invalid_sdk
        }
        return null
    }

    fun isValidSpec(value: String): Boolean {
        if (value.isBlank() || value != value.trim() || value.length > 2048 ||
            value.any { it.isWhitespace() || it.isISOControl() || it == '|' }) return false
        // Match kernel-patcher.py: only the last @ with no slash/colon suffix is a ref.
        val separator = value.lastIndexOf('@')
        val suffix = value.substring(separator + 1)
        val hasRef = separator > 0 && '/' !in suffix && ':' !in suffix
        val url = if (hasRef) value.substring(0, separator) else value
        if (hasRef && (suffix.isBlank() || '@' in suffix || suffix.startsWith('-'))) return false
        if (url.startsWith("git@")) {
            return Regex("git@[A-Za-z0-9.-]+:[^@\\s|]+").matches(url)
        }
        val parsed = runCatching { URI(url) }.getOrNull() ?: return false
        if (parsed.scheme !in setOf("https", "http", "ssh", "git") || parsed.host.isNullOrBlank()) return false
        if (parsed.query != null || parsed.fragment != null || parsed.path.isNullOrBlank() || '@' in parsed.path) return false
        // SSH usernames are supported; embedded HTTP credentials are not stored in plans.
        return parsed.userInfo == null || (parsed.scheme == "ssh" && ':' !in parsed.userInfo)
    }
}
