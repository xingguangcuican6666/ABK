package com.abk.kernel.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.BuildStatus
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.ui.common.AbkPage
import com.abk.kernel.ui.common.MutedText
import com.abk.kernel.ui.common.PageSection
import com.abk.kernel.ui.common.StatusBadge
import com.abk.kernel.ui.common.formatRunTimestamp
import com.abk.kernel.ui.common.statusLabel
import com.abk.kernel.ui.common.tone
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.viewmodel.MainUiState
import com.abk.kernel.viewmodel.MainViewModel
import io.github.xingguangcuican6666.abk.ui.components.AbkAlert
import io.github.xingguangcuican6666.abk.ui.components.AbkButton
import io.github.xingguangcuican6666.abk.ui.components.AbkButtonStyle
import io.github.xingguangcuican6666.abk.ui.components.AbkCard
import io.github.xingguangcuican6666.abk.ui.components.AbkDivider
import io.github.xingguangcuican6666.abk.ui.components.AbkEmptyState
import io.github.xingguangcuican6666.abk.ui.components.AbkHeading
import io.github.xingguangcuican6666.abk.ui.components.AbkIconButton
import io.github.xingguangcuican6666.abk.ui.components.AbkLinearProgress
import io.github.xingguangcuican6666.abk.ui.components.AbkList
import io.github.xingguangcuican6666.abk.ui.components.AbkListItem
import io.github.xingguangcuican6666.abk.ui.components.AbkSize
import io.github.xingguangcuican6666.abk.ui.components.AbkText
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone

private val ListRowInset = 20.dp

/**
 * 状态：仓库同步、进行中的构建进度、最近构建记录。
 * Read-only summary; the only actions refresh data or ask for Root.
 */
