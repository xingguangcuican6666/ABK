package com.abk.kernel.data.repository

import com.abk.kernel.data.api.GitHubApiService
import com.abk.kernel.data.model.*
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.Strictness
import kotlinx.coroutines.CancellationException

/** Repository variables do not add inputs to workflow_dispatch. */
internal class KernelRunnerSettingsStore(private val api: GitHubApiService) {
    suspend fun read(owner: String, repo: String, ref: String?): Result<KernelRunnerSettings> = attempt {
        val branch = resolveRef(owner, repo, ref)
        val supported = KernelRunnerTarget.entries.associateWith { supportsVariables(owner, repo, branch, it) }
        val variables = readVariables(owner, repo)
        fun config(target: KernelRunnerTarget): KernelRunnerConfig {
            val labels = variables[target.runnerVariable].orEmpty()
            val parsed = runCatching { parseLabels(labels) }.getOrNull()
            return KernelRunnerConfig(
                labels = labels,
                selfHostedEnabled = supported.getValue(target) &&
                    !variables[target.switchVariable].equals("false", ignoreCase = true) &&
                    labels == labels.trim() && parsed != null && isSelfHosted(parsed),
                supported = supported.getValue(target)
            )
        }
        KernelRunnerSettings(config(KernelRunnerTarget.GKI), config(KernelRunnerTarget.ONEPLUS))
    }

    suspend fun save(
        owner: String,
        repo: String,
        target: KernelRunnerTarget,
        labels: String,
        enabled: Boolean,
        ref: String?
    ): Result<KernelRunnerConfig> = attempt {
        val normalized = labels.trim()
        // Turning off must work even if a stored/draft label is malformed.
        val parsed = if (enabled) parseLabels(normalized) else null
        require(!enabled || (parsed != null && isSelfHosted(parsed))) {
            "Enter self-hosted runner labels before enabling self-hosted builds."
        }
        val value = if (parsed != null && normalized.startsWith("[")) parsed.toString() else normalized
        val branch = resolveRef(owner, repo, ref)
        if (!supportsVariables(owner, repo, branch, target)) {
            throw SettingsFailure("This workflow does not support runner settings. Sync the fork first.")
        }
        val variables = readVariables(owner, repo)
        // Disabling writes only the override, preserving the saved machine and
        // avoiding a transient switch to draft labels before GitHub hosting.
        val savedLabels = if (enabled) value else variables[target.runnerVariable].orEmpty()
        if (enabled) {
            writeVariable(owner, repo, target.runnerVariable, value, variables.containsKey(target.runnerVariable))
        }
        try {
            writeVariable(owner, repo, target.switchVariable, enabled.toString(), variables.containsKey(target.switchVariable))
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw SettingsFailure(
                if (enabled) "${error.message} Runner labels may already be saved; reload settings before retrying."
                else "${error.message} Reload settings before retrying.",
                (error as? SettingsFailure)?.code ?: -1
            )
        }
        KernelRunnerConfig(savedLabels, enabled, supported = true)
    }

    private suspend fun resolveRef(owner: String, repo: String, ref: String?): String {
        if (!ref.isNullOrBlank()) return ref
        val response = api.getRepo(owner, repo)
        if (!response.isSuccessful) fail("Read repository", response.code())
        return response.body()?.defaultBranch
            ?: throw SettingsFailure("GitHub returned an empty repository response.")
    }

    private suspend fun supportsVariables(owner: String, repo: String, ref: String, target: KernelRunnerTarget): Boolean {
        val response = api.getFileRaw(owner, repo, target.workflowPath, ref)
        if (response.code() == 404) return false
        if (!response.isSuccessful) fail("Read runner workflow", response.code())
        val source = response.body()?.use { it.string() }
            ?: throw SettingsFailure("GitHub returned an empty workflow response.")
        // Detect the active scheduling expression, not variable names in comments.
        return source.lineSequence().any { line ->
            val expression = runnerExpression.matchEntire(line)?.groupValues?.get(1)
            expression != null &&
                Regex("\\bvars\\.${target.runnerVariable}\\b").containsMatchIn(expression) &&
                Regex("\\bvars\\.${target.switchVariable}\\b").containsMatchIn(expression)
        }
    }

