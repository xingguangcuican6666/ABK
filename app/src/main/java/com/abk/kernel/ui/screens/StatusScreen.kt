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
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusScreen(
    vm: MainViewModel,
    runtimeNavigationEnabled: Boolean = false,
    onToggleRuntimeNavigation: () -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) { vm.loadRecentRuns() }

    Scaffold(
        containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
        topBar = {
            ExpressiveTopBar(
                title = stringResource(R.string.app_name),
                compactTitle = true,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onToggleRuntimeNavigation) {
                        Icon(
                            imageVector = if (runtimeNavigationEnabled) Icons.Default.SwapHoriz else Icons.Default.Home,
                            contentDescription = if (runtimeNavigationEnabled) "Switch to full navigation" else "Switch to runtime home"
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
                title = if (state.rootGranted) "Active" else "Partial activation",
                subtitle = if (state.rootGranted) {
                    state.currentRun?.let { "Build: #${it.runNumber}" } ?: "Version: ${BuildConfig.VERSION_NAME}"
                } else {
                    "Version: ${BuildConfig.VERSION_NAME} · Build and download available"
                },
                icon = if (state.rootGranted) Icons.Default.CheckCircleOutline else Icons.Default.Info,
                containerColor = if (state.rootGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (state.rootGranted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                badge = {
                    ExpressiveStatusChip(
                        label = if (state.rootGranted) "Root authorized" else "Root not authorized",
                        icon = if (state.rootGranted) Icons.Default.Lock else Icons.Default.LockOpen,
                        color = if (state.rootGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    ExpressiveStatusChip(
                        label = state.forkRepo?.name ?: "No fork detected",
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
                subtitle = "Synced with notification bar progress",
                icon = Icons.Default.RunCircle,
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                when (state.buildStatus) {
                    BuildStatus.IDLE -> StatusRow(Icons.Default.HourglassEmpty, "No active build", false)
                    BuildStatus.QUEUED -> StatusRow(Icons.Default.Queue, "Build queued, waiting for runner…", false)
                    BuildStatus.IN_PROGRESS -> Row(verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator(Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("${state.buildProgress.percent}% · ${state.buildProgress.currentStep}")
                    }
                    BuildStatus.SUCCESS -> StatusRow(Icons.Default.CheckCircle, "Latest build succeeded ✓", false)
                    BuildStatus.FAILURE -> StatusRow(Icons.Default.Error, "Latest build failed", true)
                    BuildStatus.CANCELLED -> StatusRow(Icons.Default.Cancel, "Build cancelled", true)
                }
                if (state.buildProgress.totalSteps > 0) {
                    Spacer(Modifier.height(8.dp))
                    val animatedProgress by animateFloatAsState(
                        targetValue = (state.buildProgress.percent / 100f).coerceIn(0f, 1f),
                        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        label = "status-progress"
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "${state.buildProgress.completedSteps}/${state.buildProgress.totalSteps} steps completed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                state.currentRun?.let { run ->
                    Spacer(Modifier.height(4.dp))
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
                        Text("View details #${run.runNumber}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            ExpressiveSectionCard(
                title = "Device & Repository",
                subtitle = "Used to generate default build parameters and verify workflow source.",
                icon = Icons.Default.Memory
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeviceInfoRow(
                        icon = Icons.Default.Memory,
                        label = "Kernel",
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
                    StatusRow(Icons.Default.Warning, "Fork is ${state.behindBy} commits behind upstream", true)
                }
            }

            if (state.recentRuns.isNotEmpty()) {
                ExpressiveSectionCard(
                    title = "Recent builds",
                    subtitle = "Quick overview of the last 5 workflow results.",
                    icon = Icons.Default.History
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val visibleRuns = state.recentRuns.take(5)
                        visibleRuns.forEachIndexed { index, run ->
                            RunListItem(run)
                        }
                    }
                }
            }
            Spacer(Modifier.height(80.dp))
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
                value = if (rootGranted) "Authorized" else "Partial activation",
                icon = if (rootGranted) Icons.Default.Lock else Icons.Default.LockOpen,
                color = if (rootGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
            StatusMetricCard(
                label = "Fork",
                value = if (forkReady) "Synced" else "Check needed",
                icon = Icons.Default.ForkRight,
                color = if (forkReady) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatusMetricCard(
                label = "KernelSU",
                value = if (ksuVersion == "N/A") "Not detected" else "Detected",
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
private fun RunListItem(run: WorkflowRun) {
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
                MaterialTheme.colorScheme.primary to "Success"
            run.status == "completed" ->
                MaterialTheme.colorScheme.error to "Failed"
            run.status == "in_progress" ->
                MaterialTheme.colorScheme.tertiary to "In progress"
            else -> MaterialTheme.colorScheme.outline to run.status
        }
        Badge(containerColor = color.copy(alpha = 0.15f)) {
            Text(label, color = color, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun buildStatusDisplay(status: BuildStatus): String = when (status) {
    BuildStatus.IDLE -> "Idle"
    BuildStatus.QUEUED -> "Queued"
    BuildStatus.IN_PROGRESS -> "In progress"
    BuildStatus.SUCCESS -> "Success"
    BuildStatus.FAILURE -> "Failed"
    BuildStatus.CANCELLED -> "Stopped"
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
