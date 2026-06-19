package com.abk.kernel.utils

import androidx.annotation.Keep
import com.abk.kernel.data.model.RootGrantProfile

object AbkKsuNative {
    const val KERNEL_SU_DOMAIN = "u:r:ksu:s0"
    const val ROOT_UID = 0
    const val ROOT_GID = 0
    private const val NON_ROOT_DEFAULT_PROFILE_KEY = "$"
    private const val NOBODY_UID = 9999

    private val libraryLoaded = runCatching {
        System.loadLibrary("abkksu")
    }.isSuccess

    val loaded: Boolean
        get() = libraryLoaded

    external fun hasDriverFd(): Boolean
    external fun getVersion(): Int
    external fun isSafeMode(): Boolean
    external fun isLkmMode(): Boolean
    external fun isLateLoadMode(): Boolean
    external fun isManager(): Boolean
    external fun isPrBuild(): Boolean
    external fun getFullVersion(): String
    external fun getHookType(): String
    external fun getSuperuserCount(): Int
    external fun uidShouldUmount(uid: Int): Boolean
    external fun getAppProfile(key: String?, uid: Int): Profile?
    external fun setAppProfile(profile: Profile?): Boolean
    external fun isSuEnabled(): Boolean
    external fun setSuEnabled(enabled: Boolean): Boolean
    external fun isKernelUmountEnabled(): Boolean
    external fun setKernelUmountEnabled(enabled: Boolean): Boolean
    external fun isSuLogEnabled(): Boolean
    external fun setSuLogEnabled(enabled: Boolean): Boolean
    external fun getFeature(featureId: Int): Feature?
    external fun isSelinuxHideEnabled(): Boolean
    external fun setSelinuxHideEnabled(enabled: Boolean): Int
    external fun getUserName(uid: Int): String?
    external fun getControlStatus(): String?
    external fun runControlCommand(command: String): Boolean
    private external fun encryptGitHubSecretNative(secretValue: String, publicKeyBase64: String): String

    @Volatile
    private var nativeBridgeAvailable = false

    @Volatile
    private var cachedStatus: NativeStatus? = null

    private fun hasNativeBridge(): Boolean {
        if (!libraryLoaded) return false
        if (nativeBridgeAvailable) return true
        nativeBridgeAvailable = runCatching { hasDriverFd() }.getOrDefault(false)
        return nativeBridgeAvailable
    }

    fun status(): NativeStatus? {
        cachedStatus?.let { return it }
        if (!hasNativeBridge()) return null
        val resolved = runCatching {
            val kernelVersion = getVersion()
            if (kernelVersion <= 0) return null
            val manager = isManager()
            NativeStatus(
                version = kernelVersion,
                fullVersion = getFullVersion().trim(),
                hookType = getHookType().trim(),
                isManager = manager,
                isSafeMode = isSafeMode(),
                isLkmMode = isLkmMode(),
                isLateLoadMode = isLateLoadMode(),
                isPrBuild = isPrBuild(),
                superuserCount = if (manager) getSuperuserCount() else 0
            )
        }.getOrNull()
        if (resolved != null) {
            cachedStatus = resolved
        }
        return resolved
    }

    fun isUsableManager(): Boolean = status()?.isManager == true

    fun readProfile(packageName: String, uid: Int): RootGrantProfile? {
        if (!hasNativeBridge() || packageName.isBlank()) return null
        return runCatching {
            getAppProfile(packageName, uid)?.toRootGrantProfile()
        }.getOrNull()
    }

    fun writeProfile(profile: RootGrantProfile): Boolean {
        if (!hasNativeBridge() || profile.name.isBlank()) return false
        return runCatching {
            setAppProfile(Profile(profile))
        }.getOrDefault(false)
    }

    fun userName(uid: Int): String =
        if (!hasNativeBridge()) "" else runCatching { getUserName(uid).orEmpty() }.getOrDefault("")

    fun controlStatus(): String? {
        if (!hasNativeBridge()) return null
        return runCatching { getControlStatus()?.trim()?.takeIf { it.startsWith("{") } }.getOrNull()
    }

