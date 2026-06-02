package com.abk.kernel.utils

import android.content.Context
import com.abk.kernel.BuildConfig
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
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

object DownloadUtils {

    private val client = OkHttpClient.Builder().build()
    private const val LICENSE_FILE_NAME = "LICENSE"
    private const val THIRD_PARTY_NOTICES_FILE_NAME = "THIRD_PARTY_NOTICES.md"
    private const val BUNDLE_MANIFEST_FILE_NAME = "ABK_BUNDLE_MANIFEST.txt"

    data class DownloadResult(
        val artifacts: List<DownloadedArtifact> = emptyList(),
        val errorMessage: String? = null
    )

    data class PreparedDownloadedArtifact(
        val file: File,
        val cleanupDir: File? = null
    )

    private data class NoticeFiles(
        val license: File,
        val thirdPartyNotices: File
    )

    private data class LocalDownloadEntry(
        val displayName: String,
        val file: File,
        val type: ArtifactType
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
        bundleWithNotices: Boolean = false,
        onProgress: (Int) -> Unit = {}
    ): DownloadResult = withContext(Dispatchers.IO) {
        var runDir: File? = null
        var zipFile: File? = null
        var outDir: File? = null
        var stageDir: File? = null
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
                    if (bundleWithNotices) {
                        outDir = File(targetRunDir, safeFileName(artifact.name)).apply {
                            if (exists()) deleteRecursively()
                            mkdirs()
                        }
                        stageDir = createStageDir(context, "artifact-${safeFileName(artifact.name)}")
                        zipFile = File(requireNotNull(stageDir), "${safeFileName(artifact.name)}.zip")
                    } else {
                        zipFile = File(targetRunDir, "${artifact.name}.zip")
                    }

                    body.byteStream().use { input ->
                        writeStreamToFile(input, zipFile!!, totalBytes, onProgress)
                    }
                }
            } finally {
                cancellationHandle.dispose()
            }

            val downloadedZip = requireNotNull(zipFile)
            val records = if (bundleWithNotices) {
                val stagingRoot = requireNotNull(stageDir)
                unzip(downloadedZip, stagingRoot)
                downloadedZip.delete()
                zipFile = null

                val candidates = collectCandidateFiles(stagingRoot)
                if (candidates.isEmpty()) {
                    stagingRoot.deleteRecursively()
                    stageDir = null
                    outDir?.deleteRecursively()
                    return@withContext DownloadResult(
                        errorMessage = "No downloadable payload was found in ${artifact.name}"
                    )
                }

                val notices = resolveNoticeFiles(stagingRoot)
                    ?: run {
                        stagingRoot.deleteRecursively()
                        stageDir = null
                        outDir?.deleteRecursively()
                        return@withContext DownloadResult(
                            errorMessage = "Failed to fetch $LICENSE_FILE_NAME or $THIRD_PARTY_NOTICES_FILE_NAME"
                        )
                    }

                createBundledDownloadEntries(
                    bundleRootDir = requireNotNull(outDir),
                    candidates = candidates,
                    notices = notices
                )
            } else {
                val targetOutDir = File(requireNotNull(runDir), safeFileName(artifact.name))
                outDir = targetOutDir
                if (targetOutDir.exists()) targetOutDir.deleteRecursively()
                targetOutDir.mkdirs()
                unzip(downloadedZip, targetOutDir)
                downloadedZip.delete()
                zipFile = null
                collectCandidateFiles(targetOutDir).map { candidate ->
                    LocalDownloadEntry(
                        displayName = candidate.name,
                        file = candidate,
                        type = classifyDownloadedFile(candidate)
                    )
                }
            }

            DownloadResult(
                artifacts = records.mapIndexed { index, entry ->
                    DownloadedArtifact(
                        id = artifact.id * 1000 + index + 1,
                        name = entry.displayName,
                        filePath = entry.file.absolutePath,
                        type = entry.type,
                        sizeBytes = entry.file.length(),
                        runId = run?.id ?: -1L,
                        runTitle = run?.displayTitle ?: run?.name ?: run?.let { "#${it.runNumber}" }
                            ?: context.getString(R.string.workflow_unlinked),
                        runNumber = run?.runNumber ?: 0,
                        category = entry.type.toArtifactCategory()
                    )
                }
            )
                .also {
                    stageDir?.deleteRecursively()
                    stageDir = null
                }
        } catch (e: CancellationException) {
            zipFile?.delete()
            stageDir?.deleteRecursively()
            outDir?.deleteRecursively()
            runDir?.takeIf { it.exists() && it.listFiles()?.isEmpty() == true }?.delete()
            throw e
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            zipFile?.delete()
            stageDir?.deleteRecursively()
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
        bundleWithNotices: Boolean = false,
        onProgress: (Int) -> Unit = {}
    ): DownloadResult = withContext(Dispatchers.IO) {
        var assetDir: File? = null
        var file: File? = null
        var outDir: File? = null
        var stageDir: File? = null
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
                        if (bundleWithNotices && exists()) {
                            deleteRecursively()
                        }
                        mkdirs()
                    }
                    assetDir = targetAssetDir
                    if (bundleWithNotices) {
                        stageDir = createStageDir(context, "prebuilt-${safeFileName(name)}")
                        file = File(requireNotNull(stageDir), safeFileName(name))
                    } else {
                        file = File(targetAssetDir, safeFileName(name))
                    }

                    body.byteStream().use { input ->
                        writeStreamToFile(input, file!!, totalBytes, onProgress)
                    }
                }
            } finally {
                cancellationHandle.dispose()
            }

            val downloadedFile = requireNotNull(file)
            val records = if (bundleWithNotices) {
                val byName = classifyDownloadedFile(downloadedFile)
                val candidateFiles = if (downloadedFile.extension.equals("zip", ignoreCase = true) &&
                    byName in setOf(ArtifactType.KERNEL_PACKAGE, ArtifactType.OTHER)
                ) {
                    val extractedDir = File(requireNotNull(stageDir), "extracted").apply { mkdirs() }
                    unzip(downloadedFile, extractedDir)
                    collectCandidateFiles(extractedDir)
                } else {
                    listOf(downloadedFile)
                }
                if (candidateFiles.isEmpty()) {
                    stageDir?.deleteRecursively()
                    stageDir = null
                    assetDir?.deleteRecursively()
                    return@withContext DownloadResult(
                        errorMessage = "No downloadable payload was found in $name"
                    )
                }
                val notices = resolveNoticeFiles(requireNotNull(stageDir))
                    ?: run {
                        stageDir?.deleteRecursively()
                        stageDir = null
                        assetDir?.deleteRecursively()
                        return@withContext DownloadResult(
                            errorMessage = "Failed to fetch $LICENSE_FILE_NAME or $THIRD_PARTY_NOTICES_FILE_NAME"
                        )
                    }
                createBundledDownloadEntries(
                    bundleRootDir = requireNotNull(assetDir),
                    candidates = candidateFiles,
                    notices = notices
                )
            } else {
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
                files.map { candidate ->
                    LocalDownloadEntry(
                        displayName = candidate.name,
                        file = candidate,
                        type = classifyDownloadedFile(candidate)
                    )
                }
            }

            DownloadResult(
                artifacts = records.mapIndexed { index, entry ->
                    DownloadedArtifact(
                        id = runId * 1000 + index.toLong() + 1L,
                        name = entry.displayName,
                        filePath = entry.file.absolutePath,
                        type = entry.type,
                        sizeBytes = entry.file.length(),
                        runId = runId,
                        runTitle = runTitle,
                        runNumber = 0,
                        category = entry.type.toArtifactCategory()
                    )
                }
            )
                .also {
                    stageDir?.deleteRecursively()
                    stageDir = null
                }
        } catch (e: CancellationException) {
            file?.delete()
            stageDir?.deleteRecursively()
            outDir?.deleteRecursively()
            assetDir?.deleteRecursively()
            throw e
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            file?.delete()
            stageDir?.deleteRecursively()
            outDir?.deleteRecursively()
            assetDir?.takeIf { bundleWithNotices }?.deleteRecursively()
            DownloadResult()
        }
    }

    fun prepareDownloadedArtifact(
        context: Context,
        artifact: DownloadedArtifact
    ): PreparedDownloadedArtifact {
        val source = File(artifact.filePath)
        if (!source.exists() || !looksLikeNoticeBundle(source)) {
            return PreparedDownloadedArtifact(source)
        }

        val extractDir = createStageDir(context, "prepared-${safeFileName(artifact.name)}")
        unzip(source, extractDir)
        val manifest = File(extractDir, BUNDLE_MANIFEST_FILE_NAME)
        val payloadName = parseBundledPayloadName(manifest.takeIf { it.isFile }?.readText())
        val payload = payloadName?.let { File(extractDir, it).takeIf(File::isFile) }
            ?: extractDir.walkTopDown()
                .firstOrNull {
                    it.isFile &&
                        it.name != BUNDLE_MANIFEST_FILE_NAME &&
                        it.name !in setOf(LICENSE_FILE_NAME, THIRD_PARTY_NOTICES_FILE_NAME)
                }
            ?: throw IllegalStateException("Bundled artifact missing payload: ${artifact.name}")
        return PreparedDownloadedArtifact(payload, extractDir)
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

    private fun createStageDir(context: Context, prefix: String): File =
        File(context.cacheDir, "download-stage/${prefix}-${System.currentTimeMillis()}").apply {
            deleteRecursively()
            mkdirs()
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

    private suspend fun resolveNoticeFiles(stagingRoot: File): NoticeFiles? {
        findNoticeFiles(stagingRoot)?.let { return it }

        val noticeDir = File(stagingRoot, "__abk_notices").apply { mkdirs() }
        val license = File(noticeDir, LICENSE_FILE_NAME)
        val thirdParty = File(noticeDir, THIRD_PARTY_NOTICES_FILE_NAME)
        if (!license.exists() && !downloadNoticeFile(LICENSE_FILE_NAME, license)) return null
        if (!thirdParty.exists() && !downloadNoticeFile(THIRD_PARTY_NOTICES_FILE_NAME, thirdParty)) return null
        return NoticeFiles(license = license, thirdPartyNotices = thirdParty)
    }

    private fun findNoticeFiles(root: File): NoticeFiles? {
        val license = root.walkTopDown().firstOrNull { it.isFile && it.name == LICENSE_FILE_NAME }
        val thirdParty = root.walkTopDown().firstOrNull { it.isFile && it.name == THIRD_PARTY_NOTICES_FILE_NAME }
        return if (license != null && thirdParty != null) {
            NoticeFiles(license = license, thirdPartyNotices = thirdParty)
        } else {
            null
        }
    }

    private suspend fun downloadNoticeFile(fileName: String, destination: File): Boolean {
        destination.parentFile?.mkdirs()
        val branches = listOf(
            BuildConfig.SOURCE_REPO_DEFAULT_BRANCH,
            "main",
            "dev"
        ).filter { it.isNotBlank() }.distinct()
        for (branch in branches) {
            val url = "https://raw.githubusercontent.com/${BuildConfig.SOURCE_REPO_OWNER}/${BuildConfig.SOURCE_REPO_NAME}/$branch/$fileName"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/octet-stream")
                .build()
            val call = client.newCall(request)
            val cancellationHandle = coroutineContext.job.invokeOnCompletion { cause ->
                if (cause is CancellationException) {
                    call.cancel()
                }
            }
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body ?: return@use
                    body.byteStream().use { input ->
                        writeStreamToFile(input, destination, body.contentLength().coerceAtLeast(1L)) {}
                    }
                    return true
                }
            } finally {
                cancellationHandle.dispose()
            }
        }
        destination.delete()
        return false
    }

    private fun createBundledDownloadEntries(
        bundleRootDir: File,
        candidates: List<File>,
        notices: NoticeFiles
    ): List<LocalDownloadEntry> {
        return candidates.mapIndexed { index, candidate ->
            val dirName = safeFileName(candidate.name).ifBlank { "artifact-${index + 1}" }
            val candidateDir = File(bundleRootDir, dirName).apply {
                if (exists()) deleteRecursively()
                mkdirs()
            }
            val bundleFile = File(candidateDir, "${safeFileName(candidate.name)}.bundle.zip")
            createNoticeBundle(bundleFile, candidate, notices)
            LocalDownloadEntry(
                displayName = candidate.name,
                file = bundleFile,
                type = classifyDownloadedFile(candidate)
            )
        }
    }

    private fun createNoticeBundle(
        bundleFile: File,
        payload: File,
        notices: NoticeFiles
    ) {
        ZipOutputStream(FileOutputStream(bundleFile)).use { zip ->
            zip.putNextEntry(ZipEntry(BUNDLE_MANIFEST_FILE_NAME))
            zip.write("payload=${payload.name}\n".toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            addFileToZip(zip, payload, payload.name)
            addFileToZip(zip, notices.license, LICENSE_FILE_NAME)
            addFileToZip(zip, notices.thirdPartyNotices, THIRD_PARTY_NOTICES_FILE_NAME)
        }
    }

    private fun addFileToZip(zip: ZipOutputStream, file: File, entryName: String) {
        zip.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { input ->
            input.copyTo(zip)
        }
        zip.closeEntry()
    }

    private fun looksLikeNoticeBundle(file: File): Boolean {
        if (!file.isFile || !file.extension.equals("zip", ignoreCase = true)) return false
        return runCatching {
            ZipFile(file).use { zip ->
                zip.getEntry(BUNDLE_MANIFEST_FILE_NAME) != null
            }
        }.getOrDefault(false)
    }

    private fun parseBundledPayloadName(manifest: String?): String? =
        manifest
            ?.lineSequence()
            ?.firstOrNull { it.startsWith("payload=") }
            ?.substringAfter('=')
            ?.trim()
            ?.ifBlank { null }

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
