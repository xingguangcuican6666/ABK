@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens.flash

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.ActiveDownloadTask
import com.abk.kernel.data.model.ArtifactCategory
import com.abk.kernel.data.model.ArtifactType
import com.abk.kernel.data.model.DownloadedArtifact
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.utils.FlashFilter
import com.abk.kernel.utils.FlashFilterKernelKind
import com.abk.kernel.utils.FlashFilterWorkflowState
import com.abk.kernel.utils.FlashWorkflowFilter
import com.abk.kernel.utils.WorkflowPrimary
import io.github.xingguangcuican6666.abk.ui.components.AbkCheckbox
import io.github.xingguangcuican6666.abk.ui.components.AbkDivider
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons

internal val artifactCategoryOrder =
    listOf(ArtifactCategory.KERNEL, ArtifactCategory.MANAGER, ArtifactCategory.MODULE)

internal const val MAX_VISIBLE_WORKFLOWS_PER_CATEGORY = 15

internal fun DownloadedArtifact.isInstallableApk(): Boolean =
    type == ArtifactType.KSU_MANAGER ||
        (type != ArtifactType.ABK_MANAGER && name.endsWith(".apk", ignoreCase = true))

@StringRes
internal fun ArtifactCategory.labelRes(): Int =
    when (this) {
        ArtifactCategory.KERNEL -> R.string.flash_category_kernel
        ArtifactCategory.MANAGER -> R.string.flash_category_manager
        ArtifactCategory.MODULE -> R.string.flash_category_module
    }

internal fun ArtifactCategory.icon(): ImageVector =
    when (this) {
        ArtifactCategory.KERNEL -> AbkIcons.Cpu
        ArtifactCategory.MANAGER -> AbkIcons.Settings
        ArtifactCategory.MODULE -> AbkIcons.Modules
    }

internal fun flashFilterToMap(f: FlashFilter): Map<String, String> =
    mapOf(
        "ke" to f.kernelEnabled.toString(),
        "kk" to f.kernelKinds.joinToString(",") { it.name },
        "ws" to f.workflowStates.joinToString(",") { it.name },
    )

internal fun flashFilterFromMap(m: Map<String, String?>): FlashFilter =
    FlashFilter(
        kernelEnabled = m["ke"]?.toBooleanStrictOrNull() ?: true,
        kernelKinds =
            m["kk"]
                ?.takeIf { it.isNotBlank() }
                ?.split(",")
                ?.mapNotNull { runCatching { FlashFilterKernelKind.valueOf(it) }.getOrNull() }
                ?.toSet() ?: emptySet(),
        workflowStates =
            m["ws"]
                ?.takeIf { it.isNotBlank() }
                ?.split(",")
                ?.mapNotNull { runCatching { FlashFilterWorkflowState.valueOf(it) }.getOrNull() }
                ?.toSet() ?: emptySet(),
    )

internal val FlashFilterSaver =
    androidx.compose.runtime.saveable.Saver<FlashFilter, Map<String, String>>(
        save = { flashFilterToMap(it) },
        restore = { flashFilterFromMap(it) },
    )

internal fun FlashFilter.toJsonString(): String =
    com.google.gson.Gson().toJson(flashFilterToMap(this))

@Suppress("UNCHECKED_CAST")
internal fun String.toFlashFilterOrNull(): FlashFilter? =
    runCatching {
            val raw = com.google.gson.Gson().fromJson(this, Map::class.java) as Map<String, String?>
            flashFilterFromMap(raw)
        }
        .getOrNull()

internal fun WorkflowArtifactGroup.hasRemoteManagerArtifact(): Boolean =
    cachedHasRemoteManagerArtifact

internal fun WorkflowArtifactGroup.hasManagerArtifact(): Boolean = cachedHasManagerArtifact

internal fun WorkflowArtifactGroup.hasKernelArtifact(): Boolean = cachedHasKernelArtifact

internal fun WorkflowArtifactGroup.shouldShowParameterDetails(run: WorkflowRun?): Boolean =
    FlashWorkflowFilter.shouldShowParameterDetails(
        run = run,
        runTitle = runTitle,
        hasKernelArtifact = hasKernelArtifact(),
        hasManagerArtifact = hasManagerArtifact(),
    )

internal fun WorkflowArtifactGroup.hasRemoteKernelArtifact(): Boolean =
    cachedHasRemoteKernelArtifact

internal fun WorkflowArtifactGroup.hasSusfsModuleArtifact(): Boolean = cachedHasSusfsModuleArtifact

/** Manifest notices are scoped to an explicitly opened workflow detail page. */
internal fun manifestNoticeCandidates(
    flashDetailRouteActive: Boolean,
    selectedRunId: Long?,
    workflowGroups: List<WorkflowArtifactGroup>,
): List<DownloadedArtifact> {
    if (!flashDetailRouteActive || selectedRunId == null) return emptyList()
    return workflowGroups
        .firstOrNull { it.runId == selectedRunId }
        ?.local
        ?.filter { it.verified && it.manifestClientNotice != null }
        .orEmpty()
}

internal fun WorkflowRun?.workflowState(): FlashFilterWorkflowState? =
    when {
        this == null -> null
        this.isActiveFlashRun() -> FlashFilterWorkflowState.Running
        else -> FlashFilterWorkflowState.Finished
    }

