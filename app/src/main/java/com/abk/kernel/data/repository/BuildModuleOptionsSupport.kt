package com.abk.kernel.data.repository

import com.abk.kernel.data.api.GitHubApiService
import kotlinx.coroutines.CancellationException

/** Capability is checked at the dispatch ref, never inferred from the upstream fork. */
internal class BuildModuleOptionsSupport(private val api: GitHubApiService) {
    suspend fun supports(owner: String, repo: String, workflow: String, ref: String): Result<Boolean> = try {
        val response = api.getFileRaw(owner, repo, ".github/workflows/$workflow", ref)
        if (response.code() == 404) Result.Success(false)
        else if (!response.isSuccessful) Result.Error("HTTP ${response.code()}", response.code())
        else {
            val source = response.body()?.use { it.string() }.orEmpty()
            if (!workflowUsesEnvelope(source)) Result.Success(false)
            else {
                val script = api.getFileRaw(owner, repo, ".github/scripts/build-module-options.py", ref)
                if (script.code() == 404) Result.Success(false)
                else if (!script.isSuccessful) Result.Error("HTTP ${script.code()}", script.code())
                else Result.Success(script.body()?.use { it.string() }?.let {
                    Regex("(?m)^def decode_options\\(").containsMatchIn(it) &&
                        it.contains("\"schema_version\"") && it.contains("\"ksuhook_modules\"")
                } == true)
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.Error(error.message ?: "Unable to check workflow support")
    }

    companion object {
        internal fun workflowUsesEnvelope(source: String): Boolean {
            val active = source.lineSequence().filterNot { it.trimStart().startsWith('#') }.joinToString("\n")
            return source.lineSequence().any { it.trim() == "# ABK_BUILD_MODULE_OPTIONS_SCHEMA: 1" } &&
                Regex("(?m)^\\s*run: python3 \\.github/scripts/build-module-options\\.py\\s*$").containsMatchIn(active) &&
                Regex("(?m)^\\s*ABK_BUILD_MODULE_OPTIONS: \\$\\{\\{ inputs\\.custom_external_modules }}\\s*$").containsMatchIn(active) &&
                listOf("external_modules", "ksuhook_modules", "ksuhook_sdk_ref").all { field ->
                    active.contains("needs.preflight.outputs.$field")
                }
        }
    }
}
