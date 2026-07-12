package com.abk.kernel.data.model

data class SusfsPathRule(
    val path: String = "",
    val maxTries: Int? = null,
)

data class SusfsOpenRedirectRule(
    val originalPath: String = "",
    val redirectedPath: String = "",
    val stage: String = "boot_completed",
    val uidScheme: Int? = null,
)

data class SusfsKstatEntry(
    val path: String = "",
    val ino: String = "default",
    val dev: String = "default",
    val nlink: String = "default",
    val size: String = "default",
    val atime: String = "0",
    val atimeNsec: String = "0",
    val mtime: String = "0",
    val mtimeNsec: String = "0",
    val ctime: String = "0",
    val ctimeNsec: String = "0",
    val blocks: String = "0",
    val blksize: String = "0",
)

data class SusfsPresetOptions(
    val hideCustomRomLevel: Int = 0,
    val hideVendorSepolicy: Boolean = false,
    val hideCompatMatrix: Boolean = false,
    val hideGapps: Boolean = false,
    val hideRevanced: Boolean = false,
    val spoofCmdline: Boolean = false,
    val hideLoops: Boolean = true,
    val forceHideLsposed: Boolean = false,
    val autoTryUmount: Boolean = false,
    val skipLegitMounts: Boolean = false,
    val emulateVoldAppDataMode: Int = 0,
    val umountForZygoteIsoService: Boolean = false,
)

data class SusfsConfig(
    val schemaVersion: Int = 1,
    val autoReplayEnabled: Boolean = true,
    val logEnabled: Boolean = true,
    val avcLogSpoofing: Boolean = false,
    val hideSusMountsMode: String = "off",
    val spoofUnameStage: String = "off",
    val unameValue: String = "default",
    val buildTimeValue: String = "default",
    val sdcardRootPath: String = "/sdcard",
    val androidDataRootPath: String = "/sdcard/Android/data",
    val pathRules: List<SusfsPathRule> = emptyList(),
    val loopPathRules: List<SusfsPathRule> = emptyList(),
    val maps: List<String> = emptyList(),
    val mounts: List<String> = emptyList(),
    val tryUmounts: List<String> = emptyList(),
    val legitMounts: List<String> = emptyList(),
    val openRedirects: List<SusfsOpenRedirectRule> = emptyList(),
    val kstatEntries: List<SusfsKstatEntry> = emptyList(),
    val presets: SusfsPresetOptions = SusfsPresetOptions(),
)

data class SusfsSupportMatrix(
    val log: Boolean = true,
    val hideSusMountsForAll: Boolean = false,
    val hideSusMountsForNonSu: Boolean = false,
    val susPath: Boolean = true,
    val susPathLoop: Boolean = false,
    val susMap: Boolean = false,
    val susMount: Boolean = false,
    val tryUmount: Boolean = false,
    val ksudKernelUmountFallback: Boolean = false,
    val openRedirect: Boolean = false,
    val staticKstat: Boolean = false,
    val dynamicKstat: Boolean = false,
    val setUname: Boolean = false,
    val setCmdlineOrBootconfig: Boolean = false,
    val setProcCmdline: Boolean = false,
    val sdcardRootPath: Boolean = false,
    val androidDataRootPath: Boolean = false,
    val avcLogSpoofing: Boolean = false,
    val spoofCmdlinePreset: Boolean = false,
    val hideVendorSepolicyPreset: Boolean = false,
    val hideCompatMatrixPreset: Boolean = false,
    val hideGappsPreset: Boolean = false,
    val hideRevancedPreset: Boolean = false,
    val hideLoopsPreset: Boolean = true,
    val autoTryUmountPreset: Boolean = false,
    val forceHideLsposedPreset: Boolean = false,
    val umountForZygoteIsoService: Boolean = false,
)

data class SusfsRuntimeStatus(
    val available: Boolean = false,
    val kernelVersion: String = "",
    val rawFeatureText: String = "",
    val featureFlags: List<String> = emptyList(),
    val support: SusfsSupportMatrix = SusfsSupportMatrix(),
    val bundledBinaryRef: String = "",
    val bundledBinaryVersion: String = "",
    val bundledBinaryPublishedAt: String = "",
    val bundledBinaryPath: String = "",
    val installedBinaryPath: String = "",
    val runtimeModuleId: String = "abk-susfs-control",
    val runtimeModuleDir: String = "",
    val configPath: String = "",
    val diagnostics: List<String> = emptyList(),
)