internal fun hasDownloadedFilesForRun(
    runId: Long,
    downloadedArtifacts: List<DownloadedArtifact>,
    workflowGroups: List<WorkflowArtifactGroup>,
    activeDownloadTasks: List<ActiveDownloadTask>,
): Boolean {
    if (downloadedArtifacts.any { it.runId == runId }) return true
    if (workflowGroups.any { it.runId == runId && it.local.isNotEmpty() }) return true
    if (activeDownloadTasks.any { it.runId == runId }) return true
    return false
}

internal fun WorkflowArtifactGroup.shouldAppearInWorkflowList(run: WorkflowRun?): Boolean =
    if (run.isAbkManagerFlashRun(runTitle)) {
        false
    } else if (remote.isEmpty() && local.isEmpty()) {
        run?.isSuccessfulKernelFlashRun() == true
    } else
        when (
            FlashWorkflowFilter.primaryKind(
                run = run,
                runTitle = runTitle,
                hasKernelArtifact = hasKernelArtifact(),
                hasManagerArtifact = hasManagerArtifact(),
            )
        ) {
            WorkflowPrimary.Kernel -> hasRemoteKernelArtifact()
            else -> true
        }

internal fun limitWorkflowGroupsForDisplay(
    groups: List<WorkflowArtifactGroup>,
    runs: Map<Long, WorkflowRun>,
): List<WorkflowArtifactGroup> {
    val counts = mutableMapOf<WorkflowPrimary, Int>()
    return groups.filter { group ->
        val run = runs[group.runId]
        val bucket =
            when (
                FlashWorkflowFilter.primaryKind(
                    run = run,
                    runTitle = group.runTitle,
                    hasKernelArtifact = group.hasKernelArtifact(),
                    hasManagerArtifact = group.hasManagerArtifact(),
                )
            ) {
                WorkflowPrimary.Manager -> WorkflowPrimary.Manager
                else -> WorkflowPrimary.Kernel
            }
        val next = (counts[bucket] ?: 0) + 1
        if (next > MAX_VISIBLE_WORKFLOWS_PER_CATEGORY) {
            false
        } else {
            counts[bucket] = next
            true
        }
    }
}

@StringRes
internal fun FlashFilterKernelKind.labelRes() =
    when (this) {
        FlashFilterKernelKind.BakaSu -> R.string.flash_filter_kernel_bakasu
        FlashFilterKernelKind.SukiSu -> R.string.flash_filter_kernel_sukisu
        FlashFilterKernelKind.Official -> R.string.flash_filter_kernel_official
        FlashFilterKernelKind.None -> R.string.flash_filter_kernel_none
    }

@StringRes
internal fun FlashFilterKernelKind.shortLabelRes() =
    when (this) {
        FlashFilterKernelKind.BakaSu -> R.string.flash_kernel_bakasu
        FlashFilterKernelKind.SukiSu -> R.string.flash_kernel_sukisu
        FlashFilterKernelKind.Official -> R.string.flash_kernel_official
        FlashFilterKernelKind.None -> R.string.flash_kernel_none
    }

@StringRes
internal fun FlashFilterWorkflowState.labelRes() =
    when (this) {
        FlashFilterWorkflowState.Running -> R.string.flash_filter_workflow_running
        FlashFilterWorkflowState.Finished -> R.string.flash_filter_workflow_finished
    }

@Composable
internal fun FlashFilterButton(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    filter: FlashFilter,
    onFilterChange: (FlashFilter) -> Unit,
) {
    val filterState =
        stringResource(
            if (expanded) R.string.flash_filter_expanded else R.string.flash_filter_collapsed
        )
    Box {
        FlashAction(
            label = stringResource(R.string.flash_filter_title),
            icon = AbkIcons.Controls,
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier.semantics { stateDescription = filterState },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.widthIn(min = 240.dp),
        ) {
            FilterCheckRow(
                label = stringResource(R.string.flash_filter_kernel),
                checked = filter.kernelEnabled,
                onCheckedChange = { onFilterChange(filter.copy(kernelEnabled = it)) },
            )
            FlashFilterKernelKind.entries.forEach { kind ->
                FilterCheckRow(
                    label = stringResource(kind.labelRes()),
                    checked = kind in filter.kernelKinds,
                    indent = true,
                    onCheckedChange = { add ->
                        onFilterChange(
                            filter.copy(
                                kernelKinds =
                                    if (add) filter.kernelKinds + kind
                                    else filter.kernelKinds - kind
                            )
                        )
                    },
                )
            }
            AbkDivider()
            // No top-level "Workflow" toggle — only Running/Finished sub-filters.
            FlashFilterWorkflowState.entries.forEach { st ->
                FilterCheckRow(
                    label = stringResource(st.labelRes()),
                    checked = st in filter.workflowStates,
                    onCheckedChange = { add ->
                        onFilterChange(
                            filter.copy(
                                workflowStates =
                                    if (add) filter.workflowStates + st
                                    else filter.workflowStates - st
                            )
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun FilterCheckRow(
    label: String,
    checked: Boolean,
    indent: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
) {
    AbkCheckbox(
        state = ToggleableState(checked),
        onClick = { onCheckedChange(!checked) },
        label = label,
        modifier = Modifier.fillMaxWidth().padding(start = if (indent) 24.dp else 4.dp, end = 8.dp),
    )
}
