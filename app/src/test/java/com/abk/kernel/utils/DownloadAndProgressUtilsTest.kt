package com.abk.kernel.utils

import com.abk.kernel.data.model.ArtifactType
import com.abk.kernel.data.model.WorkflowJob
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.data.model.WorkflowStep
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadAndProgressUtilsTest {

    @Test
    fun classifiesKnownArtifactNames() {
        assertEquals(ArtifactType.KERNEL_PACKAGE, DownloadUtils.classifyArtifact("GKI_kernel-android14-6.1.zip"))
        assertEquals(ArtifactType.KERNEL_IMG, DownloadUtils.classifyArtifact("boot-android14-6.1.162.img"))
        assertEquals(ArtifactType.ANYKERNEL3, DownloadUtils.classifyArtifact("AnyKernel3-android14.zip"))
        assertEquals(ArtifactType.SUSFS_MODULE, DownloadUtils.classifyArtifact("susfs-module.zip"))
        assertEquals(ArtifactType.KSU_MANAGER, DownloadUtils.classifyArtifact("KernelSU-Manager.apk"))
        assertEquals(ArtifactType.OTHER, DownloadUtils.classifyArtifact("patch-rejects.zip"))
    }

    @Test
    fun buildProgressUsesActiveStepAndCompletedPercent() {
        val progress = BuildProgressUtils.from(
            run = WorkflowRun(
                id = 1L,
                name = "Build",
                status = "in_progress",
                conclusion = null,
                htmlUrl = "",
                createdAt = "",
                updatedAt = "",
                runNumber = 11,
                workflowId = 1L,
                headBranch = "main",
                displayTitle = null
            ),
            jobs = listOf(
                WorkflowJob(
                    id = 10L,
                    name = "kernel",
                    status = "in_progress",
                    conclusion = null,
                    steps = listOf(
                        WorkflowStep("Checkout", "completed", "success", 1),
                        WorkflowStep("Compile", "in_progress", null, 2)
                    )
                )
            )
        )

        assertEquals(50, progress.percent)
        assertEquals("kernel / Compile", progress.currentStep)
        assertEquals(1, progress.completedSteps)
        assertEquals(2, progress.totalSteps)
    }
}
