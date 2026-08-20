package com.abk.kernel.utils

import android.content.Context
import android.util.Log
import com.abk.kernel.BuildConfig
import com.abk.kernel.R
import com.abk.kernel.data.model.APP_UPDATE_LINE_DEV
import com.abk.kernel.data.model.Artifact
import com.abk.kernel.data.model.ArtifactCategory
import com.abk.kernel.data.model.ArtifactType
import com.abk.kernel.data.model.BuildArtifact
import com.abk.kernel.data.model.DownloadedArtifact
import com.abk.kernel.data.model.PREBUILT_GKI_RUN_ID
import com.abk.kernel.data.model.PrebuiltGkiAsset
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.data.repository.PreferencesRepository
import com.abk.kernel.data.model.normalizeAppUpdateLine
import com.abk.kernel.data.model.toArtifact
import com.abk.kernel.data.model.toArtifactCategory
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

object DownloadUtils {

    private val client = OkHttpClient.Builder().build()
    private val gson = Gson()
    private const val LICENSE_FILE_NAME = "LICENSE"
    private const val THIRD_PARTY_NOTICES_FILE_NAME = "THIRD_PARTY_NOTICES.md"
    private const val BUNDLE_MANIFEST_FILE_NAME = "ABK_BUNDLE_MANIFEST.txt"
    private const val SIGNED_BUNDLE_MANIFEST_FILE_NAME = "ABK_BUNDLE_MANIFEST.json"
    private const val SIGNED_BUNDLE_SIGNATURE_FILE_NAME = "ABK_BUNDLE_MANIFEST.sig"
    private const val FLASH_DEPENDENCIES_FILE_NAME = "ABK_FLASH_DEPENDENCIES.json"
    private const val BUNDLE_DEPENDENCY_DIR_NAME = "magisk-dependencies"
    private const val NOTICE_STAGING_DIR_NAME = "__abk_notices"
    const val DEFAULT_THREAD_COUNT = 8
    private val downloadSlots = Semaphore(2)

    internal fun isBundledNoticeFileName(fileName: String): Boolean =
        fileName.equals(LICENSE_FILE_NAME, ignoreCase = true) ||
            fileName.equals(THIRD_PARTY_NOTICES_FILE_NAME, ignoreCase = true)

    data class DownloadResult(
        val artifacts: List<DownloadedArtifact> = emptyList(),
        val errorMessage: String? = null
    )

    data class PreparedDownloadedArtifact(
        val file: File,
        val cleanupDir: File? = null,
        val dependencyModules: List<File> = emptyList(),
        val dependencyApps: List<File> = emptyList(),
        val legacyBundleManifest: String? = null,
        val resolvedType: ArtifactType? = null
    )

    data class AppUpdatePackageResult(
        val apkFile: File? = null,
        val errorMessage: String? = null
    )

    suspend fun downloadRuntimeModuleAsset(
        context: Context,
        token: String?,
        url: String,
        name: String,
        sizeBytes: Long = 0L,
        runId: Long = -2000000001L,
        runTitle: String,
        downloadDirectoryPath: String? = null,
        preserveDownloadedZip: Boolean = true,
        downloadThreadCount: Int = DEFAULT_THREAD_COUNT,
    ): DownloadResult {
        return downloadDirectAsset(
            context = context,
            token = token,
            url = url,
            name = name,
            sizeBytes = sizeBytes,
            runId = runId,
            runTitle = runTitle,
            sourceAssetId = 0L,
            downloadDirectoryPath = downloadDirectoryPath,
            storageSubdirectory = "ABK",
            preserveDownloadedZip = preserveDownloadedZip,
            bundleWithNotices = false,
            downloadThreadCount = downloadThreadCount
        )
    }

