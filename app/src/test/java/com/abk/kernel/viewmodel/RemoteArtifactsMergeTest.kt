package com.abk.kernel.viewmodel

import com.abk.kernel.data.model.BuildArtifact
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteArtifactsMergeTest {

    @Test
    fun keepsIncomingRunWhenOldRepositoryCacheIsFull() {
        val existing = (1L..240L).map { offset ->
            artifact(
                id = offset,
                runId = 323_645_000_000L + offset,
                runNumber = 1000 + offset.toInt(),
                name = "old-$offset",
            )
        }
        val lineageArtifact = artifact(
            id = 9_393_097_992L,
            runId = 32_330_451_402L,
            runNumber = 5,
            name = "None_kernel-android12-5.10-256",
        )

        val merged = mergeRemoteArtifacts(existing, listOf(lineageArtifact))

        assertTrue(
            "Artifacts returned by the current refresh must survive an old cache",
            merged.any { it.id == lineageArtifact.id },
        )
        assertTrue(merged.size <= 240)
    }

    private fun artifact(
        id: Long,
        runId: Long,
        runNumber: Int,
        name: String,
    ) = BuildArtifact(
        id = id,
        name = name,
        sizeInBytes = 1L,
        archiveDownloadUrl = "https://example.com/$id.zip",
        expired = false,
        createdAt = "2026-08-20T04:17:01Z",
        runId = runId,
        runTitle = "Android kernel build",
        runNumber = runNumber,
        runCreatedAt = "2026-08-20T04:03:16Z",
    )
}
