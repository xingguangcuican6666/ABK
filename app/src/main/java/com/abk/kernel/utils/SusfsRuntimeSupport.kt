package com.abk.kernel.utils

import com.abk.kernel.data.model.SusfsConfig
import com.abk.kernel.data.model.SusfsKstatEntry
import com.abk.kernel.data.model.SusfsOpenRedirectRule
import com.abk.kernel.data.model.SusfsPathRule
import com.abk.kernel.data.model.SusfsSupportMatrix
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonParser

const val SUSFS_RUNTIME_MODULE_ID = "abk-susfs-control"
const val SUSFS_ROOT_DIR = "/data/adb/abk/susfs"
const val SUSFS_BINARY_DIR = "$SUSFS_ROOT_DIR/bin"
const val SUSFS_BINARY_PATH = "$SUSFS_BINARY_DIR/ksu_susfs"
const val SUSFS_COMPAT_DIR = "$SUSFS_ROOT_DIR/compat"
const val SUSFS_CONFIG_PATH = "$SUSFS_ROOT_DIR/config.json"
const val SUSFS_RUNTIME_MODULE_DIR = "/data/adb/modules/$SUSFS_RUNTIME_MODULE_ID"
const val SUSFS_HIDE_MOUNTS_OFF = "off"
const val SUSFS_HIDE_MOUNTS_ALL = "all"
const val SUSFS_HIDE_MOUNTS_NON_SU = "non_su"
const val SUSFS_SPOOF_UNAME_OFF = "off"
const val SUSFS_SPOOF_UNAME_POST_FS_DATA = "post_fs_data"
const val SUSFS_SPOOF_UNAME_BOOT_COMPLETED = "boot_completed"
const val SUSFS_OPEN_REDIRECT_STAGE_BOOT_COMPLETED = "boot_completed"
const val SUSFS_OPEN_REDIRECT_STAGE_SERVICE = "service"
const val BUNDLED_SUSFS_REF = "v1.5.2+_R27"
const val BUNDLED_SUSFS_VERSION = "v1.5.2+ Revision 27"
const val BUNDLED_SUSFS_PUBLISHED_AT = "2026-03-27"

private val gsonPretty = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

data class SusfsKernelVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) {
    fun isAtLeast(targetMajor: Int, targetMinor: Int, targetPatch: Int): Boolean =
        when {
            major != targetMajor -> major > targetMajor
            minor != targetMinor -> minor > targetMinor
            else -> patch >= targetPatch
        }
}

fun defaultSusfsLegitMounts(): List<String> = listOf(
    "/system",
    "/system_ext",
    "/vendor",
    "/odm",
    "/product",
    "/system_dlkm",
    "/vendor_dlkm",
    "/odm_dlkm",
    "/apex",
    "/system/app",
    "/system/priv-app",
    "/system/lib",
    "/system/lib64",
    "/vendor/app",
    "/vendor/priv-app",
    "/vendor/lib",
    "/vendor/lib64",
    "/product/app",
    "/product/priv-app",
    "/product/lib",
    "/product/lib64",
    "/system_ext/app",
    "/system_ext/priv-app",
    "/system_ext/lib",
    "/system_ext/lib64",
    "/data",
    "/cache",
    "/metadata",
    "/persist",
    "/mnt",
    "/storage",
    "/debug_ramdisk",
    "/dev",
    "/proc",
    "/sys",
    "/sys/fs/cgroup",
    "/my_product",
    "/my_engineering",
    "/my_company",
    "/my_carrier",
    "/my_region",
    "/my_heytap",
    "/my_stock",
    "/my_preload",
    "/my_bigball",
    "/my_manifest",
)

fun defaultSusfsConfig(): SusfsConfig = SusfsConfig(
    legitMounts = defaultSusfsLegitMounts(),
)

fun normalizeSusfsConfig(raw: SusfsConfig?): SusfsConfig {
    val config = raw ?: defaultSusfsConfig()
    return config.copy(
        schemaVersion = if (config.schemaVersion > 0) config.schemaVersion else 1,
        hideSusMountsMode = normalizeHideSusMountsMode(config.hideSusMountsMode),
        spoofUnameStage = normalizeSpoofUnameStage(config.spoofUnameStage),
        unameValue = config.unameValue.ifBlank { "default" },
        buildTimeValue = config.buildTimeValue.ifBlank { "default" },
        sdcardRootPath = config.sdcardRootPath.ifBlank { "/sdcard" },
        androidDataRootPath = config.androidDataRootPath.ifBlank { "/sdcard/Android/data" },
        pathRules = config.pathRules.filter { it.path.isNotBlank() },
        loopPathRules = config.loopPathRules.filter { it.path.isNotBlank() },
        maps = config.maps.map { it.trim() }.filter { it.isNotBlank() },
        mounts = config.mounts.map { it.trim() }.filter { it.isNotBlank() },
        tryUmounts = config.tryUmounts.map { it.trim() }.filter { it.isNotBlank() },
        legitMounts = config.legitMounts.map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { defaultSusfsLegitMounts() },
        openRedirects = config.openRedirects.filter { it.originalPath.isNotBlank() && it.redirectedPath.isNotBlank() }
            .map {
                it.copy(
                    stage = normalizeOpenRedirectStage(it.stage),
                    uidScheme = it.uidScheme?.coerceIn(0, 4),
                )
            },
        kstatEntries = config.kstatEntries.filter { it.path.isNotBlank() },
        presets = config.presets.copy(
            hideCustomRomLevel = config.presets.hideCustomRomLevel.coerceIn(0, 5),
            emulateVoldAppDataMode = config.presets.emulateVoldAppDataMode.coerceIn(0, 2),
        ),
    )
}

