package com.abk.kernel.viewmodel

import com.abk.kernel.data.model.KernelRunnerSettings
import com.abk.kernel.data.model.KernelRunnerTarget
import com.abk.kernel.data.repository.GitHubRepository
import com.abk.kernel.data.repository.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class KernelRunnerSettingsContext(
    val owner: String,
    val repository: String,
    val defaultBranch: String,
    val repositoryId: Long
) {
    val fullName: String get() = "$owner/$repository"
}

data class KernelRunnerSettingsUiState(
    val context: KernelRunnerSettingsContext? = null,
    val settings: KernelRunnerSettings? = null,
    val loading: Boolean = false,
    val savingTarget: KernelRunnerTarget? = null,
    val savedTarget: KernelRunnerTarget? = null,
    val saveRevisions: Map<KernelRunnerTarget, Long> = emptyMap(),
    val error: String? = null
) {
    val busy: Boolean get() = loading || savingTarget != null
}

/** Repository settings stay separate from dispatch inputs and local build plans. */
internal class KernelRunnerSettingsCoordinator(
    private val scope: CoroutineScope,
    private val github: GitHubRepository,
    private val readContext: () -> KernelRunnerSettingsContext?,
    private val readState: () -> KernelRunnerSettingsUiState,
    private val updateState: (KernelRunnerSettingsUiState) -> Unit,
    private val unexpectedError: () -> String
) {
    private var generation = 0L
    private var request: Job? = null

    fun contextChanged(force: Boolean = false) {
        val context = readContext()
        if (!force && readState().context == context) return
        generation++
        request?.cancel()
        request = null
        updateState(KernelRunnerSettingsUiState(context = context))
    }

    fun refresh() {
        contextChanged()
        val context = readContext() ?: return
        val state = readState()
        if (state.busy) return
        val currentGeneration = generation
        // Hide the old snapshot: a failed refresh must not leave stale settings editable.
        updateState(state.copy(settings = null, loading = true, savedTarget = null, error = null))
        request = scope.launch {
            val result = repositoryResult {
                github.readKernelRunnerSettings(context.owner, context.repository, context.defaultBranch)
            }
            if (!isCurrent(context, currentGeneration)) return@launch
            updateState(when (result) {
                is Result.Success -> readState().copy(settings = result.data, loading = false)
                is Result.Error -> readState().copy(loading = false, error = result.message)
                Result.Loading -> readState().copy(loading = false, error = unexpectedError())
            })
        }
    }

    fun save(target: KernelRunnerTarget, labels: String, enabled: Boolean) {
        contextChanged()
        val context = readContext() ?: return
        val state = readState()
        val settings = state.settings ?: return
        val config = when (target) {
            KernelRunnerTarget.GKI -> settings.gki
            KernelRunnerTarget.ONEPLUS -> settings.oneplus
        }
        if (state.busy || !config.supported) return
        val currentGeneration = generation
        updateState(state.copy(savingTarget = target, savedTarget = null, error = null))
        request = scope.launch {
            val result = repositoryResult {
                github.saveKernelRunnerSettings(
                    context.owner, context.repository, target, labels, enabled, context.defaultBranch
                )
            }
            if (!isCurrent(context, currentGeneration)) return@launch
            updateState(when (result) {
                is Result.Success -> readState().copy(
                    settings = when (target) {
                        KernelRunnerTarget.GKI -> settings.copy(gki = result.data)
                        KernelRunnerTarget.ONEPLUS -> settings.copy(oneplus = result.data)
                    },
                    savingTarget = null,
                    savedTarget = target,
                    saveRevisions = readState().saveRevisions +
                        (target to (readState().saveRevisions.getOrDefault(target, 0L) + 1L))
                )
                // A multi-variable request can fail after one write succeeded.
                // Require a fresh remote snapshot before allowing another save.
                is Result.Error -> readState().copy(settings = null, savingTarget = null, error = result.message)
                Result.Loading -> readState().copy(settings = null, savingTarget = null, error = unexpectedError())
            })
        }
    }

    private fun isCurrent(context: KernelRunnerSettingsContext, expectedGeneration: Long): Boolean =
        generation == expectedGeneration && readContext() == context

    private suspend fun <T> repositoryResult(block: suspend () -> Result<T>): Result<T> = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.Error(error.message ?: unexpectedError())
    }
}
