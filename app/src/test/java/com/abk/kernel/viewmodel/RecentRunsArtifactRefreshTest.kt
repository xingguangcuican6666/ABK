package com.abk.kernel.viewmodel

import com.abk.kernel.data.model.WorkflowRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentRunsArtifactRefreshTest {

    @Test
    fun flashRefreshRequestsCompletedArtifactsForLineageLikeKernelRun() {
        val run = lineageLikeSuccessfulKernelRun()

        assertTrue(
            shouldIncludeCompletedArtifacts(
                lightweight = true,
                includeCompletedArtifacts = true,
            )
        )
        assertEquals(
            listOf(run),
            runsNeedingArtifactRefresh(
                runs = listOf(run),
                includeCompleted = true,
                includeCompletedPureManagers = false,
            )
        )
    }

    private fun lineageLikeSuccessfulKernelRun() = WorkflowRun(
        id = 32330451402L,
        name = "Android 内核构建-类 LineageOS 源码",
        status = "completed",
        conclusion = "success",
        htmlUrl = "https://github.com/xingguangcuicanrec/ABK/actions/runs/32330451402",
        createdAt = "2026-08-20T04:03:16Z",
        updatedAt = "2026-08-20T04:17:07Z",
        runNumber = 5,
        workflowId = 337872312L,
        headBranch = "dev",
        displayTitle = "Android 内核构建-类 LineageOS 源码",
    )
}