fun normalizeHideSusMountsMode(raw: String?): String =
    when (raw?.trim()?.lowercase()) {
        SUSFS_HIDE_MOUNTS_ALL -> SUSFS_HIDE_MOUNTS_ALL
        SUSFS_HIDE_MOUNTS_NON_SU -> SUSFS_HIDE_MOUNTS_NON_SU
        else -> SUSFS_HIDE_MOUNTS_OFF
    }

fun normalizeSpoofUnameStage(raw: String?): String =
    when (raw?.trim()?.lowercase()) {
        SUSFS_SPOOF_UNAME_POST_FS_DATA -> SUSFS_SPOOF_UNAME_POST_FS_DATA
        SUSFS_SPOOF_UNAME_BOOT_COMPLETED -> SUSFS_SPOOF_UNAME_BOOT_COMPLETED
        else -> SUSFS_SPOOF_UNAME_OFF
    }

fun normalizeOpenRedirectStage(raw: String?): String =
    when (raw?.trim()?.lowercase()) {
        "service", "1", SUSFS_OPEN_REDIRECT_STAGE_SERVICE -> SUSFS_OPEN_REDIRECT_STAGE_SERVICE
        else -> SUSFS_OPEN_REDIRECT_STAGE_BOOT_COMPLETED
    }

fun parseSusfsVersion(raw: String): SusfsKernelVersion? {
    val normalized = raw.trim()
    val match = Regex("""^v?(\d+)\.(\d+)\.(\d+)""").find(normalized) ?: return null
    return SusfsKernelVersion(
        major = match.groupValues[1].toIntOrNull() ?: return null,
        minor = match.groupValues[2].toIntOrNull() ?: return null,
        patch = match.groupValues[3].toIntOrNull() ?: return null,
    )
}

fun parseSusfsFeatureFlags(raw: String): List<String> =
    raw.split(Regex("""[\s,]+"""))
        .map { it.trim() }
        .filter { it.isNotBlank() && it.startsWith("CONFIG_") }
        .distinct()

fun buildSusfsSupportMatrix(versionText: String, featureFlags: List<String>): SusfsSupportMatrix {
    val version = parseSusfsVersion(versionText)
    fun has(flag: String) = featureFlags.any { it.equals(flag, ignoreCase = true) }
    val majorVersion = version?.major ?: 0
    val supportsLoopPath = version?.isAtLeast(1, 5, 9) == true || majorVersion >= 2
    val supportsRootPaths = version?.isAtLeast(1, 5, 8) == true || majorVersion >= 2
    val supportsCmdlineOrBootconfig = version?.isAtLeast(1, 5, 4) == true || majorVersion >= 2
    val supportsHideMountsMode = version?.isAtLeast(1, 5, 7) == true || majorVersion >= 2
    val supportsSusSu = version?.let { it.isAtLeast(1, 5, 3) && it.major < 2 } == true
    return SusfsSupportMatrix(
        log = true,
        hideSusMountsForAll = supportsHideMountsMode,
        hideSusMountsForNonSu = supportsHideMountsMode,
        susPath = version != null,
        susPathLoop = supportsLoopPath,
        susMap = has("CONFIG_KSU_SUSFS_SUS_MAP"),
        susMount = has("CONFIG_KSU_SUSFS_SUS_MOUNT"),
        tryUmount = has("CONFIG_KSU_SUSFS_TRY_UMOUNT"),
        ksudKernelUmountFallback = majorVersion >= 2 && !has("CONFIG_KSU_SUSFS_TRY_UMOUNT"),
        openRedirect = has("CONFIG_KSU_SUSFS_OPEN_REDIRECT"),
        staticKstat = version?.isAtLeast(1, 5, 8) == true || majorVersion >= 2,
        dynamicKstat = true,
        setUname = version != null,
        setCmdlineOrBootconfig = supportsCmdlineOrBootconfig,
        setProcCmdline = version?.isAtLeast(1, 5, 4) != true && version != null,
        sdcardRootPath = supportsRootPaths,
        androidDataRootPath = supportsRootPaths,
        avcLogSpoofing = has("CONFIG_KSU_SUSFS_AVC_LOG_SPOOFING") || majorVersion >= 2,
        spoofCmdlinePreset = supportsCmdlineOrBootconfig || version != null,
        hideVendorSepolicyPreset = true,
        hideCompatMatrixPreset = true,
        hideGappsPreset = true,
        hideRevancedPreset = true,
        hideLoopsPreset = true,
        autoTryUmountPreset = version?.isAtLeast(1, 5, 5) == true || majorVersion >= 2,
        forceHideLsposedPreset = true,
        umountForZygoteIsoService = supportsSusSu || majorVersion >= 2,
    )
}

