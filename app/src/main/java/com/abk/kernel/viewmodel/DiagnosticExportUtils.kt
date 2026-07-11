package com.abk.kernel.viewmodel

import android.content.Context
import android.os.Build
import android.os.Process
import com.abk.kernel.AbkApplication
import com.abk.kernel.BuildConfig
import com.abk.kernel.data.model.AbkRuntimeModule
import com.abk.kernel.data.model.AbkRuntimeStatus
import com.abk.kernel.utils.RootUtils
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DiagnosticExportResult(
    val zipFile: File,
    val warnings: List<String>
)

internal suspend fun exportDiagnosticBundle(
    context: Context,
    state: MainUiState
): DiagnosticExportResult = withContext(Dispatchers.IO) {
    val gson = GsonBuilder().setPrettyPrinting().create()
    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    val exportRoot = File(context.cacheDir, "diagnostics").apply { mkdirs() }
    cleanupPreviousDiagnosticExports(exportRoot)

    val stagingDir = File(exportRoot, "abk-diagnostics-$timestamp").apply { mkdirs() }
    val rawDir = File(stagingDir, "raw").apply { mkdirs() }
    val logsDir = File(stagingDir, "logs").apply { mkdirs() }
    val runtimeDir = File(stagingDir, "runtime").apply { mkdirs() }
    val systemDir = File(stagingDir, "system").apply { mkdirs() }
    val warnings = mutableListOf<String>()

    writeText(
        File(stagingDir, "README.txt"),
        buildString {
            appendLine("ABK diagnostic export")
            appendLine("Generated: $timestamp")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Package: ${BuildConfig.APPLICATION_ID}")
        }
    )

    writeText(
        File(systemDir, "session.txt"),
        buildString {
            appendLine("generated_at_epoch_ms=${System.currentTimeMillis()}")
            appendLine("process_start_wall_clock_ms=${AbkApplication.processStartWallClockMs}")
            appendLine("process_start_elapsed_realtime_ms=${AbkApplication.processStartElapsedRealtimeMs}")
            appendLine("process_start_pid=${AbkApplication.processStartPid}")
            appendLine("current_pid=${Process.myPid()}")
            appendLine("root_granted=${state.rootGranted}")
            appendLine("manager_access_state=${state.managerAccessState}")
            appendLine("runtime_navigation_enabled=${state.runtimeNavigationEnabled}")
            appendLine("brand=${Build.BRAND}")
            appendLine("manufacturer=${Build.MANUFACTURER}")
            appendLine("device=${Build.DEVICE}")
            appendLine("model=${Build.MODEL}")
            appendLine("product=${Build.PRODUCT}")
            appendLine("sdk_int=${Build.VERSION.SDK_INT}")
            appendLine("release=${Build.VERSION.RELEASE}")
            appendLine("fingerprint=${Build.FINGERPRINT}")
        }
    )

    writeText(
        File(systemDir, "ui-state-summary.txt"),
        buildString {
            appendLine("rootGranted=${state.rootGranted}")
            appendLine("abkRuntimeLoading=${state.abkRuntimeLoading}")
            appendLine("abkRuntimeError=${state.abkRuntimeError.orEmpty()}")
            appendLine("managerAccessError=${state.managerAccessError.orEmpty()}")
            appendLine("runtimeModuleRepositories=${state.runtimeModuleRepositories.size}")
            appendLine("downloadedArtifacts=${state.downloadedArtifacts.size}")
            appendLine("rootGrantApps=${state.rootGrantApps.size}")
            appendLine("appUpdateStability=${state.appUpdateStability}")
            appendLine("appUpdateLine=${state.appUpdateLine}")
        }
    )

    runLocalCommand(
        listOf(
            "logcat",
            "-d",
            "--pid=${Process.myPid()}",
            "-v",
            "threadtime"
        )
    ).let { result ->
        writeText(File(logsDir, "abk-logcat-current-process.txt"), result.output)
        if (!result.success) {
            warnings += "Failed to capture current-process logcat"
        }
    }

    val snapshot = if (state.rootGranted) {
        runCatching { RootUtils.readManagerRuntimeSnapshot() }
            .onFailure { warnings += "Failed to read manager runtime snapshot: ${it.message.orEmpty()}" }
            .getOrNull()
    } else {
        warnings += "Root not granted; kernel logs and module files may be incomplete"
        null
    }

    writeText(
        File(runtimeDir, "manager-runtime-probe.json"),
        gson.toJson(snapshot?.manager ?: RootUtils.resolveManagerAccess(state.rootGranted).runtime)
    )

    snapshot?.controlStatusJson?.let {
        writePrettyJson(File(rawDir, "abk-control-status.json"), it, gson)
    } ?: run {
        warnings += "ABK control status JSON unavailable"
    }

    snapshot?.ksuModulesJson?.let {
        writePrettyJson(File(rawDir, "ksu-modules.json"), it, gson)
    } ?: run {
        warnings += "KSU module list JSON unavailable"
    }

    val runtimeStatus = state.abkRuntimeStatus
        ?: snapshot?.takeIf { it.manager.active }?.let {
            mergeRuntimeStatus(
                gson = Gson(),
                ksuModuleListType = object : TypeToken<List<Map<String, Any?>>>() {}.type,
                manager = it.manager,
                controlJson = it.controlStatusJson,
                ksuModulesJson = it.ksuModulesJson
            )
        }

    runtimeStatus?.let {
        writeText(File(runtimeDir, "runtime-status.json"), gson.toJson(it))
        writeRuntimeModuleSummary(File(runtimeDir, "abk-runtime-modules.tsv"), it.modules)
        writeRuntimeModuleSummary(
            File(runtimeDir, "standard-runtime-modules.tsv"),
            it.modules.filter { module -> module.isKsuBacked() }
        )
        writeRuntimeModuleSummary(
            File(runtimeDir, "nonstandard-runtime-modules.tsv"),
            it.modules.filterNot { module -> module.isKsuBacked() }
        )
    } ?: run {
        warnings += "Merged runtime status unavailable"
    }

    val rootCollection = snapshot?.let {
        collectRootDiagnostics(stagingDir)
    }
    if (rootCollection != null && !rootCollection.success) {
        warnings += rootCollection.output.ifEmpty { listOf("Root diagnostic collection failed") }
    }

    if (warnings.isNotEmpty()) {
        writeText(File(stagingDir, "warnings.txt"), warnings.joinToString(separator = "\n"))
    }

    val zipFile = File(exportRoot, "abk-diagnostics-$timestamp.zip")
    zipDirectory(stagingDir, zipFile)
    stagingDir.deleteRecursively()
    DiagnosticExportResult(zipFile = zipFile, warnings = warnings.toList())
}

