package com.abk.kernel.ui.screens.flash

import com.abk.kernel.miuix.ui.screens.flash.common.FlashTerminalLogState
import com.abk.kernel.miuix.ui.screens.flash.common.TerminalLogState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashTerminalLogStateTest {

    @Test
    fun initialState_isIdle() {
        val state = FlashTerminalLogState()
        assertEquals(TerminalLogState.IDLE, state.state.value)
        assertFalse(state.isRunning)
        assertFalse(state.isCompleted)
        assertFalse(state.isSuccess)
        assertTrue(state.log.isEmpty())
    }

    @Test
    fun setRunning_transitionsToRunning() {
        val state = FlashTerminalLogState()
        state.setRunning()
        assertEquals(TerminalLogState.RUNNING, state.state.value)
        assertTrue(state.isRunning)
        assertFalse(state.isCompleted)
        assertFalse(state.isSuccess)
    }

    @Test
    fun appendLine_addsToLog() {
        val state = FlashTerminalLogState()
        state.appendLine("test line")
        assertEquals(1, state.log.size)
        assertEquals("test line", state.log[0])
    }

    @Test
    fun appendLine_multipleLines() {
        val state = FlashTerminalLogState()
        state.appendLine("first")
        state.appendLine("second")
        state.appendLine("third")
        assertEquals(3, state.log.size)
        assertEquals("first", state.log[0])
        assertEquals("second", state.log[1])
        assertEquals("third", state.log[2])
    }

    @Test
    fun setSuccess_transitionsToSuccess() {
        val state = FlashTerminalLogState()
        state.setRunning()
        state.appendLine("some output")
        state.setSuccess()
        assertEquals(TerminalLogState.SUCCESS, state.state.value)
        assertFalse(state.isRunning)
        assertTrue(state.isCompleted)
        assertTrue(state.isSuccess)
    }

    @Test
    fun setFailed_transitionsToFailed() {
        val state = FlashTerminalLogState()
        state.setRunning()
        state.appendLine("some output")
        state.setFailed()
        assertEquals(TerminalLogState.FAILED, state.state.value)
        assertFalse(state.isRunning)
        assertTrue(state.isCompleted)
        assertFalse(state.isSuccess)
    }

    @Test
    fun setFailed_withErrorMessage() {
        val state = FlashTerminalLogState()
        state.setRunning()
        state.setFailed("something went wrong")
        assertEquals(TerminalLogState.FAILED, state.state.value)
        assertEquals("something went wrong", state.errorMessage)
    }

    @Test
    fun setFailed_withoutErrorMessage() {
        val state = FlashTerminalLogState()
        state.setRunning()
        state.setFailed()
        assertNull(state.errorMessage)
    }

    @Test
    fun reset_clearsState() {
        val state = FlashTerminalLogState()
        state.setRunning()
        state.appendLine("line1")
        state.appendLine("line2")
        state.setSuccess()
        assertTrue(state.isCompleted)

        state.reset()
        assertEquals(TerminalLogState.IDLE, state.state.value)
        assertTrue(state.log.isEmpty())
        assertFalse(state.isRunning)
        assertFalse(state.isCompleted)
        assertFalse(state.isSuccess)
        assertNull(state.errorMessage)
    }

    @Test
    fun fullLifecycle_idleToRunningToSuccess() {
        val state = FlashTerminalLogState()

        // Initially IDLE
        assertEquals(TerminalLogState.IDLE, state.state.value)
        assertTrue(state.log.isEmpty())

        // RUNNING phase
        state.setRunning()
        assertEquals(TerminalLogState.RUNNING, state.state.value)
        assertTrue(state.isRunning)
        assertFalse(state.isCompleted)

        // Append log lines during execution
        state.appendLine("Starting flash...")
        state.appendLine("Verifying image...")
        state.appendLine("Writing partition...")
        assertEquals(3, state.log.size)
        assertEquals("Starting flash...", state.log[0])
        assertEquals("Writing partition...", state.log[2])

        // SUCCESS phase
        state.setSuccess()
        assertEquals(TerminalLogState.SUCCESS, state.state.value)
        assertFalse(state.isRunning)
        assertTrue(state.isCompleted)
        assertTrue(state.isSuccess)

        // Log preserved after success
        assertEquals(3, state.log.size)
    }

    @Test
    fun fullLifecycle_idleToRunningToFailed() {
        val state = FlashTerminalLogState()

        // Initially IDLE
        assertEquals(TerminalLogState.IDLE, state.state.value)
        assertTrue(state.log.isEmpty())

        // RUNNING phase
        state.setRunning()
        assertEquals(TerminalLogState.RUNNING, state.state.value)
        assertTrue(state.isRunning)

        // Append some log
        state.appendLine("Flashing boot image...")
        state.appendLine("Verifying checksum...")
        assertEquals(2, state.log.size)

        // FAILED phase
        state.setFailed("verification failed: checksum mismatch")
        assertEquals(TerminalLogState.FAILED, state.state.value)
        assertFalse(state.isRunning)
        assertTrue(state.isCompleted)
        assertFalse(state.isSuccess)
        assertEquals("verification failed: checksum mismatch", state.errorMessage)

        // Log preserved after failure
        assertEquals(2, state.log.size)
        assertEquals("Flashing boot image...", state.log[0])
    }

    @Test
    fun setRunning_resetsProgress() {
        val state = FlashTerminalLogState()
        assertEquals(0f, state.progress.value)
        assertEquals("", state.currentStep.value)

        state.setRunning()
        assertEquals(0f, state.progress.value)
        assertEquals("", state.currentStep.value)
    }

    @Test
    fun setSuccess_setsProgressTo1() {
        val state = FlashTerminalLogState()
        state.setRunning()
        state.setSuccess()
        assertEquals(1f, state.progress.value)
    }

    @Test
    fun logText_singleLine() {
        val state = FlashTerminalLogState()
        state.appendLine("single log entry")
        assertEquals("single log entry", state.logText)
    }

    @Test
    fun logText_multipleLines() {
        val state = FlashTerminalLogState()
        state.appendLine("first line")
        state.appendLine("second line")
        assertEquals("first line\nsecond line", state.logText)
    }
}
