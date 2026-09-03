package com.abk.kernel.miuix.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.RunCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.abk.kernel.BuildConfig
import com.abk.kernel.R
import com.abk.kernel.data.model.BuildStatus
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import com.abk.kernel.miuix.util.BlurredBar
import com.abk.kernel.miuix.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop

@Composable
fun StatusScreenMiuix(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    runtimeNavigationEnabled: Boolean = false,
    onToggleRuntimeNavigation: () -> Unit = {},
) {
    val state by vm.uiState.collectAsState()
    val scrollBehavior = MiuixScrollBehavior()
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberBlurBackdrop(state.miuixBlurEnabled, surfaceColor)
    val barColor = if (backdrop != null) Color.Transparent else surfaceColor

    androidx.compose.runtime.LaunchedEffect(Unit) { vm.loadRecentRuns() }

    Scaffold(
        topBar = {
            BlurredBar(backdrop, surfaceColor) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.app_name),
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = onToggleRuntimeNavigation) {
                            Icon(
                                imageVector = if (runtimeNavigationEnabled) {
                                    Icons.Default.Home
                                } else {
                                    Icons.Default.SwapHoriz
                                },
                                contentDescription = stringResource(
                                    if (runtimeNavigationEnabled) R.string.nav_home else R.string.nav_status
                                )
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        val listState = rememberScrollState()
        Box(
            modifier = Modifier.then(
                if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(listState)
                    .padding(horizontal = 12.dp)
                    .overScrollVertical()
                    .scrollEndHaptic(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(padding.calculateTopPadding() + 16.dp))

            StatusHeroCardMiuix(
                rootGranted = state.rootGranted,
                currentVersion = BuildConfig.VERSION_NAME,
                forkRepoName = state.forkRepo?.name,
                isLoading = state.isLoading,
                onRequestRoot = { vm.requestRoot() },
                themeMode = state.themeMode
            )

            val ksuVersion = androidx.compose.runtime.remember(state.rootGranted) {
                if (state.rootGranted) RootUtils.getKsuVersion() else "N/A"
            }
            StatusMetricGridMiuix(
                ksuVersion = ksuVersion,
                buildStatus = state.buildStatus
            )

            BuildStatusCardMiuix(
                title = stringResource(R.string.status_build),
                subtitle = stringResource(R.string.status_progress_sync),
                icon = Icons.Default.RunCircle,
                status = state.kernelBuildStatus,
                progress = state.kernelBuildProgress,
                currentRun = state.kernelCurrentRun,
                activeRunsCount = state.kernelActiveBuildRuns.size,
                cancellingRunIds = state.cancellingWorkflowRunIds,
                onCancel = { run -> vm.cancelWorkflowRun(run.id) }
            )

            if (state.managerBuildStatus != BuildStatus.IDLE || state.managerCurrentRun != null) {
                BuildStatusCardMiuix(
                    title = stringResource(R.string.status_manager_build),
                    subtitle = stringResource(R.string.status_manager_progress_sync),
                    icon = Icons.Default.Shield,
                    status = state.managerBuildStatus,
                    progress = state.managerBuildProgress,
                    currentRun = state.managerCurrentRun,
                    activeRunsCount = state.managerActiveBuildRuns.size,
                    cancellingRunIds = state.cancellingWorkflowRunIds,
                    onCancel = { run -> vm.cancelWorkflowRun(run.id) }
                )
            }

            val ksuVersionForRepo = androidx.compose.runtime.remember(state.rootGranted) {
                if (state.rootGranted) RootUtils.getKsuVersion() else "N/A"
            }
            val kernelVersion = androidx.compose.runtime.remember(state.rootGranted) {
                RootUtils.getKernelVersion()
            }
            DeviceRepoCardMiuix(
                kernelVersion = kernelVersion,
                ksuVersion = ksuVersionForRepo,
                user = state.user,
                forkRepo = state.forkRepo,
                behindBy = state.behindBy
            )

            AnimatedVisibility(
                visible = state.recentRuns.isNotEmpty(),
                enter = fadeIn() + expandIn(expandFrom = Alignment.TopStart),
                exit = fadeOut() + shrinkOut(shrinkTowards = Alignment.TopStart)
            ) {
                RecentRunsCardMiuix(recentRuns = state.recentRuns.take(5))
            }

            Spacer(Modifier.height(80.dp + outerPadding.calculateBottomPadding()))
        }
        }
    }
}

@Composable
private fun StatusHeroCardMiuix(
    rootGranted: Boolean,
    currentVersion: String,
    forkRepoName: String?,
    isLoading: Boolean,
    onRequestRoot: () -> Unit,
    themeMode: String,
) {
    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val containerColor = if (rootGranted) {
        if (isDark) Color(0xFF193822) else Color(0xFFDDF5E6)
    } else {
        if (isDark) Color(0xFF381A18) else Color(0xFFF9EEEC)
    }
    val contentColor = if (isDark) Color.White else Color(0xFF1A1A1A)
    val descColor = if (isDark) Color.White.copy(alpha = 0.8f) else Color(0xFF1A1A1A).copy(alpha = 0.8f)
    val bgIconTint = if (rootGranted) {
        Color(0xFF35D267)
    } else {
        Color(0xFFD03636)
    }

    Card(
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(color = containerColor),
        modifier = Modifier.fillMaxWidth(),
        onClick = onRequestRoot,
        showIndication = true,
        pressFeedbackType = top.yukonga.miuix.kmp.utils.PressFeedbackType.Tilt
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(50.dp, 38.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Icon(
                    modifier = Modifier.size(170.dp),
                    imageVector = if (rootGranted) {
                        Icons.Rounded.CheckCircleOutline
                    } else {
                        Icons.Rounded.ErrorOutline
                    },
                    tint = bgIconTint,
                    contentDescription = null
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 16.dp)
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = if (rootGranted) {
                        stringResource(R.string.status_hero_activated_title)
                    } else {
                        stringResource(R.string.status_hero_deactivated_title)
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                Spacer(Modifier.height(2.dp))

                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.status_version, currentVersion),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = descColor
                )
                Spacer(Modifier.height(2.dp))

                val repoStatusText = if (forkRepoName != null) {
                    stringResource(R.string.status_hero_activated_subtitle_repo_synced)
                } else {
                    stringResource(R.string.status_no_fork_detected)
                }
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = repoStatusText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = descColor
                )

                if (rootGranted) {
                    Spacer(Modifier.height(35.dp))
                } else {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                progress = null,
                                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                    foregroundColor = contentColor,
                                    backgroundColor = contentColor.copy(alpha = 0.2f)
                                ),
                                strokeWidth = 2.dp,
                                size = 14.dp
                            )
                        }
                        Text(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.status_hero_deactivated_hint),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = descColor
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusChipMiuix(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StatusMetricGridMiuix(
    ksuVersion: String,
    buildStatus: BuildStatus,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        StatusMetricCardMiuix(
            label = "KernelSU",
            value = if (ksuVersion == "N/A") stringResource(R.string.status_not_detected) else stringResource(R.string.status_detected),
            icon = Icons.Default.Shield,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        StatusMetricCardMiuix(
            label = "Build",
            value = buildStatusDisplayMiuix(buildStatus),
            icon = Icons.Default.RunCircle,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatusMetricCardMiuix(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = value,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
        }
    }
}

@Composable
private fun buildStatusDisplayMiuix(status: BuildStatus): String = when (status) {
    BuildStatus.IDLE -> stringResource(R.string.status_idle)
    BuildStatus.QUEUED -> stringResource(R.string.status_queued)
    BuildStatus.IN_PROGRESS -> stringResource(R.string.status_in_progress)
    BuildStatus.SUCCESS -> stringResource(R.string.status_success)
    BuildStatus.FAILURE -> stringResource(R.string.status_failure)
    BuildStatus.CANCELLED -> stringResource(R.string.status_stopped)
}

@Composable
private fun buildStatusColorMiuix(status: BuildStatus): androidx.compose.ui.graphics.Color = when (status) {
    BuildStatus.SUCCESS -> MiuixTheme.colorScheme.primary
    BuildStatus.FAILURE -> MiuixTheme.colorScheme.error
    BuildStatus.IN_PROGRESS -> MiuixTheme.colorScheme.primary
    BuildStatus.CANCELLED -> MiuixTheme.colorScheme.onSurfaceSecondary
    else -> MiuixTheme.colorScheme.onSurfaceSecondary
}

@Composable
private fun BuildStatusCardMiuix(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    status: BuildStatus,
    progress: com.abk.kernel.data.model.BuildProgress,
    currentRun: WorkflowRun?,
    activeRunsCount: Int,
    cancellingRunIds: Set<Long>,
    onCancel: (WorkflowRun) -> Unit,
) {
    val context = LocalContext.current
    val statusColor = buildStatusColorMiuix(status)

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }

            AnimatedVisibility(
                visible = status == BuildStatus.IDLE,
                enter = fadeIn() + expandIn(expandFrom = Alignment.TopStart),
                exit = fadeOut() + shrinkOut(shrinkTowards = Alignment.TopStart)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Icon(Icons.Default.HourglassEmpty, null, tint = MiuixTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.size(28.dp))
                    Text(
                        text = stringResource(R.string.status_no_running_build),
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            AnimatedVisibility(
                visible = status != BuildStatus.IDLE,
                enter = fadeIn() + expandIn(expandFrom = Alignment.TopStart),
                exit = fadeOut() + shrinkOut(shrinkTowards = Alignment.TopStart)
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (status) {
                            BuildStatus.IDLE -> {}
                            BuildStatus.QUEUED -> {
                                Icon(Icons.Default.Queue, null, tint = statusColor, modifier = Modifier.size(28.dp))
                                Text(
                                    text = if (activeRunsCount > 1) stringResource(R.string.status_parallel_build_waiting_runner, activeRunsCount)
                                    else stringResource(R.string.status_build_waiting_runner),
                                    style = MiuixTheme.textStyles.subtitle,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            BuildStatus.IN_PROGRESS -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    progress = null,
                                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                        foregroundColor = statusColor,
                                        backgroundColor = statusColor.copy(alpha = 0.2f)
                                    ),
                                    strokeWidth = 2.dp,
                                    size = 28.dp
                                )
                                Text(
                                    text = "${progress.percent}% · ${progress.currentStep}",
                                    style = MiuixTheme.textStyles.subtitle,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            BuildStatus.SUCCESS -> {
                                Icon(Icons.Default.CheckCircle, null, tint = statusColor, modifier = Modifier.size(28.dp))
                                Text(
                                    text = stringResource(R.string.status_recent_build_success),
                                    style = MiuixTheme.textStyles.subtitle,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            BuildStatus.FAILURE -> {
                                Icon(Icons.Default.Error, null, tint = statusColor, modifier = Modifier.size(28.dp))
                                Text(
                                    text = stringResource(R.string.status_recent_build_failed),
                                    style = MiuixTheme.textStyles.subtitle,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            BuildStatus.CANCELLED -> {
                                Icon(Icons.Filled.Cancel, null, tint = statusColor, modifier = Modifier.size(28.dp))
                                Text(
                                    text = stringResource(R.string.status_build_cancelled),
                                    style = MiuixTheme.textStyles.subtitle,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    val run = currentRun
                    if (run != null && progress.totalSteps > 0) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            progress = (progress.percent / 100f).coerceIn(0f, 1f),
                            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                foregroundColor = statusColor,
                                backgroundColor = MiuixTheme.colorScheme.surface,
                            ),
                        )
                        Text(
                            text = stringResource(R.string.status_steps_complete, progress.completedSteps, progress.totalSteps),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }

                    val showSingleRunAction = activeRunsCount <= 1
                    if (run != null && showSingleRunAction) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(run.htmlUrl)))
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(),
                                insideMargin = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                minWidth = 0.dp,
                                minHeight = 0.dp
                            ) {
                                Text(
                                    text = stringResource(R.string.status_view_details, run.runNumber),
                                    fontSize = 12.sp
                                )
                            }
                            if (run.status in setOf("queued", "waiting", "requested", "pending", "in_progress")) {
                                val isCancelling = run.id in cancellingRunIds
                                Button(
                                    onClick = { onCancel(run) },
                                    enabled = !isCancelling,
                                    colors = ButtonDefaults.buttonColors(
                                        color = MiuixTheme.colorScheme.error.copy(alpha = 0.12f),
                                        contentColor = MiuixTheme.colorScheme.error,
                                        disabledColor = MiuixTheme.colorScheme.error.copy(alpha = 0.06f),
                                        disabledContentColor = MiuixTheme.colorScheme.error.copy(alpha = 0.38f)
                                    ),
                                    insideMargin = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    minWidth = 0.dp,
                                    minHeight = 0.dp
                                ) {
                                    if (isCancelling) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            progress = null,
                                            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                                foregroundColor = MiuixTheme.colorScheme.error,
                                                backgroundColor = MiuixTheme.colorScheme.error.copy(alpha = 0.2f)
                                            ),
                                            strokeWidth = 2.dp,
                                            size = 12.dp
                                        )
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = if (isCancelling) stringResource(R.string.status_cancelling) else stringResource(R.string.status_cancel),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    if (activeRunsCount > 1) {
                        Text(
                            text = stringResource(R.string.status_parallel_workflows_desc, activeRunsCount),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRepoCardMiuix(
    kernelVersion: String,
    ksuVersion: String,
    user: com.abk.kernel.data.model.GitHubUser?,
    forkRepo: com.abk.kernel.data.model.GitHubRepo?,
    behindBy: Int,
) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.status_device_repo_title),
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.status_device_repo_subtitle),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DeviceInfoRowMiuix(
                    icon = Icons.Default.Memory,
                    label = stringResource(R.string.status_kernel),
                    value = kernelVersion
                )
                DeviceInfoRowMiuix(
                    icon = Icons.Default.Shield,
                    label = "KSU",
                    value = ksuVersion
                )
            }

            if (user != null) {
                Spacer(Modifier.height(8.dp))
                AccountRepositoryRowMiuix(
                    avatarUrl = user.avatarUrl,
                    login = user.login,
                    repository = forkRepo?.name ?: stringResource(R.string.status_no_fork)
                )
            }
            if (behindBy > 0) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Warning, null, tint = MiuixTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(R.string.status_fork_behind, behindBy),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceInfoRowMiuix(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.size(28.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = value,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AccountRepositoryRowMiuix(
    avatarUrl: String,
    login: String,
    repository: String,
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
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = repository,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecentRunsCardMiuix(
    recentRuns: List<WorkflowRun>,
) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.status_recent_runs_title),
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.status_recent_runs_subtitle),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recentRuns.forEach { run ->
                    RunListItemMiuix(run = run)
                }
            }
        }
    }
}

@Composable
private fun RunListItemMiuix(
    run: WorkflowRun,
) {
    val (statusColor, statusDisplay) = when {
        run.status == "completed" && run.conclusion == "success" ->
            MiuixTheme.colorScheme.primary to stringResource(R.string.status_success)
        run.status == "completed" && run.conclusion == "cancelled" ->
            MiuixTheme.colorScheme.onSurfaceSecondary to stringResource(R.string.status_cancelled_label)
        run.status == "completed" ->
            MiuixTheme.colorScheme.error to stringResource(R.string.status_failure)
        run.status == "in_progress" ->
            MiuixTheme.colorScheme.primary to stringResource(R.string.status_in_progress)
        run.status in setOf("queued", "waiting", "requested", "pending") ->
            MiuixTheme.colorScheme.onSurfaceSecondary to stringResource(R.string.status_queued)
        else ->
            MiuixTheme.colorScheme.onSurfaceSecondary to (run.status)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = run.displayTitle ?: run.name ?: "#${run.runNumber}",
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = run.createdAt.take(10),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = statusDisplay,
            style = MiuixTheme.textStyles.body2,
            color = statusColor,
            fontWeight = FontWeight.Medium
        )
    }
}
