package com.abk.kernel.data.repository

import com.abk.kernel.data.api.NetworkClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GitHubRepositoryNetworkTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: GitHubRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = GitHubRepository(
            apiService = NetworkClient.createApiService(
                token = "test-token",
                baseUrl = server.url("/").toString()
            )
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun getRepo_usesGitHubJsonAcceptByDefault() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "id": 1,
                      "name": "repo",
                      "full_name": "owner/repo",
                      "fork": false,
                      "default_branch": "main",
                      "private": false,
                      "html_url": "https://github.com/owner/repo"
                    }
                    """.trimIndent()
                )
        )

        val result = repository.getRepo("owner", "repo")

        assertTrue(result is Result.Success)
        val request = server.takeRequest()
        assertEquals("application/vnd.github+json", request.getHeader("Accept"))
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
    }

    @Test
    fun downloadReleaseAssetText_requestsOctetStreamAndReturnsPemBody() {
        val pem = """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtestkeytestkeytest
            -----END PUBLIC KEY-----
        """.trimIndent()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/repos/owner/repo/releases/1/assets?per_page=100&page=1" -> MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        [
                          {
                            "id": 99,
                            "name": "abk-artifact-signing-public.pem",
                            "size": 120,
                            "content_type": "application/x-pem-file",
                            "browser_download_url": "https://example.com/key.pem"
                          }
                        ]
                        """.trimIndent()
                    )
                "/repos/owner/repo/releases/assets/99" -> {
                    val body = if (request.getHeader("Accept") == "application/octet-stream") {
                        pem
                    } else {
                        """
                        {
                          "id": 99,
                          "name": "abk-artifact-signing-public.pem",
                          "browser_download_url": "https://example.com/key.pem"
                        }
                        """.trimIndent()
                    }
                    MockResponse()
                        .setResponseCode(200)
                        .setBody(body)
                }
                else -> MockResponse().setResponseCode(404)
            }
        }

        val result = repository.downloadReleaseAssetText(
            owner = "owner",
            repo = "repo",
            releaseId = 1L,
            assetName = "abk-artifact-signing-public.pem"
        )

        assertEquals(Result.Success(pem), result)
        server.takeRequest()
        val downloadRequest = server.takeRequest()
        assertEquals("application/octet-stream", downloadRequest.getHeader("Accept"))
    }
}
