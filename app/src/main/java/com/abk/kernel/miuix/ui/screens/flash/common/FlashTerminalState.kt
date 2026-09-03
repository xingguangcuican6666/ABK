package com.abk.kernel.miuix.ui.screens.flash.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Result of a root operation. Returned by the lambda passed to
 * [FlashTerminalState.executeRootOp].
 */
data class RootResult(
    val success: Boolean,
    val message: String = ""
)

/**
 * Encapsulates 5 terminal-state variables and the common execute/show-failure
 * logic that was previously duplicated across 4 flash MIUIX screen files.
 *
 * State is stored in [StateFlow]s so it is safe to update from any coroutine
 * context and can be observed from Compose via `collectAsState()`.
 */
class FlashTerminalState internal constructor(
    @Suppress("unused") private val context: Context,
    @Suppress("unused") private val scope: CoroutineScope
) {
    // ── Observable state ────────────────────────────────────────────────

    private val _showDialog = MutableStateFlow(false)
    val showDialog: StateFlow<Boolean> = _showDialog.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _success = MutableStateFlow<Boolean?>(null)
    val success: StateFlow<Boolean?> = _success.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _canReboot = MutableStateFlow(false)
    val canReboot: StateFlow<Boolean> = _canReboot.asStateFlow()

    // ── Methods ─────────────────────────────────────────────────────────

    /** Append one line to the terminal log. Thread-safe (StateFlow). */
    fun appendLine(line: String) {
        _log.value = _log.value + line
    }

    /** Reset all state to defaults. */
    fun reset() {
        _showDialog.value = false
        _title.value = ""
        _isRunning.value = false
        _success.value = null
        _log.value = emptyList()
        _canReboot.value = false
    }

    /** Show a failure dialog without starting a root operation. */
    fun showFailure(title: String, message: String) {
        _showDialog.value = true
        _title.value = title
        _isRunning.value = false
        _success.value = false
        _log.value = listOf(message)
        _canReboot.value = false
    }

    /** Show a failure dialog with multiple log lines. */
    fun showFailure(title: String, lines: List<String>) {
        _showDialog.value = true
        _title.value = title
        _isRunning.value = false
        _success.value = false
        _log.value = lines
        _canReboot.value = false
    }

    /** Dismiss the terminal dialog. */
    fun dismiss() {
        _showDialog.value = false
    }

    /**
     * Unified root operation executor.
     *
     * Resets all state, shows the terminal dialog in a "running" state,
     * executes [op], and updates the dialog to reflect the result.
     *
     * The lambda receiver is [FlashTerminalState] so that [appendLine] can
     * be called directly inside the block.
     *
     * @param title     Operation title shown in the dialog (e.g. "Installing").
     * @param canReboot Whether a reboot button should appear on success.
     * @param op        The root operation implementation. Must return [RootResult].
     */
    suspend fun executeRootOp(
        title: String,
        canReboot: Boolean = false,
        op: suspend FlashTerminalState.() -> RootResult
    ) {
        _showDialog.value = true
        _title.value = title
        _isRunning.value = true
        _success.value = null
        _log.value = emptyList()
        _canReboot.value = canReboot

        try {
            val result = op()
            _isRunning.value = false
            _success.value = result.success
            if (!result.success && result.message.isNotEmpty()) {
                appendLine("Error: ${result.message}")
            }
        } catch (e: Exception) {
            _isRunning.value = false
            _success.value = false
            appendLine("Exception: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}

/**
 * Remember a [FlashTerminalState] scoped to the current composition.
 */
@Composable
fun rememberFlashTerminalState(
    context: Context,
    scope: CoroutineScope
): FlashTerminalState {
    return remember { FlashTerminalState(context, scope) }
}
