package com.abk.kernel.miuix.ui.screens.flash.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

enum class TerminalLogState { IDLE, RUNNING, SUCCESS, FAILED }

class FlashTerminalLogState {
    private val _log = mutableStateListOf<String>()
    val log: List<String> get() = _log

    private val _state = mutableStateOf(TerminalLogState.IDLE)
    val state: State<TerminalLogState> get() = _state

    val isRunning: Boolean get() = _state.value == TerminalLogState.RUNNING
    val isCompleted: Boolean get() = _state.value in setOf(TerminalLogState.SUCCESS, TerminalLogState.FAILED)
    val isSuccess: Boolean get() = _state.value == TerminalLogState.SUCCESS
    val errorMessage: String?
        get() = if (_state.value == TerminalLogState.FAILED) _errorMessage.value else null

    private val _errorMessage = mutableStateOf<String?>(null)

    private val _progress = mutableStateOf(0f)
    val progress: State<Float> get() = _progress

    private val _currentStep = mutableStateOf("")
    val currentStep: State<String> get() = _currentStep

    val logText: String get() = log.joinToString("\n")

    fun appendLine(line: String) { _log.add(line) }
    fun reset() { _log.clear(); _state.value = TerminalLogState.IDLE; _errorMessage.value = null; _progress.value = 0f; _currentStep.value = "" }
    fun setRunning() { _state.value = TerminalLogState.RUNNING; _progress.value = 0f; _currentStep.value = "" }
    fun setSuccess() { _state.value = TerminalLogState.SUCCESS; _progress.value = 1f }
    fun setFailed(error: String? = null) { _state.value = TerminalLogState.FAILED; _errorMessage.value = error }
}

@Composable
fun rememberFlashTerminalLogState(): FlashTerminalLogState = remember { FlashTerminalLogState() }