fun renderSusfsPathRules(rules: List<SusfsPathRule>): String =
    rules.joinToString("\n") { rule ->
        if (rule.maxTries == null) rule.path.trim() else "${rule.path.trim()} ${rule.maxTries}"
    }

fun parseSusfsPathRules(raw: String): List<SusfsPathRule> =
    raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line ->
            val parts = line.split(Regex("""\s+"""), limit = 2)
            val path = parts.firstOrNull().orEmpty().trim()
            require(path.isNotBlank()) { "存在空路径规则" }
            val maxTries = parts.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.toIntOrNull()
                ?: parts.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let {
                    error("路径规则重试次数无效: $line")
                }
            SusfsPathRule(path = path, maxTries = maxTries)
        }
        .toList()

fun renderSusfsStringList(values: List<String>): String = values.joinToString("\n") { it.trim() }

fun parseSusfsStringList(raw: String): List<String> =
    raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .toList()

fun renderSusfsOpenRedirects(values: List<SusfsOpenRedirectRule>): String =
    values.joinToString("\n") { rule ->
        buildString {
            append(rule.originalPath.trim())
            append(' ')
            append(rule.redirectedPath.trim())
            append(' ')
            append(
                if (normalizeOpenRedirectStage(rule.stage) == SUSFS_OPEN_REDIRECT_STAGE_SERVICE) {
                    SUSFS_OPEN_REDIRECT_STAGE_SERVICE
                } else {
                    SUSFS_OPEN_REDIRECT_STAGE_BOOT_COMPLETED
                }
            )
            rule.uidScheme?.let {
                append(' ')
                append(it.coerceIn(0, 4))
            }
        }
    }

fun parseSusfsOpenRedirects(raw: String): List<SusfsOpenRedirectRule> =
    raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line ->
            val parts = line.split(Regex("""\s+"""))
            require(parts.size >= 3) { "Open redirect 行至少需要 original redirected stage: $line" }
            val stage = normalizeOpenRedirectStage(parts[2])
            val uidScheme = parts.getOrNull(3)?.toIntOrNull()
                ?: parts.getOrNull(3)?.let { error("Open redirect uid_scheme 无效: $line") }
            SusfsOpenRedirectRule(
                originalPath = parts[0],
                redirectedPath = parts[1],
                stage = stage,
                uidScheme = uidScheme?.coerceIn(0, 4),
            )
        }
        .toList()

fun renderSusfsKstatJson(values: List<SusfsKstatEntry>): String {
    val array = JsonArray()
    values.forEach { item ->
        val obj = com.google.gson.JsonObject()
        obj.addProperty("path", item.path.trim())
        obj.addProperty("ino", item.ino)
        obj.addProperty("dev", item.dev)
        obj.addProperty("nlink", item.nlink)
        obj.addProperty("size", item.size)
        obj.addProperty("atime", item.atime)
        obj.addProperty("atime_nsec", item.atimeNsec)
        obj.addProperty("mtime", item.mtime)
        obj.addProperty("mtime_nsec", item.mtimeNsec)
        obj.addProperty("ctime", item.ctime)
        obj.addProperty("ctime_nsec", item.ctimeNsec)
        obj.addProperty("blocks", item.blocks)
        obj.addProperty("blksize", item.blksize)
        array.add(obj)
    }
    return gsonPretty.toJson(array)
}

fun parseSusfsKstatJson(raw: String): List<SusfsKstatEntry> {
    val clean = raw.trim().ifBlank { "[]" }
    val array = JsonParser.parseString(clean).asJsonArray
    return array.map { element ->
        val obj = element.asJsonObject
        val path = obj.get("path")?.asString?.trim().orEmpty()
        require(path.isNotBlank()) { "KSTAT 条目缺少 path" }
        SusfsKstatEntry(
            path = path,
            ino = obj.get("ino")?.asString ?: "default",
            dev = obj.get("dev")?.asString ?: "default",
            nlink = obj.get("nlink")?.asString ?: "default",
            size = obj.get("size")?.asString ?: "default",
            atime = obj.get("atime")?.asString ?: "0",
            atimeNsec = obj.get("atime_nsec")?.asString ?: "0",
            mtime = obj.get("mtime")?.asString ?: "0",
            mtimeNsec = obj.get("mtime_nsec")?.asString ?: "0",
            ctime = obj.get("ctime")?.asString ?: "0",
            ctimeNsec = obj.get("ctime_nsec")?.asString ?: "0",
            blocks = obj.get("blocks")?.asString ?: "0",
            blksize = obj.get("blksize")?.asString ?: "0",
        )
    }
}