private data class LocalCommandResult(
    val success: Boolean,
    val output: String
)

private fun runLocalCommand(command: List<String>): LocalCommandResult {
    return try {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        LocalCommandResult(process.waitFor() == 0, output)
    } catch (error: Throwable) {
        LocalCommandResult(false, error.stackTraceToString())
    }
}

private fun collectRootDiagnostics(stagingDir: File): RootUtils.ShellResult {
    val outPath = shellQuote(stagingDir.absolutePath)
    val keywordPattern = "ksu|ksud|kernelsu|sukisu|resukisu|susfs|sulog|abk"
    val script = """
        set +e
        OUT=$outPath
        ROOT_DIR="${'$'}OUT/root"
        LOG_DIR="${'$'}ROOT_DIR/logs"
        MODULE_DIR="${'$'}ROOT_DIR/modules"
        mkdir -p "${'$'}LOG_DIR" "${'$'}MODULE_DIR"

        dmesg > "${'$'}LOG_DIR/kernel-dmesg.txt" 2>&1
        dmesg | grep -i -E '${keywordPattern}' > "${'$'}LOG_DIR/manager-keywords-dmesg.txt" 2>&1 || true
        logcat -d -v threadtime > "${'$'}LOG_DIR/logcat-full.txt" 2>&1 || true
        logcat -d -v threadtime | grep -i -E '${keywordPattern}' > "${'$'}LOG_DIR/manager-keywords-logcat.txt" 2>&1 || true
        getprop > "${'$'}ROOT_DIR/getprop.txt" 2>&1 || true
        cat /proc/version > "${'$'}ROOT_DIR/proc-version.txt" 2>&1 || true
        ls -la /data/adb > "${'$'}ROOT_DIR/data-adb-ls.txt" 2>&1 || true

        {
          printf 'id\tenabled\tupdate\tremove\tmodule_dir\tname\tversion\n'
          for mod in /data/adb/modules/*; do
            [ -d "${'$'}mod" ] || continue
            id=${'$'}(basename "${'$'}mod")
            enabled=1
            [ -f "${'$'}mod/disable" ] && enabled=0
            update=0
            [ -f "${'$'}mod/update" ] && update=1
            remove=0
            [ -f "${'$'}mod/remove" ] && remove=1
            name=""
            version=""
            if [ -f "${'$'}mod/module.prop" ]; then
              name=${'$'}(grep -m1 '^name=' "${'$'}mod/module.prop" | cut -d= -f2-)
              version=${'$'}(grep -m1 '^version=' "${'$'}mod/module.prop" | cut -d= -f2-)
            fi
            printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "${'$'}id" "${'$'}enabled" "${'$'}update" "${'$'}remove" "${'$'}mod" "${'$'}name" "${'$'}version"
          done
        } > "${'$'}ROOT_DIR/standard-modules-summary.tsv"

        for mod in /data/adb/modules/*; do
          [ -d "${'$'}mod" ] || continue
          id=${'$'}(basename "${'$'}mod")
          dest="${'$'}MODULE_DIR/${'$'}id"
          mkdir -p "${'$'}dest"
          for file in module.prop disable update remove service.sh post-fs-data.sh action.sh sepolicy.rule system.prop; do
            if [ -f "${'$'}mod/${'$'}file" ]; then
              cp -f "${'$'}mod/${'$'}file" "${'$'}dest/${'$'}file" 2>/dev/null || cat "${'$'}mod/${'$'}file" > "${'$'}dest/${'$'}file"
            fi
          done
          find "${'$'}mod" -maxdepth 3 -type f \( -name '*.log' -o -name 'log*' -o -path '*/logs/*' -o -path '*/log/*' \) 2>/dev/null | head -n 24 | while IFS= read -r file; do
            rel=${'$'}{file#"${'$'}mod/"}
            target="${'$'}dest/${'$'}rel"
            mkdir -p "${'$'}(dirname "${'$'}target")"
            bytes=${'$'}(wc -c < "${'$'}file" 2>/dev/null || echo 0)
            if [ "${'$'}bytes" -gt 1048576 ]; then
              tail -c 1048576 "${'$'}file" > "${'$'}target" 2>/dev/null || true
            else
              cat "${'$'}file" > "${'$'}target" 2>/dev/null || true
            fi
          done
        done

        chmod -R a+rX "${'$'}ROOT_DIR" 2>/dev/null || true
    """.trimIndent()
    return RootUtils.execRootCommandForWebUi(script, timeoutSeconds = 240L)
}