    fun controlCommand(command: String): Boolean {
        if (!hasNativeBridge()) return false
        val cleanCommand = command.trim()
        if (cleanCommand.isBlank()) return false
        return runCatching { runControlCommand(cleanCommand) }.getOrDefault(false)
    }

    fun feature(featureId: Int): Feature? {
        if (!hasNativeBridge()) return null
        return runCatching { getFeature(featureId) }.getOrNull()
    }

    fun encryptGitHubSecret(secretValue: String, publicKeyBase64: String): String {
        check(libraryLoaded) { "Native library unavailable for GitHub secret encryption" }
        require(secretValue.isNotEmpty()) { "Secret value must not be empty" }
        require(publicKeyBase64.isNotBlank()) { "Repository public key must not be blank" }
        return encryptGitHubSecretNative(secretValue, publicKeyBase64.trim())
    }

    fun setDefaultUmountModules(umountModules: Boolean): Boolean {
        if (!hasNativeBridge()) return false
        return runCatching {
            setAppProfile(
                Profile(
                    name = NON_ROOT_DEFAULT_PROFILE_KEY,
                    currentUid = NOBODY_UID,
                    allowSu = false,
                    umountModules = umountModules
                )
            )
        }.getOrDefault(false)
    }

    fun isDefaultUmountModules(): Boolean? {
        if (!hasNativeBridge()) return null
        return runCatching {
            getAppProfile(NON_ROOT_DEFAULT_PROFILE_KEY, NOBODY_UID)?.umountModules
        }.getOrNull()
    }

    data class NativeStatus(
        val version: Int,
        val fullVersion: String,
        val hookType: String,
        val isManager: Boolean,
        val isSafeMode: Boolean,
        val isLkmMode: Boolean,
        val isLateLoadMode: Boolean,
        val isPrBuild: Boolean,
        val superuserCount: Int
    )

    @Keep
    data class Feature(
        val value: Long = 0,
        val supported: Boolean = false
    )

    @Keep
    data class Profile(
        var name: String = "",
        var currentUid: Int = 0,
        var allowSu: Boolean = false,
        var rootUseDefault: Boolean = true,
        var rootTemplate: String? = null,
        var uid: Int = ROOT_UID,
        var gid: Int = ROOT_GID,
        var groups: MutableList<Int> = mutableListOf(),
        var capabilities: MutableList<Int> = mutableListOf(),
        var context: String = KERNEL_SU_DOMAIN,
        var namespace: Int = Namespace.INHERITED.ordinal,
        var nonRootUseDefault: Boolean = true,
        var umountModules: Boolean = true,
        var rules: String = ""
    ) {
        enum class Namespace {
            INHERITED,
            GLOBAL,
            INDIVIDUAL
        }

        constructor(profile: RootGrantProfile) : this(
            name = profile.name,
            currentUid = profile.currentUid,
            allowSu = profile.allowSu,
            rootUseDefault = profile.rootUseDefault,
            rootTemplate = profile.rootTemplate.ifBlank { null },
            uid = profile.uid,
            gid = profile.gid,
            groups = profile.groups.toMutableList(),
            capabilities = profile.capabilities.toMutableList(),
            context = profile.context.ifBlank { KERNEL_SU_DOMAIN },
            namespace = profile.namespace,
            nonRootUseDefault = profile.nonRootUseDefault,
            umountModules = profile.umountModules,
            rules = profile.rules
        )
    }
}

private fun AbkKsuNative.Profile.toRootGrantProfile(): RootGrantProfile =
    RootGrantProfile(
        name = name,
        currentUid = currentUid,
        allowSu = allowSu,
        rootUseDefault = rootUseDefault,
        rootTemplate = rootTemplate.orEmpty(),
        uid = uid,
        gid = gid,
        groups = groups,
        capabilities = capabilities,
        context = context,
        namespace = namespace,
        nonRootUseDefault = nonRootUseDefault,
        umountModules = umountModules,
        rules = rules
    )
