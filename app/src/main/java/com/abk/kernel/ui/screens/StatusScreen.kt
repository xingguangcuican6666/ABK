@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.abk.kernel.BuildConfig
import com.abk.kernel.R
import com.abk.kernel.data.model.BuildStatus
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.ExpressiveHeroCard
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.components.ShimmerLinearProgress
import com.abk.kernel.ui.theme.appPageBackgroundColor
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    runtimeNavigationEnabled: Boolean = false,
    onToggleRuntimeNavigation: () -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) { vm.loadRecentRuns() }

    Scaffold(
        containerColor = appPageBackgroundColor(uiSurfaceColor(MaterialTheme.colorScheme.surface)),
        topBar = {
            ExpressiveTopBar(
                title = stringResource(R.string.app_name),
                compactTitle = true,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onToggleRuntimeNavigation) {
                        Icon(
                            imageVector = if (runtimeNavigationEnabled) Icons.Default.SwapHoriz else Icons.Default.Home,
                            contentDescription = if (runtimeNavigationEnabled) {
                                stringResource(R.string.nav_status)
                            } else {
                                stringResource(R.string.nav_home)
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AbkScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val ksuVersion = remember(state.rootGranted) {
                if (state.rootGranted) RootUtils.getKsuVersion() else "N/A"
            }
            val kernelVersion = remember(state.rootGranted) {
                RootUtils.getKernelVersion()
            }

            ExpressiveHeroCard(
                title = if (state.rootGranted) stringResource(R.string.status_working) else stringResource(R.string.status_partially_active),
                subtitle = if (state.rootGranted) {
                    when {
                        state.activeBuildRuns.size > 1 -> stringResource(R.string.status_parallel_build_number, state.activeBuildRuns.size)
                        state.currentRun != null -> state.currentRun?.let { stringResource(R.string.status_build_number, it.runNumber) }.orEmpty()
                        else -> stringResource(R.string.status_version, BuildConfig.VERSION_NAME)
                    }
                } else {
                    stringResource(R.string.status_version_build_download, BuildConfig.VERSION_NAME)
                },
                icon = if (state.rootGranted) Icons.Default.CheckCircleOutline else Icons.Default.Info,
                containerColor = if (state.rootGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (state.rootGranted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                badge = {
                    ExpressiveStatusChip(
                        label = if (state.rootGranted) stringResource(R.string.status_root_authorized) else stringResource(R.string.status_root_unauthorized),
                        icon = if (state.rootGranted) Icons.Default.Lock else Icons.Default.LockOpen,
                        color = if (state.rootGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    ExpressiveStatusChip(
                        label = state.forkRepo?.name ?: stringResource(R.string.status_no_fork_detected),
                        icon = Icons.Default.ForkRight,
                        color = if (state.forkRepo != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                    )
                }
            ) {
                if (!state.rootGranted) {
                    OutlinedButton(
                        onClick = { vm.requestRoot() },
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isLoading) {
                            LoadingIndicator(Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.Lock, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.grant_root))
                        }
                    }
                }
            }

            StatusMetricGrid(
                rootGranted = state.rootGranted,
                forkReady = state.forkRepo != null && state.behindBy <= 0,
                ksuVersion = ksuVersion,
                buildStatus = state.buildStatus
            )

            ExpressiveSectionCard(
                title = stringResource(R.string.status_build),
                subtitle = stringResource(R.string.status_progress_sync),
                icon = Icons.Default.RunCircle,
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                when (state.kernelBuildStatus) {
                    BuildStatus.IDLE -> StatusRow(Icons.Default.HourglassEmpty, stringResource(R.string.status_no_running_build), false)
                    BuildStatus.QUEUED -> StatusRow(
                        Icons.Default.Queue,
                        if (state.kernelActiveBuildRuns.size > 1) {
                            stringResource(R.string.status_parallel_build_waiting_runner, state.kernelActiveBuildRuns.size)
                        } else {
                            stringResource(R.string.status_build_waiting_runner)
                        },
                        false
                    )
                    BuildStatus.IN_PROGRESS -> Row(verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator(Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("${state.kernelBuildProgress.percent}% · ${state.kernelBuildProgress.currentStep}")
                    }
                    BuildStatus.SUCCESS -> StatusRow(Icons.Default.CheckCircle, stringResource(R.string.status_recent_build_success), false)
                    BuildStatus.FAILURE -> StatusRow(Icons.Default.Error, stringResource(R.string.status_recent_build_failed), true)
                    BuildStatus.CANCELLED -> StatusRow(Icons.Default.Cancel, stringResource(R.string.status_build_cancelled), true)
                }
                val kernelRun = state.kernelCurrentRun
                if (kernelRun != null && state.kernelBuildProgress.totalSteps > 0) {
                    Spacer(Modifier.height(8.dp))
                    val animatedProgress by animateFloatAsState(
                        targetValue = (state.kernelBuildProgress.percent / 100f).coerceIn(0f, 1f),
                        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        label = "status-progress"
                    )
                    ShimmerLinearProgress(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(
                            R.string.status_steps_complete,
                            state.kernelBuildProgress.completedSteps,
                            state.kernelBuildProgress.totalSteps
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val showSingleRunAction = state.kernelActiveBuildRuns.size <= 1
                state.kernelCurrentRun?.takeIf { showSingleRunAction }?.let { run ->
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(run.htmlUrl))
                                    )
                                }
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.status_view_details, run.runNumber), style = MaterialTheme.typography.labelMedium)
                        }
                        if (run.isActiveStatusRun()) {
                            TextButton(
                                onClick = { vm.cancelWorkflowRun(run.id) },
                                enabled = run.id !in state.cancellingWorkflowRunIds,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                if (run.id in state.cancellingWorkflowRunIds) {
                                    LoadingIndicator(Modifier.size(16.dp))
                                } else {
                                    Icon(Icons.Default.Cancel, null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (run.id in state.cancellingWorkflowRunIds) {
                                        stringResource(R.string.status_cancelling)
                                    } else {
                                        stringResource(R.string.status_cancel)
                                    }
                                )
                            }
                        }
                    }
                }
                if (state.kernelActiveBuildRuns.size > 1) {
                    Text(
                        stringResource(R.string.status_parallel_workflows_desc, state.kernelActiveBuildRuns.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Manager-app build mirror. Rendered only when a manager build is
            // actually happening / has happened — otherwise the screen would
            // grow a permanent "No manager build" tile that's just noise.
            if (state.managerBuildStatus != BuildStatus.IDLE || state.managerCurrentRun != null) {
                ExpressiveSectionCard(
                    title = stringResource(R.string.status_manager_build),
                    subtitle = stringResource(R.string.status_manager_progress_sync),
                    icon = Icons.Default.Shield,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    val managerProgress = state.managerBuildProgress
                    when (state.managerBuildStatus) {
                        BuildStatus.IDLE -> StatusRow(Icons.Default.HourglassEmpty, stringResource(R.string.status_no_running_build), false)
                        BuildStatus.QUEUED -> StatusRow(
                            Icons.Default.Queue,
                            if (state.managerActiveBuildRuns.size > 1) {
                                stringResource(R.string.status_parallel_build_waiting_runner, state.managerActiveBuildRuns.size)
                            } else {
                                stringResource(R.string.status_build_waiting_runner)
                            },
                            false
                        )
                        BuildStatus.IN_PROGRESS -> Row(verticalAlignment = Alignment.CenterVertically) {
                            LoadingIndicator(Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("${managerProgress.percent}% · ${managerProgress.currentStep}")
                        }
                        BuildStatus.SUCCESS -> StatusRow(Icons.Default.CheckCircle, stringResource(R.string.status_recent_build_success), false)
                        BuildStatus.FAILURE -> StatusRow(Icons.Default.Error, stringResource(R.string.status_recent_build_failed), true)
                        BuildStatus.CANCELLED -> StatusRow(Icons.Default.Cancel, stringResource(R.string.status_build_cancelled), true)
                    }
                    val managerRun = state.managerCurrentRun
                    if (managerRun != null && managerProgress.totalSteps > 0) {
                        Spacer(Modifier.height(8.dp))
                        val animatedProgress by animateFloatAsState(
                            targetValue = (managerProgress.percent / 100f).coerceIn(0f, 1f),
                            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                            label = "status-manager-progress"
                        )
                        ShimmerLinearProgress(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            stringResource(R.string.status_steps_complete, managerProgress.completedSteps, managerProgress.totalSteps),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val showSingleManagerAction = state.managerActiveBuildRuns.size <= 1
                    state.managerCurrentRun?.takeIf { showSingleManagerAction }?.let { run ->
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(run.htmlUrl))
                                        )
                                    }
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.status_view_details, run.runNumber), style = MaterialTheme.typography.labelMedium)
                            }
                            if (run.isActiveStatusRun()) {
                                TextButton(
                                    onClick = { vm.cancelWorkflowRun(run.id) },
                                    enabled = run.id !in state.cancellingWorkflowRunIds,
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    if (run.id in state.cancellingWorkflowRunIds) {
                                        LoadingIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    } else {
                                        Icon(Icons.Default.Cancel, null, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        if (run.id in state.cancellingWorkflowRunIds) {
                                            stringResource(R.string.status_cancelling)
                                        } else {
                                            stringResource(R.string.status_cancel)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (state.managerActiveBuildRuns.size > 1) {
                        Text(
                            stringResource(R.string.status_parallel_workflows_desc, state.managerActiveBuildRuns.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            ExpressiveSectionCard(
                title = stringResource(R.string.status_device_repo_title),
                subtitle = stringResource(R.string.status_device_repo_subtitle),
                icon = Icons.Default.Memory
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeviceInfoRow(
                        icon = Icons.Default.Memory,
                        label = stringResource(R.string.status_kernel),
                        value = kernelVersion,
                        isError = false
                    )
                    DeviceInfoRow(
                        icon = Icons.Default.Shield,
                        label = "KSU",
                        value = ksuVersion,
                        isError = ksuVersion == "N/A"
                    )
                }
                state.user?.let { user ->
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                    )
                    AccountRepositoryRow(
                        avatarUrl = user.avatarUrl,
                        login = user.login,
                        repository = state.forkRepo?.fullName ?: stringResource(R.string.status_no_fork)
                    )
                }
                if (state.behindBy > 0) {
                    StatusRow(Icons.Default.Warning, stringResource(R.string.status_fork_behind, state.behindBy), true)
                }
            }

            if (state.recentRuns.isNotEmpty()) {
                ExpressiveSectionCard(
                    title = stringResource(R.string.status_recent_runs_title),
                    subtitle = stringResource(R.string.status_recent_runs_subtitle),
                    icon = Icons.Default.History
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val visibleRuns = state.recentRuns.take(5)
                        visibleRuns.forEach { run ->
                            RunListItem(
                                run = run,
                                cancelling = run.id in state.cancellingWorkflowRunIds,
                                onCancel = { vm.cancelWorkflowRun(run.id) }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(80.dp + outerPadding.calculateBottomPadding()))
        }
    }
}

@Composable
private fun DeviceInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    isError: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AccountRepositoryRow(
    avatarUrl: String,
    login: String,
    repository: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = login,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = repository,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatusMetricGrid(
    rootGranted: Boolean,
    forkReady: Boolean,
    ksuVersion: String,
    buildStatus: BuildStatus
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatusMetricCard(
                label = "Root",
                value = if (rootGranted) stringResource(R.string.status_authorized) else stringResource(R.string.status_partially_active),
                icon = if (rootGranted) Icons.Default.Lock else Icons.Default.LockOpen,
                color = if (rootGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
            StatusMetricCard(
                label = "Fork",
                value = if (forkReady) stringResource(R.string.status_synced) else stringResource(R.string.status_pending_check),
                icon = Icons.Default.ForkRight,
                color = if (forkReady) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatusMetricCard(
                label = "KernelSU",
                value = if (ksuVersion == "N/A") stringResource(R.string.status_not_detected) else stringResource(R.string.status_detected),
                icon = Icons.Default.Shield,
                color = if (ksuVersion == "N/A") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            StatusMetricCard(
                label = "Build",
                value = buildStatusDisplay(buildStatus),
                icon = Icons.Default.RunCircle,
                color = buildStatusColor(buildStatus),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatusMetricCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val animatedColor by animateColorAsState(
        color,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "metric-color"
    )
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainer)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = animatedColor, modifier = Modifier.size(22.dp))
            Column {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatusRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, isError: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            icon, null,
            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RunListItem(
    run: WorkflowRun,
    cancelling: Boolean,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                run.displayTitle ?: run.name ?: "#${run.runNumber}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                run.createdAt.take(10),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val (color, label) = when {
            run.status == "completed" && run.conclusion == "success" ->
                MaterialTheme.colorScheme.primary to stringResource(R.string.status_success)
            run.status == "completed" && run.conclusion == "cancelled" ->
                MaterialTheme.colorScheme.outline to stringResource(R.string.status_cancelled_label)
            run.status == "completed" ->
                MaterialTheme.colorScheme.error to stringResource(R.string.status_failure)
            run.status == "in_progress" ->
                MaterialTheme.colorScheme.tertiary to stringResource(R.string.status_in_progress)
            else -> MaterialTheme.colorScheme.outline to run.status
        }
        Badge(containerColor = color.copy(alpha = 0.15f)) {
            Text(label, color = color, style = MaterialTheme.typography.labelSmall)
        }
        if (run.isActiveStatusRun()) {
            IconButton(onClick = onCancel, enabled = !cancelling) {
                if (cancelling) {
                    LoadingIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Icon(
                        Icons.Default.Cancel,
                        contentDescription = stringResource(R.string.status_cancel_workflow),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun WorkflowRun.isActiveStatusRun(): Boolean =
    status in setOf("queued", "waiting", "requested", "pending", "in_progress")

@Composable
private fun buildStatusDisplay(status: BuildStatus): String = when (status) {
    BuildStatus.IDLE -> stringResource(R.string.status_idle)
    BuildStatus.QUEUED -> stringResource(R.string.status_queued)
    BuildStatus.IN_PROGRESS -> stringResource(R.string.status_in_progress)
    BuildStatus.SUCCESS -> stringResource(R.string.status_success)
    BuildStatus.FAILURE -> stringResource(R.string.status_failure)
    BuildStatus.CANCELLED -> stringResource(R.string.status_stopped)
}

@Composable
private fun buildStatusColor(status: BuildStatus) = when (status) {
    BuildStatus.IDLE -> MaterialTheme.colorScheme.outline
    BuildStatus.QUEUED -> MaterialTheme.colorScheme.tertiary
    BuildStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondary
    BuildStatus.SUCCESS -> MaterialTheme.colorScheme.primary
    BuildStatus.FAILURE -> MaterialTheme.colorScheme.error
    BuildStatus.CANCELLED -> MaterialTheme.colorScheme.outline
}