fun renderSusfsCompatConfig(config: SusfsConfig): String {
    val normalized = normalizeSusfsConfig(config)
    val hideSusMountsMode = when (normalized.hideSusMountsMode) {
        SUSFS_HIDE_MOUNTS_ALL -> 1
        SUSFS_HIDE_MOUNTS_NON_SU -> 2
        else -> 0
    }
    val spoofUnameStage = when (normalized.spoofUnameStage) {
        SUSFS_SPOOF_UNAME_BOOT_COMPLETED -> 1
        SUSFS_SPOOF_UNAME_POST_FS_DATA -> 2
        else -> 0
    }
    return """
        susfs_log=${if (normalized.logEnabled) 1 else 0}
        hide_cusrom=${normalized.presets.hideCustomRomLevel}
        hide_vendor_sepolicy=${if (normalized.presets.hideVendorSepolicy) 1 else 0}
        hide_compat_matrix=${if (normalized.presets.hideCompatMatrix) 1 else 0}
        hide_gapps=${if (normalized.presets.hideGapps) 1 else 0}
        hide_revanced=${if (normalized.presets.hideRevanced) 1 else 0}
        spoof_cmdline=${if (normalized.presets.spoofCmdline) 1 else 0}
        hide_loops=${if (normalized.presets.hideLoops) 1 else 0}
        force_hide_lsposed=${if (normalized.presets.forceHideLsposed) 1 else 0}
        spoof_uname=$spoofUnameStage
        hide_sus_mnts_for_all_or_non_su_procs=$hideSusMountsMode
        umount_for_zygote_iso_service=${if (normalized.presets.umountForZygoteIsoService) 1 else 0}
        auto_try_umount=${if (normalized.presets.autoTryUmount) 1 else 0}
        skip_legit_mounts=${if (normalized.presets.skipLegitMounts) 1 else 0}
        avc_log_spoofing=${if (normalized.avcLogSpoofing) 1 else 0}
        emulate_vold_app_data=${normalized.presets.emulateVoldAppDataMode}
        ABK_SUSFS_SDCARD_ROOT_PATH='${escapeShellSingleQuote(normalized.sdcardRootPath)}'
        ABK_SUSFS_ANDROID_DATA_ROOT_PATH='${escapeShellSingleQuote(normalized.androidDataRootPath)}'
        kernel_version='${escapeShellSingleQuote(normalized.unameValue)}'
        kernel_build='${escapeShellSingleQuote(normalized.buildTimeValue)}'
        """.trimIndent()
}

fun renderSusfsOpenRedirectCompat(values: List<SusfsOpenRedirectRule>): String =
    values.joinToString("\n") { rule ->
        val stage = if (normalizeOpenRedirectStage(rule.stage) == SUSFS_OPEN_REDIRECT_STAGE_SERVICE) "1" else "0"
        buildString {
            append(rule.originalPath.trim())
            append(' ')
            append(rule.redirectedPath.trim())
            append(' ')
            append(stage)
            rule.uidScheme?.let {
                append(' ')
                append(it.coerceIn(0, 4))
            }
        }
    }

fun renderSusfsModuleProp(): String = """
    id=$SUSFS_RUNTIME_MODULE_ID
    name=ABK SUSFS Control
    version=1.0.0
    versionCode=1
    author=ABK
    description=ABK managed SUSFS runtime module
    skip_mount=true
    """.trimIndent()

fun renderSusfsActionScript(): String = """
    #!/system/bin/sh
    BASE=$SUSFS_ROOT_DIR
    BIN=$SUSFS_BINARY_PATH
    echo "ABK SUSFS Runtime"
    echo "binary=§BIN"
    echo "config=$SUSFS_CONFIG_PATH"
    [ -x "§BIN" ] && "§BIN" show version 2>/dev/null || echo "version=unavailable"
    [ -x "§BIN" ] && "§BIN" show enabled_features 2>/dev/null || true
    """.trimIndent().replace('§', '$')

