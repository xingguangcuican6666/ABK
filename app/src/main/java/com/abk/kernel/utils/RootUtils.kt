package com.abk.kernel.utils
import com.abk.kernel.tr
import com.abk.kernel.R

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Environment
import android.os.Process
import android.util.Base64
import android.util.Log
import com.abk.kernel.data.model.LspBridgeManagedModule
import com.abk.kernel.data.model.RootGrantApp
import com.abk.kernel.data.model.RootGrantProfile
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Collections
import java.util.Properties
import java.util.zip.ZipFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread

object RootUtils {

    private const val TAG = "RootUtils"
    private const val FEATURE_SU_COMPAT = "su_compat"
    private const val FEATURE_KERNEL_UMOUNT = "kernel_umount"
    private const val FEATURE_SULOG = "sulog"
    private const val FEATURE_ADB_ROOT = "adb_root"
    private const val FEATURE_SELINUX_HIDE = "selinux_hide"
    private const val BUNDLED_KSUD_ASSET_DIR = "ksud"
    private const val BUNDLED_KSUD_BINARY_NAME = "ksud"
    private const val BUNDLED_KSUD_METADATA_NAME = "source.properties"
    private const val BUNDLED_KSUD_INSTALL_DIR = "bundled-ksud"
    private const val ABK_META_MOUNT_ID = "meta-abk-mount"
    private const val ABK_META_MOUNT_DIR = "/data/adb/modules/meta-abk-mount"
    private const val ABK_META_MOUNT_WEB_ROOT = "/data/adb/modules/meta-abk-mount/webroot"
    private const val ABK_META_MOUNT_SYSFS_ENABLED = "/sys/kernel/abk_meta_mount/enabled"
    private const val ABK_META_MOUNT_SYSFS_PREPARE = "/sys/kernel/abk_meta_mount/prepare"
    private const val ABK_LSP_BRIDGE_DIR = "/data/adb/abk/lsp_bridge"
    private const val LSPOSED_CONFIG_DB = "/data/adb/lspd/config/modules_config.db"
    private val BOOT_PATCH_PARTITIONS = listOf("init_boot", "boot", "vendor_boot")
    private val KSU_FEATURE_NAME_REGEX = Regex("^[a-z0-9_]+$")
    private val ABK_LSP_FILE_NAME_REGEX = Regex("^[A-Za-z0-9._-]+$")
    private var appContext: Context? = null
    private val bundledKsudLock = Any()
    @Volatile
    private var abkMetaMountPlaceholderEnsured = false

    private data class BundledKsudMetadata(
        val ref: String,
        val commit: String,
        val supportedAbis: List<String>,
        val sha256ByAbi: Map<String, String>
    ) {
        val installToken: String
            get() {
                val raw = listOf(ref, commit.take(12))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .joinToString("-")
                return raw.replace(Regex("""[^A-Za-z0-9._-]"""), "_").ifBlank { "default" }
            }
    }

