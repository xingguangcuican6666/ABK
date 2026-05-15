package com.abk.kernel.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Base64
import android.util.Log
import com.abk.kernel.data.model.RootGrantApp
import com.abk.kernel.data.model.RootGrantProfile
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import org.json.JSONObject
import java.io.File
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object RootUtils {

    private const val TAG = "RootUtils"
    private const val FEATURE_SU_COMPAT = "su_compat"
    private const val FEATURE_KERNEL_UMOUNT = "kernel_umount"
    private const val FEATURE_SULOG = "sulog"
    private const val FEATURE_ADB_ROOT = "adb_root"
    private const val FEATURE_SELINUX_HIDE = "selinux_hide"
    private val KSU_FEATURE_NAME_REGEX = Regex("^[a-z0-9_]+$")
    private var appContext: Context? = null

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
        val embeddedKsud = embeddedKsudPath()?.let(::shellQuote) ?: ""
        val script = """
            set -e
            echo "[ABK] 开始安装模块"
            echo "[ABK] 模块路径: $safeZip"
            module_size=${'$'}(wc -c < $safeZip 2>/dev/null || echo 0)
            echo "[ABK] 模块大小: ${'$'}module_size bytes"
            chmod 0644 $safeZip 2>/dev/null || true
            installer=""
            for candidate in $embeddedKsud ${'$'}(command -v ksud 2>/dev/null || true) /data/adb/ksud; do
                [ -n "${'$'}candidate" ] || continue
                [ -x "${'$'}candidate" ] || continue
                installer="${'$'}candidate"
                break
            done
            if [ -n "${'$'}installer" ]; then
                echo "[ABK] 使用 KernelSU 安装模块: ${'$'}installer"
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
        return execRootScript(script, timeoutSeconds = 240, onOutput = onOutput)
    }

    fun installApk(
        context: Context,
        apkPath: String,
        onOutput: ((String) -> Unit)? = null
    ): ShellResult {
        val source = File(apkPath)
        if (!source.isFile) {
            val line = "APK 文件不存在: $apkPath"
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
        onOutput: ((String) -> Unit)? = null
    ): ShellResult {
        val workDir = File(context.filesDir, "ak3-flash").apply {
            deleteRecursively()
            mkdirs()
        }
        val scriptFile = File(workDir, "flash_ak3.sh")
        return try {
            scriptFile.writeText(AK3_FLASH_SCRIPT)
            val script = "F=${shellQuote(workDir.absolutePath)} Z=${shellQuote(zipPath)} /system/bin/sh ${shellQuote(scriptFile.absolutePath)}"
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

    fun resolveUserlandKsudPath(context: Context): String? {
        File(context.applicationInfo.nativeLibraryDir, "libksud.so")
            .takeIf { it.isFile && it.canExecute() }
            ?.let { return it.absolutePath }
        File("/data/adb/ksud")
            .takeIf { it.isFile && it.canExecute() }
            ?.let { return it.absolutePath }
        return runCatching {
            val process = ProcessBuilder("sh", "-c", "command -v ksud 2>/dev/null")
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return@runCatching null
            }
            if (process.exitValue() != 0) return@runCatching null
            process.inputStream.bufferedReader().use { it.readLine()?.trim() }
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

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
            return BootPatchResult(false, listOf("boot 镜像不存在: $bootImagePath"), null)
        }

        val localModule = localModulePath
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
        if (localModule != null && !localModule.isFile) {
            return BootPatchResult(false, listOf("LKM 文件不存在: $localModulePath"), null)
        }

        val asset = if (localModule == null) {
            listBundledAbkLkmAssets(context).firstOrNull {
                it.variantId == variantId && it.kmi == kmi
            } ?: return BootPatchResult(false, listOf("未内置 $variantId / $kmi 的 LKM 模块"), null)
        } else {
            null
        }

        val workDir = File(context.filesDir, "abk-lkm-patch").apply { mkdirs() }
        return runCatching {
            val moduleFile = if (localModule != null) {
                localModule
            } else {
                val bundled = checkNotNull(asset)
                File(workDir, "${bundled.variantId}_${bundled.kmi}_kernelsu.ko").also { target ->
                    context.assets.open(bundled.assetPath).use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    target.setReadable(true, false)
                }
            }

            val outputDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
                "abk-patched"
            ).apply { mkdirs() }
            val moduleName = (asset?.let { "${it.variantId}-${it.kmi}" } ?: moduleFile.nameWithoutExtension)
                .replace(Regex("""[^A-Za-z0-9._-]"""), "_")
            val outputName = "abk-${moduleName}-patched-${System.currentTimeMillis()}.img"
            val outputImage = File(outputDir, outputName)
            val args = buildList {
                add("boot-patch")
                if (sourceBoot != null) {
                    add("--boot")
                    add(sourceBoot.absolutePath)
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
                kmi.takeIf { it.isNotBlank() }?.let {
                    add("--kmi")
                    add(it)
                }
                if (allowShell) add("--allow-shell")
                if (enableAdb) add("--enable-adbd")
            }
            val requiresRootShell = flash || sourceBoot == null
            val userlandKsud = resolveUserlandKsudPath(context)
            val result = when {
                !requiresRootShell && userlandKsud != null -> {
                    onOutput?.invoke("[ABK] 使用用户态 ksud: $userlandKsud")
                    runLocalCommand(listOf(userlandKsud) + args, timeoutSeconds = 300L, onOutput = onOutput)
                }
                allowRootFallback -> {
                    if (requiresRootShell) {
                        onOutput?.invoke("[ABK] 通过 Root shell 执行 ksud boot-patch")
                    } else {
                        onOutput?.invoke("[ABK] 未找到用户态 ksud，尝试通过 Root shell 使用系统 ksud")
                    }
                    val command = args.joinToString(" ") { shellQuote(it) }
                    execRootScript(
                        withManagerShellHelpers(
                            """
                                set -e
                                ksud_path=${'$'}(abk_find_ksud)
                                [ -n "${'$'}ksud_path" ] || { echo "未找到 ksud"; exit 127; }
                                "${'$'}ksud_path" $command
                            """.trimIndent()
                        ),
                        timeoutSeconds = 300L,
                        onOutput = onOutput
                    )
                }
                requiresRootShell -> ShellResult(false, listOf("该安装方式需要 Root 权限。"))
                else -> ShellResult(
                    false,
                    listOf("未找到可执行 ksud；无 Root 时需要 APK 内置或系统可直接执行的 ksud 才能仅修补 boot。")
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
        val status = AbkKsuNative.controlStatus()
        return if (status != null) {
            ShellResult(true, listOf(status))
        } else {
            ShellResult(false, listOf("未激活"))
        }
    }

    fun readManagerRuntimeSnapshot(): ManagerRuntimeSnapshot {
        val manager = detectManagerRuntime()
        if (!manager.active) {
            return ManagerRuntimeSnapshot(manager = manager)
        }

        val control = readAbkControlStatus().takeIf { it.success }
            ?.output
            ?.joinToString("\n")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it.startsWith("{") }

        val modules = listKsuModules().takeIf { it.success }
            ?.output
            ?.joinToString("\n")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it.startsWith("[") }

        if (manager.backend == "su" && control == null && modules == null) {
            return ManagerRuntimeSnapshot(
                manager = manager.copy(
                    active = false,
                    diagnostics = (manager.diagnostics + "仅检测到通用 su shell，未检测到可用于 ABK 运行态管理的 KernelSU/ReSukiSU 控制接口。").distinct()
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
        if (!AbkKsuNative.isUsableManager()) return emptyList()
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
        if (!AbkKsuNative.isUsableManager()) return false
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
            0 -> setKsuFeatureValue(FEATURE_SU_COMPAT, 1L, persist = true)
            1 -> {
                val persistedEnabled = setKsuFeatureValue(FEATURE_SU_COMPAT, 1L, persist = true)
                if (!persistedEnabled.success) {
                    persistedEnabled
                } else {
                    mergeShellResults(
                        persistedEnabled,
                        setKsuFeatureValue(FEATURE_SU_COMPAT, 0L, persist = false)
                    )
                }
            }
            2 -> setKsuFeatureValue(FEATURE_SU_COMPAT, 0L, persist = true)
            else -> ShellResult(false, listOf("未知 su 兼容模式"))
        }
    }

    fun setKsuFeatureEnabled(featureName: String, enabled: Boolean): ShellResult {
        val feature = normalizeKsuFeatureName(featureName)
            ?: return ShellResult(false, listOf("未知 Feature"))
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
        } else {
            setKsuFeatureValue(feature, value, persist = true)
        }
    }

    fun setReSukiSuFeatureEnabled(featureName: String, enabled: Boolean): ShellResult =
        setKsuFeatureEnabled(featureName, enabled)

    fun isDefaultUmountModules(): Boolean {
        return AbkKsuNative.isDefaultUmountModules() ?: false
    }

    fun setDefaultUmountModules(enabled: Boolean): Boolean {
        return AbkKsuNative.setDefaultUmountModules(enabled)
    }

    fun listAppProfileTemplates(): ShellResult {
        return runKsudCommand("profile list-templates", timeoutSeconds = 30L)
    }

    fun readAppProfileTemplate(id: String): ShellResult {
        if (!isSafeTemplateId(id)) {
            return ShellResult(false, listOf("模板名称无效"))
        }
        return runKsudCommand("profile get-template ${shellQuote(id)}", timeoutSeconds = 30L)
    }

    fun writeAppProfileTemplate(id: String, content: String): ShellResult {
        if (!isSafeTemplateId(id)) {
            return ShellResult(false, listOf("模板名称无效"))
        }
        return runKsudCommand(
            "profile set-template ${shellQuote(id)} ${shellQuote(content)}",
            timeoutSeconds = 30L
        )
    }

    fun deleteAppProfileTemplate(id: String): ShellResult {
        if (!isSafeTemplateId(id)) {
            return ShellResult(false, listOf("模板名称无效"))
        }
        return runKsudCommand("profile delete-template ${shellQuote(id)}", timeoutSeconds = 30L)
    }

    fun listKsuModules(): ShellResult {
        val script = """
            set -e
            ksud_path=${'$'}(abk_find_ksud)
            [ -n "${'$'}ksud_path" ] || exit 127
            "${'$'}ksud_path" module list
        """.trimIndent()
        return execRootScript(withManagerShellHelpers(script), timeoutSeconds = 30L)
    }

    fun runKsuModuleAction(moduleId: String, onOutput: ((String) -> Unit)? = null): ShellResult {
        val safeId = shellQuote(moduleId.trim())
        val script = """
            set -e
            ksud_path=${'$'}(abk_find_ksud)
            [ -n "${'$'}ksud_path" ] || exit 127
            "${'$'}ksud_path" module action $safeId
        """.trimIndent()
        return execRootScript(withManagerShellHelpers(script), timeoutSeconds = 300L, onOutput = onOutput)
    }

    fun setKsuModuleEnabled(moduleId: String, enabled: Boolean): ShellResult {
        val safeId = shellQuote(moduleId.trim())
        val verb = if (enabled) "enable" else "disable"
        val script = """
            set -e
            ksud_path=${'$'}(abk_find_ksud)
            [ -n "${'$'}ksud_path" ] || exit 127
            "${'$'}ksud_path" module $verb $safeId
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
            "${'$'}ksud_path" module $verb $safeId
        """.trimIndent()
        return execRootScript(withManagerShellHelpers(script), timeoutSeconds = 30L)
    }

    fun listKpmModules(): ShellResult {
        val script = """
            set -e
            ksud_path=${'$'}(abk_find_ksud)
            [ -n "${'$'}ksud_path" ] || exit 127
            "${'$'}ksud_path" kpm list
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
            "${'$'}ksud_path" kpm info $safeName
        """.trimIndent()
        return execRootScript(withManagerShellHelpers(script), timeoutSeconds = 30L)
    }

    fun writeAbkControlCommand(command: String): ShellResult {
        return if (AbkKsuNative.controlCommand(command)) {
            ShellResult(true, emptyList())
        } else {
            ShellResult(false, listOf("未激活"))
        }
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

        val filePath = "/data/adb/modules/$cleanId/webroot/$cleanRelativePath"
        return try {
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
    }

    fun moduleInfoJson(moduleId: String): String {
        val cleanId = moduleId.trim()
        if (cleanId.isBlank()) return "{}"
        val moduleDir = "/data/adb/modules/$cleanId"
        val modules = listKsuModules().takeIf { it.success }?.output?.joinToString("\n").orEmpty()
        val moduleJson = runCatching {
            val array = org.json.JSONArray(modules.ifBlank { "[]" })
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                if (item.optString("id") == cleanId) {
                    item.put("moduleDir", moduleDir)
                    return@runCatching item.toString()
                }
            }
            org.json.JSONObject()
                .put("id", cleanId)
                .put("moduleDir", moduleDir)
                .toString()
        }.getOrDefault("{}")
        return moduleJson
    }

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
            "${'$'}ksud_path" profile set-sepolicy $safePackage $safeRules
        """.trimIndent()
        return execRootScript(withManagerShellHelpers(script), timeoutSeconds = 30L).success
    }

    data class ShellResult(val success: Boolean, val output: List<String>)

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
        val capabilities: List<String> = emptyList(),
        val diagnostics: List<String> = emptyList()
    )

    private fun runKsudCommand(args: String, timeoutSeconds: Long): ShellResult {
        val cleanArgs = args.trim()
        if (cleanArgs.isBlank()) return ShellResult(false, listOf("ksud 参数为空"))
        val script = """
            set -e
            ksud_path=${'$'}(abk_find_ksud)
            [ -n "${'$'}ksud_path" ] || exit 127
            "${'$'}ksud_path" $cleanArgs
        """.trimIndent()
        return execRootScript(withManagerShellHelpers(script), timeoutSeconds = timeoutSeconds)
    }

    private fun getKsuFeatureSupport(featureName: String): KsuFeatureSupport? {
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
        val result = runKsudCommand("feature get ${shellQuote(featureName)}", timeoutSeconds = 15L)
        if (!result.success) return null
        return parseKsuFeatureValue(result.output)
    }

    private fun getKsuFeatureConfigValue(featureName: String): Long? {
        val result = runKsudCommand("feature get ${shellQuote(featureName)} --config", timeoutSeconds = 15L)
        if (!result.success) return null
        return parseKsuFeatureValue(result.output)
    }

    private fun setKsuFeatureValue(featureName: String, value: Long, persist: Boolean): ShellResult {
        val setResult = runKsudCommand(
            "feature set ${shellQuote(featureName)} $value",
            timeoutSeconds = 30L
        )
        if (!setResult.success || !persist) return setResult
        return mergeShellResults(setResult, saveKsuFeatureConfig())
    }

    private fun saveKsuFeatureConfig(): ShellResult =
        runKsudCommand("feature save", timeoutSeconds = 30L)

    private fun readNativeFeature(featureName: String): KsuFeatureState? {
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
                val line = "命令超时"
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
            val line = "管理器未激活"
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

    private fun detectManagerRuntime(): ManagerRuntimeProbe {
        detectNativeManagerRuntime()?.let { return it }

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
                        "$safeKsud --version 2>/dev/null || true",
                        normalizeOutput = false
                    )
                        .output
                        .firstOrNull()
                        ?.trim()
                        .orEmpty()
                    val capabilityOutput = execWithShell(
                        shell,
                        """
                        caps="root_shell modules"
                        $safeKsud module list >/dev/null 2>&1 && caps="${'$'}caps module_control"
                        $safeKsud susfs status >/dev/null 2>&1 && caps="${'$'}caps susfs"
                        $safeKsud kpm version >/dev/null 2>&1 && caps="${'$'}caps kpm"
                        $safeKsud feature check su_compat >/dev/null 2>&1 && caps="${'$'}caps features"
                        printf '%s\n' "${'$'}caps"
                        """.trimIndent(),
                        normalizeOutput = false
                    ).output.firstOrNull().orEmpty()
                    val capabilities = capabilityOutput
                        .split(' ', '\n', '\t')
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                    val variant = inferManagerVariant(version)
                    ManagerRuntimeProbe(
                        active = true,
                        displayName = variant.ifBlank { "KernelSU" },
                        variant = variant.ifBlank { "KernelSU" },
                        backend = "ksud",
                        version = version,
                        capabilities = capabilities.ifEmpty { listOf("root_shell", "modules") },
                        diagnostics = listOf(
                            "当前仅通过 ksud/root shell 兼容层工作，ABK 尚未被内核识别为原生管理器，无法管理 Root 授权策略。"
                        )
                    )
                } else {
                    ManagerRuntimeProbe(
                        active = true,
                        displayName = "Root",
                        variant = "Generic",
                        backend = "su",
                        capabilities = listOf("root_shell"),
                        diagnostics = listOf(
                            "当前仅有通用 su shell 可用，未检测到 KernelSU/ReSukiSU 原生管理器接口。"
                        )
                    )
                }
            }
        } catch (error: Throwable) {
            ManagerRuntimeProbe(
                diagnostics = listOf("未检测到可用的 KernelSU/ReSukiSU 管理器接口或 Root shell。")
            )
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
                capabilities = listOf("native_kernel"),
                diagnostics = listOf(
                    "KernelSU/ReSukiSU native 接口可访问，但当前 ABK APK 未被识别为管理器。请确认安装的是与内核构建时 ABK_MANAGER_CERT_SHA256 匹配的 com.abk.kernel 正式签名 APK。"
                )
            )
        }
        val controlJson = AbkKsuNative.controlStatus()
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
            if (controlJson == null) {
                add("ABK control 未响应；内核可能没有启用 CONFIG_ABK_CONTROL，或 ABK Control 外部模块缺少 before_build 阶段。")
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
        embeddedKsudPath()?.let { path ->
            if (globalMount) {
                candidates += arrayOf(path, "debug", "su", "-g")
            } else {
                candidates += arrayOf(path, "debug", "su")
            }
        }
        if (globalMount) {
            candidates += arrayOf("ksud", "debug", "su", "-g")
            candidates += arrayOf("/data/adb/ksud", "debug", "su", "-g")
            candidates += arrayOf("su", "-mm")
        } else {
            candidates += arrayOf("ksud", "debug", "su")
            candidates += arrayOf("/data/adb/ksud", "debug", "su")
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

    private fun embeddedKsudPath(): String? {
        val context = appContext ?: return null
        return File(context.applicationInfo.nativeLibraryDir, "libksud.so")
            .takeIf { it.isFile && it.canExecute() }
            ?.absolutePath
    }

    private fun withManagerShellHelpers(script: String): String {
        val embedded = embeddedKsudPath()?.let(::shellQuote).orEmpty()
        return """
            abk_find_ksud() {
                for candidate in $embedded ${'$'}(command -v ksud 2>/dev/null || true) /data/adb/ksud; do
                    [ -n "${'$'}candidate" ] || continue
                    [ -x "${'$'}candidate" ] || continue
                    printf '%s\n' "${'$'}candidate"
                    return 0
                done
                return 1
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
            "[ABK] Root 命令执行完成，但命令未返回输出。"
        } else {
            "[ABK] Root 命令执行失败，但命令未返回输出。"
        }
        onOutput?.invoke(fallback)
        return listOf(fallback)
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

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