fun renderSusfsUtilsScript(): String = """
    #!/system/bin/sh
    export PATH=/data/adb/ksu/bin:/data/adb/ap/bin:§PATH
    ABK_SUSFS_BASE=$SUSFS_ROOT_DIR
    ABK_SUSFS_BIN=$SUSFS_BINARY_PATH
    ABK_SUSFS_COMPAT=$SUSFS_COMPAT_DIR
    ABK_SUSFS_CONFIG_SH=$SUSFS_COMPAT_DIR/config.sh
    ABK_SUSFS_MNT=$SUSFS_ROOT_DIR/mnt
    mkdir -p "§ABK_SUSFS_MNT"

    abk_susfs_find_ksud() {
        for candidate in /data/adb/ksud "§(command -v ksud 2>/dev/null || true)"; do
            [ -x "§candidate" ] || continue
            printf '%s\n' "§candidate"
            return 0
        done
        return 1
    }

    abk_susfs_load() {
        [ -x "§ABK_SUSFS_BIN" ] || return 1
        VERSION="§( "§ABK_SUSFS_BIN" show version 2>/dev/null )"
        FEATURES="§( "§ABK_SUSFS_BIN" show enabled_features 2>/dev/null )"
        MAIN="§(echo "§VERSION" | sed 's/^v//;' | cut -d'.' -f1)"
        SUB="§(echo "§VERSION" | sed 's/^v//;' | cut -d'.' -f2)"
        PATCH="§(echo "§VERSION" | sed 's/^v//;' | cut -d'.' -f3)"
        [ -f "§ABK_SUSFS_CONFIG_SH" ] && . "§ABK_SUSFS_CONFIG_SH"
        return 0
    }

    abk_susfs_version_ge() {
        want_main="§1"
        want_sub="§2"
        want_patch="§3"
        [ -n "§MAIN" ] || return 1
        [ "§MAIN" -gt "§want_main" ] && return 0
        [ "§MAIN" -lt "§want_main" ] && return 1
        [ "§SUB" -gt "§want_sub" ] && return 0
        [ "§SUB" -lt "§want_sub" ] && return 1
        [ "§PATCH" -ge "§want_patch" ]
    }

    abk_susfs_has_feature() {
        echo "§FEATURES" | grep -q "§1"
    }

    abk_susfs_apply_hide_mounts_mode() {
        case "§hide_sus_mnts_for_all_or_non_su_procs" in
            1)
                "§ABK_SUSFS_BIN" hide_sus_mnts_for_all_procs 1 >/dev/null 2>&1 || \
                    "§ABK_SUSFS_BIN" hide_sus_mnts_for_non_su_procs 1 >/dev/null 2>&1 || true
                ;;
            2)
                "§ABK_SUSFS_BIN" hide_sus_mnts_for_non_su_procs 1 >/dev/null 2>&1 || \
                    "§ABK_SUSFS_BIN" hide_sus_mnts_for_all_procs 0 >/dev/null 2>&1 || true
                ;;
            *)
                "§ABK_SUSFS_BIN" hide_sus_mnts_for_all_procs 0 >/dev/null 2>&1 || \
                    "§ABK_SUSFS_BIN" hide_sus_mnts_for_non_su_procs 0 >/dev/null 2>&1 || true
                ;;
        esac
    }

    abk_susfs_set_root_paths() {
        abk_susfs_version_ge 1 5 8 || [ "§MAIN" -ge 2 ] || return 0
        [ -n "§ABK_SUSFS_SDCARD_ROOT_PATH" ] && "§ABK_SUSFS_BIN" set_sdcard_root_path "§ABK_SUSFS_SDCARD_ROOT_PATH" >/dev/null 2>&1 || true
        [ -n "§ABK_SUSFS_ANDROID_DATA_ROOT_PATH" ] && "§ABK_SUSFS_BIN" set_android_data_root_path "§ABK_SUSFS_ANDROID_DATA_ROOT_PATH" >/dev/null 2>&1 || true
    }

    abk_susfs_spoof_uname() {
        "§ABK_SUSFS_BIN" set_uname "§kernel_version" "§kernel_build" >/dev/null 2>&1 || true
    }

    abk_susfs_apply_paths_file() {
        file="§1"
        cmd="§2"
        [ -f "§file" ] || return 0
        while IFS= read -r line; do
            case "§line" in
                ""|\#*) continue ;;
            esac
            path="§(echo "§line" | awk '{print §1}')"
            max_tries="§(echo "§line" | awk '{print §2}')"
            until [ -z "§max_tries" ] || [ "§max_tries" -le 0 ] || [ -e "§path" ]; do
                max_tries=§((max_tries - 1))
                sleep 1
            done
            "§ABK_SUSFS_BIN" "§cmd" "§path" >/dev/null 2>&1 || true
        done < "§file"
    }

    abk_susfs_apply_string_file() {
        file="§1"
        cmd="§2"
        [ -f "§file" ] || return 0
        while IFS= read -r line; do
            case "§line" in
                ""|\#*) continue ;;
            esac
            "§ABK_SUSFS_BIN" "§cmd" "§line" >/dev/null 2>&1 || true
        done < "§file"
    }

    abk_susfs_apply_try_umount_file() {
        file="§1"
        [ -f "§file" ] || return 0
        while IFS= read -r line; do
            case "§line" in
                ""|\#*) continue ;;
            esac
            if abk_susfs_has_feature "CONFIG_KSU_SUSFS_TRY_UMOUNT"; then
                "§ABK_SUSFS_BIN" add_try_umount "§line" 1 >/dev/null 2>&1 || true
            elif [ "§MAIN" -ge 2 ]; then
                ksud_path="§(abk_susfs_find_ksud)"
                [ -n "§ksud_path" ] && "§ksud_path" kernel umount add "§line" --flags 2 >/dev/null 2>&1 || true
            fi
        done < "§file"
    }

    abk_susfs_apply_open_redirect_stage() {
        stage="§1"
        file="$SUSFS_COMPAT_DIR/sus_open_redirect.txt"
        [ -f "§file" ] || return 0
        while IFS= read -r line; do
            case "§line" in
                ""|\#*) continue ;;
            esac
            original_path="§(echo "§line" | awk '{print §1}')"
            redirected_path="§(echo "§line" | awk '{print §2}')"
            execute_on="§(echo "§line" | awk '{print §3}')"
            uid_scheme="§(echo "§line" | awk '{print §4}')"
            [ "§execute_on" = "§stage" ] || continue
            if [ "§MAIN" -ge 2 ] && [ "§SUB" -ge 1 ] && [ -n "§uid_scheme" ]; then
                "§ABK_SUSFS_BIN" add_open_redirect "§original_path" "§redirected_path" "§uid_scheme" >/dev/null 2>&1 || true
            else
                "§ABK_SUSFS_BIN" add_open_redirect "§original_path" "§redirected_path" >/dev/null 2>&1 || true
            fi
            if [ -e "§original_path" ]; then
                SUS_KSTAT="§(stat -c "%i %d default default %X 0 %Y 0 %Z 0 %b %B" "§original_path" 2>/dev/null || true)"
                [ -n "§SUS_KSTAT" ] && "§ABK_SUSFS_BIN" add_sus_kstat_statically "§redirected_path" §SUS_KSTAT >/dev/null 2>&1 || true
            fi
        done < "§file"
    }

    abk_susfs_apply_kstat_json() {
        file="$SUSFS_COMPAT_DIR/sus_kstat_statically.json"
        [ -f "§file" ] || return 0
        awk '/^[[:space:]]*\{/,/^[[:space:]]*\}/' "§file" | {
            current_obj=""
            while IFS= read -r line; do
                current_obj="§current_obj§line"
                echo "§line" | grep -q '}' || continue
                path="§(echo "§current_obj" | grep -o '"path"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4 | head -n1)"
                ino="§(echo "§current_obj" | grep -o '"ino"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4 | head -n1)"
                dev="§(echo "§current_obj" | grep -o '"dev"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4 | head -n1)"
                nlink="§(echo "§current_obj" | grep -o '"nlink"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4 | head -n1)"
                size="§(echo "§current_obj" | grep -o '"size"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4 | head -n1)"
                atime="§(echo "§current_obj" | grep -o '"atime"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4 | head -n1)"
                atime_nsec="§(echo "§current_obj" | grep -o '"atime_nsec"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4 | head -n1)"
                mtime="§(echo "§current_obj" | grep -o '"mtime"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4 | head -n1)"
                mtime_nsec="§(echo "§current_obj" | grep -o '"mtime_nsec"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4 | head -n1)"
                ctime="§(echo "§current_obj" | grep -o '"ctime"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4 | head -n1)"
                ctime_nsec="§(echo "§current_obj" | grep -o '"ctime_nsec"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4 | head -n1)"
                blocks="§(echo "§current_obj" | grep -o '"blocks"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4 | head -n1)"
                blksize="§(echo "§current_obj" | grep -o '"blksize"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4 | head -n1)"
                [ -n "§path" ] && "§ABK_SUSFS_BIN" add_sus_kstat_statically "§path" "§{ino:-default}" "§{dev:-default}" "§{nlink:-default}" "§{size:-default}" "§{atime:-0}" "§{atime_nsec:-0}" "§{mtime:-0}" "§{mtime_nsec:-0}" "§{ctime:-0}" "§{ctime_nsec:-0}" "§{blocks:-0}" "§{blksize:-0}" >/dev/null 2>&1 || true
                current_obj=""
            done
        }
    }

    abk_susfs_apply_hide_loops() {
        [ "§hide_loops" = "1" ] || return 0
        ls -Ld /proc/fs/jbd2/loop*8 2>/dev/null | sed 's|/proc/fs/jbd2/||; s|-8||' | while read -r device; do
            [ -n "§device" ] || continue
            "§ABK_SUSFS_BIN" add_sus_path /proc/fs/jbd2/"§{device}"-8 >/dev/null 2>&1 || true
            "§ABK_SUSFS_BIN" add_sus_path /proc/fs/ext4/"§{device}" >/dev/null 2>&1 || true
        done
    }
    """.trimIndent().replace('§', '$')

