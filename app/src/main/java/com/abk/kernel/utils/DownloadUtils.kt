package com.abk.kernel.utils

import android.content.Context
import com.abk.kernel.R
import com.abk.kernel.data.model.Artifact
import com.abk.kernel.data.model.ArtifactCategory
import com.abk.kernel.data.model.ArtifactType
import com.abk.kernel.data.model.BuildArtifact
import com.abk.kernel.data.model.DownloadedArtifact
import com.abk.kernel.data.model.PREBUILT_GKI_RUN_ID
import com.abk.kernel.data.model.PrebuiltGkiAsset
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.data.model.toArtifact
import com.abk.kernel.data.model.toArtifactCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

object DownloadUtils {

    private val client = OkHttpClient.Builder().build()

    data class DownloadResult(
        val artifacts: List<DownloadedArtifact> = emptyList(),
        val errorMessage: String? = null
    )

    fun classifyArtifact(name: String): ArtifactType {
        val lower = name.lowercase()
        return when {
            lower.contains("reject") || lower.contains("-rej") -> ArtifactType.OTHER
            lower.contains("_kernel-android") || lower.contains("kernel-android") -> ArtifactType.KERNEL_PACKAGE
            lower.endsWith(".img") && (lower.contains("boot") || lower.contains("kernel") || lower.contains("gki")) -> ArtifactType.KERNEL_IMG
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

    fun matchesDownloadedPrebuilt(downloaded: DownloadedArtifact, asset: PrebuiltGkiAsset): Boolean =
        downloaded.runId == PREBUILT_GKI_RUN_ID &&
            downloaded.filePath.contains("/prebuilt-gki/${artifactStorageFolderName(asset.name)}/")

    fun artifactStorageFolderName(name: String): String = safeFileName(name)

    fun prebuiltProgressKey(assetId: Long): Long = -(assetId.coerceAtLeast(1L) + 1_000_000_000L)

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
        downloadDirectoryPath: String? = null,
        onProgress: (Int) -> Unit = {}
    ): DownloadResult = withContext(Dispatchers.IO) {
        var runDir: File? = null
        var zipFile: File? = null
        var outDir: File? = null
        try {
            val downloadsRoot = resolveDownloadsRoot(downloadDirectoryPath)
                ?: return@withContext DownloadResult(
                    errorMessage = downloadDirectoryErrorMessage(context, downloadDirectoryPath)
                )
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

            val call = client.newCall(request)
            val cancellationHandle = coroutineContext.job.invokeOnCompletion { cause ->
                if (cause is CancellationException) {
                    call.cancel()
                }
            }
            try {
                call.execute().use { handled ->
                    if (!handled.isSuccessful) return@withContext DownloadResult()
                    val body = handled.body ?: return@withContext DownloadResult()
                    val totalBytes = artifact.sizeInBytes.coerceAtLeast(1L)

                    val targetRunDir = File(downloadsRoot, runFolderName(run)).apply { mkdirs() }
                    runDir = targetRunDir
                    zipFile = File(targetRunDir, "${artifact.name}.zip")

                    body.byteStream().use { input ->
                        writeStreamToFile(input, zipFile!!, totalBytes, onProgress)
                    }
                }
            } finally {
                cancellationHandle.dispose()
            }

            // Unzip into named folder
            val targetOutDir = File(requireNotNull(runDir), safeFileName(artifact.name))
            outDir = targetOutDir
            if (targetOutDir.exists()) targetOutDir.deleteRecursively()
            targetOutDir.mkdirs()
            val downloadedZip = requireNotNull(zipFile)
            unzip(downloadedZip, targetOutDir)
            downloadedZip.delete()
            zipFile = null

            DownloadResult(
                artifacts = collectCandidateFiles(targetOutDir).mapIndexed { index, file ->
                    val type = classifyDownloadedFile(file)
                    DownloadedArtifact(
                        id = artifact.id * 1000 + index + 1,
                        name = file.name,
                        filePath = file.absolutePath,
                        type = type,
                        sizeBytes = file.length(),
                        runId = run?.id ?: -1L,
                        runTitle = run?.displayTitle ?: run?.name ?: run?.let { "#${it.runNumber}" }
                            ?: context.getString(R.string.workflow_unlinked),
                        runNumber = run?.runNumber ?: 0,
                        category = type.toArtifactCategory()
                    )
                }
            )
        } catch (e: CancellationException) {
            zipFile?.delete()
            outDir?.deleteRecursively()
            runDir?.takeIf { it.exists() && it.listFiles()?.isEmpty() == true }?.delete()
            throw e
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            zipFile?.delete()
            outDir?.deleteRecursively()
            DownloadResult()
        }
    }

    suspend fun downloadDirectAsset(
        context: Context,
        token: String?,
        url: String,
        name: String,
        sizeBytes: Long,
        runId: Long,
        runTitle: String,
        downloadDirectoryPath: String? = null,
        onProgress: (Int) -> Unit = {}
    ): DownloadResult = withContext(Dispatchers.IO) {
        var assetDir: File? = null
        var file: File? = null
        var outDir: File? = null
        try {
            val downloadsRoot = resolveDownloadsRoot(downloadDirectoryPath)
                ?: return@withContext DownloadResult(
                    errorMessage = downloadDirectoryErrorMessage(context, downloadDirectoryPath)
                )
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/octet-stream")
                .apply {
                    if (!token.isNullOrBlank()) {
                        header("Authorization", "Bearer $token")
                    }
                }
                .build()

            val call = client.newCall(request)
            val cancellationHandle = coroutineContext.job.invokeOnCompletion { cause ->
                if (cause is CancellationException) {
                    call.cancel()
                }
            }
            try {
                call.execute().use { handled ->
                    if (!handled.isSuccessful) return@withContext DownloadResult()
                    val body = handled.body ?: return@withContext DownloadResult()
                    val totalBytes = when {
                        sizeBytes > 0L -> sizeBytes
                        body.contentLength() > 0L -> body.contentLength()
                        else -> 1L
                    }

                    val targetAssetDir = File(downloadsRoot, "prebuilt-gki/${safeFileName(name)}").apply {
                        if (exists()) deleteRecursively()
                        mkdirs()
                    }
                    assetDir = targetAssetDir
                    file = File(targetAssetDir, safeFileName(name))

                    body.byteStream().use { input ->
                        writeStreamToFile(input, file!!, totalBytes, onProgress)
                    }
                }
            } finally {
                cancellationHandle.dispose()
            }

            val downloadedFile = requireNotNull(file)
            val byName = classifyDownloadedFile(downloadedFile)
            val files = if (downloadedFile.extension.equals("zip", ignoreCase = true) && byName in setOf(ArtifactType.KERNEL_PACKAGE, ArtifactType.OTHER)) {
                val extractedDir = File(requireNotNull(assetDir), "extracted")
                outDir = extractedDir
                extractedDir.mkdirs()
                unzip(downloadedFile, extractedDir)
                downloadedFile.delete()
                file = null
                collectCandidateFiles(extractedDir)
            } else {
                listOf(downloadedFile)
            }

            DownloadResult(
                artifacts = files.mapIndexed { index, candidate ->
                    val type = classifyDownloadedFile(candidate)
                    DownloadedArtifact(
                        id = runId * 1000 + index.toLong() + 1L,
                        name = candidate.name,
                        filePath = candidate.absolutePath,
                        type = type,
                        sizeBytes = candidate.length(),
                        runId = runId,
                        runTitle = runTitle,
                        runNumber = 0,
                        category = type.toArtifactCategory()
                    )
                }
            )
        } catch (e: CancellationException) {
            file?.delete()
            outDir?.deleteRecursively()
            assetDir?.deleteRecursively()
            throw e
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            file?.delete()
            outDir?.deleteRecursively()
            DownloadResult()
        }
    }

    private fun runFolderName(run: WorkflowRun?): String {
        if (run == null) return "manual"
        val title = run.displayTitle ?: run.name ?: "workflow"
        return "run-${run.runNumber}-${safeFileName(title).take(48)}"
    }

    private suspend fun writeStreamToFile(
        input: InputStream,
        destination: File,
        totalBytes: Long,
        onProgress: (Int) -> Unit
    ) {
        FileOutputStream(destination).use { output ->
            val buffer = ByteArray(8 * 1024)
            var downloaded = 0L
            while (true) {
                coroutineContext.ensureActive()
                val bytes = input.read(buffer)
                if (bytes == -1) break
                output.write(buffer, 0, bytes)
                downloaded += bytes
                val pct = (downloaded * 100 / totalBytes).toInt().coerceIn(0, 100)
                onProgress(pct)
            }
        }
    }

    private fun safeFileName(value: String): String =
        value.replace(Regex("""[^A-Za-z0-9._ -]"""), "_")
            .trim()
            .ifBlank { "artifact" }

    private fun resolveDownloadsRoot(downloadDirectoryPath: String?): File? {
        val normalizedPath = DownloadDirectoryUtils.normalizeDirectoryPath(downloadDirectoryPath)
        val directory = File(normalizedPath)
        if (directory.exists()) {
            return directory.takeIf { it.isDirectory && it.canWrite() }
        }
        if (!directory.mkdirs() && !directory.exists()) {
            return null
        }
        return directory.takeIf { it.isDirectory && it.canWrite() }
    }

    private fun downloadDirectoryErrorMessage(context: Context, downloadDirectoryPath: String?): String {
        val normalizedPath = DownloadDirectoryUtils.normalizeDirectoryPath(downloadDirectoryPath)
        val directory = File(normalizedPath)
        return when {
            directory.exists() && !directory.isDirectory ->
                context.getString(R.string.download_directory_not_directory, normalizedPath)
            directory.exists() && !directory.canWrite() ->
                context.getString(R.string.download_directory_not_writable, normalizedPath)
            else ->
                context.getString(R.string.download_directory_create_failed, normalizedPath)
        }
    }

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

    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
        }
    }
}
