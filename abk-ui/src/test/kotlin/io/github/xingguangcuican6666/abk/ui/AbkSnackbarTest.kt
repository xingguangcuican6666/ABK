package io.github.xingguangcuican6666.abk.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import io.github.xingguangcuican6666.abk.ui.components.AbkSnackbarHostState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

public class AbkSnackbarTest {
    @Test public fun requestsAreQueuedAndAnActionCompletesOnlyOnce(): Unit = runBlocking {
        withTimeout(5_000) {
            val state = AbkSnackbarHostState()
            var undoCount = 0
            val first = async(start = CoroutineStart.UNDISPATCHED) {
                val result = state.show("记录已删除", actionLabel = "撤销")
                if (result == SnackbarResult.ActionPerformed) undoCount++
                result
            }
            val data = checkNotNull(state.material.currentSnackbarData)
            val second = async(start = CoroutineStart.UNDISPATCHED) {
                state.show("下一条提示", duration = SnackbarDuration.Indefinite)
            }
            assertEquals("记录已删除", state.material.currentSnackbarData?.visuals?.message)
            assertFalse(second.isCompleted)
            data.performAction()
            data.performAction()
            data.dismiss()
            assertEquals(SnackbarResult.ActionPerformed, first.await())
            yield()
            assertEquals(1, undoCount)
            assertEquals("下一条提示", state.material.currentSnackbarData?.visuals?.message)
            state.dismissCurrent()
            assertEquals(SnackbarResult.Dismissed, second.await())
            assertNull(state.material.currentSnackbarData)
        }
    }

    @Test public fun cancelingQueuedRequestDoesNotBlockFollowingRequests(): Unit = runBlocking {
        withTimeout(5_000) {
            val state = AbkSnackbarHostState()
            val first = async(start = CoroutineStart.UNDISPATCHED) { state.show("第一条") }
            val canceled = async(start = CoroutineStart.UNDISPATCHED) { state.show("不应显示") }
            val last = async(start = CoroutineStart.UNDISPATCHED) { state.show("最后一条") }
            canceled.cancelAndJoin()
            state.dismissCurrent()
            assertEquals(SnackbarResult.Dismissed, first.await())
            yield()
            assertEquals("最后一条", state.material.currentSnackbarData?.visuals?.message)
            state.dismissCurrent()
            assertEquals(SnackbarResult.Dismissed, last.await())
            assertNull(state.material.currentSnackbarData)
        }
    }

    @Test public fun cancelingVisibleRequestClearsItAndShowsTheNextOne(): Unit = runBlocking {
        withTimeout(5_000) {
            val state = AbkSnackbarHostState()
            val visible = async(start = CoroutineStart.UNDISPATCHED) { state.show("当前提示") }
            val next = async(start = CoroutineStart.UNDISPATCHED) { state.show("后续提示") }
            visible.cancelAndJoin()
            yield()
            assertEquals("后续提示", state.material.currentSnackbarData?.visuals?.message)
            state.dismissCurrent()
            assertEquals(SnackbarResult.Dismissed, next.await())
            state.dismissCurrent()
            assertNull(state.material.currentSnackbarData)
        }
    }
}