private fun writeRuntimeModuleSummary(
    file: File,
    modules: List<AbkRuntimeModule>
) {
    if (modules.isEmpty()) {
        writeText(file, "")
        return
    }
    val body = buildString {
        appendLine("id\tname\ttype\tsource\tenabled\tupdate\tremove\treadonly\tcontrollable\tmodule_dir")
        modules.forEach { module ->
            appendLine(
                listOf(
                    module.id,
                    module.name,
                    module.type,
                    module.source,
                    module.enabled.toString(),
                    module.update.toString(),
                    module.remove.toString(),
                    module.readonly.toString(),
                    module.controllable.toString(),
                    module.moduleDir
                ).joinToString("\t")
            )
        }
    }
    writeText(file, body)
}

private fun zipDirectory(sourceDir: File, zipFile: File) {
    ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
        sourceDir.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val entryName = file.relativeTo(sourceDir).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
    }
}

private fun writePrettyJson(target: File, rawJson: String, gson: Gson) {
    val parsed = runCatching { gson.fromJson(rawJson, Any::class.java) }.getOrNull()
    if (parsed != null) {
        writeText(target, gson.toJson(parsed))
    } else {
        writeText(target, rawJson)
    }
}

private fun writeText(target: File, text: String) {
    target.parentFile?.mkdirs()
    target.writeText(text)
}

private fun cleanupPreviousDiagnosticExports(exportRoot: File) {
    exportRoot.listFiles().orEmpty().forEach { child ->
        if (child.name.startsWith("abk-diagnostics-")) {
            child.deleteRecursively()
        }
    }
}

private fun shellQuote(value: String): String =
    "'${value.replace("'", "'\"'\"'")}'"
