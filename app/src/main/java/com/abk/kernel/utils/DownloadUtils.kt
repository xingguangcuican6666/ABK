package com.abk.kernel.utils

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import com.abk.kernel.data.model.Artifact
import com.abk.kernel.data.model.ArtifactCategory
import com.abk.kernel.data.model.ArtifactType
import com.abk.kernel.data.model.BuildArtifact
import com.abk.kernel.data.model.DownloadedArtifact
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.data.model.toArtifact
import com.abk.kernel.data.model.toArtifactCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object DownloadUtils {

    private val client = OkHttpClient.Builder().build()

    fun classifyArtifact(name: String): ArtifactType {
        val lower = name.lowercase()
        return when {
            lower.contains("reject") || lower.contains("-rej") -> ArtifactType.OTHER
            lower.contains("_kernel-android") || lower.contains("kernel-android") -> ArtifactType.KERNEL_PACKAGE
            lower.endsWith(".img") && (lower.contains("boot") || lower.contains("kernel")) -> ArtifactType.KERNEL_IMG
            lower.contains("boot-img") || lower.contains("boot_img") || lower.contains("kernel-img") -> ArtifactType.KERNEL_IMG
            lower.contains("anykernel") || lower.contains("ak3") -> ArtifactType.ANYKERNEL3
            lower.endsWith(".zip") && isLikelyModuleZipName(lower) -> ArtifactType.SUSFS_MODULE
            isLikelyModuleZipName(lower) && !lower.contains("anykernel") -> ArtifactType.SUSFS_MODULE
            lower.endsWith(".apk") && (
                lower.contains("manager") ||
                    lower.contains("kernelsu") ||
                    lower.contains("ksu") ||
                    lower.contains("suki")
                ) -> ArtifactType.KSU_MANAGER
            lower.contains("manager") && (
                lower.contains("kernelsu") ||
                    lower.contains("ksu") ||
                    lower.contains("suki")
                ) -> ArtifactType.KSU_MANAGER
            lower.contains("sukisu-ultra") || lower.contains("sukisu_ultra") -> ArtifactType.KSU_MANAGER
            else -> ArtifactType.OTHER
        }
    }

    private fun isLikelyModuleZipName(lower: String): Boolean =
        lower.contains("susfs") ||
            lower.contains("module") ||
            lower.contains("magisk") ||
            lower.contains("zygisk") ||
            lower.contains("kpm")

    fun classifyCategory(type: ArtifactType): ArtifactCategory? = when (type) {
        ArtifactType.KERNEL_PACKAGE,
        ArtifactType.KERNEL_IMG,
        ArtifactType.ANYKERNEL3 -> ArtifactCategory.KERNEL
        ArtifactType.KSU_MANAGER -> ArtifactCategory.MANAGER
        ArtifactType.SUSFS_MODULE -> ArtifactCategory.MODULE
        ArtifactType.OTHER -> null
    }

    fun shouldAutoDownload(artifact: Artifact): Boolean {
        val lower = artifact.name.lowercase()
        val type = classifyArtifact(artifact.name)
        return when (classifyCategory(type)) {
            ArtifactCategory.KERNEL,
            ArtifactCategory.MODULE -> true
            ArtifactCategory.MANAGER -> isLikelySupportedManager(lower)
            null -> false
        }
    }

    fun shouldAutoDownload(artifact: BuildArtifact): Boolean = shouldAutoDownload(artifact.toArtifact())

    fun matchesDownloadedArtifact(downloaded: DownloadedArtifact, artifact: BuildArtifact): Boolean =
        downloaded.runId == artifact.runId &&
            downloaded.filePath.contains("/${artifactStorageFolderName(artifact.name)}/")

    fun artifactStorageFolderName(name: String): String = safeFileName(name)

    private fun isLikelySupportedManager(name: String): Boolean {
        val abi = android.os.Build.SUPPORTED_ABIS.joinToString(" ").lowercase(Locale.ROOT)
        val sdk = android.os.Build.VERSION.SDK_INT
        if (name.contains("armeabi-v7a") && !abi.contains("armeabi-v7a")) return false
        if ((name.contains("arm64") || name.contains("aarch64")) && !abi.contains("arm64")) return false
        if ((name.contains("x86_64") || name.contains("x64")) && !abi.contains("x86_64")) return false
        if (Regex("""(?:api|sdk|min)[-_ ]?(\d{2})""").find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { sdk < it } == true) {
            return false
        }
        return true
    }

    suspend fun downloadArtifact(
        context: Context,
        token: String?,
        artifact: Artifact,
        run: WorkflowRun? = null,
        downloadUrl: String? = null,
        onProgress: (Int) -> Unit = {}
    ): List<DownloadedArtifact> = withContext(Dispatchers.IO) {
        try {
            val url = downloadUrl ?: artifact.archiveDownloadUrl
            val request = Request.Builder()
                .url(url)
                .header("Accept", if (downloadUrl == null) "application/vnd.github+json" else "application/octet-stream")
                .apply {
                    if (downloadUrl == null && !token.isNullOrBlank()) {
                        header("Authorization", "Bearer $token")
                    }
                }
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body ?: return@withContext emptyList()
            val totalBytes = artifact.sizeInBytes.coerceAtLeast(1L)

            val downloadsRoot = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            val runDir = File(downloadsRoot, runFolderName(run)).apply { mkdirs() }
            val zipFile = File(runDir, "${artifact.name}.zip")

            body.byteStream().use { input ->
                FileOutputStream(zipFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var downloaded = 0L
                    var bytes: Int
                    while (input.read(buffer).also { bytes = it } != -1) {
                        output.write(buffer, 0, bytes)
                        downloaded += bytes
                        val pct = (downloaded * 100 / totalBytes).toInt().coerceIn(0, 100)
                        onProgress(pct)
                    }
                }
            }

            // Unzip into named folder
            val outDir = File(runDir, safeFileName(artifact.name))
            if (outDir.exists()) outDir.deleteRecursively()
            outDir.mkdirs()
            unzip(zipFile, outDir)
            zipFile.delete()

            collectCandidateFiles(outDir).mapIndexed { index, file ->
                val type = classifyDownloadedFile(file)
                DownloadedArtifact(
                    id = artifact.id * 1000 + index + 1,
                    name = file.name,
                    filePath = file.absolutePath,
                    type = type,
                    sizeBytes = file.length(),
                    runId = run?.id ?: -1L,
                    runTitle = run?.displayTitle ?: run?.name ?: run?.let { "#${it.runNumber}" } ?: "未关联工作流",
                    runNumber = run?.runNumber ?: 0,
                    category = type.toArtifactCategory()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun runFolderName(run: WorkflowRun?): String {
        if (run == null) return "manual"
        val title = run.displayTitle ?: run.name ?: "workflow"
        return "run-${run.runNumber}-${safeFileName(title).take(48)}"
    }

    private fun safeFileName(value: String): String =
        value.replace(Regex("""[^A-Za-z0-9._ -]"""), "_")
            .trim()
            .ifBlank { "artifact" }

    private fun unzip(zipFile: File, outDir: File) {
        val outCanonical = outDir.canonicalFile
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(outDir, entry.name).canonicalFile
                if (!file.path.startsWith(outCanonical.path + File.separator)) {
                    throw SecurityException("Unsafe zip entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun collectCandidateFiles(outDir: File): List<File> {
        val files = outDir.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") }
            .toList()

        val candidates = files.filter { file ->
            when (classifyDownloadedFile(file)) {
                ArtifactType.KERNEL_PACKAGE,
                ArtifactType.KERNEL_IMG,
                ArtifactType.ANYKERNEL3,
                ArtifactType.KSU_MANAGER,
                ArtifactType.SUSFS_MODULE -> true
                ArtifactType.OTHER -> false
            }
        }

        val source = candidates.ifEmpty { files }
        return source.sortedWith(
            compareBy<File> {
                when (classifyDownloadedFile(it)) {
                    ArtifactType.KERNEL_PACKAGE -> 0
                    ArtifactType.KERNEL_IMG -> 1
                    ArtifactType.ANYKERNEL3 -> 2
                    ArtifactType.KSU_MANAGER -> 3
                    ArtifactType.SUSFS_MODULE -> 4
                    ArtifactType.OTHER -> 5
                }
            }.thenBy { it.name }
        )
    }

    private fun classifyDownloadedFile(file: File): ArtifactType {
        val byName = classifyArtifact(file.name)
        if (byName != ArtifactType.OTHER || !file.extension.equals("zip", ignoreCase = true)) {
            return byName
        }
        return runCatching {
            ZipFile(file).use { zip ->
                val names = zip.entries().asSequence().map { it.name.lowercase(Locale.ROOT) }.take(256).toList()
                when {
                    names.any { it == "module.prop" || it.endsWith("/module.prop") } -> ArtifactType.SUSFS_MODULE
                    names.any { it.endsWith("meta-inf/com/google/android/update-binary") } &&
                        names.any { it.contains("anykernel") || it.startsWith("tools/") } -> ArtifactType.ANYKERNEL3
                    else -> ArtifactType.OTHER
                }
            }
        }.getOrDefault(ArtifactType.OTHER)
    }

    fun installApk(context: Context, filePath: String): Boolean {
        val file = File(filePath)
        if (!file.exists()) return false
        return runCatching {
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val viewIntent = buildViewIntent(context, uri, "application/vnd.android.package-archive", file.name)
            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                clipData = ClipData.newUri(context.contentResolver, file.name, uri)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivitySafely(viewIntent) || context.startActivitySafely(installIntent)
        }.getOrDefault(false)
    }

    private fun Context.startActivitySafely(intent: Intent): Boolean {
        return try {
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun buildViewIntent(
        context: Context,
        uri: android.net.Uri,
        mimeType: String,
        label: String
    ): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            clipData = ClipData.newUri(context.contentResolver, label, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
        }
    }
}
