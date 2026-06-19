package com.abk.kernel.utils

import com.abk.kernel.data.model.ArtifactType
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.util.Base64
import java.util.Locale
import java.util.zip.ZipFile

data class SignedBundleManifest(
    @SerializedName("schema") val schema: Int = 1,
    @SerializedName("bundle_name") val bundleName: String,
    @SerializedName("artifact_type") val artifactType: String,
    @SerializedName("run_id") val runId: Long,
    @SerializedName("payload_name") val payloadName: String,
    @SerializedName("payload_sha256") val payloadSha256: String,
    @SerializedName("payload_size_bytes") val payloadSizeBytes: Long,
    @SerializedName("created_at") val createdAt: String? = null
)

data class BundleVerificationResult(
    val manifest: SignedBundleManifest,
    val success: Boolean,
    val message: String
)

object ArtifactVerification {
    const val MANIFEST_FILE_NAME: String = "ABK_BUNDLE_MANIFEST.json"
    const val SIGNATURE_FILE_NAME: String = "ABK_BUNDLE_MANIFEST.sig"

    private val gson = Gson()

    fun requiresTrustedBundle(type: ArtifactType): Boolean = when (type) {
        ArtifactType.KERNEL_IMG,
        ArtifactType.ANYKERNEL3 -> true
        else -> false
    }

    fun readBundleManifest(bundleFile: File): SignedBundleManifest? = runCatching {
        ZipFile(bundleFile).use { zip ->
            zip.getEntry(MANIFEST_FILE_NAME)?.let { manifestEntry ->
                gson.fromJson(
                    zip.getInputStream(manifestEntry).use { String(it.readBytes(), Charsets.UTF_8) },
                    SignedBundleManifest::class.java
                )
            }
        }
    }.getOrNull()

    fun verifyBundleFile(
        bundleFile: File,
        expectedType: ArtifactType? = null,
        publicKeyPem: String? = null
    ): BundleVerificationResult {
        if (!bundleFile.isFile || !bundleFile.name.lowercase(Locale.ROOT).endsWith(".bundle.zip")) {
            return failureFor(bundleFile.name, expectedType, "Trusted artifact must be a signed .bundle.zip")
        }
        val publicKey = parsePublicKey(publicKeyPem)
            ?: return failureFor(bundleFile.name, expectedType, "Missing fork signing public key")
        return runCatching {
            ZipFile(bundleFile).use { zip ->
                val manifestEntry = zip.getEntry(MANIFEST_FILE_NAME)
                    ?: return failureFor(bundleFile.name, expectedType, "Missing $MANIFEST_FILE_NAME")
                val signatureEntry = zip.getEntry(SIGNATURE_FILE_NAME)
                    ?: return failureFor(bundleFile.name, expectedType, "Missing $SIGNATURE_FILE_NAME")
                val manifestBytes = zip.getInputStream(manifestEntry).use { it.readBytes() }
                val signatureBytes = zip.getInputStream(signatureEntry).use { it.readBytes() }
                val manifest = gson.fromJson(String(manifestBytes, Charsets.UTF_8), SignedBundleManifest::class.java)
                if (!verifyManifestSignature(publicKey, manifestBytes, signatureBytes)) {
                    return BundleVerificationResult(manifest, false, "Artifact signature verification failed")
                }
                val manifestType = runCatching { ArtifactType.valueOf(manifest.artifactType) }.getOrNull()
                if (expectedType != null && manifestType != expectedType) {
                    return BundleVerificationResult(manifest, false, "Artifact type mismatch")
                }
                if (manifest.bundleName != bundleFile.name) {
                    return BundleVerificationResult(manifest, false, "Bundle filename mismatch")
                }
                val payloadEntry = zip.getEntry(manifest.payloadName)
                    ?: return BundleVerificationResult(manifest, false, "Missing payload entry ${manifest.payloadName}")
                val payloadBytes = zip.getInputStream(payloadEntry).use { it.readBytes() }
                if (payloadBytes.size.toLong() != manifest.payloadSizeBytes) {
                    return BundleVerificationResult(manifest, false, "Payload size mismatch")
                }
                if (sha256(payloadBytes) != normalizeDigest(manifest.payloadSha256)) {
                    return BundleVerificationResult(manifest, false, "Payload digest mismatch")
                }
                BundleVerificationResult(manifest, true, "Verified ${bundleFile.name}")
            }
        }.getOrElse { error ->
            failureFor(bundleFile.name, expectedType, error.message ?: error::class.java.simpleName)
        }
    }

    fun normalizeDigest(value: String): String = value.trim().lowercase(Locale.ROOT)

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
        message: String
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
        message = message
    )
}

private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
