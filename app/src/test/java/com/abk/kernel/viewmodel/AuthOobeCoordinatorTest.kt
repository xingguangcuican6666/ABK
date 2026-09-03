package com.abk.kernel.viewmodel

import com.abk.kernel.data.repository.GitHubRepository
import com.abk.kernel.data.repository.PreferencesRepository
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthOobeCoordinatorTest {

    @Test
    fun completeIfRequestedTrue_setsAuthStepToThemeSelect() {
        // Given
        var capturedState: MainUiState? = null
        val coordinator = AuthOobeCoordinator(
            scope = mockk(relaxed = true),
            github = mockk(),
            prefs = mockk(),
            readState = { MainUiState(downloadDirectory = "") },
            updateState = { transform ->
                capturedState = transform(MainUiState(downloadDirectory = ""))
            },
            fetchUser = { null },
            requestForkCheck = {},
            onGitHubSessionRefreshed = {},
            text = { _, _ -> "" },
        )

        // When
        coordinator.completeIfRequested(true)

        // Then
        assertEquals(AuthStep.THEME_SELECT, capturedState?.authStep)
    }

    @Test
    fun completeIfRequestedFalse_doesNotChangeAuthStep() {
        // Given
        val initialAuthStep = AuthStep.INTRO
        var capturedState: MainUiState? = null
        val coordinator = AuthOobeCoordinator(
            scope = mockk(relaxed = true),
            github = mockk(),
            prefs = mockk(),
            readState = { MainUiState(authStep = initialAuthStep) },
            updateState = { transform ->
                capturedState = transform(MainUiState(authStep = initialAuthStep))
            },
            fetchUser = { null },
            requestForkCheck = {},
            onGitHubSessionRefreshed = {},
            text = { _, _ -> "" },
        )

        // When
        coordinator.completeIfRequested(false)

        // Then
        assertEquals(null, capturedState)
    }
}