    fun fileSha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } > 0) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class NoticeFiles(
        val license: File,
        val thirdPartyNotices: File
    )

    private data class DirectAssetStorage(
        val assetDir: File,
        val preserveDownloadedZip: Boolean = false,
        val bundleWithNotices: Boolean = false,
        val downloadedFileIsRoot: Boolean = false,
    )

    private data class LocalDownloadEntry(
        val displayName: String,
        val file: File,
        val type: ArtifactType,
        val verified: Boolean = false,
        val verificationSummary: String? = null,
        val manifest: SignedBundleManifest? = null
    )

    private data class BundledMagiskModuleDependency(
        val name: String,
        val downloadUrl: String
    )

    private data class BundledCompanionAppDependency(
        val name: String,
        val downloadUrl: String
    )

    private data class BundleVerificationState(
        val verified: Boolean,
        val summary: String?
    )

    enum class FlashSecurityIssueKind {
        MISSING_SIGNATURE,
        SIGNATURE_MISMATCH,
        MISSING_PUBLIC_KEY,
        OTHER
    }

    data class FlashSecurityPrompt(
        val kind: FlashSecurityIssueKind,
        val message: String,
    )

    private data class AuxiliaryArtifacts(
        val moduleFiles: List<File> = emptyList(),
        val appFiles: List<File> = emptyList()
    )

    fun classifyArtifact(name: String): ArtifactType {
        val lower = normalizedArtifactName(name).lowercase()
        return when {
            lower.contains("reject") || lower.contains("-rej") -> ArtifactType.OTHER
            lower.contains("_kernel-android") || lower.contains("kernel-android") -> ArtifactType.KERNEL_PACKAGE
            lower.endsWith(".img") && (lower.contains("boot") || lower.contains("kernel") || lower.contains("gki")) -> ArtifactType.KERNEL_IMG
            lower.contains("boot-img") || lower.contains("boot_img") || lower.contains("kernel-img") -> ArtifactType.KERNEL_IMG
            lower.contains("raw-image") || lower.contains("raw_image") -> ArtifactType.KERNEL_IMG
            lower.contains("anykernel") || lower.contains("ak3") -> ArtifactType.ANYKERNEL3
            lower.endsWith(".zip") && isLikelyModuleZipName(lower) -> ArtifactType.SUSFS_MODULE
            isLikelyModuleZipName(lower) && !lower.contains("anykernel") -> ArtifactType.SUSFS_MODULE
            // Build ABK App workflows upload a single artifact bundle named
            // "abk-apks" that contains the debug/release APK files. Keep the
            // dedicated type so legacy persisted downloads still parse cleanly,
            // while flash-specific UI can filter it out.
            lower == "abk-apks" || lower.contains("abk-apks") -> ArtifactType.ABK_MANAGER
            lower.contains("abk") && lower.endsWith(".apk") -> ArtifactType.ABK_MANAGER
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
        ArtifactType.ABK_MANAGER,
        ArtifactType.KSU_MANAGER -> ArtifactCategory.MANAGER
        ArtifactType.SUSFS_MODULE -> ArtifactCategory.MODULE
        ArtifactType.OTHER -> null
    }

    fun shouldAutoDownload(artifact: Artifact): Boolean {
        val lower = artifact.name.lowercase()
        val type = classifyArtifact(artifact.name)
        return when (type) {
            ArtifactType.ABK_MANAGER -> false
            else -> when (classifyCategory(type)) {
            ArtifactCategory.KERNEL,
            ArtifactCategory.MODULE -> true
            ArtifactCategory.MANAGER -> isLikelySupportedManager(lower)
            null -> false
            }
        }
    }

    fun shouldAutoDownload(artifact: BuildArtifact): Boolean = shouldAutoDownload(artifact.toArtifact())

    fun matchesDownloadedArtifact(downloaded: DownloadedArtifact, artifact: BuildArtifact): Boolean =
        downloaded.runId == artifact.runId &&
            downloaded.filePath.contains("/${artifactStorageFolderName(artifact.name)}/")

    fun matchesDownloadedPrebuilt(downloaded: DownloadedArtifact, asset: PrebuiltGkiAsset): Boolean =
        downloaded.runId == PREBUILT_GKI_RUN_ID && (
            (downloaded.sourceAssetId > 0L && downloaded.sourceAssetId == asset.id) ||
                downloaded.sourceAssetName?.trim() == asset.name ||
                downloaded.filePath.contains("/prebuilt-gki/${artifactStorageFolderName(asset.name)}/")
        )

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
        resolveSigningPublicKeyPem: (suspend () -> String?)? = null,
        downloadThreadCount: Int = DEFAULT_THREAD_COUNT,
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

            downloadUrlToFile(
                        url = url,
                        headers = buildMap {
                            put("Accept", if (downloadUrl == null) "application/vnd.github+json" else "application/octet-stream")
                            if (downloadUrl == null && !token.isNullOrBlank()) put("Authorization", "Bearer $token")
                        },
                        destination = zipFile!!,
                        knownSize = artifact.sizeInBytes,
                        threadCount = downloadThreadCount,
                        onProgress = onProgress
            )

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
                val bundledDependencies = resolveBundledMagiskModules(stagingRoot, token)
                val bundledCompanionApps = resolveBundledCompanionApps(stagingRoot, token)
                val signingVerificationEnabled = PreferencesRepository(context)
                    .readArtifactSigningVerificationEnabledBlocking()
                val requiresTrustedKey = candidates.any { candidate ->
                    val manifest = ArtifactVerification.readBundleManifest(candidate)
                    manifest != null && ArtifactVerification.requiresTrustedBundle(
                        classifyDownloadedFile(candidate),
                        manifest
                    )
                }
                val signingPublicKeyPem = if (
                    signingVerificationEnabled &&
                    requiresTrustedKey &&
                    resolveSigningPublicKeyPem != null
                ) {
                    resolveSigningPublicKeyPem()
                } else {
                    PreferencesRepository(context).readForkArtifactSigningPublicKeyBlocking()
                        ?.takeIf { it.isNotBlank() }
                        ?.let(ForkSigningManager::publicKeyPemFromStoredValue)
                }

                createBundledDownloadEntries(
                    context = context,
                    bundleRootDir = requireNotNull(outDir),
                    candidates = candidates,
                    notices = notices,
                    signingVerificationEnabled = signingVerificationEnabled,
                    signingPublicKeyPem = signingPublicKeyPem,
                    bundledDependencies = bundledDependencies,
                    bundledCompanionApps = bundledCompanionApps
                )
            } else {
                val targetOutDir = File(requireNotNull(runDir), safeFileName(artifact.name))
                outDir = targetOutDir
                if (targetOutDir.exists()) targetOutDir.deleteRecursively()
                targetOutDir.mkdirs()
                unzip(downloadedZip, targetOutDir)
                downloadedZip.delete()
                zipFile = null
                val signingPublicKey = PreferencesRepository(context).readForkArtifactSigningPublicKeyBlocking()
                val signingVerificationEnabled = PreferencesRepository(context)
                    .readArtifactSigningVerificationEnabledBlocking()
                collectCandidateFiles(targetOutDir).map { candidate ->
                    val type = classifyDownloadedFile(candidate)
                    val manifest = ArtifactVerification.readBundleManifest(candidate)
                    val verification = if (signingVerificationEnabled && ArtifactVerification.requiresTrustedBundle(type, manifest)) {
                        ArtifactVerification.verifyBundleFile(
                            candidate,
                            type,
                            ForkSigningManager.publicKeyPemFromStoredValue(signingPublicKey)
                        )
                    } else {
                        null
                    }
                    LocalDownloadEntry(
                        displayName = candidate.name,
                        file = candidate,
                        type = type,
                        verified = verification?.success == true,
                        verificationSummary = verification?.message ?: if (
                            !signingVerificationEnabled && ArtifactVerification.requiresTrustedBundle(type, manifest)
                        ) {
                            context.getString(R.string.flash_bundle_verification_disabled)
                        } else {
                            null
                        },
                        manifest = manifest.takeIf { verification?.success == true }
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
                        sourceAssetName = artifact.name,
                        verified = entry.verified,
                        verificationSummary = entry.verificationSummary,
                        manifestPayloadKind = entry.manifest?.payloadKind,
                        manifestKernelSource = entry.manifest?.kernelSource?.let(gson::toJson),
                        manifestFeatureStatus = entry.manifest?.featureStatus?.let(gson::toJson),
                        manifestClientNotice = entry.manifest?.clientNotice?.let(gson::toJson),
                        category = if (entry.manifest?.payloadKind == "KERNEL_IMAGE_SET") ArtifactCategory.KERNEL else entry.type.toArtifactCategory()
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
            DownloadResult(errorMessage = downloadExceptionMessage(context, e))
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
        sourceAssetId: Long = 0L,
        downloadDirectoryPath: String? = null,
        storageSubdirectory: String? = "prebuilt-gki",
        preserveDownloadedZip: Boolean = false,
        bundleWithNotices: Boolean = false,
        downloadThreadCount: Int = DEFAULT_THREAD_COUNT,
        onProgress: (Int) -> Unit = {}
    ): DownloadResult = downloadDirectAsset(
        context = context,
        token = token,
        url = url,
        name = name,
        sizeBytes = sizeBytes,
        runId = runId,
        runTitle = runTitle,
        sourceAssetId = sourceAssetId,
        storage = resolveDirectAssetStorage(
            downloadDirectoryPath = downloadDirectoryPath,
            storageSubdirectory = storageSubdirectory,
            preserveDownloadedZip = preserveDownloadedZip,
            bundleWithNotices = bundleWithNotices,
        ) ?: return DownloadResult(
            errorMessage = downloadDirectoryErrorMessage(context, downloadDirectoryPath)
        ),
        downloadThreadCount = downloadThreadCount,
        onProgress = onProgress,
    )

    private suspend fun downloadDirectAsset(
        context: Context,
        token: String?,
        url: String,
        name: String,
        sizeBytes: Long,
        runId: Long,
        runTitle: String,
        sourceAssetId: Long = 0L,
        storage: DirectAssetStorage,
        downloadThreadCount: Int = DEFAULT_THREAD_COUNT,
        onProgress: (Int) -> Unit = {}
    ): DownloadResult = withContext(Dispatchers.IO) {
        var assetDir: File? = storage.assetDir
        var file: File? = null
        var outDir: File? = null
        var stageDir: File? = null
        try {
            val targetAssetDir = requireNotNull(assetDir)
                    targetAssetDir.apply {
                        if (storage.bundleWithNotices && exists()) {
                            deleteRecursively()
                        }
                        mkdirs()
                    }
                    assetDir = targetAssetDir
                    if (storage.bundleWithNotices) {
                        stageDir = createStageDir(context, "prebuilt-${safeFileName(name)}")
                        file = File(requireNotNull(stageDir), safeFileName(name))
                    } else {
                        file = File(targetAssetDir, safeFileName(name))
                    }

            downloadUrlToFile(
                        url = url,
                        headers = buildMap {
                            put("Accept", "application/octet-stream")
                            if (!token.isNullOrBlank()) put("Authorization", "Bearer $token")
                        },
                        destination = file!!,
                        knownSize = sizeBytes,
                        threadCount = downloadThreadCount,
                        onProgress = onProgress
            )

            val downloadedFile = requireNotNull(file)
            val records = if (storage.bundleWithNotices) {
                val signingPublicKeyPem = PreferencesRepository(context)
                    .readForkArtifactSigningPublicKeyBlocking()
                    ?.takeIf { it.isNotBlank() }
                    ?.let(ForkSigningManager::publicKeyPemFromStoredValue)
                if (looksLikeNoticeBundle(downloadedFile)) {
                    val entry = persistBundledDownloadEntry(
                        context = context,
                        bundleRootDir = requireNotNull(assetDir),
                        downloadedFile = downloadedFile,
                        signingPublicKeyPem = signingPublicKeyPem
                    )
                    persistAuxiliaryArtifacts(
                        bundleFile = entry.file,
                        dependencyModules = emptyList(),
                        dependencyApps = emptyList()
                    )
                    listOf(entry)
                } else {
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
                        if (!storage.downloadedFileIsRoot) assetDir?.deleteRecursively()
                        return@withContext DownloadResult(
                            errorMessage = "No downloadable payload was found in $name"
                        )
                    }
                    val notices = resolveNoticeFiles(requireNotNull(stageDir))
                        ?: run {
                            stageDir?.deleteRecursively()
                            stageDir = null
                            if (!storage.downloadedFileIsRoot) assetDir?.deleteRecursively()
                            return@withContext DownloadResult(
                                errorMessage = "Failed to fetch $LICENSE_FILE_NAME or $THIRD_PARTY_NOTICES_FILE_NAME"
                            )
                        }
                    val bundledDependencies = resolveBundledMagiskModules(requireNotNull(stageDir), token)
                    val bundledCompanionApps = resolveBundledCompanionApps(requireNotNull(stageDir), token)
                    val signingVerificationEnabled = PreferencesRepository(context)
                        .readArtifactSigningVerificationEnabledBlocking()
                    createBundledDownloadEntries(
                        context = context,
                        bundleRootDir = requireNotNull(assetDir),
                        candidates = candidateFiles,
                        notices = notices,
                        signingVerificationEnabled = signingVerificationEnabled,
                        signingPublicKeyPem = signingPublicKeyPem,
                        bundledDependencies = bundledDependencies,
                        bundledCompanionApps = bundledCompanionApps
                    )
                }
            } else {
                val byName = classifyDownloadedFile(downloadedFile)
                val files = if (!storage.preserveDownloadedZip && downloadedFile.extension.equals("zip", ignoreCase = true) && byName in setOf(ArtifactType.KERNEL_PACKAGE, ArtifactType.OTHER)) {
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
                val signingVerificationEnabled = PreferencesRepository(context)
                    .readArtifactSigningVerificationEnabledBlocking()
                files.map { candidate ->
                    val type = classifyDownloadedFile(candidate)
                    val manifest = ArtifactVerification.readBundleManifest(candidate)
                    val verification = if (signingVerificationEnabled && ArtifactVerification.requiresTrustedBundle(type, manifest)) {
                        if (runId == PREBUILT_GKI_RUN_ID) {
                            null
                        } else {
                            val signingPublicKey = PreferencesRepository(context).readForkArtifactSigningPublicKeyBlocking()
                            ArtifactVerification.verifyBundleFile(
                                candidate,
                                type,
                                ForkSigningManager.publicKeyPemFromStoredValue(signingPublicKey)
                            )
                        }
                    } else {
                        null
                    }
                    LocalDownloadEntry(
                        displayName = candidate.name,
                        file = candidate,
                        type = type,
                        verified = verification?.success == true,
                        verificationSummary = verification?.message ?: if (
                            !signingVerificationEnabled && ArtifactVerification.requiresTrustedBundle(type, manifest)
                        ) {
                            context.getString(R.string.flash_bundle_verification_disabled)
                        } else {
                            null
                        },
                        manifest = manifest.takeIf { verification?.success == true }
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
                        sourceAssetId = sourceAssetId,
                        sourceAssetName = name,
                        verified = entry.verified,
                        verificationSummary = entry.verificationSummary,
                        manifestPayloadKind = entry.manifest?.payloadKind,
                        manifestKernelSource = entry.manifest?.kernelSource?.let(gson::toJson),
                        manifestFeatureStatus = entry.manifest?.featureStatus?.let(gson::toJson),
                        manifestClientNotice = entry.manifest?.clientNotice?.let(gson::toJson),
                        category = if (entry.manifest?.payloadKind == "KERNEL_IMAGE_SET") ArtifactCategory.KERNEL else entry.type.toArtifactCategory()
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
            if (!storage.downloadedFileIsRoot) assetDir?.deleteRecursively()
            throw e
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            file?.delete()
            stageDir?.deleteRecursively()
            outDir?.deleteRecursively()
            if (!storage.downloadedFileIsRoot) assetDir?.takeIf { storage.bundleWithNotices }?.deleteRecursively()
            DownloadResult(errorMessage = downloadExceptionMessage(context, e))
        }
    }

    suspend fun downloadAppUpdatePackage(
        context: Context,
        token: String?,
        url: String,
        preferredLine: String,
        downloadThreadCount: Int = DEFAULT_THREAD_COUNT,
        onProgress: (Int) -> Unit = {}
    ): AppUpdatePackageResult = withContext(Dispatchers.IO) {
        var stageDir: File? = null
        try {
            stageDir = File(context.cacheDir, "app-update").apply {
                deleteRecursively()
                mkdirs()
            }
            val fileName = safeFileName(url.substringAfterLast('/').ifBlank { "app-update.zip" })
            val archive = File(stageDir, fileName)
            downloadUrlToFile(
                        url = url,
                        headers = buildMap {
                            put("Accept", "application/octet-stream")
                            if (!token.isNullOrBlank()) put("Authorization", "Bearer $token")
                        },
                        destination = archive,
                        knownSize = 0L,
                        threadCount = downloadThreadCount,
                        onProgress = onProgress
            )

            val apkFile = if (archive.extension.equals("apk", ignoreCase = true)) {
                archive
            } else {
                val extractedDir = File(stageDir, "extracted").apply {
                    deleteRecursively()
                    mkdirs()
                }
                unzip(archive, extractedDir)
                selectAppUpdateApk(collectCandidateFiles(extractedDir), preferredLine)
            }

            if (apkFile == null || !apkFile.isFile) {
                return@withContext AppUpdatePackageResult(
                    errorMessage = context.getString(R.string.vm_app_update_apk_missing)
                )
            }
            AppUpdatePackageResult(apkFile = apkFile)
        } catch (e: CancellationException) {
            stageDir?.deleteRecursively()
            throw e
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            stageDir?.deleteRecursively()
            AppUpdatePackageResult(errorMessage = downloadExceptionMessage(context, e))
        }
    }

    fun prepareDownloadedArtifact(
        context: Context,
        artifact: DownloadedArtifact,
        allowHighRiskFallback: Boolean = false,
        signingVerificationEnabled: Boolean = PreferencesRepository(context).readArtifactSigningVerificationEnabledBlocking()
    ): PreparedDownloadedArtifact {
        val source = File(artifact.filePath)
        if (!source.exists()) {
            return PreparedDownloadedArtifact(source)
        }
        val sourceManifest = ArtifactVerification.readBundleManifest(source)
        val manifestType = sourceManifest?.artifactType
            ?.let { runCatching { ArtifactType.valueOf(it) }.getOrNull() }
        val effectiveType = manifestType ?: artifact.type
        if (ArtifactVerification.requiresTrustedBundle(effectiveType, sourceManifest) || looksLikeSignedBundle(source)) {
            val auxiliaryArtifacts = resolveAuxiliaryArtifacts(source)
            if (!signingVerificationEnabled) {
                val extractDir = createStageDir(context, "prepared-${safeFileName(artifact.name)}")
                unzip(source, extractDir)
                val signedPayloadName = ArtifactVerification.readBundleManifest(source)?.payloadName
                val legacyManifest = File(extractDir, BUNDLE_MANIFEST_FILE_NAME).takeIf { it.isFile }?.readText()
                val payloadName = signedPayloadName ?: parseBundledPayloadName(legacyManifest)
                val payload = payloadName?.let { File(extractDir, it).takeIf(File::isFile) }
                    ?: throw IllegalStateException("Bundled artifact missing payload: ${artifact.name}")
                return PreparedDownloadedArtifact(
                    file = payload,
                    cleanupDir = extractDir,
                    dependencyModules = auxiliaryArtifacts.moduleFiles,
                    dependencyApps = auxiliaryArtifacts.appFiles,
                    legacyBundleManifest = legacyManifest,
                    resolvedType = classifyDownloadedFile(payload)
                )
            }
            val verification = if (artifact.runId == PREBUILT_GKI_RUN_ID) {
                BundleVerificationResult(
                    manifest = SignedBundleManifest(
                        bundleName = source.name,
                        artifactType = effectiveType.name,
                        runId = PREBUILT_GKI_RUN_ID,
                        payloadName = "",
                        payloadSha256 = "",
                        payloadSizeBytes = 0L
                    ),
                    success = artifact.verified,
                    message = artifact.verificationSummary ?: "Prebuilt bundle requires confirmation",
                    failureReason = if (artifact.verified) {
                        BundleVerificationFailureReason.NONE
                    } else {
                        BundleVerificationFailureReason.OTHER
                    }
                )
            } else {
                val signingPublicKey = PreferencesRepository(context).readForkArtifactSigningPublicKeyBlocking()
                ArtifactVerification.verifyBundleFile(
                    source,
                    effectiveType,
                    ForkSigningManager.publicKeyPemFromStoredValue(signingPublicKey)
                )
            }
            if (!verification.success) {
                if (allowHighRiskFallback) {
                    val extractDir = createStageDir(context, "prepared-${safeFileName(artifact.name)}")
                    unzip(source, extractDir)
                    val legacyManifest = File(extractDir, BUNDLE_MANIFEST_FILE_NAME).takeIf { it.isFile }?.readText()
                    val payloadName = verification.manifest.payloadName.takeIf { it.isNotBlank() }
                        ?: ArtifactVerification.readBundleManifest(source)?.payloadName
                        ?: parseBundledPayloadName(legacyManifest)
                    val payload = payloadName?.let { File(extractDir, it).takeIf(File::isFile) }
                        ?: throw IllegalStateException("Bundled artifact missing payload: ${artifact.name}")
                    val resolvedType = classifyDownloadedFile(payload)
                    return PreparedDownloadedArtifact(
                        payload,
                        extractDir,
                        dependencyModules = auxiliaryArtifacts.moduleFiles,
                        dependencyApps = auxiliaryArtifacts.appFiles,
                        legacyBundleManifest = legacyManifest,
                        resolvedType = resolvedType
                    )
                }
                throw IllegalStateException(verification.message)
            }
            val extractDir = createStageDir(context, "prepared-${safeFileName(artifact.name)}")
            unzip(source, extractDir)
            val payload = File(extractDir, verification.manifest.payloadName)
                .takeIf(File::isFile)
                ?: throw IllegalStateException("Bundled artifact missing payload: ${artifact.name}")
            val resolvedType = classifyDownloadedFile(payload)
            return PreparedDownloadedArtifact(
                file = payload,
                cleanupDir = extractDir,
                dependencyModules = auxiliaryArtifacts.moduleFiles,
                dependencyApps = auxiliaryArtifacts.appFiles,
                resolvedType = resolvedType
            )
        }

        if (!looksLikeNoticeBundle(source)) {
            return PreparedDownloadedArtifact(source)
        }
        val extractDir = createStageDir(context, "prepared-${safeFileName(artifact.name)}")
        unzip(source, extractDir)
        val manifest = File(extractDir, BUNDLE_MANIFEST_FILE_NAME)
        val manifestText = manifest.takeIf { it.isFile }?.readText()
        val payloadName = parseBundledPayloadName(manifestText)
        val payload = payloadName?.let { File(extractDir, it).takeIf(File::isFile) }
            ?: throw IllegalStateException("Bundled artifact missing payload: ${artifact.name}")
        return PreparedDownloadedArtifact(
            file = payload,
            cleanupDir = extractDir,
            dependencyModules = resolveAuxiliaryArtifacts(source).moduleFiles,
            dependencyApps = resolveAuxiliaryArtifacts(source).appFiles,
            resolvedType = classifyDownloadedFile(payload)
        )
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

    internal data class ByteRange(val start: Long, val end: Long) {
        val length: Long get() = end - start + 1L
    }

    internal data class RangeInfo(val start: Long, val end: Long, val total: Long)

    internal suspend fun downloadUrlToFile(
        url: String,
        headers: Map<String, String>,
        destination: File,
        knownSize: Long,
        threadCount: Int,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        downloadSlots.withPermit {
            destination.parentFile?.mkdirs()
            val safeThreads = threadCount.coerceIn(1, 64)
            var completed = false
            if (safeThreads > 1) {
                completed = try {
                    downloadRanged(url, headers, destination, knownSize, safeThreads, onProgress)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    false
                }
            }
            if (!completed) {
                destination.delete()
                downloadSingle(url, headers, destination, knownSize, onProgress)
            }
        }
    }

    private suspend fun downloadRanged(
        url: String,
        headers: Map<String, String>,
        destination: File,
        knownSize: Long,
        threadCount: Int,
        onProgress: (Int) -> Unit
    ): Boolean = coroutineScope {
        val probeRequest = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }
            .header("Range", "bytes=0-0").build()
        client.newCall(probeRequest).execute().use { response ->
            if (response.code != 206) return@coroutineScope false
            val info = parseContentRange(response.header("Content-Range")) ?: return@coroutineScope false
            if (info.start != 0L || info.end != 0L || info.total <= 1L) return@coroutineScope false
            if (knownSize > 0L && knownSize != info.total) return@coroutineScope false
            val total = info.total
            val partCount = threadCount.toLong().coerceAtMost(total).toInt()
            val ranges = planByteRanges(total, partCount)
            val tempDir = File(destination.parentFile, ".${destination.name}.parts-${System.nanoTime()}").apply { mkdirs() }
            val written = AtomicLong(0L)
            try {
                val parts = ranges.mapIndexed { index, range ->
                    async(Dispatchers.IO) {
                        val part = File(tempDir, "$index.part")
                        var last: Throwable? = null
                        repeat(3) { attempt ->
                            try {
                                downloadRangePart(url, headers, range, part)
                                written.addAndGet(range.length)
                                onProgress((written.get() * 100L / total).toInt().coerceIn(0, 100))
                                return@async part
                            } catch (error: Throwable) {
                                last = error
                                if (attempt < 2) delay(250L * (1L shl attempt))
                            }
                        }
                        throw last ?: IllegalStateException("Range download failed")
                    }
                }.awaitAll()
                val merged = File(tempDir, "merged")
                FileOutputStream(merged).use { output ->
                    parts.forEach { part -> part.inputStream().use { it.copyTo(output) } }
                }
                if (merged.length() != total) return@coroutineScope false
                if (destination.exists()) destination.delete()
                if (!merged.renameTo(destination)) merged.copyTo(destination, overwrite = true)
                onProgress(100)
                true
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }

    private fun downloadRangePart(url: String, headers: Map<String, String>, range: ByteRange, destination: File) {
        val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }
            .header("Range", "bytes=${range.start}-${range.end}").build()
        client.newCall(request).execute().use { response ->
            if (response.code != 206) error("Range response ${response.code}")
            val info = parseContentRange(response.header("Content-Range"))
                ?: error("Missing Content-Range")
            if (info.start != range.start || info.end != range.end || info.total < range.end + 1L) {
                error("Invalid Content-Range")
            }
            val body = response.body ?: error("Empty response")
            FileOutputStream(destination).use { output -> body.byteStream().use { it.copyTo(output) } }
            if (destination.length() != range.length) error("Short range response")
        }
    }

    private suspend fun downloadSingle(
        url: String,
        headers: Map<String, String>,
        destination: File,
        knownSize: Long,
        onProgress: (Int) -> Unit
    ) {
        val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("Empty response")
            writeStreamToFile(body.byteStream(), destination, (if (knownSize > 0) knownSize else body.contentLength()).coerceAtLeast(1L), onProgress)
        }
    }

    internal fun planByteRanges(total: Long, partCount: Int): List<ByteRange> =
        (0 until partCount.coerceAtMost(total.coerceAtLeast(1L).toInt())).map { index ->
            val start = total * index / partCount
            val end = total * (index + 1) / partCount - 1L
            ByteRange(start, end)
        }

    internal fun parseContentRange(value: String?): RangeInfo? {
        val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(value?.trim().orEmpty()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].toLongOrNull() ?: return null
        return if (start <= end && end < total) RangeInfo(start, end, total) else null
    }

    private fun safeFileName(value: String): String =
        value.replace(Regex("""[^A-Za-z0-9._ -]"""), "_")
            .trim()
            .ifBlank { "artifact" }

    private fun normalizedArtifactName(name: String): String {
        val lower = name.lowercase(Locale.ROOT)
        return when {
            lower.endsWith(".bundle.zip") -> name.dropLast(".bundle.zip".length)
            else -> name
        }
    }

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

    /**
     * Resolve storage directory for direct asset downloads.
     *
     * If [storageSubdirectory] is null or blank, the downloads root is returned (i.e. files will be
     * placed directly into the Downloads root). In that case the returned DirectAssetStorage
     * will have downloadedFileIsRoot = true and DownloadUtils will avoid deleting that root.
     *
     * Use a non-empty storageSubdirectory to isolate asset files under a subdirectory.
     */
    private fun resolveDirectAssetStorage(
        downloadDirectoryPath: String? = null,
        storageSubdirectory: String? = "prebuilt-gki",
        preserveDownloadedZip: Boolean = false,
        bundleWithNotices: Boolean = false,
    ): DirectAssetStorage? {
        val downloadsRoot = resolveDownloadsRoot(downloadDirectoryPath) ?: return null
        val assetDir = storageSubdirectory
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { subdirectory -> File(downloadsRoot, subdirectory).apply { mkdirs() } }
            ?: downloadsRoot
        val usableAssetDir = assetDir.takeIf { it.exists() || it.mkdirs() }?.takeIf { it.isDirectory && it.canWrite() }
            ?: return null
        if (storageSubdirectory.isNullOrBlank()) {
            Log.w("DownloadUtils", "Saving direct asset into Downloads root; deletion of this directory will be suppressed.")
        }
        return DirectAssetStorage(
            assetDir = usableAssetDir,
            preserveDownloadedZip = preserveDownloadedZip,
            bundleWithNotices = bundleWithNotices,
            downloadedFileIsRoot = usableAssetDir == downloadsRoot
        )
    }

    private fun downloadHttpErrorMessage(context: Context, code: Int): String =
        context.getString(R.string.download_http_failed, code)

    private fun downloadExceptionMessage(context: Context, error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.download_unknown_error)

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

        val noticeDir = File(stagingRoot, NOTICE_STAGING_DIR_NAME).apply { mkdirs() }
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
        context: Context,
        bundleRootDir: File,
        candidates: List<File>,
        notices: NoticeFiles,
        signingVerificationEnabled: Boolean = true,
        signingPublicKeyPem: String? = null,
        bundledDependencies: List<File> = emptyList(),
        bundledCompanionApps: List<File> = emptyList()
    ): List<LocalDownloadEntry> {
        return candidates.mapIndexed { index, candidate ->
            if (candidate.name.lowercase(Locale.ROOT).endsWith(".bundle.zip")) {
                val entry = persistBundledDownloadEntry(
                    context = context,
                    bundleRootDir = bundleRootDir,
                    downloadedFile = candidate,
                    signingVerificationEnabled = signingVerificationEnabled,
                    signingPublicKeyPem = signingPublicKeyPem
                )
                val resolvedType = resolveBundlePayloadType(entry.file, entry.type)
                val dependenciesForPayload = if (resolvedType in setOf(ArtifactType.KERNEL_IMG, ArtifactType.ANYKERNEL3)) {
                    bundledDependencies
                } else {
                    emptyList()
                }
                val appsForPayload = if (resolvedType in setOf(ArtifactType.KERNEL_IMG, ArtifactType.ANYKERNEL3)) {
                    bundledCompanionApps
                } else {
                    emptyList()
                }
                persistAuxiliaryArtifacts(
                    bundleFile = entry.file,
                    dependencyModules = dependenciesForPayload,
                    dependencyApps = appsForPayload
                )
                return@mapIndexed entry
            }
            val dirName = safeFileName(candidate.name).ifBlank { "artifact-${index + 1}" }
            val candidateDir = File(bundleRootDir, dirName).apply {
                if (exists()) deleteRecursively()
                mkdirs()
            }
            val bundleFile = File(candidateDir, "${safeFileName(candidate.name)}.bundle.zip")
            val dependenciesForPayload = if (classifyDownloadedFile(candidate) in setOf(ArtifactType.KERNEL_IMG, ArtifactType.ANYKERNEL3)) {
                bundledDependencies
            } else {
                emptyList()
            }
            val appsForPayload = if (classifyDownloadedFile(candidate) in setOf(ArtifactType.KERNEL_IMG, ArtifactType.ANYKERNEL3)) {
                bundledCompanionApps
            } else {
                emptyList()
            }
            createNoticeBundle(bundleFile, candidate, notices, dependenciesForPayload, appsForPayload)
            val type = classifyDownloadedFile(candidate)
            LocalDownloadEntry(
                displayName = candidate.name,
                file = bundleFile,
                type = type,
                verified = false,
                verificationSummary = if (ArtifactVerification.requiresTrustedBundle(type)) {
                    if (signingVerificationEnabled) {
                        context.getString(R.string.flash_bundle_legacy_requires_confirmation)
                    } else {
                        context.getString(R.string.flash_bundle_verification_disabled)
                    }
                } else {
                    null
                }
            )
        }
    }

    private fun persistBundledDownloadEntry(
        context: Context,
        bundleRootDir: File,
        downloadedFile: File,
        signingVerificationEnabled: Boolean = true,
        signingPublicKeyPem: String? = null
    ): LocalDownloadEntry {
        val displayName = normalizedArtifactName(downloadedFile.name)
        val dirName = safeFileName(displayName).ifBlank { "artifact-bundle" }
        val candidateDir = File(bundleRootDir, dirName).apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        val persistedBundle = File(candidateDir, downloadedFile.name)
        downloadedFile.copyTo(persistedBundle, overwrite = true)
        val type = classifyDownloadedFile(persistedBundle)
        val verification = inspectBundleVerification(
            context = context,
            bundleFile = persistedBundle,
            type = type,
            signingVerificationEnabled = signingVerificationEnabled,
            signingPublicKeyPem = signingPublicKeyPem
        )
        return LocalDownloadEntry(
            displayName = displayName,
            file = persistedBundle,
            type = type,
            verified = verification?.verified == true,
            verificationSummary = verification?.summary,
            manifest = if (verification?.verified == true) {
                ArtifactVerification.readBundleManifest(persistedBundle)
            } else {
                null
            }
        )
    }

    private fun persistAuxiliaryArtifacts(
        bundleFile: File,
        dependencyModules: List<File>,
        dependencyApps: List<File>
    ) {
        val sidecarRoot = File(bundleFile.parentFile ?: return, "${bundleFile.name}.deps").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        val modulesDir = File(sidecarRoot, "magisk-modules").apply { mkdirs() }
        val appsDir = File(sidecarRoot, "companion-apps").apply { mkdirs() }
        dependencyModules.forEach { module ->
            module.copyTo(File(modulesDir, module.name), overwrite = true)
        }
        dependencyApps.forEach { app ->
            app.copyTo(File(appsDir, app.name), overwrite = true)
        }
    }

    private fun resolveAuxiliaryArtifacts(bundleFile: File): AuxiliaryArtifacts {
        val sidecarRoot = File(bundleFile.parentFile ?: return AuxiliaryArtifacts(), "${bundleFile.name}.deps")
        if (!sidecarRoot.isDirectory) return AuxiliaryArtifacts()
        val modules = File(sidecarRoot, "magisk-modules")
            .takeIf(File::isDirectory)
            ?.listFiles()
            ?.filter(File::isFile)
            ?.sortedBy { it.name }
            .orEmpty()
        val apps = File(sidecarRoot, "companion-apps")
            .takeIf(File::isDirectory)
            ?.listFiles()
            ?.filter(File::isFile)
            ?.sortedBy { it.name }
            .orEmpty()
        return AuxiliaryArtifacts(
            moduleFiles = modules,
            appFiles = apps
        )
    }

    private fun resolveBundlePayloadType(bundleFile: File, fallback: ArtifactType): ArtifactType {
        val type = classifyDownloadedFile(bundleFile)
        if (type != fallback) return type
        val manifest = runCatching {
            ZipFile(bundleFile).use { zip ->
                zip.getEntry(SIGNED_BUNDLE_MANIFEST_FILE_NAME)?.let { entry ->
                    zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
                } ?: zip.getEntry(BUNDLE_MANIFEST_FILE_NAME)?.let { entry ->
                    zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
                }
            }
        }.getOrNull()
        val payloadName = parseBundledPayloadName(manifest)
        return payloadName?.let { classifyDownloadedFile(File(it)) } ?: fallback
    }

    private fun createNoticeBundle(
        bundleFile: File,
        payload: File,
        notices: NoticeFiles,
        bundledDependencies: List<File> = emptyList(),
        bundledCompanionApps: List<File> = emptyList()
    ) {
        ZipOutputStream(FileOutputStream(bundleFile)).use { zip ->
            val addedEntryNames = mutableSetOf<String>()
            fun addUniqueEntry(file: File, entryName: String) {
                val key = entryName.lowercase(Locale.ROOT)
                if (!addedEntryNames.add(key)) return
                addFileToZip(zip, file, entryName)
            }

            zip.putNextEntry(ZipEntry(BUNDLE_MANIFEST_FILE_NAME))
            val manifestLines = buildString {
                append("payload=${payload.name}\n")
                bundledDependencies.forEach { dependency ->
                    append("dependency=${BUNDLE_DEPENDENCY_DIR_NAME}/${dependency.name}\n")
                }
                bundledCompanionApps.forEach { dependency ->
                    append("companion_app=companion-apps/${dependency.name}\n")
                }
            }
            zip.write(manifestLines.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            addedEntryNames.add(BUNDLE_MANIFEST_FILE_NAME.lowercase(Locale.ROOT))

            addUniqueEntry(payload, payload.name)
            addUniqueEntry(notices.license, LICENSE_FILE_NAME)
            addUniqueEntry(notices.thirdPartyNotices, THIRD_PARTY_NOTICES_FILE_NAME)
            bundledDependencies.forEach { dependency ->
                addUniqueEntry(dependency, "${BUNDLE_DEPENDENCY_DIR_NAME}/${dependency.name}")
            }
            bundledCompanionApps.forEach { dependency ->
                addUniqueEntry(dependency, "companion-apps/${dependency.name}")
            }
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

    private fun looksLikeLegacyNoticeBundle(file: File): Boolean {
        if (!looksLikeNoticeBundle(file)) return false
        return runCatching {
            ZipFile(file).use { zip ->
                zip.getEntry(SIGNED_BUNDLE_MANIFEST_FILE_NAME) == null &&
                    zip.getEntry(SIGNED_BUNDLE_SIGNATURE_FILE_NAME) == null
            }
        }.getOrDefault(false)
    }

    private fun looksLikeSignedBundle(file: File): Boolean {
        if (!file.isFile || !file.extension.equals("zip", ignoreCase = true)) return false
        return runCatching {
            ZipFile(file).use { zip ->
                zip.getEntry(SIGNED_BUNDLE_MANIFEST_FILE_NAME) != null
            }
        }.getOrDefault(false)
    }

    private fun inspectBundleVerification(
        context: Context,
        bundleFile: File,
        type: ArtifactType,
        signingVerificationEnabled: Boolean,
        signingPublicKeyPem: String?
    ): BundleVerificationState? {
        val manifest = ArtifactVerification.readBundleManifest(bundleFile)
        val manifestType = manifest?.artifactType
            ?.let { runCatching { ArtifactType.valueOf(it) }.getOrNull() }
        val effectiveType = manifestType ?: type
        if (!ArtifactVerification.requiresTrustedBundle(effectiveType, manifest) && !looksLikeSignedBundle(bundleFile)) return null
        if (!signingVerificationEnabled) {
            return BundleVerificationState(
                verified = false,
                summary = context.getString(R.string.flash_bundle_verification_disabled)
            )
        }
        if (looksLikeLegacyNoticeBundle(bundleFile)) {
            return BundleVerificationState(
                verified = false,
                summary = context.getString(R.string.flash_bundle_legacy_requires_confirmation)
            )
        }
        if (!signingPublicKeyPem.isNullOrBlank()) {
            val result = ArtifactVerification.verifyBundleFile(bundleFile, effectiveType, signingPublicKeyPem)
            return BundleVerificationState(
                verified = result.success,
                summary = result.message
            )
        }
        return BundleVerificationState(
            verified = false,
            summary = if (looksLikeSignedBundle(bundleFile)) {
                context.getString(R.string.flash_bundle_unverified_requires_confirmation)
            } else {
                context.getString(R.string.flash_bundle_legacy_requires_confirmation)
            }
        )
    }


    private fun parseBundledPayloadName(manifest: String?): String? =
        manifest
            ?.lineSequence()
            ?.firstOrNull { it.startsWith("payload=") }
            ?.substringAfter('=')
            ?.trim()
            ?.ifBlank { null }

    fun precheckFlashSecurity(
        context: Context,
        artifact: DownloadedArtifact,
        signingVerificationEnabled: Boolean,
    ): FlashSecurityPrompt? {
        if (!signingVerificationEnabled) return null
        val source = File(artifact.filePath)
        if (!source.isFile) return null
        val manifest = ArtifactVerification.readBundleManifest(source)
        val manifestType = manifest?.artifactType
            ?.let { runCatching { ArtifactType.valueOf(it) }.getOrNull() }
        val effectiveType = manifestType ?: artifact.type
        if (!ArtifactVerification.requiresTrustedBundle(effectiveType, manifest) &&
            !looksLikeSignedBundle(source) &&
            !looksLikeLegacyNoticeBundle(source)
        ) {
            return null
        }
        if (looksLikeLegacyNoticeBundle(source)) {
            return FlashSecurityPrompt(
                kind = FlashSecurityIssueKind.MISSING_SIGNATURE,
                message = context.getString(R.string.flash_bundle_missing_signature_message)
            )
        }
        val signingPublicKey = PreferencesRepository(context).readForkArtifactSigningPublicKeyBlocking()
        val verification = ArtifactVerification.verifyBundleFile(
            source,
            effectiveType,
            ForkSigningManager.publicKeyPemFromStoredValue(signingPublicKey)
        )
        if (verification.success) return null
        val kind = when (verification.failureReason) {
            BundleVerificationFailureReason.MISSING_SIGNATURE -> FlashSecurityIssueKind.MISSING_SIGNATURE
            BundleVerificationFailureReason.SIGNATURE_MISMATCH -> FlashSecurityIssueKind.SIGNATURE_MISMATCH
            BundleVerificationFailureReason.MISSING_PUBLIC_KEY -> FlashSecurityIssueKind.MISSING_PUBLIC_KEY
            else -> FlashSecurityIssueKind.OTHER
        }
        val message = when (kind) {
            FlashSecurityIssueKind.MISSING_SIGNATURE -> context.getString(R.string.flash_bundle_missing_signature_message)
            FlashSecurityIssueKind.SIGNATURE_MISMATCH -> context.getString(R.string.flash_bundle_signature_mismatch_message)
            FlashSecurityIssueKind.MISSING_PUBLIC_KEY -> context.getString(R.string.flash_bundle_missing_public_key_message)
            FlashSecurityIssueKind.OTHER -> verification.message
        }
        return FlashSecurityPrompt(kind = kind, message = message)
    }

    private fun parseBundledDependencyNames(manifest: String?): List<String> =
        manifest
            ?.lineSequence()
            ?.mapNotNull { line ->
                line.takeIf { it.startsWith("dependency=") }
                    ?.substringAfter('=')
                    ?.trim()
                    ?.ifBlank { null }
            }
            .orEmpty()
            .toList()

    private fun parseBundledCompanionAppNames(manifest: String?): List<String> =
        manifest
            ?.lineSequence()
            ?.mapNotNull { line ->
                line.takeIf { it.startsWith("companion_app=") }
                    ?.substringAfter('=')
                    ?.trim()
                    ?.ifBlank { null }
            }
            .orEmpty()
            .toList()

    private suspend fun resolveBundledMagiskModules(
        stagingRoot: File,
        token: String?
    ): List<File> {
        val manifestFile = stagingRoot.walkTopDown()
            .firstOrNull { it.isFile && it.name.equals(FLASH_DEPENDENCIES_FILE_NAME, ignoreCase = true) }
            ?: return emptyList()
        val raw = runCatching { manifestFile.readText(Charsets.UTF_8) }.getOrDefault("")
        if (raw.isBlank()) return emptyList()
        val dependencies = parseBundledMagiskDependencyManifest(raw)
        if (dependencies.isEmpty()) return emptyList()
        val dependencyDir = File(stagingRoot, "$NOTICE_STAGING_DIR_NAME/$BUNDLE_DEPENDENCY_DIR_NAME").apply {
            mkdirs()
        }
        return dependencies.mapNotNull { dependency ->
            val fromUrl = dependency.downloadUrl.substringAfterLast('/').ifBlank { dependency.name }
            val safeName = safeFileName(fromUrl).ifBlank { "magisk-module.zip" }
            val target = File(
                dependencyDir,
                if (safeName.endsWith(".zip", ignoreCase = true)) safeName else "$safeName.zip"
            )
            if (target.exists() && target.length() > 0L) {
                target
            } else if (downloadAuxiliaryFile(dependency.downloadUrl, target, token)) {
                target
            } else {
                throw IllegalStateException("Failed to download bundled Magisk dependency: ${dependency.name}")
            }
        }
    }

    private fun parseBundledMagiskDependencyManifest(raw: String): List<BundledMagiskModuleDependency> {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val items = root.optJSONArray("magiskModules") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val downloadUrl = item.optString("downloadUrl").trim()
                if (downloadUrl.isBlank()) continue
                add(
                    BundledMagiskModuleDependency(
                        name = item.optString("name").trim().ifBlank { "magisk-module" },
                        downloadUrl = downloadUrl
                    )
                )
            }
        }.distinctBy { it.downloadUrl.lowercase(Locale.ROOT) }
    }

    private fun parseBundledCompanionDependencyManifest(raw: String): List<BundledCompanionAppDependency> {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val items = root.optJSONArray("companionApps") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val downloadUrl = item.optString("downloadUrl").trim()
                if (downloadUrl.isBlank()) continue
                add(
                    BundledCompanionAppDependency(
                        name = item.optString("displayName").trim().ifBlank { "companion-app" },
                        downloadUrl = downloadUrl
                    )
                )
            }
        }.distinctBy { it.downloadUrl.lowercase(Locale.ROOT) }
    }

    private suspend fun resolveBundledCompanionApps(
        stagingRoot: File,
        token: String?
    ): List<File> {
        val manifestFile = stagingRoot.walkTopDown()
            .firstOrNull { it.isFile && it.name.equals(FLASH_DEPENDENCIES_FILE_NAME, ignoreCase = true) }
            ?: return emptyList()
        val raw = runCatching { manifestFile.readText(Charsets.UTF_8) }.getOrDefault("")
        if (raw.isBlank()) return emptyList()
        val dependencies = parseBundledCompanionDependencyManifest(raw)
        if (dependencies.isEmpty()) return emptyList()
        val dependencyDir = File(stagingRoot, "$NOTICE_STAGING_DIR_NAME/companion-apps").apply {
            mkdirs()
        }
        return dependencies.mapNotNull { dependency ->
            val fromUrl = dependency.downloadUrl.substringAfterLast('/').ifBlank { dependency.name }
            val safeName = safeFileName(fromUrl).ifBlank { "companion.apk" }
            val target = File(
                dependencyDir,
                if (safeName.endsWith(".apk", ignoreCase = true)) safeName else "$safeName.apk"
            )
            if (target.exists() && target.length() > 0L) {
                target
            } else if (downloadAuxiliaryFile(dependency.downloadUrl, target, token)) {
                target
            } else {
                throw IllegalStateException("Failed to download bundled companion app: ${dependency.name}")
            }
        }
    }

    private suspend fun downloadAuxiliaryFile(
        url: String,
        destination: File,
        token: String?
    ): Boolean {
        destination.parentFile?.mkdirs()
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
        return try {
            call.execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body ?: return false
                body.byteStream().use { input ->
                    writeStreamToFile(input, destination, body.contentLength().coerceAtLeast(1L)) {}
                }
                true
            }
        } finally {
            cancellationHandle.dispose()
        }
    }

    internal fun collectArtifactPayloadFiles(outDir: File): List<File> = collectCandidateFiles(outDir)

    internal fun selectAppUpdateApk(candidates: List<File>, preferredLine: String): File? {
        val preferDev = normalizeAppUpdateLine(preferredLine) == APP_UPDATE_LINE_DEV
        return candidates
            .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            .maxWithOrNull(
                compareBy<File> { appUpdateApkScore(it.name, preferDev) }
                    .thenBy { it.name.length }
            )
    }

    private fun collectCandidateFiles(outDir: File): List<File> {
        val noticeStagingRoot = File(outDir, NOTICE_STAGING_DIR_NAME).absolutePath + File.separator
        val files = outDir.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") }
            .filter { !isBundledNoticeFileName(it.name) }
            .filter { !it.absolutePath.startsWith(noticeStagingRoot) }
            .toList()

        val candidates = files.filter { file ->
            if (ArtifactVerification.readBundleManifest(file)?.payloadKind == "KERNEL_IMAGE_SET") {
                return@filter true
            }
            when (classifyDownloadedFile(file)) {
                ArtifactType.KERNEL_PACKAGE,
                ArtifactType.KERNEL_IMG,
                ArtifactType.ANYKERNEL3,
                ArtifactType.ABK_MANAGER,
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
                    ArtifactType.ABK_MANAGER,
                    ArtifactType.KSU_MANAGER -> 3
                    ArtifactType.SUSFS_MODULE -> 4
                    ArtifactType.OTHER -> 5
                }
            }.thenBy { it.name }
        )
    }

    private fun appUpdateApkScore(name: String, preferDev: Boolean): Int {
        val lower = name.lowercase(Locale.ROOT)
        var score = 0
        if ("unsigned" in lower) score -= 1000
        if ("release" in lower) score += 100
        if ("debug" in lower) score -= 40
        if ("abk" in lower || "app" in lower) score += 20
        if (preferDev) {
            score += if ("dev" in lower) 80 else -80
        } else {
            score += if ("dev" in lower) -80 else 10
        }
        return score
    }

    private fun classifyDownloadedFile(file: File): ArtifactType {
        ArtifactVerification.readBundleManifest(file)?.artifactType
            ?.let { runCatching { ArtifactType.valueOf(it) }.getOrNull() }
            ?.let { return it }
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
