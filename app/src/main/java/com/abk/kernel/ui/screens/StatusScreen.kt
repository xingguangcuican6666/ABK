@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.abk.kernel.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.abk.kernel.BuildConfig
import com.abk.kernel.R
import com.abk.kernel.data.model.BuildProgress
import com.abk.kernel.data.model.BuildStatus
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.ui.blur.BlurScreenScaffold
import com.abk.kernel.ui.blur.blurredCardBackground
import com.abk.kernel.ui.blur.blurredCardSurfaceColor
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.theme.appPageBackgroundColor
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.viewmodel.MainUiState
import com.abk.kernel.viewmodel.MainViewModel
import io.github.xingguangcuican6666.abk.ui.components.*
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkShapes
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone

@Composable
fun StatusScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    runtimeNavigationEnabled: Boolean = false,
    onToggleRuntimeNavigation: () -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val ksuVersion = remember(state.rootGranted) {
        if (state.rootGranted) RootUtils.getKsuVersion() else "N/A"
    }
    val kernelVersion = remember(state.rootGranted) { RootUtils.getKernelVersion() }
    LaunchedEffect(Unit) { vm.loadRecentRuns() }
    StatusScreenContent(
        state = state,
        kernelVersion = kernelVersion,
        ksuVersion = ksuVersion,
        outerPadding = outerPadding,
        runtimeNavigationEnabled = runtimeNavigationEnabled,
        onToggleRuntimeNavigation = onToggleRuntimeNavigation,
        onRefresh = { vm.loadRecentRuns() },
        onRequestRoot = { vm.requestRoot() },
        onOpenRun = { run ->
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(run.htmlUrl))) }
        },
        onCancelRun = vm::cancelWorkflowRun
    )
}