    private suspend fun readVariables(owner: String, repo: String): Map<String, String> {
        val values = mutableMapOf<String, String>()
        var page = 1
        var received = 0
        while (true) {
            val response = api.listRepositoryVariables(owner, repo, perPage = 30, page = page)
            // A 404 here can mean no access; only an absent entry in a successful
            // list response is treated as an unconfigured variable.
            if (!response.isSuccessful) fail("Read repository variables", response.code())
            val body = response.body() ?: throw SettingsFailure("GitHub returned an empty variables response.")
            body.variables.forEach { values[it.name] = it.value }
            received += body.variables.size
            if (received >= body.totalCount) return values
            if (body.variables.isEmpty()) throw SettingsFailure("The repository variables list is incomplete. Reload and try again.")
            page += 1
        }
    }

    private suspend fun writeVariable(owner: String, repo: String, name: String, value: String, exists: Boolean) {
        val request = RepositoryVariableRequest(name, value)
        var response = if (exists) api.updateRepositoryVariable(owner, repo, name, request)
            else api.createRepositoryVariable(owner, repo, request)
        // Another client can create/delete a variable after the list request.
        if (exists && response.code() == 404) {
            response = api.createRepositoryVariable(owner, repo, request)
        } else if (!exists && response.code() in setOf(409, 422)) {
            response = api.updateRepositoryVariable(owner, repo, name, request)
        }
        if (!response.isSuccessful) fail("Save $name", response.code())
    }

    private fun fail(operation: String, code: Int): Nothing {
        val hint = when (code) {
            401 -> " Sign in again."
            403 -> " Check repository access and the token's Variables permission."
            404 -> " Check the repository and token access."
            else -> ""
        }
        throw SettingsFailure("$operation failed (HTTP $code).$hint", code)
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.Success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: SettingsFailure) {
        Result.Error(error.message.orEmpty(), error.code)
    } catch (error: Exception) {
        Result.Error(error.message ?: "Unable to update runner settings.")
    }

    private class SettingsFailure(message: String, val code: Int = -1) : Exception(message)

    private companion object {
        val strictGson = GsonBuilder().setStrictness(Strictness.STRICT).create()
        val hostedLabel = Regex("(?i)(ubuntu|windows|macos)-(latest|[0-9][a-z0-9.+-]*)")
        val runnerExpression = Regex("""\s*runs-on:\s*\$\{\{(.*)}}\s*(?:#.*)?""")

        fun parseLabels(value: String): JsonArray {
            require(value.isNotBlank()) { "Enter self-hosted runner labels before enabling self-hosted builds." }
            val labels = if (value.startsWith("[")) {
                try {
                    strictGson.fromJson(value, JsonArray::class.java)
                } catch (_: Exception) {
                    throw IllegalArgumentException("Runner labels must be a single label or a JSON array of strings.")
                }
            } else {
                require(value.none { it in "[]{}\"'," || it.isISOControl() }) {
                    "Runner labels must be a single label or a JSON array of strings."
                }
                JsonArray().apply { add(value) }
            }
            require(labels != null && labels.size() > 0 && labels.all {
                it.isJsonPrimitive && it.asJsonPrimitive.isString &&
                    it.asString.isNotBlank() && it.asString == it.asString.trim() &&
                    it.asString.none(Char::isISOControl) &&
                    it.asString.lowercase() !in setOf("true", "false", "null")
            }) { "Runner labels must contain non-empty strings, not boolean values." }
            return labels
        }

        fun isSelfHosted(labels: JsonArray): Boolean =
            labels.any { it.asString.equals("self-hosted", ignoreCase = true) } ||
                labels.none { hostedLabel.matches(it.asString) }
    }
}
