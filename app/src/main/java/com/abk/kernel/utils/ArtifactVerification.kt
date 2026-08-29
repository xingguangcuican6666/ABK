package com.abk.kernel.utils

import com.abk.kernel.data.model.ArtifactType
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.util.Base64
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

data class SignedBundleManifest(
    @SerializedName("schema") val schema: Int = 1,
    @SerializedName("bundle_name") val bundleName: String,
    @SerializedName("artifact_type") val artifactType: String,
    @SerializedName("run_id") val runId: Long,
    @SerializedName("payload_name") val payloadName: String,
    @SerializedName("payload_sha256") val payloadSha256: String,
    @SerializedName("payload_size_bytes") val payloadSizeBytes: Long,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("payload_kind") val payloadKind: String? = null,
    @SerializedName("kernel_source") val kernelSource: KernelSourceManifest? = null,
    @SerializedName("feature_status") val featureStatus: FeatureStatusManifest? = null,
    @SerializedName("client_notice") val clientNotice: ClientNoticeManifest? = null
)

data class KernelSourceManifest(
    val mode: String? = null,
    val url: String? = null,
    val access: String? = null,
    @SerializedName("requested_ref") val requestedRef: String? = null,
    @SerializedName("resolved_commit") val resolvedCommit: String? = null,
    @SerializedName("kernel_version") val kernelVersion: String? = null,
    @SerializedName("android_version") val androidVersion: String? = null,
    @SerializedName("toolchain_patch_level") val toolchainPatchLevel: String? = null,
    @SerializedName("device_label") val deviceLabel: String? = null,
    val defconfigs: List<String> = emptyList()
)

data class FeatureStatusManifest(
    val requested: Map<String, Any?> = emptyMap(),
    val effective: Map<String, Any?> = emptyMap(),
    val skipped: List<SkippedFeatureManifest> = emptyList()
)

data class SkippedFeatureManifest(
    val id: String = "",
    @SerializedName("reason_code") val reasonCode: String = "",
    val message: String = ""
)

data class ClientNoticeManifest(
    val type: String? = null,
    val version: Int = 1,
    val capability: String? = null,
    val severity: String? = null,
    @SerializedName("review_before_flash") val reviewBeforeFlash: Boolean = false
)

data class BundleVerificationResult(
    val manifest: SignedBundleManifest,
    val success: Boolean,
    val message: String,
    val failureReason: BundleVerificationFailureReason = BundleVerificationFailureReason.OTHER,
)

enum class BundleVerificationFailureReason {
    NONE,
    MISSING_PUBLIC_KEY,
    MISSING_SIGNATURE,
    SIGNATURE_MISMATCH,
    OTHER,
}

object ArtifactVerification {
    const val MANIFEST_FILE_NAME: String = "ABK_BUNDLE_MANIFEST.json"
    const val SIGNATURE_FILE_NAME: String = "ABK_BUNDLE_MANIFEST.sig"

    // Mirrors the CLI limits in cli/abk.py so both verifiers refuse the same
    // oversized input instead of allocating it. These bound the read itself:
    // the manifest and signature are read before the signature is checked, so
    // an unbounded read here is reachable without any valid key.
    const val MAX_MANIFEST_SIZE: Long = 1L * 1024 * 1024
    const val MAX_SIGNATURE_SIZE: Long = 64L * 1024
    const val MAX_PAYLOAD_SIZE: Long = 8L * 1024 * 1024 * 1024

    private const val COPY_BUFFER_SIZE = 64 * 1024

    private val gson = Gson()

    fun requiresTrustedBundle(type: ArtifactType): Boolean = when (type) {
        ArtifactType.KERNEL_IMG,
        ArtifactType.ANYKERNEL3 -> true
        else -> false
    }

    fun requiresTrustedBundle(type: ArtifactType, manifest: SignedBundleManifest?): Boolean =
        requiresTrustedBundle(type) || manifest?.payloadKind == "KERNEL_IMAGE_SET"

