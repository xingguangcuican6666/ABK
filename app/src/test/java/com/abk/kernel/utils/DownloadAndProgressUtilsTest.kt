package com.abk.kernel.utils

import com.abk.kernel.data.model.ArtifactType
import com.abk.kernel.data.model.WorkflowJob
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.data.model.WorkflowStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DownloadAndProgressUtilsTest {

    @org.junit.Before
    fun setUp() {
        WorkflowStepI18n.resetForTest()
    }

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
    fun recognizesBundledNoticeFileNamesCaseInsensitively() {
        assertTrue(DownloadUtils.isBundledNoticeFileName("LICENSE"))
        assertTrue(DownloadUtils.isBundledNoticeFileName("license"))
        assertTrue(DownloadUtils.isBundledNoticeFileName("THIRD_PARTY_NOTICES.md"))
        assertFalse(DownloadUtils.isBundledNoticeFileName("KernelSU-Manager.apk"))
    }

    @Test
    fun collectArtifactPayloadFilesSkipsNoticeFilesEvenAsFallback() {
        val root = createTempDir("download-utils-test").apply {
            deleteOnExit()
        }
        File(root, "LICENSE").writeText("license text")
        File(root, "THIRD_PARTY_NOTICES.md").writeText("notices")
        File(root, "KernelSU-Manager.apk").writeBytes(byteArrayOf(0x50, 0x4B))

        val candidates = DownloadUtils.collectArtifactPayloadFiles(root)

        assertEquals(listOf("KernelSU-Manager.apk"), candidates.map { it.name })
    }

    @Test
    fun normalizesDownloadDirectoryPaths() {
        assertTrue(DownloadDirectoryUtils.normalizeDirectoryPath("/sdcard/Download/ABK/").endsWith("/sdcard/Download/ABK"))
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
