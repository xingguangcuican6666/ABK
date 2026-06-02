package com.abk.kernel.viewmodel

import com.abk.kernel.R
import com.abk.kernel.data.model.GitHubUser
import com.abk.kernel.data.repository.GitHubRepository
import com.abk.kernel.data.repository.PreferencesRepository
import com.abk.kernel.data.repository.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AuthOobeCoordinator(
    private val scope: CoroutineScope,
    private val github: GitHubRepository,
    private val prefs: PreferencesRepository,
    private val readState: () -> MainUiState,
    private val updateState: ((MainUiState) -> MainUiState) -> Unit,
    private val fetchUser: suspend (reportError: Boolean) -> GitHubUser?,
    private val requestForkCheck: (closeOobeWhenReady: Boolean) -> Unit,
    private val onGitHubSessionRefreshed: () -> Unit,
    private val text: (Int, Array<out Any>) -> String,
) {
    private var hasShownInitialOobeThisLaunch = false

    fun maybeShowInitialOobe() {
        val state = readState()
        if (!state.termsAccepted || state.oobeCompleted || hasShownInitialOobeThisLaunch) return
        hasShownInitialOobeThisLaunch = true
        updateState {
            it.copy(
                showOobe = true,
                authStep = AuthStep.INTRO,
                error = null,
            )
        }
    }

    fun openBuildOobe() {
        val state = readState()
        val nextStep = if (state.isLoggedIn && state.user != null) AuthStep.FORK_CHECK else AuthStep.LOGIN
        updateState {
            it.copy(
                showOobe = true,
                authStep = nextStep,
                error = null,
            )
        }
        if (state.isLoggedIn && state.user == null) {
            scope.launch {
                val user = fetchUser(true) ?: return@launch
                updateState { it.copy(authStep = AuthStep.FORK_CHECK, user = user, isLoggedIn = true) }
                requestForkCheck(true)
            }
        } else if (nextStep == AuthStep.FORK_CHECK) {
            requestForkCheck(true)
        }
    }

    fun continueOobeToLogin() {
        updateState {
            it.copy(
                showOobe = true,
                authStep = AuthStep.LOGIN,
                error = null,
            )
        }
    }

    fun skipOobe() {
        scope.launch {
            if (!readState().oobeCompleted) {
                prefs.setOobeCompleted(true)
            }
            closeOobe()
        }
    }

    fun startDeviceFlow() {
        scope.launch {
            updateState { it.copy(isLoading = true, error = null) }
            when (val r = github.requestDeviceCode()) {
                is Result.Success -> {
                    updateState {
                        it.copy(
                            isLoading = false,
                            deviceCode = r.data.deviceCode,
                            userCode = r.data.userCode,
                            verificationUri = r.data.verificationUri,
                            isPollingToken = true,
                        )
                    }
                    pollToken(r.data.deviceCode, r.data.interval.toLong())
                }
                is Result.Error -> updateState { it.copy(isLoading = false, error = r.message) }
                else -> {}
            }
        }
    }

    fun completeIfRequested(closeOobeWhenReady: Boolean) {
        if (closeOobeWhenReady) {
            completeOobe()
        }
    }

    private fun pollToken(deviceCode: String, intervalSeconds: Long) {
        scope.launch {
            while (readState().isPollingToken) {
                delay(intervalSeconds * 1000)
                when (val r = github.pollToken(deviceCode)) {
                    is Result.Success -> {
                        val tokenResp = r.data
                        when (tokenResp.error) {
                            null -> {
                                val token = tokenResp.accessToken ?: continue
                                onGitHubSessionRefreshed()
                                prefs.saveToken(token)
                                github.updateToken(token)
                                updateState { it.copy(isPollingToken = false) }
                                fetchUserAndContinueOobe()
                            }
                            "authorization_pending", "slow_down" -> {
                                if (tokenResp.error == "slow_down") delay(5000)
                            }
                            "expired_token", "access_denied" -> {
                                updateState {
                                    it.copy(
                                        isPollingToken = false,
                                        error = text(
                                            R.string.vm_auth_failed,
                                            arrayOf(tokenResp.error.orEmpty()),
                                        ),
                                    )
                                }
                            }
                            else -> updateState { it.copy(isPollingToken = false, error = tokenResp.error) }
                        }
                    }
                    is Result.Error -> delay(intervalSeconds * 1000)
                    else -> {}
                }
            }
        }
    }

    private suspend fun fetchUserAndContinueOobe() {
        val user = fetchUser(true) ?: return
        updateState {
            it.copy(
                user = user,
                isLoggedIn = true,
                showOobe = true,
                authStep = AuthStep.FORK_CHECK,
            )
        }
        requestForkCheck(true)
    }

    private fun closeOobe() {
        updateState {
            it.copy(
                showOobe = false,
                authStep = AuthStep.INTRO,
                deviceCode = null,
                userCode = null,
                verificationUri = null,
                isPollingToken = false,
                error = null,
            )
        }
    }

    private fun completeOobe() {
        if (!readState().oobeCompleted) {
            scope.launch { prefs.setOobeCompleted(true) }
        }
        closeOobe()
    }
}