    fun readBundleManifest(bundleFile: File): SignedBundleManifest? = runCatching {
        ZipFile(bundleFile).use { zip ->
            zip.getEntry(MANIFEST_FILE_NAME)?.let { manifestEntry ->
                readEntryAtMost(zip, manifestEntry, MAX_MANIFEST_SIZE)?.let { manifestBytes ->
                    gson.fromJson(
                        String(manifestBytes, Charsets.UTF_8),
                        SignedBundleManifest::class.java
                    )
                }
            }
        }
    }.getOrNull()

    fun verifyBundleFile(
        bundleFile: File,
        expectedType: ArtifactType? = null,
        publicKeyPem: String? = null,
        expectedRunId: Long? = null
    ): BundleVerificationResult {
        if (!bundleFile.isFile || !bundleFile.name.lowercase(Locale.ROOT).endsWith(".bundle.zip")) {
            return failureFor(
                bundleFile.name,
                expectedType,
                "Trusted artifact must be a signed .bundle.zip",
                BundleVerificationFailureReason.MISSING_SIGNATURE
            )
        }
        val publicKey = parsePublicKey(publicKeyPem)
            ?: return failureFor(
                bundleFile.name,
                expectedType,
                "Missing fork signing public key",
                BundleVerificationFailureReason.MISSING_PUBLIC_KEY
            )
        return runCatching {
            ZipFile(bundleFile).use { zip ->
                val manifestEntry = zip.getEntry(MANIFEST_FILE_NAME)
                    ?: return failureFor(
                        bundleFile.name,
                        expectedType,
                        "Missing $MANIFEST_FILE_NAME",
                        BundleVerificationFailureReason.MISSING_SIGNATURE
                    )
                val signatureEntry = zip.getEntry(SIGNATURE_FILE_NAME)
                    ?: return failureFor(
                        bundleFile.name,
                        expectedType,
                        "Missing $SIGNATURE_FILE_NAME",
                        BundleVerificationFailureReason.MISSING_SIGNATURE
                    )
                val manifestBytes = readEntryAtMost(zip, manifestEntry, MAX_MANIFEST_SIZE)
                    ?: return failureFor(
                        bundleFile.name,
                        expectedType,
                        "$MANIFEST_FILE_NAME exceeds $MAX_MANIFEST_SIZE bytes",
                        BundleVerificationFailureReason.OTHER
                    )
                val signatureBytes = readEntryAtMost(zip, signatureEntry, MAX_SIGNATURE_SIZE)
                    ?: return failureFor(
                        bundleFile.name,
                        expectedType,
                        "$SIGNATURE_FILE_NAME exceeds $MAX_SIGNATURE_SIZE bytes",
                        BundleVerificationFailureReason.OTHER
                    )
                val manifest = gson.fromJson(String(manifestBytes, Charsets.UTF_8), SignedBundleManifest::class.java)
                if (!verifyManifestSignature(publicKey, manifestBytes, signatureBytes)) {
                    return BundleVerificationResult(
                        manifest,
                        false,
                        "Artifact signature verification failed",
                        BundleVerificationFailureReason.SIGNATURE_MISMATCH
                    )
                }
                val manifestType = runCatching { ArtifactType.valueOf(manifest.artifactType) }.getOrNull()
                if (expectedType != null && manifestType != expectedType) {
                    return BundleVerificationResult(manifest, false, "Artifact type mismatch")
                }
                if (manifest.bundleName != bundleFile.name) {
                    return BundleVerificationResult(manifest, false, "Bundle filename mismatch")
                }
                // A signed bundle stays valid indefinitely unless it is bound to the run
                // that produced it, so an older signed build can otherwise be replayed in
                // place of a newer one. Sentinel ids (unknown, prebuilt) stay exempt.
                if (expectedRunId != null && expectedRunId > 0L && manifest.runId != expectedRunId) {
                    return BundleVerificationResult(manifest, false, "Bundle run id mismatch")
                }
                if (!isSafeZipMemberName(manifest.payloadName)) {
                    return BundleVerificationResult(manifest, false, "Unsafe payload name ${manifest.payloadName}")
                }
                if (manifest.payloadSizeBytes < 0L || manifest.payloadSizeBytes > MAX_PAYLOAD_SIZE) {
                    return BundleVerificationResult(manifest, false, "Payload size out of range")
                }
                val payloadEntry = zip.getEntry(manifest.payloadName)
                    ?: return BundleVerificationResult(manifest, false, "Missing payload entry ${manifest.payloadName}")
                val payload = digestEntry(zip, payloadEntry, MAX_PAYLOAD_SIZE)
                    ?: return BundleVerificationResult(manifest, false, "Payload exceeds $MAX_PAYLOAD_SIZE bytes")
                if (payload.size != manifest.payloadSizeBytes) {
                    return BundleVerificationResult(manifest, false, "Payload size mismatch")
                }
                if (payload.digest != normalizeDigest(manifest.payloadSha256)) {
                    return BundleVerificationResult(manifest, false, "Payload digest mismatch")
                }
                BundleVerificationResult(
                    manifest,
                    true,
                    "Verified ${bundleFile.name}",
                    BundleVerificationFailureReason.NONE
                )
            }
        }.getOrElse { error ->
            failureFor(
                bundleFile.name,
                expectedType,
                error.message ?: error::class.java.simpleName,
                BundleVerificationFailureReason.OTHER
            )
        }
    }

