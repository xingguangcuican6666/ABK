package com.abk.kernel.ui.screens.flash

import com.abk.kernel.data.model.ArtifactCategory
import com.abk.kernel.data.model.ArtifactType
import com.abk.kernel.data.model.BuildArtifact
import com.abk.kernel.data.model.WorkflowRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashRoutingTest {

    @Test
    fun filtersAbkManagerArtifactsOutOfWorkflowGroups() {
        val groups = buildWorkflowGroups(
            remoteArtifacts = listOf(
                BuildArtifact(
                    id = 1L,
                    name = "abk-apks",
                    sizeInBytes = 1L,
                    archiveDownloadUrl = "https://example.com/abk.zip",
                    expired = false,
                    createdAt = "",
                    runId = 99L,
                    runTitle = "Build ABK App",
                    runNumber = 1,
                    runCreatedAt = ""
                )
            ),
            downloadedArtifacts = emptyList(),
            unlinkedWorkflowTitle = "Unlinked",
            runs = emptyMap()
        )

        assertEquals(1, groups.size)
        val group = groups.first()
        assertTrue(group.remote.isEmpty())
        assertTrue(group.local.isEmpty())
        assertTrue(group.categories.isEmpty())
        assertFalse(group.hasManagerArtifact())
    }

    @Test
    fun hidesAbkManagerWorkflowFromFlashList() {
        val run = WorkflowRun(
            id = 77L,
            name = "Build ABK App Dev",
            status = "completed",
            conclusion = "success",
            htmlUrl = "",
            createdAt = "",
            updatedAt = "",
            runNumber = 12,
            workflowId = 1L,
            headBranch = "dev",
            displayTitle = "Build ABK App Dev"
        )
        val group = emptyWorkflowGroupFor(run, "Unlinked")

        assertTrue(run.isAbkManagerFlashRun(group.runTitle))
        assertFalse(group.shouldAppearInWorkflowList(run))
    }

    @Test
    fun keepsKsuManagerArtifactsVisible() {
        val groups = buildWorkflowGroups(
            remoteArtifacts = listOf(
                BuildArtifact(
                    id = 2L,
                    name = "KernelSU-Manager.apk",
                    sizeInBytes = 1L,
                    archiveDownloadUrl = "https://example.com/ksu.apk",
                    expired = false,
                    createdAt = "",
                    runId = 100L,
                    runTitle = "KernelSU Manager",
                    runNumber = 2,
                    runCreatedAt = ""
                )
            ),
            downloadedArtifacts = emptyList(),
            unlinkedWorkflowTitle = "Unlinked",
            runs = emptyMap()
        )

        assertEquals(1, groups.size)
        val group = groups.first()
        assertEquals(listOf(ArtifactCategory.MANAGER), group.categories.toList())
        assertTrue(group.hasManagerArtifact())
        assertEquals(ArtifactType.KSU_MANAGER, artifactTypeForGroup(group))
    }

    private fun artifactTypeForGroup(group: WorkflowArtifactGroup): ArtifactType =
        group.remote.firstOrNull()?.let { com.abk.kernel.utils.DownloadUtils.classifyArtifact(it.name) }
            ?: group.local.firstOrNull()?.type
            ?: ArtifactType.OTHER
}
