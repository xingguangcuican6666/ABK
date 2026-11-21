package com.abk.kernel.data.repository

import com.abk.kernel.data.api.NetworkClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class BuildModuleOptionsSupportTest {
    private val workflow = """
        # ABK_BUILD_MODULE_OPTIONS_SCHEMA: 1
        jobs:
          preflight:
            steps:
              - env:
                  ABK_BUILD_MODULE_OPTIONS: ${'$'}{{ inputs.custom_external_modules }}
                run: python3 .github/scripts/build-module-options.py
          build:
            with:
              custom_external_modules: ${'$'}{{ needs.preflight.outputs.external_modules }}
              ksuhook_modules: ${'$'}{{ needs.preflight.outputs.ksuhook_modules }}
              ksuhook_sdk_ref: ${'$'}{{ needs.preflight.outputs.ksuhook_sdk_ref }}
    """.trimIndent()

    @Test fun commentsAloneDoNotAdvertiseSupport() {
        assertTrue(BuildModuleOptionsSupport.workflowUsesEnvelope(workflow))
        assertFalse(BuildModuleOptionsSupport.workflowUsesEnvelope(workflow.lines().joinToString("\n") { "# $it" }))
        assertFalse(BuildModuleOptionsSupport.workflowUsesEnvelope(workflow.replace("run: python3", "# run: python3")))
    }

    @Test fun checksWorkflowAndParserAtExactDispatchRef() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val support = BuildModuleOptionsSupport(NetworkClient.createApiService(baseUrl = server.url("/").toString()))
            server.enqueue(MockResponse().setBody(workflow))
            server.enqueue(MockResponse().setBody("def decode_options(raw):\n    return {\"schema_version\": 1, \"ksuhook_modules\": \"\"}"))
            assertEquals(Result.Success(true), support.supports("owner", "repo", "kernel-custom.yml", "feature/patches"))
            repeat(2) {
                val request = server.takeRequest()
                assertEquals("feature/patches", request.requestUrl?.queryParameter("ref"))
            }
        }
    }

    @Test fun missingOrUnknownSupportNeverSucceeds() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val support = BuildModuleOptionsSupport(NetworkClient.createApiService(baseUrl = server.url("/").toString()))
            server.enqueue(MockResponse().setResponseCode(404))
            assertEquals(Result.Success(false), support.supports("o", "r", "kernel-custom.yml", "main"))
            server.enqueue(MockResponse().setResponseCode(403))
            assertTrue(support.supports("o", "r", "kernel-source.yml", "main") is Result.Error)
            server.enqueue(MockResponse().setBody(workflow))
            server.enqueue(MockResponse().setResponseCode(404))
            assertEquals(Result.Success(false), support.supports("o", "r", "kernel-source.yml", "main"))
        }
    }
}
