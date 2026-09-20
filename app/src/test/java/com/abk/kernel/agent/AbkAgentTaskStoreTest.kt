package com.abk.kernel.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AbkAgentTaskStoreTest {
    @Test
    fun completesSubmittedTask() {
        val snapshot = AbkAgentTaskStore.submit("test.task") {
            log("starting")
            success(message = "done", result = mapOf("ok" to true))
        }

        val finalSnapshot = waitForTask(snapshot.id)
        assertEquals("succeeded", finalSnapshot.state)
        assertEquals("done", finalSnapshot.message)
        assertTrue(finalSnapshot.output.contains("starting"))
        assertNotNull(finalSnapshot.result)
    }

    private fun waitForTask(taskId: String, attempts: Int = 40): AbkAgentTaskSnapshot {
        repeat(attempts) {
            val snapshot = requireNotNull(AbkAgentTaskStore.get(taskId))
            if (snapshot.state == "succeeded" || snapshot.state == "failed") {
                return snapshot
            }
            Thread.sleep(25L)
        }
        error("task did not complete: $taskId")
    }
}
