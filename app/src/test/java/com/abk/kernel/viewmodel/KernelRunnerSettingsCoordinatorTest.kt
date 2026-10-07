package com.abk.kernel.viewmodel

import com.abk.kernel.data.model.KernelRunnerConfig
import com.abk.kernel.data.model.KernelRunnerSettings
import com.abk.kernel.data.model.KernelRunnerTarget
import com.abk.kernel.data.repository.GitHubRepository
import com.abk.kernel.data.repository.Result
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KernelRunnerSettingsCoordinatorTest {
    private val firstContext = KernelRunnerSettingsContext("owner", "repo", "dev", 1L)
    private val supportedSettings = KernelRunnerSettings(
        gki = KernelRunnerConfig("abk", selfHostedEnabled = true, supported = true),
        oneplus = KernelRunnerConfig("opl", selfHostedEnabled = false, supported = true),
    )

    @Test
    fun refreshFailureDiscardsEditableSnapshot() = runTest {
        val github = FakeGitHubRepository().apply {
            readResult = { Result.Error("No permission", 403) }
        }
        var state = KernelRunnerSettingsUiState(firstContext, supportedSettings)
        val subject = KernelRunnerSettingsCoordinator(
            scope = this,
            github = github,
            readContext = { firstContext },
            readState = { state },
            updateState = { state = it },
            unexpectedError = { "Unexpected" },
        )

        subject.refresh()

        assertTrue(state.loading)
        assertNull(state.settings)
        runCurrent()
        assertFalse(state.loading)
        assertNull(state.settings)
        assertEquals("No permission", state.error)
    }

    @Test
    fun duplicateRefreshAndSaveAreIgnoredWhileBusy() = runTest {
        val response = CompletableDeferred<Result<KernelRunnerSettings>>()
        val github = FakeGitHubRepository().apply { readResult = { response.await() } }
        var state = KernelRunnerSettingsUiState(firstContext, supportedSettings)
        val subject = KernelRunnerSettingsCoordinator(
            scope = this,
            github = github,
            readContext = { firstContext },
            readState = { state },
            updateState = { state = it },
            unexpectedError = { "Unexpected" },
        )

        subject.refresh()
        runCurrent()
        subject.refresh()
        subject.save(KernelRunnerTarget.GKI, "new", true)
        runCurrent()

        assertEquals(1, github.reads)
        assertEquals(0, github.saves)
        response.complete(Result.Success(supportedSettings))
        runCurrent()
        assertEquals(supportedSettings, state.settings)
    }

    @Test
    fun successfulSaveUpdatesOnlyTargetSnapshot() = runTest {
        val updated = KernelRunnerConfig("new", selfHostedEnabled = true, supported = true)
        val github = FakeGitHubRepository().apply { saveResult = { Result.Success(updated) } }
        var state = KernelRunnerSettingsUiState(firstContext, supportedSettings)
        val subject = KernelRunnerSettingsCoordinator(
            scope = this,
            github = github,
            readContext = { firstContext },
            readState = { state },
            updateState = { state = it },
            unexpectedError = { "Unexpected" },
        )

        subject.save(KernelRunnerTarget.GKI, "new", true)

        assertEquals(KernelRunnerTarget.GKI, state.savingTarget)
        runCurrent()
        assertEquals(updated, state.settings?.gki)
        assertEquals(supportedSettings.oneplus, state.settings?.oneplus)
        assertNull(state.savingTarget)
        assertEquals(KernelRunnerTarget.GKI, state.savedTarget)
    }

    @Test
    fun savingIdenticalValuesIncrementsOnlyTargetRevision() = runTest {
        val github = FakeGitHubRepository().apply {
            saveResult = { Result.Success(supportedSettings.gki) }
        }
        var state = KernelRunnerSettingsUiState(firstContext, supportedSettings)
        val subject = KernelRunnerSettingsCoordinator(
            scope = this,
            github = github,
            readContext = { firstContext },
            readState = { state },
            updateState = { state = it },
            unexpectedError = { "Unexpected" },
        )

        repeat(2) { index ->
            subject.save(KernelRunnerTarget.GKI, "abk", true)
            runCurrent()

            assertEquals(supportedSettings, state.settings)
            assertEquals((index + 1).toLong(), state.saveRevisions[KernelRunnerTarget.GKI])
            assertNull(state.saveRevisions[KernelRunnerTarget.ONEPLUS])
            assertEquals(KernelRunnerTarget.GKI, state.savedTarget)
        }
        assertEquals(2, github.saves)
    }

    @Test
    fun contextChangeCancelsAndDiscardsLateResponse() = runTest {
        val response = CompletableDeferred<Result<KernelRunnerSettings>>()
        val github = FakeGitHubRepository().apply {
            readResult = { withContext(NonCancellable) { response.await() } }
        }
        var context: KernelRunnerSettingsContext? = firstContext
        var state = KernelRunnerSettingsUiState(firstContext, supportedSettings)
        val subject = KernelRunnerSettingsCoordinator(
            scope = this,
            github = github,
            readContext = { context },
            readState = { state },
            updateState = { state = it },
            unexpectedError = { "Unexpected" },
        )

        subject.refresh()
        runCurrent()
        context = firstContext.copy(owner = "other", repositoryId = 2L)
        subject.contextChanged()
        response.complete(Result.Success(supportedSettings))
        runCurrent()

        assertEquals(context, state.context)
        assertNull(state.settings)
        assertFalse(state.loading)
    }

    @Test
    fun unsupportedTargetNeverWrites() = runTest {
        val github = FakeGitHubRepository()
        var state = KernelRunnerSettingsUiState(
            context = firstContext,
            settings = supportedSettings.copy(oneplus = KernelRunnerConfig()),
        )
        val subject = KernelRunnerSettingsCoordinator(
            scope = this,
            github = github,
            readContext = { firstContext },
            readState = { state },
            updateState = { state = it },
            unexpectedError = { "Unexpected" },
        )

        subject.save(KernelRunnerTarget.ONEPLUS, "abk", true)
        runCurrent()

        assertEquals(0, github.saves)
        assertFalse(state.busy)
    }

    @Test
    fun failedSaveDiscardsSnapshotAndClearsBusyState() = runTest {
        val github = FakeGitHubRepository().apply {
            saveResult = { Result.Error("Write failed", 403) }
        }
        var state = KernelRunnerSettingsUiState(firstContext, supportedSettings)
        val subject = KernelRunnerSettingsCoordinator(
            scope = this,
            github = github,
            readContext = { firstContext },
            readState = { state },
            updateState = { state = it },
            unexpectedError = { "Unexpected" },
        )

        subject.save(KernelRunnerTarget.GKI, "new", false)
        runCurrent()

        assertNull(state.settings)
        assertFalse(state.busy)
        assertNull(state.savedTarget)
        assertEquals("Write failed", state.error)
    }

    private class FakeGitHubRepository : GitHubRepository() {
        var reads = 0
        var saves = 0
        var readResult: suspend () -> Result<KernelRunnerSettings> = { error("Read not configured") }
        var saveResult: suspend () -> Result<KernelRunnerConfig> = { error("Save not configured") }

        override suspend fun readKernelRunnerSettings(
            owner: String,
            repo: String,
            ref: String?,
        ): Result<KernelRunnerSettings> {
            reads++
            return readResult()
        }

        override suspend fun saveKernelRunnerSettings(
            owner: String,
            repo: String,
            target: KernelRunnerTarget,
            labels: String,
            enabled: Boolean,
            ref: String?,
        ): Result<KernelRunnerConfig> {
            saves++
            return saveResult()
        }
    }
}