    fun normalizeDigest(value: String): String = value.trim().lowercase(Locale.ROOT)

    /** Rejects absolute paths, backslashes and traversal segments, as the CLI does. */
    internal fun isSafeZipMemberName(name: String): Boolean {
        if (name.isEmpty() || name.startsWith("/") || name.contains("\\")) return false
        return name.split("/").none { it.isEmpty() || it == "." || it == ".." }
    }

    private data class EntryDigest(val digest: String, val size: Long)

    /**
     * Reads at most [limit] bytes from [entry], returning null past that. The declared
     * size is only a hint, so the running total is what actually bounds the read.
     */
    private fun readEntryAtMost(zip: ZipFile, entry: ZipEntry, limit: Long): ByteArray? {
        if (entry.size > limit) return null
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        val collected = ByteArrayOutputStream()
        zip.getInputStream(entry).use { input ->
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) return null
                collected.write(buffer, 0, read)
            }
        }
        return collected.toByteArray()
    }

    /** Digests [entry] in chunks so the payload never has to fit in memory at once. */
    private fun digestEntry(zip: ZipFile, entry: ZipEntry, limit: Long): EntryDigest? {
        if (entry.size > limit) return null
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        var total = 0L
        zip.getInputStream(entry).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) return null
                digest.update(buffer, 0, read)
            }
        }
        return EntryDigest(
            digest = digest.digest().joinToString("") { "%02x".format(it) },
            size = total
        )
    }

    private fun verifyManifestSignature(publicKey: PublicKey, manifestBytes: ByteArray, signatureBytes: ByteArray): Boolean {
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(publicKey)
        verifier.update(manifestBytes)
        return verifier.verify(signatureBytes)
    }

    private fun parsePublicKey(publicKeyPem: String?): PublicKey? {
        val compact = publicKeyPem
            ?.lineSequence()
            ?.filterNot { it.startsWith("-----") }
            ?.joinToString("")
            ?.trim()
            .orEmpty()
        if (compact.isBlank()) return null
        val keyBytes = Base64.getDecoder().decode(compact)
        val spec = java.security.spec.X509EncodedKeySpec(keyBytes)
        return java.security.KeyFactory.getInstance("RSA").generatePublic(spec)
    }

    private fun failureFor(
        bundleName: String,
        expectedType: ArtifactType?,
        message: String,
        failureReason: BundleVerificationFailureReason,
    ): BundleVerificationResult = BundleVerificationResult(
        manifest = SignedBundleManifest(
            bundleName = bundleName,
            artifactType = expectedType?.name ?: ArtifactType.OTHER.name,
            runId = -1L,
            payloadName = "",
            payloadSha256 = "",
            payloadSizeBytes = 0L
        ),
        success = false,
        message = message,
        failureReason = failureReason,
    )
}
