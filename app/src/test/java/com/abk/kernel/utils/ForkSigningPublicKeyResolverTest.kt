package com.abk.kernel.utils

import com.abk.kernel.data.repository.Result
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForkSigningPublicKeyResolverTest {

    private val resolver = ForkSigningPublicKeyResolver(
        assetName = "abk-artifact-signing-public.pem",
        downloadReleaseAssetText = { _, _, _, _ -> Result.Error("unused") }
    )

    @Test
    fun parse_supportsStoredBase64Value() {
        val material = ForkSigningManager.generateSigningMaterial()

        val parsed = resolver.parse(material.publicKeyBase64)

        assertNotNull(parsed)
        assertEquals(material.publicKeyBase64, parsed?.base64)
        assertEquals(material.publicKeyPem, parsed?.pem)
    }

    @Test
    fun download_returnsPemAndBase64ForValidRemotePem() = runTest {
        val material = ForkSigningManager.generateSigningMaterial()
        val resolver = ForkSigningPublicKeyResolver(
            assetName = "abk-artifact-signing-public.pem",
            downloadReleaseAssetText = { _, _, _, _ -> Result.Success(material.publicKeyPem) }
        )

        val result = resolver.download("owner", "repo", 1L)

        assertTrue(result is Result.Success)
        val key = (result as Result.Success).data
        assertEquals(material.publicKeyBase64, key.base64)
        assertEquals(material.publicKeyPem, key.pem)
    }

    @Test
    fun download_rejectsGitHubAssetMetadataJson() = runTest {
        val resolver = ForkSigningPublicKeyResolver(
            assetName = "abk-artifact-signing-public.pem",
            downloadReleaseAssetText = { _, _, _, _ ->
                Result.Success(
                    """
                    {
                      "id": 99,
                      "name": "abk-artifact-signing-public.pem",
                      "browser_download_url": "https://example.com/key.pem"
                    }
                    """.trimIndent()
                )
            }
        )

        val result = resolver.download("owner", "repo", 1L)

        assertTrue(result is Result.Error)
        assertEquals(
            INVALID_FORK_SIGNING_PUBLIC_KEY_MESSAGE,
            (result as Result.Error).message
        )
    }

    @Test
    fun download_preservesUnderlyingDownloadError() = runTest {
        val resolver = ForkSigningPublicKeyResolver(
            assetName = "abk-artifact-signing-public.pem",
            downloadReleaseAssetText = { _, _, _, _ -> Result.Error("Download release asset failed: 404", 404) }
        )

        val result = resolver.download("owner", "repo", 1L)

        assertTrue(result is Result.Error)
        val error = result as Result.Error
        assertEquals("Download release asset failed: 404", error.message)
        assertEquals(404, error.code)
    }
}