fun renderSusfsPostFsDataScript(): String = """
    #!/system/bin/sh
    MODDIR=$SUSFS_RUNTIME_MODULE_DIR
    . "§MODDIR/utils.sh"
    abk_susfs_load || exit 0
    [ "§susfs_log" = "1" ] && "§ABK_SUSFS_BIN" enable_log 1 >/dev/null 2>&1 || true
    abk_susfs_apply_hide_mounts_mode
    [ "§avc_log_spoofing" = "1" ] && "§ABK_SUSFS_BIN" enable_avc_log_spoofing 1 >/dev/null 2>&1 || true
    [ "§spoof_uname" = "2" ] && abk_susfs_spoof_uname
    [ "§umount_for_zygote_iso_service" = "1" ] && "§ABK_SUSFS_BIN" umount_for_zygote_iso_service 1 >/dev/null 2>&1 || true
    """.trimIndent().replace('§', '$')

fun renderSusfsPostMountScript(): String = """
    #!/system/bin/sh
    MODDIR=$SUSFS_RUNTIME_MODULE_DIR
    . "§MODDIR/utils.sh"
    abk_susfs_load || exit 0
    abk_susfs_apply_string_file "$SUSFS_COMPAT_DIR/sus_mount.txt" add_sus_mount
    abk_susfs_apply_try_umount_file "$SUSFS_COMPAT_DIR/try_umount.txt"
    """.trimIndent().replace('§', '$')

