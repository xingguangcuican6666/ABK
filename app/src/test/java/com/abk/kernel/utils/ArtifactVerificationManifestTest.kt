package com.abk.kernel.utils

import com.abk.kernel.data.model.ArtifactType
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactVerificationManifestTest {
    @Test
    fun schemaOneCustomSourceFieldsAreParsedAndUnknownFieldsAreIgnored() {
        val bundle = File(createTempDirectory("manifest-v1").toFile(), "Images.zip.bundle.zip")
        ZipOutputStream(bundle.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(ArtifactVerification.MANIFEST_FILE_NAME))
            zip.write(
                """
                {
                  "schema": 1,
                  "bundle_name": "Images.zip.bundle.zip",
                  "artifact_type": "OTHER",
                  "run_id": 74,
                  "payload_name": "Images.zip",
                  "payload_sha256": "",
                  "payload_size_bytes": 0,
                  "payload_kind": "KERNEL_IMAGE_SET",
                  "kernel_source": {
                    "mode": "lineage_like_git",
                    "url": "https://github.com/example/kernel.git",
                    "requested_ref": "lineage-23.2",
                    "resolved_commit": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "defconfigs": ["gki_defconfig", "vendor/device.config"]
                  },
                  "client_notice": {
                    "type": "custom_source_build",
                    "version": 1,
                    "capability": "custom_source_notice_v1",
                    "review_before_flash": true
                  },
                  "future_optional_field": {"ignored": true}
                }
                """.trimIndent().toByteArray()
            )
            zip.closeEntry()
        }

        val manifest = requireNotNull(ArtifactVerification.readBundleManifest(bundle))

        assertEquals(1, manifest.schema)
        assertEquals("KERNEL_IMAGE_SET", manifest.payloadKind)
        assertEquals("lineage-23.2", manifest.kernelSource?.requestedRef)
        assertEquals(listOf("gki_defconfig", "vendor/device.config"), manifest.kernelSource?.defconfigs)
        assertTrue(ArtifactVerification.requiresTrustedBundle(ArtifactType.OTHER, manifest))
    }
}
