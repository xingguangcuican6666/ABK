package com.abk.kernel.utils

import com.abk.kernel.data.model.ArtifactType
import com.abk.kernel.data.model.Artifact
import com.abk.kernel.data.model.APP_UPDATE_LINE_DEV
import com.abk.kernel.data.model.APP_UPDATE_LINE_NORMAL
import com.abk.kernel.data.model.DownloadedArtifact
import com.abk.kernel.data.model.PREBUILT_GKI_RUN_ID
import com.abk.kernel.data.model.PrebuiltGkiAsset
import com.abk.kernel.data.model.WorkflowJob
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.data.model.WorkflowStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertEquals(ArtifactType.KERNEL_IMG, DownloadUtils.classifyArtifact("oneplus_12_sukisu_raw-image-debug"))
        assertEquals(ArtifactType.ANYKERNEL3, DownloadUtils.classifyArtifact("AnyKernel3-android14.zip"))
        assertEquals(ArtifactType.SUSFS_MODULE, DownloadUtils.classifyArtifact("susfs-module.zip"))
        assertEquals(ArtifactType.ABK_MANAGER, DownloadUtils.classifyArtifact("abk-apks"))
        assertEquals(ArtifactType.KSU_MANAGER, DownloadUtils.classifyArtifact("KernelSU-Manager.apk"))
        assertEquals(ArtifactType.OTHER, DownloadUtils.classifyArtifact("patch-rejects.zip"))
    }

    @Test
    fun doesNotAutoDownloadAbkManagerArtifacts() {
        assertFalse(
            DownloadUtils.shouldAutoDownload(
                Artifact(
                    id = 1L,
                    name = "abk-apks",
                    sizeInBytes = 1L,
                    archiveDownloadUrl = "https://example.com/abk.zip",
                    expired = false,
                    createdAt = ""
                )
            )
        )
    }

    @Test
    fun matchesPrebuiltDownloadsBySourceAssetName() {
        val asset = PrebuiltGkiAsset(
            id = 1L,
            name = "android14-6.1.162-gki.zip",
            sizeBytes = 1L,
            browserDownloadUrl = "https://example.com/android14.zip",
            releaseTag = "gki",
            releaseName = "GKI",
            releaseHtmlUrl = "https://example.com/release",
            publishedAt = "2026-06-08T00:00:00Z"
        )
        val downloaded = DownloadedArtifact(
            id = 1L,
            name = "boot.img",
            filePath = "/storage/emulated/0/ABK/prebuilt-gki/some-other-layout/boot.img.bundle.zip",
            type = ArtifactType.KERNEL_IMG,
            sizeBytes = 1L,
            runId = PREBUILT_GKI_RUN_ID,
            runTitle = "预编译 GKI",
            sourceAssetId = asset.id,
            sourceAssetName = asset.name,
            category = ArtifactType.KERNEL_IMG.toArtifactCategory()
        )

        assertTrue(DownloadUtils.matchesDownloadedPrebuilt(downloaded, asset))
    }

    @Test
    fun matchesLegacyPrebuiltDownloadsByPath() {
        val asset = PrebuiltGkiAsset(
            id = 2L,
            name = "AnyKernel3-android14-6.1.162.zip",
            sizeBytes = 1L,
            browserDownloadUrl = "https://example.com/ak3.zip",
            releaseTag = "gki",
            releaseName = "GKI",
            releaseHtmlUrl = "https://example.com/release",
            publishedAt = "2026-06-08T00:00:00Z"
        )
        val downloaded = DownloadedArtifact(
            id = 2L,
            name = "AnyKernel3-android14-6.1.162.zip",
            filePath = "/storage/emulated/0/ABK/prebuilt-gki/AnyKernel3-android14-6.1.162.zip/AnyKernel3-android14-6.1.162.zip/AnyKernel3-android14-6.1.162.zip.bundle.zip",
            type = ArtifactType.ANYKERNEL3,
            sizeBytes = 1L,
            runId = PREBUILT_GKI_RUN_ID,
            runTitle = "预编译 GKI",
            category = ArtifactType.ANYKERNEL3.toArtifactCategory()
        )

        assertTrue(DownloadUtils.matchesDownloadedPrebuilt(downloaded, asset))
    }

    @Test
    fun selectsExpectedApkForAppUpdateChannel() {
        val root = createTempDir("app-update-select")
        val release = File(root, "app-release.apk").apply { writeText("release") }
        val debug = File(root, "app-debug.apk").apply { writeText("debug") }
        val devRelease = File(root, "app-release-dev.apk").apply { writeText("dev-release") }
        val unsigned = File(root, "app-release-unsigned.apk").apply { writeText("unsigned") }

        assertEquals(release, DownloadUtils.selectAppUpdateApk(listOf(debug, release, devRelease, unsigned), APP_UPDATE_LINE_NORMAL))
        assertEquals(devRelease, DownloadUtils.selectAppUpdateApk(listOf(debug, release, devRelease, unsigned), APP_UPDATE_LINE_DEV))
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
    fun parsesForkSigningPublicKeyFromSupportedStoredFormats() {
        val material = ForkSigningManager.generateSigningMaterial()
        val jsonValue = """{"publicKeyBase64":"${material.publicKeyBase64}"}"""

        assertEquals(material.publicKeyPem, ForkSigningManager.publicKeyPemFromStoredValue(material.publicKeyBase64))
        assertEquals(material.publicKeyPem, ForkSigningManager.publicKeyPemFromStoredValue(material.publicKeyPem))
        assertEquals(material.publicKeyPem, ForkSigningManager.publicKeyPemFromStoredValue(jsonValue))
    }

    @Test
    fun returnsNullForInvalidStoredForkSigningPublicKeyValue() {
        assertNull(ForkSigningManager.publicKeyPemFromStoredValue("{"))
        assertNull(ForkSigningManager.publicKeyPemFromStoredValue("""{"unexpected":true}"""))
        assertNull(ForkSigningManager.publicKeyPemFromStoredValue(""))
        assertNull(ForkSigningManager.publicKeyPemFromStoredValue(null))
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
