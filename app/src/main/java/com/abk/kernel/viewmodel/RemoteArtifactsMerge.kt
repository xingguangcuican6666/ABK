package com.abk.kernel.viewmodel

import com.abk.kernel.data.model.BuildArtifact

internal const val RECENT_WORKFLOW_RUNS_PAGE_SIZE = 40
internal const val MAX_REMOTE_ARTIFACT_RUNS = RECENT_WORKFLOW_RUNS_PAGE_SIZE
private const val MAX_PERSISTED_REMOTE_ARTIFACTS = 240

internal fun mergeRemoteArtifacts(
    existing: List<BuildArtifact>,
    incoming: List<BuildArtifact>,
): List<BuildArtifact> {
    val incomingRunIds = incoming.map { it.runId }.toSet()
    return (incoming + existing.filterNot { it.runId in incomingRunIds })
        .distinctBy { it.id }
        .sortedForDisplay()
        .take(MAX_PERSISTED_REMOTE_ARTIFACTS)
}

internal fun List<BuildArtifact>.sortedForDisplay(): List<BuildArtifact> =
    sortedWith(
        compareByDescending<BuildArtifact> { it.runNumber }
            .thenByDescending { it.runId }
            .thenBy { it.name }
    )