/** State and callbacks are supplied by the host; previews and UI tests perform no device operations. */
@Composable
internal fun StatusScreenContent(
    state: MainUiState,
    kernelVersion: String,
    ksuVersion: String,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    runtimeNavigationEnabled: Boolean = false,
    onToggleRuntimeNavigation: () -> Unit = {},
    onRefresh: () -> Unit,
    onRequestRoot: () -> Unit,
    onOpenRun: (WorkflowRun) -> Unit,
    onCancelRun: (Long) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    BlurScreenScaffold(
        blurConfig = state.blurConfig,
        containerColor = appPageBackgroundColor(uiSurfaceColor(AbkTheme.colors.background)),
        topBar = {
            ExpressiveTopBar(
                title = stringResource(R.string.app_name),
                compactTitle = true,
                scrollBehavior = scrollBehavior,
                enableBlur = state.blurEnabled,
                actions = {
                    AbkIconButton(AbkIcons.Refresh, stringResource(R.string.refresh), onRefresh,
                        enabled = !state.isRefreshingRecentRuns && state.user != null && state.forkRepo != null)
                    AbkIconButton(
                        if (runtimeNavigationEnabled) AbkIcons.Home else AbkIcons.Cpu,
                        stringResource(if (runtimeNavigationEnabled) R.string.nav_status else R.string.nav_home),
                        onToggleRuntimeNavigation
                    )
                }
            )
        }
    ) { topBarHeight ->
        Column(
            Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()).padding(horizontal = AbkScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(topBarHeight + 8.dp))
            AbkHero(
                title = stringResource(if (state.rootGranted) R.string.status_working else R.string.status_partially_active),
                eyebrow = stringResource(R.string.status_version, BuildConfig.VERSION_NAME),
                description = when {
                    state.activeBuildRuns.size > 1 -> stringResource(R.string.status_parallel_build_number, state.activeBuildRuns.size)
                    state.currentRun != null -> stringResource(R.string.status_build_number, state.currentRun.runNumber)
                    !state.rootGranted -> stringResource(R.string.status_version_build_download, BuildConfig.VERSION_NAME)
                    else -> stringResource(R.string.status_no_running_build)
                }
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpressiveStatusChip(
                    stringResource(if (state.rootGranted) R.string.status_root_authorized else R.string.status_root_unauthorized),
                    icon = if (state.rootGranted) AbkIcons.Check else AbkIcons.Warning,
                    color = if (state.rootGranted) AbkTheme.colors.success.color else AbkTheme.colors.warning.color
                )
                ExpressiveStatusChip(state.forkRepo?.name ?: stringResource(R.string.status_no_fork_detected),
                    icon = AbkIcons.Build, color = AbkTheme.colors.inkMuted)
            }
            if (!state.rootGranted) {
                AbkButton(onRequestRoot, Modifier.fillMaxWidth(), style = AbkButtonStyle.Outline,
                    enabled = !state.isLoading, loading = state.isLoading,
                    loadingDescription = stringResource(R.string.loading)) {
                    AbkIcon(AbkIcons.Controls, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.grant_root))
                }
            }
            StatusMetricGrid(state.rootGranted, state.forkRepo != null && state.behindBy <= 0,
                ksuVersion, state.buildStatus)
            StatusWorkflowCard(
                title = stringResource(R.string.status_build),
                subtitle = stringResource(R.string.status_progress_sync),
                status = state.kernelBuildStatus,
                progress = state.kernelBuildProgress,
                run = state.kernelCurrentRun,
                activeRunCount = state.kernelActiveBuildRuns.size,
                cancellingIds = state.cancellingWorkflowRunIds,
                onOpenRun = onOpenRun,
                onCancelRun = onCancelRun
            )
            if (state.managerBuildStatus != BuildStatus.IDLE || state.managerCurrentRun != null) {
                StatusWorkflowCard(
                    title = stringResource(R.string.status_manager_build),
                    subtitle = stringResource(R.string.status_manager_progress_sync),
                    status = state.managerBuildStatus,
                    progress = state.managerBuildProgress,
                    run = state.managerCurrentRun,
                    activeRunCount = state.managerActiveBuildRuns.size,
                    cancellingIds = state.cancellingWorkflowRunIds,
                    onOpenRun = onOpenRun,
                    onCancelRun = onCancelRun
                )
            }
            ExpressiveSectionCard(
                title = stringResource(R.string.status_device_repo_title),
                subtitle = stringResource(R.string.status_device_repo_subtitle), icon = AbkIcons.Cpu
            ) {
                DeviceInfoRow(AbkIcons.Cpu, stringResource(R.string.status_kernel), kernelVersion, false)
                AbkDivider()
                DeviceInfoRow(AbkIcons.Controls, "KernelSU", ksuVersion, ksuVersion == "N/A")
                AbkDivider()
                state.user?.let { user ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(user.avatarUrl, null, Modifier.size(40.dp).clip(CircleShape))
                        Column(Modifier.weight(1f)) {
                            Text(user.login, style = MaterialTheme.typography.titleSmall)
                            Text(state.forkRepo?.fullName ?: stringResource(R.string.status_no_fork),
                                style = MaterialTheme.typography.bodySmall, color = AbkTheme.colors.inkMuted)
                        }
                    }
                } ?: DeviceInfoRow(AbkIcons.Info, stringResource(R.string.status_login),
                    stringResource(R.string.status_not_login), false)
                if (state.behindBy > 0) StatusRow(AbkIcons.Warning,
                    stringResource(R.string.status_fork_behind, state.behindBy), AbkTheme.colors.warning.color)
            }
            ExpressiveSectionCard(
                title = stringResource(R.string.status_recent_runs_title),
                subtitle = stringResource(R.string.status_recent_runs_subtitle), icon = AbkIcons.Pending,
                trailingContent = {
                    if (state.isRefreshingRecentRuns) AbkSpinner(size = AbkSize.Small, label = stringResource(R.string.loading))
                }
            ) {
                if (state.recentRuns.isEmpty()) {
                    AbkEmptyState(stringResource(R.string.status_no_build), stringResource(R.string.empty_state_build_artifacts_hint),
                        icon = AbkIcons.Build)
                } else state.recentRuns.take(5).forEachIndexed { index, run ->
                    if (index > 0) AbkDivider()
                    RunListItem(run, run.id in state.cancellingWorkflowRunIds,
                        onOpen = { onOpenRun(run) }, onCancel = { onCancelRun(run.id) })
                }
            }
            Spacer(Modifier.height(24.dp + outerPadding.calculateBottomPadding()))
        }
    }
}

