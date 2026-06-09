package com.abk.kernel.ui.screens.miuix

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.abk.kernel.BuildConfig
import com.abk.kernel.R
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.utils.findActivity
import com.abk.kernel.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.isDynamicColor
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import androidx.core.net.toUri

@Composable
fun MiuixStatusScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues
) {
    val state by vm.uiState.collectAsState()
    val scrollBehavior = LocalMiuixScrollBehavior.current

    LaunchedEffect(Unit) {
        vm.loadRecentRuns()
    }

    val ksuVersion = remember(state.rootGranted) { RootUtils.getKsuVersion() }
    val ksuWorking = ksuVersion.isNotBlank() && ksuVersion != "N/A"
    val forkReady = state.forkRepo != null && state.behindBy <= 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                else Modifier
            )
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(
            top = outerPadding.calculateTopPadding(),
            bottom = outerPadding.calculateBottomPadding() + 80.dp
        ),
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── 状态大卡片 ──
                if (state.rootGranted) {
                    WorkingHeroCard()
                } else {
                    NotWorkingHeroCard()
                }

                // ── 4-card metric grid ──
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            label = "Root",
                            value = if (state.rootGranted) stringResource(R.string.status_authorized)
                                   else stringResource(R.string.status_partially_active),
                            color = if (state.rootGranted) colorScheme.primary else colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            label = "Fork",
                            value = if (forkReady) stringResource(R.string.status_synced)
                                   else stringResource(R.string.status_pending_check),
                            color = if (forkReady) colorScheme.primary else colorScheme.onSurfaceVariantActions,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            label = "KernelSU",
                            value = if (ksuWorking) stringResource(R.string.status_working) else "N/A",
                            color = if (ksuWorking) colorScheme.primary else colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            label = "Build",
                            value = when {
                                state.activeBuildRuns.isNotEmpty() ->
                                    stringResource(R.string.status_parallel_build_number, state.activeBuildRuns.size)
                                state.kernelCurrentRun != null -> state.kernelBuildStatus.name
                                else -> stringResource(R.string.status_idle)
                            },
                            color = when {
                                state.activeBuildRuns.isNotEmpty() -> colorScheme.primary
                                state.kernelCurrentRun != null -> colorScheme.primary
                                else -> colorScheme.onSurfaceVariantActions
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // ── 最近内核构建（始终显示）──
                KernelBuildSection(vm = vm, state = state)

                // ── 管理器构建 ──
                if (state.managerCurrentRun != null) {
                    ManagerBuildSection(state = state)
                }

                // ── 设备与仓库信息 ──
                DeviceInfoSection(
                    kernelVersion = RootUtils.getKernelVersion(),
                    ksuVersion = ksuVersion,
                    ksuWorking = ksuWorking,
                    behindBy = state.behindBy,
                    user = state.user,
                    forkRepo = state.forkRepo,
                )

                // ── 最近运行记录 ──
                if (state.recentRuns.isNotEmpty()) {
                    RecentRunsSection(runs = state.recentRuns.take(5)) { run ->
                        vm.cancelWorkflowRun(run.id)
                    }
                }
            }
        }
    }
}

// ── Working hero card ──

@Composable
private fun WorkingHeroCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = when {
                isDynamicColor -> colorScheme.secondaryContainer
                isDarkTheme() -> Color(0xFF1A3825)
                else -> Color(0xFFDFFAE4)
            }
        ),
        pressFeedbackType = PressFeedbackType.Tilt
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(26.dp, 30.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Icon(
                    modifier = Modifier.size(120.dp),
                    imageVector = Icons.Rounded.CheckCircleOutline,
                    tint = if (isDynamicColor) colorScheme.primary.copy(alpha = 0.8f)
                    else Color(0xFF36D167),
                    contentDescription = null
                )
            }
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(R.string.status_working),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.status_version, BuildConfig.VERSION_NAME),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun NotWorkingHeroCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = stringResource(R.string.status_partially_active),
            summary = stringResource(R.string.status_version_build_download, BuildConfig.VERSION_NAME),
            startAction = {
                Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 12.dp),
                    tint = colorScheme.onBackground,
                )
            },
        )
    }
}

@Composable
private fun isDarkTheme(): Boolean {
    val bg = colorScheme.background
    return (bg.red + bg.green + bg.blue) / 3f < 0.5f
}

// ── Metric card (full-width, with icon) ──

@Composable
private fun MetricCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1,
            )
        }
    }
}

// ── Kernel build section ──