@Composable
fun StatusDestination(
    vm: MainViewModel,
    state: MainUiState,
    onChildPageVisibleChange: (Boolean) -> Unit,
) {
    LaunchedEffect(Unit) {
        onChildPageVisibleChange(false)
        vm.loadRecentRuns(showRefreshIndicator = false, lightweight = true)
    }

    val kernelVersion = remember { RootUtils.getKernelVersion() }

    AbkPage(
        title = stringResource(R.string.status_title),
        // The badge already names the Root state, so the headline carries no second sentence.
        heroActions = {
            StatusBadge(
                tone = if (state.rootGranted) AbkTone.Success else AbkTone.Warning,
                label = stringResource(
                    if (state.rootGranted) R.string.status_root_authorized
                    else R.string.status_root_unauthorized
                ),
            )
        },
        topBarActions = {
            AbkIconButton(
                icon = AbkIcons.Refresh,
                contentDescription = stringResource(R.string.refresh),
                onClick = { vm.loadRecentRuns(showRefreshIndicator = true, lightweight = false) },
                enabled = !state.isRefreshingRecentRuns,
                size = AbkSize.Small,
            )
        },
    ) {
        if (!state.rootGranted) {
            AbkAlert(
                title = stringResource(R.string.root_check_title),
                message = stringResource(R.string.root_check_desc),
                tone = AbkTone.Warning,
                actions = {
                    AbkButton(
                        onClick = { vm.requestRoot() },
                        style = AbkButtonStyle.Primary,
                        enabled = !state.isLoading,
                        loading = state.isLoading,
                    ) { AbkText(stringResource(R.string.grant_root)) }
                },
            )
        }

        // The repository line: where builds are dispatched from and whether it has drifted.
        AbkCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AbkText(
                        stringResource(R.string.status_fork),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AbkTheme.colors.inkMuted,
                    )
                    AbkText(
                        state.forkRepo?.name ?: stringResource(R.string.status_no_fork),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (state.forkRepo != null) {
                    if (state.behindBy > 0) {
                        StatusBadge(AbkTone.Warning, stringResource(R.string.status_fork_behind, state.behindBy))
                    } else {
                        StatusBadge(AbkTone.Success, stringResource(R.string.status_synced))
                    }
                }
            }
            // The running kernel is the one fact this page should always show; the manager
            // version and the app version live in Settings, where they belong.
            MutedText(kernelVersion)
        }

        BuildProgressCard(state, vm)

        PageSection(title = stringResource(R.string.status_recent_runs_title)) {
            val runs = state.recentRuns
            if (runs.isEmpty()) {
                AbkEmptyState(
                    title = stringResource(R.string.status_no_build),
                    description = stringResource(R.string.status_recent_empty_desc),
                    icon = AbkIcons.Build,
                )
            } else {
                AbkList {
                    runs.take(5).forEachIndexed { index, run ->
                        if (index > 0) AbkDivider(Modifier.padding(horizontal = ListRowInset))
                        AbkListItem(
                            title = "#${run.runNumber} · ${run.displayTitle ?: run.name ?: "-"}",
                            description = formatRunTimestamp(run.createdAt),
                            leading = {
                                AbkIcon(
                                    imageVector = AbkIcons.Dot,
                                    contentDescription = null,
                                    tint = AbkTheme.colors.status(run.tone()).color,
                                )
                            },
                            trailing = { RunTrailing(run) { vm.cancelWorkflowRun(run.id) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RunTrailing(run: WorkflowRun, onCancel: () -> Unit) {
    val active = run.status in setOf("queued", "waiting", "requested", "pending", "in_progress")
    if (active) {
        AbkIconButton(
            icon = AbkIcons.Close,
            contentDescription = stringResource(R.string.status_cancel),
            onClick = onCancel,
            size = AbkSize.Small,
        )
    } else {
        StatusBadge(run.tone(), run.statusLabel())
    }
}

@Composable
private fun BuildProgressCard(state: MainUiState, vm: MainViewModel) {
    val status = state.kernelBuildStatus
    val run = state.kernelCurrentRun
    val progress = state.kernelBuildProgress
    AbkCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AbkHeading(stringResource(R.string.status_build), Modifier.weight(1f))
            StatusBadge(status.tone(), buildStatusLabel(status))
        }
        when {
            status == BuildStatus.IN_PROGRESS || status == BuildStatus.QUEUED -> {
                val fraction = if (progress.percent > 0) progress.percent / 100f else null
                AbkLinearProgress(
                    progress = fraction,
                    tone = AbkTone.Accent,
                    label = stringResource(R.string.build_progress_title),
                )
                val step = progress.currentStep
                val steps = stringResource(
                    R.string.status_steps_complete,
                    progress.completedSteps,
                    progress.totalSteps,
                )
                AbkText(
                    text = if (step.isNullOrBlank()) steps else "$step · $steps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AbkTheme.colors.inkSoft,
                )
                if (run != null) {
                    AbkButton(
                        onClick = { vm.cancelWorkflowRun(run.id) },
                        style = AbkButtonStyle.Outline,
                        enabled = run.id !in state.cancellingWorkflowRunIds,
                    ) {
                        AbkText(stringResource(R.string.status_cancel))
                    }
                }
            }
            run == null -> MutedText(stringResource(R.string.status_no_running_build))
            else -> {
                AbkText(
                    text = if (status == BuildStatus.SUCCESS) {
                        stringResource(R.string.status_recent_build_success)
                    } else {
                        stringResource(R.string.status_recent_build_failed)
                    },
                    color = AbkTheme.colors.status(status.tone()).color,
                )
                MutedText(
                    stringResource(R.string.status_view_details, run.runNumber) + " · " +
                        formatRunTimestamp(run.createdAt)
                )
            }
        }
        if (state.managerBuildStatus == BuildStatus.IN_PROGRESS) {
            MutedText(stringResource(R.string.status_manager_progress_sync))
            AbkLinearProgress(
                progress = state.managerBuildProgress.percent / 100f,
                tone = AbkTone.Info,
                label = stringResource(R.string.status_manager_build),
            )
        }
    }
}

@Composable
private fun buildStatusLabel(status: BuildStatus): String = when (status) {
    BuildStatus.IDLE -> stringResource(R.string.status_idle)
    BuildStatus.QUEUED -> stringResource(R.string.status_queued)
    BuildStatus.IN_PROGRESS -> stringResource(R.string.status_in_progress)
    BuildStatus.SUCCESS -> stringResource(R.string.status_success)
    BuildStatus.FAILURE -> stringResource(R.string.status_failure)
    BuildStatus.CANCELLED -> stringResource(R.string.status_cancelled_label)
}