@Composable
private fun StatusWorkflowCard(
    title: String,
    subtitle: String,
    status: BuildStatus,
    progress: BuildProgress,
    run: WorkflowRun?,
    activeRunCount: Int,
    cancellingIds: Set<Long>,
    onOpenRun: (WorkflowRun) -> Unit,
    onCancelRun: (Long) -> Unit
) {
    ExpressiveSectionCard(title, subtitle = subtitle, icon = AbkIcons.Build) {
        val message = when (status) {
            BuildStatus.IDLE -> stringResource(R.string.status_no_running_build)
            BuildStatus.QUEUED -> if (activeRunCount > 1) {
                stringResource(R.string.status_parallel_build_waiting_runner, activeRunCount)
            } else stringResource(R.string.status_build_waiting_runner)
            BuildStatus.IN_PROGRESS -> "${progress.percent}% · ${progress.currentStep}"
            BuildStatus.SUCCESS -> stringResource(R.string.status_recent_build_success)
            BuildStatus.FAILURE -> stringResource(R.string.status_recent_build_failed)
            BuildStatus.CANCELLED -> stringResource(R.string.status_build_cancelled)
        }
        StatusRow(buildStatusIcon(status), message, buildStatusColor(status))
        if (run != null && progress.totalSteps > 0) {
            AbkLinearProgress(progress.percent / 100f, label = title,
                tone = when (status) {
                    BuildStatus.SUCCESS -> AbkTone.Success
                    BuildStatus.FAILURE -> AbkTone.Danger
                    else -> AbkTone.Accent
                })
            Text(stringResource(R.string.status_steps_complete, progress.completedSteps, progress.totalSteps),
                style = MaterialTheme.typography.bodySmall, color = AbkTheme.colors.inkMuted)
        } else if (status == BuildStatus.QUEUED || status == BuildStatus.IN_PROGRESS) {
            AbkLinearProgress(null, label = title)
        }
        if (run != null && activeRunCount <= 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AbkButton({ onOpenRun(run) }, style = AbkButtonStyle.Outline) {
                    AbkIcon(AbkIcons.ArrowRight, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.status_view_details, run.runNumber))
                }
                if (run.isActiveStatusRun()) {
                    val cancelling = run.id in cancellingIds
                    AbkButton({ onCancelRun(run.id) }, style = AbkButtonStyle.Ghost,
                        enabled = !cancelling, loading = cancelling,
                        loadingDescription = stringResource(R.string.status_cancelling)) {
                        AbkIcon(AbkIcons.Close, null, tint = AbkTheme.colors.danger.color)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(if (cancelling) R.string.status_cancelling else R.string.status_cancel),
                            color = AbkTheme.colors.danger.color)
                    }
                }
            }
        }
        if (activeRunCount > 1) Text(stringResource(R.string.status_parallel_workflows_desc, activeRunCount),
            style = MaterialTheme.typography.bodySmall, color = AbkTheme.colors.inkMuted)
    }
}

@Composable
private fun DeviceInfoRow(icon: ImageVector, label: String, value: String, isError: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AbkIcon(if (isError) AbkIcons.Warning else icon, null,
            tint = if (isError) AbkTheme.colors.warning.color else AbkTheme.colors.inkMuted)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = AbkTheme.colors.inkMuted)
            Text(value, style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (label == stringResource(R.string.status_kernel)) AbkTheme.monoFont else null)
        }
    }
}

