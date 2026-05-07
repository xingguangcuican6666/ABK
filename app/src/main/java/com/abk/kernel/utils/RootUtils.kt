package com.abk.kernel.utils

import android.content.Context
import com.topjohnwu.superuser.Shell
import java.io.File

object RootUtils {

    fun init() {
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )
    }

    fun isRootAvailable(): Boolean {
        return Shell.isAppGrantedRoot() == true
    }

    fun requestRoot(): Boolean {
        return try {
            Shell.getShell() // triggers root request
            Shell.isAppGrantedRoot() == true
        } catch (e: Exception) {
            false
        }
    }

    fun flashImage(imagePath: String, partition: String = "boot"): ShellResult {
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
        return execRootScript(script, timeoutSeconds = 240)
    }

    fun installModule(zipPath: String): ShellResult {
        val safeZip = shellQuote(zipPath)
        val script = """
            set -e
            echo "[ABK] 开始安装模块"
            echo "[ABK] 模块路径: $safeZip"
            module_size=${'$'}(wc -c < $safeZip 2>/dev/null || echo 0)
            echo "[ABK] 模块大小: ${'$'}module_size bytes"
            chmod 0644 $safeZip 2>/dev/null || true
            if command -v ksud >/dev/null 2>&1; then
                installer=${'$'}(command -v ksud)
                echo "[ABK] 使用 KernelSU 安装模块: ${'$'}installer"
                if ksud module install $safeZip; then
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
        return execRootScript(script, timeoutSeconds = 240)
    }

    fun flashAnyKernel3(context: Context, zipPath: String): ShellResult {
        val workDir = File(context.filesDir, "ak3-flash").apply {
            deleteRecursively()
            mkdirs()
        }
        val scriptFile = File(workDir, "flash_ak3.sh")
        return try {
            scriptFile.writeText(AK3_FLASH_SCRIPT)
            val script = "F=${shellQuote(workDir.absolutePath)} Z=${shellQuote(zipPath)} /system/bin/sh ${shellQuote(scriptFile.absolutePath)}"
            val result = Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER or Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(300L)
                .build()
                .newJob()
                .add(script)
                .exec()
            ShellResult(result.isSuccess, result.out + result.err)
        } finally {
            workDir.deleteRecursively()
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
        val result = Shell.cmd("ksud --version 2>/dev/null || echo N/A").exec()
        return result.out.firstOrNull() ?: "N/A"
    }

    fun getRootMode(): String {
        val result = Shell.cmd(
            "command -v ksud >/dev/null 2>&1 && echo KernelSU || " +
                "command -v magisk >/dev/null 2>&1 && echo Magisk || " +
                "command -v apd >/dev/null 2>&1 && echo APatch || echo Unknown"
        ).exec()
        return result.out.firstOrNull() ?: "Unknown"
    }

    fun getSusfsVersion(): String {
        val result = Shell.cmd("cat /proc/self/attr/prev 2>/dev/null | head -1 || echo N/A").exec()
        return result.out.firstOrNull() ?: "N/A"
    }

    fun reboot(): ShellResult = execRootScript("svc power reboot || reboot", timeoutSeconds = 15L)

    data class ShellResult(val success: Boolean, val output: List<String>)

    private fun execRootScript(script: String, timeoutSeconds: Long): ShellResult {
        val result = Shell.Builder.create()
            .setFlags(Shell.FLAG_MOUNT_MASTER or Shell.FLAG_REDIRECT_STDERR)
            .setTimeout(timeoutSeconds)
            .build()
            .newJob()
            .add(script)
            .exec()
        val output = result.out + result.err
        return ShellResult(
            result.isSuccess,
            output.ifEmpty {
                listOf(
                    if (result.isSuccess) "[ABK] Root 命令执行完成，但命令未返回输出。"
                    else "[ABK] Root 命令执行失败，但命令未返回输出。"
                )
            }
        )
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