    enum class Ak3SlotTarget(val slotSelectValue: String) {
        CURRENT("active"),
        INACTIVE("inactive")
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )
    }

    fun isRootAvailable(): Boolean {
        if (Shell.isAppGrantedRoot() == true) return true
        return refreshRootState()
    }

    fun requestRoot(): Boolean {
        return refreshRootState()
    }

    fun refreshRootState(): Boolean {
        return try {
            createRootShell(timeoutSeconds = 10L).use { isShellRoot(it) }
        } catch (e: Exception) {
            false
        }
    }

    fun flashImage(
        imagePath: String,
        partition: String = "boot",
        onOutput: ((String) -> Unit)? = null
    ): ShellResult {
        val safeImage = shellQuote(imagePath)
        val script = """
            set -e
            echo "[ABK] 开始刷写 ${partition} 镜像"
            echo "[ABK] 镜像路径: $safeImage"
            slot=${'$'}(getprop ro.boot.slot_suffix 2>/dev/null || true)
            echo "[ABK] 当前槽位: ${'$'}{slot:-无}"
            target=""
            echo "[ABK] 搜索目标分区..."
            for candidate in \
                /dev/block/by-name/${partition}${'$'}slot \
                /dev/block/bootdevice/by-name/${partition}${'$'}slot \
                /dev/block/platform/*/by-name/${partition}${'$'}slot \
                /dev/block/mapper/${partition}${'$'}slot \
                /dev/block/by-name/$partition \
                /dev/block/bootdevice/by-name/$partition \
                /dev/block/platform/*/by-name/$partition \
                /dev/block/mapper/$partition
            do
                for block in ${'$'}candidate; do
                    [ -b "${'$'}block" ] && target="${'$'}block" && break 2
                done
            done
            [ -n "${'$'}target" ] || { echo "未找到 ${partition}${'$'}slot 分区"; exit 2; }
            img_size=${'$'}(wc -c < $safeImage)
            echo "[ABK] 目标分区: ${'$'}target"
            echo "[ABK] 镜像大小: ${'$'}img_size bytes"
            echo "[ABK] 开始写入，请不要退出应用..."
            dd if=$safeImage of="${'$'}target" bs=4096 conv=fsync
            echo "[ABK] dd 写入完成，开始 sync"
            sync
            echo "[ABK] ${partition} 镜像刷写完成"
        """.trimIndent()
        return execRootScript(script, timeoutSeconds = 240, onOutput = onOutput)
    }

    fun installModule(zipPath: String, onOutput: ((String) -> Unit)? = null): ShellResult {
        val safeZip = shellQuote(zipPath)
        val script = """
            set -e
            echo "[ABK] 开始安装模块"
            echo "[ABK] 模块路径: $safeZip"
            module_size=${'$'}(wc -c < $safeZip 2>/dev/null || echo 0)
            echo "[ABK] 模块大小: ${'$'}module_size bytes"
            chmod 0644 $safeZip 2>/dev/null || true
            installer=${'$'}(abk_find_ksud 2>/dev/null || true)
            if [ -n "${'$'}installer" ]; then
                ksud_source=${'$'}(abk_ksud_source "${'$'}installer")
                ksud_label=${'$'}(abk_ksud_label "${'$'}ksud_source")
                echo "[ABK] 使用${'$'}ksud_label ksud 安装模块: ${'$'}installer"
                if [ "${'$'}ksud_source" != "embedded" ]; then
                    echo "[ABK] 内置 SukiSU-Ultra ksud 不可用，已回退到${'$'}ksud_label ksud"
                fi
                if "${'$'}installer" module install $safeZip; then
                    echo "[ABK] KernelSU 模块安装命令完成"
                else
                    rc=${'$'}?
                    echo "[ABK] KernelSU 模块安装失败: ${'$'}rc"
                    exit ${'$'}rc
                fi
            elif command -v magisk >/dev/null 2>&1; then
                installer=${'$'}(command -v magisk)
                echo "[ABK] 使用 Magisk 安装模块: ${'$'}installer"
                if magisk --install-module $safeZip; then
                    echo "[ABK] Magisk 模块安装命令完成"
                else
                    rc=${'$'}?
                    echo "[ABK] Magisk 模块安装失败: ${'$'}rc"
                    exit ${'$'}rc
                fi
            elif command -v apd >/dev/null 2>&1; then
                installer=${'$'}(command -v apd)
                echo "[ABK] 使用 APatch 安装模块: ${'$'}installer"
                if apd module install $safeZip; then
                    echo "[ABK] APatch 模块安装命令完成"
                else
                    rc=${'$'}?
                    echo "[ABK] APatch 模块安装失败: ${'$'}rc"
                    exit ${'$'}rc
                fi
            else
                echo "未检测到 KernelSU/Magisk/APatch 模块安装器"
                exit 127
            fi
            echo "[ABK] 开始 sync"
            sync
            echo "[ABK] 模块安装完成，通常需要重启后生效"
        """.trimIndent()
        val result = execRootScript(
            withManagerShellHelpers(script),
            timeoutSeconds = 240,
            onOutput = onOutput
        )
        if (result.success) triggerAbkMetaMountPrepare()
        return result
    }

    fun installApk(
        context: Context,
        apkPath: String,
        onOutput: ((String) -> Unit)? = null
    ): ShellResult {
        val source = File(apkPath)
        if (!source.isFile) {
            val line = tr(R.string.ru_apk_not_found, apkPath)
            onOutput?.invoke(line)
            return ShellResult(false, listOf(line))
        }

        val stageDir = File(context.cacheDir, "manager-install").apply {
            deleteRecursively()
            mkdirs()
        }
        val stagedApk = File(stageDir, "manager-${System.currentTimeMillis()}.apk")
        return try {
            source.copyTo(stagedApk, overwrite = true)
            val safeApk = shellQuote(stagedApk.absolutePath)
            val script = """
                echo "[ABK] 开始安装管理器 APK"
                echo "[ABK] APK 原始路径: ${shellQuote(apkPath)}"
                echo "[ABK] APK 暂存路径: $safeApk"
                [ -f $safeApk ] || { echo "APK 暂存文件不存在"; exit 2; }
                apk_size=${'$'}(wc -c < $safeApk 2>/dev/null || echo 0)
                echo "[ABK] APK 大小: ${'$'}apk_size bytes"
                tmp="/data/local/tmp/abk-manager-${'$'}$.apk"
                rm -f "${'$'}tmp" 2>/dev/null || true
                echo "[ABK] 复制 APK 到临时安装路径: ${'$'}tmp"
                if ! cp $safeApk "${'$'}tmp" 2>/dev/null; then
                    echo "[ABK] cp 复制失败，尝试 cat 复制"
                    cat $safeApk > "${'$'}tmp" || { rc=${'$'}?; echo "[ABK] 复制 APK 失败: ${'$'}rc"; exit ${'$'}rc; }
                fi
                chmod 0644 "${'$'}tmp" || { rc=${'$'}?; echo "[ABK] chmod 失败: ${'$'}rc"; rm -f "${'$'}tmp" 2>/dev/null || true; exit ${'$'}rc; }
                restorecon "${'$'}tmp" 2>/dev/null || true
                ls -l "${'$'}tmp" 2>/dev/null || true
                pm_bin="/system/bin/pm"
                [ -x "${'$'}pm_bin" ] || pm_bin=${'$'}(command -v pm 2>/dev/null || true)
                [ -n "${'$'}pm_bin" ] || { echo "未找到 pm 命令"; rm -f "${'$'}tmp" 2>/dev/null || true; exit 127; }
                echo "[ABK] 执行 ${'$'}pm_bin install -r"
                "${'$'}pm_bin" install -r "${'$'}tmp"
                rc=${'$'}?
                rm -f "${'$'}tmp" 2>/dev/null || true
                if [ "${'$'}rc" -eq 0 ]; then
                    echo "[ABK] 管理器 APK 安装完成"
                else
                    echo "[ABK] 管理器 APK 安装失败: ${'$'}rc"
                fi
                exit "${'$'}rc"
            """.trimIndent()
            execRootScript(script, timeoutSeconds = 240, onOutput = onOutput)
        } catch (error: Exception) {
            val line = error.message ?: error::class.java.simpleName
            onOutput?.invoke(line)
            ShellResult(false, listOf(line))
        } finally {
            stageDir.deleteRecursively()
        }
    }

    fun flashAnyKernel3(
        context: Context,
        zipPath: String,
        targetSlot: Ak3SlotTarget = Ak3SlotTarget.CURRENT,
        onOutput: ((String) -> Unit)? = null
    ): ShellResult {
        val sourceZip = File(zipPath)
        if (!sourceZip.isFile) {
            val line = tr(R.string.ru_boot_image_not_found, zipPath)
            onOutput?.invoke(line)
            return ShellResult(false, listOf(line))
        }
        val workDir = File(context.filesDir, "ak3-flash").apply {
            deleteRecursively()
            mkdirs()
        }
        val scriptFile = File(workDir, "flash_ak3.sh")
        return try {
            val preparedZip = prepareAnyKernel3Zip(sourceZip, targetSlot, workDir, onOutput)
                ?: return ShellResult(
                    false,
                    listOf(
                        if (targetSlot == Ak3SlotTarget.INACTIVE) {
                            "[ABK] 当前 AnyKernel3 不支持切换到另一槽位"
                        } else {
                            "[ABK] 准备 AnyKernel3 失败"
                        }
                    )
                )
            onOutput?.invoke("[ABK] 目标槽位: ${if (targetSlot == Ak3SlotTarget.INACTIVE) "另一槽位" else "当前槽位"}")
            scriptFile.writeText(AK3_FLASH_SCRIPT)
            val script = "F=${shellQuote(workDir.absolutePath)} Z=${shellQuote(preparedZip.absolutePath)} /system/bin/sh ${shellQuote(scriptFile.absolutePath)}"
            execRootScript(script, timeoutSeconds = 300L, onOutput = onOutput)
        } finally {
            workDir.deleteRecursively()
        }
    }

    fun listBundledAbkLkmAssets(context: Context): List<AbkLkmAsset> {
        val assets = context.assets
        return ABK_LKM_VARIANTS.flatMap { variant ->
            val base = "abk_lkm/${variant.id}"
            runCatching { assets.list(base).orEmpty() }.getOrDefault(emptyArray())
                .filter { it.endsWith("_kernelsu.ko") }
                .map { name ->
                    AbkLkmAsset(
                        variantId = variant.id,
                        variantLabel = variant.label,
                        kmi = name.removeSuffix("_kernelsu.ko"),
                        assetPath = "$base/$name"
                    )
                }
        }.sortedWith(compareBy<AbkLkmAsset> { it.variantId }.thenBy { it.kmi })
    }

    fun detectCurrentKmi(): String? {
        readEmbeddedBootInfoLine(listOf("current-kmi"))?.let { return it }
        return detectCurrentKmiFallback()
    }

    fun listBootPatchPartitions(): List<String> {
        val detected = readEmbeddedBootInfoLines(listOf("available-partitions"))
            .map { it.trim() }
            .filter { it in BOOT_PATCH_PARTITIONS }
            .distinct()
        return detected.ifEmpty { listBootPatchPartitionsFallback() }
    }

    fun detectDefaultBootPartition(): String {
        val detected = readEmbeddedBootInfoLine(listOf("default-partition"))
            ?.takeIf { it in BOOT_PATCH_PARTITIONS }
        if (detected != null) return detected
        return detectDefaultBootPartitionFallback()
    }

    fun detectBootSlotSuffix(ota: Boolean = false): String? {
        val args = buildList {
            add("slot-suffix")
            if (ota) add("--ota")
        }
        return readEmbeddedBootInfoLine(args)
    }

    fun supportsAnyKernelInactiveSlot(): Boolean {
        val suffix = detectBootSlotSuffix()
            ?.trim()
            ?.lowercase()
            ?.takeIf { it == "_a" || it == "_b" }
            ?: detectSystemProperty("ro.boot.slot_suffix")
                ?.trim()
                ?.lowercase()
                ?.takeIf { it == "_a" || it == "_b" }
        if (suffix != null) return true
        return BOOT_PATCH_PARTITIONS.any { partitionExists("${it}_a") && partitionExists("${it}_b") }
    }

    private fun detectCurrentKmiFallback(): String? {
        val release = getKernelVersion().lowercase()
        Regex("""(\d+\.\d+).*?(android\d+)""").find(release)?.let { match ->
            val kernel = match.groupValues[1]
            val android = match.groupValues[2]
            return "$android-$kernel"
        }
        val kernel = Regex("""\b(\d+\.\d+)\.""").find(release)?.groupValues?.getOrNull(1)
            ?: return null
        val android = when (kernel) {
            "5.10" -> "android12"
            "5.15" -> "android13"
            "6.1" -> "android14"
            "6.6" -> "android15"
            "6.12" -> "android16"
            else -> return null
        }
        return "$android-$kernel"
    }

    private fun listBootPatchPartitionsFallback(): List<String> {
        val detected = BOOT_PATCH_PARTITIONS.filter(::partitionExists)
        return detected.ifEmpty { BOOT_PATCH_PARTITIONS }
    }

    private fun detectDefaultBootPartitionFallback(): String {
        val partitions = listBootPatchPartitionsFallback()
        return when {
            detectCurrentKmiFallback()?.startsWith("android12-") == true && "boot" in partitions -> "boot"
            "init_boot" in partitions -> "init_boot"
            "boot" in partitions -> "boot"
            else -> partitions.firstOrNull().orEmpty().ifBlank { "boot" }
        }
    }

    fun resolveUserlandKsudPath(context: Context): String? =
        prepareBundledKsudPath(context) ?: embeddedKsudPath(context)

    fun patchAbkLkmBootImage(
        context: Context,
        bootImagePath: String?,
        variantId: String,
        kmi: String,
        allowRootFallback: Boolean,
        flash: Boolean = false,
        ota: Boolean = false,
        partition: String? = null,
        allowShell: Boolean = false,
        enableAdb: Boolean = false,
        localModulePath: String? = null,
        onOutput: ((String) -> Unit)? = null
    ): BootPatchResult {
        val sourceBoot = bootImagePath
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
        if (sourceBoot != null && !sourceBoot.isFile) {
            return BootPatchResult(false, listOf(tr(R.string.ru_boot_image_not_found, bootImagePath)), null)
        }

        val localModule = localModulePath
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
        if (localModule != null && !localModule.isFile) {
            return BootPatchResult(false, listOf(tr(R.string.ru_lkm_file_not_found, localModulePath)), null)
        }

        val asset = if (localModule == null) {
            listBundledAbkLkmAssets(context).firstOrNull {
                it.variantId == variantId && it.kmi == kmi
            } ?: return BootPatchResult(false, listOf(tr(R.string.ru_lkm_module_not_bundled, variantId, kmi)), null)
        } else {
            null
        }

        val workDir = File(context.cacheDir, "abk-lkm-patch").apply {
            deleteRecursively()
            mkdirs()
        }
        return runCatching {
            val moduleFile = if (localModule != null) {
                localModule
            } else {
                stageBundledAbkLkmAsset(context, workDir, checkNotNull(asset))
            }

            val outputDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
                "abk-patched"
            ).apply { mkdirs() }
            val moduleName = (asset?.let { "${it.variantId}-${it.kmi}" } ?: moduleFile.nameWithoutExtension)
                .replace(Regex("""[^A-Za-z0-9._-]"""), "_")
            val outputName = "abk-${moduleName}-patched-${System.currentTimeMillis()}.img"
            val outputImage = File(outputDir, outputName)
            val baseArgs = buildBootPatchArgs(
                context = context,
                bootImage = sourceBoot,
                moduleFile = moduleFile,
                flash = flash,
                ota = ota,
                partition = partition,
                outputDir = outputDir,
                outputName = outputName,
                allowShell = allowShell,
                enableAdb = enableAdb
            )
            val requiresRootShell = flash || sourceBoot == null
            if (asset != null) {
                onOutput?.invoke(tr(R.string.ru_log_using_bundled_lkm, asset.variantLabel, asset.kmi))
            } else {
                onOutput?.invoke(tr(R.string.ru_log_using_local_lkm, moduleFile.name))
            }
            val result = when {
                allowRootFallback -> {
                    val rootResult = runEmbeddedBootPatchWithRoot(
                        context = context,
                        args = baseArgs,
                        onOutput = onOutput
                    )
                    when {
                        rootResult != null -> rootResult
                        !requiresRootShell -> {
                            onOutput?.invoke(tr(R.string.ru_log_no_root_shell_local_patch))
                            runBundledUserlandBootPatch(
                                context = context,
                                args = baseArgs,
                                onOutput = onOutput
                            ) ?: ShellResult(
                                false,
                                listOf(tr(R.string.ru_no_embedded_ksud))
                            )
                        }
                        else -> ShellResult(false, listOf(tr(R.string.ru_install_requires_root)))
                    }
                }
                requiresRootShell -> ShellResult(false, listOf(tr(R.string.ru_install_requires_root)))
                else -> runBundledUserlandBootPatch(
                    context = context,
                    args = baseArgs,
                    onOutput = onOutput
                ) ?: ShellResult(
                    false,
                    listOf(tr(R.string.ru_no_embedded_ksud))
                )
            }
            val outputPath = outputImage.takeIf { result.success && it.isFile }?.absolutePath
            BootPatchResult(
                success = result.success && (flash || outputPath != null),
                output = result.output,
                patchedImagePath = outputPath
            )
        }.getOrElse { error ->
            val line = error.message ?: error::class.java.simpleName
            onOutput?.invoke(line)
            BootPatchResult(false, listOf(line), null)
        }
    }

    fun getKernelVersion(): String {
        val systemVersion = System.getProperty("os.version")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (systemVersion != null) return systemVersion

        return runCatching {
            val process = ProcessBuilder("uname", "-r")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readLine()?.trim() }
            process.waitFor()
            output?.takeIf { it.isNotBlank() } ?: "Unknown"
        }.getOrDefault("Unknown")
    }

    fun getKsuVersion(): String {
        return detectManagerRuntime().version.ifBlank { "N/A" }
    }

    fun getRootMode(): String {
        val runtime = detectManagerRuntime()
        return when {
            runtime.displayName.isNotBlank() -> runtime.displayName
            runtime.active -> "Root"
            else -> "Unknown"
        }
    }

    fun getSusfsVersion(): String {
        val result = execRootScript("cat /proc/self/attr/prev 2>/dev/null | head -1 || echo N/A", timeoutSeconds = 10L)
        return result.output.firstOrNull() ?: "N/A"
    }

    fun reboot(): ShellResult = execRootScript("svc power reboot || reboot", timeoutSeconds = 15L)

    fun readAbkControlStatus(): ShellResult {
        if (!isNativeManagerActive()) {
            return nativeManagerPermissionDeniedResult()
        }
        val status = AbkKsuNative.controlStatus()
        return if (status != null) {
            ShellResult(true, listOf(status))
        } else {
            ShellResult(false, listOf(tr(R.string.ru_not_active)))
        }
    }

    private fun readAbkLspBridgeStatus(): ShellResult? {
        val control = readAbkControlStatus()
        if (!control.success) return null
        val body = control.output.joinToString("\n").trim()
        if (!body.startsWith("{")) return null
        return runCatching {
            val root = JSONObject(body)
            val modules = root.optJSONArray("modules") ?: return@runCatching null
            for (index in 0 until modules.length()) {
                val item = modules.optJSONObject(index) ?: continue
                if (item.optString("id") == "abk_lsp_bridge") {
                    return@runCatching ShellResult(true, listOf(body))
                }
            }
            null
        }.getOrNull()
    }

    fun readManagerRuntimeSnapshot(preferLspBridge: Boolean = true): ManagerRuntimeSnapshot {
        val manager = detectManagerRuntime(preferLspBridge)
        if (!manager.active) {
            return ManagerRuntimeSnapshot(manager = manager)
        }

        val control = if (manager.backend == "lsp_bridge") {
            readAbkLspBridgeStatus()
                ?.takeIf { it.success }
                ?.output
                ?.joinToString("\n")
                ?.trim()
                ?.takeIf { it.isNotBlank() && it.startsWith("{") }
        } else if (manager.workMode == "lkm") {
            null
        } else {
            readAbkControlStatus().takeIf { it.success }
                ?.output
                ?.joinToString("\n")
                ?.trim()
                ?.takeIf { it.isNotBlank() && it.startsWith("{") }
        }

        if (control?.contains(ABK_META_MOUNT_ID) == true ||
            runCatching { File(ABK_META_MOUNT_SYSFS_ENABLED).exists() }.getOrDefault(false)
        ) {
            ensureAbkMetaMountPlaceholder()
            triggerAbkMetaMountPrepare()
        }

        val modules = listKsuModules().takeIf { it.success }
            ?.output
            ?.joinToString("\n")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it.startsWith("[") }

        if (manager.backend == "su" && control == null && modules == null) {
            return ManagerRuntimeSnapshot(
                manager = manager.copy(
                    active = false,
                    diagnostics = (manager.diagnostics + tr(R.string.ru_only_generic_su)).distinct()
                )
            )
        }

        return ManagerRuntimeSnapshot(
            manager = manager,
            controlStatusJson = control,
            ksuModulesJson = modules
        )
    }

    fun isNativeManagerActive(): Boolean = AbkKsuNative.isUsableManager()

    fun listRootGrantApps(context: Context): List<RootGrantApp> {
        if (!isNativeManagerActive()) return emptyList()
        val packageManager = context.packageManager
        val apps = installedApplications(packageManager)
        return apps
            .asSequence()
            .filter { it.packageName.isNotBlank() }
            .mapNotNull { appInfo ->
                val packageName = appInfo.packageName ?: return@mapNotNull null
                val uid = appInfo.uid
                val profile = AbkKsuNative.readProfile(packageName, uid)
                    ?: RootGrantProfile(name = packageName, currentUid = uid)
                RootGrantApp(
                    packageName = packageName,
                    label = runCatching {
                        packageManager.getApplicationLabel(appInfo).toString()
                    }.getOrDefault(packageName),
                    uid = uid,
                    userName = AbkKsuNative.userName(uid),
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    profile = profile
                )
            }
            .distinctBy { "${it.uid}:${it.packageName}" }
            .sortedWith(
                compareByDescending<RootGrantApp> { it.profile.allowSu }
                    .thenBy { it.label.lowercase() }
                    .thenBy { it.packageName }
            )
            .toList()
    }

    fun setRootGrantProfile(profile: RootGrantProfile): Boolean {
        if (!isNativeManagerActive()) return false
        if (profile.allowSu && !profile.rootUseDefault && profile.rules.isNotBlank()) {
            if (!setProfileSepolicy(profile.name, profile.rules)) return false
        }
        return AbkKsuNative.writeProfile(profile)
    }

    fun readKsuFeature(featureName: String): KsuFeatureState {
        val feature = normalizeKsuFeatureName(featureName)
            ?: return KsuFeatureState(featureName, KsuFeatureSupport.UNSUPPORTED)
        val nativeState = readNativeFeature(feature)
        val status = getKsuFeatureSupport(feature)
        val value = getKsuFeatureValue(feature) ?: nativeState?.value
        val configValue = getKsuFeatureConfigValue(feature)
        return KsuFeatureState(
            name = feature,
            support = status ?: nativeState?.support ?: KsuFeatureSupport.UNSUPPORTED,
            value = value,
            configValue = configValue
        )
    }

    fun setSuCompatMode(mode: Int): ShellResult {
        return when (mode) {
            0 -> setNativeKsuFeatureValue(FEATURE_SU_COMPAT, 1L, persist = true)
            1 -> {
                val persistedEnabled = setNativeKsuFeatureValue(FEATURE_SU_COMPAT, 1L, persist = true)
                if (!persistedEnabled.success) {
                    persistedEnabled
                } else {
                    mergeShellResults(
                        persistedEnabled,
                        setNativeKsuFeatureValue(FEATURE_SU_COMPAT, 0L, persist = false)
                    )
                }
            }
            2 -> setNativeKsuFeatureValue(FEATURE_SU_COMPAT, 0L, persist = true)
            else -> ShellResult(false, listOf(tr(R.string.ru_unknown_su_compat)))
        }
    }

    fun setKsuFeatureEnabled(featureName: String, enabled: Boolean): ShellResult {
        val feature = normalizeKsuFeatureName(featureName)
            ?: return ShellResult(false, listOf(tr(R.string.ru_unknown_feature)))
        val value = if (enabled) 1L else 0L
        return if (feature == FEATURE_ADB_ROOT) {
            val setResult = setKsuFeatureValue(feature, value, persist = false)
            if (!setResult.success) {
                setResult
            } else {
                mergeShellResults(
                    setResult,
                    execRootScript("setprop ctl.restart adbd", timeoutSeconds = 15L),
                    saveKsuFeatureConfig()
                )
            }
        } else if (feature == FEATURE_SELINUX_HIDE) {
            setNativeKsuFeatureValue(feature, value, persist = true)
        } else {
            setKsuFeatureValue(feature, value, persist = true)
        }
    }

    fun setReSukiSuFeatureEnabled(featureName: String, enabled: Boolean): ShellResult =
        setKsuFeatureEnabled(featureName, enabled)

    fun isDefaultUmountModules(): Boolean {
        if (!isNativeManagerActive()) return false
        return AbkKsuNative.isDefaultUmountModules() ?: false
    }

    fun setDefaultUmountModules(enabled: Boolean): Boolean {
        if (!isNativeManagerActive()) return false
        return AbkKsuNative.setDefaultUmountModules(enabled)
    }

    fun listAppProfileTemplates(): ShellResult {
        if (!isNativeManagerActive()) return nativeManagerPermissionDeniedResult()
        return runKsudCommand("profile list-templates", timeoutSeconds = 30L)
    }

    fun readAppProfileTemplate(id: String): ShellResult {
        if (!isNativeManagerActive()) return nativeManagerPermissionDeniedResult()
        if (!isSafeTemplateId(id)) {
            return ShellResult(false, listOf(tr(R.string.ru_invalid_template_name)))
        }
        return runKsudCommand("profile get-template ${shellQuote(id)}", timeoutSeconds = 30L)
    }

    fun writeAppProfileTemplate(id: String, content: String): ShellResult {
        if (!isNativeManagerActive()) return nativeManagerPermissionDeniedResult()
        if (!isSafeTemplateId(id)) {
            return ShellResult(false, listOf(tr(R.string.ru_invalid_template_name)))
        }
        return runKsudCommand(
            "profile set-template ${shellQuote(id)} ${shellQuote(content)}",
            timeoutSeconds = 30L
        )
    }

    fun deleteAppProfileTemplate(id: String): ShellResult {
        if (!isNativeManagerActive()) return nativeManagerPermissionDeniedResult()
        if (!isSafeTemplateId(id)) {
            return ShellResult(false, listOf(tr(R.string.ru_invalid_template_name)))
        }
        return runKsudCommand("profile delete-template ${shellQuote(id)}", timeoutSeconds = 30L)
    }

    fun listKsuModules(): ShellResult {
        val script = """
            set -e
            ksud_path=${'$'}(abk_find_ksud)
            [ -n "${'$'}ksud_path" ] || exit 127
            abk_exec_ksud "${'$'}ksud_path" module list
        """.trimIndent()
        return execRootScript(withManagerShellHelpers(script), timeoutSeconds = 30L)
    }

    fun runKsuModuleAction(moduleId: String, onOutput: ((String) -> Unit)? = null): ShellResult {
        val safeId = shellQuote(moduleId.trim())
        val script = """
            set -e
            ksud_path=${'$'}(abk_find_ksud)
            [ -n "${'$'}ksud_path" ] || exit 127
            abk_exec_ksud "${'$'}ksud_path" module action $safeId
        """.trimIndent()
        return execRootScript(withManagerShellHelpers(script), timeoutSeconds = 300L, onOutput = onOutput)
    }

    fun ensureAbkMetaMountPlaceholder(force: Boolean = false): ShellResult {
        if (!force && abkMetaMountPlaceholderEnsured) return ShellResult(true, emptyList())

        val result = execRootScript(abkMetaMountPlaceholderScript(), timeoutSeconds = 30L)
        if (result.success) abkMetaMountPlaceholderEnsured = true
        return result
    }

    fun triggerAbkMetaMountPrepare(): ShellResult {
        val script = """
            set -e
            [ -e ${shellQuote(ABK_META_MOUNT_SYSFS_PREPARE)} ] || exit 0
            echo 1 > ${shellQuote(ABK_META_MOUNT_SYSFS_PREPARE)} 2>/dev/null || true
        """.trimIndent()
        return execRootScript(script, timeoutSeconds = 30L)
    }

    fun runModuleActionScript(moduleDir: String, onOutput: ((String) -> Unit)? = null): ShellResult {
        val cleanDir = moduleDir.trim().ifBlank { "/data/adb/modules" }
        val safeDir = shellQuote(cleanDir)
        val script = """
            set -e
            MOD=$safeDir
            ACTION="${'$'}MOD/action.sh"
            [ -f "${'$'}ACTION" ] || { echo "action.sh not found"; exit 2; }
            cd "${'$'}MOD" 2>/dev/null || exit 2
            /system/bin/sh "${'$'}ACTION"
        """.trimIndent()
        if (isAbkMetaMountModuleDir(cleanDir)) {
            val ensureResult = ensureAbkMetaMountPlaceholder(force = true)
            if (!ensureResult.success) return ensureResult
            triggerAbkMetaMountPrepare()
        }
        return execRootScript(script, timeoutSeconds = 300L, onOutput = onOutput)
    }

    fun setKsuModuleEnabled(moduleId: String, enabled: Boolean): ShellResult {
        val safeId = shellQuote(moduleId.trim())
        val verb = if (enabled) "enable" else "disable"
        val script = """
            set -e
            ksud_path=${'$'}(abk_find_ksud)
            [ -n "${'$'}ksud_path" ] || exit 127
            abk_exec_ksud "${'$'}ksud_path" module $verb $safeId
        """.trimIndent()
        return execRootScript(withManagerShellHelpers(script), timeoutSeconds = 30L)
    }

    fun setKsuModulePendingUninstall(moduleId: String, pending: Boolean): ShellResult {
        val safeId = shellQuote(moduleId.trim())
        val verb = if (pending) "uninstall" else "undo-uninstall"
        val script = """
            set -e
            ksud_path=${'$'}(abk_find_ksud)
            [ -n "${'$'}ksud_path" ] || exit 127
            abk_exec_ksud "${'$'}ksud_path" module $verb $safeId
        """.trimIndent()
        return execRootScript(withManagerShellHelpers(script), timeoutSeconds = 30L)
    }

    fun listKpmModules(): ShellResult {
        val script = """
            set -e
            ksud_path=${'$'}(abk_find_ksud)
            [ -n "${'$'}ksud_path" ] || exit 127
            abk_exec_ksud "${'$'}ksud_path" kpm list
        """.trimIndent()
        return execRootScript(withManagerShellHelpers(script), timeoutSeconds = 30L)
    }

    fun isKpmAvailable(): Boolean {
        return runKsudCommand("kpm version", timeoutSeconds = 15L).success ||
            runKsudCommand("kpm list", timeoutSeconds = 15L).success
    }

    fun readSelinuxMode(): ShellResult =
        execRootScript("getenforce", timeoutSeconds = 10L)

    fun setSelinuxEnforcing(enforcing: Boolean): ShellResult =
        execRootScript("setenforce ${if (enforcing) "1" else "0"}", timeoutSeconds = 10L)

    fun listUmountPaths(): ShellResult =
        runKsudCommand("umount list", timeoutSeconds = 30L)

    fun getKpmModuleInfo(name: String): ShellResult {
        val safeName = shellQuote(name.trim())
        val script = """
            set -e
            ksud_path=${'$'}(abk_find_ksud)
            [ -n "${'$'}ksud_path" ] || exit 127
            abk_exec_ksud "${'$'}ksud_path" kpm info $safeName
        """.trimIndent()
        return execRootScript(withManagerShellHelpers(script), timeoutSeconds = 30L)
    }

    fun writeAbkControlCommand(command: String): ShellResult {
        if (!isNativeManagerActive()) {
            return nativeManagerPermissionDeniedResult()
        }
        return if (AbkKsuNative.controlCommand(command)) {
            ShellResult(true, emptyList())
        } else {
            ShellResult(false, listOf(tr(R.string.ru_not_active)))
        }
    }

    fun readLspBridgeDataFile(fileName: String): ShellResult {
        val cleanName = fileName.trim()
        if (!cleanName.matches(ABK_LSP_FILE_NAME_REGEX)) {
            return ShellResult(false, listOf("invalid LSP bridge file name"))
        }
        val safePath = shellQuote("$ABK_LSP_BRIDGE_DIR/$cleanName")
        val script = """
            set -e
            file=$safePath
            [ -f "${'$'}file" ] || exit 3
            base64 "${'$'}file" 2>/dev/null | tr -d '\n'
        """.trimIndent()
        val result = execRootScript(script, timeoutSeconds = 15L)
        if (!result.success) return result
        val encoded = result.output.joinToString("").trim()
        val decoded = runCatching {
            if (encoded.isBlank()) "" else String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrElse { error ->
            return ShellResult(false, listOf(error.message ?: "failed to decode LSP bridge file"))
        }
        return ShellResult(true, listOf(decoded))
    }

    fun writeLspBridgeDataFile(fileName: String, content: String): ShellResult {
        val cleanName = fileName.trim()
        if (!cleanName.matches(ABK_LSP_FILE_NAME_REGEX)) {
            return ShellResult(false, listOf("invalid LSP bridge file name"))
        }
        val encoded = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val safeDir = shellQuote(ABK_LSP_BRIDGE_DIR)
        val safeFile = shellQuote("$ABK_LSP_BRIDGE_DIR/$cleanName")
        val safeTmp = shellQuote("$ABK_LSP_BRIDGE_DIR/.$cleanName.tmp")
        val safeEncoded = shellQuote(encoded)
        val script = """
            set -e
            dir=$safeDir
            file=$safeFile
            tmp=$safeTmp
            mkdir -p "${'$'}dir"
            chmod 0700 "${'$'}dir" 2>/dev/null || true
            printf %s $safeEncoded | base64 -d > "${'$'}tmp"
            chmod 0600 "${'$'}tmp" 2>/dev/null || true
            mv "${'$'}tmp" "${'$'}file"
        """.trimIndent()
        return execRootScript(script, timeoutSeconds = 15L)
    }

    fun appendLspBridgeLog(line: String): ShellResult {
        val trimmed = line.trim().take(512)
        if (trimmed.isBlank()) return ShellResult(true, emptyList())
        val safeDir = shellQuote(ABK_LSP_BRIDGE_DIR)
        val safeLog = shellQuote("$ABK_LSP_BRIDGE_DIR/runtime.log")
        val safeLine = shellQuote(trimmed)
        val script = """
            set -e
            dir=$safeDir
            log=$safeLog
            mkdir -p "${'$'}dir"
            chmod 0700 "${'$'}dir" 2>/dev/null || true
            printf '%s %s\n' "${'$'}(date '+%Y-%m-%dT%H:%M:%S%z' 2>/dev/null || echo unknown-time)" $safeLine >> "${'$'}log"
            chmod 0600 "${'$'}log" 2>/dev/null || true
        """.trimIndent()
        return execRootScript(script, timeoutSeconds = 15L)
    }

    fun probeAndReportLspRuntimeState(): ShellResult {
        val probe = probeOfficialLsposedRuntime()
        val state = parseShellKeyValueOutput(probe.output)
        val modulePresent = state["module_present"] == "1"
        val payloadAvailable = state["payload_available"] == "1"
        val zygoteRunning = state["zygote_running"] == "1"
        val daemonActive = state["daemon_active"] == "1"
        val payloadReady = modulePresent && payloadAvailable
        val runtimeReady = payloadReady && zygoteRunning && daemonActive

        val outputs = mutableListOf<String>()
        outputs += probe.output
        if (!isNativeManagerActive()) {
            outputs += "abk_control unavailable; runtime probe was not reported to kernel bridge"
            return ShellResult(false, outputs)
        }

        val commands = buildList {
            add(if (payloadReady) "lsp helper active" else "lsp helper inactive")
            add(if (payloadReady) "lsp payload ready" else "lsp payload detached")
            add(if (zygoteRunning) "lsp zygote attached" else "lsp zygote detached")
            add(if (daemonActive) "lsp daemon active" else "lsp daemon inactive")
            add(if (runtimeReady) "lsp runtime ready" else "lsp runtime not_ready")
        }
        var success = probe.success
        commands.forEach { command ->
            val result = writeAbkControlCommand(command)
            success = success && result.success
            if (!result.success) outputs += "failed: $command"
            outputs += result.output
        }

        val summary = "official LSPosed runtime: module=${state["module_dir"].orEmpty().ifBlank { "absent" }}, " +
            "payload=${if (payloadReady) "ready" else "not_ready"}, " +
            "daemon=${if (daemonActive) "active" else "inactive"}, " +
            "zygote=${if (zygoteRunning) "running" else "missing"}"
        appendLspBridgeLog(summary)
        outputs += summary
        return ShellResult(success, outputs)
    }

    fun syncOfficialLsposedDatabaseFromPersistedState(
        modules: List<LspBridgeManagedModule>
    ): ShellResult {
        val context = appContext
            ?: return ShellResult(false, listOf("application context is not initialized"))
        val tempDir = File(context.cacheDir, "lsposed-sync")
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            return ShellResult(false, listOf("failed to create LSPosed sync cache"))
        }
        val tempDb = File(tempDir, "modules_config.db")
        val outputs = mutableListOf<String>()
        val rows = modules.mapNotNull { module ->
            val apkPath = resolveOfficialLsposedModuleApkPath(module.packageName)
            if (apkPath == null) {
                outputs += "skip ${module.packageName}: installed Xposed module APK not found"
                null
            } else {
                OfficialLsposedModuleRow(
                    packageName = module.packageName,
                    apkPath = apkPath,
                    enabled = module.enabled,
                    scopes = module.selectedScope
                )
            }
        }

        val prepare = prepareOfficialLsposedDatabase(tempDb)
        outputs += prepare.output
        if (!prepare.success) {
            return ShellResult(false, outputs)
        }

        return runCatching {
            writeOfficialLsposedDatabase(tempDb, rows)
            val commit = commitOfficialLsposedDatabase(tempDb)
            outputs += commit.output
            if (commit.success) {
                outputs += "synced ${rows.size} module(s) to official LSPosed config"
                probeAndReportLspRuntimeState().also { probe ->
                    outputs += probe.output
                }
            }
            ShellResult(commit.success, outputs)
        }.getOrElse { error ->
            ShellResult(false, outputs + (error.message ?: error::class.java.simpleName))
        }
    }

    fun setAbkMetaMountEnabled(enabled: Boolean): ShellResult {
        val ensureResult = ensureAbkMetaMountPlaceholder(force = true)
        if (!ensureResult.success) return ensureResult
        val script = """
            set -e
            SYS=${shellQuote(ABK_META_MOUNT_SYSFS_ENABLED)}
            PREPARE=${shellQuote(ABK_META_MOUNT_SYSFS_PREPARE)}
            MOD=${shellQuote(ABK_META_MOUNT_DIR)}
            [ -e "${'$'}SYS" ] || { echo "abk_meta_mount sysfs not found"; exit 2; }
            mkdir -p "${'$'}MOD"
            if [ "${if (enabled) "1" else "0"}" = "1" ]; then
                rm -f "${'$'}MOD/disable" "${'$'}MOD/remove"
                echo 1 > "${'$'}SYS"
                [ -e "${'$'}PREPARE" ] && echo 1 > "${'$'}PREPARE" || true
            else
                touch "${'$'}MOD/disable"
                rm -f "${'$'}MOD/remove"
                echo 0 > "${'$'}SYS"
            fi
            state=${'$'}(cat "${'$'}SYS" 2>/dev/null || echo unknown)
            [ "${if (enabled) "1" else "0"}" = "${'$'}state" ] || { echo "abk_meta_mount enable state mismatch: ${'$'}state"; exit 3; }
        """.trimIndent()
        return execRootScript(script, timeoutSeconds = 30L)
    }

    fun execRootCommandForWebUi(command: String, cwd: String = "", timeoutSeconds: Long = 120L): ShellResult {
        val prefix = if (cwd.isBlank()) {
            ""
        } else {
            "cd ${shellQuote(cwd)} 2>/dev/null || exit 2\n"
        }
        return execRootScript(prefix + command, timeoutSeconds = timeoutSeconds)
    }

    fun readModuleWebResource(moduleId: String, relativePath: String): ByteArray? {
        val cleanId = moduleId.trim()
        val cleanRelativePath = sanitizeWebRelativePath(relativePath) ?: return null
        if (cleanId.isBlank()) return null

        if (isAbkMetaMountModuleId(cleanId)) {
            ensureAbkMetaMountPlaceholder()
            triggerAbkMetaMountPrepare()
        }

        val filePath = "/data/adb/modules/$cleanId/webroot/$cleanRelativePath"
        fun readOnce(): ByteArray? = try {
            createRootShell(timeoutSeconds = 30L).use { shell ->
                val result = execWithShell(
                    shell = shell,
                    script = """
                        file=${shellQuote(filePath)}
                        [ -f "${'$'}file" ] || exit 2
                        base64 "${'$'}file" 2>/dev/null | tr -d '\n'
                    """.trimIndent(),
                    normalizeOutput = false
                )
                if (!result.success) return null
                val encoded = result.output.joinToString("").trim()
                if (encoded.isBlank()) ByteArray(0) else Base64.decode(encoded, Base64.DEFAULT)
            }
        } catch (error: Throwable) {
            null
        }

        return readOnce() ?: if (isAbkMetaMountModuleId(cleanId)) {
            ensureAbkMetaMountPlaceholder(force = true)
            triggerAbkMetaMountPrepare()
            readOnce()
        } else {
            null
        }
    }

    fun moduleInfoJson(moduleId: String): String {
        val cleanId = moduleId.trim()
        if (cleanId.isBlank()) return "{}"
        val moduleDir = "/data/adb/modules/$cleanId"
        val webRoot = "$moduleDir/webroot"
        val modules = listKsuModules().takeIf { it.success }?.output?.joinToString("\n").orEmpty()
        val moduleJson = runCatching {
            val array = org.json.JSONArray(modules.ifBlank { "[]" })
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                if (item.optString("id") == cleanId) {
                    item.put("moduleDir", moduleDir)
                    item.put("webRoot", webRoot)
                    return@runCatching item.toString()
                }
            }
            org.json.JSONObject()
                .put("id", cleanId)
                .put("moduleDir", moduleDir)
                .put("webRoot", webRoot)
                .toString()
        }.getOrDefault("{}")
        return moduleJson
    }

    private fun abkMetaMountPlaceholderScript(): String = """
        set -e
        MOD=${shellQuote(ABK_META_MOUNT_DIR)}
        WEB=${shellQuote(ABK_META_MOUNT_WEB_ROOT)}
        MARK='/data/adb/metamodule'
        mkdir -p "${'$'}MOD" "${'$'}WEB"
        cat > "${'$'}MOD/module.prop" <<'ABK_META_PROP'
        id=meta-abk-mount
        name=ABK Meta Mount
        version=0.1.0
        versionCode=1
        author=ABK
        description=Built-in KernelSU-compatible metamodule provider
        metamodule=1
        mount=false
        skip_mount=true
        web=1
        webui=1
        action=1
        ABK_META_PROP
        cat > "${'$'}MOD/metamount.sh" <<'ABK_META_METAMOUNT'
        #!/system/bin/sh
        rm -f /data/adb/modules/meta-abk-mount/disable /data/adb/modules/meta-abk-mount/remove
        echo 1 > /sys/kernel/abk_meta_mount/enabled 2>/dev/null || true
        echo 1 > /sys/kernel/abk_meta_mount/prepare 2>/dev/null || true
        ABK_META_METAMOUNT
        chmod 755 "${'$'}MOD/metamount.sh"
        cat > "${'$'}MOD/action.sh" <<'ABK_META_ACTION'
        #!/system/bin/sh
        if [ -f /proc/abk_meta_mount/status ]; then
            cat /proc/abk_meta_mount/status
        else
            echo 'ABK Meta Mount status unavailable'
        fi
        ABK_META_ACTION
        chmod 755 "${'$'}MOD/action.sh"
        cat > "${'$'}WEB/index.html" <<'ABK_META_WEB'
        <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>ABK Meta Mount</title><style>body{font-family:system-ui,sans-serif;margin:20px;line-height:1.45;color:#171717;background:#f7f7f4}main{max-width:760px}button{padding:10px 14px;margin:0 8px 10px 0;border:1px solid #888;background:#fff;border-radius:6px}pre{white-space:pre-wrap;background:#101820;color:#eef5f5;padding:12px;border-radius:6px;min-height:180px;overflow:auto}</style></head><body><main><h1>ABK Meta Mount</h1><p>Built-in KernelSU metamodule provider. Disable is persistent; already-mounted overlays may require reboot to fully unwind.</p><button onclick="refresh()">Refresh</button><button onclick="setEnabled(1)">Enable</button><button onclick="setEnabled(0)">Disable</button><pre id="out">Loading...</pre></main><script>function out(v){document.getElementById('out').textContent=v}function sh(c){try{if(window.ksu&&typeof window.ksu.exec==='function'){return window.ksu.exec(c)}return 'KernelSU WebUI exec API unavailable'}catch(e){return String(e)}}function refresh(){out(sh('echo 1 > /sys/kernel/abk_meta_mount/prepare 2>/dev/null || true; cat /proc/abk_meta_mount/status 2>/dev/null || echo unavailable'))}function setEnabled(v){var c;if(v==1){c='rm -f /data/adb/modules/meta-abk-mount/disable /data/adb/modules/meta-abk-mount/remove; echo 1 > /sys/kernel/abk_meta_mount/enabled; echo 1 > /sys/kernel/abk_meta_mount/prepare 2>/dev/null || true'}else{c='mkdir -p /data/adb/modules/meta-abk-mount; touch /data/adb/modules/meta-abk-mount/disable; echo 0 > /sys/kernel/abk_meta_mount/enabled'}out(sh(c+'; cat /proc/abk_meta_mount/status 2>/dev/null || true'))}refresh()</script></body></html>
        ABK_META_WEB
        if [ -e "${'$'}MARK" ] && [ ! -L "${'$'}MARK" ]; then
            :
        else
            TAKEOVER=0
            if [ ! -e "${'$'}MARK" ]; then
                TAKEOVER=1
            elif [ -L "${'$'}MARK" ]; then
                CUR=${'$'}(readlink "${'$'}MARK" 2>/dev/null || true)
                if [ "${'$'}CUR" = "${'$'}MOD" ]; then
                    TAKEOVER=1
                elif [ -z "${'$'}CUR" ] || [ ! -d "${'$'}CUR" ] || [ -f "${'$'}CUR/disable" ] || [ -f "${'$'}CUR/remove" ]; then
                    TAKEOVER=1
                fi
            fi
            [ "${'$'}TAKEOVER" = 1 ] && ln -sfn "${'$'}MOD" "${'$'}MARK"
        fi
    """.trimIndent()

    private fun isAbkMetaMountModuleId(moduleId: String): Boolean =
        moduleId.trim() == ABK_META_MOUNT_ID

    private fun isAbkMetaMountModuleDir(moduleDir: String): Boolean =
        moduleDir.trim().trimEnd('/') == ABK_META_MOUNT_DIR

    @Suppress("DEPRECATION")
    private fun installedApplications(packageManager: PackageManager): List<ApplicationInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            packageManager.getInstalledApplications(0)
        }

    private fun setProfileSepolicy(packageName: String, rules: String): Boolean {
        if (packageName.isBlank()) return false
        val safePackage = shellQuote(packageName)
        val safeRules = shellQuote(rules)
        val script = """
            set -e
            ksud_path=${'$'}(abk_find_ksud)
            [ -n "${'$'}ksud_path" ] || exit 127
            abk_exec_ksud "${'$'}ksud_path" profile set-sepolicy $safePackage $safeRules
        """.trimIndent()
        return execRootScript(withManagerShellHelpers(script), timeoutSeconds = 30L).success
    }

    data class ShellResult(val success: Boolean, val output: List<String>)

    private data class OfficialLsposedModuleRow(
        val packageName: String,
        val apkPath: String,
        val enabled: Boolean,
        val scopes: List<String>
    )

    data class BootPatchResult(
        val success: Boolean,
        val output: List<String>,
        val patchedImagePath: String?
    )

    data class AbkLkmVariant(
        val id: String,
        val label: String
    )

    data class AbkLkmAsset(
        val variantId: String,
        val variantLabel: String,
        val kmi: String,
        val assetPath: String
    )

    val ABK_LKM_VARIANTS = listOf(
        AbkLkmVariant("kernelsu", "KernelSU"),
        AbkLkmVariant("sukisu", "SukiSU"),
        AbkLkmVariant("resukisu", "ReSukiSU")
    )

    enum class KsuFeatureSupport {
        SUPPORTED,
        UNSUPPORTED,
        MANAGED
    }

    data class KsuFeatureState(
        val name: String,
        val support: KsuFeatureSupport,
        val value: Long? = null,
        val configValue: Long? = null
    )

    data class ManagerRuntimeSnapshot(
        val manager: ManagerRuntimeProbe,
        val controlStatusJson: String? = null,
        val ksuModulesJson: String? = null
    )

    data class ManagerRuntimeProbe(
        val active: Boolean = false,
        val displayName: String = "",
        val variant: String = "",
        val backend: String = "",
        val version: String = "",
        val workMode: String = "",
        val capabilities: List<String> = emptyList(),
        val diagnostics: List<String> = emptyList()
    )

    enum class ManagerAccessKind {
        NATIVE_MANAGER,
        ROOT_ONLY,
        NO_ROOT,
        NATIVE_KERNEL_NO_MANAGER
    }

    data class ManagerAccessInfo(
        val kind: ManagerAccessKind,
        val diagnostic: String? = null,
        val runtime: ManagerRuntimeProbe? = null
    ) {
        val hasNativeManagerPermission: Boolean
            get() = kind == ManagerAccessKind.NATIVE_MANAGER
    }

    fun resolveManagerAccess(rootGranted: Boolean): ManagerAccessInfo {
        val nativeRuntime = detectNativeManagerRuntime()
        if (nativeRuntime?.active == true) {
            return ManagerAccessInfo(
                kind = ManagerAccessKind.NATIVE_MANAGER,
                diagnostic = nativeRuntime.diagnostics.firstOrNull(),
                runtime = nativeRuntime
            )
        }
        if (nativeRuntime != null) {
            return ManagerAccessInfo(
                kind = ManagerAccessKind.NATIVE_KERNEL_NO_MANAGER,
                diagnostic = nativeRuntime.diagnostics.firstOrNull(),
                runtime = nativeRuntime
            )
        }
        if (!rootGranted) {
            return ManagerAccessInfo(ManagerAccessKind.NO_ROOT)
        }
        val shellRuntime = detectShellManagerRuntime(nativeRuntime = null)
        return ManagerAccessInfo(
            kind = ManagerAccessKind.ROOT_ONLY,
            diagnostic = shellRuntime?.diagnostics?.firstOrNull(),
            runtime = shellRuntime
        )
    }

    private fun runKsudCommand(args: String, timeoutSeconds: Long): ShellResult {
        val cleanArgs = args.trim()
        if (cleanArgs.isBlank()) return ShellResult(false, listOf(tr(R.string.ru_ksud_args_empty)))
        val script = """
            set -e
            ksud_path=${'$'}(abk_find_ksud)
            [ -n "${'$'}ksud_path" ] || exit 127
            abk_exec_ksud "${'$'}ksud_path" $cleanArgs
        """.trimIndent()
        return execRootScript(withManagerShellHelpers(script), timeoutSeconds = timeoutSeconds)
    }

    private fun getKsuFeatureSupport(featureName: String): KsuFeatureSupport? {
        if (!isNativeManagerActive()) return null
        val result = runKsudCommand("feature check ${shellQuote(featureName)}", timeoutSeconds = 15L)
        val status = result.output
            .asReversed()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.lowercase()
        return when (status) {
            "supported" -> KsuFeatureSupport.SUPPORTED
            "unsupported" -> KsuFeatureSupport.UNSUPPORTED
            "managed" -> KsuFeatureSupport.MANAGED
            else -> null
        }
    }

    private fun getKsuFeatureValue(featureName: String): Long? {
        if (!isNativeManagerActive()) return null
        val result = runKsudCommand("feature get ${shellQuote(featureName)}", timeoutSeconds = 15L)
        if (!result.success) return null
        return parseKsuFeatureValue(result.output)
    }

    private fun getKsuFeatureConfigValue(featureName: String): Long? {
        if (!isNativeManagerActive()) return null
        val result = runKsudCommand("feature get ${shellQuote(featureName)} --config", timeoutSeconds = 15L)
        if (!result.success) return null
        return parseKsuFeatureValue(result.output)
    }

    private fun setKsuFeatureValue(featureName: String, value: Long, persist: Boolean): ShellResult {
        if (!isNativeManagerActive()) return nativeManagerPermissionDeniedResult()
        val setResult = runKsudCommand(
            "feature set ${shellQuote(featureName)} $value",
            timeoutSeconds = 30L
        )
        if (!setResult.success || !persist) return setResult
        return mergeShellResults(setResult, saveKsuFeatureConfig())
    }

    private fun setNativeKsuFeatureValue(featureName: String, value: Long, persist: Boolean): ShellResult {
        if (!isNativeManagerActive()) return nativeManagerPermissionDeniedResult()
        val enabled = value != 0L
        val setResult = when (featureName) {
            FEATURE_SU_COMPAT -> {
                val ok = AbkKsuNative.setSuEnabled(enabled)
                ShellResult(ok, if (ok) emptyList() else listOf(tr(R.string.ru_legacy_su_toggle_failed)))
            }
            FEATURE_SELINUX_HIDE -> {
                val code = AbkKsuNative.setSelinuxHideEnabled(enabled)
                ShellResult(code == 0, if (code == 0) emptyList() else listOf(tr(R.string.ru_hide_selinux_toggle_failed, code)))
            }
            else -> setKsuFeatureValue(featureName, value, persist = false)
        }
        if (!setResult.success || !persist) return setResult
        return mergeShellResults(setResult, saveKsuFeatureConfig())
    }

    private fun saveKsuFeatureConfig(): ShellResult =
        if (isNativeManagerActive()) {
            runKsudCommand("feature save", timeoutSeconds = 30L)
        } else {
            nativeManagerPermissionDeniedResult()
        }

    private fun readNativeFeature(featureName: String): KsuFeatureState? {
        if (!isNativeManagerActive()) return null
        val featureId = when (featureName) {
            FEATURE_SU_COMPAT -> 0
            FEATURE_KERNEL_UMOUNT -> 1
            FEATURE_SULOG -> 2
            FEATURE_ADB_ROOT -> 3
            FEATURE_SELINUX_HIDE -> 4
            else -> return null
        }
        val feature = AbkKsuNative.feature(featureId) ?: return null
        return KsuFeatureState(
            name = featureName,
            support = if (feature.supported) KsuFeatureSupport.SUPPORTED else KsuFeatureSupport.UNSUPPORTED,
            value = feature.value
        )
    }

    private fun parseKsuFeatureValue(output: List<String>): Long? {
        output.forEach { line ->
            val valueText = line.substringAfter("Value:", missingDelimiterValue = "")
                .trim()
                .takeIf { it.isNotBlank() }
            valueText?.toLongOrNull()?.let { return it }
        }
        return null
    }

    private fun normalizeKsuFeatureName(featureName: String): String? {
        val clean = featureName.trim().lowercase()
        return clean.takeIf { it.matches(KSU_FEATURE_NAME_REGEX) }
    }

    private fun isSafeTemplateId(id: String): Boolean {
        val clean = id.trim()
        return clean.isNotBlank() &&
            clean != "." &&
            clean != ".." &&
            '/' !in clean &&
            '\\' !in clean
    }

    private fun mergeShellResults(vararg results: ShellResult): ShellResult =
        ShellResult(
            success = results.all { it.success },
            output = results.flatMap { it.output }
        )

    private fun runLocalCommand(
        command: List<String>,
        timeoutSeconds: Long,
        onOutput: ((String) -> Unit)? = null
    ): ShellResult {
        val output = Collections.synchronizedList(mutableListOf<String>())
        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val reader = thread(start = true) {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        output.add(line)
                        onOutput?.invoke(line)
                    }
                }
            }
            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                val line = tr(R.string.ru_command_timeout)
                output.add(line)
                onOutput?.invoke(line)
                return ShellResult(false, output.toList())
            }
            reader.join(2000L)
            ShellResult(process.exitValue() == 0, output.toList())
        } catch (error: Throwable) {
            val line = error.message ?: error::class.java.simpleName
            onOutput?.invoke(line)
            ShellResult(false, listOf(line))
        }
    }

    private fun execRootScript(
        script: String,
        timeoutSeconds: Long,
        onOutput: ((String) -> Unit)? = null
    ): ShellResult {
        return try {
            createRootShell(timeoutSeconds = timeoutSeconds).use { shell ->
                execWithShell(shell, script, onOutput)
            }
        } catch (error: Throwable) {
            Log.w(TAG, "root command failed", error)
            val line = tr(R.string.ru_manager_not_active)
            onOutput?.invoke(line)
            ShellResult(false, listOf(line))
        }
    }

    private fun execWithShell(
        shell: Shell,
        script: String,
        onOutput: ((String) -> Unit)? = null,
        normalizeOutput: Boolean = true
    ): ShellResult {
        val output = rootOutputList(onOutput)
        val result = shell.newJob()
            .to(output, output)
            .add(script)
            .exec()
        val lines = if (normalizeOutput) {
            normalizedOutput(result.isSuccess, output, onOutput)
        } else {
            output.toList()
        }
        return ShellResult(result.isSuccess, lines)
    }

    private fun probeOfficialLsposedRuntime(): ShellResult {
        val script = """
            set +e
            module_dir=""
            module_present=0
            payload_available=0
            zygote_running=0
            daemon_active=0
            daemon_pid=""
            service_started=0
            module_version=""

            for candidate in \
                /data/adb/modules/zygisk_lsposed \
                /data/adb/modules/riru_lsposed \
                /data/adb/modules/lsposed \
                /data/adb/modules/*_lsposed \
                /data/adb/modules_update/zygisk_lsposed \
                /data/adb/modules_update/riru_lsposed
            do
                [ -d "${'$'}candidate" ] || continue
                [ -f "${'$'}candidate/remove" ] && continue
                [ -f "${'$'}candidate/disable" ] && continue
                module_dir="${'$'}candidate"
                module_present=1
                break
            done

            if [ "${'$'}module_present" = 1 ]; then
                [ -f "${'$'}module_dir/module.prop" ] && module_version=${'$'}(grep '^version=' "${'$'}module_dir/module.prop" 2>/dev/null | head -n 1 | cut -d= -f2-)
                if [ -f "${'$'}module_dir/daemon.apk" ] && [ -f "${'$'}module_dir/daemon" ]; then
                    payload_available=1
                fi
                if ls "${'$'}module_dir"/zygisk/*.so >/dev/null 2>&1; then
                    payload_available=1
                fi
                if ls "${'$'}module_dir"/riru/lib*/liblspd.so >/dev/null 2>&1; then
                    payload_available=1
                fi
            fi

            find_lspd_pid() {
                pid=${'$'}(pidof lspd 2>/dev/null | sed 's/[[:space:]].*//')
                if [ -n "${'$'}pid" ]; then
                    printf '%s\n' "${'$'}pid"
                    return
                fi
                for proc in /proc/[0-9]*; do
                    [ -r "${'$'}proc/cmdline" ] || continue
                    cmd=${'$'}(tr '\000' ' ' < "${'$'}proc/cmdline" 2>/dev/null)
                    case "${'$'}cmd" in
                        *org.lsposed.lspd.Main*|*--nice-name=lspd*)
                            printf '%s\n' "${'$'}{proc##*/}"
                            return
                            ;;
                    esac
                done
            }

            if pidof zygote64 zygote >/dev/null 2>&1 || [ -S /dev/socket/zygote ]; then
                zygote_running=1
            fi

            daemon_pid=${'$'}(find_lspd_pid)
            if [ -n "${'$'}daemon_pid" ]; then
                daemon_active=1
            elif [ "${'$'}module_present" = 1 ] && [ -f "${'$'}module_dir/service.sh" ]; then
                ( cd "${'$'}module_dir" && sh ./service.sh --system-server-max-retry=-1 >/dev/null 2>&1 ) &
                service_started=1
                sleep 1
                daemon_pid=${'$'}(find_lspd_pid)
                [ -n "${'$'}daemon_pid" ] && daemon_active=1
            fi

            config_db=0
            [ -f ${shellQuote(LSPOSED_CONFIG_DB)} ] && config_db=1
            printf 'module_present=%s\n' "${'$'}module_present"
            printf 'module_dir=%s\n' "${'$'}module_dir"
            printf 'module_version=%s\n' "${'$'}module_version"
            printf 'payload_available=%s\n' "${'$'}payload_available"
            printf 'zygote_running=%s\n' "${'$'}zygote_running"
            printf 'daemon_active=%s\n' "${'$'}daemon_active"
            printf 'daemon_pid=%s\n' "${'$'}daemon_pid"
            printf 'service_started=%s\n' "${'$'}service_started"
            printf 'config_db=%s\n' "${'$'}config_db"
            exit 0
        """.trimIndent()
        return execRootScript(script, timeoutSeconds = 20L)
    }

    private fun prepareOfficialLsposedDatabase(tempDb: File): ShellResult {
        tempDb.delete()
        val parent = tempDb.parentFile ?: return ShellResult(false, listOf("invalid LSPosed sync cache path"))
        val safeParent = shellQuote(parent.absolutePath)
        val safeTempDb = shellQuote(tempDb.absolutePath)
        val safeSourceDb = shellQuote(LSPOSED_CONFIG_DB)
        val appUid = Process.myUid()
        val script = """
            set -e
            mkdir -p $safeParent
            module_present=0
            for candidate in /data/adb/modules/zygisk_lsposed /data/adb/modules/riru_lsposed /data/adb/modules/lsposed /data/adb/modules/*_lsposed; do
                [ -d "${'$'}candidate" ] || continue
                [ -f "${'$'}candidate/remove" ] && continue
                [ -f "${'$'}candidate/disable" ] && continue
                module_present=1
                break
            done
            if [ -f $safeSourceDb ]; then
                cp -f $safeSourceDb $safeTempDb
                [ -f $safeSourceDb-wal ] && cp -f $safeSourceDb-wal $safeTempDb-wal || true
                [ -f $safeSourceDb-shm ] && cp -f $safeSourceDb-shm $safeTempDb-shm || true
                echo "copied official LSPosed config DB"
            elif [ "${'$'}module_present" = 1 ]; then
                : > $safeTempDb
                echo "created official LSPosed config DB"
            else
                echo "official LSPosed module/config not found"
                exit 4
            fi
            chown $appUid:$appUid $safeTempDb $safeTempDb-wal $safeTempDb-shm 2>/dev/null || true
            chmod 0600 $safeTempDb $safeTempDb-wal $safeTempDb-shm 2>/dev/null || true
        """.trimIndent()
        return execRootScript(script, timeoutSeconds = 15L)
    }

    private fun commitOfficialLsposedDatabase(tempDb: File): ShellResult {
        val safeTempDb = shellQuote(tempDb.absolutePath)
        val safeSourceDb = shellQuote(LSPOSED_CONFIG_DB)
        val script = """
            set -e
            src=$safeTempDb
            dst=$safeSourceDb
            [ -f "${'$'}src" ] || { echo "temporary LSPosed config DB missing"; exit 4; }
            mkdir -p /data/adb/lspd/config
            if [ -f "${'$'}dst" ]; then
                cp -af "${'$'}dst" "${'$'}dst.abk.bak" 2>/dev/null || true
            fi
            rm -f "${'$'}dst-wal" "${'$'}dst-shm"
            cp -f "${'$'}src" "${'$'}dst"
            chown 0:0 "${'$'}dst" 2>/dev/null || true
            chmod 0600 "${'$'}dst" 2>/dev/null || true
            restorecon "${'$'}dst" 2>/dev/null || chcon u:object_r:system_file:s0 "${'$'}dst" 2>/dev/null || true
            killall lspd 2>/dev/null || true
            restarted=0
            for service in /data/adb/modules/zygisk_lsposed/service.sh /data/adb/modules/riru_lsposed/service.sh /data/adb/modules/lsposed/service.sh /data/adb/modules/*_lsposed/service.sh; do
                [ -f "${'$'}service" ] || continue
                moddir=${'$'}{service%/*}
                [ -f "${'$'}moddir/remove" ] && continue
                [ -f "${'$'}moddir/disable" ] && continue
                ( cd "${'$'}moddir" && sh ./service.sh --system-server-max-retry=-1 >/dev/null 2>&1 ) &
                echo "restarted lspd via ${'$'}service"
                restarted=1
                break
            done
            [ "${'$'}restarted" = 1 ] || echo "official LSPosed service script not found; config will apply after next daemon start"
        """.trimIndent()
        return execRootScript(script, timeoutSeconds = 20L)
    }

    private fun writeOfficialLsposedDatabase(
        dbFile: File,
        modules: List<OfficialLsposedModuleRow>
    ) {
        val db = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY
        )
        try {
            db.rawQuery("PRAGMA journal_mode=DELETE", null).use { cursor ->
                cursor.moveToFirst()
            }
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS modules (" +
                    "mid integer PRIMARY KEY AUTOINCREMENT," +
                    "module_pkg_name text NOT NULL UNIQUE," +
                    "apk_path text NOT NULL," +
                    "enabled BOOLEAN DEFAULT 0 CHECK (enabled IN (0, 1))" +
                    ")"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS scope (" +
                    "mid integer," +
                    "app_pkg_name text NOT NULL," +
                    "user_id integer NOT NULL," +
                    "PRIMARY KEY (mid, app_pkg_name, user_id)," +
                    "CONSTRAINT scope_module_constraint FOREIGN KEY (mid) REFERENCES modules (mid) ON DELETE CASCADE" +
                    ")"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS configs (" +
                    "module_pkg_name text NOT NULL," +
                    "user_id integer NOT NULL," +
                    "`group` text NOT NULL," +
                    "`key` text NOT NULL," +
                    "data blob NOT NULL," +
                    "PRIMARY KEY (module_pkg_name, user_id, `group`, `key`)," +
                    "CONSTRAINT config_module_constraint FOREIGN KEY (module_pkg_name) REFERENCES modules (module_pkg_name) ON DELETE CASCADE" +
                    ")"
            )
            db.beginTransaction()
            try {
                modules.forEach { module ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO modules(module_pkg_name, apk_path, enabled) VALUES(?, ?, ?)",
                        arrayOf<Any>(module.packageName, module.apkPath, if (module.enabled) 1 else 0)
                    )
                    db.execSQL(
                        "UPDATE modules SET apk_path = ?, enabled = ? WHERE module_pkg_name = ?",
                        arrayOf<Any>(module.apkPath, if (module.enabled) 1 else 0, module.packageName)
                    )
                    val mid = queryOfficialLsposedModuleId(db, module.packageName) ?: return@forEach
                    db.execSQL("DELETE FROM scope WHERE mid = ?", arrayOf<Any>(mid))
                    module.scopes
                        .mapNotNull(::parseOfficialLsposedScope)
                        .distinct()
                        .forEach { (packageName, userId) ->
                            db.execSQL(
                                "INSERT OR IGNORE INTO scope(mid, app_pkg_name, user_id) VALUES(?, ?, ?)",
                                arrayOf<Any>(mid, packageName, userId)
                            )
                        }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { cursor ->
                cursor.moveToFirst()
            }
        } finally {
            db.close()
        }
    }

    private fun queryOfficialLsposedModuleId(db: SQLiteDatabase, packageName: String): Long? {
        db.rawQuery("SELECT mid FROM modules WHERE module_pkg_name = ?", arrayOf(packageName)).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveOfficialLsposedModuleApkPath(packageName: String): String? {
        val context = appContext ?: return null
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                )
            } else {
                context.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            }
        }.getOrNull() ?: return null
        val splitSourceDirs = info.splitSourceDirs ?: emptyArray()
        val apks = (splitSourceDirs.toList() + info.sourceDir)
            .filter { it.isNotBlank() }
            .distinct()
        return apks.firstOrNull { apk ->
            runCatching {
                ZipFile(apk).use { zip ->
                    zip.getEntry("META-INF/xposed/java_init.list") != null ||
                        zip.getEntry("assets/xposed_init") != null
                }
            }.getOrDefault(false)
        }
    }

    private fun parseOfficialLsposedScope(raw: String): Pair<String, Int>? {
        val clean = raw.trim()
        if (clean.isBlank()) return null
        val match = Regex("""^u?(\d+):(.+)$""").matchEntire(clean)
        val packageName = match?.groupValues?.getOrNull(2)?.trim() ?: clean
        val userId = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        if (packageName == "system" && userId != 0) return null
        if (!packageName.matches(Regex("[A-Za-z0-9._:-]+"))) return null
        return packageName to userId
    }

    private fun parseShellKeyValueOutput(lines: List<String>): Map<String, String> =
        lines.mapNotNull { line ->
            val index = line.indexOf('=')
            if (index <= 0) null else line.substring(0, index).trim() to line.substring(index + 1).trim()
        }.toMap()

    private fun detectManagerRuntime(preferLspBridge: Boolean = true): ManagerRuntimeProbe {
        val nativeRuntime = detectNativeManagerRuntime()
        if (preferLspBridge) {
            val lspBridgeRuntime = detectLspBridgeRuntime(nativeRuntime)
            if (lspBridgeRuntime != null) {
                return lspBridgeRuntime
            }
        }

        if (nativeRuntime?.active == true) {
            return nativeRuntime
        }

        val shellRuntime = detectShellManagerRuntime(nativeRuntime)
        if (shellRuntime != null) {
            return shellRuntime
        }

        return nativeRuntime ?: ManagerRuntimeProbe(
            diagnostics = listOf(tr(R.string.ru_no_manager_interface))
        )
    }

    private fun detectLspBridgeRuntime(
        nativeRuntime: ManagerRuntimeProbe?
    ): ManagerRuntimeProbe? {
        if (nativeRuntime?.active != true) return null
        val raw = readAbkLspBridgeStatus()
            ?.takeIf { it.success }
            ?.output
            ?.joinToString("\n")
            ?.trim()
            ?.takeIf { it.startsWith("{") }
            ?: return null

        return runCatching {
            val root = JSONObject(raw)
            val modules = root.optJSONArray("modules") ?: return@runCatching null
            var module: JSONObject? = null
            for (index in 0 until modules.length()) {
                val item = modules.optJSONObject(index) ?: continue
                if (item.optString("id") == "abk_lsp_bridge") {
                    module = item
                    break
                }
            }
            val bridge = module ?: return@runCatching null

            val displayName = bridge.optString("name").ifBlank { "ABK LSP Bridge" }
            val variant = "ABK LSP Bridge"
            val backend = "lsp_bridge"
            val version = bridge.optString("version").ifBlank { "0.1.0" }
            val capabilities = buildList {
                add("lsp_bridge")
                add("zygote_helper")
                add("plugin_bridge")
                add("hook_policies")
                add("abk_control")
                add("native_manager")
                add("root_policy")
            }.distinct()
            val diagnostics = buildList {
                bridge.optString("description")
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?.let(::add)
                add("rpc bridge via abk_control active")
            }

            ManagerRuntimeProbe(
                active = bridge.optBoolean("enabled", true),
                displayName = displayName,
                variant = variant,
                backend = backend,
                version = version,
                workMode = root.optString("work_mode").orEmpty().ifBlank { nativeRuntime.workMode.ifBlank { "built-in" } },
                capabilities = capabilities,
                diagnostics = diagnostics
            )
        }.getOrNull()
    }

    private fun detectShellManagerRuntime(
        nativeRuntime: ManagerRuntimeProbe?
    ): ManagerRuntimeProbe? {
        return try {
            createRootShell(timeoutSeconds = 10L).use { shell ->
                val ksudPath = execWithShell(
                    shell = shell,
                    script = withManagerShellHelpers("abk_find_ksud"),
                    onOutput = null,
                    normalizeOutput = false
                ).output.firstOrNull()?.trim().orEmpty()

                if (ksudPath.isNotBlank()) {
                    val safeKsud = shellQuote(ksudPath)
                    val version = execWithShell(
                        shell,
                        withManagerShellHelpers(
                            "abk_exec_ksud $safeKsud --version 2>/dev/null || true"
                        ),
                        normalizeOutput = false
                    )
                        .output
                        .firstOrNull()
                        ?.trim()
                        .orEmpty()
                    val capabilityOutput = execWithShell(
                        shell,
                        withManagerShellHelpers(
                            """
                            caps="root_shell modules"
                            abk_exec_ksud $safeKsud module list >/dev/null 2>&1 && caps="${'$'}caps module_control"
                            abk_exec_ksud $safeKsud susfs status >/dev/null 2>&1 && caps="${'$'}caps susfs"
                            abk_exec_ksud $safeKsud kpm version >/dev/null 2>&1 && caps="${'$'}caps kpm"
                            abk_exec_ksud $safeKsud feature check su_compat >/dev/null 2>&1 && caps="${'$'}caps features"
                            printf '%s\n' "${'$'}caps"
                            """.trimIndent()
                        ),
                        normalizeOutput = false
                    ).output.firstOrNull().orEmpty()
                    val capabilities = capabilityOutput
                        .split(' ', '\n', '\t')
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                    val variant = inferManagerVariant(version).ifBlank { "KernelSU" }
                    ManagerRuntimeProbe(
                        active = true,
                        displayName = nativeRuntime?.displayName?.takeIf { it.isNotBlank() } ?: variant,
                        variant = nativeRuntime?.variant?.takeIf { it.isNotBlank() } ?: variant,
                        backend = "ksud",
                        version = version.ifBlank { nativeRuntime?.version.orEmpty() },
                        workMode = nativeRuntime?.workMode.orEmpty(),
                        capabilities = capabilities.ifEmpty { listOf("root_shell", "modules") },
                        diagnostics = (
                            nativeRuntime?.diagnostics.orEmpty() +
                                tr(R.string.ru_diag_compat_shell_only)
                            ).distinct()
                    )
                } else {
                    ManagerRuntimeProbe(
                        active = true,
                        displayName = "Root",
                        variant = "Generic",
                        backend = "su",
                        workMode = nativeRuntime?.workMode.orEmpty(),
                        capabilities = listOf("root_shell"),
                        diagnostics = (
                            nativeRuntime?.diagnostics.orEmpty() +
                                tr(R.string.ru_diag_generic_su_only)
                            ).distinct()
                    )
                }
            }
        } catch (error: Throwable) {
            null
        }
    }

    private fun detectNativeManagerRuntime(): ManagerRuntimeProbe? {
        val status = AbkKsuNative.status() ?: return null
        val versionText = listOf(
            status.fullVersion,
            "kernel ${status.version}",
            status.hookType
        )
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        val nativeVariant = inferManagerVariant(versionText).ifBlank { "KernelSU" }
        if (!status.isManager) {
            return ManagerRuntimeProbe(
                active = false,
                displayName = nativeVariant,
                variant = nativeVariant,
                backend = "native",
                version = versionText,
                workMode = if (status.isLkmMode) "lkm" else "built-in",
                capabilities = listOf("native_kernel"),
                diagnostics = listOf(
                    tr(R.string.ru_diag_native_not_manager)
                )
            )
        }
        val workMode = if (status.isLkmMode) "lkm" else "built-in"
        val controlJson = if (status.isLkmMode) null else AbkKsuNative.controlStatus()
        val controlVariant = controlJson
            ?.let { json ->
                runCatching {
                    JSONObject(json)
                        .optJSONObject("manager")
                        ?.optString("variant")
                        .orEmpty()
                        .trim()
                }.getOrDefault("")
            }
            .orEmpty()
        val displayVariant = controlVariant.ifBlank { nativeVariant }
        val diagnostics = buildList {
            if (controlJson == null && !status.isLkmMode) {
                add(tr(R.string.ru_diag_control_no_response))
            }
        }
        val capabilities = buildList {
            add("native_manager")
            add("root_policy")
            if (controlVariant.isNotBlank()) add("abk_control")
            if (status.superuserCount > 0) add("superuser_profiles")
            if (status.isLkmMode) add("lkm")
            if (status.isLateLoadMode) add("late_load")
            if (status.isSafeMode) add("safe_mode")
        }
        return ManagerRuntimeProbe(
            active = true,
            displayName = displayVariant,
            variant = displayVariant,
            backend = "native",
            version = versionText,
            workMode = workMode,
            capabilities = capabilities,
            diagnostics = diagnostics
        )
    }

    private fun createRootShell(
        timeoutSeconds: Long,
        globalMount: Boolean = true
    ): Shell {
        val builder = Shell.Builder.create()
            .setFlags(Shell.FLAG_MOUNT_MASTER or Shell.FLAG_REDIRECT_STDERR)
            .setTimeout(timeoutSeconds)
        val candidates = mutableListOf<Array<String>>()
        if (globalMount) {
            candidates += arrayOf("/data/adb/ksud", "debug", "su", "-g")
            candidates += arrayOf("ksud", "debug", "su", "-g")
            candidates += arrayOf("su", "-mm")
        } else {
            candidates += arrayOf("/data/adb/ksud", "debug", "su")
            candidates += arrayOf("ksud", "debug", "su")
            candidates += arrayOf("su")
        }

        candidates.forEach { command ->
            try {
                val shell = builder.build(*command)
                if (isShellRoot(shell)) return shell
                shell.close()
            } catch (error: Throwable) {
                Log.d(TAG, "root shell candidate failed: ${command.firstOrNull().orEmpty()}", error)
            }
        }

        val shell = builder.build()
        if (isShellRoot(shell)) return shell
        shell.close()
        error("Root shell unavailable")
    }

    private fun isShellRoot(shell: Shell): Boolean {
        val output = mutableListOf<String>()
        val result = shell.newJob()
            .to(output, output)
            .add("id -u")
            .exec()
        return result.isSuccess && output.firstOrNull()?.trim() == "0"
    }

    private fun embeddedKsudPath(context: Context? = appContext): String? {
        val safeContext = context ?: return null
        return File(safeContext.applicationInfo.nativeLibraryDir, "libksud.so")
            .takeIf { it.isFile }
            ?.absolutePath
    }

    private fun isEmbeddedKsudPath(path: String): Boolean =
        File(path).name == "libksud.so"

    private fun resolveAndroidLinkerPath(): String {
        val embeddedPath = embeddedKsudPath()
        val prefers64Bit = embeddedPath?.contains("/arm64-v8a/") == true ||
            embeddedPath?.contains("/x86_64/") == true ||
            embeddedPath?.contains("64") == true
        val candidates = if (prefers64Bit) {
            listOf("/apex/com.android.runtime/bin/linker64", "/system/bin/linker64")
        } else {
            listOf("/apex/com.android.runtime/bin/linker", "/system/bin/linker")
        }
        return candidates.firstOrNull { File(it).isFile } ?: candidates.first()
    }

    private fun buildKsudCommand(ksudPath: String, args: List<String>): List<String> {
        return if (isEmbeddedKsudPath(ksudPath)) {
            listOf(resolveAndroidLinkerPath(), ksudPath) + args
        } else {
            listOf(ksudPath) + args
        }
    }

    private fun buildKsudShellCommand(ksudPath: String, args: List<String>): String =
        buildShellCommand(buildKsudCommand(ksudPath, args))

    private fun runEmbeddedKsudWithRoot(
        context: Context,
        args: List<String>,
        timeoutSeconds: Long,
        onOutput: ((String) -> Unit)? = null
    ): ShellResult? {
        val embedded = embeddedKsudPath(context) ?: return null
        return try {
            createRootShell(timeoutSeconds = timeoutSeconds).use { shell ->
                execWithShell(
                    shell,
                    buildKsudShellCommand(embedded, args),
                    onOutput = onOutput
                )
            }
        } catch (error: Throwable) {
            Log.w(TAG, "embedded ksud root command unavailable", error)
            null
        }
    }

    private fun prepareBundledKsudPath(context: Context): String? {
        val metadata = readBundledKsudMetadata(context) ?: return null
        val abi = selectBundledKsudAbi(context, metadata) ?: return null
        val assetPath = "$BUNDLED_KSUD_ASSET_DIR/$abi/$BUNDLED_KSUD_BINARY_NAME"
        if (!assetExists(context, assetPath)) return null

        val rootDir = File(context.filesDir, BUNDLED_KSUD_INSTALL_DIR).apply { mkdirs() }
        val installDir = File(rootDir, "${metadata.installToken}/$abi").apply { mkdirs() }
        val binaryFile = File(installDir, BUNDLED_KSUD_BINARY_NAME)

        if (isBundledKsudReady(binaryFile, abi, metadata)) {
            cleanupObsoleteBundledKsud(rootDir, metadata.installToken)
            return binaryFile.absolutePath
        }

        installDir.deleteRecursively()
        installDir.mkdirs()
        val tempFile = File(installDir, "$BUNDLED_KSUD_BINARY_NAME.tmp")
        return runCatching {
            context.assets.open(assetPath).use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            tempFile.setReadable(true, true)
            tempFile.setWritable(true, true)
            tempFile.setExecutable(true, true)
            val installed = File(installDir, BUNDLED_KSUD_BINARY_NAME)
            if (!tempFile.renameTo(installed)) {
                tempFile.copyTo(installed, overwrite = true)
                tempFile.delete()
            }
            installed.setReadable(true, true)
            installed.setWritable(true, true)
            installed.setExecutable(true, true)
            if (!isBundledKsudReady(installed, abi, metadata)) {
                installed.delete()
                return@runCatching null
            }
            cleanupObsoleteBundledKsud(rootDir, metadata.installToken)
            installed.absolutePath
        }.getOrNull()
    }

    private fun stageBundledAbkLkmAsset(
        context: Context,
        workDir: File,
        asset: AbkLkmAsset
    ): File {
        val target = File(workDir, "${asset.variantId}_${asset.kmi}_kernelsu.ko")
        context.assets.open(asset.assetPath).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        target.setReadable(true, false)
        target.setWritable(true, true)
        return target
    }

    private fun buildBootPatchArgs(
        context: Context,
        bootImage: File?,
        moduleFile: File,
        flash: Boolean,
        ota: Boolean,
        partition: String?,
        outputDir: File,
        outputName: String,
        allowShell: Boolean,
        enableAdb: Boolean
    ): List<String> {
        return buildList {
            add("boot-patch")
            if (bootImage != null) {
                add("--boot")
                add(bootImage.absolutePath)
            }
            add("--module")
            add(moduleFile.absolutePath)
            if (flash) add("--flash")
            if (ota) add("--ota")
            partition?.takeIf { it.isNotBlank() }?.let {
                add("--partition")
                add(it)
            }
            add("--out")
            add(outputDir.absolutePath)
            add("--out-name")
            add(outputName)
            if (allowShell) add("--allow-shell")
            if (enableAdb) add("--enable-adbd")
        }
    }

    private fun runEmbeddedBootPatchWithRoot(
        context: Context,
        args: List<String>,
        onOutput: ((String) -> Unit)? = null
    ): ShellResult? {
        val embedded = embeddedKsudPath(context) ?: return null
        return try {
            createRootShell(timeoutSeconds = 300L).use { shell ->
                onOutput?.invoke(tr(R.string.ru_log_invoke_libksud))
                onOutput?.invoke(tr(R.string.ru_log_ksud_path, embedded))
                execWithShell(
                    shell,
                    buildKsudShellCommand(embedded, args),
                    onOutput = onOutput
                )
            }
        } catch (error: Throwable) {
            Log.w(TAG, "root boot-patch shell unavailable", error)
            null
        }
    }

    private fun runBundledUserlandBootPatch(
        context: Context,
        args: List<String>,
        onOutput: ((String) -> Unit)? = null
    ): ShellResult? {
        val userlandKsud = resolveUserlandKsudPath(context) ?: return null
        onOutput?.invoke(tr(R.string.ru_log_local_boot_patch))
        onOutput?.invoke(tr(R.string.ru_log_ksud_path, userlandKsud))
        return runLocalCommand(
            command = buildKsudCommand(userlandKsud, args),
            timeoutSeconds = 300L,
            onOutput = onOutput
        )
    }

    private fun readEmbeddedBootInfoLine(args: List<String>): String? =
        readEmbeddedBootInfoLines(args).firstOrNull()

    private fun readEmbeddedBootInfoLines(args: List<String>): List<String> {
        val context = appContext ?: return emptyList()
        val result = runEmbeddedKsudWithRoot(
            context = context,
            args = listOf("boot-info") + args,
            timeoutSeconds = 15L
        ) ?: return emptyList()
        if (!result.success) return emptyList()
        return result.output
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun readBundledKsudMetadata(context: Context): BundledKsudMetadata? {
        return runCatching {
            val props = Properties()
            context.assets.open("$BUNDLED_KSUD_ASSET_DIR/$BUNDLED_KSUD_METADATA_NAME").use(props::load)
            val listedAbis = runCatching {
                context.assets.list(BUNDLED_KSUD_ASSET_DIR).orEmpty().toList()
            }.getOrDefault(emptyList())
                .filter { it.isNotBlank() && it != BUNDLED_KSUD_METADATA_NAME }
            val supportedAbis = props.getProperty("abis")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.ifEmpty { listedAbis }
                ?: listedAbis
            val sha256ByAbi = supportedAbis.mapNotNull { abi ->
                props.getProperty("sha256.$abi")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { digest -> abi to digest.lowercase() }
            }.toMap()
            BundledKsudMetadata(
                ref = props.getProperty("ref").orEmpty(),
                commit = props.getProperty("commit").orEmpty(),
                supportedAbis = supportedAbis,
                sha256ByAbi = sha256ByAbi
            )
        }.getOrNull()
    }

    private fun selectBundledKsudAbi(
        context: Context,
        metadata: BundledKsudMetadata
    ): String? {
        val supported = metadata.supportedAbis.toSet()
        Build.SUPPORTED_ABIS.forEach { abi ->
            if (abi in supported && assetExists(context, "$BUNDLED_KSUD_ASSET_DIR/$abi/$BUNDLED_KSUD_BINARY_NAME")) {
                return abi
            }
        }
        return metadata.supportedAbis.firstOrNull { abi ->
            assetExists(context, "$BUNDLED_KSUD_ASSET_DIR/$abi/$BUNDLED_KSUD_BINARY_NAME")
        }
    }

    private fun assetExists(context: Context, assetPath: String): Boolean =
        runCatching {
            context.assets.open(assetPath).use { true }
        }.getOrDefault(false)

    private fun isBundledKsudReady(
        binaryFile: File,
        abi: String,
        metadata: BundledKsudMetadata
    ): Boolean {
        if (!binaryFile.isFile || binaryFile.length() <= 0L) return false
        if (!binaryFile.canExecute()) {
            binaryFile.setExecutable(true, true)
        }
        if (!binaryFile.canExecute()) return false
        val expectedSha256 = metadata.sha256ByAbi[abi] ?: return true
        return sha256(binaryFile)?.equals(expectedSha256, ignoreCase = true) == true
    }

    private fun sha256(file: File): String? {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    private fun cleanupObsoleteBundledKsud(rootDir: File, currentToken: String) {
        rootDir.listFiles()
            ?.filter { it.isDirectory && it.name != currentToken }
            ?.forEach { it.deleteRecursively() }
    }

    private fun withManagerShellHelpers(script: String): String {
        val embedded = embeddedKsudPath()?.let(::shellQuote) ?: "''"
        val linker = shellQuote(resolveAndroidLinkerPath())
        return """
            abk_embedded_ksud=$embedded
            abk_embedded_linker=$linker
            abk_find_ksud() {
                for candidate in "${'$'}abk_embedded_ksud" /data/adb/ksud ${'$'}(command -v ksud 2>/dev/null || true); do
                    [ -n "${'$'}candidate" ] || continue
                    if [ -n "${'$'}abk_embedded_ksud" ] && [ "${'$'}candidate" = "${'$'}abk_embedded_ksud" ]; then
                        [ -r "${'$'}candidate" ] || continue
                    else
                        [ -x "${'$'}candidate" ] || continue
                    fi
                    printf '%s\n' "${'$'}candidate"
                    return 0
                done
                return 1
            }
            abk_exec_ksud() {
                local candidate="$1"
                shift
                if [ -n "${'$'}abk_embedded_ksud" ] && [ "${'$'}candidate" = "${'$'}abk_embedded_ksud" ]; then
                    "${'$'}abk_embedded_linker" "${'$'}candidate" "${'$'}@"
                else
                    "${'$'}candidate" "${'$'}@"
                fi
            }
            abk_ksud_source() {
                if [ -n "${'$'}abk_embedded_ksud" ] && [ "$1" = "${'$'}abk_embedded_ksud" ]; then
                    printf '%s\n' "embedded"
                elif [ "$1" = "/data/adb/ksud" ]; then
                    printf '%s\n' "data_adb"
                else
                    printf '%s\n' "system"
                fi
            }
            abk_ksud_label() {
                case "$1" in
                    embedded) printf '%s\n' "内置 SukiSU-Ultra" ;;
                    data_adb) printf '%s\n' "外部 /data/adb" ;;
                    *) printf '%s\n' "系统" ;;
                esac
            }
            $script
        """.trimIndent()
    }

    private fun inferManagerVariant(version: String): String {
        val lower = version.lowercase()
        return when {
            "resukisu" in lower -> "ReSukiSU"
            "sukisu" in lower -> "SukiSU"
            "kernelsu" in lower || version.isNotBlank() -> "KernelSU"
            else -> ""
        }
    }

    private fun sanitizeWebRelativePath(value: String): String? {
        val path = value.substringBefore('?').substringBefore('#')
            .trim()
            .trimStart('/')
            .ifBlank { "index.html" }
        if (path.split('/').any { it == ".." || it.contains('\u0000') }) return null
        return path
    }

    private fun rootOutputList(onOutput: ((String) -> Unit)?): MutableList<String> {
        val output = Collections.synchronizedList(mutableListOf<String>())
        return if (onOutput == null) {
            output
        } else {
            object : CallbackList<String>(output) {
                override fun onAddElement(element: String) {
                    onOutput(element)
                }
            }
        }
    }

    private fun normalizedOutput(
        success: Boolean,
        output: List<String>,
        onOutput: ((String) -> Unit)?
    ): List<String> {
        if (output.isNotEmpty()) return output.toList()
        val fallback = if (success) {
            tr(R.string.ru_log_done_no_output)
        } else {
            tr(R.string.ru_log_failed_no_output)
        }
        onOutput?.invoke(fallback)
        return listOf(fallback)
    }

    private fun nativeManagerPermissionDeniedMessage(): String =
        tr(R.string.ru_no_native_permission)

    private fun nativeManagerPermissionDeniedResult(): ShellResult =
        ShellResult(false, listOf(nativeManagerPermissionDeniedMessage()))

    private fun partitionExists(name: String): Boolean {
        return listOf(
            "/dev/block/by-name/$name",
            "/dev/block/bootdevice/by-name/$name",
            "/dev/block/mapper/$name"
        ).any { File(it).exists() }
    }

    private fun detectSystemProperty(name: String): String? {
        return runCatching {
            Runtime.getRuntime()
                .exec(arrayOf("/system/bin/getprop", name))
                .inputStream
                .bufferedReader()
                .use { it.readText().trim() }
                .ifBlank { null }
        }.getOrNull()
    }

    private fun buildShellCommand(args: List<String>): String =
        args.joinToString(" ") { shellQuote(it) }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

    internal fun rewriteAnyKernelSlotSelect(
        scriptContent: String,
        targetSlot: Ak3SlotTarget
    ): String? {
        val lineRegex = Regex("""(?m)^([ \t]*slot_select=)[^\r\n]*$""")
        val match = lineRegex.find(scriptContent) ?: return null
        return buildString {
            append(scriptContent.substring(0, match.range.first))
            append(match.groupValues[1])
            append(targetSlot.slotSelectValue)
            append(scriptContent.substring(match.range.last + 1))
        }
    }

    private fun prepareAnyKernel3Zip(
        sourceZip: File,
        targetSlot: Ak3SlotTarget,
        workDir: File,
        onOutput: ((String) -> Unit)?
    ): File? {
        val expandDir = File(workDir, "anykernel-src").apply {
            deleteRecursively()
            mkdirs()
        }
        unzipToDirectory(sourceZip, expandDir)
        val anyKernelScript = expandDir.walkTopDown()
            .firstOrNull { it.isFile && it.name.equals("anykernel.sh", ignoreCase = true) }
        if (anyKernelScript == null) {
            onOutput?.invoke("[ABK] AnyKernel3 缺少 anykernel.sh")
            return null
        }

        val original = anyKernelScript.readText()
        val rewritten = rewriteAnyKernelSlotSelect(original, targetSlot)
        if (rewritten == null) {
            return if (targetSlot == Ak3SlotTarget.CURRENT) {
                onOutput?.invoke("[ABK] 未找到 slot_select，沿用 AK3 默认当前槽位行为")
                sourceZip
            } else {
                onOutput?.invoke("[ABK] AnyKernel3 未声明 slot_select，无法切换到另一槽位")
                null
            }
        }
        if (rewritten == original) {
            return sourceZip
        }

        anyKernelScript.writeText(rewritten)
        onOutput?.invoke("[ABK] 已将 AnyKernel3 slot_select 设置为 ${targetSlot.slotSelectValue}")
        val targetZip = File(workDir, "AnyKernel3-target.zip")
        zipDirectory(expandDir, targetZip)
        return targetZip
    }

    private fun unzipToDirectory(zipFile: File, outputDir: File) {
        val outputCanonical = outputDir.canonicalFile
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val outputFile = File(outputDir, entry.name).canonicalFile
                if (!outputFile.path.startsWith(outputCanonical.path + File.separator)) {
                    throw SecurityException("Unsafe zip entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()
                    FileOutputStream(outputFile).use { output ->
                        copyStream(zip, output)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun zipDirectory(sourceDir: File, outputZip: File) {
        val sourceCanonical = sourceDir.canonicalFile
        ZipOutputStream(FileOutputStream(outputZip)).use { zip ->
            sourceDir.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val relativePath = sourceCanonical.toPath().relativize(file.canonicalFile.toPath())
                        .toString()
                        .replace(File.separatorChar, '/')
                    zip.putNextEntry(ZipEntry(relativePath))
                    FileInputStream(file).use { input ->
                        copyStream(input, zip)
                    }
                    zip.closeEntry()
                }
        }
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
        }
    }

    private val AK3_FLASH_SCRIPT = """
#!/system/bin/sh
set -e
echo "[ABK] 开始刷入 AnyKernel3"
echo "[ABK] AK3 包: ${'$'}Z"
echo "[ABK] 工作目录: ${'$'}F"
echo "[ABK] 解包 busybox"
unzip -p "${'$'}Z" 'tools*/busybox' > "${'$'}F/busybox" || { echo "AK3 缺少 busybox"; exit 2; }
echo "[ABK] 解包 update-binary"
unzip -p "${'$'}Z" 'META-INF/com/google/android/update-binary' > "${'$'}F/update-binary" || { echo "AK3 缺少 update-binary"; exit 2; }
chmod 755 "${'$'}F/busybox"
"${'$'}F/busybox" chmod 755 "${'$'}F/update-binary"
"${'$'}F/busybox" chown root:root "${'$'}F/busybox" "${'$'}F/update-binary" 2>/dev/null || true
TMP="${'$'}F/tmp"
echo "[ABK] 准备临时挂载点: ${'$'}TMP"
"${'$'}F/busybox" umount "${'$'}TMP" 2>/dev/null || true
"${'$'}F/busybox" rm -rf "${'$'}TMP" 2>/dev/null || true
"${'$'}F/busybox" mkdir -p "${'$'}TMP"
"${'$'}F/busybox" mount -t tmpfs -o noatime tmpfs "${'$'}TMP"
"${'$'}F/busybox" mount | "${'$'}F/busybox" grep -q " ${'$'}TMP " || exit 1
echo "[ABK] 临时挂载完成，开始执行 AnyKernel3 update-binary"
set +e
AKHOME="${'$'}TMP/anykernel" "${'$'}F/busybox" ash "${'$'}F/update-binary" 3 1 "${'$'}Z"
RC=${'$'}?
set -e
echo "[ABK] AnyKernel3 返回码: ${'$'}RC"
echo "[ABK] 清理临时文件"
"${'$'}F/busybox" umount "${'$'}TMP" 2>/dev/null || true
"${'$'}F/busybox" rm -rf "${'$'}TMP" "${'$'}F/update-binary" "${'$'}F/busybox" 2>/dev/null || true
if [ "${'$'}RC" -eq 0 ]; then
    echo "[ABK] AnyKernel3 刷入完成"
else
    echo "[ABK] AnyKernel3 刷入失败"
fi
exit ${'$'}RC
"""
}