@Composable
private fun StatusMetricGrid(rootGranted: Boolean, forkReady: Boolean, ksuVersion: String, buildStatus: BuildStatus) {
    val labels = listOf(stringResource(R.string.status_root), stringResource(R.string.status_fork), "KernelSU", stringResource(R.string.status_build))
    val values = listOf(
        stringResource(if (rootGranted) R.string.status_authorized else R.string.status_partially_active),
        stringResource(if (forkReady) R.string.status_synced else R.string.status_pending_check),
        stringResource(if (ksuVersion == "N/A") R.string.status_not_detected else R.string.status_detected),
        buildStatusDisplay(buildStatus)
    )
    val icons = listOf(if (rootGranted) AbkIcons.Check else AbkIcons.Warning, AbkIcons.Refresh,
        if (ksuVersion == "N/A") AbkIcons.Info else AbkIcons.Controls, buildStatusIcon(buildStatus))
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints {
        if (maxWidth < 340.dp || fontScale >= 1.5f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.indices.forEach { StatusMetricCard(labels[it], values[it], icons[it], Modifier.fillMaxWidth()) }
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            labels.indices.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    pair.forEach { StatusMetricCard(labels[it], values[it], icons[it], Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun StatusMetricCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    AbkCard(modifier.blurredCardBackground(AbkShapes.Large), containerColor = blurredCardSurfaceColor(AbkTheme.colors.raised)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AbkIcon(icon, null, tint = AbkTheme.colors.inkMuted)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = AbkTheme.colors.inkMuted)
                Text(value, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun StatusRow(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AbkIcon(icon, null, tint = color)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RunListItem(run: WorkflowRun, cancelling: Boolean, onOpen: () -> Unit, onCancel: () -> Unit) {
    val status = when {
        run.status == "completed" && run.conclusion == "success" -> BuildStatus.SUCCESS
        run.status == "completed" && run.conclusion == "cancelled" -> BuildStatus.CANCELLED
        run.status == "completed" -> BuildStatus.FAILURE
        run.status == "in_progress" -> BuildStatus.IN_PROGRESS
        else -> BuildStatus.QUEUED
    }
    Column {
        AbkListItem(title = run.displayTitle ?: run.name ?: "#${run.runNumber}",
            description = "#${run.runNumber} · ${run.createdAt.take(10)}", onClick = onOpen,
            trailing = { AbkIcon(AbkIcons.Forward, null) })
        FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpressiveStatusChip(buildStatusDisplay(status), icon = buildStatusIcon(status), color = buildStatusColor(status))
            if (run.isActiveStatusRun()) AbkButton(onCancel, style = AbkButtonStyle.Ghost,
                loading = cancelling, enabled = !cancelling, loadingDescription = stringResource(R.string.status_cancelling)) {
                AbkIcon(AbkIcons.Close, null, tint = AbkTheme.colors.danger.color)
                Text(stringResource(if (cancelling) R.string.status_cancelling else R.string.status_cancel_workflow),
                    color = AbkTheme.colors.danger.color)
            }
        }
    }
}

private fun WorkflowRun.isActiveStatusRun(): Boolean =
    status in setOf("queued", "waiting", "requested", "pending", "in_progress")

@Composable
private fun buildStatusDisplay(status: BuildStatus): String = stringResource(when (status) {
    BuildStatus.IDLE -> R.string.status_idle
    BuildStatus.QUEUED -> R.string.status_queued
    BuildStatus.IN_PROGRESS -> R.string.status_in_progress
    BuildStatus.SUCCESS -> R.string.status_success
    BuildStatus.FAILURE -> R.string.status_failure
    BuildStatus.CANCELLED -> R.string.status_stopped
})

private fun buildStatusIcon(status: BuildStatus): ImageVector = when (status) {
    BuildStatus.IDLE, BuildStatus.QUEUED -> AbkIcons.Pending
    BuildStatus.IN_PROGRESS -> AbkIcons.Build
    BuildStatus.SUCCESS -> AbkIcons.Check
    BuildStatus.FAILURE -> AbkIcons.Warning
    BuildStatus.CANCELLED -> AbkIcons.Close
}

@Composable
private fun buildStatusColor(status: BuildStatus): Color = when (status) {
    BuildStatus.SUCCESS -> AbkTheme.colors.success.color
    BuildStatus.FAILURE -> AbkTheme.colors.danger.color
    BuildStatus.QUEUED -> AbkTheme.colors.warning.color
    BuildStatus.IN_PROGRESS -> AbkTheme.colors.accent.color
    else -> AbkTheme.colors.inkMuted
}
