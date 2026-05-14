package com.abk.kernel.utils

import androidx.annotation.Keep
import com.abk.kernel.data.model.RootGrantProfile

object AbkKsuNative {
    const val KERNEL_SU_DOMAIN = "u:r:ksu:s0"
    const val ROOT_UID = 0
    const val ROOT_GID = 0

    private val libraryLoaded = runCatching {
        System.loadLibrary("abkksu")
    }.isSuccess

    val loaded: Boolean
        get() = libraryLoaded

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
    external fun isSelinuxHideEnabled(): Boolean
    external fun setSelinuxHideEnabled(enabled: Boolean): Int
    external fun getUserName(uid: Int): String?
    external fun getControlStatus(): String?
    external fun runControlCommand(command: String): Boolean

    fun status(): NativeStatus? {
        if (!libraryLoaded) return null
        return runCatching {
            val kernelVersion = getVersion()
            if (kernelVersion <= 0) return null
            NativeStatus(
                version = kernelVersion,
                fullVersion = getFullVersion().trim(),
                hookType = getHookType().trim(),
                isManager = isManager(),
                isSafeMode = isSafeMode(),
                isLkmMode = isLkmMode(),
                isLateLoadMode = isLateLoadMode(),
                isPrBuild = isPrBuild(),
                superuserCount = getSuperuserCount()
            )
        }.getOrNull()
    }

    fun isUsableManager(): Boolean = status()?.isManager == true

    fun readProfile(packageName: String, uid: Int): RootGrantProfile? {
        if (!libraryLoaded || packageName.isBlank()) return null
        return runCatching {
            getAppProfile(packageName, uid)?.toRootGrantProfile()
        }.getOrNull()
    }

    fun writeProfile(profile: RootGrantProfile): Boolean {
        if (!libraryLoaded || profile.name.isBlank()) return false
        return runCatching {
            setAppProfile(Profile(profile))
        }.getOrDefault(false)
    }

    fun userName(uid: Int): String =
        if (!libraryLoaded) "" else runCatching { getUserName(uid).orEmpty() }.getOrDefault("")

    fun controlStatus(): String? {
        if (!libraryLoaded) return null
        return runCatching { getControlStatus()?.trim()?.takeIf { it.startsWith("{") } }.getOrNull()
    }

    fun controlCommand(command: String): Boolean {
        if (!libraryLoaded) return false
        val cleanCommand = command.trim()
        if (cleanCommand.isBlank()) return false
        return runCatching { runControlCommand(cleanCommand) }.getOrDefault(false)
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