fun renderSusfsServiceScript(): String = """
    #!/system/bin/sh
    MODDIR=$SUSFS_RUNTIME_MODULE_DIR
    . "§MODDIR/utils.sh"
    abk_susfs_load || exit 0
    abk_susfs_apply_open_redirect_stage 1
    abk_susfs_apply_hide_loops
    if [ "§hide_vendor_sepolicy" = "1" ]; then
        for sepolicy_cil in \
            /vendor/etc/selinux/vendor_sepolicy.cil \
            /vendor/etc/selinux/vendor_file_contexts \
            /system_ext/etc/selinux/system_ext_sepolicy.cil
        do
            [ -f "§sepolicy_cil" ] || continue
            grep -q lineage "§sepolicy_cil" 2>/dev/null || continue
            cil_name="§(basename "§sepolicy_cil")"
            grep -v "lineage" "§sepolicy_cil" > "§ABK_SUSFS_MNT/§cil_name" 2>/dev/null || true
            "§ABK_SUSFS_BIN" add_sus_kstat "§sepolicy_cil" >/dev/null 2>&1 || true
            mount --bind "§ABK_SUSFS_MNT/§cil_name" "§sepolicy_cil" >/dev/null 2>&1 || true
            "§ABK_SUSFS_BIN" update_sus_kstat "§sepolicy_cil" >/dev/null 2>&1 || true
            "§ABK_SUSFS_BIN" add_sus_mount "§sepolicy_cil" >/dev/null 2>&1 || true
        done
    fi
    if [ "§hide_compat_matrix" = "1" ]; then
        compatibility_matrix=/system/etc/vintf/compatibility_matrix.device.xml
        if [ -f "§compatibility_matrix" ] && grep -q lineage "§compatibility_matrix" 2>/dev/null; then
            grep -v "lineage" "§compatibility_matrix" > "§ABK_SUSFS_MNT/compatibility_matrix.device.xml" 2>/dev/null || true
            "§ABK_SUSFS_BIN" add_sus_kstat "§compatibility_matrix" >/dev/null 2>&1 || true
            mount --bind "§ABK_SUSFS_MNT/compatibility_matrix.device.xml" "§compatibility_matrix" >/dev/null 2>&1 || true
            "§ABK_SUSFS_BIN" update_sus_kstat "§compatibility_matrix" >/dev/null 2>&1 || true
            "§ABK_SUSFS_BIN" add_sus_mount "§compatibility_matrix" >/dev/null 2>&1 || true
        fi
    fi
    """.trimIndent().replace('§', '$')

