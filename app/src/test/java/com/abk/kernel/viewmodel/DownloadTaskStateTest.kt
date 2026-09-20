package com.abk.kernel.viewmodel

import com.abk.kernel.data.model.BuildArtifact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadTaskStateTest {

    @Test
    fun convertsBuildArtifactToAutomaticDownloadTask() {
        val artifact = BuildArtifact(
            id = 88L,
            name = "AnyKernel3-android15.zip",
            sizeInBytes = 4096L,
            archiveDownloadUrl = "https://example.com/artifact.zip",
            expired = false,
            createdAt = "2026-05-26T00:00:00Z",
            runId = 42L,
            runTitle = "Android 15 build",
            runNumber = 123,
            runCreatedAt = "2026-05-26T00:00:00Z"
        )

        val task = artifact.toActiveDownloadTask(automatic = true)

        assertEquals(88L, task.key)
        assertEquals(42L, task.runId)
        assertEquals("AnyKernel3-android15.zip", task.name)
        assertTrue(task.automatic)
        assertEquals(0, task.progress)
    }

    @Test
    fun sortsDownloadTasksByRunNumberDescending() {
        val early = BuildArtifact(
            id = 1L,
            name = "boot-old.img",
            sizeInBytes = 1L,
            archiveDownloadUrl = "https://example.com/1",
            expired = false,
            createdAt = "",
            runId = 10L,
            runTitle = "Older",
            runNumber = 10,
            runCreatedAt = ""
        ).toActiveDownloadTask(automatic = false)
        val latest = BuildArtifact(
            id = 2L,
            name = "boot-new.img",
            sizeInBytes = 1L,
            archiveDownloadUrl = "https://example.com/2",
            expired = false,
            createdAt = "",
            runId = 20L,
            runTitle = "Newer",
            runNumber = 20,
            runCreatedAt = ""
        ).toActiveDownloadTask(automatic = true)

        val sorted = listOf(early, latest).sortedDownloadTasks()

        assertEquals(listOf(latest, early), sorted)
    }

    @Test
    fun derivesIsDownloadingFromTasksAndProgress() {
        val task = BuildArtifact(
            id = 9L,
            name = "kernel.zip",
            sizeInBytes = 1L,
            archiveDownloadUrl = "https://example.com/kernel.zip",
            expired = false,
            createdAt = "",
            runId = 100L,
            runTitle = "Kernel",
            runNumber = 5,
            runCreatedAt = ""
        ).toActiveDownloadTask(automatic = false)

        val withTask = MainUiState().withDownloadState(activeDownloadTasks = listOf(task))
        val withProgressOnly = MainUiState().withDownloadState(downloadProgress = mapOf(9L to 50))
        val idle = MainUiState().withDownloadState()

        assertTrue(withTask.isDownloading)
        assertTrue(withProgressOnly.isDownloading)
        assertFalse(idle.isDownloading)
    }
}
