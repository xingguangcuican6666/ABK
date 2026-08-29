package com.abk.kernel.utils

import com.abk.kernel.data.model.ArtifactType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

class ArtifactVerificationTest {

    companion object {
        // JUnit builds a fresh instance per test; keygen is slow enough to share.
        private val material by lazy { ForkSigningManager.generateSigningMaterial() }
    }

    @Test
    fun verifyBundleFile_acceptsWellFormedBundle() {
        val bundle = writeSignedBundle(runId = 42L)

        val result = ArtifactVerification.verifyBundleFile(
            bundle,
            ArtifactType.KERNEL_IMG,
            material.publicKeyPem
        )

        assertTrue(result.message, result.success)
    }

    @Test
    fun verifyBundleFile_rejectsRunIdMismatch() {
        val bundle = writeSignedBundle(runId = 42L)

        val result = ArtifactVerification.verifyBundleFile(
            bundle,
            ArtifactType.KERNEL_IMG,
            material.publicKeyPem,
            expectedRunId = 43L
        )

        assertFalse(result.success)
        assertEquals("Bundle run id mismatch", result.message)
    }

    @Test
    fun verifyBundleFile_acceptsMatchingRunId() {
        val bundle = writeSignedBundle(runId = 42L)

        val result = ArtifactVerification.verifyBundleFile(
            bundle,
            ArtifactType.KERNEL_IMG,
            material.publicKeyPem,
            expectedRunId = 42L
        )

        assertTrue(result.message, result.success)
    }

    /** Unknown (-1) and prebuilt (-2) sentinels must not start failing verification. */
    @Test
    fun verifyBundleFile_ignoresSentinelRunIds() {
        val bundle = writeSignedBundle(runId = 42L)

        listOf(-1L, -2L, 0L).forEach { sentinel ->
            val result = ArtifactVerification.verifyBundleFile(
                bundle,
                ArtifactType.KERNEL_IMG,
                material.publicKeyPem,
                expectedRunId = sentinel
            )
            assertTrue("sentinel $sentinel should not gate verification", result.success)
        }
    }

    /**
     * The manifest is read before the signature is checked, so an oversized one has to
     * be refused rather than allocated — no valid key is needed to reach this path.
     */
    @Test
    fun verifyBundleFile_rejectsOversizedManifest() {
        val oversized = ByteArray((ArtifactVerification.MAX_MANIFEST_SIZE + 1024L).toInt()) { ' '.code.toByte() }
        val bundle = File(tempDir(), "oversized.bundle.zip")
        ZipOutputStream(bundle.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(ArtifactVerification.MANIFEST_FILE_NAME))
            zip.write(oversized)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(ArtifactVerification.SIGNATURE_FILE_NAME))
            zip.write(ByteArray(16))
            zip.closeEntry()
        }

        val result = ArtifactVerification.verifyBundleFile(
            bundle,
            ArtifactType.KERNEL_IMG,
            material.publicKeyPem
        )

        assertFalse(result.success)
        assertTrue(result.message, result.message.contains("exceeds"))
    }

    @Test
    fun verifyBundleFile_rejectsOversizedSignature() {
        val bundle = writeSignedBundle(
            runId = 42L,
            signatureOverride = ByteArray((ArtifactVerification.MAX_SIGNATURE_SIZE + 1024L).toInt())
        )

        val result = ArtifactVerification.verifyBundleFile(
            bundle,
            ArtifactType.KERNEL_IMG,
            material.publicKeyPem
        )

        assertFalse(result.success)
        assertTrue(result.message, result.message.contains("exceeds"))
    }

    @Test
    fun verifyBundleFile_rejectsTamperedPayload() {
        val bundle = writeSignedBundle(runId = 42L, payloadOverride = "tampered".toByteArray())

        val result = ArtifactVerification.verifyBundleFile(
            bundle,
            ArtifactType.KERNEL_IMG,
            material.publicKeyPem
        )

        assertFalse(result.success)
    }

    @Test
    fun isSafeZipMemberName_rejectsTraversalAndAbsolutePaths() {
        listOf("../evil", "a/../../evil", "/etc/passwd", "a\\b", "", "./x", "a//b")
            .forEach { assertFalse(it, ArtifactVerification.isSafeZipMemberName(it)) }

        listOf("payload.img", "nested/payload.img", "a.b.c")
            .forEach { assertTrue(it, ArtifactVerification.isSafeZipMemberName(it)) }
    }

    private fun tempDir(): File = createTempDirectory("artifact-verification").toFile()

    private fun writeSignedBundle(
        runId: Long,
        payload: ByteArray = "kernel-image-bytes".toByteArray(),
        payloadOverride: ByteArray? = null,
        signatureOverride: ByteArray? = null
    ): File {
        val bundleName = "artifact.bundle.zip"
        val bundle = File(tempDir(), bundleName)
        val manifestJson = """
            {"schema":1,"bundle_name":"$bundleName","artifact_type":"KERNEL_IMG",
             "run_id":$runId,"payload_name":"payload.img",
             "payload_sha256":"${sha256Hex(payload)}","payload_size_bytes":${payload.size}}
        """.trimIndent()
        val manifestBytes = manifestJson.toByteArray()

        ZipOutputStream(bundle.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(ArtifactVerification.MANIFEST_FILE_NAME))
            zip.write(manifestBytes)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(ArtifactVerification.SIGNATURE_FILE_NAME))
            zip.write(signatureOverride ?: sign(manifestBytes))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("payload.img"))
            zip.write(payloadOverride ?: payload)
            zip.closeEntry()
        }
        return bundle
    }

    private fun sign(bytes: ByteArray): ByteArray {
        val der = Base64.getDecoder().decode(
            material.privateKeyPem
                .lineSequence()
                .filterNot { it.startsWith("-----") }
                .joinToString("")
                .trim()
        )
        val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der))
        return Signature.getInstance("SHA256withRSA").run {
            initSign(key)
            update(bytes)
            sign()
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