@Composable
private fun KernelBuildSection(
    vm: MainViewModel,
    state: com.abk.kernel.viewmodel.MainUiState,
) {
    val ctx = LocalContext.current
    val run = state.kernelCurrentRun

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.status_build),
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))

            if (run == null) {
                Text(
                    text = stringResource(R.string.status_no_running_build),
                    fontSize = 14.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            } else {
                // Progress or status
                if (state.kernelBuildStatus.name == "IN_PROGRESS") {
                    val p = state.kernelBuildProgress
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(size = 20.dp, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("${p.percent}% · ${p.currentStep}", fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                    }
                    if (p.totalSteps > 0) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(progress = p.percent / 100f)
                        Text(
                            stringResource(R.string.status_steps_complete, p.completedSteps, p.totalSteps),
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                    }
                } else {
                    Text(
                        text = when (state.kernelBuildStatus.name) {
                            "SUCCESS" -> stringResource(R.string.status_recent_build_success)
                            "FAILURE" -> stringResource(R.string.status_recent_build_failed)
                            "CANCELLED" -> stringResource(R.string.status_build_cancelled)
                            "QUEUED" -> stringResource(R.string.status_build_waiting_runner)
                            else -> state.kernelBuildStatus.name
                        },
                        fontSize = 14.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            run.htmlUrl.takeIf { it.isNotBlank() }?.let { url ->
                                ctx.findActivity()?.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.status_view_details, run.runNumber)) }
                    if (run.status in setOf("queued", "waiting", "in_progress")) {
                        TextButton(text = stringResource(R.string.status_cancel), onClick = { vm.cancelWorkflowRun(run.id) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ── Manager build section ──

@Composable
private fun ManagerBuildSection(
    state: com.abk.kernel.viewmodel.MainUiState,
) {
    val ctx = LocalContext.current
    val run = state.managerCurrentRun ?: return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.status_manager_build),
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))

            if (state.managerBuildStatus.name == "IN_PROGRESS") {
                val p = state.managerBuildProgress
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(size = 20.dp, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("${p.percent}% · ${p.currentStep}", fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                }
            } else {
                Text(
                    text = when (state.managerBuildStatus.name) {
                        "SUCCESS" -> stringResource(R.string.status_recent_build_success)
                        "FAILURE" -> stringResource(R.string.status_recent_build_failed)
                        else -> state.managerBuildStatus.name
                    },
                    fontSize = 14.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    run.htmlUrl.takeIf { it.isNotBlank() }?.let { url ->
                        ctx.findActivity()?.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.status_view_details, run.runNumber)) }
        }
    }
}

// ── Device info section ──

@Composable
private fun DeviceInfoSection(
    kernelVersion: String,
    ksuVersion: String,
    ksuWorking: Boolean,
    behindBy: Int,
    user: com.abk.kernel.data.model.GitHubUser?,
    forkRepo: com.abk.kernel.data.model.GitHubRepo?,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            InfoRow(title = stringResource(R.string.status_kernel), content = kernelVersion)
            InfoRow(
                title = "KernelSU",
                content = if (ksuWorking) ksuVersion else "N/A",
                bottomPadding = if (behindBy > 0 || user != null) 24.dp else 0.dp
            )
            if (behindBy > 0) {
                InfoRow(
                    title = stringResource(R.string.sync_title),
                    content = stringResource(R.string.sync_behind_commits, behindBy),
                )
            }
            if (user != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (user.avatarUrl.isNotBlank()) {
                        AsyncImage(model = user.avatarUrl, contentDescription = null, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                    }
                    Column {
                        Text(user.login, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                        Text(
                            forkRepo?.fullName ?: stringResource(R.string.status_no_fork),
                            fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(title: String, content: String, bottomPadding: Dp = 24.dp) {
    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
    Text(content, fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(top = 2.dp, bottom = bottomPadding))
}

// ── Recent runs section ──

@Composable
private fun RecentRunsSection(
    runs: List<WorkflowRun>,
    onCancel: (WorkflowRun) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.status_recent_runs_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            runs.forEachIndexed { i, run ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = run.displayTitle ?: run.name ?: "#${run.runNumber}",
                            fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface, maxLines = 1
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(run.createdAt.take(10), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                            val (badge, badgeColor) = runBadge(run)
                            Text(badge, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = badgeColor)
                        }
                    }
                    if (run.status in setOf("queued", "waiting", "in_progress")) {
                        IconButton(onClick = { onCancel(run) }) {
                            Icon(MiuixIcons.Close, contentDescription = stringResource(R.string.status_cancel), modifier = Modifier.size(18.dp), tint = colorScheme.error)
                        }
                    }
                }
                if (i < runs.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun runBadge(run: WorkflowRun): Pair<String, Color> {
    val cs = colorScheme
    return when {
        run.conclusion == "success" -> stringResource(R.string.status_recent_build_success) to cs.primary
        run.conclusion == "cancelled" -> stringResource(R.string.status_build_cancelled) to cs.onSurfaceVariantActions
        run.status == "in_progress" -> stringResource(R.string.status_working) to cs.primary
        run.status == "completed" && run.conclusion != "success" -> stringResource(R.string.status_recent_build_failed) to cs.error
        else -> run.status to cs.onSurfaceVariantActions
    }
}