fun renderSusfsBootCompletedScript(): String = """
    #!/system/bin/sh
    MODDIR=$SUSFS_RUNTIME_MODULE_DIR
    . "§MODDIR/utils.sh"
    abk_susfs_load || exit 0
    abk_susfs_set_root_paths
    abk_susfs_apply_open_redirect_stage 0
    abk_susfs_apply_string_file "$SUSFS_COMPAT_DIR/sus_maps.txt" add_sus_map
    abk_susfs_apply_kstat_json
    [ "§spoof_uname" = "1" ] && abk_susfs_spoof_uname
    if [ "§hide_cusrom" -gt 0 ]; then
        find /system /vendor /system_ext /product -type f -o -type d | grep -iE "lineage|crdroid" | grep -iE "\." | while read -r path; do
            [ -n "§path" ] || continue
            "§ABK_SUSFS_BIN" add_sus_path "§path" >/dev/null 2>&1 || true
        done
    fi
    [ "§hide_gapps" = "1" ] && find /system /vendor /system_ext /product -iname '*gapps*xml' -o -type d -iname '*gapps*' | while read -r path; do
        [ -n "§path" ] || continue
        "§ABK_SUSFS_BIN" add_sus_path "§path" >/dev/null 2>&1 || true
    done
    if [ "§spoof_cmdline" = "1" ]; then
        if grep -q "androidboot.verifiedbootstate" /proc/cmdline 2>/dev/null; then
            sed 's|androidboot.verifiedbootstate=orange|androidboot.verifiedbootstate=green|g' /proc/cmdline > "§ABK_SUSFS_MNT/cmdline" 2>/dev/null || true
        else
            sed 's|androidboot.verifiedbootstate=orange|androidboot.verifiedbootstate=green|g' /proc/bootconfig > "§ABK_SUSFS_MNT/bootconfig" 2>/dev/null || true
        fi
        if [ -f "§ABK_SUSFS_MNT/cmdline" ]; then
            if abk_susfs_version_ge 1 5 4 || [ "§MAIN" -ge 2 ]; then
                "§ABK_SUSFS_BIN" set_cmdline_or_bootconfig "§ABK_SUSFS_MNT/cmdline" >/dev/null 2>&1 || true
            else
                "§ABK_SUSFS_BIN" set_proc_cmdline "§ABK_SUSFS_MNT/cmdline" >/dev/null 2>&1 || true
            fi
        fi
        if [ -f "§ABK_SUSFS_MNT/bootconfig" ]; then
            if abk_susfs_version_ge 1 5 4 || [ "§MAIN" -ge 2 ]; then
                "§ABK_SUSFS_BIN" set_cmdline_or_bootconfig "§ABK_SUSFS_MNT/bootconfig" >/dev/null 2>&1 || true
            else
                "§ABK_SUSFS_BIN" set_proc_cmdline "§ABK_SUSFS_MNT/bootconfig" >/dev/null 2>&1 || true
            fi
        fi
    fi
    if [ "§hide_revanced" = "1" ]; then
        for pkg in com.google.android.youtube com.google.android.apps.youtube.music; do
            pm path "§pkg" 2>/dev/null | cut -d: -f2 | while read -r path; do
                [ -n "§path" ] || continue
                "§ABK_SUSFS_BIN" add_sus_mount "§path" >/dev/null 2>&1 || true
                if abk_susfs_has_feature "CONFIG_KSU_SUSFS_TRY_UMOUNT"; then
                    "§ABK_SUSFS_BIN" add_try_umount "§path" 1 >/dev/null 2>&1 || true
                else
                    ksud_path="§(abk_susfs_find_ksud)"
                    [ -n "§ksud_path" ] && "§ksud_path" kernel umount add "§path" --flags 2 >/dev/null 2>&1 || true
                fi
            done
        done
    fi
    [ "§force_hide_lsposed" = "1" ] && [ "§MAIN" -ge 2 ] && {
        ksud_path="§(abk_susfs_find_ksud)"
        [ -n "§ksud_path" ] || exit 0
        "§ksud_path" kernel umount add /system/apex/com.android.art/bin/dex2oat --flags 2 >/dev/null 2>&1 || true
        "§ksud_path" kernel umount add /system/apex/com.android.art/bin/dex2oat32 --flags 2 >/dev/null 2>&1 || true
        "§ksud_path" kernel umount add /system/apex/com.android.art/bin/dex2oat64 --flags 2 >/dev/null 2>&1 || true
        "§ksud_path" kernel umount add /apex/com.android.art/bin/dex2oat --flags 2 >/dev/null 2>&1 || true
        "§ksud_path" kernel umount add /apex/com.android.art/bin/dex2oat32 --flags 2 >/dev/null 2>&1 || true
        "§ksud_path" kernel umount add /apex/com.android.art/bin/dex2oat64 --flags 2 >/dev/null 2>&1 || true
    }
    if [ "§auto_try_umount" = "1" ] && [ -f "$SUSFS_COMPAT_DIR/legit_mounts.txt" ]; then
        cat /proc/1/mountinfo 2>/dev/null | grep -E "^[13|5][0-9]{5} .* (KSU|shared).*$" | awk '{print §5}' | while read -r path; do
            [ -n "§path" ] || continue
            if [ "§skip_legit_mounts" = "1" ] && grep -qx "§path" "$SUSFS_COMPAT_DIR/legit_mounts.txt" 2>/dev/null; then
                continue
            fi
            if abk_susfs_has_feature "CONFIG_KSU_SUSFS_TRY_UMOUNT"; then
                "§ABK_SUSFS_BIN" add_try_umount "§path" 1 >/dev/null 2>&1 || true
            else
                ksud_path="§(abk_susfs_find_ksud)"
                [ -n "§ksud_path" ] && "§ksud_path" kernel umount add "§path" --flags 2 >/dev/null 2>&1 || true
            fi
        done
    fi
    abk_susfs_apply_paths_file "$SUSFS_COMPAT_DIR/sus_path.txt" add_sus_path
    if abk_susfs_version_ge 1 5 9 || [ "§MAIN" -ge 2 ]; then
        abk_susfs_apply_paths_file "$SUSFS_COMPAT_DIR/sus_path_loop.txt" add_sus_path_loop
    fi
    if [ "§emulate_vold_app_data" -ge 1 ]; then
        pm list packages -3 2>/dev/null | cut -d: -f2 | while read -r pkg; do
            [ -n "§pkg" ] || continue
            target="/sdcard/Android/data/§pkg"
            if [ "§emulate_vold_app_data" = "2" ]; then
                "§ABK_SUSFS_BIN" add_sus_path_loop "§target" >/dev/null 2>&1 || true
            else
                "§ABK_SUSFS_BIN" add_sus_path "§target" >/dev/null 2>&1 || true
            fi
        done
    fi
    abk_susfs_set_root_paths
    """.trimIndent().replace('§', '$')

private fun escapeShellSingleQuote(value: String): String = value.replace("'", "'\"'\"'")
